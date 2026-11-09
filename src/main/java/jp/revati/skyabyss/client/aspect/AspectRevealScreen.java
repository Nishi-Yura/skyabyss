package jp.revati.skyabyss.client.aspect;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.aspect.Aspect;
import jp.revati.skyabyss.rpg.aspect.AspectNet;
import jp.revati.skyabyss.rpg.aspect.Aspects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 報いの祭壇 (docs/design/58)。巨神の祠のルートを越えたあと、出たアスペクトを裏返しの札で並べ、一枚ずつめくって見せる。
//   幕: 暗転 → 題字 → 札が上がる。クリック / Space / Enter で次の札をめくる (少し待てば自分でめくれる)。全部めくったらクリックで閉じる。Esc はいつでも閉じる
//   心臓の遺物の画面・場面 (シネマ) が出ているあいだは待ち、何も開いていなくなってから開く
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class AspectRevealScreen extends Screen {
    private static final int[] RARITY = {0x6FA8E0, 0xB48AE6, 0xEF8A5C};
    private static final int GOLD = 0xF2C14E;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;
    private static final int CARD_W = 132;
    private static final int CARD_H = 172;
    private static final int GAP = 21;
    private static final int T_TITLE = 6;
    private static final int T_CARDS = 18;
    private static final int FLIP_LEN = 10;
    // めくらずに待ったとき、自分でめくるまで (tick)
    private static final int AUTO_FLIP = 40;

    private static final List<AspectNet.Reveal> QUEUE = new ArrayList<>();

    private final AspectNet.Reveal reveal;
    private final int accent;
    private int age;
    // めくった札の数と、最後にめくった時刻
    private int flipped;
    private int flipAt = -100;

    private AspectRevealScreen(AspectNet.Reveal reveal) {
        super(Component.translatable("gui.skyabyss.aspect.reveal_title"));
        this.reveal = reveal;
        this.accent = switch (reveal.titan()) {
            case "tide_shell" -> 0x4FC3C8;
            case "thunder_wing" -> 0xE6C84E;
            case "ember" -> 0xE8743B;
            default -> 0x8FB8D0;
        };
    }

    // パケットから。何か開いていれば並べて待つ
    public static void open(AspectNet.Reveal reveal) {
        Minecraft.getInstance().execute(() -> QUEUE.add(reveal));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (QUEUE.isEmpty() || mc.player == null || mc.screen != null || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            return;
        }
        mc.setScreen(new AspectRevealScreen(QUEUE.remove(0)));
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        QUEUE.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (flipped < reveal.got().size() && age >= T_CARDS + 12 && age - Math.max(flipAt, T_CARDS) >= AUTO_FLIP) {
            flip();
        }
    }

    private boolean ready() {
        return age >= T_CARDS + 6 && age - flipAt >= FLIP_LEN;
    }

    private void flip() {
        if (flipped >= reveal.got().size()) {
            return;
        }
        AspectNet.Got g = reveal.got().get(flipped);
        Aspect a = Aspects.get(g.id());
        flipped++;
        flipAt = age;
        Minecraft mc = Minecraft.getInstance();
        int rarity = a == null ? 0 : a.rarity();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(rarity == Aspect.LEGENDARY ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                : SoundEvents.AMETHYST_BLOCK_CHIME, rarity == Aspect.LEGENDARY ? 1.0f : 0.8f + 0.2f * rarity, 0.9f));
    }

    private void advance() {
        if (!ready()) {
            return;
        }
        if (flipped < reveal.got().size()) {
            flip();
        } else {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            advance();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = age + pt;
        float dim = Mth.clamp(t / 8f, 0, 1);
        g.fill(0, 0, width, height, argb(0.82f * dim, 0x07090E));
        g.fillGradient(0, 0, width, height / 2, argb(0.20f * dim, accent), argb(0f, accent));
        // 題字
        float ta = Mth.clamp((t - T_TITLE) / 10f, 0, 1);
        if (ta > 0) {
            g.pose().pushPose();
            g.pose().translate(width / 2f, height / 2f - CARD_H / 2f - 40, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, title, 0, 0, argb(ta, GOLD));
            g.pose().popPose();
            Component sub = Component.translatable(jp.revati.skyabyss.trial.TrialCatalog.titanKey(reveal.titan()));
            g.drawCenteredString(font, sub, width / 2, height / 2 - CARD_H / 2 - 18, argb(ta, accent));
        }
        int n = reveal.got().size();
        int total = n * CARD_W + (n - 1) * GAP;
        int x0 = (width - total) / 2;
        int y0 = height / 2 - CARD_H / 2 + 6;
        for (int i = 0; i < n; i++) {
            float rise = Mth.clamp((t - T_CARDS - i * 5) / 10f, 0, 1);
            if (rise <= 0) {
                continue;
            }
            float ease = 1 - (1 - rise) * (1 - rise);
            int x = x0 + i * (CARD_W + GAP);
            int y = y0 + (int) ((1 - ease) * 30);
            // めくり: 0..1 で幅が 1 → 0 → 1。半分を過ぎたら表
            float fp = i < flipped ? (i == flipped - 1 ? Mth.clamp((t - flipAt) / FLIP_LEN, 0, 1) : 1) : 0;
            float sx = Math.abs(Mth.cos(fp * Mth.PI));
            boolean face = fp >= 0.5f;
            g.pose().pushPose();
            g.pose().translate(x + CARD_W / 2f, y + CARD_H / 2f, 0);
            g.pose().scale(Math.max(0.02f, sx), 1f, 1f);
            g.pose().translate(-CARD_W / 2f, -CARD_H / 2f, 0);
            if (face) {
                drawFace(g, reveal.got().get(i), ease, t - flipAt, i == flipped - 1);
            } else {
                drawBack(g, ease, i == flipped && ready());
            }
            g.pose().popPose();
        }
        if (age >= T_CARDS + 10) {
            g.drawCenteredString(font, Component.translatable("gui.skyabyss.aspect.reveal_hint"), width / 2, y0 + CARD_H + 18, argb(0.8f, DIM));
        }
    }

    private void frame(GuiGraphics g, int line, float alpha) {
        g.fill(0, 0, CARD_W, CARD_H, argb(alpha, 0x141821));
        g.fill(0, 0, CARD_W, 1, argb(alpha, line));
        g.fill(0, CARD_H - 1, CARD_W, CARD_H, argb(alpha, line));
        g.fill(0, 0, 1, CARD_H, argb(alpha, line));
        g.fill(CARD_W - 1, 0, CARD_W, CARD_H, argb(alpha, line));
        g.fill(3, 3, CARD_W - 3, 4, argb(alpha * 0.5f, line));
        g.fill(3, CARD_H - 4, CARD_W - 3, CARD_H - 3, argb(alpha * 0.5f, line));
    }

    private void drawBack(GuiGraphics g, float alpha, boolean next) {
        float pulse = next ? 0.5f + 0.5f * Mth.sin((age % 40) / 40f * Mth.TWO_PI) : 0;
        frame(g, accent, alpha);
        // 菱形の紋 (裏)
        int cx = CARD_W / 2;
        int cy = CARD_H / 2;
        for (int r = 0; r < 22; r++) {
            int w = 22 - r;
            g.fill(cx - w, cy - r, cx + w, cy - r + 1, argb(alpha * (0.18f + 0.25f * pulse), accent));
            g.fill(cx - w, cy + r, cx + w, cy + r + 1, argb(alpha * (0.18f + 0.25f * pulse), accent));
        }
        g.drawCenteredString(font, "?", cx, cy - 4, argb(alpha, TEXT));
    }

    private void drawFace(GuiGraphics g, AspectNet.Got got, float alpha, float since, boolean fresh) {
        Aspect a = Aspects.get(got.id());
        if (a == null) {
            frame(g, DIM, alpha);
            return;
        }
        int line = RARITY[a.rarity()];
        frame(g, line, alpha);
        // めくった直後の光
        if (fresh) {
            float glow = Mth.clamp(1 - (since - FLIP_LEN / 2f) / 24f, 0, 1);
            g.fillGradient(1, 1, CARD_W - 1, CARD_H / 2, argb(glow * 0.55f, line), argb(0f, line));
        }
        g.fillGradient(1, 1, CARD_W - 1, 40, argb(alpha * 0.22f, line), argb(0f, line));
        int y = 10;
        g.drawCenteredString(font, Component.translatable("aspect.skyabyss.rarity." + a.rarity()), CARD_W / 2, y, argb(alpha, line));
        y += 14;
        for (FormattedCharSequence l : font.split(Component.translatable(a.nameKey()), CARD_W - 12)) {
            g.drawCenteredString(font, l, CARD_W / 2, y, argb(alpha, TEXT));
            y += 11;
        }
        y += 6;
        int before = Aspects.levelOf(got.before());
        int after = Aspects.levelOf(got.after());
        Component lv = got.before() <= 0 ? Component.translatable("gui.skyabyss.aspect.reveal_new").append("  ").append(Component.translatable("aspect.skyabyss.level", after))
                : after > before ? Component.translatable("gui.skyabyss.aspect.reveal_up", before, after)
                : Component.translatable(after >= Aspects.MAX_LEVEL ? "aspect.skyabyss.level_max" : "aspect.skyabyss.level", after);
        g.drawCenteredString(font, lv, CARD_W / 2, y, argb(alpha, got.before() <= 0 || after > before ? GOLD : DIM));
        y += 16;
        for (FormattedCharSequence l : font.split(Component.translatable("aspect.skyabyss.skill", Component.translatable(a.skill().nameKey())), CARD_W - 12)) {
            g.drawString(font, l, 6, y, argb(alpha, DIM), false);
            y += 10;
        }
        y += 2;
        for (Aspect.Fx f : a.fx()) {
            for (FormattedCharSequence l : font.split(Component.translatable("aspect.skyabyss.fx." + f.verb().name(), Math.round(f.at(Math.max(1, after)) * 100)), CARD_W - 12)) {
                g.drawString(font, l, 6, y, argb(alpha, TEXT), false);
                y += 10;
            }
        }
        // 次の Lv までの帯
        int next = Aspects.nextCopies(got.after());
        int barW = CARD_W - 16;
        g.fill(8, CARD_H - 12, 8 + barW, CARD_H - 9, argb(alpha, 0x2A2E38));
        double fr = next < 0 ? 1 : (got.after() - Aspects.copiesFor(after)) / (double) Math.max(1, next - Aspects.copiesFor(after));
        g.fill(8, CARD_H - 12, 8 + (int) Math.round(barW * fr), CARD_H - 9, argb(alpha, line));
    }
}
