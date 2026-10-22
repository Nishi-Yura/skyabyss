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

// 試練の一覧 (docs/design/29 の 3 章のうち、作ってあるもの)。今は 原型 12 (四体 × 三つ) と、岩鯨の新作 8 (W02・W03・W04・W06・W07・W10・W11・W12)・潮殻の新作 4 (T02・T06・T07・T10)・余燼の新作 4 (E02・E06・E07・E10)。
// 引き方 (プール) を動かすのは「枠の中に作ってある試練が二つ以上ある巨神」だけ (いまは岩鯨・潮殻・余燼)。ほかの二体は、引く数が足りるまで
// 原型の三つのまま (poolActive)。
public final class TrialCatalog {
    private TrialCatalog() {
    }

    private static final Map<String, List<Def>> BY_TITAN = new LinkedHashMap<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();

    // 岩鯨の新作の修飾の口の名前 (lang の trial.skyabyss.mod.<試練>.<種類>.<名前>)
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
    // 雷翼の新作 (P3。第二の渡り場 = StormDeck2): S04・S07・S08・S11・S12
    public static final List<String> S04_NUMBERS = List.of("gust_gap", "gust_blow");
    public static final List<String> S04_HAZARDS = List.of("spark_moth", "sky_bolt");
    public static final List<String> S04_GOALS = List.of("no_fall");
    public static final List<String> S07_NUMBERS = List.of("mirror_flips", "beam_hold");
    public static final List<String> S07_HAZARDS = List.of("spark_moth", "sky_bolt");
    public static final List<String> S07_GOALS = List.of("few_flips");
    public static final List<String> S08_NUMBERS = List.of("valve_turns", "valve_hold");
    public static final List<String> S08_HAZARDS = List.of("bellows_leak", "spark_moth");
    public static final List<String> S08_GOALS = List.of("few_turns");
    public static final List<String> S11_NUMBERS = List.of("raids", "cage_speed");
    public static final List<String> S11_HAZARDS = List.of("double_bolt", "sky_bolt");
    public static final List<String> S11_GOALS = List.of("cage_whole");
    public static final List<String> S12_NUMBERS = List.of("pad_swap", "feather_storm");
    public static final List<String> S12_HAZARDS = List.of("crosswind", "sky_bolt");
    public static final List<String> S12_GOALS = List.of("no_fall");
    // P2: 岩鯨の新作 4 (W03・W04・W11・W12)
    public static final List<String> W03_NUMBERS = List.of("flow", "turn");
    public static final List<String> W03_HAZARDS = List.of("surge");
    public static final List<String> W03_GOALS = List.of("no_fall");
    public static final List<String> W04_NUMBERS = List.of("vent_period", "vent_burst");
    public static final List<String> W04_HAZARDS = List.of("rockfall");
    public static final List<String> W04_GOALS = List.of("no_fall");
    public static final List<String> W11_NUMBERS = List.of("raids", "coffin_speed");
    public static final List<String> W11_HAZARDS = List.of("rockfall");
    public static final List<String> W11_GOALS = List.of("no_scratch");
    public static final List<String> W12_NUMBERS = List.of("foes", "warning");
    public static final List<String> W12_HAZARDS = List.of("rockfall");
    public static final List<String> W12_GOALS = List.of("no_fall");
    // P2: 潮殻の新作 4 (T02・T06・T07・T10)
    public static final List<String> T02_NUMBERS = List.of("overlap", "tide_speed");
    public static final List<String> T02_HAZARDS = List.of("eddy");
    public static final List<String> T02_GOALS = List.of("no_swim");
    public static final List<String> T06_NUMBERS = List.of("chorus", "mural");
    public static final List<String> T06_HAZARDS = List.of("swell");
    public static final List<String> T06_GOALS = List.of("no_murk");
    public static final List<String> T07_NUMBERS = List.of("pulls", "hold");
    public static final List<String> T07_HAZARDS = List.of("puffers");
    public static final List<String> T07_GOALS = List.of("no_reset");
    public static final List<String> T10_NUMBERS = List.of("foes", "warning");
    public static final List<String> T10_HAZARDS = List.of("crash");
    public static final List<String> T10_GOALS = List.of("no_wet");

