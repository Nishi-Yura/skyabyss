package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.titan.TitanBodyBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 余燼の背の体と広間を建てる (13 の 3 章、26 の 11 章)。サーバーの起動時に一度だけ (版が変わったら建て直す)。
// 順番: 体 (body.bin。中は凝灰岩で詰まっている) → 口・見送りの段 → 喉 (首・尾根の広間・坂・見張り台) → 炉心の広間と大きな炉 →
//       冷えた溶岩洞 (広間から三つの試練の入口) → 試練の箱 (中は空気、仮の床) と通り道 → 熾の底の門の下の螺旋・前室・熾の底の箱 → 背の亀裂 →
//       火の井戸の踊り場 (壁の螺旋・床 119) → 掘った所の内壁を「肉と骨の壁」(洞は黒い玄武岩) に (skin) →
//       各担当の部屋の中身 (EmberBackRooms) → 休み場・熾り石・光る床・碑文・掲示・宝箱・灰溜まり・飾り (取っておく場所。担当の中身より後)
// 熾の底の門・石・光る床・ピースフルの封印の開け閉めは試練の側 (EmberBackTrials) が置く
public final class EmberBodyBuilder {
    public static final TitanBodyBlocks BODY = new TitanBodyBlocks(EmberIds.BODY_BIN);
    // 熾の底の門の下の螺旋の段の並び (中心の柱のまわり 8 マス。潮殻の SPIRAL_RING と同じ並び)
    public static final int[][] RING = {{-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}};
    // 門の螺旋: 最初の段は南の真ん中 (並びの 7 番)、広間の床の 1 下から 1 段ずつ y 51 まで
    public static final int GATE_STEP_START = 7;
    public static final int GATE_STEPS = (FLOOR_HUB - 1) - (FLOOR_HEART + 1) + 1;
    // 壁の螺旋 (広間の床 → 井戸の底 119): 最初の段は並びの 3 番 (北の真ん中)。最後の段が角でなく辺の真ん中 (並びの 5 番) になるので、まっすぐ登って着地する
    public static final int WELL_STAIR_START = 3;
    public static final int WELL_STAIR_STEPS = WELL_BOTTOM - FLOOR_HUB;
    // 喉の幅 (半幅): 首 4、尾根の広間 12、坂 3
    public static final int NECK_HALF = 4;
    public static final int HALL_HALF = 12;
    public static final int RAMP_HALF = 3;
    public static final int NECK_Z1 = -237;
    public static final int HALL_Z1 = -208;
    public static final int HALL_Z2 = -132;
    public static final int RAMP_Z1 = -131;

    private final EmberKit kit;
    private final List<Box> carved = new ArrayList<>();
    // 黒い玄武岩の内壁にする洞 (skin が肉と骨の壁でなく玄武岩の壁にする所)
    private final List<Box> tubes = new ArrayList<>();

    private EmberBodyBuilder(ServerLevel level) {
        this.kit = new EmberKit(level);
    }

    // 建てた版 (配置の版と、各担当の中身の版を合わせた数)
    public static int buildVersion() {
        return LAYOUT_VERSION * 10000 + Math.floorMod(EmberBackRooms.contentVersion(), 10000);
    }

    public static void ensureBuilt(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (!BODY.present()) {
            SkyAbyss.LOGGER.warn("ember back: {} is missing, the body is not built", EmberIds.BODY_BIN);
            return;
        }
        int version = buildVersion();
        if (data.needsBuild(version)) {
            if (data.isBuilt()) {
                SkyAbyss.LOGGER.info("Rebuilding the ember back (build {} -> {})", data.layoutVersion(), version);
            }
            build(back, data);
        }
    }

    // 建てる (かかった時間 ms を返す)。起動時の予約 (schedule) が途中なら取り消す (置いた分は「変わるブロックだけ」なので飛ばせる)
    public static long build(ServerLevel back, TitanBackData data) {
        long start = System.currentTimeMillis();
        if (BackBuildJobs.finishNow(back.dimension()) && !data.needsBuild(buildVersion())) {
            return System.currentTimeMillis() - start;
        }
        long t0 = System.currentTimeMillis();
        int placed = BODY.place(back, EmberKit.FLAGS, y -> EmberKit.AIR);
        BackBuildJobs.Steps rest = restSteps(back, data, start, () -> placed, System.currentTimeMillis() - t0);
        while (!rest.step(Long.MAX_VALUE)) {
            // 最後まで
        }
        return lastBuildMs;
    }

