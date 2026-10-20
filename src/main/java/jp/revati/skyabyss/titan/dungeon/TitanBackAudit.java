package jp.revati.skyabyss.titan.dungeon;

import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BackReach.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 岩鯨の背の「詰み監査」の指定 (docs/design/02 の詰み監査)。調べる部品は titan/audit/BackReach
public final class TitanBackAudit {
    private TitanBackAudit() {
    }

    // 体の中の部屋すべて (穴・はしごを調べる範囲と、通れるかの目標に使う)
    public static final List<TitanBackLayout.Box> ROOMS = List.of(HUB, ENTRY, W0, WA, W1, WB, W2, WC, E0, E1, CE1, E3_CORRIDOR, E3,
            E2, CK, CK_PASS, MAZE_EXIT_ROOM, MAZE_ENTRY_ROOM, MAZE_ENTRY_ROOM2, HEART_REST, HEART, HEART_CORRIDOR, BELL, BELL_PASS, W2_CONN1,
            W2_CONN2, LIBRARY, LIBRARY_PASS, WEST_HALL, EAST_HALL, EXIT_PASS);

    public static BackReach.Spec spec() {
        BackReach.Spec s = new BackReach.Spec("whale", new Box(-RX, 40, -RZ, RX, 205, RZ));
        s.voidY = BASE_Y - 50;
        s.entrances.add(arrival());
        s.exits.add(arrival());
        // 穴・はしごを調べるのは、体の中の部屋 (地表の背の遺跡・ひれは歩ける所だけ見る)
        for (TitanBackLayout.Box b : ROOMS) {
            s.scan.add(new Box(b.x1() - 1, b.floorY() - 14, b.z1() - 1, b.x2() + 1, b.ceilingY(), b.z2() + 1));
        }
        // 祠から入口の通路まで (階段の幅の外へは出ない)
        s.scan.add(new Box(-8, FLOOR - 2, ENTRY.z1() - 2, 8, FLOOR + 10, SHRINE_Z));
        // 西の谷は、落ちたら直前の休み場へ戻される (FALL_LINE) わざとの穴。足もとは溶岩
        for (TitanBackLayout.Box pit : PITS) {
            s.resets.add(new Box(pit.x1() - 1, PIT_LAVA_Y - 4, pit.z1() - 1, pit.x2() + 1, pit.ceilingY(), pit.z2() + 1));
            // 足場の並びは谷の内側だけ。縁の一列 (戸口の岩棚・壁ぎわ) は穴の検査から外さない
            s.allowHole.add(new Box(pit.x1() + 1, PIT_LAVA_Y - 4, pit.z1() + 1, pit.x2() - 1, pit.ceilingY(), pit.z2() - 1));
        }
        s.goals.put("plaza", plaza());
        s.goals.put("forge", forge().west());
        s.goals.put("shrine", new BlockPos(0, shrineY() + 1, SHRINE_Z));
        s.goals.put("camp", new BlockPos(0, WingsBuilder.CAMP_Y + 1, WingsBuilder.CAMP_Z + 3));
        s.goals.put("tail vista", new BlockPos(0, WingsBuilder.TAIL_Y + 1, WingsBuilder.TAIL_CZ - 6));
        s.goals.put("hub", HUB.center().offset(0, 0, 6));
        s.goals.put("entry", ENTRY.center());
        s.goals.put("seal corridor", HEART_CORRIDOR.center().offset(0, 0, 20));
        s.goals.put("rest w0", W0.center().offset(2, 0, 2));
        s.goals.put("rest w1", W1.center().offset(2, 0, 2));
        s.goals.put("rest w2", W2.center().offset(2, 0, 2));
        s.goals.put("bell landing", new BlockPos(-42, BellTower.LANDING_Y + 1, BELL.z2() - 2));
        s.goals.put("bell floor", new BlockPos(-43, FLOOR + 1, BELL.z1() + 8));
        s.goals.put("west stone ledge", new BlockPos(WC.x2() - 3, WEST_LEDGE_Y + 1, -52));
        s.goals.put("rest e0", E0.center().offset(2, 0, 2));
        s.goals.put("e1", E1.center());
        s.goals.put("e2", E2.center());
        s.goals.put("rest ckr", CK_PASS.center().offset(2, 0, -2));
        s.goals.put("ck", CK.center());
        s.goals.put("rest ce1", CE1.center().offset(2, 0, 2));
        s.goals.put("e3", E3.center());
        s.goals.put("east stone", GOAL_SPOTS.get(1).offset(-1, 0, 0));
        s.goals.put("maze exit room", MAZE_EXIT_ROOM.center());
        s.goals.put("lower stone", GOAL_SPOTS.get(2).offset(-1, 0, 0));
        s.goals.put("rest heart", HEART_REST.center().offset(1, 0, 1));
        s.goals.put("heart arena", HEART.center());
        s.goals.put("library", LIBRARY.center());
        // 任意の翼 (背の外): 胸びれの道の先端・尾の展望・背の野営地
        s.goals.put("bell tower floor", new BlockPos(BELL.x1() + 3, FLOOR + 1, BELL.z1() + 6));
        s.goals.put("fin left tip", WingsBuilder.finTip(-1));
        s.goals.put("fin right tip", WingsBuilder.finTip(1));
        s.goals.put("fin left cache", WingsBuilder.cachePos(-1).north());
        s.goals.put("fin right cache", WingsBuilder.cachePos(1).north());
        s.goals.put("tail vista", new BlockPos(0, WingsBuilder.TAIL_Y + 1, WingsBuilder.TAIL_CZ - 4));
        s.goals.put("back camp", new BlockPos(0, WingsBuilder.CAMP_Y + 1, WingsBuilder.CAMP_Z - 3));
        if (WhaleDeck.placed()) {
            deckAllowances(s);
        }
        return s;
    }

