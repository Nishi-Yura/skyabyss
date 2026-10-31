package jp.revati.skyabyss.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// あいだの塔の記録の板 (docs/design/54 の 2.6)。地上に保存:
//   best   人数ごと (1〜12) の最速: tick・隊の名前・日
//   guilds 今週 (TowerWagers.week) の組合ごとの越えた回数。週が替わったら空にする
public class TowerRecords extends SavedData {
    private static final String NAME = "skyabyss_tower_records";

    public record Best(long ticks, List<String> names, String day) {
    }

    final Map<Integer, Best> best = new HashMap<>();
    final Map<String, Integer> guilds = new HashMap<>();
    final Map<String, String> guildNames = new HashMap<>();
    String week = "";

    public static TowerRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TowerRecords::new, TowerRecords::load), NAME);
    }

    // 越えた隊を書く。人数の最速を更新したら true
    public boolean record(int size, long ticks, List<String> names, List<String[]> guildsOfRun) {
        rollWeek();
        boolean faster = false;
        Best b = best.get(size);
        if (b == null || ticks < b.ticks()) {
            best.put(size, new Best(ticks, List.copyOf(names), Towers.today().toString()));
            faster = true;
        }
        for (String[] g : guildsOfRun) {
            guilds.merge(g[0], 1, Integer::sum);
            guildNames.put(g[0], g[1]);
        }
        setDirty();
        return faster;
    }

    void rollWeek() {
        String w = TowerWagers.week().toString();
        if (!w.equals(week)) {
            week = w;
            guilds.clear();
            guildNames.clear();
            setDirty();
        }
    }

    public Best best(int size) {
        return best.get(size);
    }

    // 今週の組合の順位 (回数の多い順。上から n)
    public List<Map.Entry<String, Integer>> ranking(int n) {
        rollWeek();
        List<Map.Entry<String, Integer>> out = new ArrayList<>(guilds.entrySet());
        out.sort((a, b) -> b.getValue() - a.getValue());
        return out.subList(0, Math.min(n, out.size()));
    }

    public String guildName(String id) {
        return guildNames.getOrDefault(id, id);
    }

    private static TowerRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        TowerRecords r = new TowerRecords();
        CompoundTag bt = tag.getCompound("best");
        for (String k : bt.getAllKeys()) {
            CompoundTag e = bt.getCompound(k);
            List<String> names = new ArrayList<>();
            for (Tag t : e.getList("names", Tag.TAG_STRING)) {
                names.add(t.getAsString());
            }
            try {
                r.best.put(Integer.parseInt(k), new Best(e.getLong("ticks"), names, e.getString("day")));
            } catch (NumberFormatException ignored) {
                // 壊れた鍵は読まない
            }
        }
        CompoundTag gt = tag.getCompound("guilds");
        for (String k : gt.getAllKeys()) {
            r.guilds.put(k, gt.getCompound(k).getInt("n"));
            r.guildNames.put(k, gt.getCompound(k).getString("name"));
        }
        r.week = tag.getString("week");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag bt = new CompoundTag();
        for (Map.Entry<Integer, Best> e : best.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("ticks", e.getValue().ticks());
            ListTag names = new ListTag();
            e.getValue().names().forEach(n -> names.add(StringTag.valueOf(n)));
            c.put("names", names);
            c.putString("day", e.getValue().day());
            bt.put(String.valueOf(e.getKey()), c);
        }
        tag.put("best", bt);
        CompoundTag gt = new CompoundTag();
        for (Map.Entry<String, Integer> e : guilds.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("n", e.getValue());
            c.putString("name", guildNames.getOrDefault(e.getKey(), e.getKey()));
            gt.put(e.getKey(), c);
        }
        tag.put("guilds", gt);
        tag.putString("week", week);
        return tag;
    }
}
