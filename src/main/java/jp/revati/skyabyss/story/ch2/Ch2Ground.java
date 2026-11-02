package jp.revati.skyabyss.story.ch2;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.SiteTreasure;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.ChapterSites.Prefer;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.tablet.Tablets;
import jp.revati.skyabyss.titan.tide.TideOcean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 二章の地上。二章の跡地 (都の東の浜) に世界で一つずつ置く。
//   支度の段 (メインの段。順不同。PrepQuests の章の表)
//     ch2/salt_road (塩の道。語り部)  塩の道の標 5 (SaltVault が蔵から浜へ並べる) → 沈んだ塩の蔵の奥の刻み (伝承 ch2/salt_vault) + 潮の番人 (蔵のクリア)
//     ch2/ebb_boat  (船と貝。イサナ)  船を作る (#minecraft:boats) → 沖の難破船 (Wreck) の箱で潮の民の頁 (ch2/wreck_page) + 潮見の貝を持つ
//     二本そろうと合図 ch2_ready (進捗 tide_trail)
//   場所 (ChapterSites.atGround。跡地の中心からの距離)
//     沈んだ塩の蔵 ch2_salt_vault (90〜150、水辺)    小さなダンジョン (SaltVault)。入口の間・塩樽の間・奥の間。潮の喉は世界の潮で乾く/沈む (ふさがない)
//     潮だまりの浜 ch2_tidal_pools ×2 (30〜120、水辺) 潮だまり貝 2 (湧き台)。クリアで小真珠
//     漂着の筏の跡 ch2_drift_raft (30〜110、水辺)    人ごとに一度の抽選と頁 ch2/drift_raft、合図 ch2_raft。旅の職人 (二章の装い) が立つ
//     引き潮の観測の杭 ch2_ebb_stakes (50〜140、水辺) 杭を触ると次の引き潮・満ち潮までの分と、大干潮は夜の前半という一行
//     沖の難破船 (Wreck。場所の表ではなく、跡地の海の向きの浅い沖に、人が近づいたとき一度だけ置く)
//   文は assets/skyabyss_ch2/lang、部品・クエスト・会話・伝承は データを直接直す（生成スクリプトはない）
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch2Ground {
    // 支度の段 (PrepQuests.ids(2) と同じ並び)
    public static final String Q_SALT = "ch2/salt_road";
    public static final String Q_BOAT = "ch2/ebb_boat";
    public static final String Q_DRIFT = "ch2/drift_trail";
    public static final String Q_WADER = "ch2/wader_gear";
    public static final List<String> TRAILS = List.of(Q_SALT, Q_BOAT);

    // 場所の要素 ID
    public static final String VAULT = "ch2_salt_vault";
    public static final String POOLS = "ch2_tidal_pools";
    public static final String RAFT = "ch2_drift_raft";
    public static final String STAKES = "ch2_ebb_stakes";
    public static final List<String> SITES = List.of(VAULT, POOLS, RAFT, STAKES);

    // 伝承 (lore/entry/ch2/)
    public static final String LORE_VAULT = "ch2/salt_vault";
    public static final String LORE_RAFT = "ch2/drift_raft";
    public static final String LORE_WRECK = "ch2/wreck_page";
    public static final String LORE_MEMORY = "ch2/why_they_rode";
    public static final List<String> LORE = List.of(LORE_RAFT, LORE_VAULT, LORE_WRECK, LORE_MEMORY);

    // 合図
    public static final String SIGNAL_MARK = "ch2_salt_mark";
    public static final String SIGNAL_RAFT = "ch2_raft";
    public static final String SIGNAL_STAKES = "ch2_stakes";

    // 宝 (SiteTreasure)
    public static final String RAFT_TREASURE = "ch2_drift_raft";
    public static final String POOL_TREASURE = "ch2_tidal_pool";
    public static final String POOL_REPEAT = "ch2_tidal_pool_repeat";

    public static final String STAKES_SIGN = "sign.skyabyss.ch2_ebb_stakes";

    // 敵の ID (地上の敵の担当 Ch2Mobs と同じ名前。まだ登録されていなければ、潮殻の背の敵を代わりに使う)
    public static final String CLAM = "skyabyss:tidepool_clam";
    public static final String BURROWER = "skyabyss:sand_burrower";
    public static final String WARDEN = "skyabyss:shore_warden";

    private Ch2Ground() {
    }

    public static boolean entityExists(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.ENTITY_TYPE.containsKey(key);
    }

    public static String pick(String id, String standIn) {
        return entityExists(id) ? id : standIn;
    }

    // 二章 (段階 1) の人か
    public static boolean inChapter(ServerPlayer p) {
        return Progression.stage(p) == 1;
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Ch2Ground::register);
    }

    static void register() {
        SaltVault.register();
        // 潮だまりの浜: 二つ。潮だまり貝 2 の湧き台 (経験値なし。クリアで小真珠)
        SiteTreasure.register(POOL_TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:small_pearl", 2),
                new SiteTreasure.Entry("skyabyss:shell_chip", 1))));
        SiteTreasure.register(POOL_REPEAT, SiteTreasure.Table.of(List.of(), 1, 1, List.of(new SiteTreasure.Entry("skyabyss:small_pearl", 1, 2, 2),
                new SiteTreasure.Entry("skyabyss:salt_crystal", 1, 1, 1))));
        ChapterSites.element(POOLS, Chapters.TIDE, "ch2_tidal_pool").atGround().count(2).dist(30, 120).prefer(Prefer.SHORE).arriveRadius(8)
                .post(SaltVault::rememberYaw)
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch2.arrive." + POOLS)).register();
        String clam = pick(CLAM, "skyabyss:reef_scuttler");
        SiteSpawner.register(new SiteSpawner.Def(POOLS, Chapters.TIDE, List.of(
                new SiteSpawner.Pad("pools", -2, 0, 2, List.of(new SiteSpawner.Spawn(clam, 1))),
                new SiteSpawner.Pad("pools", 2, 0, -1, List.of(new SiteSpawner.Spawn(clam, 1)))),
                0, POOL_TREASURE, POOL_REPEAT, 4, SiteSpawner.originsOf(POOLS), SaltVault::yawAt));
        // 漂着の筏の跡: 人ごとに一度の抽選と頁。職人が立つ所 (印 artisan)
        SiteTreasure.register(RAFT_TREASURE, SiteTreasure.Table.of(List.of(new SiteTreasure.Entry("skyabyss:small_pearl", 3),
                new SiteTreasure.Entry("skyabyss:salt_crystal", 2), new SiteTreasure.Entry("skyabyss:shell_chip", 2),
                new SiteTreasure.Entry("skyabyss:dried_fish", 4))));
        ChapterSites.element(RAFT, Chapters.TIDE, RAFT).atGround().dist(30, 110).prefer(Prefer.SHORE).arriveRadius(8)
                .post(job -> {
                    BlockPos at = job.mark("artisan");
                    if (at != null) {
                        job.entry.extra("artisan", at);
                    }
                    SaltVault.rememberYaw(job);
                })
                .onArrive((p, s) -> arriveRaft(p)).register();
        ArtisanSpawner.register(new ArtisanSpawner.Spot(2, RAFT, "artisan", Ch2Ground::inChapter));
        // 引き潮の観測の杭 (岬)
        ChapterSites.element(STAKES, Chapters.TIDE, STAKES).atGround().dist(50, 140).prefer(Prefer.SHORE).arriveRadius(8)
                .post(job -> {
                    BlockPos tablet = job.mark("tablet");
                    if (tablet != null) {
                        Tablets.write(job.level, tablet, STAKES_SIGN);
                    }
                })
                .onArrive((p, s) -> Notice.narrate(p, "message.skyabyss.ch2.arrive." + STAKES)).register();
        Ch2Trails.register();
        jp.revati.skyabyss.opening.site.SiteNetwork.EXTRA_TARGETS.put(Wreck.TARGET, Wreck::spot);
    }

    // 筏の跡に着いた: 合図、一度の抽選、頁
    public static void arriveRaft(ServerPlayer p) {
        Notice.narrate(p, "message.skyabyss.ch2.arrive." + RAFT);
        Quests.signal(p, SIGNAL_RAFT);
        boolean fresh = !Lore.state(p).has(LORE_RAFT);
        SiteTreasure.claim(p, RAFT_TREASURE);
        if (fresh) {
            Tasks.later(50, () -> {
                Notice.narrate(p, "message.skyabyss.ch2.raft.found");
                Lore.unlock(p, LORE_RAFT, true);
            });
        }
    }

    // ---------------------------------------------------------------- 観測の杭
    // 次の引き潮 (または満ち潮) までの分。純粋 (自己テストからも)
    public static long minutesUntilTurn(long dayTime) {
        if (TideOcean.isLowTide(dayTime)) {
            long t = 0;
            while (t < 24000 && TideOcean.isLowTide(dayTime + t)) {
                t += 20;
            }
            return (t + 1199) / 1200;
        }
        return (TideOcean.ticksUntilLowTide(dayTime) + 1199) / 1200;
    }

    // 杭を読む (自己テストからも)。初めて読んだら合図
    public static void readStakes(ServerPlayer p) {
        long day = p.serverLevel().getDayTime();
        long min = minutesUntilTurn(day);
        Notice.narrate(p, TideOcean.isLowTide(day) ? "message.skyabyss.ch2.stakes.now" : "message.skyabyss.ch2.stakes.read", Long.toString(min));
        Tasks.later(40, () -> Notice.narrate(p, "message.skyabyss.ch2.stakes.ebb"));
        if (!p.getPersistentData().getBoolean("skyabyss_ch2_stakes_read")) {
            p.getPersistentData().putBoolean("skyabyss_ch2_stakes_read", true);
            Quests.signal(p, SIGNAL_STAKES);
        }
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }
        var state = level.getBlockState(event.getPos());
        if (!state.is(Blocks.STRIPPED_DARK_OAK_LOG) && !state.is(Blocks.LIGHT_BLUE_CONCRETE)) {
            return;
        }
        for (ChapterSites.Site s : ChapterSites.sites(level.getServer(), STAKES)) {
            if (s.built() && Math.abs(s.pos().getX() - event.getPos().getX()) <= 4 && Math.abs(s.pos().getZ() - event.getPos().getZ()) <= 4) {
                level.playSound(null, event.getPos(), SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.6f, 1.2f);
                readStakes(player);
                event.setCanceled(true);
                return;
            }
        }
    }

    static MinecraftServer server(ServerPlayer p) {
        return p.server;
    }
}
