package jp.revati.skyabyss.rpg.client.skill;

import java.util.List;

import jp.revati.skyabyss.rpg.aspect.Aspect;
import jp.revati.skyabyss.rpg.skill.SkillData;
import net.minecraft.network.chat.Component;

// K 画面のアスペクトの頁の色と説明を、ほかの画面 (祠の司祭の「報い」の頁) でも同じに見せるための口
public final class SkillScreenAccess {
    private SkillScreenAccess() {
    }

    public static int rarityColor(int rarity) {
        return ExtPage.RARITY[rarity];
    }

    public static List<Component> aspectTip(SkillData d, Aspect a) {
        return ExtPage.tip(d, a);
    }

    // 品の札 (技の絵・稀さの縁・持っていなければ「？」) と Lv の点
    public static void aspectTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int size, Aspect a, int lv, boolean hover, int ticks) {
        ExtPage.tile(g, font, x, y, size, a, lv, hover, false, ticks);
        if (lv > 0) {
            ExtPage.pips(g, x, y + size + 1, size, lv, a.rarity());
        }
    }

    public static Component groupName(String shrine) {
        return ExtPage.groupName(shrine);
    }
}
