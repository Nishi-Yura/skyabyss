package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 押し石 (上弦の崖。docs/design/46 の 4.4・選んだ案 A 目のある輪石)。宙に浮く輪の岩 (地面の 1.5 上)。歩かずに漂って寄ってくる。
//   押しの波 近くに人がいると輪が速く回り、足もとに光の輪が 1 秒で広がる (予告) → 輪の内の人を外へ強く押し出す (崖の外へ落とされる)。
//            伏せて (しゃがんで) いる人は押しが半分。押された人は落ちても崖の決まりで傷 4 まで (CliffTicker)
public class PushStone extends MoonMob {
    public static final String ID = "push_stone";
    public static final double HP = 46;
    public static final double DMG = 5;
    static final double HOVER = 1.5;
    static final double RANGE = 6.0;
    static final int CHARGE = 20;

    public PushStone(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "charge", "push", "hurt", "death");
        this.xpReward = 12;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Wave());
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
        if (level().isClientSide) {
            return;
        }
        // 漂う: 足もとの地面から HOVER の高さを保ち、獲物の 3 手前へ寄る
        double ground = groundBelow();
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.8);
        double wantY = ground + HOVER + Math.sin(tickCount * 0.08) * 0.15;
        v = new Vec3(v.x, (wantY - getY()) * 0.15, v.z);
        if (tg != null && tg.isAlive()) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            if (d > 3.0) {
                Vec3 dir = new Vec3(to.x, 0, to.z).normalize().scale(0.045);
                v = v.add(dir);
            }
            getLookControl().setLookAt(tg, 20f, 20f);
        }
        setDeltaMovement(v);
        hasImpulse = true;
    }

    private double groundBelow() {
        BlockPos.MutableBlockPos m = blockPosition().mutable();
        for (int i = 0; i < 12; i++) {
            if (!level().getBlockState(m).isAir()) {
                return m.getY() + 1;
            }
            m.move(0, -1, 0);
        }
        return getY() - HOVER;
    }

    // 押しの波 (6 の内に人がいれば。4 秒に一度)
    final class Wave extends Goal {
        private int t = -1;
        private int cooldown = 40;

        Wave() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return tg != null && tg.isAlive() && distanceTo(tg) <= RANGE - 0.5;
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= CHARGE + 6 && isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            playAction("charge");
            playSound(MoonMobsSetup.STONE_CHARGE.get(), 1.0f, 0.9f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            double ground = groundBelow();
            if (t <= CHARGE && t % 2 == 0) {
                double r = RANGE * t / (double) CHARGE;
                int n = 8 + (int) (r * 5);
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, ground + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (t == CHARGE) {
                playAction("push");
                playSound(MoonMobsSetup.STONE_PUSH.get(), 1.4f, 0.8f);
                sl.sendParticles(ParticleTypes.CLOUD, getX(), ground + 0.3, getZ(), 24, RANGE * 0.4, 0.1, RANGE * 0.4, 0.05);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(position(), position()).inflate(RANGE + 0.5, 3, RANGE + 0.5), e -> e.isAlive())) {
                    Vec3 away = p.position().subtract(position());
                    double d = away.horizontalDistance();
                    if (d > RANGE + 0.3) {
                        continue;
                    }
                    Vec3 dir = d < 0.01 ? new Vec3(1, 0, 0) : new Vec3(away.x / d, 0, away.z / d);
                    double force = p.isShiftKeyDown() ? 0.9 : 1.8;
                    p.push(dir.x * force, 0.35, dir.z * force);
                    p.hurtMarked = true;
                    strike(p, dmg(0.6));
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 80;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.45, 1, 2);
    }
}
