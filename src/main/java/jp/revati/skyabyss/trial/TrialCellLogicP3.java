package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.PearlKeeper;
import jp.revati.skyabyss.titan.tide.back.TideDeck;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.tide.T03Jellies;
import jp.revati.skyabyss.trial.tide.T04Whirl;
import jp.revati.skyabyss.trial.tide.T08Shelf;
import jp.revati.skyabyss.trial.tide.T11Clam;
import jp.revati.skyabyss.trial.tide.T12Keeper;
import jp.revati.skyabyss.trial.whale.W08Shadows;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// P3 の新作 (W08 影絵の間・T03 くらげの回廊・T04 渦潮の滑り台・T08 珊瑚の標本棚・T11 大貝の護送・T12 真珠守りの試し) の動きの自己テスト。
// TrialCellLogic から呼ぶ。偽の人をセルの中に立たせ、tick と右クリックを動かして、決まり (教える決まり・ひねり・人数) が働くかを見る
final class TrialCellLogicP3 {
    private TrialCellLogicP3() {
    }

    private static Mods mods(int layout, int[] numbers, int enemy, int[] hazards, int goal) {
        return new Mods(layout, numbers, enemy, hazards, goal, Extreme.NONE, false, Rarity.COMMON);
    }

    private static FakePlayer player(ServerLevel level, int i) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1c00-0000-4000-8000-0000000f" + String.format("%04d", i)), "trialp3_" + i));
    }

    private static void stand(FakePlayer p, double x, double y, double z) {
        p.setPos(x, y, z);
        p.setOnGround(true);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
    }

    private static void check(List<String> fail, boolean ok, String what) {
        if (!ok) {
            fail.add(what);
        }
    }

    // ---------------------------------------------------------------- 岩鯨 W08 影絵の間
    static void whale(ServerLevel back, List<String> fail) {
        TrialCell cell = TrialCells.get("W08");
        FakePlayer p = player(back, 1);
        UUID group = Groups.keyOf(p);
        int y = WhaleDeck.Y + 1;
        // 教える決まり: 灯を右へ一つ動かすと影は左へ一つ、柱を右へ一つ動かすと影は右へ二つ
        for (int l = 0; l < 4; l++) {
            for (int q = 0; q < 4; q++) {
                check(fail, W08Shadows.shadowStart(l + 1, q) == W08Shadows.shadowStart(l, q) - 1, "W08: a lamp one step right should move the shadow one step left");
                check(fail, W08Shadows.shadowStart(l, q + 1) == W08Shadows.shadowStart(l, q) + 2, "W08: a pillar one step right should move the shadow two steps right");
            }
        }
        // ひねり: 二つの灯は、両方から遮られた所だけが影 (重なり)
        boolean[] two = W08Shadows.shadows(new int[] {2, 3}, new int[] {2});
        int dark = 0;
        for (boolean b : two) {
            dark += b ? 1 : 0;
        }
        check(fail, dark == 2, "W08: two lamps one step apart should leave a two-cell overlap, got " + dark);
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {0, 0}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 70L + layout, 0);
            try {
                long base = back.getGameTime();
                cell.arm(back, run);
                stand(p, -40.5, y, 105.5);
                cell.tick(back, run, List.of(p), base + 5);
                long now = base + 10;
                // 使わない台は何もしない
                int[] lampsBefore = W08Shadows.lamps(run).clone();
                cell.onUse(back, run, p, W08Shadows.lampSocket(2, 0), false, now);
                check(fail, java.util.Arrays.equals(lampsBefore, W08Shadows.lamps(run)), "W08 layout " + layout + ": an unused console row moved a lamp");
                int rounds = 3;
                for (int round = 0; round < rounds; round++) {
                    check(fail, W08Shadows.round(run) == round, "W08 layout " + layout + ": the screens did not reach round " + round + " (at " + W08Shadows.round(run) + ")");
                    W08Shadows.Puzzle pz = W08Shadows.puzzle(run);
                    if (pz == null) {
                        break;
                    }
                    int expectLamps = round == 0 ? 1 : round == 1 ? 2 : 2;
                    check(fail, pz.targetLamps().length >= expectLamps && (round > 0 || pz.targetPillars().length == 1),
                            "W08 round " + round + ": wrong number of lamps or pillars");
                    // まだ合っていない
                    cell.tick(back, run, List.of(p), now);
                    check(fail, !run.cleared && W08Shadows.round(run) == round, "W08 round " + round + ": the start already counted as solved");
                    int moved = 0;
                    for (int i = 0; i < pz.startLamps().length; i++) {
                        if (pz.startLamps()[i] != pz.targetLamps()[i]) {
                            cell.onUse(back, run, p, W08Shadows.lampSocket(i, pz.targetLamps()[i]), false, now += 5);
                            moved++;
                        }
                    }
                    for (int i = 0; i < pz.startPillars().length; i++) {
                        if (pz.startPillars()[i] != pz.targetPillars()[i]) {
                            cell.onUse(back, run, p, W08Shadows.pillarSocket(i, pz.targetPillars()[i]), false, now += 5);
                            moved++;
                        }
                    }
                    check(fail, moved == pz.par(), "W08 round " + round + ": par does not equal the number of pieces to move");
                    // 合ったら 2 秒そのまま: すぐには定まらず、保てば次へ
                    cell.tick(back, run, List.of(p), now += 5);
                    check(fail, W08Shadows.round(run) == round && !run.cleared, "W08 round " + round + ": the screen was set without holding the match");
                    cell.tick(back, run, List.of(p), now += W08Shadows.hold(m) + 5);
                    now += 5;
                }
                check(fail, run.cleared && GroupGates.isOpen(back, group, WhaleDeck.W08_EXIT), "W08 layout " + layout + ": three screens did not clear the trial and open the gate");
                check(fail, run.goalOk, "W08 layout " + layout + ": solving with the fewest moves should keep the few-moves goal (" + W08Shadows.clicks(run) + " clicks)");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                stand(p, 0, 0, 0);
            }
        }
        // 手数: ばらばらに何度も動かすと目標が外れる
        {
            Mods m = mods(0, new int[] {0, 0}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 75L, 0);
            try {
                long base = back.getGameTime();
                cell.arm(back, run);
                cell.tick(back, run, List.of(p), base + 5);
                long now = base + 10;
                for (int k = 0; k < 40; k++) {
                    cell.onUse(back, run, p, W08Shadows.lampSocket(0, k % 5), false, now += 5);
                    cell.onUse(back, run, p, W08Shadows.pillarSocket(0, (k + 2) % 5), false, now += 5);
                }
                for (int round = 0; round < 3; round++) {
                    W08Shadows.Puzzle pz = W08Shadows.puzzle(run);
                    for (int i = 0; i < pz.targetLamps().length; i++) {
                        cell.onUse(back, run, p, W08Shadows.lampSocket(i, pz.targetLamps()[i]), false, now += 5);
                    }
                    for (int i = 0; i < pz.targetPillars().length; i++) {
                        cell.onUse(back, run, p, W08Shadows.pillarSocket(i, pz.targetPillars()[i]), false, now += 5);
                    }
                    cell.tick(back, run, List.of(p), now += 5);
                    cell.tick(back, run, List.of(p), now += W08Shadows.hold(m) + 5);
                }
                check(fail, run.cleared && !run.goalOk, "W08: wandering moves should still clear but lose the few-moves goal");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                stand(p, 0, 0, 0);
            }
        }
        // 隙間風 (障害): 予告のあと、灯の一つが一つずれる
        {
            Mods m = mods(0, new int[] {1, 0}, -1, new int[] {0}, -1);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 76L, 0);
            try {
                long base = back.getGameTime();
                cell.arm(back, run);
                cell.tick(back, run, List.of(p), base + 5);
                int[] before = W08Shadows.lamps(run).clone();
                cell.tick(back, run, List.of(p), base + 900 - 20);
                check(fail, java.util.Arrays.equals(before, W08Shadows.lamps(run)), "W08: a lamp drifted before the draft's warning ended");
                cell.tick(back, run, List.of(p), base + 900 + 5);
                check(fail, !java.util.Arrays.equals(before, W08Shadows.lamps(run)), "W08: the draft did not shove a lamp");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                stand(p, 0, 0, 0);
            }
        }
        check(fail, cell.audit(back).isEmpty(), "W08 audit: " + cell.audit(back));
    }

    // ---------------------------------------------------------------- 潮殻
    static void tide(ServerLevel tide, List<String> fail) {
        t03(tide, fail);
        t04(tide, fail);
        t08(tide, fail);
        t11(tide, fail);
        t12(tide, fail);
    }

    // ---- T03 くらげの回廊
    private static void t03(ServerLevel tide, List<String> fail) {
        TrialCell cell = TrialCells.get("T03");
        FakePlayer p = player(tide, 11);
        FakePlayer q = player(tide, 12);
        UUID group = Groups.keyOf(p);
        int g = TideDeck.G;
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {2, 0}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 31L + layout, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                cell.tick(tide, run, List.of(p), base + 5);
                List<BlockPos> u0 = T03Jellies.umbrella(layout, 0);
                check(fail, !GroupGates.isOpen(tide, group, u0), "T03 layout " + layout + ": an umbrella is not standing at the start");
                BlockPos c = T03Jellies.center(layout, 0);
                int after = T03Jellies.sinkAfter(m, 1);
                long now = base + 10;
                // 教える決まり: 乗っていると沈む
                for (int i = 0; i < after / 5 + 6; i++, now += 5) {
                    stand(p, c.getX() + 0.5, g + 1, c.getZ() + 0.5);
                    cell.tick(tide, run, List.of(p), now);
                }
                check(fail, GroupGates.isOpen(tide, group, u0), "T03 layout " + layout + ": an umbrella did not sink under a player who kept standing on it");
                // 沈んだあとは戻る
                for (int i = 0; i < 40; i++, now += 5) {
                    cell.tick(tide, run, List.of(), now);
                }
                check(fail, !GroupGates.isOpen(tide, group, u0), "T03 layout " + layout + ": a sunk umbrella did not come back");
                // 二人で同じ傘に乗ると沈むのが遅い (押さえ合う)。二人が別々の傘に乗れば、片方の傘は先に沈む
                int after2 = T03Jellies.sinkAfter(m, 2);
                int ticks = after2 / 5 + 1;
                BlockPos c1 = T03Jellies.center(layout, 1);
                TrialCell.Run run2 = new TrialCell.Run(group, cell, m, 41L + layout, 0);
                cell.arm(tide, run2);
                long now2 = now + 20;
                for (int i = 0; i < ticks; i++, now2 += 5) {
                    stand(p, c.getX() + 0.2, g + 1, c.getZ() + 0.5);
                    stand(q, c.getX() + 0.8, g + 1, c.getZ() + 0.5);
                    cell.tick(tide, run2, List.of(p, q), now2);
                }
                check(fail, !GroupGates.isOpen(tide, group, u0), "T03 layout " + layout + ": two players on one umbrella should sink it more slowly");
                cell.disarm(tide, run2);
                TrialCell.Run run3 = new TrialCell.Run(group, cell, m, 42L + layout, 0);
                cell.arm(tide, run3);
                long now3 = now2 + 20;
                for (int i = 0; i < ticks; i++, now3 += 5) {
                    stand(p, c.getX() + 0.5, g + 1, c.getZ() + 0.5);
                    stand(q, c1.getX() + 0.5, g + 1, c1.getZ() + 0.5);
                    cell.tick(tide, run3, List.of(p, q), now3);
                }
                check(fail, GroupGates.isOpen(tide, group, u0), "T03 layout " + layout + ": an umbrella with one player should sink within the same time");
                cell.disarm(tide, run3);
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
            }
        }
        // ひねり: 触手の線は拍で払う (膨らんで 1.5 秒のあと)。帯の外は傷つかない
        {
            Mods m = mods(0, new int[] {1, 1}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 51L, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                cell.tick(tide, run, List.of(p), base + 5);
                int period = T03Jellies.sweepPeriod(m, 2);
                // 払う直前の予告 (膨らむ) では傷つかない
                stand(p, 8.5, g + 1, T03Jellies.ROW_Z[0] + 0.5);
                stand(q, 8.5, g + 1, T03Jellies.ROW_Z[0] - 3.0);
                float hp = p.getHealth();
                long warnAt = base + period - 25;
                cell.tick(tide, run, List.of(p, q), warnAt);
                check(fail, p.getHealth() == hp, "T03: a tentacle row hurt during its warning");
                // 払う: 帯の中の人は傷つき、外の人は傷つかない
                stand(p, 8.5, g + 1, T03Jellies.ROW_Z[0] + 0.5);
                stand(q, 8.5, g + 1, T03Jellies.ROW_Z[0] - 3.0);
                float hq = q.getHealth();
                cell.tick(tide, run, List.of(p, q), base + period - 5);
                check(fail, p.getHealth() < hp || p.getDeltaMovement().y > 0.2, "T03: a tentacle row did not strike a player inside it");
                check(fail, q.getHealth() == hq && q.getDeltaMovement().y < 0.2, "T03: a tentacle row struck a player outside it");
                // 水に落ちる (任意の目標)
                stand(p, 8.5, g + 0.2, 85.5);
                cell.tick(tide, run, List.of(p, q), base + 6 + period);
                check(fail, !run.goalOk && run.getInt("falls") == 1, "T03: falling in did not count against the no-dunk goal");
                // 向こうの岸に着けば済む
                stand(p, 8.5, g + 1, 109.5);
                cell.tick(tide, run, List.of(p, q), base + 11 + period);
                check(fail, run.cleared && GroupGates.isOpen(tide, group, TideDeck.T03_EXIT), "T03: the far shore did not clear the trial and open the gate");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
                stand(q, 0, 0, 0);
            }
        }
        // 小魚 (障害): つつかれた傘は倍の速さで沈む
        {
            Mods m = mods(0, new int[] {1, 1}, -1, new int[] {0}, -1);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 52L, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                cell.tick(tide, run, List.of(p), base + 5);
                int guard = 0;
                long now = base + 220;
                boolean nibbled = false;
                while (!nibbled && guard++ < 40) {
                    cell.tick(tide, run, List.of(), now);
                    now += 5;
                    nibbled = T03Jellies.nibbledNow(run, now) > 0;
                }
                check(fail, nibbled, "T03: the minnows never nibbled an umbrella");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
            }
        }
        check(fail, cell.audit(tide).isEmpty(), "T03 audit: " + cell.audit(tide));
    }

    // ---- T04 渦潮の滑り台
    private static void t04(ServerLevel tide, List<String> fail) {
        TrialCell cell = TrialCells.get("T04");
        FakePlayer p = player(tide, 13);
        UUID group = Groups.keyOf(p);
        int g = TideDeck.G;
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {1, 1}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 61L + layout, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                cell.tick(tide, run, List.of(p), base + 5);
                int period = T04Whirl.turn(m, 1);
                int[] lanes = T04Whirl.lanesNow(run, base + 5, 1);
                // 教える決まり: 前へ流れる水路は前へ、もう一本は後ろへ押す。溜まりは前へ
                for (int k = 0; k < 3; k++) {
                    double z = T04Whirl.J_START[k] + 3.5;
                    for (int lane = 0; lane < 2; lane++) {
                        double x = lane == 0 ? 23.5 : 28.5;
                        stand(p, x, g + 0.5, z);
                        cell.tick(tide, run, List.of(p), base + 10 + k * 5 + lane);
                        double dz = p.getDeltaMovement().z;
                        check(fail, lane == lanes[k] ? dz > 0 : dz < 0, "T04 layout " + layout + " junction " + k + " lane " + lane + ": wrong flow direction (dz " + dz + ")");
                    }
                }
                stand(p, 26.5, g + 0.5, T04Whirl.WATER_Z1 + 0.5);
                cell.tick(tide, run, List.of(p), base + 40);
                check(fail, p.getDeltaMovement().z > 0, "T04 layout " + layout + ": the pool before a junction should flow forward");
                // 前へ流れる水路は拍で入れ替わる。ひねり: 一つの分かれ道だけ位相が逆
                int[] later = T04Whirl.lanesNow(run, base + 5 + period, 1);
                for (int k = 0; k < 3; k++) {
                    check(fail, later[k] != lanes[k], "T04 layout " + layout + " junction " + k + ": the forward lane did not swap after a period");
                }
                int[] ph = T04Whirl.PHASES[layout];
                int diff = 0;
                for (int k = 1; k < 3; k++) {
                    diff += ph[k] != ph[0] ? 1 : 0;
                }
                check(fail, diff >= 1, "T04 layout " + layout + ": no junction is twisted");
                // 栓: 固めているあいだは入れ替わらない。終わればまた拍で動く
                int lockBefore = lanes[0];
                cell.onUse(tide, run, p, T04Whirl.VALVES[0], false, base + 45);
                int[] locked = T04Whirl.lanesNow(run, base + 45 + period, 1);
                check(fail, locked[0] == lockBefore || T04Whirl.lanesNow(run, base + 45, 1)[0] == locked[0], "T04 layout " + layout + ": a valve did not hold the lane");
                long afterLock = base + 45 + T04Whirl.lockTicks(m, 1) + 5;
                check(fail, T04Whirl.lanesNow(run, afterLock, 1)[0] == T04Whirl.forwardLane(layout, 0, afterLock - base, period), "T04 layout " + layout + ": the lane did not go back to the beat after the valve let go");
                // 押し戻された (任意の目標): 後ろへ流れる水路に 1.5 秒いた
                int back0 = 1 - T04Whirl.lanesNow(run, base + 200, 1)[1];
                long now = base + 200;
                for (int i = 0; i < 12; i++, now += 5) {
                    stand(p, back0 == 0 ? 23.5 : 28.5, g + 0.5, T04Whirl.J_START[1] + 3.5);
                    cell.tick(tide, run, List.of(p), now);
                }
                check(fail, T04Whirl.washes(run) == 1 && !run.goalOk, "T04 layout " + layout + ": a long stay in the back lane did not count as washed back");
                // 滑り終える
                stand(p, 26.5, g + 1, 113.5);
                cell.tick(tide, run, List.of(p), now + 5);
                check(fail, run.cleared && GroupGates.isOpen(tide, group, TideDeck.T04_EXIT), "T04 layout " + layout + ": landing did not clear the trial and open the gate");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
            }
        }
        // 人数: 一人は周期が長く栓が長く効き、三人からは短い
        Mods mm = mods(0, new int[] {1, 1}, -1, new int[0], 0);
        check(fail, T04Whirl.turn(mm, 1) > T04Whirl.turn(mm, 2) && T04Whirl.turn(mm, 2) > T04Whirl.turn(mm, 3), "T04: the swap interval should shrink with more players");
        check(fail, T04Whirl.lockTicks(mm, 1) > T04Whirl.lockTicks(mm, 3), "T04: the valve should hold shorter with more players");
        check(fail, cell.audit(tide).isEmpty(), "T04 audit: " + cell.audit(tide));
    }

    // ---- T08 珊瑚の標本棚
    private static void t08(ServerLevel tide, List<String> fail) {
        TrialCell cell = TrialCells.get("T08");
        FakePlayer p = player(tide, 14);
        UUID group = Groups.keyOf(p);
        int g = TideDeck.G;
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {0, 0}, -1, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 81L + layout, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                stand(p, 64.5, g + 1, 80.5);
                cell.tick(tide, run, List.of(p), base + 5);
                long now = base + 10;
                for (int round = 0; round < 3; round++) {
                    check(fail, T08Shelf.roundIndex(run) == round, "T08 layout " + layout + ": the rack did not reach round " + round);
                    T08Shelf.Round r = T08Shelf.round(run);
                    if (r == null) {
                        break;
                    }
                    // 決まり: 一つ目は色だけ・二つ目は丈だけ・三つ目は両方
                    for (int b = 0; b < 3; b++) {
                        int[] lab = r.labels()[b];
                        check(fail, round == 0 ? lab[0] >= 0 && lab[1] < 0 : round == 1 ? lab[0] < 0 && lab[1] > 0 : lab[0] >= 0 && lab[1] > 0,
                                "T08 round " + round + ": the plaque does not follow the rule");
                    }
                    // 一つ持っているあいだは、もう一つは持てない
                    int first = 0;
                    int second = 1;
                    cell.onUse(tide, run, p, T08Shelf.slot(T08Shelf.slotOf(run, first)), false, now += 5);
                    check(fail, T08Shelf.slotOf(run, first) < 0, "T08 round " + round + ": a piece was not picked up");
                    cell.onUse(tide, run, p, T08Shelf.slot(T08Shelf.slotOf(run, second)), false, now += 5);
                    check(fail, T08Shelf.slotOf(run, second) >= 0, "T08 round " + round + ": a second piece was picked up while holding one");
                    // 違う鉢: 片は棚へ戻り、数える
                    int want = T08Shelf.bowlFor(round, r.labels(), r.pieces().get(first));
                    int wrong = (want + 1) % (round == 2 ? 4 : 3);
                    int mistakes = T08Shelf.mistakes(run);
                    cell.onUse(tide, run, p, T08Shelf.bowl(wrong), false, now += 5);
                    check(fail, T08Shelf.slotOf(run, first) >= 0 && T08Shelf.mistakes(run) == mistakes + 1 && T08Shelf.placedCount(run) == 0,
                            "T08 round " + round + ": a wrong bowl did not send the piece back to the rack");
                    boolean usedCracked = false;
                    for (int i = 0; i < r.pieces().size(); i++) {
                        T08Shelf.Piece pc = r.pieces().get(i);
                        int b = T08Shelf.bowlFor(round, r.labels(), pc);
                        usedCracked |= b == 3;
                        cell.onUse(tide, run, p, T08Shelf.slot(T08Shelf.slotOf(run, i)), false, now += 5);
                        cell.onUse(tide, run, p, T08Shelf.bowl(b), false, now += 5);
                    }
                    if (round == 2) {
                        check(fail, usedCracked, "T08 layout " + layout + ": the last round never needed the cracked bowl");
                    }
                }
                check(fail, run.cleared && GroupGates.isOpen(tide, group, TideDeck.T08_EXIT), "T08 layout " + layout + ": sorting everything did not clear the trial and open the gate");
                check(fail, !run.goalOk, "T08 layout " + layout + ": a wrong bowl should have cost the no-return goal");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
            }
        }
        // 潮替え (障害): 予告のあとで二つの鉢の札が入れ替わる
        {
            Mods m = mods(0, new int[] {0, 0}, -1, new int[] {0}, -1);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 85L, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                stand(p, 64.5, g + 1, 80.5);
                cell.tick(tide, run, List.of(p), base + 5);
                int[][] before = new int[3][];
                for (int b = 0; b < 3; b++) {
                    before[b] = T08Shelf.round(run).labels()[b].clone();
                }
                cell.tick(tide, run, List.of(p), base + 700 - 20);
                cell.tick(tide, run, List.of(p), base + 700 + 5);
                int changed = 0;
                for (int b = 0; b < 3; b++) {
                    changed += java.util.Arrays.equals(before[b], T08Shelf.round(run).labels()[b]) ? 0 : 1;
                }
                check(fail, changed == 2, "T08: the tide swap should exchange two plaques, changed " + changed);
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
            }
        }
        check(fail, cell.audit(tide).isEmpty(), "T08 audit: " + cell.audit(tide));
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> mobIds(TrialCell.Run run) {
        return (List<UUID>) run.get("mobs", () -> new ArrayList<UUID>());
    }

    // ---- T11 大貝の護送
    private static void t11(ServerLevel tide, List<String> fail) {
        TrialCell cell = TrialCells.get("T11");
        FakePlayer p = player(tide, 15);
        UUID group = Groups.keyOf(p);
        int g = TideDeck.G;
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {0, 1}, 0, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 91L + layout, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                long now = base + 5;
                // 呼ぶ: 貝の前 (道の先) にいれば進む
                for (int i = 0; i < 10; i++, now += 5) {
                    double s = (Double) run.data.get("s");
                    double[] c = T11Clam.at(layout, s);
                    double[] h = T11Clam.heading(layout, s);
                    stand(p, c[0] + 0.5 + h[0] * 2.5, g + 1, c[1] + 0.5 + h[1] * 2.5);
                    cell.tick(tide, run, List.of(p), now);
                }
                double moved = (Double) run.data.get("s");
                check(fail, moved > 1.0, "T11 layout " + layout + ": the clam does not follow a player ahead of it (" + moved + ")");
                // 貝の後ろにいるだけでは進まない (呼ぶ人は前にいる)
                double[] c0 = T11Clam.at(layout, moved);
                double[] h0 = T11Clam.heading(layout, moved);
                stand(p, c0[0] + 0.5 - h0[0] * 2.0, g + 1, c0[1] + 0.5 - h0[1] * 2.0);
                for (int i = 0; i < 4; i++, now += 5) {
                    cell.tick(tide, run, List.of(p), now);
                }
                check(fail, (Double) run.data.get("s") == moved, "T11 layout " + layout + ": the clam moved with nobody ahead of it");
                // 襲撃: 道のりの 1/3 で一回目 (はさみ虫だけ)
                double len = T11Clam.length(layout);
                run.put("s", len / 3.0 + 0.2);
                double[] c1 = T11Clam.at(layout, len / 3.0 + 0.2);
                stand(p, c1[0] + 0.5 - 3.0, g + 1, c1[1] + 0.5 - 3.0);
                cell.tick(tide, run, List.of(p), now += 5);
                List<UUID> ids = mobIds(run);
                check(fail, run.getInt("trig") == 1 && !ids.isEmpty(), "T11 layout " + layout + ": the first raid did not come at a third of the way");
                boolean allScuttlers = true;
                for (UUID id : ids) {
                    Entity e = tide.getEntity(id);
                    allScuttlers &= e != null && e.getType() == jp.revati.skyabyss.titan.tide.mob.TideMobSetup.REEF_SCUTTLER.get();
                }
                check(fail, allScuttlers, "T11 layout " + layout + ": the first raid is not reef scuttlers only");
                // 囲まれる: そばの近接の敵は貝を傷め、動かさない
                Mob foe = ids.isEmpty() ? null : (Mob) tide.getEntity(ids.get(0));
                if (foe != null) {
                    foe.setNoAi(true);
                    for (UUID id : ids) {
                        Entity e = tide.getEntity(id);
                        if (e != null && e != foe) {
                            e.discard();
                        }
                    }
                    double sNow = (Double) run.data.get("s");
                    double[] c = T11Clam.at(layout, sNow);
                    double[] h = T11Clam.heading(layout, sNow);
                    foe.setPos(c[0] + 1.0, g + 1, c[1] + 0.5);
                    stand(p, c[0] + 0.5 + h[0] * 2.5, g + 1, c[1] + 0.5 + h[1] * 2.5);
                    double hp1 = (Double) run.data.get("hp");
                    cell.tick(tide, run, List.of(p), now += 5);
                    check(fail, (Double) run.data.get("s") == sNow && (Double) run.data.get("hp") < hp1, "T11 layout " + layout + ": a foe at the clam did not stop it and gnaw it");
                    check(fail, !run.goalOk, "T11: the unhurt goal survived a gnawed clam");
                    // 潮が満ちた: 最後の襲撃の場所まで流され、群れは消える
                    run.put("legEnd", now - 5);
                    cell.tick(tide, run, List.of(p), now += 5);
                    check(fail, (Double) run.data.get("s") < sNow && !foe.isAlive(), "T11 layout " + layout + ": the flood did not carry the clam back and clear the swarm");
                    foe.discard();
                }
                // 着く
                run.put("s", len + 1.0);
                run.put("trig", T11Clam.ROUTES.length * 10);
                cell.tick(tide, run, List.of(p), now += 5);
                check(fail, run.cleared && GroupGates.isOpen(tide, group, TideDeck.T11_EXIT), "T11 layout " + layout + ": arriving did not clear the trial and open the gate");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
            }
        }
        // 二回目の襲撃は、組に応じて泡吹きか下僕が加わる
        {
            Mods puff = mods(0, new int[] {0, 1}, 1, new int[0], -1);
            Mods thrall = mods(0, new int[] {0, 1}, 0, new int[0], -1);
            TrialCell.Run r1 = new TrialCell.Run(group, cell, puff, 1L, 0);
            TrialCell.Run r2 = new TrialCell.Run(group, cell, thrall, 1L, 0);
            boolean puffer = false;
            boolean thr = false;
            for (int j = 0; j < 6; j++) {
                puffer |= T11Clam.kind(r1, 2, j).equals("puffer");
                thr |= T11Clam.kind(r2, 2, j).equals("thrall");
                check(fail, T11Clam.kind(r1, 1, j).equals("scuttler") && T11Clam.kind(r2, 1, j).equals("scuttler"), "T11: the first raid should be scuttlers only");
            }
            check(fail, puffer && thr, "T11: the second raid should add pufferfish or thralls by the enemy set");
            check(fail, T11Clam.flood(thrall, 1) > T11Clam.flood(thrall, 4), "T11: the flood deadline should tighten with more players");
        }
        check(fail, cell.audit(tide).isEmpty(), "T11 audit: " + cell.audit(tide));
    }

    // ---- T12 真珠守りの試し
    private static void t12(ServerLevel tide, List<String> fail) {
        TrialCell cell = TrialCells.get("T12");
        FakePlayer p = player(tide, 16);
        FakePlayer q = player(tide, 17);
        FakePlayer r = player(tide, 18);
        UUID group = Groups.keyOf(p);
        int g = TideDeck.G;
        double mid = T12Keeper.CX + 0.5;
        double midz = T12Keeper.CZ + 0.5;
        // 一人: 幕で狭い広間 (幕が閉じ、守り手の舞台は小さい)
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {0, 0}, layout % 2, new int[0], 0);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 101L + layout, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                BlockPos c0 = T12Keeper.curtain().get(0);
                stand(p, mid, g + 1, midz - 9.0);
                cell.tick(tide, run, List.of(p), base + 5);
                check(fail, !T12Keeper.fightOn(run), "T12 layout " + layout + ": the fight began from the rim");
                check(fail, GroupGates.isOpen(tide, group, c0), "T12 layout " + layout + ": the curtain was closed before the fight");
                stand(p, mid, g + 1, midz - 3.0);
                cell.tick(tide, run, List.of(p), base + 10);
                check(fail, T12Keeper.fightOn(run), "T12 layout " + layout + ": stepping to the middle did not start the fight");
                PearlKeeper boss = T12Keeper.keeperOf(tide, run);
                check(fail, boss != null && boss.isAlive(), "T12 layout " + layout + ": no keeper rose (boss " + boss + ", alive " + (boss != null && boss.isAlive())
                        + ", hp " + (boss == null ? -1 : boss.getHealth()) + ", removed " + (boss == null ? null : boss.getRemovalReason()) + ")");
                if (boss == null) {
                    continue;
                }
                check(fail, T12Keeper.smallStage(run) && !GroupGates.isOpen(tide, group, c0), "T12 layout " + layout + ": one player should get the small stage with the curtain closed");
                check(fail, Math.abs(boss.getMaxHealth() - PearlKeeper.HEALTH * 0.3) < 1.0, "T12 layout " + layout + ": the keeper's health does not follow the setting");
                long now = base + 15;
                // 殻に籠もる: 群れが湧き、真珠を狙う。二度目は湧かない (同じ殻のうち)
                boss.forceAction("SHELL");
                cell.tick(tide, run, List.of(p), now);
                int expect = T12Keeper.brood(run, 1, 1);
                int got = 0;
                for (UUID id : T12Keeper.broodOf(run)) {
                    got += tide.getEntity(id) != null ? 1 : 0;
                }
                check(fail, got == expect, "T12 layout " + layout + ": the shell should call " + expect + " brood, found " + got);
                cell.tick(tide, run, List.of(p), now += 5);
                check(fail, T12Keeper.broodOf(run).size() == expect, "T12 layout " + layout + ": the same shell called the brood twice");
                // 群れが真珠のそばに居座ると、真珠が割れる (任意の目標が外れる)
                Mob camper = T12Keeper.broodOf(run).isEmpty() ? null : (Mob) tide.getEntity(T12Keeper.broodOf(run).get(0));
                if (camper != null) {
                    camper.setNoAi(true);
                    BlockPos pearl = T12Keeper.pearl(layout);
                    camper.setPos(pearl.getX() + 1.0, g + 1, pearl.getZ() + 0.5);
                    int hp0 = T12Keeper.pearlHp(run);
                    for (int i = 0; i < 14; i++, now += 5) {
                        stand(p, mid, g + 1, midz + 3.0);
                        cell.tick(tide, run, List.of(p), now);
                    }
                    check(fail, T12Keeper.pearlHp(run) < hp0 && !run.goalOk, "T12 layout " + layout + ": a camping brood did not crack the pearl");
                }
                // 守り手を倒す: 戸と幕が開く
                boss.kill();
                cell.tick(tide, run, List.of(p), now += 5);
                check(fail, run.cleared && GroupGates.isOpen(tide, group, TideDeck.T12_EXIT) && GroupGates.isOpen(tide, group, c0),
                        "T12 layout " + layout + ": beating the keeper did not clear the trial and open the gates");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
            }
        }
        // 三人: 全面 (幕は閉じない)・守り手の舞台は大きく、群れは多い
        {
            Mods m = mods(0, new int[] {1, 1}, 0, new int[0], -1);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 110L, 0);
            try {
                long base = tide.getGameTime();
                cell.arm(tide, run);
                BlockPos c0 = T12Keeper.curtain().get(0);
                stand(p, mid - 2, g + 1, midz - 3.0);
                stand(q, mid + 2, g + 1, midz - 3.0);
                stand(r, mid, g + 1, midz - 5.0);
                cell.tick(tide, run, List.of(p, q, r), base + 5);
                check(fail, T12Keeper.fightOn(run) && !T12Keeper.smallStage(run) && GroupGates.isOpen(tide, group, c0),
                        "T12: three players should fight on the whole hall with the curtain open");
                check(fail, T12Keeper.brood(run, 1, 3) > T12Keeper.brood(run, 1, 1), "T12: the brood should grow with more players");
                check(fail, T12Keeper.arenaRadius(3) > T12Keeper.arenaRadius(2), "T12: the stage should grow with the party");
                // 全員が舞台を出て 6 秒で、最初に戻る
                cell.tick(tide, run, List.of(), base + 10);
                cell.tick(tide, run, List.of(), base + 10 + 125);
                check(fail, !T12Keeper.fightOn(run) && T12Keeper.keeperOf(tide, run) == null, "T12: an abandoned fight did not reset");
            } finally {
                cell.disarm(tide, run);
                GroupGates.reset(tide, group);
                stand(p, 0, 0, 0);
                stand(q, 0, 0, 0);
                stand(r, 0, 0, 0);
            }
        }
        check(fail, cell.audit(tide).isEmpty(), "T12 audit: " + cell.audit(tide));
    }
}
