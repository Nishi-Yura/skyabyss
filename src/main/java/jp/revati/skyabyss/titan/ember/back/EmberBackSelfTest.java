package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import jp.revati.skyabyss.tablet.TabletBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の背の体と広間の自己テスト (13 の 11.1節の順 2 の「確かめ方」、18 章)。ゲームの画面では確かめない (データを読む)。
//  - 体: 体の表 (body.bin) があり、建てた版が今の版。到着点 (舌の上) が床
//  - 穴: 広間の空気から体の中を広げ、空が見える所 (上に何もない) へ出るのは、決めた口 (口の先・背の亀裂・火の井戸と火口・背の上への出口) だけ
//  - 歩く: 到着点から (上り 1・下り 3・頭 2 マス)、喉の休み場・広間・試練の入口と休み場・奥の石・火口・背の上へ歩いて行ける。
//    門を開けると前室と熾の底の入口へも。門が閉じているあいだは前室へ行けない
//  - 水と溶岩: B の通路・広間・喉・前室・井戸に液体が無い (しばらく流したあとも)
//  - 看板: 碑文 6 枚と入口 3 枚が置かれ、文が SignFit の幅 (90 px) に収まる (日本語・英語)
//  - 部屋の箱: 取っておく場所が箱の中、休み場が箱の中
//  - 試練: 石を鳴らす (西はいつでも、東・南は markReady のあと)・光る床・三つで門が開く・リセットで閉じて番号が進む・眠る試練
//  - 熱: 灰溜まりで冷える・継ぎ目で温まる・縦穴・ふだん冷える・火炎耐性で半分・倍率の下限・焦げの傷 (100 → 85 まで)・熱い床の傷を消す
public final class EmberBackSelfTest {
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    static final Map<Long, Long> PARENT = new HashMap<>();

