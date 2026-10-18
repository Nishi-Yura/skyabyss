package jp.revati.skyabyss.client.prologue;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.prologue.PrologueRegistry;
import jp.revati.skyabyss.prologue.WatcherEndPayload;
import jp.revati.skyabyss.prologue.WatcherEntity;
import jp.revati.skyabyss.prologue.WatcherOutcome;
import jp.revati.skyabyss.prologue.WatcherPayload;
import jp.revati.skyabyss.prologue.WatcherSpot;
import jp.revati.skyabyss.prologue.WatcherStage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
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

// 「見る者」のクライアント側。サーバーから届いた場所に、自分の世界にだけ人影を置き、ふるまいと消え方を決める。
//
// - 立たせるのは、今見ている向きの外。振り向いたときに「いつの間にか、そこにいる」
// - 気づくと (画面に入り、遮るものがない状態が少し続くと)、かすかな耳鳴りが始まり、音楽と動物の声が止む
// - 頭だけがゆっくりこちらを追う。見つめていると、首が少しずつかしぐ
// - 見つめ続ける → 体から薄れ、目の光が最後に消える (かすかに息をのむ音)
// - 目を離す (画面の外へ・物陰に入る) → その間に音もなく消える。振り向くと、もういない
// - 近づく → 早く薄れて消える
// - 気づかれないまま時間がたつ・朝になる → 何もなかったように消える
// 攻撃しない、近づいてこない、急な大きな音も出さない。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WatcherClient {
    // 気づいたとみなすまでに、続けて見えている tick 数
    private static final int NOTICE_TICKS = 6;
    // 見た目の確かめ用 (起動時に -Dskyabyss.watcherHold=true): 見つめても近づいても消えない
    private static volatile boolean HOLD = Boolean.getBoolean("skyabyss.watcherHold");
    // 目を離してから消えるまでの tick 数
    private static final int LOOK_AWAY_TICKS = 8;
    // 見つめているとみなす、視線の中心からの角度
    private static final double STARE_CONE = 8.0;
    // 近づいたとみなす距離: 最初の距離からこれだけ寄るか、MIN_APPROACH より近い
    private static final double APPROACH_STEP = 16.0;
    private static final double MIN_APPROACH = 26.0;
    // 薄れて消えるまでの tick 数
    private static final int FADE_STARED = 40;
    private static final int FADE_APPROACHED = 14;
    private static final int FADE_IN = 60;
    // 頭が向きを変える速さ (1 tick ごとに差のこの割合だけ寄る)
    private static final float HEAD_FOLLOW = 0.05f;
    private static final float HEAD_LIMIT = 60f;
    // 朝とみなす時刻 (サーバーの夜の範囲より少し広い)
    private static final int DAY_FROM = 23200;
    private static final int DAY_UNTIL = 12800;
    // 遮るものの判定は数 tick おき
    private static final int LOS_INTERVAL = 4;

    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static WatcherEntity watcher;
    private static int id;
    private static WatcherStage stage = WatcherStage.byIndex(0);
    private static double startDistance;
    private static int age;
    private static boolean noticed;
    private static int seenTicks;
    private static int awayTicks;
    private static int stareTicks;
    private static boolean lineOfSight = true;
    private static boolean fadingIn;
    // 夜のうちに立った人影だけ、朝になったら消す (テスト用のコマンドで昼に立たせたものは消さない)
    private static boolean shownAtNight;
    @Nullable
    private static WatcherOutcome fading;
    private static int fadeTicks;
    // 耳鳴りの大きさ (WatcherPresenceSound が読む)
    private static float presence;
    @Nullable
    private static WatcherPresenceSound presenceSound;

    private WatcherClient() {
    }

    // 台本 (ShotRunner の `watcher x y z yaw`) 用: 消えずに立つ人影を、指定の位置に置く
    public static void placeForShot(double x, double y, double z, float yaw) {
        HOLD = true;
        receive(new WatcherPayload(0x7F00, x, y, z, yaw, 0));
    }

    public static void receive(WatcherPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (watcher != null) {
            finish(fading != null ? fading : noticed ? WatcherOutcome.LOOKED_AWAY : WatcherOutcome.INTERRUPTED);
        }
        if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD) {
            send(payload.id(), WatcherOutcome.INTERRUPTED);
            return;
        }
        if (!Config.WATCHER_VISIBLE.get()) {
            send(payload.id(), WatcherOutcome.DISABLED);
            return;
        }
        int cx = Mth.floor(payload.x()) >> 4;
        int cz = Mth.floor(payload.z()) >> 4;
        if (!level.getChunkSource().hasChunk(cx, cz)) {
            send(payload.id(), WatcherOutcome.INTERRUPTED);
            return;
        }
        WatcherEntity entity = new WatcherEntity(PrologueRegistry.WATCHER.get(), level);
        // サーバーの番号と重ならないよう、負の番号にする
        entity.setId(-0x5A7C0000 - (payload.id() & 0xFFFF));
        entity.moveTo(payload.x(), payload.y(), payload.z(), payload.bodyYaw(), 0f);
        entity.setYHeadRot(payload.bodyYaw());
        id = payload.id();
        stage = WatcherStage.byIndex(payload.stage());
        age = 0;
        noticed = false;
        seenTicks = awayTicks = stareTicks = 0;
        fading = null;
        fadeTicks = 0;
        lineOfSight = true;
        shownAtNight = !isDay(level);
        startDistance = Math.sqrt(mc.player.distanceToSqr(entity));
        // 届くまでのあいだに振り向いていて、もう画面の中なら、闇からゆっくり浮かび上がらせる (急に出さない)
        fadingIn = onScreen(mc, head(entity)) && clear(mc, level, head(entity));
        entity.alpha = entity.prevAlpha = fadingIn ? 0f : 1f;
        entity.eyeGlow = entity.prevEyeGlow = fadingIn ? 0f : 0.55f;
        entity.tilt = entity.prevTilt = stage.tilt() * 0.3f;
        level.addEntity(entity);
        watcher = entity;
        // 最初の兆し: 世界が静かになる (音楽が途切れ、動物が鳴き止む)
        mc.getMusicManager().stopPlaying();
    }

    private static boolean isDay(ClientLevel level) {
        long time = level.getDayTime() % 24000L;
        return time >= DAY_FROM || time < DAY_UNTIL;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        tickPresence(mc);
        WatcherEntity w = watcher;
        if (w == null) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || w.level() != level || w.isRemoved() || level.dimension() != Level.OVERWORLD) {
            finish(noticed ? WatcherOutcome.LOOKED_AWAY : WatcherOutcome.INTERRUPTED);
            return;
        }
        age++;
        if (fading != null) {
            tickFade(w);
            return;
        }
        Vec3 head = head(w);
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        if (age % LOS_INTERVAL == 0) {
            lineOfSight = clear(mc, level, head);
        }
        boolean blind = mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS);
        boolean visible = !blind && lineOfSight && w.alpha > 0.3f && onScreen(mc, head) && withinView(mc, eye, head);
        boolean staring = visible && angleFromCenter(mc, head) < STARE_CONE;

        if (fadingIn) {
            w.alpha = Math.min(1f, w.alpha + 1f / FADE_IN);
            fadingIn = w.alpha < 1f;
        }
        if (visible) {
            awayTicks = 0;
            if (++seenTicks >= NOTICE_TICKS && !noticed) {
                noticed = true;
                mc.getMusicManager().stopPlaying();
            }
        } else {
            seenTicks = 0;
            if (noticed && ++awayTicks >= LOOK_AWAY_TICKS) {
                // 目を離したすきに、音もなく消える
                finish(WatcherOutcome.LOOKED_AWAY);
                return;
            }
        }
        stareTicks = staring ? stareTicks + 1 : Math.max(0, stareTicks - 2);
        if (HOLD) {
            // 見た目の確かめ用: 消えずに立ち続ける
            aim(w, eye, staring);
            return;
        }

        double distance = Math.sqrt(mc.player.distanceToSqr(w));
        boolean morning = shownAtNight && isDay(level);
        if (distance < Math.max(MIN_APPROACH, startDistance - APPROACH_STEP)) {
            if (!noticed) {
                // 気づかないまま近くまで来てしまった: 何もなかったように消える
                finish(WatcherOutcome.UNSEEN);
                return;
            }
            startFade(w, WatcherOutcome.APPROACHED, FADE_APPROACHED);
        } else if (stareTicks >= stage.stareTicks()) {
            startFade(w, WatcherOutcome.STARED, FADE_STARED);
        } else if (!noticed && (age >= stage.lifetimeTicks() || morning || blind)) {
            finish(WatcherOutcome.UNSEEN);
            return;
        } else if (noticed && (age >= stage.lifetimeTicks() * 3 / 2 || morning)) {
            startFade(w, WatcherOutcome.STARED, FADE_STARED);
        }

        aim(w, eye, staring);
    }

    // 頭をゆっくりこちらへ向け、見つめられるほど首をかしげ、目の光を少し強める
    private static void aim(WatcherEntity w, Vec3 eye, boolean staring) {
        Vec3 head = head(w);
        double dx = eye.x - head.x;
        double dz = eye.z - head.z;
        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        // 体の向きとの差がそのまま頭の回り (GeckoLib の骨の Y 回転 = 体の向き - 見たい向き)
        float rel = Mth.wrapDegrees(w.getYRot() - targetYaw);
        if (Math.abs(rel) > HEAD_LIMIT) {
            // 首が回りきらないほどずれたら、体ごとゆっくり向き直る
            float turn = Math.copySign(Math.min(0.6f, Math.abs(rel) - HEAD_LIMIT), -rel);
            w.setYRot(w.getYRot() + turn);
            w.yRotO = w.getYRot();
            rel = Mth.clamp(rel, -HEAD_LIMIT, HEAD_LIMIT);
        }
        w.headYaw += Mth.wrapDegrees(rel - w.headYaw) * HEAD_FOLLOW;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) (Mth.atan2(head.y - eye.y, horizontal) * Mth.RAD_TO_DEG);
        w.headPitch += (Mth.clamp(pitch, -30f, 30f) - w.headPitch) * HEAD_FOLLOW;
        float progress = Mth.clamp((float) stareTicks / stage.stareTicks(), 0f, 1f);
        float tiltTarget = stage.tilt() * (0.3f + 0.7f * progress);
        w.tilt += (tiltTarget - w.tilt) * 0.04f;
        float glowTarget = noticed ? 0.55f + 0.35f * progress : 0.55f;
        if (!fadingIn) {
            w.eyeGlow += (glowTarget - w.eyeGlow) * 0.08f;
        } else {
            w.eyeGlow = 0.55f * w.alpha;
        }
    }

    private static void startFade(WatcherEntity w, WatcherOutcome outcome, int ticks) {
        fading = outcome;
        fadeTicks = ticks;
        fadingIn = false;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(new SimpleSoundInstance(PrologueRegistry.WATCHER_FADE.get().getLocation(), SoundSource.AMBIENT,
                outcome == WatcherOutcome.APPROACHED ? 0.45f : 0.3f, 0.95f + RANDOM.nextFloat() * 0.1f, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    // 体から薄れていき、目の光は少し遅れて消える
    private static void tickFade(WatcherEntity w) {
        w.alpha = Math.max(0f, w.alpha - 1f / fadeTicks);
        float eyeStep = 1f / (fadeTicks * 1.6f);
        w.eyeGlow = Math.max(0f, Math.min(w.eyeGlow, 0.9f) - eyeStep);
        if (w.alpha <= 0f && w.eyeGlow <= 0f) {
            finish(fading);
        }
    }

    private static void finish(WatcherOutcome outcome) {
        WatcherEntity w = watcher;
        watcher = null;
        fading = null;
        if (w != null && !w.isRemoved()) {
            w.discard();
        }
        send(id, outcome);
    }

    private static void send(int payloadId, WatcherOutcome outcome) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new WatcherEndPayload(payloadId, outcome));
        }
    }

    // 気づいてから消えるまで、かすかな耳鳴り。見つめるほど少し強く、消えたら引いていく
    private static void tickPresence(Minecraft mc) {
        float target = 0f;
        if (watcher != null && noticed) {
            float progress = Mth.clamp((float) stareTicks / stage.stareTicks(), 0f, 1f);
            target = fading != null ? 0f : 0.35f + 0.45f * progress;
        }
        presence += (target - presence) * (target > presence ? 0.03f : 0.08f);
        if (presence > 0.005f && (presenceSound == null || presenceSound.isStopped()) && watcher != null && noticed) {
            presenceSound = new WatcherPresenceSound();
            mc.getSoundManager().play(presenceSound);
        }
        if (presenceSound != null && presenceSound.isStopped()) {
            presenceSound = null;
        }
    }

    static float presence() {
        return presence;
    }

    static boolean presenceActive() {
        return watcher != null || presence > 0.01f;
    }

    // 人影が立っている間は、音楽・動物の鳴き声・洞窟の環境音が止む (引き算の兆し)。
    // 敵の音は止めない (身を守るための手がかりを奪わない)
    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || watcher == null) {
            return;
        }
        SoundSource source = sound.getSource();
        String path = sound.getLocation().getPath();
        if (source == SoundSource.MUSIC
                || source == SoundSource.NEUTRAL && (path.endsWith(".ambient") || path.contains("parrot"))
                || source == SoundSource.AMBIENT && !SkyAbyss.MODID.equals(sound.getLocation().getNamespace())) {
            event.setSound(null);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        watcher = null;
        fading = null;
        noticed = false;
        presence = 0f;
        presenceSound = null;
    }

    private static Vec3 head(WatcherEntity w) {
        return w.position().add(0, WatcherSpot.HEAD, 0);
    }

    private static boolean clear(Minecraft mc, ClientLevel level, Vec3 target) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        return level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
    }

    // 画面の中に入っているか (視野の 95% の内側)
    private static boolean onScreen(Minecraft mc, Vec3 target) {
        Camera camera = mc.gameRenderer.getMainCamera();
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
        return Math.abs(y) < halfV * 0.95 && Math.abs(x) < halfV * aspect * 0.95;
    }

    private static double angleFromCenter(Minecraft mc, Vec3 target) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 d = target.subtract(camera.getPosition()).normalize();
        Vector3f look = camera.getLookVector();
        double dot = Mth.clamp(d.x * look.x() + d.y * look.y() + d.z * look.z(), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    // 描画距離の内側か (外なら描かれないので、見えていないとみなす)
    private static boolean withinView(Minecraft mc, Vec3 eye, Vec3 target) {
        double reach = mc.options.getEffectiveRenderDistance() * 16.0 * 0.95;
        return eye.distanceToSqr(target) < reach * reach;
    }
}
