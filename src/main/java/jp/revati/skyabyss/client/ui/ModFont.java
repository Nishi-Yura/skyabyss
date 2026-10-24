package jp.revati.skyabyss.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// 字はマインクラフトの字のまま (2026-10-25 の試遊で、mod の字のパックをやめた)。
// 題字 (章の題字・場所の名前・余韻の札) を描く所は title() を通す。いまは字を替えず、そのまま返す
public final class ModFont {
    private ModFont() {
    }

    // 題字。scale は描くときの拡大 (いまは使わない。字を替えるときの手がかりに残す)
    public static MutableComponent title(Component c, float scale) {
        return c.copy();
    }

    public static MutableComponent title(String s, float scale) {
        return Component.literal(s);
    }

    // mod の字のパックが選ばれているか (斜体を外すかの判断。ItalicStyle)。パックはもう無い
    public static boolean active() {
        return false;
    }
}
