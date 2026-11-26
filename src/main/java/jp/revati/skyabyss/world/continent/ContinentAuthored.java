package jp.revati.skyabyss.world.continent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 前もって組んだ土地 (2026-11-25。ユーザー「最初から完全に用意すれば、なじませられる」)。
// 土地ごとに data/skyabyss/continent/authored/<土地>/ に、256×256 の区画のファイル (<tx>_<tz>.bin) と索引 (index.json)。
// 区画の列は「y0 より下は石、y0 から (数, ブロック) の並び、上は空気」。組んでいない列 (0xFFFF) とチャンクは生成器のまま。
// 作るのは tools/gen/world/authored/ (前の世界の書き出しを元に、道具の側で地形・道・建物・木をなじませる)。
// 区画は必要になったときだけ読み、少しだけ覚えておく。既定で使う (2026-11-26)。SKYABYSS_AUTHORED=0 で切る (組む前の世界を書き出すとき)。
// 範囲の中で建った場所 (町・見どころ・章の跡地) と祠は、書き出したときの位置を索引に持つ。SiteJob・TitanShrine はそれを見て建て直さない。
public final class ContinentAuthored {
    public static final int TILE = 256;
    static final boolean ENABLED = !"0".equals(System.getenv("SKYABYSS_AUTHORED"));
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int NONE = 0xFFFF;

    public record Site(String kind, int index, BlockPos pos, int yaw) {
    }

    // 一つの土地 (索引)
    static final class Land {
        final String name;
        final Map<Long, Boolean> tiles = new HashMap<>();
        final List<Site> sites = new ArrayList<>();
        final Map<String, BlockPos> shrines = new HashMap<>();

        Land(String name) {
            this.name = name;
        }
    }

    // 読んだ区画
    static final class Tile {
        final int x0, z0, y0;
        final BlockState[] palette;
        final boolean[] ground;
        final int[] off; // 列ごとの runs の始まり (-1 = 組んでいない)
        final short[] runs; // (数, パレット) の並び
        final long[] chunkMask = new long[4]; // 16×16 チャンクの組んだ印

        Tile(int x0, int z0, int y0, BlockState[] palette, int[] off, short[] runs) {
            this.x0 = x0;
            this.z0 = z0;
            this.y0 = y0;
            this.palette = palette;
            this.off = off;
            this.runs = runs;
            this.ground = new boolean[palette.length];
            for (int i = 0; i < palette.length; i++) {
                ground[i] = isGround(palette[i]);
            }
            for (int cz = 0; cz < 16; cz++) {
                for (int cx = 0; cx < 16; cx++) {
                    if (off[(cz * 16) * TILE + cx * 16] >= 0) {
                        int b = cz * 16 + cx;
                        chunkMask[b >> 6] |= 1L << (b & 63);
                    }
                }
            }
        }

        boolean chunk(int cx, int cz) {
            int b = (cz & 15) * 16 + (cx & 15);
            return (chunkMask[b >> 6] >>> (b & 63) & 1L) != 0;
        }

        int col(int x, int z) {
            return off[(z - z0) * TILE + (x - x0)];
        }

        // 列の n 番目 (高さ y) のブロック。y0 より下は null (石)、上は空気
        @Nullable
        BlockState at(int x, int y, int z) {
            int o = col(x, z);
            if (y < y0) {
                return null;
            }
            int n = runs[o] & 0xFFFF;
            int yy = y0;
            for (int i = 0; i < n; i++) {
                int len = runs[o + 1 + i * 2] & 0xFFFF;
                if (y < yy + len) {
                    return palette[runs[o + 2 + i * 2] & 0xFFFF];
                }
                yy += len;
            }
            return AIR;
        }

        int top(int x, int z) {
            int o = col(x, z);
            int n = runs[o] & 0xFFFF;
            int yy = y0;
            int last = y0 - 1;
            for (int i = 0; i < n; i++) {
                int len = runs[o + 1 + i * 2] & 0xFFFF;
                if (!palette[runs[o + 2 + i * 2] & 0xFFFF].isAir()) {
                    last = yy + len - 1;
                }
                yy += len;
            }
            return last;
        }

