package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import jp.revati.skyabyss.rpg.skill.Glyph;
import jp.revati.skyabyss.rpg.skill.Mutation;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillExtOps;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.net.SkillExtPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// K 画面の 3 ページ目「変異・紋」。左: 技の変異 (A08。8 本、素材と一度きりの選び)。右: 紋の盤 (A14。穴は最大 6、中心の技と紋を選んで差す)。
// 押す順は 穴 → 中心の技 → 持っている紋。見えるのは 1 画面に穴 6 つまで (docs/design/31)
final class ExtPage {
    static final int HIT_MUTATION = 1;
    static final int HIT_SOCKET = 2;
    static final int HIT_PICK = 3;
    static final int HIT_BAG = 4;
    private static final int ROW_H = 15;
    private static final int CHIPS = 12;
    private static final int GOLD = SkillScreen.GOLD;
    private static final int TEXT = SkillScreen.TEXT;
    private static final int SOFT = SkillScreen.SOFT;
    private static final int DIM = SkillScreen.DIM;
    private static final int PALE = SkillScreen.PALE;
    private static final int DENY = SkillScreen.DENY;

    private final List<int[]> hits = new ArrayList<>();
    private int socket = -1;
    private Skill pick;
    private int pickScroll;
    private int bagScroll;
    private int armed = -1;
    private int armedTicks;

    void tick() {
        if (armedTicks > 0 && --armedTicks == 0) {
            armed = -1;
        }
    }

    // 撮影用: 穴と中心の技を選んだ状態にする
    void preselect(int socketIndex, Skill skill) {
        socket = socketIndex;
        pick = skill;
        quiet = true;
    }

    // 撮影のとき、カーソルの下の説明を出さない
    private boolean quiet;

