package jp.revati.skyabyss.client.record;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.record.RecordCommands;
import jp.revati.skyabyss.record.RecordSnapshot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// 控え帳のキー (既定は B。バニラと、この Mod の J・K・U・R・G・V・Z とぶつからない) と、サーバーから届いた中身の受け取り
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class RecordClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.skyabyss_record.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.RECORD, "key.categories.skyabyss");

    private RecordClient() {
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                // 中身はサーバーが作る。キーはコマンドと同じ道を通す
                mc.player.connection.sendCommand(RecordCommands.COMMAND);
            }
        }
    }

    // バニラの進捗の画面 (L キー・ポーズ画面の「進捗」) の代わりに、控え帳の「記し」を開く (2026-10-26 の声「進捗の UI 変えてもいい。みにくいので」)
    @SubscribeEvent
    static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen && mc.player != null) {
            event.setCanceled(true);
            mc.player.connection.sendCommand(RecordCommands.COMMAND + " " + RecordSnapshot.DEEDS);
        }
    }

    // サーバーから: open なら画面を開き、そうでなければ同じ人の画面が開いているときだけ差し替える
    public static void receive(RecordSnapshot snapshot, boolean open, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RecordScreen screen && screen.target().equals(snapshot.target())) {
            screen.update(snapshot, open ? tab : "");
            return;
        }
        if (open) {
            mc.setScreen(new RecordScreen(snapshot, tab));
        }
    }
}
