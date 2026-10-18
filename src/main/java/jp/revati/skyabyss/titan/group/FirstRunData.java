package jp.revati.skyabyss.titan.group;

import java.util.HashMap;
import java.util.HashSet;
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

// 人ごとの「一度きり」の記録 (オーバーワールドに一つ。死んでも・パーティが変わっても残る)。今は追憶の宝を受け取った印だけ
public final class FirstRunData extends SavedData {
    private static final String NAME = "skyabyss_first_run";
    private static final Factory<FirstRunData> FACTORY = new Factory<>(FirstRunData::new, FirstRunData::load);

    private final Map<UUID, Set<String>> keepsakes = new HashMap<>();

    public static FirstRunData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean hasKeepsake(UUID player, String id) {
        Set<String> set = keepsakes.get(player);
        return set != null && set.contains(id);
    }

    // 受け取った印をつける。初めてなら true
    public boolean markKeepsake(UUID player, String id) {
        boolean added = keepsakes.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        if (added) {
            setDirty();
        }
        return added;
    }

    // 自己テスト・コマンド用: 印を消す
    public void forgetKeepsake(UUID player, String id) {
        Set<String> set = keepsakes.get(player);
        if (set != null && set.remove(id)) {
            setDirty();
        }
    }

    private static FirstRunData load(CompoundTag tag, HolderLookup.Provider registries) {
        FirstRunData data = new FirstRunData();
        CompoundTag all = tag.getCompound("Keepsakes");
        for (String key : all.getAllKeys()) {
            Set<String> set = new HashSet<>();
            for (Tag t : all.getList(key, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            data.keepsakes.put(UUID.fromString(key), set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        keepsakes.forEach((player, set) -> {
            ListTag list = new ListTag();
            set.forEach(s -> list.add(StringTag.valueOf(s)));
            all.put(player.toString(), list);
        });
        tag.put("Keepsakes", all);
        return tag;
    }
}
