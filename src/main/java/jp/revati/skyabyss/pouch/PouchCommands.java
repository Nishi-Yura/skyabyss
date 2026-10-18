package jp.revati.skyabyss.pouch;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 袋の確かめ用のコマンド (OP。画面ができるまでの仮。docs/design/22 の 4.4・9.1)。
//   /skyabyss pouch list              : 袋の中身を一覧する
//   /skyabyss pouch take <品> [個数]   : 袋から持ち物へ出す (取り出しの決まりを無視する管理者用。「取り出し済み」の印が付く)
//   /skyabyss pouch hold on|off       : on の間は掃除が持ち物の品を袋へ移さない (袋の品を作って持つとき)
//   /skyabyss pouch migrate           : 既存セーブの移し替えをもう一度流す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PouchCommands {
    private PouchCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(op(SkyAbyss.MODID).then(op("pouch")
                .then(op("list").executes(PouchCommands::list))
                .then(op("take").then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                PouchEntries.enabled().stream().map(PouchEntries.Entry::id), builder))
                        .executes(ctx -> take(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1)).executes(ctx -> take(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(op("hold").then(Commands.argument("on", BoolArgumentType.bool()).executes(PouchCommands::hold)))
                .then(op("migrate").executes(PouchCommands::migrate))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var lines = Pouch.describe(player);
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("skyabyss_pouch.cmd.empty", Component.translatable("skyabyss_pouch.name")), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("skyabyss_pouch.cmd.header", Component.translatable("skyabyss_pouch.name"), lines.size()), false);
        lines.forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + line), false));
        return lines.size();
    }

    private static int take(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        PouchEntries.Entry entry = PouchEntries.byId(id);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.translatable("skyabyss_pouch.cmd.unknown", id.toString()));
            return 0;
        }
        int moved = Pouch.withdraw(player, entry.item(), count, true);
        if (moved <= 0) {
            ctx.getSource().sendFailure(Component.translatable("skyabyss_pouch.cmd.none", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("skyabyss_pouch.cmd.took", moved, entry.item().getDescription()), false);
        return moved;
    }

    private static int hold(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean on = BoolArgumentType.getBool(ctx, "on");
        Pouch.data(player).hold = on;
        Pouch.sync(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(on ? "skyabyss_pouch.cmd.hold_on" : "skyabyss_pouch.cmd.hold_off"), false);
        return 1;
    }

    private static int migrate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int moved = PouchMigration.run(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("skyabyss_pouch.cmd.migrated", moved), false);
        return moved;
    }
}
