package jp.revati.skyabyss.npc;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// NPC のテスト用コマンド (OP)。
//   /skyabyss npc spawn <storyteller|whale_chaser>   : 足もとに立たせる (家はその場所)
//   /skyabyss npc village                             : 近くの村 (鐘) に、いなければ語り部を、要るなら鯨追いを立たせる
//   /skyabyss npc affinity <人> <役> [値]             : 好感度を見る・決める
//   /skyabyss npc bonds <人>                          : 好感度と会話の印 (このループ / 前のループ) の一覧
//   /skyabyss npc reset <人>                          : 会話の記録をすべて消す
//   /skyabyss npc loop [set <n> | advance]            : 月のループの回数を見る・決める・一つ進める (進めると会話の印が「記憶」へ移る)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NpcCommands {
    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, b) -> SharedSuggestionProvider.suggest(
            new String[] {"storyteller", "whale_chaser"}, b);

    private NpcCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn").then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .executes(NpcCommands::spawn)))
                        .then(Commands.literal("village").executes(NpcCommands::village))
                        .then(Commands.literal("affinity").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                        .executes(ctx -> affinity(ctx, null))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(Affinity.MIN, Affinity.MAX))
                                                .executes(ctx -> affinity(ctx, IntegerArgumentType.getInteger(ctx, "value")))))))
                        .then(Commands.literal("bonds").then(Commands.argument("player", EntityArgument.player()).executes(NpcCommands::bonds)))
                        .then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(NpcCommands::reset)))
                        .then(Commands.literal("loop")
                                .executes(ctx -> loopShow(ctx))
                                .then(Commands.literal("set").then(Commands.argument("n", IntegerArgumentType.integer(0, 999)).executes(ctx -> {
                                    WorldLoop.set(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "n"));
                                    return loopShow(ctx);
                                })))
                                .then(Commands.literal("advance").executes(ctx -> {
                                    WorldLoop.advance(ctx.getSource().getServer());
                                    return loopShow(ctx);
                                })))));
    }

    private static String roleId(String arg) {
        return arg.contains(":") ? arg : SkyAbyss.MODID + ":" + arg;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) {
        String role = roleId(StringArgumentType.getString(ctx, "role"));
        EntityType<NpcEntity> type = role.equals(NpcSetup.STORYTELLER_ID) ? NpcSetup.STORYTELLER.get()
                : role.equals(NpcSetup.WHALE_CHASER_ID) ? NpcSetup.WHALE_CHASER.get() : null;
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("unknown role " + role));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        NpcEntity npc = type.create(level);
        if (npc == null) {
            return 0;
        }
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getSource().getRotation().y + 180f, 0f);
        npc.setHome(at);
        npc.nameByRole();
        level.addFreshEntity(npc);
        ctx.getSource().sendSuccess(() -> Component.literal("spawned " + role + " at " + at.toShortString()), true);
        return 1;
    }

    private static int village(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        var bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), 96, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no village bell within 96 blocks"));
            return 0;
        }
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        data.villages.remove(bell.get().asLong());
        NpcSpawner.visitVillage(level, bell.get(), player);
        ctx.getSource().sendSuccess(() -> Component.literal("village at " + bell.get().toShortString() + " checked (chaser needed: "
                + NpcSpawner.needsChaser(player) + ")"), true);
        return 1;
    }

    private static int affinity(CommandContext<CommandSourceStack> ctx, Integer value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String role = roleId(StringArgumentType.getString(ctx, "role"));
        if (value != null) {
            Affinity.set(player, role, value);
        }
        int a = Affinity.get(player, role);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " / " + role + ": affinity " + a + " (")
                .append(Component.translatable("gui.skyabyss.npc.tier." + Affinity.tier(a))).append(")"), value != null);
        return a;
    }

    private static int bonds(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        NpcBonds bonds = NpcBonds.of(player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " (loop seen " + bonds.loopSeen() + ")"), false);
        bonds.bonds().forEach((role, b) -> ctx.getSource().sendSuccess(() -> Component.literal(" " + role + ": affinity " + b.affinity()
                + ", talks " + b.talks() + ", flags " + b.flags() + ", remembered " + b.echoes()), false));
        return bonds.bonds().size();
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer p : players) {
            NpcBonds b = NpcBonds.of(p);
            b.bonds.clear();
            b.loopSeen = WorldLoop.count(p.server);
            NpcBonds.sync(p);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("reset npc bonds of " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int loopShow(CommandContext<CommandSourceStack> ctx) {
        int n = WorldLoop.count(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("loop " + n), false);
        return n;
    }
}
