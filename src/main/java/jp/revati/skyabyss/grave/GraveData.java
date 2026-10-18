package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// 墓の控え (オーバーワールドに保存)。中身はブロックではなくここが持つ。
// ブロックは目印と入口だけなので、チャンクが読み込まれていなくても、控え帳に場所を出せて、語り部の救いも使える。
// 中身を渡す・救う・落とすときは、必ずここから消してから渡す (二つにならない)
public final class GraveData extends SavedData {
    private static final String NAME = "skyabyss_graves";

    public static final class Grave {
        public final UUID id;
        public final UUID owner;
        public final String ownerName;
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;
        public final long created;
        public int xp;
        public final List<ItemStack> items;

        public Grave(UUID id, UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos pos, long created, int xp, List<ItemStack> items) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.dimension = dimension;
            this.pos = pos;
            this.created = created;
            this.xp = xp;
            this.items = items;
        }

        public int itemCount() {
            int n = 0;
            for (ItemStack s : items) {
                n += s.getCount();
            }
            return n;
        }

        public boolean isEmpty() {
            return items.isEmpty() && xp <= 0;
        }
    }

    private final Map<UUID, Grave> graves = new LinkedHashMap<>();

    public static GraveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(GraveData::new, GraveData::load), NAME);
    }

    public void add(Grave grave) {
        graves.put(grave.id, grave);
        setDirty();
    }

    @Nullable
    public Grave remove(UUID id) {
        Grave g = graves.remove(id);
        if (g != null) {
            setDirty();
        }
        return g;
    }

    @Nullable
    public Grave get(UUID id) {
        return graves.get(id);
    }

    @Nullable
    public Grave at(ResourceKey<Level> dimension, BlockPos pos) {
        for (Grave g : graves.values()) {
            if (g.dimension.equals(dimension) && g.pos.equals(pos)) {
                return g;
            }
        }
        return null;
    }

    // 古い順
    public List<Grave> owned(UUID owner) {
        List<Grave> list = new ArrayList<>();
        for (Grave g : graves.values()) {
            if (g.owner.equals(owner)) {
                list.add(g);
            }
        }
        list.sort(Comparator.comparingLong((Grave g) -> g.created));
        return list;
    }

    public List<Grave> all() {
        return Collections.unmodifiableList(new ArrayList<>(graves.values()));
    }

    // 中身を書き換えたあとに呼ぶ
    public void touch() {
        setDirty();
    }

    private static GraveData load(CompoundTag tag, HolderLookup.Provider registries) {
        GraveData data = new GraveData();
        ListTag list = tag.getList("graves", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            try {
                CompoundTag c = (CompoundTag) t;
                List<ItemStack> items = new ArrayList<>();
                for (Tag it : c.getList("items", Tag.TAG_COMPOUND)) {
                    ItemStack.parse(registries, it).ifPresent(s -> {
                        if (!s.isEmpty()) {
                            items.add(s);
                        }
                    });
                }
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("dim")));
                UUID id = c.getUUID("id");
                data.graves.put(id, new Grave(id, c.getUUID("owner"), c.getString("name"), dim, BlockPos.of(c.getLong("pos")), c.getLong("created"),
                        c.getInt("xp"), items));
            } catch (RuntimeException ignored) {
                // 壊れた控えは捨てる
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Grave g : graves.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", g.id);
            c.putUUID("owner", g.owner);
            c.putString("name", g.ownerName);
            c.putString("dim", g.dimension.location().toString());
            c.putLong("pos", g.pos.asLong());
            c.putLong("created", g.created);
            c.putInt("xp", g.xp);
            ListTag items = new ListTag();
            for (ItemStack s : g.items) {
                if (!s.isEmpty()) {
                    items.add(s.save(registries));
                }
            }
            c.put("items", items);
            list.add(c);
        }
        tag.put("graves", list);
        return tag;
    }
}
