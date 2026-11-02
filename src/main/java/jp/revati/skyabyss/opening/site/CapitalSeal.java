package jp.revati.skyabyss.opening.site;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.TownGuide;

// 都の空き家 3 軒 (旧厩舎・地図屋・空見の館) は入れない (2026-11-02 の声「完全に入れなくしてもいい。碑もなくしていい」。docs/design/35 の 1 章)。
// 今の設計図は戸を壁で塞ぎ、碑を外してある (tools/capital/seal_vacant.py)。前の版で建てた都は、設計図の印 seal_<名>_<番号> の所を
// 今の設計図のブロックで置き直す (戸 → 壁、碑 → 空気。前に開いた空見の館の戸口も塞ぐ)。世界で一度。印のチャンクが読まれていなければ次の見回りで。
public final class CapitalSeal {
    static final String MARK = "seal_";
    static final String DONE = "sealed_vacant";

    private CapitalSeal() {
    }

    // 5 秒ごと (SiteGuard の見回りから)
    public static void keep(ServerLevel level, SiteData.Entry e) {
        if (e.extra(DONE) != null) {
            return;
        }
        Map<BlockPos, BlockState> cells = cells(level);
        if (cells.isEmpty()) {
            return;
        }
        for (BlockPos at : cells.keySet()) {
            if (!level.isLoaded(at)) {
                return;
            }
        }
        int changed = 0;
        for (Map.Entry<BlockPos, BlockState> c : cells.entrySet()) {
            if (!level.getBlockState(c.getKey()).equals(c.getValue())) {
                level.setBlock(c.getKey(), c.getValue(), 2 | 16);
                changed++;
            }
        }
        e.extra(DONE, e.pos);
        SiteData.get(level).setDirty();
        if (changed > 0) {
            SkyAbyss.LOGGER.info("Capital: sealed the vacant houses ({} block(s))", changed);
        }
    }

    // 印の世界の座標 → 今の設計図のその所のブロック (向きは建てた都に合わせる)
    static Map<BlockPos, BlockState> cells(ServerLevel level) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        Blueprint bp = TownGuide.blueprint();
        TownGuide.Frame f = TownGuide.frame(level.getServer(), bp);
        if (f == null) {
            return out;
        }
        Map<Long, String> want = new java.util.HashMap<>();
        for (Map.Entry<String, int[]> m : bp.marks.entrySet()) {
            if (m.getKey().startsWith(MARK)) {
                int[] v = m.getValue();
                want.put(BlockPos.asLong(v[0], v[1], v[2]), m.getKey());
            }
        }
        for (Blueprint.Cell c : bp.cells) {
            if (want.containsKey(BlockPos.asLong(c.x(), c.y(), c.z()))) {
                out.put(Blueprint.transform(c.x(), c.y() + 1, c.z(), f.rot(), f.mirror(), f.origin()), Blueprint.transform(c.state(), f.rot(), f.mirror()));
            }
        }
        return out;
    }
}
