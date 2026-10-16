package jp.revati.skyabyss.titan.storm.back.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.client.BackGaugeHud;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 風見の目盛り (12 の 3.4節): 背中にいる間、画面の右上に小さな風見の板。矢の向きと揺れで今の拍、次に変わるまでの秒、鳴った石の数。
// パーティクルの設定に左右されない。嵐のあいだは縁が明るい青白に光る (予告の印)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormGaugeHud {
    private static StormBackNetwork.Gauge gauge;
    private static int ticksLeft;
    private static int age = 1000;

    private StormGaugeHud() {
    }

    public static void receive(StormBackNetwork.Gauge g) {
        gauge = g;
        ticksLeft = g.ticksLeft();
        age = 0;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        age++;
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    static boolean shown(Minecraft mc) {
        return gauge != null && mc.level != null && mc.level.dimension() == StormTravel.STORM_BACK && !mc.options.hideGui && age <= 100;
    }

    // 板の下端 (仲間の表示がこれより下に来るように)
    public static int bottom() {
        Minecraft mc = Minecraft.getInstance();
        return shown(mc) ? BackGaugeHud.top(mc) + BackGaugeHud.HEIGHT : 0;
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        String phase = switch (gauge.phase()) {
            case 0 -> "calm";
            case 1 -> "rising";
            case 2 -> "storm";
            default -> "falling";
        };
        Component name = Component.translatable("gui.skyabyss.storm_gauge." + phase);
        Component time = ticksLeft < 0 ? Component.translatable("gui.skyabyss.storm_gauge.held")
                : Component.translatable("gui.skyabyss.storm_gauge.left", (ticksLeft + 19) / 20);
        float s = gauge.strength();
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        boolean storm = gauge.phase() == 2 || gauge.phase() == 1;
        float pulse = storm ? 0.5f + 0.5f * Mth.sin(t * 0.35f) : 0;
        int border = storm ? (0xFF << 24) | ((int) (150 + 100 * pulse) << 16) | ((int) (190 + 60 * pulse) << 8) | 0xFF : 0xFF8A98B0;
        BackGaugeHud.draw(g, mc, name, time, gauge.stones(), border, 0xB0161A26, storm ? 0xFFE8F0FF : 0xFFDCE2EC, 0xFFA8B4C8, 0xFFF2D9A0,
                (gg, x, y) -> vane(gg, x, y, s, t));
    }

    // 風見 (柱と、向きが変わる矢)。凪は矢がゆっくり南を指して静か、嵐は北へ振れて大きく揺れる
    private static void vane(GuiGraphics g, int x, int y, float strength, float t) {
        int cx = x + 12;
        int cy = y + 10;
        // 柱と台
        g.fill(cx - 1, cy, cx + 1, y + 20, 0xFFB87333);
        g.fill(cx - 4, y + 19, cx + 4, y + 20, 0xFFB87333);
        // 矢の角度: 凪は 90 度 (横) で少し揺れ、嵐は大きく揺れる
        double sway = Math.sin(t * (0.05 + 0.25 * strength)) * (8 + 40 * strength);
        double a = Math.toRadians(sway + 180 * strength);
        int len = 9;
        for (int i = -len; i <= len; i++) {
            int px = cx + (int) Math.round(Math.cos(a) * i);
            int py = cy + (int) Math.round(Math.sin(a) * i * 0.45);
            int c = i > len - 3 ? 0xFFF2D9A0 : 0xFFDCE2EC;
            g.fill(px, py - (i > len - 3 ? 1 : 0), px + 1, py + 1 + (i > len - 3 ? 1 : 0), c);
        }
        // 尾の羽 (矢の反対側)
        int tx = cx - (int) Math.round(Math.cos(a) * len);
        int ty = cy - (int) Math.round(Math.sin(a) * len * 0.45);
        g.fill(tx - 1, ty - 2, tx + 2, ty + 3, 0xFF8A98B0);
        // 嵐の印 (右の小さな稲妻、強さで明るく)
        int bolt = (int) (80 + 175 * strength);
        int col = (0xFF << 24) | (bolt << 16) | (bolt << 8) | 0xFF;
        if (strength > 0.05f) {
            g.fill(x + 22, y + 2, x + 24, y + 7, col);
            g.fill(x + 20, y + 6, x + 23, y + 8, col);
            g.fill(x + 20, y + 8, x + 22, y + 13, col);
        }
    }
}
