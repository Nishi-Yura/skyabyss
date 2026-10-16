package jp.revati.skyabyss.titan;

import java.util.Set;

import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// 巨神の背中のディメンションの一覧。背中の決まり (TitanBackRules) はこの一覧のどれにも効く。
// 岩鯨の背 (titan_back) と潮殻の背 (tide_back)。後の巨神は、ここに一行足す
public final class TitanBacks {
    private static final Set<ResourceKey<Level>> BACKS = Set.of(TitanTravel.TITAN_BACK, TideTravel.TIDE_BACK,
            jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK);

    private TitanBacks() {
    }

    public static boolean isBack(ResourceKey<Level> dimension) {
        return BACKS.contains(dimension);
    }

    // 巨神ごとの追加の禁止 (08 の 8章)。潮殻の背では舟を置けない (満潮の水路を舟で渡ると、潮の時間読みがなくなる)
    public static boolean extraBanned(ResourceKey<Level> dimension, ItemStack stack) {
        return dimension == TideTravel.TIDE_BACK && stack.getItem() instanceof BoatItem;
    }
}
