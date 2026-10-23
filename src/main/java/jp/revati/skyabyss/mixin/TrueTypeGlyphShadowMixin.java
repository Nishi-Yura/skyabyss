package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;

import jp.revati.skyabyss.client.ui.ModFont;

// mod の点の字 (DotGothic16 を点の TTF にしたもの。oversample = ModFont.PIXEL_OVERSAMPLE) だけ、影と太字のずれを GUI の 0.5 px にする。
// バニラは TTF の字に 1 px の影を付けるが、この字の 1 点は 0.5 px なので、影が点 2 つ分離れて字が二重に見える (宙に浮いた字で目立つ)。
// バニラの日本語の字 (Unifont) と同じずれ。ほかの TTF (題字の Zen Antique など) は元のまま
@Mixin(targets = "com.mojang.blaze3d.font.TrueTypeGlyphProvider$Glyph")
public abstract class TrueTypeGlyphShadowMixin implements GlyphInfo {
    @Shadow
    @Final
    TrueTypeGlyphProvider this$0;

    private boolean skyabyss$pixel() {
        return ((TrueTypeProviderAccessor) (Object) this$0).skyabyss$oversample() == ModFont.PIXEL_OVERSAMPLE;
    }

    @Override
    public float getShadowOffset() {
        return skyabyss$pixel() ? 0.5f : 1.0f;
    }

    @Override
    public float getBoldOffset() {
        return skyabyss$pixel() ? 0.5f : 1.0f;
    }
}
