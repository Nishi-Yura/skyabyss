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
    // DIG・TIDE・CHARGE・FIRE は土地ごとの課題 (CircuitLand。その土地の巡り路にだけ出る)
    public enum Kind {
        HUNT, GUARD, CARRY, BREAK, DIG, TIDE, CHARGE, FIRE
    }

    // 柱の色 (Wynncraft のルートランのビーコン): 白 = ふつう、金 = 印が二つ (手ごわい)、青 = 越えると残り時間 +2 分、
    // 紫 = 呪い (2026-11-03。Wynncraft の紫のビーコン): 選ぶと呪いが一つ重なり、巡り路の残りのあいだ敵が強くなる代わりに、終わりの封の箱が一つ増える
    public enum Bonus {
        NONE(0xE6E2D6), DOUBLE(0xF2C14E), TIME(0x7FB8FF), CURSE(0xB070E8);

        public final int rgb;

        Bonus(int rgb) {
            this.rgb = rgb;
        }
    }

    // 選べる次の課題 (柱一本)。poi = 使った見どころの番号 (選ばれなかった柱の見どころは使っていないことに戻す。-1 = 見どころでない)
    record Offer(Kind kind, BlockPos spot, @Nullable String place, @Nullable BlockPos spot2, @Nullable String place2, Bonus bonus, int poi, int poi2) {
    }

    final int region;
    final List<UUID> members = new ArrayList<>();
    final BlockPos origin;
    final long start;
    long end;
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
    // 壊す: 淵の杭 (CircuitStake)
    @Nullable
    UUID stake;
    // 自己テストの人 (FakePlayer は人の一覧にいないので、ここから引く)
    final java.util.Map<UUID, net.minecraft.server.level.ServerPlayer> fakes = new java.util.HashMap<>();
    final Set<Integer> usedPois = new HashSet<>();
    // 次の課題の柱 (空でなければ選んでいるところ) と、いまの課題の柱の色
    final List<Offer> offers = new ArrayList<>();
    Bonus bonus = Bonus.NONE;
    // 重なった呪い (紫の柱を選んだ数。巡り路の終わりまで残る)
    int curses;
    // 土地の課題 (CircuitLand): 掘る所・結晶・焚き火・沖の杭と、その進み。置いたブロック (片づける)。潮待ちの印を持つ人と潮の始まり
    final List<BlockPos> points = new ArrayList<>();
    final List<Integer> pointProgress = new ArrayList<>();
    final List<BlockPos> placed = new ArrayList<>();
    @Nullable
    UUID holder;
    long cycleStart;
    // 早越え (5 分の限りの半分以内) の連なり (2026-11-05 のダンジョンの頁 3-2)。3 で印 +1、5 で印 +2 (そこで数え直し)
    int streak;
    // いまの時 (目的の帯の一行が使う)
    long now;

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

    public boolean choosing() {
        return !offers.isEmpty();
    }

    public Bonus bonus() {
        return bonus;
    }

    public int curses() {
        return curses;
    }

    public int streak() {
        return streak;
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
