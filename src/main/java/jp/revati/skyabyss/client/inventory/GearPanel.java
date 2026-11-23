package jp.revati.skyabyss.client.inventory;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

// 持ち物・旅袋・封印の箱の画面の共通の組み方 (docs/design/56 の 4 章。2026-11-11 に組み直し):
//   真ん中 = バニラと同じ幅 176 の板 (持ち物はバニラと同じ枠の位置)。左右 = 幅 118 の横の板 (MOD の要素)。
//   つまみ (持ち物・旅袋・封印の箱) は真ん中の板の上 (クリエイティブのつまみのように。選んだつまみは板とつながる)
// 三つの画面は同じ大きさ (W x H) で、高さはいちばん高い真ん中 (旅袋 200) に合わせる。だからつまみはどの画面でも同じ場所。
// 座標は画面の左上 (leftPos, topPos) から
public final class GearPanel {
    public static final int CORE_W = 176;
    public static final int WING = 118;
    public static final int GAP = 4;
    public static final int TAB_H = 16;
    public static final int CORE_X = WING + GAP;
    public static final int CORE_Y = TAB_H - 1;
    public static final int RIGHT_X = CORE_X + CORE_W + GAP;
    public static final int TALL = 200;
    public static final int W = RIGHT_X + WING;
    public static final int H = CORE_Y + TALL;
    // 横の板の中の余白
    public static final int PAD = 7;

    private GearPanel() {
    }

    public static Golden.Box core(int h) {
        return new Golden.Box(CORE_X, CORE_Y, CORE_W, h);
    }

    public static Golden.Box left(int h) {
        return new Golden.Box(0, CORE_Y, WING, h);
    }

    public static Golden.Box right(int h) {
        return new Golden.Box(RIGHT_X, CORE_Y, WING, h);
    }

    // 三枚の板 (真ん中と左右)。pose は画面の左上へ動かしてから呼ぶ
    public static void panels(GuiGraphics g, int coreH) {
        Golden.panel(g, left(coreH));
        Golden.panel(g, right(coreH));
        Golden.panel(g, core(coreH));
    }

    // ---------------------------------------------------------------- 左の板: わたし (持ち物の画面)
    public static void drawSelf(GuiGraphics g, Font font, Player p, int coreH) {
        Golden.Box b = left(coreH);
        int x = b.x() + PAD;
        int w = b.w() - 2 * PAD;
        int y = b.y() + PAD + 1;
        GrowthData data = Growth.get(p);
        SkillData sd = SkillManager.get(p);
        g.drawString(font, p.getName(), x, y, Golden.GOLD, false);
        y += Golden.ROW;
        Job job = sd.curJob == null || sd.curJob.isEmpty() ? null : Job.byId(sd.curJob);
        Component lv = job == null ? Component.translatable("gui.skyabyss.growth.hud_level", data.level)
                : Component.translatable("gui.skyabyss.growth.hud_level", data.level).append(" ").append(Component.translatable(job.nameKey()));
        g.drawString(font, lv, x, y, Golden.SOFT, false);
        y += Golden.ROW + 2;
        Golden.rule(g, x, x + w, y);
        y += 6;
        float max = Math.max(1f, p.getMaxHealth());
        float hp = Mth.clamp(p.getHealth(), 0f, max);
        bar(g, font, "gui.skyabyss.gear.hp", x, y, w, hp / max, 0xFFD45864,
                (int) Math.ceil(jp.revati.skyabyss.Hp.shown(hp) - 1e-4) + " / " + (int) Math.ceil(jp.revati.skyabyss.Hp.shown(max) - 1e-4));
        y += Golden.ROW + 2;
        double maxMana = SkillManager.maxMana(p);
        double mana = Mth.clamp(Math.max(0, sd.mana), 0, Math.max(0, maxMana));
        bar(g, font, "gui.skyabyss.gear.mana", x, y, w, maxMana <= 0 ? 0f : (float) (mana / maxMana), 0xFF3C82D2,
                (int) Math.floor(mana) + " / " + (int) Math.ceil(maxMana));
        y += Golden.ROW + 2;
        boolean top = data.level >= GrowthRules.MAX_LEVEL;
        g.drawString(font, Component.translatable("gui.skyabyss.gear.xp"), x, y + 1, Golden.SOFT, false);
        int bx = x + BAR_LABEL;
        int bw = w - BAR_LABEL;
        g.fill(bx, y + 3, bx + bw, y + 7, Golden.OUTLINE);
        float xf = top ? 1f : (float) data.xp / Math.max(1, data.xpNeed());
        g.fill(bx + 1, y + 4, bx + 1 + Math.round((bw - 2) * Mth.clamp(xf, 0f, 1f)), y + 6, 0xFF000000 | Golden.GOLD);
        y += Golden.ROW + 2;
        Golden.rule(g, x, x + w, y);
        y += 6;
        stat(g, font, "gui.skyabyss.gear.attack", (int) Math.round(jp.revati.skyabyss.Hp.shown(p.getAttributeValue(Attributes.ATTACK_DAMAGE))), x, y, w);
        y += Golden.ROW;
        stat(g, font, "gui.skyabyss.gear.armor", p.getArmorValue(), x, y, w);
    }

