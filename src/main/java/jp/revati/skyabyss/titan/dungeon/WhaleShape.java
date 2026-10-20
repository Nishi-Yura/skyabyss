package jp.revati.skyabyss.titan.dungeon;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

// 岩鯨の背の島そのもの: 空を泳ぐ岩鯨のモデル (採用案 B) と同じ形・同じ色の、ブロックでできた鯨 (02 の「巨神の形の背中（作り直し）」)。
// 形は tools/gen_titan_back_whale.py がモデルの格子から作った data/skyabyss/titan_back/whale.bin を読む
// (ゲームの岩鯨の 2.8 倍。頭は北 -Z、体の軸は x = -3、錨石は左のわき腹 = 西)。試練の部屋は、置いたあとで各 Builder が中を掘る。
//
// whale.bin の形式は潮殻の turtle.bin と同じ (gzip): "TBT1"、原点 x0 y0 z0、大きさ nx ny nz (int)、ブロックの名前の表、
// x ごと z ごとの柱: 体 (頭・顎・胴・尾) のいちばん上の段 (short、無ければ -1)、区間の数、区間 (始めの段・長さ・表の番号)
public final class WhaleShape {
    private static Data data;

    private WhaleShape() {
    }

    private record Data(int x0, int y0, int z0, int nx, int ny, int nz, BlockState[] palette, short[] body, short[][] runs) {
    }

