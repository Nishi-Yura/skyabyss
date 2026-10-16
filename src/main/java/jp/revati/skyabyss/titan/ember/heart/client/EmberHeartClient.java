package jp.revati.skyabyss.titan.ember.heart.client;

import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.PhasedBossBar;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 余燼の心臓のクライアント側: 描画の登録、段階ごとのボスバー、引き寄せ (Nudge) を人の速さに足す、灰の帳と凍えの画面
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EmberHeartClient {
    // 段階 1 熔岩の殻: 熾の橙 / 段階 2 熔け落ちる殻: 白熱の黄 / 段階 3 余燼: 灰の白とくすんだ赤 (13 の 5.3節。案は docs/review/ember_roughs/heart/bars.png)。
    // 帯の色と、tools/gen_ember_heart_bar.py の枠の色をそろえる
    public static final PhasedBossBar.Style BAR = new PhasedBossBar.Style("ember_heart",
            new int[] {0xF67E28, 0xFFD660, 0x962A20},
            new int[] {0xFFC060, 0xFFF0B0, 0xD86A58},
            new int[] {0xFFA050, 0xFFE080, 0xE8E0D6},
            "gui.skyabyss.boss.ember_heart.phase.", "gui.skyabyss.boss.ember_heart.roaring", 0xFFB070);

    // 灰の帳の残り tick と、凍えているか (サーバーからのパケット)
    private static int veilLeft;
    private static int veilTotal;
    private static boolean chilled;

    private EmberHeartClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EmberHeartSetup.EMBER_HEART.get(), EmberHeartRenderer::new);
        event.registerEntityRenderer(EmberHeartSetup.ASH_PELLET.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.4f, true));
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

    public static void veil(int ticks) {
        veilLeft = ticks;
        veilTotal = Math.max(1, ticks);
    }

    public static void chill(boolean on) {
        chilled = on;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (veilLeft > 0) {
            veilLeft--;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            veilLeft = 0;
            chilled = false;
        }
    }

    // 灰の帳: 画面を灰でおおう (見通しが悪い。心臓の光と残り火だけが見える)。凍え: 画面の縁を霜の青灰で縁取る
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        var g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (veilLeft > 0) {
            // 入りと明けはなだらかに (最初と最後の 10 tick)
            float in = Math.min(1f, (veilTotal - veilLeft) / 10f);
            float out = Math.min(1f, veilLeft / 10f);
            int alpha = (int) (0x9A * Math.min(in, out));
            g.fillGradient(0, 0, w, h, (alpha << 24) | 0x3A3632, ((int) (alpha * 1.1f) << 24) | 0x1E1B19);
        }
        if (chilled) {
            int b = Math.max(8, Math.min(w, h) / 10);
            int edge = 0x70A8C0D0;
            int clear = 0x00A8C0D0;
            g.fillGradient(0, 0, w, b, edge, clear);
            g.fillGradient(0, h - b, w, h, clear, edge);
            g.fill(0, 0, b / 3, h, 0x38A8C0D0);
            g.fill(w - b / 3, 0, w, h, 0x38A8C0D0);
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
        List<EmberHeart> hearts = mc.level.getEntitiesOfClass(EmberHeart.class, mc.player.getBoundingBox().inflate(160),
                h -> h.isAlive() && h.getDisplayName().getString().equals(name));
        EmberHeart heart = hearts.stream().min(Comparator.comparingDouble(h -> h.distanceToSqr(mc.player))).orElse(null);
        if (heart == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, BAR, heart.getDisplayName(), heart.getPhase(), heart.isRoaring(), boss, event.getX(), event.getY());
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
