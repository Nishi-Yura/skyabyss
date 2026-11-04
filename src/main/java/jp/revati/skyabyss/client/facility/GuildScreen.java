package jp.revati.skyabyss.client.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.JpWrap;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

// ギルドの画面 (組合の受付の書記から。docs/design/35 の 3.4)。黄金比の大きい板 (docs/design/56): 主の列 (左 207) と脇の列 (右 128)、下の段 21
//   入っていないとき: 主の列 = ギルドの一覧 (行 13。申請 / 入る)、脇の列 = 作る (名前・印・旗の地の色と模様の色・見本)。
//                     下の段の左に作れる条件 (岩鯨の心臓を止めた人。00 の 80)、右に「作る」
//   入っているとき: 見出しの帯に題・旗・印と名前、右につまみ 4 つ。下の段の左に人数と自分の位、右に「抜ける」(長は「解散する」。二度押し)
//     人   : 主 = 人の一覧 (★ 長・☆ 副長、いる人は点。長は 副長に / 戻す・外す・★ 長を譲る (二度押し))。
//            脇 = 長は「誰でも入れる」、長と副長は申請を受ける・断る。長には譲り方の手引き
//     頼み : 主 = 今週の頼み (絵・進み・自分の分・ほうび)、脇 = 多く納めた人と「納める」
//     旗   : 主 = 旗の見本 (本物の旗の品)・模様・名札の色・名札の見本、脇 = 倉庫を開く・取り出せる位・旗を受け取る、長は旗を作り直す (印・色)
//     功績 : 中身いっぱいに功績の一覧 (進み・ほうびの模様と名札の色)
// 決めるのはサーバー (GuildService)。押すたびに新しい中身が届いて描き直す
public class GuildScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box HEAD = LAYOUT.head();
    private static final Golden.Box BODY = LAYOUT.body();
    private static final Golden.Box MAJOR = BODY.major(true);
    private static final Golden.Box MINOR = BODY.minor(false);
    private static final Golden.Box FOOT_L = LAYOUT.footUnder(MAJOR);
    private static final Golden.Box FOOT_R = LAYOUT.footUnder(MINOR);
    private static final int ROW = Golden.ROW;
    private static final int BTN = Golden.BTN;
    // 欄の中の字の左 (欄の縁から 5) と幅
    private static final int MX = MAJOR.x() + Golden.S5;
    private static final int MW = MAJOR.w() - 2 * Golden.S5;
    private static final int NX = MINOR.x() + Golden.S5;
    private static final int NW = MINOR.w() - 2 * Golden.S5;
    // 一覧の行 (主の列。見出しの行のあと)
    private static final int LIST_Y = MAJOR.y() + 2 + ROW + Golden.S3;
    private static final int LIST_ROWS = (MAJOR.bottom() - 2 - LIST_Y) / ROW;
    private static final int MEM_Y = MAJOR.y() + 2;
    private static final int MEM_ROWS = (MAJOR.bottom() - 2 - MEM_Y) / ROW;
    // 見出しの帯のつまみ (幅 34・高さ 13)
    private static final int TAB_W = Golden.S34;
    private static final int TAB_GAP = Golden.S3;
    private static final String[] TABS = {"gui.skyabyss.guild.tab.members", "gui.skyabyss.guild.tab.request", "gui.skyabyss.guild.tab.banner",
            "gui.skyabyss.guild.tab.achievements"};
    // 色の見本の枠 (9 角・11 ずつ・8 列 2 段)
    private static final int SW_STEP = 11;
    private static final int SW_H = 2 * SW_STEP - 2;

    private FacilityNetwork.GuildView view;
    private EditBox name;
    private EditBox tag;
    private int color = DyeColor.BLUE.getId();
    private int accent = DyeColor.WHITE.getId();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    // 入っているときのつまみ (0 人・1 頼み・2 旗・3 功績)
    private int tab;
    // 一覧の送り (ホイール)
    private int listScroll;
    private int memberScroll;
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

    // 下の段の釦の箱 (幅 w、右寄せ、高さ 13 を段の真ん中に)
    private static Golden.Box footButton(Golden.Box foot, int w) {
        return new Golden.Box(foot.right() - w, foot.y() + (foot.h() - BTN) / 2, w, BTN);
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        bottom = null;
        if (view.mine().isEmpty()) {
            String oldName = name == null ? "" : name.getValue();
            String oldTag = tag == null ? "" : tag.getValue();
            name = new EditBox(font, NX, nameBoxY(), NW, BTN, Component.translatable("gui.skyabyss.guild.name"));
            name.setMaxLength(GuildData.NAME_MAX);
            name.setValue(oldName);
            addRenderableWidget(name);
            tag = new EditBox(font, NX, tagBoxY(), Golden.S34, BTN, Component.translatable("gui.skyabyss.guild.tag"));
            tag.setMaxLength(GuildData.TAG_MAX);
            tag.setValue(oldTag);
            addRenderableWidget(tag);
            Golden.Box b = footButton(FOOT_R, Golden.S89);
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.create"), x -> send("create", "", name.getValue(), tag.getValue(), false))
                    .bounds(b.x(), b.y(), b.w(), b.h()).build());
            return;
        }
        Golden.Box fb = footButton(FOOT_R, Golden.S89);
        if (view.leader()) {
            bottom = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.disband"), b -> {
                if (confirmed("disband", "")) {
                    send("disband", "", "", "", false);
                }
            }).bounds(fb.x(), fb.y(), fb.w(), fb.h()).build());
        } else {
            bottom = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.leave"), b -> send("leave", "", "", "", false))
                    .bounds(fb.x(), fb.y(), fb.w(), fb.h()).build());
        }
        if (tab == 0 && view.leader()) {
            addRenderableWidget(Button.builder(Component.translatable(view.open() ? "gui.skyabyss.guild.open_on" : "gui.skyabyss.guild.open_off"),
                    b -> send("open", "", "", "", !view.open())).bounds(NX, MINOR.y() + Golden.S5, NW, BTN).build());
        }
        if (tab == 1 && !view.req().kill() && !view.req().done()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.deliver"), b -> send("deliver", "", "", "", false))
                    .bounds(NX, MINOR.bottom() - Golden.S5 - BTN, NW, BTN).build());
        }
        if (tab == 2) {
            int y = MINOR.y() + 2 + ROW + Golden.S3;
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.storage"), b -> send("storage", "", "", "", false))
                    .bounds(NX, y, NW, BTN).build());
            y += BTN + Golden.S5;
            Button take = Button.builder(Component.translatable("gui.skyabyss.guild.take_rank." + view.takeRank()),
                    b -> send("take", "", "", "", false, (view.takeRank() + 1) % 3, accent)).bounds(NX, y, NW, BTN).build();
            take.active = view.leader();
            addRenderableWidget(take);
            y += BTN + Golden.S5;
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.banner", view.banner()), b -> send("banner", "", "", "", false))
                    .bounds(NX, y, NW, BTN).build());
            if (view.leader()) {
                if (emblemTag == null && myRow() != null) {
                    // 作り直しの見本は今の旗から
                    color = myRow().color();
                    accent = myRow().accent();
                    pattern = view.pattern();
                    plate = view.plate();
                }
                String old = emblemTag == null ? myTag() : emblemTag.getValue();
                int ey = emblemRowY();
                emblemTag = new EditBox(font, NX, ey, Golden.S34, BTN, Component.translatable("gui.skyabyss.guild.tag"));
                emblemTag.setMaxLength(GuildData.TAG_MAX);
                emblemTag.setValue(old);
                addRenderableWidget(emblemTag);
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.guild.emblem"),
                        b -> send("emblem", pattern, plate, emblemTag.getValue(), false)).bounds(NX + Golden.S34 + Golden.S5, ey, NW - Golden.S34 - Golden.S5, BTN).build());
            }
        }
    }

    // 作る欄の縦の並び (脇の列): 見出し・名前の字・名前の欄・印の字・印の欄・地の色の字・見本・模様の色の字・見本
    private static int nameBoxY() {
        return MINOR.y() + 2 + 2 * ROW;
    }

    private static int tagBoxY() {
        return nameBoxY() + BTN + Golden.S5 + ROW;
    }

    private static int colorY() {
        return tagBoxY() + BTN + Golden.S5;
    }

    // 旗の頁: 作り直しの印の欄の行 (三つの釦と見出しのあと)
    private static int emblemRowY() {
        return MINOR.y() + 2 + ROW + Golden.S3 + 3 * (BTN + Golden.S5) + ROW;
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
            bannerCache = GuildEmblems.banner(mc.level.registryAccess(), pat, c, a);
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
        send(op, target, text, tagText, flag, color, accent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
    }

    // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderBoard(g, mouseX, mouseY, partialTick);
        fit.end(g);
    }

    private void renderBoard(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        hits.clear();
        hitActions.clear();
        if (view.mine().isEmpty()) {
            g.drawString(font, title, HEAD.x() + Golden.S5, Golden.textY(HEAD.y(), HEAD.h()), Golden.GOLD, false);
            renderList(g, font, mouseX, mouseY);
            renderCreate(g, font, mouseX, mouseY);
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

    // ---------------------------------------------------------------- 入っていないとき
    private void renderList(GuiGraphics g, Font font, int mx, int my) {
        Golden.recess(g, MAJOR);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.list_head"), MX, Golden.textY(MAJOR.y() + 2, ROW), Golden.PALE, false);
        List<FacilityNetwork.GuildRow> all = view.guilds();
        if (all.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.list_empty"), MX, Golden.textY(LIST_Y, ROW), Golden.DIM, false);
        }
        listScroll = Math.max(0, Math.min(listScroll, all.size() - LIST_ROWS));
        moreMarks(g, font, MAJOR.right() - Golden.S8 - 2, LIST_Y, LIST_ROWS, listScroll, all.size());
        for (int i = 0; i + listScroll < all.size() && i < LIST_ROWS; i++) {
            FacilityNetwork.GuildRow r = all.get(i + listScroll);
            int ry = LIST_Y + i * ROW;
            int ty = Golden.textY(ry, ROW);
            if ((i + listScroll) % 2 == 1) {
                Golden.stripe(g, new Golden.Box(MAJOR.x() + 1, ry, MAJOR.w() - 2, ROW));
            }
            Component act = Component.translatable(r.applied() ? "gui.skyabyss.guild.applied" : r.open() ? "gui.skyabyss.guild.join" : "gui.skyabyss.guild.apply");
            int aw = Math.max(Golden.S34, font.width(act) + 6);
            int ax = MAJOR.right() - Golden.S8 - 4 - aw;
            String count = r.members() + "/" + view.max();
            int cx = ax - Golden.S5 - font.width(count);
            int bx = drawBadge(g, font, MX, ty, r.tag(), r.color(), r.accent(), r.plate());
            FitText.draw(g, font, Component.literal(r.name()), bx + 3, ty, cx - Golden.S5 - bx - 3, Golden.TEXT, false);
            g.drawString(font, count, cx, ty, Golden.DIM, false);
            boolean hover = mx >= ax && mx < ax + aw && my >= ry && my < ry + ROW;
            g.fill(ax, ry, ax + aw, ry + ROW, r.applied() ? 0xFF161B22 : hover ? 0xFF2A5E6A : 0xFF1E3A40);
            g.drawString(font, act, ax + (aw - font.width(act)) / 2, ty, r.applied() ? Golden.DIM : Golden.TEXT, false);
            if (!r.applied()) {
                hit(ax, ry, aw, ROW, () -> send("apply", r.id(), "", "", false));
            }
        }
        // 下の段の左: 作れる条件
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.found_rule"), FOOT_L.x() + Golden.S5, Golden.textY(FOOT_L.y(), FOOT_L.h()),
                FOOT_L.w() - Golden.S5, Golden.DIM, false);
    }

    private void renderCreate(GuiGraphics g, Font font, int mx, int my) {
        Golden.recess(g, MINOR);
        int y = MINOR.y() + 2;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.make_head"), NX, Golden.textY(y, ROW), Golden.PALE, false);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.name"), NX, Golden.textY(y + ROW, ROW), NW, Golden.DIM, false);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.tag"), NX, Golden.textY(tagBoxY() - ROW, ROW), NW, Golden.DIM, false);
        // 名札の見本 (印の欄の右)
        String t = tag == null || tag.getValue().isBlank() ? "?" : tag.getValue().toUpperCase(java.util.Locale.ROOT);
        drawBadge(g, font, NX + Golden.S34 + Golden.S5, Golden.textY(tagBoxY(), BTN), t, color, accent);
        int cy = colorY();
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.color"), NX, Golden.textY(cy, ROW), NW, Golden.DIM, false);
        swatches(g, NX + 1, cy + ROW, color, true);
        int ay = cy + ROW + SW_H + Golden.S3;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.accent"), NX, Golden.textY(ay, ROW), NW, Golden.DIM, false);
        swatches(g, NX + 1, ay + ROW, accent, false);
        // 旗の見本 (既定の模様。見本の右)
        bannerItem(g, NX + 8 * SW_STEP + Golden.S5, cy + ROW, 1.5f, GuildEmblems.DEFAULT, color, accent);
    }

    private void swatches(GuiGraphics g, int x, int y, int selected, boolean base) {
        for (int i = 0; i < 16; i++) {
            int sx = x + (i % 8) * SW_STEP;
            int sy = y + (i / 8) * SW_STEP;
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

    // ---------------------------------------------------------------- 入っているとき
    private void renderMine(GuiGraphics g, Font font, int mx, int my) {
        FacilityNetwork.GuildRow me = myRow();
        if (me == null) {
            return;
        }
        // 見出しの帯: 題・旗・印と名前、右につまみ
        int tabsX = HEAD.right() - Golden.S5 - TABS.length * TAB_W - (TABS.length - 1) * TAB_GAP;
        int ty = Golden.textY(HEAD.y(), HEAD.h());
        g.drawString(font, title, HEAD.x() + Golden.S5, ty, Golden.GOLD, false);
        int x = HEAD.x() + Golden.S5 + font.width(title) + Golden.S8;
        bannerItem(g, x, HEAD.y() + 2, 1f, view.pattern(), me.color(), me.accent());
        x += 16 + Golden.S3;
        int bx = drawBadge(g, font, x, ty, me.tag(), me.color(), me.accent(), me.plate());
        FitText.draw(g, font, Component.literal(me.name()), bx + 3, ty, tabsX - Golden.S8 - bx - 3, Golden.TEXT, false);
        for (int i = 0; i < TABS.length; i++) {
            Golden.Box b = new Golden.Box(tabsX + i * (TAB_W + TAB_GAP), HEAD.y() + (HEAD.h() - BTN) / 2, TAB_W, BTN);
            Golden.tab(g, font, b, Component.translatable(TABS[i]), tab == i, b.contains(mx, my));
            int id = i;
            hit(b.x(), b.y(), b.w(), b.h(), () -> {
                if (tab != id) {
                    tab = id;
                    rebuildWidgets();
                }
            });
        }
        // 下の段の左: 人数と自分の位
        Component who = Component.translatable("gui.skyabyss.guild.members", view.members().size(), view.max()).append("  ・  ")
                .append(Component.translatable("gui.skyabyss.guild.rank." + view.rank()).withColor(Golden.GOLD));
        FitText.draw(g, font, who, FOOT_L.x() + Golden.S5, Golden.textY(FOOT_L.y(), FOOT_L.h()), FOOT_L.w() - Golden.S5, Golden.SOFT, false);
        switch (tab) {
            case 1 -> renderRequest(g, font);
            case 2 -> renderBanner(g, font, mx, my);
            case 3 -> renderAchievements(g, font);
            default -> renderMembers(g, font, mx, my);
        }
    }

    // 上・下にまだあるときの小さな印
    private void moreMarks(GuiGraphics g, Font font, int x, int ly, int rows, int scroll, int size) {
        if (scroll > 0) {
            g.drawString(font, "▲", x, Golden.textY(ly, ROW), Golden.DIM, false);
        }
        if (scroll + rows < size) {
            g.drawString(font, "▼", x, Golden.textY(ly + (rows - 1) * ROW, ROW), Golden.DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        int step = dy > 0 ? -1 : dy < 0 ? 1 : 0;
        if (view.mine().isEmpty()) {
            listScroll = Math.max(0, listScroll + step);
        } else {
            memberScroll = Math.max(0, memberScroll + step);
        }
        return true;
    }

    private void renderMembers(GuiGraphics g, Font font, int mx, int my) {
        Golden.recess(g, MAJOR);
        List<FacilityNetwork.MemberRow> list = view.members();
        memberScroll = Math.max(0, Math.min(memberScroll, list.size() - MEM_ROWS));
        int right = MAJOR.right() - Golden.S8 - 4;
        moreMarks(g, font, MAJOR.right() - Golden.S8 - 2, MEM_Y, MEM_ROWS, memberScroll, list.size());
        for (int i = 0; i + memberScroll < list.size() && i < MEM_ROWS; i++) {
            FacilityNetwork.MemberRow m = list.get(i + memberScroll);
            int ry = MEM_Y + i * ROW;
            int ty = Golden.textY(ry, ROW);
            if ((i + memberScroll) % 2 == 1) {
                Golden.stripe(g, new Golden.Box(MAJOR.x() + 1, ry, MAJOR.w() - 2, ROW));
            }
            g.fill(MX, ry + 5, MX + 3, ry + 8, m.online() ? FacilityUi.TEAL : 0xFF3A3F48);
            String mark = m.rank() == 2 ? "★ " : m.rank() == 1 ? "☆ " : "";
            int nameRight = right;
            if (view.leader() && m.rank() < 2) {
                Component k = Component.translatable("gui.skyabyss.guild.kick");
                int kw = font.width(k) + 6;
                int kx = right - kw;
                g.fill(kx, ry + 1, kx + kw, ry + ROW - 1, mx >= kx && mx < kx + kw && my >= ry && my < ry + ROW ? 0xFF5A2A38 : 0xFF2A1C22);
                g.drawString(font, k, kx + 3, ty, FacilityUi.LACK, false);
                hit(kx, ry + 1, kw, ROW - 2, () -> send("kick", m.id().toString(), "", "", false));
                Component o = Component.translatable(m.rank() == 1 ? "gui.skyabyss.guild.demote" : "gui.skyabyss.guild.promote");
                int ow = font.width(o) + 6;
                int ox = kx - ow - 2;
                g.fill(ox, ry + 1, ox + ow, ry + ROW - 1, mx >= ox && mx < ox + ow && my >= ry && my < ry + ROW ? 0xFF2A5E6A : 0xFF1E3A40);
                g.drawString(font, o, ox + 3, ty, FacilityUi.TEXT, false);
                hit(ox, ry + 1, ow, ROW - 2, () -> send("officer", m.id().toString(), "", "", m.rank() != 1));
                // 長を譲る (★。二度押し)
                boolean sure = confirming("transfer", m.id().toString());
                int sx = ox - 15 - 2;
                g.fill(sx, ry + 1, sx + 15, ry + ROW - 1, sure ? 0xFF5A4A20 : mx >= sx && mx < sx + 15 && my >= ry && my < ry + ROW ? 0xFF4A4426 : 0xFF2A2618);
                g.drawString(font, sure ? "?" : "★", sx + 4, ty, FacilityUi.GOLD, false);
                hit(sx, ry + 1, 15, ROW - 2, () -> {
                    if (confirmed("transfer", m.id().toString())) {
                        send("transfer", m.id().toString(), "", "", false);
                    }
                });
                nameRight = sx - Golden.S3;
            }
            FitText.draw(g, font, Component.literal(mark + m.name()), MX + 7, ty, nameRight - MX - 7, m.rank() >= 1 ? Golden.GOLD : Golden.TEXT, false);
        }
        // 脇の列: 長は「誰でも入れる」(釦)、長と副長は申請の一覧、長には譲り方の手引き
        Golden.recess(g, MINOR);
        int y = MINOR.y() + 2;
        if (view.leader()) {
            y = MINOR.y() + Golden.S5 + BTN + Golden.S5;
        }
        int hintTop = MINOR.bottom() - 2;
        if (view.leader()) {
            Component hint = Component.translatable(confirm.startsWith("transfer:") && net.minecraft.Util.getMillis() < confirmUntil
                    ? "gui.skyabyss.guild.transfer_sure" : "gui.skyabyss.guild.leader_hint");
            List<FormattedCharSequence> lines = JpWrap.split(font, hint, NW);
            hintTop = MINOR.bottom() - 2 - Math.min(3, lines.size()) * ROW;
            Golden.rule(g, NX, MINOR.right() - Golden.S5, hintTop - 2);
            for (int i = 0; i < lines.size() && i < 3; i++) {
                g.drawString(font, lines.get(i), NX, Golden.textY(hintTop + i * ROW, ROW), Golden.DIM, false);
            }
        }
        if (view.rank() < 1) {
            return;
        }
        g.drawString(font, Component.translatable("gui.skyabyss.guild.applicants"), NX, Golden.textY(y, ROW), Golden.PALE, false);
        int ay = y + ROW;
        int rows = Math.max(0, (hintTop - 2 - ay) / ROW);
        if (view.applicants().isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.no_applicants"), NX, Golden.textY(ay, ROW), Golden.DIM, false);
        }
        for (int i = 0; i < view.applicants().size() && i < rows; i++) {
            FacilityNetwork.MemberRow m = view.applicants().get(i);
            int ry = ay + i * ROW;
            int ty = Golden.textY(ry, ROW);
            FitText.draw(g, font, Component.literal(m.name()), NX, ty, NW - 36, Golden.TEXT, false);
            int okx = MINOR.right() - Golden.S5 - 32;
            g.fill(okx, ry + 1, okx + 15, ry + ROW - 1, 0xFF1E3A40);
            g.drawString(font, "✔", okx + 4, ty, FacilityUi.TEAL, false);
            hit(okx, ry + 1, 15, ROW - 2, () -> send("accept", m.id().toString(), "", "", false));
            int nox = okx + 17;
            g.fill(nox, ry + 1, nox + 15, ry + ROW - 1, 0xFF2A1C22);
            g.drawString(font, "✘", nox + 4, ty, FacilityUi.LACK, false);
            hit(nox, ry + 1, 15, ROW - 2, () -> send("deny", m.id().toString(), "", "", false));
        }
    }

    private void renderRequest(GuiGraphics g, Font font) {
        FacilityNetwork.ReqView r = view.req();
        Golden.recess(g, MAJOR);
        Golden.recess(g, MINOR);
        int y = MAJOR.y() + 2;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_head"), MX, Golden.textY(y, ROW), Golden.PALE, false);
        g.drawString(font, Component.translatable("gui.skyabyss.guild.req_top"), NX, Golden.textY(MINOR.y() + 2, ROW), Golden.PALE, false);
        if (r.id().isEmpty()) {
            return;
        }
        // 主の列: 絵と頼み、数え方、進みの帯、自分の分、ほうび
        y += ROW + Golden.S3;
        g.renderItem(r.icon(), MX, y + 5);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.req." + r.id(), r.target()), MX + 21, Golden.textY(y, ROW), MW - 21, Golden.TEXT, false);
        FitText.draw(g, font, Component.translatable(r.kill() ? "gui.skyabyss.guild.req_how_kill" : "gui.skyabyss.guild.req_how_give"), MX + 21,
                Golden.textY(y + ROW, ROW), MW - 21, Golden.DIM, false);
        y += 2 * ROW + Golden.S8;
        int bw = MW;
        g.fill(MX, y, MX + bw, y + 5, 0xFF1C222C);
        int fill = r.target() <= 0 ? 0 : Math.min(bw, bw * r.progress() / r.target());
        g.fill(MX, y, MX + fill, y + 5, r.done() ? FacilityUi.GOLD : FacilityUi.TEAL);
        g.renderOutline(MX - 1, y - 1, bw + 2, 7, FacilityUi.LINE);
        y += 5 + Golden.S5;
        g.drawString(font, Component.translatable(r.done() ? "gui.skyabyss.guild.req_done" : "gui.skyabyss.guild.req_progress", r.progress(), r.target()),
                MX, Golden.textY(y, ROW), r.done() ? Golden.GOLD : Golden.TEXT, false);
        Component mine = Component.translatable("gui.skyabyss.guild.req_mine", r.mine());
        g.drawString(font, mine, MAJOR.right() - Golden.S5 - font.width(mine), Golden.textY(y, ROW), Golden.SOFT, false);
        List<FormattedCharSequence> reward = JpWrap.split(font, Component.translatable("gui.skyabyss.guild.req_reward"), MW);
        int ry = MAJOR.bottom() - 2 - Math.min(3, reward.size()) * ROW;
        Golden.rule(g, MX, MAJOR.right() - Golden.S5, ry - 2);
        for (int i = 0; i < reward.size() && i < 3; i++) {
            g.drawString(font, reward.get(i), MX, Golden.textY(ry + i * ROW, ROW), Golden.DIM, false);
        }
        // 脇の列: 多く納めた人 (釦の上まで)
        int ty = MINOR.y() + 2 + ROW;
        int bottomY = !r.kill() && !r.done() ? MINOR.bottom() - Golden.S5 - BTN - Golden.S5 : MINOR.bottom() - 2;
        for (int i = 0; i < r.topNames().size() && ty + ROW <= bottomY; i++) {
            String n = Integer.toString(r.topCounts().get(i));
            FitText.draw(g, font, Component.literal((i + 1) + ". " + r.topNames().get(i)), NX, Golden.textY(ty, ROW), NW - font.width(n) - 4, Golden.TEXT, false);
            g.drawString(font, n, MINOR.right() - Golden.S5 - font.width(n), Golden.textY(ty, ROW), Golden.GOLD, false);
            ty += ROW;
        }
    }

    private void renderBanner(GuiGraphics g, Font font, int mx, int my) {
        Golden.recess(g, MAJOR);
        Golden.recess(g, MINOR);
        // 脇の列: 倉庫と旗の釦 (init)、長は作り直す (印・色)
        g.drawString(font, Component.translatable("gui.skyabyss.guild.house_head"), NX, Golden.textY(MINOR.y() + 2, ROW), Golden.PALE, false);
        if (view.leader()) {
            int ey = emblemRowY();
            g.drawString(font, Component.translatable("gui.skyabyss.guild.emblem_head"), NX, Golden.textY(ey - ROW, ROW), Golden.DIM, false);
            int sy = ey + BTN + Golden.S5;
            swatches(g, NX + 1, sy, color, true);
            swatches(g, NX + 1, sy + SW_H + Golden.S3, accent, false);
        }
        // 主の列: 旗の見本 (本物の旗の品) と、模様・名札の色・名札の見本
        java.util.Set<String> unlocked = new java.util.HashSet<>(view.unlocked());
        String pat = view.leader() ? pattern : view.pattern();
        int c = view.leader() ? color : myRow() == null ? 0 : myRow().color();
        int a = view.leader() ? accent : myRow() == null ? 0 : myRow().accent();
        String pl = view.leader() ? plate : view.plate();
        int top = MAJOR.y() + Golden.S5;
        Golden.Box well = new Golden.Box(MX, top, Golden.S55, Golden.S55);
        g.fill(well.x(), well.y(), well.right(), well.bottom(), 0xFF0C0E13);
        Golden.outline(g, well.x(), well.y(), well.right(), well.bottom(), 0xFF343A48);
        bannerItem(g, well.x() + 3, well.y() + 3, 3.0f, pat, c, a);
        GuildEmblems.Emblem em = GuildEmblems.emblem(pat);
        int px = well.right() + Golden.S8;
        int pw = MAJOR.right() - Golden.S5 - px;
        g.drawString(font, Component.translatable("gui.skyabyss.guild.pattern_head"), px, Golden.textY(top, ROW), Golden.DIM, false);
        String idx = (GuildEmblems.ALL.indexOf(em) + 1) + " / " + GuildEmblems.ALL.size();
        g.drawString(font, idx, MAJOR.right() - Golden.S5 - font.width(idx), Golden.textY(top, ROW), Golden.DIM, false);
        boolean locked = em.unlock() != null && !unlocked.contains(em.unlock());
        Component pname = Component.translatable(em.nameKey());
        int py = top + ROW + Golden.S3;
        if (view.leader()) {
            arrow(g, px, py, "◀", mx, my, () -> cyclePattern(-1));
            arrow(g, px + pw - BTN, py, "▶", mx, my, () -> cyclePattern(1));
            FitText.drawCentered(g, font, pname, px + pw / 2, Golden.textY(py, BTN), pw - 2 * BTN - 4, locked ? Golden.DIM : Golden.TEXT, false);
        } else {
            FitText.draw(g, font, pname, px, Golden.textY(py, BTN), pw, Golden.TEXT, false);
        }
        int ly = py + BTN + Golden.S3;
        if (locked) {
            List<FormattedCharSequence> lines = JpWrap.split(font, Component.translatable("gui.skyabyss.guild.locked_by",
                    Component.translatable("gui.skyabyss.guild.ach." + em.unlock())), pw);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                g.drawString(font, lines.get(i), px, Golden.textY(ly + i * ROW, ROW), FacilityUi.LACK, false);
            }
        } else if (em.unlock() != null) {
            g.drawString(font, Component.translatable("gui.skyabyss.guild.special"), px, Golden.textY(ly, ROW), Golden.GOLD, false);
        }
        // 名札の色 (はじめは旗の地の色。特別な色は功績で開く)
        int cy = well.bottom() + Golden.S8;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.plate_head"), MX, Golden.textY(cy, ROW), MW, Golden.DIM, false);
        int chipY = cy + ROW + 2;
        int cx = MX + 1;
        chip(g, font, cx, chipY, 0xFF000000 | GuildData.textColor(c), pl.isEmpty(), false, "");
        cx += Golden.S21 + 1;
        for (GuildEmblems.Plate p : GuildEmblems.PLATES) {
            chip(g, font, cx, chipY, 0xFF000000 | p.rgb(), p.id().equals(pl), !unlocked.contains(p.unlock()), p.id());
            cx += Golden.S21 + 1;
        }
        // 名札の見本
        int by = chipY + 10 + Golden.S8;
        String t = view.leader() ? (emblemTag == null || emblemTag.getValue().isBlank() ? "?" : emblemTag.getValue().toUpperCase(java.util.Locale.ROOT))
                : myTag();
        int bx = drawBadge(g, font, MX, Golden.textY(by, ROW), t, c, a, pl);
        g.drawString(font, Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getGameProfile().getName(), bx + 3,
                Golden.textY(by, ROW), Golden.TEXT, false);
        List<FormattedCharSequence> rule = JpWrap.split(font, Component.translatable("gui.skyabyss.guild.banner_rule"), MW);
        int ry = by + ROW;
        for (int i = 0; i < rule.size() && ry + ROW <= MAJOR.bottom() - 2; i++) {
            g.drawString(font, rule.get(i), MX, Golden.textY(ry, ROW), Golden.DIM, false);
            ry += ROW;
        }
    }

    private void cyclePattern(int d) {
        List<GuildEmblems.Emblem> all = GuildEmblems.ALL;
        int i = all.indexOf(GuildEmblems.emblem(pattern));
        pattern = all.get(Math.floorMod(i + d, all.size())).id();
    }

    private void arrow(GuiGraphics g, int x, int y, String s, int mx, int my, Runnable run) {
        boolean hover = mx >= x && mx < x + BTN && my >= y && my < y + BTN;
        g.fill(x, y, x + BTN, y + BTN, hover ? 0xFF2A5E6A : 0xFF1E3A40);
        g.drawString(font, s, x + (BTN - font.width(s)) / 2, Golden.textY(y, BTN), Golden.TEXT, false);
        hit(x, y, BTN, BTN, run);
    }

    // 名札の色の札 (開いていない色は暗く ✕)
    private void chip(GuiGraphics g, Font font, int x, int y, int rgb, boolean on, boolean lock, String id) {
        g.fill(x, y, x + 19, y + 10, lock ? 0xFF2A2E36 : rgb);
        g.renderOutline(x - 1, y - 1, 21, 12, on ? FacilityUi.GOLD : FacilityUi.LINE);
        if (lock) {
            g.drawCenteredString(font, "✕", x + 10, y + 1, FacilityUi.DIM);
        }
        if (view.leader() && !lock) {
            hit(x, y, 19, 10, () -> plate = id);
        }
    }

    private void renderAchievements(GuiGraphics g, Font font) {
        Golden.recess(g, BODY);
        int x = BODY.x() + Golden.S5;
        int w = BODY.w() - 2 * Golden.S5;
        List<Integer> st = view.stats().size() >= 3 ? view.stats() : List.of(0, 0, 0);
        int y = BODY.y() + 2;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.guild.ach_head", st.get(0), st.get(1), st.get(2)), x, Golden.textY(y, ROW), w, Golden.PALE, false);
        List<GuildAchievements.Achievement> list = GuildAchievements.ALL;
        // 列: 印 (13)・名前 (主の列の幅まで)・ほうび・進み (右寄せ)
        int nameW = MAJOR.w() - Golden.S5 - 13 - Golden.S5;
        int rewardX = MINOR.x() - Golden.S55 - Golden.S34;
        for (int i = 0; i < list.size(); i++) {
            GuildAchievements.Achievement a = list.get(i);
            int ry = y + ROW + Golden.S3 + i * ROW;
            if (ry + ROW > BODY.bottom() - 2) {
                break;
            }
            int ty = Golden.textY(ry, ROW);
            if (i % 2 == 1) {
                Golden.stripe(g, new Golden.Box(BODY.x() + 1, ry, BODY.w() - 2, ROW));
            }
            boolean done = view.unlocked().contains(a.id());
            int v = Math.min(a.target(), st.get(a.kind().ordinal()));
            g.drawString(font, done ? "✔" : "・", x, ty, done ? Golden.GOLD : Golden.DIM, false);
            FitText.draw(g, font, Component.translatable(a.nameKey()), x + 13, ty, Math.min(nameW, rewardX - Golden.S5 - x - 13), done ? Golden.TEXT : Golden.DIM, false);
            StringBuilder rw = new StringBuilder();
            for (Component r : GuildAchievements.rewards(a.id())) {
                if (!rw.isEmpty()) {
                    rw.append("・");
                }
                rw.append(r.getString());
            }
            String prog = v + "/" + a.target();
            int progX = x + w - font.width(prog);
            FitText.draw(g, font, Component.literal(rw.toString()), rewardX, ty, progX - Golden.S8 - rewardX, done ? Golden.GOLD : Golden.DIM, false);
            g.drawString(font, prog, progX, ty, done ? Golden.TEAL : Golden.TEXT, false);
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
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
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
