package jp.revati.skyabyss.titan.storm.lore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.dungeon.BackWhispers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 雷翼の伝承とクエストの自己テスト (SELFTEST storm lore。12 の 11.2 節の担当 6 の確かめ方)。
//  - 伝承: 雷翼の 12 項目と「雷翼の巨神」があり、文が日英にある。碑文の項目は StormLore の看板のキーを写す。破れた頁から風見の帳が出る
//  - 看板: 碑文 6 枚と入口の一言 3 枚がはみ出さない (SignFit、1 行 90 px)
//  - 囁き: 8 か所それぞれで一度だけ。文が日英にある
//  - クエスト: メイン「嵐を渡る翼」を、本体がなくても伝承と合図だけで通しで終えられる (見る → 乗る → 碑文 2 と風鳴り石 → 心臓 → 報告)。
//    心臓を先に止めた人は背と試練の段を飛ばす。雷翼の背の周回 (日替わり・週替わり) が倒した数で終わり、報酬が出る
// 記録は写して試し、最後に戻す
public final class StormLoreSelfTest {
    private static final String[] LANGS = {"ja_jp", "en_us"};
    static final List<String> ENTRIES = List.of(StormLore.LORE_SIGHT, StormLore.LORE_TITAN, StormLore.LORE_RUINS, StormLore.LORE_COWER,
            StormLore.LORE_LEDGER, StormLore.LORE_RAIJU, StormLore.LORE_STILLED, StormLore.inscriptionLore("tail"), StormLore.inscriptionLore("hub"),
            StormLore.inscriptionLore("west"), StormLore.inscriptionLore("east"), StormLore.inscriptionLore("eye"), StormLore.inscriptionLore("heart"));

