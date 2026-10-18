package jp.revati.skyabyss.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

// 巨神ひとつぶんの装備の表 (03 の 11章「潮殻の装備の実装」で、巨神ごとの装備を足しやすくするために作った共通の形)。
//  - titan: 由来の巨神の ID。ModGear.dungeon() と、巨神の記憶 (TitanMemoryData) の記録の名前に使う
//  - shard: その巨神の素材 (岩鯨の鱗片・潮殻の殻片・雷翼の羽軸・余燼の燠石。強化の燃料・レア以下の分解で戻るもの)
//  - core: その巨神の心核 (神器の材料。図鑑には数えない)
//  - pieces: 図鑑の品 (天井が数える装備)。並びは図鑑の並び。exclusive は専用伝説 (初回の選択と交換の対象)
// 品は ID (文字列) で持つ。まだ登録されていない品は抽選と図鑑から外す (作りかけの巨神でも表を先に書ける)。
// 登録は TitanGearSets.register。後の巨神 (雷翼・余燼) は、同じ形で表を一つ書いて登録すればよい
public final class TitanGearSet {
    public record Piece(String id, float heartChance, boolean exclusive) {
    }

    public final String titan;
    public final String shard;
    public final String core;
    private final List<Piece> pieces;

    public TitanGearSet(String titan, String shard, String core, List<Piece> pieces) {
        this.titan = titan;
        this.shard = shard;
        this.core = core;
        this.pieces = List.copyOf(pieces);
    }

    public static Piece piece(String id, float heartChance) {
        return new Piece(id, heartChance, false);
    }

    public static Piece exclusive(String id, float heartChance) {
        return new Piece(id, heartChance, true);
    }

    // ---------------------------------------------------------------- 表を読む
    public List<Piece> pieces() {
        return pieces;
    }

    // 図鑑の品の ID (並びは図鑑の並び)
    public List<String> collection() {
        return pieces.stream().map(Piece::id).toList();
    }

    public List<String> exclusives() {
        return pieces.stream().filter(Piece::exclusive).map(Piece::id).toList();
    }

    public boolean contains(String id) {
        return pieces.stream().anyMatch(p -> p.id().equals(id));
    }

    public boolean isExclusive(String id) {
        return pieces.stream().anyMatch(p -> p.exclusive() && p.id().equals(id));
    }

    // 心臓での基本の確率 (図鑑にない品は 0)
    public float chance(String id) {
        for (Piece p : pieces) {
            if (p.id().equals(id)) {
                return p.heartChance();
            }
        }
        return 0f;
    }

    public int collected(TitanMemoryData.Record r) {
        return (int) collection().stream().filter(r.owned::contains).count();
    }

    // 図鑑の大きさの見出し。巨神の名前は gear.skyabyss.origin.<titan> (例「潮殻の専用装備」)
    public String originKey() {
        return "gear.skyabyss.origin." + titan;
    }

    public static Optional<Item> item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rl);
    }

    public static boolean registered(String id) {
        return item(id).isPresent();
    }

    public Optional<Item> shardItem() {
        return item(shard);
    }

    // ---------------------------------------------------------------- 抽選 (図鑑の品。岩鯨・潮殻と同じ決まり)
    // 天井 (TitanLoot.SOFT_PITY / HARD_PITY): memory はこの撃破を数えたあとの記憶。
    //   HARD 以上なら、出せる品のうち持っていない品を一つ保証。全部持っていれば pityShards (共鳴の欠片に替える) で、記憶を 0 に。
    //   SOFT 以上なら、図鑑の確率を 2 倍
    public record Pieces(List<String> pieces, @Nullable String guaranteed, boolean pityShards) {
    }

    public Pieces rollPieces(TitanMemoryData.Record r, int memory, RandomSource random, Predicate<String> available) {
        List<String> out = new ArrayList<>();
        String guaranteed = null;
        boolean pityShards = false;
        if (memory >= TitanLoot.HARD_PITY) {
            List<String> missing = collection().stream().filter(i -> available.test(i) && !r.owned.contains(i)).toList();
            if (missing.isEmpty()) {
                pityShards = true;
                r.memory = 0;
            } else {
                guaranteed = missing.get(random.nextInt(missing.size()));
                out.add(guaranteed);
            }
        }
        float mult = TitanLoot.pityMultiplier(memory);
        for (Piece p : pieces) {
            if (!p.id().equals(guaranteed) && available.test(p.id()) && random.nextFloat() < p.heartChance() * mult) {
                out.add(p.id());
            }
        }
        return new Pieces(out, guaranteed, pityShards);
    }

    // 出た図鑑の品を記す。初めての品を返し、一つでもあれば記憶を 0 に戻す (重複では戻さない)
    public List<String> record(TitanMemoryData.Record r, List<String> got) {
        List<String> fresh = new ArrayList<>();
        for (String id : got) {
            if (contains(id) && r.owned.add(id)) {
                fresh.add(id);
            }
        }
        if (!fresh.isEmpty()) {
            r.memory = 0;
        }
        return fresh;
    }
}
