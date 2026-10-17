package jp.revati.skyabyss.client.accessory;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.item.accessory.Accessory;
import jp.revati.skyabyss.item.accessory.AccessorySetup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 装飾の枠と必要レベルのクライアント側:
//  - 装飾の画面をメニューに結びつける。持ち物の画面の上に「持ち物」「装飾」のつまみを足す
//  - H キーで護符の枠の品を使う (バニラ・このModのほかのキー J/K/U/B/O/R/G/V/Z とぶつからない)
//  - 装備のツールチップに必要レベルを常に出す (足りないときは赤で、何が起きるかも)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AccessoryClient {
    public static final KeyMapping CHARM_KEY = new KeyMapping("skyabyss_gear.key.charm", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.skyabyss");
    private static final int OK = 0x9AA0B0;
    private static final int SHORT = 0xE0505A;

    private AccessoryClient() {
    }

    @SubscribeEvent
    static void onScreens(RegisterMenuScreensEvent event) {
        event.register(AccessorySetup.MENU.get(), AccessoryScreen::new);
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(CHARM_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (CHARM_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                PacketDistributor.sendToServer(new AccessorySetup.UseCharmPayload());
            }
        }
    }

    // 持ち物の画面 (サバイバル) の上に、つまみを二つ。創造モードの持ち物の画面は形が違うので足さない
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            AccessoryScreen.addTabs(event::addListener, screen, true);
        }
    }

    // 階級の行のすぐ下に「必要 Lv」。名前 (0) と階級 (1) のあとに差し込む
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int req = GearLevel.required(stack);
        if (req <= 0 || !(stack.getItem() instanceof ModGear)) {
            return;
        }
        Player player = event.getEntity();
        boolean ok = player == null || GearLevel.meets(player, stack);
        List<Component> lines = event.getToolTip();
        int at = Math.min(2, lines.size());
        MutableComponent line = Component.translatable("skyabyss_gear.tooltip.required_level", req).withStyle(s -> s.withColor(ok ? OK : SHORT));
        if (!ok) {
            line.append(Component.translatable("skyabyss_gear.tooltip.level_now", GearLevel.level(player)).withStyle(s -> s.withColor(SHORT)));
        }
        lines.add(at++, line);
        if (!ok) {
            String what = stack.getItem() instanceof ArmorItem ? "armor" : stack.getItem() instanceof Accessory ? "accessory"
                    : stack.getItem() instanceof net.minecraft.world.item.ShieldItem ? "shield" : "weapon";
            lines.add(at++, Component.translatable("skyabyss_gear.tooltip.short." + what).withStyle(s -> s.withColor(SHORT).withItalic(true)));
        }
        if (stack.getItem() instanceof Accessory a) {
            Component hint = a.accessoryKind() == jp.revati.skyabyss.item.accessory.AccessoryKind.CHARM
                    ? Component.translatable("skyabyss_gear.tooltip.slot.charm", CHARM_KEY.getTranslatedKeyMessage())
                    : Component.translatable("skyabyss_gear.tooltip.slot.trinket");
            lines.add(at, hint.copy().withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
