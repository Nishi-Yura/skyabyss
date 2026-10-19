package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import jp.revati.skyabyss.pouch.PouchCraft;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

// レシピブックの「置く」(ServerPlaceRecipe.recipeClicked) の前後で、旅嚢の材料を一時的に持ち物へ出し、終わったら戻す (docs/design/22 の 17 章)。
// 置く処理そのものはバニラのまま (持ち物から材料を探して作業台の枠へ移す)。ここは前後だけ
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin<I extends RecipeInput, R extends Recipe<I>> {
    @Inject(method = "recipeClicked", at = @At("HEAD"))
    private void skyabyss$stage(ServerPlayer player, RecipeHolder<R> recipe, boolean placeAll, CallbackInfo ci) {
        PouchCraft.stage(player, recipe);
    }

    @Inject(method = "recipeClicked", at = @At("RETURN"))
    private void skyabyss$restore(ServerPlayer player, RecipeHolder<R> recipe, boolean placeAll, CallbackInfo ci) {
        PouchCraft.unstage(player);
    }
}
