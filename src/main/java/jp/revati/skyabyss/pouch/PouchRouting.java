package jp.revati.skyabyss.pouch;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import jp.revati.skyabyss.SkyAbyss;

// 袋の入口 (docs/design/22 の 4 章)。拾う・この Mod の渡し・取りこぼしの掃除の三つをここにそろえる (Mixin は使わない)。
//   拾う       : ItemEntityPickupEvent.Pre。袋の品なら袋へ入れ、持ち物には入れない。入りきらない分 (上限) は今までどおり持ち物へ
//   渡し       : give(player, stack)。報酬・クエスト・墓や預かりから返す品・呼び戻す品は、すべてここを通す
//   掃除       : 1 秒ごとに持ち物の袋の品を袋へ移す (ほかの mod の箱・取引・コマンド・クリエイティブの品を受け止める)。
//                画面 (箱など) を開いている間はしない。閉じた次の tick に、持ち物とカーソルの品を調べる
//   印         : 取り出し済み (pouch_loan) の品は、掃除が戻さない。拾い直すと袋へ入り、印は外れる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchRouting {
    private PouchRouting() {
    }

    // ---------------------------------------------------------------- この Mod の渡し
    // 袋に入る分は袋へ、残りは持ち物へ入れる。入りきらなかった分 (stack に残る) を返す (空なら全部入った)
    public static ItemStack give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        Pouch.add(player, stack);
        if (!stack.isEmpty()) {
            player.getInventory().add(stack);
        }
        return stack;
    }

    // 足もとまで任せる渡し: 袋 → 持ち物 → 入りきらない分は足もと (本人だけが拾える)
    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        give(player, stack);
        if (!stack.isEmpty()) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
    }

    // 持ち物の「元の場所へ戻す」(placeItemBackInInventory) の代わり: 袋 → 持ち物 → 足もと
    public static void placeBack(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Pouch.add(player, stack);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    // ---------------------------------------------------------------- 拾う
    @SubscribeEvent
    static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemEntity entity = event.getItemEntity();
        // 元の拾える決まり (待ち・取れる人の指定) を満たさないときは何もしない
        if (entity.hasPickUpDelay() || (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID()))) {
            return;
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || PouchEntries.entry(stack) == null || Pouch.data(player).hold) {
            return;
        }
        int moved = Pouch.add(player, stack);
        if (moved <= 0) {
            return;
        }
        player.take(entity, moved);
        // 元の拾い方 (持ち物へ足す) はこちらで済ませる
        event.setCanPickup(TriState.FALSE);
        if (!stack.isEmpty()) {
            // 上限で入りきらなかった分は、元の拾い方どおり持ち物へ
            int rest = stack.getCount();
            player.getInventory().add(stack);
            if (stack.getCount() < rest) {
                player.take(entity, rest - stack.getCount());
            }
        }
        if (stack.isEmpty()) {
            entity.discard();
        } else {
            entity.setItem(stack.copy());
        }
    }

    // ---------------------------------------------------------------- 掃除
    // 画面を閉じた人 (次の tick に掃除する。閉じる処理が持ち物へ戻す品を、戻したあとに見るため)
    private static final Set<UUID> AFTER_CLOSE = new HashSet<>();

    @SubscribeEvent
    static void onClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            synchronized (AFTER_CLOSE) {
                AFTER_CLOSE.add(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !player.isAlive() || player.isSpectator()) {
            return;
        }
        boolean closed;
        synchronized (AFTER_CLOSE) {
            closed = !AFTER_CLOSE.isEmpty() && AFTER_CLOSE.remove(player.getUUID());
        }
        if (closed && player.containerMenu == player.inventoryMenu) {
            Pouch.sweepAfterClose(player);
            return;
        }
        if (player.tickCount % PouchConfig.SWEEP_INTERVAL == 11 && player.containerMenu == player.inventoryMenu) {
            Pouch.sweep(player);
        }
    }
}
