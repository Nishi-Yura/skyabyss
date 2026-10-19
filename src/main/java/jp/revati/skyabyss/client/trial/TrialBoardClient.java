package jp.revati.skyabyss.client.trial;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.trial.TrialBoardNet;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

// 試練の板の画面のクライアント側の入口 (docs/design/29 の 2.7)。パケットを受けて画面を開く。決めるのは全部サーバー
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TrialBoardClient {
    private TrialBoardClient() {
    }

    public static void onOpen(TrialBoardNet.Open open) {
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TrialBoardScreen screen) {
                screen.update(open);
                return;
            }
            mc.setScreen(new TrialBoardScreen(open));
        });
    }
}
