package jp.revati.skyabyss.client.splat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// 点の模型 (ピクセルアートのスプラット)。手で彫った・彫り直したもの (assets/skyabyss/splat/<名前>.bin、形式 SPL3、tools/gen/splat/) と、
// 低ポリのメッシュからその場で作ったもの (SplatGen) の両方がこの形になる。
// 座標は描画の空間 (x 反転、ブロック)。不透明の点は骨 A の番号順に並べ、骨ごとの範囲 (boneStart / boneCount) を持つ。
// 骨の中の点は乱数の順なので、範囲の先頭から何割かだけ描けば一様に間引ける (遠くの間引き)
public final class SplatCloud {
    public static final int F_GLOW = 1;
    public static final int F_HALO = 2;
    public static final int F_EMBER = 4;

    public final String name;
    public final String[] bones;
    private final Map<String, Integer> boneIndex = new HashMap<>();
    // 不透明の点 (表面・光る点) と、足し算の点 (にじみ・火の粉) に分けて持つ
    public final Part solid;
    public final Part additive;
    public final int total;
    // 不透明の点のうち骨 i (骨 A) の点の範囲
    public final int[] boneStart;
    public final int[] boneCount;

    public static final class Part {
        public final int n;
        public final float[] x;
        public final float[] y;
        public final float[] z;
        public final float[] size;
        public final int[] argb;
        public final byte[] boneA;
        public final byte[] boneB;
        public final float[] weightA;
        public final byte[] flags;
        // 法線 (骨の空間、x127)。0,0,0 = 向きなし (裏向きでも間引かない)
        public final byte[] nx;
        public final byte[] ny;
        public final byte[] nz;

        public Part(int n) {
            this.n = n;
            x = new float[n];
            y = new float[n];
            z = new float[n];
            size = new float[n];
            argb = new int[n];
            boneA = new byte[n];
            boneB = new byte[n];
            weightA = new float[n];
            flags = new byte[n];
            nx = new byte[n];
            ny = new byte[n];
            nz = new byte[n];
        }

        void copy(int from, Part to, int k) {
            to.x[k] = x[from];
            to.y[k] = y[from];
            to.z[k] = z[from];
            to.size[k] = size[from];
            to.argb[k] = argb[from];
            to.boneA[k] = boneA[from];
            to.boneB[k] = boneB[from];
            to.weightA[k] = weightA[from];
            to.flags[k] = flags[from];
            to.nx[k] = nx[from];
            to.ny[k] = ny[from];
            to.nz[k] = nz[from];
        }
    }

    // solid は骨 A の順に並べ直す (同じ骨の中の順は保つ)
    public SplatCloud(String name, String[] bones, Part solid, Part additive) {
        this.name = name;
        this.bones = bones;
        for (int i = 0; i < bones.length; i++) {
            boneIndex.put(bones[i], i);
        }
        int nb = bones.length;
        boneStart = new int[nb];
        boneCount = new int[nb];
        for (int i = 0; i < solid.n; i++) {
            boneCount[solid.boneA[i] & 255]++;
        }
        int acc = 0;
        for (int b = 0; b < nb; b++) {
            boneStart[b] = acc;
            acc += boneCount[b];
        }
        Part sorted = new Part(solid.n);
        int[] fill = boneStart.clone();
        for (int i = 0; i < solid.n; i++) {
            solid.copy(i, sorted, fill[solid.boneA[i] & 255]++);
        }
        this.solid = sorted;
        this.additive = additive;
        this.total = solid.n + additive.n;
    }

    // 骨の番号 (無ければ -1)
    public int boneIndex(String bone) {
        Integer i = boneIndex.get(bone);
        return i == null ? -1 : i;
    }

    // SPL3 (tools/gen/splat/splat_lib.py の write_bin3): 圧縮した中身。点は骨・位置の順に並んでいるので、読んだあと乱数で並べ替える
    // (同じ名前ならいつも同じ順。骨ごとの範囲の先頭から何割かを描けば一様に間引ける)
    private static SplatCloud readSpl3(String name, DataInputStream d) throws IOException {
        int nb = d.readInt();
        String[] bones = new String[nb];
        for (int i = 0; i < nb; i++) {
            bones[i] = d.readUTF();
        }
        int n = d.readInt();
        float lx = d.readFloat(), ly = d.readFloat(), lz = d.readFloat();
        float step = d.readFloat();
        float smax = d.readFloat();
        long[][] q = new long[3][n];
        for (int k = 0; k < 3; k++) {
            long acc = 0;
            for (int i = 0; i < n; i++) {
                long v = 0;
                int shift = 0;
                int b;
                do {
                    b = d.readUnsignedByte();
                    v |= (long) (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0);
                acc += (v >>> 1) ^ -(v & 1);
                q[k][i] = acc;
            }
        }
        byte[] size = col(d, n), r = col(d, n), g = col(d, n), bl = col(d, n), a = col(d, n), bb = col(d, n), w = col(d, n), f = col(d, n);
        byte[] nx = col(d, n), ny = col(d, n), nz = col(d, n);
        Part all = new Part(n);
        int add = 0;
        for (int i = 0; i < n; i++) {
            all.x[i] = -(lx + q[0][i] * step) / 16f;
            all.y[i] = (ly + q[1][i] * step) / 16f;
            all.z[i] = (lz + q[2][i] * step) / 16f;
            all.size[i] = (size[i] & 255) / 255f * smax / 16f;
            all.argb[i] = 0xFF000000 | ((r[i] & 255) << 16) | ((g[i] & 255) << 8) | (bl[i] & 255);
            all.boneA[i] = a[i];
            all.boneB[i] = bb[i];
            all.weightA[i] = (w[i] & 255) / 255f;
            all.flags[i] = f[i];
            // 描画の空間は x を反転している
            all.nx[i] = (byte) -nx[i];
            all.ny[i] = ny[i];
            all.nz[i] = nz[i];
            if ((f[i] & (F_HALO | F_EMBER)) != 0) {
                add++;
            }
        }
        // 乱数の順に並べ替える
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.SplittableRandom rng = new java.util.SplittableRandom(name.hashCode());
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        Part solid = new Part(n - add);
        Part additive = new Part(add);
        int si = 0;
        int ai = 0;
        for (int k = 0; k < n; k++) {
            int i = order[k];
            if ((all.flags[i] & (F_HALO | F_EMBER)) != 0) {
                all.copy(i, additive, ai++);
            } else {
                all.copy(i, solid, si++);
            }
        }
        return new SplatCloud(name, bones, solid, additive);
    }

    private static byte[] col(DataInputStream d, int n) throws IOException {
        byte[] b = new byte[n];
        d.readFully(b);
        return b;
    }

    public static SplatCloud read(String name, InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] == 'S' && magic[1] == 'P' && magic[2] == 'L' && magic[3] == '3') {
            return readSpl3(name, new DataInputStream(new java.io.BufferedInputStream(new java.util.zip.InflaterInputStream(d))));
        }
        throw new IOException(name + ": not a SPL3 file");
    }
}
