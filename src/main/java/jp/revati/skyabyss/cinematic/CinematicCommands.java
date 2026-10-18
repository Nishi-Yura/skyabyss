package jp.revati.skyabyss.cinematic;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// OP のコマンド (確かめ・撮影の台本の cmd 用):
//   どれも末尾に [targets] (セレクタ。@a や名前) を足せば、その人たちに。無ければ自分
//   /skyabyss cinematic play <場面>   : 自分に場面を始める (見たことがあっても、場面の中でも。狙う物は岩鯨か、いなければ前方 120 ブロック)
//   /skyabyss cinematic stop          : 自分の場面を打ち切る
//   /skyabyss cinematic reset         : 自分の「見た場面」の記録を消す (次の条件で初見がもう一度出る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CinematicCommands {
    private CinematicCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("cinematic")
                        .then(Commands.literal("play").then(Commands.argument("scene", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    Scenes.all().forEach(s -> b.suggest(s.id()));
                                    return b.buildFuture();
                                })
                                .executes(ctx -> play(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> play(ctx, EntityArgument.getPlayers(ctx, "targets"))))))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> stop(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> reset(ctx, EntityArgument.getPlayers(ctx, "targets")))))));
    }

    private static int play(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        String id = StringArgumentType.getString(ctx, "scene");
        if (Scenes.get(id) == null) {
            ctx.getSource().sendFailure(Component.literal("unknown scene: " + id));
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : targets) {
            Cinematics.stop(p, true);
            if (play(p, id)) {
                n++;
            }
        }
        int started = n;
        ctx.getSource().sendSuccess(() -> Component.literal("cinematic " + id + ": started for " + started + " of " + targets.size() + " player(s)"), true);
        return n;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        targets.forEach(p -> Cinematics.stop(p, true));
        ctx.getSource().sendSuccess(() -> Component.literal("cinematic: stopped for " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        targets.forEach(p -> Cinematics.state(p).forgetAll());
        ctx.getSource().sendSuccess(() -> Component.literal("cinematic: forgot the seen scenes of " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static boolean play(ServerPlayer p, String id) {
        Entity target = null;
        Vec3 face = p.position().add(p.getLookAngle().multiply(120, 0, 120));
        if (id.equals(Scenes.WHALE_FIRST_SIGHT)) {
            StoneWhaleEntity whale = WhaleKeeper.current(p.server.overworld());
            if (whale != null) {
                target = whale;
                face = whale.position();
            }
        } else if (id.startsWith(Scenes.FIRST_PREFIX)) {
            // 潮殻・雷翼・余燼の初見: その巨神を狙う (いなければ前方)
            Entity e = CinematicTriggers.currentTitan(p.server.overworld(), id.substring(Scenes.FIRST_PREFIX.length()));
            if (e != null) {
                target = e;
                face = e.position();
            }
        } else if (id.startsWith(Scenes.HEART_PREFIX) || id.startsWith(Scenes.END_PREFIX)) {
            // 心臓・眠りの場面: その巨神の本体を狙う (いなければ岩鯨、それもなければ前方)
            String titan = id.substring(id.startsWith(Scenes.HEART_PREFIX) ? Scenes.HEART_PREFIX.length() : Scenes.END_PREFIX.length());
            Entity e = jp.revati.skyabyss.titan.end.TitanEnd.entity(p.server, titan);
            if (e == null) {
                e = WhaleKeeper.current(p.server.overworld());
            }
            if (e != null) {
                target = e;
                face = e.position();
            }
        }
        return Cinematics.start(p, id, target, face);
    }
}
