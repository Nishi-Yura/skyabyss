package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.rpg.skill.Skills;
import jp.revati.skyabyss.rpg.skill.net.SkillLearnPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillRespecPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillSlotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// 技の画面 (K キー)。左に木のつまみ (剣士・弓使い・星術師・深淵術師)、真ん中に木 (3 段)、右に選んだ技の説明と「習得」、
// 下に技の枠 4 つと振り直し。技を選んでから枠を押すと置ける (右クリックで空ける)。
// 背景は tools/gen_skill_assets.py で作る 2 倍密度のテクスチャ (600x368 を 300x184 で描く)
public class SkillScreen extends Screen {
    static final int W = 300;
    static final int H = 184;
    // 木のつまみ
    static final int TREE_TAB_X = 8;
    static final int TREE_TAB_Y = 26;
    static final int TREE_TAB = 26;
    static final int TREE_TAB_STEP = 30;
    // 技の印 (22x22) の位置: 列 0・1・2 (真ん中) と段 0・1・2
    static final int[] COL_X = {46, 98, 72};
    static final int[] TIER_Y = {42, 80, 118};
    static final int NODE = 22;
    // 説明の欄
    static final int INFO_X = 130;
    static final int INFO_Y = 22;
    static final int INFO_W = 162;
    static final int INFO_H = 128;
    // 習得のボタン (説明の欄の右上)
    static final int LEARN_W = 54;
    // 枠
    static final int SLOT_X = 132;
    static final int SLOT_Y = 157;
    static final int SLOT_STEP = 24;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int GOLD = 0xF2C14E;
    static final int PALE = 0x9FE8FF;
    static final int LORE = 0x7FD6D0;
    static final int DENY = 0xD9779A;
    static final int DIM = 0x5E6472;

    private static SkillTree lastTree = SkillTree.SWORD;
    private SkillTree tree = lastTree;
    private Skill selected;
    private int left;
    private int top;
    private Button learn;
    private Button respec;
    private int respecArmed;
    private Component noticeText;
    private int noticeTicks;

