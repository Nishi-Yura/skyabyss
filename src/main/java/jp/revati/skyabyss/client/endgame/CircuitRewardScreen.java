package jp.revati.skyabyss.client.endgame;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.client.ui.Golden;
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
// 押す (か Space) と残りを全部出す。品はもう旅嚢に入っている (幕は見せるだけ)。Esc で閉じる。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 枠は 34 角・9 列 (下から詰める)、箱は残りの高さの真ん中、下の段に数と手引き
public class CircuitRewardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final int BG = 0x05070A;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int DIM = 0x8C98A4;

    private static final int W = Golden.L_W;
    private static final int H = Golden.L_H;
    private static final int COLS = 9;
    private static final int SLOT = Golden.S34;
    private static final int GAP = Golden.S3;

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

    // 枠の行の数と、枠の一番上 (中身の下の端から詰める)
    private int rows() {
        return Math.max(1, (items().size() + COLS - 1) / COLS);
    }

    private int gridTop() {
        return LAYOUT.body().bottom() - rows() * (SLOT + GAP) + GAP;
    }

    // 枠の所: 行ごとに中央へ寄せる (少ないときに左へ寄らない)
    private int[] slot(int i) {
        int n = items().size();
        int row = i / COLS;
        int inRow = Math.min(COLS, n - row * COLS);
        int left = (W - inRow * SLOT - (inRow - 1) * GAP) / 2;
        return new int[] {left + (i % COLS) * (SLOT + GAP), gridTop() + row * (SLOT + GAP)};
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
        fit.fit(width, height, W, H, 3f, 0.5f);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        g.drawCenteredString(font, title, W / 2, Golden.textY(head.y(), head.h()), argb(1f, GOLD));
        Component camp = reward.camp().isEmpty() ? Component.empty() : Component.translatable("label.skyabyss.facility.circuit_camp_" + reward.camp() + ".sub");
        Component sub = Component.translatable("gui.skyabyss.reward.sub", camp, reward.marks(), reward.coins());
        g.drawCenteredString(font, sub, W / 2, Golden.textY(LAYOUT.body().y(), Golden.ROW), argb(1f, TEAL));
        // 箱の所: 一行目と枠のあいだの真ん中。高さが足りなければ 2 倍 (足りれば 3 倍)
        int boxTop = LAYOUT.body().y() + Golden.ROW + Golden.S5;
        int room = gridTop() - Golden.S5 - boxTop;
        int chestK = room >= 48 ? 3 : 2;
        int chestY = boxTop + Math.max(0, (room - 16 * chestK) / 2);
        int chestCy = chestY + 8 * chestK;
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
            for (int r = Math.min(40, room / 2 + 8); r >= 6; r -= 4) {
                disc(g, W / 2, chestCy, r, (Math.max(0, peak - r / 3) << 24) | (glow & 0xFFFFFF));
            }
        }
        float sx = (float) Math.sin((t + partialTick) * 2.1) * shake;
        float sy = (float) Math.cos((t + partialTick) * 2.7) * shake * 0.5f;
        g.pose().pushPose();
        g.pose().translate(W / 2f - 8 * chestK + sx, chestY + sy, 0);
        g.pose().scale(chestK, chestK, 1f);
        g.renderItem(new ItemStack(Items.CHEST), 0, 0);
        g.pose().popPose();
        // 出た品の枠
        hovered = ItemStack.EMPTY;
        for (int i = 0; i < items().size(); i++) {
            int[] s = slot(i);
            Golden.recess(g, new Golden.Box(s[0], s[1], SLOT, SLOT));
            if (i >= shown) {
                continue;
            }
            ItemStack st = items().get(i);
            int rgb = tier(st).rgb();
            boolean on = mx >= s[0] && mx < s[0] + SLOT && my >= s[1] && my < s[1] + SLOT;
            Golden.outline(g, s[0], s[1], s[0] + SLOT, s[1] + SLOT, argb(1f, rgb));
            if (on) {
                g.fill(s[0] + 1, s[1] + 1, s[0] + SLOT - 1, s[1] + SLOT - 1, argb(0.12f, GOLD));
            }
            if (i == flashSlot && flash > 0) {
                int a = flash * (tier(st).atLeast(GearTier.HEROIC) ? 8 : 14);
                g.fill(s[0] - 3, s[1] - 3, s[0] + SLOT + 3, s[1] + SLOT + 3, (Math.min(220, a) << 24) | (rgb & 0xFFFFFF));
            }
            g.pose().pushPose();
            g.pose().translate(s[0] + 1, s[1] + 1, 0);
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
        Golden.Box fb = LAYOUT.foot();
        g.drawCenteredString(font, foot, W / 2, Golden.textY(fb.y(), fb.h()), argb(1f, going ? Golden.DIM : TEAL));
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
