package jp.revati.skyabyss.client.story;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.Window;

import jp.revati.skyabyss.client.story.StoryReelClient.Panel;
import jp.revati.skyabyss.client.story.StoryReelClient.Reel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// 語りの紙芝居の画面。黒い地の真ん中に 240x135 の絵を整数倍で拡大し (画素をくっきり)、下に語りを一字ずつ出す (カタカタという音)。
// 絵はゆっくり、絵の画素の単位で寄る・流れる。次の絵へは暗く溶けて移る。
//   クリック・空白・Enter: 文の途中なら文を出し切る、出し切っていれば次の絵へ。文を出し切って hold 秒たてば勝手に次へ
//   Esc: 全部飛ばす (飛ばしても、語りのあとの出来事は進む)
public class StoryReelScreen extends Screen {
    static final int W = 240;
    static final int H = 135;
    // 一字を出す間 (tick)。日本語は一字が重いので 1.5 tick
    static final float CHAR_TICKS = 1.5f;
    static final int FADE = 12;
    // 絵がゆっくり動く幅 (絵の画素)
    static final int DRIFT = 10;

    private final Reel reel;
    private int index;
    private int age;
    private int fadeOut = -1;
    private boolean finished;
    private int lastChars;

    public StoryReelScreen(Reel reel) {
        super(Component.empty());
        this.reel = reel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    Panel panel() {
        return reel.panels().get(index);
    }

    Component text() {
        return Component.translatable(panel().textKey());
    }

    int totalChars() {
        return text().getString().length();
    }

    int shownChars() {
        return Mth.clamp((int) ((age - FADE) / CHAR_TICKS), 0, totalChars());
    }

    @Override
    public void tick() {
        if (finished) {
            return;
        }
        age++;
        if (age == FADE) {
            panel().sound().ifPresent(id -> {
                SoundEvent ev = SoundEvent.createVariableRangeEvent(id);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1f, 0.5f));
            });
        }
        int shown = shownChars();
        if (shown > lastChars) {
            String s = text().getString();
            char ch = s.charAt(Math.min(shown - 1, s.length() - 1));
            if (!Character.isWhitespace(ch) && "、。…「」".indexOf(ch) < 0 && shown % 2 == 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.55f, 0.18f));
            }
            lastChars = shown;
        }
        if (fadeOut >= 0) {
            fadeOut++;
            if (fadeOut >= FADE) {
                next();
            }
            return;
        }
        int doneAt = FADE + (int) Math.ceil(totalChars() * CHAR_TICKS);
        if (age >= doneAt + (int) (panel().hold() * 20)) {
            fadeOut = 0;
        }
    }

    private void next() {
        if (index + 1 >= reel.panels().size()) {
            end();
            return;
        }
        index++;
        age = 0;
        fadeOut = -1;
        lastChars = 0;
    }

    private void advance() {
        if (fadeOut >= 0 || finished) {
            return;
        }
        if (shownChars() < totalChars()) {
            age = FADE + (int) Math.ceil(totalChars() * CHAR_TICKS);
            lastChars = totalChars();
        } else {
            fadeOut = 0;
        }
    }

    private void end() {
        if (finished) {
            return;
        }
        finished = true;
        StoryReelClient.done(reel.id());
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        end();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_Z) {
            advance();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        g.fill(0, 0, sw, sh, 0xFF000000);
        if (finished || reel.panels().isEmpty()) {
            return;
        }
        Window win = Minecraft.getInstance().getWindow();
        double gui = win.getGuiScale();
        // 実の画素で整数倍 (横 88%・縦 64% に収まる最大)
        int k = Math.max(1, (int) Math.floor(Math.min(win.getWidth() * 0.88 / W, win.getHeight() * 0.64 / H)));
        float fw = (float) (W * k / gui);
        float fh = (float) (H * k / gui);
        float fx = (sw - fw) / 2f;
        float fy = sh * 0.42f - fh / 2f;
        Panel p = panel();
        // ゆっくり動く: 絵を DRIFT 画素ぶん大きく描き、時間で窓の中をずらす (絵の画素の単位で)
        float life = (age + partial) / Math.max(1f, FADE * 2 + totalChars() * CHAR_TICKS + p.hold() * 20);
        int steps = Mth.clamp((int) (life * DRIFT), 0, DRIFT);
        float px = (float) (k / gui);
        float ox = 0;
        float oy = 0;
        float extra = DRIFT * px;
        switch (p.motion()) {
            case "left" -> ox = -steps * px;
            case "right" -> ox = -(DRIFT - steps) * px;
            case "up" -> oy = -steps * px;
            case "down" -> oy = -(DRIFT - steps) * px;
            default -> {
                ox = -DRIFT / 2f * px;
                oy = -DRIFT / 2f * px;
            }
        }
        g.enableScissor((int) fx, (int) fy, (int) Math.ceil(fx + fw), (int) Math.ceil(fy + fh));
        g.pose().pushPose();
        g.pose().translate(fx + ox, fy + oy, 0);
        float scale = (fw + extra) / W;
        g.pose().scale(scale, (fh + extra) / H, 1f);
        g.blit(p.image(), 0, 0, 0, 0, W, H, W, H);
        g.pose().popPose();
        g.disableScissor();
        // 語り (絵の下。一字ずつ)
        Font font = Minecraft.getInstance().font;
        String full = text().getString();
        String shown = full.substring(0, Math.min(full.length(), shownChars()));
        int maxW = (int) Math.min(fw, sw - 32);
        List<FormattedCharSequence> lines = font.split(Component.literal(shown), maxW);
        int ty = (int) (fy + fh + 12);
        for (FormattedCharSequence line : lines) {
            int lw = font.width(line);
            g.drawString(font, line, (int) (sw / 2f - lw / 2f), ty, 0xFFF2F0EA, false);
            ty += font.lineHeight + 4;
        }
        // 飛ばし方の小さな字
        Component hint = Component.translatable("story.skyabyss.reel.hint");
        g.drawString(font, hint, sw - font.width(hint) - 10, sh - font.lineHeight - 8, 0xFF55505E, false);
        // 暗く溶ける (入り・出)
        float black = 0f;
        if (age < FADE) {
            black = 1f - (age + partial) / FADE;
        }
        if (fadeOut >= 0) {
            black = Math.max(black, (fadeOut + partial) / FADE);
        }
        if (black > 0f) {
            int a = (int) (Mth.clamp(black, 0f, 1f) * 255f);
            g.fill(0, 0, sw, sh, a << 24);
        }
    }
}
