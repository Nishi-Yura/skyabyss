package jp.revati.skyabyss.titan.dungeon;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 開発用 (製品では登録しない): 光海の灯を撮る・確かめるための近道。
//   /skyabyss dev lamp tp <n> [向き 0-3]   n 番目の灯の台 (1〜) の近くへ移り、台を見る
//   /skyabyss dev lamp light <n>           n 番目の台に、自分のグループの灯をともす (灯は消費しない)
//   /skyabyss dev lamp all                 全部ともす
//   /skyabyss dev lamp clear               自分のグループの灯を消す
//   /skyabyss dev lamp at <x> <y> <z>      背中のその位置へ
//   /skyabyss dev lamp exit                出口の戸の手前へ移り、戸の方を見る
//   /skyabyss dev lamp entry               入口の小部屋の中へ移り、奥 (苗床の壁) を見る
//   /skyabyss dev lamp look <x> <y> <z>    いまの位置からその点を見る
//   /skyabyss dev lamp path <k> [0|1]      正しい道の k 番目のマスの真ん中へ移り、次のマス (1 なら前のマス) を見る
//   /skyabyss dev lamp pocket <n>          n 番目の息継ぎのくぼみの隅の水中へ移り、泡の柱を見る
//   /skyabyss dev lamp cut <n>             n 番目の近道の戸の手前へ移り、戸を見る
//   /skyabyss dev lamp vista <n>           n 番目の台を、まっすぐな通路の手前 (最大 3 マス) から見る (遠くからの見え方)
//   /skyabyss dev lamp zone <z>            水域 z (0〜3) の最初のマスから、道の先を見る
//   /skyabyss dev lamp info                 迷路の計画 (台・くぼみ・近道・水域) を書き出す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AnemoneDev {
    private AnemoneDev() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("lamp")
                        .then(Commands.literal("tp").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
                                .executes(c -> tp(c.getSource(), IntegerArgumentType.getInteger(c, "n"), 0))
                                .then(Commands.argument("dir", IntegerArgumentType.integer(0, 3))
                                        .executes(c -> tp(c.getSource(), IntegerArgumentType.getInteger(c, "n"), IntegerArgumentType.getInteger(c, "dir"))))))
                        .then(Commands.literal("light").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
                                .executes(c -> light(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("all").executes(c -> lightAll(c.getSource())))
                        .then(Commands.literal("clear").executes(c -> clear(c.getSource())))
                        .then(Commands.literal("exit").executes(c -> exit(c.getSource())))
                        .then(Commands.literal("entry").executes(c -> entry(c.getSource())))
                        .then(Commands.literal("at").then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> at(c.getSource(), IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))
                        .then(Commands.literal("path").then(Commands.argument("k", IntegerArgumentType.integer(0, 99))
                                .executes(c -> pathCam(c.getSource(), IntegerArgumentType.getInteger(c, "k"), false))
                                .then(Commands.argument("back", IntegerArgumentType.integer(0, 1))
                                        .executes(c -> pathCam(c.getSource(), IntegerArgumentType.getInteger(c, "k"), IntegerArgumentType.getInteger(c, "back") == 1)))))
                        .then(Commands.literal("pocket").then(Commands.argument("n", IntegerArgumentType.integer(1, 99))
                                .executes(c -> pocketCam(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("cut").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
                                .executes(c -> cutCam(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("vista").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
                                .executes(c -> vista(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("zone").then(Commands.argument("z", IntegerArgumentType.integer(0, 3))
                                .executes(c -> zoneCam(c.getSource(), IntegerArgumentType.getInteger(c, "z")))))
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))
                        .then(Commands.literal("look").then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> look(c.getSource(), IntegerArgumentType.getInteger(c, "x"),
                                                        IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"))))))))));
    }

    private static ServerLevel back(CommandSourceStack src) {
        return src.getServer().getLevel(TitanTravel.TITAN_BACK);
    }

    private static void face(ServerPlayer p, ServerLevel level, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - y, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(level, x, y, z, yaw, pitch);
    }

    private static int tp(CommandSourceStack src, int n, int dir) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        List<BlockPos> stands = AnemoneLights.stands(level);
        if (n > stands.size()) {
            src.sendFailure(Component.literal("only " + stands.size() + " stands"));
            return 0;
        }
        BlockPos s = stands.get(n - 1);
        int[][] d = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        face(p, level, s.getX() + 0.5 + d[dir][0] * 1.3, s.getY() + 1.4, s.getZ() + 0.5 + d[dir][1] * 1.3, s.getX() + 0.5, s.getY() + 0.9, s.getZ() + 0.5);
        src.sendSuccess(() -> Component.literal("stand " + n + " at " + s.toShortString()), false);
        return 1;
    }

    private static int at(CommandSourceStack src, int x, int y, int z) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        p.teleportTo(back(src), x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int look(CommandSourceStack src, int x, int y, int z) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        face(p, back(src), p.getX(), p.getY(), p.getZ(), x + 0.5, y + 0.5, z + 0.5);
        return 1;
    }

    private static int pathCam(CommandSourceStack src, int k, boolean back) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        MazePlan plan = AnemoneLights.plan(level);
        k = Math.min(k, plan.path.size() - 1);
        int k2 = back ? Math.max(0, k - 1) : Math.min(plan.path.size() - 1, k + 1);
        if (k2 == k) {
            k2 = back ? k + 1 : k - 1;
        }
        BlockPos a = MazePlan.center(plan.path.get(k));
        BlockPos b = MazePlan.center(plan.path.get(k2));
        // 通路の見通しがよいよう、手前のマスの縁から、向かうマスの真ん中を見る
        double fx = a.getX() + 0.5 - Integer.signum(b.getX() - a.getX()) * 1.4;
        double fz = a.getZ() + 0.5 - Integer.signum(b.getZ() - a.getZ()) * 1.4;
        face(p, level, fx, a.getY() + 1.1, fz, b.getX() + 0.5, a.getY() + 0.9, b.getZ() + 0.5);
        return 1;
    }

    private static int vista(CommandSourceStack src, int n) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        MazePlan plan = AnemoneLights.plan(level);
        int k = plan.standK[Math.min(n, plan.standK.length) - 1];
        int back = 1;
        // 手前へ、同じ向きにまっすぐ続くマスを最大 3 つまでさかのぼる
        int[] s0 = plan.path.get(k);
        int[] s1 = plan.path.get(k - 1);
        int di = s0[0] - s1[0];
        int dj = s0[1] - s1[1];
        while (back < 3 && k - back - 1 >= 0 && plan.path.get(k - back)[0] - plan.path.get(k - back - 1)[0] == di
                && plan.path.get(k - back)[1] - plan.path.get(k - back - 1)[1] == dj) {
            back++;
        }
        BlockPos cam = MazePlan.center(plan.path.get(k - back));
        BlockPos target = MazePlan.center(s0);
        face(p, level, cam.getX() + 0.5, cam.getY() + 1.1, cam.getZ() + 0.5, target.getX() + 0.5, target.getY() + 0.7, target.getZ() + 0.5);
        src.sendSuccess(() -> Component.literal("vista " + n + " from " + cam.toShortString()), false);
        return 1;
    }

    private static int zoneCam(CommandSourceStack src, int z) throws CommandSyntaxException {
        MazePlan plan = AnemoneLights.plan(back(src));
        int k = z == 0 ? 1 : Math.min(plan.path.size() - 3, plan.standK[Math.min(z, plan.standK.length) - 1] + 1);
        return pathCam(src, k, false);
    }

    private static int pocketCam(CommandSourceStack src, int n) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        MazePlan plan = AnemoneLights.plan(level);
        List<int[]> pockets = plan.pockets;
        BlockPos c = MazeBuilder.allPockets(plan.maze).get(Math.min(n, pockets.size()) - 1);
        face(p, level, c.getX() + 0.3, c.getY() + 1.0, c.getZ() + 0.3, c.getX() + 1.5, c.getY() + 1.2, c.getZ() + 1.5);
        src.sendSuccess(() -> Component.literal("pocket " + n + " at " + c.toShortString()), false);
        return 1;
    }

    private static int cutCam(CommandSourceStack src, int n) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        MazePlan plan = AnemoneLights.plan(level);
        if (n > plan.cuts.size()) {
            src.sendFailure(Component.literal("only " + plan.cuts.size() + " shortcuts"));
            return 0;
        }
        MazePlan.Cut cut = plan.cuts.get(n - 1);
        BlockPos a = MazePlan.center(cut.a());
        BlockPos b = MazePlan.center(cut.b());
        double fx = a.getX() + 0.5 - Integer.signum(b.getX() - a.getX()) * 0.9;
        double fz = a.getZ() + 0.5 - Integer.signum(b.getZ() - a.getZ()) * 0.9;
        face(p, level, fx, a.getY() + 1.0, fz, (a.getX() + b.getX()) / 2.0 + 0.5, a.getY() + 1.0, (a.getZ() + b.getZ()) / 2.0 + 0.5);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        MazePlan plan = AnemoneLights.plan(back(src));
        StringBuilder sb = new StringBuilder("path ").append(plan.path.size()).append(" cells; stands at path index ");
        for (int k : plan.standK) {
            sb.append(k).append(' ');
        }
        sb.append("; pockets ").append(plan.pockets.size()).append(" (on path ").append(plan.pathPockets.size()).append("); shortcuts ");
        for (MazePlan.Cut cut : plan.cuts) {
            sb.append(cut.ka()).append("->").append(cut.kb()).append("@lit").append(cut.lit()).append(' ');
        }
        String msg = sb.toString();
        src.sendSuccess(() -> Component.literal(msg), false);
        SkyAbyss.LOGGER.info("LAMP INFO {}", msg);
        return 1;
    }

    private static int light(CommandSourceStack src, int n) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        UUID g = Groups.keyOf(p);
        AnemoneLights.devLight(level, g, n - 1);
        return 1;
    }

    private static int lightAll(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        UUID g = Groups.keyOf(p);
        for (int i = 0; i < AnemoneLights.STANDS; i++) {
            AnemoneLights.devLight(level, g, i);
        }
        return 1;
    }

    private static int clear(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        UUID g = Groups.keyOf(p);
        BackGroupData.Record r = GroupProgress.of(level, g);
        r.resetProgress();
        GroupOverlays.refresh(level, g);
        return 1;
    }

    private static int exit(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        List<BlockPos> gate = AnemoneLights.exitGate(level);
        BlockPos mid = gate.get(gate.size() / 2);
        BlockPos exit = MazeBuilder.exitBubble();
        int sx = Integer.signum(mid.getX() - exit.getX());
        int sz = Integer.signum(mid.getZ() - exit.getZ());
        face(p, level, mid.getX() + 0.5 + sx * 3, mid.getY() + 0.8, mid.getZ() + 0.5 + sz * 3, mid.getX() + 0.5, mid.getY() + 0.8, mid.getZ() + 0.5);
        return 1;
    }

    private static int entry(CommandSourceStack src) throws CommandSyntaxException {
        ServerLevel level = back(src);
        ServerPlayer p = src.getPlayerOrException();
        TitanBackLayout.Box r = TitanBackLayout.MAZE_ENTRY_ROOM;
        face(p, level, r.x1() + 1.5, r.floorY() + 1, r.z2() + 0.5, r.center().getX() + 0.5, r.floorY() + 1.5, r.z1() + 0.5);
        return 1;
    }
}
