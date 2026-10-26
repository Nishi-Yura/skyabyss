package jp.revati.skyabyss.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

// バニラの進捗を読まない (2026-10-26 の声「マイクラの進捗も全消しして。mod のもののみ」)。
// minecraft: の名前の進捗 (冒険・農業・ネザー・エンド・レシピの解放) を外し、天と淵の進捗 (data/skyabyss/advancement) だけを残す。
// Mod の進捗はバニラの進捗を親にしていない (自己テスト advancements が確かめる)
@Mixin(ServerAdvancementManager.class)
public abstract class AdvancementManagerMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void skyabyss$modOnly(Map<ResourceLocation, JsonElement> advancements, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        advancements.keySet().removeIf(id -> id.getNamespace().equals("minecraft"));
    }
}
