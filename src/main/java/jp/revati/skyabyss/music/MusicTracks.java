package jp.revati.skyabyss.music;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// BGM の曲の一覧 (docs/design/49)。曲はどれも 1 周で頭へつながるループ (tools/gen/music)。
// 音の名前は music.<id>、ファイルは sounds/music/<id>.ogg (sounds.json で stream)
public final class MusicTracks {
    public static final List<String> ALL = List.of(
            "title", "island", "capital", "meadow",
            "town_whale", "town_tide", "town_storm", "town_ember",
            "field_whale", "field_tide", "field_storm", "field_ember",
            "back", "boss", "heart", "abyss", "moon");

    // 地上の土地の番号 (ContinentMap。gen_continent.py の REGION_IDS: meadow, start, whale, tide, storm, ember) → 曲
    public static final String[] REGION_TRACKS = {"meadow", "island", "field_whale", "field_tide", "field_storm", "field_ember"};

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SkyAbyss.MODID);

    static {
        for (String id : ALL) {
            SOUNDS.register("music." + id, () -> SoundEvent.createVariableRangeEvent(location(id)));
        }
    }

    private MusicTracks() {
    }

    public static ResourceLocation location(String id) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "music." + id);
    }

    // 地上の場所 (ContinentAreas の "capital"・"town:<町>"・"region:<番号>") → 曲。分からなければ null
    public static String forArea(String area) {
        if (area == null || area.isEmpty()) {
            return null;
        }
        if (area.equals("capital")) {
            return "capital";
        }
        if (area.startsWith("town:")) {
            String id = "town_" + area.substring(5);
            return ALL.contains(id) ? id : null;
        }
        if (area.startsWith("region:")) {
            try {
                int r = Integer.parseInt(area.substring(7));
                return r >= 0 && r < REGION_TRACKS.length ? REGION_TRACKS[r] : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
