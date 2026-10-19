package jp.revati.skyabyss.titan.end;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

// 帰り石 (docs/design/01 の 4.3「全巨神に共通の決まり」、02 の 2.11・08 の 2.5・12・13)。四つの背中のどこからでも、地上へ戻る道をわかりやすくする共通の目印。
//   場所: 着いた所のそば (到着点から 3〜7 ブロック、見送りの段・錨石・羽根の門・見送りの石のある方へ寄せた立てる床) と、各休み場の復活の位置。
//   見た目: 淡い緑白の光の柱と枠 (BoardBeamClient)。近づくと粒の輪。その背中にいる人にだけ送る (96 ブロック以内)
//   使い方: 光の中でしゃがんで 1.5 秒。粒の輪が閉じると、その巨神の戻り道 (Titans.sendDown) で地上へ戻る。立っているだけでは戻らない
//           (復活の位置・到着の位置に立っているだけで戻らないように)。戻る先は各巨神の決まり (潮殻は近くの岸、ほかは足もと)
// 昔からの戻り方 (岩鯨の錨石・潮殻の見送りの段・雷翼の羽根の門・余燼の見送りの石・/skyabyss titan leave) はそのまま残る。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReturnBeacons {
    // しゃがんで立つ長さ (tick) と、石の半径 (水平)・高さの幅
    public static final int DWELL = 30;
    public static final double RADIUS = 1.5;
    public static final double HEIGHT = 2.2;
    // 光を送る距離 (水平)・送る間隔 (tick)
    public static final double SHOW_RANGE = 96;
    public static final int SEND_EVERY = 20;
    // 光の色 (淡い緑白。乗り込み口の光 (巨神ごとの色) と見分ける)
    public static final int RGB = 0xC8FFE0;
    // 到着点から探す距離
    private static final int FIND_MIN = 3;
    private static final int FIND_MAX = 7;

    public record Beacon(String id, Vec3 foot) {
    }

    // ディメンション → 帰り石の一覧 (最初の人が来たとき作る。保存しない)
    private static final Map<ResourceKey<Level>, List<Beacon>> CACHE = new HashMap<>();
    // しゃがんで立っている人 → tick
    private static final Map<UUID, Integer> DWELLING = new HashMap<>();

    // 試練を越えた・心臓を止めた人のそばに、すぐ帰れる帰り石を一つ置く (人ごと・保存しない)。人 → 石と、消える時刻 (背中のゲーム時間)
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    public static final int OFFER_TICKS = 20 * 60 * 5;

    private record Offer(Beacon beacon, ResourceKey<Level> dim, long until) {
    }

    private ReturnBeacons() {
    }

    // この人のそばに、帰り石を出す (試練の終わり・心臓の撃破。背中の中でだけ)。置ける床がなければ何もしない。置いたか
    public static boolean offer(ServerPlayer player) {
        String titan = Titans.titanOfBack(player.level().dimension());
        if (titan == null || !(player.level() instanceof ServerLevel back)) {
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

    private static List<Beacon> withOffer(ServerPlayer player, ServerLevel back, List<Beacon> list) {
        Offer o = OFFERS.get(player.getUUID());
        if (o == null) {
            return list;
        }
        if (o.dim() != back.dimension() || back.getGameTime() > o.until()) {
            OFFERS.remove(player.getUUID());
            return list;
        }
        List<Beacon> all = new ArrayList<>(list);
        all.add(o.beacon());
        return all;
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

    // 到着点の位置と、戻り道のある方 (到着点の帰り石をそちらへ寄せる)
    public static BlockPos arrivalOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> TideBackLayout.ARRIVAL;
            case Titans.THUNDER_WING -> StormBackLayout.ARRIVAL;
            case Titans.EMBER -> EmberBackLayout.ARRIVAL;
            default -> TitanBackLayout.arrival();
        };
    }

    public static Vec3 exitOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new Vec3(TideBackLayout.GATE_X + 0.5, TideBackLayout.GATE_Y1, TideBackLayout.GATE_Z + 1.0);
            case Titans.THUNDER_WING -> StormBackLayout.GATE_MOUTH.getCenter();
            case Titans.EMBER -> Vec3.atBottomCenterOf(EmberBackLayout.RETURN_STONE.center());
            default -> Vec3.atBottomCenterOf(TitanBackLayout.anchor());
        };
    }

    // 帰り石の一覧 (到着点のそば + 休み場)。背中が読み込まれていて到着点のそばに立てる床があれば、到着点の石も入る
    public static List<Beacon> beacons(ServerLevel back, String titan) {
        List<Beacon> cached = CACHE.get(back.dimension());
        if (cached != null) {
            return cached;
        }
        List<Beacon> list = new ArrayList<>();
        BlockPos arrival = arrivalOf(titan);
        if (!back.hasChunkAt(arrival)) {
            // まだ床が読み込まれていない (人が着いた直後)。休み場だけ先に出し、到着点の分は次の機会に足す (キャッシュしない)
            return restBeacons(titan);
        }
        Vec3 near = findNear(back, arrival, exitOf(titan));
        if (near != null) {
            list.add(new Beacon("arrival", near));
        }
        list.addAll(restBeacons(titan));
        CACHE.put(back.dimension(), list);
        return list;
    }

    private static List<Beacon> restBeacons(String titan) {
        List<Beacon> list = new ArrayList<>();
        restSpots(titan).forEach((id, pos) -> list.add(new Beacon("rest_" + id, Vec3.atBottomCenterOf(pos))));
        return list;
    }

    // 到着点から FIND_MIN〜FIND_MAX 以内の立てる床のうち、戻り道 (toward) にいちばん近いもの。なければ null
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

    // p が帰り石の中 (足もとから HEIGHT 以内、水平 RADIUS 以内) か
    public static boolean inside(Beacon b, Vec3 p) {
        double dx = p.x - b.foot().x;
        double dz = p.z - b.foot().z;
        return dx * dx + dz * dz <= RADIUS * RADIUS && p.y >= b.foot().y - 1.0 && p.y <= b.foot().y + HEIGHT;
    }

    @Nullable
    public static Beacon insideAny(List<Beacon> list, Vec3 p) {
        for (Beacon b : list) {
            if (inside(b, p)) {
                return b;
            }
        }
        return null;
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
        List<Beacon> list = withOffer(player, back, beacons(back, titan));
        if (player.tickCount % SEND_EVERY == 0) {
            sendBeams(player, list);
        }
        Beacon in = insideAny(list, player.position());
        if (in == null || player.isPassenger()) {
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
        ring(back, in.foot(), t / (float) DWELL);
        if (t >= DWELL) {
            DWELLING.remove(player.getUUID());
            leave(player, titan, in);
        }
    }

    // 戻す (自己テストからも)
    public static void leave(ServerPlayer player, String titan, @Nullable Beacon at) {
        SkyAbyss.LOGGER.info("{} used the return stone {} of the {} back", player.getGameProfile().getName(), at == null ? "-" : at.id(), titan);
        Notice.narrate(player, "message.skyabyss.return_beacon.leaving");
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        Titans.sendDown(player, titan);
    }

    private static void sendBeams(ServerPlayer player, List<Beacon> list) {
        for (Beacon b : list) {
            double dx = player.getX() - b.foot().x;
            double dz = player.getZ() - b.foot().z;
            if (dx * dx + dz * dz > SHOW_RANGE * SHOW_RANGE || Math.abs(player.getY() - b.foot().y) > 60) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new TitanEndNetwork.Beam("return_" + b.id(), RGB, b.foot().x, b.foot().y + 1.0, b.foot().z,
                    (float) RADIUS - 0.3f, 1.0f, (float) RADIUS - 0.3f, 0f, b.foot().y, b.foot().y + 16, 0.55f, BoardGlow.BEAM_TTL));
        }
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
        DWELLING.remove(event.getEntity().getUUID());
        OFFERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        CACHE.clear();
        DWELLING.clear();
        OFFERS.clear();
    }

    // 自己テスト用: キャッシュを捨てる
    public static void clearCache() {
        CACHE.clear();
        DWELLING.clear();
        OFFERS.clear();
    }

    public static boolean hasOffer(UUID player) {
        return OFFERS.containsKey(player);
    }
}
