package jp.revati.skyabyss.titan.storm.back;

import net.minecraft.core.BlockPos;

// 【仮の実装】雷翼の背の配置 (12 の 3.3節)。本物は背中の担当 (担当 1) が作る。統合で本物に置き換える。
// 中ボスの担当 (担当 3) が使うのは雷獣の巣の座標だけなので、ここには 11.1節の約束の部屋の座標だけを置く
public final class StormBackLayout {
    // 右手首の瘤 (雷獣の巣): x 116〜140、z -30〜-6、床 143 (人が立つ高さ)、直径 26、天井 158
    public static final BlockPos RAIJU_NEST = new BlockPos(128, 143, -18);
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = 158;
    // 竜骨の間 (心臓) の中心と床
    public static final BlockPos KEEL_HALL = new BlockPos(0, 121, -14);

    private StormBackLayout() {
    }
}
