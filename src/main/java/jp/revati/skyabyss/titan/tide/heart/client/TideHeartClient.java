package jp.revati.skyabyss.titan.tide.heart.client;

import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.PhasedBossBar;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 潮殻の心臓のクライアント側: 描画の登録、段階ごとのボスバー、引き寄せ (Nudge) を人の速さに足す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideHeartClient {
    // 段階 1 閉じた殻: 青緑の帯 / 段階 2 開いた殻: 真珠の白 / 段階 3 月の潮: 月の銀青 (08 の 5.3節)。
    // 14.2節の案 (heart/bars.png) の段階 2 は紅 (0xF68082)。差し替えるときは FILL[1] と tools/gen_tide_heart_bar.py の P2
    public static final PhasedBossBar.Style BAR = new PhasedBossBar.Style("tide_heart",
            new int[] {0x60DCD2, 0xECE4EA, 0xAAC8FF},
            new int[] {0xA8F4EC, 0xFFFFFF, 0xDCE8FF},
            new int[] {0x7FE6DC, 0xF2E8F0, 0xB8D4FF},
            "gui.skyabyss.boss.tide_heart.phase.", "gui.skyabyss.boss.tide_heart.roaring", 0xFF8A8A);

    private TideHeartClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TideHeartSetup.TIDE_HEART.get(), TideHeartRenderer::new);
        event.registerEntityRenderer(TideHeartSetup.TIDE_BUBBLE.get(), TideBubbleRenderer::new);
        event.registerEntityRenderer(TideHeartSetup.TIDE_PEARL.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.6f, true));
    }

    // サーバーからの引き寄せ・跳ね上げ
    public static void nudge(float x, float y, float z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(x, y, z));
        }
    }

    // テスト用: 画面を run/screenshots/<name>.png に写す (名前は英数字と _ だけ)
    public static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String safe = name.replaceAll("[^A-Za-z0-9_]", "_");
        mc.execute(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, safe + ".png", mc.getMainRenderTarget(), msg -> {
        }));
    }

    // 泡の牢に閉じこめられている間は、画面を薄い水色の泡でおおう (中からは泡の箱が見えないため)
    @SubscribeEvent
    static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        boolean inside = !mc.level.getEntitiesOfClass(jp.revati.skyabyss.titan.tide.heart.TideBubble.class,
                mc.player.getBoundingBox().inflate(1.5), b -> b.getBoundingBox().contains(eye)).isEmpty();
        if (!inside) {
            return;
        }
        var g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        g.fillGradient(0, 0, w, h, 0x5578D8F0, 0x6A3A90C8);
        int b = Math.max(6, Math.min(w, h) / 14);
        g.fill(0, 0, w, b, 0x50E8FAFF);
        g.fill(0, h - b, w, h, 0x50E8FAFF);
        g.fill(0, 0, b, h, 0x50E8FAFF);
        g.fill(w - b, 0, w, h, 0x50E8FAFF);
    }

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        TideHeart heart = jp.revati.skyabyss.client.BossOwners.find(mc, boss, TideHeart.class, 160);
        if (heart == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, BAR, heart.getDisplayName(), heart.getPhase(), heart.isRoaring(), boss, event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
