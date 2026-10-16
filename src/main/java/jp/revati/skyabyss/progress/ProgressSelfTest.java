package jp.revati.skyabyss.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

// 物語の進みとパーティの自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー、起動直後に一度。止めるのは TitanBackSelfTest)。
// 巨神の順番・段階・レベルの上限・世界の進み・乗る資格 (入口とディメンション移動)・ゲームルール・パーティの記録と分け前を確かめる。
// ワールドの記録は写し取ってから試し、最後に元へ戻す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ProgressSelfTest {
    private ProgressSelfTest() {
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        List<String> failures = new ArrayList<>();
        try {
            run(event.getServer(), failures);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("progress selftest crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST progress PASSED (order, stage, level cap, world gate, boarding gate, dimension gate, gamerules, party data, invites, share)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST progress FAILED: {}", f));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        // ---------------------------------------------------------------- 決まりの数値
        check(f, ProgressRules.previousTitan("stone_whale") == null, "the stone whale has a previous titan");
        check(f, "stone_whale".equals(ProgressRules.previousTitan("tide_shell")), "tide shell does not follow the stone whale");
        check(f, ProgressRules.canChallenge(0, "stone_whale") && !ProgressRules.canChallenge(0, "tide_shell")
                && ProgressRules.canChallenge(1, "tide_shell") && !ProgressRules.canChallenge(1, "thunder_wing"), "canChallenge is wrong");
        check(f, !ProgressRules.canChallenge(4, "unknown"), "unknown titans can be challenged");
        check(f, ProgressRules.stage(t -> t.equals("tide_shell")) == 0, "a gap in the order still counted");
        check(f, ProgressRules.stage(t -> !t.equals("ember")) == 3, "stage of three titans is wrong");
        for (int i = 0; i < ProgressRules.TITAN_LEVEL.length; i++) {
            // 上限は次の巨神の推奨 +3 (減り始める所)
            check(f, ProgressRules.levelCap(i) == ProgressRules.TITAN_LEVEL[i] + GrowthRules.FALLOFF_GRACE, "cap " + i + " is not recommended + grace");
        }
        check(f, ProgressRules.levelCap(99) == ProgressRules.LEVEL_CAP[4] && ProgressRules.levelCap(-1) == 15, "cap is not clamped");
        check(f, GrowthRules.VANILLA_DAILY == 100, "vanilla daily limit is " + GrowthRules.VANILLA_DAILY);

        // ---------------------------------------------------------------- 上限で止まる経験値
        GrowthData d = new GrowthData();
        Growth.addXpRaw(d, 1_000_000L, 15);
        check(f, d.level == 15 && d.xp == GrowthRules.xpToNext(15) - 1, "capped gain should stop at 15 with a nearly full bar: " + d.level + "/" + d.xp);
        Growth.addXpRaw(d, 500, 15);
        check(f, d.level == 15, "gain at the cap raised the level");
        check(f, Growth.addXpRaw(d, 1, 21) == 1 && d.level == 16 && d.xp == 0, "raising the cap did not release the banked bar: " + d.level + "/" + d.xp);
        GrowthData high = new GrowthData();
        high.level = 20;
        check(f, Growth.addXpRaw(high, 10, 15) == 0 && high.level == 20, "a level above the cap was lowered");

        // ---------------------------------------------------------------- 世界の進み・個人の進み (記録を写して試す)
        ProgressData data = ProgressData.get(server);
        CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
        GameRules rules = server.getGameRules();
        boolean gateBefore = rules.getBoolean(Progression.STORY_GATE);
        int shareBefore = rules.getInt(Parties.PARTY_SHARE);
        PartyData parties = PartyData.get(server);
        CompoundTag partiesSaved = parties.snapshot(server.registryAccess());
        ServerLevel overworld = server.overworld();
        try {
            rules.getRule(Progression.STORY_GATE).set(true, server);
            for (String t : ProgressRules.TITAN_ORDER) {
                data.clearWorld(t);
            }
            FakePlayer a = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "progress_test_a"));
            FakePlayer b = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "progress_test_b"));
            FakePlayer c = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "progress_test_c"));
            check(f, Progression.stage(a) == 0, "a new player has a stage");
            check(f, Progression.worldAllows(server, "stone_whale"), "the stone whale is gated");
            check(f, !Progression.worldAllows(server, "tide_shell"), "the tide shell may appear before the stone whale's heart");
            check(f, Progression.canBoard(a, "stone_whale"), "a new player cannot board the stone whale");
            check(f, !Progression.canBoard(a, "tide_shell"), "a new player can board the tide shell");

            Progression.onTitanStopped(server, List.of(a.getUUID()), "stone_whale");
            check(f, Progression.stage(a) == 1 && Progression.stage(b) == 0, "stopping the heart counted for the wrong person");
            check(f, data.worldStopped("stone_whale") && Progression.worldAllows(server, "tide_shell"), "the world did not move on");
            check(f, !Progression.worldAllows(server, "thunder_wing"), "thunder wing woke without the tide shell");
            check(f, Progression.worldStage(server) == 1, "world stage is " + Progression.worldStage(server));
            check(f, Progression.canBoard(a, "tide_shell"), "the one who stopped the heart cannot board the tide shell");
            check(f, !Progression.canBoard(b, "tide_shell"), "a player who did not stop the heart can board the tide shell");
            check(f, ProgressRules.levelCap(Progression.stage(a)) == 21 && ProgressRules.levelCap(Progression.stage(b)) == 15, "caps by stage are wrong");

            // 抜け道: 後の巨神だけの記録では進まない
            data.markPersonal(c.getUUID(), "tide_shell");
            check(f, Progression.stage(c) == 0 && !Progression.canBoard(c, "thunder_wing") && !Progression.canBoard(c, "tide_shell"),
                    "a record out of order opened the way");

            // 入口: 潮門・コマンドの乗り込み (TideTravel.board) は断る。ディメンション移動 (/tp など) は取り消す
            check(f, !TideTravel.board(b) && b.level() == overworld, "the tide gate let in a player without the stone whale");
            EntityTravelToDimensionEvent toTide = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(b, TideTravel.TIDE_BACK));
            check(f, toTide.isCanceled(), "travel to the tide back was not cancelled");
            EntityTravelToDimensionEvent okTide = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(a, TideTravel.TIDE_BACK));
            check(f, !okTide.isCanceled(), "travel to the tide back was cancelled for an eligible player");
            EntityTravelToDimensionEvent toWhale = NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(b, TitanTravel.TITAN_BACK));
            check(f, !toWhale.isCanceled(), "travel to the stone whale back was cancelled");
            // クリエイティブは問わない
            b.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            check(f, Progression.canBoard(b, "tide_shell"), "creative players are gated");
            b.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);

            // ゲームルールで切る
            rules.getRule(Progression.STORY_GATE).set(false, server);
            check(f, Progression.canBoard(b, "ember") && Progression.worldAllows(server, "ember"), "the gamerule did not open the gate");
            rules.getRule(Progression.STORY_GATE).set(true, server);
            check(f, !Progression.canBoard(b, "tide_shell"), "the gate did not close again");

            // 記録の保存と読み込み
            CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
            check(f, tag.getCompound("world").contains("stone_whale")
                    && tag.getCompound("players").getList(a.getUUID().toString(), 8).size() == 1, "progress did not save");

            // ---------------------------------------------------------------- パーティ
            partyTests(server, overworld, f, a, b, c);
        } finally {
            data.restore(saved, server.registryAccess());
            rules.getRule(Progression.STORY_GATE).set(gateBefore, server);
            rules.getRule(Parties.PARTY_SHARE).set(shareBefore, server);
            parties.restore(partiesSaved, server.registryAccess());
        }
    }

    private static void partyTests(MinecraftServer server, ServerLevel overworld, List<String> f, FakePlayer a, FakePlayer b, FakePlayer c) {
        PartyData data = PartyData.get(server);
        data.clearAll();
        PartyData.Party party = data.create(a.getUUID(), "a");
        check(f, data.of(a.getUUID()) == party && party.isLeader(a.getUUID()), "create did not make a leader");
        // 招待と受け入れ
        check(f, Parties.invite(a, b), "invite failed");
        check(f, Parties.accept(b) && data.of(b.getUUID()) == party && party.members.size() == 2, "accept did not join");
        check(f, !Parties.invite(b, c), "a non-leader could invite");
        check(f, !Parties.invite(a, b), "a member was invited twice");
        check(f, !Parties.accept(c), "accepted without an invitation");
        check(f, Parties.invite(a, c) && Parties.decline(c) && data.of(c.getUUID()) == null, "decline joined anyway");
        FakePlayer d = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "progress_test_d"));
        FakePlayer e = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "progress_test_e"));
        data.add(party, c.getUUID(), "c");
        data.add(party, d.getUUID(), "d");
        check(f, !Parties.invite(a, e), "a fifth member could be invited");
        // リーダーを渡す・外す
        check(f, Parties.promote(a, b.getUUID(), "b") && party.isLeader(b.getUUID()), "promote failed");
        check(f, !Parties.kick(a, c.getUUID(), "c"), "a former leader could kick");
        check(f, Parties.kick(b, d.getUUID(), "d") && data.of(d.getUUID()) == null && party.members.size() == 3, "kick failed");

        // 分け前の割合 (50〜80 に収める)
        server.getGameRules().getRule(Parties.PARTY_SHARE).set(95, server);
        check(f, Math.abs(Parties.shareRate(server) - 0.8) < 1e-9, "share rate is not clamped to 80");
        server.getGameRules().getRule(Parties.PARTY_SHARE).set(30, server);
        check(f, Math.abs(Parties.shareRate(server) - 0.5) < 1e-9, "share rate is not clamped to 50");
        server.getGameRules().getRule(Parties.PARTY_SHARE).set(70, server);
        check(f, Math.abs(Parties.shareRate(server) - 0.7) < 1e-9, "default share rate is not 70");

        // 居合わせなかった仲間: 近い人 (30)・遠い人 (200)・レベルの離れた人
        data.add(party, d.getUUID(), "d");
        Map<UUID, ServerPlayer> online = new HashMap<>();
        for (FakePlayer p : List.of(a, b, c, d)) {
            online.put(p.getUUID(), p);
            Growth.get(p).level = 12;
        }
        a.setPos(0, 100, 0);
        b.setPos(30, 100, 0);
        c.setPos(200, 100, 0);
        d.setPos(10, 100, 10);
        Growth.get(d).level = 1;
        List<ServerPlayer> mates = Parties.absentMates(overworld, List.of(a), online::get);
        check(f, mates.contains(b), "a nearby party member got no share");
        check(f, !mates.contains(c), "a far party member got a share");
        check(f, !mates.contains(d), "a member more than 10 levels below got a share");
        check(f, !mates.contains(a), "the one present got a share twice");
        check(f, Parties.absentMates(overworld, List.of(e), online::get).isEmpty(), "a stranger's kill was shared");
        // 巨神の背中 (ダンジョン) では、同じ背中にいれば遠くても分ける
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back != null) {
            FakePlayer ba = new FakePlayer(back, new GameProfile(a.getUUID(), "a"));
            FakePlayer bc = new FakePlayer(back, new GameProfile(c.getUUID(), "c"));
            ba.setPos(0, 100, 0);
            bc.setPos(300, 100, 0);
            Growth.get(ba).level = 12;
            Growth.get(bc).level = 12;
            Map<UUID, ServerPlayer> inBack = new HashMap<>(online);
            inBack.put(a.getUUID(), ba);
            inBack.put(c.getUUID(), bc);
            List<ServerPlayer> backMates = Parties.absentMates(back, List.of(ba), inBack::get);
            check(f, backMates.contains(bc), "a party member elsewhere on the same back got no share");
            check(f, !backMates.contains(b), "a party member on the ground got a share from the back");
        } else {
            f.add("titan back dimension is missing");
        }

        // 抜ける: 二人になってから一人抜けると解散
        data.remove(a.getUUID());
        data.remove(d.getUUID());
        check(f, data.of(b.getUUID()) == party && party.members.size() == 2, "leave did not keep the party");
        data.remove(b.getUUID());
        check(f, data.of(c.getUUID()) == null, "a party of one was kept");

        // 保存と読み込み
        PartyData.Party p2 = data.create(a.getUUID(), "a");
        data.add(p2, b.getUUID(), "b");
        CompoundTag tag = data.snapshot(server.registryAccess());
        data.clearAll();
        data.restore(tag, server.registryAccess());
        PartyData.Party loaded = data.of(b.getUUID());
        check(f, loaded != null && loaded.isLeader(a.getUUID()) && loaded.members.size() == 2 && "b".equals(loaded.names.get(b.getUUID())),
                "party did not save and load");
    }
}
