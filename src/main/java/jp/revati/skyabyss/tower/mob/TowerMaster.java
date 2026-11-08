package jp.revati.skyabyss.tower.mob;

import jp.revati.skyabyss.story.finale.mob.CloudMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// あいだに座すもの (超大型レイド「あいだの塔」の主。docs/design/54 の 2.5。形は 2026-11-02 の案 C「門を背負う者」tools/rough/lowpoly/cloud/tower_master_c.py)。
//   動かない。原点は北の広場の床の、玉座寄り (主の当たりの箱の底の真ん中)。正面は南 (橋の方)
//   当たり   幅 28・高さ 32 の箱。北の広場の手の置き場から玉座までを覆い、広場からどの職でも打てる
//   技と段   tower/TowerFight が動かす (この体は動作を見せ、傷を受けるだけ)
//   floor    体力がこの値より下がらない (落ちる月の段の手前で止める)。shielded のあいだは傷が通らない (月が生きているあいだ・目覚め)
//   形は 5 倍で描く (CloudMobRenderers)。天の手 (金・右手) は東 (x が正。橋から見て右)、淵の手 (黒い石・左手) は西。背に「あいだの門」(上 36・後ろ 30 ブロック、半径 28)
public class TowerMaster extends CloudMob {
    public static final String ID = "tower_master";
    public static final double HP = 6000;
    public static final double DMG = 26;
    // 倒れてから消えるまで (越えた場面で門が閉じる 8 秒。TowerFight.win)
    public static final int DEATH_TICKS = 160;
    private float floor;
    private boolean shielded;

    public TowerMaster(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "rake", "flense", "clap", "breath", "sigil", "beams", "stars", "arrows", "roar", "stoke", "palm", "moon", "bow", "hurt", "death");
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

    // 技の傷 (決めた傷 × factor)
    public float hit(double factor) {
        return dmg(factor);
    }

    // 床は最大体力の割合で持つ (あとで体力に倍率が掛かっても同じ所で止まる。2026-11-09 の combat/Hardness)
    public void setFloor(float hp) {
        floor = getMaxHealth() > 0 ? hp / getMaxHealth() : 0;
    }

    public boolean shielded() {
        return shielded;
    }

    public void setShielded(boolean on) {
        shielded = on;
    }

    // 光の出る所 (ラフの座標 (x, y, z) は 位置 + ((y + 144) / 16, -(z + 192) / 16) ブロック)
    //   head  角のあいだの印 (追う印の光・高まりの光)   mouth  縦に裂ける口 (淵の吐息)
    //   chest 金の肋の奥の核                             crown  角の上に開く印 (引き合いの高まり)
    public Vec3 head() {
        return position().add(0, 42.75, -7.6);
    }

    public Vec3 mouth() {
        return position().add(0, 33.1, -4.5);
    }

    public Vec3 chest() {
        return position().add(0, 24.6, -2.6);
    }

    public Vec3 crown() {
        return position().add(0, 49.0, -9.5);
    }

    @Override
    public void setHealth(float hp) {
        super.setHealth(isAlive() && hp > 0 ? Math.max(hp, floor * getMaxHealth()) : hp);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (shielded && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (level() instanceof ServerLevel sl && tickCount % 5 == 0) {
                Vec3 c = chest();
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 12, 3.0, 3.0, 1.0, 0.1);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        setYRot(0f);
        yBodyRot = 0f;
        yHeadRot = 0f;
        setXRot(0f);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    // 体が大きいので、箱が画面の外でも形が見えていれば描く (背の門の上の端は床から 64、後ろの端は 32)
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(16, 34, 22);
    }

    @Override
    public boolean isInWall() {
        return false;
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
