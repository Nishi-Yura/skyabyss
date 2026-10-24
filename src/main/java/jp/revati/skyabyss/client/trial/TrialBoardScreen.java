package jp.revati.skyabyss.client.trial;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialBoardNet;
import jp.revati.skyabyss.trial.TrialCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// 試練の板の画面 (docs/design/29 の 2.7・4.2)。暗い石板に画素の枠。二つの頁:
//   いまの三つ : 引いた三つの試練。枠 (歩・智・闘) の絵・名前・類・段・修飾の札。稀は金の縁、極は赤紫の縁。極のうち一つは「？」(入ってから分かる)
//   図鑑       : その巨神の試練ごとに、済んだ回数・任意の目標の数・出会った修飾の数。まだ引いていないものは「？」。称号
// Tab で頁を替える。Esc で閉じる。見せるだけで、決めるのはサーバー
public class TrialBoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/trial/icons.png");
    private static final int SHEET_W = 256;
    private static final int SHEET_H = 96;

    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;
    private static final int GOOD = 0x7BD66B;
    private static final int RARE = 0xF2C14E;
    private static final int EXTREME = 0xC25BD6;
    private static final int COMMON = 0x4A6A72;
    private static final int ABYSS = 0xD8343C;

    private static final int W = 360;
    private static final int H = 252;

    private static final int ABYSS_W = 118;
    private static final int CRAFT_W = 86;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private TrialBoardNet.Open data;
    private int tab;
    private int age;

    public TrialBoardScreen(TrialBoardNet.Open data) {
        super(Component.translatable("gui.skyabyss.trial.title"));
        this.data = data;
    }

    public void update(TrialBoardNet.Open open) {
        this.data = open;
    }

    // 試験・撮影のため
    public void selectTab(int t) {
        tab = Mth.clamp(t, 0, 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_LEFT) {
            tab = 1 - tab;
            return true;
        }
        if (key == GLFW.GLFW_KEY_1) {
            tab = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_2) {
            tab = 1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        int px = 0;
        int py = 0;
        if (button == 0 && my >= py + 34 && my < py + 50) {
            for (int t = 0; t < 2; t++) {
                int tx = px + 12 + t * 110;
                if (mx >= tx && mx < tx + 104) {
                    tab = t;
                    return true;
                }
            }
            // 歌片を作る (祠の炉。足りるかはサーバーが確かめる)
            int cx = px + W - 12 - ABYSS_W - 6 - CRAFT_W;
            if (mx >= cx && mx < cx + CRAFT_W) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.CraftShard(data.titan()));
                return true;
            }
            // 深淵のつまみ (転生 3 回以上の人だけ押せる。グループごと。サーバーが確かめる)
            int ax = px + W - 12 - ABYSS_W;
            if (mx >= ax && mx < ax + ABYSS_W) {
                if (data.abyssOpen()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.AbyssToggle(data.titan(), !data.abyssOn()));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
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

    // 絵の番号 (icons.png。8 列 × 3 行、32 px)
    private static final int I_WALK = 0, I_WIT = 1, I_FIGHT_SLOT = 2, I_LAYOUT = 3, I_NUMBER = 4, I_ENEMY = 5, I_HAZARD = 6, I_GOAL = 7;
    private static final int I_KIND = 8, I_QUIET = 15, I_EXTREME = 16, I_UNKNOWN = 17;

    private void icon(GuiGraphics g, int index, int x, int y, int size, float alpha) {
        int u = (index % 8) * 32;
        int v = (index / 8) * 32;
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(ICONS, x, y, size, size, u, v, 32, 32, SHEET_W, SHEET_H);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static int slotIcon(Trial.Slot s) {
        return switch (s) {
            case WALK -> I_WALK;
            case WIT -> I_WIT;
            case FIGHT -> I_FIGHT_SLOT;
        };
    }

    private static int rarityColor(int r) {
        return r >= 3 ? ABYSS : r == 2 ? EXTREME : r == 1 ? RARE : COMMON;
    }

    static String stars(int tier) {
        return jp.revati.skyabyss.trial.TrialChips.stars(tier);
    }

    // 札の文
    static Component chipText(TrialBoardNet.Chip c) {
        return jp.revati.skyabyss.trial.TrialChips.text(c);
    }

    static int chipIcon(TrialBoardNet.Chip c) {
        if (c.hidden()) {
            return I_UNKNOWN;
        }
        return switch (c.type()) {
            case "layout" -> I_LAYOUT;
            case "number" -> I_NUMBER;
            case "enemy" -> I_ENEMY;
            case "hazard" -> I_HAZARD;
            case "goal" -> I_GOAL;
            case "extreme", "abyss" -> I_EXTREME;
            case "quiet" -> I_QUIET;
            default -> I_UNKNOWN;
        };
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        g.fill(0, 0, width, height, argb(0.80f, BG));
        // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換)。論理の (0, 0) に置く
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        int px = 0;
        int py = 0;
        frame(g, px, py, W, H, 0x3A4A58, PANEL);
        // 題字
        TextStyle.HEADING.drawLeft(g, font, Component.translatable("gui.skyabyss.trial.title"), px + 12, py + 9, 1f, GOLD);
        Component titan = Component.translatable(TrialCatalog.titanKey(data.titan()));
        Component sub = Component.translatable("gui.skyabyss.trial.cycle", data.cycle());
        g.drawString(font, titan.copy().append("  ").append(sub), px + 120, py + 14, argb(1f, TEAL), false);
        // 頁
        String[] tabs = {"gui.skyabyss.trial.tab.now", "gui.skyabyss.trial.tab.codex"};
        for (int t = 0; t < 2; t++) {
            int tx = px + 12 + t * 110;
            boolean on = tab == t;
            frame(g, tx, py + 34, 104, 16, on ? GOLD : 0x2C3846, on ? 0x2A2618 : CARD);
            Component label = Component.translatable(tabs[t]);
            g.drawString(font, label, tx + 52 - font.width(label) / 2, py + 38, argb(1f, on ? GOLD : DIM), false);
        }
        renderAbyssToggle(g, font, px + W - 12 - ABYSS_W, py + 34, mouseX, mouseY);
        renderCraft(g, font, px + W - 12 - ABYSS_W - 6 - CRAFT_W, py + 34, mouseX, mouseY);
        if (tab == 0) {
            renderNow(g, font, px, py);
        } else {
            renderCodex(g, font, px, py);
        }
        // 足: 順路の記録・歌片・称号
        renderFoot(g, font, px, py);
        fit.end(g);
    }

    // 歌片を作るボタン (祠の東の縁の共鳴の炉)
    private void renderCraft(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean hover = mx >= x && mx < x + CRAFT_W && my >= y && my < y + 16;
        frame(g, x, y, CRAFT_W, 16, hover ? GOLD : 0x4A4A2C, hover ? 0x2A2618 : CARD);
        Component label = Component.translatable("gui.skyabyss.shrine.craft");
        g.drawString(font, label, x + CRAFT_W / 2 - font.width(label) / 2, y + 4, argb(1f, hover ? GOLD : TEAL), false);
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.shrine.craft_tip", jp.revati.skyabyss.trial.TrialBoard.SHARD_MATERIALS,
                    jp.revati.skyabyss.trial.TrialBoard.SHARD_RESONANCE), mx, my);
        }
    }

    // 深淵のつまみ: 閉じている (転生 3 回未満) は灰、選んでいないは暗い縁、選んでいるは赤い縁で脈打つ
    private void renderAbyssToggle(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean open = data.abyssOpen();
        boolean on = data.abyssOn();
        boolean hover = open && mx >= x && mx < x + ABYSS_W && my >= y && my < y + 16;
        float pulse = on ? 0.75f + 0.25f * (float) Math.sin(age * 0.2) : 1f;
        int line = on ? ABYSS : open ? (hover ? 0x8C4A50 : 0x4A2C32) : 0x2C3846;
        frame(g, x, y, ABYSS_W, 16, line, on ? 0x2A1216 : open ? 0x181014 : CARD);
        String key = !open ? "gui.skyabyss.trial.abyss.locked" : on ? "gui.skyabyss.trial.abyss.on" : "gui.skyabyss.trial.abyss.off";
        Component label = Component.translatable(key, jp.revati.skyabyss.abyss.Abyss.OPEN_AT);
        g.drawString(font, label, x + ABYSS_W / 2 - font.width(label) / 2, y + 4, argb(pulse, on ? 0xFF8A90 : open ? 0xC8A0A6 : DIM), false);
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.trial.abyss.tip"), mx, my);
        }
    }

    private void renderNow(GuiGraphics g, Font font, int px, int py) {
        int y = py + 54;
        for (TrialBoardNet.Card card : data.cards()) {
            Trial.Def def = TrialCatalog.get(card.cellId());
            if (def == null) {
                continue;
            }
            int rc = rarityColor(card.rarity());
            frame(g, px + 10, y, W - 20, 48, rc, CARD);
            // 枠の絵と名前
            icon(g, slotIcon(def.slot()), px + 19, y + 5, 18, 1f);
            Component slot = Component.translatable("trial.skyabyss.slot." + def.slot().id);
            g.drawString(font, slot, px + 28 - font.width(slot) / 2, y + 24, argb(1f, DIM), false);
            Component name = Component.translatable(def.nameKey());
            g.drawString(font, name, px + 48, y + 7, argb(1f, GOLD), true);
            // 類・段 (右上)
            Component kind = Component.translatable("trial.skyabyss.kind." + def.kind().id());
            String st = stars(def.tier());
            int kw = font.width(kind);
            int rx = px + W - 20 - 8;
            g.drawString(font, st, rx - font.width(st), y + 7, argb(1f, GOLD), false);
            g.drawString(font, kind, rx - font.width(st) - 8 - kw, y + 7, argb(1f, TEAL), false);
            icon(g, I_KIND + def.kind().ordinal(), rx - font.width(st) - 8 - kw - 14, y + 6, 12, 1f);
            // 一行の説明
            Component desc = Component.translatable(def.descKey());
            g.drawString(font, font.plainSubstrByWidth(desc.getString(), W - 20 - 48 - 12), px + 48, y + 20, argb(1f, DIM), false);
            // 修飾の札
            int cx = px + 48;
            int cy = y + 33;
            if (card.chips().isEmpty()) {
                g.drawString(font, Component.translatable("trial.skyabyss.chip.plain"), cx, cy + 4, argb(1f, DIM), false);
            }
            for (TrialBoardNet.Chip chip : card.chips()) {
                Component text = chipText(chip);
                int cw = font.width(text) + 20;
                if (cx + cw > px + W - 18) {
                    cx = px + 48;
                    cy += 16;
                }
                int col = rarityColor(chip.rarity());
                frame(g, cx, cy, cw, 14, col, 0x0E1118);
                icon(g, chipIcon(chip), cx + 3, cy + 1, 12, 1f);
                g.drawString(font, text, cx + 17, cy + 3, argb(1f, chip.rarity() >= 1 ? col : TEXT), false);
                cx += cw + 4;
            }
            // 済み・図鑑
            if (card.clearedNow()) {
                Component done = Component.translatable("gui.skyabyss.trial.cleared_now");
                g.drawString(font, done, px + W - 28 - font.width(done), y + 20, argb(1f, GOOD), false);
            }
            String badge = switch (card.state()) {
                case 0 -> "gui.skyabyss.trial.state.new";
                case 2 -> "gui.skyabyss.trial.state.cleared";
                case 3 -> "gui.skyabyss.trial.state.all";
                default -> null;
            };
            if (badge != null) {
                Component b = Component.translatable(badge);
                g.drawString(font, b, px + 28 - font.width(b) / 2, y + 34, argb(1f, card.state() == 0 ? GOLD : GOOD), false);
            }
            y += 52;
        }
    }

    private void renderCodex(GuiGraphics g, Font font, int px, int py) {
        int y = py + 56;
        int row = 0;
        for (TrialBoardNet.Row r : data.codex()) {
            Trial.Def def = TrialCatalog.get(r.cellId());
            if (def == null || y > py + H - 54) {
                continue;
            }
            frame(g, px + 10, y, W - 20, 20, r.cleared() > 0 ? 0x3E6B58 : 0x2C3846, CARD);
            icon(g, slotIcon(def.slot()), px + 15, y + 3, 14, r.seen() ? 1f : 0.35f);
            Component name = r.seen() ? Component.translatable(def.nameKey()) : Component.translatable("trial.skyabyss.unknown");
            g.drawString(font, name, px + 34, y + 6, argb(1f, r.seen() ? GOLD : DIM), false);
            if (r.seen()) {
                icon(g, I_KIND + def.kind().ordinal(), px + 150, y + 4, 12, 1f);
                g.drawString(font, stars(def.tier()), px + 166, y + 6, argb(1f, GOLD), false);
                Component n = Component.translatable("gui.skyabyss.trial.codex.cleared", r.cleared());
                g.drawString(font, n, px + 206, y + 6, argb(1f, r.cleared() > 0 ? GOOD : DIM), false);
                if (r.modsTotal() > 0) {
                    int bx = px + 270;
                    int bw = 56;
                    g.fill(bx, y + 8, bx + bw, y + 12, 0xFF0A0C12);
                    g.fill(bx, y + 8, bx + Math.min(bw, bw * r.modsSeen() / r.modsTotal()), y + 12, argb(1f, TEAL));
                    g.drawString(font, r.modsSeen() + "/" + r.modsTotal(), bx + bw + 4, y + 6, argb(1f, DIM), false);
                }
                if (r.goals() > 0) {
                    icon(g, I_GOAL, px + W - 32, y + 4, 12, 1f);
                }
            }
            y += 22;
            row++;
        }
        if (row == 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.trial.codex.empty"), px + 14, y + 4, argb(1f, DIM), false);
        }
    }

    private void renderFoot(GuiGraphics g, Font font, int px, int py) {
        int y = py + H - 40;
        g.fill(px + 10, y - 3, px + W - 10, y - 2, argb(0.5f, 0x3A4A58));
        // 順路の記録と歌片 (docs/design/32 の 6 章)
        long best = data.routeBest() / 20;
        Component record = data.routeClears() <= 0 ? Component.translatable("gui.skyabyss.shrine.record_none")
                : Component.translatable("gui.skyabyss.shrine.record", data.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        g.drawString(font, record, px + 14, y, argb(1f, TEAL), false);
        Component shards = Component.translatable("gui.skyabyss.shrine.shards", data.shards());
        g.drawString(font, shards, px + W - 14 - font.width(shards), y, argb(1f, data.shards() > 0 ? GOOD : GOLD), false);
        MutableComponent titles = Component.translatable("gui.skyabyss.trial.titles");
        if (data.titles().isEmpty()) {
            titles.append(Component.translatable("gui.skyabyss.trial.titles_none"));
        } else {
            for (int i = 0; i < data.titles().size(); i++) {
                titles.append(i > 0 ? "　" : "").append(Component.translatable("trial.skyabyss.title." + data.titles().get(i)));
            }
        }
        // 称号は二行まで (三つ揃うと一行に入らない)
        List<FormattedCharSequence> tl = jp.revati.skyabyss.client.ui.JpWrap.split(font, titles, W - 28);
        for (int i = 0; i < Math.min(2, tl.size()); i++) {
            g.drawString(font, tl.get(i), px + 14, y + 11 + i * 8, argb(1f, GOLD), false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.trial.help"), px + 14, y + 27, argb(1f, DIM), false);
    }

    @Override
    public int[] textBounds() {
        // 文字の範囲は描くときと同じ変換を通した画面の矩形 (論理の座標のままだと、倍率が 1 でない GUI で字が切れる)
        fit.fit(width, height, W, H, 2.5f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
