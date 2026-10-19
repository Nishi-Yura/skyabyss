package jp.revati.skyabyss.trial.ember;

import static jp.revati.skyabyss.titan.ember.back.EmberDeck.Y;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackTrials;
import jp.revati.skyabyss.titan.ember.back.EmberBodyBuilder;
import jp.revati.skyabyss.titan.ember.back.EmberDeck;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.trial.TrialCell;
import jp.revati.skyabyss.trial.TrialCells;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialPoolSelfTest;
import jp.revati.skyabyss.trial.TrialRegions;
import jp.revati.skyabyss.trial.TrialRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 余燼の試練の階 (E02 噴気の渡り・E06 煙突の風読み・E07 三つの鞴・E10 鞴持ちの炉。docs/design/29 の 3.4) の自己テスト。
// 段の名前は "trial pool ember" (SKYABYSS_SELFTEST_ONLY="trial pool" の中に入る。背を建てるので数分かかる)。
//  - 引き方: 余燼のプール (歩 2・智 3・闘 2)・周回 1 は原型・周回 2 から一つずつ・四つの新作が数周で全部出る・修飾の範囲と符号
//  - 置き場: 試練の階の箱が体の中に 3 ブロック以上埋まっている・ほかの部屋と重ならない・背の亀裂から離れている
//  - 全変種: 配置 × 数字 × 敵 × 障害 × 任意 × 静 で arm → 見かけがセルの中 → tick → disarm
//  - 動き (偽の人): 噴気は光ってから噴いて一度だけ当たる・煙は抜け口へ傾く (逆風は逆)・間違いは熱とロック・踏み板は重りと炎の衰え・鞴持ちは熱を上げ、倒すと止まる
//  - 世界: 戸 (原型の洞の口も) は引いた三つだけ開く・石と休み場がそのセルの範囲に入る・セルの audit・四つの石から広間へ戻れる
public final class EmberDeckSelfTest {
    private EmberDeckSelfTest() {
    }

