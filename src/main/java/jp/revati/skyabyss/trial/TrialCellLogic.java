package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.whale.W03Moss;
import jp.revati.skyabyss.trial.whale.W04Vents;
import jp.revati.skyabyss.trial.whale.W11Coffin;
import jp.revati.skyabyss.trial.whale.W12Arena;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 新作のセルの動きの自己テスト (trial pool から呼ぶ)。偽の人をセルの中に立たせ、セルの tick を動かして、決まりが働くかを見る:
// 苔の流れが向きへ押す・入れ替わる・穴に落ちたら最後の島へ戻る / 噴気孔が吹き上げのあいだだけ昇らせる / 棺が押されると進み、囲まれると進まず傷み、
// 傷みきると戻る・着けば戸が開く / 闘技場の波・崩れ・幕で半分・出口の橋。
final class TrialCellLogic {
    private TrialCellLogic() {
    }

    private static Mods mods(int layout, int[] numbers, int enemy, int[] hazards, int goal, boolean quiet) {
        return new Mods(layout, numbers, enemy, hazards, goal, Extreme.NONE, quiet, Rarity.COMMON);
    }

    private static FakePlayer player(ServerLevel back, int i) {
        return FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1c00-0000-4000-8000-0000000e" + String.format("%04d", i)), "trialcell" + i));
    }

    private static void stand(FakePlayer p, double x, double y, double z) {
        p.setPos(x, y, z);
        p.setOnGround(true);
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        p.setHealth(p.getMaxHealth());
    }

