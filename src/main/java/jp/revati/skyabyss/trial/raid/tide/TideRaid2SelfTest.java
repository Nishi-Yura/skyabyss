package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidLore;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 潮殻のレイドの新しい四部屋 (TR4 伸びる珊瑚の道・TR5 光を返す真珠・TR6 潮見の暦・TR7 本物の群れの母) と、枠ごとの引きの自己テスト。SKYABYSS_SELFTEST_ONLY="tide raid"
// 部屋を RaidCtx.forTest で直接建て、台本で解く (人は偽物)。全滅のやり直し (reset → start)・予告・押さえ・見分け・文言も確かめる。
// 順路を通した確認 (引かれた部屋が RaidRun で動く) は TideRaidSelfTest
public final class TideRaid2SelfTest {
    private TideRaid2SelfTest() {
    }

    private static final int LIMIT = 1800;
    private static final int SLOT = 5;
    private static final List<String> F = new ArrayList<>();
    private static ServerLevel back;
    private static UUID group;
    private static FakePlayer a;
    private static FakePlayer b;
    private static PartyData parties;
    private static CompoundTag snapshot;
    private static int stage;
    private static int phase;
    private static int ticks;
    private static RaidCtx ctx;
    private static RaidRoom room;
    private static final List<long[]> FORCED = new ArrayList<>();
    // TR4 の観察
    private static boolean sawTelegraph;
    private static boolean sawClosed;
    private static int holdRow;
    private static int holdLane;

