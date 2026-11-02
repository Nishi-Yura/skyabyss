package jp.revati.skyabyss.facility;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

// 誂え (作業台をなくした代わりに、鍛冶場・薬師・道具屋が作る品。ShopDef.Good.made)。2026-10-26 の声「クラフトとかもしない」。
// 品ぞろえは tools/gen/shop/gen_made_to_order.py が、もとのレシピから書いた。ここは品から誂えの口を探すだけ (自己テストと覚書の文)
public final class MadeToOrder {
    private MadeToOrder() {
    }

    public record Entry(ShopDef shop, ShopDef.Good good) {
        // 素材 (Mod の品) の合計
        public Map<Item, Integer> materials() {
            Map<Item, Integer> out = new HashMap<>();
            for (ShopDef.Price p : good.materials()) {
                out.merge(p.item(), p.count(), Integer::sum);
            }
            return out;
        }
    }

    // その品を誂える店の品 (誂えがなければ、ふつうに並ぶ品。どちらもなければ null)。item = "skyabyss:whale_road_helmet" か "whale_road_helmet"
    @Nullable
    public static Entry find(String item) {
        String id = item.contains(":") ? item : "skyabyss:" + item;
        Entry plain = null;
        for (ShopDef shop : ShopManager.all().values()) {
            for (ShopDef.Good g : shop.goods()) {
                if (BuiltInRegistries.ITEM.getKey(g.item()).toString().equals(id)) {
                    if (g.made()) {
                        return new Entry(shop, g);
                    }
                    plain = plain == null ? new Entry(shop, g) : plain;
                }
            }
        }
        // 前から並んでいた品 (軟膏など) は、誂えでなく買うだけの品のまま
        return plain;
    }
}
