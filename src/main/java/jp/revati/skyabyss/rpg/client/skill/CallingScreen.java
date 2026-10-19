package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

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
public class CallingScreen extends Screen {
    private static final int W = 300;
    private static final int H = 168;
    private static final int ROW_Y = 30;
    private static final int ROW_H = 24;
    private static final int LIST_W = 98;

    private final int which;
    private int left;
    private int top;
    private SkillTree picked;
    private boolean armed;
    private int armedTicks;
    private Button inherit;
    private Button later;
    private final List<SkillTree> trees = new ArrayList<>();

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
        if (inherit != null) {
            update();
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        inherit = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.calling.inherit"), b -> press())
                .bounds(left + LIST_W + 16, top + H - 22, 70, 14).build());
        later = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.calling.later"), b -> onClose())
                .bounds(left + LIST_W + 90, top + H - 22, W - LIST_W - 98, 14).build());
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2;
        int y = (height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF0B0E14);
        g.fill(x, y, x + W, y + H, 0xF0141922);
        g.fill(x, y, x + W, y + 1, 0xFF3E5A6B);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF3E5A6B);
        g.fill(x, y, x + 1, y + H, 0xFF3E5A6B);
        g.fill(x + W - 1, y, x + W, y + H, 0xFF3E5A6B);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 7, SkillScreen.TITLE);
        g.drawCenteredString(font, Component.translatable(which == 2 ? "gui.skyabyss.calling.lead2" : "gui.skyabyss.calling.lead"),
                left + W / 2, top + 18, SkillScreen.SOFT);
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
        int rw = W - LIST_W - 24;
        if (picked == null) {
            return;
        }
        int y = top + ROW_Y;
        g.drawString(font, Component.translatable("calling.skyabyss." + picked.id), rx, y, SkillScreen.GOLD, false);
        y += 13;
        y = block(g, Component.translatable(picked.descKey()), rx, y, rw, SkillScreen.SOFT) + 4;
        if (which == 1) {
            y = block(g, Component.translatable("gui.skyabyss.calling.trait", Component.translatable("calling.skyabyss." + picked.id + ".trait")), rx, y, rw,
                    SkillScreen.PALE);
            y = block(g, Component.translatable("calling.skyabyss." + picked.id + ".trait.desc"), rx, y, rw, SkillScreen.TEXT) + 4;
        }
        y = block(g, Component.translatable("gui.skyabyss.calling.role", Component.translatable("calling.skyabyss." + picked.id + ".role")), rx, y, rw,
                SkillScreen.LORE) + 4;
        block(g, Component.translatable(which == 2 ? "gui.skyabyss.calling.deep2" : "gui.skyabyss.calling.deep"), rx, y, rw, SkillScreen.SOFT);
    }

    private int block(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : TextWrap.split(font, text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }
}
