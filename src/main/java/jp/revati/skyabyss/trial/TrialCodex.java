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

    public int totalGoals(UUID player) {
        int n = 0;
        Map<String, Entry> m = entries.get(player);
        if (m != null) {
            for (Entry e : m.values()) {
                n += e.goals;
            }
        }
        return n;
    }

    public Set<String> titles(UUID player) {
        return titles.getOrDefault(player, Set.of());
    }

    public boolean addTitle(UUID player, String title) {
        if (titles.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(title)) {
            setDirty();
            return true;
        }
        return false;
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
        if (any) {
            setDirty();
        }
    }

    private static TrialCodex load(CompoundTag tag, HolderLookup.Provider registries) {
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
        return tag;
    }
}
