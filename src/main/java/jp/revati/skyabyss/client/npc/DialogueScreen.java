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
    private static final int NAME = 0xFFFFF6EA;
    private static final int SOFT = 0xFF9A929C;
    private static final int GOLD = 0xFFF89CD2;
    private static final int PANEL = 0xF2101318;
    private static final int EDGE = 0xFFC9A85C;
    private static final int EDGE_IN = 0xFF1C1A26;
    private static final int HOVER = 0x30E45A9C;
    // 行は 13 (docs/design/56)
    private static final int LINE_H = jp.revati.skyabyss.client.ui.Golden.ROW;
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
    // 開いた時 (帯と顔の札がせり上がり、名前の短冊が押される。docs/design/56 の 2.1「動き」)
    private long openedAtD;
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
        // 組み方は会話の「立ち札」(UI の 3 周目で会話 A。docs/design/56 の 2.1): 下に窓の幅いっぱいの黒い帯、
        // 左に顔の札 (傾けて帯の上へはみ出す)、真ん中に本文、右に選択肢の短冊を縦に。中身の幅は 460 まで
        panelW = Math.min(460, width - 16);
        panelX = (width - panelW) / 2;
        textX = faceColW() + GAP;
        textW = panelW - textX - GAP - OPT_W;
        lines.clear();
        lines.addAll(wrapBalanced(Component.translatable(show.textKey()).getString(), textW - ScrollPanel.GUTTER, font));
        totalChars = lines.stream().mapToInt(String::length).sum();
        optionLines.clear();
        for (NpcNetwork.OptionView o : options()) {
            optionLines.add(wrapBalanced(Component.translatable(o.textKey()).getString(), OPT_W - OPT_PAD - 6, font));
        }
        int textH = lines.size() * LINE_H;
        int natural = Math.max(face + 2 * PAD + 8, Math.max(textH, optionsH()) + 2 * PAD);
        panelH = Math.min(natural, Math.max(face + 2 * PAD + 8, height - 20));
        panelY = height - panelH - 4;
        selected = Mth.clamp(selected, 0, Math.max(0, optionLines.size() - 1));
        body.setViewport(panelX + textX, panelY + PAD, textW, panelH - 2 * PAD);
        body.setContentHeight(textH);
    }

    // 帯の中の余白、欄のあいだ、選択肢の列の幅と、短冊の中の字の左の余白
    private static final int PAD = 10;
    private static final int GAP = 12;
    private static final int OPT_W = 124;
    private static final int OPT_PAD = 12;

    private int optionH(int i) {
        return Math.max(OPTION_H + 2, optionLines.get(i).size() * LINE_H + 3);
    }

    private int optionsH() {
        int h = 0;
        for (int i = 0; i < optionLines.size(); i++) {
            h += optionH(i) + 4;
        }
        return h;
    }

    // 選択肢の短冊の左上 {x, y} (右の列に縦。下へ少しずつ左へずらす)
    private int[] optionAtXY(int i) {
        int y = panelY + PAD;
        for (int k = 0; k < i; k++) {
            y += optionH(k) + 4;
        }
        return new int[] {panelX + panelW - OPT_W - i * 3, y};
    }

    // 本文の欄 (真ん中。目盛りつきで送る)
    private final ScrollPanel body = new ScrollPanel(LINE_H * 2);

    // 顔の欄 (左の札): 顔と余白
    private int faceColW() {
        return face + 12;
    }

    private int faceColH() {
        return face + 40;
    }

    // 選択肢は右の列にいつも見えている
    private void revealOption(int i) {
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
        // ぼかさず、下を少し暗くするだけ (相手の顔と景色が見えるように)。切り絵なので段なしの一枚
        g.fill(0, height * 3 / 5, width, height, 0x700E0D14);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = Minecraft.getInstance().font;
        long nowD = net.minecraft.Util.getMillis();
        if (openedAtD == 0) {
            openedAtD = nowD;
        }
        float rise = (1f - jp.revati.skyabyss.rpg.client.skill.SkillStage.out((nowD - openedAtD) / 220f)) * 30f;
        g.pose().pushPose();
        g.pose().translate(0, rise, 0);
        int x = panelX;
        int y = panelY;
        // 黒い帯 (窓の幅いっぱい。上の辺は手で決めた点で、少しずつ斜めに切る)
        int bw = width;
        jp.revati.skyabyss.client.ui.Paper.cut(g, "talk_band", new float[] {-4, y + 2, bw * 0.38f, y - 2, bw * 0.81f, y + 3, bw + 4, y - 1, bw + 4, height + 4, -4, height + 4},
                jp.revati.skyabyss.client.ui.Paper.BLACK, jp.revati.skyabyss.client.ui.Paper.PINK_LO, 0, -3);
        // 顔の札 (傾けて、帯の上へはみ出す)
        int cw = faceColW();
        int ch = faceColH();
        float ccx = x + cw / 2f;
        float ccy = y + ch / 2f - 18;
        jp.revati.skyabyss.client.ui.Paper.cut(g, "talk_card", jp.revati.skyabyss.client.ui.Paper.rotRect(ccx, ccy, cw, ch, -4), jp.revati.skyabyss.client.ui.Paper.CREAM,
                jp.revati.skyabyss.client.ui.Paper.BLACK_LO, 3, 3);
        int fx = Math.round(ccx - face / 2f);
        int fy = Math.round(ccy - ch / 2f + 6);
        g.fill(fx - 1, fy - 1, fx + face + 1, fy + face + 1, 0xFF0E0D14);
        // 画面の画素に合わせた整数倍 (facePx 画素 = 絵の 1 画素)。GUI 単位に直した倍率で拡大する
        float k = facePx / (float) Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        g.pose().pushPose();
        g.pose().translate(fx, fy, 0);
        g.pose().scale(k, k, 1);
        g.blit(faceTexture(), 0, 0, 0, 0, PORTRAIT_PX, PORTRAIT_PX, PORTRAIT_PX, PORTRAIT_PX);
        g.pose().popPose();
        // 親しさ (札の上、顔の下に墨で) と名前 (札の足に貼った桃色の短冊)
        int affinity = Minecraft.getInstance().player == null ? 0 : NpcBonds.of(Minecraft.getInstance().player).affinity(show.role());
        int tier = Affinity.tier(affinity);
        jp.revati.skyabyss.client.ui.Paper.inkBegin();
        jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.npc.tier." + tier), Math.round(ccx), fy + face + 3, cw - 6, SOFT, false);
        drawMarks(g, Math.round(ccx), fy + face + 13, tier);
        jp.revati.skyabyss.client.ui.Paper.inkEnd();
        Component name = Component.literal(Component.translatable("entity." + show.role().replace(':', '.')).getString());
        int sw = Math.max(cw + 4, Math.min(cw + 40, font.width(name) + 18));
        int sy = fy + face + 23;
        // 名前の短冊は、開いて少しあとに大きく浮いてから押される
        float press = jp.revati.skyabyss.rpg.client.skill.SkillStage.back((nowD - openedAtD - 120) / 240f);
        float ps = 1f + (1f - press) * 0.4f;
        g.pose().pushPose();
        g.pose().translate(x - 4 + sw / 2f, sy + 7, 0);
        g.pose().scale(ps, ps, 1f);
        g.pose().translate(-(x - 4 + sw / 2f), -(sy + 7), 0);
        jp.revati.skyabyss.client.ui.Paper.slip(g, x - 4, sy, sw, 14, jp.revati.skyabyss.client.ui.Paper.PINK, jp.revati.skyabyss.client.ui.Paper.CREAM, jp.revati.skyabyss.client.ui.Paper.REST);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, name, x + 6, sy + 3, sw - 14, NAME, false);
        g.pose().popPose();
        // 本文 (一字ずつ。帯にじかに生成りの字で)
        int tx = x + textX;
        body.begin(g);
        int ty = body.top();
        int left = (int) revealed;
        for (String line : lines) {
            if (left <= 0) {
                break;
            }
            String part = line.length() <= left ? line : line.substring(0, left);
            g.drawString(font, part, tx, jp.revati.skyabyss.client.ui.Golden.textY(ty, LINE_H), TEXT, false);
            left -= line.length();
            ty += LINE_H;
        }
        body.end(g);
        body.drawBar(g, EDGE_IN, 0x88E45A9C);
        boolean more = show.hasNext() && show.options().isEmpty();
        if (fullyShown() && !more) {
            // 選択肢 (右の列に短冊を縦に。選んでいるものは桃色で浮く)
            List<NpcNetwork.OptionView> opts = options();
            int hover = optionAt(mouseX, mouseY);
            if (hover >= 0) {
                selected = hover;
            }
            for (int i = 0; i < opts.size(); i++) {
                List<String> ol = optionLines.get(i);
                int[] p = optionAtXY(i);
                int h = optionH(i);
                // 窓の下に入りきらない選択肢は描かない (数字キー・上下のキーでは選べる)
                if (p[1] + h > height) {
                    break;
                }
                boolean on = i == selected;
                int st = on ? jp.revati.skyabyss.client.ui.Paper.HOVER : jp.revati.skyabyss.client.ui.Paper.REST;
                int[] d = jp.revati.skyabyss.client.ui.Paper.slip(g, p[0], p[1], OPT_W, h, on ? jp.revati.skyabyss.client.ui.Paper.PINK : jp.revati.skyabyss.client.ui.Paper.CREAM,
                        on ? jp.revati.skyabyss.client.ui.Paper.CREAM : jp.revati.skyabyss.client.ui.Paper.PINK, st);
                NpcNetwork.OptionView o = opts.get(i);
                int color = on ? 0xFFFFF6EA : switch (o.kind()) {
                    case "quest" -> 0xFF000000 | jp.revati.skyabyss.client.ui.Paper.INK_HEAD;
                    case "leave" -> 0xFF000000 | jp.revati.skyabyss.client.ui.Paper.INK_SOFT;
                    default -> 0xFF000000 | jp.revati.skyabyss.client.ui.Paper.INK;
                };
                // 番号はいつも出す (数字キーで選べるのが分かるように)。クエストに関わるものは ◆、物を渡すものは ◇ を添える
                String mark = (i + 1) + switch (o.kind()) {
                    case "quest" -> "◆";
                    case "give" -> "◇";
                    default -> ".";
                };
                int lx = p[0] + d[0] + OPT_PAD;
                int ly = p[1] + d[1] + 2;
                for (int l = 0; l < ol.size(); l++) {
                    g.drawString(font, (l == 0 ? mark + " " : "   ") + ol.get(l), lx, jp.revati.skyabyss.client.ui.Golden.textY(ly, LINE_H), color, false);
                    ly += LINE_H;
                }
            }
        }
        // 続き (本文の欄の右下)
        if (fullyShown() && more) {
            long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            if ((t / 10) % 2 == 0) {
                g.drawString(font, "▼", x + textX + textW - 12, y + panelH - PAD - 8, NAME, false);
            }
        }
        g.pose().popPose();
    }

    private ResourceLocation faceTexture() {
        String path = show.role().substring(show.role().indexOf(':') + 1);
        // 土地の町の人 (はじまりの島の渡し守も) は自分の肖像。なければ体の肖像 (町の案内人 = town_guide)、それもなければ都の町の人の肖像を借りる (town/RegionFolk)
        if (path.startsWith("guest_") && !hasFace(path)) {
            String id = path.substring(6);
            String body = "guest_" + jp.revati.skyabyss.town.RegionFolk.body(id);
            path = hasFace(body) ? body : "guest_" + jp.revati.skyabyss.town.RegionFolk.lookOf(id);
        }
        return face(path);
    }

    private ResourceLocation face(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/npc/face/" + path + "_" + show.face() + ".png");
    }

    private boolean hasFace(String path) {
        return Minecraft.getInstance().getResourceManager().getResource(face(path)).isPresent();
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
        g.drawCenteredString(font, sb.toString(), cx, y, 0xFFFFF6EA);
    }

    private int optionAt(double mx, double my) {
        if (!fullyShown() || (show.hasNext() && show.options().isEmpty())) {
            return -1;
        }
        for (int i = 0; i < optionLines.size(); i++) {
            int[] p = optionAtXY(i);
            if (mx >= p[0] && mx < p[0] + OPT_W && my >= p[1] && my < p[1] + optionH(i) + 2) {
                return i;
            }
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
