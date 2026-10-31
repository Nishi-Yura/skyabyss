package jp.revati.skyabyss.loot;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.affix.AffixRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 共鳴の欠片での交換 (03 の 2.4節・8.1節) を巨神ごとに。決まりは岩鯨と同じ:
//  その巨神の共鳴の欠片 30 で、その巨神の専用の逸品のうち持っていない品を一つ。その巨神の心臓を倒すごとに一度。天井は戻さない。
// 記録はその巨神の巨神の記憶 (TitanMemoryData の set.titan)。共鳴の欠片は巨神ごと (古い共通の欠片はどの巨神にも使える)
public final class GearExchange {
    private GearExchange() {
    }

    public static List<String> missing(TitanGearSet set, TitanMemoryData.Record r) {
        return set.exclusives().stream().filter(i -> TitanGearSet.registered(i) && !r.owned.contains(i)).toList();
    }

    public static ForgeRules.ExchangeState state(TitanGearSet set, @Nullable TitanMemoryData.Record r) {
        if (r == null || r.clears <= 0) {
            return ForgeRules.ExchangeState.NO_CLEAR;
        }
        if (missing(set, r).isEmpty()) {
            return ForgeRules.ExchangeState.ALL_OWNED;
        }
        return r.lastExchangeClears >= r.clears ? ForgeRules.ExchangeState.USED : ForgeRules.ExchangeState.READY;
    }

    // 炉の画面に見せる: 持っている専用の逸品の印 (1 ビットずつ、set.exclusives() の順)
    public static int ownedMask(TitanGearSet set, @Nullable TitanMemoryData.Record r) {
        int mask = 0;
        List<String> all = set.exclusives();
        for (int i = 0; i < all.size(); i++) {
            if (r != null && r.owned.contains(all.get(i))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    @Nullable
    public static String denial(TitanGearSet set, TitanMemoryData.Record r, String item, boolean affordable) {
        return switch (state(set, r)) {
            case NO_CLEAR -> "message.skyabyss.exchange.no_clear";
            case USED -> "message.skyabyss.exchange.used";
            case ALL_OWNED -> "message.skyabyss.exchange.all_owned";
            case READY -> !set.isExclusive(item) || !TitanGearSet.registered(item) || r.owned.contains(item) ? "message.skyabyss.exchange.owned"
                    : !affordable ? "message.skyabyss.exchange.no_shards" : null;
        };
    }

    // 持ち物にある図鑑の品を図鑑に記す (図鑑ができる前に手にした品の分)
    public static void syncOwned(ServerPlayer player, TitanGearSet set, TitanMemoryData.Record r) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String id = TitanLoot.id(stack.getItem());
                if (set.contains(id)) {
                    r.owned.add(id);
                }
            }
        }
    }

    // 交換する。払う・図鑑に記す・渡す。できたら true
    public static boolean exchange(ServerPlayer player, TitanGearSet set, String itemId) {
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).record(player.getUUID(), set.titan);
        syncOwned(player, set, r);
        String why = denial(set, r, itemId, ForgeRules.exchangeAffordable(player, set.titan));
        Optional<Item> item = TitanGearSet.item(itemId);
        if (why != null || item.isEmpty()) {
            Notice.warn(player, why == null ? "message.skyabyss.exchange.owned" : why);
            return false;
        }
        ForgeRules.payExchange(player, set.titan);
        r.owned.add(itemId);
        r.lastExchangeClears = r.clears;
        r.exchanges++;
        ItemStack stack = AffixRules.withAffixes(new ItemStack(item.get()), player.getRandom());
        int held = jp.revati.skyabyss.loot.box.LootBoxes.direct(() -> TitanLoot.handOver(player.server, player.getUUID(), player, List.of(stack), 0));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        Notice.alert(player, "message.skyabyss.exchange.done", TitanLoot.itemLink(stack));
        TitanLoot.tellHeld(player, held);
        return true;
    }
}
