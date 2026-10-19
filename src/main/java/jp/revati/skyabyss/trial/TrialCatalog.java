package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Kind;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.Trial.Space;

// 試練の一覧 (docs/design/29 の 3 章のうち、作ってあるもの)。今は 原型 12 (四体 × 三つ) と、岩鯨の新作 4 (W02・W06・W07・W10)。
// 引き方 (プール) を動かすのは「枠の中に作ってある試練が二つ以上ある巨神」だけ (いまは岩鯨)。ほかの三体は、引く数が足りるまで
// 原型の三つのまま (poolActive)。
public final class TrialCatalog {
    private TrialCatalog() {
    }

    private static final Map<String, List<Def>> BY_TITAN = new LinkedHashMap<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();

    // 岩鯨の新作の修飾の口の名前 (lang の trial.skyabyss.mod.<試練>.<種類>.<名前>)
    public static final List<String> W02_NUMBERS = List.of("period", "window");
    public static final List<String> W02_HAZARDS = List.of("gust", "echo");
    public static final List<String> W02_GOALS = List.of("no_fall");
    public static final List<String> W06_NUMBERS = List.of("length", "memory");
    public static final List<String> W06_GOALS = List.of("no_mistake");
    public static final List<String> W07_NUMBERS = List.of("grace", "hold");
    public static final List<String> W07_HAZARDS = List.of("tide");
    public static final List<String> W07_GOALS = List.of("no_lapse");
    public static final List<String> W10_NUMBERS = List.of("gunners", "waves");
    public static final List<String> W10_HAZARDS = List.of("rockfall");
    public static final List<String> W10_GOALS = List.of("no_hit");
    // 雷翼の新作 (P2) の修飾の口の名前
    public static final List<String> S02_NUMBERS = List.of("sac_period", "sac_window");
    public static final List<String> S02_HAZARDS = List.of("crosswind", "spark_moth");
    public static final List<String> S02_GOALS = List.of("no_fall");
    public static final List<String> S03_NUMBERS = List.of("bolt_tempo", "bolt_shapes");
    public static final List<String> S03_HAZARDS = List.of("ground_arc", "spark_moth");
    public static final List<String> S03_GOALS = List.of("no_hit");
    public static final List<String> S09_NUMBERS = List.of("callers", "nest_waves");
    public static final List<String> S09_HAZARDS = List.of("overload", "sky_bolt");
    public static final List<String> S09_GOALS = List.of("no_hit");
    public static final List<String> S10_NUMBERS = List.of("wardens", "watch_posts");
    public static final List<String> S10_HAZARDS = List.of("crosswind", "sky_bolt");
    public static final List<String> S10_GOALS = List.of("no_hit");

