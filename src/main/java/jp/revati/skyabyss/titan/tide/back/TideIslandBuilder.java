package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

// 潮殻の背の島を建てる (08 の 21 章「巨神の形の背中」、26 の 9 章)。最初の乗り込みと、配置の版が変わったときに一度だけ動く。
// 順番: 巨神の形の亀 (TideTurtle。中は詰まっている) → 浜 → 潮門・到着の段・見送りの段・潮門の廊下 → 回廊 → 予約の間の輪 (upper) →
//       広間 (月の窓の縦穴) → 回り道 → 珊瑚の庭 (TideGardenBuilder) → 潮見の間 (TideWatchBuilder) →
//       首の廊下と干潟 (TideFlatsBuilder) → 螺旋の階段と殻の底 → 掘った所の内壁を潮の民の石に (skin) → 甲羅の上へ上る段 → 頂の塔の中 → 光の井戸 →
//       休み場・碑文・石・抜け道の床
// 潮で動く所 (水、門の開け閉め、庭の道の形、潮見の間の水位、巣) は試練の側 (TideBackTrials ほか) が置く
public final class TideIslandBuilder {
    private TideIslandBuilder() {
    }

    public static void build(TideKit kit) {
        long t0 = System.currentTimeMillis();
        int placed = TideTurtle.place(kit);
        SkyAbyss.LOGGER.info("Placed the tide turtle: {} blocks in {} ms", placed, System.currentTimeMillis() - t0);
        buildRest(kit);
    }

    // 亀の体を置いたあと (部屋を掘る・中身を建てる)。起動時は体を何 tick かに分けて置いてから呼ぶ (BackBuildJobs)
    public static void buildRest(TideKit kit) {
        jp.revati.skyabyss.titan.BackBuildJobs.Steps s = new jp.revati.skyabyss.titan.BackBuildJobs.Steps("tide back island");
        restSteps(s, kit);
        while (!s.step(Long.MAX_VALUE)) {
            // 最後まで
        }
    }

