package jp.revati.skyabyss.titan.ember.back;

import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;

// 余燼の背の「区」ごとの飾りと材質 (版 20。26 の 9 章)。体の中が一つの石の洞窟に見えないように、区ごとに材質・形・灯りの言葉を変える:
//   喉の首 = 肉と骨の壁、尾根の広間 = 玄武の鱗の尾根 (段になった黒い岩の背と骨の肋)、坂 = 灯りの道、
//   炉心の広間 = 火口の真下の円形の大聖堂 (三つの扉に色の柱: 西 灰・青白、東 橙、南 赤)、
//   冷えた溶岩洞 = 黒い玄武岩の丸い洞 (熔脈 = マグマの筋、床の灯りの列)、背へ出る洞 = 同じ洞、井戸 = 壁の灯り。
// 歩く道は、床の灯りの列 (中央) と壁の灯り (5〜6 ブロックおき) で光度 4 以上にする (BuildAudit LIGHT_DARK)。
// 灰色の羊毛は灰溜まり (熱が冷える目印) だけに使う。ここでは使わない。
final class EmberZones {
    // 広間の灰溜まり (中心からの相対。3 × 2)
    static final int[][] HUB_ASH = {{-20, -16}, {18, -16}, {-20, 14}, {18, 14}};

    private EmberZones() {
    }

    static int hash(int x, int y, int z) {
        return Math.floorMod(EmberKit.hash(x, y, z), 1000);
    }

    // ---------------------------------------------------------------- 広間の床
    static BlockState hubFloor(int x, int z, double d) {
        int rx = x - HUB_CENTER.getX();
        int rz = z - HUB_CENTER.getZ();
        if (d < 3.6) {
            return EmberKit.ruinFloor(x, z);
        }
        // 三つの扉への色の道 (幅 3。中心の列に灯り)
        if (d >= 6 && d < 26) {
            boolean lamp = Math.floorMod((int) Math.round(d), 4) == 0;
            if (Math.abs(rz) <= 1 && rx < 0) {
                return Math.abs(rz) == 0 && lamp ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState();
            }
            if (Math.abs(rz) <= 1 && rx > 0) {
                return Math.abs(rz) == 0 && lamp ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            }
            if (Math.abs(rx) <= 1 && rz > 0) {
                return Math.abs(rx) == 0 && lamp ? redLamp() : Blocks.RED_NETHER_BRICKS.defaultBlockState();
            }
            if (Math.abs(rx) <= 1 && rz < 0) {
                return Math.abs(rx) == 0 && lamp ? EmberKit.EMBERLIGHT : EmberKit.RUIN_CHISELED;
            }
        }
        if (d >= 14 && d < 15) {
            return EmberKit.TUFF_BRICKS;
        }
        if (d >= 24.5) {
            return EmberKit.RUIN_DARK;
        }
        // 鱗の模様 (磨いた玄武岩がまだらに)
        if (d >= 15 && hash(x, 3, z) < 160) {
            return EmberKit.BASALT;
        }
        return EmberKit.ruinFloor(x, z);
    }

    static BlockState redLamp() {
        return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
    }

    // ---------------------------------------------------------------- 洞の床・壁
    static BlockState tubeFloor(int x, int z, int lateral) {
        if (Math.abs(lateral) >= TUBE_HALF) {
            return EmberKit.RUIN_DARK;
        }
        if (lateral == 0 && Math.floorMod(x + z, 5) == 0) {
            return EmberKit.EMBERLIGHT;
        }
        return Math.abs(lateral) <= 1 ? EmberKit.ruinFloor(x, z) : EmberKit.BASALT;
    }

    // 黒い玄武岩の洞の壁: 玄武岩・黒石・磨いた玄武岩、ところどころマグマの筋 (熔脈) と光る茂み
    static BlockState tubeWall(int x, int y, int z) {
        int h = hash(x, y, z);
        if (h < 450) {
            return Blocks.BASALT.defaultBlockState();
        }
        if (h < 700) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        if (h < 820) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (h < 920) {
            return Blocks.POLISHED_BASALT.defaultBlockState();
        }
        if (h < 980) {
            return EmberKit.VEIN;
        }
        return EmberKit.GLOW;
    }

    // ---------------------------------------------------------------- 飾り (担当の中身より後。休み場より前)
    static void dress(EmberKit kit) {
        mouth(kit);
        neck(kit);
        ridgeHall(kit);
        ramp(kit);
        bays(kit);
        hub(kit);
        tubeLamps(kit);
        well(kit);
        backTube(kit);
    }

