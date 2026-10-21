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
//  - 見え方: 心臓を止めていない人には見え、止めた人には見えない (観戦者・クリエイティブはいつも見える)
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

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000051a1"), "shrine_a"));
        FakePlayer b = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000051b2"), "shrine_b"));
        Set<UUID> stopped = new java.util.HashSet<>();
        try {
            TitanShrine.STOPPED = (s, id, t) -> stopped.contains(id);
            TitanVisibility.STOPPED_OVERRIDE = (id, t) -> stopped.contains(id);
            for (String titan : Titans.all()) {
                BlockPos c = TitanShrine.ensure(server, titan);
                BlockState st = level.getBlockState(c);
                check(f, st.is(ModBlocks.TITAN_SHRINE.get()) && st.getValue(RelicStyle.PROP) == RelicStyle.ofTitan(titan), titan + ": the shrine stele is missing at " + c);
                check(f, TitanShrineBlock.titanOf(st).equals(titan), titan + ": the stele names another titan");
                check(f, level.getBlockState(c.below().north(TitanShrine.RING)).equals(TitanShrine.ringBlock(titan)), titan + ": the ring is not of the titan's stone");
                check(f, level.getBlockState(c.east(TitanShrine.PEDESTAL)).is(ModBlocks.RESONANCE_FORGE.get()), titan + ": no resonance forge beside the shrine");
                check(f, TitanShrine.ensure(server, titan).equals(c) && c.equals(TitanShrine.posOf(server, titan)), titan + ": the shrine moved when ensured again");
                a.setPos(c.getX() + 0.5, c.getY(), c.getZ() + 2.5);
                b.setPos(c.getX() + 0.5, c.getY(), c.getZ() + 9.5);
                check(f, TitanShrine.inRing(a, c) && !TitanShrine.inRing(b, c), titan + ": the ring test is wrong");
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
                stopped.add(a.getUUID());
                TitanVisibility.clearCache();
                check(f, !TitanVisibility.sees(a, titan) || !jp.revati.skyabyss.progress.Progression.gateOn(server), titan + ": a player who stilled the heart still sees the titan");
            }
            check(f, ModItems.songShard(Titans.TIDE_SHELL) != ModItems.songShard(Titans.EMBER), "the song shards are not per titan");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST trial shrine crashed", e);
            f.add("crashed: " + e);
        } finally {
            TitanShrine.STOPPED = jp.revati.skyabyss.progress.Progression::stopped;
            TitanVisibility.STOPPED_OVERRIDE = null;
            TitanVisibility.clearCache();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial shrine PASSED (four shrines built, refusals, song shards, titan visibility)");
        } else {
            for (String s : f) {
                SkyAbyss.LOGGER.error("SELFTEST trial shrine FAILED: {}", s);
            }
        }
    }
}
