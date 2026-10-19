package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 心臓の回廊と心臓の間 (最終ボスの部屋)。
// 宝物庫の奥の扉 (z=-45) から北へ回廊が伸び、途中の休み場 heart を過ぎると、円形の大広間に出る。
//  - 広間の中心は (0, 94, -85)。内側の半径 19、壁の外側 21。床は y=93、天井は y=106
//    (体の表面が y=106 あたりなので、天井の高さは 12 マスになる。体の外へ出ないための限界)
//  - 壁・天井・床は、赤土器・ネザーイボ・泥煉瓦の肉の質感。紫水晶と海のランタンが脈の光になる
//  - 骨の肋骨が壁から天井へ反り上がる。中ほどに柱があり、身を隠せる
//  - 床の溝に、わずかにマグマを置く
// 入口 (HEART_GATE) は、戦いが始まると TitanBackTrials が封じる。ここでは開けたまま作る
final class HeartArenaBuilder {
    // 円の内側と、壁の外側の二乗距離 (19^2 と 21^2 を整数で)。自己テストも使う
    static final int INNER_R2 = 625;
    static final int OUTER_R2 = 729;
    static final int EXTENT = 28;
    private static final int ARENA_TOP = HEART.ceilingY();

    private HeartArenaBuilder() {
    }

