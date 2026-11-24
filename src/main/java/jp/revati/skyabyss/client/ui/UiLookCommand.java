package jp.revati.skyabyss.client.ui;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// 画面の絵柄を、遊びながら切り替える (前の切り絵と見比べるための仮のコマンド。切り絵の経路を消すときに UiLook ごと消す)。
//   /skyabyss_ui look <paper|a>   a = 鐘の銅 (既定)・paper = 前の切り絵 (見比べ用)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class UiLookCommand {
    private UiLookCommand() {
    }

    @SubscribeEvent
    static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss_ui")
                .then(Commands.literal("look").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    UiLook.set(UiLook.parse(StringArgumentType.getString(ctx, "name")));
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("UI: " + UiLook.get()), true);
                    }
                    return 1;
                }))));
    }
}
