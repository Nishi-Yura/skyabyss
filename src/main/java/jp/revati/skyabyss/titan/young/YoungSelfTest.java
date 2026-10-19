package jp.revati.skyabyss.titan.young;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.end.TitanDormancy;
import jp.revati.skyabyss.titan.solid.TitanSolids;
import jp.revati.skyabyss.titan.solid.TitanVolume;
import jp.revati.skyabyss.titan.travel.TitanCalls;
import jp.revati.skyabyss.titan.travel.TravelNetwork;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// 巨神の子の自己テスト (段の名前は "young")。docs/design/28
//  - 眠っている巨神の笛は応えない (子が出ない)・出会っていない人は呼べない
//  - 六人のパーティが全員乗れる (席は別々)・パーティ外の人と満席の七人目は乗れない
//  - 行き先を決める → 出発 → 運ばれる (乗っている人は降りられない・傷つかない) → 着く → 降りる → 歌に溶ける
//  - 旅の途中のログアウト → 子から外れて次のログインで地面へ。途中で溶けると全員が地面へ降りる
//  - 巨神ごとの得意による行き先の限り・眠っている巨神は行き先に選べない・成体の体の外に降りる
public final class YoungSelfTest {
    private static final List<String> F = new ArrayList<>();
    private static int stage;
    private static int wait;
    private static FakePlayer[] p;
    private static PartyData.Party party;
    private static YoungEntity young;
    private static Vec3 start;
    private static BlockPos platform;

