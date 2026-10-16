package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 潮殻の背の本番のテスト用コマンド (OP)。08 の 12章の案に合わせた:
// /skyabyss titan reset tide                       : 試練だけ戻す (心臓も眠り直す)
// /skyabyss titan tide <low|high|cycle>            : 潮を干潮・満潮に止める / 時計に戻す (潮殻の背だけの潮)
// /skyabyss titan sleep tide <west|east|front|none> : 眠る試練を決めて、試練を戻す
// 確かめるための操作 (/skyabyss tideback ...):
//   go <場所>           : 浜・広間・庭・大貝・祭壇・潮見・岩棚・干潟の前室・干潟・殻の底の前室へ移る
//   ring <west|east|front> : 石を鳴らす (試練を飛ばす)
//   status              : 潮・石・眠る試練・種・巣・潮招き
//   watch <下> <中> <上> : 潮見の間の水位を直接決める
//   nests               : 群れの巣をすべて壊す (潮招きの集合が始まる)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideBackCommands {
    private static final Map<String, BlockPos> PLACES = Map.ofEntries(
            Map.entry("beach", ARRIVAL),
            Map.entry("hub", RESTS.get(0).spawn()),
            Map.entry("garden", RESTS.get(1).spawn()),
            Map.entry("clam", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1] - 2)),
            Map.entry("altar", new BlockPos(ALTAR[0] + 1, ALTAR_TOP + 1, ALTAR[1] + 1)),
            Map.entry("watch", RESTS.get(3).spawn()),
            Map.entry("ledge", new BlockPos(53, LEDGE_FLOOR + 1, -32)),
            Map.entry("f0", RESTS.get(4).spawn()),
            Map.entry("flats", new BlockPos(0, FLATS_FLOOR + 1, -76 + FLATS_DZ)),
            Map.entry("heart", RESTS.get(5).spawn()),
            Map.entry("bottom", new BlockPos(0, HUB_FLOOR + 1, 3)));

    private TideBackCommands() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> tide = op("tide");
        for (String p : new String[] {"low", "high", "cycle"}) {
            tide.then(op(p).executes(ctx -> tide(ctx, p)));
        }
        LiteralArgumentBuilder<CommandSourceStack> sleep = op("tide");
        for (String t : new String[] {"west", "east", "front", "none"}) {
            sleep.then(op(t).executes(ctx -> sleep(ctx, t)));
        }
        // 親の節 (reset など) には executes を付けない。岩鯨の同じ節へ合わさるとき、岩鯨のコマンドを上書きしないため
        dispatcher.register(op(SkyAbyss.MODID).then(op("titan")
                .then(op("reset").then(op("tide").executes(TideBackCommands::reset)))
                .then(tide)
                .then(op("sleep").then(sleep))));

        LiteralArgumentBuilder<CommandSourceStack> go = op("go");
        for (String place : PLACES.keySet()) {
            go.then(op(place).executes(ctx -> go(ctx, place)));
        }
        LiteralArgumentBuilder<CommandSourceStack> ring = op("ring");
        for (TideBackState.Trial t : TideBackState.Trial.values()) {
            ring.then(op(t.id()).executes(ctx -> ring(ctx, t)));
        }
        dispatcher.register(op(SkyAbyss.MODID).then(op("tideback").then(go).then(ring)
                .then(op("status").executes(TideBackCommands::status))
                .then(op("nests").executes(TideBackCommands::nests))
                .then(op("watch").then(Commands.argument("b", IntegerArgumentType.integer(0, 7))
                        .then(Commands.argument("m", IntegerArgumentType.integer(0, 7))
                                .then(Commands.argument("t", IntegerArgumentType.integer(0, 7)).executes(TideBackCommands::watch)))))));
    }

    private static ServerLevel back(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = ctx.getSource().getServer().getLevel(TideTravel.TIDE_BACK);
        if (back == null || TitanBackData.get(back).needsBuild(LAYOUT_VERSION)) {
            ctx.getSource().sendFailure(Component.translatable("commands.skyabyss.tideback.not_built"));
            return null;
        }
        return back;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackTrials.reset(back, true);
        TideBackState.Trial s = TideBackState.get(back).sleeping();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.reset", s == null ? "none" : s.id()), true);
        return 1;
    }

    private static int tide(CommandContext<CommandSourceStack> ctx, String phase) {
        TideClock.force(switch (phase) {
            case "low" -> TideClock.Phase.LOW;
            case "high" -> TideClock.Phase.HIGH;
            default -> null;
        });
        ServerLevel back = ctx.getSource().getServer().getLevel(TideTravel.TIDE_BACK);
        if (back != null) {
            TideBackTrials.sendGaugeNow(back);
        }
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.tide", phase), true);
        return 1;
    }

    private static int sleep(CommandContext<CommandSourceStack> ctx, String id) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackState.Trial t = TideBackState.Trial.byId(id);
        TideBackTrials.reset(back, true, t, t == null);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.sleep", id), true);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx, String place) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        BlockPos p = PLACES.get(place);
        player.fallDistance = 0;
        player.teleportTo(back, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), 0f);
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.go", place), false);
        return 1;
    }

    private static int ring(CommandContext<CommandSourceStack> ctx, TideBackState.Trial t) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackState state = TideBackState.get(back);
        if (t == TideBackState.Trial.FRONT) {
            state.markBeckonerDefeated();
        }
        TideBackTrials.ring(back, state, t, ctx.getSource().getPlayer());
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.ring", t.id()), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackState s = TideBackState.get(back);
        int nests = 0;
        for (int i = 0; i < 6; i++) {
            if ((s.nestSites() & (1 << i)) != 0 && !s.nestBroken(i)) {
                nests++;
            }
        }
        int left = nests;
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.status", TideClock.phase(back).name().toLowerCase(java.util.Locale.ROOT),
                TideClock.ticksLeft(back), s.rungCount(), s.sleeping() == null ? "none" : s.sleeping().id(), s.gardenRoute(), s.eastConfig(), left,
                s.beckonerDefeated() ? "defeated" : TideFlats.fightActive() ? "fighting" : "waiting", s.bottomOpen() ? "open" : "closed"), false);
        int[] lv = TideWatch.levels();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.watch", lv[0], lv[1], lv[2], TideWatch.config()), false);
        return 1;
    }

    private static int watch(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideWatch.setLevels(back, IntegerArgumentType.getInteger(ctx, "b"), IntegerArgumentType.getInteger(ctx, "m"), IntegerArgumentType.getInteger(ctx, "t"));
        int[] lv = TideWatch.levels();
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.watch", lv[0], lv[1], lv[2], TideWatch.config()), false);
        return 1;
    }

    private static int nests(CommandContext<CommandSourceStack> ctx) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        for (Entity e : back.getEntitiesOfClass(ReefNest.class, FLATS.aabb().inflate(4))) {
            ((ReefNest) e).kill();
        }
        return 1;
    }
}
