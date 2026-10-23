package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;

@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeProviderAccessor {
    @Accessor("oversample")
    float skyabyss$oversample();
}
