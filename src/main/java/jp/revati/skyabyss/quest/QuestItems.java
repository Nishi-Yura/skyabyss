package jp.revati.skyabyss.quest;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

// クエストで扱う品。
//  - 品の指定は ID ("minecraft:oak_log") か、タグ ("#minecraft:logs")
//  - 印 (token): 運ぶ品・見た目の小物に付ける目印 (CUSTOM_DATA の skyabyss_quest)。印のある目的は、同じ印の品だけを数える
//    (語り部の包みを、ただの革で代わりにできないように)
//  - 名前と説明は翻訳キー (言語を切り替えても追従する)
public final class QuestItems {
    public static final String TOKEN_KEY = SkyAbyss.MODID + "_quest";

    private QuestItems() {
    }

    // 読み込み時の確かめ: ID は登録にあるか、タグは書き方が正しいか (タグの中身は読み込みの順で、まだ決まっていないことがある)
    public static boolean known(String target) {
        if (target.startsWith("#")) {
            return ResourceLocation.tryParse(target.substring(1)) != null;
        }
        return Condition.item(target) != null;
    }

    public static boolean matches(ItemStack stack, String target, @Nullable String token) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean kind;
        if (target.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(target.substring(1));
            kind = tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        } else {
            Item item = Condition.item(target);
            kind = item != null && stack.is(item);
        }
        return kind && (token == null || token.equals(token(stack)));
    }

    @Nullable
    public static String token(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(TOKEN_KEY) ? tag.getString(TOKEN_KEY) : null;
    }

    // 留め袋の中の数 (印のない目的だけ。袋の品は部品のないただの品なので、印のある品は袋にない。22 の 8.3)
    private static int pouchCount(Player player, String target, @Nullable String token) {
        if (token != null) {
            return 0;
        }
        int n = 0;
        for (jp.revati.skyabyss.pouch.PouchEntries.Entry e : jp.revati.skyabyss.pouch.PouchEntries.all()) {
            if (matches(new ItemStack(e.item()), target, null)) {
                n += jp.revati.skyabyss.pouch.Pouch.count(player, e.item());
            }
        }
        return n;
    }

    public static int count(Player player, String target, @Nullable String token) {
        Inventory inv = player.getInventory();
        int n = pouchCount(player, target, token);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s, target, token)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // 着ている (防具の枠の) 品のうち、targets の種類がいくつそろっているか (目的 wear。同じ種類は 1 と数える)
    public static int worn(Player player, java.util.List<String> targets) {
        int n = 0;
        for (String target : targets) {
            for (ItemStack s : player.getInventory().armor) {
                if (matches(s, target, null)) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    // 足りなければ何もしない。取ったら true
    public static boolean take(Player player, String target, @Nullable String token, int count) {
        if (count(player, target, token) < count) {
            return false;
        }
        Inventory inv = player.getInventory();
        int left = count;
        if (token == null) {
            // 袋の中から先に払う
            for (jp.revati.skyabyss.pouch.PouchEntries.Entry e : jp.revati.skyabyss.pouch.PouchEntries.all()) {
                if (left > 0 && matches(new ItemStack(e.item()), target, null)) {
                    left -= jp.revati.skyabyss.pouch.Pouch.take(player, e.item(), left);
                }
            }
        }
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s, target, token)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        inv.setChanged();
        return true;
    }

    public static ItemStack make(Item item, int count, @Nullable String token, @Nullable String nameKey, List<String> loreKeys) {
        ItemStack stack = new ItemStack(item, count);
        if (token != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString(TOKEN_KEY, token);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (nameKey != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
        }
        if (!loreKeys.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            loreKeys.forEach(k -> lines.add(Component.translatable(k).withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY))));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }
}
