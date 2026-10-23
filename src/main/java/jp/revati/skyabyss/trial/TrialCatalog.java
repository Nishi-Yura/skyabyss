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

// 試練の一覧。順路で遊ぶのはレイドの部屋 (trial/raid が addRaid で足す。docs/design/32 の 9 章)。
// 原型 12 (四体 × 三つ) の定義は、記録・図鑑の古い id と、中ボスの段の判定 (TrialMidBoss) のために残す (体の中は建てないので遊ばない)。
// 29 のプールの新作 (W03 など) は消した (巨神の中を建てない。32 の 2.5)
public final class TrialCatalog {
    private TrialCatalog() {
    }

    private static final Map<String, List<Def>> BY_TITAN = new LinkedHashMap<>();
    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();


    static {
        String whale = Titans.STONE_WHALE;
        String tide = Titans.TIDE_SHELL;
        String storm = Titans.THUNDER_WING;
        String ember = Titans.EMBER;
        // ---- 岩鯨 (歩 W01 / 智 W05 / 闘 W09)
        add(new Def("W01", whale, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("W05", whale, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("W09", whale, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        // ---- 潮殻 (西 T01 歩 / 東 T05 智 / 前 T09 闘)
        add(new Def("T01", tide, Slot.WALK, Kind.CARRY, 1, true, true, Space.EMPTY));
        add(new Def("T05", tide, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("T09", tide, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
        // ---- 雷翼 (西 S01 歩 / 東 S05 智 / 前 S06 智。闘の枠は初回は無い)
        add(new Def("S01", storm, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("S05", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("S06", storm, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        // ---- 余燼 (西 E01 歩 / 東 E05 智 / 南 E09 闘)
        add(new Def("E01", ember, Slot.WALK, Kind.CROSS, 1, true, true, Space.EMPTY));
        add(new Def("E05", ember, Slot.WIT, Kind.SOLVE, 1, true, true, Space.EMPTY));
        add(new Def("E09", ember, Slot.FIGHT, Kind.FIGHT, 1, false, true, Space.EMPTY));
    }

    private static void add(Def def) {
        BY_TITAN.computeIfAbsent(def.titan(), k -> new ArrayList<>()).add(def);
        BY_ID.put(def.id(), def);
    }

    // レイドの部屋の定義を足す (RaidRooms.register から。docs/design/32 の 7 章)
    public static void addRaid(Def def) {
        List<Def> list = BY_TITAN.computeIfAbsent(def.titan(), k -> new ArrayList<>());
        list.removeIf(d -> d.id().equals(def.id()));
        list.add(def);
        BY_ID.put(def.id(), def);
    }

    public static List<String> titans() {
        return List.copyOf(BY_TITAN.keySet());
    }

    public static List<Def> of(String titan) {
        jp.revati.skyabyss.trial.raid.RaidRooms.ensureLoaded();
        return BY_TITAN.getOrDefault(titan, List.of());
    }

    @Nullable
    public static Def get(String id) {
        jp.revati.skyabyss.trial.raid.RaidRooms.ensureLoaded();
        return BY_ID.get(id);
    }

    public static List<Def> all() {
        return List.copyOf(BY_ID.values());
    }

    // 原型の三つ (初回の周に必ずこれ)。並びは 歩・智・闘 (雷翼は 歩・智・智)
    public static List<Def> originals(String titan) {
        List<Def> out = new ArrayList<>();
        // レイドの巨神は、決まった三つの部屋が原型 (毎周同じ。docs/design/32 の 7 章)
        if (jp.revati.skyabyss.trial.raid.RaidRooms.active(titan)) {
            for (Slot s : Slot.values()) {
                Def d = get(jp.revati.skyabyss.trial.raid.RaidRooms.idOf(titan, s));
                if (d != null) {
                    out.add(d);
                }
            }
            return out;
        }
        for (Slot s : Slot.values()) {
            for (Def d : of(titan)) {
                if (d.original() && d.slot() == s) {
                    out.add(d);
                }
            }
        }
        return out;
    }

    // レイドの巨神の周の三つ (枠ごとに登録した部屋から seed で引く。歩・智・闘の並び)
    public static List<Def> raidDraw(String titan, long seed) {
        List<Def> out = new ArrayList<>();
        for (Slot s : Slot.values()) {
            Def d = get(jp.revati.skyabyss.trial.raid.RaidRooms.drawOf(titan, s, seed));
            if (d != null) {
                out.add(d);
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
        if (jp.revati.skyabyss.trial.raid.RaidRooms.active(titan)) {
            return false;
        }
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
