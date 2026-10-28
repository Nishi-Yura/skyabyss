package jp.revati.skyabyss.story.ch5.depths;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 心臓の下の深み (docs/design/36 の 9) の、ワールドに一つの記録
//   潜り       組ごとの場所・今の階・潜りの通し番号・再開の階 (階そのものは種から作り直せるので、ブロックは持たない)
//   裂け目     心臓の間の裂け目を開けたか (倒したあとに一度だけ)
//   記録       組ごと・一人ごとの一番深い数字 (心臓の下の深さ。表示は DepthPlan.totalDepth)
//   節目       記録の節目 (DepthRecords.MILESTONES) をどこまで受け取ったか (数。組ごと・一人ごと)
final class DepthData extends SavedData {
    private static final Factory<DepthData> FACTORY = new Factory<>(DepthData::new, DepthData::load);
    private static final String NAME = "skyabyss_depth";

    // 組ごとの潜り: 場所 (slot)・今の階・潜りの通し番号 (salt)・再開の階
    static final class Run {
        int slot;
        int floor;
        int salt;
        int checkpoint = 1;
    }

    final Map<UUID, Run> runs = new HashMap<>();
    int nextSlot;
    boolean rift;
    final Map<UUID, Integer> groupBest = new HashMap<>();
    final Map<UUID, Integer> playerBest = new HashMap<>();
    final Map<UUID, Integer> groupMile = new HashMap<>();
    final Map<UUID, Integer> playerMile = new HashMap<>();
    // 記録したときの名前 (組は顔ぶれ「A・B」、人は名前)。組が解けても順位の石に名前を出すため
    final Map<UUID, String> names = new HashMap<>();

    static DepthData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static DepthData load(CompoundTag tag, HolderLookup.Provider registries) {
        DepthData d = new DepthData();
        d.nextSlot = tag.getInt("NextSlot");
        CompoundTag rs = tag.getCompound("Runs");
        for (String k : rs.getAllKeys()) {
            try {
                CompoundTag t = rs.getCompound(k);
                Run run = new Run();
                run.slot = t.getInt("Slot");
                run.floor = t.getInt("Floor");
                run.salt = t.getInt("Salt");
                run.checkpoint = Math.max(1, t.getInt("Checkpoint"));
                d.runs.put(UUID.fromString(k), run);
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
        d.rift = tag.getBoolean("Rift");
        read(tag.getCompound("GroupBest"), d.groupBest);
        read(tag.getCompound("PlayerBest"), d.playerBest);
        read(tag.getCompound("GroupMile"), d.groupMile);
        read(tag.getCompound("PlayerMile"), d.playerMile);
        CompoundTag ns = tag.getCompound("Names");
        for (String k : ns.getAllKeys()) {
            try {
                d.names.put(UUID.fromString(k), ns.getString(k));
            } catch (IllegalArgumentException ignored) {
                // 壊れた鍵は捨てる
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag rs = new CompoundTag();
        for (Map.Entry<UUID, Run> e : runs.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putInt("Slot", e.getValue().slot);
            t.putInt("Floor", e.getValue().floor);
            t.putInt("Salt", e.getValue().salt);
            t.putInt("Checkpoint", e.getValue().checkpoint);
            rs.put(e.getKey().toString(), t);
        }
        tag.put("Runs", rs);
        tag.putInt("NextSlot", nextSlot);
        tag.putBoolean("Rift", rift);
        tag.put("GroupBest", write(groupBest));
        tag.put("PlayerBest", write(playerBest));
        tag.put("GroupMile", write(groupMile));
        tag.put("PlayerMile", write(playerMile));
        CompoundTag ns = new CompoundTag();
        names.forEach((k, v) -> ns.putString(k.toString(), v));
        tag.put("Names", ns);
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
