package jp.revati.skyabyss.guide.nav;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

// 道の点の詰め方: 先頭の座標 + 次の点ごとに 1 バイト (dx,dz は -1..1、dy は -3..1 の 45 通り)
public final class TrailCodec {
    public static final int MAX_NODES = 256;

    private TrailCodec() {
    }

    public static boolean encodable(List<BlockPos> nodes) {
        for (int i = 1; i < nodes.size(); i++) {
            BlockPos a = nodes.get(i - 1);
            BlockPos b = nodes.get(i);
            int dx = b.getX() - a.getX();
            int dy = b.getY() - a.getY();
            int dz = b.getZ() - a.getZ();
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1 || dy < -3 || dy > 1) {
                return false;
            }
        }
        return true;
    }

    public static byte[] steps(List<BlockPos> nodes) {
        byte[] out = new byte[Math.max(0, nodes.size() - 1)];
        for (int i = 1; i < nodes.size(); i++) {
            BlockPos a = nodes.get(i - 1);
            BlockPos b = nodes.get(i);
            out[i - 1] = (byte) (((b.getX() - a.getX() + 1) * 3 + (b.getZ() - a.getZ() + 1)) * 5 + (b.getY() - a.getY() + 3));
        }
        return out;
    }

    public static List<BlockPos> decode(BlockPos origin, byte[] steps) {
        List<BlockPos> out = new ArrayList<>(steps.length + 1);
        out.add(origin);
        BlockPos cur = origin;
        for (byte s : steps) {
            int v = Math.floorMod((int) s, 45);
            int dy = v % 5 - 3;
            int dz = (v / 5) % 3 - 1;
            int dx = v / 15 - 1;
            cur = cur.offset(dx, dy, dz);
            out.add(cur);
        }
        return out;
    }
}
