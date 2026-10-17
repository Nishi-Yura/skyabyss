package jp.revati.skyabyss.item.storm;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

// 静電避け (雲間の干し果を食べて 30 秒)。それ自体は何もしない印の効き目。
// 雷蛾の放電の連鎖 (12 の 5.1節) が、持つ者には跳ばず、持つ者から次へも跳ばない (敵の担当が skyabyss:static_ward で引く)
public class StaticWardEffect extends MobEffect {
    public StaticWardEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xBFD8FF);
    }
}
