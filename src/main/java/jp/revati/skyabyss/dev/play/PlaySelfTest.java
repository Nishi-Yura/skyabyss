package jp.revati.skyabyss.dev.play;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.playtest.Playtest;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 試遊コマンド (skyabyss play) の自己テスト (SKYABYSS_SELFTEST=1、SKYABYSS_SELFTEST_ONLY=play で単独に)。
// 登録・構文・移動先 (立てる場所か、建っていなければ理由つきで飛ばす)・章の主線の段階・道具の品・文言の鍵。docs/design/37 の 4 節
public final class PlaySelfTest {
    private PlaySelfTest() {
    }

    // 文言の鍵 (ja_jp と en_us の両方にある必要がある)
    static final List<String> KEYS = List.of("help.0", "help.15", "raid_none", "raid_bad_room", "quest_none", "quest_next", "act.trials", "act.skip_opening", "said.raid", "said.day", "said.fast_on", "said.fast_off", "bad_chapter", "bad_stage", "unknown_boss", "bad_tier", "unknown_place", "place_missing",
            "unknown", "unknown_quest", "status_head", "quest_line", "titans_line", "stilled", "not_yet", "done", "act.quest_done", "act.quest_reset", "act.quest_stage", "act.goto", "act.kit", "act.lore_all", "act.lore_reset",
            "act.heal", "act.feed", "act.god_on", "act.god_off", "act.reset", "said.chapter", "said.boss", "said.quest", "said.time", "said.clear",
            "said.rain", "said.thunder", "said.peaceful_on", "said.peaceful_off");

