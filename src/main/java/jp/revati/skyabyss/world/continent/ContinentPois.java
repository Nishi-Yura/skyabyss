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
//   建てたら、印 loot に宝箱 (鐘銭・糧・その土地の巨神の粉) を置く。野盗の野営・古い塚 (印 foes) は、人が近くに来ると野盗・骨の兵が出る (間を置いて何度でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentPois {
    public static final String KIND = "poi";
    static final int BUILD_RADIUS = 240;
    static final int FOES_RADIUS = 28;
    static final long FOES_COOLDOWN = 20 * 60 * 8;
    static final int CHECK = 40;
    // 発見 (Wynncraft の Discovery): この距離まで近づいたら見つけたことにする
    static final int DISCOVER_RADIUS = 20;
    static final String FOUND_TAG = "skyabyss_poi_found";
    // 土地ごとの発見の経験値 (野・島・岩鯨・潮殻・雷翼・余燼。ContinentMap の土地の番号の順)
    static final int[] DISCOVER_XP = {20, 10, 30, 45, 65, 85};

    // a・b = 道しるべの行き先 (signpost.skyabyss.<id>)、da・db = そこまでのおおよその道のり
    public record Poi(String kind, int x, int z, String a, int da, String b, int db) {
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
                        out.add(new Poi(o.get("kind").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt(),
                                o.has("a") ? o.get("a").getAsString() : "", o.has("da") ? o.get("da").getAsInt() : 0,
                                o.has("b") ? o.get("b").getAsString() : "", o.has("db") ? o.get("db").getAsInt() : 0));
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
                } else {
                    if (!poi.kind().equals("signpost") && d2 <= (double) DISCOVER_RADIUS * DISCOVER_RADIUS) {
                        discover(p, i, poi);
                        // 見どころに着いた (土地の町の人の頼み「〜を見てこよう」。初めてでなくても毎回)
                        jp.revati.skyabyss.quest.Quests.signal(p, "poi:" + poi.kind());
                    }
                    if (e.extra("foes") != null && d2 <= (double) FOES_RADIUS * FOES_RADIUS) {
                        foes(over, e, list.get(i).kind());
                    }
                }
            }
        }
    }

    static final String LEADER_TAG = "skyabyss_poi_leader";
    static final net.minecraft.resources.ResourceLocation LEADER_HP = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "poi_leader_hp");
    static final jp.revati.skyabyss.item.extra.Element[] LEADER_POWDER = {jp.revati.skyabyss.item.extra.Element.STONE, jp.revati.skyabyss.item.extra.Element.STONE,
            jp.revati.skyabyss.item.extra.Element.STONE, jp.revati.skyabyss.item.extra.Element.TIDE, jp.revati.skyabyss.item.extra.Element.WIND, jp.revati.skyabyss.item.extra.Element.ASH};

    // 頭を倒したら: 土地の粉 (野と島は岩) 一つと鐘銭 4〜8 を必ず落とす
    @SubscribeEvent
    static void onLeaderDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        var dead = event.getEntity();
        if (!dead.getTags().contains(LEADER_TAG) || !(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        int r = ContinentMap.get().region(dead.getBlockX(), dead.getBlockZ());
        var e = LEADER_POWDER[Math.max(0, Math.min(LEADER_POWDER.length - 1, r))];
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, dead.getX(), dead.getY() + 0.3, dead.getZ(),
                new ItemStack(jp.revati.skyabyss.registry.ModItems.socketShard(e).get())));
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, dead.getX(), dead.getY() + 0.3, dead.getZ(),
                new ItemStack(jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get(), 4 + level.random.nextInt(5))));
    }

    // 見つけた見どころの番号 (死んでも消えない所に覚える)
    public static java.util.Set<Integer> discovered(net.minecraft.world.entity.player.Player p) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int i : persisted(p).getIntArray(FOUND_TAG)) {
            out.add(i);
        }
        return out;
    }

    static net.minecraft.nbt.CompoundTag persisted(net.minecraft.world.entity.player.Player p) {
        net.minecraft.nbt.CompoundTag root = p.getPersistentData();
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)) {
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new net.minecraft.nbt.CompoundTag());
        }
        return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    // 初めてなら覚えて、名前の札 (「発見」と見どころの名前) と土地の経験値
    static boolean discover(ServerPlayer p, int index, Poi poi) {
        java.util.Set<Integer> found = discovered(p);
        if (!found.add(index)) {
            return false;
        }
        persisted(p).putIntArray(FOUND_TAG, found.stream().mapToInt(Integer::intValue).sorted().toArray());
        jp.revati.skyabyss.notice.Notice.place(p, net.minecraft.network.chat.Component.translatable("map.skyabyss.poi." + poi.kind()),
                net.minecraft.network.chat.Component.translatable("message.skyabyss.poi.discovered", found.size(), all().stream().filter(q -> !q.kind().equals("signpost")).count()));
        int region = ContinentMap.get().region(poi.x(), poi.z());
        jp.revati.skyabyss.rpg.Growth.addXp(p, DISCOVER_XP[Math.max(0, Math.min(DISCOVER_XP.length - 1, region))], "growth.skyabyss.reason.discovery");
        p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        return true;
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
            BlockPos sign = job.mark("sign");
            if (sign != null && !poi.a().isEmpty() && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                be.setText(new net.minecraft.world.level.block.entity.SignText()
                        .setMessage(0, net.minecraft.network.chat.Component.translatable("signpost.skyabyss." + poi.a()))
                        .setMessage(1, net.minecraft.network.chat.Component.literal(poi.da() + " m"))
                        .setMessage(2, net.minecraft.network.chat.Component.translatable("signpost.skyabyss." + poi.b()))
                        .setMessage(3, net.minecraft.network.chat.Component.literal(poi.db() + " m")), true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
            BlockPos tablet = job.mark("tablet");
            if (tablet != null) {
                jp.revati.skyabyss.tablet.Tablets.write(level, tablet, "sign.skyabyss.poi_" + poi.kind());
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

    // 野盗: 弓の野盗二人と斧の野盗一人と頭 (土地の強さは ContinentDanger が掛ける)。前に出てから FOES_COOLDOWN たつまで出さない
    static void foes(ServerLevel level, SiteData.Entry e, String kind) {
        long now = level.getGameTime();
        BlockPos at = e.extra("foes");
        BlockPos last = e.extra("foes_at");
        if (last != null && now - last.getX() < FOES_COOLDOWN && now >= last.getX()) {
            return;
        }
        e.extra("foes_at", new BlockPos((int) Math.min(Integer.MAX_VALUE, now), 0, 0));
        SiteData.get(level).setDirty();
        // 塚・坑道・墓所・洞窟は骨の兵、野営は野盗。塚・坑道・洞窟は地の下 (印の高さ) に出す
        boolean undead = kind.equals("barrow") || kind.equals("mine") || kind.equals("graveyard") || kind.equals("cave");
        boolean below = kind.equals("barrow") || kind.equals("mine") || kind.equals("cave");
        EntityType<?>[] kinds = undead ? new EntityType<?>[] {EntityType.SKELETON, EntityType.SKELETON, EntityType.ZOMBIE}
                : new EntityType<?>[] {EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR};
        // 頭 (一体。名前つき、体力は 2.5 倍、倒すと土地の粉と鐘銭。onLeaderDrops)
        // はじまりの島 (最初の戦い) には頭を出さない
        EntityType<?> leaderType = undead ? EntityType.SKELETON : EntityType.VINDICATOR;
        if (ContinentMap.get().region(at.getX(), at.getZ()) != ContinentMap.OUTSKIRTS && leaderType.create(level) instanceof Mob leader) {
            int y = below ? at.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            leader.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            leader.finalizeSpawn(level, level.getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.EVENT, null);
            var hp = leader.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(LEADER_HP, 1.5,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                leader.setHealth(leader.getMaxHealth());
            }
            if (undead) {
                leader.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                leader.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                leader.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0f);
                leader.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
            }
            leader.setCustomName(net.minecraft.network.chat.Component.translatable(undead ? "entity.skyabyss.poi_leader_bone" : "entity.skyabyss.poi_leader_bandit"));
            leader.setCustomNameVisible(true);
            leader.addTag(LEADER_TAG);
            leader.setPersistenceRequired();
            level.addFreshEntity(leader);
        }
        for (int k = 0; k < kinds.length; k++) {
            double a = k * 2.1;
            int x = at.getX() + (int) Math.round(Math.cos(a) * (below ? 2 : 3));
            int z = at.getZ() + (int) Math.round(Math.sin(a) * (below ? 2 : 3));
            int y = below ? at.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
