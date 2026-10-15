package jp.revati.skyabyss.client.sky;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.sky.CloudEyeEndPayload;
import jp.revati.skyabyss.sky.CloudEyeOutcome;
import jp.revati.skyabyss.sky.CloudEyePayload;
import jp.revati.skyabyss.sky.CloudEyeStage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 「雲の切れ間の巨大な目」のクライアント側 (見せる本人の画面だけ)。
// 流れ: 気配 (音楽と動物の声が止み、雲が集まる) → 雲が上下に割れて目がゆっくり開く → 目は地上を探すように動き、
//       やがてこちらを向く → 閉じる → 雲が引く。
// 見つめると (視線の真ん中に0.4秒) 視線が合い、その瞬間に音がすっと消え、静けさの中で目が閉じる。
// ジャンプスケアにしない: 開くのは2.5秒かけて、視界の端 (今見ている向きから左右に外した所) に。大きな音は鳴らさない。
// 描画は CloudEyeRenderer、音は CloudEyeSound
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CloudEyeClient {
    // 雲の高さから目をどれだけ下に置くか (バニラの雲より手前に見せる)
    static final float BELOW_CLOUDS = 6f;
    // 雲の高さまでこれより近い (高い所にいる) ときは開かない
    static final float MIN_HEIGHT_ABOVE_EYE = 40f;
    // 見つめたとみなすまで (tick)、画面に入ったとみなすまで (tick)
    static final int STARE_TICKS = 8;
    static final int SEEN_TICKS = 10;
    // 視線が合ってから閉じ始めるまで、閉じるのにかかる時間
    static final int MET_HOLD = 24;
    static final int MET_CLOSE = 30;
    // 屋内に入ってこれだけたったら閉じ始める
    static final int INDOOR_CLOSE = 40;
    // 視線が合ったあと、静けさが続く時間 (音楽・動物の声・環境音を止めたまま)
    static final int SILENCE_AFTER = 300;
    // 開発用: 開いたまま止める (-Dskyabyss.eyeHold=true。撮影用)
    private static final boolean HOLD = Boolean.getBoolean("skyabyss.eyeHold");

    enum Phase { HUSH, OPENING, OPEN, MET, CLOSING, CLEARING }

    static final class Eye {
        final int id;
        final Vec3 pos;
        final CloudEyeStage stage;
        final int seed;
        Phase phase = Phase.HUSH;
        int phaseTicks;
        int age;
        float open;
        float openBefore;
        float patch;
        float patchBefore;
        float irisX;
        float irisY;
        float irisXBefore;
        float irisYBefore;
        int stareTicks;
        int seenTicks;
        int indoorTicks;
        int closeTicks = CloudEyeStage.CLOSE_TICKS;
        boolean met;
        boolean cancelled;
        boolean visible = true;

        Eye(int id, Vec3 pos, CloudEyeStage stage, int seed) {
            this.id = id;
            this.pos = pos;
            this.stage = stage;
            this.seed = seed;
            // はじめは下 (地上) を、少し横を見ている
            this.irisX = ((seed & 1) == 0 ? 0.5f : -0.5f);
            this.irisY = -0.7f;
            this.irisXBefore = irisX;
            this.irisYBefore = irisY;
        }

        float open(float partial) {
            return Mth.lerp(partial, openBefore, open);
        }

        float patch(float partial) {
            return Mth.lerp(partial, patchBefore, patch);
        }

        float irisX(float partial) {
            return Mth.lerp(partial, irisXBefore, irisX);
        }

        float irisY(float partial) {
            return Mth.lerp(partial, irisYBefore, irisY);
        }
    }

    @Nullable
    private static Eye eye;
    @Nullable
    private static CloudEyeSound sound;
    private static float volume;
    private static int silence;

    private CloudEyeClient() {
    }

    public static void receive(CloudEyePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        if (!Config.CLOUD_EYE_VISIBLE.get()) {
            send(payload.id(), CloudEyeOutcome.DISABLED);
            return;
        }
        if (eye != null) {
            send(eye.id, CloudEyeOutcome.CANCELLED);
        }
        float cloud = level.effects().getCloudHeight();
        if (Float.isNaN(cloud)) {
            cloud = 192f;
        }
        Vec3 cam = mc.player.getEyePosition();
        double h = cloud - BELOW_CLOUDS - cam.y;
        if (level.dimension() != Level.OVERWORLD || h < MIN_HEIGHT_ABOVE_EYE) {
            send(payload.id(), CloudEyeOutcome.CANCELLED);
            return;
        }
        double horizontal = h / Math.tan(Math.toRadians(Mth.clamp(payload.elevation(), 20f, 80f)));
        double yaw = Math.toRadians(payload.yaw());
        Vec3 pos = new Vec3(cam.x - Math.sin(yaw) * horizontal, cam.y + h, cam.z + Math.cos(yaw) * horizontal);
        eye = new Eye(payload.id(), pos, CloudEyeStage.byIndex(payload.stage()), payload.seed());
        mc.getMusicManager().stopPlaying();
    }

    @Nullable
    static Eye current() {
        return eye;
    }

    static float volume() {
        return volume;
    }

    static boolean soundWanted() {
        return eye != null || volume > 0.005f;
    }

    static float intensity() {
        return (float) (double) Config.CLOUD_EYE_INTENSITY.get();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (silence > 0) {
            silence--;
        }
        Eye e = eye;
        if (e == null || level == null || mc.player == null) {
            tickSound(mc, null);
            return;
        }
        if (level.dimension() != Level.OVERWORLD || !mc.player.isAlive()) {
            finish(e, CloudEyeOutcome.CANCELLED);
            tickSound(mc, null);
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        e.openBefore = e.open;
        e.patchBefore = e.patch;
        e.irisXBefore = e.irisX;
        e.irisYBefore = e.irisY;
        e.age++;
        e.phaseTicks++;

        // 屋内に入ったら閉じる (屋根越しには見えない)
        boolean outdoors = level.canSeeSky(mc.player.blockPosition().above());
        e.indoorTicks = outdoors ? 0 : e.indoorTicks + 1;
        if (e.indoorTicks > INDOOR_CLOSE && (e.phase == Phase.HUSH || e.phase == Phase.OPENING || e.phase == Phase.OPEN)) {
            e.cancelled = e.seenTicks < SEEN_TICKS;
            to(e, Phase.CLOSING);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        // 遮るものがないか (4tick ごとに調べる)
        if (e.age % 4 == 1) {
            e.visible = clear(mc, level, e.pos);
        }
        boolean visible = e.visible;
        if (e.open > 0.2f && visible && onScreen(mc, camera, e.pos)) {
            e.seenTicks++;
        }
        if (!e.met && e.open > 0.35f && visible && angleFromCenter(camera, e.pos) < stareCone(camera, e)) {
            e.stareTicks++;
        } else if (!e.met) {
            e.stareTicks = Math.max(0, e.stareTicks - 1);
        }
        if (!e.met && e.stareTicks >= STARE_TICKS && (e.phase == Phase.OPENING || e.phase == Phase.OPEN)) {
            // 視線が合った: 音が止み、目はこちらを見たまま静かに閉じる
            e.met = true;
            silence = SILENCE_AFTER;
            mc.getMusicManager().stopPlaying();
            to(e, Phase.MET);
        }

        float irisTargetX;
        float irisTargetY;
        float irisSpeed = 0.035f;
        switch (e.phase) {
            case HUSH -> {
                e.patch = Math.min(1f, e.patch + 1f / CloudEyeStage.HUSH_TICKS);
                if (e.phaseTicks >= CloudEyeStage.HUSH_TICKS) {
                    to(e, Phase.OPENING);
                }
            }
            case OPENING -> {
                e.patch = 1f;
                e.open = smooth(e.phaseTicks / (float) CloudEyeStage.OPEN_TICKS);
                if (e.phaseTicks >= CloudEyeStage.OPEN_TICKS) {
                    to(e, Phase.OPEN);
                }
            }
            case OPEN -> {
                e.open = 1f;
                if (e.phaseTicks >= e.stage.holdTicks() && !HOLD) {
                    to(e, Phase.CLOSING);
                }
            }
            case MET -> {
                if (e.phaseTicks >= MET_HOLD && !HOLD) {
                    e.closeTicks = MET_CLOSE;
                    to(e, Phase.CLOSING);
                }
            }
            case CLOSING -> {
                e.open = Math.max(0f, e.open - 1f / e.closeTicks);
                if (e.open <= 0f) {
                    to(e, Phase.CLEARING);
                }
            }
            case CLEARING -> {
                e.patch = Math.max(0f, e.patch - 1f / CloudEyeStage.CLEAR_TICKS);
                if (e.patch <= 0f) {
                    finish(e, outcome(e));
                }
            }
        }

        // 瞳の向き: はじめは地上を探すように動き、開いている時間の後半 (段階が進むほど早く) にこちらを向く。
        // 視線が合ったら、すぐにこちらを向いたまま止まる
        float turnAt = e.stage.holdTicks() * (e.stage.index() >= 2 ? 0.2f : 0.55f);
        boolean looking = e.met || e.phase == Phase.OPEN && e.phaseTicks > turnAt || e.phase == Phase.CLOSING && !e.cancelled;
        if (looking) {
            irisTargetX = 0f;
            irisTargetY = 0f;
            irisSpeed = e.met ? 0.22f : 0.05f;
        } else {
            float t = e.age * 0.035f + (e.seed & 0xFF) * 0.1f;
            irisTargetX = 0.55f * Mth.sin(t);
            irisTargetY = -0.55f + 0.15f * Mth.sin(t * 1.7f + 1.3f);
        }
        e.irisX += (irisTargetX - e.irisX) * irisSpeed;
        e.irisY += (irisTargetY - e.irisY) * irisSpeed;

        tickSound(mc, e);
    }

    private static CloudEyeOutcome outcome(Eye e) {
        if (e.met) {
            return CloudEyeOutcome.MET;
        }
        if (e.seenTicks >= SEEN_TICKS) {
            return CloudEyeOutcome.SEEN;
        }
        return e.cancelled ? CloudEyeOutcome.CANCELLED : CloudEyeOutcome.MISSED;
    }

    private static void to(Eye e, Phase phase) {
        e.phase = phase;
        e.phaseTicks = 0;
    }

    private static void finish(Eye e, CloudEyeOutcome outcome) {
        send(e.id, outcome);
        if (eye == e) {
            eye = null;
        }
    }

    private static void send(int id, CloudEyeOutcome outcome) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new CloudEyeEndPayload(id, outcome.ordinal()));
        }
    }

    // 気配の音: 雲が集まると低く鳴りはじめ、目が開くほど少し大きく。視線が合った瞬間に、すっと消える
    private static void tickSound(Minecraft mc, @Nullable Eye e) {
        float target = 0f;
        float rate = 0.04f;
        if (e != null) {
            switch (e.phase) {
                case HUSH -> target = 0.18f * e.patch;
                case OPENING, OPEN -> target = 0.18f + 0.32f * e.open;
                case MET -> {
                    target = 0f;
                    rate = 0.35f;
                }
                case CLOSING, CLEARING -> target = e.met ? 0f : 0.12f * e.open;
            }
            target *= intensity();
        }
        volume += (target - volume) * rate;
        if (volume < 0.002f && target == 0f) {
            volume = 0f;
        }
        if (e != null && e.phase != Phase.MET && !e.met && (sound == null || sound.isStopped())) {
            sound = new CloudEyeSound(e.pos);
            mc.getSoundManager().play(sound);
        }
        if (sound != null && sound.isStopped()) {
            sound = null;
        }
    }

    // 目が開いている間と、視線が合ったあとしばらくは、音楽・動物の声・環境音が止む (引き算)。敵の音は止めない
    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || eye == null && silence <= 0) {
            return;
        }
        SoundSource source = s.getSource();
        String path = s.getLocation().getPath();
        if (source == SoundSource.MUSIC
                || source == SoundSource.NEUTRAL && (path.endsWith(".ambient") || path.contains("parrot"))
                || source == SoundSource.AMBIENT && !SkyAbyss.MODID.equals(s.getLocation().getNamespace())
                || source == SoundSource.WEATHER && eye != null && eye.met) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        eye = null;
        sound = null;
        volume = 0f;
        silence = 0;
    }

    // 見つめたとみなす角度: 目の幅の3割ほど (遠いほど小さい)、最低5度
    private static double stareCone(Camera camera, Eye e) {
        double d = camera.getPosition().distanceTo(e.pos);
        return Math.max(5.0, Math.toDegrees(Math.atan(e.stage.width() * 0.3 / Math.max(1.0, d))));
    }

    private static boolean clear(Minecraft mc, ClientLevel level, Vec3 target) {
        Vec3 from = mc.gameRenderer.getMainCamera().getPosition();
        // 遠すぎる所までは調べない (描画距離の外はブロックがない)
        Vec3 to = from.add(target.subtract(from).normalize().scale(Math.min(from.distanceTo(target), 160.0)));
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
    }

    static boolean onScreen(Minecraft mc, Camera camera, Vec3 target) {
        Vec3 d = target.subtract(camera.getPosition());
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double forward = d.x * look.x() + d.y * look.y() + d.z * look.z();
        if (forward <= 0.1) {
            return false;
        }
        double x = (d.x * left.x() + d.y * left.y() + d.z * left.z()) / forward;
        double y = (d.x * up.x() + d.y * up.y() + d.z * up.z()) / forward;
        double halfV = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5));
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return Math.abs(y) < halfV * 0.9 && Math.abs(x) < halfV * aspect * 0.9;
    }

    static double angleFromCenter(Camera camera, Vec3 target) {
        Vec3 d = target.subtract(camera.getPosition()).normalize();
        Vector3f look = camera.getLookVector();
        double dot = Mth.clamp(d.x * look.x() + d.y * look.y() + d.z * look.z(), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
