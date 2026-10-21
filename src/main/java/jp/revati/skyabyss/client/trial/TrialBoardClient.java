package jp.revati.skyabyss.client.trial;

import jp.revati.skyabyss.trial.TrialBoardNet;
import net.minecraft.client.Minecraft;

// 試練の板の画面のクライアント側の入口 (docs/design/29 の 2.7)。パケットを受けて画面を開く。決めるのは全部サーバー
public final class TrialBoardClient {
    private TrialBoardClient() {
    }

    // 順路の今の段 (目的の帯・パーティの札が読む)。step 0 なら順路の外
    private static volatile TrialBoardNet.RouteState route = new TrialBoardNet.RouteState("", 0, false, "", "", 0);

    public static void onRoute(TrialBoardNet.RouteState state) {
        route = state;
    }

    public static TrialBoardNet.RouteState route() {
        return route;
    }

    // いまいる背の順路が動いているか
    public static boolean routeActive(Minecraft mc) {
        TrialBoardNet.RouteState r = route;
        return mc.level != null && r.step() > 0 && r.titan().equals(jp.revati.skyabyss.progress.Titans.titanOfBack(mc.level.dimension()));
    }

    // 目的の帯の一行 (「第一の試練 〇〇（歩）」など)。順路の外なら null
    @javax.annotation.Nullable
    public static String routeLine(Minecraft mc) {
        if (!routeActive(mc)) {
            return null;
        }
        TrialBoardNet.RouteState r = route;
        if (r.mid()) {
            return net.minecraft.network.chat.Component.translatable("objective.skyabyss.route_mid",
                    net.minecraft.network.chat.Component.translatable("trial.skyabyss.midboss." + jp.revati.skyabyss.trial.TrialRegions.signalTitan(r.titan()))).getString();
        }
        if (r.step() >= jp.revati.skyabyss.trial.TrialRoute.LAST_STEP) {
            return net.minecraft.network.chat.Component.translatable("objective.skyabyss.route_heart").getString();
        }
        return net.minecraft.network.chat.Component.translatable("objective.skyabyss.route_step" + r.step(),
                net.minecraft.network.chat.Component.translatable("trial.skyabyss.cell." + r.cellId()),
                net.minecraft.network.chat.Component.translatable("trial.skyabyss.slot." + r.slot())).getString();
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
