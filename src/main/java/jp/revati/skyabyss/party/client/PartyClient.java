package jp.revati.skyabyss.party.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// パーティのクライアント側。O で「物語と仲間」の画面を開き、画面の右に仲間の名前と体力を小さく出す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PartyClient {
    public static final KeyMapping PARTY_KEY = new KeyMapping("key.skyabyss.party", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.skyabyss");

    static PartyNetwork.Sync last = new PartyNetwork.Sync(0, 15, 0, "", 70, List.of(), "");

    private PartyClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(PARTY_KEY);
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_members"), PartyClient::render);
        }
    }

    public static void receive(PartyNetwork.Sync sync) {
        last = sync;
        if (Minecraft.getInstance().screen instanceof PartyScreen screen) {
            screen.refresh();
        }
    }

    public static PartyNetwork.Sync state() {
        return last;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (PARTY_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new PartyScreen());
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        last = new PartyNetwork.Sync(0, 15, 0, "", 70, List.of(), "");
    }

    // ---------------------------------------------------------------- 仲間の表示 (右端、画面の高さの 1/3 から)
    static final int W = 84;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || last.members().isEmpty()) {
            return;
        }
        Font font = mc.font;
        int x = g.guiWidth() - W - 4;
        int y = g.guiHeight() / 3;
        String self = mc.player.getUUID().toString();
        for (PartyNetwork.Member m : last.members()) {
            if (m.id().equals(self)) {
                continue;
            }
            g.fill(x - 2, y - 2, x + W + 2, y + 15, 0x60000000);
            int color = !m.online() ? 0x707070 : m.sameDim() ? 0xE8F4FF : 0x9AA6B8;
            String name = (m.leader() ? "★" : "") + m.name();
            g.drawString(font, font.plainSubstrByWidth(name, W - 28), x, y, color, true);
            if (m.online()) {
                String lv = "Lv" + m.level();
                g.drawString(font, lv, x + W - font.width(lv), y, 0x9FE8FF, true);
                float f = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0, 1);
                g.fill(x, y + 10, x + W, y + 13, 0xFF3A1E22);
                int hp = f > 0.5f ? 0xFF6BD06B : f > 0.25f ? 0xFFE8C04A : 0xFFE05050;
                g.fill(x, y + 10, x + Math.round(W * f), y + 13, m.sameDim() ? hp : 0xFF7A7A7A);
            }
            y += 19;
        }
    }
}
