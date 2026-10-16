package jp.revati.skyabyss.titan.travel.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 空の海図: 巨神の頭の乗り場で、行き先を選ぶ画面。
// 左は自分を中心にした丸い海図 (北が上。距離は対数で縮める。輪は 250・1000・4000 ブロック)、右は行き先の一覧と「出発」「降りる」。
// 骨の枠と石板は共鳴の炉の画面、青緑の印は伝承の書と同じ色。テクスチャは 2 倍密度 (tools/gen_titan_travel_assets.py)
public class TravelChartScreen extends Screen {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/travel/chart.png");
    private static final int TEX_W = 1024;
    private static final int TEX_H = 512;

    record Sprite(int u, int v, int w, int h) {
    }

    // 画素での位置 (画面には半分の大きさで描く)。値は gen_titan_travel_assets.py と同じ
    private static final Sprite PANEL = new Sprite(0, 0, 640, 380);
    private static final Sprite ROW = new Sprite(0, 384, 264, 44);
    private static final Sprite ROW_HOVER = new Sprite(272, 384, 264, 44);
    private static final Sprite ROW_SELECTED = new Sprite(544, 384, 264, 44);
    private static final Sprite BUTTON = new Sprite(0, 432, 120, 32);
    private static final Sprite BUTTON_HOVER = new Sprite(128, 432, 120, 32);
    private static final Sprite BUTTON_OFF = new Sprite(256, 432, 120, 32);
    private static final Sprite MARK = new Sprite(400, 432, 16, 16);
    private static final Sprite MARK_LOST = new Sprite(416, 432, 16, 16);
    private static final Sprite MARK_SELECTED = new Sprite(432, 432, 16, 16);
    private static final Sprite MARK_SPAWN = new Sprite(448, 432, 16, 16);
    private static final Sprite CROSS = new Sprite(464, 432, 16, 16);
    private static final Sprite CENTER = new Sprite(480, 432, 24, 24);
    private static final Sprite ARROW_UP = new Sprite(512, 432, 16, 16);
    private static final Sprite ARROW_DOWN = new Sprite(528, 432, 16, 16);

    // 配置 (GUI 単位)
    private static final int W = 320;
    private static final int H = 190;
    private static final int CHART_X = 90;
    private static final int CHART_Y = 98;
    private static final int CHART_R = 70;
    private static final double CHART_FAR = 6000;
    private static final int LIST_X = 176;
    private static final int LIST_Y = 44;
    private static final int ROW_W = 132;
    private static final int ROW_H = 22;
    private static final int ROWS = 5;
    private static final int BTN_Y = 162;
    private static final int BTN_W = 60;
    private static final int BTN_H = 16;
    private static final int DEPART_X = 178;
    private static final int LEAVE_X = 246;

    private static final int TEXT = 0xFFE2D9C2;
    private static final int DIM = 0xFF8E8A7C;
    private static final int TEAL = 0xFF7FD6D0;
    private static final int PALE = 0xFF9FE8FF;
    private static final int LOST = 0xFF6A6862;

    private TravelNetwork.Chart chart;
    private int left;
    private int top;
    private int scroll;
    @Nullable
    private String selected;

    public TravelChartScreen(TravelNetwork.Chart chart) {
        super(Component.translatable(net.minecraft.locale.Language.getInstance().has("gui.skyabyss.travel.title." + chart.titan())
                ? "gui.skyabyss.travel.title." + chart.titan() : "gui.skyabyss.travel.title"));
        this.chart = chart;
        pickDefault();
    }

    void update(TravelNetwork.Chart chart) {
        this.chart = chart;
        if (selected != null && chart.dests().stream().noneMatch(d -> d.id().equals(selected))) {
            selected = null;
        }
        pickDefault();
    }

