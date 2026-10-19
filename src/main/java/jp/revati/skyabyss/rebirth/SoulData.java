package jp.revati.skyabyss.rebirth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

// 転生で残るもの (Data Attachment skyabyss:rebirth)。魂盤の節 (転生した順の腕の id)、選んだ恩恵 (順)、導きの欠片、ポーチの余白の中身。
// 転生の回数そのものは GrowthData.rebirths (レベルと同じ所。表示と経験値のため)。
// 転生しても消えない (レベル・技・天職だけが戻る)。死んでも引き継ぐ。持ち主のクライアントにだけ同期する
public final class SoulData implements INBTSerializable<CompoundTag> {
    public static final int VERSION = 1;
    public static final int SPARE_MAX = 3;
    // 余白の枠 1 つに入る数
    public static final int SPARE_LIMIT = 999;

    public int version = VERSION;
    // 魂盤の節: 腕の id。i 番目が (i + 1) 番目の節 (転生の順 + 導きの欠片で足した節)
    public final List<String> nodes = new ArrayList<>();
    // 選んだ恩恵の id。i 番目が (i + 1) 回目の転生の恩恵
    public final List<String> boons = new ArrayList<>();
    // 導きの欠片 (B05): 持っている数と、欠片で足した節の数
    public int shards;
    public int shardNodes;
    // 導きの欠片をもらった日 (実際の日の通し番号) と、その日の回数・会った人 (サーバーだけ。同期しない)
    public long guideDay = -1;
    public int guideToday;
    public final Set<String> guideMet = new LinkedHashSet<>();
    // ポーチの余白 (B13): 品の id と数
    public final String[] spareItem = new String[SPARE_MAX];
    public final int[] spareCount = new int[SPARE_MAX];
    // 転生を重ねた総数の記録 (回数が減らないこと・最後に転生した時刻)
    public long lastRebirthMillis;

    public SoulData() {
        Arrays.fill(spareItem, "");
    }

    public int shardNodeRoom() {
        return RebirthRules.MAX_SHARD_NODES - shardNodes;
    }

    // 欠片で足せる節が 1 つあるか
    public boolean canClaimShardNode() {
        return shards >= RebirthRules.SHARDS_PER_NODE && shardNodes < RebirthRules.MAX_SHARD_NODES;
    }

    // 壊れた値を遊べる範囲へ戻す。nodes は転生の回数 + 欠片の節より多くしない
    public void sanitize(int rebirths) {
        nodes.removeIf(id -> SoulArm.byId(id) == null);
        boons.removeIf(id -> Boon.byId(id) == null);
        shards = Math.max(0, shards);
        shardNodes = Math.max(0, Math.min(RebirthRules.MAX_SHARD_NODES, shardNodes));
        guideToday = Math.max(0, guideToday);
        int allowed = rebirths + shardNodes;
        while (nodes.size() > allowed) {
            nodes.remove(nodes.size() - 1);
        }
        while (boons.size() > rebirths) {
            boons.remove(boons.size() - 1);
        }
        for (int i = 0; i < SPARE_MAX; i++) {
            if (spareItem[i] == null) {
                spareItem[i] = "";
            }
            if (spareCount[i] <= 0 || spareItem[i].isEmpty()) {
                spareCount[i] = 0;
                spareItem[i] = "";
            }
            spareCount[i] = Math.min(SPARE_LIMIT, spareCount[i]);
        }
    }

    public int nodesOf(SoulArm arm) {
        int n = 0;
        for (String id : nodes) {
            if (arm.id.equals(id)) {
                n++;
            }
        }
        return n;
    }

    public int boonCount(Boon boon) {
        int n = 0;
        for (String id : boons) {
            if (boon.id.equals(id)) {
                n++;
            }
        }
        return n;
    }

    public SoulData copy() {
        SoulData d = new SoulData();
        d.fromTag(toTag());
        return d;
    }

    // ---------------------------------------------------------------- 保存
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", version);
        tag.put("nodes", strings(nodes));
        tag.put("boons", strings(boons));
        tag.putInt("shards", shards);
        tag.putInt("shardNodes", shardNodes);
        tag.putLong("guideDay", guideDay);
        tag.putInt("guideToday", guideToday);
        tag.put("guideMet", strings(new ArrayList<>(guideMet)));
        ListTag spare = new ListTag();
        for (int i = 0; i < SPARE_MAX; i++) {
            CompoundTag s = new CompoundTag();
            s.putString("item", spareItem[i]);
            s.putInt("n", spareCount[i]);
            spare.add(s);
        }
        tag.put("spare", spare);
        tag.putLong("last", lastRebirthMillis);
        return tag;
    }

    public void fromTag(CompoundTag tag) {
        version = tag.contains("v") ? tag.getInt("v") : 1;
        nodes.clear();
        nodes.addAll(readStrings(tag, "nodes"));
        boons.clear();
        boons.addAll(readStrings(tag, "boons"));
        shards = tag.getInt("shards");
        shardNodes = tag.getInt("shardNodes");
        guideDay = tag.contains("guideDay") ? tag.getLong("guideDay") : -1;
        guideToday = tag.getInt("guideToday");
        guideMet.clear();
        guideMet.addAll(readStrings(tag, "guideMet"));
        Arrays.fill(spareItem, "");
        Arrays.fill(spareCount, 0);
        ListTag spare = tag.getList("spare", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(SPARE_MAX, spare.size()); i++) {
            CompoundTag s = spare.getCompound(i);
            spareItem[i] = s.getString("item");
            spareCount[i] = s.getInt("n");
        }
        lastRebirthMillis = tag.getLong("last");
        version = VERSION;
    }

    private static ListTag strings(List<String> list) {
        ListTag out = new ListTag();
        for (String s : list) {
            out.add(StringTag.valueOf(s));
        }
        return out;
    }

    private static List<String> readStrings(CompoundTag tag, String key) {
        List<String> out = new ArrayList<>();
        for (Tag t : tag.getList(key, Tag.TAG_STRING)) {
            out.add(t.getAsString());
        }
        return out;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return toTag();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        fromTag(tag);
    }

    // 持ち主のクライアントへ送る分 (全部。小さい)
    public static final StreamCodec<RegistryFriendlyByteBuf, SoulData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(tag -> {
        SoulData d = new SoulData();
        d.fromTag(tag);
        return d;
    }, SoulData::toTag).cast();
}
