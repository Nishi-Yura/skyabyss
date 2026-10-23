package jp.revati.skyabyss.titan.ember.heart;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;

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

// 灰の礫 (余燼の心臓の段階 2 の技)。心臓から浮かび、予告の円 (Telegraph。半径 1.6、傷 10) の上へ弧を描いて落ちる。
// 傷は円の中の人だけ (礫は見た目)。落ちる前に叩けば、礫も円も消える。壁と床はすり抜ける (部屋の中だけで使う)。保存しない
public class EmberAshPellet extends Entity implements ItemSupplier, jp.revati.skyabyss.titan.group.GroupOwned {
    public static final double SPEED = 0.18;
    public static final int LIFE = 160;

    @Nullable
    private UUID heartId;
    private Vec3 from = Vec3.ZERO;
    private Vec3 to = Vec3.ZERO;
    private int fall = 30;
    @Nullable
    private Telegraph telegraph;

    public EmberAshPellet(EntityType<? extends EmberAshPellet> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    // from から、fall tick かけて to (床の上の落ちる点) へ落ちる
    public EmberAshPellet(ServerLevel level, EmberHeart heart, Vec3 from, Vec3 to, int fall) {
        this(EmberHeartSetup.ASH_PELLET.get(), level);
        this.heartId = heart.getUUID();
        this.groupId = heart.groupId();
        this.from = from;
        this.to = to.add(0, 0.6, 0);
        this.fall = Math.max(1, fall);
        moveTo(from.x, from.y, from.z, 0f, 0f);
    }

    // この礫が落ちる予告 (叩いて消すと、この予告も消える)
    public void bind(Telegraph telegraph) {
        this.telegraph = telegraph;
    }

    // 予告の最後に落ちた (傷は予告が出す)。見た目だけ出して消える
    public void land() {
        if (level() instanceof ServerLevel server) {
            pop(server);
        }
    }

    // 心臓と同じグループのもの (ほかのグループの人には見えない。docs/design/10 の 9 章)
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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.CHARCOAL);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            if (random.nextFloat() < 0.5f) {
                level().addParticle(ParticleTypes.ASH, getX(), getY() + 0.3, getZ(), 0, 0.02, 0);
            }
            if (random.nextFloat() < 0.25f) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.3, getZ(), 0, 0.01, 0);
            }
            // 尾を引く煙と熾 (動いた向きの後ろへ)
            if (tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SMOKE, xo, yo + 0.3, zo, 0, 0.02, 0);
                level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.55f, 0.18f), 0.9f), xo, yo + 0.3, zo, 0, 0, 0);
            }
            return;
        }
        Entity heart = heartId == null ? null : server.getEntity(heartId);
        if (tickCount > LIFE || !(heart instanceof EmberHeart h) || !h.isAlive()) {
            pop(server);
            return;
        }
        double t = Math.min(1.0, (double) tickCount / fall);
        // 浮かんでから、落ちる点へ。前半は少し上がり、あとは速く落ちる
        Vec3 at = from.lerp(to, t * t * (3 - 2 * t)).add(0, Math.sin(t * Math.PI) * 1.6, 0);
        setPos(at.x, at.y, at.z);
        if (tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.ASH, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void pop(ServerLevel server) {
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.9f, 1.5f);
        server.sendParticles(ParticleTypes.ASH, getX(), getY() + 0.3, getZ(), 14, 0.3, 0.3, 0.3, 0.05);
        server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.3, getZ(), 6, 0.2, 0.2, 0.2, 0.03);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !(level() instanceof ServerLevel server)) {
            return false;
        }
        if (telegraph != null) {
            telegraph.cancel();
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
