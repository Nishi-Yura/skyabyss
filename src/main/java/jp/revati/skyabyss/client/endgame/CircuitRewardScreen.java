package jp.revati.skyabyss.client.endgame;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.CircuitCamps;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 巡りの報いの幕 (endgame/CircuitCamps.Reward。巡り路を終えたとき)。Wynncraft のルートランの報いの箱のように、
// 中央の箱が揺れて、引いた品を一つずつ枠へ出す。階級が高いほど揺れが長く強く、出たときに階級の色で光る (ヒロイック・神器は音も大きい)。
// 押す (か Space) と残りを全部出す。品はもう旅嚢に入っている (幕は見せるだけ)。Esc で閉じる
public class CircuitRewardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x05070A;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int DIM = 0x8C98A4;

    private static final int W = 380;
    private static final int H = 250;
    private static final int COLS = 8;
    private static final int SLOT = 36;
    private static final int GAP = 6;
    private static final int GRID_Y = 112;

    private final UiFit fit = new UiFit();
    private final CircuitCamps.Reward reward;
    // 出した数、今の品の揺れの経過と長さ、出た品ごとの光の残り
    private int shown;
    private int t;
    private int flashSlot = -1;
    private int flash;
    private ItemStack hovered = ItemStack.EMPTY;

    public CircuitRewardScreen(CircuitCamps.Reward reward) {
        super(Component.translatable("gui.skyabyss.reward.title"));
        this.reward = reward;
    }

    public static void receive(CircuitCamps.Reward r) {
        Minecraft.getInstance().setScreen(new CircuitRewardScreen(r));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ItemStack> items() {
        return reward.items();
    }

    static GearTier tier(ItemStack s) {
        return s.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
    }

    // 一つの品を出すまでの tick (階級が高いほど長く溜める)
    static int suspense(GearTier tier) {
        return switch (tier) {
            case ARTIFACT, MYTHIC -> 56;
            case HEROIC -> 34;
            case EPIC -> 22;
            case RARE -> 14;
            default -> 10;
        };
    }

    @Override
    public void tick() {
        flash = Math.max(0, flash - 1);
        if (shown >= items().size()) {
            return;
        }
        t++;
        GearTier tier = tier(items().get(shown));
        if (t >= suspense(tier)) {
            reveal(true);
        }
    }

    private void reveal(boolean sound) {
        GearTier tier = tier(items().get(shown));
        flashSlot = shown;
        flash = tier.atLeast(GearTier.HEROIC) ? 30 : 14;
        shown++;
        t = 0;
        if (sound) {
            var sm = Minecraft.getInstance().getSoundManager();
            float pitch = switch (tier) {
                case ARTIFACT, MYTHIC -> 2.0f;
                case HEROIC -> 1.7f;
                case EPIC -> 1.4f;
                case RARE -> 1.2f;
                default -> 1.0f;
            };
            sm.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 0.7f));
            if (tier.atLeast(GearTier.HEROIC)) {
                sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f));
            }
        }
    }

    private void revealAll() {
        while (shown < items().size()) {
            reveal(false);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f));
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (shown < items().size()) {
            revealAll();
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE && shown < items().size()) {
            revealAll();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
    }

    // 枠の所: 行ごとに中央へ寄せる (少ないときに左へ寄らない)
    private int[] slot(int i) {
        int n = items().size();
        int rows = Math.max(1, (n + COLS - 1) / COLS);
        int top = GRID_Y + Math.max(0, (3 - rows)) * (SLOT + GAP) / 2;
        int row = i / COLS;
        int inRow = Math.min(COLS, n - row * COLS);
        int left = (W - inRow * SLOT - (inRow - 1) * GAP) / 2;
        return new int[] {left + (i % COLS) * (SLOT + GAP), top + row * (SLOT + GAP)};
    }

    // 塗りつぶした丸 (半分の画素で)
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        int rr = r * 2;
        for (int dy = -rr; dy <= rr; dy++) {
            int half = (int) Math.floor(Math.sqrt(rr * rr - dy * dy));
            g.fill(cx * 2 - half, cy * 2 + dy, cx * 2 + half + 1, cy * 2 + dy + 1, color);
        }
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        g.fill(0, 0, width, height, argb(0.88f, BG));
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        frame(g, 0, 0, W, H, 0x3A4A58, PANEL);
        TextStyle.HEADING.drawCentered(g, font, title, W / 2, 9, 1f, GOLD);
        Component camp = reward.camp().isEmpty() ? Component.empty() : Component.translatable("label.skyabyss.facility.circuit_camp_" + reward.camp() + ".sub");
        Component sub = Component.translatable("gui.skyabyss.reward.sub", camp, reward.marks(), reward.coins());
        g.drawCenteredString(font, sub, W / 2, 34, argb(1f, TEAL));
        // 中央の箱: 次の品を溜めているあいだ揺れ、階級の色の光がにじむ
        boolean going = shown < items().size();
        float shake = 0f;
        int glow = 0;
        if (going) {
            GearTier next = tier(items().get(shown));
            float k = (t + partialTick) / suspense(next);
            shake = k * (next.atLeast(GearTier.HEROIC) ? 3.5f : next.atLeast(GearTier.EPIC) ? 2.2f : 1.2f);
            glow = next.rgb();
            // にじむ光: 中心ほど濃い輪を重ねる (四角く見えないよう、半径ごとに薄く)
            int peak = (int) (k * (next.atLeast(GearTier.EPIC) ? 46 : 22));
            for (int r = 40; r >= 6; r -= 4) {
                disc(g, W / 2, 78, r, (Math.max(0, peak - r / 3) << 24) | (glow & 0xFFFFFF));
            }
        }
        float sx = (float) Math.sin((t + partialTick) * 2.1) * shake;
        float sy = (float) Math.cos((t + partialTick) * 2.7) * shake * 0.5f;
        g.pose().pushPose();
        g.pose().translate(W / 2f - 24 + sx, 54 + sy, 0);
        g.pose().scale(3f, 3f, 1f);
        g.renderItem(new ItemStack(Items.CHEST), 0, 0);
        g.pose().popPose();
        // 出た品の枠
        hovered = ItemStack.EMPTY;
        for (int i = 0; i < items().size(); i++) {
            int[] s = slot(i);
            if (i >= shown) {
                frame(g, s[0], s[1], SLOT, SLOT, 0x232B36, 0x0C0F15);
                continue;
            }
            ItemStack st = items().get(i);
            int rgb = tier(st).rgb();
            boolean on = mx >= s[0] && mx < s[0] + SLOT && my >= s[1] && my < s[1] + SLOT;
            frame(g, s[0], s[1], SLOT, SLOT, rgb, on ? 0x2A2618 : CARD);
            if (i == flashSlot && flash > 0) {
                int a = flash * (tier(st).atLeast(GearTier.HEROIC) ? 8 : 14);
                g.fill(s[0] - 3, s[1] - 3, s[0] + SLOT + 3, s[1] + SLOT + 3, (Math.min(220, a) << 24) | (rgb & 0xFFFFFF));
            }
            g.pose().pushPose();
            g.pose().translate(s[0] + SLOT / 2f - 16, s[1] + SLOT / 2f - 16, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
            if (on) {
                hovered = st;
            }
        }
        Component foot = items().isEmpty() ? Component.translatable("gui.skyabyss.reward.none")
                : going ? Component.translatable("gui.skyabyss.reward.count", shown, items().size()).copy().append("  ").append(Component.translatable("gui.skyabyss.reward.skip"))
                : Component.translatable("gui.skyabyss.reward.done");
        g.drawCenteredString(font, foot, W / 2, H - 18, argb(1f, going ? DIM : TEAL));
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
