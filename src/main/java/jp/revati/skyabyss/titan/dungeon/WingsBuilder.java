package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 岩鯨の背の任意の翼と見せ場 (版 13。21 の 3.3 と 26 の 3.1): 骨の書庫、歌の祠、左右の胸びれの道、尾の展望、背の外の道の宿。
// どれも必須の道ではない (初回も周回もいつも開いている)。ボスの戸と鐘楼の戸のほかに、状態の鍵はかけない
final class WingsBuilder {
    private WingsBuilder() {
    }

    static void build(BuildKit kit) {
        library(kit);
        songShrine(kit);
        finPath(kit, -1);
        finPath(kit, 1);
        tailVista(kit);
        camp(kit);
        trailLamps(kit);
    }

    // ---------------------------------------------------------------- 骨の書庫 (入口の通路の西。十二の壁龕)
    // 壁龕の碑は sign.skyabyss.library_1〜12。最後 (12) の壁龕は書棚で、右クリックで追憶の宝 (人ごとに一度) と素材 (何度でも)
    static final int NICHES = 12;

    static BlockPos nichePos(int n) {
        // 1〜6 = 北の壁 (東向きに数える)、7〜12 = 南の壁
        int k = (n - 1) % 6;
        int x = LIBRARY.x1() + 2 + k * 4;
        int z = n <= 6 ? LIBRARY.z1() - 1 : LIBRARY.z2() + 1;
        return new BlockPos(x, FLOOR + 3, z);
    }

