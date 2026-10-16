package jp.revati.skyabyss.progress;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

// 物語の進みの記録 (オーバーワールドに保存)。
// - world: ワールドの誰かが心臓を止めた巨神 (世界の進み)。最初に止めた人の名前と日も残す (ワールドの記録の画面に使う)
// - personal: 人ごとに、心臓を止めた巨神 (個人の進み)。巨神の記憶 (TitanMemoryData) の撃破の記録と同じ意味で、
//   こちらは「撃破の報酬より先に」書く (上限が上がってから経験値を渡すため) のと、管理者が与えた分を持つ
public final class ProgressData extends SavedData {
    private static final String NAME = "skyabyss_progress";

    public final Map<String, String> worldFirstBy = new LinkedHashMap<>();
    public final Map<String, Long> worldFirstDay = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> personal = new HashMap<>();

    public static ProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgressData::new, ProgressData::load), NAME);
    }

    public boolean worldStopped(String titan) {
        return worldFirstBy.containsKey(titan);
    }

    // ワールドの記録に足す。初めてなら true
    public boolean markWorld(String titan, String by, long day) {
        if (worldFirstBy.containsKey(titan)) {
            return false;
        }
        worldFirstBy.put(titan, by);
        worldFirstDay.put(titan, day);
        setDirty();
        return true;
    }

    public void clearWorld(String titan) {
        worldFirstBy.remove(titan);
        worldFirstDay.remove(titan);
        setDirty();
    }

    public boolean personalStopped(UUID player, String titan) {
        Set<String> s = personal.get(player);
        return s != null && s.contains(titan);
    }

    public boolean markPersonal(UUID player, String titan) {
        boolean added = personal.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(titan);
        if (added) {
            setDirty();
        }
        return added;
    }

    public void clearPersonal(UUID player, String titan) {
        Set<String> s = personal.get(player);
        if (s != null && s.remove(titan)) {
            setDirty();
        }
    }

    // 自己テスト用: 写し取った記録へ戻す
    public void restore(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressData r = load(tag, registries);
        worldFirstBy.clear();
        worldFirstBy.putAll(r.worldFirstBy);
        worldFirstDay.clear();
        worldFirstDay.putAll(r.worldFirstDay);
        personal.clear();
        personal.putAll(r.personal);
        setDirty();
    }

    private static ProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressData data = new ProgressData();
        CompoundTag world = tag.getCompound("world");
        for (String titan : world.getAllKeys()) {
            CompoundTag t = world.getCompound(titan);
            data.worldFirstBy.put(titan, t.getString("by"));
            data.worldFirstDay.put(titan, t.getLong("day"));
        }
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            Set<String> set = new LinkedHashSet<>();
            for (Tag t : players.getList(key, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            try {
                data.personal.put(UUID.fromString(key), set);
            } catch (IllegalArgumentException ignored) {
                // 壊れたキーは捨てる
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag world = new CompoundTag();
        worldFirstBy.forEach((titan, by) -> {
            CompoundTag t = new CompoundTag();
            t.putString("by", by);
            t.putLong("day", worldFirstDay.getOrDefault(titan, 0L));
            world.put(titan, t);
        });
        tag.put("world", world);
        CompoundTag players = new CompoundTag();
        personal.forEach((id, set) -> {
            ListTag list = new ListTag();
            set.forEach(s -> list.add(StringTag.valueOf(s)));
            players.put(id.toString(), list);
        });
        tag.put("players", players);
        return tag;
    }
}
