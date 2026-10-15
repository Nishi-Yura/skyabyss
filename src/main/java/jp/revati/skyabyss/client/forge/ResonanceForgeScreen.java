package jp.revati.skyabyss.client.forge;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.forge.ResonanceForgeMenu;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.affix.Affix;
import jp.revati.skyabyss.item.affix.AffixRules;
import jp.revati.skyabyss.loot.TitanLoot;
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

// 共鳴の炉の画面。左の板に装備の枠・特性・持ち物、右の板に三つのつまみ (強化・再鍛造・交換)。
// 背景は 2 倍密度のテクスチャ (textures/gui/forge/forge.png、tools/gen_forge_assets.py)。配置を変えたら生成元もそろえる
public class ResonanceForgeScreen extends AbstractContainerScreen<ResonanceForgeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/forge/forge.png");
    private static final int TEX_W = 1024;
    private static final int TEX_H = 512;
    private static final int MAIN_W = 176;
    private static final int SIDE_X = 180;
    private static final int SIDE_W = 152;
    private static final int H = 166;
    private static final int TAB_W = 48;
    private static final int TAB_H = 14;
    private static final int[] TAB_X = {4, 52, 100};
    private static final int CARD_W = 136;
    private static final int CARD_H = 31;
    private static final int CARD_Y = 38;
    private static final int CARD_STEP = 33;
    private static final int EXCHANGE_Y = 62;
    private static final int EXCHANGE_STEP = 24;

    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF7A8090;
    private static final int OK = 0xFF9FE8FF;
    private static final int LACK = 0xFFD9779A;
    private static final int GOLD = 0xFFF2B53A;
    private static final int INFO_X = 72;

    private enum Tab {
        ENHANCE, REFORGE, EXCHANGE
    }

    // 最後に開いたつまみ (同じ遊びの間は覚えておく)
    private static Tab lastTab = Tab.ENHANCE;
    private Tab tab = lastTab;

    private Button enhanceButton;
    private Button dismantleButton;
    private Button trialButton;
    private Button confirmButton;
    private Button keepButton;
    private final Button[] exchangeButtons = new Button[3];
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
                .bounds(sx + 96, topPos + 23, 46, 14).build());
        confirmButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.pick"), b -> {
            if (selected >= 0) {
                press(ResonanceForgeMenu.BUTTON_PICK + selected);
                selected = -1;
            }
        }).bounds(sx + 10, topPos + 140, 62, 16).build());
        keepButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.keep"), b -> {
            if (!keepArmed) {
                keepArmed = true;
            } else {
                keepArmed = false;
                selected = -1;
                press(ResonanceForgeMenu.BUTTON_KEEP);
            }
        }).bounds(sx + 76, topPos + 140, 66, 16).build());
        for (int i = 0; i < exchangeButtons.length; i++) {
            int index = i;
            exchangeButtons[i] = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.exchange_pick"), b -> {
                if (exchangeArmed != index) {
                    exchangeArmed = index;
                } else {
                    exchangeArmed = -1;
                    press(ResonanceForgeMenu.BUTTON_EXCHANGE + index);
                }
            }).bounds(sx + 98, topPos + EXCHANGE_Y + i * EXCHANGE_STEP, 44, 16).build());
        }
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
        boolean enhanceTab = tab == Tab.ENHANCE;
        boolean reforgeTab = tab == Tab.REFORGE;
        boolean exchangeTab = tab == Tab.EXCHANGE;

        enhanceButton.visible = enhanceTab;
        dismantleButton.visible = enhanceTab;
        enhanceButton.active = ForgeRules.canEnhance(gear) && ForgeRules.affordable(minecraft.player, ForgeRules.enhanceCost(ModGear.enhance(gear)));
        dismantleButton.active = !gear.isEmpty();
        dismantleButton.setMessage(Component.translatable(dismantleArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.dismantle"));

        boolean eligible = AffixRules.eligible(gear);
        List<List<Affix>> offers = AffixRules.offers(gear);
        trialButton.visible = reforgeTab && eligible;
        confirmButton.visible = reforgeTab && eligible;
        keepButton.visible = reforgeTab && eligible;
        trialButton.active = AffixRules.canTrial(gear) && ForgeRules.affordable(minecraft.player, ForgeRules.trialCost());
        trialButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.trial_hint", AffixRules.TRIAL_CRYSTALS, AffixRules.MAX_TRIALS)));
        confirmButton.active = selected >= 0 && selected < offers.size();
        keepButton.active = !offers.isEmpty();
        keepButton.setMessage(Component.translatable(keepArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.keep"));
        keepButton.setTooltip(Tooltip.create(Component.translatable("gui.skyabyss.forge.keep_hint")));

        List<Item> exclusives = TitanLoot.exclusives();
        boolean ready = menu.exchangeState() == ForgeRules.ExchangeState.READY;
        boolean affordable = ForgeRules.exchangeAffordable(minecraft.player);
        for (int i = 0; i < exchangeButtons.length; i++) {
            Button b = exchangeButtons[i];
            boolean owned = menu.ownsExclusive(i);
            b.visible = exchangeTab && i < exclusives.size() && !owned;
            b.active = ready && affordable;
            b.setMessage(Component.translatable(exchangeArmed == i ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.exchange_pick"));
        }

        // 分解で得られるものは、ボタンに重ねると出る (右の板にも書く)
        dismantleButton.setTooltip(gear.isEmpty() ? null : Tooltip.create(Component.translatable("gui.skyabyss.forge.dismantle_hint")));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // つまみ
        for (Tab t : Tab.values()) {
            int x = leftPos + SIDE_X + TAB_X[t.ordinal()];
            int y = topPos + 4;
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H) {
                if (tab != t) {
                    setTab(t);
                    playClick();
                }
                return true;
            }
        }
        // 候補の札
        if (tab == Tab.REFORGE) {
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
        if (tab == Tab.EXCHANGE) {
            List<Item> exclusives = TitanLoot.exclusives();
            for (int i = 0; i < exclusives.size(); i++) {
                int x = leftPos + SIDE_X + 10;
                int y = topPos + EXCHANGE_Y + i * EXCHANGE_STEP;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
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
            int tx = x + SIDE_X + TAB_X[t.ordinal()];
            int ty = y + 4;
            boolean hover = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= ty && mouseY < ty + TAB_H;
            int state = t == tab ? 2 : hover ? 1 : 0;
            g.blit(TEXTURE, tx, ty, TAB_W, TAB_H, state * 100, 352, TAB_W * 2, TAB_H * 2, TEX_W, TEX_H);
            g.blit(TEXTURE, tx + 3, ty + 1, 12, 12, t.ordinal() * 32, 384, 24, 24, TEX_W, TEX_H);
        }
        if (tab == Tab.REFORGE && AffixRules.eligible(menu.gear())) {
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
        String[] tabKeys = {"gui.skyabyss.forge.tab_enhance", "gui.skyabyss.forge.tab_reforge", "gui.skyabyss.forge.tab_exchange"};
        for (Tab t : Tab.values()) {
            int tx = SIDE_X + TAB_X[t.ordinal()];
            g.drawString(font, Component.translatable(tabKeys[t.ordinal()]), tx + 16, 7, t == tab ? 0xFFFFFFFF : DIM, false);
        }
        gearInfo(g);
        switch (tab) {
            case ENHANCE -> enhancePanel(g);
            case REFORGE -> reforgePanel(g);
            case EXCHANGE -> exchangePanel(g);
        }
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
            Component tier = Component.translatable("gear.skyabyss.tier." + mg.tier().key);
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
            drawWrapped(g, AffixRules.line(a), INFO_X - 4, y, 98, 1, TEXT);
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
            costLine(g, ModItems.STONE_WHALE_SHARD.get(), cost.shards(), x, 38);
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
        Item[] items = {ModItems.RESONANCE_SHARD.get(), ModItems.STONE_WHALE_SHARD.get(), ModItems.RESONANCE_CRYSTAL.get()};
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
            drawWrapped(g, Component.translatable(gear.isEmpty() ? "gui.skyabyss.forge.reforge_help" : "gui.skyabyss.forge.no_affix"), x, 27, 132, 5, DIM);
            return;
        }
        List<List<Affix>> offers = AffixRules.offers(gear);
        int have = ForgeRules.count(minecraft.player.getInventory(), ModItems.RESONANCE_CRYSTAL.get());
        boolean enough = minecraft.player.isCreative() || have >= AffixRules.TRIAL_CRYSTALS;
        g.drawString(font, Component.translatable("gui.skyabyss.forge.trials", offers.size(), AffixRules.MAX_TRIALS), x, 24, TEXT, false);
        g.drawString(font, Component.translatable("gui.skyabyss.forge.crystals", have), x, 33, enough ? OK : LACK, false);
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
        int have = ForgeRules.count(minecraft.player.getInventory(), ModItems.RESONANCE_SHARD.get());
        boolean enough = minecraft.player.isCreative() || have >= ForgeRules.EXCHANGE_SHARDS;
        g.drawString(font, Component.translatable("gui.skyabyss.forge.exchange_shards", have, ForgeRules.EXCHANGE_SHARDS), x, 26, enough ? OK : LACK, false);
        String state = switch (menu.exchangeState()) {
            case READY -> "gui.skyabyss.forge.exchange_ready";
            case USED -> "gui.skyabyss.forge.exchange_used";
            case NO_CLEAR -> "gui.skyabyss.forge.exchange_no_clear";
            case ALL_OWNED -> "gui.skyabyss.forge.exchange_all_owned";
        };
        drawWrapped(g, Component.translatable(state), x, 37, 132, 2, menu.exchangeState() == ForgeRules.ExchangeState.READY ? TEXT : DIM);
        List<Item> exclusives = TitanLoot.exclusives();
        for (int i = 0; i < exclusives.size(); i++) {
            int y = EXCHANGE_Y + i * EXCHANGE_STEP;
            ItemStack shown = new ItemStack(exclusives.get(i));
            boolean owned = menu.ownsExclusive(i);
            g.renderItem(shown, x, y);
            Component name = shown.getHoverName();
            drawWrapped(g, name, x + 20, y + 4, owned ? 120 : 66, 1, owned ? DIM : TEXT);
            if (owned) {
                g.drawString(font, Component.translatable("gui.skyabyss.forge.exchange_owned"), SIDE_X + SIDE_W - 10 - font.width(Component.translatable("gui.skyabyss.forge.exchange_owned")),
                        y + 4, DIM, false);
            }
        }
    }

    private void costLine(GuiGraphics g, Item material, int need, int x, int y) {
        int have = ForgeRules.count(minecraft.player.getInventory(), material);
        boolean enough = minecraft.player.isCreative() || have >= need;
        Component line = Component.literal("・").append(Component.translatable(material.getDescriptionId())).append(" " + have + "/" + need);
        g.drawString(font, line, x, y, enough ? OK : LACK, false);
    }

    // 決めた幅で折り返し、決めた行数まで描く
    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, y + i * 9, color, false);
        }
    }
}
