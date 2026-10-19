package jp.revati.skyabyss.playtest;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

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

// /skyabyss playtest ... と /試遊 ... : 試遊用の近道 (OP。製品の jar でも使える)。どれも最後に [対象] (省略で自分)。docs/playtest_commands.md
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PlaytestCommands {
    private PlaytestCommands() {
    }

    private interface Fn {
        int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) throws CommandSyntaxException;
    }

    private interface One {
        void run(ServerPlayer p) throws CommandSyntaxException;
    }

    // 語の並び: help skip opening whale board goto trials gear chapter heal day fast reset god now next
    static final String[] EN = {"help", "skip", "opening", "whale", "board", "goto", "trials", "gear", "chapter", "heal", "day", "fast", "reset", "god",
            "now", "next"};
    static final String[] JA = {"説明", "とばし", "序章", "鯨", "乗る", "移動", "試練", "装備", "章", "回復", "日", "早送り", "初期化", "無敵", "いま", "つぎ"};

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2)).then(tree("playtest", EN, false)));
    }

    // 日本語は「序章とばし」を一語にしたいので、skip の節は ja では「序章とばし」(n[2]+n[1]) の一語にする
    static LiteralArgumentBuilder<CommandSourceStack> tree(String root, String[] n, boolean ja) {
        LiteralArgumentBuilder<CommandSourceStack> pt = Commands.literal(root);
        pt.then(Commands.literal(n[0]).executes(PlaytestCommands::help));
        if (ja) {
            pt.then(targeted(Commands.literal(n[2] + n[1]), (ctx, t) -> each(ctx, t, "skip_opening", Playtest::skipOpening)));
        } else {
            pt.then(Commands.literal(n[1]).then(targeted(Commands.literal(n[2]), (ctx, t) -> each(ctx, t, "skip_opening", Playtest::skipOpening))));
        }
        pt.then(targeted(Commands.literal(n[3]), (ctx, t) -> each(ctx, t, "whale", Playtest::whale)));
        pt.then(Commands.literal(n[4]).then(targeted(titanArg(), (ctx, t) -> {
            int idx = titan(ctx);
            return each(ctx, t, "board", p -> {
                Playtest.board(p, idx, pp -> {
                });
            });
        })));
        pt.then(Commands.literal(n[5]).then(titanArg().then(targeted(Commands.argument("spot", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Stream.of(0, 1, 2, 3).flatMap(i -> Playtest.spots(i).stream()).distinct(), b)),
                (ctx, t) -> {
                    int idx = titan(ctx);
                    String spot = switch (StringArgumentType.getString(ctx, "spot")) {
                        case "心臓" -> "heart";
                        case "中枢", "拠点" -> "hub";
                        default -> StringArgumentType.getString(ctx, "spot");
                    };
                    return each(ctx, t, "goto", p -> {
                        Playtest.board(p, idx, pp -> Playtest.gotoSpot(pp, idx, spot));
                    });
                }))));
        pt.then(Commands.literal(n[6]).then(targeted(titanArg(), (ctx, t) -> {
            int idx = titan(ctx);
            int c = 0;
            for (java.util.UUID g : Playtest.groupsOf(t)) {
                c += Playtest.trials(ctx.getSource().getServer(), g, idx) ? 1 : 0;
            }
            return done(ctx, "trials", c);
        })));
        pt.then(Commands.literal(n[7]).then(targeted(Commands.argument("tier", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("0", "1", "2", "3", "4", "whale", "tide", "storm", "ember"), b)),
                (ctx, t) -> {
                    String s = StringArgumentType.getString(ctx, "tier");
                    int tier = s.length() == 1 && Character.isDigit(s.charAt(0)) ? s.charAt(0) - '0' : Playtest.index(s);
                    if (tier < 0 || tier > 4) {
                        return unknown(ctx, s);
                    }
                    return each(ctx, t, "gear", p -> Playtest.gear(p, tier));
                })));
        pt.then(Commands.literal(n[8]).then(targeted(Commands.argument("n", IntegerArgumentType.integer(1, 4)), (ctx, t) -> {
            int c = IntegerArgumentType.getInteger(ctx, "n");
            return each(ctx, t, "chapter", p -> Playtest.chapter(p, c));
        })));
        pt.then(targeted(Commands.literal(n[9]), (ctx, t) -> each(ctx, t, "heal", Playtest::heal)));
        pt.then(Commands.literal(n[10]).then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
            Playtest.day(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "n"));
            return done(ctx, "day", 1);
        })));
        pt.then(Commands.literal(n[11]).then(onOff((ctx, on) -> {
            Playtest.fast(ctx.getSource().getServer(), on);
            return done(ctx, on ? "fast_on" : "fast_off", 1);
        })));
        pt.then(targeted(Commands.literal(n[12]), (ctx, t) -> each(ctx, t, "reset", Playtest::reset)));
        pt.then(Commands.literal(n[13]).then(onOffTargeted((ctx, t, on) -> each(ctx, t, on ? "god_on" : "god_off", p -> Playtest.god(p, on)))));
        pt.then(targeted(Commands.literal(n[14]), (ctx, t) -> now(ctx, t, false)));
        pt.then(targeted(Commands.literal(n[15]), (ctx, t) -> now(ctx, t, true)));
        return pt;
    }

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        Playtest.tickWaits(event.getServer());
    }

    private static int now(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, boolean next) {
        for (ServerPlayer p : t) {
            String key = Playtest.now(p, next);
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.playtest.now", p.getDisplayName(), Component.translatable(key))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return t.size();
    }

    private interface OnOff {
        int run(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException;
    }

    private interface OnOffT {
        int run(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> t, boolean on) throws CommandSyntaxException;
    }

    private static boolean flag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String s = StringArgumentType.getString(ctx, "on");
        return switch (s) {
            case "true", "on", "入" -> true;
            case "false", "off", "切" -> false;
            default -> throw new SimpleCommandExceptionType(Component.translatable("commands.skyabyss.playtest.unknown", s)).create();
        };
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> onArg() {
        return Commands.argument("on", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off", "入", "切"), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> onOff(OnOff fn) {
        return onArg().executes(ctx -> fn.run(ctx, flag(ctx)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> onOffTargeted(OnOffT fn) {
        return targeted(onArg(), (ctx, t) -> fn.run(ctx, t, flag(ctx)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> titanArg() {
        return Commands.argument("titan", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Playtest.TITANS, b));
    }

    private static int titan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String s = StringArgumentType.getString(ctx, "titan");
        int idx = Playtest.index(s);
        if (idx < 0) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.skyabyss.playtest.unknown", s)).create();
        }
        return idx;
    }

    // node の末尾 (引数のあと) に [targets] を付けて fn をつなぐ
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T targeted(T node, Fn fn) {
        return node.executes(ctx -> fn.run(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> fn.run(ctx, EntityArgument.getPlayers(ctx, "targets"))));
    }

    private static int each(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets, String act, One one) throws CommandSyntaxException {
        for (ServerPlayer p : targets) {
            one.run(p);
        }
        return done(ctx, act, targets.size());
    }

    private static int done(CommandContext<CommandSourceStack> ctx, String act, int n) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.playtest.done",
                Component.translatable("commands.skyabyss.playtest.act." + act), n).withStyle(ChatFormatting.AQUA), true);
        return n;
    }

    private static int unknown(CommandContext<CommandSourceStack> ctx, String what) {
        ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.playtest.unknown", what));
        return 0;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        for (int i = 0; i < 13; i++) {
            int k = i;
            ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.playtest.help." + k), false);
        }
        return 1;
    }
}
