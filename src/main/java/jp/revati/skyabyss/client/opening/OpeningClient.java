package jp.revati.skyabyss.client.opening;

import javax.annotation.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.OpeningLogic;
import jp.revati.skyabyss.opening.OpeningNetwork;
import jp.revati.skyabyss.opening.OpeningNetwork.CuePayload;
import jp.revati.skyabyss.opening.OpeningNetwork.ObjectivePayload;
import jp.revati.skyabyss.opening.OpeningSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

// 序盤の拍子の、画面と音のクライアント側 (docs/design/17 の 3 章)。サーバーは「いつ始まるか」だけを知らせ、演出はここで自分の時計 (tick) で進める。
//   B0 目覚め      : 暗転・まぶたの開き・題字・一行 (OpeningOverlay が描く)。鼓動と遠い鐘
//   B6 夜の足音    : 5 秒かけて環境音と動物の声が消え、3 秒おきに 3 度、遠くの巨大な足音と小さな揺れ。5 秒の無音のあと、虫の音がゆっくり戻る
//   B9 夜の歌      : 40 秒の歌。星の欠けは StarBandRenderer が描く
//   目的の帯       : 序盤の目的と目印 (ObjectiveStrip が読む)
// 画面の揺れ (screenShake) と効果の強さ (screenEffectStrength) で弱められる。音は急に鳴らさず、5 秒かけて消す・戻す (ジャンプスケア禁止)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class OpeningClient {
    // 序盤の目的 (id は objective.skyabyss.<ID>)
    public record Objective(String id, @Nullable BlockPos target) {
    }

    // 足音の時刻 (tick。始まりから): 無音への移り 5 秒 → 3 秒おきに 3 度 → 5 秒の無音 → 戻り 5 秒
    private static final int[] STEP_AT = {OpeningLogic.STEPS_FADE, OpeningLogic.STEPS_FADE + OpeningLogic.STEPS_GAP,
            OpeningLogic.STEPS_FADE + 2 * OpeningLogic.STEPS_GAP};
    private static final float[] STEP_VOLUME = {0.8f, 1.0f, 1.0f};
    private static final float[] STEP_PITCH = {0.95f, 0.85f, 0.75f};
    private static final int[] STEP_DISTANCE = {70, 48, 28};
    private static final int SILENCE_FULL_UNTIL = OpeningLogic.STEPS_FADE + 2 * OpeningLogic.STEPS_GAP + 100;
    private static final int SILENCE_END = SILENCE_FULL_UNTIL + 100;
    // 歌: 星の欠けは 5 秒後から 30 秒
    public static final int SONG_LENGTH = 800;
    public static final int BAND_START = 100;
    public static final int BAND_LENGTH = 600;

    @Nullable
    private static Objective objective;
    // 自分の時計 (一時停止中は進めない)
    private static long clock;
    private static long awakeStart = Long.MIN_VALUE;
    private static long stepsStart = Long.MIN_VALUE;
    private static float stepsYaw;
    private static long songStart = Long.MIN_VALUE;
    // 揺れの余韻 (足音のたびに足し、減らす)
    private static float shake;

    private OpeningClient() {
    }

    // ---------------------------------------------------------------- 受信
    public static void receive(ObjectivePayload payload) {
        objective = payload.id().isEmpty() ? null : new Objective(payload.id(), payload.hasTarget() ? BlockPos.of(payload.target()) : null);
    }

    public static void receive(CuePayload payload) {
        switch (payload.kind()) {
            case OpeningNetwork.CUE_AWAKE -> {
                awakeStart = clock;
                // 低い風、かすかな地鳴り、鼓動、遠い鐘 (暗いうちに重なっていく)
                play(OpeningSetup.WIND.get(), 0.5f, 1.0f, 0);
                play(OpeningSetup.RUMBLE.get(), 0.22f, 0.9f, 10);
                play(OpeningSetup.HEARTBEAT.get(), 0.8f, 1.0f, 0);
                play(OpeningSetup.BELL.get(), 0.4f, 0.85f, 70);
            }
            case OpeningNetwork.CUE_FOOTSTEPS -> {
                stepsStart = clock;
                stepsYaw = payload.yaw();
            }
            case OpeningNetwork.CUE_SONG -> {
                songStart = clock;
                play(OpeningSetup.SONG.get(), 0.55f, 1.0f, 0);
            }
            case OpeningNetwork.CUE_STOP -> stop();
            default -> {
            }
        }
    }

    private static void stop() {
        awakeStart = Long.MIN_VALUE;
        stepsStart = Long.MIN_VALUE;
        songStart = Long.MIN_VALUE;
        shake = 0f;
        Minecraft.getInstance().getSoundManager().stop(OpeningSetup.SONG.getId(), null);
    }

    // ---------------------------------------------------------------- 読み出し
    // 帯に出す序盤の目的 (オーバーワールドにいるときだけ。ほかの世界では帯はクエストの目的を出す)
    @Nullable
    public static Objective objective() {
        Minecraft mc = Minecraft.getInstance();
        if (objective == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return objective;
    }

    // ホラー演出 (足音・歌) の最中か (目的の帯を薄くする)
    public static boolean horrorCueActive() {
        return silence() > 0.01f || songTicks() >= 0;
    }

    // 開幕の暗転・題字の最中か (目的の帯はその間出さない)
    public static boolean awakeActive() {
        return awakeTicks() >= 0 || jp.revati.skyabyss.client.cinematic.CinematicClient.active();
    }

    // 目覚めの演出の経過 tick (終わっていれば -1)
    static int awakeTicks() {
        if (awakeStart == Long.MIN_VALUE) {
            return -1;
        }
        long t = clock - awakeStart;
        return t > OpeningOverlay.TOTAL ? -1 : (int) t;
    }

    // 歌の経過 tick (終わっていれば -1)
    static int songTicks() {
        if (songStart == Long.MIN_VALUE) {
            return -1;
        }
        long t = clock - songStart;
        return t > SONG_LENGTH ? -1 : (int) t;
    }

    static int stepsTicks() {
        if (stepsStart == Long.MIN_VALUE) {
            return -1;
        }
        long t = clock - stepsStart;
        return t > OpeningLogic.STEPS_TOTAL ? -1 : (int) t;
    }

    // 環境音をどれだけ消すか (0〜1)
    static float silence() {
        int t = stepsTicks();
        if (t < 0) {
            return 0f;
        }
        if (t < OpeningLogic.STEPS_FADE) {
            return smooth(t / (float) OpeningLogic.STEPS_FADE);
        }
        if (t < SILENCE_FULL_UNTIL) {
            return 1f;
        }
        if (t < SILENCE_END) {
            return 1f - smooth((t - SILENCE_FULL_UNTIL) / (float) (SILENCE_END - SILENCE_FULL_UNTIL));
        }
        return 0f;
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    // 星の欠けの帯: 西から東へ。0 = 始め、1 = 終わり。まだ・もう動いていなければ -1
    static float bandProgress() {
        int t = songTicks();
        if (t < BAND_START || t > BAND_START + BAND_LENGTH) {
            return -1f;
        }
        return (t - BAND_START) / (float) BAND_LENGTH;
    }

    // 歌のあいだ、この mod 自身の星を描く強さ (0〜1)。バニラの星は雨・雲・光源 mod やシェーダーで見えないことがあるので、欠けが読めるよう自前の星を重ねる。
    // 帯が動き出す前に 3 秒で現れ、帯が去ったあと 3 秒で消える
    static float starLayer() {
        int t = songTicks();
        if (t < 0) {
            return 0f;
        }
        return smooth((t - 20) / 80f) * (1f - smooth((t - (BAND_START + BAND_LENGTH)) / 80f));
    }

    // 画面の揺れ・効果の強さを掛けたもの (設定で 0 なら 0)
    static float effectScale() {
        return Config.SCREEN_EFFECT_STRENGTH.get().floatValue();
    }

    // ---------------------------------------------------------------- 音
    // delay: 何 tick あとに鳴らすか (0 なら今)。ここでは予約を持たず、すぐ鳴らす (遅れは呼ぶ側の拍子で作る)
    private static void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch, int delay) {
        Minecraft mc = Minecraft.getInstance();
        if (delay > 0) {
            PENDING.add(new Pending(clock + delay, sound, volume, pitch));
            return;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private record Pending(long at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
    }

    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    private static void playStep(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // 向きは足音ごとに近づく。方角はプレイヤーの yaw と同じ決め方 (0 = 南)
        double rad = Math.toRadians(stepsYaw);
        double d = STEP_DISTANCE[index];
        double x = mc.player.getX() - Math.sin(rad) * d;
        double z = mc.player.getZ() + Math.cos(rad) * d;
        float volume = STEP_VOLUME[index];
        mc.getSoundManager().play(new SimpleSoundInstance(OpeningSetup.STEP.get(), SoundSource.AMBIENT, volume, STEP_PITCH[index], RandomSource.create(),
                x, mc.player.getY(), z));
        // 重ねる低音の層: 音量は 1 でそろうので、半音ほど下げた同じ音を足して重く聞かせる
        mc.getSoundManager().play(new SimpleSoundInstance(OpeningSetup.STEP.get(), SoundSource.AMBIENT, volume * 0.85f, STEP_PITCH[index] * 0.7f,
                RandomSource.create(), x, mc.player.getY(), z));
        // 地面の揺れ: 1 回ごとに大きく (ふだんの影より弱く。画面の揺れの設定で切れる)
        shake = Math.max(shake, 0.5f + 0.35f * index);
    }

    // ---------------------------------------------------------------- 時計と拍子
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) {
            return;
        }
        clock++;
        if (awakeActive()) {
            // 開幕 (カメラの場面も含む) の間は、バニラのチュートリアルの札 (W A S D ...) や、ほかの札を出さない。操作の案内は目的の帯 (煙の立つ方) に添える
            if (mc.options.tutorialStep != net.minecraft.client.tutorial.TutorialSteps.NONE) {
                // チュートリアルは、この世界の開幕でそのまま終わりにする (あとから「W A S D」の札が出てこないように。一度だけ設定を書く)
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            }
            mc.getToasts().clear();
        }
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(p -> {
                if (clock >= p.at()) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(p.sound(), p.pitch(), p.volume()));
                    return true;
                }
                return false;
            });
        }
        // 歌の保険: 歌が降り始めて 3 秒たっても足元を向いたままなら、見上げるよう促す (星の欠けは空を見ないと分からない)
        if (songTicks() == 60 && mc.player != null && mc.player.getXRot() > -25f) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("message.skyabyss.song_look_up"), false);
        }
        int steps = stepsTicks();
        if (steps >= 0) {
            for (int i = 0; i < STEP_AT.length; i++) {
                if (steps == STEP_AT[i]) {
                    playStep(i);
                    if (i == STEP_AT.length - 1) {
                        // 3 度目のあと、地鳴りが遠のいていく
                        PENDING.add(new Pending(clock + 15, OpeningSetup.RUMBLE.get(), 0.55f, 1.0f));
                    }
                }
            }
        }
        shake *= 0.94f;
    }

    // ワールドを抜けたら、演出を持ち越さない
    @SubscribeEvent
    static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        objective = null;
        stop();
        PENDING.clear();
    }

    // 足音の間、環境音と動物の声を新しく鳴らさない (すでに鳴っているものは自然に終わる)。ほかの mod の音も同様
    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        float silence = silence();
        if (silence <= 0.01f || event.getSound() == null) {
            return;
        }
        var sound = event.getSound();
        if (sound.getLocation().getNamespace().equals(SkyAbyss.MODID) && sound.getLocation().getPath().startsWith("opening_")) {
            return;
        }
        SoundSource source = sound.getSource();
        if (source == SoundSource.AMBIENT || source == SoundSource.NEUTRAL || source == SoundSource.HOSTILE || source == SoundSource.WEATHER) {
            if (RandomSource.create().nextFloat() < silence) {
                event.setSound(null);
            }
        }
    }

    // 地面の揺れ (画面の揺れ: 設定で切れる。ふだんの影より弱い)
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake < 0.01f || !Config.SCREEN_SHAKE.get()) {
            return;
        }
        float amount = shake * effectScale() * 0.5f;
        double t = clock + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.1) * amount * 0.6f);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 1.3 + 1.0) * amount * 0.3f);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 2.7 + 2.0) * amount * 0.4f);
    }

    // 画面 (暗転・題字・足音の間のかすかな暗さ)。手の込んだ HUD (ホットバーなど) より上に重ねる
    @SubscribeEvent
    static void onLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "opening_overlay"),
                (graphics, delta) -> OpeningOverlay.render(graphics, silence() * effectScale()));
    }
}
