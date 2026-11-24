package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.Plate;
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
    private static final int ARM_TICKS = 80;
    // 職ごとの間合い (0 近く・1 中ほど・2 遠く) と難しさ (1..5)。並びは Job の並び。遊びの数値ではなく、選ぶ人への目安
    private static final int[][] STYLE = {
            {0, 1}, // 戦士: 壁。崩れにくく、始めやすい
            {0, 2}, // 鍛冶師: 近くで重く打つ
            {2, 3}, // 魔法使い: 遠くから強いが脆い
            {2, 3}, // 弓使い: 遠くから射抜き、罠で縛る
            {0, 4}, // 暗殺者: 速く強いが、当たると脆い
            {1, 3}}; // 呪術師: 仲間を支え、敵を崩す
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
    // 一つ前に選んでいた職 (見せ絵を拭って替えるあいだ、下に残す)
    private Job prevPicked;

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
            prevPicked = picked;
            pickedAt = clock();
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
            openedAt = clock();
            if (pickedAt == 0) {
                pickedAt = openedAt;
            }
        }
        if (switching) {
            addRenderableWidget(new Plate.Btn(PX + PW - 7 - 44, PY + 5, 44, 13, Component.translatable("gui.skyabyss.job_room.later"), b -> onClose(), false));
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
        decidedAt = clock();
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
        // 板の絵柄では、職ごとに高さの違う小さな澄んだ音
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f + i * 0.09f, 0.55f));
    }

    @Override
    public void tick() {
        super.tick();
        if (armedTicks > 0 && --armedTicks == 0) {
            armed = false;
        }
        if (demoDecide && decidedAt == 0 && openedAt > 0 && clock() - openedAt > 900) {
            decidedAt = clock();
        }
        if (!demoDecide && decidedAt > 0 && clock() - decidedAt >= LEAVE_MS) {
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

    private float since(long from, long delay, int span) {
        return (clock() - from - delay) / (float) span;
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
        long now = clock();
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
        return new float[] {IX + i * JT_STEP, JT_Y};
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < Job.values().length; i++) {
            float[] c = card(i);
            if (mx >= c[0] && mx < c[0] + JT_W && my >= c[1] && my < c[1] + JT_H) {
                return i;
            }
        }
        return -1;
    }

    private boolean overCta(double mx, double my) {
        return mx >= SC_X && mx < SC_X + SC_W && my >= PCTA_Y && my < PCTA_Y + PCTA_H;
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
            closedAt = clock();
        }
    }

    // 島の画面をまた開いてよいか (閉じてから REOPEN_MS たった)
    static boolean mayReopen() {
        return clock() - closedAt >= REOPEN_MS;
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        layout();
        renderPlate(g, screenX, screenY, partialTick);
    }

    // ================= 画素の板の絵柄 (部品は client/ui/Plate) =================
    // 真ん中に板を一枚 (後ろの景色はぼかして見せる)。上 = 見出し、左 = 職の名・役目・目盛り・技の升、左下 = 六つの職の升、
    // 右 = 見せ絵の窓 (止まった月と自分の姿と始めの武器)、その下に決めの釦。
    // 動き: 板が浮かび上がる → 見出しの線を光が渡る → 窓が上下に開く → 中身が左から順に出る。職を替えると窓の絵が左から拭われて替わり、
    //   字が入り直す。決めると縁を光が一周して、板が消える
    private static final int PX = 14;
    private static final int PY = 12;
    private static final int PW = 380;
    private static final int PH = 204;
    private static final int HEAD = 22;
    private static final int IX = 22;
    private static final int JT_Y = 160;
    private static final int JT_W = 38;
    private static final int JT_H = 48;
    private static final int JT_STEP = 40;
    private static final int SC_X = 266;
    private static final int SC_Y = 37;
    private static final int SC_W = 120;
    private static final int SC_H = 147;
    private static final int PCTA_Y = 188;
    private static final int PCTA_H = 21;
    // またたく星 (見せ絵の中の場所。Plate.showcase の星の並びから)
    private static final int[][] TWINKLE = {{62, 9}, {104, 12}, {96, 38}, {112, 55}};
    // 板の全体の濃さ (開く・閉じる)
    private float base = 1f;

    // 動きの時計。slow を 2 以上にすると、その分だけ動きがゆっくりになる (撮影で動きの途中を撮るため。台本の uislow)
    public static int slow = 1;

    private static long clock() {
        return net.minecraft.Util.getMillis() / Math.max(1, slow);
    }

    // これから描く絵・字・塗りの濃さ (板の濃さを掛ける)
    private void tex(GuiGraphics g, float a) {
        Plate.fade(g, base * clamp01(a));
    }

    // ずらす量を画面の画素ちょうどに
    private float snap(float v) {
        float u = fit.pixelUnit();
        return Math.round(v * u) / u;
    }

    // 窓の座標で切り抜く (変換を掛けずに渡す)
    private static void clip(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.pose().pushPose();
        g.pose().setIdentity();
        g.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
        g.pose().popPose();
    }

    private static int ink(int rgb, float a) {
        return (Math.max(6, Math.round(255 * clamp01(a))) << 24) | (rgb & 0xFFFFFF);
    }

    private void renderPlate(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        int hover = decidedAt > 0 ? -1 : cardAt(mouseX, mouseY);
        if (hover != lastHover && hover >= 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.12f));
        }
        lastHover = hover;
        stepLift(hover);
        exitMul = 1f - out(leave(0, 180));
        Plate.Pal p = Plate.pal();
        Plate.Accent ac = Plate.job(picked == null ? -1 : picked.ordinal());
        float gone = out(leave(LEAVE_MS - 240, 220));
        base = out(open(0, 160)) * (1f - gone);
        float rise = snap((1f - out(open(0, 220))) * 6f - gone * 4f);
        Plate.backdrop(g, width, height, base);
        boolean bleed = false;

        fit.begin(g);
        g.pose().pushPose();
        g.pose().translate(0, rise, 0);
        tex(g, 1f);
        Plate.panel(g, PX, PY, PW, PH, HEAD, ac);
        renderPlateHead(g, p, ac);
        if (!bleed) {
            Plate.well(g, SC_X - 1, SC_Y - 1, SC_W + 2, SC_H + 2);
        }
        // 見せ絵: 窓が上下に開く。職を替えると左から拭って替わる
        float shut = out(open(120, 260));
        int[] sr = fit.screenRect(SC_X, SC_Y, SC_W, SC_H);
        int ry = Math.round(rise * fit.scale());
        int half = Math.round(sr[3] * shut / 2f);
        int midY = sr[1] + ry + sr[3] / 2;
        clip(g, sr[0], midY - half, sr[0] + sr[2], midY + half);
        float wipe = out(swap(0, 220));
        if (prevPicked != picked && wipe < 1f && pickedAt > openedAt) {
            Plate.showcase(g, SC_X, SC_Y, SC_W, SC_H, Plate.job(prevPicked == null ? -1 : prevPicked.ordinal()), bleed);
            int front = Math.round(sr[2] * wipe);
            clip(g, sr[0], sr[1] + ry, sr[0] + front, sr[1] + ry + sr[3]);
            Plate.showcase(g, SC_X, SC_Y, SC_W, SC_H, ac, bleed);
            g.disableScissor();
            int fx = SC_X + Math.round(SC_W * wipe);
            g.fill(fx, SC_Y, fx + 1, SC_Y + SC_H, ac.hi());
        } else {
            Plate.showcase(g, SC_X, SC_Y, SC_W, SC_H, ac, bleed);
        }
        // 星のまたたき (四つの星が、ずれた拍で十字に光る)
        for (int k = 0; k < TWINKLE.length; k++) {
            float tw = (float) Math.sin(clock() / 520.0 + k * 1.9);
            if (tw > 0.72f) {
                int sx = SC_X + TWINKLE[k][0];
                int sy = SC_Y + TWINKLE[k][1];
                int c = (Math.round(200 * (tw - 0.72f) / 0.28f) << 24) | 0xFFFFFF;
                g.fill(sx - 1, sy, sx + 2, sy + 1, c);
                g.fill(sx, sy - 1, sx + 1, sy + 2, c);
            }
        }
        // 始めの武器を大きく (姿の後ろ)。職を替えると右から入る
        if (picked != null && swap(40, 300) > 0f && base > 0.5f) {
            float e = out(swap(40, 300));
            Plate.fadeEnd(g);
            g.pose().pushPose();
            g.pose().translate(SC_X + 84 + snap((1f - e) * 18f), SC_Y + 50, -140);
            // 32 画素の絵を 4 倍 (絵の 1 画素 = 字の 2 画素)。月の絵・升の絵と画素の刻みがそろう
            g.pose().scale(4f, 4f, 1f);
            g.renderItem(JobChoice.starter(picked), -8, -8);
            g.pose().popPose();
            tex(g, 1f);
        }
        g.disableScissor();
        Plate.fadeEnd(g);
        g.pose().popPose();
        fit.end(g);
        // 自分の姿 (窓の中。窓の座標で描く)
        if (mc.player != null && shut > 0.35f && base > 0.5f) {
            clip(g, sr[0], midY - half, sr[0] + sr[2], midY + half);
            int[] r = fit.screenRect(SC_X + 26, SC_Y + 22, SC_W - 36, SC_H - 30);
            int size = Math.round(50 * fit.scale());
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, r[0], r[1] + ry, r[0] + r[2], r[1] + r[3] + ry, size, 0.0625f, screenX, screenY, mc.player);
            g.disableScissor();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        fit.begin(g);
        g.pose().translate(0, rise, 0);
        tex(g, 1f);
        renderJobTiles(g, hover, p);
        renderPlateCta(g, mouseX, mouseY, partialTick, ac);
        tex(g, 1f);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        // 開いたとき: 見出しの下の線を光が渡る
        float sweep = open(160, 460);
        if (sweep > 0f && sweep < 1f) {
            int lx = PX + 5 + Math.round(out(sweep) * (PW - 10));
            for (int i = 0; i < 34; i++) {
                int x = lx - i;
                if (x >= PX + 5 && x < PX + PW - 5) {
                    int c = (Math.round(255 * (1f - i / 34f)) << 24) | (i < 6 ? 0xFFFFFF : 0xFFF2D0);
                    g.fill(x, PY + HEAD - 2, x + 1, PY + HEAD - (i < 12 ? 0 : 1), c);
                }
            }
        }
        // 決めたとき: 縁を光が一周する
        float run = leave(0, 480);
        if (run > 0f && run < 1f) {
            Plate.runLight(g, PX, PY, PW, PH, bleed ? 1 : 2, run, 54, ac.hi() & 0xFFFFFF);
        }
        if (picked == null) {
            renderPlateLead(g, p);
        } else {
            renderPlateInfo(g, mouseX, mouseY, p, ac);
        }
        Plate.fadeEnd(g);
        fit.end(g);
        g.pose().popPose();
    }

    // 見出し: 鐘の印・題・番号、右に替えられること (島) か使った点 (職の間)
    private void renderPlateHead(GuiGraphics g, Plate.Pal p, Plate.Accent ac) {
        int y = PY + 8;
        int headRgb = p.inkHead;
        Plate.bell(g, IX, y - 1, 0xFF000000 | headRgb);
        g.drawString(font, title, IX + 13, y, ink(headRgb, 1f), false);
        if (picked != null) {
            Component no = Component.literal(String.format("%02d / %02d", picked.ordinal() + 1, Job.values().length));
            g.drawString(font, no, IX + 13 + font.width(title) + 8, y, ink(p.dim, 1f), false);
        }
        if (switching) {
            if (picked != null) {
                Minecraft mc = Minecraft.getInstance();
                int spent = mc.player == null ? 0 : jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player).nodeSpent(picked);
                Component foot = Component.translatable("gui.skyabyss.job_room.points_short", spent);
                g.drawString(font, foot, PX + PW - 7 - 44 - 6 - font.width(foot), y, ink(p.inkSoft, 1f), false);
            }
        } else {
            Component foot = Component.translatable("gui.skyabyss.job_choice.switch_short");
            g.drawString(font, foot, PX + PW - 9 - font.width(foot), y, ink(p.dim, 1f), false);
        }
    }

    // 六つの職の升: 始めの武器 (2 倍) と名。選んだ升は職の色に灯って 2 画素浮く。いまの職には金の菱
    private void renderJobTiles(GuiGraphics g, int hover, Plate.Pal p) {
        Job cur = switching ? current() : null;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            boolean on = j == picked;
            float in = out(open(140 + i * 30L, 200));
            float away = decidedAt > 0 && !on ? out(leave(Math.abs(i - (picked == null ? 0 : picked.ordinal())) * 25L, 160)) : 0f;
            float a = in * (1f - away);
            if (a <= 0.01f) {
                continue;
            }
            // 選ぶ前は、升が左から順に灯って誘う (stepLift の波)
            int state = on ? Plate.ON : i == hover || (picked == null && lift[i] > 0.08f) ? Plate.HOVER : Plate.REST;
            Plate.Accent jc = Plate.job(i);
            int x = IX + i * JT_STEP;
            float dy = snap((1f - in) * 5f + away * 3f) - Math.round(Mth.clamp(lift[i], 0f, 1.15f) * 2f);
            g.pose().pushPose();
            g.pose().translate(0, dy, 0);
            tex(g, a);
            Plate.tile(g, x, JT_Y, JT_W, JT_H, state, jc);
            if (a > 0.5f && base > 0.5f) {
                Plate.fadeEnd(g);
                g.pose().pushPose();
                g.pose().translate(x + 3, JT_Y + 2, 0);
                g.pose().scale(2f, 2f, 1f);
                g.renderItem(JobChoice.starter(j), 0, 0);
                g.pose().popPose();
                tex(g, a);
            }
            Component name = Component.translatable(j.nameKey());
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.drawString(font, name, x + (JT_W - font.width(name) + 1) / 2, JT_Y + 35, ink(Plate.tileInk(state, jc), a), false);
            if (j == cur) {
                Plate.gem(g, x + JT_W - 5, JT_Y + 5, 2, 0xFFF2C47C);
            }
            g.pose().popPose();
            g.pose().popPose();
        }
        tex(g, 1f);
    }

    // 決めの釦: 乗せると明るく、押すと沈む。一度目で「もう一度押して決める」になり、残り時間の帯が減る。待っているあいだは光が渡る
    private void renderPlateCta(GuiGraphics g, int mouseX, int mouseY, float partialTick, Plate.Accent ac) {
        boolean enabled = ctaEnabled();
        boolean over = overCta(mouseX, mouseY);
        boolean decided = decidedAt > 0;
        int state = decided ? Plate.PRESS : !enabled ? Plate.FLAT : ctaDown && over ? Plate.PRESS : over || armed ? Plate.HOVER : Plate.REST;
        float in = out(open(300, 200));
        String key = decided ? "gui.skyabyss.job_choice.decided"
                : switching && picked != null && picked == current() ? "gui.skyabyss.job_room.same"
                : armed ? (switching ? "gui.skyabyss.job_room.confirm" : "gui.skyabyss.job_choice.confirm")
                : switching ? "gui.skyabyss.job_room.switch_btn" : "gui.skyabyss.job_choice.start";
        g.pose().pushPose();
        g.pose().translate(0, snap((1f - in) * 4f), 0);
        tex(g, in);
        int rgb = Plate.button(g, font, SC_X, PCTA_Y, SC_W, PCTA_H, Component.translatable(key), true, state, ac);
        if (armed) {
            float left = (armedTicks - partialTick) / ARM_TICKS;
            int bw = Math.round((SC_W - 8) * clamp01(left));
            int by = PCTA_Y + PCTA_H - 5 + (state == Plate.PRESS ? 1 : 0);
            g.fill(SC_X + 4, by, SC_X + 4 + bw, by + 1, 0xFF000000 | rgb);
        } else if (enabled && !decided && in >= 1f) {
            long cyc = (clock() - openedAt) % 3400L;
            Plate.shine(g, SC_X, PCTA_Y, SC_W, PCTA_H, cyc / 560f);
        }
        g.pose().popPose();
    }

    // 職を選ぶ前: 導きの文と、升を指す手がかり
    private void renderPlateLead(GuiGraphics g, Plate.Pal p) {
        float a = out(open(120, 200));
        int dx = Math.round(snap((1f - a) * -5f));
        Component lead = Component.translatable(switching ? "gui.skyabyss.job_room.lead" : "gui.skyabyss.job_choice.lead");
        int y = 44;
        for (net.minecraft.util.FormattedCharSequence line : TextWrap.split(font, lead, 226)) {
            g.drawString(font, line, IX + dx, y, fade(p.text, a), false);
            y += 12;
        }
        g.drawString(font, Component.translatable("gui.skyabyss.job_choice.pick_short"), IX + dx, y + 6, fade(p.dim, a), false);
        // 六つの職を一行ずつ (職の色の印・名・役目)。升に乗せると、その行が明るくなる
        Job[] jobs = Job.values();
        int top = y + 24;
        int step = Math.min(13, (156 - top) / jobs.length);
        for (int i = 0; i < jobs.length && step >= 10; i++) {
            float ai = out(open(180 + i * 30L, 200));
            Plate.Accent jc = Plate.job(i);
            boolean hot = i == lastHover;
            int ry = top + i * step;
            int rx = IX + Math.round(snap((1f - ai) * -5f));
            tex(g, ai * exitMul);
            Plate.seg(g, rx, ry, 3, 8, true, jc.mid());
            tex(g, 1f);
            g.drawString(font, Component.translatable(jobs[i].nameKey()), rx + 8, ry, fade(hot ? 0xFFF6EA : jc.hi() & 0xFFFFFF, ai), false);
            g.drawString(font, Component.translatable(jobs[i].nameKey() + ".role"), rx + 50, ry, fade(hot ? p.text : p.soft, ai), false);
        }
    }

    private void renderPlateInfo(GuiGraphics g, int mouseX, int mouseY, Plate.Pal p, Plate.Accent ac) {
        int[] style = STYLE[Math.min(picked.ordinal(), STYLE.length - 1)];
        float a0 = out(swap(0, 170));
        float a1 = out(swap(30, 170));
        float a2 = out(swap(60, 170));
        float a3 = out(swap(90, 170));
        int acHi = ac.hi() & 0xFFFFFF;
        // 職の名 (2 倍) と木の札
        Component name = Component.translatable(picked.nameKey());
        g.pose().pushPose();
        g.pose().translate(IX + snap((1f - a0) * -5f), 41, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, name, 0, 0, fade(0xFFF6EA, a0), false);
        g.pose().popPose();
        tex(g, a0 * exitMul);
        Plate.chip(g, font, IX + font.width(name) * 2 + 7, 45, Component.translatable(picked.tree.nameKey()), ac.lo(), acHi);
        tex(g, 1f);
        // 役目の一行 (職の色)
        g.drawString(font, Component.translatable(picked.nameKey() + ".role"), IX + Math.round(snap((1f - a1) * -5f)), 63, fade(acHi, a1), false);
        // 間合い (近・中・遠のうち一つが灯る) と難しさ (五つの升)
        int y = 80;
        int dx2 = Math.round(snap((1f - a2) * -5f));
        Component rl = Component.translatable("gui.skyabyss.job_choice.range");
        g.drawString(font, rl, IX + dx2, y, fade(p.soft, a2), false);
        int tx = IX + dx2 + font.width(rl) + 6;
        String[] marks = {"near", "mid", "far"};
        tex(g, a2 * exitMul);
        for (int k = 0; k < 3; k++) {
            Plate.seg(g, tx + k * 18, y + 1, 16, 6, k == style[0], ac.mid());
        }
        tex(g, 1f);
        g.drawString(font, Component.translatable("gui.skyabyss.job_choice.range." + marks[style[0]]), tx + 58, y, fade(acHi, a2), false);
        int dfx = IX + dx2 + 138;
        Component dl = Component.translatable("gui.skyabyss.job_choice.rate.difficulty");
        g.drawString(font, dl, dfx, y, fade(p.soft, a2), false);
        tex(g, a2 * exitMul);
        for (int k = 0; k < 5; k++) {
            Plate.seg(g, dfx + font.width(dl) + 6 + k * 9, y + 1, 7, 6, k < style[1], 0xFFF2C47C);
        }
        Plate.rule(g, IX, 95, 238);
        tex(g, 1f);
        // 使える技 (全部。最初に覚える技には金の菱。乗せると詳しく)
        List<Skill> actives = new ArrayList<>();
        for (Skill s : Skill.inTree(picked.tree)) {
            if (s.active && !s.keystone()) {
                actives.add(s);
            }
        }
        actives.sort(java.util.Comparator.comparingInt((Skill s) -> s.tier).thenComparingInt(s -> s.column));
        Skill first = JobChoice.firstSkill(picked);
        Skill tip = null;
        int tipX = 0;
        if (!actives.isEmpty()) {
            int dx3 = Math.round(snap((1f - a3) * -5f));
            Component head = Component.translatable("gui.skyabyss.job_choice.skills_head");
            g.drawString(font, head, IX + dx3, 101, fade(p.head, a3), false);
            if (first != null) {
                g.drawString(font, Component.translatable("gui.skyabyss.job_choice.first_hint"), IX + dx3 + font.width(head) + 8, 101, fade(p.dim, a3), false);
            }
            // 升は絵と同じ画素の大きさ (32 の絵を 32 で)。8 つ以上なら 16 で
            boolean big = actives.size() * 34 <= 238;
            int ts = big ? 34 : 18;
            int step = big ? 34 : 20;
            for (int k = 0; k < actives.size(); k++) {
                Skill s = actives.get(k);
                float pop = out(swap(90 + k * 25L, 170));
                int x = IX + k * step;
                boolean hot = k == demoHover || (mouseX >= x && mouseX < x + ts && mouseY >= 113 && mouseY < 113 + ts);
                int yy = 113 + Math.round(snap((1f - pop) * 4f)) - (hot ? 1 : 0);
                if (pop * exitMul <= 0.01f) {
                    continue;
                }
                tex(g, pop * exitMul);
                Plate.tile(g, x, yy, ts, ts, hot ? Plate.HOVER : Plate.REST, ac);
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                SkillHud.drawIcon(g, s.iconIndex(), x + 1, yy + 1, ts - 2);
                if (s == first) {
                    Plate.gem(g, x + ts - 4, yy + 4, 2, 0xFFF2C47C);
                }
                if (hot) {
                    tip = s;
                    tipX = x;
                }
            }
            tex(g, 1f);
        }
        if (tip != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
            MutableComponent head = Component.translatable(tip.nameKey()).withColor(p.head);
            if (tip == first) {
                head.append(Component.translatable("gui.skyabyss.job_choice.first_tag").withColor(p.soft));
            }
            lines.add(head.getVisualOrderText());
            if (I18n.exists(tip.typeKey())) {
                lines.add(Component.translatable(tip.typeKey()).withColor(p.soft).getVisualOrderText());
            }
            int cd = jp.revati.skyabyss.rpg.skill.SkillRules.cooldownTicks(tip, 1);
            String sec = String.format(java.util.Locale.ROOT, cd % 20 == 0 ? "%.0f" : "%.1f", cd / 20f);
            lines.add(Component.translatable("gui.skyabyss.job_choice.cost", jp.revati.skyabyss.rpg.skill.SkillRules.manaCost(tip, 1), sec)
                    .withColor(p.value).getVisualOrderText());
            lines.addAll(TextWrap.split(font, Component.translatable(tip.effectKey(), jp.revati.skyabyss.rpg.skill.SkillRules.describeArgs(tip, 1))
                    .withColor(p.text), 190));
            Plate.fadeEnd(g);
            if (demoHover >= 0) {
                g.renderTooltip(font, lines, tipX + 20, 140);
            } else {
                g.renderTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, W, H);
    }
}
