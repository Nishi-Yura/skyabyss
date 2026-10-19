package jp.revati.skyabyss.client.titan;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.Config.MarkerStyle;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.end.BoardGlow;
import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// 乗り込み口の目印・戻る場所の光の、3 つの見せ方 (A/B/C。Config.BOARD_MARKER・RETURN_MARKER。CURRENT のときは BoardBeamClient の柱と枠のまま)。
// どれも硬い直線の枠は使わず、柔らかい光・床の紋様・浮かぶ欠片・点の連なりで見せる。巨神ごとの形の言葉 (紋様): 岩鯨 = 波の輪、潮殻 = 巻き貝の渦、雷翼 = 稲妻の星、余燼 = 炎の花弁。
// 描くのは加算合成の色つき面だけ (BoardBeamClient が束ねて 1 回で描く)。粒はクライアントだけで出す (サーバーの粒とは別)。
public final class MarkerArt {
    // 開発・選定用: 設定を書き換えずに切り替える (null = 設定のまま)
    public static MarkerStyle boardOverride;
    public static MarkerStyle returnOverride;
    // 戻る光の巨神 (背中の外で見るとき用)
    public static String returnTitanOverride;

    private MarkerArt() {
    }

    public static MarkerStyle style(boolean ret) {
        MarkerStyle o = ret ? returnOverride : boardOverride;
        if (o != null) {
            return o;
        }
        try {
            return ret ? Config.RETURN_MARKER.get() : Config.BOARD_MARKER.get();
        } catch (RuntimeException e) {
            return MarkerStyle.CURRENT;
        }
    }

    public static boolean isReturn(TitanEndNetwork.Beam b) {
        return b.key().startsWith("return_");
    }

    // 戻る光のときの巨神 (背中の中なら今いる背中の巨神)
    static String returnTitan(Minecraft mc) {
        if (returnTitanOverride != null) {
            return returnTitanOverride;
        }
        String t = mc.level == null ? null : Titans.titanOfBack(mc.level.dimension());
        return t == null ? Titans.STONE_WHALE : t;
    }

    // ---------------------------------------------------------------- 選定・撮影用のコマンド

    public static void shotCommand(String rest) {
        String[] a = rest.trim().split("\\s+");
        if (a.length == 0) {
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "board" -> boardOverride = MarkerStyle.valueOf(a[1].toUpperCase(Locale.ROOT));
            case "return" -> returnOverride = MarkerStyle.valueOf(a[1].toUpperCase(Locale.ROOT));
            case "titan" -> returnTitanOverride = a[1];
            case "clear" -> BoardBeamClient.clearAll();
            case "fake" -> {
                if (a[1].equals("board")) {
                    String titan = a[2];
                    double x = Double.parseDouble(a[3]);
                    double y = Double.parseDouble(a[4]);
                    double z = Double.parseDouble(a[5]);
                    float yaw = Float.parseFloat(a[6]);
                    BoardBeamClient.receive(fakeBoard(titan, x, y, z, yaw));
                } else {
                    double x = Double.parseDouble(a[2]);
                    double y = Double.parseDouble(a[3]);
                    double z = Double.parseDouble(a[4]);
                    BoardBeamClient.receive(new TitanEndNetwork.Beam("return_offer", 0xC8FFE0, x, y + 1.0, z, 1.2f, 1.0f, 1.2f, 0f, y, y + 16, 0.55f, 200000));
                }
            }
            default -> {
            }
        }
    }

    // 各巨神の乗り込み口の寸法に近い偽の知らせ (y = 入口の床)。岩鯨・雷翼は縦の柱、潮殻・余燼は横の口
    private static TitanEndNetwork.Beam fakeBoard(String titan, double x, double y, double z, float yaw) {
        int rgb = BoardGlow.colour(titan);
        return switch (titan) {
            case Titans.TIDE_SHELL -> new TitanEndNetwork.Beam(titan, rgb, x, y + 2.3, z, 3.0f, 2.5f, 3.5f, yaw, y - 6, y + 46, 1.6f, 200000);
            case Titans.EMBER -> new TitanEndNetwork.Beam(titan, rgb, x, y + 3.0, z, 4.0f, 3.2f, 5.0f, yaw, y - 2, y + 44, 1.3f, 200000);
            case Titans.THUNDER_WING -> new TitanEndNetwork.Beam(titan, rgb, x, y + 1.5, z, 3.5f, 2.5f, 3.5f, 0f, y, y + 60, 1.4f, 200000);
            default -> new TitanEndNetwork.Beam(titan, rgb, x, y, z, 1.6f, 2.6f, 1.6f, 0f, y, y + 32, 0.9f, 200000);
        };
    }

    // ---------------------------------------------------------------- 描く

    public static void draw(BufferBuilder buf, BufferBuilder alphaBuf, Matrix4f pose, Vec3 cam, TitanEndNetwork.Beam b, MarkerStyle style, float life, double time, String returnTitan) {
        boolean ret = isReturn(b);
        int rgb = b.rgb();
        if (ret && style == MarkerStyle.B) {
            rgb = BoardGlow.colour(returnTitan);
        }
        G g = new G(buf, alphaBuf, pose, cam, rgb, life);
        if (ret) {
            String titan = returnTitan;
            switch (style) {
                case A -> returnA(g, b, time);
                case B -> returnB(g, b, time, titan);
                default -> returnC(g, b, time);
            }
        } else {
            switch (style) {
                case A -> boardA(g, b, time);
                case B -> boardB(g, b, time);
                default -> boardC(g, b, time);
            }
        }
    }

    // ---------------------------------------------------------------- 入口の形

    private record Entry(double fx, double fz, double sx, double sz, double floor, boolean vertical, double dist, double boost) {
    }

