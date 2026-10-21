package jp.revati.skyabyss.facility.guild;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.facility.StashMenu;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// ギルドの倉庫 (docs/design/35 の 6.5)。ギルドごとに 54 枠 (GuildData.Guild.storage)。組合の受付の書記から、ギルドの画面の「倉庫」で開く。
//   預けるのは誰でも (預かり所と同じく、旅嚢の大事な品と守る品は預けられない = StashMenu.allowed)
//   取り出すのは、長が決めた位から (takeRank: 誰でも / 副長から / 長だけ)。取り出せない人には、枠の品をつかめないようにする
//   何人かが同時に開いても、中身は一つ (同じ枠の並びを見る)
public final class GuildStorage extends ChestMenu {
    @Nullable
    private final Entity clerk;
    private final GuildData.Guild guild;
    private final Player owner;

    private GuildStorage(int id, Inventory inventory, Container box, GuildData.Guild guild, @Nullable Entity clerk) {
        super(MenuType.GENERIC_9x6, id, inventory, box, 6);
        this.clerk = clerk;
        this.guild = guild;
        this.owner = inventory.player;
        for (int i = 0; i < GuildData.STORAGE_SIZE; i++) {
            Slot old = slots.get(i);
            Slot s = new Slot(box, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return StashMenu.allowed(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return canTake(player);
                }
            };
            s.index = i;
            slots.set(i, s);
        }
    }

    boolean canTake(Player player) {
        return guild.canTake(player.getUUID());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return (slot.container != getContainer() || canTake(owner)) && super.canTakeItemForPickAll(stack, slot);
    }

    // 開く。ギルドに入っていなければ知らせて false
    public static boolean open(ServerPlayer player, @Nullable Entity clerk) {
        GuildData.Guild g = Guilds.of(player);
        if (g == null) {
            Notice.hint(player, Guilds.Result.NOT_IN_GUILD.key);
            return false;
        }
        GuildData data = GuildData.get(player.server);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GuildStorage(id, inv, new Box(g, data::dirty), g, clerk), Component.translatable("gui.skyabyss.guild.storage_title", g.name)));
        if (!g.canTake(player.getUUID())) {
            Notice.hint(player, "message.skyabyss.guild.storage_deposit_only");
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        // 抜けた・外された・解散したら閉じる
        GuildData.Guild now = player instanceof ServerPlayer sp ? Guilds.of(sp) : guild;
        return player.isAlive() && now == guild && (clerk == null || clerk.isAlive() && clerk.distanceToSqr(player) <= 8 * 8);
    }

    // ギルドの枠をそのまま箱として見せる
    static final class Box implements Container {
        private final GuildData.Guild g;
        private final Runnable dirty;

        Box(GuildData.Guild g, Runnable dirty) {
            this.g = g;
            this.dirty = dirty;
        }

        @Override
        public int getContainerSize() {
            return GuildData.STORAGE_SIZE;
        }

        @Override
        public boolean isEmpty() {
            return g.storage.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return g.storage.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack s = g.storage.get(slot);
            dirty.run();
            return s.isEmpty() ? ItemStack.EMPTY : s.split(amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = g.storage.get(slot);
            g.storage.set(slot, ItemStack.EMPTY);
            dirty.run();
            return s;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            g.storage.set(slot, stack);
            dirty.run();
        }

        @Override
        public void setChanged() {
            dirty.run();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return StashMenu.allowed(stack);
        }

        @Override
        public boolean canTakeItem(Container target, int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < GuildData.STORAGE_SIZE; i++) {
                g.storage.set(i, ItemStack.EMPTY);
            }
        }
    }
}
