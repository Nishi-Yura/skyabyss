package jp.revati.skyabyss.forge;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の決まりごと (画面の表示とサーバーの処理で同じ計算を使う)
//  強化: 由来の巨神の素材 (岩鯨の品は岩鯨の鱗片、潮殻の品は潮殻の殻片。TitanGearSets.shardFor) と共鳴の結晶を持ち物から使い、段階を一つ上げる。失敗はない
//  分解: 装備を壊し、由来の巨神の共鳴の欠片 (伝説) か由来の巨神の素材 (それ以外) に戻す。鍛えた分の欠片も半分戻る。特性の付く品は結晶も
//  再鍛造: 共鳴の結晶 1 つで特性の候補を一組 (最大 3 組)。今のものか候補を選んで確定する (item/affix/AffixRules)
//  交換: その巨神の共鳴の欠片 30 で、まだ持っていない専用伝説を一つ。心臓を倒すごとに一度 (loot/TitanLoot.exchange)。
//        共鳴の欠片は巨神ごと (岩鯨・潮殻・雷翼・余燼。ModItems.resonanceShard)。古い共通の共鳴の欠片は、どの巨神の交換にも使える
public final class ForgeRules {
    private ForgeRules() {
    }

    public record Cost(int shards, int crystals) {
    }

    public static boolean accepts(ItemStack stack) {
        // 旅装 (鯨道の旅装) は炉に入れない: 鍛えず、分解して巨神の欠片を稼げないように (21 の 1.2)
        return stack.getItem() instanceof ModGear && stack.getMaxStackSize() == 1 && !(stack.getItem() instanceof jp.revati.skyabyss.guide.WhaleRoadArmorItem);
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

    // 分解で得るもの: [共鳴の欠片の数 (resonanceItem), 由来の巨神の素材の数 (shardItem), 共鳴の結晶の数]
    // 結晶は特性の付く武器・防具だけ (レア 1、伝説 2)。重なった甲殻や大剣が、再鍛造の結晶になる
    public static int[] dismantleYield(ItemStack stack) {
        if (!(stack.getItem() instanceof ModGear gear)) {
            return new int[]{0, 0, 0};
        }
        int refund = shardsSpent(ModGear.enhance(stack)) / 2;
        int crystals = AffixRules.eligible(stack) ? (gear.tier() == GearTier.LEGENDARY ? DISMANTLE_CRYSTALS_LEGENDARY : DISMANTLE_CRYSTALS_RARE) : 0;
        return switch (gear.tier()) {
            case LEGENDARY -> new int[]{gear.tier().dismantleValue, refund, crystals};
            default -> new int[]{0, gear.tier().dismantleValue + refund, crystals};
        };
    }

    // ---------------------------------------------------------------- 再鍛造
    public static final int DISMANTLE_CRYSTALS_RARE = 1;
    public static final int DISMANTLE_CRYSTALS_LEGENDARY = 2;

    public static Cost trialCost() {
        return new Cost(0, AffixRules.TRIAL_CRYSTALS);
    }

    // 候補を一組作る。できたら true (費用は持ち物から)
    public static boolean reforgeTrial(Player player, ItemStack stack) {
        if (!AffixRules.canTrial(stack) || !affordable(player, trialCost())) {
            return false;
        }
        if (!player.isCreative()) {
            remove(player, ModItems.RESONANCE_CRYSTAL.get(), trialCost().crystals());
        }
        AffixRules.addTrial(stack, player.getRandom());
        return true;
    }

    // ---------------------------------------------------------------- 交換 (共鳴の欠片 30 で、まだ持っていない専用伝説を一つ)
    public static final int EXCHANGE_SHARDS = 30;

    // 交換できるか: 心臓をまだ倒していない / この周回ではもう交換した / 図鑑の専用伝説をすべて持っている / できる
    public enum ExchangeState {
        READY, NO_CLEAR, USED, ALL_OWNED
    }

    // その巨神の共鳴の欠片の手もちの数 (古い共通の共鳴の欠片も、どの巨神の分にも数える)
    public static int resonanceHeld(Inventory inventory, String titan) {
        return count(inventory, ModItems.resonanceShard(titan)) + count(inventory, ModItems.RESONANCE_SHARD.get());
    }

    // 巨神 titan (stone_whale / tide_shell / thunder_wing / ember) の専用伝説を交換する分の共鳴の欠片があるか
    public static boolean exchangeAffordable(Player player, String titan) {
        return player.isCreative() || resonanceHeld(player, titan) >= EXCHANGE_SHARDS;
    }

    // 払う: その巨神の共鳴の欠片から先に、足りない分を古い共通の欠片から (炉が古い欠片を換えて払う)
    public static void payExchange(Player player, String titan) {
        if (!player.isCreative()) {
            Item own = ModItems.resonanceShard(titan);
            int fromOwn = Math.min(EXCHANGE_SHARDS, count(player, own));
            remove(player, own, fromOwn);
            remove(player, ModItems.RESONANCE_SHARD.get(), EXCHANGE_SHARDS - fromOwn);
        }
    }

    // 分解で戻る共鳴の欠片 (伝説。由来の巨神の欠片)
    public static Item resonanceItem(ItemStack stack) {
        jp.revati.skyabyss.item.TitanGearSet set = TitanGearSets.of(stack.getItem());
        return ModItems.resonanceShard(set == null ? "stone_whale" : set.titan);
    }

    // 持ち物 + 留め袋の数 (費用の足りるかの数え方。docs/design/22 の 8.3)
    public static int count(Player player, Item item) {
        return jp.revati.skyabyss.pouch.Pouch.countAll(player, item);
    }

    // 費用を払う: 留め袋から先に、足りない分を持ち物から
    private static void remove(Player player, Item item, int amount) {
        if (amount > 0) {
            jp.revati.skyabyss.pouch.Pouch.takeAny(player, item, amount);
        }
    }

    public static int resonanceHeld(Player player, String titan) {
        return count(player, ModItems.resonanceShard(titan)) + count(player, ModItems.RESONANCE_SHARD.get());
    }

    // 持ち物だけの数 (留め袋は含まない)
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

    // 強化と分解で使う素材 (由来の巨神の素材)
    public static Item shardItem(ItemStack stack) {
        return TitanGearSets.shardFor(stack);
    }

    // 欠片を使わない費用 (再鍛造) か、岩鯨の鱗片で払う費用
    public static boolean affordable(Player player, Cost cost) {
        return affordable(player, cost, ModItems.STONE_WHALE_SHARD.get());
    }

    public static boolean affordable(Player player, Cost cost, Item shard) {
        return player.isCreative() || (count(player, shard) >= cost.shards()
                && count(player, ModItems.RESONANCE_CRYSTAL.get()) >= cost.crystals());
    }

    // 強化する。できたら true
    public static boolean enhance(Player player, ItemStack stack) {
        if (!canEnhance(stack)) {
            return false;
        }
        int level = ModGear.enhance(stack);
        Cost cost = enhanceCost(level);
        Item shard = shardItem(stack);
        if (!affordable(player, cost, shard)) {
            return false;
        }
        if (!player.isCreative()) {
            remove(player, shard, cost.shards());
            remove(player, ModItems.RESONANCE_CRYSTAL.get(), cost.crystals());
        }
        stack.set(ModDataComponents.ENHANCE.get(), level + 1);
        return true;
    }
}
