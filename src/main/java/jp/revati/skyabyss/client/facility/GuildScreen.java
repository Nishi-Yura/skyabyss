package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.guild.GuildAchievements;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.GuildEmblems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

// ギルドの画面 (組合の受付の書記から。docs/design/35 の 3.4)。
//   入っていないとき: 左 = 作る (名前・印・旗の地の色と模様の色)、右 = ギルドの一覧 (申請 / 入る)
//   入っているとき: 旗と名前、つまみ 4 つ
//     人   : 人の一覧 (★ 長・☆ 副長、いる人は点)。長は副長にする / 戻す・外す・長を譲る (★。二度押し)・「誰でも入れる」、
//            長と副長は申請を受ける・断る
//     頼み : 今週の頼み (絵・進み・自分の分・多く納めた人)、「納める」
//     旗   : 倉庫を開く・取り出せる位 (長が切り替え)、旗を受け取る、旗の見本 (本物の旗の品)。長は旗を作り直す (印・色・模様・名札の色)
//     功績 : ギルドの功績の一覧 (進み・ほうびの模様と名札の色)
//   下に「抜ける」(長は抜けられないので「解散する」。二度押し)
// 決めるのはサーバー (GuildService)。押すたびに新しい中身が届いて描き直す
public class GuildScreen extends Screen {
    private static final int W = 320;
    private static final int H = 214;
    private static final int ROW_H = 14;

    private FacilityNetwork.GuildView view;
    private int x0, y0;
    private EditBox name;
    private EditBox tag;
    private int color = DyeColor.BLUE.getId();
    private int accent = DyeColor.WHITE.getId();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    // 入っているときのつまみ (0 人・1 頼み・2 旗・3 功績)
    private int tab;
    private EditBox emblemTag;
    private String pattern = "";
    private String plate = "";
    // 二度押しの確かめ (op + 相手。4 秒)
    private String confirm = "";
    private long confirmUntil;
    private Button bottom;
    private net.minecraft.world.item.ItemStack bannerCache = net.minecraft.world.item.ItemStack.EMPTY;
    private String bannerKey = "";

    public GuildScreen(FacilityNetwork.GuildView view) {
        super(Component.translatable("gui.skyabyss.guild.title"));
        this.view = view;
        if (view.tab() >= 0) {
            tab = Math.min(3, view.tab());
        }
    }

    public void update(FacilityNetwork.GuildView v) {
        boolean changed = v.mine().isEmpty() != view.mine().isEmpty() || v.rank() != view.rank() || v.open() != view.open()
                || v.takeRank() != view.takeRank() || v.req().done() != view.req().done();
        view = v;
        if (v.tab() >= 0 && v.tab() != tab) {
            tab = Math.min(3, v.tab());
            changed = true;
        }
        if (changed) {
            rebuildWidgets();
        }
    }

