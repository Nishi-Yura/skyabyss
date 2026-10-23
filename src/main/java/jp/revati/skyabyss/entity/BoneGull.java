package jp.revati.skyabyss.entity;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.Ch1BackSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 骨鳥 (docs/design/21 の 3.3、案 A「骨の翼」): 弱い飛ぶ敵。HP 8、急降下 3。鐘楼・胸びれの小物 (今は西の翼の竪穴と崩れ橋に出る)。
//  - 急降下: 1 体ずつ (近くで別の骨鳥が降りているあいだは降りない)。頭を上げて顎を開き、翼を上げて鳴く (16 tick = 0.8 秒)。
//    そのあいだ、鳥から狙う人の向こうへ伸びる線 (幅 1.8) の印が床に出て、続く 8 tick で線に沿って一直線に降りる。
//    傷は降り終えた瞬間に線の中の人へ (印と当たりは同じ線。線から出れば当たらない)。降りたあとは 1.5 秒よろめいて地に落ち、反撃の隙になる
//  - 壁はすり抜けない (ふつうの物理。降りる線の途中が塞がっていれば手前で止まる)
public class BoneGull extends GuardMob {
    public static final int CRY_TICKS = 16;
    public static final int DIVE_TICKS = 8;
    public static final int WARN = CRY_TICKS + DIVE_TICKS;
    public static final double HALF_WIDTH = 0.9;
    public static final double MAX_LINE = 16;
    public static final float DAMAGE = 3f;
    public static final int RECOVER_TICKS = 30;
    public static final double HOVER_RADIUS = 5.5;
    public static final double HOVER_HEIGHT = 3.5;

    private enum Phase { HOVER, CRY, DIVE, RECOVER }

    private Phase phase = Phase.HOVER;
    private int phaseTicks;
    private int cooldown = 50;
    private double orbit;
    private boolean holdAttacks;
    private Vec3 diveEnd = Vec3.ZERO;
    private Vec3 home;

    public BoneGull(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_gull", "cry", "dive", "hurt", "death");
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 4;
        this.orbit = random.nextDouble() * Math.PI * 2;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 28);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(false);
        return nav;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // 試験用
    public void holdAttacks() {
        holdAttacks = true;
    }

    public void forceDive() {
        holdAttacks = false;
        cooldown = 0;
    }

    public boolean isDiving() {
        return phase == Phase.CRY || phase == Phase.DIVE;
    }

    public boolean isRecovering() {
        return phase == Phase.RECOVER;
    }

    private boolean canHit(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && canAttack(player);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (home == null) {
            home = position();
        }
        LivingEntity target = getTarget();
        switch (phase) {
            case HOVER -> tickHover(server, target);
            case CRY -> tickCry(server);
            case DIVE -> tickDive(server);
            case RECOVER -> tickRecover();
        }
    }

    private void tickHover(ServerLevel server, LivingEntity target) {
        cooldown = Math.max(0, cooldown - 1);
        if (target == null || !target.isAlive()) {
            // 持ち場のまわりをゆっくり回る
            orbit += 0.03;
            getMoveControl().setWantedPosition(home.x + Math.cos(orbit) * 3, home.y + Math.sin(orbit * 2) * 0.6, home.z + Math.sin(orbit) * 3, 0.6);
            return;
        }
        orbit += 0.07;
        double h = HOVER_HEIGHT + Math.sin(orbit * 2.3) * 0.5;
        getMoveControl().setWantedPosition(target.getX() + Math.cos(orbit) * HOVER_RADIUS, target.getY() + h, target.getZ() + Math.sin(orbit) * HOVER_RADIUS, 1.0);
        getLookControl().setLookAt(target, 30f, 30f);
        if (holdAttacks || cooldown > 0 || !(target instanceof ServerPlayer player) || !canHit(player)) {
            return;
        }
        double dist = Math.hypot(target.getX() - getX(), target.getZ() - getZ());
        if (dist < 4 || dist > 18 || !hasLineOfSight(target) || otherDiverNear(server)) {
            return;
        }
        beginCry(server, target);
    }

    // 近くのほかの骨鳥が降りている最中か (1 体ずつ降りる)
    private boolean otherDiverNear(ServerLevel server) {
        return !server.getEntitiesOfClass(BoneGull.class, getBoundingBox().inflate(28), g -> g != this && g.isAlive() && g.isDiving()
                && java.util.Objects.equals(g.groupId(), groupId())).isEmpty();
    }

    // 線の長さ: 狙う人の少し向こうまで (最長 MAX_LINE)
    public static double lineLength(double horizontalDistance) {
        return Math.min(MAX_LINE, horizontalDistance + 3.0);
    }

    private void beginCry(ServerLevel server, LivingEntity target) {
        phase = Phase.CRY;
        phaseTicks = 0;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double d = Math.hypot(dx, dz);
        double length = lineLength(d);
        double ux = dx / d;
        double uz = dz / d;
        double floor = target.getY();
        diveEnd = new Vec3(getX() + ux * length, floor + 0.9, getZ() + uz * length);
        float yaw = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90f;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        playAction("cry");
        Telegraph.in(server, TelegraphShape.line(getX(), getZ(), Telegraph.angleOf(ux, uz), length, HALF_WIDTH))
                .floor(floor).warn(WARN).danger().owner(this)
                .target(target)
                .startSound(Ch1BackSounds.GULL_CRY.get(), 1.6f, 1.0f + random.nextFloat() * 0.1f)
                .victims(this::canHit)
                .onImpact(s -> s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                        getX(), getY() + 0.3, getZ(), 12, 0.3, 0.2, 0.3, 0.1))
                .onHit((s, player) -> {
                    player.hurt(attackSource(), DAMAGE);
                    player.setDeltaMovement(player.getDeltaMovement().add(ux * 0.3, 0.2, uz * 0.3));
                    player.hurtMarked = true;
                })
                .start();
    }

    private void tickCry(ServerLevel server) {
        phaseTicks++;
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().scale(0.6));
        if (phaseTicks >= CRY_TICKS) {
            phase = Phase.DIVE;
            phaseTicks = 0;
            playAction("dive");
            server.playSound(null, getX(), getY(), getZ(), Ch1BackSounds.GULL_DIVE.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
        }
    }

    // 線に沿って DIVE_TICKS で降りる。物理に任せて壁にはぶつかる (ぶつかれば手前で止まる)
    private void tickDive(ServerLevel server) {
        phaseTicks++;
        int remaining = DIVE_TICKS - phaseTicks + 1;
        Vec3 delta = diveEnd.subtract(position()).scale(1.0 / Math.max(1, remaining));
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        move(MoverType.SELF, delta);
        server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        if (phaseTicks >= DIVE_TICKS) {
            phase = Phase.RECOVER;
            phaseTicks = 0;
            setNoGravity(false);
        }
    }

    // 降りたあとのよろめき: 地に落ちて動けない (反撃の隙)
    private void tickRecover() {
        phaseTicks++;
        getNavigation().stop();
        if (phaseTicks >= RECOVER_TICKS) {
            phase = Phase.HOVER;
            phaseTicks = 0;
            setNoGravity(true);
            cooldown = 70 + random.nextInt(50);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        Telegraph.cancelAll(this);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Ch1BackSounds.GULL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Ch1BackSounds.GULL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Ch1BackSounds.GULL_DEATH.get();
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    @Override
    public boolean isNoGravity() {
        return super.isNoGravity() && !isDeadOrDying();
    }

    // 被弾・倒れるアニメ (tools/gen/mobs/gen_guard_hurt_death.py)
    @Override
    protected boolean autoHurtDeath() {
        return true;
    }
}
