package jp.revati.skyabyss.client.titan;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.solid.SolidTitan;
import jp.revati.skyabyss.titan.solid.TitanSolids;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 巨神の「重さ」の見せ方のうち、触れたものが動く様子 (docs/design/02 の「重さ」)。体の下を 2 tick ごとに数か所選び、
//  - 雲の高さ (雲のある世界) に体がかかっていれば、その高さに雲のちぎれを出す (体が雲をかき分ける)
//  - 海面に体がかかっていれば、波しぶきと泡を出す (潮殻は喫水の線に沿って、泳ぐ岩鯨が海をかすめるときも)
//  - 体の底が地面に近ければ (余燼の脚)、動いているあいだ足もとにその地面の土ぼこりを出す
// 粒の数は少なく (1 体あたり 1 tick に最大 4 か所)、カメラから 110 ブロック以内の所だけ。音は無し (巨神の足音の素材はまだ無い)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TitanFx {
    private static final double RANGE = 110.0;

    private TitanFx() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Entity cam = mc.cameraEntity;
        if (level == null || cam == null || mc.isPaused() || !jp.revati.skyabyss.client.lowpoly.LowPoly.titanWeight) {
            return;
        }
        for (Entity titan : TitanSolids.titans()) {
            if (titan.level() != level || !(titan instanceof SolidTitan solid) || !solid.solidActive() || titan.tickCount % 2 != 0) {
                continue;
            }
            if (cam.distanceToSqr(titan) > 260.0 * 260.0) {
                continue;
            }
            TitanVolume volume = TitanVolume.get(solid.volumeName());
            if (volume == null) {
                continue;
            }
            emit(level, cam, titan, volume.coreColumns());
        }
    }

    private static void emit(ClientLevel level, Entity cam, Entity titan, List<TitanVolume.Column> columns) {
        if (columns.isEmpty()) {
            return;
        }
        RandomSource rnd = level.random;
        float yaw = titan.getYRot();
        double vx = titan.getX() - titan.xo;
        double vz = titan.getZ() - titan.zo;
        double speed = Math.hypot(vx, vz);
        float cloud = level.effects().getCloudHeight();
        double sea = level.getSeaLevel();
        for (int n = 0; n < 4; n++) {
            TitanVolume.Column c = columns.get(rnd.nextInt(columns.size()));
            Vec3 d = TitanSolids.toWorldDelta(yaw, new Vec3(c.side() + (rnd.nextDouble() - 0.5) * 2.0, 0, c.fwd() + (rnd.nextDouble() - 0.5) * 2.0));
            double x = titan.getX() + d.x;
            double z = titan.getZ() + d.z;
            if (cam.distanceToSqr(x, cam.getY(), z) > RANGE * RANGE) {
                continue;
            }
            double lo = titan.getY() + c.lo();
            double hi = titan.getY() + c.hi();
            // 雲: 体が雲の高さにかかっている柱。体の動きと逆へ流れる
            if (!Float.isNaN(cloud) && lo - 1.0 <= cloud && hi + 3.0 >= cloud && rnd.nextFloat() < 0.35f) {
                level.addParticle(ParticleTypes.CLOUD, x, cloud + rnd.nextDouble() * 2.0, z, -vx * 0.5 + (rnd.nextDouble() - 0.5) * 0.04, 0.01, -vz * 0.5 + (rnd.nextDouble() - 0.5) * 0.04);
            }
            // 海: 体が海面にかかっている柱 (喫水の線)。泳いでいるほど派手に
            if (lo <= sea + 0.5 && hi >= sea - 0.5 && rnd.nextFloat() < 0.25f + (float) Math.min(speed * 2.0, 0.5)) {
                level.addParticle(ParticleTypes.SPLASH, x, sea + 0.1, z, -vx * 0.3 + (rnd.nextDouble() - 0.5) * 0.1, 0.12, -vz * 0.3 + (rnd.nextDouble() - 0.5) * 0.1);
                if (rnd.nextFloat() < 0.5f) {
                    level.addParticle(ParticleTypes.BUBBLE, x, sea - 0.3, z, 0, 0.05, 0);
                }
            }
            // 地面: 底が地面の近くで、動いているあいだ土ぼこり
            if (speed > 0.03 && c.lo() <= 3.0 && rnd.nextFloat() < 0.3f) {
                BlockPos below = BlockPos.containing(x, lo - 0.2, z);
                BlockState state = level.getBlockState(below);
                if (state.isSolidRender(level, below) || state.canOcclude()) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, below.getY() + 1.05, z, (rnd.nextDouble() - 0.5) * 0.1, 0.08, (rnd.nextDouble() - 0.5) * 0.1);
                }
            }
        }
    }
}
