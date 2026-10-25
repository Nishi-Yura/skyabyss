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
            // 発見: 一度目だけ数えて経験値、二度目は何もしない。覚えた番号は持ち物と同じく死んでも残る所 (PlayerPersisted)
            net.neoforged.neoforge.common.util.FakePlayer probe = new net.neoforged.neoforge.common.util.FakePlayer(level,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "poi_probe"));
            ContinentPois.Poi first = ContinentPois.all().get(0);
            boolean once = ContinentPois.discover(probe, 0, first);
            boolean twice = ContinentPois.discover(probe, 0, first);
            if (!once || twice || !ContinentPois.discovered(probe).contains(0)
                    || !probe.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).contains(ContinentPois.FOUND_TAG)) {
                f.add("discovery: first " + once + " second " + twice + " found " + ContinentPois.discovered(probe));
            }
            if (jp.revati.skyabyss.map.MapMarks.MAX < ContinentPois.all().size() + 60) {
                f.add("map mark cap " + jp.revati.skyabyss.map.MapMarks.MAX + " is too small for " + ContinentPois.all().size() + " POIs");
            }
            // 地面に四角い穴が無い: 野・浜・荒野のいくつかの所で、まわり (±3) のどれよりも 2 以上低い所を数える
            int pits = 0;
            String firstPit = "";
            for (int[] c : new int[][] {{-324, 444}, {1184, 224}, {-512, 812}, {300, 1100}, {-100, -600}}) {
                for (int x = c[0] - 48; x < c[0] + 48; x++) {
                    for (int z = c[1] - 48; z < c[1] + 48; z++) {
                        int h = map.surface(x, z);
                        if (h < ContinentChunkGenerator.SEA_LEVEL) {
                            continue;
                        }
                        int around = Math.min(Math.min(map.surface(x + 3, z), map.surface(x - 3, z)), Math.min(map.surface(x, z + 3), map.surface(x, z - 3)));
                        if (h <= around - 2 && pits++ == 0) {
                            firstPit = x + "," + z + " Y " + h + " around " + around;
                        }
                    }
                }
            }
            if (pits > 0) {
                f.add(pits + " pit columns (first " + firstPit + ")");
            }
            // 外れの一本道の休み場と祠: 二つ。足跡のそばは避ける
            List<int[]> stops = ContinentOutskirts.spots(List.of());
            if (stops.size() != 2) {
                f.add("outskirts stops: " + stops.size());
            } else {
                BlockPos block = new BlockPos(stops.get(0)[0], 0, stops.get(0)[1]);
                int[] moved = ContinentOutskirts.spots(List.of(block)).get(0);
                if (Math.hypot(moved[0] - block.getX(), moved[1] - block.getZ()) < ContinentOutskirts.AVOID) {
                    f.add("the rest stop did not move away from a footprint");
                }
            }
            // 土地ごとの敵の強さ: 都のまわりのゾンビは体力 11・攻撃 1.5、灰の荒野は 42・5.4。二度かけても変わらない
            // はじまりの島 (野営地のそば) は外れの土地で陸。大陸とは海で離れている (島と都のあいだの中ほどは海)
            BlockPos start = ContinentLayout.get().start();
            if (map.region(-450, 0) != ContinentMap.MEADOW || map.region(start.getX(), start.getZ()) != ContinentMap.OUTSKIRTS
                    || map.surface(start.getX(), start.getZ()) < ContinentChunkGenerator.SEA_LEVEL || map.surface(-1465, 1420) >= ContinentChunkGenerator.SEA_LEVEL - 1) {
                f.add("regions: capital " + map.region(-450, 0) + " start " + map.region(start.getX(), start.getZ()) + " Y " + map.surface(start.getX(), start.getZ())
                        + " strait Y " + map.surface(-1465, 1420));
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
            SkyAbyss.LOGGER.info("SELFTEST continent PASSED (map loads; flat capital at Y 72, inland sea, outer deep sea, western peaks; regional enemy strength; no square pits; outskirts stops avoid footprints; discovery once per POI; map mark cap fits the POIs; real chunks match when the world is a continent)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST continent FAILED: {}", s);
            }
        }
    }
}
