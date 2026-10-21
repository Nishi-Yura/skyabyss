package jp.revati.skyabyss.facility;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 預かり所の中身 (人ごと 54 枠。docs/design/35 の 3.1、00 の 225)。どの預かり番からも同じ中身 (StashMenu)
public final class StashData implements INBTSerializable<CompoundTag> {
    public static final int SIZE = 54;

    final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public NonNullList<ItemStack> items() {
        return items;
    }

    public int count() {
        int n = 0;
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, provider);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (int i = 0; i < SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items, provider);
    }
}
