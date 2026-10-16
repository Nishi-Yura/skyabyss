package jp.revati.skyabyss.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 控え帳のコマンド。
//   /skyrecord [つまみ]                 : 自分の控え帳を開く (誰でも)
//   /skyabyss inspect <人> [つまみ]      : ほかの人の控え帳を開く (管理者)
// あわせて、管理者が /skyabyss … を実行したら、控え帳の値が変わった人ごとに「変わった値」をチャットの見出しで返す
// (テスト用のコマンドで何が変わったか、すぐ確かめられるように)。/titanmemory と /growth のあとには、画面への道を一行添える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RecordCommands {
    public static final String COMMAND = "skyrecord";
    // 変わった値を一度に出す行の数 (残りは「ほか n 件」)
    static final int MAX_CHANGE_LINES = 10;

    private record Watch(CommandSourceStack source, Map<UUID, RecordSnapshot> before) {
    }

    private record Link(UUID player, String tab) {
    }

    private static final List<Watch> WATCHES = new ArrayList<>();
    private static final List<Link> LINKS = new ArrayList<>();

    private RecordCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal(COMMAND)
                .executes(ctx -> openSelf(ctx, ""))
                .then(Commands.argument("tab", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RecordSnapshot.TAB_IDS, b))
                        .executes(ctx -> openSelf(ctx, StringArgumentType.getString(ctx, "tab")))));
        d.register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> inspect(ctx, ""))
                                .then(Commands.argument("tab", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RecordSnapshot.TAB_IDS, b))
                                        .executes(ctx -> inspect(ctx, StringArgumentType.getString(ctx, "tab")))))));
    }

    private static String tab(String tab) {
        return RecordSnapshot.TAB_IDS.contains(RecordSnapshot.parentTab(tab)) ? tab : "";
    }

    private static int openSelf(CommandContext<CommandSourceStack> ctx, String tab) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return RecordService.open(player, player, tab(tab)) ? 1 : 0;
    }

    private static int inspect(CommandContext<CommandSourceStack> ctx, String tab) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        return RecordService.open(viewer, target, tab(tab)) ? 1 : 0;
    }

    // ---------------------------------------------------------------- 変わった値の見出し
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onCommand(CommandEvent event) {
        if (event.isCanceled() || event.getException() != null) {
            return;
        }
        String input = event.getParseResults().getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String[] words = input.split("\\s+");
        if (words.length == 0) {
            return;
        }
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        String root = words[0];
        if (root.equals(SkyAbyss.MODID) && source.hasPermission(2)) {
            if (words.length > 1 && words[1].equals("inspect")) {
                return;
            }
            MinecraftServer server = source.getServer();
            Map<UUID, RecordSnapshot> before = new HashMap<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                before.put(p.getUUID(), RecordBuilder.build(p, true));
            }
            if (!before.isEmpty()) {
                WATCHES.add(new Watch(source, before));
            }
        } else if (words.length == 1 && (root.equals("titanmemory") || root.equals("growth")) && source.getEntity() instanceof ServerPlayer p) {
            LINKS.add(new Link(p.getUUID(), root.equals("growth") ? RecordSnapshot.GROWTH : RecordSnapshot.TITANS));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHES.isEmpty() && LINKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<Watch> watches = new ArrayList<>(WATCHES);
        WATCHES.clear();
        for (Watch w : watches) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                RecordSnapshot before = w.before().get(p.getUUID());
                if (before == null) {
                    continue;
                }
                List<RecordSnapshot.Change> changes = RecordSnapshot.diff(before, RecordBuilder.build(p, true)).stream()
                        .filter(c -> !RecordSnapshot.isVolatile(c.key())).toList();
                if (!changes.isEmpty()) {
                    report(w.source(), p, changes);
                }
            }
        }
        List<Link> links = new ArrayList<>(LINKS);
        LINKS.clear();
        for (Link link : links) {
            ServerPlayer p = server.getPlayerList().getPlayer(link.player());
            if (p != null) {
                p.sendSystemMessage(openLink("/" + COMMAND + " " + link.tab()));
            }
        }
    }

    static MutableComponent openLink(String command) {
        return Component.literal("  ").append(RecordBuilder.t("chat.open_link").withStyle(s -> s.withColor(0x7FD6D0).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
    }

    static void report(CommandSourceStack source, ServerPlayer target, List<RecordSnapshot.Change> changes) {
        MutableComponent head = Component.literal("✦ ").withStyle(s -> s.withColor(0x9FE8FF))
                .append(RecordBuilder.t("chat.changed", target.getDisplayName()).withStyle(s -> s.withColor(0x9FE8FF)));
        if (source.getEntity() instanceof ServerPlayer) {
            head.append(" ").append(openLink("/" + SkyAbyss.MODID + " inspect " + target.getGameProfile().getName()));
        }
        source.sendSystemMessage(head);
        int shown = 0;
        for (RecordSnapshot.Change c : changes) {
            if (shown >= MAX_CHANGE_LINES) {
                source.sendSystemMessage(RecordBuilder.t("chat.more", changes.size() - shown).withStyle(ChatFormatting.GRAY));
                break;
            }
            String tabId = c.key().substring(0, Math.max(0, c.key().indexOf('/')));
            source.sendSystemMessage(Component.literal("   ").append(RecordBuilder.t("chat.change_line",
                    RecordBuilder.t("tab." + tabId), c.label(), c.before().copy().withStyle(ChatFormatting.GRAY),
                    c.after().copy().withStyle(s -> s.withColor(0xF2C14E)))));
            shown++;
        }
    }
}
