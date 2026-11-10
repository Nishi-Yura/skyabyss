package jp.revati.skyabyss.client.hud;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 画面の下 (docs/design/56 の 5 章。2026-11-11 の案 B): 手もとの 9 枠はバニラの絵のまま (バニラのホットバーの層)。その上に
//   体力の帯 (ハートの場所・幅 81・高さ 9) とマナの帯 (満腹度の場所)。吸収は体力の帯のすぐ上の金の細線 (VitalsHud)
//   経験値の線と Lv の数はバニラの経験値のバーの場所と形 (Lv 50 では今のジョブの熟練の進み)
//   守りの札は体力の帯の左上、息の札はマナの帯の右上 (出るときだけ)。スキルの枠は手もとの右に別の枠 (SkillHud)
// 体力の帯に置き換えない人 (設定 styledVitals が false・クリエイティブ・見物) はバニラのまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BandHud {
    // 帯の高さ。体力とマナの帯の上端 (ホットバーの下端から。バニラのハートの行と同じ)
    static final int ROW_H = 9;
    static final int ROW_UP = 39;
    // 経験値のバーの上端 (ホットバーの下端から。バニラと同じ)
    static final int XP_UP = 29;
    private static final ResourceLocation XP_BG = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_FILL = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");

    private BandHud() {
    }

    public static boolean on() {
        return VitalsHud.styled();
    }

    // 置き場 (体力・マナ・守りの札・息の札)。出していなければ null (VitalsHud はバニラの置き場を使う)
    @Nullable
    static Rect rect(Id id) {
        if (!on()) {
            return null;
        }
        Rect hb = HudManager.result().vanilla.get(Id.V_HOTBAR);
        if (hb == null) {
            return null;
        }
        int y = hb.bottom() - ROW_UP;
        return switch (id) {
            case V_HEALTH -> new Rect(hb.x(), y, 81, ROW_H);
            case V_FOOD -> new Rect(hb.right() - 81, y, 81, ROW_H);
            case V_ARMOR -> new Rect(hb.x(), y - 16, 30, 11);
            case V_AIR -> new Rect(hb.right() - 44, y - 16, 44, 11);
            default -> null;
        };
    }

    // スキルの枠がホットバーのすぐ右に置けたか
    @Nullable
    static Rect joinedSkills() {
        Rect hb = HudManager.result().vanilla.get(Id.V_HOTBAR);
        Rect sk = HudManager.rect(Id.SKILL_SLOTS);
        return hb != null && sk != null && sk.x() >= hb.right() && sk.x() - hb.right() <= 12 ? sk : null;
    }

    // 型の溜めの置き場 {x, y, 幅}: スキルの枠の上。枠がなければマナの帯の上
    static int[] gaugeBox(Rect mana) {
        Rect sk = joinedSkills();
        if (sk != null) {
            return new int[] {sk.x() + 1, sk.y() - 7, sk.w() - 2};
        }
        return new int[] {mana.x() + 12, mana.y() - 6, mana.w() - 13};
    }

    // バニラのホットバーを描いたあとに、経験値のバー・Lv の数・スキルの枠の地
    @SubscribeEvent
    static void onLayer(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName()) || !on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            return;
        }
        long prof = jp.revati.skyabyss.dev.Prof.begin();
        try {
            draw(event.getGuiGraphics(), mc, mc.player);
        } finally {
            jp.revati.skyabyss.dev.Prof.end("client.band", prof);
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, Player p) {
        Rect hb = HudManager.result().vanilla.get(Id.V_HOTBAR);
        if (hb == null) {
            return;
        }
        Font font = mc.font;
        // スキルの枠の地 (枠そのものは SkillHud)
        Rect sk = joinedSkills();
        if (sk != null) {
            Golden.hudFrame(g, sk.x() - 2, sk.y() - 2, sk.right() + 2, hb.bottom() + 1);
        }
        // 経験値のバー (バニラの絵)。Lv 50 では今のジョブの熟練の進み
        GrowthData data = Growth.get(p);
        boolean max = data.level >= GrowthRules.MAX_LEVEL;
        jp.revati.skyabyss.rpg.skill.Job job = max ? jp.revati.skyabyss.rpg.client.GrowthHud.masteryJob(mc) : null;
        float frac = job != null ? jp.revati.skyabyss.rpg.JobMastery.progress(jp.revati.skyabyss.rpg.JobMastery.xp(data, job))
                : max ? 1f : (float) data.xp / Math.max(1, data.xpNeed());
        int xx = hb.x();
        int xy = hb.bottom() - XP_UP;
        g.blitSprite(XP_BG, xx, xy, 182, 5);
        int fill = Mth.clamp(Math.round(182 * frac), 0, 182);
        if (fill > 0) {
            g.blitSprite(XP_FILL, 182, 5, 0, 0, xx, xy, fill, 5);
        }
        if (jp.revati.skyabyss.rpg.client.GrowthClient.barFlash > 0 && !max && fill > 2) {
            int a = (int) (jp.revati.skyabyss.rpg.client.GrowthClient.barFlash / 12f * 0xC0);
            g.fill(xx + 1, xy + 1, xx + fill - 1, xy + 4, (a << 24) | 0xFFFFFF);
        }
        // Lv の数 (バニラと同じ緑・黒の縁取り)。振っていない点があれば右に金の点
        String lv = Integer.toString(data.level);
        int lx = hb.x() + hb.w() / 2 - font.width(lv) / 2;
        int ly = hb.bottom() - XP_UP - 6;
        outlined(g, font, lv, lx, ly, 0xFF80FF20);
        if (data.unspentPoints() > 0) {
            int dx = lx + font.width(lv) + 3;
            g.fill(dx - 1, ly + 1, dx + 4, ly + 6, 0xFF000000);
            g.fill(dx, ly + 3, dx + 3, ly + 4, 0xFFFFE08C);
            g.fill(dx + 1, ly + 2, dx + 2, ly + 5, 0xFFFFE08C);
        }
    }

    // 黒の縁取りの字 (バニラの経験値の数と同じ描き方)
    public static void outlined(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y, 0xFF000000, false);
        g.drawString(font, text, x - 1, y, 0xFF000000, false);
        g.drawString(font, text, x, y + 1, 0xFF000000, false);
        g.drawString(font, text, x, y - 1, 0xFF000000, false);
        g.drawString(font, text, x, y, color, false);
    }

    // 守りの札 (体力の帯の左上)。盾の絵と数
    static void armorTab(GuiGraphics g, Font font, Rect r, int armor, Runnable icon) {
        String t = Integer.toString(armor);
        int w = 13 + font.width(t) + 4;
        Golden.hudFrame(g, r.x(), r.y(), r.x() + w, r.y() + r.h() + 1);
        icon.run();
        g.drawString(font, t, r.x() + 13, r.y() + 2, 0xECE8DC, false);
    }

    // 息の札 (マナの帯の右上)。細い帯と秒
    static void airTab(GuiGraphics g, Font font, Rect r, float frac, int color, String text) {
        Golden.hudFrame(g, r.x(), r.y(), r.right(), r.bottom() + 1);
        int tw = font.width(text);
        int bx = r.x() + 3;
        int bw = r.w() - 6 - tw - 3;
        g.fill(bx, r.y() + 4, bx + bw, r.y() + 8, Golden.OUTLINE);
        int fill = Mth.clamp(Math.round((bw - 2) * frac), 0, bw - 2);
        g.fill(bx + 1, r.y() + 5, bx + 1 + fill, r.y() + 7, color);
        g.drawString(font, text, r.right() - 3 - tw, r.y() + 2, 0xECE8DC, false);
    }
}
