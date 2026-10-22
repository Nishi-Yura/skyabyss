package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialRuntime;
import jp.revati.skyabyss.trial.TrialSlot;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 潮殻のレイドの部屋 (TR1 真珠の灯り運び・TR2 貝の音合わせ・TR3 珊瑚の苗を守る) の自己テスト。SKYABYSS_SELFTEST_ONLY="tide raid"
// 二人組で順路を始め (部屋が三つ順に建つ)、各部屋を台本で解く。途中で全滅 (RaidRun.wipe) させて、部屋が最初の姿に戻ることも確かめる
public final class TideRaidSelfTest {
    private TideRaidSelfTest() {
    }

    private static final int LIMIT = 2400;
    private static final List<String> F = new ArrayList<>();
    private static ServerLevel back;
    private static UUID group;
    private static FakePlayer a;
    private static FakePlayer b;
    private static PartyData parties;
    private static CompoundTag snapshot;
    private static int phase;
    private static int ticks;
    private static int wipesBefore;
    private static boolean tr1Done;
    private static boolean tr2Done;
    private static boolean tr3Done;

    public static List<Step> steps() {
        return List.of(Step.once("tide raid (setup)", TideRaidSelfTest::setup), Step.until("tide raid TR1", s -> tr1(s)),
                Step.until("tide raid TR2", s -> tr2(s)), Step.until("tide raid TR3", s -> tr3(s)), Step.once("tide raid (end)", TideRaidSelfTest::end));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    private static FakePlayer fake(String id, String name) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString(id), name));
        p.setInvulnerable(true);
        return p;
    }

    private static void setup(MinecraftServer server) {
        F.clear();
        phase = 0;
        ticks = 0;
        tr1Done = false;
        tr2Done = false;
        tr3Done = false;
        try {
            jp.revati.skyabyss.dev.StructureDump.ensureBack(server, "tide_back");
            back = server.getLevel(Titans.backOf(Titans.TIDE_SHELL));
            if (back == null) {
                F.add("the tide back is missing");
                return;
            }
            parties = PartyData.get(server);
            snapshot = parties.snapshot(server.registryAccess());
            parties.clearAll();
            a = fake("7a0b1d00-0000-4000-8000-0000000007a1", "tide_a");
            b = fake("7a0b1d00-0000-4000-8000-0000000007b2", "tide_b");
            PartyData.Party party = parties.create(a.getUUID(), "tide_a");
            parties.add(party, b.getUUID(), "tide_b");
            group = Groups.keyOf(a);
            clean();
            TrialRoute.clearAll();
            List<ServerPlayer> both = List.of(a, b);
            TrialRoute.PLAYERS = l -> both;
            a.setPos(0, 70, 0);
            b.setPos(0, 70, 0);
            TrialRoute.start(back, group, Titans.TIDE_SHELL, both);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide raid setup crashed", e);
            F.add("setup crashed: " + e);
        }
    }

    private static void clean() {
        TrialRoute.finish(back, group);
        TrialRuntime.stopGroup(back, group);
        GroupGates.reset(back, group);
        BackGroupData.get(back).remove(group);
    }

    private static void end(MinecraftServer server) {
        try {
            if (back != null && group != null) {
                clean();
            }
            TrialRoute.clearAll();
            TrialSlot.clearAll();
        } finally {
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            if (parties != null && snapshot != null) {
                parties.restore(snapshot, server.registryAccess());
            }
        }
        if (!tr1Done) {
            F.add("TR1 was never solved");
        }
        if (!tr2Done) {
            F.add("TR2 was never solved");
        }
        if (!tr3Done) {
            F.add("TR3 was never solved");
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide raid PASSED (TR1 dark hall, pearl carry, hand-over, wipe reset; TR2 shell pairs, wrong pair no damage, wipe reset; TR3 sapling bites, loss resets, three waves)");
        } else {
            F.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST tide raid FAILED: {}", s));
        }
    }

    // いまの部屋 (その型で、動いている間だけ)
    private static <T> T room(Class<T> type) {
        RaidRun.Run run = group == null ? null : RaidRun.of(group);
        if (run == null || !type.isInstance(run.room) || run.phase != RaidRun.Phase.ACTIVE) {
            return null;
        }
        return type.cast(run.room);
    }

    private static RaidCtx ctx() {
        return RaidRun.of(group).ctx;
    }

    private static boolean timedOut(String what) {
        if (++ticks > LIMIT) {
            F.add(what + ": timed out in phase " + phase);
            return true;
        }
        return false;
    }

    private static void put(FakePlayer p, BlockPos at) {
        p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        p.setHealth(p.getMaxHealth());
    }

    private static void next() {
        phase++;
        ticks = 0;
    }

    private static boolean failed() {
        return back == null || group == null;
    }

    // ---------------------------------------------------------------- TR1
    private static boolean tr1(MinecraftServer server) {
        if (failed() || tr1Done) {
            return true;
        }
        TidePearlRoom r = room(TidePearlRoom.class);
        if (r == null) {
            return timedOut("TR1 (waiting for the room)");
        }
        RaidCtx c = ctx();
        BlockPos o = c.origin();
        switch (phase) {
            case 0 -> {
                check(RaidRunSlot.is(c, "TR1"), "step 1 is not TR1 (" + c.roomId() + ")");
                check(r.pearlCount() == 2, "two players should get 2 pearls, got " + r.pearlCount());
                // 暗い: 祭壇の近くの床は、灯りを持つ人がいなければ暗い
                int dark = back.getBrightness(LightLayer.BLOCK, o.offset(-12, 2, -10));
                check(dark <= 4, "the hall is not dark: block light " + dark + " at the far corner");
                check(c.cell().inner().maxY - c.cell().inner().minY >= 8, "the hall is too low");
                wipesBefore = RaidRun.of(group).wipes;
                next();
            }
            case 1 -> {
                // 持つと灯る
                put(a, r.pearlHome(0));
                put(b, o.offset(-12, 1, -10));
                if (r.carrierOf(0) != null && r.carrierOf(0).equals(a.getUUID()) && r.lit(a.getUUID())) {
                    int near = back.getBrightness(LightLayer.BLOCK, a.blockPosition().above());
                    check(near >= 10, "the carrier does not light the hall: block light " + near);
                    int far = back.getBrightness(LightLayer.BLOCK, o.offset(-12, 2, -10));
                    check(far <= 8, "the whole hall is lit by one carrier: " + far);
                    next();
                } else if (timedOut("TR1 pick up")) {
                    return true;
                }
            }
            case 2 -> {
                // 全滅 → 最初の姿へ
                RaidRun.wipe(RaidRun.of(group), null);
                next();
            }
            case 3 -> {
                if (RaidRun.of(group).phase == RaidRun.Phase.ACTIVE && RaidRun.of(group).wipes > wipesBefore) {
                    check(r.carrierOf(0) == null && !r.lit(a.getUUID()), "the wipe left a carrier / light");
                    check(r.counter() == 0, "the wipe left delivered pearls");
                    check(c.aliveMobs().size() <= 3 + 2 * c.scale(), "too many foes after the reset");
                    next();
                } else if (timedOut("TR1 wipe")) {
                    return true;
                }
            }
            case 4 -> {
                // 手渡し: a が真珠 0 を持ち、b のそばでしゃがむ
                put(a, r.pearlHome(0));
                if (r.carrierOf(0) != null && r.carrierOf(0).equals(a.getUUID())) {
                    put(b, a.blockPosition().east(2));
                    a.setShiftKeyDown(true);
                    next();
                } else if (timedOut("TR1 pick up again")) {
                    return true;
                }
            }
            case 5 -> {
                put(b, a.blockPosition().east(2));
                if (b.getUUID().equals(r.carrierOf(0))) {
                    a.setShiftKeyDown(false);
                    check(!r.lit(a.getUUID()) || r.lit(b.getUUID()), "the light stayed with the old carrier");
                    next();
                } else if (timedOut("TR1 hand-over")) {
                    return true;
                }
            }
            case 6 -> {
                // a は二つ目を取り、二人とも祭壇へ
                put(a, r.pearlHome(1));
                if (a.getUUID().equals(r.carrierOf(1))) {
                    next();
                } else if (timedOut("TR1 second pearl")) {
                    return true;
                }
            }
            case 7 -> {
                put(a, c.local(0, 1, TidePearlRoom.ALTAR_Z + 1));
                put(b, c.local(1, 1, TidePearlRoom.ALTAR_Z + 1));
                if (r.counter() >= r.counterMax()) {
                    check(r.isComplete(), "all pearls delivered but the room is not complete");
                    tr1Done = true;
                    ticks = 0;
                    phase = 0;
                    return true;
                } else if (timedOut("TR1 altar")) {
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- TR2
    private static boolean tr2(MinecraftServer server) {
        if (failed() || tr2Done) {
            return true;
        }
        TideShellRoom r = room(TideShellRoom.class);
        if (r == null) {
            return timedOut("TR2 (waiting for the room)");
        }
        RaidCtx c = ctx();
        switch (phase) {
            case 0 -> {
                check(RaidRunSlot.is(c, "TR2"), "step 2 is not TR2 (" + c.roomId() + ")");
                check(r.pairs() == 4 && r.shells() == 8, "two players should get 4 pairs, got " + r.pairs());
                int[] seen = new int[5];
                for (int i = 0; i < r.shells(); i++) {
                    seen[r.toneOf(i)]++;
                }
                for (int t = 0; t < r.pairs(); t++) {
                    check(seen[t] == 2, "tone " + t + " has " + seen[t] + " shells, not 2");
                }
                // 違う音の二つを、実際の入口 (onUse) から開ける。傷はなく、閉じる
                int i = 0;
                int j = 1;
                while (r.toneOf(j) == r.toneOf(i)) {
                    j++;
                }
                float hp = a.getHealth();
                check(r.onUse(c, a, r.shellPos(c, i), false), "onUse did not take a shell click");
                check(!r.onUse(c, a, c.origin().offset(-HX_EDGE, 1, 0), true), "onUse took a click on the wall");
                r.open(c, j);
                check(r.mistakes() == 1, "a wrong pair was not counted");
                check(r.isOpen(i) && r.isOpen(j), "a wrong pair did not stay open to be seen");
                r.open(c, firstOther(r, i, j));
                check(!r.isOpen(firstOther(r, i, j)), "a third shell opened while the wrong pair was showing");
                check(a.getHealth() >= hp, "a wrong pair hurt the player");
                next();
            }
            case 1 -> {
                if (noneOpen(r)) {
                    // 一組だけ揃えて、全滅 → 全部閉じる
                    int i = 0;
                    int j = -1;
                    for (int k = 1; k < r.shells(); k++) {
                        if (r.toneOf(k) == r.toneOf(i)) {
                            j = k;
                        }
                    }
                    r.open(c, i);
                    r.open(c, j);
                    check(r.counter() == 1 && r.isMatched(i) && r.isMatched(j), "a matching pair did not stay open");
                    wipesBefore = RaidRun.of(group).wipes;
                    RaidRun.wipe(RaidRun.of(group), null);
                    next();
                } else if (timedOut("TR2 wrong pair closing")) {
                    return true;
                }
            }
            case 2 -> {
                if (RaidRun.of(group).phase == RaidRun.Phase.ACTIVE && RaidRun.of(group).wipes > wipesBefore) {
                    boolean any = false;
                    for (int k = 0; k < r.shells(); k++) {
                        any |= r.isOpen(k);
                    }
                    check(!any && r.counter() == 0, "the wipe left shells open");
                    next();
                } else if (timedOut("TR2 wipe")) {
                    return true;
                }
            }
            case 3 -> {
                // 解く: 音ごとに二つ
                for (int t = 0; t < r.pairs(); t++) {
                    int first = -1;
                    for (int k = 0; k < r.shells(); k++) {
                        if (r.toneOf(k) == t) {
                            if (first < 0) {
                                first = k;
                                r.open(c, k);
                            } else {
                                r.open(c, k);
                            }
                        }
                    }
                }
                check(r.isComplete(), "all pairs opened but the room is not complete");
                tr2Done = true;
                ticks = 0;
                phase = 0;
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static boolean noneOpen(TideShellRoom r) {
        for (int k = 0; k < r.shells(); k++) {
            if (r.isOpen(k)) {
                return false;
            }
        }
        return true;
    }

    private static final int HX_EDGE = TideShellRoom.HX;

    private static int firstOther(TideShellRoom r, int i, int j) {
        for (int k = 0; k < r.shells(); k++) {
            if (k != i && k != j) {
                return k;
            }
        }
        return i;
    }

    // ---------------------------------------------------------------- TR3
    private static boolean tr3(MinecraftServer server) {
        if (failed() || tr3Done) {
            return true;
        }
        TideSaplingRoom r = room(TideSaplingRoom.class);
        if (r == null) {
            return timedOut("TR3 (waiting for the room)");
        }
        RaidCtx c = ctx();
        switch (phase) {
            case 0 -> {
                check(RaidRunSlot.is(c, "TR3"), "step 3 is not TR3 (" + c.roomId() + ")");
                check(r.secondaryMax() == 90 && r.secondary() == 90, "sapling HP should start at 90/90, got " + r.secondary() + "/" + r.secondaryMax());
                // 人を遠くへ (敵は苗へ向かう)
                put(a, c.cell().rest());
                put(b, c.cell().rest());
                next();
            }
            case 1 -> {
                // 敵が湧き、苗をかじる (体力が減る)
                put(a, c.cell().rest());
                put(b, c.cell().rest());
                if (r.secondary() < r.secondaryMax()) {
                    check(!c.aliveMobs().isEmpty(), "saplings were bitten but no foe is alive");
                    next();
                } else if (timedOut("TR3 bite")) {
                    return true;
                }
            }
            case 2 -> {
                // 三本とも尽きる → 最初から
                wipesBefore = RaidRun.of(group).wipes;
                for (int s = 0; s < TideSaplingRoom.SAPLINGS; s++) {
                    r.damage(c, s, 1000);
                }
                next();
            }
            case 3 -> {
                if (RaidRun.of(group).phase == RaidRun.Phase.ACTIVE && RaidRun.of(group).wipes > wipesBefore) {
                    check(r.secondary() == r.secondaryMax() && r.wave() == 0, "losing every sapling did not reset the room: hp " + r.secondary() + ", wave " + r.wave());
                    next();
                } else if (timedOut("TR3 loss reset")) {
                    return true;
                }
            }
            case 4 -> {
                // 三つの波を退ける: 湧いた敵をその場で倒す
                put(a, c.cell().rest());
                put(b, c.cell().rest());
                for (Mob m : new ArrayList<>(c.aliveMobs())) {
                    m.discard();
                }
                if (r.isComplete()) {
                    check(r.secondary() == r.secondaryMax(), "saplings lost HP although every foe died on arrival");
                    tr3Done = true;
                    ticks = 0;
                    phase = 0;
                    return true;
                } else if (timedOut("TR3 waves")) {
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 部屋の id の確認
    private static final class RaidRunSlot {
        static boolean is(RaidCtx c, String id) {
            return c.roomId().equals(id);
        }
    }
}
