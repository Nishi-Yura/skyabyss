package jp.revati.skyabyss.rpg.skill;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

// プレイヤーごとの技の記録 (Data Attachment skyabyss:skills)。成長の記録 (GrowthData) とは分けてある。
// 死んでも (copyOnDeath)、ディメンションを移っても、ログアウトしても残る。月のループでも消さない。
// 書き換えは SkillManager を通す (同期と修飾子の付け直しをそこでまとめて行う)
public final class SkillData implements INBTSerializable<CompoundTag> {
    // 記録の版。1 = 剣士・弓使いの木の 10 の技だけ、2 = 5 つの木・刻印・型の保存 (docs/design/27)、
    // 4 = 変異・紋の盤・橋・淵・前世の技 (docs/design/31 の A08・A09・A14・B08・B09。新しい欄は空で始まる)、
    // 3 = 全部の木を最初から開く・キーストーン・構え・連鎖・熟練・型 4 つ・武器ごとの枠 (docs/design/31 の A 案)
    public static final int VERSION = 4;

    // 型の保存 1 つぶん: 枠の置き方と、技ごとの刻印の選び
    public static final class Loadout {
        public final String[] slots = new String[SkillRules.SLOTS];
        public final Map<String, Integer> runes = new LinkedHashMap<>();
        // 木 v2: 取っていた節 (docs/design/48。読み込むと木がこの形に戻る)
        public final LinkedHashSet<String> nodes = new LinkedHashSet<>();
        public boolean saved;

        public Loadout() {
            Arrays.fill(slots, "");
        }

        public void copyFrom(Loadout o) {
            System.arraycopy(o.slots, 0, slots, 0, slots.length);
            runes.clear();
            runes.putAll(o.runes);
            nodes.clear();
            nodes.addAll(o.nodes);
            saved = o.saved;
        }

        public boolean sameAs(Loadout o) {
            return saved == o.saved && Arrays.equals(slots, o.slots) && runes.equals(o.runes) && nodes.equals(o.nodes);
        }
    }

    // 覚えた技と段 (id → 1..maxRank)
    public final Map<String, Integer> ranks = new LinkedHashMap<>();
    // 刻印の選び (技の id → 刻印の番号 1..)。選んだ技の分だけ点を使う
    public final Map<String, Integer> runes = new LinkedHashMap<>();
    // 技の置き場 (最大 5 枠。いま使えるのは slotCount() まで)。空は ""
    public final String[] slots = new String[SkillRules.SLOTS];
    // 型の保存 (4 つ)
    public final Loadout[] loadouts = new Loadout[SkillRules.LOADOUTS];
    // キーストーン (鍵石。最大 2)
    public final Set<String> keystones = new LinkedHashSet<>();
    // 熟練の経験 (技の id → 点。転生しても残る)
    public final Map<String, Integer> mastery = new LinkedHashMap<>();
    // 武器の持ち替えで入れ替わる枠の組 (0 = 近接など、1 = 弓)。いま使っている枠は slots、使っていないほうがここ
    public final String[][] bars = new String[2][SkillRules.SLOTS];
    // 職ごとの枠 (docs/design/43)。slots はいま持っている職 (curJob) の枠。持ち替えると useJob が入れ替える
    public final Map<String, String[]> jobBars = new java.util.HashMap<>();
    public String curJob = "";
    // 技の木 v2 (docs/design/48): 職ごとに取った節の id。技の段・刻印・古い鍵石・基礎はここから導く (applyNodes)
    public final Map<String, LinkedHashSet<String>> nodes = new java.util.HashMap<>();
    public final boolean[] barUsed = new boolean[2];
    public int bar;
    // 技の変異 (A08。技の id → 変異の id)。一度選んだら外せない。転生しても残る
    public final Map<String, String> mutations = new LinkedHashMap<>();
    // 紋 (A14)。持っている紋 (「id/段」の文字。25 まで) と、盤の穴 (最大 6。「id/段@技の id」か空)。転生しても残る
    public final java.util.List<String> glyphBag = new java.util.ArrayList<>();
    public final String[] glyphSockets = new String[SkillRules.GLYPH_SOCKETS];
    // 紋をもらった試練 ("巨神|試練")。同じ試練で二度もらわない。転生で空に戻る
    public final Set<String> glyphClaims = new LinkedHashSet<>();
    // 転生の回数 (サーバーが毎回入れて同期する。保存しない。淵・前世の技の出し分けと、前世の点に使う)
    public int rebirth;
    // 淵の残響の期限 (サーバーだけ)
    public long echoUntil;
    // 記録の版 (読んだ記録が古ければ sanitize で移す)
    public int version = VERSION;
    // レベル以外から得た技の点 (クエストや記憶の断片の報酬に使う予定。今は 0)
    public int bonusPoints;
    // 深淵術のランク上げに使った点 (docs/design/36 の 5.2)。技の点と同じ数え方で、合計から引く
    public int artPoints;
    // これまでに振り直した回数 (費用が上がる)
    public int respecs;
    // これまでに刻印を付け替えた回数 (初回は無料)
    public int runeSwaps;
    // 今のマナ。負なら「まだ決めていない」(初めてのログインで満タンにする)
    public float mana = -1;
    // 技ごとの残りのクールダウン (tick)。保存する (ログアウトで消えない)
    public final Map<String, Integer> cooldowns = new LinkedHashMap<>();
    // 全体のクールダウン (どの技のあとも 0.5 秒)
    public int globalCooldown;
    // 封印を解いた木 (星術・深淵術。章の担当が付ける)
    public final Set<String> unlockedTrees = new LinkedHashSet<>();
    // 「技の点が増えた」を知らせ済みの点の数 (ログインし直しても同じ知らせを出さない)
    public int notifiedPoints;

