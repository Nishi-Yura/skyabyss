package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Slot;
import net.minecraft.server.level.ServerLevel;

// グループごとの引きの記録 (docs/design/29 の 5.1「グループの記録に keep.draw・keep.pool・keep.clears を足す」)。
// BackGroupData.Record の自由な欄に "keep.trial.*" で書く。"keep." の欄なので、試練のリセットでは消えず、
// パーティを組む・抜けるときは記録ごと写る。巨神ごとに背中のディメンションが別なので、巨神の名前は鍵に入れない。
public final class TrialData {
    private TrialData() {
    }

    public static final String DONE = "keep.trial.done";
    public static final String DRAW = "keep.trial.draw";
    public static final String SEEN = "keep.trial.seen.";
    public static final String LAST = "keep.trial.last.";
    // いまの周で済ませた試練 (コンマ区切り)
    public static final String CLEARED = "keep.trial.cleared";

    // 世界の種を使うところの上書き (自己テスト用)。Long.MIN_VALUE なら世界の種
    public static long seedOverride = Long.MIN_VALUE;
    // 週の上書き (自己テスト用)。Long.MIN_VALUE なら実時間の週
    public static long weekOverride = Long.MIN_VALUE;

    // いまの周 (1 から)。心臓を止めた数 + 1
    public static int cycle(BackGroupData.Record r) {
        return Math.max(0, r.getInt(DONE)) + 1;
    }

    public static long worldSeed(ServerLevel back) {
        return seedOverride != Long.MIN_VALUE ? seedOverride : back.getServer().overworld().getSeed();
    }

    // 巨神の名前 (背中のディメンションから)。背中でなければ null
    @Nullable
    public static String titanOf(ServerLevel back) {
        return Titans.titanOfBack(back.dimension());
    }

    public static DrawMemory memory(BackGroupData.Record r) {
        TrialDraw.Memory mem = new TrialDraw.Memory();
        for (Slot s : Slot.values()) {
            String seen = r.getString(SEEN + s.id);
            if (!seen.isEmpty()) {
                mem.seen(s).addAll(Arrays.asList(seen.split(",")));
            }
            String last = r.getString(LAST + s.id);
            if (!last.isEmpty()) {
                mem.last.put(s, last);
            }
        }
        return new DrawMemory(mem);
    }

    // TrialDraw.Memory の包み (保存を呼ぶ側に隠すため)
    public record DrawMemory(TrialDraw.Memory mem) {
        public void save(BackGroupData.Record r) {
            for (Slot s : Slot.values()) {
                Set<String> seen = mem.seen.get(s);
                if (seen == null || seen.isEmpty()) {
                    r.remove(SEEN + s.id);
                } else {
                    r.putString(SEEN + s.id, String.join(",", seen));
                }
                String last = mem.last.get(s);
                if (last == null) {
                    r.remove(LAST + s.id);
                } else {
                    r.putString(LAST + s.id, last);
                }
            }
        }
    }

    // いまの周の引き。なければ (周が変わっていれば) 引いて保存する
    public static TrialDraw.Draw current(ServerLevel back, UUID group) {
        String titan = titanOf(back);
        BackGroupData.Record r = GroupProgress.of(back, group);
        return current(titan, r, group, worldSeed(back));
    }

    public static TrialDraw.Draw current(String titan, BackGroupData.Record r, UUID group, long worldSeed) {
        return jp.revati.skyabyss.abyss.Abyss.on(r) ? TrialDraw.abyssify(currentBase(titan, r, group, worldSeed), titan, worldSeed, group)
                : currentBase(titan, r, group, worldSeed);
    }

    // 深淵を重ねる前の、保存されている引き (深淵は表示と実行のたびに重ねる。保存はしない: つまみを戻せば元の引きに戻る)
    private static TrialDraw.Draw currentBase(String titan, BackGroupData.Record r, UUID group, long worldSeed) {
        int cycle = cycle(r);
        TrialDraw.Draw stored = r.has(DRAW) ? TrialDraw.Draw.decode(r.getString(DRAW)) : null;
        if (stored != null && stored.cycle() == cycle && matches(stored, titan)) {
            return stored;
        }
        DrawMemory mem = memory(r);
        TrialDraw.Draw draw = TrialDraw.draw(titan, cycle, worldSeed, group, mem.mem());
        mem.save(r);
        r.putString(DRAW, draw.encode());
        r.remove(CLEARED);
        return draw;
    }

    private static boolean matches(TrialDraw.Draw draw, String titan) {
        for (TrialDraw.Pick p : draw.picks()) {
            Def d = p.def();
            if (d == null || !d.titan().equals(titan)) {
                return false;
            }
        }
        return true;
    }

    // 心臓を止めた: 周を一つ進め、次の乗り込みで新しく引く
    public static void onHeartStopped(BackGroupData.Record r) {
        r.putInt(DONE, Math.max(0, r.getInt(DONE)) + 1);
        r.remove(DRAW);
        r.remove(CLEARED);
        TrialRoute.reset(r);
    }

    public static Set<String> clearedIds(BackGroupData.Record r) {
        String s = r.getString(CLEARED);
        return s.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(s.split(",")));
    }

    public static boolean isCleared(BackGroupData.Record r, String cell) {
        return clearedIds(r).contains(cell);
    }

    // いまの周に済んだ試練として記す。初めてなら true
    public static boolean markCleared(BackGroupData.Record r, String cell) {
        Set<String> set = clearedIds(r);
        if (!set.add(cell)) {
            return false;
        }
        r.putString(CLEARED, String.join(",", set));
        return true;
    }

    // ---------------------------------------------------------------- 今週の試練 (docs/design/29 の 4.4)
    public static long week() {
        if (weekOverride != Long.MIN_VALUE) {
            return weekOverride;
        }
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay() / 7;
    }

    // 巨神ごとの今週の試練。作ってある試練から種 (世界の種 + 週 + 巨神) で一つ
    @Nullable
    public static Def weekly(String titan, long worldSeed, long week) {
        List<Def> all = TrialCatalog.of(titan);
        if (all.isEmpty()) {
            return null;
        }
        long h = TrialDraw.seed(worldSeed, new UUID(week, 0x77EEC1), titan, 0);
        return all.get(new Random(h).nextInt(all.size()));
    }

    public static List<Def> weeklyAll(long worldSeed, long week) {
        List<Def> out = new ArrayList<>();
        for (String t : TrialCatalog.titans()) {
            Def d = weekly(t, worldSeed, week);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }
}
