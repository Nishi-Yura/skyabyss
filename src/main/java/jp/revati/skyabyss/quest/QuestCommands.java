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
        registerDevTalk(d);
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

    // 開発用 (台本 first_hour.txt など): 本物の会話を開く・進める。DialogueService をそのまま通るので、会話の効果とクエストの進みも本物と同じ
    //   /skyabyss dev talk <役 例 skyabyss:storyteller>   : 近く (48 ブロック) のその役の NPC と会話を開く (4 ブロックより遠ければ NPC の脇へ寄る)
    //   /skyabyss dev talk say <continue|leave|番号>      : いまの節で選ぶ (continue = 続きへ、番号 = 選択肢の位置)
    private static void registerDevTalk(CommandDispatcher<CommandSourceStack> d) {
        if (net.neoforged.fml.loading.FMLEnvironment.production) {
            return;
        }
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("talk")
                        .then(Commands.literal("say").then(Commands.argument("choice", StringArgumentType.word()).executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String node = jp.revati.skyabyss.npc.DialogueService.currentNode(p);
                            if (node == null) {
                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("no dialogue is open"));
                                return 0;
                            }
                            String c = StringArgumentType.getString(ctx, "choice");
                            int index = c.equals("continue") ? jp.revati.skyabyss.npc.DialogueService.CONTINUE
                                    : c.equals("leave") ? jp.revati.skyabyss.npc.DialogueService.LEAVE : Integer.parseInt(c);
                            jp.revati.skyabyss.npc.DialogueService.choose(p, node, index);
                            describe(ctx.getSource(), p);
                            return 1;
                        })))
                        .then(Commands.argument("role", StringArgumentType.string()).executes(ctx -> {
                            net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayerOrException();
                            String role = StringArgumentType.getString(ctx, "role");
                            jp.revati.skyabyss.npc.NpcEntity best = null;
                            for (jp.revati.skyabyss.npc.NpcEntity npc : p.serverLevel().getEntitiesOfClass(jp.revati.skyabyss.npc.NpcEntity.class,
                                    p.getBoundingBox().inflate(48), n -> n.role().equals(role))) {
                                if (best == null || npc.distanceToSqr(p) < best.distanceToSqr(p)) {
                                    best = npc;
                                }
                            }
                            if (best == null) {
                                ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("no " + role + " within 48 blocks"));
                                return 0;
                            }
                            lastRole = role;
                            // 歩いて話しかける代わり: 遠ければ NPC の南 2.5 ブロックへ寄る (会話は 8 ブロックより離れると閉じる。NPC は歩き回る)
                            if (best.distanceToSqr(p) > 16) {
                                p.teleportTo(p.serverLevel(), best.getX(), best.getY(), best.getZ() + 2.5, 180f, 0f);
                            }
                            jp.revati.skyabyss.npc.DialogueService.open(p, best);
                            describe(ctx.getSource(), p);
                            return 1;
                        })))));
    }

    private static String lastRole = "";

    // いまの節の本文と、見えている選択肢 (番号つき) をコマンドの返事に出す (台本の記録に残る)
    private static void describe(CommandSourceStack source, net.minecraft.server.level.ServerPlayer p) {
        String node = jp.revati.skyabyss.npc.DialogueService.currentNode(p);
        jp.revati.skyabyss.npc.Dialogue d = jp.revati.skyabyss.npc.DialogueManager.dialogue(lastRole);
        if (node == null || d == null || d.nodes().get(node) == null) {
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("[dialogue] closed"), false);
            return;
        }
        jp.revati.skyabyss.npc.Dialogue.Node n = d.nodes().get(node);
        StringBuilder sb = new StringBuilder("[dialogue] " + lastRole + " node=" + node + " \"" + net.minecraft.network.chat.Component.translatable(n.textKey()).getString() + "\"");
        if (n.next() != null) {
            sb.append(" next=").append(n.next());
        }
        for (int i : jp.revati.skyabyss.npc.DialogueService.shownOptions(p)) {
            var o = n.options().get(i);
            sb.append(" | ").append(i).append(":").append(o.id()).append(" \"").append(net.minecraft.network.chat.Component.translatable(o.textKey()).getString()).append("\"");
        }
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(sb.toString()), false);
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