    // ここから下は保存も同期もしない (サーバーだけ)
    // 返し刃が効く期限 (ゲーム時刻)
    public long riposteUntil;
    // 最後に戦った時刻 (傷を受けた・与えた・技を使った)。静かなときマナが早く満ちる
    public long lastCombat = Long.MIN_VALUE / 2;
    // 予告をよけた直後の期限と、無傷で完璧によけた直後の期限、最後に傷を受けた時刻
    public long dodgeUntil;
    public long perfectUntil;
    public long lastHurt = Long.MIN_VALUE / 2;
    // 連鎖: 直前に使った枠と時刻、連鎖が付いた技と期限
    public int lastCastSlot = -1;
    public long lastCastTick = Long.MIN_VALUE / 2;
    // 熟練の「当てた」を数えた時刻 (技ごと。広い技が一度に何体に当たっても、10 tick に 1 度だけ数える)
    public final Map<String, Long> masteryHitAt = new LinkedHashMap<>();
    // 最後に持ち主へ送ったマナ (送りすぎないため)
    public float sentMana = -999;
    // マナの回復の倍率 (毎秒、SkillEffects が計算する): 凪の心得・野営の手、巡りの唄の期限つきの上乗せ
    public double regenScale = 1;
    public long regenBoostUntil;
    public double regenBoost;
    // 炉の記憶の熾の数、焼き入れの期限と炎の傷の倍率、射返しの期限
    public int embers;
    public long temperUntil;
    public double temperMult;
    public long returnFireUntil;
    // 岩の返りの上乗せ (次の近接に掛かる。0 でなし)
    public double rockReturn;
    // 動かずにいる tick の数 (静かな弦)
    public int stillTicks;
    // 逃げ足の次に使える時刻
    public long fleetReadyAt;
    // 岩呼びの狙いの期限と、そのときの刻印 (重い声・岩の返りの判定)
    public long tauntUntil;
    public int tauntRune;
    // 静かな弦の「動いていない」の数え方 (前の tick の位置)
    public double stillX = Double.NaN;
    public double stillZ = Double.NaN;
    // 野営の手 (近くに焚き火・休み場・炉があるか) と、備えの報い (三灯がすべて青か)。毎秒・5 秒ごとに更新
    public boolean campNear;
    public boolean readyBlue;
    // 満腹度の減りを見る前の tick の消耗
    public float lastExhaustion;

    public SkillData() {
        Arrays.fill(slots, "");
        for (int i = 0; i < loadouts.length; i++) {
            loadouts[i] = new Loadout();
        }
        Arrays.fill(bars[0], "");
        Arrays.fill(bars[1], "");
        Arrays.fill(glyphSockets, "");
    }

    public int rank(Skill skill) {
        return ranks.getOrDefault(skill.id, 0);
    }

    // 熟練 (0〜5)
    public int masteryXp(Skill skill) {
        return mastery.getOrDefault(skill.id, 0);
    }

    public int masteryLevel(Skill skill) {
        return SkillRules.masteryLevel(masteryXp(skill));
    }

