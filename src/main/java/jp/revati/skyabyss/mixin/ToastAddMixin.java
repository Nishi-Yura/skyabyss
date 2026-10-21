package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.components.toasts.TutorialToast;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.notice.CardFeed;

// 札の入口: バニラの進捗の札は mod の「進捗」の札 (CardFeed) に替え、レシピ・操作説明の札は出さない (設定 hideHelpToasts)。
// 右上に札が積もって邪魔にならないように (docs/design/18 の 3 章)
@Mixin(ToastComponent.class)
public abstract class ToastAddMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void skyabyss$route(Toast toast, CallbackInfo ci) {
        if (toast instanceof AdvancementToast adv) {
            DisplayInfo d = ((AdvancementToastAccessor) adv).skyabyss$advancement().value().display().orElse(null);
            ci.cancel();
            if (d != null) {
                CardFeed.advancement(d.getTitle(), d.getType() == AdvancementType.CHALLENGE);
            }
            return;
        }
        if ((toast instanceof RecipeToast || toast instanceof TutorialToast) && Config.HIDE_HELP_TOASTS.get()) {
            ci.cancel();
        }
    }
}
