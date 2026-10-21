package jp.revati.skyabyss.client.map;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.map.MapMarks;
import jp.revati.skyabyss.map.MapNetwork;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 旅の地図 (docs/design/33 の 4 章) のクライアント側: キー (既定 M、設定で変えられる) で開き、サーバーに印を頼む。
// 開くのはすぐ (前に届いた印で描き、返事が来たら差し替える)。開いている間は 5 秒ごとに頼み直す (巨神は動くので)。
// 巨神の子の海図 (TravelNetwork.Chart) も同じ画面で開く (TravelClient.openChart → openVoyage)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MapClient {
    public static final KeyMapping MAP_KEY = new KeyMapping("key.skyabyss.travel_map", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.MAP, "key.categories.skyabyss");
    static final int REFRESH = 100;

    @Nullable
    private static MapMarks.Sheet last;
    private static int refresh;

    private MapClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(MAP_KEY);
        }
    }

    @Nullable
    public static MapMarks.Sheet last() {
        return last;
    }

    // M で開く (地図だけ)
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new TravelMapScreen(null, last));
        request(true);
    }

    // 子の乗り場の海図が届いた: 地図の画面に重ねて開く (開いていれば差し替え)
    public static void openVoyage(TravelNetwork.Chart chart) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TravelMapScreen screen && screen.voyage()) {
            screen.chart(chart);
            return;
        }
        mc.setScreen(new TravelMapScreen(chart, last));
        request(true);
    }

    static void request(boolean open) {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new MapNetwork.Request(open));
        }
        refresh = REFRESH;
    }

    public static void receive(MapNetwork.SheetPayload payload) {
        last = payload.sheet();
        if (Minecraft.getInstance().screen instanceof TravelMapScreen screen && !screen.demo()) {
            screen.sheet(payload.sheet());
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (MAP_KEY.consumeClick()) {
            if (mc.screen == null) {
                open();
            }
        }
        if (mc.screen instanceof TravelMapScreen screen && !screen.demo() && --refresh <= 0) {
            request(false);
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        last = null;
    }

    // ---------------------------------------------------------------- 見本 (撮影の台本の open travel_map_demo。サーバーに頼まない)
    public static MapMarks.Sheet demoSheet(BlockPos at) {
        int x = at.getX();
        int z = at.getZ();
        int tx = x + 90;
        int tz = z - 140;
        List<MapMarks.Mark> m = new ArrayList<>();
        m.add(new MapMarks.Mark(MapMarks.Kind.TOWN, "town", "town", "", tx, tz, false, 0));
        m.add(new MapMarks.Mark(MapMarks.Kind.CAMP, "camp", "camp", "", x - 40, z + 30, false, 0));
        for (int i = 0; i < 4; i++) {
            m.add(new MapMarks.Mark(MapMarks.Kind.CAIRN, "cairn" + i, "cairn", "", x - 40 + (tx - x + 40) * (i + 1) / 5 + (i % 2) * 6,
                    z + 30 + (tz - z - 30) * (i + 1) / 5, false, 0));
        }
        m.add(new MapMarks.Mark(MapMarks.Kind.FOOTPRINT, "footprint0", "footprint", "", x + 10, z - 70, false, 0));
        m.add(new MapMarks.Mark(MapMarks.Kind.FOOTPRINT, "footprint1", "footprint", "", x + 46, z - 96, false, 0));
        m.add(new MapMarks.Mark(MapMarks.Kind.RIBS, "ribs", "ribs", "", tx - 260, tz + 160, false, 0x7FF4FF));
        m.add(new MapMarks.Mark(MapMarks.Kind.SITE, "s1", "ch1_lookout", "titan_whale", tx + 210, tz + 120, false, 0x7FF4FF));
        m.add(new MapMarks.Mark(MapMarks.Kind.SITE, "s2", "ch1_lost_cart", "titan_whale", tx + 150, tz - 190, false, 0x7FF4FF));
        m.add(new MapMarks.Mark(MapMarks.Kind.SITE, "s3", "ch2_beach_stele", "titan_tide", tx - 420, tz - 380, true, 0x9FE8E0));
        m.add(new MapMarks.Mark(MapMarks.Kind.WAYMARK, "w1", "", "minecraft:birch_forest", tx + 380, tz - 60, false, 0));
        m.add(new MapMarks.Mark(MapMarks.Kind.WAYMARK, "w2", "灯台の丘", "", tx - 900, tz + 700, false, 0));
        m.add(new MapMarks.Mark(MapMarks.Kind.TITAN, "t:stone_whale", "stone_whale", "", tx + 330, tz + 340, false, 0x7FF4FF));
        m.add(new MapMarks.Mark(MapMarks.Kind.TITAN, "t:tide_shell", "tide_shell", "", tx - 1500, tz - 900, false, 0x9FE8E0));
        return new MapMarks.Sheet(tx, tz, true, m);
    }

    public static TravelNetwork.Chart demoChart(BlockPos at) {
        MapMarks.Sheet s = demoSheet(at);
        int tx = s.centerX();
        int tz = s.centerZ();
        List<TravelNetwork.Dest> d = new ArrayList<>();
        d.add(new TravelNetwork.Dest("spawn", "", "", at.getX() - 40, 70, at.getZ() + 30, 0, true, 21, ""));
        d.add(new TravelNetwork.Dest("w1", "", "minecraft:birch_forest", tx + 380, 70, tz - 60, 1, true, 28, ""));
        d.add(new TravelNetwork.Dest("w2", "灯台の丘", "", tx - 900, 70, tz + 700, 1, true, 54, ""));
        d.add(new TravelNetwork.Dest("w3", "", "minecraft:desert", tx + 2600, 70, tz + 1900, 1, false, 0, ""));
        d.add(new TravelNetwork.Dest("t:tide_shell", "tide_shell", "", tx - 1500, 70, tz - 900, 2, false, 60, "absent"));
        d.add(new TravelNetwork.Dest("t:thunder_wing", "thunder_wing", "", tx + 1700, 70, tz - 1300, 2, true, 66, ""));
        return new TravelNetwork.Chart("stone_whale", true, "", at.getX(), at.getZ(), "", d);
    }
}
