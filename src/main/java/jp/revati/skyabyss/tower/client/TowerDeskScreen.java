package jp.revati.skyabyss.tower.client;

import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.JpWrap;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.PartyStage;
import jp.revati.skyabyss.tower.TowerDeskState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

// 塔の受付の画面 (あいだの塔。docs/design/54 の 2.2)。2026-11-02 の声「なぜ大規模レイドの入り方がチャット？これまで通り UI 出したらいいんじゃない」。
//   押した釦は受付の命令 (/skyabyss_tower …) を送る。サーバーが確かめ、受付のそばの人の画面を新しくする (TowerDeskState の open = false)
// 組み方は集まりの類の「扇に並ぶ顔札」(client/ui/PartyStage。docs/design/56 の 2.1):
//   左上に題・今日の挑戦・今週の主の構え・隊の行、その下に釦 (隊を作る / 塔へ入る・隊を抜ける)。隊の人は扇の札 (隊長は桃色で菱)。
//   隊が無いときは札の代わりに入れる隊の釦を縦に。右の生成りの帯に、隊長は今週の賭け (三つまで)、ほかは手引き。下の山の上に塔の店・記録・閉じる
public final class TowerDeskScreen extends Screen {
    static final int ROW = Golden.ROW;
    // 釦の行 (高さ 13 + あいだ 3)
    static final int STEP = Golden.BTN + 3;
    static final int Y_BUTTONS = 66;
    // 右の帯 (PartyStage.STRIP より上から)
    static final float[] STRIP = {270, 30, 400, 32, 398, 196, 274, 192};
    static final int WAGER_TOP = 76;
    static final int WAGER_BOTTOM = 186;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private TowerDeskState state;
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
                List.of("half_heal", "no_revive", "fast_adds", "short_time", "fast_moon", "hard_eyes"), List.of("half_heal", "fast_moon"), 3, 30, "star"));
    }

    // 撮影用: 隊長で、隊に七人の見本 (札は 4 枚まで、残りは名前の行)
    public static TowerDeskScreen sampleMany() {
        return new TowerDeskScreen(new TowerDeskState(true, false, "Dev", List.of("Dev", "ミナト", "カイ", "Nagi", "Isana", "Sora", "Umi"), true, 12, List.of(), List.of(),
                List.of("half_heal", "no_revive", "fast_adds", "short_time", "fast_moon", "hard_eyes"), List.of("half_heal"), 3, 30, "star"));
    }

    // 撮影用: 隊が無く、入れる隊が三つある見本
    public static TowerDeskScreen sampleJoin() {
        return new TowerDeskScreen(new TowerDeskState(true, false, "", List.of(), false, 12, List.of("ミナト", "Nagi", "Isana"), List.of(3, 1, 11),
                List.of(), List.of(), 3, 30, "star"));
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

    private int wagerStep() {
        int n = Math.max(1, state.wagers().size());
        return Math.min(STEP, (WAGER_BOTTOM - WAGER_TOP) / n);
    }

    @Override
    protected void init() {
        fit.fit(width, height, PartyStage.W, PartyStage.H, 3f);
        int x = PartyStage.HEAD_X;
        int y = Y_BUTTONS;
        if (state.leader().isEmpty()) {
            Button create = Paper.tag(Component.translatable("gui.skyabyss.tower_desk.create"), b -> cmd("create"))
                    .bounds(x, y, 89, Golden.BTN).primary().build();
            create.active = !state.usedToday();
            addRenderableWidget(create);
            // 入れる隊 (札の代わりに縦に)
            for (int i = 0; i < Math.min(4, state.joinLeaders().size()); i++) {
                String leader = state.joinLeaders().get(i);
                Button join = Paper.tag(Component.translatable("gui.skyabyss.tower_desk.join", leader, state.joinCounts().get(i), state.max()), b -> cmd("join " + leader))
                        .bounds(x, y + 26 + i * (STEP + 2), 180, Golden.BTN).build();
                join.active = !state.usedToday();
                addRenderableWidget(join);
            }
        } else {
            if (state.isLeader()) {
                addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.tower_desk.start"), b -> cmd("start"))
                        .bounds(x, y, 89, Golden.BTN).primary().build());
            }
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.tower_desk.quit"), b -> cmd("quit"))
                    .bounds(state.isLeader() ? x + 95 : x, y, 89, Golden.BTN).build());
            if (state.isLeader()) {
                // 今週の賭け (右の帯): 押すと選ぶ / 外す (三つまで)
                int sx = PartyStage.STRIP_X;
                int step = wagerStep();
                for (int i = 0; i < state.wagers().size(); i++) {
                    String id = state.wagers().get(i);
                    boolean on = state.chosen().contains(id);
                    Component label = Component.literal(on ? "✔ " : "").append(Component.translatable("message.skyabyss.tower.wager." + id));
                    Button w = Paper.tag(label, b -> cmd("wager " + id))
                            .bounds(sx, WAGER_TOP + i * step, PartyStage.STRIP_W - 6, Golden.BTN)
                            .tooltip(Tooltip.create(Component.translatable("message.skyabyss.tower.wager." + id + ".desc"))).build();
                    w.active = on || state.chosen().size() < state.pickMax();
                    addRenderableWidget(w);
                }
            }
        }
        int fy = PartyStage.FOOT_Y;
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.tower_desk.shop"), b -> cmd("shop")).bounds(20, fy, 80, Golden.BTN).build());
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.tower_desk.records"), b -> {
            cmd("records");
            Minecraft.getInstance().setScreen(new ChatScreen(""));
        }).bounds(106, fy, 80, Golden.BTN).build());
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.tower_desk.close"), b -> onClose()).bounds(316, fy, 76, Golden.BTN).build());
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
        Paper.dim(g, width, height);
    }

    // 名前から顔の id (いま繋がっていればその人の、いなければ名前から決まる顔)
    private String faceId(String name) {
        PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(name);
        return info != null ? info.getProfile().getId().toString() : name;
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, PartyStage.W, PartyStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        PartyStage.backdrop(g, fit, width, height);
        PartyStage.title(g, font, title);
        int x = PartyStage.HEAD_X;
        int tw = PartyStage.HEAD_W;
        // 今日の挑戦と、今週の主の構え
        Component today = Component.translatable(state.usedToday() ? "gui.skyabyss.tower_desk.used" : "gui.skyabyss.tower_desk.ready");
        FitText.draw(g, font, today, x, 26, tw, state.usedToday() ? Golden.DENY : PartyStage.ON_PINK, false);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.tower_desk.rule_stance",
                Component.translatable("message.skyabyss.tower.stance." + state.stance())), x, 38, tw, Paper.ON_BLACK_SOFT, false);
        // 隊
        Component sq = state.leader().isEmpty() ? Component.translatable("gui.skyabyss.tower_desk.no_squad")
                : Component.translatable("gui.skyabyss.tower_desk.squad", state.leader(), state.members().size(), state.max());
        FitText.draw(g, font, sq, x, 50, tw, Paper.ON_BLACK_PINK, false);
        // 隊の人の札
        List<String> members = state.members();
        // 札は 4 枚まで (扇が下の釦にかからないように)。5 人目からは札の上の行に名前を並べる
        int n = Math.max(2, Math.min(4, members.size()));
        for (int i = 0; i < Math.min(4, members.size()); i++) {
            String m = members.get(i);
            boolean leader = m.equals(state.leader());
            int[] c = PartyStage.cardAt(i, n);
            PartyStage.card(g, i, n, leader);
            PartyStage.face(g, faceId(m), c[0], c[1]);
            PartyStage.cardText(g, font, c[0], c[1], Component.literal(m), null, null, leader);
            if (leader) {
                PartyStage.leaderPin(g, c[0], c[1]);
            }
        }
        if (members.size() > 4) {
            Component more = Component.literal("+" + (members.size() - 4) + "  " + String.join("、", members.subList(4, members.size())));
            if (font.width(more) > tw) {
                more = Component.literal("+" + (members.size() - 4));
            }
            g.drawString(font, more, x, 84, Paper.ON_BLACK_SOFT, false);
        }
        // 右の帯: 隊長は今週の賭け、ほかは手引き
        // 帯は上へ伸ばす (賭けの見出しが三行になるため)
        Paper.cut(g, "tower_strip", STRIP, Paper.CREAM, Paper.PINK_LO, 3, 4);
        Component strip = state.isLeader() ? Component.translatable("gui.skyabyss.tower_desk.wagers", state.pickMax(), state.bonusPct(), state.chosen().size())
                : Component.translatable("gui.skyabyss.tower_desk.hint");
        int sy = 38;
        for (FormattedCharSequence line : JpWrap.split(font, Component.literal(strip.getString()), PartyStage.STRIP_W - 4)) {
            if (state.isLeader() && sy + 9 > WAGER_TOP - 2) {
                break;
            }
            g.drawString(font, line, PartyStage.STRIP_X, sy, state.isLeader() ? Paper.INK_HEAD : Paper.INK_SOFT, false);
            sy += 11;
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
