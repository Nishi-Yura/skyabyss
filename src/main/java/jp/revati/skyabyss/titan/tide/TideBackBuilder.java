package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackTrials;
import jp.revati.skyabyss.titan.tide.back.TideIslandBuilder;
import jp.revati.skyabyss.titan.tide.back.TideKit;
import jp.revati.skyabyss.titan.tide.back.TideTurtle;
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

    // 開発・自己テスト: 試練の階を、いまの背へ後から置く (本番の建設では TideDeck.placed() が false のあいだ置かない)
    public static void placeDeck(ServerLevel back) {
        jp.revati.skyabyss.titan.tide.back.TideDeck.setPlaced(true);
        jp.revati.skyabyss.titan.tide.back.TideDeckBuilder.buildDeck(new TideKit(back, RandomSource.create(20261017L)));
        jp.revati.skyabyss.trial.TrialRuntime.installGates(back);
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
                TideBackTrials.resetAllRecords(back);
            }
            build(back, data);
        }
    }

    // その場で最後まで建てる。起動時の予約 (schedule) が途中なら取り消す (置いた分は「変わるブロックだけ」なので飛ばせる)
    public static void build(ServerLevel back, TitanBackData data) {
        BackBuildJobs.cancel(back.dimension());
        long start = System.currentTimeMillis();
        TideKit kit = new TideKit(back, RandomSource.create(20261017L));
        TideTurtle.place(kit);
        finish(back, data, kit, start);
    }

    // 起動時: 亀の体を何 tick かに分けて置き、置き終えた tick に残りを建てる (BackBuildJobs。13 の 24.5節)
    public static void schedule(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (!data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        if (data.isBuilt()) {
            SkyAbyss.LOGGER.info("Rebuilding the tide back (layout {} -> {})", data.layoutVersion(), LAYOUT_VERSION);
            TideBackTrials.resetAllRecords(back);
        }
        TideKit kit = new TideKit(back, RandomSource.create(20261017L));
        BackBuildJobs.schedule("tide back island", back, TideTurtle.job(kit), () -> finish(back, data, kit, System.currentTimeMillis()));
    }

    private static void finish(ServerLevel back, TitanBackData data, TideKit kit, long start) {
        TideIslandBuilder.buildRest(kit);
        data.markBuilt(LAYOUT_VERSION);
        TideBackTrials.afterBuild(back);
        jp.revati.skyabyss.titan.light.RouteLamps.place(back, "tide");
        SkyAbyss.LOGGER.info("Built the tide back island (layout {}); the rooms took {} ms", LAYOUT_VERSION, System.currentTimeMillis() - start);
    }
}
