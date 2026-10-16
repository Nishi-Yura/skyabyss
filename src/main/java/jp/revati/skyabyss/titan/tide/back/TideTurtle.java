package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.titan.TitanBodyBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の島そのもの: 巨神本体のモデル (採用案 B) と同じ形・同じ色の、ブロックでできた亀 (08 の 21 章)。
// 形は tools/gen_tide_back_turtle.py がモデルの格子から作った data/skyabyss/tide_back/turtle.bin を読む
// (ゲームの巨神の 1.7 倍。頭は北 -Z、潮門は左後ろ = 南西)。試練の部屋は、置いたあとで TideIslandBuilder が中を掘る。
// 読み書きは共通の TitanBodyBlocks (13 の 10.2節。余燼の体と同じ部品)。表の形式はそちらのコメント
public final class TideTurtle {
    private static final TitanBodyBlocks BODY = new TitanBodyBlocks("/data/skyabyss/tide_back/turtle.bin");

    private TideTurtle() {
    }

    public static int minX() {
        return BODY.minX();
    }

    public static int maxX() {
        return BODY.maxX();
    }

    public static int minZ() {
        return BODY.minZ();
    }

    public static int maxZ() {
        return BODY.maxZ();
    }

    public static int minY() {
        return BODY.minY();
    }

    public static int maxY() {
        return BODY.maxY();
    }

    // 甲羅 (体の骨) のいちばん上のブロックの y。甲羅の無い柱は Integer.MIN_VALUE
    public static int carapaceTop(int x, int z) {
        return BODY.shellTop(x, z);
    }

    // 亀のブロック (無ければ null)
    public static BlockState at(int x, int y, int z) {
        return BODY.at(x, y, z);
    }

    // 亀の何か (甲羅・遺跡・珊瑚・頭・ひれ) のいちばん上のブロックの y。無ければ Integer.MIN_VALUE
    public static int top(int x, int z) {
        return BODY.top(x, z);
    }

    // 亀を置く。範囲の中で、亀のブロックでない所は海 (y 62 まで水) と空に戻す (前の版の島を消す)。
    // 変わるブロックだけを置く (同じなら触らない)。置いたブロックの数を返す
    public static int place(TideKit kit) {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        return BODY.place(kit.level(), TideKit.FLAGS, y -> y < TideBackLayout.SEA ? water : air);
    }
}