    static void build(BuildKit kit) {
        corridor(kit);
        heartRest(kit);
        arena(kit);
        ribs(kit);
        pillars(kit);
        // 入口の通路 (壁の厚さの分をくり抜く)。封印は TitanBackTrials が戦いのたびに出し入れする
        for (int x = -3; x <= 3; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 6; y++) {
                for (int z = HEART_GATE_Z - 1; z <= HEART_GATE_Z + 1; z++) {
                    kit.set(x, y, z, BuildKit.AIR);
                }
            }
        }
        // 最後の試練の入口の一言 (入ると扉が閉じることだけは、入る前に知らせる)
        kit.wallSign(HEART_GATE_SIGN.pos(), HEART_GATE_SIGN.facing(), HEART_GATE_SIGN.key());
    }

    // 回廊: 奥の扉 (z=-45) から心臓の間の入口 (z=-64) まで。幅3、高さ4
    private static void corridor(BuildKit kit) {
        RandomSource r = kit.random;
        for (int z = HEART_GATE_Z + 1; z <= HEART_CORRIDOR.z2(); z++) {
            for (int x = -4; x <= 4; x++) {
                for (int y = FLOOR; y <= FLOOR + 7; y++) {
                    boolean inside = Math.abs(x) <= 3 && y >= FLOOR + 1 && y <= FLOOR + 6;
                    kit.set(x, y, z, inside ? BuildKit.AIR : flesh(r));
                }
            }
            // 天井の骨の梁
            if ((z & 3) == 0) {
                for (int x = -4; x <= 4; x++) {
                    kit.set(x, FLOOR + 7, z, Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
        // 側壁の脈の光
        for (int z = HEART_CORRIDOR.z2() - 3; z > HEART_GATE_Z + 3; z -= 6) {
            kit.set(-4, FLOOR + 3, z, Blocks.SEA_LANTERN.defaultBlockState());
            kit.set(4, FLOOR + 3, z, Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    // 休み場 heart: 回廊の途中を横に広げた部屋。焚き火と腰掛け、碑文
    private static void heartRest(BuildKit kit) {
        RandomSource r = kit.random;
        for (int x = HEART_REST.x1() - 1; x <= HEART_REST.x2() + 1; x++) {
            for (int z = HEART_REST.z1(); z <= HEART_REST.z2(); z++) {
                for (int y = FLOOR; y <= HEART_REST.ceilingY(); y++) {
                    boolean inside = Math.abs(x) <= HEART_REST.x2() && y > FLOOR && y < HEART_REST.ceilingY();
                    if (inside) {
                        kit.set(x, y, z, BuildKit.AIR);
                    } else if (Math.abs(x) == HEART_REST.x2() + 1 || y == FLOOR || y == HEART_REST.ceilingY()) {
                        kit.set(x, y, z, flesh(r));
                    }
                }
            }
        }
        kit.restPoint(HEART_REST);
    }

    // 心臓の間: 円の内側は空けて床・天井を張り、壁の外側の輪は肉の壁にする
    private static void arena(BuildKit kit) {
        RandomSource r = kit.random;
        for (int dx = -EXTENT; dx <= EXTENT; dx++) {
            for (int dz = -EXTENT; dz <= EXTENT; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > OUTER_R2) {
                    continue;
                }
                int x = HEART_CX + dx;
                int z = HEART_CZ + dz;
                if (d2 <= INNER_R2) {
                    kit.set(x, FLOOR, z, floorBlock(r, dx, dz));
                    for (int y = FLOOR + 1; y < ARENA_TOP; y++) {
                        kit.set(x, y, z, BuildKit.AIR);
                    }
                    kit.set(x, ARENA_TOP, z, ceilingBlock(r));
                } else {
                    for (int y = FLOOR; y <= ARENA_TOP; y++) {
                        kit.set(x, y, z, wallBlock(r, x, y, z));
                    }
                }
            }
        }
    }

    // 床: 肉の床に、マグマの溝 (斜めに点々と) と、わずかな脈の光
    private static BlockState floorBlock(RandomSource r, int dx, int dz) {
        if (Math.abs(dx) == Math.abs(dz) && (Math.abs(dx) == 7 || Math.abs(dx) == 14 || Math.abs(dx) == 21)) {
            return Blocks.MAGMA_BLOCK.defaultBlockState();
        }
        if (((HEART_CX + dx) * 7 + (HEART_CZ + dz) * 3) % 23 == 0 && r.nextFloat() < 0.5f) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        float f = r.nextFloat();
        return f < 0.4f ? Blocks.MUD_BRICKS.defaultBlockState()
                : f < 0.6f ? Blocks.RED_TERRACOTTA.defaultBlockState()
                : f < 0.75f ? Blocks.NETHER_WART_BLOCK.defaultBlockState()
                : Blocks.BROWN_TERRACOTTA.defaultBlockState();
    }

    private static BlockState ceilingBlock(RandomSource r) {
        float f = r.nextFloat();
        if (f < 0.05f) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        return f < 0.5f ? Blocks.MUD_BRICKS.defaultBlockState() : Blocks.NETHER_WART_BLOCK.defaultBlockState();
    }

    // 壁: 肉の質感。中ほどの高さに紫水晶 (脈の結晶) と海のランタンを散らす
    private static BlockState wallBlock(RandomSource r, int x, int y, int z) {
        if (y >= FLOOR + 6 && y <= FLOOR + 11) {
            if ((x + z) % 9 == 0) {
                return Blocks.AMETHYST_BLOCK.defaultBlockState();
            }
            if ((x * 3 + z) % 13 == 0) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
        }
        return flesh(r);
    }

    // 肉の質感の壁材 (赤土器・ネザーイボ・泥煉瓦を混ぜる)
    private static BlockState flesh(RandomSource r) {
        float f = r.nextFloat();
        return f < 0.45f ? Blocks.RED_TERRACOTTA.defaultBlockState()
                : f < 0.65f ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                : f < 0.82f ? Blocks.NETHER_WART_BLOCK.defaultBlockState()
                : Blocks.MUD_BRICKS.defaultBlockState();
    }

    // 骨の肋骨: 壁ぎわから天井の中ほどへ反り上がる弧 (x 方向の平面に立つ)。弧の下は通れる
    private static void ribs(BuildKit kit) {
        for (int offset : new int[]{-23, -19, -15, -11, -7, 7, 11, 15, 19, 23}) {
            int x = HEART_CX + offset;
            int reach = (int) Math.floor(Math.sqrt(INNER_R2 - offset * offset));
            int prevY = Integer.MIN_VALUE;
            for (int dz = -reach; dz <= reach; dz++) {
                double t = dz / (reach + 0.5);
                int y = FLOOR + 1 + (int) Math.round(13 * Math.sqrt(Math.max(0, 1 - t * t)));
                int from = prevY == Integer.MIN_VALUE ? y : Math.min(prevY, y);
                int to = prevY == Integer.MIN_VALUE ? y : Math.max(prevY, y);
                for (int yy = from; yy <= to; yy++) {
                    kit.set(x, yy, HEART_CZ + dz, Blocks.BONE_BLOCK.defaultBlockState());
                }
                prevY = y;
            }
        }
    }

    // 身を隠せる柱。中心 (半径3ほど) は空けておく
    private static void pillars(BuildKit kit) {
        int[][] at = {{-9, -9}, {9, 9}, {-14, 4}, {14, -4}, {-19, -14}, {19, 14}, {-4, 20}, {4, -20}, {-16, 15}, {16, -15}};
        for (int[] p : at) {
            int x = HEART_CX + p[0];
            int z = HEART_CZ + p[1];
            for (int y = FLOOR + 1; y <= FLOOR + 9; y++) {
                kit.set(x, y, z, Blocks.MUD_BRICKS.defaultBlockState());
            }
            kit.set(x, FLOOR + 10, z, Blocks.BONE_BLOCK.defaultBlockState());
        }
    }
}
