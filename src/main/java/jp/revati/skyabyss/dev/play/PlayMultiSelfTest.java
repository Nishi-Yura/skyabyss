package jp.revati.skyabyss.dev.play;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.trial.TrialRoute;
import jp.revati.skyabyss.trial.TrialRuntime;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import jp.revati.skyabyss.trial.raid.RaidRun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 試遊コマンドの複数人・クエスト・レイド部屋の自己テスト (SKYABYSS_SELFTEST_ONLY=playtest で旧い試遊と一緒に)。
//  - 新しい形がすべて構文を通る (対象つき・対象なし・旧い形)
//  - 偽の二人: quest next がメインとサブを進め (段階と目的の状態が変わる)、set / done / reset が効く
//  - 偽の二人 (同じパーティ): raid が二人を同じ部屋 (指定の段・指定の部屋) へ運び、主線が順路の段階になる
public final class PlayMultiSelfTest {
    private PlayMultiSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("playtest multi", 10, PlayMultiSelfTest::run));
    }

    private static final List<String> COMMANDS = List.of("play quest next", "play quest next @a", "play quest next @a main_song", "play quest list",
            "play quest list @a", "play quest set main_song see_whale", "play quest set main_song see_whale @a", "play quest done main_song @a",
            "play quest reset main_song @a", "play quest main_song done", "play raid whale 2", "play raid whale WR1 @a", "play raid tide wit @a",
            "play chapter 2", "play chapter 2 @a", "play chapter 2 @a see", "play boss tide", "play boss tide @a", "play boss tide @a 2",
            "play trials whale @a", "play skip opening @a", "play now @a", "play day 1", "play fast off", "play reset", "play reset @a", "play reset me",
            "play goto overworld @a", "play kit iron @a", "play status @a", "play heal @a", "play god on @a");

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            parse(server, f);
            quests(server, f);
            raid(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST playtest multi crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST playtest multi PASSED (all forms parse, quest next advances main and sub for two players, raid carries both into the room)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST playtest multi FAILED: {}", x));
        }
    }

    private static void parse(MinecraftServer server, List<String> f) {
        var disp = server.getCommands().getDispatcher();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String c : COMMANDS) {
            var parsed = disp.parse("skyabyss " + c, op);
            if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
                f.add("does not parse: " + c);
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.moveTo(level.getSharedSpawnPos().getX() + 0.5, level.getSharedSpawnPos().getY() + 1, level.getSharedSpawnPos().getZ() + 0.5);
        return p;
    }

    private static int stageIndex(ServerPlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        return e == null ? -1 : e.done ? 9999 : e.stage;
    }

    private static void quests(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer a = fake(level, "pm_a");
        FakePlayer b = fake(level, "pm_b");
        List<ServerPlayer> both = List.of(a, b);
        for (ServerPlayer p : both) {
            check(f, PlayChapters.chapter(p, 1, null) == null, "chapter 1 failed for " + p.getGameProfile().getName());
        }
        // メイン: 追跡中 (なければ進行中のメイン) が、二人とも一段進む。結果の行に前 → 後が出る
        int before = stageIndex(a, "main_song");
        check(f, before >= 0, "main_song is not started after chapter 1");
        for (ServerPlayer p : both) {
            int was = stageIndex(p, "main_song");
            PlayQuests.Result r = PlayQuests.next(p, null);
            check(f, r.ok() && r.line().contains("→"), "quest next (main) did not move " + p.getGameProfile().getName() + ": " + r.line());
            check(f, stageIndex(p, "main_song") > was, "main_song stage did not advance for " + p.getGameProfile().getName());
            check(f, !QuestLog.of(p).tracked().isEmpty(), "nothing is tracked after quest next (objective strip would be empty)");
        }
        // 段を指定 (id つき) で更に進める: 同じ道。完了まで押しても止まらず、完了後は「もう完了」で ok = false
        for (int i = 0; i < 60 && !QuestLog.of(a).entry("main_song").done; i++) {
            PlayQuests.next(a, "main_song");
        }
        check(f, QuestLog.of(a).entry("main_song").done, "quest next x N did not finish main_song");
        check(f, !PlayQuests.next(a, "main_song").ok(), "quest next on a done quest reported ok");
        // サブ: 始まっていなければ始め、もう一度で次の段階へ (サブがなければ飛ばす)
        QuestDef sub = null;
        for (QuestDef d : QuestManager.all()) {
            if (!d.main() && d.stages().size() >= 2 && QuestLog.of(b).entry(d.id()) == null) {
                sub = d;
                break;
            }
        }
        if (sub == null) {
            SkyAbyss.LOGGER.info("SELFTEST playtest multi: no sub quest with 2+ stages found, sub check skipped");
        } else {
            for (ServerPlayer p : both) {
                PlayQuests.Result r1 = PlayQuests.next(p, sub.id());
                check(f, r1.ok() && stageIndex(p, sub.id()) == 0, "quest next did not start sub " + sub.id() + ": " + r1.line());
                PlayQuests.Result r2 = PlayQuests.next(p, sub.id());
                check(f, r2.ok() && stageIndex(p, sub.id()) > 0, "quest next did not advance sub " + sub.id() + ": " + r2.line());
            }
        }
        // set / done / reset (二人とも)
        for (ServerPlayer p : both) {
            check(f, jp.revati.skyabyss.quest.Quests.setStage(p, "main_song", "see_whale") && stageIndex(p, "main_song") >= 0, "set failed");
            PlayChapters.finish(p, "main_song");
            check(f, QuestLog.of(p).entry("main_song").done, "done did not finish main_song for " + p.getGameProfile().getName());
            check(f, !PlayQuests.list(p).stream().anyMatch(l -> l.contains("main_song")), "a done quest is still in the list");
        }
    }

    private static void raid(MinecraftServer server, List<String> f) {
        String titan = Titans.STONE_WHALE;
        RaidRooms.ensureLoaded();
        if (!RaidRooms.active(titan)) {
            SkyAbyss.LOGGER.info("SELFTEST playtest multi: the whale has no raid rooms yet, raid check skipped");
            return;
        }
        PartyData parties = PartyData.get(server);
        CompoundTag snapshot = parties.snapshot(server.registryAccess());
        ServerLevel back = null;
        UUID group = null;
        try {
            jp.revati.skyabyss.dev.StructureDump.ensureBack(server, "titan_back");
            back = server.getLevel(Titans.backOf(titan));
            if (back == null) {
                f.add("the stone whale's back is missing");
                return;
            }
            FakePlayer a = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000003a1"), "pm_raid_a"));
            FakePlayer b = FakePlayerFactory.get(back, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-0000000003b2"), "pm_raid_b"));
            a.setPos(0, 0, 0);
            b.setPos(0, 0, 0);
            parties.clearAll();
            PartyData.Party party = parties.create(a.getUUID(), "pm_raid_a");
            parties.add(party, b.getUUID(), "pm_raid_b");
            group = Groups.keyOf(a);
            clean(back, group);
            TrialRoute.clearAll();
            List<ServerPlayer> pair = List.of(a, b);
            TrialRoute.PLAYERS = l -> pair;
            PlayRaid.prepare(a, 0);
            PlayRaid.prepare(b, 0);
            for (ServerPlayer p : pair) {
                QuestLog.Entry e = QuestLog.of(p).entry("main_song");
                QuestDef def = QuestManager.quest("main_song");
                check(f, e != null && def != null && def.stages().get(e.stage).id().equals("inscriptions"),
                        "raid prepare left main_song at " + (e == null ? "none" : "stage " + e.stage));
            }
            // 段 2 を指定 → 二人とも同じ部屋 (段 2) の中
            String miss = PlayRaid.enter(back, group, titan, pair, "2");
            check(f, miss == null, "raid 2 returned " + miss);
            var run = RaidRun.of(group);
            check(f, run != null && run.step == 2, "no raid run at step 2 (" + (run == null ? "none" : run.step) + ")");
            if (run != null) {
                check(f, run.ctx.cell().bounds().contains(a.position()) && run.ctx.cell().bounds().contains(b.position()),
                        "the two were not both carried into the room: " + a.position() + " / " + b.position());
                check(f, TrialRoute.step(back, group) == 2, "the route record is at step " + TrialRoute.step(back, group));
            }
            // 部屋の id を指定 → その部屋 (枠の段) の中
            String id = RaidRooms.idsOf(titan, jp.revati.skyabyss.trial.Trial.Slot.FIGHT).get(0);
            a.setPos(0, 0, 0);
            b.setPos(0, 0, 0);
            miss = PlayRaid.enter(back, group, titan, pair, id);
            check(f, miss == null, "raid " + id + " returned " + miss);
            run = RaidRun.of(group);
            check(f, run != null && run.ctx.roomId().equals(id), "the room is not " + id + " (" + (run == null ? "none" : run.ctx.roomId()) + ")");
            if (run != null) {
                check(f, run.ctx.cell().bounds().contains(a.position()) && run.ctx.cell().bounds().contains(b.position()),
                        "the two were not both carried into room " + id);
            }
            check(f, PlayRaid.enter(back, group, titan, pair, "nope") != null, "a bad room spec was accepted");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST playtest multi (raid) crashed", e);
            f.add("raid crashed: " + e);
        } finally {
            if (back != null && group != null) {
                clean(back, group);
            }
            TrialRoute.clearAll();
            TrialRoute.PLAYERS = l -> jp.revati.skyabyss.util.Players.snapshot(l.players());
            parties.restore(snapshot, server.registryAccess());
        }
    }

    private static void clean(ServerLevel back, UUID g) {
        RaidRun.clearAll();
        TrialRoute.finish(back, g);
        GroupGates.reset(back, g);
        BackGroupData.get(back).remove(g);
    }
}
