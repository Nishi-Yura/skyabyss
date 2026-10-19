package jp.revati.skyabyss.titan;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;
import java.util.zip.GZIPInputStream;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

// 巨神の形の背中の体 (ブロックでできた巨神本体) の読み書きの共通の部品 (13 の 10.2節。元は潮殻の TideTurtle)。
// 形はモデルの格子から tools/gen_*_back_*.py が作った柱ごとの区間の表 (.bin) を読む。潮殻は turtle.bin、余燼は body.bin。
//
// .bin (gzip): "TBT1"、原点 x0 y0 z0、大きさ nx ny nz (int)、ブロックの名前の表 (short 個数 + UTF。名前はバニラの名前空間)、
// x ごと z ごとの柱: 骨 (甲羅・鱗板) のいちばん上の段 (short、無ければ -1)、続く区間の数 (short)、区間 (始めの段・長さ・表の番号、short)
//
// 読むのは最初に使ったときに一度だけ (重い。余燼の体は 150 万ブロック前後)。巨神ごとに一つ作って static に持つ
public final class TitanBodyBlocks {
    private final String resource;
    private Data data;

    public TitanBodyBlocks(String resource) {
        this.resource = resource;
    }

    private record Data(int x0, int y0, int z0, int nx, int ny, int nz, BlockState[] palette, short[] shell, short[][] runs) {
    }

    private synchronized Data data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    // 表のファイルがあるか (体の担当が .bin を置く前の骨組みでは無い)
    public boolean present() {
        try (InputStream raw = TitanBodyBlocks.class.getResourceAsStream(resource)) {
            return raw != null;
        } catch (IOException e) {
            return false;
        }
    }

