package jp.revati.skyabyss.titan.ember;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.ForeshadowRegisterEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 四章「足もとから来るもの」(余燼。docs/design/13 の 17 章、01 の 4.2、計画 chapter_per_titan_plan.md の 1.7) の登録。
// 四章 = 雷翼の心臓を止めたあと (段階 3)。登録するもの:
//   予兆 3 拍 (段階 3 の人にだけ。Foreshadow の枠。ForeshadowRegisterEvent で登録)
//     1 痕跡  warm_ground  朝、足もとの土がほんのりと温かい (粒と一行)
//     2 間接  deep_hum     夕から夜、桶の水がぬるく、地の底で低いものが鳴る (低い音と小さな揺れ)
//     3 一部  ridge_moves  昼、遠くの丘の煙が昨日と違う所から上がる (巨神の尾根が近くにあるときだけ。粒と一行)
//   小要素 (ChapterSites。拠点から 150 ブロック以内。敵は足さない。経験値は合わせて 800)
//     ember_hearth_ruin  灰の民の炉跡 (樽に前の私の頁。伝承「灰の民の炉跡」)
//     ember_ash_post     灰降り観測点 (杭の目盛りに灰)
//     ember_warm_ledge   温かい岩棚 (樽に温石が一つ。なくした人のため。温石は 13 の 2.6)
//   暮らしの変化 (N3) 寝床のまわりだけ地面が温かい (粒、初めての一行。建築は触らない)
//   空の段 ChapterSky.setStage(EMBER, ...) 空と遠景をうすく灰赤へ寄せ、月の近さに少し足す
// 中盤の出来事は EmberRising、余韻と巨神編の終わりは EmberAftermath、NPC の筋はデータ (quest/ch4、dialogue/ch4)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberChapter {
    public static final String BEAT_GROUND = "warm_ground";
    public static final String BEAT_HUM = "deep_hum";
    public static final String BEAT_RIDGE = "ridge_moves";
    public static final List<String> BEATS = List.of(Chapters.EMBER.id() + "/" + BEAT_GROUND, Chapters.EMBER.id() + "/" + BEAT_HUM,
            Chapters.EMBER.id() + "/" + BEAT_RIDGE);

    public static final String SITE_HEARTH = "ember_hearth_ruin";
    public static final String SITE_POST = "ember_ash_post";
    public static final String SITE_LEDGE = "ember_warm_ledge";
    public static final List<String> SITES = List.of(SITE_HEARTH, SITE_POST, SITE_LEDGE);
    public static final String PAGE_SOURCE = "ember_hearth";
    public static final String LORE_HEARTH = "chapter1/ember_hearth";
    public static final String LORE_PREV_PAGE = "chapter1/ember_prev_page";
    public static final int SITE_XP = 300;
    public static final int POST_XP = 250;
    public static final int LEDGE_XP = 250;

    public static final String LINE_GROUND = "message.skyabyss.ember_ch4.beat.ground";
    public static final String LINE_HUM = "message.skyabyss.ember_ch4.beat.hum";
    public static final String LINE_RIDGE = "message.skyabyss.ember_ch4.beat.ridge";
    public static final String LINE_HEARTH = "message.skyabyss.ember_ch4.site.hearth";
    public static final String LINE_POST = "message.skyabyss.ember_ch4.site.post";
    public static final String LINE_LEDGE = "message.skyabyss.ember_ch4.site.ledge";
    public static final String LINE_BASE = "message.skyabyss.ember_ch4.warm_base";
    public static final String BASE_KEY = "skyabyss_ember_warm_base";

    // 空の段: 月の近さへの加算、灰赤への寄せ
    public static final ChapterSky.Stage SKY = new ChapterSky.Stage(0.08f, 0x8A4A30, 0.12f);

    // 巨神の尾根を見られる距離 (3 拍目)
    public static final double RIDGE_SEEN = 220;
    // 寝床のまわりの温かさ (N3)
    public static final double BASE_RADIUS = 12;

    private EmberChapter() {
    }

    // ---------------------------------------------------------------- 登録
    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ChapterSky.setStage(Chapters.EMBER, SKY);
            registerSites();
        });
    }

    @SubscribeEvent
    static void onForeshadow(ForeshadowRegisterEvent event) {
        for (Beat b : beats()) {
            event.register(b);
        }
    }

    static List<Beat> beats() {
        // 予兆は段階 3 (雷翼を止めて、余燼の心臓はまだ) の人にだけ。心臓を止めたあと (段階 4、鍵がまだ) の人にはもう出さない
        return List.of(
                Beat.of(Chapters.EMBER, 1, BEAT_GROUND, (p, b) -> warmGround(p)).time(Beat.Time.DAWN).when(EmberChapter::inForeshadowStage),
                Beat.of(Chapters.EMBER, 2, BEAT_HUM, (p, b) -> deepHum(p)).time(Beat.Time.DUSK).when(EmberChapter::inForeshadowStage),
                Beat.of(Chapters.EMBER, 3, BEAT_RIDGE, (p, b) -> ridgeMoves(p)).time(Beat.Time.DAY)
                        .when(p -> inForeshadowStage(p) && titanRidge(p) != null));
    }

    static boolean inForeshadowStage(ServerPlayer player) {
        return Progression.stage(player) == Chapters.EMBER.stage();
    }

    private static void registerSites() {
        ChapterSites.element(SITE_HEARTH, Chapters.EMBER, "ember_hearth_ruin").atGround().dist(50, 130).arriveRadius(14)
                .post(EmberChapter::finishHearth).onArrive((p, s) -> arrive(p, "hearth", SITE_XP, LINE_HEARTH, true)).register();
        ChapterSites.element(SITE_POST, Chapters.EMBER, "ember_ash_post").atGround().dist(40, 120).arriveRadius(12)
                .post(job -> jp.revati.skyabyss.story.ch4.Ch4Ground.clearFloatingPlants(job, 4))
                .onArrive((p, s) -> arrive(p, "post", POST_XP, LINE_POST, false)).register();
        ChapterSites.element(SITE_LEDGE, Chapters.EMBER, "ember_warm_ledge").atGround().dist(60, 140).arriveRadius(12)
                .post(EmberChapter::finishLedge).onArrive((p, s) -> arrive(p, "ledge", LEDGE_XP, LINE_LEDGE, false)).register();
        // 灰の碑 3 枚 (背の外の探索の碑。story/ChapterTablets)
        jp.revati.skyabyss.story.ChapterTablets.register(jp.revati.skyabyss.story.ChapterTablets.EMBER);
    }

    // ---------------------------------------------------------------- 予兆
    // 1 痕跡: 朝、足もとから灰色の細い湯気が立つ
    static boolean warmGround(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        level.sendParticles(player, ParticleTypes.WHITE_ASH, false, player.getX(), player.getY() + 0.2, player.getZ(), 30, 3, 0.1, 3, 0.01);
        level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, false, player.getX(), player.getY() + 0.1, player.getZ(), 4, 2.5, 0.0, 2.5, 0.01);
        Notice.narrate(player, LINE_GROUND);
        return true;
    }

    // 2 間接: 地の底の低い音と、ごく小さな揺れ (巨神はまだ現れていなくてよい。音だけ)
    static boolean deepHum(ServerPlayer player) {
        player.playNotifySound(EmberTitanSetup.RUMBLE.get(), SoundSource.AMBIENT, 0.5f, 0.5f);
        EmberSurfaceNetwork.send(player, new EmberSurfaceNetwork.Tremor(40, 0.15f));
        Notice.narrate(player, LINE_HUM);
        return true;
    }

    // 3 一部: 巨神の尾根が近くにあるとき (ないあいだは見送り、あとで起こす)。丘の煙が昨日と違う所から上がる
    static boolean ridgeMoves(ServerPlayer player) {
        Vec3 ridge = titanRidge(player);
        if (ridge == null || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, ridge.x, ridge.y, ridge.z, 12, 3, 2, 3, 0.02);
        level.sendParticles(player, ParticleTypes.WHITE_ASH, true, ridge.x, ridge.y - 2, ridge.z, 40, 8, 3, 8, 0.02);
        Notice.narrate(player, LINE_RIDGE);
        return true;
    }

    // 人の近く (RIDGE_SEEN 以内) にいる余燼の巨神の尾根の点。いなければ null
    static Vec3 titanRidge(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        EmberTitanEntity titan = EmberKeeper.KEEPER.current(level);
        if (titan == null) {
            return null;
        }
        Vec3 ridge = titan.ridgePoint();
        return Math.hypot(ridge.x - player.getX(), ridge.z - player.getZ()) <= RIDGE_SEEN ? ridge : null;
    }

    // ---------------------------------------------------------------- 小要素
    private static void finishHearth(SiteJob job) {
        jp.revati.skyabyss.story.ch4.Ch4Ground.clearFloatingPlants(job, 5);
        BlockPos barrel = job.mark("barrel");
        if (barrel != null && job.level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
            be.setItem(0, LorePageItem.of(PAGE_SOURCE));
        } else {
            SkyAbyss.LOGGER.error("Ember hearth ruin: no barrel at {}", barrel);
        }
    }

    private static void finishLedge(SiteJob job) {
        jp.revati.skyabyss.story.ch4.Ch4Ground.clearFloatingPlants(job, 4);
        BlockPos barrel = job.mark("barrel");
        if (barrel != null && job.level.getBlockEntity(barrel) instanceof BarrelBlockEntity be) {
            be.setItem(0, new ItemStack(EmberTitanSetup.WARM_STONE.get()));
        } else {
            SkyAbyss.LOGGER.error("Ember warm ledge: no barrel at {}", barrel);
        }
    }

    // 着いたとき (その章の人ごとに一度)。語り、小さな経験値 (一度だけ)、炉跡なら伝承
    static void arrive(ServerPlayer player, String key, int xp, String line, boolean lore) {
        Notice.narrate(player, line);
        if (lore) {
            Lore.unlock(player, LORE_HEARTH, true);
        }
        if (Growth.discover(player, "ch4:" + key)) {
            Growth.addXp(player, xp, "growth.skyabyss.reason.new_sight");
        }
    }

    // ---------------------------------------------------------------- 暮らしの変化 (N3): 寝床のまわりだけ地面が温かい (粒。建築は触らない)
    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        @SubscribeEvent
        static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 60 != 21 || !player.isAlive()) {
                return;
            }
            warmBase(player);
        }
    }

    // 段階 3 の人が、寝床 (オーバーワールドの復活地点) のそばにいるとき。粒は毎回、一行は初めてだけ。出したら true
    static boolean warmBase(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || player.isSpectator()
                || Chapters.of(player) != Chapters.EMBER || Progression.stage(player) != Chapters.EMBER.stage()) {
            return false;
        }
        BlockPos base = player.getRespawnPosition();
        if (base == null || player.getRespawnDimension() != Level.OVERWORLD
                || Math.hypot(base.getX() - player.getX(), base.getZ() - player.getZ()) > BASE_RADIUS) {
            return false;
        }
        level.sendParticles(player, ParticleTypes.WHITE_ASH, false, player.getX(), player.getY() + 0.1, player.getZ(), 3, 2, 0.0, 2, 0.0);
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(BASE_KEY)) {
            data.putBoolean(BASE_KEY, true);
            Notice.narrate(player, LINE_BASE);
        }
        return true;
    }
}
