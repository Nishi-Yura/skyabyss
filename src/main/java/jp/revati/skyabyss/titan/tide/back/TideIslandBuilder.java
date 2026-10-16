package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.forge.ResonanceForgeBlock;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の本番の島を建てる (08 の 3 章)。最初の乗り込みと、配置の版が変わったときに一度だけ動く (数秒かかる)。
// 順番: 甲羅 (中を詰めた楕円の丘) → 浜と潮門 → くぐり道 → 広間 → 回り道と東の回廊 → 珊瑚の庭 → 潮見の間 → 干潟 →
//       首・頭・ひれ (飾り) → 螺旋の階段と殻の底 → 掘った所の内壁を潮の民の石に (skin) → 休み場・碑文・石・抜け道の床
// 潮で動く所 (水、門の開け閉め、庭の道の形、潮見の間の水位、巣) は試練の側 (TideBackTrials ほか) が置く
public final class TideIslandBuilder {
    private TideIslandBuilder() {
    }

    public static void build(TideKit kit) {
        shell(kit);
        beach(kit);
        cave(kit);
        hub(kit);
        ramp(kit);
        garden(kit);
        eastRoom(kit);
        flats(kit);
        decor(kit);
        spiral(kit);
        TideHeartArena.build(kit.level());
        skin(kit);
        fixtures(kit);
    }

    // ---------------------------------------------------------------- 甲羅
    static boolean shellMaterial(BlockState s) {
        return s.is(Blocks.TUFF) || s.is(Blocks.DEEPSLATE_TILES);
    }

    private static void shell(TideKit kit) {
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int x = -SHELL_RX - 3; x <= SHELL_RX + 3; x++) {
            for (int z = SHELL_CZ - SHELL_RZ - 3; z <= SHELL_CZ + SHELL_RZ + 3; z++) {
                int top = shellTop(x, z);
                // 建て直しで古い建物が残らないよう、海の上と、仮の島や仮の心臓の間があった真ん中の海の中を戻す
                if (top == Integer.MIN_VALUE) {
                    for (int y = SEA; y <= SEA + 40; y++) {
                        if (!kit.get(x, y, z).isAir()) {
                            kit.set(x, y, z, TideKit.AIR);
                        }
                    }
                    for (int y = SEA_FLOOR + 1; y < SEA; y++) {
                        if (!kit.get(x, y, z).is(Blocks.WATER)) {
                            kit.set(x, y, z, water);
                        }
                    }
                    continue;
                }
                if (Math.abs(x) <= 30 && Math.abs(z) <= 30) {
                    for (int y = SEA_FLOOR + 1; y < 58; y++) {
                        kit.set(x, y, z, water);
                    }
                }
                double s2 = shellS2(x, z);
                // 縁の鋸歯 (甲羅の板の縁)
                if (s2 > 0.86) {
                    double a = Math.atan2(z - SHELL_CZ, x);
                    int notch = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 44);
                    top += notch % 2 == 0 ? 1 : 0;
                }
                for (int y = 58; y <= top; y++) {
                    BlockState s;
                    int depth = top - y;
                    if (depth == 0) {
                        s = surface(kit, x, z);
                    } else if (depth <= 2) {
                        s = plate(x, z);
                    } else {
                        s = TideKit.SHELL;
                    }
                    kit.set(x, y, z, s);
                }
                for (int y = top + 1; y <= top + 24; y++) {
                    if (!kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 珊瑚の塊 (甲羅の上に、色のついた礁が数か所)
        for (int i = 0; i < 70; i++) {
            int x = kit.random.nextInt(2 * SHELL_RX) - SHELL_RX;
            int z = kit.random.nextInt(2 * SHELL_RZ) - SHELL_RZ + SHELL_CZ;
            double s2 = shellS2(x, z);
            if (s2 > 0.8 || s2 < 0.04) {
                continue;
            }
            int top = shellTop(x, z);
            int h = 1 + kit.random.nextInt(3);
            BlockState c = reef(kit.random.nextInt(5));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int hh = (dx == 0 && dz == 0) ? h : (kit.random.nextBoolean() ? h - 1 : 0);
                    for (int y = 1; y <= hh; y++) {
                        kit.set(x + dx, shellTop(x + dx, z + dz) + y, z + dz, c);
                    }
                }
            }
            kit.set(x, top + h + 1, z, deadFan(kit.random.nextInt(4)));
        }
    }

