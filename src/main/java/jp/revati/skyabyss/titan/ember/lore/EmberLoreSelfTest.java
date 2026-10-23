package jp.revati.skyabyss.titan.ember.lore;

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
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.StorySetup;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の伝承と締めの自己テスト (SELFTEST ember lore。13 の 11.2節の担当 G の確かめ方)。
//  - 伝承: 余燼の項目があり、文が日英にある。碑文の項目は看板の 4 行を写す。破れた頁 (ember_back) から鍛えの帳が出る。言いすぎない
//  - 看板: 碑文 3 枚 (レイドの部屋の入口の碑) と入口の一言 3 枚がはみ出さない (SignFit、1 行 90 px)
//  - 語りの文 (ほかの担当が使う鍵): 日英にある、%s の数がそろう
//  - 品の名前と説明: 日英にある
//  - 天の鍵との継ぎ目: 余燼の燠石が四つ目として story の SkyKey につながる (四体を止めて四つで鍵、三つでは組まない、止めていなければ組まない)。
//    「第一章 了」は SkyKey の終わりの札 (一章　天の鍵 / ベータ版はここまでです) で出す
//  - 夜明けの地鳴り (EmberFinale): 余燼を止めた人だけ、地上の夜明けに一度
// 記録は写して試し、最後に戻す
public final class EmberLoreSelfTest {
    private static final String[] LANGS = {"ja_jp", "en_us"};
    static final List<String> ENTRIES = List.of(EmberLore.LORE_TITAN, EmberLore.LORE_RUINS, EmberLore.LORE_LEDGER, EmberLore.LORE_SMITH,
            EmberLore.LORE_STILLED, EmberLore.inscriptionLore("lore_ember_west"), EmberLore.inscriptionLore("lore_ember_east"),
            EmberLore.inscriptionLore("lore_ember_south"));
    static final List<String> ITEMS = List.of("ember_helmet", "ember_chestplate", "ember_leggings", "ember_boots", "ember_war_axe", "ember_mantle",
            "kindling_charm", "smith_hammer", "thrall_poker", "ember_lantern", "ember_horn", "ember_shard", "ember_core", "spark_dust", "cinder_husk");

