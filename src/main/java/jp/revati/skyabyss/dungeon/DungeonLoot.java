package jp.revati.skyabyss.dungeon;

import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.item.outfit.ConsumableGear;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

// ダンジョンの道中の宝箱の中身 (docs/design/55 の 2 章「道中の宝箱は人ごとに 20 分」)。中身は箱の見本になり、人ごとに写しを受け取る (loot/SharedChests)。
//   鐘銭 (目安 Lv で増える)・道の糧・その土地の巨神の粉。二層目の箱は粉が二つ
public final class DungeonLoot {
    private DungeonLoot() {
    }

    static void fill(ChestBlockEntity chest, Dungeon d, int floor, RandomSource r) {
        chest.clearContent();
        chest.setItem(4, new ItemStack(FacilitySetup.BELL_COIN.get(), d.level / 2 + 4 + r.nextInt(6)));
        chest.setItem(10, new ItemStack(ConsumableGear.TOWN_BREAD.get(), 2 + r.nextInt(3)));
        chest.setItem(16, new ItemStack(ModItems.socketShard(d.element).get(), floor >= 1 ? 2 : 1));
        chest.setChanged();
    }
}
