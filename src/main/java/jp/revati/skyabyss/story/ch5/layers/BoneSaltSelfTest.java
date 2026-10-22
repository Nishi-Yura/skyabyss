package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// SELFTEST abyss layers bone salt: 骨の橋・巣の門・塩の潮 (docs/design/36 の 8 章)。
//   印と設計図、橋の下が支えられているか、巣の戸の封じと開け、潮の周期 (決まっている・同じ時間は同じ)、
//   水の列を合わせる、浮きは満ち潮と重さの術だけ、高い棚は満ち潮の水面より上
public final class BoneSaltSelfTest {
    private static final int HOLD = 3;   // 読み込んでおく塊の半径 (中心の塊から)

    private BoneSaltSelfTest() {
    }

    // 湖・橋・巣の塊を先に読み込み、建てておく
    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
        hold(level, true);
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        try {
            check(f, level != null, "the abyss dimension is missing");
            if (level == null) {
                report(f);
                return;
            }
            for (String m : new String[] {"bridge_bone_a", "bridge_bone_b", "den_bone", "shortcut_bone", "bridge_salt_a",
                    "bridge_salt_b", "tide_salt_low", "tide_salt_high", "shelf_salt", "lake_salt"}) {
                check(f, AbyssBuild.mark(m) != null, m + " mark is missing");
            }
            BlockPos a = AbyssBuild.mark("bridge_bone_a");
            BlockPos b = AbyssBuild.mark("bridge_bone_b");
            if (a != null && b != null) {
                // 橋: 端の下が支えられていて、途中も (橋の線に沿って) 床がある
                check(f, !level.getBlockState(a.below()).isAir(), "the bone bridge start is not supported");
                check(f, !level.getBlockState(b.below()).isAir(), "the bone bridge end is not supported");
                int steps = 12;
                for (int i = 1; i < steps; i++) {
                    int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * i / (double) steps);
                    int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * i / (double) steps);
                    // 橋は端の高さから向こうの端へ下る: この所の高さに合わせて床を探す
                    int want = (int) Math.round(a.getY() + (b.getY() - a.getY()) * i / (double) steps);
                    boolean deck = false;
                    for (int y = want - 4; y <= want + 1 && !deck; y++) {
                        deck = !level.getBlockState(new BlockPos(x, y, z)).isAir();
                    }
                    check(f, deck, "the bone bridge has no deck at " + x + "," + z);
                }
            }
            // 巣の戸: 封じると塞がり、開けると通れる
            BlockPos den = AbyssBuild.mark("den_bone");
            if (den != null) {
                BoneBridge.seal(level, true);
                check(f, BoneBridge.sealed(level), "the den door did not seal");
                BoneBridge.seal(level, false);
                check(f, !BoneBridge.sealed(level), "the den door did not open again");
            }
            // 潮: 周期の両端が引き潮と満ち潮、同じ時間は同じ、半周で満ち潮
            int low = SaltTide.lowLevel();
            int high = SaltTide.highLevel();
            check(f, high > low, "the high tide is not above the low tide");
            check(f, SaltTide.levelAt(0) == low, "the tide does not start at the low level: " + SaltTide.levelAt(0));
            check(f, SaltTide.levelAt(SaltTide.PERIOD / 2) == high, "the tide is not high at half the period: " + SaltTide.levelAt(SaltTide.PERIOD / 2));
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            boolean same = true;
            for (long t = 0; t < SaltTide.PERIOD * 2; t += 10) {
                int y = SaltTide.levelAt(t);
                min = Math.min(min, y);
                max = Math.max(max, y);
                same &= y == SaltTide.levelAt(t + SaltTide.PERIOD);
            }
            check(f, min == low && max == high, "the tide does not cycle between " + low + " and " + high + " (" + min + ".." + max + ")");
            check(f, same, "the tide is not periodic");
            check(f, SaltTide.levelAt(777) == SaltTide.levelAt(777), "the tide is not deterministic");
            // 水の列: 満ち潮で水、引き潮で空気 (列が一番上の所で見る)
            SaltTide.syncTo(level, high);
            check(f, SaltTide.shownLevel() == high, "the sync did not reach the high level");
            BlockPos wet = SaltTide.firstRowCell(high);
            check(f, wet != null, "the lake has no water at the high level");
            if (wet != null) {
                check(f, level.getBlockState(wet).is(Blocks.WATER), "the lake is not full at high tide");
                SaltTide.syncTo(level, low);
                check(f, !level.getBlockState(wet).is(Blocks.WATER), "the lake is still full at low tide");
            }
            SaltTide.syncTo(level, SaltTide.levelAt(level.getGameTime()));
            // 浮き: 満ち潮の重さの術だけ
            check(f, SaltTide.lift(0) == 0, "lift without the weight art is not 0");
            check(f, SaltTide.lift(0.5) > 0, "lift with the weight art is 0");
            check(f, SaltTide.highTide(high) && !SaltTide.highTide(low), "the high-tide test is wrong");
            // 高い棚: 満ち潮の水面より三つ以上上 (浮かないと届かない)
            BlockPos shelf = AbyssBuild.mark("shelf_salt");
            check(f, shelf != null && shelf.getY() >= high + 3, "the high shelf is not above the high tide: shelf " + shelf + " high " + high);
        } finally {
            hold(level, false);
        }
        report(f);
    }

    // 湖・橋・巣のある塊を読み込む (中心の塊の周り)
    private static void hold(ServerLevel level, boolean on) {
        if (level == null) {
            return;
        }
        for (int x = -HOLD; x <= HOLD; x++) {
            for (int z = -HOLD; z <= HOLD; z++) {
                level.setChunkForced(x, z, on);
            }
        }
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss layers bone salt PASSED (marks, bone bridge deck, den door seal, tide cycle and determinism, water rows, lift only at high tide, high shelf above the tide)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss layers bone salt FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
