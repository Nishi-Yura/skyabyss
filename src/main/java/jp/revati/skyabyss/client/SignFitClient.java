package jp.revati.skyabyss.client;

import java.util.List;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

// 開発中のクライアントで、看板の文が本物の Font の幅で 1 行に収まるかを確かめてログに書く (ワールドに入った最初の一度だけ)。
// サーバーの自己テストは字の幅の表で見積もる (SignFit)。ここでは、その見積もりが本物より狭くないか (甘くないか) も見る
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SignFitClient {
    private static boolean checked;

    private SignFitClient() {
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (checked || FMLEnvironment.production) {
            return;
        }
        checked = true;
        var font = Minecraft.getInstance().font;
        List<String> keys = SignFit.allSignKeys();
        List<String> failures = SignFit.check(keys, font::width);
        for (String language : SignFit.LANGUAGES) {
            for (String key : keys) {
                for (int i = 1; i <= 4; i++) {
                    String line = SignFit.lang(language).getOrDefault(key + "." + i, "");
                    int real = font.width(line);
                    if (SignFit.approxWidth(line) < real) {
                        failures.add("SignFit table underestimates " + key + "." + i + " (" + language + "): table "
                                + SignFit.approxWidth(line) + " < font " + real);
                    }
                }
            }
        }
        failures.forEach(f -> SkyAbyss.LOGGER.warn("SignFit (client): {}", f));
        SkyAbyss.LOGGER.info("SignFit (client): checked {} signs in {} languages with the real font, {} problem(s)",
                keys.size(), SignFit.LANGUAGES.size(), failures.size());
    }
}
