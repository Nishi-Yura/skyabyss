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
    // 覚えた技と段 (id → 1..maxRank)
    public final Map<String, Integer> ranks = new LinkedHashMap<>();
    // 技の置き場 (4 枠)。空は ""
    public final String[] slots = new String[SkillRules.SLOTS];
    // レベル以外から得た技の点 (クエストや記憶の断片の報酬に使う予定。今は 0)
    public int bonusPoints;
    // これまでに振り直した回数 (費用が上がる)
    public int respecs;
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

    public SkillData() {
        Arrays.fill(slots, "");
    }

    public int rank(Skill skill) {
        return ranks.getOrDefault(skill.id, 0);
    }

    public boolean knows(Skill skill) {
        return rank(skill) > 0;
    }

    public int spentPoints() {
        int sum = 0;
        for (Map.Entry<String, Integer> e : ranks.entrySet()) {
            if (Skill.byId(e.getKey()) != null) {
                sum += e.getValue();
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

    public Skill slot(int i) {
        return i >= 0 && i < slots.length ? Skill.byId(slots[i]) : null;
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
        OK, UNKNOWN, SEALED, MAX_RANK, NO_POINTS, LEVEL, REQUIRES
    }

    public LearnResult canLearn(Skill skill, int level) {
        if (skill == null) {
            return LearnResult.UNKNOWN;
        }
        if (!treeOpen(skill.tree)) {
            return LearnResult.SEALED;
        }
        if (rank(skill) >= skill.maxRank) {
            return LearnResult.MAX_RANK;
        }
        if (level < skill.minLevel) {
            return LearnResult.LEVEL;
        }
        if (!requirementsMet(skill)) {
            return LearnResult.REQUIRES;
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

    // 壊れた値 (手で書き換えた NBT、技を消した版など) を遊べる範囲に戻す。level は成長の記録のレベル
    public void sanitize(int level) {
        ranks.entrySet().removeIf(e -> {
            Skill s = Skill.byId(e.getKey());
            return s == null || e.getValue() == null || e.getValue() <= 0;
        });
        ranks.replaceAll((k, v) -> Math.min(v, Skill.byId(k).maxRank));
        bonusPoints = Math.max(0, bonusPoints);
        respecs = Math.max(0, respecs);
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
        }
        for (int i = 0; i < slots.length; i++) {
            Skill s = Skill.byId(slots[i]);
            if (s == null || !s.active || !knows(s)) {
                slots[i] = "";
            }
            // 同じ技が二つの枠にあれば、後のほうを空ける
            for (int j = 0; j < i; j++) {
                if (!slots[i].isEmpty() && slots[i].equals(slots[j])) {
                    slots[i] = "";
                }
            }
        }
        cooldowns.entrySet().removeIf(e -> Skill.byId(e.getKey()) == null || e.getValue() == null || e.getValue() <= 0);
        globalCooldown = Math.max(0, globalCooldown);
        if (Float.isNaN(mana)) {
            mana = -1;
        }
    }

    // 振り直し: 覚えた技と置き場を空にする (点は戻る)。マナとクールダウンはそのまま
    public void clearSkills() {
        ranks.clear();
        Arrays.fill(slots, "");
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag r = new CompoundTag();
        ranks.forEach(r::putInt);
        tag.put("ranks", r);
        ListTag s = new ListTag();
        for (String id : slots) {
            s.add(StringTag.valueOf(id));
        }
        tag.put("slots", s);
        tag.putInt("bonusPoints", bonusPoints);
        tag.putInt("respecs", respecs);
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
        ranks.clear();
        CompoundTag r = tag.getCompound("ranks");
        for (String key : r.getAllKeys()) {
            ranks.put(key, r.getInt(key));
        }
        Arrays.fill(slots, "");
        ListTag s = tag.getList("slots", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(s.size(), slots.length); i++) {
            slots[i] = s.getString(i);
        }
        bonusPoints = tag.getInt("bonusPoints");
        respecs = tag.getInt("respecs");
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
                for (String id : d.slots) {
                    buf.writeUtf(id, 64);
                }
                buf.writeVarInt(d.bonusPoints);
                buf.writeVarInt(d.respecs);
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
                for (int i = 0; i < d.slots.length; i++) {
                    d.slots[i] = buf.readUtf(64);
                }
                d.bonusPoints = buf.readVarInt();
                d.respecs = buf.readVarInt();
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
