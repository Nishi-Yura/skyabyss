package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// 持ち主に結ばれた大事な品 (天の鍵の欠片・天の鍵) の記録 (オーバーワールドに保存。月のループでも消えない)。KeepsakeBonds が使う。
//  - gen: 品の「代」。呼び戻すたびに 1 つ進め、前の代の品は見つかった所で砂になる (一人一つを壊さないため)
//  - lastClears: 巨神ごとの、最後に見た心臓の撃破数 (増えたら「もう一度倒した」)
//  - stash: 箱 (チェストなど) にしまった品の在りか。箱を閉じたときに数え直す
public final class BondData extends SavedData {
    private static final String NAME = "skyabyss_record_bonds";
    // 一人あたりの箱の控えの上限
    static final int STASH_LIMIT = 32;

    public record Stash(ResourceKey<Level> dimension, BlockPos pos, String item, int count) {
    }

    static final class Entry {
        final Map<String, Integer> gen = new HashMap<>();
        final Map<String, Integer> lastClears = new HashMap<>();
        final List<Stash> stash = new ArrayList<>();
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static BondData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BondData::new, BondData::load), NAME);
    }

    private Entry entry(UUID player) {
        return players.computeIfAbsent(player, k -> new Entry());
    }

    public int gen(UUID player, String item) {
        Entry e = players.get(player);
        return e == null ? 0 : e.gen.getOrDefault(item, 0);
    }

    int nextGen(UUID player, String item) {
        setDirty();
        return entry(player).gen.merge(item, 1, Integer::sum);
    }

    // 最後に見た撃破数 (まだ見ていなければ -1)
    int lastClears(UUID player, String titan) {
        Entry e = players.get(player);
        return e == null ? -1 : e.lastClears.getOrDefault(titan, -1);
    }

    void setLastClears(UUID player, String titan, int clears) {
        if (lastClears(player, titan) != clears) {
            entry(player).lastClears.put(titan, clears);
            setDirty();
        }
    }

    public List<Stash> stash(UUID owner) {
        Entry e = players.get(owner);
        return e == null ? List.of() : List.copyOf(e.stash);
    }

    // 箱の中身を数え直した: その箱のその品の控えを count にする (0 なら外す)
    void setStash(UUID owner, ResourceKey<Level> dimension, BlockPos pos, String item, int count) {
        Entry e = entry(owner);
        boolean changed = e.stash.removeIf(s -> s.dimension().equals(dimension) && s.pos().equals(pos) && s.item().equals(item));
        if (count > 0) {
            if (e.stash.size() >= STASH_LIMIT) {
                e.stash.remove(0);
            }
            e.stash.add(new Stash(dimension, pos.immutable(), item, count));
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    // その箱にある、だれかの品の控えをすべて外す (数え直す前に)
    void clearStashAt(ResourceKey<Level> dimension, BlockPos pos, String item) {
        for (Entry e : players.values()) {
            if (e.stash.removeIf(s -> s.dimension().equals(dimension) && s.pos().equals(pos) && s.item().equals(item))) {
                setDirty();
            }
        }
    }

    void clearStash(UUID owner, String item) {
        Entry e = players.get(owner);
        if (e != null && e.stash.removeIf(s -> s.item().equals(item))) {
            setDirty();
        }
    }

    // 自己テスト用
    void forget(UUID player) {
        if (players.remove(player) != null) {
            setDirty();
        }
    }

    private static BondData load(CompoundTag tag, HolderLookup.Provider registries) {
        BondData data = new BondData();
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            CompoundTag p = all.getCompound(key);
            Entry e = new Entry();
            CompoundTag gen = p.getCompound("gen");
            for (String item : gen.getAllKeys()) {
                e.gen.put(item, gen.getInt(item));
            }
            CompoundTag clears = p.getCompound("clears");
            for (String titan : clears.getAllKeys()) {
                e.lastClears.put(titan, clears.getInt(titan));
            }
            for (Tag t : p.getList("stash", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) t;
                ResourceLocation dim = ResourceLocation.tryParse(s.getString("dim"));
                if (dim != null && e.stash.size() < STASH_LIMIT) {
                    e.stash.add(new Stash(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(s.getLong("pos")), s.getString("item"),
                            s.getInt("count")));
                }
            }
            try {
                data.players.put(UUID.fromString(key), e);
            } catch (IllegalArgumentException ignored) {
                // 壊れた名前は捨てる
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        players.forEach((uuid, e) -> {
            CompoundTag p = new CompoundTag();
            CompoundTag gen = new CompoundTag();
            e.gen.forEach(gen::putInt);
            p.put("gen", gen);
            CompoundTag clears = new CompoundTag();
            e.lastClears.forEach(clears::putInt);
            p.put("clears", clears);
            ListTag stash = new ListTag();
            for (Stash s : e.stash) {
                CompoundTag c = new CompoundTag();
                c.putString("dim", s.dimension().location().toString());
                c.putLong("pos", s.pos().asLong());
                c.putString("item", s.item());
                c.putInt("count", s.count());
                stash.add(c);
            }
            p.put("stash", stash);
            all.put(uuid.toString(), p);
        });
        tag.put("players", all);
        return tag;
    }
}
