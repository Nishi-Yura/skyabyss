package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
import jp.revati.skyabyss.titan.storm.back.StormDeck2;
import jp.revati.skyabyss.titan.storm.back.StormDeck.Island;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialCells;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 試練の甲板の「次の目的」(StormObjective が呼ぶ。26 の 3.3・25 の 3 章)。その人のグループが引いた新作のうち、まだ石を鳴らしていない一つへ、道しるべの光の柱を出す。
//   広間 (入口の近く) → 尾の間の螺旋 → 背の広場 → 橋 → 渡り場の戸 → 島の中 (次の島・風の穴・守り・石)
public final class StormGuides {
    private StormGuides() {
    }

    public record Target(String id, BlockPos at, float hx, float hy, float hz) {
    }

    // そのグループが引いた新作のうち、石がまだ鳴っていない最初のもの (なければ null)
    @Nullable
    public static Def unrung(ServerLevel back, UUID group) {
        if (!StormDeck.placed()) {
            return null;
        }
        TrialDraw.Draw draw = TrialData.current(back, group);
        StormProgress pr = StormProgress.of(back, group);
        for (TrialDraw.Pick p : draw.picks()) {
            Def d = p.def();
            TrialCell c = d == null ? null : TrialCells.get(d.id());
            if (c == null || !c.runtime()) {
                continue;
            }
            String key = StormStones.keyOf(draw, d);
            if (!pr.rung(StormBackState.Trial.valueOf(key.toUpperCase(java.util.Locale.ROOT)))) {
                return d;
            }
        }
        return null;
    }

    // 渡り場の戸の中心 (その試練へ入る所)
    static BlockPos gateOf(String id) {
        if (StormDeck2.isNewCell(id)) {
            return StormDeck2.gateCenter(id);
        }
        return switch (id) {
            case "S02" -> new BlockPos(-24, Y + 1, 73);
            case "S03" -> new BlockPos(24, Y + 1, 73);
            case "S10" -> new BlockPos(-15, Y + 1, 82);
            default -> new BlockPos(15, Y + 1, 82);
        };
    }

    static BlockPos stoneOf(String id) {
        return switch (id) {
            case "S04" -> StormDeck2.S04_STONE;
            case "S07" -> StormDeck2.S07_STONE;
            case "S08" -> StormDeck2.S08_STONE;
            case "S11" -> StormDeck2.S11_STONE;
            case "S12" -> StormDeck2.S12_STONE;
            case "S02" -> StormDeck.S02_STONE;
            case "S03" -> StormDeck.S03_STONE;
            case "S10" -> StormDeck.S10_STONE;
            default -> StormDeck.S09_STONE;
        };
    }

    // 橋と渡り場: 引いた試練の戸へ
    @Nullable
    public static Target atYard(ServerLevel back, ServerPlayer player) {
        Def d = unrung(back, Groups.keyOf(player));
        if (d == null) {
            return null;
        }
        BlockPos g = gateOf(d.id());
        if (StormDeck2.isNewCell(d.id())) {
            Vec3 v = player.position();
            // 第一の渡り場・橋の上からは、橋の口へ。第二の渡り場に着いたら戸へ
            if (v.z < StormDeck2.YARD.z1() - 1) {
                return v.z < StormDeck2.BRIDGE_Z1 ? new Target("storm_deck_bridge2", new BlockPos(0, Y + 1, StormDeck2.BRIDGE_Z1 + 1), 3.5f, 2f, 0.7f)
                        : new Target("storm_deck_bridge2", new BlockPos(0, Y + 1, StormDeck2.YARD.z1()), 3.5f, 2f, 0.7f);
            }
            boolean alongZ = d.id().equals("S04") || d.id().equals("S08");
            return new Target("storm_deck_gate", g, alongZ ? 0.7f : 3f, 3f, alongZ ? 3f : 0.7f);
        }
        boolean alongX = d.id().equals("S02") || d.id().equals("S03");
        return new Target("storm_deck_gate", g, alongX ? 0.7f : 3f, 3f, alongX ? 3f : 0.7f);
    }

    // 島の中: 次の島・風の穴・守り・石
    @Nullable
    public static Target inCell(ServerLevel back, ServerPlayer player) {
        Vec3 v = player.position();
        UUID group = Groups.keyOf(player);
        for (String id : new String[] {"S02", "S03", "S09", "S10", "S04", "S07", "S08", "S11", "S12"}) {
            TrialCell cell = TrialCells.get(id);
            if (cell == null || !cell.ports().area().contains(v)) {
                continue;
            }
            TrialCell.Run run = TrialRuntime.runOf(group, id);
            Def d = unrung(back, group);
            boolean weeklyOnly = d == null || !d.id().equals(id);
            if (weeklyOnly && run != null && run.cleared) {
                return stone(id);
            }
            if (run != null && run.cleared) {
                return stone(id);
            }
            return switch (id) {
                case "S02" -> s02(v);
                case "S03" -> s03(v);
                case "S10" -> s10(run, v);
                case "S04" -> s04(v);
                case "S07" -> s07(run);
                case "S08" -> new Target("storm_deck_s08", StormDeck2.s08Valve(1), 3f, 1f, 1.5f);
                case "S11" -> s11(run);
                case "S12" -> s12(run);
                default -> s09(run);
            };
        }
        return null;
    }

