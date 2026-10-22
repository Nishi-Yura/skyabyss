package jp.revati.skyabyss.trial.raid;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.TrialCatalog;

// レイドの部屋の登録 (docs/design/32 の 7 章)。巨神ごとに歩 (WALK)・智 (WIT)・闘 (FIGHT) の部屋を一つずつ登録する。
// 三つそろった巨神は「レイドの順路」になり、順路の段 1〜3 は引きではなく、この決まった三つになる (毎回同じ部屋。中の配置・敵・順は ctx.rng() で少し変わる)。
//   RaidRooms.register(Titans.STONE_WHALE, Trial.Slot.WALK, "WR1", WhaleCarry::new);
//   id は試練の id (最大 16 文字。巨神の頭文字 + R + 番号が目安: WR1 WR2 WR3 / TR1.. / SR1.. / ER1..)。
//   名前の lang は trial.skyabyss.cell.<id>、説明は trial.skyabyss.cell.<id>.desc、枠 (歩・智・闘) は slot から
// 登録は RaidBootstrap.registerAll() に一行足す (最初に使われるとき一度だけ呼ばれる)
public final class RaidRooms {
    private RaidRooms() {
    }

    private record Entry(String id, Supplier<RaidRoom> factory) {
    }

    private static final Map<String, Map<Slot, Entry>> BY_TITAN = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static boolean booted;

    private static void ensure() {
        if (!booted) {
            booted = true;
            RaidBootstrap.registerAll();
        }
    }

    // 登録する。同じ巨神・枠は上書き。試練の定義 (TrialCatalog) にも原型として入る
    public static void register(String titan, Slot slot, String id, Supplier<RaidRoom> factory) {
        Entry e = new Entry(id, factory);
        BY_TITAN.computeIfAbsent(titan, k -> new EnumMap<>(Slot.class)).put(slot, e);
        BY_ID.put(id, e);
        TrialCatalog.addRaid(new Trial.Def(id, titan, slot, switch (slot) {
            case WALK -> Trial.Kind.CARRY;
            case WIT -> Trial.Kind.SOLVE;
            case FIGHT -> Trial.Kind.FIGHT;
        }, 1, false, true, Trial.Space.EMPTY));
    }

    // 三つそろった巨神か (レイドの順路で動かす)
    public static boolean active(String titan) {
        ensure();
        Map<Slot, Entry> m = BY_TITAN.get(titan);
        return m != null && m.size() == Slot.values().length;
    }

    // 枠の部屋の id (なければ null)
    @Nullable
    public static String idOf(String titan, Slot slot) {
        ensure();
        Map<Slot, Entry> m = BY_TITAN.get(titan);
        Entry e = m == null ? null : m.get(slot);
        return e == null ? null : e.id();
    }

    public static boolean isRaid(String id) {
        ensure();
        return BY_ID.containsKey(id);
    }

    // 新しい部屋のインスタンス (一回の挑戦ごと)
    @Nullable
    public static RaidRoom create(String id) {
        ensure();
        Entry e = BY_ID.get(id);
        return e == null ? null : e.factory().get();
    }

    // 自己テスト用
    public static void ensureLoaded() {
        ensure();
    }
}
