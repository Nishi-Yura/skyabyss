package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 三つの試練の自己テスト (SKYABYSS_SELFTEST。ログに SELFTEST ember trial PASSED / FAILED)。13 の 4 章、11.2節の担当 C1・C2・C3。
// 余燼の背の三つの部屋の箱 (西・東・南) に、中身を建てて確かめる (体はまだ無くてよい。箱の外は触らない)。
//   段取り: 箱の読み込み → 建てる → 100 tick 待つ (溶岩を流す) → 検査 → 仕掛けの動き → 灰被りが歩いて着くのを待つ
//   検査: 箱の外が変わっていない、溶岩が湖の外へ漏れていない、取っておく場所が空いている、
//         灰の道は 3 通りとも渡れる (壺の 5 回で次の島へ届く)、冷え殻の時計 (12 秒・蛾・人が乗って待つ)、呑まれた、
//         鍛えの間は 4 通りとも解ける (一人が鞴から金床へ走る時間を入れて熱を流す)、割れ・ひび・白・焼き入れ・扉、
//         火守りは一人でも四人でも 100 まで燃やせる (熾・灰運び・蛾の模擬)、噴気の巡りと当たり、灰運びと灰被りが炉・火床へ歩いて着く
public final class EmberTrialSelfTest {
    private static final int CX1 = -3;
    private static final int CX2 = 2;
    private static final int CZ1 = -1;
    private static final int CZ2 = 3;

    private static final List<String> F = new ArrayList<>();
    private static final Map<String, BlockState[]> RINGS = new HashMap<>();
    private static final Map<String, BlockState[]> RESERVED = new HashMap<>();
    private static final List<EmberBackLayout.Box> TRIAL_BOXES = List.of(EmberBackLayout.WEST, EmberBackLayout.EAST, EmberBackLayout.SOUTH);
    private static String lastAim = "";
    private static AshThrall douser;
    private static AshThrall carrier;
    private static int waited;
    private static boolean douserDone;
    private static boolean carrierDone;

