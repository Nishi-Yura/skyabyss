package jp.revati.skyabyss.rpg.skill;

import javax.annotation.Nullable;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 武器の基礎の要求値 (docs/design/48 の 2.2、03)。職の武器 (レア以上) は、その職の主な基礎が由来の章の値に届いていないと、
// 近接の攻撃力が半分・木の印が効かない (持てはする。倒れても落とさない決まりはそのまま)。基礎は木の節で上がる (+4 ずつ)
public final class GearRequirement {
    public record Req(Stat stat, int amount) {
    }

    // 由来の巨神 → 要る基礎。木の主な基礎の節だけで届く値 (戦士の筋力は 24 まで)
    public static final int STONE_WHALE = 10;
    public static final int TIDE_SHELL = 14;
    public static final int THUNDER_WING = 18;
    public static final int EMBER = 22;
    public static final int ABYSS = 24;

    private GearRequirement() {
    }

    public static int amountFor(String dungeon) {
        if (jp.revati.skyabyss.abyss.gear.AbyssGear.DUNGEON.equals(dungeon)) {
            return ABYSS;
        }
        return switch (dungeon) {
            case "stone_whale" -> STONE_WHALE;
            case "tide_shell" -> TIDE_SHELL;
            case "thunder_wing" -> THUNDER_WING;
            case "ember" -> EMBER;
            default -> 0;
        };
    }

    // 職の主な基礎 (戦士・鍛冶師 = 筋力、弓使い・暗殺者 = 敏捷、魔法使い・呪術師 = 精神)
    public static Stat statFor(Job job) {
        return switch (job) {
            case WARRIOR, SMITH -> Stat.STRENGTH;
            case ARCHER, ASSASSIN -> Stat.AGILITY;
            case MAGE, SHAMAN -> Stat.SPIRIT;
        };
    }

    // その品の要求値。職の武器でない品、ふつうの階級 (祠の品など)、由来の無い品は null
    @Nullable
    public static Req of(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ModGear gear)) {
            return null;
        }
        Job job = Job.of(stack);
        if (job == null || gear.tier().ordinal() < GearTier.RARE.ordinal()) {
            return null;
        }
        int amount = amountFor(gear.dungeon());
        return amount <= 0 ? null : new Req(statFor(job), amount);
    }

    public static boolean met(Player player, ItemStack stack) {
        Req r = of(stack);
        return r == null || player.isCreative() || Growth.get(player).stat(r.stat) >= r.amount;
    }
}
