package jp.revati.skyabyss.dungeon;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// ダンジョンごとの仕掛け (docs/design/55 の 1・3 章の表)。印は tools/gen/world/build_dungeons.py。
//   潮の洞     tide_<n>_lo/hi の箱が TIDE_PERIOD ごとに満ち引きする (満ちると水、引くと空き)。引いたあいだにだけ下の道を通れる
//   雷晶の空洞 crystal_<n>_<扉> の結晶に CHARGE 回当てる (殴る・矢・技) と満ち、同じ扉の結晶がみな満ちると扉 (door_<扉>_crystal) が開く
//   熔けた坑道 lava_<n>_<a|b>_lo/hi の箱が LAVA_PERIOD ごとに溶岩と空きを入れ替える (a と b は逆)。切り替わる前に WARN 前から知らせる
//   鯨骨の坑   トロッコ (next に乗ると下の層へ運ばれる。Dungeons.next)
public final class Mechanics {
    public static final int TIDE_PERIOD = 1400;
    public static final int LAVA_PERIOD = 400;
    public static final int WARN = 60;
    public static final int CHARGE = 3;

    private Mechanics() {
    }

    static void tick(DungeonFloor f, ServerLevel level, long t, List<ServerPlayer> here) {
        if (here.isEmpty()) {
            return;
        }
        long age = t - f.run.startedTick;
        for (String lo : f.terrain.marksStarting("tide_")) {
            if (!lo.endsWith("_lo")) {
                continue;
            }
            String box = lo.substring(0, lo.length() - 3);
            boolean high = (age / (TIDE_PERIOD / 2)) % 2 == 0;
            long toNext = TIDE_PERIOD / 2 - age % (TIDE_PERIOD / 2);
            if (toNext == WARN) {
                for (ServerPlayer p : here) {
                    if (Dungeons.real(p)) {
                        Notice.hint(p, high ? "message.skyabyss.dungeon.tide_ebb" : "message.skyabyss.dungeon.tide_flood");
                    }
                }
                level.playSound(null, f.at(lo), high ? SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT : SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.AMBIENT, 2f, 0.6f);
            }
            set(f, level, box, high, Blocks.WATER.defaultBlockState());
        }
        for (String lo : f.terrain.marksStarting("lava_")) {
            if (!lo.endsWith("_lo")) {
                continue;
            }
            String box = lo.substring(0, lo.length() - 3);
            boolean a = box.endsWith("_a");
            boolean on = ((age / LAVA_PERIOD) % 2 == 0) == a;
            long toNext = LAVA_PERIOD - age % LAVA_PERIOD;
            if (toNext <= WARN && t % 10 == 0) {
                // 切り替わる前: 溶岩の来る所から熱い粒、引く所から湯気
                BlockPos p0 = f.at(lo);
                BlockPos p1 = f.at(box + "_hi");
                Vec3 c = new Vec3((p0.getX() + p1.getX()) / 2.0 + 0.5, p0.getY() + 0.3, (p0.getZ() + p1.getZ()) / 2.0 + 0.5);
                level.sendParticles(on ? ParticleTypes.SMOKE : ParticleTypes.LAVA, c.x, c.y, c.z, 8, Math.abs(p1.getX() - p0.getX()) / 3.0 + 0.3, 0.2,
                        Math.abs(p1.getZ() - p0.getZ()) / 3.0 + 0.3, 0.01);
                if (toNext == WARN) {
                    level.playSound(null, p0, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2f, 0.6f);
                }
            }
            set(f, level, box, on, Blocks.LAVA.defaultBlockState());
        }
    }

    // 箱の空きを液で満たす / 液を空きに戻す (箱の今の向きが変わったときだけ)
    static void set(DungeonFloor f, ServerLevel level, String box, boolean full, BlockState liquid) {
        Boolean was = f.phase.get(box);
        if (was != null && was == full) {
            return;
        }
        f.phase.put(box, full);
        BlockPos a = f.at(box + "_lo");
        BlockPos b = f.at(box + "_hi");
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            BlockState s = level.getBlockState(q);
            if (full && s.isAir()) {
                level.setBlock(q, liquid, 2);
            } else if (!full && s.getFluidState().isSource() && s.getBlock() == liquid.getBlock()) {
                level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // 流れた液 (箱の外へ流れ出た分) も引くときに消す
        if (!full) {
            for (BlockPos q : BlockPos.betweenClosed(a.offset(-2, -1, -2), b.offset(2, 1, 2))) {
                BlockState s = level.getBlockState(q);
                if (!s.getFluidState().isEmpty() && s.getBlock() == liquid.getBlock()) {
                    level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 結晶
    // 人が結晶の印のそばのブロックを打った (Dungeons の左クリック・矢から)。満ちたら true
    static boolean strike(DungeonFloor f, ServerLevel level, BlockPos hit) {
        for (String k : f.terrain.marksStarting("crystal_")) {
            BlockPos c = f.at(k);
            if (c.distManhattan(hit) > 2) {
                continue;
            }
            int n = f.charge.getOrDefault(k, 0);
            if (n >= CHARGE) {
                return false;
            }
            f.charge.put(k, n + 1);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 20 + 10 * n, 0.4, 0.6, 0.4, 0.2);
            level.playSound(null, c, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4f, 0.8f + 0.3f * n);
            if (n + 1 >= CHARGE) {
                level.setBlock(c, Blocks.SEA_LANTERN.defaultBlockState(), 3);
                // 主の間の結晶: 大蜥蜴の背の溜まりを抜く
                if (f.boss != null && level.getEntity(f.boss) instanceof jp.revati.skyabyss.dungeon.mob.CrystalLizard lizard) {
                    lizard.drain();
                }
                level.playSound(null, c, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6f, 1.6f);
                for (ServerPlayer p : f.run.present()) {
                    Notice.hint(p, "message.skyabyss.dungeon.crystal_full");
                }
                return true;
            }
            return false;
        }
        return false;
    }

    // 扉 door_<n>_crystal: 印 crystal_<k>_<n> がみな満ちていれば開く
    static boolean doorOpen(DungeonFloor f, String door) {
        if (!door.endsWith("_crystal")) {
            return false;
        }
        String n = door.substring("door_".length(), door.length() - "_crystal".length());
        boolean any = false;
        for (String k : f.terrain.marksStarting("crystal_")) {
            if (k.endsWith("_" + n)) {
                any = true;
                if (f.charge.getOrDefault(k, 0) < CHARGE) {
                    return false;
                }
            }
        }
        return any;
    }
}
