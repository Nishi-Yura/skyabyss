package jp.revati.skyabyss.client.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.math.Axis;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.grave.GraveMarkerHud;
import jp.revati.skyabyss.client.quest.ObjectiveStrip;
import jp.revati.skyabyss.grave.GraveMarkers;
import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.party.PartyLocator;
import jp.revati.skyabyss.party.client.PartyMarkers;
import jp.revati.skyabyss.titan.travel.TravelDest;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// 旅の地図 (M キー。docs/design/33 の 4 章)。北が上、町を中心にした印だけの地図 (地形は描かない。座標の数字も出さない)。
// 縮尺はホイール (と ＋／− のキー・右上のつまみ) でなめらかに替える: 半径 96 (都の中) 〜 4,800 (世界の果てまで)。指した所を中心に寄る。
// 印を指すと名前と「北東 420」(自分からの方角と距離)。大事な印 (都・巨神・章の場所・祠・仲間・目的・墓) は、名前の札を常に出す (重なる札は出さない)。
// 輪には距離の数字。自分の矢印は 2 倍で、暗い丸の上に。寄せると都の壁の四角 (2026-10-23: 地図を読みやすく・もっと寄せて・もっと引いて)。
// 巨神の子に乗っているとき (chart != null) は、子の海図 (旧 TravelChartScreen) をこの画面に重ねる: 行ける行き先の印が光り、
// 印か右の一覧で選んで「出発」。印の出どころはサーバーの MapMarks (一か所) と、クライアントが持つ自分・仲間・墓・目的の標。
// 見た目は 3 案 (設定 mapStyle。SLATE = 石板の枠に丸い海図 (既定)、PARCHMENT = 骨の枠に羊皮紙、INK = 枠なしの墨の夜図)。
// 絵は tools/gen/world/gen_travel_map_assets.py (印 marks.png・地 paper.png・A の海図 disc_a.png)、一覧の行とつまみは海図の chart.png
public class TravelMapScreen extends Screen {
    static final ResourceLocation MARKS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/marks.png");
    static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/paper.png");
    static final ResourceLocation DISC_A = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/disc_a.png");
    static final ResourceLocation CHART = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/travel/chart.png");

    // 印の絵の番号 (marks.png の並び。tools/gen/world/gen_travel_map_assets.py の SPRITES と同じ)
    static final int S_TOWN = 0, S_CAMP = 1, S_FOOTPRINT = 2, S_RIBS = 3, S_WAYMARK = 4, S_SITE = 5, S_TITAN = 6, S_GRAVE = 7,
            S_PARTY = 8, S_OBJECTIVE = 9, S_RUMOR = 10, S_SELF = 11, S_SPAWN = 12, S_SHRINE = 13, S_GLOW = 14;

    // 縮尺 (中心から縁までのブロック): いちばん寄せた / いちばん引いた / 開いたとき (町のまわり・世界)。ホイール一目盛りで 1.25 倍
    static final double MIN_RANGE = 96;
    static final double MAX_RANGE = 4800;
    static final double NEAR = 600;
    static final double FAR = 2400;
    static final double ZOOM_STEP = 1.25;
    // 輪の間の候補 (半径の中に輪が 2〜4 本になるものを選ぶ)
    static final int[] RING_STEPS = {25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000};
    // 都の壁 (真ん中から壁の外の面まで。tools/capital/town.py の W)
    static final int TOWN_HALF = 100;

    // 見た目ごとの色
    record Palette(int text, int dim, int accent, int pale, int ring, int ringStrong, int road, int rim, int lost) {
    }

    static final Palette SLATE = new Palette(0xFFE2D9C2, 0xFF8E8A7C, 0xFF7FD6D0, 0xFF9FE8FF, 0xFF2A5E6A, 0xFF4E9AA4, 0xCC9FE8FF, 0xFFC4BAA0, 0xFF6A6862);
    static final Palette PARCH = new Palette(0xFF3B2A1A, 0xFF7A6448, 0xFF2E5E60, 0xFF1F4E58, 0x996B5236, 0xCC5A4228, 0xCC5A4228, 0xFF3B2A1A, 0xFF9A8A70);
    static final Palette INK = new Palette(0xFFCFE6E4, 0xFF6F8A8C, 0xFF7FD6D0, 0xFF9FE8FF, 0x664A9AA0, 0xAA6FC4C8, 0xAA9FE8FF, 0xFF7FD6D0, 0xFF55605F);

    // 子の海図の一覧 (GUI 単位)
    private static final int ROW_W = 132;
    private static final int ROW_H = 22;
    private static final int BTN_W = 60;
    private static final int BTN_H = 16;
    private static final int LIST_W = 140;

    @Nullable
    private TravelNetwork.Chart chart;
    @Nullable
    private MapMarks.Sheet sheet;
    private boolean demo;
    // いま描いている縮尺 (なめらかに target へ寄る) と、寄せる先。anchor = 寄るときに動かない画面の点
    private double view;
    private double target;
    private double anchorX;
    private double anchorY;
    private long lastFrame;
    private int scroll;
    @Nullable
    private String selected;
    @Nullable
    private Config.MapStyle styleOverride;

    // 配置 (init で決める)
    private int fx, fy, fw, fh;
    private int cx, cy, radius;
    private int listX, listY, listRows, btnY;

    public TravelMapScreen(@Nullable TravelNetwork.Chart chart, @Nullable MapMarks.Sheet sheet) {
        super(Component.translatable("gui.skyabyss.map.title"));
        this.chart = chart;
        this.sheet = sheet;
        // 海図は遠くまで行くので世界の縮尺から
        this.view = chart != null ? FAR : NEAR;
        this.target = view;
        pickDefault();
    }

