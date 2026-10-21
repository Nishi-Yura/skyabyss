package jp.revati.skyabyss.opening.site;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Map;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.dev.StructureDump;
import jp.revati.skyabyss.opening.OpeningSites.Kind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 序盤の構造物の建設の起点 (docs/design/17 の 4 章、8.2 の担当 2)。
//  - サーバーが始まったとき、まだ場所を決めていなければ決める (SiteFinder)。新しいワールドの最初の日なら、スポーン地点を野営地へ動かす (17-11)
//  - 建て終わっていない物は、BackBuildJobs と同じように、1 tick の予算 (ms) だけ進める (SiteJob)。野営地はプレイヤーが入る前に建て終える
//  - 環境変数 SKYABYSS_SHOTS (台本の撮影) のときは、ワールド名が "opening" で始まらなければ建てない (ほかの台本の邪魔をしない)。
//    SKYABYSS_OPENING_SITES=0 でも建てない。
// 開発コマンド: /skyabyss dev opening <info|build|view|dump> (本番の jar では登録されない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteBuilds {
    public static final long BUDGET_IDLE_MS = 20;
    public static final long BUDGET_PLAYERS_MS = 6;
    // 新しいワールドとみなす経過 tick (1 日)。これを過ぎたワールドでは、スポーン地点は動かさない
    public static final long NEW_WORLD_TICKS = 24000;

    private static final ArrayDeque<SiteJob> QUEUE = new ArrayDeque<>();

    private SiteBuilds() {
    }

    public static boolean enabled(MinecraftServer server) {
        if ("0".equals(System.getenv("SKYABYSS_OPENING_SITES"))) {
            return false;
        }
        if (System.getenv("SKYABYSS_SHOTS") != null && !server.getWorldData().getLevelName().startsWith("opening")) {
            return "1".equals(System.getenv("SKYABYSS_OPENING_SITES"));
        }
        return true;
    }

    // 建てている物があるか (章の跡地の整えも数える)
    public static boolean busy() {
        synchronized (SiteBuilds.class) {
            if (!QUEUE.isEmpty()) {
                return true;
            }
        }
        return ChapterGrounds.busy();
    }

    public static synchronized int pending() {
        return QUEUE.size();
    }

    // ---------------------------------------------------------------- 起動
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ChapterGrounds.registerRoads();
        if (!enabled(server)) {
            return;
        }
        plan(server);
        ChapterGrounds.ensurePlanned(server);
        ChapterSites.dedupe(server);
        enqueueMissing(server);
        // 野営地は、プレイヤーが入る前に建て終える (チャンクはスポーン地点のまわりが読み込み済み)
        SiteJob first;
        synchronized (SiteBuilds.class) {
            first = QUEUE.peek();
        }
        if (first != null && first.label().equals("camp")) {
            long until = System.nanoTime() + 400_000_000L;
            for (int i = 0; i < 4 && !first.done() && System.nanoTime() < until; i++) {
                first.step(until);
            }
            if (first.done()) {
                synchronized (SiteBuilds.class) {
                    QUEUE.remove(first);
                }
                SkyAbyss.LOGGER.info("Opening camp built before the first player: {} blocks, {} ms", first.placedCount, first.workNanos / 1_000_000L);
            }
        }
    }

    // 重い探索 (宿り・足跡・骨) の結果待ち
    private static volatile CompletableFuture<SiteFinder.Rest> pendingPlan;

    // 場所を探している最中か (章の跡地の探しも数える)
    public static boolean planning() {
        return pendingPlan != null || ChapterGrounds.planning() || ChapterSites.pending();
    }

    // 場所を決める (まだなら)。野営地はその場で決めて (新しいワールドの最初の日ならスポーン地点を野営地へ動かす)、
    // 残りは別のスレッドで探して、できたら tick の中で SiteData に書く
    public static boolean plan(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        if (data.planned() || pendingPlan != null) {
            return false;
        }
        SiteFinder.Probe probe = new SiteFinder.Probe(level);
        SiteData.Entry campEntry = data.first(Kind.CAMP.id);
        BlockPos camp;
        if (campEntry != null) {
            camp = campEntry.pos;
        } else {
            BlockPos spawn = level.getSharedSpawnPos();
            long t0 = System.nanoTime();
            camp = SiteFinder.planCamp(probe, spawn);
            if (camp == null) {
                SkyAbyss.LOGGER.error("Opening sites: no land for the camp within 160 blocks of the spawn {}", spawn);
                return false;
            }
            data.add(Kind.CAMP.id, 0, camp, 0).extra("notebook", camp);
            boolean fresh = level.getGameTime() < NEW_WORLD_TICKS;
            boolean move = fresh && (Math.abs(camp.getX() - spawn.getX()) > 2 || Math.abs(camp.getZ() - spawn.getZ()) > 2);
            if (move) {
                level.setDefaultSpawnPos(camp, 0f);
                SkyAbyss.LOGGER.info("Opening sites: the world spawn moved from {} to the camp at {}", spawn, camp);
            }
            data.markPlanned(level.getGameTime(), spawn, move);
            data.setPlanned(false);
            SkyAbyss.LOGGER.info("Opening camp planned at {} in {} ms", camp, (System.nanoTime() - t0) / 1_000_000L);
        }
        BlockPos campFinal = camp;
        pendingPlan = CompletableFuture.supplyAsync(() -> SiteFinder.planRestAll(probe, campFinal), Util.backgroundExecutor());
        return true;
    }

    // 別のスレッドの探索ができていれば、結果を SiteData に書いて、建設を予約する (tick の中で)
    private static void applyPlan(MinecraftServer server) {
        CompletableFuture<SiteFinder.Rest> f = pendingPlan;
        if (f == null || !f.isDone()) {
            return;
        }
        pendingPlan = null;
        SiteData data = SiteData.get(server.overworld());
        try {
            SiteFinder.Rest rest = f.join();
            for (SiteFinder.Planned p : rest.planned()) {
                data.add(p.kind(), p.index(), p.pos(), p.yaw());
            }
            data.setPlanned(true);
            ChapterGrounds.accept(server, rest.grounds());
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("Opening sites: the search failed", e);
            return;
        }
        enqueueMissing(server);
        SiteNetwork.broadcast(server);
    }

    // 建て終わっていない物を、野営地 → 宿り → 積み石 → 足跡 → 骨の順で予約する
    public static synchronized void enqueueMissing(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SiteData data = SiteData.get(level);
        java.util.Set<SiteData.Entry> queued = new java.util.HashSet<>();
        QUEUE.forEach(j -> queued.add(j.entry));
        for (String kind : new String[] {Kind.CAMP.id, Kind.SHELTER.id, "cairn", Kind.FOOTPRINT.id, Kind.RIBS.id}) {
            for (SiteData.Entry e : data.entries(kind)) {
                // 跡地の中の物 (一章の骨) は、跡地を整え終わってから
                if (!e.built && !queued.contains(e) && !waitsForGround(data, e.pos)) {
                    QUEUE.add(jobFor(level, data, e));
                }
            }
        }
        // 章の小要素 (ChapterSites)。進行方向の先に置いたもの (離れた人の分) は先に建てる
        List<SiteJob> front = new java.util.ArrayList<>();
        for (SiteData.ChapterPlan plan : data.plans()) {
            for (SiteData.Entry e : new java.util.ArrayList<>(plan.sites)) {
                if (!e.built && !queued.contains(e) && !waitsForGround(data, plan.anchor)) {
                    SiteJob job = ChapterSites.jobFor(level, data, e);
                    if (job == null) {
                        continue;
                    }
                    if (plan.ahead) {
                        front.add(job);
                    } else {
                        QUEUE.add(job);
                    }
                }
            }
        }
        for (int i = front.size() - 1; i >= 0; i--) {
            QUEUE.addFirst(front.get(i));
        }
        if (!QUEUE.isEmpty()) {
            SkyAbyss.LOGGER.info("Opening sites: {} to build", QUEUE.size());
        }
    }

    // pos が、まだ整え終わっていない跡地の中 (形の大きさ + 余白) か
    static boolean waitsForGround(SiteData data, BlockPos pos) {
        for (SiteData.Ground g : data.grounds()) {
            if (!g.shaped && (ChapterSites.horizontal(g.center, pos) <= 200 || ChapterSites.horizontal(g.shapeCenter, pos) <= 200)) {
                return true;
            }
        }
        return false;
    }

    public static SiteJob jobFor(ServerLevel level, SiteData data, SiteData.Entry e) {
        return switch (e.kind) {
            case "camp" -> CampBuilder.create(level, data, e);
            case "shelter" -> ShelterBuilder.create(level, data, e);
            case "footprint" -> FootprintBuilder.create(level, data, e);
            case "ribs" -> RibBuilder.create(level, data, e);
            default -> CairnBuilder.create(level, data, e);
        };
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        applyPlan(event.getServer());
        SiteJob job;
        synchronized (SiteBuilds.class) {
            job = QUEUE.peek();
        }
        if (job == null) {
            return;
        }
        long budget = event.getServer().getPlayerCount() > 0 ? BUDGET_PLAYERS_MS : BUDGET_IDLE_MS;
        if (job.step(System.nanoTime() + budget * 1_000_000L)) {
            synchronized (SiteBuilds.class) {
                QUEUE.remove(job);
            }
            SkyAbyss.LOGGER.info("Built the opening site {}: {} blocks in {} ticks ({} ms of work)", job.label(), job.placedCount, job.ticks,
                    job.workNanos / 1_000_000L);
            SiteNetwork.broadcast(event.getServer());
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        pendingPlan = null;
        synchronized (SiteBuilds.class) {
            QUEUE.clear();
        }
    }

    // 自己テスト・開発コマンド用: 予約を最後まで、チャンクをその場で読みながら進める
    public static void buildAllNow(MinecraftServer server) {
        ChapterGrounds.finishNow(server);
        enqueueMissing(server);
        while (true) {
            SiteJob job;
            synchronized (SiteBuilds.class) {
                job = QUEUE.peek();
            }
            if (job == null) {
                return;
            }
            job.blocking = true;
            while (!job.step(System.nanoTime() + 200_000_000L)) {
                // 次の周回
            }
            synchronized (SiteBuilds.class) {
                QUEUE.remove(job);
            }
        }
    }

    // ---------------------------------------------------------------- 開発コマンド
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        var kinds = StringArgumentType.word();
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("opening")
                        .then(Commands.literal("info").executes(SiteBuilds::info))
                        .then(Commands.literal("build").executes(SiteBuilds::build))
                        // view <種類> <番号> <離れる距離> <高さ> <yaw> <pitch>: 場所の中心から、yaw の向きを見る位置へ体ごと置く
                        .then(Commands.literal("view").then(Commands.argument("kind", kinds).then(Commands.argument("index", IntegerArgumentType.integer(0, 9))
                                .then(Commands.argument("dist", FloatArgumentType.floatArg(0, 400)).then(Commands.argument("up", FloatArgumentType.floatArg(-20, 80))
                                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 720)).then(Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90))
                                                .executes(SiteBuilds::view))))))))
                        // at <種類> <番号> <x> <y> <z> <yaw> <pitch>: 設計図の座標 (回す前。y=0 が地面の上の最初の段) と、設計図の向きの yaw で体ごと置く
                        .then(Commands.literal("at").then(Commands.argument("kind", kinds).then(Commands.argument("index", IntegerArgumentType.integer(0, 9))
                                .then(Commands.argument("bx", IntegerArgumentType.integer()).then(Commands.argument("by", IntegerArgumentType.integer())
                                        .then(Commands.argument("bz", IntegerArgumentType.integer()).then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 720))
                                                .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90, 90)).executes(SiteBuilds::at)))))))))
                        // goto <notebook|bell|smoke|slab|chaser> <離れる距離>: 帳面・鐘・煙の焚き火・鯨追いの焚き火の、南へ距離だけ離れた所へ体ごと置く (北向き。first_hour.txt 用)
                        .then(Commands.literal("goto").then(Commands.argument("what", StringArgumentType.word()).then(Commands.argument("dist", FloatArgumentType.floatArg(0, 400))
                                .executes(SiteBuilds::gotoSite))))
                        // dump <種類> <番号>: 場所のまわりを書き出す (tools/structure/check.py で調べる)
                        .then(Commands.literal("dump").then(Commands.argument("kind", kinds).then(Commands.argument("index", IntegerArgumentType.integer(0, 9))
                                .executes(SiteBuilds::dump)))))));
    }

    private static SiteData.Entry find(CommandContext<CommandSourceStack> ctx) {
        String kind = StringArgumentType.getString(ctx, "kind");
        int index = IntegerArgumentType.getInteger(ctx, "index");
        SiteData data = ctx.getSource().getServer().overworld() == null ? null : SiteData.get(ctx.getSource().getServer().overworld());
        // footprint_last = 足跡の並びの最後 (地へ潜った穴のある足跡)
        boolean last = kind.equals("footprint_last");
        if (last) {
            kind = "footprint";
        }
        SiteData.Entry best = null;
        for (SiteData.Entry e : data.entries(kind)) {
            if (last ? best == null || e.index > best.index : e.index == index) {
                best = e;
            }
        }
        return best;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        SiteData data = SiteData.get(server.overworld());
        StringBuilder sb = new StringBuilder("opening sites: planned=" + data.planned() + " spawn=" + server.overworld().getSharedSpawnPos().toShortString()
                + " (was " + data.originalSpawn().toShortString() + ", moved=" + data.spawnMoved() + ") pending=" + pending());
        for (SiteData.Entry e : data.entries()) {
            sb.append("\n  ").append(e.kind).append(' ').append(e.index).append(' ').append(e.pos.toShortString()).append(e.built ? " built" : " NOT built");
        }
        String s = sb.toString();
        SkyAbyss.LOGGER.info(s);
        ctx.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        plan(server);
        CompletableFuture<SiteFinder.Rest> f = pendingPlan;
        if (f != null) {
            f.join();
            applyPlan(server);
        }
        enqueueMissing(server);
        long t0 = System.nanoTime();
        buildAllNow(server);
        SiteNetwork.broadcast(server);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        ctx.getSource().sendSuccess(() -> Component.literal("opening sites built in " + ms + " ms"), false);
        return 1;
    }

    private static int view(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SiteData.Entry e = find(ctx);
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("no such site"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        float dist = FloatArgumentType.getFloat(ctx, "dist");
        float up = FloatArgumentType.getFloat(ctx, "up");
        // yaw は、野営地からその場所を見る向きからの差 (0 = 野営地からそちらへ歩いて着いたときの向き。野営地自身は南向きが 0)
        ServerLevel lv = ctx.getSource().getServer().overworld();
        BlockPos campPos = SiteData.get(lv).first(Kind.CAMP.id) == null ? e.pos : SiteData.get(lv).first(Kind.CAMP.id).pos;
        float base = e.kind.equals("camp") ? 0f : jp.revati.skyabyss.opening.OpeningSites.yawTo(campPos, e.pos);
        float yaw = base + FloatArgumentType.getFloat(ctx, "yaw");
        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));
        ServerLevel level = ctx.getSource().getServer().overworld();
        double x = e.pos.getX() + 0.5 - fx * dist;
        double z = e.pos.getZ() + 0.5 - fz * dist;
        level.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        player.teleportTo(level, x, e.pos.getY() + up, z, yaw, pitch);
        ctx.getSource().sendSuccess(() -> Component.literal("view " + e.kind + " " + e.index + " at " + e.pos.toShortString()), false);
        return 1;
    }

    private static int at(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SiteData.Entry e = find(ctx);
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("no such site"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int rot = SiteFinder.rot(e.yaw);
        boolean mirror = SiteFinder.mirror(e.yaw);
        BlockPos w = Blueprint.transform(IntegerArgumentType.getInteger(ctx, "bx"), IntegerArgumentType.getInteger(ctx, "by"),
                IntegerArgumentType.getInteger(ctx, "bz"), rot, mirror, e.pos);
        float yaw = FloatArgumentType.getFloat(ctx, "yaw");
        yaw = (mirror ? -yaw : yaw) + 90f * (rot & 3);
        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
        ServerLevel level = ctx.getSource().getServer().overworld();
        level.getChunk(w.getX() >> 4, w.getZ() >> 4);
        player.teleportTo(level, w.getX() + 0.5, w.getY(), w.getZ() + 0.5, yaw, pitch);
        ctx.getSource().sendSuccess(() -> Component.literal("at " + w.toShortString()), false);
        return 1;
    }

    private static int gotoSite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.overworld();
        BlockPos at = switch (StringArgumentType.getString(ctx, "what")) {
            case "notebook" -> jp.revati.skyabyss.opening.OpeningSites.notebook(server).orElse(null);
            case "bell" -> jp.revati.skyabyss.opening.OpeningSites.bell(server).orElse(null);
            case "smoke" -> jp.revati.skyabyss.opening.OpeningSites.smoke(server).orElse(null);
            case "slab" -> jp.revati.skyabyss.opening.OpeningSites.slab(server).orElse(null);
            case "chaser" -> jp.revati.skyabyss.opening.OpeningSites.bell(server)
                    .map(b -> jp.revati.skyabyss.npc.NpcSpawner.chaserSpot(level, b)).orElse(null);
            default -> null;
        };
        if (at == null) {
            ctx.getSource().sendFailure(Component.literal("no such place (notebook|bell|smoke|slab|chaser), or not built yet"));
            return 0;
        }
        float dist = FloatArgumentType.getFloat(ctx, "dist");
        level.getChunk(at.getX() >> 4, (at.getZ() + (int) dist) >> 4);
        ctx.getSource().getPlayerOrException().teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5 + dist, 180f, 0f);
        ctx.getSource().sendSuccess(() -> Component.literal("goto " + StringArgumentType.getString(ctx, "what") + " " + at.toShortString()), false);
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        SiteData.Entry e = find(ctx);
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("no such site"));
            return 0;
        }
        SiteData sd = SiteData.get(ctx.getSource().getServer().overworld());
        String bpName = e.kind.equals("footprint") ? FootprintBuilder.blueprintName(sd, e) : e.kind;
        Blueprint bp = Blueprint.get(bpName);
        int[] r = bp.worldRect(SiteFinder.rot(e.yaw), SiteFinder.mirror(e.yaw), new BlockPos(e.pos.getX(), 0, e.pos.getZ()));
        int pad = 8;
        int y0 = e.pos.getY() - (e.kind.equals("footprint") ? 18 : 10);
        int y1 = e.pos.getY() + Math.max(bp.maxY, 4) + 8;
        Map<String, BlockPos> points = new LinkedHashMap<>();
        points.put("center", e.pos);
        for (int i = 0; i < e.extraNames.size(); i++) {
            points.put(e.extraNames.get(i), e.extraPos.get(i));
        }
        String name = "opening_" + e.kind + (e.kind.equals("footprint") || e.kind.equals("cairn") ? "_" + e.index : "");
        ServerLevel level = ctx.getSource().getServer().overworld();
        StructureDump.queueRange(level, name, new BlockPos(r[0] - pad, y0, r[1] - pad), new BlockPos(r[2] + pad, y1, r[3] + pad), points);
        ctx.getSource().sendSuccess(() -> Component.literal("dumping " + name), false);
        return 1;
    }
}
