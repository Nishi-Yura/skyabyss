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
// 近接の武器の 1 秒あたりの傷 = (1 + 攻撃の上乗せ) × (4 + 速さの差)。目標の幅に入るよう攻撃の上乗せを足し引きする (手の武器の表示もそのまま変わる)。
// 目標は必要 Lv の目安の線 E(L) (docs/design/51 の 2 章、tools/gen/wild_gear/spec.py の E_PTS と同じ。巨神のエピック相当) に対する倍率 (2026-11-24 の数値の見直し。03 の 1.6):
//   ヒロイック: 同じ由来のエピックの最も強いもの × HEROIC_OVER 以上、かつ E(L) の HEROIC_MIN〜HEROIC_MAX 倍
//   アーティファクト (深淵の伝説)・神器: E(L) × TOP 1.18 (どの Lv でもその Lv のいちばん上。前は Lv によらず 1 秒 19.5 で、Lv10 の伝説が目安の 1.8 倍あった)
// 弓は矢の倍率を同じ倍率で B(L) に合わせる (bowMultiplier。ChapterBowItem が使う)。野の装備 (WildGear) は表の値のまま
// 素の値は品の既定の部品 (DataComponents.ATTRIBUTE_MODIFIERS) から読む
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TierFloor {
    public static final double HEROIC_OVER = 1.08;
    public static final double HEROIC_MIN = 1.06;
    public static final double HEROIC_MAX = 1.12;
    public static final double TOP = 1.18;   // 1.15 では一章の伝説が水路の顎牙 (出血込み) の 1.05 倍に届かなかった
    // 深淵の伝説は同じ由来のヒロイックの最も強いもの × ARTIFACT_OVER 以上 (GearTierSelfTest の決まり)
    public static final double ARTIFACT_OVER = 1.05;
    public static final double ARTIFACT_BOW = 1.25;
    static final java.util.Set<String> TITAN_ORIGINS = java.util.Set.of("stone_whale", "tide_shell", "thunder_wing", "ember", "abyss");
    private static volatile Map<String, Double> heroicBest;
    // 目安の線 (必要 Lv → 巨神のエピック相当の 1 秒あたり / 矢の倍率)。間は直線
    private static final double[][] E_PTS = {{1, 8.0}, {8, 10.0}, {14, 12.6}, {20, 14.6}, {26, 16.0}, {40, 19.4}, {50, 23.0}};
    private static final double[][] B_PTS = {{1, 1.0}, {8, 1.15}, {14, 1.45}, {20, 1.75}, {26, 1.85}, {40, 2.15}, {50, 2.2}};
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

    static double lerp(double[][] pts, double x) {
        if (x <= pts[0][0]) {
            return pts[0][1];
        }
        for (int i = 1; i < pts.length; i++) {
            if (x <= pts[i][0]) {
                double t = (x - pts[i - 1][0]) / (pts[i][0] - pts[i - 1][0]);
                return pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t;
            }
        }
        return pts[pts.length - 1][1];
    }

    // 必要 Lv の目安の 1 秒あたり (近接) と矢の倍率 (弓)
    public static double curve(int level) {
        return lerp(E_PTS, level);
    }

    public static double bowCurve(int level) {
        return lerp(B_PTS, level);
    }

    // 階級の目標の幅 {下, 上} (目安の線への倍率)。幅の無い階級は null
    static double[] band(ModGear g) {
        return switch (g.tier()) {
            // ヒロイックの幅は巨神と深淵の品だけ (月の品は月の段の目安を MoonGearSelfTest が見る)
            case HEROIC -> TITAN_ORIGINS.contains(g.dungeon()) ? new double[]{HEROIC_MIN, HEROIC_MAX} : null;
            case ARTIFACT, MYTHIC -> new double[]{TOP, TOP};
            default -> null;
        };
    }

    // 弓の矢の倍率を階級の幅へ (野の装備と幅の無い階級はそのまま)
    public static double bowMultiplier(Item item, double base) {
        if (!(item instanceof ModGear g) || item instanceof jp.revati.skyabyss.item.wild.WildGear) {
            return base;
        }
        double[] b = band(g);
        if (b == null) {
            return base;
        }
        double ref = bowCurve(g.requiredLevel());
        if (g.tier() == GearTier.ARTIFACT) {
            // 矢の傷は切り上げで段になる。1.18 では一章・四章の伝説の弓が、出血・発動込みのヒロイック (GearTierSelfTest) の 1.05 倍に一段届かない
            b = new double[]{ARTIFACT_BOW, ARTIFACT_BOW};
        }
        return Math.round(Math.min(Math.max(base, ref * b[0]), ref * b[1]) * 100) / 100.0;
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


    // 由来ごとのヒロイックの最も強い 1 秒あたり (床の足し引きの後)
    static Map<String, Double> heroicBest() {
        Map<String, Double> m = heroicBest;
        if (m == null) {
            m = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof ModGear g && g.tier() == GearTier.HEROIC && !(item instanceof jp.revati.skyabyss.item.wild.WildGear)) {
                    double[] v = base(item);
                    if (v != null) {
                        m.merge(g.dungeon(), (1 + v[0] + extra(item)) * (4 + v[1]), Math::max);
                    }
                }
            }
            heroicBest = m;
        }
        return m;
    }

    // 足す攻撃の上乗せ (0 なら足さない。負なら引く)
    public static double extra(Item item) {
        Double cached = EXTRA.get(item);   // computeIfAbsent は使わない (深淵の伝説がヒロイックの値を読むので入れ子になる)
        if (cached != null) {
            return cached;
        }
        double r = compute(item);
        EXTRA.put(item, r);
        return r;
    }

    private static double compute(Item it) {
        {
            // 野の装備は表 (docs/design/51 の 2 章) の値のまま
            if (!(it instanceof ModGear g) || it instanceof jp.revati.skyabyss.item.wild.WildGear) {
                return 0.0;
            }
            double[] v = base(it);
            if (v == null) {
                return 0.0;
            }
            double[] b = band(g);
            if (b == null) {
                return 0.0;
            }
            double ref = curve(g.requiredLevel());
            double now = dps(v);
            double lo = ref * b[0], hi = ref * b[1];
            if (g.tier() == GearTier.HEROIC) {
                lo = Math.min(Math.max(lo, epicBest().getOrDefault(g.dungeon(), 0.0) * HEROIC_OVER), hi);
            } else if (g.tier() == GearTier.ARTIFACT) {
                lo = Math.max(lo, heroicBest().getOrDefault(g.dungeon(), 0.0) * ARTIFACT_OVER);
                hi = Math.max(hi, lo);
            }
            double target = Math.min(Math.max(now, lo), hi);
            if (Math.abs(target - now) < 1e-6) {
                return 0.0;
            }
            return Math.round((target / (4 + v[1]) - (1 + v[0])) * 10) / 10.0;
        }
    }

    @SubscribeEvent
    static void onModifiers(ItemAttributeModifierEvent event) {
        double add = extra(event.getItemStack().getItem());
        if (add != 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(ID, add, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
    }
}
