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

    static final int HELP_LINES = 16;

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
        // ---- 章・ボス・レイド・クエスト (対象は引数のあと。省略できる引数があるものは、対象を先に置く)
        play.then(Commands.literal("chapter").then(Commands.argument("n", IntegerArgumentType.integer(0, PlayChapters.CHAPTERS - 1))
                .executes(ctx -> chapter(ctx, List.of(ctx.getSource().getPlayerOrException()), null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> chapter(ctx, EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(stageIds(), b))
                                .executes(ctx -> chapter(ctx, EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "stage")))))));
        play.then(Commands.literal("boss").then(Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PlayChapters.BOSSES, b))
                .executes(ctx -> boss(ctx, List.of(ctx.getSource().getPlayerOrException()), null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> boss(ctx, EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> boss(ctx, EntityArgument.getPlayers(ctx, "targets"), IntegerArgumentType.getInteger(ctx, "tier")))))));
        play.then(Commands.literal("raid").then(Commands.argument("titan", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Playtest.TITANS, b))
                .then(targeted(Commands.argument("room", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PlayRaid.rooms(), b)),
                        (ctx, t) -> {
                            String miss = PlayRaid.run(t, StringArgumentType.getString(ctx, "titan"), StringArgumentType.getString(ctx, "room"));
                            if (miss != null) {
                                ctx.getSource().sendFailure(Component.translatable(miss, StringArgumentType.getString(ctx, "room")));
                                return 0;
                            }
                            say(ctx, "raid", StringArgumentType.getString(ctx, "titan"), StringArgumentType.getString(ctx, "room"), t.size());
                            return t.size();
                        }))));
        play.then(Commands.literal("trials").then(targeted(Commands.argument("titan", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Playtest.TITANS, b)), (ctx, t) -> {
                    int idx = Playtest.index(StringArgumentType.getString(ctx, "titan"));
                    if (idx < 0) {
                        return unknown(ctx, StringArgumentType.getString(ctx, "titan"));
                    }
                    int c = 0;
                    for (java.util.UUID g : Playtest.groupsOf(t)) {
                        c += Playtest.trials(ctx.getSource().getServer(), g, idx) ? 1 : 0;
                    }
                    final int count = c;
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.done",
                            Component.translatable("commands.skyabyss_play.act.trials"), count).withStyle(ChatFormatting.AQUA), true);
                    return count;
                })));
        play.then(Commands.literal("skip").then(targeted(Commands.literal("opening"), (ctx, t) -> each(ctx, t, "skip_opening", Playtest::skipOpening))));
        play.then(targeted(Commands.literal("now"), (ctx, t) -> now(ctx, t, false)));
        LiteralArgumentBuilder<CommandSourceStack> quest = Commands.literal("quest");
        quest.then(targeted(Commands.literal("list"), (ctx, t) -> {
            for (ServerPlayer p : t) {
                ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.status_head", p.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
                List<String> lines = PlayQuests.list(p);
                if (lines.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.quest_none"), false);
                }
                for (String l : lines) {
                    ctx.getSource().sendSuccess(() -> Component.literal(l), false);
                }
            }
            return t.size();
        }));
        quest.then(Commands.literal("next")
                .executes(ctx -> questNext(ctx, List.of(ctx.getSource().getPlayerOrException()), null))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> questNext(ctx, EntityArgument.getPlayers(ctx, "targets"), null))
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS)
                                .executes(ctx -> questNext(ctx, EntityArgument.getPlayers(ctx, "targets"), StringArgumentType.getString(ctx, "id"))))));
        quest.then(Commands.literal("set").then(Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS)
                .then(targeted(Commands.argument("stage", StringArgumentType.word()).suggests((ctx, b) -> {
                    QuestDef def = QuestManager.quest(StringArgumentType.getString(ctx, "id"));
                    return SharedSuggestionProvider.suggest(def == null ? List.<String>of() : def.stages().stream().map(QuestDef.Stage::id).toList(), b);
                }), (ctx, t) -> questEach(ctx, t, StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "stage"))))));
        quest.then(Commands.literal("done").then(targeted(Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS),
                (ctx, t) -> questEach(ctx, t, StringArgumentType.getString(ctx, "id"), "done"))));
        quest.then(Commands.literal("reset").then(targeted(Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS),
                (ctx, t) -> questEach(ctx, t, StringArgumentType.getString(ctx, "id"), "reset"))));
        // 旧い形 (quest <id> [段階|done|reset]) も残す。自分だけ
        quest.then(Commands.argument("id", StringArgumentType.word()).suggests(QUEST_IDS)
                .executes(ctx -> questEach(ctx, List.of(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "id"), "show"))
                .then(Commands.literal("done").executes(ctx -> questEach(ctx, List.of(ctx.getSource().getPlayerOrException()),
                        StringArgumentType.getString(ctx, "id"), "done")))
                .then(Commands.literal("reset").executes(ctx -> questEach(ctx, List.of(ctx.getSource().getPlayerOrException()),
                        StringArgumentType.getString(ctx, "id"), "reset")))
                .then(Commands.argument("stage", StringArgumentType.word()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(stageIds(), b))
                        .executes(ctx -> questEach(ctx, List.of(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "stage")))));
        play.then(quest);
        play.then(Commands.literal("day").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
            Playtest.day(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "n"));
            say(ctx, "day", IntegerArgumentType.getInteger(ctx, "n"));
            return 1;
        })));
        play.then(Commands.literal("fast")
                .then(Commands.literal("on").executes(ctx -> fast(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> fast(ctx, false))));
        // reset: 対象を省略 = 自分。旧い形 (reset me [対象]) も残す
        play.then(targeted(Commands.literal("reset"), (ctx, t) -> reset(ctx, t))
                .then(Commands.literal("me").executes(ctx -> reset(ctx, List.of(ctx.getSource().getPlayerOrException())))));
        return play;
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> QUEST_IDS =
            (ctx, b) -> SharedSuggestionProvider.suggest(QuestManager.all().stream().map(QuestDef::id), b);

    private static int fast(CommandContext<CommandSourceStack> ctx, boolean on) {
        Playtest.fast(ctx.getSource().getServer(), on);
        say(ctx, on ? "fast_on" : "fast_off");
        return 1;
    }

    private static int now(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, boolean next) {
        for (ServerPlayer p : t) {
            String key = Playtest.now(p, next);
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.playtest.now", p.getDisplayName(), Component.translatable(key))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return t.size();
    }

    private static int questNext(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, String id) {
        int ok = 0;
        for (ServerPlayer p : t) {
            PlayQuests.Result r = PlayQuests.next(p, id);
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.quest_next", p.getDisplayName(), r.line())
                    .withStyle(r.ok() ? ChatFormatting.AQUA : ChatFormatting.YELLOW), true);
            ok += r.ok() ? 1 : 0;
        }
        return ok;
    }

    private static int questEach(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, String id, String action) {
        if (QuestManager.quest(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss_play.unknown_quest", id));
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : t) {
            switch (action) {
                case "show" -> {
                }
                case "done" -> PlayChapters.finish(p, id);
                case "reset" -> Quests.setStage(p, id, null);
                default -> {
                    if (!Quests.setStage(p, id, action)) {
                        ctx.getSource().sendFailure(Component.translatable("commands.skyabyss_play.bad_stage", action));
                        return n;
                    }
                }
            }
            n++;
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss_play.quest_next", p.getDisplayName(), PlayQuests.line(p, id)), false);
        }
        return n;
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

    private static int chapter(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, String stage) {
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int ok = 0;
        for (ServerPlayer p : t) {
            String miss = PlayChapters.chapter(p, n, stage);
            if (miss != null) {
                ctx.getSource().sendFailure(Component.translatable(miss, stage == null ? "-" : stage));
                continue;
            }
            ok++;
        }
        if (ok > 0) {
            say(ctx, "chapter", n, stage == null ? "-" : stage, ok);
        }
        return ok;
    }

    private static int boss(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, Integer tier) {
        String name = StringArgumentType.getString(ctx, "name");
        int ok = 0;
        for (ServerPlayer p : t) {
            String miss = PlayChapters.boss(p, name, tier);
            if (miss != null) {
                ctx.getSource().sendFailure(Component.translatable(miss, name));
                continue;
            }
            ok++;
        }
        if (ok > 0) {
            say(ctx, "boss", name, ok);
        }
        return ok;
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
