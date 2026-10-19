package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.rpg.skill.Skills;
import jp.revati.skyabyss.rpg.skill.WeaponType;
import jp.revati.skyabyss.rpg.skill.net.SkillLearnPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillLoadoutPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillRespecPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillRunePayload;
import jp.revati.skyabyss.rpg.skill.net.SkillSlotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

// 技の画面 (K キー)。左に木のつまみ (岩守り・風読み・潮歌い・灰の鍛え人・骨の射手・旅人)、真ん中に木 (4 行。線は前提)、
// 右に選んだ技の説明と「習得」・刻印の選び、下に技の点・マナ・技の枠 (4〜5)・型の保存 (2)・振り直し。
// 技を選んでから枠を押すと置ける (右クリックで空ける)。天職を選ぶまでは、木の 1 行目だけが開いている (SkillRules.CALLINGS)。
// 背景は tools/gen_builds_assets.py で作る 2 倍密度のテクスチャ (760x368 を 380x184 で描く)
public class SkillScreen extends Screen {
    static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/builds_panel.png");
    static final int W = 380;
    static final int H = 184;
    // 木のつまみ
    static final int TREE_TAB_X = 8;
    static final int TREE_TAB_Y = 24;
    static final int TREE_TAB = 20;
    static final int TREE_TAB_STEP = 21;
    // 技の印 (22x22) の位置: 列 0・1・2 (真ん中) と行 0〜3
    static final int[] COL_X = {44, 108, 76};
    static final int[] TIER_Y = {36, 66, 96, 126};
    static final int NODE = 22;
    // 説明の欄
    static final int INFO_X = 176;
    static final int INFO_Y = 22;
    static final int INFO_W = 196;
    static final int INFO_H = 128;
    // 習得のボタン (説明の欄の右上)
    static final int LEARN_W = 54;
    // 枠
    static final int SLOT_X = 86;
    static final int SLOT_Y = 156;
    static final int SLOT_STEP = 24;
    // 型の保存・振り直し・天職のボタン
    static final int BTN_X = 214;
    static final int BTN_ROW1 = 157;
    static final int BTN_ROW2 = 169;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int GOLD = 0xF2C14E;
    static final int PALE = 0x9FE8FF;
    static final int LORE = 0x7FD6D0;
    static final int DENY = 0xD9779A;
    static final int DIM = 0x5E6472;

    private static SkillTree lastTree = SkillTree.ROCK;
    private SkillTree tree = lastTree;
    private Skill selected;
    private int left;
    private int top;
    private Button learn;
    private Button respec;
    private Button calling;
    private final Button[] loadLoadout = new Button[SkillRules.LOADOUTS];
    private final Button[] saveLoadout = new Button[SkillRules.LOADOUTS];
    private int respecArmed;
    // 刻印の付け替えの確かめ (押した刻印の番号。0 でなし)
    private int runeArmed;
    private int runeArmedTicks;
    private Component noticeText;
    private int noticeTicks;
    // 説明の欄の刻印の行 (描くたびに作り直す): {x, y, w, h, 刻印の番号}
    private final List<int[]> runeRows = new ArrayList<>();
    private int hoverRune;

