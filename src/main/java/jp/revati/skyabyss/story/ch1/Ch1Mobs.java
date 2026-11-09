package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneHauler;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 一章の地上の敵の差し込み口と湧かせ方 (P7)。
//   岩殻虫 (rock_shell_beetle)  ひとり歩きの湧き。丘・山・荒れ地、昼夜。割れ殻の洞の入口の間
//   棘うさぎ (spine_hare)       ひとり歩きの湧き。草原・平野の夜。割れ殻の洞の棘の間
//   老いた歩哨 (elder_sentry)   割れ殻の洞の奥の間 (CleftCave)
//   骨負い (bone_hauler)        湧き台なし。骨の集め場 (ch1_bone_cache) のまわりを、夕方〜夜に一体だけ歩かせる (下の HaulerWalk)。
//                               場所の湧き台 (SiteSpawner) とも、ひとり歩き (ChapterMobSpawner) とも別: 敵意がなく、集め場へ人を導く役
// 洞の湧き台は、新しい敵が登録されていればそれ、なければ今ある背中の敵 (代用) を使う。今は新しい敵が入っているので代用は使われない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch1Mobs {
    public static final String ROCK_SHELL_BEETLE = "skyabyss:rock_shell_beetle";
    public static final String SPINE_HARE = "skyabyss:spine_hare";
    public static final String BONE_HAULER = "skyabyss:bone_hauler";
    public static final String ELDER_SENTRY = "skyabyss:elder_sentry";
    // 代用 (新しい敵が登録されていないときだけ。P7 で入ったので使われない)
    public static final String STAND_IN_BEETLE = "skyabyss:moss_crawler";
    public static final String STAND_IN_HARE = "skyabyss:rib_archer";
    public static final String STAND_IN_ELDER = "skyabyss:bone_sentinel";

    // ひとり歩きは薄く: ここで通った試行のうち、この割合だけ湧く (ChapterMobSpawner は 2 秒ごとに 1 回試す)
    public static final double BEETLE_THIN = 0.3;
    public static final double HARE_THIN = 0.3;
    // 骨負い: 集め場に人がこの近さにいる夕方〜夜に、集め場の中心から RING の範囲へ一体
    public static final double HAULER_WATCH = 90;
    public static final int HAULER_RING_MIN = 4;
    public static final int HAULER_RING_MAX = 9;
    public static final double HAULER_KEEP = 28;
    // 導く骨負い (2026-11-08。issue #15「骨負いが骨の集め場へ導かない」): 集め場から LEAD_FAR より遠く HAULER_WATCH の内にいる人のそばに、
    // 人と集め場のあいだ (人から LEAD_AHEAD 先) に一体を出す。人を見ると集め場へ逃げる (BoneHauler.fleeDestination) ので、追えば着く。
    // 骨負いから SEEN の内に来た人には、サブクエスト「骨を負う獣」(ch1/follow_hauler) を始める。集め場に着くと済み (Sites の到着)
    public static final double LEAD_FAR = 30;
    public static final double LEAD_AHEAD = 14;
    public static final double SEEN = 20;
    public static final String HAULER_QUEST = "ch1/follow_hauler";
    public static final String HAULER_SIGNAL = "ch1_bone_cache";
    public static final String HAULER_LORE = "ch1/follow_hauler";
    private static final int HAULER_EVERY = 100;

    private Ch1Mobs() {
    }

    // P7 の敵が登録されていればそれ、なければ代用
    public static String pick(String p7, String standIn) {
        return Ch1Prep.entityExists(p7) ? p7 : standIn;
    }

    // ---------------------------------------------------------------- ひとり歩き
    public static boolean beetleBiome(Holder<Biome> b) {
        return b.is(BiomeTags.IS_HILL) || b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_BADLANDS) || b.is(Biomes.STONY_SHORE) || b.is(Biomes.STONY_PEAKS);
    }

    public static boolean hareBiome(Holder<Biome> b) {
        return b.is(Biomes.PLAINS) || b.is(Biomes.SUNFLOWER_PLAINS) || b.is(Biomes.MEADOW) || b.is(Biomes.SNOWY_PLAINS) || b.is(Biomes.SAVANNA);
    }

    private static Predicate<Holder<Biome>> thin(Predicate<Holder<Biome>> biome, double chance) {
        return b -> biome.test(b) && ThreadLocalRandom.current().nextDouble() < chance;
    }

    static void registerWanderers() {
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch1_rock_shell_beetle", Chapters.WHALE, ROCK_SHELL_BEETLE, 5,
                ChapterMobSpawner.Time.ANY, thin(Ch1Mobs::beetleBiome, BEETLE_THIN)));
        ChapterMobSpawner.register(new ChapterMobSpawner.Wanderer("ch1_spine_hare", Chapters.WHALE, SPINE_HARE, 5,
                ChapterMobSpawner.Time.NIGHT, thin(Ch1Mobs::hareBiome, HARE_THIN)));
    }

    // ---------------------------------------------------------------- 骨負い (骨の集め場のまわりを歩く)
    // 夕方〜夜か (一日の時刻 11500〜23000。純粋)
    public static boolean haulerHours(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 11500 && t < 23000;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % HAULER_EVERY != 53) {
            return;
        }
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = Players.snapshot(level.players());
        for (BlockPos cache : SiteSpawner.originsOf(Ch1Prep.BONE_CACHE).apply(server)) {
            syncHauler(level, cache, players);
            noticeHaulers(level, cache, players);
        }
    }

    // 骨負いを見た人に、ついて行くクエストを始める (集め場をまだ見つけていない人だけ)
    public static int noticeHaulers(ServerLevel level, BlockPos cache, List<ServerPlayer> players) {
        int started = 0;
        for (BoneHauler h : haulersAt(level, cache)) {
            for (ServerPlayer p : players) {
                if (p.level() == level && !p.isSpectator() && p.distanceToSqr(h) <= SEEN * SEEN
                        && !jp.revati.skyabyss.lore.Lore.state(p).has(HAULER_LORE) && jp.revati.skyabyss.quest.Quests.start(p, HAULER_QUEST)) {
                    jp.revati.skyabyss.notice.Notice.hint(p, "message.skyabyss.ch1.hauler.seen");
                    started++;
                }
            }
        }
        return started;
    }

    // 骨負いを出す所: 集め場から遠い人がいれば、その人から集め場の方へ LEAD_AHEAD 先 (導く)。いなければ集め場のまわり。null なら集め場のまわり
    @javax.annotation.Nullable
    static ServerPlayer leadFor(BlockPos cache, List<ServerPlayer> players) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = Math.hypot(p.getX() - cache.getX(), p.getZ() - cache.getZ());
            if (!p.isSpectator() && d > LEAD_FAR && d <= HAULER_WATCH && d < bestD && !jp.revati.skyabyss.lore.Lore.state(p).has(HAULER_LORE)) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    // 集め場のまわりの骨負いを、夕方〜夜で人が近くにいれば一体に、そうでなければ 0 にそろえる。そろえたあとの数を返す (自己テストからも)
    public static int syncHauler(ServerLevel level, BlockPos cache, List<ServerPlayer> players) {
        boolean near = false;
        for (ServerPlayer p : players) {
            if (p.level() == level && !p.isSpectator()
                    && Math.hypot(p.getX() - cache.getX(), p.getZ() - cache.getZ()) <= HAULER_WATCH) {
                near = true;
                break;
            }
        }
        List<BoneHauler> now = haulersAt(level, cache);
        boolean want = near && haulerHours(level.getDayTime()) && level.getDifficulty() != Difficulty.PEACEFUL;
        if (!want) {
            for (BoneHauler h : now) {
                level.sendParticles(ParticleTypes.POOF, h.getX(), h.getY() + 0.6, h.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
                h.discard();
            }
            return 0;
        }
        for (int i = 1; i < now.size(); i++) {
            now.get(i).discard();
        }
        if (!now.isEmpty()) {
            return 1;
        }
        ServerPlayer lead = leadFor(cache, players);
        if (lead != null && lead.level() == level) {
            double dx = cache.getX() - lead.getX();
            double dz = cache.getZ() - lead.getZ();
            double len = Math.max(1, Math.hypot(dx, dz));
            BlockPos at = BlockPos.containing(lead.getX() + dx / len * LEAD_AHEAD, cache.getY(), lead.getZ() + dz / len * LEAD_AHEAD);
            if (spawnHauler(level, cache, at, 0, 3)) {
                return 1;
            }
        }
        return spawnHauler(level, cache) ? 1 : 0;
    }

    // 骨負いを数える範囲: 集め場から導く所まで (HAULER_WATCH) を含める
    public static List<BoneHauler> haulersAt(ServerLevel level, BlockPos cache) {
        AABB box = new AABB(cache).inflate(HAULER_WATCH + 8, 40, HAULER_WATCH + 8);
        return level.getEntitiesOfClass(BoneHauler.class, box, h -> h.isAlive() && h.getTags().contains(GuardMob.TEMP_TAG));
    }

    static boolean spawnHauler(ServerLevel level, BlockPos cache) {
        return spawnHauler(level, cache, cache, HAULER_RING_MIN, HAULER_RING_MAX);
    }

    // center のまわり (rMin〜rMax) に一体。帰る先は集め場
    static boolean spawnHauler(ServerLevel level, BlockPos cache, BlockPos center, int rMin, int rMax) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = rMin + level.random.nextDouble() * (rMax - rMin);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
            if (!level.hasChunkAt(new BlockPos(x, cache.getY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty() || !level.getBlockState(feet.below()).isSolid()) {
                continue;
            }
            BoneHauler hauler = ModEntities.BONE_HAULER.get().create(level);
            if (hauler == null) {
                return false;
            }
            hauler.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            hauler.addTag(GuardMob.TEMP_TAG);
            hauler.setHome(new BlockPos(cache.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cache.getX(), cache.getZ()), cache.getZ()));
            hauler.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.EVENT, null);
            level.addFreshEntity(hauler);
            return true;
        }
        return false;
    }
}
