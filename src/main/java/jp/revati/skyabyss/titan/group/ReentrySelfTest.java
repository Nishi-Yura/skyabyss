package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 倒れた参加者の再入場の自己テスト (heart reentry)。docs/design/02 の 4.2、10 の 10.4 / 10.5。
//  - 20 秒より前は戻れない / 20 秒たてば戻れる / 回数を使い切ると戻れない / 全滅 (やり直し) で回数が戻る
//  - ゲームルール skyabyssHeartReentryLimit: 既定 3、0 = 数えない、ほかの値
//  - 心臓の間 (GroupBossRoom。4 体とも同じ部品を使う) と、岩鯨の中ボスの広間 (TitanBackTrials の広間) の両方
// 偽のプレイヤーはレベルの人の一覧に入らないので、目を覚ました時刻は Reentry へ直接入れる
public final class ReentrySelfTest {
    private ReentrySelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int before = server.getGameRules().getInt(ReentryRules.LIMIT);
        try {
            ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
            if (back == null || ReentryRules.LIMIT == null) {
                f.add("titan_back dimension or the reentry game rule is missing");
            } else {
                rule(server, f);
                pure(f);
                heartRoom(server, server.overworld(), f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST heart reentry crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (ReentryRules.LIMIT != null) {
                server.getGameRules().getRule(ReentryRules.LIMIT).set(before, server);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart reentry PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST heart reentry FAILED: {}", m));
        }
    }

    private static void setLimit(MinecraftServer server, int n) {
        server.getGameRules().getRule(ReentryRules.LIMIT).set(n, server);
    }

    private static void rule(MinecraftServer server, List<String> f) {
        setLimit(server, ReentryRules.DEFAULT_LIMIT);
        if (ReentryRules.limit(server) != 3) {
            f.add("the default reentry limit is not 3");
        }
        setLimit(server, 0);
        if (ReentryRules.limit(server) != 0) {
            f.add("limit 0 (unlimited) was not read back");
        }
        setLimit(server, 5);
        if (ReentryRules.limit(server) != 5) {
            f.add("a custom limit of 5 was not read back");
        }
    }

    // 待ち時間・回数の数え方 (時刻を渡す形の部品)
    private static void pure(List<String> f) {
        UUID id = UUID.randomUUID();
        Reentry r = new Reentry();
        if (!r.mayEnter(id, 0, 3)) {
            f.add("a participant who never fell was blocked");
        }
        r.fell(id);
        if (r.mayEnter(id, 1000, 3) || r.status(id, 1000, 3) != Reentry.Status.WAITING) {
            f.add("a fallen participant who has not woken yet was let back");
        }
        r.observeAwake(id, 100);
        if (r.remaining(id, 100) != Reentry.WAIT_TICKS || r.mayEnter(id, 100 + Reentry.WAIT_TICKS - 1, 3)) {
            f.add("a participant was let back before 20 s after waking");
        }
        if (!r.mayEnter(id, 100 + Reentry.WAIT_TICKS, 3) || r.status(id, 100 + Reentry.WAIT_TICKS, 3) != Reentry.Status.READY) {
            f.add("a participant was not let back 20 s after waking");
        }
        r.observeAwake(id, 99999);
        if (r.remaining(id, 100 + Reentry.WAIT_TICKS) != 0) {
            f.add("a later sighting moved the wake time");
        }
        for (int limit : new int[] {1, 3, 7}) {
            Reentry q = new Reentry();
            for (int i = 0; i < limit; i++) {
                q.fell(id);
                q.observeAwake(id, 0);
                if (q.status(id, Reentry.WAIT_TICKS, limit) != Reentry.Status.READY || !q.entered(id)) {
                    f.add("limit " + limit + ": return " + (i + 1) + " was refused");
                }
            }
            q.fell(id);
            q.observeAwake(id, 0);
            if (q.mayEnter(id, 100000, limit) || q.status(id, 100000, limit) != Reentry.Status.EXHAUSTED) {
                f.add("limit " + limit + ": the door opened after the returns were used up");
            }
        }
        // 0 = 数えない
        Reentry free = new Reentry();
        for (int i = 0; i < 12; i++) {
            free.fell(id);
            free.observeAwake(id, 0);
            if (!free.mayEnter(id, Reentry.WAIT_TICKS, 0) || !free.entered(id)) {
                f.add("limit 0: return " + (i + 1) + " was refused");
                break;
            }
        }
        // 目を覚ます前に中にいた扱いでは数えない
        Reentry early = new Reentry();
        early.fell(id);
        if (early.entered(id) || early.used(id) != 0) {
            f.add("entering before waking was counted");
        }
        early.clear();
        if (early.isFallen(id)) {
            f.add("clear() kept the fallen record");
        }
    }

    // 心臓の間の部品 (4 体とも GroupBossRoom): 扉の開き方・回数・全滅
    private static void heartRoom(MinecraftServer server, ServerLevel level, List<String> f) {
        FakePlayer a = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "reentry_a"));
        UUID group = Groups.keyOf(a);
        List<ServerPlayer> inside = new ArrayList<>(List.of(a));
        Mob[] boss = new Mob[1];
        GroupBossRoom.Host host = new GroupBossRoom.Host() {
            @Override
            public List<ServerPlayer> playersInside(ServerLevel l) {
                return inside;
            }

            @Override
            public void seal(ServerLevel l, UUID g) {
            }

            @Override
            public void open(ServerLevel l, UUID g) {
            }

            @Override
            public void pullIn(ServerLevel l, UUID g) {
            }

            @Override
            public void letBack(ServerLevel l, UUID g, Set<UUID> participants) {
            }

            @Override
            public Mob spawnBoss(ServerLevel l, UUID g, List<ServerPlayer> challengers) {
                boss[0] = jp.revati.skyabyss.titan.dungeon.TitanBackTrials.spawnGuard(l, "sentinel", l.getSharedSpawnPos().above(2), g);
                return boss[0];
            }

            @Override
            public Vec3 home() {
                return boss[0] == null ? Vec3.ZERO : boss[0].position();
            }

            @Override
            public double leash() {
                return 100;
            }

            @Override
            public void broadcast(ServerLevel l, UUID g, Component message) {
            }

            @Override
            public void countdown(ServerLevel l, UUID g, int secondsLeft) {
            }

            @Override
            public void cleanup(ServerLevel l, UUID g) {
            }

            @Override
            public boolean asleep(ServerLevel l) {
                return false;
            }
        };
        long now = level.getGameTime();
        for (int limit : new int[] {3, 1, 0}) {
            setLimit(server, limit);
            GroupBossRoom room = new GroupBossRoom(group);
            inside.clear();
            inside.add(a);
            if (!room.start(level, host, inside)) {
                f.add("limit " + limit + ": the test room did not start");
                return;
            }
            room.tick(level, host);
            // a が倒れる: 目を覚ます前・20 秒より前は戻れない
            GroupBossRoom.noteDeath(a.getUUID());
            inside.remove(a);
            room.tick(level, host);
            if (room.rejoinable(level).contains(a.getUUID())) {
                f.add("limit " + limit + ": the door state right after a fall is wrong");
            }
            room.reentry().observeAwake(a.getUUID(), now - Reentry.WAIT_TICKS + 20);
            if (room.rejoinable(level).contains(a.getUUID())) {
                f.add("limit " + limit + ": the door opened before 20 s");
            }
            int returns = limit == 0 ? 8 : limit;
            for (int i = 0; i < returns; i++) {
                if (i > 0) {
                    GroupBossRoom.noteDeath(a.getUUID());
                    inside.remove(a);
                    room.tick(level, host);
                }
                // 目を覚ましてから 20 秒たった形にする
                room.reentry().fell(a.getUUID());
                room.reentry().observeAwake(a.getUUID(), now - Reentry.WAIT_TICKS);
                if (!room.rejoinable(level).contains(a.getUUID())) {
                    f.add("limit " + limit + ": return " + (i + 1) + " was refused after 20 s");
                    break;
                }
                inside.add(a);
                room.tick(level, host);
                if (room.reentry().isFallen(a.getUUID()) || room.reentry().used(a.getUUID()) != i + 1) {
                    f.add("limit " + limit + ": return " + (i + 1) + " was not counted");
                }
                if (!ReentryRules.guarded(a)) {
                    f.add("limit " + limit + ": no 3 s guard after return " + (i + 1));
                }
            }
            GroupBossRoom.noteDeath(a.getUUID());
            inside.remove(a);
            room.tick(level, host);
            room.reentry().observeAwake(a.getUUID(), now - 10 * Reentry.WAIT_TICKS);
            boolean open = room.rejoinable(level).contains(a.getUUID());
            if (limit == 0 ? !open : open) {
                f.add("limit " + limit + ": after the returns were used " + (open ? "the door still opened" : "the door stayed shut with no limit"));
            }
            if (!room.participants().contains(a.getUUID())) {
                f.add("limit " + limit + ": a fallen participant lost the participant record");
            }
            // 全滅 (中に生きた参加者がいない) で、やり直し。回数も戻る
            inside.clear();
            for (int i = 0; i < 8; i++) {
                room.tick(level, host);
            }
            if (room.active() || room.reentry().isFallen(a.getUUID()) || room.reentry().used(a.getUUID()) != 0) {
                f.add("limit " + limit + ": a wipe did not reset the fight and the return counts");
            }
        }
        if (boss[0] != null) {
            boss[0].discard();
        }
    }

}
