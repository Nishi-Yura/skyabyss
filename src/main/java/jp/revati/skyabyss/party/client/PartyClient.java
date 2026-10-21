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
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.PARTY, "key.categories.skyabyss");

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
        if (sync.members().isEmpty()) {
            PartyMarkers.clear();
        }
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

    // ---------------------------------------------------------------- 仲間の表示 (右端。置き場は HudManager)
    static final int W = 84;

    // 一覧に出す仲間 (自分は除く)
    private static List<PartyNetwork.Member> others(Minecraft mc) {
        String self = mc.player.getUUID().toString();
        List<PartyNetwork.Member> out = new java.util.ArrayList<>();
        for (PartyNetwork.Member m : last.members()) {
            if (!m.id().equals(self)) {
                out.add(m);
            }
        }
        return out;
    }

    private static int rowHeight(PartyNetwork.Member m) {
        return 19 + (m.online() && PartyMarkers.elsewhereLine(m.id()) != null ? 10 : 0);
    }

    // HUD の要求: 仲間の行 (一行 19、別の場所にいる人は 10 増す)。画面の高さの 1/3 より下から
    public static void requests(Minecraft mc, int guiW, int guiH, List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || last.members().isEmpty()) {
            return;
        }
        int h = 0;
        for (PartyNetwork.Member m : others(mc)) {
            h += rowHeight(m);
        }
        if (h > 0) {
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.PARTY_LIST, jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT, W + 4, h)
                    .minTop(guiH / 3));
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.PARTY_LIST);
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || last.members().isEmpty() || r == null) {
            return;
        }
        Font font = mc.font;
        int x = r.x() + 2;
        int y = r.y() + 2;
        for (PartyNetwork.Member m : others(mc)) {
            g.fill(x - 2, y - 2, x + W + 2, y + 15, 0x60000000);
            int color = !m.online() ? 0x707070 : m.sameDim() ? 0xE8F4FF : 0x9AA6B8;
            // 順路の段 (docs/design/32 の 6 章): 同じ背にいる仲間には、いまの段の番号を添える
            String step = m.sameDim() && jp.revati.skyabyss.client.trial.TrialBoardClient.routeActive(mc)
                    ? net.minecraft.network.chat.Component.translatable("gui.skyabyss.party.route_step", jp.revati.skyabyss.client.trial.TrialBoardClient.route().step()).getString()
                    : "";
            String name = step + (m.leader() ? "★" : "") + m.name();
            g.drawString(font, font.plainSubstrByWidth(name, W - 28), x, y, color, true);
            if (m.online()) {
                String lv = "Lv" + m.level();
                g.drawString(font, lv, x + W - font.width(lv), y, 0x9FE8FF, true);
                float f = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0, 1);
                g.fill(x, y + 10, x + W, y + 13, 0xFF3A1E22);
                int hp = f > 0.5f ? 0xFF6BD06B : f > 0.25f ? 0xFFE8C04A : 0xFFE05050;
                g.fill(x, y + 10, x + Math.round(W * f), y + 13, m.sameDim() ? hp : 0xFF7A7A7A);
            }
            net.minecraft.network.chat.Component away = m.online() ? PartyMarkers.elsewhereLine(m.id()) : null;
            if (away != null) {
                int tw = Math.min(font.width(away), W);
                int x0 = x + W - tw;
                g.fill(x0 - 2, y + 15, x + W + 2, y + 25, 0x60000000);
                g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(away, tw)), x0, y + 16, 0x9AA6B8, true);
            }
            y += rowHeight(m);
        }
    }
}
