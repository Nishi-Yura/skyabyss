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

    // 品の札 (切り絵の紙の札・稀さは右上の菱・持っていなければ「？」) と Lv の点。黒い紙の上 (試練の板) に置く。seq は札の傾きを散らす番号
    public static void aspectTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int size, Aspect a, int lv, boolean hover, int seq) {
        ExtPage.paperTile(g, font, x, y, size, a, lv, hover, false, false, seq);
        if (lv > 0) {
            ExtPage.paperPips(g, x, y + size + 2, size, lv);
        }
    }

    public static Component groupName(String shrine) {
        return ExtPage.groupName(shrine);
    }
}