    // 試練の階の南の拡張 (最南の広間 S1 と四つのセル。docs/design/29 の P2) を、配置 3 通りで調べる。足場の戸 (島・岩棚・崩れる床・橋) は閉じた足場として歩く。
    // 噴気孔の吹き上げは「台から次の岩棚へ」の道として足す。穴 (落ちたら最後の島・控えへ戻る) は出口の箱。石の戻りの床は出口。
    // 試練の階の南の拡張の「わざとの穴」: 滑り坂の島の列と闘技場の穴 (底は水。落ちたら最後の島・控えへ戻される)・縦の穴の底 (落ちても吹き上げで昇れる。
    // 吹き上げの道は deck の調べで足す)。四つの背全体の調べ (spec) でも、deck の調べ (runDeck) でも同じに許す
    static void deckAllowances(BackReach.Spec s) {
        // 穴: 滑り坂の島の列 (水の底)・闘技場の穴。落ちたら控え・最後の島へ戻される
        for (int g = 0; g < jp.revati.skyabyss.trial.whale.W03Moss.GAP_Z.length; g++) {
            int z = jp.revati.skyabyss.trial.whale.W03Moss.GAP_Z[g];
            int top = jp.revati.skyabyss.trial.whale.W03Moss.GAP_Y[g];
            Box pit = new Box(WhaleDeck.W03_X1, top - 7, z, WhaleDeck.W03_X2, top - 1, z + 1);
            s.resets.add(pit);
            s.allowHole.add(new Box(WhaleDeck.W03_X1, top - 7, z - 1, WhaleDeck.W03_X2, top, z + 2));
        }
        // 穴の底 (水) に入ったら、戻される立ち位置 (出口)
        final int[] pitTops = jp.revati.skyabyss.trial.whale.W03Moss.GAP_Y;
        s.exitNode = p -> {
            for (int g = 0; g < pitTops.length; g++) {
                int z = jp.revati.skyabyss.trial.whale.W03Moss.GAP_Z[g];
                if (p.getZ() >= z && p.getZ() <= z + 1 && p.getY() <= pitTops[g] - 1 && p.getY() >= pitTops[g] - 7 && p.getX() >= WhaleDeck.W03_X1 && p.getX() <= WhaleDeck.W03_X2) {
                    return true;
                }
            }
            return Math.hypot(p.getX() - WhaleDeck.W12_CX, p.getZ() - WhaleDeck.W12_CZ) <= 12.5 && p.getY() <= WhaleDeck.Y - 1 && p.getY() >= WhaleDeck.Y - 8;
        };
        Box hall = new Box(WhaleDeck.W12_CX - 12, WhaleDeck.Y - 8, WhaleDeck.W12_CZ - 12, WhaleDeck.W12_CX + 12, WhaleDeck.Y - 1, WhaleDeck.W12_CZ + 12);
        s.resets.add(hall);
        s.allowHole.add(new Box(hall.x1(), hall.y1(), hall.z1(), hall.x2(), WhaleDeck.Y, hall.z2()));
        // 縦の穴の底: 岩棚から落ちて着く所。吹き上げの道 (extra) で上へ戻れる
        Box shaft = new Box(WhaleDeck.W04.x1(), WhaleDeck.W04.floorY(), WhaleDeck.W04.z1(), WhaleDeck.W04.x2(), WhaleDeck.W04.ceilingY(), WhaleDeck.W04.z2());
        s.allowHole.add(shaft);
        s.allowTrap.add(shaft);
    }

