package jp.revati.skyabyss.item;

import java.util.HashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

// 階級の強さの床 (2026-10-27 の声「元からあったやつがヒロイックになって変に見える」: 中ボスの逸品 (ヒロイック) が、同じ巨神の章の武器 (エピック) より弱いものがあった)。
// 近接の武器の 1 秒あたりの傷 = (1 + 攻撃の上乗せ) × (4 + 速さの差)。足りなければ攻撃の上乗せを足す (手の武器の表示もそのまま変わる)。
//   ヒロイック: 同じ由来のエピックの最も強いもの × HEROIC_OVER 以上
//   アーティファクト: ARTIFACT_DPS 以上 (伝説はどれも 1 秒 19.5 前後にそろえる)
// 素の値は品の既定の部品 (DataComponents.ATTRIBUTE_MODIFIERS) から読む。弓 (攻撃の上乗せが無い品) は触らない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TierFloor {
    public static final double HEROIC_OVER = 1.08;
    public static final double ARTIFACT_DPS = 19.5;
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tier_floor");
    private static volatile Map<String, Double> epicBest;
    private static final Map<Item, Double> EXTRA = new java.util.concurrent.ConcurrentHashMap<>();

    private TierFloor() {
    }

    // 品の素の {攻撃の上乗せ, 速さの差}。近接の武器でなければ null
    static double[] base(Item item) {
        ItemAttributeModifiers mods = item.components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return null;
        }
        double[] v = {0, 0};
        boolean any = false;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE || !e.slot().test(net.minecraft.world.entity.EquipmentSlot.MAINHAND)) {
                continue;
            }
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                v[0] += e.modifier().amount();
                any = true;
            } else if (e.attribute().is(Attributes.ATTACK_SPEED)) {
                v[1] += e.modifier().amount();
            }
        }
        return any && v[0] > 0 ? v : null;
    }

    public static double dps(double[] v) {
        return (1 + v[0]) * (4 + v[1]);
    }

    static Map<String, Double> epicBest() {
        Map<String, Double> m = epicBest;
        if (m == null) {
            m = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof ModGear g && g.tier() == GearTier.EPIC && !(item instanceof jp.revati.skyabyss.item.wild.WildGear)) {
                    double[] v = base(item);
                    if (v != null) {
                        m.merge(g.dungeon(), dps(v), Math::max);
                    }
                }
            }
            epicBest = m;
        }
        return m;
    }

    public static double epicBestOf(String dungeon) {
        return epicBest().getOrDefault(dungeon, 0.0);
    }

    // 足す攻撃の上乗せ (0 なら足さない)
    public static double extra(Item item) {
        return EXTRA.computeIfAbsent(item, it -> {
            // 野の装備は表 (docs/design/51 の 2 章) の値のまま
            if (!(it instanceof ModGear g) || it instanceof jp.revati.skyabyss.item.wild.WildGear) {
                return 0.0;
            }
            double[] v = base(it);
            if (v == null) {
                return 0.0;
            }
            double target = switch (g.tier()) {
                case HEROIC -> epicBest().getOrDefault(g.dungeon(), 0.0) * HEROIC_OVER;
                case ARTIFACT -> ARTIFACT_DPS;
                default -> 0.0;
            };
            double now = dps(v);
            if (target <= now) {
                return 0.0;
            }
            return Math.round((target / (4 + v[1]) - (1 + v[0])) * 10) / 10.0;
        });
    }

    @SubscribeEvent
    static void onModifiers(ItemAttributeModifierEvent event) {
        double add = extra(event.getItemStack().getItem());
        if (add > 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(ID, add, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
    }
}