    private Data load() {
        try (InputStream raw = TitanBodyBlocks.class.getResourceAsStream(resource)) {
            if (raw == null) {
                throw new IllegalStateException(resource + " is missing");
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals("TBT1")) {
                throw new IOException("bad " + resource);
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
                String name = new String(b, StandardCharsets.UTF_8);
                palette[i] = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name)).defaultBlockState();
                if (!name.equals("air") && palette[i].isAir()) {
                    SkyAbyss.LOGGER.warn("titan body {}: unknown block {}", resource, name);
                }
            }
            short[] shell = new short[nx * nz];
            short[][] runs = new short[nx * nz][];
            for (int a = 0; a < nx; a++) {
                for (int c = 0; c < nz; c++) {
                    int i = a * nz + c;
                    shell[i] = in.readShort();
                    int segs = in.readShort();
                    short[] r = new short[segs * 3];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = in.readShort();
                    }
                    runs[i] = r;
                }
            }
            Data data = new Data(x0, y0, z0, nx, ny, nz, palette, shell, runs);
            int[] fixed = tidy(data);
            if (fixed[0] + fixed[1] > 0) {
                SkyAbyss.LOGGER.info("titan body {}: removed {} stray voxels, filled {} one-block pits (建築の監査 FLOAT_BLOCK / FLOOR_DIMPLE)", resource, fixed[0], fixed[1]);
            }
            return data;
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }

    // 体の中の palette 番号 (無ければ -1)
    private static int cell(Data d, int a, int c, int j) {
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz || j < 0 || j >= d.ny) {
            return -1;
        }
        short[] r = d.runs[a * d.nz + c];
        for (int k = 0; k < r.length; k += 3) {
            if (j >= r[k] && j < r[k] + r[k + 1]) {
                return r[k + 2];
            }
        }
        return -1;
    }

    // モデルの格子から焼いた表の、1 ブロックの傷を直す (建築の監査 BuildAudit):
    //  (1) まわり 26 マスに何も無い、宙に浮いた 1 ブロックを消す
    //  (2) 上が空いた床の 1 マスだけが、四方より 1 低いくぼみ (1 つ低く埋まったブロック) を、同じブロックを 1 つ重ねて平らにする
    // 戻り値は {消した数, 埋めた数}
    private static int[] tidy(Data d) {
        int stray = 0;
        for (int a = 0; a < d.nx; a++) {
            for (int c = 0; c < d.nz; c++) {
                short[] r = d.runs[a * d.nz + c];
                for (int k = 0; k < r.length; k += 3) {
                    if (r[k + 1] != 1 || d.palette[r[k + 2]].isAir()) {
                        continue;
                    }
                    int j = r[k];
                    boolean alone = true;
                    for (int da = -1; da <= 1 && alone; da++) {
                        for (int dc = -1; dc <= 1 && alone; dc++) {
                            for (int dj = -1; dj <= 1; dj++) {
                                if ((da | dc | dj) != 0 && cell(d, a + da, c + dc, j + dj) >= 0) {
                                    alone = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (alone) {
                        short[] n = new short[r.length - 3];
                        System.arraycopy(r, 0, n, 0, k);
                        System.arraycopy(r, k + 3, n, k, r.length - k - 3);
                        d.runs[a * d.nz + c] = n;
                        stray++;
                        break;
                    }
                }
            }
        }
        java.util.List<int[]> pits = new java.util.ArrayList<>();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int a = 0; a < d.nx; a++) {
            for (int c = 0; c < d.nz; c++) {
                short[] r = d.runs[a * d.nz + c];
                if (r.length == 0) {
                    continue;
                }
                int j = r[r.length - 3] + r[r.length - 2] - 1;
                if (j + 5 >= d.ny || d.palette[r[r.length - 1]].isAir() || d.palette[r[r.length - 1]].getFluidState().isEmpty() == false) {
                    continue;
                }
                boolean pit = true;
                for (int[] dd : dirs) {
                    if (cell(d, a + dd[0], c + dd[1], j + 1) < 0 || d.palette[cell(d, a + dd[0], c + dd[1], j + 1)].isAir()) {
                        pit = false;
                        break;
                    }
                    for (int up = 2; up <= 4; up++) {
                        if (cell(d, a + dd[0], c + dd[1], j + up) >= 0) {
                            pit = false;
                            break;
                        }
                    }
                    if (!pit) {
                        break;
                    }
                }
                if (pit) {
                    pits.add(new int[] {a, c, j, r[r.length - 1]});
                }
            }
        }
        for (int[] p : pits) {
            short[] r = d.runs[p[0] * d.nz + p[1]];
            short[] n = java.util.Arrays.copyOf(r, r.length + 3);
            n[r.length] = (short) (p[2] + 1);
            n[r.length + 1] = 1;
            n[r.length + 2] = (short) p[3];
            d.runs[p[0] * d.nz + p[1]] = n;
        }
        return new int[] {stray, pits.size()};
    }

    public int minX() {
        return data().x0;
    }

    public int maxX() {
        return data().x0 + data().nx - 1;
    }

    public int minZ() {
        return data().z0;
    }

    public int maxZ() {
        return data().z0 + data().nz - 1;
    }

    public int minY() {
        return data().y0;
    }

    public int maxY() {
        return data().y0 + data().ny - 1;
    }

    // 骨 (甲羅・鱗板) のいちばん上のブロックの y。骨の無い柱は Integer.MIN_VALUE
    public int shellTop(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return Integer.MIN_VALUE;
        }
        int t = d.shell[a * d.nz + c];
        return t < 0 ? Integer.MIN_VALUE : d.y0 + t;
    }

    // 体のブロック (無ければ null)
    public BlockState at(int x, int y, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        int j = y - d.y0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz || j < 0 || j >= d.ny) {
            return null;
        }
        short[] r = d.runs[a * d.nz + c];
        for (int k = 0; k < r.length; k += 3) {
            if (j >= r[k] && j < r[k] + r[k + 1]) {
                return d.palette[r[k + 2]];
            }
        }
        return null;
    }

    // 体の何か (骨・遺跡・飾り・頭・脚) のいちばん上のブロックの y。無ければ Integer.MIN_VALUE
    public int top(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return Integer.MIN_VALUE;
        }
        short[] r = d.runs[a * d.nz + c];
        if (r.length == 0) {
            return Integer.MIN_VALUE;
        }
        return d.y0 + r[r.length - 3] + r[r.length - 2] - 1;
    }

    // 体を置く。範囲の中で、体のブロックでない所は background (y → ブロック。潮殻は海面まで水・上は空、余燼は灰の原・上は空) に戻す
    // (前の版の体を消す)。変わるブロックだけを置く (同じなら触らない)。置いたブロックの数を返す
    public int place(ServerLevel level, int flags, IntFunction<BlockState> background) {
        BackBuildJobs.Job job = job(level, flags, background);
        job.step(Long.MAX_VALUE);
        return job.placed();
    }

    // 同じ置き方を、1 列ずつ何 tick かに分けて進められる形で返す (BackBuildJobs)
    public BackBuildJobs.ColumnJob job(ServerLevel level, int flags, IntFunction<BlockState> background) {
        Data d = data();
        BlockState[] base = new BlockState[d.ny];
        for (int j = 0; j < d.ny; j++) {
            base[j] = background.apply(d.y0 + j);
        }
        BlockState[] column = new BlockState[d.ny];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return new BackBuildJobs.ColumnJob(level, d.x0, d.x0 + d.nx - 1, d.z0, d.z0 + d.nz - 1) {
            @Override
            protected int column(ServerLevel lv, LevelChunk chunk, int x, int z) {
                int a = x - d.x0;
                int c = z - d.z0;
                if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
                    return 0;
                }
                System.arraycopy(base, 0, column, 0, d.ny);
                short[] r = d.runs[a * d.nz + c];
                for (int k = 0; k < r.length; k += 3) {
                    BlockState s = d.palette[r[k + 2]];
                    for (int j = r[k]; j < r[k] + r[k + 1]; j++) {
                        column[j] = s;
                    }
                }
                int placed = 0;
                for (int j = 0; j < d.ny; j++) {
                    m.set(x, d.y0 + j, z);
                    if (chunk.getBlockState(m) != column[j]) {
                        lv.setBlock(m, column[j], flags);
                        placed++;
                    }
                }
                return placed;
            }
        };
    }
}
