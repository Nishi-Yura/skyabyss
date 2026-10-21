package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.util.Players;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 雷翼の背の粒。予告の粒はパーティクルの設定に左右されないよう overrideLimiter で送る (岩鯨の必須 4)
// グループごとの仕掛け (招雷の輪・予告線・眼の間の光の筋) の粒は、そのグループの人 (と観戦者) にだけ送る: to(group, () -> ...) で囲む
public final class StormFx {
    // 送る相手の距離
    private static final double RANGE = 96;
    // いま粒を送る相手のグループ (null なら全員)。to() の中だけ
    @Nullable
    private static UUID audience;

    private StormFx() {
    }

    // body の中で出す粒を、group の人 (と観戦者) にだけ送る。group が null なら全員へ
    public static void to(@Nullable UUID group, Runnable body) {
        UUID was = audience;
        audience = group;
        try {
            body.run();
        } finally {
            audience = was;
        }
    }

    public static void forced(ServerLevel level, ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayer pl : Players.snapshot(level.players())) {
            if (audience != null && !pl.isSpectator() && !Groups.isMember(pl, audience)) {
                continue;
            }
            if (pl.distanceToSqr(x, y, z) < RANGE * RANGE) {
                // 区画の写しのセルの決まりの中 (TrialFrame) では、手本の座標で比べて写しの座標に出す
                net.minecraft.world.phys.Vec3 at = jp.revati.skyabyss.trial.TrialFrame.current().world(new net.minecraft.world.phys.Vec3(x, y, z));
                level.sendParticles(pl, p, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
            }
        }
    }

    public static void burst(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread) {
        forced(level, p, at.x, at.y, at.z, count, spread, spread, spread, 0.1);
    }

    // 水平の輪 (半径 r、点の数 n)
    public static void ring(ServerLevel level, ParticleOptions p, Vec3 center, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            forced(level, p, center.x + Math.cos(a) * r, center.y + 0.1, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // 点 a から b への線 (step ブロックおき)
    public static void line(ServerLevel level, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            forced(level, p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }
}
