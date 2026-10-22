package jp.revati.skyabyss.story.ch5;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.prologue.WatcherPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 深淵の恐怖の出来事 (docs/design/36 の 8 章。00 の 255)。聞こえる・見えるのは本人だけ。
//   足音   壁の向こう (縦穴の壁の外) を、自分と並んで歩く足音。深いほど起こりやすく近い (骨の層は壁の厚み 6 ぶん外、灰の層は壁のすぐ裏)。
//          自分が止まると止まる (歩いている間だけ鳴る)。休み場の近くでは鳴らない
//   見る者 層ごとに一度だけ、脇の洞の曲がり角 (印 watch_<層>) に立つ。18 以内に来て、まだ見ていなければ。見えるのは本人だけ
//          (序章の見る者と同じ人影を、本人のクライアントに置く。近づく・見つめると消える。序章の記録は動かさない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssHorror {
    static final String SEEN = "skyabyss_abyss_watch_";
    static final double WATCH_NEAR = 18;
    static final int WATCH_STAGE = 2;
    // 足音の一続き (歩くたびに一歩ずつ、最大 8 歩)
    static final int STEPS = 8;
    private static final Map<UUID, Integer> STEPS_LEFT = new HashMap<>();
    private static final Map<UUID, Double> LAST_WALK = new HashMap<>();
    private static int watcherId = 1_000_000;

    private AbyssHorror() {
    }

    // 深さの割合 0 (上端) 〜 1 (心臓の間の上)
    static float depthShare(double y) {
        return Mth.clamp((float) ((AbyssWorld.arrival().getY() - y) / 540.0), 0f, 1f);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !AbyssWorld.in(p) || !p.isAlive() || p.isSpectator()) {
            return;
        }
        if (p.tickCount % 10 == 3) {
            watcher(p);
        }
        footsteps(p);
    }

    // ---------------------------------------------------------------- 足音
    static void footsteps(ServerPlayer p) {
        UUID id = p.getUUID();
        if (p.tickCount % 200 == 17 && !STEPS_LEFT.containsKey(id) && !AbyssPressure.nearRest(p)) {
            float share = depthShare(p.getY());
            // 骨の層の上で 1 割、灰の層の底で 6 割 (10 秒ごとの見込み)
            if (p.getRandom().nextFloat() < 0.1f + 0.5f * share) {
                STEPS_LEFT.put(id, STEPS);
                LAST_WALK.put(id, 0.0);
            }
        }
        Integer left = STEPS_LEFT.get(id);
        if (left == null) {
            return;
        }
        // 自分が歩いた距離に合わせて一歩 (止まると止まる)
        double walked = LAST_WALK.getOrDefault(id, 0.0) + Math.sqrt(p.getDeltaMovement().x * p.getDeltaMovement().x + p.getDeltaMovement().z * p.getDeltaMovement().z);
        if (walked < 1.4) {
            LAST_WALK.put(id, walked);
            return;
        }
        LAST_WALK.put(id, 0.0);
        float share = depthShare(p.getY());
        // 壁 (内の面 20、厚み 2。外の面 22) の外へ、灰の層の底で 0.5、骨の層の上で 6.5
        double rr = 22 + 0.5 + (1 - share) * 6;
        double a = Math.atan2(p.getZ(), p.getX());
        double x = Math.cos(a) * rr;
        double z = Math.sin(a) * rr;
        float vol = 0.5f + 0.5f * share;
        p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.DEEPSLATE_STEP), SoundSource.HOSTILE, x, p.getY(), z, vol, 0.8f + p.getRandom().nextFloat() * 0.1f, p.getRandom().nextLong()));
        if (left <= 1) {
            STEPS_LEFT.remove(id);
        } else {
            STEPS_LEFT.put(id, left - 1);
        }
    }

    // ---------------------------------------------------------------- 見る者
    static void watcher(ServerPlayer p) {
        CompoundTag t = persisted(p);
        for (String layer : AbyssFinds.LAYERS) {
            if (t.getBoolean(SEEN + layer)) {
                continue;
            }
            BlockPos m = AbyssBuild.mark("watch_" + layer);
            if (m == null || !p.blockPosition().closerThan(m, WATCH_NEAR) || Math.abs(p.getY() - m.getY()) > 4) {
                continue;
            }
            t.putBoolean(SEEN + layer, true);
            float yaw = (float) Math.toDegrees(Math.atan2(p.getX() - (m.getX() + 0.5), -(p.getZ() - (m.getZ() + 0.5))));
            PacketDistributor.sendToPlayer(p, new WatcherPayload(watcherId++, m.getX() + 0.5, m.getY(), m.getZ() + 0.5, yaw, WATCH_STAGE));
            return;
        }
    }

    static CompoundTag persisted(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
