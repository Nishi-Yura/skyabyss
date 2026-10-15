package jp.revati.skyabyss.client.npc;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
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

// 会話の画面。下に横長の枠: 左に顔 (32px 密度の肖像)、名前、親しさ。右に本文 (一字ずつ現れる) と選択肢。
// 本文は日本語の禁則を守って折り返す (伝承の書と同じ LoreWrap)。ゲームは止めない (マルチでも同じ)。
// 操作: クリック / 数字キー / ↑↓ と Enter・Space。本文の途中でクリックすると全文を出す。Esc で立ち去る
public class DialogueScreen extends Screen {
    private static final int TEXT = 0xFFE9E3D4;
    private static final int NAME = 0xFF7FD6D0;
    private static final int SOFT = 0xFF9A9AAE;
    private static final int GOLD = 0xFFF2C14E;
    private static final int PANEL = 0xE6101318;
    private static final int EDGE = 0xFF2E5552;
    private static final int EDGE_IN = 0xFF17302F;
    private static final int HOVER = 0x402E8F88;
    private static final int LINE_H = 11;
    private static final int OPTION_H = 13;
    private static final int FACE = 40;
    // 1 tick に出す字の数 (2 = 毎秒 40 字)
    private static final float CHARS_PER_TICK = 2.0f;

    private NpcNetwork.Show show;
    private final List<String> lines = new ArrayList<>();
    private final List<List<String>> optionLines = new ArrayList<>();
    private int totalChars;
    private float revealed;
    private int selected;
    private boolean closedByServer;
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
        panelW = Math.min(400, width - 16);
        textX = 16 + FACE + 14;
        textW = panelW - textX - 12;
        lines.clear();
        lines.addAll(LoreWrap.wrap(Component.translatable(show.textKey()).getString(), textW, font::width));
        totalChars = lines.stream().mapToInt(String::length).sum();
        optionLines.clear();
        for (NpcNetwork.OptionView o : options()) {
            optionLines.add(LoreWrap.wrap(Component.translatable(o.textKey()).getString(), textW - 14, font::width));
        }
        int optionsH = 0;
        for (List<String> ol : optionLines) {
            optionsH += Math.max(OPTION_H, ol.size() * LINE_H + 2);
        }
        int textH = lines.size() * LINE_H;
        panelH = Math.max(FACE + 44, 10 + textH + 8 + optionsH + 10);
        panelX = (width - panelW) / 2;
        panelY = height - panelH - 10;
        selected = Mth.clamp(selected, 0, Math.max(0, optionLines.size() - 1));
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
        int fx = x + 12;
        int fy = y + 10;
        g.fill(fx - 2, fy - 2, fx + FACE + 2, fy + FACE + 2, 0xFF0A0C10);
        g.renderOutline(fx - 2, fy - 2, FACE + 4, FACE + 4, EDGE);
        g.blit(faceTexture(), fx, fy, FACE, FACE, 0, 0, 80, 80, 80, 80);
        // 名前と親しさ
        Component name = Component.translatable("entity." + show.role().replace(':', '.'));
        g.drawCenteredString(font, name, fx + FACE / 2, fy + FACE + 6, NAME);
        int affinity = Minecraft.getInstance().player == null ? 0 : NpcBonds.of(Minecraft.getInstance().player).affinity(show.role());
        int tier = Affinity.tier(affinity);
        g.drawCenteredString(font, Component.translatable("gui.skyabyss.npc.tier." + tier), fx + FACE / 2, fy + FACE + 17, SOFT);
        drawMarks(g, fx + FACE / 2, fy + FACE + 28, tier);
        // 本文 (一字ずつ)
        int tx = x + textX;
        int ty = y + 10;
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
        ty = y + 10 + lines.size() * LINE_H + 8;
        if (!fullyShown()) {
            return;
        }
        // 続き
        if (show.hasNext() && show.options().isEmpty()) {
            long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            if ((t / 10) % 2 == 0) {
                g.drawString(font, "▼", x + panelW - 16, y + panelH - 14, NAME, false);
            }
            return;
        }
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
            String mark = switch (o.kind()) {
                case "quest" -> "◆";
                case "give" -> "◇";
                default -> (i + 1) + ".";
            };
            g.drawString(font, mark, tx, ty, i == selected ? color : (color & 0x00FFFFFF) | 0xB0000000, false);
            int ly = ty;
            for (String l : ol) {
                g.drawString(font, l, tx + 14, ly, i == selected ? color : (color & 0x00FFFFFF) | 0xC8000000, false);
                ly += LINE_H;
            }
            ty += h;
        }
    }

    private ResourceLocation faceTexture() {
        String path = show.role().substring(show.role().indexOf(':') + 1);
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
        int ty = panelY + 10 + lines.size() * LINE_H + 8;
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
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
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
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            selected = (selected + 1) % Math.max(1, n);
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
        if (!closedByServer) {
            PacketDistributor.sendToServer(new NpcNetwork.Closed());
        }
        super.onClose();
    }
}
