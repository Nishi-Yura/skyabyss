package jp.revati.skyabyss.readiness;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 備えの三灯の確かめ用 (開発時だけ。撮影の台本から cmd で使う):
//   /skyabyss dev readiness prompt <巨神>   乗り込み口に触れたときの一度目の確認 (三灯の一行・「このまま乗る？」) を自分に出す
//   /skyabyss dev readiness whisper <巨神>  前室の囁きを (聞いた印を消して) もう一度出す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ReadinessDev {
    private ReadinessDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("readiness")
                        .then(Commands.literal("prompt").then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String titan = StringArgumentType.getString(ctx, "titan");
                            BoardReadiness.showPrompt(p, Readiness.of(p, titan), BoardReadiness.Mode.RIGHT_CLICK);
                            return 1;
                        })))
                        .then(Commands.literal("whisper").then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String titan = StringArgumentType.getString(ctx, "titan");
                            ReadinessWhisper.forget(p, titan);
                            return ReadinessWhisper.hear(p, titan) ? 1 : 0;
                        }))))));
    }
}
