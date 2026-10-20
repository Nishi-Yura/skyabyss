package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossCue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        BossAttackCue.show(icon(p.boss(), p.attack()), Component.translatable("gui.skyabyss.attack." + p.boss() + "." + p.attack()), p.ticks());
    }

    // 技ごとの 16px の印 (textures/gui/attack/<ボス>_<技>.png。tools/gen_attack_icons.py)。無ければ印なし
    private static final java.util.Map<String, ResourceLocation> ICONS = new java.util.HashMap<>();

    private static ResourceLocation icon(String boss, String attack) {
        return ICONS.computeIfAbsent(boss + "_" + attack, k -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/attack/" + k + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(loc).isPresent() ? loc : null;
        });
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
