package jp.revati.skyabyss.titan.ember.mob;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.titan.group.GroupOwned;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

// 燠虫 (13 の 5.1節): 玄武の殻の大きな団子虫。ふだんは歩き、人を見ると丸まって (0.8 秒震える予告)、まっすぐ転がって来る。
// 転がりは当たると 12 と強い押し。丸まっている間は防御 20。
// **壁や柱にぶつかると 2 秒目を回して開く** (背を下にして腹が光る。防御 0)。避けて壁に当てるのが攻略。
// 転がった跡に 3 秒、火の粉の帯 (踏むと熱 +5、一人 1 秒に一度)
public class CinderRoller extends EmberMob {
    public enum Mode {
        WALK, CURL, ROLL, DIZZY
    }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(CinderRoller.class, EntityDataSerializers.INT);
    private static final ResourceLocation SHELL_ARMOR = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cinder_roller_shell");

    public static final float HEALTH = 36;
    public static final float DAMAGE = 12;
    public static final float ARMOR = 6;
    public static final float CURLED_ARMOR = 20;
    public static final float DIZZY_ARMOR = 0;
    // 丸まって震える予告 (0.8 秒)、転がる長さの上限 (2 秒)、目を回す長さ (2 秒)、次に丸まるまで
    public static final int CURL_TICKS = 16;
    public static final int ROLL_TICKS = 40;
    public static final int DIZZY_TICKS = 40;
    static final int ROLL_COOLDOWN = 50;
    // 転がる速さ (ブロック/tick)。人の全力疾走 (約 0.28) の倍ほど。予告を見て横へよければ当たらない
    public static final double ROLL_SPEED = 0.55;
    // 転がりの押し
    static final double KNOCKBACK = 1.6;
    // 丸まりを始める距離
    static final double CURL_MIN = 3.0;
    static final double CURL_MAX = 14.0;
    // 火の粉の帯: 残る長さ (3 秒)、踏む判定の半径、熱
    public static final int TRAIL_TICKS = 60;
    static final double TRAIL_RADIUS = 0.7;
    public static final float TRAIL_HEAT = 5;

    private int modeTicks;
    private int cooldown = 30;
    private Vec3 rollDir = Vec3.ZERO;
    private final Deque<Trail> trail = new ArrayDeque<>();
    private final Map<UUID, Integer> trailHit = new HashMap<>();
    private boolean hitSomeone;

    record Trail(Vec3 pos, long until) {
    }

