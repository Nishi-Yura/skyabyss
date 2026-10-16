package jp.revati.skyabyss.titan.tide.back;

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

// 潮殻の背の島そのもの: 巨神本体のモデル (採用案 B) と同じ形・同じ色の、ブロックでできた亀 (08 の 21 章)。
// 形は tools/gen_tide_back_turtle.py がモデルの格子から作った data/skyabyss/tide_back/turtle.bin を読む
// (ゲームの巨神の 1.7 倍。頭は北 -Z、潮門は左後ろ = 南西)。試練の部屋は、置いたあとで TideIslandBuilder が中を掘る。
//
// turtle.bin (gzip): "TBT1"、原点 x0 y0 z0、大きさ nx ny nz (int)、ブロックの名前の表 (short 個数 + UTF)、
// x ごと z ごとの柱: 甲羅のいちばん上の段 (short、無ければ -1)、続く区間の数 (short)、区間 (始めの段・長さ・表の番号、short)
public final class TideTurtle {
    private static Data data;

    private TideTurtle() {
    }

    private record Data(int x0, int y0, int z0, int nx, int ny, int nz, BlockState[] palette, short[] carapace, short[][] runs) {
    }

    private static Data data() {
        if (data == null) {
            data = load();
        }
        return data;
    }

    private static Data load() {
        try (InputStream raw = TideTurtle.class.getResourceAsStream("/data/skyabyss/tide_back/turtle.bin")) {
            if (raw == null) {
                throw new IllegalStateException("turtle.bin is missing");
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!new String(magic, java.nio.charset.StandardCharsets.US_ASCII).equals("TBT1")) {
                throw new IOException("bad turtle.bin");
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
                    SkyAbyss.LOGGER.warn("tide turtle: unknown block {}", name);
                }
            }
            short[] carapace = new short[nx * nz];
            short[][] runs = new short[nx * nz][];
            for (int a = 0; a < nx; a++) {
                for (int c = 0; c < nz; c++) {
                    int i = a * nz + c;
                    carapace[i] = in.readShort();
                    int segs = in.readShort();
                    short[] r = new short[segs * 3];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = in.readShort();
                    }
                    runs[i] = r;
                }
            }
            return new Data(x0, y0, z0, nx, ny, nz, palette, carapace, runs);
        } catch (IOException e) {
            throw new IllegalStateException("could not read turtle.bin", e);
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

    // 甲羅 (体の骨) のいちばん上のブロックの y。甲羅の無い柱は Integer.MIN_VALUE
    public static int carapaceTop(int x, int z) {
        Data d = data();
        int a = x - d.x0;
        int c = z - d.z0;
        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
            return Integer.MIN_VALUE;
        }
        int t = d.carapace[a * d.nz + c];
        return t < 0 ? Integer.MIN_VALUE : d.y0 + t;
    }

    // 亀のブロック (無ければ null)
    public static BlockState at(int x, int y, int z) {
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

    // 亀の何か (甲羅・遺跡・珊瑚・頭・ひれ) のいちばん上のブロックの y。無ければ Integer.MIN_VALUE
    public static int top(int x, int z) {
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

    // 亀を置く。範囲の中で、亀のブロックでない所は海 (y 62 まで水) と空に戻す (前の版の島を消す)。
    // 変わるブロックだけを置く (同じなら触らない)。置いたブロックの数を返す
    public static int place(TideKit kit) {
        Data d = data();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] column = new BlockState[d.ny];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        int cx0 = d.x0 >> 4;
        int cx1 = (d.x0 + d.nx - 1) >> 4;
        int cz0 = d.z0 >> 4;
        int cz1 = (d.z0 + d.nz - 1) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk chunk = kit.level().getChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx;
                        int z = (cz << 4) + lz;
                        int a = x - d.x0;
                        int c = z - d.z0;
                        if (a < 0 || c < 0 || a >= d.nx || c >= d.nz) {
                            continue;
                        }
                        for (int j = 0; j < d.ny; j++) {
                            column[j] = d.y0 + j < TideBackLayout.SEA ? water : air;
                        }
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
                                kit.level().setBlock(m, column[j], TideKit.FLAGS);
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
