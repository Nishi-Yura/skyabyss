package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 東の試練 雷の骨 (12 の 4.2節)。
// - 招雷: 嵐のあいだ 4 秒ごとに、回廊の中で、まだ満ちていない柱から 6 ブロック以内にいる誰か一人 (直前と別の人) の足もとに輪。
//   輪が柱から 3 ブロック以内なら雷は柱へ (人には当たらない)。柱から離れた所にいる人は狙われない
// - 柱は 3 回受けると満ち、対になる骨の戸が開く (リセットまで開いたまま)。柱の段は根元の 3 つの電球で見える
// - 予告線 (放電): 嵐のあいだ 3 秒ごとに、回廊の人のそばに壁から壁へ 1.2 秒光る線。低い線 (床から 1 段。跳び越す) と
//   高い線 (頭の高さ。しゃがんで潜る) の二種類。線の上の人に 12
final class StormEast {
    static final int STRIKE_EVERY = 80;
    static final int STRIKE_WARN = 24;
    static final float STRIKE_DAMAGE = 11f;
    static final int LINE_EVERY = 60;
    static final int LINE_WARN = 24;
    static final float LINE_DAMAGE = 12f;
    private static final DustParticleOptions LOW = new DustParticleOptions(new Vector3f(0.75f, 0.9f, 1f), 1.0f);
    private static final DustParticleOptions HIGH = new DustParticleOptions(new Vector3f(1f, 0.95f, 0.6f), 1.0f);

    record Line(double x, boolean high, int[] left) {
    }

    private static final List<Line> LINES = new ArrayList<>();
    private static UUID lastTarget;
    private static int lineCount;

    private StormEast() {
    }

    static boolean inCorridor(Vec3 p) {
        return p.x > EAST_X1 - 0.5 && p.x < EAST_X2 + 1 && p.z > BONE_Z1 - 0.5 && p.z < BONE_Z2 + 1.5 && p.y > BONE_FLOOR && p.y < BONE_FLOOR + EAST_HEIGHT + 1;
    }

    // 柱を登録し直す (建てたあと・リセット)。開いている戸の柱は満ちたまま
    static void registerRods(ServerLevel level, StormBackState state) {
        for (int i = 0; i < EAST_RODS.size(); i++) {
            StormRods.Rod r = StormRods.register(level, EAST_RODS.get(i));
            if (state.eastDoorOpen(i) || state.rung(StormBackState.Trial.EAST)) {
                StormRods.charge(level, r.base, StormRods.FULL);
            }
        }
    }

    static void apply(ServerLevel level, StormBackState state) {
        registerRods(level, state);
        for (int i = 0; i < EAST_DOORS.length; i++) {
            setDoor(level, i, state.eastDoorOpen(i) || state.sleeping() == StormBackState.Trial.EAST);
        }
        showRods(level);
        LINES.clear();
    }