    public SkillScreen() {
        super(Component.translatable("gui.skyabyss.skill.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        learn = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.learn"), b -> learnSelected())
                .bounds(left + INFO_X + INFO_W - 4 - LEARN_W, top + INFO_Y + 3, LEARN_W, 14).build());
        for (int i = 0; i < SkillRules.LOADOUTS; i++) {
            int index = i;
            loadLoadout[i] = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.loadout_load", i + 1), b -> sendLoadout(index, false))
                    .bounds(left + BTN_X + i * 56, top + BTN_ROW1, 26, 12).build());
            saveLoadout[i] = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.loadout_save", i + 1), b -> sendLoadout(index, true))
                    .bounds(left + BTN_X + i * 56 + 28, top + BTN_ROW1, 26, 12).build());
        }
        respec = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.respec"), b -> pressRespec())
                .bounds(left + BTN_X, top + BTN_ROW2, 66, 12).build());
        calling = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.calling_choose"), b -> openCalling())
                .bounds(left + BTN_X + 70, top + BTN_ROW2, 92, 12).build());
        RpgTabs.add(this::addRenderableWidget, width, height, RpgTabs.SKILLS);
        refresh();
    }

    // 撮影用: 技を選んだ状態で開く
    public SkillScreen select(Skill skill) {
        selected = skill;
        tree = skill.tree;
        return this;
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
            learn.setMessage(d.rank(selected) >= d.rankLimit(selected) ? Component.translatable("gui.skyabyss.skill.learn_max")
                    : d.knows(selected) ? Component.translatable("gui.skyabyss.skill.learn_up") : Component.translatable("gui.skyabyss.skill.learn"));
        }
        respec.active = d.spentPoints() > 0;
        respec.setMessage(Component.translatable(respecArmed > 0 ? "gui.skyabyss.skill.respec_confirm" : "gui.skyabyss.skill.respec"));
        for (int i = 0; i < SkillRules.LOADOUTS; i++) {
            loadLoadout[i].active = d.loadouts[i].saved;
        }
        calling.visible = pendingCalling() > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (respecArmed > 0) {
            respecArmed--;
        }
        if (runeArmedTicks > 0 && --runeArmedTicks == 0) {
            runeArmed = 0;
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

    private void openCalling() {
        int which = pendingCalling();
        if (which > 0) {
            minecraft.setScreen(new CallingScreen(which));
        }
    }

    // 選べる天職 (0 = なし)。進みはサーバーだけが知るので、サーバーが知らせた段 (SkillClient.callingReady) を見る
    private int pendingCalling() {
        SkillData d = data();
        if (!SkillRules.CALLINGS) {
            return 0;
        }
        int ready = SkillClient.callingReady;
        return !d.hasCalling() ? (ready >= 1 ? 1 : 0) : d.calling2.isEmpty() ? (ready >= 2 ? 2 : 0) : 0;
    }

    private void sendLoadout(int index, boolean save) {
        PacketDistributor.sendToServer(new SkillLoadoutPayload(index, save));
        click();
    }

    // 振り直しは二度押し (押しまちがえで技を失わないように)
    private void pressRespec() {
        // 画面を開いている間はアクションバーが見えないので、断られる理由は先にここで出す (最後はサーバーが検める)
        int cost = SkillManager.respecCostFor(minecraft.player);
        if (!minecraft.player.isCreative() && !SkillManager.freeRespec(minecraft.player)) {
            if (!SkillManager.nearForge(minecraft.player)) {
                notice(Component.translatable("message.skyabyss.skill.respec_failed.no_forge"));
                return;
            }
            if (SkillManager.countCrystals(minecraft.player) < cost) {
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

    private static List<SkillTree> shownTrees() {
        List<SkillTree> list = new ArrayList<>();
        for (SkillTree t : SkillTree.values()) {
            if (!t.sealed) {
                list.add(t);
            }
        }
        return list;
    }

    private Skill nodeAt(double mx, double my) {
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
        List<SkillTree> trees = shownTrees();
        for (int i = 0; i < trees.size(); i++) {
            if (in(mx, my, left + TREE_TAB_X, top + TREE_TAB_Y + i * TREE_TAB_STEP, TREE_TAB, TREE_TAB)) {
                return trees.get(i);
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
            runeArmed = 0;
            click();
            refresh();
            return true;
        }
        for (int[] r : runeRows) {
            if (in(mx, my, r[0], r[1], r[2], r[3])) {
                pressRune(r[4]);
                return true;
            }
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            SkillData d = data();
            if (slot >= d.slotCount()) {
                notice(Component.translatable("gui.skyabyss.skill.slot_locked"));
                return true;
            }
            if (button == 1) {
                // 右クリックで空ける
                if (d.slot(slot) != null) {
                    PacketDistributor.sendToServer(new SkillSlotPayload(slot, ""));
                    click();
                }
            } else if (selected != null && selected.active && d.knows(selected)) {
                PacketDistributor.sendToServer(new SkillSlotPayload(slot, selected.id));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_LEARN.get(), 1.6f, 0.35f));
            } else if (selected != null && !selected.active && d.knows(selected)) {
                // 常に効く技は枠に置かない。黙って何も起きないと壊れて見えるので理由を出す
                notice(Component.translatable("gui.skyabyss.skill.passive_no_slot", Component.translatable(selected.nameKey())));
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

    // 刻印を押す: はじめて選ぶのは 1 回、付け替えは二度押し (結晶を使うので)
    private void pressRune(int index) {
        if (selected == null) {
            return;
        }
        SkillData d = data();
        if (!d.runeAvailable(selected)) {
            notice(Component.translatable("gui.skyabyss.skill.rune_locked"));
            return;
        }
        int current = d.rune(selected);
        if (current == index) {
            return;
        }
        if (current == 0) {
            if (d.unspentPoints(level()) < 1) {
                notice(Component.translatable("gui.skyabyss.skill.need_points"));
                return;
            }
            PacketDistributor.sendToServer(new SkillRunePayload(selected.id, index));
            click();
            return;
        }
        if (!minecraft.player.isCreative() && d.runeSwaps > 0 && SkillManager.countCrystals(minecraft.player) < SkillRules.RUNE_SWAP_COST) {
            notice(Component.translatable("message.skyabyss.skill.rune_failed.no_crystals"));
            return;
        }
        if (runeArmed != index) {
            runeArmed = index;
            runeArmedTicks = 60;
            click();
            return;
        }
        runeArmed = 0;
        PacketDistributor.sendToServer(new SkillRunePayload(selected.id, index));
        click();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // K で閉じる、J でステータス画面へ
        if (RpgTabs.keyPressed(keyCode, scanCode, RpgTabs.SKILLS)) {
            return true;
        }
        // 技を選んでいるとき、枠のキー (R・G・V・Z・B) でその枠に置ける
        if (selected != null && !selected.active && data().knows(selected)) {
            for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
                if (SkillClientSetup.SLOT_KEYS[i].matches(keyCode, scanCode)) {
                    notice(Component.translatable("gui.skyabyss.skill.passive_no_slot", Component.translatable(selected.nameKey())));
                    return true;
                }
            }
        }
        if (selected != null && selected.active && data().knows(selected)) {
            for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
                if (SkillClientSetup.SLOT_KEYS[i].matches(keyCode, scanCode)) {
                    if (i >= data().slotCount()) {
                        notice(Component.translatable("gui.skyabyss.skill.slot_locked"));
                        return true;
                    }
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
        g.blit(PANEL, left, top, W, H, 0, 0, W * 2, H * 2, 1024, 512);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        SkillData d = data();
        int level = level();
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);
        renderTreeTabs(g, d);
        renderTree(g, d, level);
        hoverRune = 0;
        renderInfo(g, d, level, mouseX, mouseY);
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
        List<SkillTree> trees = shownTrees();
        for (int i = 0; i < trees.size(); i++) {
            SkillTree t = trees.get(i);
            int x = left + TREE_TAB_X;
            int y = top + TREE_TAB_Y + i * TREE_TAB_STEP;
            int state = t == tree ? 1 : 0;
            g.blit(SkillHud.TEXTURE, x, y, TREE_TAB, TREE_TAB, 240 + state * 56, 384, 52, 52, SkillHud.TEX_W, SkillHud.TEX_H);
            SkillHud.drawIcon(g, t.iconIndex(), x + 2, y + 2);
            // 天職の印: 1 つ目は金、2 つ目は青緑の小さな点
            if (t.id.equals(d.calling)) {
                g.fill(x + TREE_TAB - 5, y + 1, x + TREE_TAB - 1, y + 5, 0xFF000000 | GOLD);
            } else if (t.id.equals(d.calling2)) {
                g.fill(x + TREE_TAB - 5, y + 1, x + TREE_TAB - 1, y + 5, 0xFF000000 | LORE);
            }
        }
    }

    private void renderTree(GuiGraphics g, SkillData d, int level) {
        // 見出し: 木の名前 (天職なら印を添える)
        MutableComponent head = Component.translatable(tree.nameKey());
        int headColor = GOLD;
        if (SkillRules.CALLINGS) {
            if (tree.id.equals(d.calling)) {
                head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.calling_mark"));
            } else if (tree.id.equals(d.calling2)) {
                head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.calling2_mark"));
                headColor = LORE;
            } else if (!d.hasCalling() && tree.callable()) {
                headColor = SOFT;
            }
        }
        g.drawString(font, head, left + 38, top + 25, headColor, false);
        // 行の目印 (要るレベル)
        for (int tier = 0; tier < 4; tier++) {
            String lv = "Lv" + new int[] {1, 5, 12, 20}[tier];
            g.pose().pushPose();
            g.pose().translate(left + 146, top + TIER_Y[tier] + 7, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, lv, 0, 0, level >= new int[] {1, 5, 12, 20}[tier] ? SOFT : DIM, false);
            g.pose().popPose();
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
            boolean allowed = d.callingAllows(s);
            boolean reachable = d.requirementsMet(s) && level >= s.minLevel && allowed;
            int state = rank >= d.rankLimit(s) ? 3 : rank > 0 ? 2 : reachable ? 1 : 0;
            if (s.keystone()) {
                g.blit(PANEL, x, y, NODE, NODE, state * 48, 376, 44, 44, 1024, 512);
            } else {
                g.blit(SkillHud.TEXTURE, x, y, NODE, NODE, state * 48, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
            }
            if (state == 0) {
                g.setColor(0.42f, 0.42f, 0.48f, 1f);
            } else if (state == 1) {
                g.setColor(0.72f, 0.72f, 0.78f, 1f);
            }
            SkillHud.drawIcon(g, s.iconIndex(), x + 3, y + 3);
            g.setColor(1f, 1f, 1f, 1f);
            if (!allowed) {
                SkillHud.drawIcon(g, 44, x + 9, y + 9);
            }
            if (s == selected) {
                g.blit(SkillHud.TEXTURE, x - 1, y - 1, NODE + 2, NODE + 2, 192, 384, 48, 48, SkillHud.TEX_W, SkillHud.TEX_H);
            }
            // 段 (小さく、印の下)。要は段がないので出さない
            if (!s.keystone() || rank == 0) {
                String r = s.keystone() ? "" : rank + "/" + d.rankLimit(s);
                int color = rank >= d.rankLimit(s) ? GOLD : rank > 0 ? PALE : d.canLearn(s, level) == SkillData.LearnResult.OK ? TEXT : DIM;
                g.pose().pushPose();
                g.pose().translate(x + NODE / 2f, y + NODE + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, r, -font.width(r) / 2, 0, color, false);
                g.pose().popPose();
            }
            // 能動の技には小さな菱形の印 (枠に置ける)。刻印を選んでいれば金
            if (s.active) {
                int dot = d.rune(s) > 0 ? 0xFFF2C14E : rank > 0 ? 0xFF9FE8FF : 0xFF4A5060;
                g.fill(x + NODE - 4, y + 1, x + NODE - 1, y + 4, dot);
            }
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, 0xFF000000 | color);
    }

    private void renderInfo(GuiGraphics g, SkillData d, int level, int mx, int my) {
        runeRows.clear();
        int x = left + INFO_X + 5;
        int y = top + INFO_Y + 5;
        int w = INFO_W - 10;
        int maxY = top + INFO_Y + INFO_H - 2;
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component statLine = tree.mainStat == null ? Component.translatable("gui.skyabyss.skill.main_stat_none")
                : Component.translatable("gui.skyabyss.skill.main_stat", Component.translatable(tree.mainStat.nameKey()));
        if (selected == null || selected.tree != tree) {
            lines.addAll(TextWrap.split(font, Component.translatable(tree.descKey()).withColor(SOFT), w));
            lines.add(statLine.copy().withColor(SOFT).getVisualOrderText());
            lines.add(FormattedCharSequence.EMPTY);
            if (SkillRules.CALLINGS && !d.hasCalling()) {
                lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.before_calling").withColor(LORE), w));
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.pick_hint").withColor(DIM), w));
            drawLines(g, lines, x, y, maxY);
            return;
        }
        Skill s = selected;
        int rank = d.rank(s);
        int limit = d.rankLimit(s);
        // 一行目: 名前 (右に習得のボタン)
        lines.addAll(TextWrap.split(font, Component.translatable(s.nameKey()).withColor(GOLD), w - LEARN_W - 4));
        MutableComponent kind = Component.translatable(s.keystone() ? "gui.skyabyss.skill.kind_keystone"
                : s.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive");
        if (!s.keystone()) {
            kind.append(Component.literal("・")).append(Component.translatable("gui.skyabyss.skill.rank", rank, limit));
        }
        lines.add(kind.withColor(SOFT).getVisualOrderText());
        if (s.active) {
            int r = Math.max(1, rank);
            int cooldown = rank > 0 ? SkillManager.cooldownFor(minecraft.player, s) : SkillRules.cooldownTicks(s, r);
            lines.add(Component.translatable("gui.skyabyss.skill.cost", SkillRules.manaCost(s, r), SkillRules.fmtSeconds(cooldown))
                    .withColor(0x9FB8FF).getVisualOrderText());
        } else if (rank > 0) {
            lines.add(Component.translatable("gui.skyabyss.skill.passive_active").withColor(0x9FE8FF).getVisualOrderText());
        }
        // 今の段の効果 (まだ覚えていなければ段 1 の効果)。次の段は「習得」のボタンに重ねると出る
        List<FormattedCharSequence> body = new ArrayList<>();
        MutableComponent effect = Component.translatable(s.effectKey(), SkillRules.describeArgs(s, Math.max(1, rank)));
        if (rank == 0) {
            effect = Component.translatable("gui.skyabyss.skill.first").append(effect);
        }
        body.addAll(TextWrap.split(font, effect.withColor(TEXT), w));
        // 型のおまけ
        WeaponType type = SkillRules.affinity(s);
        if (type != null) {
            boolean has = WeaponType.has(minecraft.player, type);
            body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.type_line", Component.translatable(type.nameKey()),
                    Component.translatable(s.typeKey())).withColor(has ? PALE : SOFT), w));
        }
        // 足りない条件 (赤紫)
        if (rank < limit) {
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
            if (!d.callingAllows(s)) {
                body.addAll(TextWrap.split(font, Component.translatable(d.hasCalling() ? "gui.skyabyss.skill.need_calling_deep" : "gui.skyabyss.skill.need_calling")
                        .withColor(DENY), w));
            }
            if (d.unspentPoints(level) < 1 && level >= s.minLevel && d.requirementsMet(s) && d.callingAllows(s)) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_points").withColor(DENY), w));
            }
        } else if (limit < s.maxRank) {
            body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.rank_capped", limit).withColor(SOFT), w));
        }
        // 刻印 (使う技のうち、刻印のあるもの): 3 つの行。極めていなければ理由
        List<Rune> runes = s.runes();
        boolean legend = legendShown(d, s);
        boolean hasRunes = !runes.isEmpty() || legend;
        int runeHeight = !hasRunes ? 0 : 4 * 11 + 2 + (legend ? 13 : 0);
        // 入りきるなら、言い伝えの一行を効果の前に添える
        List<FormattedCharSequence> flavor = TextWrap.split(font, Component.translatable(s.flavorKey()).withStyle(st -> st.withColor(LORE).withItalic(true)), w);
        int room = (maxY - y - runeHeight) / 10 - lines.size() - body.size();
        if (room >= flavor.size()) {
            lines.addAll(flavor);
        }
        lines.addAll(body);
        int usedBottom = y + lines.size() * 10;
        // 本文は刻印の分を残して描く
        drawLines(g, lines, x, y, maxY - runeHeight);
        if (hasRunes) {
            renderRunes(g, d, s, x, Math.min(usedBottom + 2, maxY - runeHeight), w, mx, my);
        }
    }

    // 伝説の書き換えの行を出すか (その武器を主の手に持っているか、もう選んでいるとき)
    private boolean legendShown(SkillData d, Skill s) {
        Rune legend = Rune.legendaryOf(s);
        return legend != null && (legend.held(minecraft.player) || d.rune(s) == legend.index);
    }

    private void renderRunes(GuiGraphics g, SkillData d, Skill s, int x, int y, int w, int mx, int my) {
        boolean available = d.runeAvailable(s);
        int current = d.rune(s);
        MutableComponent head = Component.translatable("gui.skyabyss.skill.runes_head");
        if (!available) {
            head.append(Component.literal("  ")).append(Component.translatable(
                    SkillRules.CALLINGS && !d.runesOpen(s) ? "gui.skyabyss.skill.runes_need_calling" : "gui.skyabyss.skill.runes_need_max", d.rank(s), s.maxRank));
        } else if (current == 0) {
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.runes_pick"));
        }
        g.drawString(font, head, x, y, available ? PALE : DIM, false);
        int ry = y + 11;
        List<Rune> list = s.runes();
        int cell = (w - 4) / 3;
        for (int i = 0; i < list.size(); i++) {
            Rune r = list.get(i);
            int rx = x + i * (cell + 2);
            boolean mine = current == r.index;
            boolean hover = in(mx, my, rx, ry, cell, 12) && available;
            int bg = mine ? 0xFF2A3A40 : hover ? 0xFF222B36 : 0xFF161A22;
            int edge = mine ? 0xFF000000 | GOLD : runeArmed == r.index ? 0xFF000000 | DENY : available ? 0xFF4A5A6A : 0xFF2A2E38;
            g.fill(rx, ry, rx + cell, ry + 12, edge);
            g.fill(rx + 1, ry + 1, rx + cell - 1, ry + 11, bg);
            Component name = Component.translatable(r.nameKey());
            int color = mine ? GOLD : available ? TEXT : DIM;
            g.drawString(font, name, rx + (cell - font.width(name)) / 2, ry + 2, color, false);
            runeRows.add(new int[] {rx, ry, cell, 12, r.index});
            if (hover) {
                hoverRune = r.index;
            }
        }
        // 伝説の書き換え: 全幅の行 (その武器を持つあいだ)
        boolean legendRow = legendShown(d, s);
        if (legendRow) {
            Rune r = Rune.legendaryOf(s);
            int ly = ry + 14;
            boolean mine = current == r.index;
            boolean usable = available && r.held(minecraft.player);
            boolean hover = in(mx, my, x, ly, w - 4, 12) && usable;
            g.fill(x, ly, x + w - 4, ly + 12, mine ? 0xFF000000 | GOLD : runeArmed == r.index ? 0xFF000000 | DENY : usable ? 0xFF9A7A2E : 0xFF2A2E38);
            g.fill(x + 1, ly + 1, x + w - 5, ly + 11, mine ? 0xFF2A3A40 : hover ? 0xFF2E2A20 : 0xFF1C1A16);
            Component name = Component.translatable("gui.skyabyss.skill.rune_legend", Component.translatable(r.nameKey()));
            g.drawString(font, name, x + (w - 4 - font.width(name)) / 2, ly + 2, mine ? GOLD : usable ? 0xF2D78A : DIM, false);
            runeRows.add(new int[] {x, ly, w - 4, 12, r.index});
            if (hover) {
                hoverRune = r.index;
            }
        }
        // 見ている (選んでいる) 刻印の効果
        int shown = hoverRune != 0 ? hoverRune : current;
        Rune shownRune = shown == 0 ? null : Rune.of(s, shown);
        int ty = ry + (legendRow ? 28 : 14);
        if (shownRune != null) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable(shownRune.effectKey()).withColor(shown == current ? TEXT : PALE), w)) {
                if (ty + 9 > top + INFO_Y + INFO_H - 1) {
                    break;
                }
                g.drawString(font, line, x, ty, TEXT, false);
                ty += 10;
            }
        }
        if (shownRune != null && shownRune.legendary() && ty + 9 <= top + INFO_Y + INFO_H - 1) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("gui.skyabyss.skill.legend_need", Component.translatable(shownRune.skillNameKey())).withColor(SOFT), w)) {
                if (ty + 9 > top + INFO_Y + INFO_H - 1) {
                    break;
                }
                g.drawString(font, line, x, ty, SOFT, false);
                ty += 10;
            }
        }
        if (runeArmed != 0 && ty + 9 <= top + INFO_Y + INFO_H - 1) {
            g.drawString(font, Component.translatable("gui.skyabyss.skill.rune_again", d.runeSwaps == 0 ? 0 : SkillRules.RUNE_SWAP_COST), x, ty, DENY, false);
        }
    }

    // 習得のボタンに重ねたとき: 次の段の効果
    private void renderLearnTooltip(GuiGraphics g, SkillData d, int mx, int my) {
        if (selected == null || !learn.visible || !learn.isHovered()) {
            return;
        }
        int rank = d.rank(selected);
        if (rank >= d.rankLimit(selected)) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.skyabyss.skill.rank", rank + 1, d.rankLimit(selected)).withColor(GOLD).getVisualOrderText());
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
        g.drawString(font, Component.translatable("gui.skyabyss.skill.points", points), left + 10, top + 158, points > 0 ? GOLD : SOFT, false);
        double max = SkillManager.maxMana(minecraft.player);
        g.drawString(font, Component.translatable("gui.skyabyss.skill.mana", Math.round(Math.max(0, d.mana)), Math.round(max)),
                left + 10, top + 169, 0x9FB8FF, false);
        for (int i = 0; i < SkillRules.SLOTS; i++) {
            int x = left + SLOT_X + i * SLOT_STEP;
            int y = top + SLOT_Y;
            boolean locked = i >= d.slotCount();
            g.blit(SkillHud.TEXTURE, x, y, NODE, NODE, 420, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
            Skill s = d.slot(i);
            if (locked) {
                g.setColor(0.45f, 0.45f, 0.52f, 1f);
                SkillHud.drawIcon(g, 44, x + 3, y + 3);
                g.setColor(1f, 1f, 1f, 1f);
            } else if (s != null) {
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
            lines.add(Component.translatable(node.keystone() ? "gui.skyabyss.skill.kind_keystone"
                    : node.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive").withColor(SOFT));
            if (node.active && d.knows(node)) {
                lines.add(Component.translatable("gui.skyabyss.skill.slot_hint").withColor(DIM));
            } else if (!node.active && d.knows(node)) {
                lines.add(Component.translatable("gui.skyabyss.skill.passive_hint").withColor(DIM));
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            Skill s = d.slot(slot);
            List<Component> lines = new ArrayList<>();
            if (slot >= d.slotCount()) {
                lines.add(Component.translatable("gui.skyabyss.skill.slot_locked").withColor(SOFT));
                g.renderComponentTooltip(font, lines, mx, my);
                return;
            }
            lines.add(s == null ? Component.translatable("gui.skyabyss.skill.slot_empty").withColor(SOFT)
                    : Component.translatable(s.nameKey()).withColor(GOLD));
            lines.add(Component.translatable("gui.skyabyss.skill.slot_key", SkillClientSetup.SLOT_KEYS[slot].getTranslatedKeyMessage()).withColor(SOFT));
            lines.add(Component.translatable(selected != null && !selected.active && d.knows(selected) ? "gui.skyabyss.skill.passive_no_slot"
                    : selected != null && selected.active && d.knows(selected)
                    ? "gui.skyabyss.skill.slot_put" : "gui.skyabyss.skill.slot_how", selected == null ? "" : Component.translatable(selected.nameKey()))
                    .withColor(DIM));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        SkillTree t = treeTabAt(mx, my);
        if (t != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(t.nameKey()).withColor(GOLD));
            lines.add(Component.translatable(t.descKey()).withColor(SOFT));
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        for (int i = 0; i < SkillRules.LOADOUTS; i++) {
            if (loadLoadout[i].isHovered() || saveLoadout[i].isHovered()) {
                boolean save = saveLoadout[i].isHovered();
                List<FormattedCharSequence> lines = new ArrayList<>(TextWrap.split(font, Component.translatable(save ? "gui.skyabyss.skill.loadout_save_tip" : "gui.skyabyss.skill.loadout_load_tip",
                        i + 1, d.loadouts[i].saved ? Component.translatable("gui.skyabyss.skill.loadout_saved") : Component.translatable("gui.skyabyss.skill.loadout_empty")).withColor(TEXT), 190));
                g.renderTooltip(font, lines, mx, my);
                return;
            }
        }
        if (respec.isHovered()) {
            int cost = SkillManager.respecCostFor(minecraft.player);
            String key = SkillManager.freeRespec(minecraft.player) ? "gui.skyabyss.skill.respec_early"
                    : cost == 0 ? "gui.skyabyss.skill.respec_free" : "gui.skyabyss.skill.respec_cost";
            List<FormattedCharSequence> lines = new ArrayList<>(TextWrap.split(font, Component.translatable(key, cost, SkillRules.RESPEC_FORGE_RANGE,
                    SkillRules.FREE_RESPEC_LEVEL).withColor(TEXT), 180));
            if (respecArmed > 0) {
                lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.respec_again").withColor(DENY), 180));
            }
            g.renderTooltip(font, lines, mx, my);
        }
    }
}
