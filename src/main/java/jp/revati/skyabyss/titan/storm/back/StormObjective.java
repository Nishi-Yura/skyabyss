package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.titan.end.TitanEndNetwork;
import jp.revati.skyabyss.titan.storm.back.StormBackState.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// 雷翼の背の「次の目的」の光の柱 (26 の 3.3・25 の 3章)。いま居る所とそのグループの進みから、次にすること一つを決め、
// 行き先に小さな光の柱と枠 (TitanEndNetwork.Beam, key "trial_obj:<id>") と、左上の目的の帯の一行 (lang objective.skyabyss.trial_<id>) を、その人にだけ送る。
// 光の柱は数秒で消えるので 40 tick ごとに送り直す。道は流れ (到着 → 門 → 尾の間 → 広間 → 試練 → 石 → 広間 → 竜骨 → 心臓) の一本で、
// どの試練を先にするかは人が決める (いちばん近い鳴っていない試練を指す。拍や時間では閉じない)
final class StormObjective {
    static final String KEY_PREFIX = "trial_obj:";
    private static final int GOLD = 0xF2C14E;

    // 行き先 (id・目印の位置・枠の大きさ)
    record Goal(String id, BlockPos at, float hx, float hy, float hz) {
    }

    // 目的の id の一覧 (lang の objective.skyabyss.trial_<id> の確認用)
    static final List<String> IDS = List.of("storm_gate", "storm_hall", "storm_west", "storm_east", "storm_front_vent", "storm_front_tower", "storm_front_neck",
            "storm_keel", "storm_extra_chest", "storm_west_gap", "storm_west_rib", "storm_west_stone", "storm_east_lever", "storm_east_pad", "storm_east_raiju", "storm_east_stone",
            "storm_front_mirror", "storm_front_stone", "storm_back_w", "storm_back_e", "storm_back_f", "storm_heart",
            "storm_deck_hall", "storm_deck_turret", "storm_deck_bridge", "storm_deck_gate", "storm_deck_s02", "storm_deck_s03", "storm_deck_s09", "storm_deck_s10_vent",
            "storm_deck_s10_guard", "storm_deck_stone", "storm_deck_bridge2", "storm_deck_s04", "storm_deck_s07_a", "storm_deck_s07_b", "storm_deck_s08",
            "storm_deck_s11", "storm_deck_s12", "storm_deck_s12_fight");

    private StormObjective() {
    }

    // 40 tick ごと (StormGuide.tick から)。その人にだけ送る
    static void send(ServerLevel level, ServerPlayer p) {
        Goal g = next(level, p);
        if (g == null) {
            return;
        }
        BlockPos at = g.at();
        PacketDistributor.sendToPlayer(p, new TitanEndNetwork.Beam(KEY_PREFIX + g.id(), GOLD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5,
                g.hx(), g.hy(), g.hz(), 0f, at.getY(), at.getY() + 12, 0.35f, 70));
    }

    private static Goal goal(String id, BlockPos at, float hx, float hy, float hz) {
        return new Goal(id, at, hx, hy, hz);
    }

