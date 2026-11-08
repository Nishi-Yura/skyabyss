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
}
