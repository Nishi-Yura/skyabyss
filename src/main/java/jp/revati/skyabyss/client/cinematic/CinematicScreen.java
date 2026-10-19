package jp.revati.skyabyss.client.cinematic;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.cinematic.DialogueLine;
import jp.revati.skyabyss.client.opening.OpeningOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// 場面の間の画面 (docs/design/20_cinematics.md)。操作を受けとめ (歩く・攻撃・メニューは効かない)、HUD の代わりに
// 上下の黒帯 (レターボックス)、台詞 (話し手の名札つき)、スキップの案内を描く。ゲームは止めない。
// カメラそのものは CinematicClient が動かす
final class CinematicScreen extends Screen {
    // 黒帯の高さ (画面の高さに対する割合)
    private static final float BAR = 0.11f;

    CinematicScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // 場面の間のキーは全部ここで止める (Esc でポーズ画面を出さない。スキップは長押しで見る)
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return true;
    }

    @Override
    public void tick() {
        // マウスのカーソルは見せない
        if (minecraft != null) {
            GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    }

    private static float smooth(float x) {
        float c = Mth.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        CinematicClient.Run r = CinematicClient.run();
        if (r == null || r.screen != this) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float sec = CinematicClient.seconds(r, partial);
        float dur = r.scene.duration();
        boolean fadeIn = r.phase == CinematicClient.Phase.FADE_IN;
        // 開幕: 暗闇・まぶた・題字。そのほか: 黒から明ける
        if (r.scene.intro()) {
            if (!fadeIn) {
                OpeningOverlay.renderIntro(g, sec * 20f, false);
            }
        } else if (!fadeIn && sec < 0.8f) {
            g.fill(0, 0, w, h, ((int) ((1f - smooth(sec / 0.8f)) * 255f) << 24));
        }
        // 黒帯: 始めにせり出し、終わりにしまう (暗転で終わる場面は、帯を出したまま暗転)
        float bars = fadeIn ? 0f : r.phase == CinematicClient.Phase.FADE_OUT ? 1f : smooth(Math.min(sec / 0.9f, (dur - sec) / 0.9f));
        int barH = (int) (h * BAR * bars);
        if (barH > 0) {
            g.fill(0, 0, w, barH, 0xFF000000);
            g.fill(0, h - barH, w, h, 0xFF000000);
        }
        Font font = mc.font;
        if (!fadeIn) {
            renderLine(g, font, r, sec, w, h, (int) (h * BAR));
            renderSkip(g, font, r, sec, w, h, barH);
        }
        // 暗転 (スキップ・終わり)
        float black = switch (r.phase) {
            case FADE_OUT -> Mth.clamp((r.dipTicks + partial) / CinematicClient.DIP, 0f, 1f);
            case FADE_IN -> 1f - Mth.clamp((r.dipTicks + partial) / CinematicClient.DIP, 0f, 1f);
            default -> 0f;
        };
        if (black > 0.01f) {
            g.fill(0, 0, w, h, ((int) (black * 255f) << 24));
        }
    }

    // 台詞: 話し手ごとに色と見せ方が違う。画面の下、黒帯のすぐ上に、薄い暗がりを敷いて
    private void renderLine(GuiGraphics g, Font font, CinematicClient.Run r, float sec, int w, int h, int barFull) {
        DialogueLine line = null;
        for (DialogueLine l : r.scene.lines()) {
            if (sec >= l.at() && sec <= l.end()) {
                line = l;
                break;
            }
        }
        if (line == null) {
            return;
        }
        float a = Math.min(smooth((sec - line.at()) / 0.35f), smooth((line.end() - sec) / 0.45f));
        if (a < 0.02f) {
            return;
        }
        int alpha = Mth.clamp((int) (a * 255f), 4, 255) << 24;
        int accent;
        int labelColor;
        Style style;
        Component label;
        switch (line.speaker()) {
            case HEAVEN -> {
                style = Style.EMPTY.withColor(0xEBD07F);
                accent = 0xD8B24A;
                labelColor = 0xC9A94F;
                label = Component.translatable("cinematic.skyabyss.speaker.heaven");
            }
            case NPC -> {
                style = Style.EMPTY.withColor(0xFFFFFF);
                accent = 0x7FB8D6;
                labelColor = 0xAFC8D8;
                label = Component.translatable(line.label());
            }
            default -> {
                style = Style.EMPTY.withItalic(true).withColor(0xDCE6E4);
                accent = 0x7FD6D0;
                labelColor = 0x8FA3A6;
                // 心の声の名札は見ている本人の名前 (多人数でもそれぞれ自分の名前)
                label = Component.literal(net.minecraft.client.Minecraft.getInstance().getUser().getName());
            }
        }
        MutableComponent text = Component.translatable(line.text()).withStyle(style);
        int maxW = Math.min(w - 60, Math.max(180, (int) (w * 0.62f)));
        List<FormattedCharSequence> rows = font.split(text, maxW);
        if (rows.size() > 3) {
            rows = rows.subList(0, 3);
        }
        int blockH = rows.size() * 12 + 13;
        int bottom = h - barFull - 16;
        int top = bottom - blockH;
        int cx = w / 2;
        // 暗がり: 上へ向かって消える
        g.fillGradient(0, top - 26, w, bottom + 10, 0x00000000, ((int) (a * 0x99) << 24) | 0x05080C);
        g.fill(cx - 36, top - 6, cx + 36, top - 5, ((int) (a * 0xB0) << 24) | accent);
        g.drawCenteredString(font, label, cx, top, alpha | labelColor);
        int y = top + 13;
        for (FormattedCharSequence row : rows) {
            g.drawString(font, row, cx - font.width(row) / 2, y, alpha | 0xFFFFFF, true);
            y += 12;
        }
    }

    // スキップの案内: 右下。始めは濃く、あとは控えめに。押している間は進みの線が伸びる
    private void renderSkip(GuiGraphics g, Font font, CinematicClient.Run r, float sec, int w, int h, int barH) {
        boolean holding = r.skipHeld > 0;
        float vis = holding ? 1f : Mth.clamp(1f - (sec - 5f) / 1.5f, 0.4f, 1f);
        if (barH < 8) {
            vis *= barH / 8f;
        }
        Component hint = Component.translatable("cinematic.skyabyss.skip");
        int tw = font.width(hint);
        int x = w - tw - 14;
        int y = h - Math.max(barH, 24) + Math.max(barH, 24) / 2 - 8;
        int alpha = Mth.clamp((int) (vis * 230f), 4, 255) << 24;
        g.drawString(font, hint, x, y, alpha | (holding ? 0xFFFFFF : 0xB8C4C2), false);
        int by = y + 11;
        g.fill(x, by, x + tw, by + 2, ((int) (vis * 0x50) << 24) | 0xFFFFFF);
        float p = Mth.clamp(r.skipHeld / (float) CinematicClient.SKIP_HOLD, 0f, 1f);
        if (p > 0f) {
            g.fill(x, by, x + (int) (tw * p), by + 2, 0xFF000000 | 0xE9CC7C);
        }
    }
}
