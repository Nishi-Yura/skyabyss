package jp.revati.skyabyss.item.wild;

import jp.revati.skyabyss.item.ModGear;

// 野の装備 (docs/design/51): 物語の報酬とは別に、土地の敵・名のある強敵・層の主・町の店から出る武器 102 本と防具 18 式。
// 品の表は WildGearTable (生成)、登録と引きは WildGearSetup、能力は WildGearEvents・WildArmorEvents、落とし物と店は WildLoot。
// 由来 (dungeon) は強化の素材を決めるだけで、土地に近い巨神か深淵・月・雲海 (WildGearTypes.forgeOrigin)。必要 Lv は表の値そのまま
public interface WildGear extends ModGear {
    String region();

    int wildLevel();

    @Override
    default String dungeon() {
        return WildGearTypes.forgeOrigin(region());
    }

    @Override
    default int requiredLevel() {
        return wildLevel();
    }
}
