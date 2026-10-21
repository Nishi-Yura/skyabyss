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
// 縮尺は 2 段 (町のまわり 600 / 世界 2,400。ホイールで替える)。印を指すと名前と「北東 420」(自分からの方角と距離)。
// 巨神の子に乗っているとき (chart != null) は、子の海図 (旧 TravelChartScreen) をこの画面に重ねる: 行ける行き先の印が光り、
// 印か右の一覧で選んで「出発」。印の出どころはサーバーの MapMarks (一か所) と、クライアントが持つ自分・仲間・墓・目的の標。
// 見た目は 3 案 (設定 mapStyle。SLATE = 石板の枠に丸い海図 (既定)、PARCHMENT = 骨の枠に羊皮紙、INK = 枠なしの墨の夜図)。
// 絵は tools/gen_travel_map_assets.py (印 marks.png・地 paper.png・A の海図 disc_a.png)、一覧の行とつまみは海図の chart.png
public class TravelMapScreen extends Screen {
    static final ResourceLocation MARKS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/marks.png");
    static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/paper.png");
    static final ResourceLocation DISC_A = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/map/disc_a.png");
    static final ResourceLocation CHART = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/travel/chart.png");

    // 印の絵の番号 (marks.png の並び。gen_travel_map_assets.py の SPRITES と同じ)
    static final int S_TOWN = 0, S_CAMP = 1, S_FOOTPRINT = 2, S_RIBS = 3, S_WAYMARK = 4, S_SITE = 5, S_TITAN = 6, S_GRAVE = 7,
            S_PARTY = 8, S_OBJECTIVE = 9, S_RUMOR = 10, S_SELF = 11, S_SPAWN = 12, S_SHRINE = 13, S_GLOW = 14, S_CAIRN = 15;

    // 縮尺 (中心から縁までのブロック)
    static final int[] RANGE = {600, 2400};
    static final int[][] RINGS = {{200, 400, 600}, {600, 1200, 1800, 2400}};

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
    private int scale;
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
        this.scale = chart != null ? 1 : 0;
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
    private int centerX() {
        if (sheet != null) {
            return sheet.centerX();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.getBlockX();
    }

    private int centerZ() {
        if (sheet != null) {
            return sheet.centerZ();
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.getBlockZ();
    }

    // 世界の (x, z) を画面へ。縁より外は縁に寄せる (out[2] = 1 なら縁に寄せた)
    private int[] project(double x, double z) {
        double k = radius / (double) RANGE[scale];
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
        Palette p = palette();
        drawSheet(g, p);
        drawRings(g, p);
        List<Hit> hits = new ArrayList<>();
        drawMarks(g, p, hits, mouseX, mouseY);
        drawHeader(g, p);
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

    private void drawRings(GuiGraphics g, Palette p) {
        int[] rings = RINGS[scale];
        for (int i = 0; i < rings.length; i++) {
            int r = (int) Math.round(radius * rings[i] / (double) RANGE[scale]);
            boolean outer = i == rings.length - 1;
            if (style() == Config.MapStyle.SLATE && outer) {
                continue;
            }
            circle(g, cx, cy, r, outer ? p.ringStrong() : p.ring(), 1, outer ? 0 : 3);
        }
        // 方位の刻みと N E S W
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
        // 十字の細い線 (方位の輪)
        dashed(g, cx - radius + 8, cy, cx + radius - 8, cy, p.ring(), 2, 4);
        dashed(g, cx, cy - radius + 8, cx, cy + radius - 8, p.ring(), 2, 4);
        int lc = p.accent();
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.travel.dir.n"), cx, cy - radius - 13, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.s"), cx, cy + radius + 6, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.w"), cx - radius - 10, cy - 4, lc);
        drawCentered(g, Component.translatable("gui.skyabyss.travel.dir.e"), cx + radius + 10, cy - 4, lc);
    }

    private void drawCentered(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x - font.width(c) / 2, y, color, style() != Config.MapStyle.PARCHMENT);
    }

    private void drawHeader(GuiGraphics g, Palette p) {
        boolean shadow = style() != Config.MapStyle.PARCHMENT;
        Component t = chart != null ? chartTitle() : title;
        g.drawString(font, t, fx + 14, fy + 10, p.pale(), shadow);
        if (chart != null) {
            Component sub = chart.leader() ? Component.translatable("gui.skyabyss.travel.choose")
                    : Component.translatable("gui.skyabyss.travel.leader_chooses", chart.leaderName());
            g.drawString(font, sub, listX, fy + 22, p.dim(), shadow);
        }
        Component sc = Component.translatable(scale == 0 ? "gui.skyabyss.map.scale.near" : "gui.skyabyss.map.scale.far");
        int right = chart != null ? listX - 12 : fx + fw - 14;
        g.drawString(font, sc, right - font.width(sc), fy + 10, p.dim(), shadow);
        Component foot = sheet == null ? Component.translatable("gui.skyabyss.map.waiting")
                : !onGround() ? Component.translatable("gui.skyabyss.map.elsewhere") : Component.translatable("gui.skyabyss.map.hint");
        g.drawString(font, foot, fx + 14, fy + fh - 14, p.dim(), shadow);
    }

    private Component chartTitle() {
        String key = "gui.skyabyss.travel.title." + chart.titan();
        return Component.translatable(Language.getInstance().has(key) ? key : "gui.skyabyss.travel.title");
    }

    // ---------------------------------------------------------------- 印
    // 指したときの札 (画面の位置と、出す文)
    record Hit(int x, int y, int order, List<FormattedCharSequence> lines, @Nullable String destId) {
    }

    private void drawMarks(GuiGraphics g, Palette p, List<Hit> hits, int mouseX, int mouseY) {
        double[] me = self();
        long time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        List<MapMarks.Mark> marks = sheet == null ? List.of() : sheet.marks();
        // 道しるべの線: 町から積み石を近い順にたどり、野営地まで
        drawRoad(g, p, marks);
        // 下から: 噂 → 章の場所・碑・足跡・骨 → 野営地・町 → 巨神
        List<MapMarks.Mark> ordered = new ArrayList<>(marks);
        ordered.sort(Comparator.comparingInt(TravelMapScreen::layer));
        for (MapMarks.Mark m : ordered) {
            if (m.kind() == MapMarks.Kind.CAIRN) {
                continue;
            }
            if (chart != null && (m.kind() == MapMarks.Kind.WAYMARK || m.kind() == MapMarks.Kind.TITAN || m.kind() == MapMarks.Kind.SPAWN)) {
                // 海図の行き先として描く (下)
                continue;
            }
            int[] at = project(m.x(), m.z());
            int rim = m.color() == 0 ? p.rim() : 0xFF000000 | m.color();
            if (m.rumor()) {
                sprite(g, S_RUMOR, at[0], at[1], 0, 0.85f);
            } else {
                sprite(g, sprite(m.kind()), at[0], at[1], rim, at[2] == 1 ? 0.6f : 1f);
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
                int sp = d.kind() == 0 ? S_SPAWN : d.kind() == 2 ? S_TITAN : S_WAYMARK;
                int rim = d.kind() == 2 ? 0xFF000000 | TravelDest.titanColor(d.name()) : isSel ? 0xFF9FE8FF : p.rim();
                sprite(g, sp, at[0], at[1], rim, ok ? 1f : 0.45f);
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
            sprite(g, S_GRAVE, at[0], at[1], 0xFFD9779A, at[2] == 1 ? 0.6f : 1f);
            hits.add(hit(at, 60, List.of(Component.translatable("map.skyabyss.mark.grave"), MapText.where(me, gm.x() + 0.5, gm.z() + 0.5)), null));
        }
        for (PartyLocator.Entry e : party()) {
            if (!e.dimension().equals(Level.OVERWORLD.location().toString())) {
                continue;
            }
            int[] at = project(e.x() + 0.5, e.z() + 0.5);
            sprite(g, S_PARTY, at[0], at[1], 0xFF8FE0FF, at[2] == 1 ? 0.6f : 1f);
            hits.add(hit(at, 70, List.of(Component.literal(e.name()), MapText.where(me, e.x() + 0.5, e.z() + 0.5)), null));
        }
        BlockPos obj = objective();
        if (obj != null) {
            int[] at = project(obj.getX() + 0.5, obj.getZ() + 0.5);
            sprite(g, S_OBJECTIVE, at[0], at[1], 0, at[2] == 1 ? 0.7f : 1f);
            hits.add(hit(at, 80, List.of(Component.translatable("map.skyabyss.mark.objective"), MapText.where(me, obj.getX() + 0.5, obj.getZ() + 0.5)), null));
        }
        if (me != null) {
            int[] at = project(me[0], me[1]);
            g.pose().pushPose();
            g.pose().translate(at[0], at[1], 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) me[2] + 180f));
            sprite(g, S_SELF, 0, 0, p.rim(), 1f);
            g.pose().popPose();
            hits.add(hit(at, 90, List.of(Component.translatable("map.skyabyss.mark.self")), null));
        }
    }

