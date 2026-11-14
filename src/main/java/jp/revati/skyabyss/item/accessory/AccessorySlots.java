package jp.revati.skyabyss.item.accessory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 装飾の枠 (護符 1・装飾 2)。プレイヤーの持ち物のメニュー (InventoryMenu) の後ろに足す (mixin/InventoryMenuAccessoryMixin)。
// 持ち物の画面の、バニラの作業台の場所に並ぶ (client/inventory/SkyInventoryScreen。docs/design/56 の 4 章)。
// 枠の中身はプレイヤーの記録 (AccessoryData) を直接読み書きする。記録はサーバーから持ち主へ同期されるので、クライアントでも同じ入れ物で読める
public final class AccessorySlots {
    // InventoryMenu の枠の番号: バニラの 0〜45 の後ろ
    public static final int FIRST = 46;
    public static final int SIZE = AccessoryData.SIZE;
    // 持ち物の画面での置き場 (バニラの作業台の枠の高さ。16 角の左上)
    public static final int[] X = {98, 120, 142};
    public static final int Y = 22;

    private AccessorySlots() {
    }

    public static boolean isAccessory(int menuIndex) {
        return menuIndex >= FIRST && menuIndex < FIRST + SIZE;
    }

    public static Slot slot(Player player, Container container, int index) {
        return new Slot(container, index, X[index], Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Accessories.canPlace(player, Accessories.get(player), index, stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            // クリエイティブの持ち物のつまみはバニラの並べ方なので、そこでは出さない (手もとの枠に重なる)
            @Override
            public boolean isActive() {
                return !player.getAbilities().instabuild;
            }
        };
    }

    // 持ち物から装飾をシフトクリックしたとき、入る枠 (空いていて置ける)。なければ -1
    public static int targetFor(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof Accessory)) {
            return -1;
        }
        AccessoryData data = Accessories.get(player);
        int target = Accessories.slotFor(player, data, stack);
        return target >= 0 && data.items.get(target).isEmpty() && Accessories.canPlace(player, data, target, stack) ? target : -1;
    }

    // 枠の中身を記録に直結する入れ物。書き換えたら持ち主へ同期する
    public static final class Store implements Container {
        private final Player player;

        public Store(Player player) {
            this.player = player;
        }

        private AccessoryData data() {
            return Accessories.get(player);
        }

        @Override
        public int getContainerSize() {
            return SIZE;
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
            for (int i = 0; i < SIZE; i++) {
                data().items.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}
