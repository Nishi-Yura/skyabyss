package jp.revati.skyabyss.titan.storm.back.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.BackGaugeHud;
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
// その下に目的の板 (25 の 3章。潮殻の TideGaugeHud と同じ型): 今いる試練の「いま何をするか」と進みの点。
// パーティクルの設定に左右されない。嵐のあいだは縁が明るい青白に光る (予告の印)。板の置き方・枠・文の切り方は共通の BackGaugeHud
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormGaugeHud {
    private static StormBackNetwork.Gauge gauge;
    private static int ticksLeft;
    private static int age = 1000;

    private static final BackGaugeHud.Gauge GAUGE = StormGaugeHud::shown;

    private static final int GOAL_WIDTH = 150;
    private static final BackGaugeHud.Gauge GOAL = new BackGaugeHud.Gauge() {
        @Override
        public boolean shown(Minecraft mc) {
            return StormGaugeHud.shown(mc) && !gauge.goal().isEmpty();
        }

        @Override
        public int height() {
            Minecraft mc = Minecraft.getInstance();
            return BackGaugeHud.HEIGHT + 2 + goalHeight(mc, goalWidth(mc.getWindow().getGuiScaledWidth()));
        }
    };

    static {
        BackGaugeHud.register(GAUGE);
        BackGaugeHud.register(GOAL);
    }

    private StormGaugeHud() {
    }

    private static int goalWidth(int guiWidth) {
        return Mth.clamp(GOAL_WIDTH, 112, Math.max(112, guiWidth / 2 - 91 - 4 - 6));
    }

    private static Component goalBody() {
        return Component.translatable("gui.skyabyss.storm_goal." + gauge.goal(), gauge.done(), gauge.total(), gauge.a(), gauge.b(), gauge.c());
    }

    // 板の高さ: 見出しの行 + 本文 (幅に合わせて折り返し。最大 5 行)
    static int goalHeight(Minecraft mc, int w) {
        if (gauge == null || gauge.goal().isEmpty()) {
            return 0;
        }
        int lines = Math.min(5, mc.font.split(goalBody(), w - 10).size());
        return 6 + 11 + lines * 10;
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

    // 板の下端 (仲間の表示がこれより下に来るように)。ほかの背中の板もまとめて BackGaugeHud.bottom が見る
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
        String phase = switch (gauge.phase()) {
            case 0 -> "calm";
            case 1 -> "rising";
            case 2 -> "storm";
            default -> "falling";
        };
        Component name = Component.translatable("gui.skyabyss.storm_gauge." + phase);
        Component time = ticksLeft < 0 ? Component.translatable("gui.skyabyss.storm_gauge.held")
                : gauge.locked() ? Component.translatable("gui.skyabyss.storm_gauge.locked", (ticksLeft + 19) / 20)
                        : Component.translatable("gui.skyabyss.storm_gauge.left", (ticksLeft + 19) / 20);
        float s = gauge.strength();
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        boolean storm = gauge.phase() == 2 || gauge.phase() == 1;
        float pulse = storm ? 0.5f + 0.5f * Mth.sin(t * 0.35f) : 0;
        int border = storm ? (0xFF << 24) | ((int) (150 + 100 * pulse) << 16) | ((int) (190 + 60 * pulse) << 8) | 0xFF : 0xFF8A98B0;
        int text = Math.max(mc.font.width(name), mc.font.width(time));
        int w = BackGaugeHud.width(g, 34 + text + 10, 112);
        int h = BackGaugeHud.HEIGHT;
        int x = BackGaugeHud.left(g, w);
        int y = BackGaugeHud.top(mc);
        BackGaugeHud.frame(g, x, y, w, h, 0xB0161A26, border);
        vane(g, x + 4, y + 5, s, t);
        int room = w - 34 - 10;
        BackGaugeHud.text(g, mc.font, name, x + 34, y + 5, room, storm ? 0xFFE8F0FF : 0xFFDCE2EC);
        BackGaugeHud.text(g, mc.font, time, x + 34, y + 16, room, 0xFFA8B4C8);
        for (int i = 0; i < 3; i++) {
            g.fill(x + w - 8, y + 6 + i * 7, x + w - 4, y + 10 + i * 7, i < gauge.stones() ? 0xFFF2D9A0 : 0xFF404848);
        }
        if (!gauge.goal().isEmpty()) {
            drawGoal(g, mc, y + h + 2);
        }
    }

    // 目的の板: 見出し (試練の名前) と進みの点、本文 (いま何をするか)
    private static void drawGoal(GuiGraphics g, Minecraft mc, int y) {
        int w = goalWidth(g.guiWidth());
        int h = goalHeight(mc, w);
        int x = BackGaugeHud.left(g, w);
        boolean done = gauge.goal().endsWith(".done") || gauge.goal().endsWith(".sleep");
        BackGaugeHud.frame(g, x, y, w, h, 0xB0161A26, done ? 0xFFF2D9A0 : 0xFF8A98B0);
        String trial = gauge.goal().substring(0, gauge.goal().indexOf('.'));
        Component title = Component.translatable("gui.skyabyss.storm_goal.title." + trial);
        int dots = gauge.total();
        int dotsW = dots > 0 ? dots * 7 : 0;
        BackGaugeHud.text(g, mc.font, title, x + 5, y + 4, w - 10 - dotsW, 0xFFF2D9A0);
        for (int i = 0; i < dots; i++) {
            int dx = x + w - 5 - (dots - i) * 7 + 2;
            g.fill(dx, y + 5, dx + 5, y + 10, i < gauge.done() ? 0xFFF2D060 : 0xFF404848);
        }
        int ty = y + 4 + 11;
        var lines = mc.font.split(goalBody(), w - 10);
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            g.drawString(mc.font, lines.get(i), x + 5, ty + i * 10, 0xFFE8F0FF, true);
        }
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
