package jp.revati.skyabyss.story.ch5.client;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

// 深淵の見た目 (docs/design/36)。空なし・雲なし・どこでも霧 (遠くが闇に溶ける)。霧の色はバイオームの色 (ほぼ黒の青) のまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssClient {
    public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss");

    private AbyssClient() {
    }

    @SubscribeEvent
    static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, true) {
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
                return color;
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return true;
            }
        });
    }
}
