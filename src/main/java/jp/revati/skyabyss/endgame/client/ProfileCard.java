package jp.revati.skyabyss.endgame.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.rebirth.TitleTagClient;
import jp.revati.skyabyss.client.ui.FitText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 持ち物の画面の右上 (クラフトの枠を消して空いたところ。client/InventoryCraftHide) の「プロフィール」の札。押すとプロフィールの画面 (ProfileScreen)。
//   札には「プロフィール」と、いま付けている称号 (無ければ「称号なし」)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ProfileCard extends AbstractButton {
    // 持ち物の絵の左上から。枠のあったところ (2×2 の枠・矢印・できる物) を全部覆う (UI の皮を使わないときは枠の絵が残るため)
    public static final int X = 96;
    public static final int Y = 14;
    public static final int W = 76;
    public static final int H = 42;

    private final InventoryScreen screen;

    private ProfileCard(InventoryScreen screen) {
        super(0, 0, W, H, Component.translatable("gui.skyabyss.profile.title"));
        this.screen = screen;
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen s) {
            event.addListener(new ProfileCard(s));
        }
    }

    // 札の上か (InventoryCraftHide が枠のあったところのクリックを止めるので、札の上だけは通す)。x, y は持ち物の絵の左上から
    public static boolean over(double x, double y) {
        return x >= X && x < X + W && y >= Y && y < Y + H;
    }

    private void place() {
        setX(screen.getGuiLeft() + X);
        setY(screen.getGuiTop() + Y);
    }

    @Override
    public void onPress() {
        ProfileScreen.open();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        place();
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        place();
        boolean hover = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        g.fill(x, y, x + W, y + H, hover ? 0xFF2A303C : 0xFF1C2029);
        g.renderOutline(x, y, W, H, hover ? 0xFFECE8DC : 0xFF8A7440);
        g.fill(x + 1, y + 1, x + W - 1, y + 2, hover ? 0xFFC9A85C : 0xFF8F8670);
        var font = Minecraft.getInstance().font;
        FitText.drawCentered(g, font, getMessage(), x + W / 2, y + 9, W - 6, hover ? 0xE9C46A : 0xECE8DC, false);
        g.fill(x + 10, y + 20, x + W - 10, y + 21, 0xFF364064);
        Minecraft mc = Minecraft.getInstance();
        Component title = mc.player == null ? null : TitleTagClient.text(mc.player);
        FitText.drawCentered(g, font, title != null ? title : Component.translatable("gui.skyabyss.profile.card_no_title"), x + W / 2, y + 26, W - 6,
                title != null ? TitleTagClient.COLOR : 0x6E7484, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
