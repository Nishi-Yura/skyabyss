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
    // 野営の焚き火の位置 (BlockPos.asLong)
    public final Set<Long> camps = new LinkedHashSet<>();
    final Map<UUID, Guide> guides = new HashMap<>();

    // 語り部に会えるまでの案内の進み具合 (人ごと)。since: 案内を始めた時刻、villageState: 0 まだ探していない / 1 村が見つかった / 2 近くに村がない
    public static final class Guide {
        public long since;
        public int villageState;
        public int villageX;
        public int villageZ;
        public long hintAt = -1;
        public int hints;
        public long campAt = -1;
        public boolean done;
    }

    public Guide guide(UUID player, long now) {
        return guides.computeIfAbsent(player, p -> {
            Guide g = new Guide();
            g.since = now;
            setDirty();
            return g;
        });
    }

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
        for (long camp : tag.getLongArray("camps")) {
            data.camps.add(camp);
        }
        for (Tag t : tag.getList("guides", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Guide g = new Guide();
            g.since = c.getLong("since");
            g.villageState = c.getInt("village_state");
            g.villageX = c.getInt("village_x");
            g.villageZ = c.getInt("village_z");
            g.hintAt = c.getLong("hint_at");
            g.hints = c.getInt("hints");
            g.campAt = c.getLong("camp_at");
            g.done = c.getBoolean("done");
            data.guides.put(c.getUUID("player"), g);
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
        tag.putLongArray("camps", camps.stream().mapToLong(Long::longValue).toArray());
        ListTag gs = new ListTag();
        guides.forEach((id, g) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", id);
            c.putLong("since", g.since);
            c.putInt("village_state", g.villageState);
            c.putInt("village_x", g.villageX);
            c.putInt("village_z", g.villageZ);
            c.putLong("hint_at", g.hintAt);
            c.putInt("hints", g.hints);
            c.putLong("camp_at", g.campAt);
            c.putBoolean("done", g.done);
            gs.add(c);
        });
        tag.put("guides", gs);
        return tag;
    }
}
