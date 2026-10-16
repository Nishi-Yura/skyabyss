package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackTrials;
import jp.revati.skyabyss.titan.tide.back.TideIslandBuilder;
import jp.revati.skyabyss.titan.tide.back.TideKit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

// 潮殻の背 (tide_back) の島を建てる入口。島そのものは TideIslandBuilder (08 の 3 章の本番の配置)、配置は TideBackLayout。
// ディメンションの生成は平らな海 (岩盤 1・砂 40・水は y=62 まで)。島は最初に乗り込んだとき (と版が変わったとき) に建てる。
// 版 1 は第一歩の仮の島 (到着と門だけ)、版 2 から本番
public final class TideBackBuilder {
    public static final int LAYOUT_VERSION = TideBackLayout.LAYOUT_VERSION;
    // 生成の海面 (水の最上段は y=62)。平らな世界の getSeaLevel は使えない (-63 を返す) ので、ここで決める
    public static final int SEA = TideBackLayout.SEA;

    private TideBackBuilder() {
    }

    // 到着地点 (潮門の浜)。北 (島の中心) を向く
    public static BlockPos arrival() {
        return TideBackLayout.ARRIVAL;
    }

    // 地上へ戻る潮門の口に入っているか
    static boolean inReturnGate(double x, double y, double z) {
        return TideIslandBuilder.inReturnGate(x, y, z);
    }

    public static void ensureBuilt(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (data.needsBuild(LAYOUT_VERSION)) {
            if (data.isBuilt()) {
                // 古い版 (仮の島) の上に建て直す。進み具合を戻して試練の番号を進める (古い休み場の記録は無効)
                SkyAbyss.LOGGER.info("Rebuilding the tide back (layout {} -> {})", data.layoutVersion(), LAYOUT_VERSION);
                data.resetProgress();
            }
            build(back, data);
        }
    }

    public static void build(ServerLevel back, TitanBackData data) {
        long start = System.currentTimeMillis();
        TideIslandBuilder.build(new TideKit(back, RandomSource.create(20261017L)));
        data.markBuilt(LAYOUT_VERSION);
        TideBackTrials.afterBuild(back);
        SkyAbyss.LOGGER.info("Built the tide back island (layout {}) in {} ms", LAYOUT_VERSION, System.currentTimeMillis() - start);
    }
}
