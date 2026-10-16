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
            return new Data(x0, y0, z0, nx, ny, nz, palette, shell, runs);
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
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
        Data d = data();
        BlockState[] column = new BlockState[d.ny];
        BlockState[] base = new BlockState[d.ny];
        for (int j = 0; j < d.ny; j++) {
            base[j] = background.apply(d.y0 + j);
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        int cx0 = d.x0 >> 4;
        int cx1 = (d.x0 + d.nx - 1) >> 4;
        int cz0 = d.z0 >> 4;
        int cz1 = (d.z0 + d.nz - 1) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx;
                        int z = (cz << 4) + lz;
                        int a = x - d.x0;
                        int c = z - d.z0;
                        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
                            continue;
                        }
                        System.arraycopy(base, 0, column, 0, d.ny);
                        short[] r = d.runs[a * d.nz + c];
                        for (int k = 0; k < r.length; k += 3) {
                            BlockState s = d.palette[r[k + 2]];
                            for (int j = r[k]; j < r[k] + r[k + 1]; j++) {
                                column[j] = s;
                            }
                        }
                        for (int j = 0; j < d.ny; j++) {
                            m.set(x, d.y0 + j, z);
                            if (chunk.getBlockState(m) != column[j]) {
                                level.setBlock(m, column[j], flags);
                                placed++;
                            }
                        }
                    }
                }
            }
        }
        return placed;
    }
}
