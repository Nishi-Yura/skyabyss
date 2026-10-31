package jp.revati.skyabyss.endgame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

// 一回の巡り路 (Circuits)。歩く人たち・土地・残り時間・印と、いまの課題
public final class CircuitRun {
    public enum Kind {
        HUNT, GUARD, CARRY
    }

    final int region;
    final List<UUID> members = new ArrayList<>();
    final BlockPos origin;
    final long start;
    final long end;
    int marks;
    int tasks;

    // いまの課題
    Kind kind;
    BlockPos spot;
    // 運ぶ: 届け先
    @Nullable
    BlockPos spot2;
    // 見どころの名前 (map.skyabyss.poi.<種類>。見どころでない所は null)
    @Nullable
    String place;
    @Nullable
    String place2;
    long taskStart;
    // 0 = 行く (運ぶ: 荷を受け取る)、1 = している (倒す・守る・運ぶ)
    int phase;
    final List<UUID> mobs = new ArrayList<>();
    int need;
    int progress;
    long nextWave;
    @Nullable
    UUID carrier;
    boolean ambushed;
    // 運ぶ: 荷が落ちている (背負った人が倒れた・離れた)
    boolean dropped;
    // 自己テストの人 (FakePlayer は人の一覧にいないので、ここから引く)
    final java.util.Map<UUID, net.minecraft.server.level.ServerPlayer> fakes = new java.util.HashMap<>();
    final Set<Integer> usedPois = new HashSet<>();

    CircuitRun(int region, BlockPos origin, long start, long end) {
        this.region = region;
        this.origin = origin;
        this.start = start;
        this.end = end;
    }

    public int region() {
        return region;
    }

    public int marks() {
        return marks;
    }

    public int tasks() {
        return tasks;
    }

    public Kind kind() {
        return kind;
    }

    public BlockPos spot() {
        return spot;
    }

    @Nullable
    public BlockPos spot2() {
        return spot2;
    }

    public List<UUID> members() {
        return members;
    }
}
