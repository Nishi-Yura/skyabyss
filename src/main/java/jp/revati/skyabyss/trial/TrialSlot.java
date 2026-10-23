package jp.revati.skyabyss.trial;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import jp.revati.skyabyss.titan.group.BackGroupData;
import net.minecraft.server.level.ServerLevel;

// 順路の区画 (docs/design/32 の 5 章)。一つの順路に一つの番号 (1〜MAX) を割り当て、グループの記録 keep.trial.slot に書く。
// 番号は小さい空きから。順路が終わると返し、REUSE_AFTER のあいだは同じ番号を別の順路に渡さない (空きがなければ渡す)。
// 区画の場所は背の次元の体から遠い所 (x = ORIGIN_X + 番号 × STRIDE、z = ORIGIN_Z)。部屋と中ボスの間は RaidRun がそこへ建てる。
// 0 番の枠は心臓の間 (InstanceArenas)、33 番の枠は自己テストが使う
public final class TrialSlot {
    private TrialSlot() {
    }

    public static final String SLOT = "keep.trial.slot";
    public static final int MAX = 32;
    public static final int REUSE_AFTER = 20 * 60 * 3;
    public static final int ORIGIN_X = 4096;
    public static final int ORIGIN_Z = 4096;
    public static final int STRIDE = 1024;

    // 返した番号 → 返した時刻 (背ごと)
    private static final Map<String, Map<Integer, Long>> RELEASED = new HashMap<>();

    public static int of(BackGroupData.Record r) {
        return Math.max(0, r.getInt(SLOT));
    }

    private static String dim(ServerLevel back) {
        return back.dimension().location().toString();
    }

    // 番号 n の区画のずれ
    public static TrialFrame frame(int n) {
        return n <= 0 ? TrialFrame.ZERO : new TrialFrame(ORIGIN_X + n * STRIDE, 0, ORIGIN_Z);
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
            Map<Integer, Long> rel = RELEASED.get(dim(back));
            if (rel != null) {
                rel.remove(pick);
            }
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

    // 体の外の、閉じた部屋の並ぶ所 (区画 1〜32 の枠と、心臓の間のある 0 番の枠、自己テストの 33 番の枠) か。
    // 背の決まり (雲より下へ落ちた・島から流された・灰の原へ出た) で人を到着の台へ戻さない所
    public static boolean inInstance(double x, double z) {
        int n = (int) Math.floor((x - ORIGIN_X + STRIDE / 2.0) / STRIDE);
        return n >= 0 && n <= MAX + 1 && Math.abs(z - ORIGIN_Z) <= STRIDE / 2.0;
    }

    public static void clearAll() {
        RELEASED.clear();
    }
}
