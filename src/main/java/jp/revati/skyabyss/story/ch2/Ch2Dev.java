package jp.revati.skyabyss.story.ch2;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発コマンド (本番の jar には登録されない): 台本の撮影・確かめ用に、二章の地上の部品を平らな世界へ直接置く。
//   /skyabyss dev ch2 place <部品> <x> <y> <z> [rot]   y は地面の上面。部品の座標 (y=0 が上面の一つ上) をそのまま置く
//   /skyabyss dev ch2 vault <x> <y> <z>               塩の蔵を置き、碑を刻み、見回り (潮の喉・塩樽・戸) の対象に足す (向き 0)
//   /skyabyss dev ch2 tide wet|dry|auto               置いた蔵の潮の喉を、潮に関わらず沈める / 乾かす / 世界の潮に戻す
//   /skyabyss dev ch2 door                            置いた蔵の塩の戸を開ける
//   /skyabyss dev ch2 mark <x> <z>                    塩の道の標を一つ建てる (矢印は +x)
//   /skyabyss dev ch2 wreck <x> <y> <z>               難破船を置く (y は海の底の一つ上。記録には残さない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Ch2Dev {
    private Ch2Dev() {
    }

    private static int i(CommandContext<CommandSourceStack> c, String n) {
        return IntegerArgumentType.getInteger(c, n);
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("ch2")
                        .then(Commands.literal("place").then(Commands.argument("bp", StringArgumentType.word())
                                .then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "bp"), i(c, "x"), i(c, "y"), i(c, "z"), 0))
                                                .then(Commands.argument("rot", IntegerArgumentType.integer(0, 3))
                                                        .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "bp"), i(c, "x"), i(c, "y"), i(c, "z"),
                                                                i(c, "rot")))))))))
                        .then(Commands.literal("vault").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> vault(c.getSource(), i(c, "x"), i(c, "y"), i(c, "z")))))))
                        .then(Commands.literal("tide").then(Commands.argument("mode", StringArgumentType.word()).executes(c -> tide(c.getSource(), StringArgumentType.getString(c, "mode")))))
                        .then(Commands.literal("door").executes(c -> door(c.getSource())))
                        .then(Commands.literal("mark").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> mark(c.getSource(), i(c, "x"), i(c, "z"))))))
                        .then(Commands.literal("wreck").then(Commands.argument("x", IntegerArgumentType.integer()).then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> wreck(c.getSource(), i(c, "x"), i(c, "y"), i(c, "z"))))))))));
    }

    private static int place(CommandSourceStack src, String name, int x, int y, int z, int rot) {
        put(src.getLevel(), Blueprint.get(name), new BlockPos(x, y, z), rot);
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

    // 塩の蔵を置いて見回りの対象に足す (origin = 地面の上面)。蔵の見え方を返す
    public static SaltVault.Vault placeVault(ServerLevel level, BlockPos origin) {
        put(level, Blueprint.get(SaltVault.SITE), origin, 0);
        SaltVault.Vault v = new SaltVault.Vault(origin.above(), 0);
        SaltVault.EXTRA.removeIf(o -> o.pos().equals(v.pos()));
        SaltVault.EXTRA.add(v);
        SaltVault.decorate(level, v);
        return v;
    }

    private static int vault(CommandSourceStack src, int x, int y, int z) {
        SaltVault.Vault v = placeVault(src.getLevel(), new BlockPos(x, y, z));
        src.sendSuccess(() -> Component.literal("placed the salt vault: " + SaltVault.describe(src.getLevel(), v)), false);
        return 1;
    }

    private static int tide(CommandSourceStack src, String mode) {
        for (SaltVault.Vault v : SaltVault.EXTRA) {
            switch (mode) {
                case "wet" -> SaltVault.FORCE.put(v.pos().asLong(), true);
                case "dry" -> SaltVault.FORCE.put(v.pos().asLong(), false);
                default -> SaltVault.FORCE.remove(v.pos().asLong());
            }
            if (!mode.equals("auto")) {
                SaltVault.setSump(src.getLevel(), v, mode.equals("wet"));
            }
        }
        src.sendSuccess(() -> Component.literal("vault throat: " + mode), false);
        return 1;
    }

    private static int door(CommandSourceStack src) {
        for (SaltVault.Vault v : SaltVault.EXTRA) {
            SaltVault.openDoor(src.getLevel(), v);
        }
        src.sendSuccess(() -> Component.literal("vault door opened"), false);
        return 1;
    }

    private static int mark(CommandSourceStack src, int x, int z) {
        BlockPos at = SaltVault.placeMark(src.getLevel(), x, z, new BlockPos(x + 20, 0, z));
        src.sendSuccess(() -> Component.literal("salt mark at " + at), false);
        return 1;
    }

    private static int wreck(CommandSourceStack src, int x, int y, int z) {
        boolean ok = Wreck.place(src.getLevel(), new BlockPos(x, y, z), Rotation.NONE, null);
        src.sendSuccess(() -> Component.literal(ok ? "wreck placed" : "no wreck template"), false);
        return 1;
    }
}
