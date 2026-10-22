package jp.revati.skyabyss.abyss.mobs;

import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.story.ch4.Ch4Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import jp.revati.skyabyss.story.ch5.AbyssBuild;

// 灯喰い (深淵だけ・docs/design/36 の 4・4.1 A 黒い口の球)。灯りへ寄って消す。光の術の灯りも食う。
//   灯りの中にいるあいだは大人しく、灯りを探して寄る (人の置いた松明・灯籠・焚き火・蝋燭・光の術の灯り)。食うのは 1 秒の予告のあと。
//   灯りの外 (明るさ 3 未満) では灯り喰いは人を追う (噛みつき・予告つき)。
//   消す灯りは、休み場 (rest_*) と着く所 (arrival) の 10 ブロック内では消さない。
public class LampEater extends AbyssMobB {
    public static final String ID = "lamp_eater";
    public static final double HP = 30;
    public static final double DMG = 5;
    public static final double DARK_DMG_MUL = 1.4;
    public static final int SNUFF_TELL = 20;
    public static final int SEEK_EVERY = 30;
    public static final int SEE_DARK = 3;
    public static final double SEEK_RANGE = 16;
    private static final int PROTECT_RADIUS = 10;
    private int seekCd;
    private int snuffWind = -1;
    private BlockPos lightBlock;
    private Vec3 lampAt;

    public LampEater(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "snuff", "hurt", "death");
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 20);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Tell(this, 1.25, 1.5, 8, 24, "snuff", DARK_DMG_MUL));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean inDark() {
        return AbyssLight.lightAt(level(), blockPosition()) < SEE_DARK;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        boolean dark = inDark();
        if (!dark && getTarget() != null) {
            setTarget(null);
        }
        if (snuffWind >= 0) {
            getNavigation().stop();
            if (--snuffWind < 0) {
                snuff(sl);
            }
            return;
        }
        if (dark) {
            // 灯りの外では、人を追う (Tell の噛みつきは目標があるときだけ)
            return;
        }
        if (--seekCd <= 0) {
            seekCd = SEEK_EVERY;
            lightBlock = findLightBlock(sl);
            lampAt = AbyssLight.nearestLamp(level(), position(), SEEK_RANGE);
        }
        Vec3 goal = null;
        if (lampAt != null && (lightBlock == null || lampAt.distanceTo(position()) <= lightBlock.getCenter().distanceTo(position()))) {
            goal = lampAt;
        } else if (lightBlock != null) {
            goal = lightBlock.getCenter();
        }
        if (goal == null) {
            return;
        }
        if (position().distanceTo(goal) <= 1.6) {
            getNavigation().stop();
            playAction("snuff");
            playSound(AbyssMobsBSetup.LAMP_EATER_HISS.get(), 0.8f, 1.0f);
            snuffWind = SNUFF_TELL;
        } else if (tickCount % 10 == 0) {
            getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
        }
    }

    // 灯りを消す (予告のあと)。点の灯りは消え、ブロックの灯りは消す (松明は壊れ、蝋燭と焚き火は火を落とす)
    private void snuff(ServerLevel sl) {
        if (lampAt != null && lampAt.distanceTo(position()) <= 2.5 && AbyssLight.snuffNear(sl, lampAt, 2.0) > 0) {
            sl.sendParticles(ParticleTypes.SMOKE, lampAt.x, lampAt.y, lampAt.z, 12, 0.2, 0.2, 0.2, 0.02);
        } else if (lightBlock != null && !nearProtected(lightBlock)) {
            BlockState s = sl.getBlockState(lightBlock);
            if (s.hasProperty(BlockStateProperties.LIT)) {
                sl.setBlock(lightBlock, s.setValue(BlockStateProperties.LIT, false), 3);
            } else {
                sl.setBlock(lightBlock, Blocks.AIR.defaultBlockState(), 3);
            }
            sl.sendParticles(ParticleTypes.SMOKE, lightBlock.getX() + 0.5, lightBlock.getY() + 0.5, lightBlock.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        }
        lightBlock = null;
        lampAt = null;
        seekCd = SEEK_EVERY;
    }

    // 近くの灯り (松明・灯籠・焚き火・蝋燭)。休み場と着く所の近くは数えない
    private BlockPos findLightBlock(ServerLevel sl) {
        BlockPos best = null;
        double bestD = SEEK_RANGE * SEEK_RANGE;
        BlockPos c = blockPosition();
        int r = (int) SEEK_RANGE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    double d = p.distSqr(c);
                    if (d >= bestD || !isLightBlock(sl.getBlockState(p)) || nearProtected(p)) {
                        continue;
                    }
                    best = p;
                    bestD = d;
                }
            }
        }
        return best;
    }

    static boolean isLightBlock(BlockState s) {
        return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)
                || s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)
                || (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) && s.getLightEmission() > 0);
    }

    // 休み場と着く所の近く (消さない・灯りを探さない)
    static boolean nearProtected(BlockPos p) {
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null && arrival.distSqr(p) < PROTECT_RADIUS * PROTECT_RADIUS) {
            return true;
        }
        for (int i = 1; i <= 5; i++) {
            BlockPos rest = AbyssBuild.mark("rest_" + i);
            if (rest != null && rest.distSqr(p) < PROTECT_RADIUS * PROTECT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Ch4Mobs.drop(level, this, source, AbyssMobsBSetup.LAMP_ASH.get(), 0.6, 1, 2);
    }
}
