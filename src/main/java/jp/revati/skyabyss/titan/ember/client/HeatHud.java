package jp.revati.skyabyss.titan.ember.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.BackGaugeHud;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 熱の目盛り (13 の 3.4節): 余燼の背にいる間、画面の右上に小さな火の目盛り。今の熱の柱と 50・85 の線、帯の名前と数。
// 85 を超えると画面の縁が橙に揺れる。パーティクルの設定に左右されない。
// 板の置き場所・幅・枠・文の切り方は共通の BackGaugeHud (文は枠に収める。潮殻の 20.12節の反省)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class HeatHud {
    private static final BackGaugeHud.Gauge GAUGE = HeatHud::shown;
    // 柱の左の飾り (炎の形) の幅、柱の幅、右の余白
    static final int LEFT = 22;
    static final int RIGHT = 6;

    static {
        BackGaugeHud.register(GAUGE);
    }

    private HeatHud() {
    }

    static boolean shown(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.level.dimension() == EmberIds.BACK && !mc.options.hideGui
                && !mc.player.isSpectator() && !mc.player.isCreative();
    }

    static String bandKey(EmberHeat.Band b) {
        return switch (b) {
            case NORMAL -> "normal";
            case FLUSHED -> "flushed";
            case SCORCHING -> "scorching";
            case SCORCHED -> "scorched";
        };
    }

    // 帯の色 (ふつう 灰、火照り 橙、焦げかけ 明るい橙、焦げ 赤)
    static int bandColor(EmberHeat.Band b) {
        return switch (b) {
            case NORMAL -> 0xFFD8CFC4;
            case FLUSHED -> 0xFFFFA048;
            case SCORCHING -> 0xFFFFC060;
            case SCORCHED -> 0xFFFF5038;
        };
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(mc)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float heat = EmberHeatClient.heat();
        EmberHeat.Band band = EmberHeat.Band.of(heat);
        float t = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        // 85 を超えたら画面の縁が橙に揺れる
        if (heat >= EmberHeat.SCORCHING) {
            float pulse = 0.5f + 0.5f * Mth.sin(t * 0.35f);
            int a = (int) ((heat >= EmberHeat.MAX ? 90 : 50) + 40 * pulse);
            int c = (a << 24) | 0xFF6018;
            int w = g.guiWidth();
            int h = g.guiHeight();
            for (int i = 0; i < 6; i++) {
                int ci = ((a * (6 - i) / 6) << 24) | 0xFF6018;
                g.fill(0, i, w, i + 1, ci);
                g.fill(0, h - i - 1, w, h - i, ci);
                g.fill(i, 0, i + 1, h, ci);
                g.fill(w - i - 1, 0, w - i, h, ci);
            }
            g.fill(0, 0, 0, 0, c);
        }
        Component name = Component.translatable(EmberIds.LANG_HEAT_GUI + bandKey(band));
        Component value = Component.translatable(EmberIds.LANG_HEAT_GUI + "value", Math.round(heat));
        int text = Math.max(mc.font.width(name), mc.font.width(value));
        int w = BackGaugeHud.width(g, LEFT + text + RIGHT, 84);
        int h = BackGaugeHud.HEIGHT;
        int x = BackGaugeHud.left(g, w);
        int y = BackGaugeHud.top(mc);
        int border = band == EmberHeat.Band.NORMAL ? 0xFF8A6A58 : bandColor(band);
        BackGaugeHud.frame(g, x, y, w, h, 0xB0181010, border);
        // 炎の形 (小さな三角を三つ)
        int fx = x + 4;
        int fy = y + 5;
        int flame = band == EmberHeat.Band.NORMAL ? 0xFF7A5A48 : 0xFFE07028;
        for (int i = 0; i < 6; i++) {
            g.fill(fx + 3 - i / 2, fy + 6 + i * 2, fx + 4 + i / 2, fy + 8 + i * 2, flame);
        }
        // 熱の柱 (下から上へ)。50・85 の線
        int bx = x + 12;
        int by = y + 4;
        int bh = h - 8;
        g.fill(bx, by, bx + 6, by + bh, 0xFF2A1A16);
        int fill = Math.round(Mth.clamp(heat / EmberHeat.MAX, 0f, 1f) * bh);
        g.fill(bx, by + bh - fill, bx + 6, by + bh, bandColor(band));
        for (float line : new float[] {EmberHeat.FLUSHED, EmberHeat.SCORCHING}) {
            int ly = by + bh - Math.round(line / EmberHeat.MAX * bh);
            g.fill(bx - 1, ly, bx + 7, ly + 1, 0xFFF0E0D0);
        }
        // 文 (はみ出す分は切る。枠の外へは出さない)
        int room = w - LEFT - RIGHT;
        BackGaugeHud.text(g, mc.font, name, x + LEFT, y + 5, room, bandColor(band));
        BackGaugeHud.text(g, mc.font, value, x + LEFT, y + 16, room, 0xFFC8B8A8);
    }
}
