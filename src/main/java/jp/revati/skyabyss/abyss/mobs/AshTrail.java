package jp.revati.skyabyss.abyss.mobs;

import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 灰の這い跡 (灰の層・見えない。docs/design/36 の 4・4.1 C)。
//   見えない: 光 (ブロックの明るさ 4 以上・光の術の灯り・鼓動の術の輪郭) の中でだけ姿が出る。姿の外では床の灰の跡 (prints の骨) だけ残る。
//   当てられると 3 秒そのまま見える。足跡は灰の粒で床に残す。遅れて飛びつく (予告つき)。
public class AshTrail extends AbyssMobB {
    public static final String ID = "ash_trail";
    public static final double HP = 16;
    public static final double DMG = 4;
    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(AshTrail.class, EntityDataSerializers.BOOLEAN);
    private static final int HIT_SHOW_TICKS = 60;
    static final int SEE_LIGHT = 4;   // 灯りがこれ以上なら見える (スポーンの暗さの上限にも使う)
    private int shownUntil;

    public AshTrail(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "attack", "reveal", "hurt", "death");
        this.xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REVEALED, false);
    }

    // 姿が見えているか (描く側が読む)
    public boolean revealed() {
        return entityData.get(REVEALED);
    }

    // 光の中にいるか (ブロックの明るさ・光の術の灯り・輪郭の効き)
    public boolean lit() {
        return AbyssLight.lightAt(level(), blockPosition()) >= SEE_LIGHT || hasEffect(MobEffects.GLOWING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Tell(this, 1.1, 1.4, 6, 24, "attack", 1.0));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount % 4 == 0) {
            boolean shown = lit() || tickCount < shownUntil;
            if (shown != revealed()) {
                entityData.set(REVEALED, shown);
                if (shown) {
                    playAction("reveal");
                    playSound(jp.revati.skyabyss.abyss.mobs.AbyssMobsBSetup.ASH_TRAIL_REVEAL.get(), 0.7f, 1.0f);
                }
            }
        }
        // 歩いた跡: 灰の粒と歩く音 (見えなくても残る)
        if (getDeltaMovement().horizontalDistanceSqr() > 0.002 && tickCount % 6 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.05, getZ(), 3, 0.2, 0.0, 0.2, 0.0);
            playSound(jp.revati.skyabyss.abyss.mobs.AbyssMobsBSetup.ASH_TRAIL_STEP.get(), 0.35f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    // 殴られた所は 3 秒見える
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide) {
            shownUntil = tickCount + HIT_SHOW_TICKS;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.LAMP_ASH.get(), 0.4, 1, 1);
    }
}
