package jp.revati.skyabyss.titan.end;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.tide.back.TideBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

// 真ん中へ戻る光 (docs/design/01 の 4.3「全巨神に共通の決まり」、02 の 2.11・08 の 2.5・12・13)。
//   試練を越えた人のそばに、5 分だけ個人の光の柱を立てる (その人にだけ見える)。光の中でしゃがんで 1.5 秒で、その背中の真ん中 (鼓動の間・休み場 "hub") へ戻る。
//   途中から地上へ戻る道は、光ではなく、その巨神の呼び笛 (旅嚢) を背中の中で使う: 「地上へ下りる？」と聞かれ、5 秒以内にもう一度使うと地上へ戻る (Titans.sendDown)。
//   心臓を止めたあとの運び出しは別 (TitanEnd)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReturnBeacons {
    // しゃがんで立つ長さ (tick) と、光の半径 (水平)・高さの幅
    public static final int DWELL = 30;
    public static final double RADIUS = 1.5;
    public static final double HEIGHT = 2.2;
    // 光を送る距離 (水平)・送る間隔 (tick)
    public static final double SHOW_RANGE = 96;
    public static final int SEND_EVERY = 20;
    // 光の色 (淡い緑白。乗り込み口の光 (巨神ごとの色) と見分ける)
    public static final int RGB = 0xC8FFE0;
    // 試練の終わりのそばから探す距離
    private static final int FIND_MIN = 2;
    private static final int FIND_MAX = 6;
    // 笛で地上へ下りるとき、聞き返してからもう一度使うまでの猶予 (tick)
    public static final int CONFIRM_TICKS = 20 * 5;

    public record Beacon(String id, Vec3 foot) {
    }

    // しゃがんで立っている人 → tick
    private static final Map<UUID, Integer> DWELLING = new HashMap<>();
    // 試練を越えた人のそばに、真ん中へ戻れる光を一つ置く (人ごと・保存しない)。人 → 光と、消える時刻 (背中のゲーム時間)
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    public static final int OFFER_TICKS = 20 * 60 * 5;
    // 笛で「地上へ下りる？」と聞かれた人 → 答えられる最後の時刻 (背中のゲーム時間)
    private static final Map<UUID, Long> ASKED = new HashMap<>();

    private record Offer(Beacon beacon, ResourceKey<Level> dim, long until) {
    }

    private ReturnBeacons() {
    }

    // この人のそばに、真ん中へ戻る光を出す (試練の終わり。背中の中でだけ)。置ける床がなければ何もしない。置いたか
    public static boolean offer(ServerPlayer player) {
        String titan = Titans.titanOfBack(player.level().dimension());
        if (titan == null || !(player.level() instanceof ServerLevel back) || hubOf(titan) == null) {
            return false;
        }
        BlockPos at = player.blockPosition();
        Vec3 look = player.position().add(player.getLookAngle().x * 10, 0, player.getLookAngle().z * 10);
        Vec3 spot = findNear(back, at, look);
        if (spot == null) {
            return false;
        }
        OFFERS.put(player.getUUID(), new Offer(new Beacon("offer", spot), back.dimension(), back.getGameTime() + OFFER_TICKS));
        Notice.bar(player, ModText.hint("message.skyabyss.return_beacon.offer"));
        return true;
    }

    @Nullable
    private static Offer liveOffer(ServerPlayer player, ServerLevel back) {
        Offer o = OFFERS.get(player.getUUID());
        if (o == null) {
            return null;
        }
        if (o.dim() != back.dimension() || back.getGameTime() > o.until()) {
            OFFERS.remove(player.getUUID());
            return null;
        }
        return o;
    }

    // ---------------------------------------------------------------- 場所

    // その巨神の背中の、休み場の復活の位置 (id つき)。巨神ごとの配置表から
    public static Map<String, BlockPos> restSpots(String titan) {
        Map<String, BlockPos> map = new java.util.LinkedHashMap<>();
        switch (titan) {
            case Titans.TIDE_SHELL -> TideBackLayout.RESTS.forEach(r -> map.put(r.id(), r.spawn()));
            case Titans.THUNDER_WING -> StormBackLayout.RESTS.forEach(r -> map.put(r.id(), r.spawn()));
            case Titans.EMBER -> EmberBackLayout.RESTS.forEach(r -> map.put(r.name(), r.floor().above()));
            default -> map.putAll(TitanBackTrials.checkpointSpawns());
        }
        return map;
    }

    // その背中の真ん中 (鼓動の間・休み場 "hub") の、戻ってきたとき立つ所
    @Nullable
    public static BlockPos hubOf(String titan) {
        return restSpots(titan).get("hub");
    }

    // 到着点の位置 (自己テスト・開発用)
    public static BlockPos arrivalOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TideBackLayout.ARRIVAL;
            case Titans.THUNDER_WING -> StormBackLayout.ARRIVAL;
            case Titans.EMBER -> EmberBackLayout.ARRIVAL;
            default -> TitanBackLayout.arrival();
        };
    }

    // arrival から FIND_MIN〜FIND_MAX 以内の立てる床のうち、toward にいちばん近いもの。なければ null
    @Nullable
    public static Vec3 findNear(ServerLevel level, BlockPos arrival, Vec3 toward) {
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -FIND_MAX; dx <= FIND_MAX; dx++) {
            for (int dz = -FIND_MAX; dz <= FIND_MAX; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 < FIND_MIN * FIND_MIN || d2 > FIND_MAX * FIND_MAX) {
                    continue;
                }
                for (int dy = 2; dy >= -3; dy--) {
                    BlockPos feet = arrival.offset(dx, dy, dz);
                    if (standable(level, feet)) {
                        double d = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5).distanceToSqr(toward.x, feet.getY(), toward.z);
                        if (d < bestD) {
                            bestD = d;
                            best = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }

    // feet が立てる床: 足もとが固く (水・溶岩・空気でない)、足と頭の高さが空いていて、水でない
    public static boolean standable(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) {
            return false;
        }
        BlockState below = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        FluidState fb = level.getFluidState(feet);
        FluidState fh = level.getFluidState(feet.above());
        return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP) && fb.isEmpty() && fh.isEmpty()
                && body.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty();
    }

    // p が光の中 (足もとから HEIGHT 以内、水平 RADIUS 以内) か
    public static boolean inside(Beacon b, Vec3 p) {
        double dx = p.x - b.foot().x;
        double dz = p.z - b.foot().z;
        return dx * dx + dz * dz <= RADIUS * RADIUS && p.y >= b.foot().y - 1.0 && p.y <= b.foot().y + HEIGHT;
    }

    // ---------------------------------------------------------------- 毎 tick

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 2 != 0) {
            return;
        }
        String titan = Titans.titanOfBack(player.level().dimension());
        if (titan == null || !(player.level() instanceof ServerLevel back)) {
            DWELLING.remove(player.getUUID());
            return;
        }
        Offer offer = liveOffer(player, back);
        if (offer == null) {
            DWELLING.remove(player.getUUID());
            return;
        }
        if (player.tickCount % SEND_EVERY == 0) {
            sendBeam(player, offer.beacon());
        }
        if (!inside(offer.beacon(), player.position()) || player.isPassenger()) {
            DWELLING.remove(player.getUUID());
            return;
        }
        if (!player.isShiftKeyDown()) {
            DWELLING.remove(player.getUUID());
            if (player.tickCount % 40 == 0) {
                Notice.bar(player, ModText.hint("message.skyabyss.return_beacon.hint"));
            }
            return;
        }
        int t = DWELLING.merge(player.getUUID(), 2, Integer::sum);
        ring(back, offer.beacon().foot(), t / (float) DWELL);
        if (t >= DWELL) {
            DWELLING.remove(player.getUUID());
            OFFERS.remove(player.getUUID());
            toHub(player, back, titan);
        }
    }

    // 背中の真ん中へ戻す (自己テストからも)。戻せたか
    public static boolean toHub(ServerPlayer player, ServerLevel back, String titan) {
        BlockPos hub = hubOf(titan);
        if (hub == null) {
            return false;
        }
        SkyAbyss.LOGGER.info("{} returned to the hub of the {} back", player.getGameProfile().getName(), titan);
        Notice.narrate(player, "message.skyabyss.return_beacon.leaving");
        back.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        player.stopRiding();
        player.teleportTo(back, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        back.playSound(null, hub.getX() + 0.5, hub.getY(), hub.getZ() + 0.5, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
        return true;
    }

    // ---------------------------------------------------------------- 笛で地上へ

    // 背中の中で、その巨神の呼び笛を使った。その背中の中なら true (呼びには進まない)。一度目は聞き返し、5 秒以内の二度目で地上へ戻す
    public static boolean whistleInBack(ServerPlayer player, String kindId) {
        String titan = Titans.titanOfBack(player.level().dimension());
        if (titan == null || !titan.equals(kindId)) {
            return false;
        }
        long now = player.level().getGameTime();
        Long until = ASKED.get(player.getUUID());
        if (until != null && now <= until) {
            ASKED.remove(player.getUUID());
            SkyAbyss.LOGGER.info("{} went down from the {} back with the call whistle", player.getGameProfile().getName(), titan);
            Notice.narrate(player, "message.skyabyss.return_beacon.descending");
            Titans.sendDown(player, titan);
            return true;
        }
        ASKED.put(player.getUUID(), now + CONFIRM_TICKS);
        Notice.bar(player, ModText.hint("message.skyabyss.return_beacon.confirm"));
        return true;
    }

    public static boolean isAsking(UUID player) {
        return ASKED.containsKey(player);
    }

    private static void sendBeam(ServerPlayer player, Beacon b) {
        double dx = player.getX() - b.foot().x;
        double dz = player.getZ() - b.foot().z;
        if (dx * dx + dz * dz > SHOW_RANGE * SHOW_RANGE || Math.abs(player.getY() - b.foot().y) > 60) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new TitanEndNetwork.Beam("return_" + b.id(), RGB, b.foot().x, b.foot().y + 1.0, b.foot().z,
                (float) RADIUS - 0.3f, 1.0f, (float) RADIUS - 0.3f, 0f, b.foot().y, b.foot().y + 16, 0.55f, BoardGlow.BEAM_TTL));
    }

    // 閉じていく粒の輪 (progress 0〜1)
    private static void ring(ServerLevel level, Vec3 foot, float progress) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.78f, 1.0f, 0.88f), 1.2f);
        double r = RADIUS * (1.0 - 0.7 * Math.min(1f, progress));
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + progress * 3;
            level.sendParticles(dust, foot.x + Math.cos(a) * r, foot.y + 0.15, foot.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, foot.x, foot.y + 0.4 + progress, foot.z, 1, 0.2, 0.1, 0.2, 0.01);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DWELLING.remove(id);
        OFFERS.remove(id);
        ASKED.remove(id);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        clearCache();
    }

    // 自己テスト用: 記録を捨てる
    public static void clearCache() {
        DWELLING.clear();
        OFFERS.clear();
        ASKED.clear();
    }

    public static boolean hasOffer(UUID player) {
        return OFFERS.containsKey(player);
    }
}
