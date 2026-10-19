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
// 札の高さの分 (ROWS) だけ、下の帯と追跡の表示は下へずれる (QuestHud.y)
public final class ChapterLabel {
    static final int ROWS = 11;
    private static final int WIDTH = ObjectiveStrip.WIDTH;

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
        return Component.translatable(c.tabKey()).append("　").append(Component.translatable(c.titleKey()));
    }

    // 出す場面か (章が分かっていて、設定が入っていて、隠す場面でないとき)
    static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && current() != null && Config.CHAPTER_LABEL.get() && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen()
                && !OpeningClient.awakeActive() && !CinematicClient.active();
    }

    // 札の分だけ、下の帯をずらす
    static int rows() {
        return active() ? ROWS : 0;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Chapters c = current();
        Font font = mc.font;
        float a = mc.screen != null ? 0.45f : 0.85f;
        int x = QuestHud.X;
        int y = QuestHud.y() - ROWS;
        Component text = text(c);
        String shown = text.getString();
        // 細い金の線 (帯の金の縁と揃える) と、小さめの字
        int alpha = ((int) (255 * a)) << 24;
        g.fill(x - 3, y, x - 2, y + 8, alpha | 0xA88A3C);
        if (font.width(shown) > WIDTH) {
            shown = font.plainSubstrByWidth(shown, WIDTH - font.width("…")) + "…";
        }
        g.drawString(font, shown, x + 1, y, alpha | 0xD9C48A, true);
    }
}
