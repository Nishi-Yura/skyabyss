package jp.revati.skyabyss.story.ch6.cliff;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;

// 天秤の皿の石 (上弦の崖の探す場所 3。CliffBalance が皿ごと上げ下げする。見た目と見分けのための印のブロック)
public class BalancePlateBlock extends Block {
    public static final MapCodec<BalancePlateBlock> CODEC = simpleCodec(BalancePlateBlock::new);

    public BalancePlateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
