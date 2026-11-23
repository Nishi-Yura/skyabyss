package jp.revati.skyabyss.relic;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

// 遺物ブロックの置き方と判定。ビルダーの `Blocks.CHEST` などを、巨神の様式つきのこれらに差し替える。
// 判定 (isCache など) は遺物ブロックだけを見る。野営で自分が置くバニラの箱・焚き火は別のまま
public final class RelicBlocks {
    private RelicBlocks() {
    }

    // ---------------------------------------------------------------- 置く
    public static BlockState cache(RelicStyle style, Direction facing) {
        return ModBlocks.RELIC_CACHE.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing).setValue(RelicStyle.PROP, style);
    }

    public static BlockState hearth(RelicStyle style, boolean lit) {
        return ModBlocks.REST_HEARTH.get().defaultBlockState().setValue(RestHearthBlock.LIT, lit).setValue(RelicStyle.PROP, style);
    }


    // 向き・面・入切は呼ぶ側が .setValue で足す (バニラの LEVER.defaultBlockState() の差し替え用)
    public static BlockState leverBase(RelicStyle style) {
        return ModBlocks.RELIC_LEVER.get().defaultBlockState().setValue(RelicStyle.PROP, style);
    }

    public static BlockState button(RelicStyle style, AttachFace face, Direction facing) {
        return ModBlocks.RELIC_BUTTON.get().defaultBlockState().setValue(ButtonBlock.FACE, face).setValue(ButtonBlock.FACING, facing)
                .setValue(RelicStyle.PROP, style);
    }

    public static BlockState plate(RelicStyle style) {
        return ModBlocks.RELIC_PLATE.get().defaultBlockState().setValue(RelicStyle.PROP, style);
    }

    public static BlockState bell(RelicStyle style, Direction facing) {
        return ModBlocks.RELIC_BELL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing).setValue(RelicStyle.PROP, style);
    }




}
