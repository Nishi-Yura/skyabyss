package jp.revati.skyabyss.item.wild;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.rpg.skill.Job;

// 野の装備の表の行 (WildGearTable が並べる)
public final class WildGearTypes {
    private WildGearTypes() {
    }

    // 土地 → 強化の素材と分解で戻る欠片を決める由来 (ModGear.dungeon)。土地に近い巨神か、深淵・月・雲海
    public static String forgeOrigin(String region) {
        return switch (region) {
            case "coast" -> "tide_shell";
            case "heights" -> "thunder_wing";
            case "wastes" -> "ember";
            case "abyss_upper", "abyss_lower" -> "abyss";
            case "moon" -> "moon";
            case "cloud" -> "cloudsea";
            default -> "stone_whale";
        };
    }

    // 必要 Lv → 帯 (1〜5)
    public static int band(int level) {
        return level <= 10 ? 1 : level <= 20 ? 2 : level <= 30 ? 3 : level <= 40 ? 4 : 5;
    }

    // 職の武器の基礎の要求値 (レア以上。rpg/skill/GearRequirement と同じ物差し: 岩鯨 Lv 8 = 10 … 深淵 = 24)
    public static int baseRequirement(GearTier tier, int level) {
        if (!tier.atLeast(GearTier.RARE)) {
            return 0;
        }
        return Math.min(24, Math.max(10, 10 + (int) Math.round((level - 8) * 0.67)));
    }

    public interface Row {
        String id();

        GearTier tier();

        int level();

        String region();
    }

    public record Weapon(String id, Job job, String kind, GearTier tier, int level, String region, double attack, double speed, double bow,
            List<WildEffect.Fx> fx) implements Row {
        public boolean isBow() {
            return kind.equals("bow") || kind.equals("longbow");
        }

        public WildEffect.Fx get(WildEffect.Kind k) {
            for (WildEffect.Fx f : fx) {
                if (f.kind() == k) {
                    return f;
                }
            }
            return null;
        }

        public int band() {
            return WildGearTypes.band(level);
        }
    }

    public record ArmorSet(String id, String role, GearTier tier, int level, String region, int[] defense, double toughness,
            List<WildEffect.Fx> set2, List<WildEffect.Fx> set4) implements Row {
        public int band() {
            return WildGearTypes.band(level);
        }
    }

    // 体に描く場所 (client/accessory/AccessoryWornLayer の Mount と同じ並び。part = HEAD / BODY / RIGHT_LEG / LEFT_LEG)
    public record Mount(String part, float x, float y, float z, float rx, float ry, float rz, float scale, float px, float py, float pz) {
    }

    // 装飾の枠の品 (charm = 護符、それ以外は装飾)。cooldown は護符の待ち (tick)
    public record Accessory(String id, boolean charm, GearTier tier, int level, String region, int cooldown, List<WildEffect.Fx> fx, Mount mount)
            implements Row {
        public int band() {
            return WildGearTypes.band(level);
        }
    }
}
