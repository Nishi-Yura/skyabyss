package jp.revati.skyabyss.titan.tide.heart;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 真珠の脈 (潮殻の心臓の段階2 の技) の真珠。狙った人をゆっくり追い (人の歩きより遅い)、触れると傷 8。
// 叩けば消える。8 秒で消える。壁と島はすり抜ける (部屋の中だけで使う)。保存しない
public class TidePearl extends Entity implements ItemSupplier, jp.revati.skyabyss.titan.group.GroupOwned {
    public static final double SPEED = 0.18;
    public static final int LIFE = 160;

    @Nullable
    private UUID targetId;
    @Nullable
    private UUID heartId;

    public TidePearl(EntityType<? extends TidePearl> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    // 心臓のグループ (ほかのグループの人には送らない)
    @Nullable
    private UUID groupId;

    @Override
    @Nullable
    public UUID groupId() {
        return groupId;
    }

    @Override
    public void setGroupId(@Nullable UUID group) {
        this.groupId = group;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return super.broadcastToPlayer(player) && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(this, player);
    }

    public TidePearl(ServerLevel level, TideHeart heart, ServerPlayer target, Vec3 at) {
        this(TideHeartSetup.TIDE_PEARL.get(), level);
        this.heartId = heart.getUUID();
        this.groupId = heart.groupId();
        this.targetId = target.getUUID();
        moveTo(at.x, at.y, at.z, 0f, 0f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.SNOWBALL);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            if (random.nextFloat() < 0.5f) {
                level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 0, 0, 0);
            }
            return;
        }
        ServerPlayer target = targetId == null ? null : server.getServer().getPlayerList().getPlayer(targetId);
        if (tickCount > LIFE || target == null || !target.isAlive() || target.level() != level()) {
            pop(server);
            return;
        }
        Vec3 to = target.position().add(0, 1.0, 0).subtract(position());
        double d = to.length();
        Vec3 step = d < 1e-3 ? Vec3.ZERO : to.scale(Math.min(SPEED, d) / d);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        setDeltaMovement(step);
        if (getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
            Entity heart = heartId == null ? null : server.getEntity(heartId);
            DamageSource source = heart instanceof TideHeart h ? h.attackSource() : damageSources().magic();
            target.hurt(source, TideHeart.PEARL_DAMAGE);
            pop(server);
        }
    }

    private void pop(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2f, 1.6f);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 12, 0.3, 0.3, 0.3, 0.05);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !(level() instanceof ServerLevel server)) {
            return false;
        }
        pop(server);
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
