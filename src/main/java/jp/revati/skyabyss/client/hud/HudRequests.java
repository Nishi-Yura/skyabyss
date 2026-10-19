package jp.revati.skyabyss.client.hud;

import java.util.List;

import jp.revati.skyabyss.hud.HudLayout.Request;
import net.minecraft.client.Minecraft;

// 各描画層が「いま出したい部品」の要求を出す口の一覧 (HudManager が毎フレーム呼ぶ)
final class HudRequests {
    private HudRequests() {
    }

    static void collect(Minecraft mc, int w, int h, List<Request> out) {
    }
}