    static void run(MinecraftServer server, List<String> fail) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            return;
        }
        w03(back, fail);
        w04(back, fail);
        w11(back, fail);
        w12(back, fail);
    }

    private static void check(List<String> fail, boolean ok, String what) {
        if (!ok) {
            fail.add(what);
        }
    }

    // ---------------------------------------------------------------- W03 苔の滑り坂
    private static void w03(ServerLevel back, List<String> fail) {
        TrialCell cell = TrialCells.get("W03");
        FakePlayer p = player(back, 1);
        UUID group = Groups.keyOf(p);
        Mods m = mods(0, new int[] {1, 1}, -1, new int[0], 0, false);
        TrialCell.Run run = new TrialCell.Run(group, cell, m, 7L, 0);
        try {
            cell.arm(back, run);
            long t = 100;
            // 流れ 0 は東 (配置 0)。苔の床の上で、東へ押される
            stand(p, -40.5, 120, 133.5);
            cell.tick(back, run, List.of(p), t);
            check(fail, p.getDeltaMovement().x > 0.15, "W03: the first current does not push east (" + p.getDeltaMovement() + ")");
            // 流れ 1 は西
            stand(p, -40.5, 119, 138.5);
            cell.tick(back, run, List.of(p), t + 5);
            check(fail, p.getDeltaMovement().x < -0.15, "W03: the second current does not push west (" + p.getDeltaMovement() + ")");
            // 島の上 (流れの外) では押されない・光って残る
            stand(p, -46 + 3 + 0.5, 119, 135.5);
            cell.tick(back, run, List.of(p), t + 10);
            check(fail, Math.abs(p.getDeltaMovement().x) < 0.01, "W03: a bone island pushed the player");
            check(fail, run.getInt("lit") == 1, "W03: the island did not light after the player stood on it");
            // 落ちる: 穴の列の島のない所。最後に立った島 (列 0) へ戻る
            stand(p, -45.5, 117.0, 136.5);
            cell.tick(back, run, List.of(p), t + 15);
            // (偽の人は通信がなく、teleportTo が位置を動かさないので、戻る先は覚えている島で見る)
            @SuppressWarnings("unchecked")
            java.util.Map<UUID, BlockPos> stops = (java.util.Map<UUID, BlockPos>) run.data.get("stops");
            BlockPos last = stops.get(p.getUUID());
            check(fail, last != null && last.getX() == -46 + 3 && last.getY() == 119 && last.getZ() == 135 && run.getInt("falls") == 1 && !run.goalOk,
                    "W03: a fall did not count or the last island was not remembered: " + last + " falls " + run.getInt("falls"));
            // 流れの入れ替わり: 三本目 (流れ 2) は時で向きが変わる。流れ 0 は変わらない
            int every = W03Moss.flipEvery(run, 1);
            boolean flips = false;
            for (long tt = 0; tt < every * 3L; tt += 5) {
                flips |= W03Moss.dirAt(run, 2, tt, 1) != W03Moss.dirAt(run, 2, 0, 1);
                check(fail, W03Moss.dirAt(run, 0, tt, 1) == W03Moss.DIRS[0][0] && W03Moss.dirAt(run, 1, tt, 1) == W03Moss.DIRS[0][1], "W03: a taught current changed direction");
                // 入れ替わりの前には必ず予告が出る (予告なしの入れ替わりはない)
                if (W03Moss.dirAt(run, 2, tt + 5, 1) != W03Moss.dirAt(run, 2, tt, 1)) {
                    check(fail, W03Moss.warning(run, 2, tt, 1), "W03: a reversal came without a warning at " + tt);
                }
            }
            check(fail, flips, "W03: the third current never reverses");
            // 人数: 三人からは入れ替わりが速い・一人は流れが弱い
            check(fail, W03Moss.flipEvery(run, 3) < W03Moss.flipEvery(run, 1), "W03: the reversals do not speed up with three or more");
            // 出口の広間に着けば済む
            stand(p, -40.5, 115, 157.5);
            cell.tick(back, run, List.of(p), t + 20);
            check(fail, run.cleared, "W03: reaching the exit hall did not clear the trial");
        } finally {
            cell.disarm(back, run);
            GroupGates.reset(back, group);
            stand(p, 0, 0, 0);
        }
        // 全配置: 島は端の壁に付かず、島の間は届く (audit と同じ。世界を見る)
        check(fail, cell.audit(back).isEmpty(), "W03 audit: " + cell.audit(back));
    }

    // ---------------------------------------------------------------- W04 噴気孔の昇り
    private static void w04(ServerLevel back, List<String> fail) {
        TrialCell cell = TrialCells.get("W04");
        FakePlayer p = player(back, 2);
        UUID group = Groups.keyOf(p);
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {2, 2}, -1, new int[0], 0, false);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 9L, 0);
            try {
                cell.arm(back, run);
                for (int i = 0; i < 3; i++) {
                    W04Vents.Vent v = W04Vents.vent(layout, i);
                    // いちばんきびしい数字でも、吹き上げが昇りきれる長さ (36 tick 以上) で、休みと予告が残る
                    for (int players = 1; players <= 6; players++) {
                        int burst = W04Vents.burst(m, players, false);
                        check(fail, burst >= 36, "W04: the blast is too short to climb with " + players + " players (" + burst + ")");
                    }
                    // 吹き上げのあいだ、台の上にいる人に昇りが付く。休みのあいだは付かない
                    long t0 = -1;
                    long t1 = -1;
                    for (long t = 0; t < 400; t += 5) {
                        int s = W04Vents.state(m, i, t, 1, false);
                        if (s == 2 && t0 < 0) {
                            t0 = t;
                        }
                        if (s == 0 && t1 < 0) {
                            t1 = t;
                        }
                    }
                    check(fail, t0 >= 0 && t1 >= 0, "W04: vent " + i + " never alternates between blowing and resting");
                    stand(p, v.cx() + 0.5, v.padY() + 1, v.cz() + 0.5);
                    p.removeAllEffects();
                    cell.tick(back, run, List.of(p), run.startTick + t0);
                    check(fail, p.hasEffect(MobEffects.LEVITATION), "W04 layout " + layout + ": vent " + i + " did not lift during a blast");
                    p.removeAllEffects();
                    cell.tick(back, run, List.of(p), run.startTick + t1);
                    check(fail, !p.hasEffect(MobEffects.LEVITATION), "W04 layout " + layout + ": vent " + i + " lifted while resting");
                    // 昇り先の高さまで来たら、昇りは止まって、ふわりと降りる
                    stand(p, v.cx() + 0.5, v.targetStand() + 2.0, v.cz() + 0.5);
                    p.removeAllEffects();
                    cell.tick(back, run, List.of(p), run.startTick + t0);
                    check(fail, !p.hasEffect(MobEffects.LEVITATION) && p.hasEffect(MobEffects.SLOW_FALLING), "W04 layout " + layout + ": vent " + i
                            + " does not stop at its ledge height and let the player float down");
                }
                // 栓の台: 吹き上げが長くなる
                check(fail, W04Vents.burst(m, 2, true) > W04Vents.burst(m, 2, false), "W04: the plug pad does not lengthen the blast");
                // 出口の岩棚に着けば済む
                stand(p, -20 + 0.5, 120, 129 + 20 + 0.5);
                cell.tick(back, run, List.of(p), run.startTick + 10);
                check(fail, run.cleared && GroupGates.isOpen(back, group, WhaleDeck.W04_EXIT), "W04 layout " + layout + ": the exit ledge did not clear the trial and open the gate");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                p.removeAllEffects();
                stand(p, 0, 0, 0);
            }
        }
        // 人数で吹き上げは短くなる
        Mods std = mods(0, new int[] {1, 1}, -1, new int[0], -1, false);
        check(fail, W04Vents.burst(std, 1, false) > W04Vents.burst(std, 3, false) && W04Vents.burst(std, 3, false) > W04Vents.burst(std, 6, false),
                "W04: the blast does not shorten with more players");
    }

    // ---------------------------------------------------------------- W11 棺の護送
    private static void w11(ServerLevel back, List<String> fail) {
        TrialCell cell = TrialCells.get("W11");
        FakePlayer p = player(back, 3);
        UUID group = Groups.keyOf(p);
        for (int layout = 0; layout < 3; layout++) {
            Mods m = mods(layout, new int[] {0, 1}, 0, new int[0], 0, false);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 11L, 0);
            try {
                cell.arm(back, run);
                double[] c0 = W11Coffin.at(layout, 0);
                stand(p, c0[0] + 0.5, WhaleDeck.Y + 1, c0[1] + 0.5);
                long now = 5;
                // 押す: 近くに人がいれば進む
                for (int i = 0; i < 12; i++, now += 5) {
                    cell.tick(back, run, List.of(p), now);
                    double[] c = W11Coffin.at(layout, (Double) run.data.get("s"));
                    stand(p, c[0] + 0.5, WhaleDeck.Y + 1, c[1] + 0.5);
                }
                double sPushed = (Double) run.data.get("s");
                check(fail, sPushed > 1.0, "W11 layout " + layout + ": the coffin does not move when pushed (" + sPushed + ")");
                // 手を放す: 人がいなければ進まない
                stand(p, 9.5, WhaleDeck.Y + 1, 164.5);
                double before = (Double) run.data.get("s");
                for (int i = 0; i < 6; i++, now += 5) {
                    cell.tick(back, run, List.of(p), now);
                }
                check(fail, (Double) run.data.get("s") == before, "W11 layout " + layout + ": the coffin moved with nobody near it");
                // 囲まれる: 棺のそばに近接の敵がいると進まず、傷む
                double[] c = W11Coffin.at(layout, (Double) run.data.get("s"));
                BlockPos at = new BlockPos((int) c[0] + 1, WhaleDeck.Y + 1, (int) c[1]);
                Mob foe = TitanBackTrials.spawnGuard(back, "crawler", at, group);
                check(fail, foe != null, "W11: could not spawn a test foe");
                if (foe != null) {
                    mobIds(run).add(foe.getUUID());
                    run.get("melee", () -> new java.util.HashMap<UUID, Boolean>()).put(foe.getUUID(), true);
                    foe.setNoAi(true);
                    foe.setPos(c[0] + 1.2, WhaleDeck.Y + 1, c[1] + 0.5);
                    stand(p, c[0] + 0.5, WhaleDeck.Y + 1, c[1] + 1.5);
                    double s1 = (Double) run.data.get("s");
                    double hp1 = (Double) run.data.get("hp");
                    cell.tick(back, run, List.of(p), now);
                    now += 5;
                    check(fail, (Double) run.data.get("s") == s1 && (Double) run.data.get("hp") < hp1, "W11 layout " + layout
                            + ": a foe at the coffin did not stop it and gnaw it");
                    check(fail, !run.goalOk, "W11: the unscratched goal survived a gnawed coffin");
                    // 傷みきる: 最後の群れの場所まで戻り、敵は消え、傷みは満ちる
                    run.put("hp", 0.05);
                    cell.tick(back, run, List.of(p), now);
                    now += 5;
                    check(fail, (Double) run.data.get("hp") > 10 && !foe.isAlive(), "W11: a broken coffin did not reset");
                    foe.discard();
                }
                // 着く
                run.put("s", 1000.0);
                cell.tick(back, run, List.of(p), now);
                check(fail, run.cleared && GroupGates.isOpen(back, group, WhaleDeck.W11_EXIT), "W11 layout " + layout + ": arriving did not clear the trial and open the gate");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                stand(p, 0, 0, 0);
            }
        }
        check(fail, cell.audit(back).isEmpty(), "W11 audit: " + cell.audit(back));
    }

    // ---------------------------------------------------------------- W12 鯨骨の闘技場
    private static void w12(ServerLevel back, List<String> fail) {
        TrialCell cell = TrialCells.get("W12");
        W12Arena arena = (W12Arena) cell;
        for (int pass = 0; pass < 2; pass++) {
            boolean small = pass == 0;
            int count = small ? 1 : 3;
            List<FakePlayer> ps = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ps.add(player(back, 10 + i));
            }
            UUID group = Groups.keyOf(ps.get(0));
            int layout = pass == 0 ? 0 : 2;
            Mods m = mods(layout, new int[] {0, 0}, 0, new int[0], 0, false);
            TrialCell.Run run = new TrialCell.Run(group, cell, m, 13L, 0);
            String tag = "W12 " + (small ? "solo" : "full") + ": ";
            try {
                cell.arm(back, run);
                List<ServerPlayer> inside = new ArrayList<>(ps);
                for (FakePlayer p : ps) {
                    stand(p, WhaleDeck.W12_CX + 0.5, WhaleDeck.Y + 1, WhaleDeck.W12_CZ + 0.5);
                }
                long now = 10;
                cell.tick(back, run, inside, now);
                now += 5;
                cell.tick(back, run, inside, now);
                check(fail, run.getInt("phase") == 3, tag + "the fight did not start once everyone was on the floor");
                boolean stage0Gone = GroupGates.isOpen(back, group, arena.cellsOfStage(layout, 0).get(0));
                check(fail, stage0Gone == small, tag + "stage 0 floor should be " + (small ? "gone from the start" : "intact") + " (open=" + stage0Gone + ")");
                // 波 1
                now += 60;
                cell.tick(back, run, inside, now);
                int wave1 = mobIds(run).size();
                check(fail, run.getInt("wave") == 1 && wave1 >= 3, tag + "wave 1 did not spawn (" + wave1 + ")");
                // 人数で敵が増える
                if (!small) {
                    check(fail, wave1 > 3 + 0, tag + "three players did not raise the foe count (" + wave1 + ")");
                }
                kill(back, run);
                for (int k = 0; k < 40 && run.getInt("wave") < 3; k++) {
                    now += 5;
                    cell.tick(back, run, inside, now);
                    if (run.getInt("phase") == 1 && !mobIds(run).isEmpty()) {
                        kill(back, run);
                    }
                    now += 40;
                }
                check(fail, run.getInt("wave") == 3, tag + "the waves did not reach 3 (wave " + run.getInt("wave") + ", phase " + run.getInt("phase") + ")");
                boolean stage1Gone = GroupGates.isOpen(back, group, arena.cellsOfStage(layout, 1).get(0));
                check(fail, stage1Gone, tag + "stage 1 floor did not collapse before the last wave");
                check(fail, GroupGates.isOpen(back, group, arena.cellsOfStage(layout, 0).get(0)), tag + "stage 0 floor is back");
                kill(back, run);
                now += 5;
                cell.tick(back, run, inside, now);
                check(fail, run.cleared && !GroupGates.isOpen(back, group, W12Arena.BRIDGE_OUT.get(0)), tag + "the last wave did not clear the arena and raise the exit bridge");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                for (FakePlayer p : ps) {
                    stand(p, 0, 0, 0);
                }
            }
        }
        check(fail, cell.audit(back).isEmpty(), "W12 audit: " + cell.audit(back));
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> mobIds(TrialCell.Run run) {
        return (List<UUID>) run.get("mobs", () -> new ArrayList<UUID>());
    }

    private static void kill(ServerLevel back, TrialCell.Run run) {
        for (UUID id : new ArrayList<>(mobIds(run))) {
            Entity e = back.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
    }
}
