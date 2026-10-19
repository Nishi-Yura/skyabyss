package jp.revati.skyabyss.titan.travel;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.young.YoungCarriers;
import jp.revati.skyabyss.titan.young.YoungEntity;
import jp.revati.skyabyss.titan.young.YoungKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 巨神の子のテスト用コマンド (OP)。/skyabyss travel ...
//   unlock [人]    岩鯨・潮殻に認めさせる (心臓を倒さなくても。呼び笛はまだなら次の確認で渡る)
//   lock [人]      認めたのを取り消す (心臓の撃破の記録があれば、認められたまま)
//   met [人]       四体すべてに「出会った」ことにする
//   young [巨神]   条件 (出会い・眠り・戦い・待ち) を見ずに、足もとの前へ子を呼ぶ
//   go <x> <z>     子に乗っている持ち主が、海図を使わずに行き先を座標で決める
//   cooldown       自分の笛の待ちを消す
//   status         今の子と、自分の記録を見る
//   end            今の子を全部歌に戻す (乗っている人は地面へ降ろす)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TravelCommands {
    private TravelCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("travel")
                        .then(Commands.literal("unlock")
                                .executes(ctx -> unlock(ctx, List.of(ctx.getSource().getPlayerOrException()), true))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> unlock(ctx, EntityArgument.getPlayers(ctx, "targets"), true))))
                        .then(Commands.literal("lock")
                                .executes(ctx -> unlock(ctx, List.of(ctx.getSource().getPlayerOrException()), false))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> unlock(ctx, EntityArgument.getPlayers(ctx, "targets"), false))))
                        .then(Commands.literal("met")
                                .executes(ctx -> met(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> met(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("young")
                                .executes(ctx -> young(ctx, YoungKind.WHALE))
                                .then(Commands.argument("titan", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Titans.all(), b))
                                        .executes(ctx -> young(ctx, kind(ctx)))))
                        .then(Commands.literal("go")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(TravelCommands::go))))
                        .then(Commands.literal("cooldown").executes(ctx -> {
                            Voyages.state(ctx.getSource().getPlayerOrException()).readyAt = 0;
                            ctx.getSource().sendSuccess(() -> Component.literal("cooldown cleared"), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(TravelCommands::status))
                        .then(Commands.literal("end").executes(ctx -> {
                            int n = YoungCarriers.endAll();
                            ctx.getSource().sendSuccess(() -> Component.literal(n + " young one(s) ended"), true);
                            return 1;
                        }))));
    }

    private static YoungKind kind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        YoungKind k = YoungKind.ofTitan(StringArgumentType.getString(ctx, "titan"));
        if (k == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("unknown titan; try " + Titans.all())).create();
        }
        return k;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, boolean unlock) {
        for (ServerPlayer p : players) {
            TravelState s = Voyages.state(p);
            for (TitanKind k : TitanKind.all()) {
                if (unlock) {
                    s.unlocked.add(k.id());
                } else {
                    s.unlocked.remove(k.id());
                    s.callGiven.remove(k.id());
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal((unlock ? "unlocked" : "locked") + " every travel titan for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int met(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            for (String id : Titans.all()) {
                if (TitanCalls.known(id)) {
                    TitanCalls.markMet(p, id);
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("met every titan for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int young(CommandContext<CommandSourceStack> ctx, YoungKind kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        YoungEntity existing = YoungCarriers.of(player.getUUID());
        if (existing != null) {
            existing.discard();
        }
        net.minecraft.world.phys.Vec3 spot = YoungCarriers.spotFor(player, kind);
        YoungEntity e = spot == null ? null : YoungCarriers.spawn(player.serverLevel(), kind, player, spot);
        ctx.getSource().sendSuccess(() -> Component.literal(e == null ? "no room" : kind.model + " at " + BlockPos.containing(spot)), false);
        return e == null ? 0 : 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        BlockPos pos = new BlockPos(x, player.serverLevel().getSeaLevel(), z);
        TravelNetwork.Dest d = new TravelNetwork.Dest("test", x + ", " + z, "", x, pos.getY(), z, 1, true, 0, "");
        String refused = YoungCarriers.chooseDirect(player, d);
        if (refused != null) {
            ctx.getSource().sendFailure(Component.translatable(refused));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("destination set to " + x + ", " + z), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TravelState s = Voyages.state(player);
        for (YoungEntity e : YoungCarriers.all()) {
            String line = e.kind.model + " owner=" + e.owner() + " phase=" + (e.trip() == null ? "?" : e.trip().phase()) + " riders=" + e.riderCount()
                    + " at " + BlockPos.containing(e.position());
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("unlocked=" + s.unlocked + " met=" + s.met + " callGiven=" + s.callGiven + " waymarks="
                + s.waymarks.size() + " cooldown=" + Voyages.cooldownLeft(player) / 20 + "s voyages=" + s.voyages), false);
        return 1;
    }
}
