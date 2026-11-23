package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.JpWrap;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.PartyStage;
import jp.revati.skyabyss.party.PartyFinder;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// パーティー募集の画面 (party/PartyFinder。2026-11-07 の声「パーティー検索もできるようにしてほしい」)。
// 組み方は集まりの類の「扇に並ぶ顔札」(client/ui/PartyStage。docs/design/56 の 2.1):
//   左上に題と一覧の数。募集は一つずつ扇の札 (募集した人の顔・名前・目的・人数と平均のレベル)、札の下に「加わる」。自分の募集の札は桃色で菱を留める。
//   4 枚より多ければ下の山の上の ‹ › でめくる。右の生成りの帯に自分の募集 (今の募集・目的を選ぶ・募集する / やめる)。
// O の画面の「募集」か /party find で開く。開いているあいだ 2 秒ごとに一覧を求める。Esc で O の画面へ戻る
public final class PartyFinderScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int PER_PAGE = 4;
    private static final int BTN_W = 44;

    static PartyNetwork.Finder last = new PartyNetwork.Finder(List.of(), "", true, false);

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final List<Button> joinButtons = new ArrayList<>();
    private final Screen parent;
    // 選んでいる目的 (募集を出す前に選ぶ)
    private String goal = "any";
    private int page;
    private int ticks;
    // 撮影の見本 (一覧を求めない)
    private boolean demo;

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

    // 撮影用: 見本の一覧で開く (サーバーへ一覧を求めない)
    public static PartyFinderScreen demo(PartyNetwork.Finder f) {
        last = f;
        PartyFinderScreen s = new PartyFinderScreen(null);
        s.demo = true;
        return s;
    }

    static void send(int op, String name) {
        PacketDistributor.sendToServer(new PartyNetwork.Action(op, name));
    }

    private int pages() {
        return Math.max(1, (last.rows().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private List<PartyFinder.Row> shown() {
        List<PartyFinder.Row> rows = last.rows();
        int from = Math.min(page * PER_PAGE, rows.size());
        return rows.subList(from, Math.min(rows.size(), from + PER_PAGE));
    }

    @Override
    protected void init() {
        fit.fit(width, height, PartyStage.W, PartyStage.H, 3f);
        PartyNetwork.Finder f = last;
        page = Math.min(page, pages() - 1);
        // 自分の募集 (右の帯の下): 目的 (押すたびに次へ) と、募集する / やめる
        int sx = PartyStage.STRIP_X;
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.goal." + goal), b -> {
            int i = PartyFinder.GOALS.indexOf(goal);
            goal = PartyFinder.GOALS.get((i + 1) % PartyFinder.GOALS.size());
            if (!last.myGoal().isEmpty()) {
                send(PartyNetwork.Action.FINDER_POST, goal);
            }
            rebuildWidgets();
        }).bounds(sx, 150, PartyStage.STRIP_W - 6, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.skyabyss.party.finder.goal_tip"))).build()).active = f.canPost();
        if (f.myGoal().isEmpty()) {
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.finder.post"), b -> send(PartyNetwork.Action.FINDER_POST, goal))
                    .bounds(sx + PartyStage.STRIP_W - 66, 168, 60, Golden.BTN).primary().build()).active = f.canPost();
        } else {
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.finder.close"), b -> send(PartyNetwork.Action.FINDER_CLOSE, ""))
                    .bounds(sx + PartyStage.STRIP_W - 66, 168, 60, Golden.BTN).build());
        }
        // 札の下の「加わる」
        joinButtons.clear();
        List<PartyFinder.Row> rows = shown();
        for (int i = 0; i < rows.size(); i++) {
            PartyFinder.Row r = rows.get(i);
            int[] c = PartyStage.cardAt(i, Math.max(2, rows.size()));
            Button b = addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.finder.join"), x -> send(PartyNetwork.Action.FINDER_JOIN, r.leaderName()))
                    .bounds(c[0] - BTN_W / 2, c[1] + PartyStage.CARD_H / 2 + 4, BTN_W, Golden.BTN).build());
            b.active = !r.mine() && !f.inParty() && r.members() < jp.revati.skyabyss.party.Parties.MAX_MEMBERS;
            joinButtons.add(b);
        }
        // めくる (下の山の上)
        if (pages() > 1) {
            addRenderableWidget(Paper.tag(Component.literal("‹"), b -> {
                page = (page + pages() - 1) % pages();
                rebuildWidgets();
            }).bounds(300, PartyStage.FOOT_Y, 22, Golden.BTN).build());
            addRenderableWidget(Paper.tag(Component.literal("›"), b -> {
                page = (page + 1) % pages();
                rebuildWidgets();
            }).bounds(350, PartyStage.FOOT_Y, 22, Golden.BTN).build());
        }
    }

    @Override
    public void tick() {
        if (!demo && ticks++ % 40 == 0) {
            send(PartyNetwork.Action.FINDER_LIST, "");
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
        PartyNetwork.Finder f = last;
        PartyStage.title(g, font, title);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.list", f.rows().size()), PartyStage.HEAD_X, 26, PartyStage.HEAD_W, PartyStage.ON_PINK, false);
        if (f.inParty()) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.in_party"), PartyStage.HEAD_X, 38, PartyStage.HEAD_W, Paper.ON_BLACK_SOFT, false);
        }
        // 募集の札
        List<PartyFinder.Row> rows = shown();
        if (rows.isEmpty()) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.party.finder.empty"), PartyStage.HEAD_X, 120, PartyStage.HEAD_W, Paper.ON_BLACK_SOFT, false);
        }
        int n = Math.max(2, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PartyFinder.Row r = rows.get(i);
            int[] c = PartyStage.cardAt(i, n);
            PartyStage.card(g, i, n, r.mine());
            PartyStage.face(g, faceId(r.leaderName()), c[0], c[1]);
            PartyStage.cardText(g, font, c[0], c[1], Component.literal(r.leaderName()), Component.translatable("gui.skyabyss.party.goal." + r.goal()),
                    Component.translatable("gui.skyabyss.party.finder.row", r.members(), jp.revati.skyabyss.party.Parties.MAX_MEMBERS, r.avgLevel()), r.mine());
            if (r.mine()) {
                PartyStage.leaderPin(g, c[0], c[1]);
            }
        }
        if (pages() > 1) {
            FitText.drawCentered(g, font, Component.literal((page + 1) + "/" + pages()), 336, PartyStage.FOOT_Y + 3, 24, PartyStage.ON_PINK, false);
        }
        // 右の帯: 自分の募集
        PartyStage.strip(g, font, null);
        Component state = !f.canPost() ? Component.translatable("gui.skyabyss.party.finder.not_leader")
                : f.myGoal().isEmpty() ? Component.translatable("gui.skyabyss.party.finder.mine_none")
                : Component.translatable("gui.skyabyss.party.finder.mine", Component.translatable("gui.skyabyss.party.goal." + f.myGoal()));
        int y = PartyStage.STRIP_Y;
        for (FormattedCharSequence line : JpWrap.split(font, Component.literal(state.getString()), PartyStage.STRIP_W - 4)) {
            g.drawString(font, line, PartyStage.STRIP_X, y, f.myGoal().isEmpty() ? Paper.INK_SOFT : Paper.INK_HEAD, false);
            y += 11;
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        fit.end(g);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (pages() > 1 && dy != 0) {
            page = Math.floorMod(page + (dy < 0 ? 1 : -1), pages());
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(fit.x(mx), fit.y(my), dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, PartyStage.W, PartyStage.H);
    }
}
