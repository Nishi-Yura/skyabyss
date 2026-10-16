package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 雷翼の背 (storm_back) の鳥を建てる入口。鳥そのものは storm_back/bird.bin (BackShape)、部屋は StormBackLayout。
// サーバーの起動のときに一度だけ建てる (版が古ければ建て直す)。乗り込みの ensureBuilt は念のため
public final class StormBackBuilder {
    public static final int LAYOUT_VERSION = StormBackLayout.LAYOUT_VERSION;

    private StormBackBuilder() {
    }

    public static BlockPos arrival() {
        return StormBackLayout.ARRIVAL;
    }

    public static void ensureBuilt(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (data.needsBuild(LAYOUT_VERSION)) {
            if (data.isBuilt()) {
                SkyAbyss.LOGGER.info("Rebuilding the storm back (layout {} -> {})", data.layoutVersion(), LAYOUT_VERSION);
                data.resetProgress();
            }
            build(back, data);
        }
    }

    public static void build(ServerLevel back, TitanBackData data) {
        long start = System.currentTimeMillis();
        StormBirdBuilder.build(new StormKit(back));
        data.markBuilt(LAYOUT_VERSION);
        StormBackTrials.afterBuild(back);
        SkyAbyss.LOGGER.info("Built the storm back bird (layout {}) in {} ms", LAYOUT_VERSION, System.currentTimeMillis() - start);
    }
}
