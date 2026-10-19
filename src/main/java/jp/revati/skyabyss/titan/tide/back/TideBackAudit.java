package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BackReach.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

// 潮殻の背の「詰み監査」の指定 (docs/design/08 の詰み監査)。干潮 (水盤が空) で、戸は開いているものとして調べる
public final class TideBackAudit {
    private TideBackAudit() {
    }

    private static Box box(Area a) {
        return new Box(a.x1() - 1, a.floor() - 2, a.z1() - 1, a.x2() + 1, a.floor() + a.height() + 1, a.z2() + 1);
    }

    public static BackReach.Spec spec() {
        BackReach.Spec s = new BackReach.Spec("turtle", new Box(-190, 38, -245, 190, 230, 170));
        s.longFallIsExit = true;
        s.entrances.add(ARRIVAL);
        s.exits.add(ARRIVAL);
        // 島から海に落ちて、甲羅の外の水に 5 秒いると、潮に押し戻される (TideTravel)
        s.exitNode = p -> shellS2(p.getX() + 0.5, p.getZ() + 0.5) > 1.02 && p.getY() < SEA + 1;
        for (Rest r : RESTS) {
            s.goals.put("rest " + r.id(), r.spawn());
        }
        s.goals.put("clam", new BlockPos(CLAM[0] + 2, GARDEN_FLOOR + 1, CLAM[1] - 2));
        s.goals.put("altar", new BlockPos(ALTAR[0] + 1, ALTAR_TOP + 1, ALTAR[1] + 1));
        s.goals.put("east gallery", new BlockPos(22, HUB_LEDGE + 1, -4));
        s.goals.put("hub floor", new BlockPos(8, HUB_FLOOR + 1, 8));
        s.goals.put("flats", new BlockPos(0, FLATS_FLOOR + 1, -100));
        s.goals.put("front stone", STONE_FRONT.offset(0, 0, 1));
        s.goals.put("west stone", STONE_WEST.offset(1, 0, 0));
        s.goals.put("beach", new BlockPos(-30, BEACH.floor() + 1, 80));
        s.goals.put("shell top", new BlockPos(TOP_STAIR_X1 + 2, shellTop(TOP_STAIR_X1 + 2, TOP_STAIR_Z1 - 4) + 1, TOP_STAIR_Z1 - 4));
        // 息の間: 縁の入口の泡・各皿の着地・舞台・展望の間。昇りは光る近い泡だけ (遠い泡は潮が満ちたときの近道なので、なくても昇れること)
        for (BreathLayout.Node n : BreathLayout.nodes()) {
            s.goals.put("breath " + n.label(), landingBlock(n));
        }
        // 宝箱の前 (碑と宝を、歩いて取りに行ける)、ひれの先端 (戸は開けたグループにとって開いているとして)
        for (TideCaches.Cache c : TideCaches.CACHES) {
            // 灯台のテラスは岩棚の先 (水位の謎解きの水は、監査では引いたまま。岩棚は届かない)
            if (!c.id().equals("terrace")) {
                s.goals.put("cache " + c.id(), c.chest().relative(c.front()));
            }
        }
        for (int side : new int[] {-1, 1}) {
            s.goals.put("fin tip " + (side < 0 ? "west" : "east"), TideFins.tipCenter(side).above());
        }
        for (TideFins.Room r : TideFins.ROOMS) {
            s.goals.put("fin room " + r.id(), r.center());
        }
        s.extra = TideBackAudit::breathEdges;
        if (TideDeck.placed()) {
            // 試練の階 (TideDeck): 足場の戸 (砂州・浮き石・円環の床・橋。y = 床のブロック) は、三つの配置すべてを閉じた足場として歩く
            // (配置ごとの道は、セルの audit が調べる)。戸口の戸 (y が床より上) は開いているものとして通る
            s.solidGate = p -> p.getY() == TideDeck.F && p.getX() >= 0 && p.getX() <= 100 && p.getZ() >= 45 && p.getZ() <= 95;
            s.goals.put("deck hall", new BlockPos(40, TideDeck.F + 1, 42));
            s.goals.put("deck T02 islet", new BlockPos(12, TideDeck.F + 1, 70));
            s.goals.put("deck T02 far shore", new BlockPos(12, TideDeck.F + 1, 90));
            s.goals.put("deck T02 stone", TideDeck.T02_STONE.east());
            s.goals.put("deck T10 foyer south", new BlockPos(40, TideDeck.F + 1, 84));
            s.goals.put("deck T10 stone", TideDeck.T10_STONE.east());
            s.goals.put("deck T06 stone", TideDeck.T06_STONE.east());
            s.goals.put("deck T07 stone", TideDeck.T07_STONE.east());
        }
        return s;
    }

