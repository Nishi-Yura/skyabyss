package jp.revati.skyabyss.item.storm;

import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;

// 雷翼の装飾品 (嵐纏の足環・嵐の目の羽根)。装飾の枠に付けているあいだ効く (効果は StormGearEvents)。由来だけ雷翼にした GearItem
public class StormTrinketItem extends GearItem {
    public StormTrinketItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties, tier, descKey, foil);
    }

    @Override
    public String dungeon() {
        return StormGear.TITAN;
    }
}
