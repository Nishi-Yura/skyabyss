package jp.revati.skyabyss.pouch;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.grave.GraveData;
import jp.revati.skyabyss.grave.Graves;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 既存セーブの移し替え (docs/design/22 の 8.4)。ログインのとき、袋ができる前から持っていた mod の品を、一度だけ袋へ移す。
//   移す先の元: 持ち物 (メイン・左手)、エンダーチェスト、報酬の預かり (TitanMemoryData)、倒れたときの預かり (skyabyss_death_keep)、
//               背中で倒れたときの預かり (skyabyss_kept・skyabyss_kept_extra)、自分の墓の中身
//   そのまま     : 箱・樽・シュルカーに入れた分、他人の墓の分 (取り出せば掃除が袋へ移す)。装飾の枠 (22 の 2.2)。防具
//   一回だけ     : PouchData.migrated が MIGRATION_VERSION に届くまで。袋の版を上げれば、もう一度流れる
//   知らせ       : 移した品があったときだけ、一行 (チャットに出さない札)。移した数は記録 (ログ) に残す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchMigration {
    private PouchMigration() {
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            runOnce(player);
        }
    }

    // まだなら流す。移した数を返す (済みなら -1)
    public static int runOnce(ServerPlayer player) {
        PouchData data = Pouch.data(player);
        if (!PouchConfig.enabled(player.server) || data.migrated >= PouchData.MIGRATION_VERSION) {
            return -1;
        }
        return run(player);
    }

    // 流して「済み」の印を付ける。移した品の数を返す
    public static int run(ServerPlayer player) {
        if (!PouchConfig.enabled(player.server)) {
            return 0;
        }
        int moved = 0;
        Inventory inv = player.getInventory();
        for (List<ItemStack> part : List.of(inv.items, inv.offhand)) {
            for (ItemStack s : part) {
                moved += move(player, s);
            }
        }
        inv.setChanged();
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) {
            moved += move(player, ender.getItem(i));
        }
        ender.setChanged();
        // 報酬の預かり
        TitanMemoryData memory = TitanMemoryData.get(player.server);
        if (memory.hasPending(player.getUUID())) {
            TitanMemoryData.Pending pending = memory.pending(player.getUUID());
            for (ItemStack s : pending.items) {
                moved += move(player, s);
            }
            pending.items.removeIf(ItemStack::isEmpty);
        }
        // 倒れたときの預かり (Persisted)
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        HolderLookup.Provider registries = player.registryAccess();
        moved += moveList(player, registries, persisted.getCompound("skyabyss_death_keep").getList("Inventory", Tag.TAG_COMPOUND));
        moved += moveList(player, registries, persisted.getCompound("skyabyss_kept").getList("Inventory", Tag.TAG_COMPOUND));
        moved += moveList(player, registries, persisted.getCompound("skyabyss_kept_extra").getList("Items", Tag.TAG_COMPOUND));
        // 自分の墓
        for (GraveData.Grave grave : GraveData.get(player.server).owned(player.getUUID())) {
            int before = moved;
            for (ItemStack s : grave.items) {
                moved += move(player, s);
            }
            if (moved > before) {
                Graves.settle(player.server, grave);
            }
        }
        Pouch.data(player).migrated = PouchData.MIGRATION_VERSION;
        Pouch.sync(player);
        player.containerMenu.broadcastChanges();
        if (moved > 0) {
            Notice.hint(player, "skyabyss_pouch.migrated", net.minecraft.network.chat.Component.translatable("skyabyss_pouch.name"), moved);
            SkyAbyss.LOGGER.info("[pouch] migrated {} item(s) into {}'s pouch", moved, player.getGameProfile().getName());
        }
        return moved;
    }

    // 一つの品を袋へ (入る分だけ。stack は入れた数だけ減る)
    private static int move(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || PouchEntries.entry(stack) == null) {
            return 0;
        }
        return Pouch.add(player, stack);
    }

    // 保存の形の品の並び (預かり) から、袋へ移せる品を移す。残る分は書き直し、空になった分は並びから外す
    private static int moveList(ServerPlayer player, HolderLookup.Provider registries, ListTag list) {
        int moved = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!(list.get(i) instanceof CompoundTag tag)) {
                continue;
            }
            ItemStack stack = ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
            int n = move(player, stack);
            if (n <= 0) {
                continue;
            }
            moved += n;
            if (stack.isEmpty()) {
                list.remove(i);
            } else {
                CompoundTag rewritten = (CompoundTag) stack.save(registries, new CompoundTag());
                if (tag.contains("Slot")) {
                    rewritten.putByte("Slot", tag.getByte("Slot"));
                }
                list.set(i, rewritten);
            }
        }
        return moved;
    }
}
