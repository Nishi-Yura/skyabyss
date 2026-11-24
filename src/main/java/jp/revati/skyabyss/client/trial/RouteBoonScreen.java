package jp.revati.skyabyss.client.trial;

import jp.revati.skyabyss.client.ui.PickStage;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.trial.RouteBoons;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// 挑戦の加護を選ぶ画面 (trial/RouteBoons)。試練の板と同じ石板の枠に、二枚の札。
// 押す (または 1 / 2) と選んで閉じる。Esc では閉じない (閉じると選び直せず、選び終わるまで仲間も次の部屋へ運ばれないため。
// 残りの秒がたつとサーバーが一つ目に決めて閉じる)。幕の最中に届いたら預かり、幕が終わって画面が空いたら開く。
// 組み方は選ぶ類の「三枚を扇に」(client/ui/PickStage。docs/design/56 の 2.1): 二枚の札を扇に (乗せた札は桃色で持ち上がる)、
// 左上に題と残りの秒、右の黒い紙に手引き
@net.neoforged.fml.common.EventBusSubscriber(modid = jp.revati.skyabyss.SkyAbyss.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class RouteBoonScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int BG = 0x05070F;
    private static final int PANEL = 0x181722;
    private static final int CARD = 0x16151E;
    private static final int GOLD = 0xF89CD2;
    private static final int TEAL = 0xFFF6EA;
    private static final int TEXT = 0xECE4D6;
    private static final int DIM = 0x9A929C;

    private static final int W = PickStage.W;
    private static final int H = PickStage.H;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private final RouteBoons.Boon[] boons;
    private final long until;
    private int hover = -1;
    // 開いてから LOCK_MS は選べない (戦いの手のまま押してしまわないように。2026-11-10 の声「試練終わった後にすぐに選ぶ画面出ちゃうから、間違って押しちゃう」)
    static final long LOCK_MS = 1500;
    private final long opened = System.currentTimeMillis();

    private boolean locked() {
        return System.currentTimeMillis() - opened < LOCK_MS;
    }

    // 幕の最中に届いた見せ方と、その締め切り (System.currentTimeMillis)
    private static RouteBoons.Offer deferred;
    private static long deferredUntil;

    public RouteBoonScreen(RouteBoons.Offer offer) {
        this(offer, System.currentTimeMillis() + offer.seconds() * 1000L);
    }

    private RouteBoonScreen(RouteBoons.Offer offer, long until) {
        super(Component.translatable("gui.skyabyss.boon.title"));
        this.boons = new RouteBoons.Boon[] { RouteBoons.Offer.boon(offer.a()), RouteBoons.Offer.boon(offer.b()) };
        this.until = until;
    }

    // サーバーから: 見せる・閉じる。幕の最中は割り込まず、幕が終わってから開く (締め切りはサーバーと同じ)
    public static void receive(RouteBoons.Offer offer) {
        Minecraft mc = Minecraft.getInstance();
        if (!offer.open()) {
            deferred = null;
            if (mc.screen instanceof RouteBoonScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (RouteBoons.Offer.boon(offer.a()) == null || RouteBoons.Offer.boon(offer.b()) == null) {
            return;
        }
        if (jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            deferred = offer;
            deferredUntil = System.currentTimeMillis() + offer.seconds() * 1000L;
            return;
        }
        deferred = null;
        mc.setScreen(new RouteBoonScreen(offer));
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (deferred == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || System.currentTimeMillis() >= deferredUntil - 1000L) {
            deferred = null;
            return;
        }
        if (mc.screen == null && !jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            RouteBoons.Offer o = deferred;
            deferred = null;
            mc.setScreen(new RouteBoonScreen(o, deferredUntil));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Esc では閉じない (選ぶか、時間切れでサーバーが閉じる)
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void pick(int index) {
        if (locked()) {
            return;
        }
        PacketDistributor.sendToServer(new RouteBoons.Pick(index));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_1 || key == GLFW.GLFW_KEY_KP_1) {
            pick(0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_2 || key == GLFW.GLFW_KEY_KP_2) {
            pick(1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        int i = cardAt(fit.x(screenX), fit.y(screenY));
        if (button == 0 && i >= 0) {
            pick(i);
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    private static int cardAt(double mx, double my) {
        return PickStage.at(mx, my, 2);
    }

    // ---------------------------------------------------------------- 絵
    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
        g.fill(x + 2, y + 2, x + 3, y + h - 2, argb(0.06f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(0.35f, 0x000000));
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        jp.revati.skyabyss.client.ui.Paper.dim(g, width, height);
        fit.fit(width, height, W, H, 3f, 0.5f);
        hover = locked() ? -1 : cardAt(fit.x(screenX), fit.y(screenY));
        fit.begin(g);
        PickStage.backdrop(g, fit, width, height);
        int left = (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
        PickStage.title(g, font, Component.translatable("gui.skyabyss.boon.title"), Component.translatable("gui.skyabyss.boon.choose", left));
        PickStage.hover(hover);
        for (int i = 0; i < 2; i++) {
            RouteBoons.Boon b = boons[i];
            boolean on = hover == i;
            if (!PickStage.deal(g, i, 2)) {
                continue;
            }
            PickStage.card(g, i, 2, on, on);
            int[] in = PickStage.inner(i, 2, on);
            int x = in[0];
            int ny = in[1];
            int w = in[2];
            // 押す数字の菱と名前
            Paper.pin(g, x + 5, ny + 5, 5, !on);
            String n = String.valueOf(i + 1);
            g.drawString(font, n, x + 6 - font.width(n) / 2, ny + 1, argb(1f, 0xFFF6EA), false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(Component.translatable(b.key()).getString()), x + 16, ny + 1, w - 16,
                    argb(1f, on ? 0xFFF6EA : Paper.INK_HEAD), false);
            int ly = ny + Golden.S13 + Golden.S5;
            List<FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.literal(Component.translatable(b.key() + ".desc").getString()), w);
            int bottom = PickStage.center(i, 2, on)[1] + PickStage.cardH() / 2 - 4;
            for (int l = 0; l < lines.size() && ly + Golden.ROW <= bottom; l++) {
                g.drawString(font, lines.get(l), x, Golden.textY(ly, Golden.ROW), argb(1f, on ? 0xFFF6EA : Paper.INK), false);
                ly += Golden.ROW;
            }
            PickStage.undeal(g);
        }
        PickStage.column(g);
        int ry = PickStage.colY(44);
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.boon.help"), PickStage.RIGHT_W)) {
            g.drawString(font, line, PickStage.RIGHT_X, ry, argb(1f, Paper.ON_BLACK_SOFT), false);
            ry += Golden.ROW;
        }
        fit.end(g);
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
