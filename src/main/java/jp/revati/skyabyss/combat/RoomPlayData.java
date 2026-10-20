package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// 戦いの部屋に置いた罠の板と柱の場所 (RoomPlay)。背のディメンションごと。部屋の名前 → 置き場所
public final class RoomPlayData extends SavedData {
    private static final String NAME = "skyabyss_room_play";
    private static final Factory<RoomPlayData> FACTORY = new Factory<>(RoomPlayData::new, RoomPlayData::load);

    private final Map<String, RoomPlay.Layout> rooms = new HashMap<>();

    public static RoomPlayData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Nullable
    RoomPlay.Layout get(String room) {
        return rooms.get(room);
    }

    void put(String room, RoomPlay.Layout layout) {
        rooms.put(room, layout);
        setDirty();
    }

    void remove(String room) {
        if (rooms.remove(room) != null) {
            setDirty();
        }
    }

    private static RoomPlayData load(CompoundTag tag, HolderLookup.Provider registries) {
        RoomPlayData d = new RoomPlayData();
        for (Tag t : tag.getList("rooms", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.rooms.put(c.getString("id"), new RoomPlay.Layout(read(c.getLongArray("traps")), read(c.getLongArray("pillars"))));
        }
        return d;
    }

    private static List<BlockPos> read(long[] a) {
        List<BlockPos> out = new ArrayList<>();
        for (long l : a) {
            out.add(BlockPos.of(l));
        }
        return List.copyOf(out);
    }

    private static long[] write(List<BlockPos> list) {
        long[] a = new long[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i).asLong();
        }
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, RoomPlay.Layout> e : rooms.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("id", e.getKey());
            c.putLongArray("traps", write(e.getValue().traps()));
            c.putLongArray("pillars", write(e.getValue().pillars()));
            list.add(c);
        }
        tag.put("rooms", list);
        return tag;
    }
}
