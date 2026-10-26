package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

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

// 島の宿り木の祠で職を選ぶ画面 (docs/design/43 の 4 節)。左に六つの職 (始めの武器の絵・名前・副題)、右に役目・受け取る武器・使う技・最初に覚える技。
// 「この職で始める」は二度押し。ほかの職も、あとでその職の武器を持てばなれる (都の訓練場の師範から始めの武器をもらえる)。作りは天職の画面 (CallingScreen) と同じ
public class JobChoiceScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 340;
    private static final int MAX_H = 200;
    private static final int ROW_Y = 30;
    private static final int ROW_H = 24;
    private static final int LIST_W = 112;
    private static final int LINE_H = 11;

    private int w;
    private int h;
    private int left;
    private int top;
    private Job picked;
    private boolean armed;
    private int armedTicks;
    private Button start;
    private Button later;
    private final ScrollPanel right = new ScrollPanel(LINE_H * 2);

    public JobChoiceScreen() {
        super(Component.translatable("gui.skyabyss.job_choice.title"));
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
        w = Math.min(MAX_W, width - 16);
        h = Math.min(MAX_H, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    protected void init() {
        layout();
        start = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.job_choice.start"), b -> press())
                .bounds(left + LIST_W + 16, top + h - 22, 96, 14).build());
        later = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.job_choice.later"), b -> onClose())
                .bounds(left + LIST_W + 116, top + h - 22, w - LIST_W - 124, 14).build());
        update();
    }

    private void update() {
        start.active = picked != null;
        start.setMessage(Component.translatable(armed ? "gui.skyabyss.job_choice.confirm" : "gui.skyabyss.job_choice.start"));
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
    public boolean mouseClicked(double mx, double my, int button) {
        if (right.mouseClicked(mx, my, button)) {
            return true;
        }
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            int x = left + 8;
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
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return right.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return right.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return right.mouseScrolled(mx, my, dy) || super.mouseScrolled(mx, my, dx, dy);
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        layout();
        int x = left;
        int y = top;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0B0E14);
        g.fill(x, y, x + w, y + h, 0xF0141922);
        g.fill(x, y, x + w, y + 1, 0xFF3E5A6B);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF3E5A6B);
        g.fill(x, y, x + 1, y + h, 0xFF3E5A6B);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF3E5A6B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + w / 2, top + 7, SkillScreen.TITLE);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.job_choice.lead"), left + w / 2, top + 18, SkillScreen.SOFT);
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            int x = left + 8;
            int y = top + ROW_Y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H - 2;
            boolean isPicked = j == picked;
            g.fill(x, y, x + LIST_W, y + ROW_H - 2, isPicked ? 0xFF24384A : hover ? 0xFF1C2733 : 0xFF10151D);
            if (isPicked) {
                g.fill(x, y, x + 2, y + ROW_H - 2, 0xFF000000 | SkillScreen.GOLD);
            }
            g.renderItem(JobChoice.starter(j), x + 4, y + 3);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(j.nameKey()), x + 24, y + 2, LIST_W - 28, isPicked ? SkillScreen.GOLD : SkillScreen.TEXT, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(j.tree.nameKey()), x + 24, y + 12, LIST_W - 28, SkillScreen.DIM, false);
        }
        int rx = left + LIST_W + 16;
        int rw = w - LIST_W - 24;
        right.setViewport(rx, top + ROW_Y, rw, h - ROW_Y - 28);
        if (picked == null) {
            right.begin(g);
            int y = block(g, Component.translatable("gui.skyabyss.job_choice.pick"), rx, right.top(), right.width(), SkillScreen.SOFT);
            right.end(g);
            right.setContentHeight(y - right.top());
            return;
        }
        right.begin(g);
        int y = right.top();
        int tw = right.width();
        g.drawString(font, Component.translatable("job.skyabyss.subtitle", Component.translatable(picked.nameKey()), Component.translatable(picked.tree.nameKey())),
                rx, y, SkillScreen.GOLD, false);
        y += 13;
        y = block(g, Component.translatable(picked.nameKey() + ".role"), rx, y, tw, SkillScreen.TEXT) + 4;
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
        y += 4;
        y = block(g, Component.translatable("gui.skyabyss.job_choice.switch"), rx, y, tw, SkillScreen.SOFT);
        right.end(g);
        right.setContentHeight(y - right.top());
        right.drawBar(g, 0xFF10151D, 0xAA3E5A6B);
    }

    private int block(GuiGraphics g, Component text, int x, int y, int tw, int color) {
        for (FormattedCharSequence line : TextWrap.split(font, text, tw)) {
            g.drawString(font, line, x, y, color, false);
            y += LINE_H;
        }
        return y;
    }

    @Override
    public int[] textBounds() {
        layout();
        return new int[] {left, top, w, h};
    }
}
