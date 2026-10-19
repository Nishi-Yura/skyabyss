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
            japanese(Minecraft.getInstance());
        }
    }

    // 確かめ用の起動と台本の撮影は日本語で見る (SKYABYSS_LANG=en_us などで変えられる)。すでにその言語なら何もしない
    public static void japanese(Minecraft mc) {
        String code = System.getenv().getOrDefault("SKYABYSS_LANG", "ja_jp");
        if (code.equals(mc.options.languageCode)) {
            return;
        }
        mc.options.languageCode = code;
        mc.getLanguageManager().setSelected(code);
        mc.options.save();
        mc.reloadResourcePacks();
    }
}