    private EmberTrialSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember trial (load)", EmberTrialSelfTest::load),
                SelfTestRunner.Step.once("ember trial (build)", 60, EmberTrialSelfTest::build),
                SelfTestRunner.Step.once("ember trial (settle)", 120, s -> {
                }),
                SelfTestRunner.Step.once("ember trial (static)", 5, EmberTrialSelfTest::staticChecks),
                SelfTestRunner.Step.once("ember trial (mechanics)", 5, EmberTrialSelfTest::mechanics),
                SelfTestRunner.Step.until("ember trial (walkers)", EmberTrialSelfTest::walkers),
                SelfTestRunner.Step.once("ember trial", 5, EmberTrialSelfTest::finish));
    }

    private static void expect(boolean ok, String failure) {
        if (!ok) {
            F.add(failure);
        }
    }

    // 傷を受けて、位置合わせもできる偽のプレイヤー (本物の FakePlayer は傷を受けず、位置合わせもしない)
    private static final class TestPlayer extends FakePlayer {
        TestPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        float lastDamage;
        net.minecraft.world.damagesource.DamageSource lastSource;

        @Override
        public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
        }

        // 新しい人は少しのあいだ傷を受けない (spawnInvulnerableTime) ので、傷の出し方だけ記録する
        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            lastDamage = amount;
            lastSource = source;
            return true;
        }
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    // ---------------------------------------------------------------- 読み込みと建築
    static void load(MinecraftServer server) {
        F.clear();
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back == null) {
            return;
        }
        for (int cx = CX1; cx <= CX2; cx++) {
            for (int cz = CZ1; cz <= CZ2; cz++) {
                back.setChunkForced(cx, cz, true);
            }
        }
    }

    private static List<BlockPos> ring(EmberBackLayout.Box b) {
        List<BlockPos> out = new ArrayList<>();
        EmberBackLayout.Box g = b.grow(1);
        for (int x = g.minX(); x <= g.maxX(); x++) {
            for (int y = g.minY(); y <= g.maxY(); y++) {
                for (int z = g.minZ(); z <= g.maxZ(); z++) {
                    if (!b.contains(x, y, z) && !EmberBackLayout.SMITH_DOOR.contains(x, y, z)) {
                        out.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    static void build(MinecraftServer server) {
        ServerLevel level = server.getLevel(EmberIds.BACK);
        if (level == null) {
            F.add("no ember_back level");
            return;
        }
        EmberTrials.dryRun = true;
        // 箱の外 1 マスの様子を覚える (建てたあとに変わっていないか)
        for (EmberBackLayout.Box b : TRIAL_BOXES) {
            List<BlockPos> cells = ring(b);
            BlockState[] states = new BlockState[cells.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = level.getBlockState(cells.get(i));
            }
            RINGS.put(b.toString(), states);
        }
        // 取っておく場所 (床より上) の様子も覚える (B が先に休み場や石を置いていてもよい。私が触らないことを見る)
        for (String room : List.of("west", "east", "south")) {
            List<BlockState> cells = new ArrayList<>();
            int floor = EmberBackLayout.floorOf(room);
            for (EmberBackLayout.Box r : EmberBackLayout.reserved(room)) {
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int y = Math.max(r.minY(), floor + 1); y <= r.maxY(); y++) {
                        for (int z = r.minZ(); z <= r.maxZ(); z++) {
                            cells.add(level.getBlockState(new BlockPos(x, y, z)));
                        }
                    }
                }
            }
            RESERVED.put(room, cells.toArray(new BlockState[0]));
        }
        // B の受け口に登録されているか
        for (String room : List.of("west", "east", "south")) {
            expect(EmberBackRooms.hasBuilder(room), "no builder registered for room " + room);
        }
        AshPathRoom.build(level, EmberBackLayout.WEST);
        ForgeRoom.build(level, EmberBackLayout.EAST);
        HearthRoom.build(level, EmberBackLayout.SOUTH);
    }

    // ---------------------------------------------------------------- 静的な検査 (溶岩を 100 tick 流したあと)
    static void staticChecks(MinecraftServer server) {
        ServerLevel level = server.getLevel(EmberIds.BACK);
        if (level == null) {
            return;
        }
        // 箱の外は変わっていない
        for (EmberBackLayout.Box b : TRIAL_BOXES) {
            List<BlockPos> cells = ring(b);
            BlockState[] before = RINGS.get(b.toString());
            int changed = 0;
            BlockPos first = null;
            for (int i = 0; i < cells.size(); i++) {
                if (before[i] != level.getBlockState(cells.get(i))) {
                    changed++;
                    first = first == null ? cells.get(i) : first;
                }
            }
            expect(changed == 0, "blocks outside box " + b + " changed: " + changed + " (first " + first + ")");
        }
        // 溶岩は湖の中だけ (西の箱の中で溶岩があるのは、湖の範囲の y 74〜76 だけ)。東と南の箱に溶岩はない
        int lavaInLake = 0;
        for (EmberBackLayout.Box b : TRIAL_BOXES) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)) {
                            boolean inLake = b == EmberBackLayout.WEST && x >= AshPath.LAKE_X1 && x <= AshPath.LAKE_X2 && z >= AshPath.LAKE_Z1
                                    && z <= AshPath.LAKE_Z2 && y >= 74 && y <= AshPath.LAVA_Y;
                            if (inLake) {
                                lavaInLake++;
                            } else {
                                F.add("lava leaked to " + pos + " (box " + b + ")");
                                x = b.maxX();
                                y = b.maxY();
                                z = b.maxZ();
                            }
                        }
                    }
                }
            }
        }
        expect(lavaInLake > 2000, "the lake holds too little lava: " + lavaInLake);
        // 取っておく場所は、建てる前と同じ (床より上に私は置かない)
        for (String room : List.of("west", "east", "south")) {
            int floor = EmberBackLayout.floorOf(room);
            BlockState[] before = RESERVED.get(room);
            int k = 0;
            int changed = 0;
            BlockPos first = null;
            for (EmberBackLayout.Box r : EmberBackLayout.reserved(room)) {
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int y = Math.max(r.minY(), floor + 1); y <= r.maxY(); y++) {
                        for (int z = r.minZ(); z <= r.maxZ(); z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            if (before[k++] != level.getBlockState(pos)) {
                                changed++;
                                first = first == null ? pos : first;
                            }
                        }
                    }
                }
            }
            expect(changed == 0, "reserved cells of " + room + " changed: " + changed + " (first " + first + ")");
        }
        // 床 (取っておく場所の下も) があり、溶岩に面した所に空気がない (どの島・岸の床の上にも立てる)
        for (AshPath.Island i : AshPath.PATTERNS.get(AshPath.patternOf(level))) {
            BlockState top = level.getBlockState(new BlockPos(i.cx(), AshPath.LAVA_Y, i.cz()));
            expect(!top.isAir() && !top.is(Blocks.LAVA), "island floor missing at " + i);
            BlockPos pud = i.puddle();
            expect(level.getBlockState(pud).is(Blocks.GRAY_WOOL), "ash pool missing at " + pud);
        }
        // 道具の位置のブロック
        expect(level.getBlockState(Forge.FORGE).is(EmberTrialSetup.ASH_FORGE.get()), "forge block");
        expect(level.getBlockState(Forge.ANVIL).is(EmberTrialSetup.ASH_ANVIL.get()), "anvil block");
        expect(level.getBlockState(Forge.BELLOWS_A).is(EmberTrialSetup.FORGE_BELLOWS.get()), "bellows A");
        expect(level.getBlockState(Forge.BELLOWS_B).is(EmberTrialSetup.FORGE_BELLOWS.get()), "bellows B");
        expect(level.getBlockState(Forge.TROUGH_A).is(EmberTrialSetup.QUENCH_TROUGH.get()), "trough");
        expect(level.getBlockState(Hearth.HEARTH).is(EmberTrialSetup.EMBER_HEARTH.get()), "hearth block");
        expect(level.getBlockState(new BlockPos(-19, 77, -4)).is(EmberTrialSetup.ASH_URN_STAND.get()), "urn stand");
        for (BlockPos v : HearthVents.VENTS) {
            expect(level.getBlockState(v).is(EmberTrialSetup.HEAT_VENT.get()), "vent block at " + v);
            expect(EmberBackLayout.SOUTH.contains(v), "vent outside its box " + v);
        }
        for (BlockPos p : HearthRoom.PUDDLES) {
            expect(level.getBlockState(p).is(Blocks.GRAY_WOOL), "valley ash pool at " + p);
        }
        expect(HearthVents.VENTS.size() == 10, "10 vents");
        // 扉は閉じている
        EmberBackLayout.Box d = EmberBackLayout.SMITH_DOOR;
        expect(level.getBlockState(new BlockPos(d.minX(), d.minY(), d.minZ())).is(ModBlocks.SEALED_GATE.get()), "smith door is not sealed");
        // 道具は箱の中
        for (BlockPos p : List.of(Forge.FORGE, Forge.ANVIL, Forge.BELLOWS_A, Forge.BELLOWS_B, Forge.TROUGH_A, Forge.TROUGH_B, Forge.FORGE_FRONT, Forge.ANTE_SPAWN)) {
            expect(EmberBackLayout.EAST.contains(p), "forge fixture outside the east box: " + p);
        }
        // 打つ順 (刻みから読む) は 4 通りのどれか
        int[] pat = Forge.pattern(level);
        boolean known = false;
        for (int[] k : Forge.PATTERNS) {
            known |= java.util.Arrays.equals(k, pat);
        }
        expect(known, "pattern on the anvil marks is unknown: " + java.util.Arrays.toString(pat));
    }

    // ---------------------------------------------------------------- 仕掛けの動き
    static void mechanics(MinecraftServer server) {
        ServerLevel level = server.getLevel(EmberIds.BACK);
        if (level == null) {
            return;
        }
        try {
            registry(level);
            westCross(level);
            westCrust(level);
            westUrn(level);
            eastSolve(level);
            eastRules(level);
            eastDoor(level);
            southNumbers(level);
            southVents(level);
            southSimulate(level);
            resets(level);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember trial crashed", e);
            F.add("crashed: " + e);
        }
    }

    private static void registry(ServerLevel level) {
        for (String id : List.of(EmberIds.COOLED_CRUST, EmberIds.ASH_URN_STAND, EmberIds.ASH_FORGE, EmberIds.FORGE_BELLOWS, EmberIds.ASH_ANVIL,
                EmberIds.QUENCH_TROUGH, EmberIds.EMBER_HEARTH, EmberIds.HEAT_VENT)) {
            expect(BuiltInRegistries.BLOCK.containsKey(EmberIds.id(id)), "block not registered: " + id);
            expect(BuiltInRegistries.ITEM.containsKey(EmberIds.id(id)), "block item not registered: " + id);
        }
        for (String id : List.of(EmberIds.ASH_URN, EmberIds.FIRE_KEY_HOT, EmberIds.FIRE_KEY, EmberIds.LIVE_COAL)) {
            expect(BuiltInRegistries.ITEM.containsKey(EmberIds.id(id)), "item not registered: " + id);
        }
        for (String s : List.of(EmberIds.SOUND_ASH_SCATTER, EmberIds.SOUND_CRUST_CRACK, EmberIds.SOUND_CRUST_SINK, EmberIds.SOUND_ENGULF,
                EmberIds.SOUND_BELLOWS, EmberIds.SOUND_ANVIL_STRIKE, EmberIds.SOUND_ANVIL_TING, EmberIds.SOUND_BLOOM_CRACK, EmberIds.SOUND_QUENCH,
                EmberIds.SOUND_DOOR_KINDLE, EmberIds.SOUND_VENT_HISS, EmberIds.SOUND_VENT_BURST, EmberIds.SOUND_HEARTH_FEED, EmberIds.SOUND_HEARTH_DOUSE)) {
            expect(BuiltInRegistries.SOUND_EVENT.containsKey(EmberIds.id(s)), "sound not registered: " + s);
        }
        expect(EmberTrialSetup.COOLED_CRUST.getId().getPath().equals(EmberIds.COOLED_CRUST), "crust id is not the EmberIds one");
    }

    // ---------------------------------------------------------------- 灰の道: 3 通りとも渡れる
    private record Node(String name, Set<Long> cells, double cx, double cz) {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int kx(long k) {
        return (int) (k >> 32);
    }

    private static int kz(long k) {
        return (int) k;
    }

    private static Node islandNode(AshPath.Island i) {
        Set<Long> cells = new HashSet<>();
        for (int x = i.cx() - i.half(); x <= i.cx() + i.half(); x++) {
            for (int z = i.cz() - i.half(); z <= i.cz() + i.half(); z++) {
                if (i.contains(x, z)) {
                    cells.add(key(x, z));
                }
            }
        }
        if (i.w1()) {
            cells.add(key(i.puddle().getX(), i.puddle().getZ()));
        }
        return new Node("island " + i.cx() + "," + i.cz(), cells, i.cx(), i.cz());
    }

    private static Node bandNode(String name, int z1, int z2) {
        Set<Long> cells = new HashSet<>();
        for (int x = AshPath.LAKE_X1; x <= AshPath.LAKE_X2; x++) {
            for (int z = z1; z <= z2; z++) {
                cells.add(key(x, z));
            }
        }
        return new Node(name, cells, -25, (z1 + z2) / 2.0);
    }

    // 立っている所から、向いた先の溶岩に壺を使う (本物の使い方で。目の高さから向きを決める)。まけた数
    private static int throwAt(ServerLevel level, FakePlayer fp, int sx, int sz, int tx, int tz, ItemStack urn) {
        fp.setPos(sx + 0.5, AshPath.LAVA_Y + 1, sz + 0.5);
        double ex = sx + 0.5;
        double ey = AshPath.LAVA_Y + 1 + fp.getEyeHeight();
        double ez = sz + 0.5;
        double dx = tx + 0.5 - ex;
        double dy = AshPath.LAVA_Y + 0.85 - ey;
        double dz = tz + 0.5 - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        fp.setYRot(yaw);
        fp.setYHeadRot(yaw);
        fp.yRotO = yaw;
        fp.yHeadRotO = yaw;
        fp.setXRot((float) (-Math.toDegrees(Math.asin(dy / len))));
        lastAim = "stand " + sx + "," + sz + " target " + tx + "," + tz + " d " + dx + "," + dy + "," + dz + " yaw " + fp.getYRot() + " pitch " + fp.getXRot();
        return AshPath.useUrn(level, fp, urn);
    }

    // from の岸から to へ、壺で道を作って渡る。使った回数 (届かなければ -1)
    private static int cross(ServerLevel level, FakePlayer fp, Node from, Node to) {
        double best = Double.MAX_VALUE;
        long a = 0;
        long b = 0;
        for (long fa : from.cells()) {
            for (long tb : to.cells()) {
                double d = Math.pow(kx(fa) - kx(tb), 2) + Math.pow(kz(fa) - kz(tb), 2);
                if (d < best) {
                    best = d;
                    a = fa;
                    b = tb;
                }
            }
        }
        int cx = kx(a);
        int cz = kz(a);
        int bx = kx(b);
        int bz = kz(b);
        ItemStack urn = new ItemStack(EmberTrialSetup.ASH_URN.get());
        AshUrnItem.setCharges(urn, AshPath.URN_CHARGES);
        int throwsUsed = 0;
        try {
            while (true) {
                for (long t : to.cells()) {
                    if (Math.abs(kx(t) - cx) + Math.abs(kz(t) - cz) == 1) {
                        return throwsUsed;
                    }
                }
                if (throwsUsed >= AshPath.URN_CHARGES) {
                    return -1;
                }
                double dx = bx - cx;
                double dz = bz - cz;
                double len = Math.max(1e-6, Math.hypot(dx, dz));
                int tx = Integer.MIN_VALUE;
                int tz = 0;
                for (double dist : new double[] {2.0, 1.5, 2.5, 1.0}) {
                    int x = (int) Math.round(cx + dx / len * dist);
                    int z = (int) Math.round(cz + dz / len * dist);
                    if (level.getBlockState(new BlockPos(x, AshPath.LAVA_Y, z)).is(Blocks.LAVA)) {
                        tx = x;
                        tz = z;
                        break;
                    }
                }
                if (tx == Integer.MIN_VALUE || throwAt(level, fp, cx, cz, tx, tz, urn) == 0) {
                    return -1;
                }
                throwsUsed++;
                // 殻の上を、行き先に近い端まで歩く
                double bd = Double.MAX_VALUE;
                int nx = cx;
                int nz = cz;
                for (int x = tx - 1; x <= tx + 1; x++) {
                    for (int z = tz - 1; z <= tz + 1; z++) {
                        if (level.getBlockState(new BlockPos(x, AshPath.LAVA_Y, z)).is(EmberTrialSetup.COOLED_CRUST.get())) {
                            double d = Math.pow(x - bx, 2) + Math.pow(z - bz, 2);
                            if (d < bd) {
                                bd = d;
                                nx = x;
                                nz = z;
                            }
                        }
                    }
                }
                if (nx == cx && nz == cz) {
                    return -1;
                }
                cx = nx;
                cz = nz;
            }
        } finally {
            for (BlockPos p : AshPath.crustKeys()) {
                AshPath.sink(level, p);
            }
        }
    }

    private static void westCross(ServerLevel level) {
        FakePlayer fp = fake(level, "ember_trial_cross");
        for (int pattern = 0; pattern < AshPath.PATTERNS.size(); pattern++) {
            AshPathRoom.buildLake(level, pattern);
            AshPath.clearMemory();
            List<Node> nodes = new ArrayList<>();
            nodes.add(bandNode("entrance shore", -2, -1));
            for (AshPath.Island i : AshPath.PATTERNS.get(pattern)) {
                nodes.add(islandNode(i));
            }
            nodes.add(bandNode("far shore", 49, 50));
            int n = nodes.size();
            expect(AshPath.PATTERNS.get(pattern).size() == 7, "pattern " + pattern + " has " + AshPath.PATTERNS.get(pattern).size() + " islands");
            expect(AshPath.PATTERNS.get(pattern).stream().anyMatch(AshPath.Island::w1), "pattern " + pattern + " lacks the w1 island");
            int[][] throwsOf = new int[n][n];
            for (int[] row : throwsOf) {
                java.util.Arrays.fill(row, -1);
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i == j || nodes.get(j).cz() <= nodes.get(i).cz() && j != n - 1 || i == n - 1) {
                        continue;
                    }
                    double dist = Math.hypot(nodes.get(i).cx() - nodes.get(j).cx(), nodes.get(i).cz() - nodes.get(j).cz());
                    if (dist > 24) {
                        continue;
                    }
                    throwsOf[i][j] = cross(level, fp, nodes.get(i), nodes.get(j));
                }
            }
            // 最短の道 (使う壺の回数が少ない)、島の数が最も少ない道
            int[] cost = new int[n];
            java.util.Arrays.fill(cost, Integer.MAX_VALUE);
            cost[0] = 0;
            for (int round = 0; round < n; round++) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        if (throwsOf[i][j] >= 0 && cost[i] != Integer.MAX_VALUE && cost[i] + throwsOf[i][j] < cost[j]) {
                            cost[j] = cost[i] + throwsOf[i][j];
                        }
                    }
                }
            }
            // 余裕のある道 (どの一跳びも 4 回以内) があること
            int[] worst = new int[n];
            java.util.Arrays.fill(worst, Integer.MAX_VALUE);
            worst[0] = 0;
            for (int round = 0; round < n; round++) {
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        if (throwsOf[i][j] >= 0 && worst[i] != Integer.MAX_VALUE && Math.max(worst[i], throwsOf[i][j]) < worst[j]) {
                            worst[j] = Math.max(worst[i], throwsOf[i][j]);
                        }
                    }
                }
            }
            expect(worst[n - 1] <= 4, "ash path pattern " + pattern + " has no route with every hop within 4 scatters (best " + worst[n - 1] + ")");
            expect(cost[n - 1] != Integer.MAX_VALUE, "ash path pattern " + pattern + " cannot be crossed with 5 scatters per hop");
            int w1 = -1;
            for (int i = 0; i < n; i++) {
                if (nodes.get(i).name().equals("island -25,24")) {
                    w1 = i;
                }
            }
            expect(w1 >= 0 && cost[w1] != Integer.MAX_VALUE, "the w1 island is unreachable in pattern " + pattern);
            int maxHop = 0;
            int edges = 0;
            for (int[] row : throwsOf) {
                for (int t : row) {
                    if (t >= 0) {
                        edges++;
                        maxHop = Math.max(maxHop, t);
                    }
                }
            }
            expect(edges >= 8, "pattern " + pattern + " has too few hops: " + edges);
            SkyAbyss.LOGGER.info("ember trial: ash path pattern {} crosses with {} scatters in total, easiest route needs at most {} per hop ({} hops, longest {})", pattern,
                    cost[n - 1], worst[n - 1], edges, maxHop);
        }
        AshPathRoom.buildLake(level, AshPath.patternOf(level));
        AshPath.clearMemory();
    }

    // ---------------------------------------------------------------- 冷え殻の時計・呑まれた
    private static void westCrust(ServerLevel level) {
        AshPath.clearMemory();
        BlockPos spot = new BlockPos(-33, AshPath.LAVA_Y, 20);
        int placed = AshPath.placeCrust(level, spot.getX(), spot.getZ());
        expect(placed == 9, "3 x 3 crust expected, got " + placed);
        BlockPos mid = spot;
        AshPath.Crust c = AshPath.crustAt(mid);
        expect(c != null, "crust has no clock");
        if (c == null) {
            return;
        }
        long now = level.getGameTime();
        expect(AshPath.LIFE == 240 && AshPath.lifeOf(c) == 240, "crust life is 12 s");
        // 8 秒までは黒、8〜11 秒で割れ目、11〜12 秒で沈みかけ
        List<ServerPlayer> none = List.of();
        c.born = now - 150;
        AshPath.tickCrusts(level, now, none);
        expect(level.getBlockState(mid).getValue(CooledCrustBlock.STAGE) == 0, "crust should be black before 8 s");
        c.born = now - 165;
        AshPath.tickCrusts(level, now, none);
        expect(level.getBlockState(mid).getValue(CooledCrustBlock.STAGE) == 1, "crust should crack from 8 s");
        c.born = now - 225;
        AshPath.tickCrusts(level, now, none);
        expect(level.getBlockState(mid).getValue(CooledCrustBlock.STAGE) == 2, "crust should sink from 11 s");
        // 人が乗っているあいだは最大 3 秒待つ
        FakePlayer fp = fake(level, "ember_trial_stand");
        fp.setPos(mid.getX() + 0.5, AshPath.LAVA_Y + 1, mid.getZ() + 0.5);
        c.born = now - 241;
        List<ServerPlayer> on = List.of(fp);
        for (int i = 0; i < 59; i++) {
            AshPath.tickCrusts(level, now + i, on);
        }
        expect(level.getBlockState(mid).is(EmberTrialSetup.COOLED_CRUST.get()), "crust vanished under a standing player before 3 s");
        for (int i = 59; i < 62; i++) {
            AshPath.tickCrusts(level, now + i, on);
        }
        expect(level.getBlockState(mid).is(Blocks.LAVA), "crust under a standing player should sink after 3 s of waiting");
        expect(AshPath.crustAt(mid) == null, "sunk crust still has a clock");
        // ほかの殻は人がいなければ 12 秒で溶岩に戻る
        BlockPos side = spot.west();
        AshPath.Crust c2 = AshPath.crustAt(side);
        if (c2 != null) {
            c2.born = now - 241;
        }
        AshPath.tickCrusts(level, now, none);
        expect(level.getBlockState(side).is(Blocks.LAVA), "crust without a player should sink at 12 s");
        // 蛾が触れると 3 秒早く崩れる (最短 5 秒は残る。一つの殻に 2 秒に一度)
        AshPath.placeCrust(level, spot.getX(), spot.getZ());
        AshPath.Crust m = AshPath.crustAt(mid);
        EmberMoth moth = EmberTrials.spawn(level, EmberMobSetup.EMBER_MOTH.get(), mid.getX() + 0.5, AshPath.LAVA_Y + 1.6, mid.getZ() + 0.5);
        expect(moth != null, "could not spawn a moth");
        if (m != null && moth != null) {
            moth.setNoAi(true);
            AshPath.mothTouch(level, now);
            expect(m.shorten == 60 && AshPath.lifeOf(m) == 180, "moth touch should cut 3 s: shorten " + m.shorten);
            AshPath.mothTouch(level, now + 10);
            expect(m.shorten == 60, "a crust is touched once per 2 s");
            AshPath.mothTouch(level, now + 50);
            AshPath.mothTouch(level, now + 100);
            AshPath.mothTouch(level, now + 150);
            expect(AshPath.lifeOf(m) == AshPath.MOTH_MIN_LIFE, "a crust must stay at least 5 s: life " + AshPath.lifeOf(m));
            moth.discard();
        }
        for (BlockPos p : AshPath.crustKeys()) {
            AshPath.sink(level, p);
        }
        // 呑まれた: 最後に立った島へ戻り、4 の傷と熱 +30、持ち物は失わない
        FakePlayer swimmer = new TestPlayer(level, new GameProfile(UUID.nameUUIDFromBytes("ember_trial_engulf".getBytes()), "ember_trial_engulf"));
        BlockPos stand = new BlockPos(-25, AshPath.LAVA_Y + 1, -4);
        AshPath.setLastStand(swimmer.getUUID(), stand);
        swimmer.getInventory().add(new ItemStack(Items.DIAMOND, 3));
        swimmer.setPos(-33.5, AshPath.LAVA_Y + 0.4, 20.5);
        EmberHeat.reset(swimmer);
        AshPath.checkEngulfed(swimmer);
        expect(swimmer.blockPosition().equals(stand), "engulfed player was not moved to the last island: " + swimmer.blockPosition());
        TestPlayer sw = (TestPlayer) swimmer;
        expect(sw.lastDamage == 4f && sw.lastSource != null && sw.lastSource.is(EmberIds.ENGULFED), "engulfed damage should be 4 of type engulfed, was " + sw.lastDamage);
        expect(EmberHeat.get(swimmer) >= 29.9f, "engulfed heat should be +30, was " + EmberHeat.get(swimmer));
        expect(EmberTrials.count(swimmer, Items.DIAMOND) == 3, "engulfing must not burn items");
        expect(!swimmer.isOnFire(), "engulfed player kept burning");
        EmberHeat.reset(swimmer);
        // 溶岩の傷ではなく、呑まれた (傷の種類 engulfed) で
        expect(!level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(EmberIds.ENGULFED).is(net.minecraft.tags.DamageTypeTags.IS_FIRE), "engulfed must not be a fire damage");
    }

    // ---------------------------------------------------------------- 壺
    private static void westUrn(ServerLevel level) {
        FakePlayer fp = fake(level, "ember_trial_urn");
        AshPath.giveUrn(fp);
        ItemStack urn = null;
        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
            if (fp.getInventory().getItem(i).is(EmberTrialSetup.ASH_URN.get())) {
                urn = fp.getInventory().getItem(i);
            }
        }
        expect(urn != null, "the urn stand did not give an urn");
        if (urn == null) {
            return;
        }
        expect(AshUrnItem.charges(urn) == 5, "a new urn holds 5 scatters");
        expect(urn.getMaxStackSize() == 1, "urn stacks to 1");
        // 5 回まいて、6 回目は空。灰溜まりで満ちる
        int[][] targets = {{-33, 20}, {-33, 24}, {-33, 28}, {-33, 32}, {-33, 36}};
        for (int[] t : targets) {
            int got = throwAt(level, fp, t[0] + 2, t[1], t[0], t[1], urn);
            if (got == 0) {
                net.minecraft.world.phys.Vec3 eye = fp.getEyePosition();
                net.minecraft.world.phys.BlockHitResult h = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(fp.getViewVector(1f).scale(4)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, fp));
                F.add("debug: " + lastAim + " eye " + eye + " look " + fp.getViewVector(1f) + " hit " + h.getType() + " " + h.getBlockPos() + " " + level.getBlockState(h.getBlockPos()));
            }
            expect(got > 0, "scatter at " + t[0] + "," + t[1] + " did not place a crust (cell " + level.getBlockState(new BlockPos(t[0], AshPath.LAVA_Y, t[1])) + ", pattern " + AshPath.patternOf(level) + ")");
        }
        expect(AshUrnItem.charges(urn) == 0, "urn should be empty after 5 scatters: " + AshUrnItem.charges(urn));
        int before = AshPath.crustCount();
        expect(throwAt(level, fp, -31, 40, -33, 40, urn) == 0 && AshPath.crustCount() == before, "an empty urn must not scatter");
        // 溶岩でない所 (島の床) に向けては何も起きない
        AshUrnItem.setCharges(urn, 5);
        BlockPos pud = AshPath.PATTERNS.get(AshPath.patternOf(level)).get(0).puddle();
        int c0 = AshPath.crustCount();
        throwAt(level, fp, pud.getX(), pud.getZ() + 1, pud.getX(), pud.getZ(), urn);
        expect(AshPath.crustCount() == c0, "scattering on an island floor must not make crust");
        AshUrnItem.setCharges(urn, 1);
        expect(AshPath.refillAt(level, fp, pud) && AshUrnItem.charges(urn) == 5, "ash pool did not refill the urn");
        expect(!AshPath.refillAt(level, fp, pud.above(5)), "refill away from a pool");
        for (BlockPos p : AshPath.crustKeys()) {
            AshPath.sink(level, p);
        }
        // 島の上で壺を失った人 (休み場 w1 で目覚めた、落とした) には壺が戻る。入口の岸では戻らない
        fp.getInventory().clearContent();
        fp.setPos(-24.5, AshPath.LAVA_Y + 1, 24.5);
        AshPath.rescueUrn(level, fp);
        expect(EmberTrials.count(fp, EmberTrialSetup.ASH_URN.get()) == 1, "a player stranded on the w1 island without an urn got none");
        fp.getInventory().clearContent();
        fp.setPos(-24.5, AshPath.LAVA_Y + 1, -3.5);
        AshPath.rescueUrn(level, fp);
        expect(EmberTrials.count(fp, EmberTrialSetup.ASH_URN.get()) == 0, "the entry shore must hand out the urn only from the stand");
        AshPath.giveUrn(fp);
        // 湖を出ると壺は台へ戻る
        fp.setPos(26, 82, 5);
        EmberTrials.patrol(level, List.of(fp));
        expect(EmberTrials.count(fp, EmberTrialSetup.ASH_URN.get()) == 0, "the urn left the lake with the player");
    }

    // ---------------------------------------------------------------- 鍛えの間: 4 通りとも解ける
    private static void eastSolve(ServerLevel level) {
        FakePlayer fp = fake(level, "ember_trial_smith");
        for (int p = 0; p < Forge.PATTERNS.length; p++) {
            int[] pat = Forge.PATTERNS[p];
            ForgeRoom.buildInner(level, pat);
            Forge.clearMemory();
            fp.getInventory().clearContent();
            EmberHeat.reset(fp);
            expect(java.util.Arrays.equals(Forge.pattern(level), pat), "marks do not read back as pattern " + p);
            long vt = level.getGameTime();
            long start = vt;
            // 一人でやる動き: 鞴を踏んで金床へ走る (40 tick)。下げる色は金床で待つ
            boolean atAnvil = true;
            long lastPress = Long.MIN_VALUE / 2;
            Forge.StrikeResult last = null;
            outer:
            for (int hit = 0; hit < pat.length; hit++) {
                int target = pat[hit];
                for (int guard = 0; guard < 6000; guard++) {
                    int band = Forge.bandOf(Forge.heat());
                    if (band == target && atAnvil) {
                        last = Forge.strike(level, fp);
                        expect(last == (hit == pat.length - 1 ? Forge.StrikeResult.DONE : Forge.StrikeResult.HIT),
                                "pattern " + p + " hit " + hit + " gave " + last + " at heat " + Forge.heat());
                        if (last != Forge.StrikeResult.HIT && last != Forge.StrikeResult.DONE) {
                            break outer;
                        }
                        break;
                    }
                    if (band > target && atAnvil) {
                        Forge.stepHeat(level, vt++);
                        continue;
                    }
                    if (band >= target && !atAnvil) {
                        // 鞴にいて、目当て以上になった: 走って戻る (40 tick で 8 下がる)
                        for (int k = 0; k < 40; k++) {
                            Forge.stepHeat(level, vt++);
                        }
                        atAnvil = true;
                        continue;
                    }
                    // 目当てより低い: 鞴へ走って、走って戻る分を見込んで踏む
                    if (atAnvil) {
                        for (int k = 0; k < 40; k++) {
                            Forge.stepHeat(level, vt++);
                        }
                        atAnvil = false;
                    }
                    int[] range = switch (target) {
                        case Forge.RED -> new int[] {20, 45};
                        case Forge.ORANGE -> new int[] {45, 70};
                        default -> new int[] {70, 90};
                    };
                    if (Forge.heat() - 8f >= range[0] + 3f || Forge.heat() >= range[1] - 1f) {
                        for (int k = 0; k < 40; k++) {
                            Forge.stepHeat(level, vt++);
                        }
                        atAnvil = true;
                    } else if (vt - lastPress >= Forge.PRESS_GAP) {
                        Forge.press(level, 0);
                        lastPress = vt;
                    } else {
                        Forge.stepHeat(level, vt++);
                    }
                }
            }
            expect(last == Forge.StrikeResult.DONE, "pattern " + p + " was not solved");
            long took = vt - start;
            expect(took <= 2400, "pattern " + p + " took " + took + " ticks for one player");
            expect(EmberTrials.count(fp, EmberTrialSetup.FIRE_KEY_HOT.get()) == 1, "pattern " + p + " did not give the hot key");
            expect(EmberHeat.get(fp) >= 19.9f, "forging a key should add 20 heat: " + EmberHeat.get(fp));
            SkyAbyss.LOGGER.info("ember trial: forge pattern {} {} solved by one player in {} ticks", p, java.util.Arrays.toString(pat), took);
        }
        // 初めてはいちばんやさしい順 (赤→橙→黄)、そのあとは種で 4 通り
        expect(java.util.Arrays.equals(Forge.patternFor(false, 12345), Forge.PATTERNS[0]), "first challenge is not the easiest pattern");
        Set<Integer> seen = new HashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            seen.add(java.util.Arrays.hashCode(Forge.patternFor(true, seed)));
        }
        expect(seen.size() == 4, "seeds should reach all 4 patterns, reached " + seen.size());
        expect(EmberTrials.firstForgeCalls >= 4, "strikes should record the first forge use");
    }

    private static void eastRules(ServerLevel level) {
        FakePlayer fp = fake(level, "ember_trial_rules");
        ForgeRoom.buildInner(level, Forge.PATTERNS[0]);
        Forge.clearMemory();
        long gt = level.getGameTime();
        // 帯: 黒 0〜19、赤 20〜44、橙 45〜69、黄 70〜89、白 90〜
        expect(Forge.bandOf(19f) == Forge.BLACK && Forge.bandOf(20f) == Forge.RED && Forge.bandOf(44.9f) == Forge.RED && Forge.bandOf(45f) == Forge.ORANGE
                && Forge.bandOf(69.9f) == Forge.ORANGE && Forge.bandOf(70f) == Forge.YELLOW && Forge.bandOf(89.9f) == Forge.YELLOW
                && Forge.bandOf(90f) == Forge.WHITE, "heat bands");
        // 熱は 1 秒に −4、鞴は 1 回 +12 (一人で踏める: 板は 2 つ、同じ板は 6 tick あける)
        Forge.setHeat(50f);
        for (int i = 0; i < 20; i++) {
            Forge.stepHeat(level, gt);
        }
        expect(Math.abs(Forge.heat() - 46f) < 0.05f, "heat should fall 4 per second: " + Forge.heat());
        Forge.press(level, 0);
        expect(Math.abs(Forge.heat() - 58f) < 0.05f, "a bellows press should add 12: " + Forge.heat());
        // 白で止まり続けない: 100 から 3 秒で黄に入る (待てば下がる)
        Forge.setHeat(100f);
        for (int i = 0; i < 60 * 20 / 4; i++) {
            Forge.stepHeat(level, gt);
        }
        expect(Forge.heat() < 90f, "white must cool within 3 s...");
        // 違う色で打つとひび、2 回で割れる。5 秒後に新しい塊
        Forge.setHeat(60f);
        expect(Forge.strike(level, fp) == Forge.StrikeResult.CRACK && Forge.cracks() == 1, "wrong color should crack the bloom");
        expect(level.getBlockState(Forge.FORGE).getValue(ForgeBlocks.AshForgeBlock.CRACKED), "the forge block should show the crack");
        expect(Forge.strike(level, fp) == Forge.StrikeResult.BROKEN && !Forge.bloomPresent(), "the second wrong strike should break the bloom");
        expect(level.getBlockState(Forge.FORGE).getValue(ForgeBlocks.AshForgeBlock.STAGE) == 5, "an empty mouth should show stage 5");
        expect(Forge.strike(level, fp) == Forge.StrikeResult.NO_BLOOM, "striking an empty mouth");
        Forge.stepHeat(level, level.getGameTime() + Forge.BLOOM_RETURN_TICKS - 5);
        expect(!Forge.bloomPresent(), "the bloom came back too early");
        Forge.stepHeat(level, level.getGameTime() + Forge.BLOOM_RETURN_TICKS + 5);
        expect(Forge.bloomPresent() && Forge.hits() == 0 && Forge.cracks() == 0, "the bloom did not come back after 5 s");
        // 白で打つとすぐ割れる
        Forge.setHeat(95f);
        expect(Forge.strike(level, fp) == Forge.StrikeResult.BROKEN, "striking white should break the bloom at once");
        Forge.stepHeat(level, level.getGameTime() + 500);
        // 鞴の踏み板: 乗ると +12、乗ったままでは増えない、降りて乗り直すと +12
        Forge.clearMemory();
        Forge.setHeat(10f);
        Pig pig = EntityType.PIG.create(level);
        pig.setNoAi(true);
        pig.moveTo(Forge.BELLOWS_A.getX() + 0.5, Forge.BELLOWS_A.getY() + 0.19, Forge.BELLOWS_A.getZ() + 0.5);
        level.addFreshEntity(pig);
        long t = level.getGameTime() + 1000;
        Forge.pollBellows(level, t);
        expect(Math.abs(Forge.heat() - 22f) < 0.05f, "stepping on the plate should add 12: " + Forge.heat());
        expect(level.getBlockState(Forge.BELLOWS_A).getValue(ForgeBlocks.BellowsBlock.PRESSED), "the plate should look pressed");
        Forge.pollBellows(level, t + 10);
        expect(Math.abs(Forge.heat() - 22f) < 0.05f, "standing on the plate must not keep adding");
        pig.moveTo(Forge.BELLOWS_A.getX() + 0.5, Forge.BELLOWS_A.getY() + 3, Forge.BELLOWS_A.getZ() + 0.5);
        Forge.pollBellows(level, t + 12);
        pig.moveTo(Forge.BELLOWS_A.getX() + 0.5, Forge.BELLOWS_A.getY() + 0.19, Forge.BELLOWS_A.getZ() + 0.5);
        Forge.pollBellows(level, t + 20);
        expect(Math.abs(Forge.heat() - 34f) < 0.05f, "jumping back on the plate should add 12 again: " + Forge.heat());
        pig.discard();
        // 灰被りが大炉に触れると −30
        Forge.setHeat(80f);
        AshThrall t1 = EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), Forge.FORGE_FRONT.getX() + 0.5, Forge.FORGE_FRONT.getY(),
                Forge.FORGE_FRONT.getZ() + 0.5, Forge.DOUSER_TAG);
        expect(t1 != null, "no thrall");
        if (t1 != null) {
            t1.setNoAi(true);
            Forge.dousers(level);
            expect(Math.abs(Forge.heat() - 50f) < 0.05f, "a thrall at the forge should cool it by 30: " + Forge.heat());
            expect(!t1.getTags().contains(Forge.DOUSER_TAG), "a thrall that doused the forge keeps its errand");
            t1.discard();
        }
        // 湧き: 一人なら 2 体、四人なら 5 体 (灰被り +1 体/人)
        List<ServerPlayer> one = List.of(fp);
        EmberTrials.clearMobs(level, EmberBackLayout.EAST);
        Forge.clearMemory();
        Forge.keepThralls(level, one, 0);
        expect(countThralls(level) == 2, "one player should meet 2 thralls, got " + countThralls(level));
        EmberTrials.clearMobs(level, EmberBackLayout.EAST);
        Forge.clearMemory();
        List<ServerPlayer> four = List.of(fp, fake(level, "ember_trial_f2"), fake(level, "ember_trial_f3"), fake(level, "ember_trial_f4"));
        Forge.keepThralls(level, four, 0);
        expect(countThralls(level) == 5, "four players should meet 5 thralls, got " + countThralls(level));
        EmberTrials.clearMobs(level, EmberBackLayout.EAST);
        Forge.clearMemory();
        // 鍵: 10 秒で割れる (割れるのは焼き入れ前だけ)。焼き入れの槽で焼き入れた鍵になる
        fp.getInventory().clearContent();
        ItemStack hot = new ItemStack(EmberTrialSetup.FIRE_KEY_HOT.get());
        FireKeyItem.stamp(hot, gt);
        fp.setItemInHand(InteractionHand.MAIN_HAND, hot);
        Forge.keys(level, List.of(fp), gt + 150);
        expect(EmberTrials.count(fp, EmberTrialSetup.FIRE_KEY_HOT.get()) == 1, "the hot key should last 10 s");
        Forge.keys(level, List.of(fp), gt + 201);
        expect(EmberTrials.count(fp, EmberTrialSetup.FIRE_KEY_HOT.get()) == 0, "the hot key should crack after 10 s");
        ItemStack hot2 = new ItemStack(EmberTrialSetup.FIRE_KEY_HOT.get());
        FireKeyItem.stamp(hot2, gt);
        fp.setItemInHand(InteractionHand.MAIN_HAND, hot2);
        expect(Forge.quench(level, fp, InteractionHand.MAIN_HAND, Forge.TROUGH_A), "quenching with a hot key");
        expect(fp.getMainHandItem().is(EmberTrialSetup.FIRE_KEY.get()), "a quenched key should be a fire key");
        Forge.keys(level, List.of(fp), gt + 5000);
        expect(EmberTrials.count(fp, EmberTrialSetup.FIRE_KEY.get()) == 1, "a quenched key must not crack");
        expect(!Forge.quench(level, fp, InteractionHand.MAIN_HAND, Forge.TROUGH_A), "quenching a quenched key");
        // 持ち物がいっぱいなら、鍵は受け取れない (最後の一打は入らない)
        FakePlayer full = fake(level, "ember_trial_full");
        for (int i = 0; i < full.getInventory().items.size(); i++) {
            full.getInventory().items.set(i, new ItemStack(Items.DIRT));
        }
        Forge.clearMemory();
        ForgeRoom.buildInner(level, Forge.PATTERNS[0]);
        // 打つ回数のうち最後の一打だけが拒まれる
        Forge.setHeat(30f);
        expect(Forge.strike(level, full) == Forge.StrikeResult.HIT, "first strike with a full inventory");
        Forge.setHeat(55f);
        expect(Forge.strike(level, full) == Forge.StrikeResult.HIT, "second strike with a full inventory");
        Forge.setHeat(75f);
        expect(Forge.strike(level, full) == Forge.StrikeResult.FULL && Forge.hits() == 2, "the last strike must be refused when the key cannot be received");
        Forge.clearMemory();
        ForgeRoom.buildInner(level, Forge.PATTERNS[0]);
    }

    private static int countThralls(ServerLevel level) {
        return level.getEntitiesOfClass(AshThrall.class, EmberBackLayout.EAST.aabb(), t -> t.getTags().contains(Forge.DOUSER_TAG)).size();
    }

    private static void eastDoor(ServerLevel level) {
        FakePlayer fp = fake(level, "ember_trial_door");
        Forge.clearMemory();
        ForgeRoom.buildInner(level, Forge.PATTERNS[0]);
        fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EmberTrialSetup.FIRE_KEY_HOT.get()));
        expect(!Forge.openDoor(level, fp, InteractionHand.MAIN_HAND), "a hot key must not open the door");
        fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EmberTrialSetup.FIRE_KEY.get()));
        int[] opened = {0};
        Forge.onDoorOpened(l -> opened[0]++);
        expect(Forge.openDoor(level, fp, InteractionHand.MAIN_HAND) && fp.getMainHandItem().isEmpty(), "a quenched key should open the door and be used up");
        expect(Forge.doorState() == 1, "door state after the key");
        EmberBackLayout.Box d = EmberBackLayout.SMITH_DOOR;
        int calls = 0;
        while (Forge.doorState() == 1 && calls < 100) {
            Forge.tickDoor(level);
            calls++;
        }
        expect(calls >= 30 && calls <= 40, "the door should take about 1.5 s to open: " + calls + " ticks");
        expect(Forge.doorState() == 2, "door should be open and waiting 5 s: " + Forge.doorState());
        // 扉は鍵を差したグループにだけ開く。扉のブロックはワールドに残り、ほかのグループには閉じたまま
        UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(fp);
        UUID other = UUID.nameUUIDFromBytes("ember_trial_door_other".getBytes());
        expect(group.equals(Forge.doorGroup()), "the door does not remember the group that used the key");
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int y = d.minY(); y <= d.maxY(); y++) {
                for (int z = d.minZ(); z <= d.maxZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!jp.revati.skyabyss.titan.group.GroupGates.isOpen(level, group, p) || jp.revati.skyabyss.titan.group.GroupGates.isOpen(level, other, p)
                            || !level.getBlockState(p).is(ModBlocks.SEALED_GATE.get())) {
                        F.add("door not open only for the key's group at " + x + "," + y + "," + z + " " + level.getBlockState(p));
                        x = d.maxX();
                        y = d.maxY();
                        z = d.maxZ();
                    }
                }
            }
        }
        for (int i = 0; i < Forge.DOOR_WARNING_TICKS - 2; i++) {
            Forge.tickDoor(level);
        }
        expect(opened[0] == 0, "the fight must wait for the 5 s warning");
        for (int i = 0; i < 5; i++) {
            Forge.tickDoor(level);
        }
        expect(opened[0] == 1 && Forge.doorState() == 3, "the door listener should fire once after 5 s: " + opened[0]);
        fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EmberTrialSetup.FIRE_KEY.get()));
        expect(!Forge.openDoor(level, fp, InteractionHand.MAIN_HAND), "an open door takes no second key");
        // リセットで閉じる
        Forge.reset(level, false);
        expect(Forge.doorState() == 0 && level.getBlockState(new BlockPos(d.minX(), d.minY(), d.minZ())).is(ModBlocks.SEALED_GATE.get()), "reset should seal the door");
        jp.revati.skyabyss.titan.group.BackGroupData.get(level).remove(group);
        jp.revati.skyabyss.titan.group.BackGroupData.get(level).remove(other);
    }

    // ---------------------------------------------------------------- 火守り
    private static void southNumbers(ServerLevel level) {
        expect(Math.abs(Hearth.decayPerSecond(1) - 0.5f) < 1e-4 && Math.abs(Hearth.decayPerSecond(4) - 1.1f) < 1e-4 && Math.abs(Hearth.decayPerSecond(6) - 1.5f) < 1e-4 && Hearth.decayPerSecond(9) == Hearth.decayPerSecond(6),
                "hearth decay should be 0.5/s (+0.2 per extra player, 6 counted)");
        expect(Hearth.START == 20f && Hearth.FEED == 8f && Hearth.DOUSE == 15f, "hearth numbers");
        expect(Hearth.carrierInterval(4) < Hearth.carrierInterval(1), "more players, more carriers");
        // パーティは 6 人まで: 6 人まで数え、それより多くは数えない
        expect(Hearth.carrierInterval(6) < Hearth.carrierInterval(4) && Hearth.carrierCap(6) == 8 && Hearth.carrierCap(9) == 8,
                "the hearth does not scale up to a party of six");
        expect(AshPath.mothTarget(6) == 16 && AshPath.mothTarget(9) == 16 && AshPath.mothTarget(4) == 12, "the ash path moths do not scale up to six");
        FakePlayer fp = fake(level, "ember_trial_hearth");
        Hearth.clearMemory();
        EmberTrials.READY_CALLS.clear();
        ItemStack coal = new ItemStack(EmberTrialSetup.LIVE_COAL.get(), 3);
        expect(Hearth.strength() == 20f, "the hearth starts at 20");
        expect(Hearth.feed(level, fp, coal) && Math.abs(Hearth.strength() - 28f) < 1e-3 && coal.getCount() == 2, "a coal should add 8 and be used up");
        Hearth.douse(level);
        expect(Math.abs(Hearth.strength() - 13f) < 1e-3, "ash should take 15: " + Hearth.strength());
        Hearth.douse(level);
        expect(Hearth.strength() == 20f && !Hearth.done(), "a dead hearth restarts at 20, it is not a failure: " + Hearth.strength());
        Hearth.mothTouch(level);
        expect(Math.abs(Hearth.strength() - 18f) < 1e-3, "a moth should take 2");
        expect(level.getBlockState(Hearth.HEARTH).getValue(HearthBlock.LEVEL) == 0, "hearth block level at 18");
        Hearth.setStrength(60f);
        Hearth.show(level);
        expect(level.getBlockState(Hearth.HEARTH).getValue(HearthBlock.LEVEL) == 2, "hearth block level at 60");
        // 熾を持つと熱 +1/秒 (火床の目盛りの熱の約束)
        EmberHeat.reset(fp);
        fp.getInventory().add(new ItemStack(EmberTrialSetup.LIVE_COAL.get()));
        Hearth.second(level, List.of(fp));
        Hearth.setStrength(60f);
        // 100 で済む: 石が鳴らせる。灰運び・蛾は消える
        Hearth.setStrength(96f);
        EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), 0.5, 79, 20.5, Hearth.CARRIER_TAG);
        expect(Hearth.feed(level, fp, new ItemStack(EmberTrialSetup.LIVE_COAL.get())), "feeding at 96");
        expect(Hearth.done() && Hearth.strength() == 100f, "the hearth should reach 100");
        expect(EmberTrials.READY_CALLS.contains(EmberBackState.Trial.SOUTH), "the south stone should become ringable at 100");
        expect(level.getBlockState(Hearth.HEARTH).getValue(HearthBlock.LEVEL) == 4, "hearth block level at 100");
        expect(level.getEntitiesOfClass(AshThrall.class, EmberBackLayout.SOUTH.aabb(), t -> true).isEmpty(), "carriers should vanish when the hearth is done");
        expect(!Hearth.feed(level, fp, new ItemStack(EmberTrialSetup.LIVE_COAL.get())), "a burning hearth takes no more coal");
        Hearth.clearMemory();
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        // 熾を持って谷を出ると消える
        fp.getInventory().add(new ItemStack(EmberTrialSetup.LIVE_COAL.get()));
        fp.setPos(26, 82, 5);
        EmberTrials.patrol(level, List.of(fp));
        expect(EmberTrials.count(fp, EmberTrialSetup.LIVE_COAL.get()) == 0, "coal left the valley with the player");
        fp.getInventory().add(new ItemStack(EmberTrialSetup.FIRE_KEY.get()));
        fp.setPos(-25, 77, -4);
        EmberTrials.patrol(level, List.of(fp));
        expect(EmberTrials.count(fp, EmberTrialSetup.FIRE_KEY.get()) == 0, "a fire key left the forge with the player");
    }

    private static void southVents(ServerLevel level) {
        // 巡り: 20 秒 (400 tick) で一周、4 組 (3, 3, 2, 2 本) が 5 秒おきに 1.5 秒噴く。噴く 1.5 秒前に白く光る
        int[] sizes = new int[HearthVents.GROUPS];
        for (int i = 0; i < HearthVents.VENTS.size(); i++) {
            sizes[HearthVents.groupOf(i)]++;
        }
        expect(java.util.Arrays.equals(sizes, new int[] {3, 3, 2, 2}), "vent groups should be 3, 3, 2, 2: " + java.util.Arrays.toString(sizes));
        int maxAtOnce = 0;
        int minAtOnce = 99;
        for (int t = 0; t < HearthVents.CYCLE; t++) {
            int bursting = 0;
            for (int i = 0; i < HearthVents.VENTS.size(); i++) {
                if (HearthVents.phaseAt(i, t) == 2) {
                    bursting++;
                }
            }
            if (bursting > 0) {
                maxAtOnce = Math.max(maxAtOnce, bursting);
                minAtOnce = Math.min(minAtOnce, bursting);
            }
        }
        expect(maxAtOnce == 3 && minAtOnce == 2, "2 to 3 vents should burst at once: " + minAtOnce + ".." + maxAtOnce);
        for (int i = 0; i < HearthVents.VENTS.size(); i++) {
            int burst = 0;
            int warn = 0;
            for (int t = 0; t < HearthVents.CYCLE; t++) {
                int ph = HearthVents.phaseAt(i, t);
                burst += ph == 2 ? 1 : 0;
                warn += ph == 1 ? 1 : 0;
                // 噴く直前の 30 tick が光る
                if (ph == 2 && HearthVents.phaseAt(i, t - 1) != 2) {
                    for (int k = 1; k <= HearthVents.WARN_TICKS; k++) {
                        expect(HearthVents.phaseAt(i, t - k) == 1, "vent " + i + " did not glow " + k + " ticks before it burst");
                    }
                }
            }
            expect(burst == HearthVents.BURST_TICKS && warn == HearthVents.WARN_TICKS, "vent " + i + " burst " + burst + " warn " + warn);
        }
        // 当たり: 人は熱 +25・傷・跳ね、一回の噴気で一度だけ。敵は 30 の傷 (灰被りの殻は一度で割れる)
        FakePlayer fp = new TestPlayer(level, new GameProfile(UUID.nameUUIDFromBytes("ember_trial_vent".getBytes()), "ember_trial_vent"));
        BlockPos vent = HearthVents.VENTS.get(0);
        long burstAt = 0;
        fp.setPos(vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        EmberHeat.reset(fp);
        HearthVents.hit(level, fp, vent);
        expect(EmberHeat.get(fp) >= 24.9f, "a vent should add 25 heat: " + EmberHeat.get(fp));
        TestPlayer tp = (TestPlayer) fp;
        expect(tp.lastDamage == 6f && tp.lastSource != null && tp.lastSource.is(EmberIds.HEAT_BURST), "a vent should hurt for 6 (heat_burst): " + tp.lastDamage);
        expect(fp.getDeltaMovement().y > 0.5, "a vent should hop the player up");
        float heat = EmberHeat.get(fp);
        EmberHeat.reset(fp);
        AshThrall t = EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), vent.getX() + 0.5, vent.getY() + 1, vent.getZ() + 0.5);
        if (t != null) {
            t.setNoAi(true);
            HearthVents.burst(level, 0, burstAt + 1000);
            expect(t.shell() <= 0f && t.isFlared(), "a vent should break a thrall's shell at once (shell " + t.shell() + ")");
            t.discard();
        }
        // 割れ目の見た目: 噴く瞬間に段階 2
        HearthVents.tick(level, 0, true);
        expect(level.getBlockState(vent).getValue(HearthVents.VentBlock.PHASE) == 2, "vent block should show the burst");
        HearthVents.tick(level, 200, false);
        expect(level.getBlockState(vent).getValue(HearthVents.VentBlock.PHASE) == 0, "vent block should calm down when the hearth is done");
        HearthVents.clearMemory();
    }

    // 一人でも六人 (パーティの上限) でも 100 まで燃やせる (模擬。腕のある人が 6 秒で灰運び一体を止めて熾を一つくべる。蛾は半分が火に触れる)
    private static void southSimulate(ServerLevel level) {
        for (int n : new int[] {1, 2, 4, 6}) {
            Hearth.clearMemory();
            EmberTrials.READY_CALLS.clear();
            List<ServerPlayer> players = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                players.add(fake(level, "ember_trial_sim" + n + "_" + i));
            }
            Hearth.setStrength(Hearth.START);
            double[] freeAt = new double[n];
            // 灰運び: {湧いた秒, 熾になる秒 (止められなければ -1)}
            List<double[]> carriers = new ArrayList<>();
            double nextCarrier = 8;
            double nextMoths = 0;
            int doneAt = -1;
            int coals = 0;
            int douses = 0;
            for (int sec = 1; sec <= 1200 && doneAt < 0; sec++) {
                Hearth.second(level, players);
                if (Hearth.done()) {
                    doneAt = sec;
                    break;
                }
                if (sec >= nextCarrier && carriers.size() < Hearth.carrierCap(n)) {
                    nextCarrier = sec + Hearth.carrierInterval(n) / 20.0;
                    // 灰運びは火床に着くまで 10 秒。その前に手の空いた人が 6 秒で止める
                    int who = 0;
                    for (int i = 1; i < n; i++) {
                        if (freeAt[i] < freeAt[who]) {
                            who = i;
                        }
                    }
                    double killedAt = -1;
                    if (freeAt[who] <= sec + 4) {
                        killedAt = Math.max(freeAt[who], sec) + 6;
                        freeAt[who] = killedAt;
                    }
                    carriers.add(new double[] {sec, killedAt});
                }
                for (java.util.Iterator<double[]> it = carriers.iterator(); it.hasNext();) {
                    double[] c = it.next();
                    if (c[1] >= 0 && sec >= c[1]) {
                        coals++;
                        Hearth.feed(level, players.get(0), new ItemStack(EmberTrialSetup.LIVE_COAL.get()));
                        it.remove();
                    } else if (c[1] < 0 && sec >= c[0] + 10) {
                        douses++;
                        Hearth.douse(level);
                        it.remove();
                    }
                }
                if (Hearth.strength() > Hearth.MOTH_FROM && sec >= nextMoths) {
                    nextMoths = sec + Hearth.MOTH_INTERVAL / 20.0;
                    for (int k = 0; k < 1 + n; k++) {
                        Hearth.mothTouch(level);
                    }
                }
            }
            expect(doneAt > 0 && doneAt <= 600, n + " player(s): the hearth did not reach 100 within 10 min (done at " + doneAt + " s, coals " + coals + ", douses " + douses + ")");
            expect(EmberTrials.READY_CALLS.contains(EmberBackState.Trial.SOUTH), n + " player(s): the south stone was not made ringable");
            SkyAbyss.LOGGER.info("ember trial: hearth with {} player(s) reached 100 at {} s (coals {}, douses {})", n, doneAt, coals, douses);
        }
        Hearth.clearMemory();
        EmberTrials.READY_CALLS.clear();
    }

    private static void resets(ServerLevel level) {
        // リセット: 殻が溶岩に戻り、壺・鍵・熾が消え、打つ順と火床が戻り、扉が閉まる
        FakePlayer fp = fake(level, "ember_trial_reset");
        AshPath.placeCrust(level, -33, 20);
        AshPath.reset(level, false);
        expect(AshPath.crustCount() == 0 && level.getBlockState(new BlockPos(-33, AshPath.LAVA_Y, 20)).is(Blocks.LAVA), "ash path reset left crust behind");
        Hearth.setStrength(77f);
        Hearth.reset(level, false);
        expect(Hearth.strength() == 20f && !Hearth.done(), "hearth reset");
        Forge.setHeat(70f);
        Forge.reset(level, false);
        expect(Forge.heat() == 0f && Forge.hits() == 0 && Forge.bloomPresent(), "forge reset");
    }

    // ---------------------------------------------------------------- 灰被りが歩いて着く (本物の AI で)
    static boolean walkers(MinecraftServer server) {
        ServerLevel level = server.getLevel(EmberIds.BACK);
        if (level == null) {
            return true;
        }
        if (waited == 0) {
            Forge.clearMemory();
            Hearth.clearMemory();
            ForgeRoom.buildInner(level, Forge.PATTERNS[0]);
            HearthRoom.buildInner(level);
            EmberTrials.clearMobs(level, EmberBackLayout.EAST);
            EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
            douser = EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), Forge.ANTE_SPAWN.getX() + 0.5, Forge.ANTE_SPAWN.getY(),
                    Forge.ANTE_SPAWN.getZ() + 0.5, Forge.DOUSER_TAG);
            carrier = EmberTrials.spawn(level, EmberMobSetup.ASH_THRALL.get(), Hearth.CARRIER_SPAWN.getX() + 0.5, Hearth.CARRIER_SPAWN.getY(),
                    Hearth.CARRIER_SPAWN.getZ() + 0.5, Hearth.CARRIER_TAG);
            douserDone = false;
            carrierDone = false;
            if (douser == null || carrier == null) {
                F.add("could not spawn the walking thralls");
                return true;
            }
        }
        waited++;
        if (waited % 10 == 0) {
            if (!douserDone) {
                // 大炉の熱は時間で下がるので、調べるたびに 80 に戻してから、着いたかを見る
                Forge.setHeat(80f);
                Forge.dousers(level);
                douserDone = !douser.getTags().contains(Forge.DOUSER_TAG);
                if (douserDone) {
                    expect(Math.abs(Forge.heat() - 50f) < 0.01f, "the walking thrall should have cooled the forge by 30: " + Forge.heat());
                }
            }
            if (!carrierDone) {
                Hearth.setStrength(50f);
                Hearth.carriers(level);
                carrierDone = !carrier.getTags().contains(Hearth.CARRIER_TAG);
                if (carrierDone) {
                    expect(Math.abs(Hearth.strength() - 35f) < 0.01f, "the walking carrier should have doused the hearth by 15: " + Hearth.strength());
                }
            }
        }
        if (douserDone && carrierDone) {
            SkyAbyss.LOGGER.info("ember trial: thralls walked to the forge and the hearth in {} ticks", waited);
            cleanupWalkers(level);
            return true;
        }
        if (waited > 1200) {
            F.add("thralls did not reach the forge/hearth in 60 s (forge " + douserDone + ", hearth " + carrierDone + "; thrall at "
                    + douser.blockPosition() + ", carrier at " + carrier.blockPosition() + ")");
            cleanupWalkers(level);
            return true;
        }
        return false;
    }

    private static void cleanupWalkers(ServerLevel level) {
        EmberTrials.clearMobs(level, EmberBackLayout.EAST);
        EmberTrials.clearMobs(level, EmberBackLayout.SOUTH);
        Forge.clearMemory();
        Hearth.clearMemory();
        waited = 0;
    }

    // ---------------------------------------------------------------- まとめ
    static void finish(MinecraftServer server) {
        ServerLevel level = server.getLevel(EmberIds.BACK);
        if (level != null) {
            for (int cx = CX1; cx <= CX2; cx++) {
                for (int cz = CZ1; cz <= CZ2; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        EmberTrials.dryRun = false;
        EmberTrials.READY_CALLS.clear();
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember trial PASSED (registry, rooms stay in their boxes, lake lava contained, reserved cells empty, "
                    + "ash path crosses in 3 layouts, crust clock and moths and standing wait, engulfed, urn, forge solves 4 patterns for one player, "
                    + "cracks and white and key and door, hearth numbers and 1/2/4/6 player simulation, vent cycle and hits, thralls walk to forge and hearth)");
        } else {
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember trial FAILED: {}", x));
        }
    }
}
