package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

// 巨神の背の中 (ブロックを置けない所) では、道具・武器・防具の耐久が減らない (2026-10-22 の二人の試遊。docs/design/32 の 2.4)。
// 減らす呼び出し (ItemStack.hurtAndBreak) の頭で、背の次元なら何もしない。直す (負の値) のはそのまま
@Mixin(ItemStack.class)
public abstract class ItemStackDurabilityMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void skyabyss$noWearOnBacks(int amount, ServerLevel level, LivingEntity entity, java.util.function.Consumer<net.minecraft.world.item.Item> onBreak,
            CallbackInfo ci) {
        if (amount > 0 && jp.revati.skyabyss.titan.BackDurability.keeps(level)) {
            ci.cancel();
        }
    }
}
