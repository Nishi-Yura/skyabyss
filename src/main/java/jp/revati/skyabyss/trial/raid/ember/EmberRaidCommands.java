package jp.revati.skyabyss.trial.raid.ember;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.mid.MidRooms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影 (OP): 余燼のレイドの部屋を、順路を通さずに一つ建てて見る (建築の見比べ。tools/shots/raid_build_ember.txt)。
//   /skyemberraid room <ER1..ER6|ERM>        いまの次元の区画 1 の原点に部屋を建てる (動かさない。敵も出さない)
//   /skyemberraid at <dx> <dy> <dz> <yaw> <pitch>   部屋の床の中心からの位置へ運ぶ (北 = -z、yaw 180 = 北向き)
//   /skyemberraid end                         片付け
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberRaidCommands {
    private EmberRaidCommands() {
    }

    private static RaidCtx shotCtx;
    private static RaidRoom shotRoom;

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyemberraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("room").then(Commands.argument("id", StringArgumentType.word()).executes(EmberRaidCommands::room)))
                .then(Commands.literal("at").then(Commands.argument("dx", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                                .then(Commands.argument("pitch", FloatArgumentType.floatArg()).executes(EmberRaidCommands::at)))))))
                .then(Commands.literal("end").executes(c -> end())));
    }

    private static int end() {
        if (shotCtx != null && shotRoom != null && shotCtx.cell() != null) {
            shotRoom.end(shotCtx);
            shotCtx.clearMobs();
            RaidKit.clear(shotCtx.level(), shotCtx.cell().bounds());
        }
        shotCtx = null;
        shotRoom = null;
        return 1;
    }

    private static int room(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        boolean mid = MidRooms.isMid(id);
        RaidRoom room = mid ? MidRooms.create(Titans.EMBER) : RaidRooms.create(id);
        if (room == null) {
            ctx.getSource().sendFailure(Component.literal("unknown raid room " + id));
            return 0;
        }
        end();
        RaidCtx c = RaidCtx.forTest(p.serverLevel(), Groups.keyOf(p), Titans.EMBER, mid ? MidRooms.idOf(Titans.EMBER) : id, 1, mid ? 4 : 1, 12345L);
        room.build(c);
        if (c.cell() == null) {
            return 0;
        }
        shotCtx = c;
        shotRoom = room;
        p.teleportTo(c.cell().rest().getX() + 0.5, c.cell().rest().getY(), c.cell().rest().getZ() + 0.5);
        return 1;
    }

    private static int at(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (shotCtx == null) {
            return 0;
        }
        var o = shotCtx.origin();
        p.teleportTo(p.serverLevel(), o.getX() + 0.5 + DoubleArgumentType.getDouble(ctx, "dx"), o.getY() + 1 + DoubleArgumentType.getDouble(ctx, "dy"),
                o.getZ() + 0.5 + DoubleArgumentType.getDouble(ctx, "dz"), java.util.Set.of(), FloatArgumentType.getFloat(ctx, "yaw"),
                FloatArgumentType.getFloat(ctx, "pitch"));
        return 1;
    }
}
