package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.Trial.Slot;
import jp.revati.skyabyss.trial.TrialCodex;
import jp.revati.skyabyss.trial.TrialRoute;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// レイドの部屋の報い (RaidRewards)。SKYABYSS_SELFTEST_ONLY="raid rewards"
// 偽の人で、実際の入口 (部屋・中ボスの間を越えた・崩れた・順路を越えた) を通して見る:
//  - 一人の順路 (石・潮殻) と、二人のパーティの順路: 素材と共鳴の欠片が人ごとに満額 (二重に数えない・割らない)、中ボスの間は結晶も
//  - 居合わせなかった仲間は割合 (半分)、渡せない人 (オフライン) は預かりへ
//  - 称号は一度だけ (full / rooms / nowipe)。崩れがあると nowipe は付かず、無傷の分も付かない
//  - 周回 (初回のあと) は割合が半分、その日に六つを超えたら四分の一
//  - 保存と読み直しで、称号・越えた部屋・その日の回数が残る
public final class RaidRewardsSelfTest {
    private RaidRewardsSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PartyData parties = PartyData.get(server);
        List<UUID> groups = new ArrayList<>();
        ServerLevel back = null;
        try {
            back = server.getLevel(Titans.backOf(Titans.STONE_WHALE));
            if (back == null) {
                f.add("the stone whale's back is missing");
            } else {
                run(server, back, parties, groups, f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raid rewards crashed", e);
            f.add("crashed: " + e);
        } finally {
            if (back != null) {
                for (UUID g : groups) {
                    BackGroupData.get(back).remove(g);
                }
            }
            parties.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid rewards PASSED (solo and two-player rooms: materials and shards per person at full, absent mates at the share, "
                    + "offline to the pending box; replay halves and tires after six a day; full route titles once, nowipe only without a wipe; "
                    + "all six rooms title once; saved and read back)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST raid rewards FAILED: {}", x));
        }
    }