    static {
        String whale = Titans.STONE_WHALE;
        String tide = Titans.TIDE_SHELL;
        String storm = Titans.THUNDER_WING;
        String ember = Titans.EMBER;
        // ---- 岩鯨 (歩 W01・W02 / 智 W05・W06・W07 / 闘 W09・W10)
        add(new Def("W01", whale, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("W02", whale, Slot.WALK, Kind.BEAT, 2, false, false,
                new Space(3, W02_NUMBERS, 0, W02_HAZARDS, W02_GOALS, List.of())));
        add(new Def("W05", whale, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("W06", whale, Slot.WIT, Kind.SOLVE, 1, false, false,
                new Space(3, W06_NUMBERS, 0, List.of(), W06_GOALS, List.of())));
        add(new Def("W07", whale, Slot.WIT, Kind.TOGETHER, 2, false, false,
                new Space(3, W07_NUMBERS, 0, W07_HAZARDS, W07_GOALS, List.of())));
        add(new Def("W09", whale, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        add(new Def("W10", whale, Slot.FIGHT, Kind.FIGHT, 2, false, false,
                new Space(3, W10_NUMBERS, 2, W10_HAZARDS, W10_GOALS, List.of())));
        // ---- 潮殻 (西 T01 歩 / 東 T05 智 / 前 T09 闘)
        add(new Def("T01", tide, Slot.WALK, Kind.CARRY, 1, true, true, Space.EMPTY));
        add(new Def("T05", tide, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("T09", tide, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        // ---- 雷翼 (西 S01 歩 / 東 S05 智 / 前 S06 智。闘の枠は初回は無い)
        add(new Def("S01", storm, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("S05", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("S06", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        // ---- 雷翼の新作 4 (P2。尾の上の試練の甲板 = StormDeck): 歩 S02・S03、闘 S09・S10
        add(new Def("S02", storm, Slot.WALK, Kind.BEAT, 2, false, false, new Space(3, S02_NUMBERS, 0, S02_HAZARDS, S02_GOALS, List.of())));
        add(new Def("S03", storm, Slot.WALK, Kind.BEAT, 2, false, false, new Space(3, S03_NUMBERS, 0, S03_HAZARDS, S03_GOALS, List.of())));
        add(new Def("S09", storm, Slot.FIGHT, Kind.FIGHT, 1, true, false, new Space(3, S09_NUMBERS, 2, S09_HAZARDS, S09_GOALS, List.of())));
        add(new Def("S10", storm, Slot.FIGHT, Kind.FIGHT, 2, false, false, new Space(3, S10_NUMBERS, 2, S10_HAZARDS, S10_GOALS, List.of())));
        // ---- 余燼 (西 E01 歩 / 東 E05 智 / 南 E09 闘)
        add(new Def("E01", ember, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("E05", ember, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("E09", ember, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
    }

    private static void add(Def def) {
        BY_TITAN.computeIfAbsent(def.titan(), k -> new ArrayList<>()).add(def);
        BY_ID.put(def.id(), def);
    }

    public static List<String> titans() {
        return List.copyOf(BY_TITAN.keySet());
    }

    public static List<Def> of(String titan) {
        return BY_TITAN.getOrDefault(titan, List.of());
    }

    @Nullable
    public static Def get(String id) {
        return BY_ID.get(id);
    }

    public static List<Def> all() {
        return List.copyOf(BY_ID.values());
    }

    // 原型の三つ (初回の周に必ずこれ)。並びは 歩・智・闘 (雷翼は 歩・智・智)
    public static List<Def> originals(String titan) {
        List<Def> out = new ArrayList<>();
        for (Slot s : Slot.values()) {
            for (Def d : of(titan)) {
                if (d.original() && d.slot() == s) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    // 枠に作ってある試練
    public static List<Def> inSlot(String titan, Slot slot) {
        List<Def> out = new ArrayList<>();
        for (Def d : of(titan)) {
            if (d.slot() == slot) {
                out.add(d);
            }
        }
        return out;
    }

    // 引き方 (プール) を動かす巨神か。三つの枠すべてに、作ってある試練が二つ以上ある巨神だけ (そうでなければ周回しても原型のまま)
    public static boolean poolActive(String titan) {
        if (!deckPlaced(titan)) {
            return false;
        }
        for (Slot s : Slot.values()) {
            if (inSlot(titan, s).size() < 2) {
                return false;
            }
        }
        return true;
    }

    // 新作のセルを背に置いてあるか (岩鯨の試練の階。体の作り直しが済むまで置かない。WhaleDeck.placed)。ほかの巨神は新作がまだ無いので false
    public static boolean deckPlaced(String titan) {
        if (Titans.THUNDER_WING.equals(titan)) {
            return jp.revati.skyabyss.titan.storm.back.StormDeck.placed();
        }
        return Titans.STONE_WHALE.equals(titan) && jp.revati.skyabyss.titan.dungeon.WhaleDeck.placed();
    }

    // 石の合図 (TrialSteles の titan と trial の文字) から、その石の原型の試練。なければ null
    @Nullable
    public static Def legacyOfStone(String steleTitan, String trial) {
        String id = switch (steleTitan) {
            case "whale" -> switch (trial) {
                case "0" -> "W01";
                case "1" -> "W09";
                case "2" -> "W05";
                default -> null;
            };
            case "tide" -> switch (trial) {
                case "west" -> "T01";
                case "east" -> "T05";
                case "front" -> "T09";
                default -> null;
            };
            case "storm" -> switch (trial) {
                case "west" -> "S01";
                case "east" -> "S05";
                case "front" -> "S06";
                default -> null;
            };
            case "ember" -> switch (trial) {
                case "west" -> "E01";
                case "east" -> "E05";
                case "south" -> "E09";
                default -> null;
            };
            default -> null;
        };
        return id == null ? null : get(id);
    }

    // 巨神の名前の短い札 (lang の trial.skyabyss.titan.<id>)
    public static String titanKey(String titan) {
        return "trial.skyabyss.titan." + titan;
    }
}