    public static BackReach.Report runDeck(ServerLevel level) {
        BackReach.Report all = new BackReach.Report();
        if (!WhaleDeck.placed()) {
            return all;
        }
        jp.revati.skyabyss.trial.whale.W12Arena arena = new jp.revati.skyabyss.trial.whale.W12Arena();
        for (int layout = 0; layout < 3; layout++) {
            BackReach.Spec s = new BackReach.Spec("whale deck south " + layout, new Box(-62, 96, 66, 62, 140, 180));
            s.entrances.add(WhaleDeck.S0.center());
            s.exits.add(WhaleDeck.S0.center());
            for (BlockPos pad : new BlockPos[] {WhaleDeck.W03_PAD, WhaleDeck.W04_PAD, WhaleDeck.W11_PAD, WhaleDeck.W12_PAD, WhaleDeck.W08_PAD}) {
                s.exits.add(pad.above());
            }
            // 足場の戸
            java.util.Set<BlockPos> solid = new java.util.HashSet<>(jp.revati.skyabyss.trial.whale.W03Moss.active(layout));
            solid.addAll(jp.revati.skyabyss.trial.whale.W04Vents.ledge(1, jp.revati.skyabyss.trial.whale.W04Vents.LAYOUT[layout][0]));
            solid.addAll(jp.revati.skyabyss.trial.whale.W04Vents.ledge(2, jp.revati.skyabyss.trial.whale.W04Vents.LAYOUT[layout][1]));
            solid.addAll(arena.discCells());
            solid.addAll(jp.revati.skyabyss.trial.whale.W12Arena.BRIDGE_IN);
            solid.addAll(jp.revati.skyabyss.trial.whale.W12Arena.BRIDGE_OUT);
            s.solidGate = solid::contains;
            deckAllowances(s);
            // 穴・はしごを調べる範囲 (広間・通路・控え・石の部屋)
            for (TitanBackLayout.Box b : new TitanBackLayout.Box[] {WhaleDeck.C2, WhaleDeck.S1, WhaleDeck.W11, WhaleDeck.W11_STONE_ROOM, WhaleDeck.W04_STONE_ROOM,
                    WhaleDeck.W12_FOYER_N, WhaleDeck.W12_FOYER_S, WhaleDeck.W08, WhaleDeck.W08_STONE_ROOM}) {
                s.scan.add(new Box(b.x1() - 1, b.floorY() - 2, b.z1() - 1, b.x2() + 1, b.ceilingY(), b.z2() + 1));
            }
            for (int[] seg : WhaleDeck.W03_SEGS) {
                if (seg[1] - seg[0] > 1) {
                    s.scan.add(new Box(WhaleDeck.W03_X1 - 1, seg[2] - 2, seg[0] - 1, WhaleDeck.W03_X2 + 1, seg[2] + 10, seg[1] + 1));
                }
            }
            for (jp.revati.skyabyss.trial.TrialCell.Rest r : WhaleDeck.rests()) {
                if (r.spawn().getZ() >= 66) {
                    s.goals.put("deck rest " + r.id(), r.spawn());
                }
            }
            s.goals.put("W03 stone", WhaleDeck.W03_STONE.east());
            s.goals.put("W04 stone", WhaleDeck.W04_STONE.east());
            s.goals.put("W11 stone", WhaleDeck.W11_STONE.east());
            s.goals.put("W12 stone", WhaleDeck.W12_STONE.east());
            s.goals.put("W08 stone", WhaleDeck.W08_STONE.east());
            s.goals.put("W08 console", new BlockPos(-40, WhaleDeck.Y + 1, 100));
            s.goals.put("W04 exit ledge", new BlockPos(WhaleDeck.W04.x1() + 10, WhaleDeck.Y + 1, 149));
            s.goals.put("W12 foyer south", WhaleDeck.W12_FOYER_S.center());
            final int lay = layout;
            s.extra = (probe, out) -> {
                BlockPos p = probe.pos();
                for (int i = 0; i < 3; i++) {
                    jp.revati.skyabyss.trial.whale.W04Vents.Vent v = jp.revati.skyabyss.trial.whale.W04Vents.vent(lay, i);
                    if (Math.abs(p.getX() - v.cx()) <= 1 && Math.abs(p.getZ() - v.cz()) <= 1 && p.getY() == v.padY() + 1) {
                        int[] to = jp.revati.skyabyss.trial.whale.W04Vents.landing(lay, i);
                        out.add(new BlockPos(to[0], to[1], to[2]));
                    }
                }
            };
            BackReach.Report r = BackReach.run(level, s);
            for (String f : r.failures) {
                all.failures.add("[layout " + layout + "] " + f);
            }
            all.notes.addAll(r.notes);
            if (r.reached != null) {
                if (all.reached == null) {
                    all.reached = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
                }
                all.reached.addAll(r.reached);
            }
        }
        return all;
    }

