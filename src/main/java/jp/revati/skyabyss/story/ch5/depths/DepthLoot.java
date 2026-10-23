package jp.revati.skyabyss.story.ch5.depths;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.abyss.mobs.AbyssMobsA;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.story.ch5.AbyssRelics;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

// 深みの箱の中身と、行商の品 (docs/design/36 の 9)。深さ (階) で量が増える。急な段差はない。
//   箱: 鐘銭・A 組の素材 (骨片・塩の結晶・雷の銅)・回復 (金のリンゴ)・経験の瓶。宝の間・敵の巣・王の褒美は遺品 (前の私の落とし物) の機会がある。
//   行商: 鐘銭か深淵の素材 (骨片・塩の結晶・雷の銅のどれか) で買える。
public final class DepthLoot {
    private DepthLoot() {
    }

    // 箱一つぶん。tier: 0 ふつう・1 宝の間・2 敵の巣・3 王の褒美
    public static List<ItemStack> roll(int floor, int tier, Random r) {
        List<ItemStack> out = new ArrayList<>();
        int coins = 2 + r.nextInt(3) + floor / 4 + tier * 3;
        out.add(new ItemStack(FacilitySetup.BELL_COIN.get(), Math.min(60, coins)));
        int mats = 1 + r.nextInt(2) + floor / 15 + tier;
        for (int i = 0; i < mats; i++) {
            out.add(new ItemStack(material(r), 1 + r.nextInt(2) + floor / 20));
        }
        if (r.nextDouble() < 0.30 + 0.15 * tier) {
            out.add(new ItemStack(Items.GOLDEN_APPLE, 1));
        }
        if (r.nextDouble() < 0.5) {
            out.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + r.nextInt(3)));
        }
        double relic = switch (tier) {
            case 1 -> 0.5;
            case 2 -> 0.35;
            case 3 -> 1.0;
            default -> 0.0;
        };
        if (r.nextDouble() < relic && !AbyssRelics.RELICS.isEmpty()) {
            out.add(new ItemStack(AbyssRelics.RELICS.get(r.nextInt(AbyssRelics.RELICS.size())).item().get()));
        }
        if (tier >= 1 && floor >= 10 && r.nextDouble() < 0.25) {
            out.add(new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        // 深淵の装備: 錨鉄と、図鑑の品の機会 (abyss/gear/AbyssGearLoot)
        jp.revati.skyabyss.abyss.gear.AbyssGearLoot.chest(out, floor, tier, r);
        return out;
    }

    public static void fill(Container c, int floor, int tier, Random r) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots, r);
        int k = 0;
        for (ItemStack s : roll(floor, tier, r)) {
            if (k < slots.size()) {
                c.setItem(slots.get(k++), s);
            }
        }
    }

    public static Item material(Random r) {
        return switch (r.nextInt(3)) {
            case 0 -> AbyssMobsA.BONE_CHIP.get();
            case 1 -> AbyssMobsA.SALT_CRYSTAL.get();
            default -> AbyssMobsA.STORM_COPPER.get();
        };
    }

    // 行商の品: 品ごとに、鐘銭で買う申し出と素材で買う申し出の二つ。値は階が深いほどゆっくり上がる
    public static MerchantOffers offers(int floor) {
        MerchantOffers o = new MerchantOffers();
        int up = floor / 6;
        ware(o, new ItemStack(Items.GOLDEN_APPLE, 1), 8 + up, 3 + up / 2);
        ware(o, new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 6 + up, 2 + up / 2);
        ware(o, new ItemStack(Items.ARROW, 16), 4 + up / 2, 2);
        if (floor >= 8) {
            ware(o, new ItemStack(Items.TOTEM_OF_UNDYING, 1), 48 + up * 2, 14 + up);
        }
        return o;
    }

    private static void ware(MerchantOffers o, ItemStack result, int coins, int mats) {
        o.add(new MerchantOffer(new ItemCost(FacilitySetup.BELL_COIN.get(), coins), result.copy(), 3, 0, 0f));
        o.add(new MerchantOffer(new ItemCost(AbyssMobsA.BONE_CHIP.get(), mats), result.copy(), 3, 0, 0f));
        o.add(new MerchantOffer(new ItemCost(AbyssMobsA.SALT_CRYSTAL.get(), mats), result.copy(), 3, 0, 0f));
        o.add(new MerchantOffer(new ItemCost(AbyssMobsA.STORM_COPPER.get(), mats), result.copy(), 3, 0, 0f));
    }
}