    private EmberLoreSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ProgressData progress = ProgressData.get(server);
        CompoundTag saved = progress.save(new CompoundTag(), server.registryAccess());
        try {
            List<JsonObject> langs = langs(f);
            lore(langs, f, notes);
            signs(f, notes);
            lines(langs, f, notes);
            skyKey(server, f, notes);
            finale(server, f, notes);
        } catch (RuntimeException e) {
            f.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST ember lore exception", e);
        } finally {
            progress.restore(saved, server.registryAccess());
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST ember lore: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember lore PASSED ({} lore entries, {} signs, {} lines, sky key link, dawn)",
                    ENTRIES.size(), EmberIds.INSCRIPTIONS.size() + EmberLore.TRIAL_SIGNS.size(), EmberLore.LINES.size());
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember lore FAILED: {}", x));
        }
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) {
            f.add(what);
        }
    }

    private static List<JsonObject> langs(List<String> f) {
        List<JsonObject> out = new ArrayList<>();
        for (String code : LANGS) {
            JsonObject ember = json("/assets/skyabyss_ember/lang/" + code + ".json", f);
            if (ember != null) {
                out.add(ember);
            }
        }
        return out;
    }

    private static boolean has(JsonObject lang, String key) {
        return lang.has(key) && !lang.get(key).getAsString().isBlank();
    }

    // ---------------------------------------------------------------- 伝承
    private static void lore(List<JsonObject> langs, List<String> f, List<String> notes) {
        for (String id : ENTRIES) {
            LoreEntry e = LoreManager.catalog().entry(id);
            if (e == null) {
                f.add("missing lore " + id);
                continue;
            }
            check(e.chapter().equals("titan_ember") && e.unlockable(), id + " should be an unlockable chapter 4 (titan_ember) entry", f);
            for (JsonObject lang : langs) {
                for (String key : List.of(e.titleKey(), e.bodyKey())) {
                    check(has(lang, key), "missing text " + key, f);
                }
            }
            // 余燼の項目は「余燼の巨神」(story の項目) より後、天の鍵より前に並ぶ
            check(e.order() > 176 && e.order() < 180, id + " order " + e.order() + " should sit between chapter1/ember and the sky key", f);
        }
        for (String inscription : EmberIds.INSCRIPTIONS) {
            LoreEntry e = LoreManager.catalog().entry(EmberLore.inscriptionLore(inscription));
            String sign = EmberLore.sign(inscription);
            check(e != null && e.inscriptionSign().equals(sign) && e.unlock().stream().anyMatch(t -> t.is("sign", sign)),
                    "inscription " + inscription + " does not copy " + sign, f);
        }
        // 言いすぎない: 正体の名を出さない (13 の 9 章。答えは 2 章で)
        for (JsonObject lang : langs) {
            for (String id : ENTRIES) {
                LoreEntry e = LoreManager.catalog().entry(id);
                String body = e == null || !lang.has(e.bodyKey()) ? "" : lang.get(e.bodyKey()).getAsString();
                for (String word : List.of("見る者", "留まる者", "深淵", "watcher", "abyss")) {
                    check(!body.toLowerCase().contains(word), id + " names '" + word + "' outright", f);
                }
            }
        }
        // 天の鍵の項目は story のもの (二重に作らない)
        check(LoreManager.catalog().entry(EmberLore.LORE_SKY_KEY) != null && LoreManager.catalog().entry("chapter1/heaven_key") == null,
                "the sky key lore should be story's chapter1/sky_key_whole only", f);
        // 破れた頁 (ember_back) を読むと鍛えの帳。ほかの出どころの頁からは出ない
        FakePlayer p = player(null, "page");
        Lore.reset(p);
        check(LorePageItem.candidates(p, EmberLore.PAGE_SOURCE).stream().anyMatch(e -> e.id().equals(EmberLore.LORE_LEDGER)),
                "an ember_back page cannot give the forge ledger", f);
        check(LorePageItem.candidates(p, "titan_back").stream().noneMatch(e -> e.id().equals(EmberLore.LORE_LEDGER)),
                "a stone whale page gives the forge ledger", f);
        for (JsonObject lang : langs) {
            check(has(lang, "item.skyabyss.lore_page.desc." + EmberLore.PAGE_SOURCE), "missing page description for ember_back", f);
        }
        notes.add("lore: " + ENTRIES.size() + " entries with text in both languages, inscriptions copy the signs, the ledger comes from ember pages");
    }

    // ---------------------------------------------------------------- 看板
    private static void signs(List<String> f, List<String> notes) {
        List<String> keys = new ArrayList<>();
        EmberIds.INSCRIPTIONS.forEach(i -> keys.add(EmberLore.sign(i)));
        keys.addAll(EmberLore.TRIAL_SIGNS.values());
        f.addAll(SignFit.check(keys));
        int widest = 0;
        for (String code : SignFit.LANGUAGES) {
            var lang = SignFit.lang(code);
            for (String i : EmberIds.INSCRIPTIONS) {
                for (int n = 1; n <= 4; n++) {
                    String line = lang.getOrDefault(EmberLore.sign(i) + "." + n, "");
                    check(!line.isBlank(), code + " " + i + " line " + n + " is empty", f);
                    widest = Math.max(widest, SignFit.approxWidth(line));
                }
            }
            for (String k : EmberLore.TRIAL_SIGNS.values()) {
                check(!lang.getOrDefault(k + ".1", "").isBlank() && !lang.getOrDefault(k + ".2", "").isBlank()
                        && lang.getOrDefault(k + ".3", "").isBlank() && lang.getOrDefault(k + ".4", "").isBlank(), code + " " + k + " should use 2 lines", f);
            }
        }
        notes.add("signs: " + keys.size() + " fit the sign width (widest line " + widest + " / " + SignFit.MAX_LINE_WIDTH + " px)");
    }

    // ---------------------------------------------------------------- 語りの文・品の名前
    private static int args(String s) {
        int n = 0;
        for (int i = s.indexOf("%s"); i >= 0; i = s.indexOf("%s", i + 2)) {
            n++;
        }
        return n;
    }

    private static void lines(List<JsonObject> langs, List<String> f, List<String> notes) {
        List<String> keys = new ArrayList<>(EmberLore.LINES);
        for (String item : ITEMS) {
            keys.add("item.skyabyss." + item);
            if (!item.startsWith("ember_helmet") && !item.equals("ember_chestplate") && !item.equals("ember_leggings") && !item.equals("ember_boots")) {
                keys.add("item.skyabyss." + item + ".desc");
            }
        }
        keys.addAll(List.of("item.skyabyss.ember_armor.desc", "gear.skyabyss.origin.ember", "gear.skyabyss.origin_weapon.ember", "gui.skyabyss.forge.exchange_no_clear.ember",
                "gui.skyabyss.forge.exchange_all_owned.ember", "skyabyss_record.h.ember", "tooltip.skyabyss.ember.horn_drunk"));
        for (String key : keys) {
            for (JsonObject lang : langs) {
                check(has(lang, key), "missing text " + key, f);
            }
            if (langs.size() == 2 && has(langs.get(0), key) && has(langs.get(1), key)) {
                check(args(langs.get(0).get(key).getAsString()) == args(langs.get(1).get(key).getAsString()), key + " has different %s counts", f);
            }
        }
        notes.add("lines: " + EmberLore.LINES.size() + " narration lines and " + ITEMS.size()
                + " item names in both languages");
    }

    // ---------------------------------------------------------------- 天の鍵との継ぎ目 (14 の 4章)
    private static void skyKey(MinecraftServer server, List<String> f, List<String> notes) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "key");
        SkyKey.reset(p);
        p.getInventory().clearContent();
        // 三体を止め、欠片を三つ: 組まない
        for (String titan : ProgressRules.TITAN_ORDER.subList(0, 3)) {
            Progression.onTitanStopped(server, List.of(p.getUUID()), titan);
        }
        p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 3));
        check(!SkyKey.ready(p), "the key should not form from three fragments", f);
        // 余燼の燠石を手にしても、余燼を止めていなければ組まない (人から貰った四つ目)
        p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get(), 1));
        check(!SkyKey.ready(p), "the key should not form before the ember heart is stopped", f);
        // 余燼を止める (心臓の担当 F が報酬より先に呼ぶ): 四つ目で鍵になる
        Progression.onTitanStopped(server, List.of(p.getUUID()), Titans.EMBER);
        check(Progression.stage(p) == SkyKey.FRAGMENTS && Progression.stopped(server, p.getUUID(), EmberIds.TITAN), "stage after the ember heart", f);
        check(SkyKey.ready(p), "four fragments after the ember heart should form the key", f);
        check(SkyKey.assemble(p, false), "assemble failed", f);
        check(SkyKey.fragmentsHeld(p) == 0 && jp.revati.skyabyss.pouch.Pouch.countAll(p, StorySetup.SKY_KEY.get()) == 1 && Lore.state(p).has(EmberLore.LORE_SKY_KEY),
                "after assembling: fragments " + SkyKey.fragmentsHeld(p) + ", key " + p.getInventory().countItem(StorySetup.SKY_KEY.get()), f);
        check(!SkyKey.assemble(p, false), "the key formed twice", f);
        // 「第一章 了」は SkyKey の終わりの札で出す (13 の 15 章の判断 6 を 14 の場面に合わせた)
        for (String code : LANGS) {
            JsonObject story = json("/assets/skyabyss_story/lang/" + code + ".json", f);
            check(story != null && has(story, "scene.skyabyss.sky_key.end_title") && has(story, "scene.skyabyss.sky_key.end_sub"),
                    code + ": the sky key end card is missing", f);
        }
        SkyKey.reset(p);
        p.getInventory().clearContent();
        notes.add("sky key: three fragments do not form, a fourth before the ember heart does not form, the ember heart's fourth forms the key once");
    }

    // ---------------------------------------------------------------- 夜明けの地鳴り
    private static void finale(MinecraftServer server, List<String> f, List<String> notes) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(level, "dawn");
        EmberFinale.reset(p);
        long now = level.getGameTime();
        long longAgo = now - EmberFinale.SETTLE_TICKS - 1;
        check(EmberFinale.isDawn(23500) && EmberFinale.isDawn(500) && !EmberFinale.isDawn(6000) && !EmberFinale.isDawn(13000), "dawn window", f);
        check(!EmberFinale.due(p, now, 23500, longAgo), "dawn fired for someone who has not stopped the ember heart", f);
        for (String titan : ProgressRules.TITAN_ORDER) {
            Progression.onTitanStopped(server, List.of(p.getUUID()), titan);
        }
        check(!EmberFinale.due(p, now, 6000, longAgo), "dawn fired at noon", f);
        check(!EmberFinale.due(p, now, 23500, now), "dawn fired right after arriving on the surface", f);
        check(EmberFinale.due(p, now, 23500, longAgo), "dawn did not fire after the ember heart", f);
        EmberFinale.setDone(p, true);
        check(!EmberFinale.due(p, now, 23500, longAgo), "dawn fired twice", f);
        EmberFinale.reset(p);
        notes.add("dawn: only after the ember heart, only at dawn on the surface, after 10 seconds there, once");
    }

    private static FakePlayer player(ServerLevel level, String tag) {
        ServerLevel at = level != null ? level : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
        UUID id = UUID.nameUUIDFromBytes(("skyabyss ember lore " + tag).getBytes(StandardCharsets.UTF_8));
        FakePlayer p = new FakePlayer(at, new GameProfile(id, "[ember_" + tag + "]"));
        p.setPos(0.5, at.getMinBuildHeight() + 2, 0.5);
        return p;
    }

    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = EmberLoreSelfTest.class.getResourceAsStream(path)) {
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