    public static BackReach.Report run(ServerLevel level) {
        BackReach.Spec s = spec();
        BlockPos mz0 = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_ENTRY);
        if (mz0 != null) {
            s.goals.put("rest mz0 (maze entrance room)", mz0);
        }
        BlockPos mz = TitanBackTrials.checkpointSpawns(level).get(TitanBackTrials.MAZE_REST);
        if (mz != null) {
            s.goals.put("rest mz (maze mid ledge)", mz);
        }
        // 灯の台 (23): 3 つぜんぶ、水の中を泳いで台の上まで行けること (戸は開いているものとして通る)
        java.util.List<BlockPos> stands = AnemoneLights.stands(TitanBackData.get(level).mazeSeed());
        for (int i = 0; i < stands.size(); i++) {
            s.goals.put("lamp stand " + (i + 1), stands.get(i).above());
        }
        BackReach.Report rep = BackReach.run(level, s);
        BackReach.Report deck = runDeck(level);
        rep.failures.addAll(deck.failures);
        rep.notes.addAll(deck.notes);
        if (rep.reached != null && deck.reached != null) {
            // 試練の階の南の拡張の歩ける所も、暗さの検査に入れる (閉じた足場として歩いた所。三つの配置の和)
            rep.reached.addAll(deck.reached);
        }
        if (!rep.failures.isEmpty()) {
            List<List<int[]>> courses = ParkourCourses.current(level);
            for (int c = 0; c < courses.size(); c++) {
                StringBuilder sb = new StringBuilder();
                for (int[] st : courses.get(c)) {
                    boolean ok = rep.reached.contains(new BlockPos(st[0], st[1] + 1, st[2]).asLong());
                    sb.append(st[0]).append(",").append(st[1]).append(",").append(st[2]).append(ok ? "+ " : "- ");
                }
                jp.revati.skyabyss.SkyAbyss.LOGGER.warn("REACH dbg course {}: {}", c, sb);
            }
        }
        return rep;
    }
}
