package jp.revati.skyabyss.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

// 巨神の記憶: プレイヤー × ダンジョンごとの周回の記録。
// 天井のカウンタ、手にした装備 (図鑑)、初回クリアの有無を持つ。
// あわせて、受け取れなかった報酬 (倒れていた・持ち物がいっぱい) をプレイヤーごとに預かる。
// 地上 (オーバーワールド) に保存するので、背中の試練のリセットでも、月のループでも消えない
public class TitanMemoryData extends SavedData {
    private static final String NAME = "skyabyss_titan_memory";

    public static final class Record {
        // 天井のカウンタ (心臓を倒すたびに +1、図鑑にない装備を手にすると 0)
        public int memory;
        // これまでに心臓を倒した回数
        public int clears;
        // 手にしたことのある図鑑の装備 (アイテムID。専用の逸品もここに入る)
        public final Set<String> owned = new LinkedHashSet<>();
        // 天の鍵の欠片を受け取ったか (一人一つ)
        public boolean fragment;
        // 初回クリアを済ませたか / 初回の選択報酬をまだ受け取っていないか
        public boolean firstCleared;
        public boolean choicePending;
        // 共鳴の欠片での交換: 最後に交換したときの撃破数 (心臓を倒すごとに一度。0 = まだ一度もしていない) と、交換した回数
        public int lastExchangeClears;
        public int exchanges;

        public CompoundTag save() {
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
            tag.putBoolean("fragment", fragment);
            tag.putInt("lastExchangeClears", lastExchangeClears);
            tag.putInt("exchanges", exchanges);
            return tag;
        }

        public static Record load(CompoundTag tag) {
            Record r = new Record();
            r.memory = tag.getInt("memory");
            r.clears = tag.getInt("clears");
            for (Tag t : tag.getList("owned", Tag.TAG_STRING)) {
                r.owned.add(t.getAsString());
            }
            r.firstCleared = tag.getBoolean("firstCleared");
            r.choicePending = tag.getBoolean("choicePending");
            r.fragment = tag.getBoolean("fragment");
            // 第1段階のセーブにはない項目 (なければ 0 = まだ交換していない)
            r.lastExchangeClears = tag.getInt("lastExchangeClears");
            r.exchanges = tag.getInt("exchanges");
            return r;
        }
    }

    // 預かっている報酬 (アイテムと経験値)。目を覚ましたとき・入ったとき・/titanmemory claim で渡す
    public static final class Pending {
        public final List<ItemStack> items = new ArrayList<>();
        public int xp;

        public boolean isEmpty() {
            return items.isEmpty() && xp <= 0;
        }
    }

    private final Map<UUID, Map<String, Record>> players = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    public static TitanMemoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TitanMemoryData::new, TitanMemoryData::load), NAME);
    }

    public Record record(UUID player, String dungeon) {
        setDirty();
        return players.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(dungeon, k -> new Record());
    }

    // 見るだけ (なければ null。保存の印を付けない。炉の画面が毎 tick 見るため)
    @org.jetbrains.annotations.Nullable
    public Record peek(UUID player, String dungeon) {
        Map<String, Record> dungeons = players.get(player);
        return dungeons == null ? null : dungeons.get(dungeon);
    }

    public Pending pending(UUID player) {
        setDirty();
        return pending.computeIfAbsent(player, k -> new Pending());
    }

    public boolean hasPending(UUID player) {
        Pending p = pending.get(player);
        return p != null && !p.isEmpty();
    }

    // 預かり分をすべて取り出す (取り出したあとは空)
    public Pending takePending(UUID player) {
        setDirty();
        Pending p = pending.remove(player);
        return p == null ? new Pending() : p;
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
        CompoundTag held = tag.getCompound("pending");
        for (String key : held.getAllKeys()) {
            CompoundTag p = held.getCompound(key);
            Pending pending = new Pending();
            pending.xp = p.getInt("xp");
            for (Tag t : p.getList("items", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, t).ifPresent(pending.items::add);
            }
            if (!pending.isEmpty()) {
                data.pending.put(UUID.fromString(key), pending);
            }
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
        CompoundTag held = new CompoundTag();
        pending.forEach((uuid, p) -> {
            if (p.isEmpty()) {
                return;
            }
            CompoundTag c = new CompoundTag();
            c.putInt("xp", p.xp);
            ListTag items = new ListTag();
            for (ItemStack stack : p.items) {
                if (!stack.isEmpty()) {
                    items.add(stack.save(registries));
                }
            }
            c.put("items", items);
            held.put(uuid.toString(), c);
        });
        tag.put("pending", held);
        return tag;
    }
}
