package jp.revati.skyabyss.titan.storm.chapter.mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jp.revati.skyabyss.entity.GroundFoe;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 帯電毛玉 (docs/design/21 の 5.1。三章の地上): 連鎖の予習 (背中の雷蛾)。宙に浮く綿毛の塊。HP 10、放電 4。
//   帯電     狙いの人が CHARGE_RANGE 以内にいると、CHARGE_TICKS (4 秒) かけて帯電する。予告は「毛が逆立つ」(アニメ charge。翅ではない) と火花
//   放電     満ちたら、RANGE 以内のいちばん近い人へ 4。そこから CHAIN_RANGE 以内の別の人へ跳ぶ (最大 MAX_CHAIN 人、跳ぶごとに FALLOFF 倍)。
//            人が固まっていると隣へ渡る (離れて立てば一人で済む)。放電のあと REST だけ休む
//   天気     晴れた昼はおとなしい (calm。自分から狙わない。叩かれれば怒る)。雨・雷雨はひとり歩きの湧きが増える (Ch3Mobs)
//   落とす物 帯電綿 50%、焦げ羽 10%
public class StaticPuff extends GroundFoe {
    public static final float HEALTH = 10;
    public static final float DISCHARGE_DAMAGE = 4;
    public static final double CHARGE_RANGE = 10;
    public static final double RANGE = 4.0;
    public static final double CHAIN_RANGE = 4.0;
    public static final int MAX_CHAIN = 3;
    public static final float FALLOFF = 0.8f;
    public static final int CHARGE_TICKS = 80;
    public static final int REST = 40;
    static final double ORBIT = 3.2;

    private static final EntityDataAccessor<Float> CHARGE = SynchedEntityData.defineId(StaticPuff.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation A_CHARGE = RawAnimation.begin().thenPlayAndHold("animation.static_puff.charge");

    private int rest;
    private double orbit;

    public StaticPuff(EntityType<? extends Monster> type, Level level) {
        super(type, level, "static_puff", "discharge", "charge", "hurt", "death");
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 2;
        setNoGravity(true);
        orbit = random.nextDouble() * Math.PI * 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DISCHARGE_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, 0f);
    }

    public float getCharge() {
        return entityData.get(CHARGE);
    }

    public void setCharge(float c) {
        entityData.set(CHARGE, Math.max(0f, Math.min(1f, c)));
    }

    public boolean isFull() {
        return getCharge() >= 0.999f;
    }

