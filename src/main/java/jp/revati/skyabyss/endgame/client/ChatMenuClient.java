package jp.revati.skyabyss.endgame.client;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 押せる一覧がチャットに出たとき (endgame/ChatMenus.Open)、チャットの窓を開く。会話の画面などが開いているあいだは閉じるのを待つ (1 秒まで)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ChatMenuClient {
    private static int waiting = -1;

    private ChatMenuClient() {
    }

    public static void receive() {
        waiting = 20;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (waiting < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            waiting = -1;
            return;
        }
        if (mc.screen == null) {
            mc.setScreen(new ChatScreen(""));
            waiting = -1;
        } else if (mc.screen instanceof ChatScreen || --waiting < 0) {
            waiting = -1;
        }
    }
}
