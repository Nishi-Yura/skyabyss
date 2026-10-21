package jp.revati.skyabyss.story.ch4;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch4.mob.AshDragger;
import jp.revati.skyabyss.story.ch4.mob.ElderThrall;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 四章の地上の開発コマンド (撮影の台本 tools/shots/ch4_ground.txt。製品には入らない)
//   /skyabyss dev ch4 warm <x> <y> <z>   その位置の台を温める (温石を置いた扱い。熾り蟲が寄る)
//   /skyabyss dev ch4 crack              近く (24) の古い灰被りに、亀裂の技を今すぐ出させる (予告の円を撮る)
//   /skyabyss dev ch4 wave               近く (24) の古い灰被りの殻を割る (熱の波と燃え上がった姿)
//   /skyabyss dev ch4 curl               近く (24) の灰ひきずりを丸める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch4Dev {
    private Ch4Dev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("ch4")
                        .then(Commands.literal("warm").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> {
                                    BlockPos at = new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"));
                                    DeadForgePit.warm(c.getSource().getLevel(), at);
                                    c.getSource().sendSuccess(() -> Component.literal("warmed " + at.toShortString()), false);
                                    return 1;
                                })))))
                        .then(Commands.literal("crack").executes(c -> {
                            ElderThrall e = elder(c.getSource());
                            return e == null ? 0 : Math.max(1, e.startCracks());
                        }))
                        .then(Commands.literal("wave").executes(c -> {
                            ElderThrall e = elder(c.getSource());
                            if (e != null) {
                                e.ignite();
                            }
                            return e == null ? 0 : 1;
                        }))
                        .then(Commands.literal("curl").executes(c -> {
                            ServerLevel level = c.getSource().getLevel();
                            int n = 0;
                            for (AshDragger d : level.getEntitiesOfClass(AshDragger.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(24))) {
                                d.curl(true);
                                n++;
                            }
                            return n;
                        })))));
    }

    static ElderThrall elder(CommandSourceStack src) {
        for (ElderThrall e : src.getLevel().getEntitiesOfClass(ElderThrall.class, new AABB(BlockPos.containing(src.getPosition())).inflate(24))) {
            return e;
        }
        src.sendFailure(Component.literal("no elder thrall within 24"));
        return null;
    }
}
