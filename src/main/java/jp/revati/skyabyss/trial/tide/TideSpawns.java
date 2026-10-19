package jp.revati.skyabyss.trial.tide;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

// 潮殻の試練のセルの敵を湧かせる (そのグループのもの。ほかのグループには見えず、狙わず、傷を与え合わない。保存しない印つき)
final class TideSpawns {
    private TideSpawns() {
    }

    @Nullable
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, BlockPos at, @Nullable UUID group) {
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.addTag(TideMob.TRIAL_TAG);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(mob);
        return mob;
    }
}
