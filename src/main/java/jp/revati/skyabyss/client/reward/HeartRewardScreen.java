package jp.revati.skyabyss.client.reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.client.ui.TextStyle;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.loot.reward.HeartRewardNet;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.network.PacketDistributor;

// 心臓の遺物の選択画面 (docs/design/30_heart_reward_ui.md)。心臓を止めたあと一度だけ選べる専用の逸品を、大きなカードで見せて選ばせる。
// 暗い石板に画素の枠 (docs/design/18 の見た目)。巨神ごとに題字と差し色が違う。
//   幕 (REVEAL)   : 暗転 → 題字が浮かぶ → カードが一枚ずつ上がる。このあいだは押せない (場面の続きで押してしまわないように)
//   選ぶ (CHOOSE) : ← → / A D / Tab / マウスで見て、Enter / Space / クリックで決める (数字キーで直接も)。Esc は「あとで？」
//   確かめ (CONFIRM): 「受け取りますか？」。受け取る / 選びなおす。入った直後の 0.5 秒は押しても効かない。Esc で戻る
//   待ち (WAIT)   : サーバーの返事待ち。サーバーが断ったら (すでに受け取り済みなど) 理由を出して閉じる
//   完了 (DONE)   : 選んだ品が光って「手にした」。そのあと閉じる
//   あとで (LATER): Esc。「旅嚢の『遺物』からいつでも」。あとで選ぶ / いま選ぶ (初めの選びは「いま選ぶ」)
// 選んだ結果は必ずサーバーが決める (HeartReward.claim)。この画面は見せて、押したものを伝えるだけ。
public class HeartRewardScreen extends Screen {
    private enum Phase {
        REVEAL, CHOOSE, CONFIRM, WAIT, DONE, LATER
    }

    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;
    private static final int GOOD = 0x7BD66B;
    private static final int BAD = 0xE0505A;
    private static final int GAP = 8;
    // 幕: 題字が浮かぶ時刻、カードが上がり始める時刻・間隔・長さ (tick)
    private static final int T_TITLE = 8;
    private static final int T_CARD0 = 26;
    private static final int T_CARD_STEP = 9;
    private static final int T_CARD_LEN = 16;
    // 入った直後に押しても効かない長さ
    private static final int LOCK_TICKS = 10;
    private static final int DONE_CLOSE = 70;

    private final String titan;
    private final List<ItemStack> stacks;
    private final int ownedMask;
    private final boolean auto;
    private final int accent;

    private Phase phase = Phase.REVEAL;
    private int age;
    private int phaseAge;
    private int focus;
    private int selected = -1;
    // 確かめ・あとでの窓の中のつまみ (0 = 左、1 = 右)
    private int dialogFocus;
    private Component error;
    private int doneItem = -1;

    // カードごとの中身 (開いたとき一度だけ作る)
    private List<CardInfo> cards = List.of();
    private final boolean[] revealSounded = new boolean[8];

    // 一枚のカードに出す文字と数値
    private record CardInfo(ItemStack stack, Component name, int tierColor, Component tierLine, Component kind, List<FormattedCharSequence> effect,
            List<FormattedCharSequence> story, int required, boolean levelOk, boolean owned, Component equippedLine, List<StatRow> stats) {
    }

    private record StatRow(Component label, double now, double next, boolean percent) {
        double diff() {
            return next - now;
        }
    }

