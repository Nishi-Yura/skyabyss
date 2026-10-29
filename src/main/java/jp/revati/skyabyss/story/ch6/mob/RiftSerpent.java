package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import jp.revati.skyabyss.story.ch6.field.FallingLight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 逆さ砂の蛇 (下弦の淵。docs/design/46 の 4.6・一品一案「砂時計の蛇」)。割れた砂時計からこぼれた月の砂が蛇の形になったもの。動かない (砂時計ごと据わる)。
//   6〜16 の獲物へ砂の礫を 3 つ (飛び物。一瞬止めで止まる)。予告は口の砂の渦 0.6 秒
//   4 の内に寄られると砂に潜って (0.8 秒は傷が通らない) 8〜11 離れた所に出る
//   倒れると 3 秒前の所から残像で一度立ち上がる (RiftMob)
public class RiftSerpent extends RiftMob {
    public static final String ID = "rift_serpent";
    public static final double HP = 36;
    public static final double DMG = 7;
    static final int SINK = 16;
    private int sinking = -1;

    public RiftSerpent(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "spit", "sink", "rise", "hurt", "death");
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public boolean sunk() {
        return sinking >= 0;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Spit());
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (sinking >= 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        LivingEntity tg = getTarget();
        if (sinking < 0 && tg != null && tg.isAlive() && distanceTo(tg) < 4 && tickCount % 10 == 0) {
            sinking = 0;
            playAction("sink");
            playSound(MoonMobsSetup.SERPENT_SINK.get(), 1.0f, 1.0f);
        }
        if (sinking >= 0) {
            sinking++;
            sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()),
                    getX(), getY() + 0.4, getZ(), 4, 0.6, 0.2, 0.6, 0.0);
            if (sinking == SINK) {
                BlockPos to = away(sl, tg);
                if (to != null) {
                    teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                }
                playAction("rise");
            }
            if (sinking >= SINK + 8) {
                sinking = -1;
            }
        }
    }

    // 獲物から 8〜11 離れた、立てる所
    private BlockPos away(ServerLevel sl, LivingEntity tg) {
        Vec3 c = tg == null ? position() : tg.position();
        for (int tries = 0; tries < 16; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 8 + random.nextDouble() * 3;
            int x = (int) Math.floor(c.x + Math.cos(a) * d);
            int z = (int) Math.floor(c.z + Math.sin(a) * d);
            for (int y = (int) c.y + 3; y >= (int) c.y - 4; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (sl.getBlockState(p).isAir() && sl.getBlockState(p.above()).isAir()
                        && sl.getBlockState(p.below()).isFaceSturdy(sl, p.below(), Direction.UP)) {
                    return p;
                }
            }
        }
        return null;
    }

    // 砂の礫 (6〜16 の獲物。4 秒に一度、3 つ)
    final class Spit extends Goal {
        private int t = -1;
        private int cooldown = 40;

        Spit() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            LivingEntity tg = getTarget();
            return sinking < 0 && tg != null && tg.isAlive() && distanceTo(tg) >= 5 && distanceTo(tg) <= 16 && hasLineOfSight(tg);
        }

        @Override
        public boolean canContinueToUse() {
            return t >= 0 && t < 36 && isAlive() && sinking < 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            t = 0;
            playAction("spit");
            playSound(MoonMobsSetup.SERPENT_TELL.get(), 1.0f, 1.0f);
        }

        @Override
        public void tick() {
            t++;
            LivingEntity tg = getTarget();
            if (tg == null || !(level() instanceof ServerLevel sl)) {
                return;
            }
            getLookControl().setLookAt(tg, 30f, 30f);
            Vec3 mouth = position().add(0, 1.8, 0).add(Vec3.directionFromRotation(0, yHeadRot).scale(0.6));
            if (t < 12) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.WHITE_CONCRETE_POWDER.defaultBlockState()),
                        mouth.x, mouth.y, mouth.z, 3, 0.25, 0.25, 0.25, 0.0);
                return;
            }
            if ((t - 12) % 6 == 0 && t <= 24) {
                Vec3 aim = tg.position().add(0, 1.0, 0).add(tg.getDeltaMovement().scale(4));
                FallingLight.shootSand(sl, RiftSerpent.this, mouth, aim.subtract(mouth), 0.55, dmg(0.8));
                playSound(MoonMobsSetup.SERPENT_SPIT.get(), 1.0f, 1.0f + random.nextFloat() * 0.2f);
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
        if (!echo()) {
            jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.5, 1, 2);
        }
    }
}
