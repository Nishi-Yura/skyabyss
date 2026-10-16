package jp.revati.skyabyss.story;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 1 章の終わりのコマンド (管理者、試遊とテスト用)
// /skyabyss story ready <人>  : 四体の心臓を止めたことにし、欠片を四つまで足す (1 秒後に鍵がそろう)
// /skyabyss story key <人>    : 欠片の数を問わず、今すぐ鍵にする
// /skyabyss story scene       : 鍵がそろった場面を見直す (記録は変わらない)
// /skyabyss story reset <人>  : 鍵の記録と伝承「天の鍵」を消す (品は取り上げない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StoryCommands {
    private StoryCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("story")
                        .then(Commands.literal("ready").then(Commands.argument("players", EntityArgument.players()).executes(StoryCommands::ready)))
                        .then(Commands.literal("key").then(Commands.argument("players", EntityArgument.players()).executes(StoryCommands::key)))
                        .then(Commands.literal("scene").executes(ctx -> {
                            SkyKey.replay(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(StoryCommands::reset)))));
    }

    private static int ready(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer p : players) {
            for (String t : ProgressRules.TITAN_ORDER) {
                Progression.onTitanStopped(p.server, List.<UUID>of(p.getUUID()), t);
            }
            int missing = SkyKey.FRAGMENTS - SkyKey.fragmentsHeld(p);
            if (missing > 0 && !SkyKey.assembled(p)) {
                Quests.give(p, new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), missing));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("story ready for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int key(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        int n = 0;
        for (ServerPlayer p : players) {
            n += SkyKey.assemble(p, true) ? 1 : 0;
        }
        int done = n;
        ctx.getSource().sendSuccess(() -> Component.literal("sky key assembled for " + done + " player(s)"), true);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        players.forEach(SkyKey::reset);
        ctx.getSource().sendSuccess(() -> Component.literal("sky key record reset for " + players.size() + " player(s)"), true);
        return players.size();
    }
}
