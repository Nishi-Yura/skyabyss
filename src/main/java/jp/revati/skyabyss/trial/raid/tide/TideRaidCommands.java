package jp.revati.skyabyss.trial.raid.tide;

import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影 (OP): 潮殻のレイドの部屋の途中の姿を作る。/skytideraid shells = 貝の一組を揃え、もう一つ開けて待たせる。/skytideraid hurt = 苗を少しかじられた姿にする
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideRaidCommands {
    private TideRaidCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skytideraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("shells").executes(TideRaidCommands::shells))
                .then(Commands.literal("hurt").executes(TideRaidCommands::hurt)));
    }

    private static int shells(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RaidRun.Run run = RaidRun.of(Groups.keyOf(p));
        if (run == null || !(run.room instanceof TideShellRoom r)) {
            return 0;
        }
        int j = -1;
        for (int k = 1; k < r.shells(); k++) {
            if (r.toneOf(k) == r.toneOf(0)) {
                j = k;
            }
        }
        r.open(run.ctx, 0);
        r.open(run.ctx, j);
        // もう一組の片方だけ開けて、見せておく
        for (int k = 1; k < r.shells(); k++) {
            if (!r.isOpen(k)) {
                r.open(run.ctx, k);
                break;
            }
        }
        return 1;
    }

    private static int hurt(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RaidRun.Run run = RaidRun.of(Groups.keyOf(p));
        if (run == null || !(run.room instanceof TideSaplingRoom r)) {
            return 0;
        }
        r.damage(run.ctx, 0, 12);
        r.damage(run.ctx, 2, 24);
        return 1;
    }
}
