package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// 「物語と仲間」の画面 (O)。上の帯に物語の段階とレベルの上限 (招待が届いていれば右に受ける・断る)、主の列 (左) に仲間、脇の列 (右) に招待できる人。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56)。Esc で閉じる。操作はサーバーへ送るだけ (検めるのはサーバー)。届いた状態で描き直す
public final class PartyScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int TEXT = Golden.TEXT;
    private static final int SOFT = Golden.SOFT;
    private static final int GOLD = Golden.GOLD;
    private static final int MAX_LIST = 8;
    private static final int ROW = Golden.ROW;
    // 仲間と招待できる人の一行 (釦 13 + 余白)
    private static final int PROW = Golden.S21;

    private static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    // 上の帯 (物語) と、その下の二つの列
    private static final Golden.Box STORY = LAYOUT.body().top(Golden.S55);
    private static final Golden.Box LOWER = LAYOUT.body().afterTop(Golden.S55);
    private static final Golden.Box MEMBERS = LOWER.major(true);
    private static final Golden.Box OTHERS = LOWER.minor(false);
    private static final int CAND_Y = OTHERS.y() + ROW + Golden.S5;
    private static final int CAND_H = OTHERS.bottom() - 2 - CAND_Y;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final int left = 0;
    private final int top = 0;
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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        PartyNetwork.Sync s = PartyClient.state();
        String self = minecraft.player.getUUID().toString();
        // 招待が届いている (物語の帯の右下)
        if (!s.inviteFrom().isEmpty()) {
            int by = STORY.bottom() - Golden.S5 - Golden.BTN;
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.decline"), b -> send(PartyNetwork.Action.DECLINE, ""))
                    .bounds(STORY.right() - Golden.S5 - Golden.S34, by, Golden.S34, Golden.BTN).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.accept"), b -> send(PartyNetwork.Action.ACCEPT, ""))
                    .bounds(STORY.right() - Golden.S5 - 2 * Golden.S34 - Golden.S3, by, Golden.S34, Golden.BTN).build());
        }
        // 仲間 (主の列)
        boolean leader = amLeader(s);
        int y = MEMBERS.y() + ROW + Golden.S5;
        for (PartyNetwork.Member m : s.members()) {
            if (leader && !m.id().equals(self)) {
                int by = y + (PROW - Golden.BTN) / 2;
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.promote"), b -> send(PartyNetwork.Action.PROMOTE, m.name()))
                        .bounds(MEMBERS.right() - Golden.S5 - 2 * Golden.S21 - Golden.S3, by, Golden.S21, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.promote_tip"))).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.kick"), b -> send(PartyNetwork.Action.KICK, m.name()))
                        .bounds(MEMBERS.right() - Golden.S5 - Golden.S21, by, Golden.S21, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.kick_tip"))).build());
            }
            y += PROW;
        }
        if (!s.members().isEmpty()) {
            // 抜ける: 仲間の見出しの右
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.leave"), b -> send(PartyNetwork.Action.LEAVE, ""))
                    .bounds(MEMBERS.right() - Golden.S5 - Golden.S55, MEMBERS.y() + 2, Golden.S55, Golden.BTN).build());
        }
        // パーティー募集の画面 (脇の列の見出しの右)
        addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.finder.open"), b -> {
            minecraft.setScreen(new PartyFinderScreen(this));
            send(PartyNetwork.Action.FINDER_LIST, "");
        }).bounds(OTHERS.right() - Golden.S5 - Golden.S34, OTHERS.y() + 2, Golden.S34, Golden.BTN).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.skyabyss.party.finder.open_tip"))).build());
        // 招待できる人 (脇の列)
        inviteButtons.clear();
        if (leader && s.members().size() < 4) {
            for (String name : candidates(s)) {
                inviteButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.invite"),
                        b -> send(PartyNetwork.Action.INVITE, name)).bounds(OTHERS.right() - Golden.S8 - Golden.S34, CAND_Y, Golden.S34, Golden.BTN).build()));
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
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.recess(g, STORY);
        Golden.recess(g, MEMBERS);
        Golden.recess(g, OTHERS);
        PartyNetwork.Sync s = PartyClient.state();
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), GOLD, false);
        // 物語
        Component next = s.nextTitan().isEmpty() ? Component.translatable("gui.skyabyss.party.story_done")
                : s.nextTitan().equals("?shadow") ? Component.translatable("gui.skyabyss.party.story_next_shadow")
                : s.nextTitan().equals("?unseen") ? Component.translatable("gui.skyabyss.party.story_next_unseen")
                : Component.translatable("gui.skyabyss.party.story_next", Component.translatable("progress.skyabyss.titan." + s.nextTitan()));
        int sx = STORY.x() + Golden.S8;
        int sw = STORY.w() - 2 * Golden.S8;
        int sy = STORY.y() + 2;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.story", s.stage(), 4, s.cap()), sx, Golden.textY(sy, ROW), sw, TEXT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, next, sx, Golden.textY(sy + ROW, ROW), sw, SOFT, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.world", s.worldStage(), 4), sx, Golden.textY(sy + 2 * ROW, ROW),
                s.inviteFrom().isEmpty() ? sw : sw - 2 * Golden.S34 - Golden.S8, SOFT, false);
        if (!s.inviteFrom().isEmpty()) {
            Component from = Component.translatable("gui.skyabyss.party.invite_from", s.inviteFrom());
            int fw = Math.min(font.width(from), sw / 2);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, from, STORY.right() - Golden.S8 - fw, Golden.textY(sy + ROW, ROW), sw / 2, GOLD, false);
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        // 仲間
        int mx0 = MEMBERS.x() + Golden.S8;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.party.members", s.members().size(), 4, s.sharePercent()), mx0,
                Golden.textY(MEMBERS.y() + 2, ROW), MEMBERS.w() - 2 * Golden.S8 - (s.members().isEmpty() ? 0 : Golden.S55 + Golden.S5), GOLD, false);
        int y = MEMBERS.y() + ROW + Golden.S5;
        if (s.members().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.alone"), mx0, Golden.textY(y, PROW), SOFT, false);
        }
        for (PartyNetwork.Member m : s.members()) {
            String line = (m.leader() ? "★" : "・") + m.name();
            int ty = Golden.textY(y, PROW);
            // 転生した人は、名前に段の色の枠と記号、レベルは Lv 50+n (docs/design/31 の C15)
            int frame = jp.revati.skyabyss.client.rebirth.RebirthClient.frameStyle(m.rebirths());
            if (frame > 0) {
                jp.revati.skyabyss.client.rebirth.RebirthClient.drawFrame(g, mx0 - 3, ty - 3, 66, 13, m.rebirths());
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(line), mx0, ty, 60, m.online() ? TEXT : 0x707070, false);
            if (m.online()) {
                int lx = mx0 + 68;
                int lw = MEMBERS.right() - Golden.S8 - 2 * Golden.S21 - Golden.S3 - Golden.S5 - lx;
                if (m.rebirths() > 0) {
                    long t = jp.revati.skyabyss.client.rebirth.RebirthClient.timeMs();
                    boolean still = jp.revati.skyabyss.client.rebirth.RebirthClient.still();
                    Component lv = jp.revati.skyabyss.rebirth.RebirthText.symbols(m.rebirths(), t, still).append(" ")
                            .append(jp.revati.skyabyss.rebirth.RebirthText.level(m.level(), m.rebirths(), t, still));
                    Component hp = Component.literal(" " + jp.revati.skyabyss.Hp.number(m.health()) + "/" + jp.revati.skyabyss.Hp.of(m.maxHealth()));
                    // 横に収まらなければ体力は省く (隣と重ねない)
                    g.drawString(font, font.width(lv) + font.width(hp) <= lw ? lv.copy().append(hp) : lv, lx, ty, SOFT, false);
                } else {
                    jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal("Lv" + m.level() + " " + jp.revati.skyabyss.Hp.number(m.health()) + "/" + jp.revati.skyabyss.Hp.of(m.maxHealth())),
                            lx, ty, lw, m.sameDim() ? SOFT : 0x707070, false);
                }
            }
            y += PROW;
        }
        // 仲間の今のクエスト: 行に指を置くと、メインと追いかけているクエストを出す (2026-11-08)
        int qy = MEMBERS.y() + ROW + Golden.S5;
        List<Component> questTip = null;
        for (PartyNetwork.Member m : s.members()) {
            if (mouseX >= MEMBERS.x() && mouseX < MEMBERS.right() - 2 * Golden.S21 - Golden.S8 && mouseY >= qy && mouseY < qy + PROW && m.online()) {
                questTip = questLines(m);
            }
            qy += PROW;
        }
        // 招待できる人 (目盛りつきの送り。招待の釦はその行に合わせて動かし、枠の外の釦は隠す)
        int ox = OTHERS.x() + Golden.S8;
        g.drawString(font, Component.translatable("gui.skyabyss.party.others"), ox, Golden.textY(OTHERS.y() + 2, ROW), GOLD, false);
        List<String> others = candidates(s);
        candPanel.setViewport(ox, CAND_Y, OTHERS.right() - 2 - ox, CAND_H);
        candPanel.setContentHeight(others.size() * PROW);
        if (others.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.nobody"), ox, Golden.textY(CAND_Y, PROW), SOFT, false);
        }
        candPanel.begin(g);
        for (int i = 0; i < others.size(); i++) {
            int oy = candPanel.top() + i * PROW;
            if (oy + PROW < candPanel.y() || oy > candPanel.y() + CAND_H) {
                continue;
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(others.get(i)), ox, Golden.textY(oy, PROW),
                    OTHERS.w() - 2 * Golden.S8 - Golden.S34 - Golden.S5, TEXT, false);
        }
        candPanel.end(g);
        candPanel.drawBar(g, 0x30FFFFFF, 0x90F2C14E);
        for (int i = 0; i < inviteButtons.size(); i++) {
            Button b = inviteButtons.get(i);
            int oy = candPanel.top() + i * PROW;
            b.setY(oy + (PROW - Golden.BTN) / 2);
            b.visible = oy >= candPanel.y() && oy + PROW <= candPanel.y() + CAND_H;
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
        return fit.screenRect(0, 0, Golden.L_W, Golden.L_H);
    }
}
