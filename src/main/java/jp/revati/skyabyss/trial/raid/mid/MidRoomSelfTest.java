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

// 闘の部屋の中ボス (trial/raid/mid/FightBossRoom、00 の 277) の検査。SKYABYSS_SELFTEST_ONLY="raid mid"。四体の巨神ごとに、一人の偽の人で:
//   順路を始める → 部屋 1・2 を越えたことにする → 三つ目の闘の部屋 (中ボスだけの段は無い)
//   → 波を越えたことにすると中ボスが部屋に現れる (段なし・体力 7 割)・一人の攻撃で削れる
//   → (潮殻だけ) 招き潮で部屋の砂に水、全滅で水が引き部屋が波の段へ戻る
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
            SkyAbyss.LOGGER.info("SELFTEST raid mid PASSED ({}: no separate mid-boss step; in the fight room the boss appears after the waves, single-stage at 70% health, "
                    + "solo damage lands, tide flood in the room + wipe back to the waves, defeat moves the route to the heart)", DONE);
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
            // 世界に入れた偽の人 (中ボスが狙えるように。潮招きは狙う人がいないと砂に潜ったまま出てこない)
            if (player != null && !player.isRemoved()) {
                player.discard();
            }
            player = new net.neoforged.neoforge.common.util.FakePlayer(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000a1d" + index), "raid_mid_" + index)) {
                @Override
                public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                    return false;
                }
            };
            back.addFreshEntity(player);
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
        // 偽の人は避けない。体力を毎 tick 戻す (中ボスの技で全滅して準備に戻らないように。全滅の決まりは潮殻の段で直に見る)
        if (player != null) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        }
        switch (stage) {
            case 0 -> {
                TrialRoute.start(back, group, titan, List.of(player));
                check(TrialRoute.step(back, group) == 1, "the route did not start");
                stage = 1;
            }
            case 1, 2 -> {
                // 部屋 stage を越えたことにして、次へすぐ運ぶ
                if (TrialRoute.step(back, group) != stage) {
                    check(false, "unexpected step " + TrialRoute.step(back, group) + " before passing room " + stage);
                    return true;
                }
                TrialRoute.devPass(back, group, titan, player);
                TrialRoute.flushPending();
                TrialRoute.tick(back, titan, List.of(player), back.getGameTime());
                stage++;
                waited = 0;
            }
            case 3 -> {
                // 三つ目 = 闘の部屋。中ボスの段を包んだ部屋で、準備のあと始まる
                if (run == null || run.step != 3 || run.phase != RaidRun.Phase.ACTIVE) {
                    return timeout(400, "the fight room (step 3) to start");
                }
                check(!TrialRoute.mid(back, group), "the route still has a separate mid-boss step");
                check(run.room instanceof FightBossRoom, "the fight room is not wrapped with its mid-boss (" + run.room.getClass().getSimpleName() + ")");
                if (!(run.room instanceof FightBossRoom fb)) {
                    return true;
                }
                check(!fb.bossStage() && fb.boss() == null, "the mid-boss is out before the waves");
                // 波を越えたことにする → 中ボスが部屋に現れる
                fb.devBeginBoss(run.ctx);
                stage = 5;
                waited = 0;
            }
            case 5 -> {
                FightBossRoom fb = (FightBossRoom) run.room;
                Mob boss = fb.boss();
                if (boss == null) {
                    return timeout(200, "the boss to appear in the fight room");
                }
                check(boss.isAlive() && run.ctx.cell().inner().inflate(0.5, 8, 0.5).contains(boss.position()), "the boss is not inside the fight room: " + boss.position());
                check(boss.getTags().contains(FightBossRoom.SINGLE_STAGE), "the boss is not single-stage");
                check(run.lastHud != null && !run.lastHud.objKey().isEmpty(), "no objective strip in the boss stage");
                stage = 6;
                waited = 0;
            }
            case 6 -> {
                FightBossRoom fb = (FightBossRoom) run.room;
                Mob boss = fb.boss();
                if (boss == null) {
                    return timeout(100, "the boss");
                }
                // 一人の攻撃で削れる (潜っている・柱の上などで通らない間は待つ)
                float before = boss.getHealth();
                player.setPos(boss.getX() + 1.5, boss.getY(), boss.getZ());
                boss.invulnerableTime = 0;
                boss.hurt(back.damageSources().playerAttack(player), 20f);
                if (boss.getHealth() >= before) {
                    return timeout(400, "a solo hit to damage the boss"); // 潮招きは潜る・守りの間 (数秒) は傷が通らない
                }
                check(scaleOk(boss), "the boss is not 70% of the one-player health");
                stage = Titans.TIDE_SHELL.equals(titan) && !wiped ? 7 : 9;
                waited = 0;
            }
            case 7 -> {
                // 潮殻: 招き潮で部屋の砂の上に水 → 全滅で水が引き、部屋が波の段へ戻る (中ボスは消える)
                FightBossRoom fb = (FightBossRoom) run.room;
                TideMidRoom room = (TideMidRoom) fb.midRoom();
                room.beckon(back, 100);
                check(room.flooded(back), "flooded() is false during the flood");
                int fy = run.ctx.cell().floor(0, 0).getY();
                net.minecraft.world.phys.AABB in = run.ctx.cell().inner();
                check(waterAt(in, fy), "the beckoning tide did not flood the fight room's sand");
                Mob old = fb.boss();
                RaidRun.wipe(run, null);
                check(old == null || old.isRemoved(), "the wipe did not remove the boss");
                check(!fb.bossStage(), "the wipe did not take the room back to the waves");
                check(!waterAt(in, fy), "the wipe did not drain the tide");
                wiped = true;
                stage = 3;
                waited = 0;
            }
            case 9 -> {
                FightBossRoom.finishBoss(run.room, player);
                stage = 10;
                waited = 0;
            }
            case 10 -> {
                if (TrialRoute.step(back, group) != TrialRoute.LAST_STEP) {
                    return timeout(200, "the route to move to the heart after the defeat (step " + TrialRoute.step(back, group) + ")");
                }
                check(!TrialRoute.mid(back, group), "a mid-boss step appeared after the fight room");
                DONE.add(titan);
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 部屋の床の上 (fy) に水があるか
    private static boolean waterAt(net.minecraft.world.phys.AABB in, int fy) {
        for (int x = (int) Math.floor(in.minX); x < in.maxX; x++) {
            for (int z = (int) Math.floor(in.minZ); z < in.maxZ; z++) {
                if (back.getBlockState(new BlockPos(x, fy, z)).is(Blocks.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 一人の体力の倍率 (傷を割る倍率が 1、または体力が 1 人の基準)
    private static boolean scaleOk(Mob boss) {
        if (boss instanceof TideBeckoner) {
            return boss.getMaxHealth() <= TideBeckoner.HEALTH * FightBossRoom.HEALTH + 0.5f;
        }
        if (boss instanceof jp.revati.skyabyss.titan.storm.raiju.Raiju) {
            return jp.revati.skyabyss.titan.storm.raiju.Raiju.scaleFor(1) == 1f;
        }
        if (boss instanceof jp.revati.skyabyss.titan.ember.smith.AshSmith s) {
            return s.multiplier() <= 1.001f;
        }
        LivingEntity l = boss;
        var base = l.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        return base != null && l.getMaxHealth() <= 280 * FightBossRoom.HEALTH + 0.5f;
    }

    private static void teardown() {
        try {
            if (group != null && back != null) {
                TrialRoute.finish(back, group);
                GroupGates.reset(back, group);
                BackGroupData.get(back).remove(group);
            }
        } finally {
            if (player != null && !player.isRemoved()) {
                player.discard();
            }
            TrialRoute.clearAll();
        }
    }
}