    static Direction nicheFacing(int n) {
        return n <= 6 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void library(BuildKit kit) {
        kit.room(LIBRARY);
        kit.room(LIBRARY_PASS);
        kit.ribs(LIBRARY, 5, true);
        kit.floorBorder(LIBRARY);
        kit.pilasters(LIBRARY, 4);
        kit.wallLamps(LIBRARY, 5, FLOOR + 6);
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int n = 1; n <= NICHES; n++) {
            BlockPos p = nichePos(n);
            Direction out = nicheFacing(n);
            // 壁龕: 壁を一つ掘り、骨の枠で囲み、碑 (n = 12 は書棚) を掛ける
            for (int dy = -1; dy <= 1; dy++) {
                kit.set(p.getX(), p.getY() + dy, p.getZ(), BuildKit.AIR);
            }
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dy = -1; dy <= 1; dy++) {
                    kit.set(p.getX() + dx, p.getY() + dy, p.getZ(), bone);
                }
            }
            kit.set(p.getX(), p.getY() + 2, p.getZ(), bone);
            kit.set(p.getX(), p.getY() - 2, p.getZ(), bone);
            if (n == NICHES) {
                kit.set(p, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            } else {
                kit.wallSign(p, out, "sign.skyabyss.library_" + n);
            }
            // 壁龕の前の足もとに灯り
            BlockPos front = p.relative(out, 2);
            kit.lantern(front.getX(), FLOOR + 1, front.getZ());
        }
        // 中ほどの読書台 (机の代わりの石の台と蝋燭)
        for (int x = LIBRARY.x1() + 6; x <= LIBRARY.x2() - 6; x += 5) {
            int z = (LIBRARY.z1() + LIBRARY.z2()) / 2;
            kit.fill(x, FLOOR + 1, z - 1, x + 1, FLOOR + 1, z + 1, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
            kit.set(x, FLOOR + 2, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        }
        kit.moss(LIBRARY, 0.04f);
        kit.hangingLantern(LIBRARY.x1() + 4, LIBRARY.z1() + 6, LIBRARY.ceilingY(), 2);
        kit.hangingLantern(LIBRARY.x2() - 4, LIBRARY.z1() + 6, LIBRARY.ceilingY(), 2);
        kit.hangingLantern(LIBRARY.x1() + 4, LIBRARY.z2() - 6, LIBRARY.ceilingY(), 2);
        kit.hangingLantern(LIBRARY.x2() - 4, LIBRARY.z2() - 6, LIBRARY.ceilingY(), 2);
    }

    // ---------------------------------------------------------------- 歌の祠 (心臓の前の静かな部屋)
    private static void songShrine(BuildKit kit) {
        kit.room(SONG_SHRINE);
        kit.room(SONG_PASS);
        kit.ribs(SONG_SHRINE, 4, true);
        kit.floorBorder(SONG_SHRINE);
        BlockPos c = SONG_SHRINE.center();
        kit.set(c.getX(), FLOOR, c.getZ(), BuildKit.CHISELED);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            kit.set(c.getX() + d.getStepX() * 2, FLOOR + 1, c.getZ() + d.getStepZ() * 2,
                    Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true));
        }
        kit.set(c.getX(), FLOOR + 1, c.getZ(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        kit.hangingLantern(c.getX(), c.getZ(), SONG_SHRINE.ceilingY(), 2);
        kit.standingSign(new BlockPos(c.getX(), FLOOR + 1, SONG_SHRINE.z1() + 2), "sign.skyabyss.song_shrine");
        kit.wallLamps(SONG_SHRINE, 4, FLOOR + 4);
        kit.moss(SONG_SHRINE, 0.03f);
    }

    // ---------------------------------------------------------------- 胸びれの道 (側 side = -1 左 / +1 右)
    // 左は第一の休み場 W1、右は控えの間 CE1 から、体の外へ抜けるトンネルを通り、段を下りて、ひれの上の道 (幅 5、低い欄干) を先端の台まで。
    // 道は斜め (ひれの付け根 → 先端) に走り、道の高さは隣と 1 ずつしか違わない。先端の台に焚き火の休み場と碑
    static final int FIN_Z_A = -62;
    static final int FIN_Z_B = -34;
    static final int FIN_LEN = 72;

    static int finTunnelFloor(int side) {
        return side < 0 ? W1.floorY() : FLOOR;
    }

    static int finWallX(int side) {
        return side < 0 ? W1.x1() - 1 : CE1.x2() + 1;
    }

    // 胸びれの道の計画 (建てる前に、体の形だけから決まる): 列 i の x・z の中心・床のブロックの y と、面のある最後の列
    record Plan(int[] xs, int[] zs, int[] hs, int last) {
    }

    static int finExitX(int side) {
        int tf = finTunnelFloor(side);
        int wallX = finWallX(side);
        int exitX = wallX;
        while (Math.abs(exitX - wallX) < 40 && WhaleShape.at(exitX, tf + 3, -62) != null) {
            exitX += side;
        }
        return exitX;
    }

    static Plan plan(int side, int exitX, int tf) {
        // 外の道: 体の外へ出た所 (exitX) から、斜めにひれの先端まで
        int n = FIN_LEN;
        int[] hs = new int[n + 1];
        int[] xs = new int[n + 1];
        int[] zs = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            xs[i] = exitX + side * (i + 1);
            zs[i] = FIN_Z_A + (int) Math.round((FIN_Z_B - FIN_Z_A) * (double) i / n);
        }
        // 道の高さの目標: 道の幅の中のいちばん高い面 (ひれの上。体の面は無い所)。無ければ前の高さ
        for (int i = 0; i <= n; i++) {
            int best = Integer.MIN_VALUE;
            for (int dz = -2; dz <= 2; dz++) {
                int t = WhaleShape.top(xs[i], zs[i] + dz);
                if (t != Integer.MIN_VALUE && t < tf - 2) {
                    best = Math.max(best, t);
                }
            }
            hs[i] = best;
        }
        // 先端: 面のある最後の i
        int last = 0;
        for (int i = 0; i <= n; i++) {
            if (hs[i] != Integer.MIN_VALUE) {
                last = i;
            }
        }
        // 面の無い所は前の高さ - 1 の斜めで埋め、1 ずつしか違わないようにならす
        int prev = tf;
        for (int i = 0; i <= last; i++) {
            int target = hs[i] == Integer.MIN_VALUE ? prev - 1 : hs[i];
            hs[i] = Math.max(target, prev - 1);
            hs[i] = Math.min(hs[i], prev);
            prev = hs[i];
        }
        // 低い所から上へ (先端が根もとより上がることはないが、念のため後ろからも 1 ずつに)
        for (int i = last - 1; i >= 0; i--) {
            hs[i] = Math.min(hs[i], hs[i + 1] + 1);
        }
        return new Plan(xs, zs, hs, last);
    }

    // 先端の台の中心 (焚き火の位置の一歩手前。監査の行き先)
    public static BlockPos finTip(int side) {
        Plan plan = plan(side, finExitX(side), finTunnelFloor(side));
        return new BlockPos(plan.xs()[plan.last()] + 3 * side, plan.hs()[plan.last()] + 1, plan.zs()[plan.last()] + 2);
    }