        // 立てる地面の高さ。上に薄い岩 (道をまたぐ岩の門・張り出し、厚さ 12 まで) が浮いているときは、その下の地面
        int ground(int x, int z) {
            int o = col(x, z);
            int n = runs[o] & 0xFFFF;
            int yy = y0;
            int last = y0 - 1;
            int below = y0 - 1; // 4 以上の空きの下の地面
            int gapFrom = Integer.MIN_VALUE; // 今の地面の塊が始まった高さ (空き 4 以上のあと)
            int air = 0;
            for (int i = 0; i < n; i++) {
                int len = runs[o + 1 + i * 2] & 0xFFFF;
                int id = runs[o + 2 + i * 2] & 0xFFFF;
                if (ground[id]) {
                    if (air >= 4 && last >= y0) {
                        below = last;
                        gapFrom = yy;
                    }
                    last = yy + len - 1;
                    air = 0;
                } else if (palette[id].isAir()) {
                    air += len;
                }
                yy += len;
            }
            if (gapFrom != Integer.MIN_VALUE && last - gapFrom < 24) {
                return below;
            }
            return last;
        }

        // 列を下から積む (生成器用)。y0 より下は呼ぶ側が石で埋める
        void column(int x, int z, ColumnSink sink) {
            int o = col(x, z);
            int n = runs[o] & 0xFFFF;
            int yy = y0;
            for (int i = 0; i < n; i++) {
                int len = runs[o + 1 + i * 2] & 0xFFFF;
                BlockState s = palette[runs[o + 2 + i * 2] & 0xFFFF];
                if (!s.isAir()) {
                    sink.put(yy, yy + len - 1, s);
                }
                yy += len;
            }
        }
    }

    public interface ColumnSink {
        void put(int y0, int y1, BlockState state);
    }

