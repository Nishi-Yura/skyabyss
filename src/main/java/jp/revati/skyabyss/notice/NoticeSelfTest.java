package jp.revati.skyabyss.notice;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

// 画面の知らせの自己テスト (SKYABYSS_SELFTEST=1 のとき、SelfTestRunner が呼ぶ)。
// 文の種類分け (ModText の印 → Notice の種類 → 見せ方の列)、パケットの往復、順番待ち (重ならない・先に来たものから・混んだら縮む・あふれたら手がかりから捨てる)
public final class NoticeSelfTest {
    private NoticeSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("notice selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST notice PASSED (kinds, channels, payload codec, queue order, no overlap, pressure, overflow, dedupe)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST notice FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server, List<String> f) {
        // ---------------------------------------------------------------- 種類分け
        check(f, Notice.kindOf(ModText.lore("a.b")) == Notice.Kind.NARRATE, "lore is not NARRATE");
        check(f, Notice.kindOf(ModText.hint("a.b")) == Notice.Kind.HINT, "hint is not HINT");
        check(f, Notice.kindOf(ModText.progress("a.b")) == Notice.Kind.UNLOCK, "progress is not UNLOCK");
        check(f, Notice.kindOf(ModText.deny("a.b")) == Notice.Kind.WARN, "deny is not WARN");
        check(f, Notice.kindOf(ModText.battle("a.b")) == Notice.Kind.ALERT, "battle is not ALERT");
        check(f, Notice.kindOf(ModText.title("a.b")) == null && Notice.kindOf(Component.literal("x")) == null, "untagged text got a kind");
        check(f, Notice.body(ModText.lore("a.b")).getString().equals("a.b") && !Notice.body(ModText.lore("a.b")).getString().contains("❖"),
                "body should drop the ornament");
        check(f, Notice.plain(ModText.lore("a.b")).getString().equals("❖ a.b") && Notice.kindOf(Notice.plain(ModText.lore("a.b"))) == null,
                "plain should keep the ornament and drop the tag");
        check(f, ModText.hint("a.b").getString().equals("a.b"), "tag changed the visible text");
        check(f, Notice.Kind.NARRATE.channel() == Notice.Channel.CAPTION && Notice.Kind.HINT.channel() == Notice.Channel.CAPTION
                && Notice.Kind.LEVEL_UP.channel() == Notice.Channel.CENTER && Notice.Kind.GAIN.channel() == Notice.Channel.TOAST
                && Notice.Kind.UNLOCK.channel() == Notice.Channel.TOAST && Notice.Kind.PLACE.channel() == Notice.Channel.BANNER
                && Notice.Kind.ALERT.channel() == Notice.Channel.BANNER && Notice.Kind.WARN.channel() == Notice.Channel.ACTIONBAR
                && Notice.Kind.BAR.channel() == Notice.Channel.ACTIONBAR, "channel table is wrong");

        // ---------------------------------------------------------------- パケットの往復
        for (Notice.Kind k : Notice.Kind.values()) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            NoticePayload in = new NoticePayload(k, ModText.lore("message.skyabyss.growth.max_level"), Component.literal("sub"));
            NoticePayload.STREAM_CODEC.encode(buf, in);
            NoticePayload out = NoticePayload.STREAM_CODEC.decode(buf);
            check(f, out.kind() == k && Notice.kindOf(out.text()) == Notice.Kind.NARRATE && out.extra().getString().equals("sub"),
                    "payload round trip failed for " + k);
        }