    static void setDoor(ServerLevel level, int i, boolean open) {
        int x = EAST_DOORS[i];
        for (int z = -18; z <= -16; z++) {
            for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + 4; y++) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState want = open ? Blocks.AIR.defaultBlockState() : ModBlocks.SEALED_GATE.get().defaultBlockState();
                if (!level.getBlockState(p).equals(want)) {
                    level.setBlock(p, want, 3);
                }
            }
        }
    }

    // 柱の段の電球 (根元の上の 3 つ)
    static void showRods(ServerLevel level) {
        for (StormRods.Rod r : StormRods.all(level)) {
            for (int i = 0; i < 3; i++) {
                BlockPos p = r.base.above(1 + i);
                BlockState s = level.getBlockState(p);
                boolean lit = r.charge() > i && !StormRods.isGrounded(r.base);
                if (s.is(Blocks.WAXED_COPPER_BULB) && s.getValue(CopperBulbBlock.LIT) != lit) {
                    level.setBlock(p, s.setValue(CopperBulbBlock.LIT, lit), 3);
                }
            }
        }
    }

    static void tick(ServerLevel level, StormBackState state, boolean peaceful) {
        long gt = level.getGameTime();
        if (gt % 5 == 0) {
            showRods(level);
            openFullDoors(level, state);
        }
        if (peaceful || StormBackClock.phase(level) != StormBackClock.Phase.STORM) {
            LINES.clear();
            return;
        }
        tickStorm(level, gt);
    }

    static int openDoors(StormBackState state) {
        int n = 0;
        for (int i = 0; i < EAST_RODS.size(); i++) {
            if (state.eastDoorOpen(i)) {
                n++;
            }
        }
        return n;
    }

    // 満ちた柱の戸を開ける
    static void openFullDoors(ServerLevel level, StormBackState state) {
        {
            for (int i = 0; i < EAST_RODS.size(); i++) {
                if (!state.eastDoorOpen(i) && StormRods.isFull(level, EAST_RODS.get(i))) {
                    state.openEastDoor(i);
                    setDoor(level, i, true);
                    BlockPos d = new BlockPos(EAST_DOORS[i], BONE_FLOOR + 2, -17);
                    level.playSound(null, d, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.6f);
                    level.playSound(null, d, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6f, 1.8f);
                    for (ServerPlayer p : level.players()) {
                        if (inCorridor(p.position())) {
                            Notice.bar(p, ModText.progress("message.skyabyss.storm.door_opened", openDoors(state)));
                        }
                    }
                }
            }
        }
    }

    private static void tickStorm(ServerLevel level, long gt) {
        // 招雷
        if (gt % STRIKE_EVERY == 0) {
            List<ServerPlayer> cands = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || !p.isAlive() || !inCorridor(p.position())) {
                    continue;
                }
                for (BlockPos r : EAST_RODS) {
                    if (!StormRods.isFull(level, r) && StormRods.find(level, p.position(), 6.0) != null) {
                        cands.add(p);
                        break;
                    }
                }
            }
            if (cands.size() > 1) {
                cands.removeIf(p -> p.getUUID().equals(lastTarget));
            }
            if (!cands.isEmpty()) {
                ServerPlayer t = cands.get(level.random.nextInt(cands.size()));
                lastTarget = t.getUUID();
                StormStrike.call(level, t, STRIKE_WARN, STRIKE_DAMAGE, null);
            }
        }
        // 予告線
        if (gt % LINE_EVERY == 30) {
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || !inCorridor(p.position())) {
                    continue;
                }
                double x = Math.floor(p.getX() + (level.random.nextInt(5) - 2)) + 0.5;
                LINES.add(new Line(x, (lineCount++ & 1) == 1, new int[] {LINE_WARN}));
                level.playSound(null, x, BONE_FLOOR + 2, -17, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.7f, 1.8f);
            }
        }
        for (Iterator<Line> it = LINES.iterator(); it.hasNext();) {
            Line l = it.next();
            int left = --l.left()[0];
            double y = BONE_FLOOR + 1 + (l.high() ? 1.75 : 0.3);
            Vec3 a = new Vec3(l.x(), y, BONE_Z1);
            Vec3 b = new Vec3(l.x(), y, BONE_Z2 + 1);
            if (left > 0) {
                if (left % 3 == 0) {
                    StormFx.line(level, l.high() ? HIGH : LOW, a, b, 0.6);
                    // 床と壁にも印 (線の両端)
                    StormFx.forced(level, ParticleTypes.ELECTRIC_SPARK, l.x(), y, BONE_Z1 + 0.2, 2, 0.05, 0.1, 0.05, 0.02);
                    StormFx.forced(level, ParticleTypes.ELECTRIC_SPARK, l.x(), y, BONE_Z2 + 0.8, 2, 0.05, 0.1, 0.05, 0.02);
                }
                continue;
            }
            it.remove();
            StormFx.line(level, ParticleTypes.ELECTRIC_SPARK, a, b, 0.3);
            StormFx.line(level, ParticleTypes.END_ROD, a, b, 1.0);
            level.playSound(null, l.x(), y, -17, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1f, 1.6f);
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || !p.isAlive() || Math.abs(p.getX() - l.x()) > 0.8 || !inCorridor(p.position())) {
                    continue;
                }
                if (hits(p.getY(), p.getBbHeight(), l.high())) {
                    p.hurt(level.damageSources().lightningBolt(), LINE_DAMAGE);
                }
            }
        }
    }

    // 線に当たるか: 低い線は足が床から 0.6 より上 (跳んでいる) なら当たらない。高い線は背が 1.5 以下 (しゃがむ) なら当たらない
    static boolean hits(double feetY, double height, boolean high) {
        double floor = BONE_FLOOR + 1;
        if (high) {
            return feetY + height > floor + 1.6 && feetY < floor + 2.4;
        }
        return feetY < floor + 0.6;
    }

    static void clear() {
        LINES.clear();
        lastTarget = null;
    }
}
