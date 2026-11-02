package jp.revati.skyabyss.rpg.skill;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.affix.AffixType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 装備の「木の印」(特性) が技の力へ足す割合。手の武器の印 (docs/design/27 の 4.1)。P3 で中身を入れる
public final class SkillMarks {
    private SkillMarks() {
    }

    public static AffixType typeOf(SkillTree tree) {
        return switch (tree) {
            case ROCK -> AffixType.MARK_STONE;
            case WIND -> AffixType.MARK_WIND;
            case TIDE -> AffixType.MARK_TIDE;
            case ASH -> AffixType.MARK_ASH;
            case BONE -> AffixType.MARK_BONE;
            default -> null;
        };
    }

    // 手の武器の、その木の印の割合 (0.06 = +6%)。要るレベル・要る基礎 (48) に届いていない武器は効かない
    public static double power(ServerPlayer player, SkillTree tree) {
        AffixType type = typeOf(tree);
        if (type == null) {
            return 0;
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || AffixRules.slotOf(hand.getItem()) != AffixType.Slot.WEAPON || !GearLevel.meets(player, hand) || !GearRequirement.met(player, hand)) {
            return 0;
        }
        return AffixRules.value(hand, type);
    }
}
