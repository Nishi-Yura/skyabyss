package jp.revati.skyabyss.loot.box;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.pouch.PouchRouting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 試遊の近道 (docs/design/52 の 7 章):
//   /skyabyss play box give <種類> [数]  箱を出す (封の列は空なので、開けると箱の表から引く)
//   /skyabyss play box open             封切り師なしで、その場で封切り師の画面を開く
public final class BoxCommands {
    private BoxCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("box")
                .then(Commands.literal("give").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(BoxKind.ALL.stream().map(BoxKind::id), b))
                        .executes(ctx -> give(ctx, 1))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 999)).executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("open").executes(ctx -> {
                    BoxService.open(ctx.getSource().getPlayerOrException(), null);
                    return 1;
                }));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "kind");
        BoxKind kind = BoxKind.byId(id.startsWith("box_") ? id : "box_" + id);
        Item item = kind == null ? null : LootBoxSetup.item(kind);
        if (item == null) {
            ctx.getSource().sendFailure(Component.literal("unknown box: " + id));
            return 0;
        }
        PouchRouting.giveOrDrop(p, new ItemStack(item, n));
        ctx.getSource().sendSuccess(() -> Component.literal("gave " + n + " x " + kind.id()), false);
        return n;
    }
}
