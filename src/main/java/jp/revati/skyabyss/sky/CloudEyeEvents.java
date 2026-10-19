package jp.revati.skyabyss.sky;

import jp.revati.skyabyss.util.Players;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 「雲の切れ間の巨大な目」のサーバー側。
// 1章に入っていて、月が半分以上近づいた夜、屋外にいるプレイヤーに、数日おきに一度だけ、
// 雲の高さで巨大な目を開かせる。サーバーは「いつ・誰に・どの向きに」を決めるだけで、
// 目そのものは本人のクライアントの中だけで描く (ほかの人には見えない)。閉じ方はクライアントが知らせてくる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CloudEyeEvents {
    static final long DAY = 24000L;
    // 一度開いたら、次まで空ける間隔
    static final long GAP_MIN = DAY * 2;
    static final long GAP_MAX = DAY * 3;
    // 見逃したときは一日後にもう一度 (続けて1回まで)
    static final long RETRY = DAY;
    static final int MAX_QUICK_RETRIES = 1;
    // 打ち切ったとき (屋内に入ったなど) は、その夜のうちにもう一度ありうる
    static final long CANCEL_RETRY = 1200L;
    // 条件がそろっている夜に、1秒ごとに開く確率 (平均5分。夜はおよそ6〜7分)
    static final float CHANCE_PER_SECOND = 1f / 300f;
    // 夜の範囲 (一日の時刻)。暗くなりきってから、白みはじめる前まで
    static final int NIGHT_START = 14000;
    static final int NIGHT_END = 22000;
    // 月の近さがこれ以上の夜だけ
    static final float MOON_MIN = 0.5f;
    // 終わりの知らせが来なくても、これだけたてば終わったものとする
    static final int SERVER_GRACE = 200;

    record Active(int id, long end) {
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private CloudEyeEvents() {
    }

    static double frequency() {
        return Config.HORROR_EVENT_FREQUENCY.get() * Config.CLOUD_EYE_FREQUENCY.get();
    }

    static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, DAY);
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    // 開けない理由 (開けるなら null)。値だけで決まるので自己テストでそのまま確かめる
    @Nullable
    static String blocker(double frequency, boolean chapterOne, float moon, long dayTime, boolean thundering,
                          boolean outdoors, boolean awake, long now, long next, boolean active) {
        if (frequency <= 0) {
            return "disabled";
        }
        if (!chapterOne) {
            return "not in chapter 1";
        }
        if (moon < MOON_MIN) {
            return "moon not near";
        }
        if (!isNight(dayTime)) {
            return "not night";
        }
        if (thundering) {
            return "thunderstorm";
        }
        if (!awake) {
            return "not awake";
        }
        if (!outdoors) {
            return "not outdoors";
        }
        if (active) {
            return "already open";
        }
        if (next < 0 || now < next) {
            return "too soon";
        }
        return null;
    }

    @Nullable
    static String ineligible(ServerLevel level, ServerPlayer player) {
        ChapterSkyData.Entry entry = ChapterSkyData.get(level).entry(player.getUUID());
        boolean awake = player.isAlive() && !player.isSpectator() && !player.isSleeping();
        boolean outdoors = !player.isUnderWater() && level.canSeeSky(player.blockPosition().above());
        return blocker(frequency(), ChapterSky.inChapterOne(player), ChapterSky.moonNearness(level, player), level.getDayTime(),
                level.isThundering(), outdoors, awake, level.getGameTime(), entry.nextEye, ACTIVE.containsKey(player.getUUID()));
    }

    static long gap(ServerLevel level, double frequency, float moon) {
        long g = GAP_MIN + (long) (level.random.nextDouble() * (GAP_MAX - GAP_MIN));
        // 月が近いほど少し短く (最大2割)
        return (long) (g * (1.0 - 0.2 * Mth.clamp((moon - MOON_MIN) / (1f - MOON_MIN), 0f, 1f)) / frequency);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        if (!ACTIVE.isEmpty() && now % 20 == 0) {
            Iterator<Map.Entry<UUID, Active>> it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                if (now > it.next().getValue().end()) {
                    it.remove();
                }
            }
        }
        if (now % 20 != 13 || level.players().isEmpty() || frequency() <= 0) {
            return;
        }
        // 夜でも月が遠い間は何もしない (一人ずつ調べる前に)
        if (!isNight(level.getDayTime()) || ChapterSky.moonNearness(level) < MOON_MIN) {
            return;
        }
        ChapterSkyData data = ChapterSkyData.get(level);
        for (ServerPlayer player : Players.snapshot(level.players())) {
            ChapterSkyData.Entry entry = data.entry(player.getUUID());
            if (entry.nextEye < 0 && ChapterSky.inChapterOne(player)) {
                // 初めて条件がそろった夜から開きうる
                entry.nextEye = now;
                data.changed();
            }
            if (ineligible(level, player) != null) {
                continue;
            }
            if (level.random.nextFloat() < CHANCE_PER_SECOND) {
                open(level, player);
            }
        }
    }

    // 本人にだけ目を開かせる。今見ている向きから左右に外した向きの、雲の高さ。送ったパケットを返す
    static CloudEyePayload open(ServerLevel level, ServerPlayer player) {
        ChapterSkyData data = ChapterSkyData.get(level);
        ChapterSkyData.Entry entry = data.entry(player.getUUID());
        float moon = ChapterSky.moonNearness(level);
        CloudEyeStage stage = CloudEyeStage.of(entry.eyeSightings, moon);
        float offset = Mth.lerp(level.random.nextFloat(), stage.offsetMin(), stage.offsetMax());
        float yaw = pickYaw(player.getYRot(), offset, level.random.nextBoolean());
        float elevation = Mth.lerp(level.random.nextFloat(), stage.elevationMin(), stage.elevationMax());
        int id = nextId++;
        long now = level.getGameTime();
        ACTIVE.put(player.getUUID(), new Active(id, now + CloudEyeStage.MAX_TICKS + SERVER_GRACE));
        double frequency = frequency();
        entry.nextEye = now + (frequency > 0 ? gap(level, frequency, moon) : DAY * 3);
        data.changed();
        CloudEyePayload payload = new CloudEyePayload(id, yaw, elevation, stage.index(), level.random.nextInt());
        PacketDistributor.sendToPlayer(player, payload);
        SkyAbyss.LOGGER.debug("Cloud eye {} opened for {} (stage {}, yaw {}, elevation {})", id, player.getName().getString(),
                stage.index(), Math.round(yaw), Math.round(elevation));
        return payload;
    }

    // 左右どちらかに offset 度外した向き。月の通り道 (東西) と重なりにくい方を選ぶ
    static float pickYaw(float lookYaw, float offset, boolean right) {
        float a = lookYaw + (right ? offset : -offset);
        float b = lookYaw + (right ? -offset : offset);
        // マイクラの向き yaw の水平の向きは (-sin, cos)。東西 = x 軸に近いほど月と重なりやすい
        double ea = Math.abs(Math.sin(Math.toRadians(a)));
        double eb = Math.abs(Math.sin(Math.toRadians(b)));
        return Mth.wrapDegrees(ea <= eb + 0.25 ? a : b);
    }

    public static void onClientEnded(ServerPlayer player, int id, CloudEyeOutcome outcome) {
        Active a = ACTIVE.get(player.getUUID());
        if (a == null || a.id() != id) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        ServerLevel overworld = player.server.overworld();
        ChapterSkyData data = ChapterSkyData.get(overworld);
        double frequency = frequency();
        long now = overworld.getGameTime();
        applyOutcome(data.entry(player.getUUID()), outcome,
                frequency > 0 ? now + (long) (RETRY / frequency) : -1, now + CANCEL_RETRY);
        data.changed();
        SkyAbyss.LOGGER.debug("Cloud eye {} closed for {}: {}", id, player.getName().getString(), outcome);
    }

    // 閉じ方を記録に反映する。見たら段階が進む。見逃したら一度だけ早めに (retryAt)、打ち切りならその夜のうちに (cancelAt)
    static void applyOutcome(ChapterSkyData.Entry entry, CloudEyeOutcome outcome, long retryAt, long cancelAt) {
        if (outcome.seen()) {
            entry.eyeSightings++;
            entry.eyeMisses = 0;
        } else if (outcome == CloudEyeOutcome.MISSED) {
            entry.eyeMisses++;
            if (entry.eyeMisses <= MAX_QUICK_RETRIES && retryAt >= 0) {
                entry.nextEye = Math.min(entry.nextEye, retryAt);
            }
        } else if (outcome == CloudEyeOutcome.CANCELLED) {
            entry.nextEye = Math.min(entry.nextEye, cancelAt);
        }
    }

    static boolean isActive(UUID player) {
        return ACTIVE.containsKey(player);
    }

    static void clearActive() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