    private static Target stone(String id) {
        return new Target("storm_deck_stone", stoneOf(id), 0.7f, 1f, 0.7f);
    }

    private static Target s02(Vec3 v) {
        for (int i = 0; i < StormDeck.S02_ISLANDS.length; i++) {
            Island is = StormDeck.S02_ISLANDS[i];
            if (is.cx() < v.x - 3) {
                return new Target("storm_deck_s02", new BlockPos(is.cx(), is.y() + 1, is.cz()), 2.5f, 1f, 2.5f);
            }
        }
        return stone("S02");
    }

    private static Target s03(Vec3 v) {
        for (int i = 0; i < StormDeck.S03_ISLANDS.length; i++) {
            Island is = StormDeck.S03_ISLANDS[i];
            if (is.cx() > v.x + 3) {
                return new Target("storm_deck_s03", new BlockPos(is.cx(), is.y() + 1, is.cz()), is.size() + 0.5f, 1f, is.size() + 0.5f);
            }
        }
        return stone("S03");
    }

    private static Target s09(@Nullable TrialCell.Run run) {
        boolean started = run != null && (Boolean) run.data.getOrDefault("started", false);
        if (started) {
            return null;
        }
        Island a = StormDeck.S09_ARENA;
        return new Target("storm_deck_s09", new BlockPos(a.cx(), Y + 1, a.cz()), 4f, 1f, 4f);
    }

    private static Target s10(@Nullable TrialCell.Run run, Vec3 v) {
        int cur = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < StormDeck.S10_PLATFORMS.length; i++) {
            double d = Math.abs(v.x - StormDeck.S10_PLATFORMS[i].cx());
            if (d < best) {
                best = d;
                cur = i;
            }
        }
        if (cur < StormDeck.S10_PLATFORMS.length - 1 && (run == null || S10Watch.ventOpen(run, cur))) {
            return new Target("storm_deck_s10_vent", StormDeck.s10Vent(cur).above(), 1.5f, 1f, 1.5f);
        }
        Island p = StormDeck.S10_PLATFORMS[cur];
        return new Target("storm_deck_s10_guard", new BlockPos(p.cx(), Y + 1, p.cz()), 5f, 1f, 5f);
    }

    // S04: 次の柱か島 (西へ)
    private static Target s04(Vec3 v) {
        List<BlockPos> stops = new ArrayList<>();
        for (int i = 0; i < StormDeck2.S04_PILLARS.length; i++) {
            stops.add(new BlockPos(StormDeck2.S04_PILLARS[i] + (i < 3 ? 3 : 3), Y + 1, StormDeck2.S04_CENTER_Z + 2));
        }
        stops.add(new BlockPos(StormDeck2.S04_MID.cx(), Y + 1, StormDeck2.S04_MID.cz()));
        stops.add(new BlockPos(StormDeck2.S04_END.cx(), Y + 1, StormDeck2.S04_END.cz()));
        stops.sort(java.util.Comparator.comparingInt(p -> -p.getX()));
        for (BlockPos p : stops) {
            if (p.getX() < v.x - 4) {
                return new Target("storm_deck_s04", p, 2.5f, 1f, 3f);
            }
        }
        return stone("S04");
    }

    // S07: 窓が開くまでは部屋 1 の鏡、開いたら部屋 2 の鏡
    private static Target s07(@Nullable TrialCell.Run run) {
        boolean open = run != null && S07Mirrors.windowOpened(run);
        BlockPos m = S07Mirrors.mirrorPos(open ? 4 : 0);
        return new Target(open ? "storm_deck_s07_b" : "storm_deck_s07_a", m, 2f, 1f, 2f);
    }

    // S11: 籠のそば
    private static Target s11(@Nullable TrialCell.Run run) {
        if (run == null) {
            return new Target("storm_deck_s11", new BlockPos(20, Y + 1, 163), 3f, 1f, 3f);
        }
        double[] c = S11Cage.at(run.mods.layout(), run.data.get("s") instanceof Double d ? d : 0.0);
        return new Target("storm_deck_s11", new BlockPos((int) c[0], Y + 1, (int) c[1]), 3f, 1f, 3f);
    }

    // S12: 始まる前は頂の真ん中へ、始まったら羽守長を倒す
    private static Target s12(@Nullable TrialCell.Run run) {
        boolean started = run != null && run.getInt("phase") > 0;
        Island a = StormDeck2.S12_ARENA;
        return new Target(started ? "storm_deck_s12_fight" : "storm_deck_s12", new BlockPos(a.cx(), Y + 1, a.cz()), started ? 6f : 4f, 1f, started ? 6f : 4f);
    }
}