    private static void run(MinecraftServer server, ServerLevel back, PartyData parties, List<UUID> groups, List<String> f) {
        TrialCodex codex = TrialCodex.get(server);
        RandomSource rnd = RandomSource.create(11L);
        long day = back.getGameTime() / RaidRewards.DAY;
        String stone = Titans.STONE_WHALE;
        String tide = Titans.TIDE_SHELL;
        String ember = Titans.EMBER;

        // ---- 1. 一人が歩・智・闘の一つを越える (初回の順路): 素材 2・共鳴の欠片 1 (闘)
        FakePlayer solo = fake(back, "solo");
        TrialRoute.PLAYERS = l -> List.of(solo);
        UUID soloGroup = Groups.keyOf(solo);
        groups.add(soloGroup);
        int m0 = held(solo, RaidRewards.materialOf(stone));
        int s0 = held(solo, shardOf(stone));
        RaidRewards.onRoomCleared(back, soloGroup, stone, room(stone, Slot.FIGHT, 0), Slot.FIGHT);
        check(f, held(solo, RaidRewards.materialOf(stone)) - m0 == RaidRewards.FIGHT_MATERIAL, "solo fight room: materials were not the full " + RaidRewards.FIGHT_MATERIAL);
        check(f, held(solo, shardOf(stone)) - s0 == RaidRewards.FIGHT_SHARDS, "solo fight room: shards were not the full " + RaidRewards.FIGHT_SHARDS);

        // ---- 2. 二人のパーティが同じ闘の部屋を越える: 二人とも満額 (割らない・二重にもしない)
        FakePlayer a = fake(back, "party_a");
        FakePlayer b = fake(back, "party_b");
        parties.clearAll();
        PartyData.Party party = parties.create(a.getUUID(), "party_a");
        parties.add(party, b.getUUID(), "party_b");
        UUID partyGroup = Groups.keyOf(a);
        groups.add(partyGroup);
        TrialRoute.PLAYERS = l -> List.of(a, b);
        int am0 = held(a, RaidRewards.materialOf(tide));
        int bm0 = held(b, RaidRewards.materialOf(tide));
        int as0 = held(a, shardOf(tide));
        int bs0 = held(b, shardOf(tide));
        RaidRewards.onRoomCleared(back, partyGroup, tide, room(tide, Slot.FIGHT, 0), Slot.FIGHT);
        check(f, held(a, RaidRewards.materialOf(tide)) - am0 == RaidRewards.FIGHT_MATERIAL && held(b, RaidRewards.materialOf(tide)) - bm0 == RaidRewards.FIGHT_MATERIAL,
                "two-player fight room: each member should get the full materials (" + RaidRewards.FIGHT_MATERIAL + ")");
        check(f, held(a, shardOf(tide)) - as0 == RaidRewards.FIGHT_SHARDS && held(b, shardOf(tide)) - bs0 == RaidRewards.FIGHT_SHARDS,
                "two-player fight room: each member should get the full shards (" + RaidRewards.FIGHT_SHARDS + ")");

        // ---- 3. 居合わせなかった仲間は割合 (半分)。核の関数で直接 (偽の人は一覧に出ないため)
        FakePlayer c = fake(back, "absent_c");
        FakePlayer d = fake(back, "near_d");
        int cm0 = held(c, RaidRewards.materialOf(stone));
        RaidRewards.grant(server, stone, null, List.of(new RaidRewards.Grant(RaidRewards.materialOf(stone), 4)), List.of(d), List.of(c), false, 0.5, day, rnd);
        check(f, held(c, RaidRewards.materialOf(stone)) - cm0 == 2, "an absent mate should get the share (half of 4 = 2)");

        // ---- 4. 渡せない人 (オフライン) は預かりへ (TitanLoot.handOver)
        UUID offline = UUID.randomUUID();
        RaidRewards.give(server, null, offline, RaidRewards.roomGrants(stone, Slot.WALK), 1.0, rnd);
        check(f, TitanMemoryData.get(server).hasPending(offline), "an offline member's share did not go to the pending box");

        // ---- 5. 周回の割合: 初回のあと (心臓を止めた記録) は半分。その日に六つを超えたら四分の一
        FakePlayer e = fake(back, "replay_e");
        UUID eGroup = Groups.keyOf(e);
        groups.add(eGroup);
        TrialRoute.PLAYERS = l -> List.of(e);
        BackGroupData.get(back).of(eGroup).markFirstCleared();
        int em0 = held(e, RaidRewards.materialOf(stone));
        RaidRewards.onRoomCleared(back, eGroup, stone, room(stone, Slot.FIGHT, 0), Slot.FIGHT);
        check(f, held(e, RaidRewards.materialOf(stone)) - em0 == RaidRewards.FIGHT_MATERIAL / 2, "a replay fight room should give half the materials");
        for (int i = 0; i < RaidRewards.REPLAY_FULL_ROOMS; i++) {
            RaidRewards.onRoomCleared(back, eGroup, stone, room(stone, Slot.FIGHT, 0), Slot.FIGHT);
        }
        // 七つ目以降は四分の一 (2 x 0.25 = 0.5 の端数は確率で 0 か 1)
        int after = held(e, RaidRewards.materialOf(stone)) - em0;
        check(f, after >= 1 + 5 + 0 && after <= 1 + 5 + 1, "replays after six a day should be a quarter; materials " + after);
        check(f, codex.replayRoomsOn(e.getUUID(), day) == RaidRewards.REPLAY_FULL_ROOMS + 1, "the day's replay count is wrong: " + codex.replayRoomsOn(e.getUUID(), day));
        check(f, RaidRewards.replayRate(RaidRewards.REPLAY_FULL_ROOMS - 1) == RaidRewards.REPLAY_RATE
                && RaidRewards.replayRate(RaidRewards.REPLAY_FULL_ROOMS) == RaidRewards.REPLAY_TIRED_RATE, "replay rate table");

        // ---- 6. 一人の順路を初めて全部越える (石): 称号 full と nowipe、無傷の分の共鳴の欠片
        FakePlayer g = fake(back, "solo_route_g");
        UUID gGroup = Groups.keyOf(g);
        groups.add(gGroup);
        TrialRoute.PLAYERS = l -> List.of(g);
        int gm0 = held(g, RaidRewards.materialOf(stone));
        int gs0 = held(g, shardOf(stone));
        int gc0 = held(g, ModItems.RESONANCE_CRYSTAL.get());
        route(back, gGroup, stone, List.of(g), false);
        check(f, held(g, RaidRewards.materialOf(stone)) - gm0 == 1 + 1 + 2 + 2, "a full solo route: materials should be 6, got " + (held(g, RaidRewards.materialOf(stone)) - gm0));
        check(f, held(g, shardOf(stone)) - gs0 == 1 + 1 + 1 + 2 + RaidRewards.NO_WIPE_SHARDS, "a full solo route without a wipe: shards should be 7, got "
                + (held(g, shardOf(stone)) - gs0));
        check(f, held(g, ModItems.RESONANCE_CRYSTAL.get()) - gc0 == RaidRewards.MID_CRYSTALS, "the mid-boss room should give the crystal");
        check(f, codex.titles(g.getUUID()).contains("raid.full.stone_whale") && codex.titles(g.getUUID()).contains("raid.nowipe.stone_whale"),
                "a full solo route without a wipe should give titles full and nowipe: " + codex.titles(g.getUUID()));

        // ---- 7. 二人のパーティが潮殻の順路を越える (崩れなし): 二人とも満額、称号も二人に
        FakePlayer h = fake(back, "pair_h");
        FakePlayer i = fake(back, "pair_i");
        parties.clearAll();
        PartyData.Party pair = parties.create(h.getUUID(), "pair_h");
        parties.add(pair, i.getUUID(), "pair_i");
        UUID pairGroup = Groups.keyOf(h);
        groups.add(pairGroup);
        int hm0 = held(h, RaidRewards.materialOf(tide));
        int im0 = held(i, RaidRewards.materialOf(tide));
        route(back, pairGroup, tide, List.of(h, i), false);
        check(f, held(h, RaidRewards.materialOf(tide)) - hm0 == 6 && held(i, RaidRewards.materialOf(tide)) - im0 == 6,
                "a two-player route: each member should get 6 materials (not split, not doubled): " + (held(h, RaidRewards.materialOf(tide)) - hm0) + " / "
                        + (held(i, RaidRewards.materialOf(tide)) - im0));
        check(f, codex.titles(h.getUUID()).contains("raid.nowipe.tide_shell") && codex.titles(i.getUUID()).contains("raid.nowipe.tide_shell"),
                "both party members should get the nowipe title of the shell");

        // ---- 8. 崩れた順路 (一度でも崩れた): full は付くが nowipe と無傷の分は付かない (余燼)
        FakePlayer j = fake(back, "wiped_j");
        UUID jGroup = Groups.keyOf(j);
        groups.add(jGroup);
        TrialRoute.PLAYERS = l -> List.of(j);
        int js0 = held(j, shardOf(ember));
        RaidRewards.onWipe(back, jGroup);
        route(back, jGroup, ember, List.of(j), false);
        check(f, codex.titles(j.getUUID()).contains("raid.full.ember"), "a wiped full route should still give the full title");
        check(f, !codex.titles(j.getUUID()).contains("raid.nowipe.ember"), "a route with a wipe must not give the nowipe title");
        check(f, held(j, shardOf(ember)) - js0 == 1 + 1 + 1 + 2, "a wiped route should give only the room shards (5), got " + (held(j, shardOf(ember)) - js0));

        // ---- 9. 周回の順路 (初回のあと・崩れなし): 部屋は割合、無傷の分も割合、称号は増えない
        BackGroupData.Record gr = BackGroupData.get(back).of(gGroup);
        gr.remove(RaidRewards.ROUTE);
        gr.remove(RaidRewards.WIPES);
        int gs1 = held(g, shardOf(stone));
        int gm1 = held(g, RaidRewards.materialOf(stone));
        route(back, gGroup, stone, List.of(g), true);
        // 部屋の材料: 歩 1 x0.5・智 1 x0.5 は端数、闘 2 x0.5 = 1、中ボス 2 x0.5 = 1 (+ 端数 0〜2)
        int matReplay = held(g, RaidRewards.materialOf(stone)) - gm1;
        check(f, matReplay >= 2 && matReplay <= 4, "a replay route should give about half the materials (2 to 4), got " + matReplay);
        int shardReplay = held(g, shardOf(stone)) - gs1;
        check(f, shardReplay >= 2 && shardReplay <= 5, "a replay route should give about half the shards (2 to 5), got " + shardReplay);

        // ---- 10. 六つの部屋をすべて越えた人: 称号 rooms は一度だけ
        FakePlayer k = fake(back, "all_rooms_k");
        UUID kGroup = Groups.keyOf(k);
        groups.add(kGroup);
        TrialRoute.PLAYERS = l -> List.of(k);
        for (Slot s : Slot.values()) {
            for (String id : RaidRooms.idsOf(stone, s)) {
                RaidRewards.onRoomCleared(back, kGroup, stone, id, s);
            }
        }
        check(f, codex.titles(k.getUUID()).contains("raid.rooms.stone_whale"), "all six rooms should give the rooms title: " + codex.raidRooms(k.getUUID()));
        check(f, !codex.grantTitle(k.getUUID(), "raid.rooms.stone_whale"), "the rooms title should be granted only once");
        check(f, codex.raidRooms(k.getUUID()).size() == 6, "six different rooms should be counted: " + codex.raidRooms(k.getUUID()).size());

        // ---- 11. 保存と読み直し: 称号・越えた部屋・その日の回数が残る
        CompoundTag tag = new CompoundTag();
        codex.save(tag, server.registryAccess());
        TrialCodex loaded = TrialCodex.load(tag, server.registryAccess());
        check(f, loaded.titles(g.getUUID()).contains("raid.full.stone_whale") && loaded.titles(h.getUUID()).contains("raid.nowipe.tide_shell"),
                "titles did not survive a save");
        check(f, loaded.titles(k.getUUID()).contains("raid.rooms.stone_whale") && loaded.raidRooms(k.getUUID()).size() == 6, "the rooms did not survive a save");
        check(f, loaded.replayRoomsOn(e.getUUID(), day) == RaidRewards.REPLAY_FULL_ROOMS + 1, "the day's replay count did not survive a save");
        check(f, loaded.replayRoomsOn(e.getUUID(), day + 1) == 0, "the replay count should reset on a new day");
        check(f, !loaded.grantTitle(g.getUUID(), "raid.full.stone_whale"), "a title should stay granted once after a save");

    }

