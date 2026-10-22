package jp.revati.skyabyss.trial.raid.tide;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 潮殻のレイドの三つの部屋 (docs/design/08 のレイドの節): 歩 TR1「真珠の灯り運び」・智 TR2「貝の音合わせ」・闘 TR3「珊瑚の苗を守る」。
// 水の仕掛け・水没はない (潮殻は水を嫌う声が多い)。部屋は RaidKit.buildCell の閉じた一室。敵は潮殻の背の敵 (磯走り・泡吹き)。
// 珊瑚憑きは潮で像になる (潮は全体の時計) ので、部屋の敵には使わない
public final class TideRaid {
    private TideRaid() {
    }

    public static void register() {
        RaidRooms.register(Titans.TIDE_SHELL, Slot.WALK, "TR1", TidePearlRoom::new);
        RaidRooms.register(Titans.TIDE_SHELL, Slot.WIT, "TR2", TideShellRoom::new);
        RaidRooms.register(Titans.TIDE_SHELL, Slot.FIGHT, "TR3", TideSaplingRoom::new);
    }

    // 部屋の敵を湧かせる (グループの敵・保存しない印つき・ctx に登録)
    @Nullable
    static Mob spawn(RaidCtx ctx, EntityType<? extends Mob> type, BlockPos at) {
        ServerLevel level = ctx.level();
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.addTag(TideMob.TRIAL_TAG);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(ctx.group());
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return ctx.adopt(mob);
    }

    // 暗い所で自然に湧いた敵 (部屋の敵の印がないもの) を消す
    static void purgeStrays(RaidCtx ctx, AABB box) {
        List<Monster> strays = ctx.level().getEntitiesOfClass(Monster.class, box, m -> !m.getTags().contains(TideMob.TRIAL_TAG));
        for (Monster m : strays) {
            m.discard();
        }
    }

    static ParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    // 赤だけの予告の決まり: 危険の印は赤だけ。これは苗がかじられた印 (赤い粒)
    static final ParticleOptions RED = dust(1f, 0.1f, 0.1f, 1.2f);

    static void puff(ServerLevel level, ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
        level.sendParticles(p, x, y, z, n, dx, dy, dz, speed);
    }

    static boolean solid(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && !s.is(Blocks.LIGHT);
    }

    @Nullable
    static ServerPlayer nearest(List<ServerPlayer> ps, double x, double z) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : ps) {
            double d = (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
