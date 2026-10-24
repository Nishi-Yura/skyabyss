package jp.revati.skyabyss.trial;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 試練の図鑑・称号・順路の記録 (人ごと。docs/design/29 の 4.2、32 の 6 章。共有しない)。オーバーワールドに保存する。
// 札は初めて「引かれた」とき seen、「済ませた」とき cleared が増える。出会った修飾は "種類:内容" の文字で集める。
// 順路の記録は巨神ごとに、越えた回数と最速 (入口から心臓を止めるまでの tick)
public final class TrialCodex extends SavedData {
    private static final String NAME = "skyabyss_trial_codex";

    public static final class Entry {
        public boolean seen;
        public int cleared;
        public int goals;
        public final Set<String> mods = new LinkedHashSet<>();
    }

    private final Map<UUID, Map<String, Entry>> entries = new HashMap<>();
    private final Map<UUID, Set<String>> titles = new HashMap<>();
    // 順路: 人 → 巨神 → {越えた回数, 最速の tick}
    private final Map<UUID, Map<String, long[]>> routes = new HashMap<>();
    // レイドの部屋の報い (docs/design/32 の 9.4): 越えた部屋の id (人ごと。称号「部屋を全て」の数え)・その日に回した replay の部屋の数 {日, 数}
    private final Map<UUID, Set<String>> raidRooms = new HashMap<>();
    private final Map<UUID, long[]> replayDay = new HashMap<>();

