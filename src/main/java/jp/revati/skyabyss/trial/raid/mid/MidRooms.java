package jp.revati.skyabyss.trial.raid.mid;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.raid.RaidRoom;

// 順路の中ボスの部屋の一覧 (巨神ごとに一つ。区画の段 4 の場所に建てる。docs/design/32 の 2.1)
public final class MidRooms {
    private MidRooms() {
    }

    public static final List<String> IDS = List.of(WhaleMidRoom.ID, TideMidRoom.ID, StormMidRoom.ID, EmberMidRoom.ID);

    // その巨神の中ボスの部屋の id (なければ null)
    @Nullable
    public static String idOf(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE, "whale" -> WhaleMidRoom.ID;
            case Titans.TIDE_SHELL, "tide" -> TideMidRoom.ID;
            case Titans.THUNDER_WING, "storm" -> StormMidRoom.ID;
            case Titans.EMBER -> EmberMidRoom.ID;
            default -> null;
        };
    }

    public static boolean isMid(String id) {
        return IDS.contains(id);
    }

    @Nullable
    public static RaidRoom create(String titan) {
        Supplier<RaidRoom> f = switch (titan) {
            case Titans.STONE_WHALE, "whale" -> WhaleMidRoom::new;
            case Titans.TIDE_SHELL, "tide" -> TideMidRoom::new;
            case Titans.THUNDER_WING, "storm" -> StormMidRoom::new;
            case Titans.EMBER -> EmberMidRoom::new;
            default -> null;
        };
        return f == null ? null : f.get();
    }
}
