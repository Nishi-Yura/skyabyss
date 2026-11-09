package jp.revati.skyabyss.client.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.map.MapMarks;
import net.minecraft.client.Minecraft;

// 旅の地図の手もとの設定 (2026-11-09 の声「マップに見どころも映すと、画面がいっぱいいっぱいになる。見どころのオンオフのような、
// マップに移す要素を切り替えれるボタンをマップに用意して」「マップにピンさせる様にしてほしい」)。
//   載せる物の組 (Layer) の入り切り: どの世界でも同じ
//   ピン: 世界ごと (一人遊びは保存のフォルダの名前、サーバーは宛先)。最大 MAX_PINS。地図の上でホイールを押す・Shift + 左クリックで置く・消す。
//         ふだんの画面にも標で出し、パーティーの仲間にも見せる (PinMarkers・party/PartyPins)
// 置き場はゲームのフォルダの config/skyabyss-map.properties
public final class MapPrefs {
    public enum Layer {
        // 町・野営地・碑 / 見どころ・章の場所 / 祠と巨神 / 名のある強敵 / 巡り路と組合の旗 / 釣り場 / 道 / ピン
        TOWNS, SIGHTS, SHRINES, FOES, CIRCUITS, FISHING, ROADS, PINS;

        public String key() {
            return "gui.skyabyss.map.layer." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Pin(int x, int z) {
    }

    public static final int MAX_PINS = 9;
    private static final String FILE = "config/skyabyss-map.properties";

    private static boolean loaded;
    private static int hidden;
    private static final Map<String, List<Pin>> PINS = new LinkedHashMap<>();

    private MapPrefs() {
    }

    public static Layer layerOf(MapMarks.Mark m) {
        return switch (m.kind()) {
            case TOWN, CAMP, SPAWN -> Layer.TOWNS;
            case WAYMARK -> m.extra().startsWith("poi") ? Layer.SIGHTS : Layer.TOWNS;
            case SITE, FOOTPRINT, RIBS -> Layer.SIGHTS;
            case SHRINE, TITAN -> Layer.SHRINES;
            case FOE -> Layer.FOES;
            case LOOTCAMP, TERRITORY -> Layer.CIRCUITS;
            case FISHING -> Layer.FISHING;
        };
    }

    public static boolean shows(Layer l) {
        load();
        return (hidden & 1 << l.ordinal()) == 0;
    }

    public static void toggle(Layer l) {
        load();
        hidden ^= 1 << l.ordinal();
        save();
    }

    // ---------------------------------------------------------------- ピン
    static String world() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return "sp:" + mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName();
        }
        return mc.getCurrentServer() != null ? "mp:" + mc.getCurrentServer().ip : "unknown";
    }

    public static List<Pin> pins() {
        load();
        return PINS.getOrDefault(world(), List.of());
    }

    // (x, z) にピンを置く (いっぱいなら古いものから消す)
    public static void add(int x, int z) {
        load();
        List<Pin> list = new ArrayList<>(pins());
        if (list.size() >= MAX_PINS) {
            list.remove(0);
        }
        list.add(new Pin(x, z));
        PINS.put(world(), list);
        save();
        PinMarkers.sendMine();
    }

    public static void remove(Pin p) {
        load();
        List<Pin> list = new ArrayList<>(pins());
        list.remove(p);
        PINS.put(world(), list);
        save();
        PinMarkers.sendMine();
    }

    // ---------------------------------------------------------------- 読み書き
    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FILE);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = path();
        if (!Files.exists(f)) {
            return;
        }
        Properties props = new Properties();
        try (var r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            SkyAbyss.LOGGER.warn("Map prefs: could not read {}", f, e);
            return;
        }
        try {
            hidden = Integer.parseInt(props.getProperty("hidden", "0"));
        } catch (NumberFormatException e) {
            hidden = 0;
        }
        for (String k : props.stringPropertyNames()) {
            if (!k.startsWith("pins.")) {
                continue;
            }
            List<Pin> list = new ArrayList<>();
            for (String part : props.getProperty(k).split(";")) {
                String[] xz = part.split(",");
                if (xz.length == 2) {
                    try {
                        list.add(new Pin(Integer.parseInt(xz[0].trim()), Integer.parseInt(xz[1].trim())));
                    } catch (NumberFormatException ignored) {
                        // 壊れた行は飛ばす
                    }
                }
            }
            PINS.put(k.substring(5), list);
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty("hidden", Integer.toString(hidden));
        for (var e : PINS.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Pin p : e.getValue()) {
                if (!sb.isEmpty()) {
                    sb.append(';');
                }
                sb.append(p.x()).append(',').append(p.z());
            }
            props.setProperty("pins." + e.getKey(), sb.toString());
        }
        Path f = path();
        try {
            Files.createDirectories(f.getParent());
            try (var w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                props.store(w, "Sky Abyss travel map");
            }
        } catch (IOException e) {
            SkyAbyss.LOGGER.warn("Map prefs: could not write {}", f, e);
        }
    }
}
