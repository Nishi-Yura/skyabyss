package jp.revati.skyabyss.titan.storm;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 地上と「嵐の上」(雷翼の背、専用のディメンション storm_back) を行き来させる (12 の 2.4〜2.5節)。
// - 乗る: 昇り風の頂 (GaleColumnEntity) か、コマンドで board する。資格 (Progression.checkBoard) を見て、島を建てて、到着へ渡す
// - 戻る: 背中の羽根の門の口に 0.5 秒いると戻る。止まっていれば止まり木の足もと、止まっていなければ最後に止まった足もと、
//   記録もなければ共有スポーン地点。低速落下 10 秒
// - 嵐の雲の天井 (CLOUD_Y) より下へ落ちると、白い閃光のあと休み場 (なければ到着) へ戻し、低速落下 5 秒。傷は 2 (試練の中は 3)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormTravel {
    public static final ResourceKey<Level> STORM_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));

    // 渡ってすぐに、また門に入って行き来しないように (tick)
    private static final String TAG_COOLDOWN = "skyabyss_storm_cooldown";
    private static final int COOLDOWN = 60;
    private static final int GATE_DWELL = 10;
    private static final int FLASH_TICKS = 30;
    private static final int LEAVE_SLOW_FALL = 200;
    private static final int FALL_SLOW_FALL = 100;
    private static final float FALL_DAMAGE = 2f;
    private static final float TRIAL_FALL_DAMAGE = 3f;

    // 背中の羽根の門の口にいる人 → 何 tick いるか
    private static final Map<UUID, Integer> IN_GATE = new HashMap<>();

    private StormTravel() {
    }

    private static boolean coolingDown(ServerPlayer player) {
        return player.getPersistentData().getLong(TAG_COOLDOWN) > player.server.overworld().getGameTime();
    }

    private static void startCooldown(ServerPlayer player) {
        player.getPersistentData().putLong(TAG_COOLDOWN, player.server.overworld().getGameTime() + COOLDOWN);
    }

    // 背中へ渡す (昇り風の頂・コマンドから)。資格がなければ断る
    public static boolean board(ServerPlayer player) {
        if (!Progression.checkBoard(player, Titans.THUNDER_WING)) {
            startCooldown(player);
            return false;
        }
        ServerLevel back = player.server.getLevel(STORM_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("Storm back dimension is missing");
            return false;
        }
        StormBackBuilder.ensureBuilt(back);
        startCooldown(player);
        IN_GATE.remove(player.getUUID());
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), StormSetup.THUNDER_FAR.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
        }
        PacketDistributor.sendToPlayer(player, new StormNetwork.Flash(FLASH_TICKS));
        BlockPos arrival = StormBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(back, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, StormBackLayout.ARRIVAL_YAW, 0f);
        back.playSound(null, arrival, StormSetup.BACK_WIND.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        showTitle(player, "title.skyabyss.storm_back", "title.skyabyss.storm_back.sub");
        Notice.narrate(player, "message.skyabyss.storm.arrived");
        return true;
    }

    // 地上へ戻す (背中の門・/skyabyss titan leave・巨神の見回り)
    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Landing landing = landing(overworld);
        Vec3 drop = landing.pos();
        float yaw = Float.isNaN(landing.yaw()) ? player.getYRot() : landing.yaw();
        startCooldown(player);
        IN_GATE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new StormNetwork.Flash(FLASH_TICKS));
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), StormSetup.BACK_WIND.get(), SoundSource.PLAYERS, 1.2f, 0.9f);
        }
        player.fallDistance = 0;
        player.teleportTo(overworld, drop.x, drop.y, drop.z, yaw, 0f);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LEAVE_SLOW_FALL, 0, false, false, true));
        overworld.playSound(null, drop.x, drop.y, drop.z, StormSetup.WING_CALL.get(), SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    // 戻る先と向き (向きが NaN なら今の向きのまま)
    record Landing(Vec3 pos, float yaw) {
    }

    // 止まっている巨神の足もと、止まっていなければ最後に止まった足もと、記録もなければ共有スポーン地点
    static Landing landing(ServerLevel overworld) {
        StormWingEntity wing = StormKeeper.KEEPER.current(overworld);
        if (wing != null && wing.isPerched()) {
            Vec3 foot = wing.footPoint();
            return footLanding(overworld, foot.x, foot.z);
        }
        if (wing != null && wing.hasLastFoot()) {
            return footLanding(overworld, wing.lastFootX(), wing.lastFootZ());
        }
        CompoundTag state = StormKeeper.KEEPER.slot(overworld).state;
        if (wing == null && state.contains("LastFootX")) {
            return footLanding(overworld, state.getDouble("LastFootX"), state.getDouble("LastFootZ"));
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new Landing(new Vec3(spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5), Float.NaN);
    }

    // 足もとの地表 (立つ所)。チャンクは読み込んでよい (戻る直前に一度だけ)
    private static Landing footLanding(ServerLevel overworld, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        overworld.getChunk(bx >> 4, bz >> 4);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return new Landing(new Vec3(bx + 0.5, y, bz + 0.5), Float.NaN);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != STORM_BACK || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 2 != 0 || (player.isCreative() && player.getAbilities().flying)) {
            return;
        }
        // 羽根の門の口に 0.5 秒いたら、地上へ戻る (かすっただけでは戻さない)
        boolean inGate = !coolingDown(player) && StormBackLayout.GATE_MOUTH.contains(player.position());
        int inGateTicks = inGate ? IN_GATE.merge(player.getUUID(), 2, Integer::sum) : 0;
        if (!inGate) {
            IN_GATE.remove(player.getUUID());
        }
        if (inGateTicks >= GATE_DWELL) {
            IN_GATE.remove(player.getUUID());
            SkyAbyss.LOGGER.info("{} went through the storm back gate at {} {} {}", player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ());
            Notice.narrate(player, "message.skyabyss.storm.leaving");
            leave(player);
            return;
        }
        // 嵐の雲の天井より下へ落ちた: 白い閃光、休み場へ戻し、低速落下 5 秒、傷
        // 区画の部屋・中ボスの間・心臓の間 (体の外の閉じた部屋。床は雲より下のこともある) の中は落ちたことにしない
        if (player.getY() < StormBackLayout.CLOUD_Y && !coolingDown(player) && !jp.revati.skyabyss.trial.TrialSlot.inInstance(player.getX(), player.getZ())) {
            fallBack(player);
        }
    }

    // 嵐に投げ返される
    private static void fallBack(ServerPlayer player) {
        startCooldown(player);
        boolean trial = StormBackTrials.inTrial(player);
        BlockPos p = StormBackTrials.fallbackPoint(player);
        PacketDistributor.sendToPlayer(player, new StormNetwork.Flash(FLASH_TICKS));
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, StormBackLayout.ARRIVAL_YAW, 0f);
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FALL_SLOW_FALL, 0, false, false, true));
        player.hurt(player.damageSources().generic(), trial ? TRIAL_FALL_DAMAGE : FALL_DAMAGE);
        Notice.bar(player, ModText.hint("message.skyabyss.storm.swept_back"));
    }

    // 背中にいるあいだに島が無くなっていた (古いワールドなど) ときに建て直す。ログインの時点で
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == STORM_BACK) {
            StormBackBuilder.ensureBuilt(player.serverLevel());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IN_GATE.remove(event.getEntity().getUUID());
    }

    // 背中は乗り込みの前に建てておく (サーバーの起動のとき、まだ無いか版が古ければ)。ビルド時間はビルダーが記録する
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(STORM_BACK);
        if (back != null) {
            StormBackBuilder.schedule(back);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        IN_GATE.clear();
    }

    private static void showTitle(ServerPlayer player, String title, String subtitle) {
        Notice.place(player, ModText.title(title), ModText.subtitle(subtitle));
    }
}