    public SkillScreen() {
        super(Component.translatable("gui.skyabyss.skill.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        learn = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.learn"), b -> learnSelected())
                .bounds(left + INFO_X + INFO_W - 4 - LEARN_W, top + INFO_Y + 3, LEARN_W, 14).build());
        respec = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.respec"), b -> pressRespec())
                .bounds(left + 236, top + 160, 56, 16).build());
        RpgTabs.add(this::addRenderableWidget, width, height, RpgTabs.SKILLS);
        refresh();
    }

    private SkillData data() {
        return SkillManager.get(minecraft.player);
    }

    private int level() {
        return Growth.get(minecraft.player).level;
    }

    private void refresh() {
        SkillData d = data();
        learn.active = selected != null && d.canLearn(selected, level()) == SkillData.LearnResult.OK;
        learn.visible = selected != null && d.treeOpen(selected.tree);
        if (selected != null) {
            learn.setMessage(d.rank(selected) >= selected.maxRank ? Component.translatable("gui.skyabyss.skill.learn_max")
                    : d.knows(selected) ? Component.translatable("gui.skyabyss.skill.learn_up") : Component.translatable("gui.skyabyss.skill.learn"));
        }
        respec.active = d.spentPoints() > 0;
        respec.setMessage(Component.translatable(respecArmed > 0 ? "gui.skyabyss.skill.respec_confirm" : "gui.skyabyss.skill.respec"));
    }

    @Override
    public void tick() {
        super.tick();
        if (respecArmed > 0) {
            respecArmed--;
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        refresh();
    }

    private void learnSelected() {
        if (selected != null) {
            PacketDistributor.sendToServer(new SkillLearnPayload(selected.id));
        }
    }

    // 振り直しは二度押し (押しまちがえで技を失わないように)
    private void pressRespec() {
        // 画面を開いている間はアクションバーが見えないので、断られる理由は先にここで出す (最後はサーバーが検める)
        int cost = SkillRules.respecCost(data().respecs);
        if (!minecraft.player.isCreative()) {
            if (!SkillManager.nearForge(minecraft.player)) {
                notice(Component.translatable("message.skyabyss.skill.respec_failed.no_forge"));
                return;
            }
            if (SkillManager.countCrystals(minecraft.player.getInventory()) < cost) {
                notice(Component.translatable("message.skyabyss.skill.respec_failed.no_crystals", cost));
                return;
            }
        }
        if (respecArmed <= 0) {
            respecArmed = 60;
            return;
        }
        respecArmed = 0;
        PacketDistributor.sendToServer(new SkillRespecPayload(cost));
    }

    // 画面の中の短い知らせ (断られた理由)。3 秒で消える
    private void notice(Component text) {
        noticeText = text;
        noticeTicks = 60;
        respecArmed = 0;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_FAIL.get(), 1.0f, 0.5f));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f));
    }

    // ---------------------------------------------------------------- 位置
    private int nodeX(Skill s) {
        return left + COL_X[s.column];
    }

    private int nodeY(Skill s) {
        return top + TIER_Y[s.tier];
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Skill nodeAt(double mx, double my) {
        if (!data().treeOpen(tree)) {
            return null;
        }
        for (Skill s : Skill.inTree(tree)) {
            if (in(mx, my, nodeX(s), nodeY(s), NODE, NODE)) {
                return s;
            }
        }
        return null;
    }

    private int slotAt(double mx, double my) {
        for (int i = 0; i < SkillRules.SLOTS; i++) {
            if (in(mx, my, left + SLOT_X + i * SLOT_STEP, top + SLOT_Y, NODE, NODE)) {
                return i;
            }
        }
        return -1;
    }

    private SkillTree treeTabAt(double mx, double my) {
        for (SkillTree t : SkillTree.values()) {
            if (in(mx, my, left + TREE_TAB_X, top + TREE_TAB_Y + t.ordinal() * TREE_TAB_STEP, TREE_TAB, TREE_TAB)) {
                return t;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 入力
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        SkillTree t = treeTabAt(mx, my);
        if (t != null) {
            if (t != tree) {
                tree = t;
                lastTree = t;
                selected = null;
                click();
                refresh();
            }
            return true;
        }
        Skill s = nodeAt(mx, my);
        if (s != null) {
            selected = s;
            click();
            refresh();
            return true;
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            SkillData d = data();
            if (button == 1) {
                // 右クリックで空ける
                if (d.slot(slot) != null) {
                    PacketDistributor.sendToServer(new SkillSlotPayload(slot, ""));
                    click();
                }
            } else if (selected != null && selected.active && d.knows(selected)) {
                PacketDistributor.sendToServer(new SkillSlotPayload(slot, selected.id));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_LEARN.get(), 1.6f, 0.35f));
            } else if (d.slot(slot) != null) {
                // 置いてある技を選ぶ (木も合わせる)
                selected = d.slot(slot);
                tree = selected.tree;
                lastTree = tree;
                click();
                refresh();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // K で閉じる、J でステータス画面へ
        if (RpgTabs.keyPressed(keyCode, scanCode, RpgTabs.SKILLS)) {
            return true;
        }
        // 技を選んでいるとき、枠のキー (R・G・V・Z) でその枠に置ける
        if (selected != null && selected.active && data().knows(selected)) {
            for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
                if (SkillClientSetup.SLOT_KEYS[i].matches(keyCode, scanCode)) {
                    PacketDistributor.sendToServer(new SkillSlotPayload(i, selected.id));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_LEARN.get(), 1.6f, 0.35f));
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 描画
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(SkillHud.TEXTURE, left, top, W, H, 0, 0, W * 2, H * 2, SkillHud.TEX_W, SkillHud.TEX_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        SkillData d = data();
        int level = level();
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);
        renderTreeTabs(g, d);
        renderTree(g, d, level);
        renderInfo(g, d, level);
        renderBottom(g, d, level);
        if (noticeTicks > 0 && noticeText != null) {
            // 説明の欄の下端に、拒否の色で
            int alpha = Math.min(255, noticeTicks * 12);
            List<FormattedCharSequence> lines = TextWrap.split(font, noticeText, INFO_W - 10);
            int y = top + INFO_Y + INFO_H - 4 - lines.size() * 10;
            g.fill(left + INFO_X + 2, y - 3, left + INFO_X + INFO_W - 2, top + INFO_Y + INFO_H - 2, (Math.min(200, alpha) << 24) | 0x1A1020);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left + INFO_X + 5, y, (alpha << 24) | DENY, false);
                y += 10;
            }
        }
        renderHover(g, d, mouseX, mouseY);
        renderLearnTooltip(g, d, mouseX, mouseY);
    }

    private void renderTreeTabs(GuiGraphics g, SkillData d) {
        for (SkillTree t : SkillTree.values()) {
            int x = left + TREE_TAB_X;
            int y = top + TREE_TAB_Y + t.ordinal() * TREE_TAB_STEP;
            int state = !d.treeOpen(t) ? 2 : t == tree ? 1 : 0;
            g.blit(SkillHud.TEXTURE, x, y, TREE_TAB, TREE_TAB, 240 + state * 56, 384, 52, 52, SkillHud.TEX_W, SkillHud.TEX_H);
            if (!d.treeOpen(t)) {
                g.setColor(0.45f, 0.45f, 0.52f, 1f);
            }
            SkillHud.drawIcon(g, 10 + t.ordinal(), x + 5, y + 5);
            g.setColor(1f, 1f, 1f, 1f);
            if (!d.treeOpen(t)) {
                SkillHud.drawIcon(g, 14, x + 9, y + 9);
            }
        }
    }

    private void renderTree(GuiGraphics g, SkillData d, int level) {
        boolean open = d.treeOpen(tree);
        // 見出し: 木の名前 (効果の元のステータスは説明の欄に出す)
        g.drawString(font, Component.translatable(tree.nameKey()), left + 42, top + 25, open ? GOLD : SOFT, false);
        if (!open) {
            // 封じられた木: 形だけを暗く見せる
            for (int tier = 0; tier < 3; tier++) {
                for (int col : tier == 2 ? new int[] {2} : new int[] {0, 1}) {
                    int x = left + COL_X[col];
                    int y = top + TIER_Y[tier];
                    if (tier < 2) {
                        line(g, x + NODE / 2, y + NODE, x + NODE / 2, top + TIER_Y[tier + 1], DIM);
                    }
                    g.blit(SkillHud.TEXTURE, x, y, NODE, NODE, 0, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
                    SkillHud.drawIcon(g, 15, x + 3, y + 3);
                }
            }
            return;
        }
        // つなぐ線 (先に描く)
        for (Skill s : Skill.inTree(tree)) {
            for (Skill r : s.requires()) {
                int color = d.knows(r) ? LORE : DIM;
                int x0 = nodeX(r) + NODE / 2;
                int y0 = nodeY(r) + NODE;
                int x1 = nodeX(s) + NODE / 2;
                int y1 = nodeY(s);
                int mid = (y0 + y1) / 2 + 3;
                line(g, x0, y0 + 8, x0, mid, color);
                line(g, Math.min(x0, x1), mid, Math.max(x0, x1), mid, color);
                line(g, x1, mid, x1, y1, color);
            }
        }
        for (Skill s : Skill.inTree(tree)) {
            int x = nodeX(s);
            int y = nodeY(s);
            int rank = d.rank(s);
            boolean reachable = d.requirementsMet(s) && level >= s.minLevel;
            int state = rank >= s.maxRank ? 3 : rank > 0 ? 2 : reachable ? 1 : 0;
            g.blit(SkillHud.TEXTURE, x, y, NODE, NODE, state * 48, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
            if (state == 0) {
                g.setColor(0.42f, 0.42f, 0.48f, 1f);
            } else if (state == 1) {
                g.setColor(0.72f, 0.72f, 0.78f, 1f);
            }
            SkillHud.drawIcon(g, s.iconIndex(), x + 3, y + 3);
            g.setColor(1f, 1f, 1f, 1f);
            if (s == selected) {
                g.blit(SkillHud.TEXTURE, x - 1, y - 1, NODE + 2, NODE + 2, 192, 384, 48, 48, SkillHud.TEX_W, SkillHud.TEX_H);
            }
            // 段 (小さく、印の下)
            String r = rank + "/" + s.maxRank;
            int color = rank >= s.maxRank ? GOLD : rank > 0 ? PALE : d.canLearn(s, level) == SkillData.LearnResult.OK ? TEXT : DIM;
            g.pose().pushPose();
            g.pose().translate(x + NODE / 2f, y + NODE + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, r, -font.width(r) / 2, 0, color, false);
            g.pose().popPose();
            // 能動の技には小さな菱形の印 (枠に置ける)
            if (s.active) {
                g.fill(x + NODE - 4, y + 1, x + NODE - 1, y + 4, rank > 0 ? 0xFF9FE8FF : 0xFF4A5060);
            }
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, 0xFF000000 | color);
    }

    private void renderInfo(GuiGraphics g, SkillData d, int level) {
        int x = left + INFO_X + 5;
        int y = top + INFO_Y + 5;
        int w = INFO_W - 10;
        int maxY = top + INFO_Y + INFO_H - 2;
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component statLine = Component.translatable("gui.skyabyss.skill.main_stat", Component.translatable(tree.mainStat.nameKey()));
        if (!d.treeOpen(tree)) {
            lines.addAll(TextWrap.split(font, Component.translatable(tree.sealedKey()).withColor(LORE), w));
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(TextWrap.split(font, Component.translatable(tree.descKey()).withColor(SOFT), w));
            lines.add(statLine.copy().withColor(DIM).getVisualOrderText());
            drawLines(g, lines, x, y, maxY);
            return;
        }
        if (selected == null || selected.tree != tree) {
            lines.addAll(TextWrap.split(font, Component.translatable(tree.descKey()).withColor(SOFT), w));
            lines.add(statLine.copy().withColor(SOFT).getVisualOrderText());
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.pick_hint").withColor(DIM), w));
            drawLines(g, lines, x, y, maxY);
            return;
        }
        Skill s = selected;
        int rank = d.rank(s);
        // 一行目: 名前 (右に習得のボタン)
        lines.addAll(TextWrap.split(font, Component.translatable(s.nameKey()).withColor(GOLD), w - LEARN_W - 4));
        lines.add(Component.translatable(s.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive")
                .append(Component.literal("・")).append(Component.translatable("gui.skyabyss.skill.rank", rank, s.maxRank)).withColor(SOFT)
                .getVisualOrderText());
        if (s.active) {
            int r = Math.max(1, rank);
            lines.add(Component.translatable("gui.skyabyss.skill.cost", SkillRules.manaCost(s, r),
                    SkillRules.fmtSeconds(SkillRules.cooldownTicks(s, r))).withColor(0x9FB8FF).getVisualOrderText());
        }
        // 今の段の効果 (まだ覚えていなければ段 1 の効果)。次の段は「習得」のボタンに重ねると出る
        List<FormattedCharSequence> body = new ArrayList<>();
        MutableComponent effect = Component.translatable(s.effectKey(), SkillRules.describeArgs(s, Math.max(1, rank)));
        if (rank == 0) {
            effect = Component.translatable("gui.skyabyss.skill.first").append(effect);
        }
        body.addAll(TextWrap.split(font, effect.withColor(TEXT), w));
        // 足りない条件 (赤紫)
        if (rank < s.maxRank) {
            if (level < s.minLevel) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_level", s.minLevel).withColor(DENY), w));
            }
            if (!d.requirementsMet(s)) {
                MutableComponent names = Component.empty();
                List<Skill> req = s.requires();
                for (int i = 0; i < req.size(); i++) {
                    if (i > 0) {
                        names.append(Component.translatable("gui.skyabyss.skill.or"));
                    }
                    names.append(Component.translatable(req.get(i).nameKey()));
                }
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_skill", names).withColor(DENY), w));
            }
            if (d.unspentPoints(level) < 1 && level >= s.minLevel && d.requirementsMet(s)) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_points").withColor(DENY), w));
            }
        }
        // 入りきるなら、言い伝えの一行を効果の前に添える
        List<FormattedCharSequence> flavor = TextWrap.split(font, Component.translatable(s.flavorKey()).withStyle(st -> st.withColor(LORE).withItalic(true)), w);
        int room = (maxY - y) / 10 - lines.size() - body.size();
        if (room >= flavor.size()) {
            lines.addAll(flavor);
        }
        lines.addAll(body);
        drawLines(g, lines, x, y, maxY);
    }

    // 習得のボタンに重ねたとき: 次の段の効果
    private void renderLearnTooltip(GuiGraphics g, SkillData d, int mx, int my) {
        if (selected == null || !learn.visible || !learn.isHovered()) {
            return;
        }
        int rank = d.rank(selected);
        if (rank >= selected.maxRank) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.skyabyss.skill.rank", rank + 1, selected.maxRank).withColor(GOLD).getVisualOrderText());
        lines.addAll(TextWrap.split(font, Component.translatable(selected.effectKey(), SkillRules.describeArgs(selected, rank + 1)).withColor(PALE), 200));
        // マナと待ちは、今の段から変わるときだけ (段 0 のときは説明の欄に段 1 の値が出ている)
        if (selected.active && rank > 0 && (SkillRules.manaCost(selected, rank + 1) != SkillRules.manaCost(selected, rank)
                || SkillRules.cooldownTicks(selected, rank + 1) != SkillRules.cooldownTicks(selected, rank))) {
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.cost", SkillRules.manaCost(selected, rank + 1),
                    SkillRules.fmtSeconds(SkillRules.cooldownTicks(selected, rank + 1))).withColor(0x9FB8FF), 200));
        }
        lines.add(Component.translatable("gui.skyabyss.skill.learn_cost").withColor(SOFT).getVisualOrderText());
        g.renderTooltip(font, lines, mx, my);
    }

    private void drawLines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y, int maxY) {
        for (FormattedCharSequence line : lines) {
            if (y + 9 > maxY) {
                break;
            }
            g.drawString(font, line, x, y, TEXT, false);
            y += 10;
        }
    }

    private void renderBottom(GuiGraphics g, SkillData d, int level) {
        int points = d.unspentPoints(level);
        g.drawString(font, Component.translatable("gui.skyabyss.skill.points", points), left + 12, top + 158, points > 0 ? GOLD : SOFT, false);
        double max = SkillManager.maxMana(minecraft.player);
        g.drawString(font, Component.translatable("gui.skyabyss.skill.mana", Math.round(Math.max(0, d.mana)), Math.round(max)),
                left + 12, top + 169, 0x9FB8FF, false);
        for (int i = 0; i < SkillRules.SLOTS; i++) {
            int x = left + SLOT_X + i * SLOT_STEP;
            int y = top + SLOT_Y;
            g.blit(SkillHud.TEXTURE, x, y, NODE, NODE, 420, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
            Skill s = d.slot(i);
            if (s != null) {
                SkillHud.drawIcon(g, s.iconIndex(), x + 3, y + 3);
            }
            String k = SkillClientSetup.SLOT_KEYS[i].getTranslatedKeyMessage().getString();
            if (k.length() > 3) {
                k = k.substring(0, 3);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, k, x + NODE - 2 - font.width(k), y + NODE - 9, s == null ? DIM : SOFT, true);
            g.pose().popPose();
        }
    }

    private void renderHover(GuiGraphics g, SkillData d, int mx, int my) {
        Skill node = nodeAt(mx, my);
        if (node != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(node.nameKey()).withColor(GOLD));
            lines.add(Component.translatable(node.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive").withColor(SOFT));
            if (node.active && d.knows(node)) {
                lines.add(Component.translatable("gui.skyabyss.skill.slot_hint").withColor(DIM));
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            Skill s = d.slot(slot);
            List<Component> lines = new ArrayList<>();
            lines.add(s == null ? Component.translatable("gui.skyabyss.skill.slot_empty").withColor(SOFT)
                    : Component.translatable(s.nameKey()).withColor(GOLD));
            lines.add(Component.translatable("gui.skyabyss.skill.slot_key", SkillClientSetup.SLOT_KEYS[slot].getTranslatedKeyMessage()).withColor(SOFT));
            lines.add(Component.translatable(selected != null && selected.active && d.knows(selected)
                    ? "gui.skyabyss.skill.slot_put" : "gui.skyabyss.skill.slot_how", selected == null ? "" : Component.translatable(selected.nameKey()))
                    .withColor(DIM));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        SkillTree t = treeTabAt(mx, my);
        if (t != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(t.nameKey()).withColor(d.treeOpen(t) ? GOLD : SOFT));
            if (!d.treeOpen(t)) {
                lines.add(Component.translatable(t.sealedKey()).withColor(LORE));
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        if (respec.isHovered()) {
            int cost = SkillRules.respecCost(d.respecs);
            List<FormattedCharSequence> lines = new ArrayList<>(TextWrap.split(font, Component.translatable(cost == 0
                    ? "gui.skyabyss.skill.respec_free" : "gui.skyabyss.skill.respec_cost", cost, SkillRules.RESPEC_FORGE_RANGE).withColor(TEXT), 180));
            if (respecArmed > 0) {
                lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.respec_again").withColor(DENY), 180));
            }
            g.renderTooltip(font, lines, mx, my);
        }
    }
}
