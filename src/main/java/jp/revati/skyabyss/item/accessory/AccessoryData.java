package jp.revati.skyabyss.item.accessory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

// プレイヤーごとの装飾の枠 (Data Attachment)。0 = 護符、1〜2 = 装飾。
// 保存はプレイヤーのデータと一緒。死んだときは AccessoryEvents が持ち物と同じに落とす (落とさない場面では copyOnDeath で残る)
public final class AccessoryData implements INBTSerializable<CompoundTag> {
    public static final int CHARM = 0;
    public static final int SIZE = 3;

    public final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    // 枠を作る前のセーブの装飾品 (持ち物にある) を、一度だけ枠へ移したか
    public boolean migrated;

    public static AccessoryKind kindOf(int index) {
        return index == CHARM ? AccessoryKind.CHARM : AccessoryKind.TRINKET;
    }

    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) i);
                list.add(items.get(i).save(provider, entry));
            }
        }
        tag.put("Items", list);
        tag.putBoolean("Migrated", migrated);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (int i = 0; i < SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        for (Tag t : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < SIZE) {
                items.set(slot, ItemStack.parseOptional(provider, entry));
            }
        }
        migrated = tag.getBoolean("Migrated");
    }

    // 持ち主のクライアントへの同期 (枠の中身と移した印)
    public static final StreamCodec<RegistryFriendlyByteBuf, AccessoryData> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), d -> new ArrayList<>(d.items),
            ByteBufCodecs.BOOL, d -> d.migrated,
            (List<ItemStack> list, Boolean migrated) -> {
                AccessoryData d = new AccessoryData();
                for (int i = 0; i < Math.min(SIZE, list.size()); i++) {
                    d.items.set(i, list.get(i));
                }
                d.migrated = migrated;
                return d;
            });
}