    private static void finPath(BuildKit kit, int side) {
        int tf = finTunnelFloor(side);
        int wallX = finWallX(side);
        // トンネル: 壁から、体の外に出るまで (幅 5 = z -64〜-60、高さ 6)
        int exitX = wallX;
        while (Math.abs(exitX - wallX) < 40 && WhaleShape.at(exitX, tf + 3, -62) != null) {
            exitX += side;
        }
        int ix1 = Math.min(wallX, exitX);
        int ix2 = Math.max(wallX, exitX);
        for (int x = ix1; x <= ix2; x++) {
            for (int z = -65; z <= -59; z++) {
                boolean inner = z >= -64 && z <= -60;
                for (int y = tf; y <= tf + 7; y++) {
                    if (inner && y > tf && y <= tf + 6) {
                        kit.set(x, y, z, BuildKit.AIR);
                    } else if (!kit.get(x, y, z).isAir()) {
                        kit.set(x, y, z, y == tf ? BuildKit.POLISHED : y == tf + 7 ? BuildKit.TILES : kit.wallStone());
                    }
                }
            }
            if (Math.abs(x - wallX) % 4 == 2 && x != exitX) {
                kit.lantern(x, tf + 1, -64);
                kit.lantern(x, tf + 1, -60);
            }
        }
        Plan plan = plan(side, exitX, tf);
        int n = FIN_LEN;
        int[] xs = plan.xs();
        int[] zs = plan.zs();
        int[] hs = plan.hs();
        int last = plan.last();
        Direction up = side < 0 ? Direction.EAST : Direction.WEST;
        BlockState stair = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up);
        BlockState rail = Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState();
        // 階段の置き場 (柵は、階段の隣に置かない: 階段の低い側が柵だと床に埋まった階段に見える)
        java.util.Set<BlockPos> stairAt = new java.util.HashSet<>();
        for (int i = 0; i <= last; i++) {
            boolean drops = i == 0 ? hs[i] < tf : hs[i] < hs[i - 1];
            if (drops) {
                for (int dz = -2; dz <= 2; dz++) {
                    stairAt.add(new BlockPos(xs[i], hs[i] + 1, zs[i] + dz));
                }
            }
        }
        for (int i = 0; i <= last; i++) {
            int x = xs[i];
            int h = hs[i];
            boolean drops = i == 0 ? h < tf : h < hs[i - 1];
            for (int dz = -2; dz <= 2; dz++) {
                int z = zs[i] + dz;
                // 支えの石 (道の下を 3 つ)
                kit.fill(x, h - 3, z, x, h - 1, z, BuildKit.BRICKS);
                kit.set(x, h, z, (x + z) % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
                if (drops) {
                    // 1 段下がる所: 低い列の床 (h) の上に半段の階段を載せる。階段の背の高い側は、前の列の床 (同じ y の丸ごとのブロック)
                    kit.set(x, h + 1, z, stair);
                    kit.fill(x, h + 2, z, x, h + 7, z, BuildKit.AIR);
                } else {
                    kit.fill(x, h + 1, z, x, h + 6, z, BuildKit.AIR);
                }
            }
            for (int dz : new int[]{-3, 3}) {
                int z = zs[i] + dz;
                kit.fill(x, h - 3, z, x, h, z, BuildKit.BRICKS);
                boolean nearStair = stairAt.contains(new BlockPos(x - 1, h + 1, z)) || stairAt.contains(new BlockPos(x + 1, h + 1, z));
                kit.set(x, h + 1, z, nearStair || drops ? BuildKit.AIR : rail);
                kit.fill(x, h + 2, z, x, h + 6, z, BuildKit.AIR);
            }
            if (i % 8 == 4) {
                kit.lantern(x, h + 1, zs[i] - 2);
                kit.lantern(x, h + 1, zs[i] + 2);
            }
        }
        // 先端の台 (7×7。道の終わりから外へ)。焚き火の休み場と、端の碑
        int px = xs[last];
        int pz = zs[last];
        int ph = hs[last];
        for (int dx = 0; dx <= 6; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = px + dx * side;
                int z = pz + dz;
                boolean border = dx == 6 || Math.abs(dz) == 3;
                kit.fill(x, ph - 3, z, x, ph - 1, z, BuildKit.BRICKS);
                kit.set(x, ph, z, border ? BuildKit.BRICKS : BuildKit.POLISHED);
                kit.fill(x, ph + 1, z, x, ph + 6, z, BuildKit.AIR);
                if (border && !(dx == 0 && Math.abs(dz) <= 2)) {
                    kit.set(x, ph + 1, z, rail);
                }
            }
        }
        kit.set(px + 3 * side, ph + 1, pz, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.standingSign(new BlockPos(px + 5 * side, ph + 1, pz + 1), side < 0 ? "sign.skyabyss.fin_left" : "sign.skyabyss.fin_right");
        kit.lantern(px + 5 * side, ph + 1, pz - 2);
    }

    // ---------------------------------------------------------------- 尾の展望 (背の南の端。背全体と雲の海が見える)
    static final int TAIL_CZ = 312;
    static final int TAIL_Y = 121;
    static final int TAIL_R = 12;

