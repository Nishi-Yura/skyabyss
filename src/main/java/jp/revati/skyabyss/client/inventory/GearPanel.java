package jp.revati.skyabyss.client.inventory;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

import jp.revati.skyabyss.client.accessory.AccessoryScreen;
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
import net.minecraft.world.item.ItemStack;

// 持ち物と装飾の画面の共通の組み方 (docs/design/56 の 4 章): 大きい板 (356x220) に、
//   左の欄 (128) = わたし: 名前・Lv とジョブ、防具 4 と逆手、人形、装飾 3、体力・マナ・経験値、攻撃と守り
//   右の欄 (207) = 袋 27、手もと 9、スキル 4 (見るだけ)、プロフィールの釦
// つまみ (持ち物・装飾・旅袋・封の箱) は見出しの帯の左。持ち物の画面では防具と逆手と袋が動かせ、装飾は見るだけ。装飾の画面ではその逆。
// 座標は板の左上から
public final class GearPanel {
    public static final int W = Golden.L_W;
    public static final int H = Golden.L_H;
    static final Golden.Layout L = Golden.Layout.large(0);
    public static final Golden.Box LEFT = L.body().minor(true);
    public static final Golden.Box RIGHT = L.body().major(false);
    // 品の置き場 (16 角の左上)
    public static final int ARMOR_X = LEFT.x() + 6;
    public static final int ACC_X = LEFT.right() - 6 - 16;
    public static final int COL_Y = LEFT.y() + 19;
    public static final int COL_PITCH = 19;
    public static final int BAG_X = RIGHT.x() + (RIGHT.w() - (9 * 21 - 5)) / 2 + 1;
    public static final int BAG_Y = RIGHT.y() + 19;
    public static final int PITCH = 21;
    public static final int HOT_Y = RIGHT.y() + 103;
    static final int SKILL_Y = RIGHT.y() + 143;
    static final int BAR_X = LEFT.x() + 32;
    static final int BAR_W = LEFT.w() - 32 - 6;
    static final int BAR_Y = LEFT.y() + 118;
    static final int TAB_W = 41;

    private GearPanel() {
    }

    // 板の区切り (旅袋・封印の箱など、同じ板を使う画面のために)
    public static Golden.Layout layout() {
        return L;
    }

    public static int armorY(int i) {
        return COL_Y + i * COL_PITCH;
    }

    public static int bagX(int col) {
        return BAG_X + col * PITCH;
    }

    public static int bagY(int row) {
        return BAG_Y + row * PITCH;
    }

