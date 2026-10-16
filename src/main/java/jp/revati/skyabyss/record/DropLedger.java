package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// 地面に落ちている大事な品の控え (オーバーワールドに保存)。
// 落ちた品が読み込まれていない所にあっても、控え帳で「どこに落ちているか」を示せるようにする。
// 拾われた・消えたときに消し、読み込みが外れたときは最後の位置を残す
public final class DropLedger extends SavedData {
    private static final String NAME = "skyabyss_record_drops";
    // 一人あたりの控えの上限 (壊れたデータで膨らまないように)
    static final int PER_OWNER_LIMIT = 64;

    public record Drop(UUID owner, String item, int count, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private final Map<UUID, Drop> drops = new LinkedHashMap<>();

    public static DropLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DropLedger::new, DropLedger::load), NAME);
    }

    public void put(UUID entity, Drop drop) {
        Drop old = drops.get(entity);
        if (drop.equals(old)) {
            return;
        }
        if (old == null && owned(drop.owner()).size() >= PER_OWNER_LIMIT) {
            return;
        }
        drops.put(entity, drop);
        setDirty();
    }

    public void remove(UUID entity) {
        if (drops.remove(entity) != null) {
            setDirty();
        }
    }

    public Map<UUID, Drop> all() {
        return java.util.Collections.unmodifiableMap(drops);
    }

    public List<Map.Entry<UUID, Drop>> owned(UUID owner) {
        List<Map.Entry<UUID, Drop>> list = new ArrayList<>();
        for (Map.Entry<UUID, Drop> e : drops.entrySet()) {
            if (e.getValue().owner().equals(owner)) {
                list.add(e);
            }
        }
        return list;
    }

    private static DropLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        DropLedger ledger = new DropLedger();
        CompoundTag all = tag.getCompound("drops");
        for (String key : all.getAllKeys()) {
            try {
                CompoundTag d = all.getCompound(key);
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(d.getString("dim")));
                ledger.drops.put(UUID.fromString(key), new Drop(d.getUUID("owner"), d.getString("item"), d.getInt("count"), dim,
                        BlockPos.of(d.getLong("pos"))));
            } catch (RuntimeException ignored) {
                // 壊れた控えは捨てる
            }
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag all = new CompoundTag();
        drops.forEach((uuid, d) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("owner", d.owner());
            c.putString("item", d.item());
            c.putInt("count", d.count());
            c.putString("dim", d.dimension().location().toString());
            c.putLong("pos", d.pos().asLong());
            all.put(uuid.toString(), c);
        });
        tag.put("drops", all);
        return tag;
    }
}
