package jp.revati.skyabyss.story.ch6.valley;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.story.ch6.MoonClock;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.DustCrawler;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import jp.revati.skyabyss.story.ch6.mob.ShadowStitcher;
import jp.revati.skyabyss.story.ch6.mob.ShellHare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 新月の谷の自己テスト (docs/design/46 の 4.3)。段: "moon valley"
//   谷が流し込まれる (谷底・台地・裂け目・野営) / 印の所の仕掛け (欠片の台 3・鏡の石 3・星の門・灯りの台 6・星の溜まり・帰りの台) /
//   影の橋 (星灯りで消え、消えると戻る。脇の列は新月だけ) / 鏡の窪 (三つとも真ん中を向くとそろう) / 落ちた星の穴 (兎がいると取れない) /
//   門 (欠片のない人は通さない) / 敵の数字 (区画の倍率) / 綴じ手 (暗がりでは傷が通らない) / 影抱き (呼べる・暗がりの守り) / 頼み main_moon_valley
public final class ValleySelfTest {
    private ValleySelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        List<int[]> forced = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest(ValleyPuzzles.QUEST) != null, "main_moon_valley is not loaded");
            if (moon == null) {
                report(f);
                return;
            }
            long t0 = System.currentTimeMillis();
            moon.getChunk(MoonWorld.ARRIVAL);
            MoonWorld.build(moon);
            check(f, MoonWorld.valleyBuilt(server), "the valley was not marked as built");
            SkyAbyss.LOGGER.info("SELFTEST moon valley: build took {} ms", System.currentTimeMillis() - t0);
            // 地形: 野営は立てる、台地の上の主の間、裂け目の底は空
            BlockPos camp = MoonValley.mark("camp");
            check(f, moon.getBlockState(camp).isAir() && moon.getBlockState(camp.above()).isAir() && !moon.getBlockState(camp.below()).isAir(), "the camp is not standable");
            BlockPos arena = MoonValley.mark("arena_center");
            check(f, !moon.getBlockState(arena.below()).isAir() && moon.getBlockState(arena.above()).isAir(), "the lord's hollow is not standable");
            // 裂け目 (印は真ん中の柱の下。柱の北の脇が底まで空)
            BlockPos chasm = MoonValley.mark("chasm").offset(0, 0, -5);
            check(f, moon.getBlockState(chasm).isAir() && moon.getBlockState(chasm.above(30)).isAir(), "the chasm is not open");
            // 敵を数える所は読み込んでおく (入れた敵が見えるように)
            for (String k : new String[] {"shard_hole", "arena_center", "camp", "bridge_n"}) {
                BlockPos m = MoonValley.mark(k);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                        forced.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                    }
                }
            }
            // 仕掛け
            for (String s : ValleyPuzzles.sites()) {
                check(f, moon.getBlockState(MoonValley.mark("shard_" + s)).is(ValleyBlocks.STAR_PEDESTAL.get()), "pedestal missing at " + s);
            }
            for (int i = 0; i < 3; i++) {
                check(f, moon.getBlockState(MoonValley.mark("mirror_" + i)).is(ValleyBlocks.MIRROR_STONE.get()), "mirror stone missing " + i);
            }
            for (int i = 0; i < 6; i++) {
                check(f, moon.getBlockState(MoonValley.mark("lamp_" + i)).is(ValleyBlocks.LAMP_POST.get()), "lamp post missing " + i);
            }
            check(f, moon.getBlockState(MoonValley.mark("valley_gate")).is(ValleyBlocks.STAR_GATE.get()), "star gate missing");
            check(f, moon.getBlockState(MoonValley.mark("star_pool")).is(ValleyBlocks.STAR_POOL.get()), "star pool missing");
            check(f, moon.getBlockState(MoonValley.mark("return_stone")).is(Blocks.LODESTONE), "camp return stone missing");
            // 影の橋
            MoonClock.skipTo(moon, 0);
            StarLamps.clear(moon);
            List<BlockPos> br = ValleyTicker.bridgeBlocks(moon);
            check(f, br.size() >= 18, "too few shadow bridge blocks: " + br.size());
            ValleyTicker.updateBridges(moon);
            check(f, br.stream().allMatch(p -> moon.getBlockState(p).getValue(ShadowBridgeBlock.SOLID)), "the bridge is not solid in the dark at new moon");
            BlockPos mid = br.get(0);
            StarLamps.place(moon, mid.above(), UUID.randomUUID());
            ValleyTicker.updateBridges(moon);
            check(f, !moon.getBlockState(mid).getValue(ShadowBridgeBlock.SOLID), "a lit bridge block stayed solid");
            StarLamps.clear(moon);
            ValleyTicker.updateBridges(moon);
            check(f, moon.getBlockState(mid).getValue(ShadowBridgeBlock.SOLID), "the bridge did not come back after the light faded");
            MoonClock.skipTo(moon, 2);
            ValleyTicker.updateBridges(moon);
            boolean wideGone = br.stream().filter(p -> moon.getBlockState(p).getValue(ShadowBridgeBlock.WIDE)).noneMatch(p -> moon.getBlockState(p).getValue(ShadowBridgeBlock.SOLID));
            boolean coreStays = br.stream().filter(p -> !moon.getBlockState(p).getValue(ShadowBridgeBlock.WIDE)).allMatch(p -> moon.getBlockState(p).getValue(ShadowBridgeBlock.SOLID));
            check(f, wideGone && coreStays, "outside new moon the side lanes should fade and the middle stay (wideGone " + wideGone + ", core " + coreStays + ")");
            MoonClock.skipTo(moon, 0);
            ValleyTicker.updateBridges(moon);
            // 鏡
            check(f, !ValleyPuzzles.mirrorsAligned(moon), "the mirrors start aligned");
            BlockPos center = MoonValley.mark("shard_mirror");
            for (int i = 0; i < 3; i++) {
                BlockPos m = MoonValley.mark("mirror_" + i);
                BlockState st = moon.getBlockState(m);
                Direction want = MirrorStoneBlock.toward(m, center);
                for (int k = 0; k < 4 && st.getValue(MirrorStoneBlock.FACING) != want; k++) {
                    st = st.setValue(MirrorStoneBlock.FACING, st.getValue(MirrorStoneBlock.FACING).getClockWise());
                }
                moon.setBlock(m, st, 3);
            }
            check(f, ValleyPuzzles.mirrorsAligned(moon), "turning all three mirrors to the middle did not align them");
            check(f, ValleyPuzzles.refusal(moon, "mirror", center) == null, "the aligned mirror pedestal still refuses");
            ValleyPuzzles.scramble(moon);
            check(f, !ValleyPuzzles.mirrorsAligned(moon), "scramble did not turn the mirrors off");
            // 落ちた星の穴
            BlockPos hole = MoonValley.mark("shard_hole");
            ShellHare hare = (ShellHare) ValleyTicker.put(moon, MoonMobsSetup.SHELL_HARE.get(), MoonValley.mark("nest_0"));
            spawned.add(hare);
            check(f, hare != null && ValleyPuzzles.refusal(moon, "hole", hole) != null, "the hole pedestal can be taken while hares guard it");
            if (hare != null) {
                hare.discard();
            }
            check(f, ValleyPuzzles.refusal(moon, "hole", hole) == null, "the hole pedestal refuses with no hares");
            // 門
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_valley"));
            check(f, !ValleyPuzzles.mayCross(p), "a player without shards may cross the star gate");
            // 敵の数字
            DustCrawler c = (DustCrawler) ValleyTicker.put(moon, MoonMobsSetup.DUST_CRAWLER.get(), camp.offset(4, 0, 0));
            spawned.add(c);
            double want = DustCrawler.HP * MoonMob.SECTION_HP[0] * MoonMob.MATCHED;
            check(f, c != null && Math.abs(c.getMaxHealth() - want) < 0.01, "dust crawler hp " + (c == null ? -1 : c.getMaxHealth()) + " != " + want);
            ShadowStitcher s = (ShadowStitcher) ValleyTicker.put(moon, MoonMobsSetup.SHADOW_STITCHER.get(), MoonValley.mark("bridge_n").offset(0, 1, -4));
            spawned.add(s);
            if (s != null) {
                check(f, !s.lit(), "the stitcher by the chasm is lit without a lamp");
                float h0 = s.getHealth();
                s.hurt(moon.damageSources().generic(), 10f);
                check(f, h0 - s.getHealth() < 2.0f, "a stitcher in the dark took full damage: " + (h0 - s.getHealth()));
                StarLamps.place(moon, s.blockPosition(), UUID.randomUUID());
                check(f, s.lit(), "a stitcher under a star mark is not lit");
                StarLamps.clear(moon);
            }
            // 影抱き
            ShadowEmbracer lord = ValleyArena.summon(moon);
            spawned.add(lord);
            check(f, lord != null && lord.getMaxHealth() > 1000, "the shadow embracer did not appear with its hp: " + (lord == null ? -1 : lord.getMaxHealth()));
            if (lord != null) {
                float h0 = lord.getHealth();
                lord.invulnerableTime = 0;
                lord.hurt(moon.damageSources().generic(), 50f);
                check(f, h0 - lord.getHealth() <= 10.5f, "the embracer in the dark took full damage: " + (h0 - lord.getHealth()));
                check(f, ValleyArena.lords(moon).size() == 1, "summon twice made two lords");
            }
            ValleyArena.clear(moon);
            check(f, ValleyArena.lords(moon).isEmpty(), "clearing the hollow left the lord");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon valley crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            spawned.stream().filter(e -> e != null && e.isAlive()).forEach(Entity::discard);
            if (moon != null) {
                StarLamps.clear(moon);
                for (int[] c : forced) {
                    moon.setChunkForced(c[0], c[1], false);
                }
            }
        }
        report(f);
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon valley PASSED (valley terrain, camp, hollow, chasm, puzzle blocks, shadow bridge light and phase, mirrors, nest guard, gate, mob numbers, stitcher dark guard, lord summon and guard, main_moon_valley)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon valley FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
