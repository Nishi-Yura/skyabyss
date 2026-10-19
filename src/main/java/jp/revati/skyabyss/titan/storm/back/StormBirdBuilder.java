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
//       竜骨の下の道と前室と竜骨の間 → 掘った所の内壁を風の民の石に (skin) → 休み場・碑文・石・抜け道の床
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
        return BIRD.exists() ? BIRD.job(kit.level()) : jp.revati.skyabyss.titan.BackBuildJobs.empty();
    }

    // 鳥の体を置いたあと (部屋を掘る・中身を建てる)
    public static void buildRest(StormKit kit) {
        long t0 = System.currentTimeMillis();
        arrival(kit);
        road(kit);
        plaza(kit);
        gate(kit);
        tailHall(kit);
        hub(kit);
        StormWingBuilder.west(kit);
        StormWingBuilder.east(kit);
        StormHeadBuilder.front(kit);
        keel(kit);
        StormExtraBuilder.build(kit);
        StormSilhouette.build(kit);
        int skinned = kit.skin(StormBirdBuilder::styleAt);
        int sealed = kit.seal(p -> openToSky(p.getX(), p.getY(), p.getZ()), StormBirdBuilder::styleAt);
        int corners = kit.sealCorners(p -> openToSky(p.getX(), p.getY(), p.getZ()), StormBirdBuilder::styleAt);
        StormRibShell.build(kit);
        fixtures(kit);
        // 鳥の背・翼の上の小さな縦穴 (落ちると出られない) を埋める (詰み監査)
        if (BIRD.exists()) {
            jp.revati.skyabyss.titan.WellFiller.fill(kit.level(), -330, 330, -190, 200, BIRD::top, 140);
        }
        SkyAbyss.LOGGER.info("Carved the storm bird: {} cells, {} wall blocks, {} sealed, {} corner gaps closed in {} ms", kit.carved().size(), skinned, sealed, corners,
                System.currentTimeMillis() - t0);
    }

    static Style styleAt(BlockPos p) {
        if (p.getY() < HUB.floor() && pastKeel(p.getCenter())) {
            return Style.KEEL;
        }
        if (p.getX() < -22) {
            return Style.BONE;
        }
        if (p.getX() > 22) {
            return Style.THUNDER;
        }
        if (p.getZ() < -98) {
            return Style.EYE;
        }
        if (p.getZ() < HUB.z1() - 1) {
            return Style.CROWN;
        }
        return Style.FEATHER;
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

    // ---------------------------------------------------------------- 羽の広場 (道の途中の広い台。休み場 tail。西と東に見晴らしの碑と宝箱)
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
        // 見晴らしの宝箱 (西・東。見た目だけ。右クリックで StormCaches が贈り物を渡す)
        for (BlockPos c : new BlockPos[] {CACHE_WEST, CACHE_EAST}) {
            kit.put(c.getX(), c.getY(), c.getZ(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH));
            kit.put(c.getX(), c.getY() - 1, c.getZ(), StormKit.COPPER);
            // 宝箱の両脇の灯り
            kit.put(c.getX() - 1, c.getY(), c.getZ(), StormKit.LAMP);
            kit.put(c.getX() + 1, c.getY(), c.getZ(), StormKit.LAMP);
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

    // ---------------------------------------------------------------- 羽根の門 (腰の上) と腰の回廊 (門 → 尾の間)
    private static void gate(StormKit kit) {
        // 楕円の羽根の枠 (横 19・縦 20)。口は x -3〜3、高さ 7
        double cy = GATE_FLOOR + 8.5;
        for (int z = GATE_Z1; z <= GATE_Z2; z++) {
            for (int x = -9; x <= 9; x++) {
                for (int y = GATE_FLOOR; y <= GATE_FLOOR + 18; y++) {
                    double ex = x / 9.6;
                    double ey = (y - cy) / 9.8;
                    double e = ex * ex + ey * ey;
                    boolean mouth = Math.abs(x) <= CORRIDOR_HALF && y > GATE_FLOOR && y <= GATE_FLOOR + 7;
                    if (mouth) {
                        kit.air(x, y, z);
                    } else if (e <= 1.0 && e >= 0.55) {
                        // 外の羽 (白と灰の帯) と、内の銅の縁
                        kit.put(x, y, z, e < 0.68 ? StormKit.COPPER : ((x + y) % 3 == 0 ? StormKit.FEATHER : StormKit.FEATHER_WHITE));
                    } else if (e < 0.55 && z == GATE_Z2) {
                        // 口のまわりの光る面
                        kit.put(x, y, z, StormKit.GLASS);
                    }
                }
                if (Math.abs(x) <= CORRIDOR_HALF) {
                    kit.put(x, GATE_FLOOR, z, StormKit.COPPER);
                }
            }
        }
        // 腰の回廊: 門の内から北へ 1 段ずつ下りて尾の間の床へ (幅 7・高さ 5)
        for (int z = GATE_Z1 - 1; z > TAIL_HALL.z2(); z--) {
            int floor = Math.min(GATE_FLOOR, TAIL_HALL.floor() + Math.max(1, z - TAIL_HALL.z2()));
            kit.room(-CORRIDOR_HALF, CORRIDOR_HALF, z, z, floor, 5, Style.FEATHER, 0);
            if (floor > TAIL_HALL.floor() + 1 && floor < GATE_FLOOR) {
                for (int x = -CORRIDOR_HALF; x <= CORRIDOR_HALF; x++) {
                    kit.stair(x, floor, z, Direction.SOUTH, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
                }
            }
            if (z % 3 == 0) {
                kit.solid(CORRIDOR_HALF + 1, floor + 3, z, StormKit.LAMP);
                kit.solid(-CORRIDOR_HALF - 1, floor + 3, z, StormKit.LAMP);
            }
        }
    }

    // ---------------------------------------------------------------- 尾の間 (炉・焚き火・腰掛け・碑文) と、広間への回廊
    private static void tailHall(StormKit kit) {
        Area a = TAIL_HALL;
        kit.room(a, Style.FEATHER, 5);
        // 低い丸天井 (真ん中を 2 段高く)
        for (int x = a.x1() + 3; x <= a.x2() - 3; x++) {
            for (int z = a.z1() + 2; z <= a.z2() - 2; z++) {
                for (int y = a.floor() + a.height() + 1; y <= a.floor() + a.height() + 2; y++) {
                    kit.air(x, y, z);
                }
                kit.solid(x, a.floor() + a.height() + 3, z, StormKit.wall(Style.FEATHER, x, 0, z));
            }
        }
        for (int lx = -8; lx <= 8; lx += 4) {
            for (int lz : new int[] {34, 38, 42}) {
                kit.solid(lx, a.floor() + a.height() + 3, lz, StormKit.LAMP);
            }
        }
        // 床の灯り (真ん中の床に光る石。ここを渡る人の足もとを照らす)
        for (int lx : new int[] {-2, 2}) {
            kit.put(lx, a.floor(), 36, StormKit.LAMP);
        }
        // 炉 (共鳴の炉) と飾りの焚き火、腰掛け
        kit.put(10, a.floor() + 1, 43, ModBlocks.RESONANCE_FORGE.get().defaultBlockState());
        kit.put(-10, a.floor() + 1, 43, Blocks.CAMPFIRE.defaultBlockState());
        StormDeco.bench(kit, -12, -9, 30, 30, a.floor() + 1, Direction.SOUTH);
        // 羽軸の柱 (入口の左右)
        for (int sx : new int[] {-9, 9}) {
            StormDeco.featherPillar(kit, sx, 34, a.floor(), 5);
        }
        // 尾の間 → 広間: 1 段ずつ下りる (封印の z は SEAL_Z)。幅 7・高さ 5
        for (int z = a.z1() - 1; z >= HUB.z2() + 1; z--) {
            int floor = Math.min(a.floor(), HUB.floor() + (z - HUB.z2()));
            kit.room(-CORRIDOR_HALF, CORRIDOR_HALF, z, z, floor, 5, Style.FEATHER, 0);
            if (floor > HUB.floor() && floor < a.floor()) {
                for (int x = -CORRIDOR_HALF; x <= CORRIDOR_HALF; x++) {
                    kit.stair(x, floor, z, Direction.SOUTH, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
                }
            }
            if (z % 4 == 0) {
                kit.solid(CORRIDOR_HALF + 1, floor + 3, z, StormKit.LAMP);
                kit.solid(-CORRIDOR_HALF - 1, floor + 3, z, StormKit.LAMP);
            }
        }
    }

    // ---------------------------------------------------------------- 風の広間
    private static void hub(StormKit kit) {
        Area a = HUB;
        kit.room(a, Style.FEATHER, 6);
        int ceil = a.floor() + a.height() + 1;
        // 背の天窓: 天井の真ん中から背の上まで縦穴、上の端はガラス
        for (int x = -SKYLIGHT_R; x <= SKYLIGHT_R; x++) {
            for (int z = HUB_CENTER.getZ() - SKYLIGHT_R; z <= HUB_CENTER.getZ() + SKYLIGHT_R; z++) {
                if (x * x + (z - HUB_CENTER.getZ()) * (z - HUB_CENTER.getZ()) > SKYLIGHT_R * SKYLIGHT_R + 1) {
                    continue;
                }
                int t = Math.max(top(x, z), ceil);
                for (int y = ceil; y < t; y++) {
                    kit.air(x, y, z);
                }
                kit.put(x, t, z, StormKit.GLASS);
            }
        }
        // 羽軸の柱 (広間の目印と灯り)
        for (int[] p : HUB_PILLARS) {
            StormDeco.featherPillar(kit, p[0], p[1], a.floor(), a.height() - 2);
        }
        // 中央の昇り風の穴 (銅の格子)
        for (int x = -1; x <= 1; x++) {
            for (int z = HUB_UPDRAFT.getZ() - 1; z <= HUB_UPDRAFT.getZ() + 1; z++) {
                kit.put(x, a.floor(), z, StormKit.GRATE);
            }
        }
        kit.put(0, a.floor() - 1, HUB_UPDRAFT.getZ(), StormKit.LAMP);
        // 上の回廊 (北の壁の段、床 147) と、昇り風の上の着き場 (z -29〜-28)
        Area g = GALLERY;
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                kit.put(x, g.floor(), z, StormKit.floor(Style.FEATHER, x, z));
            }
            boolean landing = Math.abs(x) <= 3;
            if (!landing) {
                kit.put(x, g.floor() + 1, g.z2(), StormDeco.RAIL);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = g.z2() + 1; z <= g.z2() + 2; z++) {
                kit.put(x, g.floor(), z, StormKit.COPPER);
            }
        }
        // 上の回廊の下 (3 段の厚み。壁に接した宙の板にならないよう、下にも石と灯り)
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                kit.put(x, g.floor() - 1, z, StormKit.floor(Style.FEATHER, x, z));
                kit.put(x, g.floor() - 2, z, StormKit.wall(Style.FEATHER, x, g.floor() - 2, z));
            }
        }
        kit.put(g.x1(), g.floor() - 2, g.z2(), StormKit.LAMP);
        kit.put(g.x2(), g.floor() - 2, g.z2(), StormKit.LAMP);
        kit.put(0, g.floor() - 2, g.z2(), StormKit.LAMP);
        kit.put(-5, g.floor() - 2, g.z2(), StormKit.LAMP);
        kit.put(5, g.floor() - 2, g.z2(), StormKit.LAMP);
        kit.put(8, g.floor() - 2, g.z2(), StormKit.LAMP);
        calmTower(kit);
        // 竜骨の門 (床の羽根の輪。閉じているときは封印のブロック)
        int kz = HUB_CENTER.getZ();
        for (int x = -2; x <= 2; x++) {
            for (int z = kz - 2; z <= kz + 2; z++) {
                kit.put(x, a.floor(), z, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }
        // 床の羽根の模様 (門のまわりの銅の輪)
        for (int x = -3; x <= 3; x++) {
            for (int z = kz - 3; z <= kz + 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z - kz) == 3) {
                    kit.put(x, a.floor(), z, StormKit.COPPER);
                }
            }
        }
        // 行き先の線 (広間の真ん中から、西 = 水色、東 = 金、前 = 白。出口の戸の前まで)
        int wz = WZ;
        StormDeco.strip(kit, a.floor(), StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 1, new int[] {-4, kz}, new int[] {-14, kz}, new int[] {-14, wz}, new int[] {-21, wz});
        StormDeco.strip(kit, a.floor(), StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 1, new int[] {4, kz}, new int[] {14, kz}, new int[] {14, wz}, new int[] {21, wz});
        StormDeco.strip(kit, a.floor(), StormDeco.FRONT_LINE, StormDeco.FRONT_PIP, 4, 1, new int[] {0, kz - 4}, new int[] {0, HUB_UPDRAFT.getZ() + 3});
        // 出口の上の色の飾り (戸口の上に光る玉)
        kit.put(a.x1() - 1, a.floor() + 7, wz, Blocks.SEA_LANTERN.defaultBlockState());
        kit.put(a.x2() + 1, a.floor() + 7, wz, Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        kit.put(0, NECK.floor() + NECK.height() + 1, NECK.z2(), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        // 広間の南の壁の灯りの柱 (尾の間からの入口の左右) と、北の壁ぎわの灯りの柱 (上の回廊と昇り風の下)
        for (int sx : new int[] {-5, 5}) {
            StormDeco.lampPost(kit, sx, a.floor(), a.z2());
        }
        for (int sx : new int[] {-15, -8, 8, 15}) {
            StormDeco.lampPost(kit, sx, a.floor(), a.z1() + 2);
        }
    }

    // 凪の道: 南東の螺旋の塔 (5x5、芯は 3x3) で 134 → 147 の 13 段。上から東の壁ぞいの歩み板で北の回廊へ
    private static void calmTower(StormKit kit) {
        Area a = HUB;
        List<int[]> ring = ring5(CALM_TOWER_X, CALM_TOWER_Z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.fill(CALM_TOWER_X + dx, a.floor() + 1, CALM_TOWER_Z + dz, CALM_TOWER_X + dx, a.floor() + 13, CALM_TOWER_Z + dz,
                        StormKit.wall(Style.FEATHER, CALM_TOWER_X + dx, 0, CALM_TOWER_Z + dz));
            }
        }
        kit.put(CALM_TOWER_X, a.floor() + 14, CALM_TOWER_Z, StormKit.LAMP);
        // 螺旋は南の辺の西の角 (外周の 12 番) から時計回りに 13 段。13 段目 (東の辺の南の角) の東が歩み板
        int start = 12;
        for (int k = 0; k < 13; k++) {
            int[] c = ring.get((start + k) % 16);
            int y = a.floor() + 1 + k;
            kit.fill(c[0], a.floor() + 1, c[1], c[0], y, c[1], StormKit.wall(Style.FEATHER, c[0], y, c[1]));
            kit.put(c[0], y, c[1], StormKit.floor(Style.FEATHER, c[0], c[1]));
            // 頭の上を空ける (次の段へ上る分)
            for (int yy = y + 1; yy <= y + 3; yy++) {
                kit.air(c[0], yy, c[1]);
            }
        }
        int[] last = ring.get((start + 12) % 16);
        // 歩み板 (床 147、x 20〜22) を塔の上から北の回廊の東の端まで。内側 (x 19) に手すり
        int wy = UPPER_GALLERY_Y;
        int wx1 = CALM_TOWER_X + 3;
        int wx2 = a.x2();
        for (int z = last[1]; z >= GALLERY.z1(); z--) {
            for (int x = wx1; x <= wx2; x++) {
                kit.put(x, wy, z, StormKit.floor(Style.FEATHER, x, z));
                for (int y = wy + 1; y <= wy + 3; y++) {
                    kit.air(x, y, z);
                }
            }
            // 歩み板の下 (宙の板にならないよう 2 段の厚み)
            for (int x = wx1; x <= wx2; x++) {
                kit.put(x, wy - 1, z, StormKit.floor(Style.FEATHER, x, z));
                kit.put(x, wy - 2, z, StormKit.wall(Style.FEATHER, x, wy - 2, z));
            }
            if (z <= CALM_TOWER_Z - 3 && z > GALLERY.z2()) {
                kit.put(wx1 - 1, wy + 1, z, StormDeco.RAIL);
            }
        }
        for (int x = GALLERY.x2() + 1; x < wx1; x++) {
            for (int z = GALLERY.z1(); z <= GALLERY.z2(); z++) {
                kit.put(x, wy, z, StormKit.floor(Style.FEATHER, x, z));
                kit.put(x, wy - 1, z, StormKit.floor(Style.FEATHER, x, z));
                kit.put(x, wy - 2, z, StormKit.wall(Style.FEATHER, x, wy - 2, z));
            }
            kit.put(x, wy + 1, GALLERY.z2(), StormDeco.RAIL);
        }
        // 塔の入口の灯りの柱と、歩み板の灯りの柱 (8 おき)
        StormDeco.lampPost(kit, CALM_TOWER_X - 3, a.floor(), CALM_TOWER_Z + 3);
        for (int z = last[1] - 6; z > GALLERY.z2(); z -= 8) {
            StormDeco.lampPost(kit, wx1 - 1, wy, z);
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

    // ---------------------------------------------------------------- 竜骨の門の下: 低い道・螺旋・床 118 の道・前室・竜骨の間
    private static void keel(StormKit kit) {
        int tf = KEEL_TUNNEL_FLOOR;
        int kz = HUB_CENTER.getZ();
        // 低い道 (広間の床の下、x KEEL_TUNNEL_X1〜-2、z kz-1〜kz+1)
        kit.room(KEEL_TUNNEL_X1, -2, kz - 1, kz + 1, tf, 2, Style.KEEL, 5);
        // 西の螺旋 (5x5、131 → 119)
        int cx = KEEL_SPIRAL.getX();
        int cz = KEEL_SPIRAL.getZ();
        kit.room(cx - 2, cx + 2, cz - 2, cz + 2, KEEL_ANTE.floor(), tf + 2 - KEEL_ANTE.floor(), Style.KEEL, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.fill(cx + dx, KEEL_ANTE.floor() + 1, cz + dz, cx + dx, tf + 2, cz + dz, StormKit.wall(Style.KEEL, cx + dx, 0, cz + dz));
            }
        }
        // 低い道の西の端から螺旋の外周へ。東の辺の真ん中 (cx+2, cz) が床 131 の入口。そこから反時計回りに 1 段ずつ下りる
        List<int[]> ring = ring5(cx, cz);
        int start = 0;
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i)[0] == cx + 2 && ring.get(i)[1] == cz) {
                start = i;
            }
        }
        int steps = tf - KEEL_ANTE.floor();
        for (int k = 0; k <= steps; k++) {
            int[] c = ring.get(Math.floorMod(start - k, 16));
            int y = tf - k;
            for (int yy = KEEL_ANTE.floor() + 1; yy < y; yy++) {
                kit.put(c[0], yy, c[1], StormKit.wall(Style.KEEL, c[0], yy, c[1]));
            }
            kit.put(c[0], y, c[1], StormKit.floor(Style.KEEL, c[0], c[1]));
            for (int yy = y + 1; yy <= y + 3 && yy <= tf + 2; yy++) {
                kit.air(c[0], yy, c[1]);
            }
        }
        int[] bottom = ring.get(Math.floorMod(start - steps, 16));
        // 床 118 の道: 螺旋の下から北へ、そして東へ前室まで
        int bx = bottom[0];
        int bz = bottom[1];
        kit.room(bx - 1, bx + 1, KEEL_ANTE.z1() - 1 + 3, bz, KEEL_ANTE.floor(), 3, Style.KEEL, 5);
        kit.room(bx - 1, KEEL_ANTE.x1() - 1, KEEL_ANTE.z1() + 1, KEEL_ANTE.z1() + 3, KEEL_ANTE.floor(), 3, Style.KEEL, 5);
        kit.room(KEEL_ANTE, Style.KEEL, 3);
        // 床 118 の道を掘ると、螺旋のいちばん下の手前の段が削られて 2 段の落差になり、上れなくなる (詰み監査)。下の数段を置き直す
        for (int k = Math.max(0, steps - 3); k <= steps; k++) {
            int[] c = ring.get(Math.floorMod(start - k, 16));
            int y = tf - k;
            kit.put(c[0], y, c[1], StormKit.floor(Style.KEEL, c[0], c[1]));
            for (int yy = y + 1; yy <= y + 3 && yy <= tf + 2; yy++) {
                kit.air(c[0], yy, c[1]);
            }
        }
        // 竜骨の間の入口 (前室の南の口)
        for (int x = -1; x <= 1; x++) {
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
        for (int x = -1; x <= 1; x++) {
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
    }

    // ---------------------------------------------------------------- 休み場・碑文・石・抜け道の床・広間の灯り
    private static void fixtures(StormKit kit) {
        for (StormBackLayout.Rest r : RESTS) {
            kit.campfire(r.campfire());
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.facing() == Direction.UP) {
                kit.standingTablet(i.pos(), i.key());
            } else {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
        for (Inscription i : ENTRANCE_SIGNS) {
            kit.wallSign(i.pos(), i.facing(), i.key());
        }
        for (BlockPos p : new BlockPos[] {RETURN_WEST, RETURN_EAST, RETURN_FRONT}) {
            kit.put(p.getX(), p.getY(), p.getZ(), StormKit.SLATE);
        }
        for (BlockPos p : new BlockPos[] {LAMP_WEST, LAMP_EAST, LAMP_FRONT}) {
            kit.put(p.getX(), p.getY(), p.getZ(), StormKit.SLATE);
        }
        // 拍の舵 (入口の台。凪の舵と嵐の舵。彫った台座と舵輪のモデル)
        for (StormHelm.Helm h : StormHelm.HELMS) {
            kit.put(h.calm().getX(), h.calm().getY(), h.calm().getZ(), StormBackSetup.HELM_CALM.get().defaultBlockState());
            kit.put(h.storm().getX(), h.storm().getY(), h.storm().getZ(), StormBackSetup.HELM_STORM.get().defaultBlockState());
        }
        // 風鳴り石 (台の上)
        for (BlockPos s : new BlockPos[] {WEST_STONE, EAST_STONE, FRONT_STONE}) {
            kit.put(s.getX(), s.getY() - 1, s.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            kit.put(s.getX(), s.getY(), s.getZ(), StormBackSetup.CHIME_STONE.get().defaultBlockState());
        }
        // 三つ目の受け羽の下の、人の形の小さな浮き彫り (四角い頭)。眼の間の北東の角
        kit.put(10, EYE_HALL.floor() + 1, EYE_HALL.z1() - 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        kit.put(10, EYE_HALL.floor() + 2, EYE_HALL.z1() - 1, Blocks.QUARTZ_PILLAR.defaultBlockState());
        kit.put(10, EYE_HALL.floor() + 3, EYE_HALL.z1() - 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
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

    // 竜骨の門の下 (いつも同じ形。栓だけがグループごとに開く): 栓の下の 2 段を空け、低い道の床、東から西へ下る 2 段の階段、
    // 低い道とのつなぎ、穴の底の灯り。何度呼んでも同じ (起動のあと・建てたあと)
    public static void carveKeel(net.minecraft.server.level.ServerLevel level) {
        BlockState stair = Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST);
        int kz = HUB_CENTER.getZ();
        for (int x = -2; x <= 2; x++) {
            for (int z = kz - 2; z <= kz + 2; z++) {
                BlockPos p = new BlockPos(x, HUB.floor(), z);
                setIfDifferent(level, p.below(), Blocks.AIR.defaultBlockState());
                setIfDifferent(level, p.below(2), Blocks.AIR.defaultBlockState());
                setIfDifferent(level, p.below(3), StormKit.floor(Style.KEEL, x, z));
            }
            // 階段 (東から西へ下る): x 1 は床の 1 下、x 0 は 2 下。x -1・-2 は低い道の床。x 2 は栓の高さ (栓が開くと 1 段下りる)
            for (int z = kz - 1; z <= kz + 1; z++) {
                if (x == 1) {
                    setIfDifferent(level, new BlockPos(x, HUB.floor() - 1, z), stair);
                } else if (x == 0) {
                    setIfDifferent(level, new BlockPos(x, HUB.floor() - 2, z), stair);
                }
            }
        }
        // 低い道とつなぐ (x -3 の西の壁を空ける)
        for (int z = kz - 1; z <= kz + 1; z++) {
            for (int y = KEEL_TUNNEL_FLOOR + 1; y <= KEEL_TUNNEL_FLOOR + 2; y++) {
                setIfDifferent(level, new BlockPos(-3, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        setIfDifferent(level, new BlockPos(0, HUB.floor() - 4, kz), StormKit.LAMP);
    }

    private static void setIfDifferent(net.minecraft.server.level.ServerLevel level, BlockPos p, BlockState s) {
        if (!level.getBlockState(p).equals(s)) {
            level.setBlock(p, s, 3);
        }
    }

    static boolean isWall(BlockState s) {
        return s.getBlock() instanceof WallBlock;
    }
}
