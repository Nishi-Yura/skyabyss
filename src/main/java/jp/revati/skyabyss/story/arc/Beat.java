package jp.revati.skyabyss.story.arc;

import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import jp.revati.skyabyss.story.Chapters;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 予兆の拍一つ (定義。変えない)。章ごとに 3 つ (order 1〜3)。使い方は Foreshadow の頭のコメント。
//   chapter  : この章を遊んでいる人にだけ起きる (先の章の人にも、前の章の人にも起きない)
//   order    : 1 痕跡 → 2 間接 → 3 一部。条件が合えば順番どおりに調べる。ただし前の拍が済んでいなくても後の拍は止まらない
//   id       : "<章の ID>/<名前>"。人ごとの保存の鍵 (変えると起きたことが忘れられる)
//   条件     : 時刻 (Time)、距離 (anchor からの min〜max)、間隔 (cooldown。直前に何かの拍が起きてからの tick)、次元、自由な条件 (when)。
//              全部合ったときだけ。章 (chapter) の条件は暗黙
//   action   : 起こす中身。実際に起こしたら true、条件が足りなくて見送るなら false (その場合は記録せず、次の調べでまた試す)
public final class Beat {
    // 時刻 (dayTime % 24000)。DAWN 23000〜1000、DAY 1000〜11500、DUSK 11500〜13000、NIGHT 13000〜23000
    public enum Time {
        ANY, DAY, DAWN, DUSK, NIGHT;

        public boolean matches(long dayTime) {
            long t = Math.floorMod(dayTime, 24000L);
            return switch (this) {
                case ANY -> true;
                case DAWN -> t >= 23000 || t < 1000;
                case DAY -> t >= 1000 && t < 11500;
                case DUSK -> t >= 11500 && t < 13000;
                case NIGHT -> t >= 13000 && t < 23000;
            };
        }
    }

    // 拍を起こす中身。true = 起こした (記録する)
    @FunctionalInterface
    public interface Action {
        boolean fire(ServerPlayer player, Beat beat);
    }

    private final Chapters chapter;
    private final int order;
    private final String name;
    private final Action action;
    private Time time = Time.ANY;
    private Function<ServerPlayer, Vec3> anchor;
    private double minDistance;
    private double maxDistance = Double.MAX_VALUE;
    private long cooldown = Foreshadow.DEFAULT_COOLDOWN;
    private net.minecraft.resources.ResourceKey<Level> dimension = Level.OVERWORLD;
    private Predicate<ServerPlayer> when = p -> true;

    private Beat(Chapters chapter, int order, String name, Action action) {
        this.chapter = chapter;
        this.order = order;
        this.name = name;
        this.action = action;
    }

    // chapter の order (1〜3) 番目の拍。name は章の中で一意 (英小文字と _)
    public static Beat of(Chapters chapter, int order, String name, Action action) {
        if (chapter == null || !chapter.titanChapter()) {
            throw new IllegalArgumentException("foreshadowing beats belong to chapters 1-4, not " + chapter);
        }
        if (order < 1 || order > Foreshadow.BEATS_PER_CHAPTER) {
            throw new IllegalArgumentException("beat order must be 1.." + Foreshadow.BEATS_PER_CHAPTER + ": " + order);
        }
        if (name == null || !name.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("beat name must be [a-z0-9_]+: " + name);
        }
        return new Beat(chapter, order, name, action);
    }

    public Beat time(Time time) {
        this.time = time;
        return this;
    }

    // anchor (例: 拠点、巨神の方角の点) からの距離が min 以上 max 以下。anchor が null を返す間は条件が合わない
    public Beat distance(Function<ServerPlayer, Vec3> anchor, double min, double max) {
        this.anchor = anchor;
        this.minDistance = min;
        this.maxDistance = max;
        return this;
    }

    // 直前に何かの拍が起きてから ticks 以上空ける (既定 Foreshadow.DEFAULT_COOLDOWN = 5 分)。0 なら空けない
    public Beat cooldown(long ticks) {
        this.cooldown = Math.max(0, ticks);
        return this;
    }

    // 起きる次元 (既定はオーバーワールド)。null なら次元を問わない
    public Beat dimension(@Nullable net.minecraft.resources.ResourceKey<Level> dimension) {
        this.dimension = dimension;
        return this;
    }

    // 自由な条件 (天気、場所の群系、クエストの段、など)
    public Beat when(Predicate<ServerPlayer> when) {
        this.when = when;
        return this;
    }

    public Chapters chapter() {
        return chapter;
    }

    public int order() {
        return order;
    }

    public String id() {
        return chapter.id() + "/" + name;
    }

    public String name() {
        return name;
    }

    public Time time() {
        return time;
    }

    public long cooldown() {
        return cooldown;
    }

    Action action() {
        return action;
    }

    // 条件 (章を除く) が合うか。lastBeatAt = その人の直前の拍の時刻 (なければ -1)
    boolean ready(ServerPlayer player, long gameTime, long dayTime, long lastBeatAt) {
        if (lastBeatAt >= 0 && gameTime - lastBeatAt < cooldown) {
            return false;
        }
        if (!time.matches(dayTime)) {
            return false;
        }
        if (dimension != null && player.level().dimension() != dimension) {
            return false;
        }
        if (anchor != null) {
            Vec3 at = anchor.apply(player);
            if (at == null) {
                return false;
            }
            double d = player.position().distanceTo(at);
            if (d < minDistance || d > maxDistance) {
                return false;
            }
        }
        return when.test(player);
    }
}
