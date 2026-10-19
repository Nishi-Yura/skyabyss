package jp.revati.skyabyss.client.trial;

import jp.revati.skyabyss.trial.TrialBoardNet;
import net.minecraft.client.Minecraft;

// 試練の板の画面のクライアント側の入口 (docs/design/29 の 2.7)。パケットを受けて画面を開く。決めるのは全部サーバー
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