    static final int BAR_LABEL = 26;

    private static void bar(GuiGraphics g, Font font, String key, int x, int y, int w, float frac, int color, String text) {
        g.drawString(font, Component.translatable(key), x, y + 1, Golden.SOFT, false);
        int bx = x + BAR_LABEL;
        int bw = w - BAR_LABEL;
        g.fill(bx, y, bx + bw, y + 10, Golden.OUTLINE);
        int fill = Math.round((bw - 2) * Mth.clamp(frac, 0f, 1f));
        if (fill > 0) {
            g.fill(bx + 1, y + 1, bx + 1 + fill, y + 9, color);
            g.fill(bx + 1, y + 1, bx + 1 + fill, y + 2, 0x40FFFFFF);
        }
        g.drawString(font, text, bx + bw - 3 - font.width(text), y + 1, 0xFAF6EE, true);
    }

    private static void stat(GuiGraphics g, Font font, String key, int value, int x, int y, int w) {
        g.drawString(font, Component.translatable(key), x, y, Golden.SOFT, false);
        String v = Integer.toString(value);
        g.drawString(font, v, x + w - font.width(v), y, Golden.TEXT, false);
    }

    // ---------------------------------------------------------------- 右の板: スキル (見るだけ) とキー
    public static final int SKILL_Y = 22;
    public static final int SKILL_PITCH_X = 52;
    public static final int SKILL_PITCH_Y = 24;

