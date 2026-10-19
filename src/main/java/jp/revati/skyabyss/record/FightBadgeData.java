package jp.revati.skyabyss.record;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 「倒れた回数の札」の人ごとの記録 (オーバーワールドに保存)。巨神 × 戦いの種類 (心臓 heart・中ボス mid) ごとに、
// その人が参加して勝った戦いの「パーティの倒れた回数」の最少。札は見た目だけで、報酬は変えない (docs/design/10 の 10.14)。
public final class FightBadgeData extends SavedData {
    private static final String NAME = "skyabyss_fight_badges";
    public static final String HEART = "heart";
    public static final String MID = "mid";

    private final Map<UUID, Map<String, Integer>> best = new HashMap<>();

    public static FightBadgeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(FightBadgeData::new, FightBadgeData::load), NAME);
    }

    private static String key(String titan, String kind) {
        return titan + "/" + kind;
    }

    // 最少の倒れた回数 (まだ勝っていなければ -1)
    public int best(UUID player, String titan, String kind) {
        Map<String, Integer> m = best.get(player);
        return m == null ? -1 : m.getOrDefault(key(titan, kind), -1);
    }

    // 勝った戦いの結果を記録する。最良 (最少) を更新したら true (初めての勝ちも true)
    public boolean record(UUID player, String titan, String kind, int falls) {
        Map<String, Integer> m = best.computeIfAbsent(player, k -> new HashMap<>());
        Integer old = m.get(key(titan, kind));
        if (old != null && old <= falls) {
            return false;
        }
        m.put(key(titan, kind), Math.max(0, falls));
        setDirty();
        return true;
    }

    public void forget(UUID player) {
        if (best.remove(player) != null) {
            setDirty();
        }
    }

    private static FightBadgeData load(CompoundTag tag, HolderLookup.Provider registries) {
        FightBadgeData data = new FightBadgeData();
        CompoundTag all = tag.getCompound("players");
        for (String id : all.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(id);
                CompoundTag p = all.getCompound(id);
                Map<String, Integer> m = new HashMap<>();
                for (String k : p.getAllKeys()) {
                    m.put(k, p.getInt(k));
                }
                data.best.put(uuid, m);
            } catch (IllegalArgumentException ignored) {
                // 壊れた名前は捨てる
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        best.forEach((uuid, m) -> {
            CompoundTag p = new CompoundTag();
            m.forEach(p::putInt);
            all.put(uuid.toString(), p);
        });
        tag.put("players", all);
        return tag;
    }
}
