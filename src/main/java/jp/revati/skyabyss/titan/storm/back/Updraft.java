package jp.revati.skyabyss.titan.storm.back;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 昇り風の動き (12 の 2.4節・4.1節・5.3節)。乗り込みの柱 (GaleColumnEntity)、西の試練の風の穴、心臓の風穴が同じ動きを使う。
// 柱の中にいる間、毎 tick lift(entity, maxHeight) を呼ぶ。maxHeight は上がる上の端の高さ (絶対の y)。
// - 毎秒 8 ブロック (0.4/tick) で上がる。上の端の近くでは速さを落とし、端で浮いたまま止まる
// - 柱の中でも横へ自由に動ける (横の速さは触らない。旧い版は毎 tick サーバーの古い速さ ×0.92 を送り返して、横の入力を打ち消していた)
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
        // プレイヤーの横の速さは、サーバーの deltaMovement ではなく、実際の動き (位置の差) から取る。サーバー側の速さは古く、送ると入力が止まる
        Vec3 h = e instanceof net.minecraft.server.level.ServerPlayer sp ? actualMotion(sp) : v;
        e.setDeltaMovement(h.x, vy, h.z);
        e.fallDistance = 0;
        e.hurtMarked = true;
        if (e instanceof Player p) {
            // クライアントで動きを予測するので、プレイヤーには速さを送り直す
            p.hurtMarked = true;
        }
        return y >= maxHeight - 0.3;
    }

    private static final java.util.Map<java.util.UUID, double[]> LAST = new java.util.HashMap<>();

    // 実際の動き (1 tick あたり、位置の差)。毎 tick 呼ばなくてよい (間が 5 tick 以内なら割る)。初回・間が空いたとき・瞬間移動のあとは 0
    public static Vec3 actualMotion(net.minecraft.server.level.ServerPlayer p) {
        long now = p.level().getGameTime();
        double[] last = LAST.put(p.getUUID(), new double[] {p.getX(), p.getY(), p.getZ(), now});
        if (last == null) {
            return Vec3.ZERO;
        }
        long gap = now - (long) last[3];
        if (gap <= 0 || gap > 5) {
            return Vec3.ZERO;
        }
        Vec3 d = new Vec3((p.getX() - last[0]) / gap, (p.getY() - last[1]) / gap, (p.getZ() - last[2]) / gap);
        return d.length() > 1.2 ? Vec3.ZERO : d;
    }

    public static void forget(java.util.UUID id) {
        LAST.remove(id);
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
