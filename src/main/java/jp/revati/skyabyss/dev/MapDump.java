package jp.revati.skyabyss.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 旅の地図の絵のための書き出し (開発時だけ。2026-11-26 ユーザー「ブロックをそのまま投影している地図」)。
// 範囲の列ごとに、真上から見えるブロック・その高さ・水の深さ・水の底のブロック・バイオームを書く。
// 読むのは tools/gen/world/map/render_map.py (ブロックの絵の平均の色と高さの陰で 1 ブロック 1 点の絵にする)。
//
//   /skyabyss dev mapdump <名前> <x0> <z0> <x1> <z1>   : 実行した次元の範囲。チャンクが無ければ作る
//
// 書き出し先: StructureDump と同じ dumps/<名前>.smap。台本の waitdump はこちらも待つ。
// 形式 (gzip): "SKYMAP01" / ヘッダの長さ (4 バイト、big endian) / ヘッダの JSON (UTF-8) /
//   見えるブロックの番号 (uint16 LE、z・x の順) / 高さ (int16 LE) / 水の深さ (uint8) / 底のブロックの番号 (uint16 LE、深さ 0 なら 0) / バイオームの番号 (uint8)。
// ヘッダ: 名前・x0・z0・幅・奥行き・ブロック (名前)・バイオーム (名前・草・葉・水の色)
// 背の低い草花 (置き換えられる・液体でない・雪でない) と地図の色の無いブロックは飛ばして、その下を見る。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MapDump {
    private static final int CHUNKS_PER_TICK = 4;
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static int writing;

    private MapDump() {
    }

    public static synchronized boolean busy() {
        return !JOBS.isEmpty() || writing > 0;
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").then(Commands.literal("mapdump")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("x0", IntegerArgumentType.integer())
                                        .then(Commands.argument("z0", IntegerArgumentType.integer())
                                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z1", IntegerArgumentType.integer())
                                                                .executes(MapDump::run)))))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        int x0 = IntegerArgumentType.getInteger(ctx, "x0"), z0 = IntegerArgumentType.getInteger(ctx, "z0");
        int x1 = IntegerArgumentType.getInteger(ctx, "x1"), z1 = IntegerArgumentType.getInteger(ctx, "z1");
        Job job = new Job(name, ctx.getSource().getLevel(), Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        synchronized (MapDump.class) {
            JOBS.add(job);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("mapdump " + name + " queued (" + job.w + " x " + job.h + ")"), false);
        return 1;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        synchronized (MapDump.class) {
            JOBS.clear();
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Job job;
        synchronized (MapDump.class) {
            job = JOBS.peek();
        }
        if (job == null || !job.step(CHUNKS_PER_TICK)) {
            return;
        }
        synchronized (MapDump.class) {
            JOBS.poll();
            writing++;
        }
        Path file = StructureDump.outFile(event.getServer(), job.name).resolveSibling(job.name + ".smap");
        Util.ioPool().execute(() -> {
            try {
                job.write(file);
                SkyAbyss.LOGGER.info("[mapdump] wrote {} ({} x {}, {} blocks, {} biomes)", file, job.w, job.h, job.blocks.size(), job.biomes.size());
            } catch (IOException e) {
                SkyAbyss.LOGGER.error("[mapdump] could not write {}", file, e);
            } finally {
                synchronized (MapDump.class) {
                    writing--;
                }
            }
        });
    }

    private static final class Job {
        final String name;
        final ServerLevel level;
        final int x0, z0, w, h, cx1, cz1, cx2, cz2;
        final short[] top, floor;
        final short[] y;
        final byte[] depth, biome;
        final List<String> blocks = new ArrayList<>();
        final Map<String, Integer> blockIds = new HashMap<>();
        final List<Holder<Biome>> biomes = new ArrayList<>();
        final Map<Holder<Biome>, Integer> biomeIds = new HashMap<>();
        int next;

        Job(String name, ServerLevel level, int x0, int z0, int x1, int z1) {
            this.name = name;
            this.level = level;
            this.x0 = x0;
            this.z0 = z0;
            this.w = x1 - x0 + 1;
            this.h = z1 - z0 + 1;
            int n = w * h;
            top = new short[n];
            floor = new short[n];
            y = new short[n];
            depth = new byte[n];
            biome = new byte[n];
            cx1 = x0 >> 4;
            cz1 = z0 >> 4;
            cx2 = x1 >> 4;
            cz2 = z1 >> 4;
            blockId(Blocks.AIR.defaultBlockState());
        }

        int blockId(BlockState s) {
            String key = BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
            return blockIds.computeIfAbsent(key, k -> {
                blocks.add(k);
                return blocks.size() - 1;
            });
        }

        int biomeId(Holder<Biome> b) {
            return biomeIds.computeIfAbsent(b, k -> {
                if (biomes.size() >= 255) {
                    return 0;
                }
                biomes.add(k);
                return biomes.size() - 1;
            });
        }

        // 真上から見て飛ばすブロック (背の低い草花・地図の色の無いもの)
        static boolean skip(BlockState s) {
            if (s.isAir()) {
                return true;
            }
            if (!s.getFluidState().isEmpty()) {
                return false;
            }
            if (s.is(Blocks.SNOW)) {
                return false;
            }
            return s.canBeReplaced() || s.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.NONE;
        }

        static boolean water(BlockState s) {
            return s.getFluidState().is(FluidTags.WATER) && (s.is(Blocks.WATER) || s.canBeReplaced() || s.is(Blocks.BUBBLE_COLUMN)
                    || s.is(Blocks.KELP_PLANT) || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS));
        }

        boolean step(int n) {
            int cw = cx2 - cx1 + 1;
            int total = cw * (cz2 - cz1 + 1);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int minY = level.getMinBuildHeight();
            for (int k = 0; k < n && next < total; k++, next++) {
                int cx = cx1 + next % cw;
                int cz = cz1 + next / cw;
                LevelChunk chunk = level.getChunk(cx, cz);
                int ax = Math.max(x0, cx << 4), bx = Math.min(x0 + w - 1, (cx << 4) + 15);
                int az = Math.max(z0, cz << 4), bz = Math.min(z0 + h - 1, (cz << 4) + 15);
                for (int z = az; z <= bz; z++) {
                    for (int x = ax; x <= bx; x++) {
                        int i = (z - z0) * w + (x - x0);
                        int yy = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        BlockState s = chunk.getBlockState(p.set(x, yy, z));
                        while (yy > minY && skip(s)) {
                            s = chunk.getBlockState(p.set(x, --yy, z));
                        }
                        top[i] = (short) blockId(s);
                        y[i] = (short) yy;
                        if (water(s)) {
                            int d = 0;
                            BlockState f = s;
                            while (yy - d > minY && water(f)) {
                                d++;
                                f = chunk.getBlockState(p.set(x, yy - d, z));
                            }
                            depth[i] = (byte) Math.min(255, d);
                            floor[i] = (short) blockId(f);
                        }
                        biome[i] = (byte) biomeId(level.getBiome(p.set(x, yy + 1, z)));
                    }
                }
            }
            if (next % 2000 < n) {
                SkyAbyss.LOGGER.info("[mapdump] {} {}/{} chunks", name, next, total);
            }
            return next >= total;
        }

        void write(Path file) throws IOException {
            JsonObject head = new JsonObject();
            head.addProperty("name", name);
            head.addProperty("x0", x0);
            head.addProperty("z0", z0);
            head.addProperty("w", w);
            head.addProperty("h", h);
            JsonArray bl = new JsonArray();
            blocks.forEach(bl::add);
            head.add("blocks", bl);
            JsonArray bi = new JsonArray();
            for (Holder<Biome> b : biomes) {
                JsonObject o = new JsonObject();
                o.addProperty("name", b.unwrapKey().map(k -> k.location().toString()).orElse("?"));
                o.addProperty("grass", b.value().getGrassColor(0, 0));
                o.addProperty("foliage", b.value().getFoliageColor());
                o.addProperty("water", b.value().getWaterColor());
                bi.add(o);
            }
            head.add("biomes", bi);
            byte[] hb = head.toString().getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(file.getParent());
            try (OutputStream raw = Files.newOutputStream(file);
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw, 1 << 16), 1 << 16))) {
                out.write("SKYMAP01".getBytes(StandardCharsets.US_ASCII));
                out.writeInt(hb.length);
                out.write(hb);
                le16(out, top);
                le16(out, y);
                out.write(depth);
                le16(out, floor);
                out.write(biome);
            }
        }

        static void le16(DataOutputStream out, short[] a) throws IOException {
            byte[] b = new byte[a.length * 2];
            for (int i = 0; i < a.length; i++) {
                b[i * 2] = (byte) a[i];
                b[i * 2 + 1] = (byte) (a[i] >> 8);
            }
            out.write(b);
        }
    }
}
