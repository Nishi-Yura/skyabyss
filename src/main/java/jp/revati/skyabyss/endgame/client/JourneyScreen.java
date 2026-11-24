package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.LedgerStage;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.Journey;
import jp.revati.skyabyss.endgame.RegionFame;
import jp.revati.skyabyss.rpg.JobMastery;
import jp.revati.skyabyss.rpg.client.skill.RpgTabs;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// 旅の記録 (記録・技のつまみの「旅」。docs/design/54 の 6.2.7)。見るだけの画面:
//   土地ごと (都のまわり・鯨見・汐守・風待・灰炉): 名声の段とバー、名所の数、今週の旗。乗せると点・値引き・週の点
//   図鑑: そろえた巨神の装備の表の数と敵の図鑑 (乗せると巨神ごとの数。押すと狩人の手帳)
//   熟練: いまの職の段とバー (Lv 50 からたまる)
// 中身は開いたときにサーバーへ頼む (Journey.Request → Snapshot)。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1): つまみはスキルの木と同じ左上、その右に題。
// 左の黒い紙に土地の 5 行、右の生成りの帯に図鑑と熟練。左下の山の上に記録の画面への釦
public class JourneyScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/journey/journey.png");
    static final int TEX = 512;
    // 土地の行
    static final int ROW_X = LedgerStage.PAGE_X;
    static final int ROW_W = LedgerStage.PAGE_W;
    static final int ROW_Y = LedgerStage.K_BODY_Y + 2;
    static final int ROW_H = 30;
    static final int ROW_STEP = 32;
    // 右の帯: 図鑑 (上) と熟練 (下)
    static final int SIDE_X = LedgerStage.INDEX_X;
    static final int SIDE_W = LedgerStage.INDEX_W;
    static final int CODEX_Y = 14;
    static final int CODEX_BOTTOM = 96;
    static final int MASTERY_Y = 104;
    static final int MASTERY_BOTTOM = 212;

    static final int TEXT = Golden.TEXT;
    static final int SOFT = Paper.ON_BLACK_SOFT;
    static final int DIM = Paper.ON_BLACK_DIM;
    static final int GOLD = Paper.ON_BLACK_PINK;
    static final int TEAL = Paper.ON_BLACK_HI;
    // 土地の印の色 (都の鐘・岩鯨の岩・潮殻の潮・雷翼の雷・余燼の熾)
    static final int[] LAND_RGB = {0xD8C27A, 0, 0xB8A27E, 0x8FD0FF, 0x9FB8FF, 0xFF9A5A}; // 意味の色 (土地ごと。切り絵でも替えない)

    @Nullable
    private static CompoundTag data;

    private final UiFit fit = new UiFit();
    private List<FormattedCharSequence> pendingTip;

    public JourneyScreen() {
        super(Component.translatable("gui.skyabyss.journey.title"));
    }

    public static void receive(Journey.Snapshot snap) {
        data = snap.data();
    }

    // つまみを出すか (Lv 10 から。序盤は名声も図鑑も空なので見せない)
    public static boolean visible(Player p) {
        var g = jp.revati.skyabyss.rpg.Growth.get(p);
        return g.rebirths > 0 || g.level >= 10;
    }

    @Override
    protected void init() {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        RpgTabs.addHead(this::addRenderableWidget, LedgerStage.K_HEAD, RpgTabs.JOURNEY, true);
        // 記録の画面 (踏破・塔・深み・釣り・早駆け・転生・空見。record/RecordsHub) を開く釦。左下の山の上
        addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.records_hub.open"),
                b -> jp.revati.skyabyss.client.record.RecordsHubScreen.request(jp.revati.skyabyss.record.RecordsHub.CLEAR))
                .bounds(ROW_X, LedgerStage.footBtn(LedgerStage.FOOT_Y + 4), 70, Golden.BTN).build());
        PacketDistributor.sendToServer(new Journey.Request());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RpgTabs.keyPressed(keyCode, scanCode, RpgTabs.JOURNEY)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height, LedgerStage.HEAD_K, true);
        LedgerStage.kTitle(g, font, title, LedgerStage.K_HEAD.x() + RpgTabs.headWidth(RpgTabs.JOURNEY));
        if (data == null) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.loading"), ROW_X, 100, ROW_W, SOFT, false);
        } else {
            renderLands(g, mx, my);
            renderCodex(g, mx, my);
            renderMastery(g, mx, my);
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        fit.end(g);
        if (pendingTip != null) {
            g.renderTooltip(font, pendingTip, screenX, screenY);
        }
    }

    private static boolean codexHit(double mx, double my) {
        return mx >= SIDE_X - 4 && mx < SIDE_X + SIDE_W && my >= CODEX_Y && my < CODEX_BOTTOM;
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        // 図鑑を押すと狩人の手帳
        if (button == 0 && data != null && codexHit(fit.x(screenX), fit.y(screenY))) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new HunterNotesScreen());
            return true;
        }
        return super.mouseClicked(fit.x(screenX), fit.y(screenY), button);
    }

    private void tip(List<Component> lines) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : lines) {
            out.addAll(font.split(c, 200));
        }
        pendingTip = out;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- 土地の行 (黒い紙の上)
    private void renderLands(GuiGraphics g, int mx, int my) {
        ListTag lands = data.getList("lands", Tag.TAG_COMPOUND);
        for (int i = 0; i < lands.size() && i < 5; i++) {
            CompoundTag l = lands.getCompound(i);
            int region = l.getInt("r");
            int x = ROW_X;
            int y = ROW_Y + i * ROW_STEP;
            int right = ROW_X + ROW_W;
            int y1 = y + 3;
            int y2 = y + 16;
            if (i > 0) {
                for (int d = x; d < right; d += 3) {
                    g.fill(d, y - 2, d + 1, y - 1, Paper.PINK_LO);
                }
            }
            landGem(g, x + 5, y + 13, LAND_RGB[Math.max(0, Math.min(LAND_RGB.length - 1, region))]);
            int found = l.getInt("found");
            int total = l.getInt("total");
            Component sights = Component.translatable("gui.skyabyss.journey.sights", found, total);
            int sw = font.width(sights);
            FitText.draw(g, font, RegionFame.landName(region), x + 16, y1, right - x - 16 - sw - 6, TEXT, false);
            g.drawString(font, sights, right - sw, y1, total > 0 && found >= total ? TEAL : SOFT, false);
            boolean town = RegionFame.townLand(region);
            int fame = l.getInt("fame");
            if (town) {
                int t = RegionFame.tier(fame);
                Component tier = Component.translatable("endgame.skyabyss.fame.tier." + t);
                int tw = FitText.draw(g, font, tier, x + 16, y2, 68, t >= RegionFame.STEPS.length ? GOLD : t > 0 ? TEXT : DIM, false);
                int bx = x + 16 + Math.max(tw, 55) + 6;
                int bw = right - 14 - bx;
                bar(g, bx, y2 + 3, bw, progress(fame, t), t >= RegionFame.STEPS.length, false);
                if (!l.getString("flag").isEmpty()) {
                    // 今週の旗 (自分の組合なら桃色)
                    flag(g, right - 8, y2 + 8, l.getBoolean("mine") ? Paper.PINK : Paper.GREY);
                }
            } else {
                g.drawString(font, Component.translatable("gui.skyabyss.journey.no_fame"), x + 16, y2, DIM, false);
            }
            if (in(mx, my, x, y, ROW_W, ROW_H)) {
                List<Component> lines = new ArrayList<>();
                lines.add(RegionFame.landName(region).copy().withColor(GOLD));
                if (town) {
                    int t = RegionFame.tier(fame);
                    lines.add(Component.translatable("gui.skyabyss.journey.tip.fame", fame,
                            t >= RegionFame.STEPS.length ? Component.translatable("message.skyabyss.fame.max") : Component.literal(String.valueOf(RegionFame.STEPS[t])))
                            .withColor(TEXT));
                    lines.add(Component.translatable("gui.skyabyss.journey.tip.week", l.getInt("week")).withColor(SOFT));
                    lines.add(Component.translatable("gui.skyabyss.journey.tip.discount", l.getInt("discount")).withColor(SOFT));
                    String flag = l.getString("flag");
                    lines.add((flag.isEmpty() ? Component.translatable("gui.skyabyss.journey.tip.no_flag")
                            : Component.translatable("gui.skyabyss.journey.tip.flag", flag)).withColor(l.getBoolean("mine") ? GOLD : SOFT));
                }
                lines.add(Component.translatable("gui.skyabyss.journey.tip.sights", found, total).withColor(SOFT));
                lines.add(Component.translatable("gui.skyabyss.journey.tip.how").withColor(DIM));
                tip(lines);
            }
        }
    }

    static float progress(int fame, int t) {
        if (t >= RegionFame.STEPS.length) {
            return 1f;
        }
        int lo = t == 0 ? 0 : RegionFame.STEPS[t - 1];
        return (float) (fame - lo) / Math.max(1, RegionFame.STEPS[t] - lo);
    }

    // 細い帯 (高さ 3。満ちれば明るい色)。cream = 生成りの帯の上
    private static void bar(GuiGraphics g, int x, int y, int w, float frac, boolean full, boolean cream) {
        g.fill(x, y, x + w, y + 3, cream ? Paper.CREAM_LO : Paper.EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, cream ? Paper.CREAM : Paper.BLACK_LO);
        int fill = Math.max(0, Math.min(w - 2, Math.round((w - 2) * frac)));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, full ? (cream ? Paper.PINK_LO : 0xFFFFF6EA) : Paper.PINK);
        }
    }

    // 土地の印 (菱形の結晶。土地の色は意味の色)
    private static void landGem(GuiGraphics g, int cx, int cy, int rgb) {
        int c = 0xFF000000 | rgb;
        int dark = 0xFF000000 | ((rgb >> 1) & 0x7F7F7F);
        for (int d = 0; d <= 4; d++) {
            g.fill(cx - (4 - d), cy - d, cx + (4 - d) + 1, cy - d + 1, d == 4 ? c : dark);
            g.fill(cx - (4 - d), cy + d, cx + (4 - d) + 1, cy + d + 1, dark);
        }
        g.fill(cx - 2, cy - 2, cx + 1, cy + 1, c);
        g.fill(cx - 1, cy - 3, cx, cy - 2, 0xFFFFFFFF);
    }

    // 小さな旗 (竿と三角)
    private static void flag(GuiGraphics g, int x, int y, int argb) {
        g.fill(x, y - 8, x + 1, y + 1, Paper.CREAM_LO);
        for (int i = 0; i < 4; i++) {
            g.fill(x + 1, y - 8 + i, x + 1 + (6 - i), y - 7 + i, argb);
        }
    }

    // ---------------------------------------------------------------- 図鑑 (右の帯の上。字は墨へ写る)
    private void renderCodex(GuiGraphics g, int mx, int my) {
        int x = SIDE_X;
        int w = SIDE_W - 4;
        int y = CODEX_Y;
        boolean hot = codexHit(mx, my);
        // 板の側: 押せる所 (狩人の手帳へ) は升
        jp.revati.skyabyss.client.ui.Plate.tile(g, SIDE_X - 4, CODEX_Y - 4, SIDE_W + 4, CODEX_BOTTOM - CODEX_Y + 4,
                hot ? jp.revati.skyabyss.client.ui.Plate.HOVER : jp.revati.skyabyss.client.ui.Plate.REST, jp.revati.skyabyss.client.ui.Plate.GOLD);
        LedgerStage.section(g, font, Component.translatable("gui.skyabyss.journey.codex"), x, y, 11, Paper.INK_HEAD);
        ListTag sets = data.getList("sets", Tag.TAG_COMPOUND);
        int done = 0;
        for (int i = 0; i < sets.size(); i++) {
            CompoundTag s = sets.getCompound(i);
            if (s.getInt("total") > 0 && s.getInt("owned") >= s.getInt("total")) {
                done++;
            }
        }
        y += 15;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.codex_sets", done, sets.size()), x, y, w, TEXT, false);
        int foes = data.getInt("foes");
        int foesTotal = data.getInt("foes_total");
        y += 12;
        FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.codex_foes", foes, foesTotal), x, y, w, TEXT, false);
        y += 13;
        bar(g, x, y, w, foesTotal == 0 ? 0f : (float) foes / foesTotal, foesTotal > 0 && foes >= foesTotal, true);
        y += 8;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.journey.codex_open"), w)) {
            if (y + 9 > CODEX_BOTTOM) {
                break;
            }
            g.drawString(font, line, x, y, hot ? GOLD : DIM, false);
            y += 11;
        }
        if (hot) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.skyabyss.journey.codex").withColor(GOLD));
            for (int i = 0; i < sets.size(); i++) {
                CompoundTag s = sets.getCompound(i);
                boolean full = s.getInt("owned") >= s.getInt("total");
                lines.add(Component.translatable("message.skyabyss.codex.set_line", Component.translatable("endgame.skyabyss.codex.set." + s.getString("titan")),
                        s.getInt("owned"), s.getInt("total")).withColor(full ? TEAL : TEXT));
            }
            lines.add(Component.translatable("message.skyabyss.codex.foes_line", foes, foesTotal).withColor(foes >= foesTotal ? TEAL : TEXT));
            lines.add(Component.translatable("message.skyabyss.codex.foot").withColor(DIM));
            tip(lines);
        }
    }

    // ---------------------------------------------------------------- 熟練 (右の帯の下)
    private void renderMastery(GuiGraphics g, int mx, int my) {
        int x = SIDE_X;
        int w = SIDE_W - 4;
        int y = MASTERY_Y;
        LedgerStage.section(g, font, Component.translatable("gui.skyabyss.journey.mastery"), x, y, 11, Paper.INK_HEAD);
        Job job = Job.byId(data.getString("job"));
        long xp = data.getLong("mastery");
        int t = JobMastery.tier(xp);
        y += 15;
        if (job == null) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.no_job"), x, y, w, DIM, false);
            return;
        }
        Component step = Component.translatable("gui.skyabyss.journey.mastery_step", t, JobMastery.TIERS);
        int sw = font.width(step);
        FitText.draw(g, font, Component.translatable(job.nameKey()), x, y, w - sw - 5, TEXT, false);
        g.drawString(font, step, x + w - sw, y, t >= JobMastery.TIERS ? GOLD : TEXT, false);
        y += 12;
        bar(g, x, y, w, (float) JobMastery.progress(xp), t >= JobMastery.TIERS, true);
        y += 8;
        boolean capped = jp.revati.skyabyss.rpg.Growth.get(minecraft.player).level >= jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font,
                Component.translatable(capped ? "gui.skyabyss.journey.mastery_on" : "gui.skyabyss.journey.mastery_off"), w)) {
            if (y + 9 > MASTERY_BOTTOM) {
                break;
            }
            g.drawString(font, line, x, y, capped ? SOFT : DIM, false);
            y += 11;
        }
        if (in(mx, my, SIDE_X - 4, MASTERY_Y, SIDE_W + 4, MASTERY_BOTTOM - MASTERY_Y)) {
            tip(List.of(Component.translatable("gui.skyabyss.journey.mastery").withColor(GOLD),
                    Component.translatable("gui.skyabyss.journey.tip.mastery").withColor(TEXT)));
        }
    }
}
