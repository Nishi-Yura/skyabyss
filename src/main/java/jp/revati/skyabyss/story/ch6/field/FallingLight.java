package jp.revati.skyabyss.story.ch6.field;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 光の塊 (docs/design/46 の 4.5)。絵は持たず、サーバーが粒で描く (描く側は NoopRenderer)。一瞬止め (MomentStop) で宙に止まる。
//   降る光 (fall)   降る光の溝に降る。落ちる所に光の円 (予告 TELL tick) → 上から真っすぐ落ち、当たった所 (地面か影の窪の屋根) の半径 2.5 に傷。
//                   屋根の下にいる人には当たらない (当たった高さより下の人は数えない)
//   光の球 (shot)   望の灯守が遠くの人へ放つ。まっすぐ飛び、人に触れるか何かに当たると弾ける
//   保存しない (読み込み直すと消える)
public class FallingLight extends Entity {
    public static final String ID = "falling_light";
    public static final int TELL = 24;
    public static final double HEIGHT = 16;
    public static final double RADIUS = 2.5;
    private boolean shot;
    private float damage = 6f;
    private int age;
    private double groundY;
    @Nullable
    private Entity owner;

    public FallingLight(EntityType<? extends FallingLight> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    // 降る光: 地面 ground (立つ高さ) の上 HEIGHT から
    public static FallingLight fall(ServerLevel level, Vec3 ground, float damage) {
        FallingLight e = FieldBlocks.FALLING_LIGHT.get().create(level);
        if (e == null) {
            return null;
        }
        e.groundY = ground.y;
        e.damage = damage;
        e.moveTo(ground.x, ground.y + HEIGHT, ground.z, 0f, 0f);
        level.addFreshEntity(e);
        level.playSound(null, ground.x, ground.y, ground.z, FieldBlocks.LIGHT_TELL.get(), SoundSource.HOSTILE, 0.8f, 1.2f);
        return e;
    }

    // 光の球: from から dir の向きへ (速さ speed / tick)
    public static FallingLight shoot(ServerLevel level, @Nullable Entity owner, Vec3 from, Vec3 dir, double speed, float damage) {
        FallingLight e = FieldBlocks.FALLING_LIGHT.get().create(level);
        if (e == null) {
            return null;
        }
        e.shot = true;
        e.owner = owner;
        e.damage = damage;
        e.moveTo(from.x, from.y, from.z, 0f, 0f);
        e.setDeltaMovement(dir.normalize().scale(speed));
        level.addFreshEntity(e);
        return e;
    }

    public boolean isShot() {
        return shot;
    }

    public int age() {
        return age;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) {
            return;
        }
        age++;
        if (shot) {
            tickShot(sl);
        } else {
            tickFall(sl);
        }
    }

    private void tickFall(ServerLevel sl) {
        if (age <= TELL) {
            // 予告: 落ちる所の地面に光の円、上に光の塊が膨らむ
            if (age % 2 == 0) {
                int n = 18;
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2 + age * 0.05;
                    sl.sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * RADIUS, groundY + 0.1, getZ() + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
                }
            }
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 2, 0.25 + age * 0.01, 0.25 + age * 0.01, 0.25 + age * 0.01, 0.0);
            return;
        }
        // 落ちる (一 tick に 1.2)。途中に足場 (影の窪の屋根や地面) があれば、そこで弾ける
        double step = 1.2;
        Vec3 next = position().add(0, -step, 0);
        BlockPos at = BlockPos.containing(next);
        boolean hit = next.y <= groundY || !sl.getBlockState(at).getCollisionShape(sl, at).isEmpty();
        if (hit) {
            double y = Math.max(groundY, Math.floor(next.y) + 1.0);
            burst(sl, new Vec3(getX(), y, getZ()));
            return;
        }
        setPos(next);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.6, getZ(), 3, 0.2, 0.6, 0.2, 0.0);
        sl.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
    }

    private void tickShot(ServerLevel sl) {
        Vec3 v = getDeltaMovement();
        Vec3 next = position().add(v);
        BlockPos at = BlockPos.containing(next);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 3, 0.15, 0.15, 0.15, 0.0);
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(position(), next).inflate(0.7), e -> e.isAlive() && !e.isSpectator())) {
            p.hurt(damageSources().indirectMagic(this, owner instanceof LivingEntity le ? le : null), damage);
            burst(sl, position());
            return;
        }
        if (age > 100 || !sl.getBlockState(at).getCollisionShape(sl, at).isEmpty()) {
            burst(sl, position());
            return;
        }
        setPos(next);
    }

    // 弾ける: 半径 RADIUS (光の球は 1.5) の人に傷。当たった高さより下 (屋根の下) の人は数えない
    private void burst(ServerLevel sl, Vec3 c) {
        double r = shot ? 1.5 : RADIUS;
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, 30, r * 0.4, 0.4, r * 0.4, 0.12);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        sl.playSound(null, c.x, c.y, c.z, FieldBlocks.LIGHT_IMPACT.get(), SoundSource.HOSTILE, 1.0f, shot ? 1.4f : 1.0f);
        if (!shot) {
            for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r, 3, r), e -> e.isAlive() && !e.isSpectator())) {
                if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) <= r && p.getY() >= c.y - 0.6) {
                    p.hurt(damageSources().magic(), damage);
                }
            }
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
