package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.util.FakePlayer;

// 土地の湧き (RegionSpawner。docs/design/44) の自己テスト。段の名前は "region spawns":
// 表が六つの土地ぶん読める・敵の名前と地形の名前が実在する・敵だけ・昼と夜と地形で選び分ける・湧かせた敵に印が付く・町の中には湧かない
public final class RegionSpawnerSelfTest {
    private RegionSpawnerSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("region spawns", RegionSpawnerSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<List<RegionSpawner.Entry>> t = RegionSpawner.table();
        if (t.size() != RegionSpawner.REGIONS.length) {
            f.add("table has " + t.size() + " regions");
        }
        ServerLevel level = server.overworld();
        int entries = 0;
        for (int r = 0; r < t.size(); r++) {
            for (RegionSpawner.Entry e : t.get(r)) {
                entries++;
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(e.entity()));
                if (type.isEmpty()) {
                    f.add(RegionSpawner.REGIONS[r] + ": unknown entity " + e.entity());
                    continue;
                }
                if (!e.entity().startsWith(SkyAbyss.MODID + ":")) {
                    f.add(RegionSpawner.REGIONS[r] + ": not a mod enemy " + e.entity());
                }
                var made = type.get().create(level);
                if (!(made instanceof Enemy)) {
                    f.add(RegionSpawner.REGIONS[r] + ": " + e.entity() + " is not an enemy");
                }
                if (made != null) {
                    made.discard();
                }
                if (e.weight() <= 0 || e.min() < 1 || e.max() > 5) {
                    f.add(RegionSpawner.REGIONS[r] + ": bad weight or group for " + e.entity());
                }
            }
        }
        // 選び分け: 岩鯨の昼の丘は骨鳥が候補、夜は骨棘の射手が候補になり得る / 灰の夜に昼だけの敵は出ない
        int hills = ContinentBiomes.WINDSWEPT_HILLS;
        boolean gull = false;
        boolean dayInNight = false;
        for (int i = 0; i < 1000; i++) {
            RegionSpawner.Entry d = RegionSpawner.pick(ContinentMap.WHALE, hills, false, i);
            gull |= d != null && d.entity().endsWith("bone_gull");
            RegionSpawner.Entry n = RegionSpawner.pick(ContinentMap.EMBER, ContinentBiomes.BADLANDS, true, i);
            dayInNight |= n != null && n.time() == RegionSpawner.Time.DAY;
        }
        if (!gull) {
            f.add("the whale hills by day never picked a bone gull");
        }
        if (dayInNight) {
            f.add("a day-only enemy was picked at night");
        }
        if (RegionSpawner.pick(ContinentMap.TIDE, ContinentBiomes.BEACH, false, 7) == null) {
            f.add("the tide beach by day has no candidates");
        }
        // 湧かせる: 印が付き、町の中ではない所
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "region_spawn"));
        BlockPos at = level.getSharedSpawnPos().offset(40, 0, 40);
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        at = new BlockPos(at.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        Mob m = RegionSpawner.spawn(level, SkyAbyss.MODID + ":spine_hare", at, p);
        if (m == null || !m.getTags().contains(RegionSpawner.REGION_TAG) || !m.getTags().contains(RegionSpawner.TAG)) {
            f.add("a region spawn was not made or not marked at " + at.toShortString() + " (made " + (m != null) + ")");
        } else if (!m.isAlive() || level.getEntity(m.getUUID()) == null) {
            f.add("a region spawn was removed right after joining");
        }
        BlockPos center = jp.revati.skyabyss.facility.TownGuide.center(server);
        if (center != null && !jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, center)) {
            f.add("the capital center is not counted as town (spawns would enter it)");
        }
        RegionSpawner.clearAll(level);
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST region spawns PASSED ({} entries in {} regions, day/night/terrain picks, marked spawns, towns kept clear)", entries, t.size());
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST region spawns FAILED: {}", s));
        }
    }
}
