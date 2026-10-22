package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.rpg.skill.net.CallingChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// 天職を選ぶ画面 (心臓の報酬のあと・K の画面から)。五つの木が左に並び、右に天性・役目・開くもの。
// 「継ぐ」は二度押し。一つ目の天職は木が全部開く、二つ目は 3 行目まで (要と天性はなし)
// 2026-10-22 の部屋の見直し: 窓は画面に合わせて広げ、右の説明は目盛りつきで送る (以前は長い説明が窓の下で切れた)
public class CallingScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int MAX_W = 340;
    private static final int MAX_H = 200;
    private static final int ROW_Y = 30;
    private static final int ROW_H = 24;
    private static final int LIST_W = 98;
    private static final int LINE_H = 11;

    private final int which;
    private int w;
    private int h;
    private int left;
    private int top;
    private SkillTree picked;
    private boolean armed;
    private int armedTicks;
    private Button inherit;
    private Button later;
    private final List<SkillTree> trees = new ArrayList<>();
    private final ScrollPanel right = new ScrollPanel(LINE_H * 2);

    public CallingScreen(int which) {
        super(Component.translatable(which == 2 ? "gui.skyabyss.calling.title2" : "gui.skyabyss.calling.title"));
        this.which = which;
        for (SkillTree t : SkillTree.values()) {
            if (t.callable()) {
                trees.add(t);
            }
        }
    }

    public SkillTree picked() {
        return picked;
    }

    public void pick(SkillTree tree) {
        picked = tree;
        armed = false;
        right.setScroll(0);
        if (inherit != null) {
            update();
        }
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
        inherit = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.calling.inherit"), b -> press())
                .bounds(left + LIST_W + 16, top + h - 22, 70, 14).build());
        later = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.calling.later"), b -> onClose())
                .bounds(left + LIST_W + 90, top + h - 22, w - LIST_W - 98, 14).build());
        update();
    }

    private SkillData data() {
        return SkillManager.get(Minecraft.getInstance().player);
    }

    private boolean taken(SkillTree t) {
        SkillData d = data();
        return t.id.equals(d.calling) || t.id.equals(d.calling2);
    }

    private void update() {
        inherit.active = picked != null && !taken(picked);
        inherit.setMessage(Component.translatable(armed ? "gui.skyabyss.calling.confirm" : "gui.skyabyss.calling.inherit"));
    }

    private void press() {
        if (picked == null || taken(picked)) {
            return;
        }
        if (!armed) {
            armed = true;
            armedTicks = 80;
            update();
            return;
        }
        PacketDistributor.sendToServer(new CallingChoosePayload(picked.id));
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
        // もう選べなくなったら (別の画面で選んだ) 閉じる
        SkillData d = data();
        if (which == 1 && d.hasCalling() || which == 2 && !d.calling2.isEmpty()) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (right.mouseClicked(mx, my, button)) {
            return true;
        }
        for (int i = 0; i < trees.size(); i++) {
            int x = left + 8;
            int y = top + ROW_Y + i * ROW_H;
            if (mx >= x && mx < x + LIST_W && my >= y && my < y + ROW_H - 2) {
                if (!taken(trees.get(i))) {
                    pick(trees.get(i));
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2f));
                }
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
        g.drawCenteredString(font, Component.translatable(which == 2 ? "gui.skyabyss.calling.lead2" : "gui.skyabyss.calling.lead"),
                left + w / 2, top + 18, SkillScreen.SOFT);
        for (int i = 0; i < trees.size(); i++) {
            SkillTree t = trees.get(i);
            int x = left + 8;
            int y = top + ROW_Y + i * ROW_H;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H - 2;
            boolean isPicked = t == picked;
            boolean isTaken = taken(t);
            g.fill(x, y, x + LIST_W, y + ROW_H - 2, isPicked ? 0xFF24384A : hover && !isTaken ? 0xFF1C2733 : 0xFF10151D);
            if (isPicked) {
                g.fill(x, y, x + 2, y + ROW_H - 2, 0xFF000000 | SkillScreen.GOLD);
            }
            SkillHud.drawIcon(g, t.iconIndex(), x + 5, y + 3);
            g.drawString(font, Component.translatable("calling.skyabyss." + t.id), x + 26, y + 4, isTaken ? SkillScreen.DIM : SkillScreen.TEXT, false);
            if (isTaken) {
                g.drawString(font, Component.translatable("gui.skyabyss.calling.taken"), x + 26, y + 13, SkillScreen.DIM, false);
            }
        }
        int rx = left + LIST_W + 16;
        int rw = w - LIST_W - 24;
        right.setViewport(rx, top + ROW_Y, rw, h - ROW_Y - 28);
        if (picked == null) {
            return;
        }
        right.begin(g);
        int y = right.top();
        g.drawString(font, Component.translatable("calling.skyabyss." + picked.id), rx, y, SkillScreen.GOLD, false);
        y += 13;
        int tw = right.width();
        y = block(g, Component.translatable(picked.descKey()), rx, y, tw, SkillScreen.SOFT) + 4;
        if (which == 1) {
            y = block(g, Component.translatable("gui.skyabyss.calling.trait", Component.translatable("calling.skyabyss." + picked.id + ".trait")), rx, y, tw,
                    SkillScreen.PALE);
            y = block(g, Component.translatable("calling.skyabyss." + picked.id + ".trait.desc"), rx, y, tw, SkillScreen.TEXT) + 4;
        }
        y = block(g, Component.translatable("gui.skyabyss.calling.role", Component.translatable("calling.skyabyss." + picked.id + ".role")), rx, y, tw,
                SkillScreen.LORE) + 4;
        y = block(g, Component.translatable(which == 2 ? "gui.skyabyss.calling.deep2" : "gui.skyabyss.calling.deep"), rx, y, tw, SkillScreen.SOFT);
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
