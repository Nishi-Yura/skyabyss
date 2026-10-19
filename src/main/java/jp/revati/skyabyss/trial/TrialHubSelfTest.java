package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.end.ReturnBeacons;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 試練を越えたあとの流れ (TrialHub・HubTablets・TrialRegions) の自己テスト。段の名前は "trial hub"。
//  - 範囲: 四体のすべての試練で、石が自分の範囲の中・ほかの範囲の外、鼓動の間は全部の外、休み場が正しい試練の中
//  - 碑: 鼓動の間に三つ、互いに離れて、決まった場所 (置き直しても同じ)。越えたグループの画面だけで光る (見かけの上書き)
//  - 流れ: 越えたら、その試練の中の人だけが鼓動の間へ戻る (ほかの試練・鼓動の間の人は動かない)。別のグループの人は、
//    同じ試練の中にいても動かず、越えたグループの人だけが入り直すと戻される (パーティごとの封)
// 背が建ててある巨神だけ世界の検査をする (建ててなければ範囲だけ)。
public final class TrialHubSelfTest {
    private TrialHubSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("trial hub", 10, TrialHubSelfTest::run));
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static final Map<String, Map<String, String>> REST_KEYS = Map.of(
            Titans.STONE_WHALE, Map.of("w0", "0", "w1", "0", "w2", "0", "e0", "1", "ce1", "1", "e2r", "1", "ckr", "1", "mz0", "2", "mz1", "2"),
            Titans.TIDE_SHELL, Map.of("g0", "west", "g1", "west", "f0", "front"),
            Titans.THUNDER_WING, Map.of("w0", "west", "wm", "west", "w1", "west", "w2", "west", "w4", "west", "e0", "east", "e1", "east", "e3", "east", "f0", "front"),
            Titans.EMBER, Map.of("w0", "west", "w1", "west", "e0", "east", "s0", "south"));

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        String notes = "";
        boolean placedBefore = jp.revati.skyabyss.titan.dungeon.WhaleDeck.placed();
        HubTablets.TEST_CLEARED.clear();
        try {
            for (String titan : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER)) {
                ServerLevel back = server.getLevel(Titans.backOf(titan));
                if (back == null) {
                    f.add(titan + ": the back dimension is missing");
                    continue;
                }
                regions(f, back, titan);
                BlockPos hub = ReturnBeacons.hubOf(titan);
                if (hub != null) {
                    // 鼓動の間のまわりを読み込む (建ててあれば床がある。建ててなければ空気のまま)
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            back.getChunk((hub.getX() >> 4) + dx, (hub.getZ() >> 4) + dz);
                        }
                    }
                }
                // 単独の実行 (ONLY) では、岩鯨のほかの背は建てていない (建てるのは各巨神の背の自己テスト) ので、世界の検査は全体の実行だけ
                boolean only = System.getenv("SKYABYSS_SELFTEST_ONLY") != null;
                boolean ready = hub != null && back.hasChunkAt(hub) && (!only || Titans.STONE_WHALE.equals(titan) || Titans.TIDE_SHELL.equals(titan) && TrialPoolSelfTest.tideBuilt()) && !back.getBlockState(hub.below()).isAir()
                        && (!Titans.STONE_WHALE.equals(titan) || placedBefore);
                if (ready) {
                    world(f, back, titan, hub);
                    notes += " " + titan;
                }
            }
        } catch (RuntimeException e) {
            f.add("exception " + e);
            SkyAbyss.LOGGER.error("SELFTEST trial hub exception", e);
        } finally {
            HubTablets.TEST_CLEARED.clear();
            TrialHub.clearAll();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST trial hub PASSED (world checks:{})", notes.isEmpty() ? " none" : notes);
        } else {
            f.forEach(p -> SkyAbyss.LOGGER.error("SELFTEST trial hub FAILED: {}", p));
        }
    }

    // ---------------------------------------------------------------- 範囲
    private static void regions(List<String> f, ServerLevel back, String titan) {
        List<String> keys = TrialRegions.keys(titan);
        check(f, keys.size() == 3, titan + ": expected three trials");
        BlockPos hub = ReturnBeacons.hubOf(titan);
        for (String key : keys) {
            List<AABB> mine = TrialRegions.of(back, titan, key, null);
            check(f, !mine.isEmpty(), titan + " " + key + ": no region");
            BlockPos stone = TrialRegions.stoneOf(titan, key);
            check(f, stone != null && TrialRegions.inside(mine, Vec3.atCenterOf(stone)), titan + " " + key + ": its stone is outside its region (" + stone + ")");
            for (String other : keys) {
                if (!other.equals(key) && stone != null) {
                    check(f, !TrialRegions.inside(TrialRegions.of(back, titan, other, null), Vec3.atCenterOf(stone)),
                            titan + " " + key + ": its stone is inside the region of " + other);
                }
            }
            if (hub != null) {
                check(f, !TrialRegions.inside(mine, Vec3.atCenterOf(hub)), titan + " " + key + ": the hub is inside the region");
            }
        }
        Map<String, BlockPos> rests = ReturnBeacons.restSpots(titan);
        for (var e : REST_KEYS.get(titan).entrySet()) {
            BlockPos at = rests.get(e.getKey());
            if (at == null) {
                continue;
            }
            check(f, TrialRegions.inside(TrialRegions.of(back, titan, e.getValue(), null), Vec3.atCenterOf(at)),
                    titan + ": rest " + e.getKey() + " is not inside the region of trial " + e.getValue() + " at " + at);
            for (String k : keys) {
                if (!k.equals(e.getValue())) {
                    check(f, !TrialRegions.inside(TrialRegions.of(back, titan, k, null), Vec3.atCenterOf(at)),
                            titan + ": rest " + e.getKey() + " is inside the region of another trial " + k);
                }
            }
        }
        for (String id : List.of("hub", "heart")) {
            BlockPos at = rests.get(id);
            if (at == null) {
                continue;
            }
            for (String k : keys) {
                check(f, !TrialRegions.inside(TrialRegions.of(back, titan, k, null), Vec3.atCenterOf(at)), titan + ": the " + id + " rest is inside the region of " + k);
            }
        }
    }

    // ---------------------------------------------------------------- 碑と流れ
    private static void world(List<String> f, ServerLevel back, String titan, BlockPos hub) {
        // 巨神ごとに、封の見張りの冷めを忘れる (前の巨神で戻した人の記録が、同じ偽の人の次の戻しを止めないように)
        TrialHub.clearAll();
        HubTablets.clearCache();
        List<HubTablets.Spot> spots = HubTablets.spots(back, titan);
        check(f, spots.size() == 3, titan + ": found " + spots.size() + " tablet spots");
        if (spots.size() != 3) {
            return;
        }
        HubTablets.install(back, titan);
        for (HubTablets.Spot s : spots) {
            check(f, back.getBlockState(s.pos()).is(ModBlocks.TRIAL_TABLET.get()), titan + ": tablet missing at " + s.pos());
            check(f, s.pos().distSqr(hub) >= 9, titan + ": a tablet is on the hub rest spot");
            check(f, back.getBlockState(s.pos().above()).isAir() && back.getBlockState(s.pos().above(2)).isAir(), titan + ": a tablet has no head room at " + s.pos());
        }
        // 置き直し・再計算しても同じ場所
        List<HubTablets.Spot> before = List.copyOf(spots);
        HubTablets.clearCache();
        check(f, HubTablets.spots(back, titan).equals(before), titan + ": the tablet spots changed after recomputing");
        HubTablets.install(back, titan);
        // ---- 流れ
        UUID ga = UUID.fromString("7a0b1c00-0000-4000-8000-00000000a001");
        UUID gb = UUID.fromString("7a0b1c00-0000-4000-8000-00000000b002");
        FakePlayer a = FakePlayerFactory.get(back, new GameProfile(ga, "trialhub_a"));
        FakePlayer b = FakePlayerFactory.get(back, new GameProfile(gb, "trialhub_b"));
        FakePlayer c = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1c00-0000-4000-8000-00000000c003"), "trialhub_c"));
        List<String> keys = TrialRegions.keys(titan);
        Vec3 inA = center(TrialRegions.of(back, titan, keys.get(0), null).get(0), back, titan, keys.get(0));
        Vec3 inB = center(TrialRegions.of(back, titan, keys.get(1), null).get(0), back, titan, keys.get(1));
        UUID keyA = Groups.keyOf(a);
        UUID keyB = Groups.keyOf(b);
        check(f, !keyA.equals(keyB), "two fake players share a group");
        a.setPos(inA);
        b.setPos(inB);
        c.setPos(Vec3.atBottomCenterOf(hub));
        // A のグループが試練 0 を越えた。仲間 B (同じグループのつもり) が試練 1、C は鼓動の間
        HubTablets.TEST_CLEARED.add(keyA + "|" + titan + "|" + keys.get(0));
        TrialHub.onCleared(back, keyA, titan, keys.get(0), null);
        check(f, TrialHub.pending(keyA, titan, keys.get(0)), titan + ": the clear did not queue a return");
        TrialHub.onCleared(back, keyA, titan, keys.get(0), null);
        check(f, TrialHub.pendingCount() == 1, titan + ": a double clear queued twice");
        // 待っているあいだは、中にいても封じで戻さない (戻しの前に二重に動かさない)
        check(f, TrialHub.tickSeal(back, titan, List.of(a), back.getGameTime()) == 0, titan + ": the seal bounced a player while the return was pending");
        TrialHub.flushPending(back);
        TrialHub.tickPending(back, back.getGameTime() + 1, g -> List.of(a, b, c));
        check(f, a.position().distanceTo(Vec3.atBottomCenterOf(hub)) < 2.5, titan + ": the player inside the cleared trial was not returned to the hub: " + a.position());
        check(f, b.position().distanceTo(inB) < 0.5, titan + ": a player in another trial was moved");
        check(f, c.position().distanceTo(Vec3.atBottomCenterOf(hub)) < 0.5, titan + ": the player at the hub was moved");
        check(f, TrialHub.pendingCount() == 0, titan + ": the return stayed queued");
        // 越えた人 (試練の中にいた A) にだけ紋が出る。別の試練にいた B・鼓動の間の C には出ない (docs/design/31 の A14)
        check(f, jp.revati.skyabyss.rpg.skill.SkillManager.get(a).glyphClaims.contains(titan + "|" + keys.get(0)), titan + ": the player who cleared the trial got no glyph");
        check(f, jp.revati.skyabyss.rpg.skill.SkillManager.get(b).glyphClaims.isEmpty() && jp.revati.skyabyss.rpg.skill.SkillManager.get(c).glyphClaims.isEmpty(),
                titan + ": a player who did not clear the trial got a glyph");
        // 光る碑: A のグループの画面だけ 0 番目が光る
        Map<BlockPos, BlockState> litA = GroupOverlays.compute(back, keyA);
        Map<BlockPos, BlockState> litB = GroupOverlays.compute(back, keyB);
        HubTablets.Spot s0 = spots.get(0);
        HubTablets.Spot s1 = spots.get(1);
        check(f, litA.containsKey(s0.pos()) && litA.get(s0.pos()).getValue(TrialTabletBlock.LIT), titan + ": the cleared tablet does not light for its own group");
        check(f, !litA.containsKey(s1.pos()), titan + ": an uncleared tablet lit up");
        check(f, !litB.containsKey(s0.pos()), titan + ": the tablet lit for a group that has not cleared it");
        // 封: 越えたグループの人が入り直すと戻される。越えていないグループの人は、同じ場所にいても動かない
        a.setPos(inA);
        b.setPos(inA);
        int bounced = TrialHub.tickSeal(back, titan, List.of(a, b), back.getGameTime() + 1000);
        check(f, bounced == 1, titan + ": expected exactly the clearing group's player to be bounced, got " + bounced + " (inside " + TrialHub.inside(back, keyA, titan, keys.get(0), a)
                + ", cleared " + HubTablets.cleared(back, keyA, titan, keys.get(0)) + ", pending " + TrialHub.pending(keyA, titan, keys.get(0)) + ", at " + a.position() + ")");
        check(f, a.position().distanceTo(Vec3.atBottomCenterOf(hub)) < 2.5, titan + ": the sealed trial did not send its player back to the hub");
        check(f, b.position().distanceTo(inA) < 0.5, titan + ": a player of another party was bounced out of a trial it has not cleared");
        // 「光の中でしゃがむ」手順はない: 試練を越えた合図を送っても、個人の光は出ない
        try {
            jp.revati.skyabyss.quest.Quests.signal(a, jp.revati.skyabyss.quest.free.FreeQuestEvents.TRIAL_CLEARED);
        } catch (RuntimeException e) {
            // 合図の先の記録 (進捗・クエスト) は偽の人では動かないことがある。光が出ないことだけ見る
        }
        check(f, !ReturnBeacons.hasOffer(a.getUUID()), titan + ": a return light was offered after the clear");
        // 後始末
        a.setPos(0, 0, 0);
        b.setPos(0, 0, 0);
        c.setPos(0, 0, 0);
        GroupGates.reset(back, keyA);
    }

    // 範囲の中の、立てる場所の近くの点 (範囲の真ん中の x・z で、石の高さ)
    private static Vec3 center(AABB box, ServerLevel back, String titan, String key) {
        BlockPos stone = TrialRegions.stoneOf(titan, key);
        if (stone != null && box.contains(Vec3.atCenterOf(stone))) {
            return Vec3.atBottomCenterOf(stone);
        }
        return box.getCenter();
    }
}
