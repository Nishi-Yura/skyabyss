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
        s.then("throat", () -> holder[0].throat());
        s.then("hub", () -> holder[0].hub());
        s.then("tubes", () -> holder[0].tubes());
        s.then("trial boxes", () -> holder[0].trialBoxes());
        s.then("gate shaft", () -> holder[0].gateShaft());
        s.then("wall stair", () -> holder[0].wallStair());
        s.then("deck rooms", () -> holder[0].deckRooms());
        s.then("skin", () -> {
            holder[0].skin();
            t[1] = System.currentTimeMillis() - t[0];
        });
        s.then("throat lights", () -> holder[0].throatLights());
        for (String room : EmberBackRooms.roomOrder()) {
            if (room.equals("heart")) {
                // 熾の底は体の外 (InstanceArenas)
                continue;
            }
            s.then("room " + room, () -> {
                long r0 = System.currentTimeMillis();
                EmberBackRooms.buildOne(back, room);
                t[2] += System.currentTimeMillis() - r0;
            });
        }
        s.then("fixtures", () -> holder[0].fixtures());
        s.then("deck content", () -> holder[0].deckContent());
        // ボス部屋の大門 (鍛え手の扉)。部屋・休み場・碑文を置いたあと
        s.then("boss gates", () -> EmberBossGates.build(back));
        // 熾の底 (心臓の間) と前室は体の外の封じた部屋 (trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, back, jp.revati.skyabyss.progress.Titans.EMBER);
        // 順路の部屋を分ける封じ (入口の箱の先・炉心の広間。trial/RouteSeals。32 の 2.5)
        s.then("route seals", () -> jp.revati.skyabyss.trial.RouteSeals.place(back, jp.revati.skyabyss.progress.Titans.EMBER));
        jp.revati.skyabyss.titan.audit.BackReach.RepairJob[] repair = new jp.revati.skyabyss.titan.audit.BackReach.RepairJob[1];
        s.thenJob("repair", () -> repair[0] = holder[0].repairJob(back));
        int[] dimples = new int[1];
        s.then("repair report", () -> {
            if (repair[0] != null && repair[0].filled() > 0) {
                SkyAbyss.LOGGER.info("Ember back: filled {} blocks of dead-end pockets on the body's outside", repair[0].filled());
            }
        });
        for (int pass = 0; pass < 2; pass++) {
            for (Box b : DIMPLE_BOXES) {
                s.then("dimples", () -> dimples[0] += holder[0].smoothDimples(b));
            }
        }
        s.then("dimples report", () -> {
            if (dimples[0] > 0) {
                SkyAbyss.LOGGER.info("Ember back: smoothed {} one-block dimples", dimples[0]);
            }
        });
        s.then("mark built", () -> {
            data.markBuilt(buildVersion());
            EmberBackTrials.afterBuild(back);
            jp.revati.skyabyss.titan.light.RouteLamps.place(back, "ember");
            long ms = System.currentTimeMillis() - start;
            lastBuildMs = ms;
            SkyAbyss.LOGGER.info("Built the ember back (build {}): {} body blocks in {} ms, carving {} ms, rooms {} ms, total {} ms",
                    buildVersion(), placed.getAsInt(), tBody, t[1], t[2], ms);
        });
        return s;
    }

    // 体の外 (脚のあいだ・火口の縁など) の、落ちたら出られない凹みを、詰み監査で見つけて埋める (部屋・通路の中は触らない)。docs/design/13 の詰み監査
    jp.revati.skyabyss.titan.audit.BackReach.RepairJob repairJob(ServerLevel back) {
        List<jp.revati.skyabyss.titan.audit.BackReach.Box> within = List.of(
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-190, 63, 80, -40, 130, 240),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(40, 63, 80, 190, 130, 240),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-72, 150, -130, 72, 232, 24),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-110, 63, -245, 110, 128, -90));
        return new jp.revati.skyabyss.titan.audit.BackReach.RepairJob(back, () -> EmberBackAudit.spec(back), within, 200, 14,
                Blocks.BASALT.defaultBlockState());
    }

    // 体の外 (脚のあいだ・火口の縁など) の、落ちたら出られない凹みを、詰み監査で見つけて埋める (部屋・通路の中は触らない)。docs/design/13 の詰み監査
    void repairPockets(ServerLevel back) {
        List<jp.revati.skyabyss.titan.audit.BackReach.Box> within = List.of(
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-190, 63, 80, -40, 130, 240),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(40, 63, 80, 190, 130, 240),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-72, 150, -130, 72, 232, 24),
                new jp.revati.skyabyss.titan.audit.BackReach.Box(-110, 63, -245, 110, 128, -90));
        int filled = jp.revati.skyabyss.titan.audit.BackReach.repair(back, () -> EmberBackAudit.spec(back), within, 200, 14,
                Blocks.BASALT.defaultBlockState());
        if (filled > 0) {
            SkyAbyss.LOGGER.info("Ember back: filled {} blocks of dead-end pockets on the body's outside", filled);
        }
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

    private void trackTube(int x1, int y1, int z1, int x2, int y2, int z2) {
        track(x1, y1, z1, x2, y2, z2);
        tubes.add(new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)).grow(1));
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

    // 喉の半幅 (z ごと)
    public static int throatHalf(int z) {
        if (z < HALL_Z1) {
            return NECK_HALF;
        }
        return z <= HALL_Z2 ? HALL_HALF : RAMP_HALF;
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

    private void throat() {
        for (int z = NECK_Z1; z <= HUB_DOOR_NORTH.getZ(); z++) {
            int f = throatFloor(z);
            boolean rise = z > NECK_Z1 && f > throatFloor(z - 1);
            int hw = throatHalf(z);
            for (int x = -hw; x <= hw; x++) {
                if (rise && Math.abs(x) <= 4) {
                    kit.stair(x, f, z, Direction.SOUTH);
                } else {
                    kit.set(x, f, z, Math.abs(x) <= 1 ? EmberKit.ruinFloor(x, z) : Math.abs(x) <= 4 ? EmberKit.BASALT : EmberKit.FLESH);
                }
                kit.set(x, f - 1, z, EmberKit.FLESH);
                int h = throatHeight(z, x);
                for (int y = f + 1; y <= f + h; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
                // 段の上の前 (北) の床の高さまで空ける (頭を打たない)
                if (rise) {
                    int prev = throatFloor(z - 1);
                    for (int y = f + h + 1; y <= prev + throatHeight(z - 1, x); y++) {
                        kit.set(x, y, z, EmberKit.AIR);
                    }
                }
            }
        }
        track(-NECK_HALF, MOUTH_FLOOR - 1, NECK_Z1, NECK_HALF, MOUTH_FLOOR + 9, HALL_Z1 - 1);
        track(-HALL_HALF, MOUTH_FLOOR - 1, HALL_Z1, HALL_HALF, THROAT_LANDING + 14, HALL_Z2);
        track(-RAMP_HALF, THROAT_LANDING - 1, RAMP_Z1, RAMP_HALF, FLOOR_HUB + 8, HUB_DOOR_NORTH.getZ());
    }

    // 順路の入口の箱 (喉の坂の休み場の平ら。32 の 2.2) の灯り (版 31: 真っ暗で予告の文字しか見えなかった)。壁の皮 (skin) を張ったあとに置く
    void throatLights() {
        for (int z = -128; z <= -120; z += 4) {
            for (int x : new int[] {-RAMP_HALF - 1, RAMP_HALF + 1}) {
                kit.set(x, THROAT_LANDING + 2, z, EmberKit.GLOW);
            }
        }
        for (int[] c : new int[][] {{-RAMP_HALF, -129}, {RAMP_HALF, -129}, {-RAMP_HALF, -121}, {RAMP_HALF, -121}}) {
            if (throatFloor(c[1]) == THROAT_LANDING) {
                kit.set(c[0], THROAT_LANDING + 1, c[1], Blocks.LANTERN.defaultBlockState());
            }
        }
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

    private void hub() {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        int fy = HUB_CENTER.getY();
        for (int x = cx - 28; x <= cx + 28; x++) {
            for (int z = cz - 28; z <= cz + 28; z++) {
                double d = hubDist(x, z);
                if (d > HUB_RADIUS + 0.5) {
                    continue;
                }
                kit.set(x, fy, z, EmberZones.hubFloor(x, z, d));
                kit.set(x, fy - 1, z, EmberKit.RUIN);
                int top = hubCeiling(d);
                for (int y = fy + 1; y <= top; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        track(cx - 28, fy - 1, cz - 28, cx + 28, WELL_BOTTOM, cz + 28);
        // 大きな炉 (5 × 5 の石の囲い、2 段 + 角の柱。中の 3 × 3 が熾の底の門。門 (扉のブロック) は EmberBackTrials.placeFixtures が置き、火と南の口はグループごとの見かけ)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                kit.set(cx + dx, fy + 1, cz + dz, corner ? EmberKit.BASALT : EmberKit.RUIN_CHISELED);
                kit.set(cx + dx, fy + 2, cz + dz, corner ? EmberKit.BASALT : EmberKit.RUIN);
                if (corner) {
                    for (int y = fy + 3; y <= fy + 5; y++) {
                        kit.set(cx + dx, y, cz + dz, y == fy + 5 ? EmberKit.GLOW : EmberKit.RAIL);
                    }
                }
            }
        }
        // 灰溜まり (3 × 2 を四つ、壁ぎわ)
        for (int[] a : EmberZones.HUB_ASH) {
            for (int dx = 0; dx <= 2; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    kit.set(cx + a[0] + dx, fy, cz + a[1] + dz, EmberKit.ASH);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 冷えた溶岩洞 (広間から三つの試練の入口。幅 7、高さ 5〜7 の丸い断面)
    public static int tubeHeight(int lateral) {
        return 7 - Math.max(0, Math.abs(lateral) - 1);
    }

    private void tubes() {
        // 西: 広間の西の扉から西へ (z -57〜-51、床 92)、x -46〜-40 で南へ下り、灰の道の入口 (床 80)
        tubeX(TUBE_Z, HUB_DOOR_WEST.getX(), TUBE_WEST_X - TUBE_HALF, FLOOR_HUB);
        tubeZ(TUBE_WEST_X, TUBE_Z + TUBE_HALF + 1, WEST_DOOR.minZ() - 1, EmberBodyBuilder::legWestFloor);
        // 東: 東の扉から東へ、x 41〜47 で南へ (床 92 → 86)
        tubeX(TUBE_Z, TUBE_EAST_X + TUBE_HALF, HUB_DOOR_EAST.getX(), FLOOR_HUB);
        tubeZ(TUBE_EAST_X, TUBE_Z + TUBE_HALF + 1, EAST_DOOR.minZ() - 1, EmberBodyBuilder::legEastFloor);
        // 南: 南の扉から谷の入口 (床 92 → 83)
        tubeZ(0, HUB_DOOR_SOUTH.getZ(), SOUTH_DOOR.minZ() - 1, EmberBodyBuilder::legSouthFloor);
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

    // 東西にまっすぐな洞 (中心の z、x の範囲)。床は平ら
    private void tubeX(int cz, int xa, int xb, int floor) {
        int x1 = Math.min(xa, xb);
        int x2 = Math.max(xa, xb);
        for (int x = x1; x <= x2; x++) {
            for (int dz = -TUBE_HALF; dz <= TUBE_HALF; dz++) {
                int z = cz + dz;
                kit.set(x, floor, z, EmberZones.tubeFloor(x, z, dz));
                kit.set(x, floor - 1, z, EmberKit.RUIN);
                for (int y = floor + 1; y <= floor + tubeHeight(dz); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        trackTube(x1, floor - 1, cz - TUBE_HALF, x2, floor + 8, cz + TUBE_HALF);
    }

    // 南北の洞 (中心の x、z の範囲)。床は南へ下る (下りる所は北向きの階段)
    private void tubeZ(int cx, int z1, int z2, IntUnaryOperator floorAt) {
        int lo = Integer.MAX_VALUE;
        for (int z = z1; z <= z2; z++) {
            int f = floorAt.applyAsInt(z);
            lo = Math.min(lo, f);
            boolean drop = z > z1 && f < floorAt.applyAsInt(z - 1);
            for (int dx = -TUBE_HALF; dx <= TUBE_HALF; dx++) {
                int x = cx + dx;
                // 下りる段は、前 (北) の床と同じ高さ (f + 1) に置く。背の高い側が前の床の上面にそろい、低い側が下りた床より半段高い
                int top = drop ? f + 1 : f;
                if (drop) {
                    kit.set(x, f, z, EmberKit.RUIN);
                    kit.stair(x, top, z, Direction.NORTH);
                } else {
                    kit.set(x, f, z, EmberZones.tubeFloor(x, z, dx));
                }
                kit.set(x, f - 1, z, EmberKit.RUIN);
                for (int y = top + 1; y <= top + tubeHeight(dx); y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
                // 下りた段の上の前の床の高さまで空ける (段の頭を打たないように)
                if (z > z1) {
                    for (int y = top + tubeHeight(dx) + 1; y <= floorAt.applyAsInt(z - 1) + tubeHeight(dx); y++) {
                        kit.set(x, y, z, EmberKit.AIR);
                    }
                }
            }
        }
        trackTube(cx - TUBE_HALF, lo - 1, z1, cx + TUBE_HALF, floorAt.applyAsInt(z1) + 8, z2);
    }

    // ---------------------------------------------------------------- 試練の箱 (中は空気、仮の床は FLOOR_* の高さまで) と通り道
    private void trialBoxes() {
        for (String room : TRIAL_ROOMS) {
            if (room.equals("heart")) {
                // 熾の底は体の外 (buildHeartInstance)
                continue;
            }
            Box b = ROOMS.get(room);
            carve(b);
            int f = floorOf(room);
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int y = b.minY(); y <= f; y++) {
                        kit.set(x, y, z, y == f ? EmberKit.TEMP_FLOOR : EmberKit.CORE);
                    }
                }
            }
        }
        for (Box door : List.of(WEST_DOOR, EAST_DOOR, SOUTH_DOOR, SMITH_DOOR)) {
            carve(door);
            for (int x = door.minX(); x <= door.maxX(); x++) {
                for (int z = door.minZ(); z <= door.maxZ(); z++) {
                    kit.set(x, door.minY() - 1, z, EmberKit.RUIN);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 熾の底の門の下の螺旋 (3 × 3、y 91 → 51) と前室への通路
    private void gateShaft() {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = FLOOR_HEART + 1; y <= FLOOR_HUB - 1; y++) {
                    boolean pillar = dx == 0 && dz == 0;
                    kit.set(cx + dx, y, cz + dz, pillar ? (y % 5 == 0 ? EmberKit.GLOW : EmberKit.RUIN_DARK) : EmberKit.AIR);
                }
                kit.set(cx + dx, FLOOR_HEART, cz + dz, EmberKit.RUIN);
            }
        }
        for (int i = 0; i < GATE_STEPS; i++) {
            int[] c = RING[(GATE_STEP_START + i) % 8];
            int[] prev = RING[Math.floorMod(GATE_STEP_START + i - 1, 8)];
            Direction up = Direction.fromDelta(prev[0] - c[0], 0, prev[1] - c[1]);
            kit.stair(cx + c[0], FLOOR_HUB - 1 - i, cz + c[1], up == null ? Direction.NORTH : up);
        }
        track(cx - 1, FLOOR_HEART, cz - 1, cx + 1, FLOOR_HUB, cz + 1);
        // 底から南の短い通路 (版 33: 前室は体の外へ移したので、元の前室の手前 z -30 で行き止まり)。壁に灯りを埋める
        int anteZ = -29;
        for (int x = -1; x <= 1; x++) {
            for (int z = cz + 2; z < anteZ; z++) {
                kit.set(x, FLOOR_HEART, z, EmberKit.RUIN);
                for (int y = FLOOR_HEART + 1; y <= FLOOR_HEART + 3; y++) {
                    kit.set(x, y, z, EmberKit.AIR);
                }
            }
        }
        for (int z = cz + 6; z < anteZ - 2; z += 6) {
            kit.set(-2, FLOOR_HEART + 2, z, EmberKit.GLOW);
            kit.set(2, FLOOR_HEART + 2, z, EmberKit.GLOW);
        }
        track(-1, FLOOR_HEART, cz + 2, 1, FLOOR_HEART + 3, anteZ - 1);
    }

    // ---------------------------------------------------------------- 体の外の封じた熾の底 (trial/raid/InstanceArenas。版 33)
    // 体の芯の石で詰めた箱 → 熾の底の箱 (仮の床) → 入口 → 前室 → 休み場 heart → 入口の通り道の床 → 碑文 → 心臓の担当の部屋 (EmberBackRooms) → 大門
    public static void buildHeartInstance(ServerLevel level) {
        long t0 = System.nanoTime();
        EmberBodyBuilder b = new EmberBodyBuilder(level);
        Box h = HEART;
        for (int x = h.minX() - 3; x <= h.maxX() + 3; x++) {
            for (int z = HEART_ANTE.minZ() - 3; z <= h.maxZ() + 3; z++) {
                for (int y = h.minY() - 3; y <= h.maxY() + 8; y++) {
                    b.kit.set(x, y, z, EmberKit.CORE);
                }
            }
        }
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

    // ---------------------------------------------------------------- 火の井戸の踊り場 (壁の螺旋で床 92 → 119。試練の階 EmberDeck への踊り場)
    // 井戸の縦穴・火口・背へ出る洞は無い。踊り場から南は EmberDeck の通路が受け持つ
    private void wallStair() {
        // 壁の螺旋 (広間の南東の壁、3 × 3。床 92 → 119)
        int sx = WELL_STAIR.getX();
        int sz = WELL_STAIR.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = FLOOR_HUB + 1; y <= WELL_BOTTOM + 4; y++) {
                    boolean pillar = dx == 0 && dz == 0 && y < WELL_BOTTOM;
                    kit.set(sx + dx, y, sz + dz, pillar ? (y % 6 == 0 ? EmberKit.GLOW : EmberKit.BASALT) : EmberKit.AIR);
                }
                kit.set(sx + dx, FLOOR_HUB, sz + dz, EmberKit.RUIN);
            }
        }
        kit.set(sx, WELL_BOTTOM, sz, EmberKit.RUIN);
        for (int i = 0; i < WELL_STAIR_STEPS; i++) {
            int[] c = RING[(WELL_STAIR_START + i) % 8];
            int[] next = RING[(WELL_STAIR_START + i + 1) % 8];
            Direction up = Direction.fromDelta(next[0] - c[0], 0, next[1] - c[1]);
            kit.stair(sx + c[0], FLOOR_HUB + 1 + i, sz + c[1], up == null ? Direction.NORTH : up);
        }
        // 広間から壁の螺旋の最初の段への口 (最初の段の西の 1 マス)
        int[] first = RING[WELL_STAIR_START];
        for (int y = FLOOR_HUB + 1; y <= FLOOR_HUB + 4; y++) {
            kit.set(sx + first[0] - 1, y, sz + first[1], EmberKit.AIR);
        }
        kit.set(sx + first[0] - 1, FLOOR_HUB, sz + first[1], EmberKit.RUIN);
        track(sx - 2, FLOOR_HUB - 1, sz - 2, sx + 2, WELL_BOTTOM + 5, sz + 2);
        // 踊り場 (床 119): 最後の段の高い側 (南) の 1 マスが着地。そこから東へ 3 (EmberDeck の踊り場 x 15〜18、z 幅 3)
        int[] next = RING[(WELL_STAIR_START + WELL_STAIR_STEPS) % 8];
        int lx = sx + next[0];
        int lz = sz + next[1];
        int ex = lx + 3;
        for (int x = lx; x <= ex; x++) {
            landingCell(x, lz);
        }
        track(lx - 2, WELL_BOTTOM - 1, lz - 2, ex + 2, WELL_BOTTOM + 5, lz + 3);
    }

    // 踊り場の 1 マス (幅 3・高さ 4。z は lz から南へ 0〜+2)
    private void landingCell(int x, int z) {
        for (int w = 0; w <= 2; w++) {
            kit.set(x, WELL_BOTTOM, z + w, EmberKit.ruinFloor(x, z + w));
            for (int y = WELL_BOTTOM + 1; y <= WELL_BOTTOM + 4; y++) {
                kit.set(x, y, z + w, EmberKit.AIR);
            }
        }
    }

    // ---------------------------------------------------------------- 試練の階 (EmberDeck。docs/design/29)
    // 箱を掘り、床を張り、回廊とセルのあいだの戸口 (高さ 5) を開ける。中身は deckContent (休み場・碑文のあと)
    private void deckRooms() {
        if (!EmberDeck.placed()) {
            return;
        }
        for (Box b : EmberDeck.ROOMS) {
            carve(b);
        }
        for (List<BlockPos> gate : EmberDeck.GATES) {
            for (BlockPos p : gate) {
                kit.set(p, EmberKit.AIR);
            }
            BlockPos lo = gate.get(0);
            BlockPos hi = gate.get(gate.size() - 1);
            track(lo.getX(), EmberDeck.Y, lo.getZ(), hi.getX(), EmberDeck.G1, hi.getZ());
        }
        EmberDeckBuilder.floors(kit);
    }

    private void deckContent() {
        if (EmberDeck.placed()) {
            EmberDeckBuilder.build(kit);
        }
    }

    // ---------------------------------------------------------------- 掘った所の内壁を「肉と骨の壁」(洞は玄武岩) に (体の芯の凝灰岩・灰の原の石が見えたら)
    static boolean coreMaterial(BlockState s) {
        return s.is(Blocks.TUFF) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.BASALT);
    }

    private boolean inTube(int x, int y, int z) {
        for (Box t : tubes) {
            if (t.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private void skin() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        ServerLevel level = kit.level();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Box b0 : carved) {
            Box b = b0.grow(1);
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        m.set(x, y, z);
                        if (!done.add(m.asLong())) {
                            continue;
                        }
                        if (!coreMaterial(level.getBlockState(m))) {
                            continue;
                        }
                        for (Direction d : Direction.values()) {
                            n.setWithOffset(m, d);
                            if (level.getBlockState(n).isAir()) {
                                kit.set(x, y, z, inTube(x, y, z) ? EmberZones.tubeWall(x, y, z) : EmberKit.flesh(x, y, z));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 休み場・熾り石・光る床・碑文・掲示・宝箱・飾り (担当の中身より後)
    private void fixtures() {
        EmberZones.dress(kit);
        List<Box> restAreas = new ArrayList<>();
        for (Rest r : RESTS) {
            if (r.name().equals("heart")) {
                continue;
            }
            rest(r);
            restAreas.add(r.area());
        }
        // 取っておく場所のうち、床と同じ高さから始まる箱 (入口の通り道・奥の岸・扉の前) の床と空気 (休み場の台と亀裂の下は除く)
        for (String room : TRIAL_ROOMS) {
            Box rb = ROOMS.get(room);
            int f = floorOf(room);
            for (Box res : reserved(room)) {
                if (res.minY() != f || restAreas.contains(res) || room.equals("heart")) {
                    continue;
                }
                farFloor(res, rb.minY());
            }
        }
        for (Inscription in : INSCRIPTIONS) {
            if (in.id().equals("lore_ember_heart")) {
                continue;
            }
            kit.wallSign(in.pos(), in.facing(), "sign.skyabyss." + in.id(), DyeColor.ORANGE);
        }
        for (Inscription in : ENTRANCE_SIGNS) {
            kit.wallSign(in.pos(), in.facing(), "sign.skyabyss.ember_" + in.id(), DyeColor.ORANGE);
        }
        for (Inscription in : BOARDS) {
            kit.board(in.pos(), in.facing(), "sign.skyabyss.ember_board_" + in.id(), DyeColor.WHITE);
        }
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

    // 体の中の芯か (自己テストの「穴」の検査で、体の外と中を分ける)
    public static boolean bodyAt(int x, int y, int z) {
        return BODY.at(x, y, z) != null;
    }

}