    private static boolean solid(EmberKit kit, int x, int y, int z) {
        BlockState s = kit.get(x, y, z);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    private static void lampIfSolid(EmberKit kit, int x, int y, int z) {
        if (solid(kit, x, y, z)) {
            kit.set(x, y, z, EmberKit.GLOW);
        }
    }

    // 口: 舌の帯の縁と、見送りの石のそばに灯りの柱
    private static void mouth(EmberKit kit) {
        for (int z : new int[] {-262, -252, -242}) {
            for (int sx : new int[] {-9, 9}) {
                for (int y = MOUTH_FLOOR + 1; y <= MOUTH_FLOOR + 3; y++) {
                    kit.set(sx, y, z, EmberKit.BASALT);
                }
                kit.set(sx, MOUTH_FLOOR + 4, z, EmberKit.GLOW);
            }
        }
        for (int sx : new int[] {-3, 3}) {
            for (int y = PLAIN_TOP + 1; y <= PLAIN_TOP + 2; y++) {
                kit.set(sx, y, RETURN_STONE.minZ(), EmberKit.BASALT);
            }
            kit.set(sx, PLAIN_TOP + 3, RETURN_STONE.minZ(), EmberKit.GLOW);
        }
    }

    // 首 (口の奥の幅 9 の肉の喉): 壁に灯り、床の中央に灯りの列
    private static void neck(EmberKit kit) {
        for (int z = EmberBodyBuilder.NECK_Z1; z < EmberBodyBuilder.HALL_Z1; z++) {
            int f = EmberBodyBuilder.throatFloor(z);
            if (Math.floorMod(z, 6) == 0) {
                lampIfSolid(kit, -(EmberBodyBuilder.NECK_HALF + 1), f + 3, z);
                lampIfSolid(kit, EmberBodyBuilder.NECK_HALF + 1, f + 3, z + 3);
                kit.set(0, f, z, EmberKit.EMBERLIGHT);
            }
        }
    }

    // 尾根の広間 (玄武の鱗の尾根): 道は幅 9 (x -4〜4)、その両側に黒い岩の段が壁へ向かって高くなる (縁石 1 → 壁ぎわ 4〜5)。
    // 8 ブロックおきに骨の肋が天井を渡り、壁ぎわの肋の柱から段まで下りる。縁石に灯り。見張り台の張り出し (BAY_*) の口は段を切って開ける
    static int ridgeHeight(int ax, int x, int z) {
        if (ax < 5) {
            return 0;
        }
        int h = ax <= 6 ? 1 : ax <= 8 ? 2 : ax <= 10 ? 3 : 4;
        if (ax >= 7 && hash(x, 5, z) % 5 == 0) {
            h++;
        }
        return h;
    }

    static BlockState ridgeBlock(int x, int y, int z) {
        int h = hash(x, y, z) % 10;
        return h < 4 ? Blocks.BASALT.defaultBlockState() : h < 6 ? Blocks.POLISHED_BASALT.defaultBlockState()
                : h < 8 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    private static void ridgeHall(EmberKit kit) {
        for (int z = EmberBodyBuilder.HALL_Z1; z <= EmberBodyBuilder.HALL_Z2; z++) {
            int f = EmberBodyBuilder.throatFloor(z);
            boolean riser = f > EmberBodyBuilder.throatFloor(z - 1);
            boolean bay = z >= BAY_WEST.minZ() && z <= BAY_WEST.maxZ();
            int rib = Math.floorMod(z + 207, 8);
            for (int x = -EmberBodyBuilder.HALL_HALF; x <= EmberBodyBuilder.HALL_HALF; x++) {
                int ax = Math.abs(x);
                int top = f + EmberBodyBuilder.throatHeight(z, x);
                if (!riser && ax >= 5) {
                    int h = ridgeHeight(ax, x, z);
                    if (bay && ax >= 9) {
                        h = 0;
                    }
                    for (int y = f + 1; y <= f + h; y++) {
                        kit.set(x, y, z, ridgeBlock(x, y, z));
                    }
                }
                if (rib == 4) {
                    kit.set(x, top, z, ax == 0 || ax == 6 || ax == 12 ? EmberKit.GLOW : EmberKit.BONE);
                }
            }
            // 肋の柱 (壁ぎわ。見張り台の口は除く)
            if (rib == 4 && !bay) {
                for (int sx : new int[] {-11, 11}) {
                    int base = f + ridgeHeight(11, sx, z) + 1;
                    for (int y = base; y < f + EmberBodyBuilder.throatHeight(z, sx); y++) {
                        kit.set(sx, y, z, EmberKit.BONE);
                    }
                }
            }
            if (!riser && Math.floorMod(z + 207, 6) == 0) {
                // 縁石の灯りと、中央の灯りの列
                for (int sx : new int[] {-5, 5}) {
                    kit.set(sx, f + 1, z, EmberKit.GLOW);
                }
                kit.set(0, f, z, EmberKit.EMBERLIGHT);
            }
        }
    }

    // 坂 (幅 7): 壁の灯りと、中央の灯りの列。尾根の広間から入る所に骨の門柱
    private static void ramp(EmberKit kit) {
        int f0 = EmberBodyBuilder.throatFloor(EmberBodyBuilder.RAMP_Z1);
        for (int sx : new int[] {-4, 4}) {
            for (int y = f0; y <= f0 + 7; y++) {
                kit.set(sx, y, EmberBodyBuilder.RAMP_Z1, y == f0 + 7 ? EmberKit.GLOW : EmberKit.BONE);
            }
        }
        for (int z = EmberBodyBuilder.RAMP_Z1 + 2; z < HUB_DOOR_NORTH.getZ() - 1; z++) {
            int f = EmberBodyBuilder.throatFloor(z);
            boolean riser = f > EmberBodyBuilder.throatFloor(z - 1);
            if (Math.floorMod(z, 5) == 0) {
                lampIfSolid(kit, -(EmberBodyBuilder.RAMP_HALF + 1), f + 3, z);
                lampIfSolid(kit, EmberBodyBuilder.RAMP_HALF + 1, f + 3, z + 2);
            }
            if (!riser && Math.floorMod(z, 6) == 0) {
                kit.set(0, f, z, EmberKit.EMBERLIGHT);
            }
        }
    }

    // 見張り台 (尾根の祠): 角に骨の柱、奥の壁と天井に灯り
    private static void bays(EmberKit kit) {
        for (int side : new int[] {-1, 1}) {
            Box b = side < 0 ? BAY_WEST : BAY_EAST;
            int far = side < 0 ? b.minX() : b.maxX();
            for (int z : new int[] {b.minZ(), b.maxZ()}) {
                for (int y = BAY_FLOOR + 1; y <= BAY_FLOOR + 6; y++) {
                    kit.set(far, y, z, y == BAY_FLOOR + 6 ? EmberKit.GLOW : EmberKit.BONE);
                }
            }
            lampIfSolid(kit, far + side, BAY_FLOOR + 5, (b.minZ() + b.maxZ()) / 2);
            kit.set(far - side * 4, BAY_FLOOR + 8, (b.minZ() + b.maxZ()) / 2, EmberKit.GLOW);
            // 床の中央に灯りの列 (広間から台へ)
            for (int x = Math.min(side * 13, side * 21); x <= Math.max(side * 13, side * 21); x += 2) {
                kit.set(x, BAY_FLOOR, (b.minZ() + b.maxZ()) / 2, EmberKit.EMBERLIGHT);
            }
        }
    }

    // 炉心の広間: 三つの扉に色の柱 (西 灰・青白、東 橙、南 赤。壁の側から天井まで)、灯りの柱 8 本、天井の肋と灯り
    private static void hub(EmberKit kit) {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        int fy = HUB_CENTER.getY();
        record Pylon(int x, int z, BlockState body, BlockState cap) {
        }
        Pylon[] pylons = {
                new Pylon(cx - 27, cz - 4, Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState()),
                new Pylon(cx - 27, cz + 4, Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState()),
                new Pylon(cx + 27, cz - 4, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState()),
                new Pylon(cx + 27, cz + 4, Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState()),
                new Pylon(cx - 4, cz + 27, Blocks.RED_TERRACOTTA.defaultBlockState(), redLamp()),
                new Pylon(cx + 4, cz + 27, Blocks.RED_TERRACOTTA.defaultBlockState(), redLamp())};
        for (Pylon p : pylons) {
            int top = EmberBodyBuilder.hubCeiling(EmberBodyBuilder.hubDist(p.x(), p.z())) - 1;
            for (int y = fy + 1; y <= top; y++) {
                kit.set(p.x(), y, p.z(), y == top || y == fy + 7 ? p.cap() : p.body());
            }
        }
        // 灯りの柱 (扉の角度を避けた 8 本)
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int px = cx + (int) Math.round(21 * Math.cos(a));
            int pz = cz + (int) Math.round(21 * Math.sin(a));
            for (int y = fy + 1; y <= fy + 4; y++) {
                kit.set(px, y, pz, EmberKit.BASALT);
            }
            kit.set(px, fy + 5, pz, EmberKit.GLOW);
        }
        // 天井の肋 (12 本。中心の口の外から壁まで放射状。3 ブロックおきに灯り)
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            int lastX = Integer.MIN_VALUE;
            int lastZ = Integer.MIN_VALUE;
            for (double r = 6; r <= 26; r += 0.5) {
                int x = cx + (int) Math.round(r * Math.cos(a));
                int z = cz + (int) Math.round(r * Math.sin(a));
                if (x == lastX && z == lastZ) {
                    continue;
                }
                lastX = x;
                lastZ = z;
                int y = EmberBodyBuilder.hubCeiling(EmberBodyBuilder.hubDist(x, z));
                kit.set(x, y, z, Math.floorMod((int) Math.round(r), 4) == 0 ? EmberKit.GLOW : EmberKit.BONE);
            }
        }
    }

