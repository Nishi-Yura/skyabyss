package jp.revati.skyabyss.client.forge;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// 共鳴の炉の画面を、メニューに結びつける
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ForgeClient {
    private ForgeClient() {
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESONANCE_FORGE.get(), ResonanceForgeScreen::new);
    }
}
