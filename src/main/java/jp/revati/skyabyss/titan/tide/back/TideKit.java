package jp.revati.skyabyss.titan.tide.back;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

// 潮殻の背を建てる道具箱 (岩鯨の BuildKit の潮殻版)。潮の民の石組み: 海の石のレンガ・暗い海の石・方解石 (真珠層)・平らな砂岩
public final class TideKit {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState BRICK = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    public static final BlockState DARK = Blocks.DARK_PRISMARINE.defaultBlockState();
    public static final BlockState ROUGH = Blocks.PRISMARINE.defaultBlockState();
    public static final BlockState NACRE = Blocks.CALCITE.defaultBlockState();
    public static final BlockState FLOOR = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
    public static final BlockState SAND = Blocks.SAND.defaultBlockState();
    public static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    public static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    public static final BlockState SHELL = Blocks.TUFF.defaultBlockState();
    public static final BlockState SHELL_DARK = Blocks.DEEPSLATE_TILES.defaultBlockState();

    private final ServerLevel level;
    final RandomSource random;

    public TideKit(ServerLevel level, RandomSource random) {
        this.level = level;
        this.random = random;
    }

    public ServerLevel level() {
        return level;
    }

    public void set(int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    public void set(BlockPos p, BlockState s) {
        level.setBlock(p, s, FLAGS);
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    // 壁の石: ところどころ暗い石と真珠層の筋
    public BlockState wall(int x, int y, int z) {
        int h = Math.floorMod(x * 7 + y * 13 + z * 5, 23);
        if (h == 0) {
            return NACRE;
        }
        if (h < 4) {
            return ROUGH;
        }
        return (y % 5 == 0) ? DARK : BRICK;
    }

    // 箱の部屋: 内側を空け、外周 1 マスを壁・床・天井にする (すでに空いている所の外周はふさがない)
    public void room(int x1, int x2, int z1, int z2, int floor, int height, BlockState floorState) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                for (int y = floor; y <= floor + height + 1; y++) {
                    boolean in = x >= x1 && x <= x2 && z >= z1 && z <= z2 && y > floor && y <= floor + height;
                    if (in) {
                        set(x, y, z, AIR);
                    } else if (!get(x, y, z).isAir() || y == floor) {
                        set(x, y, z, y == floor ? floorState : wall(x, y, z));
                    }
                }
            }
        }
    }

    public void room(TideBackLayout.Area a, BlockState floorState) {
        room(a.x1(), a.x2(), a.z1(), a.z2(), a.floor(), a.height(), floorState);
    }

    // 階段 (登る向き facing)
    public void stair(int x, int y, int z, Direction facing, BlockState base) {
        set(x, y, z, base.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
    }

    // 休み場の焚き火 (青い焚き火。しゃがんで触れると、試練をあきらめて浜へ戻る)
    public void campfire(BlockPos p) {
        set(p.below(), Blocks.CHISELED_SANDSTONE.defaultBlockState());
        set(p, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
        set(p.above(), AIR);
    }

    public void wallSign(BlockPos pos, Direction facing, String key) {
        set(pos, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        writeSign(pos, key, DyeColor.CYAN);
    }

    // 看板に 4 行。キーは lang の "<key>.1"〜"<key>.4"。古い碑文らしく、光る青緑の字
    public void writeSign(BlockPos pos, String key, DyeColor color) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(color).setHasGlowingText(true);
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i, Component.translatable(key + "." + (i + 1)));
            }
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }
}
