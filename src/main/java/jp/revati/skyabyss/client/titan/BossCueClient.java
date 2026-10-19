package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossCue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// BossCue.Payload の受け口 (印はなし。名前だけ BossAttackCue へ渡す) と、技の名前の描画 (四つの心臓・中ボス・番兵が共通)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BossCueClient {
    private BossCueClient() {
    }

    public static void receive(BossCue.Payload p) {
        BossAttackCue.show(null, Component.translatable("gui.skyabyss.attack." + p.boss() + "." + p.attack()), p.ticks());
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        BossAttackCue.render(event.getGuiGraphics(), mc, t);
    }
}
