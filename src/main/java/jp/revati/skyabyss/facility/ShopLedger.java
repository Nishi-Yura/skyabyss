package jp.revati.skyabyss.facility;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 店の記録 (人ごと)。数に限りのある品 (闇市の日替わり) を、その日に何個買ったか。
//   鍵は "<店>/<品の番号>@<日>"。古い日の記録は、書き足すときに捨てる
public final class ShopLedger implements INBTSerializable<CompoundTag> {
    final Map<String, Integer> bought = new HashMap<>();

    public int bought(String shop, String good, long day) {
        return bought.getOrDefault(key(shop, good, day), 0);
    }

    public void add(String shop, String good, long day, int n) {
        String suffix = "@" + day;
        bought.keySet().removeIf(k -> !k.endsWith(suffix));
        bought.merge(key(shop, good, day), n, Integer::sum);
    }

    private static String key(String shop, String good, long day) {
        return shop + "/" + good + "@" + day;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        bought.forEach(tag::putInt);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        bought.clear();
        for (String k : tag.getAllKeys()) {
            bought.put(k, tag.getInt(k));
        }
    }
}
