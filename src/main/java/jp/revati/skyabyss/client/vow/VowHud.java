package jp.revati.skyabyss.client.vow;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.client.PartyMarkers;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.titan.vow.Vows;
import jp.revati.skyabyss.titan.vow.VowNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 誓いの帯 (周回の縛りを立てているあいだ、画面の右上に細い二行): 「誓い 段 N ・巨神」と、誓いの名前の並び。
// 時間制限があれば残りを足す。同じグループの全員の画面に出る (サーバーが VowNetwork.Sync を送る)。場面の最中・画面を開いている間・F1 では出さない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class VowHud {
    private static volatile VowNetwork.Sync current = new VowNetwork.Sync("", java.util.List.of(), 0, -1);
    private static volatile long receivedAt;

    private VowHud() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "vow_band"), VowHud::render);
        }
    }

    public static void receive(VowNetwork.Sync sync) {
        current = sync;
        receivedAt = net.minecraft.Util.getMillis();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = new VowNetwork.Sync("", java.util.List.of(), 0, -1);
    }

    // 帯の文 (空なら出さない)。画面と自己テストで同じ
    public static java.util.List<Component> lines(VowNetwork.Sync s, long elapsedMillis) {
        if (s.ids().isEmpty()) {
            return java.util.List.of();
        }
        MutableComponent head = Component.translatable("hud.skyabyss.vow_band", s.level(), Progression.titanName(s.titan()));
        if (s.timerSec() >= 0) {
            int left = Math.max(0, s.timerSec() - (int) (elapsedMillis / 1000));
            head.append(Component.translatable("hud.skyabyss.vow_band_timer", left / 60, String.format("%02d", left % 60)));
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (String id : s.ids()) {
            var vow = Vows.get(id);
            names.append(Component.literal(first ? "" : " / ")).append(vow == null ? Component.literal(id) : Component.translatable(vow.nameKey()));
            first = false;
        }
        return java.util.List.of(head, names);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        VowNetwork.Sync s = current;
        if (s.ids().isEmpty() || !PartyMarkers.hudAllowed(mc)) {
            return;
        }
        Font font = mc.font;
        int y = 4;
        for (Component line : lines(s, net.minecraft.Util.getMillis() - receivedAt)) {
            int w = font.width(line);
            g.drawString(font, line, g.guiWidth() - w - 6, y, y == 4 ? 0xF2C14E : 0xE9E3D4, true);
            y += 11;
        }
    }
}
