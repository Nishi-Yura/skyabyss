package jp.revati.skyabyss.story.ch6.mob;

import java.util.EnumSet;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.block.Blocks;

// 砂這い (新月の谷。docs/design/46 の 4.3・選んだ案 A 甲殻の掘り手)。
//   潜る   月の砂に潜って甲羅だけ出して進む (砂が盛り上がるのが予告。潜っている間は傷が半分)。獲物の足もとまで来ると
//   跳び出す 予告 (砂の噴き上がり 0.5 秒) のあと前へ跳び、噛む。そのあと 4 秒は地上で鋏で戦い、離れるとまた潜る
public class DustCrawler extends MoonMob {
    public static final String ID = "dust_crawler";
    public static final double HP = 30;
    public static final double DMG = 6;
    private static final EntityDataAccessor<Boolean> BURIED = SynchedEntityData.defineId(DustCrawler.class, EntityDataSerializers.BOOLEAN);
    static final int TELL = 10;
    static final int SURFACE_TICKS = 80;

    public DustCrawler(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "burrow", "leap", "attack", "hurt", "death");
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 20)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURIED, true);
    }

    public boolean buried() {
        return entityData.get(BURIED);
    }

    void setBuried(boolean b) {
        if (b != buried()) {
            entityData.set(BURIED, b);
            playAction(b ? "burrow" : "leap");
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Hunt());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // 潜って進むと砂が盛り上がる (見る人への予告)
        if (!level().isClientSide && buried() && getDeltaMovement().horizontalDistanceSqr() > 0.001 && tickCount % 3 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()), getX(), getY() + 0.1, getZ(),
                    4, 0.35, 0.05, 0.35, 0.02);
            if (tickCount % 12 == 0) {
                playSound(MoonMobsSetup.CRAWLER_DIG.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, buried() ? amount * 0.5f : amount);
    }

    // 潜る → 足もとで予告 → 跳び出して噛む → 地上で戦う → 離れたら潜る
    final class Hunt extends Goal {
        private int wind = -1;
        private int surfaced;
        private int cooldown;
        private int repath;

        Hunt() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            getLookControl().setLookAt(t, 30f, 30f);
            if (cooldown > 0) {
                cooldown--;
            }
            double d = distanceTo(t);
            if (wind >= 0) {
                getNavigation().stop();
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState()), getX(), getY() + 0.3, getZ(),
                            6, 0.3, 0.3, 0.3, 0.1);
                }
                if (--wind < 0) {
                    setBuried(false);
                    playSound(MoonMobsSetup.CRAWLER_BURST.get(), 1.0f, 1.0f);
                    setDeltaMovement(t.position().subtract(position()).normalize().scale(0.55).add(0, 0.45, 0));
                    if (d <= 2.6) {
                        strike(t, dmg(1.2));
                        playSound(MoonMobsSetup.CRAWLER_BITE.get(), 1.0f, 1.0f);
                    }
                    surfaced = SURFACE_TICKS;
                    cooldown = 20;
                }
                return;
            }
            if (buried()) {
                if (d <= 2.2 && cooldown == 0) {
                    wind = TELL;
                    playSound(MoonMobsSetup.CRAWLER_DIG.get(), 1.0f, 0.6f);
                    return;
                }
                if (--repath <= 0) {
                    repath = 6;
                    getNavigation().moveTo(t, 1.25);
                }
                return;
            }
            // 地上: 鋏で噛む (予告つき)。時間が来て離れていれば潜る
            if (surfaced > 0) {
                surfaced--;
            }
            if (surfaced == 0 && d > 4.0) {
                setBuried(true);
                cooldown = 30;
                return;
            }
            if (d <= 1.8 && cooldown == 0) {
                getNavigation().stop();
                playAction("attack");
                wind = -1;
                cooldown = 26;
                jp.revati.skyabyss.story.ch1.Tasks.later(6, () -> {
                    if (isAlive() && getTarget() == t && distanceTo(t) <= 2.4) {
                        strike(t, dmg());
                        playSound(MoonMobsSetup.CRAWLER_BITE.get(), 0.9f, 1.1f);
                    }
                });
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(t, 1.0);
            }
        }

        @Override
        public void stop() {
            wind = -1;
            getNavigation().stop();
            if (!buried()) {
                setBuried(true);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.4, 1, 2);
    }
}
