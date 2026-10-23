package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.BackShape;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の背の鳥を建てる (12 の 3.2〜3.3節・26 の 3.3)。サーバーの起動のときに一度だけ動く。
// 順番: 鳥の形 (BackShape storm_back/bird.bin。中は詰まっている) → 尾羽の上の到着・羽軸の道・羽の広場 → 羽根の門と腰の回廊 → 尾の間 →
//       風の広間 (昇り風・凪の塔・上の回廊・天窓・竜骨の門・羽軸の柱・行き先の線) → 西の骨・東の骨 (StormWingBuilder) → 首・冠の間・眼の間 (StormHeadBuilder) →
//       竜骨の下の道と前室と竜骨の間 → 掘った所の内壁を風の民の石に (skin) → 休み場・碑文・石
// 拍で動く所 (昇り風、横風、落雷、骨の戸の開け閉め、鏡の向き) は試練の側 (StormBackTrials ほか) が置く
public final class StormBirdBuilder {
    public static final BackShape BIRD = new BackShape("/data/skyabyss/storm_back/bird.bin");

    private StormBirdBuilder() {
    }

    public static void build(StormKit kit) {
        long t0 = System.currentTimeMillis();
        if (BIRD.exists()) {
            int placed = BIRD.place(kit.level());
            SkyAbyss.LOGGER.info("Placed the storm bird: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
        } else {
            // 形がまだ無い (取り込みの前)。部屋だけを空に建てる
            SkyAbyss.LOGGER.warn("storm_back/bird.bin is missing; building the rooms without the bird");
        }
        buildRest(kit);
    }

    // 起動時: 鳥の体を何 tick かに分けて置く仕事 (BackBuildJobs)。形が無ければ空の仕事
    public static jp.revati.skyabyss.titan.BackBuildJobs.Job job(StormKit kit) {
        return BIRD.exists() ? jp.revati.skyabyss.titan.BackBuildJobs.afterAsync(BIRD::preload, () -> BIRD.job(kit.level()))
                : jp.revati.skyabyss.titan.BackBuildJobs.empty();
    }

    // 鳥の体を置いたあと (部屋を掘る・中身を建てる)
    public static void buildRest(StormKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("storm back bird");
        restSteps(s, kit);
        while (!s.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    // buildRest の手順 (順は同じ。tick に分けて進められる)
    public static void restSteps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, StormKit kit) {
        long[] t0 = new long[1];
        int[] n = new int[3];
        // 仕上げが触る範囲 (背の監査の範囲) のチャンクを先に読み込む (読みに行くと、その場で tick が止まる。道・広場・甲板で 0.5〜0.9 秒)
        s.thenJob("load area", () -> jp.revati.skyabyss.titan.BackBuildJobs.loadArea(kit.level(), -330, -190, 330, 270));
        s.then("arrival", () -> {
            t0[0] = System.currentTimeMillis();
            arrival(kit);
        });
        s.then("road", () -> road(kit));
        s.then("plaza", () -> plaza(kit));
        // 体の中は建てない (外見だけ。docs/design/32 の 2.5)。尾羽の上の到着の台・羽軸の道・羽の広場と、肋の尖塔の外の姿だけ
        s.then("rib shell", () -> StormRibShell.build(kit));
        // 鳥の背・翼の上の小さな縦穴 (落ちると出られない) を埋める (詰み監査)
        s.then("wells", () -> {
            if (BIRD.exists()) {
                jp.revati.skyabyss.titan.WellFiller.fill(kit.level(), -330, 330, -190, 200, BIRD::top, 140);
            }
            SkyAbyss.LOGGER.info("Dressed the storm bird's outside in {} ms", System.currentTimeMillis() - t0[0]);
        });
    }

    // 鳥の上 (いちばん上のブロック)。形が無ければ到着の高さ
    public static int top(int x, int z) {
        if (!BIRD.exists()) {
            return ARRIVAL.getY() - 1;
        }
        int t = BIRD.top(x, z);
        return t == Integer.MIN_VALUE ? ARRIVAL.getY() - 1 : t;
    }

    // ---------------------------------------------------------------- 尾羽の上: 到着の台・下り風の輪
    private static void arrival(StormKit kit) {
        int fy = ARRIVAL.getY() - 1;
        // 到着の台 (11x12)。台の下は尾羽まで羽で埋める
        for (int x = -5; x <= 5; x++) {
            for (int z = ARRIVAL.getZ() - 2; z <= DOWNDRAFT.getZ() + 2; z++) {
                int t = top(x, z);
                for (int y = Math.min(t, fy - 1); y < fy; y++) {
                    kit.put(x, y, z, StormKit.FEATHER);
                }
                boolean edge = Math.abs(x) == 5 || z == DOWNDRAFT.getZ() + 2;
                kit.put(x, fy, z, edge ? StormKit.COPPER : StormKit.FEATHER_WHITE);
                for (int y = fy + 1; y <= fy + 5; y++) {
                    kit.air(x, y, z);
                }
                // 縁の手すり (落ちにくいよう。北の端は道の口)
                if (edge && !(z == ARRIVAL.getZ() - 2 && Math.abs(x) <= PATH_X + 1)) {
                    kit.put(x, fy + 1, z, StormDeco.RAIL);
                }
            }
        }
        // 下り風の輪 (床の光る輪。真ん中はガラス。0.5 秒立つと地上へ)
        int dy = DOWNDRAFT.getY() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.put(DOWNDRAFT.getX() + dx, dy, DOWNDRAFT.getZ() + dz, dx == 0 && dz == 0 ? StormKit.GLASS : StormKit.LAMP);
            }
        }
        kit.put(DOWNDRAFT.getX(), dy - 1, DOWNDRAFT.getZ(), StormKit.LAMP);
        // 台の四隅の灯りの柱
        for (int sx : new int[] {-5, 5}) {
            StormDeco.lampPost(kit, sx, fy, ARRIVAL.getZ() - 1);
            StormDeco.lampPost(kit, sx, fy, DOWNDRAFT.getZ() + 1);
        }
    }

