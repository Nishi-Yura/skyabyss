package jp.revati.skyabyss.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 通過中の画面効果。影の下にいるほど強くなる。
// 暗さ、縁の暗がり、冷たい色味、迫ってくる霧、息をのむような視野の狭まり、空気の震え。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SkyScreenEffects {
    private static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/sky_vignette.png");

    private SkyScreenEffects() {
    }

    private static float strength() {
        return Config.SCREEN_EFFECT_STRENGTH.get().floatValue();
    }

    // 影の真下でどれだけ効果をかけるか (0〜1.5)
    private static float under() {
        return SkyPassClient.underShadow() * strength();
    }

    // 近くを通っているだけでも少しかける
    private static float near() {
        return Math.max(SkyPassClient.storm() * 0.5f, SkyPassClient.proximity() * 0.35f) * strength() * SkyPassClient.nearExposure();
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Pre event) {
        float under = under();
        float near = near();
        if (under < 0.01f && near < 0.01f) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int dark = (int) (Mth.clamp(under * 0.45f + near * 0.08f, 0f, 0.8f) * 255f);
        graphics.fill(0, 0, w, h, dark << 24 | 0x04040C);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, Mth.clamp(under * 0.95f + near * 0.35f, 0f, 1f));
        graphics.blit(VIGNETTE, 0, 0, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // 霧が迫り、遠くが見えなくなる
    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        float amount = Mth.clamp(under() * 0.7f + near() * 0.3f, 0f, 0.85f);
        // 水中・溶岩・盲目の霧は、バニラのものを残す (上書きすると視界が壊れる)
        if (amount < 0.01f || event.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return;
        }
        event.setFarPlaneDistance(Mth.lerp(amount, event.getFarPlaneDistance(), Math.max(24f, event.getFarPlaneDistance() * 0.25f)));
        event.setNearPlaneDistance(Mth.lerp(amount, event.getNearPlaneDistance(), 0f));
        event.setCanceled(true);
    }

    // 色が抜け、冷たい青紫に沈む
    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float amount = Mth.clamp(under() * 0.75f + near() * 0.4f, 0f, 0.9f);
        if (amount < 0.01f) {
            return;
        }
        float gray = (event.getRed() + event.getGreen() + event.getBlue()) / 3f * 0.35f;
        event.setRed(Mth.lerp(amount, event.getRed(), gray * 0.8f));
        event.setGreen(Mth.lerp(amount, event.getGreen(), gray * 0.8f));
        event.setBlue(Mth.lerp(amount, event.getBlue(), gray * 1.15f + 0.02f));
    }

    // 羽ばたきに合わせて、わずかに視野が狭まる
    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        float under = under();
        if (under < 0.01f || SkyPassClient.current() == null) {
            return;
        }
        double t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() + event.getPartialTick() : 0;
        double breath = 0.5 + 0.5 * Math.cos(t * 2.0 * Math.PI / 70.0);
        event.setFOV(event.getFOV() * (1.0 - under * (0.03 + 0.03 * breath)));
    }

    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float shake = SkyPassClient.shake();
        if (shake < 0.01f) {
            return;
        }
        double t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(t * 1.7) * shake * 0.6f);
        event.setYaw(event.getYaw() + (float) Math.sin(t * 2.3 + 1.0) * shake * 0.4f);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 3.1 + 2.0) * shake * 0.5f);
    }
}