    // その木に使った点 (職の木は節の点の合計。淵・前世は技の段と刻印)
    public int treePoints(SkillTree tree) {
        Job owner = Job.ofTree(tree);
        if (owner != null && jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(owner) != null) {
            return nodeSpent(owner);
        }
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            Skill s = Skill.byId(e.getKey());
            if (s != null && s.tree == tree) {
                sum += e.getValue();
            }
        }
        for (String id : runes.keySet()) {
            Skill s = Skill.byId(id);
            if (s != null && s.tree == tree) {
                sum += 1;
            }
        }
        return sum;
    }

    // ---------------------------------------------------------------- 橋・淵・前世の技 (31 の A09・B08・B09)
    // 橋が開くか: 親の 2 本の木のそれぞれで、技を 3 つ以上覚えている (木の「段 3」)
    // 前世の点の持ち分: 転生 1 回につき 2 点 (6 回ぶんの 12 点まで)。通常の点とは別枠
    public static final int PAST_POINTS_PER_REBIRTH = 2;
    public static final int PAST_POINTS_MAX = 12;

    public int pastPool() {
        return Math.min(PAST_POINTS_MAX, Math.max(0, rebirth) * PAST_POINTS_PER_REBIRTH);
    }

    public int pastSpent() {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            Skill s = Skill.byId(e.getKey());
            if (s != null && s.tree == SkillTree.PAST) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    public int pastLeft() {
        return pastPool() - pastSpent();
    }

    // 転生の回数が足りない淵・前世の技を戻す (回数が下がった・開発コマンド)。前世の点が持ち分を超えたら前世の技を全部戻す。戻したら true
    public boolean enforceRebirth() {
        boolean changed = false;
        for (Skill s : Skill.values()) {
            if (s.rebirthAt() > rebirth && ranks.containsKey(s.id)) {
                ranks.remove(s.id);
                runes.remove(s.id);
                changed = true;
            }
        }
        if (pastSpent() > pastPool()) {
            ranks.keySet().removeIf(id -> Skill.byId(id) != null && Skill.byId(id).tree == SkillTree.PAST);
            changed = true;
        }
        if (changed) {
            // 淵の途中が消えて前提が崩れた技も戻す
            pruneBroken();
            for (int i = 0; i < slots.length; i++) {
                Skill sk = Skill.byId(slots[i]);
                if (sk != null && !knows(sk)) {
                    slots[i] = "";
                }
            }
        }
        return changed;
    }

    public boolean knows(Skill skill) {
        return rank(skill) > 0;
    }

    // 選んでいる刻印の番号 (なければ 0)
    public int rune(Skill skill) {
        return runes.getOrDefault(skill.id, 0);
    }

    // 使った点 (職ごとの点になったので「共有の分 + いちばん使っている職の分」。docs/design/43)。PAST は別枠
    public int spentPoints() {
        return sharedSpent() + maxJobSpent();
    }

    // 前の版の数え方 (全部の木の合計。記録の移し替えの検査用)
    public int spentPointsAllTrees() {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            Skill sk = Skill.byId(e.getKey());
            if (sk != null && sk.tree != SkillTree.PAST) {
                sum += e.getValue();
            }
        }
        sum += artPoints;
        for (String id : runes.keySet()) {
            if (Skill.byId(id) != null) {
                sum += 1;
            }
        }
        sum += keystones.size();
        return Math.max(0, sum);
    }

    public int totalPoints(int level) {
        return SkillRules.pointsForLevel(level) + bonusPoints;
    }

    // ---------------------------------------------------------------- 職ごとの点 (docs/design/43)
    // 職の木の点はその職だけのもの。深淵の術 (artPoints) と淵の木は職をまたいで使う (どの職の残りからも引く)
    public int jobSpent(SkillTree tree) {
        Job owner = Job.ofTree(tree);
        if (owner != null && jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(owner) != null) {
            return nodeSpent(owner);
        }
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            Skill sk = Skill.byId(e.getKey());
            if (sk != null && sk.tree == tree) {
                sum += e.getValue();
            }
        }
        for (String id : runes.keySet()) {
            Skill sk = Skill.byId(id);
            if (sk != null && sk.tree == tree) {
                sum += 1;
            }
        }
        return sum;
    }

    public int sharedSpent() {
        return artPoints + jobSpent(SkillTree.DEEP);
    }

    // いちばん点を使っている職の分
    public int maxJobSpent() {
        int max = 0;
        for (Job j : Job.values()) {
            max = Math.max(max, jobSpent(j.tree));
        }
        return max;
    }

    // その木の技を覚えるのに使える残り (職の木 = その職の残り、淵 = どの職でも足りる残り)
    public int unspentFor(SkillTree tree, int level) {
        Job job = Job.ofTree(tree);
        int own = job != null ? jobSpent(tree) : maxJobSpent();
        return totalPoints(level) - sharedSpent() - own;
    }

    // 持った武器の職へ枠を入れ替える。替わったら true。初めての職は、覚えている使う技を行の順に並べる
    public boolean useJob(Job job) {
        String key = job == null ? "" : job.id;
        if (key.equals(curJob)) {
            return false;
        }
        jobBars.put(curJob, slots.clone());
        String[] next = jobBars.get(key);
        Arrays.fill(slots, "");
        if (next != null) {
            System.arraycopy(next, 0, slots, 0, Math.min(next.length, slots.length));
        } else if (job != null) {
            int i = 0;
            for (Skill s : jobActives(job)) {
                if (knows(s) && i < slotCount()) {
                    slots[i++] = s.id;
                }
            }
        }
        curJob = key;
        return true;
    }

    // ---------------------------------------------------------------- 技の木 v2 (docs/design/48)
    public java.util.Set<String> nodesOf(Job job) {
        if (job == null) {
            return java.util.Set.of();
        }
        return nodes.computeIfAbsent(job.id, k -> new LinkedHashSet<>());
    }

    public boolean hasNode(Job job, String id) {
        return job != null && nodesOf(job).contains(id);
    }

    // その職の木で使った点
    public int nodeSpent(Job job) {
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
        if (tree == null) {
            return 0;
        }
        int sum = 0;
        for (String id : nodesOf(job)) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
            if (n != null) {
                sum += n.cost;
            }
        }
        return sum;
    }

    // その型 (枝) に使った点
    public int archPoints(Job job, String arch) {
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
        if (tree == null || arch == null) {
            return 0;
        }
        int sum = 0;
        for (String id : nodesOf(job)) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
            if (n != null && arch.equals(n.arch)) {
                sum += n.cost;
            }
        }
        return sum;
    }

    // 取っている使う技の数 (4 本まで)
    public int activeCount(Job job) {
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
        if (tree == null) {
            return 0;
        }
        int n = 0;
        for (String id : nodesOf(job)) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode node = tree.node(id);
            if (node != null && node.countsAsActive()) {
                n++;
            }
        }
        return n;
    }

    public enum TakeResult {
        OK, UNKNOWN, TAKEN, NO_POINTS, REQUIRES, EXCLUDED, ARCH_POINTS, ACTIVES_FULL, RANK_MAX, SEALED, NOT_MASTERED, NO_MATERIAL
    }

    // その節を今取れるか
    public TakeResult canTake(Job job, jp.revati.skyabyss.rpg.skill.tree.AbilityNode n, int level) {
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
        if (job == null || tree == null || n == null || tree.node(n.id) != n) {
            return TakeResult.UNKNOWN;
        }
        java.util.Set<String> mine = nodesOf(job);
        if (mine.contains(n.id)) {
            return TakeResult.TAKEN;
        }
        if (!n.requires.isEmpty()) {
            boolean any = false;
            for (String r : n.requires) {
                if (mine.contains(r)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return TakeResult.REQUIRES;
            }
        }
        for (String e : n.excludes) {
            if (mine.contains(e)) {
                return TakeResult.EXCLUDED;
            }
        }
        if (n.kind == jp.revati.skyabyss.rpg.skill.tree.AbilityNode.Kind.RUNE) {
            // 同じ技の刻印は一つだけ
            for (jp.revati.skyabyss.rpg.skill.tree.AbilityNode other : tree.runesOf(n.skill)) {
                if (other != n && mine.contains(other.id)) {
                    return TakeResult.EXCLUDED;
                }
            }
        }
        if (n.arch != null && n.archPoints > 0 && archPoints(job, n.arch) < n.archPoints) {
            return TakeResult.ARCH_POINTS;
        }
        if (n.countsAsActive() && activeCount(job) >= SkillRules.BASE_SLOTS) {
            return TakeResult.ACTIVES_FULL;
        }
        if (n.kind == jp.revati.skyabyss.rpg.skill.tree.AbilityNode.Kind.RANK && rank(n.skill) >= n.skill.maxRank) {
            return TakeResult.RANK_MAX;
        }
        // 変異は技を極めてから (素材はサーバーが取るときに見る)
        if (n.kind == jp.revati.skyabyss.rpg.skill.tree.AbilityNode.Kind.MUTATION && rank(n.skill) < n.skill.maxRank) {
            return TakeResult.NOT_MASTERED;
        }
        if (unspentFor(job.tree, level) < n.cost) {
            return TakeResult.NO_POINTS;
        }
        return TakeResult.OK;
    }

    // 節から技の段・刻印・古い鍵石を導く (節を変えたあと・読み込んだあとに呼ぶ)。職の木の技だけを書き換える (淵・前世・深淵の術は触らない)
    public void applyNodes() {
        for (Job job : Job.values()) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
            if (tree == null) {
                continue;
            }
            for (Skill s : Skill.inTree(job.tree)) {
                ranks.remove(s.id);
                runes.remove(s.id);
            }
            for (String id : nodesOf(job)) {
                jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
                if (n == null) {
                    continue;
                }
                switch (n.kind) {
                    case ACTIVE, PASSIVE, KEYSTONE, RANK -> ranks.merge(n.skill.id, 1, Integer::sum);
                    case RUNE -> runes.put(n.skill.id, n.rune);
                    default -> {
                    }
                }
            }
            for (Skill s : Skill.inTree(job.tree)) {
                Integer r = ranks.get(s.id);
                if (r != null && r > s.maxRank) {
                    ranks.put(s.id, s.maxRank);
                }
            }
        }
        // 鍵石 (型の要) は今の職の木の節から。木がなければ (検査で木を外したとき) 前のまま
        Job cur = Job.byId(curJob);
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(cur);
        if (tree != null) {
            keystones.clear();
            for (String id : nodesOf(cur)) {
                jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
                if (n != null && n.kind == jp.revati.skyabyss.rpg.skill.tree.AbilityNode.Kind.TRAIT) {
                    keystones.add(n.keystone.id);
                }
            }
        }
    }

    // 今の職の木から導いた基礎 (筋力・敏捷・精神・耐久の順)
    public int[] statsFromNodes(Job job) {
        int[] out = new int[jp.revati.skyabyss.rpg.GrowthRules.Stat.values().length];
        jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
        if (tree == null) {
            return out;
        }
        for (String id : nodesOf(job)) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
            if (n != null && n.kind == jp.revati.skyabyss.rpg.skill.tree.AbilityNode.Kind.STAT) {
                out[n.stat.ordinal()] += n.amount;
            }
        }
        return out;
    }

    // その職の木を空にする (点は戻る)
    public void clearJob(Job job) {
        if (job == null) {
            return;
        }
        nodesOf(job).clear();
        applyNodes();
    }

    // 節の検め: 知らない節・前提の崩れた節・排他の重なり・点の使いすぎを戻す
    void sanitizeNodes(int level) {
        for (Job job : Job.values()) {
            jp.revati.skyabyss.rpg.skill.tree.AbilityTree tree = jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(job);
            java.util.Set<String> mine = nodesOf(job);
            if (tree == null) {
                mine.clear();
                continue;
            }
            mine.removeIf(id -> tree.node(id) == null);
            boolean changed = true;
            while (changed) {
                changed = false;
                for (String id : new java.util.ArrayList<>(mine)) {
                    jp.revati.skyabyss.rpg.skill.tree.AbilityNode n = tree.node(id);
                    boolean ok = n.requires.isEmpty();
                    for (String r : n.requires) {
                        if (mine.contains(r)) {
                            ok = true;
                        }
                    }
                    for (String e : n.excludes) {
                        if (mine.contains(e) && mine.contains(id)) {
                            // 排他の重なりは、あとの方 (この節) を外す
                            ok = false;
                        }
                    }
                    if (!ok) {
                        mine.remove(id);
                        changed = true;
                    }
                }
            }
            if (nodeSpent(job) > totalPoints(level) + 0 && nodeSpent(job) > SkillRules.pointsForLevel(GrowthRules_MAX_LEVEL()) + bonusPoints) {
                mine.clear();
            }
        }
        applyNodes();
    }

    private static int GrowthRules_MAX_LEVEL() {
        return jp.revati.skyabyss.rpg.GrowthRules.MAX_LEVEL;
    }

    // その職で枠に置ける技か (その職の木か、職をまたぐ木 = 淵・前世・深淵の術)
    public static boolean usableBy(Job job, Skill skill) {
        Job own = Job.ofTree(skill.tree);
        return job == null || own == null || own == job;
    }

    // 職の枠: その職の木の使う技を、行の順 (同じ行は列の順) に。覚えていなければ空 (null)
    public Skill jobSlot(Job job, int i) {
        if (job == null || i < 0) {
            return null;
        }
        java.util.List<Skill> list = jobActives(job);
        if (i >= list.size()) {
            return null;
        }
        Skill s = list.get(i);
        return knows(s) ? s : null;
    }

    // 職の木で、枠に入る使う技の並び
    public static java.util.List<Skill> jobActives(Job job) {
        java.util.List<Skill> out = new java.util.ArrayList<>();
        for (Skill s : Skill.values()) {
            if (job != null && s.tree == job.tree && s.active) {
                out.add(s);
            }
        }
        out.sort(java.util.Comparator.comparingInt((Skill s) -> s.tier).thenComparingInt(s -> s.column));
        return out;
    }

    public int unspentPoints(int level) {
        return totalPoints(level) - spentPoints();
    }

    // 職 job の i 番目の型の箱
    public static int loadoutIndex(Job job, int i) {
        return job.ordinal() * SkillRules.LOADOUTS_PER_JOB + i;
    }

    // いま使える枠の数 (4。5 つ目の枠は天職とともに 43 で削った)
    public int slotCount() {
        return SkillRules.BASE_SLOTS;
    }

    public Skill slot(int i) {
        return i >= 0 && i < slotCount() ? Skill.byId(slots[i]) : null;
    }

    public int cooldown(Skill skill) {
        return cooldowns.getOrDefault(skill.id, 0);
    }

    public boolean treeOpen(SkillTree tree) {
        return !tree.sealed || unlockedTrees.contains(tree.id);
    }

    // 1 tick 進める (サーバーとクライアントの両方で数える)
    public boolean tickCooldowns() {
        boolean any = globalCooldown > 0 || !cooldowns.isEmpty();
        if (globalCooldown > 0) {
            globalCooldown--;
        }
        cooldowns.replaceAll((k, v) -> v - 1);
        cooldowns.values().removeIf(v -> v <= 0);
        return any;
    }

    // ---------------------------------------------------------------- 習得の条件
    public enum LearnResult {
        OK, UNKNOWN, SEALED, MAX_RANK, NO_POINTS, LEVEL, REQUIRES, CALLING, REBIRTH
    }

    public LearnResult canLearn(Skill skill, int level) {
        if (skill == null) {
            return LearnResult.UNKNOWN;
        }
        // 職の木の技は節で取る (docs/design/48。SkillManager.takeNode)
        if (Job.ofTree(skill.tree) != null && jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(Job.ofTree(skill.tree)) != null) {
            return LearnResult.SEALED;
        }
        if (!treeOpen(skill.tree)) {
            return LearnResult.SEALED;
        }
        if (skill.rebirthAt() > rebirth) {
            return LearnResult.REBIRTH;
        }
        if (rank(skill) >= rankLimit(skill)) {
            return rank(skill) >= skill.maxRank ? LearnResult.MAX_RANK : LearnResult.CALLING;
        }
        if (level < skill.minLevel) {
            return LearnResult.LEVEL;
        }
        if (!requirementsMet(skill)) {
            return LearnResult.REQUIRES;
        }
        if (skill.tree == SkillTree.PAST ? pastLeft() < 1 : unspentFor(skill.tree, level) < 1) {
            return LearnResult.NO_POINTS;
        }
        return LearnResult.OK;
    }

    public boolean requirementsMet(Skill skill) {
        // 職の木の技の前提は木の線 (節の requires) で見る。技どうしの前提は淵・前世だけ
        if (Job.ofTree(skill.tree) != null && jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(Job.ofTree(skill.tree)) != null) {
            return true;
        }
        var req = skill.requires();
        if (req.isEmpty()) {
            return true;
        }
        for (Skill r : req) {
            if (knows(r)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 段の上限と刻印
    public int rankLimit(Skill skill) {
        return skill.maxRank;
    }

    // 刻印が使える状態か (全部の木で使える。点は 1)
    public boolean runesOpen(Skill skill) {
        return true;
    }

    // その技の刻印を選べるか (技を極めている。刻印の 4 つ目は熟練 3 から)
    public boolean runeAvailable(Skill skill) {
        return skill.active && rank(skill) >= skill.maxRank && (!skill.runes().isEmpty() || Rune.legendaryOf(skill) != null
                || masteryLevel(skill) >= SkillRules.MASTERY_RUNE_LEVEL);
    }

    // その番号の刻印を今選べるか (熟練の刻印は熟練 3 から)
    public boolean runeUsable(Skill skill, int index) {
        return runeAvailable(skill) && Rune.valid(skill, index) && (index != Rune.MASTERY || masteryLevel(skill) >= SkillRules.MASTERY_RUNE_LEVEL);
    }

    // 画面の試着 (仮振り) 用の写し。技の段・刻印・キーストーン・天職・熟練・追加の点・開いた木だけ
    public SkillData copyOf() {
        SkillData c = new SkillData();
        nodes.forEach((k, v) -> c.nodes.put(k, new LinkedHashSet<>(v)));
        c.curJob = curJob;
        c.ranks.putAll(ranks);
        c.runes.putAll(runes);
        c.keystones.addAll(keystones);
        c.mastery.putAll(mastery);
        c.unlockedTrees.addAll(unlockedTrees);
        c.bonusPoints = bonusPoints;
        c.artPoints = artPoints;
        c.rebirth = rebirth;
        c.mana = mana;
        System.arraycopy(slots, 0, c.slots, 0, slots.length);
        return c;
    }

    // キーストーンを取れる状態か (そのタグの木に点が足りているか)
    public boolean keystoneReachable(SkillKeystone k) {
        return treePoints(k.tag.tree) >= SkillRules.KEYSTONE_TREE_POINTS;
    }

    // ---------------------------------------------------------------- 記録の検め
    // 壊れた値 (手で書き換えた NBT、技を消した版など) を遊べる範囲に戻す。level は成長の記録のレベル
    public void sanitize(int level) {
        ranks.entrySet().removeIf(e -> {
            Skill s = Skill.byId(e.getKey());
            return s == null || e.getValue() == null || e.getValue() <= 0;
        });
        ranks.replaceAll((k, v) -> Math.min(v, Skill.byId(k).maxRank));
        bonusPoints = Math.max(0, bonusPoints);
        respecs = Math.max(0, respecs);
        runeSwaps = Math.max(0, runeSwaps);
        // 木 v2: 節を検め、職の木の技の段・刻印・鍵石 (型の要) は節から導き直す
        sanitizeNodes(level);
        if (jp.revati.skyabyss.rpg.skill.tree.AbilityTrees.of(Job.byId(curJob)) == null) {
            // 木がないとき (職がまだ無い・検査で木を外したとき) は鍵石を持たない (43 で削った仕組み。型の要は木の節だけ)
            keystones.clear();
        }
        mastery.entrySet().removeIf(e -> Skill.byId(e.getKey()) == null || e.getValue() == null || e.getValue() <= 0);
        bar = bar == 1 ? 1 : 0;
        // 前の版の記録: 木が組み替わったので、前提が崩れた技 (と、その先) だけ戻す。ほかは残す
        if (version < VERSION) {
            pruneBroken();
            version = VERSION;
        }
        // 刻印: 技を極めている・刻印がある・選びが範囲内、のものだけ残す
        runes.entrySet().removeIf(e -> {
            Skill s = Skill.byId(e.getKey());
            return s == null || e.getValue() == null || !Rune.valid(s, e.getValue()) || rank(s) < s.maxRank;
        });
        // 点を使いすぎている (レベルを下げられた) か、前提が崩れていたら、すべて戻して振り直してもらう。
        // 要るレベル (minLevel) は覚えるときだけの条件で、あとから下がっても消さない
        boolean broken = sharedSpent() + maxJobSpent() > totalPoints(level);
        for (String id : ranks.keySet()) {
            Skill s = Skill.byId(id);
            if (!requirementsMet(s) || !treeOpen(s.tree)) {
                broken = true;
            }
        }
        if (broken) {
            ranks.clear();
            runes.clear();
            keystones.clear();
            nodes.clear();
            artPoints = 0;
        }
        Job cur = Job.byId(curJob);
        for (int i = 0; i < slots.length; i++) {
            Skill s = Skill.byId(slots[i]);
            if (s == null || !s.active || !knows(s) || i >= slotCount() || !usableBy(cur, s)) {
                slots[i] = "";
            }
            // 同じ技が二つの枠にあれば、後のほうを空ける
            for (int j = 0; j < i; j++) {
                if (!slots[i].isEmpty() && slots[i].equals(slots[j])) {
                    slots[i] = "";
                }
            }
        }
        for (String[] b : bars) {
            for (int i = 0; i < b.length; i++) {
                Skill s = Skill.byId(b[i]);
                if (s == null || !s.active || !knows(s) || i >= slotCount()) {
                    b[i] = "";
                }
                for (int j = 0; j < i; j++) {
                    if (!b[i].isEmpty() && b[i].equals(b[j])) {
                        b[i] = "";
                    }
                }
            }
        }
        for (Loadout l : loadouts) {
            for (int i = 0; i < l.slots.length; i++) {
                Skill s = Skill.byId(l.slots[i]);
                if (s == null || !s.active || i >= SkillRules.SLOTS) {
                    l.slots[i] = "";
                }
                for (int j = 0; j < i; j++) {
                    if (!l.slots[i].isEmpty() && l.slots[i].equals(l.slots[j])) {
                        l.slots[i] = "";
                    }
                }
            }
            l.runes.entrySet().removeIf(e -> {
                Skill s = Skill.byId(e.getKey());
                return s == null || e.getValue() == null || !Rune.valid(s, e.getValue());
            });
        }
        sanitizeExt();
        cooldowns.entrySet().removeIf(e -> Skill.byId(e.getKey()) == null || e.getValue() == null || e.getValue() <= 0);
        globalCooldown = Math.max(0, globalCooldown);
        if (Float.isNaN(mana)) {
            mana = -1;
        }
    }

    // 変異・紋の検め (知らない id・壊れた形を落とす。技を覚えているかは見ない: 転生で技が空になっても残る)
    void sanitizeExt() {
        mutations.entrySet().removeIf(e -> {
            Mutation m = Mutation.byId(e.getValue());
            return m == null || !m.skill.id.equals(e.getKey());
        });
        glyphBag.removeIf(g -> Glyph.parse(g) == null);
        while (glyphBag.size() > SkillRules.GLYPH_BAG_MAX) {
            glyphBag.remove(glyphBag.size() - 1);
        }
        for (int i = 0; i < glyphSockets.length; i++) {
            String g = glyphSockets[i];
            if (g == null || (!g.isEmpty() && Glyph.parsePlaced(g) == null)) {
                glyphSockets[i] = "";
            }
        }
    }

    // 前提のどれも覚えていない技を、先から順に戻す (何度でも。木の上の技が消えれば下の技も消える)
    void pruneBroken() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Skill s : Skill.values()) {
                if (knows(s) && !requirementsMet(s)) {
                    ranks.remove(s.id);
                    runes.remove(s.id);
                    changed = true;
                }
            }
        }
    }

    // 振り直し: 覚えた技と刻印と置き場を空にする (点は戻る)。マナとクールダウンと型の保存の枠の置き方はそのまま
    public void clearSkills() {
        nodes.clear();
        ranks.clear();
        // 深淵術のランクに使った点も戻す (術のランクは abyss/arts/AbyssArts.reconcile が 1 へ戻す)
        artPoints = 0;
        runes.clear();
        keystones.clear();
        Arrays.fill(slots, "");
        Arrays.fill(bars[0], "");
        Arrays.fill(bars[1], "");
        barUsed[0] = false;
        barUsed[1] = false;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", version);
        CompoundTag r = new CompoundTag();
        ranks.forEach(r::putInt);
        tag.put("ranks", r);
        CompoundTag ru = new CompoundTag();
        runes.forEach(ru::putInt);
        tag.put("runes", ru);
        ListTag s = new ListTag();
        for (String id : slots) {
            s.add(StringTag.valueOf(id));
        }
        tag.put("slots", s);
        ListTag ld = new ListTag();
        for (Loadout l : loadouts) {
            CompoundTag lt = new CompoundTag();
            ListTag ls = new ListTag();
            for (String id : l.slots) {
                ls.add(StringTag.valueOf(id));
            }
            lt.put("slots", ls);
            CompoundTag lr = new CompoundTag();
            l.runes.forEach(lr::putInt);
            lt.put("runes", lr);
            lt.putBoolean("saved", l.saved);
            ListTag ln = new ListTag();
            for (String id : l.nodes) {
                ln.add(StringTag.valueOf(id));
            }
            lt.put("nodes", ln);
            ld.add(lt);
        }
        tag.put("jobLoadouts", ld);
        ListTag ks = new ListTag();
        for (String id : keystones) {
            ks.add(StringTag.valueOf(id));
        }
        tag.put("keystones", ks);
        CompoundTag ms = new CompoundTag();
        mastery.forEach(ms::putInt);
        tag.put("mastery", ms);
        tag.putInt("bar", bar);
        CompoundTag jb = new CompoundTag();
        jobBars.forEach((k, v) -> {
            ListTag l = new ListTag();
            for (String id : v) {
                l.add(StringTag.valueOf(id == null ? "" : id));
            }
            jb.put(k.isEmpty() ? "_" : k, l);
        });
        tag.put("jobBars", jb);
        tag.putString("curJob", curJob);
        CompoundTag nd = new CompoundTag();
        nodes.forEach((k, v) -> {
            ListTag l = new ListTag();
            for (String id : v) {
                l.add(StringTag.valueOf(id));
            }
            nd.put(k, l);
        });
        tag.put("nodes", nd);
        for (int b = 0; b < 2; b++) {
            ListTag bl = new ListTag();
            for (String id : bars[b]) {
                bl.add(StringTag.valueOf(id));
            }
            tag.put("bar" + b, bl);
            tag.putBoolean("barUsed" + b, barUsed[b]);
        }
        tag.putInt("bonusPoints", bonusPoints);
        tag.putInt("artPoints", artPoints);
        tag.putInt("respecs", respecs);
        tag.putInt("runeSwaps", runeSwaps);
        tag.putFloat("mana", mana);
        CompoundTag c = new CompoundTag();
        cooldowns.forEach(c::putInt);
        tag.put("cooldowns", c);
        ListTag u = new ListTag();
        for (String id : unlockedTrees) {
            u.add(StringTag.valueOf(id));
        }
        tag.put("unlockedTrees", u);
        tag.putInt("notifiedPoints", notifiedPoints);
        CompoundTag mu = new CompoundTag();
        mutations.forEach(mu::putString);
        tag.put("mutations", mu);
        ListTag gb = new ListTag();
        for (String g : glyphBag) {
            gb.add(StringTag.valueOf(g));
        }
        tag.put("glyphBag", gb);
        ListTag gs = new ListTag();
        for (String g : glyphSockets) {
            gs.add(StringTag.valueOf(g));
        }
        tag.put("glyphSockets", gs);
        ListTag gc = new ListTag();
        for (String g : glyphClaims) {
            gc.add(StringTag.valueOf(g));
        }
        tag.put("glyphClaims", gc);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        // v がなければ前の版 (1)
        version = tag.contains("v") ? tag.getInt("v") : 1;
        ranks.clear();
        CompoundTag r = tag.getCompound("ranks");
        for (String key : r.getAllKeys()) {
            ranks.put(key, r.getInt(key));
        }
        runes.clear();
        CompoundTag ru = tag.getCompound("runes");
        for (String key : ru.getAllKeys()) {
            runes.put(key, ru.getInt(key));
        }
        Arrays.fill(slots, "");
        ListTag s = tag.getList("slots", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(s.size(), slots.length); i++) {
            slots[i] = s.getString(i);
        }
        jobBars.clear();
        CompoundTag jb = tag.getCompound("jobBars");
        for (String k : jb.getAllKeys()) {
            ListTag l = jb.getList(k, Tag.TAG_STRING);
            String[] v = new String[SkillRules.SLOTS];
            Arrays.fill(v, "");
            for (int i = 0; i < Math.min(l.size(), v.length); i++) {
                v[i] = l.getString(i);
            }
            jobBars.put(k.equals("_") ? "" : k, v);
        }
        curJob = tag.getString("curJob");
        nodes.clear();
        CompoundTag nd = tag.getCompound("nodes");
        for (String k : nd.getAllKeys()) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (Tag t : nd.getList(k, Tag.TAG_STRING)) {
                set.add(t.getAsString());
            }
            nodes.put(k, set);
        }
        for (Loadout l : loadouts) {
            l.copyFrom(new Loadout());
        }
        ListTag ld = tag.getList("jobLoadouts", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(ld.size(), loadouts.length); i++) {
            CompoundTag lt = ld.getCompound(i);
            Loadout l = loadouts[i];
            ListTag ls = lt.getList("slots", Tag.TAG_STRING);
            for (int k = 0; k < Math.min(ls.size(), l.slots.length); k++) {
                l.slots[k] = ls.getString(k);
            }
            CompoundTag lr = lt.getCompound("runes");
            for (String key : lr.getAllKeys()) {
                l.runes.put(key, lr.getInt(key));
            }
            l.saved = lt.getBoolean("saved");
            l.nodes.clear();
            for (Tag t : lt.getList("nodes", Tag.TAG_STRING)) {
                l.nodes.add(t.getAsString());
            }
        }
        keystones.clear();
        for (Tag t : tag.getList("keystones", Tag.TAG_STRING)) {
            keystones.add(t.getAsString());
        }
        mastery.clear();
        CompoundTag ms = tag.getCompound("mastery");
        for (String key : ms.getAllKeys()) {
            mastery.put(key, ms.getInt(key));
        }
        bar = tag.getInt("bar");
        for (int b = 0; b < 2; b++) {
            Arrays.fill(bars[b], "");
            ListTag bl = tag.getList("bar" + b, Tag.TAG_STRING);
            for (int i = 0; i < Math.min(bl.size(), bars[b].length); i++) {
                bars[b][i] = bl.getString(i);
            }
            barUsed[b] = tag.getBoolean("barUsed" + b);
        }
        bonusPoints = tag.getInt("bonusPoints");
        artPoints = tag.getInt("artPoints");
        respecs = tag.getInt("respecs");
        runeSwaps = tag.getInt("runeSwaps");
        mana = tag.contains("mana") ? tag.getFloat("mana") : -1;
        cooldowns.clear();
        CompoundTag c = tag.getCompound("cooldowns");
        for (String key : c.getAllKeys()) {
            cooldowns.put(key, c.getInt(key));
        }
        unlockedTrees.clear();
        for (Tag t : tag.getList("unlockedTrees", Tag.TAG_STRING)) {
            unlockedTrees.add(t.getAsString());
        }
        notifiedPoints = tag.getInt("notifiedPoints");
        mutations.clear();
        CompoundTag mu = tag.getCompound("mutations");
        for (String key : mu.getAllKeys()) {
            mutations.put(key, mu.getString(key));
        }
        glyphBag.clear();
        for (Tag t : tag.getList("glyphBag", Tag.TAG_STRING)) {
            glyphBag.add(t.getAsString());
        }
        Arrays.fill(glyphSockets, "");
        ListTag gs = tag.getList("glyphSockets", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(gs.size(), glyphSockets.length); i++) {
            glyphSockets[i] = gs.getString(i);
        }
        glyphClaims.clear();
        for (Tag t : tag.getList("glyphClaims", Tag.TAG_STRING)) {
            glyphClaims.add(t.getAsString());
        }
        // レベルを知らないので、点の検めはログインのとき (SkillManager.onLogin) に行う。ここでは形だけ整える
        sanitize(50);
    }

    // 持ち主のクライアントへ送る分 (返し刃の期限などサーバーだけの値は送らない)
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeVarInt(d.ranks.size());
                d.ranks.forEach((id, n) -> {
                    buf.writeUtf(id, 64);
                    buf.writeVarInt(n);
                });
                buf.writeVarInt(d.runes.size());
                d.runes.forEach((id, n) -> {
                    buf.writeUtf(id, 64);
                    buf.writeVarInt(n);
                });
                for (String id : d.slots) {
                    buf.writeUtf(id, 64);
                }
                for (Loadout l : d.loadouts) {
                    buf.writeBoolean(l.saved);
                    for (String id : l.slots) {
                        buf.writeUtf(id, 64);
                    }
                    buf.writeVarInt(l.runes.size());
                    l.runes.forEach((id, n) -> {
                        buf.writeUtf(id, 64);
                        buf.writeVarInt(n);
                    });
                    buf.writeVarInt(l.nodes.size());
                    for (String id : l.nodes) {
                        buf.writeUtf(id, 64);
                    }
                }
                buf.writeVarInt(d.nodes.size());
                d.nodes.forEach((job, set) -> {
                    buf.writeUtf(job, 32);
                    buf.writeVarInt(set.size());
                    for (String id : set) {
                        buf.writeUtf(id, 64);
                    }
                });
                buf.writeVarInt(d.keystones.size());
                for (String id : d.keystones) {
                    buf.writeUtf(id, 32);
                }
                buf.writeVarInt(d.mastery.size());
                d.mastery.forEach((id, n) -> {
                    buf.writeUtf(id, 64);
                    buf.writeVarInt(n);
                });
                buf.writeVarInt(d.bar);
                for (int b = 0; b < 2; b++) {
                    buf.writeBoolean(d.barUsed[b]);
                    for (String id : d.bars[b]) {
                        buf.writeUtf(id, 64);
                    }
                }
                buf.writeVarInt(d.bonusPoints);
                buf.writeVarInt(d.artPoints);
                buf.writeVarInt(d.respecs);
                buf.writeVarInt(d.runeSwaps);
                buf.writeFloat(d.mana);
                buf.writeVarInt(d.cooldowns.size());
                d.cooldowns.forEach((id, n) -> {
                    buf.writeUtf(id, 64);
                    buf.writeVarInt(n);
                });
                buf.writeVarInt(d.globalCooldown);
                buf.writeVarInt(d.unlockedTrees.size());
                for (String id : d.unlockedTrees) {
                    buf.writeUtf(id, 64);
                }
                buf.writeVarInt(d.rebirth);
                buf.writeVarInt(d.mutations.size());
                d.mutations.forEach((k, v) -> {
                    buf.writeUtf(k, 64);
                    buf.writeUtf(v, 64);
                });
                buf.writeVarInt(d.glyphBag.size());
                for (String g : d.glyphBag) {
                    buf.writeUtf(g, 64);
                }
                for (String g : d.glyphSockets) {
                    buf.writeUtf(g, 96);
                }
                buf.writeUtf(d.curJob, 32);
            },
            buf -> {
                SkillData d = new SkillData();
                int n = buf.readVarInt();
                for (int i = 0; i < n; i++) {
                    d.ranks.put(buf.readUtf(64), buf.readVarInt());
                }
                int rn = buf.readVarInt();
                for (int i = 0; i < rn; i++) {
                    d.runes.put(buf.readUtf(64), buf.readVarInt());
                }
                for (int i = 0; i < d.slots.length; i++) {
                    d.slots[i] = buf.readUtf(64);
                }
                for (Loadout l : d.loadouts) {
                    l.saved = buf.readBoolean();
                    for (int i = 0; i < l.slots.length; i++) {
                        l.slots[i] = buf.readUtf(64);
                    }
                    int lr = buf.readVarInt();
                    for (int i = 0; i < lr; i++) {
                        l.runes.put(buf.readUtf(64), buf.readVarInt());
                    }
                    int lnn = buf.readVarInt();
                    for (int i = 0; i < lnn; i++) {
                        l.nodes.add(buf.readUtf(64));
                    }
                }
                int jn = buf.readVarInt();
                for (int j = 0; j < jn; j++) {
                    String job = buf.readUtf(32);
                    int cnt = buf.readVarInt();
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    for (int i = 0; i < cnt; i++) {
                        set.add(buf.readUtf(64));
                    }
                    d.nodes.put(job, set);
                }
                int kn = buf.readVarInt();
                for (int i = 0; i < kn; i++) {
                    d.keystones.add(buf.readUtf(32));
                }
                int mn = buf.readVarInt();
                for (int i = 0; i < mn; i++) {
                    d.mastery.put(buf.readUtf(64), buf.readVarInt());
                }
                d.bar = buf.readVarInt();
                for (int b = 0; b < 2; b++) {
                    d.barUsed[b] = buf.readBoolean();
                    for (int i = 0; i < d.bars[b].length; i++) {
                        d.bars[b][i] = buf.readUtf(64);
                    }
                }
                d.bonusPoints = buf.readVarInt();
                d.artPoints = buf.readVarInt();
                d.respecs = buf.readVarInt();
                d.runeSwaps = buf.readVarInt();
                d.mana = buf.readFloat();
                int c = buf.readVarInt();
                for (int i = 0; i < c; i++) {
                    d.cooldowns.put(buf.readUtf(64), buf.readVarInt());
                }
                d.globalCooldown = buf.readVarInt();
                int u = buf.readVarInt();
                for (int i = 0; i < u; i++) {
                    d.unlockedTrees.add(buf.readUtf(64));
                }
                d.rebirth = buf.readVarInt();
                int mn2 = buf.readVarInt();
                for (int i = 0; i < mn2; i++) {
                    d.mutations.put(buf.readUtf(64), buf.readUtf(64));
                }
                int gn = buf.readVarInt();
                for (int i = 0; i < gn; i++) {
                    d.glyphBag.add(buf.readUtf(64));
                }
                for (int i = 0; i < d.glyphSockets.length; i++) {
                    d.glyphSockets[i] = buf.readUtf(96);
                }
                d.curJob = buf.readUtf(32);
                return d;
            });
}
