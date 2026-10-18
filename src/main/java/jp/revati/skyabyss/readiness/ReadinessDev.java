package jp.revati.skyabyss.readiness;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 備えの三灯の確かめ用 (開発時だけ。撮影の台本から cmd で使う):
//   /skyabyss dev readiness prompt <巨神>   乗り込み口に触れたときの一度目の確認 (三灯の一行・「このまま乗る？」) を自分に出す
//   /skyabyss dev readiness summary <red|others>  扉の予告の準備確認 (A2) の見本 (red = 自分の刃が赤、others = 仲間の誰かが赤)
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
                        .then(Commands.literal("summary").then(Commands.argument("who", StringArgumentType.word()).executes(ctx -> {
                            // 扉の予告 (A2) の見本: red = 自分の刃が赤 (一行の直し方つき)、others = 自分は緑で仲間の誰かが赤
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            boolean red = StringArgumentType.getString(ctx, "who").equals("red");
                            Readiness.Result mine = red ? new Readiness.Result(Titans.STONE_WHALE, 1.0, 0.62, 0.8)
                                    : new Readiness.Result(Titans.STONE_WHALE, 1.0, 1.0, 1.0);
                            Notice.bar(p, ReadinessSummary.barLine(mine, !red, 3));
                            if (red) {
                                Notice.send(p, Notice.Kind.HINT, ReadinessAdvice.line(p, Titans.STONE_WHALE, mine, Readiness.Lamp.BLADE));
                            }
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
