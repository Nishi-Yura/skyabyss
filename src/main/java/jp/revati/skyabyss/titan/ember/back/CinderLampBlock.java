package jp.revati.skyabyss.titan.ember.back;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

// 燠の灯 (13 の 6.1 / 00 の 126): 鎖で吊るす (床にも置ける) 灯り。燠の殻から作業台で作る。光 12。
// 黒石と鉄の籠の中で熾が赤く息をして、ときどき火の粉と灰がこぼれる。吊るす・置く・水に浸かる挙動はバニラの角灯と同じ。
// 形は tools/gen_back_blocks.py (models/block/cinder_lamp{,_hanging}.json)
public class CinderLampBlock extends LanternBlock {
    public static final int LIGHT = 12;

    public CinderLampBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + (state.getValue(HANGING) ? 0.45 : 0.35);
        double z = pos.getZ() + 0.5;
        // ごくまれに、籠の隙間から火の粉がひとつ昇る (ちらつきの代わり。明るさ自体は変えない)
        if (random.nextInt(14) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.25, y + 0.1, z + (random.nextDouble() - 0.5) * 0.25,
                    0, 0.004, 0);
        }
        if (random.nextInt(22) == 0) {
            level.addParticle(ParticleTypes.ASH, x + (random.nextDouble() - 0.5) * 0.4, y - 0.25, z + (random.nextDouble() - 0.5) * 0.4,
                    0, -0.01, 0);
        }
    }
}
