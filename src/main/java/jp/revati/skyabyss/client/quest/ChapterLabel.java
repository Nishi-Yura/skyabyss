package jp.revati.skyabyss.client.quest;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import jp.revati.skyabyss.client.opening.OpeningClient;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 今の章の札 (「一章　空を泳ぐもの」)。左上の目的の帯のすぐ上に、小さく出しっぱなしにする (docs/design/18 の 3.5 節)。
// 章はサーバーが人ごとに導いて送る (quest/QuestUx)。場面・F1・F3・序盤の目覚めの間は出さない。設定 chapterLabel で消せる。
// 置き場は HudManager (Id.CHAPTER。目的の帯のすぐ上)
public final class ChapterLabel {
    static final int ROWS = 11;

    // 受け取った章の番号 (-1 = まだ届いていない)
    private static volatile int chapter = -1;

    private ChapterLabel() {
    }

    public static void receive(int number) {
        chapter = number;
    }

    // 開発用 (撮影): 章を決める
    public static void setForTest(int number) {
        chapter = number;
    }

    @Nullable
    static Chapters current() {
        int n = chapter;
        Chapters[] all = Chapters.values();
        return n >= 0 && n < all.length ? all[n] : null;
    }

    static Component text(Chapters c) {
        // 深淵の中は、いる区切り (五章の一「骨の層」…) を出す
        Component abyss = jp.revati.skyabyss.story.ch5.client.AbyssSectionClient.label();
        if (abyss != null) {
            return abyss;
        }
        return Component.translatable(c.tabKey()).append("　").append(Component.translatable(c.titleKey()));
    }

    // 出す場面か (章が分かっていて、設定が入っていて、隠す場面でないとき)
    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && current() != null && Config.CHAPTER_LABEL.get() && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && !OpeningClient.awakeActive() && !CinematicClient.active();
    }

    // HUD の要求: 左上の帯の、目的の帯のすぐ上 (成長の表示の下)
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (active()) {
            Font font = mc.font;
            String full = text(current()).getString();
            int want = ObjectiveStrip.bandWidth(mc);
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.CHAPTER, jp.revati.skyabyss.hud.HudLayout.Region.TOP_LEFT, jp.revati.skyabyss.hud.HudLayout.Fit.keyed(java.util.List.of(full, want), (maxW, maxH) -> {
                int w = Math.min(want, maxW - 6);
                int n = Math.min(2, (maxH + 3) / ROWS);
                if (n < 1 || w < 40) {
                    return null;
                }
                int lines = Math.min(n, jp.revati.skyabyss.lore.LoreWrap.wrap(full, w - 2, font::width).size());
                return new jp.revati.skyabyss.hud.HudLayout.Size(w + 6, lines * ROWS - 3);
            })));
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Chapters c = current();
        Font font = mc.font;
        float a = mc.screen != null ? 0.45f : 0.85f;
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.CHAPTER);
        if (r == null) {
            return;
        }
        int x = r.x() + 3;
        int y = r.y();
        Component text = text(c);
        java.util.List<String> lines = new java.util.ArrayList<>(jp.revati.skyabyss.lore.LoreWrap.wrap(text.getString(), r.w() - 8, font::width));
        int maxLines = Math.max(1, (r.h() + 3) / ROWS);
        if (lines.size() > maxLines) {
            lines = new java.util.ArrayList<>(lines.subList(0, maxLines));
            lines.set(maxLines - 1, font.plainSubstrByWidth(lines.get(maxLines - 1), r.w() - 8 - font.width("…")) + "…");
        }
        // 細い金の線 (帯の金の縁と揃える) と、小さめの字
        int alpha = ((int) (255 * a)) << 24;
        g.fill(x - 3, y, x - 2, y + lines.size() * ROWS - 3, alpha | 0xA88A3C);
        for (String line : lines) {
            g.drawString(font, line, x + 1, y, alpha | 0xD9C48A, true);
            y += ROWS;
        }
    }
}