    // 順路を一つ通す (歩・智・闘を越え、中ボスの間を越え、心臓を止める)。party は偽の人の一覧
    private static void route(ServerLevel back, UUID group, String titan, List<FakePlayer> party, boolean replay) {
        TrialRoute.PLAYERS = l -> new ArrayList<>(party);
        BackGroupData.Record r = BackGroupData.get(back).of(group);
        if (replay) {
            r.markFirstCleared();
        }
        RaidRewards.onRoomCleared(back, group, titan, room(titan, Slot.WALK, 0), Slot.WALK);
        RaidRewards.onRoomCleared(back, group, titan, room(titan, Slot.WIT, 0), Slot.WIT);
        RaidRewards.onRoomCleared(back, group, titan, room(titan, Slot.FIGHT, 0), Slot.FIGHT);
        RaidRewards.onMidCleared(back, group, titan);
        RaidRewards.onRouteFinished(back, titan, r, party.stream().map(FakePlayer::getUUID).toList());
    }

    // 枠の部屋の id (二案のうち n 番目)
    private static String room(String titan, Slot slot, int n) {
        List<String> ids = RaidRooms.idsOf(titan, slot);
        return ids.get(Math.min(n, ids.size() - 1));
    }

    private static FakePlayer fake(ServerLevel back, String name) {
        FakePlayer p = FakePlayerFactory.get(back, new GameProfile(UUID.randomUUID(), "[raid_rw_" + name + "]"));
        p.setPos(0, 0, 0);
        return p;
    }

    private static Item shardOf(String titan) {
        return RaidRewards.roomGrants(titan, Slot.WALK).get(1).item();
    }

    private static int held(FakePlayer p, Item item) {
        return Pouch.countAll(p, item);
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

}
