package jp.revati.skyabyss.client.titan;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Region;
import jp.revati.skyabyss.hud.HudLayout.Request;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// ボスの技の名前の表示 (心臓・中ボス共通。docs/design/18_notices.md の 6.1)。
// SMALL (既定): ボスバーのすぐ下に小さな一行 (印は 8px)。短く出てすっと消える。続けて来たら下へ最大 3 行まで積み、同じ技は一行にまとめる。
//   画面の中央・足もとの予告には届かない。
// FULL: 従来どおり画面の上のほうに印と名前と残りの帯を大きく一つだけ。
// OFF: 出さない。
public final class BossAttackCue {
    private static final int MAX_LINES = 3;
    private static final int SMALL_MAX_TICKS = 40;
    private static final float FADE_IN = 4f;
    private static final float FADE_OUT = 10f;

    private static final class Entry {
        ResourceLocation icon;
        Component label;
        float start;
        int ticks;
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private BossAttackCue() {
    }

    private static float now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void clear() {
        ENTRIES.clear();
    }

    // 技が始まる (ticks = 予告の長さ)。同じ名前がまだ出ていれば、その行を延ばすだけ
    public static void show(ResourceLocation icon, Component label, int ticks) {
        Config.BossAttackNames mode = Config.BOSS_ATTACK_NAMES.get();
        if (mode == Config.BossAttackNames.OFF) {
            return;
        }
        float t = now();
        if (mode == Config.BossAttackNames.FULL) {
            ENTRIES.clear();
        }
        for (Entry e : ENTRIES) {
            if (e.label.getString().equals(label.getString())) {
                e.start = t;
                e.ticks = ticks;
                return;
            }
        }
        Entry e = new Entry();
        e.icon = icon;
        e.label = label;
        e.start = t;
        e.ticks = ticks;
        ENTRIES.add(e);
        while (ENTRIES.size() > MAX_LINES) {
            ENTRIES.remove(0);
        }
    }

    // HUD の要求: 上中央の、ボスバーのすぐ下 (SMALL は一行 11 を行数ぶん、FULL は印と名前と帯の大きな一つ)
    public static void requests(Minecraft mc, java.util.List<Request> out) {
        if (ENTRIES.isEmpty() || mc.options.hideGui) {
            return;
        }
        if (Config.BOSS_ATTACK_NAMES.get() == Config.BossAttackNames.FULL) {
            Entry e = ENTRIES.get(ENTRIES.size() - 1);
            out.add(Request.of(Id.BOSS_CUE, Region.TOP_CENTER, Math.max(48, mc.font.width(e.label)) + 8, 42));
            return;
        }
        int w = 0;
        for (Entry e : ENTRIES) {
            w = Math.max(w, mc.font.width(e.label) + (e.icon == null ? 0 : 10) + 6);
        }
        out.add(Request.of(Id.BOSS_CUE, Region.TOP_CENTER, w, ENTRIES.size() * 11));
    }

    public static void render(GuiGraphics g, Minecraft mc, float t) {
        if (ENTRIES.isEmpty() || mc.options.hideGui) {
            return;
        }
        Rect r = HudManager.rect(Id.BOSS_CUE);
        if (r == null) {
            return;
        }
        if (Config.BOSS_ATTACK_NAMES.get() == Config.BossAttackNames.FULL) {
            renderFull(g, mc, t, ENTRIES.get(ENTRIES.size() - 1), r);
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y() + 1;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            float shown = Math.min(Math.max(e.ticks, 14), SMALL_MAX_TICKS);
            float age = t - e.start;
            if (age < 0) {
                continue;
            }
            if (age > shown + FADE_OUT) {
                ENTRIES.remove(i--);
                continue;
            }
            float a = Math.min(1f, age / FADE_IN);
            if (age > shown) {
                a *= 1f - (age - shown) / FADE_OUT;
            }
            a *= 0.85f;
            int ia = Math.max(4, (int) (a * 255));
            int lw = mc.font.width(e.label);
            int iconW = e.icon == null ? 0 : 10;
            int x = cx - (lw + iconW) / 2;
            g.fill(x - 3, y - 1, x + lw + iconW + 3, y + 9, ((int) (a * 110) << 24) | 0x0A0E18);
            if (e.icon != null) {
                g.setColor(1f, 1f, 1f, a);
                g.blit(e.icon, x, y, 8, 8, 0, 0, 32, 32, 32, 32);
                g.setColor(1f, 1f, 1f, 1f);
            }
            g.drawString(mc.font, e.label, x + iconW, y, (ia << 24) | 0xEADFC0, false);
            y += 11;
        }
    }

    // 従来の大きい表示 (設定 FULL)
    private static void renderFull(GuiGraphics g, Minecraft mc, float t, Entry e, Rect r) {
        float k = (t - e.start) / Math.max(1, e.ticks);
        if (k > 1.25f) {
            ENTRIES.clear();
            return;
        }
        if (k < 0) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y();
        float alpha = k <= 1f ? 1f : 1f - (k - 1f) / 0.25f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * (0.6f + k * 1.4f));
        int a = (int) (alpha * 255);
        if (e.icon != null) {
            g.setColor(1f, 1f, 1f, alpha * pulse);
            g.blit(e.icon, cx - 12, y, 24, 24, 0, 0, 32, 32, 32, 32);
            g.setColor(1f, 1f, 1f, 1f);
        }
        int lw = mc.font.width(e.label);
        g.drawString(mc.font, e.label, cx - lw / 2, y + 27, (a << 24) | 0xFFF4D0, true);
        if (k <= 1f) {
            int bw = 48;
            int left = Math.round(bw * (1f - k));
            g.fill(cx - bw / 2, y + 38, cx + bw / 2, y + 40, (a / 2 << 24) | 0x202838);
            g.fill(cx - bw / 2, y + 38, cx - bw / 2 + left, y + 40, (a << 24) | 0xFFE278);
        }
    }
}
