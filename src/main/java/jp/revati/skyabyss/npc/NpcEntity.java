package jp.revati.skyabyss.npc;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

// この Mod の村人 (語り部・鯨追い)。バニラの村人とは別の生き物で、取引はせず、話しかけると会話の画面が開く。
//  - 役 (role) はエンティティの ID ("skyabyss:storyteller")。会話もクエストも役で引く
//  - 家 (home) のまわりだけを歩く。話している間は足を止めて相手を見る
//  - プレイヤーや敵の攻撃では傷つかない (クエストの相手が死んで進めなくならないように)。/kill と奈落だけは効く
//  - 空の影が近くを通ると、足を止めて空を見上げる
public class NpcEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> TALKING = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    // 話し相手が何 tick 応答しなければ、話していないとみなすか (画面が閉じたのに知らせが届かなかったとき)
    private static final int TALK_TIMEOUT = 20 * 60;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation idle;
    private final RawAnimation walk;
    private final RawAnimation talk;
    private final int homeRadius;

    @Nullable
    private BlockPos home;
    // 話している人 → 話し始めた tick
    private final Map<UUID, Integer> talkers = new HashMap<>();
    @Nullable
    private UUID lastTalker;
    private int lastStruck = -1000;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level, int homeRadius) {
        super(type, level);
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        this.idle = RawAnimation.begin().thenLoop("animation." + name + ".idle");
        this.walk = RawAnimation.begin().thenLoop("animation." + name + ".walk");
        this.talk = RawAnimation.begin().thenLoop("animation." + name + ".talk");
        this.homeRadius = homeRadius;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes(double speed) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    public String role() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).toString();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TALKING, false);
    }

    public boolean isTalking() {
        return entityData.get(TALKING);
    }

    // ---------------------------------------------------------------- 家
    // 見つめたときに名前が出るよう、役の名前を付ける (翻訳キーなので言語を切り替えても追従する)
    public void nameByRole() {
        setCustomName(Component.translatable(getType().getDescriptionId()));
    }

    public void setHome(BlockPos pos) {
        this.home = pos.immutable();
        restrictTo(home, homeRadius);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    // ---------------------------------------------------------------- 会話
    void startTalking(ServerPlayer player) {
        talkers.put(player.getUUID(), tickCount);
        lastTalker = player.getUUID();
        getNavigation().stop();
        entityData.set(TALKING, true);
    }

    void stopTalking(ServerPlayer player) {
        talkers.remove(player.getUUID());
        if (talkers.isEmpty()) {
            entityData.set(TALKING, false);
        }
    }

    @Nullable
    private Player talker() {
        if (lastTalker != null && talkers.containsKey(lastTalker)) {
            Player p = level().getPlayerByUUID(lastTalker);
            if (p != null) {
                return p;
            }
        }
        for (UUID id : talkers.keySet()) {
            Player p = level().getPlayerByUUID(id);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp && !sp.isSpectator()) {
            DialogueService.open(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 20 == 0 && !talkers.isEmpty()) {
            // 応答のない話し相手を外す (抜けた・画面の知らせが届かなかった)
            Iterator<Map.Entry<UUID, Integer>> it = talkers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> e = it.next();
                Player p = level().getPlayerByUUID(e.getKey());
                if (p == null || p.distanceToSqr(this) > 12 * 12 || tickCount - e.getValue() > TALK_TIMEOUT) {
                    it.remove();
                }
            }
            if (talkers.isEmpty()) {
                entityData.set(TALKING, false);
            }
        }
    }

    // ---------------------------------------------------------------- 傷つかない
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        // 手を上げられたら嫌がる (好感度が少し下がる。5 秒に一度まで)
        if (source.getEntity() instanceof ServerPlayer player && !player.isCreative() && tickCount - lastStruck > 100) {
            lastStruck = tickCount;
            Affinity.add(player, role(), Affinity.STRUCK);
            player.displayClientMessage(Component.translatable("message." + getType().getDescriptionId().replace("entity.", "") + ".struck")
                    .withStyle(s -> s.withColor(0xD9779A).withItalic(true)), true);
            getLookControl().setLookAt(player);
        }
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    // ---------------------------------------------------------------- 動き
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new TalkGoal());
        goalSelector.addGoal(2, new SkyShadowGoal());
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5, 0.004f));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f, 0.04f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // 話している間: 足を止めて、話し相手を見る
    private final class TalkGoal extends Goal {
        TalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !talkers.isEmpty();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player p = talker();
            if (p != null) {
                getLookControl().setLookAt(p.getX(), p.getEyeY(), p.getZ(), 30f, 30f);
            }
        }
    }

    // 空の影が近くを通る間: 足を止め、影の中心の空を見上げる (動物と同じく、怯えて立ちつくす)
    private final class SkyShadowGoal extends Goal {
        private Vec3 gaze = Vec3.ZERO;

        SkyShadowGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return tickCount % 10 == 0 && underShadow();
        }

        @Override
        public boolean canContinueToUse() {
            return tickCount % 20 != 0 || underShadow();
        }

        private boolean underShadow() {
            if (!(level() instanceof ServerLevel server) || server.dimension() != Level.OVERWORLD) {
                return false;
            }
            SkyPass pass = SkyEvents.activePass();
            long now = server.getGameTime();
            if (pass == null || !pass.isActive(now)) {
                return false;
            }
            double age = pass.age(now, 0f);
            double dx = pass.centerX(age) - getX();
            double dz = pass.centerZ(age) - getZ();
            if (dx * dx + dz * dz > SkyPass.REACH * SkyPass.REACH) {
                return false;
            }
            gaze = new Vec3(pass.centerX(age), getEyeY() + 90, pass.centerZ(age));
            return true;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(gaze.x, gaze.y, gaze.z, 10f, 80f);
        }
    }

    // ---------------------------------------------------------------- 保存
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Home").ifPresent(this::setHome);
    }

    // ---------------------------------------------------------------- 動き (GeckoLib)
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isTalking()) {
                return state.setAndContinue(talk);
            }
            return state.setAndContinue(state.isMoving() ? walk : idle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