    private static void tailVista(BuildKit kit) {
        for (int dx = -TAIL_R; dx <= TAIL_R; dx++) {
            for (int dz = -TAIL_R; dz <= TAIL_R; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > TAIL_R) {
                    continue;
                }
                int x = dx;
                int z = TAIL_CZ + dz;
                kit.fill(x, TAIL_Y - 6, z, x, TAIL_Y - 1, z, BuildKit.BRICKS);
                boolean edge = d > TAIL_R - 1.2;
                kit.set(x, TAIL_Y, z, edge ? BuildKit.BRICKS : d < 3 ? BuildKit.CHISELED : (x + z) % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
                kit.fill(x, TAIL_Y + 1, z, x, TAIL_Y + 8, z, BuildKit.AIR);
                if (edge && !(Math.abs(dx) <= 2 && dz < 0)) {
                    kit.set(x, TAIL_Y + 1, z, Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.5;
            int x = (int) Math.round((TAIL_R - 2) * Math.cos(a));
            int z = TAIL_CZ + (int) Math.round((TAIL_R - 2) * Math.sin(a));
            kit.set(x, TAIL_Y + 1, z, BuildKit.POLISHED);
            kit.set(x, TAIL_Y + 2, z, BuildKit.POLISHED);
            kit.set(x, TAIL_Y + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        kit.set(-3, TAIL_Y + 1, TAIL_CZ, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        kit.set(3, TAIL_Y + 1, TAIL_CZ, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        kit.standingSign(new BlockPos(0, TAIL_Y + 1, TAIL_CZ + 1), "sign.skyabyss.tail_vista");
    }

    // ---------------------------------------------------------------- 背の外の道の宿 (広場から祠への踏み跡の途中。休み場 camp)
    // 折れた柱の野営跡、鎖の祭壇の跡、前の私の頁 (碑)。休み場の範囲は CAMP (TitanBackTrials)
    static final int CAMP_Z = 120;
    static final int CAMP_Y = 147;

    private static void camp(BuildKit kit) {
        for (int x = -6; x <= 6; x++) {
            for (int z = CAMP_Z - 6; z <= CAMP_Z + 6; z++) {
                kit.fill(x, CAMP_Y - 6, z, x, CAMP_Y - 1, z, BuildKit.BRICKS);
                boolean edge = Math.max(Math.abs(x), Math.abs(z - CAMP_Z)) == 6;
                kit.set(x, CAMP_Y, z, edge ? BuildKit.CRACKED : (x + z) % 2 == 0 ? BuildKit.POLISHED : BuildKit.TILES);
                kit.fill(x, CAMP_Y + 1, z, x, CAMP_Y + 10, z, BuildKit.AIR);
            }
        }
        // 折れた柱 (高さの違う骨の柱)
        int[][] pillars = {{-5, -5, 5}, {5, -5, 3}, {-5, 5, 2}, {5, 5, 6}};
        for (int[] p : pillars) {
            for (int y = 1; y <= p[2]; y++) {
                kit.set(p[0], CAMP_Y + y, CAMP_Z + p[1], y == p[2] ? Blocks.BONE_BLOCK.defaultBlockState() : BuildKit.POLISHED);
            }
        }
        // 鎖の祭壇の跡と焚き火
        kit.set(0, CAMP_Y + 1, CAMP_Z, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        kit.set(0, CAMP_Y + 2, CAMP_Z, Blocks.CHAIN.defaultBlockState());
        kit.set(2, CAMP_Y + 1, CAMP_Z, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        kit.set(3, CAMP_Y + 1, CAMP_Z, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
        kit.set(2, CAMP_Y + 1, CAMP_Z + 1, Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
        kit.standingSign(new BlockPos(-2, CAMP_Y + 1, CAMP_Z - 2), "sign.skyabyss.camp_page");
        kit.lantern(-3, CAMP_Y + 1, CAMP_Z + 3);
    }

    // 広場から祠へ、背の踏み跡に沿って、道しるべの灯りの柱 (24 マスごと。尾の展望のほうへも)
    private static void trailLamps(BuildKit kit) {
        for (int z = 180; z <= 296; z += 24) {
            for (int side : new int[]{-1, 1}) {
                int x = 7 * side;
                int base = top(x, z) + 1;
                kit.set(x, base, z, BuildKit.POLISHED);
                kit.set(x, base + 1, z, BuildKit.POLISHED);
                kit.set(x, base + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
    }

    // 外から使う: 休み場の範囲
    static List<Integer> campBounds() {
        return List.of(-6, 6, CAMP_Z - 6, CAMP_Z + 6, CAMP_Y);
    }
}
