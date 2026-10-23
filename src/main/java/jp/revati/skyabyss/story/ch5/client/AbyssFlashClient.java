package jp.revati.skyabyss.story.ch5.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssNetwork;
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

// 地上へ帰る磁石石の光 (story/ch5/AbyssReturn)。画面がやわらかい白へ明るくなり、少しそのまま、明けると都の下の門の前
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssFlashClient {
    private static int in;
    private static int hold;
    private static int out;
    private static int rgb = 0xFFF8E8;
    private static int ticks = -1;

    private AbyssFlashClient() {
    }

    public static void receive(AbyssNetwork.Flash f) {
        Minecraft.getInstance().execute(() -> {
            in = Math.max(1, f.in());
            hold = Math.max(0, f.hold());
            out = Math.max(1, f.out());
            rgb = f.rgb() & 0xFFFFFF;
            ticks = 0;
        });
    }

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
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_flash"), AbyssFlashClient::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        float a = alpha(delta.getGameTimeDeltaPartialTick(false));
        if (a > 0.004f) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (a * 235f) << 24) | rgb);
        }
    }
}
