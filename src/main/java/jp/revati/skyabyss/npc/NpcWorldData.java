package jp.revati.skyabyss.npc;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

// NPC まわりのワールドの記録 (オーバーワールドに保存)。
//   loop     : 月のループの回数 (M4 のループ本体が WorldLoop.advance で進める。それまでは 0)
//   camps    : 語り部の野営 (鐘のある村が近くに無い人のために立てた焚き火。村と同じく、近づくと語り部が立つ。07 の 3.1節)
//   guides   : 人ごとの「語り部に会えるまでの案内」の進み具合 (StoryGuide)
//   villages : 村 (鐘の位置) ごとに、NPC を最後に立たせた時刻。いなくなったとき (コマンドで消した等) に、すぐ立て直さないため
public class NpcWorldData extends SavedData {
    private static final String NAME = "skyabyss_npc";
    private static final Factory<NpcWorldData> FACTORY = new Factory<>(NpcWorldData::new, NpcWorldData::load);

    int loop;
    // 鐘の位置 (BlockPos.asLong) → 役 → 立たせたゲーム時刻
    final Map<Long, Map<String, Long>> villages = new HashMap<>();

    public static NpcWorldData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    long spawnedAt(long bell, String role) {
        Map<String, Long> m = villages.get(bell);
        return m == null ? -1 : m.getOrDefault(role, -1L);
    }

    void markSpawned(long bell, String role, long time) {
        villages.computeIfAbsent(bell, b -> new HashMap<>()).put(role, time);
        setDirty();
    }

    static NpcWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcWorldData data = new NpcWorldData();
        data.loop = tag.getInt("loop");
        for (Tag t : tag.getList("villages", Tag.TAG_COMPOUND)) {
            CompoundTag v = (CompoundTag) t;
            Map<String, Long> roles = new HashMap<>();
            CompoundTag r = v.getCompound("roles");
            for (String key : r.getAllKeys()) {
                roles.put(key, r.getLong(key));
            }
            data.villages.put(v.getLong("bell"), roles);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("loop", loop);
        ListTag list = new ListTag();
        villages.forEach((bell, roles) -> {
            CompoundTag v = new CompoundTag();
            v.putLong("bell", bell);
            CompoundTag r = new CompoundTag();
            roles.forEach(r::putLong);
            v.put("roles", r);
            list.add(v);
        });
        tag.put("villages", list);
        return tag;
    }
}
