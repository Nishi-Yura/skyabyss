package jp.revati.skyabyss.titan.shrine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.record.Keepsakes;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.relic.RelicStyle;
import jp.revati.skyabyss.titan.TitanVisibility;

// 巨神の祠・歌片・巨神の見え方の自己テスト (docs/design/32 の 3 章)。段の名前は "trial shrine"。
//  - 祠: 四体とも建ち (碑の巨神・輪の材質・炉・台座)、建て直しても同じ場所、輪の判定
//  - 断り方: 心臓を止めていない人 (本人・輪の仲間)・歌片なし・順路が動いている。全部そろえば断らない
//  - 歌片: 巨神ごとに別の品、大事な品 (投げ捨てられない)、旅嚢の固定枠
//  - 見え方: 心臓を止めていない人には見え、止めた人には見えない (観戦者・クリエイティブはいつも見える)。止めた人も眠りの間だけは見る
public final class ShrineSelfTest {
    private ShrineSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("trial shrine", 10, ShrineSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static boolean walkable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty()
                && level.getBlockState(feet).getFluidState().isEmpty()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    // 参道の終わりから、集合台と碑のそば (水平 2・高さ ±2) まで、一段ずつ (跳んで一段まで) 歩いて行けるか
    static boolean reachable(ServerLevel level, BlockPos c, String titan) {
        java.util.Set<BlockPos> w = walkSet(level, c, titan);
        if (!w.contains(ShrineBuild.padOf(c, titan))) {
            return false;
        }
        for (BlockPos p : w) {
            if (Math.abs(p.getX() - c.getX()) <= 2 && Math.abs(p.getZ() - c.getZ()) <= 2 && Math.abs(p.getY() - c.getY()) <= 2) {
                return true;
            }
        }
        return false;
    }

    static BlockPos pathStart(BlockPos c, String titan) {
        ShrineBuild.Layout l = ShrineBuild.layout(titan);
        return ShrineBuild.originOf(c, titan).offset(0, 1, l.pathEnd());
    }

    static java.util.Set<BlockPos> walkSet(ServerLevel level, BlockPos c, String titan) {
        BlockPos o = ShrineBuild.originOf(c, titan);
        ShrineBuild.Layout l = ShrineBuild.layout(titan);
        int r = l.radius() + 3;
        BlockPos start = pathStart(c, titan);
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(start);
        seen.add(start);
        if (!walkable(level, start)) {
            return seen;
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -3; dy--) {
                    BlockPos n = p.relative(d).above(dy);
                    if (Math.abs(n.getX() - o.getX()) > r || n.getZ() - o.getZ() > l.pathEnd() || n.getZ() - o.getZ() < -r || !walkable(level, n)) {
                        continue;
                    }
                    if (dy == 1 && !level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()) {
                        continue;
                    }
                    if (seen.add(n)) {
                        queue.add(n);
                    }
                    break;
                }
            }
        }
        return seen;
    }

    // 歩いて行ける床のうち、近く (9 歩) に光るものがない所 (なければ null)
    static String unlit(ServerLevel level, BlockPos c, String titan) {
        BlockPos o = ShrineBuild.originOf(c, titan);
        int r = ShrineBuild.layout(titan).radius() + 3;
        List<BlockPos> sources = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= ShrineBuild.layout(titan).pathEnd(); z++) {
                for (int y = -4; y <= 36; y++) {
                    BlockPos p = o.offset(x, y, z);
                    if (level.getBlockState(p).getLightEmission() > 0) {
                        sources.add(p);
                    }
                }
            }
        }
        java.util.Set<BlockPos> built = new java.util.HashSet<>();
        for (jp.revati.skyabyss.opening.site.Blueprint.Cell cell : ShrineBuild.layout(titan).bp().cells) {
            if (!cell.state().isAir()) {
                built.add(o.offset(cell.x(), cell.y() + 1, cell.z()));
            }
        }
        for (BlockPos p : walkSet(level, c, titan)) {
            if (!built.contains(p)) {
                continue;
            }
            boolean lit = false;
            for (BlockPos s : sources) {
                if (s.distManhattan(p) <= 9) {
                    lit = true;
                    break;
                }
            }
            if (!lit) {
                return p.toShortString() + " rel=" + p.subtract(o).toShortString();
            }
        }
        return null;
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000051a1"), "shrine_a"));
        FakePlayer b = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000051b2"), "shrine_b"));
        Set<UUID> stopped = new java.util.HashSet<>();
        try {
            TitanShrine.STOPPED = (s, id, t) -> stopped.contains(id);
            TitanVisibility.STOPPED_OVERRIDE = (id, t) -> stopped.contains(id);
            Set<UUID> notReached = new java.util.HashSet<>();
            TitanVisibility.REACHED_OVERRIDE = (id, t) -> !notReached.contains(id);
            for (String titan : Titans.all()) {
                BlockPos c = TitanShrine.ensure(server, titan);
                BlockState st = level.getBlockState(c);
                check(f, st.is(ModBlocks.TITAN_SHRINE.get()) && st.getValue(RelicStyle.PROP) == RelicStyle.ofTitan(titan), titan + ": the shrine stele is missing at " + c);
                check(f, TitanShrineBlock.titanOf(st).equals(titan), titan + ": the stele names another titan");
                check(f, level.getBlockState(ShrineBuild.forgeOf(c, titan)).is(ModBlocks.RESONANCE_FORGE.get()), titan + ": no resonance forge at the shrine");
                if (!reachable(level, c, titan)) {
                    java.util.Set<BlockPos> w = walkSet(level, c, titan);
                    BlockPos best = null;
                    for (BlockPos q : w) {
                        if (best == null || q.distSqr(c) < best.distSqr(c)) {
                            best = q;
                        }
                    }
                    f.add(titan + ": the gathering dais (" + w.contains(ShrineBuild.padOf(c, titan)) + ") or the stele cannot be walked to; walked " + w.size()
                            + ", nearest to the stele " + (best == null ? "-" : best.subtract(ShrineBuild.originOf(c, titan)).toShortString()) + ", start "
                            + walkable(level, pathStart(c, titan)));
                }
                String dark = unlit(level, c, titan);
                check(f, dark == null, titan + ": a walkable floor has no light nearby (mobs can spawn): " + dark);
                check(f, TitanShrine.ensure(server, titan).equals(c) && c.equals(TitanShrine.posOf(server, titan)), titan + ": the shrine moved when ensured again");
                // 集合台 (広場の真ん中の円い壇)。碑のそばは台ではない
                BlockPos pad = ShrineBuild.padOf(c, titan);
                check(f, !level.getBlockState(pad.below()).getCollisionShape(level, pad.below()).isEmpty() && walkable(level, pad),
                        titan + ": no standing room on the gathering dais at " + pad.toShortString());
                a.setPos(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
                b.setPos(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                check(f, TitanShrine.onPad(a, c, titan) && !TitanShrine.onPad(b, c, titan), titan + ": the pad test is wrong");
                // 断り方
                stopped.clear();
                check(f, TitanShrine.refusal(server, a, titan, List.of(a), true, false) != null, titan + ": a stranger was let in");
                stopped.add(a.getUUID());
                check(f, TitanShrine.refusal(server, a, titan, List.of(a, b), true, false) != null, titan + ": a ring member who never stilled the heart was let in");
                check(f, TitanShrine.refusal(server, a, titan, List.of(a), false, false) != null, titan + ": no shard was needed");
                check(f, TitanShrine.refusal(server, a, titan, List.of(a), true, true) != null, titan + ": a second route started on a running one");
                check(f, TitanShrine.refusal(server, a, titan, List.of(a), true, false) == null, titan + ": a ready player was refused");
                // 歌片
                ItemStack shard = new ItemStack(ModItems.songShard(titan));
                check(f, titan.equals(ModItems.songShardTitan(shard.getItem())), titan + ": the song shard does not name its titan");
                check(f, Keepsakes.guarded(shard), titan + ": the song shard can be thrown away");
                check(f, jp.revati.skyabyss.pouch.PouchEntries.byId(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(shard.getItem())) != null,
                        titan + ": the song shard has no pouch slot");
                // 見え方
                stopped.clear();
                TitanVisibility.clearCache();
                check(f, TitanVisibility.sees(a, titan), titan + ": a chapter player cannot see the titan");
                // まだその巨神の章に来ていない人には見えない (心臓を止めていなくても)
                boolean gate0 = jp.revati.skyabyss.progress.Progression.gateOn(server);
                notReached.add(a.getUUID());
                TitanVisibility.clearCache();
                check(f, !TitanVisibility.sees(a, titan) || !gate0, titan + ": a player whose story has not reached the titan sees it");
                notReached.clear();
                TitanVisibility.clearCache();
                stopped.add(a.getUUID());
                TitanVisibility.clearCache();
                boolean gate = jp.revati.skyabyss.progress.Progression.gateOn(server);
                check(f, !TitanVisibility.sees(a, titan) || !gate, titan + ": a player who stilled the heart still sees the titan");
                // 眠りを見届ける人: 心臓を止めて運ばれた人は、眠りが終わるまで見る。目覚めたら見えない
                jp.revati.skyabyss.titan.end.TitanDormancy.begin(server, titan, 0);
                TitanVisibility.witnessRest(titan, List.of(a.getUUID()));
                check(f, TitanVisibility.sees(a, titan), titan + ": the carried-out player cannot see the sleeping titan");
                jp.revati.skyabyss.titan.end.TitanDormancy.clear(server, titan);
                TitanVisibility.clearCache();
                check(f, !TitanVisibility.sees(a, titan) || !gate, titan + ": the titan stayed visible to its stopper after the sleep");
                if (titan.equals(Titans.STONE_WHALE) && gate) {
                    // 倒した巨神の出現の知らせ (歌・文) は、止めた人に届かない。止めていない人には届く
                    stopped.clear();
                    stopped.add(a.getUUID());
                    TitanVisibility.clearCache();
                    a.setPos(0.5, 200, 0.5);
                    b.setPos(0.5, 200, 0.5);
                    java.util.List<net.minecraft.server.level.ServerPlayer> heard = jp.revati.skyabyss.titan.WhaleKeeper.appearAudience(List.of(a, b), 0.5, 0.5);
                    check(f, !heard.contains(a) && heard.contains(b), "the whale's appearance was announced to a player who already stilled its heart");
                    stopped.clear();
                    TitanVisibility.clearCache();
                }
            }
            check(f, ModItems.songShard(Titans.TIDE_SHELL) != ModItems.songShard(Titans.EMBER), "the song shards are not per titan");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial shrine crashed", e);
            f.add("crashed: " + e);
        } finally {
            TitanShrine.STOPPED = jp.revati.skyabyss.progress.Progression::stopped;
            TitanVisibility.STOPPED_OVERRIDE = null;
            TitanVisibility.REACHED_OVERRIDE = null;
            TitanVisibility.clearCache();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial shrine PASSED (four shrines built from their own blueprints, walkable and lit, refusals, song shards, titan visibility)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial shrine FAILED: {}", s);
            }
        }
    }
}