    private static Entry entry(G g, TitanEndNetwork.Beam b) {
        double yaw = Math.toRadians(b.yaw());
        boolean vertical = Math.abs(b.hx() - b.hz()) < 0.01f;
        double floor = vertical ? b.beamFrom() + 0.06 : b.cy() - b.hy() + 0.26;
        double dist = Math.sqrt(sq(b.cx() - g.cam.x) + sq(b.cy() - g.cam.y) + sq(b.cz() - g.cam.z));
        // 遠いほど太く・大きく (見えなくならないように)
        double boost = 1.0 + Math.min(4.0, dist * 0.012);
        return new Entry(-Math.sin(yaw), Math.cos(yaw), Math.cos(yaw), Math.sin(yaw), floor, vertical, dist, boost);
    }

    private static double sq(double v) {
        return v * v;
    }

    // 巨神ごとの紋様 (単位半径の閉じた点列 [x, z])
    static double[][] motif(String titan) {
        int n;
        double[][] p;
        switch (titan) {
            case Titans.TIDE_SHELL -> {
                // 巻き貝の渦 (対数螺旋を 2 周)
                n = 56;
                p = new double[n][];
                for (int i = 0; i < n; i++) {
                    double th = i / (double) (n - 1) * Math.PI * 4.0;
                    double r = 0.10 * Math.exp(0.18 * th);
                    p[i] = new double[] {Math.cos(th) * r, Math.sin(th) * r};
                }
                return p;
            }
            case Titans.THUNDER_WING -> {
                // 稲妻の星 (8 つの尖り)
                n = 17;
                p = new double[n][];
                for (int i = 0; i < n; i++) {
                    double th = Math.PI * 2 * i / (n - 1);
                    double r = (i % 2 == 0) ? 1.0 : 0.5;
                    p[i] = new double[] {Math.cos(th) * r, Math.sin(th) * r};
                }
                return p;
            }
            case Titans.EMBER -> {
                // 炎の花弁 (5 枚)
                n = 80;
                p = new double[n + 1][];
                for (int i = 0; i <= n; i++) {
                    double th = Math.PI * 2 * i / n;
                    double r = 0.45 + 0.55 * Math.abs(Math.cos(2.5 * th));
                    p[i] = new double[] {Math.cos(th) * r, Math.sin(th) * r};
                }
                return p;
            }
            default -> {
                // 波の輪 (岩鯨)。波打つ輪と、内側に肋骨の弧
                n = 96;
                p = new double[n + 1][];
                for (int i = 0; i <= n; i++) {
                    double th = Math.PI * 2 * i / n;
                    double r = 0.85 + 0.15 * Math.sin(8 * th);
                    p[i] = new double[] {Math.cos(th) * r, Math.sin(th) * r};
                }
                return p;
            }
        }
    }

    private static boolean closed(String titan) {
        return !titan.equals(Titans.TIDE_SHELL);
    }

    // ---------------------------------------------------------------- 乗り込み口 A: 柔らかい光の筋 + 床の紋様 + 入口へ縮む輪

