package jp.revati.skyabyss.rpg;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;

// プレイヤーごとの成長の記録 (Data Attachment)。
// 死んでも (copyOnDeath)、ディメンションを移っても、ログアウトしても残る。月のループでも消さない。
// 書き換えは Growth を通す (同期と修飾子の付け直しをそこでまとめて行う)
public final class GrowthData implements INBTSerializable<CompoundTag> {
    public int level = 1;
    // いまのレベルの中で貯めた経験値 (次のレベルに要る量より少ない)
    public int xp;
    public final int[] stats = new int[Stat.values().length];
    // レベル以外から得た力 (記憶の断片やクエストの報酬に使う予定。今は 0)
    public int bonusPoints;
    // 記憶の断片: 種類 (ID) ごとの数。上限は capacity
    public final Map<String, Integer> fragments = new LinkedHashMap<>();
    public int fragmentCapacity = GrowthRules.FRAGMENT_CAPACITY;
    // ステータスを振り直した回数 (費用を決める。技の振り直しとは別に数える。持ち主のクライアントへも送る)
    public int statRespecs;
    // 転生した回数 (0〜20。docs/design/31)。表示は「Lv 50+n」、必要経験値が減る。持ち主のクライアントへも送る
    public int rebirths;

    // ここから下はサーバーだけが使う (クライアントへは送らない)
    // 一度だけの経験値 (新しい場所、初めての撃破) の記録
    public final Set<String> discoveries = new LinkedHashSet<>();
    // バニラの敵から今日もらった量 (日が変わると 0)
    public long vanillaDay = -1;
    public int vanillaToday;

    public int stat(Stat s) {
        return stats[s.ordinal()];
    }

    public int spentPoints() {
        int sum = 0;
        for (int v : stats) {
            sum += v;
        }
        return sum;
    }

    public int totalPoints() {
        return GrowthRules.pointsForLevel(level) + bonusPoints;
    }

    public int unspentPoints() {
        return totalPoints() - spentPoints();
    }

    public int fragmentCount() {
        int sum = 0;
        for (int v : fragments.values()) {
            sum += v;
        }
        return sum;
    }

    // 記憶の断片を器へ入れる。入りきらない分は入れず、入れた数を返す
    public int addFragments(String id, int count) {
        int room = Math.max(0, fragmentCapacity - fragmentCount());
        int added = Math.min(room, Math.max(0, count));
        if (added > 0) {
            fragments.merge(id, added, Integer::sum);
        }
        return added;
    }

    // いまのレベルから次へ要る経験値 (転生ごとに減る。B03)
    public int xpNeed() {
        return GrowthRules.xpToNext(level, rebirths);
    }

    public long lifetimeXp() {
        return GrowthRules.totalXpFor(level) + xp;
    }

    // 壊れた値 (手で書き換えた NBT など) を、遊べる範囲に戻す
    public void sanitize() {
        level = Mth.clamp(level, 1, GrowthRules.MAX_LEVEL);
        rebirths = Mth.clamp(rebirths, 0, jp.revati.skyabyss.rebirth.RebirthRules.MAX_REBIRTHS);
        xp = level >= GrowthRules.MAX_LEVEL ? 0 : Mth.clamp(xp, 0, xpNeed() - 1);
        bonusPoints = Math.max(0, bonusPoints);
        for (int i = 0; i < stats.length; i++) {
            stats[i] = Mth.clamp(stats[i], 0, GrowthRules.STAT_CAP);
        }
        // 基礎は技の木から導く (SkillManager.refreshStats が上書きする) ので、ここでは使いすぎを見ない
        fragmentCapacity = Math.max(0, fragmentCapacity);
        statRespecs = Math.max(0, statRespecs);
        fragments.values().removeIf(v -> v == null || v <= 0);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("xp", xp);
        CompoundTag s = new CompoundTag();
        for (Stat stat : Stat.values()) {
            s.putInt(stat.id, stat(stat));
        }
        tag.put("stats", s);
        tag.putInt("bonusPoints", bonusPoints);
        CompoundTag f = new CompoundTag();
        fragments.forEach(f::putInt);
        tag.put("fragments", f);
        tag.putInt("fragmentCapacity", fragmentCapacity);
        tag.putInt("statRespecs", statRespecs);
        tag.putInt("rebirths", rebirths);
        ListTag d = new ListTag();
        for (String id : discoveries) {
            d.add(StringTag.valueOf(id));
        }
        tag.put("discoveries", d);
        tag.putLong("vanillaDay", vanillaDay);
        tag.putInt("vanillaToday", vanillaToday);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        level = tag.contains("level") ? tag.getInt("level") : 1;
        xp = tag.getInt("xp");
        CompoundTag s = tag.getCompound("stats");
        for (Stat stat : Stat.values()) {
            stats[stat.ordinal()] = s.getInt(stat.id);
        }
        bonusPoints = tag.getInt("bonusPoints");
        fragments.clear();
        CompoundTag f = tag.getCompound("fragments");
        for (String key : f.getAllKeys()) {
            fragments.put(key, f.getInt(key));
        }
        fragmentCapacity = tag.contains("fragmentCapacity") ? tag.getInt("fragmentCapacity") : GrowthRules.FRAGMENT_CAPACITY;
        statRespecs = tag.getInt("statRespecs");
        rebirths = tag.getInt("rebirths");
        discoveries.clear();
        for (Tag t : tag.getList("discoveries", Tag.TAG_STRING)) {
            discoveries.add(t.getAsString());
        }
        vanillaDay = tag.contains("vanillaDay") ? tag.getLong("vanillaDay") : -1;
        vanillaToday = tag.getInt("vanillaToday");
        sanitize();
    }

    // 持ち主のクライアントへ送る分 (発見の記録とバニラの日ごとの量は送らない)
    public static final StreamCodec<RegistryFriendlyByteBuf, GrowthData> STREAM_CODEC = StreamCodec.of(
            (buf, data) -> {
                buf.writeVarInt(data.level);
                buf.writeVarInt(data.xp);
                for (int v : data.stats) {
                    buf.writeVarInt(v);
                }
                buf.writeVarInt(data.bonusPoints);
                buf.writeVarInt(data.fragmentCapacity);
                buf.writeVarInt(data.fragments.size());
                data.fragments.forEach((id, n) -> {
                    buf.writeUtf(id);
                    buf.writeVarInt(n);
                });
                buf.writeVarInt(data.statRespecs);
                buf.writeVarInt(data.rebirths);
            },
            buf -> {
                GrowthData data = new GrowthData();
                data.level = buf.readVarInt();
                data.xp = buf.readVarInt();
                for (int i = 0; i < data.stats.length; i++) {
                    data.stats[i] = buf.readVarInt();
                }
                data.bonusPoints = buf.readVarInt();
                data.fragmentCapacity = buf.readVarInt();
                int n = buf.readVarInt();
                for (int i = 0; i < n; i++) {
                    data.fragments.put(buf.readUtf(), buf.readVarInt());
                }
                data.statRespecs = buf.readVarInt();
                data.rebirths = buf.readVarInt();
                return data;
            });
}