    private static Data data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private static Data load() {
        try (InputStream raw = WhaleShape.class.getResourceAsStream("/data/skyabyss/titan_back/whale.bin")) {
            if (raw == null) {
                throw new IllegalStateException("whale.bin is missing");
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("TBT1")) {
                throw new IOException("bad whale.bin");
            }
            int x0 = in.readInt();
            int y0 = in.readInt();
            int z0 = in.readInt();
            int nx = in.readInt();
            int ny = in.readInt();
            int nz = in.readInt();
            int n = in.readShort();
            BlockState[] palette = new BlockState[n];
            for (int i = 0; i < n; i++) {
                byte[] b = new byte[in.readShort()];
                in.readFully(b);
                String name = new String(b, java.nio.charset.StandardCharsets.UTF_8);
                palette[i] = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name)).defaultBlockState();
                if (!name.equals("air") && palette[i].isAir()) {
                    SkyAbyss.LOGGER.warn("stone whale back: unknown block {}", name);
                }
            }
            short[] body = new short[nx * nz];
            short[][] runs = new short[nx * nz][];
            for (int a = 0; a < nx; a++) {
                for (int c = 0; c < nz; c++) {
                    int i = a * nz + c;
                    body[i] = in.readShort();
                    int segs = in.readShort();
                    short[] r = new short[segs * 3];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = in.readShort();
                    }
                    runs[i] = r;
                }
            }
            return new Data(x0, y0, z0, nx, ny, nz, palette, body, runs);
        } catch (IOException e) {
            throw new IllegalStateException("could not read whale.bin", e);
        }
    }

    public static int minX() {
        return data().x0;
    }

    public static int maxX() {
        return data().x0 + data().nx - 1;
    }

    public static int minZ() {
        return data().z0;
    }

    public static int maxZ() {
        return data().z0 + data().nz - 1;
    }

    public static int minY() {
        return data().y0;
    }

    public static int maxY() {
        return data().y0 + data().ny - 1;
    }

    private static int index(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return -1;
        }
        return a * d.nz + c;
    }

    // 体 (頭・顎・胴・尾) のいちばん上のブロックの y。体の無い柱は Integer.MIN_VALUE。背の遺跡・背骨・ひれは含まない
    public static int bodyTop(int x, int z) {
        int i = index(x, z);
        if (i < 0 || data().body[i] < 0) {
            return Integer.MIN_VALUE;
        }
        return data().y0 + data().body[i];
    }

    // 鯨の何か (体・遺跡・背骨・ひれ) のいちばん上 / いちばん下のブロックの y。無ければ Integer.MIN_VALUE
    public static int top(int x, int z) {
        int i = index(x, z);
        if (i < 0 || data().runs[i].length == 0) {
            return Integer.MIN_VALUE;
        }
        short[] r = data().runs[i];
        return data().y0 + r[r.length - 3] + r[r.length - 2] - 1;
    }

    public static int bottom(int x, int z) {
        int i = index(x, z);
        if (i < 0 || data().runs[i].length == 0) {
            return Integer.MIN_VALUE;
        }
        return data().y0 + data().runs[i][0];
    }

    // 鯨のブロック (無ければ null)
    public static BlockState at(int x, int y, int z) {
        int i = index(x, z);
        int j = y - data().y0;
        if (i < 0 || j < 0 || j >= data().ny) {
            return null;
        }
        short[] r = data().runs[i];
        for (int k = 0; k < r.length; k += 3) {
            if (j >= r[k] && j < r[k] + r[k + 1]) {
                return data().palette[r[k + 2]];
            }
        }
        return null;
    }

    // 鯨を置く。範囲の中で、鯨のブロックでない所は空に戻す (前の版の島や、掘った部屋を消す)。
    // 変わるブロックだけを置く (同じなら触らない)。置いたブロックの数を返す
    // 版を上げる建て直しで、前の版の部屋・小部屋・足場が残らないようにする (版 27)。体の型 (whale.bin) の範囲の外 (鯨の外周 RX/RZ の中) と、
    // 型の範囲の上下の外を、体を置く前に空気にする。型の範囲の中は体の置き直しが全部の柱を書き直す。空でない区画だけを見るので軽い
    static jp.revati.skyabyss.titan.BackBuildJobs.ColumnJob staleJob(BuildKit kit) {
        Data d = data();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int rx = TitanBackLayout.RX;
        int rz = TitanBackLayout.RZ;
        return new jp.revati.skyabyss.titan.BackBuildJobs.ColumnJob(kit.level(), Math.min(-rx, d.x0), Math.max(rx, d.x0 + d.nx - 1), Math.min(-rz, d.z0), Math.max(rz, d.z0 + d.nz - 1)) {
            @Override
            protected void beginChunk(net.minecraft.server.level.ServerLevel lv, LevelChunk chunk) {
                for (BlockPos p : java.util.List.copyOf(chunk.getBlockEntitiesPos())) {
                    chunk.removeBlockEntity(p);
                }
            }

            @Override
            protected int column(net.minecraft.server.level.ServerLevel lv, LevelChunk chunk, int x, int z) {
                boolean inside = index(x, z) >= 0;
                int placed = 0;
                for (int si = 0; si < chunk.getSectionsCount(); si++) {
                    if (chunk.getSection(si).hasOnlyAir()) {
                        continue;
                    }
                    int wy0 = chunk.getSectionYFromSectionIndex(si) << 4;
                    for (int wy = wy0; wy < wy0 + 16; wy++) {
                        if (inside && wy >= d.y0 && wy < d.y0 + d.ny) {
                            continue;
                        }
                        m.set(x, wy, z);
                        if (!chunk.getBlockState(m).isAir()) {
                            lv.setBlock(m, air, BuildKit.UPDATE);
                            placed++;
                        }
                    }
                }
                return placed;
            }
        };
    }

    // 古い島を空にしてから体を置く仕事 (一続きの Job)
    static jp.revati.skyabyss.titan.BackBuildJobs.Job jobWithClear(BuildKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Job clear = staleJob(kit);
        jp.revati.skyabyss.titan.BackBuildJobs.Job body = job(kit);
        return new jp.revati.skyabyss.titan.BackBuildJobs.Job() {
            private boolean cleared;

            @Override
            public boolean step(long deadlineNanos) {
                if (!cleared) {
                    cleared = clear.step(deadlineNanos);
                    return false;
                }
                return body.step(deadlineNanos);
            }

            @Override
            public int placed() {
                return clear.placed() + body.placed();
            }
        };
    }

    static int place(BuildKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Job job = jobWithClear(kit);
        while (!job.step(Long.MAX_VALUE)) {
            // 空にする仕事の次の一歩へ
        }
        return job.placed();
    }

    static int placeBodyOnly(BuildKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Job job = job(kit);
        job.step(Long.MAX_VALUE);
        return job.placed();
    }

    // 同じ置き方を、1 列ずつ何 tick かに分けて進められる形で返す (BackBuildJobs)。
    // 深い内側 (長い同じブロックの区間。外から 5 ブロックより奥) は、LevelChunk.setBlockState (高さ地図・明るさ・更新) を通さずに
    // 区画 (16×16×16) へ直接書く。区画の全部が同じ石なら、区画ごと入れ替える (26 の 4.2 の「詰まった区画の一括」)。
    // 殻と、空気に面するブロックは今までどおり (明るさの更新が要るため)
    static jp.revati.skyabyss.titan.BackBuildJobs.ColumnJob job(BuildKit kit) {
        Data d = data();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] column = new BlockState[d.ny];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean[] inner = new boolean[d.palette.length];
        for (int i = 0; i < inner.length; i++) {
            inner[i] = d.palette[i].is(Blocks.TUFF) || d.palette[i].is(Blocks.DEEPSLATE);
        }
        return new jp.revati.skyabyss.titan.BackBuildJobs.ColumnJob(kit.level(), d.x0, d.x0 + d.nx - 1, d.z0, d.z0 + d.nz - 1) {
            // このチャンクで直接書いた区画 (番号の集合)
            private long touched;

            @Override
            protected void beginChunk(net.minecraft.server.level.ServerLevel lv, LevelChunk chunk) {
                touched = 0;
                // 前の島のブロックの持ち物 (看板・スポナー・焚き火) は、体を置き直すので捨てる (あとで部屋を建てるときに作り直す)
                for (BlockPos p : java.util.List.copyOf(chunk.getBlockEntitiesPos())) {
                    chunk.removeBlockEntity(p);
                }
                int cx = chunk.getPos().x << 4;
                int cz = chunk.getPos().z << 4;
                for (int si = 0; si < chunk.getSectionsCount(); si++) {
                    int wy0 = chunk.getSectionYFromSectionIndex(si) << 4;
                    int j0 = wy0 - d.y0;
                    if (j0 < 0 || j0 + 16 > d.ny) {
                        continue;
                    }
                    int pal = -2;
                    boolean ok = true;
                    for (int lx = 0; lx < 16 && ok; lx++) {
                        for (int lz = 0; lz < 16 && ok; lz++) {
                            int i = index(cx + lx, cz + lz);
                            if (i < 0) {
                                ok = false;
                                break;
                            }
                            short[] r = d.runs[i];
                            int found = -1;
                            for (int k = 0; k < r.length; k += 3) {
                                if (r[k] <= j0 && r[k] + r[k + 1] >= j0 + 16) {
                                    found = r[k + 2];
                                    break;
                                }
                            }
                            if (found < 0 || !inner[found] || (pal != -2 && pal != found)) {
                                ok = false;
                            } else {
                                pal = found;
                            }
                        }
                    }
                    if (ok && pal >= 0) {
                        net.minecraft.world.level.chunk.LevelChunkSection old = chunk.getSection(si);
                        var states = new net.minecraft.world.level.chunk.PalettedContainer<BlockState>(
                                net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY, d.palette[pal],
                                net.minecraft.world.level.chunk.PalettedContainer.Strategy.SECTION_STATES);
                        chunk.getSections()[si] = new net.minecraft.world.level.chunk.LevelChunkSection(states, old.getBiomes());
                        touched |= 1L << si;
                        addPlaced(4096);
                    }
                }
            }

            @Override
            protected void endChunk(net.minecraft.server.level.ServerLevel lv, LevelChunk chunk) {
                if (touched == 0) {
                    return;
                }
                for (int si = 0; si < chunk.getSectionsCount(); si++) {
                    if ((touched & (1L << si)) != 0) {
                        lv.getChunkSource().getLightEngine().updateSectionStatus(
                                net.minecraft.core.SectionPos.of(chunk.getPos().x, chunk.getSectionYFromSectionIndex(si), chunk.getPos().z), false);
                    }
                }
                chunk.setUnsaved(true);
                touched = 0;
            }

            @Override
            protected int column(net.minecraft.server.level.ServerLevel lv, LevelChunk chunk, int x, int z) {
                int i = index(x, z);
                if (i < 0) {
                    return 0;
                }
                java.util.Arrays.fill(column, air);
                short[] r = d.runs[i];
                int placed = 0;
                for (int k = 0; k < r.length; k += 3) {
                    BlockState s = d.palette[r[k + 2]];
                    boolean direct = inner[r[k + 2]] && r[k + 1] >= 8;
                    for (int j = r[k]; j < r[k] + r[k + 1]; j++) {
                        column[j] = s;
                        if (direct) {
                            int wy = d.y0 + j;
                            int si = chunk.getSectionIndex(wy);
                            net.minecraft.world.level.chunk.LevelChunkSection sec = chunk.getSection(si);
                            if (sec.getBlockState(x & 15, wy & 15, z & 15) != s) {
                                sec.setBlockState(x & 15, wy & 15, z & 15, s, false);
                                touched |= 1L << si;
                                placed++;
                            }
                        }
                    }
                }
                for (int j = 0; j < d.ny; j++) {
                    m.set(x, d.y0 + j, z);
                    if (chunk.getBlockState(m) != column[j]) {
                        lv.setBlock(m, column[j], BuildKit.UPDATE);
                        placed++;
                    }
                }
                return placed;
            }
        };
    }
}
