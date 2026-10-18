package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Look;
import jp.revati.skyabyss.cinematic.Scene;
import jp.revati.skyabyss.cinematic.Shot;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 初回と周回の共通部品 (FirstRun) の自己テスト。段の名前は "back first" (SKYABYSS_SELFTEST_ONLY="back first" でこれだけ流せる)。
// first_cleared の前後・グループごと・パーティの組む入る抜ける・リセットで残る・戸と光る床と静かな通路・任意の翼・
// 場面が人ごとに一度・追憶の宝が人ごとに一度で装備は拒む・素材は繰り返す
public final class FirstRunSelfTest {
    private static final String ROOM = "selftest_first_room";
    private static final String WING = "selftest_first_wing";
    private static final String SCENE = "selftest_first_scene";
    private static final String KEEPSAKE = "selftest_keepsake";

    private FirstRunSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        List<UUID> groups = new ArrayList<>();
        BlockPos door = new BlockPos(5000, 100, 5000);
        BlockPos floor = new BlockPos(5001, 99, 5000);
        FakePlayer a = null;
        FakePlayer b = null;
        FakePlayer c = null;
        try {
            if (back == null) {
                f.add("titan_back dimension is missing");
            } else {
                parties.clearAll();
                back.getChunk(door);
                GroupGates.place(back, List.of(door), false);
                a = player(back, "first_a");
                b = player(back, "first_b");
                c = player(back, "first_c");
                UUID ga = a.getUUID();
                UUID gb = b.getUUID();
                groups.addAll(List.of(ga, gb, c.getUUID()));
                FirstRun.register(FirstRun.Room.firstRun(ROOM, back.dimension(), new AABB(4990, 90, 4990, 5010, 110, 5010)).doors(List.of(door))
                        .guide(List.of(floor)));
                FirstRun.register(FirstRun.Room.optional(WING, back.dimension(), new AABB(4900, 90, 4900, 4950, 110, 4950)));
                BlockPos inRoom = new BlockPos(5000, 100, 5000);
                BlockPos inWing = new BlockPos(4910, 100, 4910);

                // 初回 (first_cleared 前): 戸は閉じ、光る床なし、敵は出る
                check(f, FirstRun.isFirstRun(back, ga) && FirstRun.isFirstRun(back, gb), "a fresh group was not on its first run");
                check(f, !FirstRun.isKnownPath(back, ga, ROOM) && FirstRun.allowSpawn(back, ga, ROOM) && FirstRun.allowSpawnAt(back, ga, inRoom),
                        "the first-run room was quiet before first_cleared");
                check(f, !GroupGates.isOpen(back, ga, door), "the first-run door was open before first_cleared");
                check(f, !GroupOverlays.compute(back, ga).containsKey(floor), "the guide floor showed before first_cleared");

                // A のグループだけが心臓を止める
                FirstRun.onFirstCleared(back, List.of(ga));
                BackGroupData.Record ra = BackGroupData.get(back).of(ga);
                check(f, ra.isFirstCleared() && ra.flag("keep.first_cleared"), "first_cleared was not recorded under keep.");
                check(f, !FirstRun.isFirstRun(back, ga) && FirstRun.isFirstRun(back, gb), "first_cleared leaked to another group");
                check(f, FirstRun.isKnownPath(back, ga, ROOM) && !FirstRun.isKnownPath(back, gb, ROOM), "the known path is not per group");
                check(f, !FirstRun.allowSpawn(back, ga, ROOM) && !FirstRun.allowSpawnAt(back, ga, inRoom), "the known path still spawns enemies");
                check(f, FirstRun.allowSpawn(back, gb, ROOM) && FirstRun.allowSpawnAt(back, gb, inRoom), "the other group's room went quiet");
                check(f, GroupGates.isOpen(back, ga, door) && !GroupGates.isOpen(back, gb, door), "the known-path door is not open for exactly that group");
                BlockState glow = GroupOverlays.compute(back, ga).get(floor);
                check(f, glow != null && glow.is(Blocks.SEA_LANTERN) && !GroupOverlays.compute(back, gb).containsKey(floor),
                        "the guide floor is not shown to exactly that group");
                // 任意の翼は初回でも周回でも変わらない
                check(f, !FirstRun.isKnownPath(back, ga, WING) && FirstRun.allowSpawn(back, ga, WING) && FirstRun.allowSpawnAt(back, ga, inWing),
                        "an optional wing went quiet");
                check(f, FirstRun.allowSpawnAt(back, ga, new BlockPos(0, 100, 0)), "a spot outside every room went quiet");

                // 試練のリセットでは残る。戸の上書きは消えるが、すぐ開け直す
                GroupProgress.reset(back, ga);
                check(f, BackGroupData.get(back).of(ga).isFirstCleared(), "the trial reset erased first_cleared");
                check(f, !GroupGates.isOpen(back, ga, door), "the reset did not clear the door override (test setup)");
                FirstRun.syncDoors(back, ga);
                check(f, GroupGates.isOpen(back, ga, door), "the known-path door did not reopen after the reset");

                // パーティ: A (周回) が組む → パーティは周回から始まる。B が入る → B もパーティの記録 (周回の道)
                PartyData.Party party = parties.create(ga, "first_a");
                groups.add(party.id);
                check(f, !FirstRun.isFirstRun(back, party.id), "a party led by a cleared group started on a first run");
                parties.add(party, gb, "first_b");
                check(f, !FirstRun.isFirstRun(back, Groups.keyOf(server, gb)), "a first-run member joining a cleared party did not use the party's path");
                // 抜けると B の一人の記録はパーティの写し (進みを取り上げない)
                parties.remove(gb);
                check(f, !FirstRun.isFirstRun(back, Groups.keyOf(server, gb)), "a member who left lost the party's first_cleared");
                parties.remove(ga);
                // 初回のパーティに周回の人が入ると、道はパーティの記録 (初回) になる
                PartyData.Party fresh = parties.create(c.getUUID(), "first_c");
                groups.add(fresh.id);
                check(f, FirstRun.isFirstRun(back, fresh.id), "a party led by a first-run group was not on a first run");
                parties.add(fresh, ga, "first_a");
                check(f, FirstRun.isFirstRun(back, Groups.keyOf(server, ga)), "a cleared member joining a first-run party did not follow the party's path");
                parties.remove(ga);

                // 場面: 人ごとに一度 (cine_seen.<名前>)。見た人には出ず、見ていない人には出る。未登録の場面は見たことにしない
                check(f, !FirstRun.playFirstScene(a, SCENE, null, null) && !FirstRun.sceneSeen(a, SCENE), "an unregistered scene played or was recorded");
                FirstRun.registerScene(Scene.of(SCENE).shot(Shot.of(1f).pos(0, 2, -4).posTo(0, 2, -3).look(Look.frame(0, 1, 0)).lookTo(Look.frame(0, 1, 0)).build())
                        .build());
                List<Boolean> ended = new ArrayList<>();
                FirstRun.onSceneEnd(SCENE, (p, skipped) -> ended.add(skipped));
                check(f, FirstRun.playFirstScene(a, SCENE, null, null), "the first-run scene did not play for a new player");
                check(f, FirstRun.sceneSeen(a, SCENE) && Cinematics.state(a).seen(FirstRun.seenKey(SCENE)), "cine_seen.<name> was not recorded");
                Cinematics.end(a, SCENE, true);
                check(f, ended.size() == 1 && ended.get(0), "the skipped scene's end hook did not run");
                check(f, !FirstRun.playFirstScene(a, SCENE, null, null), "the first-run scene played twice for one player");
                check(f, FirstRun.playFirstScene(b, SCENE, null, null) && !FirstRun.sceneSeen(c, SCENE), "the scene record is not per player");
                Cinematics.stop(b, false);

                // 追憶の宝: 人ごとに一度、装備は拒む。素材は繰り返す
                var store = FirstRunData.get(server);
                store.forgetKeepsake(a.getUUID(), KEEPSAKE);
                store.forgetKeepsake(b.getUUID(), KEEPSAKE);
                List<ItemStack> treat = List.of(new ItemStack(Items.BREAD, 2));
                check(f, FirstRun.keepsakeAllowed(treat.get(0)) && !FirstRun.keepsakeAllowed(new ItemStack(ModItems.WHALEBONE_GREATSWORD.get()))
                        && !FirstRun.keepsakeAllowed(new ItemStack(ModItems.STONE_WHALE_HELMET.get())) && !FirstRun.keepsakeAllowed(ItemStack.EMPTY),
                        "keepsakeAllowed let gear through or refused a consumable");
                check(f, !FirstRun.grantKeepsake(a, KEEPSAKE, List.of(new ItemStack(ModItems.WHALEBONE_GREATSWORD.get()))) && !FirstRun.hasKeepsake(a, KEEPSAKE)
                        && a.getInventory().countItem(ModItems.WHALEBONE_GREATSWORD.get()) == 0, "a keepsake with titan gear was granted or marked");
                check(f, FirstRun.grantKeepsake(a, KEEPSAKE, treat) && a.getInventory().countItem(Items.BREAD) == 2, "the keepsake was not granted the first time");
                check(f, !FirstRun.grantKeepsake(a, KEEPSAKE, treat) && a.getInventory().countItem(Items.BREAD) == 2, "the keepsake was granted twice to one player");
                check(f, FirstRun.grantKeepsake(b, KEEPSAKE, treat) && b.getInventory().countItem(Items.BREAD) == 2, "the keepsake is not once per person");
                List<ItemStack> mats = List.of(new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 3));
                FirstRun.grantMaterials(a, mats);
                FirstRun.grantMaterials(a, mats);
                check(f, a.getInventory().countItem(ModItems.STONE_WHALE_SHARD.get()) == 6, "optional-wing materials did not repeat");
                // 周回のグループでも (宝は人ごと): c は初めてなので受け取れる
                check(f, FirstRun.grantKeepsake(c, KEEPSAKE, treat), "a first-timer in a cleared group could not take the keepsake");
                store.forgetKeepsake(a.getUUID(), KEEPSAKE);
                store.forgetKeepsake(b.getUUID(), KEEPSAKE);
                store.forgetKeepsake(c.getUUID(), KEEPSAKE);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST back first crashed", e);
            f.add("crashed: " + e);
        } finally {
            parties.restore(snapshot, server.registryAccess());
            FirstRun.unregister(ROOM);
            FirstRun.unregister(WING);
            jp.revati.skyabyss.cinematic.Scenes.unregister(SCENE);
            for (FakePlayer p : java.util.Arrays.asList(a, b, c)) {
                if (p != null) {
                    Cinematics.stop(p, false);
                    Cinematics.state(p).forgetAll();
                }
            }
            if (back != null) {
                back.setBlock(door, Blocks.AIR.defaultBlockState(), 2);
                for (UUID g : groups) {
                    BackGroupData.get(back).remove(g);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST back first PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST back first FAILED: {}", m));
        }
    }
}
