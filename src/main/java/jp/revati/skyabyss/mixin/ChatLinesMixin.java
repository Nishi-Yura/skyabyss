package jp.revati.skyabyss.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import jp.revati.skyabyss.client.hud.HudManager;
import net.minecraft.client.gui.components.ChatComponent;

// チャットの見える行数と幅を、置き場の計算 (HudLayout の chatLines) が出した上限に絞る。mod の部品と重ならないよう、混んだ場面では行を減らす (新しい行を残す)。
// 履歴そのものは消えない (チャットの窓で遡れる)。チャットの窓を開いているあいだは絞らない (押せる一覧を全部見せる。endgame/ChatMenus)
@Mixin(ChatComponent.class)
public abstract class ChatLinesMixin {
    @Inject(method = "getLinesPerPage", at = @At("RETURN"), cancellable = true)
    private void skyabyss$cap(CallbackInfoReturnable<Integer> cir) {
        int cap = HudManager.result().chatLines;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
            return;
        }
        if (cap > 0 && cap < cir.getReturnValue()) {
            cir.setReturnValue(cap);
        }
    }

    // 幅も同じ: 狭い所へ寄せるときは細くする。新しい文はその幅で折り返され、いまある文は HudManager が rescaleChat で折り返し直す
    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void skyabyss$width(CallbackInfoReturnable<Integer> cir) {
        int cap = HudManager.result().chatWidth;
        if (cap > 0 && cap < cir.getReturnValue()) {
            cir.setReturnValue(cap);
        }
    }
}
