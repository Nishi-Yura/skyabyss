package jp.revati.skyabyss.loot;

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

// 巨神の記憶: プレイヤー × ダンジョンごとの周回の記録。
// 天井のカウンタ、手にした専用伝説、初回クリアの有無を持つ。
// 地上 (オーバーワールド) に保存するので、背中の試練のリセットでも、月のループでも消えない
public class TitanMemoryData extends SavedData {
    private static final String NAME = "skyabyss_titan_memory";

    public static final class Record {
        // 天井のカウンタ (ボスを倒すたびに +1、未所持の専用伝説を手にすると 0)
        public int memory;
        // これまでにボスを倒した回数 (記録用)
        public int clears;
        // 手にしたことのある専用伝説 (アイテムID)
        public final Set<String> owned = new LinkedHashSet<>();
        // 初回クリアを済ませたか / 初回の選択報酬をまだ受け取っていないか
        public boolean firstCleared;
        public boolean choicePending;

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("memory", memory);
            tag.putInt("clears", clears);
            ListTag list = new ListTag();
            for (String id : owned) {
                list.add(StringTag.valueOf(id));
            }
            tag.put("owned", list);
            tag.putBoolean("firstCleared", firstCleared);
            tag.putBoolean("choicePending", choicePending);
            return tag;
        }

        static Record load(CompoundTag tag) {
            Record r = new Record();
            r.memory = tag.getInt("memory");
            r.clears = tag.getInt("clears");
            for (Tag t : tag.getList("owned", Tag.TAG_STRING)) {
                r.owned.add(t.getAsString());
            }
            r.firstCleared = tag.getBoolean("firstCleared");
            r.choicePending = tag.getBoolean("choicePending");
            return r;
        }
    }

    private final Map<UUID, Map<String, Record>> players = new HashMap<>();

    public static TitanMemoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TitanMemoryData::new, TitanMemoryData::load), NAME);
    }

    public Record record(UUID player, String dungeon) {
        setDirty();
        return players.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(dungeon, k -> new Record());
    }

    private static TitanMemoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        TitanMemoryData data = new TitanMemoryData();
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            Map<String, Record> dungeons = new HashMap<>();
            CompoundTag d = all.getCompound(key);
            for (String dungeon : d.getAllKeys()) {
                dungeons.put(dungeon, Record.load(d.getCompound(dungeon)));
            }
            data.players.put(UUID.fromString(key), dungeons);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        players.forEach((uuid, dungeons) -> {
            CompoundTag d = new CompoundTag();
            dungeons.forEach((dungeon, record) -> d.put(dungeon, record.save()));
            all.put(uuid.toString(), d);
        });
        tag.put("players", all);
        return tag;
    }
}
