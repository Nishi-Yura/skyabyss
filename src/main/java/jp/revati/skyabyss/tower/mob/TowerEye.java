package jp.revati.skyabyss.tower.mob;

import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.LampEye;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 側の目 (あいだの塔。docs/design/54 の 2.5 の Eyes of War)。橋の左右の台に据わり、光線を撃つ (撃つ順と狙いは TowerFight)。壊せる。
//   形は灯守の目 (lamp_eye) を借りる。小さい目 4 つ (1 倍) と大きい目 2 つ (1.8 倍。TOWER_EYE_BIG)
public class TowerEye extends CloudMob {
    public static final double HP_SMALL = 260;
    public static final double HP_BIG = 600;
    public static final double DMG = 20;
    public static final double HOVER = 1.2;
    private Vec3 home;

    public TowerEye(EntityType<? extends Monster> type, Level level) {
        super(type, level, LampEye.ID, "stare", "closed", "rain", "call", "hurt", "death");
        this.xpReward = 0;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP_SMALL)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public float hit(double factor) {
        return dmg(factor);
    }

    public void setHome(Vec3 at) {
        home = at;
    }

    // 光の出る所
    public Vec3 eye() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    // 狙う人へ向く
    public void face(Vec3 to) {
        Vec3 d = to.subtract(eye());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && home != null && position().distanceToSqr(home) > 0.04) {
            setPos(home.x, home.y, home.z);
        }
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
