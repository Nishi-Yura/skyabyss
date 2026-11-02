package jp.revati.skyabyss.client.npc;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.lore.LoreWrap;
import jp.revati.skyabyss.npc.Affinity;
import jp.revati.skyabyss.npc.DialogueService;
import jp.revati.skyabyss.npc.NpcBonds;
import jp.revati.skyabyss.npc.NpcNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// 会話の画面。下に横長の枠: 左に顔 (128x128 のドット絵の肖像、整数倍で出す)、名前、親しさ。右に本文 (一字ずつ現れる) と選択肢。
// 本文は日本語の禁則を守って折り返す (伝承の書と同じ LoreWrap)。ゲームは止めない (マルチでも同じ)。
// 操作: クリック / 数字キー / ↑↓ と Enter・Space。本文の途中でクリックすると全文を出す。Esc で立ち去る
public class DialogueScreen extends Screen {
    private static final int TEXT = 0xFFE9E3D4;
    private static final int NAME = 0xFF7FD6D0;
    private static final int SOFT = 0xFF9A9AAE;
    private static final int GOLD = 0xFFF2C14E;
    private static final int PANEL = 0xF2101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int HOVER = 0x402E8F88;
    private static final int LINE_H = 11;
    private static final int OPTION_H = 13;
    // 肖像は 128x128 のドット絵。画面の画素に対して整数倍で、ぼかさず (最近傍) 出す。GUI の倍率が 2 なら等倍、3 なら等倍で小さめ、4 以上は 2 倍
    private static final int PORTRAIT_PX = 128;
    private int face = 64;
    private int facePx = 1;
    // 選択肢の印 (「1◆」) の幅
    private static final int MARK_W = 18;
    // 1 tick に出す字の数 (2 = 毎秒 40 字)
    private static final float CHARS_PER_TICK = 2.0f;

    private NpcNetwork.Show show;
    private final List<String> lines = new ArrayList<>();
    private final List<List<String>> optionLines = new ArrayList<>();
    private int totalChars;
    private float revealed;
    private int selected;
    private boolean closedByServer;
    private boolean closeSent;
    // 選択を送ってから次の節が届くまで、二度押しを受けない
    private boolean waiting;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int textX;
    private int textW;

    public DialogueScreen(NpcNetwork.Show show) {
        super(Component.translatable("gui.skyabyss.npc.dialogue"));
        this.show = show;
    }

    int npcId() {
        return show.npcId();
    }

    void setNode(NpcNetwork.Show next) {
        this.show = next;
        this.waiting = false;
        this.revealed = 0;
        this.selected = 0;
        layout();
    }

    void closeFromServer() {
        closedByServer = true;
        onClose();
    }

    @Override
    protected void init() {
        layout();
    }

    private void layout() {
        Font font = Minecraft.getInstance().font;
        double scale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        facePx = Math.max(1, (int) Math.floor(64 * scale / PORTRAIT_PX + 0.35));
        face = (int) Math.ceil(PORTRAIT_PX * facePx / scale);
        panelW = Math.min(420, width - 16);
        textX = 16 + face + 14;
        textW = panelW - textX - 12;
        lines.clear();
        lines.addAll(wrapBalanced(Component.translatable(show.textKey()).getString(), textW, font));
        totalChars = lines.stream().mapToInt(String::length).sum();
        optionLines.clear();
        for (NpcNetwork.OptionView o : options()) {
            optionLines.add(wrapBalanced(Component.translatable(o.textKey()).getString(), textW - MARK_W, font));
        }
        int optionsH = 0;
        for (List<String> ol : optionLines) {
            optionsH += Math.max(OPTION_H, ol.size() * LINE_H + 2);
        }
        int textH = lines.size() * LINE_H;
        // 本文と選択肢は右の欄に収め、窓の高さを越えるときは目盛りつきで送る (2026-10-22 の部屋の見直し)
        int natural = Math.max(face + 52, 10 + textH + 8 + optionsH + 10);
        panelH = Math.min(natural, Math.max(face + 52, height - 20));
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 10;
        selected = Mth.clamp(selected, 0, Math.max(0, optionLines.size() - 1));
        body.setViewport(panelX + textX - 4, panelY + 10, panelW - textX - 4, panelH - 18);
        body.setContentHeight(textH + 8 + optionsH);
    }

    // 本文と選択肢の欄 (右の欄。目盛りつきで送る)
    private final ScrollPanel body = new ScrollPanel(LINE_H * 2);

    // 選んだ選択肢が欄の中に見えるよう送る
    private void revealOption(int i) {
        int top = lines.size() * LINE_H + 8;
        for (int k = 0; k < i && k < optionLines.size(); k++) {
            top += Math.max(OPTION_H, optionLines.get(k).size() * LINE_H + 2);
        }
        int h = i < optionLines.size() ? Math.max(OPTION_H, optionLines.get(i).size() * LINE_H + 2) : OPTION_H;
        if (top < body.scroll()) {
            body.setScroll(top);
        } else if (top + h > body.scroll() + body.height()) {
            body.setScroll(top + h - body.height());
        }
    }

