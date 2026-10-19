package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.trial.Trial.Def;
import jp.revati.skyabyss.trial.Trial.Extreme;
import jp.revati.skyabyss.trial.Trial.Mods;
import jp.revati.skyabyss.trial.Trial.Rarity;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.Trial.Space;

// 引き方と修飾の抽選 (docs/design/29 の 2.2・2.5)。純粋な計算で、ゲームなしで自己テストできる。
//  - 周回 1 は原型の三つで固定 (修飾なし)。原型は「引いた」扱いで履歴に入る
//  - 周回 2 から枠ごとに、その枠でまだ引いていない試練から一つ。尽きたらその枠だけ、直前を除いて元へ戻す
//  - 段 (tier) が周回以下のものだけ (なければ段の制限を外す)。段が周回と同じ試練は重み 1.5
//  - 種 = 世界の種・グループ・巨神・周回。装備・職・刻印・レベルは入れない
//  - 修飾: 配置の型は必ず一つ。数字・障害・任意の目標・極は、試練ごとの稀さ (常 70 / 稀 25 / 極 5) で出る
public final class TrialDraw {
    private TrialDraw() {
    }

    public record Pick(String cellId, Mods mods) {
        public Def def() {
            return TrialCatalog.get(cellId);
        }
    }

    public record Draw(int cycle, List<Pick> picks) {
        public String encode() {
            StringBuilder sb = new StringBuilder().append(cycle);
            for (Pick p : picks) {
                sb.append('|').append(p.cellId()).append('=').append(p.mods().encode());
            }
            return sb.toString();
        }

        public static Draw decode(String s) {
            try {
                String[] parts = s.split("\\|");
                int cycle = Integer.parseInt(parts[0]);
                List<Pick> picks = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    String id = parts[i].substring(0, eq);
                    if (TrialCatalog.get(id) == null) {
                        return null;
                    }
                    picks.add(new Pick(id, Mods.decode(parts[i].substring(eq + 1))));
                }
                return picks.isEmpty() ? null : new Draw(cycle, List.copyOf(picks));
            } catch (RuntimeException e) {
                return null;
            }
        }

        public boolean has(String cellId) {
            for (Pick p : picks) {
                if (p.cellId().equals(cellId)) {
                    return true;
                }
            }
            return false;
        }

        public Pick pick(String cellId) {
            for (Pick p : picks) {
                if (p.cellId().equals(cellId)) {
                    return p;
                }
            }
            return null;
        }

