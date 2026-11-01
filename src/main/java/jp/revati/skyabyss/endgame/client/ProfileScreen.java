package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.rebirth.TitleTagClient;
import jp.revati.skyabyss.client.ui.FitText;
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

// プロフィール (持ち物の画面の右上の札から開く。自分のことを見る・決める画面)。
//   左      人形・名前と名札の称号 (選んだ位置で上か下)・組合の旗と名前
//   右の上  つまみ三つ: わたし (職・転生・鐘銭・遊んだ時間・倒した数・称号・名声・図鑑・塔) / 称号 (一覧から選ぶ・名札の位置) / 見え方 (名札と背の飾りの設定・順位表)
//   中身はサーバーへ頼む (Profile.Request → Snapshot)。称号と転生は同期してある RebirthData を読む。背景は tools/gen/items/gen_profile_assets.py
//   E で持ち物へ戻る。J/K は記録と技へ
public class ProfileScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/profile/profile.png");
    static final int TEX_W = 1024;
    static final int TEX_H = 512;
    static final int W = 300;
    static final int H = 196;
    static final int DOLL_X0 = 14;
    static final int DOLL_Y0 = 30;
    static final int DOLL_X1 = 98;
    static final int DOLL_Y1 = 126;
    static final int BODY_X = 116;
    static final int BODY_R = 286;
    static final int SUB_X = 110;
    static final int SUB_Y = 24;
    static final int SUB_W = 58;
    static final int SUB_H = 14;
    static final int ROW = 12;
    static final int LIST_Y = 45;
    static final int LIST_ROWS = 9;
    static final int POS_X = 154;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int DIM = 0x6E7484;
    static final int GOLD = 0xF2C14E;

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
    private final int left = 2;
    private final int top = 2;
    private int scroll;
    private List<FormattedCharSequence> pendingTip;
    private ItemStack banner = ItemStack.EMPTY;
    private String bannerKey = "";

    public ProfileScreen() {
        super(Component.translatable("gui.skyabyss.profile.title"));
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
        fit.fit(width, height, W + 4, H + 4, 2.5f);
        for (int i = 0; i < 3; i++) {
            addRenderableWidget(new SubTab(left + SUB_X + i * (SUB_W + 3), top + SUB_Y, i));
        }
        if (sub == TITLES) {
            String[] keys = {"above", "below", "off"};
            for (int i = 0; i < 3; i++) {
                int pos = i;
                addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.profile.pos." + keys[i]), b -> {
                    PacketDistributor.sendToServer(new RebirthNet.TitlePosPayload(pos));
                }).bounds(left + POS_X + i * 44, top + 166, 42, 16).build());
            }
        } else if (sub == LOOKS) {
            int y = top + 46;
            for (Toggle t : TOGGLES) {
                addRenderableWidget(Button.builder(label(t), btn -> {
                    t.value.set(!RebirthConfig.on(t.value));
                    RebirthConfig.CLIENT_SPEC.save();
                    btn.setMessage(label(t));
                }).bounds(left + BODY_X, y, 170, 16).build());
                y += 18;
            }
            addRenderableWidget(Button.builder(boardLabel(hidden()), btn -> {
                boolean now = !hidden();
                PacketDistributor.sendToServer(new RebirthNet.HiddenPayload(now));
                btn.setMessage(boardLabel(now));
            }).bounds(left + BODY_X, y, 170, 16).build());
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
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        g.blit(TEXTURE, left, top, W, H, 0, 0, W * 2, H * 2, TEX_W, TEX_H);
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);
        fit.end(g);
        // 人形は画面の座標で描く (切り抜きが板の変換を通らないため)
        if (minecraft != null && minecraft.player != null) {
            int[] r = fit.screenRect(left + DOLL_X0, top + DOLL_Y0, DOLL_X1 - DOLL_X0, DOLL_Y1 - DOLL_Y0);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, r[0], r[1], r[0] + r[2], r[1] + r[3] - 4, Math.round(36 * fit.scale()), 0.0625f,
                    screenX, screenY, minecraft.player);
        }
        fit.begin(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        renderSide(g, mx, my);
        switch (sub) {
            case TITLES -> renderTitles(g, mx, my);
            case LOOKS -> renderLooks(g);
            default -> renderMe(g, mx, my);
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

    // ---------------------------------------------------------------- 左: 名前と称号・組合
    private void renderSide(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int cx = left + 56;
        Component name = mc.player.getName();
        Component title = TitleTagClient.text(mc.player);
        int pos = RebirthView.data(mc.player).titlePos();
        if (title != null && pos == RebirthData.TITLE_ABOVE) {
            FitText.drawCentered(g, font, title, cx, top + 131, 88, TitleTagClient.COLOR, false);
            FitText.drawCentered(g, font, name, cx, top + 141, 88, TEXT, false);
        } else if (title != null) {
            FitText.drawCentered(g, font, name, cx, top + 132, 88, TEXT, false);
            FitText.drawCentered(g, font, title, cx, top + 142, 88, TitleTagClient.COLOR, false);
        } else {
            FitText.drawCentered(g, font, name, cx, top + 137, 88, TEXT, false);
        }
        // 組合
        CompoundTag guild = data == null || !data.contains("guild") ? null : data.getCompound("guild");
        if (guild == null) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.profile.no_guild"), cx, top + 165, 88, DIM, false);
            return;
        }
        String key = guild.getString("pattern") + "/" + guild.getInt("color") + "/" + guild.getInt("accent");
        if (!key.equals(bannerKey) && mc.level != null) {
            banner = GuildEmblems.banner(mc.level.registryAccess(), guild.getString("pattern"), guild.getInt("color"), guild.getInt("accent"));
            bannerKey = key;
        }
        g.renderItem(banner, left + 14, top + 160);
        int tagColor = GuildData.textColor(guild.getInt("color"));
        Component tag = Component.literal("[" + guild.getString("tag") + "]").withStyle(s -> s.withColor(tagColor));
        g.drawString(font, tag, left + 33, top + 159, TEXT, false);
        FitText.draw(g, font, Component.literal(guild.getString("name")), left + 33, top + 170, 66, SOFT, false);
        if (in(mx, my, left + 12, top + 156, 90, 26)) {
            int rank = guild.getInt("rank");
            tip(List.of(Component.literal(guild.getString("name")),
                    Component.translatable("gui.skyabyss.profile.rank." + (rank == GuildData.RANK_LEADER ? "leader" : rank == GuildData.RANK_OFFICER ? "officer" : "member"))
                            .withStyle(s -> s.withColor(SOFT))));
        }
    }

    // ---------------------------------------------------------------- わたし
    private void renderMe(GuiGraphics g, int mx, int my) {
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.journey.loading"), left + 201, top + 100, SOFT);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RebirthData rd = RebirthView.data(mc.player);
        Job job = Job.byId(data.getString("job"));
        int level = data.getInt("level");
        int y = top + 47;
        row(g, y, "job", job == null ? Component.translatable("gui.skyabyss.profile.none")
                : Component.translatable("gui.skyabyss.profile.job_value", Component.translatable(job.nameKey()), level));
        y += ROW;
        int mastery = data.getInt("mastery");
        row(g, y, "mastery", level >= jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL
                ? Component.translatable("gui.skyabyss.profile.tier_value", mastery) : Component.translatable("gui.skyabyss.profile.from_50"));
        y += ROW;
        row(g, y, "rebirths", Component.translatable("gui.skyabyss.profile.times", rd.count()));
        y += ROW;
        row(g, y, "coins", Component.literal(String.format("%,d", data.getInt("coins"))));
        y += ROW;
        long minutes = data.getLong("play") / 20 / 60;
        row(g, y, "play", Component.translatable("gui.skyabyss.profile.hours", minutes / 60, minutes % 60));
        y += ROW;
        row(g, y, "kills", Component.literal(String.format("%,d", data.getInt("kills"))));
        y += ROW;
        row(g, y, "deaths", Component.literal(String.format("%,d", data.getInt("deaths"))));
        y += ROW;
        row(g, y, "titles", Component.literal(Long.bitCount(rd.unlocked()) + " / " + RebirthTitles.MAX));
        y += ROW;
        row(g, y, "fame", Component.translatable("gui.skyabyss.profile.of_tiers", data.getInt("fame"), data.getInt("fame_max")));
        y += ROW;
        row(g, y, "foes", Component.literal(data.getInt("foes") + " / " + data.getInt("foes_total")));
        y += ROW;
        row(g, y, "tower", Component.translatable("gui.skyabyss.profile.times", data.getInt("tower")));
        if (in(mx, my, left + BODY_X, top + 47 + 8 * ROW, BODY_R - BODY_X, 2 * ROW)) {
            tip(List.of(Component.translatable("gui.skyabyss.profile.see_journey").withStyle(s -> s.withColor(SOFT))));
        }
    }

    private void row(GuiGraphics g, int y, String key, Component value) {
        g.drawString(font, Component.translatable("gui.skyabyss.profile.row." + key), left + BODY_X, y, SOFT, false);
        int w = Math.min(font.width(value), 100);
        FitText.draw(g, font, value, left + BODY_R - w, y, 100, TEXT, false);
        g.fill(left + BODY_X, y + 10, left + BODY_R, y + 11, 0x18FFFFFF);
    }

    // ---------------------------------------------------------------- 称号
    private void renderTitles(GuiGraphics g, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        RebirthData d = RebirthView.data(mc.player);
        List<RebirthTitles.Title> all = ordered(d);
        int total = all.size() + 1;
        scroll = Mth.clamp(scroll, 0, Math.max(0, total - LIST_ROWS));
        for (int i = 0; i < LIST_ROWS && scroll + i < total; i++) {
            int idx = scroll + i;
            int y = top + LIST_Y + i * ROW;
            boolean hover = in(mx, my, left + BODY_X - 2, y, BODY_R - BODY_X - 4, ROW);
            if (hover) {
                g.fill(left + BODY_X - 2, y, left + BODY_R - 6, y + ROW, 0x24FFFFFF);
            }
            if (idx == 0) {
                boolean sel = d.title().isEmpty();
                g.drawString(font, (sel ? "▶ " : "  ") + Component.translatable("skyabyss_rebirth.gui.no_title").getString(), left + BODY_X, y + 2,
                        sel ? GOLD : hover ? TEXT : SOFT, false);
                continue;
            }
            RebirthTitles.Title ti = all.get(idx - 1);
            boolean has = RebirthTitles.has(d.unlocked(), ti);
            boolean sel = ti.id().equals(d.title());
            Component name = Component.translatable("title.skyabyss_rebirth." + ti.id());
            String shown = has ? name.getString() : "？ " + "・".repeat(Math.min(6, name.getString().length()));
            FitText.draw(g, font, Component.literal((sel ? "▶ " : "  ") + shown), left + BODY_X, y + 2, 160, sel ? GOLD : has ? TEXT : DIM, false);
            if (hover) {
                Component how = Component.translatable("title.skyabyss_rebirth." + ti.id() + ".how");
                tip(has ? List.of(name.copy().withStyle(s -> s.withColor(TitleTagClient.COLOR)), how.copy().withStyle(s -> s.withColor(SOFT)))
                        : List.of(Component.translatable("skyabyss_rebirth.gui.locked_how", how).withStyle(s -> s.withColor(SOFT))));
            }
        }
        if (total > LIST_ROWS) {
            int barH = LIST_ROWS * ROW;
            int knob = Math.max(10, barH * LIST_ROWS / total);
            int ky = top + LIST_Y + (barH - knob) * scroll / Math.max(1, total - LIST_ROWS);
            g.fill(left + BODY_R + 1, top + LIST_Y, left + BODY_R + 3, top + LIST_Y + barH, 0x30FFFFFF);
            g.fill(left + BODY_R + 1, ky, left + BODY_R + 3, ky + knob, 0xA0FFFFFF);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.profile.count", Long.bitCount(d.unlocked()), RebirthTitles.MAX), left + BODY_X,
                top + LIST_Y + LIST_ROWS * ROW + 3, DIM, false);
        g.drawString(font, Component.translatable("gui.skyabyss.profile.pos"), left + BODY_X, top + 170, SOFT, false);
        // いまの位置のボタンに金の下線
        int x = left + POS_X + d.titlePos() * 44;
        g.fill(x + 3, top + 182, x + 39, top + 183, 0xFF000000 | GOLD);
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

    // ---------------------------------------------------------------- 見え方
    private void renderLooks(GuiGraphics g) {
        FitText.draw(g, font, Component.translatable("gui.skyabyss.profile.looks_note"), left + BODY_X, top + 176, BODY_R - BODY_X, DIM, false);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (sub == TITLES && button == 0 && mx >= left + BODY_X - 2 && mx < left + BODY_R - 6 && my >= top + LIST_Y && my < top + LIST_Y + LIST_ROWS * ROW) {
            int idx = scroll + (int) ((my - (top + LIST_Y)) / ROW);
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

    // 右の上のつまみ (わたし・称号・見え方)
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
            int state = sub == which ? 2 : isHoveredOrFocused() ? 1 : 0;
            g.blit(TEXTURE, getX(), getY(), SUB_W, SUB_H, state * 120, 400, SUB_W * 2, SUB_H * 2, TEX_W, TEX_H);
            int color = sub == which ? GOLD : isHoveredOrFocused() ? TEXT : SOFT;
            g.drawString(font, getMessage(), getX() + (SUB_W - font.width(getMessage())) / 2, getY() + 3, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