    private StormLoreSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ProgressData progress = ProgressData.get(server);
        CompoundTag saved = progress.save(new CompoundTag(), server.registryAccess());
        try {
            lore(f, notes);
            signs(f, notes);
            ServerLevel level = server.overworld();
            whispers(level, f, notes);
            quests(server, level, f, notes);
        } catch (RuntimeException e) {
            f.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST storm lore exception", e);
        } finally {
            progress.restore(saved, server.registryAccess());
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST storm lore: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm lore PASSED ({} lore entries, {} signs, {} whispers, main quest and 2 board quests, {} checks)",
                    ENTRIES.size(), StormLore.INSCRIPTIONS.size() + StormLore.TRIAL_SIGNS.size(), StormWhispers.WHISPERS.size(), notes.size());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST storm lore FAILED: {}", x));
        }
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---------------------------------------------------------------- 伝承
    private static void lore(List<String> f, List<String> notes) {
        List<JsonObject> langs = new ArrayList<>();
        for (String code : LANGS) {
            JsonObject storm = json("/assets/skyabyss_storm/lang/" + code + ".json", f);
            JsonObject story = json("/assets/skyabyss_story/lang/" + code + ".json", f);
            if (storm != null && story != null) {
                story.entrySet().forEach(e -> {
                    if (!storm.has(e.getKey())) {
                        storm.add(e.getKey(), e.getValue());
                    }
                });
                langs.add(storm);
            }
        }
        for (String id : ENTRIES) {
            LoreEntry e = LoreManager.catalog().entry(id);
            if (e == null) {
                f.add("missing lore " + id);
                continue;
            }
            check(e.chapter().equals("titan_storm") && e.unlockable(), id + " should be a chapter 3 (titan_storm) entry", f);
            for (JsonObject lang : langs) {
                for (String key : List.of(e.titleKey(), e.bodyKey())) {
                    check(lang.has(key) && !lang.get(key).getAsString().isBlank(), "missing text " + key, f);
                }
            }
        }
        // 碑文の項目は、StormLore の看板の 4 行を写す (背中の担当はこのキーで看板を立てる)
        StormLore.INSCRIPTIONS.forEach((place, sign) -> {
            LoreEntry e = LoreManager.catalog().entry(StormLore.inscriptionLore(place));
            check(e != null && e.inscriptionSign().equals(sign) && e.unlock().stream().anyMatch(t -> t.is("sign", sign)),
                    "inscription " + place + " does not copy " + sign, f);
        });
        // 本文が言いすぎない: 「見る者」「留まる者」「深淵」の名は出さない (12 の 9 章: 役目は明かさない)
        for (JsonObject lang : langs) {
            for (String id : ENTRIES) {
                LoreEntry e = LoreManager.catalog().entry(id);
                String body = e == null || !lang.has(e.bodyKey()) ? "" : lang.get(e.bodyKey()).getAsString();
                for (String word : List.of("見る者", "留まる者", "深淵", "watcher", "abyss")) {
                    check(!body.toLowerCase().contains(word), id + " names '" + word + "' outright", f);
                }
            }
        }
        // 破れた頁 (雷乞いが落とす。出どころ storm_back) を読むと風見の帳。ほかの出どころの頁からは出ない
        FakePlayer p = player(null, "a");
        Lore.reset(p);
        check(LorePageItem.candidates(p, StormLore.PAGE_SOURCE).stream().anyMatch(e -> e.id().equals(StormLore.LORE_LEDGER)),
                "a storm_back page cannot give the wind ledger", f);
        check(LorePageItem.candidates(p, "titan_back").stream().noneMatch(e -> e.id().equals(StormLore.LORE_LEDGER)),
                "a stone whale page gives the wind ledger", f);
        notes.add("lore: " + ENTRIES.size() + " entries with text in both languages, inscriptions copy the signs, the ledger comes from storm pages");
    }

    // ---------------------------------------------------------------- 看板
    private static void signs(List<String> f, List<String> notes) {
        List<String> keys = new ArrayList<>(StormLore.INSCRIPTIONS.values());
        keys.addAll(StormLore.TRIAL_SIGNS.values());
        List<String> bad = SignFit.check(keys);
        f.addAll(bad);
        // 碑文は 4 行とも、入口の一言は 2 行だけ埋まっている
        for (String code : SignFit.LANGUAGES) {
            var lang = SignFit.lang(code);
            StormLore.INSCRIPTIONS.values().forEach(k -> {
                for (int i = 1; i <= 4; i++) {
                    check(!lang.getOrDefault(k + "." + i, "").isBlank(), k + "." + i + " is empty in " + code, f);
                }
            });
            StormLore.TRIAL_SIGNS.values().forEach(k -> check(!lang.getOrDefault(k + ".2", "").isBlank()
                    && lang.getOrDefault(k + ".3", "x").isBlank(), k + " should have exactly two lines in " + code, f));
        }
        check(SignFit.allSignKeys().containsAll(keys), "SignFit does not see the storm signs (skyabyss_storm lang)", f);
        notes.add("signs: " + keys.size() + " storm signs, " + bad.size() + " overflowing lines");
    }

    // ---------------------------------------------------------------- 囁き
    private static void whispers(ServerLevel level, List<String> f, List<String> notes) {
        for (String code : LANGS) {
            JsonObject lang = json("/assets/skyabyss_storm/lang/" + code + ".json", f);
            if (lang == null) {
                continue;
            }
            List<String> keys = new ArrayList<>(StormWhispers.keys());
            keys.add(StormWhispers.CAMPFIRE_KEY);
            for (String k : keys) {
                check(lang.has(k) && !lang.get(k).getAsString().isBlank(), code + ": missing whisper " + k, f);
            }
        }
        check(StormWhispers.WHISPERS.size() == 8, "expected 8 whispers, found " + StormWhispers.WHISPERS.size(), f);
        FakePlayer p = player(level, "w");
        forgetWhispers(p);
        for (StormWhispers.Whisper w : StormWhispers.WHISPERS) {
            BlockPos at = center(w.id());
            StormWhispers.Whisper found = StormWhispers.at(at);
            check(found != null && found.id().equals(w.id()), "the middle of " + w.id() + " whispers " + (found == null ? "nothing" : found.id()), f);
            StormWhispers.hearAt(p, at);
            check(BackWhispers.heardAlready(p, w.id()), w.id() + " was not heard", f);
        }
        check(BackWhispers.heardAlready(p, StormWhispers.CAMPFIRE_FLAG), "the campfire hint was not added at the first rest point", f);
        check(StormWhispers.at(new BlockPos(0, 60, 0)) == null, "a whisper outside the bird", f);
        forgetWhispers(p);
        notes.add("whispers: 8 places, each heard once, the campfire hint at the first rest point");
    }

    private static BlockPos center(String id) {
        StormWhispers.Box b = switch (id) {
            case "storm_landing" -> StormWhispers.LANDING;
            case "storm_tail" -> StormWhispers.TAIL;
            case "storm_hub" -> StormWhispers.HUB;
            case "storm_top" -> StormWhispers.TOP;
            case "storm_west" -> StormWhispers.WEST;
            case "storm_east" -> StormWhispers.EAST;
            case "storm_front" -> StormWhispers.FRONT;
            default -> StormWhispers.HEART;
        };
        return new BlockPos((b.x0() + b.x1()) / 2, (b.y0() + b.y1()) / 2, (b.z0() + b.z1()) / 2);
    }

    private static void forgetWhispers(Player p) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag heard = persisted.getCompound("skyabyss_back_heard");
        for (StormWhispers.Whisper w : StormWhispers.WHISPERS) {
            heard.remove(w.id());
        }
        heard.remove(StormWhispers.CAMPFIRE_FLAG);
    }

    // ---------------------------------------------------------------- クエスト
    private static void quests(MinecraftServer server, ServerLevel level, List<String> f, List<String> notes) {
        QuestManager.loadErrors().forEach(e -> f.add("quest load: " + e));
        for (String id : StormLore.QUESTS) {
            QuestDef def = QuestManager.quest(id);
            check(def != null && def.board() && def.repeats() && def.minStage() == 3, id + " should be a stage-3 board quest", f);
        }
        // メインの合図の名前が、StormLore の約束と同じ
        QuestDef main = QuestManager.quest("main_thunder");
        if (main == null) {
            f.add("missing main_thunder");
            return;
        }
        List<String> signals = main.stages().stream().flatMap(s -> s.objectives().stream()).filter(o -> o.type().equals("signal"))
                .flatMap(o -> o.targets().stream()).toList();
        check(signals.equals(StormLore.TRIALS.stream().map(StormLore::trialSignal).toList()), "main_thunder listens for " + signals, f);

        // 一人目: 本体なしで、伝承と合図だけで通す
        FakePlayer p = player(level, "q");
        reset(server, p);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.STONE_WHALE);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.TIDE_SHELL);
        Quests.start(p, "main_thunder");
        check(stage(p, "main_thunder").equals("rumor"), "main_thunder should start at the rumor", f);
        Lore.unlock(p, StormLore.LORE_SIGHT, false);   // 遠くの嵐を見た (噂の段は飛ぶ)
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("board"), "seeing the storm should skip the rumor and lead to boarding: " + stage(p, "main_thunder"), f);
        Lore.unlock(p, StormLore.LORE_RUINS, false);   // 背に入った
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("trials"), "boarding did not lead to the trials: " + stage(p, "main_thunder"), f);
        Lore.unlock(p, StormLore.inscriptionLore("tail"), false);
        Quests.signal(p, StormLore.trialSignal("east"));
        Quests.signal(p, "storm_trial_nowhere");
        Quests.poll(p, true);
        QuestLog.Entry e = QuestLog.of(p).entry("main_thunder");
        check(stage(p, "main_thunder").equals("trials") && e.progress(0) == 1 && e.progress(1) == 1,
                "one inscription and one stone should leave the trials at 1/2 and 1/1", f);
        Lore.unlock(p, StormLore.inscriptionLore("hub"), false);
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("heart"), "two inscriptions and a stone did not lead to the heart: " + stage(p, "main_thunder"), f);
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("heart"), "the heart stage passed before the heart stopped", f);
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.THUNDER_WING);
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("report"), "stilling the heart did not lead to the report: " + stage(p, "main_thunder"), f);
        Quests.onTalk(p, jp.revati.skyabyss.npc.NpcSetup.WHALE_CHASER_ID, "ms_c_thunder_back");
        Quests.poll(p, true);
        check(stage(p, "main_thunder").equals("done"), "the report to Isana did not finish main_thunder: " + stage(p, "main_thunder"), f);

        // 二人目: 仲間と心臓を先に止めた (背・試練の段を飛ばす)
        FakePlayer b = player(level, "b");
        reset(server, b);
        Progression.onTitanStopped(server, List.of(b.getUUID()), Titans.STONE_WHALE);
        Progression.onTitanStopped(server, List.of(b.getUUID()), Titans.TIDE_SHELL);
        Quests.start(b, "main_thunder");
        Lore.unlock(b, StormLore.LORE_TITAN, false);
        Progression.onTitanStopped(server, List.of(b.getUUID()), Titans.THUNDER_WING);
        Quests.poll(b, true);
        check(stage(b, "main_thunder").equals("report"), "a player who stilled the heart first should skip to the report: " + stage(b, "main_thunder"), f);

        // 雷翼の背の周回: 段階 3 の人。倒した数で終わり、報酬が出る (雷翼の欠片は品ができてから)
        Quests.start(p, "daily_storm_rounds");
        for (int i = 0; i < 8; i++) {
            Quests.onKill(p, StormLore.BACK_MOBS.get(i % 3), true, 2);
        }
        Quests.poll(p, true);
        check(stage(p, "daily_storm_rounds").equals("done"), "eight back kills did not finish the rounds", f);
        check(p.getInventory().countItem(item("skyabyss:resonance_shard")) >= 1, "the rounds paid no resonance shard", f);
        Quests.start(p, "weekly_storm_heart");
        Quests.onKill(p, "skyabyss:tide_heart", true, 2);
        Quests.poll(p, true);
        check(stage(p, "weekly_storm_heart").equals("heart"), "a tide heart counted for the storm heart", f);
        Quests.onKill(p, StormLore.HEART, true, 2);
        Quests.poll(p, true);
        check(stage(p, "weekly_storm_heart").equals("done"), "stilling the heart did not finish the weekly", f);
        check(p.getInventory().countItem(item("skyabyss:resonance_crystal")) >= 1, "the weekly paid no resonance crystal", f);
        boolean shard = BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(StormLore.SHARD));
        boolean cock = BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(StormLore.WEATHERCOCK));
        if (shard) {
            check(p.getInventory().countItem(item(StormLore.SHARD)) >= 5, "the board quests paid no thunder wing shards", f);
        }
        // 古い地図の報酬に風見鶏 (品ができていれば渡る)
        QuestDef map = QuestManager.quest("sub_old_map");
        check(map != null && map.reward().items().stream().anyMatch(r -> r.item().equals(StormLore.WEATHERCOCK)),
                "the old map does not hand over the weathercock", f);
        notes.add("quests: main_thunder end to end from lore and signals only (and the skip after a shared heart kill), "
                + "rounds and weekly heart paid; shard " + (shard ? "paid" : "waits for its item") + ", weathercock " + (cock ? "registered" : "waits for its item"));
        Quests.forgetAll(p);
        Quests.forgetAll(b);
    }

    private static void reset(MinecraftServer server, FakePlayer p) {
        Lore.reset(p);
        Quests.forgetAll(p);
        p.getInventory().clearContent();
        jp.revati.skyabyss.progress.ProgressRules.TITAN_ORDER.forEach(t -> ProgressData.get(server).clearPersonal(p.getUUID(), t));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static FakePlayer player(ServerLevel level, String tag) {
        ServerLevel at = level != null ? level : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
        UUID id = UUID.nameUUIDFromBytes(("skyabyss storm lore " + tag).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(at, new GameProfile(id, "[storm_" + tag + "]"));
    }

    private static String stage(Player p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null) {
            return "none";
        }
        return e.done ? "done" : def.stages().get(e.stage).id();
    }

    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = StormLoreSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("could not read " + path + ": " + e);
            return null;
        }
    }
}
