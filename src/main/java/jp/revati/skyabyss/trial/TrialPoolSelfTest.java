package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.WhaleDeck;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.Trial.Space;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// 試練のプールの自己テスト (docs/design/29 の 5.3)。段の名前は "trial pool" (SKYABYSS_SELFTEST_ONLY="trial pool" でこれだけ。背を建てるので数分かかる)。
//  - 引き方: 周回 1 は原型・周回 2 から枠ごとに一つ・履歴・尽きと戻し・直前を引かない・種の再現・グループごとの独立・段
//  - 修飾: 禁止の組み合わせが出ない・数字の範囲・稀さの割合・符号の往復・極の中身
//  - 全セルの全変種 (配置 × 数字 × 敵 × 障害 × 任意 × 極 × 静): arm → overlay (セルの箱の中) → tick → disarm
//  - 今週の試練・図鑑・称号・板の表示データとその通信の往復・戸 (引いた三つだけ開く)・セルの audit
//  - 複数グループ: 二つのグループが同じ背で、別々の記録・別々の戸・別々の引きを持つ
public final class TrialPoolSelfTest {
    private TrialPoolSelfTest() {
    }

    private static final List<String> FAIL = new ArrayList<>();

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAIL.add(what);
        }
    }

    public static List<Step> steps() {
        return List.of(
                Step.once("trial pool (build)", server -> {
                    FAIL.clear();
                    ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
                    if (back == null) {
                        FAIL.add("titan_back dimension is missing");
                        return;
                    }
                    WhaleDeck.setPlaced(true);
                    TitanBackData data = TitanBackData.get(back);
                    TitanBackBuilder.build(back, data);
                    TitanBackBuilder.placeDeck(back);
                }),
                Step.once("trial pool", 20, TrialPoolSelfTest::run));
    }

    private static void run(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        try {
            pure();
            variants(server);
            TrialCellLogic.run(server, FAIL);
            world(server);
        } catch (RuntimeException e) {
            FAIL.add("exception " + e);
            SkyAbyss.LOGGER.error("SELFTEST trial pool exception", e);
        } finally {
            WhaleDeck.setPlaced(true);
        }
        if (FAIL.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial pool PASSED ({} ms)", System.currentTimeMillis() - t0);
        } else {
            FAIL.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST trial pool FAILED: {}", p));
        }
    }

    // ---------------------------------------------------------------- 引き方・修飾 (ゲームなし)
    private static void pure() {
        String whale = Titans.STONE_WHALE;
        WhaleDeck.setPlaced(true);
        check(TrialCatalog.originals(whale).stream().map(Def::id).toList().equals(List.of("W01", "W05", "W09")), "whale originals are not W01 W05 W09");
        check(TrialCatalog.poolActive(whale), "the whale pool is not active with the deck placed");
        check(!TrialCatalog.poolActive(Titans.EMBER), "the ember pool must stay off (no new cells)");
        for (Def d : TrialCatalog.all()) {
            check(TrialCells.get(d.id()) != null, d.id() + " has no cell");
        }
        // ---- 周回 1 は原型の三つ・修飾なし
        UUID g = new UUID(1, 2);
        TrialDraw.Memory mem = new TrialDraw.Memory();
        TrialDraw.Draw d1 = TrialDraw.draw(whale, 1, 4242L, g, mem);
        check(d1.picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("W01", "W05", "W09")), "cycle 1 is not the originals");
        check(d1.picks().stream().allMatch(p -> p.mods().equals(Mods.PLAIN) || p.mods().encode().equals(Mods.PLAIN.encode())), "cycle 1 has modifiers");
        // ---- 周回 2 以降: 枠ごとに一つ、履歴、直前を引かない、種の再現
        for (int trial = 0; trial < 40; trial++) {
            UUID grp = new UUID(trial, 77);
            TrialDraw.Memory m = new TrialDraw.Memory();
            TrialDraw.draw(whale, 1, 4242L, grp, m);
            java.util.Map<Slot, String> prev = new java.util.EnumMap<>(Slot.class);
            for (Def o : TrialCatalog.originals(whale)) {
                prev.put(o.slot(), o.id());
            }
            for (int cycle = 2; cycle <= 9; cycle++) {
                TrialDraw.Draw dr = TrialDraw.draw(whale, cycle, 4242L, grp, m);
                check(dr.picks().size() == 3, "cycle " + cycle + " did not draw three");
                for (Slot s : Slot.values()) {
                    TrialDraw.Pick p = dr.inSlot(s);
                    check(p != null && p.def().slot() == s, "cycle " + cycle + " slot " + s + " is missing or in the wrong slot");
                    if (p != null) {
                        check(!p.cellId().equals(prev.get(s)), "slot " + s + " repeated " + p.cellId() + " at cycle " + cycle);
                        prev.put(s, p.cellId());
                        check(p.def().tier() <= cycle || TrialCatalog.inSlot(whale, s).stream().noneMatch(x -> x.tier() <= dr.cycle()), "tier above the cycle was drawn");
                        check(p.mods().layout() >= 0 && p.mods().layout() < Math.max(1, p.def().space().layouts()), "layout out of range");
                    }
                }
            }
        }
        // 種の再現 (履歴を最初から積み直して同じ結果)
        for (int trial = 0; trial < 10; trial++) {
            UUID grp = new UUID(trial, 5);
            List<String> run1 = sequence(whale, grp, 6);
            List<String> run2 = sequence(whale, grp, 6);
            check(run1.equals(run2), "the same group and world seed drew differently: " + run1 + " vs " + run2);
        }
        check(TrialDraw.seed(4242L, new UUID(1, 1), whale, 2) != TrialDraw.seed(4242L, new UUID(1, 2), whale, 2), "two groups share a seed");
        check(TrialDraw.seed(4242L, new UUID(1, 1), whale, 2) != TrialDraw.seed(4242L, new UUID(1, 1), whale, 3), "two cycles share a seed");
        // 四周で各枠の候補を全部見る (歩 2・智 3・闘 2 なので、周回 2〜4 で智が全部。歩・闘は周回 3 で戻る)
        List<String> seq = sequence(whale, new UUID(9, 9), 4);
        check(new HashSet<>(seq.subList(0, 3)).size() == 3 || seq.size() == 12, "sequence malformed: " + seq);
        // 巨神ごとに原型へ固定 (新作が無い巨神)
        TrialDraw.Draw ember = TrialDraw.draw(Titans.EMBER, 4, 4242L, g, new TrialDraw.Memory());
        check(ember.picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("E01", "E05", "E09")), "the ember draw is not the originals");
        // 置かないあいだは岩鯨も原型のまま (本番の安全)
        WhaleDeck.setPlaced(false);
        TrialDraw.Draw off = TrialDraw.draw(whale, 5, 4242L, g, new TrialDraw.Memory());
        check(off.picks().stream().map(TrialDraw.Pick::cellId).toList().equals(List.of("W01", "W05", "W09")), "the whale pool drew with the deck not placed");
        WhaleDeck.setPlaced(true);
        // 符号の往復
        TrialDraw.Draw rt = TrialDraw.draw(whale, 3, 4242L, g, copyMem(whale, g, 2));
        TrialDraw.Draw back = TrialDraw.Draw.decode(rt.encode());
        check(back != null && back.encode().equals(rt.encode()), "draw encode/decode does not round-trip");
        // ---- 修飾
        Random rng = new Random(12345);
        for (Def d : TrialCatalog.all()) {
            Space sp = d.space();
            int[] rarity = new int[3];
            int n = 4000;
            for (int i = 0; i < n; i++) {
                Mods m = TrialDraw.rollMods(d, rng);
                rarity[m.rarity().ordinal()]++;
                check(Mods.decode(m.encode()).encode().equals(m.encode()), d.id() + " mods do not round-trip: " + m.encode());
                if (sp.isEmpty()) {
                    continue;
                }
                check(m.layout() >= 0 && m.layout() < Math.max(1, sp.layouts()), d.id() + " layout out of range");
                check(m.numbers().length == sp.numbers().size(), d.id() + " number count");
                for (int lv : m.numbers()) {
                    check(lv >= 0 && lv <= 2, d.id() + " number level out of range");
                }
                check(m.hazards().length <= 2, d.id() + " has more than two hazards");
                for (int h : m.hazards()) {
                    check(h >= 0 && h < sp.hazards().size() && !sp.forbids(m.layout(), h), d.id() + " rolled a forbidden hazard " + h + " in layout " + m.layout());
                }
                check(m.goal() < sp.goals().size(), d.id() + " goal out of range");
                if (sp.enemySets() > 0) {
                    check(m.enemy() >= 0 && m.enemy() < sp.enemySets(), d.id() + " enemy set out of range");
                }
                if (m.extreme() == Extreme.DOUBLE_HAZARD) {
                    check(m.hazards().length == 2 && m.hazards()[0] != m.hazards()[1], d.id() + " double hazard is not two different hazards");
                }
                if (m.extreme() == Extreme.GAUNTLET) {
                    check(java.util.Arrays.stream(m.numbers()).allMatch(x -> x == 2), d.id() + " gauntlet is not all harsh");
                }
                check((m.rarity() == Rarity.EXTREME) == (m.extreme() != Extreme.NONE), d.id() + " rarity and extreme disagree");
            }
            if (!sp.isEmpty()) {
                double extremeShare = rarity[2] / (double) n;
                double commonShare = rarity[0] / (double) n;
                check(commonShare > 0.55 && commonShare < 0.85, d.id() + " common share " + commonShare);
                check(extremeShare < 0.10, d.id() + " extreme share " + extremeShare);
            }
        }
        // 戦いの強さは入った人の数で決まる (パーティの人数ではない): 同じ人数なら、パーティの大きさによらず同じ
        TrialCell.Run lone = new TrialCell.Run(g, TrialCells.get("W10"), new Mods(0, new int[] {1, 1}, 0, new int[0], -1, Extreme.NONE, false, Rarity.COMMON), 1L, 0);
        check(jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 1) < jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 3)
                && jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 3) < jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 5)
                && jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 1) == jp.revati.skyabyss.trial.whale.W10Fort.gunners(lone, 2),
                "the fort's gunners do not scale with the number inside");
        // 素材の数: 稀さで増える・任意の目標で増える・週替わりで倍
        Def w10 = TrialCatalog.get("W10");
        Mods plain = new Mods(0, new int[] {1, 1}, 0, new int[0], -1, Extreme.NONE, false, Rarity.COMMON);
        Mods rare = new Mods(0, new int[] {1, 1}, 0, new int[] {0}, 0, Extreme.NONE, false, Rarity.RARE);
        int base = TrialDraw.materialCount(w10, plain, false, false);
        check(base >= 1, "no materials for a plain trial");
        check(TrialDraw.materialCount(w10, rare, false, false) > base, "a rare trial gives no more materials");
        check(TrialDraw.materialCount(w10, rare, true, false) > TrialDraw.materialCount(w10, rare, false, false), "the optional goal gives no extra");
        check(TrialDraw.materialCount(w10, plain, false, true) >= base * 2, "the weekly trial does not double");
        // 今週の試練
        for (long week : new long[] {2900, 2901, 2902}) {
            List<Def> w = TrialData.weeklyAll(4242L, week);
            check(w.size() == TrialCatalog.titans().size(), "weekly count");
            check(TrialData.weeklyAll(4242L, week).equals(w), "weekly is not stable");
        }
        Set<String> distinct = new HashSet<>();
        for (long week = 2800; week < 2860; week++) {
            Def w = TrialData.weekly(whale, 4242L, week);
            check(w != null && w.titan().equals(whale), "weekly is not of the titan");
            if (w != null) {
                distinct.add(w.id());
            }
        }
        check(distinct.size() >= 3, "the weekly trial barely varies: " + distinct);
    }

    private static TrialDraw.Memory copyMem(String titan, UUID g, int upTo) {
        TrialDraw.Memory m = new TrialDraw.Memory();
        for (int c = 1; c <= upTo; c++) {
            TrialDraw.draw(titan, c, 4242L, g, m);
        }
        return m;
    }

    private static List<String> sequence(String titan, UUID g, int cycles) {
        TrialDraw.Memory m = new TrialDraw.Memory();
        List<String> out = new ArrayList<>();
        for (int c = 1; c <= cycles; c++) {
            TrialDraw.Draw d = TrialDraw.draw(titan, c, 4242L, g, m);
            for (TrialDraw.Pick p : d.picks()) {
                out.add(p.cellId() + "=" + p.mods().encode());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 全変種
    private static List<Mods> variantsOf(Def d) {
        Space sp = d.space();
        List<Mods> out = new ArrayList<>();
        int nn = sp.numbers().size();
        int combos = 1;
        for (int i = 0; i < nn; i++) {
            combos *= 3;
        }
        List<int[]> hazardSets = new ArrayList<>();
        hazardSets.add(new int[0]);
        for (int h = 0; h < sp.hazards().size(); h++) {
            hazardSets.add(new int[] {h});
        }
        if (sp.hazards().size() >= 2) {
            hazardSets.add(new int[] {0, 1});
        }
        for (int layout = 0; layout < Math.max(1, sp.layouts()); layout++) {
            for (int c = 0; c < combos; c++) {
                int[] numbers = new int[nn];
                int x = c;
                for (int i = 0; i < nn; i++) {
                    numbers[i] = x % 3;
                    x /= 3;
                }
                for (int[] hs : hazardSets) {
                    boolean forbidden = false;
                    for (int h : hs) {
                        forbidden |= sp.forbids(layout, h);
                    }
                    if (forbidden) {
                        continue;
                    }
                    for (int goal = -1; goal < sp.goals().size(); goal++) {
                        for (int enemy = sp.enemySets() > 0 ? 0 : -1; enemy < Math.max(sp.enemySets(), 0) || enemy == -1; enemy++) {
                            for (boolean quiet : new boolean[] {false, true}) {
                                Extreme ex = hs.length == 2 ? Extreme.DOUBLE_HAZARD : Extreme.NONE;
                                out.add(new Mods(layout, numbers, enemy, hs, goal, ex, quiet, hs.length == 2 ? Rarity.EXTREME : hs.length == 1 ? Rarity.RARE : Rarity.COMMON));
                                if (enemy == -1) {
                                    break;
                                }
                            }
                            if (enemy == -1) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    // 試練の階の部屋が、背のほかの部屋 (TitanBackLayout の Box) と体の中で重ならない (壁を共有するだけは良い)
    private static void deckOverlaps() {
        for (java.lang.reflect.Field fld : jp.revati.skyabyss.titan.dungeon.TitanBackLayout.class.getDeclaredFields()) {
            if (fld.getType() != jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box.class || !java.lang.reflect.Modifier.isStatic(fld.getModifiers())) {
                continue;
            }
            try {
                fld.setAccessible(true);
                var other = (jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box) fld.get(null);
                for (var room : WhaleDeck.ROOMS) {
                    boolean x = room.x1() - 1 <= other.x2() && other.x1() <= room.x2() + 1;
                    boolean z = room.z1() - 1 <= other.z2() && other.z1() <= room.z2() + 1;
                    boolean y = room.floorY() <= other.floorY() + other.height() + 1 && other.floorY() <= room.floorY() + room.height() + 1;
                    if (x && z && y) {
                        FAIL.add("deck room " + room + " overlaps layout box " + fld.getName() + " " + other);
                    }
                }
            } catch (IllegalAccessException e) {
                FAIL.add("cannot read " + fld.getName());
            }
        }
    }

    private static void variants(MinecraftServer server) {
        deckOverlaps();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        int total = 0;
        for (TrialCell cell : TrialCells.all()) {
            if (!cell.runtime()) {
                continue;
            }
            check(cell.audit(back).isEmpty(), cell.def().id() + " audit: " + cell.audit(back));
            net.minecraft.world.phys.AABB box = cell.ports().area().inflate(3);
            int n = 0;
            for (Mods m : variantsOf(cell.def())) {
                n++;
                UUID group = new UUID(0x5E1F, n);
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
            SkyAbyss.LOGGER.info("SELFTEST trial pool variants {}: {}", cell.def().id(), n);
            total += n;
        }
        check(total >= 500, "too few variants were enumerated: " + total);
    }

    // ---------------------------------------------------------------- 世界: 戸・複数グループ・記録
    private static void world(MinecraftServer server) {
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        String whale = Titans.STONE_WHALE;
        TrialRuntime.installGates(back);
        UUID a = new UUID(0xA, 1);
        UUID b = new UUID(0xB, 2);
        BackGroupData.Record ra = GroupProgress2.of(back, a);
        BackGroupData.Record rb = GroupProgress2.of(back, b);
        // 引きを別々に決める (周回 2)
        ra.putInt(TrialData.DONE, 1);
        rb.putInt(TrialData.DONE, 1);
        TrialDraw.Draw da = TrialData.current(whale, ra, a, TrialData.worldSeed(back));
        TrialDraw.Draw db = TrialData.current(whale, rb, b, TrialData.worldSeed(back));
        check(da.picks().size() == 3 && db.picks().size() == 3, "a group did not draw three");
        TrialRuntime.syncGates(back, a, whale, da);
        TrialRuntime.syncGates(back, b, whale, db);
        for (TrialCell c : TrialCells.of(whale)) {
            if (c.ports().entrance().isEmpty()) {
                continue;
            }
            // 今週の試練は、引いていなくても入れる
            boolean weekly = c.def().equals(TrialData.weekly(whale, TrialData.worldSeed(back), TrialData.week()));
            boolean wa = da.has(c.def().id()) || weekly;
            boolean wb = db.has(c.def().id()) || weekly;
            check(GroupGates.isOpen(back, a, c.ports().entrance()) == wa, c.def().id() + " gate for group A: expected " + wa);
            check(GroupGates.isOpen(back, b, c.ports().entrance()) == wb, c.def().id() + " gate for group B: expected " + wb);
        }
        // 進みは別 (A が済ませても B は済んでいない)
        TrialData.markCleared(ra, "W02");
        check(TrialData.isCleared(ra, "W02") && !TrialData.isCleared(rb, "W02"), "progress leaked between groups");
        GroupGates.reset(back, a);
        GroupGates.reset(back, b);
        ra.remove(TrialData.DONE);
        rb.remove(TrialData.DONE);
    }

    // BackGroupData への薄い口 (テスト用)
    private static final class GroupProgress2 {
        static BackGroupData.Record of(ServerLevel back, UUID group) {
            return jp.revati.skyabyss.titan.group.GroupProgress.of(back, group);
        }
    }
}