    // buildRest の手順 (順は同じ。tick に分けて進められる)
    public static void restSteps(jp.revati.skyabyss.titan.BackBuildJobs.Steps s, TideKit kit) {
        // 仕上げが触る範囲 (背の監査の範囲) のチャンクを先に読み込む (読みに行くと、その場で tick が止まる)
        s.thenJob("load area", () -> jp.revati.skyabyss.titan.BackBuildJobs.loadArea(kit.level(), -190, -245, 190, 170));
        // 体の中は建てない (外見だけ。docs/design/32 の 2.5)。外から見える所の手直しと、地上へ戻る潮門の到着の段だけ
        s.then("trenches", () -> fillTrenches(kit));
        // 潮門の段と廊下は建てない。形に残る潮門の枠と光る面は継ぎ当てで閉じる (titan/BackSeal。2026-11-07「完全に閉じて」)
        s.then("seal", () -> jp.revati.skyabyss.titan.BackSeal.apply(kit.level(), "tide_back"));
        s.then("wells", () -> fillWells(kit));
        // 心臓の間 (殻の底) と休み場 heart は体の外の封じた部屋 (trial/raid/InstanceArenas)
        jp.revati.skyabyss.trial.raid.InstanceArenas.addHeartSteps(s, kit.level(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
    }

    // 甲羅の台地の上の、幅 1 の溝 (両隣が 2 以上高い。模型の薄い壁の抜けたあと) を、深さ 1 になるまで埋める (落ちると戻れない穴を作らない)
    static void fillTrenches(TideKit kit) {
        int x0 = -130;
        int z0 = -175;
        int w = 261;
        int d = 340;
        int[][] h = new int[w][d];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                int t = TideTurtle.top(x0 + i, z0 + j);
                h[i][j] = t == Integer.MIN_VALUE ? -1 : t;
            }
        }
        int filled = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i < w - 1; i++) {
                for (int j = 1; j < d - 1; j++) {
                    int t = h[i][j];
                    if (t < 64) {
                        continue;
                    }
                    int a = Math.min(h[i - 1][j], h[i + 1][j]);
                    int b = Math.min(h[i][j - 1], h[i][j + 1]);
                    int to = Math.max(a >= t + 2 ? a - 1 : t, b >= t + 2 ? b - 1 : t);
                    // 四方が 1 以上高いくぼみ (1 マスの凹み) も、いちばん低い隣の高さまで埋める
                    int m4 = Math.min(a, b);
                    if (m4 >= t + 1) {
                        to = Math.max(to, m4);
                    }
                    if (to <= t) {
                        continue;
                    }
                    BlockState top = TideTurtle.at(x0 + i, Math.min(t, TideTurtle.maxY()), z0 + j);
                    if (top == null) {
                        top = Blocks.TUFF.defaultBlockState();
                    }
                    for (int y = t + 1; y <= to; y++) {
                        kit.set(x0 + i, y, z0 + j, top);
                    }
                    h[i][j] = to;
                    filled++;
                }
            }
        }
        SkyAbyss.LOGGER.info("Filled {} trench cells on the shell terraces", filled);
    }

    // 亀の体 (珊瑚・遺跡の形) の凹みにできた小さな縦穴を埋める (詰み監査)
    static void fillWells(TideKit kit) {
        jp.revati.skyabyss.titan.WellFiller.fill(kit.level(), TideTurtle.minX(), TideTurtle.maxX(), TideTurtle.minZ(), TideTurtle.maxZ(),
                TideIslandBuilder::surface, TOP_Y - 2);
    }

    // なめらかな値の雑音 (0〜1)。格子の点に乱数を置き、間をなめらかにつなぐ
    static double noise(double x, double z, double scale, int seed) {
        double fx = x / scale;
        double fz = z / scale;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = fx - ix;
        double tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(ix, iz, seed);
        double b = lattice(ix + 1, iz, seed);
        double c = lattice(ix, iz + 1, seed);
        double d = lattice(ix + 1, iz + 1, seed);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private static double lattice(int x, int z, int seed) {
        long h = x * 374761393L + z * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    // ---------------------------------------------------------------- 潮門 (モデルの左後ろの門)、到着の段、見送りの段、潮門の廊下
    static final boolean RETURN_GATE = false;

    // 地上へ戻る見送りの段 (いちばん下の段とその先の水) に入っているか
    public static boolean inReturnGate(double x, double y, double z) {
        // 潮門の見送りの段はもうない (地上へは待機場所の帰りの石で。trial/ArrivalDeck。2026-11-07)
        return RETURN_GATE && x >= GATE_X - 1 && x < GATE_X + 2 && z >= GATE_Z && z < GATE_Z + 2 && y >= GATE_Y0 - 0.5 && y < GATE_Y1;
    }

    private static int surface(int x, int z) {
        return Math.max(TideTurtle.top(x, z), shellTop(x, z));
    }

    private static int groundTop(int x, int z) {
        int top = shellTop(x, z);
        return top == Integer.MIN_VALUE ? TideTurtle.top(x, z) : top;
    }

    // ---------------------------------------------------------------- 休み場・碑文・潮鳴り石・抜け道の床
    // 看板は、試練の入口の一言 (3 枚)・その補い (栓の色の読み方・戻しの栓・広間の案内。3 枚) と碑文 (6 枚)。使い方は最初の一度だけの囁き (TideWhispers)、形・光・音で伝える
    public static final List<Inscription> ENTRANCE_SIGNS = List.of(
            new Inscription("sign.skyabyss.tide_garden", new BlockPos(-32, PLATFORM_TOP + 2, -6), Direction.SOUTH),
            new Inscription("sign.skyabyss.tide_watch", new BlockPos(40, HUB_LEDGE + 2, -14), Direction.EAST),
            new Inscription("sign.skyabyss.tide_flats", new BlockPos(3, TUNNEL_FLOOR + 2, -64), Direction.SOUTH),
            // 潮見の間: 栓の色の読み方 (入口の隣)・戻しの栓の上。広間: 三つの試練の一言ずつ (どこへ行って何をするか)
            new Inscription("sign.skyabyss.tide_watch_color", new BlockPos(40, HUB_LEDGE + 2, -17), Direction.EAST),
            new Inscription("sign.skyabyss.tide_watch_reset", new BlockPos(46, HUB_LEDGE + 3, -6), Direction.NORTH));

}
