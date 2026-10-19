package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import jp.revati.skyabyss.tablet.Tablets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;

// 岩鯨の背を建てるための道具箱。部屋を掘る・壁に柱を立てる・天井に骨の梁を渡す・灯りを吊るす、などの部品
final class BuildKit {
    static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    // 体内の石組みの基本の材質
    static final BlockState BRICKS = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState CRACKED = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    final RandomSource random;

    BuildKit(ServerLevel level, RandomSource random) {
        this.level = level;
        this.random = random;
    }

    ServerLevel level() {
        return level;
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, UPDATE);
    }

    void set(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, UPDATE);
    }

    BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    // 壁の石は、ところどころひび割れさせて古びた感じにする
    BlockState wallStone() {
        return random.nextFloat() < 0.18f ? CRACKED : BRICKS;
    }

    // 内側を空にし、外周1マスを壁・床・天井で覆う。すでに掘った空間はふさがない
    void room(Box box, BlockState floor, BlockState ceiling) {
        for (int x = box.x1() - 1; x <= box.x2() + 1; x++) {
            for (int z = box.z1() - 1; z <= box.z2() + 1; z++) {
                for (int y = box.floorY(); y <= box.ceilingY(); y++) {
                    boolean inside = x >= box.x1() && x <= box.x2() && z >= box.z1() && z <= box.z2()
                            && y > box.floorY() && y < box.ceilingY();
                    if (inside) {
                        set(x, y, z, AIR);
                    } else if (!get(x, y, z).isAir()) {
                        set(x, y, z, y == box.floorY() ? floor : y == box.ceilingY() ? ceiling : wallStone());
                    }
                }
            }
        }
    }

    void room(Box box) {
        room(box, POLISHED, TILES);
    }

    // 戸口を開ける (壁に x1〜x2 × z1〜z2 × 高さ height の穴)
    void doorway(int x1, int x2, int z1, int z2, int floorY, int height) {
        fill(x1, floorY + 1, z1, x2, floorY + height, z2, AIR);
    }

    // 壁ぎわの柱: 一定の間隔で、壁に沿って立つ磨き石の柱と、彫刻の柱頭。
    // 戸口・封印・はしごの前には立てない (壁の向こうが空いている所、封印のある所、足元が埋まっている所は飛ばす)
    void pilasters(Box box, int spacing) {
        int top = box.ceilingY() - 1;
        for (int x = box.x1() + 2; x < box.x2() - 1; x += spacing) {
            column(x, box.z1(), x, box.z1() - 1, box.floorY() + 1, top);
            column(x, box.z2(), x, box.z2() + 1, box.floorY() + 1, top);
        }
        for (int z = box.z1() + 2; z < box.z2() - 1; z += spacing) {
            column(box.x1(), z, box.x1() - 1, z, box.floorY() + 1, top);
            column(box.x2(), z, box.x2() + 1, z, box.floorY() + 1, top);
        }
    }

    private void column(int x, int z, int behindX, int behindZ, int y1, int y2) {
        for (int y = y1; y <= y1 + 3; y++) {
            BlockState behind = get(behindX, y, behindZ);
            if (behind.isAir() || behind.is(ModBlocks.SEALED_GATE.get()) || !get(x, y, z).isAir()) {
                return;
            }
        }
        for (int y = y1; y <= y2; y++) {
            set(x, y, z, y == y2 ? CHISELED : y == y1 ? TILES : POLISHED);
        }
    }

    // 壁に埋め込んだ灯り (巨神の体の中で淡く光る、海の灯り)。一定の間隔で、高さ y の壁の石を置き換える。
    // 戸口・封印・柱の前は避ける
    void wallLamps(Box box, int spacing, int y) {
        BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
        for (int x = box.x1() + 1; x < box.x2(); x += spacing) {
            lamp(x, y, box.z1() - 1, x, box.z1(), lamp);
            lamp(x, y, box.z2() + 1, x, box.z2(), lamp);
        }
        for (int z = box.z1() + 1; z < box.z2(); z += spacing) {
            lamp(box.x1() - 1, y, z, box.x1(), z, lamp);
            lamp(box.x2() + 1, y, z, box.x2(), z, lamp);
        }
    }

    private void lamp(int wx, int y, int wz, int ix, int iz, BlockState lamp) {
        BlockState wall = get(wx, y, wz);
        boolean isWall = wall.is(Blocks.DEEPSLATE_BRICKS) || wall.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                || wall.is(Blocks.BASALT) || wall.is(Blocks.BLACKSTONE) || wall.is(Blocks.DEEPSLATE_TILES);
        if (isWall && get(ix, y, iz).isAir()) {
            set(wx, y, wz, lamp);
        }
    }

    // 天井に渡す、鯨の肋骨のような骨の梁
    void ribs(Box box, int spacing, boolean alongZ) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongZ ? Direction.Axis.X : Direction.Axis.Z);
        int y = box.ceilingY() - 1;
        if (alongZ) {
            for (int z = box.z1() + 2; z < box.z2(); z += spacing) {
                for (int x = box.x1(); x <= box.x2(); x++) {
                    // 両端は1段下げて弧に見せる
                    int yy = (x == box.x1() || x == box.x2()) ? y - 1 : y;
                    set(x, yy, z, bone);
                }
            }
        } else {
            for (int x = box.x1() + 2; x < box.x2(); x += spacing) {
                for (int z = box.z1(); z <= box.z2(); z++) {
                    int yy = (z == box.z1() || z == box.z2()) ? y - 1 : y;
                    set(x, yy, z, bone);
                }
            }
        }
    }

    // 床の縁取りと、中央の模様
    void floorBorder(Box box) {
        for (int x = box.x1(); x <= box.x2(); x++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                boolean edge = x == box.x1() || x == box.x2() || z == box.z1() || z == box.z2();
                if (edge && !get(x, box.floorY(), z).isAir()) {
                    set(x, box.floorY(), z, TILES);
                }
            }
        }
    }

    // 天井から鎖で吊るした魂のランタン
    void hangingLantern(int x, int z, int ceilingY, int chainLength) {
        for (int i = 1; i <= chainLength; i++) {
            set(x, ceilingY - i, z, Blocks.CHAIN.defaultBlockState());
        }
        set(x, ceilingY - chainLength - 1, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    void lantern(int x, int y, int z) {
        set(x, y, z, Blocks.SOUL_LANTERN.defaultBlockState());
    }

    // 天井から垂れる鍾乳石
    void stalactite(int x, int ceilingY, int z, int length) {
        for (int i = 1; i <= length; i++) {
            DripstoneThickness t = i == length ? DripstoneThickness.TIP : i == length - 1 ? DripstoneThickness.FRUSTUM
                    : i == 1 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            set(x, ceilingY - i, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, t));
        }
    }

    // 床にところどころ苔を散らす
    void moss(Box box, float chance) {
        for (int x = box.x1(); x <= box.x2(); x++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                int y = box.floorY() + 1;
                BlockState below = get(x, y - 1, z);
                if (random.nextFloat() < chance && get(x, y, z).isAir() && !below.isAir() && !below.is(Blocks.LADDER)) {
                    set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
        }
    }

    // 休み場の設え: 青い焚き火、腰掛け、灯り。案内の看板は置かない (着いたことは焚き火と音で、先の様子は最初の一度だけの囁きで伝える: BackWhispers)
    void restPoint(Box box) {
        restPoint(box, false);
    }

    // smokeless = true: 焚き火の代わりに、煙の出ない「魂の火鉢」(壁の柱の上の魂のランタン。まわりに灯したろうそく)。
    // 閉じた部屋で焚き火の煙が壁や掲示板を覆うのを避ける。しゃがんで触れるとあきらめる働きは焚き火と同じ (TitanBackTrials)
    void restPoint(Box box, boolean smokeless) {
        BlockPos c = box.center();
        set(c.getX(), box.floorY(), c.getZ(), CHISELED);
        if (smokeless) {
            set(c.getX(), box.floorY() + 1, c.getZ(), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
            set(c.getX(), box.floorY() + 2, c.getZ(), Blocks.SOUL_LANTERN.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                set(c.getX() + d.getStepX(), box.floorY() + 1, c.getZ() + d.getStepZ(),
                        Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
            }
        } else {
            set(c.getX(), box.floorY() + 1, c.getZ(), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int bx = c.getX() + d.getStepX() * 2;
            int bz = c.getZ() + d.getStepZ() * 2;
            if (bx > box.x1() && bx < box.x2() && bz > box.z1() && bz < box.z2() && get(bx, box.floorY() + 1, bz).isAir()) {
                set(bx, box.floorY() + 1, bz, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM));
            }
        }
        set(box.x1(), box.floorY() + 1, box.z1(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        set(box.x2(), box.floorY() + 1, box.z2(), Blocks.AMETHYST_CLUSTER.defaultBlockState());
        hangingLantern(c.getX() + 1, c.getZ() + 1, box.ceilingY(), 1);
    }

    // 碑 (看板の代わり)。文のキーは lang の "<key>.1" 〜 "<key>.4"
    void wallSign(BlockPos pos, Direction facing, String key) {
        set(pos, Tablets.wallState(facing));
        Tablets.write(level, pos, key);
    }

    void standingSign(BlockPos pos, String key) {
        set(pos, Tablets.standingState());
        Tablets.write(level, pos, key);
    }
}
