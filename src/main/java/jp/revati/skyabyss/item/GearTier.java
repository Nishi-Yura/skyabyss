package jp.revati.skyabyss.item;

import net.minecraft.network.chat.TextColor;

// 装備の階級。名前の色と、ツールチップの見出しに使う
public enum GearTier {
    UNCOMMON("uncommon", 0x7BD66B, 1),
    RARE("rare", 0x5AA9FF, 3),
    LEGENDARY("legendary", 0xF2B53A, 10);

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
