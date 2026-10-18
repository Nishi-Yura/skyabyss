package jp.revati.skyabyss.titan.ember.back;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 余燼の背の体と広間のテスト用コマンド (OP。13 の 12章。担当 B の分。go・heat・status は骨組みの EmberCommands)
// /skyabyss titan reset ember (すべてのグループ) | rebuild ember | sleep ember <west|east|south|none> (実行した人のグループ)
// /skyabyss emberback ring <west|east|south> | ready <…> | gate <open|close> | state | resetgroup (どれも実行した人のグループ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberBackCommands {
    private EmberBackCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("reset").then(op(EmberIds.TITAN).executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.resetAll(back, true);
                    return "ember back trials reset (all groups)";
                }))))
                .then(op("rebuild").then(op(EmberIds.TITAN).executes(ctx -> run(ctx, back -> {
                    long ms = EmberBodyBuilder.build(back, TitanBackData.get(back));
                    return "ember back rebuilt in " + ms + " ms";
                }))))
                .then(op("sleep").then(op(EmberIds.TITAN).then(Commands.argument("trial", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"west", "east", "south", "none"}, b))
                        .executes(ctx -> run(ctx, back -> {
                            String id = StringArgumentType.getString(ctx, "trial");
                            EmberBackTrials.setSleeping(back, group(ctx), EmberBackState.Trial.byId(id));
                            return "ember sleeping: " + id;
                        })))))));
        d.register(op(SkyAbyss.MODID).then(op("emberback")
                .then(op("ring").then(trialArg().executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.ring(back, group(ctx), trial(ctx), null);
                    return "rang " + trial(ctx).id();
                }))))
                .then(op("ready").then(trialArg().executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.markReady(back, group(ctx), trial(ctx));
                    return "ready " + trial(ctx).id();
                }))))
                .then(op("gate").then(op("open").executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.openGate(back, group(ctx));
                    return "gate open";
                }))).then(op("close").executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.closeGate(back, group(ctx));
                    return "gate closed for your group (state unchanged)";
                }))))
                .then(op("resetgroup").executes(ctx -> run(ctx, back -> {
                    EmberBackTrials.resetGroup(back, group(ctx), true, null, false);
                    return "ember back trials reset for your group";
                })))
                .then(op("state").executes(ctx -> run(ctx, back -> {
                    EmberBackState s = EmberBackState.of(back, group(ctx));
                    StringBuilder b = new StringBuilder("ember back (group " + group(ctx) + ", epoch " + s.epoch() + "): rung");
                    for (EmberBackState.Trial t : EmberBackState.Trial.values()) {
                        b.append(' ').append(t.id()).append('=').append(s.rung(t)).append(s.ready(t) ? "(ready)" : "");
                    }
                    b.append(", sleeping ").append(s.sleeping() == null ? "none" : s.sleeping().id()).append(", gate ").append(s.gateOpen())
                            .append(", heart ").append(s.heartDefeated()).append(", seed ").append(s.seed()).append(", resets ").append(s.resets()).append(", build ")
                            .append(TitanBackData.get(back).layoutVersion()).append(", rooms");
                    for (EmberRooms.Room r : EmberRooms.ALL) {
                        b.append(' ').append(r.id()).append('=').append(EmberRooms.owner(r));
                    }
                    return b.toString();
                })))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> trialArg() {
        return Commands.argument("trial", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"west", "east", "south"}, b));
    }

    private static EmberBackState.Trial trial(CommandContext<CommandSourceStack> ctx) {
        EmberBackState.Trial t = EmberBackState.Trial.byId(StringArgumentType.getString(ctx, "trial"));
        if (t == null) {
            throw new IllegalArgumentException("unknown trial");
        }
        return t;
    }

    // 実行した人のグループ (パーティ、または一人)
    private static java.util.UUID group(CommandContext<CommandSourceStack> ctx) {
        net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            throw new IllegalArgumentException("run this as a player (the trials are per group)");
        }
        return jp.revati.skyabyss.titan.group.Groups.keyOf(p);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, java.util.function.Function<ServerLevel, String> action) {
        ServerLevel back = ctx.getSource().getServer().getLevel(EmberIds.BACK);
        if (back == null) {
            ctx.getSource().sendFailure(Component.literal("no ember back"));
            return 0;
        }
        try {
            String msg = action.apply(back);
            ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }
}
