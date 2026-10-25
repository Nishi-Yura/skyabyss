package jp.revati.skyabyss.item.extra;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// 巨神の粉 (docs/design/41。2026-10-25 に A に決まった)。前の「属性の欠片」(Element・ElementShard) を、段 (I〜III) のある粉にしたもの。
//   - 段は品の CUSTOM_DATA の "powder_tier" (無ければ I)。穴に差すと Gem の milli に段を写す (0 は I)
//   - 同じ段の粉三つで、一つ上の段の粉一つ (手に持って使う)。効き目の数字は段の数だけ掛ける (I ×1・II ×2・III ×3)
//   - 粉の技: 一つの装備に差した粉の段を属性ごとに足し、WEIGHT_FOR_ART 以上なら、その属性の粉の技が働く (PowderArts)。
//     穴は二つまでなので、III と I、II と II などで届く
public final class Powder {
    public static final String TIER_KEY = "powder_tier";
    public static final int MAX_TIER = 3;
    public static final int COMBINE = 3;
    public static final int WEIGHT_FOR_ART = 4;

    private Powder() {
    }

    public static int tier(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        int t = d == null ? 1 : d.copyTag().getInt(TIER_KEY);
        return Math.max(1, Math.min(MAX_TIER, t == 0 ? 1 : t));
    }

    public static ItemStack withTier(ItemStack stack, int tier) {
        int t = Math.max(1, Math.min(MAX_TIER, tier));
        if (t == 1) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return stack;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TIER_KEY, t);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    // 穴の中の段 (milli が 0 なら I)
    public static int tier(Gem gem) {
        return Math.max(1, Math.min(MAX_TIER, gem.milli() <= 0 ? 1 : gem.milli()));
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    // 装備の粉の重さ (属性ごとの段の和)
    public static Map<Element, Integer> weights(ItemStack gear) {
        Map<Element, Integer> out = new EnumMap<>(Element.class);
        for (Gem g : GearFreedom.extras(gear).gems()) {
            Element e = g.isShard() ? Element.byId(g.arg()) : null;
            if (e != null) {
                out.merge(e, tier(g), Integer::sum);
            }
        }
        return out;
    }

    // 粉の技が働く属性 (なければ null。二つ届くことはない: 穴は二つまで)
    @Nullable
    public static Element art(ItemStack gear) {
        for (Map.Entry<Element, Integer> e : weights(gear).entrySet()) {
            if (e.getValue() >= WEIGHT_FOR_ART) {
                return e.getKey();
            }
        }
        return null;
    }
}
