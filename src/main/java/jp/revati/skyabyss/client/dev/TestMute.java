package jp.revati.skyabyss.client.dev;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 確かめ用の起動 (./gradlew runClientTest、環境変数 SKYABYSS_MUTE=1) は主音量 0 で始める。普通の遊びには効かない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TestMute {
    private static boolean done;

    private TestMute() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        done = true;
        if (System.getenv("SKYABYSS_MUTE") != null) {
            Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        }
    }
}
