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
        JOINED("joined"),
        NOT_OFFICER("not_officer"),
        NO_COINS("no_coins"),
        REQ_DONE("req_done"),
        NOTHING_TO_GIVE("nothing_to_give"),
        BANNER("banner"),
        DELIVERED("delivered"),
        LEADER_STAYS("leader_stays"),
        NEW_LEADER("new_leader"),
        DISBANDED("disbanded"),
        PATTERN_LOCKED("pattern_locked"),
        PLATE_LOCKED("plate_locked");

        public final String key;

        Result(String id) {
            this.key = "message.skyabyss.guild." + id;
        }

        public boolean ok() {
            return this == OK || this == APPLIED || this == JOINED || this == BANNER || this == DELIVERED || this == NEW_LEADER || this == DISBANDED;
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
        jp.revati.skyabyss.advancement.Beats.fire(player, "guild:join");
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
        for (UUID m : g.members) {
            ServerPlayer officer = g.rank(m) >= GuildData.RANK_OFFICER ? player.server.getPlayerList().getPlayer(m) : null;
            if (officer != null) {
                Notice.hint(officer, "message.skyabyss.guild.new_applicant", player.getGameProfile().getName(), g.name);
            }
        }
        return Result.APPLIED;
    }

    // 長か副長が申請を受ける / 断る
    public static Result answer(ServerPlayer leader, UUID applicant, boolean accept) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (g.rank(leader.getUUID()) < GuildData.RANK_OFFICER) {
            return Result.NOT_OFFICER;
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
        ServerPlayer joined = server.getPlayerList().getPlayer(who);
        if (joined != null) {
            jp.revati.skyabyss.advancement.Beats.fire(joined, "guild:join");
        }
        String name = g.nameOf(who);
        for (UUID m : g.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) {
                Notice.hint(p, "message.skyabyss.guild.member_joined", name, g.name);
            }
        }
        refresh(server, g);
        GuildAchievements.check(server, g);
    }

    // ---------------------------------------------------------------- 抜ける・外す・譲る・解散
    // 長は抜けられない (間違いよけ)。先に長を譲る (transfer) か、解散する (disband)
    public static Result leave(ServerPlayer player) {
        GuildData data = GuildData.get(player.server);
        GuildData.Guild g = data.of(player.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (g.leader.equals(player.getUUID())) {
            return Result.LEADER_STAYS;
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

    // 長がほかの人に長を譲る (前の長は副長になる)
    public static Result transfer(ServerPlayer leader, UUID member) {
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
        g.leader = member;
        g.officers.remove(member);
        g.officers.add(leader.getUUID());
        data.setDirty();
        for (UUID m : g.members) {
            ServerPlayer p = leader.server.getPlayerList().getPlayer(m);
            if (p != null) {
                Notice.hint(p, "message.skyabyss.guild.leader_changed", g.name, g.nameOf(member));
            }
        }
        SkyAbyss.LOGGER.info("[guild] {} handed {} to {}", leader.getGameProfile().getName(), g.name, g.nameOf(member));
        return Result.NEW_LEADER;
    }

    // 長が解散する。倉庫の中身は長へ (持ちきれない分は足もと)。ギルドの人みなの印を消して知らせる
    public static Result disband(ServerPlayer leader) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        for (net.minecraft.world.item.ItemStack s : g.storage) {
            if (!s.isEmpty()) {
                jp.revati.skyabyss.quest.Quests.give(leader, s.copy());
            }
        }
        data.remove(g);
        for (UUID m : g.members) {
            ServerPlayer p = leader.server.getPlayerList().getPlayer(m);
            if (p != null) {
                setBadge(p, GuildBadge.NONE);
                Notice.hint(p, "message.skyabyss.guild.disbanded_by", g.name);
            }
        }
        setBadge(leader, GuildBadge.NONE);
        SkyAbyss.LOGGER.info("[guild] {} disbanded {}", leader.getGameProfile().getName(), g.name);
        return Result.DISBANDED;
    }

    static void removeMember(MinecraftServer server, GuildData.Guild g, UUID who) {
        GuildData data = GuildData.get(server);
        g.members.remove(who);
        g.officers.remove(who);
        data.setDirty();
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) {
            setBadge(p, GuildBadge.NONE);
            Notice.hint(p, "message.skyabyss.guild.left", g.name);
        }
        if (g.members.isEmpty()) {
            // 倉庫の中身は最後の人へ (いなければ消える)
            if (p != null) {
                for (net.minecraft.world.item.ItemStack s : g.storage) {
                    if (!s.isEmpty()) {
                        jp.revati.skyabyss.quest.Quests.give(p, s.copy());
                    }
                }
            }
            data.remove(g);
            SkyAbyss.LOGGER.info("[guild] {} disbanded (no members)", g.name);
            return;
        }
        if (g.leader.equals(who)) {
            // 副長がいれば古い副長から、いなければいちばん古い人
            UUID nextId = g.officers.isEmpty() ? g.members.iterator().next() : g.officers.iterator().next();
            g.officers.remove(nextId);
            g.leader = nextId;
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

    // ---------------------------------------------------------------- 位・倉庫の決まり・旗
    // 長が人を副長にする / 副長から戻す
    public static Result setOfficer(ServerPlayer leader, UUID member, boolean officer) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        if (member.equals(g.leader) || !g.members.contains(member)) {
            return Result.NOT_MEMBER;
        }
        if (officer) {
            g.officers.add(member);
        } else {
            g.officers.remove(member);
        }
        data.setDirty();
        return Result.OK;
    }

    // 長が倉庫から取り出せる位を決める (0 = 誰でも、1 = 副長から、2 = 長だけ)
    public static Result setTakeRank(ServerPlayer leader, int rank) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        g.takeRank = Math.max(GuildData.RANK_MEMBER, Math.min(GuildData.RANK_LEADER, rank));
        data.setDirty();
        return Result.OK;
    }

    // 長が旗 (印と色) を作り直す (模様と名札の色は今のまま)
    public static Result setEmblem(ServerPlayer leader, String rawTag, int color, int accent) {
        GuildData.Guild g = of(leader);
        return setEmblem(leader, rawTag, color, accent, g == null ? GuildEmblems.DEFAULT : g.pattern, g == null ? "" : g.plate);
    }

    // 長が旗 (印・色・模様・名札の色) を作り直す。模様と名札の色は、はじめからのものか功績で開いたものだけ
    public static Result setEmblem(ServerPlayer leader, String rawTag, int color, int accent, String pattern, String plate) {
        GuildData data = GuildData.get(leader.server);
        GuildData.Guild g = data.of(leader.getUUID());
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!g.leader.equals(leader.getUUID())) {
            return Result.NOT_LEADER;
        }
        String tag = cleanTag(rawTag);
        if (tag.isEmpty() || tag.codePointCount(0, tag.length()) > GuildData.TAG_MAX) {
            return Result.TAG_LENGTH;
        }
        if (!GuildEmblems.usable(pattern, g.unlocked)) {
            return Result.PATTERN_LOCKED;
        }
        if (!GuildEmblems.plateUsable(plate, g.unlocked)) {
            return Result.PLATE_LOCKED;
        }
        g.tag = tag;
        g.color = clampDye(color);
        g.accent = clampDye(accent);
        g.pattern = pattern;
        g.plate = plate;
        data.setDirty();
        refresh(leader.server, g);
        setBadge(leader, g.badge());
        return Result.OK;
    }

    // 旗の値段 (鐘銭)
    public static final int BANNER_COST = 3;

    // ギルドの旗の品 (地の色に、模様の色でギルドの模様。画面の見本と同じ品)
    public static net.minecraft.world.item.ItemStack bannerItem(MinecraftServer server, GuildData.Guild g) {
        net.minecraft.world.item.ItemStack stack = GuildEmblems.banner(server.registryAccess(), g.pattern, g.color, g.accent);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("item.skyabyss.guild_banner", g.name));
        return stack;
    }

    // 受付で旗を受け取る (鐘銭 BANNER_COST)
    public static Result takeBanner(ServerPlayer player) {
        GuildData.Guild g = of(player);
        if (g == null) {
            return Result.NOT_IN_GUILD;
        }
        if (!jp.revati.skyabyss.facility.Coins.take(player, BANNER_COST)) {
            return Result.NO_COINS;
        }
        jp.revati.skyabyss.quest.Quests.give(player, bannerItem(player.server, g));
        return Result.BANNER;
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
