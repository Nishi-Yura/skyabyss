package jp.revati.skyabyss.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.GuardMob;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: /skyabyss dev anim <対象> <動作名> で、番兵の動作アニメーション (pounce など) を流す (台本のスクショで動きの途中を撮るため)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GuardAnimDev {
    private GuardAnimDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("anim")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("action", StringArgumentType.word()).executes(ctx -> {
                                    int n = 0;
                                    for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                                        if (e instanceof GuardMob mob) {
                                            mob.playAction(StringArgumentType.getString(ctx, "action"));
                                            n++;
                                        } else if (e instanceof software.bernie.geckolib.animatable.GeoEntity geo) {
                                            // 水路の顎など、GuardMob でない GeckoLib のモブ ("action" の制御に bite など)
                                            geo.triggerAnim("action", StringArgumentType.getString(ctx, "action"));
                                            n++;
                                        }
                                    }
                                    return n;
                                }))))));
    }
}