    // 起動時: 体を何 tick かに分けて置き、置き終えた tick に残りを建てる (BackBuildJobs。13 の 24.5節)
    public static void schedule(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (!BODY.present()) {
            SkyAbyss.LOGGER.warn("ember back: {} is missing, the body is not built", EmberIds.BODY_BIN);
            return;
        }
        int version = buildVersion();
        if (!data.needsBuild(version)) {
            return;
        }
        if (data.isBuilt()) {
            SkyAbyss.LOGGER.info("Rebuilding the ember back (build {} -> {})", data.layoutVersion(), version);
        }
        BackBuildJobs.Job job = BODY.job(back, EmberKit.FLAGS, y -> EmberKit.AIR);
        long start = System.currentTimeMillis();
        BackBuildJobs.Steps steps = new BackBuildJobs.Steps("ember back body").thenJob("body", () -> job);
        BackBuildJobs.Steps rest = restSteps(back, data, start, job::placed, 0);
        BackBuildJobs.schedule("ember back body", back, new BackBuildJobs.Job() {
            private boolean bodyDone;

            @Override
            public boolean step(long deadlineNanos) {
                if (!bodyDone) {
                    if (!steps.step(deadlineNanos)) {
                        return false;
                    }
                    bodyDone = true;
                    if (System.nanoTime() >= deadlineNanos) {
                        return false;
                    }
                }
                return rest.step(deadlineNanos);
            }

            @Override
            public int placed() {
                return job.placed();
            }
        }, () -> {
        });
    }

    // 体を置いたあとの仕上げ。手順の順は前の一度に建てる形と同じ (一つずつ tick に分けて進められる)
    private static BackBuildJobs.Steps restSteps(ServerLevel back, TitanBackData data, long start, java.util.function.IntSupplier placed, long tBody) {
        BackBuildJobs.Steps s = new BackBuildJobs.Steps("ember back body");
        EmberBodyBuilder[] holder = new EmberBodyBuilder[1];
        long[] t = new long[4];
        // 仕上げが触る範囲 (背の監査の範囲) のチャンクを先に読み込む (読みに行くと、その場で tick が止まる)
        s.thenJob("load area", () -> BackBuildJobs.loadArea(back, -165, -292, 165, 365));
        s.then("underground", () -> {
            holder[0] = new EmberBodyBuilder(back);
            t[0] = System.currentTimeMillis();
            holder[0].restoreUnderground();
        });
        s.then("slit", () -> holder[0].plugSlit());
        s.then("mouth", () -> holder[0].mouth());
        // 体の中は建てない (外見だけ。docs/design/32 の 2.5)。地の下・皮の割れ目の栓・口 (顔。地上へ戻る到着の所) だけ
        s.then("outside", () -> t[1] = System.currentTimeMillis() - t[0]);
        // 熾の底 (心臓の間) と前室は体の外の封じた部屋 (trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, back, jp.revati.skyabyss.progress.Titans.EMBER);
        // 体の外の、四方が 1 高い床の中の 1 マスの凹みを埋める (詰みにならない背の上・脚のあいだ・顎のまわり)
        int[] dimples = new int[1];
        for (int pass = 0; pass < 2; pass++) {
            for (Box b : DIMPLE_BOXES) {
                s.then("dimples", () -> dimples[0] += holder[0].smoothDimples(b));
            }
        }
        s.then("mark built", () -> {
            data.markBuilt(buildVersion());
            EmberBackTrials.afterBuild(back);
            long ms = System.currentTimeMillis() - start;
            lastBuildMs = ms;
            SkyAbyss.LOGGER.info("Built the ember back (build {}): {} body blocks in {} ms, carving {} ms, rooms {} ms, total {} ms",
                    buildVersion(), placed.getAsInt(), tBody, t[1], t[2], ms);
        });
        return s;
    }

