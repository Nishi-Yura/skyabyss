package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 淵の羽影 (下弦の淵。docs/design/46 の 4.6・一品一案「下弦の仮面の羽影」)。欠けた月の翼の影の飛ぶもの。地面の 5 上を漂う。
//   獲物の上を回り、3.5 秒ごとに線の予告 (0.7 秒) → その線を急降下して斬る (触れた人に傷) → また上へ
//   倒れると 3 秒前の所から残像で一度立ち上がり、同じように急降下する (RiftMob)
public class RiftWing extends RiftMob {
    public static final String ID = "rift_wing";
    public static final double HP = 28;
    public static final double DMG = 7;
    static final double HOVER = 5.0;
    static final int TELL = 14;
    static final int DIVE = 16;
    static final double DIVE_SPEED = 0.95;
    private int diving;

    public RiftWing(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "dive", "hurt", "death");
        this.xpReward = 12;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Dive());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || diving > 0) {
            return;
        }
        // 漂う: 獲物 (いなければ自分) の足もとの地面から HOVER の高さ、獲物のまわり 5 を回る
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.85);
        double ground = groundBelow(tg != null ? tg.blockPosition() : blockPosition());
        double wantY = ground + HOVER + Math.sin(tickCount * 0.08) * 0.6;
        v = new Vec3(v.x, (wantY - getY()) * 0.08, v.z);
        if (tg != null && tg.isAlive()) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            double pull = d > 6 ? 0.05 : d < 4 ? -0.04 : 0.0;
            v = v.add(dir.scale(pull)).add(side.scale(0.035));
            getLookControl().setLookAt(tg, 30f, 30f);
            setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        }
        setDeltaMovement(v);
        hasImpulse = true;
    }

    private double groundBelow(BlockPos from) {
        BlockPos.MutableBlockPos m = from.mutable();
        for (int i = 0; i < 16; i++) {
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
                return m.getY() + 1;
            }
            m.move(0, -1, 0);
        }
        return getY() - HOVER;
    }

    // 急降下 (獲物が 3〜14 の内。3.5 秒に一度)
    final class Dive extends Goal {
        private int t = -1;
        private int cooldown = 50;
        private Vec3 from = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        private final Set<Integer> hit = new HashSet<>();

        Dive() {
            setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) >= 3 && distanceTo(tg) <= 14 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + DIVE && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            hit.clear();
            LivingEntity tg = getTarget();
            from = position();
            Vec3 aim = tg == null ? from : tg.position().add(0, 0.6, 0);
            dir = aim.subtract(from).normalize();
            playSound(MoonMobsSetup.WING_TELL.get(), 1.0f, 1.2f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            if (t <= TELL) {
                if (t % 2 == 0) {
                    for (int i = 1; i <= 15; i++) {
                        Vec3 p = from.add(dir.scale(i));
                        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.2, p.z, 1, 0, 0, 0, 0);
                    }
                }
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            if (t == TELL + 1) {
                playAction("dive");
                playSound(MoonMobsSetup.WING_DIVE.get(), 1.2f, 1.0f);
            }
            diving = 2;
            setDeltaMovement(dir.scale(DIVE_SPEED));
            hasImpulse = true;
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.7), e -> e.isAlive() && !e.isSpectator())) {
                if (hit.add(p.getId())) {
                    strike(p, dmg());
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 70;
            diving = 0;
            setDeltaMovement(getDeltaMovement().scale(0.2).add(0, 0.3, 0));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (diving > 0) {
            diving--;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!echo()) {
            jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.SHADOW_THREAD.get(), 0.35, 1, 1);
        }
    }
}
