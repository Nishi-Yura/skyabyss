package jp.revati.skyabyss.titan.ember.mob;

import jp.revati.skyabyss.registry.MobSounds;
import java.util.EnumSet;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 灰被り (13 の 5.1節): 消えた灰の民の名残。冷えた灰の殻をまとい、火掻き棒を振り下ろす (届く 3)。
// 灰の殻: 体力とは別に殻 30。殻があるあいだは受ける傷が半分で、殻にも同じだけ入る。
//   火照った人 (熱 50 以上、EmberHeat.flushed) の攻撃と、熱の噴き (傷の種類 heat_burst。噴気・亀裂) は殻に 2 倍。
// 殻が割れると「燃え上がる」: 割れ目が赤く光り、速さ 1.3 倍、攻撃 16、受ける傷 1.25 倍。15 秒で燃え尽きて倒れる
// (倒した扱い: 最後に叩いた人の撃破として、経験値と落とす物が出る。熾を落とす所も同じ)。
// 振り下ろしは 0.5 秒の振りかぶり (火掻き棒を頭の後ろへ) を見せてから。前方左右 60 度、体の縁から 3 ブロック
public class AshThrall extends EmberMob {
    private static final EntityDataAccessor<Float> SHELL = SynchedEntityData.defineId(AshThrall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FLARED = SynchedEntityData.defineId(AshThrall.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceLocation FLARE_SPEED = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ash_thrall_flare_speed");
    private static final ResourceLocation FLARE_DAMAGE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ash_thrall_flare_damage");

    public static final float HEALTH = 60;
    public static final float DAMAGE = 13;
    public static final float ARMOR = 10;
    public static final float SHELL_MAX = 30;
    // 殻があるあいだ、体と殻に入る割合
    public static final float SHELL_SHARE = 0.5f;
    // 火照った人・熱の噴きの、殻への倍率
    public static final float SHELL_HEAT_MULT = 2f;
    // 燃え上がり
    public static final float FLARED_SPEED = 1.3f;
    public static final float FLARED_DAMAGE = 16;
    public static final float FLARED_TAKEN = 1.25f;
    public static final int BURNOUT_TICKS = 300;
    public static final double SPEED = 0.22;
    // 振り下ろしが届く距離 (体の前の縁から)、前方の角度
    public static final double REACH = 3.0;
    static final double ARC = 60;
    // 振りかぶりから当たるまで (tick。アニメの 0.5 秒)、次まで
    public static final int WINDUP = 10;
    static final int COOLDOWN = 26;

    private int burnLeft;
    // 最後に叩いた人 (燃え尽きたときの撃破の名義)
    private UUID lastPlayer;

    public AshThrall(EntityType<? extends Monster> type, Level level) {
        this(type, level, EmberIds.ASH_THRALL, new String[] {"attack", "shell_break"});
    }

    // 変種 (四章の地上の灰被りの屑・古い灰被り。story/ch4/mob) 用: アニメの名前の頭と動作を替える
    protected AshThrall(EntityType<? extends Monster> type, Level level, String prefix, String[] actions) {
        super(type, level, prefix, actions);
        this.xpReward = 10;
    }

    // 殻の最大 (変種が替える。defineSynchedData から呼ばれるので、定数を返すこと)
    public float shellMax() {
        return SHELL_MAX;
    }

    // 燃え上がったときの攻撃 (変種が替える)
    protected float flaredDamage() {
        return FLARED_DAMAGE;
    }

    // 燃え上がってから燃え尽きるまで (tick。0 なら燃え尽きない。名づきの古い灰被り)
    protected int burnoutTicks() {
        return BURNOUT_TICKS;
    }

    // 殻が割れて燃え上がった瞬間 (変種が熱の波などを足す)
    protected void onIgnite() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHELL, shellMax());
        builder.define(FLARED, false);
    }

    public float shell() {
        return entityData.get(SHELL);
    }

    public boolean isFlared() {
        return entityData.get(FLARED);
    }

