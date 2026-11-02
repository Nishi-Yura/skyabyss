package jp.revati.skyabyss.story.ch6.valley;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 星灯り (docs/design/46 の 4.2)。星の灯で置く星の印と、主の間の灯りの台の光。世界に一つずつの点の光 (中心と半径と終わりの時刻)。
//   人の印: 一人 MAX_PER_PLAYER まで (超えると一番古いものが消える)、PLAYER_RADIUS・PLAYER_TICKS。足もとに星の印のブロック (本当に照らす)
//   台の光: LampPostBlock が足す (半径 5)
// 読むもの: 影の橋 (照らされると消える)・影の綴じ手 (引き出される)・影抱き (固まる)。サーバーのスレッドだけで触る
public final class StarLamps {
    public static final int MAX_PER_PLAYER = 2;
    public static final double PLAYER_RADIUS = 6.0;
    public static final int PLAYER_TICKS = 600;

    private record Lamp(ResourceKey<Level> dim, BlockPos at, double radius, long until, UUID owner, boolean post) {
        Vec3 center() {
            return at.getCenter();
        }
    }

    private static final List<Lamp> LAMPS = new ArrayList<>();
    private static final UUID POST = new UUID(0, 0);

    private StarLamps() {
    }

    // 人が足もとに置く (星の印のブロックも置く)。古いものは消える
    public static void place(ServerLevel level, BlockPos feet, UUID owner) {
        int mine = 0;
        for (Lamp l : LAMPS) {
            if (!l.post && l.owner.equals(owner)) {
                mine++;
            }
        }
        for (int i = 0; mine >= MAX_PER_PLAYER && i < LAMPS.size(); i++) {
            Lamp l = LAMPS.get(i);
            if (!l.post && l.owner.equals(owner)) {
                LAMPS.remove(i--);
                fade(level, l);
                mine--;
            }
        }
        LAMPS.add(new Lamp(level.dimension(), feet, PLAYER_RADIUS, level.getGameTime() + PLAYER_TICKS, owner, false));
        if (level.getBlockState(feet).canBeReplaced()) {
            level.setBlock(feet, ValleyBlocks.STAR_MARK.get().defaultBlockState(), 3);
        }
        level.playSound(null, feet, ValleyBlocks.STAR_LAMP_PLACE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        ring(level, feet.getCenter(), PLAYER_RADIUS, 40);
    }

    static void addPost(ServerLevel level, BlockPos pos, double radius, long until) {
        removePost(level, pos);
        LAMPS.add(new Lamp(level.dimension(), pos, radius, until, POST, true));
    }

    static void removePost(ServerLevel level, BlockPos pos) {
        LAMPS.removeIf(l -> l.post && l.at.equals(pos) && l.dim.equals(level.dimension()));
    }

    static boolean postLit(ServerLevel level, BlockPos pos) {
        for (Lamp l : LAMPS) {
            if (l.post && l.at.equals(pos) && l.dim.equals(level.dimension()) && l.until > level.getGameTime()) {
                return true;
            }
        }
        return false;
    }

    // 星灯りの中か
    public static boolean inLight(Level level, Vec3 p) {
        for (Lamp l : LAMPS) {
            if (l.dim.equals(level.dimension()) && l.center().distanceToSqr(p) < l.radius * l.radius) {
                return true;
            }
        }
        return false;
    }

    public static boolean inLight(Level level, BlockPos p) {
        return inLight(level, p.getCenter());
    }

    // 一番近い光の中心 (なければ null)
    @org.jetbrains.annotations.Nullable
    public static Vec3 nearest(Level level, Vec3 p, double within) {
        Vec3 best = null;
        double bd = within * within;
        for (Lamp l : LAMPS) {
            if (!l.dim.equals(level.dimension())) {
                continue;
            }
            double d = l.center().distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = l.center();
            }
        }
        return best;
    }

    public static int count(Level level) {
        int n = 0;
        for (Lamp l : LAMPS) {
            if (l.dim.equals(level.dimension())) {
                n++;
            }
        }
        return n;
    }

    // 時間切れを消し、光の輪を見せる (ValleyTicker から 10 tick ごと)
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Iterator<Lamp> it = LAMPS.iterator(); it.hasNext(); ) {
            Lamp l = it.next();
            if (!l.dim.equals(level.dimension())) {
                continue;
            }
            if (l.until <= now) {
                it.remove();
                fade(level, l);
                continue;
            }
            if (!l.post && (now / 10) % 2 == 0) {
                ring(level, l.center(), l.radius, 14);
            }
        }
    }

    private static void fade(ServerLevel level, Lamp l) {
        if (!l.post && level.getBlockState(l.at).is(ValleyBlocks.STAR_MARK.get())) {
            level.removeBlock(l.at, false);
            level.playSound(null, l.at, ValleyBlocks.STAR_LAMP_FADE.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }
    }

    // 光の輪 (地面の高さで、半径の縁に星の粒)
    static void ring(ServerLevel level, Vec3 c, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 + level.getGameTime() * 0.02;
            level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y - 0.3, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.0);
        }
    }

    // 開発用・自己テスト用: 全部消す
    public static void clear(ServerLevel level) {
        for (Iterator<Lamp> it = LAMPS.iterator(); it.hasNext(); ) {
            Lamp l = it.next();
            if (l.dim.equals(level.dimension())) {
                it.remove();
                fade(level, l);
            }
        }
    }
}
