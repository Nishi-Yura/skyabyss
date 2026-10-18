package jp.revati.skyabyss.lore;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 伝承の書の入口。どこからでも Lore.fire / Lore.unlock を呼べば、条件に合う項目が書に記される。
// 記されたら本人にだけ控えめな知らせを送る。記録はプレイヤーごと (マルチでは人によって書の中身が違う)
public final class Lore {
    // 伝承の書そのものの項目 (書を手にしたときに記される)
    public static final String BOOK_ENTRY = "prologue/the_book";

    private Lore() {
    }

    public static LoreState state(Player player) {
        return player.getData(LoreSetup.STATE);
    }

    // 種類 type の条件のうち、match に合うものを持つ項目を、まだ記されていなければ記す。記した数を返す
    public static int fire(ServerPlayer player, String type, Predicate<LoreTrigger> match) {
        LoreState state = state(player);
        List<String> hits = new ArrayList<>();
        for (LoreEntry entry : LoreManager.catalog().entries()) {
            if (!entry.unlockable() || state.has(entry.id())) {
                continue;
            }
            for (LoreTrigger t : entry.unlock()) {
                if (t.type().equals(type) && match.test(t)) {
                    hits.add(entry.id());
                    break;
                }
            }
        }
        int count = 0;
        for (String id : hits) {
            if (unlock(player, id, true)) {
                count++;
            }
        }
        return count;
    }

    // 項目を記す。すでに記されている・存在しない・枠だけの項目なら false
    public static boolean unlock(ServerPlayer player, String id, boolean notify) {
        LoreEntry entry = LoreManager.catalog().entry(id);
        if (entry == null || !entry.unlockable()) {
            return false;
        }
        LoreState state = state(player);
        if (!state.unlocked.add(id)) {
            return false;
        }
        if (notify) {
            send(player, new LoreNetwork.Unlocked(id));
            // 序盤の「はじめて見た物」の経験値 (17 の 5.2、04 の 3.2)。一人一回
            jp.revati.skyabyss.rpg.GrowthRewards.onLore(player, id);
        }
        jp.revati.skyabyss.advancement.Beats.fire(player, "lore:" + id);
        ensureBook(player, state);
        return true;
    }

    // 記録から消す (コマンド用)
    public static boolean lock(ServerPlayer player, String id) {
        LoreState state = state(player);
        state.seen.remove(id);
        return state.unlocked.remove(id);
    }

    // 記録をすべて消す (コマンド用。書を受け取った記録も消すので、空の影から試し直せる)
    public static void reset(ServerPlayer player) {
        LoreState state = state(player);
        state.unlocked.clear();
        state.seen.clear();
        state.bookGiven = false;
        state.bookDropAt = -1;
        LoreEvents.forgetDrop(player);
    }

    public static void markSeen(ServerPlayer player, String id) {
        LoreState state = state(player);
        if (state.has(id)) {
            state.seen.add(id);
        }
    }

    public static void sync(ServerPlayer player) {
        LoreState state = state(player);
        send(player, new LoreNetwork.Sync(LoreManager.catalog(), List.copyOf(state.unlocked), List.copyOf(state.seen)));
    }

    // 何かが記されたとき、書を持っていなければ渡す。
    //  - まだ一度も受け取っていない (空の影より先に巨神に乗った人など): 荷物の中に現れる。
    //    空の影のあとで書が落ちてくる予定のときは、そちらを待つ
    //  - 受け取ったのに手元にない (なくした・置いてきた): 持ち物にもエンダーチェストにもなければ、荷物の中に戻る
    //  - 倒れている人には渡さない (岩鯨の背では持ち物が預かられているので、手元にないように見える)
    static void ensureBook(ServerPlayer player, LoreState state) {
        // 落ちてきた書がまだ地面にあるあいだも渡さない (拾えば済む)
        if (!player.isAlive() || LoreEvents.hasPendingDrop(player)) {
            return;
        }
        if (!state.bookGiven) {
            if (hasBook(player)) {
                // ほかの道 (クリエイティブなど) で先に手にしていた
                state.bookGiven = true;
                state.bookDropAt = -1;
                return;
            }
            if (state.bookDropAt >= 0) {
                return;
            }
            giveBook(player, "message.skyabyss.lore.book_appeared");
            return;
        }
        if (!hasBook(player)) {
            giveBook(player, "message.skyabyss.lore.book_returned");
        }
    }

    static void giveBook(ServerPlayer player, @Nullable String messageKey) {
        LoreState state = state(player);
        state.bookGiven = true;
        state.bookDropAt = -1;
        ItemStack book = new ItemStack(LoreSetup.LORE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        if (messageKey != null) {
            Notice.narrate(player, messageKey);
        }
        unlock(player, BOOK_ENTRY, true);
    }

    public static boolean hasBook(Player player) {
        return contains(player.getInventory().items, LoreSetup.LORE_BOOK.get())
                || contains(player.getInventory().offhand, LoreSetup.LORE_BOOK.get())
                || containsEnder(player);
    }

    private static boolean containsEnder(Player player) {
        var ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            if (ender.getItem(i).is(LoreSetup.LORE_BOOK.get())) {
                return true;
            }
        }
        return false;
    }

    static boolean contains(List<ItemStack> stacks, Item item) {
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }
}