    public CinderRoller(EntityType<? extends Monster> type, Level level) {
        super(type, level, EmberIds.CINDER_ROLLER, "uncurl", "rightself");
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, Mode.WALK.ordinal());
    }

    public Mode mode() {
        return Mode.values()[entityData.get(MODE)];
    }

    // 丸まっている (脚・触角・節の間の膜を隠す。描画が見る)
    public boolean isBall() {
        Mode m = mode();
        return m == Mode.CURL || m == Mode.ROLL;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RollGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected String state() {
        return switch (mode()) {
            case CURL -> "curled";
            case ROLL -> "rolling";
            case DIZZY -> "dizzy";
            default -> null;
        };
    }

    @Override
    protected RawAnimation stateAnimation(String name) {
        return switch (name) {
            case "curled" -> sequence("curl", "curled");
            case "dizzy" -> sequence("pop", "dizzy");
            default -> anim(name, true);
        };
    }

    @Override
    protected boolean frozen() {
        return mode() != Mode.WALK;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || mode() == Mode.CURL || mode() == Mode.DIZZY;
    }

    // ---------------------------------------------------------------- 姿の切り替え

    void setMode(Mode m) {
        Mode before = mode();
        entityData.set(MODE, m.ordinal());
        modeTicks = 0;
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(SHELL_ARMOR);
            float want = switch (m) {
                case CURL, ROLL -> CURLED_ARMOR;
                case DIZZY -> DIZZY_ARMOR;
                default -> ARMOR;
            };
            if (want != ARMOR) {
                armor.addTransientModifier(new AttributeModifier(SHELL_ARMOR, want - ARMOR, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (level().isClientSide) {
            return;
        }
        if (m == Mode.WALK && (before == Mode.CURL || before == Mode.ROLL)) {
            playAction("uncurl");
        } else if (m == Mode.WALK && before == Mode.DIZZY) {
            playAction("rightself");
        }
    }

    // 人へ向けて丸まる (0.8 秒震えてから転がる)
    public void curlToward(LivingEntity target) {
        Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
        rollDir = d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : d.normalize();
        faceDir(rollDir);
        getNavigation().stop();
        setMode(Mode.CURL);
        playSound(EmberMobSetup.ROLLER_CURL.get(), 1f, 1f);
    }

    // 決めた向きへ転がり出す (予告の 0.8 秒が終わったとき。テストからも呼ぶ)
    public void startRoll(Vec3 dir) {
        rollDir = new Vec3(dir.x, 0, dir.z).normalize();
        faceDir(rollDir);
        hitSomeone = false;
        setMode(Mode.ROLL);
        playSound(EmberMobSetup.ROLLER_ROLL.get(), 1f, 1f);
    }

    private void faceDir(Vec3 d) {
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180 / Math.PI)) - 90f;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    // 壁にぶつかった: 2 秒目を回す
    public void bonk() {
        setDeltaMovement(rollDir.scale(-0.25).add(0, 0.25, 0));
        setMode(Mode.DIZZY);
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), EmberMobSetup.ROLLER_BONK.get(), SoundSource.HOSTILE, 1.2f, 1f);
            server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.4, getZ(), 4, 0.2, 0.2, 0.2, 0);
        }
    }

    public int modeTicks() {
        return modeTicks;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (mode() == Mode.DIZZY && tickCount % 4 == 0) {
                // 目が回る: 頭の上の星の代わりに、腹から火の粉
                level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.4), getY() + 0.6, getRandomZ(0.4), 0, 0.04, 0);
            }
            return;
        }
        modeTicks++;
        if (cooldown > 0) {
            cooldown--;
        }
        switch (mode()) {
            case CURL -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (modeTicks >= CURL_TICKS) {
                    startRoll(rollDir);
                }
            }
            case ROLL -> rollTick();
            case DIZZY -> {
                if (modeTicks >= DIZZY_TICKS) {
                    setMode(Mode.WALK);
                    cooldown = ROLL_COOLDOWN;
                }
            }
            default -> {
            }
        }
        trailTick();
    }

    private void rollTick() {
        Vec3 v = rollDir.scale(ROLL_SPEED);
        setDeltaMovement(v.x, getDeltaMovement().y, v.z);
        // 押し出し: 進む先の人に当たる
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.3), q -> EmberMoth.targetable(q) && GroupOwned.mayTarget(this, q))) {
            if (!hitSomeone) {
                hitSomeone = true;
                if (p.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
                    p.knockback(KNOCKBACK, -rollDir.x, -rollDir.z);
                    p.hurtMarked = true;
                }
                setMode(Mode.WALK);
                cooldown = ROLL_COOLDOWN;
                return;
            }
        }
        if (modeTicks % 2 == 0 && onGround()) {
            trail.addLast(new Trail(position(), level().getGameTime() + TRAIL_TICKS));
        }
        if (modeTicks >= ROLL_TICKS) {
            setMode(Mode.WALK);
            cooldown = ROLL_COOLDOWN;
        }
    }

    // 壁や柱にぶつかった (動いたあとに横の当たりがある) → 目を回す
    @Override
    public void move(MoverType type, Vec3 movement) {
        super.move(type, movement);
        if (!level().isClientSide && mode() == Mode.ROLL && modeTicks > 1 && horizontalCollision) {
            bonk();
        }
    }

    // 火の粉の帯: 3 秒残り、踏んだ人に熱 +5 (一人 1 秒に一度)
    private void trailTick() {
        long now = level().getGameTime();
        while (!trail.isEmpty() && trail.peekFirst().until() < now) {
            trail.removeFirst();
        }
        trailHit.replaceAll((k, v) -> v - 1);
        trailHit.values().removeIf(v -> v <= 0);
        if (trail.isEmpty() || !(level() instanceof ServerLevel server)) {
            return;
        }
        int i = 0;
        for (Trail t : trail) {
            if ((i++ + tickCount) % 3 == 0) {
                server.sendParticles(ParticleTypes.SMALL_FLAME, t.pos().x, t.pos().y + 0.05, t.pos().z, 1, 0.2, 0.0, 0.2, 0.005);
            }
        }
        for (Player p : Players.snapshot(server.players())) {
            if (!EmberMoth.targetable(p) || !GroupOwned.mayTarget(this, p) || trailHit.containsKey(p.getUUID()) || p.distanceToSqr(this) > 40 * 40) {
                continue;
            }
            if (onTrail(p.position())) {
                trailHit.put(p.getUUID(), 20);
                EmberHeat.add(p, TRAIL_HEAT, EmberHeat.Reason.ROLLER_TRAIL);
            }
        }
    }

    // その位置 (足もと) が火の粉の帯の上か
    public boolean onTrail(Vec3 feet) {
        for (Trail t : trail) {
            double dx = t.pos().x - feet.x;
            double dz = t.pos().z - feet.z;
            if (dx * dx + dz * dz <= TRAIL_RADIUS * TRAIL_RADIUS && Math.abs(t.pos().y - feet.y) < 1.2) {
                return true;
            }
        }
        return false;
    }

    public int trailLength() {
        return trail.size();
    }

    // ---------------------------------------------------------------- 丸まる時を決める

    private static final class RollGoal extends Goal {
        private final CinderRoller mob;
        private int repath;

        RollGoal(CinderRoller mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return (t != null && t.isAlive()) || mob.mode() != Mode.WALK;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (mob.mode() != Mode.WALK) {
                mob.getNavigation().stop();
                return;
            }
            LivingEntity t = mob.getTarget();
            if (t == null) {
                return;
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = mob.distanceTo(t);
            if (mob.cooldown == 0 && d >= CURL_MIN && d <= CURL_MAX && Math.abs(t.getY() - mob.getY()) < 2.0 && mob.hasLineOfSight(t)) {
                mob.curlToward(t);
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                if (d < CURL_MIN) {
                    // 近すぎると転がれない: 少し離れる
                    Vec3 away = mob.position().subtract(t.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1e-4) {
                        away = new Vec3(1, 0, 0);
                    }
                    Vec3 to = mob.position().add(away.normalize().scale(4));
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
                } else {
                    mob.getNavigation().moveTo(t, 1.0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 音

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return EmberMobSetup.ROLLER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return EmberMobSetup.ROLLER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.SILVERFISH_STEP, 0.3f, 0.6f);
    }
}
