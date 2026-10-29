package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.story.ch6.field.FieldTide;
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

// 光の群れ (満月の野。docs/design/46 の 4.5・一品一案「欠けた輪の芯の群れ」)。欠けた光の結晶が群れて一つの体。宙に浮く (地面の 1.3 上)。
//   満ち  獲物のまわり 6〜8 を漂い、3 秒ごとに線の予告 (0.8 秒) → その線を素早く突っ込む (触れた人に傷)
//   引き  結晶が散って漂うだけ (突っ込まない)。傷 ×1.5
public class LightSwarm extends MoonMob {
    public static final String ID = "light_swarm";
    public static final double HP = 26;
    public static final double DMG = 7;
    static final double HOVER = 1.3;
    static final int TELL = 16;
    static final int DASH = 12;
    static final double DASH_SPEED = 1.1;
    private boolean scattered;

    public LightSwarm(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "dash", "scatter", "gather", "hurt", "death");
        this.xpReward = 12;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Dash());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean scattered() {
        return scattered;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, scattered ? amount * 1.5f : amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        boolean lit = FieldTide.lit(sl, blockPosition());
        if (lit == scattered) {
            scattered = !lit;
            playAction(scattered ? "scatter" : "gather");
        }
        if (dashing > 0) {
            return;
        }
        // 漂う: 足もとの地面から HOVER の高さ。満ちは獲物のまわり 7 を回り、引きはゆっくり揺れるだけ
        double ground = groundBelow();
        LivingEntity tg = getTarget();
        Vec3 v = getDeltaMovement().scale(0.8);
        double wantY = ground + HOVER + Math.sin(tickCount * 0.1) * 0.3;
        v = new Vec3(v.x, (wantY - getY()) * 0.12, v.z);
        if (tg != null && tg.isAlive() && !scattered) {
            Vec3 to = tg.position().subtract(position());
            double d = to.horizontalDistance();
            Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            double pull = d > 8 ? 0.05 : d < 6 ? -0.04 : 0.0;
            v = v.add(dir.scale(pull)).add(side.scale(0.03));
            getLookControl().setLookAt(tg, 30f, 30f);
        } else if (scattered) {
            v = v.add(Math.sin(tickCount * 0.05 + getId()) * 0.01, 0, Math.cos(tickCount * 0.05 + getId()) * 0.01);
        }
        setDeltaMovement(v);
        hasImpulse = true;
    }

    private double groundBelow() {
        BlockPos.MutableBlockPos m = blockPosition().mutable();
        for (int i = 0; i < 12; i++) {
            if (!level().getBlockState(m).getCollisionShape(level(), m).isEmpty()) {
                return m.getY() + 1;
            }
            m.move(0, -1, 0);
        }
        return getY() - HOVER;
    }

    private int dashing;

    // 突っ込み (満ちのあいだ、獲物が 3〜12 の内。3 秒に一度)
    final class Dash extends Goal {
        private int t = -1;
        private int cooldown = 50;
        private Vec3 from = Vec3.ZERO;
        private Vec3 dir = Vec3.ZERO;
        private final Set<Integer> hit = new HashSet<>();

        Dash() {
            setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return !scattered && tg != null && tg.isAlive() && distanceTo(tg) >= 3 && distanceTo(tg) <= 12 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t <= TELL + DASH && isAlive() && !(scattered && t < TELL);
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
            Vec3 aim = tg == null ? from : tg.position().add(0, 0.8, 0);
            dir = aim.subtract(from).normalize();
            playSound(MoonMobsSetup.SWARM_TELL.get(), 1.0f, 1.3f);
        }

        @Override
        public void tick() {
            t++;
            if (!(level() instanceof ServerLevel sl)) {
                return;
            }
            if (t <= TELL) {
                // 予告: 突っ込む線に光の粒 (長さ 14)
                if (t % 2 == 0) {
                    for (int i = 1; i <= 14; i++) {
                        Vec3 p = from.add(dir.scale(i));
                        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
                    }
                }
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            if (t == TELL + 1) {
                playAction("dash");
                playSound(MoonMobsSetup.SWARM_DASH.get(), 1.2f, 1.2f);
            }
            dashing = 2;
            setDeltaMovement(dir.scale(DASH_SPEED));
            hasImpulse = true;
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.6), e -> e.isAlive() && !e.isSpectator())) {
                if (hit.add(p.getId())) {
                    strike(p, dmg());
                }
            }
        }

        @Override
        public void stop() {
            t = -1;
            cooldown = 60;
            dashing = 0;
            setDeltaMovement(getDeltaMovement().scale(0.2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (dashing > 0) {
            dashing--;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.45, 1, 2);
    }
}
