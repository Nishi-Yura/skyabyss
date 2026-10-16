package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

// 【空の実装 (敵の担当が置いた)】招雷の輪 (12 の 4.2節、11.1 の API)。統合で背中の担当 (担当 1) の本物に置き換わる。
// target の足もとに白い輪を warnTicks のあいだ出し、そのあと雷を落とす。輪から 3 ブロック以内に避雷の柱があれば、雷は柱へ落ちる
// (人には当たらず、柱が 1 段満ちる)。当たると damage と 0.5 秒の鈍足。輪の半径 1.5
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormStrike {
    public static final double RADIUS = 1.5;
    public static final double ROD_RANGE = 3.0;
    private static final ParticleOptions RING = new DustParticleOptions(new Vector3f(0.92f, 0.95f, 1.0f), 1.3f);

    private record Pending(ServerLevel level, Vec3 pos, @Nullable BlockPos rod, float damage, @Nullable LivingEntity owner, int[] left) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private StormStrike() {
    }

    public static void call(ServerLevel level, Vec3 target, int warnTicks, float damage, @Nullable LivingEntity owner) {
        BlockPos rod = StormRods.find(level, target, ROD_RANGE);
        PENDING.add(new Pending(level, target, rod, damage, owner, new int[] {Math.max(1, warnTicks)}));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            tick(level);
        }
    }

    // 自己テストからも呼ぶ
    public static void tick(ServerLevel level) {
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level() != level) {
                continue;
            }
            int left = --p.left()[0];
            if (left % 2 == 0) {
                ring(p);
            }
            if (left <= 0) {
                it.remove();
                due.add(p);
            }
        }
        due.forEach(StormStrike::strike);
    }

    private static void ring(Pending p) {
        for (ServerPlayer player : p.level().players()) {
            if (player.distanceToSqr(p.pos()) > 64 * 64) {
                continue;
            }
            for (int i = 0; i < 20; i++) {
                double a = Math.PI * 2 * i / 20;
                p.level().sendParticles(player, RING, true, p.pos().x + Math.cos(a) * RADIUS, p.pos().y + 0.1, p.pos().z + Math.sin(a) * RADIUS, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void strike(Pending p) {
        ServerLevel level = p.level();
        Vec3 at = p.pos();
        if (p.rod() != null && level.getBlockState(p.rod()).isAir() == false) {
            at = Vec3.atBottomCenterOf(p.rod()).add(0, 1, 0);
            StormRods.charge(level, p.rod(), 1);
        } else {
            AABB box = new AABB(at.x - RADIUS, at.y - 1, at.z - RADIUS, at.x + RADIUS, at.y + 2.5, at.z + RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != p.owner()
                    && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                double dx = e.getX() - at.x;
                double dz = e.getZ() - at.z;
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                if (p.owner() != null && e.getClass() == p.owner().getClass()) {
                    continue;
                }
                e.hurt(p.owner() != null ? level.damageSources().mobAttack(p.owner()) : level.damageSources().lightningBolt(), p.damage());
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 1));
            }
        }
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + i * 0.6, at.z, 3, 0.1, 0.2, 0.1, 0.05);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.2f, 1.2f);
    }
}
