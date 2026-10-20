package jp.revati.skyabyss.titan.ember.back;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

// 建てた余燼の背を文字の図にして書き出す開発用 (環境変数 SKYABYSS_EMBER_DUMP=<出力>、指定は <出力>.spec。
// 行: `plan y x1 x2 z1 z2` (平面。行 = z、列 = x)、`secx x y1 y2 z1 z2` (行 = y 上から、列 = z)、`secz z y1 y2 x1 x2` (行 = y、列 = x))。
// 字: 空気 ' '、固い '#'、階段 '/'、半ブロック '_'、溶岩 'L'、光る (光度 8 以上) 'O'、看板・碑 'T'、宝箱 'C'、封印の扉 'G'、その他 '+'。
public final class EmberDump {
    private EmberDump() {
    }

    private static char ch(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.isAir()) {
            return ' ';
        }
        if (s.is(Blocks.LAVA)) {
            return 'L';
        }
        if (s.is(jp.revati.skyabyss.registry.ModBlocks.RELIC_CACHE.get())) {
            return 'C';
        }
        if (s.getBlock() instanceof jp.revati.skyabyss.tablet.TabletBlock) {
            return 'T';
        }
        if (s.is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get())) {
            return 'G';
        }
        if (s.getLightEmission() >= 8) {
            return 'O';
        }
        if (s.getBlock() instanceof StairBlock) {
            return '/';
        }
        if (s.getBlock() instanceof SlabBlock) {
            return '_';
        }
        if (s.isCollisionShapeFullBlock(l, p)) {
            return '#';
        }
        return '+';
    }

    public static void run(ServerLevel level) {
        String out = System.getenv("SKYABYSS_EMBER_DUMP");
        if (out == null || out.isEmpty()) {
            return;
        }
        try {
            List<String> spec = Files.readAllLines(Path.of(out + ".spec"));
            List<String> text = new ArrayList<>();
            for (String line : spec) {
                String[] t = line.trim().split("\\s+");
                if (t.length < 6) {
                    continue;
                }
                int[] a = new int[t.length - 1];
                for (int i = 1; i < t.length; i++) {
                    a[i - 1] = Integer.parseInt(t[i]);
                }
                text.add("== " + line.trim());
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                switch (t[0]) {
                    case "plan" -> {
                        text.add("   x from " + a[1]);
                        for (int z = a[3]; z <= a[4]; z++) {
                            StringBuilder sb = new StringBuilder(String.format("%4d ", z));
                            for (int x = a[1]; x <= a[2]; x++) {
                                sb.append(ch(level, m.set(x, a[0], z)));
                            }
                            text.add(sb.toString());
                        }
                    }
                    case "secx" -> {
                        text.add("   z from " + a[3]);
                        for (int y = a[2]; y >= a[1]; y--) {
                            StringBuilder sb = new StringBuilder(String.format("%4d ", y));
                            for (int z = a[3]; z <= a[4]; z++) {
                                sb.append(ch(level, m.set(a[0], y, z)));
                            }
                            text.add(sb.toString());
                        }
                    }
                    case "secz" -> {
                        text.add("   x from " + a[3]);
                        for (int y = a[2]; y >= a[1]; y--) {
                            StringBuilder sb = new StringBuilder(String.format("%4d ", y));
                            for (int x = a[3]; x <= a[4]; x++) {
                                sb.append(ch(level, m.set(x, y, a[0])));
                            }
                            text.add(sb.toString());
                        }
                    }
                    default -> {
                    }
                }
            }
            Files.write(Path.of(out), text, StandardCharsets.UTF_8);
            SkyAbyss.LOGGER.info("Ember dump written to {}", out);
        } catch (IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("Ember dump failed", e);
        }
    }
}