    public static List<Step> steps() {
        return List.of(Step.once("tide raid2 (setup)", TideRaid2SelfTest::setup), Step.until("tide raid2 rooms", TideRaid2SelfTest::run),
                Step.once("tide raid2 (end)", TideRaid2SelfTest::end));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    private static FakePlayer fake(String id, String name) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString(id), name));
        p.setInvulnerable(true);
        return p;
    }

    private static void setup(MinecraftServer server) {
        F.clear();
        stage = 0;
        phase = 0;
        ticks = 0;
        ctx = null;
        room = null;
        try {
            jp.revati.skyabyss.dev.StructureDump.ensureBack(server, "tide_back");
            back = server.getLevel(Titans.backOf(Titans.TIDE_SHELL));
            if (back == null) {
                F.add("the tide back is missing");
                return;
            }
            parties = PartyData.get(server);
            snapshot = parties.snapshot(server.registryAccess());
            parties.clearAll();
            a = fake("7a0b1d00-0000-4000-8000-0000000007c1", "tide2_a");
            b = fake("7a0b1d00-0000-4000-8000-0000000007c2", "tide2_b");
            PartyData.Party party = parties.create(a.getUUID(), "tide2_a");
            parties.add(party, b.getUUID(), "tide2_b");
            group = Groups.keyOf(a);
            TrialRoute.clearAll();
            List<ServerPlayer> both = List.of(a, b);
            TrialRoute.PLAYERS = l -> both;
            a.setPos(0, 70, 0);
            b.setPos(0, 70, 0);
            draws();
            texts();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide raid2 setup crashed", e);
            F.add("setup crashed: " + e);
        }
    }

    // 枠の引き: 登録した部屋だけ、どれも出る。TR2 は出ない。同じ種なら同じ
    private static void draws() {
        String t = Titans.TIDE_SHELL;
        check(RaidRooms.active(t), "the tide shell is not on a raid route");
        check(RaidRooms.idsOf(t, Trial.Slot.WALK).equals(List.of("TR1", "TR4")), "walk rooms: " + RaidRooms.idsOf(t, Trial.Slot.WALK));
        check(RaidRooms.idsOf(t, Trial.Slot.WIT).equals(List.of("TR5", "TR6")), "wit rooms: " + RaidRooms.idsOf(t, Trial.Slot.WIT));
        check(RaidRooms.idsOf(t, Trial.Slot.FIGHT).equals(List.of("TR3", "TR7")), "fight rooms: " + RaidRooms.idsOf(t, Trial.Slot.FIGHT));
        check(!RaidRooms.isRaid("TR2") && RaidRooms.create("TR2") == null, "TR2 is still registered");
        check(!RaidLore.SIGNS.containsKey("TR2"), "the TR2 inscription mapping is still in RaidLore");
        for (Trial.Slot s : Trial.Slot.values()) {
            Set<String> met = new HashSet<>();
            for (long seed = 1; seed <= 300; seed++) {
                String id = RaidRooms.drawOf(t, s, seed * 7919L);
                check(id != null && RaidRooms.idsOf(t, s).contains(id), "draw picked an unregistered room " + id + " for " + s);
                check(java.util.Objects.equals(id, RaidRooms.drawOf(t, s, seed * 7919L)), "the draw is not deterministic");
                met.add(id);
            }
            check(met.size() == RaidRooms.idsOf(t, s).size(), "slot " + s + " drew only " + met + " of " + RaidRooms.idsOf(t, s));
        }
        // 路の周ごとの引き (TrialCatalog.raidDraw) にも TR2 は出ず、枠ごとに一つ
        for (long seed = 1; seed <= 200; seed++) {
            List<Trial.Def> d = TrialCatalog.raidDraw(t, seed * 104729L);
            check(d.size() == 3, "a raid draw is not three rooms");
            for (Trial.Def x : d) {
                check(!x.id().equals("TR2"), "TR2 was drawn");
            }
        }
        TrialDraw.Memory mem = new TrialDraw.Memory();
        TrialDraw.Draw first = TrialDraw.draw(t, 1, 4242L, new UUID(9, 9), mem);
        check(first.picks().size() == 3 && first.picks().stream().noneMatch(p -> p.cellId().equals("TR2")), "a cycle-1 draw has TR2 or is not three");
    }

    // 文言: 部屋の名前・説明・目的・メッセージ・碑・心の声 (日本語と英語)
    private static void texts() {
        // 碑の各行が、看板の幅 (90 px) に収まる
        List<String> signKeys = new ArrayList<>();
        for (String id : List.of("TR4", "TR5", "TR6", "TR7")) {
            signKeys.add(RaidLore.SIGNS.get(id));
        }
        for (String f : SignFit.check(signKeys)) {
            F.add(f);
        }
        for (String lg : List.of("ja_jp", "en_us")) {
            var lang = SignFit.lang(lg);
            for (String id : List.of("TR4", "TR5", "TR6", "TR7")) {
                for (String k : List.of("trial.skyabyss.cell." + id, "trial.skyabyss.cell." + id + ".desc", "raid.skyabyss." + id + ".%s")) {
                    if (k.endsWith(".%s")) {
                        continue;
                    }
                    check(!lang.getOrDefault(k, "").isEmpty(), lg + " lacks " + k);
                }
                String sign = RaidLore.SIGNS.get(id);
                check(sign != null, id + " has no inscription mapping");
                for (int n = 1; n <= 2; n++) {
                    check(!lang.getOrDefault("message.skyabyss.raid_lore." + id + "." + n, "").isEmpty(), lg + " lacks the inner voice " + id + "." + n);
                }
                if (sign != null) {
                    for (int n = 1; n <= 4; n++) {
                        check(!lang.getOrDefault(sign + "." + n, "").isEmpty(), lg + " lacks " + sign + "." + n);
                    }
                }
            }
            for (String k : List.of("raid.skyabyss.TR4.cross", "raid.skyabyss.TR5.guide", "raid.skyabyss.TR6.arrange", "raid.skyabyss.TR7.find", "raid.skyabyss.TR7.mother",
                    "message.skyabyss.raid.TR5.lit", "message.skyabyss.raid.TR6.aligned", "message.skyabyss.raid.TR7.decoy")) {
                check(!lang.getOrDefault(k, "").isEmpty(), lg + " lacks " + k);
            }
        }
    }

    private static void end(MinecraftServer server) {
        try {
            finishRoom();
            unforce();
        } finally {
            TrialRoute.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            if (parties != null && snapshot != null) {
                parties.restore(snapshot, server.registryAccess());
            }
        }
        if (stage < 4) {
            F.add("the script did not finish (stage " + stage + ", phase " + phase + ")");
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide raid2 PASSED (draw among registered rooms per slot, TR2 never drawn; TR4 telegraph/hold/at-least-one-open/cross/reset; "
                    + "TR5 per-run solvable layouts, rotate, reset, beam lit; TR6 mural = dials' target, reset, solve; TR7 decoy pops, mother kill, reset)");
        } else {
            F.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST tide raid2 FAILED: {}", s));
        }
    }

    // ---------------------------------------------------------------- 道具
    private static void force(RaidCtx c) {
        net.minecraft.world.phys.AABB box = c.cell().bounds();
        for (int cx = ((int) Math.floor(box.minX)) >> 4; cx <= ((int) Math.ceil(box.maxX)) >> 4; cx++) {
            for (int cz = ((int) Math.floor(box.minZ)) >> 4; cz <= ((int) Math.ceil(box.maxZ)) >> 4; cz++) {
                back.setChunkForced(cx, cz, true);
                FORCED.add(new long[] {cx, cz});
            }
        }
    }

    private static void unforce() {
        if (back != null) {
            for (long[] p : FORCED) {
                back.setChunkForced((int) p[0], (int) p[1], false);
            }
        }
        FORCED.clear();
    }

    private static void put(FakePlayer p, BlockPos at) {
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.setHealth(p.getMaxHealth());
    }

    private static void begin(String id, int step, long seed) {
        finishRoom();
        ctx = RaidCtx.forTest(back, group, Titans.TIDE_SHELL, id, SLOT, step, seed);
        room = RaidRooms.create(id);
        room.build(ctx);
        check(ctx.cell() != null, id + ": no cell");
        force(ctx);
        put(a, ctx.cell().rest());
        put(b, ctx.cell().rest());
        room.start(ctx);
        phase = 0;
        ticks = 0;
    }

    private static void finishRoom() {
        if (ctx != null && room != null && ctx.cell() != null) {
            try {
                room.end(ctx);
            } catch (RuntimeException ignored) {
                // 片付けの失敗は数えない
            }
            ctx.clearMobs();
            RaidKit.clear(back, ctx.cell().bounds());
        }
        ctx = null;
        room = null;
    }

    private static void next() {
        phase++;
        ticks = 0;
    }

    private static boolean timedOut(String what) {
        if (++ticks > LIMIT) {
            F.add(what + ": timed out in phase " + phase);
            return true;
        }
        return false;
    }

    private static void advance() {
        finishRoom();
        stage++;
        phase = 0;
        ticks = 0;
    }

    private static boolean run(MinecraftServer server) {
        if (back == null || group == null) {
            return true;
        }
        try {
            boolean done = switch (stage) {
                case 0 -> tr4();
                case 1 -> tr5();
                case 2 -> tr6();
                case 3 -> tr7();
                default -> true;
            };
            if (done && stage < 4) {
                advance();
            }
            return stage >= 4;
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide raid2 crashed in stage " + stage, e);
            F.add("crashed in stage " + stage + ": " + e);
            stage = 4;
            return true;
        }
    }

    // ---------------------------------------------------------------- TR4 伸びる珊瑚の道
    private static boolean tr4() {
        if (room == null) {
            begin("TR4", 1, 41L);
            sawTelegraph = false;
            sawClosed = false;
            return false;
        }
        TideCoralRoadRoom r = (TideCoralRoadRoom) room;
        r.tick(ctx);
        // どの瞬間でも、どの壁にも完全に開いた戸が一つ以上。閉じた戸の柱は塞がっている。予告は塞がる前に出る
        for (int row = 0; row < TideCoralRoadRoom.ROWS; row++) {
            int open = 0;
            for (int lane = 0; lane < TideCoralRoadRoom.LANES; lane++) {
                if (r.fullyOpen(row, lane)) {
                    open++;
                }
                int s = r.shownColumns(row, lane);
                for (int k = 0; k < 3; k++) {
                    boolean solid = r.solidAt(ctx, row, lane, k);
                    if (k < s && !solid) {
                        F.add("TR4: a grown column is not solid (row " + row + " lane " + lane + " k " + k + ")");
                    }
                    if (k >= s && solid) {
                        F.add("TR4: a retracted column is solid (row " + row + " lane " + lane + " k " + k + ")");
                    }
                    if (k >= s && r.carpetAt(ctx, row, lane, k)) {
                        sawTelegraph = true;
                        check(!solid, "TR4: the telegraph carpet sits on a solid column");
                    }
                }
                if (s == 3) {
                    sawClosed = true;
                }
            }
            check(open >= 1, "TR4: row " + row + " has no open gate at tick " + ticks);
        }
        switch (phase) {
            case 0 -> {
                check(r.counterMax() == TideCoralRoadRoom.ROWS && r.counter() == 0, "TR4: counter should start at 0/" + TideCoralRoadRoom.ROWS);
                check(!r.isComplete(), "TR4: complete at the start");
                check(!r.objectiveKey().isEmpty(), "TR4: empty objective");
                // 押さえ: 行 0 で、いちばん早く閉じ始める戸を探し、その最初の柱に立つ
                int best = Integer.MAX_VALUE;
                for (int lane = 0; lane < TideCoralRoadRoom.LANES; lane++) {
                    int p = r.phase(ctx, 0, lane);
                    int wait = Math.floorMod(TideCoralRoadRoom.CLOSE_AT - p, TideCoralRoadRoom.CYCLE);
                    if (wait < best) {
                        best = wait;
                        holdLane = lane;
                    }
                }
                holdRow = 0;
                next();
            }
            case 1 -> {
                // 閉じ始める前に戸の最初の柱へ入り、閉じる時刻を過ぎても伸びないことを見る
                BlockPos col = r.nextColumn(holdRow, holdLane);
                put(a, col);
                if (r.phase(ctx, holdRow, holdLane) >= TideCoralRoadRoom.CLOSE_AT + 40 && r.phase(ctx, holdRow, holdLane) < TideCoralRoadRoom.CLOSE_AT + 80) {
                    check(r.shownColumns(holdRow, holdLane) == 0, "TR4: the held gate closed anyway (" + r.shownColumns(holdRow, holdLane) + ")");
                    next();
                } else if (timedOut("TR4 hold")) {
                    return true;
                }
            }
            case 2 -> {
                // 放す (入口へ戻す) → 伸びる
                put(a, ctx.cell().rest());
                next();
            }
            case 3 -> {
                if (r.shownColumns(holdRow, holdLane) >= 1) {
                    next();
                } else if (timedOut("TR4 release")) {
                    return true;
                }
            }
            case 4 -> {
                check(sawTelegraph, "TR4: no telegraph carpet was ever seen before a gate closed");
                check(sawClosed, "TR4: no gate was ever fully closed");
                // 全滅 → 戸が全部開く (reset → start)
                room.reset(ctx);
                for (int row = 0; row < TideCoralRoadRoom.ROWS; row++) {
                    for (int lane = 0; lane < TideCoralRoadRoom.LANES; lane++) {
                        check(r.shownColumns(row, lane) == 0 && !r.solidAt(ctx, row, lane, 0) && !r.solidAt(ctx, row, lane, 1) && !r.solidAt(ctx, row, lane, 2),
                                "TR4: the reset left a gate grown (row " + row + " lane " + lane + ")");
                    }
                }
                room.start(ctx);
                put(a, ctx.cell().rest());
                put(b, ctx.cell().rest());
                holdRow = 0;
                next();
            }
            case 5 -> {
                // 渡る: 壁ごとに完全に開いた戸を探して、その北へ (a と b)
                int row = holdRow;
                int lane = -1;
                for (int l = 0; l < TideCoralRoadRoom.LANES; l++) {
                    if (r.fullyOpen(row, l)) {
                        lane = l;
                    }
                }
                if (lane < 0) {
                    return timedOut("TR4 open gate row " + row);
                }
                BlockPos north = r.gateSpot(row, lane).north(2);
                put(a, north);
                put(b, north);
                check(r.passedBy(a) >= row + 1, "TR4: the player past row " + row + " is not counted (" + r.passedBy(a) + ")");
                holdRow++;
                if (holdRow >= TideCoralRoadRoom.ROWS) {
                    next();
                }
            }
            case 6 -> {
                if (r.isComplete()) {
                    check(r.counter() == TideCoralRoadRoom.ROWS, "TR4: counter at the end is " + r.counter());
                    return true;
                } else if (timedOut("TR4 complete")) {
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- TR5 光を返す真珠
    private static boolean tr5() {
        if (room == null) {
            // 配置が違っても、どれも解ける (台本で全部解いて、光が届くか)
            for (long seed : new long[] {101L, 202L, 303L, 404L, 505L, 606L}) {
                begin("TR5", 2, seed);
                TideLightRoom r = (TideLightRoom) room;
                check(r.pathMirrors() >= 2 && r.mirrorsTotal() >= r.pathMirrors(), "TR5: too few mirrors (seed " + seed + ")");
                check(r.emitterColumn() != 2 && r.targetColumn() != 2, "TR5: the lighthouse or crystal blocks a door (seed " + seed + ")");
                r.tick(ctx);
                check(!r.lit(), "TR5: lit at the start (seed " + seed + ")");
                for (int[] n : r.pathNodes()) {
                    check(r.facing(n[0], n[1]) != r.correctFacing(n[0], n[1]), "TR5: a path mirror starts correct (seed " + seed + ")");
                }
                solveLight(r, seed);
                r.tick(ctx);
                check(r.lit(), "TR5: the solved layout is not lit (seed " + seed + ")");
            }
            begin("TR5", 2, 777L);
            return false;
        }
        TideLightRoom r = (TideLightRoom) room;
        r.tick(ctx);
        switch (phase) {
            case 0 -> {
                check(r.counterMax() == r.pathMirrors() + 1 && !r.objectiveKey().isEmpty(), "TR5: HUD numbers");
                List<int[]> path = r.pathNodes();
                int[] n = path.get(0);
                int f0 = r.facing(n[0], n[1]);
                check(!r.onUse(ctx, a, ctx.origin().offset(-TideLightRoom.HX, 1, 0), false), "TR5: onUse took a click on the wall");
                check(r.onUse(ctx, a, r.mirrorPos(n[0], n[1]), false), "TR5: onUse did not take a mirror click");
                check(r.facing(n[0], n[1]) == (f0 + 1) % 4, "TR5: use did not turn the mirror clockwise");
                check(r.onUse(ctx, a, r.mirrorPos(n[0], n[1]), true), "TR5: onUse did not take a mirror strike");
                check(r.facing(n[0], n[1]) == f0, "TR5: strike did not turn it back");
                // 一つ回した状態から全滅 → はじめの向きへ
                r.onUse(ctx, a, r.mirrorPos(n[0], n[1]), false);
                solveLight(r, 0);
                room.reset(ctx);
                for (int[] p : r.pathNodes()) {
                    check(r.facing(p[0], p[1]) == r.startFacing(p[0], p[1]), "TR5: the reset left a mirror turned");
                }
                check(ctx.aliveMobs().isEmpty(), "TR5: the reset left foes");
                room.start(ctx);
                check(ctx.aliveMobs().size() <= 1 + ctx.scale(), "TR5: too many foes at the start");
                next();
            }
            case 1 -> {
                if (!r.lit()) {
                    solveLight(r, 1);
                }
                if (r.isComplete()) {
                    check(r.counter() == r.pathMirrors() + 1, "TR5: counter at the end");
                    return true;
                } else if (timedOut("TR5 hold the light")) {
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 経路の台を、正解の向きまで onUse で回す (使う = 時計回り)
    private static void solveLight(TideLightRoom r, long tag) {
        for (int[] n : r.pathNodes()) {
            for (int k = 0; k < 4 && r.facing(n[0], n[1]) != r.correctFacing(n[0], n[1]); k++) {
                r.onUse(ctx, a, r.mirrorPos(n[0], n[1]), false);
            }
            check(r.facing(n[0], n[1]) == r.correctFacing(n[0], n[1]), "TR5: could not turn a mirror to the answer (" + tag + ")");
        }
    }

    // ---------------------------------------------------------------- TR6 潮見の暦
    private static boolean tr6() {
        if (room == null) {
            // 壁画の形が、台の目標と同じ (世界のブロックから読む)。配置を変えても、必ずどれも違う初期形
            Set<String> orders = new HashSet<>();
            for (long seed : new long[] {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L}) {
                begin("TR6", 3, seed);
                TideMoonRoom r = (TideMoonRoom) room;
                orders.add(r.first() + "/" + r.step());
                for (int k = 0; k < TideMoonRoom.DIALS; k++) {
                    check(java.util.Arrays.equals(r.muralMask(ctx, k), TideMoonRoom.MASK[r.correctOf(k)]), "TR6: the mural " + k + " does not show phase " + r.correctOf(k) + " (seed " + seed + ")");
                    check(java.util.Arrays.equals(r.dialMask(ctx, k), TideMoonRoom.MASK[r.startOf(k)]), "TR6: the dial " + k + " does not show its start phase");
                    check(r.startOf(k) != r.correctOf(k), "TR6: a dial starts on the answer");
                }
                check(!r.isComplete(), "TR6: complete at the start");
            }
            check(orders.size() >= 3, "TR6: the order barely varies between runs: " + orders);
            for (int p = 0; p < TideMoonRoom.PHASES; p++) {
                for (int q = p + 1; q < TideMoonRoom.PHASES; q++) {
                    check(!java.util.Arrays.equals(TideMoonRoom.MASK[p], TideMoonRoom.MASK[q]), "TR6: phases " + p + " and " + q + " look the same");
                }
            }
            begin("TR6", 3, 99L);
            return false;
        }
        TideMoonRoom r = (TideMoonRoom) room;
        r.tick(ctx);
        switch (phase) {
            case 0 -> {
                check(!r.objectiveKey().isEmpty() && r.counterMax() == 6 && r.counter() < 6, "TR6: HUD shows how many moon stones are right");
                check(!r.onUse(ctx, a, ctx.origin().offset(-TideMoonRoom.HX, 1, 0), true), "TR6: onUse took a click on the wall");
                int before = r.phaseOf(2);
                check(r.onUse(ctx, a, r.dialPos(2), false), "TR6: onUse did not take a dial click");
                check(r.phaseOf(2) == (before + 1) % TideMoonRoom.PHASES, "TR6: use did not advance the phase");
                check(r.onUse(ctx, a, r.dialPos(2), true), "TR6: onUse did not take a dial strike");
                check(r.phaseOf(2) == before, "TR6: strike did not go back");
                // 全滅 → はじめの形
                r.onUse(ctx, a, r.dialPos(4), false);
                room.reset(ctx);
                for (int k = 0; k < TideMoonRoom.DIALS; k++) {
                    check(r.phaseOf(k) == r.startOf(k) && java.util.Arrays.equals(r.dialMask(ctx, k), TideMoonRoom.MASK[r.startOf(k)]), "TR6: the reset left a dial turned");
                }
                room.start(ctx);
                // 五つ合わせても、越えない (合っているかを教えない)
                for (int k = 0; k < TideMoonRoom.DIALS - 1; k++) {
                    setDial(r, k);
                }
                check(!r.isComplete(), "TR6: complete with one dial wrong");
                next();
            }
            case 1 -> {
                setDial(r, TideMoonRoom.DIALS - 1);
                check(r.isComplete(), "TR6: all dials right but the room is not complete");
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 台 k を、onUse で正解まで回す (近い向きで)
    private static void setDial(TideMoonRoom r, int k) {
        for (int i = 0; i < TideMoonRoom.PHASES && r.phaseOf(k) != r.correctOf(k); i++) {
            int fwd = Math.floorMod(r.correctOf(k) - r.phaseOf(k), TideMoonRoom.PHASES);
            r.onUse(ctx, a, r.dialPos(k), fwd > TideMoonRoom.PHASES / 2);
        }
        check(r.phaseOf(k) == r.correctOf(k), "TR6: could not turn dial " + k + " to the answer");
    }

    // ---------------------------------------------------------------- TR7 本物の群れの母
    private static boolean tr7() {
        if (room == null) {
            begin("TR7", 3, 55L);
            return false;
        }
        TideBroodRoom r = (TideBroodRoom) room;
        r.tick(ctx);
        put(a, ctx.cell().rest());
        put(b, ctx.cell().rest());
        switch (phase) {
            case 0 -> {
                Mob m = r.mother();
                check(m != null && m.isAlive(), "TR7: no mother");
                check(r.decoys().size() == r.decoyTarget() && r.decoyTarget() >= 3, "TR7: decoys " + r.decoys().size() + "/" + r.decoyTarget());
                check(m != null && m.getTags().contains(TideBroodRoom.MOTHER_TAG), "TR7: the mother has no mark");
                for (Mob d : r.decoys()) {
                    check(d.getTags().contains(TideBroodRoom.DECOY_TAG) && d.getType() == m.getType() && d.getMaxHealth() >= m.getMaxHealth(), "TR7: a decoy is not a copy of the mother's kind");
                }
                check(r.secondaryMax() > 0 && !r.objectiveKey().isEmpty(), "TR7: HUD numbers");
                next();
            }
            case 1 -> {
                // 本物だけが足跡を残す (歩いたあと)。偽物は足跡のブロックを置かない
                if (r.printCount() > 0) {
                    next();
                } else if (timedOut("TR7 footprints")) {
                    return true;
                }
            }
            case 2 -> {
                // 偽物を叩く: 弾けて消える。母の体力は変わらず、手下も増えない。しばらくして偽物が戻る
                Mob d = r.decoys().get(0);
                Mob m = r.mother();
                float hp = m.getHealth();
                int aliveBefore = ctx.aliveMobs().size();
                d.hurt(back.damageSources().magic(), 4f);
                r.tick(ctx);
                check(d.isRemoved() && r.popped() == 1, "TR7: a hit decoy did not pop");
                check(m.getHealth() >= hp && m.isAlive(), "TR7: hitting a decoy hurt the mother");
                check(ctx.aliveMobs().size() <= aliveBefore - 1, "TR7: hitting a decoy spawned something (" + aliveBefore + " -> " + ctx.aliveMobs().size() + ")");
                check(r.pendingRespawns() == 1, "TR7: the popped decoy is not scheduled to return");
                check(!r.isComplete(), "TR7: complete after a wrong hit");
                next();
            }
            case 3 -> {
                if (r.decoys().size() == r.decoyTarget() && r.pendingRespawns() == 0) {
                    next();
                } else if (timedOut("TR7 decoy return")) {
                    return true;
                }
            }
            case 4 -> {
                // 全滅 → 敵も足跡も消え、最初の姿から
                room.reset(ctx);
                check(ctx.aliveMobs().isEmpty() && r.printCount() == 0, "TR7: the reset left foes or footprints");
                room.start(ctx);
                check(r.mother() != null && r.mother().isAlive() && r.decoys().size() == r.decoyTarget() && r.popped() == 0, "TR7: the room did not restart");
                next();
            }
            case 5 -> {
                // 母を倒す → 越える。偽物は消える
                Mob m = r.mother();
                m.hurt(back.damageSources().magic(), 10000f);
                r.tick(ctx);
                check(r.isComplete(), "TR7: the mother died but the room is not complete");
                check(r.decoys().isEmpty(), "TR7: decoys remained after the mother died");
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }
}
