package jp.revati.skyabyss.client.lowpoly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

// 低ポリの防具 (案 C「骨組み」) の選び方 (docs/design/16_armor_design.md 5 章)。
//  - メッシュ: 階級で決まる (コモン・アンコモン = 無印、レア = _rare、ヒロイック・アーティファクト = _legend)
//  - アトラス + 光る画素: 強化段階で決まる (+0〜4 = 基準、+5〜9 = _plus5、+10 = _plus10)。無印メッシュには +5/+10 の絵が無いので基準のまま
// 戻し方: enabled = false にすると 4 組とも元のキューブの geo (geo/armor/<組>_armor.geo.json) に戻る
public final class LowPolyArmor {
    // false: 元のキューブの防具。ShotRunner の `lowpoly armoroff|armoron` で実行中にも切り替えられる
    public static volatile boolean enabled = true;
    // 撮影用: "common" / "rare" / "legend" で階級を決め打つ (null = 品の階級)
    public static volatile String forcedTier = null;
    // 撮影用: 0 以上で強化段階を決め打つ (-1 = 品の強化段階)
    public static volatile int forcedLevel = -1;

    private static final Map<ResourceLocation, Boolean> EXISTS = new ConcurrentHashMap<>();

    private LowPolyArmor() {
    }

    public static String pieceName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            default -> "boots";
        };
    }

    public static String tierSuffix(ItemStack stack) {
        String forced = forcedTier;
        if (forced != null) {
            return switch (forced) {
                case "rare" -> "_rare";
                case "legend", "legendary" -> "_legend";
                default -> "";
            };
        }
        GearTier tier = stack.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
        return switch (tier.meshTier) {
            case "common" -> "";
            case "rare" -> "_rare";
            default -> "_legend";
        };
    }

    public static String levelSuffix(ItemStack stack) {
        int level = forcedLevel >= 0 ? forcedLevel : ModGear.enhance(stack);
        return level >= 10 ? "_plus10" : level >= 5 ? "_plus5" : "";
    }

    // メッシュ名 (lowpoly/armor/<組>/<部位>[_階級])
    public static String meshName(String set, EquipmentSlot slot, ItemStack stack) {
        return "armor/" + set + "/" + pieceName(slot) + tierSuffix(stack);
    }

    // この部位の低ポリの絵 (無ければ基準の絵、それも無ければ null)
    public static ResourceLocation atlas(String set, EquipmentSlot slot, ItemStack stack) {
        String base = "textures/lowpoly/armor/" + set + "/" + pieceName(slot) + tierSuffix(stack);
        ResourceLocation leveled = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, base + levelSuffix(stack) + ".png");
        if (exists(leveled)) {
            return leveled;
        }
        ResourceLocation plain = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, base + ".png");
        return exists(plain) ? plain : null;
    }

    // 低ポリで描けるか (スイッチが入っていて、メッシュが読めている)。キューブ版と切り替える判定
    public static boolean usable(String set, EquipmentSlot slot) {
        return enabled && LowPolyMeshes.get("armor/" + set + "/" + pieceName(slot)) != null;
    }

    private static boolean exists(ResourceLocation id) {
        return EXISTS.computeIfAbsent(id, i -> Minecraft.getInstance().getResourceManager().getResource(i).isPresent());
    }

    static void clearCache() {
        EXISTS.clear();
    }
}
