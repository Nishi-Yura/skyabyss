package jp.revati.skyabyss.client.hud;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 下の一枚の帯 (docs/design/56 の 5 章): 体力 | Lv | マナ の段がホットバーの上に載り、スキルの枠が右に続く。一つの枠の中に、
//   上の段 (高さ 10): 体力の帯 (幅 81)・Lv の札 (幅 18)・マナの帯 (幅 81)。スキルの上には型の溜め
//   経験値の線 (高さ 1)
//   下の段 (高さ 20): 持ち物の 9 枠、区切りの線、スキルの枠と護符の枠 (SkillHud が描く)
// を置く。守りは枠の左上の札、息は枠の右上の札 (出るときだけ)。吸収・毒・凍えは体力の帯の中 (VitalsHud)。
// バニラのホットバーの層は止めて、ここで枠・品・選んだ印・逆手・攻撃の目盛りを描く。体力の帯に置き換えない人 (設定 styledVitals が false・
// クリエイティブ・見物) はバニラのまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BandHud {
    // 上の段の高さ・枠の上端 (画面の下から)
    static final int ROW_H = 10;
    static final int TOP = 38;
    static final int SLOT = 20;

    private BandHud() {
    }

    public static boolean on() {
        return VitalsHud.styled();
    }

    // 帯の中の置き場 (体力・マナ・守りの札・息の札)。帯を出していなければ null (VitalsHud はバニラの置き場を使う)
    @Nullable
    static Rect rect(Id id) {
        if (!on()) {
            return null;
        }
        Rect hb = HudManager.result().vanilla.get(Id.V_HOTBAR);
        if (hb == null) {
            return null;
        }
        int y = hb.bottom() - TOP + 3;
        return switch (id) {
            case V_HEALTH -> new Rect(hb.x(), y, 81, ROW_H);
            case V_FOOD -> new Rect(hb.right() - 81, y, 81, ROW_H);
            case V_ARMOR -> new Rect(hb.x() - 3, hb.bottom() - TOP - 10, 30, 11);
            case V_AIR -> new Rect(hb.right() + 3 - 44, hb.bottom() - TOP - 10, 44, 11);
            default -> null;
        };
    }

    // スキルの枠が帯につながっているか (ホットバーのすぐ右に置けたとき)
    @Nullable
    static Rect joinedSkills() {
        Rect hb = HudManager.result().vanilla.get(Id.V_HOTBAR);
        Rect sk = HudManager.rect(Id.SKILL_SLOTS);
        return hb != null && sk != null && sk.x() >= hb.right() && sk.x() - hb.right() <= 8 ? sk : null;
    }

    // 型の溜めの置き場 {x, y, 幅}: スキルの上。つながっていなければマナの帯の上 (枠の外)
    static int[] gaugeBox(Rect mana) {
        Rect sk = joinedSkills();
        if (sk != null) {
            return new int[] {sk.x() + 1, mana.y() + 3, sk.w() - 2};
        }
        return new int[] {mana.x() + 12, mana.y() - 9, mana.w() - 13};
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName()) || !on()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        event.setCanceled(true);
        if (mc.options.hideGui || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
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
        Rect sk = joinedSkills();
        int x0 = hb.x() - 3;
        int x1 = (sk != null ? sk.right() : hb.right()) + 3;
        int y0 = hb.bottom() - TOP;
        int y1 = hb.bottom();
        int slotY = y1 - 3 - SLOT;
        Golden.hudFrame(g, x0, y0, x1, y1);
        if (sk != null) {
            g.fill(hb.right() + 1, slotY, hb.right() + 2, slotY + SLOT, Golden.LINE_DIM);
        }
        // Lv の札 (体力とマナのあいだ)。振っていない点があれば右上に金の点
        GrowthData data = Growth.get(p);
        int lx = hb.x() + 82;
        int ly = y0 + 3;
        g.fill(lx, ly, lx + 18, ly + ROW_H, Golden.LINE);
        g.fill(lx + 1, ly + 1, lx + 17, ly + ROW_H - 1, 0xFF0A0E1E);
        String lv = Integer.toString(data.level);
        g.drawString(font, lv, lx + 9 - font.width(lv) / 2, ly + 1, Golden.GOLD, false);
        if (data.unspentPoints() > 0) {
            g.fill(lx + 15, ly - 1, lx + 18, ly + 2, 0xFFFFE08C);
        }
        // 経験値の線 (Lv 50 では今のジョブの熟練の進み)
        boolean max = data.level >= GrowthRules.MAX_LEVEL;
        jp.revati.skyabyss.rpg.skill.Job job = max ? jp.revati.skyabyss.rpg.client.GrowthHud.masteryJob(mc) : null;
        float frac = job != null ? jp.revati.skyabyss.rpg.JobMastery.progress(jp.revati.skyabyss.rpg.JobMastery.xp(data, job))
                : max ? 1f : (float) data.xp / Math.max(1, data.xpNeed());
        int xw = x1 - x0 - 6;
        int xy = slotY - 1;
        g.fill(x0 + 3, xy, x0 + 3 + xw, xy + 1, 0xA0000000);
        int fill = Mth.clamp(Math.round(xw * frac), 0, xw);
        if (fill > 0) {
            g.fill(x0 + 3, xy, x0 + 3 + fill, xy + 1, 0xFF000000 | Golden.GOLD);
        }
        if (jp.revati.skyabyss.rpg.client.GrowthClient.barFlash > 0 && !max) {
            int a = (int) (jp.revati.skyabyss.rpg.client.GrowthClient.barFlash / 12f * 0xC0);
            g.fill(x0 + 3, xy, x0 + 3 + fill, xy + 1, (a << 24) | 0xFFFFFF);
        }
        // 持ち物の 9 枠
        var inv = p.getInventory();
        int sx = hb.x() + 1;
        for (int i = 0; i < 9; i++) {
            int x = sx + i * SLOT;
            Golden.slot(g, x, slotY, SLOT);
            if (i == inv.selected) {
                Golden.outline(g, x - 1, slotY - 1, x + SLOT + 1, slotY + SLOT + 1, 0xFF000000 | Golden.GOLD);
                g.fill(x + 1, slotY + 1, x + SLOT - 1, slotY + SLOT - 1, 0x28E9C46A);
            }
        }
        for (int i = 0; i < 9; i++) {
            item(g, font, p, inv.items.get(i), sx + i * SLOT + 2, slotY + 2, i + 1);
        }
        // 逆手 (持っているときだけ。利き手の反対側) と攻撃の目盛り (設定がホットバーのとき)
        Rect off = HudManager.result().vanilla.get(Id.V_OFFHAND);
        if (off != null && !p.getOffhandItem().isEmpty()) {
            int ox = off.x() < hb.x() ? x0 - SLOT - 5 : x1 + 5;
            Golden.hudFrame(g, ox - 2, slotY - 2, ox + SLOT + 2, slotY + SLOT + 2);
            item(g, font, p, p.getOffhandItem(), ox + 2, slotY + 2, 10);
        }
        Rect atk = HudManager.result().vanilla.get(Id.V_ATTACK);
        if (atk != null && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float s = p.getAttackStrengthScale(0f);
            if (s < 1f) {
                int ax = atk.x() + 6;
                g.fill(ax, slotY, ax + 4, slotY + SLOT, Golden.OUTLINE);
                int h = Math.round((SLOT - 2) * s);
                g.fill(ax + 1, slotY + SLOT - 1 - h, ax + 3, slotY + SLOT - 1, 0xFF000000 | Golden.TEXT);
            }
        }
    }

    private static void item(GuiGraphics g, Font font, Player p, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(p, stack, x, y, seed);
        g.renderItemDecorations(font, stack, x, y);
    }

    // 守りの札 (枠の左上につく)。盾の絵と数
    static void armorTab(GuiGraphics g, Font font, Rect r, int armor, Runnable icon) {
        String t = Integer.toString(armor);
        int w = 13 + font.width(t) + 4;
        Golden.hudFrame(g, r.x(), r.y(), r.x() + w, r.y() + r.h() + 1);
        icon.run();
        g.drawString(font, t, r.x() + 13, r.y() + 2, 0xD8DEE6, false);
    }

    // 息の札 (枠の右上につく)。細い帯と秒
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
