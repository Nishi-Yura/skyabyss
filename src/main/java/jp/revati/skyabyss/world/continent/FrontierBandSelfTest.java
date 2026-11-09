package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Hardness;
import jp.revati.skyabyss.combat.MobTuning;
import jp.revati.skyabyss.opening.site.SiteData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

// 外れの帯 (FrontierBand・FrontierCaches・FrontierTerrain) の自己テスト。段の名前は "frontier band" (大陸の世界で):
//  - 町のそばは外れでなく、外れの集落・奥のダンジョンの置き場は外れ。体力の倍率は 1 より大きく、Lv が高い土地ほど大きくない (壊れた値でない)
//  - 外れの隠し荷が四つの土地に 3 か所以上ずつ。道・見どころ・集落から離れている。一つ建てると宝箱に鐘銭と封の箱
//  - 見せ場の地形の置く物 (肋骨・浮き岩・岩礁) がそれぞれ一つ以上。外れの集落・奥のダンジョン・跡地のそばでは地形を変えない
//  - 外れで湧かせた敵は体力 (Hardness の far) と攻撃 (MobTuning.attackFactor) が上がる
public final class FrontierBandSelfTest {
    private FrontierBandSelfTest() {
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel over = server.overworld();
        ContinentLayout lay = ContinentLayout.get();
        try {
            for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
                BlockPos town = lay.town(t);
                check(f, town != null && !FrontierBand.far(town.getX(), town.getZ()), "the " + t + " town counts as the frontier");
            }
            // 外れの集落と奥のダンジョンの置き場はほとんど外れ (潮の南東の二つは潮の町から 550〜600 で、帯の手前)
            int farSpots = 0;
            for (ContinentLayout.Frontier s : lay.frontier()) {
                farSpots += FrontierBand.far(s.pos().getX(), s.pos().getZ()) ? 1 : 0;
                check(f, FrontierTerrain.weight(ContinentMap.get().region(s.pos().getX(), s.pos().getZ()), s.pos().getX(), s.pos().getZ()) == 0,
                        "the frontier terrain changes the ground at " + s.key());
            }
            check(f, farSpots >= lay.frontier().size() - 2, "only " + farSpots + " of " + lay.frontier().size() + " hamlets and deep dungeons are in the frontier band");
            for (String t : new String[] {"whale", "tide", "storm", "ember"}) {
                BlockPos site = lay.site(t);
                check(f, site == null || FrontierTerrain.weight(ContinentMap.get().region(site.getX(), site.getZ()), site.getX(), site.getZ()) == 0,
                        "the frontier terrain changes the ground at the " + t + " site");
            }
            for (int r : new int[] {ContinentMap.WHALE, ContinentMap.TIDE, ContinentMap.STORM, ContinentMap.EMBER}) {
                double k = FrontierBand.healthFactor(r);
                check(f, k > 1.05 && k < 2.0, "frontier health factor for region " + r + " is " + k);
            }
            // 隠し荷
            int[] per = new int[8];
            for (FrontierCaches.Cache c : FrontierCaches.all()) {
                per[c.region()]++;
                check(f, !FrontierCaches.roadNear(lay, c.x(), c.z()) && !FrontierCaches.poiNear(c.x(), c.z()) && !FrontierCaches.spotNear(lay, c.x(), c.z()),
                        "frontier cache " + c.id() + " sits by a road, a place or a hamlet");
            }
            for (int r : new int[] {ContinentMap.WHALE, ContinentMap.TIDE, ContinentMap.STORM, ContinentMap.EMBER}) {
                check(f, per[r] >= 3, "region " + r + " has only " + per[r] + " frontier caches");
            }
            if (!FrontierCaches.all().isEmpty()) {
                FrontierCaches.Cache c = FrontierCaches.all().get(0);
                over.getChunk(c.x() >> 4, c.z() >> 4);
                SiteData.Entry e = FrontierCaches.build(over, SiteData.get(over), c, FrontierCaches.entry(SiteData.get(over), c.id()));
                check(f, e != null && e.built, "frontier cache " + c.id() + " was not built");
                SkyAbyss.LOGGER.info("SELFTEST frontier band cache built at {}", e == null ? null : e.pos);
                if (e != null && over.getBlockEntity(e.pos) instanceof ChestBlockEntity chest) {
                    boolean coins = false;
                    boolean box = false;
                    for (int i = 0; i < chest.getContainerSize(); i++) {
                        var st = chest.getItem(i);
                        coins |= st.is(jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get());
                        for (jp.revati.skyabyss.loot.box.BoxKind k : jp.revati.skyabyss.loot.box.BoxKind.ALL) {
                            var item = jp.revati.skyabyss.loot.box.LootBoxSetup.item(k);
                            box |= item != null && st.is(item);
                        }
                    }
                    check(f, coins && box, "the frontier cache chest has no coins or no sealed box");
                } else {
                    f.add("the frontier cache has no chest");
                }
            }
            // 地形の置く物 (撮影のために場所を書く)
            for (int kind = 0; kind <= 2; kind++) {
                SkyAbyss.LOGGER.info("SELFTEST frontier band feature {} at {}", kind, FrontierTerrain.firstFeature(kind));
                check(f, FrontierTerrain.firstFeature(kind) != null, "no frontier terrain feature of kind " + kind);
            }
            {
                ContinentMap map = ContinentMap.get();
                BlockPos lava = null;
                BlockPos flat = null;
                for (int z = -2600; z <= 2600 && (lava == null || flat == null); z += 16) {
                    for (int x = -2600; x <= 2600; x += 16) {
                        if (!map.inside(x, z)) {
                            continue;
                        }
                        int r = map.region(x, z);
                        if (lava == null && r == ContinentMap.EMBER && map.surface(x, z) > 70 && FrontierTerrain.lavaRiver(x, z) > 0.6 && FrontierTerrain.weight(r, x, z) > 0.95) {
                            lava = new BlockPos(x, map.surface(x, z), z);
                        }
                        if (flat == null && r == ContinentMap.TIDE && Math.abs(map.surface(x, z) - 63) <= 1 && FrontierTerrain.weight(r, x, z) > 0.95) {
                            flat = new BlockPos(x, map.surface(x, z), z);
                        }
                    }
                }
                SkyAbyss.LOGGER.info("SELFTEST frontier band lava river at {}, tidal flat at {}, first cache at {}", lava, flat,
                        FrontierCaches.all().isEmpty() ? null : FrontierCaches.all().get(0));
            }
            BlockPos rib = FrontierTerrain.firstFeature(0);
            if (rib != null) {
                over.getChunk(rib.getX() >> 4, rib.getZ() >> 4);
                int bones = 0;
                for (int dx = -14; dx <= 14; dx++) {
                    for (int dz = -14; dz <= 14; dz++) {
                        for (int y = rib.getY(); y <= rib.getY() + 20; y++) {
                            if (over.getBlockState(new BlockPos(rib.getX() + dx, y, rib.getZ() + dz)).is(Blocks.BONE_BLOCK)) {
                                bones++;
                            }
                        }
                    }
                }
                check(f, bones > 30, "the bone valley ribs at " + rib + " have only " + bones + " bone blocks");
            }
            // 外れの敵
            ContinentLayout.Frontier spot = lay.frontier().get(0);
            BlockPos at = new BlockPos(spot.pos().getX() + 40, 0, spot.pos().getZ());
            over.getChunk(at.getX() >> 4, at.getZ() >> 4);
            int y = over.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            Mob m = jp.revati.skyabyss.registry.ModEntities.FERAL_DOG.get().create(over);
            if (m != null) {
                m.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5);
                over.addFreshEntity(m);
                check(f, m.getTags().contains(FrontierBand.TAG) && Hardness.get(m, "far") > 1.05, "a foe in the frontier did not get tougher");
                check(f, MobTuning.attackFactor(m) > 1 + MobTuning.ATTACK + 0.01, "a foe in the frontier does not hit harder (" + MobTuning.attackFactor(m) + ")");
                m.discard();
            }
        } catch (RuntimeException e) {
            f.add("exception: " + e);
            SkyAbyss.LOGGER.error("frontier band self-test", e);
        }
        f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST frontier band FAILED: {}", m));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST frontier band PASSED (band edges, health factor, {} caches with coins and a box, ribs/floating rocks/reefs, tougher foes)",
                    FrontierCaches.all().size());
        }
    }
}
