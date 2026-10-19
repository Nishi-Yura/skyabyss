package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormExtra.*;

import java.util.List;

import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Inscription;
import jp.revati.skyabyss.titan.storm.back.StormKit.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の背の「任意の翼」を建てる (StormExtra の座標。鳥の形の上に建てる。尾の間の螺旋だけは体の中)。
// 外に開いた所 (広場・外の台・廊・庭) の空気は掘った所として覚えない (kit.put で空気を置く)。覚えると、あとの seal が屋根をかけてしまう
final class StormExtraBuilder {
    private static final BlockState AIR = StormKit.AIR;
    private static final BlockState RAIL = StormDeco.RAIL;

    private StormExtraBuilder() {
    }

    static void build(StormKit kit) {
        plaza(kit);
        turret(kit);
        for (Tower t : TOWERS) {
            tower(kit, t);
        }
        for (int side : new int[] {-1, 1}) {
            outer(kit, side);
            tip(kit, side);
            garden(kit, side);
        }
        lookout(kit);
        signs(kit);
    }

    // ---------------------------------------------------------------- 部品
    private static BlockState wall(Style s, int x, int y, int z) {
        return StormKit.wall(s, x, y, z);
    }

    // (x, z) の柱を、鳥の背から floorY の 1 下まで同じ石で埋める (支えのない所は 10 までで止める)
    private static void support(StormKit kit, int x, int z, int floorY, Style s) {
        support(kit, x, z, floorY, s, 10);
    }

    private static void support(StormKit kit, int x, int z, int floorY, Style s, int depth) {
        int from = Math.max(StormBirdBuilder.top(x, z) + 1, floorY - depth);
        for (int y = from; y < floorY; y++) {
            kit.put(x, y, z, wall(s, x, y, z));
        }
    }

