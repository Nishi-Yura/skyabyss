package jp.revati.skyabyss.titan.dungeon;

import java.nio.file.Files;
import java.nio.file.Path;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 開発用: 岩鯨の背の平面図を文字で書き出す (SKYABYSS_WHALE_DUMP=1 のとき、自己テスト "whale scale" が whale_dump.txt を書く)。
// 建てた形を、画面を見ずに確かめるための道具
final class WhaleDump {
    private WhaleDump() {
    }

    static char glyph(BlockState s) {
        if (s.isAir()) {
            return ' ';
        }
        if (s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
            return '#';
        }
        if (s.is(Blocks.POLISHED_DEEPSLATE)) {
            return 'p';
        }
        if (s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.CRACKED_DEEPSLATE_TILES)) {
            return 't';
        }
        if (s.is(Blocks.CHISELED_DEEPSLATE)) {
            return 'C';
        }
        if (s.is(Blocks.BONE_BLOCK)) {
            return 'B';
        }
        if (s.is(ModBlocks.SEALED_GATE.get())) {
            return 'G';
        }
        if (s.is(ModBlocks.CRUMBLING_BONE.get())) {
            return 'x';
        }
        if (s.is(Blocks.LAVA)) {
            return '!';
        }
        if (s.is(Blocks.WATER)) {
            return '~';
        }
        if (s.is(Blocks.GLASS)) {
            return 'g';
        }
        if (s.is(Blocks.CHAIN)) {
            return 'c';
        }
        if (s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.LANTERN) || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.SHROOMLIGHT) || s.is(Blocks.GLOWSTONE)) {
            return 'L';
        }
        if (s.is(Blocks.LADDER)) {
            return 'H';
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
            return '>';
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
            return '_';
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.WallBlock || s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
            return '|';
        }
        if (s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
            return 'b';
        }
        return '?';
    }

    // 体のままの石は ':'、体の外の空気は '.'
    static char cell(BlockState s, int x, int y, int z) {
        BlockState body = WhaleShape.at(x, y, z);
        if (s.isAir()) {
            return body == null ? '.' : ' ';
        }
        return body != null && body.equals(s) ? ':' : glyph(s);
    }

    // 壁の輪の空気のうち、わざと開けてある所 (別の部屋の内側へ通じる戸口・胸びれのトンネル・書庫の壁龕)
    static boolean allowedOpening(net.minecraft.core.BlockPos p, TitanBackLayout.Box self) {
        // 別の部屋の内側か、そのすぐ外の壁 (部屋どうしの共有の壁に開けた戸口)
        for (TitanBackLayout.Box o : TitanBackAudit.ROOMS) {
            if (o != self && p.getX() >= o.x1() - 1 && p.getX() <= o.x2() + 1 && p.getZ() >= o.z1() - 1 && p.getZ() <= o.z2() + 1
                    && p.getY() > o.floorY() && p.getY() < o.ceilingY()) {
                return true;
            }
        }
        // 大門の前庭 (BossGate の掘り込み。心臓の回廊の先・番兵長の広間の前室)
        if ((p.getX() >= -14 && p.getX() <= 14 && p.getZ() >= -122 && p.getZ() <= -99) || (p.getX() >= 33 && p.getX() <= 40 && p.getZ() >= -109 && p.getZ() <= -97)) {
            return true;
        }
        // 入口の通路の東の壁から試練の階へ上る階段の戸口 (WhaleDeck。docs/design/29)
        if (WhaleDeck.placed() && p.getX() >= WhaleDeck.STAIR_DOOR_X && p.getX() <= WhaleDeck.LAND_X && p.getZ() >= WhaleDeck.STAIR_Z0 - 1
                && p.getZ() <= WhaleDeck.STAIR_Z1 + 1) {
            return true;
        }
        // 祠から下りる階段
        if (p.getX() >= -4 && p.getX() <= 4 && p.getZ() >= 15 && p.getZ() <= TitanBackLayout.SHRINE_Z) {
            return true;
        }
        if (self == TitanBackLayout.LIBRARY && p.getY() >= TitanBackLayout.FLOOR + 2 && p.getY() <= TitanBackLayout.FLOOR + 4) {
            return true;
        }
        // 胸びれのトンネル (z -64〜-60)
        return p.getZ() >= -64 && p.getZ() <= -60 && (p.getX() == TitanBackLayout.W1.x1() - 1 || p.getX() == TitanBackLayout.CE1.x2() + 1);
    }

    // y の層の x1〜x2 × z1〜z2 (z が行、x が列)。体のあるのに空気の所は ' '、体のない所は '.'
    static void plan(StringBuilder out, ServerLevel level, String title, int y, int x1, int x2, int z1, int z2) {
        out.append("== ").append(title).append(" y=").append(y).append(" x ").append(x1).append("..").append(x2).append(" z ").append(z1).append("..").append(z2).append('\n');
        for (int z = z1; z <= z2; z++) {
            StringBuilder row = new StringBuilder(String.format("%5d ", z));
            for (int x = x1; x <= x2; x++) {
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                row.append(cell(s, x, y, z));
            }
            out.append(row).append('\n');
        }
    }

    // 断面: z 一定で x が列、y が行 (上が高い)
    static void sectionZ(StringBuilder out, ServerLevel level, String title, int z, int x1, int x2, int y1, int y2) {
        out.append("== ").append(title).append(" z=").append(z).append(" x ").append(x1).append("..").append(x2).append(" y ").append(y1).append("..").append(y2).append('\n');
        for (int y = y2; y >= y1; y--) {
            StringBuilder row = new StringBuilder(String.format("%5d ", y));
            for (int x = x1; x <= x2; x++) {
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                row.append(cell(s, x, y, z));
            }
            out.append(row).append('\n');
        }
    }

    // 断面: x 一定で z が列、y が行
    static void sectionX(StringBuilder out, ServerLevel level, String title, int x, int z1, int z2, int y1, int y2) {
        out.append("== ").append(title).append(" x=").append(x).append(" z ").append(z1).append("..").append(z2).append(" y ").append(y1).append("..").append(y2).append('\n');
        for (int y = y2; y >= y1; y--) {
            StringBuilder row = new StringBuilder(String.format("%5d ", y));
            for (int z = z1; z <= z2; z++) {
                BlockState s = level.getBlockState(new BlockPos(x, y, z));
                row.append(cell(s, x, y, z));
            }
            out.append(row).append('\n');
        }
    }

    // 体の幅 (y の高さの x の範囲) の表。配置を決めるための目安 (世界は要らない)
    static void widths(StringBuilder out, int y) {
        out.append("== body extent at y=").append(y).append(" (z: xmin..xmax)\n");
        for (int z = -170; z <= 330; z += 10) {
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (int x = -140; x <= 140; x++) {
                if (WhaleShape.at(x, y, z) != null) {
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            out.append(String.format("z %4d: %4d..%4d%n", z, lo, hi));
        }
    }

    // 環境変数 SKYABYSS_WHALE_DUMP (出力のパス) が立っているとき、<パス>.spec の各行 (plan|sx|sz|widths ...) の図を書く。
    //   plan y x1 x2 z1 z2 / secz z x1 x2 y1 y2 / secx x z1 z2 y1 y2 / widths y
    static void write(ServerLevel level) {
        String path = System.getenv("SKYABYSS_WHALE_DUMP");
        if (path == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append("fin tips: left ").append(WingsBuilder.finTip(-1)).append(" cache ").append(WingsBuilder.cachePos(-1))
                .append(" / right ").append(WingsBuilder.finTip(1)).append(" cache ").append(WingsBuilder.cachePos(1)).append((char) 10);
        try {
            for (String line : Files.readAllLines(Path.of(path + ".spec"))) {
                String[] t = line.trim().split("\s+");
                if (t.length == 0 || t[0].isEmpty() || t[0].startsWith("#")) {
                    continue;
                }
                int[] n = new int[t.length - 1];
                for (int i = 1; i < t.length; i++) {
                    n[i - 1] = Integer.parseInt(t[i]);
                }
                switch (t[0]) {
                    case "plan" -> plan(out, level, line, n[0], n[1], n[2], n[3], n[4]);
                    case "secz" -> sectionZ(out, level, line, n[0], n[1], n[2], n[3], n[4]);
                    case "secx" -> sectionX(out, level, line, n[0], n[1], n[2], n[3], n[4]);
                    case "light" -> {
                        out.append("== light y=").append(n[0]).append(" x ").append(n[1]).append("..").append(n[2]).append(" z ").append(n[3]).append("..").append(n[4]).append((char) 10);
                        for (int z = n[3]; z <= n[4]; z++) {
                            StringBuilder row = new StringBuilder(String.format("%5d ", z));
                            for (int x = n[1]; x <= n[2]; x++) {
                                int l = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(x, n[0], z));
                                row.append(Integer.toHexString(l));
                            }
                            out.append(row).append((char) 10);
                        }
                    }
                    case "widths" -> widths(out, n[0]);
                    default -> out.append("?? ").append(line).append((char) 10);
                }
            }
            Files.writeString(Path.of(path), out.toString());
        } catch (Exception e) {
            SkyAbyss.LOGGER.warn("whale dump failed: {}", e.toString());
        }
    }
}
