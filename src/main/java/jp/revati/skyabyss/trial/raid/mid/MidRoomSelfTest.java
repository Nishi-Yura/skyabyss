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
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
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
//   → 部屋ごとの出方 (32 の 9.5 の表): 番兵長が核を削る・輪の外の番兵長が削れる・苗の台が乾いたまま・影の潮招き二体 (叩くと弾ける)・
//     連鎖の雷が雷獣に当たる・突風が吹き続ける・鍛え手が炉を削る・師の名
//   → (潮殻だけ) 招き潮で部屋の砂に水、全滅で水が引き部屋が波の段へ戻る
//   闘の部屋は巨神ごとに二つ。引きを決めて (RaidRooms.DEV_DRAW) 八つとも通す
//   → 倒すと越え、順路が心臓の段 (LAST_STEP) へ進む
public final class MidRoomSelfTest {
    private MidRoomSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.until("raid mid", MidRoomSelfTest::tick));
    }

    private static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};
    // (巨神, 闘の部屋の id)
    private static final List<String[]> COMBOS = new ArrayList<>();
    private static String fightId;
    private static float measure;
    private static long measureAt;
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
        RaidRooms.DEV_DRAW.clear();
        if (FAILS.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid mid PASSED ({}: no separate mid-boss step; in the fight room the boss appears after the waves, single-stage at 70% health, "
                    + "solo damage lands, each room's mechanic works on the boss, tide flood in the room + wipe back to the waves, defeat moves the route to the heart)", DONE);
        } else {
            for (String f : FAILS) {
                SkyAbyss.LOGGER.error("SELFTEST raid mid FAILED: {}", f);
            }
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILS.add(fightId + ": " + what);
        }
    }

    // 待つ。時間切れなら失敗にして、この巨神を終える (true)
    private static boolean timeout(int max, String what) {
        if (++waited > max) {
            FAILS.add(fightId + ": timed out waiting for " + what);
            stage = 99;
            return true;
        }
        return false;
    }

    private static boolean next(MinecraftServer server) {
        if (index < 0 && COMBOS.isEmpty()) {
            for (String t : TITANS) {
                for (String id : RaidRooms.idsOf(t, jp.revati.skyabyss.trial.Trial.Slot.FIGHT)) {
                    COMBOS.add(new String[] {t, id});
                }
            }
        }
        while (++index < COMBOS.size()) {
            titan = COMBOS.get(index)[0];
            fightId = COMBOS.get(index)[1];
            back = server.getLevel(Titans.backOf(titan));
            if (back == null || !RaidRooms.active(titan) || MidRooms.idOf(titan) == null) {
                SkyAbyss.LOGGER.info("SELFTEST raid mid: {} skipped (no back or no raid route)", fightId);
                continue;
            }
            RaidRooms.DEV_DRAW.clear();
            RaidRooms.DEV_DRAW.put(titan + ":" + jp.revati.skyabyss.trial.Trial.Slot.FIGHT.name(), fightId);
            TrialRoute.clearAll();
            // 世界に入れた偽の人 (中ボスが狙えるように。潮招きは狙う人がいないと砂に潜ったまま出てこない)
            if (player != null && !player.isRemoved()) {
                player.discard();
            }
            player = new net.neoforged.neoforge.common.util.FakePlayer(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000a1d" + Integer.toHexString(0x10 + index)), "raid_mid_" + index)) {
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
                check(fightId.equals(run.ctx.roomId()), "the forced fight room was not drawn (" + run.ctx.roomId() + ")");
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
                stage = wiped ? 9 : 8;
                measureAt = -1;
                waited = 0;
            }
            case 8 -> {
                // 部屋ごとの出方
                FightBossRoom fb = (FightBossRoom) run.room;
                Mob boss = fb.boss();
                if (boss == null) {
                    return timeout(100, "the boss");
                }
                if (!roomMechanic(run, fb, boss)) {
                    return false;
                }
                if (stage == 99) {
                    return true;
                }
                stage = Titans.TIDE_SHELL.equals(titan) ? 7 : 9;
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
                DONE.add(fightId);
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    // 部屋ごとの出方を一つ確かめる。終わったら true (待つあいだ false)
    private static boolean roomMechanic(RaidRun.Run run, FightBossRoom fb, Mob boss) {
        RaidCtx ctx = run.ctx;
        RaidRoom inner = fb.inner();
        BlockPos far = ctx.cell().floor(0, ctx.cell().hz() - 2);
        switch (fightId) {
            case "WR3" -> {
                // 番兵長が核のそばで核を削る (人は遠く)
                var core = (jp.revati.skyabyss.trial.raid.whale.WhaleCoreRoom) inner;
                check(core.counterMax() == 2, "WR3 should have two waves before the captain (" + core.counterMax() + ")");
                check(inner.bossKeepsSecondary() && fb.secondaryKey().equals("raid.skyabyss.WR3.core"), "the core health is not on the strip in the boss stage");
                player.setPos(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                BlockPos c = ctx.cell().floor(2, 2);
                boss.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                if (measureAt < 0) {
                    measure = (float) core.coreHp();
                    measureAt = 0;
                }
                if (core.coreHp() < measure - 0.5) {
                    return true;
                }
                return timeout(120, "the captain to wear down the core");
            }
            case "WR6" -> {
                // 輪の外の番兵長は体力の割合で削れる
                var ring = (jp.revati.skyabyss.trial.raid.whale.WhaleRingRoom) inner;
                check(ring.counterMax() == 2, "WR6 should have two waves before the captain (" + ring.counterMax() + ")");
                ring.rewindShrink(20000);
                player.setPos(ctx.origin().getX() + 0.5, ctx.cell().floor(0, 0).getY(), ctx.origin().getZ() + 0.5);
                BlockPos out = ctx.cell().floor(10, -10);
                boss.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
                if (measureAt < 0) {
                    measure = boss.getHealth();
                    measureAt = 0;
                }
                if (boss.getHealth() < measure - boss.getMaxHealth() * 0.02f) {
                    return true;
                }
                return timeout(80, "the captain to lose health outside the ring");
            }
            case "TR3" -> {
                // 苗の台が招き潮の逃げ場 (台の上は乾いたまま)
                TideMidRoom room = (TideMidRoom) fb.midRoom();
                check(room.usesBeds(), "the sapling beds are not the tide refuges");
                room.beckon(back, 60);
                BlockPos bed = ctx.local(-8 + 1, 2, -3);
                check(!back.getBlockState(bed).isAir() && !back.getBlockState(bed).is(Blocks.WATER), "the sapling bed was not raised: " + back.getBlockState(bed));
                check(!back.getBlockState(bed.above()).is(Blocks.WATER), "water on top of a sapling bed");
                check(waterAt(ctx.cell().inner(), ctx.cell().floor(0, 0).getY()), "the beckoning tide did not flood the sand");
                return true;
            }
            case "TR7" -> {
                // 影の潮招きが二体 (技なし)。叩くと弾ける
                var brood = (jp.revati.skyabyss.trial.raid.tide.TideBroodRoom) inner;
                if (measureAt < 0) {
                    check(brood.decoys().size() + brood.pendingRespawns() == 2, "there are not two shadow beckoners (" + brood.decoys().size() + " + " + brood.pendingRespawns() + ")");
                    for (Mob d : brood.decoys()) {
                        check(d instanceof TideBeckoner b && b.isShadow(), "a shadow is not a shadow beckoner: " + d);
                    }
                    if (brood.decoys().isEmpty()) {
                        return true;
                    }
                    Mob d = brood.decoys().get(0);
                    d.invulnerableTime = 0;
                    d.hurt(back.damageSources().playerAttack(player), 4f);
                    measureAt = 0;
                    return false;
                }
                if (brood.pendingRespawns() >= 1) {
                    return true;
                }
                Mob d = brood.decoys().isEmpty() ? null : brood.decoys().get(0);
                if (d != null && waited % 20 == 0) {
                    d.invulnerableTime = 0;
                    d.hurt(back.damageSources().playerAttack(player), 4f);
                }
                return timeout(100, "a hit shadow beckoner to pop");
            }
            case "SR3" -> {
                // 光る敵が倒れた所から跳んだ雷が、雷獣に当たる (柱を渡っている間は待つ)
                var chain = (jp.revati.skyabyss.trial.raid.storm.ChargeChain) inner;
                check(chain.boss() == boss, "the charge chain room does not know the raiju");
                float before = boss.getHealth();
                boss.invulnerableTime = 0;
                chain.chain(ctx, boss.position().add(1, 0.5, 0), null);
                if (boss.getHealth() < before) {
                    return true;
                }
                return timeout(400, "the chain lightning to hurt the raiju");
            }
            case "SR6" -> {
                // 突風は中ボスの段でも吹き続ける (年が進み、突風までの残りが帯に出る)
                var eye = (jp.revati.skyabyss.trial.raid.storm.EyeAdvance) inner;
                if (measureAt < 0) {
                    measureAt = eye.age();
                    return false;
                }
                if (eye.age() >= measureAt + 20) {
                    check(fb.midRoom().timerTicks() >= 0 || fb.timerTicks() == eye.bossTimerTicks(), "the gust timer is not on the strip in the boss stage");
                    return true;
                }
                return timeout(60, "the gusts to keep going in the boss stage");
            }
            case "ER3" -> {
                // 鍛え手が炉のそばで炉の火を削る (人は遠く)
                var forge = (jp.revati.skyabyss.trial.raid.ember.EmberForgeDefense) inner;
                check(forge.counterMax() == 2, "ER3 should have two waves before the smith (" + forge.counterMax() + ")");
                check(fb.secondaryKey().equals("raid.skyabyss.ER3.forge"), "the forge health is not on the strip in the boss stage");
                player.setPos(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
                BlockPos f = forge.forgePos();
                boss.moveTo(f.getX() + 3.0, f.getY() + 1, f.getZ() + 0.5);
                if (measureAt < 0) {
                    measure = forge.forgeHp();
                    measureAt = 0;
                }
                if (forge.forgeHp() < measure) {
                    return true;
                }
                return timeout(120, "the smith to douse the forge");
            }
            case "ER6" -> {
                // 四人目の代わりに師
                check(boss.getCustomName() != null && boss.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("raid.skyabyss.ER6.master"), "the smith is not named the master");
                check(inner.counterMax() == 4, "the apprentice count changed");
                return true;
            }
            default -> {
                return true;
            }
        }
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