    // 羽軸の道の床 (到着の台の北の端 → 羽根の門の口)。鳥の背の高さに沿い、一歩 1 段まで。surf 以上
    static int[] roadFloors() {
        int z0 = ARRIVAL.getZ() - 3;
        int z1 = GATE_Z2 + 1;
        int n = z0 - z1 + 1;
        int[] f = new int[n];
        for (int i = 0; i < n; i++) {
            int z = z0 - i;
            int m = ARRIVAL.getY() - 1;
            for (int x = -PATH_X - 1; x <= PATH_X + 1; x++) {
                m = Math.max(m, top(x, z));
            }
            f[i] = m;
            // 羽の広場の高さに合わせる
            if (z >= TAIL_PLAZA.z1() - 1 && z <= TAIL_PLAZA.z2() + 1) {
                f[i] = TAIL_PLAZA.floor();
            }
        }
        f[n - 1] = GATE_FLOOR;
        for (int i = n - 2; i >= 0; i--) {
            f[i] = Math.max(f[i], f[i + 1] - 1);
        }
        for (int i = 1; i < n; i++) {
            f[i] = Math.max(f[i], f[i - 1] - 1);
        }
        return f;
    }

    // ---------------------------------------------------------------- 羽軸の道: 到着の台の北の端から羽根の門の口まで (幅 7。羽に沿って上り下りし、段は一歩 1 段まで)
    private static void road(StormKit kit) {
        int z0 = ARRIVAL.getZ() - 3;
        int[] f = roadFloors();
        for (int i = 0; i < f.length; i++) {
            int z = z0 - i;
            int floor = f[i];
            for (int x = -PATH_X; x <= PATH_X; x++) {
                int surf = top(x, z);
                for (int y = Math.min(surf, floor - 1); y < floor; y++) {
                    kit.put(x, y, z, StormKit.FEATHER);
                }
                // 羽軸 (真ん中の 1 列は銅の筋、両脇は白と灰の羽の縞)
                BlockState s = x == 0 ? ((z & 3) == 0 ? StormKit.COPPER : StormKit.FEATHER_WHITE) : Math.abs(x) == PATH_X ? StormKit.FEATHER : StormKit.FEATHER_WHITE;
                kit.put(x, floor, z, s);
                for (int y = floor + 1; y <= floor + 5; y++) {
                    kit.air(x, y, z);
                }
            }
            // 縁 (手すり)。羽より低ければ下を埋める。8 ブロックおきに灯りの柱
            for (int sx : new int[] {-PATH_X - 1, PATH_X + 1}) {
                for (int y = Math.min(top(sx, z), floor - 1); y <= floor; y++) {
                    kit.put(sx, y, z, StormKit.FEATHER);
                }
                if (z % 8 == 0) {
                    StormDeco.lampPost(kit, sx, floor, z);
                } else {
                    kit.put(sx, floor + 1, z, StormDeco.RAIL);
                }
            }
            // 12 ブロックおきの矢じるし (羽根の門の向き)
            if (z % 12 == 0 && z < ARRIVAL.getZ() - 8 && z > GATE_Z2 + 6 && !(z >= TAIL_PLAZA.z1() - 4 && z <= TAIL_PLAZA.z2() + 4)) {
                StormDeco.chevron(kit, 0, z - 1, Direction.NORTH, StormKit.COPPER, zz -> f[Math.min(f.length - 1, Math.max(0, z0 - zz))]);
            }
        }
    }

