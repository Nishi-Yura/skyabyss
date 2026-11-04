package jp.revati.skyabyss.client.record;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.record.RecordsHub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 記録 (record/RecordsHub。2026-11-05 の ct_records = one)。見出しの下につまみ 7 (踏破・塔・深み・釣り・早駆け・転生・空見)、
// その下の欄に頁の行 (サーバーが作った文。見出しは金)。行は欄の幅で折り返し、目盛りつきで送る。つまみを押すとその頁をサーバーへ頼む
public class RecordsHubScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    static final Golden.Box TABS = new Golden.Box(LAYOUT.body().x(), LAYOUT.body().y(), LAYOUT.body().w(), Golden.BTN);
    static final Golden.Box LIST = new Golden.Box(LAYOUT.body().x(), TABS.bottom() + Golden.GAP, LAYOUT.body().w(), LAYOUT.body().bottom() - TABS.bottom() - Golden.GAP);
    static final int LINE_H = Golden.ROW;

    private final UiFit fit = new UiFit();
    private final ScrollPanel panel = new ScrollPanel(LINE_H * 3);
    private int tab;
    private List<Component> lines = List.of();
    private final List<FormattedCharSequence> wrapped = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();

    public RecordsHubScreen(int tab, List<Component> lines) {
        super(Component.translatable("gui.skyabyss.records_hub.title"));
        this.tab = tab;
        this.lines = lines;
    }

    // 届いた頁: 開いていれば中身を替え、閉じていれば開く
    public static void receive(RecordsHub.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RecordsHubScreen s) {
            s.show(v.tab(), v.lines());
        } else {
            mc.setScreen(new RecordsHubScreen(v.tab(), v.lines()));
        }
    }

    // 手元から頁を頼む (旅の記録の釦)
    public static void request(int tab) {
        PacketDistributor.sendToServer(new RecordsHub.Request(tab));
    }

    void show(int tab, List<Component> lines) {
        this.tab = tab;
        this.lines = lines;
        panel.setScroll(0);
        rewrap();
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = i != tab;
        }
    }

    private void layout() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        panel.setViewport(LIST.x() + Golden.S5, LIST.y() + 3, LIST.w() - Golden.S5 - 3, LIST.h() - 6);
    }

    private void rewrap() {
        wrapped.clear();
        int w = LIST.w() - Golden.S5 - Golden.S13;
        for (Component c : lines) {
            if (c.getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(c, w));
            }
        }
        panel.setContentHeight(wrapped.size() * LINE_H);
    }

    @Override
    protected void init() {
        layout();
        tabButtons.clear();
        int n = RecordsHub.TABS.length;
        int w = (TABS.w() - (n - 1) * 3) / n;
        for (int i = 0; i < n; i++) {
            int which = i;
            Button b = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.records_hub.tab." + RecordsHub.TABS[i]), x -> request(which))
                    .bounds(TABS.x() + i * (w + 3), TABS.y(), w, Golden.BTN).build());
            b.active = i != tab;
            tabButtons.add(b);
        }
        rewrap();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        layout();
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.frame(g, font, LAYOUT, Component.translatable("gui.skyabyss.records_hub.tab." + RecordsHub.TABS[tab]).copy().append(" · ").append(title));
        Golden.recess(g, LIST);
        panel.begin(g);
        for (int i = 0; i < wrapped.size(); i++) {
            int y = panel.top() + i * LINE_H;
            if (y + LINE_H < panel.y() || y > panel.y() + panel.height()) {
                continue;
            }
            g.drawString(font, wrapped.get(i), LIST.x() + Golden.S8, Golden.textY(y, LINE_H), Golden.TEXT, false);
        }
        panel.end(g);
        panel.drawBar(g, 0xFF17302F, 0x887FD6D0);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        fit.end(g);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        return panel.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double sx, double sy, int button, double sdx, double sdy) {
        return panel.mouseDragged(fit.x(sx), fit.y(sy), button, sdx / fit.scale(), sdy / fit.scale()) || super.mouseDragged(fit.x(sx), fit.y(sy), button, sdx, sdy);
    }

    @Override
    public boolean mouseReleased(double sx, double sy, int button) {
        return panel.mouseReleased(fit.x(sx), fit.y(sy), button) || super.mouseReleased(fit.x(sx), fit.y(sy), button);
    }

    @Override
    public boolean mouseScrolled(double sx, double sy, double dx, double dy) {
        return panel.mouseScrolled(fit.x(sx), fit.y(sy), dy) || super.mouseScrolled(sx, sy, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            return panel.keyPressed(key) || super.keyPressed(key, scan, mods);
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }

    // 撮影用: 見本の頁 (`open jp.revati.skyabyss.client.record.RecordsHubScreen$Sample`)
    public static final class Sample extends RecordsHubScreen {
        public Sample() {
            super(RecordsHub.FISHING, List.of(Component.literal("釣り大会（今週）").withStyle(s -> s.withColor(0xF2D78A)),
                    Component.literal("1. Alice　黄金のマス 52.3 cm"), Component.literal("2. Bob　岩魚 41.0 cm"), Component.empty(),
                    Component.literal("自分の一番: 岩魚 38.2 cm")));
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Setup {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            RecordsHub.clientOpen = RecordsHubScreen::receive;
        }
    }
}
