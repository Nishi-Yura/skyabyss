package jp.revati.skyabyss.titan.tide.back.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.BackGaugeHud;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 潮見の目盛り (08 の 3.4節): 背中にいる間、画面の右上に小さな貝の目盛り。今の潮位と、次に変わるまでの秒数。
// パーティクルの設定に左右されない (岩鯨の必須4 の反省)。招き潮の予告と満ちている間は、縁が赤く光る。
// 板の置き場所・幅・枠・文の切り方は共通の BackGaugeHud (13 の 10.2節)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TideGaugeHud {
    private static TideBackNetwork.Gauge gauge;
    private static int ticksLeft;
    private static int age = 1000;

    private static final BackGaugeHud.Gauge GAUGE = TideGaugeHud::shown;

    static {
        BackGaugeHud.register(GAUGE);
    }

    private TideGaugeHud() {
    }

    private static boolean shown(Minecraft mc) {
        return gauge != null && mc.level != null && mc.level.dimension() == TideTravel.TIDE_BACK && !mc.options.hideGui && age <= 100;
    }

    public static void receive(TideBackNetwork.Gauge g) {
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

    // 板の上端 (効き目の絵の下へずらす)
    static int top(Minecraft mc) {
        return BackGaugeHud.top(mc);
    }

    // 板の下端 (仲間の表示がこれより下に来るように)。今は余燼の熱など、ほかの背中の板もまとめて BackGaugeHud.bottom が見る
    public static int bottom() {
        return BackGaugeHud.bottom();
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        boolean alert = gauge.beckon();
        // 文: 段階と、あと何秒。板の幅は文の長さに合わせる (日本語・英語とも枠に収める)
        String phase = switch (gauge.phase()) {
            case 0 -> "low";
            case 1 -> "rising";
            case 2 -> "high";
            default -> "falling";
        };
        Component name = Component.translatable(alert ? "gui.skyabyss.tide_gauge.beckon" : "gui.skyabyss.tide_gauge." + phase);
        Component time = ticksLeft < 0 ? Component.translatable("gui.skyabyss.tide_gauge.held")
                : Component.translatable("gui.skyabyss.tide_gauge.left", (ticksLeft + 19) / 20);
        int text = Math.max(mc.font.width(name), mc.font.width(time));
        // 左の飾り (貝の溝と水位の柱) 34、文、右の石の点 10。ボスバー (中央の幅 182) との間を 4 あけた幅まで
        int w = BackGaugeHud.width(g, 34 + text + 10, 112);
        int h = BackGaugeHud.HEIGHT;
        int x = BackGaugeHud.left(g, w);
        int y = top(mc);
        float pulse = alert ? 0.5f + 0.5f * Mth.sin((mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true)) * 0.4f) : 0;
        int border = alert ? (0xFF << 24) | ((int) (180 + 75 * pulse) << 16) | 0x3020 : 0xFF5FB8B0;
        // 貝の形の枠 (角を落とした板)
        BackGaugeHud.frame(g, x, y, w, h, 0xB0101C22, border);
        // 貝の溝 (左の小さな扇)
        int sx = x + 5;
        int sy = y + 5;
        for (int i = 0; i < 5; i++) {
            g.fill(sx + i * 3, sy + 2 - (i % 2), sx + i * 3 + 2, sy + 20, 0xFFD8CBB0);
        }
        // 水位の柱 (三つの目盛り)
        int bx = x + 23;
        int by = y + 5;
        int bh = 20;
        g.fill(bx, by, bx + 7, by + bh, 0xFF1B2A30);
        int fill = Math.round(gauge.fullness() * bh);
        int water = alert ? 0xFFE05A3C : 0xFF3FB6C8;
        g.fill(bx, by + bh - fill, bx + 7, by + bh, water);
        for (int i = 1; i < 3; i++) {
            g.fill(bx - 1, by + bh * i / 3, bx + 8, by + bh * i / 3 + 1, 0xFF90A8A8);
        }
        // 狭い画面で幅が足りなければ、はみ出す分を切る (枠の外へは出さない)
        int room = w - 34 - 10;
        BackGaugeHud.text(g, mc.font, name, bx + 11, by, room, alert ? 0xFFFF8060 : 0xFFE6F2EE);
        BackGaugeHud.text(g, mc.font, time, bx + 11, by + 11, room, 0xFFB0C8C4);
        // 鳴った石 (小さな三つの点)
        for (int i = 0; i < 3; i++) {
            int c = i < gauge.stones() ? 0xFFF0E8B0 : 0xFF404848;
            g.fill(x + w - 8, y + 6 + i * 7, x + w - 4, y + 10 + i * 7, c);
        }
    }
}
