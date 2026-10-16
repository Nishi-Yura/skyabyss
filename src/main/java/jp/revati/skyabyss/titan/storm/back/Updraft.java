package jp.revati.skyabyss.titan.storm.back;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 昇り風の動き (12 の 2.4節・4.1節・5.3節)。乗り込みの柱 (GaleColumnEntity)、西の試練の風の穴、心臓の風穴が同じ動きを使う。
// 柱の中にいる間、毎 tick lift(entity, maxHeight) を呼ぶ。maxHeight は上がる上の端の高さ (絶対の y)。
// - 毎秒 8 ブロック (0.4/tick) で上がる。上の端の近くでは速さを落とし、端で浮いたまま止まる
// - 柱の中は横へも少し動ける (横の速さはそのまま、少しだけ弱める)
// - しゃがむと下りる (毎秒 4)
// - 落下の距離を消す (柱から出て落ちても、柱の中で溜まった高さで傷を受けない)
public final class Updraft {
    public static final double RISE = 0.4;
    public static final double SINK = -0.2;

    private Updraft() {
    }

    // 上の端 maxHeight まで持ち上げる。上の端に着いていれば true
    public static boolean lift(Entity e, double maxHeight) {
        double y = e.getY();
        Vec3 v = e.getDeltaMovement();
        double vy;
        if (e.isShiftKeyDown()) {
            vy = SINK;
        } else if (y < maxHeight - 1.5) {
            vy = Math.min(RISE, v.y + 0.12);
        } else if (y < maxHeight) {
            // 端の近くで落ち着かせる
            vy = Math.max(0.05, (maxHeight - y) * 0.25);
        } else {
            vy = Math.max(-0.04, (maxHeight - y) * 0.2);
        }
        e.setDeltaMovement(v.x * 0.92, vy, v.z * 0.92);
        e.fallDistance = 0;
        e.hurtMarked = true;
        if (e instanceof Player p) {
            // クライアントで動きを予測するので、プレイヤーには速さを送り直す
            p.hurtMarked = true;
        }
        return y >= maxHeight - 0.3;
    }

    // 柱の根元 baseY から height だけ上がる形
    public static boolean lift(Entity e, double baseY, double height) {
        return lift(e, baseY + height);
    }

    // 柱の粒 (吸い上げられる羽根と雨粒)。height の上の端まで粒が上がるので、今どこまで上がるか見える
    public static void particles(ServerLevel level, Vec3 base, double radius, double height, int amount) {
        for (int i = 0; i < amount; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = level.random.nextDouble() * radius;
            double h = level.random.nextDouble() * height;
            StormFx.forced(level, ParticleTypes.CLOUD, base.x + Math.cos(a) * r, base.y + h, base.z + Math.sin(a) * r, 0, 0, 1, 0, 0.25);
        }
        // 上の端の印 (白い羽根の輪)
        StormFx.ring(level, ParticleTypes.END_ROD, base.add(0, height, 0), radius * 0.8, 6);
    }
}