    private void pickDefault() {
        if (!chart.chosen().isEmpty()) {
            selected = chart.chosen();
        }
        if (selected == null) {
            // いちばん遠い、行ける行き先から選んでおく (近すぎる所は選べない)
            selected = chart.dests().stream().filter(this::reachable)
                    .max((a, b) -> Double.compare(distance(a), distance(b))).map(TravelNetwork.Dest::id).orElse(null);
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double distance(TravelNetwork.Dest d) {
        return Math.hypot(d.x() + 0.5 - chart.originX(), d.z() + 0.5 - chart.originZ());
    }

    private boolean reachable(TravelNetwork.Dest d) {
        return d.valid() && distance(d) >= 64;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        blit(g, left, top, PANEL);
        g.drawString(font, title, left + LIST_X + 2, top + 14, PALE, false);
        Component sub = chart.leader() ? Component.translatable("gui.skyabyss.travel.choose")
                : Component.translatable("gui.skyabyss.travel.leader_chooses", chart.leaderName());
        g.drawString(font, sub, left + LIST_X + 2, top + 27, DIM, false);
        renderChart(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        renderButtons(g, mouseX, mouseY);
    }

    // 背景は描かない (乗り場からの景色が見えるように、少しだけ暗くする)
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x60101418, 0x80101418);
    }

    // ---- 丸い海図
    private int[] chartPoint(TravelNetwork.Dest d) {
        double dx = d.x() + 0.5 - chart.originX();
        double dz = d.z() + 0.5 - chart.originZ();
        double dist = Math.hypot(dx, dz);
        double r = Math.min(CHART_R - 6, CHART_R * Math.log1p(dist / 100.0) / Math.log1p(CHART_FAR / 100.0));
        double a = Math.atan2(dz, dx);
        return new int[]{left + CHART_X + (int) Math.round(Math.cos(a) * r), top + CHART_Y + (int) Math.round(Math.sin(a) * r)};
    }

    private void renderChart(GuiGraphics g, int mouseX, int mouseY) {
        // 方角 (北が上)
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.n"), left + CHART_X, top + CHART_Y - CHART_R - 10, TEAL);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.s"), left + CHART_X, top + CHART_Y + CHART_R + 2, TEAL);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.w"), left + CHART_X - CHART_R - 7, top + CHART_Y - 4, TEAL);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.e"), left + CHART_X + CHART_R + 7, top + CHART_Y - 4, TEAL);
        blit(g, left + CHART_X - 6, top + CHART_Y - 6, CENTER);
        TravelNetwork.Dest hovered = null;
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        for (TravelNetwork.Dest d : chart.dests()) {
            int[] p = chartPoint(d);
            boolean sel = d.id().equals(selected);
            Sprite s = !d.valid() ? MARK_LOST : sel ? MARK_SELECTED : d.kind() == 0 ? MARK_SPAWN : MARK;
            if (sel && (time / 8) % 2 == 0) {
                // 選んだ行き先へ、点線を引く
                dotted(g, left + CHART_X, top + CHART_Y, p[0], p[1]);
            }
            blit(g, p[0] - 4, p[1] - 4, s);
            if (Math.abs(mouseX - p[0]) <= 4 && Math.abs(mouseY - p[1]) <= 4) {
                hovered = d;
            }
        }
        if (hovered != null) {
            g.renderTooltip(font, List.of(name(hovered).getVisualOrderText(), detail(hovered).getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void dotted(GuiGraphics g, int x0, int y0, int x1, int y1) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        for (double t = 8; t < len - 4; t += 4) {
            int x = x0 + (int) Math.round((x1 - x0) * t / len);
            int y = y0 + (int) Math.round((y1 - y0) * t / len);
            g.fill(x, y, x + 1, y + 1, 0xAA9FE8FF);
        }
    }

    // ---- 一覧
    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<TravelNetwork.Dest> dests = chart.dests();
        scroll = Mth.clamp(scroll, 0, Math.max(0, dests.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < dests.size(); i++) {
            TravelNetwork.Dest d = dests.get(scroll + i);
            int x = left + LIST_X;
            int y = top + LIST_Y + i * ROW_H;
            boolean hover = d.valid() && inside(mouseX, mouseY, x, y, ROW_W, ROW_H - 1);
            boolean sel = d.id().equals(selected);
            blit(g, x, y, sel ? ROW_SELECTED : hover ? ROW_HOVER : ROW);
            // 失われた碑は灰色 (選べない。サーバーは海図を送ったときに記録から外している)
            int color = !d.valid() ? LOST : reachable(d) ? TEXT : DIM;
            g.drawString(font, font.plainSubstrByWidth(name(d).getString(), ROW_W - 18), x + 5, y + 3, color, false);
            g.drawString(font, detail(d), x + 5, y + 12, d.valid() ? DIM : LOST, false);
            if (d.kind() == 1 && d.valid() && chart.leader() && hover) {
                // 碑を忘れる
                blit(g, x + ROW_W - 11, y + 3, CROSS);
            }
        }
        if (scroll > 0) {
            blit(g, left + LIST_X + ROW_W - 9, top + LIST_Y - 9, ARROW_UP);
        }
        if (scroll + ROWS < dests.size()) {
            blit(g, left + LIST_X + ROW_W - 9, top + LIST_Y + ROWS * ROW_H, ARROW_DOWN);
        }
        if (dests.size() <= 1) {
            g.drawString(font, Component.translatable("gui.skyabyss.travel.no_waymarks"), left + LIST_X + 4, top + LIST_Y + ROW_H + 4, DIM, false);
            g.drawString(font, Component.translatable("gui.skyabyss.travel.no_waymarks2"), left + LIST_X + 4, top + LIST_Y + ROW_H + 14, DIM, false);
        }
    }

    private Component name(TravelNetwork.Dest d) {
        return Voyages.destName(d);
    }

    private Component detail(TravelNetwork.Dest d) {
        if (!d.valid()) {
            return Component.translatable("gui.skyabyss.travel.lost");
        }
        double dist = distance(d);
        if (dist < 64) {
            return Component.translatable("gui.skyabyss.travel.here");
        }
        double angle = Math.toDegrees(Math.atan2(d.x() + 0.5 - chart.originX(), -(d.z() + 0.5 - chart.originZ())));
        int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
        String[] dirs = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return Component.translatable("gui.skyabyss.travel.detail", String.format("%,d", (int) dist),
                Component.translatable("gui.skyabyss.travel.dir." + dirs[sector]), d.seconds());
    }

    // ---- つまみ
    private void renderButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean canDepart = chart.leader() && chart.chosen().isEmpty() && selectedDest() != null && reachable(selectedDest());
        button(g, DEPART_X, Component.translatable(chart.chosen().isEmpty() ? "gui.skyabyss.travel.depart" : "gui.skyabyss.travel.departing"),
                canDepart, mouseX, mouseY);
        button(g, LEAVE_X, Component.translatable("gui.skyabyss.travel.disembark"), chart.chosen().isEmpty() || !chart.leader(), mouseX, mouseY);
    }

    private void button(GuiGraphics g, int bx, Component label, boolean active, int mouseX, int mouseY) {
        int x = left + bx;
        int y = top + BTN_Y;
        boolean hover = active && inside(mouseX, mouseY, x, y, BTN_W, BTN_H);
        blit(g, x, y, !active ? BUTTON_OFF : hover ? BUTTON_HOVER : BUTTON);
        g.drawCenteredString(font, label, x + BTN_W / 2, y + 4, active ? (hover ? PALE : TEXT) : DIM);
    }

    @Nullable
    private TravelNetwork.Dest selectedDest() {
        for (TravelNetwork.Dest d : chart.dests()) {
            if (d.id().equals(selected)) {
                return d;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mouseX = (int) mx;
        int mouseY = (int) my;
        List<TravelNetwork.Dest> dests = chart.dests();
        for (int i = 0; i < ROWS && scroll + i < dests.size(); i++) {
            TravelNetwork.Dest d = dests.get(scroll + i);
            int x = left + LIST_X;
            int y = top + LIST_Y + i * ROW_H;
            if (inside(mouseX, mouseY, x, y, ROW_W, ROW_H - 1)) {
                if (!d.valid()) {
                    return true;
                }
                if (d.kind() == 1 && chart.leader() && inside(mouseX, mouseY, x + ROW_W - 12, y + 2, 10, 10)) {
                    PacketDistributor.sendToServer(new TravelNetwork.Forget(d.id()));
                    click(0.8f);
                    return true;
                }
                selected = d.id();
                click(1.2f);
                return true;
            }
        }
        for (TravelNetwork.Dest d : dests) {
            int[] p = chartPoint(d);
            if (d.valid() && Math.abs(mouseX - p[0]) <= 4 && Math.abs(mouseY - p[1]) <= 4) {
                selected = d.id();
                click(1.2f);
                return true;
            }
        }
        TravelNetwork.Dest sel = selectedDest();
        if (inside(mouseX, mouseY, left + DEPART_X, top + BTN_Y, BTN_W, BTN_H) && chart.leader() && chart.chosen().isEmpty()
                && sel != null && reachable(sel)) {
            PacketDistributor.sendToServer(new TravelNetwork.Choose(sel.id()));
            click(0.7f);
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, left + LEAVE_X, top + BTN_Y, BTN_W, BTN_H) && (chart.chosen().isEmpty() || !chart.leader())) {
            PacketDistributor.sendToServer(new TravelNetwork.Disembark());
            click(0.9f);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, chart.dests().size() - ROWS));
        return true;
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static void blit(GuiGraphics g, int x, int y, Sprite s) {
        g.blit(TEX, x, y, s.w() / 2, s.h() / 2, s.u(), s.v(), s.w(), s.h(), TEX_W, TEX_H);
    }
}
