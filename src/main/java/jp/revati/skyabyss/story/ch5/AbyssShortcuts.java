package jp.revati.skyabyss.story.ch5;

import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

// 深淵の近道 (docs/design/36 の 8 章「近道」)。層の底の番人を倒すと開く。開いた近道はワールドに一つ保存して共有する。
//   key は層の名前 ("ash" = 灰の迷路の燠の番人)。開いたことの記録と、近道の道を置くかどうかの問いだけを持つ。
//   鎖のはしごそのものの作りは層ごとの仕事 (layer_<層>.py と、近道の場所を決める所)。
public final class AbyssShortcuts {
    private AbyssShortcuts() {
    }

    // 近道を開く。もう開いていれば何もせず false
    public static boolean open(MinecraftServer server, String key) {
        Data d = Data.get(server);
        if (!d.keys.add(key)) {
            return false;
        }
        d.setDirty();
        SkyAbyss.LOGGER.info("[abyss] shortcut opened: {}", key);
        return true;
    }

    public static boolean isOpen(MinecraftServer server, String key) {
        return Data.get(server).keys.contains(key);
    }

    // 試験用: 開いた近道を全部閉じる
    public static void reset(MinecraftServer server) {
        Data d = Data.get(server);
        d.keys.clear();
        d.setDirty();
    }

    // 開いた近道 (ワールドに一つ)
    static final class Data extends SavedData {
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        final Set<String> keys = new HashSet<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "skyabyss_abyss_shortcuts");
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("Open", 8);
            for (int i = 0; i < list.size(); i++) {
                d.keys.add(list.getString(i));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (String k : keys) {
                list.add(StringTag.valueOf(k));
            }
            tag.put("Open", list);
            return tag;
        }
    }
}
