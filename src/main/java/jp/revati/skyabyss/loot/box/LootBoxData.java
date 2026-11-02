package jp.revati.skyabyss.loot.box;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

// 封の列: 人 × 箱の種類ごとに、封をした中身 (落ちた時点で決まった品。特性つき) を古い順に持つ (docs/design/52 の 1 章)。
// 箱そのものは中身を持たない (同じ種類なら重なる) ので、開けたときに古い方から取り出す。列が空の箱 (渡された箱・試しの箱) は、
// 開けるときにその箱の表から新しく引く。地上 (オーバーワールド) に保存する
public class LootBoxData extends SavedData {
    private static final String NAME = "skyabyss_loot_boxes";
    // 一つの列に覚えておく数の上限 (箱を人に渡し続けたときに増え続けないよう、古い方から捨てる)
    public static final int MAX_QUEUE = 512;

    private final Map<UUID, Map<String, Deque<ItemStack>>> queues = new HashMap<>();

    public static LootBoxData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LootBoxData::new, LootBoxData::load), NAME);
    }

    public Deque<ItemStack> queue(UUID player, String box) {
        return queues.computeIfAbsent(player, k -> new HashMap<>()).computeIfAbsent(box, k -> new ArrayDeque<>());
    }

    public void push(UUID player, String box, ItemStack gear) {
        Deque<ItemStack> q = queue(player, box);
        q.addLast(gear.copy());
        while (q.size() > MAX_QUEUE) {
            q.removeFirst();
        }
        setDirty();
    }

    // 古い方から一つ (空なら空の品)
    public ItemStack pop(UUID player, String box) {
        Deque<ItemStack> q = queue(player, box);
        ItemStack s = q.pollFirst();
        setDirty();
        return s == null ? ItemStack.EMPTY : s;
    }

    public int size(UUID player, String box) {
        return queue(player, box).size();
    }

    public void clear(UUID player) {
        queues.remove(player);
        setDirty();
    }

    private static LootBoxData load(CompoundTag tag, HolderLookup.Provider registries) {
        LootBoxData d = new LootBoxData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag boxes = players.getCompound(key);
            for (String box : boxes.getAllKeys()) {
                ListTag list = boxes.getList(box, Tag.TAG_COMPOUND);
                Deque<ItemStack> q = d.queue(id, box);
                for (Tag t : list) {
                    ItemStack.parse(registries, t).ifPresent(q::addLast);
                }
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        queues.forEach((id, boxes) -> {
            CompoundTag b = new CompoundTag();
            boxes.forEach((box, q) -> {
                if (q.isEmpty()) {
                    return;
                }
                ListTag list = new ListTag();
                for (ItemStack s : q) {
                    if (!s.isEmpty()) {
                        list.add(s.save(registries));
                    }
                }
                b.put(box, list);
            });
            if (!b.isEmpty()) {
                players.put(id.toString(), b);
            }
        });
        tag.put("players", players);
        return tag;
    }
}
