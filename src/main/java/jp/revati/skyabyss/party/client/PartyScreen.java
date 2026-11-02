package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// 「物語と仲間」の画面 (O)。上に物語の段階とレベルの上限、左に仲間、右に招待できる人。
// 操作はサーバーへ送るだけ (検めるのはサーバー)。届いた状態で描き直す
public final class PartyScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int TEXT = 0xE8F4FF;
    private static final int SOFT = 0xA8B4C4;
    private static final int GOLD = 0xF2C14E;
    private static final int MAX_LIST = 8;

    // 招待できる人の一覧の高さ (6 行ぶん)。下の「完了」の釦 (top + 206) にかからない
    private static final int CAND_H = 104;

    private int left;
    private int top;
    private final ScrollPanel candPanel = new ScrollPanel(16);
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
        PartyNetwork.Sync s = PartyClient.state();
        left = width / 2 - 160;
        top = Math.max(10, height / 2 - 110);
        String self = minecraft.player.getUUID().toString();
        // 招待が届いている
        if (!s.inviteFrom().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.accept"), b -> send(PartyNetwork.Action.ACCEPT, ""))
                    .bounds(left + 200, top + 58, 56, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.decline"), b -> send(PartyNetwork.Action.DECLINE, ""))
                    .bounds(left + 262, top + 58, 56, 18).build());
        }
        // 仲間 (左)
        boolean leader = amLeader(s);
        int y = top + 96;
        for (PartyNetwork.Member m : s.members()) {
            if (leader && !m.id().equals(self)) {
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.promote"), b -> send(PartyNetwork.Action.PROMOTE, m.name()))
                        .bounds(left + 104, y - 2, 26, 14).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.promote_tip"))).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.kick"), b -> send(PartyNetwork.Action.KICK, m.name()))
                        .bounds(left + 132, y - 2, 26, 14).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                                Component.translatable("gui.skyabyss.party.kick_tip"))).build());
            }
            y += 16;
        }
        if (!s.members().isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.leave"), b -> send(PartyNetwork.Action.LEAVE, ""))
                    .bounds(left, top + 96 + 16 * 4 + 6, 70, 18).build());
        }
        // 招待できる人 (右)
        inviteButtons.clear();
        if (leader && s.members().size() < 4) {
            for (String name : candidates(s)) {
                inviteButtons.add(addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.invite"),
                        b -> send(PartyNetwork.Action.INVITE, name)).bounds(left + 262, top + 96, 50, 14).build()));
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 40, top + 206, 80, 18).build());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PartyNetwork.Sync s = PartyClient.state();
        g.drawString(font, title, left, top - 2, GOLD, false);
        // 物語
        Component next = s.nextTitan().isEmpty() ? Component.translatable("gui.skyabyss.party.story_done")
                : s.nextTitan().equals("?shadow") ? Component.translatable("gui.skyabyss.party.story_next_shadow")
                : s.nextTitan().equals("?unseen") ? Component.translatable("gui.skyabyss.party.story_next_unseen")
                : Component.translatable("gui.skyabyss.party.story_next", Component.translatable("progress.skyabyss.titan." + s.nextTitan()));
        g.drawString(font, Component.translatable("gui.skyabyss.party.story", s.stage(), 4, s.cap()), left, top + 14, TEXT, false);
        g.drawString(font, next, left, top + 26, SOFT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.party.world", s.worldStage(), 4), left, top + 38, SOFT, false);
        if (!s.inviteFrom().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.invite_from", s.inviteFrom()), left, top + 63, GOLD, false);
        }
        // 仲間
        g.drawString(font, Component.translatable("gui.skyabyss.party.members", s.members().size(), 4, s.sharePercent()), left, top + 82, GOLD, false);
        int y = top + 96;
        if (s.members().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.alone"), left, y, SOFT, false);
        }
        for (PartyNetwork.Member m : s.members()) {
            String line = (m.leader() ? "★" : "・") + m.name();
            // 転生した人は、名前に段の色の枠と記号、レベルは Lv 50+n (docs/design/31 の C15)
            int frame = jp.revati.skyabyss.client.rebirth.RebirthClient.frameStyle(m.rebirths());
            if (frame > 0) {
                jp.revati.skyabyss.client.rebirth.RebirthClient.drawFrame(g, left - 3, y - 3, 66, 13, m.rebirths());
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(line), left, y, 60, m.online() ? TEXT : 0x707070, false);
            if (m.online()) {
                if (m.rebirths() > 0) {
                    long t = jp.revati.skyabyss.client.rebirth.RebirthClient.timeMs();
                    boolean still = jp.revati.skyabyss.client.rebirth.RebirthClient.still();
                    Component head = jp.revati.skyabyss.rebirth.RebirthText.symbols(m.rebirths(), t, still).append(" ")
                            .append(jp.revati.skyabyss.rebirth.RebirthText.level(m.level(), m.rebirths(), t, still));
                    Component hp = Component.literal(" " + jp.revati.skyabyss.Hp.number(m.health()) + "/" + jp.revati.skyabyss.Hp.of(m.maxHealth()));
                    // 横に収まらなければ体力は省く (隣の列と重ねない)
                    g.drawString(font, font.width(head) + font.width(hp) <= 108 ? head.copy().append(hp) : head, left + 68, y, SOFT, false);
                } else {
                    g.drawString(font, "Lv" + m.level() + " " + jp.revati.skyabyss.Hp.number(m.health()) + "/" + jp.revati.skyabyss.Hp.of(m.maxHealth()), left + 68, y,
                            m.sameDim() ? SOFT : 0x707070, false);
                }
            }
            y += 16;
        }
        // 招待できる人 (目盛りつきの送り。招待の釦はその行に合わせて動かし、枠の外の釦は隠す)
        g.drawString(font, Component.translatable("gui.skyabyss.party.others"), left + 180, top + 82, GOLD, false);
        List<String> others = candidates(s);
        candPanel.setViewport(left + 180, top + 96, 140, CAND_H);
        candPanel.setContentHeight(others.size() * 16);
        if (others.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.nobody"), left + 180, top + 96, SOFT, false);
        }
        candPanel.begin(g);
        for (int i = 0; i < others.size(); i++) {
            int oy = candPanel.top() + i * 16;
            if (oy + 16 < candPanel.y() || oy > candPanel.y() + CAND_H) {
                continue;
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(others.get(i)), left + 180, oy, 82, TEXT, false);
        }
        candPanel.end(g);
        candPanel.drawBar(g, 0x30FFFFFF, 0x90F2C14E);
        for (int i = 0; i < inviteButtons.size(); i++) {
            Button b = inviteButtons.get(i);
            // 行の上端で見せるか決める (釦は行より 1 上に置くので、釦の上端で決めると一行目がいつも枠の外になって消えていた)
            int oy = candPanel.top() + i * 16;
            b.setY(oy - 1);
            b.visible = oy >= candPanel.y() && oy + 12 <= candPanel.y() + CAND_H;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return candPanel.mouseScrolled(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return candPanel.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return candPanel.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return candPanel.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // ほかの画面 (順位の石・墓・記録) と同じ枠: 濃い板に、上と下の細い明るい線。仲間と招きの間に区切りの線 (2026-10-28 の点検)
        g.fill(left - 8, top - 8, left + 328, top + 200, 0xC0101418);
        g.fill(left - 7, top - 7, left + 327, top - 6, 0x60FFFFFF);
        g.fill(left - 7, top + 198, left + 327, top + 199, 0x60FFFFFF);
        g.fill(left, top + 84, left + 320, top + 85, 0x30E6B84A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int[] textBounds() {
        return new int[] {left - 8, top - 8, 336, 208};
    }
}
