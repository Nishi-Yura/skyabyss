package jp.revati.skyabyss.story.ch6.mob;

import jp.revati.skyabyss.abyss.mobs.AbyssMobB;
import jp.revati.skyabyss.story.ch6.MoonClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 月の敵 (docs/design/46 の 4.3。数字は 36 の 4.2 の曲線を延ばす)。深淵の B 組の決まり (段の絵・予告つきの近接 Tell・保存しない) をそのまま使う。
//   数字: 元の値 × 区画の倍率 (区画 1 = 体力 1.6・傷 1.3 = 深淵の底より少し上)。合った位相 (区画 1 は新月) では さらに 1.2 倍で、姿は二段目
//   姿の三段目は主の分身 (影抱きが影に置く綴じ手)
public abstract class MoonMob extends AbyssMobB {
    public static final double[] SECTION_HP = {1.6, 1.85, 2.1, 2.35};
    public static final double[] SECTION_DMG = {1.3, 1.4, 1.5, 1.6};
    public static final double MATCHED = 1.2;
    // 成長の経験値の推奨の段 (六章の帯 Lv 50〜。区画 1 / 区画 2 / 区画 3)
    public static final int MOON_LEVEL = 52;
    public static final int MOON_LEVEL_2 = 54;
    public static final int MOON_LEVEL_3 = 56;
    private double moonDmg;

    protected MoonMob(EntityType<? extends Monster> type, Level level, String prefix, String... actions) {
        super(type, level, prefix, actions);
    }

    // 湧いたときに一度。section は区画 (0〜3)、phase は区画の位相 (合えば強い)
    public void tuneMoon(int section, boolean matched, boolean clone, double hp, double dmg) {
        int form = clone ? 3 : matched ? 2 : 1;
        tune(form, hp, dmg);
        double m = matched ? MATCHED : 1.0;
        double max = hp * SECTION_HP[section] * m;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        setHealth((float) max);
        moonDmg = dmg * SECTION_DMG[section] * m;
    }

    // 区画 1 の位相 (新月) が合っているか
    public static boolean newMoon(ServerLevel level) {
        return MoonClock.phase(level) == 0;
    }

    // 区画 2 の位相 (上弦) が合っているか
    public static boolean waxing(ServerLevel level) {
        return MoonClock.phase(level) == 1;
    }

    // 区画 3 の位相 (満月) が合っているか
    public static boolean fullMoon(ServerLevel level) {
        return MoonClock.phase(level) == 2;
    }

    @Override
    protected float dmg() {
        return (float) moonDmg;
    }

    @Override
    protected float dmg(double factor) {
        return (float) (moonDmg * factor);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 56;
    }
}