    private static List<Skill> known(SkillData d) {
        List<Skill> list = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (d.knows(s)) {
                list.add(s);
            }
        }
        return list;
    }

    private static String pct(double v) {
        return String.valueOf(Math.round(Math.abs(v) * 100));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- 描画
    void render(GuiGraphics g, Font font, Minecraft mc, SkillData d, int left, int top, int mx, int my) {
        hits.clear();
        List<Component> tip = null;
        // ---- 左: 変異
        int x0 = left + 10;
        g.drawString(font, Component.translatable("gui.skyabyss.ext.mutation_head"), x0, top + 23, GOLD, false);
        Mutation[] all = Mutation.values();
        for (int i = 0; i < all.length; i++) {
            Mutation m = all[i];
            int y = top + 34 + i * ROW_H;
            boolean done = d.mutations.containsKey(m.skill.id);
            boolean mastered = d.rank(m.skill) >= m.skill.maxRank;
            boolean mats = SkillExtOps.hasMaterial(mc.player, m);
            boolean hover = in(mx, my, x0, y, 164, ROW_H - 1);
            boolean arm = armed == i;
            g.fill(x0, y, x0 + 164, y + ROW_H - 1, arm ? 0xFF000000 | GOLD : done ? 0xFF8A7A3A : hover ? 0xFF6A7A8A : 0xFF3A4452);
            g.fill(x0 + 1, y + 1, x0 + 163, y + ROW_H - 2, done ? 0xFF2A2A1E : hover ? 0xFF222B36 : 0xFF161A22);
            if (!mastered && !done) {
                g.setColor(0.5f, 0.5f, 0.55f, 1f);
            }
            SkillHud.drawIcon(g, m.skill.iconIndex(), x0 + 1, y - 1, 15);
            g.setColor(1f, 1f, 1f, 1f);
            Component name = Component.translatable(m.skill.nameKey());
            g.drawString(font, name, x0 + 19, y + 3, done ? SOFT : mastered ? TEXT : DIM, false);
            Component status = done ? Component.translatable(m.nameKey())
                    : !mastered ? Component.translatable("gui.skyabyss.ext.need_master")
                            : mats ? Component.translatable(arm ? "gui.skyabyss.ext.confirm" : "gui.skyabyss.ext.ready")
                                    : Component.translatable("gui.skyabyss.ext.need_material");
            int color = done ? GOLD : arm ? GOLD : !mastered ? DIM : mats ? PALE : DENY;
            g.drawString(font, status, x0 + 162 - font.width(status), y + 3, color, false);
            hits.add(new int[] {x0, y, 164, ROW_H - 1, HIT_MUTATION, i});
            if (hover) {
                tip = mutationTip(mc, d, m, done, mastered);
            }
        }
        // ---- 右: 紋の盤
        int rx = left + 186;
        int open = SkillRules.glyphSockets(d.rebirth);
        g.drawString(font, Component.translatable("gui.skyabyss.ext.glyph_head", open, SkillRules.GLYPH_SOCKETS), rx, top + 23, GOLD, false);
        for (int i = 0; i < SkillRules.GLYPH_SOCKETS; i++) {
            int cx = rx + (i % 3) * 61;
            int cy = top + 34 + (i / 3) * 33;
            boolean closed = i >= open;
            boolean sel = i == socket;
            boolean hover = in(mx, my, cx, cy, 59, 31);
            g.fill(cx, cy, cx + 59, cy + 31, sel ? 0xFF000000 | GOLD : hover && !closed ? 0xFF6A7A8A : 0xFF3A4452);
            g.fill(cx + 1, cy + 1, cx + 58, cy + 30, closed ? 0xFF10131A : 0xFF161A22);
            Glyph.Placed p = Glyph.parsePlaced(d.glyphSockets[i]);
            if (closed) {
                Component lock = Component.translatable("gui.skyabyss.ext.socket_closed", i - SkillRules.GLYPH_SOCKETS_BASE + 1);
                g.drawString(font, lock, cx + (59 - font.width(lock)) / 2, cy + 11, DIM, false);
            } else if (p == null) {
                g.drawString(font, "+", cx + 28, cy + 12, sel ? GOLD : DIM, false);
            } else {
                boolean live = d.knows(p.center());
                if (!live) {
                    g.setColor(0.5f, 0.5f, 0.55f, 1f);
                }
                SkillHud.drawGlyph(g, p.item().glyph(), cx + 4, cy + 4, 24);
                SkillHud.drawIcon(g, p.center().iconIndex(), cx + 34, cy + 3, 18);
                g.setColor(1f, 1f, 1f, 1f);
                String lv = "Lv" + p.item().level();
                g.drawString(font, lv, cx + 31, cy + 22, GOLD, false);
            }
            hits.add(new int[] {cx, cy, 59, 31, HIT_SOCKET, i});
            if (hover && p != null && !closed) {
                tip = glyphTip(d, p.item(), p.center());
            }
        }
        // 中心の技の選び (覚えている技。ホイールで送る)
        List<Skill> ks = known(d);
        int py = top + 100;
        pickScroll = Math.max(0, Math.min(pickScroll, Math.max(0, ks.size() - CHIPS)));
        g.drawString(font, Component.translatable("gui.skyabyss.ext.pick_head"), rx, py - 0, SOFT, false);
        py += 10;
        for (int k = 0; k < CHIPS; k++) {
            int idx = pickScroll + k;
            int x = rx + k * 15;
            g.fill(x, py, x + 14, py + 14, idx < ks.size() && ks.get(idx) == pick ? 0xFF000000 | GOLD : 0xFF2A3038);
            if (idx < ks.size()) {
                Skill s = ks.get(idx);
                SkillHud.drawIcon(g, s.iconIndex(), x + 1, py + 1, 12);
                hits.add(new int[] {x, py, 14, 14, HIT_PICK, idx});
                if (in(mx, my, x, py, 14, 14)) {
                    tip = List.of(Component.translatable(s.nameKey()).withColor(GOLD));
                }
            }
        }
        // 持っている紋
        int by = top + 135;
        bagScroll = Math.max(0, Math.min(bagScroll, Math.max(0, d.glyphBag.size() - CHIPS)));
        Glyph.Item hoverItem = null;
        for (int k = 0; k < CHIPS; k++) {
            int idx = bagScroll + k;
            int x = rx + k * 15;
            g.fill(x, by, x + 14, by + 14, 0xFF2A3038);
            Glyph.Item item = idx < d.glyphBag.size() ? Glyph.parse(d.glyphBag.get(idx)) : null;
            if (item != null) {
                SkillHud.drawGlyph(g, item.glyph(), x, by, 14);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, String.valueOf(item.level()), x + 9, by + 6, GOLD, true);
                g.pose().popPose();
                hits.add(new int[] {x, by, 14, 14, HIT_BAG, idx});
                if (in(mx, my, x, by, 14, 14)) {
                    hoverItem = item;
                    tip = glyphTip(d, item, pick);
                }
            }
        }
        Component more = Component.translatable("gui.skyabyss.ext.bag_count", d.glyphBag.size(), SkillRules.GLYPH_BAG_MAX);
        g.drawString(font, more, rx + 180 - font.width(more), by - 10, d.glyphBag.isEmpty() ? DIM : SOFT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.ext.bag_head"), rx, by - 10, SOFT, false);
        if (hoverItem == null && d.glyphBag.isEmpty()) {
            g.drawString(font, Component.translatable("gui.skyabyss.ext.bag_empty"), rx + 2, by + 3, DIM, false);
        }
        if (tip != null && !quiet) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private List<Component> mutationTip(Minecraft mc, SkillData d, Mutation m, boolean done, boolean mastered) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(m.nameKey()).withColor(GOLD));
        tip.add(Component.translatable("gui.skyabyss.ext.mutation_of", Component.translatable(m.skill.nameKey())).withColor(SOFT));
        tip.add(Component.translatable(m.effectKey()).withColor(TEXT));
        int core = SkillExtOps.count(mc.player, m.coreItem());
        int shard = SkillExtOps.count(mc.player, m.shardItem());
        tip.add(Component.translatable("gui.skyabyss.ext.material", m.coreItem().getDescription(), Math.min(core, 99), Mutation.CORES,
                m.shardItem().getDescription(), Math.min(shard, 99), Mutation.SHARDS).withColor(core >= Mutation.CORES && shard >= Mutation.SHARDS ? PALE : DENY));
        tip.add(Component.translatable(done ? "gui.skyabyss.ext.mutation_done" : mastered ? "gui.skyabyss.ext.mutation_once" : "gui.skyabyss.ext.mutation_master")
                .withColor(DIM));
        return tip;
    }

    private List<Component> glyphTip(SkillData d, Glyph.Item item, Skill center) {
        List<Component> tip = new ArrayList<>();
        Glyph gl = item.glyph();
        tip.add(Component.translatable("gui.skyabyss.ext.glyph_name", Component.translatable(gl.nameKey()), item.level()).withColor(GOLD));
        tip.add(Component.translatable(gl.effectKey(), pct(gl.centerValue(item.level())), pct(gl.neighborValue(item.level()))).withColor(TEXT));
        tip.add(Component.translatable("gui.skyabyss.ext.glyph_tag", Component.translatable(gl.tag.nameKey())).withColor(SOFT));
        if (center != null) {
            tip.add(Component.translatable("gui.skyabyss.ext.glyph_center", Component.translatable(center.nameKey())).withColor(PALE));
        }
        return tip;
    }

    // ---------------------------------------------------------------- 入力
    boolean click(double mx, double my, SkillData d, Minecraft mc, Consumer<Component> notice, Runnable click) {
        for (int[] h : hits) {
            if (!in(mx, my, h[0], h[1], h[2], h[3])) {
                continue;
            }
            switch (h[4]) {
                case HIT_MUTATION -> pressMutation(h[5], d, mc, notice, click);
                case HIT_SOCKET -> pressSocket(h[5], d, notice, click);
                case HIT_PICK -> {
                    List<Skill> ks = known(d);
                    pick = h[5] < ks.size() ? ks.get(h[5]) : pick;
                    click.run();
                }
                case HIT_BAG -> pressBag(h[5], d, notice, click);
                default -> {
                }
            }
            return true;
        }
        return false;
    }

    private void pressMutation(int index, SkillData d, Minecraft mc, Consumer<Component> notice, Runnable click) {
        Mutation m = Mutation.values()[index];
        if (d.mutations.containsKey(m.skill.id)) {
            notice.accept(Component.translatable("message.skyabyss.skill.ext_failed.already"));
            return;
        }
        if (d.rank(m.skill) < m.skill.maxRank) {
            notice.accept(Component.translatable("message.skyabyss.skill.ext_failed.not_mastered"));
            return;
        }
        if (!SkillExtOps.hasMaterial(mc.player, m)) {
            notice.accept(Component.translatable("message.skyabyss.skill.ext_failed.no_material"));
            return;
        }
        if (armed != index) {
            // 戻せないので二度押し
            armed = index;
            armedTicks = 60;
            click.run();
            return;
        }
        armed = -1;
        PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.MUTATE, m.skill.id, 0, 0));
        click.run();
    }

    private void pressSocket(int index, SkillData d, Consumer<Component> notice, Runnable click) {
        if (index >= SkillRules.glyphSockets(d.rebirth)) {
            notice.accept(Component.translatable("message.skyabyss.skill.ext_failed.closed"));
            return;
        }
        Glyph.Placed p = Glyph.parsePlaced(d.glyphSockets[index]);
        if (index == socket && p != null) {
            // 選んだままもう一度: 外す
            PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.REMOVE, "", index, 0));
            click.run();
            return;
        }
        socket = index;
        if (p != null) {
            pick = p.center();
        }
        click.run();
    }

    private void pressBag(int index, SkillData d, Consumer<Component> notice, Runnable click) {
        if (socket < 0) {
            notice.accept(Component.translatable("gui.skyabyss.ext.hint_socket"));
            return;
        }
        if (pick == null || !d.knows(pick)) {
            notice.accept(Component.translatable("gui.skyabyss.ext.hint_pick"));
            return;
        }
        PacketDistributor.sendToServer(new SkillExtPayload(SkillExtPayload.PLACE, pick.id, socket, index));
        click.run();
    }

    boolean scroll(double mx, double my, double delta, SkillData d, int left, int top) {
        int rx = left + 186;
        if (my >= top + 114 && my < top + 128 && mx >= rx) {
            pickScroll -= (int) Math.signum(delta) * 3;
            return true;
        }
        if (my >= top + 140 && my < top + 154 && mx >= rx) {
            bagScroll -= (int) Math.signum(delta) * 3;
            return true;
        }
        return false;
    }
}
