package jp.revati.skyabyss.prologue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 序章の演出の、プレイヤーごとの記録 (オーバーワールドに保存)。
// 見る者は一人ずつに現れるので、次に現れうる時刻と、これまでに見た回数を人ごとに持つ。
// 死んでも、抜けて入り直しても消えない
public class PrologueData extends SavedData {
    private static final String NAME = "skyabyss_prologue";
    private static final Factory<PrologueData> FACTORY = new Factory<>(PrologueData::new, PrologueData::load);

    public static final class Entry {
        // 次に見る者が現れうるゲーム時刻 (-1 = まだ決めていない)
        public long nextWatcher = -1;
        // 見る者に気づいた回数 (段階を決める)
        public int sightings;
        // 続けて気づかれなかった回数 (気づかれなかったときは早めにもう一度、を何度も繰り返さないため)
        public int misses;
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static PrologueData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Entry entry(UUID player) {
        return players.computeIfAbsent(player, id -> new Entry());
    }

    public void changed() {
        setDirty();
    }

    static PrologueData load(CompoundTag tag, HolderLookup.Provider registries) {
        PrologueData data = new PrologueData();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            if (!p.hasUUID("id")) {
                continue;
            }
            Entry e = data.entry(p.getUUID("id"));
            e.nextWatcher = p.getLong("nextWatcher");
            e.sightings = p.getInt("sightings");
            e.misses = p.getInt("misses");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        players.forEach((id, e) -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("id", id);
            p.putLong("nextWatcher", e.nextWatcher);
            p.putInt("sightings", e.sightings);
            p.putInt("misses", e.misses);
            list.add(p);
        });
        tag.put("players", list);
        return tag;
    }
}
