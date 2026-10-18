package jp.revati.skyabyss.cinematic;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.StoneWhaleEntity;
import jp.revati.skyabyss.titan.WhaleKeeper;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// OP のコマンド (確かめ・撮影の台本の cmd 用):
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
                                }).executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "scene");
                                    if (Scenes.get(id) == null) {
                                        ctx.getSource().sendFailure(Component.literal("unknown scene: " + id));
                                        return 0;
                                    }
                                    Cinematics.stop(p, true);
                                    return play(p, id) ? 1 : 0;
                                })))
                        .then(Commands.literal("stop").executes(ctx -> {
                            Cinematics.stop(ctx.getSource().getPlayerOrException(), true);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            Cinematics.state(ctx.getSource().getPlayerOrException()).forgetAll();
                            return 1;
                        }))));
    }

    private static boolean play(ServerPlayer p, String id) throws CommandSyntaxException {
        Entity target = null;
        Vec3 face = p.position().add(p.getLookAngle().multiply(120, 0, 120));
        if (id.equals(Scenes.WHALE_FIRST_SIGHT) || id.startsWith(Scenes.HEART_PREFIX)) {
            StoneWhaleEntity whale = WhaleKeeper.current(p.server.overworld());
            if (whale != null) {
                target = whale;
                face = whale.position();
            }
        }
        return Cinematics.start(p, id, target, face);
    }
}
