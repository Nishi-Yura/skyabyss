package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssReturn;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// SELFTEST abyss v3 (2026-10-23 の試遊の直し): 帰りの祠・道案内・主の技と段とボスバー・縄張りの主のバーと技。
//   祠      骨の野営の印 exit に磁石石、足もとに彫った石英、天井に光、碑。帰り始めは二度押しても一度だけ
//   道案内  印なし → 近い印へ / 巣に守られた印 → 巣へ / 印三つ → 北の格子の門 / 主の間 → 主 / 主を倒した → 下りの鎖。生き残りの一言の段
//   主      四つの層とも技が 4 つ以上、一段目 2 つ以上・三段目は全部。どの技も赤い予告を置く。段の境 (66%・33%)。
//           目覚めは咆哮 (刃が通らない・バーは咆哮の刻み)。段の変わり目でバーの色が変わる。一度の当たりで段を越えない
//   縄張り  縄張りの主にボスバーと予告つきの技
public final class AbyssV3SelfTest {
    private AbyssV3SelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        hold(level, true);
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
    }

    private static void hold(ServerLevel level, boolean on) {
        for (int cx = -5; cx <= 4; cx++) {
            for (int cz = -5; cz <= 4; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        try {
            if (level == null) {
                f.add("the abyss dimension is missing");
            } else {
                shrine(f, server, level);
                guide(f, server, level);
                lords(f, server, level);
                elite(f, server, level);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss v3 crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (level != null) {
                AbyssLords.resetForTest(level);
                LayerFights.resetForTest(level);
                hold(level, false);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss v3 PASSED (return shrine and one return at a time; guide: nearest mark, nest first, bar gate, lord, descent, survivor states; "
                    + "lords: 4+ moves each, phase pools, red telegraphs, phase borders, intro roar blocks blades, phase colours on the bar, one hit cannot skip a phase; "
                    + "territory boss bar and telegraphed moves)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST abyss v3 FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 帰りの祠
    static void shrine(List<String> f, MinecraftServer server, ServerLevel level) {
        BlockPos exit = AbyssBuild.mark("exit");
        check(f, exit != null, "the exit mark is missing");
        if (exit == null) {
            return;
        }
        check(f, level.getBlockState(exit).is(Blocks.LODESTONE), "no lodestone at the exit: " + level.getBlockState(exit));
        check(f, level.getBlockState(exit.below()).is(Blocks.CHISELED_QUARTZ_BLOCK), "the shrine dais is missing under the lodestone");
        check(f, level.getBlockState(exit.above(7)).is(Blocks.PEARLESCENT_FROGLIGHT), "the shrine light above the chain is missing: " + level.getBlockState(exit.above(7)));
        BlockPos sign = AbyssBuild.mark("returnsign");
        check(f, sign != null && level.getBlockState(sign).is(jp.revati.skyabyss.registry.ModBlocks.TABLET.get()), "the return tablet is missing");
        check(f, Layers.inCamp("bone", exit), "the return shrine is not inside the bone camp");
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v3_home"));
        p.moveTo(exit.getX() + 1.5, exit.getY(), exit.getZ() + 0.5);
        check(f, AbyssReturn.begin(p, false) && !AbyssReturn.begin(p, false), "beginning the return twice should only start once");
        AbyssReturn.flushForTest(server);
        check(f, !AbyssReturn.going(p), "the return did not finish");
    }

    // ---------------------------------------------------------------- 道案内
    static void guide(List<String> f, MinecraftServer server, ServerLevel level) {
        LayerData d = LayerData.get(server);
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v3_guide"));
        try {
            BlockPos camp = AbyssBuild.mark("camp_bone");
            p.moveTo(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5);
            AbyssGuide.Line l = AbyssGuide.lineFor(p);
            check(f, (l.key().endsWith(".mark") || l.key().endsWith(".nest")) && l.target() != null, "a player with no marks should be sent to a mark (or its nest): " + l);
            d.set(p.getUUID(), "mark:bone_1");
            d.set(p.getUUID(), "mark:bone_3");
            l = AbyssGuide.lineFor(p);
            check(f, l.key().endsWith(".nest") && l.target() != null && l.target().equals(AbyssBuild.mark("nest_bone")),
                    "the guarded mark should send the player to the nest first: " + l);
            check(f, "guard".equals(AbyssGuide.survivorState(p, "bone")), "the survivor should talk about the nest: " + AbyssGuide.survivorState(p, "bone"));
            d.set(p.getUUID(), "clear:nest_bone");
            l = AbyssGuide.lineFor(p);
            check(f, l.key().endsWith(".mark") && AbyssBuild.mark("mark_bone_2").equals(l.target()), "after the nest the guide should point at mark 2: " + l);
            d.set(p.getUUID(), "mark:bone_2");
            l = AbyssGuide.lineFor(p);
            check(f, l.key().endsWith(".gate") && AbyssBuild.mark("gate_bone").equals(l.target()), "three marks should point at the bar gate: " + l);
            check(f, "lord".equals(AbyssGuide.survivorState(p, "bone")), "the survivor should teach the lord: " + AbyssGuide.survivorState(p, "bone"));
            BlockPos arena = AbyssBuild.mark("arena_bone");
            p.moveTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5);
            l = AbyssGuide.lineFor(p);
            check(f, l.key().endsWith(".lord"), "in the arena the guide should name the lord: " + l);
            d.set(p.getUUID(), "lord:bone");
            l = AbyssGuide.lineFor(p);
            check(f, l.key().endsWith(".descend") && AbyssBuild.mark("descend_bone").equals(l.target()), "after the lord the guide should point at the chain down: " + l);
            check(f, AbyssGuide.boardLine(p, "bone", 2).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().endsWith("board.done"), "the route board should say mark 2 is taken");
        } finally {
            d.forget(p.getUUID());
        }
    }

    // ---------------------------------------------------------------- 主
    static void lords(List<String> f, MinecraftServer server, ServerLevel level) {
        for (int li = 0; li < 4; li++) {
            check(f, LordMoves.MOVES[li].length >= 4, Layers.ORDER.get(li) + " lord has fewer than 4 moves");
            check(f, LordMoves.pool(li, 0).size() >= 2, Layers.ORDER.get(li) + " lord has fewer than 2 moves in phase 1");
            check(f, LordMoves.pool(li, 2).size() == LordMoves.MOVES[li].length, Layers.ORDER.get(li) + " lord does not use every move in phase 3");
            for (LordMoves.Attack a : LordMoves.MOVES[li]) {
                check(f, LordMoves.warnOf(a, 2) >= LordMoves.MIN_WARN, a.anim() + " warns for less than " + LordMoves.MIN_WARN + " ticks in phase 3");
            }
        }
        check(f, AbyssLords.phaseFor(1f) == 0 && AbyssLords.phaseFor(0.6f) == 1 && AbyssLords.phaseFor(0.2f) == 2, "the phase borders are wrong");
        ServerBossEvent probe = new ServerBossEvent(Component.literal("probe"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        for (int ph = 0; ph < 3; ph++) {
            AbyssBossBars.phase(probe, ph);
            check(f, AbyssBossBars.phaseOf(probe) == ph, "the bar colour does not carry phase " + ph);
        }
        AbyssBossBars.roar(probe, true);
        check(f, AbyssBossBars.roaring(probe), "the bar does not carry the roar");

        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v3_lord"));
        for (String layer : Layers.ORDER) {
            AbyssLords.resetForTest(level);
            BlockPos arena = AbyssBuild.mark("arena_" + layer);
            p.moveTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 4.5);
            long now = level.getGameTime();
            AbyssLords.wake(level, layer, List.of(p), now);
            AbyssLords.Fight fight = AbyssLords.fight(layer);
            LivingEntity lord = AbyssLords.lord(level, layer);
            check(f, fight != null && lord != null, layer + " lord did not wake");
            if (fight == null || lord == null) {
                continue;
            }
            check(f, fight.roarUntil > now, layer + " lord has no intro roar");
            float hp = lord.getHealth();
            lord.hurt(level.damageSources().playerAttack(p), 20f);
            check(f, lord.getHealth() >= hp, layer + " lord took damage during its intro roar");
            // どの技も赤い予告を置く
            for (LordMoves.Attack a : LordMoves.MOVES[fight.li]) {
                Telegraph.resetStats();
                LordMoves.start(level, fight, lord, p, List.of(p), a, LordMoves.warnOf(a, 0));
                check(f, Telegraph.damagingCreated() > 0, layer + " move " + a.anim() + " did not place a red telegraph");
            }
            // 段の境: 一度の大きな当たりでも 66% で止まり、段の変わり目で二段目 (紫のバー・咆哮)
            fight.roarUntil = 0;
            lord.invulnerableTime = 0;
            lord.hurt(level.damageSources().playerAttack(p), lord.getMaxHealth());
            check(f, lord.isAlive() && lord.getHealth() >= lord.getMaxHealth() * 0.6f, layer + " lord skipped a phase on one hit: " + lord.getHealth() + "/" + lord.getMaxHealth());
            lord.setHealth(lord.getMaxHealth() * 0.6f);
            AbyssLords.phaseShift(level, fight, lord, List.of(p), AbyssLords.phaseFor(0.6f), level.getGameTime());
            AbyssBossBars.phase(fight.bar, fight.phase);
            AbyssBossBars.roar(fight.bar, fight.roarUntil > level.getGameTime());
            check(f, fight.phase == 1 && fight.bar.getColor() == BossEvent.BossBarColor.PURPLE && AbyssBossBars.roaring(fight.bar),
                    layer + " lord phase shift did not reach the bar: " + fight.phase + " " + fight.bar.getColor());
        }
        AbyssLords.resetForTest(level);
    }

    // ---------------------------------------------------------------- 縄張りの主
    static void elite(List<String> f, MinecraftServer server, ServerLevel level) {
        BlockPos at = AbyssBuild.mark("wp_salt_3");
        check(f, at != null, "wp_salt_3 mark is missing");
        if (at == null) {
            return;
        }
        LivingEntity boss = LayerFights.elite(level, "white_shell_keeper", at, 3.0, 1.5, "abyss_v2.skyabyss.elite.terr_salt");
        check(f, boss != null, "the territory boss did not spawn");
        if (boss == null) {
            return;
        }
        try {
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "abyss_v3_elite"));
            p.moveTo(boss.getX() + 3, boss.getY(), boss.getZ());
            LayerFights.Run run = new LayerFights.Run("terr_salt");
            run.mobs.add(boss.getUUID());
            Telegraph.resetStats();
            long now = level.getGameTime();
            LayerFights.eliteTick(level, run, boss, List.of(p), now);
            LayerFights.eliteTick(level, run, boss, List.of(p), now + 100);
            check(f, run.bar != null, "the territory boss has no boss bar");
            check(f, Telegraph.damagingCreated() > 0, "the territory boss did not telegraph a move");
            if (run.bar != null) {
                run.bar.removeAllPlayers();
            }
        } finally {
            boss.discard();
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
