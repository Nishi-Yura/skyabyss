package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.JobChoice;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.net.JobChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// 島の野営地で職を選ぶ画面 (docs/design/43 の 4 節) と、街の職の間で職を替える画面 (switching。docs/design/48 の 3 章)。
// 切り絵の舞台 (docs/design/56 の「切り絵」)。見せるものは絞る: 職の名・木の判子・役目の一行・間合いと難しさ・最初に覚える技 (ほかの技は絵だけ)。
//   下 = 六枚の職の札を桃色の山に挿して並べる (武器の絵と名)。選んだ札は跳ねて持ち上がり、桃色になる
//   右 = 尖った窓の台紙に、始めの武器を大きく (見せ絵)、その前に自分の姿。マウスに合わせて紙の層が少しずれる (奥ほど動かない)
// 動き (2026-11-24 の声「ボタンやアニメーションにも凝ってほしい」): 開くと山がせり上がり、台紙が立ち、札が一枚ずつ配られる。
//   職を替えると、字が左から入り、判子が押され、武器が振り込まれる。決めの釦は一度目で「もう一度押して決める」になり、残り時間の帯が減っていく。
//   入りは減速 (ease out)、跳ねる所だけ少し行き過ぎて戻す。出は入りより短い。菱は漂う
// 島の画面には「あとで」を置かない: 職を選ぶまで島は進まず、街の職の間は渡し船の先なので、ほかに選ぶ所がない
// (2026-11-03 の声「最初に選ばなかったら、どこで選ぶの？ 後での項目いらなくない？」)。Esc では閉じられ、野営地にいれば REOPEN_MS のあとでまた開く (JobChoice.prompt)
// 中身は論理の 408x228 (1280x720・GUI 3 でも 1 倍で入る) に置き、窓に合わせて画素ちょうどで大きくする。黒い紙と山は窓の端まで
public class JobChoiceScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 島の画面を閉じてから、また開くまで (この間に Esc をもう一度押せばゲームのメニューが開く)
    static final long REOPEN_MS = 5000;
    private static long closedAt;
    static final int W = 408;
    static final int H = 228;
    // 左の字の欄
    private static final int LX = 18;
    // 札 (下に横並び)
    private static final int CARD_X = 14;
    private static final int CARD_Y = 180;
    private static final int CARD_W = 44;
    private static final int CARD_H = 50;
    private static final int CARD_STEP = 47;
    // 決めの釦
    private static final int CTA_X = 296;
    private static final int CTA_Y = 205;
    private static final int CTA_W = 104;
    private static final int CTA_H = 17;
    private static final int ARM_TICKS = 80;
    // 姿の台紙 (尖った窓の形)
    private static final float[] ARCH = {8, 176, 6, 62, 14, 32, 34, 8, 56, 0, 78, 8, 98, 32, 106, 62, 104, 176};
    private static final int ARCH_X = 262;
    private static final int ARCH_Y = 30;
    private static final int FIG_X0 = 268;
    private static final int FIG_X1 = 352;
    private static final int FIG_Y0 = 64;
    private static final int FIG_Y1 = 200;
    // 浮かぶ菱 {x, y, 大きさ, 桃色なら 1}
    private static final int[][] DIAMONDS = {{240, 30, 3, 1}, {388, 40, 5, 1}, {396, 84, 2, 0}, {250, 162, 2, 1}, {390, 140, 4, 0},
            {240, 154, 2, 0}, {372, 112, 2, 1}, {150, 20, 2, 0}, {206, 54, 3, 1}};
    // 職ごとの間合い (0 近く・1 中ほど・2 遠く) と難しさ (1..5)。並びは Job の並び。遊びの数値ではなく、選ぶ人への目安
    private static final int[][] STYLE = {
            {0, 1}, // 戦士: 壁。崩れにくく、始めやすい
            {0, 2}, // 鍛冶師: 近くで重く打つ
            {2, 3}, // 魔法使い: 遠くから強いが脆い
            {2, 3}, // 弓使い: 遠くから射抜き、罠で縛る
            {0, 4}, // 暗殺者: 速く強いが、当たると脆い
            {1, 3}}; // 呪術師: 仲間を支え、敵を崩す
    // 動きの長さ (ミリ秒)
    private static final int ENTER = 260;
    private static final int STAGGER = 45;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private Job picked;
    private long openedAt;
    private long pickedAt;
    // 札ごとの持ち上がり (跳ねる。0..1 を行き過ぎる)
    private final float[] lift = new float[6];
    private final float[] liftV = new float[6];
    private long lastFrame;
    private boolean armed;
    private int armedTicks;
    private boolean ctaDown;
    // 決めた時刻 (0 = まだ)。決めると札と字が退き、選んだ札だけが残ってから閉じる
    private long decidedAt;
    private static final int LEAVE_MS = 700;
    private int lastHover = -1;
    // 撮影の見本: 開いて少ししたら決めた動きだけ見せる (送らない・閉じない。ShotRunner の job_choice_decide)
    private boolean demoDecide;
    // 撮影の見本: この番の技に乗せたことにする (-1 = しない。ShotRunner の job_choice_hover)
    private int demoHover = -1;
    // 退く動きの字の透け (毎こま決める)
    private float exitMul = 1f;
    private final boolean switching;

    public JobChoiceScreen() {
        this(false);
    }

    public JobChoiceScreen(boolean switching) {
        super(Component.translatable(switching ? "gui.skyabyss.job_room.title" : "gui.skyabyss.job_choice.title"));
        this.switching = switching;
    }

    private Job current() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : Job.byId(jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player).curJob);
    }

    public JobChoiceScreen pick(Job job) {
        if (job != picked) {
            pickedAt = net.minecraft.Util.getMillis();
        }
        picked = job;
        armed = false;
        return this;
    }

    public JobChoiceScreen demoHover(int k) {
        demoHover = k;
        return this;
    }

    public JobChoiceScreen demoDecide() {
        demoDecide = true;
        return this;
    }

    private void layout() {
        fit.fit(width, height, W, H, 3f);
    }

    @Override
    protected void init() {
        layout();
        if (openedAt == 0) {
            openedAt = net.minecraft.Util.getMillis();
            if (pickedAt == 0) {
                pickedAt = openedAt;
            }
        }
        if (switching) {
            addRenderableWidget(new Paper.Tag(356, 8, 44, 14, Component.translatable("gui.skyabyss.job_room.later"), b -> onClose(), false));
        }
    }

    private boolean ctaEnabled() {
        return picked != null && !(switching && picked == current());
    }

    private void press() {
        if (!ctaEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!armed) {
            armed = true;
            armedTicks = ARM_TICKS;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.6f));
            return;
        }
        PacketDistributor.sendToServer(new JobChoosePayload(picked.id));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.5f));
        decidedAt = net.minecraft.Util.getMillis();
        armed = false;
        liftV[picked.ordinal()] += 0.2f;
    }

    private void choose(int i) {
        Job j = Job.values()[i];
        if (j == picked) {
            return;
        }
        pick(j);
        liftV[i] += 0.12f;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (armedTicks > 0 && --armedTicks == 0) {
            armed = false;
        }
        if (demoDecide && decidedAt == 0 && openedAt > 0 && net.minecraft.Util.getMillis() - openedAt > 900) {
            decidedAt = net.minecraft.Util.getMillis();
        }
        if (!demoDecide && decidedAt > 0 && net.minecraft.Util.getMillis() - decidedAt >= LEAVE_MS) {
            onClose();
        }
    }

    // ---------- 動きの道具 ----------
    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    // 減速して着く
    private static float out(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // 少し行き過ぎて戻る
    private static float back(float t) {
        t = clamp01(t);
        float c = 1.7f;
        float u = t - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    private float since(long from, long delay, int span) {
        return (net.minecraft.Util.getMillis() - from - delay) / (float) span;
    }

    // 開いてからの進み
    private float open(long delay, int span) {
        return since(openedAt, delay, span);
    }

    // 職を替えてからの進み (開いたときは開いた時刻から)
    private float swap(long delay, int span) {
        return since(Math.max(pickedAt, openedAt + 150), delay, span);
    }

    // 決めてからの進み (まだなら 0)
    private float leave(long delay, int span) {
        return decidedAt == 0 ? 0f : since(decidedAt, delay, span);
    }

    // 字の色に透けを掛ける (0 だと Font が不透明と見なすので下限を置く)。決めたあとは退く分も掛ける
    private int fade(int rgb, float a) {
        int al = Math.max(6, Math.round(255 * clamp01(a) * exitMul));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    // 札の跳ね (ばね)。選んだ札は 1、乗せた札は 0.25、ほかは 0 へ
    private void stepLift(int hover) {
        long now = net.minecraft.Util.getMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            float target = jobs[i] == picked ? 1f : i == hover ? 0.3f : 0f;
            // まだ選んでいないあいだは、札が左から順に小さく波打って誘う (2.4 秒ごと)
            if (picked == null && i != hover && open(0, 1) > 1200) {
                float ph = ((now - openedAt) / 1000f * 1.2f - i * 0.12f) % 2.4f;
                target = ph > 0f && ph < 0.4f ? (float) Math.sin(ph / 0.4f * Math.PI) * 0.22f : 0f;
            }
            float k = 260f;
            float d = 20f;
            liftV[i] += ((target - lift[i]) * k - liftV[i] * d) * dt;
            lift[i] += liftV[i] * dt;
        }
    }

    // ---------- 当たり ----------
    // 札の左上 {x, y} (配られる動き・持ち上がりを含む)
    private float[] card(int i) {
        float deal = back(open(160 + i * STAGGER, 320));
        float x = CARD_X + i * CARD_STEP;
        float y = CARD_Y + (1f - deal) * 60f - lift[i] * 14f;
        // 決めたら、ほかの札は順に山へ沈み、選んだ札は少し高く上がる
        if (decidedAt > 0) {
            boolean chosen = picked != null && picked.ordinal() == i;
            y += chosen ? -out(leave(0, 320)) * 12f : out(leave(Math.abs(i - (picked == null ? 0 : picked.ordinal())) * 40L, 260)) * 70f;
        }
        return new float[] {x, y};
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < Job.values().length; i++) {
            float[] c = card(i);
            if (mx >= c[0] && mx < c[0] + CARD_W && my >= c[1] && my < Math.max(c[1] + CARD_H, CARD_Y + CARD_H)) {
                return i;
            }
        }
        return -1;
    }

    private boolean overCta(double mx, double my) {
        return mx >= CTA_X && mx < CTA_X + CTA_W && my >= CTA_Y && my < CTA_Y + CTA_H;
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (decidedAt > 0) {
            return true;
        }
        if (button == 0 && overCta(mx, my)) {
            ctaDown = true;
            return true;
        }
        int i = cardAt(mx, my);
        if (i >= 0) {
            choose(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (ctaDown && button == 0) {
            ctaDown = false;
            if (overCta(mx, my)) {
                press();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Job[] jobs = Job.values();
        if (decidedAt > 0) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int i = picked == null ? 0 : (picked.ordinal() + (key == GLFW.GLFW_KEY_LEFT ? jobs.length - 1 : 1)) % jobs.length;
            choose(i);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            press();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (!switching) {
            closedAt = net.minecraft.Util.getMillis();
        }
    }

    // 島の画面をまた開いてよいか (閉じてから REOPEN_MS たった)
    static boolean mayReopen() {
        return net.minecraft.Util.getMillis() - closedAt >= REOPEN_MS;
    }

    // ---------- 描く ----------
    // 黒い紙は窓の端まで (論理の座標で、窓の四隅まで)
    private int[] sheetBox() {
        int x0 = (int) Math.floor(fit.x(0));
        int y0 = (int) Math.floor(fit.y(0));
        int x1 = (int) Math.ceil(fit.x(width));
        int y1 = (int) Math.ceil(fit.y(height));
        return new int[] {x0, y0, x1 - x0, y1 - y0};
    }

    private void renderBlack(GuiGraphics g) {
        int[] b = sheetBox();
        int sw = b[2];
        int sh = b[3];
        Paper.draw(g, "job_black", sw + "x" + sh, b[0], b[1], sw, sh, s -> s.poly(new float[] {
                7, 10, sw * 0.22f, 6, sw * 0.47f, 9, sw * 0.71f, 4, sw - 9, 8,
                sw - 5, sh * 0.30f, sw - 8, sh * 0.62f, sw - 4, sh - 12,
                sw * 0.64f, sh - 6, sw * 0.31f, sh - 9, 10, sh - 5,
                5, sh * 0.58f, 8, sh * 0.27f}, Paper.BLACK));
    }

    // 桃色の山 (二重。奥の山は暗く高く、手前の山は札を挿す)。開くとせり上がる
    private void renderHills(GuiGraphics g, float par, boolean front) {
        int[] b = sheetBox();
        int sw = b[2];
        int sh = b[3];
        int ox = -b[0];
        int oy = -b[1];
        float rise = (1f - out(open(front ? 60 : 0, 380))) * (front ? 40 : 30) + out(leave(front ? 260 : 300, 300)) * 24f;
        g.pose().pushPose();
        g.pose().translate(par * (front ? 1.6f : 0.8f), rise, 0);
        Paper.draw(g, front ? "job_hill_f" : "job_hill_b", ox + "," + oy + "," + sw + "x" + sh, b[0], b[1], sw, sh, s -> {
            float base = oy + 228;
            float[][] peaks = front
                    ? new float[][] {{-20, -8}, {ox + 30, -22}, {ox + 80, -14}, {ox + 132, -26}, {ox + 186, -12}, {ox + 238, -24}, {ox + 284, -10},
                            {ox + 330, -18}, {ox + 380, -6}, {ox + 430, -16}, {sw + 20, -10}}
                    : new float[][] {{-20, -20}, {ox + 50, -44}, {ox + 110, -30}, {ox + 170, -52}, {ox + 226, -36}, {ox + 262, -58},
                            {ox + 316, -70}, {ox + 362, -46}, {ox + 400, -60}, {ox + 440, -34}, {sw + 20, -40}};
            float[] hill = new float[peaks.length * 2 + 4];
            for (int i = 0; i < peaks.length; i++) {
                hill[i * 2] = peaks[i][0];
                hill[i * 2 + 1] = base + peaks[i][1];
            }
            hill[peaks.length * 2] = sw + 20;
            hill[peaks.length * 2 + 1] = sh + 60;
            hill[peaks.length * 2 + 2] = -20;
            hill[peaks.length * 2 + 3] = sh + 60;
            s.poly(hill, front ? Paper.PINK_LO : 0xFF3A2236);
            if (front) {
                for (int i = 0; i + 1 < peaks.length; i++) {
                    float ax = peaks[i][0];
                    float ay = base + peaks[i][1];
                    float bx = peaks[i + 1][0];
                    float by = base + peaks[i + 1][1];
                    int n = (int) Math.max(1, Math.abs(bx - ax));
                    for (int k = 0; k <= n; k++) {
                        s.px(Math.round(ax + (bx - ax) * k / n), Math.round(ay + (by - ay) * k / n), Paper.PINK);
                    }
                }
            }
        });
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        layout();
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        int hover = decidedAt > 0 ? -1 : cardAt(mouseX, mouseY);
        // 札に乗せ替えたら小さく鳴らす (手ざわり)
        if (hover != lastHover && hover >= 0) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.12f));
        }
        lastHover = hover;
        stepLift(hover);
        // 決めたら字は入りより速く退く
        exitMul = 1f - out(leave(0, 180));
        // マウスに合わせた紙の層のずれ (-1..1)。画素の単位にそろえる
        float unit = fit.pixelUnit();
        float px = Mth.clamp((mouseX - W / 2f) / (W / 2f), -1f, 1f);
        float py = Mth.clamp((mouseY - H / 2f) / (H / 2f), -1f, 1f);
        float parX = Math.round(px * 2f / unit) * unit;
        float parY = Math.round(py * 1.5f / unit) * unit;
        long now = net.minecraft.Util.getMillis();

        fit.begin(g);
        renderBlack(g);
        // 漂う菱
        for (int k = 0; k < DIAMONDS.length; k++) {
            int[] d = DIAMONDS[k];
            float bob = Math.round((float) Math.sin(now / 900.0 + k * 1.7) * 1.5f / unit) * unit;
            float a = out(open(200 + k * 30L, 400));
            if (a <= 0f) {
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(-parX * 0.5f, bob - parY * 0.5f + (1f - a) * 6f, 0);
            boolean pink = d[3] == 1;
            Paper.draw(g, "job_dm_" + d[2] + (pink ? "p" : "g"), "", d[0] - d[2] - 1, d[1] - d[2] - 1, d[2] * 2 + 3, d[2] * 2 + 3,
                    s -> s.diamond(d[2] + 1, d[2] + 1, d[2], pink ? Paper.PINK : Paper.GREY, pink ? Paper.PINK_HI : 0xFFD8D2CA));
            g.pose().popPose();
        }
        renderHills(g, parX, false);
        // 台紙が立つ (下から起き上がる)
        float stand = back(open(80, 360));
        g.pose().pushPose();
        g.pose().translate(-parX * 0.6f, (1f - stand) * 30f - parY * 0.6f, 0);
        Paper.draw(g, "job_arch3", "", ARCH_X - 2, ARCH_Y - 2, 116, 186, s -> s.layer(shift(ARCH, 2, 2), Paper.CREAM, Paper.PINK, 5, 4));
        // 見せ絵: 始めの武器を大きく (台紙からはみ出す)。職を替えると右から振り込まれる
        if (picked != null && swap(0, 340) > 0f) {
            float e = back(swap(0, 340));
            g.pose().pushPose();
            g.pose().translate(300 + (1f - e) * 34f - parX * 0.4f, 104 - (1f - e) * 6f, -140);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((1f - e) * 18f));
            g.pose().scale(8f, 8f, 1f);
            g.renderItem(JobChoice.starter(picked), -8, -8);
            g.pose().popPose();
        }
        g.pose().popPose();
        fit.end(g);
        // 自分の姿 (台紙の前。窓の座標で描く。台紙と一緒に立つ)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && stand > 0.6f) {
            int[] r = fit.screenRect(FIG_X0, FIG_Y0, FIG_X1 - FIG_X0, FIG_Y1 - FIG_Y0);
            int lift0 = Math.round((1f - stand) * 30f * fit.scale());
            int size = Math.round(56 * fit.scale());
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, r[0], r[1] + lift0, r[0] + r[2], r[1] + r[3] + lift0, size, 0.0625f, screenX, screenY, mc.player);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        fit.begin(g);
        if (picked != null && swap(120, 300) > 0f) {
            // 窓の肩に木の印 (判子のように押される)
            float st = back(swap(120, 300));
            float sc = 1f + (1f - st) * 0.6f;
            g.pose().pushPose();
            g.pose().translate(350 - parX * 0.6f, 38 + (1f - stand) * 30f - parY * 0.6f, 0);
            g.pose().scale(sc, sc, 1f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((1f - st) * -20f));
            Paper.draw(g, "job_seal", "", -15, -15, 30, 30, s -> {
                s.diamond(15, 15, 14, Paper.BLACK_LO, 0);
                s.diamond(14, 14, 14, Paper.PINK, Paper.PINK_HI);
                s.diamond(14, 14, 11, Paper.CREAM, 0);
            });
            SkillHud.drawIcon(g, picked.tree.iconIndex(), -9, -9, 16);
            g.pose().popPose();
        }
        renderHills(g, parX, true);
        renderCards(g, hover);
        renderCta(g, mouseX, mouseY, partialTick);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        if (picked == null) {
            renderLead(g);
        } else {
            renderInfo(g, mouseX, mouseY);
        }
        fit.end(g);
        g.pose().popPose();
    }

    // 札: 生成りの紙に武器の絵 (2 倍) と職の名。選んだ札は桃色、いまの職には菱を留める
    private void renderCards(GuiGraphics g, int hover) {
        Job cur = switching ? current() : null;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            boolean on = j == picked;
            float[] c = card(i);
            int body = on ? Paper.PINK : i == hover ? Paper.CREAM_HI : Paper.CREAM;
            float tilt = (i % 2 == 0 ? -2.5f : 2f) * (1f - Mth.clamp(lift[i], 0f, 1f));
            g.pose().pushPose();
            g.pose().translate(c[0] + CARD_W / 2f, c[1] + CARD_H / 2f, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(tilt));
            int sh = on ? 4 : 2;
            Paper.draw(g, "job_card2_" + (on ? "on" : i == hover ? "hi" : "lo"), "", -CARD_W / 2 - 1, -CARD_H / 2 - 1, CARD_W + 7, CARD_H + 7, s -> {
                float[] p = Paper.quad(1, 1, CARD_W, CARD_H, 0, 0, 0, 0);
                s.poly(p, on ? Paper.PINK_LO : Paper.BLACK_LO, sh, sh);
                s.poly(p, body);
                // 札の上の切り込み (山の形の飾り)
                s.poly(new float[] {CARD_W / 2f - 3, 1, CARD_W / 2f + 4, 1, CARD_W / 2f + 0.5f, 5}, on ? Paper.PINK_LO : Paper.CREAM_LO);
            });
            g.pose().pushPose();
            g.pose().translate(0, -6, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(JobChoice.starter(j), -8, -8);
            g.pose().popPose();
            Component name = Component.translatable(j.nameKey());
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.drawString(font, name, -font.width(name) / 2, CARD_H / 2 - 13, on ? Paper.ON_BLACK_HI : Paper.INK, false);
            if (j == cur) {
                Paper.draw(g, "job_pin2", "", CARD_W / 2 - 7, -CARD_H / 2 - 4, 9, 9, s -> s.diamond(4, 4, 4, Paper.PINK_HI, 0xFFFFFFFF));
            }
            g.pose().popPose();
            g.pose().popPose();
        }
    }

    // 決めの釦: 乗せると浮き、押すと沈む。一度目で「もう一度押して決める」になり、残り時間の帯が減る
    private void renderCta(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean enabled = ctaEnabled();
        boolean over = overCta(mouseX, mouseY);
        boolean decided = decidedAt > 0;
        int state = decided ? Paper.PRESS : !enabled ? Paper.FLAT : ctaDown && over ? Paper.PRESS : over || armed ? Paper.HOVER : Paper.REST;
        float in = out(open(420, 300));
        String key = decided ? "gui.skyabyss.job_choice.decided"
                : switching && picked != null && picked == current() ? "gui.skyabyss.job_room.same"
                : armed ? (switching ? "gui.skyabyss.job_room.confirm" : "gui.skyabyss.job_choice.confirm")
                : switching ? "gui.skyabyss.job_room.switch_btn" : "gui.skyabyss.job_choice.start";
        Component label = Component.translatable(key);
        g.pose().pushPose();
        // 入りは右から、決めたあとは押したまま少し待って右へ退く
        g.pose().translate((1f - in) * 30f + out(leave(320, 240)) * 40f, 0, 0);
        Paper.button(g, font, CTA_X, CTA_Y, CTA_W, CTA_H, label, true, state);
        if (armed) {
            float left = (armedTicks - partialTick) / ARM_TICKS;
            int bw = Math.round((CTA_W - 10) * clamp01(left));
            int dy = state == Paper.PRESS ? 1 : -1;
            g.fill(CTA_X + 6, CTA_Y + CTA_H - 3 + dy, CTA_X + 6 + bw, CTA_Y + CTA_H - 2 + dy, Paper.CREAM);
        }
        g.pose().popPose();
    }

    // 職を選ぶ前: 導きの一行と、札を指す手がかり
    private void renderLead(GuiGraphics g) {
        float a = out(open(250, ENTER));
        int dx = Math.round((1f - a) * -10f);
        g.drawString(font, title, LX + dx, 16, fade(Paper.ON_BLACK_PINK, a), false);
        Component lead = Component.translatable(switching ? "gui.skyabyss.job_room.lead" : "gui.skyabyss.job_choice.lead");
        int y = 34;
        for (net.minecraft.util.FormattedCharSequence line : TextWrap.split(font, lead, 200)) {
            g.drawString(font, line, LX + dx, y, fade(Paper.ON_BLACK, a), false);
            y += 12;
        }
        g.drawString(font, Component.translatable("gui.skyabyss.job_choice.pick_short"), LX + dx, y + 6, fade(Paper.ON_BLACK_DIM, a), false);
    }

    private void renderInfo(GuiGraphics g, int mouseX, int mouseY) {
        int[] style = STYLE[Math.min(picked.ordinal(), STYLE.length - 1)];
        // 字は左から入る (段ごとに少し遅れて)
        float a0 = out(swap(0, ENTER));
        float a1 = out(swap(STAGGER, ENTER));
        float a2 = out(swap(STAGGER * 2L, ENTER));
        float a3 = out(swap(STAGGER * 3L, ENTER));
        // 小さな題と番号
        Component no = Component.literal(String.format("%02d / %02d", picked.ordinal() + 1, Job.values().length));
        g.drawString(font, title, LX, 14, fade(Paper.ON_BLACK_PINK, 1f), false);
        g.drawString(font, no, LX + font.width(title) + 8, 14, fade(Paper.ON_BLACK_DIM, 1f), false);
        // 大きな職の名 (3 倍)
        Component name = Component.translatable(picked.nameKey());
        g.pose().pushPose();
        g.pose().translate(LX + (1f - a0) * -14f, 30, 0);
        g.pose().scale(3f, 3f, 1f);
        g.drawString(font, name, 0, 0, fade(Paper.ON_BLACK_HI, a0), false);
        g.pose().popPose();
        // 木の判子 (押される)
        Component tree = Component.translatable(picked.tree.nameKey());
        float st = back(swap(90, 280));
        int tw = font.width(tree) + 12;
        float sx = LX + font.width(name) * 3 + 10 + tw / 2f;
        g.pose().pushPose();
        g.pose().translate(sx, 44, 0);
        float sc = 1f + (1f - st) * 0.5f;
        g.pose().scale(sc, sc, 1f);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4f + (1f - st) * -10f));
        if (st > 0f && exitMul > 0.3f) {
            Paper.cut(g, "job_stamp2_" + tw, Paper.quad(-tw / 2f, -7, tw, 14, 1, -1, 1, -1), Paper.PINK, Paper.PINK_LO, 2, 2);
            g.drawString(font, tree, -tw / 2 + 6, -4, Paper.ON_BLACK_HI, false);
        }
        g.pose().popPose();
        // 役目の一行
        g.drawString(font, Component.translatable(picked.nameKey() + ".role"), LX + Math.round((1f - a1) * -10f), 64, fade(Paper.ON_BLACK, a1), false);
        // 間合いと難しさ
        int y = 84;
        int dx2 = Math.round((1f - a2) * -10f);
        Component rl = Component.translatable("gui.skyabyss.job_choice.range");
        g.drawString(font, rl, LX + dx2, y, fade(Paper.ON_BLACK_SOFT, a2), false);
        int tx = LX + dx2 + 40;
        // 近・中・遠の三つの点を線で結び、その職の所に桃色の菱
        g.fill(tx + 3, y + 3, tx + 3 + 56, y + 4, fade(Paper.GREY_LO, a2));
        String[] marks = {"near", "mid", "far"};
        for (int k = 0; k < 3; k++) {
            int mx = tx + 3 + k * 28;
            boolean on = k == style[0];
            if (on && a2 * exitMul > 0.3f) {
                int ox = mx;
                Paper.draw(g, "job_rmark", "", ox - 4, y - 1, 9, 9, s -> s.diamond(4, 4, 4, Paper.PINK, Paper.PINK_HI));
            } else {
                g.fill(mx - 1, y + 2, mx + 2, y + 5, fade(Paper.GREY, a2));
            }
        }
        Component rv = Component.translatable("gui.skyabyss.job_choice.range." + marks[style[0]]);
        g.drawString(font, rv, tx + 70, y, fade(Paper.ON_BLACK_PINK, a2), false);
        // 難しさ (間合いと同じ行の右)
        int dfx = LX + dx2 + 150;
        Component dl = Component.translatable("gui.skyabyss.job_choice.rate.difficulty");
        g.drawString(font, dl, dfx, y, fade(Paper.ON_BLACK_SOFT, a2), false);
        for (int k = 0; k < 5; k++) {
            boolean on = k < style[1];
            int cx = dfx + font.width(dl) + 9 + k * 10;
            String key = on ? "job_dfon" : "job_dfoff";
            if (a2 * exitMul < 0.3f) {
                continue;
            }
            Paper.draw(g, key, "", cx - 3, y + 1, 7, 7, s -> s.diamond(3, 3, 3, on ? Paper.PINK : Paper.EDGE, on ? Paper.PINK_HI : 0));
        }
        // 使える技 (全部。3 列。最初に覚える技は桃色の台。乗せると特徴)
        int dx3 = Math.round((1f - a3) * -10f);
        List<Skill> actives = new ArrayList<>();
        for (Skill s : Skill.inTree(picked.tree)) {
            if (s.active && !s.keystone()) {
                actives.add(s);
            }
        }
        actives.sort(java.util.Comparator.comparingInt((Skill s) -> s.tier).thenComparingInt(s -> s.column));
        Skill first = JobChoice.firstSkill(picked);
        Skill tip = null;
        if (!actives.isEmpty()) {
            y = 104;
            Component head = Component.translatable("gui.skyabyss.job_choice.skills_head");
            g.drawString(font, head, LX + dx3, y, fade(Paper.ON_BLACK_PINK, a3), false);
            if (first != null) {
                g.drawString(font, Component.translatable("gui.skyabyss.job_choice.first_hint"), LX + dx3 + font.width(head) + 8, y, fade(Paper.ON_BLACK_DIM, a3), false);
            }
            y += 13;
            for (int k = 0; k < actives.size(); k++) {
                Skill s = actives.get(k);
                float pop = back(swap(STAGGER * 3L + k * 35L, 260));
                int x = LX + k * 30;
                boolean hot = k == demoHover || (mouseX >= x && mouseX < x + 26 && mouseY >= y - 2 && mouseY < y + 26);
                int yy = y + Math.round((1f - pop) * 8f) - (hot ? 2 : 0);
                boolean isFirst = s == first;
                if (pop * exitMul > 0.3f) {
                    int under = hot ? Paper.PINK : Paper.BLACK_LO;
                    Paper.draw(g, "job_skt_" + (hot ? "h" : "n"), "", x - 1, yy - 1, 32, 32, p -> p.layer(Paper.quad(1, 1, 26, 26, 0, 0, 0, 0), Paper.CREAM, under, 2, 2));
                    SkillHud.drawIcon(g, s.iconIndex(), x + 1, yy + 1, 24);
                    if (isFirst) {
                        Paper.draw(g, "job_skpin", "", x + 20, yy - 4, 9, 9, p -> p.diamond(4, 4, 4, Paper.PINK, Paper.PINK_HI));
                    }
                }
                if (hot) {
                    tip = s;
                }
            }
        }
        // 右上: 替えられること (島) と、使った点 (職の間)
        Component foot;
        if (switching) {
            Minecraft mc = Minecraft.getInstance();
            int spent = mc.player == null ? 0 : jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player).nodeSpent(picked);
            foot = Component.translatable("gui.skyabyss.job_room.points_short", spent);
            g.drawString(font, foot, 350 - font.width(foot), 11, fade(Paper.ON_BLACK_SOFT, 1f), false);
        } else {
            foot = Component.translatable("gui.skyabyss.job_choice.switch_short");
            g.drawString(font, foot, 400 - font.width(foot), 11, fade(Paper.ON_BLACK_DIM, 1f), false);
        }
        // 乗せた技の詳細: 名・特徴・マナとクールダウン・効き目 (折り返す)
        if (tip != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            MutableComponent head = Component.translatable(tip.nameKey()).withColor(Paper.ON_BLACK_PINK);
            if (tip == first) {
                head.append(Component.translatable("gui.skyabyss.job_choice.first_tag").withColor(Paper.ON_BLACK_SOFT));
            }
            lines.add(head.getVisualOrderText());
            if (I18n.exists(tip.typeKey())) {
                lines.add(Component.translatable(tip.typeKey()).withColor(Paper.ON_BLACK_SOFT).getVisualOrderText());
            }
            int cd = jp.revati.skyabyss.rpg.skill.SkillRules.cooldownTicks(tip, 1);
            String sec = String.format(java.util.Locale.ROOT, cd % 20 == 0 ? "%.0f" : "%.1f", cd / 20f);
            lines.add(Component.translatable("gui.skyabyss.job_choice.cost", jp.revati.skyabyss.rpg.skill.SkillRules.manaCost(tip, 1), sec)
                    .withColor(Paper.ON_BLACK_SOFT).getVisualOrderText());
            lines.addAll(TextWrap.split(font, Component.translatable(tip.effectKey(), jp.revati.skyabyss.rpg.skill.SkillRules.describeArgs(tip, 1))
                    .withColor(Paper.ON_BLACK), 190));
            if (demoHover >= 0) {
                g.renderTooltip(font, lines, LX + demoHover * 30 + 20, y + 20);
            } else {
                g.renderTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private static float[] shift(float[] p, float dx, float dy) {
        float[] o = p.clone();
        for (int i = 0; i < o.length; i += 2) {
            o[i] += dx;
            o[i + 1] += dy;
        }
        return o;
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, W, H);
    }
}
