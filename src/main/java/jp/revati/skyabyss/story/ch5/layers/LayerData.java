package jp.revati.skyabyss.story.ch5.layers;

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

// 深淵の層の記録 (ワールドに一つ)。
//   人ごと (flags): "mark:bone_1" 印を持つ・"lord:bone" 主を倒した・"clear:nest_bone" 巣や縄張りを払った・"seen:bone" 区切りの題字を見た・
//                   "read:journal_bone_1" 手記を読んだ・"took:frag_salt_1" 品を拾った
//   ワールド (open): "scut_bone_1" 近道の格子・"door_bone" 灯皿の戸・"hid_bone" 隠し壁・"door_storm_0" 鼓動の戸・"seal_ash" 三つの術の封
public final class LayerData extends SavedData {
    private static final Factory<LayerData> FACTORY = new Factory<>(LayerData::new, LayerData::load);
    final Map<UUID, Set<String>> flags = new HashMap<>();
    final Set<String> open = new HashSet<>();

    public static LayerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_abyss_layers");
    }

    public boolean has(UUID who, String flag) {
        Set<String> s = flags.get(who);
        return s != null && s.contains(flag);
    }

    // 立てた (新しく立ったなら true)
    public boolean set(UUID who, String flag) {
        boolean added = flags.computeIfAbsent(who, k -> new HashSet<>()).add(flag);
        if (added) {
            setDirty();
        }
        return added;
    }

    public void unset(UUID who, String flag) {
        Set<String> s = flags.get(who);
        if (s != null && s.remove(flag)) {
            setDirty();
        }
    }

    public void forget(UUID who) {
        if (flags.remove(who) != null) {
            setDirty();
        }
    }

    public boolean isOpen(String what) {
        return open.contains(what);
    }

    public boolean setOpen(String what) {
        boolean added = open.add(what);
        if (added) {
            setDirty();
        }
        return added;
    }

    public void close(String what) {
        if (open.remove(what)) {
            setDirty();
        }
    }

    public int marks(UUID who, String layer) {
        int n = 0;
        for (int i = 1; i <= 3; i++) {
            if (has(who, "mark:" + layer + "_" + i)) {
                n++;
            }
        }
        return n;
    }

    private static LayerData load(CompoundTag tag, HolderLookup.Provider registries) {
        LayerData d = new LayerData();
        CompoundTag f = tag.getCompound("Flags");
        for (String k : f.getAllKeys()) {
            Set<String> s = new HashSet<>();
            for (Tag t : f.getList(k, Tag.TAG_STRING)) {
                s.add(t.getAsString());
            }
            try {
                d.flags.put(UUID.fromString(k), s);
            } catch (IllegalArgumentException ignored) {
                // 壊れた名前は捨てる
            }
        }
        for (Tag t : tag.getList("Open", Tag.TAG_STRING)) {
            d.open.add(t.getAsString());
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag f = new CompoundTag();
        flags.forEach((k, s) -> {
            ListTag l = new ListTag();
            s.forEach(x -> l.add(StringTag.valueOf(x)));
            f.put(k.toString(), l);
        });
        tag.put("Flags", f);
        ListTag o = new ListTag();
        open.forEach(x -> o.add(StringTag.valueOf(x)));
        tag.put("Open", o);
        return tag;
    }
}
