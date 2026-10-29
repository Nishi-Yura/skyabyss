package jp.revati.skyabyss.story.ch6.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.cliff.LightBody;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 星術「軽身」の画面の側 (story/ch6/cliff/LightBody)。覚えていて月の上にいるとき、空中で跳ぶ鍵を押し直すと、
// その場の跳ぶ力でもう一段跳ぶ (足が着くまで一度)。跳んでから 3 tick 以上たってから (跳んだ鍵を押しっぱなしでは出ない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class LightBodyClient {
    static final int MIN_AIR_TICKS = 3;
    private static boolean learned;
    private static boolean wasDown;
    private static boolean used;
    private static int airTicks;

    private LightBodyClient() {
    }

    public static void receive(boolean on) {
        learned = on;
    }

    public static boolean learned() {
        return learned;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        learned = false;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        boolean down = mc.options.keyJump.isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (p.onGround() || p.isInWater() || p.onClimbable() || p.getAbilities().flying || p.isPassenger()) {
            used = false;
            airTicks = 0;
            return;
        }
        airTicks++;
        if (!learned || used || !pressed || airTicks < MIN_AIR_TICKS || mc.level.dimension() != MoonWorld.LEVEL || mc.screen != null) {
            return;
        }
        used = true;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, p.getAttributeValue(Attributes.JUMP_STRENGTH), v.z);
        p.fallDistance = 0;
        PacketDistributor.sendToServer(new LightBody.Leap());
    }
}