    private EmberBackSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        List<SelfTestRunner.Step> steps = new ArrayList<>();
        steps.add(SelfTestRunner.Step.once("ember back (load)", server -> guarded(server, back -> {
            FAILURES.clear();
            NOTES.clear();
            forceChunks(back, true);
            if (EmberBodyBuilder.lastBuildMs >= 0) {
                NOTES.add("startup build " + EmberBodyBuilder.lastBuildMs + " ms");
            }
            EmberBodyBuilder.ensureBuilt(back);
            EmberBackTrials.reset(back, false, null, true);
        })));
        steps.add(SelfTestRunner.Step.once("ember back (structure)", 40, server -> guarded(server, back -> {
            checkBody(back);
            checkLayout();
            checkSigns(back);
            checkHoles(back);
            checkFluids(back, "after building");
            checkWalk(back, false);
        })));
        steps.add(SelfTestRunner.Step.once("ember back (flow)", 100, server -> guarded(server, back -> checkFluids(back, "100 ticks after building"))));
        steps.add(SelfTestRunner.Step.once("ember back (ring)", server -> guarded(server, back -> checkRing(back))));
        steps.add(SelfTestRunner.Step.once("ember back (gate)", EmberBackTrials.GATE_OPEN_TICKS + 20, server -> guarded(server, back -> {
            EmberBackState state = EmberBackState.get(back);
            expect(state.gateOpen(), "the gate did not open " + EmberBackTrials.GATE_OPEN_TICKS + " ticks after the third stone");
            expect(back.getBlockState(HEART_GATE.min()).isAir() && back.getBlockState(HUB_CENTER.above()).isAir(), "the opened gate still has its grate or fire");
            checkWalk(back, true);
            checkReset(back);
            checkHeat(back);
            checkFluids(back, "after the trials");
        })));
        steps.add(SelfTestRunner.Step.once("ember back", server -> guarded(server, back -> {
            forceChunks(back, false);
            if (FAILURES.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST ember back PASSED ({})", String.join("; ", NOTES));
            } else {
                SkyAbyss.LOGGER.error("SELFTEST ember back FAILED: {} ({})", String.join("; ", FAILURES), String.join("; ", NOTES));
            }
        })));
        return steps;
    }

    private static void guarded(MinecraftServer server, java.util.function.Consumer<ServerLevel> body) {
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back == null) {
            FAILURES.add("ember_back is missing");
            return;
        }
        try {
            body.accept(back);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember back crashed", e);
            FAILURES.add("crashed: " + e);
        }
    }

    private static void forceChunks(ServerLevel back, boolean on) {
        for (int cx = BODY.minX() >> 4; cx <= BODY.maxX() >> 4; cx++) {
            for (int cz = BODY.minZ() >> 4; cz <= BODY.maxZ() >> 4; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    private static void expect(boolean ok, String failure) {
        if (!ok) {
            FAILURES.add(failure);
        }
    }

    // ---------------------------------------------------------------- 体
    private static void checkBody(ServerLevel back) {
        expect(EmberBodyBuilder.BODY.present(), "body.bin is missing");
        TitanBackData data = TitanBackData.get(back);
        expect(data.isBuilt() && data.layoutVersion() == EmberBodyBuilder.buildVersion(), "the ember back is not built at build " + EmberBodyBuilder.buildVersion()
                + " (data " + data.layoutVersion() + ")");
        expect(!back.getBlockState(ARRIVAL).isAir() && back.getBlockState(ARRIVAL.above()).isAir() && back.getBlockState(ARRIVAL.above(2)).isAir(),
                "the arrival on the tongue is not a floor with room to stand");
        expect(EmberBodyBuilder.BODY.minZ() > BODY.minZ() && EmberBodyBuilder.BODY.maxZ() < BODY.maxZ() && EmberBodyBuilder.BODY.maxY() < BODY.maxY()
                && EmberBodyBuilder.BODY.minX() >= BODY.minX() && EmberBodyBuilder.BODY.maxX() <= BODY.maxX(), "body.bin does not fit in the BODY box");
        // 体が火山の形 (火口が高い)・腹が灰の原の上
        int crater = EmberBodyBuilder.crater();
        expect(crater > 150, "the crater floor is at " + crater + ", expected the volcano");
        NOTES.add("crater floor " + crater);
        expect(!back.getBlockState(new BlockPos(0, PLAIN_TOP + 1, 40)).isAir(), "the belly does not rest on the ash plain");
        // 体の外 (灰の原) は平らなまま
        expect(back.getBlockState(new BlockPos(0, PLAIN_TOP, -200)).is(Blocks.SMOOTH_BASALT) && back.getBlockState(new BlockPos(0, PLAIN_TOP + 1, -200)).isAir(),
                "the ash plain in front of the body is not flat");
    }

    // ---------------------------------------------------------------- 部屋の箱
    private static void checkLayout() {
        for (String room : TRIAL_ROOMS) {
            for (Box r : reserved(room)) {
                expect(r.inside(ROOMS.get(room)), "reserved " + r + " is outside the " + room + " box");
            }
        }
        for (Rest r : RESTS) {
            boolean in = false;
            for (Box b : ROOMS.values()) {
                in |= b.contains(r.floor());
            }
            if (!r.name().equals("throat")) {
                expect(in, "rest " + r.name() + " is outside every room box");
            }
        }
        expect(GATE_SHAFT.contains(HUB_CENTER.getX(), 84, HUB_CENTER.getZ()) && HEART_GATE.contains(HUB_CENTER), "the gate is not over its shaft");
    }

    // ---------------------------------------------------------------- 看板
    private static void checkSigns(ServerLevel back) {
        List<String> keys = new ArrayList<>();
        List<Inscription> all = new ArrayList<>(INSCRIPTIONS);
        all.addAll(ENTRANCE_SIGNS);
        for (Inscription in : INSCRIPTIONS) {
            keys.add("sign.skyabyss." + in.id());
        }
        for (Inscription in : ENTRANCE_SIGNS) {
            keys.add("sign.skyabyss.ember_" + in.id());
        }
        for (Inscription in : all) {
            BlockState s = back.getBlockState(in.pos());
            expect(s.getBlock() instanceof TabletBlock && s.getValue(HorizontalDirectionalBlock.FACING) == in.facing(), "tablet " + in.id() + " is missing at " + in.pos().toShortString());
            expect(back.getBlockState(in.pos().relative(in.facing().getOpposite())).isSolid(), "tablet " + in.id() + " has nothing behind it");
        }
        for (String id : EmberIds.INSCRIPTIONS) {
            expect(keys.contains("sign.skyabyss." + id), "inscription " + id + " is not placed");
        }
        List<String> bad = SignFit.check(keys);
        FAILURES.addAll(bad);
        expect(SignFit.allSignKeys().containsAll(keys), "SignFit does not see the ember signs (skyabyss_ember lang)");
        expect(all.size() == 9, "there should be 9 tablets (6 inscriptions, 3 entrances), not " + all.size());
    }

    // ---------------------------------------------------------------- 穴 (体の中の空気が、決めた口のほかで空へ抜けない)
    static boolean allowedOpening(int x, int y, int z) {
        if (z <= -128) {
            return true;
        }
        if (Math.abs(x) <= 4 && z >= CRACK.minZ() - 2 && z <= CRACK.maxZ() + 2 && y >= SOUTH.minY()) {
            return true;
        }
        if (EmberBodyBuilder.wellDist(x, z) <= 15 && y >= EmberBodyBuilder.crater() - 2
                || EmberBodyBuilder.wellDist(x, z) <= WELL_RADIUS + 0.5 && y >= WELL_BOTTOM - 1) {
            return true;
        }
        return Math.abs(x) <= 3 && z >= -30 && z <= 6 && y >= BACK_EXIT_FLOOR;
    }

    private static boolean open(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && !s.is(ModBlocks.SEALED_GATE.get());
    }

    // 上に体のブロックが無い (空が見える)
    private static boolean skyAbove(ServerLevel l, BlockPos p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = p.getY() + 1; y <= BODY.maxY(); y++) {
            if (!l.getBlockState(m.set(p.getX(), y, p.getZ())).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static void checkHoles(ServerLevel back) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        BlockPos start = HUB_CENTER.offset(0, 4, -8);
        q.add(start);
        seen.add(start.asLong());
        List<String> holes = new ArrayList<>();
        int sides = 0;
        while (!q.isEmpty() && seen.size() < 1_500_000) {
            BlockPos p = q.poll();
            if (allowedOpening(p.getX(), p.getY(), p.getZ())) {
                continue;
            }
            if (skyAbove(back, p) || p.getY() >= BODY.maxY() - 1 || p.getX() <= BODY.minX() || p.getX() >= BODY.maxX() || p.getZ() >= BODY.maxZ()) {
                if (holes.size() < 6) {
                    holes.add(p.toShortString());
                }
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n.asLong()) && open(back, n)) {
                    q.add(n);
                }
            }
            sides++;
        }
        expect(holes.isEmpty(), "the body has holes to the sky outside the planned openings, e.g. " + holes);
        expect(seen.size() < 1_500_000, "the inside air does not end (the body is open)");
        NOTES.add("inside air " + sides + " cells");
    }

    // ---------------------------------------------------------------- 液体
    private static void checkFluids(ServerLevel back, String when) {
        List<Box> regions = List.of(new Box(-7, MOUTH_FLOOR, -166, 7, 80, -134), new Box(-3, MOUTH_FLOOR, -135, 3, 91, -49), HUB,
                new Box(-27, 75, -36, 23, 90, -9), new Box(-1, 77, -20, 1, 90, -7), HEART_ANTE, GATE_SHAFT,
                new Box(-7, WELL_BOTTOM, HUB_CENTER.getZ() - 7, 7, EmberBodyBuilder.crater(), HUB_CENTER.getZ() + 7));
        int fluids = 0;
        String first = "";
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Box b : regions) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        if (!back.getFluidState(m.set(x, y, z)).isEmpty()) {
                            if (fluids++ == 0) {
                                first = m.toShortString();
                            }
                        }
                    }
                }
            }
        }
        expect(fluids == 0, when + ": " + fluids + " fluid blocks in the B corridors and halls (first at " + first + ")");
    }

    // ---------------------------------------------------------------- 歩く
    // 試練の仕掛けを通ったものとして歩く (灰の道の湖を冷え殻で渡る、鍛えの間の扉を火の鍵で開ける)。通常は false
    private static boolean mechanisms;

    private static boolean lakeLava(ServerLevel l, BlockPos p) {
        return p.getY() == jp.revati.skyabyss.titan.ember.back.trial.AshPath.LAVA_Y && l.getFluidState(p).is(FluidTags.LAVA)
                && EmberBackLayout.WEST.contains(p);
    }

    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (mechanisms && s.is(ModBlocks.SEALED_GATE.get()) && SMITH_DOOR.contains(p)) {
            return true;
        }
        return s.getCollisionShape(l, p).isEmpty() && !s.is(ModBlocks.SEALED_GATE.get()) && !l.getFluidState(p).is(FluidTags.LAVA)
                && !s.is(Blocks.FIRE) && !s.is(Blocks.SOUL_FIRE);
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockPos b = p.below();
        return !l.getBlockState(b).getCollisionShape(l, b).isEmpty() || l.getBlockState(p).is(Blocks.WATER) || mechanisms && lakeLava(l, b);
    }

    static Map<Long, Integer> walk(ServerLevel l, BlockPos start) {
        PARENT.clear();
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start.asLong(), 0);
        q.add(start);
        while (!q.isEmpty() && dist.size() < 600000) {
            BlockPos p = q.poll();
            int d = dist.get(p.asLong());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos m = n.above(dy);
                    if (dy == 1 && !passable(l, p.above(2))) {
                        continue;
                    }
                    if (dy < 0) {
                        boolean clear = true;
                        for (int k = dy; k <= 1; k++) {
                            if (!passable(l, n.above(k))) {
                                clear = false;
                            }
                        }
                        if (!clear) {
                            continue;
                        }
                    }
                    if (standable(l, m)) {
                        if (BODY.grow(4).contains(m) && !dist.containsKey(m.asLong())) {
                            dist.put(m.asLong(), d + 1);
                            PARENT.put(m.asLong(), p.asLong());
                            q.add(m);
                        }
                        break;
                    }
                }
            }
        }
        return dist;
    }

    // 目標の近く (r マス以内、高さ ±2) に歩いて行けた足の位置があるか
    private static int reached(Map<Long, Integer> dist, BlockPos goal, int r) {
        int best = -1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Integer d = dist.get(goal.offset(dx, dy, dz).asLong());
                    if (d != null && (best < 0 || d < best)) {
                        best = d;
                    }
                }
            }
        }
        return best;
    }

    // 試練の向こう側にある所。仕掛け (冷え殻・火の鍵) を通らないと歩いて行けないのが仕様 (13 の 24.1節)
    private static final Set<String> TRIAL_GATED = Set.of("rest w1", "west stone", "east stone");

    private static void checkWalk(ServerLevel back, boolean gateOpen) {
        mechanisms = false;
        checkWalk(back, gateOpen, false);
        mechanisms = true;
        try {
            checkWalk(back, gateOpen, true);
        } finally {
            mechanisms = false;
        }
    }

    private static void checkWalk(ServerLevel back, boolean gateOpen, boolean mech) {
        Map<Long, Integer> dist = walk(back, ARRIVAL.above());
        Map<String, BlockPos> goals = new LinkedHashMap<>();
        for (Rest r : RESTS) {
            if (!r.name().equals("heart") || gateOpen) {
                goals.put("rest " + r.name(), r.floor().above());
            }
        }
        goals.put("return stone", new BlockPos(0, PLAIN_TOP + 1, RETURN_STONE.minZ()));
        goals.put("west door", WEST_DOOR.min());
        goals.put("east door", EAST_DOOR.min());
        goals.put("south door", SOUTH_DOOR.min());
        goals.put("smith door", SMITH_DOOR.min());
        goals.put("west stone", STONE_WEST.north());
        goals.put("east stone", STONE_EAST.north());
        goals.put("south stone", STONE_SOUTH.north());
        goals.put("well top (crater)", new BlockPos(HUB_CENTER.getX() + 8, EmberBodyBuilder.crater() + 1, HUB_CENTER.getZ()));
        goals.put("back top", new BlockPos(0, BACK_EXIT_FLOOR + 1, 4));
        goals.put("crack rail", new BlockPos(5, 115, 30));
        if (gateOpen) {
            goals.put("heart door", HEART_DOOR.min());
        }
        List<String> miss = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BlockPos> e : goals.entrySet()) {
            int r = e.getKey().equals("back top") || e.getKey().equals("crack rail") ? 4 : e.getKey().contains("crater") ? 2 : 1;
            int d = reached(dist, e.getValue(), r);
            if (d < 0) {
                if (!mech && TRIAL_GATED.contains(e.getKey())) {
                    continue;
                }
                miss.add(e.getKey() + " " + e.getValue().toShortString());
            } else if (!gateOpen && !mech) {
                sb.append(e.getKey()).append('=').append(d).append(' ');
            }
        }
        expect(miss.isEmpty(), (gateOpen ? "with the gate open" : "with the gate closed") + (mech ? " and the trials' mechanisms used" : "")
                + " cannot walk from the arrival to: " + miss);
        if (mech) {
            return;
        }
        if (!gateOpen) {
            // 門が閉じていれば前室へは行けない
            expect(reached(dist, rest("heart").floor().above(), 3) < 0, "the ante is reachable while the gate is closed");
            NOTES.add("walk steps: " + sb.toString().trim() + " (" + dist.size() + " cells)");
        } else {
            NOTES.add("heart rest in " + reached(dist, rest("heart").floor().above(), 1) + " steps with the gate open");
        }
    }

    // ---------------------------------------------------------------- 熾り石と門
    private static void checkRing(ServerLevel back) {
        EmberBackTrials.reset(back, false, null, true);
        EmberBackState state = EmberBackState.get(back);
        expect(back.getBlockState(HEART_GATE.min()).is(ModBlocks.SEALED_GATE.get()) && back.getBlockState(HUB_CENTER.above()).is(Blocks.CAMPFIRE),
                "the closed gate has no grate or fire");
        expect(state.rungCount() == 0 && !state.gateOpen(), "a fresh round starts with rung stones");
        FakePlayer p = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "ember_test_ring"));
        // 東は鍛え手を倒すまで黙る
        EmberBackTrials.useStone(back, STONE_EAST, p);
        expect(!state.rung(EmberBackState.Trial.EAST), "the east stone rang before the smith fell");
        EmberBackTrials.useStone(back, STONE_WEST, p);
        expect(state.rung(EmberBackState.Trial.WEST), "the west stone did not ring");
        expect(back.getBlockState(STONE_WEST).getValue(KindleStoneBlock.ACTIVE), "the rung west stone is not lit");
        expect(back.getBlockState(PAD_WEST).is(EmberKit.EMBERLIGHT.getBlock()), "the west shortcut floor is not lit");
        expect(!back.getBlockState(PAD_EAST).is(EmberKit.EMBERLIGHT.getBlock()), "the east shortcut floor is lit before its stone");
        EmberBackTrials.markReady(back, EmberBackState.Trial.EAST);
        EmberBackTrials.useStone(back, STONE_EAST, p);
        EmberBackTrials.markReady(back, EmberBackState.Trial.SOUTH);
        EmberBackTrials.useStone(back, STONE_SOUTH, p);
        expect(state.rungCount() == 3, "three stones did not ring: " + state.rungCount());
        expect(EmberBackTrials.gateOpening(), "the forge does not start dimming after three stones");
        expect(!state.gateOpen(), "the gate opened at once (no dimming)");
    }

    private static void checkReset(ServerLevel back) {
        int epoch = TitanBackData.get(back).epoch();
        EmberBackState state = EmberBackState.get(back);
        int resets = state.resets();
        EmberBackTrials.reset(back, false, null, true);
        expect(TitanBackData.get(back).epoch() > epoch, "a reset does not advance the trial number");
        expect(state.resets() == resets + 1 && state.rungCount() == 0 && !state.gateOpen(), "a reset does not clear the stones and gate");
        expect(back.getBlockState(HEART_GATE.min()).is(ModBlocks.SEALED_GATE.get()), "a reset does not close the gate");
        expect(!back.getBlockState(STONE_WEST).getValue(KindleStoneBlock.ACTIVE), "a reset does not dim the stones");
        // 眠る試練: 東が眠ると smith も眠り、東の石は最初から鳴っている
        EmberBackTrials.setSleeping(back, EmberBackState.Trial.EAST);
        expect(state.rung(EmberBackState.Trial.EAST) && EmberBackTrials.sleeping(back, "smith") && EmberBackTrials.sleeping(back, "east")
                && !EmberBackTrials.sleeping(back, "west"), "a sleeping east does not ring its stone or put the smith to sleep");
        // 心臓を倒したことがあれば、次のリセットで一つ眠る (同じ試練は二回続けて眠らない)
        state.markEverDefeated();
        EmberBackTrials.reset(back, false);
        expect(state.sleeping() != null && state.sleeping() != EmberBackState.Trial.EAST, "after the heart fell, a reset does not put another trial to sleep: "
                + state.sleeping());
        EmberBackTrials.reset(back, false, null, true);
        expect(state.sleeping() == null, "noSleep still put a trial to sleep");
    }

    // ---------------------------------------------------------------- 熱
    private static void checkHeat(ServerLevel back) {
        FakePlayer p = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "ember_test_heat_b"));
        // 灰溜まり (広間の壁ぎわ)
        BlockPos ash = HUB_CENTER.offset(-9, 1, -8);
        p.moveTo(ash.getX() + 0.5, ash.getY(), ash.getZ() + 0.5);
        expect(EmberHeat.spot(p) == EmberHeat.Spot.ASH, "the hub ash pool is not an ash spot: " + EmberHeat.spot(p));
        EmberHeat.set(p, 50);
        p.tickCount = 20;
        EmberHeat.tickPlayer(p);
        expect(Math.abs(EmberHeat.get(p) - 42.5f) < 0.01f, "the ash pool does not cool 15 a second: " + EmberHeat.get(p));
        // ふだん (広間の真ん中の床でなく、北の扉のそば)
        BlockPos plain = HUB_CENTER.offset(-6, 1, 6);
        p.moveTo(plain.getX() + 0.5, plain.getY(), plain.getZ() + 0.5);
        EmberHeat.set(p, 50);
        EmberHeat.tickPlayer(p);
        expect(EmberHeat.spot(p) == EmberHeat.Spot.NONE && Math.abs(EmberHeat.get(p) - 48.5f) < 0.01f, "passive cooling is not 3 a second: "
                + EmberHeat.spot(p) + " " + EmberHeat.get(p));
        // 休み場では動かない
        BlockPos rest = rest("hub").floor().above();
        p.moveTo(rest.getX() + 0.5, rest.getY(), rest.getZ() + 0.5);
        EmberHeat.set(p, 50);
        EmberHeat.tickPlayer(p);
        expect(EmberHeat.get(p) == 50f, "heat moves at a rest");
        // 継ぎ目の上: 熱い床 (マグマ) を一時的に置いて
        BlockPos hot = HUB_CENTER.offset(6, 0, 6);
        BlockState was = back.getBlockState(hot);
        back.setBlock(hot, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        p.moveTo(hot.getX() + 0.5, hot.getY() + 1, hot.getZ() + 0.5);
        EmberHeat.set(p, 10);
        EmberHeat.tickPlayer(p);
        expect(EmberHeat.spot(p) == EmberHeat.Spot.HOT_GROUND && Math.abs(EmberHeat.get(p) - 10.5f) < 0.01f, "magma seams do not heat 1 a second: "
                + EmberHeat.spot(p) + " " + EmberHeat.get(p));
        back.setBlock(hot, was, 3);
        // 縦穴
        BlockPos shaft = new BlockPos(HUB_CENTER.getX() + 5, WELL_BOTTOM + 1, HUB_CENTER.getZ());
        p.moveTo(shaft.getX() + 0.5, shaft.getY(), shaft.getZ() + 0.5);
        expect(EmberHeat.spot(p) == EmberHeat.Spot.SHAFT, "the fire well is not a shaft spot: " + EmberHeat.spot(p));
        // 火炎耐性で半分、角杯の口 (強いほうだけ)、重ねる口と下限 0.4
        EmberHeat.set(p, 0);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        expect(EmberHeat.add(p, 20, EmberHeat.Reason.VENT) == 10f, "fire resistance does not halve heat gain");
        p.removeAllEffects();
        expect(Math.abs(EmberHeat.gainMultiplier(p, EmberHeat.Reason.VENT) - 1f) < 1e-6, "the multiplier is not 1 without gear");
        // 焦げ: 100 で 1 秒に 2、85 を切るまで
        EmberHeat.set(p, 100);
        float hp = p.getHealth();
        p.tickCount = 40;
        EmberHeat.tickPlayer(p);
        expect(EmberHeat.scorched(p), "100 heat does not scorch");
        var heatSource = new net.minecraft.world.damagesource.DamageSource(back.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(EmberIds.HEAT));
        expect(p.getHealth() < hp || p.isInvulnerableTo(heatSource), "scorching did no damage (health " + hp + " -> " + p.getHealth() + ")");
        EmberHeat.set(p, 90);
        EmberHeat.tickPlayer(p);
        expect(EmberHeat.scorched(p), "scorching stops above 85");
        EmberHeat.set(p, 80);
        EmberHeat.tickPlayer(p);
        expect(!EmberHeat.scorched(p), "scorching goes on below 85");
        EmberHeat.reset(p);
        // 熱い床の傷は消える
        p.discard();
        net.minecraft.world.entity.animal.Pig pig = net.minecraft.world.entity.EntityType.PIG.create(back);
        if (pig != null) {
            BlockPos at = HUB_DOOR_NORTH.offset(0, 0, 3);
            pig.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            back.addFreshEntity(pig);
            float hp2 = pig.getHealth();
            pig.hurt(back.damageSources().hotFloor(), 4f);
            expect(pig.getHealth() == hp2, "magma hot-floor damage is not cancelled on the ember back");
            pig.discard();
        }
    }
}
