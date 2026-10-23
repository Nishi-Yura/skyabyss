package jp.revati.skyabyss.abyss.gear;

import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;

// 深淵の装飾品 (鼓動の聴き筒・測量士の錘)。装飾の枠に付けているあいだ効く (効果は AbyssGearEvents)。由来だけ深淵にした GearItem
public class AbyssTrinketItem extends GearItem {
    public AbyssTrinketItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties, tier, descKey, foil);
    }

    @Override
    public String dungeon() {
        return AbyssGear.DUNGEON;
    }

    @Override
    public int requiredLevel() {
        return AbyssGear.requiredLevel(tier());
    }
}
