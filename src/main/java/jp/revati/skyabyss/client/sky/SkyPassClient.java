package jp.revati.skyabyss.client.sky;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

// 「雲の上を何かが飛んで通る」のクライアント側の状態と音・天候。
// 描画は SkyPassRenderer、画面効果は SkyScreenEffects が、ここの値を読んで行う。
// マルチプレイでは、各プレイヤーが自分と通過との距離に応じて演出を受ける。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkyPassClient {
    // 羽ばたきや唸りが聞こえる距離
    private static final double HEARING_RANGE = 300.0;
    // 空が荒れて見える距離
    private static final double STORM_RANGE = 380.0;
    // 羽ばたき音の一番大きい瞬間が、音の頭から何tick後にあるか
    private static final double WINGBEAT_PEAK_DELAY = 31.0;

    private static final RandomSource RANDOM = RandomSource.create();

    @Nullable
    private static SkyPass current;
    @Nullable
    private static SkyDroneSound drone;
    private static int lastWingbeat;
    private static int lastPeak;
    private static int lastCall;

    // 0〜1 の演出の強さ。描画と画面効果が読む
    private static float proximity;
    private static float storm;
    private static float underShadow;
    private static float shake;
    private static float glow;
    // 頭の上がどれだけ覆われているか (0 = 空が見える、1 = 屋根・岩・地下で空の光が届かない)。ゆっくり追いかける (戸口を出入りしても急に変わらない)
    private static float cover;

    // 天候を上書きする前の、サーバーから届いた本来の値
    private static boolean weatherOverridden;
    private static float serverRain;
    private static float serverThunder;
    private static float appliedRain = -1f;

    private SkyPassClient() {
    }

    public static void receive(SkyPass pass) {
        current = pass;
        double age = 0;
        lastWingbeat = pass.flapIndex(age + WINGBEAT_PEAK_DELAY);
        lastPeak = pass.flapIndex(age);
        lastCall = 0;
    }

    // 開発用 (台本の skypass off)
    public static void clear() {
        current = null;
    }

    @Nullable
    public static SkyPass current() {
        return current;
    }

    static boolean isPassing() {
        return current != null;
    }

    static float proximity() {
        return proximity;
    }

    static float storm() {
        return storm;
    }

    static float underShadow() {
        return underShadow;
    }

    static float shake() {
        return shake;
    }

    static float glow() {
        return glow;
    }

    static float cover() {
        return cover;
    }

    // 影の真下の画面の効果 (縁の暗がり・霧・視野・色味) にかける係数。空が見えれば 1、覆われていても 0.7。
    // 暗さそのものは画素ごとの影 (OverheadShadow) が世界の位置でかけるので、屋根の下でも影の輪郭の中なら周りの景色は暗い。
    // ここで弱めるのは、画面全体にかかる気配 (霧が迫る・視野が狭まる) の分だけ
    static float exposure() {
        return OverheadShadow.ready() ? 1f - 0.3f * cover : 1f - 0.88f * cover;
    }

    // 近くを通るだけの効果 (嵐の暗さ・近さ) にかける係数。屋内でも外が暗くなった気配は残す (1 → 0.3)
    static float nearExposure() {
        return 1f - 0.7f * cover;
    }

    // 音は壁の向こうから聞こえる: 覆われているほど小さく、低くこもる
    static float muffle() {
        return 1f - 0.5f * cover;
    }

    static float droneVolume() {
        return current == null ? 0f : ((float) Math.pow(proximity, 1.3) * 0.9f + storm * 0.25f) * muffle();
    }

    // 頭の上の覆い (0〜1)。空が見えれば 0。見えないとき、空の光 (その場所の天空光) が届くほど薄い (木の葉の下は 0.3 まで、屋根・地下は 1)
    static float measureCover(ClientLevel level, Vec3 eye) {
        BlockPos head = BlockPos.containing(eye);
        if (level.canSeeSky(head)) {
            return 0f;
        }
        return Mth.clamp(1f - level.getBrightness(LightLayer.SKY, head) / 15f, 0.3f, 1f);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
        drone = null;
        proximity = storm = underShadow = shake = glow = cover = 0f;
        weatherOverridden = false;
        appliedRain = -1f;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            return;
        }
        if (current != null && (level.dimension() != Level.OVERWORLD || level.getGameTime() >= current.startTick() + current.duration())) {
            current = null;
        }

        float targetProximity = 0f;
        float targetStorm = 0f;
        float targetShadow = 0f;
        cover += (measureCover(level, mc.player.getEyePosition()) - cover) * 0.12f;
        if (current != null && current.isActive(level.getGameTime())) {
            double age = current.age(level.getGameTime(), 0f);
            Vec3 pos = mc.player.position();
            double distance = Math.hypot(pos.x - current.centerX(age), pos.z - current.centerZ(age));
            targetProximity = (float) Mth.clamp(1.0 - distance / HEARING_RANGE, 0.0, 1.0);
            targetStorm = current.storm(age) * (float) Mth.clamp(1.0 - distance / STORM_RANGE, 0.0, 1.0);
            targetShadow = current.shadow(pos.x, pos.z, age) * exposure();
            playPassSounds(mc, level, current, age, targetProximity);
        }
        proximity += (targetProximity - proximity) * 0.1f;
        storm += (targetStorm - storm) * 0.03f;
        underShadow += (targetShadow - underShadow) * 0.08f;
        shake *= 0.9f;
        glow *= 0.93f;
        if (Config.SCREEN_SHAKE.get() && proximity > 0.4f) {
            // 真下に近いと、空気そのものが細かく震える
            shake = Math.max(shake, (proximity - 0.4f) * 0.25f * (0.4f + 0.6f * exposure()));
        }

        if (proximity > 0.05f) {
            mc.getMusicManager().stopPlaying();
        }
        if (drone == null && current != null && proximity > 0.01f) {
            drone = new SkyDroneSound();
            mc.getSoundManager().play(drone);
        } else if (drone != null && drone.isStopped()) {
            drone = null;
        }
        updateWeather(level);
    }

    private static void playPassSounds(Minecraft mc, ClientLevel level, SkyPass pass, double age, float near) {
        // 羽ばたき: 音の一番大きい瞬間が、影の翼が広がりきる瞬間に重なるよう先に鳴らす
        int wingbeat = pass.flapIndex(age + WINGBEAT_PEAK_DELAY);
        if (wingbeat != lastWingbeat) {
            lastWingbeat = wingbeat;
            if (near > 0f) {
                playGlobal(mc, ModSounds.SKY_WINGBEAT.get(), (0.25f + near * 0.95f) * muffle(), (0.85f + RANDOM.nextFloat() * 0.15f) * (1f - 0.1f * cover));
            }
        }
        int peak = pass.flapIndex(age);
        if (peak != lastPeak) {
            lastPeak = peak;
            if (near > 0f && Config.SCREEN_SHAKE.get()) {
                shake = Math.max(shake, near * near * 1.2f * (0.4f + 0.6f * exposure()));
            }
        }
        // 呼び声は遠くまで届き、雲の中が一瞬光る
        int call = pass.callIndex(age);
        if (call > lastCall) {
            lastCall = call;
            float heard = Math.max(near, storm);
            if (heard > 0f) {
                playGlobal(mc, ModSounds.SKY_CALL.get(), (0.5f + heard * 0.7f) * muffle(), (0.9f + RANDOM.nextFloat() * 0.1f) * (1f - 0.1f * cover));
                glow = 1f;
                level.setSkyFlashTime(3);
            }
        }
    }

    private static void playGlobal(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.AMBIENT, volume, pitch, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    // 通過が近づくと、このプレイヤーの画面だけ空が急に荒れる（サーバーの天気は変えない）
    private static void updateWeather(ClientLevel level) {
        float rainNow = level.getRainLevel(1f);
        if (!weatherOverridden || Math.abs(rainNow - appliedRain) > 1.0e-4f) {
            // サーバーから天気の更新が届いた（または上書き前）なので、本来の値として覚えておく
            serverRain = rainNow;
            serverThunder = rainNow > 0f ? level.getThunderLevel(1f) / rainNow : 0f;
        }
        if (storm < 0.005f) {
            if (weatherOverridden) {
                level.setRainLevel(serverRain);
                level.setThunderLevel(serverThunder);
                weatherOverridden = false;
                appliedRain = -1f;
            }
            return;
        }
        float rain = Math.max(serverRain, storm * 0.7f);
        float thunder = Math.max(serverThunder, storm);
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
        appliedRain = rain;
        weatherOverridden = true;
    }

    // BGM を止めるか (client/music/MusicDirector。近くを通過している間)
    public static boolean hushesMusic() {
        return current != null && proximity >= 0.05f;
    }

    // 近くを通過している間は、音楽と動物の鳴き声が止む
    @SubscribeEvent
    static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (current == null || sound == null || proximity < 0.05f) {
            return;
        }
        if (sound.getSource() == SoundSource.MUSIC) {
            event.setSound(null);
            return;
        }
        String path = sound.getLocation().getPath();
        if (sound.getSource() == SoundSource.NEUTRAL && (path.endsWith(".ambient") || path.contains("parrot"))) {
            event.setSound(null);
        }
    }
}