    // P3: 岩鯨 W08・潮殻 T03・T04・T08・T11・T12 (docs/design/29 の 3.7)
    public static final List<String> W08_NUMBERS = List.of("pieces", "beams");
    public static final List<String> W08_HAZARDS = List.of("draft");
    public static final List<String> W08_GOALS = List.of("few_moves");
    public static final List<String> T03_NUMBERS = List.of("jelly_sink", "sweep");
    public static final List<String> T03_HAZARDS = List.of("minnows");
    public static final List<String> T03_GOALS = List.of("no_dunk");
    public static final List<String> T04_NUMBERS = List.of("current", "turn");
    public static final List<String> T04_HAZARDS = List.of("eddies");
    public static final List<String> T04_GOALS = List.of("no_wash");
    public static final List<String> T08_NUMBERS = List.of("specimens", "plaque");
    public static final List<String> T08_HAZARDS = List.of("tideswap");
    public static final List<String> T08_GOALS = List.of("no_return");
    public static final List<String> T11_NUMBERS = List.of("clam_raids", "flood");
    public static final List<String> T11_HAZARDS = List.of("crash");
    public static final List<String> T11_GOALS = List.of("no_chip");
    public static final List<String> T12_NUMBERS = List.of("brood", "keeper_hp");
    public static final List<String> T12_HAZARDS = List.of("crash");
    public static final List<String> T12_GOALS = List.of("no_crack");

    // 余燼の新作の修飾の口の名前
    public static final List<String> E02_NUMBERS = List.of("vent_period", "vent_warn");
    public static final List<String> E02_HAZARDS = List.of("gust", "moths");
    public static final List<String> E02_GOALS = List.of("no_burn");
    public static final List<String> E06_NUMBERS = List.of("flues", "rounds");
    public static final List<String> E06_HAZARDS = List.of("gust");
    public static final List<String> E06_GOALS = List.of("no_mistake");
    public static final List<String> E07_NUMBERS = List.of("grace", "hold");
    public static final List<String> E07_HAZARDS = List.of("ash");
    public static final List<String> E07_GOALS = List.of("no_lapse");
    public static final List<String> E10_NUMBERS = List.of("bearers", "waves");
    public static final List<String> E10_HAZARDS = List.of("vents");
    public static final List<String> E10_GOALS = List.of("no_scorch");
    // 余燼の新作 (P3)。E03 鎖橋・E04 熾の道・E08 鋳型・E11 荷車・E12 巣
    public static final List<String> E03_NUMBERS = List.of("hot_time", "cool_time");
    public static final List<String> E03_HAZARDS = List.of("moths");
    public static final List<String> E03_GOALS = List.of("no_burn");
    public static final List<String> E04_NUMBERS = List.of("gust_period", "pools");
    public static final List<String> E04_HAZARDS = List.of("gust");
    public static final List<String> E04_GOALS = List.of("no_douse");
    public static final List<String> E08_NUMBERS = List.of("steps", "show_speed");
    public static final List<String> E08_HAZARDS = List.of("steam");
    public static final List<String> E08_GOALS = List.of("one_pour");
    public static final List<String> E11_NUMBERS = List.of("raids", "push_speed");
    public static final List<String> E11_HAZARDS = List.of("moths");
    public static final List<String> E11_GOALS = List.of("no_dent");
    public static final List<String> E12_NUMBERS = List.of("rollers", "waves");
    public static final List<String> E12_HAZARDS = List.of("vents");
    public static final List<String> E12_GOALS = List.of("no_vent");

