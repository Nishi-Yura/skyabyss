package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.FightBadgeData;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.FightBadges;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.ReentryRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 仲間が起こせる仕組み・倒れた回数の札・「ここ」の印の自己テスト (party revive)。docs/design/10 の 10.12〜10.14
//  - 倒れる → 起こす (3 秒・体力 3 割・短い守り・再入場に数えない) / 傷・離れるで中断
//  - 時間切れ → 死 → 再入場の記録 (倒れた回数は二重に数えない) / 仲間の全滅 / 一人用 / 別の次元の仲間 / ゲームルール false
//  - 札の数え方・最良の記録・勝ったときの記録
//  - 印: パーティの人にだけ・出しすぎの制限
// 偽のプレイヤーは ServerPlayer.die を動かさないので、死の知らせ (LivingDeathEvent) はテストが流す
public final class ReviveSelfTest {
    private ReviveSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        boolean rule = Downed.REVIVE == null || server.getGameRules().getBoolean(Downed.REVIVE);
        try {
            run(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST party revive crashed", e);
            f.add("crashed: " + e);
        } finally {
            Downed.testLookup = null;
            Downed.clearAll();
            Ping.clearForTest();
            if (Downed.REVIVE != null) {
                server.getGameRules().getRule(Downed.REVIVE).set(rule, server);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST party revive PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST party revive FAILED: {}", m));
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.setPos(0.5, 100, 0.5);
        return p;
    }

    private static void check(List<String> f, boolean ok, String message) {
        if (!ok) {
            f.add(message);
        }
    }

    private static void setRevive(MinecraftServer server, boolean on) {
        server.getGameRules().getRule(Downed.REVIVE).set(on, server);
    }

    private static void run(MinecraftServer server, List<String> f) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        ServerLevel over = server.overworld();
        if (back == null || Downed.REVIVE == null) {
            f.add("titan_back dimension or the revive game rule is missing");
            return;
        }
        check(f, server.getGameRules().getBoolean(Downed.REVIVE), "skyabyssRevive default is not true");
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        FakePlayer a = player(back, "rv_a");
        FakePlayer b = player(back, "rv_b");
        FakePlayer c = player(over, "rv_c");
        FakePlayer solo = player(back, "rv_solo");
        Map<UUID, ServerPlayer> online = new HashMap<>();
        for (FakePlayer p : List.of(a, b, c, solo)) {
            online.put(p.getUUID(), p);
        }
        Downed.testLookup = online::get;
        try {
            parties.clearAll();
            Downed.clearAll();
            setRevive(server, true);
            var generic = back.damageSources().generic();
            float[] dmg = new float[1];

            // ---- 一人用: 倒れずにそのまま (傷は変えない)
            dmg[0] = 100f;
            check(f, !Downed.tryDown(solo, 100f, generic, d -> dmg[0] = d) && dmg[0] == 100f && !Downed.isDowned(solo), "a solo player went down");

            // ---- パーティ a, b (背中) と c (地上)
            PartyData.Party party = parties.create(a.getUUID(), "rv_a");
            parties.add(party, b.getUUID(), "rv_b");
            parties.add(party, c.getUUID(), "rv_c");

            // ゲームルール false: 倒れずに普通の死
            setRevive(server, false);
            dmg[0] = 100f;
            check(f, !Downed.tryDown(a, 100f, generic, d -> dmg[0] = d) && !Downed.isDowned(a), "a player went down with skyabyssRevive off");
            setRevive(server, true);

            // 虚空・/kill のような無視できない傷は耐えない
            check(f, !Downed.tryDown(a, 100f, back.damageSources().genericKill(), d -> dmg[0] = d), "a bypassing damage source put a player down");

            // 傷が致命でなければ倒れない
            check(f, !Downed.tryDown(a, 5f, generic, d -> dmg[0] = d) && !Downed.isDowned(a), "a non-lethal hit put a player down");

            // ---- 倒れる → 起こす
            dmg[0] = 100f;
            check(f, Downed.tryDown(a, 100f, generic, d -> dmg[0] = d) && Downed.isDowned(a), "a lethal hit did not put a party player down");
            check(f, dmg[0] < a.getHealth() + a.getAbsorptionAmount(), "the lethal damage was not reduced to survivable (" + dmg[0] + ")");
            // 倒れている間は新しく倒れない・倒れた人は仲間として数えない
            check(f, !Downed.mayDown(a), "a downed player may go down again");
            check(f, !Downed.rescuers(b).contains(a), "a downed player counts as a rescuer");
            // 同期: 倒れた本人と仲間にだけ・パーティの外には送らない
            Map<UUID, List<DownedPackets.Sync>> got = new HashMap<>();
            Downed.clearSyncForTest();
            Downed.syncTick(server, List.of(a, b, c, solo), (p, s) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(s));
            check(f, got.containsKey(a.getUUID()) && got.containsKey(b.getUUID()) && got.containsKey(c.getUUID()) && !got.containsKey(solo.getUUID()),
                    "the downed list did not go exactly to the party");
            check(f, got.get(b.getUUID()) != null && got.get(b.getUUID()).get(0).entries().size() == 1
                    && got.get(b.getUUID()).get(0).entries().get(0).id().equals(a.getUUID().toString()), "the downed list content is wrong");
            got.clear();
            Downed.syncTick(server, List.of(a, b, c, solo), (p, s) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(s));
            check(f, got.isEmpty(), "the downed list was resent although nothing changed (spam)");

            // 遠い人・別の人の信号は受けない
            b.setPos(0.5, 100, 0.5);
            long now = back.getGameTime();
            check(f, !Downed.hold(solo, a.getUUID(), now), "a non-member could revive");
            check(f, !Downed.hold(a, a.getUUID(), now), "a downed player could revive themselves");
            b.setPos(50, 100, 50);
            check(f, !Downed.hold(b, a.getUUID(), now), "a far player could start reviving");
            b.setPos(0.5, 100, 0.5);

            // 中断: 傷を受けた
            check(f, Downed.hold(b, a.getUUID(), now), "a near party member could not start reviving");
            for (int i = 0; i < 10; i++) {
                Downed.hold(b, a.getUUID(), now);
                Downed.tick(server);
            }
            check(f, Downed.progress(a.getUUID()) == 10, "reviving did not progress (" + Downed.progress(a.getUUID()) + ")");
            b.hurtTime = 10;
            Downed.hold(b, a.getUUID(), now);
            Downed.tick(server);
            check(f, Downed.progress(a.getUUID()) == 0 && Downed.isDowned(a), "a hit did not interrupt reviving");
            b.hurtTime = 0;
            // 中断: 動いた
            for (int i = 0; i < 5; i++) {
                Downed.hold(b, a.getUUID(), now);
                Downed.tick(server);
            }
            b.setPos(0.5 + Downed.MOVE_LIMIT + 1.0, 100, 0.5);
            Downed.hold(b, a.getUUID(), now);
            Downed.tick(server);
            check(f, Downed.progress(a.getUUID()) == 0 && Downed.isDowned(a), "moving away did not interrupt reviving");
            // 中断: 押すのをやめた
            b.setPos(0.5, 100, 0.5);
            for (int i = 0; i < 5; i++) {
                Downed.hold(b, a.getUUID(), now);
                Downed.tick(server);
            }
            b.setPos(0.5, 100, 0.5);
            // 信号が途切れる (時刻が HOLD_GRACE を超えて古くなった形): hold の時刻を過去へ
            Downed.hold(b, a.getUUID(), now - Downed.HOLD_GRACE - 5);
            Downed.tick(server);
            check(f, Downed.progress(a.getUUID()) == 0, "releasing the button did not reset the progress");

            // 完了: 3 秒
            for (int i = 0; i < Downed.REVIVE_TICKS - 1; i++) {
                Downed.hold(b, a.getUUID(), now);
                Downed.tick(server);
            }
            check(f, Downed.isDowned(a), "revived before 3 s");
            Downed.hold(b, a.getUUID(), now);
            Downed.tick(server);
            check(f, !Downed.isDowned(a), "not revived after 3 s of holding");
            check(f, Math.abs(a.getHealth() - a.getMaxHealth() * Downed.REVIVE_HEALTH) < 0.6f, "revive health is not about 30% (" + a.getHealth() + ")");
            check(f, ReentryRules.guarded(a), "no brief protection after the revive");
            check(f, a.getForcedPose() == null, "the downed pose was kept after the revive");
            Downed.syncTick(server, List.of(a, b, c, solo), (p, s) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(s));
            check(f, got.get(a.getUUID()) != null && got.get(a.getUUID()).get(got.get(a.getUUID()).size() - 1).entries().isEmpty(),
                    "an empty downed list was not sent once the player was revived");

            // ---- 時間切れ → 死 → 再入場の記録 (戦いの部屋と一緒に)。倒れた回数は二重に数えない
            a.setHealth(a.getMaxHealth());
            ReentryRules.clearAll();
            Mob[] boss = new Mob[1];
            List<ServerPlayer> inside = new ArrayList<>(List.of(a, b));
            UUID group = Groups.keyOf(a);
            GroupBossRoom.Host host = new TestHost(inside, boss);
            GroupBossRoom room = new GroupBossRoom(group);
            setRevive(server, true);
            check(f, room.start(over, host, inside), "the test fight did not start");
            room.tick(over, host);
            dmg[0] = 100f;
            Downed.tryDown(a, 100f, generic, d -> dmg[0] = d);
            check(f, room.falls() == 1, "a down was not counted as a fall (" + room.falls() + ")");
            check(f, !room.reentry().isFallen(a.getUUID()), "a downed (not dead) player was recorded as fallen for re-entry");
            // 起こされたあとは、再入場の回数を使わない
            for (int i = 0; i < Downed.REVIVE_TICKS; i++) {
                Downed.hold(b, a.getUUID(), now);
                Downed.tick(server);
            }
            check(f, !Downed.isDowned(a) && room.reentry().used(a.getUUID()) == 0 && !room.reentry().isFallen(a.getUUID()),
                    "a revive was counted against the re-entry limit");
            // もう一度倒れ、誰も起こさず時間切れ
            a.setHealth(a.getMaxHealth());
            Downed.tryDown(a, 100f, generic, d -> dmg[0] = d);
            check(f, room.falls() == 2, "the second down was not counted");
            Downed.ageForTest(a.getUUID(), Downed.DOWN_TICKS);
            Downed.tick(server);
            check(f, !Downed.isDowned(a), "the timeout did not end the downed state");
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(a, a.damageSources().genericKill()));
            check(f, room.reentry().isFallen(a.getUUID()) && room.fallen().contains(a.getUUID()), "a timeout death did not record the re-entry (fallen)");
            check(f, room.falls() == 2, "a timeout death was counted twice (" + room.falls() + ")");
            // 倒れずに死んだ人 (一人用・ルール false・無視できない傷) は 1 回として数える
            inside.remove(a);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(b, b.damageSources().genericKill()));
            check(f, room.falls() == 3, "a plain death was not counted as a fall (" + room.falls() + ")");

            // 勝ったとき: 札を記録 (中ボスの種類。背中の次元の巨神)。3 回 = 札なし
            FightBadgeData badges = FightBadgeData.get(server);
            inside.clear();
            inside.add(b);
            Set<UUID> ids = room.finish(over, host, boss[0].getUUID());
            check(f, ids != null, "the test fight did not finish");
            // 札の記録: 背中の次元の巨神に、中ボス (HeartBoss でない) の戦いとして
            String titan = jp.revati.skyabyss.progress.Titans.titanOfBack(back.dimension());
            check(f, titan != null, "the titan back has no titan bound");
            if (titan != null) {
                FightBadges.onVictory(back, boss[0], Set.of(b.getUUID()), 3);
                int recorded = badges.best(b.getUUID(), titan, FightBadgeData.MID);
                check(f, recorded == 3, "the fight result was not recorded in the record data (" + recorded + ")");
                check(f, badges.best(b.getUUID(), titan, FightBadgeData.HEART) == -1, "a mid-boss result was recorded as a heart result");
                FightBadges.onVictory(back, boss[0], Set.of(b.getUUID()), 0);
                check(f, badges.best(b.getUUID(), titan, FightBadgeData.MID) == 0, "a better result (0 falls) was not recorded");
            }
            badges.forget(a.getUUID());
            badges.forget(b.getUUID());
            if (boss[0] != null) {
                boss[0].discard();
            }

            // ---- 仲間の全滅: 起こせる仲間がいなければ倒れない / 倒れたあとに仲間が全員いなくなったら、すぐ死ぬ
            Downed.clearAll();
            a.setHealth(a.getMaxHealth());
            b.setHealth(b.getMaxHealth());
            Downed.down(a);
            check(f, !Downed.mayDown(b), "the last standing member may go down (whole party would be stuck)");
            Downed.testLookup = id -> id.equals(b.getUUID()) ? null : online.get(id);
            Downed.tick(server);
            check(f, !Downed.isDowned(a), "a downed player with no rescuer left stayed downed");
            Downed.testLookup = online::get;
            a.setHealth(a.getMaxHealth());

            // 仲間が別の次元 (c だけ) なら、起こせる仲間にならない
            Downed.clearAll();
            parties.remove(b.getUUID());
            check(f, !Downed.mayDown(a), "a party member in another dimension counted as a rescuer");

            // ---- 札の数え方
            check(f, FightBadges.OATH.equals(FightBadges.badgeOf(0)), "0 falls is not the oath badge");
            check(f, FightBadges.STEADY.equals(FightBadges.badgeOf(1)) && FightBadges.STEADY.equals(FightBadges.badgeOf(2)), "1-2 falls is not the steady badge");
            check(f, FightBadges.badgeOf(3) == null && FightBadges.badgeOf(9) == null, "3+ falls got a badge");
            UUID pid = UUID.randomUUID();
            check(f, badges.best(pid, "stone_whale", FightBadgeData.HEART) == -1, "an unseen record is not -1");
            check(f, badges.record(pid, "stone_whale", FightBadgeData.HEART, 4) && badges.best(pid, "stone_whale", FightBadgeData.HEART) == 4,
                    "the first result was not recorded");
            check(f, badges.record(pid, "stone_whale", FightBadgeData.HEART, 1) && badges.best(pid, "stone_whale", FightBadgeData.HEART) == 1,
                    "a better result did not replace the best");
            check(f, !badges.record(pid, "stone_whale", FightBadgeData.HEART, 5) && badges.best(pid, "stone_whale", FightBadgeData.HEART) == 1,
                    "a worse result replaced the best");
            check(f, badges.best(pid, "stone_whale", FightBadgeData.MID) == -1 && badges.best(pid, "tide_shell", FightBadgeData.HEART) == -1,
                    "records of different titans / kinds leaked");
            badges.forget(pid);

            // ---- 「ここ」の印
            pingTest(server, f, back, parties, a, b, c, solo, online);
        } finally {
            parties.restore(snapshot, server.registryAccess());
            Downed.testLookup = null;
            Downed.clearAll();
        }
    }

    private static void pingTest(MinecraftServer server, List<String> f, ServerLevel back, PartyData parties, FakePlayer a, FakePlayer b, FakePlayer c,
            FakePlayer solo, Map<UUID, ServerPlayer> online) {
        parties.clearAll();
        Ping.clearForTest();
        PartyData.Party party = parties.create(a.getUUID(), "rv_a");
        parties.add(party, b.getUUID(), "rv_b");
        parties.add(party, c.getUUID(), "rv_c");
        Ping.Target target = new Ping.Target(new Vec3(10, 100, 10), -1, false);
        Map<UUID, List<Ping.Show>> got = new HashMap<>();
        java.util.function.BiConsumer<ServerPlayer, Ping.Show> sink = (p, s) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(s);
        // 送る: 本人と同じ次元の仲間 (b) にだけ。別の次元の仲間 (c)・パーティの外 (solo) には送らない
        check(f, Ping.send(a, target, 1000, online::get, sink), "a party ping was not sent");
        check(f, got.containsKey(a.getUUID()) && got.containsKey(b.getUUID()) && !got.containsKey(c.getUUID()) && !got.containsKey(solo.getUUID())
                && got.size() == 2, "the ping did not go exactly to the sender and same-dimension members: " + got.size());
        Ping.Show show = got.get(b.getUUID()) == null ? null : got.get(b.getUUID()).get(0);
        check(f, show != null && show.senderId().equals(a.getUUID().toString()) && show.name().equals("rv_a") && show.ttl() == Ping.TTL_TICKS
                && !show.enemy(), "the ping content is wrong");
        // 敵の印
        got.clear();
        check(f, Ping.send(a, new Ping.Target(new Vec3(1, 100, 1), 7, true), 1000 + Ping.COOLDOWN_TICKS, online::get, sink)
                && got.get(b.getUUID()).get(0).enemy() && got.get(b.getUUID()).get(0).entityId() == 7, "the enemy ping variant is wrong");
        // 出しすぎ: 同じ時刻・間隔が短い
        got.clear();
        check(f, !Ping.send(a, target, 1000 + Ping.COOLDOWN_TICKS, online::get, sink) && got.isEmpty(), "a ping inside the cooldown was sent");
        check(f, !Ping.send(a, target, 1000 + Ping.COOLDOWN_TICKS + 3, online::get, sink) && got.isEmpty(), "a ping 3 ticks later was sent");
        // 窓の中の回数: 5 回まで (すでに 2 回)
        long t = 1000 + Ping.COOLDOWN_TICKS;
        int sent = 2;
        for (int i = 0; i < 10; i++) {
            t += Ping.COOLDOWN_TICKS;
            if (Ping.send(a, target, t, online::get, sink)) {
                sent++;
            }
        }
        check(f, sent == Ping.MAX_PER_WINDOW, "the window limit was not " + Ping.MAX_PER_WINDOW + " pings (" + sent + ")");
        // 窓が過ぎれば、また出せる
        got.clear();
        check(f, Ping.send(a, target, t + Ping.WINDOW_TICKS, online::get, sink), "a ping after the window was refused");
        // 別の人の制限は別
        got.clear();
        check(f, Ping.send(b, target, t + Ping.WINDOW_TICKS, online::get, sink), "one player's limit blocked another player");
        // パーティの外・一人: 何も送らない
        got.clear();
        check(f, !Ping.send(solo, target, 5, online::get, sink) && got.isEmpty(), "a ping from outside any party was sent");
        parties.remove(b.getUUID());
        parties.remove(c.getUUID());
        Ping.clearForTest();
        check(f, !Ping.send(a, target, 9, online::get, sink) && got.isEmpty(), "a ping from a one-person party was sent");
    }

    private static final class TestHost implements GroupBossRoom.Host {
        private final List<ServerPlayer> inside;
        private final Mob[] boss;

        TestHost(List<ServerPlayer> inside, Mob[] boss) {
            this.inside = inside;
            this.boss = boss;
        }

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
    }
}