    // 燃え尽きるまでの残り (tick。燃え上がっていなければ 0)
    public int burnLeft() {
        return burnLeft;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new SwingGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String walkAnimation() {
        return isFlared() ? "walk_flared" : "walk";
    }

    // ---------------------------------------------------------------- 灰の殻と燃え上がり

    // 傷の分け方 (アーマーで減る前の値で決める)。殻があるあいだは体へ半分、殻へ同じだけ (火照り・熱の噴きは殻へ 2 倍)。
    // 燃え上がったあとは 1.25 倍。NeoForge は actuallyHurt に渡す値を使わない (傷の入れ物の値を使う) ので、hurt で量を変える。
    // 殻が減るのは、傷が通ったとき (無敵時間で弾かれたときは減らない) だけ
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source) || isDeadOrDying()) {
            return super.hurt(source, amount);
        }
        if (source.getEntity() instanceof ServerPlayer p) {
            lastPlayer = p.getUUID();
        }
        if (isFlared()) {
            return super.hurt(source, amount * FLARED_TAKEN);
        }
        if (shell() <= 0) {
            return super.hurt(source, amount);
        }
        float toShell = amount * SHELL_SHARE * shellMultiplier(source);
        boolean hit = super.hurt(source, amount * SHELL_SHARE);
        if (hit) {
            float left = Math.max(0f, shell() - toShell);
            entityData.set(SHELL, left);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()),
                        getX(), getY() + 1.4, getZ(), 8, 0.3, 0.5, 0.3, 0.05);
            }
            if (left <= 0f && isAlive()) {
                ignite();
            }
        }
        return hit;
    }

    // 殻への倍率: 火照った人 (熱 50 以上) の攻撃と、熱の噴き (噴気・亀裂) は 2 倍
    public static float shellMultiplier(DamageSource source) {
        if (source.is(EmberIds.HEAT_BURST)) {
            return SHELL_HEAT_MULT;
        }
        if (source.getEntity() instanceof Player p && EmberHeat.flushed(p)) {
            return SHELL_HEAT_MULT;
        }
        return 1f;
    }

    // 殻が割れて燃え上がる
    public void ignite() {
        if (isFlared()) {
            return;
        }
        entityData.set(SHELL, 0f);
        entityData.set(FLARED, true);
        burnLeft = burnoutTicks();
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(FLARE_SPEED)) {
            speed.addPermanentModifier(new AttributeModifier(FLARE_SPEED, FLARED_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && !dmg.hasModifier(FLARE_DAMAGE)) {
            dmg.addPermanentModifier(new AttributeModifier(FLARE_DAMAGE, flaredDamage() - (float) dmg.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        }
        playAction("shell_break");
        onIgnite();
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()),
                    getX(), getY() + 1.2, getZ(), 40, 0.4, 0.8, 0.4, 0.15);
            server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.4, getZ(), 6, 0.3, 0.5, 0.3, 0.1);
            server.playSound(null, getX(), getY(), getZ(), EmberMobSetup.THRALL_SHELL_BREAK.get(), SoundSource.HOSTILE, 1.2f, 1f);
            server.playSound(null, getX(), getY(), getZ(), EmberMobSetup.THRALL_IGNITE.get(), SoundSource.HOSTILE, 1f, 1f);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (isFlared() && random.nextInt(3) == 0) {
                // 燃え上がった体から上る火の粉 (見た目だけ)
                level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                        getRandomX(0.5), getY() + 0.4 + random.nextDouble() * 1.8, getRandomZ(0.5), 0, 0.03, 0);
            }
            return;
        }
        if (isFlared() && isAlive() && burnLeft > 0 && --burnLeft == 0) {
            burnOut();
        }
    }

    // 燃え尽きて倒れる。最後に叩いた人がいれば、その人が倒した扱いにする (経験値・落とす物)
    void burnOut() {
        ServerPlayer credit = null;
        if (lastPlayer != null && level() instanceof ServerLevel server && server.getPlayerByUUID(lastPlayer) instanceof ServerPlayer p
                && p.isAlive() && p.level() == level()) {
            credit = p;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE_POWDER.defaultBlockState()),
                    getX(), getY() + 0.6, getZ(), 30, 0.4, 0.4, 0.4, 0.05);
        }
        if (credit != null) {
            setLastHurtByPlayer(credit);
            hurt(damage(EmberIds.HEAT, credit), getMaxHealth() * 10);
        } else {
            hurt(damage(EmberIds.HEAT, null), getMaxHealth() * 10);
        }
        if (isAlive()) {
            // 何かで防がれたとき (試練の都合で無敵など) は、そのまま消す
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Shell", shell());
        tag.putBoolean("Flared", isFlared());
        tag.putInt("BurnLeft", burnLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Shell")) {
            entityData.set(SHELL, tag.getFloat("Shell"));
        }
        if (tag.getBoolean("Flared")) {
            entityData.set(FLARED, false);
            ignite();
            burnLeft = burnoutTicks() > 0 ? Math.max(1, tag.getInt("BurnLeft")) : 0;
        }
    }

    // ---------------------------------------------------------------- 振り下ろし

    // 振り下ろしが当たるか: 体の前の縁から REACH 以内で、前方 (左右 ARC 度) にいる
    public boolean inSwingReach(LivingEntity target) {
        Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
        double dist = to.length() - getBbWidth() / 2 - target.getBbWidth() / 2;
        if (dist > REACH || Math.abs(target.getY() - getY()) > 2.5) {
            return false;
        }
        if (to.lengthSqr() < 1e-4) {
            return true;
        }
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        return look.dot(to.normalize()) > Math.cos(Math.toRadians(ARC));
    }

    private static final class SwingGoal extends Goal {
        private final AshThrall mob;
        private int windup = -1;
        private int cooldown;
        private int repath;

        SwingGoal(AshThrall mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || windup >= 0;
        }

        @Override
        public void stop() {
            windup = -1;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (cooldown > 0) {
                cooldown--;
            }
            if (windup >= 0) {
                // 振りかぶりの間は止まって、相手のほうを向き続ける
                mob.getNavigation().stop();
                if (t != null) {
                    mob.getLookControl().setLookAt(t, 20f, 20f);
                    face(t, 10f);
                }
                if (--windup == 0) {
                    windup = -1;
                    cooldown = COOLDOWN;
                    if (t != null && t.isAlive() && mob.inSwingReach(t)) {
                        mob.doHurtTarget(t);
                    }
                    if (mob.level() instanceof ServerLevel server) {
                        Vec3 f = Vec3.directionFromRotation(0, mob.getYRot()).scale(2.0);
                        server.sendParticles(ParticleTypes.LAVA, mob.getX() + f.x, mob.getY() + 0.2, mob.getZ() + f.z, 3, 0.3, 0.1, 0.3, 0);
                        server.playSound(null, mob.getX(), mob.getY(), mob.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                                SoundSource.HOSTILE, 0.8f, 0.6f + mob.getRandom().nextFloat() * 0.15f);
                    }
                }
                return;
            }
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = mob.distanceTo(t) - mob.getBbWidth() / 2 - t.getBbWidth() / 2;
            if (d <= REACH - 0.6 && mob.hasLineOfSight(t)) {
                mob.getNavigation().stop();
                face(t, 20f);
                if (cooldown == 0 && mob.inSwingReach(t)) {
                    windup = WINDUP;
                    mob.playAction("attack");
                }
            } else if (--repath <= 0) {
                repath = 8;
                mob.getNavigation().moveTo(t, 1.0);
            }
        }

        private void face(LivingEntity t, float max) {
            float want = (float) (Mth.atan2(t.getZ() - mob.getZ(), t.getX() - mob.getX()) * (180 / Math.PI)) - 90f;
            float yaw = Mth.approachDegrees(mob.getYRot(), want, max);
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
    }

    // ---------------------------------------------------------------- 音

    @Override
    protected SoundEvent getAmbientSound() {
        return EmberMobSetup.THRALL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EmberMobSetup.THRALL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EmberMobSetup.THRALL_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.ASH_THRALL_STEP.get(), 0.3f);
    }
}
