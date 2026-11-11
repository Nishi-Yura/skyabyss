package jp.revati.skyabyss.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.prop.AttackProps;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用: 技の形 (combat/prop) を直に出す (台本のスクショで、突き出る・落ちる・飛ぶ姿を撮るため)。
//   /skyabyss dev prop <種類> rise <場所> [留まる tick]
//   /skyabyss dev prop <種類> fall <場所> <予告 tick>
//   /skyabyss dev prop <種類> toss <から> <へ> <予告 tick>
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AttackPropDev {
    private AttackPropDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("prop").then(Commands.argument("kind", StringArgumentType.word())
                        .then(Commands.literal("rise").then(Commands.argument("at", Vec3Argument.vec3()).executes(ctx -> {
                            ServerLevel lv = ctx.getSource().getLevel();
                            Vec3 at = Vec3Argument.getVec3(ctx, "at");
                            AttackProps.rise(lv, StringArgumentType.getString(ctx, "kind"), null, at, 0f, 1f, 200);
                            return 1;
                        })))
                        .then(Commands.literal("fall").then(Commands.argument("at", Vec3Argument.vec3()).then(Commands.argument("warn", IntegerArgumentType.integer(2, 400))
                                .executes(ctx -> {
                                    AttackProps.fall(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "kind"), null, Vec3Argument.getVec3(ctx, "at"),
                                            IntegerArgumentType.getInteger(ctx, "warn"), 14, 14f, 1.4f, 200);
                                    return 1;
                                }))))
                        .then(Commands.literal("toss").then(Commands.argument("from", Vec3Argument.vec3()).then(Commands.argument("to", Vec3Argument.vec3())
                                .then(Commands.argument("warn", IntegerArgumentType.integer(2, 400)).executes(ctx -> {
                                    AttackProps.toss(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "kind"), null, Vec3Argument.getVec3(ctx, "from"),
                                            Vec3Argument.getVec3(ctx, "to"), IntegerArgumentType.getInteger(ctx, "warn"), 40, 4f, 1.4f, 200);
                                    return 1;
                                })))))))));
    }
}
