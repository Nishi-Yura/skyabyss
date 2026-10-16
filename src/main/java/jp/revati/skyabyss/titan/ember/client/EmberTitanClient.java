package jp.revati.skyabyss.titan.ember.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberSurfaceNetwork;
import jp.revati.skyabyss.titan.ember.EmberTitanSetup;
import jp.revati.skyabyss.titan.ember.WarmStoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 余燼の巨神 (地上) のクライアント側: 描画の登録、口に呑まれるときの「橙に沈む」画面、地鳴りの微振動、
// 眠る巨神のまわりの赤い空 (灰の降る段)、温石を持つときの手の温かさの表示
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EmberTitanClient {
    private static int plungeTotal;
    private static int plungeLeft;
    private static int plungeLeftO;
    private static int tremorTotal;
    private static int tremorLeft;
    private static float tremorStrength;
    // 灰の段 (届いた値) と、今の赤さ (ゆっくり近づく)
    private static int ashTier;
    private static int ashAge = 9999;
    private static float ashTint;
    @Nullable
    private static EmberSurfaceNetwork.StoneInfo stone;
    private static int stoneAge = 9999;

    private EmberTitanClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EmberTitanSetup.EMBER_TITAN.get(), EmberTitanRenderer::new);
        event.registerEntityRenderer(EmberTitanSetup.EMBER_MOUTH.get(), NoopRenderer::new);
    }

    // ---- パケットの受け口 (EmberSurfaceNetwork)

    public static void plunge(int ticks) {
        plungeTotal = Math.max(10, ticks);
        plungeLeft = plungeTotal;
        plungeLeftO = plungeTotal;
    }

    public static void tremor(int ticks, float strength) {
        tremorTotal = Math.max(1, ticks);
        tremorLeft = tremorTotal;
        tremorStrength = strength;
    }

    public static void ashSky(int tier) {
        ashTier = tier;
        ashAge = 0;
    }

    public static void stoneInfo(EmberSurfaceNetwork.StoneInfo info) {
        stone = info;
        stoneAge = 0;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        plungeLeftO = plungeLeft;
        if (plungeLeft > 0) {
            plungeLeft--;
        }
        if (tremorLeft > 0) {
            tremorLeft--;
        }
        ashAge++;
        stoneAge++;
        Minecraft mc = Minecraft.getInstance();
        boolean overworld = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        // 届かなくなって 5 秒で薄れ始める
        float target = overworld && ashAge < 100 ? ashTier / 3f : 0f;
        ashTint += (target - ashTint) * 0.02f;
    }

    // 地鳴りの微振動 (クライアント設定 screenShake で切れる)
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (tremorLeft <= 0 || !Config.SCREEN_SHAKE.get()) {
            return;
        }
        float fade = tremorLeft / (float) tremorTotal;
        float a = tremorStrength * fade * fade;
        double t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) Math.sin(t * 1.9) * a * 0.6f);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 1.3 + 1.0) * a * 0.5f);
    }

    // 灰の降る所では、空と霧がうすく赤くなる (遠くから巨神を探す手がかり)
    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (ashTint < 0.005f) {
            return;
        }
        float k = ashTint * 0.28f;
        event.setRed(Mth.lerp(k, event.getRed(), 0.62f));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.24f));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.16f));
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        GuiGraphics g = event.getGuiGraphics();
        if (plungeLeft > 0 || plungeLeftO > 0) {
            float left = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(true), plungeLeftO, plungeLeft);
            float a = Mth.clamp(left / plungeTotal * 2f, 0f, 1f);
            if (a > 0.01f) {
                int top = ((int) (a * 235) << 24) | 0xC2541C;
                int bottom = ((int) (a * 250) << 24) | 0x3A0E08;
                g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), top, bottom);
            }
        }
        drawStone(g);
    }

    // 温石を持つ人への表示: 手の温かさ (向き)、距離、眠っているか、次の眠りまで、頼んだ人
    private static void drawStone(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || stone == null || stoneAge > 60 || mc.options.hideGui) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof WarmStoneItem) && !(player.getOffhandItem().getItem() instanceof WarmStoneItem)) {
            return;
        }
        String key = EmberIds.LANG_SURFACE + "stone.";
        List<Component> lines = new ArrayList<>();
        if (!stone.present()) {
            lines.add(Component.translatable(key + "hud_cold"));
        } else {
            String side = switch (stone.side()) {
                case 1 -> "right";
                case -1 -> "left";
                case 2 -> "back";
                default -> "front";
            };
            lines.add(Component.translatable(key + "hud_" + side));
            lines.add(Component.translatable(key + "hud_dist_" + Mth.clamp(stone.distance(), 0, 2)));
            lines.add(Component.translatable(key + (stone.sleeping() ? "hud_sleeping" : "hud_under")));
            if (!stone.requester().isEmpty()) {
                lines.add(Component.translatable(key + "hud_asked", stone.requester()));
            }
        }
        if (stone.ticksToSleep() > 0 && (!stone.present() || !stone.sleeping())) {
            int sec = stone.ticksToSleep() / 20;
            lines.add(Component.translatable(key + "hud_until", sec / 60, String.format("%02d", sec % 60)));
        }
        int w = 0;
        for (Component c : lines) {
            w = Math.max(w, mc.font.width(c));
        }
        int x = (g.guiWidth() - w) / 2;
        int y = g.guiHeight() - 64 - lines.size() * 10;
        g.fill(x - 4, y - 3, x + w + 4, y + lines.size() * 10 + 1, 0x8C1A0E0A);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(mc.font, lines.get(i), x, y + i * 10, i == 0 ? 0xFFF2B27A : 0xFFE0D6CC, false);
        }
    }
}
