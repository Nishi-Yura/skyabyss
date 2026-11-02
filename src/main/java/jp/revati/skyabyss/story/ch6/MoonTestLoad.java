package jp.revati.skyabyss.story.ch6;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

// 月の自己テストの先の段 (docs/design/46)。月を流し込み、谷・崖・野・淵・核のテストが生き物を置く所の塊を先に読み込んでおく。
// 塊の生き物の区画は読み込みに数 tick かかり、読み込みきる前に加えた生き物は、同じ tick の内に探しても見つからない
// (主を呼び直して二体になる・一瞬止めが空振る、などの揺れ)。最後の月のテストのあとに unload で外す
public final class MoonTestLoad {
    private static final List<int[]> FORCED = new ArrayList<>();

    private MoonTestLoad() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        if (moon == null) {
            return;
        }
        moon.getChunk(MoonWorld.ARRIVAL);
        MoonWorld.build(moon);
        List<BlockPos> at = new ArrayList<>();
        for (String k : new String[] {"camp", "arena_center", "valley_gate"}) {
            at.add(jp.revati.skyabyss.story.ch6.valley.MoonValley.mark(k));
        }
        for (String k : new String[] {"camp", "arena_center", "plate_w"}) {
            at.add(jp.revati.skyabyss.story.ch6.cliff.MoonCliff.mark(k));
        }
        for (String k : new String[] {"camp", "arena_center", "shard_pond"}) {
            at.add(jp.revati.skyabyss.story.ch6.field.MoonField.mark(k));
        }
        for (String k : new String[] {"camp", "arena_center", "door_entry", "leap_entry", "hall_floor"}) {
            at.add(jp.revati.skyabyss.story.ch6.rift.MoonRift.mark(k));
        }
        for (String k : new String[] {"core_gate", "arena_center"}) {
            at.add(jp.revati.skyabyss.story.ch6.core.MoonCore.mark(k));
        }
        for (BlockPos m : at) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                    FORCED.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                }
            }
        }
    }

    public static void unload(MinecraftServer server) {
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        if (moon != null) {
            for (int[] c : FORCED) {
                moon.setChunkForced(c[0], c[1], false);
            }
        }
        FORCED.clear();
    }
}
