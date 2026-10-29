package jp.revati.skyabyss.story.ch6.rift;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch6.MoonClock;
import jp.revati.skyabyss.story.ch6.MoonEnd;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.cliff.LightBody;
import jp.revati.skyabyss.story.ch6.field.MomentStop;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.RiftArmor;
import jp.revati.skyabyss.story.ch6.mob.RiftMob;
import jp.revati.skyabyss.story.ch6.mob.RiftWing;
import jp.revati.skyabyss.story.ch6.mob.WaningBlade;
import jp.revati.skyabyss.story.ch6.valley.ShadowBridgeBlock;
import jp.revati.skyabyss.story.ch6.valley.StarLamps;
import jp.revati.skyabyss.story.ch6.valley.ValleyBlocks;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 下弦の淵の自己テスト (docs/design/46 の 4.6)。段: "moon rift"
//   淵が流し込まれる (野営・板の門・渡り・回廊・主の間) / 印の所の仕掛け (欠片の台 3・鏡 3・板 3・格子・門・帰り口・溜まり・帰りの台・影の橋) /
//   渡りの形 (島は縁から軽身でも届かず、残像の頭からなら届く) / 自分の残像 (覚えた所をなぞる・乗れる・板を踏む・一瞬止めで 5 秒・星灯りで消える) /
//   二つの板の門 (残像二つで開く) / 影の橋 (星灯りで消える) / 落ちた人と主の間の守り / 頼み main_moon_rift と「ここまで」の札 /
//   敵の数字 (区画 4 の倍率)・残り鎧の前の守り / 敵の残像 (3 秒前の所に残り影 → 40% で立つ・残像は残像を残さない・光の中は立たない) /
//   残月の剣守 (呼べる・主の残像がつく・残像は傷を受けず一瞬止めで止まらない・残像の刃で主が崩れる・崩れは傷 ×1.6・段 3 の床の崩れと片付け)
public final class RiftSelfTest {
    static final double DOUBLE_JUMP_REACH_UP9 = 13.0;

    private RiftSelfTest() {
    }

    private static final List<int[]> FORCED = new ArrayList<>();