    private YoungSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("young (load)", YoungSelfTest::load),
                SelfTestRunner.Step.until("young", YoungSelfTest::tick),
                SelfTestRunner.Step.once("young rules", 5, YoungSelfTest::rules));
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            F.add(message);
        }
    }

    private static void report(String name) {
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST {} PASSED", name);
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST {} FAILED: {}", name, m));
        }
        F.clear();
    }

    private static FakePlayer fake(ServerLevel level, int i) {
        FakePlayer f = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000d00" + (10 + i)), "[young_" + i + "]"));
        f.setPos(platform.getX() + 0.5 + i, platform.getY() + 1, platform.getZ() + 0.5);
        f.setHealth(f.getMaxHealth());
        f.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Voyages.state(f).readyAt = 0;
        return f;
    }

    private static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        platform = new BlockPos(spawn.getX() + 40, 130, spawn.getZ() + 40);
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                level.getChunk((platform.getX() + dx) >> 4, (platform.getZ() + dz) >> 4);
            }
        }
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                level.setBlock(platform.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 1; dy <= 8; dy++) {
                    level.setBlock(platform.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        p = new FakePlayer[7];
        for (int i = 0; i < 7; i++) {
            p[i] = fake(level, i);
        }
        stage = 0;
        wait = 0;
    }

    private static List<ServerPlayerLike> none() {
        return List.of();
    }

    private interface ServerPlayerLike {
    }

    // 段ごとの流れ (毎 tick 呼ばれる。true で終わり)
    private static boolean tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        try {
            return flow(server, level);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST young crashed", e);
            F.add("crashed: " + e);
            cleanup(server);
            report("young");
            return true;
        }
    }

    private static double groundAt(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    private static boolean flow(MinecraftServer server, ServerLevel level) {
        switch (stage) {
            case 0 -> {
                // 眠っている巨神の笛は応えない・出会っていない人は呼べない
                for (int i = 0; i < 6; i++) {
                    TitanCalls.markMet(p[i], Titans.STONE_WHALE);
                }
                TitanDormancy.begin(server, Titans.STONE_WHALE, 0);
                check(!YoungCarriers.whistle(p[0], Titans.STONE_WHALE), "a sleeping titan's call was answered");
                check(YoungCarriers.of(p[0].getUUID()) == null, "a young one appeared for a sleeping titan");
                TitanDormancy.clear(server, Titans.STONE_WHALE);
                check(!YoungCarriers.whistle(p[6], Titans.STONE_WHALE), "a player who had not met the titan could call it");
                // パーティ: p0 が持ち主、p0〜p5
                PartyData data = PartyData.get(server);
                party = data.create(p[0].getUUID(), "young0");
                for (int i = 1; i < 6; i++) {
                    data.add(party, p[i].getUUID(), "young" + i);
                }
                check(YoungCarriers.whistle(p[0], Titans.STONE_WHALE), "the call was refused");
                young = YoungCarriers.of(p[0].getUUID());
                check(young != null && young.phase() == YoungEntity.APPEAR, "no young one in APPEAR");
                if (young == null) {
                    cleanup(server);
                    report("young");
                    return true;
                }
                wait = 0;
                stage = 1;
            }
            case 1 -> {
                if (++wait < YoungEntity.APPEAR_TICKS + 3) {
                    return false;
                }
                check(young.phase() == YoungEntity.WAIT, "the young one did not reach WAIT (" + young.phase() + ")");
                // 持ち主が乗る → 仲間が笛で乗る → パーティ外は乗れない
                YoungCarriers.tryBoard(young, p[0]);
                check(p[0].getVehicle() == young, "the owner could not board");
                for (int i = 1; i < 6; i++) {
                    check(YoungCarriers.whistle(p[i], Titans.STONE_WHALE), "party member " + i + " could not board by call");
                }
                YoungCarriers.tryBoard(young, p[6]);
                check(p[6].getVehicle() != young, "a player outside the party boarded");
                check(young.riderCount() == 6, "not six riders (" + young.riderCount() + ")");
                Set<Integer> slots = new HashSet<>();
                Set<String> places = new HashSet<>();
                for (int i = 0; i < 6; i++) {
                    int s = young.slotOf(p[i]);
                    slots.add(s);
                    double[] seat = young.kind.seat(s);
                    places.add(seat[0] + "," + seat[2]);
                }
                check(slots.size() == 6 && !slots.contains(-1) && places.size() == 6, "seats overlap or are missing: " + slots);
                check(young.slotOf(p[0]) == 0, "the owner is not in the first seat");
                // 七人目 (パーティに入れても満席)
                PartyData.get(server).add(party, p[6].getUUID(), "young6");
                YoungCarriers.tryBoard(young, p[6]);
                check(p[6].getVehicle() != young && young.riderCount() == 6, "a seventh rider boarded");
                PartyData.get(server).remove(p[6].getUUID());
                // 眠っている巨神は行き先に選べない
                TitanDormancy.begin(server, Titans.THUNDER_WING, 0);
                TitanCalls.markMet(p[0], Titans.THUNDER_WING);
                boolean sleepingLocked = false;
                for (TravelNetwork.Dest d : YoungReach.list(p[0], young.kind, young.position())) {
                    if (d.id().equals("t:" + Titans.THUNDER_WING)) {
                        sleepingLocked = !d.valid() && d.lock().equals("sleeping");
                    }
                }
                check(sleepingLocked, "a sleeping titan was a selectable destination");
                TravelNetwork.Dest sleepy = new TravelNetwork.Dest("t:" + Titans.THUNDER_WING, Titans.THUNDER_WING, "", 400, 100, 400, 2, false, 10, "sleeping");
                check(young.trip().choose(sleepy) != null && young.trip().phase() == YoungTrip.Phase.WAIT, "choosing a sleeping titan was accepted");
                TitanDormancy.clear(server, Titans.THUNDER_WING);
                // 行き先を決める (東へ 170 ブロック)
                start = young.position();
                TravelNetwork.Dest d = new TravelNetwork.Dest("test", "east", "", (int) start.x + 170, 100, (int) start.z, 1, true, 0, "");
                String refused = YoungCarriers.chooseDirect(p[0], d);
                check(refused == null, "choose refused: " + refused);
                check(young.trip().phase() == YoungTrip.Phase.COUNTDOWN, "not in COUNTDOWN");
                wait = 0;
                stage = 2;
            }
            case 2 -> {
                if (young.trip().phase() == YoungTrip.Phase.COUNTDOWN) {
                    if (++wait > 400) {
                        F.add("the countdown never ended");
                        cleanup(server);
                        report("young");
                        return true;
                    }
                    return false;
                }
                check(young.trip().phase() == YoungTrip.Phase.CARRY, "not in CARRY (" + young.trip().phase() + ")");
                wait = 0;
                stage = 3;
            }
            case 3 -> {
                if (++wait < 50) {
                    return false;
                }
                double moved = Math.hypot(young.getX() - start.x, young.getZ() - start.z);
                check(moved > 20, "the young one barely moved (" + moved + ")");
                int near = 0;
                for (int i = 0; i < 6; i++) {
                    if (p[i].distanceTo(young) < 8) {
                        near++;
                    }
                }
                check(near == 6, "riders did not follow (" + near + ")");
                p[1].stopRiding();
                check(p[1].getVehicle() == young, "a rider got off in flight");
                // 地形の上を飛ぶ (プラットフォームの上の高さ以上)
                check(young.getY() > groundAt(level, young.getX(), young.getZ()) - 1, "the young one flew into the ground");
                wait = 0;
                stage = 4;
            }
            case 4 -> {
                if (young.trip().phase() != YoungTrip.Phase.OFFER) {
                    if (++wait > 1200) {
                        F.add("never arrived (" + young.trip().phase() + " at " + young.blockPosition() + ")");
                        cleanup(server);
                        report("young");
                        return true;
                    }
                    return false;
                }
                double dist = Math.hypot(young.getX() - (start.x + 170), young.getZ() - start.z);
                check(dist < 12, "arrived far from the destination (" + dist + ")");
                double ground = groundAt(level, young.getX(), young.getZ());
                check(young.getY() <= ground + 2, "landed in the air (" + young.getY() + " vs " + ground + ")");
                check(young.riderCount() == 6, "riders lost on the way");
                YoungCarriers.disembark(p[1]);
                check(p[1].getVehicle() != young, "a rider could not get off after arrival");
                check(p[1].distanceTo(young) < 6, "dismount spot too far");
                for (int i = 0; i < 6; i++) {
                    YoungCarriers.disembark(p[i]);
                }
                check(young.riderCount() == 0, "riders remain after disembarking");
                wait = 0;
                stage = 5;
            }
            case 5 -> {
                if (!young.isRemoved()) {
                    if (++wait > 400) {
                        F.add("the young one never dissolved");
                        cleanup(server);
                        report("young");
                        return true;
                    }
                    return false;
                }
                check(YoungCarriers.of(p[0].getUUID()) == null, "the young one is still registered");
                check(Voyages.cooldownLeft(p[0]) > 0, "no cooldown after the trip");
                // 二度目: ログアウト・途中で溶ける
                Voyages.state(p[0]).readyAt = 0;
                for (int i = 0; i < 3; i++) {
                    p[i].setPos(platform.getX() + 0.5 + i, platform.getY() + 1, platform.getZ() + 0.5);
                }
                check(YoungCarriers.whistle(p[0], Titans.STONE_WHALE), "second call refused");
                young = YoungCarriers.of(p[0].getUUID());
                wait = 0;
                stage = 6;
            }
            case 6 -> {
                if (++wait < YoungEntity.APPEAR_TICKS + 3) {
                    return false;
                }
                YoungCarriers.tryBoard(young, p[0]);
                YoungCarriers.whistle(p[1], Titans.STONE_WHALE);
                YoungCarriers.whistle(p[2], Titans.STONE_WHALE);
                check(young.riderCount() == 3, "second flight riders " + young.riderCount());
                start = young.position();
                String refused = YoungCarriers.chooseDirect(p[0], new TravelNetwork.Dest("test", "west", "", (int) start.x - 300, 100, (int) start.z, 1, true, 0, ""));
                check(refused == null, "second choose refused: " + refused);
                wait = 0;
                stage = 7;
            }
            case 7 -> {
                if (young.trip().phase() != YoungTrip.Phase.CARRY || ++wait < 60) {
                    if (wait > 500) {
                        F.add("second flight never started");
                        cleanup(server);
                        report("young");
                        return true;
                    }
                    wait++;
                    return false;
                }
                // ログアウト
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p[1]));
                check(p[1].getVehicle() == null, "a logged out rider is still aboard");
                check(young.riderCount() == 2, "riders after logout " + young.riderCount());
                check(p[1].getPersistentData().contains("skyabyss_voyage_safe"), "no safe spot was recorded at logout");
                // ログイン: 地面へ降ろされる
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p[1]));
                double ground = groundAt(level, p[1].getX(), p[1].getZ());
                check(p[1].getY() <= ground + 3, "relogged player left in the air (" + p[1].getY() + " vs " + ground + ")");
                check(p[1].hasEffect(MobEffects.SLOW_FALLING), "no slow falling after relog");
                // 途中で溶ける: 全員が地面へ
                young.trip().dissolve("blocked", young.trip().riders());
                for (int i : new int[] {0, 2}) {
                    double g = groundAt(level, p[i].getX(), p[i].getZ());
                    check(p[i].getVehicle() == null, "rider " + i + " still aboard after dissolving");
                    check(p[i].getY() <= g + 3, "rider " + i + " left in the air (" + p[i].getY() + " vs " + g + ")");
                }
                wait = 0;
                stage = 8;
            }
            case 8 -> {
                if (!young.isRemoved() && ++wait < 100) {
                    return false;
                }
                check(young.isRemoved(), "the dissolving young one stayed");
                cleanup(server);
                report("young");
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void cleanup(MinecraftServer server) {
        YoungCarriers.endAll();
        for (YoungEntity e : YoungCarriers.all()) {
            e.discard();
        }
        TitanDormancy.clear(server, Titans.STONE_WHALE);
        TitanDormancy.clear(server, Titans.THUNDER_WING);
        PartyData data = PartyData.get(server);
        if (party != null) {
            data.disband(party);
            party = null;
        }
    }

    // ---------------------------------------------------------------- 巨神ごとの得意・成体の体の外・座席の式

    private static void rules(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // 座席: 六つ・重ならない・横の符号は左右
        for (YoungKind k : YoungKind.values()) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < YoungKind.SEATS; i++) {
                double[] s = k.seat(i);
                seen.add(s[0] + "," + s[2]);
                check(s[1] > 0.5, k + " seat too low");
            }
            check(seen.size() == 6, k + " seats overlap");
            check(YoungKind.ofTitan(k.titan) == k, k + " titan lookup");
        }
        Vec3 from = new Vec3(0, 100, 0);
        // どの子にも遠すぎる行き先
        for (YoungKind k : YoungKind.values()) {
            BlockPos far = new BlockPos(k.maxRange + 500, 70, 0);
            check(YoungReach.refuse(level, k, from, far).equals("too_far"), k + " accepted a destination beyond its range");
        }
        // 海と陸を探す (生地形の biome だけ。粗い格子)
        BlockPos ocean = null;
        BlockPos land = null;
        for (int r = 200; r <= 2000 && (ocean == null || land == null); r += 100) {
            for (int a = 0; a < 16 && (ocean == null || land == null); a++) {
                int x = (int) (Math.cos(a * Math.PI / 8) * r);
                int z = (int) (Math.sin(a * Math.PI / 8) * r);
                BlockPos at = new BlockPos(x, level.getSeaLevel(), z);
                BlockPos sea = YoungReach.seaNear(level, at);
                if (sea != null && sea.getX() == x && sea.getZ() == z) {
                    if (ocean == null) {
                        ocean = at;
                    }
                } else if (land == null) {
                    land = at;
                }
            }
        }
        if (ocean != null) {
            check(YoungReach.refuse(level, YoungKind.WHALE, from, ocean).isEmpty(), "the whale calf refused an ocean destination");
            check(YoungReach.refuse(level, YoungKind.STORM, from, ocean).equals("no_land"), "the fledgling accepted an ocean destination");
            check(YoungReach.refuse(level, YoungKind.EMBER, from, ocean).equals("water"), "the whelp accepted an ocean destination");
            check(YoungReach.refuse(level, YoungKind.TIDE, from, ocean).isEmpty(), "the hatchling refused an ocean destination");
        } else {
            SkyAbyss.LOGGER.warn("SELFTEST young rules: no ocean found within 2000 blocks; ocean limits not checked");
        }
        if (land != null) {
            check(YoungReach.refuse(level, YoungKind.WHALE, from, land).isEmpty(), "the whale calf refused a land destination");
        }
        // 成体の体の外: 岩鯨が止まっているとして、止まる所は立入禁止の外
        Vec3 at = new Vec3(300, 130, 300);
        TitanVolume volume = TitanVolume.get("stone_whale");
        YoungReach.Pad pad = YoungReach.settle(level, YoungKind.WHALE, 400, 300);
        Vec3 local = TitanSolids.toLocal(at.x, at.y, at.z, 0f, new Vec3(pad.pos().x, pad.pos().y, pad.pos().z));
        check(!volume.inKeepOut(local.x, local.y, local.z), "the pad is inside the whale's body");
        // 道: 溶岩・深い水・木の葉でなく、地面に降りる
        check(pad.pos().y >= level.getMinBuildHeight(), "pad below the world");
        report("young rules");
    }
}
