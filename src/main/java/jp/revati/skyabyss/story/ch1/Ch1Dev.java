package jp.revati.skyabyss.story.ch1;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発コマンド (本番の jar には登録されない): 台本の撮影用に、一章の新しい場所の部品を平らな世界へ直接置く。
//   /skyabyss dev ch1 place <部品> <x> <y> <z> [rot]   y は地面の上面。部品の座標 (y=0 が上面の一つ上) をそのまま置く
//   /skyabyss dev ch1 chamber <x> <y> <z> [open]       宿りの部品と、その鐘の下の歌の間 (open で戸が梯子)。向きは 0
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch1Dev {
    private Ch1Dev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("ch1")
                        .then(Commands.literal("place").then(Commands.argument("bp", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "bp"), IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"), 0))
                                                .then(Commands.argument("rot", IntegerArgumentType.integer(0, 3))
                                                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "bp"), IntegerArgumentType.getInteger(c, "x"),
                                                                IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"),
                                                                IntegerArgumentType.getInteger(c, "rot")))))))))
                        .then(Commands.literal("chamber").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> chamber(c.getSource(), IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"),
                                                IntegerArgumentType.getInteger(c, "z"), false))
                                        .then(Commands.literal("open").executes(c -> chamber(c.getSource(), IntegerArgumentType.getInteger(c, "x"),
                                                IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"), true))))))))));
    }

    private static int place(CommandSourceStack src, String name, int x, int y, int z, int rot) {
        ServerLevel level = src.getLevel();
        Blueprint bp = Blueprint.get(name);
        put(level, bp, new BlockPos(x, y, z), rot);
        src.sendSuccess(() -> Component.literal("placed " + name + " at " + x + " " + y + " " + z), false);
        return 1;
    }

    static void put(ServerLevel level, Blueprint bp, BlockPos origin, int rot) {
        for (Blueprint.Cell c : bp.cells) {
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, false, origin);
            level.getChunk(at);
            level.setBlock(at, Blueprint.transform(c.state(), rot, false), 2 | 16);
        }
    }

    private static int chamber(CommandSourceStack src, int x, int y, int z, boolean open) {
        ServerLevel level = src.getLevel();
        BlockPos origin = new BlockPos(x, y, z);
        Blueprint shelter = Blueprint.get("shelter");
        put(level, shelter, origin, 0);
        int[] bell = shelter.marks.get("bell");
        Blueprint ch = Blueprint.get(Ch1Prep.SONG_CHAMBER_BLUEPRINT);
        for (Blueprint.Cell c : ch.cells) {
            BlockPos at = new BlockPos(origin.getX() + bell[0] + c.x(), origin.getY() + 1 + c.y(), origin.getZ() + bell[2] + c.z());
            level.getChunk(at);
            level.setBlock(at, c.state(), 2 | 16);
        }
        if (open) {
            int[] door = ch.marks.get("door");
            BlockPos at = new BlockPos(origin.getX() + bell[0] + door[0], origin.getY() + 1 + door[1], origin.getZ() + bell[2] + door[2]);
            level.setBlock(at, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.WEST), 2 | 16);
        }
        src.sendSuccess(() -> Component.literal("placed the lodge and the song chamber at " + x + " " + y + " " + z), false);
        return 1;
    }
}
