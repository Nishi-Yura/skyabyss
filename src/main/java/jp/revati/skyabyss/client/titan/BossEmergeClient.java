package jp.revati.skyabyss.client.titan;

import java.util.HashMap;
import java.util.Map;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

// 主が地の中から這い出る見た目 (combat/BossEmerge)。出る間は、体を床の下へ沈めて描く (床のブロックが下を隠す)。
// 出方は主ごとの鍵の列 (出る長さの割合 → 見えている高さの割合・回り・前のめり)。鍵のあいだは滑らかにつなぐ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BossEmergeClient {
    private record Run(float start, int ticks, float[][] keys) {
    }

    private static final Map<Integer, Run> RUNS = new HashMap<>();

    // {割合, 見えている高さ (1 より大きいと地の上に浮く), 回り (度), 前のめり (度) [, 横の大きさ, 縦の大きさ]}
    private static final float[][] KING = {{0, 0, 0, 0}, {0.1f, 0.03f, 0, 0}, {0.82f, 0.9f, 0, 0}, {1, 1, 0, 0}};
    private static final float[][] RIB = {{0, 0, 0, 0}, {0.15f, 0.22f, 0, 8}, {0.25f, 0.18f, 0, 4}, {0.42f, 0.48f, 0, 10}, {0.5f, 0.42f, 0, 5},
            {0.68f, 0.74f, 0, 12}, {0.76f, 0.68f, 0, 6}, {0.88f, 1.04f, 0, 0}, {1, 1, 0, 0}};
    private static final float[][] SALT = {{0, 0, 360, 0}, {0.35f, 0.3f, 250, 0}, {0.7f, 0.97f, 40, 0}, {0.85f, 1.02f, -8, 0}, {1, 1, 0, 0}};
    private static final float[][] STORM = {{0, 0, 0, 0}, {0.15f, 0.12f, 0, 0}, {0.2f, 0.03f, 0, 0}, {0.3f, 0.14f, 0, 0}, {0.35f, 0.04f, 0, 0},
            {0.45f, 0.17f, 0, 0}, {0.5f, 0.05f, 0, 0}, {0.6f, 1.3f, 0, -10}, {0.74f, 0.94f, 0, 6}, {0.86f, 1.03f, 0, 0}, {1, 1, 0, 0}};
    private static final float[][] ASH = {{0, 0, 0, 40}, {0.3f, 0.28f, 0, 38}, {0.72f, 0.88f, 0, 26}, {0.88f, 1.0f, 0, -6}, {1, 1, 0, 0}};
    // 空から落ちてくる: 高い所で一瞬止まり、一気に落ちて潰れ、弾んで立つ
    private static final float[][] DROP = {{0, 3.2f, 0, 0, 1, 1}, {0.5f, 2.9f, 0, -8, 1, 1}, {0.72f, 1.0f, 0, 6, 1.12f, 0.78f}, {0.8f, 1.0f, 0, 0, 0.95f, 1.08f},
            {0.9f, 1.0f, 0, 0, 1.02f, 0.97f}, {1, 1, 0, 0, 1, 1}};
    // 糸で天井から降りる: ゆっくり下り、一度止まって揺れ、糸が切れて落ちる
    private static final float[][] THREAD = {{0, 2.8f, 0, 0, 1, 1}, {0.45f, 1.9f, 25, 0, 1, 1}, {0.55f, 1.95f, -20, 0, 1, 1}, {0.65f, 1.9f, 10, 0, 1, 1},
            {0.78f, 1.0f, 0, 0, 1.1f, 0.82f}, {0.88f, 1.0f, 0, 0, 0.96f, 1.05f}, {1, 1, 0, 0, 1, 1}};
    // 空から回りながらゆっくり降りる
    private static final float[][] SKY = {{0, 2.6f, 540, 0, 1, 1}, {0.7f, 1.25f, 90, 0, 1, 1}, {0.85f, 0.98f, 0, 0, 1.04f, 0.95f}, {1, 1, 0, 0, 1, 1}};
    // 結晶のように小さく芽を出し、回りながら育つ
    private static final float[][] GROW = {{0, 1, 0, 0, 0.04f, 0.04f}, {0.3f, 1, 60, 0, 0.3f, 0.45f}, {0.7f, 1, 10, 0, 1.08f, 1.1f}, {0.85f, 1, 0, 0, 0.96f, 0.96f},
            {1, 1, 0, 0, 1, 1}};
    // 紙が開くように、平たい所から縦に広がる
    private static final float[][] UNFOLD = {{0, 1, 0, 0, 1, 0.02f}, {0.25f, 1, 0, 12, 1, 0.08f}, {0.7f, 1, 0, -8, 1, 1.14f}, {0.82f, 1, 0, 3, 1, 0.94f},
            {1, 1, 0, 0, 1, 1}};
    // 地から噴き上がる: 地の下で溜めて、跳ね上がって着く
    private static final float[][] ERUPT = {{0, 0, 0, 0, 1, 1}, {0.5f, 0.08f, 0, 0, 1, 1}, {0.62f, 1.3f, 0, 0, 0.9f, 1.15f}, {0.75f, 0.96f, 0, 0, 1.08f, 0.9f},
            {0.86f, 1.03f, 0, 0, 1, 1}, {1, 1, 0, 0, 1, 1}};

    private BossEmergeClient() {
    }

    private static float now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    public static void start(int entity, String style, int ticks) {
        float[][] keys = switch (style) {
            case "lord_rib" -> RIB;
            case "lord_salt" -> SALT;
            case "lord_storm" -> STORM;
            case "lord_ash" -> ASH;
            case "drop" -> DROP;
            case "thread" -> THREAD;
            case "sky" -> SKY;
            case "grow" -> GROW;
            case "unfold" -> UNFOLD;
            case "erupt" -> ERUPT;
            default -> KING;
        };
        RUNS.put(entity, new Run(now(), Math.max(1, ticks), keys));
    }

    public static boolean active(Entity e) {
        Run r = RUNS.get(e.getId());
        if (r != null && now() - r.start() >= r.ticks()) {
            RUNS.remove(e.getId());
            return false;
        }
        return r != null;
    }

    // {沈める高さ (ブロック。負なら浮かせる), 回り (度), 前のめり (度), 横の大きさ, 縦の大きさ}。出ていなければ null
    public static float[] pose(Entity e, float visualHeight) {
        Run r = RUNS.get(e.getId());
        if (r == null) {
            return null;
        }
        float p = (now() - r.start()) / r.ticks();
        if (p >= 1f || p < -0.5f) {
            RUNS.remove(e.getId());
            return null;
        }
        p = Math.max(0f, p);
        float[][] k = r.keys();
        int i = 0;
        while (i < k.length - 2 && p > k[i + 1][0]) {
            i++;
        }
        float f = Mth.clamp((p - k[i][0]) / Math.max(1e-4f, k[i + 1][0] - k[i][0]), 0f, 1f);
        f = f * f * (3f - 2f * f);
        float shown = Mth.lerp(f, k[i][1], k[i + 1][1]);
        float sx = Mth.lerp(f, col(k[i], 4), col(k[i + 1], 4));
        float sy = Mth.lerp(f, col(k[i], 5), col(k[i + 1], 5));
        float sink = shown > 1f ? (1f - shown) * visualHeight : (1f - shown) * visualHeight * 1.08f;
        return new float[] {sink, Mth.lerp(f, k[i][2], k[i + 1][2]), Mth.lerp(f, k[i][3], k[i + 1][3]), sx, sy};
    }

    private static float col(float[] key, int i) {
        return key.length > i ? key[i] : 1f;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RUNS.clear();
    }
}