    // 撮影の見本 (サーバーに頼まない。仲間・墓・目的も見本を描く)
    public static TravelMapScreen demo(boolean voyage) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        TravelMapScreen s = new TravelMapScreen(voyage ? MapClient.demoChart(at) : null, MapClient.demoSheet(at));
        s.demo = true;
        return s;
    }

    public boolean demo() {
        return demo;
    }

    public boolean voyage() {
        return chart != null;
    }

    void sheet(MapMarks.Sheet sheet) {
        this.sheet = sheet;
    }

    void chart(TravelNetwork.Chart chart) {
        this.chart = chart;
        if (selected != null && chart.dests().stream().noneMatch(d -> d.id().equals(selected))) {
            selected = null;
        }
        pickDefault();
    }

    private void pickDefault() {
        if (chart == null) {
            return;
        }
        if (!chart.chosen().isEmpty()) {
            selected = chart.chosen();
        }
        if (selected == null) {
            // いちばん遠い、行ける行き先から選んでおく (近すぎる所は選べない)
            selected = chart.dests().stream().filter(this::reachable).max(Comparator.comparingDouble(this::distance)).map(TravelNetwork.Dest::id).orElse(null);
        }
    }

    Config.MapStyle style() {
        if (styleOverride != null) {
            return styleOverride;
        }
        try {
            return Config.MAP_STYLE.get();
        } catch (IllegalStateException e) {
            return Config.MapStyle.SLATE;
        }
    }

    // 撮影の台本 (mapscale) から縮尺を替える (0 = 町のまわり、1 = 世界)
    public void scale(int s) {
        zoomTo(s <= 0 ? NEAR : FAR);
    }

    // 撮影の台本 (mapscale <ブロック>) から: その半径にすぐ替える
    public void zoomTo(double range) {
        view = target = Mth.clamp(range, MIN_RANGE, MAX_RANGE);
        clampPan();
    }

    public double range() {
        return view;
    }

    // ホイール・キー・つまみ: steps 目盛り寄せる (負なら引く)。(ax, ay) の画面の点のまわりで (丸の外なら真ん中)
    private void zoomBy(double steps, double ax, double ay) {
        double next = Mth.clamp(target / Math.pow(ZOOM_STEP, steps), MIN_RANGE, MAX_RANGE);
        if (Math.abs(next - target) < 1e-6) {
            return;
        }
        boolean inDisc = Math.hypot(ax - cx, ay - cy) <= radius;
        anchorX = inDisc ? ax : cx;
        anchorY = inDisc ? ay : cy;
        target = next;
        click(steps > 0 ? 1.3f : 1.0f);
    }

    // 毎フレーム: 縮尺を寄せる先へなめらかに (寄る点の下の世界の場所は動かさない)
    private void animateZoom() {
        long now = net.minecraft.Util.getMillis();
        double dt = lastFrame == 0 ? 0.016 : Math.min(0.1, (now - lastFrame) / 1000.0);
        lastFrame = now;
        if (view == target || radius <= 0) {
            return;
        }
        double next = view + (target - view) * (1 - Math.exp(-dt * 14));
        if (Math.abs(next - target) < target * 0.003) {
            next = target;
        }
        double k0 = view / radius;
        double k1 = next / radius;
        panX += (anchorX - cx) * (k0 - k1);
        panZ += (anchorY - cy) * (k0 - k1);
        view = next;
        clampPan();
    }

    // 撮影の台本 (mapstyle) から見た目を替える
    public void styleOverride(@Nullable Config.MapStyle s) {
        this.styleOverride = s;
        init();
    }

    Palette palette() {
        return switch (style()) {
            case PARCHMENT -> PARCH;
            case INK -> INK;
            default -> SLATE;
        };
    }

    @Override
    protected void init() {
        int margin = style() == Config.MapStyle.INK ? 0 : 10;
        fx = margin;
        fy = margin;
        fw = width - margin * 2;
        fh = height - margin * 2;
        int inX = fx + 12;
        int inY = fy + 24;
        int inW = fw - 24;
        int inH = fh - 24 - 18;
        if (chart != null) {
            listX = fx + fw - 12 - LIST_W + 4;
            listY = inY + 26;
            btnY = fy + fh - 14 - BTN_H;
            listRows = Math.max(2, (btnY - 6 - listY) / ROW_H);
            inW -= LIST_W + 6;
        }
        cx = inX + inW / 2;
        cy = inY + inH / 2;
        radius = Math.max(40, Math.min(inW, inH) / 2 - 10);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 位置
    // 盤の真ん中は自分 (地上にいないときは町)。左ボタンで引くと動かせる (知っている所 = 自分・町・載っている印の範囲まで)。右クリックで自分へ戻す。
    // 2026-10-21 の試遊: 「自分の周りを映したい」「通ってきた範囲ならマウスで動かしたい」
    private double panX;
    private double panZ;

    private int baseX() {
        double[] me = self();
        if (me != null && !demo) {
            return (int) Math.floor(me[0]);
        }
        if (sheet != null) {
            return sheet.centerX();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.getBlockX();
    }

    private int baseZ() {
        double[] me = self();
        if (me != null && !demo) {
            return (int) Math.floor(me[1]);
        }
        if (sheet != null) {
            return sheet.centerZ();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.getBlockZ();
    }

    private int centerX() {
        return baseX() + (int) Math.round(panX);
    }

    private int centerZ() {
        return baseZ() + (int) Math.round(panZ);
    }

    // 動かせる範囲 (世界の座標の {x0, z0, x1, z1}): 自分・町・載っている印を囲む長方形
    private int[] knownBounds() {
        int x0 = baseX();
        int x1 = x0;
        int z0 = baseZ();
        int z1 = z0;
        if (sheet != null) {
            if (sheet.townKnown()) {
                x0 = Math.min(x0, sheet.centerX());
                x1 = Math.max(x1, sheet.centerX());
                z0 = Math.min(z0, sheet.centerZ());
                z1 = Math.max(z1, sheet.centerZ());
            }
            for (MapMarks.Mark m : sheet.marks()) {
                x0 = Math.min(x0, m.x());
                x1 = Math.max(x1, m.x());
                z0 = Math.min(z0, m.z());
                z1 = Math.max(z1, m.z());
            }
        }
        return new int[] {x0, z0, x1, z1};
    }

    private void clampPan() {
        int[] b = knownBounds();
        double bx = baseX();
        double bz = baseZ();
        panX = Mth.clamp(panX, b[0] - bx, b[2] - bx);
        panZ = Mth.clamp(panZ, b[1] - bz, b[3] - bz);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && radius > 0 && Math.hypot(mx - cx, my - cy) <= radius + 8) {
            double k = view / radius;
            panX -= dx * k;
            panZ -= dy * k;
            clampPan();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    // 世界の (x, z) を画面へ。縁より外は縁に寄せる (out[2] = 1 なら縁に寄せた)
    private int[] project(double x, double z) {
        double k = radius / view;
        double dx = (x - centerX()) * k;
        double dz = (z - centerZ()) * k;
        double d = Math.hypot(dx, dz);
        double max = radius - 5;
        int beyond = 0;
        if (d > max) {
            dx *= max / d;
            dz *= max / d;
            beyond = 1;
        }
        return new int[]{cx + (int) Math.round(dx), cy + (int) Math.round(dz), beyond};
    }

    @Nullable
    private double[] self() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return new double[]{mc.player.getX(), mc.player.getZ(), mc.player.getYRot()};
    }

    private boolean onGround() {
        return self() != null;
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 景色を少し暗くする (INK は地図そのものが景色の上に乗るので、ここで濃く)
        if (style() == Config.MapStyle.INK) {
            g.fillGradient(0, 0, width, height, 0xB0080B10, 0xD0080B10);
        } else {
            g.fillGradient(0, 0, width, height, 0x80101418, 0xA0101418);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        animateZoom();
        Palette p = palette();
        drawSheet(g, p);
        drawContinent(g);
        drawRings(g, p);
        drawTown(g, p);
        List<Hit> hits = new ArrayList<>();
        drawMarks(g, p, hits, mouseX, mouseY);
        drawCompass(g, p);
        drawHeader(g, p, mouseX, mouseY);
        if (chart != null) {
            drawList(g, p, mouseX, mouseY);
            drawButtons(g, p, mouseX, mouseY);
        }
        Hit hover = nearest(hits, mouseX, mouseY);
        if (hover != null && !(chart != null && mouseX >= listX - 4)) {
            g.renderTooltip(font, hover.lines(), mouseX, mouseY);
        }
    }

    private void drawSheet(GuiGraphics g, Palette p) {
        Config.MapStyle st = style();
        switch (st) {
            case SLATE -> {
                tile(g, 0, fx, fy, fw, fh);
                frame(g, fx, fy, fw, fh, false);
                if (chart != null) {
                    recess(g, listX - 4, fy + 14, LIST_W, fh - 28);
                }
                // 丸い海図 (海図の画面と同じ雲の海)
                int r = (int) Math.round(radius * 256 / 250.0);
                g.blit(DISC_A, cx - r, cy - r, r * 2, r * 2, 0, 0, 512, 512, 512, 512);
                circle(g, cx, cy, radius + 1, 0xFF6E6654, 1, 0);
                circle(g, cx, cy, radius + 2, 0xFFC4BAA0, 2, 0);
                circle(g, cx, cy, radius + 3, 0xFFE2D9C2, 1, 0);
                circle(g, cx, cy, radius + 4, 0xFF0C0E12, 1, 0);
            }
            case PARCHMENT -> {
                frame(g, fx, fy, fw, fh, true);
                int inset = 4;
                g.enableScissor(fx + inset, fy + inset, fx + fw - inset, fy + fh - inset);
                tile(g, 1, fx + inset, fy + inset, fw - inset * 2, fh - inset * 2);
                g.disableScissor();
                // 焼けた縁 (内側へ薄くなる茶)
                for (int i = 0; i < 6; i++) {
                    int a = (0x50 - i * 0x0C) << 24;
                    outlineRect(g, fx + inset + i, fy + inset + i, fw - (inset + i) * 2, fh - (inset + i) * 2, a | 0x4A3018);
                }
                if (chart != null) {
                    g.fill(listX - 4, fy + 14, listX - 4 + LIST_W, fy + fh - 14, 0xE0141820);
                    outlineRect(g, listX - 4, fy + 14, LIST_W, fh - 28, 0xFF6B5236);
                }
            }
            case INK -> {
                g.setColor(1f, 1f, 1f, 0.55f);
                tile(g, 2, 0, 0, width, height);
                g.setColor(1f, 1f, 1f, 1f);
                for (int[] c : new int[][]{{8, 8}, {width - 9, 8}, {8, height - 9}, {width - 9, height - 9}}) {
                    gem(g, c[0], c[1]);
                }
                if (chart != null) {
                    g.fill(listX - 4, fy + 14, listX - 4 + LIST_W, fy + fh - 14, 0x90080B10);
                    g.fill(listX - 5, fy + 14, listX - 4, fy + fh - 14, 0x804A9AA0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 天と淵の大陸 (docs/design/40 の 3 章)
    // 大陸の世界では、丸い盤の中に描いた大陸の絵 (continent.png。tools/gen/world/gen_continent.py) を敷き、引いたときは土地の名前と目安のレベルを書く
    static final ResourceLocation CONTINENT = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/continent.png");
    static final int CONTINENT_HALF = 2000;
    // 土地の名前を出す縮尺 (この間。引きすぎると重なる)
    static final double NAMES_FROM = 700;
    static final double NAMES_TO = 3200;

    private void drawContinent(GuiGraphics g) {
        if (!jp.revati.skyabyss.world.continent.ContinentInfo.clientContinent || radius <= 0) {
            return;
        }
        double k = radius / view;
        int x0 = cx + (int) Math.round((-CONTINENT_HALF - centerX()) * k);
        int y0 = cy + (int) Math.round((-CONTINENT_HALF - centerZ()) * k);
        int size = (int) Math.round(CONTINENT_HALF * 2 * k);
        // 丸い盤の形に切る (一行ずつ切って、絵全体を置く)。外の海は透けて盤の地になじむ
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int y = cy - radius; y < cy + radius; y++) {
            double dy = y + 0.5 - cy;
            int w = (int) Math.floor(Math.sqrt(Math.max(0, (double) radius * radius - dy * dy)));
            if (w <= 0) {
                continue;
            }
            g.enableScissor(cx - w, y, cx + w, y + 1);
            g.blit(CONTINENT, x0, y0, size, size, 0, 0, 1024, 1024, 1024, 1024);
            g.disableScissor();
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        if (view < NAMES_FROM || view > NAMES_TO) {
            return;
        }
        jp.revati.skyabyss.world.continent.ContinentLayout lay = jp.revati.skyabyss.world.continent.ContinentLayout.get();
        for (var e : lay.names().entrySet()) {
            int[] at = project(e.getValue().getX(), e.getValue().getZ());
            if (at[2] == 0) {
                Component name = Component.translatable("map.skyabyss.continent." + e.getKey());
                g.drawCenteredString(font, name, at[0], at[1] - 4, 0xFF4A3A2A);
            }
        }
        for (var e : lay.levels().entrySet()) {
            int[] at = project(e.getValue().pos().getX(), e.getValue().pos().getZ());
            if (at[2] == 0) {
                String s = "Lv " + e.getValue().text();
                int w = font.width(s);
                g.fill(at[0] - w / 2 - 3, at[1] - 6, at[0] + w / 2 + 3, at[1] + 6, 0xE0F4EAD2);
                outlineRect(g, at[0] - w / 2 - 3, at[1] - 6, w + 6, 12, 0xFF7A5A3A);
                g.drawString(font, s, at[0] - w / 2, at[1] - 4, 0xFF7A3C1E, false);
            }
        }
    }

    static int ringStep(double range) {
        for (int st : RING_STEPS) {
            if (range / st <= 4.2) {
                return st;
            }
        }
        return RING_STEPS[RING_STEPS.length - 1];
    }

    // 距離の輪 (2〜4 本。縮尺に合わせた切りのよい数) と、その距離の数字 (輪の南の内側)
    private void drawRings(GuiGraphics g, Palette p) {
        int step = ringStep(view);
        boolean shadow = style() != Config.MapStyle.PARCHMENT;
        for (int d = step; d < view * 0.97; d += step) {
            int r = (int) Math.round(radius * d / view);
            if (r < 14) {
                continue;
            }
            circle(g, cx, cy, r, p.ring(), 1, 3);
            // 数字は南の十字の線の右、輪のすぐ内 (印の多い東西を避ける)
            String label = String.format("%,d", d);
            if (r > 20) {
                g.drawString(font, label, cx + 3, cy + r - 10, (p.dim() & 0x00FFFFFF) | 0xC0000000, shadow);
            }
        }
        if (style() != Config.MapStyle.SLATE) {
            circle(g, cx, cy, radius, p.ringStrong(), 1, 0);
        }
        // 十字の細い線 (方位の輪)
        dashed(g, cx - radius + 8, cy, cx + radius - 8, cy, p.ring(), 2, 4);
        dashed(g, cx, cy - radius + 8, cx, cy + radius - 8, p.ring(), 2, 4);
    }

    // 方位の刻みと N E S W (印の上に描く)
    private void drawCompass(GuiGraphics g, Palette p) {
        for (int k = 0; k < 32; k++) {
            double a = k / 32.0 * Math.PI * 2;
            int len = k % 8 == 0 ? 6 : k % 4 == 0 ? 4 : 2;
            int c = k % 8 == 0 ? p.accent() : p.ring();
            for (int s = 0; s < len; s++) {
                double rr = radius - 1 - s;
                int x = cx + (int) Math.round(Math.cos(a) * rr);
                int y = cy + (int) Math.round(Math.sin(a) * rr);
                g.fill(x, y, x + 1, y + 1, c);
            }
        }
        int lc = p.accent();
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.n"), cx, cy - radius - 13, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.s"), cx, cy + radius + 6, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.w"), cx - radius - 10, cy - 4, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.e"), cx + radius + 10, cy - 4, lc);
    }

    // 寄せたとき (半径 400 まで): 都の壁の四角 (点線) と大通りの十字
    private void drawTown(GuiGraphics g, Palette p) {
        if (view > 400 || sheet == null || !sheet.townKnown()) {
            return;
        }
        MapMarks.Mark town = null;
        for (MapMarks.Mark m : sheet.marks()) {
            if (m.kind() == MapMarks.Kind.TOWN && !m.rumor()) {
                town = m;
            }
        }
        if (town == null) {
            return;
        }
        int[] a = project(town.x() - TOWN_HALF, town.z() - TOWN_HALF);
        int[] b = project(town.x() + TOWN_HALF, town.z() + TOWN_HALF);
        if (a[2] == 1 || b[2] == 1) {
            return;
        }
        boolean parch = style() == Config.MapStyle.PARCHMENT;
        int c = (p.rim() & 0x00FFFFFF) | 0xB0000000;
        g.fill(a[0], a[1], b[0] + 1, b[1] + 1, parch ? 0x206B5236 : 0x28E2D9C2);
        dashed(g, a[0], a[1], b[0], a[1], c, 3, 2);
        dashed(g, a[0], b[1], b[0], b[1], c, 3, 2);
        dashed(g, a[0], a[1], a[0], b[1], c, 3, 2);
        dashed(g, b[0], a[1], b[0], b[1], c, 3, 2);
        int mx = (a[0] + b[0]) / 2;
        int my = (a[1] + b[1]) / 2;
        int sc = (p.rim() & 0x00FFFFFF) | 0x48000000;
        g.fill(a[0] + 1, my - 1, b[0], my + 1, sc);
        g.fill(mx - 1, a[1] + 1, mx + 1, b[1], sc);
    }

    private void drawCentered(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x - font.width(c) / 2, y, color, style() != Config.MapStyle.PARCHMENT);
    }

    // 右上の縮尺: 「半径 600」と、寄せ・引きのつまみ (− 目盛り ＋。押しても替わる)
    private static final int ZB = 11;
    private static final int GAUGE_W = 54;

    private int zoomRight() {
        return chart != null ? listX - 12 : fx + fw - 14;
    }

    private int plusX() {
        return zoomRight() - ZB;
    }

    private int minusX() {
        return plusX() - 8 - GAUGE_W - ZB;
    }

    private void drawHeader(GuiGraphics g, Palette p, int mouseX, int mouseY) {
        boolean shadow = style() != Config.MapStyle.PARCHMENT;
        Component t = chart != null ? chartTitle() : title;
        g.drawString(font, t, fx + 14, fy + 10, p.pale(), shadow);
        if (chart != null) {
            Component sub = chart.leader() ? Component.translatable("gui.skyabyss.travel.choose")
                    : Component.translatable("gui.skyabyss.travel.leader_chooses", chart.leaderName());
            g.drawString(font, sub, listX, fy + 22, p.dim(), shadow);
        }
        int y = fy + 8;
        int gaugeX = minusX() + ZB + 4;
        zoomButton(g, p, minusX(), y, false, mouseX, mouseY);
        zoomButton(g, p, plusX(), y, true, mouseX, mouseY);
        // 目盛り: 右ほど寄せている (対数)。開いたときの二つの縮尺 (町のまわり・世界) に刻み
        double span = Math.log(MAX_RANGE / MIN_RANGE);
        int gy = y + ZB / 2;
        g.fill(gaugeX, gy, gaugeX + GAUGE_W, gy + 1, p.ring() | 0xFF000000);
        for (double mark : new double[] {NEAR, FAR}) {
            int mx = gaugeX + (int) Math.round((1 - Math.log(mark / MIN_RANGE) / span) * GAUGE_W);
            g.fill(mx, gy - 2, mx + 1, gy + 3, p.dim());
        }
        int kx = gaugeX + (int) Math.round(Mth.clamp(1 - Math.log(view / MIN_RANGE) / span, 0, 1) * GAUGE_W);
        g.fill(kx - 2, gy - 3, kx + 3, gy + 4, 0xFF0C0E12);
        g.fill(kx - 1, gy - 2, kx + 2, gy + 3, p.accent());
        Component sc = Component.translatable("gui.skyabyss.map.scale", String.format("%,d", (int) Math.round(view / 10.0) * 10));
        g.drawString(font, sc, minusX() - 6 - font.width(sc), fy + 10, p.text(), shadow);
        Component foot = sheet == null ? Component.translatable("gui.skyabyss.map.waiting")
                : !onGround() ? Component.translatable("gui.skyabyss.map.elsewhere") : Component.translatable("gui.skyabyss.map.hint");
        g.drawString(font, foot, fx + 14, fy + fh - 14, p.dim(), shadow);
    }

    private void zoomButton(GuiGraphics g, Palette p, int x, int y, boolean plus, int mouseX, int mouseY) {
        boolean can = plus ? target > MIN_RANGE + 1e-6 : target < MAX_RANGE - 1e-6;
        boolean hover = can && inside(mouseX, mouseY, x, y, ZB, ZB);
        g.fill(x, y, x + ZB, y + ZB, 0xFF0C0E12);
        g.fill(x + 1, y + 1, x + ZB - 1, y + ZB - 1, hover ? 0xFF2A5E6A : 0xFF1C222C);
        int c = !can ? p.lost() : hover ? 0xFFFFFFFF : p.accent();
        g.fill(x + 3, y + ZB / 2, x + ZB - 3, y + ZB / 2 + 1, c);
        if (plus) {
            g.fill(x + ZB / 2, y + 3, x + ZB / 2 + 1, y + ZB - 3, c);
        }
    }

    private Component chartTitle() {
        String key = "gui.skyabyss.travel.title." + chart.titan();
        return Component.translatable(Language.getInstance().has(key) ? key : "gui.skyabyss.travel.title");
    }

    // ---------------------------------------------------------------- 印
    // 指したときの札 (画面の位置と、出す文)
    record Hit(int x, int y, int order, List<FormattedCharSequence> lines, @Nullable String destId) {
    }

    // 名前の札を出す印 (重なる札は、先に置いた (大事な) 方を残す)。priority が大きいほど先
    private record Label(int x, int y, Component text, int color, int priority) {
    }

    private void drawMarks(GuiGraphics g, Palette p, List<Hit> hits, int mouseX, int mouseY) {
        double[] me = self();
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        List<MapMarks.Mark> marks = sheet == null ? List.of() : sheet.marks();
        List<Label> labels = new ArrayList<>();
        List<int[]> icons = new ArrayList<>();
        // 道 (町から道しるべをつなぐ線) は描かない (2026-10-21 の試遊: ごちゃつく)
        // 下から: 噂 → 章の場所・碑・足跡・骨 → 野営地・町 → 巨神
        List<MapMarks.Mark> ordered = new ArrayList<>(marks);
        ordered.sort(Comparator.comparingInt(TravelMapScreen::layer));
        for (MapMarks.Mark m : ordered) {
            if (chart != null && (m.kind() == MapMarks.Kind.WAYMARK || m.kind() == MapMarks.Kind.TITAN || m.kind() == MapMarks.Kind.SPAWN)) {
                // 海図の行き先として描く (下)
                continue;
            }
            int[] at = project(m.x(), m.z());
            int rim = m.color() == 0 ? p.rim() : 0xFF000000 | m.color();
            if (m.rumor()) {
                sprite(g, S_RUMOR, at[0], at[1], 0, 0.85f);
            } else {
                backing(g, at[0], at[1], at[2] == 1 ? 0.45f : 0.75f);
                sprite(g, sprite(m.kind()), at[0], at[1], rim, at[2] == 1 ? 0.6f : 1f);
                icons.add(new int[] {at[0] - 8, at[1] - 8, 16, 16});
                int pr = labelPriority(m);
                if (pr > 0 && at[2] == 0) {
                    labels.add(new Label(at[0], at[1], MapText.name(m), m.kind() == MapMarks.Kind.TITAN || m.color() != 0 ? rim : p.text(), pr));
                }
            }
            List<Component> lines = new ArrayList<>();
            lines.add(MapText.name(m));
            lines.add(m.rumor() ? MapText.rumor(me, m.x(), m.z()) : MapText.where(me, m.x(), m.z()));
            hits.add(hit(at, layer(m), lines, null));
        }
        // 海図の行き先: 行けるものは光の輪が明滅する
        if (chart != null) {
            TravelNetwork.Dest sel = selectedDest();
            if (sel != null && (time / 8) % 2 == 0) {
                int[] a = project(chart.originX(), chart.originZ());
                int[] b = project(sel.x() + 0.5, sel.z() + 0.5);
                dashed(g, a[0], a[1], b[0], b[1], p.road(), 1, 4);
            }
            for (TravelNetwork.Dest d : chart.dests()) {
                int[] at = project(d.x() + 0.5, d.z() + 0.5);
                boolean ok = reachable(d);
                boolean isSel = d.id().equals(selected);
                if (ok) {
                    float pulse = 0.45f + 0.35f * (float) Math.sin((time + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * 0.25);
                    sprite(g, S_GLOW, at[0], at[1], 0, 0f, isSel ? 0xFF9FE8FF : 0xFF7FD6D0, isSel ? 0.95f : pulse);
                }
                int sp = d.kind() == 4 ? S_TOWN : d.kind() == 0 ? S_SPAWN : d.kind() == 2 ? S_TITAN : S_WAYMARK;
                int rim = d.kind() == 2 ? 0xFF000000 | TravelDest.titanColor(d.name()) : isSel ? 0xFF9FE8FF : p.rim();
                backing(g, at[0], at[1], ok ? 0.75f : 0.4f);
                sprite(g, sp, at[0], at[1], rim, ok ? 1f : 0.45f);
                icons.add(new int[] {at[0] - 8, at[1] - 8, 16, 16});
                if (isSel && at[2] == 0) {
                    labels.add(new Label(at[0], at[1], TravelDest.destName(d), 0xFF9FE8FF, 95));
                }
                List<Component> lines = new ArrayList<>();
                lines.add(TravelDest.destName(d));
                lines.add(MapText.where(me, d.x() + 0.5, d.z() + 0.5));
                lines.add(destDetail(d));
                hits.add(hit(at, 50, lines, d.valid() ? d.id() : null));
            }
        }
        // 墓・仲間・目的・自分 (クライアントが持っているもの)
        for (GraveMarkers.Mark gm : graves()) {
            if (!gm.dimension().equals(Level.OVERWORLD.location().toString())) {
                continue;
            }
            int[] at = project(gm.x() + 0.5, gm.z() + 0.5);
            backing(g, at[0], at[1], 0.75f);
            sprite(g, S_GRAVE, at[0], at[1], 0xFFD9779A, at[2] == 1 ? 0.6f : 1f);
            icons.add(new int[] {at[0] - 8, at[1] - 8, 16, 16});
            if (at[2] == 0) {
                labels.add(new Label(at[0], at[1], Component.translatable("map.skyabyss.mark.grave"), 0xFFD9779A, 60));
            }
            hits.add(hit(at, 60, List.of(Component.translatable("map.skyabyss.mark.grave"), MapText.where(me, gm.x() + 0.5, gm.z() + 0.5)), null));
        }
        for (PartyLocator.Entry e : party()) {
            if (!e.dimension().equals(Level.OVERWORLD.location().toString())) {
                continue;
            }
            int[] at = project(e.x() + 0.5, e.z() + 0.5);
            backing(g, at[0], at[1], 0.75f);
            sprite(g, S_PARTY, at[0], at[1], 0xFF8FE0FF, at[2] == 1 ? 0.6f : 1f);
            icons.add(new int[] {at[0] - 8, at[1] - 8, 16, 16});
            labels.add(new Label(at[0], at[1], Component.literal(e.name()), 0xFF8FE0FF, at[2] == 0 ? 85 : 84));
            hits.add(hit(at, 70, List.of(Component.literal(e.name()), MapText.where(me, e.x() + 0.5, e.z() + 0.5)), null));
        }
        BlockPos obj = objective();
        if (obj != null) {
            int[] at = project(obj.getX() + 0.5, obj.getZ() + 0.5);
            // 目的は明滅する光の輪の上に (いちばん目立つ印)
            float pulse = 0.35f + 0.3f * (float) Math.sin((time + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) * 0.2);
            sprite(g, S_GLOW, at[0], at[1], 0, 0f, 0xFFF2C14E, pulse);
            backing(g, at[0], at[1], 0.8f);
            sprite(g, S_OBJECTIVE, at[0], at[1], 0, at[2] == 1 ? 0.75f : 1f);
            icons.add(new int[] {at[0] - 8, at[1] - 8, 16, 16});
            labels.add(new Label(at[0], at[1], Component.translatable("map.skyabyss.mark.objective"), 0xFFF2C14E, 90));
            hits.add(hit(at, 80, List.of(Component.translatable("map.skyabyss.mark.objective"), MapText.where(me, obj.getX() + 0.5, obj.getZ() + 0.5)), null));
        }
        int[] selfAt = null;
        if (me != null) {
            selfAt = project(me[0], me[1]);
            // 自分: 暗い丸と、ゆっくり広がる輪の上に、2 倍の矢印
            float t = (time % 40 + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)) / 40f;
            int ring = 9 + (int) (t * 7);
            circle(g, selfAt[0], selfAt[1], ring, ((int) ((1 - t) * 0xB0) << 24) | (p.accent() & 0xFFFFFF), 1, 0);
            disc(g, selfAt[0], selfAt[1], 10, 0xC00A0D12);
            circle(g, selfAt[0], selfAt[1], 10, p.accent(), 1, 0);
            g.pose().pushPose();
            g.pose().translate(selfAt[0], selfAt[1], 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) me[2] + 180f));
            g.pose().scale(1.5f, 1.5f, 1f);
            sprite(g, S_SELF, 0, 0, 0xFFFFFFFF, 1f);
            g.pose().popPose();
            icons.add(new int[] {selfAt[0] - 11, selfAt[1] - 11, 22, 22});
            hits.add(hit(selfAt, 90, List.of(Component.translatable("map.skyabyss.mark.self")), null));
        }
        drawLabels(g, labels, icons);
    }

    // 名前の札を出す印の大事さ (0 = 出さない)。足跡・骨・碑・噂は、指したときだけ
    private int labelPriority(MapMarks.Mark m) {
        return switch (m.kind()) {
            case TOWN -> 80;
            case TITAN -> 75;
            case SHRINE -> 70;
            case SITE -> 65;
            case CAMP -> view <= 1200 ? 40 : 0;
            case SPAWN -> view <= 1200 ? 35 : 0;
            default -> 0;
        };
    }

    // 札: 印の右 (入らなければ左・下・上) に、暗い地の名前。ほかの札・印と重なる所には置かない
    private void drawLabels(GuiGraphics g, List<Label> labels, List<int[]> icons) {
        labels.sort(Comparator.comparingInt(Label::priority).reversed());
        List<int[]> placed = new ArrayList<>();
        boolean parch = style() == Config.MapStyle.PARCHMENT;
        for (Label l : labels) {
            String text = font.plainSubstrByWidth(l.text().getString(), 110);
            int w = font.width(text) + 6;
            int h = 11;
            int[][] spots = {{l.x() + 10, l.y() - 5}, {l.x() - 10 - w, l.y() - 5}, {l.x() - w / 2, l.y() + 10}, {l.x() - w / 2, l.y() - 10 - h}};
            for (int[] sp : spots) {
                int[] r = {sp[0], sp[1], w, h};
                if (Math.hypot(sp[0] + w / 2.0 - cx, sp[1] + h / 2.0 - cy) > radius - 6 && Math.hypot(sp[0] - cx, sp[1] - cy) > radius - 4) {
                    continue;
                }
                if (overlaps(r, placed) || overlapsIcons(r, icons, l)) {
                    continue;
                }
                placed.add(r);
                g.fill(r[0], r[1], r[0] + w, r[1] + h, parch ? 0xC8F2E6C8 : 0xC80A0D12);
                g.fill(r[0], r[1], r[0] + 1, r[1] + h, l.color());
                g.drawString(font, text, r[0] + 4, r[1] + 2, parch ? 0xFF3B2A1A : l.color(), !parch);
                break;
            }
        }
    }

    private static boolean overlaps(int[] r, List<int[]> list) {
        for (int[] o : list) {
            if (r[0] < o[0] + o[2] && o[0] < r[0] + r[2] && r[1] < o[1] + o[3] && o[1] < r[1] + r[3]) {
                return true;
            }
        }
        return false;
    }

    // 札が、自分の印ではないほかの印に重なるか
    private static boolean overlapsIcons(int[] r, List<int[]> icons, Label own) {
        for (int[] o : icons) {
            if (Math.abs(o[0] + o[2] / 2 - own.x()) <= 1 && Math.abs(o[1] + o[3] / 2 - own.y()) <= 1) {
                continue;
            }
            if (r[0] < o[0] + o[2] - 3 && o[0] + 3 < r[0] + r[2] && r[1] < o[1] + o[3] - 3 && o[1] + 3 < r[1] + r[3]) {
                return true;
            }
        }
        return false;
    }

    // 印の下の暗い丸 (どんな地の上でも印が浮く)
    private void backing(GuiGraphics g, int x, int y, float alpha) {
        int a = (int) (alpha * (style() == Config.MapStyle.PARCHMENT ? 0x50 : 0x90));
        disc(g, x, y, 8, a << 24 | (style() == Config.MapStyle.PARCHMENT ? 0x3B2A1A : 0x05070A));
    }

    // 塗りつぶした丸 (半分の画素で)
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        int rr = r * 2;
        for (int dy = -rr; dy <= rr; dy++) {
            int half = (int) Math.floor(Math.sqrt(rr * rr - dy * dy));
            g.fill(cx * 2 - half, cy * 2 + dy, cx * 2 + half + 1, cy * 2 + dy + 1, color);
        }
        g.pose().popPose();
    }

    private static int layer(MapMarks.Mark m) {
        if (m.rumor()) {
            return 0;
        }
        return switch (m.kind()) {
            case SITE, WAYMARK, SHRINE -> 10;
            case FOOTPRINT, RIBS, SPAWN -> 20;
            case CAMP -> 30;
            case TOWN -> 35;
            case TITAN -> 40;
        };
    }

    static int sprite(MapMarks.Kind kind) {
        return switch (kind) {
            case TOWN -> S_TOWN;
            case CAMP -> S_CAMP;
            case FOOTPRINT -> S_FOOTPRINT;
            case RIBS -> S_RIBS;
            case WAYMARK -> S_WAYMARK;
            case SITE -> S_SITE;
            case TITAN -> S_TITAN;
            case SHRINE -> S_SHRINE;
            case SPAWN -> S_SPAWN;
        };
    }

    private Hit hit(int[] at, int order, List<Component> lines, @Nullable String destId) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            out.add(c.getVisualOrderText());
        }
        return new Hit(at[0], at[1], order, out, destId);
    }

    @Nullable
    private static Hit nearest(List<Hit> hits, int mx, int my) {
        Hit best = null;
        double bestD = 7;
        for (Hit h : hits) {
            double d = Math.hypot(h.x() - mx, h.y() - my);
            if (d < bestD || (best != null && d <= bestD + 0.01 && h.order() > best.order())) {
                best = h;
                bestD = d;
            }
        }
        return best;
    }

    private List<GraveMarkers.Mark> graves() {
        if (demo) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
            return List.of(new GraveMarkers.Mark(Level.OVERWORLD.location().toString(), at.getX() + 150, 64, at.getZ() + 60));
        }
        return GraveMarkerHud.marks();
    }

    private List<PartyLocator.Entry> party() {
        if (demo) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
            return List.of(new PartyLocator.Entry("demo", "Alice", Level.OVERWORLD.location().toString(), at.getX() + 60, 64, at.getZ() - 20, 80));
        }
        return PartyMarkers.entries();
    }

    @Nullable
    private BlockPos objective() {
        Minecraft mc = Minecraft.getInstance();
        if (demo) {
            BlockPos at = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
            return at.offset(-170, 0, -40);
        }
        ObjectiveStrip.Resolved r = ObjectiveStrip.resolve(mc);
        return r.inside() ? null : r.pos();
    }

    // ---------------------------------------------------------------- 子の海図の一覧とつまみ (旧 TravelChartScreen)
    private double distance(TravelNetwork.Dest d) {
        return Math.hypot(d.x() + 0.5 - chart.originX(), d.z() + 0.5 - chart.originZ());
    }

    private boolean reachable(TravelNetwork.Dest d) {
        return d.valid() && distance(d) >= 64;
    }

    @Nullable
    private TravelNetwork.Dest selectedDest() {
        if (chart == null) {
            return null;
        }
        for (TravelNetwork.Dest d : chart.dests()) {
            if (d.id().equals(selected)) {
                return d;
            }
        }
        return null;
    }

    private Component destDetail(TravelNetwork.Dest d) {
        if (!d.valid()) {
            return d.lock().isEmpty() ? Component.translatable("gui.skyabyss.travel.lost") : Component.translatable("gui.skyabyss.young.lock." + d.lock());
        }
        double dist = distance(d);
        if (dist < 64) {
            return Component.translatable("gui.skyabyss.travel.here");
        }
        return Component.translatable("gui.skyabyss.travel.detail", String.format("%,d", (int) dist), MapText.dir(d.x() + 0.5 - chart.originX(),
                d.z() + 0.5 - chart.originZ()), d.seconds());
    }

    private void drawList(GuiGraphics g, Palette p, int mouseX, int mouseY) {
        List<TravelNetwork.Dest> dests = chart.dests();
        scroll = Mth.clamp(scroll, 0, Math.max(0, dests.size() - listRows));
        for (int i = 0; i < listRows && scroll + i < dests.size(); i++) {
            TravelNetwork.Dest d = dests.get(scroll + i);
            int x = listX;
            int y = listY + i * ROW_H;
            boolean hover = d.valid() && inside(mouseX, mouseY, x, y, ROW_W, ROW_H - 1);
            boolean sel = d.id().equals(selected);
            chartBlit(g, x, y, sel ? 544 : hover ? 272 : 0, 384, 264, 44);
            int color = !d.valid() ? SLATE.lost() : reachable(d) ? SLATE.text() : SLATE.dim();
            if (d.kind() == 2 && d.valid() && reachable(d)) {
                color = 0xFF000000 | TravelDest.titanColor(d.name());
            }
            g.drawString(font, font.plainSubstrByWidth(TravelDest.destName(d).getString(), ROW_W - 18), x + 5, y + 3, color, false);
            g.drawString(font, destDetail(d), x + 5, y + 12, d.valid() ? SLATE.dim() : SLATE.lost(), false);
            if (d.kind() == 1 && d.valid() && chart.leader() && hover) {
                chartBlit(g, x + ROW_W - 11, y + 3, 464, 432, 16, 16);
            }
        }
        if (scroll > 0) {
            chartBlit(g, listX + ROW_W - 9, listY - 9, 512, 432, 16, 16);
        }
        if (scroll + listRows < dests.size()) {
            chartBlit(g, listX + ROW_W - 9, listY + listRows * ROW_H, 528, 432, 16, 16);
        }
        if (dests.size() <= 1) {
            g.drawString(font, Component.translatable("gui.skyabyss.travel.no_waymarks"), listX + 4, listY + ROW_H + 4, SLATE.dim(), false);
            g.drawString(font, Component.translatable("gui.skyabyss.travel.no_waymarks2"), listX + 4, listY + ROW_H + 14, SLATE.dim(), false);
        }
    }

    private int departX() {
        return listX + 2;
    }

    private int leaveX() {
        return listX + 2 + BTN_W + 8;
    }

    private boolean canDepart() {
        TravelNetwork.Dest sel = selectedDest();
        return chart.leader() && chart.chosen().isEmpty() && sel != null && reachable(sel);
    }

    private boolean canLeave() {
        return chart.chosen().isEmpty() || !chart.leader();
    }

    private void drawButtons(GuiGraphics g, Palette p, int mouseX, int mouseY) {
        button(g, departX(), Component.translatable(chart.chosen().isEmpty() ? "gui.skyabyss.travel.depart" : "gui.skyabyss.travel.departing"),
                canDepart(), mouseX, mouseY);
        button(g, leaveX(), Component.translatable("gui.skyabyss.travel.disembark"), canLeave(), mouseX, mouseY);
    }

    private void button(GuiGraphics g, int x, Component label, boolean active, int mouseX, int mouseY) {
        boolean hover = active && inside(mouseX, mouseY, x, btnY, BTN_W, BTN_H);
        chartBlit(g, x, btnY, !active ? 256 : hover ? 128 : 0, 432, 120, 32);
        g.drawCenteredString(font, label, x + BTN_W / 2, btnY + 4, active ? (hover ? SLATE.pale() : SLATE.text()) : SLATE.dim());
    }

    // ---------------------------------------------------------------- 操作
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (MapClient.MAP_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        // ＋／− (テンキーも) で寄せる・引く。0 で開いたときの縮尺と自分へ戻す
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) {
            zoomBy(1, cx, cy);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomBy(-1, cx, cy);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_0 || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0) {
            target = chart != null ? FAR : NEAR;
            anchorX = cx;
            anchorY = cy;
            panX = 0;
            panZ = 0;
            click(1.1f);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (chart != null && mx >= listX - 4) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, chart.dests().size() - listRows));
            return true;
        }
        if (dy != 0) {
            zoomBy(Math.signum(dy), mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mouseX = (int) mx;
        int mouseY = (int) my;
        if (button == 0 && inside(mouseX, mouseY, minusX(), fy + 8, ZB, ZB)) {
            zoomBy(-1, cx, cy);
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, plusX(), fy + 8, ZB, ZB)) {
            zoomBy(1, cx, cy);
            return true;
        }
        if (button == 1 && radius > 0 && Math.hypot(mx - cx, my - cy) <= radius + 8) {
            panX = 0;
            panZ = 0;
            return true;
        }
        if (chart == null) {
            return super.mouseClicked(mx, my, button);
        }
        List<TravelNetwork.Dest> dests = chart.dests();
        for (int i = 0; i < listRows && scroll + i < dests.size(); i++) {
            TravelNetwork.Dest d = dests.get(scroll + i);
            int x = listX;
            int y = listY + i * ROW_H;
            if (inside(mouseX, mouseY, x, y, ROW_W, ROW_H - 1)) {
                if (!d.valid()) {
                    return true;
                }
                if (d.kind() == 1 && chart.leader() && inside(mouseX, mouseY, x + ROW_W - 12, y + 2, 10, 10)) {
                    if (!demo) {
                        PacketDistributor.sendToServer(new TravelNetwork.Forget(d.id()));
                    }
                    click(0.8f);
                    return true;
                }
                selected = d.id();
                click(1.2f);
                return true;
            }
        }
        // 地図の上の行き先の印
        TravelNetwork.Dest best = null;
        double bestD = 7;
        for (TravelNetwork.Dest d : dests) {
            int[] at = project(d.x() + 0.5, d.z() + 0.5);
            double dist = Math.hypot(at[0] - mouseX, at[1] - mouseY);
            if (d.valid() && dist < bestD) {
                best = d;
                bestD = dist;
            }
        }
        if (best != null && mouseX < listX - 4) {
            selected = best.id();
            click(1.2f);
            return true;
        }
        TravelNetwork.Dest sel = selectedDest();
        if (inside(mouseX, mouseY, departX(), btnY, BTN_W, BTN_H) && canDepart() && sel != null) {
            if (!demo) {
                PacketDistributor.sendToServer(new TravelNetwork.Choose(sel.id()));
            }
            click(0.7f);
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, leaveX(), btnY, BTN_W, BTN_H) && canLeave()) {
            if (!demo) {
                PacketDistributor.sendToServer(new TravelNetwork.Disembark());
            }
            click(0.9f);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---------------------------------------------------------------- 描く道具
    private static void chartBlit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        g.blit(CHART, x, y, w / 2, h / 2, u, v, w, h, 1024, 512);
    }

    // 印を中心 (x, y) に 16x16 で。rim = 縁取りの色 (0 なら縁なし)、alpha = 濃さ
    private static void sprite(GuiGraphics g, int index, int x, int y, int rim, float alpha) {
        sprite(g, index, x, y, rim, alpha, 0, 0f);
    }

    private static void sprite(GuiGraphics g, int index, int x, int y, int rim, float alpha, int tint, float tintAlpha) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (tint != 0) {
            g.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, tintAlpha);
            g.blit(MARKS, x - 8, y - 8, 16, 16, index * 32, 0, 32, 32, 512, 64);
            g.setColor(1f, 1f, 1f, 1f);
            return;
        }
        if (rim != 0) {
            g.setColor(((rim >> 16) & 0xFF) / 255f, ((rim >> 8) & 0xFF) / 255f, (rim & 0xFF) / 255f, alpha);
            g.blit(MARKS, x - 8, y - 8, 16, 16, index * 32, 32, 32, 32, 512, 64);
        }
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(MARKS, x - 8, y - 8, 16, 16, index * 32, 0, 32, 32, 512, 64);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // 地の模様 (paper.png の which 番目の 128x128 = GUI 64x64) を敷き詰める
    private static void tile(GuiGraphics g, int which, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 64) {
            for (int tx = x; tx < x + w; tx += 64) {
                g.blit(PAPER, tx, ty, 64, 64, which * 128, 0, 128, 128, 384, 128);
            }
        }
        g.disableScissor();
    }

    // 骨の枠 (gen_forge_assets.bone_frame と同じ作り。半分の画素で描く)。thin = 羊皮紙のまわりの細めの枠
    private static void frame(GuiGraphics g, int x, int y, int w, int h, boolean thin) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        int x0 = x * 2;
        int y0 = y * 2;
        int x1 = x0 + w * 2 - 1;
        int y1 = y0 + h * 2 - 1;
        int band = thin ? 5 : 6;
        rect2(g, x0, y0, x1, y1, 0xFF0C0E12);
        for (int i = 1; i <= band; i++) {
            int c = i <= 2 ? 0xFFE2D9C2 : i <= 4 ? 0xFFC4BAA0 : 0xFFA0967C;
            rect2(g, x0 + i, y0 + i, x1 - i, y1 - i, c);
        }
        for (int i = 1; i <= band; i++) {
            int shade = i <= 3 ? 0xFF6E6654 : 0xFFA0967C;
            g.fill(x1 - i, y0 + i, x1 - i + 1, y1 - i + 1, shade);
            g.fill(x0 + i, y1 - i, x1 - i + 1, y1 - i + 1, shade);
        }
        rect2(g, x0 + band + 1, y0 + band + 1, x1 - band - 1, y1 - band - 1, 0xFF0C0E12);
        for (int tx = x0 + 24; tx < x1 - 16; tx += 24) {
            g.fill(tx, y0 + 2, tx + 1, y0 + band + 1, 0xFF6E6654);
            g.fill(tx + 1, y0 + 2, tx + 2, y0 + band + 1, 0xFFE2D9C2);
            g.fill(tx, y1 - band, tx + 1, y1 - 1, 0xFF6E6654);
        }
        for (int ty = y0 + 24; ty < y1 - 16; ty += 24) {
            g.fill(x0 + 2, ty, x0 + band + 1, ty + 1, 0xFF6E6654);
            g.fill(x1 - band, ty, x1 - 1, ty + 1, 0xFF6E6654);
        }
        for (int[] c : new int[][]{{x0 + 7, y0 + 7}, {x1 - 7, y0 + 7}, {x0 + 7, y1 - 7}, {x1 - 7, y1 - 7}}) {
            gem2(g, c[0], c[1], 6);
        }
        g.pose().popPose();
    }

    private static void rect2(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1 + 1, y0 + 1, c);
        g.fill(x0, y1, x1 + 1, y1 + 1, c);
        g.fill(x0, y0, x0 + 1, y1 + 1, c);
        g.fill(x1, y0, x1 + 1, y1 + 1, c);
    }

    // 青緑のひし形 (半分の画素の座標で)
    private static void gem2(GuiGraphics g, int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, 0xFF0C0E12);
            if (half >= 1) {
                g.fill(cx - half + 1, cy + dy, cx + half, cy + dy + 1, 0xFF2A5E6A);
            }
            // 右上の明るい面
            if (dy < 0 && half >= 2) {
                g.fill(cx, cy + dy, cx + half - 1, cy + dy + 1, 0xFF7FD6D0);
            }
        }
        g.fill(cx - 1, cy - r + 3, cx, cy - r + 4, 0xFF9FE8FF);
    }

    private static void gem(GuiGraphics g, int x, int y) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        gem2(g, x * 2, y * 2, 6);
        g.pose().popPose();
    }

    private static void recess(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0F1117);
        g.fill(x, y, x + w, y + 1, 0xFF0C0E12);
        g.fill(x, y, x + 1, y + h, 0xFF0C0E12);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF343A48);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF343A48);
    }

    private static void outlineRect(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    // 円 (半分の画素で点を打つ)。dash > 0 なら点線
    private static void circle(GuiGraphics g, int cx, int cy, int r, int color, int thick, int dash) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        for (int t = 0; t < thick; t++) {
            double rr = r * 2 + t;
            int steps = (int) Math.ceil(Math.PI * 2 * rr);
            for (int i = 0; i < steps; i++) {
                if (dash > 0 && (i / (dash * 2)) % 2 == 1) {
                    continue;
                }
                double a = i / (double) steps * Math.PI * 2;
                int x = (int) Math.round(cx * 2 + Math.cos(a) * rr);
                int y = (int) Math.round(cy * 2 + Math.sin(a) * rr);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
        g.pose().popPose();
    }

    // 点線 (GUI の画素で)。on = 点の長さ、gap = 間
    private static void dashed(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int on, int gap) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 1) {
            return;
        }
        for (double t = 0; t < len; t += on + gap) {
            for (int k = 0; k < on && t + k < len; k++) {
                int x = x0 + (int) Math.round((x1 - x0) * (t + k) / len);
                int y = y0 + (int) Math.round((y1 - y0) * (t + k) / len);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
}
