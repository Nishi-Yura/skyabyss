package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// 売った品の控え (2026-11-07 の声「間違って売ってしまったとき用のヘルプもおいて」)。店の画面の「買い戻す」つまみに並ぶ。
// 人ごとに最近の MAX 件 (新しい順)。売ったときの値 (受け取った鐘銭) で買い戻せる。買い戻すと控えから消える。
// どの店でも、売る店 (素材の買い取り) と封切り師の買い取り (装備) の両方を控える。倒れても消えない (永続の印 PERSISTED)
public final class SoldLog {
    public static final int MAX = 10;
    static final String KEY = "skyabyss_sold_log";

    public record Sold(ItemStack stack, int coins) {
    }

    private SoldLog() {
    }

    private static CompoundTag root(Player p) {
        CompoundTag data = p.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static List<Sold> list(ServerPlayer p) {
        List<Sold> out = new ArrayList<>();
        for (Tag t : root(p).getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ItemStack.parse(p.registryAccess(), c.getCompound("item")).ifPresent(s -> out.add(new Sold(s, c.getInt("coins"))));
        }
        return out;
    }

    private static void save(ServerPlayer p, List<Sold> list) {
        ListTag tag = new ListTag();
        for (Sold s : list) {
            if (s.stack().isEmpty()) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.put("item", s.stack().save(p.registryAccess()));
            c.putInt("coins", s.coins());
            tag.add(c);
        }
        root(p).put(KEY, tag);
    }

    // 売った品を控える (新しいものを先頭に。MAX を超えた古いものは消える)
    public static void add(ServerPlayer p, ItemStack stack, int coins) {
        if (stack.isEmpty() || coins < 0) {
            return;
        }
        List<Sold> list = list(p);
        list.add(0, new Sold(stack.copy(), coins));
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        save(p, list);
    }

    public enum Result { OK, NO_ENTRY, NO_COINS, NO_ROOM }

    // 控えの i 番目を買い戻す
    public static Result buyBack(ServerPlayer p, int i) {
        List<Sold> list = list(p);
        if (i < 0 || i >= list.size()) {
            return Result.NO_ENTRY;
        }
        Sold s = list.get(i);
        if (Coins.count(p) < s.coins()) {
            return Result.NO_COINS;
        }
        if (!fits(p, s.stack())) {
            return Result.NO_ROOM;
        }
        if (!Coins.take(p, s.coins())) {
            return Result.NO_COINS;
        }
        ItemStack give = s.stack().copy();
        p.getInventory().add(give);
        if (!give.isEmpty()) {
            p.drop(give, false);
        }
        list.remove(i);
        save(p, list);
        return Result.OK;
    }

    // 持ち物に入るか (空きの枠、または重ねられる枠の残り)
    static boolean fits(ServerPlayer p, ItemStack stack) {
        int left = stack.getCount();
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty()) {
                left -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(s, stack)) {
                left -= s.getMaxStackSize() - s.getCount();
            }
            if (left <= 0) {
                return true;
            }
        }
        return left <= 0;
    }

    public static List<FacilityNetwork.SellView> views(ServerPlayer p) {
        List<FacilityNetwork.SellView> out = new ArrayList<>();
        List<Sold> list = list(p);
        for (int i = 0; i < list.size(); i++) {
            out.add(new FacilityNetwork.SellView(i, list.get(i).stack(), list.get(i).coins(), 0));
        }
        return out;
    }

    // 自己テスト
    public static void clear(ServerPlayer p) {
        root(p).remove(KEY);
    }
}
