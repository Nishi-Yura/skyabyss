package jp.revati.skyabyss.facility;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

// 封切り師の買い取り (2026-11-01「アイテム売ってお金にできる機能を箱空ける NPC に」。docs/design/52 の 5 章)。
// 店の画面 (ShopScreen) を「売る」だけで開く (店の id は ID。ShopService が開く・売るを振り分ける)。
//   買う品   持ち物 (36 枠) の Mod の装備 (武器・防具・装飾。ModGear)。身につけている防具・装飾の枠の品は出ない。
//            アーティファクト (神器・深淵の伝説) と、取引に出せない品 (守る品・旅嚢の品・頼みの印) は買わない
//   値       階級の元値 × (1 + 必要 Lv / 10)。元値はコモン 3・アンコモン 6・レア 14・エピック 30・ヒロイック 60
//   一つ押すと 1 つ、シフトで その枠を全部
public final class Buyback {
    public static final String ID = "buyback";
    public static final String TITLE = "gui.skyabyss.buyback.title";
    public static final int INV = 36;

    private Buyback() {
    }

    public static int base(GearTier tier) {
        return switch (tier) {
            case COMMON -> 3;
            case UNCOMMON -> 6;
            case RARE -> 14;
            case EPIC -> 30;
            case HEROIC -> 60;
            default -> 0;
        };
    }

    // 一つの値 (買わない品は 0)
    public static int price(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof ModGear g) || !jp.revati.skyabyss.facility.trade.Trades.allowed(s)) {
            return 0;
        }
        int b = base(g.tier());
        if (b <= 0) {
            return 0;
        }
        int lv = Math.max(1, GearLevel.required(s));
        return (int) Math.round(b * (1 + lv / 10.0));
    }

    public static FacilityNetwork.ShopView view(ServerPlayer player) {
        List<FacilityNetwork.SellView> sells = new ArrayList<>();
        List<ItemStack> inv = player.getInventory().items;
        for (int i = 0; i < Math.min(INV, inv.size()); i++) {
            ItemStack s = inv.get(i);
            int p = price(s);
            if (p > 0) {
                sells.add(new FacilityNetwork.SellView(i, s.copy(), p, s.getCount()));
            }
        }
        return new FacilityNetwork.ShopView(ID, TITLE, Coins.count(player), List.of(), sells);
    }

    // 枠 slot の品を times 個まで売る。売った数 (0 = 売れない)
    public static int sell(ServerPlayer player, int slot, int times) {
        List<ItemStack> inv = player.getInventory().items;
        if (slot < 0 || slot >= Math.min(INV, inv.size())) {
            return 0;
        }
        ItemStack s = inv.get(slot);
        int p = price(s);
        if (p <= 0) {
            return 0;
        }
        int n = Math.min(Math.max(1, times), s.getCount());
        s.shrink(n);
        player.getInventory().setChanged();
        Coins.give(player, n * p);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.5f, 1.1f);
        return n;
    }
}
