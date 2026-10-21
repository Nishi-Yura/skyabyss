package jp.revati.skyabyss.facility.guild;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// ギルドの作る・申請する・受ける・抜ける (docs/design/35 の 3.4)。画面 (GuildScreen) とコマンド (/guild) の両方がここを呼ぶ。
// 結果は Result (知らせの文の鍵を持つ)。ギルドの中身が変わったら、入っている人 (と申請した人) の名札の印を直す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Guilds {
    public enum Result {
        OK("ok"),
        IN_GUILD("in_guild"),
        NOT_IN_GUILD("not_in_guild"),
        NAME_LENGTH("name_length"),
        NAME_TAKEN("name_taken"),
        TAG_LENGTH("tag_length"),
        NO_GUILD("no_guild"),
        FULL("full"),
        APPLIED("applied"),
        ALREADY_APPLIED("already_applied"),
        NOT_LEADER("not_leader"),
        NOT_APPLICANT("not_applicant"),
        NOT_MEMBER("not_member"),
        JOINED("joined");

        public final String key;

        Result(String id) {
            this.key = "message.skyabyss.guild." + id;
        }

        public boolean ok() {
            return this == OK || this == APPLIED || this == JOINED;
        }
    }

    private Guilds() {
    }

    // 名前と印の整え (前後の空白を落とし、印は大文字に)
    public static String cleanName(String name) {
        return name.strip().replaceAll("\\s+", " ");
    }

    public static String cleanTag(String tag) {
        return tag.strip().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
    }

    @Nullable
    public static GuildData.Guild of(ServerPlayer player) {
        return GuildData.get(player.server).of(player.getUUID());
    }

    // ---------------------------------------------------------------- 作る
    public static Result create(ServerPlayer player, String rawName, String rawTag, int color, int accent) {
        GuildData data = GuildData.get(player.server);
        String name = cleanName(rawName);
        String tag = cleanTag(rawTag);
        if (data.of(player.getUUID()) != null) {
            return Result.IN_GUILD;
        }
        if (name.codePointCount(0, name.length()) < GuildData.NAME_MIN || name.codePointCount(0, name.length()) > GuildData.NAME_MAX) {
            return Result.NAME_LENGTH;
        }
        if (tag.isEmpty() || tag.codePointCount(0, tag.length()) > GuildData.TAG_MAX) {
            return Result.TAG_LENGTH;
        }
        if (data.byName(name) != null) {
            return Result.NAME_TAKEN;
        }
        // 申請していたギルドがあれば取り下げる
        for (GuildData.Guild g : data.all()) {
            g.applicants.remove(player.getUUID());
        }
        GuildData.Guild g = data.create(name, tag, clampDye(color), clampDye(accent), player.getUUID(), player.getGameProfile().getName(),
                player.server.overworld().getGameTime());
        refresh(player.server, g);
        setBadge(player, g.badge());
        SkyAbyss.LOGGER.info("[guild] {} created {} [{}]", player.getGameProfile().getName(), g.name, g.tag);
        return Result.OK;
    }

    static int clampDye(int c) {
        return Math.max(0, Math.min(15, c));
    }

    // ---------------------------------------------------------------- 申請・入る
    // 誰でも入れるギルドならそのまま入る (JOINED)。そうでなければ申請 (APPLIED。長に知らせる)
    public static Result apply(ServerPlayer player, String guildIdOrName) {
        GuildData data = GuildData.get(player.server);
        if (data.of(player.getUUID()) != null) {
            return Result.IN_GUILD;
        }
        GuildData.Guild g = data.byId(guildIdOrName);
        if (g == null) {
            g = data.byName(guildIdOrName);
        }
        if (g == null) {
            return Result.NO_GUILD;
        }
        if (g.members.size() >= GuildData.MAX_MEMBERS) {
            return Result.FULL;
        }
        g.names.put(player.getUUID(), player.getGameProfile().getName());
        if (g.open) {
            join(player.server, g, player.getUUID());
            setBadge(player, g.badge());
            return Result.JOINED;
        }
        if (!g.applicants.add(player.getUUID())) {
            return Result.ALREADY_APPLIED;
        }
        data.setDirty();
        ServerPlayer leader = player.server.getPlayerList().getPlayer(g.leader);
        if (leader != null) {
            Notice.hint(leader, "message.skyabyss.guild.new_applicant", player.getGameProfile().getName(), g.name);
        }
        return Result.APPLIED;
    }

    // 長が申請を受ける / 断る
    public static Result answer(ServerPlayer leader, UUID applicant, boolean accept) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        if (!g.applicants.remove(applicant)) {
            return Result.NOT_APPLICANT;
        }
        data.setDirty();
        ServerPlayer who = leader.server.getPlayerList().getPlayer(applicant);
        if (!accept) {
            if (who != null) {
                Notice.hint(who, "message.skyabyss.guild.denied", g.name);
            }
            return Result.OK;
        }
        if (data.of(applicant) != null) {
            return Result.IN_GUILD;
        }
        if (g.members.size() >= GuildData.MAX_MEMBERS) {
            return Result.FULL;
        }
        join(leader.server, g, applicant);
        return Result.JOINED;
    }

    private static void join(MinecraftServer server, GuildData.Guild g, UUID who) {
        GuildData data = GuildData.get(server);
        // ほかのギルドへの申請は取り下げる
        for (GuildData.Guild other : data.all()) {
            other.applicants.remove(who);
        }
        g.members.add(who);
        data.setDirty();
        String name = g.nameOf(who);
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                Notice.hint(p, "message.skyabyss.guild.member_joined", name, g.name);
            }
        }
        refresh(server, g);
    }

    // ---------------------------------------------------------------- 抜ける・外す・解散
    public static Result leave(ServerPlayer player) {
        GuildData data = GuildData.get(player.server);
        GuildData.Guild g = data.of(player.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        removeMember(player.server, g, player.getUUID());
        setBadge(player, GuildBadge.NONE);
        return Result.OK;
    }

    public static Result kick(ServerPlayer leader, UUID member) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        if (member.equals(leader.getUUID()) || !g.members.contains(member)) {
            return Result.NOT_MEMBER;
        }
        removeMember(leader.server, g, member);
        return Result.OK;
    }

    static void removeMember(MinecraftServer server, GuildData.Guild g, UUID who) {
        GuildData data = GuildData.get(server);
        g.members.remove(who);
        data.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) {
            setBadge(p, GuildBadge.NONE);
            Notice.hint(p, "message.skyabyss.guild.left", g.name);
        }
        if (g.members.isEmpty()) {
            data.remove(g);
            SkyAbyss.LOGGER.info("[guild] {} disbanded (no members)", g.name);
            return;
        }
        if (g.leader.equals(who)) {
            g.leader = g.members.iterator().next();
            ServerPlayer next = server.getPlayerList().getPlayer(g.leader);
            if (next != null) {
                Notice.hint(next, "message.skyabyss.guild.now_leader", g.name);
            }
        }
        refresh(server, g);
    }

    public static Result setOpen(ServerPlayer leader, boolean open) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        g.open = open;
        data.setDirty();
        return Result.OK;
    }

    // ---------------------------------------------------------------- 名札の印
    // ギルドの人 (いまいる人) の印を今の中身に合わせる
    static void refresh(MinecraftServer server, GuildData.Guild g) {
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                setBadge(p, g.badge());
            }
        }
    }

    static void setBadge(ServerPlayer player, GuildBadge badge) {
        if (!badge.equals(player.getData(FacilitySetup.BADGE))) {
            player.setData(FacilitySetup.BADGE, badge);
            if (!(player instanceof FakePlayer)) {
                player.refreshDisplayName();
                player.refreshTabListName();
            }
        }
    }

    public static GuildBadge badge(Player player) {
        return player.getData(FacilitySetup.BADGE);
    }

    // 入るとき: 抜けている間に外された・解散したなら印を消す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GuildData.Guild g = of(player);
            if (g != null) {
                g.names.put(player.getUUID(), player.getGameProfile().getName());
            }
            setBadge(player, g == null ? GuildBadge.NONE : g.badge());
        }
    }

    // 「[印] 名前」の頭 (印は旗の色、括弧は模様の色)
    public static MutableComponent prefix(GuildBadge b) {
        MutableComponent out = Component.empty();
        Style accent = Style.EMPTY.withColor(TextColor.fromRgb(b.accentColor()));
        out.append(Component.literal("[").withStyle(accent));
        out.append(Component.literal(b.tag()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(b.textColor()))));
        out.append(Component.literal("] ").withStyle(accent));
        return out;
    }

    // チャットと Tab の名前の頭に印を足す (サーバー側。名札はクライアントの GuildClient が描く)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onNameFormat(PlayerEvent.NameFormat event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) {
            return;
        }
        GuildBadge b = badge(p);
        if (b.present()) {
            event.setDisplayname(prefix(b).append(event.getDisplayname()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTabName(PlayerEvent.TabListNameFormat event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) {
            return;
        }
        GuildBadge b = badge(p);
        if (b.present()) {
            event.setDisplayName(prefix(b).append(event.getDisplayName() != null ? event.getDisplayName() : p.getName()));
        }
    }
}
