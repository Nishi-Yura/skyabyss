package jp.revati.skyabyss.titan.light;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;

import it.unimi.dsi.fastutil.longs.LongIterator;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BuildAudit;
import jp.revati.skyabyss.titan.audit.BuildAuditSelfTest;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 開発用: 歩く道の灯りの場所を選ぶ。/skyabyss dev routelight <tide|storm|ember>
// 建てた背の道 (BackReach の到達点) の光度を測り、光度 4 未満の所の近くの石を灯りのブロック (RouteLamps.lampOf) に置き換える。
// 光の計算を待って (80 tick) 測り直し、暗い所が無くなるまで (最大 10 回) 繰り返す。灯りどうしは 5 マス以上あける (所々に影を残す)。
// 終わると全部の灯りの場所を、サーバーのフォルダの route_lamps_<名前>.json に書く (data/skyabyss/<名前>_back/route_lamps.json へ写す)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RouteLightDev {
    static final int NEED = 4;
    static final int SPACING = 5;
    private static String name;
    private static ServerLevel level;
    private static BackReach.Box box;
    private static int wait;
    private static int round;
    private static final List<BlockPos> LAMPS = new ArrayList<>();

    private RouteLightDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("routelight").then(Commands.argument("titan", StringArgumentType.word()).executes(ctx -> {
                    String n = StringArgumentType.getString(ctx, "titan");
                    ResourceKey<Level> key = switch (n) {
                        case "tide" -> TideTravel.TIDE_BACK;
                        case "storm" -> StormTravel.STORM_BACK;
                        case "ember" -> EmberIds.BACK;
                        default -> null;
                    };
                    if (key == null) {
                        return 0;
                    }
                    ServerLevel lv = ctx.getSource().getServer().getLevel(key);
                    if (lv == null) {
                        return 0;
                    }
                    name = n;
                    level = lv;
                    box = switch (n) {
                        case "tide" -> BuildAuditSelfTest.TIDE_BOX;
                        case "storm" -> BuildAuditSelfTest.STORM_BOX;
                        default -> BuildAuditSelfTest.EMBER_BOX;
                    };
                    LAMPS.clear();
                    LAMPS.addAll(RouteLamps.load(n));
                    round = 0;
                    wait = 100;
                    SkyAbyss.LOGGER.info("ROUTELIGHT {} start with {} existing lamps", n, LAMPS.size());
                    return 1;
                })))));
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (level == null || --wait > 0) {
            return;
        }
        BackReach.Report reach = BuildAuditSelfTest.routeOf(name, level);
        List<BlockPos> dark = new ArrayList<>();
        int cells = 0;
        if (reach.reached != null) {
            LongIterator it = reach.reached.iterator();
            while (it.hasNext()) {
                BlockPos p = BlockPos.of(it.nextLong());
                if (!box.contains(p)) {
                    continue;
                }
                cells++;
                if (level.getRawBrightness(p, 0) < NEED) {
                    dark.add(p);
                }
            }
        }
        SkyAbyss.LOGGER.info("ROUTELIGHT {} round {}: {} route cells, {} dark (< {}), {} lamps", name, round, cells, dark.size(), NEED, LAMPS.size());
        if (dark.isEmpty() || round >= 10) {
            finish(dark.size());
            return;
        }
        dark.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY()) : a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX()) : Integer.compare(a.getZ(), b.getZ()));
        BlockState lamp = RouteLamps.lampOf(name);
        List<BlockPos> thisRound = new ArrayList<>();
        int stuck = 0;
        for (BlockPos c : dark) {
            boolean near = false;
            for (BlockPos l : thisRound) {
                if (l.distSqr(c) <= SPACING * SPACING) {
                    near = true;
                    break;
                }
            }
            if (near) {
                continue;
            }
            BlockPos q = pick(c);
            if (q == null) {
                stuck++;
                continue;
            }
            level.setBlock(q, lamp, 2 | 16);
            thisRound.add(q);
            LAMPS.add(q);
        }
        SkyAbyss.LOGGER.info("ROUTELIGHT {} round {}: placed {} lamps, {} cells with no spot", name, round, thisRound.size(), stuck);
        if (thisRound.isEmpty()) {
            finish(dark.size());
            return;
        }
        round++;
        wait = 80;
    }

    // 立ち位置 c の近く (横 2・上下 0〜3) の空気に接した、置き換えてよい石。近いほど・上と横ほどよい (床は避ける)
    private static BlockPos pick(BlockPos c) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos a = c.offset(dx, dy, dz);
                    BlockState as = level.getBlockState(a);
                    if (BuildAudit.topOf(as) != 0 || !as.getFluidState().isEmpty()) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        BlockPos q = a.relative(d);
                        BlockState qs = level.getBlockState(q);
                        if (!RouteLamps.replaceable(qs)) {
                            continue;
                        }
                        double score = q.distSqr(c.above());
                        if (q.getY() < c.getY()) {
                            score += 12;
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = q;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static void finish(int darkLeft) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < LAMPS.size(); i++) {
            BlockPos p = LAMPS.get(i);
            sb.append(i == 0 ? "" : ",").append("[").append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append("]");
        }
        sb.append("]");
        try {
            Files.writeString(Path.of("route_lamps_" + name + ".json"), sb.toString());
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.warn("could not write the lamp list: {}", e.toString());
        }
        SkyAbyss.LOGGER.info("ROUTELIGHT {} done: {} lamps, {} dark cells left", name, LAMPS.size(), darkLeft);
        level = null;
    }
}
