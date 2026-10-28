package jp.revati.skyabyss.story.ch5.warden;

import jp.revati.skyabyss.story.ch5.AbyssBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 心臓の間の入口を闇で閉じる (docs/design/36 の 6 章)。番人が出ているあいだ、入口の細い通路 (印 heart から南へ 31、幅 3・高さ 4) を
// 黒い幕でふさぎ、両側に闇の霧を出す。戦いが終わる・間が空いて闇へ戻ると開く。出ていない番人のぶん (読み込み直し) は開いたままにする。
// 幕の場所にいた人は、間の外 (南) へ押し出す。WardenArena.onTick が 10 tick ごとに update を呼ぶ
final class WardenSeal {
    static final int DOOR_DZ = 31;
    static final int HALF_W = 1;
    static final int HEIGHT = 4;
    private static final BlockState DARK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static boolean sealed;

    private WardenSeal() {
    }

    static void update(ServerLevel level, boolean fight) {
        BlockPos heart = AbyssBuild.mark("heart");
        if (heart == null) {
            return;
        }
        BlockPos base = heart.offset(0, -1, DOOR_DZ);
        if (fight && !sealed) {
            for (ServerPlayer p : level.players()) {
                if (Math.abs(p.getX() - (base.getX() + 0.5)) <= HALF_W + 1 && Math.abs(p.getZ() - (base.getZ() + 0.5)) <= 1.2
                        && p.getY() >= base.getY() - 1 && p.getY() <= base.getY() + HEIGHT) {
                    p.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.5);
                }
            }
            set(level, base, DARK);
            level.playSound(null, base, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.2f, 0.5f);
            sealed = true;
        } else if (!fight && (sealed || level.getBlockState(base).is(Blocks.BLACK_CONCRETE))) {
            set(level, base, Blocks.AIR.defaultBlockState());
            level.playSound(null, base, SoundEvents.SCULK_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
            sealed = false;
        }
        if (sealed) {
            for (int side = -1; side <= 1; side += 2) {
                level.sendParticles(ParticleTypes.SQUID_INK, base.getX() + 0.5, base.getY() + 2, base.getZ() + 0.5 + side * 0.7, 6, 0.9, 1.2, 0.1, 0.01);
            }
        }
    }

    private static void set(ServerLevel level, BlockPos base, BlockState st) {
        for (int dx = -HALF_W; dx <= HALF_W; dx++) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                BlockPos p = base.offset(dx, dy, 0);
                BlockState now = level.getBlockState(p);
                // 開くときは幕だけを消す (通路の石は触らない)
                if (st.isAir() ? now.is(Blocks.BLACK_CONCRETE) : now.isAir()) {
                    level.setBlock(p, st, 3);
                }
            }
        }
    }

    // 自己テスト用
    static boolean closed(ServerLevel level) {
        BlockPos heart = AbyssBuild.mark("heart");
        return heart != null && level.getBlockState(heart.offset(0, 0, DOOR_DZ)).is(Blocks.BLACK_CONCRETE);
    }
}
