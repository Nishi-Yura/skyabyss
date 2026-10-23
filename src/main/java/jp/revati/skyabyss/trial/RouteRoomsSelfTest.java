package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.trial.Trial.Def;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// 順路の部屋が分かれているかの自己テスト (docs/design/32 の 2.5。段の名前は "route rooms")。四体の背で、封じ (RouteSeals) を壁として歩く:
//  - 到着の台から、どの試練の入口の休み場・中ボスの前室・心臓の前室にも歩いて着かない (入口の箱から先へ歩けない)
//  - 原型の試練の入口の休み場から、ほかの試練の休み場・心臓の前室・到着の台に歩いて着かない (中ボスの前室はその原型の中のものだけ着いてよい)
// 歩き方は背の監査と同じ (BackReach)。封じでない戸は開いたものとして歩く (仕掛けの戸は越えられるものとして、それでも分かれていること)
public final class RouteRoomsSelfTest {
    private RouteRoomsSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("route rooms", 10, RouteRoomsSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        int checked = 0;
        int seals = 0;
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                jp.revati.skyabyss.dev.StructureDump.ensureBack(server, switch (titan) {
                    case Titans.STONE_WHALE -> "titan_back";
                    case Titans.TIDE_SHELL -> "tide_back";
                    case Titans.THUNDER_WING -> "storm_back";
                    default -> "ember_back";
                });
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null) {
                    continue;
                }
                int n = RouteSeals.count(back);
                seals += n;
                if (n == 0) {
                    f.add(titan + ": no route seals were placed");
                    continue;
                }
                // 部屋の一覧: 原型の入口の休み場 (試練ごと)、中ボスの前室 (その原型の中)、心臓の前室
                Map<String, BlockPos> rooms = new LinkedHashMap<>();
                Map<String, String> owner = new LinkedHashMap<>();
                boolean raid = jp.revati.skyabyss.trial.raid.RaidRooms.active(titan);
                for (Def d : TrialCatalog.originals(titan)) {
                    if (jp.revati.skyabyss.trial.raid.RaidRooms.isRaid(d.id())) {
                        continue; // レイドの部屋は背の上ではなく区画の写しに建つ (運ばれて入る。背の上の休み場は使わない)
                    }
                    String rest = TrialRoute.entryRest(titan, d, null);
                    BlockPos at = rest == null ? null : TrialRoute.spawnOf(back, titan, rest);
                    if (at != null) {
                        rooms.put(d.id(), at);
                        owner.put(d.id(), d.id());
                    }
                }
                String midRest = TrialMidBoss.restOf(titan);
                BlockPos mid = midRest == null ? null : TrialRoute.spawnOf(back, titan, midRest);
                if (mid != null) {
                    rooms.put("mid", mid);
                    owner.put("mid", TrialMidBoss.hostOf(titan));
                }
                BlockPos heart = TrialRoute.spawnOf(back, titan, TrialRoute.HEART_REST);
                if (heart != null) {
                    rooms.put("heart", heart);
                    owner.put("heart", "heart");
                }
                BlockPos arrival = jp.revati.skyabyss.titan.end.ReturnBeacons.arrivalOf(titan).above();
                // 到着の台から
                checked += reachNone(f, back, titan, "arrival", arrival, rooms, null);
                // レイドの順路: 中ボスの前室から心臓の前室へは歩けない (倒すと運ばれる)
                if (raid && mid != null && heart != null) {
                    checked += reachNone(f, back, titan, "mid", mid, Map.of("heart", heart), null);
                }
                // 原型の入口から
                for (Map.Entry<String, BlockPos> e : rooms.entrySet()) {
                    if (e.getKey().equals("mid") || e.getKey().equals("heart")) {
                        continue;
                    }
                    Map<String, BlockPos> others = new LinkedHashMap<>();
                    for (Map.Entry<String, BlockPos> o : rooms.entrySet()) {
                        if (!o.getKey().equals(e.getKey()) && !e.getKey().equals(owner.get(o.getKey()))) {
                            others.put(o.getKey(), o.getValue());
                        }
                    }
                    others.put("arrival", arrival);
                    checked += reachNone(f, back, titan, e.getKey(), e.getValue(), others, null);
                }
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST route rooms crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST route rooms PASSED ({} walks: the trials, the mid-boss and heart rooms and the arrival do not connect on foot; {} seal blocks)", checked, seals);
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST route rooms FAILED: {}", s);
            }
        }
    }

    // from から歩いて、goals のどれにも着かないこと。歩いた数 (1)
    private static int reachNone(List<String> f, ServerLevel back, String titan, String name, BlockPos from, Map<String, BlockPos> goals, Object unused) {
        BackReach.Spec base = specOf(back, titan);
        if (base == null) {
            return 0;
        }
        BackReach.Spec spec = base.narrowed(titan + " from " + name, base.bounds, from, goals);
        spec.solidGate = p -> RouteSeals.isSeal(back, p);
        BackReach.Report rep = BackReach.reachGoals(back, spec);
        List<String> missed = new ArrayList<>();
        for (String s : rep.failures) {
            missed.add(s);
        }
        for (String g : goals.keySet()) {
            boolean unreached = false;
            for (String s : missed) {
                if (s.contains("'" + g + "'")) {
                    unreached = true;
                }
            }
            if (!unreached) {
                f.add(titan + ": from " + name + " " + from.toShortString() + " you can walk to " + g + " " + goals.get(g).toShortString());
            }
        }
        return 1;
    }

    private static BackReach.Spec specOf(ServerLevel back, String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> jp.revati.skyabyss.titan.dungeon.TitanBackAudit.spec();
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.back.TideBackAudit.spec();
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackAudit.spec(back);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBackAudit.spec(back);
            default -> null;
        };
    }
}
