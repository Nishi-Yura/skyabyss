package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.pouch.PouchEntries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// ポーチの余白の枠 (B13): 転生 2・6・12 回で 1 つずつ (最大 3)。1 枠に 1 種類の品を 999 個まで預けられる。
// 倒れても残る (RebirthData は死んでも引き継ぐ)。部品のない (そのままの) 品・積める品だけ。登録された袋の品 (専用の枠がある) は入れない。
// 預ける・出す操作は PouchMenu の clicked から呼ぶ (サーバーが決める)
public final class RebirthSpare {
    private RebirthSpare() {
    }

    public static int slots(Player player) {
        return Rebirth.spareSlots(player);
    }

    // 余白に預けてよい品か
    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() <= 1 || stack.isDamageableItem()) {
            return false;
        }
        // 部品 (名前・中身・強化など) のない品だけ。持ち出したときに同じ品に戻せる
        if (!stack.getComponentsPatch().isEmpty()) {
            return false;
        }
        return PouchEntries.entry(stack) == null;
    }

    // 枠 slot の見かけの品 (空なら EMPTY)
    public static ItemStack view(Player player, int slot) {
        RebirthData d = Rebirth.data(player);
        if (slot < 0 || slot >= RebirthData.SPARE_MAX || slot >= slots(player) || d.spareCount[slot] <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = item(d.spareItem[slot]);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, d.spareCount[slot]);
    }

    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    // 預ける。動かした数を返す。枠が空か同じ品のときだけ
    public static int deposit(ServerPlayer player, int slot, ItemStack carried, int n) {
        if (slot < 0 || slot >= slots(player) || n <= 0 || !accepts(carried)) {
            return 0;
        }
        RebirthData d = Rebirth.data(player);
        String id = BuiltInRegistries.ITEM.getKey(carried.getItem()).toString();
        if (d.spareCount[slot] > 0 && !d.spareItem[slot].equals(id)) {
            return 0;
        }
        // 同じ品が別の枠にあるときは、そちらへ (一つの品が 2 枠に割れない)
        for (int i = 0; i < RebirthData.SPARE_MAX; i++) {
            if (i != slot && d.spareCount[i] > 0 && d.spareItem[i].equals(id)) {
                return 0;
            }
        }
        int room = RebirthData.SPARE_LIMIT - d.spareCount[slot];
        int moved = Math.min(Math.min(n, carried.getCount()), room);
        if (moved <= 0) {
            return 0;
        }
        d.spareItem[slot] = id;
        d.spareCount[slot] += moved;
        carried.shrink(moved);
        Rebirth.sync(player);
        return moved;
    }

    // 取り出す (want 個まで、1 スタックを超えない)。取り出した品 (空なら EMPTY)
    public static ItemStack take(ServerPlayer player, int slot, int want) {
        RebirthData d = Rebirth.data(player);
        ItemStack shown = view(player, slot);
        if (shown.isEmpty() || want <= 0) {
            return ItemStack.EMPTY;
        }
        int n = Math.min(Math.min(want, d.spareCount[slot]), shown.getMaxStackSize());
        d.spareCount[slot] -= n;
        if (d.spareCount[slot] <= 0) {
            d.spareCount[slot] = 0;
            d.spareItem[slot] = "";
        }
        Rebirth.sync(player);
        return new ItemStack(shown.getItem(), n);
    }
}
