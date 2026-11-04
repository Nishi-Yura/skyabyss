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
// 置き場は data/skyabyss/continent/pois.json、部品は data/skyabyss/opening/poi_<種類>.json。
//   名のある場所 (name) は、その場所だけの部品 poi_<種類>__<名> (添え物・碑・旅人の立つ所。tools/gen/world/build_poi_places.py) と、
//   名 (map.skyabyss.poi_name.<名>)・書付 (碑。sign.skyabyss.poi_note.<名>) を持つ。道しるべは名の 4 行 (sign.skyabyss.waypost.<名>) を看板に書く
//   世界が始まったときには建てない。人が BUILD_RADIUS まで近づいたら、その一つを SiteData (種類 "poi"、番号 = pois.json の並び) に足して建てる
//   建てたら、印 loot に宝箱 (鐘銭・糧・その土地の巨神の粉) を置く。野盗の野営・古い塚 (印 foes) は、人が近くに来ると野盗・骨の兵が出る (間を置いて何度でも)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ContinentPois {
    public static final String KIND = "poi";
    static final int BUILD_RADIUS = 240;
    static final int FOES_RADIUS = 28;
    static final int FOLK_RADIUS = 64;
    static final long FOES_COOLDOWN = 20 * 60 * 8;
    static final int CHECK = 40;
    // 発見 (Wynncraft の Discovery): この距離まで近づいたら見つけたことにする
    static final int DISCOVER_RADIUS = 20;
    static final String FOUND_TAG = "skyabyss_poi_found";
    // 土地ごとの発見の経験値 (野・島・岩鯨・潮殻・雷翼・余燼。ContinentMap の土地の番号の順)
    // 土地ごと (草原・外れ・岩鯨・潮・嵐・灰)。章の初めての場所が章の経験値の 20% になるよう、土地の見どころの数で割った値 (04 の 3 章、自己テスト xp table)
    static final int[] DISCOVER_XP = {6, 10, 9, 33, 92, 98};

    // 土地の見どころを初めて見つけた経験値 (隠れた発見はその倍。ContinentSecrets)
    public static int discoverXp(int region) {
        return DISCOVER_XP[Math.max(0, Math.min(DISCOVER_XP.length - 1, region))];
    }

    // a・b = 古い道しるべの行き先 (signpost.skyabyss.<id>)、da・db = そこまでのおおよその道のり。
    // name = 場所の名の id (なければ種類の名と書付)、chief = 野盗の頭目の兄弟の id (entity.skyabyss.bandit_brother.<id>)
    public record Poi(String kind, int x, int z, String a, int da, String b, int db, String name, String chief) {
        // 行ける見どころ (道しるべと、消した見どころの空き "gone" を除く)。
        // 見どころは pois.json の並びの番号で覚える (建った印・発見) ので、消すときは並びから抜かずに "gone" にする
        public boolean site() {
            return !kind.equals("signpost") && !kind.equals(GONE);
        }

        // 建てるもの (道しるべも建てる。消した空きは建てない)
        public boolean built() {
            return !kind.equals(GONE);
        }

        // 部品の名 (名のある場所はその場所の部品)
        public String blueprint() {
            return name.isEmpty() || kind.equals("signpost") ? "poi_" + kind : "poi_" + kind + "__" + name;
        }

        // 地図・発見の札の名 (名のある場所はその名、ほかは種類の名)
        public String titleKey() {
            return name.isEmpty() ? "map.skyabyss.poi." + kind : "map.skyabyss.poi_name." + name;
        }

        public net.minecraft.network.chat.Component title() {
            return net.minecraft.network.chat.Component.translatable(titleKey());
        }
    }

    public static final String GONE = "gone";

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
                                o.has("b") ? o.get("b").getAsString() : "", o.has("db") ? o.get("db").getAsInt() : 0,
                                o.has("name") ? o.get("name").getAsString() : "", o.has("chief") ? o.get("chief").getAsString() : ""));
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
        if (over.getGameTime() % CHECK != 0 || over.players().isEmpty()) {
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
                // 消した見どころは建てない (道しるべは大きな分かれ道の六つだけ建てる。docs/design/40 の 3.3)
                if (!poi.built()) {
                    continue;
                }
                double d2 = p.distanceToSqr(poi.x() + 0.5, p.getY(), poi.z() + 0.5);
                if (d2 > (double) BUILD_RADIUS * BUILD_RADIUS) {
                    continue;
                }
                SiteData.Entry e = entry(data, i);
                if (e == null) {
                    // 道にかかる見どころは、道から離れる向きにずらす (2026-11-02 の声「道の上に構造物できないようにして」。かかった所だけ欠けて建っていた)
                    BlockPos at = RoadClearance.pushAway(new BlockPos(poi.x(), 0, poi.z()), radius(poi), 2, 40);
                    int y = ContinentMap.get().surface(at.getX(), at.getZ()) + 1;
                    // 正面 (部品は南が入口) を一番近い道へ向ける (2026-10-28 の試遊「建物の向きがおかしい」。前は番号で回していた)
                    e = data.add(KIND, i, new BlockPos(at.getX(), y, at.getZ()), faceRoad(at.getX(), at.getZ()));
                }
                // 見つけた・敵の近さは、建った所から測る (道からずらした見どころ)
                d2 = p.distanceToSqr(e.pos.getX() + 0.5, p.getY(), e.pos.getZ() + 0.5);
                if (!e.built) {
                    if (!SiteBuilds.queued(e)) {
                        SiteBuilds.enqueue(job(over, data, e));
                    }
                } else {
                    if (poi.site() && d2 <= (double) DISCOVER_RADIUS * DISCOVER_RADIUS) {
                        discover(p, i, poi);
                        // 見どころに着いた (土地の町の人の頼み「〜を見てこよう」。初めてでなくても毎回)。名のある場所は「poi:@<名>」も
                        jp.revati.skyabyss.quest.Quests.signal(p, "poi:" + poi.kind());
                        if (!poi.name().isEmpty()) {
                            jp.revati.skyabyss.quest.Quests.signal(p, "poi:@" + poi.name());
                        }
                    }
                    // 野営の旅人 (人が近くにいるあいだ立たせる。town/RegionFolk の町 "camp"、印 = 場所の名)
                    BlockPos folk = e.extra("folk");
                    if (folk != null && d2 <= (double) FOLK_RADIUS * FOLK_RADIUS) {
                        jp.revati.skyabyss.town.RegionFolk.ensure(over, jp.revati.skyabyss.town.RegionFolk.CAMP, poi.name(), folk);
                    }
                    // はじまりの島の見どころは敵を出さない (島の敵は IslandTrail の目的の場所だけ)
                    if (e.extra("foes") != null && d2 <= (double) FOES_RADIUS * FOES_RADIUS
                            && ContinentMap.get().region(poi.x(), poi.z()) != ContinentMap.OUTSKIRTS) {
                        foes(over, e, list.get(i));
                    }
                }
            }
        }
    }

    static final String LEADER_TAG = "skyabyss_poi_leader";
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
        jp.revati.skyabyss.notice.Notice.place(p, poi.title(),
                net.minecraft.network.chat.Component.translatable("message.skyabyss.poi.discovered", found.size(), all().stream().filter(Poi::site).count()));
        int region = ContinentMap.get().region(poi.x(), poi.z());
        jp.revati.skyabyss.rpg.Growth.addXp(p, discoverXp(region), "growth.skyabyss.reason.discovery");
        p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        // 旅帳と土地の名声 (endgame/RegionFame)
        jp.revati.skyabyss.endgame.RegionFame.discovered(p, region);
        return true;
    }

    // 自己テスト用: 見どころを見つけたことにする / 見つけた記録を消す
    public static boolean discoverForTest(ServerPlayer p, int index) {
        return discover(p, index, all().get(index));
    }

    public static void forgetForTest(net.minecraft.world.entity.player.Player p) {
        persisted(p).remove(FOUND_TAG);
    }

    static SiteData.Entry entry(SiteData data, int index) {
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.index == index) {
                return e;
            }
        }
        return null;
    }

    // 見どころの部品の半径 (向きによらず収まる半分の大きさ + 地均しの輪)。部品が読めなければ 8
    static int radius(Poi poi) {
        try {
            Blueprint bp = Blueprint.get(poi.blueprint());
            return Math.max(bp.rx1 - bp.rx0, bp.rz1 - bp.rz0) / 2 + 1 + bp.ring;
        } catch (RuntimeException ex) {
            return 8;
        }
    }

    // 一番近い道の点へ正面 (南) を向ける回し (道がなければ南のまま)
    static int faceRoad(int x, int z) {
        double best = Double.MAX_VALUE;
        int bx = x;
        int bz = z + 1;
        for (java.util.List<BlockPos> road : ContinentLayout.get().roads()) {
            for (int k = 0; k + 1 < road.size(); k++) {
                BlockPos a = road.get(k);
                BlockPos b = road.get(k + 1);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double len2 = Math.max(1e-6, dx * dx + dz * dz);
                double t = Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / len2));
                double px = a.getX() + dx * t;
                double pz = a.getZ() + dz * t;
                double d = (px - x) * (px - x) + (pz - z) * (pz - z);
                if (d < best) {
                    best = d;
                    bx = (int) Math.round(px);
                    bz = (int) Math.round(pz);
                }
            }
        }
        if (bx == x && bz == z) {
            return 0;
        }
        return jp.revati.skyabyss.opening.site.SiteFinder.rotToward(bx - x, bz - z, true);
    }

    public static SiteJob job(ServerLevel level, SiteData data, SiteData.Entry entry) {
        Poi poi = all().get(Math.max(0, Math.min(all().size() - 1, entry.index)));
        return new SiteJob(level, data, entry, Blueprint.get(poi.blueprint()), "poi " + poi.blueprint(), job -> {
            BlockPos foes = job.mark("foes");
            if (foes != null) {
                job.entry.extra("foes", foes);
            }
            BlockPos sign = job.mark("sign");
            if (sign != null && !poi.name().isEmpty() && level.getBlockEntity(sign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity be) {
                // 行き先と道のり・土地の名・目安のレベル (4 行。sign.skyabyss.waypost.<名>.1〜4)
                net.minecraft.world.level.block.entity.SignText text = new net.minecraft.world.level.block.entity.SignText();
                for (int k = 0; k < 4; k++) {
                    text = text.setMessage(k, net.minecraft.network.chat.Component.translatable("sign.skyabyss.waypost." + poi.name() + "." + (k + 1)));
                }
                be.setText(text, true);
                be.setWaxed(true);
                be.setChanged();
                level.sendBlockUpdated(sign, be.getBlockState(), be.getBlockState(), 3);
            }
            BlockPos tablet = job.mark("tablet");
            if (tablet != null) {
                jp.revati.skyabyss.tablet.Tablets.write(level, tablet, noteKey(poi));
            }
            BlockPos folk = job.mark("folk");
            if (folk != null) {
                job.entry.extra("folk", folk);
            }
            BlockPos loot = job.mark("loot");
            if (loot != null) {
                fillLoot(level, loot, poi);
            }
            // 地下のダンジョンの入口の間 (docs/design/55): 層の門・帰り着く所・噂の主の立つ所
            for (String k : new String[]{"gate", "back", "rumor"}) {
                BlockPos at = job.mark(k);
                if (at != null) {
                    job.entry.extra(k, at);
                }
            }
        });
    }

    // 碑の文の鍵 (名のある場所はその場所の書付、ほかは種類の書付)
    public static String noteKey(Poi poi) {
        return poi.name().isEmpty() ? "sign.skyabyss.poi_" + poi.kind() : "sign.skyabyss.poi_note." + poi.name();
    }

    // 建った見どころ kind の印 extra の世界の座標 (まだ建っていなければ null)。地下のダンジョンの層の門・帰り着く所
    @org.jetbrains.annotations.Nullable
    public static BlockPos extraOf(ServerLevel level, String kind, String extra) {
        ServerLevel over = level.getServer().overworld();
        SiteData data = SiteData.get(over);
        List<Poi> list = all();
        for (SiteData.Entry e : data.entries(KIND)) {
            if (e.built && e.index >= 0 && e.index < list.size() && list.get(e.index).kind().equals(kind)) {
                return e.extra(extra);
            }
        }
        return null;
    }

    // 建った見どころ kind の、正面 (部品の南) へ dist 出た所と、部品の原点 (撮影・開発用)。まだ建っていなければ null
    @org.jetbrains.annotations.Nullable
    public static BlockPos[] frontOf(ServerLevel level, String kind, int dist) {
        ServerLevel over = level.getServer().overworld();
        List<Poi> list = all();
        for (SiteData.Entry e : SiteData.get(over).entries(KIND)) {
            if (e.built && e.index >= 0 && e.index < list.size() && list.get(e.index).kind().equals(kind)) {
                BlockPos front = Blueprint.transform(0, 0, dist, jp.revati.skyabyss.opening.site.SiteFinder.rot(e.yaw),
                        jp.revati.skyabyss.opening.site.SiteFinder.mirror(e.yaw), e.pos);
                return new BlockPos[]{front, e.pos};
            }
        }
        return null;
    }

    // 見どころ kind の置き場 (pois.json の x・z。y は 0)
    @org.jetbrains.annotations.Nullable
    public static BlockPos placeOf(String kind) {
        for (Poi p : all()) {
            if (p.kind().equals(kind)) {
                return new BlockPos(p.x(), 0, p.z());
            }
        }
        return null;
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
        chest.setItem(10, new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get(), 2 + r.nextInt(4)));
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
    static void foes(ServerLevel level, SiteData.Entry e, Poi poi) {
        String kind = poi.kind();
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
        // 骸の歩兵・弓兵と、野盗・弓手 (docs/design/44。土地の強さは ContinentDanger が掛ける)
        EntityType<?>[] kinds = undead ? new EntityType<?>[] {jp.revati.skyabyss.registry.ModEntities.BONE_FOOTMAN.get(), jp.revati.skyabyss.registry.ModEntities.BONE_BOWMAN.get(),
                jp.revati.skyabyss.registry.ModEntities.BONE_FOOTMAN.get()}
                : new EntityType<?>[] {jp.revati.skyabyss.registry.ModEntities.BANDIT_ARCHER.get(), jp.revati.skyabyss.registry.ModEntities.BANDIT_ARCHER.get(),
                jp.revati.skyabyss.registry.ModEntities.BANDIT.get()};
        // 頭 (一体。骸の旗手か野盗の頭目、名前つき、倒すと土地の粉と鐘銭。onLeaderDrops)
        // はじまりの島 (最初の戦い) には頭を出さない
        EntityType<?> leaderType = undead ? jp.revati.skyabyss.registry.ModEntities.BONE_STANDARD.get() : jp.revati.skyabyss.registry.ModEntities.BANDIT_CHIEF.get();
        if (ContinentMap.get().region(at.getX(), at.getZ()) != ContinentMap.OUTSKIRTS && leaderType.create(level) instanceof Mob leader) {
            int y = below ? at.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
            leader.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            leader.finalizeSpawn(level, level.getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.EVENT, null);
            // 野盗の頭目の兄弟は名で呼ぶ (docs/design/40 の 3.3)
            leader.setCustomName(net.minecraft.network.chat.Component.translatable(!poi.chief().isEmpty() ? "entity.skyabyss.bandit_brother." + poi.chief()
                    : undead ? "entity.skyabyss.poi_leader_bone" : "entity.skyabyss.poi_leader_bandit"));
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

    // 大陸 (地上) にいるか
    public static boolean in(Level level) {
        return level instanceof ServerLevel && level.dimension() == Level.OVERWORLD;
    }
}
