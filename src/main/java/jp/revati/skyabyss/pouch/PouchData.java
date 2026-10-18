package jp.revati.skyabyss.pouch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

// プレイヤーごとの袋の中身 (Data Attachment skyabyss:pouch)。品の ID → 個数だけを持つ (枠の位置は保存しない。22 の 3・8.1 章)。
//   保存: 版 v、移し替え済みの版 migrated、品の ID → 個数 items、いちど入れた品 seen、集める品を持ち物に残す印 hold
//   登録にない品の ID も、そのまま保存して読み飛ばす (mod を抜いたあと戻しても消えない)
//   袋に入れるのは部品のない (その品のままの) 品だけなので、22 の 8.1 の「見本 (proto)」は持たず、ID と個数だけで足りる
//   死んでも引き継ぐ (copyOnDeath)。持ち主のクライアントにだけ同期する (PouchSetup)
public final class PouchData {
    public static final int VERSION = 1;
    // 既存セーブの移し替え (PouchMigration) の版。上げると、次のログインでもう一度流れる
    public static final int MIGRATION_VERSION = 1;

    public static final Codec<PouchData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("v").forGetter(d -> Optional.of(d.version)),
            Codec.INT.optionalFieldOf("migrated", 0).forGetter(d -> d.migrated),
            Codec.BOOL.optionalFieldOf("hold", false).forGetter(d -> d.hold),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("items", Map.of()).forGetter(d -> d.counts),
            ResourceLocation.CODEC.listOf().optionalFieldOf("seen", List.of()).forGetter(d -> new ArrayList<>(d.seen)))
            .apply(i, PouchData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PouchData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, d -> d.version,
            ByteBufCodecs.VAR_INT, d -> d.migrated,
            ByteBufCodecs.BOOL, d -> d.hold,
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceLocation, Integer, Map<ResourceLocation, Integer>>map(LinkedHashMap::new,
                    ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT), d -> d.counts,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), d -> new ArrayList<>(d.seen),
            PouchData::new);

    // 保存の形の版 (読み込んだあとは今の版に読み替えて、次の保存から新しい版で書く)
    public int version = VERSION;
    // 移し替えを済ませた版 (0 = まだ)
    public int migrated;
    // 管理者の確かめ用: true の間は掃除が持ち物の品を袋へ移さない (22 の 4.4)
    public boolean hold;
    private final Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
    // いちど袋に入れた品 (初めてのときだけ知らせる。22 の 4.3)
    private final Set<ResourceLocation> seen = new LinkedHashSet<>();

    public PouchData() {
    }

    private PouchData(int version, int migrated, boolean hold, Map<ResourceLocation, Integer> items, List<ResourceLocation> seen) {
        this(Optional.of(version), migrated, hold, items, seen);
    }

    private PouchData(Optional<Integer> version, int migrated, boolean hold, Map<ResourceLocation, Integer> items, List<ResourceLocation> seen) {
        this.version = VERSION;
        this.migrated = migrated;
        this.hold = hold;
        items.forEach((id, n) -> {
            if (n > 0) {
                counts.put(id, n);
            }
        });
        this.seen.addAll(seen);
    }

    public int count(ResourceLocation id) {
        return counts.getOrDefault(id, 0);
    }

    public void set(ResourceLocation id, int n) {
        if (n <= 0) {
            counts.remove(id);
        } else {
            counts.put(id, n);
        }
    }

    public boolean markSeen(ResourceLocation id) {
        return seen.add(id);
    }

    public boolean hasSeen(ResourceLocation id) {
        return seen.contains(id);
    }

    // 読むだけの写し (品の ID → 個数。登録にない品も含む)
    public Map<ResourceLocation, Integer> snapshot() {
        return new LinkedHashMap<>(counts);
    }

    public void clear() {
        counts.clear();
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public int total() {
        int n = 0;
        for (int c : counts.values()) {
            n += c;
        }
        return n;
    }

    // 死んだあと・同期の写し
    public PouchData copy() {
        return new PouchData(Optional.of(version), migrated, hold, counts, new ArrayList<>(seen));
    }
}
