package jp.revati.skyabyss.titan.ember.gear;

import jp.revati.skyabyss.item.GearItem;
import jp.revati.skyabyss.item.GearTier;

// 余燼の装飾品 (熾火の外套・余燼の灯)。装飾の枠に付けているあいだ効く (効果は EmberGearEvents と、熱は EmberGear.heatGain)。
// 由来だけ余燼にした GearItem (潮殻の TideTrinketItem と同じ)
public class EmberTrinketItem extends GearItem {
    public EmberTrinketItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties, tier, descKey, foil);
    }

    @Override
    public String dungeon() {
        return EmberGear.TITAN;
    }
}
