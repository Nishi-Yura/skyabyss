package jp.revati.skyabyss.item;

import net.minecraft.network.chat.TextColor;

// 装備の階級。名前の色と、ツールチップの見出しに使う (03 の 1.2節: 白・緑・青・金・紫)。
// 特性 (Affix) の数は item/affix/AffixRules。コモンと神器はまだ使う品がない (色と名前だけ先に決めておく)
public enum GearTier {
    COMMON("common", 0xE4E4E4, 0),
    UNCOMMON("uncommon", 0x7BD66B, 1),
    RARE("rare", 0x5AA9FF, 3),
    LEGENDARY("legendary", 0xF2B53A, 10),
    DIVINE("divine", 0xB98CFF, 30);

    public final String key;
    public final TextColor color;
    // 共鳴の炉で分解したときに得られる量の基準
    public final int dismantleValue;

    GearTier(String key, int rgb, int dismantleValue) {
        this.key = key;
        this.color = TextColor.fromRgb(rgb);
        this.dismantleValue = dismantleValue;
    }
}