    // 自然の地面と道の敷石だけ (屋根・木・灯りは地面にしない)。ContinentMap.surface の代わりに使う
    static boolean isGround(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.MOSS_BLOCK)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CALCITE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.DIRT_PATH) || s.is(Blocks.PACKED_MUD) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS)
                || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.BEDROCK)
                // 土地ごとの岩 (潮の浜の砂岩・砂漠の素焼き・灰の荒野の黒い石と玄武岩)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.SMOOTH_SANDSTONE) || s.is(Blocks.CUT_SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(net.minecraft.tags.BlockTags.TERRACOTTA) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.COARSE_DIRT)
                // 敷石 (都の広場・町の道)
                || s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.POLISHED_DIORITE) || s.is(Blocks.POLISHED_GRANITE) || s.is(Blocks.SMOOTH_STONE)
                || s.is(Blocks.POLISHED_BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || s.is(Blocks.MUD_BRICKS)
                || s.is(Blocks.POLISHED_DEEPSLATE) || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.DEEPSLATE_TILES)
                // 岩鯨の土地の道に敷いた骨 (骨の上を歩くので地面。数えないと道に 2 段の段があるように見えた)
                || s.is(Blocks.BONE_BLOCK);
    }

    private static volatile List<Land> lands;
    private static final Map<Long, Tile> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Tile> e) {
            return size() > 12;
        }
    };
    private static final Map<Long, List<Land>> TILE_LAND = new HashMap<>();
    // 土地の名前 → 地域 (ContinentMap の番号)。二つの土地にまたがる区画は、列ごとにその地域の土地の列を使う
    private static final Map<String, Integer> LAND_REGION = Map.of("meadow", ContinentMap.MEADOW, "outskirts", ContinentMap.OUTSKIRTS,
            "whale", ContinentMap.WHALE, "tide", ContinentMap.TIDE, "storm", ContinentMap.STORM, "ember", ContinentMap.EMBER);

    static long key(int tx, int tz) {
        return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    static List<Land> lands() {
        if (!ENABLED) {
            return List.of();
        }
        List<Land> l = lands;
        if (l == null) {
            synchronized (ContinentAuthored.class) {
                if (lands == null) {
                    lands = load();
                }
                l = lands;
            }
        }
        return l;
    }

    @Nullable
    static Tile tile(int x, int z) {
        if (lands().isEmpty()) {
            return null;
        }
        int tx = Math.floorDiv(x, TILE);
        int tz = Math.floorDiv(z, TILE);
        long k = key(tx, tz);
        List<Land> owners = TILE_LAND.get(k);
        if (owners == null) {
            return null;
        }
        synchronized (CACHE) {
            Tile t = CACHE.get(k);
            if (t == null) {
                List<Tile> parts = new ArrayList<>();
                List<Integer> regions = new ArrayList<>();
                for (Land land : owners) {
                    Tile p = readTile(land.name, tx, tz);
                    if (p != null) {
                        parts.add(p);
                        regions.add(LAND_REGION.getOrDefault(land.name, -1));
                    }
                }
                if (parts.isEmpty()) {
                    return null;
                }
                t = parts.size() == 1 ? parts.get(0) : merge(parts, regions);
                CACHE.put(k, t);
            }
            return t;
        }
    }

    // ---------------------------------------------------------------- 生成器と高さ
    public static boolean chunk(int cx, int cz) {
        Tile t = tile(cx << 4, cz << 4);
        return t != null && t.chunk(cx, cz);
    }

    @Nullable
    static Tile chunkTile(int cx, int cz) {
        Tile t = tile(cx << 4, cz << 4);
        return t != null && t.chunk(cx, cz) ? t : null;
    }

    // 組んだ列か
    public static boolean covers(int x, int z) {
        Tile t = tile(x, z);
        return t != null && t.col(x, z) >= 0;
    }

    // 組んだ列の地面の上面 (木・草花・屋根を除く)。組んでいなければ Integer.MIN_VALUE
    public static int ground(int x, int z) {
        Tile t = tile(x, z);
        return t == null || t.col(x, z) < 0 ? Integer.MIN_VALUE : t.ground(x, z);
    }

    public static int top(int x, int z) {
        Tile t = tile(x, z);
        return t == null || t.col(x, z) < 0 ? Integer.MIN_VALUE : t.top(x, z);
    }

    // ---------------------------------------------------------------- 建った場所と祠
    // 書き出した世界に建っていた同じ場所 (種類と番号、x・z が 200 以内)。無ければ null
    @Nullable
    public static Site site(String kind, int index, BlockPos planned) {
        if (planned == null) {
            return null;
        }
        for (Land land : lands()) {
            for (Site s : land.sites) {
                if (s.kind.equals(kind) && s.index == index
                        && Math.abs(s.pos.getX() - planned.getX()) <= 200 && Math.abs(s.pos.getZ() - planned.getZ()) <= 200) {
                    return s;
                }
            }
        }
        return null;
    }

    // 書き出した世界で建った見どころ・隠しが (x, z) から水平 r 以内にあるか (自己テストで、建物の岩の屋根を自然の張り出しと数えない)
    public static boolean nearBuiltPoi(int x, int z, int r) {
        for (Land land : lands()) {
            for (Site s : land.sites) {
                if ((s.kind.equals("poi") || s.kind.equals("secret"))
                        && Math.abs(s.pos.getX() - x) <= r && Math.abs(s.pos.getZ() - z) <= r) {
                    return true;
                }
            }
        }
        return false;
    }

    // 範囲 (x0, z0)〜(x1, z1) の列が全部組んであるか (四隅と中を見る)
    public static boolean coversRect(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x += Math.max(1, (x1 - x0) / 4)) {
            for (int z = z0; z <= z1; z += Math.max(1, (z1 - z0) / 4)) {
                if (!covers(x, z)) {
                    return false;
                }
            }
        }
        return covers(x1, z1);
    }

    @Nullable
    public static BlockPos shrine(String titan) {
        for (Land land : lands()) {
            BlockPos p = land.shrines.get(titan);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 読む
    private static List<Land> load() {
        List<Land> out = new ArrayList<>();
        try (InputStream in = ContinentAuthored.class.getResourceAsStream("/data/skyabyss/continent/authored/index.json")) {
            if (in == null) {
                return out;
            }
            for (JsonElement n : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                Land land = readLand(n.getAsString());
                if (land != null) {
                    out.add(land);
                }
            }
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("authored lands: cannot read the index", e);
        }
        return out;
    }

    @Nullable
    private static Land readLand(String name) {
        String path = "/data/skyabyss/continent/authored/" + name + "/index.json";
        try (InputStream in = ContinentAuthored.class.getResourceAsStream(path)) {
            if (in == null) {
                SkyAbyss.LOGGER.error("authored lands: missing {}", path);
                return null;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Land land = new Land(name);
            for (JsonElement t : o.getAsJsonArray("tiles")) {
                JsonArray a = t.getAsJsonArray();
                long k = key(a.get(0).getAsInt(), a.get(1).getAsInt());
                land.tiles.put(k, true);
                TILE_LAND.computeIfAbsent(k, q -> new ArrayList<>()).add(land);
            }
            JsonObject sites = o.getAsJsonObject("sites");
            if (sites != null) {
                for (JsonElement e : sites.getAsJsonArray("entries")) {
                    JsonObject so = e.getAsJsonObject();
                    JsonArray p = so.getAsJsonArray("pos");
                    land.sites.add(new Site(so.get("kind").getAsString(), so.get("index").getAsInt(),
                            new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()), so.get("yaw").getAsInt()));
                }
                if (sites.has("shrines")) {
                    for (var en : sites.getAsJsonObject("shrines").entrySet()) {
                        JsonArray p = en.getValue().getAsJsonArray();
                        land.shrines.put(en.getKey(), new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt()));
                    }
                }
            }
            SkyAbyss.LOGGER.info("authored land {}: {} tiles, {} sites", name, land.tiles.size(), land.sites.size());
            return land;
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("authored lands: cannot read {}", path, e);
            return null;
        }
    }

    // 二つ以上の土地の同じ区画を一枚に: 列ごとに、その列の地域の土地の列 (なければ組んだほうの列)
    static Tile merge(List<Tile> parts, List<Integer> regions) {
        Tile a = parts.get(0);
        for (Tile p : parts) {
            if (p.y0 != a.y0) {
                SkyAbyss.LOGGER.warn("authored lands: tile {},{} has different bottoms, using one land", a.x0, a.z0);
                return a;
            }
        }
        List<BlockState> pal = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        int[][] remap = new int[parts.size()][];
        int total = 0;
        for (int i = 0; i < parts.size(); i++) {
            Tile p = parts.get(i);
            remap[i] = new int[p.palette.length];
            for (int j = 0; j < p.palette.length; j++) {
                remap[i][j] = index.computeIfAbsent(p.palette[j], s -> {
                    pal.add(s);
                    return pal.size() - 1;
                });
            }
            total = Math.max(total, 0) + p.runs.length;
        }
        ContinentMap map = ContinentMap.get();
        int[] off = new int[TILE * TILE];
        short[] runs = new short[total];
        int pos = 0;
        for (int c = 0; c < TILE * TILE; c++) {
            int region = map.region(a.x0 + c % TILE, a.z0 + c / TILE);
            int best = -1;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).off[c] >= 0) {
                    if (best < 0) {
                        best = i;
                    }
                    if (regions.get(i) == region) {
                        best = i;
                        break;
                    }
                }
            }
            if (best < 0) {
                off[c] = -1;
                continue;
            }
            Tile p = parts.get(best);
            int o = p.off[c];
            int n = p.runs[o] & 0xFFFF;
            off[c] = pos;
            runs[pos++] = (short) n;
            for (int i = 0; i < n; i++) {
                runs[pos++] = p.runs[o + 1 + i * 2];
                runs[pos++] = (short) remap[best][p.runs[o + 2 + i * 2] & 0xFFFF];
            }
        }
        return new Tile(a.x0, a.z0, a.y0, pal.toArray(new BlockState[0]), off, java.util.Arrays.copyOf(runs, pos));
    }

    @Nullable
    private static Tile readTile(String land, int tx, int tz) {
        String path = "/data/skyabyss/continent/authored/" + land + "/" + tx + "_" + tz + ".bin";
        try (InputStream raw = ContinentAuthored.class.getResourceAsStream(path)) {
            if (raw == null) {
                return null;
            }
            DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16));
            byte[] magic = new byte[8];
            in.readFully(magic);
            if (!"SKYTILE1".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IOException("bad header in " + path);
            }
            byte[] json = new byte[in.readInt()];
            in.readFully(json);
            JsonObject head = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            int y0 = head.get("y0").getAsInt();
            JsonArray pal = head.getAsJsonArray("palette");
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < palette.length; i++) {
                String st = pal.get(i).getAsString();
                try {
                    palette[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), st, false).blockState();
                } catch (Exception ex) {
                    SkyAbyss.LOGGER.warn("authored land {}: unknown block {}", land, st);
                    palette[i] = AIR;
                }
            }
            int total = in.readInt(); // runs の short の数
            short[] runs = new short[total];
            int[] off = new int[TILE * TILE];
            int pos = 0;
            for (int c = 0; c < TILE * TILE; c++) {
                int n = in.readUnsignedShort();
                if (n == NONE) {
                    off[c] = -1;
                    continue;
                }
                off[c] = pos;
                runs[pos++] = (short) n;
                for (int i = 0; i < n * 2; i++) {
                    runs[pos++] = (short) in.readUnsignedShort();
                }
            }
            return new Tile(tx * TILE, tz * TILE, y0, palette, off, runs);
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("authored lands: cannot read {}", path, e);
            return null;
        }
    }

    private ContinentAuthored() {
    }
}
