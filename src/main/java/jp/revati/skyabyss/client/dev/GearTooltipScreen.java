package jp.revati.skyabyss.client.dev;

import java.util.List;
import java.util.Optional;

import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.ember.gear.EmberGearSetup;
import jp.revati.skyabyss.titan.tide.gear.TideGearSetup;
import jp.revati.skyabyss.item.storm.StormGearSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 確認用の画面 (台本のスクショ `open jp.revati.skyabyss.client.dev.GearTooltipScreen`): 四巨神の武器のツールチップを並べて見せる。
// 物語の一行 (03 の 3.4節) の見え方を撮るため。ゲームの状態は触らない (手に持たせず、画面の中で作った品を描くだけ)
public class GearTooltipScreen extends Screen {
    private static final List<java.util.function.Supplier<Item>> ITEMS = List.of(
            ModItems.WHALEBONE_GREATSWORD::get, TideGearSetup.TRIDENT::get, StormGearSetup.BOW::get, EmberGearSetup.WAR_AXE::get);

    public GearTooltipScreen() {
        super(Component.literal("gear tooltips"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF14161C);
        Minecraft mc = Minecraft.getInstance();
        // 二列 x 二行 (説明の行が長いので、重ならないよう広く取る)
        for (int i = 0; i < ITEMS.size(); i++) {
            ItemStack stack = new ItemStack(ITEMS.get(i).get());
            List<Component> lines = Screen.getTooltipFromItem(mc, stack);
            g.renderTooltip(mc.font, lines, Optional.empty(), (i % 2) * (width / 2) + 8, 20 + (i / 2) * 140);
        }
    }
}
