package jp.revati.skyabyss.opening.site;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

// 巨大な足跡の「中にいるか」を、見える足の形そのもので調べる (中心からの円ではなく)。
// 部品 (footprint / footprint_end) の掘る・盛る列 (relief) に、余白 margin ブロックを足した範囲を、向きと左右反転を戻して引く。
// 足跡は長さ 50・幅 45 だが、部品の原点は踵寄りにずれていて (爪先まで 37、踵まで 20)、原点からの円では爪先の中が判定の外に出る
public final class FootprintShape {
    private static final Map<String, Set<Long>> CACHE = new HashMap<>();

    private FootprintShape() {
    }

    // pos (水平) が、足跡 entry の形 + margin の中か
    public static boolean contains(SiteData data, SiteData.Entry entry, BlockPos pos, int margin) {
        return contains(FootprintBuilder.blueprintName(data, entry), entry.pos, entry.yaw, pos, margin);
    }

    public static boolean contains(String blueprint, BlockPos origin, int yaw, BlockPos pos, int margin) {
        Set<Long> cells = cells(blueprint, margin);
        // 部品の座標へ戻す: 回す (時計回り rot 回) の逆 → 左右反転の逆
        int x = pos.getX() - origin.getX();
        int z = pos.getZ() - origin.getZ();
        for (int i = 0; i < SiteFinder.rot(yaw); i++) {
            int nx = z;
            int nz = -x;
            x = nx;
            z = nz;
        }
        if (SiteFinder.mirror(yaw)) {
            x = -x;
        }
        return cells.contains(key(x, z));
    }

    private static synchronized Set<Long> cells(String blueprint, int margin) {
        return CACHE.computeIfAbsent(blueprint + "/" + margin, k -> {
            Blueprint bp = Blueprint.get(blueprint);
            Set<Long> out = new HashSet<>();
            for (int[] r : bp.relief) {
                // 掘る・盛る列 (kind 0, 1)。立てる物 (kind 2) は含めない
                if (r[2] > 1) {
                    continue;
                }
                for (int dx = -margin; dx <= margin; dx++) {
                    for (int dz = -margin; dz <= margin; dz++) {
                        if (dx * dx + dz * dz <= margin * margin + 1) {
                            out.add(key(r[0] + dx, r[1] + dz));
                        }
                    }
                }
            }
            return out;
        });
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
