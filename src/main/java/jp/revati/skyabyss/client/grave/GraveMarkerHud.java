package jp.revati.skyabyss.client.grave;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.grave.GraveMarkers;
import jp.revati.skyabyss.party.client.PartyMarkers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 自分の墓の標 (rpg_brushup A8)。サーバーが自分にだけ送った墓の場所を、仲間の札と同じ投影 (PartyMarkers.drawMarker) で描く:
// 画面の中の墓の上に札、画面の外なら縁の矢印と距離。墓へ NEAR_BLOCKS 以内に近づくと消える。拾い終えれば、サーバーが空を送って消える。
// 場面の最中・画面を開いている間・F1 では出さない。設定 graveMarker で消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GraveMarkerHud {
    private static List<GraveMarkers.Mark> marks = List.of();
    // 見た目の確認用 (撮影の台本): true の間、設定と場面の条件を無視して描く
    private static volatile boolean forceShow;

    private GraveMarkerHud() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "grave_marker"), GraveMarkerHud::render);
        }
    }

    public static void receive(GraveMarkers.Pos pos) {
        marks = pos.marks();
    }

    public static void clear() {
        marks = List.of();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void forceShow(boolean on) {
        forceShow = on;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (marks.isEmpty() || !(forceShow || Config.GRAVE_MARKER.get()) || !PartyMarkers.hudAllowed(mc)) {
            return;
        }
        String here = mc.level.dimension().location().toString();
        List<int[]> taken = new ArrayList<>();
        for (GraveMarkers.Mark m : marks) {
            if (!m.dimension().equals(here)) {
                continue;
            }
            Vec3 pos = new Vec3(m.x() + 0.5, m.y() + 1.4, m.z() + 0.5);
            int dist = (int) Math.round(mc.player.position().distanceTo(new Vec3(m.x() + 0.5, m.y() + 0.5, m.z() + 0.5)));
            if (dist <= GraveMarkers.NEAR_BLOCKS) {
                continue;
            }
            PartyMarkers.drawMarker(g, pos, Component.translatable("hud.skyabyss.grave_marker", dist), 0xE8D8A8, PartyMarkers.Symbol.GRAVE, -1, taken);
        }
    }
}
