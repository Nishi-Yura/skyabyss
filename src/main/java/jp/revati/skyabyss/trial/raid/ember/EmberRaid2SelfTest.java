package jp.revati.skyabyss.trial.raid.ember;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼のレイドの二案め三つ (ER4 落ちる鎖の道・ER5 炉の圧合わせ・ER6 鍛え手の弟子たち) の自己テスト。SKYABYSS_SELFTEST_ONLY="ember raid"
//  建てて (閉じているか)、手順どおりに解いて越える。リセットで元に戻る。周の引きが枠ごとに二つのどちらにもなる。文 (日英) がある
final class EmberRaid2SelfTest {
    private static final int FLOOR = 150;
    private static final float HEALTH = 40f;

    private EmberRaid2SelfTest() {
    }

    static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("ember raid (rooms 2)", 30, EmberRaid2SelfTest::run));
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        List<FakePlayer> fakes = new ArrayList<>();
        EmberRaidSelfTest.load(server);
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            draw(f);
            StringBuilder dump = new StringBuilder("{");
            chain(level, f, fakes, dump);
            valves(level, f, fakes, dump);
            apprentices(level, f, fakes, dump);
            dump.append("}");
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("raid_ember2_dump.json"), dump.toString());
            } catch (java.io.IOException e) {
                f.add("dump write failed: " + e);
            }
        } catch (RuntimeException | ReflectiveOperationException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember raid (rooms 2) crashed", e);
            f.add("crashed: " + e);
        } finally {
            server.setDifficulty(before, true);
            for (FakePlayer p : fakes) {
                p.discard();
            }
            for (int cx = (2000 >> 4) - 3; cx <= (2000 >> 4) + 3; cx++) {
                for (int cz = (2000 >> 4) - 3; cz <= (2000 >> 4) + 3; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember raid (rooms 2) PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ember raid (rooms 2) FAILED: {}", m));
        }
    }

    private static void check(List<String> f, String what, boolean ok) {
        EmberRaidSelfTest.check(f, what, ok);
    }

    // 立つ位置を厳密に (高さも)
    private static void atExact(FakePlayer p, BlockPos pos) {
        p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        p.setOnGround(true);
        p.setDeltaMovement(0, 0, 0);
    }

    // ---- 周の引き・文
    private static void draw(List<String> f) {
        for (Slot s : Slot.values()) {
            List<String> ids = RaidRooms.idsOf(Titans.EMBER, s);
            check(f, "ember has two rooms in slot " + s + " (" + ids + ")", ids.size() == 2);
            Set<String> seen = new HashSet<>();
            for (long seed = 1; seed <= 60; seed++) {
                seen.add(RaidRooms.drawOf(Titans.EMBER, s, seed * 7919L));
            }
            check(f, "the draw picks either room in slot " + s + " (" + seen + ")", seen.size() == 2);
        }
        check(f, "ember is still an active raid", RaidRooms.active(Titans.EMBER));
        check(f, "registered ER4", RaidRooms.create(EmberChainPath.ID) instanceof EmberChainPath);
        check(f, "registered ER5", RaidRooms.create(EmberValves.ID) instanceof EmberValves);
        check(f, "registered ER6", jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(RaidRooms.create(EmberApprentices.ID)) instanceof EmberApprentices);
        for (String lang : new String[] {"ja_jp", "en_us"}) {
            var map = SignFit.lang(lang);
            for (String id : new String[] {"ER4", "ER5", "ER6"}) {
                for (String k : new String[] {"trial.skyabyss.cell." + id, "trial.skyabyss.cell." + id + ".desc"}) {
                    check(f, lang + " missing " + k, !map.getOrDefault(k, "").isEmpty());
                }
            }
            for (String k : new String[] {"raid.skyabyss.ER4.cross", "raid.skyabyss.ER5.align", "raid.skyabyss.ER5.done", "raid.skyabyss.ER6.fight",
                    "raid.skyabyss.ER6.apprentice", "raid.skyabyss.ER6.apprentice_hp", "raid.skyabyss.ER6.enter"}) {
                check(f, lang + " missing " + k, !map.getOrDefault(k, "").isEmpty());
            }
        }
    }

    // ---- ER4
    private static void chain(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberChainPath room = new EmberChainPath();
        RaidCtx ctx = EmberRaidSelfTest.newCtx(level, 41);
        room.build(ctx);
        EmberRaidSelfTest.sealed(level, ctx, f, "ER4");
        EmberRaidSelfTest.decor(f, room, "ER4");
        EmberRaidSelfTest.dumpPlan(level, ctx, "ER4", dump);
        FakePlayer a = EmberRaidSelfTest.fake(level, "a4");
        FakePlayer b = EmberRaidSelfTest.fake(level, "b4");
        fakes.add(a);
        fakes.add(b);
        room.testPlayers = List.of(a);
        room.start(ctx);
        check(f, "ER4 every plank is hung at the start", allPlanks(room, ctx, true));
        BlockPos[] isl = new BlockPos[4];
        for (int i = 0; i < 4; i++) {
            isl[i] = room.islandAt(ctx, i);
        }
        // 橋に乗ると落ち始める (赤い予告のあと板が落ちる)
        atExact(a, room.islandAt(ctx, 0));
        EmberRaidSelfTest.ticks(level, room, ctx, 2);
        check(f, "ER4 an unused bridge stays whole", room.spanState(0) == 0);
        atExact(a, room.spanHead(ctx, 0));
        EmberRaidSelfTest.ticks(level, room, ctx, 1);
        check(f, "ER4 stepping on the bridge starts it falling", room.spanState(0) == 1);
        atExact(a, isl[1]);
        EmberRaidSelfTest.ticks(level, room, ctx, 3);
        check(f, "ER4 the first plank turns hot (red warning) before it falls", level.getBlockState(ctx.local(0, 3, 11)).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && room.plankAt(ctx, 0, 0));
        EmberRaidSelfTest.ticks(level, room, ctx, EmberChainPath.PLANK_WARN + 2);
        check(f, "ER4 the first plank has dropped after the warning", !room.plankAt(ctx, 0, 0) && room.plankAt(ctx, 0, room.planks(0) - 1));
        EmberRaidSelfTest.ticks(level, room, ctx, room.planks(0) * EmberChainPath.PLANK_GAP + 5);
        check(f, "ER4 the whole bridge falls in sequence", room.spanState(0) >= 2 && !room.plankAt(ctx, 0, 0) && !room.plankAt(ctx, 0, room.planks(0) - 1));
        // 落ちた人は手前の島へ。傷なし
        float hp = a.getHealth();
        atExact(a, ctx.local(0, 1, 7));
        EmberRaidSelfTest.ticks(level, room, ctx, 1);
        check(f, "ER4 a faller is returned to the island behind", room.falls() == 1 && a.getY() > FLOOR + 3 && a.getZ() > ctx.origin().getZ() + 11);
        check(f, "ER4 falling costs no health", a.getHealth() >= hp);
        // 手前に人がいれば吊り直される
        EmberRaidSelfTest.ticks(level, room, ctx, EmberChainPath.REBUILD_WAIT + room.planks(0) * EmberChainPath.REBUILD_GAP + 10);
        check(f, "ER4 the fallen bridge is rehung while someone waits behind", allPlanksOf(room, ctx, 0, true) && room.spanState(0) == 0);
        // 一人で渡りきる
        room.reset(ctx);
        room.start(ctx);
        check(f, "ER4 reset rehangs every bridge", allPlanks(room, ctx, true) && !room.isComplete());
        for (int s = 0; s < room.spans(); s++) {
            atExact(a, room.spanHead(ctx, s));
            EmberRaidSelfTest.ticks(level, room, ctx, 4);
            atExact(a, isl[s + 1]);
            EmberRaidSelfTest.ticks(level, room, ctx, 2);
        }
        check(f, "ER4 not done on the last island", !room.isComplete());
        atExact(a, room.exitStep(ctx));
        EmberRaidSelfTest.ticks(level, room, ctx, 2);
        check(f, "ER4 solvable by one player (crossing before the planks drop)", room.isComplete() && room.counter() == room.counterMax());
        // 二人: 一人が渡っても、もう一人が渡るまで越えない。手前の人がいる限り橋は直る
        room.reset(ctx);
        room.start(ctx);
        room.testPlayers = List.of(a, b);
        atExact(b, ctx.local(0, 1, 22));
        for (int s = 0; s < room.spans(); s++) {
            atExact(a, room.spanHead(ctx, s));
            EmberRaidSelfTest.ticks(level, room, ctx, 4);
            atExact(a, isl[s + 1]);
            EmberRaidSelfTest.ticks(level, room, ctx, 2);
        }
        atExact(a, room.exitStep(ctx));
        EmberRaidSelfTest.ticks(level, room, ctx, 2);
        check(f, "ER4 a party is not done until everyone has crossed", !room.isComplete() && room.counter() == 1 && room.counterMax() == 2);
        EmberRaidSelfTest.ticks(level, room, ctx, 400);
        check(f, "ER4 the bridges are rehung for the one waiting behind", allPlanks(room, ctx, true));
        for (int s = 0; s < room.spans(); s++) {
            atExact(b, room.spanHead(ctx, s));
            EmberRaidSelfTest.ticks(level, room, ctx, 4);
            atExact(b, isl[s + 1]);
            EmberRaidSelfTest.ticks(level, room, ctx, 2);
        }
        atExact(b, room.exitStep(ctx));
        EmberRaidSelfTest.ticks(level, room, ctx, 2);
        check(f, "ER4 the party completes once both are across", room.isComplete());
        room.reset(ctx);
        check(f, "ER4 wipe resets the room", !room.isComplete() && allPlanks(room, ctx, true) && room.counter() == 0);
        check(f, "ER4 HUD key", !room.objectiveKey().isEmpty());
    }

    private static boolean allPlanks(EmberChainPath room, RaidCtx ctx, boolean present) {
        for (int s = 0; s < room.spans(); s++) {
            if (!allPlanksOf(room, ctx, s, present)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPlanksOf(EmberChainPath room, RaidCtx ctx, int s, boolean present) {
        for (int j = 0; j < room.planks(s); j++) {
            if (room.plankAt(ctx, s, j) != present) {
                return false;
            }
        }
        return true;
    }

    // ---- ER5
    private static void valves(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberValves room = new EmberValves();
        RaidCtx ctx = EmberRaidSelfTest.newCtx(level, 51);
        room.build(ctx);
        EmberRaidSelfTest.sealed(level, ctx, f, "ER5");
        EmberRaidSelfTest.decor(f, room, "ER5");
        EmberRaidSelfTest.dumpPlan(level, ctx, "ER5", dump);
        FakePlayer a = EmberRaidSelfTest.fake(level, "a5");
        fakes.add(a);
        room.testPlayers = List.of(a);
        room.start(ctx);
        // 毎回違う・必ず解ける・最初からはそろっていない
        Set<String> starts = new HashSet<>();
        for (int s = 0; s < 40; s++) {
            EmberValves r = new EmberValves();
            RaidCtx c = EmberRaidSelfTest.newCtx(level, 500 + s * 13L);
            r.build(c);
            int[] n = r.needles();
            starts.add(java.util.Arrays.toString(n));
            boolean eq = n[0] == n[1] && n[1] == n[2] && n[2] == n[3];
            int[] path = r.solve();
            if (eq || path == null) {
                f.add("ER5 seed " + (500 + s * 13L) + " is " + (eq ? "already aligned" : "unsolvable"));
            }
        }
        check(f, "ER5 the start differs between runs (" + starts.size() + ")", starts.size() >= 10);
        // 作り直して本番の部屋を解く
        room = new EmberValves();
        ctx = EmberRaidSelfTest.newCtx(level, 51);
        room.build(ctx);
        room.testPlayers = List.of(a);
        room.start(ctx);
        int[] s0 = room.start();
        for (int i = 0; i < 4; i++) {
            check(f, "ER5 gauge " + i + " shows its needle", level.getBlockState(room.needleBlock(ctx, i, s0[i])).is(net.minecraft.world.level.block.Blocks.SHROOMLIGHT));
        }
        // 右・左で逆に回る、隣も動く
        room.onUse(ctx, a, room.valvePos(ctx, 1), false);
        int[] n1 = room.needles();
        check(f, "ER5 turning a valve moves it and its neighbours",
                n1[1] == (s0[1] + 1) % 7 && n1[0] == Math.floorMod(s0[0] - 1, 7) && n1[2] == Math.floorMod(s0[2] - 1, 7) && n1[3] == s0[3]);
        room.onUse(ctx, a, room.valvePos(ctx, 1), true);
        check(f, "ER5 left-click undoes it", java.util.Arrays.equals(room.needles(), s0));
        check(f, "ER5 a block outside the valves is not an input", !room.onUse(ctx, a, ctx.local(0, 0, 0), false));
        int[] path = room.solve();
        check(f, "ER5 the start is solvable (" + (path == null ? -1 : path.length) + " turns)", path != null);
        if (path != null) {
            for (int i = 0; i < path.length; i++) {
                check(f, "ER5 not done before the last turn", !room.isComplete());
                room.onUse(ctx, a, room.valvePos(ctx, path[i]), false);
            }
        }
        check(f, "ER5 solved: the needles agree", room.isComplete());
        room.reset(ctx);
        room.start(ctx);
        check(f, "ER5 reset restores the start needles", !room.isComplete() && java.util.Arrays.equals(room.needles(), s0) && room.moves() == 0
                && level.getBlockState(room.needleBlock(ctx, 0, s0[0])).is(net.minecraft.world.level.block.Blocks.SHROOMLIGHT));
        check(f, "ER5 HUD key", !room.objectiveKey().isEmpty() && room.counterMax() == 4);
    }

    // ---- ER6
    private static void apprentices(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberApprentices room = new EmberApprentices();
        RaidCtx ctx = EmberRaidSelfTest.newCtx(level, 61);
        room.build(ctx);
        EmberRaidSelfTest.sealed(level, ctx, f, "ER6");
        EmberRaidSelfTest.decor(f, room, "ER6");
        EmberRaidSelfTest.dumpPlan(level, ctx, "ER6", dump);
        FakePlayer a = EmberRaidSelfTest.fake(level, "a6");
        fakes.add(a);
        room.testPlayers = List.of(a);
        room.start(ctx);
        check(f, "ER6 nobody at first", room.spawned() == 0 && ctx.aliveMobs().isEmpty());
        float prevHp = 0;
        for (int i = 0; i < EmberApprentices.COUNT; i++) {
            for (int k = 0; k < 400 && room.current() == null; k++) {
                EmberRaidSelfTest.ticks(level, room, ctx, 1);
            }
            Mob m = room.current();
            check(f, "ER6 apprentice " + (i + 1) + " appeared alone", m != null && room.spawned() == i + 1 && ctx.aliveMobs().size() == 1);
            if (m == null) {
                return;
            }
            check(f, "ER6 apprentice " + (i + 1) + " is stronger than the one before (" + m.getMaxHealth() + ")", m.getMaxHealth() > prevHp);
            prevHp = m.getMaxHealth();
            check(f, "ER6 apprentice " + (i + 1) + " is named", m.hasCustomName());
            check(f, "ER6 HUD shows the apprentice's health", room.secondaryMax() == 100 && room.secondary() > 0);
            // 技: 人を近くに置き (敵は動かさない)、使える技だけが出ることを見る
            m.setNoAi(true);
            Set<Integer> used = new HashSet<>();
            double mx = m.getX();
            double mz = m.getZ();
            boolean hurtSeen = false;
            for (int k = 0; k < 520; k++) {
                a.moveTo(mx + 3.5, FLOOR + 1, mz, 0, 0);
                a.setOnGround(true);
                a.invulnerableTime = 0;
                if (a.getHealth() < HEALTH - 0.5f) {
                    hurtSeen = true;
                }
                a.setHealth(HEALTH);
                EmberRaidSelfTest.ticks(level, room, ctx, 1);
                if (room.lastMove() >= 0) {
                    used.add(room.lastMove());
                }
            }
            check(f, "ER6 apprentice " + (i + 1) + " uses exactly " + (i + 1) + " moves (" + used + ")", used.size() == i + 1);
            check(f, "ER6 apprentice " + (i + 1) + " moves hurt after the red mark", hurtSeen);
            m.discard();
            EmberRaidSelfTest.ticks(level, room, ctx, 2);
            check(f, "ER6 defeat " + (i + 1) + " counted", room.defeated() == i + 1 && room.counter() == i + 1);
            if (i < EmberApprentices.COUNT - 1) {
                check(f, "ER6 the next one is not there yet", ctx.aliveMobs().isEmpty());
            }
        }
        check(f, "ER6 four defeated completes the room", room.isComplete());
        check(f, "ER6 the four lamps are lit", level.getBlockState(room.lampPos(ctx, 3)).is(net.minecraft.world.level.block.Blocks.SHROOMLIGHT));
        // 全滅 → リセット
        room.reset(ctx);
        room.start(ctx);
        check(f, "ER6 wipe resets: no apprentices, lamps dark, count zero", !room.isComplete() && room.defeated() == 0 && room.spawned() == 0 && ctx.aliveMobs().isEmpty()
                && level.getBlockState(room.lampPos(ctx, 0)).is(net.minecraft.world.level.block.Blocks.BLACKSTONE));
        // 人数で体力が増える (二人)
        FakePlayer c = EmberRaidSelfTest.fake(level, "c6");
        fakes.add(c);
        room.testPlayers = List.of(a, c);
        for (int k = 0; k < 200 && room.current() == null; k++) {
            EmberRaidSelfTest.ticks(level, room, ctx, 1);
        }
        check(f, "ER6 health scales with the party", room.current() != null && room.current().getMaxHealth() > 60f * 0.7f * 1.5f * (1 + (float) jp.revati.skyabyss.combat.MobTuning.HEALTH));
        ctx.clearMobs();
        check(f, "ER6 HUD key", !room.objectiveKey().isEmpty());
    }
}
