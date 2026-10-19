package jp.revati.skyabyss.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreSetup;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

// 初回の手引き (TutorialHints) の自己テスト: 品を持つと始まる・済むまで出続ける・人ごと・済んだら二度と出ない・出来事で始まる・
// 保存 (コーデックの往復 = 再ログインで残る)・優先の並び・日英の文の鍵がそろう
public final class TutorialSelfTest {
    private TutorialSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("tutorial selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tutorial PASSED (starts on item/event, stays until done, per player, never restarts, persists, order, ja/en keys)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST tutorial FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(MinecraftServer server, String name) {
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        return p;
    }

    private static TutorialData roundTrip(TutorialData d) {
        DataResult<Tag> enc = TutorialData.CODEC.encodeStart(NbtOps.INSTANCE, d);
        return TutorialData.CODEC.parse(NbtOps.INSTANCE, enc.result().orElseThrow()).result().orElseThrow();
    }

    private static void run(MinecraftServer server, List<String> f) {
        FakePlayer a = player(server, "[tut_a]");
        FakePlayer b = player(server, "[tut_b]");
        check(f, TutorialHints.active(a).isEmpty(), "a new player should have no hints");
        check(f, !TutorialHints.scan(a), "an empty inventory should start nothing");

        // 伝承の書を持つと始まり、済むまで消えない (見回しを何度しても・再び持っても)
        a.getInventory().add(new ItemStack(LoreSetup.LORE_BOOK.get()));
        check(f, TutorialHints.scan(a), "holding the lore book should start its hint");
        check(f, TutorialHints.active(a).equals(List.of(TutorialHints.LORE_BOOK)), "only the book hint should be active: " + TutorialHints.active(a));
        check(f, !TutorialHints.scan(a), "scanning again must not restart");
        for (int i = 0; i < 5; i++) {
            TutorialHints.scan(a);
        }
        check(f, TutorialHints.active(a).contains(TutorialHints.LORE_BOOK), "the book hint must stay until done");
        check(f, TutorialHints.active(b).isEmpty() && !TutorialData.of(b).isActive(TutorialHints.LORE_BOOK), "hints must be per player");

        // 保存 (再ログイン): 出ている・済んだの両方が残る
        TutorialData copy = roundTrip(TutorialData.of(a));
        check(f, copy.isActive(TutorialHints.LORE_BOOK) && !copy.isDone(TutorialHints.LORE_BOOK), "an active hint must survive a save");

        // 破れた頁・笛・旅嚢の品
        a.getInventory().add(new ItemStack(LoreSetup.LORE_PAGE.get()));
        a.getInventory().add(new ItemStack(jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get()));
        TutorialHints.scan(a);
        check(f, TutorialHints.active(a).equals(List.of(TutorialHints.LORE_BOOK, TutorialHints.LORE_PAGE, TutorialHints.WHISTLE)),
                "book, page and whistle should be active in priority order: " + TutorialHints.active(a));
        Pouch.add(a, new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 3));
        TutorialHints.scan(a);
        check(f, TutorialHints.active(a).get(0).equals(TutorialHints.POUCH), "the pouch hint should come first once the pouch holds something: " + TutorialHints.active(a));

        // した → 済み。二度と出ない (品を持ったままでも)
        check(f, TutorialHints.done(a, TutorialHints.LORE_BOOK), "done should report that an active hint finished");
        check(f, !TutorialHints.active(a).contains(TutorialHints.LORE_BOOK) && TutorialData.of(a).isDone(TutorialHints.LORE_BOOK), "a finished hint should leave the active list");
        TutorialHints.scan(a);
        check(f, !TutorialHints.active(a).contains(TutorialHints.LORE_BOOK), "a finished hint must never restart");
        check(f, !TutorialHints.done(a, TutorialHints.LORE_BOOK), "finishing twice should report false");
        TutorialData after = roundTrip(TutorialData.of(a));
        check(f, after.isDone(TutorialHints.LORE_BOOK) && !after.isActive(TutorialHints.LORE_BOOK) && after.isActive(TutorialHints.LORE_PAGE), "done/active must both persist");
        check(f, TutorialHints.active(b).isEmpty(), "the other player must be unaffected");

        // 先にした人 (品を得る前に操作を済ませた) には出ない
        check(f, !TutorialHints.done(b, TutorialHints.WHISTLE), "finishing a hint that was not active should report false");
        b.getInventory().add(new ItemStack(jp.revati.skyabyss.titan.travel.TravelSetup.STONE_WHALE_CALL.get()));
        TutorialHints.scan(b);
        check(f, !TutorialHints.active(b).contains(TutorialHints.WHISTLE), "a hint done before the item arrived must not show");

        // 出来事で始まる (控え帳・仲間の印)
        check(f, TutorialHints.start(a, TutorialHints.RECORD), "an event should start the record hint");
        check(f, !TutorialHints.start(a, TutorialHints.RECORD), "starting twice should report false");
        check(f, TutorialHints.start(a, TutorialHints.PING), "party join should start the ping hint");
        TutorialHints.done(a, TutorialHints.RECORD);
        check(f, !TutorialHints.start(a, TutorialHints.RECORD), "a finished event hint must not restart");
        check(f, !TutorialHints.start(a, "no_such_hint"), "an unknown id should be refused");

        // 文の鍵が日英にそろっている
        langKeys(f);
    }

    private static void langKeys(List<String> f) {
        JsonObject ja = load("ja_jp");
        JsonObject en = load("en_us");
        List<String> keys = new ArrayList<>();
        keys.add("tutorial.skyabyss.done");
        for (TutorialHints.Hint h : TutorialHints.ALL) {
            String base = "tutorial.skyabyss." + h.id();
            if (h.item() != null) {
                for (String v : List.of("hand", "short", "wheel")) {
                    keys.add(base + "." + v);
                }
            } else {
                keys.add(base);
            }
        }
        for (String k : keys) {
            check(f, ja != null && ja.has(k), "ja_jp is missing " + k);
            check(f, en != null && en.has(k), "en_us is missing " + k);
            if (ja != null && en != null && ja.has(k) && en.has(k) && !k.endsWith(".done") && !k.endsWith("hand")) {
                check(f, ja.get(k).getAsString().contains("%s") && en.get(k).getAsString().contains("%s"), k + " should carry the key name placeholder");
            }
        }
    }

    private static JsonObject load(String lang) {
        try (var in = TutorialSelfTest.class.getResourceAsStream("/assets/skyabyss_tutorial/lang/" + lang + ".json")) {
            return in == null ? null : JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
