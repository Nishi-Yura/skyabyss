package jp.revati.skyabyss.trial;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import jp.revati.skyabyss.titan.group.BackGroupData;
import net.minecraft.server.level.ServerLevel;

// 順路の区画 (docs/design/32 の 5 章)。一つの順路に一つの番号 (1〜MAX) を割り当て、グループの記録 keep.trial.slot に書く。
// 番号は小さい空きから。順路が終わると返し、REUSE_AFTER のあいだは同じ番号を別の順路に渡さない (終わった順路の後片付けの間)。
// 今の作り: セルの部屋は背の中に一組だけあり、区画ごとの写しは建てない。どのセルもグループごとの見かけの上書き (GroupOverlays)・
// 敵の持ち主 (GroupOwned)・戸 (GroupGates) で動くので、同じ部屋に別の区画のグループが同時にいても互いに見えず、待ちも出ない。
// 番号は、区画ごとに建てる形 (背の次元の遠くにセルを写す) へ移るときの置き場の鍵になる
public final class TrialSlot {
    private TrialSlot() {
    }

    public static final String SLOT = "keep.trial.slot";
    public static final int MAX = 32;
    public static final int REUSE_AFTER = 20 * 60 * 3;

    // 返した番号 → 返した時刻 (背ごと)
    private static final Map<String, Map<Integer, Long>> RELEASED = new HashMap<>();

    public static int of(BackGroupData.Record r) {
        return Math.max(0, r.getInt(SLOT));
    }

    private static String dim(ServerLevel back) {
        return back.dimension().location().toString();
    }

    // いま使っている番号
    public static TreeSet<Integer> inUse(ServerLevel back) {
        TreeSet<Integer> used = new TreeSet<>();
        BackGroupData data = BackGroupData.get(back);
        for (UUID g : data.ids()) {
            BackGroupData.Record r = data.peek(g);
            if (r != null && of(r) > 0) {
                used.add(of(r));
            }
        }
        return used;
    }

    // 番号を割り当てる (もう持っていればそのまま)。空きがなければ 0
    public static int assign(ServerLevel back, BackGroupData.Record r) {
        if (of(r) > 0) {
            return of(r);
        }
        TreeSet<Integer> used = inUse(back);
        Map<Integer, Long> released = RELEASED.getOrDefault(dim(back), Map.of());
        long now = back.getGameTime();
        int pick = 0;
        for (int n = 1; n <= MAX; n++) {
            Long at = released.get(n);
            if (!used.contains(n) && (at == null || now - at >= REUSE_AFTER || now < at)) {
                pick = n;
                break;
            }
        }
        if (pick == 0) {
            // 後片付けの間の番号しか空いていなければ、それを使う (待たせない)
            for (int n = 1; n <= MAX; n++) {
                if (!used.contains(n)) {
                    pick = n;
                    break;
                }
            }
        }
        if (pick > 0) {
            r.putInt(SLOT, pick);
            RELEASED.getOrDefault(dim(back), new HashMap<>()).remove(pick);
        }
        return pick;
    }

    // 番号を返す
    public static void release(ServerLevel back, BackGroupData.Record r) {
        int n = of(r);
        if (n <= 0) {
            return;
        }
        r.remove(SLOT);
        RELEASED.computeIfAbsent(dim(back), k -> new HashMap<>()).put(n, back.getGameTime());
    }

    public static void clearAll() {
        RELEASED.clear();
    }
}
