package jp.revati.skyabyss.notice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 知らせの確かめ用 (開発時だけ。撮影の台本から cmd で使う):
//   /skyabyss dev notice <narrate|hint|levelup|gain|unlock|alert|warn|bar> <文の鍵>
//   /skyabyss dev notice place <題の鍵> <副題の鍵>
// 本番と同じ Notice の経路で、自分に一つ送る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NoticeDev {
    private NoticeDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("notice")
                        .then(Commands.literal("place").then(Commands.argument("title", StringArgumentType.word())
                                .then(Commands.argument("subtitle", StringArgumentType.word()).executes(ctx -> {
                                    Notice.place(ctx.getSource().getPlayerOrException(), ModText.title(StringArgumentType.getString(ctx, "title")),
                                            ModText.subtitle(StringArgumentType.getString(ctx, "subtitle")));
                                    return 1;
                                }))))
                        .then(Commands.argument("kind", StringArgumentType.word()).then(Commands.argument("key", StringArgumentType.word())
                                .executes(NoticeDev::send))))));
    }

    private static int send(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(ctx, "key");
        switch (StringArgumentType.getString(ctx, "kind")) {
            case "narrate" -> Notice.narrate(p, key);
            case "hint" -> Notice.hint(p, key);
            case "levelup" -> Notice.levelUp(p, key);
            case "gain" -> Notice.gain(p, key);
            case "unlock" -> Notice.unlock(p, key);
            case "alert" -> Notice.alert(p, key);
            case "warn" -> Notice.warn(p, key);
            case "bar" -> Notice.bar(p, ModText.hint(key));
            default -> {
                ctx.getSource().sendFailure(Component.literal("unknown kind"));
                return 0;
            }
        }
        return 1;
    }
}
