package jp.revati.skyabyss.abyss.arts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 光の術の灯り (docs/design/36 の 5.1・5.4)。世界に一つずつの点の光で、ブロックは置かない (壊されず、消すのは時間だけ)。
// 中心が 15、半径の端が 0 へ直線に下がる。ほかの人の灯りも数える (共有)。一人は MAX_PER_PLAYER まで (超えると一番古いものが消える)
// 闇の声 (story/ch5/AbyssPressure) は lightAt / lampAt を読む。サーバーのスレッドだけで触る
public final class AbyssLight {
    public static final int MAX_PER_PLAYER = 3;

    private record Lamp(ResourceKey<Level> dim, Vec3 at, double radius, long until, UUID owner) {
    }

    private static final List<Lamp> LAMPS = new ArrayList<>();

    private AbyssLight() {
    }

    // 灯りを置く。until は overworld の時刻 (ゲームの tick)
    public static void add(Level level, BlockPos pos, double radius, long until, UUID owner) {
        int mine = 0;
        for (Lamp l : LAMPS) {
            if (l.owner.equals(owner)) {
                mine++;
            }
        }
        for (int i = 0; mine >= MAX_PER_PLAYER && i < LAMPS.size(); i++) {
            if (LAMPS.get(i).owner.equals(owner)) {
                LAMPS.remove(i--);
                mine--;
            }
        }
        LAMPS.add(new Lamp(level.dimension(), pos.getCenter(), radius, until, owner));
    }

    // 点の灯りだけの明るさ (0〜15)
    public static int lampAt(Level level, BlockPos pos) {
        Vec3 c = pos.getCenter();
        int best = 0;
        for (Lamp l : LAMPS) {
            if (!l.dim.equals(level.dimension())) {
                continue;
            }
            double d = c.distanceTo(l.at);
            if (d < l.radius) {
                best = Math.max(best, (int) Math.round(15 * (1 - d / l.radius)));
            }
        }
        return best;
    }

    // ワールドの明るさと点の灯りの大きい方 (0〜15)
    public static int lightAt(Level level, BlockPos pos) {
        return Math.max(level.getMaxLocalRawBrightness(pos), lampAt(level, pos));
    }

    // 期限が切れた灯りを消す (now は overworld の時刻)
    public static void tick(long now) {
        LAMPS.removeIf(l -> l.until <= now);
    }

    // 灯喰い (docs/design/36 の 4): at から r 以内の点の灯りを消す。消した数
    public static int snuffNear(Level level, Vec3 at, double r) {
        int n = 0;
        for (Iterator<Lamp> it = LAMPS.iterator(); it.hasNext(); ) {
            Lamp l = it.next();
            if (l.dim.equals(level.dimension()) && l.at.distanceTo(at) <= r) {
                it.remove();
                n++;
            }
        }
        return n;
    }

    // at から r 以内で一番近い点の灯りの位置 (なければ null)
    @Nullable
    public static Vec3 nearestLamp(Level level, Vec3 at, double r) {
        Vec3 best = null;
        double bestD = r;
        for (Lamp l : LAMPS) {
            if (l.dim.equals(level.dimension()) && l.at.distanceTo(at) < bestD) {
                bestD = l.at.distanceTo(at);
                best = l.at;
            }
        }
        return best;
    }

    public static void clear() {
        LAMPS.clear();
    }

    public static int count() {
        return LAMPS.size();
    }
}
