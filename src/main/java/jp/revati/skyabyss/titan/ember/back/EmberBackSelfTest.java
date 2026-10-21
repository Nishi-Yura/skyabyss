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
            EmberDump.run(back);
            EmberBackTrials.placeFixtures(back);
            EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
            EmberBackTrials.resetGroup(back, GROUP_B, false, null, true);
        })));
        steps.add(SelfTestRunner.Step.once("ember back (structure)", 40, server -> guarded(server, back -> {
            checkBody(back);
            checkLayout();
            checkSigns(back);
            checkHoles(back);
            checkFluids(back, "after building");
            checkWalk(back, false);
            // 詰み監査 (到達・戻れるか・はしご・床の穴)。戸は開いているものとして、灰の道の湖は冷え殻で渡れるものとして
            jp.revati.skyabyss.titan.audit.BackReach.Report reach = EmberBackAudit.run(back);
            reach.notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST {}", n));
            reach.failures.forEach(f -> SkyAbyss.LOGGER.warn("REACH {}", f));
            reach.failures.forEach(f -> expect(false, f));
        })));
        steps.add(SelfTestRunner.Step.once("ember back (flow)", 100, server -> guarded(server, back -> checkFluids(back, "100 ticks after building"))));
        steps.add(SelfTestRunner.Step.once("ember back (ring)", server -> guarded(server, back -> checkRing(back))));
        steps.add(SelfTestRunner.Step.once("ember back (gate)", EmberBackTrials.GATE_OPEN_TICKS + 20, server -> guarded(server, back -> {
            EmberBackState state = EmberBackState.of(back, GROUP_A);
            expect(state.gateOpen(), "the gate did not open " + EmberBackTrials.GATE_OPEN_TICKS + " ticks after the third stone");
            checkGateForTwoGroups(back);
            checkWalk(back, true);
            checkReset(back);
            checkGroups(back);
            checkHeat(back);
            checkFluids(back, "after the trials");
            EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
            EmberBackTrials.resetGroup(back, GROUP_B, false, null, true);
            jp.revati.skyabyss.titan.group.BackGroupData.get(back).remove(GROUP_A);
            jp.revati.skyabyss.titan.group.BackGroupData.get(back).remove(GROUP_B);
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
        // 腹が灰の原の上
        expect(!back.getBlockState(new BlockPos(0, PLAIN_TOP + 1, 40)).isAir(), "the belly does not rest on the ash plain");
        // 体の外 (灰の原) は平らなまま
        expect(back.getBlockState(new BlockPos(0, PLAIN_TOP, -295)).is(Blocks.SMOOTH_BASALT) && back.getBlockState(new BlockPos(0, PLAIN_TOP + 1, -295)).isAir(),
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
        expect(GATE_SHAFT.contains(HUB_CENTER.getX(), FLOOR_HUB - 8, HUB_CENTER.getZ()) && HEART_GATE.contains(HUB_CENTER), "the gate is not over its shaft");
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
        // 掲示 (部屋の中の碑)・宝箱の碑
        List<String> more = new ArrayList<>();
        for (Inscription in : BOARDS) {
            more.add("sign.skyabyss.ember_board_" + in.id());
            BlockState bs = back.getBlockState(in.pos());
            expect(bs.getBlock() instanceof TabletBlock && bs.getValue(HorizontalDirectionalBlock.FACING) == in.facing(), "board " + in.id() + " is missing at " + in.pos().toShortString());
            expect(back.getBlockState(in.pos().relative(in.facing().getOpposite())).isSolid(), "board " + in.id() + " has nothing behind it");
        }
        FAILURES.addAll(SignFit.check(more));
        expect(SignFit.allSignKeys().containsAll(more), "SignFit does not see the ember boards (skyabyss_ember lang)");
    }

    // ---------------------------------------------------------------- 穴 (体の中の空気が、決めた口のほかで空へ抜けない)
    static boolean allowedOpening(int x, int y, int z) {
        return z <= EmberBodyBuilder.NECK_Z1;
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
        List<Box> regions = List.of(MOUTH, THROAT, HUB,
                new Box(-48, 78, -58, 50, 96, -16), new Box(-4, 82, -27, 4, 96, -9), HEART_ANTE, GATE_SHAFT);
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

    // 歩く人のグループ (扉の開け閉めをこのグループで見る。null なら扉の既定)
    private static UUID walkGroup;

    // 試練のセルの入口の戸 (原型の三つの冷えた溶岩洞の口)。引き (試練のプール) で、引いたグループにだけ開く。歩いて届くかだけを見る
    private static java.util.Set<Long> cellEntrances;

    private static boolean isCellEntrance(BlockPos p) {
        if (cellEntrances == null) {
            cellEntrances = new java.util.HashSet<>();
            for (jp.revati.skyabyss.trial.TrialCell cell : jp.revati.skyabyss.trial.TrialCells.of("ember")) {
                cell.ports().entrance().forEach(b -> cellEntrances.add(b.asLong()));
            }
        }
        return cellEntrances.contains(p.asLong());
    }

    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.is(ModBlocks.SEALED_GATE.get())) {
            if (mechanisms && SMITH_DOOR.contains(p) || EmberDeck.isPoolGate(p) || isCellEntrance(p)) {
                // 試練の階の戸は引き (試練のプール) で開く。歩いて届くかだけを見る (引きと戸は trial pool ember が調べる)
                return true;
            }
            return walkGroup == null ? s.getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN)
                    : jp.revati.skyabyss.titan.group.GroupGates.isOpen(l, walkGroup, p);
        }
        return s.getCollisionShape(l, p).isEmpty() && !l.getFluidState(p).is(FluidTags.LAVA)
                && !s.is(Blocks.FIRE) && !s.is(Blocks.SOUL_FIRE);
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockPos b = p.below();
        BlockState under = l.getBlockState(b);
        // 扉の上には、閉じている (歩く人のグループで) ときだけ立てる
        boolean solid = under.is(ModBlocks.SEALED_GATE.get()) ? !passable(l, b) : !under.getCollisionShape(l, b).isEmpty();
        return solid || l.getBlockState(p).is(Blocks.WATER) || mechanisms && lakeLava(l, b);
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

    // gateOpen: 門を開けたグループ A として歩く。閉じているときは、何も進んでいないグループ B として歩く
    private static void checkWalk(ServerLevel back, boolean gateOpen) {
        walkGroup = gateOpen ? GROUP_A : GROUP_B;
        mechanisms = false;
        try {
            checkWalk(back, gateOpen, false);
            mechanisms = true;
            checkWalk(back, gateOpen, true);
        } finally {
            mechanisms = false;
            walkGroup = null;
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
        if (gateOpen) {
            goals.put("heart door", HEART_DOOR.min());
        }
        List<String> miss = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, BlockPos> e : goals.entrySet()) {
            int r = 1;
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
        if (!miss.isEmpty()) {
            // 届かないときの手がかり: 歩いて届いた所に接している、閉じた戸
            Set<BlockPos> shut = new java.util.LinkedHashSet<>();
            for (Long key : dist.keySet()) {
                BlockPos c = BlockPos.of(key);
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos n = c.relative(dir).above(dy);
                        if (back.getBlockState(n).is(ModBlocks.SEALED_GATE.get()) && !passable(back, n) && shut.size() < 12) {
                            shut.add(n.immutable());
                        }
                    }
                }
            }
            miss.add("closed doors next to the walked area: " + shut);
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

    // ---------------------------------------------------------------- 熾り石と門 (グループごと)
    // 二つのグループ (偽のプレイヤー。パーティに入っていないので一人のグループ = その人の UUID)
    static final UUID GROUP_A = UUID.nameUUIDFromBytes("skyabyss ember back test A".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    static final UUID GROUP_B = UUID.nameUUIDFromBytes("skyabyss ember back test B".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // 位置を移せる偽のプレイヤー (接続のない偽のプレイヤーは teleportTo で動かないので、位置を直接置く)
    static FakePlayer fake(ServerLevel back, UUID id, String name) {
        return new FakePlayer(back, new GameProfile(id, name)) {
            @Override
            public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                moveTo(x, y, z, yaw, pitch);
            }
        };
    }

    private static boolean overlayHas(ServerLevel back, UUID group, BlockPos pos, java.util.function.Predicate<BlockState> test) {
        BlockState s = jp.revati.skyabyss.titan.group.GroupOverlays.compute(back, group).get(pos);
        return s != null && test.test(s);
    }

    private static void checkRing(ServerLevel back) {
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
        EmberBackTrials.resetGroup(back, GROUP_B, false, null, true);
        EmberBackState state = EmberBackState.of(back, GROUP_A);
        BlockState plug = back.getBlockState(HEART_GATE.min());
        expect(plug.is(ModBlocks.SEALED_GATE.get()) && !plug.getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN),
                "the hub gate is not a closed gate block in the world");
        expect(overlayHas(back, GROUP_A, HUB_CENTER.above(), s -> s.is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())), "a group with the gate closed does not see the forge fire");
        expect(state.rungCount() == 0 && !state.gateOpen(), "a fresh round starts with rung stones");
        FakePlayer p = fake(back, GROUP_A, "ember_test_ring");
        // 東は鍛え手を倒すまで黙る
        EmberBackTrials.useStone(back, STONE_EAST, p);
        expect(!state.rung(EmberBackState.Trial.EAST), "the east stone rang before the smith fell");
        EmberBackTrials.useStone(back, STONE_WEST, p);
        expect(state.rung(EmberBackState.Trial.WEST), "the west stone did not ring");
        // 光る石はグループの見かけ。ワールドのブロックは鳴っていない姿のまま、ほかのグループには見えない
        expect(overlayHas(back, GROUP_A, STONE_WEST, s -> jp.revati.skyabyss.trial.TrialGoal.isReached(s)), "the rung west stone is not lit for its group");
        expect(!jp.revati.skyabyss.trial.TrialGoal.isReached(back.getBlockState(STONE_WEST)), "ringing changed the world block of the stone");
        expect(!overlayHas(back, GROUP_B, STONE_WEST, s -> jp.revati.skyabyss.trial.TrialGoal.isReached(s)) && EmberBackState.of(back, GROUP_B).rungCount() == 0,
                "group B sees or has group A's rung stone");
        EmberBackTrials.markReady(back, GROUP_A, EmberBackState.Trial.EAST);
        EmberBackTrials.useStone(back, STONE_EAST, p);
        EmberBackTrials.markReady(back, GROUP_A, EmberBackState.Trial.SOUTH);
        EmberBackTrials.useStone(back, STONE_SOUTH, p);
        expect(state.rungCount() == 3, "three stones did not ring: " + state.rungCount());
        expect(EmberBackTrials.gateOpening(GROUP_A), "the forge does not start dimming after three stones");
        expect(!EmberBackTrials.gateOpening(GROUP_B), "group B's forge dims with group A's stones");
        expect(!state.gateOpen(), "the gate opened at once (no dimming)");
        // 越えた試練の順路の運びの待ちは捨てる (歩きの検査は運ぶ前の姿で見る)
        jp.revati.skyabyss.trial.TrialRoute.clearAll();
    }

    // 門を開けたグループ A は通れ、B は閉じたまま (当たり判定も)
    private static void checkGateForTwoGroups(ServerLevel back) {
        BlockPos plug = HEART_GATE.min();
        expect(back.getBlockState(plug).is(ModBlocks.SEALED_GATE.get()), "opening the gate removed the gate block from the world");
        expect(jp.revati.skyabyss.titan.group.GroupGates.effective(back, GROUP_A, plug, false), "the gate is not open for group A");
        expect(!jp.revati.skyabyss.titan.group.GroupGates.effective(back, GROUP_B, plug, false), "the gate is open for group B");
        FakePlayer a = fake(back, GROUP_A, "ember_test_gate_a");
        FakePlayer b = fake(back, GROUP_B, "ember_test_gate_b");
        BlockState s = back.getBlockState(plug);
        expect(s.getCollisionShape(back, plug, net.minecraft.world.phys.shapes.CollisionContext.of(a)).isEmpty(), "group A collides with its open gate");
        expect(!s.getCollisionShape(back, plug, net.minecraft.world.phys.shapes.CollisionContext.of(b)).isEmpty(), "group B walks through a gate it has not opened");
        for (BlockPos w : EmberBackTrials.HUB_WALL) {
            expect(!back.getBlockState(w).getCollisionShape(back, w, net.minecraft.world.phys.shapes.CollisionContext.of(b)).isEmpty(),
                    "the furnace mouth is open for group B at " + w.toShortString());
        }
        expect(!overlayHas(back, GROUP_A, HUB_CENTER.above(), s2 -> s2.is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())), "group A still sees the forge fire after the gate opened");
        expect(overlayHas(back, GROUP_B, HUB_CENTER.above(), s2 -> s2.is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())), "group B lost its forge fire when group A opened the gate");
    }

    private static void checkReset(ServerLevel back) {
        EmberBackState state = EmberBackState.of(back, GROUP_A);
        int epoch = state.epoch();
        int epochB = EmberBackState.of(back, GROUP_B).epoch();
        int resets = state.resets();
        int seed = state.seed();
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
        expect(state.epoch() > epoch, "a reset does not advance the trial number");
        expect(state.seed() != seed, "a reset does not change the seed");
        expect(EmberBackState.of(back, GROUP_B).epoch() == epochB, "resetting group A advanced group B's trial number");
        expect(state.resets() == resets + 1 && state.rungCount() == 0 && !state.gateOpen(), "a reset does not clear the stones and gate");
        expect(!EmberBackTrials.gateOpenFor(back, GROUP_A), "a reset does not close the gate for the group");
        expect(!overlayHas(back, GROUP_A, STONE_WEST, s -> jp.revati.skyabyss.trial.TrialGoal.isReached(s)), "a reset does not dim the stones");
        // 静かな試練: 東が静かだと smith の部屋も静かな周になるが、石は鳴っておらず (最後までできる)、やさしい打つ順になる (部屋の持ち主のグループにとって)
        EmberRooms.claimForTest(back, EmberRooms.EAST, GROUP_A);
        EmberRooms.claimForTest(back, EmberRooms.WEST, GROUP_A);
        EmberBackTrials.setQuiet(back, GROUP_A, EmberBackState.Trial.EAST);
        expect(!state.rung(EmberBackState.Trial.EAST) && state.rungCount() == 0 && EmberBackTrials.quiet(back, "smith") && EmberBackTrials.quiet(back, "east")
                && !EmberBackTrials.quiet(back, "west"), "a quiet east rang its stone for free or did not quiet the smith room");
        expect(state.forgePattern(4) == 0, "a quiet east does not use the gentlest forging order");
        expect(!state.hasProgress(), "a quiet trial counted as progress");
        expect(!EmberBackState.of(back, GROUP_B).quiet(EmberBackState.Trial.EAST), "group A's quiet trial leaked into group B");
        // 心臓を倒したことがあれば、次のリセットで一つ静かな試練になる (同じ試練は二回続けて静かにならない)
        state.markEverDefeated();
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, false);
        expect(EmberRooms.owner(EmberRooms.EAST) == null, "a group reset kept the group's room");
        expect(state.quiet() != null && state.quiet() != EmberBackState.Trial.EAST, "after the heart fell, a reset does not quiet another trial: "
                + state.quiet());
        expect(state.everDefeated(), "a reset forgot that the group stilled the heart once");
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
        expect(state.quiet() == null, "noSleep still quieted a trial");
        state.setEverDefeated(false);
    }

    // 二つのグループ: 部屋の占有・グループの敵・心臓の撃破のあとのリセット・放置のリセット・グループが変わった人
    private static void checkGroups(ServerLevel back) {
        FakePlayer a = fake(back, GROUP_A, "ember_test_group_a");
        FakePlayer b = fake(back, GROUP_B, "ember_test_group_b");
        // 部屋の占有: A が灰の道を持っているあいだ、入ってきた B は広間へ戻され、持ち主は A のまま
        EmberRooms.claimForTest(back, EmberRooms.WEST, GROUP_A);
        BlockPos in = rest("w0").floor().above();
        a.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5);
        b.moveTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5);
        back.addFreshEntity(a);
        back.addFreshEntity(b);
        try {
            jp.revati.skyabyss.titan.group.RoomLocks.update(back, EmberRooms.WEST.lock(), EmberRooms.WEST.box().aabb(), EmberRooms.WEST.eject(),
                    EmberRooms.WEST.grace(), null);
            expect(GROUP_A.equals(EmberRooms.owner(EmberRooms.WEST)), "the west room changed hands while group A was inside");
            expect(!EmberRooms.WEST.contains(b.position()), "group B was not sent out of the west room held by group A (at " + b.blockPosition() + ")");
            expect(EmberRooms.WEST.contains(a.position()), "the owner was sent out of its own room");
            // 部屋の数え方は持ち主だけ
            b.moveTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5);
            List<net.minecraft.server.level.ServerPlayer> counted = jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.playersIn(back, WEST);
            expect(counted.contains(a) && !counted.contains(b), "the west trial counts players of another group");
            // 試練の敵は持ち主のグループのもの: B には見えず、B を狙わず、B の傷を受けない
            net.minecraft.world.entity.Mob moth = jp.revati.skyabyss.titan.ember.back.trial.EmberTrials.spawn(back,
                    jp.revati.skyabyss.titan.ember.mob.EmberMobSetup.EMBER_MOTH.get(), in.getX() + 0.5, in.getY() + 2, in.getZ() + 3.5);
            if (moth == null) {
                FAILURES.add("could not spawn a moth for the group checks");
            } else {
                expect(moth instanceof jp.revati.skyabyss.titan.group.GroupOwned o && GROUP_A.equals(o.groupId()), "a trial mob in group A's room is not group A's");
                expect(jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(moth, a) && !jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(moth, b),
                        "group A's moth is visible to group B (or hidden from A)");
                expect(moth.canAttack(a) && !moth.canAttack(b), "group A's moth may attack group B (or not A)");
                float hp = moth.getHealth();
                moth.hurt(back.damageSources().playerAttack(b), 2f);
                expect(moth.getHealth() == hp, "group B hurt group A's moth");
                moth.discard();
            }
        } finally {
            a.discard();
            b.discard();
            EmberRooms.releaseForTest(back, EmberRooms.WEST);
        }
        // 心臓を倒したグループだけ、記録が変わる。門の奥に誰もいなければ 30 秒でそのグループだけ次の周
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
        int epochB = EmberBackState.of(back, GROUP_B).epoch();
        EmberBackState.of(back, GROUP_A).markHeartDefeated();
        expect(!EmberBackState.of(back, GROUP_B).heartDefeated(), "group A's heart kill was written to group B");
        int epochA = EmberBackState.of(back, GROUP_A).epoch();
        // いま背中にいたことにする (放置のリセットではなく、心臓のあとのリセットで戻ることを見る)
        EmberBackState.of(back, GROUP_A).record().touch(back.getGameTime());
        EmberBackState.of(back, GROUP_B).record().touch(back.getGameTime());
        for (int t = 0; t <= EmberBackTrials.RESET_AFTER_HEART; t += 10) {
            invokeTickReset(back);
        }
        expect(EmberBackState.of(back, GROUP_A).epoch() > epochA && !EmberBackState.of(back, GROUP_A).heartDefeated(),
                "group A's trials were not renewed 30 s after its heart kill");
        expect(EmberBackState.of(back, GROUP_A).everDefeated(), "the renewal forgot that group A stilled the heart");
        expect(EmberBackState.of(back, GROUP_B).epoch() == epochB, "group A's renewal touched group B");
        EmberBackState.of(back, GROUP_A).setEverDefeated(false);
        EmberBackTrials.resetGroup(back, GROUP_A, false, null, true);
        // グループが変わった人 (パーティに入った): 試練の中なら広間へ。休み場の記録は番号が違えば消える
        FakePlayer c = fake(back, GROUP_A, "ember_test_group_change");
        BlockPos w1 = rest("w1").floor().above();
        c.moveTo(w1.getX() + 0.5, w1.getY(), w1.getZ() + 0.5);
        EmberBackTrials.persisted(c).putString("skyabyss_ember_checkpoint", "w1");
        EmberBackTrials.persisted(c).putInt("skyabyss_ember_checkpoint_epoch", -5);
        EmberBackTrials.onGroupChanged(c);
        expect(HUB.contains(c.blockPosition()), "a player whose group changed inside a trial was not moved to the hub (at " + c.blockPosition() + ")");
        expect(EmberBackTrials.validCheckpoint(EmberBackTrials.persisted(c), EmberBackState.of(back, GROUP_A).epoch()).isEmpty()
                && !EmberBackTrials.persisted(c).contains("skyabyss_ember_checkpoint"), "a stale checkpoint survived the group change");
        checkParty(back);
        NOTES.add("two groups: own gates, stones, rooms, mobs, renewals");
    }

    private static void invokeTickReset(ServerLevel back) {
        try {
            java.lang.reflect.Method m = EmberBackTrials.class.getDeclaredMethod("tickReset", ServerLevel.class);
            m.setAccessible(true);
            m.invoke(null, back);
        } catch (ReflectiveOperationException e) {
            FAILURES.add("could not run the reset tick: " + e);
        }
    }

    // パーティ (6 人まで) を組む・入る・抜ける: 記録の引き継ぎ (GroupEvents)
    private static void checkParty(ServerLevel back) {
        var parties = jp.revati.skyabyss.party.PartyData.get(back.getServer());
        var data = jp.revati.skyabyss.titan.group.BackGroupData.get(back);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < jp.revati.skyabyss.party.Parties.MAX_MEMBERS; i++) {
            ids.add(UUID.nameUUIDFromBytes(("skyabyss ember party test " + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        jp.revati.skyabyss.party.PartyData.Party party = null;
        try {
            // リーダーは西の石を鳴らしている。入る人は何も鳴らしていない
            EmberBackState.of(back, ids.get(0)).ring(EmberBackState.Trial.WEST);
            party = parties.create(ids.get(0), "lead");
            for (int i = 1; i < ids.size(); i++) {
                parties.add(party, ids.get(i), "m" + i);
            }
            UUID key = jp.revati.skyabyss.titan.group.Groups.keyOf(back.getServer(), ids.get(5));
            expect(key.equals(party.id) && party.members.size() == 6, "a party of six does not share one group key");
            expect(EmberBackState.of(back, party.id).rung(EmberBackState.Trial.WEST), "a new party does not start from its leader's record");
            // パーティの記録で進む。抜けた人は、そのときのパーティの記録の写しを持って一人に戻る
            EmberBackState.of(back, party.id).ring(EmberBackState.Trial.SOUTH);
            parties.remove(ids.get(5));
            EmberBackState left = EmberBackState.of(back, ids.get(5));
            expect(left.rung(EmberBackState.Trial.WEST) && left.rung(EmberBackState.Trial.SOUTH), "a player leaving the party lost the party's progress");
            expect(jp.revati.skyabyss.titan.group.Groups.keyOf(back.getServer(), ids.get(5)).equals(ids.get(5)), "a player who left is still in the party group");
        } finally {
            for (UUID id : ids) {
                parties.remove(id);
                data.remove(id);
            }
            if (party != null) {
                data.remove(party.id);
            }
        }
    }

    // ---------------------------------------------------------------- 熱
    private static void checkHeat(ServerLevel back) {
        FakePlayer p = new FakePlayer(back, new GameProfile(UUID.randomUUID(), "ember_test_heat_b"));
        // 灰溜まり (広間の壁ぎわ)
        BlockPos ash = HUB_CENTER.offset(EmberZones.HUB_ASH[0][0] + 1, 1, EmberZones.HUB_ASH[0][1]);
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
        // 火炎耐性で半分、角杯の口 (強いほうだけ)、重ねる口と下限 0.4
        EmberHeat.set(p, 0);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        expect(EmberHeat.add(p, 20, EmberHeat.Reason.VENT) == 10f, "fire resistance does not halve heat gain");
        p.removeAllEffects();
        expect(Math.abs(EmberHeat.gainMultiplier(p, EmberHeat.Reason.VENT) - 1f) < 1e-6, "the multiplier is not 1 without gear");
        // 焦げ: 100 で 1 秒に 2、85 を切るまで (熱の動かない休み場で見る)
        p.moveTo(rest.getX() + 0.5, rest.getY(), rest.getZ() + 0.5);
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
