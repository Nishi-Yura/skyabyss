package jp.revati.skyabyss.loot.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.Cinematics;
import jp.revati.skyabyss.cinematic.Scenes;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.item.TitanGearSets;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.story.ch1.Tasks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 心臓の遺物の選択 (HeartReward) の自己テスト。SKYABYSS_SELFTEST_ONLY="heart reward" でこれだけ流せる。結果は SELFTEST heart reward PASSED / FAILED。
// 画面の描画は窓がないと確かめられないので、サーバーの決まりを確かめる:
//   選べる品 (四体とも・専用伝説・作られた品だけ)、一度だけ受け取れる (二度目は断る・持ち物に一つ・図鑑に記す)、
//   選べない品・持っている品は断る (記録は変わらない)、入り直し (記録の往復・パケットの往復・開き直せる)、
//   二人 (それぞれ別に選べる・同じ品を選んでもよい・一人の選びがもう一人を消さない)、「あとで」で記録が変わらず、あとでも一度だけ受け取れる、
//   場面の終わりで画面が開く (スキップでも。終わる前には開かない)、日英の文、旧コマンド (/titanmemory choose) が同じ決まりを通る
public final class HeartRewardSelfTest {
    private static final String[] TITANS = {Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER};

    private static FakePlayer sceneP;

