package jp.revati.skyabyss.client.endgame;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.client.ui.PickStage;
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
import jp.revati.skyabyss.client.ui.Paper;

// 巡りの報いの幕 (endgame/CircuitCamps.Reward。巡り路を終えたとき)。Wynncraft のルートランの報いの箱のように、
// 中央の箱が揺れて、引いた品を一つずつ札へ出す。階級が高いほど揺れが長く強く、箱の後ろの階級の色の紙の菱が大きく育つ (ヒロイック・神器は音も大きい)。
// 押す (か Space) と残りを全部出す。品はもう旅袋に入っている (幕は見せるだけ)。Esc で閉じる。
// 組み方は選ぶ類の「三枚を扇に」(client/ui/PickStage。docs/design/56 の 2.1) の舞台: 左上に題と野営地・印・鐘銭。
// 出た品は少し傾けた生成りの札 (下から 9 枚ずつ詰める。影は階級の色 = 意味の色を黒の側に)。出た瞬間の札は桃色で持ち上がる。下の山の上に数と手引き
public class CircuitRewardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int W = PickStage.W;
    private static final int H = PickStage.H;
    private static final int COLS = 9;
    private static final int SLOT = 30;
    private static final int GAP = 5;
    private static final int GRID_BOTTOM = 192;

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

    // 撮影用: はじめから全部出した幕
    public CircuitRewardScreen revealed() {
        shown = items().size();
        return this;
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

    // 枠の行の数と、枠の一番上 (中身の下の端から詰める)
    private int rows() {
        return Math.max(1, (items().size() + COLS - 1) / COLS);
    }

    private int gridTop() {
        return GRID_BOTTOM - rows() * (SLOT + GAP) + GAP;
    }

    // 枠の所: 行ごとに中央へ寄せる (少ないときに左へ寄らない)
    private int[] slot(int i) {
        int n = items().size();
        int row = i / COLS;
        int inRow = Math.min(COLS, n - row * COLS);
        int left = (W - inRow * SLOT - (inRow - 1) * GAP) / 2;
        return new int[] {left + (i % COLS) * (SLOT + GAP), gridTop() + row * (SLOT + GAP)};
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, W, H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        PickStage.backdrop(g, fit, width, height);
        Component camp = reward.camp().isEmpty() ? Component.empty() : Component.translatable("label.skyabyss.facility.circuit_camp_" + reward.camp() + ".sub");
        PickStage.title(g, font, title, Component.translatable("gui.skyabyss.reward.sub", camp, reward.marks(), reward.coins()));
        // 箱の所: 題と札のあいだの真ん中。高さが足りなければ 2 倍 (足りれば 3 倍)
        int boxTop = 36;
        int room = gridTop() - 6 - boxTop;
        int chestK = room >= 56 ? 3 : 2;
        int chestY = boxTop + Math.max(0, (room - 16 * chestK) / 2);
        int chestCy = chestY + 8 * chestK;
        // 中央の箱: 次の品を溜めているあいだ揺れ、後ろで階級の色の紙の菱が育つ
        boolean going = shown < items().size();
        float shake = 0f;
        if (going) {
            GearTier next = tier(items().get(shown));
            float k = Math.min(1f, (t + partialTick) / suspense(next));
            shake = k * (next.atLeast(GearTier.HEROIC) ? 3.5f : next.atLeast(GearTier.EPIC) ? 2.2f : 1.2f);
            int max = next.atLeast(GearTier.HEROIC) ? 40 : next.atLeast(GearTier.EPIC) ? 32 : 24;
            int r = 2 * Math.round((8 + k * (max - 8)) / 2f);
            float cx = W / 2f;
            Paper.cut(g, "reward_burst", new float[] {cx, chestCy - r, cx + r, chestCy, cx, chestCy + r, cx - r, chestCy},
                    0xFF000000 | next.rgb(), Paper.BLACK_LO, 3, 3);
        }
        float sx = (float) Math.sin((t + partialTick) * 2.1) * shake;
        float sy = (float) Math.cos((t + partialTick) * 2.7) * shake * 0.5f;
        g.pose().pushPose();
        g.pose().translate(W / 2f - 8 * chestK + sx, chestY + sy, 0);
        g.pose().scale(chestK, chestK, 1f);
        g.renderItem(new ItemStack(Items.CHEST), 0, 0);
        g.pose().popPose();
        // 出た品の札
        hovered = ItemStack.EMPTY;
        for (int i = 0; i < items().size(); i++) {
            int[] s = slot(i);
            float ang = ((i * 5) % 7 - 3) * 1.3f;
            if (i >= shown) {
                // まだの札は伏せた灰の紙
                Paper.cut(g, "reward_slot" + i, Paper.rotRect(s[0] + SLOT / 2f, s[1] + SLOT / 2f, SLOT - 6, SLOT - 6, ang), Paper.GREY_LO, 0, 0, 0);
                continue;
            }
            ItemStack st = items().get(i);
            int rgb = 0xFF000000 | tier(st).rgb();
            boolean on = mx >= s[0] && mx < s[0] + SLOT && my >= s[1] && my < s[1] + SLOT;
            boolean fresh = i == flashSlot && flash > 0;
            int lift = fresh || on ? 2 : 0;
            Paper.cut(g, "reward_slot" + i, Paper.rotRect(s[0] + SLOT / 2f, s[1] + SLOT / 2f - lift, SLOT, SLOT, ang), fresh || on ? Paper.PINK : Paper.CREAM,
                    rgb, 2, 3 + lift);
            g.pose().pushPose();
            g.pose().translate(s[0] + 3, s[1] + 3 - lift, 0);
            g.pose().scale(1.5f, 1.5f, 1f);
            g.renderItem(st, 0, 0);
            g.renderItemDecorations(font, st, 0, 0);
            g.pose().popPose();
            if (on) {
                hovered = st;
            }
        }
        Component foot = items().isEmpty() ? Component.translatable("gui.skyabyss.reward.none")
                : going ? Component.translatable("gui.skyabyss.reward.count", shown, items().size()).copy().append("  ").append(Component.translatable("gui.skyabyss.reward.skip"))
                : Component.translatable("gui.skyabyss.reward.done");
        PickStage.hint(g, font, foot);
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f);
        return fit.screenRect(0, 0, W, H);
    }
}
