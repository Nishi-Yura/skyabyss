package jp.revati.skyabyss.titan.tide.gear;

import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;

// 潮殻の装飾品 (月潮の真珠)。装飾の枠に付けているあいだ効く (効果は TideGearEvents)。由来だけ潮殻にした GearItem
public class TideTrinketItem extends GearItem {
    public TideTrinketItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties, tier, descKey, foil);
    }

    @Override
    public String dungeon() {
        return TideGear.TITAN;
    }
}
