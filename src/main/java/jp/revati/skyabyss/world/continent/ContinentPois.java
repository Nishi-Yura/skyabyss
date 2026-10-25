package jp.revati.skyabyss.world.continent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.extra.Element;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.SiteBuilds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 天と淵の大陸の見どころ (風車・灯台・巨神の肋骨・焼けた砦・野盗の野営など。docs/design/40 の 3 章)。
// 置き場は data/skyabyss/continent/pois.json、部品は data/skyabyss/opening/poi_<種類>.json (どちらも tools/gen/world/gen_continent_pois.py)。
//   世界が始まったときには建てない。人が BUILD_RADIUS まで近づいたら、その一つを SiteData (種類 "poi"、番号 = pois.json の並び) に足して建てる
//   建てたら、印 loot に宝箱 (鐘銭・糧・その土地の巨神の粉) を置く。野盗の野営 (印 foes) は、人が近くに来ると野盗が出る (間を置いて何度でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentPois {
    public static final String KIND = "poi";
    static final int BUILD_RADIUS = 240;
    static final int FOES_RADIUS = 28;
    static final long FOES_COOLDOWN = 20 * 60 * 8;
    static final int CHECK = 40;

    public record Poi(String kind, int x, int z) {
    }

    private static List<Poi> pois;

    private ContinentPois() {
    }

    public static synchronized List<Poi> all() {
        if (pois == null) {
            List<Poi> out = new ArrayList<>();
            String path = "/data/skyabyss/continent/pois.json";
            try (InputStream in = ContinentPois.class.getResourceAsStream(path)) {
                if (in != null) {
                    for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                        JsonObject o = e.getAsJsonObject();
                        out.add(new Poi(o.get("kind").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt()));
                    }
                }
            } catch (java.io.IOException ex) {
                SkyAbyss.LOGGER.error("cannot read {}", path, ex);
            }
            pois = List.copyOf(out);
        }
        return pois;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        ServerLevel over = event.getServer().overworld();
        if (over.getGameTime() % CHECK != 0 || !(over.getChunkSource().getGenerator() instanceof ContinentChunkGenerator) || over.players().isEmpty()) {
            return;
        }
        SiteData data = SiteData.get(over);
        if (!data.planned()) {
            return;
        }
        List<Poi> list = all();
        for (ServerPlayer p : over.players()) {
            if (p.isSpectator()) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                Poi poi = list.get(i);
                double d2 = p.distanceToSqr(poi.x() + 0.5, p.getY(), poi.z() + 0.5);
                if (d2 > (double) BUILD_RADIUS * BUILD_RADIUS) {
                    continue;
                }
                SiteData.Entry e = entry(data, i);
                if (e == null) {
                    int y = ContinentMap.get().surface(poi.x(), poi.z()) + 1;
                    e = data.add(KIND, i, new BlockPos(poi.x(), y, poi.z()), (i * 37) % 4);
                }
                if (!e.built) {
                    if (!SiteBuilds.queued(e)) {
                        SiteBuilds.enqueue(job(over, data, e));
                    }
                } else if (e.extra("foes") != null && d2 <= (double) FOES_RADIUS * FOES_RADIUS) {
                    foes(over, e);
                }
            }
        }
    }

    static SiteData.Entry entry(SiteData data, int index) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == index) {
                return e;
            }
        }
        return null;
    }

    public static SiteJob job(ServerLevel level, SiteData data, SiteData.Entry entry) {
        Poi poi = all().get(Math.max(0, Math.min(all().size() - 1, entry.index)));
        return new SiteJob(level, data, entry, Blueprint.get("poi_" + poi.kind()), "poi " + poi.kind(), job -> {
            BlockPos foes = job.mark("foes");
            if (foes != null) {
                job.entry.extra("foes", foes);
            }
            BlockPos loot = job.mark("loot");
            if (loot != null) {
                fillLoot(level, loot, poi);
            }
        });
    }

    // 宝箱: 鐘銭 3〜8・糧・その土地の巨神の粉 (I) 一つ
    static void fillLoot(ServerLevel level, BlockPos at, Poi poi) {
        if (!(level.getBlockEntity(at) instanceof ChestBlockEntity)) {
            level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
        }
        if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
            return;
        }
        var r = level.random;
        chest.setItem(4, new ItemStack(jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get(), 3 + r.nextInt(6)));
        chest.setItem(10, new ItemStack(Items.BREAD, 2 + r.nextInt(4)));
        Element e = switch (ContinentMap.get().region(poi.x(), poi.z())) {
            case ContinentMap.WHALE -> Element.STONE;
            case ContinentMap.TIDE -> Element.TIDE;
            case ContinentMap.STORM -> Element.WIND;
            case ContinentMap.EMBER -> Element.ASH;
            default -> null;
        };
        if (e != null) {
            chest.setItem(16, new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(e).get()));
        }
        chest.setChanged();
    }

    // 野盗: 弓の野盗二人と斧の野盗一人 (土地の強さは ContinentDanger が掛ける)。前に出てから FOES_COOLDOWN たつまで出さない
    static void foes(ServerLevel level, SiteData.Entry e) {
        long now = level.getGameTime();
        BlockPos at = e.extra("foes");
        BlockPos last = e.extra("foes_at");
        if (last != null && now - last.getX() < FOES_COOLDOWN && now >= last.getX()) {
            return;
        }
        e.extra("foes_at", new BlockPos((int) Math.min(Integer.MAX_VALUE, now), 0, 0));
        SiteData.get(level).setDirty();
        EntityType<?>[] kinds = {EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR};
        for (int k = 0; k < kinds.length; k++) {
            double a = k * 2.1;
            int x = at.getX() + (int) Math.round(Math.cos(a) * 3);
            int z = at.getZ() + (int) Math.round(Math.sin(a) * 3);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (kinds[k].create(level) instanceof Mob m) {
                m.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
            }
        }
    }

    public static boolean in(Level level) {
        return level instanceof ServerLevel s && s.getChunkSource().getGenerator() instanceof ContinentChunkGenerator;
    }
}