    private static final List<String> FAIL = new ArrayList<>();
    private static final String EMBER = Titans.EMBER;

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAIL.add(what);
        }
    }

    public static List<Step> steps() {
        return List.of(
                Step.once("trial pool ember (build)", server -> {
                    FAIL.clear();
                    ServerLevel back = server.getLevel(EmberIds.BACK);
                    if (back == null) {
                        FAIL.add("ember_back dimension is missing");
                        return;
                    }
                    EmberDeck.setPlaced(true);
                    EmberBodyBuilder.ensureBuilt(back);
                    TitanBackData data = TitanBackData.get(back);
                    check(data.isBuilt(), "the ember back is not built");
                    EmberBackTrials.placeFixtures(back);
                }),
                Step.once("trial pool ember", 20, EmberDeckSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        try {
            ServerLevel back = server.getLevel(EmberIds.BACK);
            TrialRuntime.clearAll();
            pure();
            layout();
            variants(back);
            e02(back);
            e06(back);
            e07(back);
            e10(back);
            world(back);
        } catch (RuntimeException e) {
            FAIL.add("exception " + e);
            SkyAbyss.LOGGER.error("SELFTEST trial pool ember exception", e);
        } finally {
            TrialRuntime.clearAll();
            EmberDeck.setPlaced(true);
        }
        if (FAIL.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial pool ember PASSED ({} ms)", System.currentTimeMillis() - t0);
        } else {
            FAIL.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST trial pool ember FAILED: {}", p));
        }
    }

    // ---------------------------------------------------------------- 引き方・修飾
    private static void pure() {
        EmberDeck.setPlaced(true);
        check(TrialCatalog.poolActive(EMBER), "the ember pool is not active with the deck placed");
        check(TrialCatalog.originals(EMBER).stream().map(Def::id).toList().equals(List.of("E01", "E05", "E09")), "ember originals are not E01 E05 E09");
        check(TrialCatalog.inSlot(EMBER, Slot.WALK).size() == 2 && TrialCatalog.inSlot(EMBER, Slot.WIT).size() == 3 && TrialCatalog.inSlot(EMBER, Slot.FIGHT).size() == 2,
                "the ember slots are not 2 / 3 / 2");
        for (String id : List.of("E02", "E06", "E07", "E10")) {
            Def d = TrialCatalog.get(id);
            check(d != null && d.titan().equals(EMBER) && !d.original() && !d.physical(), id + " is not a registered, non-physical new ember trial");
            check(TrialCells.get(id) != null && TrialCells.get(id).runtime(), id + " has no runtime cell");
            check(d != null && d.space().layouts() == 3 && d.space().numbers().size() == 2 && !d.space().goals().isEmpty() && !d.space().hazards().isEmpty(),
                    id + " does not have layouts 3 / numbers 2 / a hazard / a goal");
        }
        check(TrialCatalog.get("E10").space().enemySets() == 2, "E10 needs two enemy sets");
        check(TrialCatalog.get("E02").slot() == Slot.WALK && TrialCatalog.get("E06").slot() == Slot.WIT && TrialCatalog.get("E07").slot() == Slot.WIT
                && TrialCatalog.get("E10").slot() == Slot.FIGHT, "an ember trial is in the wrong slot");
        // 置かないあいだは原型のまま (本番の安全)
        UUID g = new UUID(3, 4);
        EmberDeck.setPlaced(false);
        check(!TrialCatalog.poolActive(EMBER), "the ember pool drew with the deck not placed");
        check(TrialDraw.draw(EMBER, 5, 4242L, g, new TrialDraw.Memory()).picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("E01", "E05", "E09")),
                "the ember draw is not the originals with the deck not placed");
        EmberDeck.setPlaced(true);
        // 周回 1 は原型・周回 2 から枠ごとに一つ
        check(TrialDraw.draw(EMBER, 1, 4242L, g, new TrialDraw.Memory()).picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("E01", "E05", "E09")),
                "ember cycle 1 is not the originals");
        Set<String> seen = new HashSet<>();
        for (int trial = 0; trial < 40; trial++) {
            UUID grp = new UUID(trial, 91);
            TrialDraw.Memory m = new TrialDraw.Memory();
            TrialDraw.draw(EMBER, 1, 4242L, grp, m);
            java.util.Map<Slot, String> prev = new java.util.EnumMap<>(Slot.class);
            prev.put(Slot.WALK, "E01");
            prev.put(Slot.WIT, "E05");
            prev.put(Slot.FIGHT, "E09");
            for (int cycle = 2; cycle <= 7; cycle++) {
                TrialDraw.Draw dr = TrialDraw.draw(EMBER, cycle, 4242L, grp, m);
                check(dr.picks().size() == 3, "ember cycle " + cycle + " did not draw three");
                for (Slot s : Slot.values()) {
                    TrialDraw.Pick p = dr.inSlot(s);
                    check(p != null && p.def().slot() == s && p.def().titan().equals(EMBER), "ember cycle " + cycle + " slot " + s + " is missing or wrong");
                    if (p != null) {
                        check(!p.cellId().equals(prev.get(s)), "ember slot " + s + " repeated " + p.cellId());
                        prev.put(s, p.cellId());
                        if (trial < 12) {
                            seen.add(p.cellId());
                        }
                    }
                }
            }
        }
        check(seen.containsAll(List.of("E02", "E06", "E07", "E10", "E01", "E05", "E09")), "not every ember trial was drawn within seven cycles: " + seen);
        // 修飾: 範囲・符号の往復・深淵の数式
        Random rng = new Random(777);
        for (String id : List.of("E02", "E06", "E07", "E10")) {
            Def d = TrialCatalog.get(id);
            for (int i = 0; i < 1500; i++) {
                Mods m = TrialDraw.rollMods(d, rng);
                check(Mods.decode(m.encode()).encode().equals(m.encode()), id + " mods do not round-trip");
                check(m.layout() >= 0 && m.layout() < 3 && m.numbers().length == 2 && m.goal() < d.space().goals().size(), id + " mods out of range");
                for (int h : m.hazards()) {
                    check(h >= 0 && h < d.space().hazards().size(), id + " hazard out of range");
                }
                if (d.space().enemySets() > 0) {
                    check(m.enemy() >= 0 && m.enemy() < d.space().enemySets(), id + " enemy set out of range");
                }
                Mods ab = TrialDraw.abyssify(d, m, new Random(i));
                check(ab.abyss() && ab.numbers().length == 2 && java.util.Arrays.stream(ab.numbers()).allMatch(x -> x == 2) && ab.goal() >= 0 && ab.hazards().length >= 1,
                        id + " abyss is not the strictest with a goal and a hazard: " + ab.encode());
            }
        }
        // 噴気の渡り: 渡れる間は常に 1.2 秒以上 (audit が段・深淵・6 人で調べる)
        check(E02Vents.warnOf(new Mods(0, new int[] {2, 2}, -1, new int[0], -1, Extreme.NONE, false, Rarity.ABYSS)) >= EmberCellKit.WARN_MIN, "E02 warning under 0.8 s");
        // 人数で決まる: 同じ人数なら同じ。1 < 3 < 5
        Mods lone = new Mods(0, new int[] {1, 1}, 0, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        TrialCell.Run run10 = new TrialCell.Run(g, TrialCells.get("E10"), lone, 1L, 0);
        check(E10Furnace.bearers(run10, 1) < E10Furnace.bearers(run10, 3) && E10Furnace.bearers(run10, 3) < E10Furnace.bearers(run10, 5)
                && E10Furnace.bearers(run10, 1) == E10Furnace.bearers(run10, 2), "the furnace's bearers do not scale with the number inside");
        check(E02Vents.periodOf(lone, 1) > E02Vents.periodOf(lone, 6) && E02Vents.periodOf(lone, 6) >= 70, "the vent period does not shrink with entrants (or breaks the floor)");
    }

    // ---------------------------------------------------------------- 置き場
    private static void layout() {
        List<EmberBackLayout.Box> deck = EmberDeck.ROOMS;
        for (int i = 0; i < deck.size(); i++) {
            for (int j = i + 1; j < deck.size(); j++) {
                check(!deck.get(i).intersects(deck.get(j)), "deck rooms " + i + " and " + j + " overlap");
            }
        }
        for (var e : EmberBackLayout.ROOMS.entrySet()) {
            if (e.getKey().startsWith("deck_")) {
                continue;
            }
            for (EmberBackLayout.Box b : deck) {
                check(!b.grow(2).intersects(e.getValue()), "a deck room is within 2 blocks of " + e.getKey());
            }
        }
        check(deck.stream().noneMatch(b -> b.grow(3).intersects(EmberBackLayout.CRACK)), "a deck room is within 3 blocks of the back crack");
        check(deck.stream().noneMatch(b -> b.grow(3).intersects(EmberBackLayout.BACK_TUBE)), "a deck room is within 3 blocks of the back tube");
        // 体の中に埋まっている (体の表の 3 ブロック以上内側。元の体の表で見る)
        int outside = 0;
        BlockPos first = null;
        for (EmberBackLayout.Box b : deck) {
            EmberBackLayout.Box g = b.grow(3);
            for (int x = g.minX(); x <= g.maxX(); x++) {
                for (int z = g.minZ(); z <= g.maxZ(); z++) {
                    for (int y = g.minY(); y <= g.maxY(); y++) {
                        if (!EmberBodyBuilder.bodyAt(x, y, z)) {
                            outside++;
                            if (first == null) {
                                first = new BlockPos(x, y, z);
                            }
                        }
                    }
                }
            }
        }
        check(outside == 0, "the deck is not buried 3 blocks inside the body: " + outside + " voxels outside, first at " + first);
        // 休み場・石・戸・板が正しい箱の中
        for (var r : EmberDeck.RESTS) {
            check(EmberBackLayout.ROOMS.get(EmberDeck.roomOfRest(r.name())).contains(r.floor()), "rest " + r.name() + " is outside " + EmberDeck.roomOfRest(r.name()));
        }
        check(EmberDeck.A.contains(EmberDeck.E02_STONE) && EmberDeck.B.contains(EmberDeck.E10_STONE) && EmberDeck.C.contains(EmberDeck.E06_STONE)
                && EmberDeck.D.contains(EmberDeck.E07_STONE), "a stone is outside its cell");
        check(EmberDeck.A.contains(EmberDeck.E02_PAD) && EmberDeck.B.contains(EmberDeck.E10_PAD) && EmberDeck.C.contains(EmberDeck.E06_PAD) && EmberDeck.D.contains(EmberDeck.E07_PAD),
                "a return pad is outside its cell");
        check(EmberDeck.D0.contains(EmberDeck.BOARD_D0), "the deck board is outside the hall");
        check(EmberBackLayout.HUB.contains(EmberDeck.BOARD_HUB), "the hub board is outside the hub");
    }

    // ---------------------------------------------------------------- 全変種
    private static void variants(ServerLevel back) {
        int total = 0;
        for (TrialCell cell : TrialCells.of(EMBER)) {
            if (!cell.runtime()) {
                continue;
            }
            check(cell.audit(back).isEmpty(), cell.def().id() + " audit: " + cell.audit(back));
            AABB box = cell.ports().area().inflate(3);
            int n = 0;
            for (Mods m : TrialPoolSelfTest.variantsOf(cell.def())) {
                n++;
                UUID group = new UUID(0xE7B0, n);
                TrialCell.Run run = new TrialCell.Run(group, cell, m, 4242L + n, 0);
                try {
                    cell.arm(back, run);
                    Set<BlockPos> seen = new HashSet<>();
                    cell.overlay(back, run, (pos, st) -> seen.add(pos));
                    for (BlockPos p : seen) {
                        if (!box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) {
                            FAIL.add(cell.def().id() + " variant " + m.encode() + " overlays a block outside its cell at " + p);
                            break;
                        }
                    }
                    for (long t = 5; t <= 25; t += 5) {
                        cell.tick(back, run, List.of(), t);
                    }
                } finally {
                    cell.disarm(back, run);
                    GroupGates.reset(back, group);
                }
                if (FAIL.size() > 30) {
                    return;
                }
            }
            SkyAbyss.LOGGER.info("SELFTEST trial pool ember variants {}: {}", cell.def().id(), n);
            total += n;
        }
        check(total >= 700, "too few ember variants were enumerated: " + total);
    }

    // ---------------------------------------------------------------- 偽の人
    private static FakePlayer fake(ServerLevel back, int n) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1c00-0000-4000-8000-0000000e" + String.format("%04d", n)), "emberdeck_" + n));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        EmberHeat.reset(p);
        return p;
    }

    private static void stand(FakePlayer p, double x, double z) {
        p.setPos(x, Y + 1, z);
        p.setOnGround(true);
    }

    private static Mods mods(int layout, int n0, int n1, int enemy, int[] hazards, int goal) {
        return new Mods(layout, new int[] {n0, n1}, enemy, hazards, goal, Extreme.NONE, false, hazards.length > 0 ? Rarity.RARE : Rarity.COMMON);
    }

    // E02: 光る → 噴く → 一度だけ当たる。渡り切ると済む
    private static void e02(ServerLevel back) {
        E02Vents cell = (E02Vents) TrialCells.get("E02");
        FakePlayer p = fake(back, 2);
        UUID group = Groups.keyOf(p);
        TrialCell.Run run = new TrialCell.Run(group, cell, mods(0, 1, 1, -1, new int[0], 0), 11L, 0);
        try {
            cell.arm(back, run);
            long base = run.getLong("base");
            int warn = E02Vents.warnOf(run.mods);
            stand(p, E02Vents.cx(0) + 0.5, E02Vents.cz(0) + 0.5);
            cell.tick(back, run, List.of(p), base - 10);
            check(run.getInt("burns") == 0, "E02: burned before the base time");
            int[] states = run.get("states", () -> new int[E02Vents.BANDS * E02Vents.LANES]);
            cell.tick(back, run, List.of(p), base + 2);
            check(states[0] == 1, "E02: the patch did not glow first (state " + states[0] + ")");
            check(run.getInt("burns") == 0 && EmberHeat.get(p) == 0f, "E02: the glow itself burned the player");
            Set<BlockPos> glow = new HashSet<>();
            cell.overlay(back, run, (pos, st) -> glow.add(pos));
            check(glow.contains(new BlockPos(E02Vents.cx(0), Y, E02Vents.cz(0))), "E02: a glowing patch has no overlay");
            cell.tick(back, run, List.of(p), base + warn + 4);
            check(states[0] == 2, "E02: the patch did not burst after the glow");
            check(run.getInt("burns") == 1 && EmberHeat.get(p) >= 20f, "E02: the burst did not burn (burns " + run.getInt("burns") + ", heat " + EmberHeat.get(p) + ")");
            check(!run.goalOk, "E02: the goal 'never burned' stayed ok after a burn");
            cell.tick(back, run, List.of(p), base + warn + 9);
            check(run.getInt("burns") == 1, "E02: one burst hit twice");
            cell.tick(back, run, List.of(p), base + warn + 40);
            check(states[0] == 0 && run.getInt("burns") == 1, "E02: the burst did not end / burned while quiet");
            int period = run.getInt("period");
            cell.tick(back, run, List.of(p), base + period + warn + 4);
            check(run.getInt("burns") == 2, "E02: the next burst did not hit");
            // 一筋おきの帯 (配置 0 の 3 番目の帯): 隣の筋は同時に噴かない
            int band = 2;
            check(E02Vents.PATTERN[0][band] == 1, "E02: layout 0 band 2 is not the striped band");
            long at = base + warn + 4;
            int period2 = run.getInt("period");
            int a = E02Vents.phaseAt(run, band, 0, at);
            int b = E02Vents.phaseAt(run, band, 1, at);
            check(a != b || a == 0, "E02: neighbouring lanes of the striped band burst together");
            // 渡り切る
            stand(p, 24.5, 62.5);
            cell.tick(back, run, List.of(p), base + 1000);
            check(run.cleared, "E02: reaching the far bank did not clear");
            check(period2 > 0, "E02: no period");
        } finally {
            cell.disarm(back, run);
            GroupGates.reset(back, group);
        }
        // 障害: 火の粉蛾が出て、片付けで消える
        TrialCell.Run moth = new TrialCell.Run(group, cell, mods(1, 1, 1, -1, new int[] {1}, -1), 12L, 0);
        try {
            cell.arm(back, moth);
            cell.tick(back, moth, List.of(p), 100);
            int n = back.getEntitiesOfClass(EmberMoth.class, EmberDeck.A.aabb().inflate(2)).size();
            check(n >= 2, "E02: the moths hazard did not spawn moths (" + n + ")");
        } finally {
            cell.disarm(back, moth);
        }
        check(back.getEntitiesOfClass(EmberMoth.class, EmberDeck.A.aabb().inflate(2)).isEmpty(), "E02: moths stayed after disarm");
        EmberHeat.reset(p);
    }

    // E06: 煙は抜け口へ傾く (逆風は逆)。間違いは熱とロック。正しい蓋で進み、全部で炉の扉が開く
    private static void e06(ServerLevel back) {
        E06Flues cell = (E06Flues) TrialCells.get("E06");
        FakePlayer p = fake(back, 6);
        UUID group = Groups.keyOf(p);
        stand(p, -24.5, 28.5);
        for (int layout = 0; layout < 3; layout++) {
            TrialCell.Run run = new TrialCell.Run(group, cell, mods(layout, 1, 1, -1, new int[0], 0), 21L + layout, 0);
            try {
                cell.arm(back, run);
                long now = 1000;
                cell.tick(back, run, List.of(p), now);
                int rounds = E06Flues.rounds(run);
                check(rounds == 3, "E06: expected three rounds at the standard level, got " + rounds);
                for (int round = 1; round <= rounds; round++) {
                    boolean[] active = run.get("active", () -> new boolean[E06Flues.SITES]);
                    boolean[] decoy = run.get("decoy", () -> new boolean[E06Flues.SITES]);
                    int flue = run.getInt("flue") - 1;
                    int mode = run.getInt("mode");
                    check(run.getInt("round") == round, "E06: round counter " + run.getInt("round") + " != " + round);
                    check(flue >= 0 && active[flue] && !decoy[flue], "E06: the flue is not an active, non-decoy chimney");
                    int live = 0;
                    int decoys = 0;
                    for (int s = 0; s < E06Flues.SITES; s++) {
                        live += active[s] ? 1 : 0;
                        decoys += decoy[s] ? 1 : 0;
                    }
                    check(round != 1 || live == 4 && decoys == 0, "E06: the first round is not four plain chimneys (" + live + "/" + decoys + ")");
                    check(round == 1 || live >= 5 && decoys >= 1, "E06: a later round has no decoy (" + live + "/" + decoys + ")");
                    check(mode == (round >= 3 ? 1 : 0), "E06: round " + round + " has the wrong draft direction " + mode);
                    int leaners = 0;
                    for (int s = 0; s < E06Flues.SITES; s++) {
                        if (!active[s]) {
                            continue;
                        }
                        double[] d = E06Flues.lean(run, s, now);
                        if (s == flue || decoy[s]) {
                            check(d[0] == 0 && d[1] == 0, "E06: the flue or a decoy does not rise straight");
                            continue;
                        }
                        leaners++;
                        double tx = E06Flues.siteX(layout, flue) - E06Flues.siteX(layout, s);
                        double tz = E06Flues.siteZ(flue) - E06Flues.siteZ(s);
                        double dot = (d[0] * tx + d[1] * tz) / Math.hypot(tx, tz);
                        check(mode == 0 ? dot > 0.99 : dot < -0.99, "E06: smoke leans the wrong way in round " + round + " (dot " + dot + ")");
                    }
                    check(leaners >= 3, "E06: fewer than three leaning plumes in round " + round);
                    if (round == 1) {
                        // 間違えると熱とロック。ロック中は正しい蓋も開かない
                        int wrong = -1;
                        for (int s = 0; s < E06Flues.SITES; s++) {
                            if (active[s] && s != flue) {
                                wrong = s;
                                break;
                            }
                        }
                        float heat0 = EmberHeat.get(p);
                        check(cell.onUse(back, run, p, E06Flues.lid(layout, wrong), false, now), "E06: a wrong lid was not handled");
                        check(EmberHeat.get(p) >= heat0 + 15f, "E06: a wrong lid gave no heat");
                        check(!run.goalOk && run.getInt("mistakes") == 1, "E06: a wrong lid did not break the goal");
                        cell.onUse(back, run, p, E06Flues.lid(layout, flue), false, now + 10);
                        check(run.getLong("pauseUntil") == 0, "E06: the lock did not hold");
                        now += 80;
                    }
                    check(cell.onUse(back, run, p, E06Flues.lid(layout, flue), false, now), "E06: the true lid was not handled");
                    check(run.getLong("pauseUntil") > 0, "E06: the true lid did not start the next round");
                    now += 60;
                    cell.tick(back, run, List.of(p), now);
                    now += 5;
                }
                check(run.cleared, "E06: layout " + layout + " did not clear after the last flue");
                check(GroupGates.isOpen(back, group, EmberDeck.E06_EXIT), "E06: the furnace door did not open");
            } finally {
                cell.disarm(back, run);
                GroupGates.reset(back, group);
                EmberHeat.reset(p);
            }
        }
    }

    // E07: 踏み板・炎の衰え・ひび・重り・人数
    private static void e07(ServerLevel back) {
        E07Bellows cell = (E07Bellows) TrialCells.get("E07");
        FakePlayer a = fake(back, 71);
        FakePlayer b = fake(back, 72);
        UUID group = Groups.keyOf(a);
        TrialCell.Run run = new TrialCell.Run(group, cell, mods(0, 1, 1, -1, new int[0], 0), 31L, 0);
        try {
            cell.arm(back, run);
            long now = 2000;
            int grace = E07Bellows.graceOf(run.mods);
            int hold = E07Bellows.holdOf(run.mods);
            int[] lvl = run.get("lvl", () -> new int[4]);
            cell.tick(back, run, List.of(a, b), now);
            // 第一の口: 踏み板 0・1。一人だけでは足りない
            stand(a, E07Bellows.PAD_X[0] + 0.5, E07Bellows.PAD_Z[0] + 0.5);
            stand(b, -24.5, 70.5);
            for (long t = 5; t <= hold + 30; t += 5) {
                cell.tick(back, run, List.of(a, b), now + t);
            }
            check(run.getInt("stage") == 1 && lvl[0] == 100 && lvl[1] == 0, "E07: one pad alone opened the mouth or did not light (stage " + run.getInt("stage") + ")");
            // 炎の衰え: 離れると grace のあいだに LIT を切る (0.6〜1.0 倍)
            stand(a, -24.5, 70.5);
            int steps = 0;
            now += hold + 40;
            while (lvl[0] >= E07Bellows.LIT && steps < 400) {
                steps++;
                cell.tick(back, run, List.of(a, b), now + steps * 5L);
            }
            check(steps * 5 >= grace * 0.6 && steps * 5 <= grace, "E07: the flame fades in " + steps * 5 + " ticks, expected about " + grace);
            now += steps * 5L + 10;
            // 二人で第一の口
            stand(a, E07Bellows.PAD_X[0] + 0.5, E07Bellows.PAD_Z[0] + 0.5);
            stand(b, E07Bellows.PAD_X[1] + 0.5, E07Bellows.PAD_Z[1] + 0.5);
            for (long t = 5; t <= hold + 30; t += 5) {
                cell.tick(back, run, List.of(a, b), now + t);
            }
            check(run.getInt("stage") == 2, "E07: two pads held did not open the first mouth");
            now += hold + 40;
            check(E07Bellows.padsOf(run).length == 3, "E07: the second mouth is not three pads for two entrants");
            // 第二の口: 一人 + 重り二つ (しゃがんで右クリック)
            int[] pads = E07Bellows.padsOf(run);
            int leaky = E07Bellows.LEAKY[0];
            int free = -1;
            for (int pd : pads) {
                if (pd != leaky) {
                    free = pd;
                    break;
                }
            }
            int weighted = 0;
            for (int pd : pads) {
                if (pd != leaky && pd != free) {
                    check(cell.onUse(back, run, a, new BlockPos(E07Bellows.PAD_X[pd], Y, E07Bellows.PAD_Z[pd]), true, now), "E07: a weight was not handled");
                    weighted++;
                }
            }
            check(weighted == 1, "E07: expected one pad for a weight besides the leaky one");
            check(cell.onUse(back, run, a, new BlockPos(E07Bellows.PAD_X[free], Y, E07Bellows.PAD_Z[free]), true, now), "E07: the second weight was not handled");
            check(run.getInt("weightsLeft") == 0, "E07: weights left " + run.getInt("weightsLeft"));
            check(cell.onUse(back, run, a, new BlockPos(E07Bellows.PAD_X[leaky], Y, E07Bellows.PAD_Z[leaky]), true, now), "E07: a third weight click was not handled");
            check(!run.get("weight", () -> new boolean[4])[leaky], "E07: a third weight was set");
            // ひび割れた鞴は一人が立つ。決まりでは重りの二つで残りを留められる
            stand(a, E07Bellows.PAD_X[leaky] + 0.5, E07Bellows.PAD_Z[leaky] + 0.5);
            for (long t = 5; t <= hold + 30; t += 5) {
                cell.tick(back, run, List.of(a), now + t);
            }
            check(run.cleared, "E07: one player and two weights did not open the last mouth");
            check(GroupGates.isOpen(back, group, EmberDeck.E07_EXIT), "E07: the far door did not open");
        } finally {
            cell.disarm(back, run);
            GroupGates.reset(back, group);
        }
        // 四人以上なら第二の口は踏み板四つ
        TrialCell.Run four = new TrialCell.Run(group, cell, mods(2, 1, 1, -1, new int[0], -1), 32L, 0);
        try {
            cell.arm(back, four);
            four.put("stage", 2);
            four.put("entrants", 4);
            check(E07Bellows.padsOf(four).length == 4, "E07: four entrants do not get four pads");
            four.put("entrants", 1);
            check(E07Bellows.padsOf(four).length == 3, "E07: a lone player does not get three pads");
        } finally {
            cell.disarm(back, four);
        }
        // 灰降り: 踏み板が灰に埋まり、炎が消える
        TrialCell.Run ash = new TrialCell.Run(group, cell, mods(0, 1, 1, -1, new int[] {0}, -1), 33L, 0);
        try {
            cell.arm(back, ash);
            long now = 5000;
            stand(a, E07Bellows.PAD_X[0] + 0.5, E07Bellows.PAD_Z[0] + 0.5);
            stand(b, E07Bellows.PAD_X[1] + 0.5, E07Bellows.PAD_Z[1] + 0.5);
            long[] ashUntil = ash.get("ashUntil", () -> new long[4]);
            boolean any = false;
            for (long t = 0; t < 900; t += 5) {
                cell.tick(back, ash, List.of(a, b), now + t);
                for (long u : ashUntil) {
                    any |= u > now + t;
                }
                if (any) {
                    break;
                }
            }
            check(any, "E07: the ashfall hazard never buried a pad");
        } finally {
            cell.disarm(back, ash);
            GroupGates.reset(back, group);
        }
    }

    // E10: 鞴持ちが生きているあいだ熱が上がり、噴き、倒すと止まる。波を重ねて済む
    private static void e10(ServerLevel back) {
        E10Furnace cell = (E10Furnace) TrialCells.get("E10");
        FakePlayer p = fake(back, 10);
        UUID group = Groups.keyOf(p);
        TrialCell.Run run = new TrialCell.Run(group, cell, mods(0, 1, 1, 0, new int[] {0}, 0), 41L, 0);
        try {
            cell.arm(back, run);
            long now = 3000;
            stand(p, 17.5, 76.5);
            cell.tick(back, run, List.of(p), now);
            check(!(Boolean) run.data.getOrDefault("started", false), "E10: started before the player left the entrance");
            stand(p, 24.5, 80.5);
            cell.tick(back, run, List.of(p), now + 5);
            check((Boolean) run.data.getOrDefault("started", false), "E10: did not start when the player moved in");
            long t = now + 5 + 70;
            cell.tick(back, run, List.of(p), t);
            check(run.getInt("wave") == 1, "E10: the first wave did not start");
            int expected = E10Furnace.bearers(run, 1);
            List<AshThrall> bearers = back.getEntitiesOfClass(AshThrall.class, EmberDeck.B.aabb(), e -> e.getTags().contains(E10Furnace.BEARER_TAG));
            check(bearers.size() == expected, "E10: expected " + expected + " bearers, found " + bearers.size());
            for (AshThrall th : bearers) {
                check(th instanceof jp.revati.skyabyss.titan.group.GroupOwned o && group.equals(o.groupId()), "E10: a bearer does not belong to the group");
            }
            // 熱が上がる → 噴く
            double heat0 = (Double) run.data.getOrDefault("heat", 0.0);
            for (int i = 1; i <= 8; i++) {
                cell.tick(back, run, List.of(p), t + i * 5L);
            }
            double heat1 = (Double) run.data.getOrDefault("heat", 0.0);
            check(heat1 > heat0 + 4, "E10: the hall heat did not rise with bearers alive (" + heat0 + " -> " + heat1 + ")");
            int guard = 0;
            long tt = t + 40;
            while (run.getInt("bursts") == 0 && guard++ < 400) {
                tt += 5;
                cell.tick(back, run, List.of(p), tt);
            }
            check(run.getInt("bursts") >= 1, "E10: the furnace never burst");
            check(EmberHeat.get(p) >= 25f, "E10: a burst did not heat the player (" + EmberHeat.get(p) + ")");
            check(!run.goalOk || EmberHeat.get(p) < EmberHeat.SCORCHING, "E10: the goal state is inconsistent");
            // 倒すと止まる
            for (AshThrall th : bearers) {
                th.discard();
            }
            double before = (Double) run.data.getOrDefault("heat", 0.0);
            for (int i = 1; i <= 6; i++) {
                cell.tick(back, run, List.of(p), tt + i * 5L);
            }
            check((Double) run.data.getOrDefault("heat", 0.0) < before, "E10: the hall heat did not fall after the bearers fell");
            tt += 30;
            // 次の波
            tt += 100;
            cell.tick(back, run, List.of(p), tt);
            check(run.getInt("wave") == 2, "E10: the second wave did not start (wave " + run.getInt("wave") + ")");
            for (Entity e : back.getEntitiesOfClass(Entity.class, EmberDeck.B.aabb(), x -> x.getTags().contains(E10Furnace.BEARER_TAG) || x instanceof EmberMoth)) {
                e.discard();
            }
            cell.tick(back, run, List.of(p), tt + 5);
            check(run.cleared, "E10: the last wave's end did not clear");
            check(EmberHeat.get(p) < 1f, "E10: the clear did not cool the player");
            // 噴気 (障害): 光ってから噴く
            Set<BlockPos> ov = new HashSet<>();
            cell.overlay(back, run, (pos, st) -> ov.add(pos));
            check(!ov.isEmpty(), "E10: no overlay after the clear");
        } finally {
            cell.disarm(back, run);
            GroupGates.reset(back, group);
            EmberHeat.reset(p);
        }
        check(back.getEntitiesOfClass(AshThrall.class, EmberDeck.B.aabb(), e -> e.getTags().contains(E10Furnace.BEARER_TAG)).isEmpty(), "E10: bearers stayed after disarm");
        // 障害の噴気で焼かれる
        TrialCell.Run v = new TrialCell.Run(group, cell, mods(0, 1, 1, 0, new int[] {0}, -1), 42L, 0);
        try {
            cell.arm(back, v);
            long now = 9000;
            stand(p, 24.5, 80.5);
            cell.tick(back, v, List.of(p), now);
            cell.tick(back, v, List.of(p), now + 5);
            long base = v.getLong("ventBase");
            stand(p, E10Furnace.VENTS[0][0] + 0.5, E10Furnace.VENTS[0][1] + 0.5);
            cell.tick(back, v, List.of(p), base + 2);
            check(E10Furnace.ventPhase(v, 0, base + 2) == 1, "E10: a vent did not glow first");
            check(v.getInt("burns") == 0, "E10: the glow burned");
            cell.tick(back, v, List.of(p), base + E10Furnace.VENT_WARN + 4);
            check(v.getInt("burns") == 1, "E10: a burst vent did not burn (" + v.getInt("burns") + ")");
        } finally {
            cell.disarm(back, v);
            GroupGates.reset(back, group);
            EmberHeat.reset(p);
        }
    }

    // ---------------------------------------------------------------- 世界: 戸・石・休み場・範囲
    private static void world(ServerLevel back) {
        TrialRuntime.installGates(back);
        UUID a = new UUID(0xEA, 1);
        UUID b = new UUID(0xEB, 2);
        BackGroupData.Record ra = GroupProgress.of(back, a);
        BackGroupData.Record rb = GroupProgress.of(back, b);
        ra.putInt(TrialData.DONE, 1);
        rb.putInt(TrialData.DONE, 3);
        TrialDraw.Draw da = TrialData.current(EMBER, ra, a, TrialData.worldSeed(back));
        TrialDraw.Draw db = TrialData.current(EMBER, rb, b, TrialData.worldSeed(back));
        check(da.picks().size() == 3 && db.picks().size() == 3, "an ember group did not draw three");
        TrialRuntime.syncGates(back, a, EMBER, da);
        TrialRuntime.syncGates(back, b, EMBER, db);
        for (TrialCell c : TrialCells.of(EMBER)) {
            if (c.ports().entrance().isEmpty()) {
                continue;
            }
            boolean weekly = c.def().equals(TrialData.weekly(EMBER, TrialData.worldSeed(back), TrialData.week()));
            boolean wa = da.has(c.def().id()) || weekly;
            boolean wb = db.has(c.def().id()) || weekly;
            check(GroupGates.isOpen(back, a, c.ports().entrance()) == wa, c.def().id() + " gate for group A: expected " + wa);
            check(GroupGates.isOpen(back, b, c.ports().entrance()) == wb, c.def().id() + " gate for group B: expected " + wb);
        }
        // 戸のブロックが実際に置いてある (原型の洞の口は既定で開き、新作は閉じ)
        for (TrialCell c : TrialCells.of(EMBER)) {
            for (BlockPos p : c.ports().entrance()) {
                if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                    FAIL.add(c.def().id() + " entrance block missing at " + p);
                    break;
                }
            }
            for (BlockPos p : c.ports().exitGate()) {
                if (!back.getBlockState(p).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
                    FAIL.add(c.def().id() + " exit gate block missing at " + p);
                    break;
                }
            }
        }
        // 範囲: 石と休み場が、引いたセルの範囲に入る。鼓動の間は入らない
        for (UUID g : List.of(a, b)) {
            TrialDraw.Draw draw = TrialData.current(EMBER, GroupProgress.of(back, g), g, TrialData.worldSeed(back));
            for (String key : TrialRegions.keys(EMBER)) {
                List<AABB> regions = TrialRegions.of(back, EMBER, key, g);
                check(!regions.isEmpty(), "ember " + key + ": no region for the drawn cell");
                check(!TrialRegions.inside(regions, Vec3.atCenterOf(EmberBackLayout.rest("hub").floor().above())), "ember " + key + ": the hub is inside the region");
                Slot slot = switch (key) {
                    case "west" -> Slot.WALK;
                    case "east" -> Slot.WIT;
                    default -> Slot.FIGHT;
                };
                TrialDraw.Pick pick = draw.inSlot(slot);
                TrialCell cell = TrialCells.get(pick.cellId());
                if (cell != null && cell.runtime()) {
                    check(TrialRegions.inside(regions, Vec3.atCenterOf(cell.ports().stone())), pick.cellId() + ": its stone is outside the region of " + key);
                    for (TrialCell.Rest r : cell.ports().rests()) {
                        check(TrialRegions.inside(regions, Vec3.atCenterOf(r.spawn())), pick.cellId() + ": rest " + r.id() + " is outside the region of " + key);
                    }
                    for (String other : TrialRegions.keys(EMBER)) {
                        if (!other.equals(key)) {
                            check(!TrialRegions.inside(TrialRegions.of(back, EMBER, other, g), Vec3.atCenterOf(cell.ports().stone())),
                                    pick.cellId() + ": its stone is inside the region of " + other);
                        }
                    }
                }
            }
        }
        // 石: 引いていない試練の石は鳴らせない。引いた試練が済むと、熾り石の記録 (西・東・南) に数えられる
        FakePlayer p = fake(back, 99);
        UUID pg = Groups.keyOf(p);
        BackGroupData.Record rp = GroupProgress.of(back, pg);
        rp.putInt(TrialData.DONE, 1);
        TrialDraw.Draw dp = TrialData.current(EMBER, rp, pg, TrialData.worldSeed(back));
        TrialDraw.Pick newPick = null;
        for (TrialDraw.Pick pk : dp.picks()) {
            if (!pk.def().original()) {
                newPick = pk;
                break;
            }
        }
        if (newPick != null) {
            TrialCell cell = TrialCells.get(newPick.cellId());
            TrialCell.Run run = TrialRuntime.armed(back, pg, cell, dp, back.getGameTime());
            run.cleared = true;
            var state = jp.revati.skyabyss.titan.ember.back.EmberBackState.of(back, pg);
            String key = TrialRuntime.emberTrial(cell.def().slot());
            var trial = jp.revati.skyabyss.titan.ember.back.EmberBackState.Trial.byId(key);
            check(!state.rung(trial), "the new cell's stone was already rung");
            p.setPos(cell.ports().stone().getX() + 0.5, cell.ports().stone().getY(), cell.ports().stone().getZ() + 1.5);
            var result = TrialRuntime.useStone(back, p, cell.ports().stone());
            check(result != null, "the new cell's stone was not handled");
            check(state.rung(trial), "ringing the new cell's stone did not ring the ember stone " + key);
            check(jp.revati.skyabyss.titan.group.TrialSteles.rung(back.getServer(), pg, jp.revati.skyabyss.titan.group.TrialSteles.signal("ember", key)),
                    "the stone signal for " + key + " is not rung");
            check(jp.revati.skyabyss.trial.HubTablets.cleared(back, pg, EMBER, key), "the hub tablet does not see the cleared trial");
            // 二度目は何も起きない
            var again = TrialRuntime.useStone(back, p, cell.ports().stone());
            check(again != null, "the second click on the stone was not handled");
            TrialRuntime.stopGroup(back, pg);
        } else {
            FAIL.add("the cycle-2 draw had no new ember trial (seed-dependent but unexpected)");
        }
        // 後始末
        jp.revati.skyabyss.trial.TrialHub.clearAll();
        for (UUID g : List.of(a, b, pg)) {
            GroupGates.reset(back, g);
            BackGroupData.get(back).remove(g);
        }
        EmberHeat.reset(p);
        p.setPos(0, 0, 0);
    }
}