    private static void clear(StormKit kit, int x1, int x2, int y1, int y2, int z1, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    kit.put(x, y, z, AIR);
                }
            }
        }
    }

    // 縁の手すり (床と同じ高さの石の上に低い壁)
    private static void rail(StormKit kit, int x, int z, int floorY, Style s) {
        support(kit, x, z, floorY, s);
        kit.put(x, floorY, z, wall(s, x, floorY, z));
        kit.put(x, floorY + 1, z, RAIL);
    }

    // 碑の台 (attach に柱、pos に碑。床の上 3 段)
    private static void stele(StormKit kit, Inscription i, int floorY) {
        BlockPos at = i.pos().relative(i.facing().getOpposite());
        for (int y = floorY + 1; y <= i.pos().getY() + 1; y++) {
            kit.put(at.getX(), y, at.getZ(), y == floorY + 1 || y == i.pos().getY() + 1 ? StormKit.COPPER : StormKit.CALCITE);
        }
        kit.put(i.pos().getX(), i.pos().getY(), i.pos().getZ(), AIR);
    }

    private static void signs(StormKit kit) {
        for (Inscription i : SIGNS) {
            int floorY = i.pos().getY() - 2;
            stele(kit, i, floorY);
        }
    }

    // ---------------------------------------------------------------- 背の広場
    private static void plaza(StormKit kit) {
        int fy = DECK_Y;
        for (int x = -25; x <= 25; x++) {
            for (int z = 11; z <= 36; z++) {
                boolean inside = x >= -24 && x <= 24 && z >= 12 && z <= 35;
                if (inside) {
                    support(kit, x, z, fy, Style.FEATHER);
                    kit.put(x, fy, z, plazaFloor(x, z));
                    for (int y = fy + 1; y <= fy + 12; y++) {
                        kit.put(x, y, z, AIR);
                    }
                } else if (Math.abs(x) == 25 && z >= 12 && z <= 34) {
                    continue; // 塔の壁
                } else if (z == 36 && x >= TURRET_X - 3 && x <= TURRET_X + 3) {
                    continue; // 螺旋の塔の壁
                } else {
                    rail(kit, x, z, fy, Style.FEATHER);
                }
            }
        }
        // 縁の灯りの柱 (8 おき)
        for (int x = -20; x <= 20; x += 8) {
            StormDeco.lampPost(kit, x, fy, 11);
            if (Math.abs(x - TURRET_X) > 4) {
                StormDeco.lampPost(kit, x, fy, 36);
            }
        }
        for (int sx : new int[] {-25, 25}) {
            StormDeco.lampPost(kit, sx, fy, 35);
            StormDeco.lampPost(kit, sx, fy, 11);
        }
        // 中央の風見の円盤は床の模様。まわりに羽軸の柱と腰掛け
        for (int sx : new int[] {-12, 12}) {
            StormDeco.featherPillar(kit, sx, 17, fy, 7);
            StormDeco.featherPillar(kit, sx, 29, fy, 7);
        }
        StormDeco.bench(kit, -4, -2, 28, 28, fy + 1, Direction.NORTH);
        StormDeco.bench(kit, 2, 4, 28, 28, fy + 1, Direction.NORTH);
        // 行き先の色の線 (広場の真ん中から西の塔 = 水色、東の塔 = 金へ。南は螺旋の塔へ)
        StormDeco.strip(kit, fy, StormDeco.WEST_LINE, StormDeco.WEST_PIP, 4, 1, new int[] {-5, 23}, new int[] {-23, 23});
        StormDeco.strip(kit, fy, StormDeco.EAST_LINE, StormDeco.EAST_PIP, 4, 1, new int[] {5, 23}, new int[] {23, 23});
    }

    // 広場の床: 羽の縞に、真ん中の風見の円盤 (銅と白の同心の四角) と十字
    private static BlockState plazaFloor(int x, int z) {
        int dx = Math.abs(x);
        int dz = Math.abs(z - 23);
        int r = Math.max(dx, dz);
        boolean border = dx == 24 || z == 12 || z == 35;
        if (border) {
            return StormKit.COPPER;
        }
        if (r <= 8) {
            return r == 8 || r == 4 ? StormKit.COPPER : r == 6 || r == 2 ? StormKit.FEATHER_WHITE : StormKit.QUARTZ;
        }
        if (dx <= 1 || dz <= 1) {
            return StormKit.FEATHER_WHITE;
        }
        return StormKit.floor(Style.FEATHER, x, z);
    }

    // ---------------------------------------------------------------- 尾の間の北西の螺旋 (体の中) → 広場の南の戸
    private static void turret(StormKit kit) {
        int cx = TURRET_X;
        int cz = TURRET_Z;
        int base = StormBackLayout.TAIL_HALL.floor();
        int top = DECK_Y + 4;
        // 箱を石で満たす (尾の間の空気も)。戸の穴は後で
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                for (int y = base + 1; y <= top; y++) {
                    kit.put(x, y, z, wall(Style.FEATHER, x, y, z));
                }
                // 箱の下の床は尾の間のまま
                support(kit, x, z, base, Style.FEATHER);
            }
        }
        List<int[]> ring = StormBirdBuilder.ring5(cx, cz);
        int start = 2;
        for (int k = 0; k < TURRET_STEPS; k++) {
            int[] c = ring.get((start + k) % 16);
            int y = base + 1 + k;
            for (int yy = y + 1; yy <= y + 3; yy++) {
                kit.air(c[0], yy, c[1]);
            }
            kit.put(c[0], y, c[1], StormKit.floor(Style.FEATHER, c[0], c[1]));
        }
        // 戸: 下は尾の間の床 (北の壁 cz-3 の 3 マス、高さ 3)。上は広場の床の高さ
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                kit.air(cx + dx, base + dy, TURRET_DOOR_Z);
                kit.air(cx + dx, DECK_Y + dy, TURRET_DOOR_Z);
            }
            kit.put(cx + dx, DECK_Y, TURRET_DOOR_Z, StormKit.floor(Style.FEATHER, cx + dx, TURRET_DOOR_Z));
            kit.put(cx + dx, base, TURRET_DOOR_Z, StormKit.floor(Style.FEATHER, cx + dx, TURRET_DOOR_Z));
        }
        // 戸の枠 (銅) と、戸の上の灯り
        for (int sx : new int[] {-2, 2}) {
            for (int y = base + 1; y <= base + 3; y++) {
                kit.put(cx + sx, y, TURRET_DOOR_Z, StormKit.COPPER);
            }
            for (int y = DECK_Y + 1; y <= DECK_Y + 3; y++) {
                kit.put(cx + sx, y, TURRET_DOOR_Z, StormKit.COPPER);
            }
        }
        kit.put(cx, base + 4, TURRET_DOOR_Z, StormKit.LAMP);
        kit.put(cx, DECK_Y + 4, TURRET_DOOR_Z, StormKit.LAMP);
        // 芯の灯り (3 段おき。その段の頭の高さに、芯の面が螺旋に面する所)
        for (int k = 0; k < TURRET_STEPS; k += 3) {
            int[] c = ring.get((start + k) % 16);
            if (Math.abs(c[0] - cx) == 2 && Math.abs(c[1] - cz) == 2) {
                continue;
            }
            int lx = Math.max(cx - 1, Math.min(cx + 1, c[0]));
            int lz = Math.max(cz - 1, Math.min(cz + 1, c[1]));
            kit.put(lx, base + 1 + k + 2, lz, StormKit.LAMP);
        }
        // 屋根の縁の銅の帯
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                if (Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3) {
                    kit.put(x, top, z, StormKit.COPPER);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 塔
    private static void tower(StormKit kit, Tower t) {
        boolean vane = t == VANE;
        Style style = vane ? Style.FEATHER : Style.THUNDER;
        int lo = Math.min(t.x(0), t.x(18));
        int hi = Math.max(t.x(0), t.x(18));
        // 土台: 塔の足あとの下
        for (int x = t.xMin(); x <= t.xMax(); x++) {
            for (int z = 12; z <= 34; z++) {
                support(kit, x, z, DECK_Y, style, 24);
            }
        }
        for (int i = 0; i < TIERS; i++) {
            kit.room(lo, hi, Z1, Z2, floorY(i), TIER_H - 1, style, 6);
        }
        // 頂の甲板 (最上階の天井 = 床。まわりに胸壁)
        int ty = TOP_Y;
        for (int x = t.xMin(); x <= t.xMax(); x++) {
            for (int z = 12; z <= 34; z++) {
                kit.put(x, ty, z, StormKit.floor(vane ? Style.FEATHER : Style.THUNDER, x, z));
                for (int y = ty + 1; y <= ty + 9; y++) {
                    kit.put(x, y, z, AIR);
                }
                boolean edge = x == t.xMin() || x == t.xMax() || z == 12 || z == 34;
                if (edge) {
                    kit.put(x, ty, z, StormKit.COPPER);
                    kit.put(x, ty + 1, z, RAIL);
                }
            }
        }
        for (int i = 0; i < TIERS; i++) {
            tierShell(kit, t, i, style);
        }
        for (int i = 0; i <= TIERS; i++) {
            stairAndHole(kit, t, i);
        }
        // 外の台 (1〜5。5 は頂の台)
        for (int i = 1; i <= TIERS; i++) {
            balcony(kit, t, i);
        }
        if (vane) {
            padAt(kit, vanePads().get(0));
        }
        // 戸 (面の戸は全階。奥の戸は一階だけ = 外の廊への戸)
        for (int i = 0; i < TIERS; i++) {
            door(kit, t.faceX(), floorY(i), t.dir());
        }
        door(kit, t.farWallX(), floorY(0), -t.dir());
        // 軸 (風見の軸・避雷の芯)。一階から頂の上まで
        axle(kit, t, vane);
        for (int i = 0; i < TIERS; i++) {
            furnish(kit, t, i);
        }
        crown(kit, t, vane);
    }

    // 階 i の窓・帯・外の柱 (外側の飾り)
    private static void tierShell(StormKit kit, Tower t, int i, Style style) {
        int f = floorY(i);
        int fx = t.faceX();
        int far = t.farWallX();
        // 銅の帯 (階の境の外周)
        for (int x = t.xMin(); x <= t.xMax(); x++) {
            for (int z : new int[] {12, 34}) {
                kit.put(x, f, z, StormKit.COPPER);
            }
        }
        for (int z = 12; z <= 34; z++) {
            kit.put(fx, f, z, StormKit.COPPER);
            kit.put(far, f, z, StormKit.COPPER);
        }
        // 窓 (北と南の壁・奥の壁。3 幅、高さ 4。羽の窓 = 水色のガラス)
        for (int u = 2; u <= 16; u += 7) {
            for (int dy = 3; dy <= 6; dy++) {
                for (int du = 0; du < 3; du++) {
                    int x = t.x(u + du);
                    kit.put(x, f + dy, 12, StormKit.LENS);
                    kit.put(x, f + dy, 34, StormKit.LENS);
                }
            }
        }
        for (int z : new int[] {15, 19, 27, 31}) {
            for (int dy = 3; dy <= 6; dy++) {
                kit.put(far, f + dy, z, StormKit.LENS);
                kit.put(far, f + dy, z + 1, StormKit.LENS);
            }
        }
        // 外の肋 (北と南の壁の外に 1 マス張り出す柱。4 おき)
        for (int u = 0; u <= 18; u += 6) {
            int x = t.x(u);
            for (int y = f + 1; y <= f + TIER_H - 1; y++) {
                kit.put(x, y, 11, y % 4 == 0 ? StormKit.COPPER : StormKit.CALCITE);
                kit.put(x, y, 35, y % 4 == 0 ? StormKit.COPPER : StormKit.CALCITE);
            }
        }
        // 外の肋の根元の土台 (広場の高さより低い所には出ない)
        if (i == 0) {
            for (int u = 0; u <= 18; u += 6) {
                int x = t.x(u);
                support(kit, x, 11, DECK_Y, style, 24);
                support(kit, x, 35, DECK_Y, style, 24);
            }
        }
    }

    // 階段 (階 i → i+1) と、上の階の床の穴・穴の手すり
    private static void stairAndHole(StormKit kit, Tower t, int i) {
        // i 番目の階の床の穴 = 階 i-1 から昇る階段の上の部分。階 i の階段 = i → i+1 (i < TIERS)
        boolean tierStair = i < TIERS;
        if (tierStair) {
            boolean a = (i & 1) == 0;
            int u0 = a ? STRIP_A_U : STRIP_B_U;
            int f = floorY(i);
            for (int j = 0; j < STEPS; j++) {
                int z = a ? 14 + j : 32 - j;
                for (int du = 0; du < 3; du++) {
                    int x = t.x(u0 + du);
                    kit.fill(x, f + 1, z, x, f + 1 + j, z, wall(t == VANE ? Style.FEATHER : Style.THUNDER, x, f + j, z));
                    kit.put(x, f + 1 + j, z, StormKit.floor(t == VANE ? Style.FEATHER : Style.THUNDER, x, z));
                }
                if (j >= 1 && j % 3 == 1) {
                    // 段の下の灯り (階段の脇の面に光る)
                    kit.put(t.x(u0), f + j, z, StormKit.LAMP);
                    kit.put(t.x(u0 + 2), f + j, z, StormKit.LAMP);
                }
            }
        }
        if (i >= 1) {
            // 下の階の階段の上の端の穴 (階 i の床。z 21〜24 か 25〜22)
            boolean a = ((i - 1) & 1) == 0;
            int u0 = a ? STRIP_A_U : STRIP_B_U;
            int f = floorY(i);
            int z1 = a ? 21 : 22;
            int z2 = a ? 24 : 25;
            for (int z = z1; z <= z2; z++) {
                for (int du = 0; du < 3; du++) {
                    kit.air(t.x(u0 + du), f, z);
                }
            }
            // 手すり (穴の三辺。登ってくる側の辺と、両脇)
            int lowZ = a ? 20 : 26;
            for (int z = z1; z <= z2; z++) {
                kit.put(t.x(u0 - 1), f + 1, z, RAIL);
                kit.put(t.x(u0 + 3), f + 1, z, RAIL);
            }
            for (int du = -1; du <= 3; du++) {
                kit.put(t.x(u0 + du), f + 1, lowZ, RAIL);
            }
        }
    }

    // 外の台 (階 i の床の高さ。面の壁から d 1〜7 の x、z 19〜27)。風見の塔は昇り風の柱と穴つき
    private static void balcony(StormKit kit, Tower t, int i) {
        boolean vane = t == VANE;
        int f = floorY(i);
        for (int d = 1; d <= 7; d++) {
            int x = t.bx(d);
            for (int z = BZ1 - 1; z <= BZ2 + 1; z++) {
                boolean inside = z >= BZ1 && z <= BZ2;
                if (inside) {
                    kit.put(x, f, z, StormKit.floor(Style.FEATHER, x, z));
                    kit.put(x, f - 1, z, StormKit.COPPER);
                    for (int y = f + 1; y <= f + 6; y++) {
                        kit.put(x, y, z, AIR);
                    }
                } else {
                    kit.put(x, f, z, StormKit.COPPER);
                    kit.put(x, f - 1, z, StormKit.COPPER);
                    kit.put(x, f + 1, z, RAIL);
                }
            }
        }
        // 外の縁
        int ex = t.bx(8);
        for (int z = BZ1 - 1; z <= BZ2 + 1; z++) {
            kit.put(ex, f, z, StormKit.COPPER);
            kit.put(ex, f - 1, z, StormKit.COPPER);
            kit.put(ex, f + 1, z, RAIL);
        }
        // 角の灯り
        StormDeco.lampPost(kit, t.bx(7), f, BZ1);
        StormDeco.lampPost(kit, t.bx(7), f, BZ2);
        if (vane) {
            // 昇り風の柱の穴 (階 i-1 の柱の真上)。下の柱の位置 = (bx(4), padZ(i-1))
            int nz = padZ(i - 1);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = t.bx(4) + dx * t.dir();
                    kit.put(x, f, nz + dz, AIR);
                    kit.put(x, f - 1, nz + dz, AIR);
                }
            }
            if (i < TIERS) {
                padAt(kit, vanePads().get(i));
            }
        }
    }

    // 昇り風の柱 (3x3 の銅の格子と、その下の灯り)。根の座標 = 中心の床
    private static void padAt(StormKit kit, BlockPos c) {
        StormBirdBuilder.vent(kit, c);
        // 柱のまわりの縁取り (目印の灯り)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    int x = c.getX() + dx;
                    int z = c.getZ() + dz;
                    BlockState cur = kit.get(x, c.getY(), z);
                    if (cur.is(StormKit.FEATHER.getBlock()) || cur.is(StormKit.FEATHER_WHITE.getBlock()) || cur.is(StormKit.CALCITE.getBlock())
                            || cur.is(StormKit.QUARTZ.getBlock()) || cur.is(StormKit.DIORITE.getBlock())) {
                        kit.put(x, c.getY(), z, (dx + dz) % 2 == 0 ? StormKit.LAMP : StormKit.COPPER);
                    }
                }
            }
        }
    }

    // 戸 (壁の x、床の y。向き dir は戸の外 = 外の台の側)。z 21〜25、高さ 4 に銅の枠
    private static void door(StormKit kit, int wallX, int floorY, int outDir) {
        for (int z = DOOR_Z1; z <= DOOR_Z2; z++) {
            for (int y = floorY + 1; y <= floorY + DOOR_H; y++) {
                kit.air(wallX, y, z);
            }
            kit.put(wallX, floorY, z, StormKit.COPPER);
        }
        for (int y = floorY + 1; y <= floorY + DOOR_H; y++) {
            kit.put(wallX, y, DOOR_Z1 - 1, StormKit.COPPER);
            kit.put(wallX, y, DOOR_Z2 + 1, StormKit.COPPER);
        }
        for (int z = DOOR_Z1 - 1; z <= DOOR_Z2 + 1; z++) {
            kit.put(wallX, floorY + DOOR_H + 1, z, StormKit.COPPER);
        }
        kit.put(wallX, floorY + DOOR_H + 1, (DOOR_Z1 + DOOR_Z2) / 2, StormKit.LAMP);
    }

    // 軸: 一階の床から頂の甲板の上まで (風見の軸 = 銅と石英、避雷の芯 = 銅と深い石)。各階の床の上に台座
    private static void axle(StormKit kit, Tower t, boolean vane) {
        int ax = t.x(AXLE_U);
        BlockState shaft = vane ? StormKit.COPPER_BLOCK : Blocks.WAXED_EXPOSED_COPPER.defaultBlockState();
        for (int y = DECK_Y + 1; y <= TOP_Y; y++) {
            kit.put(ax, y, AXLE_Z, y % 6 == 0 ? Blocks.WAXED_CHISELED_COPPER.defaultBlockState() : shaft);
        }
        for (int i = 0; i < TIERS; i++) {
            int f = floorY(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        kit.put(ax + dx, f + 1, AXLE_Z + dz, i % 2 == 0 ? StormKit.COPPER : Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                    }
                }
            }
            // 軸のまわりの灯り (腰の高さ。4 方向)
            kit.put(ax + 2, f + 1, AXLE_Z, StormKit.LAMP);
            kit.put(ax - 2, f + 1, AXLE_Z, StormKit.LAMP);
        }
    }

    // 各階の家具 (階ごとに違う部屋)
    private static void furnish(StormKit kit, Tower t, int i) {
        boolean vane = t == VANE;
        int f = floorY(i);
        // 柱 (羽軸の柱か、避雷の柱)。奥から 5 のところの二本。避雷の塔は避雷の柱 (StormExtra.towerRods)
        if (vane) {
            StormDeco.featherPillar(kit, t.x(5), 17, f, 8);
            StormDeco.featherPillar(kit, t.x(5), 29, f, 8);
        } else {
            for (int z : new int[] {17, 29}) {
                BlockPos base = new BlockPos(t.x(5), f + 1, z);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        kit.put(base.getX() + dx, f, base.getZ() + dz, StormKit.COPPER);
                    }
                }
                StormBirdBuilder.rod(kit, base, 0);
            }
        }
        int near = 2;
        switch (i) {
            case 0 -> {
                // 関所の広間: 軸のまわりの円盤の床 (風見・避雷の円)。腰掛けを戸の両脇に
                int ax = t.x(AXLE_U);
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        int r = Math.max(Math.abs(dx), Math.abs(dz));
                        if (r >= 3 && (r == 3 || r == 5)) {
                            kit.put(ax + dx, f, AXLE_Z + dz, r == 3 ? StormKit.COPPER : vane ? StormKit.FEATHER_WHITE : Blocks.YELLOW_CONCRETE.defaultBlockState());
                        }
                    }
                }
                StormDeco.bench(kit, t.x(near), t.x(near), DOOR_Z1 - 4, DOOR_Z1 - 2, f + 1, t.dir() > 0 ? Direction.EAST : Direction.WEST);
                StormDeco.bench(kit, t.x(near), t.x(near), DOOR_Z2 + 2, DOOR_Z2 + 4, f + 1, t.dir() > 0 ? Direction.EAST : Direction.WEST);
            }
            case 1 -> shelves(kit, t, f, vane ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            case 2 -> {
                // 窓辺の台 (北と南の窓の下に低い台と灯り)
                for (int u = 2; u <= 16; u += 7) {
                    for (int du = 0; du < 3; du++) {
                        kit.put(t.x(u + du), f + 1, 14, vane ? Blocks.CARTOGRAPHY_TABLE.defaultBlockState() : Blocks.LIGHTNING_ROD.defaultBlockState());
                        kit.put(t.x(u + du), f + 1, 32, vane ? Blocks.CARTOGRAPHY_TABLE.defaultBlockState() : Blocks.LIGHTNING_ROD.defaultBlockState());
                    }
                }
            }
            case 3 -> {
                for (int u = 1; u <= 8; u += 2) {
                    kit.put(t.x(u), f + 1, 14, vane ? Blocks.LOOM.defaultBlockState() : Blocks.WAXED_COPPER_GRATE.defaultBlockState());
                    kit.put(t.x(u), f + 1, 32, vane ? Blocks.FLETCHING_TABLE.defaultBlockState() : Blocks.WAXED_COPPER_GRATE.defaultBlockState());
                }
            }
            default -> {
                // 軸の間: 軸のまわりに一段高い歯車の円盤 (風見の歯車・放電の輪)
                int ax = t.x(AXLE_U);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        int r2 = dx * dx + dz * dz;
                        if (r2 >= 1 && r2 <= 10) {
                            BlockState b = r2 >= 5 ? ((dx + dz) % 2 == 0 ? Blocks.WAXED_CHISELED_COPPER.defaultBlockState() : StormKit.COPPER) : StormKit.QUARTZ;
                            kit.put(ax + dx, f + 1, AXLE_Z + dz, b);
                        }
                    }
                }
                // 円盤の灯り (軸の四方。円盤に埋まらないよう上書きしたあとに置く)
                kit.put(ax + 2, f + 1, AXLE_Z, StormKit.LAMP);
                kit.put(ax - 2, f + 1, AXLE_Z, StormKit.LAMP);
                kit.put(ax, f + 1, AXLE_Z + 2, StormKit.LAMP);
                kit.put(ax, f + 1, AXLE_Z - 2, StormKit.LAMP);
            }
        }
    }

    private static void shelves(StormKit kit, Tower t, int f, BlockState shelf) {
        for (int u = 1; u <= 9; u++) {
            if (u % 4 == 1) {
                continue;
            }
            for (int dy = 1; dy <= 2; dy++) {
                kit.put(t.x(u), f + dy, 14, shelf);
                kit.put(t.x(u), f + dy, 32, shelf);
            }
        }
        kit.put(t.x(2), f + 1, 17, Blocks.LECTERN.defaultBlockState());
    }

    // 塔の頂: 風見の塔 = 軸の先の風見 (北を指す羽の矢)、避雷の塔 = 軸の先の大きな避雷の柱。宝箱と碑、休む腰掛け
    private static void crown(StormKit kit, Tower t, boolean vane) {
        int ty = TOP_Y;
        int ax = t.x(AXLE_U);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.put(ax + dx, ty, AXLE_Z + dz, dx == 0 && dz == 0 ? StormKit.COPPER_BLOCK : StormKit.COPPER);
            }
        }
        if (vane) {
            for (int y = ty + 1; y <= ty + 5; y++) {
                kit.put(ax, y, AXLE_Z, StormKit.COPPER_BLOCK);
            }
            // 風見: 北 (z 小) を指す矢。柄が z 方向、矢じりが北、尾羽が南
            int vy = ty + 6;
            for (int z = AXLE_Z - 7; z <= AXLE_Z + 6; z++) {
                kit.put(ax, vy, z, z < AXLE_Z - 4 ? StormKit.COPPER : StormKit.FEATHER_WHITE);
            }
            // 矢じりの返し (斜めに開く。面でつながるよう、斜めの一段ごとに隣も置く)
            for (int k = 1; k <= 3; k++) {
                for (int sx : new int[] {-1, 1}) {
                    kit.put(ax + sx * k, vy, AXLE_Z - 7 + k, StormKit.COPPER);
                    kit.put(ax + sx * (k - 1), vy, AXLE_Z - 7 + k, StormKit.COPPER);
                }
            }
            // 尾羽 (後ろへ開く白い羽。上にもう一段)
            for (int k = 1; k <= 3; k++) {
                for (int sx : new int[] {-1, 1}) {
                    kit.put(ax + sx * k, vy, AXLE_Z + 6 - k, StormKit.FEATHER_WHITE);
                    kit.put(ax + sx * (k - 1), vy, AXLE_Z + 6 - k, StormKit.FEATHER_WHITE);
                    kit.put(ax + sx * k, vy + 1, AXLE_Z + 6 - k, StormKit.CALCITE);
                    kit.put(ax + sx * (k - 1), vy + 1, AXLE_Z + 6 - k, StormKit.CALCITE);
                }
            }
            kit.put(ax, vy + 1, AXLE_Z - 7, StormKit.LAMP);
            // 宝箱
            BlockPos c = CACHE_VANE_TOP;
            chest(kit, c, t.dir() > 0 ? Direction.EAST : Direction.WEST);
        } else {
            BlockPos base = towerRods().get(towerRods().size() - 1);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.put(base.getX() + dx, ty, base.getZ() + dz, StormKit.COPPER);
                }
            }
            StormBirdBuilder.rod(kit, base, 0);
            for (int y = ty + 1; y <= ty + 6; y++) {
                kit.put(ax, y, AXLE_Z, Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
            }
            kit.put(ax, ty + 7, AXLE_Z, StormKit.LAMP);
            chest(kit, CACHE_ROD_TOP, t.dir() > 0 ? Direction.EAST : Direction.WEST);
        }
        // 頂の甲板の灯りの柱 (四隅)
        for (int x : new int[] {t.xMin() + 1, t.xMax() - 1}) {
            StormDeco.lampPost(kit, x, ty, 13);
            StormDeco.lampPost(kit, x, ty, 33);
        }
        StormDeco.bench(kit, t.x(4), t.x(4), 18, 20, ty + 1, t.dir() > 0 ? Direction.EAST : Direction.WEST);
        StormDeco.bench(kit, t.x(4), t.x(4), 26, 28, ty + 1, t.dir() > 0 ? Direction.EAST : Direction.WEST);
    }

    private static void chest(StormKit kit, BlockPos c, Direction facing) {
        kit.put(c.getX(), c.getY(), c.getZ(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing));
        kit.put(c.getX(), c.getY() - 1, c.getZ(), StormKit.COPPER);
        kit.put(c.getX(), c.getY(), c.getZ() - 1, StormKit.LAMP);
        kit.put(c.getX(), c.getY(), c.getZ() + 1, StormKit.LAMP);
    }

    // ---------------------------------------------------------------- 翼の外の廊 (side -1 西、+1 東)
    private static void outer(StormKit kit, int side) {
        int[] xs = outerX(side);
        int fy = DECK_Y;
        Style st = Style.FEATHER;
        for (int x = xs[0]; x <= xs[1]; x++) {
            for (int z = OUT_Z1 - 1; z <= OUT_Z2 + 1; z++) {
                boolean in = z >= OUT_Z1 && z <= OUT_Z2;
                if (in) {
                    support(kit, x, z, fy, st);
                    kit.put(x, fy, z, outerFloor(x, z, side));
                    for (int y = fy + 1; y <= fy + 8; y++) {
                        kit.put(x, y, z, AIR);
                    }
                } else {
                    rail(kit, x, z, fy, st);
                }
            }
        }
        // 先へ向かう矢じるし (真ん中の列。12 おき)
        int mid = (OUT_Z1 + OUT_Z2) / 2;
        for (int k = 1; k <= 11; k++) {
            int x = side * (OUT_X1 + 12 * k);
            if (Math.abs(x) < OUT_X2 - 3) {
                StormDeco.chevron(kit, x, mid, side < 0 ? Direction.WEST : Direction.EAST, StormKit.COPPER, zz -> fy);
            }
        }
        // 灯りの柱 (8 おき、北と南の縁)
        for (int k = 0; ; k++) {
            int x = side * (OUT_X1 + 4 + 8 * k);
            if (Math.abs(x) > OUT_X2) {
                break;
            }
            StormDeco.lampPost(kit, x, fy, k % 2 == 0 ? OUT_Z1 - 1 : OUT_Z2 + 1);
        }
        // 風の門 (28 おき): 両縁の 2x2 の柱と、渡す梁。道のリズムと、遠くから見える輪郭
        for (int k = 1; k <= 4; k++) {
            gate(kit, side * (OUT_X1 + 28 * k) - (side > 0 ? 0 : 1), fy, side);
        }
        if (side < 0) {
            // 吹き流し (風下 = 東へ流れる旗。横風の向きが見える)
            for (int k = 0; k <= 8; k++) {
                int x = -(OUT_X1 + 7 + 14 * k);
                int z = (k & 1) == 0 ? OUT_Z1 - 1 : OUT_Z2 + 1;
                for (int y = fy + 1; y <= fy + 8; y++) {
                    kit.put(x, y, z, y % 3 == 0 ? StormKit.CALCITE : StormKit.COPPER_BLOCK);
                }
                for (int d = 1; d <= 4; d++) {
                    kit.put(x + d, fy + 8, z, d % 2 == 0 ? StormKit.FEATHER_WHITE : StormKit.COPPER);
                    kit.put(x + d, fy + 7, z, d % 2 == 0 ? StormKit.COPPER : StormKit.FEATHER_WHITE);
                    if (d <= 2) {
                        kit.put(x + d, fy + 6, z, StormKit.FEATHER_WHITE);
                    }
                }
                kit.put(x, fy + 9, z, StormKit.LAMP);
            }
            for (int[] p : windPiers()) {
                pier(kit, p[0], p[1], fy);
            }
        } else {
            // 碍子の柱: 銅の柱に先の細い針。放電の線の道しるべ (柱ではない。雷は落ちない)
            for (BlockPos r : outerPylons()) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        kit.put(r.getX() + dx, fy, r.getZ() + dz, StormKit.COPPER);
                    }
                }
                for (int y = fy + 1; y <= fy + 4; y++) {
                    kit.put(r.getX(), y, r.getZ(), y % 2 == 0 ? Blocks.WAXED_CHISELED_COPPER.defaultBlockState() : StormKit.COPPER_BLOCK);
                }
                kit.put(r.getX(), fy + 5, r.getZ(), Blocks.LIGHTNING_ROD.defaultBlockState());
                kit.put(r.getX() + 1, fy + 1, r.getZ(), StormKit.LAMP);
                kit.put(r.getX() - 1, fy + 1, r.getZ(), StormKit.LAMP);
            }
        }
    }

    // 門: x, x+1 の 2 列。柱は z OUT_Z1〜+1 と OUT_Z2-1〜OUT_Z2、梁は高さ 10
    private static void gate(StormKit kit, int x, int fy, int side) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int z : new int[] {OUT_Z1, OUT_Z1 + 1, OUT_Z2 - 1, OUT_Z2}) {
                for (int y = fy + 1; y <= fy + 9; y++) {
                    kit.put(x + dx, y, z, y % 4 == 0 ? StormKit.COPPER : y == fy + 9 ? StormKit.CALCITE : StormKit.FEATHER_WHITE);
                }
            }
            for (int z = OUT_Z1; z <= OUT_Z2; z++) {
                kit.put(x + dx, fy + 10, z, z % 6 == 0 ? StormKit.COPPER_BLOCK : StormKit.COPPER);
            }
            // 梁から下がる羽根の房 (縁に近いほど長い。真ん中はあける)
            int mid = (OUT_Z1 + OUT_Z2) / 2;
            for (int z = OUT_Z1 + 3; z <= OUT_Z2 - 3; z++) {
                int hang = Math.abs(z - mid) >= 5 ? 2 : Math.abs(z - mid) >= 3 ? 1 : 0;
                for (int h = 1; h <= hang; h++) {
                    kit.put(x + dx, fy + 10 - h, z, h == 2 ? StormKit.CALCITE : StormKit.FEATHER_WHITE);
                }
            }
        }
        kit.put(x, fy + 9, (OUT_Z1 + OUT_Z2) / 2, side < 0 ? StormKit.LAMP : Blocks.OCHRE_FROGLIGHT.defaultBlockState());
    }

    private static BlockState outerFloor(int x, int z, int side) {
        boolean border = z == OUT_Z1 || z == OUT_Z2;
        if (border) {
            return StormKit.COPPER;
        }
        // 中央の導きの線 (真ん中の 1 列は白と灰、風の外廊は水色、雷の外廊は金の縞が 6 おき)
        int mid = (OUT_Z1 + OUT_Z2) / 2;
        if (z == mid) {
            if (Math.abs(x) % 6 == 0) {
                return side < 0 ? StormDeco.WEST_LINE : StormDeco.EAST_LINE;
            }
            return StormKit.FEATHER_WHITE;
        }
        return StormKit.floor(Style.FEATHER, x, z);
    }

    // 風よけの柱: 風上 (西) 側が尖った、幅 3・厚さ 2・高さ 5 の骨の柱
    private static void pier(StormKit kit, int x, int zc, int fy) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int y = fy + 1; y <= fy + 4; y++) {
                    kit.put(x + dx, y, zc + dz, y % 4 == 0 ? StormKit.CALCITE : StormKit.BONE);
                }
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            kit.put(x + dx, fy + 5, zc, StormKit.CALCITE);
        }
        kit.put(x, fy + 6, zc, StormKit.LAMP);
        // 根元の青い目印 (風の外廊の色)
        for (int dz = -2; dz <= 2; dz++) {
            kit.put(x - 1, fy, zc + dz, StormDeco.WEST_LINE);
        }
    }

    // ---------------------------------------------------------------- 翼の先の見張り台
    private static void tip(StormKit kit, int side) {
        int[] xs = tipX(side);
        int fy = DECK_Y;
        int mid = (OUT_Z1 + OUT_Z2) / 2;
        for (int x = xs[0] - 1; x <= xs[1] + 1; x++) {
            for (int z = OUT_Z1 - 1; z <= OUT_Z2 + 1; z++) {
                boolean in = x >= xs[0] && x <= xs[1] && z >= OUT_Z1 && z <= OUT_Z2;
                if (in) {
                    support(kit, x, z, fy, Style.FEATHER);
                    int cx = Math.abs(x) - (TIP_X1 + TIP_X2) / 2;
                    int r = Math.max(Math.abs(cx), Math.abs(z - mid));
                    boolean border = x == xs[0] || x == xs[1] || z == OUT_Z1 || z == OUT_Z2;
                    // 風見の円盤 (銅と白の同心の四角)
                    BlockState s = border ? StormKit.COPPER : r <= 6 && (r == 6 || r == 3) ? StormKit.COPPER : r <= 6 && r % 2 == 0 ? StormKit.FEATHER_WHITE
                            : StormKit.floor(Style.FEATHER, x, z);
                    kit.put(x, fy, z, s);
                    for (int y = fy + 1; y <= fy + 8; y++) {
                        kit.put(x, y, z, AIR);
                    }
                } else if (!(Math.abs(x) == Math.abs(side * (TIP_X1 - 1)) && z >= OUT_Z1 && z <= OUT_Z2)) {
                    rail(kit, x, z, fy, Style.FEATHER);
                }
            }
        }
        // 四隅と中ほどの灯りの柱、宝箱、腰掛け、休み場
        for (int x : new int[] {xs[0] + 1, xs[1] - 1}) {
            StormDeco.lampPost(kit, x, fy, OUT_Z1 + 1);
            StormDeco.lampPost(kit, x, fy, OUT_Z2 - 1);
        }
        chest(kit, side < 0 ? CACHE_TIP_W : CACHE_TIP_E, side < 0 ? Direction.EAST : Direction.WEST);
        int bx = side * 192;
        StormDeco.bench(kit, bx - 1, bx + 1, 16, 16, fy + 1, Direction.SOUTH);
        StormDeco.featherPillar(kit, side * 190, 17, fy, 6);
        StormDeco.featherPillar(kit, side * 190, 29, fy, 6);
        // 手前の入口の灯りの柱
        StormDeco.lampPost(kit, side * (TIP_X1 - 2), fy, OUT_Z1 - 1);
        StormDeco.lampPost(kit, side * (TIP_X1 - 2), fy, OUT_Z2 + 1);
    }

    // ---------------------------------------------------------------- 尾羽の庭
    private static void garden(StormKit kit, int side) {
        int fy = GARDEN_Y;
        Area g = StormExtra.garden(side);
        // 庭の床と、外側の縁の手すり (道に面した側は道の手すりがそのまま。口だけあける)
        for (int x = g.x1() - 1; x <= g.x2() + 1; x++) {
            for (int z = g.z1() - 1; z <= g.z2() + 1; z++) {
                boolean in = x >= g.x1() && x <= g.x2() && z >= g.z1() && z <= g.z2();
                if (in) {
                    support(kit, x, z, fy, Style.FEATHER);
                    kit.put(x, fy, z, gardenFloor(x, z, side));
                    for (int y = fy + 1; y <= fy + 8; y++) {
                        kit.put(x, y, z, AIR);
                    }
                } else if (Math.abs(x) == GARDEN_X1 - 1) {
                    continue;
                } else {
                    rail(kit, x, z, fy, Style.FEATHER);
                }
            }
        }
        // 道との口 (道の手すり・灯りの柱を除き、庭の床の高さにつなぐ)
        for (int z = GARDEN_Z1 + 4; z <= GARDEN_Z2 - 4; z++) {
            boolean mouth = (z >= 126 && z <= 134) || (z >= 150 && z <= 158);
            if (!mouth) {
                continue;
            }
            int x = side * (GARDEN_X1 - 1);
            support(kit, x, z, fy, Style.FEATHER);
            kit.put(x, fy, z, z == 130 || z == 154 ? StormKit.COPPER : StormKit.floor(Style.FEATHER, x, z));
            for (int y = fy + 1; y <= fy + 4; y++) {
                kit.put(x, y, z, AIR);
            }
        }
        // 口の両端の灯りの柱
        for (int z : new int[] {125, 135, 149, 159}) {
            StormDeco.lampPost(kit, side * (GARDEN_X1 - 1), fy, z);
        }
        // 縁の灯りの柱 (8 おき)
        for (int z = GARDEN_Z1 + 2; z <= GARDEN_Z2 - 2; z += 8) {
            StormDeco.lampPost(kit, side * (GARDEN_X2 + 1), fy, z);
        }
        StormDeco.featherPillar(kit, side * 10, 140, fy, 6);
        if (side < 0) {
            windGarden(kit);
        } else {
            stormGarden(kit);
        }
        // 口から庭の中心への色の線
        StormDeco.strip(kit, fy, side < 0 ? StormDeco.WEST_LINE : StormDeco.EAST_LINE, side < 0 ? StormDeco.WEST_PIP : StormDeco.EAST_PIP, 4, 0,
                new int[] {side * GARDEN_X1, 130}, new int[] {side * 20, 130}, new int[] {side * 20, 140});
    }

    private static BlockState gardenFloor(int x, int z, int side) {
        boolean border = Math.abs(x) == GARDEN_X2 || z == GARDEN_Z1 || z == GARDEN_Z2;
        if (border) {
            return StormKit.COPPER;
        }
        // 敷石の格子 (8 おきの白い線)
        if (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) {
            return StormKit.FEATHER_WHITE;
        }
        return StormKit.floor(Style.FEATHER, x, z);
    }

    // 西の庭: 風の見本。昇り風 (凪 4 = 鐘の台、嵐 13 = 見張りの台)
    private static void windGarden(StormKit kit) {
        BlockPos v = GARDEN_VENT;
        StormBirdBuilder.vent(kit, v);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    kit.put(v.getX() + dx, v.getY(), v.getZ() + dz, (dx + dz) % 2 == 0 ? StormKit.LAMP : StormKit.COPPER);
                }
            }
        }
        // 鐘の台 (3x3 の柱。頂 = 床 + 4。鐘は東の端)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = GARDEN_Y + 1; y <= GARDEN_Y + 4; y++) {
                    kit.put(-13 + dx, y, 143 + dz, y % 2 == 0 ? StormKit.CALCITE : StormKit.FEATHER_WHITE);
                }
                kit.put(-13 + dx, GARDEN_Y + 4, 143 + dz, StormKit.COPPER);
            }
        }
        kit.put(BELL.getX(), BELL.getY(), BELL.getZ(), Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
        // 吹き流し (庭の北の端に細い柱と、風上の側を指す羽の旗)
        for (int y = GARDEN_Y + 1; y <= GARDEN_Y + 9; y++) {
            kit.put(-28, y, 127, y == GARDEN_Y + 9 ? StormKit.LAMP : StormKit.COPPER_BLOCK);
        }
        for (int k = 1; k <= 4; k++) {
            kit.put(-28 + k, GARDEN_Y + 8, 127, k % 2 == 0 ? StormKit.FEATHER_WHITE : StormKit.COPPER);
        }
    }

    // 東の庭: 雷の見本。三本の柱の三角と、腰掛け。見本の輪はここにいる人の足もとへ (傷なし)
    private static void stormGarden(StormKit kit) {
        for (BlockPos r : gardenRods()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.put(r.getX() + dx, GARDEN_Y, r.getZ() + dz, StormKit.COPPER);
                }
            }
            StormBirdBuilder.rod(kit, r, 0);
            kit.put(r.getX() + 2, GARDEN_Y + 1, r.getZ(), StormKit.LAMP);
        }
        // 柱を結ぶ銅の導線 (床の線)
        List<BlockPos> rs = gardenRods();
        for (int k = 0; k < rs.size(); k++) {
            BlockPos a = rs.get(k);
            BlockPos b = rs.get((k + 1) % rs.size());
            StormDeco.strip(kit, GARDEN_Y, StormKit.COPPER, StormDeco.EAST_PIP, 5, 0, new int[] {a.getX(), a.getZ()}, new int[] {b.getX(), a.getZ()},
                    new int[] {b.getX(), b.getZ()});
        }
        // 柱のない所に見本の標 (輪が落ちる円の中心の印。柱から離れた所に二つ)
        for (int[] p : new int[][] {{12, 146}, {28, 146}}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    kit.put(p[0] + dx, GARDEN_Y, p[1] + dz, Math.abs(dx) + Math.abs(dz) == 0 ? StormKit.LAMP : Blocks.RED_CONCRETE.defaultBlockState());
                }
            }
        }
        StormDeco.bench(kit, 18, 22, 160, 160, GARDEN_Y + 1, Direction.NORTH);
    }

    // 西の庭の見張りの台 (床 GARDEN_Y + 13)。階段 (凪) は北西から。昇り風 (嵐) は東の柱から
    private static void lookout(StormKit kit) {
        Area a = LOOKOUT;
        int fy = a.floor();
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                boolean border = x == a.x1() || x == a.x2() || z == a.z1() || z == a.z2();
                kit.put(x, fy, z, border ? StormKit.COPPER : StormKit.floor(Style.FEATHER, x, z));
                kit.put(x, fy - 1, z, StormKit.COPPER);
                for (int y = fy + 1; y <= fy + 6; y++) {
                    kit.put(x, y, z, AIR);
                }
            }
        }
        // 柱 (四隅の 2x2 を床まで)
        for (int cx : new int[] {a.x1(), a.x2() - 1}) {
            for (int cz : new int[] {a.z1(), a.z2() - 1}) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        for (int y = GARDEN_Y + 1; y < fy - 1; y++) {
                            kit.put(cx + dx, y, cz + dz, y % 4 == 0 ? StormKit.COPPER : StormKit.CALCITE);
                        }
                    }
                }
            }
        }
        // 手すり (階段の口 = 北の縁の x -23〜-21 と、昇り風の側 = 東の縁の z 142〜144 を除く)
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z : new int[] {a.z1(), a.z2()}) {
                boolean stairMouth = z == a.z1() && x >= -26 && x <= -22;
                if (!stairMouth) {
                    kit.put(x, fy + 1, z, RAIL);
                }
            }
        }
        for (int z = a.z1() + 1; z < a.z2(); z++) {
            kit.put(a.x1(), fy + 1, z, RAIL);
            boolean ventMouth = z >= 141 && z <= 145;
            if (!ventMouth) {
                kit.put(a.x2(), fy + 1, z, RAIL);
            }
        }
        // 階段 (幅 3、z 134〜136、x -34 から東へ。13 段。台の北の縁に着く)
        for (int j = 0; j < 13; j++) {
            int x = LOOKOUT_STAIR_X1 + j;
            for (int z = LOOKOUT_STAIR_Z1; z < LOOKOUT_STAIR_Z1 + 3; z++) {
                int y = GARDEN_Y + 1 + j;
                kit.fill(x, GARDEN_Y + 1, z, x, y, z, wall(Style.FEATHER, x, y, z));
                kit.put(x, y, z, StormKit.floor(Style.FEATHER, x, z));
                for (int yy = y + 1; yy <= y + 4; yy++) {
                    kit.put(x, yy, z, AIR);
                }
            }
        }
        // 階段の北の縁: 階段と同じ高さまで石を積み、上に低い壁 (宙に浮かない)。ところどころ灯り
        for (int j = 0; j < 13; j++) {
            int x = LOOKOUT_STAIR_X1 + j;
            int y = GARDEN_Y + 1 + j;
            int z = LOOKOUT_STAIR_Z1 - 1;
            for (int yy = GARDEN_Y + 1; yy <= y; yy++) {
                kit.put(x, yy, z, yy == y && j % 4 == 2 ? StormKit.LAMP : wall(Style.FEATHER, x, yy, z));
            }
            kit.put(x, y + 1, z, RAIL);
        }
        chest(kit, LOOKOUT_CACHE, Direction.EAST);
        StormDeco.lampPost(kit, a.x1() + 1, fy, a.z1() + 1);
        StormDeco.lampPost(kit, a.x1() + 1, fy, a.z2() - 1);
    }
}
