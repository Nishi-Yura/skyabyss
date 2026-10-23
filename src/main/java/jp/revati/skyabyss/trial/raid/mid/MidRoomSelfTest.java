package jp.revati.skyabyss.trial.raid.mid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialRuntime;
import jp.revati.skyabyss.trial.TrialSlot;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 順路の中ボスの間 (trial/raid/mid) の検査。SKYABYSS_SELFTEST_ONLY="raid mid"。四体の巨神ごとに、一人の偽の人で:
//   順路を始める → 部屋 1〜3 を越えたことにする → 区画の段 4 の中ボスの間の中へ運ばれる (前室を歩かない)
//   → 準備のあと中ボスが現れる (番兵長は手下の波のあと)・体力の倍率は一人で 1 倍・一人の攻撃で削れる
//   → (潮殻だけ) 招き潮の水と、全滅のやり直しで水が引き中ボスが湧き直す
//   → 倒すと越え、順路が心臓の段 (LAST_STEP) へ進む
public final class MidRoomSelfTest {
    private MidRoomSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.until("raid mid", MidRoomSelfTest::tick));
    }

    private static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
    private static final List<String> FAILS = new ArrayList<>();
    private static final List<String> DONE = new ArrayList<>();
    private static int index = -1;
    private static int stage;
    private static int waited;
    private static ServerLevel back;
    private static ServerPlayer player;
    private static UUID group;
    private static String titan;
    private static boolean wiped;

    private static boolean tick(MinecraftServer server) {
        try {
            if (index < 0 || stage < 0) {
                if (!next(server)) {
                    report();
                    return true;
                }
            }
            if (step()) {
                teardown();
                stage = -1;
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raid mid crashed", e);
            FAILS.add(titan + ": crashed in stage " + stage + ": " + e);
            teardown();
            stage = -1;
        }
        return false;
    }

    private static void report() {
        TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid mid PASSED ({}: carried into the slot's mid-boss arena after room 3, boss appears, solo scale 1x, "
                    + "damage lands, tide flood + wipe reset, defeat moves the route to the heart)", DONE);
        } else {
            for (String f : FAILS) {
                SkyAbyss.LOGGER.error("SELFTEST raid mid FAILED: {}", f);
            }
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILS.add(titan + ": " + what);
        }
    }

    // 待つ。時間切れなら失敗にして、この巨神を終える (true)
    private static boolean timeout(int max, String what) {
        if (++waited > max) {
            FAILS.add(titan + ": timed out waiting for " + what);
            stage = 99;
            return true;
        }
        return false;
    }

    private static boolean next(MinecraftServer server) {
        while (++index < TITANS.length) {
            titan = TITANS[index];
            back = server.getLevel(Titans.backOf(titan));
            if (back == null || !RaidRooms.active(titan) || MidRooms.idOf(titan) == null) {
                SkyAbyss.LOGGER.info("SELFTEST raid mid: {} skipped (no back or no raid route)", titan);
                continue;
            }
            TrialRoute.clearAll();
            player = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000a1d" + index), "raid_mid_" + index));
            group = Groups.keyOf(player);
            List<ServerPlayer> ps = List.of(player);
            ServerLevel lv = back;
            TrialRoute.PLAYERS = l -> l == lv ? ps : List.of();
            BackGroupData.get(back).remove(group);
            Downed.reviveFull(player);
            player.setHealth(player.getMaxHealth());
            stage = 0;
            waited = 0;
            wiped = false;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean step() {
        RaidRun.Run run = RaidRun.of(group);
        switch (stage) {
            case 0 -> {
                TrialRoute.start(back, group, titan, List.of(player));
                check(TrialRoute.step(back, group) == 1, "the route did not start");
                stage = 1;
            }
            case 1, 2, 3 -> {
                // 部屋 stage を越えたことにして、次へすぐ運ぶ
                if (TrialRoute.step(back, group) != stage || TrialRoute.mid(back, group)) {
                    check(false, "unexpected step " + TrialRoute.step(back, group) + " before passing room " + stage);
                    return true;
                }
                TrialRoute.devPass(back, group, titan, player);
                TrialRoute.flushPending();
                TrialRoute.tick(back, titan, List.of(player), back.getGameTime());
                stage++;
            }
            case 4 -> {
                check(TrialRoute.mid(back, group), "after room 3 the route is not on the mid-boss step");
                check(run != null && run.mid && MidRooms.idOf(titan).equals(run.ctx.roomId()), "no mid-boss room run (" + (run == null ? null : run.ctx.roomId()) + ")");
                if (run == null || !run.mid) {
                    return true;
                }
                BlockPos o = run.ctx.origin();
                check(o.equals(RaidRun.originOf(TrialSlot.of(BackGroupData.get(back).peek(group)), RaidRun.MID_STEP)), "the arena is not at the slot's step-4 origin: " + o);
                check(run.ctx.cell().inner().contains(player.position()), "the player was not carried INTO the arena (at " + player.blockPosition().toShortString()
                        + ", arena " + run.ctx.cell().inner() + ")");
                check(back.getBlockState(player.blockPosition().below()).isSolid(), "no floor under the carried player");
                stage = 5;
                waited = 0;
            }
            case 5 -> {
                // 準備のあと始まる。番兵長は手下を倒すと出る
                if (run == null || run.phase != RaidRun.Phase.ACTIVE) {
                    return timeout(200, "the mid room to start");
                }
                MidRoom room = (MidRoom) run.room;
                if (room.boss == null) {
                    for (Mob m : new ArrayList<>(run.ctx.aliveMobs())) {
                        m.kill();
                    }
                    return timeout(200, "the boss to appear");
                }
                Mob boss = room.boss;
                check(boss.isAlive() && run.ctx.cell().inner().inflate(0.5, 8, 0.5).contains(boss.position()), "the boss is not inside the arena: " + boss.position());
                check(run.lastHud != null && !run.lastHud.objKey().isEmpty(), "no objective strip for the mid room");
                stage = 6;
                waited = 0;
            }
            case 6 -> {
                MidRoom room = (MidRoom) run.room;
                Mob boss = room.boss;
                if (boss == null) {
                    return timeout(100, "the boss");
                }
                // 一人の攻撃で削れる (潜っている・柱の上などで通らない間は待つ)
                float before = boss.getHealth();
                player.setPos(boss.getX() + 1.5, boss.getY(), boss.getZ());
                boss.invulnerableTime = 0;
                boss.hurt(back.damageSources().playerAttack(player), 20f);
                if (boss.getHealth() >= before) {
                    return timeout(800, "a solo hit to damage the boss"); // 潮招きは潜る・守りの間 (数秒〜十数秒) は傷が通らない
                }
                check(scaleOk(boss), "the boss is scaled for more than one player");
                stage = Titans.TIDE_SHELL.equals(titan) && !wiped ? 7 : 9;
                waited = 0;
            }
            case 7 -> {
                // 潮殻: 招き潮で砂の上に水 → 全滅で水が引き、中ボスが湧き直す
                TideMidRoom room = (TideMidRoom) run.room;
                room.beckon(back, 100);
                BlockPos w = run.ctx.local(10, 1, 0);
                check(back.getBlockState(w).is(Blocks.WATER), "the beckoning tide did not flood the sand");
                check(room.flooded(back), "flooded() is false during the flood");
                Mob old = room.boss;
                RaidRun.wipe(run, null);
                check(!back.getBlockState(w).is(Blocks.WATER), "the wipe did not drain the tide");
                check(old == null || old.isRemoved(), "the wipe did not remove the boss");
                check(run.ctx.cell().inner().contains(player.position()), "after the wipe the player is not back inside the arena");
                wiped = true;
                stage = 5;
                waited = 0;
            }
            case 9 -> {
                MidRoom room = (MidRoom) run.room;
                if (room.boss != null && room.boss.isAlive()) {
                    room.boss.kill();
                }
                stage = 10;
                waited = 0;
            }
            case 10 -> {
                if (!(RaidRun.midCleared(group) && !TrialRoute.mid(back, group) && TrialRoute.step(back, group) == TrialRoute.LAST_STEP)) {
                    return timeout(100, "the route to move to the heart after the defeat (step " + TrialRoute.step(back, group) + ", mid "
                            + TrialRoute.mid(back, group) + ", cleared " + RaidRun.midCleared(group) + ")");
                }
                check(run.ctx.aliveMobs().isEmpty(), "mobs left in the arena after the defeat");
                DONE.add(titan);
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 一人の体力の倍率 (傷を割る倍率が 1、または体力が 1 人の基準)
    private static boolean scaleOk(Mob boss) {
        if (boss instanceof TideBeckoner) {
            return boss.getMaxHealth() <= TideBeckoner.HEALTH + 0.5f;
        }
        if (boss instanceof jp.revati.skyabyss.titan.storm.raiju.Raiju) {
            return jp.revati.skyabyss.titan.storm.raiju.Raiju.scaleFor(1) == 1f;
        }
        if (boss instanceof jp.revati.skyabyss.titan.ember.smith.AshSmith s) {
            return s.multiplier() <= 1.001f;
        }
        LivingEntity l = boss;
        var base = l.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        return base != null && l.getMaxHealth() <= 280.5f;
    }

    private static void teardown() {
        try {
            if (group != null && back != null) {
                TrialRoute.finish(back, group);
                TrialRuntime.stopGroup(back, group);
                GroupGates.reset(back, group);
                BackGroupData.get(back).remove(group);
            }
        } finally {
            TrialRoute.clearAll();
        }
    }
}
