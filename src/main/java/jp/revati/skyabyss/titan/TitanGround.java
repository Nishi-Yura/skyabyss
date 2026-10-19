package jp.revati.skyabyss.titan;

import java.util.List;

import jp.revati.skyabyss.titan.solid.TitanVolume;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

// 巨神の体が地形にめり込まないようにする共通の測り (「ブロックにめり込まない」)。
// 巨神の体積 (TitanVolume の core。近景メッシュから焼いた 2 ブロック四方の柱) の一本一本について、
// 柱の真下の地形の高さ (木・葉・丘を含む) と、柱の一番低い所 (局所の高さ lo) を比べて、
// 「この位置・向きで、体のどこも地形の上に余白 margin を残すために、巨神の位置の y は最低いくつか」を返す (requiredY)。
// 巨神の位置 y は体の底 (geo の y=0)。読み込まれていないチャンクの柱は調べない (同期の読み込みを起こさない)。
public final class TitanGround {
    // 体の底と地形の余白 (ブロック)
    public static final double MARGIN = 1.5;

    // 地形のとり方
    public enum Surface {
        // 木・葉・丘と水面 (空を飛ぶ・陸に降りる巨神。MOTION_BLOCKING)
        TOP(Heightmap.Types.MOTION_BLOCKING),
        // 水を無視した固い地形 (水の中を歩く潮殻。海底・岸・木・葉を数え、水面は数えない。OCEAN_FLOOR)
        FLOOR(Heightmap.Types.OCEAN_FLOOR);

        private final Heightmap.Types types;

        Surface(Heightmap.Types types) {
            this.types = types;
        }
    }

    // 開発用 (/skyabyss dev titanground false): false なら測らず、地形に関わらず従来の高さ・場所にする (直す前後の見比べ)
    public static volatile boolean enabled = true;

    private TitanGround() {
    }

    // (x, z) の地形の高さ (立つ面の y。ブロックの上面)。読み込まれていなければ NaN
    public static double terrainAt(ServerLevel level, Surface surface, double x, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
            return Double.NaN;
        }
        return level.getHeight(surface.types, bx, bz);
    }

    // この体積・位置 (x, z)・向き yaw で、体のどこも地形に余白 margin 以上離れる最低の y。
    // stride > 1 なら柱を間引く (候補を数多く試すとき。余白が間引きの取りこぼしを受ける)。調べた柱がなければ -Infinity
    public static double requiredY(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, float yaw, double margin, int stride) {
        return requiredY(level, surface, volume, x, z, yaw, margin, stride, Double.NEGATIVE_INFINITY);
    }

    // loFrom より上に底のある柱だけを調べる版 (地面に伏せて腹が土に埋まる余燼は、地面より上の体だけが丘・木に触れないことを見る)
    public static double requiredY(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, float yaw, double margin, int stride, double loFrom) {
        if (volume == null || !enabled) {
            return Double.NEGATIVE_INFINITY;
        }
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double need = Double.NEGATIVE_INFINITY;
        List<TitanVolume.Column> cols = volume.coreColumns();
        for (int i = 0; i < cols.size(); i += Math.max(1, stride)) {
            TitanVolume.Column c = cols.get(i);
            if (c.lo() < loFrom) {
                continue;
            }
            double wx = x + fx * c.fwd() + fz * c.side();
            double wz = z + fz * c.fwd() - fx * c.side();
            double t = terrainAt(level, surface, wx, wz);
            if (!Double.isNaN(t)) {
                need = Math.max(need, t + margin - c.lo());
            }
        }
        return need;
    }

    // 柱の真下だけでなく、その前後左右 spread ブロック先の地形も数える版 (動くたびに向きや位置が少しずれるので、崖のへりで下限が一気に跳ばないように、
    // 先に持ち上がり始める。毎 tick の「今の位置」の下限に使う)
    public static double requiredYPadded(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, float yaw, double margin, double spread) {
        if (volume == null || !enabled) {
            return Double.NEGATIVE_INFINITY;
        }
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double need = Double.NEGATIVE_INFINITY;
        for (TitanVolume.Column c : volume.coreColumns()) {
            double wx = x + fx * c.fwd() + fz * c.side();
            double wz = z + fz * c.fwd() - fx * c.side();
            double t = Math.max(terrainAt(level, surface, wx, wz), Math.max(terrainAt(level, surface, wx + spread, wz), terrainAt(level, surface, wx - spread, wz)));
            t = Math.max(t, Math.max(terrainAt(level, surface, wx, wz + spread), terrainAt(level, surface, wx, wz - spread)));
            if (!Double.isNaN(t)) {
                need = Math.max(need, t + margin - c.lo());
            }
        }
        return need;
    }

    public static double requiredY(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, float yaw) {
        return requiredY(level, surface, volume, x, z, yaw, MARGIN, 1);
    }

    // 今の位置と、向いている方へ進んだ先 (distances のそれぞれ) の最低の y のうち一番高いもの。進みながら先に持ち上がるための見通し
    public static double requiredAhead(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, float yaw, double[] distances, int stride) {
        double need = requiredY(level, surface, volume, x, z, yaw, MARGIN, stride);
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (double d : distances) {
            need = Math.max(need, requiredY(level, surface, volume, x + fx * d, z + fz * d, yaw, MARGIN, stride));
        }
        return need;
    }

    // yaw をまだ決めていないとき (降りてから向きが決まる雷翼): 45 度ごとの 8 方向のうち一番高い y
    public static double requiredAnyYaw(ServerLevel level, Surface surface, TitanVolume volume, double x, double z, int stride) {
        double need = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < 8; k++) {
            need = Math.max(need, requiredY(level, surface, volume, x, z, k * 45f, MARGIN, stride));
        }
        return need;
    }

    // 試験用: この位置・向き・高さ y で、体と地形の食い込み (ブロック。0 以下ならめり込んでいない)。柱ごとの (地形 - 体の底) の最大。余白は引かない
    public static double overlap(ServerLevel level, Surface surface, TitanVolume volume, double x, double y, double z, float yaw) {
        return requiredY(level, surface, volume, x, z, yaw, 0.0, 1) - y;
    }
}
