package jp.revati.skyabyss.trial;

import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;

// 試練の枠の名前 (key)。石の合図 (TrialSteles) と同じ: 岩鯨は 0 歩 / 2 智 / 1 闘、潮殻・雷翼は west / east / front、余燼は west / east / south。
// 順路の段の「越えた」(TrialRoute.onCleared) と、クエスト・伝承の合図の名前に使う
public final class TrialRegions {
    private TrialRegions() {
    }

    // 試練の名前の一覧 (石の合図の順)
    public static List<String> keys(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE, "whale" -> List.of("0", "1", "2");
            case Titans.TIDE_SHELL, "tide", Titans.THUNDER_WING, "storm" -> List.of("west", "east", "front");
            case Titans.EMBER -> List.of("west", "east", "south");
            default -> List.of();
        };
    }

    // 進行の名前 (stone_whale…) と石の合図の名前 (whale…) の両方を受ける
    public static String signalTitan(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> "whale";
            case Titans.TIDE_SHELL -> "tide";
            case Titans.THUNDER_WING -> "storm";
            case Titans.EMBER -> "ember";
            default -> titan;
        };
    }

    // 引いた試練 def が、その巨神のどの枠 (key) か。なければ null
    @Nullable
    public static String keyOfDef(String titan, Trial.Def def, @Nullable TrialDraw.Draw draw) {
        String t = signalTitan(titan);
        return switch (t) {
            case "whale" -> String.valueOf(def.slot().stone);
            case "tide", "storm" -> switch (def.slot()) {
                case WALK -> "west";
                case WIT -> "east";
                case FIGHT -> "front";
            };
            case "ember" -> switch (def.slot()) {
                case WALK -> "west";
                case WIT -> "east";
                case FIGHT -> "south";
            };
            default -> null;
        };
    }
}
