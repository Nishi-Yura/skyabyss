package jp.revati.skyabyss.client.map;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.guide.ObjectiveMarker;
import jp.revati.skyabyss.party.PartyPins;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

// 旅の地図のピンを、ふだんの画面 (一人称・三人称) にも出す (2026-11-09 の声「マップでピンさしたら、1人称でも見えてほしい。
// パーティー組んでたらパーティーの人にも見えるように」)。目的の標と同じひし形と距離 (client/guide/ObjectiveMarker) で、色はピンの赤。
// パーティーの仲間のピンは水色で、名前と番号を添える (party/PartyPins がサーバーから届ける)。
// 自分のピンは、置いた・外したときと、世界に入ったあとにサーバーへ送る。地図の「ピン」の組を切ると、ここにも出さない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PinMarkers {
    static final int OWN = 0xE8564A;
    static final int MATE = 0x8FE0FF;
    private static List<PartyPins.Pin> mates = List.of();
    private static boolean sendSoon;

    private PinMarkers() {
    }

    public static void receive(PartyPins.Mates m) {
        mates = List.copyOf(m.pins());
    }

    public static List<PartyPins.Pin> mates() {
        return mates;
    }

    // 自分のピンをサーバーへ (置いた・外したとき)
    public static void sendMine() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        List<int[]> out = new ArrayList<>();
        for (MapPrefs.Pin p : MapPrefs.pins()) {
            out.add(new int[] {p.x(), p.z()});
        }
        PacketDistributor.sendToServer(new PartyPins.Mine(out));
    }

    @SubscribeEvent
    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        sendSoon = true;
        mates = List.of();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (sendSoon && Minecraft.getInstance().player != null) {
            sendSoon = false;
            sendMine();
        }
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "pin_markers"), PinMarkers::render);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!ObjectiveMarker.visible(mc) || mc.level.dimension() != Level.OVERWORLD || !MapPrefs.shows(MapPrefs.Layer.PINS)) {
            return;
        }
        List<MapPrefs.Pin> own = MapPrefs.pins();
        for (int i = 0; i < own.size(); i++) {
            draw(g, mc, own.get(i).x(), own.get(i).z(), OWN, String.valueOf(i + 1));
        }
        for (PartyPins.Pin p : mates) {
            draw(g, mc, p.x(), p.z(), MATE, p.owner() + " " + p.number());
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, int x, int z, int rgb, String name) {
        // 高さ: 読み込んだ地面の上。読み込んでいない (遠い) 所は大陸の図の地面の高さ (2026-11-09 の声「赤ピンの場所、めっちゃ下」:
        // 遠いチャンクは空のチャンクとして返り、高さが世界の底になっていた)
        double y = Double.NaN;
        if (mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (h > mc.level.getMinBuildHeight() + 1) {
                y = h + 1.5;
            }
        }
        if (Double.isNaN(y)) {
            jp.revati.skyabyss.world.continent.ContinentMap map = jp.revati.skyabyss.world.continent.ContinentMap.get();
            y = map.inside(x, z) ? Math.max(map.surface(x, z), jp.revati.skyabyss.world.continent.ContinentChunkGenerator.SEA_LEVEL) + 1.5 : mc.player.getY() + 1.5;
        }
        ObjectiveMarker.Placement p = ObjectiveMarker.place(g, mc, new Vec3(x + 0.5, y, z + 0.5), 0.6f, 3.0);
        if (p != null) {
            ObjectiveMarker.show(g, mc, p.withLabel(name + "  " + p.label()), rgb, false);
        }
    }
}
