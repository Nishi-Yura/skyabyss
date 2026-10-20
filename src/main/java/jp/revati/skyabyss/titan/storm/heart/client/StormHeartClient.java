package jp.revati.skyabyss.titan.storm.heart.client;

import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.BossAttackCue;
import jp.revati.skyabyss.client.titan.PhasedBossBar;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeartSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 雷翼の心臓のクライアント側: 描画の登録、段階ごとのボスバー、技の予告の印、閃光の暗転と瞬き
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormHeartClient {
    // 段階 1 畳んだ翼: 銅の橙 / 段階 2 開いた翼: 風の青白 / 段階 3 骨だけの翼: 稲妻の白金 (12 の 5.3節)。
    // 14.2節の案 (heart/bars.png) は 青白・金・白金。差し替えるときは FILL と tools/gen_storm_heart_bar.py の STYLE・FILL
    public static final PhasedBossBar.Style BAR = new PhasedBossBar.Style("storm_heart",
            new int[] {0xD98A4E, 0xA8D8FF, 0xF6F0C8},
            new int[] {0xFFC090, 0xE8F6FF, 0xFFFFF0},
            new int[] {0xF0A878, 0xC8E8FF, 0xFFF4C0},
            "gui.skyabyss.boss.thunder_wing_heart.phase.", "gui.skyabyss.boss.thunder_wing_heart.roaring", 0xFFD27A);

    // 閃光: 暗くなる始まりと長さ、瞬きの始まりと強さ
    private static long darkStart = -1000;
    private static int darkTicks;
    private static long flashStart = -1000;
    private static boolean flashHit;

    private StormHeartClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StormHeartSetup.STORM_HEART.get(), StormHeartRenderer::new);
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void warn(String move, int ticks) {
        if (move.isEmpty()) {
            BossAttackCue.clear();
            return;
        }
        BossAttackCue.show(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/storm_heart/warn_" + move + ".png"),
                Component.translatable("gui.skyabyss.storm_heart.warn." + move), ticks);
    }

    public static void flash(int ticks, boolean hit) {
        if (ticks > 0) {
            darkStart = now();
            darkTicks = ticks;
            return;
        }
        darkTicks = 0;
        flashStart = now();
        flashHit = hit;
    }

    public static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        String safe = name.replaceAll("[^A-Za-z0-9_]", "_");
        mc.execute(() -> net.minecraft.client.Screenshot.grab(mc.gameDirectory, safe + ".png", mc.getMainRenderTarget(), msg -> {
        }));
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float t = now() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        drawFlash(g, mc, t);
    }

    // 閃光の暗転 (画面の効果の強さの設定が低い人にも出す。弱めるだけ) と、閃光の瞬き
    private static void drawFlash(GuiGraphics g, Minecraft mc, float t) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float scale = Math.max(0.45f, mc.options.screenEffectScale().get().floatValue());
        if (darkTicks > 0) {
            float k = (t - darkStart) / darkTicks;
            if (k >= 0 && k <= 1.2f) {
                float a = Math.min(1f, k) * 0.62f * scale;
                int alpha = (int) (a * 255) << 24;
                g.fillGradient(0, 0, w, h, alpha | 0x050810, alpha | 0x0A1020);
            } else if (k > 1.2f) {
                darkTicks = 0;
            }
        }
        float f = t - flashStart;
        int len = flashHit ? 16 : 8;
        if (f >= 0 && f < len) {
            float a = (1f - f / len) * (flashHit ? 0.92f : 0.25f) * (flashHit ? 1f : scale);
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0xFFFCEC);
        }
    }

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        List<StormHeart> hearts = mc.level.getEntitiesOfClass(StormHeart.class, mc.player.getBoundingBox().inflate(160),
                h -> h.isAlive() && h.getDisplayName().getString().equals(name));
        StormHeart heart = hearts.stream().min(Comparator.comparingDouble(h -> h.distanceToSqr(mc.player))).orElse(null);
        if (heart == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, BAR, heart.getDisplayName(), heart.getPhase(), heart.isRoaring(), boss, event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
