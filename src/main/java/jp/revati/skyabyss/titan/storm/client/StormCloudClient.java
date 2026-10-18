package jp.revati.skyabyss.titan.storm.client;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 嵐の雲の中の画面 (12 の 2.2節)。雷翼から 96 ブロック以内にいる人の画面だけ、雨と雷の空になる (サーバーの天気は変えない)。
// 稲妻は光と遠い音だけ (火も傷もない)。SkyPassClient の天気の作りと同じ (本来の値を覚えておき、離れたら戻す)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormCloudClient {
    // 嵐の雲の半径 (ブロック)
    static final double RANGE = 96.0;
    // 遠い稲妻の音が鳴る間隔 (tick の平均)
    private static final int THUNDER_EVERY = 400;

    private static final RandomSource RANDOM = RandomSource.create();
    private static float strength;
    private static boolean overridden;
    private static float serverRain;
    private static float serverThunder;
    private static float appliedRain = -1f;

    private StormCloudClient() {
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        strength = 0f;
        overridden = false;
        appliedRain = -1f;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            return;
        }
        double distance = nearestDistance(mc, level);
        float target = (float) Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
        strength += (target - strength) * 0.05f;
        if (strength > 0.2f && RANDOM.nextInt(THUNDER_EVERY) == 0) {
            // 稲妻: 空が一瞬光り、遠くで鳴る (火も傷もない)
            level.setSkyFlashTime(3);
            mc.getSoundManager().play(new SimpleSoundInstance(StormSetup.THUNDER_FAR.get().getLocation(), SoundSource.AMBIENT,
                    0.4f + strength * 0.5f, 0.9f + RANDOM.nextFloat() * 0.1f, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
        updateWeather(level);
    }

    // 一番近い雷翼までの水平距離 (なければ大きな値)
    private static double nearestDistance(Minecraft mc, ClientLevel level) {
        AABB box = mc.player.getBoundingBox().inflate(RANGE);
        List<StormWingEntity> wings = level.getEntitiesOfClass(StormWingEntity.class, box);
        double best = Double.MAX_VALUE;
        for (StormWingEntity w : wings) {
            // 眠っている雷翼は嵐をまとわない (稲妻がやむ)
            if (w.isDormantNow()) {
                continue;
            }
            best = Math.min(best, Math.hypot(w.getX() - mc.player.getX(), w.getZ() - mc.player.getZ()));
        }
        return best;
    }

    // 天気を上書きする。離れたら、サーバーから届いた本来の値へ戻す
    private static void updateWeather(ClientLevel level) {
        float rainNow = level.getRainLevel(1f);
        if (!overridden || Math.abs(rainNow - appliedRain) > 1.0e-4f) {
            serverRain = rainNow;
            serverThunder = rainNow > 0f ? level.getThunderLevel(1f) / rainNow : 0f;
        }
        if (strength < 0.005f) {
            if (overridden) {
                level.setRainLevel(serverRain);
                level.setThunderLevel(serverThunder);
                overridden = false;
                appliedRain = -1f;
            }
            return;
        }
        float rain = Math.max(serverRain, strength * 0.8f);
        float thunder = Math.max(serverThunder, strength);
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);
        appliedRain = rain;
        overridden = true;
    }
}
