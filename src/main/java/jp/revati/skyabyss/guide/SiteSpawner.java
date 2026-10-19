package jp.revati.skyabyss.guide;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 場所ごとの湧き台 (docs/design/21 の 1.5、P3)。小さなダンジョン・集め場など、地上の場所の敵を出す共通の仕組み。章の担当 (P6 など) は
// 場所を登録するだけで、湧かせ方・グループごとの見え方・クリアの記録・宝・周回が揃う。
//
// ---------------------------------------------------------------- 口
//   登録   SiteSpawner.register(new Def(id, chapter, pads, xp, treasure, repeatTreasure, maxMobs, origins))
//            pads     : 湧き台 (Pad(room, 場所の基点からのずれ dx dy dz, 湧かせる敵 Spawn(entity id, count) の並び))。room は部屋の名前。
//                       pads の並びの最後の部屋 (lastRoom) が、クリア後の 1 日ごとに戻る部屋
//            xp       : 初回のクリアの経験値 (人ごとに一度。章の枠 ChapterXpBudget に数える)
//            treasure : 初回のクリアの宝 (SiteTreasure の id。人ごとに一度)。repeatTreasure: 周回の素材の表 (何度でも)
//            origins  : 場所の基点の一覧。ChapterSites の要素なら SiteSpawner.originsOf(要素の ID) (全部の拠点の分。建て終わった場所だけ)
//   決まり
//     湧く     人 (観戦者でない・生きている) が基点から 48 ブロック以内に来たとき、その人のグループ (パーティ、または一人) の分だけ湧かせる。
//              敵はグループの印 (GroupOwned.setGroupId) つき: ほかのグループには見えず・狙われず・傷を受けない (GroupEvents)。
//              同じ場所に二つのグループが同時に入れる。敵は保存しない (サーバーが止まる・読み込みから戻った個体は消す)。
//              湧かせる敵は GroupOwned を実装していること (GuardMob の系統など)。していなければ登録の時点で警告し、湧かせる
//     上限     一つの場所・一つのグループで同時に生きている敵は maxMobs (既定 12) まで。残りは、倒れたぶん順に湧く
//     クリア   湧かせた敵がすべて倒れたら、そのとき基点の近く (96 以内) にいたグループの人全員に、人ごとの記録 (site_clear.<id>) を付ける。
//              初めての人には経験値 (Growth.discover の鍵) と宝 (SiteTreasure.claim) と合図 `site_clear:<id>` (クエストの signal の目的)
//     再訪     クリアした人だけのグループには、前に素材を受け取ってから 24000 tick たつと、最後の部屋だけ敵が戻る。倒すと周回の素材 (repeatTreasure。
//              経験値なし)。まだクリアしていない人が近くにいれば全部の部屋 (その人の初回)
//     離れる   グループの人が基点から 96 ブロック以上離れて 30 秒たつと、敵を消す (進みは捨てる。戻れば湧き直す)
//   確かめ  SELFTEST site cave (guide/GuideSelfTest)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteSpawner {
    public static final String TAG = "skyabyss_site_mob";
    public static final double ACTIVATE = 48;
    public static final double CREDIT = 96;
    public static final int IDLE_DESPAWN = 600;
    public static final int MAX_MOBS = 12;
    public static final long REPEAT_TICKS = 24000L;
    public static final int EVERY = 20;

    public record Spawn(String entity, int count) {
    }

    public record Pad(String room, int dx, int dy, int dz, List<Spawn> spawns) {
    }

    // yawOf: 場所の基点 → ChapterSites の向き (SiteFinder の符号化。回す・反転する)。部屋の位置 (pads のずれ) を部品の向きに合わせて回すときだけ渡す。
    //        null なら回さない (ずれはそのまま世界の座標)
    public record Def(String id, Chapters chapter, List<Pad> pads, int xp, @Nullable String treasure, @Nullable String repeatTreasure, int maxMobs,
                      Function<MinecraftServer, List<BlockPos>> origins, @Nullable java.util.function.ToIntFunction<BlockPos> yawOf) {
        public Def(String id, Chapters chapter, List<Pad> pads, int xp, @Nullable String treasure, @Nullable String repeatTreasure, int maxMobs,
                   Function<MinecraftServer, List<BlockPos>> origins) {
            this(id, chapter, pads, xp, treasure, repeatTreasure, maxMobs, origins, null);
        }

        // 湧き台 pad の世界の位置 (基点 + 向きに合わせて回したずれ)
        public BlockPos padAt(Pad pad, BlockPos origin) {
            if (yawOf == null) {
                return origin.offset(pad.dx(), pad.dy(), pad.dz());
            }
            int yaw = yawOf.applyAsInt(origin);
            return jp.revati.skyabyss.opening.site.Blueprint.transform(pad.dx(), pad.dy(), pad.dz(), jp.revati.skyabyss.opening.site.SiteFinder.rot(yaw),
                    jp.revati.skyabyss.opening.site.SiteFinder.mirror(yaw), origin);
        }

        public String lastRoom() {
            return pads.isEmpty() ? "" : pads.get(pads.size() - 1).room();
        }
    }

    // 一つの場所・一つのグループの湧き (メモリだけ)
    static final class Instance {
        final Def def;
        final BlockPos origin;
        final UUID group;
        final boolean repeat;
        final Deque<Task> pending = new ArrayDeque<>();
        final Map<UUID, BlockPos> live = new LinkedHashMap<>();
        int spawned;
        int idle;

        Instance(Def def, BlockPos origin, UUID group, boolean repeat) {
            this.def = def;
            this.origin = origin;
            this.group = group;
            this.repeat = repeat;
        }
    }

    record Task(Pad pad, String entity) {
    }

    private static final Map<String, Def> DEFS = new ConcurrentHashMap<>();
    // 今この実行で湧かせた個体 (保存されて戻ってきた個体と区別する)
    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();
    private static final Map<String, Instance> INSTANCES = new HashMap<>();

    private SiteSpawner() {
    }

    // ---------------------------------------------------------------- 登録
    public static void register(Def def) {
        for (Pad pad : def.pads()) {
            for (Spawn s : pad.spawns()) {
                EntityType<?> type = type(s.entity());
                if (type == null) {
                    SkyAbyss.LOGGER.warn("SiteSpawner {}: unknown entity {} in room {}", def.id(), s.entity(), pad.room());
                }
            }
        }
        DEFS.put(def.id(), def);
    }

    public static void unregister(String id) {
        DEFS.remove(id);
        INSTANCES.entrySet().removeIf(e -> e.getValue().def.id().equals(id));
    }

    public static Collection<Def> defs() {
        return DEFS.values();
    }

    @Nullable
    public static Def def(String id) {
        return DEFS.get(id);
    }

    // ChapterSites の要素 (小さなダンジョンの入口など) の、建て終わった場所の基点 (全部の拠点の分)
    public static Function<MinecraftServer, List<BlockPos>> originsOf(String elementId) {
        return server -> {
            List<BlockPos> list = new ArrayList<>();
            for (ChapterSites.Site s : ChapterSites.sites(server, elementId)) {
                if (s.built()) {
                    list.add(s.pos());
                }
            }
            return list;
        };
    }

    @Nullable
    static EntityType<?> type(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
    }

    // 場所の敵か (GroupEvents が、背中の外でも別のグループの傷・狙いを取り消すのに使う)
    public static boolean isSiteMob(@Nullable Entity entity) {
        return entity != null && entity.getTags().contains(TAG);
    }

    // ---------------------------------------------------------------- tick
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!DEFS.isEmpty() && server.getTickCount() % EVERY == 3) {
            ServerLevel overworld = server.overworld();
            tick(overworld, Players.snapshot(overworld.players()), overworld.getGameTime());
        }
    }

    // 呼び出しは 20 tick ごと。players は基点の近くを探す人 (自己テストは偽のプレイヤーを渡す)
    public static void tick(ServerLevel level, Collection<? extends ServerPlayer> players, long now) {
        MinecraftServer server = level.getServer();
        Set<String> seen = new HashSet<>();
        for (Def def : List.copyOf(DEFS.values())) {
            for (BlockPos origin : def.origins().apply(server)) {
                // この場所の近くにいる人を、グループごとに
                Map<UUID, List<ServerPlayer>> near = new LinkedHashMap<>();
                Map<UUID, List<ServerPlayer>> credit = new LinkedHashMap<>();
                for (ServerPlayer p : players) {
                    if (p.level() != level || !p.isAlive() || p.isSpectator()) {
                        continue;
                    }
                    double d2 = p.distanceToSqr(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                    UUID g = Groups.keyOf(p);
                    if (d2 <= ACTIVATE * ACTIVATE) {
                        near.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
                    }
                    if (d2 <= CREDIT * CREDIT) {
                        credit.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
                    }
                }
                for (var entry : near.entrySet()) {
                    for (ServerPlayer p : entry.getValue()) {
                        SiteProgressData.get(server).markFound(p.getUUID(), def.id());
                    }
                    String key = key(def, origin, entry.getKey());
                    seen.add(key);
                    Instance inst = INSTANCES.get(key);
                    if (inst == null) {
                        Boolean repeat = mode(server, def, entry.getValue(), now);
                        if (repeat == null) {
                            continue;
                        }
                        inst = new Instance(def, origin, entry.getKey(), repeat);
                        fill(inst);
                        INSTANCES.put(key, inst);
                    }
                    step(level, inst, credit.getOrDefault(entry.getKey(), entry.getValue()), now);
                }
            }
        }
        // 人が離れた場所の湧き
        for (var it = INSTANCES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Instance inst = e.getValue();
            if (!DEFS.containsKey(inst.def.id())) {
                discardMobs(level, inst);
                it.remove();
                continue;
            }
            boolean anyone = false;
            for (ServerPlayer p : players) {
                if (p.level() == level && p.isAlive() && !p.isSpectator() && inst.group.equals(Groups.keyOf(p))
                        && p.distanceToSqr(inst.origin.getX() + 0.5, inst.origin.getY(), inst.origin.getZ() + 0.5) <= CREDIT * CREDIT) {
                    anyone = true;
                    break;
                }
            }
            if (anyone) {
                inst.idle = 0;
            } else if ((inst.idle += EVERY) >= IDLE_DESPAWN) {
                discardMobs(level, inst);
                it.remove();
            }
        }
    }

    private static String key(Def def, BlockPos origin, UUID group) {
        return def.id() + "@" + origin.asLong() + "#" + group;
    }

    // 湧かせるか、どの形か。null = 湧かせない、false = 全部の部屋 (初回)、true = 最後の部屋だけ (周回)
    @Nullable
    static Boolean mode(MinecraftServer server, Def def, List<ServerPlayer> near, long now) {
        SiteProgressData data = SiteProgressData.get(server);
        boolean anyFirst = false;
        boolean anyRepeat = false;
        for (ServerPlayer p : near) {
            if (!data.cleared(p.getUUID(), def.id())) {
                anyFirst = true;
            } else if (repeatReady(data, p, def.id(), now)) {
                anyRepeat = true;
            }
        }
        return anyFirst ? Boolean.FALSE : anyRepeat ? Boolean.TRUE : null;
    }

    // クリアしたあと 1 日たったか (受け取った記録がなければ、いつでも)
    static boolean repeatReady(SiteProgressData data, ServerPlayer p, String site, long now) {
        long at = data.repeatAt(p.getUUID(), site);
        return at == Long.MIN_VALUE || now - at >= REPEAT_TICKS;
    }

    private static void fill(Instance inst) {
        for (Pad pad : inst.def.pads()) {
            if (inst.repeat && !pad.room().equals(inst.def.lastRoom())) {
                continue;
            }
            for (Spawn s : pad.spawns()) {
                for (int i = 0; i < s.count(); i++) {
                    inst.pending.add(new Task(pad, s.entity()));
                }
            }
        }
    }

    private static void step(ServerLevel level, Instance inst, List<ServerPlayer> credit, long now) {
        // 倒れた個体を数える (読み込まれていない所の個体は、いるものとして残す)
        for (var it = inst.live.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity mob = level.getEntity(e.getKey());
            // 見つからない個体は、その場所が動いている (エンティティが tick する) ときだけ倒れたとみなす。読み込まれていない所の個体は、いるものとして残す
            if (mob == null ? level.isPositionEntityTicking(e.getValue()) : !mob.isAlive()) {
                LIVE.remove(e.getKey());
                it.remove();
            }
        }
        int cap = Math.max(1, inst.def.maxMobs());
        while (!inst.pending.isEmpty() && inst.live.size() < cap) {
            Task task = inst.pending.poll();
            Mob mob = spawn(level, inst, task);
            if (mob != null) {
                inst.live.put(mob.getUUID(), mob.blockPosition());
                inst.spawned++;
            }
        }
        if (inst.pending.isEmpty() && inst.live.isEmpty()) {
            cleared(level, inst, credit, now);
            INSTANCES.values().remove(inst);
        }
    }

    @Nullable
    private static Mob spawn(ServerLevel level, Instance inst, Task task) {
        EntityType<?> type = type(task.entity());
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
        BlockPos at = inst.def.padAt(task.pad(), inst.origin);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(inst.group);
        } else {
            SkyAbyss.LOGGER.warn("SiteSpawner {}: {} is not GroupOwned, so every group can see it", inst.def.id(), task.entity());
        }
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        LIVE.add(mob.getUUID());
        if (!level.addFreshEntity(mob)) {
            LIVE.remove(mob.getUUID());
            return null;
        }
        return mob;
    }

    private static void discardMobs(ServerLevel level, Instance inst) {
        for (UUID id : new ArrayList<>(inst.live.keySet())) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
            LIVE.remove(id);
        }
        inst.live.clear();
        inst.pending.clear();
    }

    // ---------------------------------------------------------------- クリア
    private static void cleared(ServerLevel level, Instance inst, List<ServerPlayer> credit, long now) {
        MinecraftServer server = level.getServer();
        SiteProgressData data = SiteProgressData.get(server);
        Def def = inst.def;
        for (ServerPlayer p : credit) {
            if (inst.repeat) {
                // 周回の素材 (経験値なし)。受け取れるのは 1 日ぶん待った人だけ
                if (data.cleared(p.getUUID(), def.id()) && repeatReady(data, p, def.id(), now)) {
                    data.markRepeat(p.getUUID(), def.id(), now);
                    SiteTreasure.Table table = def.repeatTreasure() == null ? null : SiteTreasure.table(def.repeatTreasure());
                    if (table != null) {
                        SiteTreasure.give(p, table);
                    }
                }
                continue;
            }
            // 初回。すでにクリアした人も、待ちが明けていれば周回の素材を受け取る
            if (data.markCleared(p.getUUID(), def.id())) {
                data.markRepeat(p.getUUID(), def.id(), now);
                if (def.xp() > 0 && Growth.discover(p, "site:" + def.id())) {
                    Growth.addXp(p, ChapterXpBudget.allowSite(def.chapter(), def.xp()), "growth.skyabyss.reason.site");
                }
                if (def.treasure() != null) {
                    SiteTreasure.claim(p, def.treasure());
                }
                Quests.signal(p, signal(def.id()));
            } else if (repeatReady(data, p, def.id(), now)) {
                data.markRepeat(p.getUUID(), def.id(), now);
                SiteTreasure.Table table = def.repeatTreasure() == null ? null : SiteTreasure.table(def.repeatTreasure());
                if (table != null) {
                    SiteTreasure.give(p, table);
                }
            }
        }
    }

    // クリアの合図 (クエストの signal の目的の target)
    public static String signal(String siteId) {
        return "site_clear:" + siteId;
    }

    // ---------------------------------------------------------------- 状態 (自己テスト・調べ用)
    public static int liveCount(String siteId, BlockPos origin, UUID group) {
        Def def = DEFS.get(siteId);
        Instance inst = def == null ? null : INSTANCES.get(key(def, origin, group));
        return inst == null ? 0 : inst.live.size();
    }

    public static int pendingCount(String siteId, BlockPos origin, UUID group) {
        Def def = DEFS.get(siteId);
        Instance inst = def == null ? null : INSTANCES.get(key(def, origin, group));
        return inst == null ? 0 : inst.pending.size();
    }

    public static boolean hasInstance(String siteId, BlockPos origin, UUID group) {
        Def def = DEFS.get(siteId);
        return def != null && INSTANCES.containsKey(key(def, origin, group));
    }

    public static List<UUID> liveIds(String siteId, BlockPos origin, UUID group) {
        Def def = DEFS.get(siteId);
        Instance inst = def == null ? null : INSTANCES.get(key(def, origin, group));
        return inst == null ? List.of() : new ArrayList<>(inst.live.keySet());
    }

    // 自己テストが片付けるとき: すべての湧きを消す
    public static void clearAll(ServerLevel level) {
        for (Instance inst : List.copyOf(INSTANCES.values())) {
            discardMobs(level, inst);
        }
        INSTANCES.clear();
    }

    // ---------------------------------------------------------------- 保存して戻ってきた個体を消す
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!event.getLevel().isClientSide && e.getTags().contains(TAG) && !LIVE.contains(e.getUUID()) && !ChapterMobSpawner.isLive(e.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        INSTANCES.clear();
        LIVE.clear();
    }
}
