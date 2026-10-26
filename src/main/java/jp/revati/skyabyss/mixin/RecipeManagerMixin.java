package jp.revati.skyabyss.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;

// レシピを一つも読まない (2026-10-26 の声「クラフトとかもしない。出来ないようにして」「レシピのデータも消せるので、マイクラ軽くできるのでは」)。
// 作業台・かまど・石切台・鍛冶台のバニラのレシピ (千あまり) も、手元へ送るレシピブックも空になる (読み込み・送る量が減る)。
// Mod の品は鍛冶場・薬師・道具屋の誂え (data/skyabyss/shop の made の品。tools/gen/shop/gen_made_to_order.py) で手に入る
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void skyabyss$noRecipes(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        recipes.clear();
    }
}
