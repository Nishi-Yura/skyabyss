package jp.revati.skyabyss.titan.ember.lore.client;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 夜明けの地鳴り (13 の 9.4節) の画面側: 遠い地鳴りを一度鳴らし、足もとを 3 秒ほどごく小さく揺らす。
// 揺れは画面の揺れの設定 (Config.SCREEN_SHAKE) を切っていれば出さない。音は A の ember_world_turn (なければバニラの低い音)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EmberFinaleClient {
    private static final int SHAKE_TICKS = 60;
    private static long shakeUntil = -1;

    private EmberFinaleClient() {
    }

    public static void play() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(EmberIds.id(EmberIds.SOUND_WORLD_TURN)).orElse(SoundEvents.WARDEN_HEARTBEAT);
        float pitch = sound == SoundEvents.WARDEN_HEARTBEAT ? 0.5f : 1.0f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.9f));
        shakeUntil = mc.level.getGameTime() + SHAKE_TICKS;
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shakeUntil < 0 || !Config.SCREEN_SHAKE.get()) {
            return;
        }
        double t = mc.level.getGameTime() + event.getPartialTick();
        double left = shakeUntil - t;
        if (left <= 0) {
            shakeUntil = -1;
            return;
        }
        // はじめ強く、だんだん収まる。ごく小さく (最大 0.6 度)
        double k = Math.min(1, left / SHAKE_TICKS) * 0.6;
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 2.3) * k));
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 3.1 + 1.0) * k * 0.5));
    }
}
