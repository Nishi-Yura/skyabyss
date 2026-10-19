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
// /skyabyss titan reset tide                       : 打った人のグループの試練だけ戻す (心臓も眠り直す。コンソールからは記録のあるすべてのグループ)
// /skyabyss titan tide <low|high|cycle>            : 潮を干潮・満潮に止める / 時計に戻す (潮殻の背だけの潮)
// /skyabyss titan sleep tide <west|east|front|none> : 打った人のグループの眠る試練を決めて、そのグループの試練を戻す
// 確かめるための操作 (/skyabyss tideback ...):
//   go <場所>           : 浜・広間・庭・大貝・祭壇・潮見・岩棚・干潟の前室・干潟・殻の底の前室・息の間の縁 (breath)・舞台の下 (vent)・舞台 (summit)・展望の間 (deck) へ移る
//   ring <west|east|front> : 打った人のグループの石を鳴らす (試練を飛ばす)
//   status              : 潮と、打った人のグループの石・眠る試練・種・巣・潮招き、部屋の持ち主
//   watch <下> <中> <上> : 潮見の間の水位を直接決める
//   nests               : 群れの巣をすべて壊す (潮招きの集合が始まる)
//   route <x> <y> <z>   : 自分の足もとから、そこまで歩いて (跳んで 1 段・落ちて 3 段) 行く道の曲がり角を出す。
//                         試遊の自動の歩き (外の道具がキーで歩かせる) と、迷う所を調べるのに使う。行けなければ「行けない」
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
            Map.entry("bottom", new BlockPos(0, HUB_FLOOR + 1, 3)),
            // 息の間: 縁 (入口の泡の近く)・皿 7 (舞台の真下)・舞台・展望の間
            Map.entry("breath", RESTS.get(6).spawn()),
            Map.entry("vent", BlockPos.containing(BreathLayout.dishNode(BreathLayout.DISHES - 1).landX(), BreathLayout.dishNode(BreathLayout.DISHES - 1).landY(),
                    BreathLayout.dishNode(BreathLayout.DISHES - 1).landZ())),
            Map.entry("summit", BlockPos.containing(BreathLayout.arena().landX(), BreathLayout.arena().landY(), BreathLayout.arena().landZ())),
            Map.entry("deck", RESTS.get(9).spawn()));

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
                .then(op("route").then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(TideBackCommands::route)))))
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

    // 進みはグループごと。コマンドを打った人のグループ (人でなければ、記録のあるすべてのグループ) を戻す
    private static int reset(CommandContext<CommandSourceStack> ctx) {
        return resetFor(ctx, null, false, "commands.skyabyss.tideback.reset", null);
    }

    private static int resetFor(CommandContext<CommandSourceStack> ctx, TideBackState.Trial force, boolean noSleep, String key, String arg) {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            java.util.UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(player);
            TideBackTrials.resetGroup(back, group, true, force, noSleep);
            TideBackState.Trial s = TideBackState.of(back, group).sleeping();
            String shown = arg != null ? arg : s == null ? "none" : s.id();
            ctx.getSource().sendSuccess(() -> ModText.hint(key, shown), true);
        } else {
            TideBackTrials.resetAllGroups(back, true, force, noSleep);
            String shown = arg != null ? arg : "-";
            ctx.getSource().sendSuccess(() -> ModText.hint(key, shown), true);
        }
        return 1;
    }

    // コマンドを打った人のグループの進み (人でなければ失敗)
    private static TideBackState.Group groupState(CommandContext<CommandSourceStack> ctx, ServerLevel back) throws CommandSyntaxException {
        return TideBackTrials.state(back, ctx.getSource().getPlayerOrException());
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
        TideBackState.Trial t = TideBackState.Trial.byId(id);
        return resetFor(ctx, t, t == null, "commands.skyabyss.tideback.sleep", id);
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

    private static int ring(CommandContext<CommandSourceStack> ctx, TideBackState.Trial t) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackState.Group state = groupState(ctx, back);
        if (t == TideBackState.Trial.FRONT) {
            state.markBeckonerDefeated();
        }
        TideBackTrials.ring(back, state, t, ctx.getSource().getPlayer());
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.ring", t.id()), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel back = back(ctx);
        if (back == null) {
            return 0;
        }
        TideBackState.Group s = groupState(ctx, back);
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
        ctx.getSource().sendSuccess(() -> ModText.hint("commands.skyabyss.tideback.rooms", shortId(TideRooms.owner(TideRooms.GARDEN)),
                shortId(TideRooms.owner(TideRooms.WATCH)), shortId(TideRooms.owner(TideRooms.FLATS)),
                shortId(jp.revati.skyabyss.titan.tide.heart.TideHeartBattle.owner()), shortId(s.id)), false);
        return 1;
    }

    private static String shortId(java.util.UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
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

    // 歩いて行く道 (曲がり角だけ)。行き先は立てる所で、近い所 (2 マス以内) に着ければよい
    private static int route(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel back = back(ctx);
        if (back == null || player.level() != back) {
            return 0;
        }
        BlockPos goal = new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
        // 足もとが立てる所でなければ (階段・半ブロック・水の中など)、すぐそばの立てる所から
        BlockPos start = player.blockPosition();
        search:
        for (int dy : new int[] {0, 1, -1}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos c = player.blockPosition().offset(dx, dy, dz);
                    if (TideBackSelfTest.standable(back, c)) {
                        start = c;
                        break search;
                    }
                }
            }
        }
        java.util.Map<Long, Integer> dist = TideBackSelfTest.walk(back, start);
        BlockPos best = null;
        double bd = 4.5;
        for (long k : dist.keySet()) {
            BlockPos q = BlockPos.of(k);
            double d = q.distSqr(goal);
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        if (best == null) {
            ctx.getSource().sendFailure(Component.literal("route: unreachable from " + start.toShortString()));
            return 0;
        }
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        Long cur = best.asLong();
        while (cur != null) {
            cells.add(BlockPos.of(cur));
            cur = TideBackSelfTest.PARENT.get(cur);
        }
        java.util.Collections.reverse(cells);
        // 糸を張る: まっすぐ歩ける (間の点が、行ける所で高さが 1 段ずつしか変わらない) いちばん先の点へ
        StringBuilder sb = new StringBuilder("route ").append(cells.size() - 1).append(':');
        int i = 0;
        while (i < cells.size() - 1) {
            int j = i + 1;
            for (int k = Math.min(cells.size() - 1, i + 24); k > i + 1; k--) {
                if (straight(dist, cells.get(i), cells.get(k))) {
                    j = k;
                    break;
                }
            }
            BlockPos w = cells.get(j);
            sb.append(' ').append(w.getX()).append(',').append(w.getY()).append(',').append(w.getZ());
            i = j;
        }
        String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return cells.size();
    }

    private static boolean straight(java.util.Map<Long, Integer> dist, BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) {
            return false;
        }
        double len = Math.sqrt(Math.pow(b.getX() - a.getX(), 2) + Math.pow(b.getZ() - a.getZ(), 2));
        int n = (int) Math.ceil(len / 0.25);
        // 体の幅 (0.6) ぶん、左右にずらした線も調べる
        double nx = -(b.getZ() - a.getZ()) / len * 0.35;
        double nz = (b.getX() - a.getX()) / len * 0.35;
        for (int s = 0; s <= n; s++) {
            double t = (double) s / n;
            double x = a.getX() + 0.5 + (b.getX() - a.getX()) * t;
            double z = a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t;
            for (int side = -1; side <= 1; side++) {
                BlockPos c = BlockPos.containing(x + nx * side, a.getY(), z + nz * side);
                if (!dist.containsKey(c.asLong())) {
                    return false;
                }
            }
        }
        return true;
    }
}