    public FacilityNetwork.GuildView view() {
        return view;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        bottom = null;
        if (view.mine().isEmpty()) {
            String oldName = name == null ? "" : name.getValue();
            String oldTag = tag == null ? "" : tag.getValue();
            name = new EditBox(font, x0 + 16, y0 + 42, 130, 16, Component.translatable("gui.skyabyss.guild.name"));
            name.setMaxLength(GuildData.NAME_MAX);
            name.setValue(oldName);
            addRenderableWidget(name);
            tag = new EditBox(font, x0 + 16, y0 + 76, 50, 16, Component.translatable("gui.skyabyss.guild.tag"));
            tag.setMaxLength(GuildData.TAG_MAX);
            tag.setValue(oldTag);
            addRenderableWidget(tag);
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.create"), b -> send("create", "", name.getValue(), tag.getValue(), false))
                    .bounds(x0 + 16, y0 + H - 34, 130, 18).build());
        } else {
            if (view.leader()) {
                bottom = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.disband"), b -> {
                    if (confirmed("disband", "")) {
                        send("disband", "", "", "", false);
                    }
                }).bounds(x0 + W - 96, y0 + H - 28, 80, 18).build());
            } else {
                bottom = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.leave"), b -> send("leave", "", "", "", false))
                        .bounds(x0 + W - 76, y0 + H - 28, 60, 18).build());
            }
            if (tab == 0 && view.leader()) {
                addRenderableWidget(Button.builder(Component.translatable(view.open() ? "gui.skyabyss.guild.open_on" : "gui.skyabyss.guild.open_off"),
                        b -> send("open", "", "", "", !view.open())).bounds(x0 + 16, y0 + H - 28, 120, 18).build());
            }
            if (tab == 1 && !view.req().kill() && !view.req().done()) {
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.deliver"), b -> send("deliver", "", "", "", false))
                        .bounds(x0 + 16, y0 + H - 28, 100, 18).build());
            }
            if (tab == 2) {
                int x = x0 + 16;
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.storage"), b -> send("storage", "", "", "", false))
                        .bounds(x, y0 + 64, 128, 16).build());
                Button take = Button.builder(Component.translatable("gui.skyabyss.guild.take_rank." + view.takeRank()),
                        b -> send("take", "", "", "", false, (view.takeRank() + 1) % 3, accent)).bounds(x, y0 + 84, 128, 16).build();
                take.active = view.leader();
                addRenderableWidget(take);
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.banner", view.banner()), b -> send("banner", "", "", "", false))
                        .bounds(x, y0 + 104, 128, 16).build());
                if (view.leader()) {
                    if (emblemTag == null && myRow() != null) {
                        // 作り直しの見本は今の旗から
                        color = myRow().color();
                        accent = myRow().accent();
                        pattern = view.pattern();
                        plate = view.plate();
                    }
                    String old = emblemTag == null ? myTag() : emblemTag.getValue();
                    emblemTag = new EditBox(font, x, y0 + 136, 40, 14, Component.translatable("gui.skyabyss.guild.tag"));
                    emblemTag.setMaxLength(GuildData.TAG_MAX);
                    emblemTag.setValue(old);
                    addRenderableWidget(emblemTag);
                    addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.emblem"),
                            b -> send("emblem", pattern, plate, emblemTag.getValue(), false)).bounds(x + 46, y0 + 135, 82, 16).build());
                }
            }
        }
    }

    // 二度押しの確かめ: 一度目は印を付けて false、4 秒のうちにもう一度で true
    private boolean confirmed(String op, String who) {
        String key = op + ":" + who;
        long now = net.minecraft.Util.getMillis();
        if (key.equals(confirm) && now < confirmUntil) {
            confirm = "";
            return true;
        }
        confirm = key;
        confirmUntil = now + 4000;
        return false;
    }

    private boolean confirming(String op, String who) {
        return (op + ":" + who).equals(confirm) && net.minecraft.Util.getMillis() < confirmUntil;
    }

    // 本物の旗の品 (模様つき) を大きくして描く
    private void bannerItem(GuiGraphics g, int x, int y, float scale, String pat, int c, int a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String key = pat + "/" + c + "/" + a;
        if (!key.equals(bannerKey)) {
            bannerCache = jp.revati.skyabyss.facility.guild.GuildEmblems.banner(mc.level.registryAccess(), pat, c, a);
            bannerKey = key;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(bannerCache, 0, 0);
        g.pose().popPose();
    }

    private String myTag() {
        FacilityNetwork.GuildRow me = myRow();
        return me == null ? "" : me.tag();
    }

    private FacilityNetwork.GuildRow myRow() {
        for (FacilityNetwork.GuildRow r : view.guilds()) {
            if (r.id().equals(view.mine())) {
                return r;
            }
        }
        return null;
    }

    private void send(String op, String target, String text, String tagText, boolean flag, int c, int a) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new FacilityNetwork.GuildAction(op, target, text, tagText, c, a, flag));
        }
    }

    private void send(String op, String target, String text, String tagText, boolean flag) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new FacilityNetwork.GuildAction(op, target, text, tagText, color, accent, flag));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        FacilityUi.panel(g, x0, y0, W, H);
        Font font = this.font;
        FacilityUi.gem(g, x0 + 12, y0 + 13);
        g.drawString(font, title, x0 + 18, y0 + 9, FacilityUi.TEXT, false);
        hits.clear();
        hitActions.clear();
        if (view.mine().isEmpty()) {
            renderCreate(g, font, mouseX, mouseY);
            renderList(g, font, mouseX, mouseY);
        } else {
            renderMine(g, font, mouseX, mouseY);
        }
        if (bottom != null && view.leader()) {
            bottom.setMessage(Component.translatable(confirming("disband", "") ? "gui.skyabyss.guild.disband_sure" : "gui.skyabyss.guild.disband"));
        }
        for (var r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderCreate(GuiGraphics g, Font font, int mx, int my) {
        int x = x0 + 16;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.make_head"), x, y0 + 22, FacilityUi.TEAL, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.name"), x, y0 + 33, FacilityUi.DIM, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.tag"), x, y0 + 66, FacilityUi.DIM, false);
        // 名札の見本
        String t = tag == null || tag.getValue().isBlank() ? "?" : tag.getValue().toUpperCase(java.util.Locale.ROOT);
        drawBadge(g, font, x + 58, y0 + 80, t, color, accent);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.color"), x, y0 + 98, FacilityUi.DIM, false);
        swatches(g, x, y0 + 108, color, true, mx, my);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.accent"), x, y0 + 132, FacilityUi.DIM, false);
        swatches(g, x, y0 + 142, accent, false, mx, my);
        // 旗の見本 (既定の模様)
        bannerItem(g, x + 100, y0 + 96, 2.0f, jp.revati.skyabyss.facility.guild.GuildEmblems.DEFAULT, color, accent);
    }

    private void swatches(GuiGraphics g, int x, int y, int selected, boolean base, int mx, int my) {
        for (int i = 0; i < 16; i++) {
            int sx = x + (i % 8) * 11;
            int sy = y + (i / 8) * 11;
            int rgb = 0xFF000000 | DyeColor.byId(i).getTextureDiffuseColor();
            g.fill(sx, sy, sx + 9, sy + 9, rgb);
            g.renderOutline(sx - 1, sy - 1, 11, 11, i == selected ? FacilityUi.GOLD : FacilityUi.LINE);
            int id = i;
            hit(sx, sy, 9, 9, () -> {
                if (base) {
                    color = id;
                } else {
                    accent = id;
                }
            });
        }
    }

    private void renderList(GuiGraphics g, Font font, int mx, int my) {
        int x = x0 + 160;
        int y = y0 + 22;
        int w = W - 176;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.list_head"), x, y, FacilityUi.TEAL, false);
        int ly = y + 12;
        FacilityUi.well(g, x, ly, w, 11 * ROW_H);
        if (view.guilds().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.list_empty"), x + 4, ly + 4, FacilityUi.DIM, false);
        }
        for (int i = 0; i < view.guilds().size() && i < 11; i++) {
            FacilityNetwork.GuildRow r = view.guilds().get(i);
            int ry = ly + i * ROW_H;
            int bx = drawBadge(g, font, x + 3, ry + 3, r.tag(), r.color(), r.accent(), r.plate());
            g.drawString(font, font.plainSubstrByWidth(r.name(), w - (bx - x) - 64), bx + 2, ry + 3, FacilityUi.TEXT, false);
            g.drawString(font, r.members() + "/" + view.max(), x + w - 58, ry + 3, FacilityUi.DIM, false);
            Component act = Component.translatable(r.applied() ? "gui.skyabyss.guild.applied" : r.open() ? "gui.skyabyss.guild.join" : "gui.skyabyss.guild.apply");
            int aw = font.width(act) + 6;
            int ax = x + w - aw - 2;
            boolean hover = mx >= ax && mx < ax + aw && my >= ry + 1 && my < ry + ROW_H - 1;
            g.fill(ax, ry + 1, ax + aw, ry + ROW_H - 1, r.applied() ? 0xFF161B22 : hover ? 0xFF2A5E6A : 0xFF1E3A40);
            g.drawString(font, act, ax + 3, ry + 3, r.applied() ? FacilityUi.DIM : FacilityUi.TEXT, false);
            if (!r.applied()) {
                hit(ax, ry + 1, aw, ROW_H - 2, () -> send("apply", r.id(), "", "", false));
            }
        }
    }

    private void renderMine(GuiGraphics g, Font font, int mx, int my) {
        FacilityNetwork.GuildRow me = myRow();
        if (me == null) {
            return;
        }
        int x = x0 + 16;
        bannerItem(g, x - 3, y0 + 22, 1.7f, view.pattern(), me.color(), me.accent());
        int bx = drawBadge(g, font, x + 28, y0 + 26, me.tag(), me.color(), me.accent(), me.plate());
        g.drawString(font, font.plainSubstrByWidth(me.name(), 100), bx + 4, y0 + 26, FacilityUi.TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.members", view.members().size(), view.max()), x + 28, y0 + 40, FacilityUi.DIM, false);
        // つまみ
        String[] tabs = {"gui.skyabyss.guild.tab.members", "gui.skyabyss.guild.tab.request", "gui.skyabyss.guild.tab.banner",
                "gui.skyabyss.guild.tab.achievements"};
        int tx = x0 + W - 16;
        for (int i = tabs.length - 1; i >= 0; i--) {
            Component c = Component.translatable(tabs[i]);
            int tw = font.width(c) + 10;
            tx -= tw + 2;
            boolean on = tab == i;
            boolean hover = mx >= tx && mx < tx + tw && my >= y0 + 22 && my < y0 + 36;
            g.fill(tx, y0 + 22, tx + tw, y0 + 36, on ? 0xFF2A5E6A : hover ? FacilityUi.ROW_HOVER : FacilityUi.ROW);
            g.renderOutline(tx, y0 + 22, tw, 14, on ? FacilityUi.TEAL : FacilityUi.LINE);
            g.drawString(font, c, tx + 5, y0 + 25, on ? FacilityUi.TEXT : FacilityUi.DIM, false);
            int id = i;
            hit(tx, y0 + 22, tw, 14, () -> {
                if (tab != id) {
                    tab = id;
                    rebuildWidgets();
                }
            });
        }
        Component rank = Component.translatable("gui.skyabyss.guild.rank." + view.rank());
        g.drawString(font, rank, x0 + W - 16 - font.width(rank), y0 + 40, FacilityUi.GOLD, false);
        switch (tab) {
            case 1 -> renderRequest(g, font);
            case 2 -> renderBanner(g, font, mx, my);
            case 3 -> renderAchievements(g, font);
            default -> renderMembers(g, font, mx, my);
        }
    }

    private void renderMembers(GuiGraphics g, Font font, int mx, int my) {
        int x = x0 + 16;
        int ly = y0 + 62;
        boolean officer = view.rank() >= 1;
        int lw = officer ? 176 : W - 32;
        FacilityUi.well(g, x, ly, lw, 8 * ROW_H);
        for (int i = 0; i < view.members().size() && i < 8; i++) {
            FacilityNetwork.MemberRow m = view.members().get(i);
            int ry = ly + i * ROW_H;
            g.fill(x + 3, ry + 5, x + 6, ry + 8, m.online() ? FacilityUi.TEAL : 0xFF3A3F48);
            String mark = m.rank() == 2 ? "★ " : m.rank() == 1 ? "☆ " : "";
            g.drawString(font, font.plainSubstrByWidth(mark + m.name(), view.leader() ? lw - 98 : lw - 14), x + 10, ry + 3,
                    m.rank() >= 1 ? FacilityUi.GOLD : FacilityUi.TEXT, false);
            if (view.leader() && m.rank() < 2) {
                Component k = Component.translatable("gui.skyabyss.guild.kick");
                int kw = font.width(k) + 6;
                int kx = x + lw - kw - 2;
                g.fill(kx, ry + 1, kx + kw, ry + ROW_H - 1, mx >= kx && mx < kx + kw && my >= ry && my < ry + ROW_H ? 0xFF5A2A38 : 0xFF2A1C22);
                g.drawString(font, k, kx + 3, ry + 3, FacilityUi.LACK, false);
                hit(kx, ry + 1, kw, ROW_H - 2, () -> send("kick", m.id().toString(), "", "", false));
                Component o = Component.translatable(m.rank() == 1 ? "gui.skyabyss.guild.demote" : "gui.skyabyss.guild.promote");
                int ow = font.width(o) + 6;
                int ox = kx - ow - 2;
                g.fill(ox, ry + 1, ox + ow, ry + ROW_H - 1, mx >= ox && mx < ox + ow && my >= ry && my < ry + ROW_H ? 0xFF2A5E6A : 0xFF1E3A40);
                g.drawString(font, o, ox + 3, ry + 3, FacilityUi.TEXT, false);
                hit(ox, ry + 1, ow, ROW_H - 2, () -> send("officer", m.id().toString(), "", "", m.rank() != 1));
                // 長を譲る (★。二度押し)
                boolean sure = confirming("transfer", m.id().toString());
                String star = sure ? "?" : "★";
                int sx = ox - 17;
                g.fill(sx, ry + 1, sx + 15, ry + ROW_H - 1, sure ? 0xFF5A4A20 : mx >= sx && mx < sx + 15 && my >= ry && my < ry + ROW_H ? 0xFF4A4426 : 0xFF2A2618);
                g.drawString(font, star, sx + 4, ry + 3, FacilityUi.GOLD, false);
                hit(sx, ry + 1, 15, ROW_H - 2, () -> {
                    if (confirmed("transfer", m.id().toString())) {
                        send("transfer", m.id().toString(), "", "", false);
                    }
                });
            }
        }
        if (view.leader()) {
            g.drawString(font, Component.translatable(confirm.startsWith("transfer:") && net.minecraft.Util.getMillis() < confirmUntil
                    ? "gui.skyabyss.guild.transfer_sure" : "gui.skyabyss.guild.leader_hint"), x, ly + 8 * ROW_H + 3, FacilityUi.DIM, false);
        }
        if (view.members().size() > 8) {
            g.drawString(font, "…", x + lw - 10, ly + 8 * ROW_H + 2, FacilityUi.DIM, false);
        }
        // 長と副長: 申請の一覧
        if (officer) {
            int ax = x + lw + 8;
            int aw = W - 16 - (ax - x0);
            g.drawString(font, Component.translatable("gui.skyabyss.guild.applicants"), ax, ly - 11, FacilityUi.TEAL, false);
            FacilityUi.well(g, ax, ly, aw, 8 * ROW_H);
            if (view.applicants().isEmpty()) {
                g.drawString(font, Component.translatable("gui.skyabyss.guild.no_applicants"), ax + 4, ly + 4, FacilityUi.DIM, false);
            }
            for (int i = 0; i < view.applicants().size() && i < 8; i++) {
                FacilityNetwork.MemberRow m = view.applicants().get(i);
                int ry = ly + i * ROW_H;
                g.drawString(font, font.plainSubstrByWidth(m.name(), aw - 40), ax + 4, ry + 3, FacilityUi.TEXT, false);
                int okx = ax + aw - 34;
                g.fill(okx, ry + 1, okx + 15, ry + ROW_H - 1, 0xFF1E3A40);
                g.drawString(font, "✔", okx + 4, ry + 3, FacilityUi.TEAL, false);
                hit(okx, ry + 1, 15, ROW_H - 2, () -> send("accept", m.id().toString(), "", "", false));
                int nox = ax + aw - 17;
                g.fill(nox, ry + 1, nox + 15, ry + ROW_H - 1, 0xFF2A1C22);
                g.drawString(font, "✘", nox + 4, ry + 3, FacilityUi.LACK, false);
                hit(nox, ry + 1, 15, ROW_H - 2, () -> send("deny", m.id().toString(), "", "", false));
            }
        }
    }

    private void renderRequest(GuiGraphics g, Font font) {
        FacilityNetwork.ReqView r = view.req();
        int x = x0 + 16;
        int y = y0 + 56;
        int w = W - 32;
        FacilityUi.well(g, x, y, w, 122);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_head"), x + 4, y + 4, FacilityUi.TEAL, false);
        if (r.id().isEmpty()) {
            return;
        }
        g.renderItem(r.icon(), x + 4, y + 16);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req." + r.id(), r.target()), x + 24, y + 17, FacilityUi.TEXT, false);
        g.drawString(font, Component.translatable(r.kill() ? "gui.skyabyss.guild.req_how_kill" : "gui.skyabyss.guild.req_how_give"), x + 24, y + 27,
                FacilityUi.DIM, false);
        // 進みの帯
        int bw = w - 8;
        int by = y + 42;
        g.fill(x + 4, by, x + 4 + bw, by + 6, 0xFF1C222C);
        int fill = r.target() <= 0 ? 0 : Math.min(bw, bw * r.progress() / r.target());
        g.fill(x + 4, by, x + 4 + fill, by + 6, r.done() ? FacilityUi.GOLD : FacilityUi.TEAL);
        g.renderOutline(x + 3, by - 1, bw + 2, 8, FacilityUi.LINE);
        g.drawString(font, Component.translatable(r.done() ? "gui.skyabyss.guild.req_done" : "gui.skyabyss.guild.req_progress", r.progress(), r.target()),
                x + 4, by + 10, r.done() ? FacilityUi.GOLD : FacilityUi.TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_mine", r.mine()), x + w / 2, by + 10, FacilityUi.DIM, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_top"), x + 4, by + 24, FacilityUi.TEAL, false);
        for (int i = 0; i < r.topNames().size(); i++) {
            g.drawString(font, font.plainSubstrByWidth((i + 1) + ". " + r.topNames().get(i) + "  " + r.topCounts().get(i), w / 2 - 8),
                    x + 8 + (i / 3) * (w / 2), by + 35 + (i % 3) * 10, FacilityUi.TEXT, false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_reward"), x + 4, y + 122 - 11, FacilityUi.DIM, false);
    }

    private void renderBanner(GuiGraphics g, Font font, int mx, int my) {
        int x = x0 + 16;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.house_head"), x, y0 + 54, FacilityUi.TEAL, false);
        if (view.leader()) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.emblem_head"), x, y0 + 125, FacilityUi.DIM, false);
            swatches(g, x, y0 + 155, color, true, mx, my);
            swatches(g, x, y0 + 178, accent, false, mx, my);
        }
        // 右: 旗の見本 (本物の旗の品)・模様・名札の色
        java.util.Set<String> unlocked = new java.util.HashSet<>(view.unlocked());
        int rx = x0 + 156;
        String pat = view.leader() ? pattern : view.pattern();
        int c = view.leader() ? color : myRow() == null ? 0 : myRow().color();
        int a = view.leader() ? accent : myRow() == null ? 0 : myRow().accent();
        String pl = view.leader() ? plate : view.plate();
        FacilityUi.well(g, rx, y0 + 56, 52, 52);
        bannerItem(g, rx + 2, y0 + 58, 3.0f, pat, c, a);
        GuildEmblems.Emblem em = GuildEmblems.emblem(pat);
        int px = rx + 60;
        int pw = x0 + W - 16 - px;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.pattern_head"), px, y0 + 56, FacilityUi.DIM, false);
        boolean locked = em.unlock() != null && !unlocked.contains(em.unlock());
        String pname = Component.translatable(em.nameKey()).getString();
        if (view.leader()) {
            arrow(g, px, y0 + 68, "◀", mx, my, () -> cyclePattern(-1));
            arrow(g, px + pw - 12, y0 + 68, "▶", mx, my, () -> cyclePattern(1));
            g.drawCenteredString(font, font.plainSubstrByWidth(pname, pw - 28), px + pw / 2, y0 + 70, locked ? FacilityUi.DIM : FacilityUi.TEXT);
        } else {
            g.drawString(font, font.plainSubstrByWidth(pname, pw), px, y0 + 70, FacilityUi.TEXT, false);
        }
        if (locked) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("gui.skyabyss.guild.locked_by",
                    Component.translatable("gui.skyabyss.guild.ach." + em.unlock())), pw);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                g.drawString(font, lines.get(i), px, y0 + 84 + i * 10, FacilityUi.LACK, false);
            }
        } else if (em.unlock() != null) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.special"), px, y0 + 84, FacilityUi.GOLD, false);
        }
        String idx = (GuildEmblems.ALL.indexOf(em) + 1) + " / " + GuildEmblems.ALL.size();
        g.drawString(font, idx, x0 + W - 16 - font.width(idx), y0 + 56, FacilityUi.DIM, false);
        // 名札の色 (はじめは旗の地の色。特別な色は功績で開く)
        g.drawString(font, Component.translatable("gui.skyabyss.guild.plate_head"), rx, y0 + 114, FacilityUi.DIM, false);
        int cx = rx;
        int cy = y0 + 125;
        chip(g, font, cx, cy, 0xFF000000 | GuildData.textColor(c), pl.isEmpty(), false, "");
        cx += 22;
        for (GuildEmblems.Plate p : GuildEmblems.PLATES) {
            chip(g, font, cx, cy, 0xFF000000 | p.rgb(), p.id().equals(pl), !unlocked.contains(p.unlock()), p.id());
            cx += 22;
        }
        // 名札の見本
        String t = view.leader() ? (emblemTag == null || emblemTag.getValue().isBlank() ? "?" : emblemTag.getValue().toUpperCase(java.util.Locale.ROOT))
                : myTag();
        int bx = drawBadge(g, font, rx, y0 + 142, t, c, a, pl);
        g.drawString(font, Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName(), bx + 2, y0 + 142,
                FacilityUi.TEXT, false);
        List<net.minecraft.util.FormattedCharSequence> rule = font.split(Component.translatable("gui.skyabyss.guild.banner_rule"), x0 + W - 16 - rx);
        for (int i = 0; i < rule.size() && i < 2; i++) {
            g.drawString(font, rule.get(i), rx, y0 + 156 + i * 10, FacilityUi.DIM, false);
        }
    }

    private void cyclePattern(int d) {
        List<GuildEmblems.Emblem> all = GuildEmblems.ALL;
        int i = all.indexOf(GuildEmblems.emblem(pattern));
        pattern = all.get(Math.floorMod(i + d, all.size())).id();
    }

    private void arrow(GuiGraphics g, int x, int y, String s, int mx, int my, Runnable run) {
        boolean hover = mx >= x && mx < x + 12 && my >= y && my < y + 12;
        g.fill(x, y, x + 12, y + 12, hover ? 0xFF2A5E6A : 0xFF1E3A40);
        g.drawCenteredString(font, s, x + 6, y + 2, FacilityUi.TEXT);
        hit(x, y, 12, 12, run);
    }

    // 名札の色の札 (開いていない色は暗く ✕)
    private void chip(GuiGraphics g, Font font, int x, int y, int rgb, boolean on, boolean lock, String id) {
        g.fill(x, y, x + 18, y + 10, lock ? 0xFF2A2E36 : rgb);
        g.renderOutline(x - 1, y - 1, 20, 12, on ? FacilityUi.GOLD : FacilityUi.LINE);
        if (lock) {
            g.drawCenteredString(font, "✕", x + 9, y + 1, FacilityUi.DIM);
        }
        if (view.leader() && !lock) {
            hit(x, y, 18, 10, () -> plate = id);
        }
    }

    private void renderAchievements(GuiGraphics g, Font font) {
        int x = x0 + 16;
        int y = y0 + 54;
        int w = W - 32;
        List<Integer> st = view.stats().size() >= 3 ? view.stats() : List.of(0, 0, 0);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.ach_head", st.get(0), st.get(1), st.get(2)), x, y, FacilityUi.TEAL, false);
        int ly = y + 11;
        List<GuildAchievements.Achievement> list = GuildAchievements.ALL;
        FacilityUi.well(g, x, ly, w, list.size() * 12 + 2);
        for (int i = 0; i < list.size(); i++) {
            GuildAchievements.Achievement a = list.get(i);
            int ry = ly + 2 + i * 12;
            boolean done = view.unlocked().contains(a.id());
            int v = Math.min(a.target(), st.get(a.kind().ordinal()));
            g.drawString(font, done ? "✔" : "・", x + 3, ry, done ? FacilityUi.GOLD : FacilityUi.DIM, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(a.nameKey()).getString(), 104), x + 13, ry,
                    done ? FacilityUi.TEXT : FacilityUi.DIM, false);
            StringBuilder rw = new StringBuilder();
            for (Component r : GuildAchievements.rewards(a.id())) {
                if (!rw.isEmpty()) {
                    rw.append("・");
                }
                rw.append(r.getString());
            }
            g.drawString(font, font.plainSubstrByWidth(rw.toString(), w - 170), x + 120, ry, done ? FacilityUi.GOLD : FacilityUi.DIM, false);
            String prog = v + "/" + a.target();
            g.drawString(font, prog, x + w - 4 - font.width(prog), ry, done ? FacilityUi.TEAL : FacilityUi.TEXT, false);
        }
    }

    // 「[印]」を旗の色で描き、右端の x を返す
    static int drawBadge(GuiGraphics g, Font font, int x, int y, String tag, int color, int accent) {
        return drawBadge(g, font, x, y, tag, color, accent, "");
    }

    // plate = 名札の特別な色 ("" = 旗の地の色)
    static int drawBadge(GuiGraphics g, Font font, int x, int y, String tag, int color, int accent, String plate) {
        int c = 0xFF000000 | new jp.revati.skyabyss.facility.guild.GuildBadge("x", tag, color, accent, plate).textColor();
        int a = 0xFF000000 | GuildData.textColor(accent);
        g.drawString(font, "[", x, y, a, false);
        int px = x + font.width("[");
        g.drawString(font, tag, px, y, c, false);
        px += font.width(tag);
        g.drawString(font, "]", px, y, a, false);
        return px + font.width("]");
    }

    private void hit(int x, int y, int w, int h, Runnable r) {
        hits.add(new int[]{x, y, w, h});
        hitActions.add(r);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < hits.size(); i++) {
                int[] r = hits.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    hitActions.get(i).run();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.4f));
                    return true;
                }
            }
        }
        return false;
    }
}
