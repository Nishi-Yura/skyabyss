package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// グループの共通部分の自己テスト (docs/design/10 の 10 章): 部屋の占有・戦いの参加者 (M6)・影 (M4)・乗っている人の数え方 (M5)・放置の分け前 (M7)。
// 扉・敵・記録・パーティの引き継ぎは、各背中の自己テスト (titan back (groups) など) が見る。
// 偽のプレイヤー (FakePlayer) はレベルの人の一覧に入らないので、一覧を渡す形の部品で確かめる
public final class GroupSelfTest {
    private GroupSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
            if (back == null) {
                f.add("titan_back dimension is missing");
            } else {
                roomLocks(back, f);
                bossRoom(server.overworld(), f);
                presence(back, f);
                shadow(server, f);
                share(server, back, f);
                perf(back, f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST group crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST group PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST group FAILED: {}", m));
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    // 部屋の占有: 最初のグループが持ち主・別のグループは外へ・持ち主が去って grace で手放し・次のグループが新しい持ち主
    private static void roomLocks(ServerLevel back, List<String> f) {
        String room = "selftest_room";
        RoomLocks.release(room);
        FakePlayer a = player(back, "lock_a");
        FakePlayer b = player(back, "lock_b");
        List<UUID> events = new ArrayList<>();
        RoomLocks.Listener listener = new RoomLocks.Listener() {
            @Override
            public void acquired(ServerLevel level, UUID group) {
                events.add(group);
            }

            @Override
            public void released(ServerLevel level, UUID formerOwner) {
                events.add(null);
            }
        };
        List<ServerPlayer> ejected = new ArrayList<>();
        UUID ga = Groups.keyOf(a);
        UUID gb = Groups.keyOf(b);
        UUID owner = RoomLocks.update(back, room, List.of(a, b), 100, listener, ejected::add);
        if (!ga.equals(owner) || events.size() != 1 || !ga.equals(events.get(0))) {
            f.add("the first group in did not become the room owner");
        }
        if (ejected.size() != 1 || ejected.get(0) != b) {
            f.add("the second group was not turned away from the room");
        }
        // 持ち主が部屋にいる間は、別のグループが入っても持ち主のまま
        ejected.clear();
        owner = RoomLocks.update(back, room, List.of(b), 100, listener, ejected::add);
        if (!ga.equals(RoomLocks.owner(room)) || ejected.size() != 1) {
            f.add("the owner lost the room before the grace time (owner " + owner + ")");
        }
        RoomLocks.release(room);
        owner = RoomLocks.update(back, room, List.of(b), 100, listener, ejected::add);
        if (!gb.equals(owner)) {
            f.add("the next group did not take the released room");
        }
        RoomLocks.release(room);
    }

    // 戦いの参加者 (M6): 倒れる瞬間に部屋にいた人・部屋で倒れた人・切断した人だけ。ほんの一瞬いた人、出て行った人は数えない
    // 地表の読み込み済みの所 (世界の出生地) で試す: 湧かせたボスがすぐ見つかる (背中のチャンクはこの tick には読み込み済みと限らない)
    private static void bossRoom(ServerLevel back, List<String> f) {
        FakePlayer a = player(back, "boss_a");
        UUID group = Groups.keyOf(a);
        List<ServerPlayer> inside = new ArrayList<>(List.of(a));
        Mob boss = TitanBackTrials.spawnGuard(back, "sentinel", back.getSharedSpawnPos().above(2), group);
        if (boss == null) {
            f.add("could not spawn the test boss");
            return;
        }
        GroupBossRoom.Host host = new GroupBossRoom.Host() {
            @Override
            public List<ServerPlayer> playersInside(ServerLevel level) {
                return inside;
            }

            @Override
            public void seal(ServerLevel level, UUID g) {
            }

            @Override
            public void open(ServerLevel level, UUID g) {
            }

            @Override
            public void pullIn(ServerLevel level, UUID g) {
            }

            @Override
            public void letBack(ServerLevel level, UUID g, Set<UUID> participants) {
            }

            @Override
            public Mob spawnBoss(ServerLevel level, UUID g, List<ServerPlayer> challengers) {
                return boss;
            }

            @Override
            public Vec3 home() {
                return boss.position();
            }

            @Override
            public double leash() {
                return 100;
            }

            @Override
            public void broadcast(ServerLevel level, UUID g, net.minecraft.network.chat.Component message) {
            }

            @Override
            public void countdown(ServerLevel level, UUID g, int secondsLeft) {
            }

            @Override
            public void cleanup(ServerLevel level, UUID g) {
            }

            @Override
            public boolean asleep(ServerLevel level) {
                return false;
            }
        };
        GroupBossRoom room = new GroupBossRoom(group);
        if (!room.start(back, host, inside)) {
            f.add("the test boss room did not start");
            return;
        }
        // a が部屋にいる間に倒れた: a は参加者に数える
        room.tick(back, host);
        Set<UUID> ids = room.finish(back, host, boss.getUUID());
        if (ids == null || !ids.contains(a.getUUID())) {
            f.add("the player in the room when the boss fell was not credited");
        }
        // 戦いの最中に部屋の外へ出た人 (倒れていない) は数えない
        if (!room.start(back, host, inside)) {
            f.add("the test boss room did not restart");
            return;
        }
        room.tick(back, host);
        inside.clear();
        room.tick(back, host);
        ids = room.finish(back, host, boss.getUUID());
        if (ids == null || ids.contains(a.getUUID())) {
            f.add("a player who walked out of the room before the boss fell was still credited");
        }
        // 部屋で倒れた人は、外で目を覚ましても数える
        inside.add(a);
        if (!room.start(back, host, inside)) {
            f.add("the test boss room did not restart (death)");
            return;
        }
        room.tick(back, host);
        GroupBossRoom.noteDeath(a.getUUID());
        // 倒れた参加者は、倒れた直後は扉から戻れない (参加者には残る。20 秒の待ちは heart reentry で見る)
        if (!room.fallen().contains(a.getUUID()) || room.rejoinable(back).contains(a.getUUID()) || !room.participants().contains(a.getUUID())) {
            f.add("a participant who died in the room can still be let back through the door during the fight");
        }
        inside.clear();
        room.tick(back, host);
        ids = room.finish(back, host, boss.getUUID());
        if (ids == null || !ids.contains(a.getUUID())) {
            f.add("a player who died in the room was not credited");
        }
        // 切断した人は、最後に部屋にいた記録で数える (a は偽のプレイヤーなのでサーバーの一覧にいない)
        inside.add(a);
        if (!room.start(back, host, inside)) {
            f.add("the test boss room did not restart (offline)");
            return;
        }
        room.tick(back, host);
        inside.clear();
        ids = room.finish(back, host, boss.getUUID());
        if (ids == null || !ids.contains(a.getUUID())) {
            f.add("a player who disconnected in the room was not credited");
        }
        // 戦いの途中から入った人は、少し (30 tick) いないと参加者にならない: 一瞬 (10 tick) だけでは足りない
        inside.add(a);
        room.start(back, host, inside);
        if (room.participants().size() != 1) {
            f.add("the participants at the start were not counted");
        }
        room.reset(back, host, false);
        boss.discard();
    }

    // 乗っている人の数え方 (M5): 観戦者・クリエイティブ・長く動いていない人は数えない
    private static void presence(ServerLevel back, List<String> f) {
        FakePlayer active = player(back, "p_active");
        FakePlayer spectator = player(back, "p_spectator");
        FakePlayer creative = player(back, "p_creative");
        FakePlayer idle = player(back, "p_idle");
        spectator.setGameMode(GameType.SPECTATOR);
        creative.setGameMode(GameType.CREATIVE);
        Activity.setLastActive(idle, -Activity.PRESENT_TICKS - 100);
        if (Activity.anyoneAboard(List.of(spectator, creative, idle))) {
            f.add("a spectator, a creative player or an idle player counted as someone aboard");
        }
        if (!Activity.anyoneAboard(List.of(spectator, active))) {
            f.add("an active player did not count as someone aboard");
        }
        GameType restore = GameType.SURVIVAL;
        spectator.setGameMode(restore);
        creative.setGameMode(restore);
    }

    // 遅れて来た人の個人の影 (M4): 世界の影は立たず、その人にだけ見える
    private static void shadow(MinecraftServer server, List<String> f) {
        ServerLevel overworld = server.overworld();
        FakePlayer late = player(overworld, "late_player");
        FakePlayer other = player(overworld, "other_player");
        if (SkyEvents.activePass() != null) {
            f.add("a world sky pass was running during the personal shadow test");
            return;
        }
        int before = jp.revati.skyabyss.sky.SkyEventData.get(overworld).passCount();
        SkyEvents.startPersonal(overworld, late);
        if (SkyEvents.activePass() != null) {
            f.add("a personal shadow started a world shadow (everyone's sky, steps and the whale would stop)");
        }
        if (SkyEvents.activePassFor(late) == null) {
            f.add("the late player does not see their own shadow");
        }
        if (SkyEvents.activePassFor(other) != null) {
            f.add("another player sees the late player's personal shadow");
        }
        if (jp.revati.skyabyss.sky.SkyEventData.get(overworld).passCount() != before) {
            f.add("a personal shadow was counted as a world shadow pass");
        }
        if (SkyEvents.personalEnd(late) < 0) {
            f.add("the end of the personal shadow is unknown");
        }
    }

    // パーティの分け前 (M7): 背中の中でも、放置している遠い仲間には渡さない
    private static void share(MinecraftServer server, ServerLevel back, List<String> f) {
        FakePlayer here = player(back, "share_here");
        FakePlayer far = player(back, "share_far");
        FakePlayer near = player(back, "share_near");
        here.setPos(0, 100, 0);
        far.setPos(500, 100, 0);
        near.setPos(100, 100, 0);
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        try {
            parties.clearAll();
            PartyData.Party party = parties.create(here.getUUID(), "share_here");
            parties.add(party, far.getUUID(), "share_far");
            parties.add(party, near.getUUID(), "share_near");
            java.util.Map<UUID, ServerPlayer> online = new java.util.HashMap<>();
            for (FakePlayer p : List.of(here, far, near)) {
                online.put(p.getUUID(), p);
            }
            // 遠くの仲間が動いている: 受け取る
            Activity.setLastActive(far, back.getGameTime());
            Activity.setLastActive(near, back.getGameTime());
            List<ServerPlayer> got = Parties.absentMates(back, List.of(here), online::get);
            if (!got.contains(far) || !got.contains(near)) {
                f.add("active mates in the same back did not receive their share");
            }
            // 遠くの仲間が長く動いていない: 受け取らない。近い仲間 (128 以内) は放置でも受け取る
            Activity.setLastActive(far, -Activity.SHARE_TICKS - 100);
            Activity.setLastActive(near, -Activity.SHARE_TICKS - 100);
            got = Parties.absentMates(back, List.of(here), online::get);
            if (got.contains(far)) {
                f.add("an idle mate far away still received a share (party AFK leeching)");
            }
            if (!got.contains(near)) {
                f.add("an idle mate within 128 blocks did not receive a share");
            }
        } finally {
            parties.restore(snapshot, server.registryAccess());
            Activity.setLastActive(far, back.getGameTime());
            Activity.setLastActive(near, back.getGameTime());
        }
    }

    // 負荷 (10 の 10.8): 扉の当たり判定 (動く者が扉に当たるたびに引く) と、グループへ送る見かけの組み立て
    private static void perf(ServerLevel back, List<String> f) {
        FakePlayer p = player(back, "perf_a");
        BlockPos pos = TitanBackLayout.GATE.get(0);
        back.setBlock(pos, jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get().defaultBlockState(), 2);
        var state = back.getBlockState(pos);
        UUID group = Groups.keyOf(p);
        GroupGates.open(back, group, TitanBackLayout.GATE);
        var ctx = net.minecraft.world.phys.shapes.CollisionContext.of(p);
        int calls = 200_000;
        long t0 = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < calls; i++) {
            sink += state.getCollisionShape(back, pos, ctx).isEmpty() ? 1 : 0;
        }
        long shapeNs = System.nanoTime() - t0;
        t0 = System.nanoTime();
        int overlays = 2000;
        for (int i = 0; i < overlays; i++) {
            sink += GroupOverlays.compute(back, group).size();
        }
        long overlayNs = System.nanoTime() - t0;
        GroupProgress.reset(back, group);
        SkyAbyss.LOGGER.info("SELFTEST group perf: gate collision {} ns per query ({} queries), overlay build {} us per player ({} builds, {})",
                shapeNs / calls, calls, overlayNs / overlays / 1000, overlays, sink);
        if (shapeNs / calls > 20_000 || overlayNs / overlays > 2_000_000) {
            f.add("the per-group gate lookups are too slow");
        }
    }
}
