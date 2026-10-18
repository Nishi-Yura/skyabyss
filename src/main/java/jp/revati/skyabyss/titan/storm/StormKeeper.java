package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanKeeper;
import jp.revati.skyabyss.titan.TitanWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 雷翼の巨神の出現と見守り (12 の 2.1節と 2.8節)。仕組みは巨神で共通の TitanKeeper。雷翼に固有のこと:
// - 出現: 潮殻の心臓をワールドの誰かが止めたあと (Progression.worldAllows)、そこから最初の夜明け (日の番号が変わって、朝の 0〜1000) に一度だけ。
//   選んだ人から 300〜420 ブロック、地表 + 60 と 150 の高い方の高さ。出現のとき、外にいる人に遠い雷を一度だけ鳴らす
// - 呼び直しの位置も同じ探し方 (300〜420)。背中 (storm_back) に人がいる間は呼び直さない
// - 近くの人 (240 ブロック以内) には、伝承「雷翼の巨神」を記す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormKeeper {
    public static final String SLOT = "thunder_wing";
    static final double SPAWN_MIN = 300;
    static final double SPAWN_MAX = 420;
    static final double LORE_RANGE = 240;
    static final String LORE_ENTRY = "chapter1/thunder_wing";
    // 記録のキー: 潮殻の心臓を止めた日 (出現は、その次の夜明けから)
    private static final String KEY_ALLOWED_DAY = "AllowedDay";

    private static long nextTry;

    public static final TitanKeeper<StormWingEntity> KEEPER = new TitanKeeper<>(SLOT, StormWingEntity.class,
            "skyabyss_thunder_wing", "thunder wing titan") {
        @Override
        protected StormWingEntity create(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
            StormWingEntity titan = StormSetup.THUNDER_WING.get().create(level);
            if (titan == null) {
                throw new IllegalStateException("Failed to create the thunder wing titan");
            }
            titan.initCentre(x, z, StormWingEntity.CRUISE_MIN_Y, level.random);
            if (state != null && !state.isEmpty()) {
                titan.restoreState(state);
            }
            return titan;
        }

        @Override
        protected CompoundTag saveState(StormWingEntity titan) {
            return titan.saveState();
        }

        @Override
        @Nullable
        protected Vec3 relocationPoint(ServerLevel level, ServerPlayer target) {
            return spawnPoint(level, target);
        }

        @Override
        protected boolean someoneAboard(ServerLevel overworld) {
            ServerLevel back = overworld.getServer().getLevel(StormTravel.STORM_BACK);
            return back != null && jp.revati.skyabyss.titan.group.Activity.anyoneAboard(back);
        }

        @Override
        protected void announce(ServerLevel level, StormWingEntity titan) {
            // 知らせない。最初の出会いは「遠くの嵐が、風に逆らって動いていた」と自分で気づく形にする
        }
    };

    private StormKeeper() {
    }

    // 人から 300〜420 ブロック離れた、読み込まれた点 (高さは地表 + 60 と 150 の高い方)。読み込まれていなければ null
    @Nullable
    static Vec3 spawnPoint(ServerLevel level, ServerPlayer target) {
        for (int i = 0; i < 16; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = SPAWN_MIN + level.random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
            double x = target.getX() + Math.cos(a) * d;
            double z = target.getZ() + Math.sin(a) * d;
            double surface = StormPerch.surfaceAt(level, x, z);
            if (!Double.isNaN(surface)) {
                return new Vec3(x, Math.max(surface + 60, 150), z);
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        KEEPER.tick(level);
        if (level.getGameTime() % 20 == 0) {
            tellLore(level);
        }
        if (level.getGameTime() % 200 != 0 || level.getGameTime() < nextTry || KEEPER.slot(level).spawned) {
            return;
        }
        if (!mayAppearNow(level)) {
            return;
        }
        ServerPlayer player = TitanKeeper.randomEligible(level);
        Vec3 at = player == null ? null : spawnPoint(level, player);
        if (at == null) {
            nextTry = level.getGameTime() + 1200;
            return;
        }
        StormWingEntity titan = KEEPER.spawn(level, at.x, at.z, null);
        thunderFar(level, titan);
    }

    // 出現してよいか: 潮殻の心臓を誰かが止めていて、記された日から見て次の夜明け (朝の 0〜1000) を迎えたあと
    static boolean mayAppearNow(ServerLevel level) {
        if (!Progression.worldAllows(level.getServer(), Titans.THUNDER_WING)) {
            return false;
        }
        TitanWorldData.Slot slot = KEEPER.slot(level);
        long day = level.getDayTime() / 24000L;
        if (!slot.state.contains(KEY_ALLOWED_DAY)) {
            // 初めて許された日を覚える (その日の夜明けは、まだ次)
            slot.state.putLong(KEY_ALLOWED_DAY, day);
            TitanWorldData.get(level).setDirty();
            return false;
        }
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        return day > slot.state.getLong(KEY_ALLOWED_DAY) && dayTime < 1000;
    }

    // 出現のとき、屋外の人に遠い雷を一度だけ (320 ブロック以内)
    static void thunderFar(ServerLevel level, StormWingEntity titan) {
        for (ServerPlayer player : level.players()) {
            if (TitanKeeper.eligible(player) && player.distanceToSqr(titan.getX(), player.getY(), titan.getZ()) < 320 * 320
                    && level.canSeeSky(player.blockPosition().above())) {
                player.playNotifySound(StormSetup.THUNDER_FAR.get(), SoundSource.AMBIENT, 0.7f, 0.9f);
            }
        }
    }

    // 近くの人 (240 ブロック以内) に伝承を記す (一度だけ。記されていれば何もしない)
    private static void tellLore(ServerLevel level) {
        StormWingEntity titan = KEEPER.current(level);
        if (titan == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (TitanKeeper.eligible(player) && player.distanceToSqr(titan.getX(), player.getY(), titan.getZ()) < LORE_RANGE * LORE_RANGE) {
                Lore.unlock(player, LORE_ENTRY, true);
            }
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        KEEPER.handleJoin(event);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        KEEPER.reset();
        nextTry = 0;
    }
}
