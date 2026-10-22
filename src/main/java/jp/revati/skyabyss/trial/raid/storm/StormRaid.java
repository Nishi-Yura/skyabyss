package jp.revati.skyabyss.trial.raid.storm;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.storm.mob.StormMob;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 雷翼のレイドの三つの部屋 (docs/design/12 の「レイド」節)。歩 SR1「避雷の羽運び」・智 SR2「銅の回路つなぎ」・闘 SR3「帯電の連鎖」。
// 色は雷翼のパレット (RaidKit.STORM: 銅・緑青・蛙灯)。予告は赤だけ (combat/Telegraph)
public final class StormRaid {
    private StormRaid() {
    }

    public static final String SR1 = "SR1";
    public static final String SR2 = "SR2";
    public static final String SR3 = "SR3";

    public static void register() {
        RaidRooms.register(Titans.THUNDER_WING, Trial.Slot.WALK, SR1, FeatherCarry::new);
        RaidRooms.register(Titans.THUNDER_WING, Trial.Slot.WIT, SR2, CircuitRoom::new);
        RaidRooms.register(Titans.THUNDER_WING, Trial.Slot.FIGHT, SR3, ChargeChain::new);
    }

    // ---- 共通の小道具
    // 見た目だけの雷 (火も傷も出ない)
    static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
        if (b != null) {
            b.moveTo(at.x, at.y, at.z);
            b.setVisualOnly(true);
            level.addFreshEntity(b);
        }
    }

    static void sparks(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, spread, spread, spread, 0.05);
    }

    static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, 0.01);
    }

    static void zap(ServerLevel level, BlockPos at, float vol, float pitch) {
        level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, vol, pitch);
    }

    // 雷翼の敵を一体 (そのグループのもの。ディスクに保存されない)
    @Nullable
    static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, UUID group, double x, double y, double z) {
        T m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
        m.addTag(StormMob.TRIAL_TAG);
        if (m instanceof GroupOwned owned) {
            owned.setGroupId(group);
        }
        m.setPersistenceRequired();
        return level.addFreshEntity(m) ? m : null;
    }

    static void set(ServerLevel level, int x, int y, int z, BlockState st) {
        RaidKit.set(level, new BlockPos(x, y, z), st);
    }
}
