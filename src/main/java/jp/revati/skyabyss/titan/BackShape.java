package jp.revati.skyabyss.titan;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

// 巨神の形の背中 (ブロックでできた巨神) の読み書き (12 の 10.2節。潮殻の TideTurtle を巨神の種類によらない形にしたもの)。
// 形は tools/gen_back_shape.py がモデルの格子から作った bin を読む。雷翼は data/skyabyss/storm_back/bird.bin。
// (潮殻は今のところ TideTurtle のまま。中身は同じ形式なので、潮殻の担当が移すときはこの部品に置き換えられる)
//
// bin (gzip): "TBT1"、原点 x0 y0 z0、大きさ nx ny nz (int)、ブロックの名前の表 (short 個数 + UTF)、
// x ごと z ごとの柱: 体の骨のいちばん上の段 (short、無ければ -1)、続く区間の数 (short)、区間 (始めの段・長さ・表の番号、short)
public final class BackShape {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final String path;
    private Data data;

    private record Data(int x0, int y0, int z0, int nx, int ny, int nz, BlockState[] palette, short[] body, short[][] runs) {
    }

    public BackShape(String path) {
        this.path = path;
    }

    public synchronized boolean exists() {
        try (InputStream raw = BackShape.class.getResourceAsStream(path)) {
            return raw != null;
        } catch (IOException e) {
            return false;
        }
    }

    private synchronized Data data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private Data load() {
        try (InputStream raw = BackShape.class.getResourceAsStream(path)) {
            if (raw == null) {
                throw new IllegalStateException(path + " is missing");
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("TBT1")) {
                throw new IOException("bad " + path);
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
                    SkyAbyss.LOGGER.warn("back shape {}: unknown block {}", path, name);
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
            throw new IllegalStateException("could not read " + path, e);
        }
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

    // 体の骨のいちばん上のブロックの y。無い柱は Integer.MIN_VALUE
    public int bodyTop(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return Integer.MIN_VALUE;
        }
        int t = d.body[a * d.nz + c];
        return t < 0 ? Integer.MIN_VALUE : d.y0 + t;
    }

    // 形のブロック (無ければ null)
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

    // いちばん上のブロックの y (無ければ Integer.MIN_VALUE)
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

    // いちばん下のブロックの y (無ければ Integer.MAX_VALUE)
    public int bottom(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return Integer.MAX_VALUE;
        }
        short[] r = d.runs[a * d.nz + c];
        return r.length == 0 ? Integer.MAX_VALUE : d.y0 + r[0];
    }

    // 形のブロックの数
    public long count() {
        Data d = data();
        long n = 0;
        for (short[] r : d.runs) {
            for (int k = 0; k < r.length; k += 3) {
                if (!d.palette[r[k + 2]].isAir()) {
                    n += r[k + 1];
                }
            }
        }
        return n;
    }

    // 形を置く。範囲の中で、形のブロックでない所は空に戻す (前の版の形を消す)。変わるブロックだけを置く。置いた数を返す
    public int place(ServerLevel level) {
        BackBuildJobs.Job job = job(level);
        job.step(Long.MAX_VALUE);
        return job.placed();
    }

    // 同じ置き方を、1 列ずつ何 tick かに分けて進められる形で返す (BackBuildJobs)
    public BackBuildJobs.ColumnJob job(ServerLevel level) {
        Data d = data();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
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
                java.util.Arrays.fill(column, air);
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
                        lv.setBlock(m, column[j], FLAGS);
                        placed++;
                    }
                }
                return placed;
            }
        };
    }
}
