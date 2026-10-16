package jp.revati.skyabyss.titan.travel;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
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

// 巨神での移動のテスト用コマンド (OP)。/skyabyss travel ...
//   unlock [人]    岩鯨に認めさせる (心臓を倒さなくても呼べる)。呼び笛はまだなら次の確認で渡る
//   lock [人]      認めたのを取り消す (心臓の撃破の記録があれば、認められたまま)
//   call           条件 (クールダウン・空・戦い) を見ずに岩鯨を呼ぶ
//   go <x> <z>     乗り場の上で、海図を使わずに行き先を座標で決める
//   cooldown       自分のクールダウンを消す
//   status         今の旅と、自分の記録を見る
//   end            今の旅を打ち切る (乗っている人は緩やかに降ろす)
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
                        .then(Commands.literal("call").executes(ctx -> call(ctx, TitanKind.STONE_WHALE))
                                .then(Commands.argument("titan", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(kindIds(), b))
                                        .executes(ctx -> call(ctx, kind(ctx)))))
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
                            for (TitanKind k : TitanKind.all()) {
                                Voyages.forceEnd(k);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("voyages ended"), true);
                            return 1;
                        }))));
    }

    private static List<String> kindIds() {
        List<String> ids = new java.util.ArrayList<>();
        TitanKind.all().forEach(k -> ids.add(k.id()));
        return ids;
    }

    private static TitanKind kind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TitanKind k = TitanKind.byId(StringArgumentType.getString(ctx, "titan"));
        if (k == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("unknown titan; try " + kindIds())).create();
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

    private static int call(CommandContext<CommandSourceStack> ctx, TitanKind kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Voyages.CallResult r = Voyages.call(player, kind, true);
        ctx.getSource().sendSuccess(() -> Component.literal("call: " + r), false);
        return r == Voyages.CallResult.REFUSED ? 0 : 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        Voyage v = Voyages.involving(player.getUUID());
        if (v == null || !v.riders.contains(player.getUUID()) || v.dest != null || v.phase != Voyage.Phase.WAITING) {
            ctx.getSource().sendFailure(Component.literal("sit on the titan's head (before choosing a destination) first"));
            return 0;
        }
        v.leader = player.getUUID();
        v.leaderName = player.getGameProfile().getName();
        BlockPos pos = new BlockPos(x, player.serverLevel().getSeaLevel(), z);
        TravelNetwork.Dest d = new TravelNetwork.Dest("test", x + ", " + z, "", x, pos.getY(), z, 2, true, 0);
        String refused = v.accept(d);
        if (refused != null) {
            ctx.getSource().sendFailure(Component.translatable(refused));
            return 0;
        }
        v.chosen(d);
        ctx.getSource().sendSuccess(() -> Component.literal("destination set to " + x + ", " + z), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TravelState s = Voyages.state(player);
        for (TitanKind k : TitanKind.all()) {
            String voyage = Voyages.describe(k);
            ctx.getSource().sendSuccess(() -> Component.literal(k.id() + " voyage: " + (voyage == null ? "none" : voyage)), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("unlocked=" + Voyages.isUnlocked(player, TitanKind.STONE_WHALE) + " " + s.unlocked
                + " callGiven=" + s.callGiven + " waymarks=" + s.waymarks.size() + " cooldown=" + TravelRules.cooldownLeft(player) / 20 + "s voyages=" + s.voyages), false);
        return 1;
    }
}
