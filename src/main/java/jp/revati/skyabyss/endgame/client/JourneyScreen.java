package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
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

// 旅の記録 (記録・技のつまみの「旅」。docs/design/54 の 6.2.7)。見るだけの画面:
//   上の 5 行   土地ごと (都のまわり・鯨見・汐守・風待・灰炉): 名声の段とバー、名所の数、今週の旗。乗せると点・値引き・週の点
//   下の左     図鑑: そろえた巨神の装備の表の数と敵の図鑑 (乗せると巨神ごとの数)
//   下の右     熟練: いまの職の段とバー (Lv 50 からたまる)
// 中身は開いたときにサーバーへ頼む (Journey.Request → Snapshot)。板は黄金比の大きい板 (client/ui/Golden。docs/design/56):
// 主の列 (左) に土地の 5 行 (高さ 31・あいだ 5)、脇の列 (右) を上下に分けて図鑑と熟練
public class JourneyScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/journey/journey.png");
    static final int TEX = 512;
    static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    static final int W = Golden.L_W;
    static final int H = Golden.L_H;
    static final Golden.Box MAIN = LAYOUT.body().major(true);
    static final Golden.Box SIDE = LAYOUT.body().minor(false);
    // 土地の行
    static final int ROW_H = 31;
    static final int ROW_STEP = ROW_H + Golden.S5;
    // 図鑑 (脇の列の上) と熟練 (下)
    static final Golden.Box CODEX = new Golden.Box(SIDE.x(), SIDE.y(), SIDE.w(), (SIDE.h() - Golden.GAP) / 2);
    static final Golden.Box MASTERY = new Golden.Box(SIDE.x(), CODEX.bottom() + Golden.GAP, SIDE.w(), SIDE.bottom() - CODEX.bottom() - Golden.GAP);

    static final int TITLE = Golden.PALE;
    static final int TEXT = Golden.TEXT;
    static final int SOFT = Golden.SOFT;
    static final int DIM = Golden.DIM;
    static final int GOLD = Golden.GOLD;
    static final int TEAL = Golden.TEAL;
    // 土地の印の色 (都の鐘・岩鯨の岩・潮殻の潮・雷翼の雷・余燼の熾)
    static final int[] LAND_RGB = {0xD8C27A, 0, 0xB8A27E, 0x8FD0FF, 0x9FB8FF, 0xFF9A5A};

    @Nullable
    private static CompoundTag data;

    private final UiFit fit = new UiFit();
    private int left;
    private int top;
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
        fit.fit(width, height, W, H, 3f);
        left = 0;
        top = 0;
        RpgTabs.addHead(this::addRenderableWidget, LAYOUT.head(), RpgTabs.JOURNEY);
        // 記録の画面 (踏破・塔・深み・釣り・早駆け・転生・空見。record/RecordsHub) を開く釦。つまみの右
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.skyabyss.records_hub.open"),
                b -> jp.revati.skyabyss.client.record.RecordsHubScreen.request(jp.revati.skyabyss.record.RecordsHub.CLEAR))
                .bounds(LAYOUT.head().x() + RpgTabs.headWidth(RpgTabs.JOURNEY) + Golden.S8, LAYOUT.head().y() + 4, Golden.S55, Golden.BTN).build());
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
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.frame(g, font, LAYOUT, title);
        for (int i = 0; i < 5; i++) {
            Golden.recess(g, landRow(i));
        }
        Golden.recess(g, CODEX);
        Golden.recess(g, MASTERY);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.journey.loading"), MAIN.cx(), Golden.textY(MAIN.y() + MAIN.h() / 2 - 6, Golden.ROW), SOFT);
        } else {
            renderLands(g, mx, my);
            renderCodex(g, mx, my);
            renderMastery(g, mx, my);
        }
        fit.end(g);
        if (pendingTip != null) {
            g.renderTooltip(font, pendingTip, screenX, screenY);
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        // 図鑑の枠を押すと狩人の手帳
        if (button == 0 && data != null && CODEX.contains(fit.x(screenX), fit.y(screenY))) {
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

    // ---------------------------------------------------------------- 土地の行
    static Golden.Box landRow(int i) {
        return new Golden.Box(MAIN.x(), MAIN.y() + 1 + i * ROW_STEP, MAIN.w(), ROW_H);
    }

    private void renderLands(GuiGraphics g, int mx, int my) {
        ListTag lands = data.getList("lands", Tag.TAG_COMPOUND);
        for (int i = 0; i < lands.size() && i < 5; i++) {
            CompoundTag l = lands.getCompound(i);
            int region = l.getInt("r");
            Golden.Box row = landRow(i);
            int x = row.x();
            int y = row.y();
            int y1 = Golden.textY(y + 2, Golden.ROW);
            int y2 = Golden.textY(y + 2 + Golden.ROW, Golden.ROW);
            landGem(g, x + 11, y + 15, LAND_RGB[Math.max(0, Math.min(LAND_RGB.length - 1, region))]);
            g.drawString(font, RegionFame.landName(region), x + Golden.S21, y1, TEXT, false);
            int found = l.getInt("found");
            int total = l.getInt("total");
            Component sights = Component.translatable("gui.skyabyss.journey.sights", found, total);
            g.drawString(font, sights, row.right() - Golden.S8 - font.width(sights), y1, total > 0 && found >= total ? TEAL : SOFT, false);
            boolean town = RegionFame.townLand(region);
            int fame = l.getInt("fame");
            if (town) {
                int t = RegionFame.tier(fame);
                Component tier = Component.translatable("endgame.skyabyss.fame.tier." + t);
                int tw = jp.revati.skyabyss.client.ui.FitText.draw(g, font, tier, x + Golden.S21, y2, Golden.S55 + Golden.S13, t >= RegionFame.STEPS.length ? GOLD : t > 0 ? TEXT : DIM, false);
                int bx = x + Golden.S21 + Math.max(tw, Golden.S55) + Golden.S8;
                int bw = row.right() - Golden.S21 - bx;
                bar(g, bx, y + 2 + Golden.ROW + 5, bw, progress(fame, t), t >= RegionFame.STEPS.length);
                if (!l.getString("flag").isEmpty()) {
                    // 今週の旗 (自分の組合なら金)
                    flag(g, row.right() - Golden.S13, y + 2 + Golden.ROW + 10, l.getBoolean("mine") ? GOLD : SOFT);
                }
            } else {
                g.drawString(font, Component.translatable("gui.skyabyss.journey.no_fame"), x + Golden.S21, y2, DIM, false);
            }
            if (row.contains(mx, my)) {
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

    // 細い帯 (高さ 3。満ちれば水色)
    private static void bar(GuiGraphics g, int x, int y, int w, float frac, boolean full) {
        g.fill(x, y, x + w, y + 3, 0xFF364064);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF0A0E1E);
        int fill = Math.max(0, Math.min(w - 2, Math.round((w - 2) * frac)));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, full ? 0xFF8FD0FF : 0xFFE0B040);
        }
    }

    // 土地の印 (印の台の中の菱形の結晶)
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
    private static void flag(GuiGraphics g, int x, int y, int rgb) {
        int c = 0xFF000000 | rgb;
        g.fill(x, y - 8, x + 1, y + 1, 0xFFB8B0A0);
        for (int i = 0; i < 4; i++) {
            g.fill(x + 1, y - 8 + i, x + 1 + (6 - i), y - 7 + i, c);
        }
    }

    // ---------------------------------------------------------------- 図鑑
    private void renderCodex(GuiGraphics g, int mx, int my) {
        int x = CODEX.x() + Golden.S8;
        int w = CODEX.w() - 2 * Golden.S8;
        int y = CODEX.y() + Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.journey.codex"), x, Golden.textY(y, Golden.ROW), GOLD, false);
        ListTag sets = data.getList("sets", Tag.TAG_COMPOUND);
        int done = 0;
        for (int i = 0; i < sets.size(); i++) {
            CompoundTag s = sets.getCompound(i);
            if (s.getInt("total") > 0 && s.getInt("owned") >= s.getInt("total")) {
                done++;
            }
        }
        y += Golden.ROW + Golden.S3;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.codex_sets", done, sets.size()), x, Golden.textY(y, Golden.ROW), w, TEXT, false);
        int foes = data.getInt("foes");
        int foesTotal = data.getInt("foes_total");
        y += Golden.ROW;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.codex_foes", foes, foesTotal), x, Golden.textY(y, Golden.ROW), w, TEXT, false);
        y += Golden.ROW + Golden.S3;
        bar(g, x, y, w, foesTotal == 0 ? 0f : (float) foes / foesTotal, foesTotal > 0 && foes >= foesTotal);
        Component open = Component.translatable("gui.skyabyss.journey.codex_open");
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, open, x, Golden.textY(CODEX.bottom() - Golden.S5 - Golden.ROW, Golden.ROW), w, CODEX.contains(mx, my) ? TEAL : DIM, false);
        if (CODEX.contains(mx, my)) {
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

    // ---------------------------------------------------------------- 熟練
    private void renderMastery(GuiGraphics g, int mx, int my) {
        int x = MASTERY.x() + Golden.S8;
        int w = MASTERY.w() - 2 * Golden.S8;
        int y = MASTERY.y() + Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.journey.mastery"), x, Golden.textY(y, Golden.ROW), GOLD, false);
        Job job = Job.byId(data.getString("job"));
        long xp = data.getLong("mastery");
        int t = JobMastery.tier(xp);
        y += Golden.ROW + Golden.S3;
        if (job == null) {
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.journey.no_job"), x, Golden.textY(y, Golden.ROW), w, DIM, false);
            return;
        }
        Component step = Component.translatable("gui.skyabyss.journey.mastery_step", t, JobMastery.TIERS);
        int sw = font.width(step);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(job.nameKey()), x, Golden.textY(y, Golden.ROW), w - sw - Golden.S5, TEXT, false);
        g.drawString(font, step, x + w - sw, Golden.textY(y, Golden.ROW), t >= JobMastery.TIERS ? GOLD : TEXT, false);
        boolean capped = jp.revati.skyabyss.rpg.Growth.get(minecraft.player).level >= jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL;
        y += Golden.ROW;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font,
                Component.translatable(capped ? "gui.skyabyss.journey.mastery_on" : "gui.skyabyss.journey.mastery_off"), w)) {
            g.drawString(font, line, x, Golden.textY(y, Golden.ROW), capped ? SOFT : DIM, false);
            y += Golden.ROW;
        }
        bar(g, x, MASTERY.bottom() - Golden.S8 - 3, w, (float) JobMastery.progress(xp), t >= JobMastery.TIERS);
        if (MASTERY.contains(mx, my)) {
            tip(List.of(Component.translatable("gui.skyabyss.journey.mastery").withColor(GOLD),
                    Component.translatable("gui.skyabyss.journey.tip.mastery").withColor(TEXT)));
        }
    }
}
