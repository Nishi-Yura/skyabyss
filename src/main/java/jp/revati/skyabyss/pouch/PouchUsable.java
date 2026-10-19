package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

// 旅嚢から使える品 (呼び笛・呼び貝・潮見の貝・風見鶏・伝承の書・破れた頁)。docs/design/22 の 10 章。
// 右クリック (Item.use) と旅嚢の輪・短押し (PouchUse パケット) は、どちらも同じ usePouch の中身を通る。待ち・条件・音・効果は同じ
public interface PouchUsable {
    // 使う。使えたら true。断られた (条件を満たさない・待ち) ら false。
    // 品を使い切るもの (破れた頁) は、使えたときに consumeOne を呼ぶ (右クリックは手の品を一つ減らし、旅嚢からは袋の数を一つ減らす)
    boolean usePouch(ServerPlayer player, Runnable consumeOne);

    enum Result {
        // 使えた
        OK,
        // 断られた (条件を満たさない。理由は品の知らせが出す)
        REFUSED,
        // 袋にない・使える品でない・袋が切のとき
        NONE,
        // 待ち (クールダウン) の最中
        COOLDOWN
    }

    // 袋にあって、使える品 (輪の並び = 袋の登録の順)
    static List<PouchEntries.Entry> usable(Player player) {
        List<PouchEntries.Entry> out = new ArrayList<>();
        for (PouchEntries.Entry e : PouchEntries.enabled()) {
            if (e.item() instanceof PouchUsable && Pouch.count(player, e.item()) > 0) {
                out.add(e);
            }
        }
        return out;
    }

    // 旅嚢の品を使う共通の入口 (PouchUse パケット・自己テスト)。サーバーで呼ぶ
    static Result usePouch(ServerPlayer player, Item item) {
        PouchEntries.Entry e = PouchEntries.entry(item);
        if (e == null || !(item instanceof PouchUsable usable) || Pouch.count(player, item) <= 0 || !PouchConfig.enabled(player.server)) {
            return Result.NONE;
        }
        if (player.getCooldowns().isOnCooldown(item)) {
            return Result.COOLDOWN;
        }
        return usable.usePouch(player, () -> Pouch.take(player, item, 1)) ? Result.OK : Result.REFUSED;
    }
}
