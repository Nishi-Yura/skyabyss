package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影: /skyabyss moonworld enter | leave | gate (門へ行く) | phase (今の位相) | phase <0〜3> (位相の頭へ進める)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonCommands {
    private MoonCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyabyss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("moonworld")
                        .then(Commands.literal("enter").executes(c -> MoonWorld.enter(c.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("leave").executes(c -> {
                            MoonWorld.leave(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("gate").executes(c -> {
                            BlockPos g = MoonGate.gatePos(c.getSource().getServer());
                            if (g == null) {
                                c.getSource().sendFailure(Component.literal("the capital has no moon gate (built before the gate existed?)"));
                                return 0;
                            }
                            c.getSource().getPlayerOrException().teleportTo(c.getSource().getServer().overworld(), g.getX() + 0.5, g.getY(), g.getZ() + 2.5, 180f, 0f);
                            return 1;
                        }))
                        .then(Commands.literal("phase").executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            int ph = moon == null ? -1 : MoonClock.phase(moon);
                            c.getSource().sendSuccess(() -> Component.literal("moon phase " + (ph < 0 ? "-" : MoonClock.PHASES[ph] + " (" + MoonClock.untilNext(MoonClock.time(moon)) / 20 + " s to the next)")), false);
                            return 1;
                        }).then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                            ServerLevel moon = c.getSource().getServer().getLevel(MoonWorld.LEVEL);
                            if (moon == null) {
                                return 0;
                            }
                            MoonClock.skipTo(moon, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n"));
                            return 1;
                        })))));
    }
}
