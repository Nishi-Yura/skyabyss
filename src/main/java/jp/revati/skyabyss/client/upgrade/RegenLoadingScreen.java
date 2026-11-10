package jp.revati.skyabyss.client.upgrade;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.upgrade.RegenLoading;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// 作り直しのあとの読み込み画面 (upgrade/RegenLoading)。サーバーが「終わり」と言い、手元にもまわりのチャンクが届いて描けるまで閉じない。
// Esc では閉じない。夜空の色に、題・まわりの読み込みの帯・建てている物の数
public class RegenLoadingScreen extends Screen {
    private static final int BG = 0xFF05070F;
    private static final int GOLD = 0xFFE9C46A;
    private static final int TEAL = 0xFF8FD0FF;
    private static final int DIM = 0xFF9AA4C4;
    private static final int RADIUS = 4;

    private static RegenLoading.State last = new RegenLoading.State(true, false, 0, 0);
    private int age;

    public RegenLoadingScreen() {
        super(Component.translatable("gui.skyabyss.regen.title"));
    }

    public static void receive(RegenLoading.State s) {
        last = s;
        Minecraft mc = Minecraft.getInstance();
        if (s.open() && !(mc.screen instanceof RegenLoadingScreen)) {
            mc.setScreen(new RegenLoadingScreen());
        }
    }

    // 手元にまわりのチャンクが届き、立っている所が描けるか
    private static boolean clientReady(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return mc.levelRenderer.isSectionCompiled(mc.player.blockPosition());
    }

    @Override
    public void tick() {
        age++;
        Minecraft mc = Minecraft.getInstance();
        if (last.done() && clientReady(mc)) {
            onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int y = height / 2 - 36;
        TextStyle.HEADING.drawCentered(g, font, title, cx, y, 1f, GOLD & 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.regen.sub"), cx, y + 26, DIM);
        // まわりの読み込みの帯
        int w = Math.min(240, width - 40);
        int bx = cx - w / 2;
        int by = y + 48;
        g.fill(bx - 1, by - 1, bx + w + 1, by + 7, 0xFF2A3342);
        g.fill(bx, by, bx + w, by + 6, 0xFF0C1022);
        int fill = Math.round(w * Mth.clamp(last.percent() / 100f, 0f, 1f));
        g.fill(bx, by, bx + fill, by + 6, TEAL);
        String dots = ".".repeat(1 + (age / 10) % 3);
        Component line = last.builds() > 0 ? Component.translatable("gui.skyabyss.regen.builds", last.builds())
                : Component.translatable("gui.skyabyss.regen.land", last.percent());
        g.drawCenteredString(font, line.copy().append(dots), cx, by + 14, TEAL);
    }
}
