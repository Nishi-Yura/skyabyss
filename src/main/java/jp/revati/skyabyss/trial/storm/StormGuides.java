package jp.revati.skyabyss.trial.storm;

import static jp.revati.skyabyss.titan.storm.back.StormDeck.Y;

import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormDeck;
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
        return switch (id) {
            case "S02" -> new BlockPos(-24, Y + 1, 73);
            case "S03" -> new BlockPos(24, Y + 1, 73);
            case "S10" -> new BlockPos(-15, Y + 1, 82);
            default -> new BlockPos(15, Y + 1, 82);
        };
    }

    static BlockPos stoneOf(String id) {
        return switch (id) {
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
        boolean alongX = d.id().equals("S02") || d.id().equals("S03");
        return new Target("storm_deck_gate", g, alongX ? 0.7f : 3f, 3f, alongX ? 3f : 0.7f);
    }

    // 島の中: 次の島・風の穴・守り・石
    @Nullable
    public static Target inCell(ServerLevel back, ServerPlayer player) {
        Vec3 v = player.position();
        UUID group = Groups.keyOf(player);
        for (String id : new String[] {"S02", "S03", "S09", "S10"}) {
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
}
