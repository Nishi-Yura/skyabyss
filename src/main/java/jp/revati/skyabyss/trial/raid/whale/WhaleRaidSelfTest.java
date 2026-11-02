package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialData;
import jp.revati.skyabyss.trial.TrialDraw;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialRuntime;
import jp.revati.skyabyss.trial.TrialSlot;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.RaidRun;

// 岩鯨のレイドの検査 (docs/design/32 の 7 章)。実際の順路を一人で歩かせ、三つの部屋を台本で越える:
//   部屋が固定 (WR1 → WR2 → WR3)・目的の帯の中身・骨玉の重さと戻り・全滅 (と核の崩れ) で入口に戻って部屋が戻る・各部屋が越えられる
// SKYABYSS_SELFTEST_ONLY="raid"。実時間で 1 分ほどかかる (部屋の準備の 3 秒・運びの 4 秒が三回)
public final class WhaleRaidSelfTest {
    private WhaleRaidSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.until("raid whale", WhaleRaidSelfTest::tick));
    }

    private static final List<String> FAILS = new ArrayList<>();
    private static final List<BooleanSupplier> STAGES = new ArrayList<>();
    private static boolean started;
    private static boolean finished;
    private static int stage;
    private static int waited;
    private static ServerLevel back;
    private static FakePlayer player;
    private static UUID group;
    private static List<ServerPlayer> players;
    // 引かれた部屋の id (歩・智・闘の順)。二つずつの部屋のどちらでも、引かれた方を解く
    private static final String[] DRAWN = new String[3];

    private static void check(boolean ok, String what) {
        if (!ok) {
            FAILS.add(what);
        }
    }

    private static boolean waitFor(BooleanSupplier cond, int maxTicks, String what) {
        if (cond.getAsBoolean()) {
            waited = 0;
            return true;
        }
        if (++waited > maxTicks) {
            FAILS.add("timed out waiting for: " + what);
            waited = 0;
            finished = true;
            return true;
        }
        return false;
    }

    private static RaidRun.Run run() {
        return RaidRun.of(group);
    }

    private static boolean hudLive(String id) {
        RaidRun.Run r = run();
        return r != null && r.lastHud != null && r.lastHud.cellId().equals(id) && !r.lastHud.objKey().equals("raid.skyabyss.hud.ready");
    }

    private static boolean active(Class<? extends RaidRoom> type) {
        RaidRun.Run r = run();
        return r != null && type.isInstance(jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(r.room)) && r.phase == RaidRun.Phase.ACTIVE;
    }

    private static boolean tick(MinecraftServer server) {
        if (!started) {
            started = true;
            back = server.getLevel(Titans.backOf(Titans.STONE_WHALE));
            if (back == null || !setup()) {
                SkyAbyss.LOGGER.info("SELFTEST raid whale SKIPPED (the whale back is not built here)");
                teardown();
                return true;
            }
        }
        if (!finished && stage < STAGES.size()) {
            try {
                if (STAGES.get(stage).getAsBoolean()) {
                    stage++;
                }
            } catch (RuntimeException e) {
                SkyAbyss.LOGGER.error("SELFTEST raid whale crashed", e);
                FAILS.add("crashed in stage " + stage + ": " + e);
                finished = true;
            }
        }
        if (finished || stage >= STAGES.size()) {
            teardown();
            if (FAILS.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST raid whale PASSED (the drawn rooms on the route {}, HUD, orb weight/return, bone order, core defense, wipes reset the room)", java.util.Arrays.toString(DRAWN));
            } else {
                for (String s : FAILS) {
                    SkyAbyss.LOGGER.error("SELFTEST raid whale FAILED: {}", s);
                }
            }
            return true;
        }
        return false;
    }

    private static void teardown() {
        try {
            if (group != null && back != null) {
                TrialRoute.finish(back, group);
                GroupGates.reset(back, group);
                BackGroupData.get(back).remove(group);
            }
        } finally {
            TrialRoute.clearAll();
            TrialSlot.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
    }

    // ---------------------------------------------------------------- 準備
    private static boolean setup() {
        AABB entry = TrialRoute.entryBox(Titans.STONE_WHALE);
        if (entry == null) {
            return false;
        }
        back.getChunk(BlockPos.containing(entry.getCenter()));
        TrialRoute.clearAll();
        player = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000ba001"), "raid_whale"));
        group = Groups.keyOf(player);
        players = List.of(player);
        TrialRoute.PLAYERS = l -> players;
        BackGroupData.get(back).remove(group);
        player.setPos(entry.getCenter());
        long now = back.getGameTime();
        TrialRoute.tick(back, Titans.STONE_WHALE, players, now);
        TrialRoute.tick(back, Titans.STONE_WHALE, players, now + TrialRoute.ENTRY_WAIT + 10);
        check(TrialRoute.step(back, group) == 1, "the route did not start (step " + TrialRoute.step(back, group) + ")");
        TrialDraw.Draw draw = TrialData.current(back, group);
        check(RaidRooms.active(Titans.STONE_WHALE), "the whale is not on a raid route");
        String[] ids = {WhaleRaid.WALK, WhaleRaid.WIT, WhaleRaid.FIGHT};
        for (int i = 1; i <= 3; i++) {
            Trial.Def d = TrialRoute.defOf(draw, i);
            Trial.Slot slot = Trial.Slot.values()[i - 1];
            DRAWN[i - 1] = d == null ? null : d.id();
            check(d != null && RaidRooms.idsOf(Titans.STONE_WHALE, slot).contains(d.id()), "step " + i + " is not one of the registered rooms: " + (d == null ? null : d.id()));
            check(java.util.Objects.equals(RaidRooms.drawOf(Titans.STONE_WHALE, slot, 12345L), RaidRooms.drawOf(Titans.STONE_WHALE, slot, 12345L)), "the room draw is not deterministic");
            check(ids[i - 1].equals(d == null ? null : d.id()) || RaidRooms.idsOf(Titans.STONE_WHALE, slot).size() > 1, "step " + i + " (one room per slot) is not " + ids[i - 1]);
        }
        if (!FAILS.isEmpty()) {
            finished = true;
        }
        buildStages();
        return true;
    }

    // ---------------------------------------------------------------- 台本
    private static void buildStages() {
        // ---- WR1 歩「骨玉運び」 (WR4 が引かれたらそちら)
        if (!WhaleRaid.WALK.equals(DRAWN[0])) {
            walk2();
        } else {
        STAGES.add(() -> waitFor(() -> active(WhaleCarryRoom.class), 400, "WR1 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR1"), 60, "WR1 objective strip"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            check(r.step == 1 && r.ctx.cell().bounds().contains(player.position()), "WR1: the player is not inside the room's cell");
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR1") && hud.max() == 3 && !hud.objKey().isEmpty(), "WR1: the objective strip is empty: " + hud);
            giveOrb(0, r, false);
            return true;
        });
        STAGES.add(() -> waitFor(() -> player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(WhaleCarryRoom.SLOW_ID), 60, "carry slow"));
        STAGES.add(() -> {
            check(player.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(WhaleCarryRoom.JUMP_ID), "WR1: the carrier can still jump");
            // 落とす: 遠くに置いた骨玉は、しばらくして元の場所へ戻る
            RaidRun.Run r = run();
            ItemStack orb = takeOrb();
            check(!orb.isEmpty(), "WR1: the carried orb is missing from the inventory");
            BlockPos far = r.ctx.cell().floor(8, 8);
            ItemEntity e = new ItemEntity(back, far.getX() + 0.5, far.getY() + 1, far.getZ() + 0.5, orb);
            e.setUnlimitedLifetime();
            // 試しの人が近くにいても拾い直さないように (前の自己テストのあとだと立ち位置が近いことがある)
            e.setPickUpDelay(WhaleCarryRoom.LINGER + 400);
            back.addFreshEntity(e);
            return true;
        });
        STAGES.add(() -> {
            BlockPos home = ((WhaleCarryRoom) run().room).home(0);
            if (waited == 150) {
                for (ItemEntity e : back.getEntitiesOfClass(ItemEntity.class, run().ctx.cell().bounds().inflate(4))) {
                    SkyAbyss.LOGGER.info("SELFTEST raid whale debug: item {} at {} ground={} vel={} home={} phase={}", idxOf(e.getItem()), e.position(), e.onGround(),
                            e.getDeltaMovement(), home, run().phase);
                }
            }
            return waitFor(() -> orbNear(home, 2.5, 0), WhaleCarryRoom.LINGER + 200, "dropped orb 0 returns home");
        });
        STAGES.add(() -> waitFor(() -> !player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(WhaleCarryRoom.SLOW_ID), 60, "carry slow removed"));
        STAGES.add(() -> {
            giveOrb(0, run(), true);
            return true;
        });
        STAGES.add(() -> waitFor(() -> ((WhaleCarryRoom) run().room).delivered() == 1, 80, "one orb delivered"));
        STAGES.add(() -> {
            // 一つ届けてから全滅 → 入口に戻り、部屋は最初から
            RaidRun.Run r = run();
            BlockPos mid = r.ctx.cell().floor(0, 0);
            player.setPos(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
            RaidRun.wipe(r, null);
            check(r.phase == RaidRun.Phase.COUNTDOWN, "WR1: a wipe did not put the room back to the countdown");
            check(((WhaleCarryRoom) r.room).delivered() == 0, "WR1: a wipe did not reset the delivered count");
            check(player.position().distanceTo(Vec3.atBottomCenterOf(r.ctx.cell().rest())) < 2.5, "WR1: a wipe did not bring the player back to the room's entrance");
            return true;
        });
        STAGES.add(() -> waitFor(() -> active(WhaleCarryRoom.class), 400, "WR1 active again"));
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            STAGES.add(() -> {
                giveOrb(idx, run(), true);
                return true;
            });
            STAGES.add(() -> waitFor(() -> ((WhaleCarryRoom) run().room).delivered() >= idx + 1, 80, "orb " + idx + " delivered"));
        }
        STAGES.add(() -> waitFor(() -> run() != null && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(run().room, null) && false || run().phase == RaidRun.Phase.COMPLETE, 40, "WR1 complete"));

        }
        // ---- WR2 智「共鳴の骨」 (WR5 が引かれたらそちら)
        if (!WhaleRaid.WIT.equals(DRAWN[1])) {
            wit2();
        } else {
        STAGES.add(() -> waitFor(() -> run() != null && run().step == 2 && run().room instanceof WhaleBonesRoom, 400, "carried to WR2"));
        STAGES.add(() -> waitFor(() -> active(WhaleBonesRoom.class), 400, "WR2 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR2"), 60, "WR2 objective strip"));
        STAGES.add(() -> waitFor(() -> !((WhaleBonesRoom) run().room).listening() && !run().room.isComplete(), 600, "WR2 input phase"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleBonesRoom room = (WhaleBonesRoom) r.room;
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR2") && hud.max() == 5, "WR2: the objective strip is wrong: " + hud);
            check(r.ctx.cell().bounds().contains(player.position()), "WR2: the player is not inside the room's cell");
            // 間違い = 聴き直し (傷なし)
            int[] first = room.sequence().get(0);
            int wrong = -1;
            for (int b = 0; b < WhaleBonesRoom.BONES && wrong < 0; b++) {
                boolean in = false;
                for (int w : first) {
                    in |= w == b;
                }
                wrong = in ? -1 : b;
            }
            float hp = player.getHealth();
            room.strike(r.ctx, wrong);
            check(room.mistakes() == 1 && room.listening(), "WR2: a wrong bone did not replay the sequence");
            check(player.getHealth() >= hp, "WR2: a wrong bone hurt the player");
            return true;
        });
        STAGES.add(() -> waitFor(() -> !((WhaleBonesRoom) run().room).listening(), 600, "WR2 input phase again"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleBonesRoom room = (WhaleBonesRoom) r.room;
            check(room.sequence().size() == WhaleBonesRoom.LENGTH, "WR2: the sequence has the wrong length");
            for (int[] chord : new ArrayList<>(room.sequence())) {
                for (int b : chord) {
                    room.strike(r.ctx, b);
                }
            }
            check(room.isComplete(), "WR2: striking the whole sequence in order did not finish the room");
            return true;
        });
        STAGES.add(() -> waitFor(() -> run() != null && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(run().room, null) && false || run().phase == RaidRun.Phase.COMPLETE, 40, "WR2 complete"));

        }
        // ---- WR3 闘「鯨の核を守る」 (WR6 が引かれたらそちら)
        if (!WhaleRaid.FIGHT.equals(DRAWN[2])) {
            fight2();
        } else {
        STAGES.add(() -> waitFor(() -> run() != null && run().step == 3 && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(run().room) instanceof WhaleCoreRoom, 400, "carried to WR3"));
        STAGES.add(() -> waitFor(() -> active(WhaleCoreRoom.class), 400, "WR3 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR3"), 60, "WR3 objective strip"));
        STAGES.add(() -> waitFor(() -> ((WhaleCoreRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(run().room)).wave() >= 1 && !run().ctx.aliveMobs().isEmpty(), 400, "WR3 wave 1"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR3") && hud.secMax() > 0 && hud.max() == 2, "WR3: the objective strip lacks the core bar / wave counter: " + hud);
            check(r.ctx.cell().bounds().contains(player.position()), "WR3: the player is not inside the room's cell");
            // 核が壊れる → 全滅と同じ (入口へ・部屋は戻る)
            ((WhaleCoreRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(r.room)).damageCore(1000);
            return true;
        });
        STAGES.add(() -> waitFor(() -> run().phase == RaidRun.Phase.COUNTDOWN, 40, "WR3 reset after the core broke"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleCoreRoom room = (WhaleCoreRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(r.room);
            check(room.wave() == 0 && room.coreHp() >= WhaleCoreRoom.CORE_MAX - 0.01 && r.ctx.aliveMobs().isEmpty(),
                    "WR3: the room was not reset (wave " + room.wave() + ", core " + room.coreHp() + ")");
            check(r.wipes == 1, "WR3: the wipe was not counted");
            return true;
        });
        STAGES.add(() -> waitFor(() -> active(WhaleCoreRoom.class), 400, "WR3 active again"));
        STAGES.add(() -> {
            // 敵を片付けつづけて三つの波を越える
            RaidRun.Run r = run();
            // 波の敵は片付ける (中ボスの段では中ボスを倒す。片付けると湧き直すので)
            if (!(r.room instanceof jp.revati.skyabyss.trial.raid.mid.FightBossRoom fb && fb.bossStage())) {
                for (Mob m : new ArrayList<>(r.ctx.aliveMobs())) {
                    m.discard();
                }
            }
            return waitFor(() -> jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(r.room, null) && false || r.phase == RaidRun.Phase.COMPLETE, 2400, "WR3 complete (waves)");
        });
        }
    }

    // ---- 二つ目の部屋 (WR4・WR5・WR6) を順路の中で解く
    private static void walk2() {
        STAGES.add(() -> waitFor(() -> run() != null && run().step == 1 && active(WhaleShaftRoom.class), 400, "WR4 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR4"), 60, "WR4 objective strip"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleShaftRoom room = (WhaleShaftRoom) r.room;
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR4") && hud.max() == 4 && hud.secMax() > 0 && !hud.objKey().isEmpty(), "WR4: the objective strip is wrong: " + hud);
            check(r.ctx.cell().bounds().contains(player.position()), "WR4: the player is not inside the room's cell");
            // 階段を一つ出してから全滅 → 入口に戻り、階段は引っ込む
            room.onUse(r.ctx, player, room.masterSwitch(r.ctx, 0), false);
            return true;
        });
        STAGES.add(() -> waitFor(() -> ((WhaleShaftRoom) run().room).grown(0) == WhaleShaftRoom.RISE, 80, "WR4 flight grows"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            RaidRun.wipe(r, null);
            check(r.phase == RaidRun.Phase.COUNTDOWN, "WR4: a wipe did not put the room back to the countdown");
            check(((WhaleShaftRoom) r.room).grown(0) == 0, "WR4: a wipe did not retract the stairs");
            check(player.position().distanceTo(Vec3.atBottomCenterOf(r.ctx.cell().rest())) < 2.5, "WR4: a wipe did not bring the player back to the room's entrance");
            return true;
        });
        STAGES.add(() -> waitFor(() -> active(WhaleShaftRoom.class), 400, "WR4 active again"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleShaftRoom room = (WhaleShaftRoom) r.room;
            for (int f = 0; f < WhaleShaftRoom.FLIGHTS; f++) {
                room.onUse(r.ctx, player, room.localSwitch(r.ctx, f), true);
            }
            BlockPos top = room.landingSpot(r.ctx, WhaleShaftRoom.FLIGHTS);
            player.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
            return true;
        });
        STAGES.add(() -> waitFor(() -> run() != null && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(run().room, null) && false || run().phase == RaidRun.Phase.COMPLETE, 40, "WR4 complete"));
    }

    private static void wit2() {
        STAGES.add(() -> waitFor(() -> run() != null && run().step == 2 && run().room instanceof WhaleTilesRoom, 400, "carried to WR5"));
        STAGES.add(() -> waitFor(() -> active(WhaleTilesRoom.class), 400, "WR5 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR5"), 60, "WR5 objective strip"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleTilesRoom room = (WhaleTilesRoom) r.room;
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR5") && !hud.objKey().isEmpty(), "WR5: the objective strip is wrong: " + hud);
            check(r.ctx.cell().bounds().contains(player.position()), "WR5: the player is not inside the room's cell");
            // 一枚叩いてから全滅 → 入口に戻り、石板は最初の向きへ
            int[][] before = new int[WhaleTilesRoom.COLS][WhaleTilesRoom.ROWS];
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    before[c][rr] = room.currentMask(c, rr);
                }
            }
            room.turn(r.ctx, 3, 2);
            room.turn(r.ctx, 0, 2);
            RaidRun.wipe(r, null);
            check(r.phase == RaidRun.Phase.COUNTDOWN, "WR5: a wipe did not put the room back to the countdown");
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    check(room.currentMask(c, rr) == before[c][rr], "WR5: a wipe did not restore tile " + c + "," + rr);
                }
            }
            check(player.position().distanceTo(Vec3.atBottomCenterOf(r.ctx.cell().rest())) < 2.5, "WR5: a wipe did not bring the player back to the room's entrance");
            return true;
        });
        STAGES.add(() -> waitFor(() -> active(WhaleTilesRoom.class), 400, "WR5 active again"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            WhaleTilesRoom room = (WhaleTilesRoom) r.room;
            for (int c = 0; c < WhaleTilesRoom.COLS; c++) {
                for (int rr = 0; rr < WhaleTilesRoom.ROWS; rr++) {
                    for (int k = room.turnsNeeded(c, rr); k > 0; k--) {
                        room.onUse(r.ctx, player, room.tileCenter(r.ctx, c, rr), false);
                    }
                }
            }
            return true;
        });
        STAGES.add(() -> waitFor(() -> run() != null && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(run().room, null) && false || run().phase == RaidRun.Phase.COMPLETE, 40, "WR5 complete"));
    }

    private static void fight2() {
        STAGES.add(() -> waitFor(() -> run() != null && run().step == 3 && jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(run().room) instanceof WhaleRingRoom, 400, "carried to WR6"));
        STAGES.add(() -> waitFor(() -> active(WhaleRingRoom.class), 400, "WR6 active"));
        STAGES.add(() -> waitFor(() -> hudLive("WR6"), 60, "WR6 objective strip"));
        STAGES.add(() -> waitFor(() -> ((WhaleRingRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(run().room)).wave() >= 1 && !run().ctx.aliveMobs().isEmpty(), 400, "WR6 wave 1"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            var hud = r.lastHud;
            check(hud != null && hud.cellId().equals("WR6") && hud.secMax() > 0 && hud.max() == 2, "WR6: the objective strip lacks the ring size / wave counter: " + hud);
            check(r.ctx.cell().bounds().contains(player.position()), "WR6: the player is not inside the room's cell");
            ((WhaleRingRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(r.room)).rewindShrink(1000);
            RaidRun.wipe(r, null);
            WhaleRingRoom room = (WhaleRingRoom) jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(r.room);
            check(r.phase == RaidRun.Phase.COUNTDOWN && room.wave() == 0 && room.radius() >= WhaleRingRoom.R0 - 0.01 && r.ctx.aliveMobs().isEmpty(),
                    "WR6: a wipe did not reset the room (wave " + room.wave() + ", ring " + room.radius() + ")");
            check(player.position().distanceTo(Vec3.atBottomCenterOf(r.ctx.cell().rest())) < 2.5, "WR6: a wipe did not bring the player back to the room's entrance");
            return true;
        });
        STAGES.add(() -> waitFor(() -> active(WhaleRingRoom.class), 400, "WR6 active again"));
        STAGES.add(() -> {
            RaidRun.Run r = run();
            // 輪の中へ立たせ、敵を片付けつづけて三つの波を越える
            BlockPos mid = r.ctx.cell().floor(0, 0);
            player.setPos(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5);
            // 波の敵は片付ける (中ボスの段では中ボスを倒す。片付けると湧き直すので)
            if (!(r.room instanceof jp.revati.skyabyss.trial.raid.mid.FightBossRoom fb && fb.bossStage())) {
                for (Mob m : new ArrayList<>(r.ctx.aliveMobs())) {
                    m.discard();
                }
            }
            return waitFor(() -> jp.revati.skyabyss.trial.raid.mid.FightBossRoom.finishBoss(r.room, null) && false || r.phase == RaidRun.Phase.COMPLETE, 2400, "WR6 complete (waves)");
        });
    }

    // ---- 骨玉の道具
    private static int idxOf(ItemStack st) {
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return !st.isEmpty() && cd != null && cd.contains(WhaleCarryRoom.ORB_TAG) ? cd.copyTag().getInt(WhaleCarryRoom.ORB_TAG) : -1;
    }

    private static boolean orbNear(BlockPos at, double r, int idx) {
        for (ItemEntity e : back.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(r + 1))) {
            if (idxOf(e.getItem()) == idx && e.distanceToSqr(at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5) <= r * r) {
                return true;
            }
        }
        return false;
    }

    // 床の骨玉 idx を拾ったことにする。toAltar なら台座の前へ運ぶ
    private static void giveOrb(int idx, RaidRun.Run r, boolean toAltar) {
        for (ItemEntity e : back.getEntitiesOfClass(ItemEntity.class, r.ctx.cell().inner().inflate(2))) {
            if (idxOf(e.getItem()) == idx) {
                ItemStack st = e.getItem().copy();
                e.discard();
                player.getInventory().add(st);
                if (toAltar) {
                    BlockPos altar = ((WhaleCarryRoom) r.room).altar(r.ctx);
                    player.setPos(altar.getX() + 0.5, altar.getY(), altar.getZ() + 2.0);
                }
                return;
            }
        }
        FAILS.add("WR1: the floor orb " + idx + " was not found");
    }

    private static ItemStack takeOrb() {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (idxOf(st) >= 0) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                return st;
            }
        }
        return ItemStack.EMPTY;
    }
}