    static BlockPos landingBlock(BreathLayout.Node n) {
        return BlockPos.containing(n.landX(), n.landY() + 0.01, n.landZ());
    }

    // 運び (近い泡) と下り (しゃがみ) を道に足す: 泡に立つ足もとから行き先の着地へ、乗り場の上の足もとからひとつ前の着地へ
    static void breathEdges(BackReach.Probe probe, BackReach.Extra out) {
        BlockPos p = probe.pos();
        if (!Breath.active(net.minecraft.world.phys.Vec3.atBottomCenterOf(p))) {
            return;
        }
        double cx = p.getX() + 0.5;
        double cz = p.getZ() + 0.5;
        for (BreathLayout.Lift l : Breath.LIFTS) {
            if (l.far()) {
                continue;
            }
            double[] pad = l.pad();
            if (Math.hypot(cx - pad[0], cz - pad[1]) <= l.padRadius() + 0.5 && p.getY() == (int) Math.floor(l.padY() + 1e-6)) {
                out.add(landingBlock(l.b()));
            }
        }
        for (BreathLayout.Node n : BreathLayout.nodes()) {
            if (n.kind() == BreathLayout.Kind.ENTRY) {
                continue;
            }
            boolean on = switch (n.kind()) {
                case DISH -> Math.hypot(cx - (BreathLayout.dish(n.dish()).wx()), cz - BreathLayout.dish(n.dish()).wz()) <= BreathLayout.RING_R
                        && (p.getY() == BreathLayout.dish(n.dish()).floorY() || p.getY() == BreathLayout.dish(n.dish()).floorY() + 1);
                case ARENA -> Math.hypot(cx - BreathLayout.WX, cz - BreathLayout.WZ) <= BreathLayout.ARENA_R && p.getY() == BreathLayout.ARENA_FLOOR + 1;
                case DECK -> BreathLayout.deckBox().contains(net.minecraft.world.phys.Vec3.atBottomCenterOf(p)) && p.getY() == BreathLayout.DECK_FLOOR + 1;
                default -> false;
            };
            if (on) {
                out.add(landingBlock(BreathLayout.nodes().get(n.id() - 1)));
            }
        }
    }

    // 庭の道の形 (3 通り) × 潮見の間の配置 (4 通り) のすべてで調べる。干潮、戸は開いているものとして
    public static BackReach.Report runAll(ServerLevel level, java.util.UUID group) {
        BackReach.Report all = new BackReach.Report();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int route = 0; route < GARDEN_ROUTES.length; route++) {
            for (int east = 0; east < 4; east++) {
                TideRooms.releaseGroup(level, group);
                TideBackState.Group state = TideBackState.of(level, group);
                state.record.putInt("tide.route", route);
                state.record.putInt("tide.east", east);
                for (TideField.Pool p : TideBackTrials.FIELD.pools()) {
                    TideField.settleNow(level, p, 0);
                }
                TideRooms.claim(level, TideRooms.GARDEN, group);
                TideRooms.claim(level, TideRooms.WATCH, group);
                TideRooms.claim(level, TideRooms.FLATS, group);
                BackReach.Report r = BackReach.run(level, spec());
                String tag = "[route " + route + ", east " + east + "] ";
                for (String f : r.failures) {
                    counts.merge(f, 1, Integer::sum);
                }
                all.notes.add(tag + r.notes.get(0));
            }
        }
        // 同じ指摘は、何通りの配置で出たかを添えて一度だけ
        counts.forEach((f, n) -> all.failures.add(f + (n < GARDEN_ROUTES.length * 4 ? " (in " + n + " of " + GARDEN_ROUTES.length * 4 + " layouts)" : "")));
        return all;
    }
}
