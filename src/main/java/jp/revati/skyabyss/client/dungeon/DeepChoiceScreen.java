package jp.revati.skyabyss.client.dungeon;

import java.util.List;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.dungeon.DeepChoice;
import jp.revati.skyabyss.rpg.client.skill.TextWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// 層の門で「ふつうに入る / 深く潜る」を選ぶ画面 (dungeon/DeepChoice)。左右二枚の札: ふつう (石の色) と深く潜る (淵の紫、何が変わるかを三行)。
// 札を押すと入る。Esc で閉じれば入らない
public class DeepChoiceScreen extends Screen {
    private static final Golden.Layout LAYOUT = Golden.Layout.medium(0);
    private static final int DEEP = 0xB48AE6;
    private final DeepChoice.Open data;
    private final UiFit fit = new UiFit();

    public DeepChoiceScreen(DeepChoice.Open data) {
        super(Component.translatable("gui.skyabyss.dungeon.deep.title", Component.translatable("dungeon.skyabyss." + data.dungeon())));
        this.data = data;
    }

    public static void open(DeepChoice.Open data) {
        Minecraft.getInstance().setScreen(new DeepChoiceScreen(data));
    }

    @Override
    protected void init() {
        fit.fit(width, height, Golden.M_W, Golden.M_H, 3f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Golden.Box card(boolean deep) {
        Golden.Box b = LAYOUT.body().afterTop(Golden.ROW);
        int w = (b.w() - Golden.S8) / 2;
        return new Golden.Box(deep ? b.x() + w + Golden.S8 : b.x(), b.y(), w, b.h());
    }

    @Override
    public void render(GuiGraphics g, int sx, int sy, float partialTick) {
        renderBackground(g, sx, sy, partialTick);
        int mx = (int) Math.floor(fit.x(sx));
        int my = (int) Math.floor(fit.y(sy));
        fit.begin(g);
        Golden.frame(g, font, LAYOUT, null);
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, head.x() + Golden.S5, Golden.textY(head.y(), head.h()), Golden.GOLD, false);
        Golden.Box body = LAYOUT.body();
        g.drawString(font, Component.translatable("gui.skyabyss.dungeon.deep.mood", Component.translatable(data.moodName())), body.x() + 2,
                Golden.textY(body.y(), Golden.ROW), Golden.SOFT, false);
        for (boolean deep : new boolean[] {false, true}) {
            Golden.Box c = card(deep);
            boolean hover = mx >= c.x() && mx < c.right() && my >= c.y() && my < c.bottom();
            g.fill(c.x(), c.y(), c.right(), c.bottom(), deep ? (hover ? 0xFF2E2440 : 0xFF1E182C) : (hover ? 0xFF262A34 : 0xFF181B22));
            Golden.outline(g, c.x(), c.y(), c.right(), c.bottom(), 0xFF000000 | (deep ? DEEP : hover ? Golden.TEXT : 0x5A6690));
            if (hover) {
                Golden.outline(g, c.x() + 1, c.y() + 1, c.right() - 1, c.bottom() - 1, 0x60FFFFFF);
            }
            int x = c.x() + Golden.S5;
            int w = c.w() - 2 * Golden.S5;
            int y = c.y() + Golden.S5;
            Golden.gem(g, x + 3, y + 4, 3);
            g.drawString(font, Component.translatable(deep ? "gui.skyabyss.dungeon.deep.dive" : "gui.skyabyss.dungeon.deep.normal"), x + 10, y,
                    deep ? DEEP : Golden.TEXT, false);
            y += Golden.ROW + Golden.S3;
            List<String> keys = deep ? List.of("gui.skyabyss.dungeon.deep.dive_desc1", "gui.skyabyss.dungeon.deep.dive_desc2", "gui.skyabyss.dungeon.deep.dive_desc3")
                    : List.of("gui.skyabyss.dungeon.deep.normal_desc");
            for (String k : keys) {
                for (FormattedCharSequence line : TextWrap.split(font, Component.literal("・").append(Component.translatable(k)), w)) {
                    g.drawString(font, line, x, y, Golden.SOFT, false);
                    y += 10;
                }
                y += 2;
            }
            if (deep) {
                y += 3;
                for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("gui.skyabyss.dungeon.deep.party"), w)) {
                    g.drawString(font, line, x, y, Golden.DIM, false);
                    y += 10;
                }
            }
        }
        fit.end(g);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        for (boolean deep : new boolean[] {false, true}) {
            Golden.Box c = card(deep);
            if (button == 0 && mx >= c.x() && mx < c.right() && my >= c.y() && my < c.bottom()) {
                PacketDistributor.sendToServer(new DeepChoice.Choose(data.dungeon(), deep));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), deep ? 0.7f : 1.0f, 0.5f));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(sx, sy, button);
    }
}
