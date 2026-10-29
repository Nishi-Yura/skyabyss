package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 大陸の地上の敵の湧き (docs/design/44。2026-10-26 の声「全敵 mod の敵。どこに何がスポーンするのか決めていって」)。
// 表は data/skyabyss/spawns/<土地>.json (土地 = island / meadow / whale / tide / storm / ember。ContinentMap の土地の番号の並び)。
//   entries: [{entity, weight, time: day|night|any, biomes: [地形の名前 …] (省くとどこでも), group: [少, 多]}]
// 決まり: 2 秒ごとに人ごとに一度、24〜56 ブロック先の地面に試す。人のまわり 64 の中に、ここで湧かせた敵が 10 体を超えない。
// 都と土地の町の中 (SiteGuard.inTown)・道の上 (ContinentRoads.flattened)・水の上には湧かない。人が 96 離れたら消す。保存しない (SiteSpawner の印)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RegionSpawner {
    public static final String[] REGIONS = {"meadow", "island", "whale", "tide", "storm", "ember"};
    public static final int EVERY = 40;
    public static final int MIN_DIST = 24;
    public static final int MAX_DIST = 56;
    public static final int NEAR_CAP = 10;
    public static final double NEAR = 64;
    public static final double KEEP = 96;
    static final String TAG = jp.revati.skyabyss.guide.SiteSpawner.TAG;
    static final String REGION_TAG = "skyabyss_region_mob";

    public enum Time { DAY, NIGHT, ANY }

    // near: 決まった所のまわりだけに出す ([x, z, 半径] の並び。空ならどこでも)。表では "near": [["島の図の名前", 半径], …]
    // (はじまりの島は寄り道の場所のまわりだけ。2026-10-27 の声「最初の島に敵が出るのはおかしい。特定の場所以外」)
    public record Entry(String entity, int weight, Time time, List<Integer> biomes, int min, int max, List<int[]> near) {
        public Entry(String entity, int weight, Time time, List<Integer> biomes, int min, int max) {
            this(entity, weight, time, biomes, min, max, List.of());
        }

        public boolean fits(int biome, boolean night) {
            return (time == Time.ANY || (time == Time.NIGHT) == night) && (biomes.isEmpty() || biomes.contains(biome));
        }

        public boolean fitsAt(int biome, boolean night, int x, int z) {
            if (!fits(biome, night)) {
                return false;
            }
            if (near.isEmpty()) {
                return true;
            }
            for (int[] c : near) {
                if ((long) (x - c[0]) * (x - c[0]) + (long) (z - c[1]) * (z - c[1]) <= (long) c[2] * c[2]) {
                    return true;
                }
            }
            return false;
        }
    }

    private static volatile List<List<Entry>> table;
    private static final Map<UUID, UUID> LIVE = new ConcurrentHashMap<>();

    private RegionSpawner() {
    }

    // ---------------------------------------------------------------- 表
    public static List<List<Entry>> table() {
        if (table == null) {
            List<List<Entry>> t = new ArrayList<>();
            for (String r : REGIONS) {
                t.add(load(r));
            }
            table = t;
        }
        return table;
    }

    static List<Entry> load(String region) {
        List<Entry> out = new ArrayList<>();
        String path = "/data/skyabyss/spawns/" + region + ".json";
        try (InputStream in = RegionSpawner.class.getResourceAsStream(path)) {
            if (in == null) {
                return out;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("entries")) {
                JsonObject j = e.getAsJsonObject();
                List<Integer> biomes = new ArrayList<>();
                if (j.has("biomes")) {
                    for (JsonElement b : j.getAsJsonArray("biomes")) {
                        int id = biomeId(b.getAsString());
                        if (id >= 0) {
                            biomes.add(id);
                        } else {
                            SkyAbyss.LOGGER.warn("[spawns] {}: unknown biome {}", region, b.getAsString());
                        }
                    }
                }
                int min = 1;
                int max = 1;
                if (j.has("group")) {
                    min = j.getAsJsonArray("group").get(0).getAsInt();
                    max = j.getAsJsonArray("group").get(1).getAsInt();
                }
                List<int[]> near = new ArrayList<>();
                if (j.has("near")) {
                    for (JsonElement n : j.getAsJsonArray("near")) {
                        var a = n.getAsJsonArray();
                        net.minecraft.core.BlockPos at = ContinentLayout.get().island(a.get(0).getAsString());
                        if (at != null) {
                            near.add(new int[] {at.getX(), at.getZ(), a.get(1).getAsInt()});
                        } else {
                            SkyAbyss.LOGGER.warn("[spawns] {}: unknown place {}", region, a.get(0).getAsString());
                        }
                    }
                }
                out.add(new Entry(j.get("entity").getAsString(), j.get("weight").getAsInt(),
                        Time.valueOf(j.has("time") ? j.get("time").getAsString().toUpperCase(java.util.Locale.ROOT) : "ANY"), List.copyOf(biomes), min, Math.max(min, max),
                        List.copyOf(near)));
            }
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("[spawns] could not read {}", path, ex);
        }
        return out;
    }

    // 地形の名前 (ContinentBiomes の定数の小文字) → 番号
    public static int biomeId(String name) {
        try {
            return ContinentBiomes.class.getField(name.toUpperCase(java.util.Locale.ROOT)).getInt(null);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    // その場所・時刻の候補から重みで選ぶ (r は 0〜999)。候補がなければ null
    @Nullable
    public static Entry pick(int region, int biome, boolean night, int r) {
        return pick(region, biome, night, r, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    // x, z = 試す所 (MIN_VALUE なら所を見ない)
    public static Entry pick(int region, int biome, boolean night, int r, int x, int z) {
        List<List<Entry>> t = table();
        if (region < 0 || region >= t.size()) {
            return null;
        }
        int total = 0;
        for (Entry e : t.get(region)) {
            if (x == Integer.MIN_VALUE ? e.fits(biome, night) : e.fitsAt(biome, night, x, z)) {
                total += e.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int k = r % total;
        for (Entry e : t.get(region)) {
            if (x == Integer.MIN_VALUE ? e.fits(biome, night) : e.fitsAt(biome, night, x, z)) {
                k -= e.weight();
                if (k < 0) {
                    return e;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 湧かせる
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % EVERY != 23 || jp.revati.skyabyss.SelfTestRunner.enabled()) {
            return;
        }
        ServerLevel level = server.overworld();
        if (!jp.revati.skyabyss.rpg.rules.RpgRules.adventure(level)) {
            return;
        }
        sweep(level);
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.getY() > 30 && p.getY() < 260 && !jp.revati.skyabyss.story.reel.StoryReels.watching(p)) {
                trySpawn(level, p);
            }
        }
    }

    public static boolean night(Level level) {
        long t = level.getDayTime() % 24000L;
        return t >= 13000 && t < 23000;
    }

    // 一人のまわりに一度だけ試す。湧かせた数 (群れなら複数)
    public static int trySpawn(ServerLevel level, ServerPlayer player) {
        if (nearCount(level, player) >= NEAR_CAP) {
            return 0;
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = MIN_DIST + level.random.nextDouble() * (MAX_DIST - MIN_DIST);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        BlockPos at = ground(level, x, z);
        if (at == null || jp.revati.skyabyss.opening.site.SiteGuard.inTown(level, at) || ContinentRoads.flattened(x + 0.5, z + 0.5)) {
            return 0;
        }
        // はじまりの島では時間で湧かせない (浜や沖の縁が野の土地に数えられて湧いていた。2026-10-29 の試遊「最初の島に敵自然スポーンするのやめて」)
        if (ContinentOutskirts.onIsland(level, at) || ContinentOutskirts.onIsland(level, player.blockPosition())) {
            return 0;
        }
        ContinentMap map = ContinentMap.get();
        Entry e = pick(map.region(x, z), map.biome(x, z), night(level), level.random.nextInt(1000), x, z);
        if (e == null) {
            return 0;
        }
        int n = e.min() + (e.max() > e.min() ? level.random.nextInt(e.max() - e.min() + 1) : 0);
        int made = 0;
        for (int i = 0; i < n; i++) {
            BlockPos p = i == 0 ? at : ground(level, x + level.random.nextInt(5) - 2, z + level.random.nextInt(5) - 2);
            if (p != null && spawn(level, e.entity(), p, player) != null) {
                made++;
            }
        }
        return made;
    }

    @Nullable
    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);
        if (!level.getBlockState(at.below()).isSolid() || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
                || !level.getFluidState(at.below()).isEmpty()) {
            return null;
        }
        return at;
    }

    @Nullable
    public static Mob spawn(ServerLevel level, String entity, BlockPos at, ServerPlayer owner) {
        ResourceLocation key = ResourceLocation.tryParse(entity);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) {
            return null;
        }
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) {
                e.discard();
            }
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        mob.addTag(TAG);
        mob.addTag(REGION_TAG);
        LIVE.put(mob.getUUID(), owner.getUUID());
        if (!level.addFreshEntity(mob)) {
            LIVE.remove(mob.getUUID());
            return null;
        }
        return mob;
    }

    public static boolean isLive(UUID id) {
        return LIVE.containsKey(id);
    }

    static int nearCount(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(NEAR), m -> m.getTags().contains(REGION_TAG) && m.isAlive()).size();
    }

    // 倒れた・消えた物の記録を落とし、どの人からも 96 より遠い物を消す
    static void sweep(ServerLevel level) {
        Map<UUID, Boolean> seen = new HashMap<>();
        for (var it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Entity mob = level.getEntity(en.getKey());
            if (mob == null || !mob.isAlive()) {
                it.remove();
                continue;
            }
            boolean kept = false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(mob) <= KEEP * KEEP) {
                    kept = true;
                    break;
                }
            }
            seen.put(en.getKey(), kept);
            if (!kept) {
                mob.discard();
                it.remove();
            }
        }
    }

    // 自己テスト用
    public static void clearAll(ServerLevel level) {
        for (UUID id : List.copyOf(LIVE.keySet())) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        LIVE.clear();
    }
}
