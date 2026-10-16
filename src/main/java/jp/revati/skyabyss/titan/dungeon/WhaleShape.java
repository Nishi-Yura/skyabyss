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
    static int place(BuildKit kit) {
        Data d = data();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] column = new BlockState[d.ny];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int cx = d.x0 >> 4; cx <= (d.x0 + d.nx - 1) >> 4; cx++) {
            for (int cz = d.z0 >> 4; cz <= (d.z0 + d.nz - 1) >> 4; cz++) {
                LevelChunk chunk = kit.level().getChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx;
                        int z = (cz << 4) + lz;
                        int i = index(x, z);
                        if (i < 0) {
                            continue;
                        }
                        java.util.Arrays.fill(column, air);
                        short[] r = d.runs[i];
                        for (int k = 0; k < r.length; k += 3) {
                            BlockState s = d.palette[r[k + 2]];
                            for (int j = r[k]; j < r[k] + r[k + 1]; j++) {
                                column[j] = s;
                            }
                        }
                        for (int j = 0; j < d.ny; j++) {
                            m.set(x, d.y0 + j, z);
                            if (chunk.getBlockState(m) != column[j]) {
                                kit.level().setBlock(m, column[j], BuildKit.UPDATE);
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
