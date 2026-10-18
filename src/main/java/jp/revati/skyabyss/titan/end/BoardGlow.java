package jp.revati.skyabyss.titan.end;

import java.util.List;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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

    // 脈の強さ (0〜1)。tick から
    public static float pulse(long tick) {
        return 0.5f + 0.5f * Mth.sin((float) (tick % PULSE) / PULSE * Mth.TWO_PI);
    }

    // この人に光を見せてよいか (乗る資格があり、巨神が眠っていない)
    public static boolean visibleTo(ServerPlayer player, String titan) {
        return !player.isSpectator() && Progression.canBoard(player, titan) && !TitanDormancy.isResting(player.server, titan);
    }

    // points に光を出す。EVERY tick ごとに呼んでよい (それ以外の tick は何もしない)。光を送った人の数を返す
    public static int emit(ServerLevel level, String titan, List<Vec3> points) {
        long t = level.getGameTime();
        if (points.isEmpty() || t % EVERY != 0 || TitanDormancy.isResting(level.getServer(), titan)) {
            return 0;
        }
        float pulse = pulse(t);
        int rgb = colour(titan);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), 2.2f + 2.0f * pulse);
        int served = 0;
        for (ServerPlayer player : level.players()) {
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