    // なめらかな値の雑音 (0〜1)。格子の点に乱数を置き、間をなめらかにつなぐ
    static double noise(double x, double z, double scale, int seed) {
        double fx = x / scale;
        double fz = z / scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(ix, iz, seed);
        double b = lattice(ix + 1, iz, seed);
        double c = lattice(ix, iz + 1, seed);
        double d = lattice(ix + 1, iz + 1, seed);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private static double lattice(int x, int z, int seed) {
        long h = x * 374761393L + z * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    // 甲羅の地表: 甲羅の板の継ぎ目 (同心の環と放射の線) は暗い石、板の上は苔と砂の大きな斑、ところどころ珊瑚の色
    private static BlockState surface(TideKit kit, int x, int z) {
        double s = Math.sqrt(shellS2(x, z));
        double a = Math.atan2(z - SHELL_CZ, x);
        double ring = Math.abs(s * 3.2 - Math.round(s * 3.2));
        double ray = Math.abs(Math.sin(a * 4 + (s < 0.31 ? 0 : 0.6)));
        if (s > 0.12 && (ring < 0.025 || (ray < 0.05 && s > 0.31))) {
            return (Math.floorMod(x + z, 3) == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
        }
        double n = noise(x, z, 9, 1) * 0.7 + noise(x, z, 3.5, 2) * 0.3;
        double c = noise(x, z, 6, 3);
        if (c > 0.72) {
            return reef((int) (noise(x, z, 4, 4) * 10));
        }
        if (n < 0.42) {
            return Blocks.MOSS_BLOCK.defaultBlockState();
        }
        if (n < 0.55) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (n < 0.70) {
            return Blocks.SAND.defaultBlockState();
        }
        return n < 0.78 ? Blocks.GRAVEL.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
    }

    // 甲羅の板: 同心の環ごとに色を変える (掘った所の断面で見える)
    private static BlockState plate(int x, int z) {
        double r = Math.sqrt(shellS2(x, z)) * 10;
        return ((int) r) % 2 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    static BlockState reef(int i) {
        return switch (Math.floorMod(i, 5)) {
            case 0 -> Blocks.PINK_TERRACOTTA.defaultBlockState();
            case 1 -> Blocks.RED_TERRACOTTA.defaultBlockState();
            case 2 -> Blocks.ORANGE_TERRACOTTA.defaultBlockState();
            case 3 -> Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            default -> Blocks.MAGENTA_TERRACOTTA.defaultBlockState();
        };
    }

    private static BlockState deadFan(int i) {
        return switch (Math.floorMod(i, 4)) {
            case 0 -> Blocks.DEAD_BRAIN_CORAL.defaultBlockState();
            case 1 -> Blocks.DEAD_FIRE_CORAL.defaultBlockState();
            case 2 -> Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState();
            default -> Blocks.DEAD_HORN_CORAL.defaultBlockState();
        };
    }

    // ---------------------------------------------------------------- 潮門の浜 (到着) と潮門
    private static void beach(TideKit kit) {
        Area b = BEACH;
        for (int x = b.x1(); x <= b.x2(); x++) {
            for (int z = b.z1(); z <= b.z2(); z++) {
                for (int y = 58; y <= b.floor(); y++) {
                    kit.set(x, y, z, y == b.floor() ? beachFloor(x, z) : TideKit.SANDSTONE);
                }
                for (int y = b.floor() + 1; y <= b.floor() + 40; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 浜のまわりの甲羅をすり鉢のように下げる (浜が深い穴に見えないように。胸壁の外なので歩いては出られない)
        for (int x = b.x1() - 16; x <= b.x2() + 16; x++) {
            for (int z = b.z1() - 16; z <= b.z2() + 16; z++) {
                int top = shellTop(x, z);
                if (top == Integer.MIN_VALUE || (x >= b.x1() - 1 && x <= b.x2() + 1 && z >= b.z1() - 1 && z <= b.z2() + 1)) {
                    continue;
                }
                double dx = Math.max(0, Math.max(b.x1() - x, x - b.x2()));
                double dz = Math.max(0, Math.max(b.z1() - z, z - b.z2()));
                int limit = b.floor() + 5 + (int) Math.round(Math.hypot(dx, dz) * 1.4);
                if (top <= limit) {
                    continue;
                }
                // くぐり道の上は残す
                if (x >= CAVE_X1 - 2 && x <= CAVE_X2 + 2 && z <= b.z1()) {
                    limit = Math.max(limit, caveFloor(Math.max(z, CAVE_Z_HUB)) + 7);
                }
                for (int y = limit + 1; y <= top + 1; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                kit.set(x, limit, z, surface(kit, x, z));
                kit.set(x, limit - 1, z, plate(x, z));
            }
        }
        // 浜のまわりの胸壁 (外の甲羅の上へ歩いて出られないように。浜の床から 4 段)。北はくぐり道、南は潮門の段だけ開く
        for (int x = b.x1() - 1; x <= b.x2() + 1; x++) {
            for (int z = b.z1() - 1; z <= b.z2() + 1; z++) {
                boolean ring = x == b.x1() - 1 || x == b.x2() + 1 || z == b.z1() - 1 || z == b.z2() + 1;
                if (!ring) {
                    continue;
                }
                boolean cave = z == b.z1() - 1 && x >= CAVE_X1 && x <= CAVE_X2;
                boolean gate = z == b.z2() + 1 && x >= GATE_X - 1 && x <= GATE_X + 1;
                if (cave || gate) {
                    continue;
                }
                for (int y = 58; y <= b.floor() + 4; y++) {
                    if (kit.get(x, y, z).isAir() || kit.get(x, y, z).is(Blocks.WATER) || y <= b.floor()) {
                        kit.set(x, y, z, y == b.floor() + 4 ? Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState() : kit.wall(x, y, z));
                    }
                }
            }
        }
        // 潮門: 枠は x -28 と -24、口は x -27〜-25、y 67〜69、z 59〜60。上の梁は y 70
        for (int z = GATE_Z; z <= GATE_Z + 1; z++) {
            for (int y = GATE_Y0 - 1; y <= GATE_Y1 + 1; y++) {
                for (int x = GATE_X - 2; x <= GATE_X + 2; x++) {
                    boolean side = Math.abs(x - GATE_X) == 2;
                    BlockState s;
                    if (y == GATE_Y0 - 1) {
                        s = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    } else if (y >= GATE_Y1) {
                        s = y == GATE_Y1 + 1 && !side ? Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState() : TideKit.DARK;
                    } else if (side) {
                        s = y == GATE_Y0 + 1 ? TideKit.LAMP : TideKit.BRICK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 門の外の段 (浜 66 から海 62 へ)。両脇は縁の石
        for (int i = 0; i < 6; i++) {
            int z = GATE_Z + 2 + i;
            int y = GATE_Y0 - 2 - i;
            for (int x = GATE_X - 1; x <= GATE_X + 1; x++) {
                if (y >= SEA - 1) {
                    kit.stair(x, y, z, Direction.NORTH, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                    for (int yy = y + 1; yy <= y + 5; yy++) {
                        kit.set(x, yy, z, TideKit.AIR);
                    }
                } else {
                    for (int yy = SEA; yy <= SEA + 5; yy++) {
                        kit.set(x, yy, z, TideKit.AIR);
                    }
                }
            }
            for (int side = -2; side <= 2; side += 4) {
                for (int yy = SEA - 1; yy <= Math.max(y, SEA) + 5; yy++) {
                    kit.set(GATE_X + side, yy, z, kit.wall(GATE_X + side, yy, z));
                }
            }
        }
        // 浜の道具: 炉、腰掛け、焚き火、潮見の目盛りの石盤、三つの試練の碑文
        kit.set(-14, 67, 46, ModBlocks.RESONANCE_FORGE.get().defaultBlockState().setValue(ResonanceForgeBlock.FACING, Direction.WEST));
        kit.set(-18, 67, 54, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.stair(-20, 67, 54, Direction.WEST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-16, 67, 54, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        kit.stair(-18, 67, 56, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        // 石盤 (北の崖に背をつけた 5x3 の板。潮見の目盛りの説明と、三つの試練)
        for (int x = -24; x <= -20; x++) {
            for (int y = 67; y <= 69; y++) {
                kit.set(x, y, 42, y == 69 && (x == -24 || x == -20) ? TideKit.LAMP : TideKit.DARK);
            }
        }
        kit.wallSign(new BlockPos(-23, 68, 43), Direction.SOUTH, "sign.skyabyss.tide_gauge");
        kit.wallSign(new BlockPos(-21, 68, 43), Direction.SOUTH, "sign.skyabyss.tide_trials");
        // 碑文の石柱
        for (int y = 67; y <= 69; y++) {
            kit.set(-17, y, 42, TideKit.DARK);
        }
        // 浜の縁の灯り
        for (int[] l : new int[][] {{-31, 43}, {-31, 61}, {-13, 61}}) {
            kit.set(l[0], 67, l[1], Blocks.PRISMARINE_WALL.defaultBlockState());
            kit.set(l[0], 68, l[1], TideKit.LAMP);
        }
    }

    private static BlockState beachFloor(int x, int z) {
        int h = Math.floorMod(x * 7 + z * 3, 9);
        return h < 6 ? TideKit.SAND : h < 8 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // 地上へ戻る潮門の口に入っているか
    public static boolean inReturnGate(double x, double y, double z) {
        return x >= GATE_X - 1 && x < GATE_X + 2 && z >= GATE_Z && z < GATE_Z + 2 && y >= GATE_Y0 - 0.5 && y < GATE_Y1;
    }

    // ---------------------------------------------------------------- 縁のくぐり道 (浜 66 → 広間の南の段 74)
    static int caveFloor(int z) {
        int len = CAVE_Z_BEACH - CAVE_Z_HUB;
        return BEACH.floor() + (int) Math.round((HUB_LEDGE - BEACH.floor()) * (CAVE_Z_BEACH - z) / (double) len);
    }

    private static void cave(TideKit kit) {
        for (int z = CAVE_Z_BEACH + 1; z >= CAVE_Z_HUB - 1; z--) {
            int f = z > CAVE_Z_BEACH ? BEACH.floor() : caveFloor(Math.max(z, CAVE_Z_HUB));
            for (int x = CAVE_X1 - 1; x <= CAVE_X2 + 1; x++) {
                boolean wall = x < CAVE_X1 || x > CAVE_X2;
                for (int y = f; y <= f + 5; y++) {
                    if (wall) {
                        kit.set(x, y, z, z % 6 == 0 && y == f + 2 ? TideKit.LAMP : kit.wall(x, y, z));
                    } else if (y == f) {
                        boolean step = z <= CAVE_Z_BEACH && z > CAVE_Z_HUB - 1 && caveFloor(z) > caveFloor(z + 1);
                        if (step) {
                            kit.set(x, y - 1, z, TideKit.FLOOR);
                            kit.stair(x, y, z, Direction.NORTH, Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 5) {
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 潮の広間
    private static void hub(TideKit kit) {
        for (int x = -HUB_R - 3; x <= HUB_R + 3; x++) {
            for (int z = -HUB_R - 3; z <= HUB_R + 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                for (int y = HUB_FLOOR - 1; y <= HUB_CEILING + 2; y++) {
                    BlockState s;
                    if (r <= HUB_R + 0.5) {
                        if (y < HUB_FLOOR) {
                            s = TideKit.BRICK;
                        } else if (y == HUB_FLOOR) {
                            s = hubFloor(x, z, r);
                        } else if (y < HUB_CEILING) {
                            s = TideKit.AIR;
                        } else if (r <= MOON_R + 0.5) {
                            s = TideKit.AIR;
                        } else if (r <= MOON_R + 1.5) {
                            s = TideKit.DARK;
                        } else {
                            s = (y == HUB_CEILING && Math.floorMod(x * 3 + z * 7, 13) == 0) ? TideKit.LAMP : TideKit.BRICK;
                        }
                    } else if (r <= HUB_R + 2.5) {
                        // 壁: 潮の高さ (71〜73) は暗い帯、上は真珠層の筋、灯りは縁の段の高さ
                        if (y >= HUB_FLOOR + 1 && y <= HUB_FLOOR + 3) {
                            s = TideKit.DARK;
                        } else if (y == HUB_LEDGE + 3 && Math.floorMod((int) Math.round(Math.atan2(z, x) * 12 / Math.PI), 3) == 0 && r <= HUB_R + 1.5) {
                            s = TideKit.LAMP;
                        } else {
                            s = kit.wall(x, y, z);
                        }
                    } else {
                        continue;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 月の窓: 天井の穴から地表まで。上はガラスでふさぐ (外から落ちないように)。窓のまわりの地表に柱 4 本
        int top = shellTop(0, 0);
        for (int x = -MOON_R - 1; x <= MOON_R + 1; x++) {
            for (int z = -MOON_R - 1; z <= MOON_R + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                for (int y = HUB_CEILING; y <= top + 1; y++) {
                    if (r <= MOON_R + 0.5) {
                        kit.set(x, y, z, y == top ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : TideKit.AIR);
                    } else if (r <= MOON_R + 1.5) {
                        kit.set(x, y, z, y >= top ? Blocks.DARK_PRISMARINE_SLAB.defaultBlockState() : TideKit.DARK);
                    }
                }
            }
        }
        for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            int h = shellTop(c[0], c[1]);
            for (int y = h + 1; y <= h + 5; y++) {
                kit.set(c[0], y, c[1], y == h + 5 ? TideKit.LAMP : Blocks.PRISMARINE_WALL.defaultBlockState());
            }
        }
        // 縁の段 (南・西はつながる。東の回廊は離れる)。段の下は詰める
        for (Area a : new Area[] {LEDGE_S, LEDGE_W, GALLERY_E}) {
            for (int x = a.x1(); x <= a.x2(); x++) {
                for (int z = a.z1(); z <= a.z2(); z++) {
                    for (int y = HUB_FLOOR; y < HUB_LEDGE; y++) {
                        kit.set(x, y, z, TideKit.BRICK);
                    }
                    kit.set(x, HUB_LEDGE, z, (x + z) % 4 == 0 ? TideKit.DARK : TideKit.FLOOR);
                    for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 5; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 段の縁の手すり (水盤の側の角に低い壁)。東の回廊の縁は開けておく (水から上がるため)
        // 南の段から水盤の底へ下りる階段
        for (int i = 0; i < 3; i++) {
            int z = 7 - i;
            int y = HUB_LEDGE - 1 - i;
            for (int x = HUB_STAIR_X1; x <= HUB_STAIR_X2; x++) {
                for (int yy = HUB_FLOOR + 1; yy < y; yy++) {
                    kit.set(x, yy, z, TideKit.BRICK);
                }
                kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            }
        }
        // 干潟の門の先: 北へ下りる通路 (広間の床 70 → 干潟の前室 66)
        for (int z = FLATS_GATE_Z; z >= F0.z2() + 1; z--) {
            int f = switch (z) {
                case -13, -14 -> HUB_FLOOR;
                case -15 -> 69;
                case -16 -> 68;
                case -17 -> 67;
                default -> 66;
            };
            for (int x = -2; x <= 2; x++) {
                for (int y = f - 1; y <= f + 5; y++) {
                    boolean wall = Math.abs(x) == 2;
                    if (wall) {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    } else if (y == f - 1) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else if (y == f) {
                        if (z <= -15 && z >= -17) {
                            kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 5 || (z == FLATS_GATE_Z && y == f + 4)) {
                        // 門の段は口を 3 マスの高さにする (水面の上のすき間から泳いで抜けないように)
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 西の段から庭の入口 g0 へ下りる通路 (x -13 → -18。74 → 68)
        for (int x = -13; x >= -18; x--) {
            int f = HUB_LEDGE + 1 + x; // -13: 72? を避けるため下で補正
            f = switch (x) {
                case -13 -> 73;
                case -14 -> 72;
                case -15 -> 71;
                case -16 -> 70;
                case -17 -> 69;
                default -> 68;
            };
            for (int z = -2; z <= 2; z++) {
                for (int y = f - 1; y <= f + 6; y++) {
                    boolean wall = Math.abs(z) == 2;
                    if (wall) {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    } else if (y == f - 1) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else if (y == f) {
                        if (x >= -17) {
                            kit.stair(x, y, z, Direction.EAST, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else if (y == f + 6) {
                        kit.set(x, y, z, TideKit.DARK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 東の回廊から潮見の前室への道
        Area h = EAST_HALL;
        kit.room(h.x1(), h.x2(), h.z1(), h.z2(), h.floor(), h.height(), TideKit.FLOOR);
    }

    private static BlockState hubFloor(int x, int z, double r) {
        if (Math.abs(x) <= BOTTOM_GATE_R && Math.abs(z) <= BOTTOM_GATE_R) {
            return TideKit.DARK;
        }
        int ring = (int) Math.round(r);
        if (ring == 4 || ring == 8) {
            return TideKit.DARK;
        }
        return ring % 2 == 0 ? TideKit.FLOOR : Blocks.CUT_SANDSTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 回り道 (干潮の道)
    // 点を結ぶ幅 3 の通路。{x, z, 床の高さ} を返す (同じ所は一度だけ)
    public static List<int[]> rampCells() {
        List<int[]> cells = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (int i = 0; i + 1 < RAMP.length; i++) {
            int[] a = RAMP[i];
            int[] b = RAMP[i + 1];
            int len = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int k = 0; k <= len; k++) {
                int cx = a[0] + Integer.signum(b[0] - a[0]) * k;
                int cz = a[1] + Integer.signum(b[1] - a[1]) * k;
                int f = a[2] + (int) Math.floor((b[2] - a[2]) * k / (double) Math.max(1, len) + 1e-9);
                if (k == len) {
                    f = b[2];
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long key = ((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL);
                        if (seen.add(key)) {
                            cells.add(new int[] {cx + dx, cz + dz, f});
                        }
                    }
                }
            }
        }
        return cells;
    }

    private static void ramp(TideKit kit) {
        List<int[]> cells = rampCells();
        java.util.Map<Long, Integer> floorAt = new java.util.HashMap<>();
        for (int[] c : cells) {
            floorAt.put(((long) c[0] << 32) ^ (c[1] & 0xffffffffL), c[2]);
        }
        // 外壁 (通路のまわり 1 マス) を先に、それから中を掘る
        for (int[] c : cells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long k = ((long) (c[0] + dx) << 32) ^ ((c[1] + dz) & 0xffffffffL);
                    if (floorAt.containsKey(k) || Math.sqrt((c[0] + dx) * (c[0] + dx) + (c[1] + dz) * (c[1] + dz)) <= HUB_R + 0.5) {
                        continue;
                    }
                    for (int y = c[2]; y <= c[2] + 5; y++) {
                        // ほかの通路 (東の回廊への道) で空いている所はふさがない
                        if (!kit.get(c[0] + dx, y, c[1] + dz).isAir()) {
                            kit.set(c[0] + dx, y, c[1] + dz, kit.wall(c[0] + dx, y, c[1] + dz));
                        }
                    }
                }
            }
        }
        for (int[] c : cells) {
            if (Math.sqrt(c[0] * c[0] + c[1] * c[1]) <= HUB_R + 0.5) {
                // 広間の中 (床と天井はそのまま)
                continue;
            }
            kit.set(c[0], c[2] - 1, c[1], TideKit.BRICK);
            // 一段上がる所は階段 (登る向きは、低い隣の反対)
            Direction up = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Integer n = floorAt.get(((long) (c[0] - d.getStepX()) << 32) ^ ((c[1] - d.getStepZ()) & 0xffffffffL));
                if (n != null && n == c[2] - 1) {
                    up = d;
                }
            }
            if (up != null) {
                kit.stair(c[0], c[2], c[1], up, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
            } else {
                kit.set(c[0], c[2], c[1], TideKit.FLOOR);
            }
            for (int y = c[2] + 1; y <= c[2] + 4; y++) {
                kit.set(c[0], y, c[1], TideKit.AIR);
            }
            kit.set(c[0], c[2] + 5, c[1], (c[0] + c[1]) % 5 == 0 ? TideKit.LAMP : TideKit.DARK);
        }
    }

    // ---------------------------------------------------------------- 珊瑚の庭 (西)
    // 水路の L 字 (幅 3): 始めは z の向き、次に x の向き
    public static List<BlockPos> channelCells(Channel c) {
        List<BlockPos> list = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int fx = c.from[0];
        int fz = c.from[1];
        int tx = c.to[0];
        int tz = c.to[1];
        for (int z = Math.min(fz, tz); z <= Math.max(fz, tz); z++) {
            for (int d = -1; d <= 1; d++) {
                add(list, seen, fx + d, z);
            }
        }
        for (int x = Math.min(fx, tx); x <= Math.max(fx, tx); x++) {
            for (int d = -1; d <= 1; d++) {
                add(list, seen, x, tz + d);
            }
        }
        return list;
    }

    private static void add(List<BlockPos> list, Set<Long> seen, int x, int z) {
        if (seen.add(((long) x << 32) ^ (z & 0xffffffffL))) {
            list.add(new BlockPos(x, 0, z));
        }
    }

    private static void garden(TideKit kit) {
        Area g = GARDEN;
        // 礁の壁 (水路の外) をいったん全部詰め、上は空まで開ける
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                int bump = gardenBump(x, z);
                for (int y = 58; y <= GARDEN_WALL_TOP + bump; y++) {
                    kit.set(x, y, z, y > GARDEN_WALL_TOP - 1 + bump ? gardenCap(x, z) : gardenWall(x, y, z));
                }
                int top = Math.max(shellTop(x, z), GARDEN_WALL_TOP + 4);
                for (int y = GARDEN_WALL_TOP + bump + 1; y <= top + 2; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
                // 珊瑚の芽 (枯れた珊瑚の扇と、ときどき高い枝)
                double sp = noise(x, z, 2.2, 7);
                if (sp > 0.86) {
                    int hgt = 1 + (int) ((sp - 0.86) * 30);
                    for (int y = 1; y <= hgt; y++) {
                        kit.set(x, GARDEN_WALL_TOP + bump + y, z, reef(x + z));
                    }
                } else if (sp > 0.74) {
                    kit.set(x, GARDEN_WALL_TOP + bump + 1, z, deadFan(x * 3 + z));
                }
            }
        }
        // 庭の縁 (四方) に、礁の壁の上の胸壁を一段 (外から落ちてこないように。中からは届かない高さ)
        for (int x = g.x1(); x <= g.x2(); x++) {
            for (int z = g.z1(); z <= g.z2(); z++) {
                if (x == g.x1() || x == g.x2() || z == g.z1() || z == g.z2()) {
                    int top = Math.max(shellTop(x, z), GARDEN_WALL_TOP);
                    for (int y = GARDEN_WALL_TOP + 1; y <= Math.min(top, GARDEN_WALL_TOP + 3) + 1; y++) {
                        kit.set(x, y, z, gardenWall(x, y, z));
                    }
                }
            }
        }
        // 大貝の淵 (半径 3 の窪み。床 64)。大貝そのものは道の形と一緒に置く
        for (int x = CLAM[0] - 3; x <= CLAM[0] + 3; x++) {
            for (int z = CLAM[1] - 3; z <= CLAM[1] + 3; z++) {
                if (Math.hypot(x - CLAM[0], z - CLAM[1]) <= 3.3) {
                    kit.set(x, GARDEN_FLOOR, z, TideKit.SAND);
                    for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        carveGardenRoute(kit, 0, true);
    }

    // 庭の壁の上の高さの揺れ (0〜2)
    static int gardenBump(int x, int z) {
        return (int) Math.floor(noise(x, z, 5, 5) * 2.99);
    }

    // 壁のいちばん上 (苔と珊瑚)
    static BlockState gardenCap(int x, int z) {
        double n = noise(x, z, 4, 6);
        return n < 0.45 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 0.7 ? reef((int) (n * 13)) : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    static BlockState gardenWall(int x, int y, int z) {
        int h = Math.floorMod(x * 5 + y * 3 + z * 11 + (y / 3) * 7, 17);
        if (h < 6) {
            return reef(h + y / 2);
        }
        if (h < 9) {
            return Blocks.TERRACOTTA.defaultBlockState();
        }
        if (h < 12) {
            return Blocks.TUFF_BRICKS.defaultBlockState();
        }
        if (h < 14) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.WHITE_TERRACOTTA.defaultBlockState();
    }

    // 庭の道の形を置く: すべての水路を壁でふさいでから、選んだ形の水路と、いつも開いている水路 (大貝〜A、g0〜C) を掘る。
    // 台・祭壇・入口の段は最後に置き直す (水路が台を通るため)
    public static void carveGardenRoute(TideKit kit, int route, boolean full) {
        for (Channel c : Channel.values()) {
            for (BlockPos p : channelCells(c)) {
                int bump = gardenBump(p.getX(), p.getZ());
                for (int y = GARDEN_FLOOR; y <= GARDEN_WALL_TOP + bump; y++) {
                    kit.set(p.getX(), y, p.getZ(), y > GARDEN_WALL_TOP - 1 + bump ? gardenCap(p.getX(), p.getZ()) : gardenWall(p.getX(), y, p.getZ()));
                }
            }
        }
        List<Channel> open = new ArrayList<>(List.of(GARDEN_ROUTES[Math.floorMod(route, GARDEN_ROUTES.length)]));
        open.add(Channel.CLAM_A);
        open.add(Channel.G0_C);
        for (Channel c : open) {
            for (BlockPos p : channelCells(c)) {
                kit.set(p.getX(), GARDEN_FLOOR, p.getZ(), noise(p.getX(), p.getZ(), 3, 8) < 0.25 ? Blocks.GRAVEL.defaultBlockState() : TideKit.SAND);
                for (int y = GARDEN_FLOOR + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(p.getX(), y, p.getZ(), TideKit.AIR);
                }
            }
        }
        // 台 (9x9 の段。上の 3x3 が床 68)
        for (int[] c : new int[][] {PLAT_A, PLAT_B1, PLAT_B2, PLAT_C, PLAT_D1, PLAT_D2}) {
            ziggurat(kit, c[0], c[1], GARDEN_FLOOR, PLATFORM_TOP, open, c == PLAT_C);
        }
        // 祭壇の台 (床 69。上は 5x5)
        ziggurat(kit, ALTAR[0], ALTAR[1], GARDEN_FLOOR, ALTAR_TOP, open, false);
        // g1 の焚き火と、台の灯りを置き直す (台を建て直したため)
        Rest g1 = RESTS.get(2);
        kit.campfire(g1.campfire());
        if (!full) {
            return;
        }
        // 入口の段 g0 (x -24〜-19、z -3〜3、床 68) と、水路へ下りる階段 (西へ)
        for (int x = G0.x1(); x <= G0.x2(); x++) {
            for (int z = G0.z1(); z <= G0.z2(); z++) {
                for (int y = GARDEN_FLOOR; y <= PLATFORM_TOP; y++) {
                    kit.set(x, y, z, y == PLATFORM_TOP ? TideKit.FLOOR : TideKit.SANDSTONE);
                }
                for (int y = PLATFORM_TOP + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = G0.x1() - 1 - i;
            for (int z = -1; z <= 1; z++) {
                for (int y = GARDEN_FLOOR; y < PLATFORM_TOP - 1 - i; y++) {
                    kit.set(x, y, z, TideKit.SANDSTONE);
                }
                kit.stair(x, PLATFORM_TOP - 1 - i, z, Direction.EAST, Blocks.SANDSTONE_STAIRS.defaultBlockState());
                for (int y = PLATFORM_TOP - i; y <= GARDEN_WALL_TOP + 2; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 大貝 (淵の真ん中の台。真珠は試練の側が置く)
        int cx = CLAM[0];
        int cz = CLAM[1];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                kit.set(cx + dx, GARDEN_FLOOR + 1, cz + dz, Blocks.WHITE_TERRACOTTA.defaultBlockState());
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            kit.set(cx + dx, GARDEN_FLOOR + 2, cz + 1, Blocks.PINK_TERRACOTTA.defaultBlockState());
            kit.set(cx + dx, GARDEN_FLOOR + 3, cz + 1, dx == 0 ? Blocks.WHITE_TERRACOTTA.defaultBlockState() : Blocks.PINK_TERRACOTTA.defaultBlockState());
        }
        kit.set(cx - 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
        kit.set(cx + 1, GARDEN_FLOOR + 2, cz, Blocks.PINK_TERRACOTTA.defaultBlockState());
    }

    // 段の台: 外周から内へ一段ずつ上がる。上の床は top (中心の 3x3、祭壇は 5x5)
    private static void ziggurat(TideKit kit, int cx, int cz, int floor, int top, List<Channel> open, boolean rest) {
        int steps = top - floor;
        int coreR = top == ALTAR_TOP ? 2 : 1;
        for (int dx = -coreR - steps; dx <= coreR + steps; dx++) {
            for (int dz = -coreR - steps; dz <= coreR + steps; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                int h = ring <= coreR ? top : top - (ring - coreR);
                if (h <= floor) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                for (int y = floor; y <= h; y++) {
                    kit.set(x, y, z, y == h ? (ring <= coreR ? TideKit.FLOOR : Blocks.SMOOTH_SANDSTONE.defaultBlockState()) : TideKit.SANDSTONE);
                }
                for (int y = h + 1; y <= GARDEN_WALL_TOP + 6; y++) {
                    kit.set(x, y, z, TideKit.AIR);
                }
            }
        }
        // 台の隅に灯り (潮除けの台の目印)
        kit.set(cx + coreR, top + 1, cz + coreR, Blocks.PRISMARINE_WALL.defaultBlockState());
        kit.set(cx + coreR, top + 2, cz + coreR, TideKit.LAMP);
    }

    // ---------------------------------------------------------------- 潮見の間 (東)
    private static void eastRoom(TideKit kit) {
        // 外枠を詰め、上を空へ開ける。四方に高い胸壁 (外へ飛び降りないように)
        for (int x = 23; x <= 58; x++) {
            for (int z = -37; z <= -4; z++) {
                boolean edge = x == 23 || x == 58 || z == -37 || z == -4;
                for (int y = 58; y <= 96; y++) {
                    if (y < HUB_LEDGE) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (edge) {
                        kit.set(x, y, z, y == 96 ? TideKit.DARK : kit.wall(x, y, z));
                    } else if (y == HUB_LEDGE) {
                        kit.set(x, y, z, TideKit.BRICK);
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 西の壁の口 (回り道の終わり z -11〜-9 から前室へ)
        for (int z = -11; z <= -9; z++) {
            for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 4; y++) {
                kit.set(23, y, z, TideKit.AIR);
            }
            kit.set(23, HUB_LEDGE, z, TideKit.FLOOR);
        }
        // 前室 s0 と段 0 (床 74)。前室は屋根付き
        Area s0 = S0;
        for (int x = s0.x1(); x <= TIER0.x2(); x++) {
            for (int z = s0.z1(); z <= s0.z2(); z++) {
                kit.set(x, HUB_LEDGE, z, (x + z) % 3 == 0 ? TideKit.DARK : TideKit.FLOOR);
            }
        }
        for (int x = s0.x1(); x <= s0.x2(); x++) {
            for (int z = s0.z1() - 1; z <= s0.z2() + 1; z++) {
                kit.set(x, HUB_LEDGE + 6, z, TideKit.DARK);
            }
        }
        // 前室の外 (段 0 の南北と、西の通路の口以外) は壁で囲む
        for (int x = 24; x <= 33; x++) {
            for (int y = HUB_LEDGE + 1; y <= HUB_LEDGE + 6; y++) {
                kit.set(x, y, s0.z1() - 1, kit.wall(x, y, s0.z1() - 1));
                kit.set(x, y, s0.z2() + 1, kit.wall(x, y, s0.z2() + 1));
            }
        }
        // 段 1・段 2・岩棚 (下は詰める)
        tierBlock(kit, TIER1, TideKit.FLOOR);
        tierBlock(kit, TIER2, TideKit.FLOOR);
        tierBlock(kit, LEDGE_E, TideKit.FLOOR);
        // 溜め (内側は空。壁は外周 1 マス、上は次の段の床の高さまで)
        for (int k = 0; k < 3; k++) {
            int[] b = BASIN[k];
            int bottom = TIER_FLOOR[k] - 1;
            int wallTop = k < 2 ? TIER_FLOOR[k + 1] : LEDGE_FLOOR;
            for (int x = b[0] - 1; x <= b[1] + 1; x++) {
                for (int z = b[2] - 1; z <= b[3] + 1; z++) {
                    boolean inside = x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3];
                    for (int y = HUB_LEDGE; y <= wallTop; y++) {
                        if (inside) {
                            kit.set(x, y, z, y < bottom ? TideKit.SHELL : y == bottom ? TideKit.DARK : TideKit.AIR);
                        } else {
                            kit.set(x, y, z, basinWall(x, y, z, k));
                        }
                    }
                }
            }
        }
        // 溜めの戸の下の敷居 (戸は試練の側が開け閉めする)
        for (int k = 0; k < 3; k++) {
            BlockPos d = BASIN_DOOR[k];
            kit.set(d.below(), TideKit.FLOOR);
            kit.set(d, TideKit.AIR);
            kit.set(d.above(), TideKit.AIR);
        }
        kit.set(T_EXIT_DOOR.below(), TideKit.FLOOR);
        kit.set(T_EXIT_DOOR, TideKit.AIR);
        kit.set(T_EXIT_DOOR.above(), TideKit.AIR);
        // 上の溜めの北の戸から岩棚へ上る階段 (x 52〜54、z -23〜-30)
        for (int i = 0; i <= 7; i++) {
            int z = -23 - i;
            int f = Math.min(LEDGE_FLOOR, TIER_FLOOR[2] + i);
            for (int x = 51; x <= 55; x++) {
                boolean wall = x == 51 || x == 55;
                for (int y = HUB_LEDGE; y <= f + 4; y++) {
                    if (wall) {
                        kit.set(x, y, z, y <= f + 1 ? kit.wall(x, y, z) : TideKit.AIR);
                    } else if (y < f) {
                        kit.set(x, y, z, TideKit.SHELL);
                    } else if (y == f) {
                        if (i > 0 && f > TIER_FLOOR[2] + i - 1 && f < LEDGE_FLOOR + 1 && i <= LEDGE_FLOOR - TIER_FLOOR[2]) {
                            kit.stair(x, y, z, Direction.SOUTH, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
                        } else {
                            kit.set(x, y, z, TideKit.FLOOR);
                        }
                    } else {
                        kit.set(x, y, z, TideKit.AIR);
                    }
                }
            }
        }
        // 岩棚の碑文の石柱
        for (int y = LEDGE_FLOOR + 1; y <= LEDGE_FLOOR + 3; y++) {
            kit.set(55, y, -36, TideKit.DARK);
        }
    }

    private static void tierBlock(TideKit kit, Area a, BlockState top) {
        for (int x = a.x1(); x <= a.x2(); x++) {
            for (int z = a.z1(); z <= a.z2(); z++) {
                for (int y = HUB_LEDGE; y <= a.floor(); y++) {
                    kit.set(x, y, z, y == a.floor() ? top : TideKit.BRICK);
                }
            }
        }
    }

    // 溜めの壁。浮き石の届く高さ (5 段目) に目盛りの灯り
    private static BlockState basinWall(int x, int y, int z, int k) {
        int mark = TIER_FLOOR[k] + 5;
        if (y == mark && (x + z) % 2 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return (y - TIER_FLOOR[k]) % 2 == 0 ? TideKit.DARK : TideKit.BRICK;
    }

    // ---------------------------------------------------------------- 群れの干潟 (前)
    private static void flats(TideKit kit) {
        Area f = FLATS;
        for (int x = f.x1() - 2; x <= f.x2() + 2; x++) {
            for (int z = f.z1() - 2; z <= f.z2(); z++) {
                boolean inside = x >= f.x1() && x <= f.x2() && z >= f.z1();
                for (int y = 58; y <= 100; y++) {
                    BlockState s;
                    if (inside) {
                        s = y < FLATS_FLOOR ? TideKit.SANDSTONE : y == FLATS_FLOOR ? flatsFloor(x, z) : TideKit.AIR;
                    } else if (y <= 69) {
                        s = rock(x, y, z);
                    } else if (y > 69) {
                        s = TideKit.AIR;
                    } else {
                        continue;
                    }
                    if (!inside && y > 69 && shellTop(x, z) != Integer.MIN_VALUE && y <= shellTop(x, z)) {
                        continue;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        // 岩の島 (7x7 の段。上 3x3 が床 66)
        for (int[] r : FLATS_ROCKS) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int ring = Math.max(Math.abs(dx), Math.abs(dz));
                    int h = ring <= 1 ? ROCK_TOP : ring == 2 ? ROCK_TOP - 1 : ROCK_TOP - 2;
                    for (int y = FLATS_FLOOR; y <= h; y++) {
                        kit.set(r[0] + dx, y, r[1] + dz, y == h ? (ring <= 1 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState()) : Blocks.ANDESITE.defaultBlockState());
                    }
                }
            }
        }
        // 干潟の前室 f0 と、干潟へのトンネル (床 66)、干潟の口と、干潟へ下りる段
        Area f0 = F0;
        kit.room(f0, TideKit.FLOOR);
        for (int z = f0.z1() - 1; z >= MOUTH_Z; z--) {
            for (int x = -2; x <= 2; x++) {
                for (int y = TUNNEL_FLOOR - 1; y <= TUNNEL_FLOOR + 5; y++) {
                    boolean wall = Math.abs(x) == 2;
                    BlockState s;
                    if (wall) {
                        s = z % 7 == 0 && y == TUNNEL_FLOOR + 2 ? TideKit.LAMP : kit.wall(x, y, z);
                    } else if (y < TUNNEL_FLOOR) {
                        s = TideKit.BRICK;
                    } else if (y == TUNNEL_FLOOR) {
                        s = TideKit.FLOOR;
                    } else if (y == TUNNEL_FLOOR + 5 || (z == MOUTH_Z && y == TUNNEL_FLOOR + 4)) {
                        s = TideKit.DARK;
                    } else {
                        s = TideKit.AIR;
                    }
                    kit.set(x, y, z, s);
                }
            }
        }
        for (int i = 0; i < 2; i++) {
            int z = MOUTH_Z - 1 - i;
            for (int x = -1; x <= 1; x++) {
                for (int y = FLATS_FLOOR; y < TUNNEL_FLOOR - 1 - i; y++) {
                    kit.set(x, y, z, Blocks.ANDESITE.defaultBlockState());
                }
                kit.stair(x, TUNNEL_FLOOR - 1 - i, z, Direction.SOUTH, Blocks.STONE_STAIRS.defaultBlockState());
            }
        }
    }

    private static BlockState flatsFloor(int x, int z) {
        double n = noise(x, z, 5, 9) * 0.75 + noise(x, z, 2, 10) * 0.25;
        return n < 0.55 ? TideKit.SAND : n < 0.72 ? Blocks.MUD.defaultBlockState() : n < 0.85 ? Blocks.CLAY.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
    }

    private static BlockState rock(int x, int y, int z) {
        int h = Math.floorMod(x * 3 + y * 5 + z * 7, 9);
        return h < 4 ? Blocks.STONE.defaultBlockState() : h < 7 ? Blocks.ANDESITE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    }

    // ---------------------------------------------------------------- 首・頭・ひれ (飾り。当たり判定はあるが、背中の島からは届かない)
    private static void decor(TideKit kit) {
        BlockState skin = Blocks.GREEN_TERRACOTTA.defaultBlockState();
        BlockState skin2 = Blocks.BROWN_TERRACOTTA.defaultBlockState();
        // 首: 干潟の北の壁の向こう (z -106〜-124)。断面は楕円、海から立ち上がる
        for (int z = -106; z >= -124; z--) {
            double t = (-106 - z) / 18.0;
            double cy = 60 + t * 12;
            double rx = 6.5 - t * 1.0;
            double ry = 5.0;
            ellipseSlice(kit, 0, cy, z, rx, ry, (z % 3 == 0) ? skin2 : skin);
        }
        // 頭: 中心 (0, 76, -131)
        for (int x = -10; x <= 10; x++) {
            for (int y = 66; y <= 86; y++) {
                for (int z = -142; z <= -122; z++) {
                    double e = sq(x / 8.5) + sq((y - 76) / 7.0) + sq((z + 131) / 10.0);
                    if (e <= 1) {
                        kit.set(x, y, z, e > 0.78 ? ((x + y + z) % 4 == 0 ? skin2 : skin) : Blocks.TERRACOTTA.defaultBlockState());
                    }
                }
            }
        }
        // 目 (暗い目に、かすかな光)
        for (int sx = -1; sx <= 1; sx += 2) {
            kit.set(sx * 7, 79, -133, Blocks.BLACK_CONCRETE.defaultBlockState());
            kit.set(sx * 7, 79, -134, Blocks.BLACK_CONCRETE.defaultBlockState());
            kit.set(sx * 8, 79, -133, Blocks.SHROOMLIGHT.defaultBlockState());
        }
        // 嘴の鉤
        for (int y = 70; y <= 75; y++) {
            kit.set(0, y, -141 + (75 - y) / 3, Blocks.BROWN_TERRACOTTA.defaultBlockState());
        }
        // ひれ (前の左右、後ろの左右)。海面すれすれの平たい板
        flipper(kit, 1, 34, -82, 26, 12, skin);
        flipper(kit, -1, 34, -82, 26, 12, skin);
        flipper(kit, 1, 52, 56, 16, 9, skin2);
        flipper(kit, -1, 52, 56, 16, 9, skin2);
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void ellipseSlice(TideKit kit, int cx, double cy, int z, double rx, double ry, BlockState s) {
        for (int x = (int) -Math.ceil(rx); x <= Math.ceil(rx); x++) {
            for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
                if (sq((x - cx) / rx) + sq((y - cy) / ry) <= 1 && y > SEA_FLOOR) {
                    kit.set(x, y, z, s);
                }
            }
        }
    }

    // side は +1 (東) / -1 (西)。ax: 付け根から先までの x の距離の中心、len・wid は長さと幅
    private static void flipper(TideKit kit, int side, int ax, int az, int len, int wid, BlockState s) {
        for (int i = 0; i <= len; i++) {
            int x = side * (ax - len / 2 + i);
            double w = wid * (1 - Math.pow(i / (double) len, 1.6)) / 2;
            int y = SEA - 2 - (i < len / 3 ? 0 : 1);
            for (int dz = (int) -Math.ceil(w); dz <= Math.ceil(w); dz++) {
                int z = az + dz + (int) Math.round(i * 0.25);
                if (shellTop(x, z) != Integer.MIN_VALUE || (Math.abs(x) <= 26 && z <= -70 && z >= -106)) {
                    continue;
                }
                kit.set(x, y, z, s);
                kit.set(x, y - 1, z, s);
            }
        }
    }

    // ---------------------------------------------------------------- 潮の底の門の下: 螺旋の階段と、殻の底の前室への通路
    // 螺旋の段の並び (中心の柱のまわり 8 マス)。最後の段 (18 番目) は北西の角、そこから北の通路へ
    public static final int[][] SPIRAL_RING = {{-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}};
    public static final int SPIRAL_STEPS = SPIRAL_TOP - SPIRAL_BOTTOM + 1;

    private static void spiral(TideKit kit) {
        // 外枠 (海の中に石の塔)
        for (int x = -5; x <= 5; x++) {
            for (int z = -10; z <= 4; z++) {
                for (int y = 48; y < HUB_FLOOR; y++) {
                    kit.set(x, y, z, TideKit.BRICK);
                }
            }
        }
        // 縦穴 (x -1〜1、z -1〜1。中心は柱)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = SPIRAL_BOTTOM; y < HUB_FLOOR; y++) {
                    kit.set(x, y, z, x == 0 && z == 0 ? (y % 4 == 0 ? TideKit.LAMP : TideKit.DARK) : TideKit.AIR);
                }
                kit.set(x, SPIRAL_BOTTOM - 1, z, TideKit.FLOOR);
            }
        }
        for (int i = 0; i < SPIRAL_STEPS; i++) {
            int[] c = SPIRAL_RING[i % 8];
            int[] prev = SPIRAL_RING[Math.floorMod(i - 1, 8)];
            int y = SPIRAL_TOP - i;
            Direction up = Direction.fromDelta(prev[0] - c[0], 0, prev[1] - c[1]);
            kit.stair(c[0], y, c[1], up == null ? Direction.NORTH : up, Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState());
        }
        // 底から北の通路 (z -2〜-9) と、休み場の小部屋 (x -4〜4、z -9〜-3)
        Area r = HEART_REST;
        for (int x = r.x1() - 1; x <= r.x2() + 1; x++) {
            for (int z = r.z1() - 1; z <= r.z2() + 1; z++) {
                for (int y = r.floor(); y <= r.floor() + r.height() + 1; y++) {
                    boolean in = x >= r.x1() && x <= r.x2() && z >= r.z1() && z <= r.z2() && y > r.floor() && y <= r.floor() + r.height();
                    if (in) {
                        kit.set(x, y, z, TideKit.AIR);
                    } else if (y == r.floor()) {
                        kit.set(x, y, z, TideKit.FLOOR);
                    } else {
                        kit.set(x, y, z, kit.wall(x, y, z));
                    }
                }
            }
        }
        // 縦穴から小部屋への口 (0, 51〜53, -2)
        for (int y = SPIRAL_BOTTOM; y <= SPIRAL_BOTTOM + 2; y++) {
            kit.set(0, y, -2, TideKit.AIR);
        }
        // 小部屋の北の口 (x -1〜1、z -10) は殻の底の前室へ
        for (int x = -1; x <= 1; x++) {
            for (int y = SPIRAL_BOTTOM; y <= SPIRAL_BOTTOM + 2; y++) {
                kit.set(x, y, -10, TideKit.AIR);
            }
            kit.set(x, SPIRAL_BOTTOM - 1, -10, TideKit.FLOOR);
        }
        kit.set(-4, SPIRAL_BOTTOM + 3, -3, TideKit.LAMP);
        kit.set(4, SPIRAL_BOTTOM + 3, -9, TideKit.LAMP);
    }

    // ---------------------------------------------------------------- 掘った所の内壁を、潮の民の石にする
    private static void skin(TideKit kit) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -SHELL_RX; x <= SHELL_RX; x++) {
            for (int z = SHELL_CZ - SHELL_RZ; z <= SHELL_CZ + SHELL_RZ; z++) {
                int top = shellTop(x, z);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                boolean garden = x >= GARDEN.x1() && x <= GARDEN.x2() && z >= GARDEN.z1() && z <= GARDEN.z2();
                for (int y = 58; y <= top - 3; y++) {
                    m.set(x, y, z);
                    BlockState s = kit.level().getBlockState(m);
                    if (!shellMaterial(s)) {
                        continue;
                    }
                    boolean open = false;
                    for (Direction d : Direction.values()) {
                        if (kit.level().getBlockState(m.relative(d)).isAir()) {
                            open = true;
                            break;
                        }
                    }
                    if (open) {
                        kit.set(x, y, z, garden ? gardenWall(x, y, z) : kit.wall(x, y, z));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 休み場・碑文・潮鳴り石・抜け道の床
    private static void fixtures(TideKit kit) {
        for (Rest r : RESTS) {
            kit.campfire(r.campfire());
        }
        for (Inscription i : LORE_SIGNS) {
            if (i.facing() == Direction.UP) {
                kit.set(i.pos(), Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
                kit.writeSign(i.pos(), i.key(), net.minecraft.world.item.DyeColor.CYAN);
            } else {
                kit.wallSign(i.pos(), i.facing(), i.key());
            }
        }
        // 干潟の門は閉じた形で建てる (干潮で水盤が空のときに、試練の側が開ける)
        for (int x = -1; x <= 1; x++) {
            for (int y = HUB_FLOOR + 1; y <= HUB_FLOOR + 3; y++) {
                kit.set(x, y, FLATS_GATE_Z, ModBlocks.SEALED_GATE.get().defaultBlockState());
            }
        }
        // 抜け道の床 (石が鳴るまでは暗い石。鳴ると光る)
        for (BlockPos p : new BlockPos[] {RETURN_WEST, RETURN_EAST, RETURN_FRONT}) {
            kit.set(p, TideKit.DARK);
        }
        // 各試練の入口の手引きの看板
        kit.wallSign(new BlockPos(-19, PLATFORM_TOP + 2, -3), Direction.SOUTH, "sign.skyabyss.tide_garden");
        kit.wallSign(new BlockPos(24, HUB_LEDGE + 2, -14), Direction.SOUTH, "sign.skyabyss.tide_watch");
        kit.wallSign(new BlockPos(3, TUNNEL_FLOOR + 2, -30), Direction.SOUTH, "sign.skyabyss.tide_flats");
        kit.wallSign(new BlockPos(2, HUB_LEDGE + 2, 12), Direction.NORTH, "sign.skyabyss.tide_hub_route");
    }
}
