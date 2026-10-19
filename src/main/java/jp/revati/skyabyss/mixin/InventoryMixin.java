package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.pouch.PouchCraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;

// クライアントのレシピブックが「作れる」を数えるとき (Inventory.fillStackedContents)、旅嚢の材料も足す (サーバーでは何もしない。置くときに持ち物へ出すので二重にならない)
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Inject(method = "fillStackedContents", at = @At("TAIL"))
    private void skyabyss$pouch(StackedContents contents, CallbackInfo ci) {
        PouchCraft.fillClient(this.player, contents);
    }
}
