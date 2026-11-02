package jp.revati.skyabyss.client.rules;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.rules.RpgRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// アドベンチャーでブロックを左クリックしたとき、サーバーへ「叩いた」と知らせる (rpg/rules/RpgRules.AdventureClick)。
// 手元のマインクラフトは、壊す権利のないブロックへの左クリックをサーバーへ送らないため
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AdventureClickClient {
    private AdventureClickClient() {
    }

    @SubscribeEvent
    static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() != GameType.ADVENTURE
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        PacketDistributor.sendToServer(new RpgRules.AdventureClick(hit.getBlockPos(), hit.getDirection()));
    }
}