    private static void boardA(G g, TitanEndNetwork.Beam b, double time) {
        Entry e = entry(g, b);
        double far = Mth.clamp((e.dist() - 30) / 70.0, 0, 1);
        double rad = Math.max(0.4, b.beamRadius()) + Math.min(4.0, e.dist() * 0.018);
        double h = b.beamTo() - b.beamFrom();
        // 筋: 太さの違う 3 つの層を重ねて芯ほど濃く。光の帯が下から上へ昇っていく。外の 2 層は通常合成 (昼空でも色が乗る)、芯は加算 (発光)
        double[] layer = {1.0, 0.6, 0.3};
        float[] layerA = {0.20f * (1 + (float) far * 0.5f), 0.22f * (1 + (float) far * 0.5f), 0.30f};
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            g.pass(k < 2);
            g.cyl(b.cx(), b.cz(), b.beamFrom(), b.beamTo(), rad * layer[k], rad * layer[k] * 0.8, 1.0f, 0.0f, k == 2 ? 0.35f : 0.0f, 16, 28, t -> {
                double base = Math.pow(1 - t, 1.7) * 0.9 + 0.1 * (1 - t);
                double band = Math.pow(Math.max(0, Math.sin((t * h * 0.09 - time * 0.07) * Math.PI)), 8);
                return layerA[kk] * (base + band * 0.9 * (1 - t));
            });
        }
        g.pass(false);
        // 床の紋様
        double ry = e.floor() + 0.05;
        double r = (Math.max(b.hx(), b.hz()) * 1.35 + 1.0) * (1 + (e.boost() - 1) * 0.45);
        double spin = time * 0.012;
        float pulse = 0.75f + 0.25f * Mth.sin((float) (time / 50.0 * Mth.TWO_PI));
        String titan = b.key();
        g.floorRing(b.cx(), ry, b.cz(), 0.0, r, 0.20f * pulse, 0.0f, 0.15f, 28);
        g.floorRing(b.cx(), ry, b.cz(), r - 0.18 * e.boost(), r, 0.65f * pulse, 0.65f * pulse, 0.3f, 48);
        g.floorRing(b.cx(), ry, b.cz(), r * 0.62 - 0.1 * e.boost(), r * 0.62, 0.40f * pulse, 0.40f * pulse, 0.2f, 40);
        g.floorLine(b.cx(), ry + 0.01, b.cz(), motif(titan), r * 0.82, spin, 0.13 * e.boost(), 0.85f * pulse, 0.45f, closed(titan));
        // 入口へ向かって縮む輪 (2 つ。ゆっくり)
        for (int k = 0; k < 2; k++) {
            double ph = (time / 60.0 + k * 0.5) % 1.0;
            double rr = r * (1.45 - 1.35 * ph);
            float a = (float) Math.sin(Math.PI * ph) * 0.55f;
            g.floorRing(b.cx(), ry + 0.02, b.cz(), rr - 0.12 * e.boost(), rr, a, a, 0.5f, 40);
        }
        // 筋の根もとの明るい芯
        g.billDisc(b.cx(), e.floor() + 0.8, b.cz(), 1.6 * e.boost(), 0.45f * pulse, 0.0f, 0.5f, 0f, 14);
    }

    // ---------------------------------------------------------------- 乗り込み口 B: 欠片の輪 + 細い筋 + 入口へ流れる矢印

    private static void boardB(G g, TitanEndNetwork.Beam b, double time) {
        Entry e = entry(g, b);
        shaft(g, b, e, 0.5, 0.12f, 0.42f);
        double ro = Math.max(b.hx(), b.hz()) + 1.5;
        double k = Math.min(3.5, 1.0 + e.dist() * 0.02);
        double mid = e.vertical() ? b.cy() + 0.5 : b.cy();
        // 欠片の輪 2 段 (逆回り)
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? 9 : 6;
            double rr = ro + ring * 0.9;
            double dir = ring == 0 ? 1 : -1;
            for (int i = 0; i < n; i++) {
                double a = time * 0.016 * dir + Math.PI * 2 * i / n + ring * 0.4;
                double y = mid + (ring == 0 ? -0.3 : 1.7) + Math.sin(time * 0.05 + i * 1.7) * 0.35;
                float pulse = 0.7f + 0.3f * Mth.sin((float) (time * 0.08 + i));
                g.shard(b.cx() + Math.cos(a) * rr, y, b.cz() + Math.sin(a) * rr, 0.62 * k, 0.26 * k, time * 0.04 + i, 0.8f * pulse);
            }
        }
        // 入口へ流れる矢印 (山形)
        double spacing = 2.6;
        double len = 15.0;
        int count = (int) (len / spacing) + 1;
        for (int i = 0; i < count; i++) {
            double t = len - ((time * 0.09 + i * spacing) % len); // 入口からの距離 (入口に向かって縮む)
            double edge = Math.sin(Math.PI * (1 - t / len));
            float a = (float) (Math.pow(edge, 0.7) * 0.75);
            double w = 0.95 * Math.min(1.8, 1 + (e.boost() - 1) * 0.4);
            if (e.vertical()) {
                // 柱: 上へ昇る山形 (視線に向く面の上で、^ の形)
                double y = b.beamFrom() + 1.0 + (len - t) * 1.6;
                double[] r = g.viewRight(b.cx(), y, b.cz());
                g.ribbon(b.cx() - r[0] * w, y - 0.7, b.cz() - r[1] * w, b.cx(), y + 0.1, b.cz(), 0.11 * e.boost(), a);
                g.ribbon(b.cx() + r[0] * w, y - 0.7, b.cz() + r[1] * w, b.cx(), y + 0.1, b.cz(), 0.11 * e.boost(), a);
            } else {
                // 横の口: 体の内側 (-前) へ向かう > の形 (腰の高さで浮かせる)
                double y = e.floor() + 1.0;
                double ax = b.cx() + e.fx() * (b.hz() + t);
                double az = b.cz() + e.fz() * (b.hz() + t);
                g.ribbon(ax, y, az, ax + e.fx() * 0.9 + e.sx() * w, y, az + e.fz() * 0.9 + e.sz() * w, 0.11 * e.boost(), a);
                g.ribbon(ax, y, az, ax + e.fx() * 0.9 - e.sx() * w, y, az + e.fz() * 0.9 - e.sz() * w, 0.11 * e.boost(), a);
            }
        }
        // 入口の足もとの小さな紋様 (欠片の輪の下)
        String titan = b.key();
        g.floorLine(b.cx(), e.floor() + 0.05, b.cz(), motif(titan), ro * 0.9, time * 0.01, 0.1 * e.boost(), 0.45f, 0.4f, closed(titan));
    }

    // ---------------------------------------------------------------- 乗り込み口 C: 巨神の体が応える (灯の点の連なり・提灯・光の綱) + ごく淡い標

    private static void boardC(G g, TitanEndNetwork.Beam b, double time) {
        Entry e = entry(g, b);
        shaft(g, b, e, 0.4, 0.10f, 0.40f);
        double k = Math.min(3.0, 1.0 + e.dist() * 0.02);
        if (e.vertical()) {
            // 柱: 2 本の光の綱が螺旋に昇る + 根もとに提灯の輪
            double r = b.hx() * 0.92;
            for (int strand = 0; strand < 2; strand++) {
                int beads = 34;
                double top = Math.min(b.beamTo(), b.beamFrom() + 26);
                for (int i = 0; i < beads; i++) {
                    double t = i / (double) beads;
                    double ang = strand * Math.PI + t * Math.PI * 6 + time * 0.03;
                    double y = b.beamFrom() + 0.3 + t * (top - b.beamFrom());
                    float a = (float) (0.45 + 0.55 * Math.sin(time * 0.15 - t * 14)) * (1 - 0.6f * (float) t);
                    g.billDisc(b.cx() + Math.cos(ang) * r, y, b.cz() + Math.sin(ang) * r, 0.2 * k, Math.max(0.1f, a), 0f, 0.6f, 0.2f, 8);
                }
            }
            for (int i = 0; i < 4; i++) {
                double ang = Math.PI / 2 * i + 0.4 + time * 0.004;
                double lr = r + 1.3;
                double ly = b.beamFrom() + 1.3 + Math.sin(time * 0.05 + i) * 0.12;
                lantern(g, b.cx() + Math.cos(ang) * lr, ly, b.cz() + Math.sin(ang) * lr, k, time + i * 17, e.floor());
            }
            // 床から入口へ向かう 4 本の綱
            for (int i = 0; i < 4; i++) {
                double ang = Math.PI / 2 * i + Math.PI / 4;
                rope(g, b.cx(), e.floor() + 0.5, b.cz(), Math.cos(ang), Math.sin(ang), r + 0.3, 9.0, 0.0, time + i * 11, k);
            }
        } else {
            // 口: 入口の縁に点の連なり (柱の側面は直線 + 上は半円。灯が順に走る)。ルーン (菱形) が 3 つに 1 つ
            double fcx = b.cx() + e.fx() * b.hz();
            double fcz = b.cz() + e.fz() * b.hz();
            double hx = b.hx();
            double hy = b.hy();
            double straight = hy;
            double arcLen = Math.PI * hx;
            double total = straight * 2 + arcLen;
            int beads = (int) Math.max(24, total / 0.55);
            for (int i = 0; i <= beads; i++) {
                double s = total * i / beads;
                double lx;
                double ly;
                if (s < straight) {
                    lx = -hx;
                    ly = -hy + s;
                } else if (s < straight + arcLen) {
                    double th = Math.PI - (s - straight) / hx;
                    lx = Math.cos(th) * hx;
                    ly = Math.sin(th) * Math.min(hy, hx * 0.9 + 0.5);
                } else {
                    lx = hx;
                    ly = -hy + (total - s);
                }
                double px = fcx + e.sx() * lx;
                double pz = fcz + e.sz() * lx;
                double py = b.cy() + ly;
                float a = (float) (0.35 + 0.65 * Math.max(0, Math.sin(time * 0.12 - s * 0.5)));
                if (i % 3 == 0) {
                    g.diamond(px, py, pz, 0.34 * k, a, 0.55f);
                } else {
                    g.billDisc(px, py, pz, 0.17 * k, a * 0.8f, 0f, 0.6f, 0.2f, 8);
                }
            }
            // 提灯 2 つ (入口の両脇)
            for (int side = -1; side <= 1; side += 2) {
                double lx = fcx + e.sx() * (hx + 1.3) * side + e.fx() * 0.6;
                double lz = fcz + e.sz() * (hx + 1.3) * side + e.fz() * 0.6;
                lantern(g, lx, b.cy() + hy * 0.35 + Math.sin(time * 0.05 + side) * 0.12, lz, k, time + side * 9, e.floor());
            }
            // 入口へ向かう光の綱 2 本
            for (int side = -1; side <= 1; side += 2) {
                rope(g, fcx, e.floor() + 1.0, fcz, e.fx(), e.fz(), 0.0, 15.0, side * hx * 0.7, time + side * 6, k);
            }
        }
        // 標の上の光の花 (遠くからも見える)
        double hy2 = Math.min(b.beamTo(), b.beamFrom() + 22);
        g.billDisc(b.cx(), hy2, b.cz(), 2.2 * k, 0.28f, 0f, 0.5f, 0f, 16);
    }

    // 細い光の筋。近くでは淡く、遠くでは太く濃く (遠くからの目印)。nearA → farA
    private static void shaft(G g, TitanEndNetwork.Beam b, Entry e, double radScale, float nearA, float farA) {
        double far = Mth.clamp((e.dist() - 30) / 70.0, 0, 1);
        double rad = Math.max(0.4, b.beamRadius()) * radScale + Math.min(3.0, e.dist() * 0.012);
        float a = (float) Mth.lerp(far, nearA, farA);
        g.pass(true);
        g.cyl(b.cx(), b.cz(), b.beamFrom(), b.beamTo(), rad, rad * 0.75, 1f, 0f, 0f, 12, 20, t -> a * (Math.pow(1 - t, 1.5) * 0.9 + 0.1));
        g.pass(false);
        g.cyl(b.cx(), b.cz(), b.beamFrom(), b.beamTo(), rad * 0.4, rad * 0.3, 1f, 0f, 0.5f, 8, 20, t -> a * 0.8 * (Math.pow(1 - t, 1.5) * 0.9 + 0.1));
    }

    // 提灯: 浮かぶ光の玉と、下へ垂れる細い糸
    private static void lantern(G g, double x, double y, double z, double k, double t, double floor) {
        float pulse = 0.8f + 0.2f * Mth.sin((float) (t * 0.1));
        g.billDisc(x, y, z, 0.75 * k, 0.28f * pulse, 0f, 0.5f, 0f, 14);
        g.billDisc(x, y, z, 0.25 * k, 0.95f, 0.5f, 0.9f, 0.4f, 12);
        g.ribbon(x, y - 0.25 * k, z, x, Math.max(floor + 0.2, y - 1.6), z, 0.03 * k, 0.28f * pulse);
    }

    // 光の綱: 始点 (x0, y0, z0) から向き (dx, dz) へ、距離 from〜to に、少したるんだ点の連なり。点は入口へ向かって流れる。横ずれ off
    private static void rope(G g, double x0, double y0, double z0, double dx, double dz, double from, double to, double off, double t, double k) {
        double px = -dz;
        double pz = dx;
        int beads = 24;
        double span = to - from;
        for (int i = 0; i < beads; i++) {
            double d = to - ((t * 0.06 + i * span / beads) % span); // 入口に近づく
            double u = d / span; // 0 = 入口側
            double x = x0 + dx * (from + d) + px * off;
            double z = z0 + dz * (from + d) + pz * off;
            double y = y0 + Math.sin(u * Math.PI) * 0.7 + u * 0.4;
            float a = (float) (Math.sin(Math.PI * (1 - u)) * 0.85);
            g.billDisc(x, y, z, 0.16 * k, Math.max(0f, a), 0f, 0.6f, 0.2f, 8);
        }
    }

    // ---------------------------------------------------------------- 戻る光 A: 浮かぶ灯の精

    private static void returnA(G g, TitanEndNetwork.Beam b, double time) {
        double fx = b.cx();
        double fy = b.beamFrom();
        double fz = b.cz();
        double dist = Math.sqrt(sq(fx - g.cam.x) + sq(fy - g.cam.y) + sq(fz - g.cam.z));
        double k = 1.0 + Math.min(1.5, dist * 0.015);
        double bob = Math.sin(time * 0.06) * 0.16;
        double oy = fy + 1.45 + bob;
        float pulse = 0.82f + 0.18f * Mth.sin((float) (time * 0.1));
        // 灯の精: 外の暈・内の暈・芯
        g.billDisc(fx, oy, fz, 1.1 * k, 0.16f * pulse, 0f, 0.4f, 0f, 18);
        g.billDisc(fx, oy, fz, 0.55 * k, 0.32f * pulse, 0f, 0.6f, 0.2f, 16);
        g.billDisc(fx, oy, fz, 0.2 * k, 0.95f, 0.8f, 0.95f, 0.8f, 12);
        // 周りを回る小さな火の粉 4 つ
        for (int i = 0; i < 4; i++) {
            double a = time * 0.05 + Math.PI / 2 * i;
            double rr = 0.55 + 0.08 * Math.sin(time * 0.1 + i);
            g.billDisc(fx + Math.cos(a) * rr, oy + Math.sin(a * 1.7) * 0.28, fz + Math.sin(a) * rr, 0.07 * k, 0.8f, 0.5f, 0.9f, 0.5f, 8);
        }
        // 床へ垂れる細い光の糸と、足もとの淡い光の溜まり (踏み込む場所)
        g.cyl(fx, fz, fy + 0.05, oy - 0.2, 0.05 * k, 0.03 * k, 0.3f, 0.2f, 0.3f, 6, 4, t -> 0.5);
        g.floorRing(fx, fy + 0.04, fz, 0.0, b.hx() + 0.3, 0.20f * pulse, 0.0f, 0.2f, 24);
        g.floorRing(fx, fy + 0.05, fz, b.hx() + 0.3 - 0.07 * k, b.hx() + 0.3, 0.45f * pulse, 0.45f * pulse, 0.3f, 36);
    }

    // ---------------------------------------------------------------- 戻る光 B: 床のルーンの輪 (巨神ごとの紋様)

    private static void returnB(G g, TitanEndNetwork.Beam b, double time, String titan) {
        double fx = b.cx();
        double fy = b.beamFrom() + 0.05;
        double fz = b.cz();
        double dist = Math.sqrt(sq(fx - g.cam.x) + sq(fy - g.cam.y) + sq(fz - g.cam.z));
        double k = 1.0 + Math.min(2.0, dist * 0.02);
        double r = b.hx() + 0.3;
        float pulse = 0.8f + 0.2f * Mth.sin((float) (time * 0.08));
        g.floorRing(fx, fy, fz, 0.0, r, 0.24f * pulse, 0.05f, 0.2f, 28);
        g.floorRing(fx, fy + 0.01, fz, r - 0.1 * k, r, 0.7f * pulse, 0.7f * pulse, 0.35f, 48);
        g.floorRing(fx, fy + 0.01, fz, r * 0.7 - 0.06 * k, r * 0.7, 0.45f * pulse, 0.45f * pulse, 0.25f, 40);
        // 外周の目盛り (12 本。交互に長短)。ゆっくり回る
        for (int i = 0; i < 12; i++) {
            double a = time * 0.01 + Math.PI * 2 * i / 12;
            double r0 = r * (i % 2 == 0 ? 0.74 : 0.82);
            double r1 = r * 0.96;
            g.floorQuad(fx, fy + 0.02, fz, a, r0, r1, 0.045 * k, 0.65f * pulse, 0.3f);
        }
        // 巨神の紋様 (逆回り)
        g.floorLine(fx, fy + 0.02, fz, motif(titan), r * 0.62, -time * 0.015, 0.06 * k, 0.9f * pulse, 0.5f, closed(titan));
        // ごく淡い光の筒 (遠くからでも輪の場所が分かる)
        g.cyl(fx, fz, fy, fy + 2.6 + k, r * 0.95, r * 0.7, 1f, 0f, 0.3f, 18, 8, t -> 0.07 * (1 - t));
    }

    // ---------------------------------------------------------------- 戻る光 C: 光の拱門 (揺らぐ光の幕)

    private static void returnC(G g, TitanEndNetwork.Beam b, double time) {
        double fx = b.cx();
        double fy = b.beamFrom();
        double fz = b.cz();
        double[] r = g.viewRight(fx, fy + 1.4, fz);
        double rx = r[0];
        double rz = r[1];
        double dist = Math.sqrt(sq(fx - g.cam.x) + sq(fy - g.cam.y) + sq(fz - g.cam.z));
        double k = 1.0 + Math.min(1.2, dist * 0.012);
        double halfW = 1.0;
        double straightH = 1.55;
        double archH = 1.0;
        float pulse = 0.85f + 0.15f * Mth.sin((float) (time * 0.07));
        // 幕: 縦の細片。縁ほど濃く、中は淡くきらめく。上は丸い拱門の形に切る
        int strips = 18;
        for (int i = 0; i < strips; i++) {
            double u0 = -1 + 2.0 * i / strips;
            double u1 = -1 + 2.0 * (i + 1) / strips;
            double top0 = straightH + archH * Math.sqrt(Math.max(0, 1 - u0 * u0));
            double top1 = straightH + archH * Math.sqrt(Math.max(0, 1 - u1 * u1));
            float e0 = (float) (0.06 + 0.16 * u0 * u0);
            float e1 = (float) (0.06 + 0.16 * u1 * u1);
            double sh0 = 0.5 + 0.5 * Math.sin(u0 * 6 + time * 0.12);
            double sh1 = 0.5 + 0.5 * Math.sin(u1 * 6 + time * 0.12);
            g.stripQuad(fx + rx * u0 * halfW, fz + rz * u0 * halfW, fx + rx * u1 * halfW, fz + rz * u1 * halfW, fy + 0.02, top0 + fy, top1 + fy,
                    (float) (e0 * (0.7 + 0.5 * sh0)) * pulse, (float) (e1 * (0.7 + 0.5 * sh1)) * pulse, 0.25f);
        }
        // 縁の光: 太さの違う 3 層 + 走る明るい点
        int segs = 40;
        for (int layer = 0; layer < 3; layer++) {
            double w = (layer == 0 ? 0.30 : layer == 1 ? 0.16 : 0.06) * (layer == 0 ? 1 : 1) * Math.min(2.0, k);
            float a = layer == 0 ? 0.10f : layer == 1 ? 0.22f : 0.7f;
            float white = layer == 2 ? 0.6f : 0.15f;
            double px = 0;
            double py = 0;
            double pz = 0;
            for (int i = 0; i <= segs; i++) {
                double s = i / (double) segs; // 0..1: 左の足 → 弧 → 右の足
                double u;
                double y;
                if (s < 0.25) {
                    u = -1;
                    y = straightH * (s / 0.25);
                } else if (s < 0.75) {
                    double th = Math.PI - (s - 0.25) / 0.5 * Math.PI;
                    u = Math.cos(th);
                    y = straightH + archH * Math.sin(th);
                } else {
                    u = 1;
                    y = straightH * (1 - (s - 0.75) / 0.25);
                }
                double x = fx + rx * u * halfW;
                double z = fz + rz * u * halfW;
                double yy = fy + y;
                if (i > 0) {
                    g.ribbon(px, py, pz, x, yy, z, w, a * pulse, white);
                }
                px = x;
                py = yy;
                pz = z;
            }
        }
        // 縁を走る光
        double s = (time * 0.012) % 1.0;
        for (int j = 0; j < 2; j++) {
            double sj = (s + j * 0.5) % 1.0;
            double u;
            double y;
            if (sj < 0.25) {
                u = -1;
                y = straightH * (sj / 0.25);
            } else if (sj < 0.75) {
                double th = Math.PI - (sj - 0.25) / 0.5 * Math.PI;
                u = Math.cos(th);
                y = straightH + archH * Math.sin(th);
            } else {
                u = 1;
                y = straightH * (1 - (sj - 0.75) / 0.25);
            }
            g.billDisc(fx + rx * u * halfW, fy + y, fz + rz * u * halfW, 0.28 * k, 0.9f, 0.6f, 0.9f, 0.5f, 10);
        }
        // 足もとの光の溜まり
        g.floorRing(fx, fy + 0.04, fz, 0.0, b.hx() + 0.3, 0.16f * pulse, 0.0f, 0.2f, 24);
    }

    // ---------------------------------------------------------------- 粒 (クライアントだけ)

    public static void particles(Minecraft mc, TitanEndNetwork.Beam b, MarkerStyle style, long tick, String returnTitan) {
        if (mc.level == null || mc.player == null || tick % 3 != 0) {
            return;
        }
        double d2 = sq(b.cx() - mc.player.getX()) + sq(b.cz() - mc.player.getZ());
        boolean ret = isReturn(b);
        if (d2 > (ret ? 40 * 40 : 90 * 90)) {
            return;
        }
        var rnd = mc.level.random;
        if (ret) {
            double fy = b.beamFrom();
            if (style == MarkerStyle.A) {
                if (tick % 6 == 0) {
                    mc.level.addParticle(ParticleTypes.END_ROD, b.cx() + (rnd.nextDouble() - 0.5) * 0.6, fy + 1.5, b.cz() + (rnd.nextDouble() - 0.5) * 0.6,
                            (rnd.nextDouble() - 0.5) * 0.01, -0.01, (rnd.nextDouble() - 0.5) * 0.01);
                }
            } else if (style == MarkerStyle.B) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double rr = (b.hx() + 0.3) * (0.4 + rnd.nextDouble() * 0.55);
                ParticleOptions p = switch (returnTitan) {
                    case Titans.TIDE_SHELL -> ParticleTypes.NAUTILUS;
                    case Titans.THUNDER_WING -> ParticleTypes.ELECTRIC_SPARK;
                    case Titans.EMBER -> ParticleTypes.SMALL_FLAME;
                    default -> dust(BoardGlow.colour(returnTitan), 0.9f);
                };
                double vy = returnTitan.equals(Titans.THUNDER_WING) ? 0.06 : 0.025;
                mc.level.addParticle(p, b.cx() + Math.cos(a) * rr, fy + 0.15, b.cz() + Math.sin(a) * rr, 0, vy, 0);
            } else if (style == MarkerStyle.C) {
                if (tick % 6 == 0) {
                    double[] r = {1, 0};
                    mc.level.addParticle(ParticleTypes.END_ROD, b.cx() + (rnd.nextDouble() - 0.5) * 1.6 * r[0], fy + 0.3 + rnd.nextDouble() * 2.4, b.cz() + (rnd.nextDouble() - 0.5) * 0.6,
                            0, 0.01, 0);
                }
            }
            return;
        }
        double floor = Math.abs(b.hx() - b.hz()) < 0.01f ? b.beamFrom() : b.cy() - b.hy();
        DustParticleOptions dust = dust(b.rgb(), 0.8f);
        double R = Math.max(b.hx(), b.hz()) + 1.0;
        switch (style) {
            case A -> {
                // 筋の中をゆっくり昇る光の粒
                double a = rnd.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rnd.nextDouble()) * Math.max(0.5, b.beamRadius()) * 1.2;
                mc.level.addParticle(dust, b.cx() + Math.cos(a) * rr, floor + 0.2 + rnd.nextDouble() * 3, b.cz() + Math.sin(a) * rr, 0, 0.05 + rnd.nextDouble() * 0.04, 0);
                if (tick % 9 == 0) {
                    mc.level.addParticle(ParticleTypes.END_ROD, b.cx() + Math.cos(a) * rr, floor + 0.5, b.cz() + Math.sin(a) * rr, 0, 0.06, 0);
                }
            }
            case B -> {
                // 欠片から剥がれる細かい粒
                double a = rnd.nextDouble() * Math.PI * 2;
                mc.level.addParticle(dust, b.cx() + Math.cos(a) * R, b.cy() + (rnd.nextDouble() - 0.5) * 2, b.cz() + Math.sin(a) * R, 0, -0.01, 0);
            }
            default -> {
                // 提灯からの小さな火の粉
                double a = rnd.nextDouble() * Math.PI * 2;
                mc.level.addParticle(ParticleTypes.END_ROD, b.cx() + Math.cos(a) * R, b.cy() + rnd.nextDouble() * 2, b.cz() + Math.sin(a) * R, 0, 0.01, 0);
            }
        }
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), size);
    }

    // ---------------------------------------------------------------- 描画の道具

    static final class G {
        final BufferBuilder buf;
        final BufferBuilder alphaBuf;
        boolean useAlpha;
        final Matrix4f pose;
        final Vec3 cam;
        private final int r;
        private final int gr;
        private final int bl;
        private final float strength;

        G(BufferBuilder buf, BufferBuilder alphaBuf, Matrix4f pose, Vec3 cam, int rgb, float strength) {
            this.buf = buf;
            this.alphaBuf = alphaBuf;
            this.pose = pose;
            this.cam = cam;
            this.r = (rgb >> 16) & 0xFF;
            this.gr = (rgb >> 8) & 0xFF;
            this.bl = rgb & 0xFF;
            this.strength = strength;
        }

        // 以降の面を、通常のアルファ合成 (明るい昼空でも色が乗る) で描くか、加算 (発光) で描くか
        void pass(boolean alpha) {
            this.useAlpha = alpha;
        }

        // 頂点。w = 白への寄せ (0〜1)
        void v(double x, double y, double z, float a, float w) {
            int rr = (int) (r + (255 - r) * w);
            int gg = (int) (gr + (255 - gr) * w);
            int bb = (int) (bl + (255 - bl) * w);
            (useAlpha ? alphaBuf : buf).addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(rr, gg, bb, (int) (Mth.clamp(a * strength, 0f, 1f) * 255f));
        }

        // 視線に垂直な水平の「右」 (x, z)
        double[] viewRight(double x, double y, double z) {
            double vx = x - cam.x;
            double vz = z - cam.z;
            double l = Math.hypot(vx, vz);
            if (l < 1e-6) {
                return new double[] {1, 0};
            }
            return new double[] {vz / l, -vx / l};
        }

        // 常にこちらを向く円盤 (中心 aC → 縁 aR のなめらかな濃さ)
        void billDisc(double cx, double cy, double cz, double rad, float aC, float aR, float wC, float wR, int sides) {
            double vx = cx - cam.x;
            double vy = cy - cam.y;
            double vz = cz - cam.z;
            double rx = vz;
            double rz = -vx;
            double rl = Math.hypot(rx, rz);
            if (rl < 1e-6) {
                rx = 1;
                rz = 0;
                rl = 1;
            }
            rx /= rl;
            rz /= rl;
            double ux = vy * rz;
            double uy = vz * rx - vx * rz;
            double uz = -vy * rx;
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul < 1e-9) {
                return;
            }
            ux /= ul;
            uy /= ul;
            uz /= ul;
            for (int i = 0; i < sides; i++) {
                double a0 = Math.PI * 2 * i / sides;
                double a1 = Math.PI * 2 * (i + 1) / sides;
                v(cx, cy, cz, aC, wC);
                v(cx + (Math.cos(a0) * rx + Math.sin(a0) * ux) * rad, cy + Math.sin(a0) * uy * rad, cz + (Math.cos(a0) * rz + Math.sin(a0) * uz) * rad, aR, wR);
                v(cx + (Math.cos(a1) * rx + Math.sin(a1) * ux) * rad, cy + Math.sin(a1) * uy * rad, cz + (Math.cos(a1) * rz + Math.sin(a1) * uz) * rad, aR, wR);
                v(cx, cy, cz, aC, wC);
            }
        }

        // 常にこちらを向く菱形 (ルーン)
        void diamond(double cx, double cy, double cz, double size, float a, float w) {
            double[] r2 = viewRight(cx, cy, cz);
            v(cx, cy + size, cz, a * 0.5f, w);
            v(cx + r2[0] * size * 0.6, cy, cz + r2[1] * size * 0.6, a, w);
            v(cx, cy - size, cz, a * 0.5f, w);
            v(cx - r2[0] * size * 0.6, cy, cz - r2[1] * size * 0.6, a, w);
        }

        // 水平な輪 (r0〜r1。内側 a0 → 外側 a1)
        void floorRing(double cx, double y, double cz, double r0, double r1, float a0, float a1, float w, int sides) {
            for (int i = 0; i < sides; i++) {
                double t0 = Math.PI * 2 * i / sides;
                double t1 = Math.PI * 2 * (i + 1) / sides;
                double c0 = Math.cos(t0);
                double s0 = Math.sin(t0);
                double c1 = Math.cos(t1);
                double s1 = Math.sin(t1);
                v(cx + c0 * r0, y, cz + s0 * r0, a0, w);
                v(cx + c0 * r1, y, cz + s0 * r1, a1, w);
                v(cx + c1 * r1, y, cz + s1 * r1, a1, w);
                v(cx + c1 * r0, y, cz + s1 * r0, a0, w);
            }
        }

        // 水平な目盛り (角度 ang・半径 r0〜r1・幅 width)
        void floorQuad(double cx, double y, double cz, double ang, double r0, double r1, double width, float a, float w) {
            double c = Math.cos(ang);
            double s = Math.sin(ang);
            double px = -s * width;
            double pz = c * width;
            v(cx + c * r0 - px, y, cz + s * r0 - pz, a, w);
            v(cx + c * r0 + px, y, cz + s * r0 + pz, a, w);
            v(cx + c * r1 + px, y, cz + s * r1 + pz, a, w);
            v(cx + c * r1 - px, y, cz + s * r1 - pz, a, w);
        }

        // 水平な折れ線 (単位半径の点列を scale 倍・rot 回転)
        void floorLine(double cx, double y, double cz, double[][] pts, double scale, double rot, double width, float a, float w, boolean closed) {
            double c = Math.cos(rot);
            double s = Math.sin(rot);
            int n = pts.length;
            int last = closed ? n : n - 1;
            for (int i = 0; i < last; i++) {
                double[] p0 = pts[i];
                double[] p1 = pts[(i + 1) % n];
                double x0 = cx + (p0[0] * c - p0[1] * s) * scale;
                double z0 = cz + (p0[0] * s + p0[1] * c) * scale;
                double x1 = cx + (p1[0] * c - p1[1] * s) * scale;
                double z1 = cz + (p1[0] * s + p1[1] * c) * scale;
                double dx = x1 - x0;
                double dz = z1 - z0;
                double l = Math.hypot(dx, dz);
                if (l < 1e-6) {
                    continue;
                }
                double px = -dz / l * width * 0.5;
                double pz = dx / l * width * 0.5;
                v(x0 - px, y, z0 - pz, a, w);
                v(x0 + px, y, z0 + pz, a, w);
                v(x1 + px, y, z1 + pz, a, w);
                v(x1 - px, y, z1 - pz, a, w);
            }
        }

        // 縦の筒 (下 a=1 → 上 a=0 の濃さの関数 alpha(t))。半径は下 r0 → 上 r1
        void cyl(double cx, double cz, double y0, double y1, double r0, double r1, float unusedBottom, float unusedTop, float w, int sides, int steps, DoubleUnaryOperator alpha) {
            for (int j = 0; j < steps; j++) {
                double t0 = j / (double) steps;
                double t1 = (j + 1) / (double) steps;
                float a0 = (float) alpha.applyAsDouble(t0);
                float a1 = (float) alpha.applyAsDouble(t1);
                double ra = Mth.lerp(t0, r0, r1);
                double rb = Mth.lerp(t1, r0, r1);
                double ya = Mth.lerp(t0, y0, y1);
                double yb = Mth.lerp(t1, y0, y1);
                for (int i = 0; i < sides; i++) {
                    double s0 = Math.PI * 2 * i / sides;
                    double s1 = Math.PI * 2 * (i + 1) / sides;
                    v(cx + Math.cos(s0) * ra, ya, cz + Math.sin(s0) * ra, a0, w);
                    v(cx + Math.cos(s1) * ra, ya, cz + Math.sin(s1) * ra, a0, w);
                    v(cx + Math.cos(s1) * rb, yb, cz + Math.sin(s1) * rb, a1, w);
                    v(cx + Math.cos(s0) * rb, yb, cz + Math.sin(s0) * rb, a1, w);
                }
            }
        }

        // 縦の細片 (底 y0、上 top0・top1。底は薄く、上へ濃く)。幕の 1 枚
        void stripQuad(double xa, double za, double xb, double zb, double y0, double top0, double top1, float a0, float a1, float w) {
            v(xa, y0, za, a0 * 0.4f, w);
            v(xb, y0, zb, a1 * 0.4f, w);
            v(xb, top1, zb, a1, w);
            v(xa, top0, za, a0, w);
        }

        // 欠片 (縦長の八面体)。spin で回る
        void shard(double cx, double cy, double cz, double h, double wd, double spin, float a) {
            double[] ex = new double[4];
            double[] ez = new double[4];
            for (int i = 0; i < 4; i++) {
                ex[i] = cx + Math.cos(spin + i * Math.PI / 2) * wd;
                ez[i] = cz + Math.sin(spin + i * Math.PI / 2) * wd;
            }
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                float shade = 0.55f + 0.45f * (float) Math.cos(spin + i * Math.PI / 2 + Math.PI / 4);
                // 上半分
                v(cx, cy + h, cz, a * 0.9f * shade, 0.7f);
                v(ex[i], cy, ez[i], a * 0.35f, 0.1f);
                v(ex[j], cy, ez[j], a * 0.35f, 0.1f);
                v(cx, cy + h, cz, a * 0.9f * shade, 0.7f);
                // 下半分
                v(cx, cy - h * 0.8, cz, a * 0.5f * shade, 0.3f);
                v(ex[i], cy, ez[i], a * 0.35f, 0.1f);
                v(ex[j], cy, ez[j], a * 0.35f, 0.1f);
                v(cx, cy - h * 0.8, cz, a * 0.5f * shade, 0.3f);
            }
        }

        void ribbon(double ax, double ay, double az, double bx, double by, double bz, double w, float a) {
            ribbon(ax, ay, az, bx, by, bz, w, a, 0.3f);
        }

        // 視線に垂直な帯 a→b (幅 w)
        void ribbon(double ax, double ay, double az, double bx, double by, double bz, double w, float a, float white) {
            double ex = bx - ax;
            double ey = by - ay;
            double ez = bz - az;
            double mx = (ax + bx) / 2 - cam.x;
            double my = (ay + by) / 2 - cam.y;
            double mz = (az + bz) / 2 - cam.z;
            double px = ey * mz - ez * my;
            double py = ez * mx - ex * mz;
            double pz = ex * my - ey * mx;
            double len = Math.sqrt(px * px + py * py + pz * pz);
            if (len < 1.0e-9) {
                return;
            }
            px = px / len * w;
            py = py / len * w;
            pz = pz / len * w;
            v(ax - px, ay - py, az - pz, a, white);
            v(ax + px, ay + py, az + pz, a, white);
            v(bx + px, by + py, bz + pz, a, white);
            v(bx - px, by - py, bz - pz, a, white);
        }
    }
}
