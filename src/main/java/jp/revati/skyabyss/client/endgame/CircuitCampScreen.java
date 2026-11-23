package jp.revati.skyabyss.client.endgame;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.JpWrap;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.PickStage;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.endgame.CircuitCamps;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 巡り路の野営地の画面 (endgame/CircuitCamps。報いの箱を押すと開く)。
// 組み方は選ぶ類の「三枚を扇に」(client/ui/PickStage。docs/design/56 の 2.1) の舞台で、中身は祠の報いの頁と同じく黒い紙にじかに:
//   左上に題と野営地の名。左に階級ごとの段 (階級の名と出やすさは黒の上に階級の色、品は少し傾けた生成りの札。指すと桃色で品の説明)。
//   右の列に入れ替えまでの時間・引き方・決まり・今日の残り・「歩き出す」(歩いているあいだは「やめる」)。下の山の上に神器の出やすさ
public class CircuitCampScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    // 段 (階級 4 + 薬・糧・鐘銭)
    private static final int ROW_X = 16;
    private static final int LABEL_W = 70;
    private static final int TILE = 26;
    private static final int TILE_STEP = 32;
    private static final int ROW_Y = 40;
    private static final int ROW_STEP = 32;
    private static final int BTN_W = 96;
    private static final int BTN_Y = 188;

    private final UiFit fit = new UiFit();
    private final CircuitCamps.View view;
    private ItemStack hovered = ItemStack.EMPTY;

    public CircuitCampScreen(CircuitCamps.View view) {
        super(Component.translatable("gui.skyabyss.camp.title"));
        this.view = view;
    }

    public static void receive(CircuitCamps.View v) {
        Minecraft.getInstance().setScreen(new CircuitCampScreen(v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static int tierRgb(ItemStack s) {
        return s.getItem() instanceof ModGear g ? g.tier().rgb() : 0x9A929C;
    }

    static Component time(int seconds) {
        int h = seconds / 3600;
        return h >= 24 ? Component.translatable("gui.skyabyss.camp.time", h / 24, h % 24) : Component.translatable("gui.skyabyss.camp.time_h", h, seconds / 60 % 60);
    }

    @Override
    protected void init() {
        fit.fit(width, height, PickStage.W, PickStage.H, 3f);
        addRenderableWidget(Paper.tag(Component.translatable(view.running() ? "gui.skyabyss.camp.stop" : "gui.skyabyss.camp.start"), b -> {
            PacketDistributor.sendToServer(view.running() ? new CircuitCamps.Stop() : new CircuitCamps.Start(view.camp()));
            onClose();
        }).bounds(PickStage.RIGHT_X + PickStage.RIGHT_W - BTN_W, BTN_Y, BTN_W, Golden.BTN).primary().build());
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        return super.mouseClicked(fit.x(screenX), fit.y(screenY), button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    // 段ごとの品 (階級 0..3、4 = 薬・糧・鐘銭)
    private List<List<ItemStack>> rows() {
        List<List<ItemStack>> out = new ArrayList<>();
        for (int t = 0; t <= CircuitCamps.TIERS.length; t++) {
            out.add(new ArrayList<>());
        }
        for (ItemStack st : view.pool()) {
            int t = CircuitCamps.TIERS.length;
            if (st.getItem() instanceof ModGear gear) {
                for (int k = 0; k < CircuitCamps.TIERS.length; k++) {
                    if (CircuitCamps.TIERS[k] == gear.tier()) {
                        t = k;
                    }
                }
            }
            out.get(t).add(st);
        }
        return out;
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, PickStage.W, PickStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        PickStage.backdrop(g, fit, width, height);
        PickStage.title(g, font, title, Component.translatable("label.skyabyss.facility.circuit_camp_" + view.camp() + ".sub"));
        // 階級ごとの段
        int total = CircuitCamps.SUPPLY_WEIGHT;
        for (int w : CircuitCamps.WEIGHTS) {
            total += w;
        }
        hovered = ItemStack.EMPTY;
        List<List<ItemStack>> rows = rows();
        for (int t = 0; t < rows.size(); t++) {
            int y = ROW_Y + t * ROW_STEP;
            boolean supply = t == CircuitCamps.TIERS.length;
            int weight = supply ? CircuitCamps.SUPPLY_WEIGHT : CircuitCamps.WEIGHTS[t];
            double pct = weight * 100.0 / total;
            Component name = supply ? Component.translatable("gui.skyabyss.camp.supply")
                    : Component.literal(Component.translatable("gear.skyabyss.tier." + CircuitCamps.TIERS[t].key).getString());
            int rgb = supply ? Golden.TEXT : CircuitCamps.TIERS[t].rgb();
            FitText.draw(g, font, name, ROW_X, y + 4, LABEL_W - 4, rgb, false);
            FitText.draw(g, font, Component.literal(String.format(java.util.Locale.ROOT, pct < 1 ? "%.1f%%" : "%.0f%%", pct)), ROW_X, y + 15, LABEL_W - 4,
                    Paper.ON_BLACK_SOFT, false);
            List<ItemStack> items = rows.get(t);
            for (int i = 0; i < items.size(); i++) {
                int x = ROW_X + LABEL_W + i * TILE_STEP;
                boolean on = mx >= x && mx < x + TILE && my >= y && my < y + TILE;
                float ang = ((t * 5 + i * 3) % 7 - 3) * 1.2f;
                Paper.cut(g, "camp_tile" + t + "_" + i, Paper.rotRect(x + TILE / 2f, y + TILE / 2f, TILE, TILE, ang), on ? Paper.PINK : Paper.CREAM,
                        on ? Paper.BLACK_LO : Paper.PINK_LO, 1, 2);
                g.pose().pushPose();
                g.pose().translate(x + 1, y + 1, 0);
                g.pose().scale(1.5f, 1.5f, 1f);
                g.renderItem(items.get(i), 0, 0);
                g.pose().popPose();
                if (on) {
                    hovered = items.get(i);
                }
            }
            // 神器の段には桃色の菱
            if (!supply && CircuitCamps.TIERS[t] == GearTier.MYTHIC && !items.isEmpty()) {
                Paper.pin(g, ROW_X + LABEL_W + TILE - 1, y, 3, true);
            }
        }
        // 右の列: 入れ替えまでの時間・引き方・決まり・今日の残り
        int rx = PickStage.RIGHT_X;
        int rw = PickStage.RIGHT_W;
        int ry = 40;
        ry = para(g, Component.translatable("gui.skyabyss.camp.pool", time(view.secondsToRotate())), rx, ry, rw, PickStage.ON_PINK) + Golden.S5;
        ry = para(g, Component.translatable("gui.skyabyss.camp.how"), rx, ry, rw, Paper.ON_BLACK_SOFT) + Golden.S5;
        para(g, Component.translatable("gui.skyabyss.camp.rules"), rx, ry, rw, Paper.ON_BLACK_DIM);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.camp.left", view.runsLeft()), rx, BTN_Y - 13, rw,
                view.runsLeft() > 0 ? Paper.ON_BLACK_HI : Paper.ON_BLACK_DIM, false);
        PickStage.hint(g, font, Component.translatable("gui.skyabyss.camp.chance"));
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        fit.end(g);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, screenX, screenY);
        }
    }

    // 折り返した文 (返すのは次の行の y)
    private int para(GuiGraphics g, Component text, int x, int y, int w, int color) {
        for (FormattedCharSequence line : JpWrap.split(font, text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    @Override
    public int[] textBounds() {
        fit.fit(width, height, PickStage.W, PickStage.H, 3f);
        return fit.screenRect(0, 0, PickStage.W, PickStage.H);
    }
}
