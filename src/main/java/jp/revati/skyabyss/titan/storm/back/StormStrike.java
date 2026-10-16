package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

// 【仮の実装】招雷の輪 (12 の 10.3節・11.1節の約束の API): 足もとに白い輪を warnTicks 出してから雷を落とす。
// 輪から 3 ブロック以内に避雷の柱があれば、雷は柱へ落ちて柱が 1 段満ちる (人には当たらない)。
// 本物は背中の担当 (担当 1) が作る。統合で本物に置き換える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormStrike {
    public static final double RADIUS = 1.5;
    public static final double ROD_REACH = 3.0;
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level, Vec3 at, int strikeAt, float damage, @Nullable Entity owner, int[] age) {
    }

    private StormStrike() {
    }

    // target の足もとに輪を出し、warnTicks のあとに雷 (damage) を落とす
    public static void call(ServerLevel level, Vec3 target, int warnTicks, float damage, @Nullable Entity owner) {
        PENDING.add(new Pending(level, target, Math.max(1, warnTicks), damage, owner, new int[] {0}));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level() != level) {
                continue;
            }
            int age = ++p.age()[0];
            if (age % 3 == 1) {
                ring(level, p.at());
            }
            if (age >= p.strikeAt()) {
                it.remove();
                due.add(p);
            }
        }
        for (Pending p : due) {
            strike(level, p);
        }
    }

    private static void ring(ServerLevel level, Vec3 at) {
        DustParticleOptions white = new DustParticleOptions(new Vector3f(0.95f, 0.97f, 1.0f), 1.3f);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) > 64 * 64) {
                continue;
            }
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                level.sendParticles(player, white, true, at.x + Math.cos(a) * RADIUS, at.y + 0.1, at.z + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
            }
            level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, at.x, at.y + 0.2, at.z, 2, 0.4, 0.05, 0.4, 0.02);
        }
    }

    private static void strike(ServerLevel level, Pending p) {
        BlockPos rod = StormRods.find(level, p.at(), ROD_REACH);
        Vec3 hit = p.at();
        if (rod != null && !StormRods.isGrounded(rod)) {
            StormRods.charge(level, rod, 1);
            hit = Vec3.atBottomCenterOf(rod).add(0, 1, 0);
        } else {
            rod = null;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(hit.x, hit.y, hit.z);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.6f, 1.3f);
        if (rod != null) {
            return;
        }
        DamageSource source = p.owner() instanceof LivingEntity mob ? level.damageSources().mobAttack(mob) : level.damageSources().lightningBolt();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - p.at().x;
            double dz = player.getZ() - p.at().z;
            if (dx * dx + dz * dz <= RADIUS * RADIUS + 0.3 && Math.abs(player.getY() - p.at().y) < 2.5) {
                if (player.hurt(source, p.damage())) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1));
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
