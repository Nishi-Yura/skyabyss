package jp.revati.skyabyss.client.dungeon;

import jp.revati.skyabyss.client.ui.PickStage;
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
import jp.revati.skyabyss.client.ui.Paper;

// 層の門で「ふつうに入る / 深く潜る」を選ぶ画面 (dungeon/DeepChoice)。左右二枚の札: ふつう (石の色) と深く潜る (淵の紫、何が変わるかを三行)。
// 札を押すと入る。Esc で閉じれば入らない
public class DeepChoiceScreen extends Screen {
    // 組み方は選ぶ類の「三枚を扇に」(client/ui/PickStage。docs/design/56 の 2.1): 二枚の札 (ふつう・深く潜る) を扇に、乗せた札は桃色で持ち上がる。
    // 深く潜る札には大きな桃色の菱を留める。右の黒い紙の上に、層の気配と仲間への手引き
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
        fit.fit(width, height, PickStage.W, PickStage.H, 3f);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int sx, int sy, float partialTick) {
        fit.fit(width, height, PickStage.W, PickStage.H, 3f);
        renderBackground(g, sx, sy, partialTick);
        int mx = (int) Math.floor(fit.x(sx));
        int my = (int) Math.floor(fit.y(sy));
        fit.begin(g);
        PickStage.backdrop(g, fit, width, height);
        PickStage.title(g, font, title, null);
        int hot = PickStage.at(mx, my, 2);
        for (int i = 0; i < 2; i++) {
            boolean deep = i == 1;
            boolean lift = i == hot;
            PickStage.card(g, i, 2, lift, lift);
            int[] c = PickStage.center(i, 2, lift);
            if (deep) {
                Paper.pin(g, c[0] + PickStage.CARD_W / 2 - 10, c[1] - PickStage.CARD_H / 2 + 6, 5, !lift);
            }
            int[] in = PickStage.inner(i, 2, lift);
            int x = in[0];
            int y = in[1];
            int w = in[2];
            int head = lift ? PickStage.ON_PINK : deep ? Paper.INK_HEAD : Paper.INK;
            int soft = lift ? PickStage.ON_PINK : Paper.INK_SOFT;
            g.drawString(font, Component.literal(Component.translatable(deep ? "gui.skyabyss.dungeon.deep.dive" : "gui.skyabyss.dungeon.deep.normal").getString()), x, y + 2, head, false);
            g.fill(x, y + 14, x + w, y + 15, lift ? 0xFFFFF6EA : Paper.CREAM_LO);
            y += Golden.ROW + Golden.S5;
            List<String> keys = deep ? List.of("gui.skyabyss.dungeon.deep.dive_desc1", "gui.skyabyss.dungeon.deep.dive_desc2", "gui.skyabyss.dungeon.deep.dive_desc3")
                    : List.of("gui.skyabyss.dungeon.deep.normal_desc");
            for (String k : keys) {
                for (FormattedCharSequence line : TextWrap.split(font, Component.literal("・" + Component.translatable(k).getString()), w)) {
                    g.drawString(font, line, x, y, soft, false);
                    y += 11;
                }
                y += 3;
            }
        }
        // 右の列: 層の気配と、仲間への手引き
        int rx = PickStage.RIGHT_X;
        int ry = 44;
        for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("gui.skyabyss.dungeon.deep.mood", Component.translatable(data.moodName())), PickStage.RIGHT_W)) {
            g.drawString(font, line, rx, ry, PickStage.ON_PINK, false);
            ry += Golden.ROW;
        }
        ry += Golden.S8;
        for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("gui.skyabyss.dungeon.deep.party"), PickStage.RIGHT_W)) {
            g.drawString(font, line, rx, ry, Paper.ON_BLACK_SOFT, false);
            ry += Golden.ROW;
        }
        fit.end(g);
    }

    @Override
    public boolean mouseClicked(double sx, double sy, int button) {
        double mx = fit.x(sx);
        double my = fit.y(sy);
        int i = PickStage.at(mx, my, 2);
        if (button == 0 && i >= 0) {
            boolean deep = i == 1;
            PacketDistributor.sendToServer(new DeepChoice.Choose(data.dungeon(), deep));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), deep ? 0.7f : 1.0f, 0.5f));
            onClose();
            return true;
        }
        return super.mouseClicked(sx, sy, button);
    }
}
