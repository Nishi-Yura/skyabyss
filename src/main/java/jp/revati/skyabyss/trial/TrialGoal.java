package jp.revati.skyabyss.trial;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 試練のゴール (到着の印)。石を押す仕組みは無くした (共鳴石・潮鳴り石・風鳴り石・熾り石はもう無い)。試練の終わりの床に光る印を一つ置き、
// そこへ着けば (近づけば) 越えたことになる。越えたグループの画面では金の印に変わる (見かけの上書き。当たり判定は同じ)
public final class TrialGoal {
    private TrialGoal() {
    }

    // 到着の印の見かけ (まだ着いていない)
    public static BlockState mark() {
        return Blocks.GLOWSTONE.defaultBlockState();
    }

    // 着いたグループの画面の見かけ
    public static BlockState reached() {
        return Blocks.GOLD_BLOCK.defaultBlockState();
    }

    public static boolean isMark(BlockState state) {
        return state.is(Blocks.GLOWSTONE) || state.is(Blocks.GOLD_BLOCK);
    }

    public static boolean isReached(BlockState state) {
        return state.is(Blocks.GOLD_BLOCK);
    }
}
