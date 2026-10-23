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
                // グループの進みは建て終えたとき (StormBackTrials.afterBuild) に全部戻す
                SkyAbyss.LOGGER.info("Rebuilding the storm back (layout {} -> {})", data.layoutVersion(), LAYOUT_VERSION);
            }
            build(back, data);
        }
    }

    // その場で最後まで建てる。起動時の予約 (schedule) が途中なら、その続きを最後まで進める (最初からやり直さない)
    public static void build(ServerLevel back, TitanBackData data) {
        if (jp.revati.skyabyss.titan.BackBuildJobs.finishNow(back.dimension()) && !data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        long start = System.currentTimeMillis();
        StormBirdBuilder.build(new StormKit(back));
        finish(back, data, start);
    }

    // 起動時: 鳥の体を何 tick かに分けて置き、そのあとの仕上げも手順ごとに tick に分けて建てる (BackBuildJobs。13 の 24.5節)
    public static void schedule(ServerLevel back) {
        TitanBackData data = TitanBackData.get(back);
        if (!data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        if (data.isBuilt()) {
            SkyAbyss.LOGGER.info("Rebuilding the storm back (layout {} -> {})", data.layoutVersion(), LAYOUT_VERSION);
        }
        StormKit kit = new StormKit(back);
        long start = System.currentTimeMillis();
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("storm back bird");
        s.thenJob("body", () -> StormBirdBuilder.job(kit));
        StormBirdBuilder.restSteps(s, kit);
        finishSteps(s, back, data, start);
        jp.revati.skyabyss.titan.BackBuildJobs.schedule("storm back bird", back, s, () -> {
        });
    }

    private static void finish(ServerLevel back, TitanBackData data, long start) {
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("storm back bird");
        finishSteps(s, back, data, start);
        while (!s.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    private static void finishSteps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, ServerLevel back, TitanBackData data, long start) {
        // 竜骨の間 (心臓の間) と前室は体の外の封じた部屋 (trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, back, jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        // 入口の箱 (尾の広場の北の端。trial/TrialRoute.entryBox) の灯り: 箱の床の四隅に海のランタンを埋める (歩く邪魔にならない)。
        // 潮殻の入口と同じ。明るさ 6 で自己テスト trial walk (light) が暗いと言った (版 33)
        s.then("entry lights", () -> {
            net.minecraft.world.phys.AABB box = jp.revati.skyabyss.trial.TrialRoute.entryBox(jp.revati.skyabyss.progress.Titans.THUNDER_WING);
            if (box == null) {
                return;
            }
            int floor = (int) Math.floor(box.minY);
            for (int x : new int[] {(int) box.minX + 1, (int) box.maxX - 2}) {
                for (int z : new int[] {(int) box.minZ + 1, (int) box.maxZ - 2}) {
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, floor, z);
                    if (back.getBlockState(p).isSolidRender(back, p)) {
                        back.setBlock(p, net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState(), 3);
                    }
                }
            }
        });
        s.then("after build", () -> {
            data.markBuilt(LAYOUT_VERSION);
            StormBackTrials.afterBuild(back);
        });
        s.then("done", () -> {
            SkyAbyss.LOGGER.info("Built the storm back bird (layout {}) in {} ms", LAYOUT_VERSION, System.currentTimeMillis() - start);
        });
    }
}
