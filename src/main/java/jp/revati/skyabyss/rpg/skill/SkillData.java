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
    // 記録の版。1 = 剣士・弓使いの木の 10 の技だけ、2 = 5 つの木・刻印・型の保存 (docs/design/27)
    public static final int VERSION = 2;

    // 型の保存 1 つぶん: 枠の置き方と、技ごとの刻印の選び
    public static final class Loadout {
        public final String[] slots = new String[SkillRules.SLOTS];
        public final Map<String, Integer> runes = new LinkedHashMap<>();
        public boolean saved;

        public Loadout() {
            Arrays.fill(slots, "");
        }

        public void copyFrom(Loadout o) {
            System.arraycopy(o.slots, 0, slots, 0, slots.length);
            runes.clear();
            runes.putAll(o.runes);
            saved = o.saved;
        }

        public boolean sameAs(Loadout o) {
            return saved == o.saved && Arrays.equals(slots, o.slots) && runes.equals(o.runes);
        }
    }

    // 覚えた技と段 (id → 1..maxRank)
    public final Map<String, Integer> ranks = new LinkedHashMap<>();
    // 刻印の選び (技の id → 刻印の番号 1..)。選んだ技の分だけ点を使う
    public final Map<String, Integer> runes = new LinkedHashMap<>();
    // 技の置き場 (最大 5 枠。いま使えるのは slotCount() まで)。空は ""
    public final String[] slots = new String[SkillRules.SLOTS];
    // 型の保存 (2 つ)
    public final Loadout[] loadouts = {new Loadout(), new Loadout()};
    // 記録の版 (読んだ記録が古ければ sanitize で移す)
    public int version = VERSION;
    // レベル以外から得た技の点 (クエストや記憶の断片の報酬に使う予定。今は 0)
    public int bonusPoints;
    // これまでに振り直した回数 (費用が上がる)
    public int respecs;
    // これまでに刻印を付け替えた回数 (初回は無料)
    public int runeSwaps;
    // 天職 (木の id。選ぶまで空)。2 つ目は第三章のあと
    public String calling = "";
    public String calling2 = "";
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
    }

    public int rank(Skill skill) {
        return ranks.getOrDefault(skill.id, 0);
    }

    public boolean knows(Skill skill) {
        return rank(skill) > 0;
    }

    // 選んでいる刻印の番号 (なければ 0)
    public int rune(Skill skill) {
        return runes.getOrDefault(skill.id, 0);
    }

    public int spentPoints() {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            if (Skill.byId(e.getKey()) != null) {
                sum += e.getValue();
            }
        }
        for (String id : runes.keySet()) {
            if (Skill.byId(id) != null) {
                sum += 1;
            }
        }
        return sum;
    }

    public int totalPoints(int level) {
        return SkillRules.pointsForLevel(level) + bonusPoints;
    }

    public int unspentPoints(int level) {
        return totalPoints(level) - spentPoints();
    }

    // いま使える枠の数 (2 つ目の天職を迎えると 5)
    public int slotCount() {
        return calling2.isEmpty() ? SkillRules.BASE_SLOTS : SkillRules.SLOTS;
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

    // ---------------------------------------------------------------- 天職
    public boolean hasCalling() {
        return !calling.isEmpty();
    }

    public SkillTree callingTree() {
        return SkillTree.byId(calling);
    }

    public SkillTree calling2Tree() {
        return SkillTree.byId(calling2);
    }

    // ---------------------------------------------------------------- 習得の条件
    public enum LearnResult {
        OK, UNKNOWN, SEALED, MAX_RANK, NO_POINTS, LEVEL, REQUIRES, CALLING
    }

    public LearnResult canLearn(Skill skill, int level) {
        if (skill == null) {
            return LearnResult.UNKNOWN;
        }
        if (!treeOpen(skill.tree)) {
            return LearnResult.SEALED;
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
        if (!callingAllows(skill)) {
            return LearnResult.CALLING;
        }
        if (unspentPoints(level) < 1) {
            return LearnResult.NO_POINTS;
        }
        return LearnResult.OK;
    }

    public boolean requirementsMet(Skill skill) {
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

    // ---------------------------------------------------------------- 天職の決まり (docs/design/27 の 10 節)
    // 天職の前: 5 つの木の 1 行目と、旅人の 1 行目だけ、段は 3 まで
    // 天職のあと: 天職の木はすべて (要まで)、2 つ目の天職の木は 3 行目まで (要なし)、
    //   ほかの木は 1 行目だけ・段は 2 まで、旅人はすべて
    public boolean callingAllows(Skill skill) {
        if (!SkillRules.CALLINGS) {
            return true;
        }
        if (skill.tree == SkillTree.TRAVELER || skill.tree.sealed) {
            return !hasCalling() ? skill.tier == 0 : true;
        }
        if (!hasCalling()) {
            return skill.tier == 0;
        }
        if (skill.tree.id.equals(calling)) {
            return true;
        }
        if (skill.tree.id.equals(calling2)) {
            return !skill.keystone();
        }
        return skill.tier == 0;
    }

    // 今の天職で、その技を覚えられる最大の段 (ほかの木の技は 2 まで)
    public int rankLimit(Skill skill) {
        if (!SkillRules.CALLINGS || !hasCalling() || skill.tree == SkillTree.TRAVELER || skill.tree.sealed || skill.tree.id.equals(calling)
                || skill.tree.id.equals(calling2)) {
            return skill.maxRank;
        }
        return Math.min(skill.maxRank, 2);
    }

    // 刻印が使える状態か (天職の前は刻印なし。ほかの木の技にもなし。2 つ目の天職の木は可)
    public boolean runesOpen(Skill skill) {
        if (!SkillRules.CALLINGS) {
            return true;
        }
        if (!hasCalling()) {
            return false;
        }
        return skill.tree.id.equals(calling) || skill.tree.id.equals(calling2);
    }

    // その技の刻印を選べるか (技を極めている・刻印がある・天職の木)
    public boolean runeAvailable(Skill skill) {
        return skill.active && rank(skill) >= skill.maxRank && (!skill.runes().isEmpty() || Rune.legendaryOf(skill) != null) && runesOpen(skill);
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
        if (calling == null || SkillTree.byId(calling) == null || !SkillTree.byId(calling).callable()) {
            calling = "";
        }
        if (calling2 == null || SkillTree.byId(calling2) == null || !SkillTree.byId(calling2).callable() || calling.isEmpty()
                || calling2.equals(calling)) {
            calling2 = "";
        }
        // 前の版の記録: 木が組み替わったので、前提が崩れた技 (と、その先) だけ戻す。ほかは残す
        if (version < VERSION) {
            pruneBroken();
            version = VERSION;
        }
        // 刻印: 技を極めている・刻印がある・選びが範囲内、のものだけ残す
        runes.entrySet().removeIf(e -> {
            Skill s = Skill.byId(e.getKey());
            return s == null || e.getValue() == null || Rune.of(s, e.getValue()) == null || rank(s) < s.maxRank;
        });
        // 点を使いすぎている (レベルを下げられた) か、前提が崩れていたら、すべて戻して振り直してもらう。
        // 要るレベル (minLevel) は覚えるときだけの条件で、あとから下がっても消さない
        boolean broken = spentPoints() > totalPoints(level);
        for (String id : ranks.keySet()) {
            Skill s = Skill.byId(id);
            if (!requirementsMet(s) || !treeOpen(s.tree)) {
                broken = true;
            }
        }
        if (broken) {
            ranks.clear();
            runes.clear();
        }
        for (int i = 0; i < slots.length; i++) {
            Skill s = Skill.byId(slots[i]);
            if (s == null || !s.active || !knows(s) || i >= slotCount()) {
                slots[i] = "";
            }
            // 同じ技が二つの枠にあれば、後のほうを空ける
            for (int j = 0; j < i; j++) {
                if (!slots[i].isEmpty() && slots[i].equals(slots[j])) {
                    slots[i] = "";
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
                return s == null || e.getValue() == null || Rune.of(s, e.getValue()) == null;
            });
        }
        cooldowns.entrySet().removeIf(e -> Skill.byId(e.getKey()) == null || e.getValue() == null || e.getValue() <= 0);
        globalCooldown = Math.max(0, globalCooldown);
        if (Float.isNaN(mana)) {
            mana = -1;
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
        ranks.clear();
        runes.clear();
        Arrays.fill(slots, "");
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
            ld.add(lt);
        }
        tag.put("loadouts", ld);
        tag.putInt("bonusPoints", bonusPoints);
        tag.putInt("respecs", respecs);
        tag.putInt("runeSwaps", runeSwaps);
        tag.putString("calling", calling);
        tag.putString("calling2", calling2);
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
        for (Loadout l : loadouts) {
            l.copyFrom(new Loadout());
        }
        ListTag ld = tag.getList("loadouts", Tag.TAG_COMPOUND);
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
        }
        bonusPoints = tag.getInt("bonusPoints");
        respecs = tag.getInt("respecs");
        runeSwaps = tag.getInt("runeSwaps");
        calling = tag.getString("calling");
        calling2 = tag.getString("calling2");
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
                }
                buf.writeVarInt(d.bonusPoints);
                buf.writeVarInt(d.respecs);
                buf.writeVarInt(d.runeSwaps);
                buf.writeUtf(d.calling, 32);
                buf.writeUtf(d.calling2, 32);
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
                }
                d.bonusPoints = buf.readVarInt();
                d.respecs = buf.readVarInt();
                d.runeSwaps = buf.readVarInt();
                d.calling = buf.readUtf(32);
                d.calling2 = buf.readUtf(32);
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
                return d;
            });
}
