package jp.revati.skyabyss.titan.tide.chapter;

import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteJob;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.ChapterCards;
import jp.revati.skyabyss.story.arc.Foreshadow;
import jp.revati.skyabyss.story.arc.ForeshadowRegisterEvent;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideOcean;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 二章「引く海」(潮殻。01 の 4.2、08 の 2.8節) の中身。章の部品 (予兆の枠・小要素の基盤・空の段・余韻の札) へ登録するだけで、共有のファイルは触らない。
//   予兆 3 拍 (段階 1 の人にだけ)  ①塩の積み石  ②遠い貝の音と海鳥  ③沖の島が動いている、甲羅の縁
//   小要素 4 つ                    塩の積み石 (高い=朝・低い=夕)、漂着の荷 (潮見の貝をもらう)、浜の碑
//   中盤の出来事                    大干潮 (TideEbb)
//   空の段                          ごくわずかに月が近く、空に青緑 (ChapterSky)
//   余韻                            潮殻の心臓を止めた人に、章の終わりの札 (人ごとに一度)
public final class TideChapter {
    public static final String CAIRN_A = "ch2_salt_cairn_a";
    public static final String CAIRN_B = "ch2_salt_cairn_b";
    public static final String CARGO = "ch2_drift_cargo";
    public static final String STELE = "ch2_beach_stele";

    public static final String SIGNAL_CAIRN = "ch2_cairn";
    public static final String SIGNAL_CARGO = "ch2_cargo";
    public static final String SIGNAL_STELE = "ch2_stele";

    public static final String LORE_DRIFT = "chapter1/ch2_drift_log";
    public static final String LORE_STELE = "chapter1/ch2_beach_stele";

    // 潮見の貝を渡したかの印 (人の持続データ)
    static final String SHELL_GIVEN = "skyabyss_tide_watching_shell_given";

    public static final String BEAT_CAIRNS = "salt_cairns";
    public static final String BEAT_CONCH = "far_conch";
    public static final String BEAT_RIM = "shell_rim";

    // 予兆の距離 (ブロック)
    static final double CAIRN_NEAR = 28;
    static final double SHORE_NEAR = 24;
    static final double RIM_MIN = 40;
    static final double RIM_MAX = 420;

    private TideChapter() {
    }

    // ---------------------------------------------------------------- 巨神の場所
    // 沖の島の位置 (現れていなければ null。読み込まれていなければ、最後の位置)
    @Nullable
    public static Vec3 titanPos(net.minecraft.server.MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        TitanWorldData.Slot slot = TideKeeper.KEEPER.slot(overworld);
        if (!slot.spawned) {
            return null;
        }
        TideShellEntity titan = TideKeeper.KEEPER.current(overworld);
        return titan != null ? titan.position() : slot.lastPosition();
    }

    // ---------------------------------------------------------------- 予兆
    static boolean daylight(long dayTime) {
        return Beat.Time.DAY.matches(dayTime) || Beat.Time.DAWN.matches(dayTime);
    }

    static boolean twilight(long dayTime) {
        return Beat.Time.DAWN.matches(dayTime) || Beat.Time.DUSK.matches(dayTime);
    }

    // 積み石の近くにいる (小要素がまだ置かれていない人は、水辺にいれば足りる)
    static boolean nearCairn(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        List<ChapterSites.Site> all = new java.util.ArrayList<>(ChapterSites.sitesFor(p.server, p.getUUID(), CAIRN_A));
        all.addAll(ChapterSites.sitesFor(p.server, p.getUUID(), CAIRN_B));
        if (all.isEmpty()) {
            return p.level() instanceof ServerLevel level && TideConchItem.nearWater(level, p.getX(), p.getZ(), (int) SHORE_NEAR);
        }
        for (ChapterSites.Site s : all) {
            if (Math.hypot(s.pos().getX() - at.getX(), s.pos().getZ() - at.getZ()) <= CAIRN_NEAR) {
                return true;
            }
        }
        return false;
    }

    // 海のある向き (巨神がいればその向き、なければ近い海、それもなければ人ごとに決まった向き)
    static Vec3 seaDirection(ServerPlayer p) {
        Vec3 t = titanPos(p.server);
        if (t != null) {
            Vec3 d = new Vec3(t.x - p.getX(), 0, t.z - p.getZ());
            if (d.lengthSqr() > 1) {
                return d.normalize();
            }
        }
        if (p.level() instanceof ServerLevel level) {
            Vec3 sea = TideOcean.findOceanPoint(level, level.random, p.getX(), p.getZ(), 60, 240, 6, 24);
            if (sea != null) {
                return new Vec3(sea.x - p.getX(), 0, sea.z - p.getZ()).normalize();
            }
        }
        double a = (p.getUUID().hashCode() & 0xffff) / 65536.0 * Math.PI * 2;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    private static Beat cairns() {
        return Beat.of(Chapters.TIDE, 1, BEAT_CAIRNS, (p, b) -> {
            Notice.narrate(p, "message.skyabyss.ch2.beat.cairn");
            return true;
        }).when(p -> daylight(p.level().getDayTime()) && nearCairn(p));
    }

    private static Beat farConch() {
        return Beat.of(Chapters.TIDE, 2, BEAT_CONCH, (p, b) -> {
            if (!(p.level() instanceof ServerLevel level)) {
                return false;
            }
            Vec3 d = seaDirection(p);
            Vec3 at = p.position().add(d.scale(44));
            level.playSound(null, at.x, p.getY() + 2, at.z, TideChapterSetup.CONCH_FAR.get(), SoundSource.AMBIENT, 4.0f, 0.9f);
            Notice.narrate(p, "message.skyabyss.ch2.beat.conch");
            return true;
        }).when(p -> twilight(p.level().getDayTime()) && !p.isUnderWater() && p.level().canSeeSky(p.blockPosition()));
    }

    private static Beat shellRim() {
        return Beat.of(Chapters.TIDE, 3, BEAT_RIM, (p, b) -> {
            Notice.narrate(p, "message.skyabyss.ch2.beat.rim");
            return true;
        }).distance(p -> titanPos(p.server), RIM_MIN, RIM_MAX)
                .when(p -> TideOcean.ticksUntilLowTide(p.level().getDayTime()) <= 1200 && p.level().canSeeSky(p.blockPosition()));
    }

    // ---------------------------------------------------------------- 小要素
    // 漂着の荷の箱の中身 (潮見の貝は人ごとに渡すので、箱には入れない)
    static void fillCargo(SiteJob job) {
        BlockPos chest = job.mark("chest");
        if (chest != null && job.level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
            be.setItem(0, new ItemStack(Items.DRIED_KELP, 6));
            be.setItem(1, new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.TOWN_BREAD.get(), 3));
            be.setItem(2, new ItemStack(Items.TORCH, 6));
            be.setItem(4, new ItemStack(jp.revati.skyabyss.item.outfit.ConsumableGear.DRIED_FISH.get(), 2));
        } else {
            SkyAbyss.LOGGER.error("Tide drift cargo: no chest at {}", chest);
        }
    }

