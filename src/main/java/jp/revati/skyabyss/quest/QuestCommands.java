package jp.revati.skyabyss.quest;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// クエストのテスト用コマンド (OP)。
//   /skyabyss quest list [人]                    : 進み具合 (段階と目的の数)
//   /skyabyss quest start <人> <クエスト>
//   /skyabyss quest stage <人> <クエスト> <段階>  : その段階から (途中の段階の報酬は渡さない)
//   /skyabyss quest next <人> <クエスト>         : 今の段階を済ませて次へ
//   /skyabyss quest forget <人> <クエスト|all>   : 記録から消す (報酬は一人一回なので、二度目は経験値が出ない)
//   /skyabyss quest signal <人> <合図>
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class QuestCommands {
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (ctx, b) -> SharedSuggestionProvider.suggest(
            QuestManager.all().stream().map(QuestDef::id), b);

    private QuestCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("start").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("quest", StringArgumentType.string()).suggests(QUESTS).executes(ctx -> each(ctx,
                                        p -> Quests.start(p, quest(ctx)))))))
                        .then(Commands.literal("stage").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("quest", StringArgumentType.string()).suggests(QUESTS)
                                        .then(Commands.argument("stage", StringArgumentType.word()).suggests((ctx, b) -> {
                                            QuestDef def = QuestManager.quest(StringArgumentType.getString(ctx, "quest"));
                                            return SharedSuggestionProvider.suggest(def == null ? java.util.stream.Stream.empty()
                                                    : def.stages().stream().map(QuestDef.Stage::id), b);
                                        }).executes(ctx -> each(ctx, p -> Quests.setStage(p, quest(ctx), StringArgumentType.getString(ctx, "stage"))))))))
                        .then(Commands.literal("next").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("quest", StringArgumentType.string()).suggests(QUESTS).executes(ctx -> each(ctx,
                                        p -> Quests.forceStage(p, quest(ctx)))))))
                        .then(Commands.literal("forget").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("quest", StringArgumentType.string()).suggests(QUESTS).executes(ctx -> each(ctx, p -> {
                                    if (quest(ctx).equals("all")) {
                                        Quests.forgetAll(p);
                                        return true;
                                    }
                                    return Quests.forget(p, quest(ctx));
                                })))))
                        .then(Commands.literal("signal").then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("signal", StringArgumentType.word()).executes(ctx -> each(ctx, p -> {
                                    Quests.signal(p, StringArgumentType.getString(ctx, "signal"));
                                    return true;
                                })))))));
    }

    private static String quest(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "quest");
    }

    interface PlayerOp {
        boolean apply(ServerPlayer player);
    }

    private static int each(CommandContext<CommandSourceStack> ctx, PlayerOp op) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        int n = 0;
        for (ServerPlayer p : players) {
            if (op.apply(p)) {
                n++;
            }
        }
        int done = n;
        ctx.getSource().sendSuccess(() -> Component.literal("ok for " + done + " / " + players.size() + " player(s)"), true);
        return done;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        QuestLog log = QuestLog.of(player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " — tracked: " + log.tracked()), false);
        for (QuestDef def : QuestManager.all()) {
            QuestLog.Entry e = log.entry(def.id());
            Component line;
            if (e == null) {
                line = Component.literal("  " + def.id() + ": -").withStyle(ChatFormatting.DARK_GRAY);
            } else if (e.done) {
                line = Component.literal("  " + def.id() + ": done").withStyle(ChatFormatting.AQUA);
            } else {
                QuestDef.Stage s = def.stages().get(Math.min(e.stage, def.stages().size() - 1));
                StringBuilder sb = new StringBuilder("  " + def.id() + ": " + s.id() + " (" + (e.stage + 1) + "/" + def.stages().size() + ")");
                for (int i = 0; i < s.objectives().size(); i++) {
                    sb.append(" ").append(s.objectives().get(i).id()).append("=").append(e.progress(i)).append("/").append(s.objectives().get(i).count());
                }
                line = Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE);
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return log.quests().size();
    }
}
