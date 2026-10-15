package jp.revati.skyabyss.lore;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
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

// 伝承の書のテスト用コマンド (OP)。
//   /skyabyss lore list [人]               : 記された項目を章ごとに見る
//   /skyabyss lore unlock <人> <項目|all>  : 記す (知らせも出る)
//   /skyabyss lore lock <人> <項目|all>    : 記録から消す
//   /skyabyss lore reset <人>              : 記録と「書を受け取った」記録をすべて消す (空の影から試し直す)
//   /skyabyss lore book [人]               : 伝承の書を渡す
//   /skyabyss lore page [人] [出どころ]    : 破れた頁を渡す (village / titan_back。省略するとどこの頁でもよい)
//   /skyabyss lore drop [人]               : 空から書が落ちてくる演出をすぐ起こす
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LoreCommands {
    private static final SuggestionProvider<CommandSourceStack> ENTRIES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
                    LoreManager.catalog().entries().stream().filter(LoreEntry::unlockable).map(LoreEntry::id)), builder);
    private static final SuggestionProvider<CommandSourceStack> SOURCES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            new String[] {"village", "titan_back"}, builder);

    private LoreCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("lore")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("entry", StringArgumentType.greedyString()).suggests(ENTRIES)
                                                .executes(ctx -> unlock(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "entry"))))))
                        .then(Commands.literal("lock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("entry", StringArgumentType.greedyString()).suggests(ENTRIES)
                                                .executes(ctx -> lock(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "entry"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> reset(ctx, EntityArgument.getPlayers(ctx, "players")))))
                        .then(Commands.literal("book")
                                .executes(ctx -> book(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> book(ctx, EntityArgument.getPlayers(ctx, "players")))))
                        .then(Commands.literal("page")
                                .executes(ctx -> page(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), ""))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> page(ctx, EntityArgument.getPlayers(ctx, "players"), ""))
                                        .then(Commands.argument("source", StringArgumentType.word()).suggests(SOURCES)
                                                .executes(ctx -> page(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "source"))))))
                        .then(Commands.literal("drop")
                                .executes(ctx -> drop(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> drop(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        LoreCatalog catalog = LoreManager.catalog();
        LoreState state = Lore.state(player);
        int total = (int) catalog.entries().stream().filter(LoreEntry::unlockable).count();
        int known = (int) catalog.entries().stream().filter(e -> e.unlockable() && state.has(e.id())).count();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.list", player.getDisplayName(), known, total), false);
        for (LoreChapter chapter : catalog.chapters()) {
            MutableComponent line = Component.literal(" ").append(Component.translatable(chapter.tabKey())).append(": ");
            boolean first = true;
            for (LoreEntry entry : catalog.inChapter(chapter.id())) {
                if (!first) {
                    line.append(" ");
                }
                first = false;
                String name = entry.id().substring(entry.id().indexOf('/') + 1);
                ChatFormatting color = entry.placeholder() ? ChatFormatting.DARK_GRAY : state.has(entry.id()) ? ChatFormatting.AQUA : ChatFormatting.GRAY;
                line.append(Component.literal(name).withStyle(color));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return known;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String entry) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : players) {
            if (entry.equals("all")) {
                for (LoreEntry e : LoreManager.catalog().entries()) {
                    if (Lore.unlock(player, e.id(), false)) {
                        count++;
                    }
                }
                Lore.sync(player);
            } else {
                if (LoreManager.catalog().entry(entry) == null) {
                    ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.lore.unknown", entry));
                    return 0;
                }
                if (Lore.unlock(player, entry, true)) {
                    count++;
                }
            }
        }
        int n = count;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.unlocked", n), true);
        return n;
    }

    private static int lock(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String entry) {
        int count = 0;
        for (ServerPlayer player : players) {
            if (entry.equals("all")) {
                for (LoreEntry e : LoreManager.catalog().entries()) {
                    if (Lore.lock(player, e.id())) {
                        count++;
                    }
                }
            } else if (Lore.lock(player, entry)) {
                count++;
            }
            Lore.sync(player);
        }
        int n = count;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.locked", n), true);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Lore.reset(player);
            Lore.sync(player);
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.reset", players.size()), true);
        return players.size();
    }

    private static int book(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Lore.giveBook(player, null);
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.book", players.size()), true);
        return players.size();
    }

    private static int page(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String source) {
        for (ServerPlayer player : players) {
            var stack = source.isEmpty() ? new net.minecraft.world.item.ItemStack(LoreSetup.LORE_PAGE.get()) : LorePageItem.of(source);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.page", players.size()), true);
        return players.size();
    }

    private static int drop(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        LoreEvents.forgetDrop(player);
        LoreEvents.dropBook(player, player.serverLevel(), player.serverLevel().getGameTime());
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.lore.drop", player.getDisplayName()), true);
        return 1;
    }
}
