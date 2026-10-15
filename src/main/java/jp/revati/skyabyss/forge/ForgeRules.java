package jp.revati.skyabyss.forge;

import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の決まりごと (画面の表示とサーバーの処理で同じ計算を使う)
//  強化: 岩鯨の欠片と共鳴の結晶を持ち物から使い、段階を一つ上げる。失敗はない
//  分解: 装備を壊し、共鳴の欠片 (伝説) か岩鯨の欠片 (それ以外) に戻す。鍛えた分の欠片も半分戻る
public final class ForgeRules {
    private ForgeRules() {
    }

    public record Cost(int shards, int crystals) {
    }

    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof ModGear && stack.getMaxStackSize() == 1;
    }

    public static boolean canEnhance(ItemStack stack) {
        return stack.getItem() instanceof ModGear gear && gear.enhanceable() && ModGear.enhance(stack) < ModGear.MAX_ENHANCE;
    }

    // level 段階から次へ上げる費用: 欠片 4,6,8…22、結晶 +5 からは 2 つ
    public static Cost enhanceCost(int level) {
        return new Cost(4 + level * 2, level >= ModGear.AWAKEN_LEVEL ? 2 : 1);
    }

    // これまでに鍛えるのに使った欠片の合計
    public static int shardsSpent(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += enhanceCost(i).shards();
        }
        return total;
    }

    // 分解で得るもの: [共鳴の欠片の数, 岩鯨の欠片の数]
    public static int[] dismantleYield(ItemStack stack) {
        if (!(stack.getItem() instanceof ModGear gear)) {
            return new int[]{0, 0};
        }
        int refund = shardsSpent(ModGear.enhance(stack)) / 2;
        return switch (gear.tier()) {
            case LEGENDARY -> new int[]{gear.tier().dismantleValue, refund};
            default -> new int[]{0, gear.tier().dismantleValue + refund};
        };
    }

    public static int count(Inventory inventory, Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void remove(Inventory inventory, Item item, int amount) {
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
    }

    public static boolean affordable(Player player, Cost cost) {
        return player.isCreative() || (count(player.getInventory(), ModItems.STONE_WHALE_SHARD.get()) >= cost.shards()
                && count(player.getInventory(), ModItems.RESONANCE_CRYSTAL.get()) >= cost.crystals());
    }

    // 強化する。できたら true
    public static boolean enhance(Player player, ItemStack stack) {
        if (!canEnhance(stack)) {
            return false;
        }
        int level = ModGear.enhance(stack);
        Cost cost = enhanceCost(level);
        if (!affordable(player, cost)) {
            return false;
        }
        if (!player.isCreative()) {
            remove(player.getInventory(), ModItems.STONE_WHALE_SHARD.get(), cost.shards());
            remove(player.getInventory(), ModItems.RESONANCE_CRYSTAL.get(), cost.crystals());
        }
        stack.set(ModDataComponents.ENHANCE.get(), level + 1);
        return true;
    }
}
