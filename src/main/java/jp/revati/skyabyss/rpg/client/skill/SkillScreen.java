package jp.revati.skyabyss.rpg.client.skill;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.PanZoomCanvas;
import jp.revati.skyabyss.client.ui.Paper;
import jp.revati.skyabyss.client.ui.ScrollPanel;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Mutation;
import jp.revati.skyabyss.rpg.skill.Rune;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillTag;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.SkillTree;
import jp.revati.skyabyss.rpg.skill.Skills;
import jp.revati.skyabyss.rpg.skill.WeaponType;
import jp.revati.skyabyss.rpg.skill.net.SkillLearnPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillNodePayload;
import jp.revati.skyabyss.rpg.skill.net.SkillTryonPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillLoadoutPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillRespecPayload;
import jp.revati.skyabyss.rpg.skill.net.SkillRunePayload;
import jp.revati.skyabyss.rpg.skill.net.SkillSlotPayload;
import jp.revati.skyabyss.rpg.skill.tree.AbilityNode;
import jp.revati.skyabyss.rpg.skill.tree.AbilityTree;
import jp.revati.skyabyss.rpg.skill.tree.AbilityTrees;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Golden;

// 技の画面 (K キー)。左に木のつまみ (岩守り・風読み・潮歌い・灰の鍛え人・骨の射手・旅人)、真ん中に木 (4 行。線は前提)、
// 右に選んだ技の説明と「習得」・刻印の選び、下に技の点・マナ・技の枠 (4〜5)・型の保存 (2)・振り直し。
// 技を選んでから枠を押すと置ける (右クリックで空ける)。天職を選ぶまでは、木の 1 行目だけが開いている (SkillRules.CALLINGS)。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 見出しの帯に記録・技・旅のつまみと木のつまみと頁のつまみ、
// 中身は主の列 (0.618) に木の盤、脇の列 (0.382) に説明、下の段は左に SP・マナ・技の枠・秘伝、右に型の保存・振り直し・試着
public class SkillScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/skill/builds_panel.png");
    // 切り絵の舞台 (論理の 408x228。docs/design/56 の「切り絵」)。黒い紙と下の桃色の山は窓の端まで敷く
    static final int W = 408;
    static final int H = 228;
    // 見出し (記録・スキル・旅のつまみ、木のつまみ、職と木の名前、右にページのつまみ)
    static final Golden.Box HEAD = new Golden.Box(10, 5, 388, 21);
    // 左に木の盤 (黒い紙の上)、右に傾けた生成りの帯の中の説明の欄 (字の欄は帯に内接するまっすぐな四角)
    static final Golden.Box BOARD = new Golden.Box(8, 31, 248, 152);
    static final Golden.Box INFO = new Golden.Box(266, 31, 134, 152);
    // 名残のページの中身 (盤と説明の欄を合わせた広さ)
    static final Golden.Box BODY = new Golden.Box(8, 31, 392, 152);
    // 下の段 (桃色の山の上): 左に SP・マナ・技の枠・秘伝、右に型の保存・振り直し・試着
    static final Golden.Box FOOT_L = new Golden.Box(8, 187, 248, 34);
    static final Golden.Box FOOT_R = new Golden.Box(266, 187, 134, 34);
    // 木のつまみ (見出しの帯の中、記録・技・旅のつまみの右。絵 16、間隔 21)
    static final int TREE_TAB = 16;
    static final int TREE_TAB_STEP = Golden.S21;
    // 説明の欄と、その中の行の高さ
    static final int LH = Golden.ROW;
    // 技の印 (22x22) の位置: 列 0・1・2 (真ん中) と行 0〜3
    // 木の中の位置 (盤の単位。盤の左上が原点)。2026-10-22 の部屋の見直しで、間隔を広げて盤の上で動かせるようにした
    static final int[] COL_X = {12, 130, 71};
    static final int[] TIER_Y = {0, 56, 112, 168};
    // 盤の大きさ (盤の単位) と、盤を映す窓 (画面の左上からの位置と大きさ)
    static final int BOARD_W = 196;
    static final int BOARD_H = 192;
    static final int BOARD_VX = BOARD.x() + 1;
    static final int BOARD_VY = BOARD.y() + 1;
    static final int BOARD_VW = BOARD.w() - 2;
    static final int BOARD_VH = BOARD.h() - 2;
    static final int NODE = 22;
    // 説明の欄
    static final int INFO_X = INFO.x();
    static final int INFO_Y = INFO.y();
    static final int INFO_W = INFO.w();
    static final int INFO_H = INFO.h();
    // 取る (習得) の釦: 説明の欄の下の端に、欄の幅いっぱいで。中身はその上まで送る
    static final int LEARN_Y = INFO.bottom() - Golden.S5 - Golden.BTN;
    static final int INFO_BODY_BOTTOM = LEARN_Y - Golden.S3;
    // 技の枠 (下の段の左。SP とマナの右)
    static final int SLOT_X = FOOT_L.x() + 75;
    static final int SLOT_Y = FOOT_L.y() + 6;
    static final int SLOT_STEP = NODE + Golden.S3;
    // 型の保存・振り直し・試着の釦 (下の段の右。二列)
    // 三つの釦の列の幅 (どの行もこの幅にそろえて、欄の真ん中に)
    static final int BTN_ROW_W = 3 * Golden.S34 + 2 * Golden.S3;
    static final int BTN_X = FOOT_R.x() + (FOOT_R.w() - BTN_ROW_W) / 2;
    static final int BTN_ROW1 = FOOT_R.y() + 3;
    static final int BTN_ROW2 = FOOT_R.y() + 18;
    // ページのつまみ (見出しの右寄せ)
    static final int SUB_W = 44;
    static final int SUB_H = Golden.BTN;
    static final int SUB_X = HEAD.right() - 2 * SUB_W - Golden.S3;
    static final int SUB_Y = HEAD.y() + 4;

    static final int TITLE = 0x8FD0FF;
    static final int TEXT = 0xECE8DC;
    static final int SOFT = Golden.SOFT;
    static final int GOLD = Golden.GOLD;
    static final int PALE = Golden.PALE;
    static final int LORE = Golden.TEAL;
    static final int DENY = Golden.DENY;
    static final int DIM = Golden.DIM;

    private static SkillTree lastTree = SkillTree.ROCK;
    private SkillTree tree = lastTree;
    // 3 ページ目 (変異・紋の盤)
    private final ExtPage ext = new ExtPage();
    private Skill selected;
    // 木 v2 (docs/design/48): 職の木では節を選ぶ。selected はその節の技 (枠に置くため)
    private AbilityNode selectedNode;
    // 木 v2: いまマウスの下にある節 (その節へつながる線を光らせる)
    private AbilityNode hoverNode;
    // 木 v2 の格子の間隔 (盤の単位。節は NODE = 22)
    static final int GRID = 34;
    static final int GRID_X0 = 8;
    static final int GRID_Y0 = 14;
    private int left;
    private int top;
    // 木の盤 (動かせて拡大できる。画面の中の窓は BOARD_V*)
    private final PanZoomCanvas canvas = new PanZoomCanvas();
    // 説明の欄 (送れる面)。名前の行は上に残し、その下の中身を送る
    private final ScrollPanel info = new ScrollPanel(LH * 3);
    // 板の大きさを窓に合わせる変換 (描くのも当たりも、この一つ)
    private final UiFit fit = new UiFit();
    private Button learn;
    private Button respec;
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
    private int respecArmed;
    // 刻印の付け替えの確かめ (押した刻印の番号。0 でなし)
    private int runeArmed;
    private int runeArmedTicks;
    private Component noticeText;
    private int noticeTicks;
    // 説明の欄の刻印の行 (描くたびに作り直す): {x, y, w, h, 刻印の番号}
    private final List<int[]> runeRows = new ArrayList<>();
    private int hoverRune;
    // 動き (docs/design/56 の「切り絵」。JobChoiceScreen と同じ言葉): 開くと節が段ごとに跳ねて並び、節を取ると判が押され、
    // 親からの糸が桃色に満ちる。取ったかどうかは描くたびの差で見る (鯖の返事でも試着でも同じに動く)。
    // 開き直し・木の切り替え・型の読み込み・振り直し・試着の出入りでは差を写すだけで動かさない
    private final long openedAt = net.minecraft.Util.getMillis();
    private long assembleAt = openedAt;
    private long selectedAt;
    private AbilityNode lastSelNode;
    private AbilityNode lastHoverNode;
    private long infoSwapAt = openedAt;
    private String lastInfoKey;
    private SkillTree takenTree;
    private java.util.Set<String> takenSeen;
    private final java.util.Map<String, Long> takenAt = new java.util.HashMap<>();
    private long quietUntil;
    private long lastTakeAt = -100000;
    static final int TAKE_MS = 520;
    private int lastPoints = -1;
    private SkillTree lastTree2;
    private int spentDelta;
    private long spentAt = -100000;
    static final int ROW_STAGGER = 22;
    static final int POP_MS = 180;

    private static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    private static float out(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    private static float back(float t) {
        t = clamp01(t);
        float c = 1.9f;
        float u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    // 説明の札 (節を選ぶと右から入る。選ばないときは黒の上に木の表紙)
    private boolean cardShown;
    private long cardAt = -100000;
    private long coverAt = openedAt;

    // 札の下の端 (舞台の座標)。中身が短ければ縮む (前の絵で測った中身の下端から決め、少しずつ寄せる)
    private float cardBottom = INFO.bottom();
    private int needBottom = INFO.bottom();

    // 送る本文の窓の高さを行の高さの倍に (下の端で行が半分に切れないように)
    private static int wholeRows(int h) {
        return h > LH ? h - h % LH : h;
    }

    private int infoBodyBottom() {
        return Math.round(cardBottom) - Golden.S5 - Golden.BTN - Golden.S3;
    }

    private boolean hasCard() {
        if (page != 0) {
            return false;
        }
        return v2() != null ? selectedNode != null : selected != null && selected.tree == tree;
    }

    private void quiet() {
        quietUntil = net.minecraft.Util.getMillis() + 1500;
    }

    public SkillScreen() {
        super(Component.translatable("gui.skyabyss.skill.title"));
        // 職の武器を持っていれば、その職の木から開く (docs/design/43)
        net.minecraft.client.player.LocalPlayer me = net.minecraft.client.Minecraft.getInstance().player;
        jp.revati.skyabyss.rpg.skill.Job held = me == null ? null : jp.revati.skyabyss.rpg.skill.Job.held(me);
        // 木 v2: 今の職 (職の間で替える) の木から開く
        Job cur = me == null ? null : Job.byId(SkillManager.get(me).curJob);
        if (cur != null) {
            tree = cur.tree;
        } else if (held != null) {
            tree = held.tree;
        }
    }

    @Override
    protected void init() {
        // 板は論理の (0, 0) に置き、窓に合わせた変換 (fit。画素ちょうど) で大きく描く
        left = 0;
        top = 0;
        fit.fit(width, height, W, H, 3f);
        canvas.setViewport(left + BOARD_VX, top + BOARD_VY, BOARD_VW, BOARD_VH);
        canvas.setPixelUnit(fit.pixelUnit());
        // 盤は大きいので、最初は字と節が小さくなりすぎない大きさで上から見せる (残りは送る・拡げる)
        canvas.setStartMin(0.75f);
        fitCanvas();
        learn = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + INFO_X + Golden.S5, top + LEARN_Y, INFO_W - 2 * Golden.S5, Golden.BTN, Component.translatable("gui.skyabyss.skill.learn"), b -> learnSelected(), true));
        // 型の保存は、いま持っている職の 2 つ (docs/design/43)
        for (int i = 0; i < SkillRules.LOADOUTS_PER_JOB; i++) {
            int index = i;
            loadLoadout[i] = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X + i * (Golden.S21 + Golden.S3), top + BTN_ROW1, Golden.S21, Golden.BTN, Component.literal(String.valueOf(i + 1)), b -> pressLoadout(index), false));
        }
        saveToggle = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X + 2 * (Golden.S21 + Golden.S3), top + BTN_ROW1, BTN_ROW_W - 2 * (Golden.S21 + Golden.S3), Golden.BTN,
                Component.translatable("gui.skyabyss.skill.loadout_save_mode"), b -> {
                    saveMode = !saveMode;
                    click();
                }, false));
        respec = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X, top + BTN_ROW2, Golden.S55, Golden.BTN, Component.translatable("gui.skyabyss.skill.respec"), b -> pressRespec(), false));
        tryonBtn = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X + Golden.S55 + Golden.S3, top + BTN_ROW2, BTN_ROW_W - Golden.S55 - Golden.S3, Golden.BTN, Component.translatable("gui.skyabyss.skill.tryon"), b -> toggleTryon(), false));
        String[] subKeys = {"gui.skyabyss.skill.sub_tree", "gui.skyabyss.skill.sub_build", "gui.skyabyss.skill.sub_ext"};
        // ビルドのページ (構え・連鎖・タグ・鍵石) は 43 (6 職) で削った。つまみは「木」と「変異・紋」の二つ
        int[] pages = {0, 2};
        for (int k = 0; k < pages.length; k++) {
            int i = pages[k];
            subTabs[i] = addRenderableWidget(new SubTab(left + SUB_X + k * (SUB_W + Golden.S3), top + SUB_Y, i, Component.translatable(subKeys[i])));
        }
        tryonOk = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X, top + BTN_ROW2, Golden.S34, Golden.BTN, Component.translatable("gui.skyabyss.skill.tryon_ok"), b -> tryonConfirm(), true));
        tryonUndo = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X + Golden.S34 + Golden.S3, top + BTN_ROW2, Golden.S34, Golden.BTN, Component.translatable("gui.skyabyss.skill.tryon_undo"), b -> tryonUndo(), false));
        tryonCancel = addRenderableWidget(new jp.revati.skyabyss.client.ui.Paper.Tag(left + BTN_X + 2 * (Golden.S34 + Golden.S3), top + BTN_ROW2, Golden.S34, Golden.BTN, Component.translatable("gui.skyabyss.skill.tryon_cancel"), b -> tryonCancel(), false));
        // 記録・スキル・旅のつまみは、この画面の中だけ切り絵の札で (ほかの画面は夜空のまま)
        RpgTabs.addHead(this::addRenderableWidget, HEAD, RpgTabs.SKILLS, true);
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
        page = 0;
        return this;
    }

    // 撮影用: 試着中 (仮に振った技を順に) の状態で開く
    private List<Skill> demoTry = new ArrayList<>();

    public SkillScreen tryOn(Skill... skills) {
        demoTry = new ArrayList<>(List.of(skills));
        return this;
    }

    // 撮影用: 木 v2 の節を選んだ状態で開く (今の職の木)
    public SkillScreen selectNode(String id) {
        AbilityNode n = AbilityTrees.node(Job.ofTree(tree), id);
        if (n != null) {
            selectedNode = n;
            selected = n.skill;
        }
        return this;
    }

    // 撮影用: 技を選んだ状態で開く
    // 撮影用: マウスを乗せた節 (浮きと桃色の影。撮影ではマウスを動かせないので)
    private String demoHoverId;

    public SkillScreen demoHover(String id) {
        demoHoverId = id;
        return this;
    }

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
        if (v2() != null) {
            // 木 v2: 「取る」は選んだ節に対して
            SkillData.TakeResult tr = selectedNode == null ? null : d.canTake(tree, selectedNode, level());
            learn.visible = page == 0 && selectedNode != null;
            learn.active = tr == SkillData.TakeResult.OK;
            learn.setMessage(Component.translatable(tr == SkillData.TakeResult.TAKEN ? "gui.skyabyss.tree.taken" : "gui.skyabyss.tree.take"));
        } else {
            learn.active = selected != null && d.canLearn(selected, level()) == SkillData.LearnResult.OK;
            learn.visible = page == 0 && selected != null && d.treeOpen(selected.tree);
            if (selected != null) {
                learn.setMessage(d.rank(selected) >= d.rankLimit(selected) ? Component.translatable("gui.skyabyss.skill.learn_max")
                        : d.knows(selected) ? Component.translatable("gui.skyabyss.skill.learn_up") : Component.translatable("gui.skyabyss.skill.learn"));
            }
        }
        SkillData r = real();
        boolean on = tryon != null;
        respec.visible = !on;
        tryonBtn.visible = !on;
        for (SubTab t : subTabs) {
            if (t != null) {
                t.visible = !on;
            }
        }
        tryonOk.visible = on;
        tryonUndo.visible = on;
        tryonCancel.visible = on;
        tryonOk.active = !tryonOrder.isEmpty();
        tryonUndo.active = !tryonOrder.isEmpty();
        tryonBtn.active = page == 0;
        if (!shownTrees().contains(tree)) {
            List<SkillTree> shown = shownTrees();
            tree = shown.isEmpty() ? SkillTree.ROCK : shown.get(0);
            lastTree = tree;
            selected = null;
            selectedNode = null;
            fitCanvas();
        }
        Job rj = Job.byId(r.curJob);
        respec.active = rj != null && AbilityTrees.of(rj) != null ? !r.nodesOf(rj).isEmpty() : r.spentPoints() > 0;
        respec.setMessage(Component.translatable(respecArmed > 0 ? "gui.skyabyss.skill.respec_confirm" : "gui.skyabyss.skill.respec"));
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(r.curJob);
        for (int i = 0; i < SkillRules.LOADOUTS_PER_JOB; i++) {
            loadLoadout[i].active = job != null && (saveMode || r.loadouts[SkillData.loadoutIndex(job, i)].saved);
        }
        saveToggle.setMessage(Component.translatable(saveMode ? "gui.skyabyss.skill.loadout_save_on" : "gui.skyabyss.skill.loadout_save_mode"));
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
        if (v2() != null) {
            if (selectedNode == null) {
                return;
            }
            if (tryon != null) {
                // 試着: 写しの木だけを進める (サーバーへは確定のときにまとめて送る)
                SkillData.TakeResult r = tryon.canTake(tree, selectedNode, level());
                if (r == SkillData.TakeResult.OK) {
                    tryon.nodesOf(tree).add(selectedNode.id);
                    tryon.applyNodes();
                    tryonOrder.add(selectedNode.id);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Skills.SOUND_LEARN.get(), 1.8f, 0.3f));
                } else {
                    notice(Component.translatable("message.skyabyss.skill.node_failed." + r.name().toLowerCase()));
                }
                return;
            }
            PacketDistributor.sendToServer(new SkillNodePayload(tree.id, selectedNode.id));
            return;
        }
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
        quiet();
        click();
    }

    private void tryonConfirm() {
        if (tryon != null && !tryonOrder.isEmpty()) {
            PacketDistributor.sendToServer(new SkillTryonPayload(new ArrayList<>(tryonOrder)));
        }
        tryon = null;
        tryonOrder.clear();
        quiet();
        click();
    }

    private void tryonCancel() {
        tryon = null;
        tryonOrder.clear();
        quiet();
        click();
    }

    // 仮に振った最後の 1 つを戻す
    private void tryonUndo() {
        if (tryon == null || tryonOrder.isEmpty()) {
            return;
        }
        String id = tryonOrder.remove(tryonOrder.size() - 1);
        if (v2() != null && tryon.nodesOf(tree).remove(id)) {
            tryon.applyNodes();
            click();
            return;
        }
        int left = tryon.ranks.merge(id, -1, Integer::sum);
        if (left <= 0) {
            tryon.ranks.remove(id);
        }
        click();
    }

    private void setPage(int p) {
        if (p == 1) {
            p = 0;
        }
        if (p != page) {
            page = p;
            if (p == 2) {
                ext.opened();
            }
            click();
        }
    }

    // ページの見出し (切り絵のつまみ)。字は箱に収まらなければ縮める
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
            // 切り絵の札 (Paper.tab): いまのページは桃色、ほかは生成り
            Paper.tab(g, font, getX(), getY(), width, height, getMessage(), page == index, isHovered());
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

    private void pressLoadout(int i) {
        if (tryon != null) {
            notice(Component.translatable("gui.skyabyss.skill.tryon_locked"));
            return;
        }
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(real().curJob);
        if (job == null) {
            return;
        }
        boolean save = saveMode;
        saveMode = false;
        PacketDistributor.sendToServer(new SkillLoadoutPayload(SkillData.loadoutIndex(job, i), save));
        quiet();
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
        quiet();
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
    // 盤の単位の位置 (盤の左上が原点。描くときは canvas の中で描く)
    private int nodeX(Skill s) {
        return COL_X[s.column];
    }

    private int nodeY(Skill s) {
        return TIER_Y[s.tier];
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // 見える木: 封じていない木。淵と前世の技は、転生した人にだけ (未転生の人には見えない)
    private List<SkillTree> shownTrees() {
        List<SkillTree> list = new ArrayList<>();
        int reborn = minecraft == null || minecraft.player == null ? 0 : rebirth();
        // 木 v2: 職の木は今の職のものだけ (ほかの職は職の間で替えてから)
        Job cur = minecraft == null || minecraft.player == null ? null : Job.byId(real().curJob);
        if (cur != null && AbilityTrees.of(cur) != null) {
            list.add(cur.tree);
        } else {
            for (jp.revati.skyabyss.rpg.skill.Job j : jp.revati.skyabyss.rpg.skill.Job.values()) {
                if (!Skill.inTree(j.tree).isEmpty() && (!j.tree.sealed || real().treeOpen(j.tree))) {
                    list.add(j.tree);
                }
            }
        }
        for (SkillTree t : new SkillTree[] {SkillTree.DEEP, SkillTree.PAST}) {
            if (reborn > 0) {
                list.add(t);
            }
        }
        return list;
    }

    // タブの大きさ: 6 本までは今のまま、それより多いときは詰める
    // 木のつまみは、下の「技の点」の文字 (top + 156) にかからない高さ (top + 24 〜 150) に収める。木が増えたぶんだけ詰める
    // 木のつまみの左上 (見出しの帯の中、記録・技・旅のつまみの右へ並べる)
    private int treeTabX(int i) {
        return left + HEAD.x() + RpgTabs.headWidth(RpgTabs.SKILLS) + Golden.S8 + i * TREE_TAB_STEP;
    }

    private int treeTabY() {
        return top + HEAD.y() + (HEAD.h() - TREE_TAB) / 2;
    }

    private Skill nodeAt(double mx, double my) {
        if (!canvas.contains(mx, my) || v2() != null) {
            return null;
        }
        double bx = canvas.toContentX(mx);
        double by = canvas.toContentY(my);
        for (Skill s : Skill.inTree(tree)) {
            if (in(bx, by, nodeX(s), nodeY(s), NODE, NODE)) {
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
            if (in(mx, my, treeTabX(i), treeTabY(), TREE_TAB, TREE_TAB)) {
                return trees.get(i);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 入力
    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        if (button == 0 && artsHovered(fit.x(screenX), fit.y(screenY)) && minecraft.player != null) {
            // 秘伝の一覧 (チャットの押せる一覧) を出し、チャットの窓を開いて全部見せる
            minecraft.player.connection.sendCommand("skyabyss_arts");
            minecraft.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            return true;
        }
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        // 説明の欄の目盛りのつまみ (右の細い帯)
        if (page == 0 && info.mouseClicked(mx, my, button)) {
            return true;
        }
        if (page == 2) {
            return ext.click(mx, my, real(), minecraft, this::notice, this::click);
        }
        SkillTree t = page == 0 ? treeTabAt(mx, my) : null;
        if (t != null) {
            if (t != tree) {
                tree = t;
                lastTree = t;
                selected = null;
                selectedNode = null;
                fitCanvas();
                assembleAt = net.minecraft.Util.getMillis();
                click();
                refresh();
            }
            return true;
        }
        if (page == 0 && v2() != null) {
            AbilityNode n = v2NodeAt(mx, my);
            if (n != null) {
                selectedNode = n;
                selected = n.skill;
                runeArmed = 0;
                click();
                refresh();
                return true;
            }
        }
        Skill s = page == 0 ? nodeAt(mx, my) : null;
        if (s != null) {
            selected = s;
            runeArmed = 0;
            click();
            refresh();
            return true;
        }
        // 盤の上の左ボタンは、動かすための握り (印を押したときは上で済む)
        if (page == 0 && canvas.mouseClicked(mx, my, button)) {
            return true;
        }
        for (int[] r : runeRows) {
            if (info.contains(mx, my) && in(mx, my, r[0], r[1], r[2], r[3])) {
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
                if (tree != selected.tree) {
                    tree = selected.tree;
                    lastTree = tree;
                    fitCanvas();
                }
                selectedNode = v2() == null ? null : v2().skillNode(selected);
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
            if (d.unspentFor(selected.tree, level()) < 1) {
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
        // 盤の見え方を戻す (R。技を選んでいるときの枠のキーが先)
        if (page == 0 && (info.keyPressed(keyCode) || canvas.keyPressed(keyCode))) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dx, double dy) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        dx /= fit.scale();
        dy /= fit.scale();
        return (page == 0 && (info.mouseDragged(mx, my, button, dx, dy) || canvas.mouseDragged(mx, my, button, dx, dy)))
                || (page == 2 && ext.drag(mx, my, button, dx, dy))
                || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        return info.mouseReleased(mx, my, button) || (page == 0 && canvas.mouseReleased(mx, my, button)) || (page == 2 && ext.release(mx, my, button))
                || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double scrollX, double scrollY) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (page == 2 && ext.scroll(mx, my, scrollY, real(), BODY)) {
            return true;
        }
        if (page == 0 && info.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (page == 0 && canvas.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- 描画
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 板は render の中で、窓に合わせた変換の中で描く (描くのと当たりを一つの変換にする)
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    // 囲み (ツールチップ) は変換の外で、画面の座標へ描く (変換の中では窓の端で切れる)
    private List<FormattedCharSequence> pendingTip;

    private void tip(List<FormattedCharSequence> lines) {
        pendingTip = lines;
    }

    private void tipC(List<Component> lines) {
        pendingTip = lines.stream().map(Component::getVisualOrderText).toList();
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderSheet(g);
        long nowR = net.minecraft.Util.getMillis();
        boolean wantCard = hasCard();
        if (wantCard != cardShown) {
            cardShown = wantCard;
            cardAt = nowR;
            if (!wantCard) {
                coverAt = nowR + 60;
            }
        }
        // 札: 入るときは 220ms で少し行き過ぎて止まり、出るときは 140ms で右へ抜ける
        float cardT = cardShown ? SkillStage.back((nowR - cardAt) / 220f) : 1f - SkillStage.out((nowR - cardAt) / 140f);
        int cardDx = Math.round((1f - cardT) * 110f);
        int target = Math.max(118, Math.min(INFO.bottom(), needBottom + Golden.S3 + Golden.BTN + Golden.S5));
        cardBottom = cardT < 0.3f ? target : cardBottom + (target - cardBottom) * 0.35f;
        if (Math.abs(target - cardBottom) < 0.5f) {
            cardBottom = target;
        }
        int cb = Math.round(cardBottom / 2f) * 2;
        learn.setY(top + cb - Golden.S5 - Golden.BTN);
        if (page == 0 && cardT > 0.02f) {
            jp.revati.skyabyss.client.ui.Plate.tile(g, left + INFO.x() + cardDx, top + INFO.y(), INFO.w(), cb - INFO.y(),
                    jp.revati.skyabyss.client.ui.Plate.REST, jp.revati.skyabyss.client.ui.Plate.GOLD);
        }
        // 釦など (Screen の部品) は板の上に、いつもの順。「取る」は札が止まってから
        for (Renderable r : renderables) {
            if (r == learn && cardT < 0.85f) {
                continue;
            }
            r.render(g, mouseX, mouseY, partialTick);
        }
        if (respecArmed > 0 && respec.visible) {
            // 二度押しの待ち: 釦の下の端に、減っていく桃色の帯
            float f = Math.max(0f, (respecArmed - partialTick) / 60f);
            int bw = Math.round((respec.getWidth() - 6) * f);
            g.fill(respec.getX() + 3, respec.getY() + respec.getHeight() - 3, respec.getX() + 3 + bw, respec.getY() + respec.getHeight() - 1, 0xFF000000 | Paper.PINK_HI);
        }
        SkillData d = data();
        int level = level();
        if (page == 0) {
            renderTreeTabs(g, d);
            hoverNode = v2() != null ? v2NodeAt(mouseX, mouseY) : null;
            if (hoverNode == null && demoHoverId != null && v2() != null) {
                hoverNode = v2().node(demoHoverId);
            }
            renderTree(g, d, level);
            renderHelp(g, mouseX, mouseY);
            hoverRune = 0;
            if (pendingCaption != null) {
                boardCaption(g, pendingCaption, pendingCaptionColor);
                pendingCaption = null;
            }
            String infoKey = tree + ":" + (selectedNode != null ? selectedNode.id : selected != null ? selected.id : "");
            long nowI = net.minecraft.Util.getMillis();
            if (!infoKey.equals(lastInfoKey)) {
                if (lastInfoKey != null) {
                    infoSwapAt = nowI;
                }
                lastInfoKey = infoKey;
            }
            // 中身が替わると、字が右から 8 だけ滑りこむ (180ms)。札は札ごと動く
            int slide = Math.round((1f - out((nowI - infoSwapAt) / 180f)) * 8f);
            if (cardShown) {
                g.pose().pushPose();
                g.pose().translate(slide + cardDx, 0, 0);
                renderInfo(g, d, level, mouseX, mouseY);
                g.pose().popPose();
            } else if (cardT <= 0.02f) {
                renderCover(g, d, level, mouseX, mouseY);
            }
        } else {
            runeRows.clear();
            ext.render(g, font, minecraft, d, BODY, mouseX, mouseY);
        }
        renderBottom(g, d, level);
        renderArts(g);
        if (lessonLearn && page == 0) {
            if (learn.active && learn.visible) {
                jp.revati.skyabyss.client.tutorial.SkillLessonClient.frame(g, learn.getX(), learn.getY(), learn.getWidth(), learn.getHeight());
            }
            // 盤の下の端の帯に (等倍)
            boardStrip(g, Component.translatable("lesson.skyabyss.screen.learn"), Paper.ON_BLACK_PINK);
        }
        if (tryon != null && page == 0) {
            boardCaption(g, Component.translatable("gui.skyabyss.skill.tryon_banner", tryonOrder.size()), Paper.ON_BLACK_PINK);
            // 試着の間は盤を桃色の点線で囲む (本当に取ったのではないと一目で分かるように)。線は少しずつ流れる
            int bx0 = left + BOARD_VX - 2;
            int by0 = top + BOARD_VY - 2;
            int bx1 = left + BOARD_VX + BOARD_VW + 1;
            int by1 = top + BOARD_VY + BOARD_VH + 1;
            int ph = (int) ((net.minecraft.Util.getMillis() / 90) % 6);
            int c = 0xFF000000 | Paper.PINK;
            for (int i = 0; i < BOARD_VW + 4; i++) {
                if ((i + ph) % 6 < 3) {
                    g.fill(bx0 + i, by0, bx0 + i + 1, by0 + 1, c);
                    g.fill(bx1 - i, by1, bx1 - i + 1, by1 + 1, c);
                }
            }
            for (int i = 0; i < BOARD_VH + 4; i++) {
                if ((i + ph) % 6 < 3) {
                    g.fill(bx1, by0 + i, bx1 + 1, by0 + i + 1, c);
                    g.fill(bx0, by1 - i, bx0 + 1, by1 - i + 1, c);
                }
            }
        }
        if (noticeTicks > 0 && noticeText != null) {
            // 説明の欄の下端に、生成りの地を敷いて濃い赤で
            int alpha = Math.min(255, noticeTicks * 12);
            List<FormattedCharSequence> lines = TextWrap.split(font, noticeText, INFO_W - 2 * Golden.S5);
            int y = top + infoBodyBottom() - lines.size() * LH;
            g.fill(left + INFO_X + 1, y - Golden.S3, left + INFO_X + INFO_W - 1, top + infoBodyBottom(), (Math.min(255, alpha) << 24) | (Paper.CREAM_LO & 0xFFFFFF));
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, left + INFO_X + Golden.S5, Golden.textY(y, LH), (alpha << 24) | INK_DENY, false);
                y += LH;
            }
        }
        // 盤の上に来たとき、動かし方を一行 (下の端の帯に、等倍で)
        // 盤の動かし方は「？ 遊び方」の札の中 (盤の上に字を置くと一番上の節にかかった)
        renderHover(g, d, mouseX, mouseY);
        renderLearnTooltip(g, d, mouseX, mouseY);
        fit.end(g);
        if (pendingTip != null) {
            g.renderTooltip(font, pendingTip, screenX, screenY);
            pendingTip = null;
        }
    }

    // 見出しの字 (職と木の名前・試着中)。つまみの右の黒い紙の上に、生成りか桃色で等倍
    private void boardCaption(GuiGraphics g, Component text, int color) {
        List<SkillTree> trees = shownTrees();
        int tabs = trees.size() < 2 && v2() != null ? 0 : trees.size() * TREE_TAB_STEP;
        int x = left + HEAD.x() + RpgTabs.headWidth(RpgTabs.SKILLS) + Golden.S8 + tabs;
        int right = tryon != null ? left + HEAD.right() : left + SUB_X - Golden.S5;
        if (tryon == null) {
            color = 0xF2C47C;
        }
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, text, x, top + HEAD.y() + 6, Math.max(10, right - x), color, false);
    }

    // 盤の下の端の帯 (動かし方・稽古の手引き)
    private void boardStrip(GuiGraphics g, Component text, int color) {
        int x = left + BOARD_VX;
        int y = top + BOARD_VY + BOARD_VH - LH;
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        // 不透明 (下の節の「+4」「2SP」が透けて字に重ならないように)
        g.fill(x, y, x + BOARD_VW, y + LH, Paper.BLACK_LO);
        jp.revati.skyabyss.client.dev.OverlapAudit.cover(g, x, y, x + BOARD_VW, y + LH);
        int w = Math.min(font.width(text), BOARD_VW - 2 * Golden.S5);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, text, x + (BOARD_VW - w) / 2, Golden.textY(y, LH), BOARD_VW - 2 * Golden.S5, color, false);
        g.pose().popPose();
    }

    private void renderTreeTabs(GuiGraphics g, SkillData d) {
        List<SkillTree> trees = shownTrees();
        if (trees.size() < 2 && v2() != null) {
            // 職の木が一つだけなら、つまみは出さない (選ぶものがない)
            return;
        }
        for (int i = 0; i < trees.size(); i++) {
            SkillTree t = trees.get(i);
            int x = treeTabX(i);
            int y = treeTabY();
            boolean on = t == tree;
            // 切り絵の小さな札 (少し傾けた四角)。いまの木は桃色
            int body = on ? Paper.PINK : 0xFF1B2030;
            Paper.draw(g, "skill_treetab_" + (on ? 1 : 0), "", x - 4, y - 4, TREE_TAB + 9, TREE_TAB + 9,
                    s -> s.layer(Paper.rotRect(12, 12, TREE_TAB + 3, TREE_TAB + 3, 0), body, Paper.BLACK_LO, 1, 1));
            if (!on) {
                g.setColor(0.7f, 0.7f, 0.76f, 1f);
            }
            SkillHud.drawIcon(g, t.iconIndex(), x, y, TREE_TAB);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    private boolean lessonLearn;
    // 盤の見出し (盤を描いたあとに上へ重ねる)
    private Component pendingCaption;
    private int pendingCaptionColor;

    private void renderTree(GuiGraphics g, SkillData d, int level) {
        lessonLearn = tryon == null && jp.revati.skyabyss.client.tutorial.SkillLessonClient.learning(minecraft);
        if (v2() != null) {
            renderTreeV2(g, d, level);
            return;
        }
        // 見出し: 職と木の名前
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.ofTree(tree);
        MutableComponent head = job == null ? Component.translatable(tree.nameKey())
                : Component.translatable("job.skyabyss.subtitle", Component.translatable(job.nameKey()), Component.translatable(tree.nameKey()));
        int headColor = Paper.ON_BLACK;
        if (job != null && minecraft != null && minecraft.player != null && jp.revati.skyabyss.rpg.skill.Job.held(minecraft.player) == job) {
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.job_held"));
        }
        if (tree == SkillTree.PAST) {
            // 前世の点 (通常の点とは別枠)
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.ext.past_points", d.pastLeft(), d.pastPool())
                    .withColor(d.pastLeft() > 0 ? PALE : SOFT));
        }
        if (tryon == null) {
            pendingCaption = head;
            pendingCaptionColor = headColor;
        }
        // 盤の中 (節と線と印は盤の単位で描く。窓の外は切れる)
        canvas.begin(g);
        // 行の目印 (要るレベル。淵は要る転生の回数。前世の技は行の目印なし)
        for (int tier = 0; tier < 4 && tree != SkillTree.PAST; tier++) {
            boolean deep = tree == SkillTree.DEEP;
            int need = deep ? new int[] {1, 3, 4, 5}[tier] : new int[] {1, 5, 12, 20}[tier];
            String lv = deep ? "転" + need : "Lv" + need;
            g.drawString(font, lv, COL_X[1] + NODE + Golden.S5, TIER_Y[tier] + 7, (deep ? d.rebirth : level) >= need ? Paper.ON_BLACK_SOFT : 0x5A5460 & 0xFFFFFF, false);
        }
        // つなぐ線 (先に描く)
        for (Skill s : Skill.inTree(tree)) {
            for (Skill r : s.requires()) {
                // 取った道は桃色の糸、まだの道は点線
                boolean on = d.knows(r);
                int color = on ? Paper.PINK : Paper.GREY_LO;
                int x0 = nodeX(r) + NODE / 2;
                int y0 = nodeY(r) + NODE;
                int x1 = nodeX(s) + NODE / 2;
                int y1 = nodeY(s);
                int mid = (y0 + y1) / 2 + 3;
                thread(g, x0, y0 + 8, x0, mid, color, 1, !on);
                thread(g, x0, mid, x1, mid, color, 1, !on);
                thread(g, x1, mid, x1, y1, color, 1, !on);
            }
        }
        for (Skill s : Skill.inTree(tree)) {
            int x = nodeX(s);
            int y = nodeY(s);
            int rank = d.rank(s);
            boolean reachable = d.requirementsMet(s) && level >= s.minLevel && s.rebirthAt() <= d.rebirth;
            int state = rank >= d.rankLimit(s) ? 3 : rank > 0 ? 2 : reachable ? 1 : 0;
            // 切り抜きの円 (要は菱)。取った = 桃色、取れる = 生成り、まだ = 暗い色。選んだ節は持ち上げて桃色の菱を留める
            int ring = state >= 2 ? Paper.PINK : state == 1 ? 0xFF232A3C : Paper.GREY_LO;
            if (s.keystone()) {
                paperNode(g, false, x, y, NODE, NODE / 2 + 3, ring, s == selected);
            } else {
                paperNode(g, true, x, y, NODE, NODE / 2 + 1, ring, s == selected);
            }
            if (state == 0) {
                g.setColor(0.42f, 0.42f, 0.48f, 1f);
            } else if (state == 1) {
                g.setColor(0.72f, 0.72f, 0.78f, 1f);
            }
            SkillHud.drawIcon(g, s.iconIndex(), x + 3, y + 3);
            g.setColor(1f, 1f, 1f, 1f);
            // 技の稽古の覚える段: 覚えられる能動の技を光らせる
            if (lessonLearn && s.active && rank == 0 && d.canLearn(s, level) == SkillData.LearnResult.OK) {
                jp.revati.skyabyss.client.tutorial.SkillLessonClient.frame(g, x, y, NODE, NODE);
            }
            // 段 (小さく、印の下)。要は段がないので出さない
            if (!s.keystone() || rank == 0) {
                String r = d.rebirth < s.rebirthAt() ? "転" + s.rebirthAt() : s.keystone() ? "" : rank + "/" + d.rankLimit(s);
                boolean prov = tryon != null && rank > real().rank(s);
                int color = prov ? Paper.ON_BLACK_PINK : rank >= d.rankLimit(s) ? Paper.ON_BLACK_PINK : rank > 0 ? Paper.ON_BLACK
                        : d.canLearn(s, level) == SkillData.LearnResult.OK ? Paper.ON_BLACK : Paper.ON_BLACK_SOFT;
                g.drawString(font, r, x + NODE / 2 - font.width(r) / 2, y + NODE + 2, color, false);
            }
            // タグの色の点 (左上)
            SkillTag tg = SkillTag.of(s);
            if (tg != null) {
                g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF000000 | tg.rgb);
            }
            // 能動の技には小さな菱形の印 (枠に置ける)。刻印を選んでいれば金
            if (s.active) {
                int dot = d.rune(s) > 0 ? Paper.PINK_HI : rank > 0 ? Paper.CREAM : Paper.GREY_LO;
                activeMark(g, x + NODE - 2, y + 1, dot);
            }
        }
        canvas.end(g);
    }

    // ---------------------------------------------------------------- 切り絵の道具 (docs/design/56 の「切り絵」)
    // 生成りの上の拒否の色 (濃い赤) と、黒い紙の上の拒否の色
    static final int INK_DENY = 0xA82838;
    static final int BOARD_DENY = 0xE0506A;
    // 桃色の山の上の控えめな字
    static final int HILL_SOFT = 0xF0D2E0;
    // 板の見出しの段の下の溝 (板の上の端から。つまみの段 HEAD の下)
    static final int PLATE_HEAD = HEAD.bottom() + 2;

    // 黒い紙と桃色の山は窓の端まで (論理の座標で、窓の四隅まで)。峰と菱は手で置いた
    private void renderSheet(GuiGraphics g) {
        // 板の絵柄: 舞台の大きさの板を一枚。見出しの段 (つまみ・職と木の名前) は溝で区切り、
        // 盤・下の段の左 (SP・技の枠・秘伝)・下の段の右 (型・振り直し・試着) は沈んだ欄。欄の列は盤と説明の列にそろえる
        jp.revati.skyabyss.client.ui.Plate.openFade(g);
        jp.revati.skyabyss.client.ui.Plate.panel(g, 0, 0, W, H, PLATE_HEAD, jp.revati.skyabyss.client.ui.Plate.GOLD);
        if (page == 0) {
            jp.revati.skyabyss.client.ui.Plate.well(g, BOARD.x(), BOARD.y(), BOARD.w(), BOARD.h());
        } else {
            Golden.Box rb = ExtPage.right(BODY);
            jp.revati.skyabyss.client.ui.Plate.well(g, rb.x() - 4, rb.y(), rb.w() + 4, rb.h());
        }
        jp.revati.skyabyss.client.ui.Plate.well(g, FOOT_L.x(), FOOT_L.y(), FOOT_L.w(), FOOT_L.h());
        jp.revati.skyabyss.client.ui.Plate.well(g, FOOT_R.x(), FOOT_R.y(), FOOT_R.w(), FOOT_R.h());
    }

    // 節の紙: round なら切り抜きの円 (縁 ring・中は黒)、でなければ菱。r は外の半径。
    // 選んだ節は持ち上げて (影を大きくずらす) 上の端に桃色の菱を留める (職を選ぶ画面の札と同じ言葉)。
    // 絵は形・大きさ・色・選びごとに一枚 (送り・拡大では作り直さない)
    private static void paperNode(GuiGraphics g, boolean round, int x, int y, int sz, int r, int ring, boolean sel) {
        paperNode(g, round, x, y, sz, r, ring, sel, false);
    }

    // hot = マウスの下 (選んだ節と同じく浮かせて桃色の影。菱は留めない)
    private static void paperNode(GuiGraphics g, boolean round, int x, int y, int sz, int r, int ring, boolean sel, boolean hot) {
        int pad = r + 5;
        float c = sz / 2f;
        boolean up = sel || hot;
        String name = "skill_node_" + (round ? "o" : "d") + sz + "_" + r + "_" + Integer.toHexString(ring) + (sel ? "_s" : hot ? "_h" : "");
        int ox = Math.round(c) - pad;
        Paper.draw(g, name, "", x + ox, y + ox, 2 * pad + 1, 2 * pad + 1, s -> {
            float cc = c - ox;
            int ci = Math.round(cc);
            // 影はずらさず、縁のまわりに 1 画素 (枠と中の絵の芯がずれて見えないように)
            if (round) {
                s.disc(cc, cc, r + 1, up ? Paper.PINK_LO : Paper.BLACK_LO);
                s.disc(cc, cc, r, ring);
                s.disc(cc, cc, r - 2, Paper.BLACK_LO);
            } else {
                s.diamond(ci, ci, r + 1, up ? Paper.PINK_LO : Paper.BLACK_LO, 0);
                s.diamond(ci, ci, r, ring, 0);
                s.diamond(ci, ci, r - 2, Paper.BLACK_LO, 0);
            }
        });
        if (sel) {
            int px = x + Math.round(c);
            int py = y + Math.round(c) - r;
            Paper.draw(g, "skill_selpin", "", px - 4, py - 5, 9, 9, s -> s.diamond(4, 4, 4, Paper.PINK_HI, 0xFFFFFFFF));
        }
    }

    // 使う技の印: 小さな菱 (黒い縁つき)。(cx, cy) が真ん中
    private static void activeMark(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 2, cy - 1, cx + 3, cy + 2, Paper.BLACK_LO);
        g.fill(cx - 1, cy - 2, cx + 2, cy + 3, Paper.BLACK_LO);
        g.fill(cx - 1, cy, cx + 2, cy + 1, color);
        g.fill(cx, cy - 1, cx + 1, cy + 2, color);
    }

    // 糸 (まっすぐ。斜めも)。dotted なら 4 歩ごとに 2 歩だけ描く点線
    private static void thread(GuiGraphics g, int x0, int y0, int x1, int y1, int color, int w, boolean dotted) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int h = w / 2;
        for (int i = 0; i <= steps; i++) {
            if (dotted && i % 4 >= 2) {
                continue;
            }
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, steps));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, steps));
            g.fill(x - h, y - h, x - h + w, y - h + w, 0xFF000000 | color);
        }
    }

    // 生成りの紙の上の区切りの線
    private static void inkRule(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, 0xFFB8AC98);
    }

    // 生成りの上に描く字の色: 黒い地のための明るい色 (行に焼いてある色) を、墨・濃い桃色へ写す。
    // 囲み (ツールチップ) は暗い地なので写さない (同じ行を v2Lines から両方へ出す)
    static int ink(int rgb) {
        rgb &= 0xFFFFFF;
        if (rgb == TEXT || rgb == 0x9FB8FF) {
            return Paper.INK;
        }
        if (rgb == SOFT) {
            return Paper.INK_SOFT;
        }
        if (rgb == DIM) {
            return 0x6C7084;
        }
        if (rgb == GOLD || rgb == 0xF2D78A) {
            return Paper.INK_HEAD;
        }
        if (rgb == PALE || rgb == LORE || rgb == 0x7FFFA0 || rgb == 0xB8F2A8) {
            return 0x7A2452;
        }
        if (rgb == DENY) {
            return INK_DENY;
        }
        int r = rgb >> 16 & 0xFF;
        int gg = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        if (r * 3 + gg * 6 + b < 0x70 * 10) {
            return rgb;
        }
        // 表にない明るい色 (タグ・変える物の色など) は墨に寄せて暗くする
        int ir = 0x181722 >> 16 & 0xFF;
        int ig = 0x181722 >> 8 & 0xFF;
        int ib = 0x181722 & 0xFF;
        return (r * 45 + ir * 55) / 100 << 16 | (gg * 45 + ig * 55) / 100 << 8 | (b * 45 + ib * 55) / 100;
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, 0xFF000000 | color);
    }

    // 木の表紙 (節を選んでいないとき)。紙の札は出さず、黒の上に: 小さな題 (職)・大きな木の名・効果の元の判子・説明 (3 行まで)・
    // 発動スキルの菱・型の力 (乗せると全文)・遊び方 (乗せると全文)。開くと段ごとに少し遅れて左から入る
    static final int CX = 272;
    static final int CW = 128;

    private void renderCover(GuiGraphics g, SkillData d, int level, int mx, int my) {
        long now = net.minecraft.Util.getMillis();
        long base = Math.max(openedAt, coverAt);
        float a0 = SkillStage.out((now - base) / 240f);
        float a1 = SkillStage.out((now - base - 45) / 240f);
        float a2 = SkillStage.out((now - base - 90) / 240f);
        float a3 = SkillStage.out((now - base - 135) / 240f);
        int x = left + CX;
        int y = top + 34;
        Job job = v2Job();
        Component eyebrow = job != null ? Component.translatable(job.nameKey()) : Component.translatable("gui.skyabyss.skill.cover_shared");
        g.drawString(font, eyebrow, x + Math.round((1f - a0) * -8f), y, SkillStage.fade(Paper.ON_BLACK_PINK, a0), false);
        // 大きな木の名 (2 倍)
        Component name = Component.translatable(tree.nameKey());
        int nx = x + Math.round((1f - a0) * -12f);
        int nh = SkillStage.big(g, font, name, nx, y + 13, CW, SkillStage.fade(Paper.ON_BLACK_HI, a0));
        int nw = font.width(name) * (nh > font.lineHeight ? 2 : 1);
        // 効果の元の判子 (名の右に入れば右、入らなければ下)
        Component stat = tree.mainStat == null ? null : Component.translatable(tree.mainStat.nameKey());
        int yy = y + 13 + nh + 6;
        if (stat != null) {
            float press = SkillStage.clamp01((now - base - 120) / 260f);
            int sw = font.width(stat) + 10;
            if (nw + 8 + sw <= CW) {
                SkillStage.stamp(g, font, stat, x + nw + 8, y + 13 + nh / 2 - 6, press, true);
            } else {
                SkillStage.stamp(g, font, stat, x, yy, press, true);
                yy += 18;
            }
        }
        // 説明 (5 行まで。途中で切ると意味が欠ける)
        int dx1 = Math.round((1f - a1) * -10f);
        List<FormattedCharSequence> desc = TextWrap.split(font, Component.translatable(tree.descKey()), CW);
        for (int i = 0; i < Math.min(5, desc.size()); i++) {
            g.drawString(font, desc.get(i), x + dx1, yy + i * 11, SkillStage.fade(Paper.ON_BLACK, a1), false);
        }
        yy += Math.min(5, desc.size()) * 11 + 8;
        // 発動スキルの数 (菱の列)
        int dx2 = Math.round((1f - a2) * -10f);
        Component sh = Component.translatable("gui.skyabyss.skill.slots_head");
        g.drawString(font, sh, x + dx2, yy, SkillStage.fade(Paper.ON_BLACK_SOFT, a2), false);
        if (a2 > 0.3f) {
            SkillStage.pips(g, x + dx2 + font.width(sh) + 6, yy, SkillRules.BASE_SLOTS, d.activeCount(), 3, true);
        }
        yy += 16;
        // 型の力: 菱・名前・型の点 / 要る点。乗せると全文
        AbilityTree t = v2();
        int dx3 = Math.round((1f - a3) * -10f);
        if (t != null && t.job != null) {
            for (String arch : t.archetypes) {
                var gk = jp.revati.skyabyss.rpg.skill.Gauges.Kind.of(t.job, arch);
                if (gk == null) {
                    continue;
                }
                int pts = d.archPoints(tree, arch);
                int need = jp.revati.skyabyss.rpg.skill.Gauges.ARCH_POINTS;
                boolean on = pts >= need;
                if (a3 > 0.3f) {
                    Paper.draw(g, "skcov_g" + (on ? 1 : 0), "", x + dx3, yy + 1, 7, 7, p -> p.diamond(3, 3, 3, on ? Paper.PINK : Paper.EDGE, on ? Paper.PINK_HI : Paper.GREY_LO));
                }
                Component gn = Component.translatable(gk.nameKey());
                g.drawString(font, gn, x + dx3 + 11, yy, SkillStage.fade(on ? Paper.ON_BLACK_PINK : Paper.ON_BLACK, a3), false);
                // 要る点までの目盛り (一点ごとに小さな升。使った分が桃色)
                if (a3 > 0.3f) {
                    int bx = x + CW - need * 5 + 1;
                    for (int k = 0; k < need; k++) {
                        g.fill(bx + k * 5, yy + 3, bx + k * 5 + 3, yy + 6, k < pts ? Paper.PINK : Paper.EDGE);
                    }
                }
                if (mx >= x && mx < x + CW && my >= yy - 2 && my < yy + 10) {
                    List<Component> tl = new ArrayList<>();
                    tl.add(gn.copy().withColor(Paper.ON_BLACK_PINK));
                    tl.add(Component.translatable("gui.skyabyss.tree.arch_line", Component.translatable(t.archKey(arch)), pts).withColor(Paper.ON_BLACK_SOFT));
                    tl.add(Component.translatable(gk.nameKey() + ".desc").withColor(Paper.ON_BLACK));
                    tip(wrapAll(tl, 200));
                }
                yy += 12;
            }
        }
        if (tree == SkillTree.PAST) {
            g.drawString(font, Component.translatable("gui.skyabyss.ext.past_points", d.pastLeft(), d.pastPool()), x + dx3, yy, SkillStage.fade(Paper.ON_BLACK_PINK, a3), false);
        }
    }

    // 遊び方の札 (盤の左上の角。乗せると遊び方と盤の動かし方)
    private void renderHelp(GuiGraphics g, int mx, int my) {
        int x = left + BOARD_VX + 4;
        float a3 = SkillStage.out((net.minecraft.Util.getMillis() - assembleAt - 135) / 240f);
        Component help = Component.translatable("gui.skyabyss.skill.help_chip");
        int hy = top + BOARD_VY + 3;
        boolean helpHot = mx >= x && mx < x + font.width(help) + 9 && my >= hy && my < hy + 12;
        if (a3 > 0.3f) {
            // 板の絵柄: 小さな升 (乗せると明るく)
            int st = helpHot ? jp.revati.skyabyss.client.ui.Plate.HOVER : jp.revati.skyabyss.client.ui.Plate.REST;
            jp.revati.skyabyss.client.ui.Plate.tile(g, x, hy, font.width(help) + 9, 13, st, jp.revati.skyabyss.client.ui.Plate.GOLD);
            g.drawString(font, help, x + 5, hy + 2, jp.revati.skyabyss.client.ui.Plate.tileInk(st, jp.revati.skyabyss.client.ui.Plate.GOLD), false);
        }
        if (mx >= x && mx < x + font.width(help) + 9 && my >= hy && my < hy + 12) {
            List<FormattedCharSequence> hl = new ArrayList<>(TextWrap.split(font, Component.translatable("gui.skyabyss.tree.pick_hint").withColor(Paper.ON_BLACK), 220));
            hl.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.board_hint").withColor(Paper.ON_BLACK_PINK), 220));
            tip(hl);
        }
    }

    private void renderInfo(GuiGraphics g, SkillData d, int level, int mx, int my) {
        if (v2() != null) {
            renderInfoV2(g, d, level, mx, my);
            return;
        }
        renderInfoSkill(g, d, level, mx, my);
    }

    private void renderInfoSkill(GuiGraphics g, SkillData d, int level, int mx, int my) {
        runeRows.clear();
        int x = left + INFO_X + Golden.S5;
        int y = top + INFO_Y + 2;
        // 字は右の目盛り (送りの帯) の手前まで
        int w = INFO_W - 2 * Golden.S5 - 4;
        List<FormattedCharSequence> lines = new ArrayList<>();
        // 名前の行は送らず上に残す。その下の中身を送れる面に入れる
        int bodyY = y;
        if (selected != null && selected.tree == tree) {
            bodyY = skillHeader(g, d, selected, x, y, w);
            inkRule(g, x, left + INFO_X + INFO_W - Golden.S5, bodyY - 2);
        }
        info.setViewport(left + INFO_X + 2, bodyY, INFO_W - 4, wholeRows(top + infoBodyBottom() - bodyY));
        Component statLine = tree.mainStat == null ? Component.translatable("gui.skyabyss.skill.main_stat_none")
                : Component.translatable("gui.skyabyss.skill.main_stat", Component.translatable(tree.mainStat.nameKey()));
        if (selected == null || selected.tree != tree) {
            lines.addAll(TextWrap.split(font, Component.translatable(tree.descKey()).withColor(SOFT), w));
            lines.addAll(TextWrap.split(font, statLine.copy().withColor(SOFT), w));
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.pick_hint").withColor(DIM), w));
            drawInfo(g, lines, x);
            return;
        }
        Skill s = selected;
        int rank = d.rank(s);
        int limit = d.rankLimit(s);
        // 種類・段・熟練・マナ・待ちは上の札 (skillHeader) に出す
        if (!s.active && rank > 0) {
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.passive_active").withColor(0x8FD0FF), w));
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
        // 足りない条件 (赤紫)。技の木 v2 では段も前提も節で取るので、ここ (前の段を一つずつ覚える形の名残) は出さない。
        // 節ごとの点・前提・取ったかは下の v2 の行が出す (2026-11-13: 取った節の説明に「スキルポイントが足りない」と出ていた)
        if (v2() != null) {
            // 何も足さない
        } else if (rank < limit) {
            if (level < s.minLevel && Job.ofTree(s.tree) == null) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_level", s.minLevel).withColor(DENY), w));
            }
            if (s.rebirthAt() > d.rebirth) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.need_rebirth", s.rebirthAt()).withColor(DENY), w));
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
            if (s.tree != SkillTree.PAST && d.unspentFor(s.tree, level) < 1 && level >= s.minLevel && d.requirementsMet(s)
                    && s.rebirthAt() <= d.rebirth) {
                body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.need_points").withColor(DENY), w));
            }
        } else if (limit < s.maxRank) {
            body.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.skill.rank_capped", limit).withColor(SOFT), w));
        }
        // 木 v2: この節の点・前提・型の点・いま取れるか
        if (v2() != null && selectedNode != null && selectedNode.skill == s) {
            List<Component> st = v2Status(d, selectedNode, level, true);
            if (!st.isEmpty()) {
                st.remove(0);
            }
            if (!st.isEmpty()) {
                body.add(FormattedCharSequence.EMPTY);
                body.addAll(wrapAll(st, w));
            }
        }
        // 刻印 (使う技のうち、刻印のあるもの) は本文の下に続く。言い伝えの一行は効果の前に
        // 刻印: 職の木 v2 では節で選ぶので、ここには出さない
        boolean hasRunes = s.active && v2() == null;
        // いま技を変えているもの (刻印・書き換え・変異・秘伝) は一番上に (技が見た目のまま変わって分かりづらい、の声)
        List<Component> changes = new ArrayList<>();
        changeLines(changes, s);
        if (!changes.isEmpty()) {
            lines.addAll(wrapAll(changes, w));
            lines.add(FormattedCharSequence.EMPTY);
        }
        lines.addAll(TextWrap.split(font, Component.translatable(s.flavorKey()).withStyle(st -> st.withColor(LORE).withItalic(true)), w));
        lines.addAll(body);
        // 送れる面の中で描く (字が長ければ送る)。刻印の段は本文の続きに置き、枠の外の段は当たりを取らない
        info.begin(g);
        int bottom = drawInfoLines(g, lines, x, info.top());
        if (hasRunes) {
            boolean inside = info.contains(mx, my);
            bottom = renderRunes(g, d, s, x, bottom + 2, w, inside ? mx : -1, inside ? my : -1);
        }
        endInfo(g, bottom);
    }

    // 技の札の上の段: 大きな名 (2 倍。入らなければ 1 倍で折る)、種類の判子・段の菱・熟練の札、使う技ならマナと待ちの札。
    // 中身の始まる高さを返す
    private int skillHeader(GuiGraphics g, SkillData d, Skill s, int x, int y, int w) {
        Component name = Component.translatable(s.nameKey());
        int yy;
        if (font.width(name) * 2 <= w) {
            SkillStage.big(g, font, name, x, y + 1, w, 0xFF000000 | Paper.INK_HEAD);
            yy = y + 1 + 2 * font.lineHeight + 4;
        } else {
            List<FormattedCharSequence> nl = TextWrap.split(font, name, w);
            for (int i = 0; i < nl.size(); i++) {
                g.drawString(font, nl.get(i), x, Golden.textY(y + i * LH, LH), 0xFF000000 | Paper.INK_HEAD, false);
            }
            yy = y + nl.size() * LH + 2;
        }
        int rank = d.rank(s);
        int limit = d.rankLimit(s);
        Component kind = Component.translatable(s.keystone() ? "gui.skyabyss.skill.kind_keystone"
                : s.active ? "gui.skyabyss.skill.kind_active" : "gui.skyabyss.skill.kind_passive");
        float press = SkillStage.clamp01((net.minecraft.Util.getMillis() - infoSwapAt - 60) / 220f);
        int cx = x + SkillStage.stamp(g, font, kind, x, yy, Math.max(press, cardShown ? 0.06f : 0f), s.active || s.keystone()) + 4;
        if (!s.keystone() && limit > 0) {
            cx += SkillStage.pips(g, cx, yy + 2, limit, rank, 3, false) + 2;
        }
        AbilityNode sn = v2() != null && selectedNode != null && selectedNode.skill == s ? selectedNode : null;
        Component extra = sn != null && !d.hasNode(tree, sn.id) ? Component.translatable("gui.skyabyss.tree.cost", sn.cost)
                : s.active ? Component.translatable("gui.skyabyss.skill.mastery_short", d.masteryLevel(s)) : null;
        if (extra != null && cx + font.width(extra) + 8 <= x + w) {
            SkillStage.chip(g, font, extra, cx, yy, sn != null && !d.hasNode(tree, sn.id));
        }
        yy += 16;
        if (s.active) {
            int r = Math.max(1, rank);
            // 試着の最中は、仮の段の数字 (待ち・マナは基準の値) を見せる
            boolean live = tryon == null && rank > 0;
            int cooldown = live ? SkillManager.cooldownFor(minecraft.player, s) : SkillRules.cooldownTicks(s, r);
            int mana = live ? SkillManager.manaCostFor(minecraft.player, s) : SkillRules.manaCost(s, r);
            int c2 = x;
            c2 += SkillStage.chip(g, font, Component.translatable("gui.skyabyss.skill.chip_mana", mana), c2, yy, true) + 3;
            SkillStage.chip(g, font, Component.translatable("gui.skyabyss.skill.chip_cd", SkillRules.fmtSeconds(cooldown)), c2, yy, true);
            yy += 16;
        }
        return yy + 2;
    }

    // 説明の欄の中身を送れる面に描く (行は 13 画素。docs/design/56)。描いた下端を返す
    private int drawInfoLines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y) {
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, Golden.textY(y, LH), Paper.INK, false);
            y += LH;
        }
        return y;
    }

    // 中身が本文だけのとき (技を選んでいないとき)
    private void drawInfo(GuiGraphics g, List<FormattedCharSequence> lines, int x) {
        info.begin(g);
        endInfo(g, drawInfoLines(g, lines, x, info.top()));
    }

    // 送れる面を閉じ、中身の高さ (描いた下端まで) を伝えて目盛りを描く
    private void endInfo(GuiGraphics g, int bottom) {
        info.end(g);
        info.setContentHeight(bottom - info.top());
        needBottom = info.y() + (bottom - info.top()) - top;
        info.drawBar(g, 0x30181C28, 0xFFC48A44);
    }

    // 逸品の書き換えの行を出すか (その武器を主の手に持っているか、もう選んでいるとき)
    private boolean legendShown(SkillData d, Skill s) {
        Rune legend = Rune.legendFor(s, minecraft.player);
        return legend != null && (legend.held(minecraft.player) || d.rune(s) == 4);
    }

    // 狭い箱に名前を収める (中心 cx、上端 y)。入らなければ箱の中で横へ流す (Marquee。前は「…」で切っていた)
    private void drawFit(GuiGraphics g, Component name, int cx, int y, int maxW, int color) {
        int w = font.width(name);
        if (w <= maxW) {
            g.drawString(font, name, cx - w / 2, y, color, false);
        } else {
            jp.revati.skyabyss.client.ui.Marquee.draw(g, font, name, cx - maxW / 2, y, maxW, color, false);
        }
    }

    // 刻印の段を描き、下端を返す (送れる面の中の座標)
    private int renderRunes(GuiGraphics g, SkillData d, Skill s, int x, int y, int w, int mx, int my) {
        boolean available = d.runeAvailable(s);
        int current = d.rune(s);
        MutableComponent head = Component.translatable("gui.skyabyss.skill.runes_head");
        if (!available) {
            head.append(Component.literal("  ")).append(Component.translatable(
                    d.rank(s) >= s.maxRank ? "gui.skyabyss.skill.runes_need_mastery" : "gui.skyabyss.skill.runes_need_max", d.rank(s), s.maxRank));
        } else if (current == 0) {
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.runes_pick"));
        }
        g.drawString(font, head.getVisualOrderText(), x, y, available ? Paper.INK_HEAD : Paper.INK_SOFT, false);
        int ry = y + 11;
        List<Rune> list = s.runes();
        int cells = list.size() + 1;
        int cell = (w - 4 - (cells - 1) * 2) / cells;
        for (int i = 0; i < list.size(); i++) {
            Rune r = list.get(i);
            int rx = x + i * (cell + 2);
            boolean mine = current == r.index;
            boolean hover = in(mx, my, rx, ry, cell, 12) && available;
            int bg = mine ? Paper.PINK : hover ? 0xFFFFF6EA : available ? Paper.CREAM_LO : 0xFF0C0E15;
            int edge = mine ? Paper.PINK_LO : runeArmed == r.index ? 0xFF000000 | INK_DENY : available ? 0xFF8C8078 : 0xFFB8AEA4;
            g.fill(rx, ry, rx + cell, ry + 12, edge);
            g.fill(rx + 1, ry + 1, rx + cell - 1, ry + 11, bg);
            Component name = Component.translatable(r.nameKey());
            int color = mine || available ? Paper.INK : Paper.INK_SOFT;
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
            int bg = mine ? Paper.PINK : hover && usable ? 0xFFFFF6EA : usable ? Paper.CREAM_LO : 0xFF0C0E15;
            int edge = mine ? Paper.PINK_LO : runeArmed == Rune.MASTERY ? 0xFF000000 | INK_DENY : usable ? 0xFF8C8078 : 0xFFB8AEA4;
            g.fill(rx, ry, rx + cell, ry + 12, edge);
            g.fill(rx + 1, ry + 1, rx + cell - 1, ry + 11, bg);
            Component name = Component.translatable("rune.skyabyss.mastery");
            drawFit(g, name, rx + cell / 2, ry + 2, cell - 3, mine || usable ? Paper.INK : Paper.INK_SOFT);
            runeRows.add(new int[] {rx, ry, cell, 12, Rune.MASTERY});
            if (hover) {
                hoverRune = Rune.MASTERY;
            }
        }
        // 逸品の書き換え: 全幅の行 (その武器を持つあいだ)
        boolean legendRow = legendShown(d, s);
        if (legendRow) {
            Rune r = Rune.legendFor(s, minecraft.player);
            int ly = ry + 14;
            // 持つだけで効く (選ばない)。効いていれば金の縁
            boolean mine = SkillManager.legendRune(minecraft.player, s) == r;
            boolean usable = r.held(minecraft.player);
            boolean hover = in(mx, my, x, ly, w - 4, 12) && usable;
            g.fill(x, ly, x + w - 4, ly + 12, mine ? Paper.PINK_LO : runeArmed == 4 ? 0xFF000000 | INK_DENY : usable ? 0xFF8C8078 : 0xFFB8AEA4);
            g.fill(x + 1, ly + 1, x + w - 5, ly + 11, mine ? 0xFF4A3A2C : hover ? 0xFF283046 : 0xFF2A3044);
            Component name = Component.translatable("gui.skyabyss.skill.rune_legend", Component.translatable(r.nameKey()));
            g.drawString(font, name, x + (w - 4 - font.width(name)) / 2, ly + 2, mine || usable ? Paper.INK : Paper.INK_SOFT, false);
            if (hover) {
                hoverRune = 4;
            }
        }
        // 見ている (選んでいる) 刻印の効果
        int shown = hoverRune != 0 ? hoverRune : current;
        Rune shownRune = shown == 0 || shown == 4 && hoverRune != 4 ? null : shown == 4 ? Rune.legendFor(s, minecraft.player) : Rune.of(s, shown);
        int ty = ry + (legendRow ? 28 : 14);
        if (shown == Rune.MASTERY) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("rune.skyabyss.mastery.effect", SkillRules.MASTERY_RUNE_LEVEL,
                    d.masteryLevel(s)).withColor(shown == current ? TEXT : PALE), w)) {
                g.drawString(font, line, x, Golden.textY(ty, LH), Paper.INK, false);
                ty += LH;
            }
        }
        if (shownRune != null && !shownRune.legendary()) {
            // 箱に収まらず縮めた名前も、ここで全文が読める
            g.drawString(font, Component.translatable(shownRune.nameKey()), x, Golden.textY(ty, LH), Paper.INK_HEAD, false);
            ty += LH;
        }
        if (shownRune != null) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable(shownRune.effectKey()).withColor(shown == current ? TEXT : PALE), w)) {
                g.drawString(font, line, x, Golden.textY(ty, LH), Paper.INK, false);
                ty += LH;
            }
        }
        if (shownRune != null && shownRune.legendary()) {
            for (FormattedCharSequence line : TextWrap.split(font, Component.translatable("gui.skyabyss.skill.legend_need", Component.translatable(shownRune.skillNameKey())).withColor(SOFT), w)) {
                g.drawString(font, line, x, Golden.textY(ty, LH), Paper.INK_SOFT, false);
                ty += LH;
            }
        }
        if (runeArmed != 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.skill.rune_again", d.runeSwaps == 0 ? 0 : SkillRules.RUNE_SWAP_COST), x, Golden.textY(ty, LH), INK_DENY, false);
            ty += LH;
        }
        return ty;
    }

    // 習得のボタンに重ねたとき: 次の段の効果
    private void renderLearnTooltip(GuiGraphics g, SkillData d, int mx, int my) {
        if (v2() != null) {
            if (selectedNode != null && learn.visible && learn.isHovered()) {
                tip(wrapAll(v2Lines(d, selectedNode, level()), 200));
            }
            return;
        }
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
        tip(lines);
    }

    private boolean subTabHovered() {
        for (SubTab t : subTabs) {
            if (t != null && t.isHovered()) {
                return true;
            }
        }
        return false;
    }

    private void renderBottom(GuiGraphics g, SkillData d, int level) {
        int points = tree == SkillTree.PAST ? d.pastLeft() : d.unspentFor(tree, level);
        int fx = left + FOOT_L.x() + Golden.S5;
        int fy = top + FOOT_L.y() + 6;
        int fw = SLOT_X - FOOT_L.x() - 2 * Golden.S5;
        // SP: 小さな札の字と、2 倍の数 (試着中は桃色の「仮」)
        Component spl = Component.translatable(tryon != null ? "gui.skyabyss.skill.sp_label_tryon" : "gui.skyabyss.skill.sp_label");
        g.drawString(font, spl, fx, fy + 4, 0xFF000000 | (tryon != null ? Paper.PINK_HI : HILL_SOFT), false);
        String num = String.valueOf(points);
        int numX = fx + font.width(spl) + 4;
        g.pose().pushPose();
        g.pose().translate(numX, fy - 2, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, num, 0, 0, 0xFF000000 | (tryon != null ? Paper.PINK_HI : points > 0 ? Paper.ON_BLACK_HI : HILL_SOFT), false);
        g.pose().popPose();
        // 点を使うと、字の右に桃色の「-n」が浮かんで消える (600ms)
        long nowP = net.minecraft.Util.getMillis();
        if (lastPoints >= 0 && points < lastPoints && lastTree2 == tree && nowP >= quietUntil) {
            spentDelta = lastPoints - points;
            spentAt = nowP;
        }
        lastPoints = points;
        lastTree2 = tree;
        float sp = (nowP - spentAt) / 600f;
        if (sp < 1f) {
            int tw = numX - fx + font.width(num) * 2;
            int a = Math.max(6, Math.round(255 * (1f - sp * sp)));
            g.drawString(font, "-" + spentDelta, fx + tw + 3, fy - 2 - Math.round(out(sp) * 6f), (a << 24) | (Paper.PINK_HI & 0xFFFFFF), false);
        }
        double max = SkillManager.maxMana(minecraft.player);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.skill.mana", Math.round(Math.max(0, d.mana)), Math.round(max)),
                fx, Golden.textY(fy + LH + 2, LH), fw, HILL_SOFT, false);
        for (int i = 0; i < d.slotCount(); i++) {
            int x = left + SLOT_X + i * SLOT_STEP;
            int y = top + SLOT_Y;
            boolean locked = false;
            Skill s = d.slot(i);
            // 技の枠: 少し傾けた生成りの札 (技を置いた枠は桃色)
            boolean filled = s != null;
            // 板の絵柄: 空きは沈んだ升、技を置いた枠は灯った升
            if (filled) {
                jp.revati.skyabyss.client.ui.Plate.tile(g, x, y, NODE, NODE, jp.revati.skyabyss.client.ui.Plate.ON, jp.revati.skyabyss.client.ui.Plate.GOLD);
            } else {
                jp.revati.skyabyss.client.ui.Plate.well(g, x, y, NODE, NODE);
            }
            if (locked) {
                g.setColor(0.45f, 0.45f, 0.52f, 1f);
                SkillHud.drawIcon(g, 44, x + 3, y + 3);
                g.setColor(1f, 1f, 1f, 1f);
            } else if (s != null) {
                SkillHud.drawIcon(g, jp.revati.skyabyss.rpg.skill.SkillChanges.icon(minecraft.player, s), x + 3, y + 3);
                SkillHud.changeFrame(g, minecraft.player, s, x + 3, y + 3, 16);
                SkillHud.changeMark(g, minecraft.player, s, x + 2, y + 2);
            }
            String k = SkillClientSetup.SLOT_KEYS[i].getTranslatedKeyMessage().getString();
            if (k.length() > 3) {
                k = k.substring(0, 3);
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            if (s == null) {
                g.drawString(font, k, x + NODE - 2 - font.width(k), y + NODE - 9, Paper.INK, false);
            } else {
                g.drawString(font, k, x + NODE - 2 - font.width(k), y + NODE - 9, Paper.ON_BLACK, true);
            }
            g.pose().popPose();
        }
    }

    // 秘伝の枠 (技の枠の右、型の札の左)。中は付けた秘伝の技の絵と段の点 (I〜III)
    static final int ARTS_BOX = Golden.S13;
    static final int ARTS_X = SLOT_X + 4 * SLOT_STEP + 1;
    static final int ARTS_Y = FOOT_L.y() + 3;

    private void renderArts(GuiGraphics g) {
        var held = jp.revati.skyabyss.rpg.skill.SecretArts.heldFor(minecraft.player);
        for (int i = 0; i < jp.revati.skyabyss.rpg.skill.SecretArts.SLOTS; i++) {
            int x = left + ARTS_X + (i % 2) * (ARTS_BOX + 2);
            int y = top + ARTS_Y + (i / 2) * (ARTS_BOX + 2);
            boolean on = i < held.size();
            // 秘伝の枠: 小さな紙の升 (付けていれば桃色の縁に黒の地、空きは生成り)
            // 板の絵柄: 空きは沈んだ升、付けた枠は灯った升
            if (on) {
                jp.revati.skyabyss.client.ui.Plate.tile(g, x, y, ARTS_BOX, ARTS_BOX, jp.revati.skyabyss.client.ui.Plate.ON, jp.revati.skyabyss.client.ui.Plate.GOLD);
            } else {
                jp.revati.skyabyss.client.ui.Plate.well(g, x, y, ARTS_BOX, ARTS_BOX);
            }
            if (on) {
                var h = held.get(i);
                SkillHud.drawIcon(g, h.art().skill.iconIndex(), x + 1, y + 1, ARTS_BOX - 2);
                // 段の点 (下の縁に 1〜3 つ)
                for (int t = 0; t < h.tier(); t++) {
                    int px = x + ARTS_BOX / 2 - h.tier() + t * 2;
                    g.fill(px, y + ARTS_BOX - 2, px + 1, y + ARTS_BOX - 1, 0xFFFFFFFF);
                }
            }
        }
        Component label = Component.translatable("gui.skyabyss.skill.arts_label");
        g.drawString(font, label, left + ARTS_X + ARTS_BOX + 1 - font.width(label) / 2, top + ARTS_Y + ARTS_BOX + 3, HILL_SOFT, false);
    }

    private boolean artsHovered(double mx, double my) {
        return mx >= left + ARTS_X && mx < left + ARTS_X + 2 * ARTS_BOX + 2 && my >= top + ARTS_Y && my < top + ARTS_Y + ARTS_BOX + 12;
    }

    private void renderHover(GuiGraphics g, SkillData d, int mx, int my) {
        if (artsHovered(mx, my)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.skyabyss.skill.arts_title").withColor(GOLD));
            var held = jp.revati.skyabyss.rpg.skill.SecretArts.heldFor(minecraft.player);
            for (int i = 0; i < jp.revati.skyabyss.rpg.skill.SecretArts.SLOTS; i++) {
                if (i < held.size()) {
                    var h = held.get(i);
                    lines.add(Component.literal("・").append(jp.revati.skyabyss.rpg.skill.SecretArts.name(h.art(), h.tier())).append(" — ")
                            .append(Component.translatable(h.art().skill.nameKey())).withColor(jp.revati.skyabyss.rpg.skill.SkillChanges.Kind.SECRET.rgb));
                    lines.add(Component.literal("  ").append(Component.translatable(h.art().nameKey() + ".desc")).withColor(TEXT));
                } else {
                    lines.add(Component.translatable("gui.skyabyss.skill.arts_empty").withColor(DIM));
                }
            }
            lines.add(Component.translatable("gui.skyabyss.skill.arts_hint").withColor(DIM));
            tip(wrapAll(lines, 220));
            return;
        }
        if (page == 0 && v2() != null) {
            AbilityNode n = v2NodeAt(mx, my);
            if (n != null) {
                tip(wrapAll(v2Lines(d, n, level()), 200));
                return;
            }
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
            tipC(lines);
            return;
        }
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            Skill s = d.slot(slot);
            List<Component> lines = new ArrayList<>();
            if (slot >= d.slotCount()) {
                return;
            }
            lines.add(s == null ? Component.translatable("gui.skyabyss.skill.slot_empty").withColor(SOFT)
                    : Component.translatable(s.nameKey()).withColor(GOLD));
            if (s != null) {
                changeLines(lines, s);
            }
            lines.add(Component.translatable("gui.skyabyss.skill.slot_key", SkillClientSetup.SLOT_KEYS[slot].getTranslatedKeyMessage()).withColor(SOFT));
            lines.add(Component.translatable(selected != null && !selected.active && d.knows(selected) ? "gui.skyabyss.skill.passive_no_slot"
                    : selected != null && selected.active && d.knows(selected)
                    ? "gui.skyabyss.skill.slot_put" : "gui.skyabyss.skill.slot_how", selected == null ? "" : Component.translatable(selected.nameKey()))
                    .withColor(DIM));
            tipC(lines);
            return;
        }
        SkillTree t = page == 0 ? treeTabAt(mx, my) : null;
        if (t != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(t.nameKey()).withColor(GOLD));
            lines.add(Component.translatable(t.descKey()).withColor(SOFT));
            tipC(lines);
            return;
        }
        for (int i = 0; i < SkillRules.LOADOUTS_PER_JOB; i++) {
            if (loadLoadout[i].isHovered()) {
                SkillData rd = real();
                jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(rd.curJob);
                if (job == null) {
                    tip(new ArrayList<>(TextWrap.split(font, Component.translatable("message.skyabyss.skill.no_job_weapon").withColor(TEXT), 190)));
                    return;
                }
                List<FormattedCharSequence> lines = new ArrayList<>(TextWrap.split(font, Component.translatable(saveMode ? "gui.skyabyss.skill.loadout_save_tip" : "gui.skyabyss.skill.loadout_load_tip",
                        i + 1, rd.loadouts[SkillData.loadoutIndex(job, i)].saved ? Component.translatable("gui.skyabyss.skill.loadout_saved") : Component.translatable("gui.skyabyss.skill.loadout_empty")).withColor(TEXT), 190));
                tip(lines);
                return;
            }
        }
        if (saveToggle.isHovered() || tryonBtn.isHovered() || subTabHovered() || tryonOk.isHovered() || tryonUndo.isHovered() || tryonCancel.isHovered()) {
            String key = saveToggle.isHovered() ? "gui.skyabyss.skill.loadout_mode_tip" : tryonBtn.isHovered() ? "gui.skyabyss.skill.tryon_tip"
                    : subTabHovered() ? "gui.skyabyss.skill.page_tip" : tryonOk.isHovered() ? "gui.skyabyss.skill.tryon_ok_tip"
                    : tryonUndo.isHovered() ? "gui.skyabyss.skill.tryon_undo_tip" : "gui.skyabyss.skill.tryon_cancel_tip";
            tip(new ArrayList<>(TextWrap.split(font, Component.translatable(key, SkillRules.LOADOUTS_PER_JOB).withColor(TEXT), 190)));
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
            tip(lines);
        }
    }

    // ---------------------------------------------------------------- 技の木 v2 (docs/design/48)。職の木は格子の盤
    // いま見ている木が職の木 v2 なら、その木 (淵・前世・木のない職は null)
    private AbilityTree v2() {
        return AbilityTrees.of(tree);
    }

    private Job v2Job() {
        return Job.ofTree(tree);
    }

    // 盤の大きさを木に合わせ、見え方を戻す
    private void fitCanvas() {
        AbilityTree t = v2();
        if (t != null) {
            canvas.setContent(GRID_X0 * 2 + t.width * GRID + NODE, GRID_Y0 + t.height * GRID + NODE + 24);
        } else {
            canvas.setContent(BOARD_W, BOARD_H);
        }
        canvas.reset();
    }

    private static int v2X(AbilityNode n) {
        return GRID_X0 + n.x * GRID;
    }

    private static int v2Y(AbilityNode n) {
        return GRID_Y0 + n.y * GRID;
    }

    // 節の大きさ (種類ごと。使う技がいちばん大きく、基礎はいちばん小さい。2026-10-30 の声「どれがスキルかわからない」)
    static int v2Size(AbilityNode n) {
        return switch (n.kind) {
            case ACTIVE -> 30;
            case KEYSTONE, TRAIT -> 28;
            case PASSIVE, MUTATION -> 24;
            case STAT -> 15;
            default -> 18;
        };
    }

    // 節の左上 (格子の升の真ん中に、大きさの分だけ寄せる)
    private static int v2L(AbilityNode n) {
        return v2X(n) + (NODE - v2Size(n)) / 2;
    }

    private static int v2T(AbilityNode n) {
        return v2Y(n) + (NODE - v2Size(n)) / 2;
    }

    // 開くときの節の跳ね (0..1、少し行き過ぎる)。上の段から 22ms ずつ遅れて
    private float pop(AbilityNode n, long now) {
        return back((now - assembleAt - n.y * ROW_STAGGER) / (float) POP_MS);
    }

    private AbilityNode v2NodeAt(double mx, double my) {
        AbilityTree t = v2();
        if (t == null || !canvas.contains(mx, my)) {
            return null;
        }
        double bx = canvas.toContentX(mx);
        double by = canvas.toContentY(my);
        for (AbilityNode n : t.nodes()) {
            // 小さな節も押しやすいよう、当たりは 18 より小さくしない
            int hs = Math.max(18, v2Size(n));
            if (in(bx, by, v2X(n) + (NODE - hs) / 2, v2Y(n) + (NODE - hs) / 2, hs, hs)) {
                return n;
            }
        }
        return null;
    }

    // 節の絵: 技の節はその技の絵、基礎は icons2 の 0〜3 (筋力・敏捷・精神・耐久)、刻印 4、常に 45、閾値 46、型の要 47 (tools/gen/ui/gen_deep_skill_assets.py)
    private static int v2Icon(AbilityNode n) {
        // 変異の節は変異の絵
        if (n.mutation != null) {
            return n.mutation.iconIndex();
        }
        return switch (n.kind) {
            case STAT -> 100 + n.stat.ordinal();
            case RUNE -> 104;
            case EFFECT -> 145;
            case THRESHOLD -> 146;
            case TRAIT -> 147;
            default -> n.skill.iconIndex();
        };
    }

    // 丸い受け皿の節 (技の絵そのもの: 使う技・常の技・変異)
    private static boolean v2Round(AbilityNode n) {
        return n.kind == AbilityNode.Kind.ACTIVE || n.kind == AbilityNode.Kind.PASSIVE || n.kind == AbilityNode.Kind.MUTATION;
    }

    // 塗りつぶした丸 (半径 r、真ん中 cx, cy)
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.floor(Math.sqrt(Math.max(0, r * r - yy * yy)) + 0.25);
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static boolean v2Big(AbilityNode n) {
        return n.kind == AbilityNode.Kind.KEYSTONE || n.kind == AbilityNode.Kind.TRAIT;
    }

    private void renderTreeV2(GuiGraphics g, SkillData d, int level) {
        AbilityTree t = v2();
        Job job = v2Job();
        MutableComponent head = job == null ? Component.translatable(tree.nameKey())
                : Component.translatable("job.skyabyss.subtitle", Component.translatable(job.nameKey()), Component.translatable(tree.nameKey()));
        if (job != null && minecraft != null && minecraft.player != null && Job.held(minecraft.player) == job) {
            head.append(Component.literal("  ")).append(Component.translatable("gui.skyabyss.skill.job_held"));
        }
        if (tryon == null) {
            pendingCaption = head;
            pendingCaptionColor = Paper.ON_BLACK;
        }
        long now = net.minecraft.Util.getMillis();
        java.util.Set<String> cur = d.nodesOf(tree);
        if (takenSeen == null || takenTree != tree || now < quietUntil) {
            takenAt.clear();
        } else {
            for (String id : cur) {
                if (!takenSeen.contains(id)) {
                    takenAt.put(id, now);
                    lastTakeAt = now;
                }
            }
        }
        takenSeen = new java.util.HashSet<>(cur);
        takenTree = tree;
        takenAt.values().removeIf(t0 -> now - t0 > TAKE_MS);
        if (hoverNode != lastHoverNode && hoverNode != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.1f));
        }
        lastHoverNode = hoverNode;
        if (selectedNode != lastSelNode) {
            selectedAt = now;
            lastSelNode = selectedNode;
        }
        canvas.begin(g);
        // 型の名前と点: その型の節が並ぶ列の下 (盤の末尾。節の字と重ならない) に 0.75 倍で
        int bottomY = GRID_Y0 + t.height * GRID + NODE + 4;
        for (String arch : t.archetypes) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (AbilityNode n : t.nodes()) {
                if (arch.equals(n.arch)) {
                    minX = Math.min(minX, v2X(n));
                    maxX = Math.max(maxX, v2X(n));
                }
            }
            if (minX == Integer.MAX_VALUE) {
                continue;
            }
            int pts = d.archPoints(tree, arch);
            Component label = Component.translatable(t.archKey(arch)).append(" " + pts);
            g.drawString(font, label, (minX + maxX + NODE) / 2 - font.width(label) / 2, bottomY, pts > 0 ? PALE : SOFT, false);
        }
        // つなぐ線 (先に描く)。2026-11-02 の声「線が機能してない」: 前は折れ線で、同じ行どうしの横の線が重なって、どの節がどれにつながるか
        // 読めなかった。いまは節の真ん中どうしをまっすぐ結ぶ。両方取っていれば金、手前を取っていれば (次に取れる) 明るく、ほかは暗く。
        // マウスの下か選んだ節へつながる線は太い水色 (前提はどれか一つ取っていればよい)
        AbilityNode focus = hoverNode != null ? hoverNode : selectedNode;
        for (int pass = 0; pass < 2; pass++) {
            for (AbilityNode n : t.nodes()) {
                for (String rid : n.requires) {
                    AbilityNode r = t.node(rid);
                    if (r == null) {
                        continue;
                    }
                    boolean lit = n == focus || r == focus;
                    if ((pass == 1) != lit) {
                        continue;
                    }
                    boolean a = d.hasNode(tree, r.id);
                    boolean b = d.hasNode(tree, n.id);
                    // 取った道 = 桃色の糸、手前を取った道 (次に取れる) = 生成りの点線、まだ = 暗い点線。
                    // マウスの下か選んだ節へつながる線は明るい桃色の太い糸
                    if (Math.min(pop(r, now), pop(n, now)) < 0.85f) {
                        continue;
                    }
                    int x0 = v2L(r) + v2Size(r) / 2;
                    int y0 = v2T(r) + v2Size(r) / 2;
                    int x1 = v2L(n) + v2Size(n) / 2;
                    int y1 = v2T(n) + v2Size(n) / 2;
                    Long t0 = a && b ? takenAt.get(n.id) : null;
                    if (t0 != null && !lit) {
                        // 取ったばかり: 前の点線の上を、親から桃色の糸が伸びていく
                        float f = out((now - t0) / 240f);
                        thread(g, x0, y0, x1, y1, Paper.CREAM_LO, 1, true);
                        thread(g, x0, y0, x0 + Math.round((x1 - x0) * f), y0 + Math.round((y1 - y0) * f), Paper.PINK, 2, false);
                        continue;
                    }
                    int color = lit ? Paper.PINK_HI : a && b ? Paper.PINK : a ? Paper.CREAM_LO : Paper.GREY_LO;
                    thread(g, x0, y0, x1, y1, color, lit || a && b ? 2 : 1, !lit && !(a && b));
                }
            }
        }
        for (AbilityNode n : t.nodes()) {
            int x = v2L(n);
            int y = v2T(n);
            int sz = v2Size(n);
            boolean taken = d.hasNode(tree, n.id);
            SkillData.TakeResult tr = taken ? SkillData.TakeResult.TAKEN : d.canTake(tree, n, level);
            boolean reachable = tr == SkillData.TakeResult.OK || tr == SkillData.TakeResult.NO_POINTS;
            int state = taken ? 3 : reachable ? 1 : 0;
            // 節の形 (2026-11-09 の声「丸あるのに四角もあって見にくい」): 技の絵 (使う技・常の技・変異) は丸い受け皿、
            // 能力値・段・刻印などの小さな節は平らな四角の板、要の節 (奥義・型の要) は今の大きな枠。縁の色は取った = 金、取れる = 明るい、まだ = 暗い
            // 切り絵 (docs/design/56 の「切り絵」): 技の絵 (使う技・常の技・変異) は切り抜きの円、要の節は大きな菱、小さな節は菱。
            // 縁の色は取った = 桃色、取れる = 生成り、まだ = 暗い色。選んだ節は持ち上げて桃色の菱を留める
            boolean round = v2Round(n);
            float pop = pop(n, now);
            if (pop < 0.12f) {
                continue;
            }
            Long t0 = taken ? takenAt.get(n.id) : null;
            float k = t0 == null ? 1f : (now - t0) / (float) TAKE_MS;
            boolean hot = n == hoverNode && n != selectedNode;
            // 大きさ: 開くときは 0.5 から跳ねて 1、取ると 1.5 から押しつけて 1、選ぶと少し膨らんで戻る
            float scale = 0.5f + 0.5f * pop;
            if (t0 != null) {
                scale *= 1f + 0.5f * (1f - back((now - t0) / 200f));
            }
            if (n == selectedNode) {
                scale *= 1f + 0.16f * (1f - back((now - selectedAt) / 180f));
            }
            float cx = x + sz / 2f;
            float cy = y + sz / 2f;
            g.pose().pushPose();
            g.pose().translate(cx, cy - (hot ? 1 : 0), 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-cx, -cy, 0);
            int ring = taken ? (k < 0.3f ? Paper.PINK_HI : Paper.PINK) : state == 1 ? 0xFF232A3C : Paper.GREY_LO;
            int r = round ? sz / 2 + 2 : v2Big(n) ? sz / 2 + 4 : sz / 2 + 3;
            paperNode(g, round, x, y, sz, r, ring, n == selectedNode, hot);
            if (state == 0) {
                g.setColor(0.42f, 0.42f, 0.48f, 1f);
            } else if (state == 1) {
                g.setColor(0.72f, 0.72f, 0.78f, 1f);
            }
            // 菱の中の絵は、内側の菱に収まる大きさ
            int icon = round ? sz : Math.min(sz, (int) ((r - 2) * 1.41f));
            int io = (sz - icon) / 2;
            if (!round) {
                // 菱は画素の芯 (round(sz/2) + 0.5) に描かれるので、絵は奇数の大きさにして同じ芯に置く
                icon -= icon % 2 == 0 ? 1 : 0;
                io = Math.round(sz / 2f) - (icon - 1) / 2;
            }
            SkillHud.drawIcon(g, v2Icon(n), x + io, y + io, icon);
            g.setColor(1f, 1f, 1f, 1f);
            // 技の稽古の覚える段: 取れる使う技を光らせる
            if (lessonLearn && n.kind == AbilityNode.Kind.ACTIVE && tr == SkillData.TakeResult.OK) {
                jp.revati.skyabyss.client.tutorial.SkillLessonClient.frame(g, x, y, sz, sz);
            }
            // 節の下の字 (点・+数) は出さない (2026-11-09 の声「アイコンの下にある 1sp や +4 いらない」)。点と数は節の説明に出る
            // 使う技の節には右上に小さな菱形の印 (枠に置ける。丸の外の縁に)
            if (n.kind == AbilityNode.Kind.ACTIVE) {
                activeMark(g, x + sz - 1, y - 1, taken ? Paper.PINK_HI : Paper.GREY);
            }
            // 排他の節には赤紫の点 (左上)
            if (!n.blocks.isEmpty()) {
                g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF000000 | BOARD_DENY);
            }
            // 同じスキルの変化を先に取っていて取れない節は、赤い × (2026-11-02「思ったような選択ルートにならない」: 黙って閉じていた)
            if (!taken && tr == SkillData.TakeResult.EXCLUDED) {
                int c = 0xFF000000 | BOARD_DENY;
                for (int i = 0; i < sz - 4; i++) {
                    g.fill(x + 2 + i, y + 2 + i, x + 4 + i, y + 4 + i, c);
                    g.fill(x + sz - 4 - i, y + 2 + i, x + sz - 2 - i, y + 4 + i, c);
                }
            }
            g.pose().popPose();
            // 判を押したあと: 桃色の小さな菱が 4 つ、斜めに散る
            if (t0 != null && k > 0.12f && k < 0.9f) {
                float dist = sz / 2f + 2 + 12 * out((k - 0.12f) / 0.78f);
                for (int q = 0; q < 4; q++) {
                    int sx = Math.round(cx + (q % 2 == 0 ? -dist : dist) * 0.8f);
                    int sy = Math.round(cy + (q < 2 ? -dist : dist) * 0.8f);
                    Paper.draw(g, "skill_spark", "", sx - 3, sy - 3, 7, 7, p -> p.diamond(3, 3, 3, Paper.PINK_HI, 0xFFFFFFFF));
                }
            }
        }
        canvas.end(g);
    }

    private void renderInfoV2(GuiGraphics g, SkillData d, int level, int mx, int my) {
        AbilityTree t = v2();
        AbilityNode n = selectedNode;
        if (n != null && n.skill != null && n.kind != AbilityNode.Kind.RUNE && n.kind != AbilityNode.Kind.MUTATION) {
            // 技の節: いつもの技の説明 (段・待ち・マナ・効果)。節の点は「取る」の釦に重ねると出る
            selected = n.skill;
            renderInfoSkill(g, d, level, mx, my);
            return;
        }
        runeRows.clear();
        int x = left + INFO_X + Golden.S5;
        int y = top + INFO_Y + 2;
        int w = INFO_W - 2 * Golden.S5 - 4;
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component name = n == null ? Component.translatable(tree.nameKey()) : Component.translatable(n.nameKey(t.key));
        List<FormattedCharSequence> nameLines = TextWrap.split(font, name.copy().withColor(GOLD), w);
        int bodyY;
        if (n != null) {
            // 節の札: 大きな名・種類の判子・要る SP の札・型の札
            int yy;
            if (font.width(name) * 2 <= w) {
                SkillStage.big(g, font, name, x, y + 1, w, 0xFF000000 | Paper.INK_HEAD);
                yy = y + 1 + 2 * font.lineHeight + 4;
            } else {
                for (int i = 0; i < nameLines.size(); i++) {
                    g.drawString(font, nameLines.get(i), x, Golden.textY(y + i * LH, LH), Paper.INK_HEAD, false);
                }
                yy = y + nameLines.size() * LH + 2;
            }
            float press = SkillStage.clamp01((net.minecraft.Util.getMillis() - infoSwapAt - 60) / 220f);
            int cx = x + SkillStage.stamp(g, font, Component.translatable(n.kind.key()), x, yy, Math.max(press, 0.06f), false) + 4;
            cx += SkillStage.chip(g, font, Component.translatable("gui.skyabyss.tree.cost", n.cost), cx, yy, true) + 3;
            if (n.arch != null) {
                Component ac = Component.translatable(t.archKey(n.arch));
                if (cx + font.width(ac) + 8 <= x + w) {
                    SkillStage.chip(g, font, ac, cx, yy, false);
                }
            }
            bodyY = yy + 18;
        } else if (font.width(name) * 2 <= w) {
            // 表紙: 木の名前を 2 倍で
            g.pose().pushPose();
            g.pose().translate(x, y + 1, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(font, name.copy().withColor(GOLD).getVisualOrderText(), 0, 0, Paper.INK_HEAD, false);
            g.pose().popPose();
            bodyY = y + 2 * font.lineHeight + Golden.S3 + 2;
        } else {
            for (int i = 0; i < nameLines.size(); i++) {
                g.drawString(font, nameLines.get(i), x, Golden.textY(y + i * LH, LH), Paper.INK_HEAD, false);
            }
            bodyY = y + nameLines.size() * LH + Golden.S3;
        }
        inkRule(g, x, left + INFO_X + INFO_W - Golden.S5, bodyY - 2);
        if (n == null) {
            // 発動スキルの数は、字ではなく菱の列で (置ける数のうち、いくつ持っているか)
            int actives = d.activeCount();
            Component head = Component.translatable("gui.skyabyss.skill.slots_head");
            int py = bodyY + 2;
            g.drawString(font, head.copy().withColor(SOFT).getVisualOrderText(), x, Golden.textY(py, LH), Paper.INK, false);
            int px = x + font.width(head) + 6;
            for (int i = 0; i < SkillRules.BASE_SLOTS; i++) {
                boolean on = i < actives;
                Paper.draw(g, "skill_pip_" + (on ? 1 : 0), "", px + i * 11, py, 11, 11,
                        p -> p.diamond(5, 5, 5, on ? Paper.PINK : Paper.CREAM_LO, on ? Paper.PINK_HI : Paper.CREAM_LO));
            }
            bodyY += LH + 4;
        }
        info.setViewport(left + INFO_X + 2, bodyY, INFO_W - 4, wholeRows(top + infoBodyBottom() - bodyY));
        if (n == null) {
            lines.addAll(TextWrap.split(font, Component.translatable(tree.descKey()).withColor(SOFT), w));
            lines.add(FormattedCharSequence.EMPTY);
            // 型ごとの点は盤の下に出ているので、ここには書かない
            // 型の力 (溜まるもの。rpg/skill/Gauges): その型に 8 点使うと出る
            for (String arch : t.archetypes) {
                var gk = t.job == null ? null : jp.revati.skyabyss.rpg.skill.Gauges.Kind.of(t.job, arch);
                if (gk != null) {
                    boolean on = d.archPoints(tree, arch) >= jp.revati.skyabyss.rpg.skill.Gauges.ARCH_POINTS;
                    lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.tree.gauge_line", Component.translatable(gk.nameKey()),
                            jp.revati.skyabyss.rpg.skill.Gauges.ARCH_POINTS, Component.translatable(gk.nameKey() + ".desc")).withColor(on ? PALE : SOFT), w));
                }
            }
            if (tree == SkillTree.PAST) {
                lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.ext.past_points", d.pastLeft(), d.pastPool()).withColor(PALE), w));
            }
            int[] st = d.statsFromNodes(tree);
            MutableComponent stats = Component.empty();
            for (jp.revati.skyabyss.rpg.GrowthRules.Stat stat : jp.revati.skyabyss.rpg.GrowthRules.Stat.values()) {
                if (stat.ordinal() > 0) {
                    stats.append("  ");
                }
                stats.append(Component.translatable(stat.nameKey())).append(" " + st[stat.ordinal()]);
            }
            lines.addAll(TextWrap.split(font, stats.withColor(SOFT), w));
            lines.add(FormattedCharSequence.EMPTY);
            lines.addAll(TextWrap.split(font, Component.translatable("gui.skyabyss.tree.pick_hint").withColor(DIM), w));
            drawInfo(g, lines, x);
            return;
        }
        List<Component> body = v2Lines(d, n, level);
        body.remove(0);
        // 種類・点・型の行は上の札に出したので省く
        if (!body.isEmpty()) {
            body.remove(0);
        }
        lines.addAll(wrapAll(body, w));
        drawInfo(g, lines, x);
    }

    private List<FormattedCharSequence> wrapAll(List<Component> parts, int w) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component c : parts) {
            out.addAll(TextWrap.split(font, c, w));
        }
        return out;
    }

    // 節の説明の行 (名前・種類と点・中身・いま取れるか)。囲みと説明の欄の両方で使う
    // 今その技を変えているもの (刻印・書き換え・変異・秘伝。SkillChanges)
    private void changeLines(List<Component> lines, Skill s) {
        var changes = jp.revati.skyabyss.rpg.skill.SkillChanges.of(minecraft.player, s);
        if (changes.isEmpty()) {
            return;
        }
        lines.add(Component.translatable("gui.skyabyss.skill.change.head").withColor(SOFT));
        for (var c : changes) {
            lines.add(Component.literal(" ").append(c.line()).withColor(c.active() ? c.kind().rgb : DIM));
            // 何が変わるか (2026-11-09 の声「説明もないので何がなにかわからない」)
            if (c.effect() != null && c.active()) {
                lines.add(Component.literal("   ").append(c.effect()).withColor(TEXT));
            }
        }
    }

    private List<Component> v2Lines(SkillData d, AbilityNode n, int level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(n.nameKey(v2().key)).withColor(GOLD));
        lines.addAll(v2Status(d, n, level, false));
        return lines;
    }

    // 種類と点 (withKind)・中身 (技の節では省く: 技の説明がある)・前提・排他・型の点・いま取れるか
    private List<Component> v2Status(SkillData d, AbilityNode n, int level, boolean skillInfoShown) {
        AbilityTree t = v2();
        String key = t.key;
        List<Component> lines = new ArrayList<>();
        MutableComponent kind = Component.translatable(n.kind.key()).append(Component.literal("・")).append(Component.translatable("gui.skyabyss.tree.cost", n.cost));
        if (n.arch != null) {
            kind.append(Component.literal("・")).append(Component.translatable(t.archKey(n.arch)));
        }
        lines.add(kind.withColor(SOFT));
        switch (skillInfoShown ? AbilityNode.Kind.STAT : n.kind) {
            case STAT -> {
                if (!skillInfoShown) {
                    lines.add(Component.translatable("gui.skyabyss.tree.stat_effect", Component.translatable(n.stat.nameKey()), n.amount).withColor(TEXT));
                }
            }
            case THRESHOLD -> {
                int now = jp.revati.skyabyss.rpg.Growth.get(minecraft.player).stat(n.stat);
                boolean on = d.hasNode(tree, n.id) && now >= n.at;
                lines.add(Component.translatable("gui.skyabyss.tree.threshold_line", Component.translatable(n.stat.nameKey()), n.at, now).withColor(on ? PALE : SOFT));
                lines.add(Component.translatable(n.effectKey(key)).withColor(TEXT));
                if (on) {
                    lines.add(Component.translatable("gui.skyabyss.tree.threshold_on").withColor(PALE));
                }
            }
            case RUNE -> {
                lines.add(Component.translatable("gui.skyabyss.tree.rune_line", Component.translatable(n.skill.nameKey())).withColor(SOFT));
                lines.add(Component.translatable(n.effectKey(key)).withColor(TEXT));
            }
            case MUTATION -> {
                lines.add(Component.translatable("gui.skyabyss.tree.mutation_line", Component.translatable(n.skill.nameKey())).withColor(SOFT));
                lines.add(Component.translatable(n.effectKey(key)).withColor(TEXT));
                Mutation m = n.mutation;
                if (d.mutations.containsKey(n.skill.id)) {
                    lines.add(Component.translatable("gui.skyabyss.tree.mutation_paid").withColor(PALE));
                } else {
                    int core = jp.revati.skyabyss.rpg.skill.SkillExtOps.count(minecraft.player, m.coreItem());
                    int shard = jp.revati.skyabyss.rpg.skill.SkillExtOps.count(minecraft.player, m.shardItem());
                    boolean enough = core >= Mutation.CORES && shard >= Mutation.SHARDS;
                    lines.add(Component.translatable("gui.skyabyss.tree.material", m.coreItem().getDescription(), Math.min(core, 99), Mutation.CORES,
                            m.shardItem().getDescription(), Math.min(shard, 99), Mutation.SHARDS).withColor(enough ? PALE : DENY));
                }
            }
            case RANK -> {
                lines.add(Component.translatable("gui.skyabyss.tree.rank_line", Component.translatable(n.skill.nameKey())).withColor(SOFT));
                int next = Math.min(n.skill.maxRank, Math.max(1, d.rank(n.skill)) + (d.hasNode(tree, n.id) ? 0 : 1));
                lines.add(Component.translatable(n.skill.effectKey(), SkillRules.describeArgs(n.skill, next)).withColor(TEXT));
            }
            case ACTIVE, PASSIVE, KEYSTONE -> lines.add(Component.translatable(n.skill.effectKey(), SkillRules.describeArgs(n.skill, Math.max(1, d.rank(n.skill)))).withColor(TEXT));
            default -> lines.add(Component.translatable(n.effectKey(key)).withColor(TEXT));
        }
        if (n.skill != null && n == t.skillNode(n.skill)) {
            // 付けているアスペクトがこの技を変えていれば、その名前 (docs/design/58)
            for (jp.revati.skyabyss.rpg.aspect.Aspects.Worn w : jp.revati.skyabyss.rpg.aspect.Aspects.worn(net.minecraft.client.Minecraft.getInstance().player)) {
                if (w.aspect().skill() == n.skill) {
                    lines.add(Component.translatable(w.aspect().nameKey()).append(" ").append(Component.translatable("aspect.skyabyss.level", w.level())).withColor(PALE));
                }
            }
            if (!skillInfoShown) {
                changeLines(lines, n.skill);
            }
        }
        // 前のノードは木の線で見えるので、札には書かない (2026-11-10)
        if (!n.blocks.isEmpty()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < n.blocks.size(); i++) {
                AbilityNode e = t.node(n.blocks.get(i));
                if (i > 0) {
                    names.append(Component.literal("・"));
                }
                names.append(e == null ? Component.literal(n.blocks.get(i)) : Component.translatable(e.nameKey(key)));
            }
            lines.add(Component.translatable("gui.skyabyss.tree.excludes", names).withColor(DENY));
        }
        if (n.archPoints > 0) {
            int have = d.archPoints(tree, n.arch);
            lines.add(Component.translatable("gui.skyabyss.tree.arch_points", n.archPoints, have).withColor(have >= n.archPoints ? SOFT : DENY));
        }
        SkillData.TakeResult tr = d.hasNode(tree, n.id) ? SkillData.TakeResult.TAKEN : d.canTake(tree, n, level);
        switch (tr) {
            case TAKEN -> lines.add(Component.translatable("gui.skyabyss.tree.taken_line").withColor(PALE));
            case OK -> lines.add(Component.translatable("gui.skyabyss.tree.can_take").withColor(PALE));
            case ACTIVES_FULL -> lines.add(Component.translatable("gui.skyabyss.tree.actives_full", SkillRules.BASE_SLOTS).withColor(DENY));
            case NO_POINTS -> lines.add(Component.translatable("gui.skyabyss.skill.need_points").withColor(DENY));
            case RANK_MAX -> lines.add(Component.translatable("message.skyabyss.skill.learn_failed.max_rank").withColor(DENY));
            case NOT_MASTERED -> lines.add(Component.translatable("message.skyabyss.skill.node_failed.not_mastered").withColor(DENY));
            case REBIRTH -> lines.add(Component.translatable("gui.skyabyss.ext.need_rebirth", n.rebirthAt).withColor(DENY));
            // 前提が足りないときは、上の「線でつながった上の節」の行が赤くなる (同じことを二度書かない)
            case EXCLUDED -> {
                AbilityNode other = null;
                if (n.kind == AbilityNode.Kind.RUNE) {
                    for (AbilityNode o : t.runesOf(n.skill)) {
                        if (o != n && d.hasNode(tree, o.id)) {
                            other = o;
                        }
                    }
                }
                lines.add(other != null ? Component.translatable("gui.skyabyss.tree.rune_taken", Component.translatable(other.nameKey(key))).withColor(DENY)
                        : Component.translatable("message.skyabyss.skill.node_failed.excluded").withColor(DENY));
            }
            case ARCH_POINTS -> lines.add(Component.translatable("gui.skyabyss.tree.need_arch", Component.translatable(t.archKey(n.arch)), n.archPoints - d.archPoints(tree, n.arch)).withColor(DENY));
            default -> {
            }
        }
        return lines;
    }

    @Override
    public int[] textBounds() {
        // 文の範囲は画面の座標 (切りは画面の座標で掛かる)。つまみも板の中
        return fit.screenRect(left, top, W, H);
    }
}
