package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.rebirth.TitleTagClient;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.LedgerStage;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.Profile;
import jp.revati.skyabyss.facility.guild.GuildData;
import jp.revati.skyabyss.facility.guild.GuildEmblems;
import jp.revati.skyabyss.rebirth.RebirthConfig;
import jp.revati.skyabyss.rebirth.RebirthData;
import jp.revati.skyabyss.rebirth.RebirthNet;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.RebirthView;
import jp.revati.skyabyss.rpg.client.skill.RpgTabs;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// プロフィール (持ち物の画面の右上の札から開く。自分のことを見る・決める画面)。
//   人形・名前と名札の称号 (名前の上)・組合の旗と名前。つまみ三つ: わたし (職・転生・鐘銭・遊んだ時間・倒した数・称号・名声・図鑑・塔) /
//   称号 (一覧から選ぶ) / 見え方 (名札と背の飾りの設定・順位表)
//   中身はサーバーへ頼む (Profile.Request → Snapshot)。称号と転生は同期してある RebirthData を読む。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1):
//   左の黒い紙に題・組合 (左の列)・大きな人形と、その下に称号と名前。右の生成りの帯 (目録) の上につまみ、その下につまみの中身
//   (わたし = 数の表、称号 = 一覧 (選んでいる称号は桃色の付箋)、見え方 = 切り替えの釦と注)
//   E で持ち物へ戻る。J/K は記録と技へ
public class ProfileScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/profile/profile.png");
    static final int TEX_W = 1024;
    static final int TEX_H = 512;
    static final int W = LedgerStage.W;
    static final int H = LedgerStage.H;
    // 人形の枠 (左の黒い紙の真ん中)
    static final int DOLL_X0 = 104;
    static final int DOLL_Y0 = 40;
    static final int DOLL_X1 = 246;
    static final int DOLL_Y1 = 168;
    // 組合 (左の列)
    static final int GUILD_X = LedgerStage.PAGE_X;
    static final int GUILD_Y = 46;
    static final int GUILD_W = 80;
    // 右の帯の中
    static final int BODY_X = LedgerStage.INDEX_X;
    static final int BODY_R = LedgerStage.INDEX_X + LedgerStage.INDEX_W - 4;
    // つまみ (帯の上の端)
    static final int SUB_W = 35;
    static final int SUB_GAP = 2;
    static final int SUB_H = Golden.BTN;
    static final int SUB_X = LedgerStage.INDEX_X - 2;
    static final int SUB_Y = 12;
    static final int ROW = 13;
    static final int ME_ROW = 13;
    static final int LIST_Y = LedgerStage.LIST_Y + 2;
    static final int LIST_ROWS = (214 - LIST_Y - ROW) / ROW;

    static final int TEXT = Golden.TEXT;
    static final int SOFT = Paper.ON_BLACK_SOFT;
    static final int DIM = Paper.ON_BLACK_DIM;
    static final int GOLD = Paper.ON_BLACK_PINK;

    static final int ME = 0;
    static final int TITLES = 1;
    static final int LOOKS = 2;
    // 最後に開いていたつまみ (開き直しても同じつまみ)
    private static int sub = ME;

    @Nullable
    private static CompoundTag data;

    private record Toggle(String key, ModConfigSpec.BooleanValue value) {
    }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("nametag_title", RebirthConfig.NAMETAG_TITLE),
            new Toggle("nametag_effects", RebirthConfig.NAMETAG_EFFECTS),
            new Toggle("back_self", RebirthConfig.BACK_DECOR_SELF),
            new Toggle("back_others", RebirthConfig.BACK_DECOR_OTHERS),
            new Toggle("burst", RebirthConfig.BURST),
            new Toggle("prism_still", RebirthConfig.PRISM_STILL));

    private final UiFit fit = new UiFit();
    private int scroll;
    private List<FormattedCharSequence> pendingTip;
    private ItemStack banner = ItemStack.EMPTY;
    private String bannerKey = "";

    public ProfileScreen() {
        super(Component.translatable("gui.skyabyss.profile.title"));
    }

    // 撮影用: つまみを選んで開く
    public ProfileScreen page(int which) {
        sub = Math.max(ME, Math.min(LOOKS, which));
        return this;
    }

    public static void receive(Profile.Snapshot snap) {
        data = snap.data();
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.closeContainer();
        }
        mc.setScreen(new ProfileScreen());
    }

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        if (sub > LOOKS) {
            sub = ME;
        }
        for (int i = 0; i <= LOOKS; i++) {
            addRenderableWidget(new SubTab(SUB_X + i * (SUB_W + SUB_GAP), SUB_Y, i));
        }
        if (sub == LOOKS) {
            int y = LIST_Y;
            int bw = BODY_R - BODY_X;
            for (Toggle t : TOGGLES) {
                addRenderableWidget(Paper.tag(label(t), btn -> {
                    t.value.set(!RebirthConfig.on(t.value));
                    RebirthConfig.CLIENT_SPEC.save();
                    btn.setMessage(label(t));
                }).bounds(BODY_X, y, bw, Golden.BTN).build());
                y += Golden.BTN + 3;
            }
            addRenderableWidget(Paper.tag(boardLabel(hidden()), btn -> {
                boolean now = !hidden();
                PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(now));
                btn.setMessage(boardLabel(now));
            }).bounds(BODY_X, y, bw, Golden.BTN).build());
        }
        PacketDistributor.sendToServer(new Profile.Request());
    }

    private static Component label(Toggle t) {
        return Component.translatable("skyabyss_rebirth.gui.toggle." + t.key, Component.translatable(RebirthConfig.on(t.value) ? "options.on" : "options.off"));
    }

    private static boolean hidden() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && RebirthView.data(mc.player).hidden();
    }

    private static Component boardLabel(boolean hidden) {
        return Component.translatable("skyabyss_rebirth.gui.toggle.board", Component.translatable(hidden ? "options.off" : "options.on"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode) && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            return true;
        }
        if (RpgTabs.keyPressed(keyCode, scanCode, -2)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, W, H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height);
        LedgerStage.pageTitle(g, font, title, null);
        fit.end(g);
        // 人形は画面の座標で描く (切り抜きが板の変換を通らないため)
        if (minecraft != null && minecraft.player != null) {
            int[] r = fit.screenRect(DOLL_X0, DOLL_Y0, DOLL_X1 - DOLL_X0, DOLL_Y1 - DOLL_Y0);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, r[0], r[1], r[0] + r[2], r[1] + r[3] - 4, Math.round(52 * fit.scale()), 0.0625f,
                    screenX, screenY, minecraft.player);
        }
        fit.begin(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        renderSide(g, mx, my);
        switch (sub) {
            case TITLES -> renderTitles(g, mx, my);
            case LOOKS -> renderLooks(g);
            default -> renderMe(g, mx, my);
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        g.pose().popPose();
        fit.end(g);
        if (pendingTip != null) {
            g.renderTooltip(font, pendingTip, screenX, screenY);
        }
    }

    private void tip(List<Component> lines) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            out.addAll(font.split(c, 200));
        }
        pendingTip = out;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- 左の黒い紙: 名前と称号・組合
    private void renderSide(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int cx = (DOLL_X0 + DOLL_X1) / 2;
        int tw = DOLL_X1 - DOLL_X0 + 10;
        Component name = mc.player.getName();
        Component title = TitleTagClient.text(mc.player);
        int ny = DOLL_Y1 + 2;
        if (title != null) {
            FitText.drawCentered(g, font, title, cx, ny, tw, TitleTagClient.COLOR, false);
            ny += 12;
        }
        FitText.drawCentered(g, font, name, cx, ny, tw, TEXT, false);
        // 組合 (左の列)
        int gy = GUILD_Y;
        CompoundTag guild = data == null || !data.contains("guild") ? null : data.getCompound("guild");
        if (guild == null) {
            for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.profile.no_guild"), GUILD_W)) {
                g.drawString(font, line, GUILD_X, gy, DIM, false);
                gy += 11;
            }
            return;
        }
        String key = guild.getString("pattern") + "/" + guild.getInt("color") + "/" + guild.getInt("accent");
        if (!key.equals(bannerKey) && mc.level != null) {
            banner = GuildEmblems.banner(mc.level.registryAccess(), guild.getString("pattern"), guild.getInt("color"), guild.getInt("accent"));
            bannerKey = key;
        }
        g.pose().pushPose();
        g.pose().translate(GUILD_X, gy, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(banner, 0, 0);
        g.pose().popPose();
        int tagColor = GuildData.textColor(guild.getInt("color"));
        Component tag = Component.literal("[" + guild.getString("tag") + "]").withStyle(s -> s.withColor(tagColor));
        g.drawString(font, tag, GUILD_X, gy + 36, TEXT, false);
        int ly = gy + 48;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.literal(guild.getString("name")), GUILD_W)) {
            g.drawString(font, line, GUILD_X, ly, SOFT, false);
            ly += 11;
        }
        if (in(mx, my, GUILD_X, gy, GUILD_W, ly - gy)) {
            int rank = guild.getInt("rank");
            tip(List.of(Component.literal(guild.getString("name")),
                    Component.translatable("gui.skyabyss.profile.rank." + (rank == GuildData.RANK_LEADER ? "leader" : rank == GuildData.RANK_OFFICER ? "officer" : "member"))
                            .withStyle(s -> s.withColor(SOFT))));
        }
    }

    // ---------------------------------------------------------------- わたし (右の帯の数の表)
    private void renderMe(GuiGraphics g, int mx, int my) {
        if (data == null) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.loading"), BODY_X, LIST_Y + 2, BODY_R - BODY_X, Paper.INK_SOFT, false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RebirthData rd = RebirthView.data(mc.player);
        Job job = Job.byId(data.getString("job"));
        int level = data.getInt("level");
        int y = LIST_Y;
        rowIndex = 0;
        row(g, y, "job", job == null ? Component.translatable("gui.skyabyss.profile.none")
                : Component.translatable("gui.skyabyss.profile.job_value", Component.translatable(job.nameKey()), level));
        y += ME_ROW;
        int mastery = data.getInt("mastery");
        row(g, y, "mastery", level >= jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL
                ? Component.translatable("gui.skyabyss.profile.tier_value", mastery) : Component.translatable("gui.skyabyss.profile.from_50"));
        y += ME_ROW;
        row(g, y, "rebirths", Component.translatable("gui.skyabyss.profile.times", rd.count()));
        y += ME_ROW;
        row(g, y, "coins", Component.literal(String.format("%,d", data.getInt("coins"))));
        y += ME_ROW;
        long minutes = data.getLong("play") / 20 / 60;
        row(g, y, "play", Component.translatable("gui.skyabyss.profile.hours", minutes / 60, minutes % 60));
        y += ME_ROW;
        row(g, y, "kills", Component.literal(String.format("%,d", data.getInt("kills"))));
        y += ME_ROW;
        row(g, y, "deaths", Component.literal(String.format("%,d", data.getInt("deaths"))));
        y += ME_ROW;
        row(g, y, "titles", Component.literal(Long.bitCount(rd.unlocked()) + " / " + RebirthTitles.MAX));
        y += ME_ROW;
        row(g, y, "fame", Component.translatable("gui.skyabyss.profile.of_tiers", data.getInt("fame"), data.getInt("fame_max")));
        y += ME_ROW;
        row(g, y, "foes", Component.literal(data.getInt("foes") + " / " + data.getInt("foes_total")));
        y += ME_ROW;
        row(g, y, "tower", Component.translatable("gui.skyabyss.profile.times", data.getInt("tower")));
        y += ME_ROW;
        row(g, y, "fish", Component.literal(data.getInt("fish") + " / " + data.getInt("fish_total")));
        if (in(mx, my, BODY_X - 4, LIST_Y + 8 * ME_ROW, BODY_R - BODY_X + 4, 2 * ME_ROW)) {
            tip(List.of(Component.translatable("gui.skyabyss.profile.see_journey").withStyle(s -> s.withColor(SOFT))));
        }
    }

    // 数の表の一行 (墨の字。行のあいだに点線)
    private int rowIndex;

    private void row(GuiGraphics g, int y, String key, Component value) {
        if (rowIndex++ > 0) {
            for (int d = BODY_X; d < BODY_R; d += 3) {
                g.fill(d, y - 1, d + 1, y, Paper.CREAM_LO);
            }
        }
        Component label = Component.translatable("gui.skyabyss.profile.row." + key);
        int lw = font.width(label);
        g.drawString(font, label, BODY_X, y + 2, Paper.INK_SOFT, false);
        int room = BODY_R - BODY_X - lw - 4;
        int w = Math.min(font.width(value), room);
        FitText.draw(g, font, Component.literal(value.getString()), BODY_R - w, y + 2, room, Paper.INK, false);
    }

    // ---------------------------------------------------------------- 称号 (右の帯の一覧)
    private void renderTitles(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        RebirthData d = RebirthView.data(mc.player);
        List<RebirthTitles.Title> all = ordered(d);
        int total = all.size() + 1;
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - LIST_ROWS));
        for (int i = 0; i < LIST_ROWS && scroll + i < total; i++) {
            int idx = scroll + i;
            int y = LIST_Y + i * ROW;
            boolean hover = in(mx, my, BODY_X - 4, y, BODY_R - BODY_X + 4, ROW);
            boolean sel;
            boolean has = true;
            String shown;
            if (idx == 0) {
                sel = d.title().isEmpty();
                shown = Component.translatable("skyabyss_rebirth.gui.no_title").getString();
            } else {
                RebirthTitles.Title ti = all.get(idx - 1);
                has = RebirthTitles.has(d.unlocked(), ti);
                sel = ti.id().equals(d.title());
                Component name = Component.translatable("title.skyabyss_rebirth." + ti.id());
                shown = has ? name.getString() : "？ " + "・".repeat(Math.min(6, name.getString().length()));
                if (hover) {
                    Component how = Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how");
                    tip(has ? List.of(name.copy().withStyle(s -> s.withColor(TitleTagClient.COLOR)), how.copy().withStyle(s -> s.withColor(SOFT)))
                            : List.of(Component.translatable("skyabyss_rebirth.gui.locked_how", how).withStyle(s -> s.withColor(SOFT))));
                }
            }
            if (sel) {
                LedgerStage.flag(g, y, ROW);
            } else if (hover) {
                LedgerStage.hover(g, y, ROW);
            }
            FitText.draw(g, font, Component.literal(shown), BODY_X, y + 3, BODY_R - BODY_X - 4, sel ? LedgerStage.ON_PINK : has ? Paper.INK : 0x6C7084, false);
        }
        if (total > LIST_ROWS) {
            int barH = LIST_ROWS * ROW;
            int knob = Math.max(10, barH * LIST_ROWS / total);
            int ky = LIST_Y + (barH - knob) * scroll / Math.max(1, total - LIST_ROWS);
            int bx = LedgerStage.INDEX_X + LedgerStage.INDEX_W - 2;
            g.fill(bx, LIST_Y, bx + 1, LIST_Y + barH, Paper.CREAM_LO);
            g.fill(bx - 1, ky, bx + 2, ky + knob, Paper.PINK);
        }
        FitText.draw(g, font, Component.translatable("gui.skyabyss.profile.count", Long.bitCount(d.unlocked()), RebirthTitles.MAX), BODY_X, 203,
                BODY_R - BODY_X, Paper.INK_SOFT, false);
    }

    // 一覧の並び: 解けた称号を先に (どちらも元の並び順のまま)
    private static List<RebirthTitles.Title> ordered(RebirthData d) {
        List<RebirthTitles.Title> out = new ArrayList<>();
        for (RebirthTitles.Title t : RebirthTitles.all()) {
            if (RebirthTitles.has(d.unlocked(), t)) {
                out.add(t);
            }
        }
        for (RebirthTitles.Title t : RebirthTitles.all()) {
            if (!RebirthTitles.has(d.unlocked(), t)) {
                out.add(t);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 見え方 (釦の下に注)
    private void renderLooks(GuiGraphics g) {
        int y = LIST_Y + (TOGGLES.size() + 1) * (Golden.BTN + 3) + 4;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.profile.looks_note"), BODY_R - BODY_X)) {
            if (y + 9 > 214) {
                break;
            }
            g.drawString(font, line, BODY_X, y, Paper.INK_SOFT, false);
            y += 11;
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (sub == TITLES && button == 0 && mx >= BODY_X - 4 && mx < BODY_R && my >= LIST_Y && my < LIST_Y + LIST_ROWS * ROW) {
            int idx = scroll + (int) ((my - LIST_Y) / ROW);
            Minecraft mc = Minecraft.getInstance();
            if (idx == 0) {
                PacketDistributor.sendToServer(new RebirthNet.SelectTitlePayload(""));
                return true;
            }
            List<RebirthTitles.Title> all = ordered(RebirthView.data(mc.player));
            if (idx - 1 < all.size()) {
                RebirthTitles.Title ti = all.get(idx - 1);
                if (RebirthTitles.has(RebirthView.data(mc.player).unlocked(), ti)) {
                    PacketDistributor.sendToServer(new RebirthNet.SelectTitlePayload(ti.id()));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
        if (sub == TITLES) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(screenX, screenY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        return super.mouseReleased(fit.x(screenX), fit.y(screenY), button);
    }

    // 右の帯の上のつまみ (わたし・称号・見え方)
    private final class SubTab extends AbstractButton {
        private final int which;

        SubTab(int x, int y, int which) {
            super(x, y, SUB_W, SUB_H, Component.translatable("gui.skyabyss.profile.sub." + (which == ME ? "me" : which == TITLES ? "titles" : "looks")));
            this.which = which;
        }

        @Override
        public void onPress() {
            if (sub != which) {
                sub = which;
                scroll = 0;
                rebuildWidgets();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Paper.tab(g, font, getX(), getY(), SUB_W, SUB_H, getMessage(), sub == which, isHoveredOrFocused());
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