    public static TrialCodex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TrialCodex::new, TrialCodex::load), NAME);
    }

    public Entry of(UUID player, String cell) {
        return entries.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(cell, k -> new Entry());
    }

    // 読むだけ (なければ空の札。作らない)
    public Entry peek(UUID player, String cell) {
        Map<String, Entry> m = entries.get(player);
        Entry e = m == null ? null : m.get(cell);
        return e == null ? new Entry() : e;
    }

    // 引かれた。初めてなら true
    public boolean markSeen(UUID player, String cell) {
        Entry e = of(player, cell);
        if (e.seen) {
            return false;
        }
        e.seen = true;
        setDirty();
        return true;
    }

    // 済ませた。modKeys = 出会った修飾の印。その人にとって初めて済ませたなら true
    public boolean markCleared(UUID player, String cell, Set<String> modKeys, boolean goalDone) {
        Entry e = of(player, cell);
        boolean first = e.cleared == 0;
        e.seen = true;
        e.cleared++;
        if (goalDone) {
            e.goals++;
        }
        e.mods.addAll(modKeys);
        setDirty();
        return first;
    }

    public int totalCleared(UUID player) {
        int n = 0;
        Map<String, Entry> m = entries.get(player);
        if (m != null) {
            for (Entry e : m.values()) {
                if (e.cleared > 0) {
                    n++;
                }
            }
        }
        return n;
    }

    public Set<String> titles(UUID player) {
        return titles.getOrDefault(player, Set.of());
    }

    // 称号を得る。初めてなら true (二度目以降は何もしない)
    public boolean grantTitle(UUID player, String title) {
        if (titles.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(title)) {
            setDirty();
            return true;
        }
        return false;
    }

    // レイドの部屋を越えた (人ごと)。新しく越えた id なら true
    public boolean addRaidRoom(UUID player, String room) {
        if (raidRooms.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(room)) {
            setDirty();
            return true;
        }
        return false;
    }

    public Set<String> raidRooms(UUID player) {
        return raidRooms.getOrDefault(player, Set.of());
    }

    // その日 (日の番号) に replay の部屋の報いを受けた数。日が変われば 0
    public int replayRoomsOn(UUID player, long day) {
        long[] d = replayDay.get(player);
        return d == null || d[0] != day ? 0 : (int) d[1];
    }

    public void countReplayRoom(UUID player, long day) {
        long[] d = replayDay.get(player);
        if (d == null || d[0] != day) {
            replayDay.put(player, new long[] {day, 1});
        } else {
            d[1]++;
        }
        setDirty();
    }

    public int routeClears(UUID player, String titan) {
        long[] r = routes.getOrDefault(player, Map.of()).get(titan);
        return r == null ? 0 : (int) r[0];
    }

    // 最速 (tick)。まだなら 0
    public long routeBest(UUID player, String titan) {
        long[] r = routes.getOrDefault(player, Map.of()).get(titan);
        return r == null ? 0 : r[1];
    }

    // 順路を越えた (心臓を止めた)。ticks = 入口から心臓までの長さ (分からなければ 0)。最速を更新したら true
    public boolean addRoute(UUID player, String titan, long ticks) {
        long[] r = routes.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(titan, k -> new long[2]);
        r[0]++;
        boolean best = ticks > 0 && (r[1] == 0 || ticks < r[1]);
        if (best) {
            r[1] = ticks;
        }
        setDirty();
        return best;
    }

    public void forget(UUID player) {
        boolean any = entries.remove(player) != null;
        any |= titles.remove(player) != null;
        any |= routes.remove(player) != null;
        any |= raidRooms.remove(player) != null;
        any |= replayDay.remove(player) != null;
        if (any) {
            setDirty();
        }
    }

    // 読み込み (保存の中身をそのまま戻す。自己テストが保存と読み直しを確かめる)
    public static TrialCodex load(CompoundTag tag, HolderLookup.Provider registries) {
        TrialCodex data = new TrialCodex();
        CompoundTag all = tag.getCompound("entries");
        for (String pid : all.getAllKeys()) {
            UUID uuid = parse(pid);
            if (uuid == null) {
                continue;
            }
            CompoundTag p = all.getCompound(pid);
            for (String cell : p.getAllKeys()) {
                CompoundTag c = p.getCompound(cell);
                Entry e = data.of(uuid, cell);
                e.seen = c.getBoolean("seen");
                e.cleared = c.getInt("cleared");
                e.goals = c.getInt("goals");
                for (Tag t : c.getList("mods", Tag.TAG_STRING)) {
                    e.mods.add(t.getAsString());
                }
            }
        }
        CompoundTag tl = tag.getCompound("titles");
        for (String pid : tl.getAllKeys()) {
            UUID uuid = parse(pid);
            if (uuid == null) {
                continue;
            }
            Set<String> set = new LinkedHashSet<>();
            for (Tag t : tl.getList(pid, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            data.titles.put(uuid, set);
        }
        CompoundTag rr = tag.getCompound("raidRooms");
        for (String pid : rr.getAllKeys()) {
            UUID uuid = parse(pid);
            if (uuid == null) {
                continue;
            }
            Set<String> set = new LinkedHashSet<>();
            for (Tag t : rr.getList(pid, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            data.raidRooms.put(uuid, set);
        }
        CompoundTag rd = tag.getCompound("replayDay");
        for (String pid : rd.getAllKeys()) {
            UUID uuid = parse(pid);
            if (uuid == null) {
                continue;
            }
            CompoundTag c = rd.getCompound(pid);
            data.replayDay.put(uuid, new long[] {c.getLong("day"), c.getLong("n")});
        }
        CompoundTag rt = tag.getCompound("routes");
        for (String pid : rt.getAllKeys()) {
            UUID uuid = parse(pid);
            if (uuid == null) {
                continue;
            }
            CompoundTag p = rt.getCompound(pid);
            Map<String, long[]> m = new HashMap<>();
            for (String titan : p.getAllKeys()) {
                CompoundTag c = p.getCompound(titan);
                m.put(titan, new long[] {c.getLong("clears"), c.getLong("best")});
            }
            data.routes.put(uuid, m);
        }
        return data;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        entries.forEach((uuid, m) -> {
            CompoundTag p = new CompoundTag();
            m.forEach((cell, e) -> {
                CompoundTag c = new CompoundTag();
                c.putBoolean("seen", e.seen);
                c.putInt("cleared", e.cleared);
                c.putInt("goals", e.goals);
                ListTag mods = new ListTag();
                e.mods.forEach(s -> mods.add(StringTag.valueOf(s)));
                c.put("mods", mods);
                p.put(cell, c);
            });
            all.put(uuid.toString(), p);
        });
        tag.put("entries", all);
        CompoundTag tl = new CompoundTag();
        titles.forEach((uuid, set) -> {
            ListTag l = new ListTag();
            set.forEach(s -> l.add(StringTag.valueOf(s)));
            tl.put(uuid.toString(), l);
        });
        tag.put("titles", tl);
        CompoundTag rt = new CompoundTag();
        routes.forEach((uuid, m) -> {
            CompoundTag p = new CompoundTag();
            m.forEach((titan, r) -> {
                CompoundTag c = new CompoundTag();
                c.putLong("clears", r[0]);
                c.putLong("best", r[1]);
                p.put(titan, c);
            });
            rt.put(uuid.toString(), p);
        });
        tag.put("routes", rt);
        CompoundTag rr = new CompoundTag();
        raidRooms.forEach((uuid, set) -> {
            ListTag l = new ListTag();
            set.forEach(s -> l.add(StringTag.valueOf(s)));
            rr.put(uuid.toString(), l);
        });
        tag.put("raidRooms", rr);
        CompoundTag rd = new CompoundTag();
        replayDay.forEach((uuid, d) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("day", d[0]);
            c.putLong("n", d[1]);
            rd.put(uuid.toString(), c);
        });
        tag.put("replayDay", rd);
        return tag;
    }
}
