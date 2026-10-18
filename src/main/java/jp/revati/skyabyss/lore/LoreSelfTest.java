package jp.revati.skyabyss.lore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 伝承の書の自己テスト (SKYABYSS_SELFTEST=1 の専用サーバー)。
// JSON の読み込み・文の有無・解放の条件・記録の保存と引き継ぎ・頁・村の宝箱を確かめ、ログに PASSED / FAILED を出す。
// サーバーを止めるのは岩鯨の背の自己テスト (TitanBackSelfTest) の役目なので、ここは起動直後に一度で済ませる
public final class LoreSelfTest {
    private static final String[] LANGS = {"ja_jp", "en_us"};

    private LoreSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            run(server, failures);
        } catch (RuntimeException e) {
            failures.add("exception: " + e);
            SkyAbyss.LOGGER.error("SELFTEST lore exception", e);
        }
        if (failures.isEmpty()) {
            LoreCatalog c = LoreManager.catalog();
            SkyAbyss.LOGGER.info("SELFTEST lore PASSED ({} chapters, {} entries, {} unlockable)", c.chapters().size(), c.entries().size(),
                    c.entries().stream().filter(LoreEntry::unlockable).count());
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST lore FAILED: {}", f));
        }
    }

    private static void run(MinecraftServer server, List<String> failures) {
        LoreCatalog catalog = LoreManager.catalog();
        // ---- 読み込み
        LoreManager.loadErrors().forEach(e -> failures.add("load: " + e));
        long unlockable = catalog.entries().stream().filter(LoreEntry::unlockable).count();
        long early = catalog.entries().stream().filter(e -> e.unlockable() && (e.chapter().equals("prologue") || e.chapter().startsWith("titan_"))).count();
        // 12〜20 に、自由なモードのサブクエストが記す 2 項目 (07 の 15 章) と、その先の余裕を足した
        // 一〜四章 (旧 1 章) は巨神ごとに碑文と項目が増える (岩鯨・潮殻で 30 前後、雷翼の 11 を足して 50 前後。余燼の分も見込む)
        if (early < 12 || early > 110) {
            failures.add("prologue + chapters 1-4 should have 12-110 entries, found " + early);
        }
        if (unlockable != early) {
            failures.add("only the prologue and chapters 1-4 may be written yet (" + unlockable + " unlockable)");
        }
        for (String later : List.of("chapter2", "chapter3", "finale")) {
            List<LoreEntry> list = catalog.inChapter(later);
            if (list.isEmpty() || list.stream().anyMatch(e -> !e.placeholder())) {
                failures.add("chapter " + later + " should hold placeholders only (" + list.size() + ")");
            }
        }
        if (catalog.entry(Lore.BOOK_ENTRY) == null) {
            failures.add("the book's own entry " + Lore.BOOK_ENTRY + " is missing");
        }
        // ---- 条件の先が実在するか
        Set<String> signKeys = new HashSet<>();
        TitanBackLayout.LORE_SIGNS.forEach(s -> signKeys.add(s.key()));
        // 潮殻の背の碑文 (08 の 9.2節)
        jp.revati.skyabyss.titan.tide.back.TideBackLayout.LORE_SIGNS.forEach(s -> signKeys.add(s.key()));
        // 雷翼の背の碑文 (12 の 9.2節。キーは伝承の担当が決め、背中の担当がこのキーで看板を立てる)
        signKeys.addAll(jp.revati.skyabyss.titan.storm.lore.StormLore.INSCRIPTIONS.values());
        // 余燼の背の碑文 (13 の 9.2節。文は担当 G、看板は B が置く)
        signKeys.addAll(jp.revati.skyabyss.titan.ember.lore.EmberLore.inscriptionSigns());
        // 雷翼の巨神・雷獣・心臓・背中は別の担当が登録する。まだないうちは失敗にせず、待っているものとして書き出す
        Set<String> pending = new java.util.TreeSet<>();
        Set<String> usedSigns = new HashSet<>();
        for (LoreEntry entry : catalog.entries()) {
            for (LoreTrigger t : entry.unlock()) {
                switch (t.type()) {
                    case "sign" -> {
                        usedSigns.add(t.target());
                        if (!signKeys.contains(t.target())) {
                            failures.add(entry.id() + ": no inscription is placed with key " + t.target());
                        }
                    }
                    case "item" -> {
                        if (LoreEvents.item(t.target()) == null) {
                            failures.add(entry.id() + ": unknown item " + t.target());
                        }
                    }
                    case "kill", "near_entity" -> {
                        if (LoreEvents.entityType(t.target()) == null && (jp.revati.skyabyss.titan.storm.lore.StormLore.pending(t.target()) || jp.revati.skyabyss.titan.ember.lore.EmberLore.pending(t.target()))) {
                            pending.add(t.target());
                        } else if (LoreEvents.entityType(t.target()) == null) {
                            failures.add(entry.id() + ": unknown entity " + t.target());
                        }
                        if (t.radius() <= 0) {
                            failures.add(entry.id() + ": " + t.type() + " needs a radius");
                        }
                    }
                    case "dimension" -> {
                        ResourceLocation dim = ResourceLocation.tryParse(t.target());
                        boolean missing = dim == null || server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim)) == null;
                        if (missing && jp.revati.skyabyss.titan.storm.lore.StormLore.pending(t.target())) {
                            pending.add(t.target());
                        } else if (missing) {
                            failures.add(entry.id() + ": unknown dimension " + t.target());
                        }
                    }
                    case "page" -> {
                        if (!t.target().equals("village") && !t.target().equals("titan_back") && !t.target().equals("shadow")
                                && !t.target().equals(jp.revati.skyabyss.story.ch1.Ch1.PAGE_TRACE) && !t.target().equals(jp.revati.skyabyss.story.ch1.Ch1.PAGE_DIARY)
                                && !t.target().equals(jp.revati.skyabyss.titan.storm.lore.StormLore.PAGE_SOURCE) && !t.target().equals(jp.revati.skyabyss.titan.ember.lore.EmberLore.PAGE_SOURCE)) {
                            failures.add(entry.id() + ": unknown page source " + t.target());
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        if (!pending.isEmpty()) {
            SkyAbyss.LOGGER.warn("SELFTEST lore: waiting for other parts to register {} (their lore triggers stay idle until then)", pending);
        }
        if (!usedSigns.containsAll(signKeys)) {
            Set<String> missing = new HashSet<>(signKeys);
            missing.removeAll(usedSigns);
            failures.add("inscriptions without a lore entry: " + missing);
        }
        // ---- 文 (ja_jp と en_us の両方にあるか)
        for (String lang : LANGS) {
            JsonObject json = lang(lang, failures);
            if (json == null) {
                continue;
            }
            List<String> keys = new ArrayList<>(List.of("item.skyabyss.lore_book", "item.skyabyss.lore_book.desc", "item.skyabyss.lore_page",
                    "item.skyabyss.lore_page.desc", "item.skyabyss.lore_page.desc.village", "item.skyabyss.lore_page.desc.titan_back",
                    "item.skyabyss.lore_page.desc.storm_back", "item.skyabyss.lore_page.desc.ember_back",
                    "gui.skyabyss.lore.toast", "gui.skyabyss.lore.related", "gui.skyabyss.lore.count", "gui.skyabyss.lore.empty",
                    "message.skyabyss.lore.book_falls", "message.skyabyss.lore.book_appeared", "message.skyabyss.lore.book_returned",
                    "message.skyabyss.lore.page_read", "message.skyabyss.lore.page_known",
                    "commands.skyabyss.lore.list", "commands.skyabyss.lore.unknown", "commands.skyabyss.lore.unlocked",
                    "commands.skyabyss.lore.locked", "commands.skyabyss.lore.reset", "commands.skyabyss.lore.book",
                    "commands.skyabyss.lore.page", "commands.skyabyss.lore.drop"));
            for (LoreChapter chapter : catalog.chapters()) {
                keys.add(chapter.tabKey());
                keys.add(chapter.nameKey());
            }
            for (LoreEntry entry : catalog.entries()) {
                keys.add(entry.hintKey());
                if (entry.placeholder()) {
                    continue;
                }
                keys.add(entry.titleKey());
                keys.add(entry.bodyKey());
                if (entry.inscription()) {
                    keys.add(entry.inscriptionKey());
                }
            }
            for (String key : keys) {
                if (!json.has(key) || json.get(key).getAsString().isBlank()) {
                    failures.add(lang + ": missing text " + key);
                }
            }
        }
        // 碑文の写しに使う看板の文が、本体の lang (ja_jp / en_us) にあるか
        for (String lang : LANGS) {
            JsonObject main = json("/assets/skyabyss/lang/" + lang + ".json", failures);
            if (main == null) {
                continue;
            }
            // 潮殻の背の看板の文は skyabyss_tide にある
            // 雷翼の背の看板の文は skyabyss_storm にある
            for (String ns : List.of("skyabyss_tide", "skyabyss_storm", "skyabyss_ember")) {
                JsonObject backSigns = json("/assets/" + ns + "/lang/" + lang + ".json", failures);
                if (backSigns != null) {
                    backSigns.entrySet().forEach(e -> {
                        if (!main.has(e.getKey())) {
                            main.add(e.getKey(), e.getValue());
                        }
                    });
                }
            }
            for (LoreEntry entry : catalog.entries()) {
                if (!entry.inscriptionSign().isEmpty()) {
                    for (String key : entry.inscriptionKeys()) {
                        if (!main.has(key)) {
                            failures.add(lang + ": " + entry.id() + " copies a sign line that does not exist: " + key);
                        }
                    }
                }
            }
        }
        // ---- 参加者へ送る形: 書いて読み戻すと同じ一覧になる
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        catalog.write(buf);
        LoreCatalog back = LoreCatalog.read(buf);
        if (!back.entries().equals(catalog.entries()) || !back.chapters().equals(catalog.chapters())) {
            failures.add("the catalog does not survive the network codec");
        }
        // ---- 折り返し: 行頭に句読点や閉じかっこが来ない、幅を超えない
        java.util.function.ToIntFunction<String> measure = str -> {
            int w = 0;
            for (char ch : str.toCharArray()) {
                w += ch >= 0x2000 ? 9 : 6;
            }
            return w;
        };
        String sample = "大人に尋ねると、話をそらされた。「留まる者」という言葉の脇に、爪で引っかいたような細い傷が並んでいる。";
        for (int width = 60; width <= 130; width += 7) {
            List<String> wrapped = LoreWrap.wrap(sample, width, measure);
            if (!String.join("", wrapped).equals(sample)) {
                failures.add("wrapping lost or changed text at width " + width);
            }
            for (int i = 0; i < wrapped.size(); i++) {
                String line = wrapped.get(i);
                if (measure.applyAsInt(line) > width) {
                    failures.add("wrapped line too wide at width " + width + ": " + line);
                }
                if (i > 0 && !line.isEmpty() && "、。」』）".indexOf(line.charAt(0)) >= 0) {
                    failures.add("a line starts with punctuation at width " + width + ": " + line);
                }
                if (!line.isEmpty() && "「『（".indexOf(line.charAt(line.length() - 1)) >= 0 && i < wrapped.size() - 1) {
                    failures.add("a line ends with an opening bracket at width " + width + ": " + line);
                }
            }
        }
        List<String> english = LoreWrap.wrap("The chain hauled me up into a town. Or what had been one.", 80, measure);
        for (String line : english) {
            if (line.startsWith(" ") || line.endsWith(" ")) {
                failures.add("english wrap left spaces: [" + line + "]");
            }
        }
        if (!String.join(" ", english).equals("The chain hauled me up into a town. Or what had been one.")) {
            failures.add("english wrap split a word: " + english);
        }
        // ---- 記録: 解放・保存・死んだときの引き継ぎ
        ServerLevel overworld = server.overworld();
        FakePlayer a = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000a001"), "[lore_test_a]"));
        FakePlayer b = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000a002"), "[lore_test_b]"));
        Lore.reset(a);
        Lore.reset(b);
        a.getInventory().clearContent();
        b.getInventory().clearContent();
        // 碑文を読む → 記される。書をまだ持っていないので、荷物の中に現れ、書の項目も記される
        int n = Lore.fire(a, "sign", t -> t.target().equals("sign.skyabyss.lore_heart"));
        if (n != 1 || !Lore.state(a).has("chapter1/inscription_heart")) {
            failures.add("reading the heart inscription did not write chapter1/inscription_heart (" + n + ")");
        }
        if (!Lore.state(a).bookGiven() || !Lore.contains(a.getInventory().items, LoreSetup.LORE_BOOK.get()) || !Lore.state(a).has(Lore.BOOK_ENTRY)) {
            failures.add("the first entry did not hand over the lore book");
        }
        if (Lore.fire(a, "sign", t -> t.target().equals("sign.skyabyss.lore_heart")) != 0) {
            failures.add("an entry was written twice");
        }
        // ほかの人の書には何も起きない
        if (!Lore.state(b).unlocked().isEmpty()) {
            failures.add("another player's record changed");
        }
        // クリエイティブなどで先に書を持っていた人には、二冊目を渡さない
        FakePlayer c = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000a003"), "[lore_test_c]"));
        Lore.reset(c);
        c.getInventory().clearContent();
        c.getInventory().add(new ItemStack(LoreSetup.LORE_BOOK.get()));
        Lore.fire(c, "sign", t -> t.target().equals("sign.skyabyss.lore_east"));
        if (c.getInventory().countItem(LoreSetup.LORE_BOOK.get()) != 1 || !Lore.state(c).bookGiven()) {
            failures.add("a player who already held the book got another one");
        }
        Lore.reset(c);
        c.getInventory().clearContent();
        // 書をなくしたら、次に何かが記されたとき戻ってくる
        a.getInventory().clearContent();
        Lore.fire(a, "sign", t -> t.target().equals("sign.skyabyss.lore_west"));
        if (!Lore.contains(a.getInventory().items, LoreSetup.LORE_BOOK.get())) {
            failures.add("a lost book did not return with the next entry");
        }
        // 空の影を見た人は、書が落ちてくるのを待つ (その前に荷物の中へは入らない)
        LoreState sb = Lore.state(b);
        sb.bookDropAt = overworld.getGameTime() + 100000;
        Lore.fire(b, "sky_shadow", t -> true);
        if (!sb.has("prologue/shadow") || sb.bookGiven() || Lore.contains(b.getInventory().items, LoreSetup.LORE_BOOK.get())) {
            failures.add("the sky shadow did not wait for the falling book: " + sb);
        }
        // 天の鍵の欠片を持つと、欠片と心臓の項目が記される
        b.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
        LoreEvents.checkItems(b, sb);
        if (!sb.has("chapter1/sky_key") || !sb.has("chapter1/heart_stilled")) {
            failures.add("holding a sky key fragment did not write its entries: " + sb.unlocked());
        }
        // 心臓が倒れたとき、近くにいた人に記される (kill の条件。倒した人なら距離を問わない)
        Lore.lock(b, "chapter1/heart_stilled");
        Lore.fire(b, "kill", t -> t.target().equals(BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.STONE_WHALE_HEART.get()).toString()));
        if (!sb.has("chapter1/heart_stilled")) {
            failures.add("the heart kill trigger did not write chapter1/heart_stilled");
        }
        // 頁: 村の頁は村の項目だけ、岩鯨の背の頁は背の項目だけ
        for (String source : List.of("village", "titan_back")) {
            List<LoreEntry> choices = LorePageItem.candidates(b, source);
            if (choices.isEmpty()) {
                failures.add("no page entries for source " + source);
            }
            for (LoreEntry e : choices) {
                if (e.unlock().stream().noneMatch(t -> t.is("page", source))) {
                    failures.add("page source " + source + " offered " + e.id());
                }
            }
        }
        // 読んだ印は、記された項目にだけ付く
        Lore.markSeen(b, "chapter2/unknown_1");
        Lore.markSeen(b, "prologue/shadow");
        if (sb.seen().contains("chapter2/unknown_1") || !sb.seen().contains("prologue/shadow")) {
            failures.add("seen marks are wrong: " + sb.seen());
        }
        // 枠だけの項目は、コマンドでも記されない
        if (Lore.unlock(b, "chapter2/unknown_1", false)) {
            failures.add("a placeholder entry was unlocked");
        }
        // 保存: プレイヤーのデータに書かれ、読み戻すと同じ記録になる
        CompoundTag attachments = a.serializeAttachments(server.registryAccess());
        Tag saved = attachments.get(SkyAbyss.MODID + ":lore_state");
        if (saved == null) {
            failures.add("the lore record is not written with the player data: " + attachments.getAllKeys());
        } else {
            LoreState loaded = LoreState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            if (!loaded.equals(Lore.state(a))) {
                failures.add("the saved record does not load back the same: " + loaded + " vs " + Lore.state(a));
            }
        }
        Codec<LoreState> codec = LoreState.CODEC;
        LoreState empty = codec.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        if (!empty.unlocked().isEmpty() || empty.bookDropAt() != -1) {
            failures.add("an empty record does not load as empty");
        }
        // 死んだとき: 新しい体へ引き継ぐ (copyOnDeath)
        Lore.reset(b);
        b.copyAttachmentsFrom(a, true);
        if (!Lore.state(b).equals(Lore.state(a))) {
            failures.add("the record was not carried over on death");
        }
        // 後片付け (テスト用の記録は残さない)
        Lore.reset(a);
        Lore.reset(b);
        a.getInventory().clearContent();
        b.getInventory().clearContent();
        // ---- 村の宝箱に頁が入ること (25% を 400 回引いて、少なくとも一枚)
        LootTable table = server.reloadableRegistries().getLootTable(BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
        int pages = 0;
        for (int i = 0; i < 400; i++) {
            LootParams params = new LootParams.Builder(overworld).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(LoreSetup.LORE_PAGE.get())) {
                    pages++;
                    if (!"village".equals(LorePageItem.source(stack))) {
                        failures.add("a village chest page has source " + LorePageItem.source(stack));
                    }
                }
            }
        }
        if (pages < 40 || pages > 180) {
            failures.add("village chests gave " + pages + " pages in 400 rolls (expected about 100)");
        }
    }

    // 伝承の文は skyabyss_lore に。サブクエストが記す項目 (柱の刻み目・干潮の刻) の文は skyabyss_quest にある
    private static JsonObject lang(String code, List<String> failures) {
        JsonObject lang = json("/assets/skyabyss_lore/lang/" + code + ".json", failures);
        JsonObject quest = json("/assets/skyabyss_quest/lang/" + code + ".json", failures);
        // 潮殻の心臓が記す項目 (潮の止まる刻) の文は skyabyss_tide にある
        JsonObject tide = json("/assets/skyabyss_tide/lang/" + code + ".json", failures);
        // 1 章の続き (雷翼・余燼・天の鍵・数え歌のいつつ・嵐の通り道) の文は skyabyss_story にある
        JsonObject story = json("/assets/skyabyss_story/lang/" + code + ".json", failures);
        // 雷翼の背の項目 (風に逆らう嵐・碑文・雷獣・晴れた空・風見の帳ほか) の文は skyabyss_storm にある (12 の 9 章)
        JsonObject storm = json("/assets/skyabyss_storm/lang/" + code + ".json", failures);
        // 余燼の項目 (地を泳ぐもの・灰の民の炉・碑文・鍛え手・消えた火・鍛えの帳) の文は skyabyss_ember にある (13 の 9 章、担当 G)
        JsonObject ember = json("/assets/skyabyss_ember/lang/" + code + ".json", failures);
        // 序章・一章の旅の跡・歌石などの項目 (17 の 1.5) の文は skyabyss_ch1 にある
        JsonObject ch1 = json("/assets/skyabyss_ch1/lang/" + code + ".json", failures);
        for (JsonObject extra : java.util.Arrays.asList(quest, tide, story, storm, ember, ch1)) {
            if (lang != null && extra != null) {
                extra.entrySet().forEach(e -> {
                    if (!lang.has(e.getKey())) {
                        lang.add(e.getKey(), e.getValue());
                    }
                });
            }
        }
        return lang;
    }

    private static JsonObject json(String path, List<String> failures) {
        try (InputStream in = LoreSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                failures.add("missing " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            failures.add("could not read " + path + ": " + e);
            return null;
        }
    }
}
