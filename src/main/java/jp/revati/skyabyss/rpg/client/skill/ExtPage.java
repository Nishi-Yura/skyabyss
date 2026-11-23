package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.rpg.aspect.Aspect;
import jp.revati.skyabyss.rpg.aspect.Aspects;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.net.SkillExtPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// K 画面の 3 ページ目「アスペクト」(docs/design/58 の 3 章)。
// 2026-11-09 の声「アスペクトの画面構成を wynncraft みたいに。オリジナルの見た目で。最初から全部みえてるのちょっとなあ。作り込んだ見た目に」
// 「文字がはみ出たりしてる。スクロール対応していい」:
//   左の脇の列 (0.382): 祭壇。付ける枠 (最大 5) を輪に並べ、真ん中に今の職の印。枠を押すと外す。下に付けた数と集めた数
//   右の主の列 (0.618): 集めた品の棚。「どの祠でも」と祠ごとの段 (一段 8 品) を縦に送る。品は技の絵の札で、名前と効き目は重ねた説明に。
//     まだ持っていない品は「？」の札 (名前も効き目も伏せる。出る所だけ)。持っている品を押すと空いた枠へ付ける
final class ExtPage {
    static final int HIT_SLOT = 2;
    static final int HIT_CARD = 3;
    private static final int GOLD = SkillScreen.GOLD;
    private static final int TEXT = SkillScreen.TEXT;
    private static final int SOFT = SkillScreen.SOFT;
    private static final int DIM = SkillScreen.DIM;
    private static final int PALE = SkillScreen.PALE;
    static final int[] RARITY = {0x6FA8E0, 0xB48AE6, 0xEF8A5C};
    // 段の並び (空 = どの祠でも)
    static final String[] GROUPS = {"", "stone_whale", "tide_shell", "thunder_wing", "ember"};
    // 一行の数 (段の中はレアの行と、エピック・レジェンドの行。どれも 6 まで)
    private static final int PER_ROW = 6;
    // 札 (棚)・枠 (祭壇) の大きさと、祭壇の輪の半径
    private static final int TILE = 20;
    private static final int SLOT = 22;
    private static final int RING = 34;
    private static final int GROUP_HEAD = 12;
    private static final int PIPS = 5;
    private static final int GROUP_GAP = 6;

    private final List<int[]> hits = new ArrayList<>();
    // 棚の中の当たり (中身の座標。送りを引いて見る)
    private final List<Object[]> cards = new ArrayList<>();
    private final ScrollPanel shelf = new ScrollPanel(GROUP_HEAD + TILE + PIPS + GROUP_GAP);
    private int ticks;

    void tick() {
        ticks++;
    }

