package jp.revati.skyabyss.dev.play;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.playtest.Playtest;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /skyabyss play ... : 試遊用の近道 (OP、権限 2)。章・場所・道具・ボス・クエスト・状態を名前で切り替える。
// 既存の /skyabyss playtest はそのまま残す。docs/design/37 の 2〜4 節
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PlayCommands {
    private PlayCommands() {
    }

    static final int HELP_LINES = 12;

    private interface Fn {
        int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) throws CommandSyntaxException;
    }

    private interface One {
        void run(ServerPlayer p) throws CommandSyntaxException;
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2)).then(tree()));
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree() {
        LiteralArgumentBuilder<CommandSourceStack> play = Commands.literal("play");
        play.then(Commands.literal("help").executes(PlayCommands::help));
        play.then(targeted(Commands.literal("status"), (ctx, t) -> {
            for (ServerPlayer p : t) {
                status(ctx, p);
            }
            return t.size();
        }));
        // 章: 自分だけ。stage を渡すとその章の主線の段階を決める
        play.then(Commands.literal("chapter").then(Commands.argument("n", IntegerArgumentType.integer(0, PlayChapters.CHAPTERS - 1))
                .executes(ctx -> chapter(ctx, null))
                .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(stageIds(), b))
                        .executes(ctx -> chapter(ctx, StringArgumentType.getString(ctx, "stage"))))));
        play.then(Commands.literal("goto").then(targeted(Commands.argument("place", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PlayPlaces.names(), b)),
                (ctx, t) -> {
                    String place = StringArgumentType.getString(ctx, "place");
                    return each(ctx, t, "goto", p -> {
                        String miss = PlayPlaces.go(p, place);
                        if (miss != null) {
                            p.sendSystemMessage(Component.translatable(miss, place).withStyle(ChatFormatting.RED));
                        }
                    });
                })));
        play.then(Commands.literal("kit").then(targeted(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PlayChapters.KITS, b)),
                (ctx, t) -> {
                    String name = StringArgumentType.getString(ctx, "name");
                    int tier = PlayChapters.kitTier(name);
                    if (tier < 0) {
                        return unknown(ctx, name);
                    }
                    return each(ctx, t, "kit", p -> PlayChapters.kit(p, tier));
                })));
        play.then(Commands.literal("boss").then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PlayChapters.BOSSES, b))
                .executes(ctx -> boss(ctx, null))
                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 4)).executes(ctx -> boss(ctx,
                        IntegerArgumentType.getInteger(ctx, "tier"))))));
        play.then(Commands.literal("quest").then(Commands.argument("id", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(QuestManager.all().stream().map(QuestDef::id), b))
                .executes(ctx -> quest(ctx, "show"))
                .then(Commands.literal("done").executes(ctx -> quest(ctx, "done")))
                .then(Commands.literal("reset").executes(ctx -> quest(ctx, "reset")))
                .then(Commands.argument("stage", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(stageIds(), b))
                        .executes(ctx -> quest(ctx, StringArgumentType.getString(ctx, "stage"))))));
        play.then(Commands.literal("lore")
                .then(targeted(Commands.literal("unlock-all"), (ctx, t) -> each(ctx, t, "lore_all", PlayCommands::unlockAllLore)))
                .then(targeted(Commands.literal("reset"), (ctx, t) -> each(ctx, t, "lore_reset", Lore::reset))));
        play.then(Commands.literal("time")
                .then(Commands.literal("day").executes(ctx -> time(ctx, 1000)))
                .then(Commands.literal("noon").executes(ctx -> time(ctx, 6000)))
                .then(Commands.literal("night").executes(ctx -> time(ctx, 13000)))
                .then(Commands.literal("midnight").executes(ctx -> time(ctx, 18000))));
        play.then(Commands.literal("weather")
                .then(Commands.literal("clear").executes(ctx -> run(ctx, "weather clear", "clear")))
                .then(Commands.literal("rain").executes(ctx -> run(ctx, "weather rain", "rain")))
                .then(Commands.literal("thunder").executes(ctx -> run(ctx, "weather thunder", "thunder"))));
        play.then(Commands.literal("peaceful")
                .then(Commands.literal("on").executes(ctx -> run(ctx, "difficulty peaceful", "peaceful_on")))
                .then(Commands.literal("off").executes(ctx -> run(ctx, "difficulty normal", "peaceful_off"))));
        play.then(targeted(Commands.literal("heal"), (ctx, t) -> each(ctx, t, "heal", Playtest::heal)));
        play.then(targeted(Commands.literal("feed"), (ctx, t) -> each(ctx, t, "feed", p -> {
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20f);
        })));
        play.then(Commands.literal("god")
                .then(targeted(Commands.literal("on"), (ctx, t) -> each(ctx, t, "god_on", p -> Playtest.god(p, true))))
                .then(targeted(Commands.literal("off"), (ctx, t) -> each(ctx, t, "god_off", p -> Playtest.god(p, false)))));
        play.then(Commands.literal("reset").then(Commands.literal("me").executes(ctx -> reset(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> reset(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
        return play;
    }

    // node の末尾 (引数のあと) に [targets] (省略で自分) を付けて fn をつなぐ
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T targeted(T node, Fn fn) {
        return node.executes(ctx -> fn.run(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> fn.run(ctx, EntityArgument.getPlayers(ctx, "targets"))));
    }

    // 主線の段階の候補 (全部の主線の段階 id を並べる。重複は除く)
    static List<String> stageIds() {
        return QuestManager.all().stream().flatMap(d -> d.stages().stream().map(QuestDef.Stage::id)).distinct().sorted().toList();
    }

    private static int chapter(CommandContext<CommandSourceStack> ctx, String stage) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        String miss = PlayChapters.chapter(p, n, stage);
        if (miss != null) {
            ctx.getSource().sendFailure(Component.translatable(miss));
            return 0;
        }
        say(ctx, "chapter", n, stage == null ? "-" : stage);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> ctx, Integer tier) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        String miss = PlayChapters.boss(p, name, tier);
        if (miss != null) {
            ctx.getSource().sendFailure(Component.translatable(miss, name));
            return 0;
        }
        say(ctx, "boss", name);
        return 1;
    }

    private static int quest(CommandContext<CommandSourceStack> ctx, String action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        if (QuestManager.quest(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss_play.unknown_quest", id));
            return 0;
        }
        switch (action) {
            case "show" -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.quest_line", questLine(p, id)), false);
                return 1;
            }
            case "done" -> PlayChapters.finish(p, id);
            case "reset" -> Quests.setStage(p, id, null);
            default -> {
                if (!Quests.setStage(p, id, action)) {
                    ctx.getSource().sendFailure(Component.translatable("commands.skyabyss_play.bad_stage", action));
                    return 0;
                }
            }
        }
        String label = switch (action) {
            case "done" -> "commands.skyabyss_play.act.quest_done";
            case "reset" -> "commands.skyabyss_play.act.quest_reset";
            default -> "commands.skyabyss_play.act.quest_stage";
        };
        say(ctx, "quest", id, Component.translatable(label, action));
        return 1;
    }

    private static int time(CommandContext<CommandSourceStack> ctx, int tick) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Playtest.run(p, "time set " + tick);
        say(ctx, "time", tick);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String cmd, String act) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int n = Playtest.run(p, cmd);
        say(ctx, act);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t) {
        return each(ctx, t, "reset", Playtest::reset);
    }

    // 状態の表示: 主線の段階と、四つの巨神のうち止めたもの
    private static void status(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.status_head", p.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
        for (String q : PlayChapters.MAIN) {
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.quest_line", questLine(p, q)), false);
        }
        MutableComponent titans = Component.empty();
        for (String t : ProgressRules.TITAN_ORDER) {
            if (!titans.getSiblings().isEmpty()) {
                titans.append(" / ");
            }
            titans.append(t + " = ").append(Component.translatable(PlayChapters.stilled(p, t) ? "commands.skyabyss_play.stilled" : "commands.skyabyss_play.not_yet"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.titans_line", titans), false);
    }

    // 一つの主線の状態を一行に (未着手・完了・段階名)
    static String questLine(ServerPlayer p, String id) {
        QuestLog.Entry e = QuestLog.of(p).entry(id);
        if (e == null) {
            return id + ": 未着手";
        }
        if (e.done) {
            return id + ": 完了";
        }
        QuestDef def = QuestManager.quest(id);
        String stage = def == null || e.stage >= def.stages().size() ? "?" : def.stages().get(e.stage).id();
        return id + ": " + stage;
    }

    private static void unlockAllLore(ServerPlayer p) {
        for (LoreEntry e : LoreManager.catalog().entries()) {
            if (e.unlockable()) {
                Lore.unlock(p, e.id(), false);
            }
        }
    }

    private static int each(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String act, One one) {
        int n = 0;
        for (ServerPlayer p : targets) {
            try {
                one.run(p);
                n++;
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            }
        }
        final int count = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.done", Component.translatable("commands.skyabyss_play.act." + act), count)
                .withStyle(ChatFormatting.AQUA), true);
        return n;
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String act, Object... args) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.said." + act, args).withStyle(ChatFormatting.AQUA), true);
    }

    private static int unknown(CommandContext<CommandSourceStack> ctx, String what) {
        ctx.getSource().sendFailure(Component.translatable("commands.skyabyss_play.unknown", what));
        return 0;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        for (int i = 0; i < HELP_LINES; i++) {
            int k = i;
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.help." + k), false);
        }
        return 1;
    }
}
