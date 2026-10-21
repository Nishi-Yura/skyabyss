package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.LangSelfTest;
import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 順路を一人で通す自己テスト (docs/design/32 の 8.2 の R6。段の名前は "trial walk")。四体の背が建ててあれば、
//   入口の箱 (床がある・暗すぎない) → 段 1 (歩) → 段 2 (智) → 段 3 (闘) → (中ボス) → 心臓の前室 を、初回と周回 (祠から) で一人の偽の人が通る。
//   各段で見ること:
//   - 運ばれた先が立てる所 (足と頭が空いていて、足もとが固い)
//   - 目的の帯の中身 (RouteState) が今の段の試練を指し、その文 (目的の帯の行・試練の名前・枠・中ボスの名前) が ja にある
//   - その試練の範囲の中を、入口の休み場から石 (新作は石の差し口) まで歩いて着く (背の監査と同じ歩き方。仕掛けの戸は開いたものとして)
//   周回は 2〜5 周 (引いた新作のセル・中ボスの段を含む)。周回で長くなる原型 (RouteLength) の入口も同じように見る
public final class RouteWalkSelfTest {
    private RouteWalkSelfTest() {
    }

    public static List<Step> steps() {
        // 明るさは、背を建てた直後は光の計算が済んでいないので、少し待ってから見る
        return List.of(Step.once("trial walk", 10, RouteWalkSelfTest::run), Step.once("trial walk (light)", 200, RouteWalkSelfTest::light));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static int reached;
    private static int landings;
    private static int longWalks;

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int walked = 0;
        reached = 0;
        landings = 0;
        longWalks = 0;
        Map<String, String> ja;
        try {
            ja = LangSelfTest.table("ja_jp");
        } catch (Exception e) {
            ja = Map.of();
            f.add("could not read the ja lang table: " + e);
        }
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                // 背がまだなら建てる (自己テストの起動では背を建てないため)
                jp.revati.skyabyss.dev.StructureDump.ensureBack(server, switch (titan) {
                    case Titans.STONE_WHALE -> "titan_back";
                    case Titans.TIDE_SHELL -> "tide_back";
                    case Titans.THUNDER_WING -> "storm_back";
                    default -> "ember_back";
                });
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null || !built(back, titan)) {
                    SkyAbyss.LOGGER.info("SELFTEST trial walk: {} back is not built here, skipped", titan);
                    continue;
                }
                entryBox(f, back, titan);
                walk(f, ja, back, titan, false);
                walk(f, ja, back, titan, true);
                walked++;
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial walk crashed", e);
            f.add("crashed: " + e);
        } finally {
            TrialRoute.clearAll();
            TrialSlot.clearAll();
            RouteLength.force = null;
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
        if (walked == 0) {
            f.add("no titan back was built (the walk needs the backs; run with the backs built)");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial walk PASSED ({} titan(s): first run and replays, {} landings on floors, {} trials walked from their entry to the stone, {} long walks)",
                    walked, landings, reached, longWalks);
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial walk FAILED: {}", s);
            }
        }
    }

    private static boolean built(ServerLevel back, String titan) {
        TrialDraw.Draw first = first(titan);
        String rest = TrialRoute.restOf(titan, first, 1, false);
        BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
        if (at == null) {
            return false;
        }
        back.getChunk(at);
        return !back.getBlockState(at.below()).isAir();
    }

    private static TrialDraw.Draw first(String titan) {
        List<TrialDraw.Pick> picks = new ArrayList<>();
        for (Def d : TrialCatalog.originals(titan)) {
            picks.add(new TrialDraw.Pick(d.id(), Trial.Mods.PLAIN));
        }
        return new TrialDraw.Draw(1, picks);
    }

    // 立てる所: 足と頭に当たりがなく液体でもなく、足もとに当たりがある (段・半ブロックも床)
    static boolean standable(ServerLevel back, BlockPos feet) {
        back.getChunk(feet);
        BlockPos below = feet.below();
        return back.getBlockState(feet).getCollisionShape(back, feet).isEmpty() && back.getBlockState(feet).getFluidState().isEmpty()
                && back.getBlockState(feet.above()).getCollisionShape(back, feet.above()).isEmpty()
                && !back.getBlockState(below).getCollisionShape(back, below).isEmpty();
    }

    // 歩いて石へ着くかを見ない原型 (仕掛けが床や水を動かして初めて届く部屋。動きは各背の自己テストが見る)。新作のセルは trial pool のセルの監査が見る
    static final java.util.Set<String> MOVING = java.util.Set.of(
            // 潮見の間: 溜めの水位を変えて泳いで上がる
            "T05");

    // ---------------------------------------------------------------- 入口の箱
    private static void entryBox(List<String> f, ServerLevel back, String titan) {
        AABB box = TrialRoute.entryBox(titan);
        if (box == null) {
            f.add(titan + ": no entry box");
            return;
        }
        int cx = (int) Math.floor(box.getCenter().x);
        int cz = (int) Math.floor(box.getCenter().z);
        BlockPos feet = null;
        for (int y = (int) Math.floor(box.minY); y < (int) Math.ceil(box.maxY); y++) {
            BlockPos p = new BlockPos(cx, y, cz);
            if (standable(back, p)) {
                feet = p;
                break;
            }
        }
        check(f, feet != null, titan + ": the entry box " + box + " has no floor in its middle column");
    }

    // 入口の箱が暗すぎない (真っ暗だと予告の文字しか見えない。R6 の撮影の余燼)。箱のまんなかの列の、立てる所の目の高さで、空の光か灯りが 7 以上
    private static void light(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
            ServerLevel back = server.getLevel(Titans.backOf(titan));
            AABB box = TrialRoute.entryBox(titan);
            if (back == null || box == null || !built(back, titan)) {
                continue;
            }
            int cx = (int) Math.floor(box.getCenter().x);
            int cz = (int) Math.floor(box.getCenter().z);
            for (int y = (int) Math.floor(box.minY); y < (int) Math.ceil(box.maxY); y++) {
                BlockPos p = new BlockPos(cx, y, cz);
                if (standable(back, p)) {
                    int light = back.getMaxLocalRawBrightness(p.above());
                    check(f, light >= 7, titan + ": the entry box is too dark (light " + light + " at " + p.above().toShortString() + ")");
                    break;
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial walk (light) PASSED (the four entry boxes are lit)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial walk (light) FAILED: {}", s);
            }
        }
    }

    // ---------------------------------------------------------------- 通し
    private static void walk(List<String> f, Map<String, String> ja, ServerLevel back, String titan, boolean replay) {
        TrialRoute.clearAll();
        FakePlayer a = FakePlayerFactory.get(back, new GameProfile(UUID.fromString(replay ? "7a0b1d00-0000-4000-8000-0000000e0002" : "7a0b1d00-0000-4000-8000-0000000e0001"),
                replay ? "walk_replay" : "walk_first"));
        UUID g = Groups.keyOf(a);
        List<ServerPlayer> players = List.of(a);
        TrialRoute.PLAYERS = l -> players;
        long now = back.getGameTime();
        try {
            int cycles = replay ? 4 : 1;
            for (int c = 0; c < cycles; c++) {
                int cycle = replay ? 2 + c : 1;
                clean(back, g);
                BackGroupData.Record r = GroupProgress.of(back, g);
                if (replay) {
                    r.markFirstCleared();
                    r.putInt(TrialData.DONE, cycle - 1);
                    r.remove(TrialData.DRAW);
                    if (c == 0) {
                        // 一周目の周回は、歩を原型にして前の道を通る (闘は中ボスの段を通るもの)
                        r.putString(TrialData.DRAW, TrialRoute.walkOriginalDraw(titan, TrialData.current(back, g)).encode());
                    }
                }
                // 初回は入口の箱、周回は祠から (どちらも始めると段 1 の入口の休み場へ運ばれる)
                if (replay) {
                    // 祠と同じ始め方: 区画の写しは tick ごとに進み、段 1 の写しが済んでから運ぶ (ここでは待ちの上限を過ぎた tick で、その場で写し終える)
                    TrialRoute.startSoon(back, g, titan, List.of(a));
                    TrialRoute.tick(back, titan, players, now + TrialRoute.COPY_WAIT + 1);
                } else {
                    TrialRoute.start(back, g, titan, List.of(a));
                }
                TrialDraw.Draw draw = TrialData.current(back, g);
                String tag = titan + (replay ? " replay cycle " + cycle : " first run") + " [" + draw.encode() + "]";
                check(f, draw.cycle() == cycle, tag + ": the draw is on cycle " + draw.cycle());
                for (int step = 1; step <= 3; step++) {
                    Def d = TrialRoute.defOf(draw, step);
                    String st = tag + " step " + step + " (" + (d == null ? "-" : d.id()) + ")";
                    landed(f, back, a, st);
                    band(f, ja, back, g, titan, step, false, d, st);
                    // 周回で長くなる歩の原型: 前の道の休み場へ運ばれ、前の道の出口の戸がこのグループに開いている。ほかの段では閉じている
                    LongWalk.Pre pre = LongWalk.of(titan);
                    if (pre != null) {
                        boolean want = replay && d != null && d.id().equals(pre.def());
                        boolean open = GroupGates.isOpen(back, g, pre.gate());
                        check(f, open == want, st + ": the gate of the walk before " + pre.def() + " is " + (open ? "open" : "closed"));
                        if (want) {
                            BlockPos rest = TrialRoute.spawnOf(back, titan, pre.rest());
                            check(f, rest != null && a.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(rest)) < 2.5,
                                    st + ": not carried to the rest of the walk before (" + pre.rest() + "): " + a.position());
                            longWalks++;
                        }
                    }
                    if (d != null && d.original() && !MOVING.contains(d.id())) {
                        reach(f, back, titan, g, draw, step, d, a, st);
                    }
                    TrialRoute.onCleared(back, g, titan, TrialRoute.keyOf(titan, draw, step), null);
                    TrialRoute.flushPending();
                    TrialRoute.tick(back, titan, players, now + 1);
                }
                if (TrialRoute.mid(back, g)) {
                    landed(f, back, a, tag + " mid-boss");
                    band(f, ja, back, g, titan, 3, true, null, tag + " mid-boss");
                    TrialRoute.onMidBossDefeated(back, g, titan);
                    TrialRoute.flushPending();
                    TrialRoute.tick(back, titan, players, now + 1);
                }
                check(f, TrialRoute.step(back, g) == TrialRoute.LAST_STEP, tag + ": the route did not reach the heart (step " + TrialRoute.step(back, g) + ")");
                landed(f, back, a, tag + " heart");
                band(f, ja, back, g, titan, TrialRoute.LAST_STEP, false, null, tag + " heart");
            }
        } finally {
            clean(back, g);
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
    }

    private static void clean(ServerLevel back, UUID g) {
        TrialRoute.finish(back, g);
        TrialRuntime.stopGroup(back, g);
        GroupGates.reset(back, g);
        BackGroupData.get(back).remove(g);
    }

    // 運ばれた先 (偽の人の位置) に立てる
    private static void landed(List<String> f, ServerLevel back, FakePlayer a, String what) {
        BlockPos feet = BlockPos.containing(a.getX(), a.getY() + 0.01, a.getZ());
        if (standable(back, feet)) {
            landings++;
        } else {
            f.add(what + ": carried to " + feet.toShortString() + " which is not a floor to stand on ("
                    + back.getBlockState(feet.below()).getBlock().getDescriptionId() + " below)");
        }
    }

    // 目的の帯の中身と文
    private static void band(List<String> f, Map<String, String> ja, ServerLevel back, UUID g, String titan, int step, boolean mid, Def d, String what) {
        TrialBoardNet.RouteState s = TrialRoute.routeState(back, g, titan);
        check(f, s.step() == step && s.mid() == mid, what + ": the band says step " + s.step() + (s.mid() ? " (mid)" : ""));
        if (mid) {
            String key = "trial.skyabyss.midboss." + TrialRegions.signalTitan(titan);
            check(f, ja.containsKey(key) && ja.containsKey("objective.skyabyss.route_mid"), what + ": no text for the mid-boss band (" + key + ")");
        } else if (step >= TrialRoute.LAST_STEP) {
            check(f, ja.containsKey("objective.skyabyss.route_heart"), what + ": no text for the heart band");
        } else {
            check(f, d != null && d.id().equals(s.cellId()) && d.slot().id.equals(s.slot()), what + ": the band names " + s.cellId() + "/" + s.slot());
            check(f, ja.containsKey("objective.skyabyss.route_step" + step) && ja.containsKey("trial.skyabyss.cell." + s.cellId())
                    && ja.containsKey("trial.skyabyss.slot." + s.slot()), what + ": the band text is missing (cell " + s.cellId() + ", slot " + s.slot() + ")");
        }
    }

    // 入口の休み場 (区画の写しなら手本の休み場) から、その試練の範囲の中を歩いて石へ着く
    private static void reach(List<String> f, ServerLevel back, String titan, UUID g, TrialDraw.Draw draw, int step, Def d, FakePlayer a, String what) {
        String key = TrialRoute.keyOf(titan, draw, step);
        String rest = TrialRoute.restOf(titan, draw, step, false);
        BlockPos from = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
        // 周回で長くなる原型は、前の道の休み場から (RouteLength。運ばれた先と同じ)
        BlockPos carried = TrialRoute.frameOfStep(back, g, draw, step, false).isZero() ? BlockPos.containing(a.position()) : null;
        if (carried != null) {
            from = carried;
        }
        BlockPos goal;
        TrialCell cell = d.original() ? null : TrialCells.get(d.id());
        if (cell != null && cell.ports().stone() != null) {
            goal = cell.ports().stone();
        } else {
            goal = TrialRegions.stoneOf(titan, key);
        }
        if (from == null || goal == null) {
            f.add(what + ": no entry (" + from + ") or stone (" + goal + ") to walk between");
            return;
        }
        List<AABB> regions = TrialRegions.of(back, titan, key, g);
        AABB box = null;
        for (AABB b : regions) {
            box = box == null ? b : box.minmax(b);
        }
        if (box == null) {
            f.add(what + ": no region");
            return;
        }
        box = box.minmax(new AABB(from)).minmax(new AABB(goal)).inflate(12, 8, 12);
        BackReach.Spec base = specOf(back, titan);
        if (base == null) {
            return;
        }
        BackReach.Box bounds = new BackReach.Box((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.ceil(box.maxX), (int) Math.ceil(box.maxY), (int) Math.ceil(box.maxZ));
        BackReach.Spec spec = base.narrowed(what, bounds, from, Map.of("stone", goal));
        BackReach.Report rep = BackReach.reachGoals(back, spec);
        if (rep.failures.isEmpty()) {
            reached++;
        } else {
            for (String s : rep.failures) {
                f.add(what + ": " + s);
            }
        }
    }

    private static BackReach.Spec specOf(ServerLevel back, String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.dungeon.TitanBackAudit.spec();
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackAudit.spec();
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackAudit.spec(back);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackAudit.spec(back);
            default -> null;
        };
    }
}