    public static void drawSkills(GuiGraphics g, Font font, Player p, int coreH) {
        Golden.Box b = right(coreH);
        int x = b.x() + PAD;
        g.drawString(font, Component.translatable("gui.skyabyss.gear.skills"), x, b.y() + PAD + 1, Golden.PALE, false);
        SkillData sd = SkillManager.get(p);
        for (int i = 0; i < sd.slotCount(); i++) {
            int sx = x + 1 + (i % 2) * SKILL_PITCH_X;
            int sy = b.y() + SKILL_Y + (i / 2) * SKILL_PITCH_Y;
            Golden.slot(g, sx - 1, sy - 1, 18);
            Skill s = sd.slot(i);
            if (s != null) {
                jp.revati.skyabyss.rpg.client.skill.SkillHud.drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(p, s), sx, sy);
            }
            if (i < jp.revati.skyabyss.rpg.client.skill.SkillClientSetup.SLOT_KEYS.length) {
                String k = jp.revati.skyabyss.rpg.client.skill.SkillClientSetup.SLOT_KEYS[i].getTranslatedKeyMessage().getString();
                if (k.length() > 4) {
                    k = k.substring(0, 4);
                }
                g.drawString(font, "[" + k + "]", sx + 21, sy + 4, s == null ? Golden.DIM : Golden.TEXT, false);
            }
        }
    }

    // 人形 (バニラと同じ場所・大きさ。画面の座標で)
    public static void doll(GuiGraphics g, int px, int py, int mouseX, int mouseY, Player p) {
        int x = px + CORE_X;
        int y = py + CORE_Y;
        g.fill(x + 26, y + 8, x + 75, y + 78, 0x60000000);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY, p);
    }

    // ---------------------------------------------------------------- つまみ (真ん中の板の上)
    public static final int TABS = 3;

    public static Component tabName(int which) {
        return Component.translatable(which == 0 ? "skyabyss_gear.screen.tab_inventory" : which == 1 ? "skyabyss_pouch.tab" : "skyabyss_box.tab");
    }

    // つまみを押した: その画面へ。持ち物はバニラの画面を開くと SkyInventoryScreen に替わる
    public static void go(int which) {
        Minecraft mc = Minecraft.getInstance();
        keepCursor();
        if (which == 1) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new jp.revati.skyabyss.pouch.PouchNet.Open());
        } else if (which == 2 && mc.player != null) {
            mc.player.closeContainer();
            mc.setScreen(new jp.revati.skyabyss.client.box.BoxPouchScreen());
        } else if (mc.player != null) {
            mc.player.closeContainer();
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    public static void addTabs(Consumer<AbstractButton> add, IntSupplier left, IntSupplier top, int selected) {
        restoreCursor();
        Font font = Minecraft.getInstance().font;
        int x = CORE_X + 6;
        for (int i = 0; i < TABS; i++) {
            int w = font.width(tabName(i)) + 16;
            Tab tab = new Tab(left, top, x, w, i, i == selected);
            tab.place();
            add.accept(tab);
            x += w + 2;
        }
    }

    static final class Tab extends AbstractButton {
        private final IntSupplier left;
        private final IntSupplier top;
        private final int dx;
        private final int which;
        private final boolean selected;

        Tab(IntSupplier left, IntSupplier top, int dx, int w, int which, boolean selected) {
            super(0, 0, w, TAB_H, tabName(which));
            this.left = left;
            this.top = top;
            this.dx = dx;
            this.which = which;
            this.selected = selected;
        }

        private void place() {
            setX(left.getAsInt() + dx);
            setY(top.getAsInt() + (selected ? 0 : 2));
            setHeight(selected ? TAB_H + 3 : TAB_H - 2);
        }

        @Override
        public void onPress() {
            if (!selected) {
                go(which);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            place();
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            Font font = Minecraft.getInstance().font;
            if (selected) {
                // 板とつながるつまみ: 板の縁の線を下の辺で消す
                int y1 = top.getAsInt() + CORE_Y + 3;
                g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y1, Golden.GLASS_TOP, Golden.GLASS_TOP);
                g.fill(x0, y0, x1, y0 + 1, Golden.OUTLINE);
                g.fill(x0, y0, x0 + 1, y1 - 2, Golden.OUTLINE);
                g.fill(x1 - 1, y0, x1, y1 - 2, Golden.OUTLINE);
                g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, Golden.LINE);
                g.fill(x0 + 2, y0 + 2, x0 + 3, y1, Golden.LINE);
                g.fill(x1 - 3, y0 + 2, x1 - 2, y1, Golden.LINE);
                g.fill(x0 + 3, y1 - 3, x1 - 3, y1, Golden.GLASS_TOP);
            } else {
                int y1 = y0 + getHeight();
                g.fill(x0 + 1, y0 + 1, x1 - 1, y1, isHoveredOrFocused() ? 0xE0182040 : 0xD00A0E1E);
                Golden.outline(g, x0, y0, x1, y1 + 1, Golden.RECESS_EDGE);
            }
            Component label = getMessage();
            int color = selected ? Golden.GOLD : isHoveredOrFocused() ? Golden.TEXT : Golden.SOFT;
            g.drawString(font, label, x0 + (getWidth() - font.width(label)) / 2, top.getAsInt() + 5, color, false);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            place();
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // つまみを切り替えると、画面の開き直しでマウスが画面の中央へ戻される (バニラの releaseMouse)。押した時の位置を覚え、次の画面の
    // つまみが並ぶとき (開き直しの後) に同じ位置へ戻す
    private static double[] keptCursor;
    private static long keptAt;

    static void keepCursor() {
        Minecraft mc = Minecraft.getInstance();
        keptCursor = new double[] {mc.mouseHandler.xpos(), mc.mouseHandler.ypos()};
        keptAt = net.minecraft.Util.getMillis();
    }

    public static void restoreCursor() {
        double[] c = keptCursor;
        keptCursor = null;
        if (c != null && net.minecraft.Util.getMillis() - keptAt < 4000) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.mouseHandler.isMouseGrabbed()) {
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), c[0], c[1]);
            }
        }
    }
}
