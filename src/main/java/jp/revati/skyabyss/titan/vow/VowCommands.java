package jp.revati.skyabyss.titan.vow;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /vow : 誓いの選び方 (周回の縛り。docs/design/21 の 1.8)。チャットの一覧の [+] [-] を押して選ぶ。
//   /vow                          今いる背中の巨神 (背中の外では、周回の巨神のうち最初の一つ) の一覧
//   /vow <巨神>                   その巨神の一覧
//   /vow <巨神> add|remove <誓い>  足す・外す (最大 3 つ。選べるのは周回で、試練を進める前まで)
//   /vow <巨神> clear             全部外す
// 選んだ誓いはグループ (パーティ) のもの。変えると、同じグループの全員に知らせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class VowCommands {
    private VowCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vow").executes(ctx -> menu(ctx, defaultTitan(ctx.getSource().getPlayerOrException())));
        root.then(Commands.argument("titan", StringArgumentType.word())
                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Titans.all(), b))
                .executes(ctx -> menu(ctx, StringArgumentType.getString(ctx, "titan")))
                .then(Commands.literal("add").then(Commands.argument("vow", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Vows.all().stream().map(Vow::id), b))
                        .executes(ctx -> change(ctx, true))))
                .then(Commands.literal("remove").then(Commands.argument("vow", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Vows.all().stream().map(Vow::id), b))
                        .executes(ctx -> change(ctx, false))))
                .then(Commands.literal("clear").executes(VowCommands::clear)));
        event.getDispatcher().register(root);
    }

    // 今いる背中の巨神。なければ誓いを立てている巨神、それもなければ最初の巨神
    static String defaultTitan(ServerPlayer p) {
        String here = Titans.titanOfBack(p.level().dimension());
        if (here != null) {
            return here;
        }
        UUID group = Groups.keyOf(p);
        for (String titan : Titans.all()) {
            var back = Vows.backLevel(p.server, titan);
            if (back != null && !Vows.active(back, group).isEmpty()) {
                return titan;
            }
        }
        return Titans.all().get(0);
    }

    private static int menu(CommandContext<CommandSourceStack> ctx, String titan) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        show(p, titan);
        return 1;
    }

    // 一覧をチャットに出す
    public static void show(ServerPlayer p, String titan) {
        if (Vows.backLevel(p.server, titan) == null) {
            p.sendSystemMessage(ModText.deny("message.skyabyss.vow.no_back"));
            return;
        }
        UUID group = Groups.keyOf(p);
        Vows.Result can = Vows.canChange(p.server, group, titan);
        List<Vow> active = Vows.active(Vows.backLevel(p.server, titan), group);
        p.sendSystemMessage(ModText.title("message.skyabyss.vow.menu_head").copy().append(Component.literal(" ")).append(Progression.titanName(titan)));
        if (can != Vows.Result.OK) {
            p.sendSystemMessage(ModText.hint("message.skyabyss.vow.closed." + can.name().toLowerCase()));
        }
        for (Vow v : Vows.all()) {
            boolean on = active.contains(v);
            MutableComponent line = Component.literal(on ? "[x] " : "[ ] ").append(Component.translatable(v.nameKey()))
                    .append(Component.literal("  +" + v.points() + "  ")).append(ModText.hint(v.descKey()));
            if (can == Vows.Result.OK) {
                String cmd = "/vow " + titan + (on ? " remove " : " add ") + v.id();
                line.append(Component.literal(on ? "  [-]" : "  [+]")
                        .withStyle(Style.EMPTY.withColor(on ? 0xD9779A : 0x9FE8FF).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))));
            }
            p.sendSystemMessage(line);
        }
        int level = Vows.level(active);
        p.sendSystemMessage(ModText.progress("message.skyabyss.vow.menu_foot", level, Math.round(Vows.multiplier(level) * 100), Vows.MAX_VOWS,
                FirstRunBest.of(p, titan)));
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String titan = StringArgumentType.getString(ctx, "titan");
        String id = StringArgumentType.getString(ctx, "vow");
        UUID group = Groups.keyOf(p);
        Vows.Result r = add ? Vows.add(p.server, group, titan, id) : Vows.remove(p.server, group, titan, id);
        if (r == Vows.Result.OK) {
            Vow v = Vows.get(id);
            for (ServerPlayer m : p.server.getPlayerList().getPlayers()) {
                if (Groups.isMember(m, group)) {
                    Notice.hint(m, add ? "message.skyabyss.vow.added" : "message.skyabyss.vow.removed", p.getDisplayName(),
                            Component.translatable(v.nameKey()), Vows.level(Vows.active(Vows.backLevel(p.server, titan), group)));
                }
            }
        } else {
            p.sendSystemMessage(ModText.deny("message.skyabyss.vow.fail." + r.name().toLowerCase()));
        }
        show(p, titan);
        return r == Vows.Result.OK ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String titan = StringArgumentType.getString(ctx, "titan");
        Vows.Result r = Vows.clear(p.server, Groups.keyOf(p), titan);
        if (r != Vows.Result.OK) {
            p.sendSystemMessage(ModText.deny("message.skyabyss.vow.fail." + r.name().toLowerCase()));
        }
        show(p, titan);
        return r == Vows.Result.OK ? 1 : 0;
    }

    // 最高の段 (人ごと)
    static final class FirstRunBest {
        static int of(ServerPlayer p, String titan) {
            return jp.revati.skyabyss.titan.group.FirstRunData.get(p.server).vowBest(p.getUUID(), titan);
        }
    }
}
