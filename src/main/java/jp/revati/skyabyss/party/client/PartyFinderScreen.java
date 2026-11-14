package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.party.PartyFinder;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// パーティー募集の画面 (party/PartyFinder。2026-11-07 の声「パーティー検索もできるようにしてほしい」)。
// 上の帯: 自分の募集 (目的を選ぶ・募集する・やめる)。下: 募集の一覧 (募集した人・目的・人数・平均のレベル・「加わる」)。
// O の画面の「募集」か /party find で開く。開いているあいだ 2 秒ごとに一覧を求める。Esc で O の画面へ戻る
public final class PartyFinderScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int TEXT = Golden.TEXT;
    private static final int SOFT = Golden.SOFT;
    private static final int GOLD = Golden.GOLD;
    private static final int ROW = Golden.ROW;
    private static final int PROW = Golden.S21;

    private static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    // 自分の募集の欄: 一行目に今の募集、二行目にパーティーの中かどうか、その下に釦 (2026-11-09 の声「募集UI、文字の上にボタンある」:
    // 前は欄が 34 で、二行目の文と釦が重なっていた)
    private static final int MINE_H = 2 + 2 * ROW + Golden.S3 + Golden.BTN + Golden.S5;
    private static final Golden.Box MINE = LAYOUT.body().top(MINE_H);
    private static final Golden.Box LIST = LAYOUT.body().afterTop(MINE_H);
    private static final int LIST_Y = LIST.y() + ROW + Golden.S5;
    private static final int LIST_H = LIST.bottom() - 2 - LIST_Y;

    static PartyNetwork.Finder last = new PartyNetwork.Finder(List.of(), "", true, false);

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final ScrollPanel panel = new ScrollPanel(PROW);
    private final List<Button> joinButtons = new ArrayList<>();
    private final Screen parent;
    // 選んでいる目的 (募集を出す前に選ぶ)
    private String goal = "any";
    private int ticks;

    public PartyFinderScreen(Screen parent) {
        super(Component.translatable("gui.skyabyss.party.finder.title"));
        this.parent = parent;
        if (!last.myGoal().isEmpty()) {
            goal = last.myGoal();
        }
    }

    // 一覧が届いた。開いていれば描き直し、何も開いていなければ開く (/party find)
    public static void receive(PartyNetwork.Finder f) {
        last = f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PartyFinderScreen s) {
            s.rebuildWidgets();
        } else if (mc.screen == null) {
            mc.setScreen(new PartyFinderScreen(null));
        }
    }

    static void send(int op, String name) {
        PacketDistributor.sendToServer(new PartyNetwork.Action(op, name));
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        PartyNetwork.Finder f = last;
        int by = MINE.bottom() - Golden.S5 - Golden.BTN;
        // 目的 (押すたびに次へ)
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.goal." + goal), b -> {
            int i = PartyFinder.GOALS.indexOf(goal);
            goal = PartyFinder.GOALS.get((i + 1) % PartyFinder.GOALS.size());
            if (!last.myGoal().isEmpty()) {
                send(PartyNetwork.Action.FINDER_POST, goal);
            }
            rebuildWidgets();
        }).bounds(MINE.x() + Golden.S8, by, Golden.S89, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.skyabyss.party.finder.goal_tip"))).build()).active = f.canPost();
        if (f.myGoal().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.finder.post"), b -> send(PartyNetwork.Action.FINDER_POST, goal))
                    .bounds(MINE.right() - Golden.S8 - Golden.S55, by, Golden.S55, Golden.BTN).build()).active = f.canPost();
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.finder.close"), b -> send(PartyNetwork.Action.FINDER_CLOSE, ""))
                    .bounds(MINE.right() - Golden.S8 - Golden.S55, by, Golden.S55, Golden.BTN).build());
        }
        joinButtons.clear();
        for (PartyFinder.Row r : f.rows()) {
            Button b = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.finder.join"), x -> send(PartyNetwork.Action.FINDER_JOIN, r.leaderName()))
                    .bounds(LIST.right() - Golden.S8 - Golden.S34, LIST_Y, Golden.S34, Golden.BTN).build());
            b.active = !r.mine() && !f.inParty() && r.members() < jp.revati.skyabyss.party.Parties.MAX_MEMBERS;
            joinButtons.add(b);
        }
    }

    @Override
    public void tick() {
        if (ticks++ % 40 == 0) {
            send(PartyNetwork.Action.FINDER_LIST, "");
        }
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.recess(g, MINE);
        Golden.recess(g, LIST);
        PartyNetwork.Finder f = last;
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        // 自分の募集
        int sx = MINE.x() + Golden.S8;
        int sw = MINE.w() - 2 * Golden.S8;
        Component state = !f.canPost() ? Component.translatable("gui.skyabyss.party.finder.not_leader")
                : f.myGoal().isEmpty() ? Component.translatable("gui.skyabyss.party.finder.mine_none")
                : Component.translatable("gui.skyabyss.party.finder.mine", Component.translatable("gui.skyabyss.party.goal." + f.myGoal()));
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, state, sx, Golden.textY(MINE.y() + 2, ROW), sw, f.myGoal().isEmpty() ? SOFT : GOLD, false);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            if (!(r instanceof Button b) || !joinButtons.contains(b)) {
                r.render(g, mouseX, mouseY, partialTick);
            }
        }
        // 一覧
        int ox = LIST.x() + Golden.S8;
        g.drawString(font, Component.translatable("gui.skyabyss.party.finder.list", f.rows().size()), ox, Golden.textY(LIST.y() + 2, ROW), GOLD, false);
        panel.setViewport(ox, LIST_Y, LIST.right() - 2 - ox, LIST_H);
        panel.setContentHeight(f.rows().size() * PROW);
        if (f.rows().isEmpty()) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.empty"), ox, Golden.textY(LIST_Y, PROW),
                    LIST.w() - 2 * Golden.S8, SOFT, false);
        }
        panel.begin(g);
        int nameW = Golden.S89;
        int goalW = LIST.w() - 2 * Golden.S8 - nameW - Golden.S55 - Golden.S34 - 3 * Golden.S5;
        for (int i = 0; i < f.rows().size(); i++) {
            PartyFinder.Row r = f.rows().get(i);
            int oy = panel.top() + i * PROW;
            if (oy + PROW < panel.y() || oy > panel.y() + LIST_H) {
                continue;
            }
            int ty = Golden.textY(oy, PROW);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal((r.mine() ? "★" : "・") + r.leaderName()), ox, ty, nameW, r.mine() ? GOLD : TEXT, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.goal." + r.goal()), ox + nameW + Golden.S5, ty, goalW, TEXT, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.row", r.members(),
                    jp.revati.skyabyss.party.Parties.MAX_MEMBERS, r.avgLevel()), ox + nameW + goalW + 2 * Golden.S5, ty, Golden.S55, SOFT, false);
        }
        panel.end(g);
        panel.drawBar(g, 0x30FFFFFF, 0x90E9C46A);
        for (int i = 0; i < joinButtons.size(); i++) {
            Button b = joinButtons.get(i);
            int oy = panel.top() + i * PROW;
            b.setY(oy + (PROW - Golden.BTN) / 2);
            b.visible = oy >= panel.y() && oy + PROW <= panel.y() + LIST_H;
            b.render(g, mouseX, mouseY, partialTick);
        }
        if (f.inParty()) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.in_party"), sx, Golden.textY(MINE.y() + 2 + ROW, ROW), sw, SOFT, false);
        }
        fit.end(g);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return panel.mouseScrolled(fit.x(mx), fit.y(my), dy) || super.mouseScrolled(fit.x(mx), fit.y(my), dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return panel.mouseClicked(fit.x(mx), fit.y(my), button) || super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return panel.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale())
                || super.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return panel.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