    // 撮影用 (前の紋の盤の口。いまは何もしない)
    void preselect(int socketIndex, Skill skill) {
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static Golden.Box left(Golden.Box body) {
        return body.minor(true);
    }

    static Golden.Box right(Golden.Box body) {
        return body.major(false);
    }

    static int argb(int rgb) {
        return 0xFF000000 | rgb;
    }

    static int alpha(int rgb, int a) {
        return (a & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    static Component groupName(String shrine) {
        return shrine.isEmpty() ? Component.translatable("gui.skyabyss.aspect.group_shared")
                : Component.translatable("gui.skyabyss.aspect.group_shrine", Component.translatable(jp.revati.skyabyss.trial.TrialCatalog.titanKey(shrine)));
    }

    // その職の段ごとの品 (稀さの順)
    static List<Aspect> group(Job job, String shrine) {
        List<Aspect> out = new ArrayList<>();
        for (Aspect a : Aspects.ofJob(job)) {
            if (a.shrine().equals(shrine)) {
                out.add(a);
            }
        }
        out.sort(java.util.Comparator.comparingInt(Aspect::rarity));
        return out;
    }

    // 品の説明 (名前・稀さ・Lv・変える技・効き目・出る所・次の Lv まで)。持っていなければ伏せる
    static List<Component> tip(SkillData d, Aspect a) {
        List<Component> out = new ArrayList<>();
        int copies = d.aspects.getOrDefault(a.id(), 0);
        int lv = Aspects.levelOf(copies);
        if (lv <= 0) {
            out.add(Component.translatable("gui.skyabyss.aspect.unknown").withColor(RARITY[a.rarity()]));
            out.add(Component.translatable("aspect.skyabyss.rarity." + a.rarity()).append("  ")
                    .append(Component.translatable("aspect.skyabyss.unowned")).withColor(SOFT));
            out.add(where(a).withColor(DIM));
            return out;
        }
        out.add(Component.translatable(a.nameKey()).withColor(RARITY[a.rarity()]));
        out.add(Component.translatable("aspect.skyabyss.rarity." + a.rarity()).append("  ")
                .append(Component.translatable(lv >= Aspects.MAX_LEVEL ? "aspect.skyabyss.level_max" : "aspect.skyabyss.level", lv)).withColor(SOFT));
        out.add(Component.translatable("aspect.skyabyss.skill", Component.translatable(a.skill().nameKey())).withColor(PALE));
        // 技が変わっていても効く (アスペクトは技そのものに付く。変異・刻印・書き換え・秘伝・木の変化のどれでも)
        net.minecraft.world.entity.player.Player me = Minecraft.getInstance().player;
        for (jp.revati.skyabyss.rpg.skill.SkillChanges.Change c : jp.revati.skyabyss.rpg.skill.SkillChanges.of(me, a.skill())) {
            if (c.active()) {
                out.add(Component.translatable("aspect.skyabyss.applies_changed", c.line()).withColor(DIM));
            }
        }
        for (Aspect.Fx f : a.fx()) {
            out.add(Component.translatable("aspect.skyabyss.fx." + f.verb().name(), Math.round(f.at(lv) * 100)).withColor(TEXT));
        }
        out.add(where(a).withColor(DIM));
        int next = Aspects.nextCopies(copies);
        if (next > 0) {
            out.add(Component.translatable("aspect.skyabyss.copies", copies - Aspects.copiesFor(lv), next - Aspects.copiesFor(lv)).withColor(DIM));
        }
        return out;
    }

    static net.minecraft.network.chat.MutableComponent where(Aspect a) {
        return a.shared() ? Component.translatable("aspect.skyabyss.shared")
                : Component.translatable("aspect.skyabyss.shrine", Component.translatable(jp.revati.skyabyss.trial.TrialCatalog.titanKey(a.shrine())));
    }

    // ---------------------------------------------------------------- 描く
    void render(GuiGraphics g, Font font, Minecraft mc, SkillData d, Golden.Box body, int mx, int my) {
        hits.clear();
        cards.clear();
        List<Component> tip = null;
        Job job = Job.byId(d.curJob);
        int open = Aspects.slots(mc.player);
        List<String> worn = java.util.Arrays.asList(d.aspectSlots);

        // ---- 左: 祭壇
        Golden.Box lb = left(body);
        int lx = lb.x() + Golden.S5;
        int lw = lb.w() - 2 * Golden.S5;
        FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.aspect.altar"), lb.cx(), Golden.textY(lb.y() + 2, Golden.ROW), lw, GOLD, false);
        int cx = lb.cx();
        int cy = lb.y() + 2 + Golden.ROW + 4 + RING + SLOT / 2;
        altar(g, cx, cy, job);
        for (int i = 0; i < Aspects.MAX_SLOTS; i++) {
            double ang = Math.toRadians(-90 + 72 * i);
            int sx = cx + (int) Math.round(Math.cos(ang) * RING) - SLOT / 2;
            int sy = cy + (int) Math.round(Math.sin(ang) * RING) - SLOT / 2;
            boolean isOpen = i < open;
            Aspect a = isOpen ? Aspects.get(d.aspectSlots[i]) : null;
            boolean hover = in(mx, my, sx, sy, SLOT, SLOT);
            if (!isOpen) {
                g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF05070F);
                Golden.outline(g, sx, sy, sx + SLOT, sy + SLOT, 0xFF2A2E36);
                String need = i < 4 ? String.valueOf(new int[] {20, 30, 40, 50}[i]) : "転";
                g.drawString(font, need, sx + (SLOT - font.width(need)) / 2 + 1, sy + 7, 0xFF4A505C, false);
                if (hover) {
                    tip = List.of(i < 4 ? Component.translatable("gui.skyabyss.aspect.slot_locked", new int[] {20, 30, 40, 50}[i]).withColor(SOFT)
                            : Component.translatable("gui.skyabyss.aspect.slot_locked_rebirth").withColor(SOFT));
                }
                continue;
            }
            if (a == null) {
                g.fill(sx, sy, sx + SLOT, sy + SLOT, hover ? 0xFF20242C : 0xFF15181E);
                Golden.outline(g, sx, sy, sx + SLOT, sy + SLOT, 0xFF4A5680);
                // 空きの印 (小さな菱)
                diamond(g, sx + SLOT / 2, sy + SLOT / 2, 2, 0xFF3A404C);
                if (hover) {
                    tip = List.of(Component.translatable("gui.skyabyss.aspect.empty").withColor(SOFT),
                            Component.translatable("gui.skyabyss.aspect.empty_hint").withColor(DIM));
                }
            } else {
                int lv = Aspects.level(d, a);
                tile(g, font, sx, sy, SLOT, a, lv, hover, false);
                if (hover) {
                    tip = new ArrayList<>(tip(d, a));
                    tip.add(Component.translatable("gui.skyabyss.aspect.click_remove").withColor(PALE));
                }
            }
            hits.add(new int[] {sx, sy, SLOT, SLOT, HIT_SLOT, i});
        }
        int used = 0;
        for (String s : d.aspectSlots) {
            if (Aspects.get(s) != null) {
                used++;
            }
        }
        int owned = 0;
        int total = job == null ? 0 : Aspects.ofJob(job).size();
        if (job != null) {
            for (Aspect a : Aspects.ofJob(job)) {
                if (Aspects.level(d, a) > 0) {
                    owned++;
                }
            }
        }
        int sy0 = cy + RING + SLOT / 2 + 4;
        FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.aspect.slots", used, open), cx, sy0, lw, SOFT, false);
        if (sy0 + Golden.ROW + 8 <= lb.bottom()) {
            FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.aspect.pity", Math.max(0, Aspects.PITY - d.aspectPity)), cx, sy0 + Golden.ROW, lw, DIM, false);
        }

        // ---- 右: 棚
        Golden.Box rb = right(body);
        int rx = rb.x() + Golden.S5;
        int rw = rb.w() - 2 * Golden.S5;
        Component count = Component.translatable("gui.skyabyss.aspect.collected", owned, total);
        int cw = font.width(count);
        g.drawString(font, count, rx + rw - cw, Golden.textY(rb.y() + 2, Golden.ROW), SOFT, false);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.aspect.title").append(job == null ? Component.empty()
                : Component.literal("・").append(Component.translatable(job.nameKey()))), rx, Golden.textY(rb.y() + 2, Golden.ROW), rw - cw - Golden.S8, GOLD, false);
        int vy = rb.y() + 2 + Golden.ROW + 3;
        g.fill(rx, vy - 2, rx + rw, vy - 1, 0xFF222A48);
        shelf.setViewport(rx, vy, rw, rb.bottom() - 3 - vy);
        int groupH = GROUP_HEAD + 2 * (TILE + PIPS + 1) + GROUP_GAP;
        shelf.setContentHeight(job == null ? 0 : GROUPS.length * groupH - GROUP_GAP);
        int iw = shelf.scrollable() ? shelf.width() : rw;
        int gap = Math.max(1, (iw - PER_ROW * TILE) / (PER_ROW - 1));
        int rowW = PER_ROW * TILE + (PER_ROW - 1) * gap;
        int tx0 = rx + Math.max(0, (iw - rowW) / 2);
        boolean inView = shelf.contains(mx, my);
        shelf.begin(g);
        if (job != null) {
            int y = shelf.top();
            for (String grp : GROUPS) {
                List<Aspect> list = group(job, grp);
                int have = 0;
                for (Aspect a : list) {
                    if (Aspects.level(d, a) > 0) {
                        have++;
                    }
                }
                // 段の見出し: 小さな玉・名前・数・細い線
                diamond(g, tx0 + 2, y + 5, 2, argb(grp.isEmpty() ? 0x8FD0FF : GOLD));
                Component n = groupName(grp);
                String c = have + " / " + list.size();
                g.drawString(font, n, tx0 + 8, y + 1, have > 0 ? TEXT : SOFT, false);
                int nw = font.width(n);
                g.drawString(font, c, tx0 + rowW - font.width(c), y + 1, DIM, false);
                g.fill(tx0 + 8 + nw + 4, y + 5, tx0 + rowW - font.width(c) - 4, y + 6, 0xFF222A48);
                int ty = y + GROUP_HEAD;
                int[] col = new int[2];
                for (int i = 0; i < list.size(); i++) {
                    Aspect a = list.get(i);
                    int row = a.rarity() == Aspect.RARE ? 0 : 1;
                    int tx = tx0 + col[row]++ * (TILE + gap);
                    int tyy = ty + row * (TILE + PIPS + 1);
                    int lv = Aspects.level(d, a);
                    boolean hover = inView && in(mx, my, tx, tyy, TILE, TILE);
                    tile(g, font, tx, tyy, TILE, a, lv, hover, worn.contains(a.id()));
                    if (lv > 0) {
                        pips(g, tx, tyy + TILE + 1, TILE, lv, a.rarity());
                    }
                    if (hover) {
                        tip = new ArrayList<>(tip(d, a));
                        if (lv > 0 && !worn.contains(a.id())) {
                            tip.add(Component.translatable("gui.skyabyss.aspect.click_equip").withColor(PALE));
                        } else if (lv > 0) {
                            tip.add(Component.translatable("gui.skyabyss.aspect.worn").withColor(GOLD));
                        }
                    }
                    cards.add(new Object[] {tx, tyy, a});
                }
                y += groupH;
            }
        }
        shelf.end(g);
        shelf.drawBar(g, 0xFF1A1D24, 0xFF5A6690);
        if (job == null) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.aspect.no_job"), rx, vy + 4, rw, SOFT, false);
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    // 祭壇の地: 枠を結ぶ点の輪・五つの枠へ伸びる細い線・真ん中の職の印
    private void altar(GuiGraphics g, int cx, int cy, Job job) {
        for (int deg = 0; deg < 360; deg += 5) {
            double r = Math.toRadians(deg);
            int px = cx + (int) Math.round(Math.cos(r) * RING);
            int py = cy + (int) Math.round(Math.sin(r) * RING);
            g.fill(px, py, px + 1, py + 1, deg % 15 == 0 ? 0xFF3E4652 : 0xFF262C35);
        }
        for (int i = 0; i < Aspects.MAX_SLOTS; i++) {
            double a = Math.toRadians(-90 + 72 * i);
            for (int t = 10; t < RING - SLOT / 2 - 1; t += 2) {
                int px = cx + (int) Math.round(Math.cos(a) * t);
                int py = cy + (int) Math.round(Math.sin(a) * t);
                g.fill(px, py, px + 1, py + 1, 0xFF2E3540);
            }
        }
        // 真ん中: 二重の菱と、職の木の絵 (ゆっくり光る)
        float pulse = 0.5f + 0.5f * (float) Math.sin(ticks * 0.08);
        diamond(g, cx, cy, 11, 0xFF0A0E1E);
        diamond(g, cx, cy, 10, alpha(GOLD, 60 + (int) (60 * pulse)));
        diamond(g, cx, cy, 9, 0xFF14171D);
        if (job != null && job.tree != null) {
            SkillHud.drawIcon(g, job.tree.iconIndex(), cx - 6, cy - 6, 12);
        } else {
            Golden.gem(g, cx, cy, 3);
        }
    }

    // 品の札: 地・稀さの縁 (レジェンドは二重で光る)・技の絵。持っていなければ「？」
    private void tile(GuiGraphics g, Font font, int x, int y, int s, Aspect a, int lv, boolean hover, boolean worn) {
        tile(g, font, x, y, s, a, lv, hover, worn, ticks);
    }

    static void tile(GuiGraphics g, Font font, int x, int y, int s, Aspect a, int lv, boolean hover, boolean worn, int ticks) {
        int col = RARITY[a.rarity()];
        if (lv <= 0) {
            g.fill(x, y, x + s, y + s, hover ? 0xFF181B21 : 0xFF0E1014);
            Golden.outline(g, x, y, x + s, y + s, alpha(col, 70));
            g.drawString(font, "?", x + (s - font.width("?")) / 2 + 1, y + (s - 8) / 2 + 1, alpha(col, 140), false);
            return;
        }
        g.fill(x, y, x + s, y + s, hover ? 0xFF2A2F3A : 0xFF1A1D24);
        // 地の下半分に稀さの色を薄く
        g.fillGradient(x + 1, y + s / 2, x + s - 1, y + s - 1, alpha(col, 0), alpha(col, 70));
        Golden.outline(g, x, y, x + s, y + s, argb(col));
        if (a.rarity() == Aspect.LEGENDARY) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(ticks * 0.15 + x * 0.1);
            Golden.outline(g, x - 1, y - 1, x + s + 1, y + s + 1, alpha(col, 70 + (int) (110 * pulse)));
        }
        int icon = s - 4;
        // 今の形の絵 (変異していれば変異の絵)。刻印・書き換え・秘伝はその色の縁
        net.minecraft.world.entity.player.Player me = Minecraft.getInstance().player;
        SkillHud.drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(me, a.skill()), x + 2, y + 2, icon);
        SkillHud.changeFrame(g, me, a.skill(), x + 2, y + 2, icon);
        if (worn) {
            // 付けている印: 左上の金の三角
            for (int i = 0; i < 5; i++) {
                g.fill(x + 1, y + 1 + i, x + 6 - i, y + 2 + i, argb(GOLD));
            }
        }
    }

    // Lv の点 (5 つ。持っている Lv だけ稀さの色)
    static void pips(GuiGraphics g, int x, int y, int w, int lv, int rarity) {
        int step = 4;
        int x0 = x + (w - (Aspects.MAX_LEVEL * step - 1)) / 2;
        for (int i = 0; i < Aspects.MAX_LEVEL; i++) {
            g.fill(x0 + i * step, y, x0 + i * step + 3, y + 2, i < lv ? argb(RARITY[rarity]) : 0xFF222A48);
        }
    }

    private static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    // ---------------------------------------------------------------- 押す・送る
    boolean click(double mx, double my, SkillData d, Minecraft mc, Consumer<Component> notice, Runnable click) {
        if (shelf.mouseClicked(mx, my, 0)) {
            return true;
        }
        for (int[] h : hits) {
            if (in(mx, my, h[0], h[1], h[2], h[3]) && h[4] == HIT_SLOT) {
                if (Aspects.get(d.aspectSlots[h[5]]) != null) {
                    PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.REMOVE, "", h[5], 0));
                    click.run();
                }
                return true;
            }
        }
        if (!shelf.contains(mx, my)) {
            return false;
        }
        for (Object[] c : cards) {
            if (!in(mx, my, (int) c[0], (int) c[1], TILE, TILE)) {
                continue;
            }
            Aspect a = (Aspect) c[2];
            if (Aspects.level(d, a) <= 0 || java.util.Arrays.asList(d.aspectSlots).contains(a.id())) {
                return true;
            }
            int open = Aspects.slots(mc.player);
            int free = -1;
            for (int i = 0; i < open; i++) {
                if (Aspects.get(d.aspectSlots[i]) == null) {
                    free = i;
                    break;
                }
            }
            if (free < 0) {
                notice.accept(Component.translatable("message.skyabyss.aspect.equip_full"));
                return true;
            }
            PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.PLACE, a.id(), free, 0));
            click.run();
            return true;
        }
        return false;
    }

    boolean scroll(double mx, double my, double delta, SkillData d, Golden.Box body) {
        return shelf.mouseScrolled(mx, my, delta);
    }

    boolean drag(double mx, double my, int button, double dx, double dy) {
        return shelf.mouseDragged(mx, my, button, dx, dy);
    }

    boolean release(double mx, double my, int button) {
        return shelf.mouseReleased(mx, my, button);
    }
}