    // 次の目的。なければ null (心臓の戦いの中など)
    @Nullable
    static Goal next(ServerLevel level, ServerPlayer player) {
        Vec3 v = player.position();
        StormProgress pr = StormProgress.of(level, player);
        if (StormBackLayout.pastKeel(v)) {
            if (pr.heartDefeated()) {
                return null;
            }
            // 螺旋と道を抜けて前室、前室から竜骨の間の戸
            return goal("storm_heart", new BlockPos(0, KEEL_ANTE.floor() + 1, (int) HEART_DOOR.minZ), 3f, 3f, 0.7f);
        }
        // 試練の甲板 (29 の P2): 島の中・橋・渡り場・背の広場から橋へ
        if (StormDeck.placed()) {
            jp.revati.skyabyss.trial.storm.StormGuides.Target t = jp.revati.skyabyss.trial.storm.StormGuides.inCell(level, player);
            if (t != null) {
                return goal(t.id(), t.at(), t.hx(), t.hy(), t.hz());
            }
            boolean onDeck = StormDeck.YARD.aabb().inflate(1, 4, 1).contains(v) || StormDeck2.onLanding(v)
                    || v.x >= StormDeck.BRIDGE_X1 - 1 && v.x <= StormDeck.BRIDGE_X2 + 2 && v.z >= StormDeck.BRIDGE_Z1 && v.z <= StormDeck.BRIDGE_Z2 + 1 && v.y > StormDeck.Y - 2;
            if (onDeck) {
                var g = jp.revati.skyabyss.trial.storm.StormGuides.atYard(level, player);
                if (g != null) {
                    return goal(g.id(), g.at(), g.hx(), g.hy(), g.hz());
                }
            } else if (jp.revati.skyabyss.trial.storm.StormGuides.unrung(level, jp.revati.skyabyss.titan.group.Groups.keyOf(player)) != null
                    && StormExtra.PLAZA.aabb().inflate(1, 6, 1).contains(v)) {
                return goal("storm_deck_bridge", new BlockPos((StormDeck.BRIDGE_X1 + StormDeck.BRIDGE_X2) / 2, StormDeck.Y + 1, StormDeck.BRIDGE_Z1 + 1), 3.5f, 2f, 0.7f);
            }
        }
        if (StormBackLayout.inWest(v)) {
            return west(pr, v);
        }
        if (StormBackLayout.inEast(v)) {
            return east(level, player, pr, v);
        }
        if (StormBackLayout.inFront(v)) {
            return front(pr, v);
        }
        // 任意の翼 (背の広場・塔・外廊・庭): まだ開けていない近くの宝箱。なければ本道 (広間) へ戻る道しるべ
        if (StormExtra.inAnyZone(v)) {
            BlockPos chest = nearestChest(player, v);
            if (chest != null) {
                return goal("storm_extra_chest", chest, 0.7f, 1f, 0.7f);
            }
        }
        // 広間とその上の回廊
        if (HUB.aabb().inflate(2, 8, 2).contains(v) || GALLERY.aabb().inflate(1, 2, 1).contains(v)) {
            return hub(level, player, pr, v);
        }
        // 尾の間と腰の回廊 (広間の南)
        if (v.z < GATE_Z1 && v.z > HUB.z2() && v.y > 128) {
            if (deckWanted(level, player, pr)) {
                // 尾の間の北西の螺旋の塔 (背の広場へ上る)
                return goal("storm_deck_turret", new BlockPos(StormExtra.TURRET_X, TAIL_HALL.floor() + 1, StormExtra.TURRET_Z), 2.5f, 2f, 2.5f);
            }
            return goal("storm_hall", new BlockPos(0, HUB.floor() + 1, HUB.z2()), 3f, 2f, 0.7f);
        }
        // 尾羽の上 (到着の台・羽軸の道・羽の広場) と羽根の門
        if (v.z >= GATE_Z1 - 1) {
            return goal("storm_gate", new BlockPos(0, GATE_FLOOR + 1, GATE_Z1), 3.5f, 4f, 0.7f);
        }
        return null;
    }

