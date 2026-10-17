package jp.revati.skyabyss.story;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 1 章の終わりの記録 (オーバーワールドに保存、skyabyss_story)。
// - keys: 天の鍵をそろえた人と、そろえた日 (ワールドの日数)
// - firstBy / firstDay: ワールドで最初に鍵をそろえた人
public final class StoryData extends SavedData {
    private static final String NAME = "skyabyss_story";

    private final Map<UUID, Long> keys = new LinkedHashMap<>();
    private String firstBy = "";
    private long firstDay = -1;

    public static StoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(StoryData::new, StoryData::load), NAME);
    }

    public boolean assembled(UUID player) {
        return keys.containsKey(player);
    }

    public long day(UUID player) {
        return keys.getOrDefault(player, -1L);
    }

    // 記録する。ワールドで最初なら true
    public boolean mark(UUID player, String name, long day) {
        keys.put(player, day);
        boolean first = firstBy.isEmpty();
        if (first) {
            firstBy = name;
            firstDay = day;
        }
        setDirty();
        return first;
    }

    public void clear(UUID player) {
        if (keys.remove(player) != null) {
            setDirty();
        }
    }

    public String firstBy() {
        return firstBy;
    }

    public long firstDay() {
        return firstDay;
    }

    public int count() {
        return keys.size();
    }

    // 自己テスト用: 写し取った記録へ戻す
    public CompoundTag snapshot(HolderLookup.Provider registries) {
        return save(new CompoundTag(), registries);
    }

    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        StoryData r = load(tag, registries);
        keys.clear();
        keys.putAll(r.keys);
        firstBy = r.firstBy;
        firstDay = r.firstDay;
        setDirty();
    }

    private static StoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        StoryData d = new StoryData();
        CompoundTag k = tag.getCompound("keys");
        for (String id : k.getAllKeys()) {
            try {
                d.keys.put(UUID.fromString(id), k.getLong(id));
            } catch (IllegalArgumentException ignored) {
                // 壊れたキーは捨てる
            }
        }
        d.firstBy = tag.getString("first_by");
        d.firstDay = tag.contains("first_day") ? tag.getLong("first_day") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag k = new CompoundTag();
        keys.forEach((id, day) -> k.putLong(id.toString(), day));
        tag.put("keys", k);
        tag.putString("first_by", firstBy);
        tag.putLong("first_day", firstDay);
        return tag;
    }
}
