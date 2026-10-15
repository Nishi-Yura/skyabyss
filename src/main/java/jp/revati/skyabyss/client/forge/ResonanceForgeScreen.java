package jp.revati.skyabyss.client.forge;

import jp.revati.skyabyss.forge.ForgeRules;
import jp.revati.skyabyss.forge.ResonanceForgeMenu;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// 共鳴の炉の画面。テクスチャは使わず、石と共鳴の光の色で描く
public class ResonanceForgeScreen extends AbstractContainerScreen<ResonanceForgeMenu> {
    private static final int PANEL = 0xFF1C2029;
    private static final int PANEL_EDGE = 0xFF3A4152;
    private static final int PANEL_GLOW = 0xFF2A5E6A;
    private static final int SLOT_BG = 0xFF0E1015;
    private static final int TEXT = 0xFFD8DCE6;
    private static final int OK = 0xFF9FE8FF;
    private static final int LACK = 0xFFD9779A;
    private static final int GOLD = 0xFFF2B53A;
    private static final int INFO_X = 70;

    private Button enhanceButton;
    private Button dismantleButton;
    // 分解はもう一度押して確定する
    private boolean dismantleArmed;
    private ItemStack lastGear = ItemStack.EMPTY;

    public ResonanceForgeScreen(ResonanceForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        enhanceButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.enhance"), b -> press(ResonanceForgeMenu.BUTTON_ENHANCE))
                .bounds(leftPos + INFO_X, topPos + 58, 48, 16).build());
        dismantleButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.forge.dismantle"), b -> {
            if (!dismantleArmed) {
                dismantleArmed = true;
            } else {
                dismantleArmed = false;
                press(ResonanceForgeMenu.BUTTON_DISMANTLE);
            }
        }).bounds(leftPos + INFO_X + 50, topPos + 58, 50, 16).build());
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack gear = menu.gear();
        if (!ItemStack.isSameItemSameComponents(gear, lastGear)) {
            dismantleArmed = false;
            lastGear = gear.copy();
        }
        enhanceButton.active = ForgeRules.canEnhance(gear) && ForgeRules.affordable(minecraft.player, ForgeRules.enhanceCost(ModGear.enhance(gear)));
        dismantleButton.active = !gear.isEmpty();
        dismantleButton.setMessage(Component.translatable(dismantleArmed ? "gui.skyabyss.forge.confirm" : "gui.skyabyss.forge.dismantle"));
        // 分解で得られるものは、ボタンに重ねると出る
        if (!gear.isEmpty()) {
            int[] yield = ForgeRules.dismantleYield(gear);
            dismantleButton.setTooltip(Tooltip.create(yield[0] > 0
                    ? Component.translatable("gui.skyabyss.forge.yield_resonance", yield[0], yield[1])
                    : Component.translatable("gui.skyabyss.forge.yield_shards", yield[1])));
        } else {
            dismantleButton.setTooltip(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_EDGE);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
        // 装備の枠のまわりに、炉の光
        g.fillGradient(x + 8, y + 16, x + 66, y + 72, PANEL_GLOW, PANEL);
        g.fill(x + 8, y + 76, x + imageWidth - 8, y + 77, PANEL_EDGE);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, PANEL_EDGE);
            g.fill(sx + 1, sy + 1, sx + 18, sy + 18, SLOT_BG);
        }
        Slot gearSlot = menu.slots.get(0);
        g.renderOutline(x + gearSlot.x - 3, y + gearSlot.y - 3, 22, 22, OK);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, OK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        ItemStack gear = menu.gear();
        if (gear.isEmpty()) {
            drawWrapped(g, Component.translatable("gui.skyabyss.forge.empty"), INFO_X, 18, TEXT);
            return;
        }
        drawWrapped(g, gear.getHoverName(), INFO_X, 18, TEXT);
        int level = ModGear.enhance(gear);
        if (ForgeRules.canEnhance(gear)) {
            ForgeRules.Cost cost = ForgeRules.enhanceCost(level);
            g.drawString(font, Component.translatable("gui.skyabyss.forge.next", level, level + 1), INFO_X, 30, level + 1 >= ModGear.AWAKEN_LEVEL ? GOLD : OK, false);
            costLine(g, ModItems.STONE_WHALE_SHARD.get().getDefaultInstance(), cost.shards(), 40);
            costLine(g, ModItems.RESONANCE_CRYSTAL.get().getDefaultInstance(), cost.crystals(), 49);
        } else if (gear.getItem() instanceof ModGear g2 && g2.enhanceable()) {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.max"), INFO_X, 30, GOLD, false);
        } else {
            g.drawString(font, Component.translatable("gui.skyabyss.forge.not_enhanceable"), INFO_X, 30, TEXT, false);
        }
    }

    private void costLine(GuiGraphics g, ItemStack material, int need, int y) {
        int have = ForgeRules.count(minecraft.player.getInventory(), material.getItem());
        boolean enough = minecraft.player.isCreative() || have >= need;
        Component line = Component.literal("・").append(Component.translatable(material.getDescriptionId()))
                .append(" " + have + "/" + need);
        g.drawString(font, line, INFO_X, y, enough ? OK : LACK, false);
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int color) {
        // 長い名前は一行に収まるところまで
        java.util.List<FormattedCharSequence> lines = font.split(text, imageWidth - x - 6);
        if (!lines.isEmpty()) {
            g.drawString(font, lines.get(0), x, y, color, false);
        }
    }
}