    private HeartRewardSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        List<SelfTestRunner.Step> steps = new ArrayList<>();
        steps.add(SelfTestRunner.Step.once("heart reward", HeartRewardSelfTest::runSelfTest));
        // 場面の終わりから 50 tick のあと (Tasks)。余裕を見て待つ
        steps.add(SelfTestRunner.Step.once("heart reward (scene open)", 90, HeartRewardSelfTest::checkSceneOpen));
        return steps;
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(ServerLevel level, String tail, String name) {
        FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000d" + tail), name));
        p.setPos(0.5, 100, 0.5);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static void setPending(MinecraftServer server, UUID id, String titan, boolean pending) {
        TitanMemoryData.Record r = TitanMemoryData.get(server).record(id, titan);
        r.firstCleared = true;
        r.choicePending = pending;
        r.owned.removeAll(HeartReward.options(titan));
    }

    private static int held(FakePlayer p, MinecraftServer server, String id) {
        Item item = TitanGearSet.item(id).orElseThrow();
        int n = Pouch.countAll(p, item);
        TitanMemoryData.Pending pending = TitanMemoryData.get(server).pending(p.getUUID());
        for (ItemStack s : pending.items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void clear(FakePlayer p, MinecraftServer server) {
        p.getInventory().clearContent();
        TitanMemoryData.get(server).takePending(p.getUUID());
        HeartReward.clearLastOpened(p.getUUID());
        for (String t : TITANS) {
            TitanMemoryData.get(server).record(p.getUUID(), t).choicePending = false;
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        FakePlayer a = null;
        FakePlayer b = null;
        FakePlayer c = null;
        try {
            ServerLevel level = server.overworld();
            a = fake(level, "0001", "[reward_a]");
            b = fake(level, "0002", "[reward_b]");
            c = fake(level, "0003", "[reward_c]");
            for (FakePlayer p : List.of(a, b, c)) {
                clear(p, server);
            }
            run(server, a, b, c, f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("heart reward selftest crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (FakePlayer p : new FakePlayer[] {a, b, c}) {
                if (p != null) {
                    clear(p, server);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart reward PASSED (options for four titans, claim once, refusals, relog round trip, two players, later then claim, packet codecs, lang)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST heart reward FAILED: {}", x));
        }
    }

    private static void run(MinecraftServer server, FakePlayer a, FakePlayer b, FakePlayer c, List<String> f) {
        // ---- 選べる品: 四体とも、専用伝説だけ、作られた品だけ
        for (String t : TITANS) {
            List<String> options = HeartReward.options(t);
            TitanGearSet set = TitanGearSets.get(t);
            check(f, set != null && !options.isEmpty(), t + " has no choosable relic");
            if (set != null) {
                check(f, options.stream().allMatch(set::isExclusive), t + " offers a non-exclusive item");
                check(f, options.stream().allMatch(TitanGearSet::registered), t + " offers an item that is not registered");
            }
        }
        check(f, HeartReward.options(Titans.STONE_WHALE).size() == 3, "the Stone Whale should offer three relics: " + HeartReward.options(Titans.STONE_WHALE));
        check(f, HeartReward.options("no_such_titan").isEmpty(), "an unknown titan offered relics");

        // ---- 受け取れる状態でないときは開かない・断る
        check(f, !HeartReward.openFirst(a, false), "opened with nothing pending");
        check(f, HeartReward.claim(a, Titans.STONE_WHALE, HeartReward.options(Titans.STONE_WHALE).get(0)) == HeartReward.Result.NOT_PENDING, "claimed without a pending choice");

        for (String t : TITANS) {
            claimOnce(server, a, t, f);
        }

        // ---- 選べない品・持っている品
        String whale = Titans.STONE_WHALE;
        List<String> wo = HeartReward.options(whale);
        setPending(server, a.getUUID(), whale, true);
        check(f, HeartReward.claim(a, whale, "skyabyss:stone_whale_helmet") == HeartReward.Result.BAD_ITEM, "a non-exclusive item was claimable");
        check(f, HeartReward.claim(a, whale, "minecraft:diamond_sword") == HeartReward.Result.BAD_ITEM, "a foreign item was claimable");
        check(f, HeartReward.claim(a, Titans.TIDE_SHELL, wo.get(0)) == HeartReward.Result.BAD_ITEM || HeartReward.claim(a, Titans.TIDE_SHELL, wo.get(0)) == HeartReward.Result.NOT_PENDING,
                "another titan's item was claimable under the wrong titan");
        check(f, TitanMemoryData.get(server).record(a.getUUID(), whale).choicePending, "a refused claim spent the choice");
        TitanMemoryData.get(server).record(a.getUUID(), whale).owned.add(wo.get(0));
        // 記録にあるだけで手もとにない遺物は、持っているとは出さない (持っていない遺物が持っていると出る不具合)
        check(f, HeartReward.ownedMask(a, TitanMemoryData.get(server).record(a.getUUID(), whale), wo) == 0, "a relic only in the record was shown as owned");
        a.getInventory().add(new net.minecraft.world.item.ItemStack(TitanGearSet.item(wo.get(0)).orElseThrow()));
        check(f, HeartReward.ownedMask(a, TitanMemoryData.get(server).record(a.getUUID(), whale), wo) == 1, "a held relic was not shown as owned");
        check(f, HeartReward.claim(a, whale, wo.get(0)) == HeartReward.Result.OWNED, "an owned relic was claimable while others were left");
        check(f, TitanMemoryData.get(server).record(a.getUUID(), whale).choicePending, "an owned-relic refusal spent the choice");
        // 全部持っているときは、持っている品も選べる (選ぶ品がなくならない)
        TitanMemoryData.get(server).record(a.getUUID(), whale).owned.addAll(wo);
        for (String id : wo) {
            a.getInventory().add(new net.minecraft.world.item.ItemStack(TitanGearSet.item(id).orElseThrow()));
        }
        check(f, HeartReward.claim(a, whale, wo.get(1)) == HeartReward.Result.OK, "with every relic owned, the choice was stuck");
        clear(a, server);

        // ---- 入り直し: 記録の往復 (保存の形) と、開き直せること
        for (String t : TITANS) {
            setPending(server, a.getUUID(), t, false);
        }
        setPending(server, a.getUUID(), whale, true);
        TitanMemoryData.Record before = TitanMemoryData.get(server).record(a.getUUID(), whale);
        CompoundTag saved = before.save();
        TitanMemoryData.Record loaded = TitanMemoryData.Record.load(saved);
        check(f, loaded.choicePending && loaded.firstCleared, "a pending choice did not survive the save/load round trip");
        HeartReward.clearLastOpened(a.getUUID());
        check(f, HeartReward.hasPending(a) && HeartReward.pending(server, a.getUUID()).equals(List.of(whale)), "the pending list is wrong after a relog");
        check(f, HeartReward.openFirst(a, false) && whale.equals(HeartReward.lastOpened(a.getUUID())), "the screen could not be reopened after a relog");

        // ---- 二人: それぞれ別に選べる
        clear(a, server);
        for (FakePlayer p : List.of(a, b, c)) {
            setPending(server, p.getUUID(), whale, true);
        }
        check(f, HeartReward.claim(a, whale, wo.get(0)) == HeartReward.Result.OK, "player A could not claim");
        check(f, HeartReward.claim(a, whale, wo.get(1)) == HeartReward.Result.NOT_PENDING, "player A claimed twice");
        check(f, TitanMemoryData.get(server).record(b.getUUID(), whale).choicePending && TitanMemoryData.get(server).record(c.getUUID(), whale).choicePending,
                "A's claim spent B's or C's choice");
        check(f, HeartReward.claim(b, whale, wo.get(2)) == HeartReward.Result.OK, "player B could not claim");
        check(f, HeartReward.claim(c, whale, wo.get(0)) == HeartReward.Result.OK, "player C could not take the same relic A took");
        check(f, held(a, server, wo.get(0)) == 1 && held(b, server, wo.get(2)) == 1 && held(c, server, wo.get(0)) == 1, "each player should hold exactly their own relic");
        check(f, held(a, server, wo.get(2)) == 0 && held(b, server, wo.get(0)) == 0, "a relic went to the wrong player");
        check(f, !HeartReward.hasPending(a) && !HeartReward.hasPending(b) && !HeartReward.hasPending(c), "a choice stayed pending after being claimed");
        for (FakePlayer p : List.of(a, b, c)) {
            clear(p, server);
        }

        // ---- 「あとで」: 記録は変わらず、あとでも一度だけ受け取れる
        setPending(server, a.getUUID(), whale, true);
        HeartReward.onLaterPacket(a, whale);
        check(f, TitanMemoryData.get(server).record(a.getUUID(), whale).choicePending && HeartReward.hasPending(a), "'later' changed the pending choice");
        HeartReward.clearLastOpened(a.getUUID());
        check(f, HeartReward.openFirst(a, false), "a later choice could not be opened from the pouch/record");
        check(f, HeartReward.claim(a, whale, wo.get(1)) == HeartReward.Result.OK && held(a, server, wo.get(1)) == 1, "a later claim did not give the relic");
        check(f, HeartReward.claim(a, whale, wo.get(1)) == HeartReward.Result.NOT_PENDING && held(a, server, wo.get(1)) == 1, "a later claim could be repeated");
        clear(a, server);

        // ---- パケットの往復 (コーデック)
        packets(server, f);

        // ---- 日英の文
        lang(f);

        // ---- 場面の終わりで開く (次の段で開いたかを確かめる)
        sceneP = fake(server.overworld(), "0004", "[reward_scene]");
        clear(sceneP, server);
        setPending(server, sceneP.getUUID(), whale, true);
        Cinematics.state(sceneP).forgetAll();
        Cinematics.stop(sceneP, false);
        check(f, Cinematics.start(sceneP, Scenes.endId(whale), null, sceneP.position().add(0, 0, 100)), "the sleep scene did not start");
        int tasksBefore = Tasks.pending();
        check(f, HeartReward.lastOpened(sceneP.getUUID()) == null, "the screen opened before the scene ended");
        Cinematics.end(sceneP, Scenes.endId(whale), true);
        check(f, Tasks.pending() > tasksBefore, "the end of the scene did not schedule the screen");
        check(f, HeartReward.lastOpened(sceneP.getUUID()) == null, "the screen opened the moment the scene ended (it should wait for the end card)");
    }

    private static void claimOnce(MinecraftServer server, FakePlayer a, String titan, List<String> f) {
        List<String> options = HeartReward.options(titan);
        if (options.isEmpty()) {
            return;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(server).record(a.getUUID(), titan);
        r.owned.removeAll(options);
        setPending(server, a.getUUID(), titan, true);
        check(f, HeartReward.hasPending(a), titan + ": a pending choice is not reported");
        check(f, HeartReward.open(a, titan, true) && titan.equals(HeartReward.lastOpened(a.getUUID())), titan + ": the screen was not opened");
        String pick = options.get(options.size() - 1);
        check(f, HeartReward.claim(a, titan, pick) == HeartReward.Result.OK, titan + ": the first claim failed");
        check(f, held(a, server, pick) == 1, titan + ": the relic was not handed over exactly once");
        check(f, TitanMemoryData.get(server).record(a.getUUID(), titan).owned.contains(pick), titan + ": the relic was not recorded");
        check(f, HeartReward.claim(a, titan, pick) == HeartReward.Result.NOT_PENDING, titan + ": a second claim was allowed");
        check(f, HeartReward.claim(a, titan, options.get(0)) == HeartReward.Result.NOT_PENDING, titan + ": a second claim of another relic was allowed");
        check(f, held(a, server, pick) == 1, titan + ": the relic was duplicated");
        check(f, !HeartReward.open(a, titan, true), titan + ": the screen opened with nothing pending");
        check(f, !HeartReward.hasPending(a), titan + ": still reported as pending");
    }

    private static void packets(MinecraftServer server, List<String> f) {
        var buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        var open = new HeartRewardNet.Open("stone_whale", List.of("skyabyss:captain_pike", "skyabyss:waterway_fang"), 2, true);
        HeartRewardNet.Open.STREAM_CODEC.encode(buf, open);
        check(f, HeartRewardNet.Open.STREAM_CODEC.decode(buf).equals(open), "Open did not round trip");
        var claim = new HeartRewardNet.Claim("ember", "skyabyss:x");
        HeartRewardNet.Claim.STREAM_CODEC.encode(buf, claim);
        check(f, HeartRewardNet.Claim.STREAM_CODEC.decode(buf).equals(claim), "Claim did not round trip");
        var result = new HeartRewardNet.Result(3, "tide_shell", "skyabyss:y");
        HeartRewardNet.Result.STREAM_CODEC.encode(buf, result);
        check(f, HeartRewardNet.Result.STREAM_CODEC.decode(buf).equals(result), "Result did not round trip");
        HeartRewardNet.State.STREAM_CODEC.encode(buf, new HeartRewardNet.State(true));
        check(f, HeartRewardNet.State.STREAM_CODEC.decode(buf).pending(), "State did not round trip");
        HeartRewardNet.Request.STREAM_CODEC.encode(buf, new HeartRewardNet.Request());
        check(f, HeartRewardNet.Request.STREAM_CODEC.decode(buf) != null, "Request did not round trip");
        HeartRewardNet.Later.STREAM_CODEC.encode(buf, new HeartRewardNet.Later("ember"));
        check(f, HeartRewardNet.Later.STREAM_CODEC.decode(buf).titan().equals("ember"), "Later did not round trip");
        check(f, buf.readableBytes() == 0, "packet codecs left bytes unread");
    }

    private static void lang(List<String> f) {
        List<String> keys = new ArrayList<>(List.of("sub", "title").stream().flatMap(p -> List.of(TITANS).stream().map(t -> "skyabyss_reward." + p + "." + t)).toList());
        keys.addAll(List.of("keys.choose", "keys.dialog", "tag.owned", "kind.weapon", "kind.armor", "kind.charm", "kind.trinket", "req", "req_now", "equipped", "equipped.none",
                "stat.attack_damage", "stat.attack_speed", "stat.armor", "stat.toughness", "stat.reach", "confirm.title", "confirm.body", "confirm.yes", "confirm.no", "later.title",
                "later.body", "later.yes", "later.no", "done", "done_affix", "err.not_pending", "err.owned", "err.bad", "announce", "later_made", "waiting", "none", "later",
                "pouch.button", "pouch.tip").stream().map(k -> k.startsWith("skyabyss_reward.") ? k : "skyabyss_reward." + k).toList());
        for (String lang : List.of("ja_jp", "en_us")) {
            java.util.Map<?, ?> texts;
            try (var in = HeartRewardSelfTest.class.getResourceAsStream("/assets/skyabyss_reward/lang/" + lang + ".json")) {
                texts = in == null ? null : new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), java.util.Map.class);
            } catch (java.io.IOException e) {
                texts = null;
            }
            for (String key : keys) {
                check(f, texts != null && texts.containsKey(key), lang + " is missing " + key);
            }
        }
    }

    // 場面が終わって 50 tick あと: 画面を開かせた (偽のプレイヤーにはパケットを送らず、記録だけ)
    public static void checkSceneOpen(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            if (sceneP == null) {
                f.add("no scene player");
            } else {
                check(f, Titans.STONE_WHALE.equals(HeartReward.lastOpened(sceneP.getUUID())), "the screen did not open after the sleep scene ended (skip counts too)");
                // 開いたあとも、選ぶまで残っている
                check(f, HeartReward.hasPending(sceneP), "the choice vanished when the screen opened");
                clear(sceneP, server);
            }
        } catch (RuntimeException e) {
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart reward (scene open) PASSED");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST heart reward (scene open) FAILED: {}", x));
        }
    }
}
