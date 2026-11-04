package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.JobChoice;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.net.JobChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 島の野営地で職を選ぶ画面 (docs/design/43 の 4 節) と、街の職の間で職を替える画面 (switching。docs/design/48 の 3 章)。
// 左に六つの職 (始めの武器の絵・名前・副題)、右に役目・受け取る武器・使う技・最初に覚える技。「この職で始める」「この職に替える」は二度押し。
// 職の間では、いまの職に（いま）を添え、その職の木に使った点を見せる (替えても残る)。
// 島の画面には「あとで」を置かない: 職を選ぶまで島は進まず、街の職の間は渡し船の先なので、ほかに選ぶ所がない
// (2026-11-03 の声「最初に選ばなかったら、どこで選ぶの？ 後での項目いらなくない？」)。Esc では閉じられ、野営地にいれば REOPEN_MS のあとでまた開く (JobChoice.prompt)
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 職の一覧は脇の列 (左)、説明は主の列 (右)、決める釦は下の段の右
public class JobChoiceScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 島の画面を閉じてから、また開くまで (この間に Esc をもう一度押せばゲームのメニューが開く)
    static final long REOPEN_MS = 5000;
    private static long closedAt;
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    private static final Golden.Box LIST = LAYOUT.body().minor(true);
    private static final Golden.Box PAGE = LAYOUT.body().major(false);
    private static final int ROW_Y = LIST.y() + 3;
    private static final int ROW_H = 24;
    private static final int LIST_W = LIST.w() - 6;
    private static final int LINE_H = Golden.ROW;
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    private int w;
    private int h;
    private int left;
    private int top;
    private Job picked;
    private boolean armed;
    private int armedTicks;
    private Button start;
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
        fit.fit(width, height, Golden.L_W, Golden.L_H, 3f);
        w = Golden.L_W;
        h = Golden.L_H;
        left = 0;
        top = 0;
    }

    @Override
    protected void init() {
        layout();
        Golden.Box foot = LAYOUT.footUnder(PAGE);
        int by = foot.y() + 4;
        if (switching) {
            addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.job_room.later"), b -> onClose())
                    .bounds(foot.right() - Golden.S55, by, Golden.S55, Golden.BTN).build());
        }
        int sx = foot.right() - Golden.S89 - (switching ? Golden.S55 + Golden.S3 : 0);
        start = addRenderableWidget(Button.builder(Component.translatable(switching ? "gui.skyabyss.job_room.switch_btn" : "gui.skyabyss.job_choice.start"), b -> press())
                .bounds(sx, by, Golden.S89, Golden.BTN).build());
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

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        if (right.mouseClicked(mx, my, button)) {
            return true;
        }
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            int x = LIST.x() + 3;
            int y = top + ROW_Y + i * ROW_H;
            if (mx >= x && mx < x + LIST_W && my >= y && my < y + ROW_H - 2) {
                pick(jobs[i]);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                return true;
            }
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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    // 板・中身・釦は、窓に合わせた変換 (fit。画素ちょうど) の中で描く
    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        layout();
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.recess(g, LIST);
        Golden.recess(g, PAGE);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), SkillScreen.TITLE, false);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        Job cur = switching ? current() : null;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            int x = LIST.x() + 3;
            int y = ROW_Y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H - 2;
            boolean isPicked = j == picked;
            if (isPicked) {
                Golden.selected(g, new Golden.Box(x, y, LIST_W, ROW_H - 1));
                g.fill(x, y, x + 2, y + ROW_H - 1, 0xFF000000 | SkillScreen.GOLD);
            } else if (hover) {
                g.fill(x, y, x + LIST_W, y + ROW_H - 1, 0xFF1C2733);
            }
            g.renderItem(JobChoice.starter(j), x + Golden.S5, y + 4);
            MutableComponent rowName = Component.translatable(j.nameKey());
            if (j == cur) {
                rowName.append(Component.translatable("gui.skyabyss.job_room.current"));
            }
            int tx = x + Golden.S5 + 16 + Golden.S5;
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, rowName, tx, y + 2, x + LIST_W - tx - 3, isPicked ? SkillScreen.GOLD : j == cur ? SkillScreen.PALE : SkillScreen.TEXT, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(j.tree.nameKey()), tx, y + 13, x + LIST_W - tx - 3, SkillScreen.SOFT, false);
        }
        int rx = PAGE.x() + Golden.S8;
        int rw = PAGE.right() - 2 - rx;
        right.setViewport(rx, PAGE.y() + 2, rw, PAGE.h() - 4);
        renderPage(g, rx);
        fit.end(g);
    }

    private void renderPage(GuiGraphics g, int rx) {
        if (picked == null) {
            // 導きの一行と選び方 (職を選ぶまで)
            right.begin(g);
            int y = block(g, Component.translatable(switching ? "gui.skyabyss.job_room.lead" : "gui.skyabyss.job_choice.lead"), rx, right.top(), right.width(), SkillScreen.TEXT);
            y = block(g, Component.translatable(switching ? "gui.skyabyss.job_room.pick" : "gui.skyabyss.job_choice.pick"), rx, y + Golden.S5, right.width(), SkillScreen.SOFT);
            right.end(g);
            right.setContentHeight(y - right.top());
            return;
        }
        right.begin(g);
        int y = right.top();
        int tw = right.width();
        g.drawString(font, Component.translatable("job.skyabyss.subtitle", Component.translatable(picked.nameKey()), Component.translatable(picked.tree.nameKey())),
                rx, Golden.textY(y, LINE_H), SkillScreen.GOLD, false);
        y += LINE_H + Golden.S5;
        y = block(g, Component.translatable(picked.nameKey() + ".role"), rx, y, tw, SkillScreen.TEXT) + Golden.S5;
        ItemStack weapon = JobChoice.starter(picked);
        y = block(g, Component.translatable("gui.skyabyss.job_choice.weapon", weapon.getHoverName()), rx, y, tw, SkillScreen.PALE);
        List<Skill> actives = new ArrayList<>();
        for (Skill s : Skill.inTree(picked.tree)) {
            if (s.active && !s.keystone()) {
                actives.add(s);
            }
        }
        actives.sort(java.util.Comparator.comparingInt((Skill s) -> s.tier).thenComparingInt(s -> s.column));
        if (actives.isEmpty()) {
            y = block(g, Component.translatable("gui.skyabyss.job_choice.skills_soon"), rx, y, tw, SkillScreen.SOFT);
        } else {
            MutableComponent names = Component.empty();
            for (int i = 0; i < actives.size(); i++) {
                if (i > 0) {
                    names.append(Component.translatable("gui.skyabyss.job_choice.sep"));
                }
                names.append(Component.translatable(actives.get(i).nameKey()));
            }
            y = block(g, Component.translatable("gui.skyabyss.job_choice.skills", names), rx, y, tw, SkillScreen.TEXT);
            Skill first = JobChoice.firstSkill(picked);
            if (first != null) {
                y = block(g, Component.translatable("gui.skyabyss.job_choice.first", Component.translatable(first.nameKey())), rx, y, tw, SkillScreen.PALE);
            }
        }
        y += Golden.S5;
        if (switching) {
            Minecraft mc = Minecraft.getInstance();
            int spent = mc.player == null ? 0 : jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player).nodeSpent(picked);
            y = block(g, Component.translatable("gui.skyabyss.job_room.points", spent), rx, y, tw, SkillScreen.PALE);
            y = block(g, Component.translatable("gui.skyabyss.job_room.note"), rx, y, tw, SkillScreen.SOFT);
        } else {
            y = block(g, Component.translatable("gui.skyabyss.job_choice.switch"), rx, y, tw, SkillScreen.SOFT);
        }
        right.end(g);
        right.setContentHeight(y - right.top());
        right.drawBar(g, 0xFF10151D, 0xAA3E5A6B);
    }

    private int block(GuiGraphics g, Component text, int x, int y, int tw, int color) {
        for (FormattedCharSequence line : TextWrap.split(font, text, tw)) {
            g.drawString(font, line, x, Golden.textY(y, LINE_H), color, false);
            y += LINE_H;
        }
        return y;
    }

    @Override
    public int[] textBounds() {
        layout();
        return fit.screenRect(0, 0, w, h);
    }
}
