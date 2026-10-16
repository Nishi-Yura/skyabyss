package jp.revati.skyabyss.titan.storm.back;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

// 雷翼の背の試練の進み (休み場・リセット・眠る試練・ピースフル・三つの試練)。12 の 4 章・7 章
public final class StormBackTrials {
    private StormBackTrials() {
    }

    // 嵐に投げ返されたときに戻す所 (最後の休み場、なければ到着)
    public static BlockPos fallbackPoint(ServerPlayer player) {
        return StormBackLayout.ARRIVAL;
    }

    // 試練の中にいるか (嵐に投げ返されたときの傷が 3 になる)
    public static boolean inTrial(ServerPlayer player) {
        return false;
    }
}
