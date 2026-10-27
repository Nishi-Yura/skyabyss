package jp.revati.skyabyss.story.ch4.mob;

import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 灰被りの屑: 灰被りの未完成。殻の予習。HP 28、殴り 8、殻 15。
// 殻があるあいだは傷が半分 (灰被りと同じ AshThrall の決まり)。殻が割れると燃えて速くなり (殴り 10)、10 秒で燃え尽きる。
// 骨とアニメは灰被りのもの (animation.ash_thrall.*)。描画は灰被りの 0.85 倍 (Ch4Renderers)
public class AshHusk extends AshThrall {
    public static final float HEALTH = 28;
    public static final float DAMAGE = 8;
    public static final float SHELL = 15;
    public static final float FLARED = 10;
    public static final int BURNOUT = 200;

    public AshHusk(EntityType<? extends Monster> type, Level level) {
        super(type, level, EmberIds.ASH_THRALL, new String[] {"attack", "shell_break"});
        this.xpReward = 4;
    }

    public static AttributeSupplier.Builder createHuskAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    public float shellMax() {
        return SHELL;
    }

    @Override
    protected float flaredDamage() {
        return FLARED;
    }

    @Override
    protected int burnoutTicks() {
        return BURNOUT;
    }

    // 煤の砥石で研いだ刃は、殻へ 1.5 倍 (殻の傷は AshThrall.hurt が量から決めるので、殻のあるあいだだけ量を上げる)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isFlared() && shell() > 0) {
            amount *= Ch4Mobs.shellBonus(source);
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, Ch4Setup.ASH_HEMP.get(), 0.4, 1, 1);
        Ch4Mobs.drop(level, this, source, Ch4Setup.SOOT_IRON.get(), 0.15, 1, 1);
    }
}