    // 任意の翼の宝箱 (StormCaches の side 3〜7) のうち、その人がまだ開けていない、いちばん近いもの
    @Nullable
    private static BlockPos nearestChest(ServerPlayer player, Vec3 v) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (var e : StormExtra.caches().entrySet()) {
            if (StormCaches.taken(player, e.getKey())) {
                continue;
            }
            double d = Vec3.atCenterOf(e.getValue()).distanceToSqr(v);
            if (d < bd) {
                bd = d;
                best = e.getValue();
            }
        }
        return best;
    }

    // 広間: いちばん近い、まだ鳴っていない試練の入口。三つ鳴っていれば竜骨の門
    private static Goal hub(ServerLevel level, ServerPlayer player, StormProgress pr, Vec3 v) {
        if (pr.allRung()) {
            return goal("storm_keel", new BlockPos(0, HUB.floor(), HUB_CENTER.getZ()), 3f, 1f, 3f);
        }
        Trial best = null;
        double bd = Double.MAX_VALUE;
        boolean deck = false;
        jp.revati.skyabyss.trial.TrialDraw.Draw draw = StormDeck.placed() ? jp.revati.skyabyss.trial.TrialData.current(level, jp.revati.skyabyss.titan.group.Groups.keyOf(player)) : null;
        for (Trial t : Trial.values()) {
            if (pr.rung(t)) {
                continue;
            }
            // 引いたのが新作 (試練の甲板) なら、入口は広間の南の出口 (尾の間から背の広場へ)
            boolean newCell = draw != null && isDeckKey(draw, t);
            Vec3 e = newCell ? new Vec3(0, HUB.floor() + 1, HUB.z2()) : entrance(t);
            double d = e.distanceToSqr(v);
            if (d < bd) {
                bd = d;
                best = t;
                deck = newCell;
            }
        }
        if (best == null) {
            return null;
        }
        if (deck) {
            return goal("storm_deck_hall", new BlockPos(0, HUB.floor() + 1, HUB.z2()), 3f, 2f, 0.7f);
        }
        switch (best) {
            case WEST:
                return goal("storm_west", new BlockPos(WEST_PASS.x2() + 1, HUB.floor() + 1, WZ), 0.7f, 3f, 3.5f);
            case EAST:
                return goal("storm_east", new BlockPos(EAST_PASS.x1() - 1, HUB.floor() + 1, WZ), 0.7f, 3f, 3.5f);
            default:
                // 前は、嵐なら中央の昇り風、凪なら塔の入口。上の回廊にいれば首の入口
                if (v.y > UPPER_GALLERY_Y - 1) {
                    return goal("storm_front_neck", new BlockPos(0, UPPER_GALLERY_Y + 1, NECK.z2()), 3.5f, 4f, 0.7f);
                }
                return StormBackClock.phase(level).stormy() ? goal("storm_front_vent", new BlockPos(HUB_UPDRAFT.getX(), HUB.floor() + 1, HUB_UPDRAFT.getZ()), 1.5f, 2f, 1.5f)
                        : goal("storm_front_tower", new BlockPos(CALM_TOWER_X - 3, HUB.floor() + 1, CALM_TOWER_Z + 3), 1.5f, 2f, 1.5f);
        }
    }

    // この枠 (西・東・前) に、このグループが引いたのは新作 (試練の甲板) か
    private static boolean isDeckKey(jp.revati.skyabyss.trial.TrialDraw.Draw draw, Trial t) {
        var d = jp.revati.skyabyss.trial.storm.StormStones.cellOfKey(draw, t.id());
        var c = d == null ? null : jp.revati.skyabyss.trial.TrialCells.get(d.id());
        return c != null && c.runtime();
    }

    // 石がまだの新作が一つでもあるか
    private static boolean deckWanted(ServerLevel level, ServerPlayer player, StormProgress pr) {
        return StormDeck.placed() && jp.revati.skyabyss.trial.storm.StormGuides.unrung(level, jp.revati.skyabyss.titan.group.Groups.keyOf(player)) != null;
    }

    private static Vec3 entrance(Trial t) {
        return switch (t) {
            case WEST -> new Vec3(WEST_PASS.x2() + 1, HUB.floor() + 1, WZ);
            case EAST -> new Vec3(EAST_PASS.x1() - 1, HUB.floor() + 1, WZ);
            default -> new Vec3(HUB_UPDRAFT.getX(), HUB.floor() + 1, HUB_UPDRAFT.getZ());
        };
    }

    // 西: 割れた床 → 肋の間 (次の岩棚) → 頂の石 → 光る床で戻る
    private static Goal west(StormProgress pr, Vec3 v) {
        if (pr.rung(Trial.WEST)) {
            return goal("storm_back_w", RETURN_WEST, 1.5f, 0.6f, 1.5f);
        }
        for (int[] g : WEST_GAPS) {
            if (v.x > g[0] - 1) {
                // この割れた床の向こう側 (西) の縁
                return goal("storm_west_gap", new BlockPos(g[0] - 3, BONE_FLOOR + 1, WZ), 3f, 3f, 3f);
            }
        }
        if (pr.ribLayer() < RIB_LAYERS) {
            return goal("storm_west_rib", ribLanding(pr.ribLayer() + 1).above(), 2.5f, 1f, 2.5f);
        }
        return goal("storm_west_stone", WEST_STONE, 0.7f, 1f, 0.7f);
    }

    // 東: いちばん近い、まだの回路 (レバー → 金の床)。戸が全部開けば雷獣、倒せば頂の石
    private static Goal east(ServerLevel level, ServerPlayer player, StormProgress pr, Vec3 v) {
        if (pr.rung(Trial.EAST)) {
            return goal("storm_back_e", RETURN_EAST, 1.5f, 0.6f, 1.5f);
        }
        if (pr.raijuDefeated()) {
            return goal("storm_east_stone", EAST_STONE, 0.7f, 1f, 0.7f);
        }
        // 戸は手前から順に開ける: まだの最初の回路
        StormCircuit.Circuit cur = null;
        for (StormCircuit.Circuit c : StormCircuit.CIRCUITS) {
            if (!StormCircuit.done(pr, c)) {
                cur = c;
                break;
            }
        }
        if (cur == null) {
            return goal("storm_east_raiju", new BlockPos((int) RAIJU_NEST_MOUTH.minX, BONE_FLOOR + 1, WZ), 0.7f, 3f, 3.5f);
        }
        boolean armed = StormCircuit.trace(cur, lp -> StormCircuit.leverOn(level, lp)).outcome() == StormCircuit.Outcome.DOOR;
        if (armed) {
            BlockPos pad = cur.pad();
            return goal("storm_east_pad", new BlockPos(pad.getX(), BONE_FLOOR + 1, pad.getZ()), 1.5f, 0.6f, 1.5f);
        }
        // 倒すレバー (解のうち、まだ倒していない最初の一本)
        for (BlockPos l : StormCircuit.solution(cur)) {
            if (!StormCircuit.leverOn(level, l)) {
                return goal("storm_east_lever", l, 0.7f, 1f, 0.7f);
            }
        }
        BlockPos pad = cur.pad();
        return goal("storm_east_pad", new BlockPos(pad.getX(), BONE_FLOOR + 1, pad.getZ()), 1.5f, 0.6f, 1.5f);
    }

    // 前: 冠の間 → 冠の道 → 眼の間 (光を導く) → 戸が開けば嘴の石 → 光る床で戻る
    private static Goal front(StormProgress pr, Vec3 v) {
        if (pr.rung(Trial.FRONT)) {
            return goal("storm_back_f", RETURN_FRONT, 1.5f, 0.6f, 1.5f);
        }
        if (pr.receivers() == 7) {
            return goal("storm_front_stone", FRONT_STONE, 0.7f, 1f, 0.7f);
        }
        return goal("storm_front_mirror", new BlockPos(EYE_HALL_CENTER.getX(), EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ()), 6f, 3f, 6f);
    }
}
