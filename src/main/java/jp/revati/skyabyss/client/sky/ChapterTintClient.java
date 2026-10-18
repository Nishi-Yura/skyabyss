package jp.revati.skyabyss.client.sky;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.sky.ChapterTintPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 章ごとの空の色味 (ChapterSky.Stage の tint)。霧の色 (遠景と空の下の端) を、ごくわずかにその色へ寄せる。強さは最大 0.35
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ChapterTintClient {
    public static final float MAX = 0.35f;
    private static int rgb;
    private static float strength;

    private ChapterTintClient() {
    }

    public static void receive(ChapterTintPayload payload) {
        rgb = payload.rgb();
        strength = Mth.clamp(payload.strength(), 0f, MAX);
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (strength < 0.005f || Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        event.setRed(Mth.lerp(strength, event.getRed(), (rgb >> 16 & 255) / 255f));
        event.setGreen(Mth.lerp(strength, event.getGreen(), (rgb >> 8 & 255) / 255f));
        event.setBlue(Mth.lerp(strength, event.getBlue(), (rgb & 255) / 255f));
    }
}
