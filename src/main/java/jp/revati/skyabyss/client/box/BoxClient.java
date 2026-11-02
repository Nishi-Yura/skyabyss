package jp.revati.skyabyss.client.box;

import jp.revati.skyabyss.loot.box.BoxNetwork;
import net.minecraft.client.Minecraft;

// 封切り師の画面の出し入れ (サーバーからの知らせ。loot/box/BoxNetwork)
public final class BoxClient {
    private BoxClient() {
    }

    public static void view(BoxNetwork.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoxScreen s) {
            s.update(view);
        } else {
            mc.setScreen(new BoxScreen(view));
        }
    }

    public static void result(BoxNetwork.Result result) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoxScreen s) {
            s.spin(result);
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoxScreen) {
            mc.setScreen(null);
        }
    }
}
