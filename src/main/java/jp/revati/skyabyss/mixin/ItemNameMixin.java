package jp.revati.skyabyss.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 名前の色は階級の色 (2026-10-27 の声「レア度おかしいやつ」: エピックなのに青い名前の品があった)。
// 自分で名前を組む装備 (getName を持つ品。ModGear.name で色と「+段」を付ける) はそのまま。持たない装備にだけ、ここで同じ形を当てる
@Mixin(Item.class)
public abstract class ItemNameMixin {
    private static final Map<Class<?>, Boolean> skyabyss$own = new ConcurrentHashMap<>();

    @Inject(method = "getName(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void skyabyss$tierColor(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        Object self = this;
        if (!(self instanceof ModGear gear) || skyabyss$own.computeIfAbsent(self.getClass(), ItemNameMixin::skyabyss$overrides)) {
            return;
        }
        cir.setReturnValue(ModGear.name(stack, cir.getReturnValue(), gear.tier()));
    }

    private static boolean skyabyss$overrides(Class<?> c) {
        try {
            return c.getMethod("getName", ItemStack.class).getDeclaringClass() != Item.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