        public Pick inSlot(Slot slot) {
            for (Pick p : picks) {
                if (p.def().slot() == slot) {
                    return p;
                }
            }
            return null;
        }
    }

    // 枠ごとの「引いたことがある」履歴と、直前に引いたもの
    public static final class Memory {
        public final Map<Slot, Set<String>> seen = new EnumMap<>(Slot.class);
        public final Map<Slot, String> last = new EnumMap<>(Slot.class);

        public Set<String> seen(Slot s) {
            return seen.computeIfAbsent(s, k -> new LinkedHashSet<>());
        }
    }

    public static long seed(long worldSeed, UUID group, String titan, int cycle) {
        long h = mix(worldSeed ^ 0x5DEECE66DL);
        h = mix(h ^ group.getMostSignificantBits());
        h = mix(h ^ group.getLeastSignificantBits());
        h = mix(h ^ titan.hashCode() * 0x9E3779B97F4A7C15L);
        return mix(h ^ cycle * 0xC2B2AE3D27D4EB4FL);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // 引く。mem は書き換える (呼ぶ側が保存する)。cycle は 1 から
    public static Draw draw(String titan, int cycle, long worldSeed, UUID group, Memory mem) {
        long seed = seed(worldSeed, group, titan, cycle);
        List<Def> chosen = new ArrayList<>();
        if (cycle <= 1 || !TrialCatalog.poolActive(titan)) {
            chosen.addAll(TrialCatalog.originals(titan));
            for (Def d : chosen) {
                mem.seen(d.slot()).add(d.id());
                mem.last.put(d.slot(), d.id());
            }
        } else {
            for (Slot slot : Slot.values()) {
                Def pick = drawSlot(titan, slot, cycle, seed, mem);
                if (pick != null) {
                    chosen.add(pick);
                }
            }
        }
        List<Pick> picks = new ArrayList<>();
        for (Def d : chosen) {
            Mods mods = cycle <= 1 ? Mods.PLAIN : rollMods(d, new Random(mix(seed ^ d.id().hashCode())));
            picks.add(new Pick(d.id(), mods));
        }
        if (cycle >= 2 && !picks.isEmpty()) {
            // 静 (敵の少ない試練) は三つのうち一つ。敵のいる試練に当たったときだけ効く
            int q = (int) Math.floorMod(mix(seed ^ 0x51ED), picks.size());
            Pick p = picks.get(q);
            Def d = p.def();
            if (d.kind() == Trial.Kind.FIGHT || d.kind() == Trial.Kind.ESCORT) {
                picks.set(q, new Pick(p.cellId(), p.mods().withQuiet(true)));
            }
        }
        return new Draw(cycle, List.copyOf(picks));
    }

    static Def drawSlot(String titan, Slot slot, int cycle, long seed, Memory mem) {
        List<Def> all = TrialCatalog.inSlot(titan, slot);
        if (all.isEmpty()) {
            return null;
        }
        Set<String> seen = mem.seen(slot);
        List<Def> cand = new ArrayList<>();
        for (Def d : all) {
            if (!seen.contains(d.id())) {
                cand.add(d);
            }
        }
        if (cand.isEmpty()) {
            // 尽きた: その枠だけ、直前に引いたものを除いて元へ戻す
            String last = mem.last.get(slot);
            seen.clear();
            if (last != null) {
                seen.add(last);
            }
            for (Def d : all) {
                if (!seen.contains(d.id())) {
                    cand.add(d);
                }
            }
            if (cand.isEmpty()) {
                cand.addAll(all);
            }
        }
        List<Def> byTier = new ArrayList<>();
        for (Def d : cand) {
            if (d.tier() <= cycle) {
                byTier.add(d);
            }
        }
        if (!byTier.isEmpty()) {
            cand = byTier;
        }
        Random rng = new Random(mix(seed + slot.ordinal() * 7919L));
        double total = 0;
        double[] w = new double[cand.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = cand.get(i).tier() == cycle ? 1.5 : 1.0;
            total += w[i];
        }
        double r = rng.nextDouble() * total;
        Def pick = cand.get(cand.size() - 1);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) {
                pick = cand.get(i);
                break;
            }
        }
        seen.add(pick.id());
        mem.last.put(slot, pick.id());
        return pick;
    }

    // 修飾を引く。配置は必ず。ほかは試練の稀さ (常 70 / 稀 25 / 極 5) で
    public static Mods rollMods(Def def, Random rng) {
        Space sp = def.space();
        if (sp.isEmpty()) {
            return Mods.PLAIN;
        }
        int layout = sp.layouts() > 1 ? rng.nextInt(sp.layouts()) : 0;
        int[] numbers = new int[sp.numbers().size()];
        java.util.Arrays.fill(numbers, 1);
        int enemy = sp.enemySets() > 0 ? rng.nextInt(sp.enemySets()) : -1;
        List<Integer> hazards = new ArrayList<>();
        int goal = -1;
        Extreme extreme = Extreme.NONE;
        int roll = rng.nextInt(100);
        Rarity rarity = roll < 70 ? Rarity.COMMON : roll < 95 ? Rarity.RARE : Rarity.EXTREME;
        if (rarity == Rarity.EXTREME) {
            List<Extreme> ex = sp.extremes();
            if (ex.isEmpty()) {
                rarity = Rarity.RARE;
            } else {
                extreme = ex.get(rng.nextInt(ex.size()));
                if (extreme == Extreme.DOUBLE_HAZARD) {
                    List<Integer> ok = allowedHazards(sp, layout);
                    if (ok.size() >= 2) {
                        java.util.Collections.shuffle(ok, rng);
                        hazards.add(ok.get(0));
                        hazards.add(ok.get(1));
                    } else {
                        extreme = Extreme.GAUNTLET;
                    }
                }
                if (extreme == Extreme.GAUNTLET) {
                    if (numbers.length == 0) {
                        extreme = Extreme.NONE;
                        rarity = Rarity.RARE;
                    } else {
                        java.util.Arrays.fill(numbers, 2);
                    }
                }
                if (!sp.goals().isEmpty()) {
                    goal = rng.nextInt(sp.goals().size());
                }
            }
        }
        if (rarity == Rarity.RARE) {
            // 標準からのずれを 1〜2 つ (段が上がると 2 つが出やすい)
            List<String> kinds = new ArrayList<>();
            if (numbers.length > 0) {
                kinds.add("number");
            }
            if (!allowedHazards(sp, layout).isEmpty()) {
                kinds.add("hazard");
            }
            if (!sp.goals().isEmpty()) {
                kinds.add("goal");
            }
            java.util.Collections.shuffle(kinds, rng);
            int count = Math.min(kinds.size(), def.tier() >= 2 && rng.nextInt(100) < 40 ? 2 : 1);
            for (int i = 0; i < count; i++) {
                switch (kinds.get(i)) {
                    case "number" -> numbers[rng.nextInt(numbers.length)] = rng.nextBoolean() ? 0 : 2;
                    case "hazard" -> {
                        List<Integer> ok = allowedHazards(sp, layout);
                        hazards.add(ok.get(rng.nextInt(ok.size())));
                    }
                    default -> goal = rng.nextInt(sp.goals().size());
                }
            }
            if (count == 0) {
                rarity = Rarity.COMMON;
            }
        }
        if (rarity == Rarity.COMMON) {
            java.util.Arrays.fill(numbers, 1);
            hazards.clear();
            goal = -1;
        }
        int[] hz = hazards.stream().mapToInt(Integer::intValue).toArray();
        return new Mods(layout, numbers, enemy, hz, goal, extreme, false, rarity);
    }

    private static List<Integer> allowedHazards(Space sp, int layout) {
        List<Integer> ok = new ArrayList<>();
        for (int h = 0; h < sp.hazards().size(); h++) {
            if (!sp.forbids(layout, h)) {
                ok.add(h);
            }
        }
        return ok;
    }

    // 報いの素材の個数 (docs/design/29 の 2.3・2.5): 基本 2 × 段の倍率 + 稀 +1・極 +2 + 任意の目標 +1
    public static int materialCount(Def def, Mods mods, boolean goalDone, boolean weekly) {
        double mult = def.tier() >= 3 ? 1.5 : def.tier() == 2 ? 1.2 : 1.0;
        int n = (int) Math.round(2 * mult);
        n += switch (mods.rarity()) {
            case COMMON -> 0;
            case RARE -> 1;
            case EXTREME -> 2;
        };
        if (goalDone) {
            n += 1;
        }
        return weekly ? n * 2 : n;
    }
}
