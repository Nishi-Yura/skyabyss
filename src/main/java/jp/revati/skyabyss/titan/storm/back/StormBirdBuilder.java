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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// 雷翼の背の鳥を建てる (12 の 3.2〜3.3節)。サーバーの起動のときに一度だけ動く (数秒かかる)。
// 順番: 鳥の形 (BackShape storm_back/bird.bin。中は詰まっている) → 尾羽の上の到着と歩く道 → 羽根の門と腰の回廊 → 尾の間 →
//       風の広間 (昇り風・凪の塔・上の回廊・天窓・竜骨の門) → 西の骨 → 東の骨と雷獣の巣 → 首と眼の間 → 竜骨の下の道と前室と竜骨の間 →
//       背の上へ上る段 → 掘った所の内壁を風の民の石に (skin) → 休み場・碑文・石・抜け道の床
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
        arrival(kit);
        gate(kit);
        tailHall(kit);
        hub(kit);
        west(kit);
        east(kit);
        front(kit);
        keel(kit);
        topStair(kit);
        int skinned = kit.skin(StormBirdBuilder::styleAt);
        int sealed = kit.seal(p -> openToSky(p.getX(), p.getY(), p.getZ()), StormBirdBuilder::styleAt);
        fixtures(kit);
        SkyAbyss.LOGGER.info("Carved the storm bird: {} cells, {} wall blocks, {} sealed in {} ms", kit.carved().size(), skinned, sealed,
                System.currentTimeMillis() - t0);
    }

    static Style styleAt(BlockPos p) {
        if (p.getY() < 137 && pastKeel(p.getCenter())) {
            return Style.KEEL;
        }
        if (p.getX() < -22) {
            return Style.BONE;
        }
        if (p.getX() > 22) {
            return Style.THUNDER;
        }
        if (p.getZ() < -43) {
            return Style.EYE;
        }
        return Style.HALL;
    }

    // 鳥の上 (いちばん上のブロック)。形が無ければ到着の高さ
    public static int top(int x, int z) {
        if (!BIRD.exists()) {
            return ARRIVAL.getY() - 1;
        }
        int t = BIRD.top(x, z);
        return t == Integer.MIN_VALUE ? ARRIVAL.getY() - 1 : t;
    }

    // ---------------------------------------------------------------- 尾羽の上: 到着の台・下り風の輪・羽根の門までの道
    private static void arrival(StormKit kit) {
        int fy = ARRIVAL.getY() - 1;
        // 到着の台 (7x10)。台の下は尾羽まで羽で埋める
        for (int x = -3; x <= 3; x++) {
            for (int z = ARRIVAL.getZ() - 2; z <= DOWNDRAFT.getZ() + 2; z++) {
                int t = top(x, z);
                for (int y = Math.min(t, fy - 1); y < fy; y++) {
                    kit.put(x, y, z, StormKit.FEATHER);
                }
                kit.put(x, fy, z, Math.abs(x) == 3 ? StormKit.COPPER : StormKit.FEATHER_WHITE);
                for (int y = fy + 1; y <= fy + 4; y++) {
                    kit.air(x, y, z);
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
        // 歩く道: 到着の台の北の端から羽根の門の口まで、まっすぐの坂 (一歩で 1 段まで)。羽より低ければ溝、高ければ盛る
        int z0 = ARRIVAL.getZ() - 3;
        int z1 = GATE_Z2 + 1;
        int y0 = fy;
        int y1 = GATE_FLOOR;
        for (int z = z0; z >= z1; z--) {
            double t = (z0 - z) / (double) Math.max(1, z0 - z1);
            int floor = (int) Math.round(y0 + (y1 - y0) * t);
            for (int x = -PATH_X; x <= PATH_X; x++) {
                int surf = top(x, z);
                for (int y = Math.min(surf, floor - 1); y < floor; y++) {
                    kit.put(x, y, z, StormKit.FEATHER);
                }
                kit.put(x, floor, z, (z % 4 == 0 && x == 0) ? StormKit.COPPER : StormKit.FEATHER_WHITE);
                for (int y = floor + 1; y <= floor + 3; y++) {
                    kit.air(x, y, z);
                }
            }
            // 道の縁 (落ちにくいよう、羽の上に出ている所に低い縁)
            for (int x : new int[] {-PATH_X - 1, PATH_X + 1}) {
                if (top(x, z) < floor) {
                    for (int y = top(x, z); y <= floor; y++) {
                        kit.solid(x, y, z, StormKit.FEATHER);
                    }
                    kit.solid(x, floor + 1, z, Blocks.DIORITE_WALL.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------- 羽根の門 (腰の上) と腰の回廊 (門 → 尾の間)
    private static void gate(StormKit kit) {
        // 楕円の羽根の枠 (横 13・縦 10)。口は x -2〜2、高さ 5
        int cy = GATE_FLOOR + 4;
        for (int z = GATE_Z1; z <= GATE_Z2; z++) {
            for (int x = -6; x <= 6; x++) {
                for (int y = GATE_FLOOR; y <= GATE_FLOOR + 9; y++) {
                    double ex = x / 6.5;
                    double ey = (y - cy) / 5.5;
                    double e = ex * ex + ey * ey;
                    boolean mouth = Math.abs(x) <= 2 && y > GATE_FLOOR && y <= GATE_FLOOR + 5;
                    if (mouth) {
                        kit.air(x, y, z);
                    } else if (e <= 1.0 && e >= 0.45) {
                        // 外の羽 (白と灰の帯) と、内の銅の縁
                        kit.put(x, y, z, e < 0.6 ? StormKit.COPPER : ((x + y) % 3 == 0 ? StormKit.FEATHER : StormKit.FEATHER_WHITE));
                    } else if (e < 0.45 && !mouth && z == GATE_Z2) {
                        // 口のまわりの光る面
                        kit.put(x, y, z, StormKit.GLASS);
                    }
                }
                if (Math.abs(x) <= 2) {
                    kit.put(x, GATE_FLOOR, z, StormKit.COPPER);
                }
            }
        }
        // 口の上の灯り (光る面の奥)
        kit.put(0, GATE_FLOOR + 7, GATE_Z1, StormKit.LAMP);
        // 腰の回廊: 門の内 (z 39) から北へ 1 段ずつ下りて尾の間の床 (146) へ
        for (int z = GATE_Z1 - 1; z >= TAIL_HALL.z2(); z--) {
            int floor = TAIL_HALL.floor() + Math.max(0, z - (TAIL_HALL.z2() - 1));
            floor = Math.min(floor, GATE_FLOOR);
            kit.room(-1, 1, z, z, floor, 4, Style.HALL, 0);
            if (floor > TAIL_HALL.floor()) {
                for (int x = -1; x <= 1; x++) {
                    kit.stair(x, floor, z, Direction.SOUTH, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
                }
            }
        }
        kit.solid(2, GATE_FLOOR - 2, 36, StormKit.LAMP);
        kit.solid(-2, GATE_FLOOR - 4, 34, StormKit.LAMP);
    }

    // ---------------------------------------------------------------- 尾の間 (炉・焚き火・腰掛け・碑文) と、広間への回廊
    private static void tailHall(StormKit kit) {
        StormBackLayout.Area a = TAIL_HALL;
        kit.room(a, Style.HALL, 5);
        // 低い丸天井 (真ん中を 2 段高く)
        for (int x = a.x1() + 3; x <= a.x2() - 3; x++) {
            for (int z = a.z1() + 2; z <= a.z2() - 2; z++) {
                for (int y = a.floor() + a.height() + 1; y <= a.floor() + a.height() + 2; y++) {
                    kit.air(x, y, z);
                }
                kit.solid(x, a.floor() + a.height() + 3, z, StormKit.wall(Style.HALL, x, 0, z));
            }
        }
        kit.solid(0, a.floor() + a.height() + 3, 26, StormKit.LAMP);
        // 炉 (共鳴の炉) と飾りの焚き火、腰掛け
        kit.put(8, a.floor() + 1, 30, ModBlocks.RESONANCE_FORGE.get().defaultBlockState());
        kit.put(-6, a.floor() + 1, 30, Blocks.CAMPFIRE.defaultBlockState());
        for (int x = -9; x <= -7; x++) {
            kit.stair(x, a.floor() + 1, 31, Direction.SOUTH, Blocks.BIRCH_STAIRS.defaultBlockState());
        }
        // 尾の間 → 広間: 1 段ずつ下りる (封印の z は 19)
        for (int z = a.z1() - 1; z >= HUB.z2() + 1; z--) {
            int floor = HUB.floor() + (z - HUB.z2());
            kit.room(-1, 1, z, z, floor, 4, Style.HALL, 0);
            for (int x = -1; x <= 1; x++) {
                kit.stair(x, floor, z, Direction.SOUTH, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
            }
        }
    }

    // ---------------------------------------------------------------- 風の広間
    private static void hub(StormKit kit) {
        StormBackLayout.Area a = HUB;
        kit.room(a, Style.HALL, 6);
        int ceil = a.floor() + a.height() + 1;
        // 背の天窓: 天井の真ん中から背の上まで縦穴、上の端はガラス
        for (int x = -SKYLIGHT_R; x <= SKYLIGHT_R; x++) {
            for (int z = -4 - SKYLIGHT_R; z <= -4 + SKYLIGHT_R; z++) {
                if (x * x + (z + 4) * (z + 4) > SKYLIGHT_R * SKYLIGHT_R + 1) {
                    continue;
                }
                int t = Math.max(top(x, z), ceil);
                for (int y = ceil; y < t; y++) {
                    kit.air(x, y, z);
                }
                kit.put(x, t, z, StormKit.GLASS);
            }
        }
        // 中央の昇り風の穴 (銅の格子)
        for (int x = -1; x <= 1; x++) {
            for (int z = HUB_UPDRAFT.getZ() - 1; z <= HUB_UPDRAFT.getZ() + 1; z++) {
                kit.put(x, a.floor(), z, StormKit.GRATE);
            }
        }
        kit.put(0, a.floor() - 1, HUB_UPDRAFT.getZ(), StormKit.LAMP);
        // 上の回廊 (北の壁の段、床 150) と、昇り風の上の着き場 (z -15〜-14)
        StormBackLayout.Area g = GALLERY;
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                kit.put(x, g.floor(), z, StormKit.floor(Style.HALL, x, z));
            }
            boolean landing = Math.abs(x) <= 1;
            if (!landing) {
                kit.put(x, g.floor() + 1, g.z2(), Blocks.DIORITE_WALL.defaultBlockState());
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = g.z2() + 1; z <= g.z2() + 2; z++) {
                kit.put(x, g.floor(), z, StormKit.COPPER);
            }
        }
        // 凪の道: 南東の螺旋の塔 (5x5、芯は 3x3) で 137 → 150 の 13 段。上から東の壁ぞいの歩み板で北の回廊へ
        List<int[]> ring = ring5(CALM_TOWER_X, CALM_TOWER_Z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.fill(CALM_TOWER_X + dx, a.floor() + 1, CALM_TOWER_Z + dz, CALM_TOWER_X + dx, a.floor() + 13, CALM_TOWER_Z + dz,
                        StormKit.wall(Style.HALL, CALM_TOWER_X + dx, 0, CALM_TOWER_Z + dz));
            }
        }
        kit.put(CALM_TOWER_X, a.floor() + 14, CALM_TOWER_Z, StormKit.LAMP);
        for (int k = 0; k < 13; k++) {
            int[] c = ring.get(k);
            int y = a.floor() + 1 + k;
            kit.fill(c[0], a.floor() + 1, c[1], c[0], y, c[1], StormKit.wall(Style.HALL, c[0], y, c[1]));
            kit.put(c[0], y, c[1], StormKit.floor(Style.HALL, c[0], c[1]));
        }
        // 歩み板 (床 150、x 12〜13) を塔の上から北の回廊の東の端まで。内側に手すり
        int[] last = ring.get(12);
        int wy = UPPER_GALLERY_Y;
        for (int z = last[1]; z >= g.z1(); z--) {
            for (int x = 12; x <= 13; x++) {
                kit.put(x, wy, z, StormKit.floor(Style.HALL, x, z));
            }
            if (z < last[1] - 1 && z > g.z2()) {
                kit.put(11, wy + 1, z, Blocks.DIORITE_WALL.defaultBlockState());
            }
        }
        for (int x = g.x2() + 1; x <= 11; x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                kit.put(x, wy, z, StormKit.floor(Style.HALL, x, z));
            }
            kit.put(x, wy + 1, g.z2(), Blocks.DIORITE_WALL.defaultBlockState());
        }
        // 竜骨の門 (床の羽根の輪。閉じているときは封印のブロック)
        for (int x = -2; x <= 2; x++) {
            for (int z = -6; z <= -2; z++) {
                kit.put(x, a.floor(), z, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }
        // 床の羽根の模様 (門のまわりの銅の輪)
        for (int x = -3; x <= 3; x++) {
            for (int z = -7; z <= -1; z++) {
                if (Math.abs(x) == 3 || z == -7 || z == -1) {
                    kit.put(x, a.floor(), z, StormKit.COPPER);
                }
            }
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

    // ---------------------------------------------------------------- 西の試練 風切りの骨
    private static void west(StormKit kit) {
        // 肩の道 (広間の床 137 → 肩の門の 142)
        shoulderPass(kit, WEST_PASS, -1, Style.HALL);
        kit.room(W0, Style.BONE, 5);
        // 上腕と前腕の骨 (空洞の骨の中の回廊)
        bone(kit, WEST_UPPER_X1, WEST_UPPER_X2);
        kit.cave(WEST_ELBOW, ELBOW_R, BONE_FLOOR, BONE_FLOOR + 10, Style.BONE, 5);
        bone(kit, WEST_FORE_X1, WEST_FORE_X2);
        // 左手首の瘤 (丸い洞)。高い岩棚と、その下の昇り風の穴
        kit.cave(WEST_KNOB, KNOB_R, WEST_KNOB.getY(), KNOB_CEILING, Style.BONE, 7);
        StormBackLayout.Area l = WEST_LEDGE;
        for (int x = l.x1(); x <= l.x2(); x++) {
            for (int z = l.z1(); z <= l.z2(); z++) {
                for (int y = WEST_KNOB.getY() + 1; y <= l.floor(); y++) {
                    kit.put(x, y, z, StormKit.wall(Style.BONE, x, y, z));
                }
                kit.put(x, l.floor(), z, StormKit.floor(Style.BONE, x, z));
                // 岩棚の上は 3 段空ける (丸天井の端で低くならないように)
                for (int y = l.floor() + 1; y <= l.floor() + 3; y++) {
                    kit.air(x, y, z);
                }
                kit.solid(x, l.floor() + 4, z, StormKit.wall(Style.BONE, x, l.floor() + 4, z));
            }
        }
        vent(kit, WEST_KNOB_VENT);
        // 骨の梁 (瘤の中を斜めに渡る白い梁。景色と陰)
        for (int i = -8; i <= 8; i++) {
            kit.put(WEST_KNOB.getX() + i, WEST_KNOB.getY() + 9 + Math.abs(i) / 3, WEST_KNOB.getZ() - 6, StormKit.BONE);
        }
    }

    // 肩の道: 広間の壁から 5 段上って肩の門 (dir -1 = 西、+1 = 東)
    private static void shoulderPass(StormKit kit, StormBackLayout.Area p, int dir, Style style) {
        int xIn = dir < 0 ? p.x2() : p.x1();
        int xOut = dir < 0 ? p.x1() : p.x2();
        for (int i = 0; ; i++) {
            int x = xIn + dir * i;
            int floor = HUB.floor() + Math.min(5, Math.max(0, i - 1));
            kit.room(x, x, p.z1(), p.z2(), floor, 4, style, 0);
            if (i >= 1 && i <= 5) {
                for (int z = p.z1(); z <= p.z2(); z++) {
                    kit.stair(x, floor, z, dir < 0 ? Direction.WEST : Direction.EAST, Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState());
                }
            }
            if (x == xOut) {
                break;
            }
        }
        kit.solid(dir < 0 ? p.x2() - 3 : p.x1() + 3, HUB.floor() + 7, p.z1() - 1, StormKit.LAMP);
    }

    // 空洞の骨の一本 (x1〜x2)。割れた床・低い梁・昇り風の穴・高い梁・陰の肋
    private static void bone(StormKit kit, int x1, int x2) {
        kit.room(x1, x2, BONE_Z1, BONE_Z2, BONE_FLOOR, BONE_HEIGHT, Style.BONE, 6);
        // 肋 (骨の中を斜めに渡る細い梁。横風の陰になる)。6 ブロックおきに、北と南の壁から
        for (int x = x2 - 3; x >= x1 + 2; x -= 6) {
            if (inGap(x) || inGap(x + 1) || inGap(x - 1)) {
                continue;
            }
            for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + 3; y++) {
                kit.put(x, y, BONE_Z1, StormKit.BONE);
                kit.put(x, y, BONE_Z2, StormKit.BONE);
            }
            for (int y = BONE_FLOOR + 1; y <= HIGH_BEAM_Y + 2; y += 1) {
                kit.put(x, y, BONE_Z1 + 1, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
            }
        }
    }

    static boolean inGap(int x) {
        for (int[] g : WEST_GAPS) {
            if (x >= g[0] && x <= g[1]) {
                return true;
            }
        }
        return false;
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

    // 道の形 (リセットの種): 割れた床の低い梁の位置と、昇り風の穴と高い梁。建て直さずに試練の側が置き直す
    public static void carveWestRoute(StormKit kit, int route) {
        for (int gi = 0; gi < WEST_GAPS.length; gi++) {
            int[] g = WEST_GAPS[gi];
            // 割れた床: 床と、その下の骨を嵐の雲まで抜く
            for (int x = g[0]; x <= g[1]; x++) {
                for (int z = BONE_Z1; z <= BONE_Z2; z++) {
                    int bottom = BIRD.exists() ? Math.min(BIRD.bottom(x, z), BONE_FLOOR) : BONE_FLOOR - 3;
                    for (int y = bottom - 1; y <= BONE_FLOOR; y++) {
                        kit.air(x, y, z);
                    }
                    for (int y = BONE_FLOOR + 1; y < HIGH_BEAM_Y; y++) {
                        kit.air(x, y, z);
                    }
                }
            }
            // 縁は 1 段高い (割れた床の両側の 1 列)
            for (int z = BONE_Z1; z <= BONE_Z2; z++) {
                kit.put(g[0] - 1, BONE_FLOOR + 1, z, Blocks.CALCITE.defaultBlockState());
                kit.put(g[1] + 1, BONE_FLOOR + 1, z, Blocks.CALCITE.defaultBlockState());
            }
            // 低い梁 (幅 1。形は道の形で 3 通り: 北・南・食い違い)
            int pattern = (route + gi) % 3;
            int zA = pattern == 0 ? -20 : pattern == 1 ? -14 : -19;
            int zB = pattern == 2 ? -15 : zA;
            int mid = (g[0] + g[1]) / 2;
            for (int x = g[0] - 1; x <= g[1] + 1; x++) {
                int z = x <= mid ? zA : zB;
                if (x == g[0] - 1 || x == g[1] + 1) {
                    // 縁の切れ目 (梁に乗る所だけ縁を下げる)
                    kit.put(x, BONE_FLOOR + 1, z, StormKit.AIR);
                    kit.carved().add(BlockPos.asLong(x, BONE_FLOOR + 1, z));
                }
                kit.put(x, BONE_FLOOR, z, StormKit.BONE);
            }
            if (zA != zB) {
                for (int z = Math.min(zA, zB); z <= Math.max(zA, zB); z++) {
                    kit.put(mid, BONE_FLOOR, z, StormKit.BONE);
                    kit.put(mid + 1, BONE_FLOOR, z, StormKit.BONE);
                }
            }
        }
        // 昇り風の穴と、嵐の高い道 (二つの割れた床の上をまとめて渡る梁。幅 2、z -17〜-16)
        for (int arm = 0; arm < 2; arm++) {
            int vx = WEST_VENT_X[arm];
            vent(kit, new BlockPos(vx, BONE_FLOOR, -20));
            int[] far = WEST_GAPS[arm * 2 + 1];
            for (int x = vx + 1; x >= far[0] - 3; x--) {
                for (int z = -17; z <= -16; z++) {
                    kit.put(x, HIGH_BEAM_Y, z, StormKit.BONE);
                }
                // 陰の柱 (梁の北の縁、5 ブロックおき)。横風をよける
                if (Math.floorMod(x, 5) == 0) {
                    kit.put(x, HIGH_BEAM_Y + 1, -18, StormKit.CALCITE);
                    kit.put(x, HIGH_BEAM_Y + 2, -18, StormKit.CALCITE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 東の試練 雷の骨と雷獣の巣
    private static void east(StormKit kit) {
        shoulderPass(kit, EAST_PASS, 1, Style.HALL);
        kit.room(E0, Style.THUNDER, 5);
        kit.room(EAST_X1, EAST_X2, BONE_Z1, BONE_Z2, BONE_FLOOR, EAST_HEIGHT, Style.THUNDER, 7);
        kit.cave(EAST_ELBOW, ELBOW_R, BONE_FLOOR, BONE_FLOOR + 9, Style.THUNDER, 5);
        // 稲妻の筋 (骨の壁と天井を走る銅の筋。手首の瘤へ集まる)
        for (int x = EAST_X1; x <= EAST_X2; x++) {
            int z = BONE_Z1 - 1 + (Math.floorMod(x * 7, 13) < 6 ? 0 : 0);
            int y = BONE_FLOOR + 5 + (int) Math.round(2 * Math.sin(x * 0.45));
            kit.solid(x, y, z, StormKit.COPPER);
            kit.solid(x, BONE_FLOOR + EAST_HEIGHT + 1, -17 + (int) Math.round(3 * Math.sin(x * 0.3)), StormKit.COPPER);
        }
        // 骨の戸 (壁と、口の封印)
        for (int dx : EAST_DOORS) {
            for (int z = BONE_Z1; z <= BONE_Z2; z++) {
                for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + EAST_HEIGHT; y++) {
                    boolean mouth = z >= -18 && z <= -16 && y <= BONE_FLOOR + 4;
                    kit.put(dx, y, z, mouth ? ModBlocks.SEALED_GATE.get().defaultBlockState() : StormKit.wall(Style.BONE, dx, y, z));
                }
            }
        }
        for (BlockPos r : EAST_RODS) {
            rod(kit, r, 0);
        }
        // 右手首の瘤 (雷獣の巣)
        kit.cave(RAIJU_NEST, RAIJU_NEST_RADIUS, RAIJU_NEST.getY(), RAIJU_NEST_CEILING, Style.THUNDER, 8);
        for (BlockPos r : raijuRods()) {
            rod(kit, r, 0);
            // 接地の栓 (銅の取っ手 = レバー)。柱の根元の、洞の真ん中の側
            int dx = Integer.signum(RAIJU_NEST.getX() - r.getX());
            int dz = Integer.signum(RAIJU_NEST.getZ() - r.getZ());
            BlockPos lever = r.offset(Math.abs(dx) >= Math.abs(dz) ? dx : 0, 0, Math.abs(dx) >= Math.abs(dz) ? 0 : dz);
            Direction face = Direction.getNearest(lever.getX() - r.getX(), 0, lever.getZ() - r.getZ());
            kit.put(lever.getX(), lever.getY() + 1, lever.getZ(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, face));
        }
        // 頂の台 (東の石) と、真ん中の昇り風の穴 (雷獣を倒すと立つ)
        StormBackLayout.Area p = EAST_PERCH;
        for (int x = p.x1(); x <= p.x2(); x++) {
            for (int z = p.z1(); z <= p.z2(); z++) {
                kit.put(x, p.floor(), z, StormKit.COPPER);
                for (int y = p.floor() + 1; y <= p.floor() + 2; y++) {
                    kit.air(x, y, z);
                }
            }
        }
        for (int y = p.floor() + 3; y <= RAIJU_NEST_CEILING + 1; y++) {
            kit.put(p.x2(), y, p.z1(), StormKit.TILE);
        }
        vent(kit, RAIJU_VENT);
    }

    // 避雷の柱 (根元 base、高さ 6)。根元の銅、段の電球 3 つ、銅、先の避雷針
    static void rod(StormKit kit, BlockPos base, int charge) {
        kit.put(base.getX(), base.getY(), base.getZ(), StormKit.COPPER_BLOCK);
        for (int i = 0; i < 3; i++) {
            kit.put(base.getX(), base.getY() + 1 + i, base.getZ(), Blocks.WAXED_COPPER_BULB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, charge > i));
        }
        kit.put(base.getX(), base.getY() + 4, base.getZ(), StormKit.COPPER);
        kit.put(base.getX(), base.getY() + 5, base.getZ(), Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    // ---------------------------------------------------------------- 前の試練 首と眼の間
    private static void front(StormKit kit) {
        kit.room(NECK, Style.HALL, 6);
        kit.room(F0, Style.EYE, 5);
        // 前室から眼の間へ: 北へ 4 段下りる
        for (int z = F0.z1() - 1; z >= EYE_HALL.z2() + 1; z--) {
            int floor = Math.max(EYE_HALL.floor(), F0.floor() - Math.max(0, (F0.z1() - 2) - z));
            kit.room(-1, 1, z, z, floor, 4, Style.EYE, 0);
            if (floor > EYE_HALL.floor() && floor < F0.floor()) {
                for (int x = -1; x <= 1; x++) {
                    kit.stair(x, floor, z, Direction.SOUTH, Blocks.QUARTZ_STAIRS.defaultBlockState());
                }
            }
        }
        StormBackLayout.Area e = EYE_HALL;
        kit.room(e, Style.EYE, 0);
        // 天井の灯り (格子の上)
        for (int c = 0; c < 5; c += 2) {
            for (int r = 0; r < 5; r += 2) {
                BlockPos g = gridCell(c, r);
                kit.solid(g.getX(), e.floor() + e.height() + 1, g.getZ(), StormKit.LAMP);
            }
        }
        // 鏡の台 (25 台。鏡は試練の側が置く)
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                BlockPos g = gridCell(c, r);
                kit.put(g.getX(), BEAM_Y - 1, g.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            }
        }
        // 左の眼の窓 (西の壁の光の帯。光の入る行はガラスが光る)
        for (int r = 0; r < 5; r++) {
            BlockPos g = gridCell(0, r);
            for (int x = e.x1() - 3; x <= e.x1() - 1; x++) {
                kit.put(x, BEAM_Y, g.getZ(), StormKit.LENS);
            }
        }
        // 嘴の付け根の小部屋 (前の石) と、戸
        kit.room(BEAK_ROOM, Style.EYE, 0);
        kit.solid(0, BEAK_ROOM.floor() + BEAK_ROOM.height() + 1, -92, StormKit.LAMP);
        for (int z = BEAK_DOOR_Z1; z <= BEAK_DOOR_Z2; z++) {
            for (int x = -1; x <= 1; x++) {
                kit.put(x, e.floor(), z, StormKit.floor(Style.EYE, x, z));
                for (int y = e.floor() + 1; y <= e.floor() + 3; y++) {
                    kit.put(x, y, z, ModBlocks.SEALED_GATE.get().defaultBlockState());
                }
            }
        }
        // 戻しの鏡の台 (前室)
        kit.put(MIRROR_RESET.getX(), MIRROR_RESET.getY() - 1, MIRROR_RESET.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        kit.put(MIRROR_RESET.getX(), MIRROR_RESET.getY(), MIRROR_RESET.getZ(), Blocks.LEVER.defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.EAST));
    }

    // ---------------------------------------------------------------- 竜骨の門の下: 低い道・螺旋・床 121 の道・前室・竜骨の間
    private static void keel(StormKit kit) {
        int tf = KEEL_TUNNEL_FLOOR;
        // 低い道 (広間の床の下、x -16〜-2、z -5〜-3)
        kit.room(-16, -2, -5, -3, tf, 2, Style.KEEL, 5);
        // 西の螺旋 (5x5、135 → 122)
        int cx = KEEL_SPIRAL.getX();
        int cz = KEEL_SPIRAL.getZ();
        kit.room(cx - 2, cx + 2, cz - 2, cz + 2, KEEL_ANTE.floor(), tf + 2 - KEEL_ANTE.floor(), Style.KEEL, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.fill(cx + dx, KEEL_ANTE.floor() + 1, cz + dz, cx + dx, tf + 2, cz + dz, StormKit.wall(Style.KEEL, cx + dx, 0, cz + dz));
            }
        }
        // 低い道の西の端 (x -16) から螺旋の外周へ。東の辺の真ん中 (cx+2, cz) が床 134 の入口。そこから反時計回りに 1 段ずつ下りる
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
        // 床 121 の道: 螺旋の下から北へ、そして東へ前室まで
        int bx = bottom[0];
        int bz = bottom[1];
        kit.room(bx - 1, bx + 1, KEEL_ANTE.z1() - 1 + 3, bz, KEEL_ANTE.floor(), 3, Style.KEEL, 5);
        kit.room(bx - 1, KEEL_ANTE.x1() - 1, KEEL_ANTE.z1() + 1, KEEL_ANTE.z1() + 3, KEEL_ANTE.floor(), 3, Style.KEEL, 5);
        kit.room(KEEL_ANTE, Style.KEEL, 3);
        // 竜骨の間の入口 (前室の北の口、z -30)
        for (int x = -1; x <= 1; x++) {
            for (int y = KEEL_ANTE.floor() + 1; y <= KEEL_ANTE.floor() + 3; y++) {
                kit.air(x, y, KEEL_ANTE.z2() + 1);
            }
        }
        // 竜骨の間 (円形、床 120、空気は 133 まで)。細かい形 (柱・風穴・帯電の床) は心臓の担当が建て直してよい
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
        // 前室の床 (121) と竜骨の間の外の床 (121) をつなぐ: 入口の下
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

    // ---------------------------------------------------------------- 背の上へ上る段 (尾の間の西の壁から 5x5 の螺旋)
    private static void topStair(StormKit kit) {
        int cx = TOP_STAIR.getX();
        int cz = TOP_STAIR.getZ();
        int floor = TAIL_HALL.floor();
        // 尾の間の西の壁の口 (x -11、z 26)
        kit.room(-12, -11, cz, cz, floor, 3, Style.HALL, 0);
        int surface = Math.max(floor + 6, top(cx, cz));
        List<int[]> ring = ring5(cx, cz);
        int start = 0;
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i)[0] == cx + 2 && ring.get(i)[1] == cz) {
                start = i;
            }
        }
        kit.room(cx - 2, cx + 2, cz - 2, cz + 2, floor, surface - floor + 1, Style.HALL, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.fill(cx + dx, floor + 1, cz + dz, cx + dx, surface + 1, cz + dz, StormKit.wall(Style.HALL, cx + dx, 0, cz + dz));
            }
        }
        int k = 0;
        int y = floor;
        while (y < surface) {
            int[] c = ring.get(Math.floorMod(start + 1 + k, 16));
            y = floor + 1 + k;
            for (int yy = floor + 1; yy < y; yy++) {
                if (!kit.isCarved(c[0], yy, c[1]) || yy < y - 3) {
                    kit.put(c[0], yy, c[1], StormKit.wall(Style.HALL, c[0], yy, c[1]));
                }
            }
            kit.put(c[0], y, c[1], StormKit.floor(Style.HALL, c[0], c[1]));
            k++;
        }
        // 上の口 (最後の段の上を空へ開け、まわりに低い縁)
        int[] last = ring.get(Math.floorMod(start + k, 16));
        for (int yy = y + 1; yy <= Math.max(y + 3, top(last[0], last[1]) + 3); yy++) {
            kit.air(last[0], yy, last[1]);
        }
        int[] exit = ring.get(Math.floorMod(start + k + 1, 16));
        int et = top(exit[0], exit[1]);
        for (int yy = y; yy <= et; yy++) {
            kit.put(exit[0], yy, exit[1], StormKit.FEATHER);
        }
        // 螺旋の屋根 (縦穴を空から見てふさぐ。口だけ空ける)
        for (int[] c : ring) {
            if (c != last) {
                kit.solid(c[0], surface + 2, c[1], StormKit.FEATHER);
            }
        }
        kit.solid(cx, surface + 2, cz, StormKit.LAMP);
    }

    // ---------------------------------------------------------------- 休み場・碑文・石・抜け道の床・広間の灯り
    private static void fixtures(StormKit kit) {
        for (StormBackLayout.Rest r : RESTS) {
            kit.campfire(r.campfire());
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.facing() == Direction.UP) {
                kit.put(i.pos().getX(), i.pos().getY(), i.pos().getZ(), Blocks.BIRCH_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
                kit.writeSign(i.pos(), i.key(), net.minecraft.world.item.DyeColor.LIGHT_BLUE);
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
        // 風鳴り石 (台の上)
        for (BlockPos s : new BlockPos[] {WEST_STONE, EAST_STONE, FRONT_STONE}) {
            kit.put(s.getX(), s.getY() - 1, s.getZ(), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            kit.put(s.getX(), s.getY(), s.getZ(), StormBackSetup.CHIME_STONE.get().defaultBlockState());
        }
        // 三つ目の受け羽の下の、人の形の小さな浮き彫り (四角い頭)。眼の間の北東の角
        kit.put(7, EYE_HALL.floor() + 1, EYE_HALL.z1() - 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        kit.put(7, EYE_HALL.floor() + 2, EYE_HALL.z1() - 1, Blocks.QUARTZ_PILLAR.defaultBlockState());
        kit.put(7, EYE_HALL.floor() + 3, EYE_HALL.z1() - 1, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
    }

    // 竜骨の門を開ける・閉じる (三つの石がそろうと開く)。開くと床の輪の下に 3 段の階段
    public static void setKeelGate(net.minecraft.server.level.ServerLevel level, boolean open) {
        BlockState seal = ModBlocks.SEALED_GATE.get().defaultBlockState();
        BlockState stair = Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.EAST);
        for (int x = -2; x <= 2; x++) {
            for (int z = -6; z <= -2; z++) {
                BlockPos p = new BlockPos(x, HUB.floor(), z);
                level.setBlock(p, open ? Blocks.AIR.defaultBlockState() : seal, 3);
                level.setBlock(p.below(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(p.below(2), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(p.below(3), StormKit.floor(Style.KEEL, x, z), 3);
            }
            // 階段 (東から西へ下る): x 2 は床の高さ、x 1 は 1 下、x 0 は 2 下。x -1・-2 は低い道の床
            for (int z = -5; z <= -3; z++) {
                if (open) {
                    if (x == 2) {
                        level.setBlock(new BlockPos(x, HUB.floor(), z), stair, 3);
                    } else if (x == 1) {
                        level.setBlock(new BlockPos(x, HUB.floor() - 1, z), stair, 3);
                    } else if (x == 0) {
                        level.setBlock(new BlockPos(x, HUB.floor() - 2, z), stair, 3);
                    }
                }
            }
        }
        // 低い道とつなぐ (x -2 の西の壁を空ける)
        for (int z = -5; z <= -3; z++) {
            for (int y = KEEL_TUNNEL_FLOOR + 1; y <= KEEL_TUNNEL_FLOOR + 2; y++) {
                level.setBlock(new BlockPos(-3, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (open) {
            level.setBlock(new BlockPos(0, HUB.floor() - 4, -4), StormKit.LAMP, 3);
        }
    }

    static boolean isWall(BlockState s) {
        return s.getBlock() instanceof WallBlock;
    }
}
