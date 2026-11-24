package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.Paper;
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
    // 動き: ページを開くと祭壇が跳ねて立ち、五つの枠が順に、棚の段が順に入る。品を付けると枠に判が押される
    private long shownAt = net.minecraft.Util.getMillis();
    private String[] lastSlots;
    private final long[] slotAt = new long[Aspects.MAX_SLOTS];

    void opened() {
        shownAt = net.minecraft.Util.getMillis();
        lastSlots = null;
    }

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
        // 題: 2 倍の「名残の祭壇」(入らなければ 1 倍)。開くと左から入る
        long now0 = net.minecraft.Util.getMillis();
        float ta = SkillStage.out((now0 - shownAt) / 240f);
        SkillStage.big(g, font, Component.translatable("gui.skyabyss.aspect.altar"), lx + Math.round((1f - ta) * -12f), lb.y() + 2, lw, SkillStage.fade(Paper.ON_BLACK_HI, ta));
        int cx = lb.cx();
        int cy = lb.y() + 2 + 18 + 4 + ALTAR_RAD + 2;
        long now = net.minecraft.Util.getMillis();
        if (lastSlots != null) {
            for (int i = 0; i < Math.min(lastSlots.length, d.aspectSlots.length); i++) {
                if (d.aspectSlots[i] != null && !d.aspectSlots[i].equals(lastSlots[i]) && Aspects.get(d.aspectSlots[i]) != null) {
                    slotAt[i] = now;
                }
            }
        }
        lastSlots = d.aspectSlots.clone();
        float ap = SkillStage.back((now - shownAt) / 240f);
        if (ap > 0.12f) {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            float as = 0.6f + 0.4f * ap;
            g.pose().scale(as, as, 1f);
            g.pose().translate(-cx, -cy, 0);
            altar(g, cx, cy, job);
            g.pose().popPose();
        }
        for (int i = 0; i < Aspects.MAX_SLOTS; i++) {
            double ang = Math.toRadians(-90 + 72 * i);
            int sx = cx + (int) Math.round(Math.cos(ang) * RING) - SLOT / 2;
            int sy = cy + (int) Math.round(Math.sin(ang) * RING) - SLOT / 2;
            boolean isOpen = i < open;
            Aspect a = isOpen ? Aspects.get(d.aspectSlots[i]) : null;
            boolean hover = in(mx, my, sx, sy, SLOT, SLOT);
            float sp = SkillStage.back((now - shownAt - 80 - i * 35L) / 200f);
            if (sp < 0.12f) {
                continue;
            }
            float ss = 0.5f + 0.5f * sp;
            if (now - slotAt[i] < 400) {
                ss *= 1f + 0.5f * (1f - SkillStage.back((now - slotAt[i]) / 200f));
            }
            g.pose().pushPose();
            g.pose().translate(sx + SLOT / 2f, sy + SLOT / 2f, 0);
            g.pose().scale(ss, ss, 1f);
            g.pose().translate(-(sx + SLOT / 2f), -(sy + SLOT / 2f), 0);
            try {
            if (!isOpen) {
                // 閉じた枠: 生成りの台紙を少し沈めた升に、開く Lv を墨で
                g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFFB8AEA4);
                g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xFFD8D0C4);
                String need = i < 4 ? String.valueOf(new int[] {20, 30, 40, 50}[i]) : "転";
                g.drawString(font, need, sx + (SLOT - font.width(need)) / 2 + 1, sy + 7, Paper.INK_SOFT, false);
                if (hover) {
                    tip = List.of(i < 4 ? Component.translatable("gui.skyabyss.aspect.slot_locked", new int[] {20, 30, 40, 50}[i]).withColor(SOFT)
                            : Component.translatable("gui.skyabyss.aspect.slot_locked_rebirth").withColor(SOFT));
                }
                continue;
            }
            if (a == null) {
                // 空きの枠: 台紙を切り抜いた黒い升と、真ん中の灰の菱
                g.fill(sx, sy, sx + SLOT, sy + SLOT, hover ? Paper.BLACK : Paper.BLACK_LO);
                diamond(g, sx + SLOT / 2, sy + SLOT / 2, 2, hover ? Paper.GREY : Paper.GREY_LO);
                if (hover) {
                    tip = List.of(Component.translatable("gui.skyabyss.aspect.empty").withColor(SOFT),
                            Component.translatable("gui.skyabyss.aspect.empty_hint").withColor(DIM));
                }
            } else {
                int lv = Aspects.level(d, a);
                paperTile(g, font, sx, sy, SLOT, a, lv, hover, false, true, i);
                if (hover) {
                    tip = new ArrayList<>(tip(d, a));
                    tip.add(Component.translatable("gui.skyabyss.aspect.click_remove").withColor(PALE));
                }
            }
            hits.add(new int[] {sx, sy, SLOT, SLOT, HIT_SLOT, i});
            } finally {
                g.pose().popPose();
            }
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
        int sy0 = cy + (int) (ALTAR_RAD * 0.81f) + 6;
        // 付けている数: 字と菱 (開いた枠の数だけ。付けた分が桃色)
        Component wl = Component.translatable("gui.skyabyss.aspect.worn_head");
        int wRowW = font.width(wl) + 6 + open * 9;
        int wx = cx - wRowW / 2;
        if (open > 0) {
            g.drawString(font, wl, wx, sy0, 0xFF000000 | Paper.ON_BLACK_SOFT, false);
            SkillStage.pips(g, wx + font.width(wl) + 6, sy0 + 1, open, used, 3, true);
        }
        // レジェンドまで: 字と、8 つ目で必ず出る目盛り (進んだ分が桃色)
        if (sy0 + 12 + 10 <= lb.bottom() + 2) {
            int left = Math.max(0, Aspects.PITY - d.aspectPity);
            Component pl = Component.translatable("gui.skyabyss.aspect.pity", left);
            int pw = font.width(pl);
            g.drawString(font, pl, cx - pw / 2, sy0 + 12, 0xFF000000 | Paper.ON_BLACK_DIM, false);
        }

        // ---- 右: 棚
        Golden.Box rb = right(body);
        int rx = rb.x() + Golden.S5;
        int rw = rb.w() - 2 * Golden.S5;
        Component count = Component.translatable("gui.skyabyss.aspect.collected", owned, total);
        int cw = font.width(count);
        g.drawString(font, count, rx + rw - cw, Golden.textY(rb.y() + 2, Golden.ROW), Paper.ON_BLACK_SOFT, false);
        FitText.draw(g, font, Component.translatable("gui.skyabyss.aspect.title").append(job == null ? Component.empty()
                : Component.literal("・").append(Component.translatable(job.nameKey()))), rx, Golden.textY(rb.y() + 2, Golden.ROW), rw - cw - Golden.S8, Paper.ON_BLACK_PINK, false);
        int vy = rb.y() + 2 + Golden.ROW + 3;
        // 集めた割合の帯 (見出しの下の線をそのまま目盛りに)
        g.fill(rx, vy - 3, rx + rw, vy - 1, Paper.EDGE);
        if (total > 0) {
            float ga = SkillStage.out((now0 - shownAt - 100) / 400f);
            g.fill(rx, vy - 3, rx + Math.round(rw * ga * owned / (float) total), vy - 1, Paper.PINK);
        }
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
            int gi = 0;
            for (String grp : GROUPS) {
                List<Aspect> list = group(job, grp);
                // 段は上から 45ms ずつ遅れて、下から 8 だけ上がって入る
                float ge = SkillStage.out((now - shownAt - 120 - gi++ * 45L) / 240f);
                if (ge < 0.3f) {
                    y += groupH;
                    continue;
                }
                g.pose().pushPose();
                g.pose().translate(0, Math.round((1f - ge) * 8f), 0);
                int have = 0;
                for (Aspect a : list) {
                    if (Aspects.level(d, a) > 0) {
                        have++;
                    }
                }
                // 段の見出し: 小さな玉・名前・数・細い線
                // 段の見出し: 菱 (どの祠でも = 灰、祠ごと = 桃色)・名前・数・点線
                diamond(g, tx0 + 2, y + 5, 2, grp.isEmpty() ? Paper.GREY : Paper.PINK);
                Component n = groupName(grp);
                String c = have + " / " + list.size();
                g.drawString(font, n, tx0 + 8, y + 1, have > 0 ? Paper.ON_BLACK : Paper.ON_BLACK_SOFT, false);
                int nw = font.width(n);
                g.drawString(font, c, tx0 + rowW - font.width(c), y + 1, have > 0 ? Paper.ON_BLACK_PINK : Paper.ON_BLACK_DIM, false);
                for (int lx2 = tx0 + 8 + nw + 4; lx2 < tx0 + rowW - font.width(c) - 4; lx2 += 4) {
                    g.fill(lx2, y + 5, lx2 + 2, y + 6, Paper.GREY_LO);
                }
                int ty = y + GROUP_HEAD;
                int[] col = new int[2];
                for (int i = 0; i < list.size(); i++) {
                    Aspect a = list.get(i);
                    int row = a.rarity() == Aspect.RARE ? 0 : 1;
                    int tx = tx0 + col[row]++ * (TILE + gap);
                    int tyy = ty + row * (TILE + PIPS + 1);
                    int lv = Aspects.level(d, a);
                    boolean hover = inView && in(mx, my, tx, tyy, TILE, TILE);
                    if (lv <= 0) {
                        // まだ見つけていない品: 黒い紙を少し沈めた升と、真ん中の小さな菱 (「？」は並ぶとうるさい)
                        g.fill(tx + 1, tyy + 1, tx + TILE - 1, tyy + TILE - 1, hover ? Paper.EDGE : Paper.BLACK_LO);
                        diamond(g, tx + TILE / 2, tyy + TILE / 2, hover ? 2 : 1, hover ? Paper.GREY : Paper.GREY_LO);
                    } else {
                        paperTile(g, font, tx, tyy, TILE, a, lv, hover, worn.contains(a.id()), false, i + col[row]);
                    }
                    if (lv > 0) {
                        paperPips(g, tx, tyy + TILE + 1, TILE, lv);
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
                g.pose().popPose();
                y += groupH;
            }
        }
        shelf.end(g);
        shelf.drawBar(g, 0xFF2A2834, Paper.PINK);
        if (job == null) {
            FitText.draw(g, font, Component.translatable("gui.skyabyss.aspect.no_job"), rx, vy + 4, rw, Paper.ON_BLACK_SOFT, false);
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    // 祭壇の地 (切り絵): 五つの枠を角に置いた生成りの五角の台紙 (角は手で少しずつずらす) と濃い桃色の影、
    // 真ん中から枠へ伸びる桃色の点線、真ん中の桃色の菱に職の木の絵
    // 台紙の五角の半径 (枠の角まで包む)
    private static final int ALTAR_RAD = 55;
    private static final float[] ALTAR_R = {1.00f, 0.95f, 1.04f, 0.97f, 1.02f};
    private static final float[] ALTAR_TURN = {-2f, 3f, -1f, 2f, -3f};

    private void altar(GuiGraphics g, int cx, int cy, Job job) {
        int rad = ALTAR_RAD;
        int pad = rad + 6;
        Paper.draw(g, "aspect_altar", "", cx - pad, cy - pad, 2 * pad + 1, 2 * pad + 1, s -> {
            float[] pts = new float[10];
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(-90 + 72 * i + ALTAR_TURN[i]);
                pts[i * 2] = pad + (float) Math.cos(a) * rad * ALTAR_R[i];
                pts[i * 2 + 1] = pad + (float) Math.sin(a) * rad * ALTAR_R[i];
            }
            s.layer(pts, Paper.CREAM, Paper.PINK_LO, 3, 4);
        });
        for (int i = 0; i < Aspects.MAX_SLOTS; i++) {
            double a = Math.toRadians(-90 + 72 * i);
            for (int t = 12; t < RING - SLOT / 2 - 1; t += 3) {
                int px = cx + (int) Math.round(Math.cos(a) * t);
                int py = cy + (int) Math.round(Math.sin(a) * t);
                g.fill(px, py, px + 1, py + 1, Paper.PINK_LO);
            }
        }
        diamond(g, cx + 1, cy + 1, 10, Paper.PINK_LO);
        diamond(g, cx, cy, 10, Paper.PINK);
        diamond(g, cx, cy, 8, Paper.BLACK_LO);
        if (job != null && job.tree != null) {
            SkillHud.drawIcon(g, job.tree.iconIndex(), cx - 6, cy - 6, 12);
        } else {
            diamond(g, cx, cy, 3, Paper.PINK_HI);
        }
    }

    // 切り絵の品の札 (名残のページ): 少し傾けた紙の札に技の絵。稀さは右上の菱 (レア = 灰、エピック = 桃色、レジェンド = 大きな明るい桃色)。
    // 持っていなければ暗い札に「？」。付けている品は持ち上げて (影を濃い桃色に) 上の端に桃色の菱を留める。
    // onCream なら祭壇の台紙の上 (札の地は黒)、でなければ黒い紙の上 (札の地は生成り)。
    // 試練の板 (TrialBoardScreen) も SkillScreenAccess.aspectTile からこれを使う
    static void paperTile(GuiGraphics g, Font font, int x, int y, int s, Aspect a, int lv, boolean hover, boolean worn, boolean onCream, int seq) {
        boolean known = lv > 0;
        int body = !known ? (onCream ? 0xFFB8AEA4 : Paper.GREY_LO) : onCream ? Paper.BLACK_LO : hover ? 0xFFFFF6EA : Paper.CREAM;
        int under = worn ? Paper.PINK_LO : onCream ? 0xFFB8AEA4 : Paper.BLACK_LO;
        int off = worn ? 3 : 1;
        float tilt = jp.revati.skyabyss.client.ui.UiLook.plate() ? 0f : seq % 3 == 0 ? -2.5f : seq % 3 == 1 ? 1.5f : 3f;
        int lift = hover && known ? 1 : 0;
        String name = "aspect_tile_" + s + "_" + Integer.toHexString(body) + "_" + Integer.toHexString(under) + "_" + off + "_" + tilt;
        Paper.draw(g, name, "", x - 4, y - 4 - lift, s + 9, s + 9,
                sh -> sh.layer(Paper.rotRect(s / 2f + 4, s / 2f + 4, s, s, tilt), body, under, off, off));
        if (!known) {
            g.drawString(font, "?", x + (s - font.width("?")) / 2 + 1, y + (s - 8) / 2 + 1, onCream ? Paper.INK_SOFT : Paper.ON_BLACK_SOFT, false);
            return;
        }
        int icon = s - 4;
        net.minecraft.world.entity.player.Player me = Minecraft.getInstance().player;
        SkillHud.drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(me, a.skill()), x + 2, y + 2 - lift, icon);
        SkillHud.changeFrame(g, me, a.skill(), x + 2, y + 2 - lift, icon);
        int r = a.rarity() == Aspect.LEGENDARY ? 3 : 2;
        int rc = a.rarity() == Aspect.LEGENDARY ? Paper.PINK_HI : a.rarity() == Aspect.RARE ? Paper.GREY : Paper.PINK;
        diamond(g, x + s - 1, y + 1 - lift, r + 1, Paper.BLACK_LO);
        diamond(g, x + s - 1, y + 1 - lift, r, rc);
        if (worn) {
            diamond(g, x + s / 2, y - 2 - lift, 3, Paper.BLACK_LO);
            diamond(g, x + s / 2, y - 2 - lift, 2, Paper.PINK_HI);
        }
    }

    // Lv の点 (5 つ。持っている Lv だけ桃色)
    static void paperPips(GuiGraphics g, int x, int y, int w, int lv) {
        int step = 4;
        int x0 = x + (w - (Aspects.MAX_LEVEL * step - 1)) / 2;
        for (int i = 0; i < Aspects.MAX_LEVEL; i++) {
            g.fill(x0 + i * step, y, x0 + i * step + 3, y + 2, i < lv ? Paper.PINK : Paper.GREY_LO);
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