    // 段落ごとに折り返す。最後の行が「れど。」のように短く残る (幅の 1/4 未満) ときは、行の数を変えずに
    // 幅を少しずつ狭めて、前の行から字を送る (ゲーム画面で「勧めないけ／れど。」と切れていたため)
    static List<String> wrapBalanced(String text, int width, Font font) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n", -1)) {
            List<String> best = LoreWrap.wrap(para, width, font::width);
            int lines = best.size();
            for (int w = width - 4; lines >= 2 && font.width(best.get(lines - 1)) < width / 4 && w > width * 2 / 3; w -= 4) {
                List<String> narrower = LoreWrap.wrap(para, w, font::width);
                if (narrower.size() != lines) {
                    break;
                }
                best = narrower;
            }
            out.addAll(best);
        }
        return out;
    }

    // 画面に出す選択肢。選択肢も続きもない節には「立ち去る」を一つ置く
    private List<NpcNetwork.OptionView> options() {
        if (show.options().isEmpty() && !show.hasNext()) {
            return List.of(new NpcNetwork.OptionView(DialogueService.LEAVE, "gui.skyabyss.npc.leave", "leave"));
        }
        return show.options();
    }

    private boolean fullyShown() {
        return revealed >= totalChars;
    }

    @Override
    public void tick() {
        if (!fullyShown()) {
            int before = (int) revealed;
            revealed = Math.min(totalChars, revealed + CHARS_PER_TICK);
            // 声の代わりに、6 字ごとにごく小さく紙の音
            if ((int) revealed / 6 != before / 6 && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.9f, 0.05f));
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // ぼかさず、下を少し暗くするだけ (相手の顔と景色が見えるように)
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x90000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        int x = panelX;
        int y = panelY;
        // 枠
        g.fill(x, y, x + panelW, y + panelH, PANEL);
        g.renderOutline(x, y, panelW, panelH, EDGE);
        g.renderOutline(x + 2, y + 2, panelW - 4, panelH - 4, EDGE_IN);
        // 顔
        int fx = x + 13;
        int fy = y + 10;
        g.fill(fx - 3, fy - 3, fx + face + 3, fy + face + 3, 0xFF0A0C10);
        g.renderOutline(fx - 3, fy - 3, face + 6, face + 6, EDGE);
        g.renderOutline(fx - 1, fy - 1, face + 2, face + 2, EDGE_IN);
        // 画面の画素に合わせた整数倍 (facePx 画素 = 絵の 1 画素)。GUI 単位に直した倍率で拡大する
        float k = facePx / (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushPose();
        g.pose().translate(fx, fy, 0);
        g.pose().scale(k, k, 1);
        g.blit(faceTexture(), 0, 0, 0, 0, PORTRAIT_PX, PORTRAIT_PX, PORTRAIT_PX, PORTRAIT_PX);
        g.pose().popPose();
        // 名前と親しさ
        Component name = Component.translatable("entity." + show.role().replace(':', '.'));
        jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, name, fx + face / 2, fy + face + 7, face + 18, NAME, true);
        int affinity = Minecraft.getInstance().player == null ? 0 : NpcBonds.of(Minecraft.getInstance().player).affinity(show.role());
        int tier = Affinity.tier(affinity);
        jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.npc.tier." + tier), fx + face / 2, fy + face + 18, face + 18, SOFT, true);
        drawMarks(g, fx + face / 2, fy + face + 29, tier);
        // 本文 (一字ずつ)
        int tx = x + textX;
        // 本文と選択肢は目盛りつきの欄の中 (はみ出すぶんは送る)
        body.begin(g);
        int ty = body.top();
        int left = (int) revealed;
        for (String line : lines) {
            if (left <= 0) {
                break;
            }
            String part = line.length() <= left ? line : line.substring(0, left);
            g.drawString(font, part, tx, ty, TEXT, false);
            left -= line.length();
            ty += LINE_H;
        }
        ty = body.top() + lines.size() * LINE_H + 8;
        boolean more = show.hasNext() && show.options().isEmpty();
        if (fullyShown() && !more) {
            // 選択肢
            List<NpcNetwork.OptionView> opts = options();
            int hover = optionAt(mouseX, mouseY);
            if (hover >= 0) {
                selected = hover;
            }
            for (int i = 0; i < opts.size(); i++) {
                List<String> ol = optionLines.get(i);
                int h = Math.max(OPTION_H, ol.size() * LINE_H + 2);
                if (i == selected) {
                    g.fill(tx - 4, ty - 2, x + panelW - 8, ty + h - 2, HOVER);
                }
                NpcNetwork.OptionView o = opts.get(i);
                int color = switch (o.kind()) {
                    case "quest" -> GOLD;
                    case "give" -> NAME;
                    case "leave" -> SOFT;
                    default -> TEXT;
                };
                // 番号はいつも出す (数字キーで選べるのが分かるように)。クエストに関わるものは ◆、物を渡すものは ◇ を添える
                String mark = (i + 1) + switch (o.kind()) {
                    case "quest" -> "◆";
                    case "give" -> "◇";
                    default -> ".";
                };
                g.drawString(font, mark, tx, ty, i == selected ? color : (color & 0x00FFFFFF) | 0xB0000000, false);
                int ly = ty;
                for (String l : ol) {
                    g.drawString(font, l, tx + MARK_W, ly, i == selected ? color : (color & 0x00FFFFFF) | 0xC8000000, false);
                    ly += LINE_H;
                }
                ty += h;
            }
        }
        body.end(g);
        body.drawBar(g, EDGE_IN, 0x887FD6D0);
        // 続き (欄の外の右下に出す)
        if (fullyShown() && more) {
            long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            if ((t / 10) % 2 == 0) {
                g.drawString(font, "▼", x + panelW - 16, y + panelH - 14, NAME, false);
            }
        }
    }

    private ResourceLocation faceTexture() {
        String path = show.role().substring(show.role().indexOf(':') + 1);
        // 土地の町の人 (はじまりの島の渡し守も) は自分の肖像。なければ体と同じく都の町の人の肖像を借りる (town/RegionFolk)
        if (path.startsWith("guest_") && Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/npc/face/" + path + "_" + show.face() + ".png")).isEmpty()) {
            path = "guest_" + jp.revati.skyabyss.town.RegionFolk.lookOf(path.substring(6));
        }
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/npc/face/" + path + "_" + show.face() + ".png");
    }

    // 親しさの印: 段階の数だけ明るい菱形 (よそよそしいときは赤紫の一つ)
    private void drawMarks(GuiGraphics g, int cx, int y, int tier) {
        Font font = Minecraft.getInstance().font;
        if (tier == 0) {
            g.drawCenteredString(font, "◇", cx, y, 0xFFD9779A);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            sb.append(i <= tier ? "◆" : "◇");
        }
        g.drawCenteredString(font, sb.toString(), cx, y, 0xFF5FB8B0);
    }

    private int optionAt(double mx, double my) {
        if (!fullyShown() || (show.hasNext() && show.options().isEmpty())) {
            return -1;
        }
        if (!body.contains(mx, my)) {
            return -1;
        }
        int ty = body.top() + lines.size() * LINE_H + 8;
        for (int i = 0; i < optionLines.size(); i++) {
            int h = Math.max(OPTION_H, optionLines.get(i).size() * LINE_H + 2);
            if (mx >= panelX + textX - 4 && mx < panelX + panelW - 8 && my >= ty - 2 && my < ty + h - 2) {
                return i;
            }
            ty += h;
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return body.mouseScrolled(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return body.mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return body.mouseReleased(mx, my, button) || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (body.mouseClicked(mx, my, button)) {
            return true;
        }
        if (!fullyShown()) {
            revealed = totalChars;
            return true;
        }
        if (show.hasNext() && show.options().isEmpty()) {
            send(DialogueService.CONTINUE);
            return true;
        }
        int i = optionAt(mx, my);
        if (i >= 0) {
            pick(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        boolean confirm = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER;
        if (!fullyShown()) {
            if (confirm) {
                revealed = totalChars;
            }
            return true;
        }
        if (show.hasNext() && show.options().isEmpty()) {
            if (confirm) {
                send(DialogueService.CONTINUE);
            }
            return true;
        }
        int n = optionLines.size();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            selected = (selected + n - 1) % Math.max(1, n);
            revealOption(selected);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            selected = (selected + 1) % Math.max(1, n);
            revealOption(selected);
            return true;
        }
        if (confirm) {
            pick(selected);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < n) {
            pick(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void pick(int i) {
        List<NpcNetwork.OptionView> opts = options();
        if (i < 0 || i >= opts.size()) {
            return;
        }
        int index = opts.get(i).index();
        if (index == DialogueService.LEAVE) {
            send(DialogueService.LEAVE);
            onClose();
            return;
        }
        send(index);
    }

    private void send(int index) {
        if (waiting) {
            return;
        }
        waiting = true;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.25f));
        }
        PacketDistributor.sendToServer(new NpcNetwork.Choose(show.node(), index));
    }

    @Override
    public void onClose() {
        sendClosed();
        super.onClose();
    }

    // 別の画面 (場面・書など) に差し替えられたときも、サーバーの会話を閉じる (onClose は差し替えでは呼ばれない)
    @Override
    public void removed() {
        sendClosed();
        super.removed();
    }

    private void sendClosed() {
        if (!closedByServer && !closeSent) {
            closeSent = true;
            PacketDistributor.sendToServer(new NpcNetwork.Closed());
        }
    }
}
