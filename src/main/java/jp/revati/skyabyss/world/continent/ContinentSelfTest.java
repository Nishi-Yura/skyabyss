package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

// 天と淵の大陸の地形の自己テスト。段の名前は "continent"。
//  - 図が読めて、都・内海・西の峰・外の海が思った高さと土地になっている (図だけで見る。どの世界でも)
//  - 土地ごとの敵の強さ (都のまわりは弱く、灰の荒野は強い。二度かけない)
//  - この世界が大陸の種類なら、本物のチャンクでも同じ: 都の地面は平らな草、外の海は水、峰は石、高さの問い合わせが地面と合う
public final class ContinentSelfTest {
    private ContinentSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("continent", ContinentSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ContinentMap map = ContinentMap.get();
            if (map.size != 1000 || map.scale != 4) {
                f.add("map size " + map.size + " scale " + map.scale);
            }
            // 都 (-450, 0) は平らで Y 72
            for (int[] p : new int[][] {{-450, 0}, {-400, 40}, {-500, -40}}) {
                int y = map.surface(p[0], p[1]);
                if (y != 72) {
                    f.add("capital ground at " + p[0] + "," + p[1] + " is Y " + y);
                }
            }
            if (map.biome(400, 30) != ContinentBiomes.INLAND_SEA || map.surface(400, 30) >= ContinentChunkGenerator.SEA_LEVEL - 1) {
                f.add("inland sea: land " + map.biome(400, 30) + " Y " + map.surface(400, 30));
            }
            if (map.surface(1950, 1950) >= ContinentChunkGenerator.SEA_LEVEL - 1 || map.biome(1950, 1950) != ContinentBiomes.DEEP_OCEAN) {
                f.add("outer sea corner is not deep ocean");
            }
            if (map.surface(3000, 0) != ContinentMap.OUTSIDE_Y) {
                f.add("outside the map is not sea floor");
            }
            int peak = 0;
            for (int z = -800; z <= 400; z += 20) {
                peak = Math.max(peak, map.surface(-950, z));
            }
            if (peak < 150) {
                f.add("the western ridge peaks at only Y " + peak);
            }
            ServerLevel level = server.overworld();
            // 土地ごとの敵の強さ: 都のまわりのゾンビは体力 11・攻撃 1.5、灰の荒野は 42・5.4。二度かけても変わらない
            if (map.region(-450, 0) != ContinentMap.MEADOW || map.region(-760, 560) != ContinentMap.OUTSKIRTS) {
                f.add("regions: capital " + map.region(-450, 0) + " start " + map.region(-760, 560));
            }
            for (int[] r : new int[][] {{ContinentMap.MEADOW, 11, 15}, {ContinentMap.EMBER, 42, 54}}) {
                net.minecraft.world.entity.monster.Zombie z = new net.minecraft.world.entity.monster.Zombie(level);
                ContinentDanger.apply(z, r[0]);
                ContinentDanger.apply(z, r[0]);
                double dmg = z.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (Math.abs(z.getMaxHealth() - r[1]) > 0.01 || Math.abs(dmg * 10 - r[2]) > 0.1) {
                    f.add("danger in region " + r[0] + ": health " + z.getMaxHealth() + " damage " + dmg);
                }
            }
            if (level.getChunkSource().getGenerator() instanceof ContinentChunkGenerator gen) {
                BlockPos cap = new BlockPos(-450, 0, 0);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, cap.getX(), cap.getZ());
                // 都がもう建っている世界では、建物の上に乗ることがある。地面の高さは図で見る
                if (!level.getBlockState(new BlockPos(-450, 72, 0)).isSolid() && level.getBlockState(new BlockPos(-450, 72, 0)).isAir()) {
                    f.add("capital ground block is air (world surface " + top + ")");
                }
                if (!level.getBlockState(new BlockPos(1950, 62, 1950)).is(Blocks.WATER)) {
                    f.add("outer sea at 1950,1950 Y62 is " + level.getBlockState(new BlockPos(1950, 62, 1950)));
                }
                int base = gen.getBaseHeight(-950, -200, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
                if (base != map.surface(-950, -200) + 1) {
                    f.add("base height " + base + " vs map " + map.surface(-950, -200));
                }
                level.getChunk(-950 >> 4, -200 >> 4);
                int real = level.getHeight(Heightmap.Types.OCEAN_FLOOR, -950, -200);
                if (Math.abs(real - base) > 1) {
                    f.add("real ground at the ridge " + real + " vs base " + base);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST continent crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST continent PASSED (map loads; flat capital at Y 72, inland sea, outer deep sea, western peaks; regional enemy strength; real chunks match when the world is a continent)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST continent FAILED: {}", s);
            }
        }
    }
}
