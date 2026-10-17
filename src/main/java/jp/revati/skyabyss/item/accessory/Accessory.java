package jp.revati.skyabyss.item.accessory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 装飾の枠に付けられる品。護符 (CHARM) は activate で使い、装飾 (TRINKET) は枠にあるあいだ効く
// (効果は GearEvents などが Accessories.worn で見る)
public interface Accessory {
    AccessoryKind accessoryKind();

    // 護符を使う (サーバー)。待ちの最中などで使えなければ false
    default boolean activate(ServerPlayer player, ItemStack stack) {
        return false;
    }

    // 護符の待ち (tick)。休んでいるときの「あと何秒」の計算に使う
    default int cooldownTicks() {
        return 20 * 60;
    }
}
