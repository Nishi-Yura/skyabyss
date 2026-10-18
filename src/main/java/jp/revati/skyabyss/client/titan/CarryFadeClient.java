package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

// 巨神に地上へ降ろされるときの暗転 (TitanEnd。docs/design/01 の 4.3)。画面が黒へ落ち、しばらく黒のまま、明ける
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CarryFadeClient {
    private static int in;
    private static int hold;
    private static int out;
    // 経過 tick (-1 = 動いていない)
    private static int ticks = -1;

    private CarryFadeClient() {
    }

    public static void receive(TitanEndNetwork.Fade fade) {
        Minecraft.getInstance().execute(() -> {
            in = Math.max(1, fade.in());
            hold = Math.max(0, fade.hold());
            out = Math.max(0, fade.out());
            ticks = 0;
        });
    }

    // 黒の濃さ (0〜1)
    static float alpha(float partial) {
        if (ticks < 0) {
            return 0f;
        }
        float t = ticks + partial;
        if (t < in) {
            return smooth(t / in);
        }
        if (t < in + hold) {
            return 1f;
        }
        if (out == 0) {
            return 0f;
        }
        return 1f - smooth((t - in - hold) / out);
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (ticks >= 0 && !Minecraft.getInstance().isPaused()) {
            ticks++;
            if (ticks > in + hold + out + 2) {
                ticks = -1;
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ticks = -1;
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "carry_fade"), CarryFadeClient::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        float a = alpha(delta.getGameTimeDeltaPartialTick(false));
        if (a > 0.004f) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 255f) << 24));
        }
    }
}
