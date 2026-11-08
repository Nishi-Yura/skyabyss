package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.client.ui.Golden;
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
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// K 画面の 3 ページ目「アスペクト」(docs/design/58。2026-11-09 に紋の盤から替えた)。
//   左の脇の列 (0.382): 付けている枠 (最大 5。開いていない枠は開く条件)。枠を押すと外す
//   右の主の列 (0.618): 今の職の 16 品 (稀さの色の札・Lv・次の Lv までの数)。持っている品を押すと空いた枠へ付ける
final class ExtPage {
    static final int HIT_SLOT = 2;
    static final int HIT_CARD = 3;
    private static final int GOLD = SkillScreen.GOLD;
    private static final int TEXT = SkillScreen.TEXT;
    private static final int SOFT = SkillScreen.SOFT;
    private static final int DIM = SkillScreen.DIM;
    private static final int PALE = SkillScreen.PALE;
    private static final int DENY = SkillScreen.DENY;
    static final int[] RARITY = {0x6FA8E0, 0xB48AE6, 0xEF8A5C};
    private static final int COLS = 4;
    private static final int CARD_H = 24;

    private final List<int[]> hits = new ArrayList<>();

    void tick() {
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

    // 品の説明 (名前・稀さ・Lv・変える技・効き目・出る所・次の Lv まで)
    static List<Component> tip(SkillData d, Aspect a) {
        List<Component> out = new ArrayList<>();
        int copies = d.aspects.getOrDefault(a.id(), 0);
        int lv = Aspects.levelOf(copies);
        out.add(Component.translatable(a.nameKey()).withColor(RARITY[a.rarity()]));
        out.add(Component.translatable("aspect.skyabyss.rarity." + a.rarity()).append("  ")
                .append(lv > 0 ? Component.translatable(lv >= Aspects.MAX_LEVEL ? "aspect.skyabyss.level_max" : "aspect.skyabyss.level", lv)
                        : Component.translatable("aspect.skyabyss.unowned")).withColor(SOFT));
        out.add(Component.translatable("aspect.skyabyss.skill", Component.translatable(a.skill().nameKey())).withColor(PALE));
        int shown = Math.max(1, lv);
        for (Aspect.Fx f : a.fx()) {
            out.add(Component.translatable("aspect.skyabyss.fx." + f.verb().name(), Math.round(f.at(shown) * 100)).withColor(TEXT));
        }
        out.add((a.shared() ? Component.translatable("aspect.skyabyss.shared")
                : Component.translatable("aspect.skyabyss.shrine", Component.translatable(jp.revati.skyabyss.trial.TrialCatalog.titanKey(a.shrine())))).withColor(DIM));
        int next = Aspects.nextCopies(copies);
        if (lv > 0 && next > 0) {
            out.add(Component.translatable("aspect.skyabyss.copies", copies - Aspects.copiesFor(lv), next - Aspects.copiesFor(lv)).withColor(DIM));
        }
        return out;
    }

    void render(GuiGraphics g, Font font, Minecraft mc, SkillData d, Golden.Box body, int mx, int my) {
        hits.clear();
        List<Component> tip = null;
        Job job = Job.byId(d.curJob);
        int open = Aspects.slots(mc.player);
        // ---- 左: 枠
        Golden.Box lb = left(body);
        int x0 = lb.x() + Golden.S5;
        int used = 0;
        for (String s : d.aspectSlots) {
            if (s != null && !s.isEmpty()) {
                used++;
            }
        }
        g.drawString(font, Component.translatable("gui.skyabyss.aspect.slots", used, open), x0, Golden.textY(lb.y() + 2, Golden.ROW), GOLD, false);
        int sy = lb.y() + 2 + Golden.ROW + Golden.S3;
        int sw = lb.w() - 2 * Golden.S5;
        for (int i = 0; i < Aspects.MAX_SLOTS; i++) {
            int y = sy + i * (CARD_H + Golden.S3);
            boolean isOpen = i < open;
            Aspect a = isOpen ? Aspects.get(d.aspectSlots[i]) : null;
            boolean hover = in(mx, my, x0, y, sw, CARD_H);
            int line = a != null ? RARITY[a.rarity()] : isOpen ? 0x4A5060 : 0x2C3038;
            g.fill(x0, y, x0 + sw, y + CARD_H, isOpen ? (hover ? 0xFF262A34 : 0xFF1A1D24) : 0xFF14161B);
            Golden.outline(g, x0, y, x0 + sw, y + CARD_H, argb(line));
            Component label;
            if (!isOpen) {
                label = i < 4 ? Component.translatable("gui.skyabyss.aspect.slot_locked", new int[] {20, 30, 40, 50}[i])
                        : Component.translatable("gui.skyabyss.aspect.slot_locked_rebirth");
            } else if (a == null) {
                label = Component.translatable("gui.skyabyss.aspect.empty");
            } else {
                label = Component.translatable(a.nameKey());
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, label, x0 + Golden.S5, Golden.textY(y, 14), sw - 2 * Golden.S5, a != null ? argb(RARITY[a.rarity()]) : DIM, false);
            if (a != null) {
                int lv = Aspects.level(d, a);
                g.drawString(font, Component.translatable("aspect.skyabyss.level", lv), x0 + Golden.S5, y + 14, SOFT, false);
                if (hover) {
                    tip = new ArrayList<>(tip(d, a));
                    tip.add(Component.translatable("gui.skyabyss.aspect.click_remove").withColor(PALE));
                }
            }
            if (isOpen) {
                hits.add(new int[] {x0, y, sw, CARD_H, HIT_SLOT, i});
            }
        }
        // ---- 右: 今の職の品
        Golden.Box rb = right(body);
        int rx = rb.x() + Golden.S5;
        int rw = rb.w() - 2 * Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.aspect.title").append(job == null ? Component.empty()
                : Component.literal("  ").append(Component.translatable(job.nameKey()))), rx, Golden.textY(rb.y() + 2, Golden.ROW), GOLD, false);
        List<Aspect> list = job == null ? List.of() : Aspects.ofJob(job);
        int cw = (rw - (COLS - 1) * Golden.S3) / COLS;
        int top = rb.y() + 2 + Golden.ROW + Golden.S3;
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            Aspect a = list.get(i);
            int cx = rx + (i % COLS) * (cw + Golden.S3);
            int cy = top + (i / COLS) * (CARD_H + Golden.S3);
            int copies = d.aspects.getOrDefault(a.id(), 0);
            int lv = Aspects.levelOf(copies);
            any |= lv > 0;
            boolean hover = in(mx, my, cx, cy, cw, CARD_H);
            boolean worn = java.util.Arrays.asList(d.aspectSlots).contains(a.id());
            g.fill(cx, cy, cx + cw, cy + CARD_H, lv > 0 ? (hover ? 0xFF262A34 : 0xFF1A1D24) : 0xFF121418);
            Golden.outline(g, cx, cy, cx + cw, cy + CARD_H, argb(lv > 0 ? RARITY[a.rarity()] : 0x30343C));
            if (worn) {
                g.fill(cx + 1, cy + 1, cx + 4, cy + CARD_H - 1, argb(GOLD));
            }
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(a.nameKey()), cx + 5, cy + 3, cw - 8,
                    lv > 0 ? argb(RARITY[a.rarity()]) : DIM, false);
            // 次の Lv までの帯
            int next = Aspects.nextCopies(copies);
            int barW = cw - 10;
            g.fill(cx + 5, cy + CARD_H - 6, cx + 5 + barW, cy + CARD_H - 4, 0xFF2A2E38);
            if (lv > 0) {
                double fr = next < 0 ? 1 : (copies - Aspects.copiesFor(lv)) / (double) Math.max(1, next - Aspects.copiesFor(lv));
                g.fill(cx + 5, cy + CARD_H - 6, cx + 5 + (int) Math.round(barW * fr), cy + CARD_H - 4, argb(RARITY[a.rarity()]));
                g.drawString(font, String.valueOf(lv), cx + cw - 9, cy + 3, SOFT, false);
            }
            if (hover) {
                tip = new ArrayList<>(tip(d, a));
                if (lv > 0 && !worn) {
                    tip.add(Component.translatable("gui.skyabyss.aspect.click_equip").withColor(PALE));
                }
            }
            hits.add(new int[] {cx, cy, cw, CARD_H, HIT_CARD, i});
        }
        if (!any) {
            int y = top + 4 * (CARD_H + Golden.S3) + Golden.S3;
            for (FormattedCharSequence l : TextWrap.split(font, Component.translatable("gui.skyabyss.aspect.none_for_job"), rw)) {
                g.drawString(font, l, rx, y, SOFT, false);
                y += Golden.ROW;
            }
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    boolean click(double mx, double my, SkillData d, Minecraft mc, Consumer<Component> notice, Runnable click) {
        for (int[] h : hits) {
            if (!in(mx, my, h[0], h[1], h[2], h[3])) {
                continue;
            }
            if (h[4] == HIT_SLOT) {
                if (Aspects.get(d.aspectSlots[h[5]]) != null) {
                    PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.REMOVE, "", h[5], 0));
                    click.run();
                }
                return true;
            }
            if (h[4] == HIT_CARD) {
                Job job = Job.byId(d.curJob);
                List<Aspect> list = job == null ? List.of() : Aspects.ofJob(job);
                if (h[5] >= list.size()) {
                    return true;
                }
                Aspect a = list.get(h[5]);
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
        }
        return false;
    }

    boolean scroll(double mx, double my, double delta, SkillData d, Golden.Box body) {
        return false;
    }
}
