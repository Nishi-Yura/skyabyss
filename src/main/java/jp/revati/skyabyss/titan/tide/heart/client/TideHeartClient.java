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

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        List<TideHeart> hearts = mc.level.getEntitiesOfClass(TideHeart.class, mc.player.getBoundingBox().inflate(160),
                h -> h.isAlive() && h.getDisplayName().getString().equals(name));
        TideHeart heart = hearts.stream().min(Comparator.comparingDouble(h -> h.distanceToSqr(mc.player))).orElse(null);
        if (heart == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, BAR, heart.getDisplayName(), heart.getPhase(), heart.isRoaring(), boss, event.getX(), event.getY());
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
