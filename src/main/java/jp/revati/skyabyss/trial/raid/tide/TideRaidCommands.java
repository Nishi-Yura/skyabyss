package jp.revati.skyabyss.trial.raid.tide;

import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発・撮影 (OP): /skytideraid room <id> = 順路を通さずに部屋を建てて動かす (pose = 途中の姿、end = 片付け)。潮殻のレイドの部屋の途中の姿を作る。/skytideraid shells = 貝の一組を揃え、もう一つ開けて待たせる。/skytideraid hurt = 苗を少しかじられた姿にする
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideRaidCommands {
    private TideRaidCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skytideraid").requires(s -> s.hasPermission(2))
                .then(Commands.literal("shells").executes(TideRaidCommands::shells))
                .then(Commands.literal("hurt").executes(TideRaidCommands::hurt))
                .then(Commands.literal("room").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(TideRaidCommands::room)))
                .then(Commands.literal("pose").executes(TideRaidCommands::pose))
                .then(Commands.literal("end").executes(c -> endShot())));
    }

    // ---- 撮影: 順路を通さずに部屋を一つ建てて動かす (潮殻の背の中で。区画 1・段 1 の原点)
    private static RaidCtx shotCtx;
    private static RaidRoom shotRoom;

    private static int endShot() {
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
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
        RaidRoom room = RaidRooms.create(id);
        if (room == null) {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("unknown raid room " + id));
            return 0;
        }
        endShot();
        RaidCtx c = RaidCtx.forTest(p.serverLevel(), Groups.keyOf(p), Titans.TIDE_SHELL, id, 1, 1, 12345L);
        room.build(c);
        RaidKit.setGate(c, c.cell().entryGate(), true);
        shotCtx = c;
        shotRoom = room;
        p.teleportTo(c.cell().rest().getX() + 0.5, c.cell().rest().getY(), c.cell().rest().getZ() + 0.5);
        room.start(c);
        return 1;
    }

    // 部屋の途中の姿: TR5 = 光を水晶へ導いた姿、TR6 = 台の半分を合わせた姿、TR7 = 母の近くへ寄る
    private static int pose(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (shotCtx == null) {
            return 0;
        }
        if (shotRoom instanceof TideLightRoom r) {
            for (int[] n : r.pathNodes()) {
                for (int k = 0; k < 4 && r.facing(n[0], n[1]) != r.correctFacing(n[0], n[1]); k++) {
                    r.onUse(shotCtx, p, r.mirrorPos(n[0], n[1]), false);
                }
            }
        } else if (shotRoom instanceof TideMoonRoom r) {
            for (int d = 0; d < 3; d++) {
                for (int k = 0; k < TideMoonRoom.PHASES && r.phaseOf(d) != r.correctOf(d); k++) {
                    r.onUse(shotCtx, p, r.dialPos(d), false);
                }
            }
        } else if (shotRoom instanceof TideBroodRoom r && r.mother() != null) {
            net.minecraft.world.entity.Mob m = r.mother();
            p.teleportTo(m.getX(), m.getY(), m.getZ() + 7);
        }
        return 1;
    }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (shotCtx != null && shotRoom != null && shotCtx.cell() != null) {
            shotRoom.tick(shotCtx);
        }
    }

    private static int shells(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RaidRun.Run run = RaidRun.of(Groups.keyOf(p));
        if (run == null || !(run.room instanceof TideShellRoom r)) {
            return 0;
        }
        int j = -1;
        for (int k = 1; k < r.shells(); k++) {
            if (r.toneOf(k) == r.toneOf(0)) {
                j = k;
            }
        }
        r.open(run.ctx, 0);
        r.open(run.ctx, j);
        // もう一組の片方だけ開けて、見せておく
        for (int k = 1; k < r.shells(); k++) {
            if (!r.isOpen(k)) {
                r.open(run.ctx, k);
                break;
            }
        }
        return 1;
    }

    private static int hurt(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        RaidRun.Run run = RaidRun.of(Groups.keyOf(p));
        if (run == null || !(run.room instanceof TideSaplingRoom r)) {
            return 0;
        }
        r.damage(run.ctx, 0, 12);
        r.damage(run.ctx, 2, 24);
        return 1;
    }
}
