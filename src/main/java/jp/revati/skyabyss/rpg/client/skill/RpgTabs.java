package jp.revati.skyabyss.rpg.client.skill;

import java.util.function.Consumer;

import jp.revati.skyabyss.rpg.client.GrowthClientSetup;
import jp.revati.skyabyss.rpg.client.GrowthScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

// 「記録」(ステータス画面) と「技」(技の画面) を行き来するつまみ。どちらの画面も高さ 184 で、つまみは画面の真ん中から決める
// (二つの画面の幅が違っても、つまみの位置は動かない)。J と K でも行き来できる
public final class RpgTabs {
    public static final int STATUS = 0;
    public static final int SKILLS = 1;
    public static final int REBIRTH = 2;
    static final int TAB_W = 56;
    static final int TAB_H = 16;
    static final int PANEL_H = 184;

    private RpgTabs() {
    }

    // 画面の init から呼ぶ。add には addRenderableWidget を渡す
    public static void add(Consumer<AbstractButton> add, int screenWidth, int screenHeight, int active) {
        int cx = screenWidth / 2;
        int top = (screenHeight - PANEL_H) / 2;
        add.accept(new Tab(cx - 112, top - TAB_H + 2, STATUS, active == STATUS));
        add.accept(new Tab(cx - 112 + TAB_W + 2, top - TAB_H + 2, SKILLS, active == SKILLS));
        // 転生のつまみ: Lv 40 になるか、転生したあとから (序盤に見せない。docs/design/31)
        if (rebirthVisible() || active == REBIRTH) {
            add.accept(new Tab(cx - 112 + 2 * (TAB_W + 2), top - TAB_H + 2, REBIRTH, active == REBIRTH));
        }
    }

    // 画面の keyPressed から呼ぶ。J なら記録、K なら技へ (今の画面と同じなら閉じる)。扱ったら true
    public static boolean keyPressed(int keyCode, int scanCode, int active) {
        Minecraft mc = Minecraft.getInstance();
        if (GrowthClientSetup.STATUS_KEY.matches(keyCode, scanCode)) {
            open(mc, active == STATUS ? -1 : STATUS);
            return true;
        }
        if (SkillClientSetup.SKILLS_KEY.matches(keyCode, scanCode)) {
            open(mc, active == SKILLS ? -1 : SKILLS);
            return true;
        }
        return false;
    }

    public static boolean rebirthVisible() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        var g = jp.revati.skyabyss.rpg.Growth.get(mc.player);
        return g.rebirths > 0 || g.level >= 40;
    }

    static void open(Minecraft mc, int which) {
        if (which == REBIRTH) {
            mc.setScreen(new jp.revati.skyabyss.client.rebirth.SoulScreen());
        } else if (which == STATUS) {
            mc.setScreen(new GrowthScreen());
        } else if (which == SKILLS) {
            mc.setScreen(new SkillScreen());
        } else {
            mc.setScreen(null);
        }
    }

    static final class Tab extends AbstractButton {
        private final int which;
        private final boolean selected;

        Tab(int x, int y, int which, boolean selected) {
            super(x, y, TAB_W, TAB_H, Component.translatable(which == STATUS ? "gui.skyabyss.skill.tab_status"
                    : which == REBIRTH ? "gui.skyabyss.skill.tab_rebirth" : "gui.skyabyss.skill.tab_skills"));
            this.which = which;
            this.selected = selected;
        }

        @Override
        public void onPress() {
            if (!selected) {
                open(Minecraft.getInstance(), which);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int u = selected ? 116 : 0;
            g.pose().pushPose();
            // 選んでいるつまみは画面の縁より手前に描き、縁とつながって見えるようにする
            g.pose().translate(0, 0, selected ? 50 : 0);
            g.blit(SkillHud.TEXTURE, getX(), getY(), TAB_W, TAB_H, u, 470, 112, 32, SkillHud.TEX_W, SkillHud.TEX_H);
            int color = selected ? 0xF2C14E : isHoveredOrFocused() ? 0xE6E2D6 : 0xA9AEBC;
            var font = Minecraft.getInstance().font;
            g.drawString(font, getMessage(), getX() + (TAB_W - font.width(getMessage())) / 2, getY() + 5, color, false);
            g.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
