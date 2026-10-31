package jp.revati.skyabyss.loot.box;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.item.GearTier;

// 封の箱の種類 (docs/design/52 の 2 章)。巨神の箱 4 種・階級の箱 (階級 × 帯)・淵の金の箱・塔の箱 (あいだの塔の報酬。docs/design/54 の 2.6)。
// 同じ種類 (= 同じ品) の箱は重なる。階級の箱は帯ごとに別の品にする (旅嚢は品ごとの数で持つので、帯を品で分ける)
public record BoxKind(String id, Type type, @Nullable String titan, GearTier tier, int band) {
    public enum Type {
        TITAN, RARITY, ARTIFACT, TOWER
    }

    public static final List<String> TITANS = List.of("stone_whale", "tide_shell", "thunder_wing", "ember");
    // 階級の箱が持つ帯 (その階級の品がある帯だけ)
    static final int[][] BANDS = {{1, 2}, {1, 2, 3, 4, 5}, {2, 3, 4, 5}, {3, 4, 5}};
    static final GearTier[] RARITIES = {GearTier.UNCOMMON, GearTier.RARE, GearTier.EPIC, GearTier.HEROIC};

    public static final List<BoxKind> ALL = all();

    private static List<BoxKind> all() {
        List<BoxKind> out = new ArrayList<>();
        for (String t : TITANS) {
            out.add(new BoxKind("box_" + t, Type.TITAN, t, GearTier.RARE, 0));
        }
        for (int i = 0; i < RARITIES.length; i++) {
            for (int b : BANDS[i]) {
                out.add(new BoxKind("box_" + RARITIES[i].key + "_" + b, Type.RARITY, null, RARITIES[i], b));
            }
        }
        out.add(new BoxKind("box_artifact", Type.ARTIFACT, null, GearTier.ARTIFACT, 0));
        out.add(new BoxKind("box_tower", Type.TOWER, null, GearTier.HEROIC, 0));
        return List.copyOf(out);
    }

    @Nullable
    public static BoxKind byId(String id) {
        for (BoxKind k : ALL) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    @Nullable
    public static BoxKind titan(String titan) {
        return byId("box_" + titan);
    }

    // 階級と帯から階級の箱 (その組の箱が無ければ、帯を近い方へ寄せる)
    @Nullable
    public static BoxKind rarity(GearTier tier, int band) {
        for (int i = 0; i < RARITIES.length; i++) {
            if (RARITIES[i] == tier) {
                int[] bs = BANDS[i];
                int best = bs[0];
                for (int b : bs) {
                    if (Math.abs(b - band) < Math.abs(best - band)) {
                        best = b;
                    }
                }
                return byId("box_" + tier.key + "_" + best);
            }
        }
        return null;
    }

    // 形 (立体と絵) の名前: 巨神の箱と金の箱はそれぞれ、階級の箱は帯にかかわらず階級ごとに一つ
    public String look() {
        return type == Type.RARITY ? "box_" + tier.key : id;
    }

    // 帯の Lv の幅 (帯 I = 1〜10 …)
    public int minLevel() {
        return band <= 0 ? 1 : (band - 1) * 10 + 1;
    }

    public int maxLevel() {
        return band <= 0 ? 50 : band * 10;
    }
}
