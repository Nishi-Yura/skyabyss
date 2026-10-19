package jp.revati.skyabyss.client.rebirth;

import net.minecraft.client.Minecraft;

// 転生の画面を開く入口 (クライアントだけ)。台座を右クリックしたとき (RebirthOpenPayload) と、K 画面のつまみから
public final class SoulClient {
    private SoulClient() {
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new SoulScreen());
        }
    }
}
