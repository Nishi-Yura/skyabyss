package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

// 一つの店の品ぞろえ (data/<名前空間>/shop/<店>.json)。読み込んだら変えない。
//   title    : 店の名前の翻訳キー (省くと gui.skyabyss.shop.<店>)
//   goods    : いつも並ぶ品 [{id, item, count, potion?, coins, materials: [{item, count}], limit?, chapter?}]
//              coins = 鐘銭の値段、materials = 巨神の素材などの値段 (両方書けば両方払う)。limit = 一日に一人が買える数 (0 = 限りなし)、
//              chapter = この章から並ぶ (それより前は伏せて見せる)、made = 誂えの品 (買うと「作った」合図)
//   rotation : 日替わり {count, pool: [goods と同じ書式]}。ワールドの日と店の名前で決まる抽選 (その日は全員が同じ顔ぶれ)
//   buys     : 店が買い取る品 [{item, per, coins}] = per 個で coins 枚
// エメラルドは値段にも品にも使わない (00 の 224)。読み込みで弾く
public record ShopDef(String id, String titleKey, List<Good> goods, int rotationCount, List<Good> pool, List<Buy> buys) {
    public record Price(Item item, int count) {
    }

    // made = 誂えの品 (作業台をなくした代わりに職人が作る。買うと「作った」合図 ItemCraftedEvent を流す。tools/gen/shop/gen_made_to_order.py)
    public record Good(String id, Item item, int count, @Nullable ResourceLocation potion, int coins, List<Price> materials, int limit, int chapter, boolean made) {
        public ItemStack stack() {
            ItemStack s = new ItemStack(item, count);
            if (potion != null) {
                BuiltInRegistries.POTION.getHolder(potion).ifPresent(h -> s.set(DataComponents.POTION_CONTENTS, new PotionContents(h)));
            }
            return s;
        }
    }

    public record Buy(Item item, int per, int coins) {
    }

    // その日の並び (いつもの品 + 日替わりの抽選)
    public List<Good> today(long day) {
        List<Good> out = new ArrayList<>(goods);
        if (rotationCount > 0 && !pool.isEmpty()) {
            List<Good> left = new ArrayList<>(pool);
            long seed = day * 0x9E3779B97F4A7C15L ^ id.hashCode() * 0xC2B2AE3D27D4EB4FL;
            java.util.Random r = new java.util.Random(seed);
            for (int i = 0; i < rotationCount && !left.isEmpty(); i++) {
                out.add(left.remove(r.nextInt(left.size())));
            }
        }
        return out;
    }

    @Nullable
    public Good good(String goodId, long day) {
        for (Good g : today(day)) {
            if (g.id().equals(goodId)) {
                return g;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- JSON
    public static ShopDef parse(String id, JsonObject o, List<String> errors) {
        String where = "shop " + id;
        List<Good> goods = parseGoods(o.get("goods"), errors, where);
        List<Good> pool = new ArrayList<>();
        int count = 0;
        if (o.has("rotation")) {
            JsonObject r = GsonHelper.getAsJsonObject(o, "rotation");
            count = GsonHelper.getAsInt(r, "count", 0);
            pool = parseGoods(r.get("pool"), errors, where + " rotation");
        }
        List<Buy> buys = new ArrayList<>();
        if (o.has("buys")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(o, "buys")) {
                JsonObject b = e.getAsJsonObject();
                Item item = item(GsonHelper.getAsString(b, "item", ""), errors, where + " buys");
                if (item != null) {
                    buys.add(new Buy(item, Math.max(1, GsonHelper.getAsInt(b, "per", 1)), Math.max(1, GsonHelper.getAsInt(b, "coins", 1))));
                }
            }
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Good g : goods) {
            if (!ids.add(g.id())) {
                errors.add(where + ": duplicate good " + g.id());
            }
        }
        for (Good g : pool) {
            if (!ids.add(g.id())) {
                errors.add(where + ": duplicate good " + g.id());
            }
        }
        return new ShopDef(id, GsonHelper.getAsString(o, "title", "gui.skyabyss.shop." + id), List.copyOf(goods), count, List.copyOf(pool),
                List.copyOf(buys));
    }

    private static List<Good> parseGoods(@Nullable JsonElement e, List<String> errors, String where) {
        List<Good> out = new ArrayList<>();
        if (e == null || !e.isJsonArray()) {
            return out;
        }
        for (JsonElement g : e.getAsJsonArray()) {
            JsonObject o = g.getAsJsonObject();
            String gid = GsonHelper.getAsString(o, "id", "");
            String w = where + " good " + gid;
            if (gid.isEmpty()) {
                errors.add(where + ": good without id");
                continue;
            }
            Item item = item(GsonHelper.getAsString(o, "item", ""), errors, w);
            if (item == null) {
                continue;
            }
            List<Price> materials = new ArrayList<>();
            if (o.has("materials")) {
                for (JsonElement m : GsonHelper.getAsJsonArray(o, "materials")) {
                    JsonObject mo = m.getAsJsonObject();
                    Item mi = item(GsonHelper.getAsString(mo, "item", ""), errors, w);
                    if (mi != null) {
                        materials.add(new Price(mi, Math.max(1, GsonHelper.getAsInt(mo, "count", 1))));
                    }
                }
            }
            int coins = GsonHelper.getAsInt(o, "coins", 0);
            if (coins <= 0 && materials.isEmpty()) {
                errors.add(w + ": no price");
            }
            ResourceLocation potion = null;
            if (o.has("potion")) {
                potion = ResourceLocation.tryParse(GsonHelper.getAsString(o, "potion"));
                if (potion == null || BuiltInRegistries.POTION.getOptional(potion).isEmpty()) {
                    errors.add(w + ": unknown potion " + GsonHelper.getAsString(o, "potion"));
                    potion = null;
                }
            }
            out.add(new Good(gid, item, Math.max(1, GsonHelper.getAsInt(o, "count", 1)), potion, Math.max(0, coins), List.copyOf(materials),
                    Math.max(0, GsonHelper.getAsInt(o, "limit", 0)), Math.max(1, GsonHelper.getAsInt(o, "chapter", 1)), GsonHelper.getAsBoolean(o, "made", false)));
        }
        return out;
    }

    @Nullable
    private static Item item(String id, List<String> errors, String where) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item == null || item == Items.AIR) {
            errors.add(where + ": unknown item " + id);
            return null;
        }
        if (item == Items.EMERALD || item == Items.EMERALD_BLOCK) {
            errors.add(where + ": emeralds are not used in the capital's shops");
            return null;
        }
        return item;
    }

    // 自己テスト用: 品ぞろえに使った品 (値段の素材を含む)
    public List<Item> allItems() {
        List<Item> out = new ArrayList<>();
        for (List<Good> list : List.of(goods, pool)) {
            for (Good g : list) {
                out.add(g.item());
                g.materials().forEach(m -> out.add(m.item()));
            }
        }
        buys.forEach(b -> out.add(b.item()));
        return out;
    }

}
