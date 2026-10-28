package jp.revati.skyabyss.trial.raid;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

// 心臓の間の大広間 (2026-10-28 の試遊「鯨以外の巨神の心臓ボスの部屋、鯨みたいに大きくして作り込んでほしい」)。
// 潮殻・雷翼・余燼の心臓の間は、動かない心臓を中心に半径 14〜16 の床で戦う作り (技の届く範囲・沈む区画・満ち潮・テストがこの半径で組んである)。
// 戦う床はそのままにして、まわりを鯨の間 (54 x 54) と同じ大きさの大広間にする:
//  - 戦う床の縁の壁は高さ 2 の欄干だけ残し、その上と元の天井を取り払う
//  - 欄干の外は深い谷 (DEPTH 下)。谷の底は巨神ごとに 海水 / 雲の層 / 溶岩。戦う床は谷から立つ太い台の上に乗る
//  - 外壁は半径 HALL_R、高さ TOP まで。上は中心ほど高い円天井。壁の際に太い柱が PILLARS 本、柱と天井に灯り
//  - 入口の側 (前室・休み場) は幅 KEEP*2+1 の石の橋として残す (前室はその中のまま)。橋の上は取り払って空を見せる
// 人が立てる所は今までどおり (欄干は越えられない高さ)。建てるのは InstanceArenas.carve の最後 (元の部屋を建てたあと)
public final class HeartHall {
    private HeartHall() {
    }

    // 広間の外壁の半径 (鯨の間の半分の幅 27 にそろえる)
    public static final int HALL_R = 28;
    // 谷の深さ (戦う床の空気の段から下へ) と、外壁の高さ (上へ)
    static final int DEPTH = 13;
    static final int TOP = 22;
    // 円天井の盛り上がり (中心で TOP + DOME)
    static final int DOME = 7;
    static final int PILLARS = 10;
    // 入口の側に残す橋の半分の幅
    static final int KEEP = 5;

    // 円天井のいちばん高い所 (中心の真上) の y。air = 戦う床の上の空気の段
    public static int domeY(int air) {
        return air + TOP + DOME;
    }

    // 部屋の天井があるか (元の天井か、大広間の円天井)
    public static boolean roofed(ServerLevel level, int cx, int cz, int ceiling, int air) {
        return !level.getBlockState(new BlockPos(cx, ceiling, cz)).isAir() || !level.getBlockState(new BlockPos(cx, domeY(air), cz)).isAir();
    }

    public enum Theme {
        TIDE, STORM, EMBER
    }

    // cx, cz = 中心。air = 戦う床の上の空気の段の y。r = 戦う床の半径。gateDir = 入口の向き (+1 南 / -1 北)
    public static void build(ServerLevel level, Theme theme, int cx, int cz, int air, int r, int gateDir) {
        build(level, theme, cx, cz, air, r, gateDir, false);
    }