    private static int layer(MapMarks.Mark m) {
        if (m.rumor()) {
            return 0;
        }
        return switch (m.kind()) {
            case CAIRN -> 0;
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
            case CAIRN -> S_CAIRN;
            case SPAWN -> S_SPAWN;
        };
    }

    private void drawRoad(GuiGraphics g, Palette p, List<MapMarks.Mark> marks) {
        MapMarks.Mark town = null;
        MapMarks.Mark camp = null;
        List<MapMarks.Mark> cairns = new ArrayList<>();
        for (MapMarks.Mark m : marks) {
            if (m.kind() == MapMarks.Kind.TOWN && !m.rumor()) {
                town = m;
            } else if (m.kind() == MapMarks.Kind.CAMP) {
                camp = m;
            } else if (m.kind() == MapMarks.Kind.CAIRN) {
                cairns.add(m);
            }
        }
        if (town == null || cairns.isEmpty()) {
            return;
        }
        MapMarks.Mark from = town;
        cairns.sort(Comparator.comparingDouble(c -> Math.hypot(c.x() - from.x(), c.z() - from.z())));
        List<MapMarks.Mark> path = new ArrayList<>();
        path.add(town);
        path.addAll(cairns);
        if (camp != null) {
            path.add(camp);
        }
        for (int i = 0; i + 1 < path.size(); i++) {
            int[] a = project(path.get(i).x(), path.get(i).z());
            int[] b = project(path.get(i + 1).x(), path.get(i + 1).z());
            dashed(g, a[0], a[1], b[0], b[1], p.road(), 1, 3);
        }
        for (MapMarks.Mark c : cairns) {
            int[] a = project(c.x(), c.z());
            g.fill(a[0] - 1, a[1] - 1, a[0] + 2, a[1] + 2, 0xFF0C0E12);
            g.fill(a[0], a[1], a[0] + 1, a[1] + 1, p.road() | 0xFF000000);
        }
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
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (chart != null && mx >= listX - 4) {
            scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, chart.dests().size() - listRows));
            return true;
        }
        int next = dy > 0 ? 0 : 1;
        if (next != scale) {
            scale = next;
            click(next == 0 ? 1.3f : 1.0f);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mouseX = (int) mx;
        int mouseY = (int) my;
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
