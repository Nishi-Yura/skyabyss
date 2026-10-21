package jp.revati.skyabyss.trial;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;

// 順路 (docs/design/32): あきらめの返事 (/skyroute yes|no。チャットの [はい] [いいえ] が押す) と、管理者の状態の確認
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RouteCommands {
    private RouteCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyroute")
                .then(Commands.literal("yes").executes(ctx -> reply(ctx, true)))
                .then(Commands.literal("no").executes(ctx -> reply(ctx, false)))
                .then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(RouteCommands::status))
                .then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(RouteCommands::start))
                .then(Commands.literal("clear").requires(s -> s.hasPermission(2)).executes(RouteCommands::clear)));
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, boolean yes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return TrialRoute.reply(p, yes) ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        java.util.UUID group = Groups.keyOf(p);
        ctx.getSource().sendSuccess(() -> Component.literal("route of the " + titan + ": " + TrialRoute.describe(back, group)
                + " pending " + TrialRoute.pendingCount() + " abandon " + TrialRoute.abandonPending(group)), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = p.serverLevel();
        String titan = Titans.titanOfBack(back.dimension());
        if (titan == null) {
            ctx.getSource().sendFailure(Component.literal("not on a titan back"));
            return 0;
        }
        TrialRoute.start(back, Groups.keyOf(p), titan, TrialRoute.present(back, Groups.keyOf(p)));
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TrialRoute.finish(p.serverLevel(), Groups.keyOf(p));
        return 1;
    }
}
