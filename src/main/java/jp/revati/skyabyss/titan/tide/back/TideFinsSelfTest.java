package jp.revati.skyabyss.titan.tide.back;

import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupGates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// ひれの道の自己テスト (26 の 9 章): 建て (貝の台・戸・先端の台・宝)、貝の仕掛け (間違えると最初から、合えば戸がそのグループにだけ開く、
// 潮の向き・壁の読み・鏡)、戸を開けると到着から先端まで歩いて行けること。 SKYABYSS_SELFTEST_ONLY="tide back (build),tide fins"
public final class TideFinsSelfTest {
    private static final List<String> FAILURES = new ArrayList<>();

    private TideFinsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("tide fins", 20, server -> {
            ServerLevel back = server.getLevel(jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK);
            FAILURES.clear();
            if (back == null) {
                SkyAbyss.LOGGER.error("SELFTEST tide fins FAILED: tide_back is missing");
                return;
            }
            try {
                run(back);
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST tide fins crashed", e);
                FAILURES.add("crashed: " + e);
            }
            if (FAILURES.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST tide fins PASSED");
            } else {
                FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide fins FAILED: {}", f));
            }
        }));
    }

    private static void check(boolean ok, String m) {
        if (!ok) {
            FAILURES.add(m);
        }
    }

    private static void run(ServerLevel back) {
        TideFins.clear();
        TideBackTrials.resetGroup(back, TideBackSelfTest.TEST, false, null, true);
        // 建て: 貝の台は色つきの石、戸は封印の扉 (ワールドでは閉じている)、先端の台に宝箱
        for (TideFins.Room r : TideFins.ROOMS) {
            for (int i = 0; i < r.pedZ().length; i++) {
                check(back.getBlockState(r.pedestal(i)).getBlock().getDescriptionId().contains("concrete"), "pedestal missing " + r.id() + " " + i);
            }
            for (BlockPos g : r.gate()) {
                check(GroupGates.isGate(back, g) && !jp.revati.skyabyss.titan.tide.back.TideGates.defaultOpen(back, g), "gate not shut " + r.id() + " " + g.toShortString());
            }
        }
        for (String id : new String[] {"fin_w_tip", "fin_e_tip"}) {
            boolean found = false;
            for (TideCaches.Cache c : TideCaches.CACHES) {
                if (c.id().equals(id)) {
                    found = back.getBlockState(c.chest()).is(Blocks.CHEST);
                }
            }
            check(found, "cache chest missing " + id);
        }
        TideBackSelfTest.TestPlayer p = TideBackSelfTest.join(back, TideBackSelfTest.TEST, "[TideFins]", ARRIVAL);
        try {
            java.util.UUID group = jp.revati.skyabyss.titan.group.Groups.keyOf(p);
            for (TideFins.Room r : TideFins.ROOMS) {
                GroupGates.reset(back, group);
                TideFins.clear();
                int n = r.pedZ().length;
                TideClock.force(TideClock.Phase.LOW);
                // 間違い: 正しい最初の貝でない貝を叩くと 0 (最初から)
                int wrong = (r.expected(0, 1) + 1) % n;
                check(TideFins.press(back, p, r, wrong) == 0, r.id() + ": a wrong shell was accepted");
                // 正しい順 (引き潮)
                int res = 0;
                for (int k = 0; k < n; k++) {
                    res = TideFins.press(back, p, r, r.expected(k, 1));
                }
                check(res == 2, r.id() + ": the right order did not solve it at the ebb (last result " + res + ")");
                check(GroupGates.isOpen(back, group, r.gate()), r.id() + ": the gate did not open for the group");
                check(!jp.revati.skyabyss.titan.tide.back.TideGates.defaultOpen(back, r.gate().get(0)), r.id() + ": the gate opened for everyone");
                if (r.mode() == TideFins.Mode.ASC_TIDE) {
                    // 満ち潮は降り: 昇りの順は違う、降りの順で解ける
                    GroupGates.reset(back, group);
                    TideFins.clear();
                    TideClock.force(TideClock.Phase.HIGH);
                    check(TideFins.press(back, p, r, r.expected(0, 1)) == 0 || n == 1, r.id() + ": the ebb order worked at the flood");
                    TideFins.clear();
                    int last = 0;
                    for (int k = 0; k < n; k++) {
                        last = TideFins.press(back, p, r, r.expected(k, -1));
                    }
                    check(last == 2 && GroupGates.isOpen(back, group, r.gate()), r.id() + ": the flood order did not solve it at the flood");
                    TideClock.force(TideClock.Phase.LOW);
                }
            }
            // 全部の戸が開いているなら、到着から先端まで歩いて行ける
            TideBackSelfTest.walkGroup = group;
            for (TideFins.Room r : TideFins.ROOMS) {
                GroupGates.open(back, group, r.gate());
            }
            Map<Long, Integer> dist = TideBackSelfTest.walk(back, ARRIVAL);
            TideBackSelfTest.walkGroup = null;
            for (int side : new int[] {-1, 1}) {
                BlockPos tip = TideFins.tipCenter(side).offset(0, 1, 0);
                check(dist.containsKey(tip.asLong()), "cannot walk to the " + (side < 0 ? "west" : "east") + " fin tip " + tip.toShortString() + " " + closest(dist, tip));
            }
            // 戸が閉じている間は、一つ目の戸の先へ行けない
            GroupGates.reset(back, group);
            TideBackSelfTest.walkGroup = group;
            Map<Long, Integer> shut = TideBackSelfTest.walk(back, ARRIVAL);
            TideBackSelfTest.walkGroup = null;
            for (TideFins.Room r : TideFins.ROOMS) {
                BlockPos in = r.center();
                boolean second = r.u1() == TideFins.ROOM2_U1;
                check(second ? !shut.containsKey(in.asLong()) : shut.containsKey(in.asLong()), "room " + r.id() + " is " + (second ? "reachable with the first gate shut" : "not reachable at all"));
            }
        } finally {
            TideClock.force(null);
            TideBackSelfTest.leave(back, p);
            TideBackTrials.resetGroup(back, TideBackSelfTest.TEST, false, null, true);
            TideFins.clear();
        }
    }

    private static String closest(Map<Long, Integer> dist, BlockPos g) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (long k : dist.keySet()) {
            BlockPos q = BlockPos.of(k);
            double d = q.distSqr(g);
            if (d < bd) {
                bd = d;
                best = q;
            }
        }
        return "(closest " + best + ")";
    }
}
