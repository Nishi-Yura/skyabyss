package jp.revati.skyabyss.titan.storm.raiju.client;

import java.util.Comparator;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.raiju.Raiju;
import jp.revati.skyabyss.titan.storm.raiju.RaijuSetup;
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

// 雷獣のクライアント側: 描画の登録と、10 段のボスバー (段階なし、青白い帯。12 の 5.2節)。
// 絵は tools/gen_raiju_bar.py (Pillow): 顔の印 raiju_icon.png (16x16)、枠 raiju_frame.png (182x16。溝は x 3〜177、y 3〜8)、
// 帯の上に重ねる 10 段の刻み raiju_notches.png。名前の横に、痺れている間は「痺れ」(帯も黄色)、怒ったあとは「怒り」
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class RaijuClient {
    public static final int WIDTH = 182;
    public static final int FRAME_HEIGHT = 16;
    public static final int ICON = 16;
    public static final int EXTRA_HEIGHT = 12;
    private static final int TRACK_X = 3;
    private static final int TRACK_W = 175;
    private static final int TRACK_Y = 3;
    private static final int TRACK_H = 6;
    public static final int FILL = 0x9CCFFF;
    public static final int FILL_TOP = 0xE6F4FF;
    public static final int FILL_STUNNED = 0xF2E27A;
    private static final ResourceLocation ICON_TEX = tex("raiju_icon");
    private static final ResourceLocation FRAME_TEX = tex("raiju_frame");
    private static final ResourceLocation NOTCH_TEX = tex("raiju_notches");

    private RaijuClient() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + name + ".png");
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RaijuSetup.RAIJU.get(), RaijuRenderer::new);
    }

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        Raiju raiju = mc.level.getEntitiesOfClass(Raiju.class, mc.player.getBoundingBox().inflate(64),
                        r -> r.isAlive() && r.getDisplayName().getString().equals(name))
                .stream().min(Comparator.comparingDouble(r -> r.distanceToSqr(mc.player))).orElse(null);
        if (raiju == null) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, raiju, boss, event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        event.setIncrement(event.getIncrement() + EXTRA_HEIGHT);
    }

    static void draw(GuiGraphics g, Minecraft mc, Raiju raiju, BossEvent boss, int x, int y) {
        g.blit(ICON_TEX, x, y, 0, 0, ICON, ICON, ICON, ICON);
        Component name = raiju.getDisplayName();
        g.drawString(mc.font, name, x + ICON + 4, y + 4, 0xFFFFFF, true);
        Component label = raiju.isStunned() ? Component.translatable("gui.skyabyss.boss.raiju.stunned")
                : raiju.isAngry() ? Component.translatable("gui.skyabyss.boss.raiju.angry") : null;
        if (label != null) {
            g.drawString(mc.font, label, x + ICON + 4 + mc.font.width(name) + 6, y + 4, raiju.isStunned() ? 0xFFE66A : 0xFF9AB4, true);
        }
        String percent = Math.round(boss.getProgress() * 100) + "%";
        g.drawString(mc.font, percent, x + WIDTH - mc.font.width(percent), y + 4, 0xFFFFFF, true);
        int barY = y + ICON + 2;
        g.blit(FRAME_TEX, x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, boss.getProgress()));
        int fill = Math.round(TRACK_W * progress);
        if (fill > 0) {
            int left = x + TRACK_X;
            int top = barY + TRACK_Y;
            int color = raiju.isStunned() ? FILL_STUNNED : FILL;
            g.fill(left, top, left + fill, top + TRACK_H, 0xFF000000 | color);
            g.fill(left, top, left + fill, top + 1, 0xFF000000 | FILL_TOP);
        }
        g.blit(NOTCH_TEX, x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
    }
}
