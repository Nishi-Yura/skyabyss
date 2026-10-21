package jp.revati.skyabyss.titan.tide;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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

// 地上と「潮殻の背」(専用のディメンション tide_back) を行き来させる (08 の 2.4〜2.5節)。
// - 乗る: 休んでいる潮殻の巨神の潮門の口に、舟か泳ぎで入る。舟に乗っていたら降ろしてから渡す (舟は門の前に残る)
// - 戻る: 背中の潮門の浜の門をくぐる。巨神が休んでいれば門の前の水の上へ、休んでいなければ最後に休んだ場所の岸へ、
//   記録もなければ共有スポーン地点へ。溺れないよう水中呼吸を 10 秒付ける
// - 背中のまわりの海に落ちると、5 秒後に潮門の浜へ戻される。島の中心から 160 ブロックより外へ泳ぐと、すぐ戻される
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideTravel {
    public static final ResourceKey<Level> TIDE_BACK = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tide_back"));

    // 渡ってすぐに、また門に入って行き来しないように (tick)。背中の潮門の口に 0.5 秒いたら地上へ。
    // 待ち・資格・舟から降ろす・0.5 秒は共通の TitanGate (13 の 10.2節。待ちの記録の名前 skyabyss_tide_cooldown は前と同じ)
    static final jp.revati.skyabyss.titan.TitanGate PASSAGE = new jp.revati.skyabyss.titan.TitanGate("tide", TideKeeper.SLOT, 60, 10);
    // 自己テスト用 (ほかの包みから): 門の待ちを忘れる
    public static final java.util.function.Consumer<java.util.UUID> PASSAGE_FORGET = PASSAGE::forget;
    private static final int SWEPT_AFTER = 100;
    // 背の中心 (0, SHELL_CZ) からこれより遠くへ出た人は押し戻す。ひれの先端の台 (x ±179)・到着の段 (半径 172) が入る大きさ。
    // 旧い値 160 は版 20 の拡大の前のままで、到着の直後と貝の間・先端の宝のそばで押し戻され続けた
    public static final double FAR = 240;

    // 背中のまわりの海にいる人 → 何 tick いるか
    private static final Map<UUID, Integer> ADRIFT = new HashMap<>();

    private TideTravel() {
    }

    private static void startCooldown(ServerPlayer player) {
        PASSAGE.startCooldown(player);
    }

    // 休んでいる巨神の潮門の口に入った (資格がなければ、舟から降ろす前に断る)
    static boolean boardFromGate(ServerPlayer player, TideShellEntity titan) {
        return PASSAGE.admit(player) && board(player);
    }

    // 背中へ渡す (コマンドからも)
    public static boolean board(ServerPlayer player) {
        // 乗れるのは、自分で岩鯨の心臓を止めた人だけ (10 の 3 章)。断ったら門の判定がすぐまた来ないように待たせる
        if (!PASSAGE.checkBoard(player)) {
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
        Notice.narrate(player, "message.skyabyss.tide.arrived");
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
        if (landing.water()) {
            // 岸が見つからず海の上へ出るときは、舟を一艘添える (沖で泳いで戻る羽目にしない)
            giveBoat(overworld, player, drop);
        }
        overworld.playSound(null, drop.x, drop.y, drop.z, TideSetup.GATE.get(), SoundSource.PLAYERS, 1.2f, 1.1f);
    }

    private static void giveBoat(ServerLevel overworld, ServerPlayer player, Vec3 at) {
        net.minecraft.world.entity.vehicle.Boat boat = new net.minecraft.world.entity.vehicle.Boat(overworld, at.x, overworld.getSeaLevel(), at.z);
        boat.setVariant(net.minecraft.world.entity.vehicle.Boat.Type.OAK);
        boat.setYRot(player.getYRot());
        overworld.addFreshEntity(boat);
        player.startRiding(boat);
        Notice.bar(player, ModText.hint("message.skyabyss.tide.boat_given"));
    }

    // 戻る先と向き (向きが NaN なら今の向きのまま)
    // water = 海の上へ出る (岸が見つからなかった)。そのときは舟を添える
    record Landing(Vec3 pos, float yaw, boolean water) {
        Landing(Vec3 pos, float yaw) {
            this(pos, yaw, false);
        }
    }

    // 巨神が休んでいれば門の前の水の上 (巨神に背を向ける)、休んでいなければ最後に休んだ場所の岸、記録もなければ共有スポーン地点
    static Landing landing(ServerLevel overworld) {
        TideShellEntity titan = TideKeeper.KEEPER.current(overworld);
        if (titan != null && titan.isResting()) {
            // 休んでいる間も、海の上ではなく近くの岸へ降ろす (背中から出て沖に取り残されない)。舟は岸が見つからないときだけ
            Vec3 shore = shoreNear(overworld, titan.getX(), titan.getZ());
            if (shore != null) {
                return new Landing(shore, Float.NaN);
            }
            return new Landing(titan.gateExit(), titan.getYRot() - 90f, true);
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

    // 戻る先が、水の中でない (岸・陸) か、海の上なら舟が付く (自己テスト用)
    public static boolean landingSafe(ServerLevel overworld) {
        Landing l = landing(overworld);
        return l.water() || !overworld.getFluidState(BlockPos.containing(l.pos())).is(net.minecraft.tags.FluidTags.WATER);
    }

    // 巨神 (x, z) の近くの岸: 水際の立てる地面 (巨神の休む所は岸から 40 以内。48 ブロックの輪、なければ 140 ブロックまで)
    @javax.annotation.Nullable
    static Vec3 shoreNear(ServerLevel overworld, double x, double z) {
        Vec3 shore = TideOcean.standableShore(overworld, x, z);
        if (shore == null) {
            shore = jp.revati.skyabyss.titan.end.SafeSpot.find(overworld, x, z, 0, 140, jp.revati.skyabyss.titan.end.SafeSpot.Kind.SHORE, 24);
        }
        return shore;
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
        if (PASSAGE.dwell(player, TideBackBuilder.inReturnGate(player.getX(), player.getY(), player.getZ()), 2)) {
            SkyAbyss.LOGGER.info("{} went through the tide back gate at {} {} {}", player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ());
            Notice.narrate(player, "message.skyabyss.tide.leaving");
            leave(player);
            return;
        }
        if (player.tickCount % 40 == 0) {
            if (player.distanceToSqr(jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_X + 0.5, jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_Y0,
                    jp.revati.skyabyss.titan.tide.back.TideBackLayout.GATE_Z + 1) < 6 * 6) {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.return_hint"));
            }
        }
        if (farOut(player.getX(), player.getZ())) {
            sweepBack(player);
            return;
        }
        // 島から海に落ちた: 5 秒たつと、潮に押し戻される (甲羅の外で、海の水の中にいる)
        if (offIsland(player.getX(), player.getY(), player.getZ()) && player.isInWater()) {
            int t = ADRIFT.merge(player.getUUID(), 2, Integer::sum);
            if (t >= SWEPT_AFTER) {
                sweepBack(player);
            }
        } else {
            ADRIFT.remove(player.getUUID());
        }
    }

    public static boolean farOut(double x, double z) {
        return Math.hypot(x, z - jp.revati.skyabyss.titan.tide.back.TideBackLayout.SHELL_CZ) > FAR;
    }

    // 甲羅の外の海面の高さ (島の外。ひれ・到着の段・甲羅の上は海面より上なので入らない)
    public static boolean offIsland(double x, double y, double z) {
        return jp.revati.skyabyss.titan.tide.back.TideBackLayout.shellS2(x, z) > 1.02 && y < TideBackBuilder.SEA + 1;
    }

    private static void sweepBack(ServerPlayer player) {
        ADRIFT.remove(player.getUUID());
        BlockPos a = TideBackBuilder.arrival();
        player.fallDistance = 0;
        player.teleportTo(player.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL_YAW, 0f);
        player.serverLevel().playSound(null, a, TideSetup.SURF.get(), SoundSource.PLAYERS, 1.0f, 1.1f);
        Notice.bar(player, ModText.hint("message.skyabyss.tide.swept_back"));
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
        PASSAGE.forget(event.getEntity().getUUID());
    }

    // 島は乗り込みの前に建てておく (サーバーの起動のとき、まだ無いか版が古ければ)。乗り込みの途中で 4〜6 秒止まらないように
    // (08 の 20.12節: 初めての乗り込みで地上へ戻された件の手当て)。乗り込みの ensureBuilt は、念のための残し
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(TIDE_BACK);
        if (back != null) {
            TideBackBuilder.schedule(back);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ADRIFT.clear();
        PASSAGE.clear();
    }

    private static void showTitle(ServerPlayer player, String title, String subtitle) {
        Notice.place(player, ModText.title(title), ModText.subtitle(subtitle));
    }
}