    // 1 つだけ四方より 1 低い床のブロック (建築の監査 FLOOR_DIMPLE) を、同じブロックを重ねて平らにする。体の外 (repairPockets の範囲) と喉・尾根の広間の中。
    // 床 = 上が 2 つ空いた固いブロック。四方の隣も同じ高さの床 (上が空いている) より 1 高く並んでいる所
    void smoothDimples() {
        int filled = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (Box b : DIMPLE_BOXES) {
                filled += smoothDimples(b);
            }
        }
        if (filled > 0) {
            SkyAbyss.LOGGER.info("Ember back: smoothed {} one-block dimples", filled);
        }
    }

    static final List<Box> DIMPLE_BOXES = List.of(new Box(-190, 63, 80, -40, 130, 240), new Box(40, 63, 80, 190, 130, 240), new Box(-72, 150, -130, 72, 232, 24),
            new Box(-110, 63, -245, 110, 128, -90));

    // 箱一つ分 (smoothDimples の一回分。手順を tick に分けるとき用)
    int smoothDimples(Box b) {
        int filled = 0;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    BlockState s = kit.get(x, y, z);
                    if (s.isAir() || !s.isCollisionShapeFullBlock(kit.level(), new BlockPos(x, y, z))
                            || !kit.get(x, y + 1, z).isAir() || !kit.get(x, y + 2, z).isAir()) {
                        continue;
                    }
                    boolean pit = true;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        BlockState n = kit.get(x + d[0], y + 1, z + d[1]);
                        if (n.isAir() || !n.isCollisionShapeFullBlock(kit.level(), new BlockPos(x + d[0], y + 1, z + d[1]))
                                || !kit.get(x + d[0], y + 2, z + d[1]).isAir()) {
                            pit = false;
                            break;
                        }
                    }
                    if (pit) {
                        kit.set(x, y + 1, z, s);
                        filled++;
                    }
                }
            }
        }
        return filled;
    }

    // 最後に建てたときの時間 (自己テストの報告用。建てていなければ -1)
    public static long lastBuildMs = -1;

    // ---------------------------------------------------------------- 灰の原の層 (dimension/ember_back.json と同じ)
    static BlockState plain(int y) {
        if (y <= 0) {
            return Blocks.BEDROCK.defaultBlockState();
        }
        if (y <= 20) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (y <= 36) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (y <= 62) {
            return Blocks.TUFF.defaultBlockState();
        }
        return y == PLAIN_TOP ? Blocks.SMOOTH_BASALT.defaultBlockState() : EmberKit.AIR;
    }

    // 建て直しのとき、灰の原より下に掘った所 (熾の底・前室・螺旋の下) を灰の原に戻す。口の先の見送りの石の床も
    private void restoreUnderground() {
        Box old = OLD_UNDERGROUND;
        for (int x = old.minX(); x <= old.maxX(); x++) {
            for (int z = old.minZ(); z <= old.maxZ(); z++) {
                for (int y = old.minY(); y <= old.maxY(); y++) {
                    kit.set(x, y, z, plain(y));
                }
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = RETURN_STONE.minZ() - 2; z <= -266; z++) {
                kit.set(x, PLAIN_TOP, z, plain(PLAIN_TOP));
            }
        }
    }

    // 頭と胴のつなぎ目 (z -197〜-187) は、モデルの顎の下に左右へ抜ける細い隙間 (y 64〜76) がある。喉の広間がそこに触れるので、
    // 広間の外 (|x| 13 以上) で、上に体がある (外の空ではない) 空気の所を体の芯の石で埋める
    private void plugSlit() {
        for (int x = -90; x <= 90; x++) {
            if (Math.abs(x) <= HALL_HALF) {
                continue;
            }
            for (int z = -197; z <= -187; z++) {
                int top = BODY.top(x, z);
                for (int y = PLAIN_TOP + 1; y <= 79; y++) {
                    if (top > y && kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, EmberKit.CORE);
                    }
                }
            }
        }
    }

    private void carve(Box b) {
        kit.fill(b, EmberKit.AIR);
        carved.add(b);
    }

    private void track(int x1, int y1, int z1, int x2, int y2, int z2) {
        carved.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
    }

    // ---------------------------------------------------------------- 口 (舌の上の到着点と、顎の先への段)
    // モデルの口は開いている (舌の上面 y 70、空洞 y 71〜75、上あごの下面 y 76。z -272〜-238。左右にも開いている)。
    // 到着の床を舌より 1 低い 69 にならし (幅 19)、天井はモデルのまま。顎の先 (z -267) から灰の原へ 5 段下りる。口の縁に灯りの柱
    private void mouth() {
        for (int z = -265; z <= -238; z++) {
            for (int x = -9; x <= 9; x++) {
                kit.set(x, MOUTH_FLOOR, z, Math.abs(x) <= 4 ? Blocks.RED_TERRACOTTA.defaultBlockState() : EmberKit.ruinFloor(x, z));
                for (int y = MOUTH_FLOOR + 1; y <= MOUTH_FLOOR + 6; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(-10, MOUTH_FLOOR, -267, 10, MOUTH_FLOOR + 8, -238);
        // 見送りの段: 床 64〜69 と上がる (z -271〜-266)。その先 (z -272 から) は灰の原。床の高さ = 段のブロックの y、顎の先を 7 幅に切る
        for (int z = -271; z <= -266; z++) {
            int floor = PLAIN_TOP + 1 + (z + 271);
            for (int x = -3; x <= 3; x++) {
                for (int y = PLAIN_TOP + 1; y < floor; y++) {
                    kit.set(x, y, z, EmberKit.RUIN);
                }
                kit.stair(x, floor, z, Direction.SOUTH);
                for (int y = floor + 1; y <= MOUTH_FLOOR + 6; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(-4, PLAIN_TOP, -272, 4, MOUTH_FLOOR + 7, -266);
        // 見送りの石 (灰の原の上の床に、光る石の印)
        for (int x = RETURN_STONE.minX(); x <= RETURN_STONE.maxX(); x++) {
            for (int z = RETURN_STONE.minZ(); z <= RETURN_STONE.maxZ(); z++) {
                kit.set(x, PLAIN_TOP, z, x == 0 ? EmberKit.EMBERLIGHT : EmberKit.RUIN_CHISELED);
            }
        }
    }

    // ---------------------------------------------------------------- 喉 (首 幅 9 → 尾根の広間 幅 25 → 坂 幅 7。床 69 → 78 (休み場の平ら) → 92)
    public static int throatFloor(int z) {
        if (z <= HALL_Z1) {
            return MOUTH_FLOOR;
        }
        if (z <= -121) {
            return Math.min(THROAT_LANDING, MOUTH_FLOOR + Math.floorDiv(z + 207, 8));
        }
        if (z <= -84) {
            return THROAT_LANDING + (int) Math.round((z + 121) * 14 / 37.0);
        }
        return FLOOR_HUB;
    }

    // 喉の天井までの空気の数 (床の上。端ほど低い丸い断面)
    public static int throatHeight(int z, int x) {
        int ax = Math.abs(x);
        if (z < HALL_Z1) {
            return 7 - Math.max(0, ax - 2);
        }
        if (z <= HALL_Z2) {
            return 12 - Math.max(0, ax - 9);
        }
        return 6 - Math.max(0, ax - 2);
    }

    // ---------------------------------------------------------------- 炉心の広間 (円 半径 27、床 92、天井は丸く 116〜108)
    public static double hubDist(int x, int z) {
        double dx = x - HUB_CENTER.getX();
        double dz = z - HUB_CENTER.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int hubCeiling(double d) {
        return 116 - (int) Math.round(8 * (d / HUB_RADIUS) * (d / HUB_RADIUS));
    }

    // 洞の南の脚の床 (z ごと。灰の道・鍛えの間・火守りの谷の入口の高さへ下る)
    public static int legWestFloor(int z) {
        return FLOOR_HUB - clamp((int) Math.round((z + 46) * 12 / 30.0), 0, 12);
    }

    public static int legEastFloor(int z) {
        return FLOOR_HUB - clamp((int) Math.round((z + 46) * 6 / 30.0), 0, 6);
    }

    public static int legSouthFloor(int z) {
        return FLOOR_HUB - clamp((int) Math.round((z + 26) * 9 / 17.0), 0, 9);
    }

    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------------------------------------------------------- 体の外の封じた熾の底 (trial/raid/InstanceArenas。版 33)
    // 体の芯の石で詰めた箱 → 熾の底の箱 (仮の床) → 入口 → 前室 → 休み場 heart → 入口の通り道の床 → 碑文 → 心臓の担当の部屋 (EmberBackRooms) → 大門
    public static void buildHeartInstance(ServerLevel level) {
        heartFill(level).runAll();
        buildHeartInstance(level, false);
    }

    // 熾の底のまわりを体の芯の石で詰める箱 (tick に分けて詰められる。trial/raid/InstanceArenas.addHeartSteps)
    public static jp.revati.skyabyss.trial.raid.InstanceArenas.Fill heartFill(ServerLevel level) {
        EmberBodyBuilder b = new EmberBodyBuilder(level);
        Box h = HEART;
        return new jp.revati.skyabyss.trial.raid.InstanceArenas.Fill(h.minX() - 3, h.maxX() + 3, HEART_ANTE.minZ() - 3, h.maxZ() + 3, h.minY() - 3, h.maxY() + 8,
                (x, y, z) -> b.kit.set(x, y, z, EmberKit.CORE));
    }

    // 詰めた箱の中に、元の形を掘って建てる (withFill = 先に箱を詰める)
    public static void buildHeartInstance(ServerLevel level, boolean withFill) {
        if (withFill) {
            heartFill(level).runAll();
        }
        long t0 = System.nanoTime();
        EmberBodyBuilder b = new EmberBodyBuilder(level);
        Box h = HEART;
        b.carve(h);
        int f = floorOf("heart");
        for (int x = h.minX(); x <= h.maxX(); x++) {
            for (int z = h.minZ(); z <= h.maxZ(); z++) {
                for (int y = h.minY(); y <= f; y++) {
                    b.kit.set(x, y, z, y == f ? EmberKit.TEMP_FLOOR : EmberKit.CORE);
                }
            }
        }
        b.carve(HEART_DOOR);
        for (int x = HEART_DOOR.minX(); x <= HEART_DOOR.maxX(); x++) {
            for (int z = HEART_DOOR.minZ(); z <= HEART_DOOR.maxZ(); z++) {
                b.kit.set(x, HEART_DOOR.minY() - 1, z, EmberKit.RUIN);
            }
        }
        b.ante();
        b.rest(EmberBackLayout.rest("heart"));
        b.farFloor(new Box(-1 + HEART_DX, FLOOR_HEART, -21 + HEART_DZ, 1 + HEART_DX, FLOOR_HEART + 4, -20 + HEART_DZ), h.minY());
        for (Inscription in : INSCRIPTIONS) {
            if (in.id().equals("lore_ember_heart")) {
                b.kit.wallSign(in.pos(), in.facing(), "sign.skyabyss." + in.id(), DyeColor.ORANGE);
            }
        }
        long t1 = System.nanoTime();
        // 心臓の担当の部屋の中身 (EmberBackRooms の "heart" = EmberHeartArena.buildRoom から、体じゅうの歩く道の灯りの置き直しを除いたもの)
        jp.revati.skyabyss.titan.ember.heart.EmberHeartArena.build(level);
        long t2 = System.nanoTime();
        EmberBossGates.heart(level);
        SkyAbyss.LOGGER.info("Ember heart arena: shell {} ms, room {} ms, gate {} ms", (t1 - t0) / 1_000_000L, (t2 - t1) / 1_000_000L, (System.nanoTime() - t2) / 1_000_000L);
    }

    // ---------------------------------------------------------------- 熾の底の前室 (床 50)
    private void ante() {
        carve(new Box(HEART_ANTE.minX(), HEART_ANTE.minY() + 1, HEART_ANTE.minZ(), HEART_ANTE.maxX(), HEART_ANTE.maxY(), HEART_ANTE.maxZ()));
        for (int x = HEART_ANTE.minX(); x <= HEART_ANTE.maxX(); x++) {
            for (int z = HEART_ANTE.minZ(); z <= HEART_ANTE.maxZ(); z++) {
                kit.set(x, FLOOR_HEART, z, EmberKit.ruinFloor(x, z));
            }
        }
        carved.add(HEART_ANTE);
    }

    // 休み場の 5 × 5 の台 (下は箱の底まで詰める)、南の端の真ん中に青い焚き火、北の端に灰溜まり
    private void rest(Rest r) {
        Box a = r.area();
        BlockPos f = r.floor();
        int bottom = f.getY();
        if (r.name().equals("w1")) {
            bottom = WEST.minY();
        }
        for (int x = a.minX(); x <= a.maxX(); x++) {
            for (int z = a.minZ(); z <= a.maxZ(); z++) {
                for (int y = bottom; y < f.getY(); y++) {
                    kit.set(x, y, z, EmberKit.BASALT);
                }
                boolean edge = x == a.minX() || x == a.maxX() || z == a.minZ() || z == a.maxZ();
                kit.set(x, f.getY(), z, edge ? EmberKit.RUIN_DARK : EmberKit.RUIN);
                for (int y = f.getY() + 1; y <= a.maxY(); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            kit.set(f.getX() + dx, f.getY(), f.getZ() - 2, EmberKit.ASH);
        }
        kit.campfire(r.campfire());
    }

    private void farFloor(Box b, int bottom) {
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = bottom; y < b.minY(); y++) {
                    kit.set(x, y, z, EmberKit.BASALT);
                }
                kit.set(x, b.minY(), z, EmberKit.ruinFloor(x, z));
                for (int y = b.minY() + 1; y <= b.maxY(); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
    }

}
