package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.group.Activity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// パーティの操作 (コマンドと画面の両方から) と、経験値を分ける相手の選び方 (10 の 6 章)。
// - 最大 6 人。リーダーが招待し、招待は 60 秒で切れる
// - 居合わせなかった仲間への分け前: 居合わせた人の取り分の skyabyssPartyShare % (既定 70、50〜80 に収める)
//   ・同じディメンションで、居合わせた仲間の誰かから 64 ブロック以内
//   ・巨神の背中 (ダンジョン) の中では、同じ背中にいれば距離を問わない
//   ・居合わせた仲間のいちばん高いレベルより 10 を超えて低い人には分けない (引き上げの抜け道)
//   ・倒れている人・観戦者には分けない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Parties {
    public static final int MAX_MEMBERS = 6;
    public static final double SHARE_RANGE = 64;
    // 背中の中で、放置していても分け前を受け取れる、居合わせた仲間からの距離
    public static final double BACK_SHARE_RANGE = 128;
    public static final int LEVEL_GAP = 10;
    public static final int SHARE_MIN = 50;
    public static final int SHARE_MAX = 80;
    public static final int SHARE_DEFAULT = 70;
    static final long INVITE_TICKS = 20 * 60;

    public static GameRules.Key<GameRules.IntegerValue> PARTY_SHARE;

    // 招待された人 → (招待したパーティのリーダー, 切れる時刻)
    record Invite(UUID from, String fromName, long expires) {
    }

    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    private Parties() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> PARTY_SHARE = GameRules.register("skyabyssPartyShare", GameRules.Category.MISC,
                GameRules.IntegerValue.create(SHARE_DEFAULT)));
    }

    // 分け前の割合 (0.5〜0.8)
    public static double shareRate(MinecraftServer server) {
        int v = PARTY_SHARE == null ? SHARE_DEFAULT : server.getGameRules().getInt(PARTY_SHARE);
        return Mth.clamp(v, SHARE_MIN, SHARE_MAX) / 100.0;
    }

    @Nullable
    public static PartyData.Party of(ServerPlayer player) {
        return PartyData.get(player.server).of(player.getUUID());
    }

    // パーティの今いる人 (パーティが無ければ空。2026-10-23: 一人で深淵の印を取ると落ちて、近道の格子が開かなかった)
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, @org.jetbrains.annotations.Nullable PartyData.Party party) {
        List<ServerPlayer> list = new ArrayList<>();
        if (party == null) {
            return list;
        }
        for (UUID id : party.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- 分け前
    // present (経験値を受け取った人) のパーティの仲間のうち、居合わせなかったが分け前を受け取る人
    public static List<ServerPlayer> absentMates(ServerLevel level, Collection<ServerPlayer> present) {
        MinecraftServer server = level.getServer();
        return absentMates(level, present, id -> server.getPlayerList().getPlayer(id));
    }

    // lookup: UUID から今いる人を引く (自己テストでは偽のプレイヤーを渡す)
    public static List<ServerPlayer> absentMates(ServerLevel level, Collection<ServerPlayer> present,
            java.util.function.Function<UUID, ServerPlayer> lookup) {
        List<ServerPlayer> out = new ArrayList<>();
        if (present.isEmpty()) {
            return out;
        }
        PartyData data = PartyData.get(level.getServer());
        List<PartyData.Party> seen = new ArrayList<>();
        for (ServerPlayer p : present) {
            PartyData.Party party = data.of(p.getUUID());
            if (party == null || seen.contains(party)) {
                continue;
            }
            seen.add(party);
            // 居合わせた仲間 (このパーティの人だけ)
            List<ServerPlayer> here = new ArrayList<>();
            int top = 0;
            for (ServerPlayer q : present) {
                if (party.members.contains(q.getUUID())) {
                    here.add(q);
                    top = Math.max(top, Growth.get(q).level);
                }
            }
            for (UUID id : party.members) {
                ServerPlayer m = lookup.apply(id);
                if (m == null || present.contains(m) || out.contains(m) || !m.isAlive() || m.isSpectator()
                        || m.level() != level) {
                    continue;
                }
                if (top - Growth.get(m).level > LEVEL_GAP) {
                    continue;
                }
                // 背中・深淵 (ダンジョン) の中では距離を問わないが、放置の仲間には渡さない (10 の 6.2):
                // 直近 60 秒に動いた・戦った人か、居合わせた仲間から 128 ブロック以内の人だけ
                boolean sameBack = (TitanBacks.isBack(level.dimension()) || level.dimension() == jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL)
                        && (Activity.active(m, Activity.SHARE_TICKS) || near(m, here, BACK_SHARE_RANGE));
                if (sameBack || near(m, here, SHARE_RANGE)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static boolean near(ServerPlayer m, List<ServerPlayer> here, double range) {
        for (ServerPlayer q : here) {
            if (q.level() == m.level() && q.distanceToSqr(m) <= range * range) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 操作 (成功したら true。理由はその場で本人に伝える)
    public static boolean invite(ServerPlayer from, ServerPlayer to) {
        MinecraftServer server = from.server;
        PartyData data = PartyData.get(server);
        PartyData.Party party = data.of(from.getUUID());
        if (to == from) {
            return deny(from, "message.skyabyss.party.invite_self");
        }
        if (party != null && !party.isLeader(from.getUUID())) {
            return deny(from, "message.skyabyss.party.not_leader");
        }
        if (party != null && party.members.size() >= MAX_MEMBERS) {
            return deny(from, "message.skyabyss.party.full", MAX_MEMBERS);
        }
        if (data.of(to.getUUID()) != null) {
            return deny(from, "message.skyabyss.party.already_in", to.getDisplayName());
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), from.getGameProfile().getName(), server.overworld().getGameTime() + INVITE_TICKS));
        Notice.hint(from, "message.skyabyss.party.invited", to.getDisplayName());
        // 右上の札 (「進展」)。受け方 (O の画面・/party accept) は同じ札の本文に入れてある
        Notice.unlock(to, "message.skyabyss.party.invite_received", from.getDisplayName());
        to.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        PartyNetwork.syncSoon(to);
        return true;
    }

    @Nullable
    static Invite inviteFor(ServerPlayer player) {
        Invite inv = INVITES.get(player.getUUID());
        if (inv != null && inv.expires() < player.server.overworld().getGameTime()) {
            INVITES.remove(player.getUUID());
            return null;
        }
        return inv;
    }

    public static boolean accept(ServerPlayer player) {
        MinecraftServer server = player.server;
        Invite inv = inviteFor(player);
        if (inv == null) {
            return deny(player, "message.skyabyss.party.no_invite");
        }
        INVITES.remove(player.getUUID());
        PartyData data = PartyData.get(server);
        if (data.of(player.getUUID()) != null) {
            return deny(player, "message.skyabyss.party.leave_first");
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(inv.from());
        PartyData.Party party = data.of(inv.from());
        if (party == null) {
            if (leader == null) {
                return deny(player, "message.skyabyss.party.invite_gone");
            }
            party = data.create(leader.getUUID(), leader.getGameProfile().getName());
        } else if (!party.isLeader(inv.from())) {
            return deny(player, "message.skyabyss.party.invite_gone");
        }
        if (party.members.size() >= MAX_MEMBERS) {
            return deny(player, "message.skyabyss.party.full", MAX_MEMBERS);
        }
        data.add(party, player.getUUID(), player.getGameProfile().getName());
        for (ServerPlayer m : onlineMembers(server, party)) {
            Notice.unlock(m, "message.skyabyss.party.joined", player.getDisplayName());
            jp.revati.skyabyss.tutorial.TutorialHints.start(m, jp.revati.skyabyss.tutorial.TutorialHints.PING);
            PartyNetwork.syncSoon(m);
        }
        return true;
    }

    public static boolean decline(ServerPlayer player) {
        Invite inv = INVITES.remove(player.getUUID());
        if (inv == null) {
            return deny(player, "message.skyabyss.party.no_invite");
        }
        ServerPlayer from = player.server.getPlayerList().getPlayer(inv.from());
        if (from != null) {
            Notice.hint(from, "message.skyabyss.party.declined", player.getDisplayName());
        }
        Notice.hint(player, "message.skyabyss.party.you_declined");
        PartyNetwork.syncSoon(player);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        PartyData data = PartyData.get(player.server);
        PartyData.Party party = data.of(player.getUUID());
        if (party == null) {
            return deny(player, "message.skyabyss.party.none");
        }
        List<ServerPlayer> before = onlineMembers(player.server, party);
        data.remove(player.getUUID());
        for (ServerPlayer m : before) {
            Notice.hint(m, "message.skyabyss.party.left", player.getDisplayName());
            PartyNetwork.syncSoon(m);
        }
        return true;
    }

    public static boolean kick(ServerPlayer leader, UUID target, String targetName) {
        PartyData data = PartyData.get(leader.server);
        PartyData.Party party = data.of(leader.getUUID());
        if (party == null) {
            return deny(leader, "message.skyabyss.party.none");
        }
        if (!party.isLeader(leader.getUUID())) {
            return deny(leader, "message.skyabyss.party.not_leader");
        }
        if (target.equals(leader.getUUID()) || !party.members.contains(target)) {
            return deny(leader, "message.skyabyss.party.not_member", targetName);
        }
        List<ServerPlayer> before = onlineMembers(leader.server, party);
        data.remove(target);
        for (ServerPlayer m : before) {
            Notice.hint(m, "message.skyabyss.party.kicked", targetName);
            PartyNetwork.syncSoon(m);
        }
        return true;
    }

    public static boolean promote(ServerPlayer leader, UUID target, String targetName) {
        PartyData data = PartyData.get(leader.server);
        PartyData.Party party = data.of(leader.getUUID());
        if (party == null) {
            return deny(leader, "message.skyabyss.party.none");
        }
        if (!party.isLeader(leader.getUUID())) {
            return deny(leader, "message.skyabyss.party.not_leader");
        }
        if (!party.members.contains(target) || target.equals(leader.getUUID())) {
            return deny(leader, "message.skyabyss.party.not_member", targetName);
        }
        data.promote(party, target);
        for (ServerPlayer m : onlineMembers(leader.server, party)) {
            Notice.hint(m, "message.skyabyss.party.new_leader", targetName);
            PartyNetwork.syncSoon(m);
        }
        return true;
    }

    @Nullable
    public static UUID memberByName(PartyData.Party party, String name) {
        for (Map.Entry<UUID, String> e : party.names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static boolean deny(ServerPlayer player, String key, Object... args) {
        Notice.send(player, ModText.deny(key, args));
        return false;
    }

    // 自己テスト用
    static void clearInvites() {
        INVITES.clear();
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        INVITES.clear();
    }
}
