package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

// 影の綴じ手 (新月の谷の暗がり。docs/design/46 の 4.3・選んだ案 C 裂けた帯の影)。
//   見えない   暗がりでは体が見えず、空ろな輪の頭と星の目だけが浮かぶ。傷はほとんど通らない (×0.15)
//   引き寄せる 暗がりから長い腕を伸ばし (予告 0.5 秒)、9 の内の獲物を自分の方へ引く。星灯りの中に立つ獲物には届かない
//   引き出す   星灯り (星の印・灯りの台) か明るい所 (ブロックの光 10 以上) に入ると、姿を見せて 2 秒竦む。光の中では傷が 1.25 倍。光から逃げようとする
public class ShadowStitcher extends MoonMob {
    public static final String ID = "shadow_stitcher";
    public static final double HP = 40;
    public static final double DMG = 6;
    private static final EntityDataAccessor<Boolean> EXPOSED = SynchedEntityData.defineId(ShadowStitcher.class, EntityDataSerializers.BOOLEAN);
    static final int SEE_LIGHT = 10;
    static final double REACH = 9.0;
    static final int REACH_TELL = 10;
    static final int REACH_COOLDOWN = 120;
    static final int FLINCH = 40;
    private int flinch;

    public ShadowStitcher(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "reach", "attack", "exposed", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EXPOSED, false);
    }

    // 姿が見えているか (描く側が読む)
    public boolean exposed() {
        return entityData.get(EXPOSED);
    }

    // 光の中か (星灯り・明るい所)
    public boolean lit() {
        BlockPos at = blockPosition().above();
        return StarLamps.inLight(level(), position().add(0, 1, 0)) || level().getBrightness(LightLayer.BLOCK, at) >= SEE_LIGHT;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Flinch());
        goalSelector.addGoal(1, new Reach());
        goalSelector.addGoal(2, new Tell(this, 1.0, 1.8, 8, 26, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (flinch > 0) {
            flinch--;
        }
        if (tickCount % 4 == 0) {
            boolean lit = lit();
            if (lit != exposed()) {
                entityData.set(EXPOSED, lit);
                if (lit) {
                    flinch = FLINCH;
                    playAction("exposed");
                    playSound(MoonMobsSetup.STITCHER_EXPOSED.get(), 1.0f, 1.0f);
                }
            }
        }
        if (!exposed() && tickCount % 80 == 0 && random.nextInt(3) == 0) {
            playSound(MoonMobsSetup.STITCHER_WHISPER.get(), 0.6f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return super.hurt(source, exposed() ? amount * 1.25f : amount * 0.15f);
    }

    // 光に入ったら竦み、そのあと光の外へ逃げる
    final class Flinch extends Goal {
        Flinch() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return exposed() && (flinch > 0 || StarLamps.nearest(level(), position(), 8) != null);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (flinch > 0) {
                getNavigation().stop();
                return;
            }
            Vec3 c = StarLamps.nearest(level(), position(), 8);
            if (c != null && tickCount % 10 == 0) {
                Vec3 away = position().subtract(c).multiply(1, 0, 1).normalize().scale(8).add(position());
                getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            }
        }
    }

    // 暗がりから腕を伸ばして引き寄せる
    final class Reach extends Goal {
        private int wind = -1;
        private int cooldown = 40;

        Reach() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !exposed() && distanceTo(t) > 3.0 && distanceTo(t) <= REACH && hasLineOfSight(t)
                    && !StarLamps.inLight(level(), t.position().add(0, 0.5, 0));
        }

        @Override
        public boolean canContinueToUse() {
            return wind >= 0 && !exposed();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            wind = REACH_TELL;
            getNavigation().stop();
            playAction("reach");
            playSound(MoonMobsSetup.STITCHER_REACH.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                wind = -1;
                return;
            }
            getLookControl().setLookAt(t, 30f, 30f);
            // 予告: 腕の先から獲物へ影の糸
            if (level() instanceof ServerLevel sl && wind % 2 == 0) {
                Vec3 a = position().add(0, 1.6, 0);
                Vec3 b = t.position().add(0, 1.0, 0);
                for (int k = 1; k < 8; k++) {
                    Vec3 p = a.lerp(b, k / 8.0 * (1 - wind / (double) REACH_TELL));
                    sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            if (--wind < 0) {
                if (distanceTo(t) <= REACH + 1 && hasLineOfSight(t) && !StarLamps.inLight(level(), t.position().add(0, 0.5, 0))) {
                    Vec3 pull = position().subtract(t.position()).normalize().scale(1.15).add(0, 0.35, 0);
                    t.setDeltaMovement(pull);
                    t.hurtMarked = true;
                    strike(t, dmg(0.6));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                }
                cooldown = REACH_COOLDOWN;
            }
        }

        @Override
        public void stop() {
            wind = -1;
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.SHADOW_THREAD.get(), 0.45, 1, 2);
    }
}
