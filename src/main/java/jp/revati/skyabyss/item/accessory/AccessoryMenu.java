package jp.revati.skyabyss.item.accessory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 装飾の画面のメニュー: 護符 1・装飾 2 の枠と、持ち物。枠の中身はプレイヤーの記録 (AccessoryData) を直接読み書きする。
// 配置は client/accessory/AccessoryScreen と tools/gen_accessory_assets.py (案 B) と同じ
public class AccessoryMenu extends AbstractContainerMenu {
    public static final int[][] SLOT_POS = {{30, 36}, {130, 26}, {130, 50}};
    public static final int INV_Y = 84;
    private static final int ACC = AccessoryData.SIZE;
    private final Player player;

    public AccessoryMenu(int id, Inventory inventory) {
        super(AccessorySetup.MENU.get(), id);
        this.player = inventory.player;
        Container container = new Slots(player);
        for (int i = 0; i < ACC; i++) {
            int index = i;
            addSlot(new Slot(container, i, SLOT_POS[i][0], SLOT_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Accessories.canPlace(player, Accessories.get(player), index, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ACC) {
            if (!moveItemStackTo(stack, ACC, ACC + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = stack.getItem() instanceof Accessory ? Accessories.slotFor(player, Accessories.get(player), stack) : -1;
            if (target >= 0 && slots.get(target).getItem().isEmpty() && slots.get(target).mayPlace(stack)) {
                if (!moveItemStackTo(stack, target, target + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < ACC + 27) {
                if (!moveItemStackTo(stack, ACC + 27, ACC + 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, ACC, ACC + 27, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    // 枠の中身を記録に直結する入れ物。書き換えたら持ち主へ同期する
    private static final class Slots implements Container {
        private final Player player;

        Slots(Player player) {
            this.player = player;
        }

        private AccessoryData data() {
            return Accessories.get(player);
        }

        @Override
        public int getContainerSize() {
            return ACC;
        }

        @Override
        public boolean isEmpty() {
            return data().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return data().items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack s = data().items.get(slot);
            if (s.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = s.split(amount);
            if (s.isEmpty()) {
                data().items.set(slot, ItemStack.EMPTY);
            }
            setChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = data().items.get(slot);
            data().items.set(slot, ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            data().items.set(slot, stack);
            setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            Accessories.sync(player);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < ACC; i++) {
                data().items.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}
