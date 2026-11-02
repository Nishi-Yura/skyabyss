package jp.revati.skyabyss.endgame.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
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
// 中身は開いたときにサーバーへ頼む (Journey.Request → Snapshot)。背景は tools/gen/items/gen_journey_assets.py
public class JourneyScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/journey/journey.png");
    static final int TEX = 512;
    static final int W = 240;
    static final int H = 184;
    static final int ROW_Y = 24;
    static final int ROW_H = 20;
    static final int BOX_Y = 128;
    static final int BAR_X = 28;
    static final int BAR_W = 142;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int DIM = 0x6E7484;
    static final int GOLD = 0xF2C14E;
    static final int TEAL = 0x7FD6D0;
    // 土地の印の色 (都の鐘・岩鯨の岩・潮殻の潮・雷翼の雷・余燼の熾)
    static final int[] LAND_RGB = {0xD8C27A, 0, 0xB8A27E, 0x7FD6D0, 0x9FB8FF, 0xFF9A5A};

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
        fit.fit(width, height, W + 4, H + 24, 2.5f);
        left = 2;
        top = 20;
        RpgTabs.addAt(this::addRenderableWidget, left + W / 2, top, RpgTabs.JOURNEY);
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
        g.blit(TEXTURE, left, top, W, H, 0, 0, W * 2, H * 2, TEX, TEX);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);
        if (data == null) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.journey.loading"), left + W / 2, top + 70, SOFT);
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
    private void renderLands(GuiGraphics g, int mx, int my) {
        ListTag lands = data.getList("lands", Tag.TAG_COMPOUND);
        for (int i = 0; i < lands.size() && i < 5; i++) {
            CompoundTag l = lands.getCompound(i);
            int region = l.getInt("r");
            int x = left;
            int y = top + ROW_Y + i * ROW_H;
            landGem(g, x + 16, y + 9, LAND_RGB[Math.max(0, Math.min(LAND_RGB.length - 1, region))]);
            g.drawString(font, RegionFame.landName(region), x + 28, y + 3, TEXT, false);
            int found = l.getInt("found");
            int total = l.getInt("total");
            Component sights = Component.translatable("gui.skyabyss.journey.sights", found, total);
            g.drawString(font, sights, x + 226 - font.width(sights), y + 3, total > 0 && found >= total ? TEAL : SOFT, false);
            boolean town = RegionFame.townLand(region);
            int fame = l.getInt("fame");
            if (town) {
                int t = RegionFame.tier(fame);
                Component tier = Component.translatable("endgame.skyabyss.fame.tier." + t);
                g.drawString(font, tier, x + 104, y + 3, t >= RegionFame.STEPS.length ? GOLD : t > 0 ? TEXT : DIM, false);
                bar(g, x + BAR_X, y + 13, progress(fame, t), t >= RegionFame.STEPS.length);
                if (!l.getString("flag").isEmpty()) {
                    // 今週の旗 (自分の組合なら金)
                    int fx = x + 174;
                    flag(g, fx, y + 11, l.getBoolean("mine") ? GOLD : SOFT);
                }
            } else {
                g.drawString(font, Component.translatable("gui.skyabyss.journey.no_fame"), x + 104, y + 3, DIM, false);
            }
            if (in(mx, my, x + 8, y, 224, ROW_H - 2)) {
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

    private void bar(GuiGraphics g, int x, int y, float frac, boolean full) {
        int fill = Math.max(0, Math.min(BAR_W - 2, Math.round((BAR_W - 2) * frac)));
        if (fill > 0) {
            g.blit(TEXTURE, x + 1, y + 1, fill, 1, 0, full ? 470 : 466, fill * 2, 2, TEX, TEX);
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
        int x = left + 8;
        int y = top + BOX_Y;
        g.drawString(font, Component.translatable("gui.skyabyss.journey.codex"), x + 6, y + 3, GOLD, false);
        ListTag sets = data.getList("sets", Tag.TAG_COMPOUND);
        int done = 0;
        for (int i = 0; i < sets.size(); i++) {
            CompoundTag s = sets.getCompound(i);
            if (s.getInt("total") > 0 && s.getInt("owned") >= s.getInt("total")) {
                done++;
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.journey.codex_sets", done, sets.size()), x + 6, y + 16, TEXT, false);
        int foes = data.getInt("foes");
        int foesTotal = data.getInt("foes_total");
        g.drawString(font, Component.translatable("gui.skyabyss.journey.codex_foes", foes, foesTotal), x + 6, y + 26, TEXT, false);
        int w = 110 - 12;
        int fill = Math.round((w - 2) * (foesTotal == 0 ? 0f : (float) foes / foesTotal));
        if (fill > 0) {
            g.blit(TEXTURE, x + 7, y + 38, fill, 1, 0, foes >= foesTotal ? 470 : 466, fill * 2, 2, TEX, TEX);
        }
        if (in(mx, my, x, y, 110, 47)) {
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
        int x = left + 122;
        int y = top + BOX_Y;
        g.drawString(font, Component.translatable("gui.skyabyss.journey.mastery"), x + 6, y + 3, GOLD, false);
        Job job = Job.byId(data.getString("job"));
        long xp = data.getLong("mastery");
        int t = JobMastery.tier(xp);
        if (job == null) {
            g.drawString(font, Component.translatable("gui.skyabyss.journey.no_job"), x + 6, y + 16, DIM, false);
            return;
        }
        g.drawString(font, Component.translatable(job.nameKey()), x + 6, y + 16, TEXT, false);
        Component step = Component.translatable("gui.skyabyss.journey.mastery_step", t, JobMastery.TIERS);
        g.drawString(font, step, x + 104 - font.width(step), y + 16, t >= JobMastery.TIERS ? GOLD : TEXT, false);
        boolean capped = jp.revati.skyabyss.rpg.Growth.get(minecraft.player).level >= jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL;
        g.drawString(font, Component.translatable(capped ? "gui.skyabyss.journey.mastery_on" : "gui.skyabyss.journey.mastery_off"), x + 6, y + 26,
                capped ? SOFT : DIM, false);
        int w = 110 - 12;
        int fill = Math.round((w - 2) * JobMastery.progress(xp));
        if (fill > 0) {
            g.blit(TEXTURE, x + 7, y + 38, fill, 1, 0, t >= JobMastery.TIERS ? 470 : 466, fill * 2, 2, TEX, TEX);
        }
        if (in(mx, my, x, y, 110, 47)) {
            tip(List.of(Component.translatable("gui.skyabyss.journey.mastery").withColor(GOLD),
                    Component.translatable("gui.skyabyss.journey.tip.mastery").withColor(TEXT)));
        }
    }
}