    // 晴れた昼 (雨でなく、昼) はおとなしい。自分から狙わない
    public boolean calm() {
        long t = level().getDayTime() % 24000L;
        return !level().isRaining() && t < 12500;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new HoverGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.6));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> !calm()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        // 帯電の間は毛が逆立つ (4 秒かけて開ききり、そのまま)
        controllers.add(new AnimationController<>(this, "charge", 3, s -> getCharge() > 0.02f && !isDeadOrDying() ? s.setAndContinue(A_CHARGE) : PlayState.STOP));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (getCharge() > 0.4f && random.nextFloat() < getCharge() * 0.5f) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.7), getRandomY(), getRandomZ(0.7), 0, 0, 0);
            }
            return;
        }
        LivingEntity t = getTarget();
        boolean hunting = t != null && t.isAlive() && t instanceof ServerPlayer sp && canHit(sp) && distanceTo(t) < CHARGE_RANGE;
        if (rest > 0) {
            rest--;
        } else if (hunting && !isFull()) {
            if (getCharge() <= 0f) {
                playSound(Ch3Mobs.PUFF_CHARGE.get(), 0.9f, 1.1f);
            }
            setCharge(getCharge() + 1f / CHARGE_TICKS);
        } else if (!hunting && !isFull()) {
            setCharge(getCharge() - 0.01f);
        }
        if (isFull() && rest == 0) {
            List<Hit> plan = plan(position().add(0, getBbHeight() / 2, 0), nearPlayers(), e -> hasLineOfSight(e));
            if (!plan.isEmpty()) {
                discharge(plan);
            }
        }
    }

    private List<LivingEntity> nearPlayers() {
        List<LivingEntity> out = new ArrayList<>();
        for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
            if (canHit(p) && !p.isCreative() && p.distanceToSqr(this) < (CHAIN_RANGE * 4) * (CHAIN_RANGE * 4)) {
                out.add(p);
            }
        }
        return out;
    }

    public record Hit(LivingEntity target, float damage) {
    }

    // 放電の跳び方: origin から RANGE 以内のいちばん近い人へ、そこから CHAIN_RANGE 以内のまだ当たっていない一番近い人へ (最大 MAX_CHAIN 人、FALLOFF 倍ずつ)
    public static List<Hit> plan(Vec3 origin, List<? extends LivingEntity> candidates, java.util.function.Predicate<LivingEntity> canSee) {
        List<Hit> hits = new ArrayList<>();
        List<LivingEntity> left = new ArrayList<>(candidates);
        Vec3 from = origin;
        float damage = DISCHARGE_DAMAGE;
        double range = RANGE;
        while (hits.size() < MAX_CHAIN) {
            LivingEntity best = null;
            double bestD = range * range;
            for (LivingEntity e : left) {
                double d = e.position().add(0, e.getBbHeight() / 2, 0).distanceToSqr(from);
                if (d <= bestD && (!hits.isEmpty() || canSee.test(e))) {
                    bestD = d;
                    best = e;
                }
            }
            if (best == null) {
                break;
            }
            hits.add(new Hit(best, damage));
            left.remove(best);
            from = best.position().add(0, best.getBbHeight() / 2, 0);
            damage *= FALLOFF;
            range = CHAIN_RANGE;
        }
        return hits;
    }

    private void discharge(List<Hit> plan) {
        playAction("discharge");
        playSound(Ch3Mobs.PUFF_DISCHARGE.get(), 1.1f, 1.2f);
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        for (Hit h : plan) {
            Vec3 to = h.target().position().add(0, h.target().getBbHeight() / 2, 0);
            arc(from, to);
            h.target().hurt(attackSource(), h.damage());
            from = to;
        }
        setCharge(0);
        rest = REST;
    }

    private void arc(Vec3 a, Vec3 b) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 d = b.subtract(a);
        int n = Math.max(4, (int) (d.length() * 4));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double j = (i % 2 == 0 ? 1 : -1) * 0.12 * Math.sin(t * Math.PI);
            Vec3 p = a.add(d.scale(t)).add(j, j * 0.5, -j);
            warn(server, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // 狙いのまわりを ORBIT 離れて漂い、満ちたら寄る
    private static final class HoverGoal extends Goal {
        private final StaticPuff mob;
        private int repath;

        HoverGoal(StaticPuff mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || --repath > 0) {
                return;
            }
            repath = 8;
            mob.getLookControl().setLookAt(t, 30f, 30f);
            Vec3 goal;
            if (mob.getCharge() > 0.75f) {
                goal = t.position().add(0, 1.4, 0);
            } else {
                mob.orbit += 0.3;
                goal = t.position().add(Math.cos(mob.orbit) * ORBIT, 1.5 + Math.sin(mob.orbit * 1.3) * 0.4, Math.sin(mob.orbit) * ORBIT);
            }
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            playAction("hurt");
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            playAction("death");
            setCharge(0);
        }
    }

    // 帯電綿 50%、焦げ羽 10%
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        Item cotton = Ch3Mobs.item(Ch3Mobs.STATIC_COTTON);
        Item feather = Ch3Mobs.item(Ch3Mobs.SCORCHED_FEATHER);
        if (cotton != null && random.nextFloat() < 0.5f) {
            spawnAtLocation(new ItemStack(cotton));
        }
        if (feather != null && random.nextFloat() < 0.1f) {
            spawnAtLocation(new ItemStack(feather));
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch3Mobs.PUFF_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch3Mobs.PUFF_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch3Mobs.PUFF_DEATH.get();
    }
}
