package jp.revati.skyabyss.client.npc;

import jp.revati.skyabyss.npc.NpcNetwork;
import net.minecraft.client.Minecraft;

// 会話のクライアント側の受け口。サーバーから節が届いたら画面を開く (開いていれば中身を替える)
public final class NpcClient {
    private NpcClient() {
    }

    public static void show(NpcNetwork.Show show) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen screen && screen.npcId() == show.npcId()) {
            screen.setNode(show);
        } else {
            mc.setScreen(new DialogueScreen(show));
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen screen) {
            screen.closeFromServer();
        }
    }
}