    // 潮見の貝を一人に一つ渡す (荷に着いたとき。渡したら true。なくしたら /skyabyss tide conch で出し直せる)
    public static boolean giveShell(ServerPlayer p) {
        if (p.getPersistentData().getBoolean(SHELL_GIVEN)) {
            return false;
        }
        p.getPersistentData().putBoolean(SHELL_GIVEN, true);
        ItemStack shell = new ItemStack(TideChapterSetup.TIDE_WATCHING_SHELL.get());
        jp.revati.skyabyss.pouch.PouchRouting.give(p, shell);
        if (!shell.isEmpty()) {
            p.drop(shell, false);
        }
        return true;
    }

    private static void registerSites() {
        ChapterSites.element(CAIRN_A, Chapters.TIDE, "tide_cairn_a").atGround().dist(30, 110).prefer(ChapterSites.Prefer.SHORE).arriveRadius(8)
                .onArrive((p, s) -> {
                    Notice.hint(p, "message.skyabyss.ch2.cairn.tall");
                    Quests.signal(p, SIGNAL_CAIRN);
                }).register();
        ChapterSites.element(CAIRN_B, Chapters.TIDE, "tide_cairn_b").atGround().dist(40, 120).prefer(ChapterSites.Prefer.SHORE).arriveRadius(8)
                .onArrive((p, s) -> {
                    Notice.hint(p, "message.skyabyss.ch2.cairn.low");
                    Quests.signal(p, SIGNAL_CAIRN);
                }).register();
        ChapterSites.element(CARGO, Chapters.TIDE, "tide_cargo").atGround().dist(50, 130).prefer(ChapterSites.Prefer.SHORE).arriveRadius(8)
                .post(TideChapter::fillCargo)
                .onArrive((p, s) -> {
                    Lore.unlock(p, LORE_DRIFT, true);
                    if (giveShell(p)) {
                        Notice.narrate(p, "message.skyabyss.ch2.cargo.shell");
                    } else {
                        Notice.narrate(p, "message.skyabyss.ch2.cargo.page");
                    }
                    Quests.signal(p, SIGNAL_CARGO);
                }).register();
        ChapterSites.element(STELE, Chapters.TIDE, "tide_stele").atGround().dist(60, 140).prefer(ChapterSites.Prefer.SHORE).arriveRadius(6)
                .onArrive((p, s) -> {
                    Lore.unlock(p, LORE_STELE, true);
                    Notice.hint(p, "message.skyabyss.ch2.stele");
                    Quests.signal(p, SIGNAL_STELE);
                }).register();
    }

    // ---------------------------------------------------------------- 余韻
    // 潮殻の心臓を自分で止めた人 (今は三章) に、章の終わりの札を一度だけ。予兆を一つでも見た人にだけ (すでに先を遊んでいた古い人には出さない)
    static boolean aftermath(ServerPlayer p) {
        if (Chapters.of(p) != Chapters.STORM || !Progression.stopped(p.server, p.getUUID(), Chapters.TIDE.titan())
                || Foreshadow.count(p, Chapters.TIDE) == 0) {
            return false;
        }
        if (ChapterCards.aftermath(p, Chapters.TIDE)) {
            Notice.narrate(p, "message.skyabyss.ch2.aftermath");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 登録の口
    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        static void onForeshadow(ForeshadowRegisterEvent event) {
            event.register(cairns());
            event.register(farConch());
            event.register(shellRim());
        }

        @SubscribeEvent
        static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                registerSites();
                // 空の段は「ごくわずか」: 月が少し近く、空に青緑が薄く (一章 0.01/0.03 → 二章 0.02/0.05 → 三章 → 四章と順に濃くなる)
                ChapterSky.setStage(Chapters.TIDE, new ChapterSky.Stage(0.02f, 0x4A7C8C, 0.05f));
            });
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        private Events() {
        }

        @SubscribeEvent
        static void onPlayerTick(PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer) {
                return;
            }
            if (sp.tickCount % 100 == 53) {
                aftermath(sp);
            }
        }
    }
}
