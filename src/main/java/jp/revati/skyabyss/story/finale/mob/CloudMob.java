package jp.revati.skyabyss.story.finale.mob;

import jp.revati.skyabyss.abyss.mobs.AbyssMobB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 雲海の敵 (docs/design/50 の 4.2)。深淵の B 組の決まり (段の絵・予告つきの近接 Tell・保存しない) をそのまま使う。
//   数字は湧いたときに tuneCloud で入れる (月の段 5 の少し上。高さの倍率はかけない)。雲の海の下へ落ちたら消える
public abstract class CloudMob extends AbyssMobB {
    // 成長の経験値の推奨の段 (終章。月の区画 4 = 58 の上)
    public static final int CLOUD_LEVEL = 60;
    static final double LOST_Y = 20;
    private double cloudDmg;

    protected CloudMob(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
        super(type, level, prefix, actions);
    }

    // 湧いたときに一度。form は姿の段 (1〜3)
    public void tuneCloud(int form, double hp, double dmg) {
        tune(form, hp, dmg);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth((float) hp);
        cloudDmg = dmg;
    }

    @Override
    protected float dmg() {
        return (float) cloudDmg;
    }

    @Override
    protected float dmg(double factor) {
        return (float) (cloudDmg * factor);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getY() < LOST_Y) {
            discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 56;
    }
}
