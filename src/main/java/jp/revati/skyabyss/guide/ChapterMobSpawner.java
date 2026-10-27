package jp.revati.skyabyss.guide;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 地上のひとり歩きの小物 (P3)。自然の湧きは増やさず、その章の人の近くだけに薄く湧かせる。
//   登録   ChapterMobSpawner.register(new Wanderer(id, chapter, entity, weight, Time))
//   決まり
//     湧く     その章 (Chapters.of(人)) の人が、その章の拠点 (ChapterSites の計画) から 300 ブロック以内にいるとき、その人の 24〜64 ブロック先の地面に、
//              2 秒ごとに 1 回だけ試す。1 チャンクあたり 1 体まで、1 人のグループあたり同時に 8 体まで (負荷)
//     グループ  湧かせた人のグループの印 (GroupOwned) つき。ほかのグループには見えず・狙われない。ほかの章の人の地面は荒らさない
//     消える   グループの人が 128 ブロック以内にいなくなったら消す。保存しない (SiteSpawner の TAG と同じ扱い)
//   名づき・巣・群れは場所の湧き台 (SiteSpawner)。ここはひとり歩きの小物だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ChapterMobSpawner {
    public enum Time {
        ANY, DAY, NIGHT
    }

    // biome: 湧く地形の条件 (null ならどこでも)
    public record Wanderer(String id, Chapters chapter, String entity, int weight, Time time,
                           @Nullable java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biome) {
        public Wanderer(String id, Chapters chapter, String entity, int weight, Time time) {
            this(id, chapter, entity, weight, time, null);
        }
    }

    public static final double ANCHOR_RANGE = 300;
    public static final int MIN_DIST = 24;
    public static final int MAX_DIST = 64;
    public static final int PER_CHUNK = 1;
    public static final int PER_GROUP = 8;
    public static final double KEEP_RANGE = 128;
    public static final int EVERY = 40;

    private static final List<Wanderer> WANDERERS = new CopyOnWriteArrayList<>();
    private static final Map<UUID, UUID> LIVE = new ConcurrentHashMap<>();

    // 自己テストが差し替える口 (本番は null = 今の章と SiteData の拠点)
    @Nullable
    static java.util.function.Function<ServerPlayer, Chapters> chapterOverride;
    @Nullable
    static java.util.function.BiFunction<ServerPlayer, Chapters, BlockPos> anchorOverride;

    private ChapterMobSpawner() {
    }

    public static void register(Wanderer w) {
        WANDERERS.removeIf(x -> x.id().equals(w.id()));
        WANDERERS.add(w);
    }

    public static void unregister(String id) {
        WANDERERS.removeIf(x -> x.id().equals(id));
    }

    // 登録済みのひとり歩き (自己テスト用)
    public static List<Wanderer> registered() {
        return List.copyOf(WANDERERS);
    }

    public static boolean isLive(UUID entity) {
        return LIVE.containsKey(entity);
    }

    public static Set<UUID> liveIds() {
        return Set.copyOf(LIVE.keySet());
    }

    // ---------------------------------------------------------------- 章と拠点
    static Chapters chapterOf(ServerPlayer player) {
        return chapterOverride != null ? chapterOverride.apply(player) : Chapters.of(player);
    }

    // その人が使う、その章の拠点 (なければ null)
    @Nullable
    static BlockPos anchorOf(ServerPlayer player, Chapters chapter) {
        if (anchorOverride != null) {
            return anchorOverride.apply(player, chapter);
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (SiteData.ChapterPlan plan : SiteData.get(player.server.overworld()).plans()) {
            if (!plan.chapter.equals(chapter.id())) {
                continue;
            }
            double d = player.distanceToSqr(plan.anchor.getX() + 0.5, player.getY(), plan.anchor.getZ() + 0.5);
            if (d < bestD) {
                bestD = d;
                best = plan.anchor;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- tick
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!WANDERERS.isEmpty() && server.getTickCount() % EVERY == 11) {
            ServerLevel overworld = server.overworld();
            tick(overworld, Players.snapshot(overworld.players()), overworld.getGameTime());
        }
    }

    // 2 秒ごとに呼ぶ。人ごとに一度だけ湧かせる。players は自己テストなら偽のプレイヤーも渡せる
    public static void tick(ServerLevel level, Collection<? extends ServerPlayer> players, long now) {
        sweep(level, players);
        if (WANDERERS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : players) {
            if (player.level() != level || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            Chapters chapter = chapterOf(player);
            BlockPos anchor = chapter == null ? null : anchorOf(player, chapter);
            if (anchor == null || !withinAnchor(player.position().x, player.position().z, anchor)) {
                continue;
            }
            Wanderer pick = pick(chapter, level.random.nextInt(1000), dayTime(level));
            if (pick != null) {
                trySpawn(level, player, pick);
            }
        }
    }

    static boolean withinAnchor(double x, double z, BlockPos anchor) {
        double dx = x - (anchor.getX() + 0.5);
        double dz = z - (anchor.getZ() + 0.5);
        return dx * dx + dz * dz <= ANCHOR_RANGE * ANCHOR_RANGE;
    }

    private static long dayTime(ServerLevel level) {
        return level.getDayTime() % 24000L;
    }

    static boolean timeOk(Time time, long dayTime) {
        boolean night = dayTime >= 13000 && dayTime < 23000;
        return time == Time.ANY || (time == Time.NIGHT) == night;
    }

    // 重みで選ぶ (章と時刻に合うものだけ。r は 0〜999 の乱数)
    @Nullable
    static Wanderer pick(Chapters chapter, int r, long dayTime) {
        List<Wanderer> ok = new ArrayList<>();
        int total = 0;
        for (Wanderer w : WANDERERS) {
            if (w.chapter() == chapter && timeOk(w.time(), dayTime) && w.weight() > 0) {
                ok.add(w);
                total += w.weight();
            }
        }
        if (total == 0) {
            return null;
        }
        int pick = r % total;
        for (Wanderer w : ok) {
            pick -= w.weight();
            if (pick < 0) {
                return w;
            }
        }
        return null;
    }

    // 湧かせてみる。湧いたら true
    static boolean trySpawn(ServerLevel level, ServerPlayer player, Wanderer w) {
        UUID group = Groups.keyOf(player);
        if (liveOf(group) >= PER_GROUP) {
            return false;
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = MIN_DIST + level.random.nextDouble() * (MAX_DIST - MIN_DIST);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        if (countInChunk(level, new ChunkPos(new BlockPos(x, 0, z))) >= PER_CHUNK) {
            return false;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);
        if (!level.getBlockState(at.below()).isSolid() || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
                || !level.getFluidState(at.below()).isEmpty()) {
            return false;
        }
        if (w.biome() != null && !w.biome().test(level.getBiome(at))) {
            return false;
        }
        EntityType<?> type = SiteSpawner.type(w.entity());
        if (type == null) {
            return false;
        }
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) {
                e.discard();
            }
            return false;
        }
        mob.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        mob.addTag(SiteSpawner.TAG);
        LIVE.put(mob.getUUID(), group);
        if (!level.addFreshEntity(mob)) {
            LIVE.remove(mob.getUUID());
            return false;
        }
        return true;
    }

    static int liveOf(UUID group) {
        int n = 0;
        for (UUID g : LIVE.values()) {
            if (g.equals(group)) {
                n++;
            }
        }
        return n;
    }

    // このチャンクにいる (ここで湧かせた) 個体の数
    static int countInChunk(ServerLevel level, ChunkPos chunk) {
        AABB box = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(),
                chunk.getMaxBlockZ() + 1);
        return level.getEntitiesOfClass(Mob.class, box, m -> LIVE.containsKey(m.getUUID())).size();
    }

    // 死んだ個体の記録を落とし、グループの人が 128 ブロック以内にいなくなった個体を消す
    static void sweep(ServerLevel level, Collection<? extends ServerPlayer> players) {
        Map<UUID, List<ServerPlayer>> byGroup = new HashMap<>();
        for (ServerPlayer p : players) {
            if (p.level() == level && p.isAlive() && !p.isSpectator()) {
                byGroup.computeIfAbsent(Groups.keyOf(p), k -> new ArrayList<>()).add(p);
            }
        }
        for (var it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity mob = level.getEntity(e.getKey());
            if (mob == null) {
                // 倒れた・チャンクごと読み込みから外れた。記録を落とす (ディスクから戻ってきても、LIVE にないので入れない: SiteSpawner.onJoin)
                it.remove();
                continue;
            }
            if (!mob.isAlive()) {
                it.remove();
                continue;
            }
            boolean kept = false;
            for (ServerPlayer p : byGroup.getOrDefault(e.getValue(), List.of())) {
                if (p.distanceToSqr(mob) <= KEEP_RANGE * KEEP_RANGE) {
                    kept = true;
                    break;
                }
            }
            if (!kept) {
                mob.discard();
                it.remove();
            }
        }
    }

    // 自己テストが片付けるとき
    public static void clearAll(ServerLevel level) {
        for (UUID id : List.copyOf(LIVE.keySet())) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        LIVE.clear();
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LIVE.clear();
        chapterOverride = null;
        anchorOverride = null;
    }
}
