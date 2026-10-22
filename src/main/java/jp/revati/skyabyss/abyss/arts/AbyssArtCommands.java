package jp.revati.skyabyss.abyss.arts;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用 (権限 2・開発の環境だけ): /skyabyss abyss art learn|up <術> / open / forget。試遊と自己テストのため。
// 溜まりに行かずに覚えさせる・木を開く・ランクを上げる (点が足りなければ知らせる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssArtCommands {
    private AbyssArtCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("abyss").then(Commands.literal("art")
                        .then(Commands.literal("learn").then(Commands.argument("art", StringArgumentType.word())
                                .executes(c -> learn(c.getSource(), StringArgumentType.getString(c, "art")))))
                        .then(Commands.literal("up").then(Commands.argument("art", StringArgumentType.word())
                                .executes(c -> up(c.getSource(), StringArgumentType.getString(c, "art")))))
                        .then(Commands.literal("open").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            AbyssArts.openTree(p);
                            return 1;
                        })))));
    }

    private static AbyssArt parse(String id) {
        for (AbyssArt a : AbyssArt.values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    private static int learn(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        AbyssArt a = parse(id);
        if (a == null) {
            src.sendFailure(Component.literal("unknown art: " + id));
            return 0;
        }
        boolean ok = AbyssArts.learn(p, a);
        src.sendSuccess(() -> Component.literal(ok ? "learned " + a.id : a.id + " was already learned"), false);
        return ok ? 1 : 0;
    }

    private static int up(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        AbyssArt a = parse(id);
        if (a == null) {
            src.sendFailure(Component.literal("unknown art: " + id));
            return 0;
        }
        AbyssArts.Result r = AbyssArts.up(p, a);
        src.sendSuccess(() -> Component.literal("up " + a.id + ": " + r), false);
        return r == AbyssArts.Result.RANKED ? 1 : 0;
    }
}
