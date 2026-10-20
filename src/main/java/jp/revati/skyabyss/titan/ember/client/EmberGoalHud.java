package jp.revati.skyabyss.titan.ember.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.BackGaugeHud;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 余燼の背の目的の板 (13 の 5.7。titan.ember.back.trial.EmberGuide が人ごとに決めて送る)。熱の目盛りの下に、見出し (部屋の名前)・進みの点・今の一行。
// 枠・幅・文の切り方は共通の BackGaugeHud。文は幅に合わせて折り返す (最大 5 行)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EmberGoalHud {
    private static EmberNetwork.Goal goal = new EmberNetwork.Goal("", 0, 0, 0, 0);
    private static final int GOAL_WIDTH = 150;

    private static final BackGaugeHud.Gauge GOAL = new BackGaugeHud.Gauge() {
        @Override
        public boolean shown(Minecraft mc) {
            return EmberGoalHud.shown(mc);
        }

        @Override
        public int height() {
            Minecraft mc = Minecraft.getInstance();
            return BackGaugeHud.HEIGHT + 2 + goalHeight(mc, goalWidth(mc.getWindow().getGuiScaledWidth()));
        }
    };

    static {
        BackGaugeHud.register(GOAL);
    }

    private EmberGoalHud() {
    }

    public static void receive(EmberNetwork.Goal g) {
        Minecraft.getInstance().execute(() -> goal = g);
    }

    static boolean shown(Minecraft mc) {
        return !goal.id().isEmpty() && mc.level != null && mc.player != null && mc.level.dimension() == EmberIds.BACK && !mc.options.hideGui;
    }

    private static int goalWidth(int guiWidth) {
        return Mth.clamp(GOAL_WIDTH, 112, Math.max(112, guiWidth / 2 - 91 - 4 - 6));
    }

    private static Component body() {
        return Component.translatable("gui.skyabyss.ember_goal." + goal.id(), goal.done(), goal.total(), goal.a(), goal.b());
    }

    private static int goalHeight(Minecraft mc, int w) {
        if (goal.id().isEmpty()) {
            return 0;
        }
        int lines = Math.min(5, mc.font.split(body(), w - 10).size());
        return 6 + 11 + lines * 10;
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc) || !jp.revati.skyabyss.client.titan.BackGaugeHud.placed()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = goalWidth(g.guiWidth());
        int h = goalHeight(mc, w);
        int x = BackGaugeHud.left(g, w);
        int y = BackGaugeHud.top(mc) + BackGaugeHud.HEIGHT + 2;
        boolean done = goal.id().endsWith(".done");
        BackGaugeHud.frame(g, x, y, w, h, 0xB0181010, done ? 0xFFF0C070 : 0xFF8A6A58);
        String room = goal.id().substring(0, goal.id().indexOf('.'));
        int dots = goal.total();
        int dotsW = dots > 0 ? dots * 7 : 0;
        BackGaugeHud.text(g, mc.font, Component.translatable("gui.skyabyss.ember_goal.title." + room), x + 5, y + 4, w - 10 - dotsW, 0xFFFFB060);
        for (int i = 0; i < dots; i++) {
            int dx = x + w - 5 - (dots - i) * 7 + 2;
            g.fill(dx, y + 5, dx + 5, y + 10, i < goal.done() ? 0xFFFFA048 : 0xFF3A2A24);
        }
        int ty = y + 4 + 11;
        var lines = mc.font.split(body(), w - 10);
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            g.drawString(mc.font, lines.get(i), x + 5, ty + i * 10, 0xFFF4E8DC, true);
        }
    }
}
