package jp.revati.skyabyss.client.cinematic;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.CameraMath;
import jp.revati.skyabyss.cinematic.CinematicNetwork.EndPayload;
import jp.revati.skyabyss.cinematic.CinematicNetwork.StartPayload;
import jp.revati.skyabyss.cinematic.CinematicNetwork.StopPayload;
import jp.revati.skyabyss.cinematic.Scene;
import jp.revati.skyabyss.cinematic.Scenes;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 場面 (シネマティック) のクライアント側 (docs/design/19_cinematics.md)。
// サーバーの Start を受けたら、見えないカメラ (CameraMarker) をカメラの持ち主にして、Scenes の道筋どおりに動かす。
// 入力と画面は CinematicScreen (HUD を隠し、帯・台詞・スキップの表示を描く)。スキップは Space か Esc を 1 秒押し続ける。
// どう終わっても (最後まで・スキップ・サーバーの打ち切り・退出)、カメラとHUDを元に戻す。End は本人が見ているときに一度だけサーバーへ送る
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CinematicClient {
    // スキップに必要な長押し (tick)
    static final int SKIP_HOLD = 20;
    // 暗転 (スキップ・終わり) の出入りの長さ (tick)
    static final int DIP = 8;

    enum Phase {
        PLAY,
        FADE_OUT,
        FADE_IN
    }

    // 一つの場面の進み
    static final class Run {
        final Scene scene;
        final Vec3 origin;
        final float yaw;
        final int targetId;
        Vec3 targetPos;
        final CameraMarker marker;
        final CinematicScreen screen = new CinematicScreen();
        final boolean hudBefore;
        Phase phase = Phase.PLAY;
        // PLAY のあいだの tick、暗転の tick
        int ticks;
        int dipTicks;
        int skipHeld;
        boolean skipped;
        boolean sentEnd;
        boolean restored;
        // 今の視野の倍率の元 (度。0 = そのまま)
        float fov;
        float fovOld;
        float yaw0;
        float pitch0;
        Vec3 lastLook;

        Run(Scene scene, StartPayload p, CameraMarker marker, boolean hudBefore) {
            this.scene = scene;
            this.origin = new Vec3(p.x(), p.y(), p.z());
            this.yaw = p.yaw();
            this.targetId = p.targetId();
            this.targetPos = new Vec3(p.tx(), p.ty(), p.tz());
            this.marker = marker;
            this.hudBefore = hudBefore;
        }
    }

    @Nullable
    private static StartPayload pending;
    @Nullable
    private static Run run;

    private CinematicClient() {
    }

    // ---------------------------------------------------------------- 状態
    // 場面の最中か (待っている間も含む)
    public static boolean active() {
        return run != null || pending != null;
    }

    // 開幕の場面 (opening) が開幕の演出を受け持つか (OpeningOverlay は自分では描かない)
    public static boolean takesOverOpening() {
        if (!Config.CINEMATIC_SCENES.get()) {
            return false;
        }
        return (run != null && run.scene.intro()) || (pending != null && pending.scene().equals(Scenes.OPENING));
    }

    @Nullable
    static Run run() {
        return run;
    }

    // ---------------------------------------------------------------- 受信
    public static void receive(StartPayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (run != null || Scenes.get(payload.scene()) == null) {
                return;
            }
            pending = payload;
        });
    }

    public static void receive(StopPayload payload) {
        Minecraft.getInstance().execute(() -> {
            if (pending != null && pending.scene().equals(payload.scene())) {
                pending = null;
            }
            if (run != null && run.scene.id().equals(payload.scene())) {
                // サーバーが打ち切った: すぐ戻す (End は送らない。サーバーはもう終えている)
                run.sentEnd = true;
                restore(run);
                close();
            }
        });
    }

    // ---------------------------------------------------------------- 始める・終える
    private static void begin(StartPayload p) {
        Minecraft mc = Minecraft.getInstance();
        Scene scene = Scenes.get(p.scene());
        if (scene == null || mc.level == null || mc.player == null) {
            return;
        }
        CameraMarker marker = new CameraMarker(mc.level);
        Run r = new Run(scene, p, marker, mc.options.hideGui);
        run = r;
        CameraMath.Pose pose = pose(r, 0f);
        Vec3 dir = pose.look().subtract(pose.pos());
        r.yaw0 = CameraMath.yawOf(dir);
        r.pitch0 = CameraMath.pitchOf(dir);
        marker.place(pose.pos().x, pose.pos().y, pose.pos().z, r.yaw0, r.pitch0);
        mc.options.hideGui = true;
        mc.setCameraEntity(marker);
        mc.setScreen(r.screen);
    }

    // カメラと HUD を元に戻し、操作する人の向きを合わせる (一度だけ)
    private static void restore(Run r) {
        if (r.restored) {
            return;
        }
        r.restored = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = r.hudBefore;
        if (mc.player != null) {
            if (r.scene.handBack()) {
                CameraMath.Pose last = pose(r, r.scene.duration());
                Vec3 eye = r.origin.add(0, CameraMath.EYE, 0);
                Vec3 look = last.look();
                // 狙う物は動いているので、今の位置を見る。向きは本人の今の目から
                Vec3 dir = look.subtract(mc.player.getEyePosition());
                if (dir.lengthSqr() < 1.0e-4) {
                    dir = look.subtract(eye);
                }
                float yaw = CameraMath.yawOf(dir);
                float pitch = Mth.clamp(CameraMath.pitchOf(dir), -80f, 80f);
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
                mc.player.yRotO = yaw;
                mc.player.xRotO = pitch;
                mc.player.setYHeadRot(yaw);
                mc.player.yHeadRotO = yaw;
                mc.player.yBodyRot = yaw;
                mc.player.yBodyRotO = yaw;
            }
            mc.setCameraEntity(mc.player);
        } else {
            mc.setCameraEntity(null);
        }
    }

    // 画面を閉じて、場面を片づける
    private static void close() {
        Minecraft mc = Minecraft.getInstance();
        Run r = run;
        run = null;
        pending = null;
        if (r != null && mc.screen == r.screen) {
            mc.setScreen(null);
        }
    }

    // 本人の見ている場面を終える: 最後まで見た (skipped = false) か、スキップ (true)。暗転を挟んでから戻す
    static void finish(Run r, boolean skipped) {
        if (r.phase != Phase.PLAY) {
            return;
        }
        r.skipped = skipped;
        if (!skipped && r.scene.seamless()) {
            // 最後のカメラが本人の目と同じなので、そのまま操作へ
            send(r);
            restore(r);
            close();
            return;
        }
        r.phase = Phase.FADE_OUT;
        r.dipTicks = 0;
    }

    private static void send(Run r) {
        if (!r.sentEnd) {
            r.sentEnd = true;
            PacketDistributor.sendToServer(new EndPayload(r.scene.id(), r.skipped));
        }
    }

    // ---------------------------------------------------------------- カメラの位置
    // 場面の t 秒のカメラ (狙う物は今の位置)
    static CameraMath.Pose pose(Run r, float seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (r.targetId >= 0 && mc.level != null) {
            Entity target = mc.level.getEntity(r.targetId);
            if (target != null && target.isAlive()) {
                r.targetPos = target.position();
            }
        }
        return CameraMath.sample(r.scene, seconds, new CameraMath.Ctx(r.origin, r.yaw, r.targetPos));
    }

    // 地形に入り込まないよう、本人の目から見て壁があればその手前まで引く
    private static Vec3 clip(Run r, Vec3 want) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return want;
        }
        Vec3 from = r.origin.add(0, CameraMath.EYE, 0);
        Vec3 delta = want.subtract(from);
        double len = delta.length();
        if (len < 0.5) {
            return want;
        }
        var hit = mc.level.clip(new ClipContext(from, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, r.marker));
        if (hit.getType() == HitResult.Type.MISS) {
            return want;
        }
        double back = Math.min(0.35, len);
        double reach = Math.max(0.0, hit.getLocation().subtract(from).length() - back);
        return from.add(delta.scale(reach / len));
    }

    private static void applyCamera(Run r) {
        CameraMath.Pose pose = pose(r, Math.min(r.ticks / 20f, r.scene.duration()));
        Vec3 pos = clip(r, pose.pos());
        Vec3 dir = pose.look().subtract(pos);
        if (dir.lengthSqr() < 1.0e-6) {
            dir = new Vec3(0, 0, 1);
        }
        float yaw = CameraMath.yawOf(dir);
        float pitch = Mth.clamp(CameraMath.pitchOf(dir), -89f, 89f);
        // 向きは一周しないように、前の向きにつなげる
        yaw = r.marker.getYRot() + Mth.wrapDegrees(yaw - r.marker.getYRot());
        r.marker.moveEye(pos.x, pos.y, pos.z, yaw, pitch);
        r.fovOld = r.fov;
        r.fov = pose.fov();
        r.lastLook = pose.look();
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (run != null || pending != null) {
                abort();
            }
            return;
        }
        if (pending != null) {
            if (!Config.CINEMATIC_SCENES.get()) {
                // 見ない設定: 物語の効果だけ適用して、すぐ操作へ
                PacketDistributor.sendToServer(new EndPayload(pending.scene(), true));
                pending = null;
            } else if (mc.screen == null && !mc.isPaused() && !mc.player.isDeadOrDying()) {
                StartPayload p = pending;
                pending = null;
                begin(p);
            }
            return;
        }
        Run r = run;
        if (r == null) {
            return;
        }
        if (mc.screen != r.screen) {
            // ほかの画面 (一時停止など) が重なっている間は進めない。閉じたら戻す
            if (mc.screen == null) {
                mc.setScreen(r.screen);
            }
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        switch (r.phase) {
            case PLAY -> {
                tickSkip(r, mc);
                if (r.phase == Phase.PLAY) {
                    r.ticks++;
                    applyCamera(r);
                    if (r.ticks >= r.scene.ticks()) {
                        finish(r, false);
                    }
                }
            }
            case FADE_OUT -> {
                r.dipTicks++;
                if (r.dipTicks >= DIP) {
                    send(r);
                    restore(r);
                    r.phase = Phase.FADE_IN;
                    r.dipTicks = 0;
                }
            }
            case FADE_IN -> {
                r.dipTicks++;
                if (r.dipTicks >= DIP) {
                    close();
                }
            }
        }
    }

    // Space か Esc を押し続けるとスキップ。離すと戻る
    private static void tickSkip(Run r, Minecraft mc) {
        long window = mc.getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(window, 32) || InputConstants.isKeyDown(window, 256);
        if (down && r.ticks >= 10) {
            r.skipHeld++;
            if (r.skipHeld >= SKIP_HOLD) {
                finish(r, true);
            }
        } else {
            r.skipHeld = Math.max(0, r.skipHeld - 2);
        }
    }

    // 状態を捨てて元に戻す (ワールドを抜けた・サーバーと切れた)
    private static void abort() {
        Run r = run;
        pending = null;
        if (r != null) {
            r.sentEnd = true;
            restore(r);
        }
        run = null;
    }

    @SubscribeEvent
    static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        Run r = run;
        pending = null;
        run = null;
        if (r != null) {
            mc.options.hideGui = r.hudBefore;
            r.restored = true;
        }
    }

    // 場面の視野 (撮影の fov は、設定の視野 70 との比で効かせる)
    @SubscribeEvent
    static void onComputeFov(ViewportEvent.ComputeFov event) {
        Run r = run;
        if (r == null || r.restored || r.fov <= 0f) {
            return;
        }
        float fov = Mth.lerp((float) event.getPartialTick(), r.fovOld > 0f ? r.fovOld : r.fov, r.fov);
        event.setFOV(event.getFOV() * fov / Scenes.BASE_FOV);
    }

    // ---------------------------------------------------------------- 描画の読み出し (CinematicScreen)
    static float seconds(Run r, float partial) {
        return Math.min(r.scene.duration(), (r.ticks + (r.phase == Phase.PLAY ? partial : 0f)) / 20f);
    }
}
