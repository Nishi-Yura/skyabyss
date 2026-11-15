package jp.revati.skyabyss.client.splat;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// 点の模型 (ピクセルアートのスプラット。試作は熔岩の大蛇)。assets/skyabyss/splat/<名前>.bin を読んだもの。
// 作るのは tools/gen/splat/ (splat_lib.py の write_bin が形式の元)。点は GeckoLib の骨の二本に重みで結ばれ、骨のポーズで動く (SplatDraw)。
// 座標は読み込みのときに描画空間 (x 反転、1/16 ブロック → ブロック) へ直しておく。点は乱数の順に並んでいるので、先頭から何割かだけ描けば一様に間引ける
public final class SplatCloud {
    public static final int F_GLOW = 1;
    public static final int F_HALO = 2;
    public static final int F_EMBER = 4;

    public final String name;
    public final String[] bones;
    private final Map<String, Integer> boneIndex = new HashMap<>();
    // 不透明の点 (表面・けば・光る点) と、足し算の点 (にじみ・火の粉) に分けて持つ
    public final Part solid;
    public final Part additive;
    public final int total;

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

        Part(int n) {
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
    }

    private SplatCloud(String name, String[] bones, Part solid, Part additive) {
        this.name = name;
        this.bones = bones;
        for (int i = 0; i < bones.length; i++) {
            boneIndex.put(bones[i], i);
        }
        this.solid = solid;
        this.additive = additive;
        this.total = solid.n + additive.n;
    }

    // 骨の番号 (無ければ -1)
    public int boneIndex(String bone) {
        Integer i = boneIndex.get(bone);
        return i == null ? -1 : i;
    }

    public static SplatCloud read(String name, InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (magic[0] != 'S' || magic[1] != 'P' || magic[2] != 'L' || (magic[3] != '1' && magic[3] != '2')) {
            throw new IOException(name + ": not a SPL1/SPL2 file");
        }
        boolean normals = magic[3] == '2';
        int nb = d.readInt();
        String[] bones = new String[nb];
        for (int i = 0; i < nb; i++) {
            bones[i] = d.readUTF();
        }
        int n = d.readInt();
        float[] x = new float[n], y = new float[n], z = new float[n], s = new float[n];
        int[] c = new int[n];
        byte[] a = new byte[n], b = new byte[n], w = new byte[n], f = new byte[n];
        byte[] qx = new byte[n], qy = new byte[n], qz = new byte[n];
        int add = 0;
        for (int i = 0; i < n; i++) {
            x[i] = -d.readFloat() / 16f;
            y[i] = d.readFloat() / 16f;
            z[i] = d.readFloat() / 16f;
            s[i] = d.readFloat() / 16f;
            c[i] = d.readInt();
            a[i] = d.readByte();
            b[i] = d.readByte();
            w[i] = d.readByte();
            f[i] = d.readByte();
            if (normals) {
                // 描画の空間は x を反転している
                qx[i] = (byte) -d.readByte();
                qy[i] = d.readByte();
                qz[i] = d.readByte();
            }
            if ((f[i] & (F_HALO | F_EMBER)) != 0) {
                add++;
            }
        }
        Part solid = new Part(n - add);
        Part additive = new Part(add);
        int si = 0;
        int ai = 0;
        for (int i = 0; i < n; i++) {
            boolean isAdd = (f[i] & (F_HALO | F_EMBER)) != 0;
            Part p = isAdd ? additive : solid;
            int k = isAdd ? ai++ : si++;
            p.x[k] = x[i];
            p.y[k] = y[i];
            p.z[k] = z[i];
            p.size[k] = s[i];
            p.argb[k] = c[i];
            p.boneA[k] = a[i];
            p.boneB[k] = b[i];
            p.weightA[k] = (w[i] & 255) / 255f;
            p.flags[k] = f[i];
            p.nx[k] = qx[i];
            p.ny[k] = qy[i];
            p.nz[k] = qz[i];
        }
        return new SplatCloud(name, bones, solid, additive);
    }
}
