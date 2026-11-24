package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.PartyStage;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// 「物語と仲間」の画面 (O)。組み方は集まりの類の「扇に並ぶ顔札」(client/ui/PartyStage。docs/design/56 の 2.1):
// 左上に物語の段階とレベルの上限、その下に仲間の札を扇に (顔・名前・Lv・体力。長の札は桃色で菱を留める。長には札の下に ★ と ×)。
// 右上から差し込む桃色の紙に届いた招待 (受ける・断る)、右の生成りの帯に招待できる人。下の山の上に「抜ける」と「募集」。
// Esc で閉じる。操作はサーバーへ送るだけ (検めるのはサーバー)。届いた状態で描き直す
public final class PartyScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_LIST = 8;
    // 招待できる人の一行 (釦 13 + 余白)
    private static final int PROW = Golden.S21;
    private static final int CAND_X = PartyStage.STRIP_X;
    private static final int CAND_Y = PartyStage.STRIP_Y + 16;
    private static final int CAND_H = 104;
    private static final int CAND_W = PartyStage.STRIP_W;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final ScrollPanel candPanel = new ScrollPanel(PROW);
    private final List<Button> inviteButtons = new ArrayList<>();

    public PartyScreen() {
        super(Component.translatable("gui.skyabyss.party.title"));
    }

    void refresh() {
        rebuildWidgets();
    }

    private static void send(int op, String name) {
        PacketDistributor.sendToServer(new PartyNetwork.Action(op, name));
    }

    private boolean amLeader(PartyNetwork.Sync s) {
        String self = Minecraft.getInstance().player.getUUID().toString();
        return s.members().isEmpty() || s.members().stream().anyMatch(m -> m.leader() && m.id().equals(self));
    }

    @Override
    protected void init() {
        fit.fit(width, height, PartyStage.W, PartyStage.H, 3f);
        PartyNetwork.Sync s = PartyClient.state();
        String self = minecraft.player.getUUID().toString();
        // 招待が届いている (右上の桃色の紙)
        if (!s.inviteFrom().isEmpty()) {
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.accept"), b -> send(PartyNetwork.Action.ACCEPT, ""))
                    .bounds(300, 26, 46, Golden.BTN).build());
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.decline"), b -> send(PartyNetwork.Action.DECLINE, ""))
                    .bounds(352, 26, 46, Golden.BTN).build());
        }
        // 仲間の札の下に ★ (長を任せる) と × (外す)
        boolean leader = amLeader(s);
        int n = s.members().size();
        for (int i = 0; i < n; i++) {
            PartyNetwork.Member m = s.members().get(i);
            if (leader && !m.id().equals(self)) {
                int[] c = PartyStage.cardAt(i, n);
                int by = c[1] + PartyStage.CARD_H / 2 + 4;
                addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.promote"), b -> send(PartyNetwork.Action.PROMOTE, m.name()))
                        .bounds(c[0] - (n >= 5 ? 20 : 25), by, n >= 5 ? 19 : 24, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.promote_tip"))).build());
                addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.kick"), b -> send(PartyNetwork.Action.KICK, m.name()))
                        .bounds(c[0] + 1, by, n >= 5 ? 19 : 24, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.kick_tip"))).build());
            }
        }
        if (!s.members().isEmpty()) {
            addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.leave"), b -> send(PartyNetwork.Action.LEAVE, ""))
                    .bounds(20, PartyStage.FOOT_Y, 60, Golden.BTN).build());
        }
        // パーティー募集の画面
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.finder.open"), b -> {
            minecraft.setScreen(new PartyFinderScreen(this));
            send(PartyNetwork.Action.FINDER_LIST, "");
        }).bounds(320, PartyStage.FOOT_Y - 2, 70, Golden.BTN).primary().tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.skyabyss.party.finder.open_tip"))).build());
        // 招待できる人 (右の帯)
        inviteButtons.clear();
        if (leader && s.members().size() < jp.revati.skyabyss.party.Parties.MAX_MEMBERS) {
            for (String name : candidates(s)) {
                inviteButtons.add(addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.party.invite"),
                        b -> send(PartyNetwork.Action.INVITE, name)).bounds(CAND_X + CAND_W - 44 - ScrollPanel.GUTTER, CAND_Y, 44, Golden.BTN).build()));
            }
        }
    }

    // 同じサーバーにいて、まだ仲間でない人 (タブの一覧から)
    private List<String> candidates(PartyNetwork.Sync s) {
        List<String> out = new ArrayList<>();
        Set<String> members = new HashSet<>();
        s.members().forEach(m -> members.add(m.id()));
        if (minecraft.getConnection() == null) {
            return out;
        }
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String id = info.getProfile().getId().toString();
            if (id.equals(minecraft.player.getUUID().toString()) || members.contains(id)) {
                continue;
            }
            out.add(info.getProfile().getName());
            if (out.size() >= MAX_LIST) {
                break;
            }
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, PartyStage.W, PartyStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        PartyStage.backdrop(g, fit, width, height);
        PartyNetwork.Sync s = PartyClient.state();
        PartyStage.title(g, font, title);
        // 物語
        Component next = s.nextTitan().isEmpty() ? Component.translatable("gui.skyabyss.party.story_done")
                : s.nextTitan().equals("?shadow") ? Component.translatable("gui.skyabyss.party.story_next_shadow")
                : s.nextTitan().equals("?unseen") ? Component.translatable("gui.skyabyss.party.story_next_unseen")
                : Component.translatable("gui.skyabyss.party.story_next", Component.translatable("progress.skyabyss.titan." + s.nextTitan()));
        int hx = PartyStage.HEAD_X;
        int hw = PartyStage.HEAD_W;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.story", s.stage(), 4, s.cap()), hx, 26, hw, PartyStage.ON_PINK, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, next, hx, 38, hw, Paper.ON_BLACK_SOFT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.world", s.worldStage(), 4), hx, 50, hw, Paper.ON_BLACK_SOFT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.members", s.members().size(), jp.revati.skyabyss.party.Parties.MAX_MEMBERS, s.sharePercent()), hx, 68, hw,
                Paper.ON_BLACK_HI, false);
        // 招待 (右上の桃色の紙)
        if (!s.inviteFrom().isEmpty()) {
            int[] t = PartyStage.note(g);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.invite_from", s.inviteFrom()), t[0], t[1], 140, PartyStage.ON_PINK, false);
        }
        // 仲間の札 (扇)。一人のときは自分の札を一枚
        List<Component> questTip = null;
        int n = s.members().size();
        if (n == 0) {
            int[] c = PartyStage.cardAt(0, 1);
            PartyStage.card(g, 0, 1, false);
            PartyStage.face(g, minecraft.player.getUUID().toString(), c[0], c[1]);
            PartyStage.cardText(g, font, c[0], c[1], minecraft.player.getName(), Component.translatable("gui.skyabyss.party.alone"), null, false);
        }
        for (int i = 0; i < n; i++) {
            PartyNetwork.Member m = s.members().get(i);
            int[] c = PartyStage.cardAt(i, n);
            PartyStage.card(g, i, n, m.leader());
            PartyStage.face(g, m.id(), c[0], c[1]);
            Component lv;
            if (m.rebirths() > 0) {
                long t = jp.revati.skyabyss.client.rebirth.RebirthClient.timeMs();
                boolean still = jp.revati.skyabyss.client.rebirth.RebirthClient.still();
                lv = jp.revati.skyabyss.rebirth.RebirthText.symbols(m.rebirths(), t, still).append(" ")
                        .append(jp.revati.skyabyss.rebirth.RebirthText.level(m.level(), m.rebirths(), t, still));
            } else {
                lv = Component.literal("Lv " + m.level());
            }
            Component hp = m.online() ? Component.literal(jp.revati.skyabyss.Hp.number(m.health()) + "/" + jp.revati.skyabyss.Hp.of(m.maxHealth())) : null;
            PartyStage.cardText(g, font, c[0], c[1], Component.literal(m.name()), m.online() ? lv : null, hp, m.leader());
            if (m.leader()) {
                PartyStage.leaderPin(g, c[0], c[1]);
            }
            // 仲間の今のクエスト: 札に指を置くと、メインと追いかけているクエストを出す (2026-11-08)
            if (m.online() && PartyStage.in(mouseX, mouseY, c[0] - PartyStage.cardW(n) / 2, c[1] - PartyStage.CARD_H / 2, PartyStage.CARD_W, PartyStage.CARD_H)) {
                questTip = questLines(m);
            }
        }
        // 招待できる人 (右の帯。目盛りつきの送り。招待の釦はその行に合わせて動かし、枠の外の釦は隠す)
        PartyStage.strip(g, font, Component.translatable("gui.skyabyss.party.others"));
        List<String> others = candidates(s);
        candPanel.setViewport(CAND_X, CAND_Y, CAND_W, CAND_H);
        candPanel.setContentHeight(others.size() * PROW);
        if (others.isEmpty()) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.nobody"), CAND_X, Golden.textY(CAND_Y, PROW), CAND_W, Paper.INK_SOFT, false);
        }
        candPanel.begin(g);
        for (int i = 0; i < others.size(); i++) {
            int oy = candPanel.top() + i * PROW;
            if (oy + PROW < candPanel.y() || oy > candPanel.y() + CAND_H) {
                continue;
            }
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(others.get(i));
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, PartyStage.skin(info == null ? others.get(i) : info.getProfile().getId().toString()), CAND_X, oy + 4, 12);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(others.get(i)), CAND_X + 16, Golden.textY(oy, PROW),
                    CAND_W - 16 - 44 - ScrollPanel.GUTTER - 4, Paper.INK, false);
        }
        candPanel.end(g);
        candPanel.drawBar(g, 0x30181722, 0xFFE45A9C);
        for (int i = 0; i < inviteButtons.size(); i++) {
            Button b = inviteButtons.get(i);
            int oy = candPanel.top() + i * PROW;
            b.setY(oy + (PROW - Golden.BTN) / 2);
            b.visible = oy >= candPanel.y() && oy + PROW <= candPanel.y() + CAND_H;
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        if (questTip != null) {
            g.renderComponentTooltip(font, questTip, mouseX, mouseY);
        }
        fit.end(g);
    }

    // 仲間の今のクエストの行 (題名と、今の段の最初のやること)
    static List<Component> questLines(PartyNetwork.Member m) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(m.name()).withStyle(net.minecraft.ChatFormatting.GOLD));
        quest(out, "gui.skyabyss.party.quest_main", m.mainQuest(), m.mainStage());
        quest(out, "gui.skyabyss.party.quest_track", m.trackQuest(), m.trackStage());
        if (out.size() == 1) {
            out.add(Component.translatable("gui.skyabyss.party.quest_none").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return out;
    }

    private static void quest(List<Component> out, String label, String id, int stageIndex) {
        if (id.isEmpty()) {
            return;
        }
        jp.revati.skyabyss.quest.QuestDef def = jp.revati.skyabyss.client.quest.QuestClient.quest(id);
        if (def == null) {
            return;
        }
        out.add(Component.translatable(label, Component.translatable(def.titleKey())).withStyle(net.minecraft.ChatFormatting.YELLOW));
        if (stageIndex >= 0 && stageIndex < def.stages().size() && !def.stages().get(stageIndex).objectives().isEmpty()) {
            jp.revati.skyabyss.quest.QuestDef.Stage st = def.stages().get(stageIndex);
            out.add(Component.literal("  ・" + jp.revati.skyabyss.client.quest.QuestHud.objectiveText(def, st, st.objectives().get(0))).withStyle(net.minecraft.ChatFormatting.WHITE));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return candPanel.mouseScrolled(fit.x(mx), fit.y(my), dy) || super.mouseScrolled(fit.x(mx), fit.y(my), dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return candPanel.mouseClicked(fit.x(mx), fit.y(my), button) || super.mouseClicked(fit.x(mx), fit.y(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return candPanel.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale())
                || super.mouseDragged(fit.x(mx), fit.y(my), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return candPanel.mouseReleased(fit.x(mx), fit.y(my), button) || super.mouseReleased(fit.x(mx), fit.y(my), button);
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
