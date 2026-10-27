package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.story.ch2.Ch2Mobs;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

// 宿借り (二章の地上): 追いかけっこ (敵意なし)。流木の殻を背負った小さな宿借り。攻撃はしない。
//   殻に潜る   人が SCARE の近さに来ると殻に潜って動かない。人が CALM より離れて少したつと出てくる
//   拾い物     殻を人が叩くと、拾い物 (小真珠 / 貝の欠け) を一つ吐く。一匹に一度だけ (spat。保存する) なので、叩き続けても増えない
//   行列       浜のひとり歩きで湧いた一匹 (先頭) が、後ろに 2〜4 匹 (TEMP_TAG。保存しない) を連れて歩く。後ろの者は前の者の後ろを追う。
//              先頭は向きを少しずつ変えながらまっすぐ歩く (浜に沿う行列に見える)。前の者が消えたら自分が先頭になる。
//              連れの者は、人が 128 ブロック以内にいなくなると消える (先頭はひとり歩きの仕組みが消す)
public class DriftHermit extends GuardMob {
    public static final double SCARE = 5.0;
    public static final double CALM = 7.0;
    public static final int UNHIDE_DELAY = 40;
    public static final double GAP = 1.3;
    public static final int MIN_FOLLOWERS = 2;
    public static final int MAX_FOLLOWERS = 4;
    public static final double KEEP_RANGE = 128;

    private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(DriftHermit.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation A_HIDE = RawAnimation.begin().thenPlay("animation.drift_hermit.hide").thenLoop("animation.drift_hermit.hidden");

    @Nullable
    private UUID ahead;
    private boolean formed;
    private boolean spat;
    private int calm;
    private float heading = Float.NaN;

    public DriftHermit(EntityType<? extends Monster> type, Level level) {
        super(type, level, "drift_hermit", "spit", "hide", "hurt", "death");
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HIDDEN, false);
    }

    public boolean isHidden() {
        return entityData.get(HIDDEN);
    }

    public boolean hasSpat() {
        return spat;
    }

    @Nullable
    public UUID ahead() {
        return ahead;
    }

