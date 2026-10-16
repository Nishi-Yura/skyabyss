package jp.revati.skyabyss.progress;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /story                                       : 自分の物語の進み・レベルの上限・ワールドの進みを見る (誰でも)
// /skyabyss progress show <人>                  : 人の進みを見る (管理者)
// /skyabyss progress grant <人> <巨神>           : 心臓を止めたことにする (前の巨神も順に足す)。世界の進みにも写る
// /skyabyss progress revoke <人> <巨神>          : この記録から消す (巨神の記憶の撃破の記録は消さない)
// /skyabyss progress world grant|revoke <巨神>   : 世界の進みだけを変える (巨神の出現を試すため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ProgressCommands {
    private ProgressCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("story").executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException())));
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("progress")
                        .then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("grant").then(Commands.argument("players", EntityArgument.players())
                                .then(titanArg().executes(ctx -> grant(ctx, true)))))
                        .then(Commands.literal("revoke").then(Commands.argument("players", EntityArgument.players())
                                .then(titanArg().executes(ctx -> grant(ctx, false)))))
                        .then(Commands.literal("world")
                                .then(Commands.literal("grant").then(titanArg().executes(ctx -> world(ctx, true))))
                                .then(Commands.literal("revoke").then(titanArg().executes(ctx -> world(ctx, false)))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> titanArg() {
        return Commands.argument("titan", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(ProgressRules.TITAN_ORDER, b));
    }

    private static String titan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String t = StringArgumentType.getString(ctx, "titan");
        if (ProgressRules.titanIndex(t) < 0) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("unknown titan: " + t)).create();
        }
        return t;
    }

    static int show(CommandSourceStack source, ServerPlayer player) {
        MinecraftServer server = player.server;
        int stage = Progression.stage(player);
        MutableComponent text = Component.translatable("message.skyabyss.progress.show_head", player.getDisplayName()).withStyle(ChatFormatting.AQUA);
        text.append("\n").append(Component.translatable("message.skyabyss.progress.show_stage", stage, ProgressRules.TITAN_ORDER.size(),
                Progression.levelCap(player)).withStyle(ChatFormatting.GRAY));
        ProgressData data = ProgressData.get(server);
        for (String t : ProgressRules.TITAN_ORDER) {
            boolean mine = Progression.stopped(server, player.getUUID(), t);
            boolean world = data.worldStopped(t);
            String key = mine ? "message.skyabyss.progress.row_mine" : world ? "message.skyabyss.progress.row_world"
                    : Progression.worldAllows(server, t) ? "message.skyabyss.progress.row_open" : "message.skyabyss.progress.row_sleep";
            text.append("\n").append(Component.translatable(key, Progression.titanName(t),
                    ProgressRules.TITAN_LEVEL[ProgressRules.titanIndex(t)], data.worldFirstBy.getOrDefault(t, "-"))
                    .withStyle(mine ? ChatFormatting.GOLD : world ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        // 天の鍵 (1 章の終わり。story/SkyKey)
        jp.revati.skyabyss.story.StoryData story = jp.revati.skyabyss.story.StoryData.get(server);
        Component key = story.assembled(player.getUUID())
                ? Component.translatable("message.skyabyss.story.key_yes", story.day(player.getUUID()) + 1)
                : Component.translatable("message.skyabyss.story.key_no", jp.revati.skyabyss.story.SkyKey.fragmentsHeld(player));
        text.append("\n").append(Component.translatable("message.skyabyss.story.show_key", key).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        String t = titan(ctx);
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        MinecraftServer server = ctx.getSource().getServer();
        for (ServerPlayer p : players) {
            if (grant) {
                // 前の巨神も順に (段階は続いている所までしか数えないため)
                for (int i = 0; i <= ProgressRules.titanIndex(t); i++) {
                    Progression.onTitanStopped(server, List.<UUID>of(p.getUUID()), ProgressRules.TITAN_ORDER.get(i));
                }
            } else {
                ProgressData.get(server).clearPersonal(p.getUUID(), t);
                jp.revati.skyabyss.party.PartyNetwork.syncSoon(p);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal((grant ? "granted " : "revoked ") + t + " for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int world(CommandContext<CommandSourceStack> ctx, boolean grant) throws CommandSyntaxException {
        String t = titan(ctx);
        ProgressData data = ProgressData.get(ctx.getSource().getServer());
        if (grant) {
            data.markWorld(t, ctx.getSource().getTextName(), ctx.getSource().getServer().overworld().getGameTime() / 24000L);
        } else {
            data.clearWorld(t);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("world " + (grant ? "granted " : "revoked ") + t), true);
        return 1;
    }
}