    // 板・欄・見出し・枠・人形・帯・数。品そのものは画面 (入れ物の枠) が描く
    public static void draw(GuiGraphics g, Font font, int px, int py, int mouseX, int mouseY, Player p, Component title) {
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        Golden.frame(g, font, L, title);
        Golden.recess(g, LEFT);
        Golden.recess(g, RIGHT);
        // 左: 名前、Lv とジョブ
        GrowthData data = Growth.get(p);
        SkillData sd = SkillManager.get(p);
        g.drawString(font, p.getName(), LEFT.x() + 6, LEFT.y() + 6, Golden.GOLD, false);
        Job job = sd.curJob == null || sd.curJob.isEmpty() ? null : Job.byId(sd.curJob);
        Component lv = job == null ? Component.translatable("gui.skyabyss.growth.hud_level", data.level)
                : Component.translatable("gui.skyabyss.growth.hud_level", data.level).append(" ").append(Component.translatable(job.nameKey()));
        g.drawString(font, lv, LEFT.right() - 6 - font.width(lv), LEFT.y() + 6, Golden.SOFT, false);
        for (int i = 0; i < 5; i++) {
            Golden.slot(g, ARMOR_X - 1, armorY(i) - 1, 18);
        }
        for (int i = 0; i < 3; i++) {
            Golden.slot(g, ACC_X - 1, armorY(i) - 1, 18);
        }
        g.fill(ARMOR_X + 21, COL_Y - 1, ACC_X - 5, armorY(4) + 17, 0x60000000);
        // 帯: 体力・マナ・経験値
        float max = Math.max(1f, p.getMaxHealth());
        float hp = Mth.clamp(p.getHealth(), 0f, max);
        label(g, font, "gui.skyabyss.gear.hp", BAR_Y);
        bar(g, font, BAR_Y, hp / max, 0xFFD45864,
                (int) Math.ceil(jp.revati.skyabyss.Hp.shown(hp) - 1e-4) + " / " + (int) Math.ceil(jp.revati.skyabyss.Hp.shown(max) - 1e-4));
        double maxMana = SkillManager.maxMana(p);
        double mana = Mth.clamp(Math.max(0, sd.mana), 0, Math.max(0, maxMana));
        label(g, font, "gui.skyabyss.gear.mana", BAR_Y + 13);
        bar(g, font, BAR_Y + 13, maxMana <= 0 ? 0f : (float) (mana / maxMana), 0xFF3C82D2, (int) Math.floor(mana) + " / " + (int) Math.ceil(maxMana));
        boolean top = data.level >= GrowthRules.MAX_LEVEL;
        label(g, font, "gui.skyabyss.gear.xp", BAR_Y + 26);
        g.fill(BAR_X, BAR_Y + 29, BAR_X + BAR_W, BAR_Y + 33, Golden.OUTLINE);
        float xf = top ? 1f : (float) data.xp / Math.max(1, data.xpNeed());
        g.fill(BAR_X + 1, BAR_Y + 30, BAR_X + 1 + Math.round((BAR_W - 2) * Mth.clamp(xf, 0f, 1f)), BAR_Y + 32, 0xFF000000 | Golden.GOLD);
        // 数: 攻撃と守り
        int sy = BAR_Y + 39;
        stat(g, font, "gui.skyabyss.gear.attack", (int) Math.round(jp.revati.skyabyss.Hp.shown(p.getAttributeValue(Attributes.ATTACK_DAMAGE))), LEFT.x() + 6, sy);
        stat(g, font, "gui.skyabyss.gear.armor", p.getArmorValue(), LEFT.x() + 6 + (LEFT.w() - 12) / 2 + 3, sy);
        // 右: 袋・手もと・スキル
        int used = 0;
        for (int i = 9; i < 36; i++) {
            if (!p.getInventory().items.get(i).isEmpty()) {
                used++;
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.gear.bag"), RIGHT.x() + 8, RIGHT.y() + 6, Golden.PALE, false);
        String count = used + " / 27";
        g.drawString(font, count, RIGHT.right() - 8 - font.width(count), RIGHT.y() + 6, Golden.DIM, false);
        for (int i = 0; i < 27; i++) {
            Golden.slot(g, bagX(i % 9) - 1, bagY(i / 9) - 1, 18);
        }
        Golden.rule(g, RIGHT.x() + 8, RIGHT.right() - 8, RIGHT.y() + 84);
        g.drawString(font, Component.translatable("gui.skyabyss.gear.hand"), RIGHT.x() + 8, RIGHT.y() + 90, Golden.PALE, false);
        for (int i = 0; i < 9; i++) {
            Golden.slot(g, bagX(i) - 1, HOT_Y - 1, 18);
            if (i == p.getInventory().selected) {
                Golden.outline(g, bagX(i) - 2, HOT_Y - 2, bagX(i) + 18, HOT_Y + 18, 0xFF000000 | Golden.GOLD);
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.gear.skills"), RIGHT.x() + 8, RIGHT.y() + 130, Golden.PALE, false);
        for (int i = 0; i < sd.slotCount(); i++) {
            int x = bagX(i);
            Golden.slot(g, x - 1, SKILL_Y - 1, 18);
            Skill s = sd.slot(i);
            if (s != null) {
                jp.revati.skyabyss.rpg.client.skill.SkillHud.drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(p, s), x, SKILL_Y);
            }
        }
        g.pose().popPose();
        // 人形 (画面の座標で)
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + ARMOR_X + 21, py + COL_Y, px + ACC_X - 5, py + armorY(4) + 16, 34, 0.0625f,
                mouseX, mouseY, p);
    }

    private static void label(GuiGraphics g, Font font, String key, int y) {
        g.drawString(font, Component.translatable(key), LEFT.x() + 6, y + 1, Golden.SOFT, false);
    }

    private static void bar(GuiGraphics g, Font font, int y, float frac, int color, String text) {
        g.fill(BAR_X, y, BAR_X + BAR_W, y + 10, Golden.OUTLINE);
        int fill = Math.round((BAR_W - 2) * Mth.clamp(frac, 0f, 1f));
        if (fill > 0) {
            g.fill(BAR_X + 1, y + 1, BAR_X + 1 + fill, y + 9, color);
            g.fill(BAR_X + 1, y + 1, BAR_X + 1 + fill, y + 2, 0x40FFFFFF);
        }
        g.drawString(font, text, BAR_X + BAR_W - 3 - font.width(text), y + 1, 0xFAF6EE, true);
    }

    private static void stat(GuiGraphics g, Font font, String key, int value, int x, int y) {
        g.drawString(font, Component.translatable(key), x, y, Golden.SOFT, false);
        String v = Integer.toString(value);
        g.drawString(font, v, x + (LEFT.w() - 12) / 2 - 3 - font.width(v), y, Golden.TEXT, false);
    }

    // 見るだけの品 (持ち物の画面の装飾・装飾の画面の防具)。少し沈めて描き、重ねると品の説明
    public static void shown(GuiGraphics g, Font font, int px, int py, int x, int y, ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(stack, px + x, py + y);
        g.renderItemDecorations(font, stack, px + x, py + y);
        if (mouseX >= px + x - 1 && mouseX < px + x + 17 && mouseY >= py + y - 1 && mouseY < py + y + 17) {
            g.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- つまみ (見出しの帯の左)
    public static void addTabs(Consumer<AbstractButton> add, IntSupplier left, IntSupplier top, int selected) {
        AccessoryScreen.restoreCursor();
        for (int i = 0; i < 4; i++) {
            Tab tab = new Tab(left, top, i, i == selected);
            tab.place();
            add.accept(tab);
        }
    }

    static final class Tab extends AbstractButton {
        private final IntSupplier left;
        private final IntSupplier top;
        private final int which;
        private final boolean selected;

        Tab(IntSupplier left, IntSupplier top, int which, boolean selected) {
            super(0, 0, TAB_W, Golden.BTN, AccessoryScreen.tabName(which));
            this.left = left;
            this.top = top;
            this.which = which;
            this.selected = selected;
        }

        private void place() {
            setX(left.getAsInt() + L.head().x() + Golden.S3 + which * (TAB_W + Golden.S3));
            setY(top.getAsInt() + L.head().y() + 4);
        }

        @Override
        public void onPress() {
            if (!selected) {
                AccessoryScreen.go(which);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            place();
            Golden.tab(g, Minecraft.getInstance().font, new Golden.Box(getX(), getY(), TAB_W, Golden.BTN), getMessage(), selected, isHoveredOrFocused());
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
}
