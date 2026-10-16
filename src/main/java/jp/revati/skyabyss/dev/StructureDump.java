package jp.revati.skyabyss.dev;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.tide.TideBackBuilder;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 構造をデータで確かめるための書き出し (開発時だけ)。指定した範囲のブロックを 1 つのファイル (.sbd) に書く。
// 読むのは tools/structure/ の Python (上・横の断面図、等角の図、外とつながる空気、歩いて行ける所、水の広がり、看板の一覧)。
//
//   /skyabyss dev dump <名前> <端1> <端2>   : 実行した次元の範囲 (execute in で次元を選ぶ)
//   /skyabyss dev dump preset <titan_back|tide_back|storm_back> : 巨神の背の島全体 (まだ建っていなければ建てる)
//   環境変数 SKYABYSS_DUMP=titan_back,tide_back で専用サーバーを起動すると、起動後に書き出してサーバーを止める
//
// 書き出し先: 環境変数 SKYABYSS_DUMP_OUT、無ければ <サーバーのフォルダ>/dumps/<名前>.sbd (worktree の run/ の下)。
// 形式 (gzip): "SKYDUMP1" / ヘッダの長さ (4 バイト、big endian) / ヘッダの JSON (UTF-8) / パレットの番号 (uint16 little endian、y・z・x の順)。
// ヘッダ: 名前・次元・端 (origin)・大きさ (size [x,y,z])・パレット (名前・状態・地図の色・当たり・液体・はしご)・看板・ブロックエンティティ
// 読み込むチャンクが多いので、1 tick に数チャンクずつ読む (ウォッチドッグに止められないように)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StructureDump {
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        static Box of(BlockPos a, BlockPos b) {
            return new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }

        int sx() {
            return x2 - x1 + 1;
        }

        int sy() {
            return y2 - y1 + 1;
        }

        int sz() {
            return z2 - z1 + 1;
        }
    }

    // 巨神の背の島全体。鯨は x ±100・z ±256・y 58〜 (WhaleShape)、亀は甲羅 x ±71・z -88〜82 に頭と尾、海底 41
    public static final Map<String, Box> PRESETS = Map.of(
            "titan_back", new Box(-100, 50, -256, 100, 190, 256),
            "tide_back", new Box(-100, 38, -150, 100, 160, 120),
            // 雷翼の鳥は翼 x ±227・頭 z -124〜尾 132・y 110〜172 (StormBirdBuilder.BIRD)
            "storm_back", new Box(-232, 96, -136, 232, 176, 140));
    private static final int CHUNKS_PER_TICK = 6;

    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static int writing;
    private static boolean stopWhenDone;

    private StructureDump() {
    }

    // まだ読み込み中か書き出し中のものがあるか (台本の waitdump が見る)
    public static synchronized boolean busy() {
        return !JOBS.isEmpty() || writing > 0;
    }

    // ---------------------------------------------------------------- コマンド
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("dump")
                        .then(Commands.literal("preset").then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    PRESETS.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(StructureDump::preset)))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(StructureDump::custom)))))
                        // /skyabyss dev arrive <titan_back|tide_back|storm_back> : 巨神の背の到着地点へ、到着の向きで移る (建っていなければ建てる)
                        .then(Commands.literal("arrive").then(Commands.argument("back", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    PRESETS.keySet().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(StructureDump::arrive)))));
    }

    private static int preset(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "preset");
        String err = queuePreset(ctx.getSource().getServer(), name);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("dumping " + name + " -> " + outFile(ctx.getSource().getServer(), name)), false);
        return 1;
    }

    private static int arrive(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "back");
        net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ensureBack(ctx.getSource().getServer(), name);
        if (level == null) {
            ctx.getSource().sendFailure(Component.literal("unknown back " + name));
            return 0;
        }
        BlockPos a = name.equals("titan_back") ? jp.revati.skyabyss.titan.dungeon.TitanBackLayout.arrival()
                : name.equals("storm_back") ? jp.revati.skyabyss.titan.storm.back.StormBackLayout.ARRIVAL
                : jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL;
        float yaw = name.equals("titan_back") ? 180f : name.equals("storm_back") ? jp.revati.skyabyss.titan.storm.back.StormBackLayout.ARRIVAL_YAW
                : jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL_YAW;
        player.teleportTo(level, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, yaw, 0f);
        ctx.getSource().sendSuccess(() -> Component.literal("arrived at " + name + " " + a.toShortString() + " yaw " + yaw), false);
        return 1;
    }

    private static int custom(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Box box = Box.of(BlockPosArgument.getLoadedBlockPos(ctx, "from"), BlockPosArgument.getLoadedBlockPos(ctx, "to"));
        ServerLevel level = ctx.getSource().getLevel();
        queue(new Job(name, level, box));
        ctx.getSource().sendSuccess(() -> Component.literal("dumping " + name + " " + box + " -> " + outFile(level.getServer(), name)), false);
        return 1;
    }

    // 巨神の背を書き出す。まだ建っていなければ建てる。だめなら理由を返す
    public static String queuePreset(MinecraftServer server, String name) {
        Box box = PRESETS.get(name);
        if (box == null) {
            return "unknown preset " + name + " (" + PRESETS.keySet() + ")";
        }
        ServerLevel level = ensureBack(server, name);
        if (level == null) {
            return "no dimension for " + name;
        }
        Job job = new Job(name, level, box);
        // 調べるときの起点 (Python の --walk-from などで名前で使える)。立つ位置 (足の高さ)
        if (name.equals("titan_back")) {
            job.point("arrival", jp.revati.skyabyss.titan.dungeon.TitanBackLayout.arrival());
            job.point("shrine", new BlockPos(0, jp.revati.skyabyss.titan.dungeon.TitanBackLayout.shrineY() + 1, jp.revati.skyabyss.titan.dungeon.TitanBackLayout.SHRINE_Z));
            job.point("hub", jp.revati.skyabyss.titan.dungeon.TitanBackLayout.HUB.center());
        } else if (name.equals("storm_back")) {
            job.point("arrival", jp.revati.skyabyss.titan.storm.back.StormBackLayout.ARRIVAL);
            job.point("hub", jp.revati.skyabyss.titan.storm.back.StormBackLayout.HUB_CENTER.above());
            for (var rest : jp.revati.skyabyss.titan.storm.back.StormBackLayout.RESTS) {
                job.point("rest_" + rest.id(), rest.spawn());
            }
            job.point("west_stone", jp.revati.skyabyss.titan.storm.back.StormBackLayout.WEST_STONE.offset(1, 0, 0));
            job.point("east_stone", jp.revati.skyabyss.titan.storm.back.StormBackLayout.EAST_STONE.offset(-1, 0, 0));
            job.point("front_stone", jp.revati.skyabyss.titan.storm.back.StormBackLayout.FRONT_STONE.offset(1, 0, 0));
            job.point("eye_hall", jp.revati.skyabyss.titan.storm.back.StormBackLayout.EYE_HALL_CENTER.offset(1, 0, 6));
        } else {
            job.point("arrival", jp.revati.skyabyss.titan.tide.back.TideBackLayout.ARRIVAL);
            for (var rest : jp.revati.skyabyss.titan.tide.back.TideBackLayout.RESTS) {
                job.point("rest_" + rest.id(), rest.spawn());
            }
        }
        queue(job);
        return null;
    }

    // 巨神の背の次元 (titan_back / tide_back)。島がまだ建っていなければ建てる
    public static ServerLevel ensureBack(MinecraftServer server, String name) {
        ResourceKey<Level> dim = switch (name) {
            case "titan_back" -> TitanTravel.TITAN_BACK;
            case "tide_back" -> TideTravel.TIDE_BACK;
            case "storm_back" -> jp.revati.skyabyss.titan.storm.StormTravel.STORM_BACK;
            default -> null;
        };
        ServerLevel level = dim == null ? null : server.getLevel(dim);
        if (level == null) {
            return null;
        }
        if (name.equals("titan_back")) {
            TitanBackData data = TitanBackData.get(level);
            if (data.needsBuild()) {
                if (data.isBuilt()) {
                    data.resetProgress();
                }
                TitanBackBuilder.build(level, data);
            }
        } else if (name.equals("storm_back")) {
            jp.revati.skyabyss.titan.storm.back.StormBackBuilder.ensureBuilt(level);
        } else {
            TideBackBuilder.ensureBuilt(level);
        }
        return level;
    }

    private static synchronized void queue(Job job) {
        JOBS.add(job);
        SkyAbyss.LOGGER.info("[dump] queued {} {} in {}", job.name, job.box, job.level.dimension().location());
    }

    public static Path outFile(MinecraftServer server, String name) {
        String o = System.getenv("SKYABYSS_DUMP_OUT");
        Path dir = o != null && !o.isBlank() ? Path.of(o) : server.getServerDirectory().resolve("dumps");
        return dir.resolve(name + ".sbd").toAbsolutePath().normalize();
    }

    // ---------------------------------------------------------------- 環境変数で起動したとき
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        String env = System.getenv("SKYABYSS_DUMP");
        if (env == null || env.isBlank() || FMLEnvironment.production || !event.getServer().isDedicatedServer()) {
            return;
        }
        stopWhenDone = true;
        for (String name : env.split(",")) {
            String err = queuePreset(event.getServer(), name.strip());
            if (err != null) {
                SkyAbyss.LOGGER.error("[dump] {}", err);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (StructureDump.class) {
            JOBS.clear();
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Job job;
        synchronized (StructureDump.class) {
            job = JOBS.peek();
        }
        if (job == null) {
            if (stopWhenDone && !busy()) {
                stopWhenDone = false;
                SkyAbyss.LOGGER.info("[dump] DUMP DONE, stopping the server");
                event.getServer().halt(false);
            }
            return;
        }
        if (job.step(CHUNKS_PER_TICK)) {
            synchronized (StructureDump.class) {
                JOBS.poll();
                writing++;
            }
            Path file = outFile(event.getServer(), job.name);
            Util.ioPool().execute(() -> {
                try {
                    job.write(file);
                    SkyAbyss.LOGGER.info("[dump] wrote {} ({} x {} x {}, {} block kinds, {} signs)", file, job.box.sx(), job.box.sy(), job.box.sz(),
                            job.palette.size(), job.signs.size());
                } catch (IOException e) {
                    SkyAbyss.LOGGER.error("[dump] could not write {}", file, e);
                } finally {
                    synchronized (StructureDump.class) {
                        writing--;
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- 一つの書き出し
    private static final class Job {
        final String name;
        final ServerLevel level;
        final Box box;
        final short[] data;
        final List<BlockState> palette = new ArrayList<>();
        final Map<BlockState, Integer> ids = new IdentityHashMap<>();
        final JsonArray signs = new JsonArray();
        final JsonArray blockEntities = new JsonArray();
        final JsonObject points = new JsonObject();
        final int cx1, cz1, cx2, cz2;
        int next;

        Job(String name, ServerLevel level, Box box) {
            this.name = name;
            this.level = level;
            this.box = box;
            this.data = new short[box.sx() * box.sy() * box.sz()];
            cx1 = box.x1 >> 4;
            cz1 = box.z1 >> 4;
            cx2 = box.x2 >> 4;
            cz2 = box.z2 >> 4;
            id(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }

        void point(String key, BlockPos p) {
            points.add(key, pos(p));
        }

        int id(BlockState s) {
            Integer i = ids.get(s);
            if (i == null) {
                i = palette.size();
                if (i > 65535) {
                    throw new IllegalStateException("too many block states");
                }
                palette.add(s);
                ids.put(s, i);
            }
            return i;
        }

        // n チャンクぶん読む。全部読んだら true
        boolean step(int n) {
            int w = cx2 - cx1 + 1;
            int total = w * (cz2 - cz1 + 1);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int k = 0; k < n && next < total; k++, next++) {
                int cx = cx1 + next % w;
                int cz = cz1 + next / w;
                LevelChunk chunk = level.getChunk(cx, cz);
                int x0 = Math.max(box.x1, cx << 4), x1 = Math.min(box.x2, (cx << 4) + 15);
                int z0 = Math.max(box.z1, cz << 4), z1 = Math.min(box.z2, (cz << 4) + 15);
                for (int y = box.y1; y <= box.y2; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            BlockState s = chunk.getBlockState(p.set(x, y, z));
                            if (!s.isAir()) {
                                data[((y - box.y1) * box.sz() + (z - box.z1)) * box.sx() + (x - box.x1)] = (short) id(s);
                            }
                        }
                    }
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos bp = be.getBlockPos();
                    if (bp.getX() < box.x1 || bp.getX() > box.x2 || bp.getY() < box.y1 || bp.getY() > box.y2 || bp.getZ() < box.z1 || bp.getZ() > box.z2) {
                        continue;
                    }
                    JsonObject o = new JsonObject();
                    o.add("pos", pos(bp));
                    o.addProperty("type", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
                    if (be instanceof SignBlockEntity sign) {
                        o.addProperty("block", BlockStateParser.serialize(be.getBlockState()));
                        o.add("front", text(sign.getFrontText()));
                        o.add("back", text(sign.getBackText()));
                        signs.add(o);
                    } else {
                        blockEntities.add(o);
                    }
                }
            }
            return next >= total;
        }

        void write(Path file) throws IOException {
            JsonObject head = new JsonObject();
            head.addProperty("name", name);
            head.addProperty("dimension", level.dimension().location().toString());
            head.add("origin", pos(new BlockPos(box.x1, box.y1, box.z1)));
            JsonArray size = new JsonArray();
            size.add(box.sx());
            size.add(box.sy());
            size.add(box.sz());
            head.add("size", size);
            head.addProperty("order", "yzx");
            head.addProperty("gameTime", level.getGameTime());
            JsonArray pal = new JsonArray();
            for (BlockState s : palette) {
                pal.add(describe(s));
            }
            head.add("palette", pal);
            head.add("points", points);
            head.add("signs", signs);
            head.add("blockEntities", blockEntities);
            byte[] json = new GsonBuilder().disableHtmlEscaping().create().toJson(head).getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream raw = Files.newOutputStream(tmp);
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw, 1 << 16), 1 << 16))) {
                out.write("SKYDUMP1".getBytes(StandardCharsets.US_ASCII));
                out.writeInt(json.length);
                out.write(json);
                ByteBuffer buf = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);
                for (short v : data) {
                    if (!buf.hasRemaining()) {
                        out.write(buf.array(), 0, buf.position());
                        buf.clear();
                    }
                    buf.putShort(v);
                }
                out.write(buf.array(), 0, buf.position());
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // パレットの一つ: 名前・状態・地図の色と、歩ける/水かを Python で判断するための性質
    private static JsonObject describe(BlockState s) {
        JsonObject o = new JsonObject();
        o.addProperty("name", BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        o.addProperty("state", BlockStateParser.serialize(s));
        int color = 0;
        boolean collide = false;
        try {
            color = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            collide = !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException ignored) {
            collide = s.blocksMotion();
        }
        o.addProperty("color", String.format(Locale.ROOT, "#%06x", color));
        o.addProperty("air", s.isAir());
        o.addProperty("collide", collide);
        o.addProperty("blocksMotion", s.blocksMotion());
        o.addProperty("opaque", s.canOcclude());
        o.addProperty("climbable", s.is(BlockTags.CLIMBABLE));
        FluidState f = s.getFluidState();
        if (!f.isEmpty()) {
            o.addProperty("fluid", f.is(Fluids.WATER) || f.is(Fluids.FLOWING_WATER) ? "water"
                    : f.is(Fluids.LAVA) || f.is(Fluids.FLOWING_LAVA) ? "lava" : BuiltInRegistries.FLUID.getKey(f.getType()).toString());
            o.addProperty("source", f.isSource());
            o.addProperty("amount", f.getAmount());
        }
        return o;
    }

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static JsonArray text(SignText t) {
        JsonArray a = new JsonArray();
        for (Component c : t.getMessages(false)) {
            JsonObject line = new JsonObject();
            line.addProperty("text", c.getString());
            if (c.getContents() instanceof TranslatableContents tr) {
                line.addProperty("key", tr.getKey());
            }
            a.add(line);
        }
        return a;
    }
}
