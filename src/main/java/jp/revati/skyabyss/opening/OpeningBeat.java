package jp.revati.skyabyss.opening;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

// 序盤の拍子 (docs/design/17 の 3 章、B0〜B10)。名前は 17 の 11.3節 (語りの担当が決めた ID) と、伝承の項目の名前にそろえてある
public enum OpeningBeat {
    AWAKE("awake"),
    CAMP("camp"),
    TRAIL("trail"),
    FOOTPRINTS("footprints"),
    SHADOW("shadow"),
    STORYTELLER("storyteller"),
    FOOTSTEPS("footsteps"),
    ISANA("isana"),
    BONES("bones"),
    SONG("song"),
    WHALE("whale");

    public final String id;

    OpeningBeat(String id) {
        this.id = id;
    }

    // B0 は 0、B10 は 10
    public int number() {
        return ordinal();
    }

    @Nullable
    public static OpeningBeat byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (OpeningBeat beat : values()) {
            if (beat.id.equals(key)) {
                return beat;
            }
        }
        return null;
    }
}
