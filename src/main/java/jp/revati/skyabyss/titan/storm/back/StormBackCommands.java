package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 雷翼の背のテスト用コマンド (OP)。12 の 12章:
// グループ: reset・sleep は打った人のグループ (コンソールなら記録のあるすべてのグループ)、ring・raiju・mirrors・status は打った人のグループ
// /skyabyss titan reset storm                        : 試練だけ戻す
// /skyabyss titan storm <calm|storm|cycle>           : 拍を凪・嵐に止める / 拍に戻す
// /skyabyss titan sleep storm <west|east|front|none> : 眠る試練を決めて、試練を戻す
// /skyabyss stormback go <場所> | ring <試練> | mirrors <配置> | raiju | status
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackCommands {
    static final Map<String, BlockPos> PLACES = new LinkedHashMap<>();

    static {
        PLACES.put("arrival", ARRIVAL);
        PLACES.put("plaza", rest("tail"));
        PLACES.put("gate", new BlockPos(0, GATE_FLOOR + 1, GATE_Z2 + 2));
        PLACES.put("tail", TAIL_CENTER);
        PLACES.put("hub", rest("hub"));
        PLACES.put("gallery", new BlockPos(0, UPPER_GALLERY_Y + 1, GALLERY.z1() + 3));
        PLACES.put("w0", rest("w0"));
        PLACES.put("wm", rest("wm"));
        PLACES.put("w1", rest("w1"));
        PLACES.put("rib", new BlockPos(RIB_X2 - 1, BONE_FLOOR + 1, WZ));
        PLACES.put("rib1", rest("w2"));
        PLACES.put("rib2", rest("w3"));
        PLACES.put("ledge", WEST_STONE.offset(1, 0, 1));
        PLACES.put("pad1", new BlockPos(76, BONE_FLOOR + 1, WZ - 3));
        PLACES.put("pad2", new BlockPos(103, BONE_FLOOR + 1, WZ - 4));
        PLACES.put("pad3", new BlockPos(128, BONE_FLOOR + 1, WZ - 4));
        PLACES.put("pad4", new BlockPos(155, BONE_FLOOR + 1, WZ - 4));
        PLACES.put("helm_w", StormHelm.HELMS.get(0).calm().offset(2, 0, 2));
        PLACES.put("helm_e", StormHelm.HELMS.get(1).calm().offset(2, 0, 2));
        PLACES.put("helm_f", StormHelm.HELMS.get(2).calm().offset(0, 0, 2));
        PLACES.put("e0", rest("e0"));
        PLACES.put("e1", rest("e1"));
        PLACES.put("e2", rest("e2"));
        PLACES.put("e3", rest("e3"));
        PLACES.put("nest", RAIJU_NEST.offset(-8, 1, 0));
        PLACES.put("perch", EAST_STONE.offset(-1, 0, 1));
        PLACES.put("f0", rest("f0"));
        PLACES.put("eye", EYE_HALL_CENTER.offset(0, 0, 6));
        PLACES.put("beak", FRONT_STONE.offset(1, 0, 0));
        PLACES.put("heart", rest("heart"));
        PLACES.put("keel", HEART_ROOM.offset(0, 2, 8));
        // 試練の甲板 (29 の P2)
        PLACES.put("yard", rest("yard"));
        PLACES.put("s02", rest("s02a"));
        PLACES.put("s02m", rest("s02m"));
        PLACES.put("s02stone", StormDeck.S02_STONE.east(2));
        PLACES.put("s03", rest("s03a"));
        PLACES.put("s03m", rest("s03m"));
        PLACES.put("s03stone", StormDeck.S03_STONE.west(2));
        PLACES.put("s09", rest("s09a"));
        PLACES.put("s09arena", new BlockPos(StormDeck.S09_ARENA.cx() - 5, StormDeck.Y + 1, StormDeck.S09_ARENA.cz()));
        PLACES.put("s09stone", StormDeck.S09_STONE.west(2));
        PLACES.put("s10", rest("s10a"));
        PLACES.put("s10m", rest("s10m"));
        PLACES.put("s10stone", StormDeck.S10_STONE.east(2));
        // 第二の渡り場 (29 の P3)
        PLACES.put("yard2", rest("yard2"));
        PLACES.put("s04", rest("s04a"));
        PLACES.put("s04m", rest("s04m"));
        PLACES.put("s04stone", StormDeck2.S04_STONE.east(2));
        PLACES.put("s07", rest("s07a"));
        PLACES.put("s07stone", StormDeck2.S07_STONE.west(2));
        PLACES.put("s08", rest("s08a"));
        PLACES.put("s08stone", StormDeck2.S08_STONE.west(2));
        PLACES.put("s11", rest("s11a"));
        PLACES.put("s11stone", StormDeck2.S11_STONE.west(2));
        PLACES.put("s12", rest("s12a"));
        PLACES.put("s12arena", new BlockPos(StormDeck2.S12_ARENA.cx() - 2, StormDeck2.Y + 1, StormDeck2.S12_ARENA.cz() - 8));
        PLACES.put("s12stone", StormDeck2.S12_STONE.north(2));
    }

    private StormBackCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> beat = op("storm");
        for (String p : new String[] {"calm", "storm", "cycle"}) {
            beat.then(op(p).executes(ctx -> beat(ctx, p)));
        }
        LiteralArgumentBuilder<CommandSourceStack> sleep = op("storm");
        for (String t : new String[] {"west", "east", "front", "none"}) {
            sleep.then(op(t).executes(ctx -> sleep(ctx, t)));
        }
        dispatcher.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("reset").then(op("storm").executes(StormBackCommands::reset)))
                .then(beat)
                .then(op("sleep").then(sleep))));

        LiteralArgumentBuilder<CommandSourceStack> go = op("go");
        for (String place : PLACES.keySet()) {
            go.then(op(place).executes(ctx -> go(ctx, place)));
        }
        LiteralArgumentBuilder<CommandSourceStack> ring = op("ring");
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            ring.then(op(t.id()).executes(ctx -> ring(ctx, t)));
        }
        dispatcher.register(op(SkyAbyss.MODID).then(op("stormback").then(go).then(ring)
                .then(op("status").executes(StormBackCommands::status))
                .then(op("raiju").executes(StormBackCommands::raiju))
                .then(op("circuits").executes(StormBackCommands::circuits))
                .then(helm())
                .then(op("mirrors").then(Commands.argument("config", IntegerArgumentType.integer(0, EyeMirrors.CONFIGS.size() - 1))
                        .executes(StormBackCommands::mirrors)))));
    }

    // /skyabyss stormback helm <west|east|front> <calm|storm>: 舵を回したのと同じ (60 秒の固定。もう一度で放す)
    private static LiteralArgumentBuilder<CommandSourceStack> helm() {
        LiteralArgumentBuilder<CommandSourceStack> top = op("helm");
        for (StormBackState.Trial t : StormBackState.Trial.values()) {
            LiteralArgumentBuilder<CommandSourceStack> n = op(t.id());
            for (String ph : new String[] {"calm", "storm"}) {
                n.then(op(ph).executes(ctx -> {
                    ServerLevel back = back(ctx);
                    UUID g = group(ctx);
                    if (back == null || g == null) {
                        return 0;
                    }
                    StormHelm.set(back, g, t, ph.equals("storm") ? StormBackClock.Phase.STORM : StormBackClock.Phase.CALM,
                            ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null);
                    return 1;
                }));
            }
            top.then(n);
        }
        return top;
    }

    // /skyabyss stormback circuits: 三つの回路をつないだ状態にする (打った人のグループ。開発用)
    private static int circuits(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        StormProgress p = StormProgress.of(back, ctx.getSource().getPlayerOrException());
        for (int i = 0; i < EAST_DOORS.length; i++) {
            p.openEastDoor(i);
        }
        StormBackTrials.syncGates(back, p.group);
        if (p.group.equals(RoomLocks.owner(StormBackTrials.EAST_ROOM))) {
            StormEast.apply(back, p);
        }
        return 1;
    }

    private static ServerLevel back(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = ctx.getSource().getServer().getLevel(StormTravel.STORM_BACK);
        if (back != null) {
            StormBackBuilder.ensureBuilt(back);
        }
        return back;
    }

    private static int beat(CommandContext<CommandSourceStack> ctx, String p) {
        StormBackClock.force(switch (p) {
            case "calm" -> StormBackClock.Phase.CALM;
            case "storm" -> StormBackClock.Phase.STORM;
            default -> null;
        });
        ServerLevel back = back(ctx);
        if (back != null) {
            StormBackTrials.sendGaugeNow(back);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("storm beat: " + p), true);
        return 1;
    }

    // コマンドを打った人のグループ (コンソールなら null = 記録のあるすべてのグループ)
    @org.jetbrains.annotations.Nullable
    private static UUID group(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? Groups.keyOf(p) : null;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        UUID g = group(ctx);
        if (g == null) {
            StormBackTrials.resetAll(back, true, null, false);
        } else {
            StormBackTrials.resetGroup(back, g, true, null, false, true);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(g == null ? "storm back trials reset (all groups)" : "storm back trials reset (your group)"), true);
        return 1;
    }

    private static int sleep(CommandContext<CommandSourceStack> ctx, String t) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        StormBackState.Trial trial = StormBackState.Trial.byId(t);
        UUID g = group(ctx);
        if (g == null) {
            StormBackTrials.resetAll(back, true, trial, trial == null);
        } else {
            StormBackTrials.resetGroup(back, g, true, trial, trial == null, true);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("storm back sleeping: " + t), true);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx, String place) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        BlockPos to = PLACES.get(place);
        p.fallDistance = 0;
        p.teleportTo(back, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int ring(CommandContext<CommandSourceStack> ctx, StormBackState.Trial t) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        StormBackTrials.ring(back, StormProgress.of(back, p), t, null);
        return 1;
    }

    private static int raiju(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        StormBackTrials.onRaijuDefeated(back, Groups.keyOf(p));
        ctx.getSource().sendSuccess(() -> Component.literal("raiju marked defeated (your group)"), true);
        return 1;
    }

    private static int mirrors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        int c = IntegerArgumentType.getInteger(ctx, "config");
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        StormProgress progress = StormProgress.of(back, p);
        progress.setMirrorConfig(c);
        progress.setReceivers(0);
        if (progress.group.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM))) {
            EyeMirrors.apply(back, progress);
        }
        StormBackTrials.syncGates(back, progress.group);
        ctx.getSource().sendSuccess(() -> Component.literal("mirror config " + c + " (min " + EyeMirrors.solve(EyeMirrors.CONFIGS.get(c)) + " moves)"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        StormProgress s = StormProgress.of(back, p);
        String msg = String.format("beat %s (%d left, strength %.2f) | stones %d/3 west %s east %s front %s | sleeping %s | west route %d | mirrors %d receivers %s"
                + " | doors %s%s%s | raiju %s | keel %s | heart %s | epoch %d | eye room %s | east room %s",
                StormBackClock.phase(back).id(), StormBackClock.ticksLeft(back), StormBackClock.strength(back), s.rungCount(),
                s.rung(StormBackState.Trial.WEST), s.rung(StormBackState.Trial.EAST), s.rung(StormBackState.Trial.FRONT),
                s.sleeping() == null ? "none" : s.sleeping().id(), StormBackState.get(back).westRoute(), s.mirrorConfig(), Integer.toBinaryString(s.receivers()),
                s.eastDoorOpen(0) ? "1" : "0", s.eastDoorOpen(1) ? "1" : "0", s.eastDoorOpen(2) ? "1" : "0", s.raijuDefeated(), s.keelOpen(),
                s.heartDefeated(), s.epoch(), RoomLocks.owner(StormBackTrials.EYE_ROOM), RoomLocks.owner(StormBackTrials.EAST_ROOM));
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
