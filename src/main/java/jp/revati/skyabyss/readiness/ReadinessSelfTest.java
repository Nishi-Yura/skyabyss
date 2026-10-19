package jp.revati.skyabyss.readiness;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.HeartbeatWard;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.readiness.BoardReadiness.Flow;
import jp.revati.skyabyss.readiness.BoardReadiness.Mode;
import jp.revati.skyabyss.readiness.BoardReadiness.Outcome;
import jp.revati.skyabyss.readiness.Readiness.Colour;
import jp.revati.skyabyss.readiness.Readiness.Lamp;
import jp.revati.skyabyss.record.RecordBuilder;
import jp.revati.skyabyss.record.RecordSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 備えの三灯の自己テスト (SKYABYSS_SELFTEST=1。SELFTEST readiness PASSED / FAILED)。
//  - 式と色の境目 (0.9 は緑、0.7 は黄、0.7 未満は赤、1.1 の頭打ち、巨神四体の目安ちょうどで 1.0)
//  - 持ち物から読む値 (武器・回復の品・体力・防御の属性)
//  - 乗り込みの確認の流れ (右クリックと入り直し、5 秒の窓、debounce、別の巨神)
//  - 前室の囁き (最初の一度だけ、緑なら言わない)、控え帳の「備え」の行、日英の文
public final class ReadinessSelfTest {
    private ReadinessSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("readiness selftest crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST readiness PASSED (formula and colour boundaries, inventory scan, confirm flow, antechamber whisper, record row, ja/en lang)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST readiness FAILED: {}", x));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    static void run(MinecraftServer server, List<String> f) {
        ServerLevel overworld = server.overworld();
        FakePlayer p = FakePlayerFactory.get(overworld, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-00000000c001"), "[readiness_test]"));
        p.getInventory().clearContent();
        formula(f);
        flow(f, p);
        lang(f);
        advice(f);
        summary(f, overworld);
        inventory(f, p);
        whisper(f, p);
        record(f, p);
    }

    // ---------------------------------------------------------------- 式
    static void formula(List<String> f) {
        // 色の境目
        check(f, Readiness.colour(0.9) == Colour.GREEN, "0.9 should be green");
        check(f, Readiness.colour(0.8999) == Colour.YELLOW, "0.8999 should be yellow");
        check(f, Readiness.colour(0.7) == Colour.YELLOW, "0.7 should be yellow");
        check(f, Readiness.colour(0.6999) == Colour.RED, "0.6999 should be red");
        check(f, Readiness.colour(0) == Colour.RED && Readiness.colour(1.1) == Colour.GREEN, "0 red / 1.1 green");

        for (GearCurve.Stage s : GearCurve.STAGES) {
            double goal = Readiness.goalHearts(s.id());
            check(f, goal >= 13 && goal <= 19, s.id() + " goal hearts " + goal);
            // 目安ちょうど = 1.0
            check(f, near(Readiness.body(s.armor(), s.toughness(), goal * 2, s, goal), 1.0), s.id() + " body at the benchmark should be 1.0");
            check(f, near(Readiness.blade(s.dps0(), s), 1.0), s.id() + " blade at the benchmark should be 1.0");
            // 頭打ち
            check(f, near(Readiness.body(100, 100, 1000, s, goal), Readiness.CAP), s.id() + " body should cap at 1.1");
            check(f, near(Readiness.blade(s.dps0() * 5, s), Readiness.CAP), s.id() + " blade should cap at 1.1");
            // 武器の境目: 0.9 倍は緑、0.89 倍は黄、0.7 倍は黄、0.69 倍は赤
            check(f, Readiness.colour(Readiness.blade(s.dps0() * 0.9, s)) == Colour.GREEN, s.id() + " blade 0.9 green");
            check(f, Readiness.colour(Readiness.blade(s.dps0() * 0.89, s)) == Colour.YELLOW, s.id() + " blade 0.89 yellow");
            check(f, Readiness.colour(Readiness.blade(s.dps0() * 0.7, s)) == Colour.YELLOW, s.id() + " blade 0.7 yellow");
            check(f, Readiness.colour(Readiness.blade(s.dps0() * 0.69, s)) == Colour.RED, s.id() + " blade 0.69 red");
            // 身の重み: 防具だけ目安・体力は素の 10❤ のとき 0.6 + 0.4 × 10 ÷ 目安
            double plainHealth = Readiness.body(s.armor(), s.toughness(), 20, s, goal);
            check(f, near(plainHealth, 0.6 + 0.4 * 20 / (goal * 2)), s.id() + " body with plain health " + plainHealth);
            // 防具なし・素の体力は赤、防具だけが 85% 以上あっても体力が薄いと黄
            check(f, Readiness.colour(Readiness.body(0, 0, 20, s, goal)) == Colour.RED, s.id() + " naked body should be red");
        }
        GearCurve.Stage whale = Readiness.stage(Titans.STONE_WHALE);
        // 旅装 (一章 17/5 = 22 に対し 20+8 = 28 → 0.786) と素の体力 10❤ では身は 0.6×0.786 + 0.4×0.769 = 0.779 で黄 (旅装で赤から黄へ、体力増強で緑へ)
        double travel = Readiness.body(17, 5, 20, whale, 13);
        check(f, Readiness.colour(travel) == Colour.YELLOW, "chapter-1 travel gear with plain health should be yellow, got " + travel);
        check(f, Readiness.colour(Readiness.body(17, 5, 26, whale, 13)) == Colour.GREEN || Readiness.colour(Readiness.body(17, 5, 26, whale, 13)) == Colour.YELLOW,
                "travel gear with full health is yellow or green");
        // 糧
        check(f, near(Readiness.provisions(24, false), 1.0), "24 points of healing = 1.0");
        check(f, Readiness.colour(Readiness.provisions(0, false)) == Colour.RED, "no provisions red");
        check(f, Readiness.colour(Readiness.provisions(12, false)) == Colour.RED, "12 points (6 hearts) red");
        check(f, Readiness.colour(Readiness.provisions(17, false)) == Colour.YELLOW, "17 points yellow");
        check(f, Readiness.colour(Readiness.provisions(12, true)) == Colour.YELLOW, "12 points + charm = 0.7 yellow");
        check(f, Readiness.colour(Readiness.provisions(21.6, false)) == Colour.GREEN, "21.6 points green");
        check(f, near(Readiness.provisions(0, true), Readiness.CHARM_BONUS), "charm alone is 0.2");
        // 結果の一番弱い灯・赤
        Readiness.Result r = new Readiness.Result(Titans.EMBER, 1.0, 0.75, 0.5);
        check(f, r.anyRed() && r.weakest() == Lamp.PROVISIONS && r.worst() == Colour.RED, "result weakest/red");
        Readiness.Result g = new Readiness.Result(Titans.EMBER, 1.0, 0.95, 0.9);
        check(f, !g.anyRed() && g.weakest() == null && g.worst() == Colour.GREEN, "all green result");
        check(f, Readiness.stage("nobody") == null && !Readiness.none("nobody").anyRed(), "unknown titan reads as all green");
    }

    // ---------------------------------------------------------------- 確認の流れ
    static void flow(List<String> f, FakePlayer fake) {
        UUID a = UUID.randomUUID();
        // 赤なし: そのまま。待ちの途中なら消える
        Flow flow = new Flow();
        check(f, flow.step(a, Titans.TIDE_SHELL, 0, false, Mode.RIGHT_CLICK) == Outcome.PROCEED, "no red proceeds");
        check(f, !flow.isPending(a), "nothing pending after proceed");
        check(f, BoardReadiness.attempt(fake, Titans.STONE_WHALE, Mode.RIGHT_CLICK) && BoardReadiness.attempt(fake, Titans.EMBER, Mode.WALK_IN),
                "fake players (other selftests) are never held up, even bare");
        // 右クリック: 一度目は止まる、二度目 (debounce のあと、5 秒以内) で乗る
        check(f, flow.step(a, Titans.STONE_WHALE, 100, true, Mode.RIGHT_CLICK) == Outcome.PROMPT, "first click prompts");
        check(f, flow.step(a, Titans.STONE_WHALE, 102, true, Mode.RIGHT_CLICK) == Outcome.WAITING, "double-fire inside the debounce waits silently");
        check(f, flow.step(a, Titans.STONE_WHALE, 105, true, Mode.RIGHT_CLICK) == Outcome.CONFIRMED, "second click confirms");
        check(f, !flow.isPending(a), "confirmed clears the pending");
        check(f, flow.step(a, Titans.STONE_WHALE, 110, true, Mode.RIGHT_CLICK) == Outcome.PROMPT, "after boarding the next try prompts again");
        // 5 秒の窓: ちょうど 100 tick までは数え、101 で切れて新しい一度目
        check(f, flow.step(a, Titans.STONE_WHALE, 210, true, Mode.RIGHT_CLICK) == Outcome.CONFIRMED, "100 ticks after the first is still in the window");
        check(f, flow.step(a, Titans.STONE_WHALE, 300, true, Mode.RIGHT_CLICK) == Outcome.PROMPT, "prompt");
        check(f, flow.step(a, Titans.STONE_WHALE, 401, true, Mode.RIGHT_CLICK) == Outcome.PROMPT, "101 ticks later is a new first prompt");
        // 別の巨神の確認は引き継がない
        check(f, flow.step(a, Titans.TIDE_SHELL, 410, true, Mode.RIGHT_CLICK) == Outcome.PROMPT, "another titan starts a new prompt");
        // 灯が緑になったら待ちは消える (その間に整えた)
        check(f, flow.step(a, Titans.TIDE_SHELL, 420, false, Mode.RIGHT_CLICK) == Outcome.PROCEED && !flow.isPending(a), "greening clears the pending");
        // 入って乗る入口: 入ったまま (4 tick ごと) の連続は二度目に数えず、出て入り直すと乗る
        long t = 1000;
        check(f, flow.step(a, Titans.THUNDER_WING, t, true, Mode.WALK_IN) == Outcome.PROMPT, "walk-in first touch prompts");
        for (int i = 1; i <= 8; i++) {
            check(f, flow.step(a, Titans.THUNDER_WING, t + 4L * i, true, Mode.WALK_IN) == Outcome.WAITING, "standing inside is not a second try (" + i + ")");
        }
        long left = t + 32 + REENTER + 1;
        check(f, flow.step(a, Titans.THUNDER_WING, left, true, Mode.WALK_IN) == Outcome.CONFIRMED, "re-entering within the window confirms");
        // 入り直しが 5 秒を過ぎたら、新しい一度目
        check(f, flow.step(a, Titans.EMBER, 2000, true, Mode.WALK_IN) == Outcome.PROMPT, "prompt");
        check(f, flow.step(a, Titans.EMBER, 2000 + BoardReadiness.WINDOW + 30, true, Mode.WALK_IN) == Outcome.PROMPT, "re-entering after the window is a new prompt");
        // 人ごとに別
        UUID b = UUID.randomUUID();
        check(f, flow.step(b, Titans.EMBER, 2000 + BoardReadiness.WINDOW + 40, true, Mode.WALK_IN) == Outcome.PROMPT, "another player has their own prompt");
        // 偽プレイヤーと管理者は素通り (自己テストの乗り込みを止めない)
    }

    private static final int REENTER = BoardReadiness.REENTER_GAP;

    // ---------------------------------------------------------------- 持ち物から
    static void inventory(List<String> f, FakePlayer p) {
        // 何も持たない: 防具なし (属性 0)・刃なし・糧なし = 赤三つ
        Readiness.Result bare = Readiness.of(p, Titans.STONE_WHALE);
        check(f, bare.colour(Lamp.BODY) == Colour.RED && bare.colour(Lamp.BLADE) == Colour.RED && bare.colour(Lamp.PROVISIONS) == Colour.RED,
                "bare player should be red on all three, got " + bare);
        check(f, near(bare.blade(), 0) && near(bare.provisions(), 0), "bare blade/provisions are 0");

        // 武器: ダイヤの剣 (7 × 1.6 = 11.2 → 11.5 に対し 0.974 で緑)、鉄 (6 × 1.6 = 9.6 → 0.835 黄)、石 (5 × 1.6 = 8 → 0.696 赤)
        double diamond = Readiness.weaponDps(new ItemStack(Items.DIAMOND_SWORD), 0);
        check(f, near(diamond, GearCurve.diamondSwordDps()), "diamond sword dps " + diamond);
        p.getInventory().setItem(3, new ItemStack(Items.STONE_SWORD));
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.BLADE) == Colour.RED, "stone sword is red against the whale stage");
        p.getInventory().setItem(4, new ItemStack(Items.IRON_SWORD));
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.BLADE) == Colour.YELLOW, "iron sword is yellow against the whale stage");
        p.getInventory().setItem(5, new ItemStack(Items.DIAMOND_SWORD));
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.BLADE) == Colour.GREEN, "diamond sword is green against the whale stage");
        check(f, Readiness.of(p, Titans.EMBER).colour(Lamp.BLADE) == Colour.RED, "diamond sword is red against the ember stage");
        // 武器でない品 (パン・つるはしでない棒) は刃に数えない
        check(f, near(Readiness.weaponDps(new ItemStack(Items.BREAD), 0), 0) && near(Readiness.weaponDps(new ItemStack(Items.STICK), 0), 0), "non-weapons do not count");
        // 砥石: 足し込み口。鉄の剣 (+1.5 攻撃) で 0.835 → (6 + 1.5) × 1.6 = 12 → 緑
        double[] stone = {0};
        Readiness.registerWhetstone(pl -> pl == p ? stone[0] : 0);
        p.getInventory().setItem(5, ItemStack.EMPTY);
        stone[0] = 1.5;
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.BLADE) == Colour.GREEN, "whetstone lifts the iron sword to green");
        stone[0] = 0;
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.BLADE) == Colour.YELLOW, "no whetstone back to yellow");

        // 糧: 金のリンゴ (再生 5 秒 = 4 点 + 吸収 4 点 = 8 点) 3 個で 24 点 = 緑
        ItemStack apple = new ItemStack(Items.GOLDEN_APPLE, 2);
        check(f, near(Readiness.pointsPerItem(new ItemStack(Items.GOLDEN_APPLE)), 8), "golden apple = 8 points, got " + Readiness.pointsPerItem(new ItemStack(Items.GOLDEN_APPLE)));
        p.getInventory().setItem(10, apple);
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.PROVISIONS) == Colour.RED, "two golden apples (16 points = 0.67) are red");
        p.getInventory().setItem(11, new ItemStack(Items.GOLDEN_APPLE, 1));
        check(f, Readiness.of(p, Titans.STONE_WHALE).colour(Lamp.PROVISIONS) == Colour.GREEN, "three golden apples are green");
        // 薬: 即時回復 II の水薬 (8 点)。パンなど効果のない食べ物は数えない
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.STRONG_HEALING));
        check(f, near(Readiness.pointsPerItem(potion), 8), "strong healing potion = 8 points, got " + Readiness.pointsPerItem(potion));
        check(f, near(Readiness.pointsPerItem(new ItemStack(Items.BREAD, 5)), 0), "bread has no healing effect");
        // 鼓動守の足し込み口
        boolean[] charm = {false};
        Readiness.registerCharm(pl -> pl == p && charm[0]);
        p.getInventory().setItem(11, ItemStack.EMPTY);
        p.getInventory().setItem(10, new ItemStack(Items.GOLDEN_APPLE, 2));
        double without = Readiness.of(p, Titans.STONE_WHALE).provisions();
        charm[0] = true;
        check(f, near(Readiness.of(p, Titans.STONE_WHALE).provisions() - without, Readiness.CHARM_BONUS), "charm adds 0.2 to provisions");
        charm[0] = false;
        // 回復の品の登録口
        Readiness.registerProvision(st -> st.is(Items.HONEY_BOTTLE) ? 6 : 0);
        p.getInventory().setItem(10, new ItemStack(Items.HONEY_BOTTLE, 2));
        check(f, near(Readiness.healingPoints(p), 12), "registered provision counts, got " + Readiness.healingPoints(p));

        // 属性: 防御と強靭、最大体力。体力の差は身に出る
        var armor = p.getAttribute(Attributes.ARMOR);
        var tough = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
        var health = p.getAttribute(Attributes.MAX_HEALTH);
        double oa = armor.getBaseValue();
        double ot = tough.getBaseValue();
        double oh = health.getBaseValue();
        try {
            armor.setBaseValue(20);
            tough.setBaseValue(8);
            health.setBaseValue(20);
            Readiness.Result d = Readiness.of(p, Titans.STONE_WHALE);
            check(f, near(d.body(), 0.6 + 0.4 * 20 / 26), "diamond-armour body with 10 hearts, got " + d.body());
            check(f, d.colour(Lamp.BODY) == Colour.GREEN, "diamond armour with plain health is green for the whale (0.907)");
            check(f, Readiness.of(p, Titans.EMBER).colour(Lamp.BODY) == Colour.RED, "diamond armour is red for the ember stage");
            health.setBaseValue(38);
            check(f, near(Readiness.of(p, Titans.EMBER).body(), 0.6 * 28 / 38 + 0.4 * 38 / 38), "ember body with 19 hearts");
        } finally {
            armor.setBaseValue(oa);
            tough.setBaseValue(ot);
            health.setBaseValue(oh);
        }
        p.getInventory().clearContent();
    }

    // ---------------------------------------------------------------- 理由 + 手段の一行 (A7)
    private static String keyOf(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
    }

    private static Object[] argsOf(Component c) {
        return c.getContents() instanceof TranslatableContents tc ? tc.getArgs() : new Object[0];
    }

    static void advice(List<String> f) {
        String p = "message.skyabyss.readiness.";
        // 理由: 刃は割合 (62%)、何も持たないときは別の言い回し
        check(f, ReadinessAdvice.reasonKey(Lamp.BLADE, 0.62, 1, 1).equals(p + "reason.blade"), "blade reason key");
        check(f, ReadinessAdvice.reasonKey(Lamp.BLADE, 0, 1, 1).equals(p + "reason.blade.none"), "no weapon reason key");
        check(f, ReadinessAdvice.reasonKey(Lamp.PROVISIONS, 0.4, 1, 1).equals(p + "reason.provisions"), "provisions reason key");
        check(f, ReadinessAdvice.reasonKey(Lamp.PROVISIONS, 0, 1, 1).equals(p + "reason.provisions.none"), "no provisions reason key");
        check(f, ReadinessAdvice.percent(Lamp.BLADE, 0.62, 1, 1) == 62, "blade percent 62, got " + ReadinessAdvice.percent(Lamp.BLADE, 0.62, 1, 1));
        // 身は低い方の内訳 (防具か体力) を言い分ける
        check(f, ReadinessAdvice.reasonKey(Lamp.BODY, 0.7, 0.5, 0.9).equals(p + "reason.body.armor"), "body: armour is the weaker part");
        check(f, ReadinessAdvice.reasonKey(Lamp.BODY, 0.7, 1.0, 0.7).equals(p + "reason.body.health"), "body: health is the weaker part");
        check(f, ReadinessAdvice.percent(Lamp.BODY, 0.7, 0.5, 0.9) == 50 && ReadinessAdvice.percent(Lamp.BODY, 0.7, 1.0, 0.7) == 70, "body percent is the weaker part");
        // 手段: 登録がなければ一般の言い回し (砥石)、あれば名前つき
        Component line = ReadinessAdvice.line(Lamp.BLADE, 0.62, 1, 1, List.of(), false);
        check(f, keyOf(line).equals(p + "line"), "advice line is the shared template");
        Object[] args = argsOf(line);
        boolean shaped = args.length == 3 && args[0] instanceof Component a0 && args[1] instanceof Component a1 && args[2] instanceof Component a2
                && keyOf(a0).equals(p + "name.blade") && keyOf(a1).equals(p + "reason.blade") && argsOf(a1).length == 1 && argsOf(a1)[0].equals(62)
                && keyOf(a2).equals(p + "remedy.blade.generic");
        check(f, shaped, "blade line without remedies: only things that exist now");
        Component stoned = ReadinessAdvice.line(Lamp.BLADE, 0.62, 1, 1, List.of(), true);
        check(f, argsOf(stoned)[2] instanceof Component r && keyOf(r).equals(p + "remedy.blade.whetstone"), "a registered whetstone switches the blade remedy");
        Component withRemedy = ReadinessAdvice.line(Lamp.PROVISIONS, 0.4, 1, 1, List.of(Component.literal("X")), false);
        check(f, argsOf(withRemedy)[2] instanceof Component r && keyOf(r).equals(p + "remedy.provisions.with"), "registered remedy is named");
        Component health = ReadinessAdvice.line(Lamp.BODY, 0.7, 1.0, 0.7, List.of(Component.literal("X")), false);
        check(f, argsOf(health)[2] instanceof Component r && keyOf(r).equals(p + "remedy.body.health.generic"), "thin health gets the health remedy, not armour");
        // 登録口: 鼓動守は糧の手段に入っている
        HeartbeatWard.init();
        check(f, !Readiness.remedies(Lamp.PROVISIONS).isEmpty(), "the heartbeat ward is a registered provisions remedy");
        int before = Readiness.remedies(Lamp.BLADE).size();
        Readiness.registerRemedy(Lamp.BLADE, () -> Component.literal("test"));
        check(f, Readiness.remedies(Lamp.BLADE).size() == before + 1, "registerRemedy adds a name");
        // 文: すべての鍵が日英にある
        Map<String, String> ja = read("/assets/skyabyss_readiness/lang/ja_jp.json", f);
        Map<String, String> en = read("/assets/skyabyss_readiness/lang/en_us.json", f);
        List<String> keys = new ArrayList<>(List.of(p + "line", p + "remedy.sep", p + "remedy.name.ward", p + "summary", p + "summary.others",
                p + "remedy.body.health.generic", p + "reason.body.armor", p + "reason.body.health", p + "reason.blade", p + "reason.blade.none",
                p + "reason.provisions", p + "reason.provisions.none"));
        for (Lamp l : Lamp.values()) {
            keys.add(p + "name." + l.id());
            keys.add(p + "remedy." + l.id() + ".with");
            if (l != Lamp.BODY) {
                keys.add(p + "remedy." + l.id() + ".generic");
            }
            if (l == Lamp.BLADE) {
                keys.add(p + "remedy.blade.whetstone");
            }
        }
        keys.add(p + "remedy.body.generic");
        for (String k : keys) {
            check(f, ja.containsKey(k) && !ja.get(k).isBlank(), "ja lang missing " + k);
            check(f, en.containsKey(k) && !en.get(k).isBlank(), "en lang missing " + k);
        }
        check(f, ja.get(p + "line").split("%s", -1).length == 4, "ja line template has three slots");
    }

    // ---------------------------------------------------------------- 扉の予告の準備確認 (A2)
    static void summary(List<String> f, ServerLevel overworld) {
        String p = "message.skyabyss.readiness.";
        Readiness.Result green = new Readiness.Result(Titans.STONE_WHALE, 1.0, 1.0, 1.0);
        Readiness.Result red = new Readiness.Result(Titans.STONE_WHALE, 1.0, 0.5, 0.8);
        Readiness.Result redLower = new Readiness.Result(Titans.STONE_WHALE, 0.6, 0.3, 1.0);
        check(f, !ReadinessSummary.anyoneRed(List.of(green, green)) && ReadinessSummary.anyoneRed(List.of(green, red)), "anyoneRed");
        check(f, ReadinessSummary.worstRed(green) == null && ReadinessSummary.worstRed(red) == Lamp.BLADE, "worstRed of a single red lamp");
        check(f, ReadinessSummary.worstRed(redLower) == Lamp.BLADE, "worstRed picks the lowest red lamp");
        // 赤でない人: だれかが赤なら「仲間の誰かの備えが低い」を足す (名前も人数もなし)。だれも赤でなければ足さない
        MutableComponent othersLow = ReadinessSummary.barLine(green, true, 3);
        check(f, keyOf(othersLow).equals(p + "summary") && othersLow.getSiblings().size() == 1
                && keyOf(othersLow.getSiblings().get(0)).equals(p + "summary.others"), "a green player sees the generic party line");
        check(f, argsOf(othersLow).length == 2 && argsOf(othersLow)[0].equals(3), "the summary carries only the seconds and the player's own lamps");
        check(f, ReadinessSummary.barLine(green, false, 2).getSiblings().isEmpty(), "no party line when nobody is red");
        // 赤の本人: ほかの人の赤は足さない (自分の三灯だけ。一行の直し方は字幕で別に出る)
        check(f, ReadinessSummary.barLine(red, true, 3).getSiblings().isEmpty(), "a red player sees no party line");
        // 人がいなければ何もしない
        ReadinessSummary.show(overworld, List.of(), 3, true);
    }

    // ---------------------------------------------------------------- 前室の囁き
    static void whisper(List<String> f, FakePlayer p) {
        for (String titan : Titans.all()) {
            check(f, ReadinessWhisper.antechamber(titan) != null, "antechamber for " + titan);
        }
        check(f, ReadinessWhisper.antechamber(Titans.STONE_WHALE).test(jp.revati.skyabyss.titan.dungeon.TitanBackLayout.HEART_REST.center()),
                "whale antechamber contains its centre");
        check(f, ReadinessWhisper.antechamber(Titans.TIDE_SHELL).test(new BlockPos(0, 51, -6)), "tide antechamber contains (0, 51, -6)");
        check(f, ReadinessWhisper.antechamber(Titans.THUNDER_WING).test(new BlockPos(0, jp.revati.skyabyss.titan.storm.back.StormBackLayout.KEEL_ANTE.floor() + 2, -44)), "storm antechamber contains the antechamber centre");
        var ante = jp.revati.skyabyss.titan.ember.back.EmberBackLayout.HEART_ANTE;
        check(f, ReadinessWhisper.antechamber(Titans.EMBER).test(new BlockPos((ante.minX() + ante.maxX()) / 2, ante.minY() + 1, (ante.minZ() + ante.maxZ()) / 2)),
                "ember antechamber contains its centre");
        check(f, !ReadinessWhisper.antechamber(Titans.STONE_WHALE).test(new BlockPos(0, 200, 0)), "far away is not the antechamber");

        // 赤のまま初めて入る: 囁く。二度目は言わない。緑なら言わずに「聞いた」ことにする
        p.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        check(f, !ReadinessWhisper.heardAlready(p, Titans.STONE_WHALE), "not heard yet");
        check(f, ReadinessWhisper.hear(p, Titans.STONE_WHALE), "a bare player is whispered to");
        check(f, ReadinessWhisper.heardAlready(p, Titans.STONE_WHALE), "heard is remembered");
        check(f, !ReadinessWhisper.hear(p, Titans.STONE_WHALE), "only once");
        check(f, !ReadinessWhisper.heardAlready(p, Titans.TIDE_SHELL), "other titans are separate");
        // 緑の人: 言わない
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        p.getInventory().setItem(1, new ItemStack(Items.GOLDEN_APPLE, 4));
        var armor = p.getAttribute(Attributes.ARMOR);
        var tough = p.getAttribute(Attributes.ARMOR_TOUGHNESS);
        double oa = armor.getBaseValue();
        double ot = tough.getBaseValue();
        try {
            armor.setBaseValue(20);
            tough.setBaseValue(8);
            check(f, Readiness.of(p, Titans.TIDE_SHELL).weakest() != null, "(sanity) the tide stage is not all green for a whale-level kit");
            armor.setBaseValue(100);
            tough.setBaseValue(100);
            p.getInventory().setItem(0, new ItemStack(Items.NETHERITE_SWORD));
            var health = p.getAttribute(Attributes.MAX_HEALTH);
            double oh = health.getBaseValue();
            health.setBaseValue(60);
            try {
                check(f, Readiness.of(p, Titans.TIDE_SHELL).weakest() == null, "an over-geared player is all green: " + Readiness.of(p, Titans.TIDE_SHELL));
                check(f, !ReadinessWhisper.hear(p, Titans.TIDE_SHELL), "all green says nothing");
                check(f, ReadinessWhisper.heardAlready(p, Titans.TIDE_SHELL), "all green still counts as heard");
            } finally {
                health.setBaseValue(oh);
            }
        } finally {
            armor.setBaseValue(oa);
            tough.setBaseValue(ot);
        }
        p.getInventory().clearContent();
        p.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- 控え帳の行
    static void record(List<String> f, FakePlayer p) {
        RecordSnapshot snap = RecordBuilder.build(p, true);
        RecordSnapshot.Tab page = snap.tab(RecordSnapshot.TITANS + RecordSnapshot.SUB + Titans.STONE_WHALE);
        check(f, page != null, "whale page exists");
        if (page == null) {
            return;
        }
        RecordSnapshot.Row row = page.row("readiness");
        check(f, row != null, "whale page has the readiness row");
        if (row != null) {
            String value = row.value().getString();
            check(f, value.contains("●") || value.contains("▲") || value.contains("■"), "readiness row shows the lamps: " + value);
            check(f, row.detail().size() == 5, "readiness row detail has the intro, three lamps and the free note, got " + row.detail().size());
            check(f, row.mark() == RecordSnapshot.MARK_LOST, "a bare player's readiness row is marked as short (red)");
        }
        // 章のチェックリスト (A9): 一章の頁に頼み・覚書の行 (寄り道は場所が登録されていれば)。見ていない巨神には出さない
        check(f, page.row("h.checklist") != null, "whale page has the chapter checklist header");
        RecordSnapshot.Row prep = page.row("check_prep");
        check(f, !jp.revati.skyabyss.guide.PrepQuests.defined(1) || (prep != null && prep.detail().size() == 4), "prep row (when the chapter has its requests): three requests and the note");
        check(f, jp.revati.skyabyss.guide.PrepQuests.defined(1) == (prep != null), "prep row appears exactly when the chapter has its requests");
        check(f, page.row("check_notes") != null, "notebook row on the whale page");
        long whaleSites = SiteSpawner.defs().stream().filter(d -> d.chapter() == Chapters.WHALE).count();
        check(f, (page.row("check_sites") != null) == (whaleSites > 0), "sites row appears exactly when the chapter has sites");
        // 見つけた数: 近づくと数える (クリアも見つけたことになる)
        SiteProgressData sites = SiteProgressData.get(p.server);
        check(f, !sites.found(p.getUUID(), "selftest_site"), "site not found yet");
        check(f, sites.markFound(p.getUUID(), "selftest_site") && !sites.markFound(p.getUUID(), "selftest_site"), "markFound once");
        check(f, sites.found(p.getUUID(), "selftest_site"), "found is remembered");
        sites.forget(p.getUUID(), "selftest_site");
        check(f, !sites.found(p.getUUID(), "selftest_site"), "forget clears found");
        check(f, sites.markCleared(p.getUUID(), "selftest_site2") && sites.found(p.getUUID(), "selftest_site2"), "cleared implies found");
        sites.forget(p.getUUID(), "selftest_site2");
        for (String lang : new String[] {"ja_jp", "en_us"}) {
            Map<String, String> rl = read("/assets/skyabyss_record/lang/" + lang + ".json", f);
            for (String k : List.of("h.checklist", "r.check_prep", "d.check_prep", "r.check_sites", "v.check_sites", "d.check_sites", "d.check_sites_hint",
                    "r.check_notes", "v.check_notes_yes", "v.check_notes_no", "d.check_notes")) {
                check(f, rl.containsKey("skyabyss_record." + k), lang + " record lang missing " + k);
            }
        }
        // 見知らぬ巨神 (まだ見ていない頁) には出さない
        String last = Titans.all().get(Titans.all().size() - 1);
        RecordSnapshot.Tab hidden = snap.tab(RecordSnapshot.TITANS + RecordSnapshot.SUB + last);
        if (hidden != null && hidden.row("unknown") != null) {
            check(f, hidden.row("readiness") == null, "a hidden titan's page has no readiness row");
            check(f, hidden.row("h.checklist") == null && hidden.row("check_prep") == null, "a hidden titan's page has no checklist");
        }
    }

    // ---------------------------------------------------------------- 日英の文
    static void lang(List<String> f) {
        Map<String, String> ja = read("/assets/skyabyss_readiness/lang/ja_jp.json", f);
        Map<String, String> en = read("/assets/skyabyss_readiness/lang/en_us.json", f);
        List<String> keys = new ArrayList<>();
        for (Lamp l : Lamp.values()) {
            keys.add("message.skyabyss.readiness.lamp." + l.id());
            keys.add("message.skyabyss.readiness.advice." + l.id());
            keys.add("message.skyabyss.readiness.advice." + l.id() + ".ok");
            keys.add(ReadinessWhisper.key(l, Colour.RED));
            keys.add(ReadinessWhisper.key(l, Colour.YELLOW));
        }
        keys.add("message.skyabyss.readiness.note");
        keys.add("message.skyabyss.readiness.confirm_click");
        keys.add("message.skyabyss.readiness.confirm_walk");
        for (String k : keys) {
            check(f, ja.containsKey(k) && !ja.get(k).isBlank(), "ja lang missing " + k);
            check(f, en.containsKey(k) && !en.get(k).isBlank(), "en lang missing " + k);
        }
        check(f, ja.size() == en.size(), "ja/en readiness lang size differs: " + ja.size() + " vs " + en.size());
        for (String k : List.of("skyabyss_record.r.readiness", "skyabyss_record.d.readiness", "skyabyss_record.d.readiness_free")) {
            check(f, read("/assets/skyabyss_record/lang/ja_jp.json", f).containsKey(k), "ja record lang missing " + k);
            check(f, read("/assets/skyabyss_record/lang/en_us.json", f).containsKey(k), "en record lang missing " + k);
        }
    }

    private static Map<String, String> read(String path, List<String> f) {
        Map<String, String> out = new java.util.HashMap<>();
        try (var in = ReadinessSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing resource " + path);
                return out;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            o.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            f.add("cannot read " + path + ": " + e);
        }
        return out;
    }
}