    static {
        String whale = Titans.STONE_WHALE;
        String tide = Titans.TIDE_SHELL;
        String storm = Titans.THUNDER_WING;
        String ember = Titans.EMBER;
        // ---- 岩鯨 (歩 W01・W03・W04 / 智 W05・W06・W07 / 闘 W09・W10)
        add(new Def("W01", whale, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("W03", whale, Slot.WALK, Kind.CROSS, 2, true, false,
                new Space(3, W03_NUMBERS, 0, W03_HAZARDS, W03_GOALS, List.of())));
        add(new Def("W04", whale, Slot.WALK, Kind.BEAT, 3, true, false,
                new Space(3, W04_NUMBERS, 0, W04_HAZARDS, W04_GOALS, List.of())));
        add(new Def("W05", whale, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("W06", whale, Slot.WIT, Kind.SOLVE, 1, false, false,
                new Space(3, W06_NUMBERS, 0, List.of(), W06_GOALS, List.of())));
        add(new Def("W07", whale, Slot.WIT, Kind.TOGETHER, 2, false, false,
                new Space(3, W07_NUMBERS, 0, W07_HAZARDS, W07_GOALS, List.of())));
        add(new Def("W08", whale, Slot.WIT, Kind.SOLVE, 3, false, false,
                new Space(3, W08_NUMBERS, 0, W08_HAZARDS, W08_GOALS, List.of())));
        add(new Def("W09", whale, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        add(new Def("W10", whale, Slot.FIGHT, Kind.FIGHT, 2, false, false,
                new Space(3, W10_NUMBERS, 2, W10_HAZARDS, W10_GOALS, List.of())));
        add(new Def("W11", whale, Slot.FIGHT, Kind.ESCORT, 2, true, false,
                new Space(3, W11_NUMBERS, 2, W11_HAZARDS, W11_GOALS, List.of())));
        add(new Def("W12", whale, Slot.FIGHT, Kind.FIGHT, 3, true, false,
                new Space(3, W12_NUMBERS, 2, W12_HAZARDS, W12_GOALS, List.of())));
        // ---- 潮殻 (西 T01 歩 / 東 T05 智 / 前 T09 闘)
        add(new Def("T01", tide, Slot.WALK, Kind.CARRY, 1, true, true, Space.EMPTY));
        add(new Def("T02", tide, Slot.WALK, Kind.BEAT, 2, true, false,
                new Space(3, T02_NUMBERS, 0, T02_HAZARDS, T02_GOALS, List.of())));
        add(new Def("T03", tide, Slot.WALK, Kind.CROSS, 2, false, false,
                new Space(3, T03_NUMBERS, 0, T03_HAZARDS, T03_GOALS, List.of())));
        add(new Def("T04", tide, Slot.WALK, Kind.BEAT, 3, false, false,
                new Space(3, T04_NUMBERS, 0, T04_HAZARDS, T04_GOALS, List.of())));
        add(new Def("T05", tide, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("T06", tide, Slot.WIT, Kind.SOLVE, 1, false, false,
                new Space(1, T06_NUMBERS, 0, T06_HAZARDS, T06_GOALS, List.of())));
        add(new Def("T07", tide, Slot.WIT, Kind.TOGETHER, 2, false, false,
                new Space(3, T07_NUMBERS, 0, T07_HAZARDS, T07_GOALS, List.of())));
        add(new Def("T08", tide, Slot.WIT, Kind.SOLVE, 3, false, false,
                new Space(3, T08_NUMBERS, 0, T08_HAZARDS, T08_GOALS, List.of())));
        add(new Def("T09", tide, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        add(new Def("T10", tide, Slot.FIGHT, Kind.FIGHT, 2, true, false,
                new Space(3, T10_NUMBERS, 2, T10_HAZARDS, T10_GOALS, List.of())));
        add(new Def("T11", tide, Slot.FIGHT, Kind.ESCORT, 2, false, false,
                new Space(3, T11_NUMBERS, 2, T11_HAZARDS, T11_GOALS, List.of())));
        add(new Def("T12", tide, Slot.FIGHT, Kind.FIGHT, 3, false, false,
                new Space(3, T12_NUMBERS, 2, T12_HAZARDS, T12_GOALS, List.of())));
        // ---- 雷翼 (西 S01 歩 / 東 S05 智 / 前 S06 智。闘の枠は初回は無い)
        add(new Def("S01", storm, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("S05", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("S06", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        // ---- 雷翼の新作 4 (P2。尾の上の試練の甲板 = StormDeck): 歩 S02・S03、闘 S09・S10
        add(new Def("S02", storm, Slot.WALK, Kind.BEAT, 2, false, false, new Space(3, S02_NUMBERS, 0, S02_HAZARDS, S02_GOALS, List.of())));
        add(new Def("S03", storm, Slot.WALK, Kind.BEAT, 2, false, false, new Space(3, S03_NUMBERS, 0, S03_HAZARDS, S03_GOALS, List.of())));
        add(new Def("S09", storm, Slot.FIGHT, Kind.FIGHT, 1, true, false, new Space(3, S09_NUMBERS, 2, S09_HAZARDS, S09_GOALS, List.of())));
        add(new Def("S10", storm, Slot.FIGHT, Kind.FIGHT, 2, false, false, new Space(3, S10_NUMBERS, 2, S10_HAZARDS, S10_GOALS, List.of())));
        // ---- 雷翼の新作 5 (P3。第二の渡り場 = StormDeck2): 歩 S04、智 S07・S08、闘 S11・S12
        add(new Def("S04", storm, Slot.WALK, Kind.CROSS, 3, false, false, new Space(3, S04_NUMBERS, 0, S04_HAZARDS, S04_GOALS, List.of())));
        add(new Def("S07", storm, Slot.WIT, Kind.TOGETHER, 2, true, false, new Space(3, S07_NUMBERS, 0, S07_HAZARDS, S07_GOALS, List.of())));
        add(new Def("S08", storm, Slot.WIT, Kind.SOLVE, 3, false, false, new Space(3, S08_NUMBERS, 0, S08_HAZARDS, S08_GOALS, List.of())));
        add(new Def("S11", storm, Slot.FIGHT, Kind.ESCORT, 2, false, false, new Space(3, S11_NUMBERS, 2, S11_HAZARDS, S11_GOALS, List.of())));
        add(new Def("S12", storm, Slot.FIGHT, Kind.FIGHT, 3, true, false, new Space(3, S12_NUMBERS, 2, S12_HAZARDS, S12_GOALS, List.of())));
        // ---- 余燼 (西 E01 歩 / 東 E05 智 / 南 E09 闘)
        add(new Def("E01", ember, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("E02", ember, Slot.WALK, Kind.BEAT, 2, false, false,
                new Space(3, E02_NUMBERS, 0, E02_HAZARDS, E02_GOALS, List.of())));
        add(new Def("E05", ember, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("E06", ember, Slot.WIT, Kind.SOLVE, 1, false, false,
                new Space(3, E06_NUMBERS, 0, E06_HAZARDS, E06_GOALS, List.of())));
        add(new Def("E07", ember, Slot.WIT, Kind.TOGETHER, 2, false, false,
                new Space(3, E07_NUMBERS, 0, E07_HAZARDS, E07_GOALS, List.of())));
        add(new Def("E09", ember, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        add(new Def("E10", ember, Slot.FIGHT, Kind.FIGHT, 2, false, false,
                new Space(3, E10_NUMBERS, 2, E10_HAZARDS, E10_GOALS, List.of())));
        // ---- 余燼の新作 5 (P3): 歩 E03・E04、智 E08、闘 E11・E12。★ は 2 / 3 / 3 / 2 / 3 (合わせて ★1:4・★2:5・★3:3)
        add(new Def("E03", ember, Slot.WALK, Kind.CROSS, 2, false, false,
                new Space(3, E03_NUMBERS, 0, E03_HAZARDS, E03_GOALS, List.of())));
        add(new Def("E04", ember, Slot.WALK, Kind.CARRY, 3, false, false,
                new Space(3, E04_NUMBERS, 0, E04_HAZARDS, E04_GOALS, List.of())));
        add(new Def("E08", ember, Slot.WIT, Kind.SOLVE, 3, false, false,
                new Space(3, E08_NUMBERS, 0, E08_HAZARDS, E08_GOALS, List.of())));
        add(new Def("E11", ember, Slot.FIGHT, Kind.ESCORT, 2, false, false,
                new Space(3, E11_NUMBERS, 2, E11_HAZARDS, E11_GOALS, List.of())));
        add(new Def("E12", ember, Slot.FIGHT, Kind.FIGHT, 3, false, false,
                new Space(3, E12_NUMBERS, 2, E12_HAZARDS, E12_GOALS, List.of())));
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
        return Titans.STONE_WHALE.equals(titan) && jp.revati.skyabyss.titan.dungeon.WhaleDeck.placed()
                || Titans.TIDE_SHELL.equals(titan) && jp.revati.skyabyss.titan.tide.back.TideDeck.placed()
                || Titans.THUNDER_WING.equals(titan) && jp.revati.skyabyss.titan.storm.back.StormDeck.placed()
                || Titans.EMBER.equals(titan) && jp.revati.skyabyss.titan.ember.back.EmberDeck.placed();
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
