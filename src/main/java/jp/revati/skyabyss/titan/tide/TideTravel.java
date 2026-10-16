package jp.revati.skyabyss.titan.tide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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

// 地上と「潮殻の背」(専用のディメンション tide_back) を行き来させる (08 の 2.4〜2.5節)。
// - 乗る: 休んでいる潮殻の巨神の潮門の口に、舟か泳ぎで入る。舟に乗っていたら降ろしてから渡す (舟は門の前に残る)
// - 戻る: 背中の潮門の浜の門をくぐる。巨神が休んでいれば門の前の水の上へ、休んでいなければ最後に休んだ場所の岸へ、
//   記録もなければ共有スポーン地点へ。溺れないよう水中呼吸を 10 秒付ける
// - 背中のまわりの海に落ちると、5 秒後に潮門の浜へ戻される。島の中心から 160 ブロックより外へ泳ぐと、すぐ戻される
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideTravel {
    public static final ResourceKey<Level> TIDE_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_back"));

    // 渡ってすぐに、また門に入って行き来しないように (tick)
    private static final String TAG_COOLDOWN = "skyabyss_tide_cooldown";
    private static final int COOLDOWN = 60;
    private static final int SWEPT_AFTER = 100;
    private static final double FAR = 160;

    // 背中のまわりの海にいる人 → 何 tick いるか
    private static final Map<UUID, Integer> ADRIFT = new HashMap<>();
    // 背中の潮門の口にいる人 → 何 tick いるか (GATE_DWELL で地上へ)
    private static final Map<UUID, Integer> IN_GATE = new HashMap<>();
    private static final int GATE_DWELL = 10;

    private TideTravel() {
    }

    private static boolean coolingDown(ServerPlayer player) {
        return player.getPersistentData().getLong(TAG_COOLDOWN) > player.server.overworld().getGameTime();
    }

    private static void startCooldown(ServerPlayer player) {
        player.getPersistentData().putLong(TAG_COOLDOWN, player.server.overworld().getGameTime() + COOLDOWN);
    }

    // 休んでいる巨神の潮門の口に入った
    static void boardFromGate(ServerPlayer player, TideShellEntity titan) {
        if (coolingDown(player) || player.isSleeping()) {
            return;
        }
        // 資格がなければ、舟から降ろす前に断る
        if (!jp.revati.skyabyss.progress.Progression.checkBoard(player, TideKeeper.SLOT)) {
            startCooldown(player);
            return;
        }
        if (player.isPassenger()) {
            player.stopRiding();
        }
        board(player);
    }

    // 背中へ渡す (コマンドからも)
    public static boolean board(ServerPlayer player) {
        // 乗れるのは、自分で岩鯨の心臓を止めた人だけ (10 の 3 章)。断ったら門の判定がすぐまた来ないように待たせる
        if (!jp.revati.skyabyss.progress.Progression.checkBoard(player, TideKeeper.SLOT)) {
            startCooldown(player);
            return false;
        }
        ServerLevel back = player.server.getLevel(TIDE_BACK);
        if (back == null) {
            SkyAbyss.LOGGER.error("Tide back dimension is missing");
            return false;
        }
        TideBackBuilder.ensureBuilt(back);
        startCooldown(player);
        ADRIFT.remove(player.getUUID());
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), TideSetup.GATE.get(), SoundSource.PLAYERS, 1.5f, 1.0f);
            from.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), 40, 0.8, 0.6, 0.8, 0.2);
        }
        PacketDistributor.sendToPlayer(player, new TideNetwork.Plunge(30));
        BlockPos arrival = TideBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(back, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL_YAW, 0f);
        back.playSound(null, arrival, TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 1.2f);
        showTitle(player, "title.skyabyss.tide_back", "title.skyabyss.tide_back.sub");
        player.sendSystemMessage(ModText.lore("message.skyabyss.tide.arrived"));
        return true;
    }

    // 地上へ戻す
    public static void leave(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        Landing landing = landing(overworld);
        Vec3 drop = landing.pos();
        float yaw = Float.isNaN(landing.yaw()) ? player.getYRot() : landing.yaw();
        startCooldown(player);
        ADRIFT.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new TideNetwork.Plunge(30));
        if (player.level() instanceof ServerLevel from) {
            from.playSound(null, player.getX(), player.getY(), player.getZ(), TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 0.9f);
        }
        player.fallDistance = 0;
        player.teleportTo(overworld, drop.x, drop.y, drop.z, yaw, 0f);
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 10, 0, false, false, true));
        overworld.playSound(null, drop.x, drop.y, drop.z, TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 1.1f);
    }

    // 戻る先と向き (向きが NaN なら今の向きのまま)
    record Landing(Vec3 pos, float yaw) {
    }

    // 巨神が休んでいれば門の前の水の上 (巨神に背を向ける)、休んでいなければ最後に休んだ場所の岸、記録もなければ共有スポーン地点
    static Landing landing(ServerLevel overworld) {
        TideShellEntity titan = TideKeeper.KEEPER.current(overworld);
        if (titan != null && titan.isResting()) {
            return new Landing(titan.gateExit(), titan.getYRot() - 90f);
        }
        Vec3 rest = titan != null ? titan.lastRestSpot() : lastRestFromRecord(overworld);
        if (rest != null) {
            Vec3 shore = TideOcean.standableShore(overworld, rest.x, rest.z);
            if (shore != null) {
                return new Landing(shore, Float.NaN);
            }
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        return new Landing(new Vec3(spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5), Float.NaN);
    }

    // 巨神が読み込まれていないときは、見守りの記録から最後に休んだ場所を読む
    private static Vec3 lastRestFromRecord(ServerLevel overworld) {
        CompoundTag state = TideKeeper.KEEPER.slot(overworld).state;
        return state.contains("LastRestX") ? new Vec3(state.getDouble("LastRestX"), 0, state.getDouble("LastRestZ")) : null;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TIDE_BACK || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 2 != 0 || (player.isCreative() && player.getAbilities().flying)) {
            return;
        }
        // 背中の潮門をくぐると、地上へ戻る。口の中に 0.5 秒いたら (かすっただけ・押し戻しの一瞬では戻さない)
        boolean inGate = !coolingDown(player) && TideBackBuilder.inReturnGate(player.getX(), player.getY(), player.getZ());
        int inGateTicks = inGate ? IN_GATE.merge(player.getUUID(), 2, Integer::sum) : 0;
        if (!inGate) {
            IN_GATE.remove(player.getUUID());
        }
        if (inGateTicks >= GATE_DWELL) {
            IN_GATE.remove(player.getUUID());
            SkyAbyss.LOGGER.info("{} went through the tide back gate at {} {} {}", player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ());
            player.sendSystemMessage(ModText.lore("message.skyabyss.tide.leaving"));
            leave(player);
            return;
        }
        if (player.tickCount % 40 == 0) {
            if (player.distanceToSqr(jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_X + 0.5, jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_Y0,
                    jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_Z + 1) < 6 * 6) {
                player.displayClientMessage(ModText.hint("message.skyabyss.tide.return_hint"), true);
            }
        }
        double r = Math.hypot(player.getX(), player.getZ() - jp.revati.skyabyss.titan.tide.back.TideBackLayout.SHELL_CZ);
        if (r > FAR) {
            sweepBack(player);
            return;
        }
        // 島から海に落ちた: 5 秒たつと、潮に押し戻される (甲羅の外で、海の水の中にいる)
        boolean offIsland = jp.revati.skyabyss.titan.tide.back.TideBackLayout.shellS2(player.getX(), player.getZ()) > 1.02
                && player.getY() < TideBackBuilder.SEA + 1;
        if (offIsland && player.isInWater()) {
            int t = ADRIFT.merge(player.getUUID(), 2, Integer::sum);
            if (t >= SWEPT_AFTER) {
                sweepBack(player);
            }
        } else {
            ADRIFT.remove(player.getUUID());
        }
    }

    private static void sweepBack(ServerPlayer player) {
        ADRIFT.remove(player.getUUID());
        BlockPos a = TideBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL_YAW, 0f);
        player.serverLevel().playSound(null, a, TideSetup.SURF.get(), SoundSource.PLAYERS, 1.0f, 1.1f);
        player.displayClientMessage(ModText.hint("message.skyabyss.tide.swept_back"), true);
    }

    // 背中にいるあいだに島が無くなっていた (古いワールドなど) ときに建て直す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == TIDE_BACK) {
            TideBackBuilder.ensureBuilt(player.serverLevel());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ADRIFT.remove(event.getEntity().getUUID());
        IN_GATE.remove(event.getEntity().getUUID());
    }

    // 島は乗り込みの前に建てておく (サーバーの起動のとき、まだ無いか版が古ければ)。乗り込みの途中で 4〜6 秒止まらないように
    // (08 の 20.12節: 初めての乗り込みで地上へ戻された件の手当て)。乗り込みの ensureBuilt は、念のための残し
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(TIDE_BACK);
        if (back != null) {
            TideBackBuilder.ensureBuilt(back);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ADRIFT.clear();
        IN_GATE.clear();
    }

    private static void showTitle(ServerPlayer player, String title, String subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(ModText.subtitle(subtitle)));
        player.connection.send(new ClientboundSetTitleTextPacket(ModText.title(title)));
    }
}
