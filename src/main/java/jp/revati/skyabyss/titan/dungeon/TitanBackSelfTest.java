package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.trial.RouteSeals;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.raid.InstanceArenas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 四体の巨神の背の次元の自己テスト (docs/design/32 の 2.5「巨神の中は建てない」)。SKYABYSS_SELFTEST_ONLY="titan back"
//  - titan back (build): 四体の背を建て終える (起動時の予約の続きを最後まで)
//  - titan back (layout): 外見だけになったか。体はある (体の芯の柱にブロックが並ぶ)・古い体の中 (鼓動の間・潮の広間・風の広間・炉心の広間の真ん中) は
//    埋まっている (掘っていない)・到着の台に立てる・到着の台が順路の入口の箱の中・体の外の心臓の間が建ち、中の空気が箱の外へ抜けない (封じた部屋)
//  - titan back (rebuild): チャンクを読み込んだまま四体を一度ずつ建て直し、かかった ms を記録に出す (建てる重さ。docs/design/32 の 5 章)
//  - titan back: まとめ (最後に流す。サーバーは止めない)
public final class TitanBackSelfTest {
    private TitanBackSelfTest() {
    }

    static final List<String> TITANS = List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER);
    private static final List<String> PENDING = new ArrayList<>();
    private static final Map<String, Long> REBUILD_MS = new LinkedHashMap<>();

    public static List<SelfTestRunner.Step> buildSteps() {
        return List.of(
                SelfTestRunner.Step.once("titan back (build)", server -> guarded(() -> {
                    PENDING.clear();
                    REBUILD_MS.clear();
                    for (String t : TITANS) {
                        ServerLevel back = server.getLevel(Titans.backOf(t));
                        if (back == null) {
                            PENDING.add(t + ": the back dimension is missing");
                            continue;
                        }
                        ensureBuilt(back, t);
                    }
                    long[] r = BackBuildJobs.runAll();
                    SkyAbyss.LOGGER.info("SELFTEST titan back: finished the queued back builds ({} ms)", r.length > 0 ? r[0] : 0);
                })),
                SelfTestRunner.Step.once("titan back (layout)", server -> guarded(() -> {
                    for (String t : TITANS) {
                        ServerLevel back = server.getLevel(Titans.backOf(t));
                        if (back != null) {
                            checkLayout(back, t, PENDING);
                        }
                    }
                })),
                SelfTestRunner.Step.once("titan back (rebuild)", server -> guarded(() -> {
                    for (String t : TITANS) {
                        ServerLevel back = server.getLevel(Titans.backOf(t));
                        if (back == null) {
                            continue;
                        }
                        TitanBackData data = TitanBackData.get(back);
                        data.markBuilt(-1);
                        long t0 = System.currentTimeMillis();
                        ensureBuilt(back, t);
                        BackBuildJobs.runAll();
                        REBUILD_MS.put(t, System.currentTimeMillis() - t0);
                        if (!data.isBuilt() || data.layoutVersion() < 0) {
                            PENDING.add(t + ": the rebuild did not mark the back as built");
                        }
                        checkLayout(back, t, PENDING);
                    }
                    SkyAbyss.LOGGER.info("SELFTEST titan back build ms (rebuild with the chunks loaded): {}", REBUILD_MS);
                })));
    }

    public static List<SelfTestRunner.Step> trialSteps() {
        return List.of(SelfTestRunner.Step.once("titan back", 20, server -> {
            if (PENDING.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST titan back PASSED (four backs are outside-only: body kept, old insides filled, arrival pad is the route entry and penned in, "
                        + "sealed heart arenas outside the bodies; rebuild ms {})", REBUILD_MS);
            } else {
                PENDING.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST titan back FAILED: {}", f));
            }
        }));
    }

    private static void guarded(Runnable check) {
        try {
            check.run();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST titan back crashed", e);
            PENDING.add("crashed: " + e);
        }
    }

    // 背を建てる (建ててあれば何もしない。予約が途中なら最後まで)
    static void ensureBuilt(ServerLevel back, String titan) {
        switch (titan) {
            case Titans.STONE_WHALE -> {
                TitanBackData data = TitanBackData.get(back);
                if (data.needsBuild()) {
                    TitanBackBuilder.build(back, data);
                }
            }
            case Titans.TIDE_SHELL -> jp.revati.skyabyss.titan.tide.TideBackBuilder.ensureBuilt(back);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackBuilder.ensureBuilt(back);
            case Titans.EMBER -> jp.revati.skyabyss.titan.ember.back.EmberBodyBuilder.ensureBuilt(back);
            default -> {
            }
        }
    }

    // 古い体の中の真ん中 (鼓動の間・潮の広間・風の広間・炉心の広間)。掘らなくなったので、体のブロックで埋まっている
    static BlockPos oldInside(String titan) {
        return switch (titan) {
            case Titans.STONE_WHALE -> TitanBackLayout.HUB.center().above(2);
            case Titans.TIDE_SHELL -> new BlockPos(0, jp.revati.skyabyss.titan.tide.back.TideBackLayout.HUB_FLOOR + 2, 0);
            case Titans.THUNDER_WING -> jp.revati.skyabyss.titan.storm.back.StormBackLayout.HUB_CENTER.above(2);
            default -> jp.revati.skyabyss.titan.ember.back.EmberBackLayout.HUB_CENTER.above(2);
        };
    }

    static void checkLayout(ServerLevel back, String titan, List<String> f) {
        // 体はある: 古い体の中の真ん中の柱に、体のブロックが続いている
        BlockPos in = oldInside(titan);
        int solid = 0;
        for (int dy = -6; dy <= 6; dy++) {
            if (!back.getBlockState(in.above(dy)).isAir()) {
                solid++;
            }
        }
        if (solid < 10) {
            f.add(titan + ": the old inside at " + in.toShortString() + " is hollow (" + solid + " of 13 blocks) — the body is missing or still carved");
        }
        // 到着の台に立てる
        BlockPos feet = RouteSeals.safeFeet(titan);
        if (!back.getBlockState(feet).getCollisionShape(back, feet).isEmpty() || !back.getBlockState(feet.above()).getCollisionShape(back, feet.above()).isEmpty()
                || !back.getBlockState(feet.below()).isFaceSturdy(back, feet.below(), Direction.UP)) {
            f.add(titan + ": no standing room on the arrival pad at " + feet.toShortString());
        }
        AABB box = TrialRoute.entryBox(titan);
        if (box == null || !box.contains(Vec3.atBottomCenterOf(feet).add(0, 0.1, 0))) {
            f.add(titan + ": the arrival pad is not inside the route entry box");
        }
        // 待機場所「渦の口」(ArrivalDeck。2026-11-07): 二度目に置く物がない・集まる所は平らに立てる・広場の外 (渦・堀・空) に落ちた人は戻る・
        // 広場の上の人は戻らない・心臓の前室と間の入口は見張りの円の外・帰りの石がある
        if (jp.revati.skyabyss.trial.ArrivalDeck.paletteOf(titan) != null) {
            jp.revati.skyabyss.trial.ArrivalDeck.place(back, titan);
            int again = jp.revati.skyabyss.trial.ArrivalDeck.place(back, titan);
            int bad = jp.revati.skyabyss.trial.ArrivalDeck.badStanding(back, titan);
            if (again != 0 || bad != 0) {
                f.add(titan + ": the arrival lobby is not settled (" + bad + " bad standing cells round the feet, " + again + " blocks placed on the second pass)");
            }
            BlockPos vortex = jp.revati.skyabyss.trial.ArrivalDeck.vortex(titan);
            if (vortex == null || !jp.revati.skyabyss.trial.ArrivalDeck.fallen(titan, vortex.getX() + 0.5, vortex.getY() - 8, vortex.getZ() + 0.5)
                    || jp.revati.skyabyss.trial.ArrivalDeck.fallen(titan, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5)) {
                f.add(titan + ": the lobby fall guard does not catch a fall into the vortex, or catches a player standing on the feet");
            }
            for (BlockPos q : new BlockPos[] {InstanceArenas.heartRest(back, titan), InstanceArenas.heartEntry(titan)}) {
                if (q != null && jp.revati.skyabyss.trial.ArrivalDeck.fallen(titan, q.getX() + 0.5, q.getY(), q.getZ() + 0.5)) {
                    f.add(titan + ": the lobby fall guard reaches the heart rooms at " + q.toShortString());
                }
            }
            BlockPos stone = jp.revati.skyabyss.trial.ArrivalDeck.homeStone(titan);
            if (stone == null || !back.getBlockState(stone).is(net.minecraft.world.level.block.Blocks.LODESTONE)
                    || !jp.revati.skyabyss.trial.ArrivalDeck.isReturnStone(back, stone)) {
                f.add(titan + ": no return stone in the arrival lobby at " + stone);
            }
        }
        // 体の外の心臓の間: 建っていて、中の空気が部屋の箱の外へ抜けない
        if (!InstanceArenas.heartBuilt(back, titan)) {
            BlockPos e = InstanceArenas.heartEntry(titan);
            f.add(titan + ": the heart arena outside the body is not built (entry " + e + ": below " + (e == null ? "-" : back.getBlockState(e.below()))
                    + ", at " + (e == null ? "-" : back.getBlockState(e)) + ", above " + (e == null ? "-" : back.getBlockState(e.above())) + ")");
            return;
        }
        BlockPos entry = InstanceArenas.heartEntry(titan);
        AABB arena = InstanceArenas.heartArena(titan);
        String leak = leak(back, entry, arena.inflate(24));
        if (leak != null) {
            f.add(titan + ": the heart arena is not sealed: air reaches " + leak);
        }
    }

    // from から空気 (当たりのないブロック) を辿り、limit の箱の外まで届けばその場所 (届かなければ null)。辿るのは 40 万マスまで
    public static String leak(ServerLevel level, BlockPos from, AABB limit) {
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(from);
        seen.add(from.asLong());
        while (!q.isEmpty() && seen.size() < 400_000) {
            BlockPos p = q.poll();
            if (!limit.contains(Vec3.atCenterOf(p))) {
                return p.toShortString();
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n.asLong()) && level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
                    q.add(n);
                }
            }
        }
        return null;
    }
}