    // 冷えた溶岩洞の壁の灯り (5 ブロックおき、左右交互)
    private static void tubeLamps(EmberKit kit) {
        for (int x = TUBE_WEST_X - TUBE_HALF + 1; x <= HUB_DOOR_WEST.getX() - 2; x += 5) {
            lampIfSolid(kit, x, FLOOR_HUB + 3, TUBE_Z - TUBE_HALF - 1 + (Math.floorMod(x, 10) == 0 ? 2 * TUBE_HALF + 2 : 0));
        }
        for (int x = HUB_DOOR_EAST.getX() + 2; x <= TUBE_EAST_X + TUBE_HALF - 1; x += 5) {
            lampIfSolid(kit, x, FLOOR_HUB + 3, TUBE_Z - TUBE_HALF - 1 + (Math.floorMod(x, 10) == 0 ? 2 * TUBE_HALF + 2 : 0));
        }
        for (int z = TUBE_Z + TUBE_HALF + 1; z < WEST_DOOR.minZ(); z += 5) {
            int side = Math.floorMod(z, 10) == 0 ? 1 : -1;
            lampIfSolid(kit, TUBE_WEST_X + side * (TUBE_HALF + 1), EmberBodyBuilder.legWestFloor(z) + 3, z);
            lampIfSolid(kit, TUBE_EAST_X - side * (TUBE_HALF + 1), EmberBodyBuilder.legEastFloor(z) + 3, z);
        }
        for (int z = HUB_DOOR_SOUTH.getZ() + 1; z < SOUTH_DOOR.minZ(); z += 4) {
            int side = Math.floorMod(z, 8) == 0 ? 1 : -1;
            lampIfSolid(kit, side * (TUBE_HALF + 1), EmberBodyBuilder.legSouthFloor(z) + 3, z);
        }
    }