    // ---------------------------------------------------------------- 羽の広場 (道の途中の広い台。休み場 tail。西と東に見晴らしの碑)
    private static void plaza(StormKit kit) {
        Area a = TAIL_PLAZA;
        int fy = a.floor();
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                int t = top(x, z);
                for (int y = Math.min(t, fy - 1); y < fy; y++) {
                    kit.put(x, y, z, StormKit.FEATHER);
                }
                boolean border = x == a.x1() || x == a.x2() || z == a.z1() || z == a.z2();
                BlockState s = border ? StormKit.COPPER : StormKit.floor(Style.FEATHER, x, z);
                // 羽の模様 (真ん中に銅の菱形)
                if (Math.abs(x) + Math.abs(z - PLAZA_CENTER.getZ()) <= 3) {
                    s = StormKit.COPPER;
                }
                kit.put(x, fy, z, s);
                for (int y = fy + 1; y <= fy + a.height(); y++) {
                    kit.air(x, y, z);
                }
            }
        }
        // 縁: 東・西は低い手すり。南 (道の口) と北 (道の口) は幅 7 をあける。北の壁は碑の背
        for (int z = a.z1(); z <= a.z2(); z++) {
            for (int sx : new int[] {a.x1() - 1, a.x2() + 1}) {
                for (int y = Math.min(top(sx, z), fy - 1); y <= fy; y++) {
                    kit.put(sx, y, z, StormKit.FEATHER);
                }
                kit.put(sx, fy + 1, z, StormDeco.RAIL);
            }
        }
        for (int x = a.x1() - 1; x <= a.x2() + 1; x++) {
            for (int zz : new int[] {a.z1() - 1, a.z2() + 1}) {
                boolean mouth = Math.abs(x) <= PATH_X;
                if (mouth) {
                    continue;
                }
                for (int y = Math.min(top(x, zz), fy - 1); y <= fy; y++) {
                    kit.put(x, y, zz, StormKit.FEATHER);
                }
                boolean north = zz == a.z1() - 1;
                int wallTop = north && Math.abs(x) >= 15 ? fy + 4 : fy + 1;
                for (int y = fy + 1; y <= wallTop; y++) {
                    kit.put(x, y, zz, north && Math.abs(x) >= 15 ? StormKit.wall(Style.FEATHER, x, y, zz) : StormDeco.RAIL);
                }
            }
        }
        // 羽軸の柱 (広場の目印)。道のそば (x ±9) に 2 本
        for (int sx : new int[] {-10, 10}) {
            StormDeco.featherPillar(kit, sx, PLAZA_CENTER.getZ(), fy, 8);
        }
        // 広場の四隅と南北の口の灯りの柱
        for (int sx : new int[] {a.x1(), a.x2()}) {
            StormDeco.lampPost(kit, sx, fy, a.z2());
            StormDeco.lampPost(kit, sx, fy, a.z1() + 3);
        }
        for (int sx : new int[] {-PATH_X - 1, PATH_X + 1}) {
            StormDeco.lampPost(kit, sx, fy, a.z2() + 1);
            StormDeco.lampPost(kit, sx, fy, a.z1() - 1);
        }
    }

    // 5x5 の外周 16 マス (北西の角から時計回り)
    static List<int[]> ring5(int cx, int cz) {
        List<int[]> out = new ArrayList<>();
        for (int x = cx - 2; x <= cx + 2; x++) {
            out.add(new int[] {x, cz - 2});
        }
        for (int z = cz - 1; z <= cz + 2; z++) {
            out.add(new int[] {cx + 2, z});
        }
        for (int x = cx + 1; x >= cx - 2; x--) {
            out.add(new int[] {x, cz + 2});
        }
        for (int z = cz + 1; z >= cz - 1; z--) {
            out.add(new int[] {cx - 2, z});
        }
        return out;
    }

    // 昇り風の穴 (3x3 の銅の格子と、その下の灯り)
    static void vent(StormKit kit, BlockPos c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.put(c.getX() + dx, c.getY(), c.getZ() + dz, StormKit.GRATE);
            }
        }
        kit.put(c.getX(), c.getY() - 1, c.getZ(), StormKit.LAMP);
    }

    // 道の形 (リセットの種)。西の骨の割れた床の形 (StormWingBuilder)
    public static void carveWestRoute(StormKit kit, int route) {
        StormWingBuilder.carveWestRoute(kit, route);
    }

    static boolean inGap(int x) {
        return StormWingBuilder.inGap(x);
    }

    // 避雷の柱 (根元 base、高さ 6)。専用のブロック: 根元の台、段の電球 3 つ、軸、先の針 (12 の 120)
    static void rod(StormKit kit, BlockPos base, int charge) {
        kit.put(base.getX(), base.getY(), base.getZ(), StormBackSetup.ROD_BASE.get().defaultBlockState());
        for (int i = 0; i < 3; i++) {
            kit.put(base.getX(), base.getY() + 1 + i, base.getZ(), StormBackSetup.ROD_BULB.get().defaultBlockState()
                    .setValue(StormBlocks.RodBulb.LIT, charge > i));
        }
        kit.put(base.getX(), base.getY() + 4, base.getZ(), StormBackSetup.ROD_SHAFT.get().defaultBlockState());
        kit.put(base.getX(), base.getY() + 5, base.getZ(), StormBackSetup.ROD_TIP.get().defaultBlockState());
    }

    // 体の外の封じた竜骨の間 (trial/raid/InstanceArenas。版 34): 竜骨の材質で詰めた箱 → 前室 (休み場 heart) → 入口 → 竜骨の間 → 柱・風穴・灯り →
    // 心臓の担当の形 (StormHeartArena.buildShape) → 大門 → 焚き火と碑文
    public static void buildHeartInstance(net.minecraft.server.level.ServerLevel level) {
        StormKit kit = new StormKit(level);
        int r0 = HEART_ROOM_RADIUS + 4;
        for (int x = HEART_ROOM.getX() - r0; x <= HEART_ROOM.getX() + r0; x++) {
            for (int z = KEEL_ANTE.z1() - 4; z <= HEART_ROOM.getZ() + r0; z++) {
                for (int y = HEART_ROOM.getY() - 4; y <= HEART_ROOM_CEILING + 3; y++) {
                    kit.put(x, y, z, StormKit.wall(Style.KEEL, x, y, z));
                }
            }
        }
        kit.room(KEEL_ANTE, Style.KEEL, 3);
        int hx = HEART_ROOM.getX();
        // 竜骨の間の入口 (前室の南の口)
        for (int x = hx - 1; x <= hx + 1; x++) {
            for (int y = KEEL_ANTE.floor() + 1; y <= KEEL_ANTE.floor() + 3; y++) {
                kit.air(x, y, KEEL_ANTE.z2() + 1);
            }
        }
        // 竜骨の間 (円形、床 117、空気は 130 まで)。細かい形 (柱・風穴・帯電の床) は心臓の担当が建て直してよい
        int r = HEART_ROOM_RADIUS;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = HEART_ROOM.getX() + dx;
                int z = HEART_ROOM.getZ() + dz;
                if (d <= r + 0.5) {
                    // 舟の底の形 (中央の 1 段低い帯)
                    boolean keelLine = Math.abs(dx) <= 2;
                    kit.put(x, HEART_ROOM.getY(), z, keelLine ? StormKit.SMOOTH : StormKit.floor(Style.KEEL, x, z));
                    if (!keelLine) {
                        kit.put(x, HEART_ROOM.getY() + 1, z, StormKit.floor(Style.KEEL, x, z));
                    }
                    for (int y = HEART_ROOM.getY() + (keelLine ? 1 : 2); y <= HEART_ROOM_CEILING; y++) {
                        kit.air(x, y, z);
                    }
                    kit.put(x, HEART_ROOM_CEILING + 1, z, StormKit.wall(Style.KEEL, x, 0, z));
                } else if (d <= r + 1.6) {
                    for (int y = HEART_ROOM.getY(); y <= HEART_ROOM_CEILING + 1; y++) {
                        kit.solid(x, y, z, StormKit.wall(Style.KEEL, x, y, z));
                    }
                }
            }
        }
        // 前室の床 (118) と竜骨の間の外の床 (118) をつなぐ: 入口の下
        for (int x = hx - 1; x <= hx + 1; x++) {
            kit.put(x, KEEL_ANTE.floor(), KEEL_ANTE.z2() + 1, StormKit.floor(Style.KEEL, x, 0));
        }
        for (BlockPos p : HEART_RODS) {
            rod(kit, p.above(), 0);
        }
        for (BlockPos v : HEART_VENTS) {
            vent(kit, v.above());
        }
        // 肋の灯り
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            kit.solid(HEART_ROOM.getX() + (int) Math.round(Math.cos(a) * (r + 1)), HEART_ROOM.getY() + 4,
                    HEART_ROOM.getZ() + (int) Math.round(Math.sin(a) * (r + 1)), StormKit.LAMP);
        }
        jp.revati.skyabyss.titan.storm.heart.StormHeartArena.buildShape(level);
        StormBossGates.heart(level);
        for (StormBackLayout.Rest rest : RESTS) {
            if (rest.id().equals("heart")) {
                kit.campfire(rest.campfire());
            }
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.key().equals("sign.skyabyss.lore_storm_heart")) {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
    }

    // 竜骨の門の栓 (広間の床の 5x5。グループごとの扉 = 封印の扉のブロック。三つの石がそろったグループにだけ開く)
    public static List<BlockPos> keelPlug() {
        List<BlockPos> out = new ArrayList<>();
        int kz = HUB_CENTER.getZ();
        for (int x = -2; x <= 2; x++) {
            for (int z = kz - 2; z <= kz + 2; z++) {
                out.add(new BlockPos(x, HUB.floor(), z));
            }
        }
        return out;
    }

}
