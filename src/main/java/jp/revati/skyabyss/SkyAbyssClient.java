package jp.revati.skyabyss;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// クライアント専用の処理。専用サーバーでは読み込まれない。
@Mod(value = SkyAbyss.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class SkyAbyssClient {
    public SkyAbyssClient(ModContainer container) {
        // Modsメニューから設定画面を開けるようにする
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        SkyAbyss.LOGGER.info("[{}] クライアントの準備が完了しました", SkyAbyss.MODID);
    }
}
