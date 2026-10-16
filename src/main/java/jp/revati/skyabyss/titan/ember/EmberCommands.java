package jp.revati.skyabyss.titan.ember;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 余燼のテスト用コマンド (OP)。13 の 12章。**骨組み (担当 0) の版**: 背中へ渡る・場所へ移る・熱を決める・様子だけ。
// /skyabyss titan board ember        : 背中へ直接渡る (乗る資格は見る。/skyabyss progress grant で足す)
// /skyabyss emberback go <場所>       : 部屋の箱の中心・休み場へ移る (hub・west・east・smith・south・heart_ante・heart・mouth、休み場の名前)
// /skyabyss emberback heat <0-100>   : 自分の熱を決める
// /skyabyss emberback status         : 自分の熱と帯
// summon・rest・rebuild・reset・sleep は A・B が足す (親の節には executes を付けない。岩鯨・潮殻の同じ節を上書きしないため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberCommands {
    private EmberCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("board").then(op(EmberIds.TITAN).executes(ctx -> EmberTravel.board(ctx.getSource().getPlayerOrException()) ? 1 : 0)))));
        dispatcher.register(op(SkyAbyss.MODID).then(op("emberback")
                .then(op("go").then(Commands.argument("place", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(places(), b))
                        .executes(EmberCommands::go)))
                .then(op("heat").then(Commands.argument("value", FloatArgumentType.floatArg(0, EmberHeat.MAX)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    EmberHeat.set(player, FloatArgumentType.getFloat(ctx, "value"));
                    return status(ctx);
                })))
                .then(op("status").executes(EmberCommands::status))));
    }

    private static java.util.List<String> places() {
        java.util.List<String> names = new java.util.ArrayList<>(EmberBackLayout.ROOMS.keySet());
        for (EmberBackLayout.Rest r : EmberBackLayout.RESTS) {
            names.add("rest_" + r.name());
        }
        return names;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        String place = StringArgumentType.getString(ctx, "place");
        BlockPos at;
        float yaw = 0f;
        if (place.startsWith("rest_")) {
            EmberBackLayout.Rest r;
            try {
                r = EmberBackLayout.rest(place.substring(5));
            } catch (IllegalArgumentException e) {
                ctx.getSource().sendFailure(Component.literal("unknown place " + place));
                return 0;
            }
            at = r.floor().above();
            yaw = r.yaw();
        } else {
            EmberBackLayout.Box box = EmberBackLayout.ROOMS.get(place);
            if (box == null) {
                ctx.getSource().sendFailure(Component.literal("unknown place " + place));
                return 0;
            }
            at = new BlockPos(box.center().getX(), box.minY() + 1, box.center().getZ());
        }
        if (back == null) {
            return 0;
        }
        player.teleportTo(back, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        BlockPos fat = at;
        ctx.getSource().sendSuccess(() -> Component.literal("ember back: " + place + " " + fat.toShortString()), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        float heat = EmberHeat.get(player);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("ember heat %.1f (%s), on the back: %s", heat, EmberHeat.band(player),
                EmberHeat.affects(player))), false);
        return 1;
    }
}
