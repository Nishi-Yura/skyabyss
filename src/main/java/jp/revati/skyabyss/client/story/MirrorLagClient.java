package jp.revati.skyabyss.client.story;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.prologue.PrologueRegistry;
import jp.revati.skyabyss.prologue.WatcherEntity;
import jp.revati.skyabyss.titan.storm.chapter.MirrorLag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 竜骨の間の入口の遅れた影 (サーバーは titan/storm/chapter/MirrorLag) のクライアント側。自分の世界にだけ、見る者の形の人影を一つ置く。
// - 毎 tick、自分の位置と向きを覚え、delay tick 前のものを鏡の面で折り返した所へ影を動かす (映った自分が遅れて動く)
// - 影は半透明 (0.55)。目の光は弱い。見つめ続けると (1.5 秒)、影が先にこちらを向き、薄れて消える
// - 長さ (duration) が過ぎる・次元が変わる・切断で消す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MirrorLagClient {
    private static final float ALPHA = 0.55f;
    private static final int STARE_TICKS = 30;
    private static final int FADE = 30;
    private static final double STARE_CONE = 10.0;

    private record Pose(Vec3 pos, float yaw) {
    }

    @Nullable
    private static WatcherEntity shade;
    @Nullable
    private static MirrorLag.Plane plane;
    private static final Deque<Pose> HISTORY = new ArrayDeque<>();
    private static int delay;
    private static int left;
    private static int stare;
    private static int fade;

    private MirrorLagClient() {
    }

    public static void receive(MirrorLag.Payload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        clear();
        if (level == null || me == null) {
            return;
        }
        plane = new MirrorLag.Plane(payload.axis(), payload.plane());
        delay = Math.max(1, payload.delay());
        left = payload.duration();
        stare = 0;
        fade = 0;
        HISTORY.clear();
        WatcherEntity e = new WatcherEntity(PrologueRegistry.WATCHER.get(), level);
        e.setId(-0x5A7D0000);
        Vec3 at = plane.reflect(me.position());
        float yaw = plane.reflectYaw(me.getYRot());
        e.moveTo(at.x, at.y, at.z, yaw, 0f);
        e.setYHeadRot(yaw);
        e.alpha = e.prevAlpha = 0f;
        e.eyeGlow = e.prevEyeGlow = 0.2f;
        level.addEntity(e);
        shade = e;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        WatcherEntity e = shade;
        if (e == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || e.level() != mc.level || plane == null) {
            clear();
            return;
        }
        HISTORY.addLast(new Pose(me.position(), me.getYRot()));
        while (HISTORY.size() > delay + 1) {
            HISTORY.removeFirst();
        }
        Pose old = HISTORY.peekFirst();
        boolean turned = stare >= STARE_TICKS;
        if (old != null && !turned) {
            Vec3 at = plane.reflect(old.pos());
            float yaw = plane.reflectYaw(old.yaw());
            // setPos (moveTo は前の位置も書き換えて補間が切れる)
            e.setPos(at.x, at.y, at.z);
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.headYaw = 0f;
        } else if (turned) {
            // 先にこちらを向く
            double dx = me.getX() - e.getX();
            double dz = me.getZ() - e.getZ();
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            e.setYRot(Mth.approachDegrees(e.getYRot(), want, 6f));
            e.setYHeadRot(e.getYRot());
        }
        left--;
        if (fade > 0) {
            fade--;
            e.alpha = ALPHA * fade / FADE;
            e.eyeGlow = 0.5f * fade / FADE;
            if (fade == 0) {
                clear();
            }
            return;
        }
        e.alpha = Math.min(ALPHA, e.alpha + ALPHA / 20f);
        if (staring(me, e)) {
            stare++;
            if (stare == STARE_TICKS) {
                e.eyeGlow = 0.5f;
            }
            if (stare >= STARE_TICKS + 20) {
                fade = FADE;
            }
        }
        if (left <= 0) {
            fade = FADE;
        }
    }

    private static boolean staring(LocalPlayer me, WatcherEntity e) {
        Vec3 eye = me.getEyePosition();
        Vec3 to = e.position().add(0, e.getBbHeight() * 0.8, 0).subtract(eye);
        if (to.lengthSqr() < 1e-4) {
            return false;
        }
        double cos = me.getViewVector(1f).normalize().dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(STARE_CONE));
    }

    static void clear() {
        if (shade != null) {
            shade.discard();
        }
        shade = null;
        plane = null;
        HISTORY.clear();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
