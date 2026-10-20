package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Mutation;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillBuild;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillKeystone;
import jp.revati.skyabyss.rpg.skill.SkillStance;
import jp.revati.skyabyss.rpg.skill.SkillTag;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.rpg.skill.Skills;
import jp.revati.skyabyss.rpg.skill.WeaponType;
import jp.revati.skyabyss.rpg.skill.net.SkillChainPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillKeystonePayload;
import jp.revati.skyabyss.rpg.skill.net.SkillLearnPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillStancePayload;
import jp.revati.skyabyss.rpg.skill.net.SkillTryonPayload;
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
    // ページの見出し (上の帯の右)
    static final int SUB_X = 222;
    static final int SUB_Y = 5;
    static final int SUB_W = 50;
    static final int SUB_H = 13;

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
    // 3 ページ目 (変異・紋の盤)
    private final ExtPage ext = new ExtPage();
    private Skill selected;
    private int left;
    private int top;
    private Button learn;
    private Button respec;
    private Button calling;
    private final Button[] loadLoadout = new Button[SkillRules.LOADOUTS];
    private Button saveToggle;
    private boolean saveMode;
    private Button tryonBtn;
    private Button tryonOk;
    private Button tryonUndo;
    private Button tryonCancel;
    // ページの見出し (木 / 構え・鍵石 / 変異・紋)。いつも 3 つ並び、いまのページが金の縁になる
    private final SubTab[] subTabs = new SubTab[3];
    // 0 = 木のページ、1 = ビルドのページ (構え・連鎖・タグ・鍵石)
    private int page;
    // 試着 (仮振り): 画面だけの写しと、仮に振った順。null = 試着していない
    private SkillData tryon;
    private final List<String> tryonOrder = new ArrayList<>();
    // ビルドのページで押せる所: {x, y, w, h, 種類 (1 = 構え、2 = 連鎖、3 = 鍵石、4 = タグ), 引数}
    private final List<int[]> hits = new ArrayList<>();
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
            loadLoadout[i] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)), b -> pressLoadout(index))
                    .bounds(left + BTN_X + i * 18, top + BTN_ROW1, 17, 12).build());
        }
        saveToggle = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.loadout_save_mode"), b -> {
            saveMode = !saveMode;
            click();
        }).bounds(left + BTN_X + 74, top + BTN_ROW1, 32, 12).build());
        calling = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.calling_choose_short"), b -> openCalling())
                .bounds(left + BTN_X + 108, top + BTN_ROW1, 50, 12).build());
        respec = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.respec"), b -> pressRespec())
                .bounds(left + BTN_X, top + BTN_ROW2, 50, 12).build());
        tryonBtn = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.tryon"), b -> toggleTryon())
                .bounds(left + BTN_X + 52, top + BTN_ROW2, 36, 12).build());
        String[] subKeys = {"gui.skyabyss.skill.sub_tree", "gui.skyabyss.skill.sub_build", "gui.skyabyss.skill.sub_ext"};
        for (int i = 0; i < 3; i++) {
            subTabs[i] = addRenderableWidget(new SubTab(left + SUB_X + i * (SUB_W + 1), top + SUB_Y, i, Component.translatable(subKeys[i])));
        }
        tryonOk = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.tryon_ok"), b -> tryonConfirm())
                .bounds(left + BTN_X, top + BTN_ROW2, 50, 12).build());
        tryonUndo = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.tryon_undo"), b -> tryonUndo())
                .bounds(left + BTN_X + 52, top + BTN_ROW2, 50, 12).build());
        tryonCancel = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.skill.tryon_cancel"), b -> tryonCancel())
                .bounds(left + BTN_X + 104, top + BTN_ROW2, 54, 12).build());
        RpgTabs.add(this::addRenderableWidget, width, height, RpgTabs.SKILLS);
        if (!demoTry.isEmpty() && tryon == null) {
            tryon = real().copyOf();
            for (Skill s : demoTry) {
                tryon.ranks.merge(s.id, 1, Integer::sum);
                tryonOrder.add(s.id);
            }
            demoTry = new ArrayList<>();
        }
        refresh();
    }

    // 撮影用: ビルドのページで開く
    public SkillScreen buildPage() {
        page = 1;
        return this;
    }

    // 撮影用: 試着中 (仮に振った技を順に) の状態で開く
    private List<Skill> demoTry = new ArrayList<>();

    public SkillScreen tryOn(Skill... skills) {
        demoTry = new ArrayList<>(List.of(skills));
        return this;
    }

    // 撮影用: 技を選んだ状態で開く
    public SkillScreen select(Skill skill) {
        selected = skill;
        tree = skill.tree;
        return this;
    }

    private SkillData real() {
        return SkillManager.get(minecraft.player);
    }

    // 画面に見せる記録: 試着中は仮に振った写し
    private SkillData data() {
        return tryon != null && page == 0 ? tryon : real();
    }

    private int level() {
        return Growth.get(minecraft.player).level;
    }

    private void refresh() {
        SkillData d = data();
        learn.active = selected != null && d.canLearn(selected, level()) == SkillData.LearnResult.OK;
        learn.visible = page == 0 && selected != null && d.treeOpen(selected.tree);
        if (selected != null) {
            learn.setMessage(d.rank(selected) >= d.rankLimit(selected) ? Component.translatable("gui.skyabyss.skill.learn_max")
                    : d.knows(selected) ? Component.translatable("gui.skyabyss.skill.learn_up") : Component.translatable("gui.skyabyss.skill.learn"));
        }
        SkillData r = real();
        boolean on = tryon != null;
        respec.visible = !on;
        tryonBtn.visible = !on;
        for (SubTab t : subTabs) {
            t.visible = !on;
        }
        tryonOk.visible = on;
        tryonUndo.visible = on;
        tryonCancel.visible = on;
        tryonOk.active = !tryonOrder.isEmpty();
        tryonUndo.active = !tryonOrder.isEmpty();
        tryonBtn.active = page == 0;
        if (!shownTrees().contains(tree)) {
            tree = SkillTree.ROCK;
            lastTree = tree;
            selected = null;
        }
        respec.active = r.spentPoints() > 0;
        respec.setMessage(Component.translatable(respecArmed > 0 ? "gui.skyabyss.skill.respec_confirm" : "gui.skyabyss.skill.respec"));
        for (int i = 0; i < SkillRules.LOADOUTS; i++) {
            loadLoadout[i].active = saveMode || r.loadouts[i].saved;
        }
        saveToggle.setMessage(Component.translatable(saveMode ? "gui.skyabyss.skill.loadout_save_on" : "gui.skyabyss.skill.loadout_save_mode"));
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
        ext.tick();
        refresh();
    }

    private void learnSelected() {
        if (selected == null) {
            return;
        }
        if (tryon != null) {
            // 試着: 画面の写しだけを進める (サーバーへは確定のときにまとめて送る)
            if (tryon.canLearn(selected, level()) == SkillData.LearnResult.OK) {
                tryon.ranks.merge(selected.id, 1, Integer::sum);
                tryonOrder.add(selected.id);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_LEARN.get(), 1.8f, 0.3f));
            } else {
                notice(Component.translatable("message.skyabyss.skill.learn_failed." + tryon.canLearn(selected, level()).name().toLowerCase()));
            }
            return;
        }
        PacketDistributor.sendToServer(new SkillLearnPayload(selected.id));
    }

    private void toggleTryon() {
        if (tryon != null) {
            tryonCancel();
            return;
        }
        tryon = real().copyOf();
        tryonOrder.clear();
        click();
    }

    private void tryonConfirm() {
        if (tryon != null && !tryonOrder.isEmpty()) {
            PacketDistributor.sendToServer(new SkillTryonPayload(new ArrayList<>(tryonOrder)));
        }
        tryon = null;
        tryonOrder.clear();
        click();
    }

    private void tryonCancel() {
        tryon = null;
        tryonOrder.clear();
        click();
    }

    // 仮に振った最後の 1 つを戻す
    private void tryonUndo() {
        if (tryon == null || tryonOrder.isEmpty()) {
            return;
        }
        String id = tryonOrder.remove(tryonOrder.size() - 1);
        int left = tryon.ranks.merge(id, -1, Integer::sum);
        if (left <= 0) {
            tryon.ranks.remove(id);
        }
        click();
    }

    private void setPage(int p) {
        if (p != page) {
            page = p;
            click();
        }
    }

    // ページの見出し: 暗い札。いまのページは金の縁、触れていれば明るい縁。字は箱に収まらなければ縮める
    private final class SubTab extends net.minecraft.client.gui.components.AbstractWidget {
        private final int index;

        SubTab(int x, int y, int index, Component label) {
            super(x, y, SUB_W, SUB_H, label);
            this.index = index;
        }

        @Override
        public void onClick(double mx, double my) {
            setPage(index);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
            boolean on = page == index;
            int edge = on ? 0xFF000000 | GOLD : isHovered() ? 0xFF6A7A8A : 0xFF3A4452;
            g.fill(getX(), getY(), getX() + width, getY() + height, edge);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, on ? 0xFF2A3A40 : 0xFF161A22);
            drawFit(g, getMessage(), getX() + width / 2, getY() + 3, width - 6, on ? GOLD : SOFT);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    // 撮影用: 変異・紋のページで開く (穴と中心の技を選んだ状態)
    public SkillScreen extPage(int socket, Skill center) {
        page = 2;
        ext.preselect(socket, center);
        return this;
    }

    // 撮影用: 木を選ぶ
    public SkillScreen showTree(SkillTree t) {
        tree = t;
        lastTree = t;
        return this;
    }

    // 転生の回数 (記録に写してある)
    private int rebirth() {
        return real().rebirth;
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

    private void pressLoadout(int index) {
        if (tryon != null) {
            notice(Component.translatable("gui.skyabyss.skill.tryon_locked"));
            return;
        }
        boolean save = saveMode;
        saveMode = false;
        PacketDistributor.sendToServer(new SkillLoadoutPayload(index, save));
        click();
    }

    // 振り直しは二度押し (押しまちがえで技を失わないように)
    private void pressRespec() {
        if (tryon != null) {
            return;
        }
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
    // 橋の輪: 5 本の木 (角) の間に橋 (辺の真ん中)。輪の中心と半径
    static final int RING_CX = 104;
    static final int RING_CY = 94;
    static final int RING_NODE_R = 34;
    static final int RING_TREE_R = 54;

    private static double ringAngle(int i, boolean node) {
        return Math.toRadians(-90 + 72 * i + (node ? 36 : 0));
    }

    private int nodeX(Skill s) {
        if (s.tree == SkillTree.HYBRID) {
            return left + RING_CX + (int) Math.round(Math.cos(ringAngle(s.column, true)) * RING_NODE_R) - NODE / 2;
        }
        return left + COL_X[s.column];
    }

    private int nodeY(Skill s) {
        if (s.tree == SkillTree.HYBRID) {
            return top + RING_CY + (int) Math.round(Math.sin(ringAngle(s.column, true)) * RING_NODE_R) - NODE / 2;
        }
        return top + TIER_Y[s.tier];
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // 見える木: 封じていない木。淵と前世の技は、転生した人にだけ (未転生の人には見えない)
    private List<SkillTree> shownTrees() {
        List<SkillTree> list = new ArrayList<>();
        int reborn = minecraft == null || minecraft.player == null ? 0 : rebirth();
        for (SkillTree t : SkillTree.values()) {
            if (!t.sealed && (!t.rebornOnly() || reborn > 0)) {
                list.add(t);
            }
        }
        return list;
    }

    // タブの大きさ: 6 本までは今のまま、それより多いときは詰める
    // 木のつまみは、下の「技の点」の文字 (top + 156) にかからない高さ (top + 24 〜 150) に収める。木が増えたぶんだけ詰める
    private int tabStep(int count) {
        return Math.min(TREE_TAB_STEP, (150 - TREE_TAB_Y) / Math.max(1, count));
    }

    private int tabSize(int count) {
        return Math.min(TREE_TAB, tabStep(count) - 1);
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
            if (in(mx, my, left + TREE_TAB_X, top + TREE_TAB_Y + i * tabStep(trees.size()), tabSize(trees.size()), tabSize(trees.size()))) {
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
        if (page == 2) {
            return ext.click(mx, my, real(), minecraft, this::notice, this::click);
        }
        if (page == 1) {
            for (int[] h : hits) {
                if (in(mx, my, h[0], h[1], h[2], h[3]) && h[4] != 4) {
                    pressHit(h[4], h[5]);
                    return true;
                }
            }
        }
        SkillTree t = page == 0 ? treeTabAt(mx, my) : null;
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
        Skill s = page == 0 ? nodeAt(mx, my) : null;
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
            if (tryon != null) {
                notice(Component.translatable("gui.skyabyss.skill.tryon_locked"));
                return true;
            }
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
        if (tryon != null) {
            notice(Component.translatable("gui.skyabyss.skill.tryon_locked"));
            return;
        }
        if (!d.runeAvailable(selected)) {
            notice(Component.translatable("gui.skyabyss.skill.rune_locked"));
            return;
        }
        if (index == Rune.MASTERY && !d.runeUsable(selected, index)) {
            notice(Component.translatable("gui.skyabyss.skill.mastery_locked", SkillRules.MASTERY_RUNE_LEVEL));
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
        if (tryon == null && page == 0 && selected != null && !selected.active && data().knows(selected)) {
            for (int i = 0; i < SkillClientSetup.SLOT_KEYS.length; i++) {
                if (SkillClientSetup.SLOT_KEYS[i].matches(keyCode, scanCode)) {
                    notice(Component.translatable("gui.skyabyss.skill.passive_no_slot", Component.translatable(selected.nameKey())));
                    return true;
                }
            }
        }
        if (tryon == null && page == 0 && selected != null && selected.active && data().knows(selected)) {
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

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (page == 2 && ext.scroll(mx, my, scrollY, real(), left, top)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
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
        if (page == 0) {
            renderTreeTabs(g, d);
            renderTree(g, d, level);
            hoverRune = 0;
            renderInfo(g, d, level, mouseX, mouseY);
        } else if (page == 1) {
            runeRows.clear();
            renderBuild(g, d, level, mouseX, mouseY);
        } else {
            runeRows.clear();
            ext.render(g, font, minecraft, d, left, top, mouseX, mouseY);
        }
        renderBottom(g, d, level);
        if (tryon != null && page == 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.skill.tryon_banner", tryonOrder.size()), left + 38, top + 25 + 0, 0xFF7FFFA0, false);
        }
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
        int size = tabSize(trees.size());
        for (int i = 0; i < trees.size(); i++) {
            SkillTree t = trees.get(i);
            int x = left + TREE_TAB_X;
            int y = top + TREE_TAB_Y + i * tabStep(trees.size());
            int state = t == tree ? 1 : 0;
            g.blit(SkillHud.TEXTURE, x, y, size, size, 240 + state * 56, 384, 52, 52, SkillHud.TEX_W, SkillHud.TEX_H);
            SkillHud.drawIcon(g, t.iconIndex(), x + 2, y + 2, size - 4);
            // 天職の印: 1 つ目は金、2 つ目は青緑の小さな点
            if (t.id.equals(d.calling)) {
                g.fill(x + size - 5, y + 1, x + size - 1, y + 5, 0xFF000000 | GOLD);
            } else if (t.id.equals(d.calling2)) {
                g.fill(x + size - 5, y + 1, x + size - 1, y + 5, 0xFF000000 | LORE);
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
        if (tryon != null) {
            head = Component.empty();
        }
        g.drawString(font, head, left + 38, top + 25, headColor, false);
        if (tryon == null && SkillTag.of(tree) != null && tree != SkillTree.TRAVELER) {
            int tier = SkillBuild.setTier(d, tree);
            int hx = left + 38 + font.width(head) + 8;
            for (int i = 0; i < 3; i++) {
                g.fill(hx + i * 6, top + 27, hx + i * 6 + 4, top + 31, i < tier ? 0xFF000000 | GOLD : 0xFF3A404C);
            }
        }
        if (tree == SkillTree.PAST) {
            // 前世の点 (通常の点とは別枠)
            Component pool = Component.translatable("gui.skyabyss.ext.past_points", d.pastLeft(), d.pastPool());
            g.drawString(font, pool, left + 38 + font.width(head) + 8, top + 25, d.pastLeft() > 0 ? PALE : SOFT, false);
        }
        if (tree == SkillTree.HYBRID) {
            renderRing(g, d);
        }
        // 行の目印 (要るレベル。淵は要る転生の回数。橋と前世の技は行の目印なし)
        for (int tier = 0; tier < 4 && tree != SkillTree.HYBRID && tree != SkillTree.PAST; tier++) {
            boolean deep = tree == SkillTree.DEEP;
            int need = deep ? new int[] {1, 3, 4, 5}[tier] : new int[] {1, 5, 12, 20}[tier];
            String lv = deep ? "転" + need : "Lv" + need;
            g.pose().pushPose();
            g.pose().translate(left + 146, top + TIER_Y[tier] + 7, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, lv, 0, 0, (deep ? d.rebirth : level) >= need ? SOFT : DIM, false);
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
            boolean reachable = d.requirementsMet(s) && level >= s.minLevel && allowed && s.rebirthAt() <= d.rebirth && d.bridgeOpen(s);
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
                String r = d.rebirth < s.rebirthAt() ? "転" + s.rebirthAt() : s.keystone() ? "" : rank + "/" + d.rankLimit(s);
                boolean prov = tryon != null && rank > real().rank(s);
                int color = prov ? 0x7FFFA0 : rank >= d.rankLimit(s) ? GOLD : rank > 0 ? PALE : d.canLearn(s, level) == SkillData.LearnResult.OK ? TEXT : DIM;
                g.pose().pushPose();
                g.pose().translate(x + NODE / 2f, y + NODE + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, r, -font.width(r) / 2, 0, color, false);
                g.pose().popPose();
            }
            // タグの色の点 (左上)
            SkillTag tg = SkillTag.of(s);
            if (tg != null) {
                g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF000000 | tg.rgb);
            }
            // 能動の技には小さな菱形の印 (枠に置ける)。刻印を選んでいれば金
            if (s.active) {
                int dot = d.rune(s) > 0 ? 0xFFF2C14E : rank > 0 ? 0xFF9FE8FF : 0xFF4A5060;
                g.fill(x + NODE - 4, y + 1, x + NODE - 1, y + 4, dot);
            }
        }
    }

    // 橋の輪: 角に 5 本の木の印 (覚えた技の数 n/3)、辺に橋。橋から両隣の木へ線を引く
    private static final SkillTree[] RING_TREES = {SkillTree.ROCK, SkillTree.WIND, SkillTree.TIDE, SkillTree.ASH, SkillTree.BONE};

    private void renderRing(GuiGraphics g, SkillData d) {
        int cx = left + RING_CX;
        int cy = top + RING_CY;
        for (Skill s : Skill.inTree(SkillTree.HYBRID)) {
            SkillTree[] parents = s.bridgeTrees();
            int nx = nodeX(s) + NODE / 2;
            int ny = nodeY(s) + NODE / 2;
            for (int k = 0; k < 2; k++) {
                int vi = (s.column + k) % 5;
                double a = ringAngle(vi, false);
                int vx = cx + (int) Math.round(Math.cos(a) * RING_TREE_R);
                int vy = cy + (int) Math.round(Math.sin(a) * RING_TREE_R);
                boolean ok = d.knownIn(parents[k]) >= SkillData.BRIDGE_SKILLS_PER_TREE;
                diag(g, nx, ny, vx, vy, ok ? LORE : DIM);
            }
        }
        for (int i = 0; i < 5; i++) {
            double a = ringAngle(i, false);
            int vx = cx + (int) Math.round(Math.cos(a) * RING_TREE_R);
            int vy = cy + (int) Math.round(Math.sin(a) * RING_TREE_R);
            int n = d.knownIn(RING_TREES[i]);
            boolean ok = n >= SkillData.BRIDGE_SKILLS_PER_TREE;
            g.fill(vx - 8, vy - 8, vx + 8, vy + 8, 0xFF161A22);
            g.fill(vx - 8, vy - 8, vx + 8, vy - 7, ok ? 0xFF000000 | GOLD : 0xFF3A4452);
            SkillHud.drawIcon(g, RING_TREES[i].iconIndex(), vx - 7, vy - 7, 14);
            String t = Math.min(n, 9) + "/" + SkillData.BRIDGE_SKILLS_PER_TREE;
            g.pose().pushPose();
            g.pose().translate(vx, vy + 9, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, t, -font.width(t) / 2, 0, ok ? GOLD : SOFT, false);
            g.pose().popPose();
        }
    }

    private static void diag(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, 0xFF000000 | color);
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
            SkillTag treeTag = SkillTag.of(tree);
            if (treeTag != null && tree != SkillTree.TRAVELER) {
                int tier = SkillBuild.setTier(d, tree);
                lines.add(Component.translatable("gui.skyabyss.skill.set_head", d.treePoints(tree), tier).withColor(PALE).getVisualOrderText());
                for (int i = 0; i < 3; i++) {
                    lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.set_line", SkillRules.SET_AT[i],
                            Component.translatable("skillset.skyabyss." + treeTag.id + "." + (i + 1))).withColor(i < tier ? GOLD : DIM), w));
                }
                lines.add(FormattedCharSequence.EMPTY);
            }
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
        if (s.active) {
            kind.append(Component.literal("・")).append(Component.translatable("gui.skyabyss.skill.mastery_short", d.masteryLevel(s)));
        }
        if (d.granted(s)) {
            kind.append(Component.literal("・")).append(Component.translatable("gui.skyabyss.skill.granted"));
        }
        lines.add(kind.withColor(SOFT).getVisualOrderText());
        if (s.active) {
            int r = Math.max(1, rank);
            // 試着の最中は、仮の段の数字 (待ち・マナは基準の値) を見せる
            boolean live = tryon == null && rank > 0;
            int cooldown = live ? SkillManager.cooldownFor(minecraft.player, s) : SkillRules.cooldownTicks(s, r);
            int mana = live ? SkillManager.manaCostFor(minecraft.player, s) : SkillRules.manaCost(s, r);
            lines.add(Component.translatable("gui.skyabyss.skill.cost", mana, SkillRules.fmtSeconds(cooldown))
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
        // 変異 (A08): 選んでいれば名前と形を添える
        Mutation mutation = jp.revati.skyabyss.rpg.skill.Mutation.of(s);
        if (mutation != null && d.mutations.containsKey(s.id)) {
            body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.mutated_line", Component.translatable(mutation.nameKey()),
                    Component.translatable(mutation.effectKey())).withColor(GOLD), w));
        }
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
            if (s.rebirthAt() > d.rebirth) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.need_rebirth", s.rebirthAt()).withColor(DENY), w));
            }
            if (!d.bridgeOpen(s)) {
                SkillTree[] bt = s.bridgeTrees();
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.need_bridge", Component.translatable(bt[0].nameKey()),
                        d.knownIn(bt[0]), Component.translatable(bt[1].nameKey()), d.knownIn(bt[1]), SkillData.BRIDGE_SKILLS_PER_TREE).withColor(DENY), w));
            }
            if (s.tree == SkillTree.PAST && d.pastLeft() < 1 && s.rebirthAt() <= d.rebirth) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.need_past_points").withColor(DENY), w));
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
            if (s.tree != SkillTree.PAST && d.unspentPoints(level) < 1 && level >= s.minLevel && d.requirementsMet(s) && d.callingAllows(s)
                    && s.rebirthAt() <= d.rebirth && d.bridgeOpen(s)) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_points").withColor(DENY), w));
            }
        } else if (limit < s.maxRank) {
            body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.rank_capped", limit).withColor(SOFT), w));
        }
        // 刻印 (使う技のうち、刻印のあるもの): 3 つの行。極めていなければ理由
        List<Rune> runes = s.runes();
        boolean legend = legendShown(d, s);
        boolean hasRunes = s.active;
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

    // 狭い箱に名前を収める: 幅が足りなければ 0.7 倍まで縮め、それでも足りなければ「…」で切る (中心 cx、上端 y)
    private void drawFit(GuiGraphics g, Component name, int cx, int y, int maxW, int color) {
        String text = name.getString();
        int w = font.width(text);
        float k = w <= maxW ? 1f : Math.max(0.7f, maxW / (float) w);
        if (w * k > maxW) {
            text = font.plainSubstrByWidth(text, (int) (maxW / k) - font.width("…")) + "…";
            w = font.width(text);
        }
        g.pose().pushPose();
        g.pose().translate(cx - w * k / 2f, y + (1f - k) * 3.5f, 0f);
        g.pose().scale(k, k, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void renderRunes(GuiGraphics g, SkillData d, Skill s, int x, int y, int w, int mx, int my) {
        boolean available = d.runeAvailable(s);
        int current = d.rune(s);
        MutableComponent head = Component.translatable("gui.skyabyss.skill.runes_head");
        if (!available) {
            head.append(Component.literal("  ")).append(Component.translatable(
                    d.rank(s) >= s.maxRank ? "gui.skyabyss.skill.runes_need_mastery" : "gui.skyabyss.skill.runes_need_max", d.rank(s), s.maxRank));
        } else if (current == 0) {
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.runes_pick"));
        }
        g.drawString(font, head, x, y, available ? PALE : DIM, false);
        int ry = y + 11;
        List<Rune> list = s.runes();
        int cells = list.size() + 1;
        int cell = (w - 4 - (cells - 1) * 2) / cells;
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
            drawFit(g, name, rx + cell / 2, ry + 2, cell - 3, color);
            runeRows.add(new int[] {rx, ry, cell, 12, r.index});
            if (hover) {
                hoverRune = r.index;
            }
        }
        // 熟練の刻印 (最後の 1 つ。熟練 3 から)
        {
            int rx = x + list.size() * (cell + 2);
            boolean usable = d.runeUsable(s, Rune.MASTERY);
            boolean mine = current == Rune.MASTERY;
            boolean hover = in(mx, my, rx, ry, cell, 12);
            int bg = mine ? 0xFF2A3A40 : hover && usable ? 0xFF222B36 : 0xFF161A22;
            int edge = mine ? 0xFF000000 | GOLD : runeArmed == Rune.MASTERY ? 0xFF000000 | DENY : usable ? 0xFF6A8F5A : 0xFF2A2E38;
            g.fill(rx, ry, rx + cell, ry + 12, edge);
            g.fill(rx + 1, ry + 1, rx + cell - 1, ry + 11, bg);
            Component name = Component.translatable("rune.skyabyss.mastery");
            drawFit(g, name, rx + cell / 2, ry + 2, cell - 3, mine ? GOLD : usable ? 0xB8F2A8 : DIM);
            runeRows.add(new int[] {rx, ry, cell, 12, Rune.MASTERY});
            if (hover) {
                hoverRune = Rune.MASTERY;
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
        if (shown == Rune.MASTERY) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("rune.skyabyss.mastery.effect", SkillRules.MASTERY_RUNE_LEVEL,
                    d.masteryLevel(s)).withColor(shown == current ? TEXT : PALE), w)) {
                if (ty + 9 > top + INFO_Y + INFO_H - 1) {
                    break;
                }
                g.drawString(font, line, x, ty, TEXT, false);
                ty += 10;
            }
        }
        if (shownRune != null && !shownRune.legendary() && ty + 9 <= top + INFO_Y + INFO_H - 1) {
            // 箱に収まらず縮めた名前も、ここで全文が読める
            g.drawString(font, Component.translatable(shownRune.nameKey()).withColor(GOLD), x, ty, GOLD, false);
            ty += 10;
        }
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
        FormattedCharSequence last = null;
        int lastY = 0;
        for (FormattedCharSequence line : lines) {
            if (y + 9 > maxY) {
                // 入りきらない続きがある: 最後の行の終わりに「…」で知らせる (途中で黙って切れない)
                if (last != null) {
                    g.drawString(font, "…", x + font.width(last) + 1, lastY, SOFT, false);
                }
                break;
            }
            g.drawString(font, line, x, y, TEXT, false);
            last = line;
            lastY = y;
            y += 10;
        }
    }

    private boolean subTabHovered() {
        for (SubTab t : subTabs) {
            if (t.isHovered()) {
                return true;
            }
        }
        return false;
    }

    private void renderBottom(GuiGraphics g, SkillData d, int level) {
        int points = d.unspentPoints(level);
        g.drawString(font, Component.translatable(tryon != null ? "gui.skyabyss.skill.points_tryon" : "gui.skyabyss.skill.points", points), left + 10, top + 158,
                tryon != null ? 0x7FFFA0 : points > 0 ? GOLD : SOFT, false);
        // 枠の組 (近接など / 弓): 武器を持ち替えると入れ替わる
        Component barLabel = Component.translatable(real().bar == 1 ? "gui.skyabyss.skill.bar_bow" : "gui.skyabyss.skill.bar_melee");
        g.drawString(font, barLabel, left + SLOT_X - 3 - font.width(barLabel), top + SLOT_Y + 7, SOFT, false);
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
        if (page == 1 && buildTooltip(g, real(), mx, my)) {
            return;
        }
        Skill node = page == 0 ? nodeAt(mx, my) : null;
        if (node != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(node.nameKey()).withColor(GOLD));
            lines.add(Component.translatable(node.keystone() ? "gui.skyabyss.skill.kind_keystone"
                    : node.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive").withColor(SOFT));
            SkillTag nodeTag = SkillTag.of(node);
            if (nodeTag != null) {
                lines.add(Component.translatable("gui.skyabyss.skill.tag_line", Component.translatable(nodeTag.nameKey())).withColor(nodeTag.rgb));
            }
            if (node.active) {
                int ml = d.masteryLevel(node);
                int xp = d.masteryXp(node);
                lines.add(Component.translatable("gui.skyabyss.skill.mastery_line", ml, xp,
                        ml >= SkillRules.MASTERY_AT.length ? "-" : String.valueOf(SkillRules.MASTERY_AT[ml])).withColor(PALE));
            }
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
        SkillTree t = page == 0 ? treeTabAt(mx, my) : null;
        if (t != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(t.nameKey()).withColor(GOLD));
            lines.add(Component.translatable(t.descKey()).withColor(SOFT));
            SkillTag tabTag = SkillTag.of(t);
            if (tabTag != null && t != SkillTree.TRAVELER) {
                int tier = SkillBuild.setTier(d, t);
                lines.add(Component.translatable("gui.skyabyss.skill.set_head", d.treePoints(t), tier).withColor(PALE));
                for (int i = 0; i < 3; i++) {
                    lines.add(Component.translatable("gui.skyabyss.skill.set_line", SkillRules.SET_AT[i],
                            Component.translatable("skillset.skyabyss." + tabTag.id + "." + (i + 1))).withColor(i < tier ? GOLD : DIM));
                }
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        for (int i = 0; i < SkillRules.LOADOUTS; i++) {
            if (loadLoadout[i].isHovered()) {
                SkillData rd = real();
                List<FormattedCharSequence> lines = new ArrayList<>(TextWrap.split(font, Component.translatable(saveMode ? "gui.skyabyss.skill.loadout_save_tip" : "gui.skyabyss.skill.loadout_load_tip",
                        i + 1, rd.loadouts[i].saved ? Component.translatable("gui.skyabyss.skill.loadout_saved") : Component.translatable("gui.skyabyss.skill.loadout_empty")).withColor(TEXT), 190));
                g.renderTooltip(font, lines, mx, my);
                return;
            }
        }
        if (saveToggle.isHovered() || tryonBtn.isHovered() || subTabHovered() || tryonOk.isHovered() || tryonUndo.isHovered() || tryonCancel.isHovered()) {
            String key = saveToggle.isHovered() ? "gui.skyabyss.skill.loadout_mode_tip" : tryonBtn.isHovered() ? "gui.skyabyss.skill.tryon_tip"
                    : subTabHovered() ? "gui.skyabyss.skill.page_tip" : tryonOk.isHovered() ? "gui.skyabyss.skill.tryon_ok_tip"
                    : tryonUndo.isHovered() ? "gui.skyabyss.skill.tryon_undo_tip" : "gui.skyabyss.skill.tryon_cancel_tip";
            g.renderTooltip(font, new ArrayList<>(TextWrap.split(font, Component.translatable(key, SkillRules.LOADOUTS).withColor(TEXT), 190)), mx, my);
            return;
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

    // ---------------------------------------------------------------- ビルドのページ (構え・連鎖・タグ・鍵石)
    private void pressHit(int type, int arg) {
        switch (type) {
            case 1 -> {
                SkillData r = real();
                if (r.stance == arg) {
                    return;
                }
                if (r.stanceCd > 0 && !minecraft.player.isCreative()) {
                    notice(Component.translatable("message.skyabyss.skill.stance_cooldown"));
                    return;
                }
                PacketDistributor.sendToServer(new SkillStancePayload(arg));
                click();
            }
            case 2 -> {
                SkillData r = real();
                if ((r.chainMask & (1 << arg)) == 0 && Integer.bitCount(r.chainMask) >= SkillRules.CHAIN_MAX) {
                    notice(Component.translatable("message.skyabyss.skill.chain_failed.too_many"));
                    return;
                }
                PacketDistributor.sendToServer(new SkillChainPayload(arg));
                click();
            }
            case 3 -> {
                SkillData r = real();
                SkillKeystone k = SkillKeystone.values()[arg];
                if (!r.keystones.contains(k.id)) {
                    if (r.keystones.size() >= SkillRules.KEYSTONE_MAX) {
                        notice(Component.translatable("message.skyabyss.skill.keystone_failed.full"));
                        return;
                    }
                    if (!r.keystoneReachable(k)) {
                        notice(Component.translatable("message.skyabyss.skill.keystone_failed.not_reached", SkillRules.KEYSTONE_TREE_POINTS,
                                Component.translatable(k.tag.tree.nameKey())));
                        return;
                    }
                    if (r.unspentPoints(level()) < 1) {
                        notice(Component.translatable("gui.skyabyss.skill.need_points"));
                        return;
                    }
                }
                PacketDistributor.sendToServer(new SkillKeystonePayload(k.id));
                click();
            }
            default -> {
            }
        }
    }

    private void renderBuild(GuiGraphics g, SkillData d, int level, int mx, int my) {
        hits.clear();
        int x0 = left + 12;
        int colW = 170;
        int y = top + 24;
        // 構え
        g.drawString(font, Component.translatable("gui.skyabyss.skill.stance_head"), x0, y, GOLD, false);
        g.fill(x0, y + 9, x0 + colW, y + 10, 0x553A4452);
        Component cd = d.stanceCd > 0 ? Component.translatable("gui.skyabyss.skill.stance_cd", SkillRules.fmtSeconds(d.stanceCd))
                : Component.translatable("gui.skyabyss.skill.stance_ready");
        g.drawString(font, cd, x0 + colW - font.width(cd), y, d.stanceCd > 0 ? DENY : SOFT, false);
        y += 11;
        SkillStance shown = SkillStance.of(d.stance);
        for (int i = 0; i < 4; i++) {
            SkillStance st = SkillStance.of(i);
            int cx = x0 + i * 43;
            boolean mine = d.stance == i;
            boolean hover = in(mx, my, cx, y, 41, 13);
            g.fill(cx, y, cx + 41, y + 13, mine ? 0xFF000000 | GOLD : hover ? 0xFF6A7A8A : 0xFF3A4452);
            g.fill(cx + 1, y + 1, cx + 40, y + 12, mine ? 0xFF2A3A40 : hover ? 0xFF222B36 : 0xFF161A22);
            Component name = Component.translatable(st.nameKey());
            drawFit(g, name, cx + 20, y + 3, 37, mine ? GOLD : TEXT);
            hits.add(new int[] {cx, y, 41, 13, 1, i});
            if (hover) {
                shown = st;
            }
        }
        y += 15;
        int dy = y;
        for (FormattedCharSequence line : TextWrap.split(font, Component.translatable(shown.descKey()).withColor(SOFT), colW)) {
            if (dy + 9 > top + 84) {
                break;
            }
            g.drawString(font, line, x0, dy, SOFT, false);
            dy += 10;
        }
        // 連鎖
        y = top + 86;
        g.drawString(font, Component.translatable("gui.skyabyss.skill.chain_head", Integer.bitCount(d.chainMask), SkillRules.CHAIN_MAX), x0, y, GOLD, false);
        g.fill(x0, y + 9, x0 + colW, y + 10, 0x553A4452);
        y += 11;
        for (int i = 0; i < d.slotCount(); i++) {
            int cx = x0 + i * 32;
            g.blit(SkillHud.TEXTURE, cx, y, 18, 18, 420, 384, 44, 44, SkillHud.TEX_W, SkillHud.TEX_H);
            Skill s = d.slot(i);
            if (s != null) {
                SkillHud.drawIcon(g, s.iconIndex(), cx + 1, y + 1);
            }
            if (i + 1 < d.slotCount()) {
                boolean on = (d.chainMask & (1 << i)) != 0;
                int bx = cx + 20;
                boolean hover = in(mx, my, bx, y, 10, 18);
                g.fill(bx, y + 4, bx + 10, y + 14, on ? 0xFF000000 | GOLD : hover ? 0xFF6A7A8A : 0xFF3A4452);
                g.fill(bx + 1, y + 5, bx + 9, y + 13, on ? 0xFF3A3220 : 0xFF161A22);
                g.drawString(font, "\u203a", bx + 3, y + 5, on ? GOLD : SOFT, false);
                hits.add(new int[] {bx, y + 4, 10, 10, 2, i});
            }
        }
        // タグ
        y = top + 118;
        g.drawString(font, Component.translatable("gui.skyabyss.skill.tag_head"), x0, y, GOLD, false);
        g.fill(x0, y + 9, x0 + colW, y + 10, 0x553A4452);
        y += 11;
        for (int i = 0; i < SkillTag.values().length; i++) {
            SkillTag t = SkillTag.values()[i];
            int cx = x0 + i * 34;
            int n = SkillBuild.tagCount(minecraft.player, t);
            boolean hover = in(mx, my, cx, y, 32, 13);
            g.fill(cx, y, cx + 32, y + 13, hover ? 0xFF6A7A8A : 0xFF3A4452);
            g.fill(cx + 1, y + 1, cx + 31, y + 12, 0xFF161A22);
            g.fill(cx + 3, y + 4, cx + 8, y + 9, 0xFF000000 | t.rgb);
            String num = String.valueOf(n);
            g.drawString(font, num, cx + 12, y + 3, n >= SkillRules.TAG_STEP2 ? GOLD : n >= SkillRules.TAG_STEP1 ? PALE : n > 0 ? TEXT : DIM, false);
            if (SkillBuild.condActive(minecraft.player, t) && n >= SkillRules.COND_MIN_TAGS) {
                g.fill(cx + 25, y + 4, cx + 29, y + 8, 0xFF000000 | GOLD);
            }
            hits.add(new int[] {cx, y, 32, 13, 4, i});
            // 凡例: 色の点の意味 (タグの名前) を札の下に小さく
            g.pose().pushPose();
            Component tagName = Component.translatable(t.nameKey());
            float k = Math.min(0.75f, 31f / Math.max(1, font.width(tagName)));
            g.pose().translate(cx + 16 - font.width(tagName) * k / 2f, y + 15, 0);
            g.pose().scale(k, k, 1f);
            g.drawString(font, tagName, 0, 0, 0xFF000000 | t.rgb, false);
            g.pose().popPose();
        }
        // 二つの列の境の線
        g.fill(left + 188, top + 24, left + 189, top + 148, 0x553A4452);
        // 鍵石 (右の列)
        int x1 = left + 194;
        int w1 = 176;
        y = top + 24;
        g.drawString(font, Component.translatable("gui.skyabyss.skill.keystone_head", d.keystones.size(), SkillRules.KEYSTONE_MAX), x1, y, GOLD, false);
        g.fill(x1, y + 9, x1 + w1, y + 10, 0x553A4452);
        y += 11;
        for (int i = 0; i < SkillKeystone.values().length; i++) {
            SkillKeystone k = SkillKeystone.values()[i];
            boolean mine = d.keystones.contains(k.id);
            boolean reach = d.keystoneReachable(k);
            boolean hover = in(mx, my, x1, y, w1, 17);
            g.fill(x1, y, x1 + w1, y + 17, mine ? 0xFF000000 | GOLD : hover ? 0xFF6A7A8A : reach ? 0xFF4A5A6A : 0xFF2A2E38);
            g.fill(x1 + 1, y + 1, x1 + w1 - 1, y + 16, mine ? 0xFF2A3A40 : hover ? 0xFF222B36 : 0xFF161A22);
            g.fill(x1 + 3, y + 3, x1 + 7, y + 7, 0xFF000000 | k.tag.rgb);
            g.drawString(font, Component.translatable(k.nameKey()), x1 + 10, y + 1, mine ? GOLD : reach ? TEXT : DIM, false);
            g.pose().pushPose();
            g.pose().translate(x1 + 10, y + 10.5f, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, Component.translatable(k.effectKey()), 0, 0, mine ? PALE : reach ? SOFT : DIM, false);
            g.pose().popPose();
            hits.add(new int[] {x1, y, w1, 17, 3, i});
            y += 18;
        }
        if (noticeTicks > 0 && noticeText != null) {
            int alpha = Math.min(255, noticeTicks * 12);
            List<FormattedCharSequence> lines = TextWrap.split(font, noticeText, 330);
            int ny = top + 146 - lines.size() * 10;
            g.fill(left + 10, ny - 2, left + W - 10, top + 149, (Math.min(200, alpha) << 24) | 0x1A1020);
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left + 14, ny, (alpha << 24) | DENY, false);
                ny += 10;
            }
        }
    }

    private boolean buildTooltip(GuiGraphics g, SkillData d, int mx, int my) {
        for (int[] h : hits) {
            if (!in(mx, my, h[0], h[1], h[2], h[3])) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            switch (h[4]) {
                case 1 -> {
                    SkillStance st = SkillStance.of(h[5]);
                    lines.add(Component.translatable(st.nameKey()).withColor(GOLD));
                    lines.add(Component.translatable(st.descKey()).withColor(SOFT));
                    lines.add(Component.translatable("gui.skyabyss.skill.stance_tip", SkillClientSetup.STANCE_KEY.getTranslatedKeyMessage(),
                            SkillRules.fmtSeconds(SkillRules.STANCE_SWAP_TICKS)).withColor(DIM));
                }
                case 2 -> {
                    lines.add(Component.translatable("gui.skyabyss.skill.chain_tip", SkillRules.CHAIN_MAX, SkillRules.fmtSeconds(SkillRules.CHAIN_WINDOW),
                            Math.round(SkillRules.CHAIN_POWER * 100)).withColor(TEXT));
                }
                case 3 -> {
                    SkillKeystone k = SkillKeystone.values()[h[5]];
                    lines.add(Component.translatable(k.nameKey()).withColor(k.tag.rgb));
                    lines.add(Component.translatable(k.effectKey()).withColor(TEXT));
                    lines.add(Component.translatable("gui.skyabyss.skill.keystone_need", SkillRules.KEYSTONE_TREE_POINTS, Component.translatable(k.tag.tree.nameKey()),
                            d.treePoints(k.tag.tree)).withColor(d.keystoneReachable(k) ? SOFT : DENY));
                }
                default -> {
                    SkillTag t = SkillTag.values()[h[5]];
                    int n = SkillBuild.tagCount(minecraft.player, t);
                    int extra = SkillBuild.extraTagCount(minecraft.player, t);
                    lines.add(Component.translatable(t.nameKey()).withColor(t.rgb));
                    lines.add(Component.translatable("gui.skyabyss.skill.tag_count", n, n - extra, extra,
                            Math.round(SkillRules.TAG_POWER_PER * Math.min(SkillRules.TAG_COUNT_CAP, n) * 100)).withColor(TEXT));
                    lines.add(Component.translatable("gui.skyabyss.skill.tag_step", SkillRules.TAG_STEP1, SkillRules.TAG_STEP2,
                            Component.translatable("tag.skyabyss." + t.id + ".step")).withColor(n >= SkillRules.TAG_STEP1 ? PALE : DIM));
                    lines.add(Component.translatable("gui.skyabyss.skill.tag_cond", SkillRules.COND_MIN_TAGS, SkillRules.COND_BIG_TAGS,
                            Component.translatable("tag.skyabyss." + t.id + ".cond")).withColor(n >= SkillRules.COND_MIN_TAGS ? PALE : DIM));
                }
            }
            g.renderComponentTooltip(font, lines, mx, my);
            return true;
        }
        return false;
    }
}
