package jp.revati.skyabyss.story.arc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.notice.NoticeQueue;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 章の演出の共通部品 (P2) の自己テスト: 予兆の枠 (冪等・順番に寛容・先の章に出ない・条件・保存)、題字の札 (一度だけ・場面の間は預かる・旧セーブ)、
// 余韻の札、クエスト JSON の chapter 欄、会話の追加ファイル、空の段の口
public final class ArcSelfTest {
    private ArcSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("arc selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST arc PASSED (beats idempotent/order-tolerant/chapter-bound/conditions/persisted, title card once, aftermath, quest chapter field, extra dialogue, sky stage)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST arc FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_arc_test_" + suffix).getBytes());
        return FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_arc_" + suffix));
    }

    private static ArcData roundTrip(ArcData d) {
        DataResult<Tag> enc = ArcData.CODEC.encodeStart(NbtOps.INSTANCE, d);
        return ArcData.CODEC.parse(NbtOps.INSTANCE, enc.result().orElseThrow()).result().orElseThrow();
    }

    private static void run(MinecraftServer server, List<String> f) {
        beats(server, f);
        cards(server, f);
        notices(f);
        quests(f);
        dialogues(f);
        sky(server, f);
    }

    // ---------------------------------------------------------------- 予兆の枠
    private static void beats(MinecraftServer server, List<String> f) {
        FakePlayer p = fake(server, "beats");
        ArcData data = ArcData.of(p);
        data.reset();
        java.util.Map<String, Beat> saved = Foreshadow.snapshot();
        Foreshadow.clearAll();
        try {
            int[] fired = new int[4];
            Beat night = Beat.of(Chapters.TIDE, 1, "t_night", (pl, b) -> {
                fired[1]++;
                return true;
            }).time(Beat.Time.NIGHT).cooldown(100);
            Beat any2 = Beat.of(Chapters.TIDE, 2, "t_any", (pl, b) -> {
                fired[2]++;
                return true;
            }).cooldown(100);
            Beat any3 = Beat.of(Chapters.TIDE, 3, "t_any2", (pl, b) -> {
                fired[3]++;
                return true;
            }).cooldown(100);
            Foreshadow.register(night);
            Foreshadow.register(any2);
            Foreshadow.register(any3);
            check(f, Foreshadow.beats(Chapters.TIDE).stream().map(Beat::order).toList().equals(List.of(1, 2, 3)), "beats should list in order");
            boolean threw = false;
            try {
                Foreshadow.register(Beat.of(Chapters.TIDE, 2, "t_dup_order", (pl, b) -> true));
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(f, threw, "two beats with the same order in a chapter should be refused");
            threw = false;
            try {
                Foreshadow.register(night);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(f, threw, "registering a beat id twice should be refused");
            check(f, throwsFor(() -> Beat.of(Chapters.TIDE, 4, "x", (pl, b) -> true)) && throwsFor(() -> Beat.of(Chapters.ABYSS, 1, "x", (pl, b) -> true))
                    && throwsFor(() -> Beat.of(Chapters.PROLOGUE, 1, "x", (pl, b) -> true)) && throwsFor(() -> Beat.of(Chapters.TIDE, 1, "Bad Name", (pl, b) -> true)),
                    "orders 1-3, chapters 1-4 and plain names only");

            long day = 6000;
            long dark = 18000;
            // 順番に寛容: 昼は夜だけの 1 拍目を飛ばして 2 拍目が起きる (1 拍目に止められない)
            Beat b = Foreshadow.tryFire(p, Chapters.TIDE, 1000, day);
            check(f, b == any2 && fired[1] == 0 && fired[2] == 1, "a night-only first beat must not block the second: " + (b == null ? null : b.id()));
            // 間隔: すぐ次は起きない
            check(f, Foreshadow.tryFire(p, Chapters.TIDE, 1050, day) == null, "cooldown should hold the next beat back");
            // 間隔が過ぎれば 3 拍目 (昼のまま)
            b = Foreshadow.tryFire(p, Chapters.TIDE, 1200, day);
            check(f, b == any3 && fired[3] == 1, "third beat should fire after the cooldown while the first is still waiting");
            // 夜になれば見送られていた 1 拍目が、後からでも起きる
            check(f, Foreshadow.tryFire(p, Chapters.TIDE, 1250, dark) == null, "cooldown applies to the late first beat too");
            b = Foreshadow.tryFire(p, Chapters.TIDE, 1400, dark);
            check(f, b == night && fired[1] == 1, "the skipped first beat should still fire later at night");
            // 冪等: 三つとも済んだら何も起きない。起きた回数は 1 ずつ
            check(f, Foreshadow.tryFire(p, Chapters.TIDE, 99999, dark) == null && Foreshadow.tryFire(p, Chapters.TIDE, 99999, day) == null,
                    "fired beats must never fire again");
            check(f, fired[1] == 1 && fired[2] == 1 && fired[3] == 1, "each action should have run exactly once: " + java.util.Arrays.toString(fired));
            check(f, data.beatCount(Chapters.TIDE) == 3 && Foreshadow.done(p, night.id()), "three tide beats should be recorded");
            // 保存: 書き出して読み戻しても起きたまま
            ArcData back = roundTrip(data);
            check(f, back.beatDone(any2.id()) && back.beatCount(Chapters.TIDE) == 3 && back.lastBeatAt() == data.lastBeatAt(), "beats should survive the codec round trip");

            // 先の章・前の章の人には出ない: 別の人で、二章の拍が三章の人・一章の人に起きない
            FakePlayer q = fake(server, "beats_other");
            ArcData qd = ArcData.of(q);
            qd.reset();
            check(f, Foreshadow.tryFire(q, Chapters.STORM, 1000, day) == null && Foreshadow.tryFire(q, Chapters.WHALE, 1000, day) == null,
                    "tide beats must not fire for a player in the storm or whale chapter");
            check(f, Foreshadow.next(q, Chapters.STORM, 1000, day) == null && qd.beats().isEmpty(), "nothing should be recorded for other chapters");
            check(f, Foreshadow.tryFire(q, Chapters.TIDE, 1000, day) == any2, "...but a tide-chapter player gets them");

            // 条件: 距離 (anchor が null の間は見送り)、自由な条件、false を返す action は記録しない、例外は一度きり
            Foreshadow.clearAll();
            FakePlayer r = fake(server, "beats_cond");
            ArcData rd = ArcData.of(r);
            rd.reset();
            Vec3[] anchor = {null};
            boolean[] ok = {false};
            int[] tries = {0};
            Foreshadow.register(Beat.of(Chapters.STORM, 1, "s_far", (pl, bt) -> true).cooldown(0).distance(pl -> anchor[0], 10, 50));
            Foreshadow.register(Beat.of(Chapters.STORM, 2, "s_when", (pl, bt) -> true).cooldown(0).when(pl -> ok[0]));
            Foreshadow.register(Beat.of(Chapters.STORM, 3, "s_retry", (pl, bt) -> ++tries[0] >= 2).cooldown(0));
            Foreshadow.register(Beat.of(Chapters.EMBER, 1, "e_boom", (pl, bt) -> {
                throw new IllegalStateException("boom");
            }).cooldown(0).dimension(null));
            check(f, Foreshadow.tryFire(r, Chapters.STORM, 10, day) == null && tries[0] == 1 && !rd.beatDone("titan_storm/s_retry"),
                    "with the anchor missing and the condition false, only the retry beat is tried and an unfired action is not recorded");
            check(f, Foreshadow.tryFire(r, Chapters.STORM, 20, day) != null && rd.beatDone("titan_storm/s_retry"), "a beat that declined should be retried");
            anchor[0] = r.position().add(5, 0, 0);
            check(f, Foreshadow.next(r, Chapters.STORM, 30, day) == null, "too close to the anchor");
            anchor[0] = r.position().add(30, 0, 0);
            Beat far = Foreshadow.tryFire(r, Chapters.STORM, 30, day);
            check(f, far != null && far.name().equals("s_far"), "within the distance band the far beat should fire");
            anchor[0] = r.position().add(500, 0, 0);
            check(f, Foreshadow.next(r, Chapters.STORM, 40, day) == null, "past the max distance nothing is eligible (the when-beat is still false)");
            ok[0] = true;
            Beat when = Foreshadow.tryFire(r, Chapters.STORM, 40, day);
            check(f, when != null && when.name().equals("s_when"), "the free condition should let the beat fire once true");
            Beat boom = Foreshadow.tryFire(r, Chapters.EMBER, 50, day);
            check(f, boom != null && rd.beatDone("titan_ember/e_boom") && Foreshadow.tryFire(r, Chapters.EMBER, 60, day) == null,
                    "a throwing beat is recorded so it does not repeat");

            // 時刻の区分
            check(f, Beat.Time.NIGHT.matches(18000) && !Beat.Time.NIGHT.matches(6000) && Beat.Time.DAY.matches(6000) && Beat.Time.DAWN.matches(23500)
                    && Beat.Time.DAWN.matches(500) && Beat.Time.DUSK.matches(12000) && Beat.Time.ANY.matches(-5) && Beat.Time.NIGHT.matches(24000 + 18000),
                    "time of day bands");
            rd.reset();
            qd.reset();
            data.reset();
        } finally {
            Foreshadow.restore(saved);
        }
    }

    private static boolean throwsFor(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 題字の札・余韻の札
    private static void cards(MinecraftServer server, List<String> f) {
        FakePlayer p = fake(server, "cards");
        ArcData d = ArcData.of(p);
        d.reset();
        ChapterCards.forget(p.getUUID());
        // 一章: 見てから 5 秒たつまで出さず、出したあとは二度と出ない
        check(f, !ChapterCards.tick(p, Chapters.WHALE, false, 1000), "the title card should wait for the delay");
        check(f, !ChapterCards.tick(p, Chapters.WHALE, false, 1000 + ChapterCards.TITLE_DELAY - 1), "still waiting");
        check(f, ChapterCards.tick(p, Chapters.WHALE, false, 1000 + ChapterCards.TITLE_DELAY), "the title card should show once the delay has passed");
        check(f, d.titleShown(Chapters.WHALE) && !d.titleShown(Chapters.TIDE), "shown is recorded for that chapter only");
        for (int i = 0; i < 5; i++) {
            check(f, !ChapterCards.tick(p, Chapters.WHALE, false, 5000 + i * 200L), "the title card must show only once (" + i + ")");
        }
        check(f, roundTrip(d).titleShown(Chapters.WHALE), "shown must persist");
        // 場面の間は出さず、終わってから出す
        ChapterCards.setBusyForTest(Boolean.TRUE);
        try {
            check(f, !ChapterCards.tick(p, Chapters.TIDE, false, 9000) && !ChapterCards.tick(p, Chapters.TIDE, false, 20000), "no title card during a cinematic");
            check(f, !d.titleShown(Chapters.TIDE), "the card is not used up while hidden");
        } finally {
            ChapterCards.setBusyForTest(Boolean.FALSE);
        }
        check(f, ChapterCards.tick(p, Chapters.TIDE, false, 20001), "the title card should show right after the cinematic ends");
        check(f, !ChapterCards.tick(p, Chapters.TIDE, false, 40000), "...and only once");

        // 序章・五章・すでに遊んでいた人: 出さず、出したことにする
        d.reset();
        ChapterCards.forget(p.getUUID());
        check(f, !ChapterCards.tick(p, Chapters.PROLOGUE, false, 0) && !ChapterCards.tick(p, Chapters.PROLOGUE, false, 1000), "no title card in the prologue");
        check(f, !ChapterCards.tick(p, Chapters.ABYSS, false, 0) && d.titleShown(Chapters.ABYSS), "no card for chapters that are not written yet");
        d.reset();
        check(f, !ChapterCards.tick(p, Chapters.STORM, true, 0) && !ChapterCards.tick(p, Chapters.STORM, true, 500) && d.titleShown(Chapters.STORM)
                && d.titleShown(Chapters.WHALE), "a legacy player gets no card");
        // 先の章で始めた人: その章の札だけ。前の章の札は出さない
        d.reset();
        ChapterCards.forget(p.getUUID());
        ChapterCards.tick(p, Chapters.STORM, false, 0);
        check(f, ChapterCards.tick(p, Chapters.STORM, false, ChapterCards.TITLE_DELAY) && d.titleShown(Chapters.WHALE) && d.titleShown(Chapters.TIDE)
                && d.titleShown(Chapters.STORM) && !d.titleShown(Chapters.EMBER), "a player already in chapter 3 gets the chapter 3 card only");
        check(f, !ChapterCards.tick(p, Chapters.WHALE, false, 99999) && !ChapterCards.tick(p, Chapters.TIDE, false, 99999), "earlier chapters never show again");

        // 余韻: 章ごとに一度。場面の間は預かる
        d.reset();
        ChapterCards.forget(p.getUUID());
        ChapterCards.setBusyForTest(Boolean.FALSE);
        check(f, ChapterCards.aftermath(p, Chapters.WHALE, Component.literal("x")) && d.aftermathShown(Chapters.WHALE), "aftermath should be delivered and recorded");
        check(f, !ChapterCards.aftermath(p, Chapters.WHALE, Component.literal("x")), "aftermath only once per chapter");
        ChapterCards.setBusyForTest(Boolean.TRUE);
        try {
            check(f, ChapterCards.aftermath(p, Chapters.TIDE, Component.literal("y")) && !d.aftermathShown(Chapters.TIDE) && ChapterCards.held(p.getUUID()) == 1,
                    "aftermath during a cinematic is held");
            check(f, !ChapterCards.aftermath(p, Chapters.TIDE, Component.literal("y")) && ChapterCards.held(p.getUUID()) == 1, "...held only once");
        } finally {
            ChapterCards.setBusyForTest(Boolean.FALSE);
        }
        check(f, ChapterCards.tick(p, Chapters.TIDE, true, 0) && d.aftermathShown(Chapters.TIDE) && ChapterCards.held(p.getUUID()) == 0,
                "held aftermath is delivered after the cinematic");
        check(f, roundTrip(d).aftermathShown(Chapters.WHALE), "aftermath shown must persist");
        // 文
        check(f, Chapters.WHALE.label().getString().length() > 0 && ChapterCards.titleLine(Chapters.WHALE).getContents() != null
                && ChapterCards.aftermathHead(Chapters.WHALE).getString().length() > 0, "texts");
        d.reset();
        ChapterCards.forget(p.getUUID());
        ChapterCards.setBusyForTest(null);
    }

    // ---------------------------------------------------------------- 知らせの種類 (順番待ちの中での並び)
    private static void notices(List<String> f) {
        check(f, Notice.Kind.CHAPTER.channel() == Notice.Channel.BANNER && Notice.Kind.AFTERMATH.channel() == Notice.Channel.BANNER,
                "chapter and aftermath cards share the top banner queue");
        // 末尾に足した (パケットは番号で送る)
        Notice.Kind[] k = Notice.Kind.values();
        check(f, k[k.length - 2] == Notice.Kind.CHAPTER && k[k.length - 1] == Notice.Kind.AFTERMATH, "new kinds must stay at the end of the enum");
        NoticeQueue<String> q = new NoticeQueue<>();
        q.offer("alert", Notice.Kind.ALERT, "alert", 8);
        q.offer("chapter", Notice.Kind.CHAPTER, "chapter", 8);
        q.offer("after", Notice.Kind.AFTERMATH, "after", 20);
        q.advance(0);
        check(f, q.current() != null && q.current().key.equals("alert") && q.waiting() == 2, "the chapter card should wait behind an alert");
        long t = 0;
        while (!q.idle() && t < 120_000) {
            q.advance(100);
            t += 100;
        }
        check(f, q.shownKeys().equals(List.of("alert", "chapter", "after")), "cards queue behind other banner notices in order: " + q.shownKeys());
        check(f, NoticeQueue.holdMillis(Notice.Kind.CHAPTER, 10, 1.0, 0) >= NoticeQueue.holdMillis(Notice.Kind.PLACE, 10, 1.0, 0)
                && NoticeQueue.holdMillis(Notice.Kind.AFTERMATH, 20, 1.0, 0) >= 4500, "cards stay longer than a place name");
    }

    // ---------------------------------------------------------------- クエスト JSON の chapter 欄
    private static void quests(List<String> f) {
        String base = "{\"category\":\"sub\",%s\"stages\":[{\"id\":\"a\",\"objectives\":[{\"id\":\"o\",\"type\":\"signal\",\"target\":\"arc_test\"}]}]}";
        List<String> errors = new ArrayList<>();
        QuestDef withChapter = QuestDef.parse("arc_test", JsonParser.parseString(String.format(base, "\"chapter\":\"titan_tide\",")).getAsJsonObject(), errors);
        check(f, errors.isEmpty() && withChapter.chapter().equals("titan_tide") && withChapter.chapterOrNull() == Chapters.TIDE, "chapter field should load: " + errors);
        errors.clear();
        QuestDef none = QuestDef.parse("arc_test", JsonParser.parseString(String.format(base, "")).getAsJsonObject(), errors);
        check(f, errors.isEmpty() && none.chapter().isEmpty() && none.chapterOrNull() == null, "a quest without the field still loads (old JSON): " + errors);
        errors.clear();
        QuestDef bad = QuestDef.parse("arc_test", JsonParser.parseString(String.format(base, "\"chapter\":\"nowhere\",")).getAsJsonObject(), errors);
        check(f, errors.size() == 1 && errors.get(0).contains("unknown chapter") && bad.chapter().isEmpty(), "an unknown chapter should be reported: " + errors);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        withChapter.write(buf);
        QuestDef read = QuestDef.read(buf);
        check(f, read.chapter().equals("titan_tide") && read.id().equals("arc_test") && read.stages().size() == 1, "chapter should survive the network round trip");
        buf.release();
        // 本物のクエスト (データパックから読んだもの): メインの 5 本は章を持つ
        if (QuestManager.quest("main_tide") != null) {
            check(f, "titan_whale".equals(QuestManager.quest("main_song").chapter()) && "titan_tide".equals(QuestManager.quest("main_tide").chapter())
                    && "titan_storm".equals(QuestManager.quest("main_thunder").chapter()) && "titan_ember".equals(QuestManager.quest("main_ember").chapter())
                    && "titan_ember".equals(QuestManager.quest("main_key").chapter()), "main quests should carry their chapter");
            for (QuestDef def : QuestManager.all()) {
                check(f, def.chapter().isEmpty() || Chapters.byId(def.chapter()) != null, def.id() + " has an unknown chapter");
            }
            check(f, QuestManager.loadErrors().stream().noneMatch(e -> e.contains("chapter")), "quest load errors mention chapter: " + QuestManager.loadErrors());
        } else {
            f.add("quests were not loaded");
        }
    }

    // ---------------------------------------------------------------- 会話の追加ファイル ("extends": true)
    private static void dialogues(List<String> f) {
        JsonObject base = JsonParser.parseString("{\"npc\":\"skyabyss:arc_test\",\"entries\":[{\"node\":\"hello\"}],"
                + "\"nodes\":{\"hello\":{\"options\":[{\"id\":\"bye\",\"kind\":\"leave\"}]}}}").getAsJsonObject();
        JsonObject ext = JsonParser.parseString("{\"npc\":\"skyabyss:arc_test\",\"extends\":true,"
                + "\"entries\":[{\"if\":{\"type\":\"quest\",\"quest\":\"main_song\",\"is\":\"done\"},\"node\":\"ch_hello\"}],"
                + "\"nodes\":{\"ch_hello\":{\"face\":\"grave\",\"options\":[{\"id\":\"ok\",\"goto\":\"@entry\"}]}},"
                + "\"add_options\":{\"hello\":{\"before\":\"bye\",\"options\":[{\"id\":\"more\",\"goto\":\"ch_hello\"}]}}}").getAsJsonObject();
        List<String> errors = new ArrayList<>();
        DialogueManager.merge(base, ext, "arc_test_ext", errors);
        Dialogue d = Dialogue.parse("skyabyss:arc_test", base, errors);
        check(f, errors.isEmpty(), "an extra dialogue file should merge cleanly: " + errors);
        check(f, d.nodes().containsKey("ch_hello") && d.entries().size() == 2 && d.entries().get(0).node().equals("ch_hello")
                && d.entries().get(1).node().equals("hello"), "extra nodes and entries (before the unconditional last entry) should load");
        check(f, d.nodes().get("hello").options().stream().map(Dialogue.Option::id).toList().equals(List.of("more", "bye")),
                "add_options should insert before the named option");
        // 本物: 追加ファイル (storyteller_free / storyteller_story …) を足した語り部が読めている
        Dialogue st = DialogueManager.dialogue("skyabyss:storyteller");
        check(f, st != null && st.nodes().containsKey("ms_coal_offer") && DialogueManager.loadErrors().isEmpty(),
                "the storyteller should load with its extension files: " + DialogueManager.loadErrors());
    }

    // ---------------------------------------------------------------- 空の段の口
    private static void sky(MinecraftServer server, List<String> f) {
        // 登録がなければ全章 NONE (今の動きのまま)
        for (Chapters c : Chapters.values()) {
            check(f, ChapterSky.stage(c).equals(ChapterSky.Stage.NONE), "no sky stage should be registered by default: " + c);
        }
        ChapterSky.setStage(Chapters.TIDE, new ChapterSky.Stage(0.1f, 0x8FB8C8, 0.05f));
        try {
            check(f, ChapterSky.stage(Chapters.TIDE).moonBias() == 0.1f && ChapterSky.stage(Chapters.WHALE).equals(ChapterSky.Stage.NONE), "a sky stage should register per chapter");
        } finally {
            ChapterSky.setStage(Chapters.TIDE, null);
        }
        check(f, ChapterSky.stage(Chapters.TIDE).equals(ChapterSky.Stage.NONE), "clearing a stage should restore NONE");
        net.minecraft.network.RegistryFriendlyByteBuf pb = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        jp.revati.skyabyss.sky.ChapterTintPayload tint = new jp.revati.skyabyss.sky.ChapterTintPayload(0x8FB8C8, 0.05f);
        jp.revati.skyabyss.sky.ChapterTintPayload.STREAM_CODEC.encode(pb, tint);
        check(f, tint.equals(jp.revati.skyabyss.sky.ChapterTintPayload.STREAM_CODEC.decode(pb)), "tint payload round trip");
        pb.release();
        FakePlayer p = fake(server, "sky");
        float world = ChapterSky.moonNearness(server.overworld());
        check(f, ChapterSky.moonNearness(server.overworld(), (ServerPlayer) p) == world, "with no bias the per-player moon equals the world moon");
    }
}
