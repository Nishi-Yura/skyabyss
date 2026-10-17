package jp.revati.skyabyss.opening;

import java.util.StringJoiner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// テスト用のコマンド (OP)。拍子を進める・戻す。結果はチャット (管理者向けは今までどおり)。
//   /skyabyss opening status [player]          : 済んだ拍子と、いまの目的
//   /skyabyss opening <拍子> [player]          : その拍子を今すぐ起こし、前の拍子は済んだことにする
//   /skyabyss opening back <拍子> [player]     : その拍子とそれ以後を「まだ」に戻す (記した頁も消す。演出も止める)
//   /skyabyss opening reset [player]           : 全部なかったことにする。次の見回りで B0 から
// 拍子の名前: awake camp trail footprints shadow storyteller footsteps isana bones song whale (B0〜B10)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class OpeningCommands {
    private OpeningCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> opening = Commands.literal("opening");
        for (OpeningBeat beat : OpeningBeat.values()) {
            opening.then(Commands.literal(beat.id)
                    .executes(ctx -> go(ctx, ctx.getSource().getPlayerOrException(), beat))
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> go(ctx, EntityArgument.getPlayer(ctx, "player"), beat))));
        }
        LiteralArgumentBuilder<CommandSourceStack> back = Commands.literal("back");
        for (OpeningBeat beat : OpeningBeat.values()) {
            back.then(Commands.literal(beat.id)
                    .executes(ctx -> back(ctx, ctx.getSource().getPlayerOrException(), beat))
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> back(ctx, EntityArgument.getPlayer(ctx, "player"), beat))));
        }
        opening.then(back);
        opening.then(Commands.literal("reset")
                .executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))));
        opening.then(Commands.literal("status")
                .executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))));
        dispatcher.register(Commands.literal(SkyAbyss.MODID).requires(source -> source.hasPermission(2)).then(opening));
    }

    private static int go(CommandContext<CommandSourceStack> ctx, ServerPlayer player, OpeningBeat beat) throws CommandSyntaxException {
        OpeningDirector.force(player, beat);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.opening.go", beat.number(), beat.id, player.getDisplayName()), true);
        return 1;
    }

    private static int back(CommandContext<CommandSourceStack> ctx, ServerPlayer player, OpeningBeat beat) throws CommandSyntaxException {
        OpeningDirector.rewind(player, beat);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.opening.back", beat.number(), beat.id, player.getDisplayName()), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        OpeningDirector.reset(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.opening.reset", player.getDisplayName()), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
        OpeningState st = OpeningDirector.state(player);
        StringJoiner done = new StringJoiner(" ");
        for (OpeningBeat beat : OpeningBeat.values()) {
            done.add((st.done(beat) ? "[B" + beat.number() + " " : "(B" + beat.number() + " ") + beat.id + (st.done(beat) ? "]" : ")"));
        }
        String text = done + (st.legacy() ? " legacy" : "");
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.opening.status", player.getDisplayName(), text), false);
        return 1;
    }
}
