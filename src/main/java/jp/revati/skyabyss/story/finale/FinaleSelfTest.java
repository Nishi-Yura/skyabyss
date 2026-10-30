package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 終章の舞台と行き方の自己テスト (docs/design/50 の 4.1)。段: "cloudsea"
//   ディメンションがある / 着く所の島が流し込まれ、印 (着く所の床・門の鐘・帰りの台・橋の台) がある / 遠くは雲の海 (y 36〜39) で、その下は空気 /
//   雲の下へ落ちた人は島へ戻り、体力が減る / 門の条件 (「雲の上へ」の段 ascend から) / 頼みと紙芝居と文がある
public final class FinaleSelfTest {
    private FinaleSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel cloud = server.getLevel(FinaleWorld.LEVEL);
        FakePlayer p = null;
        try {
            check(f, cloud != null, "the cloud sea dimension is missing");
            if (cloud == null) {
                report(f);
                return;
            }
            FinaleWorld.build(cloud);
            check(f, FinaleWorld.built(server), "the arrival island was not recorded as built");
            FinaleTerrain t = FinaleWorld.arrival();
            BlockPos at = t.mark("arrival");
            cloud.getChunk(at);
            check(f, cloud.getBlockState(at.below()).isFaceSturdy(cloud, at.below(), net.minecraft.core.Direction.UP)
                    && cloud.getBlockState(at).isAir() && cloud.getBlockState(at.above()).isAir(), "the arrival spot is not standable: " + cloud.getBlockState(at.below()));
            check(f, cloud.getBlockState(t.mark("gate")).is(Blocks.BELL), "the gate bell is missing: " + cloud.getBlockState(t.mark("gate")));
            check(f, cloud.getBlockState(t.mark("return")).is(Blocks.LODESTONE), "the return stone is missing");
            BlockPos bridge = t.mark("bridge_n");
            check(f, cloud.getBlockState(bridge.below()).isFaceSturdy(cloud, bridge.below(), net.minecraft.core.Direction.UP), "the bridge pier has no floor");
            for (String m : List.of("whale", "tide", "storm", "ember")) {
                check(f, !cloud.getBlockState(t.mark(m)).isAir(), "the " + m + " memorial plinth is missing");
            }
            BlockPos far = new BlockPos(300, 0, 300);
            cloud.getChunk(far);
            check(f, cloud.getBlockState(far.atY(37)).is(FinaleBlocks.CLOUD.get()) && cloud.getBlockState(far.atY(30)).isAir()
                    && cloud.getBlockState(far.atY(41)).isAir(), "the cloud layer is not at y 36-39 with air below");
            // 雲の下へ落ちると戻る
            p = FakePlayerFactory.get(cloud, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000c10d"), "cloud_fall"));
            p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            p.setHealth(p.getMaxHealth());
            p.moveTo(at.getX() + 0.5, 10, at.getZ() + 0.5, 0f, 0f);
            boolean back = FinaleWorld.rescueCheck(p);
            check(f, back && p.getY() > FinaleWorld.CLOUD_TOP && p.getHealth() < p.getMaxHealth() && p.getHealth() > 0,
                    "a fall under the clouds was not caught: back " + back + ", y " + p.getY() + ", health " + p.getHealth());
            // 門の条件
            Quests.forgetAll(p);
            check(f, !FinaleGate.open(p), "the gate opened without the quest");
            check(f, QuestManager.quest(FinaleGate.QUEST) != null, "quest " + FinaleGate.QUEST + " is missing");
            Quests.start(p, FinaleGate.QUEST);
            QuestLog.Entry e = QuestLog.of(p).entry(FinaleGate.QUEST);
            check(f, e != null && !FinaleGate.open(p), "the gate opened at the verse stage");
            if (e != null) {
                e.stage = 1;
                check(f, FinaleGate.open(p), "the gate did not open at the ascend stage");
            }
            Quests.forgetAll(p);
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/story_reels/" + FinaleGate.REEL + ".json") != null, "reel " + FinaleGate.REEL + " is missing");
            isle1(cloud, p, f);
            isle2(cloud, p, f);
            isle3(cloud, p, f);
            isle4(cloud, p, f);
            seat(cloud, p, f);
            trial(cloud, p, f);
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST cloudsea crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            if (p != null) {
                p.discard();
            }
        }
        report(f);
    }