    // 火の井戸の壁の灯り (螺旋の外の壁に、らせん状に 4 段おき。火口の床の下まで)
    private static void well(EmberKit kit) {
        int cx = HUB_CENTER.getX();
        int cz = HUB_CENTER.getZ();
        int crater = EmberBodyBuilder.craterFloorOf();
        for (int y = WELL_BOTTOM + 3; y <= crater - 4; y += 4) {
            double a = y * 0.9;
            int x = cx + (int) Math.round((WELL_RADIUS + 0.9) * Math.cos(a));
            int z = cz + (int) Math.round((WELL_RADIUS + 0.9) * Math.sin(a));
            lampIfSolid(kit, x, y, z);
        }
    }

    // 背へ出る洞: 壁の灯り (6 ブロックおき)、小部屋の灯り
    private static void backTube(EmberKit kit) {
        int end = EmberBodyBuilder.backTubeEnd();
        for (int z = HUB_CENTER.getZ() + 8; z <= end - 4; z += 6) {
            lampIfSolid(kit, -(TUBE_HALF + 1), BACK_EXIT_FLOOR + 3, z);
            lampIfSolid(kit, TUBE_HALF + 1, BACK_EXIT_FLOOR + 3, z + 3);
        }
        lampIfSolid(kit, TUBE_HALF + 7, BACK_EXIT_FLOOR + 3, (EmberBodyBuilder.RIM_NICHE_Z1 + EmberBodyBuilder.RIM_NICHE_Z2) / 2);
        kit.set(TUBE_HALF + 3, BACK_EXIT_FLOOR + 5, (EmberBodyBuilder.RIM_NICHE_Z1 + EmberBodyBuilder.RIM_NICHE_Z2) / 2, EmberKit.GLOW);
    }
}
