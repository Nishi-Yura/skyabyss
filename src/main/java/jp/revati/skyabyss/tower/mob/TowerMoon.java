package jp.revati.skyabyss.tower.mob;

import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.PilgrimMoon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 落ちる月 (あいだの塔。docs/design/54 の 2.5 の New Sun)。主が体力 50% と 20% で呼ぶ。橋の北から南へ進みながら大きくなる
// (進みと大きさは TowerFight が決める)。南の端に着く前に壊さないと全滅。形は巡礼者の月 (pilgrim_moon) を借りる
public class TowerMoon extends CloudMob {
    public static final double HP = 1200;
    public static final double DMG = 22;

    public TowerMoon(EntityType<? extends Monster> type, Level level) {
        super(type, level, PilgrimMoon.ID, "pull", "sweep", "rewind", "fall", "call", "hurt", "death");
        this.xpReward = 0;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public float hit(double factor) {
        return dmg(factor);
    }

    public void setSize(double scale) {
        getAttribute(Attributes.SCALE).setBaseValue(scale);
    }

    @Override
    public void tick() {
        super.tick();
        setYRot(0f);
        yBodyRot = 0f;
        yHeadRot = 0f;
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
