package jp.revati.skyabyss.titan.travel;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 世界にある碑の一覧 (オーバーワールドに保存)。位置 → 名前。
// 碑を置くと加わり、壊すと消える。プレイヤーが覚えた碑のうち、ここにないものは「失われた碑」として行き先に出す
public final class TravelWorldData extends SavedData {
    private static final String NAME = "skyabyss_titan_travel";
    private static final Factory<TravelWorldData> FACTORY = new Factory<>(TravelWorldData::new, TravelWorldData::load);

    // 碑の名前。金床で名付けた碑ならその名、なければ空 (画面では土地の名で呼ぶ)
    public record Waymark(String name, String biome) {
    }

    private final Map<BlockPos, Waymark> waymarks = new HashMap<>();

    public static TravelWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Nullable
    public Waymark waymark(BlockPos pos) {
        return waymarks.get(pos);
    }

    public void put(BlockPos pos, Waymark mark) {
        waymarks.put(pos.immutable(), mark);
        setDirty();
    }

    public boolean remove(BlockPos pos) {
        boolean removed = waymarks.remove(pos) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int size() {
        return waymarks.size();
    }

    static TravelWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        TravelWorldData data = new TravelWorldData();
        for (Tag t : tag.getList("waymarks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.waymarks.put(BlockPos.of(c.getLong("pos")), new Waymark(c.getString("name"), c.getString("biome")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        waymarks.forEach((pos, mark) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", pos.asLong());
            c.putString("name", mark.name());
            c.putString("biome", mark.biome());
            list.add(c);
        });
        tag.put("waymarks", list);
        return tag;
    }
}