    // 画面の表示用: 巨神ごとの差し色
    static int accentOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> 0x4FC3C8;
            case Titans.THUNDER_WING -> 0xE6C84E;
            case Titans.EMBER -> 0xE8743B;
            default -> 0x8FB8D0;
        };
    }

    public HeartRewardScreen(String titan, List<ItemStack> stacks, int ownedMask, boolean auto) {
        super(Component.translatable("skyabyss_reward.title." + titan));
        this.titan = titan;
        this.stacks = List.copyOf(stacks);
        this.ownedMask = ownedMask;
        this.auto = auto;
        this.accent = accentOf(titan);
    }

    @Override
    protected void init() {
        super.init();
        showCursor();
        if (cards.isEmpty()) {
            cards = buildCards();
            // 最初に選べるカードへ
            focus = firstSelectable();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // 一人用でも止めない (止めるとサーバーが Claim を処理できない)
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景は render の中で描く (ぼかしはかけない)
    }

    // ---------------------------------------------------------------- カードの中身
    private List<CardInfo> buildCards() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        List<CardInfo> out = new ArrayList<>();
        int width = Math.max(90, cardWidth() - 14);
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            Item item = stack.getItem();
            GearTier tier = item instanceof ModGear g ? g.tier() : GearTier.COMMON;
            int tierColor = tier.color.getValue();
            String origin = item instanceof ModGear g ? g.dungeon() : titan;
            MutableComponent tierLine = Component.translatable(tier.translationKey()).withStyle(s -> s.withColor(tierColor))
                    .append(Component.literal(" · ").withStyle(s -> s.withColor(DIM)))
                    .append(kindOf(item).copy().withStyle(s -> s.withColor(DIM)));
            Component kind = kindOf(item);
            // 説明: 品の説明の一行と物語の一行 (lang の item.<名前空間>.<名>.desc / .story。装備のツールチップと同じ文)
            net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            String base = "item." + key.getNamespace() + "." + key.getPath();
            net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
            List<FormattedCharSequence> effect = lang.has(base + ".desc") ? font.split(Component.translatable(base + ".desc"), width) : List.of();
            List<FormattedCharSequence> story = lang.has(base + ".story") ? font.split(Component.translatable(base + ".story"), width) : List.of();
            int required = GearLevel.required(stack);
            boolean levelOk = player == null || GearLevel.meets(player, stack);
            boolean owned = (ownedMask & (1 << i)) != 0;
            Component equipped = Component.translatable("skyabyss_reward.equipped.none");
            List<StatRow> stats = List.of();
            if (player != null) {
                if (item instanceof Accessory acc) {
                    equipped = accessoryEquipped(player, acc);
                } else {
                    EquipmentSlot slot = item instanceof ArmorItem armor ? armor.getEquipmentSlot() : EquipmentSlot.MAINHAND;
                    ItemStack now = player.getItemBySlot(slot);
                    equipped = now.isEmpty() ? Component.translatable("skyabyss_reward.equipped.none") : Component.translatable("skyabyss_reward.equipped", now.getHoverName());
                    stats = statRows(stack, now, slot);
                }
            }
            out.add(new CardInfo(stack, stack.getHoverName().copy().withStyle(s -> s.withColor(tierColor)), tierColor, tierLine, kind, effect, story, required, levelOk,
                    owned, equipped, stats));
        }
        return out;
    }

    private static Component kindOf(Item item) {
        if (item instanceof Accessory acc) {
            return Component.translatable("skyabyss_reward.kind." + acc.accessoryKind().key);
        }
        return Component.translatable(item instanceof ArmorItem ? "skyabyss_reward.kind.armor" : "skyabyss_reward.kind.weapon");
    }

    private static Component accessoryEquipped(Player player, Accessory acc) {
        AccessoryData data = Accessories.get(player);
        List<Component> worn = new ArrayList<>();
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            ItemStack s = data.items.get(i);
            if (!s.isEmpty() && AccessoryData.kindOf(i) == acc.accessoryKind()) {
                worn.add(s.getHoverName());
            }
        }
        if (worn.isEmpty()) {
            return Component.translatable("skyabyss_reward.equipped.none");
        }
        MutableComponent all = Component.empty();
        for (int i = 0; i < worn.size(); i++) {
            if (i > 0) {
                all.append(Component.literal("・"));
            }
            all.append(worn.get(i));
        }
        return Component.translatable("skyabyss_reward.equipped", all);
    }

    // 数値の比べ: その枠に効く足し算の補正を、攻撃力・攻撃速度・防御・強靭・その他の順に
    private List<StatRow> statRows(ItemStack next, ItemStack now, EquipmentSlot slot) {
        Map<Holder<Attribute>, double[]> sums = new LinkedHashMap<>();
        for (Holder<Attribute> a : List.of(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
            sums.put(a, new double[2]);
        }
        collect(sums, next, slot, 1);
        collect(sums, now, slot, 0);
        // 素手の基礎値 (攻撃力 1・攻撃速度 4。手の枠だけ)
        if (slot == EquipmentSlot.MAINHAND) {
            sums.get(Attributes.ATTACK_DAMAGE)[0] += 1.0;
            sums.get(Attributes.ATTACK_DAMAGE)[1] += 1.0;
            sums.get(Attributes.ATTACK_SPEED)[0] += 4.0;
            sums.get(Attributes.ATTACK_SPEED)[1] += 4.0;
            // 間合い (素手 3.0)。補正があるときだけ行になる
            for (Map.Entry<Holder<Attribute>, double[]> e : sums.entrySet()) {
                if (e.getKey().value().getDescriptionId().endsWith("entity_interaction_range")) {
                    e.getValue()[0] += 3.0;
                    e.getValue()[1] += 3.0;
                }
            }
        }
        List<StatRow> rows = new ArrayList<>();
        for (Map.Entry<Holder<Attribute>, double[]> e : sums.entrySet()) {
            double a = e.getValue()[0];
            double b = e.getValue()[1];
            if (Math.abs(a) < 1e-6 && Math.abs(b) < 1e-6) {
                continue;
            }
            rows.add(new StatRow(statLabel(e.getKey()), a, b, false));
        }
        return rows;
    }

    private static void collect(Map<Holder<Attribute>, double[]> sums, ItemStack stack, EquipmentSlot slot, int index) {
        if (stack.isEmpty()) {
            return;
        }
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE || !entry.slot().test(slot)) {
                continue;
            }
            sums.computeIfAbsent(entry.attribute(), k -> new double[2])[index] += entry.modifier().amount();
        }
    }

    private static Component statLabel(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.ATTACK_DAMAGE)) {
            return Component.translatable("skyabyss_reward.stat.attack_damage");
        }
        if (attribute.is(Attributes.ATTACK_SPEED)) {
            return Component.translatable("skyabyss_reward.stat.attack_speed");
        }
        if (attribute.is(Attributes.ARMOR)) {
            return Component.translatable("skyabyss_reward.stat.armor");
        }
        if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
            return Component.translatable("skyabyss_reward.stat.toughness");
        }
        if (attribute.value().getDescriptionId().endsWith("entity_interaction_range")) {
            return Component.translatable("skyabyss_reward.stat.reach");
        }
        return Component.translatable(attribute.value().getDescriptionId());
    }

    // ---------------------------------------------------------------- 寸法
    private int panelW() {
        return Math.min(width - 12, 600);
    }

    private int panelX() {
        return (width - panelW()) / 2;
    }

    private int panelY() {
        return Math.max(4, Math.min(10, (height - 200) / 2));
    }

    private int panelH() {
        return height - panelY() * 2;
    }

    private int cardWidth() {
        int n = Math.max(1, stacks.size());
        return Mth.clamp((panelW() - 20 - GAP * (n - 1)) / n, 100, 172);
    }

    private int cardsLeft() {
        int n = stacks.size();
        int total = cardWidth() * n + GAP * (n - 1);
        return (width - total) / 2;
    }

    // 高さの段: 0 = 広い (題字・線・一行の下の文)、1 = ふつう (題字・文を一行)、2 = 狭い (題字だけ)
    private int mode() {
        return height >= 300 ? 0 : height >= 215 ? 1 : 2;
    }

    private int cardTop() {
        return panelY() + switch (mode()) {
            case 0 -> 68;
            case 1 -> 50;
            default -> 36;
        };
    }

    private int cardBottom() {
        return panelY() + panelH() - (mode() == 0 ? 28 : 24);
    }

    private int cardX(int i) {
        return cardsLeft() + i * (cardWidth() + GAP);
    }

    private int cardIn(int i) {
        // その枚の上がりの進み 0..1
        float t = (age - T_CARD0 - i * T_CARD_STEP) / (float) T_CARD_LEN;
        return Math.round(Mth.clamp(t, 0f, 1f) * 100);
    }

    private boolean selectable(int i) {
        if (i < 0 || i >= cards.size()) {
            return false;
        }
        if (!cards.get(i).owned()) {
            return true;
        }
        // 全部持っているときだけ、持っている品も選べる (サーバーの決まりと同じ)
        return cards.stream().allMatch(CardInfo::owned);
    }

    private int firstSelectable() {
        for (int i = 0; i < cards.size(); i++) {
            if (selectable(i)) {
                return i;
            }
        }
        return 0;
    }

    private int revealEnd() {
        return T_CARD0 + (stacks.size() - 1) * T_CARD_STEP + T_CARD_LEN + 4;
    }

    // ---------------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        age++;
        phaseAge++;
        // 開いている間は守る (サーバーが 2 秒ぶん延ばす。傷つかず、敵は狙わない)
        if (age % 10 == 1) {
            PacketDistributor.sendToServer(new HeartRewardNet.Hold());
        }
        showCursor();
        if (phase == Phase.REVEAL) {
            for (int i = 0; i < stacks.size() && i < revealSounded.length; i++) {
                if (!revealSounded[i] && age >= T_CARD0 + i * T_CARD_STEP) {
                    revealSounded[i] = true;
                    ui(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f + 0.18f * i, 0.35f);
                }
            }
            if (age == 2) {
                ui(SoundEvents.AMBIENT_CAVE.value(), 0.7f, 0.25f);
            }
            if (age >= revealEnd()) {
                setPhase(Phase.CHOOSE);
            }
        } else if (phase == Phase.DONE && phaseAge >= DONE_CLOSE) {
            onClose();
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseAge = 0;
        error = null;
    }

    private void ui(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- サーバーの返事
    public void onResult(int code, String item) {
        if (phase != Phase.WAIT) {
            return;
        }
        if (code == 0) {
            doneItem = selected;
            setPhase(Phase.DONE);
            ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);
            ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.4f);
            return;
        }
        String key = switch (code) {
            case 1 -> "skyabyss_reward.err.not_pending";
            case 3 -> "skyabyss_reward.err.owned";
            default -> "skyabyss_reward.err.bad";
        };
        if (code == 1) {
            // すでに受け取っている: 閉じる
            Minecraft.getInstance().setScreen(null);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), true);
            }
            return;
        }
        selected = -1;
        setPhase(Phase.CHOOSE);
        error = Component.translatable(key);
    }

    // ---------------------------------------------------------------- 入力
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (phase) {
            case REVEAL, WAIT, DONE -> {
                // 幕と待ちの間は何も効かない (Esc で消えて取りこぼさないように)。完了は 0.7 秒たてば押して閉じられる
                if (phase == Phase.DONE && phaseAge > 14 && isConfirmKey(keyCode)) {
                    onClose();
                }
                return true;
            }
            case CHOOSE -> {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    ui(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.6f);
                    dialogFocus = 1;
                    setPhase(Phase.LATER);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
                    moveFocus(-1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
                    moveFocus(1);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_TAB) {
                    moveFocus(hasShiftDown() ? -1 : 1);
                    return true;
                }
                if (keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + cards.size()) {
                    int i = keyCode - GLFW.GLFW_KEY_1;
                    if (i != focus) {
                        focus = i;
                        ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
                    }
                    return true;
                }
                if (isConfirmKey(keyCode)) {
                    choose(focus);
                    return true;
                }
                return true;
            }
            case CONFIRM -> {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    back();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D
                        || keyCode == GLFW.GLFW_KEY_TAB) {
                    dialogFocus = 1 - dialogFocus;
                    ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
                    return true;
                }
                if (isConfirmKey(keyCode) && phaseAge >= LOCK_TICKS) {
                    if (dialogFocus == 0) {
                        claim();
                    } else {
                        back();
                    }
                }
                return true;
            }
            case LATER -> {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    // もう一度 Esc: 「いま選ぶ」へ戻る (うっかり閉じない)
                    back();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D
                        || keyCode == GLFW.GLFW_KEY_TAB) {
                    dialogFocus = 1 - dialogFocus;
                    ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
                    return true;
                }
                if (isConfirmKey(keyCode) && phaseAge >= LOCK_TICKS) {
                    if (dialogFocus == 0) {
                        later();
                    } else {
                        back();
                    }
                }
                return true;
            }
        }
        return true;
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    private void moveFocus(int dir) {
        int n = cards.size();
        for (int k = 1; k <= n; k++) {
            int i = Math.floorMod(focus + dir * k, n);
            if (selectable(i) || k == n) {
                if (i != focus) {
                    focus = i;
                    ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f);
                }
                return;
            }
        }
    }

    private void choose(int i) {
        if (!selectable(i)) {
            error = Component.translatable("skyabyss_reward.err.owned");
            ui(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.5f);
            return;
        }
        selected = i;
        focus = i;
        dialogFocus = 0;
        ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.45f);
        setPhase(Phase.CONFIRM);
    }

    private void back() {
        ui(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
        setPhase(Phase.CHOOSE);
    }

    private void claim() {
        if (selected < 0 || selected >= stacks.size()) {
            return;
        }
        Item item = stacks.get(selected).getItem();
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        PacketDistributor.sendToServer(new HeartRewardNet.Claim(titan, id));
        setPhase(Phase.WAIT);
    }

    private void later() {
        PacketDistributor.sendToServer(new HeartRewardNet.Later(titan));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return true;
        }
        switch (phase) {
            case CHOOSE -> {
                int i = cardAt(mx, my);
                if (i >= 0) {
                    choose(i);
                }
            }
            case CONFIRM, LATER -> {
                if (phaseAge >= LOCK_TICKS) {
                    int b = dialogButtonAt(mx, my);
                    if (b >= 0) {
                        dialogFocus = b;
                        if (phase == Phase.CONFIRM) {
                            if (b == 0) {
                                claim();
                            } else {
                                back();
                            }
                        } else if (b == 0) {
                            later();
                        } else {
                            back();
                        }
                    }
                }
            }
            case DONE -> {
                if (phaseAge > 14) {
                    onClose();
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (phase == Phase.CHOOSE) {
            int i = cardAt(mx, my);
            if (i >= 0 && i != focus && selectable(i)) {
                focus = i;
            }
        } else if (phase == Phase.CONFIRM || phase == Phase.LATER) {
            int b = dialogButtonAt(mx, my);
            if (b >= 0) {
                dialogFocus = b;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (phase == Phase.CHOOSE && sy != 0) {
            moveFocus(sy > 0 ? -1 : 1);
        }
        return true;
    }

    private int cardAt(double mx, double my) {
        if (my < cardTop() || my > cardBottom()) {
            return -1;
        }
        for (int i = 0; i < cards.size(); i++) {
            if (mx >= cardX(i) && mx < cardX(i) + cardWidth()) {
                return i;
            }
        }
        return -1;
    }

    // 窓の中のつまみ (0 = 左、1 = 右、-1 = なし)
    private int dialogButtonAt(double mx, double my) {
        int[] r = dialogRect();
        int by = r[1] + r[3] - 26;
        int bw = 96;
        int total = bw * 2 + 14;
        int bx = r[0] + (r[2] - total) / 2;
        if (my < by || my > by + 18) {
            return -1;
        }
        if (mx >= bx && mx < bx + bw) {
            return 0;
        }
        if (mx >= bx + bw + 14 && mx < bx + bw * 2 + 14) {
            return 1;
        }
        return -1;
    }

    // 窓の枠: x, y, w, h
    private int[] dialogRect() {
        int w = Math.min(380, panelW() - 20);
        int h = 96;
        return new int[] {(width - w) / 2, (height - h) / 2, w, h};
    }

    // 台本 (ShotRunner の heartreward) から: 見せ場まで進める。開発用。幕を飛ばし、選ぶ・確かめ・あとで・完了の絵を撮れるようにする
    public void devPhase(String what, int index) {
        age = Math.max(age, revealEnd() + 1);
        switch (what) {
            case "focus" -> {
                setPhase(Phase.CHOOSE);
                focus = Mth.clamp(index, 0, cards.size() - 1);
            }
            case "confirm" -> {
                selected = Mth.clamp(index, 0, cards.size() - 1);
                focus = selected;
                dialogFocus = 0;
                setPhase(Phase.CONFIRM);
                phaseAge = LOCK_TICKS + 1;
            }
            case "later" -> {
                dialogFocus = 1;
                setPhase(Phase.LATER);
                phaseAge = LOCK_TICKS + 1;
            }
            case "done" -> {
                selected = Mth.clamp(index, 0, cards.size() - 1);
                doneItem = selected;
                setPhase(Phase.DONE);
                phaseAge = 30;
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 描く
    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float ease(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    // 画素の枠: 外の黒、差し色の線、内の暗い面、上と左の明るい線、四隅の金の点
    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill, float a, boolean studs) {
        g.fill(x, y, x + w, y + h, argb(a, 0x03050A));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(a, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(a, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(a * 0.10f, 0xFFFFFF));
        g.fill(x + 2, y + 2, x + 3, y + h - 2, argb(a * 0.06f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(a * 0.35f, 0x000000));
        if (studs) {
            int s = 3;
            for (int[] c : new int[][] {{x - 1, y - 1}, {x + w - s + 1, y - 1}, {x - 1, y + h - s + 1}, {x + w - s + 1, y + h - s + 1}}) {
                g.fill(c[0], c[1], c[0] + s, c[1] + s, argb(a, GOLD));
                g.fill(c[0] + 1, c[1] + 1, c[0] + s - 1, c[1] + s - 1, argb(a, 0x8A6A22));
            }
        }
    }

    private double lastMx = -1;
    private double lastMy = -1;

    // 画面が開いている間は、いつもマウスの矢印を出す (ほかの画面や場面のあとで、マウスが捕まったまま・隠れたままになることがあった)
    private void showCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
        long window = mc.getWindow().getWindow();
        if (org.lwjgl.glfw.GLFW.glfwGetInputMode(window, org.lwjgl.glfw.GLFW.GLFW_CURSOR) != org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(window, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = age + partialTick;
        // 矢印の下のカードを毎枠調べる (開いた時に矢印が動かないままだと mouseMoved が来ず、強調が付かなかった)
        if (mouseX != lastMx || mouseY != lastMy) {
            lastMx = mouseX;
            lastMy = mouseY;
            mouseMoved(mouseX, mouseY);
        }
        float dim = ease(t / 10f);
        // 暗転 (全面)。ぼかさず、黒に少し差し色を混ぜる
        g.fill(0, 0, width, height, argb(0.94f * dim, BG));
        g.fillGradient(0, 0, width, height / 2, argb(0.22f * dim, accent), argb(0f, accent));
        // 板
        int px = panelX();
        int py = panelY();
        frame(g, px, py, panelW(), panelH(), darker(accent, 0.55f), PANEL, dim, true);
        renderHeader(g, t);
        for (int i = 0; i < cards.size(); i++) {
            renderCard(g, i, t, mouseX, mouseY);
        }
        renderFooter(g, t);
        switch (phase) {
            case CONFIRM -> renderDialog(g, t, true);
            case LATER -> renderDialog(g, t, false);
            case WAIT -> {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                renderWait(g, t);
                g.pose().popPose();
            }
            case DONE -> {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400);
                renderDone(g, t);
                g.pose().popPose();
            }
            default -> {
            }
        }
    }

    private static int darker(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 0xFF) * k);
        int gr = (int) (((rgb >> 8) & 0xFF) * k);
        int b = (int) ((rgb & 0xFF) * k);
        return (r << 16) | (gr << 8) | b;
    }

    private void renderHeader(GuiGraphics g, float t) {
        float a = ease((t - T_TITLE) / 14f);
        if (a <= 0f) {
            return;
        }
        int cx = width / 2;
        int y = panelY() + 8;
        int mode = mode();
        // 題字: 光のにじみは浮かんだあと少し強く、静まる
        float glow = 0.5f + 0.5f * Mth.sin(t * 0.05f);
        TextStyle.TITLE.drawGlowCentered(g, font, title, cx, y + (mode == 0 ? 2 : 0), a, 0.55f * a + 0.25f * glow * a, 1.4f, mode == 0 ? 1.0f : mode == 1 ? 0.85f : 0.7f);
        if (mode <= 1) {
            // 題字の下の線: 中央から広がる
            int ly0 = y + (mode == 0 ? 28 : 22);
            int half = (int) (Math.min(panelW() - 60, 260) / 2 * ease((t - T_TITLE - 4) / 16f));
            g.fill(cx - half, ly0, cx + half, ly0 + 1, argb(a * 0.8f, accent));
            g.fill(cx - 2, ly0 - 1, cx + 2, ly0 + 2, argb(a, GOLD));
            Component sub = Component.translatable("skyabyss_reward.sub." + titan);
            int ly = ly0 + 7;
            int lines = 0;
            for (FormattedCharSequence line : font.split(sub, panelW() - 40)) {
                TextStyle.BODY.drawCentered(g, font, line, cx, ly, a * 0.85f, 0xC8D0D8);
                ly += 11;
                if (++lines >= (mode == 0 ? 2 : 1)) {
                    break;
                }
            }
        }
    }

    private void renderCard(GuiGraphics g, int i, float t, int mouseX, int mouseY) {
        int in = cardIn(i);
        if (in <= 0) {
            return;
        }
        CardInfo c = cards.get(i);
        float a = ease(in / 100f);
        int w = cardWidth();
        int x = cardX(i);
        int rise = Math.round((1f - a) * 18f);
        boolean hot = (phase == Phase.CHOOSE && i == focus) || (phase != Phase.CHOOSE && phase != Phase.REVEAL && i == selected);
        boolean dimmed = phase == Phase.CONFIRM || phase == Phase.LATER || phase == Phase.WAIT;
        boolean sel = i == selected && phase != Phase.CHOOSE && phase != Phase.REVEAL;
        int lift = hot ? 3 : 0;
        int y = cardTop() + rise - lift;
        int h = cardBottom() - cardTop() + lift;
        boolean ok = selectable(i);
        float alpha = a * (ok ? 1f : 0.55f) * (dimmed && !sel ? 0.55f : 1f);
        int line = sel ? GOLD : hot ? accent : 0x2C323E;
        frame(g, x, y, w, h, line, hot || sel ? 0x1D2230 : CARD, alpha, hot || sel);
        // 浮かぶ光 (選んでいる枚の後ろ)
        if (hot || sel) {
            float pulse = 0.5f + 0.5f * Mth.sin(t * 0.12f);
            g.fillGradient(x + 3, y + 3, x + w - 3, y + 62, argb(alpha * (0.16f + 0.10f * pulse), line), argb(0f, line));
        }
        g.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
        int cx = x + w / 2;
        int cy = y + (h < 150 ? 6 : 8);
        boolean compact = h < 150;
        int iconScale = compact ? 2 : 3;
        int iconPx = 16 * iconScale;
        // 大きな絵 (光の台つき)
        int iconX = cx - iconPx / 2;
        int iconY = cy + (compact ? 0 : 2);
        int m = compact ? 3 : 5;
        g.fill(iconX - m, iconY - m, iconX + iconPx + m, iconY + iconPx + m, argb(alpha * 0.55f, 0x0A0C12));
        g.renderOutline(iconX - m, iconY - m, iconPx + 2 * m, iconPx + 2 * m, argb(alpha * 0.9f, darker(c.tierColor(), 0.7f)));
        g.fill(iconX - m, iconY + iconPx + m - 1, iconX + iconPx + m, iconY + iconPx + m, argb(alpha, c.tierColor()));
        if (alpha > 0.45f) {
            g.pose().pushPose();
            g.pose().translate(iconX, iconY + (hot ? Mth.sin(t * 0.1f) * 1.2f : 0f), 0);
            g.pose().scale(iconScale, iconScale, 1f);
            g.renderItem(c.stack(), 0, 0);
            g.pose().popPose();
        }
        int ty = iconY + iconPx + (compact ? 7 : 10);
        // 持っている印
        if (c.owned()) {
            Component tag = Component.translatable("skyabyss_reward.tag.owned");
            int tw = font.width(tag) + 6;
            g.fill(x + w - tw - 5, y + 5, x + w - 5, y + 16, argb(alpha, 0x5A2430));
            g.drawString(font, tag, x + w - tw - 2, y + 7, argb(alpha, 0xFFB0B8), false);
        }
        // 名前 (階級の色)
        int innerW = w - 14;
        for (FormattedCharSequence line2 : jp.revati.skyabyss.client.ui.TextClip.limit(font, font.split(c.name(), innerW), innerW, 2)) {
            TextStyle.BODY.drawCentered(g, font, line2, cx, ty, alpha, 0xFFFFFF);
            ty += 11;
        }
        // 階級 · 種類 (一行)
        TextStyle.BODY.drawCentered(g, font, c.tierLine(), cx, ty, alpha * 0.95f, 0xFFFFFF);
        ty += 12;
        g.fill(x + 8, ty - 3, x + w - 8, ty - 2, argb(alpha * 0.5f, 0x3A4252));
        // 効果
        for (FormattedCharSequence line2 : c.effect()) {
            if (ty + 10 > y + h - 4) {
                break;
            }
            g.drawString(font, line2, x + 7, ty, argb(alpha, 0xC9D2DA), false);
            ty += 10;
        }
        if (!compact) {
            for (FormattedCharSequence line2 : c.story()) {
                if (ty + 10 > y + h - 4) {
                    break;
                }
                g.drawString(font, line2, x + 7, ty, argb(alpha * 0.8f, 0x8C98A4), false);
                ty += 10;
            }
        }
        ty += 3;
        // 必要レベル
        if (c.required() > 0 && ty + 10 <= y + h - 4) {
            Component req = c.levelOk() ? Component.translatable("skyabyss_reward.req", c.required())
                    : Component.translatable("skyabyss_reward.req_now", c.required(), GearLevel.level(Minecraft.getInstance().player));
            g.drawString(font, req, x + 7, ty, argb(alpha, c.levelOk() ? GOOD : BAD), false);
            ty += 11;
        }
        // いまの装備との比べ
        if (ty + 10 <= y + h - 4) {
            g.fill(x + 8, ty - 1, x + w - 8, ty, argb(alpha * 0.5f, 0x3A4252));
            ty += 3;
            for (FormattedCharSequence line2 : font.split(c.equippedLine(), innerW)) {
                g.drawString(font, line2, x + 7, ty, argb(alpha * 0.9f, TEAL), false);
                ty += 10;
                break;
            }
            if (!c.stats().isEmpty()) {
                for (StatRow row : c.stats()) {
                    if (ty + 10 > y + h - 4) {
                        break;
                    }
                    drawStat(g, x + 7, ty, innerW, row, alpha);
                    ty += 10;
                }
            }
        }
        g.disableScissor();
        // 選んでいる枚の下に番号の札
        if (cards.size() > 1 && (phase == Phase.CHOOSE || phase == Phase.REVEAL)) {
            Component n = Component.literal(String.valueOf(i + 1));
            g.drawString(font, n, x + 6, y + 6, argb(alpha * 0.7f, hot ? GOLD : DIM), false);
        }
    }

    private void drawStat(GuiGraphics g, int x, int y, int w, StatRow row, float alpha) {
        double d = row.diff();
        String sign = Math.abs(d) < 0.005 ? "＝" : d > 0 ? "▲" : "▼";
        int color = Math.abs(d) < 0.005 ? DIM : d > 0 ? GOOD : BAD;
        // 攻撃速度は大きいほうが速い (良い)。数字の向きのまま
        String text = fmt(row.now()) + " → " + fmt(row.next()) + " " + sign;
        g.drawString(font, text, x + w - font.width(text), y, argb(alpha, color), false);
        // 名前は値の左に収まる長さまで
        String label = font.plainSubstrByWidth(row.label().getString(), Math.max(10, w - font.width(text) - 4));
        g.drawString(font, label, x, y, argb(alpha, DIM), false);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private void renderFooter(GuiGraphics g, float t) {
        float a = ease((t - revealEnd() + 6) / 10f);
        int y = panelY() + panelH() - 20;
        int cx = width / 2;
        if (error != null) {
            TextStyle.BODY.drawCentered(g, font, error, cx, y, 1f, BAD);
            return;
        }
        if (a <= 0f) {
            return;
        }
        String key = switch (phase) {
            case CHOOSE -> "skyabyss_reward.keys.choose";
            case CONFIRM, LATER -> "skyabyss_reward.keys.dialog";
            default -> "";
        };
        if (!key.isEmpty()) {
            TextStyle.CAPTION.drawCentered(g, font, Component.translatable(key), cx, y, a * 0.9f, DIM);
        }
        if (phase == Phase.CHOOSE && !auto) {
            // 旅嚢から開いたとき: 何も変わらない (自動のときと同じ)
        }
    }

    private void renderDialog(GuiGraphics g, float t, boolean confirm) {
        // 絵 (アイテム) は奥行きが前に出るので、窓と暗幕はそれより手前へ
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        renderDialogBody(g, t, confirm);
        g.pose().popPose();
    }

    private void renderDialogBody(GuiGraphics g, float t, boolean confirm) {
        float a = ease(phaseAge / 5f);
        g.fill(0, 0, width, height, argb(0.55f * a, 0x000000));
        int[] r = dialogRect();
        int x = r[0];
        int y = r[1] + Math.round((1f - a) * 6f);
        frame(g, x, y, r[2], r[3], confirm ? GOLD : accent, 0x141821, a, true);
        Component head;
        Component body;
        Component left;
        Component right;
        if (confirm) {
            Component name = selected >= 0 ? cards.get(selected).name() : Component.empty();
            head = Component.translatable("skyabyss_reward.confirm.title", name);
            body = Component.translatable("skyabyss_reward.confirm.body");
            left = Component.translatable("skyabyss_reward.confirm.yes");
            right = Component.translatable("skyabyss_reward.confirm.no");
        } else {
            head = Component.translatable("skyabyss_reward.later.title");
            body = Component.translatable("skyabyss_reward.later.body");
            left = Component.translatable("skyabyss_reward.later.yes");
            right = Component.translatable("skyabyss_reward.later.no");
        }
        int cx = x + r[2] / 2;
        int ty = y + 10;
        for (FormattedCharSequence line : font.split(head, r[2] - 24)) {
            TextStyle.HEADING.drawCentered(g, font, line, cx, ty, a, 0xFFFFFF);
            ty += TextStyle.HEADING.lineHeight() - 2;
            break;
        }
        ty += 6;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.TextClip.limit(font, font.split(body, r[2] - 28), r[2] - 28, Math.max(1, (y + r[3] - 30 - ty) / 10))) {
            TextStyle.BODY.drawCentered(g, font, line, cx, ty, a * 0.85f, 0xC8D0D8);
            ty += 10;
        }
        int by = y + r[3] - 26;
        int bw = 96;
        int total = bw * 2 + 14;
        int bx = x + (r[2] - total) / 2;
        boolean locked = phaseAge < LOCK_TICKS;
        button(g, bx, by, bw, left, dialogFocus == 0, a, locked, confirm ? GOLD : accent);
        button(g, bx + bw + 14, by, bw, right, dialogFocus == 1, a, locked, 0x6A7488);
    }

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean on, float a, boolean locked, int color) {
        float k = locked ? 0.5f : 1f;
        g.fill(x, y, x + w, y + 18, argb(a, 0x03050A));
        g.fill(x + 1, y + 1, x + w - 1, y + 17, argb(a * k, on ? color : darker(color, 0.5f)));
        g.fill(x + 2, y + 2, x + w - 2, y + 16, argb(a, on ? darker(color, 0.28f) : 0x1B202B));
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 5, argb(a * k, on ? 0xFFFFFF : 0xA9AEBC), false);
    }

    private void renderWait(GuiGraphics g, float t) {
        int dots = 1 + (int) (t / 6) % 3;
        TextStyle.BODY.drawCentered(g, font, Component.literal(".".repeat(dots)), width / 2, height / 2 - 4, 1f, GOLD);
    }

    // 完了: 選んだ品が中央で大きく光る
    private void renderDone(GuiGraphics g, float t) {
        float a = ease(phaseAge / 8f);
        float out = ease((phaseAge - (DONE_CLOSE - 14)) / 14f);
        float alpha = a * (1f - out);
        g.fill(0, 0, width, height, argb(0.88f * alpha, 0x000000));
        if (doneItem < 0 || doneItem >= cards.size()) {
            return;
        }
        CardInfo c = cards.get(doneItem);
        int cx = width / 2;
        int cy = height / 2 - 14;
        // 広がる光の輪
        float burst = Mth.clamp(phaseAge / 24f, 0f, 1f);
        int rad = 20 + Math.round(burst * 46);
        for (int k = 0; k < 3; k++) {
            int rr = rad - k * 9;
            if (rr > 0) {
                g.renderOutline(cx - rr, cy - rr, rr * 2, rr * 2, argb(alpha * (1f - burst * 0.7f) * (0.5f - k * 0.14f), k == 0 ? GOLD : accent));
            }
        }
        // 粒 (決まった位置が、ゆっくり昇る)
        for (int k = 0; k < 18; k++) {
            float ang = k * 2.399f;
            float dist = 24 + (k * 7 % 40) + burst * 22;
            float rise = (phaseAge + k * 3) % 40 / 40f;
            int sx = cx + Math.round(Mth.cos(ang) * dist);
            int sy = cy + Math.round(Mth.sin(ang) * dist * 0.7f - rise * 14f);
            g.fill(sx, sy, sx + 2, sy + 2, argb(alpha * (1f - rise) * 0.9f, k % 3 == 0 ? GOLD : c.tierColor()));
        }
        if (alpha > 0.4f) {
            g.pose().pushPose();
            g.pose().translate(cx - 32, cy - 32 + Mth.sin(t * 0.1f) * 2f, 0);
            g.pose().scale(4f, 4f, 1f);
            g.renderItem(c.stack(), 0, 0);
            g.pose().popPose();
        }
        TextStyle.HEADING.drawCentered(g, font, Component.translatable("skyabyss_reward.done", c.name()), cx, cy + 44, alpha, 0xFFFFFF);
        if (phaseAge > 14) {
            TextStyle.CAPTION.drawCentered(g, font, Component.translatable("skyabyss_reward.done_affix"), cx, cy + 62, alpha * 0.9f, DIM);
        }
    }
}
