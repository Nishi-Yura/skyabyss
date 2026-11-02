package jp.revati.skyabyss.tower.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.tower.TowerDeskState;
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
public final class TowerDeskScreen extends Screen {
    static final int W = 300;
    static final int H = 214;
    static final int GOLD = 0xF2D78A;
    static final int TEXT = 0xE6E1D2;
    static final int SOFT = 0xA8A496;
    static final int DENY = 0xD9779A;

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
        left = (width - W) / 2;
        top = (height - H) / 2;
        int y = top + 92;
        if (state.leader().isEmpty()) {
            Button create = Button.builder(Component.translatable("gui.skyabyss.tower_desk.create"), b -> cmd("create"))
                    .bounds(left + 12, y, 132, 18).build();
            create.active = !state.usedToday();
            addRenderableWidget(create);
            for (int i = 0; i < Math.min(4, state.joinLeaders().size()); i++) {
                String leader = state.joinLeaders().get(i);
                Button join = Button.builder(Component.translatable("gui.skyabyss.tower_desk.join", leader, state.joinCounts().get(i), state.max()), b -> cmd("join " + leader))
                        .bounds(left + 152, y + i * 20, 136, 18).build();
                join.active = !state.usedToday();
                addRenderableWidget(join);
            }
        } else {
            if (state.isLeader()) {
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.start"), b -> cmd("start"))
                        .bounds(left + 12, y, 132, 18).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.quit"), b -> cmd("quit"))
                    .bounds(left + (state.isLeader() ? 152 : 12), y, 136, 18).build());
            if (state.isLeader()) {
                // 今週の賭け: 押すと選ぶ / 外す (三つまで)
                for (int i = 0; i < state.wagers().size(); i++) {
                    String id = state.wagers().get(i);
                    boolean on = state.chosen().contains(id);
                    Component label = Component.literal(on ? "✔ " : "").append(Component.translatable("message.skyabyss.tower.wager." + id));
                    Button w = Button.builder(label, b -> cmd("wager " + id)).bounds(left + 12 + (i % 2) * 140, top + 124 + (i / 2) * 20, 136, 18)
                            .tooltip(Tooltip.create(Component.translatable("message.skyabyss.tower.wager." + id + ".desc"))).build();
                    w.active = on || state.chosen().size() < state.pickMax();
                    addRenderableWidget(w);
                }
            }
        }
        int by = top + H - 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.shop"), b -> cmd("shop")).bounds(left + 12, by, 88, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.records"), b -> {
            cmd("records");
            Minecraft.getInstance().setScreen(new ChatScreen(""));
        }).bounds(left + 106, by, 88, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.tower_desk.close"), b -> onClose()).bounds(left + 200, by, 88, 18).build());
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
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF000000 | GOLD);
        g.fill(left, top, left + W, top + H, 0xF0141118);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 8, GOLD);
        // 今日の挑戦
        Component today = Component.translatable(state.usedToday() ? "gui.skyabyss.tower_desk.used" : "gui.skyabyss.tower_desk.ready");
        g.drawString(font, today, left + 12, top + 26, state.usedToday() ? DENY : TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.tower_desk.rule"), left + 12, top + 38, SOFT, false);
        // 隊
        Component head = state.leader().isEmpty() ? Component.translatable("gui.skyabyss.tower_desk.no_squad")
                : Component.translatable("gui.skyabyss.tower_desk.squad", state.leader(), state.members().size(), state.max());
        g.drawString(font, head, left + 12, top + 56, GOLD, false);
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!state.members().isEmpty()) {
            lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.literal(String.join("、", state.members())), W - 24));
        } else if (state.joinLeaders().isEmpty()) {
            lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.tower_desk.hint"), W - 24));
        }
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), left + 12, top + 68 + i * 10, TEXT, false);
        }
        if (state.isLeader()) {
            g.drawString(font, Component.translatable("gui.skyabyss.tower_desk.wagers", state.pickMax(), state.bonusPct(), state.chosen().size()), left + 12, top + 113, SOFT, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