    public void setAhead(@Nullable UUID id) {
        this.ahead = id;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FollowAheadGoal(this));
        goalSelector.addGoal(5, new LeadGoal(this));
    }

    // 潜っている間は、歩きの代わりに潜った姿 (殻だけ)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, "shell", 3, state -> isHidden() ? state.setAndContinue(A_HIDE) : PlayState.STOP));
    }

    // 敵意なし: 誰も狙わない
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (!formed) {
            formed = true;
            if (getTags().contains(jp.revati.skyabyss.guide.SiteSpawner.TAG) && ahead == null) {
                int n = MIN_FOLLOWERS + random.nextInt(MAX_FOLLOWERS - MIN_FOLLOWERS + 1);
                followers(level, this, n);
            }
        }
        // 前の者が消えた: 自分が先頭になる
        if (ahead != null && tickCount % 20 == 0) {
            Entity e = level.getEntity(ahead);
            if (e == null || !e.isAlive()) {
                ahead = null;
            }
        }
        // 連れの者は、人が遠くにいなくなったら消える
        if (getTags().contains(TEMP_TAG) && tickCount % 40 == 13 && !playerWithin(KEEP_RANGE)) {
            discard();
            return;
        }
        // 殻に潜る / 出る
        boolean near = playerWithin(isHidden() ? CALM : SCARE);
        if (near) {
            calm = 0;
            if (!isHidden()) {
                entityData.set(HIDDEN, true);
                getNavigation().stop();
                playSound(MobSounds.DRIFT_HERMIT_HIDE.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
            }
        } else if (isHidden() && ++calm >= UNHIDE_DELAY) {
            entityData.set(HIDDEN, false);
        }
        if (isHidden()) {
            getNavigation().stop();
        }
    }

    private boolean playerWithin(double range) {
        for (ServerPlayer p : Players.snapshot(((ServerLevel) level()).players())) {
            if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(this) <= range * range && GroupOwned.visibleTo(this, p)) {
                return true;
            }
        }
        return false;
    }

    // 殻を叩かれると、一度だけ拾い物を吐く (叩いた人の方へ)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive()) {
            if (!spat && source.getEntity() instanceof Player player) {
                spit(player);
            } else {
                playAction("hurt");
            }
        }
        return hit;
    }

    // 拾い物を吐く (小真珠 40% / 貝の欠け 60%。素材がまだ登録されていなければ、吐いた印だけ)。吐いたら true
    public boolean spit(@Nullable Player toward) {
        if (spat) {
            return false;
        }
        spat = true;
        playAction("spit");
        playSound(MobSounds.DRIFT_HERMIT_SPIT.get(), 0.9f, 1.0f);
        Item item = random.nextFloat() < 0.4f ? Ch2Mobs.item(Ch2Mobs.SMALL_PEARL) : Ch2Mobs.item(Ch2Mobs.SHELL_CHIP);
        if (item == null) {
            item = Ch2Mobs.item(Ch2Mobs.SHELL_CHIP);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.4, getZ(), 6, 0.2, 0.1, 0.2, 0.02);
            if (item != null) {
                ItemEntity drop = new ItemEntity(server, getX(), getY() + 0.4, getZ(), new ItemStack(item));
                Vec3 dir = toward == null ? new Vec3(0, 0, 0) : toward.position().subtract(position()).multiply(1, 0, 1).normalize();
                drop.setDeltaMovement(dir.x * 0.18, 0.25, dir.z * 0.18);
                drop.setDefaultPickUpDelay();
                server.addFreshEntity(drop);
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- 行列
    // leader の後ろに n 匹の連れを並べる (一時の個体。保存しない)。湧いた連れを返す。湧き台・試験からも呼べる
    public static List<DriftHermit> followers(ServerLevel level, DriftHermit leader, int n) {
        List<DriftHermit> out = new ArrayList<>();
        DriftHermit prev = leader;
        double rad = Math.toRadians(leader.getYRot());
        Vec3 back = new Vec3(Math.sin(rad), 0, -Math.cos(rad));
        for (int i = 1; i <= n; i++) {
            DriftHermit h = ModEntities.DRIFT_HERMIT.get().create(level);
            if (h == null) {
                break;
            }
            double x = leader.getX() + back.x * GAP * i;
            double z = leader.getZ() + back.z * GAP * i;
            BlockPos col = BlockPos.containing(x, leader.getY(), z);
            double y = level.hasChunkAt(col) ? Math.max(leader.getY() - 2, Math.min(leader.getY() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()))) : leader.getY();
            h.moveTo(x, y, z, leader.getYRot(), 0f);
            h.addTag(TEMP_TAG);
            h.setGroupId(leader.groupId());
            h.formed = true;
            h.ahead = prev.getUUID();
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(col), MobSpawnType.EVENT, null);
            if (level.addFreshEntity(h)) {
                out.add(h);
                prev = h;
            }
        }
        return out;
    }

    // 前の者の後ろ (GAP) を追う
    static final class FollowAheadGoal extends Goal {
        private final DriftHermit mob;

        FollowAheadGoal(DriftHermit mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private Entity aheadEntity() {
            return mob.ahead == null || !(mob.level() instanceof ServerLevel s) ? null : s.getEntity(mob.ahead);
        }

        @Override
        public boolean canUse() {
            Entity a = aheadEntity();
            return !mob.isHidden() && a != null && a.isAlive() && mob.distanceToSqr(a) > GAP * GAP * 1.4;
        }

        @Override
        public boolean canContinueToUse() {
            Entity a = aheadEntity();
            return !mob.isHidden() && a != null && a.isAlive() && mob.distanceToSqr(a) > GAP * GAP;
        }

        @Override
        public void tick() {
            Entity a = aheadEntity();
            if (a == null) {
                return;
            }
            double d = mob.distanceTo(a);
            mob.getNavigation().moveTo(a.getX(), a.getY(), a.getZ(), d > 4 ? 1.3 : 0.9);
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    // 先頭: 向きを少しずつ変えながら、6〜9 ブロック先へ歩いては少し休む
    static final class LeadGoal extends Goal {
        private final DriftHermit mob;
        private int rest;

        LeadGoal(DriftHermit mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.ahead != null || mob.isHidden()) {
                return false;
            }
            if (rest > 0) {
                rest--;
                return false;
            }
            return true;
        }

        @Override
        public void start() {
            if (Float.isNaN(mob.heading)) {
                mob.heading = mob.getYRot();
            }
            for (int i = 0; i < 6; i++) {
                float h = mob.heading + (mob.random.nextFloat() - 0.5f) * (i < 3 ? 60f : 220f);
                double dist = 6 + mob.random.nextDouble() * 3;
                double rad = Math.toRadians(h);
                double x = mob.getX() - Math.sin(rad) * dist;
                double z = mob.getZ() + Math.cos(rad) * dist;
                BlockPos col = BlockPos.containing(x, mob.getY(), z);
                if (!mob.level().hasChunkAt(col)) {
                    continue;
                }
                int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
                if (!mob.level().getFluidState(new BlockPos(col.getX(), y - 1, col.getZ())).isEmpty() || Math.abs(y - mob.getY()) > 3) {
                    continue;
                }
                if (mob.getNavigation().moveTo(x, y, z, 0.8)) {
                    mob.heading = Mth.wrapDegrees(h);
                    return;
                }
            }
            mob.heading = Mth.wrapDegrees(mob.heading + 180f);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.ahead == null && !mob.isHidden() && !mob.getNavigation().isDone();
        }

        @Override
        public void stop() {
            rest = 30 + mob.random.nextInt(50);
        }
    }

    // 小真珠 30%、貝の欠け 70% (1〜2)
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!recentlyHit) {
            return;
        }
        Item pearl = Ch2Mobs.item(Ch2Mobs.SMALL_PEARL);
        Item chip = Ch2Mobs.item(Ch2Mobs.SHELL_CHIP);
        if (pearl != null && random.nextFloat() < 0.3f) {
            spawnAtLocation(new ItemStack(pearl));
        }
        if (chip != null && random.nextFloat() < 0.7f) {
            spawnAtLocation(new ItemStack(chip, 1 + random.nextInt(2)));
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            entityData.set(HIDDEN, false);
            playAction("death");
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Spat", spat);
        tag.putBoolean("Formed", formed);
        if (ahead != null) {
            tag.putUUID("Ahead", ahead);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spat = tag.getBoolean("Spat");
        formed = tag.getBoolean("Formed");
        ahead = tag.hasUUID("Ahead") ? tag.getUUID("Ahead") : null;
        // 撮影・開発用: {Hidden:1b} で潜った姿で出す
        if (tag.getBoolean("Hidden")) {
            entityData.set(HIDDEN, true);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isHidden() ? null : MobSounds.DRIFT_HERMIT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.DRIFT_HERMIT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.DRIFT_HERMIT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.DRIFT_HERMIT_STEP.get(), 0.2f);
    }
}
