package jp.revati.skyabyss.titan.storm.back;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout.Area;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// 雷翼の背の「小さな建築の間違い」探し (自己テスト "storm scan")。BuildAudit (階段・戸・はしご) では見えない、ブロックの並びの穴を調べる:
//  - 壁の穴: 部屋 (Area) の中の空気が、外の空 (体の外から空気を伝って届く所) とつながっている
//  - 浮いたブロック: 小さな塊で、体につながっていない
//  - 埋まった半端ブロック: 階段・半ブロック・灯り・避雷針など、全方向を丸ごとのブロックにふさがれている
// 結果はログの "STORM SCAN" 行
public final class StormBuildScan {
    private static final int X1 = -335, X2 = 335, Y1 = 100, Y2 = 230, Z1 = -190, Z2 = 200;
    private static final int NX = X2 - X1 + 1, NY = Y2 - Y1 + 1, NZ = Z2 - Z1 + 1;

    private StormBuildScan() {
    }

    private static int idx(int x, int y, int z) {
        return ((x - X1) * NY + (y - Y1)) * NZ + (z - Z1);
    }

    private static int xOf(int i) {
        return i / (NZ * NY) + X1;
    }

    private static int yOf(int i) {
        return (i / NZ) % NY + Y1;
    }

    private static int zOf(int i) {
        return i % NZ + Z1;
    }

    private static boolean inBox(int x, int y, int z) {
        return x >= X1 && x <= X2 && y >= Y1 && y <= Y2 && z >= Z1 && z <= Z2;
    }

