package jp.revati.skyabyss.story.ch6.rift;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 自分の残像 (docs/design/46 の 4.6「残像」)。人の姿で青白く透け、頭の上に乗れる (当たりのある体)。保存しない。
//   なぞる (replay)   下弦の鏡で覚えた動き (一 tick ごとの位置と向き) を、覚えたとおりになぞって消える。残像の板を踏める。
//                     一瞬止めで宙に止まる (自分の残像は 5 秒。MomentStop)。星灯りの中に入ると消える
//   人の残像 (chase)  残月の剣守の段 3。狙った人の 3 秒前の位置をなぞり続け、触れると傷。乗れない。星灯りの中では薄れて当たらない
// 描く側 (client/EchoShadeRenderer) は持ち主の姿 (肌) を借りる
public class EchoShade extends Entity {
    public static final String ID = "echo_shade";
    public static final int LINGER = 24;
    public static final int CHASE_DELAY = 60;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(EchoShade.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> CHASE = SynchedEntityData.defineId(EchoShade.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FADE = SynchedEntityData.defineId(EchoShade.class, EntityDataSerializers.INT);

    public record Frame(Vec3 pos, float yaw, float pitch) {
    }

    private List<Frame> frames = List.of();
    private int index;
    private int linger;
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    @Nullable
    private UUID chaseTarget;
    private float chaseDamage;
    private int hitCooldown;
    // 描く側の歩きの揺れ
    public float walkPos;
    public float walkSpeed;
    public float walkSpeedO;

    public EchoShade(EntityType<? extends EchoShade> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    // なぞる残像: frames を最初の位置から
    public static EchoShade replay(ServerLevel level, ServerPlayer owner, List<Frame> frames) {
        EchoShade e = RiftBlocks.ECHO_SHADE.get().create(level);
        if (e == null || frames.isEmpty()) {
            return null;
        }
        e.frames = new ArrayList<>(frames);
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        Frame f = frames.get(0);
        e.moveTo(f.pos().x, f.pos().y, f.pos().z, f.yaw(), f.pitch());
        level.addFreshEntity(e);
        return e;
    }

    // 人の残像: target の 3 秒前を追う
    public static EchoShade chase(ServerLevel level, ServerPlayer target, float damage) {
        EchoShade e = RiftBlocks.ECHO_SHADE.get().create(level);
        if (e == null) {
            return null;
        }
        e.entityData.set(OWNER, Optional.of(target.getUUID()));
        e.entityData.set(CHASE, true);
        e.chaseTarget = target.getUUID();
        e.chaseDamage = damage;
        e.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0f);
        level.addFreshEntity(e);
        return e;
    }

    @Nullable
    public UUID owner() {
        return entityData.get(OWNER).orElse(null);
    }

    public boolean chasing() {
        return entityData.get(CHASE);
    }

    // 0〜1 の薄さ (消えていくとき・人の残像が光の中のとき)
    public float fade() {
        return entityData.get(FADE) / 100f;
    }

    public boolean replaying() {
        return !chasing() && index < frames.size();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(CHASE, false);
        builder.define(FADE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            float d = (float) Math.hypot(getX() - xo, getZ() - zo);
            walkSpeedO = walkSpeed;
            walkSpeed += (Math.min(d * 4f, 1f) - walkSpeed) * 0.4f;
            walkPos += walkSpeed;
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (chasing()) {
            tickChase(sl);
            return;
        }
        if (index < frames.size()) {
            Frame f = frames.get(index++);
            setPos(f.pos());
            setYRot(f.yaw());
            setXRot(f.pitch());
            if (StarLamps.inLight(sl, position().add(0, 0.5, 0))) {
                vanish(sl);
                return;
            }
            if (index % 4 == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.9, getZ(), 1, 0.2, 0.5, 0.2, 0.0);
            }
            return;
        }
        // 覚えた動きの終わり: 少し残って薄れて消える
        linger++;
        entityData.set(FADE, Math.min(100, linger * 100 / LINGER));
        if (linger >= LINGER) {
            vanish(sl);
        }
    }

    private void tickChase(ServerLevel sl) {
        Player target = chaseTarget == null ? null : sl.getPlayerByUUID(chaseTarget);
        if (target == null || !target.isAlive()) {
            discard();
            return;
        }
        trail.addLast(target.position());
        while (trail.size() > CHASE_DELAY) {
            Vec3 p = trail.pollFirst();
            Vec3 d = p.subtract(position());
            setPos(p);
            if (d.horizontalDistanceSqr() > 1.0E-4) {
                setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            }
        }
        boolean lit = StarLamps.inLight(sl, position().add(0, 0.5, 0));
        entityData.set(FADE, lit ? 80 : 0);
        if (hitCooldown > 0) {
            hitCooldown--;
        }
        if (!lit && hitCooldown == 0 && trail.size() >= CHASE_DELAY) {
            for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.3), e -> e.isAlive() && !e.isSpectator())) {
                p.hurt(damageSources().magic(), chaseDamage);
                hitCooldown = 20;
                sl.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }
        if (tickCount % 3 == 0) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.2, getZ(), 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    public void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 24, 0.3, 0.8, 0.3, 0.04);
        sl.playSound(null, getX(), getY(), getZ(), RiftBlocks.ECHO_FADE.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
        discard();
    }

    // 乗れる体 (人の残像は乗れない)
    @Override
    public boolean canBeCollidedWith() {
        return !chasing() && isAlive();
    }

    @Override
    public boolean isPickable() {
        return false;
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
