package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

// 東の試練 雷の骨 (12 の 4.2節・25 の 5章 B)。
// - 雷の回路 (StormCircuit): 受け柱 → (中継の柱) → 戸の柱を銅の導線でつなぎ、レバーで雷の通り道を切り替える。金の床に立つと雷が受け柱へ落ち、
//   柱から柱へ跳んで、戸の柱に届くと対の骨の戸が開く (リセットまで開いたまま)。導線に雷が通った所は光り続ける
// - 予告線 (放電): 嵐のあいだ 3 秒ごとに、回廊の人のそばに壁から壁へ 1.2 秒光る線。低い線 (床から 1 段。跳び越す) と
//   高い線 (頭の高さ。しゃがんで潜る) の二種類。線の上の人に 12。凪には線がない
// - 拍はその回廊の持ち主のグループの舵 (StormHelm) の固定があればそれ、なければ背中の拍。凪は輪が遅く、雷が柱から柱へ跳ぶのも遅い。嵐は速い
// グループ (docs/design/10 の 9 章): 回廊 (肩の門の先から雷獣の巣の口まで) は部屋の占有 (RoomLocks.EAST_ROOM)。
// 柱・回路・予告線は回廊の持ち主のグループのもので、持ち主が変わると回路をその記録から置き直す
// (開けた戸の回路は柱が満ち、導線が光る)。骨の戸はグループごとの扉 (doorBlocks): 回路をつないだグループにだけ開く
final class StormEast {
    // 招雷の傷 (輪の中に人が立っていて、柱へ落ちなかったとき。回路の輪は必ず受け柱へ落ちるので当たらない)
    static final float STRIKE_DAMAGE = 11f;
    static final int LINE_EVERY = 60;
    static final int LINE_WARN = 24;
    static final float LINE_DAMAGE = 12f;
    private static final DustParticleOptions LOW = new DustParticleOptions(new Vector3f(0.75f, 0.9f, 1f), 1.0f);
    private static final DustParticleOptions HIGH = new DustParticleOptions(new Vector3f(1f, 0.95f, 0.6f), 1.0f);

    record Line(double x, boolean high, int[] left) {
    }

    // 回廊の持ち主の予告線と、直前に狙った人 (持ち主が変わると消す)
    private static final List<Line> LINES = new ArrayList<>();
    private static int lineCount;

    private StormEast() {
    }

    static boolean inCorridor(Vec3 p) {
        return p.x > EAST_X1 - 0.5 && p.x < EAST_X2 + 1 && p.z > BONE_Z1 - 0.5 && p.z < BONE_Z2 + 1.5 && p.y > BONE_FLOOR && p.y < BONE_FLOOR + EAST_HEIGHT + 1;
    }

    // 部屋の占有の箱 (回廊と肘の洞。雷獣の巣の口の手前まで)
    static AABB room() {
        return new AABB(EAST_X1, BONE_FLOOR, BONE_Z1, RAIJU_NEST_MOUTH.minX, BONE_FLOOR + EAST_HEIGHT + 1, BONE_Z2 + 1);
    }