    // 先の段: 淵を流し込み、使う所の塊を読み込んでおく (加えたばかりの生き物が、同じ tick の内に探しても見つからないため)
    public static void load(MinecraftServer server) {
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        if (moon == null) {
            return;
        }
        moon.getChunk(MoonWorld.ARRIVAL);
        MoonWorld.build(moon);
        for (String k : new String[] {"camp", "door_entry", "door_plate_b", "leap_entry", "shard_leap", "hall_floor", "shard_hall", "arena_center",
                "gate_front"}) {
            BlockPos m = MoonRift.mark(k);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    moon.setChunkForced((m.getX() >> 4) + dx, (m.getZ() >> 4) + dz, true);
                    FORCED.add(new int[] {(m.getX() >> 4) + dx, (m.getZ() >> 4) + dz});
                }
            }
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<Entity> spawned = new ArrayList<>();
        ServerLevel moon = server.getLevel(MoonWorld.LEVEL);
        try {
            check(f, moon != null, "the moon dimension is missing");
            check(f, QuestManager.quest(RiftPuzzles.QUEST) != null, "main_moon_rift is not loaded");
            if (moon == null) {
                report(f);
                return;
            }
            long t0 = System.currentTimeMillis();
            moon.getChunk(MoonWorld.ARRIVAL);
            MoonWorld.build(moon);
            check(f, MoonWorld.riftBuilt(server), "the rift was not marked as built");
            SkyAbyss.LOGGER.info("SELFTEST moon rift: build took {} ms", System.currentTimeMillis() - t0);
            // 地形: 立てる所
            for (String k : new String[] {"camp", "door_entry", "door_plate_a", "door_plate_b", "leap_entry", "leap_edge", "hall_floor", "gate_front",
                    "arena_entry", "arena_center", "past_self"}) {
                check(f, standable(moon, MoonRift.mark(k)), k + " is not standable at " + MoonRift.mark(k));
            }
            // 仕掛け
            for (String s : MoonRift.sites()) {
                check(f, moon.getBlockState(MoonRift.mark("shard_" + s)).is(ValleyBlocks.STAR_PEDESTAL.get()), "pedestal missing at " + s);
            }
            for (String k : new String[] {"camp_mirror", "door_mirror", "leap_mirror"}) {
                check(f, moon.getBlockState(MoonRift.mark(k)).is(RiftBlocks.ECHO_MIRROR.get()), "mirror missing at " + k);
            }
            for (String k : new String[] {"camp_plate", "door_plate_a", "door_plate_b"}) {
                check(f, moon.getBlockState(MoonRift.mark(k)).is(RiftBlocks.ECHO_PLATE.get()), "plate missing at " + k);
            }
            check(f, moon.getBlockState(MoonRift.mark("door_gate")).is(Blocks.IRON_BARS), "the door bars are missing");
            check(f, moon.getBlockState(MoonRift.mark("rift_gate")).is(ValleyBlocks.STAR_GATE.get()), "the waning gate is missing");
            check(f, moon.getBlockState(MoonRift.mark("arena_back")).is(ValleyBlocks.STAR_GATE.get()), "the arena return gate is missing");
            check(f, moon.getBlockState(MoonRift.mark("return_stone")).is(Blocks.LODESTONE), "camp return stone missing");
            check(f, moon.getBlockState(MoonRift.mark("star_pool").above()).is(ValleyBlocks.STAR_POOL.get()), "moon pool missing");
            long bridges = MoonRift.shadow().stream().filter(p -> moon.getBlockState(p).getBlock() instanceof ShadowBridgeBlock).count();
            check(f, bridges == MoonRift.shadow().size() && bridges >= 12, "shadow bridges: " + bridges + " of " + MoonRift.shadow().size());
            // 渡りの形: 島の上は縁より 9 高く、縁から島の端まで軽身で届く距離 (+9 で 13) より遠い。残像の頭 (6.5 上・6.3 先) から島へは届く
            BlockPos edge = MoonRift.mark("leap_edge");
            BlockPos isle = MoonRift.mark("shard_leap");
            double up = isle.getY() - edge.getY();
            double gap = Math.hypot(isle.getX() - edge.getX(), isle.getZ() - edge.getZ()) - 3.5;
            check(f, up >= 8 && gap > DOUBLE_JUMP_REACH_UP9 + 1 && gap - 6.3 < 17.5, "the island leap: up " + up + ", gap " + gap);
            double plates = Math.sqrt(MoonRift.mark("door_plate_a").distSqr(MoonRift.mark("door_plate_b")));
            check(f, plates >= 16, "the two plates are too close: " + plates);
            // 自分の残像: 覚えた所をなぞる・乗れる・板を踏む・一瞬止めで 5 秒止まる・星灯りで消える
            FakePlayer me = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_echo"));
            BlockPos pb = MoonRift.mark("door_plate_b");
            List<EchoShade.Frame> stay = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                stay.add(new EchoShade.Frame(new Vec3(pb.getX() + 0.5, pb.getY(), pb.getZ() + 0.5), 0f, 0f));
            }
            RiftEcho.storeForTest(me, MoonRift.mark("door_mirror"), stay);
            EchoShade shade = RiftEcho.releaseFor(me);
            spawned.add(shade);
            check(f, shade != null && shade.canBeCollidedWith() && shade.replaying(), "the afterimage did not appear or cannot be stood on");
            if (shade != null) {
                shade.tick();
                check(f, RiftTicker.pressed(moon, pb), "the afterimage does not press the plate");
                FakePlayer caster = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_caster"));
                caster.moveTo(shade.getX() + 2, shade.getY(), shade.getZ());
                MomentStop.cast(caster);
                check(f, MomentStop.frozen(shade), "moment stop did not freeze the afterimage");
                // 二つの板の門: もう一つの残像を板 A に
                FakePlayer me2 = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_echo2"));
                BlockPos pa = MoonRift.mark("door_plate_a");
                List<EchoShade.Frame> stayA = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    stayA.add(new EchoShade.Frame(new Vec3(pa.getX() + 0.5, pa.getY(), pa.getZ() + 0.5), 0f, 0f));
                }
                EchoShade shadeA = EchoShade.replay(moon, me2, stayA);
                spawned.add(shadeA);
                check(f, !RiftTicker.doorOpen(), "the door was open before both plates");
                RiftTicker.plates(moon);
                check(f, RiftTicker.doorOpen() && moon.getBlockState(MoonRift.mark("door_gate")).isAir(), "two pressed plates did not open the door");
                if (shadeA != null) {
                    shadeA.discard();
                }
                RiftTicker.door(moon, false);
                // 星灯りの中に入った残像は消える
                StarLamps.place(moon, pb, me.getUUID());
                MomentStop.freeze(shade, moon.getGameTime());
                shade.tick();
                check(f, !shade.isAlive(), "the afterimage did not vanish in starlight");
                StarLamps.clear(moon);
            }
            // 影の橋: 星灯りの 6 の内は消える
            BlockPos bridge = MoonRift.shadow().get(MoonRift.shadow().size() / 2);
            FakePlayer lamp = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_lamp"));
            StarLamps.place(moon, bridge.above(), lamp.getUUID());
            RiftTicker.bridges(moon, true);
            boolean gone = !moon.getBlockState(bridge).getValue(ShadowBridgeBlock.SOLID);
            StarLamps.clear(moon);
            RiftTicker.bridges(moon, true);
            boolean back = moon.getBlockState(bridge).getValue(ShadowBridgeBlock.SOLID);
            check(f, gone && back, "the shadow bridge did not fade in starlight and come back: gone " + gone + ", back " + back);
            // 落ちた人と主の間の守り
            FakePlayer faller = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_faller"));
            BlockPos ec = MoonRift.mark("leap_edge");
            faller.moveTo(ec.getX() + 8.5, -10, ec.getZ() + 6.5);
            check(f, MoonRift.overChasm(faller.position()) && RiftTicker.fell(faller) && !MoonRift.overChasm(faller.position()),
                    "a fall into the rift did not return to an entry: " + faller.blockPosition());
            FakePlayer hall = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_hall"));
            BlockPos hf = MoonRift.mark("hall_floor");
            hall.moveTo(hf.getX() + 0.5, hf.getY(), hf.getZ() + 0.5);
            check(f, MoonRift.inHall(hall.position()) && !RiftTicker.fell(hall), "standing in the lamp gallery counted as a fall");
            FakePlayer onBridge = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_bridge"));
            BlockPos ae = MoonRift.mark("arena_entry");
            onBridge.moveTo(ae.getX() - 3.5, ae.getY() + 2, ae.getZ() + 0.5);
            check(f, !RiftTicker.fell(onBridge), "the stone stair to the arena counted as a fall");
            FakePlayer sneak = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_sneak"));
            sneak.moveTo(ae.getX() + 0.5, ae.getY(), ae.getZ() + 0.5);
            check(f, RiftTicker.guardArena(sneak) && !MoonRift.inArenaZone(sneak.position()), "a player without the shards stayed in the arena");
            // 頼みの通し: 会う → 月の溜まり → 欠片 3 → 門 → 主 → 約束。「ここまで」の札は淵のあと
            FakePlayer p = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift"));
            check(f, !RiftPuzzles.mayCross(p), "a player without shards may cross the waning gate");
            String role = TownGuests.ROLE_PREFIX + RiftTicker.PAST_SELF;
            check(f, Quests.start(p, RiftPuzzles.QUEST), "main_moon_rift would not start");
            Quests.onTalk(p, role, "ch6_rift_meet3");
            check(f, RiftPuzzles.stage(p) == RiftPuzzles.stageIndex("pool"), "talking at the camp did not move to pool: " + RiftPuzzles.stage(p));
            RiftPuzzles.pool(p, MoonRift.mark("star_pool"));
            check(f, LightBody.learned(p) && MomentStop.learned(p), "the moon pool did not bring back the arts");
            check(f, RiftPuzzles.stage(p) == RiftPuzzles.stageIndex("shards") && RiftPuzzles.shards(p) == 0, "the moon pool did not move to shards");
            RiftPuzzles.claim(p, MoonRift.mark("shard_door"));
            RiftPuzzles.claim(p, MoonRift.mark("shard_leap"));
            check(f, RiftPuzzles.shards(p) == 2 && !RiftPuzzles.mayCross(p), "two shards: count " + RiftPuzzles.shards(p));
            RiftPuzzles.claim(p, MoonRift.mark("shard_hall"));
            check(f, RiftPuzzles.mayCross(p), "three shards did not open the waning gate");
            Quests.signal(p, RiftPuzzles.GATE);
            Quests.signal(p, RiftPuzzles.LORD);
            check(f, RiftPuzzles.stage(p) == RiftPuzzles.stageIndex("promise"), "the lord signal did not move to promise: " + RiftPuzzles.stage(p));
            check(f, !MoonEnd.due(p), "the moon end card came before the rift was done");
            Quests.onTalk(p, role, "ch6_rift_promise3");
            QuestLog.Entry done = QuestLog.of(p).entry(RiftPuzzles.QUEST);
            check(f, done != null && done.done, "the promise did not finish main_moon_rift");
            check(f, MoonEnd.due(p), "the moon end card is not due after the rift");
            // 敵の数字 (下弦でない位相) と残り鎧の前の守り
            MoonClock.skipTo(moon, 0);
            BlockPos near = MoonRift.mark("camp").offset(30, 0, -20);
            RiftWing wing = (RiftWing) RiftTicker.put(moon, MoonMobsSetup.RIFT_WING.get(), standableNear(moon, near));
            spawned.add(wing);
            double want = RiftWing.HP * MoonMob.SECTION_HP[3];
            check(f, wing != null && Math.abs(wing.getMaxHealth() - want) < 0.01 && wing.isNoGravity(), "wing hp " + (wing == null ? -1 : wing.getMaxHealth()));
            RiftArmor armor = (RiftArmor) RiftTicker.put(moon, MoonMobsSetup.RIFT_ARMOR.get(), standableNear(moon, near.offset(4, 0, 0)));
            spawned.add(armor);
            armor.setYRot(0f);
            armor.yBodyRot = 0f;
            FakePlayer front = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_front"));
            front.moveTo(armor.getX(), armor.getY(), armor.getZ() + 2);
            FakePlayer behind = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_behind"));
            behind.moveTo(armor.getX(), armor.getY(), armor.getZ() - 2);
            check(f, armor.fromFront(moon.damageSources().playerAttack(front)) && !armor.fromFront(moon.damageSources().playerAttack(behind)),
                    "the remnant armor does not tell front from back");
            // 敵の残像: 倒れると残り影 → 40% で立つ。残像は残像を残さない
            RiftRemnants.clear();
            armor.kill();
            check(f, RiftRemnants.pending() == 1, "a fallen remnant armor left no remnant: " + RiftRemnants.pending());
            for (int i = 0; i < RiftRemnants.MARK; i++) {
                RiftRemnants.tick(moon);
            }
            List<RiftArmor> echoes = moon.getEntitiesOfClass(RiftArmor.class, new AABB(armor.blockPosition()).inflate(8), e -> e.isAlive() && e.echo());
            spawned.addAll(echoes);
            double ehp = RiftArmor.HP * MoonMob.SECTION_HP[3] * RiftMob.ECHO_HP;
            check(f, echoes.size() == 1 && Math.abs(echoes.get(0).getMaxHealth() - ehp) < 0.01, "the armor afterimage: " + echoes.size()
                    + (echoes.isEmpty() ? "" : " hp " + echoes.get(0).getMaxHealth()));
            if (!echoes.isEmpty()) {
                echoes.get(0).kill();
                check(f, RiftRemnants.pending() == 0, "an afterimage left another remnant");
            }
            // 光の中の残り影は立たない
            FakePlayer lit = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_lit"));
            BlockPos lp = standableNear(moon, near.offset(-6, 0, 4));
            StarLamps.place(moon, lp, lit.getUUID());
            RiftRemnants.mark(moon, MoonMobsSetup.RIFT_ARMOR.get(), lp.getBottomCenter(), null);
            for (int i = 0; i < RiftRemnants.MARK; i++) {
                RiftRemnants.tick(moon);
            }
            int litEchoes = moon.getEntitiesOfClass(RiftArmor.class, new AABB(lp).inflate(3), e -> e.isAlive() && e.echo()).size();
            check(f, litEchoes == 0, "a remnant rose inside starlight");
            StarLamps.clear(moon);
            // 残月の剣守
            WaningBlade lord = RiftArena.summon(moon);
            spawned.add(lord);
            check(f, lord != null && lord.getMaxHealth() >= WaningBlade.HP - 0.01 && lord.getMaxHealth() <= 1024,
                    "the waning blade did not appear with its hp: " + (lord == null ? -1 : lord.getMaxHealth()));
            if (lord != null) {
                List<WaningBlade> ghosts = moon.getEntitiesOfClass(WaningBlade.class, RiftArena.box(), e -> e.isAlive() && e.echo());
                check(f, ghosts.size() == 1 && ghosts.get(0).delay() == WaningBlade.DELAY, "the blade's afterimage did not come: " + ghosts.size());
                if (!ghosts.isEmpty()) {
                    WaningBlade ghost = ghosts.get(0);
                    check(f, !ghost.hurt(moon.damageSources().generic(), 50f), "the blade's afterimage took damage");
                    FakePlayer caster = new FakePlayer(moon, new GameProfile(UUID.randomUUID(), "moon_rift_caster2"));
                    caster.moveTo(ghost.getX() + 2, ghost.getY(), ghost.getZ());
                    MomentStop.cast(caster);
                    check(f, !MomentStop.frozen(ghost), "moment stop froze the blade's afterimage");
                    // 残像の刃が主に当たると主が崩れる
                    ghost.moveTo(lord.getX(), lord.getY(), lord.getZ() - 2, 0f, 0f);
                    ghost.setYRot(0f);
                    ghost.strikeAs(moon, "slash", lord);
                    check(f, lord.staggered(), "the afterimage's slash did not stagger the blade");
                    float h1 = lord.getHealth();
                    lord.invulnerableTime = 0;
                    lord.hurt(moon.damageSources().generic(), 50f);
                    check(f, h1 - lord.getHealth() >= 70f, "the staggered blade did not take extra damage: " + (h1 - lord.getHealth()));
                }
                RiftArena.waning(moon, lord);
                check(f, RiftArena.crumbled(), "the third phase did not crumble the floor");
                RiftArena.summon(moon);
                check(f, RiftArena.lords(moon).size() == 1, "summoning again should keep one blade");
            }
            RiftArena.clear(moon);
            check(f, RiftArena.lords(moon).isEmpty() && !RiftArena.crumbled(), "clearing the hall left the blade or the crumbled floor");
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST moon rift crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            spawned.stream().filter(e -> e != null && e.isAlive()).forEach(Entity::discard);
            RiftRemnants.clear();
            if (moon != null) {
                StarLamps.clear(moon);
                MoonClock.skipTo(moon, 0);
                for (int[] c : FORCED) {
                    moon.setChunkForced(c[0], c[1], false);
                }
                FORCED.clear();
            }
        }
        report(f);
    }

    static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    static BlockPos standableNear(ServerLevel level, BlockPos at) {
        for (int dy = 12; dy >= -12; dy--) {
            if (standable(level, at.above(dy))) {
                return at.above(dy);
            }
        }
        return at;
    }

    private static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon rift PASSED (rift terrain, camp, door, leap, gallery, hall, puzzle blocks, leap reach, afterimage replay/stand/plate/"
                    + "freeze/starlight, two-plate door, shadow bridges, falls and hall guard, main_moon_rift and the end card, mob numbers, armor front guard, "
                    + "remnants rise at 40% once and not in light, blade summon, its afterimage (no damage, no freeze), stagger by its own afterimage, floor crumble)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon rift FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
