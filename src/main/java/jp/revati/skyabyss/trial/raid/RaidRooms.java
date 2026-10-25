package jp.revati.skyabyss.trial.raid;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static final Map<String, Map<Slot, List<Entry>>> BY_TITAN = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static boolean booted;

    private static void ensure() {
        if (!booted) {
            booted = true;
            RaidBootstrap.registerAll();
        }
    }

    // 登録する。同じ巨神・枠に複数登録すると、周ごとに一つ引く (drawOf。枠ごと 2 案)。同じ id は上書き。試練の定義 (TrialCatalog) にも原型として入る
    public static void register(String titan, Slot slot, String id, Supplier<RaidRoom> raw) {
        // 闘の部屋は、波のあと中ボスが部屋に現れる (docs/design/32 の 9.5、00 の 277)
        Supplier<RaidRoom> factory = slot == Slot.FIGHT && jp.revati.skyabyss.trial.raid.mid.MidRooms.idOf(titan) != null
                ? () -> new jp.revati.skyabyss.trial.raid.mid.FightBossRoom(titan, raw.get()) : raw;
        Entry e = new Entry(id, factory);
        List<Entry> list = BY_TITAN.computeIfAbsent(titan, k -> new EnumMap<>(Slot.class)).computeIfAbsent(slot, k -> new ArrayList<>());
        list.removeIf(x -> x.id().equals(id));
        list.add(e);
        BY_ID.put(id, e);
        TrialCatalog.addRaid(new Trial.Def(id, titan, slot, switch (slot) {
            case WALK -> Trial.Kind.CARRY;
            case WIT -> Trial.Kind.SOLVE;
            case FIGHT -> Trial.Kind.FIGHT;
        }, 1, false, true, Trial.Space.EMPTY));
    }

    // 登録したすべての部屋 (id → 巨神。登録の順)。自己テスト raid solo が一つずつ一人で越える
    public static Map<String, String> all() {
        ensure();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Slot, List<Entry>>> t : BY_TITAN.entrySet()) {
            for (List<Entry> list : t.getValue().values()) {
                for (Entry e : list) {
                    out.put(e.id(), t.getKey());
                }
            }
        }
        return out;
    }

    // 三つそろった巨神か (レイドの順路で動かす)
    public static boolean active(String titan) {
        ensure();
        Map<Slot, List<Entry>> m = BY_TITAN.get(titan);
        return m != null && m.size() == Slot.values().length;
    }

    // 枠の部屋の id (なければ null)
    @Nullable
    public static String idOf(String titan, Slot slot) {
        ensure();
        Map<Slot, List<Entry>> m = BY_TITAN.get(titan);
        List<Entry> l = m == null ? null : m.get(slot);
        return l == null || l.isEmpty() ? null : l.get(0).id();
    }

    // 枠に登録した部屋の id (登録順。一つか二つ)
    public static List<String> idsOf(String titan, Slot slot) {
        ensure();
        Map<Slot, List<Entry>> m = BY_TITAN.get(titan);
        List<Entry> l = m == null ? null : m.get(slot);
        List<String> out = new ArrayList<>();
        if (l != null) {
            for (Entry e : l) {
                out.add(e.id());
            }
        }
        return out;
    }

    // 一回の挑戦 (周) の部屋を枠ごとに一つ引く。seed は周ごとに決まった値 (同じ周なら同じ部屋。やり直し・再開でも変わらない。引きは記録される)
    @Nullable
    public static String drawOf(String titan, Slot slot, long seed) {
        List<String> ids = idsOf(titan, slot);
        if (ids.isEmpty()) {
            return null;
        }
        long z = seed ^ (slot.ordinal() * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ids.get((int) Math.floorMod(z, (long) ids.size()));
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
