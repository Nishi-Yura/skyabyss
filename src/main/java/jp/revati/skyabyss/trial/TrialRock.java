package jp.revati.skyabyss.trial;

import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

// 障害「落石」の共通部品 (W04・W10・W11・W12 など)。誰かの足もとに予告の輪が 1.2 秒 → 落ちて傷を与える。
// 状態は Run.data の rockAt (落ちる時刻。0 = 予告なし)・rockPos・nextRock。始めに run.put("nextRock", ...) を入れておく
public final class TrialRock {
    private TrialRock() {
    }

    public static final int WARN = 24;

    // 一回分を進める (5 tick ごとに呼ぶ)。every = 次の落石までの間 (tick)。人がいなければ何もしない
    public static void tick(ServerLevel back, TrialCell.Run run, List<ServerPlayer> inside, long now, int every, float damage) {
        tick(back, run, inside, now, every, damage, ParticleTypes.POOF, ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE.value());
    }

    // 見かけの違う落下 (高波など): 予告の粒・当たりの粒・音を替える
    public static void tick(ServerLevel back, TrialCell.Run run, List<ServerPlayer> inside, long now, int every, float damage,
            net.minecraft.core.particles.ParticleOptions warn, net.minecraft.core.particles.ParticleOptions hit, net.minecraft.sounds.SoundEvent sound) {
        long rockAt = run.getLong("rockAt");
        if (rockAt > 0) {
            BlockPos at = (BlockPos) run.data.get("rockPos");
            if (now < rockAt) {
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    TrialFx.particles(back, run.group, warn, at.getX() + 0.5 + Math.cos(a) * 1.5, at.getY() + 0.1, at.getZ() + 0.5 + Math.sin(a) * 1.5,
                            1, 0, 0, 0, 0);
                }
            } else {
                TrialFx.particles(back, run.group, hit, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0, 0, 0, 0);
                TrialFx.sound(back, run.group, at, sound, 1.0f, 1.2f);
                for (ServerPlayer p : inside) {
                    if (Math.hypot(p.getX() - (at.getX() + 0.5), p.getZ() - (at.getZ() + 0.5)) < 1.7 && Math.abs(p.getY() - at.getY()) < 3) {
                        p.hurt(p.damageSources().generic(), damage);
                    }
                }
                run.put("rockAt", 0L);
            }
        } else if (now >= run.getLong("nextRock") && !inside.isEmpty()) {
            ServerPlayer target = inside.get(new Random(run.seed ^ now).nextInt(inside.size()));
            run.put("rockPos", target.blockPosition());
            run.put("rockAt", now + WARN);
            run.put("nextRock", now + every);
        }
    }
}
