package jp.revati.skyabyss.titan.tide.chapter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 二章のテスト用コマンド (OP)。
// /skyabyss tide conch        : 潮見の貝を出す (なくしたとき。渡した印は変えない)
// /skyabyss tide ebb start    : 今いる所の近くで大干潮を始める (時刻・章・巨神を問わない。途中の道と重なるなら何もしない)
// /skyabyss tide ebb end      : 途中の大干潮を終える (水を戻す)
// /skyabyss tide ebb reset    : 大干潮の記録 (途中のもの・迎えた人) を消す (道の床は海の底に残る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideChapterCommands {
    private TideChapterCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(op(SkyAbyss.MODID).then(op("tide")
                .then(op("conch").executes(TideChapterCommands::conch))
                .then(op("ebb")
                        .then(op("start").executes(ctx -> ebb(ctx, "start")))
                        .then(op("end").executes(ctx -> ebb(ctx, "end")))
                        .then(op("reset").executes(ctx -> ebb(ctx, "reset"))))));
    }

    private static int conch(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack shell = new ItemStack(TideChapterSetup.TIDE_WATCHING_SHELL.get());
        if (!player.getInventory().add(shell)) {
            player.drop(shell, false);
        }
        return 1;
    }

    private static int ebb(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.server.overworld();
        TideEbb.EbbData data = TideEbb.EbbData.get(level);
        switch (what) {
            case "start" -> {
                                TideEbb.Plan plan = player.level() == level ? TideEbb.plan(level, player.getBlockX(), player.getBlockZ()) : null;
                if (plan == null) {
                    ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.ch2.ebb.no_sea"));
                    return 0;
                }
                if (!TideEbb.begin(level, data, plan)) {
                    ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.ch2.ebb.not_none"));
                    return 0;
                }
            }
            case "end" -> {
                if (!data.active()) {
                    ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.ch2.ebb.not_active"));
                    return 0;
                }
                TideEbb.end(level, data);
            }
            default -> {
                data.runs.clear();
                data.witnessed.clear();
                data.setDirty();
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.skyabyss.ch2.ebb." + what), true);
        return 1;
    }
}
