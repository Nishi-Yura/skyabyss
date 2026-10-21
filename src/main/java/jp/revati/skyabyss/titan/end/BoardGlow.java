package jp.revati.skyabyss.titan.end;

import jp.revati.skyabyss.util.Players;

import java.util.List;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

// 乗り込み口の光 (docs/design/01 の 4.3「全巨神に共通の決まり」)。岩鯨の鎖・潮殻の潮門・雷翼の昇り風・余燼の口が、
// 乗れる人にだけ、遠くから見えるほのかな脈打つ光と粒を出す。乗る資格のない人・眠っている巨神には出さない。
// 粒は人ごとに送る (ServerLevel.sendParticles(player, ...))。ほかの人の画面は変わらない。巨神の本体 (entity) が、乗れる状態のあいだ毎 tick 呼ぶ。
public final class BoardGlow {
    // 光が見える距離 (ブロック)。粒の遠距離描画 (longDistance) で送る
    public static final double RANGE = 220.0;
    // 脈の周期 (tick)
    public static final int PULSE = 50;
    // 点を送る間隔 (tick)
    public static final int EVERY = 4;

    // 巨神ごとの色 (岩鯨 = 冷たい水色、潮殻 = 真珠の青白、雷翼 = 黄みの白、余燼 = 橙)
    public static int colour(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> 0x9FE8E0;
            case Titans.THUNDER_WING -> 0xFFF2A0;
            case Titans.EMBER -> 0xFF9A3C;
            default -> 0x7FF4FF;
        };
    }

    private BoardGlow() {
    }

    // 光の柱と枠の知らせ (巨神ごとの色)
    public static TitanEndNetwork.Beam beam(String titan, Shape s) {
        return beam(titan, titan, s);
    }

    // key = 知らせの鍵 (同じ巨神の二つ目の標は「巨神#名札」。クライアントは鍵ごとに一つ描く)
    public static TitanEndNetwork.Beam beam(String key, String titan, Shape s) {
        return new TitanEndNetwork.Beam(key, colour(titan), s.centre().x, s.centre().y, s.centre().z, s.hx(), s.hy(), s.hz(), s.yaw(),
                s.beamFrom(), s.beamTo(), s.beamRadius(), BEAM_TTL);
    }

    // 脈の強さ (0〜1)。tick から
    public static float pulse(long tick) {
        return 0.5f + 0.5f * Mth.sin((float) (tick % PULSE) / PULSE * Mth.TWO_PI);
    }

    // この人に光を見せてよいか (乗る資格があり、巨神が眠っていない)
    public static boolean visibleTo(ServerPlayer player, String titan) {
        return !player.isSpectator() && Progression.canBoard(player, titan) && !TitanDormancy.isResting(player.server, titan);
    }

    // 光の柱と枠 (クライアントが描く。TitanEndNetwork.Beam)。centre = 枠の中心、hx・hy・hz = 枠の半分の大きさ (hx は yaw の向きの横、hz は奥行き)、
    // beamFrom〜beamTo = 光の柱の高さ (y)、beamRadius = 柱の半径
    public record Shape(Vec3 centre, float hx, float hy, float hz, float yaw, double beamFrom, double beamTo, float beamRadius) {
        // 点の並び (縦の鎖・昇り風の柱) から: いちばん低い点に枠、そこから最も高い点 (最低 32 ブロック) まで柱
        public static Shape fromPoints(List<Vec3> points) {
            Vec3 lo = points.get(0);
            double hi = lo.y;
            for (Vec3 p : points) {
                if (p.y < lo.y) {
                    lo = p;
                }
                hi = Math.max(hi, p.y);
            }
            return new Shape(lo, 1.6f, 2.6f, 1.6f, 0f, lo.y, Math.max(hi, lo.y + MIN_BEAM), 0.9f);
        }
    }

    // 光の柱の最低の高さ (ブロック。遠くから見えるように)
    public static final double MIN_BEAM = 32.0;
    // 光の柱・枠の知らせを送る間隔 (tick) と、クライアントが表示を続ける長さ (tick。次の知らせが来なければ消える)
    public static final int BEAM_EVERY = 20;
    public static final int BEAM_TTL = 34;

    public static int emit(ServerLevel level, String titan, List<Vec3> points) {
        return points.isEmpty() ? 0 : emit(level, titan, points, Shape.fromPoints(points));
    }

    // points に光を出す。EVERY tick ごとに呼んでよい (それ以外の tick は何もしない)。光を送った人の数を返す
    public static int emit(ServerLevel level, String titan, List<Vec3> points, Shape shape) {
        return emit(level, titan, titan, points, shape);
    }

    // key = 光の柱と枠の知らせの鍵 (同じ巨神に標を二つ立てるとき、二つ目は「巨神#名札」)
    public static int emit(ServerLevel level, String titan, String key, List<Vec3> points, Shape shape) {
        long t = level.getGameTime();
        if (points.isEmpty() || t % EVERY != 0 || TitanDormancy.isResting(level.getServer(), titan)) {
            return 0;
        }
        float pulse = pulse(t);
        int rgb = colour(titan);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), 2.2f + 2.0f * pulse);
        int served = 0;
        for (ServerPlayer player : Players.snapshot(level.players())) {
            if (!visibleTo(player, titan)) {
                continue;
            }
            Vec3 near = null;
            double best = RANGE * RANGE;
            for (Vec3 p : points) {
                double d = player.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    near = p;
                }
            }
            if (near == null) {
                continue;
            }
            served++;
            if (t % BEAM_EVERY == 0 && player.distanceToSqr(shape.centre()) < (RANGE + 40) * (RANGE + 40)) {
                // 光の柱と枠 (遠くからでも見える。乗れる人にだけ、巨神が眠る・去るとすぐ消える)
                PacketDistributor.sendToPlayer(player, beam(key, titan, shape));
            }
            // 岩鯨は、鎖の下の地面の陣と鎖そのものが目印なので、鎖に沿った青い粒は出さない (ユーザーの声)
            if (Titans.STONE_WHALE.equals(titan)) {
                continue;
            }
            // 遠いほど、かたまりを大きく (見えるように)。近づくと細かい粒だけになる
            boolean far = best > 40 * 40;
            double spread = far ? 1.1 : 0.5;
            for (Vec3 p : points) {
                level.sendParticles(player, dust, true, p.x, p.y, p.z, far ? 4 + (int) (pulse * 5) : 1 + (int) (pulse * 2), spread, spread, spread, 0.0);
                if (t % (EVERY * 3) == 0) {
                    level.sendParticles(player, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 1, 0.4, 0.8, 0.4, 0.01);
                }
            }
        }
        return served;
    }
}