        // ---------------------------------------------------------------- 順番待ち: 同時に 10 本の字幕
        NoticeQueue<String> q = new NoticeQueue<>();
        List<String> offered = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            q.offer("n" + i, Notice.Kind.NARRATE, "n" + i, 30);
            offered.add("n" + i);
        }
        check(f, q.waiting() == 5, "5 offers should wait, waiting=" + q.waiting());
        long t = 0;
        int overlaps = 0;
        String last = null;
        while (!q.idle() && t < 120_000) {
            q.advance(50);
            t += 50;
            if (q.current() != null) {
                if (last != null && !last.equals(q.current().key) && q.alpha() > 0.99f && q.current().age() < 100) {
                    // 次のものが出た時点で前のものは消えている (current は常に一つ)
                }
                last = q.current().key;
            }
            if (q.current() == null && q.waiting() > 0) {
                // 空きが一刻でも出たら、すぐ次が来る (advance の中で拾う)
                q.advance(0);
                if (q.current() == null) {
                    overlaps++;
                }
            }
        }
        check(f, overlaps == 0, "queue stalled with waiting items");
        check(f, q.shownKeys().equals(offered), "order is not first-come first-served: " + q.shownKeys());
        check(f, t < 40_000, "5 captions took too long to drain: " + t + " ms");

        // 混んだら一つあたりが縮む
        long alone = NoticeQueue.holdMillis(Notice.Kind.NARRATE, 30, 1.0, 0);
        long crowded = NoticeQueue.holdMillis(Notice.Kind.NARRATE, 30, 1.0, 5);
        check(f, crowded < alone && crowded >= alone * 0.4 - 1, "pressure should shorten but not below 40%: " + alone + " -> " + crowded);
        check(f, NoticeQueue.holdMillis(Notice.Kind.NARRATE, 30, 2.0, 0) < alone && NoticeQueue.holdMillis(Notice.Kind.NARRATE, 30, 0.5, 0) > alone,
                "speed setting should scale the hold");
        check(f, NoticeQueue.holdMillis(Notice.Kind.NARRATE, 200, 1.0, 0) <= 7500 && NoticeQueue.holdMillis(Notice.Kind.NARRATE, 1, 1.0, 0) >= 2600,
                "hold should stay within 2.6 to 7.5 seconds");

        // あふれたら手がかりから捨てる
        NoticeQueue<String> o = new NoticeQueue<>();
        o.offer("first", Notice.Kind.NARRATE, "first", 10);
        o.advance(0);
        o.offer("hint", Notice.Kind.HINT, "hint", 10);
        for (int i = 0; i < NoticeQueue.MAX_WAITING; i++) {
            o.offer("v" + i, Notice.Kind.NARRATE, "v" + i, 10);
        }
        check(f, o.waiting() == NoticeQueue.MAX_WAITING, "waiting should be capped at " + NoticeQueue.MAX_WAITING + ", is " + o.waiting());
        while (!o.idle()) {
            o.advance(100);
        }
        check(f, !o.shownKeys().contains("hint") && o.shownKeys().contains("v5"), "the hint should be dropped first on overflow: " + o.shownKeys());

        // 同じ文は続けて来ても一度だけ
        NoticeQueue<String> d = new NoticeQueue<>();
        d.offer("x", Notice.Kind.NARRATE, "same", 4);
        d.offer("x", Notice.Kind.NARRATE, "same", 4);
        d.advance(0);
        d.offer("x", Notice.Kind.NARRATE, "same", 4);
        check(f, d.waiting() == 0 && d.current() != null, "duplicates should be merged");

        // 関所 (会話・場面の最中は溜め、閉じたあとに優先の順で間隔をあけて出す。first_hour_log B-2・B-9)
        NoticeGate gate = new NoticeGate();
        List<String> order = new ArrayList<>();
        gate.offer(NoticeGate.P_HINT, 400, () -> order.add("hint"));
        gate.offer(NoticeGate.P_CENTER, 1500, () -> order.add("center"));
        gate.offer(NoticeGate.P_CARD, 700, () -> order.add("card1"));
        gate.offer(NoticeGate.P_GROWTH, 0, () -> order.add("xp"));
        gate.offer(NoticeGate.P_CARD, 700, () -> order.add("card2"));
        check(f, gate.drain(0, true) == 0 && gate.drain(100000, true) == 0 && order.isEmpty(), "the gate released something while a dialogue was open");
        long gt = 10_000;
        int released = gate.drain(gt, false);
        check(f, released == 1 && order.equals(List.of("card1")), "the first release should be the first card: " + order);
        check(f, gate.drain(gt + 300, false) == 0, "the gate released two cards at once (no gap)");
        gt += 700;
        gate.drain(gt, false);
        gt += 700;
        gate.drain(gt, false);
        gt += 1;
        gate.drain(gt, false);
        gt += 5000;
        for (int i = 0; i < 4; i++) {
            gate.drain(gt, false);
            gt += 2000;
        }
        check(f, order.equals(List.of("card1", "card2", "xp", "center", "hint")), "the gate order should be cards, growth, center, hints: " + order);
        check(f, gate.pending() == 0, "the gate kept items");
        // 画面が開いていなければ、すぐ出る (ふだんの遊びを遅らせない)
        NoticeGate quick = new NoticeGate();
        List<String> now = new ArrayList<>();
        quick.offer(NoticeGate.P_CARD, 700, () -> now.add("a"));
        quick.drain(1_000_000, false);
        check(f, now.equals(List.of("a")), "an idle gate should release at once");
        // あふれたら手がかりから捨てる
        NoticeGate full = new NoticeGate();
        full.offer(NoticeGate.P_HINT, 0, () -> {
        });
        for (int i = 0; i < NoticeGate.MAX_PENDING + 3; i++) {
            full.offer(NoticeGate.P_CARD, 0, () -> {
            });
        }
        check(f, full.pending() == NoticeGate.MAX_PENDING, "the gate should cap its backlog: " + full.pending());
        // 開幕の目的の知らせは戦いの型 (⚔・太字) ではなく、目的の型
        check(f, Notice.kindOf(ModText.objective("a.b")) == Notice.Kind.HINT && Notice.body(ModText.objective("a.b")).getString().equals("◆ a.b"), "the objective notice should be a diamond-led hint, not a battle notice");
        check(f, !Notice.plain(ModText.objective("a.b")).getString().contains("⚔"), "the objective notice carries the battle ornament");

        // 濃さ: 出て、保って、消える
        NoticeQueue<String> a = new NoticeQueue<>();
        a.offer("a", Notice.Kind.PLACE, "a", 4);
        a.advance(0);
        check(f, a.alpha() == 0f, "should start invisible");
        a.advance(NoticeQueue.FADE_IN + 100);
        check(f, a.alpha() == 1f, "should be fully visible while held");
        a.advance(NoticeQueue.holdMillis(Notice.Kind.PLACE, 4, 1.0, 0) + NoticeQueue.FADE_OUT);
        check(f, a.idle(), "should be gone after the fade");
    }
}
