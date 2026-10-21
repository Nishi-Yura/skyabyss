package jp.revati.skyabyss.titan;

import net.minecraft.server.level.ServerLevel;

// 巨神の背の中では、道具・武器・防具の耐久が減らない (ブロックを置けない所。2026-10-22 の二人の試遊)。決まりは mixin/ItemStackDurabilityMixin
public final class BackDurability {
    private BackDurability() {
    }

    // その次元で耐久を減らさないか (四体の背)
    public static boolean keeps(ServerLevel level) {
        return level != null && jp.revati.skyabyss.progress.Titans.titanOfBack(level.dimension()) != null;
    }
}
