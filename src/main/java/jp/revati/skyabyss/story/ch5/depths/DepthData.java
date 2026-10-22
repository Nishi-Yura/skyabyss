package jp.revati.skyabyss.story.ch5.depths;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 心臓の下の深み (docs/design/36 の 9) の、ワールドに一つの記録
//   建てた版   区画の番号 → 建てた版 (版を上げると建て直す)
//   裂け目     心臓の間の裂け目を開けたか (倒したあとに一度だけ)
//   記録       組ごと・一人ごとの一番深い数字 (心臓の下の深さ。表示は DepthPlan.totalDepth)
//   節目       記録の節目 (DepthRecords.MILESTONES) をどこまで受け取ったか (数。組ごと・一人ごと)
final class DepthData extends SavedData {
    private static final Factory<DepthData> FACTORY = new Factory<>(DepthData::new, DepthData::load);
    private static final String NAME = "skyabyss_depth";

    final Map<Integer, Integer> built = new HashMap<>();
    boolean rift;
    final Map<UUID, Integer> groupBest = new HashMap<>();
    final Map<UUID, Integer> playerBest = new HashMap<>();
    final Map<UUID, Integer> groupMile = new HashMap<>();
    final Map<UUID, Integer> playerMile = new HashMap<>();

    static DepthData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static DepthData load(CompoundTag tag, HolderLookup.Provider registries) {
        DepthData d = new DepthData();
        CompoundTag b = tag.getCompound("Built");
        for (String k : b.getAllKeys()) {
            d.built.put(Integer.parseInt(k), b.getInt(k));
        }
        d.rift = tag.getBoolean("Rift");
        read(tag.getCompound("GroupBest"), d.groupBest);
        read(tag.getCompound("PlayerBest"), d.playerBest);
        read(tag.getCompound("GroupMile"), d.groupMile);
        read(tag.getCompound("PlayerMile"), d.playerMile);
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag b = new CompoundTag();
        for (Map.Entry<Integer, Integer> e : built.entrySet()) {
            b.putInt(String.valueOf(e.getKey()), e.getValue());
        }
        tag.put("Built", b);
        tag.putBoolean("Rift", rift);
        tag.put("GroupBest", write(groupBest));
        tag.put("PlayerBest", write(playerBest));
        tag.put("GroupMile", write(groupMile));
        tag.put("PlayerMile", write(playerMile));
        return tag;
    }

    private static CompoundTag write(Map<UUID, Integer> m) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : m.entrySet()) {
            t.putInt(e.getKey().toString(), e.getValue());
        }
        return t;
    }

    private static void read(CompoundTag t, Map<UUID, Integer> into) {
        for (String k : t.getAllKeys()) {
            try {
                into.put(UUID.fromString(k), t.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
    }
}
