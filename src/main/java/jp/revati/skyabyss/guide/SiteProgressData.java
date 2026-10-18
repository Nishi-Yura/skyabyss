package jp.revati.skyabyss.guide;

import java.util.HashMap;
import java.util.HashSet;
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

// 地上の場所 (小さなダンジョンなど。21 の 1.5) の、人ごとの記録 (オーバーワールドに一つ。死んでも・パーティが変わっても残る)。
//   cleared  : クリアした場所の ID (`site_clear.<id>`)。初回の経験値・宝はこれが立つとき一度だけ
//   repeatAt : 周回の素材を最後に受け取ったゲーム内の時刻 (1 日 = 24000 tick ごとに最後の部屋が戻る)
//   treasure : 一度きりの宝 (SiteTreasure) を受け取った印
public final class SiteProgressData extends SavedData {
    private static final String NAME = "skyabyss_site_progress";
    private static final Factory<SiteProgressData> FACTORY = new Factory<>(SiteProgressData::new, SiteProgressData::load);

    private final Map<UUID, Set<String>> cleared = new HashMap<>();
    private final Map<UUID, Map<String, Long>> repeatAt = new HashMap<>();
    private final Map<UUID, Set<String>> treasure = new HashMap<>();

    public static SiteProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public boolean cleared(UUID player, String site) {
        Set<String> set = cleared.get(player);
        return set != null && set.contains(site);
    }

    // 初めてなら true
    public boolean markCleared(UUID player, String site) {
        boolean added = cleared.computeIfAbsent(player, k -> new HashSet<>()).add(site);
        if (added) {
            setDirty();
        }
        return added;
    }

    // 最後に周回の素材を受け取った時刻 (まだなら Long.MIN_VALUE)
    public long repeatAt(UUID player, String site) {
        Map<String, Long> m = repeatAt.get(player);
        return m == null ? Long.MIN_VALUE : m.getOrDefault(site, Long.MIN_VALUE);
    }

    public void markRepeat(UUID player, String site, long now) {
        repeatAt.computeIfAbsent(player, k -> new HashMap<>()).put(site, now);
        setDirty();
    }

    public boolean hasTreasure(UUID player, String id) {
        Set<String> set = treasure.get(player);
        return set != null && set.contains(id);
    }

    // 初めてなら true
    public boolean markTreasure(UUID player, String id) {
        boolean added = treasure.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        if (added) {
            setDirty();
        }
        return added;
    }

    // 自己テスト・コマンド用: その人のその場所の記録をすべて消す
    public void forget(UUID player, String site) {
        boolean changed = false;
        Set<String> c = cleared.get(player);
        changed |= c != null && c.remove(site);
        Map<String, Long> r = repeatAt.get(player);
        changed |= r != null && r.remove(site) != null;
        Set<String> t = treasure.get(player);
        changed |= t != null && t.remove(site);
        if (changed) {
            setDirty();
        }
    }

    public void forgetTreasure(UUID player, String id) {
        Set<String> t = treasure.get(player);
        if (t != null && t.remove(id)) {
            setDirty();
        }
    }

    private static SiteProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiteProgressData data = new SiteProgressData();
        readSets(tag.getCompound("Cleared"), data.cleared);
        readSets(tag.getCompound("Treasure"), data.treasure);
        CompoundTag rep = tag.getCompound("RepeatAt");
        for (String key : rep.getAllKeys()) {
            Map<String, Long> m = new HashMap<>();
            CompoundTag inner = rep.getCompound(key);
            for (String site : inner.getAllKeys()) {
                m.put(site, inner.getLong(site));
            }
            data.repeatAt.put(UUID.fromString(key), m);
        }
        return data;
    }

    private static void readSets(CompoundTag all, Map<UUID, Set<String>> into) {
        for (String key : all.getAllKeys()) {
            Set<String> set = new HashSet<>();
            for (Tag t : all.getList(key, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            into.put(UUID.fromString(key), set);
        }
    }

    private static CompoundTag writeSets(Map<UUID, Set<String>> from) {
        CompoundTag all = new CompoundTag();
        from.forEach((player, set) -> {
            ListTag list = new ListTag();
            set.forEach(s -> list.add(StringTag.valueOf(s)));
            all.put(player.toString(), list);
        });
        return all;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Cleared", writeSets(cleared));
        tag.put("Treasure", writeSets(treasure));
        CompoundTag rep = new CompoundTag();
        repeatAt.forEach((player, m) -> {
            CompoundTag inner = new CompoundTag();
            m.forEach(inner::putLong);
            rep.put(player.toString(), inner);
        });
        tag.put("RepeatAt", rep);
        return tag;
    }
}