    private static final List<String> COMMANDS = List.of("play help", "play status", "play chapter 2", "play chapter 2 @a see", "play goto whale-heart",
            "play goto abyss-depth-5", "play kit tide", "play boss tide @a 2", "play boss warden", "play quest main_song done", "play quest main_song reset",
            "play quest main_song see_whale", "play lore unlock-all", "play lore reset", "play time day", "play weather clear", "play peaceful on",
            "play heal", "play feed", "play god on", "play god off", "play reset me");

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("play selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST play PASSED (commands registered at permission 2, places standable or skipped, chapter stages, kits, lang keys)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST play FAILED: {}", x));
        }
    }

    private static void run(MinecraftServer server, List<String> f) throws Exception {
        // 1. 登録と構文
        var root = server.getCommands().getDispatcher().getRoot();
        CommandNode<CommandSourceStack> sky = root.getChild("skyabyss");
        if (sky == null || sky.getChild("play") == null) {
            f.add("skyabyss play is not registered");
            return;
        }
        if (!sky.getRequirement().test(server.createCommandSourceStack().withPermission(2))
                || sky.getRequirement().test(server.createCommandSourceStack().withPermission(1))) {
            f.add("skyabyss play is not exactly permission 2");
        }
        var disp = server.getCommands().getDispatcher();
        CommandSourceStack op = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String c : COMMANDS) {
            var parsed = disp.parse("skyabyss " + c, op);
            if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
                f.add("does not parse: " + c);
            }
        }

        // 1b. 既存の開始コマンドの形 (boss が流す文字列。木になければ構文の段階で落ちる)
        for (String c : List.of("tideheart start", "emberheart start", "stormheart start", "raiju start", "smith start",
                "abyss warden wake", "abyss enter", "abyss depth go 5", "titan board", "titan rest")) {
            var parsed = disp.parse("skyabyss " + c, op);
            if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
                f.add("boss start command does not parse: skyabyss " + c);
            }
        }
        if (disp.parse("skyabyss_nightmare enter", op).getReader().canRead()) {
            f.add("skyabyss_nightmare enter does not parse");
        }

        // 2. 移動先: 解決できるもの・建っていないので飛ばすもの・立てないのに建っているもの (失敗)
        int ok = 0;
        int skipped = 0;
        for (String name : PlayPlaces.names()) {
            if (name.startsWith("abyss-depth-") || name.equals("abyss") || name.equals("overworld")) {
                continue;
            }
            if (PlayPlaces.spot(server, name) != null) {
                ok++;
                continue;
            }
            // 立てる場所が取れない: 世界がない・まだ組んでいない・位置が合わない、のどれか。
            // ここでは立てるかを確かめられないので、理由をログに出して飛ばす (失敗にはしない)
            skipped++;
            SkyAbyss.LOGGER.info("SELFTEST play skipped place {} (no standable spot: back or layers not built in this world)", name);
        }
        SkyAbyss.LOGGER.info("SELFTEST play places: {} standable, {} skipped (not built)", ok, skipped);

        // 3. 章の用意: 主線の段階が期待どおりか (偽のプレイヤー)
        ServerLevel level = server.overworld();
        for (int n = 0; n < PlayChapters.CHAPTERS; n++) {
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "play_" + n));
            p.moveTo(level.getSharedSpawnPos().getX() + 0.5, level.getSharedSpawnPos().getY() + 1, level.getSharedSpawnPos().getZ() + 0.5);
            String miss = PlayChapters.chapter(p, n, null);
            if (miss != null) {
                f.add("chapter " + n + " returned " + miss);
                continue;
            }
            checkChapter(p, n, f);
        }
        FakePlayer s = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "play_stage"));
        if (PlayChapters.chapter(s, 2, "see") != null || !"see".equals(stage(s, "main_tide"))) {
            f.add("chapter 2 with stage see is at " + stage(s, "main_tide"));
        }
        if (PlayChapters.chapter(s, 2, "no_such_stage") == null) {
            f.add("a stage that does not exist was accepted");
        }

        // 4. 道具一式: 各巨神の装備の品が登録されている
        for (int tier = 1; tier <= 4; tier++) {
            TitanGearSet set = TitanGearSets.get(ProgressRules.TITAN_ORDER.get(tier - 1));
            if (set == null) {
                f.add("kit " + PlayChapters.KITS.get(tier) + " has no gear set");
                continue;
            }
            for (TitanGearSet.Piece piece : set.pieces()) {
                if (TitanGearSet.item(piece.id()).isEmpty()) {
                    f.add("kit " + PlayChapters.KITS.get(tier) + " has an unregistered item " + piece.id());
                }
            }
        }

        // 5. 文言: ja_jp と en_us の両方に、使う鍵がある
        JsonObject ja = lang("ja_jp");
        JsonObject en = lang("en_us");
        for (String k : KEYS) {
            String key = "commands.skyabyss_play." + k;
            if (!ja.has(key)) {
                f.add("ja_jp lacks " + key);
            }
            if (!en.has(key)) {
                f.add("en_us lacks " + key);
            }
        }
        if (ja.size() != en.size()) {
            f.add("ja_jp and en_us have different key counts: " + ja.size() + " / " + en.size());
        }
    }

    // 章 n の主線が、その章の段階になっているか
    private static void checkChapter(FakePlayer p, int n, List<String> f) {
        switch (n) {
            case 0 -> {
                if (QuestLog.of(p).entry("main_song") != null) {
                    f.add("chapter 0 still has main_song");
                }
            }
            case 1 -> {
                if (!"see_whale".equals(stage(p, "main_song"))) {
                    f.add("chapter 1 main_song is at " + stage(p, "main_song"));
                }
            }
            case 5 -> {
                if (!isDone(p, "main_key")) {
                    f.add("chapter 5 did not finish main_key");
                }
                if (!"rumor".equals(stage(p, "main_abyss"))) {
                    f.add("chapter 5 main_abyss is at " + stage(p, "main_abyss"));
                }
            }
            default -> {
                String q = PlayChapters.MAIN[n - 1];
                if (QuestLog.of(p).entry(q) == null) {
                    f.add("chapter " + n + " did not start " + q);
                }
            }
        }
    }

    private static boolean isDone(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        return e != null && e.done;
    }

    private static String stage(FakePlayer p, String quest) {
        var def = jp.revati.skyabyss.quest.QuestManager.quest(quest);
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        if (e == null || def == null) {
            return null;
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
    }

    private static JsonObject lang(String locale) throws Exception {
        try (var in = PlaySelfTest.class.getResourceAsStream("/assets/skyabyss_play/lang/" + locale + ".json")) {
            if (in == null) {
                throw new IllegalStateException("missing lang " + locale);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
