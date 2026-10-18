package jp.revati.skyabyss.titan.ember;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.ember.smith.AshSmith;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: /skyabyss dev emberforce <対象> <技> [人数]
// 余燼の心臓 (BREATH / PULSE / ORB / FLOOR / GALE / PELLET / EMBERS / FADING / VEIL / ROAR) と灰の鍛え手 (SLAM / COLD_SLAM / BREATH / SPARKS) の技を一つ、
// 部屋の外 (平らな世界) でも出す。次の技は始めない (台本のスクショで予告の途中を撮るため)。人数は、部屋にいる人数を実際より多いことにする
// (一人で複数人の技を見る)。鍛え手は近くの人 (60 ブロック以内) を戦っている人にする。本番の jar には入らない (FMLEnvironment.production)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberForceDev {
    private EmberForceDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("emberforce")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"), 0))
                                        .then(Commands.argument("party", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> run(EntityArgument.getEntities(ctx, "targets"), StringArgumentType.getString(ctx, "action"),
                                                        IntegerArgumentType.getInteger(ctx, "party")))))))));
    }

    private static int run(java.util.Collection<? extends Entity> targets, String action, int party) {
        int n = 0;
        for (Entity e : targets) {
            String name = action.toUpperCase(Locale.ROOT);
            if (e instanceof EmberHeart heart) {
                heart.skipIntro();
                heart.holdAttacks(true);
                heart.setPartyOverride(party);
                ServerPlayer nearest = nearest(e);
                if (nearest != null) {
                    heart.setTarget(nearest);
                }
                heart.forceAction(EmberHeart.Action.valueOf(name));
                n++;
            } else if (e instanceof AshSmith smith) {
                List<ServerPlayer> near = e.level().getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(60));
                smith.armForDev(near, party);
                if (name.equals("COLD_SLAM")) {
                    smith.armCold();
                    name = "SLAM";
                }
                smith.forceAction(AshSmith.Action.valueOf(name));
                n++;
            }
        }
        return n;
    }

    private static ServerPlayer nearest(Entity e) {
        return e.level().getEntitiesOfClass(ServerPlayer.class, e.getBoundingBox().inflate(60)).stream()
                .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(e))).orElse(null);
    }
}
