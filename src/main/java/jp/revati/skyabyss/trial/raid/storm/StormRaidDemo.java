package jp.revati.skyabyss.trial.raid.storm;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 開発・撮影 (OP): 雷翼のレイドの新しい三つの部屋 (SR4・SR5・SR6) を、順路なしで建てて動かす。/skystormraid show SR4|SR5|SR6|off
// 部屋は自己テストと同じ場所 (x = -4400 / -4200 / -4000、y 150、z 5000)。人は参加者にならないので、SR4 の柱は仮の目標 (広間を回る点) を追う
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormRaidDemo {
    private StormRaidDemo() {
    }

    private static RaidCtx ctx;
    private static RaidRoom room;
    private static ServerLevel level;
    private static int ticks;

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skystormraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("off").executes(c -> {
                    stop();
                    return 1;
                }))
                .then(Commands.literal("show").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    show(c.getSource().getLevel(), StringArgumentType.getString(c, "id"));
                    return 1;
                }))));
    }

    private static void stop() {
        if (room != null && ctx != null) {
            room.end(ctx);
            ctx.clearMobs();
        }
        room = null;
        ctx = null;
    }

    private static void show(ServerLevel lv, String id) {
        stop();
        level = lv;
        int x = switch (id) {
            case "SR4" -> -4400;
            case "SR5" -> -4200;
            default -> -4000;
        };
        for (int cx = (x - 24) >> 4; cx <= (x + 24) >> 4; cx++) {
            for (int cz = (5000 - 40) >> 4; cz <= (5000 + 40) >> 4; cz++) {
                lv.setChunkForced(cx, cz, true);
            }
        }
        try {
            Constructor<?> k = RaidCtx.class.getDeclaredConstructors()[0];
            k.setAccessible(true);
            ctx = (RaidCtx) k.newInstance(lv, UUID.randomUUID(), Titans.THUNDER_WING, id, 1, 1, new BlockPos(x, 150, 5000), 20261023L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        room = switch (id) {
            case "SR4" -> new ColumnChase();
            case "SR5" -> new BeatTiles();
            default -> new EyeAdvance();
        };
        room.build(ctx);
        room.start(ctx);
        ticks = 0;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (room == null || ctx == null) {
            return;
        }
        ticks++;
        if (room instanceof ColumnChase c) {
            double a = ticks * 0.012;
            c.update(ctx, List.of(new double[] {ctx.origin().getX() + 0.5 + Math.cos(a) * 9, ctx.origin().getZ() + 0.5 + Math.sin(a) * 6}));
        } else if (room instanceof BeatTiles b) {
            b.advance(ctx, new boolean[BeatTiles.N * BeatTiles.N]);
        } else {
            room.tick(ctx);
        }
    }
}