    private static int[] grow(int[] a, int need) {
        return need < a.length ? a : Arrays.copyOf(a, a.length * 2);
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.getLevel(StormTravel.STORM_BACK);
        if (level == null) {
            SkyAbyss.LOGGER.error("SELFTEST storm scan FAILED: no storm back level");
            return;
        }
        StormBackBuilder.ensureBuilt(level);
        long t0 = System.currentTimeMillis();
        int total = NX * NY * NZ;
        BitSet air = new BitSet(total);
        BitSet solid = new BitSet(total);
        BitSet fullBlock = new BitSet(total);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = X1; x <= X2; x++) {
            for (int z = Z1; z <= Z2; z++) {
                for (int y = Y1; y <= Y2; y++) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    int i = idx(x, y, z);
                    if (s.isAir()) {
                        air.set(i);
                        continue;
                    }
                    solid.set(i);
                    var shape = s.getCollisionShape(level, m);
                    if (shape.isEmpty()) {
                        air.set(i);
                    }
                    if (Block.isShapeFullBlock(shape)) {
                        fullBlock.set(i);
                    }
                }
            }
        }
        // 外の空: 箱の外周から、通れる所を伝って届く所
        BitSet outside = new BitSet(total);
        int[] stack = new int[1 << 20];
        int sp = 0;
        for (int x = X1; x <= X2; x++) {
            for (int y = Y1; y <= Y2; y++) {
                for (int z = Z1; z <= Z2; z++) {
                    boolean edge = x == X1 || x == X2 || y == Y2 || z == Z1 || z == Z2;
                    if (!edge) {
                        continue;
                    }
                    int i = idx(x, y, z);
                    if (air.get(i) && !outside.get(i)) {
                        outside.set(i);
                        stack = grow(stack, sp + 1);
                        stack[sp++] = i;
                    }
                }
            }
        }
        while (sp > 0) {
            int i = stack[--sp];
            int x = xOf(i);
            int y = yOf(i);
            int z = zOf(i);
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX();
                int ny = y + d.getStepY();
                int nz = z + d.getStepZ();
                if (!inBox(nx, ny, nz)) {
                    continue;
                }
                int j = idx(nx, ny, nz);
                if (air.get(j) && !outside.get(j)) {
                    outside.set(j);
                    stack = grow(stack, sp + 1);
                    stack[sp++] = j;
                }
            }
        }
        List<String> out = new ArrayList<>();
        // 2. 浮いたブロックの塊 (2.5 万個未満で、体 (大きな塊) につながらない)
        BitSet seen = new BitSet(total);
        int floating = 0;
        int designFloating = 0;
        List<String> floatList = new ArrayList<>();
        for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
            if (seen.get(i)) {
                continue;
            }
            int size = 0;
            int first = i;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int top = 0;
            stack = grow(stack, 1);
            stack[top++] = i;
            seen.set(i);
            while (top > 0) {
                int c = stack[--top];
                size++;
                int x = xOf(c);
                int y = yOf(c);
                int z = zOf(c);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                for (Direction d : Direction.values()) {
                    int nx = x + d.getStepX();
                    int ny = y + d.getStepY();
                    int nz = z + d.getStepZ();
                    if (!inBox(nx, ny, nz)) {
                        continue;
                    }
                    int j = idx(nx, ny, nz);
                    if (solid.get(j) && !seen.get(j)) {
                        seen.set(j);
                        stack = grow(stack, top + 1);
                        stack[top++] = j;
                    }
                }
            }
            if (size < 25000) {
                boolean inRibHall = minX >= StormBackLayout.RIB_X1 - 1 && maxX <= StormBackLayout.RIB_X2 + 1 && minZ >= StormBackLayout.BONE_Z1 - 1 && maxZ <= StormBackLayout.BONE_Z2 + 1
                        && minY >= StormBackLayout.BONE_FLOOR && maxY <= StormBackLayout.RIB_TOP_Y + 4;
                m.set(xOf(first), yOf(first), zOf(first));
                String name = level.getBlockState(m).getBlock().getDescriptionId().replace("block.minecraft.", "");
                if (inRibHall) {
                    designFloating += size;
                    continue;
                }
                floating += size;
                StringBuilder col = new StringBuilder();
                int cx = (minX + maxX) / 2;
                int cz = (minZ + maxZ) / 2;
                for (int yy = minY + 1; yy >= minY - 6; yy--) {
                    m.set(cx, yy, cz);
                    col.append(level.getBlockState(m).getBlock().getDescriptionId().replace("block.minecraft.", "").replace("block.skyabyss.", "s:")).append(yy == minY ? "*," : ",");
                }
                if (floatList.size() < 20) {
                    StringBuilder sl = new StringBuilder("SLICE x" + (cx - 9) + ".." + (cx + 9) + " z=" + cz + " y" + (minY + 4) + "->" + (minY - 8) + "\n");
                    for (int yy = minY + 4; yy >= minY - 8; yy--) {
                        for (int xx = cx - 9; xx <= cx + 9; xx++) {
                            m.set(xx, yy, cz);
                            BlockState bs = level.getBlockState(m);
                            sl.append(bs.isAir() ? '.' : Block.isShapeFullBlock(bs.getCollisionShape(level, m)) ? '#' : 'o');
                        }
                        sl.append('\n');
                    }
                    SkyAbyss.LOGGER.warn("STORM SCAN {}", sl);
                }
                floatList.add(size + " " + name + " [" + minX + ".." + maxX + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ + "] col@" + cx + "," + cz + " " + col);
            }
        }
        if (!floatList.isEmpty()) {
            out.add("FLOATING " + floatList.size() + " pieces (" + floating + " blocks): " + floatList);
        }
        // 3. 埋まった半端ブロック
        int buried = 0;
        List<String> buriedList = new ArrayList<>();
        Map<String, Integer> oddType = new TreeMap<>();
        for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
            if (fullBlock.get(i)) {
                continue;
            }
            int x = xOf(i);
            int y = yOf(i);
            int z = zOf(i);
            boolean allFull = true;
            for (Direction d : Direction.values()) {
                int nx = x + d.getStepX();
                int ny = y + d.getStepY();
                int nz = z + d.getStepZ();
                if (!inBox(nx, ny, nz) || !fullBlock.get(idx(nx, ny, nz))) {
                    allFull = false;
                    break;
                }
            }
            if (allFull) {
                buried++;
                m.set(x, y, z);
                String name = level.getBlockState(m).getBlock().getDescriptionId();
                oddType.merge(name, 1, Integer::sum);
                if (buriedList.size() < 14) {
                    buriedList.add(name + "@" + x + "," + y + "," + z);
                }
            }
        }
        if (buried > 0) {
            out.add("BURIED " + buried + " partial blocks " + oddType + " e.g. " + buriedList);
        }
        for (String s : out) {
            SkyAbyss.LOGGER.warn("STORM SCAN {}", s);
        }
        SkyAbyss.LOGGER.info("SELFTEST storm scan: floating {} (rib hall design pieces {} excluded) buried {} in {} ms", floating, designFloating, buried, System.currentTimeMillis() - t0);
    }
}