    // inner = 戦う床のまわり (欄干と元の天井) だけを開け直す (心臓の間を建て直したあと。広間の外は触らない)
    public static void build(ServerLevel level, Theme theme, int cx, int cz, int air, int r, int gateDir, boolean inner) {
        long t0 = System.nanoTime();
        Palette pal = Palette.of(theme);
        int bottom = air - DEPTH;
        int top = air + TOP;
        int flags = 2 | 16;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int span = HALL_R + 3;
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > HALL_R + 2.5 || (inner && d > r + 2.5)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                boolean bridge = Math.abs(dx) <= KEEP && dz * gateDir > r - 1;
                int dome = top + (int) Math.round(DOME * Math.max(0, 1 - (d / HALL_R) * (d / HALL_R)));
                for (int y = inner ? air - 2 : bottom - 1; y <= dome + 2; y++) {
                    p.set(x, y, z);
                    BlockState s = null;
                    if (y >= dome) {
                        // 円天井 (二段)。灯りの輪
                        s = (y == dome && ring(d, HALL_R * 0.55) && Math.floorMod(dx * 7 + dz * 3, 5) == 0) ? pal.light : (y == dome ? pal.ceiling : pal.wall);
                    } else if (d > HALL_R) {
                        // 外壁: 帯と灯り
                        int h = y - bottom;
                        s = h % 6 == 5 ? pal.band : (h % 6 == 2 && Math.floorMod(dx + dz, 7) == 0 ? pal.light : pal.wall);
                    } else if (y < bottom) {
                        s = pal.floor;
                    } else if (bridge) {
                        // 入口の側の橋: 前室はそのまま。下の空きは柱で埋め、上は空ける
                        if (y < air - 2 && level.getBlockState(p).isAir()) {
                            s = pal.pedestal;
                        } else if (y > air + 7) {
                            s = Blocks.AIR.defaultBlockState();
                        }
                    } else if (d <= r + 2.2) {
                        if (y < air - 2) {
                            // 戦う床を支える台 (空いている所だけ)
                            if (level.getBlockState(p).isAir()) {
                                s = (y - bottom) % 5 == 4 ? pal.band : pal.pedestal;
                            }
                        } else if (d > r && y == air + 1) {
                            // 欄干の上の段
                            s = Math.floorMod(Math.round(Math.atan2(dz, dx) * 60), 9) == 0 ? pal.railLight : pal.rail;
                        } else if (d > r && y >= air + 2) {
                            s = Blocks.AIR.defaultBlockState();
                        } else if (d <= r && y >= air + 12) {
                            // 元の天井と、その上 (戦う床の頭の上は 12 段まで元のまま)
                            s = Blocks.AIR.defaultBlockState();
                        }
                    } else {
                        // 谷と広間の空き。谷の底は巨神ごとの満たし
                        s = y < bottom + pal.fillDepth ? pal.fill : Blocks.AIR.defaultBlockState();
                    }
                    if (s != null) {
                        level.setBlock(p, s, flags);
                    }
                }
            }
        }
        if (inner) {
            return;
        }
        pillars(level, pal, cx, cz, bottom, top, r, gateDir, flags);
        details(level, theme, pal, cx, cz, air, bottom, top, r, gateDir, flags);
        SkyAbyss.LOGGER.info("Heart hall ({}) built around {}, {} in {} ms", theme, cx, cz, (System.nanoTime() - t0) / 1_000_000L);
    }

    static boolean ring(double d, double at) {
        return Math.abs(d - at) < 0.7;
    }

    // 外壁の際の太い柱 (3 x 3)。帯と、高さの半ばの灯り
    static void pillars(ServerLevel level, Palette pal, int cx, int cz, int bottom, int top, int r, int gateDir, int flags) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k < PILLARS; k++) {
            double a = Math.PI * 2 * (k + 0.5) / PILLARS;
            int px = cx + (int) Math.round(Math.cos(a) * (HALL_R - 3));
            int pz = cz + (int) Math.round(Math.sin(a) * (HALL_R - 3));
            if (Math.abs(px - cx) <= KEEP + 2 && (pz - cz) * gateDir > 0) {
                continue;
            }
            for (int y = bottom; y <= top + 2; y++) {
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        boolean edge = Math.abs(ox) + Math.abs(oz) == 2;
                        int h = y - bottom;
                        BlockState s = h % 7 == 6 ? pal.band : (edge ? pal.pillarEdge : pal.pillar);
                        if (h == DEPTH + 8 && !edge && (ox != 0 || oz != 0)) {
                            s = pal.light;
                        }
                        level.setBlock(p.set(px + ox, y, pz + oz), s, flags);
                    }
                }
            }
        }
    }

    // 巨神ごとの飾り
    static void details(ServerLevel level, Theme theme, Palette pal, int cx, int cz, int air, int bottom, int top, int r, int gateDir, int flags) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(cx * 31L + cz);
        switch (theme) {
            case TIDE -> {
                // 谷の水の中に昆布、壁の際に珊瑚の塊
                for (int i = 0; i < 90; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double d = r + 4 + rnd.nextDouble() * (HALL_R - r - 6);
                    int x = cx + (int) Math.round(Math.cos(a) * d);
                    int z = cz + (int) Math.round(Math.sin(a) * d);
                    if (bridgeAt(x - cx, z - cz, r, gateDir)) {
                        continue;
                    }
                    int h = 2 + rnd.nextInt(pal.fillDepth - 2);
                    for (int y = bottom; y < bottom + h; y++) {
                        level.setBlock(p.set(x, y, z), y == bottom + h - 1 ? Blocks.KELP.defaultBlockState() : Blocks.KELP_PLANT.defaultBlockState(), flags);
                    }
                }
                for (int i = 0; i < 40; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    int x = cx + (int) Math.round(Math.cos(a) * (HALL_R - 1));
                    int z = cz + (int) Math.round(Math.sin(a) * (HALL_R - 1));
                    int y = bottom + pal.fillDepth + rnd.nextInt(TOP);
                    BlockState coral = switch (rnd.nextInt(4)) {
                        case 0 -> Blocks.BRAIN_CORAL_BLOCK.defaultBlockState();
                        case 1 -> Blocks.TUBE_CORAL_BLOCK.defaultBlockState();
                        case 2 -> Blocks.FIRE_CORAL_BLOCK.defaultBlockState();
                        default -> Blocks.HORN_CORAL_BLOCK.defaultBlockState();
                    };
                    if (!bridgeAt(x - cx, z - cz, r, gateDir) && level.getBlockState(p.set(x, y, z)).isAir()) {
                        // 乾いた珊瑚は灰色に枯れるので、水の外は死んだ珊瑚の色を避けて方解石と海の灯りを混ぜる
                        level.setBlock(p, rnd.nextInt(3) == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.CALCITE.defaultBlockState(), flags);
                    }
                    if (y < bottom + pal.fillDepth) {
                        level.setBlock(p, coral, flags);
                    }
                }
            }
            case STORM -> {
                // 欄干の避雷の針と、天井から下がる鎖と銅の灯
                for (int k = 0; k < 24; k++) {
                    double a = Math.PI * 2 * k / 24;
                    int x = cx + (int) Math.round(Math.cos(a) * (r + 1.5));
                    int z = cz + (int) Math.round(Math.sin(a) * (r + 1.5));
                    if (!bridgeAt(x - cx, z - cz, r, gateDir)) {
                        level.setBlock(p.set(x, air + 2, z), Blocks.LIGHTNING_ROD.defaultBlockState(), flags);
                    }
                }
                for (int k = 0; k < 14; k++) {
                    double a = Math.PI * 2 * k / 14 + 0.2;
                    double d = r + 5 + (k % 3) * 3;
                    hang(level, p, cx + (int) Math.round(Math.cos(a) * d), cz + (int) Math.round(Math.sin(a) * d), top, 6 + (k % 4) * 2,
                            Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, true), flags, r, gateDir, cx, cz);
                }
            }
            case EMBER -> {
                // 天井から下がる鎖と灯籠、溶岩の上に浮く玄武岩の柱の折れ
                for (int k = 0; k < 14; k++) {
                    double a = Math.PI * 2 * k / 14 + 0.1;
                    double d = r + 5 + (k % 3) * 3;
                    hang(level, p, cx + (int) Math.round(Math.cos(a) * d), cz + (int) Math.round(Math.sin(a) * d), top, 5 + (k % 4) * 2,
                            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), flags, r, gateDir, cx, cz);
                }
                for (int i = 0; i < 22; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double d = r + 5 + rnd.nextDouble() * (HALL_R - r - 9);
                    int x = cx + (int) Math.round(Math.cos(a) * d);
                    int z = cz + (int) Math.round(Math.sin(a) * d);
                    if (bridgeAt(x - cx, z - cz, r, gateDir)) {
                        continue;
                    }
                    int h = pal.fillDepth + 1 + rnd.nextInt(6);
                    for (int y = bottom; y < bottom + h; y++) {
                        level.setBlock(p.set(x, y, z), y == bottom + h - 1 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BASALT.defaultBlockState(), flags);
                    }
                }
            }
        }
    }

    static boolean bridgeAt(int dx, int dz, int r, int gateDir) {
        return Math.abs(dx) <= KEEP + 1 && dz * gateDir > r - 2;
    }

    static void hang(ServerLevel level, BlockPos.MutableBlockPos p, int x, int z, int top, int len, BlockState lamp, int flags, int r, int gateDir, int cx, int cz) {
        if (bridgeAt(x - cx, z - cz, r, gateDir)) {
            return;
        }
        for (int i = 0; i < len; i++) {
            level.setBlock(p.set(x, top + 2 - i, z), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y), flags);
        }
        level.setBlock(p.set(x, top + 2 - len, z), lamp, flags);
    }

    record Palette(BlockState wall, BlockState band, BlockState ceiling, BlockState light, BlockState floor, BlockState fill, int fillDepth,
                   BlockState pedestal, BlockState pillar, BlockState pillarEdge, BlockState rail, BlockState railLight) {
        static Palette of(Theme t) {
            return switch (t) {
                case TIDE -> new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                        Blocks.SEA_LANTERN.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.WATER.defaultBlockState(), 8,
                        Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(),
                        Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState());
                case STORM -> new Palette(Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CUT_COPPER.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState(),
                        Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, true),
                        Blocks.WHITE_CONCRETE_POWDER.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), 2,
                        Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(),
                        Blocks.WAXED_CUT_COPPER.defaultBlockState(), Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, true));
                case EMBER -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                        Blocks.BLACKSTONE.defaultBlockState(), Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.LAVA.defaultBlockState(), 4,
                        Blocks.BASALT.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                        Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState());
            };
        }
    }
}
