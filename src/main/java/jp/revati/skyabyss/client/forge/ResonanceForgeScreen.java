package jp.revati.skyabyss.client.forge;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.forge.ResonanceForgeMenu;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.item.extra.GearExtras;
import jp.revati.skyabyss.item.extra.GearFreedom;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.registry.ModDataComponents;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の画面。左の板に装備の枠・材料の枠・特性・持ち物、右の板に四つのつまみ (強化・粉・再鍛造・交換)。
// 再鍛造のつまみの中に、再鍛造・抽出・刻印の三頁 (2026-11-05 の粉と炉の頁 2-3)。町の鍛冶師で開いたときは粉のつまみだけ (menu.powderOnly)。
// 背景は 2 倍密度のテクスチャ (textures/gui/forge/forge.png、tools/gen/items/gen_forge_assets.py)。配置を変えたら生成元もそろえる
public class ResonanceForgeScreen extends AbstractContainerScreen<ResonanceForgeMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/forge/forge.png");
    private static final int TEX_W = 1024;
    private static final int TEX_H = 512;
    private static final int MAIN_W = 176;
    private static final int SIDE_X = 180;
    private static final int SIDE_W = 152;
    private static final int H = 166;
    // つまみは 4 つ (強化・粉・再鍛造・交換)。元の絵 (幅 48) を幅 35 に縮めて並べ、字は幅に収まるまで縮める
    private static final int TAB_W = 35;
    private static final int TAB_SRC_W = 48;
    private static final int TAB_H = 14;
    private static final int[] TAB_X = {3, 40, 77, 114};
    private static final int CARD_W = 136;
    private static final int CARD_H = 31;
    // 再鍛造の候補の札 (頁のボタンの下から)
    private static final int CARD_Y = 51;
    private static final int CARD_STEP = 31;
    private static final int EXCHANGE_Y = 62;
    private static final int EXCHANGE_STEP = 24;

    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF7A8090;
    private static final int OK = 0xFF9FE8FF;
    private static final int LACK = 0xFFD9779A;
    private static final int GOLD = 0xFFF2B53A;
    private static final int INFO_X = 72;

    private enum Tab {
        ENHANCE, POWDER, REFORGE, EXCHANGE
    }

    // 再鍛造のつまみの中の三つの頁: 再鍛造 (特性の振り直し)・抽出 (ヒロイック以上 → アスペクトの芯)・刻印 (芯 → レア・エピックの装備)
    private enum Sub {
        REFORGE, EXTRACT, IMPRINT
    }

    private static final int SUB_Y = 20;
    // 粉のつまみ: 穴の行
    private static final int SOCKET_ROW_Y = 34;
    private static final int SOCKET_ROW_STEP = 13;
    private static final int ART_Y = 76;
    private static final int POWDER_BUTTON_Y = 124;
    private static Sub lastSub = Sub.REFORGE;
    private Sub sub = lastSub;
    private final Button[] subButtons = new Button[3];
    private Button extractDestroyButton;
    private Button extractKeepButton;
    private Button imprintButton;
    private Button insertButton;
    private Button combineButton;
    private final Button[] removeButtons = new Button[GearExtras.MAX_SOCKETS];
    private boolean extractDestroyArmed;
    private boolean extractKeepArmed;

    // 最後に開いたつまみ (同じ遊びの間は覚えておく)
    private static Tab lastTab = Tab.ENHANCE;
    private Tab tab = lastTab;

    private Button enhanceButton;
    private Button dismantleButton;
    private Button trialButton;
    private Button confirmButton;
    private Button keepButton;
    private final Button[] exchangeButtons = new Button[3];
    // 交換の巨神を切り替える (岩鯨 → 潮殻 → …)
    private Button titanButton;
    private static int lastTitan;
    private int titan = lastTitan;
    // 分解・今のまま・交換は、もう一度押して確定する
    private boolean dismantleArmed;
    private boolean keepArmed;
    private int exchangeArmed = -1;
    // 選んでいる候補 (-1 = なし)
    private int selected = -1;
    private ItemStack lastGear = ItemStack.EMPTY;

    public ResonanceForgeScreen(ResonanceForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SIDE_X + SIDE_W;
        imageHeight = H;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int sx = leftPos + SIDE_X;
        enhanceButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.enhance"), b -> press(ResonanceForgeMenu.BUTTON_ENHANCE))
                .bounds(sx + 10, topPos + 60, 60, 16).build());
        dismantleButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.dismantle"), b -> {
            if (!dismantleArmed) {
                dismantleArmed = true;
            } else {
                dismantleArmed = false;
                press(ResonanceForgeMenu.BUTTON_DISMANTLE);
            }
        }).bounds(sx + 10, topPos + 138, 60, 16).build());
        trialButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.trial"), b -> press(ResonanceForgeMenu.BUTTON_TRIAL))
                .bounds(sx + 96, topPos + 35, 46, 14).build());
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.pick"), b -> {
            if (selected >= 0) {
                press(ResonanceForgeMenu.BUTTON_PICK + selected);
                selected = -1;
            }
        }).bounds(sx + 10, topPos + 147, 62, 15).build());
        keepButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.keep"), b -> {
            if (!keepArmed) {
                keepArmed = true;
            } else {
                keepArmed = false;
                selected = -1;
                press(ResonanceForgeMenu.BUTTON_KEEP);
            }
        }).bounds(sx + 76, topPos + 147, 66, 15).build());
        for (int i = 0; i < exchangeButtons.length; i++) {
            int index = i;
            exchangeButtons[i] = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.exchange_pick"), b -> {
                if (exchangeArmed != index) {
                    exchangeArmed = index;
                } else {
                    exchangeArmed = -1;
                    press(ResonanceForgeMenu.BUTTON_EXCHANGE + titan * ResonanceForgeMenu.EXCHANGE_STRIDE + index);
                }
            }).bounds(sx + 98, topPos + EXCHANGE_Y + i * EXCHANGE_STEP, 44, 16).build());
        }
        titanButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            titan = (titan + 1) % Math.max(1, TitanGearSets.exchangeable().size());
            lastTitan = titan;
            exchangeArmed = -1;
            updateButtons();
        }).bounds(sx + 10, topPos + 140, 132, 16).build());
        String[] subKeys = {"gui.skyabyss.forge.sub_reforge", "gui.skyabyss.forge.sub_extract", "gui.skyabyss.forge.sub_imprint"};
        for (int i = 0; i < subButtons.length; i++) {
            Sub target = Sub.values()[i];
            subButtons[i] = addRenderableWidget(Button.builder(Component.translatable(subKeys[i]), b -> setSub(target))
                    .bounds(sx + 8 + i * 46, topPos + SUB_Y, 44, 14).build());
        }
        extractDestroyButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.extract_destroy"), b -> {
            if (!extractDestroyArmed) {
                extractDestroyArmed = true;
                extractKeepArmed = false;
            } else {
                extractDestroyArmed = false;
                press(ResonanceForgeMenu.BUTTON_EXTRACT_DESTROY);
            }
        }).bounds(sx + 10, topPos + 122, 60, 16).build());
        extractKeepButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.extract_keep"), b -> {
            if (!extractKeepArmed) {
                extractKeepArmed = true;
                extractDestroyArmed = false;
            } else {
                extractKeepArmed = false;
                press(ResonanceForgeMenu.BUTTON_EXTRACT_KEEP);
            }
        }).bounds(sx + 76, topPos + 122, 66, 16).build());
        imprintButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.imprint"), b -> press(ResonanceForgeMenu.BUTTON_IMPRINT))
                .bounds(sx + 10, topPos + 138, 132, 16).build());
        insertButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.insert"), b -> press(ResonanceForgeMenu.BUTTON_INSERT))
                .bounds(sx + 10, topPos + POWDER_BUTTON_Y, 64, 16).build());
        combineButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.combine"), b -> press(ResonanceForgeMenu.BUTTON_COMBINE))
                .bounds(sx + 78, topPos + POWDER_BUTTON_Y, 64, 16).build());
        for (int i = 0; i < removeButtons.length; i++) {
            int index = i;
            removeButtons[i] = addRenderableWidget(Button.builder(Component.literal("×"), b -> press(ResonanceForgeMenu.BUTTON_REMOVE + index))
                    .bounds(sx + 130, topPos + SOCKET_ROW_Y - 2 + i * SOCKET_ROW_STEP, 12, 12)
                    .tooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.remove_hint"))).build());
        }
        updateButtons();
    }

    // 町の鍛冶師で開いたときは、粉のつまみだけ
    private Tab shownTab() {
        return menu.powderOnly() ? Tab.POWDER : tab;
    }

    private boolean tabShown(Tab t) {
        return !menu.powderOnly() || t == Tab.POWDER;
    }

    private void setSub(Sub next) {
        sub = next;
        lastSub = next;
        extractDestroyArmed = false;
        extractKeepArmed = false;
        keepArmed = false;
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void setTab(Tab next) {
        tab = next;
        lastTab = next;
        dismantleArmed = false;
        keepArmed = false;
        exchangeArmed = -1;
        extractDestroyArmed = false;
        extractKeepArmed = false;
        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack gear = menu.gear();
        if (!ItemStack.isSameItemSameComponents(gear, lastGear)) {
            // 装備が替わった・候補が増えた・確定した: 二度押しの構えと、範囲外の選択を解く
            dismantleArmed = false;
            keepArmed = false;
            extractDestroyArmed = false;
            extractKeepArmed = false;
            if (!ItemStack.isSameItem(gear, lastGear) || selected >= AffixRules.offers(gear).size()) {
                selected = -1;
            }
            lastGear = gear.copy();
        }
        updateButtons();
    }

    private void updateButtons() {
        if (enhanceButton == null || minecraft == null || minecraft.player == null) {
            return;
        }
        ItemStack gear = menu.gear();
        Tab now = shownTab();
        boolean enhanceTab = now == Tab.ENHANCE;
        boolean reforgeTab = now == Tab.REFORGE;
        boolean exchangeTab = now == Tab.EXCHANGE;

        enhanceButton.visible = enhanceTab;
        dismantleButton.visible = enhanceTab;
        enhanceButton.active = ForgeRules.canEnhance(gear)
                && ForgeRules.affordable(minecraft.player, ForgeRules.enhanceCost(ModGear.enhance(gear)), ForgeRules.shardItem(gear));
        dismantleButton.active = !gear.isEmpty();
        dismantleButton.setMessage(Component.translatable(dismantleArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.dismantle"));

        for (int i = 0; i < subButtons.length; i++) {
            subButtons[i].visible = reforgeTab;
            subButtons[i].active = Sub.values()[i] != sub;
        }
        boolean eligible = AffixRules.eligible(gear);
        boolean reforgePage = reforgeTab && sub == Sub.REFORGE;
        List<List<Affix>> offers = AffixRules.offers(gear);
        trialButton.visible = reforgePage && eligible;
        confirmButton.visible = reforgePage && eligible;
        keepButton.visible = reforgePage && eligible;
        trialButton.active = AffixRules.canTrial(gear) && ForgeRules.affordable(minecraft.player, ForgeRules.trialCost());
        trialButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.trial_hint", AffixRules.TRIAL_CRYSTALS, AffixRules.MAX_TRIALS)));
        confirmButton.active = selected >= 0 && selected < offers.size();
        keepButton.active = !offers.isEmpty();
        keepButton.setMessage(Component.translatable(keepArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.keep"));
        keepButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.keep_hint")));

        if (titan >= TitanGearSets.exchangeable().size()) {
            titan = 0;
        }
        List<Item> exclusives = exclusives();
        boolean ready = menu.exchangeState(titan) == ForgeRules.ExchangeState.READY;
        boolean affordable = ForgeRules.exchangeAffordable(minecraft.player, set().titan);
        titanButton.visible = exchangeTab && TitanGearSets.exchangeable().size() > 1;
        titanButton.setMessage(Component.translatable("gui.skyabyss.forge.exchange_titan", Component.translatable(set().originKey())));
        titanButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.exchange_titan_hint")));
        for (int i = 0; i < exchangeButtons.length; i++) {
            Button b = exchangeButtons[i];
            boolean owned = menu.ownsExclusive(titan, i);
            b.visible = exchangeTab && i < exclusives.size() && exclusives.get(i) != null && !owned;
            b.active = ready && affordable;
            b.setMessage(Component.translatable(exchangeArmed == i ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.exchange_pick"));
        }

        updateInscribeButtons(gear, reforgeTab);
        updatePowderButtons(gear, now == Tab.POWDER);

        // 分解で得られるものは、ボタンに重ねると出る (右の板にも書く)
        dismantleButton.setTooltip(gear.isEmpty() ? null : Tooltip.create(Component.translatable("gui.skyabyss.forge.dismantle_hint")));
    }

    // 抽出と刻印の頁のボタン (見えるか・押せるか)。押せるかの検めはサーバーもやる
    private void updateInscribeButtons(ItemStack gear, boolean reforgeTab) {
        net.minecraft.world.entity.player.Player me = minecraft.player;
        ItemStack mat = menu.material();
        extractDestroyButton.visible = reforgeTab && sub == Sub.EXTRACT;
        extractKeepButton.visible = reforgeTab && sub == Sub.EXTRACT;
        extractDestroyButton.active = ForgeRules.canExtract(me, gear, true);
        extractKeepButton.active = ForgeRules.canExtract(me, gear, false);
        extractDestroyButton.setMessage(Component.translatable(extractDestroyArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.extract_destroy"));
        extractKeepButton.setMessage(Component.translatable(extractKeepArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.extract_keep"));
        extractDestroyButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.extract_destroy_hint")));
        extractKeepButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.extract_keep_hint")));
        imprintButton.visible = reforgeTab && sub == Sub.IMPRINT;
        imprintButton.active = ForgeRules.canImprint(me, gear, mat);
    }

    // 粉のつまみのボタン
    private void updatePowderButtons(ItemStack gear, boolean on) {
        net.minecraft.world.entity.player.Player me = minecraft.player;
        ItemStack mat = menu.material();
        insertButton.visible = on;
        insertButton.active = ForgeRules.canInsert(gear, mat);
        combineButton.visible = on;
        combineButton.active = ForgeRules.canCombine(mat);
        combineButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.combine_hint", jp.revati.skyabyss.item.extra.Powder.COMBINE)));
        int gems = GearFreedom.extras(gear).gems().size();
        for (int i = 0; i < removeButtons.length; i++) {
            removeButtons[i].visible = on && i < gems;
            removeButtons[i].active = ForgeRules.canRemoveGem(me, gear, i);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // つまみ
        for (Tab t : Tab.values()) {
            if (!tabShown(t)) {
                continue;
            }
            int x = leftPos + SIDE_X + TAB_X[t.ordinal()];
            int y = topPos + 4;
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H) {
                if (shownTab() != t) {
                    setTab(t);
                    playClick();
                }
                return true;
            }
        }
        // 候補の札
        if (shownTab() == Tab.REFORGE && sub == Sub.REFORGE) {
            int count = AffixRules.offers(menu.gear()).size();
            for (int i = 0; i < count; i++) {
                int x = leftPos + SIDE_X + 8;
                int y = topPos + CARD_Y + i * CARD_STEP;
                if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                    selected = selected == i ? -1 : i;
                    keepArmed = false;
                    playClick();
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // 交換の品にカーソルを合わせると、その品の説明
        if (shownTab() == Tab.EXCHANGE) {
            List<Item> exclusives = exclusives();
            for (int i = 0; i < exclusives.size(); i++) {
                int x = leftPos + SIDE_X + 10;
                int y = topPos + EXCHANGE_Y + i * EXCHANGE_STEP;
                if (exclusives.get(i) != null && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    g.renderTooltip(font, new ItemStack(exclusives.get(i)), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, MAIN_W, H, 0, 0, MAIN_W * 2, H * 2, TEX_W, TEX_H);
        g.blit(TEXTURE, x + SIDE_X, y, SIDE_W, H, SIDE_X * 2, 0, SIDE_W * 2, H * 2, TEX_W, TEX_H);
        for (Tab t : Tab.values()) {
            if (!tabShown(t)) {
                continue;
            }
            int tx = x + SIDE_X + TAB_X[t.ordinal()];
            int ty = y + 4;
            boolean hover = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H;
            int state = t == shownTab() ? 2 : hover ? 1 : 0;
            g.blit(TEXTURE, tx, ty, TAB_W, TAB_H, state * 100, 352, TAB_SRC_W * 2, TAB_H * 2, TEX_W, TEX_H);
        }
        // 材料の枠の縁 (絵に焼き込まれていないので描く)
        int mx = x + ResonanceForgeMenu.MATERIAL_X - 1;
        int my = y + ResonanceForgeMenu.MATERIAL_Y - 1;
        g.fill(mx - 1, my - 1, mx + 19, my + 19, 0xFF4F8C96);
        g.fill(mx, my, mx + 18, my + 18, 0xFF10161E);
        if (shownTab() == Tab.REFORGE && sub == Sub.REFORGE && AffixRules.eligible(menu.gear())) {
            int count = AffixRules.offers(menu.gear()).size();
            for (int i = 0; i < AffixRules.MAX_TRIALS; i++) {
                int cx = x + SIDE_X + 8;
                int cy = y + CARD_Y + i * CARD_STEP;
                boolean hover = i < count && mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H;
                int state = i == selected ? 2 : hover ? 1 : 0;
                if (i >= count) {
                    // まだ試していない枠は暗く
                    g.setColor(0.55f, 0.55f, 0.6f, 1f);
                }
                g.blit(TEXTURE, cx, cy, CARD_W, CARD_H, state * 280, 416, CARD_W * 2, CARD_H * 2, TEX_W, TEX_H);
                g.setColor(1f, 1f, 1f, 1f);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, OK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        String[] tabKeys = {"gui.skyabyss.forge.tab_enhance", "gui.skyabyss.forge.tab_powder", "gui.skyabyss.forge.tab_reforge", "gui.skyabyss.forge.tab_exchange"};
        for (Tab t : Tab.values()) {
            if (!tabShown(t)) {
                continue;
            }
            int tx = SIDE_X + TAB_X[t.ordinal()];
            Component label = Component.translatable(tabKeys[t.ordinal()]);
            // 字は等倍 (docs/design/56)。入らなければ「…」
            jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, label, tx + TAB_W / 2, 8, TAB_W - 4, t == shownTab() ? 0xFFFFFFFF : DIM, false);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.forge.material"), ResonanceForgeMenu.MATERIAL_X - 2, ResonanceForgeMenu.MATERIAL_Y - 12, DIM, false);
        gearInfo(g);
        switch (shownTab()) {
            case ENHANCE -> enhancePanel(g);
            case POWDER -> powderPanel(g);
            case REFORGE -> {
                switch (sub) {
                    case REFORGE -> reforgePanel(g);
                    case EXTRACT -> extractPanel(g);
                    case IMPRINT -> imprintPanel(g);
                }
            }
            case EXCHANGE -> exchangePanel(g);
        }
    }

    // ---------------------------------------------------------------- 右: 粉 (差す・外す・合わせる)
    private void powderPanel(GuiGraphics g) {
        ItemStack gear = menu.gear();
        ItemStack mat = menu.material();
        int x = SIDE_X + 10;
        if (gear.isEmpty()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.powder_help"), x, 24, 132, 7, DIM);
        } else if (!GearFreedom.socketable(gear)) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.powder_no"), x, 24, 132, 5, DIM);
        } else {
            GearExtras ex = GearFreedom.extras(gear);
            g.drawString(font, Component.translatable("gui.skyabyss.forge.sockets_of", ex.gems().size(), ex.sockets()), x, 22, TEXT, false);
            for (int i = 0; i < ex.sockets(); i++) {
                int y = SOCKET_ROW_Y + i * SOCKET_ROW_STEP;
                if (i < ex.gems().size()) {
                    g.drawString(font, Component.literal("◆"), x, y, GearFreedom.TAG_COLOR | 0xFF000000, false);
                    drawWrapped(g, GearFreedom.gemShort(ex.gems().get(i), gear), x + 9, y, 110, 1, TEXT);
                } else {
                    g.drawString(font, Component.translatable("gear.skyabyss.socket_empty"), x, y, DIM, false);
                }
            }
            // 粉の技の行 (働くか・あといくつ)
            int ay = ART_Y;
            boolean weapon = GearFreedom.socketSlot(gear.getItem()) == jp.revati.skyabyss.item.affix.AffixType.Slot.WEAPON;
            for (java.util.Map.Entry<jp.revati.skyabyss.item.extra.Element, Integer> e : jp.revati.skyabyss.item.extra.Powder.weights(gear).entrySet()) {
                if (ay > ART_Y + 18) {
                    break;
                }
                Component name = Component.translatable(jp.revati.skyabyss.item.extra.PowderArts.artKey(e.getKey(), weapon));
                boolean on = e.getValue() >= jp.revati.skyabyss.item.extra.Powder.WEIGHT_FOR_ART;
                Component line = on ? Component.translatable("gui.skyabyss.forge.art_on", name)
                        : Component.translatable("gui.skyabyss.forge.art_off", name, jp.revati.skyabyss.item.extra.Powder.WEIGHT_FOR_ART - e.getValue());
                drawWrapped(g, line, x, ay, 132, 1, on ? 0xFF000000 | jp.revati.skyabyss.item.extra.PowderArts.COLORS[e.getKey().ordinal()] : DIM);
                ay += 9;
            }
            jp.revati.skyabyss.item.extra.Gem gem = ForgeRules.gemOf(mat);
            Component hint;
            int color = DIM;
            if (gem == null) {
                hint = Component.translatable("gui.skyabyss.forge.insert_help");
            } else if (GearFreedom.canInsert(gear, gem)) {
                hint = Component.translatable("gui.skyabyss.forge.insert_ready", GearFreedom.gemLine(gem, gear));
                color = OK;
            } else {
                hint = Component.translatable(ex.gems().size() >= ex.sockets() ? "gui.skyabyss.forge.insert_full" : "gui.skyabyss.forge.insert_dup");
                color = LACK;
            }
            drawWrapped(g, hint, x, 104, 132, 2, color);
        }
        // 合わせる (材料の枠の粉)
        Component combine;
        int color = DIM;
        if (ForgeRules.canCombine(mat)) {
            int t = jp.revati.skyabyss.item.extra.Powder.tier(mat);
            combine = Component.translatable("gui.skyabyss.forge.combine_ready", jp.revati.skyabyss.item.extra.Powder.COMBINE,
                    jp.revati.skyabyss.item.extra.Powder.roman(t), jp.revati.skyabyss.item.extra.Powder.roman(t + 1));
            color = OK;
        } else {
            combine = Component.translatable("gui.skyabyss.forge.combine_help", jp.revati.skyabyss.item.extra.Powder.COMBINE);
        }
        drawWrapped(g, combine, x, POWDER_BUTTON_Y + 20, 132, 2, color);
    }

    // ---------------------------------------------------------------- 右: 抽出 (再鍛造のつまみの中)
    private void extractPanel(GuiGraphics g) {
        ItemStack gear = menu.gear();
        int x = SIDE_X + 10;
        if (!AffixRules.eligible(gear)) {
            drawWrapped(g, Component.translatable(gear.isEmpty() ? "gui.skyabyss.forge.inscribe_help" : "gui.skyabyss.forge.inscribe_no"), x, 42, 132, 5, DIM);
            return;
        }
        GearExtras ex = GearFreedom.extras(gear);
        int crystals = ForgeRules.count(minecraft.player, ModItems.RESONANCE_CRYSTAL.get());
        jp.revati.skyabyss.rpg.skill.Rune own = GearFreedom.ownRune(gear);
        if (own == null) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.extract_none"), x, 42, 132, 4, DIM);
        } else if (ex.hollow()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.extract_hollow"), x, 42, 132, 4, DIM);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.extract_rewrite", Component.translatable(own.skillNameKey())), x, 42, GOLD, false);
            drawWrapped(g, Component.translatable(own.nameKey()), x, 52, 132, 1, TEXT);
            drawWrapped(g, Component.translatable(own.effectKey()), x, 62, 132, 4, DIM);
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.extract_cost", ForgeRules.extractCost(true), ForgeRules.extractCost(false), crystals), x, 104, 132, 2,
                    minecraft.player.isCreative() || crystals >= ForgeRules.extractCost(true) ? OK : LACK);
        }
    }

    // ---------------------------------------------------------------- 右: 刻印 (再鍛造のつまみの中)
    private void imprintPanel(GuiGraphics g) {
        ItemStack gear = menu.gear();
        ItemStack mat = menu.material();
        int x = SIDE_X + 10;
        if (!AffixRules.eligible(gear)) {
            drawWrapped(g, Component.translatable(gear.isEmpty() ? "gui.skyabyss.forge.inscribe_help" : "gui.skyabyss.forge.inscribe_no"), x, 42, 132, 5, DIM);
            return;
        }
        GearExtras ex = GearFreedom.extras(gear);
        int crystals = ForgeRules.count(minecraft.player, ModItems.RESONANCE_CRYSTAL.get());
        jp.revati.skyabyss.rpg.skill.Rune core = GearFreedom.coreRune(mat);
        if (!ex.aspect().isEmpty()) {
            jp.revati.skyabyss.rpg.skill.Rune have = jp.revati.skyabyss.rpg.skill.Rune.byId(ex.aspect());
            g.drawString(font, Component.translatable("gui.skyabyss.forge.imprint_has"), x, 42, GOLD, false);
            if (have != null) {
                drawWrapped(g, Component.translatable(have.nameKey()), x, 52, 132, 1, TEXT);
                drawWrapped(g, Component.translatable("tooltip.skyabyss.legend_rewrite", Component.translatable(have.skillNameKey()), Component.translatable(have.nameKey())), x, 62, 132, 2, DIM);
            }
        } else if (!(gear.getItem() instanceof ModGear mg) || !mg.tier().aspectHost()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.imprint_rare_only"), x, 42, 132, 4, DIM);
        } else if (core == null) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.imprint_help"), x, 42, 132, 4, DIM);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.imprint_ready"), x, 42, OK, false);
            drawWrapped(g, Component.translatable(core.nameKey()), x, 52, 132, 1, TEXT);
            drawWrapped(g, Component.translatable(core.effectKey()), x, 62, 132, 4, DIM);
        }
        g.drawString(font, Component.translatable("gui.skyabyss.forge.imprint_cost", GearFreedom.IMPRINT_CRYSTALS, crystals), x, 126,
                minecraft.player.isCreative() || crystals >= GearFreedom.IMPRINT_CRYSTALS ? OK : LACK, false);
    }

    // ---------------------------------------------------------------- 左: 装備の名前・強化・特性
    private void gearInfo(GuiGraphics g) {
        ItemStack gear = menu.gear();
        if (gear.isEmpty()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.empty"), INFO_X, 19, 94, 3, TEXT);
            return;
        }
        drawWrapped(g, gear.getHoverName(), INFO_X, 19, 94, 1, TEXT);
        if (gear.getItem() instanceof ModGear mg) {
            Component tier = Component.translatable(mg.tier().translationKey());
            Component line = mg.enhanceable()
                    ? tier.copy().append(Component.literal("  +" + ModGear.enhance(gear) + "/" + ModGear.MAX_ENHANCE))
                    : tier;
            g.drawString(font, line, INFO_X, 29, 0xFF000000 | mg.tier().color.getValue(), false);
        }
        int y = 40;
        if (!AffixRules.eligible(gear)) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.no_affix"), INFO_X, y, 94, 3, DIM);
            return;
        }
        if (!gear.has(ModDataComponents.AFFIXES.get())) {
            drawWrapped(g, Component.translatable("gear.skyabyss.affix_unrolled"), INFO_X, y, 94, 3, DIM);
            return;
        }
        for (Affix a : AffixRules.affixes(gear)) {
            drawWrapped(g, AffixRules.line(a, AffixRules.wide(gear)), INFO_X - 4, y, 98, 1, TEXT);
            y += 10;
        }
    }

    // ---------------------------------------------------------------- 右: 強化と分解
    private void enhancePanel(GuiGraphics g) {
        ItemStack gear = menu.gear();
        int x = SIDE_X + 10;
        if (gear.isEmpty()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.enhance_help"), x, 27, 132, 4, DIM);
            return;
        }
        int level = ModGear.enhance(gear);
        if (ForgeRules.canEnhance(gear)) {
            ForgeRules.Cost cost = ForgeRules.enhanceCost(level);
            g.drawString(font, Component.translatable("gui.skyabyss.forge.next", level, level + 1), x, 27, level + 1 >= ModGear.AWAKEN_LEVEL ? GOLD : OK, false);
            costLine(g, ForgeRules.shardItem(gear), cost.shards(), x, 38);
            costLine(g, ModItems.RESONANCE_CRYSTAL.get(), cost.crystals(), x, 48);
        } else if (gear.getItem() instanceof ModGear g2 && g2.enhanceable()) {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.max"), x, 27, GOLD, false);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.not_enhanceable"), x, 27, TEXT, false);
        }
        // 分解で得るもの
        g.drawString(font, Component.translatable("gui.skyabyss.forge.dismantle_yields"), x, 92, TEXT, false);
        int[] yield = ForgeRules.dismantleYield(gear);
        int y = 103;
        Item[] items = {ForgeRules.resonanceItem(gear), ForgeRules.shardItem(gear), ModItems.RESONANCE_CRYSTAL.get()};
        for (int i = 0; i < 3; i++) {
            if (yield[i] > 0) {
                g.drawString(font, Component.literal("・").append(Component.translatable(items[i].getDescriptionId())).append(" ×" + yield[i]), x, y, DIM, false);
                y += 10;
            }
        }
    }

    // ---------------------------------------------------------------- 右: 再鍛造
    private void reforgePanel(GuiGraphics g) {
        ItemStack gear = menu.gear();
        int x = SIDE_X + 10;
        if (!AffixRules.eligible(gear)) {
            drawWrapped(g, Component.translatable(gear.isEmpty() ? "gui.skyabyss.forge.reforge_help" : "gui.skyabyss.forge.no_affix"), x, 40, 132, 5, DIM);
            return;
        }
        List<List<Affix>> offers = AffixRules.offers(gear);
        int have = ForgeRules.count(minecraft.player, ModItems.RESONANCE_CRYSTAL.get());
        boolean enough = minecraft.player.isCreative() || have >= AffixRules.TRIAL_CRYSTALS;
        g.drawString(font, Component.translatable("gui.skyabyss.forge.trials", offers.size(), AffixRules.MAX_TRIALS), x, 36, TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.forge.crystals_short", have), x + 46, 36, enough ? OK : LACK, false);
        for (int i = 0; i < AffixRules.MAX_TRIALS; i++) {
            int cx = SIDE_X + 8 + 8;
            int cy = CARD_Y + i * CARD_STEP + 3;
            if (i < offers.size()) {
                int ly = cy;
                for (Affix a : offers.get(i)) {
                    drawWrapped(g, AffixRules.describe(a), cx, ly, 124, 1, i == selected ? GOLD : TEXT);
                    ly += 9;
                }
            } else if (i == offers.size()) {
                drawWrapped(g, Component.translatable("gui.skyabyss.forge.trial_empty"), cx, cy + 8, 124, 1, DIM);
            }
        }
    }

    // ---------------------------------------------------------------- 右: 交換
    private void exchangePanel(GuiGraphics g) {
        int x = SIDE_X + 10;
        int have = ForgeRules.resonanceHeld(minecraft.player, set().titan);
        boolean enough = minecraft.player.isCreative() || have >= ForgeRules.EXCHANGE_SHARDS;
        g.drawString(font, Component.translatable("gui.skyabyss.forge.exchange_shards", Component.translatable(ModItems.resonanceShard(set().titan).getDescriptionId()), have, ForgeRules.EXCHANGE_SHARDS), x, 26, enough ? OK : LACK, false);
        String state = switch (menu.exchangeState(titan)) {
            case READY -> "gui.skyabyss.forge.exchange_ready";
            case USED -> "gui.skyabyss.forge.exchange_used";
            case NO_CLEAR -> "gui.skyabyss.forge.exchange_no_clear";
            case ALL_OWNED -> "gui.skyabyss.forge.exchange_all_owned";
        };
        // 巨神ごとの文があればそちら (例 gui.skyabyss.forge.exchange_no_clear.tide_shell)。岩鯨は元の文
        String perTitan = state + "." + set().titan;
        if (net.minecraft.client.resources.language.I18n.exists(perTitan)) {
            state = perTitan;
        }
        drawWrapped(g, Component.translatable(state), x, 37, 132, 2, menu.exchangeState(titan) == ForgeRules.ExchangeState.READY ? TEXT : DIM);
        List<Item> exclusives = exclusives();
        for (int i = 0; i < exclusives.size(); i++) {
            if (exclusives.get(i) == null) {
                continue;
            }
            int y = EXCHANGE_Y + i * EXCHANGE_STEP;
            ItemStack shown = new ItemStack(exclusives.get(i));
            boolean owned = menu.ownsExclusive(titan, i);
            g.renderItem(shown, x, y);
            Component name = shown.getHoverName();
            drawWrapped(g, name, x + 20, y + 4, owned ? 120 : 66, 1, owned ? DIM : TEXT);
            if (owned) {
                g.drawString(font, Component.translatable("gui.skyabyss.forge.exchange_owned"), SIDE_X + SIDE_W - 10 - font.width(Component.translatable("gui.skyabyss.forge.exchange_owned")),
                        y + 4, DIM, false);
            }
        }
    }

    // 交換の巨神の表と、その専用の逸品 (まだ登録されていない品は null。番号は set.exclusives() の並び)
    private TitanGearSet set() {
        List<TitanGearSet> all = TitanGearSets.exchangeable();
        return all.get(Math.min(titan, all.size() - 1));
    }

    private List<Item> exclusives() {
        List<Item> out = new java.util.ArrayList<>();
        for (String id : set().exclusives()) {
            out.add(TitanGearSet.item(id).orElse(null));
        }
        return out.subList(0, Math.min(out.size(), exchangeButtons.length));
    }

    private void costLine(GuiGraphics g, Item material, int need, int x, int y) {
        int have = ForgeRules.count(minecraft.player, material);
        boolean enough = minecraft.player.isCreative() || have >= need;
        Component line = Component.literal("・").append(Component.translatable(material.getDescriptionId())).append(" " + have + "/" + need);
        g.drawString(font, line, x, y, enough ? OK : LACK, false);
    }

    // 決めた幅で折り返し、決めた行数まで描く
    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = jp.revati.skyabyss.client.ui.JpWrap.split(font, text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, y + i * 9, color, false);
        }
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos - 4, topPos - 24, imageWidth + 8, imageHeight + 28};
    }
}
