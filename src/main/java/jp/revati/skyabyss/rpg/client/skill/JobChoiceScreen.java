package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.JobChoice;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.net.JobChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 島の野営地で職を選ぶ画面 (docs/design/43 の 4 節) と、街の職の間で職を替える画面 (switching。docs/design/48 の 3 章)。
// 切り絵の画面 (docs/design/56 の「切り絵」): 黒い紙を画面いっぱいに敷き、左上の生成りの帯に説明、右に自分の姿 (生成りの台紙の前)、
// 左下に六つの職の札を扇に広げる。選んだ札は桃色で持ち上がる。「この職で始める」「この職に替える」は二度押し。
// 職の間では、いまの職の札に桃色の菱を留め、その職の木に使った点を見せる (替えても残る)。
// 島の画面には「あとで」を置かない: 職を選ぶまで島は進まず、街の職の間は渡し船の先なので、ほかに選ぶ所がない
// (2026-11-03 の声「最初に選ばなかったら、どこで選ぶの？ 後での項目いらなくない？」)。Esc では閉じられ、野営地にいれば REOPEN_MS のあとでまた開く (JobChoice.prompt)
// 中身は論理の 408x228 (1280x720・GUI 3 でも 1 倍で入る) に置き、窓に合わせて画素ちょうどで大きくする。黒い紙と山は窓の端まで
public class JobChoiceScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 島の画面を閉じてから、また開くまで (この間に Esc をもう一度押せばゲームのメニューが開く)
    static final long REOPEN_MS = 5000;
    private static long closedAt;
    static final int W = 408;
    static final int H = 228;
    // 説明の帯 (生成り) と、その中の字の欄
    private static final float[] STRIP = {-6, 12, 118, 5, 224, 1, 228, 64, 222, 128, 106, 134, -6, 137};
    private static final int TEXT_X = 8;
    private static final int TEXT_Y = 13;
    private static final int TEXT_W = 206;
    private static final int TEXT_H = 114;
    // 姿の台紙 (尖った窓の形)
    private static final float[] ARCH = {264, 214, 262, 100, 270, 70, 288, 48, 306, 40, 324, 48, 342, 70, 350, 100, 348, 214};
    private static final int FIG_X0 = 266;
    private static final int FIG_X1 = 346;
    private static final int FIG_Y0 = 56;
    private static final int FIG_Y1 = 208;
    // 札 (扇)
    private static final int CARD_W = 28;
    private static final int CARD_H = 38;
    // 浮かぶ菱 {x, y, 大きさ, 桃色なら 1}
    private static final int[][] DIAMONDS = {{238, 26, 3, 1}, {372, 20, 5, 1}, {392, 66, 2, 0}, {226, 150, 2, 1}, {384, 138, 4, 0},
            {244, 96, 2, 0}, {396, 178, 3, 1}, {362, 100, 2, 1}, {214, 182, 2, 0}};
    private static final int LINE_H = 13;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private Job picked;
    private boolean armed;
    private int armedTicks;
    private Paper.Tag start;
    private final ScrollPanel right = new ScrollPanel(LINE_H * 2);
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
        picked = job;
        armed = false;
        right.setScroll(0);
        if (start != null) {
            update();
        }
        return this;
    }

    private void layout() {
        fit.fit(width, height, W, H, 3f);
    }

    @Override
    protected void init() {
        layout();
        int by = 207;
        if (switching) {
            addRenderableWidget(new Paper.Tag(232, by, 58, 15, Component.translatable("gui.skyabyss.job_room.later"), b -> onClose(), false));
        }
        start = addRenderableWidget(new Paper.Tag(296, by, 106, 15, Component.translatable(switching ? "gui.skyabyss.job_room.switch_btn" : "gui.skyabyss.job_choice.start"), b -> press(), true));
        update();
    }

    private void update() {
        start.active = picked != null && !(switching && picked == current());
        start.setMessage(Component.translatable(armed ? (switching ? "gui.skyabyss.job_room.confirm" : "gui.skyabyss.job_choice.confirm")
                : switching ? "gui.skyabyss.job_room.switch_btn" : "gui.skyabyss.job_choice.start"));
    }

    private void press() {
        if (picked == null) {
            return;
        }
        if (!armed) {
            armed = true;
            armedTicks = 80;
            update();
            return;
        }
        PacketDistributor.sendToServer(new JobChoosePayload(picked.id));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (armedTicks > 0 && --armedTicks == 0) {
            armed = false;
            update();
        }
    }

    // 札の真ん中 {x, y} と傾き (度)。選んだ札は持ち上がる
    private static float[] card(int i, boolean picked, boolean hover) {
        float cx = 30 + i * 30;
        float cy = 190 + Math.abs(i - 2.5f) * 5 - (picked ? 18 : hover ? 4 : 0);
        return new float[] {cx, cy, -20 + i * 8};
    }

    private int cardAt(double mx, double my) {
        Job[] jobs = Job.values();
        // 右の札が上に重なるので、右から調べる
        for (int i = jobs.length - 1; i >= 0; i--) {
            float[] c = card(i, jobs[i] == picked, false);
            if (Math.abs(mx - c[0]) <= CARD_W / 2f && my >= c[1] - CARD_H / 2f - 4 && my <= c[1] + CARD_H / 2f) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (right.mouseClicked(mx, my, button)) {
            return true;
        }
        int i = cardAt(mx, my);
        if (i >= 0) {
            pick(Job.values()[i]);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double sx, double sy, int button, double dx, double dy) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        return right.mouseDragged(mx, my, button, dx / fit.scale(), dy / fit.scale()) || super.mouseDragged(mx, my, button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        return right.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double sx, double sy, double dx, double dy) {
        return right.mouseScrolled(fit.x(sx), fit.y(sy), dy) || super.mouseScrolled(sx, sy, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return right.keyPressed(key) || super.keyPressed(key, scan, mods);
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

    // 黒い紙と桃色の山は窓の端まで (論理の座標で、窓の四隅まで)
    private void renderSheet(GuiGraphics g) {
        int x0 = (int) Math.floor(fit.x(0));
        int y0 = (int) Math.floor(fit.y(0));
        int x1 = (int) Math.ceil(fit.x(width));
        int y1 = (int) Math.ceil(fit.y(height));
        int sw = x1 - x0;
        int sh = y1 - y0;
        // 中身の箱 (0..W, 0..H) の窓の中での位置
        int ox = -x0;
        int oy = -y0;
        Paper.draw(g, "job_sheet", ox + "," + oy, x0, y0, sw, sh, s -> {
            // 黒い紙: 四辺を少し内側で、まっすぐ切る (角は斜めに落とす)
            float[] black = {
                    7, 10, sw * 0.22f, 6, sw * 0.47f, 9, sw * 0.71f, 4, sw - 9, 8,
                    sw - 5, sh * 0.30f, sw - 8, sh * 0.62f, sw - 4, sh - 12,
                    sw * 0.64f, sh - 6, sw * 0.31f, sh - 9, 10, sh - 5,
                    5, sh * 0.58f, 8, sh * 0.27f};
            s.poly(black, Paper.BLACK);
            // 桃色の山 (下の辺に沿って。峰は手で置いた)
            float base = oy + 226;
            float[][] peaks = {{-20, 0}, {ox + 18, -16}, {ox + 44, -6}, {ox + 86, -30}, {ox + 120, -12}, {ox + 150, -22}, {ox + 196, -4},
                    {ox + 236, -26}, {ox + 262, -10}, {ox + 312, -36}, {ox + 350, -14}, {ox + 384, -24}, {ox + 420, -8}, {sw + 20, -18}};
            float[] hill = new float[peaks.length * 2 + 4];
            for (int i = 0; i < peaks.length; i++) {
                hill[i * 2] = peaks[i][0];
                hill[i * 2 + 1] = base + peaks[i][1];
            }
            hill[peaks.length * 2] = sw + 20;
            hill[peaks.length * 2 + 1] = sh + 4;
            hill[peaks.length * 2 + 2] = -20;
            hill[peaks.length * 2 + 3] = sh + 4;
            s.poly(hill, Paper.PINK_LO);
            // 山の手前の峰を一枚 (明るい桃色の縁だけ見せる)
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
            for (int[] d : DIAMONDS) {
                s.diamond(ox + d[0], oy + d[1], d[2], d[3] == 1 ? Paper.PINK : Paper.GREY, d[3] == 1 ? Paper.PINK_HI : 0xFFD8D2CA);
            }
        });
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        layout();
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderSheet(g);
        // 帯と台紙
        Paper.draw(g, "job_strip", "", -10, -2, 244, 144, s -> s.layer(shift(STRIP, 10, 2), Paper.CREAM, Paper.PINK_LO, -3, 5));
        Paper.draw(g, "job_arch", "", 256, 34, 104, 186, s -> s.layer(shift(ARCH, -256, -34), Paper.CREAM, Paper.PINK, 5, 4));
        fit.end(g);
        // 自分の姿 (台紙の前。窓の座標で描く)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int[] r = fit.screenRect(FIG_X0, FIG_Y0, FIG_X1 - FIG_X0, FIG_Y1 - FIG_Y0);
            int size = Math.round(62 * fit.scale());
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, r[0], r[1], r[0] + r[2], r[1] + r[3], size, 0.0625f, screenX, screenY, mc.player);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        fit.begin(g);
        // 札
        Job cur = switching ? current() : null;
        Job[] jobs = Job.values();
        int hover = cardAt(mouseX, mouseY);
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            boolean isPicked = j == picked;
            float[] c = card(i, isPicked, i == hover && !isPicked);
            int body = isPicked ? Paper.PINK : Paper.CREAM;
            int bx = Math.round(c[0]) - 26;
            int byy = Math.round(c[1]) - 28;
            float ang = c[2];
            Paper.draw(g, "job_card_" + i, body + ":" + ang, bx, byy, 52, 56,
                    s -> s.layer(Paper.rotRect(26, 28, CARD_W, CARD_H, ang), body, Paper.BLACK_LO, 2, 2));
            g.renderItem(JobChoice.starter(j), Math.round(c[0]) - 8, Math.round(c[1]) - 12);
            if (j == cur) {
                Paper.draw(g, "job_pin", "", Math.round(c[0]) - 4, Math.round(c[1]) - 26, 9, 9, s -> s.diamond(4, 4, 4, Paper.PINK_HI, 0xFFFFFFFF));
            }
        }
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        right.setViewport(TEXT_X, TEXT_Y, TEXT_W, TEXT_H);
        renderPage(g, TEXT_X);
        if (hover >= 0) {
            Job j = jobs[hover];
            MutableComponent name = Component.translatable(j.nameKey());
            if (j == cur) {
                name.append(Component.translatable("gui.skyabyss.job_room.current"));
            }
            g.renderComponentTooltip(font, List.of(name, Component.translatable(j.tree.nameKey()).withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
        fit.end(g);
        g.pose().popPose();
    }

    private static float[] shift(float[] p, float dx, float dy) {
        float[] o = p.clone();
        for (int i = 0; i < o.length; i += 2) {
            o[i] += dx;
            o[i + 1] += dy;
        }
        return o;
    }

    private void renderPage(GuiGraphics g, int rx) {
        right.begin(g);
        int y = right.top();
        int tw = right.width();
        g.drawString(font, title, rx, y + 2, Paper.INK_HEAD, false);
        y += LINE_H + 2;
        if (picked == null) {
            // 導きの一行と選び方 (職を選ぶまで)
            y = block(g, Component.translatable(switching ? "gui.skyabyss.job_room.lead" : "gui.skyabyss.job_choice.lead"), rx, y, tw, Paper.INK);
            y = block(g, Component.translatable(switching ? "gui.skyabyss.job_room.pick" : "gui.skyabyss.job_choice.pick"), rx, y + 3, tw, Paper.INK_SOFT);
            right.end(g);
            right.setContentHeight(y - right.top());
            return;
        }
        g.drawString(font, Component.translatable(picked.nameKey()), rx, y + 2, Paper.INK, false);
        g.drawString(font, Component.translatable(picked.tree.nameKey()), rx + font.width(Component.translatable(picked.nameKey())) + 6, y + 2, Paper.INK_HEAD, false);
        y += LINE_H;
        y = block(g, Component.translatable(picked.nameKey() + ".role"), rx, y, tw, Paper.INK) + 3;
        ItemStack weapon = JobChoice.starter(picked);
        y = block(g, Component.translatable("gui.skyabyss.job_choice.weapon", Component.literal(weapon.getHoverName().getString())), rx, y, tw, Paper.INK);
        List<Skill> actives = new ArrayList<>();
        for (Skill s : Skill.inTree(picked.tree)) {
            if (s.active && !s.keystone()) {
                actives.add(s);
            }
        }
        actives.sort(java.util.Comparator.comparingInt((Skill s) -> s.tier).thenComparingInt(s -> s.column));
        if (actives.isEmpty()) {
            y = block(g, Component.translatable("gui.skyabyss.job_choice.skills_soon"), rx, y, tw, Paper.INK_SOFT);
        } else {
            MutableComponent names = Component.empty();
            for (int i = 0; i < actives.size(); i++) {
                if (i > 0) {
                    names.append(Component.translatable("gui.skyabyss.job_choice.sep"));
                }
                names.append(Component.translatable(actives.get(i).nameKey()));
            }
            y = block(g, Component.translatable("gui.skyabyss.job_choice.skills", names), rx, y, tw, Paper.INK);
            Skill first = JobChoice.firstSkill(picked);
            if (first != null) {
                y = block(g, Component.translatable("gui.skyabyss.job_choice.first", Component.translatable(first.nameKey())), rx, y, tw, Paper.INK_HEAD);
            }
        }
        y += 3;
        if (switching) {
            Minecraft mc = Minecraft.getInstance();
            int spent = mc.player == null ? 0 : jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player).nodeSpent(picked);
            y = block(g, Component.translatable("gui.skyabyss.job_room.points", spent), rx, y, tw, Paper.INK);
            y = block(g, Component.translatable("gui.skyabyss.job_room.note"), rx, y, tw, Paper.INK_SOFT);
        } else {
            y = block(g, Component.translatable("gui.skyabyss.job_choice.switch"), rx, y, tw, Paper.INK_SOFT);
        }
        right.end(g);
        right.setContentHeight(y - right.top());
        right.drawBar(g, 0x30181722, 0xFFE45A9C);
    }

    private int block(GuiGraphics g, Component text, int x, int y, int tw, int color) {
        for (FormattedCharSequence line : TextWrap.split(font, text, tw)) {
            g.drawString(font, line, x, y + 2, color, false);
            y += LINE_H;
        }
        return y;
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, W, H);
    }
}