    // 骨の戸 i のブロック (グループごとの扉)
    static List<BlockPos> doorBlocks(int i) {
        List<BlockPos> out = new ArrayList<>();
        int x = EAST_DOORS[i];
        for (int z = WZ - DOOR_HALF; z <= WZ + DOOR_HALF; z++) {
            for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + DOOR_HEIGHT; y++) {
                out.add(new BlockPos(x, y, z));
            }
        }
        return out;
    }

    // 回路を置き直す (柱の登録・戸が開いた回路の光・レバー・導線)。progress が null なら誰も持っていない回廊 (全部空)
    static void registerRods(ServerLevel level, @Nullable StormProgress progress) {
        StormCircuit.apply(level, progress);
    }

    // 回廊を持ち主のグループの記録のとおりに置き直す (持ち主が変わったとき)
    static void apply(ServerLevel level, StormProgress progress) {
        registerRods(level, progress);
        LINES.clear();
        StormStrike.cancelIn(level, room());
        showRods(level);
    }

    // だれも持っていない回廊 (柱は空)
    static void applyNeutral(ServerLevel level) {
        registerRods(level, null);
        LINES.clear();
        StormStrike.cancelIn(level, room());
        showRods(level);
    }

    // 柱の段の電球 (根元の上の 3 つ)
    static void showRods(ServerLevel level) {
        for (StormRods.Rod r : StormRods.all(level)) {
            for (int i = 0; i < 3; i++) {
                BlockPos p = r.base.above(1 + i);
                BlockState s = level.getBlockState(p);
                boolean lit = r.charge() > i && !StormRods.isGrounded(r.base);
                if (s.is(StormBackSetup.ROD_BULB.get()) && s.getValue(StormBlocks.RodBulb.LIT) != lit) {
                    level.setBlock(p, s.setValue(StormBlocks.RodBulb.LIT, lit), 3);
                }
            }
        }
    }

    // owner: 回廊の持ち主 (いなければ null。そのときは柱の電球だけ見る)
    static void tick(ServerLevel level, @Nullable UUID owner, boolean peaceful) {
        long gt = level.getGameTime();
        StormCircuit.tickFlash(level);
        if (gt % 5 == 0) {
            showRods(level);
            if (owner != null) {
                openFullDoors(level, StormProgress.of(level, owner));
            }
        }
        if (owner == null) {
            LINES.clear();
            return;
        }
        // どの拍でも雷は落ちて回路は組める (25 の 2章)。拍が変えるのは遊び方: 凪は輪と雷が遅く、放電の線もない。嵐は速く、線が走る
        boolean storm = StormHelm.phase(level, StormBackState.Trial.EAST, owner) == StormBackClock.Phase.STORM;
        StormFx.to(owner, () -> StormCircuit.tick(level, owner, StormProgress.of(level, owner), storm, peaceful));
        if (peaceful) {
            LINES.clear();
            return;
        }
        if (!storm) {
            LINES.clear();
        }
        StormFx.to(owner, () -> tickStorm(level, owner, gt, storm));
    }

    // 満ちた柱の戸を、回廊の持ち主のグループにだけ開ける
    static void openFullDoors(ServerLevel level, StormProgress progress) {
        for (int i = 0; i < EAST_RODS.size(); i++) {
            if (!progress.eastDoorOpen(i) && StormRods.isFull(level, EAST_RODS.get(i))) {
                progress.openEastDoor(i);
                GroupGates.open(level, progress.group, doorBlocks(i));
                BlockPos d = new BlockPos(EAST_DOORS[i], BONE_FLOOR + 2, WZ);
                level.playSound(null, d, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.6f);
                level.playSound(null, d, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6f, 1.8f);
                for (ServerPlayer p : Groups.online(level, progress.group)) {
                    if (inCorridor(p.position())) {
                        Notice.bar(p, ModText.progress("message.skyabyss.storm.door_opened", progress.openDoorCount()));
                    }
                }
            }
        }
    }

    private static void tickStorm(ServerLevel level, UUID owner, long gt, boolean storm) {
        // 予告線 (嵐だけ)
        if (storm && gt % LINE_EVERY == 30) {
            for (ServerPlayer p : Groups.alive(level, owner)) {
                if (!inCorridor(p.position())) {
                    continue;
                }
                double x = Math.floor(p.getX() + (level.random.nextInt(5) - 2)) + 0.5;
                LINES.add(new Line(x, (lineCount++ & 1) == 1, new int[] {LINE_WARN}));
                level.playSound(null, x, BONE_FLOOR + 2, WZ, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.7f, 1.8f);
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
            level.playSound(null, l.x(), y, WZ, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1f, 1.6f);
            for (ServerPlayer p : Groups.alive(level, owner)) {
                if (p.isCreative() || Math.abs(p.getX() - l.x()) > 0.8 || !inCorridor(p.position())) {
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
    }
}
