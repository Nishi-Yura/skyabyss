package jp.revati.skyabyss.combat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

// 「一方向に回り続けるだけ」を見張る部品 (四つの心臓が一つずつ持つ。docs/design の各心臓の「回り続けを許さない」の節)。
// 毎 tick、心臓の部屋の人の位置の差から、心臓を中心にした回る向き (+1 = 反時計・-1 = 時計) と、その向きに回り続けた長さ (streak) を数える。
//  - 回る速さが速い (MIN_TANGENTIAL = 2.8 マス/秒以上) 向きの続きだけを数える。止まる・逆へ回る・内外へ走るとやり直し (止まれば少しずつ減る)
//  - STRAFE_TICKS (1.2 秒) 続けたら「回っている」。そのあいだ心臓の狙いは、いまの足もとでなく、予告が満ちる頃にいる所 (predict) へ向く。
//    予告は床に見えているので、止まる・逆へ回る・跳ぶ・内外へ逃げるで避けられる (読める)。
// 判定の寸法 (距離・時間) は、ここと HeartPursuit に集めてある。
public final class AntiStrafe {
    public static final int STRAFE_TICKS = 24;
    public static final double MIN_TANGENTIAL = 0.14;

    private static final class Track {
        Vec3 last;
        double vx;
        double vz;
        double omega;
        int streak;
        int dir;
        long seen;
    }

    private final Map<UUID, Track> tracks = new HashMap<>();
    private double cx;
    private double cz;
    private long clock;
    // 試験用: 切ると誰も「回っている」と見なさない (修正前の動きとの比べ用)
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            tracks.clear();
        }
    }

    // 中心と人の位置から、毎 tick 一度だけ更新する (一度に複数回呼んでも、同じ位置なら速さは 0 とみなして減るだけ)
    public void update(double cx, double cz, Collection<? extends Player> players) {
        this.cx = cx;
        this.cz = cz;
        clock++;
        if (!enabled) {
            return;
        }
        for (Player p : players) {
            Track t = tracks.computeIfAbsent(p.getUUID(), k -> new Track());
            t.seen = clock;
            Vec3 pos = p.position();
            if (t.last != null) {
                double dx = pos.x - t.last.x;
                double dz = pos.z - t.last.z;
                if (dx * dx + dz * dz > 9.0) {
                    // 瞬間移動 (復帰・転送): やり直し
                    t.vx = 0;
                    t.vz = 0;
                    t.omega = 0;
                    t.streak = 0;
                    t.dir = 0;
                } else {
                    t.vx = t.vx * 0.55 + dx * 0.45;
                    t.vz = t.vz * 0.55 + dz * 0.45;
                    double rx = pos.x - cx;
                    double rz = pos.z - cz;
                    double r2 = rx * rx + rz * rz;
                    if (r2 > 1.0) {
                        double r = Math.sqrt(r2);
                        double tang = (rx * t.vz - rz * t.vx) / r;
                        double rad = (rx * t.vx + rz * t.vz) / r;
                        t.omega = t.omega * 0.7 + ((rx * dz - rz * dx) / r2) * 0.3;
                        int sign = tang > 0 ? 1 : -1;
                        if (Math.abs(tang) >= MIN_TANGENTIAL && Math.abs(tang) >= 0.6 * Math.abs(rad)) {
                            if (sign == t.dir) {
                                t.streak++;
                            } else {
                                t.dir = sign;
                                t.streak = 1;
                            }
                        } else {
                            t.streak = Math.max(0, t.streak - 2);
                        }
                    }
                }
            }
            t.last = pos;
        }
        if (clock % 200 == 0) {
            for (Iterator<Track> it = tracks.values().iterator(); it.hasNext();) {
                if (clock - it.next().seen > 100) {
                    it.remove();
                }
            }
        }
    }

    public void reset() {
        tracks.clear();
    }

    public int streak(Player p) {
        Track t = tracks.get(p.getUUID());
        return t == null ? 0 : t.streak;
    }

    public boolean isStrafing(Player p) {
        return streak(p) >= STRAFE_TICKS;
    }

    // 回っている向き (+1 反時計 / -1 時計 / 0 回っていない)
    public int direction(Player p) {
        Track t = tracks.get(p.getUUID());
        return t == null || t.streak < STRAFE_TICKS ? 0 : t.dir;
    }

    // 一番長く回り続けている人 (回っている人がいなければ null)
    @Nullable
    public Player strafer(Collection<? extends Player> players, int minStreak) {
        Player best = null;
        int bestStreak = minStreak - 1;
        for (Player p : players) {
            int s = streak(p);
            if (s > bestStreak) {
                bestStreak = s;
                best = p;
            }
        }
        return best;
    }

    // ticks 後にいる所 (回っているなら、同じ半径のまま回り続けた先。そうでなければ今の足もと)
    public Vec3 predict(Player p, int ticks) {
        Track t = tracks.get(p.getUUID());
        Vec3 pos = p.position();
        if (t == null || t.streak < STRAFE_TICKS) {
            return pos;
        }
        double rx = pos.x - cx;
        double rz = pos.z - cz;
        double r = Math.hypot(rx, rz);
        if (r < 1.0) {
            return pos;
        }
        double theta = Math.atan2(rz, rx) + t.omega * ticks;
        return new Vec3(cx + Math.cos(theta) * r, pos.y, cz + Math.sin(theta) * r);
    }

    // 狙い: 回っていれば ticks 後の先、そうでなければ足もと
    public Vec3 aim(Player p, int ticks) {
        return predict(p, ticks);
    }

    // 心臓から見た、ticks 後にいる角度 (ラジアン)
    public double angleAt(Player p, int ticks) {
        Vec3 v = predict(p, ticks);
        return Math.atan2(v.z - cz, v.x - cx);
    }

    // 回る速さ (ラジアン / tick。符号つき)。回っていなければ 0
    public double omega(Player p) {
        Track t = tracks.get(p.getUUID());
        return t == null || t.streak < STRAFE_TICKS ? 0 : t.omega;
    }
}
