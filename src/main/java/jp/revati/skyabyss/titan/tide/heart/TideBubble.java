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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

// 泡の牢 (潮殻の心臓の段階2 の技)。当たった人を包み、1.5 ブロック浮かせる。最長 4 秒、毎秒 2 の傷。
// 泡の体力は 12。中からも外からも叩いて割れる (中にいる人の照準は泡の判定に当たる)。保存しない
public class TideBubble extends Entity implements jp.revati.skyabyss.titan.group.GroupOwned {
    public static final float HEALTH = 12f;
    public static final int MAX_AGE = 80;
    private static final double LIFT = 1.5;
    private static final int RISE_TICKS = 15;

    @Nullable
    private UUID trapped;
    @Nullable
    private UUID heartId;
    private float health = HEALTH;
    private double baseY;

    public TideBubble(EntityType<? extends TideBubble> type, Level level) {
        super(type, level);
        this.noPhysics = true;
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

    public TideBubble(ServerLevel level, ServerPlayer player, TideHeart heart) {
        this(TideHeartSetup.TIDE_BUBBLE.get(), level);
        this.trapped = player.getUUID();
        this.heartId = heart.getUUID();
        this.groupId = heart.groupId();
        this.baseY = player.getY();
        moveTo(player.getX(), player.getY() - 0.2, player.getZ(), 0f, 0f);
        player.fallDistance = 0;
    }

    // その人を包んでいる泡があるか
    public static boolean isTrapped(ServerLevel level, Player player) {
        return !level.getEntitiesOfClass(TideBubble.class, player.getBoundingBox().inflate(2), b -> player.getUUID().equals(b.trapped)).isEmpty();
    }

    @Nullable
    public UUID trappedPlayer() {
        return trapped;
    }

    public float health() {
        return health;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            if (random.nextFloat() < 0.6f) {
                level().addParticle(ParticleTypes.BUBBLE_POP, getX() + (random.nextDouble() - 0.5) * 1.4, getY() + random.nextDouble() * 2.4,
                        getZ() + (random.nextDouble() - 0.5) * 1.4, 0, 0.02, 0);
            }
            return;
        }
        ServerPlayer player = trapped == null ? null : server.getServer().getPlayerList().getPlayer(trapped);
        if (player == null || !player.isAlive() || player.level() != level() || player.isSpectator() || tickCount > MAX_AGE) {
            pop(server, player);
            return;
        }
        // 浮かせる (はじめの 0.75 秒で 1.5 ブロック)。人を泡の中に留める
        double t = Math.min(1.0, tickCount / (double) RISE_TICKS);
        double y = baseY + LIFT * t;
        setPos(getX(), y - 0.2, getZ());
        if (player.distanceToSqr(getX(), y, getZ()) > 0.0025) {
            player.teleportTo(getX(), y, getZ());
        }
        // 宙に浮いている間、専用サーバーの「飛びすぎ」で蹴られないように、短い浮遊の効果を付けておく (泡が割れたら外す)
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 0, false, false, false));
        player.fallDistance = 0;
        player.setDeltaMovement(0, 0, 0);
        if (tickCount % 20 == 10) {
            Entity heart = heartId == null ? null : server.getEntity(heartId);
            DamageSource source = heart instanceof TideHeart h ? h.attackSource() : damageSources().drown();
            player.hurt(source, TideHeart.BUBBLE_DAMAGE_PER_SECOND);
        }
        if (tickCount % 4 == 0) {
            server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.2, getZ(), 3, 0.6, 0.9, 0.6, 0.02);
        }
    }

    private void pop(ServerLevel server, @Nullable ServerPlayer player) {
        server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.HOSTILE, 2f, 0.8f);
        server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1.2, getZ(), 30, 0.6, 0.8, 0.6, 0.2);
        server.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1.2, getZ(), 20, 0.6, 0.8, 0.6, 0.1);
        if (player != null) {
            player.fallDistance = 0;
            player.removeEffect(MobEffects.LEVITATION);
        }
        discard();
    }

    // 叩かれた (中からでも外からでも)。泡の体力が尽きると割れる
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !(level() instanceof ServerLevel server)) {
            return false;
        }
        health -= Math.max(1f, amount);
        server.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.HOSTILE, 1.2f, 1.6f);
        if (health <= 0) {
            ServerPlayer player = trapped == null ? null : server.getServer().getPlayerList().getPlayer(trapped);
            pop(server, player);
        }
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
    public boolean isPushable() {
        return false;
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
