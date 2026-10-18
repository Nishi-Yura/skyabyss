package jp.revati.skyabyss.guide.client;

import net.minecraft.client.Minecraft;

// 旅の覚書の画面を開く (WayfarerNotesItem から。クライアントでしか呼ばれない)
public final class WayfarerNotesClient {
    private WayfarerNotesClient() {
    }

    public static void open(int chapter) {
        Minecraft.getInstance().setScreen(new WayfarerNotesScreen(chapter));
    }
}
