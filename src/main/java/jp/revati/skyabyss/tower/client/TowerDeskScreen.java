package jp.revati.skyabyss.tower.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.tower.TowerDeskState;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 塔の受付の画面 (あいだの塔。docs/design/54 の 2.2)。2026-11-02 の声「なぜ大規模レイドの入り方がチャット？これまで通り UI 出したらいいんじゃない」。
//   上: 今日の挑戦 (一人一日一回)。中: 隊 (作る・入る・抜ける・隊の人)。隊長には今週の賭け (三つまで) と「塔へ入る」。下: 塔の店・記録の板・閉じる。
//   押した釦は受付の命令 (/skyabyss_tower …) を送る。サーバーが確かめ、受付のそばの人の画面を新しくする (TowerDeskState の open = false)
//   板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 中身は上から今日の挑戦・決まり・隊・隊の人、左の列に作る・入る、右の列に入れる隊、隊長は賭け (二列)。下の段に店・記録・閉じる
public final class TowerDeskScreen extends Screen {
    static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    static final int W = Golden.L_W;
    static final int H = Golden.L_H;
    static final int GOLD = Golden.GOLD;
    static final int TEXT = Golden.TEXT;
    static final int SOFT = Golden.SOFT;
    static final int DENY = Golden.DENY;
    static final int ROW = Golden.ROW;
    // 釦の行 (高さ 13 + あいだ 5)
    static final int STEP = Golden.BTN + Golden.S5;
    static final Golden.Box BODY = LAYOUT.body();
    static final Golden.Box LEFT = BODY.minor(true);
    static final Golden.Box RIGHT = BODY.major(false);
    // 縦の位置 (中身の上から)
    static final int Y_SQUAD = BODY.y() + 2 * ROW + Golden.S5;
    static final int Y_BUTTONS = Y_SQUAD + 2 * ROW + Golden.S5;
    static final int Y_WAGERS = Y_BUTTONS + STEP;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private TowerDeskState state;
    private int left;
    private int top;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> openedIn;

    TowerDeskScreen(TowerDeskState state) {
        super(Component.translatable("gui.skyabyss.tower_desk.title"));
        this.state = state;
        Minecraft mc = Minecraft.getInstance();
        this.openedIn = mc.level == null ? null : mc.level.dimension();
    }

    // 撮影用: 隊長で、隊に三人、賭けを二つ選んだ見本
    public static TowerDeskScreen sample() {
        return new TowerDeskScreen(new TowerDeskState(true, false, "Dev", List.of("Dev", "ミナト", "カイ"), true, 12, List.of(), List.of(),
                List.of("half_heal", "no_revive", "fast_adds", "short_time", "fast_moon", "hard_eyes"), List.of("half_heal", "fast_moon"), 3, 30));
    }

    public static void receive(TowerDeskState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TowerDeskScreen s) {
            s.state = state;
            s.rebuildWidgets();
        } else if (state.open()) {
            mc.setScreen(new TowerDeskScreen(state));
        }
    }

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        left = 0;
        top = 0;
        int y = Y_BUTTONS;
        if (state.leader().isEmpty()) {
            Button create = Button.builder(Component.translatable("gui.skyabyss.tower_desk.create"), b -> cmd("create"))
                    .bounds(LEFT.x(), y, LEFT.w(), Golden.BTN).build();
            create.active = !state.usedToday();
            addRenderableWidget(create);
            for (int i = 0; i < Math.min(4, state.joinLeaders().size()); i++) {
                String leader = state.joinLeaders().get(i);
                Button join = Button.builder(Component.translatable("gui.skyabyss.tower_desk.join", leader, state.joinCounts().get(i), state.max()), b -> cmd("join " + leader))
                        .bounds(RIGHT.x(), y + i * STEP, RIGHT.w(), Golden.BTN).build();
                join.active = !state.usedToday();
                addRenderableWidget(join);
            }
        } else {
            if (state.isLeader()) {
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.start"), b -> cmd("start"))
                        .bounds(LEFT.x(), y, LEFT.w(), Golden.BTN).build());
            }
            Golden.Box quitBox = state.isLeader() ? RIGHT : LEFT;
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.quit"), b -> cmd("quit"))
                    .bounds(quitBox.x(), y, Math.min(quitBox.w(), Golden.S89 + Golden.S34), Golden.BTN).build());
            if (state.isLeader()) {
                // 今週の賭け: 押すと選ぶ / 外す (三つまで)。二列
                int colW = (BODY.w() - Golden.GAP) / 2;
                for (int i = 0; i < state.wagers().size(); i++) {
                    String id = state.wagers().get(i);
                    boolean on = state.chosen().contains(id);
                    Component label = Component.literal(on ? "✔ " : "").append(Component.translatable("message.skyabyss.tower.wager." + id));
                    Button w = Button.builder(label, b -> cmd("wager " + id))
                            .bounds(BODY.x() + (i % 2) * (colW + Golden.GAP), Y_WAGERS + ROW + 3 + (i / 2) * STEP, colW, Golden.BTN)
                            .tooltip(Tooltip.create(Component.translatable("message.skyabyss.tower.wager." + id + ".desc"))).build();
                    w.active = on || state.chosen().size() < state.pickMax();
                    addRenderableWidget(w);
                }
            }
        }
        Golden.Box foot = LAYOUT.foot();
        int by = foot.y() + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.shop"), b -> cmd("shop")).bounds(foot.x(), by, Golden.S89, Golden.BTN).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.records"), b -> {
            cmd("records");
            Minecraft.getInstance().setScreen(new ChatScreen(""));
        }).bounds(foot.x() + Golden.S89 + Golden.S5, by, Golden.S89, Golden.BTN).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.close"), b -> onClose()).bounds(foot.right() - Golden.S89, by, Golden.S89, Golden.BTN).build());
    }

    private void cmd(String sub) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand("skyabyss_tower " + sub);
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        // 塔へ入った (世界が替わった) ら閉じる
        if (mc.level == null || mc.level.dimension() != openedIn) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        int x = BODY.x() + Golden.S5;
        int tw = BODY.w() - Golden.S13;
        // 今日の挑戦と決まり
        Component today = Component.translatable(state.usedToday() ? "gui.skyabyss.tower_desk.used" : "gui.skyabyss.tower_desk.ready");
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, today, x, Golden.textY(BODY.y(), ROW), tw, state.usedToday() ? DENY : TEXT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.tower_desk.rule"), x, Golden.textY(BODY.y() + ROW, ROW), tw, SOFT, false);
        // 隊
        Component sq = state.leader().isEmpty() ? Component.translatable("gui.skyabyss.tower_desk.no_squad")
                : Component.translatable("gui.skyabyss.tower_desk.squad", state.leader(), state.members().size(), state.max());
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, sq, x, Golden.textY(Y_SQUAD, ROW), tw, GOLD, false);
        Component second = !state.members().isEmpty() ? Component.literal(String.join("、", state.members()))
                : state.joinLeaders().isEmpty() ? Component.translatable("gui.skyabyss.tower_desk.hint") : null;
        if (second != null) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, second, x, Golden.textY(Y_SQUAD + ROW, ROW), tw, TEXT, false);
        }
        if (state.isLeader()) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.tower_desk.wagers", state.pickMax(), state.bonusPct(), state.chosen().size()),
                    x, Golden.textY(Y_WAGERS, ROW), tw, SOFT, false);
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
