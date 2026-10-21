package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.facility.guild.GuildData;
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
//   入っているとき: 旗と名前、人の一覧 (長は印、いる人は点)、抜ける。長は申請を受ける・断る・外す・「誰でも入れる」
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

    public GuildScreen(FacilityNetwork.GuildView view) {
        super(Component.translatable("gui.skyabyss.guild.title"));
        this.view = view;
    }

    public void update(FacilityNetwork.GuildView v) {
        boolean changed = v.mine().isEmpty() != view.mine().isEmpty();
        view = v;
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
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.leave"), b -> send("leave", "", "", "", false))
                    .bounds(x0 + W - 96, y0 + H - 30, 80, 18).build());
            if (view.leader()) {
                addRenderableWidget(Button.builder(Component.translatable(view.open() ? "gui.skyabyss.guild.open_on" : "gui.skyabyss.guild.open_off"),
                        b -> send("open", "", "", "", !view.open())).bounds(x0 + 16, y0 + H - 30, 120, 18).build());
            }
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
        // 旗の見本
        banner(g, x + 104, y0 + 98, 24, 36, color, accent);
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
            int bx = drawBadge(g, font, x + 3, ry + 3, r.tag(), r.color(), r.accent());
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
        FacilityNetwork.GuildRow me = null;
        for (FacilityNetwork.GuildRow r : view.guilds()) {
            if (r.id().equals(view.mine())) {
                me = r;
            }
        }
        if (me == null) {
            return;
        }
        int x = x0 + 16;
        banner(g, x, y0 + 24, 20, 30, me.color(), me.accent());
        int bx = drawBadge(g, font, x + 28, y0 + 26, me.tag(), me.color(), me.accent());
        g.drawString(font, me.name(), bx + 4, y0 + 26, FacilityUi.TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.members", view.members().size(), view.max()), x + 28, y0 + 40, FacilityUi.DIM, false);
        // 人の一覧
        int ly = y0 + 60;
        int lw = view.leader() ? 160 : W - 32;
        FacilityUi.well(g, x, ly, lw, 9 * ROW_H);
        for (int i = 0; i < view.members().size() && i < 9; i++) {
            FacilityNetwork.MemberRow m = view.members().get(i);
            int ry = ly + i * ROW_H;
            g.fill(x + 3, ry + 5, x + 6, ry + 8, m.online() ? FacilityUi.TEAL : 0xFF3A3F48);
            g.drawString(font, (m.leader() ? "★ " : "") + m.name(), x + 10, ry + 3, m.leader() ? FacilityUi.GOLD : FacilityUi.TEXT, false);
            if (view.leader() && !m.leader()) {
                Component k = Component.translatable("gui.skyabyss.guild.kick");
                int kw = font.width(k) + 6;
                int kx = x + lw - kw - 2;
                g.fill(kx, ry + 1, kx + kw, ry + ROW_H - 1, mx >= kx && mx < kx + kw && my >= ry && my < ry + ROW_H ? 0xFF5A2A38 : 0xFF2A1C22);
                g.drawString(font, k, kx + 3, ry + 3, FacilityUi.LACK, false);
                hit(kx, ry + 1, kw, ROW_H - 2, () -> send("kick", m.id().toString(), "", "", false));
            }
        }
        if (view.members().size() > 9) {
            g.drawString(font, "…", x + 4, ly + 9 * ROW_H + 2, FacilityUi.DIM, false);
        }
        // 長: 申請の一覧
        if (view.leader()) {
            int ax = x + lw + 10;
            int aw = W - 16 - (ax - x0);
            g.drawString(font, Component.translatable("gui.skyabyss.guild.applicants"), ax, ly - 11, FacilityUi.TEAL, false);
            FacilityUi.well(g, ax, ly, aw, 9 * ROW_H);
            if (view.applicants().isEmpty()) {
                g.drawString(font, Component.translatable("gui.skyabyss.guild.no_applicants"), ax + 4, ly + 4, FacilityUi.DIM, false);
            }
            for (int i = 0; i < view.applicants().size() && i < 9; i++) {
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

    // 「[印]」を旗の色で描き、右端の x を返す
    static int drawBadge(GuiGraphics g, Font font, int x, int y, String tag, int color, int accent) {
        int c = 0xFF000000 | GuildData.textColor(color);
        int a = 0xFF000000 | GuildData.textColor(accent);
        g.drawString(font, "[", x, y, a, false);
        int px = x + font.width("[");
        g.drawString(font, tag, px, y, c, false);
        px += font.width(tag);
        g.drawString(font, "]", px, y, a, false);
        return px + font.width("]");
    }

    // 旗 (地の色に、模様の色の帯と縁)
    static void banner(GuiGraphics g, int x, int y, int w, int h, int color, int accent) {
        int base = 0xFF000000 | DyeColor.byId(color).getTextureDiffuseColor();
        int ac = 0xFF000000 | DyeColor.byId(accent).getTextureDiffuseColor();
        g.fill(x + w / 2 - 1, y - 3, x + w / 2 + 1, y + h + 2, 0xFF6E6654);
        g.fill(x - 2, y - 3, x + w + 2, y - 1, 0xFF6E6654);
        g.fill(x, y, x + w, y + h, base);
        g.fill(x, y + h / 3, x + w, y + h / 3 + Math.max(2, h / 8), ac);
        g.renderOutline(x, y, w, h, ac);
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
