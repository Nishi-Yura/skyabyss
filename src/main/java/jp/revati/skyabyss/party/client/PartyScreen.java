package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public final class PartyScreen extends Screen {
    private static final int TEXT = 0xE8F4FF;
    private static final int SOFT = 0xA8B4C4;
    private static final int GOLD = 0xF2C14E;
    private static final int MAX_LIST = 8;

    private int left;
    private int top;

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
        if (leader && s.members().size() < 4) {
            y = top + 96;
            for (String name : candidates(s)) {
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.party.invite"), b -> send(PartyNetwork.Action.INVITE, name))
                        .bounds(left + 266, y - 2, 52, 14).build());
                y += 16;
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
        g.drawCenteredString(font, title, width / 2, top - 2, GOLD);
        // 物語
        Component next = s.nextTitan().isEmpty() ? Component.translatable("gui.skyabyss.party.story_done")
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
            g.drawString(font, font.plainSubstrByWidth(line, 60), left, y, m.online() ? TEXT : 0x707070, false);
            if (m.online()) {
                g.drawString(font, "Lv" + m.level() + " " + jp.revati.skyabyss.Hearts.number(m.health()) + "/" + jp.revati.skyabyss.Hearts.of(m.maxHealth()), left + 62, y,
                        m.sameDim() ? SOFT : 0x707070, false);
            }
            y += 16;
        }
        // 招待できる人
        g.drawString(font, Component.translatable("gui.skyabyss.party.others"), left + 180, top + 82, GOLD, false);
        y = top + 96;
        List<String> others = candidates(s);
        if (others.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.party.nobody"), left + 180, y, SOFT, false);
        }
        for (String name : others) {
            g.drawString(font, font.plainSubstrByWidth(name, 82), left + 180, y, TEXT, false);
            y += 16;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 8, top - 8, left + 328, top + 200, 0x90000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