    // 島 1「鐘の島」(50 の 4.2.1): 白い橋が途切れない / 鐘雲は鐘で固まり、無音でほどける / 鐘 7 がある / 扉は段 door から
    static void isle1(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 2, "island 1 was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = BellIsle.terrain();
        BlockPos s = t.mark("bridge_s");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            // 一番上の足場 (石板の半分の段も足場) の上に頭の空きが 2 つ
            boolean floor = false;
            for (int y = 80; y >= 60; y--) {
                BlockPos q = new BlockPos(0, y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the white bridge has " + gaps + " gaps between the arrival pier and island 1");
        for (String k : List.of("bell_c", "bell_w", "bell_e", "bell_n", "arena_bell_1", "arena_bell_2", "arena_bell_3", "arena_bell_4")) {
            check(f, cloud.getBlockState(t.mark(k)).is(Blocks.BELL), "bell " + k + " is missing");
        }
        java.util.List<BlockPos> clouds = BellIsle.clouds();
        check(f, clouds.size() > 300, "too few bell clouds: " + clouds.size());
        BellIsle.reset();
        BlockPos bw = t.mark("bell_w");
        BlockPos near = clouds.stream().filter(c -> c.distSqr(bw) <= 14 * 14).findFirst().orElse(null);
        BlockPos far = clouds.stream().filter(c -> c.distSqr(bw) > 40 * 40).findFirst().orElse(null);
        check(f, near != null && far != null, "no bell cloud near / far from the west bell");
        if (near != null && far != null) {
            BellIsle.update(cloud);
            check(f, !cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "a bell cloud was solid before any bell rang");
            BellIsle.ring(cloud, bw);
            check(f, cloud.getBlockState(near).getValue(BellCloudBlock.SOLID) && !cloud.getBlockState(far).getValue(BellCloudBlock.SOLID),
                    "ringing the west bell did not solidify only the near clouds");
            check(f, BellIsle.silence(cloud, bw, 8) == 1 && !cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "silence did not undo the west bell");
        }
        BellIsle.reset();
        BellIsle.update(cloud);
        Quests.forgetAll(p);
        check(f, !BellIsle.doorOpen(p), "the bell tower door opened without the quest");
        Quests.start(p, BellIsle.QUEST);
        QuestLog.Entry qe = QuestLog.of(p).entry(BellIsle.QUEST);
        check(f, qe != null && !BellIsle.doorOpen(p), "the door opened before the three bells");
        if (qe != null) {
            qe.stage = 2;
            check(f, BellIsle.doorOpen(p), "the door did not open at the door stage");
        }
        Quests.forgetAll(p);
        // 敵と主 (素材・湧き・主の間の崩れと戻り)
        for (String id : List.of("bell_mourner", "mist_bird", "last_bellkeeper")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        BlockPos bc = t.mark("bell_c");
        BellIsle.ring(cloud, bc);
        check(f, BellIsle.ringingNear(cloud, bc.north(3), 10) == 1, "the plaza bell is not counted as ringing");
        jp.revati.skyabyss.story.finale.mob.CloudMob m = BellArena.put(cloud, jp.revati.skyabyss.story.finale.mob.CloudMobsSetup.BELL_MOURNER.get(), t.mark("door_front"));
        check(f, m != null && Math.abs(m.getMaxHealth() - jp.revati.skyabyss.story.finale.mob.BellMourner.HP) < 0.01, "a bell mourner could not be put");
        if (m != null) {
            m.discard();
        }
        BellIsle.reset();
        BellIsle.update(cloud);
        jp.revati.skyabyss.story.finale.mob.LastBellkeeper lord = BellArena.summon(cloud);
        check(f, lord != null && lord.blockPosition().equals(t.mark("lord_spawn")) && lord.getMaxHealth() > 900, "the last bellkeeper did not appear at its spot");
        BlockPos east = t.mark("arena").east(4);
        BellArena.crumble(cloud);
        check(f, cloud.getBlockState(east).getBlock() instanceof BellCloudBlock && cloud.getBlockState(t.mark("arena")).isFaceSturdy(cloud, t.mark("arena"), net.minecraft.core.Direction.UP),
                "the arena floor did not crumble into bell cloud (east half only)");
        BellArena.clear(cloud);
        check(f, BellArena.lords(cloud).isEmpty() && cloud.getBlockState(east).isFaceSturdy(cloud, east, net.minecraft.core.Direction.UP), "clearing the arena did not restore the floor");
        for (String k : List.of("arena_entry", "door_front", "arrive")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir(), k + " is not standable");
        }
    }

    // 島 2「灯の島」(50 の 4.2.2): 光の橋が途切れない・関所 / 漁の灯 3・扉 / 光の筋 (見えていれば重い、陰で外れる、灯されれば陰でも) / 主の間の主
    static void isle2(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 3, "island 2 was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = LampIsle.terrain();
        BlockPos s = t.mark("bridge_gate");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            boolean floor = false;
            for (int y = 110; y >= 80; y--) {
                BlockPos q = new BlockPos(0, y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the light bridge has " + gaps + " gaps between the bell tower and island 2");
        for (String k : List.of("w", "e", "s")) {
            check(f, cloud.getBlockState(t.mark("lamp_" + k)).getBlock() instanceof net.minecraft.world.level.block.RedstoneLampBlock, "fishing lamp " + k + " is missing");
        }
        for (String k : List.of("arena_entry", "door_front", "arrive", "frag_w", "frag_e", "frag_s")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir(), k + " is not standable");
        }
        // 関所: 鐘の島を終えていない人は主の間の入口へ戻る
        Quests.forgetAll(p);
        p.moveTo(s.getX() + 0.5, s.getY(), s.getZ() - 3 + 0.5, 0f, 0f);
        LampIsle.gate(p);
        check(f, p.blockPosition().closerThan(BellIsle.mark("arena_entry"), 2), "the light bridge let someone through before the bell lord");
        // 扉
        check(f, !LampIsle.doorOpen(p), "the lighthouse door opened without the quest");
        Quests.start(p, LampIsle.QUEST);
        QuestLog.Entry qe = QuestLog.of(p).entry(LampIsle.QUEST);
        check(f, qe != null && !LampIsle.doorOpen(p), "the lighthouse door opened before the three lamps");
        if (qe != null) {
            qe.stage = 2;
            check(f, LampIsle.doorOpen(p), "the lighthouse door did not open at the door stage");
        }
        Quests.forgetAll(p);
        // 光の筋 (主の間で: 目から入口の人は見える、石柱の陰の人は見えない)
        LampIsle.reset(p);
        net.minecraft.world.phys.Vec3 eye = t.mark("lord_spawn").getCenter().add(0, 0.9, 0);
        BlockPos en = t.mark("arena_entry");
        p.moveTo(en.getX() + 0.5, en.getY(), en.getZ() + 0.5, 0f, 0f);
        double toEntry = Math.atan2(p.getEyePosition().z - eye.z, p.getEyePosition().x - eye.x);
        check(f, LampIsle.inBeam(cloud, eye, toEntry, 0.2, 13, p), "the eye cannot see someone at the arena entry");
        check(f, !LampIsle.inBeam(cloud, eye, toEntry + Math.PI, 0.2, 13, p), "the beam pointing away still caught someone");
        BlockPos pil = t.mark("pillar_1");
        net.minecraft.world.phys.Vec3 pc = new net.minecraft.world.phys.Vec3(pil.getX() + 1.0, pil.getY(), pil.getZ() + 1.0);
        net.minecraft.world.phys.Vec3 out = pc.subtract(eye.x, pc.y, eye.z).multiply(1, 0, 1).normalize();
        net.minecraft.world.phys.Vec3 behind = pc.add(out.scale(1.8));
        p.moveTo(behind.x, pil.getY(), behind.z, 0f, 0f);
        double toBehind = Math.atan2(p.getEyePosition().z - eye.z, p.getEyePosition().x - eye.x);
        check(f, !LampIsle.inBeam(cloud, eye, toBehind, 0.2, 13, p), "a pillar did not shade someone from the eye");
        jp.revati.skyabyss.story.finale.mob.LampMoth.mark(p);
        check(f, LampIsle.inBeam(cloud, eye, toBehind, 0.2, 13, p), "a lit (moth-marked) person was shaded by a pillar");
        LampIsle.gaze(p, true, LampIsle.HOLD, 0, null);
        check(f, LampIsle.heavy(p) && p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH) < 0.01, "the beam did not make someone heavy");
        LampIsle.gaze(p, false, LampIsle.HOLD, 0, null);
        check(f, !LampIsle.heavy(p), "leaving the beam did not lift the weight");
        LampIsle.reset(p);
        // 素材と主
        for (String id : List.of("net_caster", "lamp_moth", "lamp_eye")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        jp.revati.skyabyss.story.finale.mob.LampEye lord = LampArena.summon(cloud);
        check(f, lord != null && lord.blockPosition().closerThan(t.mark("lord_spawn"), 2.5) && lord.getMaxHealth() > 900, "the lamp-keeper's eye did not appear at its spot");
        LampArena.clear(cloud);
        check(f, LampArena.lords(cloud).isEmpty(), "clearing the lighthouse arena left the eye");
    }

    // 島 3「炉の島」(50 の 4.2.3): 光の橋・関所 / 炉 3・扉 / 戻り石の組と巻き戻り (主の間の輪は段 2 まで石) / 巻き戻りの打ち (同じ所でもう一度) / 主
    static void isle3(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 4, "island 3 was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = ForgeIsle.terrain();
        BlockPos s = t.mark("bridge_gate");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            boolean floor = false;
            for (int y = 126; y >= 95; y--) {
                BlockPos q = new BlockPos(s.getX(), y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the light bridge has " + gaps + " gaps between the lighthouse and island 3");
        for (String k : List.of("w", "e", "n")) {
            check(f, cloud.getBlockState(t.mark("forge_" + k)).getBlock() instanceof net.minecraft.world.level.block.BlastFurnaceBlock, "forge " + k + " is missing");
            check(f, ForgeIsle.groups().get(k).size() >= 9, "broken bridge " + k + " has too few rewind stones: " + ForgeIsle.groups().get(k).size());
        }
        check(f, ForgeIsle.groups().get("arena").size() > 100, "the arena ring has too few rewind stones: " + ForgeIsle.groups().get("arena").size());
        for (String k : List.of("arena_entry", "door_front", "arrive", "frag_w", "frag_e", "frag_n")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir(), k + " is not standable");
        }
        // 巻き戻り: 石の相は時刻どおり、主の間の輪は段 2 まで石
        ForgeIsle.arenaRewinds = false;
        ForgeIsle.update(cloud, true);
        BlockPos gw = ForgeIsle.groups().get("w").get(0);
        check(f, cloud.getBlockState(gw).getValue(RewindStoneBlock.PHASE) == ForgeIsle.phaseOf(cloud, "w"), "a broken bridge stone is not in its group's phase");
        BlockPos ga = ForgeIsle.groups().get("arena").get(0);
        check(f, RewindStoneBlock.solid(cloud.getBlockState(ga)), "the arena ring is not stone before the rewinder's second phase");
        // 関所: 灯の島を終えていない人は灯台の上の入口へ戻る
        Quests.forgetAll(p);
        p.moveTo(s.getX() + 0.5, s.getY(), s.getZ() - 3 + 0.5, 0f, 0f);
        ForgeIsle.gate(p);
        check(f, p.blockPosition().closerThan(LampIsle.mark("arena_entry"), 2), "the light bridge let someone through before the lamp lord");
        // 扉
        check(f, !ForgeIsle.doorOpen(p), "the great forge door opened without the quest");
        Quests.start(p, ForgeIsle.QUEST);
        QuestLog.Entry qe = QuestLog.of(p).entry(ForgeIsle.QUEST);
        check(f, qe != null && !ForgeIsle.doorOpen(p), "the great forge door opened before the three forges");
        if (qe != null) {
            qe.stage = 2;
            check(f, ForgeIsle.doorOpen(p), "the great forge door did not open at the door stage");
        }
        Quests.forgetAll(p);
        // 巻き戻りの打ち: 印の中だけが燃える (円と扇)
        BlockPos en = t.mark("arena_entry");
        net.minecraft.world.phys.Vec3 at = en.getCenter();
        Echoes.clear();
        Echoes.circle(cloud, at, 2.0, 60, 6f, null);
        Echoes.cone(cloud, at.add(0, 0, -4), new net.minecraft.world.phys.Vec3(0, 0, 1), 5.0, 60, 60, 6f, null);
        check(f, Echoes.pending(cloud) == 2, "the echoes were not waiting");
        check(f, Echoes.covering(cloud, at.add(1.2, 0, 0)) == 2 && Echoes.covering(cloud, at.add(5, 0, 0)) == 0
                && Echoes.covering(cloud, at.add(0, 0, -8)) == 0, "an echo covers the wrong place");
        Echoes.fireAll(cloud);
        check(f, Echoes.pending(cloud) == 0, "fired echoes were left waiting");
        // 素材と主
        for (String id : List.of("ash_striker", "backfire", "rewinder")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        jp.revati.skyabyss.story.finale.mob.Rewinder lord = ForgeArena.summon(cloud);
        check(f, lord != null && lord.blockPosition().closerThan(t.mark("lord_spawn"), 2) && lord.getMaxHealth() > 900, "the rewinder did not appear at its spot");
        ForgeArena.clear(cloud);
        check(f, ForgeArena.lords(cloud).isEmpty() && !ForgeIsle.arenaRewinds, "clearing the forge arena left the rewinder");
    }

    // 島 4「約束の島」(50 の 4.2.4): 光の橋・関所 / 四人の幻と並ぶ / 扉 / 盾の円・起こす術 / 羽根の矢の筋 / 主
    static void isle4(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 5, "island 4 was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = PromiseIsle.terrain();
        BlockPos s = t.mark("bridge_gate");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            boolean floor = false;
            for (int y = 132; y >= 108; y--) {
                BlockPos q = new BlockPos(s.getX(), y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the light bridge has " + gaps + " gaps between the great forge and island 4");
        for (String k : List.of("arena_entry", "door_front", "arrive", "self_1", "self_2", "self_3", "self_4", "seat_bridge")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir()
                    && cloud.getBlockState(q.above()).isAir(), k + " is not standable");
        }
        check(f, cloud.getBlockState(t.mark("camp")).getBlock() instanceof net.minecraft.world.level.block.CampfireBlock, "the camp has no fire");
        // 関所: 炉の島を終えていない人は大炉の上の入口へ戻る
        Quests.forgetAll(p);
        p.moveTo(s.getX() + 0.5, s.getY(), s.getZ() - 3 + 0.5, 0f, 0f);
        PromiseIsle.gate(p);
        check(f, p.blockPosition().closerThan(ForgeIsle.mark("arena_entry"), 2), "the light bridge let someone through before the rewinder");
        // 四人の幻が天幕の前に立つ
        PromiseIsle.reset();
        PromiseIsle.standUp(cloud);
        for (int k = 1; k <= 4; k++) {
            jp.revati.skyabyss.town.TownGuestEntity g = PromiseIsle.self(cloud, k);
            check(f, g != null && g.blockPosition().closerThan(t.mark("self_" + k), 2), "former self " + k + " is not standing at their tent");
        }
        // 並ぶ: 段 selves で目的 k が済んだ人だけ / 扉は段 door から
        check(f, !PromiseIsle.doorOpen(p), "the rock door opened without the quest");
        Quests.start(p, PromiseIsle.QUEST);
        Quests.signal(p, "finale_isle4_arrive");
        Quests.signal(p, "finale_promise_1");
        check(f, PromiseIsle.joined(p, 1) && !PromiseIsle.joined(p, 2), "standing with the valley self did not count alone");
        check(f, !PromiseIsle.doorOpen(p), "the rock door opened before all four stood with you");
        for (int k = 2; k <= 4; k++) {
            Quests.signal(p, "finale_promise_" + k);
        }
        check(f, PromiseIsle.doorOpen(p) && PromiseIsle.joined(p, 4), "the rock door did not open after all four stood with you");
        // 盾の円・起こす術 (開発用の join で四人をそばへ)
        BlockPos camp = t.mark("self_1");
        p.moveTo(camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5, 0f, 0f);
        PromiseIsle.join(p);
        check(f, PromiseIsle.shielded(p), "the valley self's circle does not shield someone beside them");
        check(f, PromiseIsle.canRaise(p) && jp.revati.skyabyss.party.Downed.mayDown(p), "the field self would not raise someone who falls alone");
        p.moveTo(camp.getX() + 0.5 + 9, camp.getY(), camp.getZ() + 0.5, 0f, 0f);
        check(f, !PromiseIsle.shielded(p), "the valley self's circle reaches too far");
        // 落ち羽の筋
        Vec3 from = new Vec3(0, 100, 0);
        Vec3 dir = new Vec3(1, 0, 0);
        check(f, jp.revati.skyabyss.story.finale.mob.FallenPlume.onLine(from, dir, new Vec3(6, 100, 0.5))
                && !jp.revati.skyabyss.story.finale.mob.FallenPlume.onLine(from, dir, new Vec3(6, 100, 2.0))
                && !jp.revati.skyabyss.story.finale.mob.FallenPlume.onLine(from, dir, new Vec3(-2, 100, 0)), "the fallen plume's line covers the wrong ground");
        // 素材と主
        for (String id : List.of("fallen_plume", "plume_warden", "pilgrim_plume")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        jp.revati.skyabyss.story.finale.mob.PilgrimPlume lord = PromiseArena.summon(cloud);
        check(f, lord != null && lord.blockPosition().closerThan(t.mark("lord_spawn").above(3), 2) && lord.getMaxHealth() > 1000, "the pilgrim's plume did not appear at its spot");
        PromiseArena.clear(cloud);
        check(f, PromiseArena.lords(cloud).isEmpty(), "clearing the rock arena left the plume");
        for (jp.revati.skyabyss.town.TownGuestEntity g : PromiseIsle.selves(cloud)) {
            g.discard();
        }
        PromiseIsle.reset();
        Quests.forgetAll(p);
    }

    // 巡礼者の座 (50 の 4.3): 光の橋・関所 / 鐘雲の輪と鐘 / 三形態の部位 / 第 3 形態の四人 (盾の円) / 片付け
    static void seat(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 6, "the seat was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = PilgrimSeat.terrain();
        BlockPos s = t.mark("bridge_gate");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            boolean floor = false;
            for (int y = 166; y >= 130; y--) {
                BlockPos q = new BlockPos(s.getX(), y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the light bridge has " + gaps + " gaps between the moon-viewing rock and the seat");
        for (String k : List.of("arrive", "arena_entry", "throne_front", "lord_spawn")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir(), k + " is not standable");
        }
        check(f, PilgrimSeat.clouds().size() > 300, "the seat's cloud ring is too small: " + PilgrimSeat.clouds().size());
        for (BlockPos b : PilgrimSeat.bells()) {
            check(f, cloud.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BellBlock, "a seat bell is missing at " + b);
        }
        // 鐘で輪が固まる
        BellIsle.addExtra(PilgrimSeat.clouds());
        BlockPos bell = PilgrimSeat.bells().get(0);
        BlockPos near = null;
        for (BlockPos c : PilgrimSeat.clouds()) {
            if (c.distSqr(bell) < 64) {
                near = c;
                break;
            }
        }
        BellIsle.ring(cloud, bell);
        check(f, near != null && cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "ringing a seat bell did not harden the ring");
        BellIsle.silence(cloud, bell, 2);
        check(f, near != null && !cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "silencing the seat bell left the ring hard");
        // 関所: 約束の島を終えていない人は月見の岩の上の入口へ戻る
        Quests.forgetAll(p);
        p.moveTo(s.getX() + 0.5, s.getY(), s.getZ() - 3 + 0.5, 0f, 0f);
        PilgrimSeat.gate(p);
        check(f, p.blockPosition().closerThan(PromiseIsle.mark("arena_entry"), 2), "the light bridge let someone through before the pilgrim's plume");
        check(f, !PilgrimSeat.mayChallenge(p), "the seat could be touched without the quest");
        // 三形態の部位
        SeatArena.toForm(cloud, 1);
        check(f, SeatArena.parts(cloud).stream().filter(x -> x instanceof jp.revati.skyabyss.story.finale.mob.PilgrimHand).count() == 2, "the pilgrim's two hands did not rise");
        SeatArena.toForm(cloud, 2);
        check(f, SeatArena.parts(cloud).size() == 1 && SeatArena.parts(cloud).get(0) instanceof jp.revati.skyabyss.story.finale.mob.PilgrimEye
                && cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "the eye form did not open with the ring hardened");
        SeatArena.toForm(cloud, 3);
        check(f, SeatArena.parts(cloud).size() == 1 && SeatArena.parts(cloud).get(0) instanceof jp.revati.skyabyss.story.finale.mob.PilgrimMoon
                && SeatArena.allies(cloud).size() == 4, "the moon form did not come with the four former selves");
        jp.revati.skyabyss.town.TownGuestEntity valley = SeatArena.ally(cloud, 1);
        check(f, valley != null && PromiseIsle.active(cloud, 1) == valley, "the seat's valley self does not use its arts");
        if (valley != null) {
            p.moveTo(valley.getX() + 1, valley.getY(), valley.getZ(), 0f, 0f);
            check(f, PromiseIsle.shielded(p), "the seat's valley self does not shield someone beside them");
        }
        for (String id : List.of("pilgrim_hand", "pilgrim_eye", "pilgrim_moon")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        SeatArena.clear(cloud);
        check(f, SeatArena.parts(cloud).isEmpty() && SeatArena.allies(cloud).isEmpty() && SeatArena.form() == 0, "clearing the seat left the pilgrim or the four");
        // 約束の島の四人が座まで来ていたら天幕の前へ帰る / 初めての一言は人ごとに一度
        PromiseIsle.standUp(cloud);
        jp.revati.skyabyss.town.TownGuestEntity stray = PromiseIsle.self(cloud, 2);
        if (stray != null) {
            BlockPos at = PilgrimSeat.mark("lord_spawn");
            stray.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            PilgrimSeat.sendSelvesHome(cloud);
            check(f, stray.blockPosition().closerThan(PromiseIsle.mark("self_2"), 2), "an island self that reached the seat was not sent home");
        }
        p.getPersistentData().remove(FinaleHints.KEY);
        check(f, FinaleHints.once(p, "hand_stuck") && !FinaleHints.once(p, "hand_stuck"), "a first-time hint showed twice or never");
        p.getPersistentData().remove(FinaleHints.KEY);
        // 後ろの空白・全角の空白のあるコマンドも通る (CommandTidy)
        var src = cloud.getServer().createCommandSourceStack().withPermission(4);
        var disp = cloud.getServer().getCommands().getDispatcher();
        for (String typed : List.of("skyabyss cloudsea enter ", "skyabyss　cloudsea  enter")) {
            var parsed = disp.parse(jp.revati.skyabyss.CommandTidy.tidy(typed), src);
            check(f, !parsed.getReader().canRead() && parsed.getExceptions().isEmpty() && parsed.getContext().getCommand() != null,
                    "the cloudsea command with stray spaces does not parse: [" + typed + "]");
        }
        Quests.forgetAll(p);
        ending(p, f);
    }

    // 結末 (50 の 4.4): 選べる条件・受け入れるの条件・記録と空の段・語りと伝承の素材
    static void ending(FakePlayer p, List<String> f) {
        p.getPersistentData().remove(FinaleEnding.KEY);
        check(f, FinaleEnding.chosen(p) == null && FinaleEnding.stage(p) == null, "a fresh player already has an ending");
        check(f, !FinaleEnding.mayChoose(p), "someone without the seat quest could choose an ending");
        check(f, !FinaleEnding.mayAccept(p) && !FinaleEnding.choose(p, "accept"), "accept was open without the twelve memories and the watcher lore");
        FinaleEnding.apply(p, "calm");
        check(f, "calm".equals(FinaleEnding.chosen(p)) && jp.revati.skyabyss.sky.ChapterSky.stage(p).moonBias() < 0, "choosing calm did not change the sky");
        FinaleEnding.apply(p, "break");
        check(f, "break".equals(FinaleEnding.chosen(p)), "choosing again did not replace the ending");
        for (String e : FinaleEnding.ENDINGS) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/story_reels/finale_end_" + e + ".json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/gui/story/finale_end_" + e + "/p5.png") != null
                    && FinaleSelfTest.class.getResource("/data/skyabyss/lore/entry/finale/ending_" + e + ".json") != null, "the " + e + " ending reel or lore is missing");
        }
        check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/textures/environment/finale_crack.png") != null
                && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/environment/finale_wing.png") != null, "the ending sky textures are missing");
        p.getPersistentData().remove(FinaleEnding.KEY);
        divine(p, f);
    }

    // 神器 (50 の 4.5): 6 本と職・受け取り・職を替えたときの持ち替え・心核を砕く・書き換えの品
    static void divine(FakePlayer p, List<String> f) {
        jp.revati.skyabyss.rpg.skill.Job[] jobs = {jp.revati.skyabyss.rpg.skill.Job.WARRIOR, jp.revati.skyabyss.rpg.skill.Job.SMITH,
                jp.revati.skyabyss.rpg.skill.Job.MAGE, jp.revati.skyabyss.rpg.skill.Job.ARCHER, jp.revati.skyabyss.rpg.skill.Job.ASSASSIN,
                jp.revati.skyabyss.rpg.skill.Job.SHAMAN};
        for (jp.revati.skyabyss.rpg.skill.Job j : jobs) {
            net.minecraft.world.item.Item it = jp.revati.skyabyss.story.finale.gear.DivineGear.item(jp.revati.skyabyss.story.finale.gear.DivineGear.forJob(j));
            check(f, it instanceof jp.revati.skyabyss.item.ModGear g && g.tier() == jp.revati.skyabyss.item.GearTier.ARTIFACT
                    && jp.revati.skyabyss.rpg.skill.Job.of(it) == j, "the " + j.id + " relic is missing, not an artifact, or belongs to another job");
        }
        p.getInventory().clearContent();
        p.getPersistentData().remove("skyabyss_divine_given");
        jp.revati.skyabyss.story.finale.gear.DivineGear.reward(p);
        check(f, jp.revati.skyabyss.story.finale.gear.DivineGear.given(p) && countRelics(p) == 1
                && p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.CORE.get()) == 1, "the first defeat did not give one relic and the cloud core");
        int shardsBefore = p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.SHARD.get());
        jp.revati.skyabyss.story.finale.gear.DivineGear.reward(p);
        check(f, countRelics(p) == 1 && p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.SHARD.get()) > shardsBefore,
                "a later defeat gave another relic or no shards");
        // 職を替えると神器も替わる (強化はそのまま)
        int slot = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (jp.revati.skyabyss.story.finale.gear.DivineGear.isRelic(p.getInventory().getItem(i))) {
                slot = i;
            }
        }
        if (slot >= 0) {
            p.getInventory().getItem(slot).set(jp.revati.skyabyss.registry.ModDataComponents.ENHANCE.get(), 3);
        }
        jp.revati.skyabyss.story.finale.gear.DivineGear.swap(p, jp.revati.skyabyss.rpg.skill.Job.MAGE);
        net.minecraft.world.item.ItemStack swapped = slot >= 0 ? p.getInventory().getItem(slot) : net.minecraft.world.item.ItemStack.EMPTY;
        check(f, swapped.getItem() == jp.revati.skyabyss.story.finale.gear.DivineGearSetup.STAFF.get() && jp.revati.skyabyss.item.ModGear.enhance(swapped) == 3,
                "changing job did not turn the relic into the mage's, keeping its upgrade");
        // 心核を砕く
        net.minecraft.world.item.ItemStack core = new net.minecraft.world.item.ItemStack(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.CORE.get(), 2);
        int sh = p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.SHARD.get());
        check(f, jp.revati.skyabyss.story.finale.gear.DivineGear.breakCore(p, core) && core.getCount() == 1
                && p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.SHARD.get()) == sh + jp.revati.skyabyss.story.finale.gear.DivineGear.SHARDS_PER_CORE,
                "breaking a heart core did not give shards");
        for (String c : jp.revati.skyabyss.story.finale.gear.DivineGear.CORES) {
            check(f, jp.revati.skyabyss.story.finale.gear.DivineGear.item(c) != net.minecraft.world.item.Items.AIR, "the heart core " + c + " is not registered");
        }
        // 書き換えの品
        for (jp.revati.skyabyss.rpg.skill.Rune r : jp.revati.skyabyss.rpg.skill.Rune.values()) {
            if (r.id.startsWith("divine_")) {
                check(f, jp.revati.skyabyss.story.finale.gear.DivineGear.RELICS.contains(SkyAbyss.MODID + ":" + r.weapon), "the rewrite " + r.id + " points at no relic");
            }
        }
        p.getInventory().clearContent();
        p.getPersistentData().remove("skyabyss_divine_given");
    }

    // 巡礼の試し (50 の 4.6): 暦・閉じた人・段の倍率と体力の上限・技の間・報酬と記録・片付け
    static void trial(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 7 && cloud.getBlockState(PilgrimSeat.mark("calendar")).getBlock()
                instanceof net.minecraft.world.level.block.DaylightDetectorBlock, "the cloud calendar is missing");
        Quests.forgetAll(p);
        p.getPersistentData().remove(PilgrimTrial.KEY);
        check(f, !PilgrimTrial.open(p) && PilgrimTrial.next(p) == 1, "the trial was open before the seat quest");
        SeatArena.startTrial(cloud, 3);
        java.util.List<jp.revati.skyabyss.story.finale.mob.CloudMob> hands = new java.util.ArrayList<>();
        SeatArena.toForm(cloud, 1);
        check(f, SeatArena.tier() == 0, "a plain fight kept the trial tier");
        SeatArena.startTrial(cloud, 3);
        SeatArena.summonForm(cloud);
        hands.addAll(SeatArena.parts(cloud));
        check(f, hands.size() == 2 && SeatArena.tier() == 3 && SeatArena.pace() < 1, "the tier 3 trial did not raise two hands with faster moves");
        if (!hands.isEmpty()) {
            jp.revati.skyabyss.story.finale.mob.CloudMob h = hands.get(0);
            double want = jp.revati.skyabyss.story.finale.mob.PilgrimHand.HP * PilgrimTrial.hpScale(3);
            check(f, h.getMaxHealth() <= jp.revati.skyabyss.story.finale.mob.CloudMob.MAX_HP
                    && Math.abs(h.getMaxHealth() / h.toughness() - want) < 1, "the trial hand's health is not the tier's (" + h.getMaxHealth() + " / " + h.toughness() + ")");
        }
        SeatArena.clear(cloud);
        check(f, SeatArena.tier() == 0 && SeatArena.form() == 0, "clearing the trial left its tier");
        // 報酬と記録 (段 5 は記念の頁)
        PilgrimTrial.won(java.util.List.of(p), 5);
        check(f, PilgrimTrial.cleared(p) == 5 && PilgrimTrial.next(p) == 5
                && p.getInventory().countItem(jp.revati.skyabyss.story.finale.gear.DivineGearSetup.SHARD.get()) >= 8
                && jp.revati.skyabyss.lore.Lore.state(p).has(PilgrimTrial.KEEPSAKE), "winning tier 5 did not record, reward, or write the keepsake");
        p.getInventory().clearContent();
        p.getPersistentData().remove(PilgrimTrial.KEY);
    }

    static int countRelics(FakePlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            n += jp.revati.skyabyss.story.finale.gear.DivineGear.isRelic(p.getInventory().getItem(i)) ? 1 : 0;
        }
        return n;
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST cloudsea PASSED (dimension, arrival island and marks, cloud layer, fall rescue, gate condition, quest and reel; island 1 bridge, bells, bell clouds ring/silence, tower door, foes, lord and arena floor; island 2 light bridge and gate, lamps, door, beam and shade, moth light, eye; island 3 light bridge and gate, forges, door, rewind stones, echoes, rewinder; island 4 light bridge and gate, former selves, door, shield and raise, plume line, pilgrim's plume; seat bridge and gate, cloud ring and bells, three forms, the four allies; endings; divine relics; pilgrim's trial)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST cloudsea FAILED: {}", m));
        }
    }
}
