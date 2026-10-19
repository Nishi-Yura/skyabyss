package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 世界ごとの転生の記録 (順位表と、巨神ごとの最速の元データ。世界に保存して消さない。専用サーバーならそのサーバーの記録)。docs/design/31 の B17・C11・C21。
// 人ごと: 転生の数・人生の最速・一人生の最少の死・死の合計・巨神ごとの自己最速・今入っている巨神の入りの時刻・順位表を断ったか。
// 巨神ごと: 世界の最速 (誰が、何 tick で、いつ)。「いつ」が新しいと飾り板が光る
public final class RebirthStats extends SavedData {
    private static final String NAME = "skyabyss_rebirth_stats";

    public static final class Entry {
        public String name = "?";
        public int rebirths;
        public long order = Long.MAX_VALUE;
        public long lifeStartPlay;
        public int deathsLife;
        public int deathsTotal;
        public long fastestLife = -1;
        public int fewestDeaths = -1;
        public int flawless;
        public boolean hidden;
        public final Map<String, Long> titanBest = new LinkedHashMap<>();
        public final Map<String, Long> entered = new LinkedHashMap<>();
    }

    public record Record(UUID who, String name, long ticks, long at) {
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<String, Record> records = new LinkedHashMap<>();
    private long nextOrder;

    public static RebirthStats get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RebirthStats::new, RebirthStats::load), NAME);
    }

    public Entry entry(UUID id) {
        return entries.computeIfAbsent(id, k -> {
            setDirty();
            return new Entry();
        });
    }

    @Nullable
    public Entry peek(UUID id) {
        return entries.get(id);
    }

    public Map<UUID, Entry> all() {
        return entries;
    }

    public long takeOrder() {
        setDirty();
        return nextOrder++;
    }

    @Nullable
    public Record record(String titan) {
        return records.get(titan);
    }

    public Map<String, Record> records() {
        return records;
    }

    // 世界の最速を更新できたら true
    public boolean offerRecord(String titan, UUID who, String name, long ticks, long now) {
        Record cur = records.get(titan);
        if (cur != null && cur.ticks <= ticks) {
            return false;
        }
        records.put(titan, new Record(who, name, ticks, now));
        setDirty();
        return true;
    }

    public void touch() {
        setDirty();
    }

    // 順位表の元 (断った人は除く)
    public List<RebirthLeaderboard.Row> rows() {
        List<RebirthLeaderboard.Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            Entry x = e.getValue();
            if (!x.hidden) {
                rows.add(new RebirthLeaderboard.Row(e.getKey(), x.name, x.rebirths, x.fastestLife, x.fewestDeaths, x.deathsTotal, x.order));
            }
        }
        return rows;
    }

    // 試験用: 中身を空にして、保存した写しから戻す (世界を使い回しても、前の記録が試験に混ざらない)
    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        RebirthStats s = load(tag, registries);
        entries.clear();
        entries.putAll(s.entries);
        records.clear();
        records.putAll(s.records);
        nextOrder = s.nextOrder;
        setDirty();
    }

    public void clearForTest() {
        entries.clear();
        records.clear();
        setDirty();
    }

    private static RebirthStats load(CompoundTag tag, HolderLookup.Provider registries) {
        RebirthStats s = new RebirthStats();
        s.nextOrder = tag.getLong("nextOrder");
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Entry e = new Entry();
            e.name = c.getString("name");
            e.rebirths = c.getInt("rebirths");
            e.order = c.contains("order") ? c.getLong("order") : Long.MAX_VALUE;
            e.lifeStartPlay = c.getLong("lifeStartPlay");
            e.deathsLife = c.getInt("deathsLife");
            e.deathsTotal = c.getInt("deathsTotal");
            e.fastestLife = c.contains("fastestLife") ? c.getLong("fastestLife") : -1;
            e.fewestDeaths = c.contains("fewestDeaths") ? c.getInt("fewestDeaths") : -1;
            e.flawless = c.getInt("flawless");
            e.hidden = c.getBoolean("hidden");
            CompoundTag best = c.getCompound("titanBest");
            for (String k : best.getAllKeys()) {
                e.titanBest.put(k, best.getLong(k));
            }
            CompoundTag in = c.getCompound("entered");
            for (String k : in.getAllKeys()) {
                e.entered.put(k, in.getLong(k));
            }
            s.entries.put(c.getUUID("id"), e);
        }
        for (Tag t : tag.getList("records", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            s.records.put(c.getString("titan"), new Record(c.getUUID("who"), c.getString("name"), c.getLong("ticks"), c.getLong("at")));
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("nextOrder", nextOrder);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> en : entries.entrySet()) {
            Entry e = en.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("id", en.getKey());
            c.putString("name", e.name);
            c.putInt("rebirths", e.rebirths);
            if (e.order != Long.MAX_VALUE) {
                c.putLong("order", e.order);
            }
            c.putLong("lifeStartPlay", e.lifeStartPlay);
            c.putInt("deathsLife", e.deathsLife);
            c.putInt("deathsTotal", e.deathsTotal);
            if (e.fastestLife >= 0) {
                c.putLong("fastestLife", e.fastestLife);
            }
            if (e.fewestDeaths >= 0) {
                c.putInt("fewestDeaths", e.fewestDeaths);
            }
            c.putInt("flawless", e.flawless);
            c.putBoolean("hidden", e.hidden);
            CompoundTag best = new CompoundTag();
            e.titanBest.forEach(best::putLong);
            c.put("titanBest", best);
            CompoundTag in = new CompoundTag();
            e.entered.forEach(in::putLong);
            c.put("entered", in);
            list.add(c);
        }
        tag.put("entries", list);
        ListTag rec = new ListTag();
        for (Map.Entry<String, Record> en : records.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("titan", en.getKey());
            c.putUUID("who", en.getValue().who);
            c.putString("name", en.getValue().name);
            c.putLong("ticks", en.getValue().ticks);
            c.putLong("at", en.getValue().at);
            rec.add(c);
        }
        tag.put("records", rec);
        return tag;
    }
}
