package jp.revati.skyabyss.client.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.combat.TelegraphStartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 技の予告の床の印 (decal) のクライアント側の状態。サーバーからの開始・取り消しを受け、描画 (TelegraphRenderer) が読む。
// 時間はクライアントの tick で数える (パケットの ageAtSend をいまの時刻から引いて、始まりの時刻にする)。
// telegraphStyle が PARTICLES / BOTH なら、ここで点線の粒をクライアントの側で出す (サーバーは粒を送らない)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TelegraphClient {
    // 当たったあとの光が消えるまでの tick 数
    static final int FLASH_TICKS = 8;

    static final class Decal {
        final int id;
        final TelegraphShape shape;
        final double floor;
        final long localStart;
        final int warn;
        final int active;
        final int rgb;
        final Telegraph.Style style;
        @Nullable
        final UUID target;
        final Map<Long, Float> heights = new HashMap<>();

        Decal(int id, TelegraphShape shape, double floor, long localStart, int warn, int active, int rgb,
                Telegraph.Style style, @Nullable UUID target) {
            this.id = id;
            this.shape = shape;
            this.floor = floor;
            this.localStart = localStart;
            this.warn = warn;
            this.active = active;
            this.rgb = rgb;
            this.style = style;
            this.target = target;
        }

        int lifetime() {
            return warn + Math.max(0, active) + FLASH_TICKS;
        }

        // その場所の床の高さ (印の floor の近くにある、いちばん上の固い面)。段差は 1.6 まで、それより高い壁・柱は無視する
        float ground(ClientLevel level, double x, double z) {
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            long key = BlockPos.asLong(bx, 0, bz);
            Float cached = heights.get(key);
            if (cached != null) {
                return cached;
            }
            float h = (float) floor;
            if (level.hasChunk(bx >> 4, bz >> 4)) {
                int top = Mth.floor(floor + 0.01) + 1;
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, top, bz);
                for (int y = top; y >= top - 5; y--) {
                    pos.setY(y);
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shapeHere = state.getCollisionShape(level, pos);
                    if (!shapeHere.isEmpty()) {
                        double surface = y + shapeHere.max(net.minecraft.core.Direction.Axis.Y);
                        if (surface <= floor + 1.6) {
                            h = (float) surface;
                            break;
                        }
                    }
                }
            }
            heights.put(key, h);
            return h;
        }
    }

    private static final Map<Integer, Decal> DECALS = new HashMap<>();
    @Nullable
    private static ClientLevel levelOf;

    private TelegraphClient() {
    }

    public static void start(TelegraphStartPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        TelegraphShape shape = p.shape();
        if (level == null || shape == null) {
            return;
        }
        if (levelOf != level) {
            DECALS.clear();
            levelOf = level;
        }
        // 長さが極端なものは壊れた入力として捨てる (予告は数秒)
        if (p.warn() < 1 || p.warn() > 1200 || p.active() < 0 || p.active() > 1200) {
            return;
        }
        DECALS.put(p.id(), new Decal(p.id(), shape, p.floor(), level.getGameTime() - p.ageAtSend(), p.warn(), p.active(), p.rgb(),
                Telegraph.Style.byId(p.style()), p.target()));
    }

    public static void cancel(int id) {
        DECALS.remove(id);
    }

    static Iterable<Decal> decals() {
        return DECALS.values();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DECALS.clear();
        levelOf = null;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            DECALS.clear();
            levelOf = null;
            return;
        }
        if (DECALS.isEmpty()) {
            return;
        }
        if (levelOf != level) {
            DECALS.clear();
            levelOf = level;
            return;
        }
        long now = level.getGameTime();
        boolean particles = Config.TELEGRAPH_STYLE.get() != Config.TelegraphStyle.DECAL;
        for (Iterator<Decal> it = DECALS.values().iterator(); it.hasNext();) {
            Decal d = it.next();
            long t = now - d.localStart;
            if (t > d.lifetime()) {
                it.remove();
            } else if (particles && t < d.warn + Math.max(0, d.active) && t % 2 == 0) {
                sprinkle(level, d, (int) t);
            }
        }
    }

    // 点線の粒 (旧来の見せ方)。輪郭と、満ちていく縁
    private static void sprinkle(ClientLevel level, Decal d, int t) {
        double progress = Math.min(1.0, (double) t / d.warn);
        TelegraphShape current = t >= d.warn && d.active > 0 ? d.shape.at((double) (t - d.warn) / d.active) : d.shape;
        int r = lerp((d.rgb >> 16) & 0xFF, 255, progress * 0.55);
        int g = lerp((d.rgb >> 8) & 0xFF, 255, progress * 0.55);
        int b = lerp(d.rgb & 0xFF, 255, progress * 0.55);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(r / 255f, g / 255f, b / 255f), (float) (1.0 + 0.5 * progress));
        double[] bounds = current.bounds();
        double spacing = Math.max(0.9, Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]) / 24.0);
        TelegraphShape.PointSink sink = (x, z) -> level.addParticle(dust, x, d.ground(level, x, z) + 0.1, z, 0, 0, 0);
        current.outline(sink, spacing);
        if (t < d.warn) {
            current.edge(progress, sink, spacing);
        }
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
