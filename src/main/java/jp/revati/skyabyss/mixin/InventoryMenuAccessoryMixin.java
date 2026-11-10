package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.item.accessory.AccessorySlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// プレイヤーの持ち物のメニューに装飾の 3 枠を足す (46〜48。item/accessory/AccessorySlots)。持ち物の画面で付け外しできるように (2026-11-11、装飾のつまみはなくした)。
// シフトクリック: 持ち物の装飾は空いた装飾の枠へ。装飾の枠からはバニラの決まり (9〜44 へ) のまま
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuAccessoryMixin extends AbstractContainerMenu {
    protected InventoryMenuAccessoryMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void skyabyss$accessorySlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        AccessorySlots.Store store = new AccessorySlots.Store(owner);
        for (int i = 0; i < AccessorySlots.SIZE; i++) {
            addSlot(AccessorySlots.slot(owner, store, i));
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void skyabyss$toAccessory(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < 9 || index >= 45 || index >= slots.size()) {
            return;
        }
        Slot from = slots.get(index);
        ItemStack stack = from.getItem();
        int target = AccessorySlots.targetFor(player, stack);
        if (target < 0) {
            return;
        }
        int at = AccessorySlots.FIRST + target;
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, at, at + 1, false)) {
            return;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        cir.setReturnValue(copy);
    }
}
