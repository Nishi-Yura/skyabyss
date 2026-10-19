package jp.revati.skyabyss.rebirth;

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

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyNetwork;
import jp.revati.skyabyss.record.RecordSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

// 転生の見せ方の自己テスト: 色の段・記号の計算、称号の解除、順位表の並び、同期 (データ・パケット・記録画面・パーティ一覧の符号化の往復)、
// 記録 (人生の最速・最少の倒れ・巨神の最速)、名前の飾り、墓の飾り、日英の文の鍵
public final class RebirthSelfTest {
    private RebirthSelfTest() {
    }

    // 墓の飾りの台のまわりの区画を先に読み込んでおく (文字の表示は動いている区画にしか足せず、区画が動くのは次の tick から。本体の段の前の別の段)
    private static BlockPos graveBase(MinecraftServer server) {
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 90, 150, spawn.getZ() + 90);
    }

    private static void force(MinecraftServer server, boolean on) {
        BlockPos c = graveBase(server);
        for (int cx = (c.getX() - 8) >> 4; cx <= (c.getX() + 8) >> 4; cx++) {
            for (int cz = (c.getZ() - 8) >> 4; cz <= (c.getZ() + 8) >> 4; cz++) {
                server.overworld().setChunkForced(cx, cz, on);
            }
        }
    }

    public static void load(MinecraftServer server) {
        force(server, true);
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            tiers(f);
            titles(f);
            leaderboard(f);
            sync(server, f);
            flow(server, f);
            graves(server, f);
            lang(f);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("rebirth selftest crashed", e);
            f.add("crashed: " + e);
        }
        force(server, false);
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST rebirth PASSED (tier math, symbols, titles, leaderboard order, sync round trips, life records, names, grave flair, ja/en)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST rebirth FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer player(MinecraftServer server, String name) {
        return new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), name));
    }

    // ---------------------------------------------------------------- 色の段・記号
    private static void tiers(List<String> f) {
        int[] counts = {0, 1, 4, 5, 14, 15, 20, 25, -3};
        RebirthTier.Tier[] want = {RebirthTier.Tier.WHITE, RebirthTier.Tier.TEAL, RebirthTier.Tier.TEAL, RebirthTier.Tier.GOLD, RebirthTier.Tier.GOLD,
                RebirthTier.Tier.PRISM, RebirthTier.Tier.PRISM, RebirthTier.Tier.PRISM, RebirthTier.Tier.WHITE};
        for (int i = 0; i < counts.length; i++) {
            check(f, RebirthTier.of(counts[i]) == want[i], "tier of " + counts[i] + " = " + RebirthTier.of(counts[i]));
        }
        check(f, RebirthTier.clamp(99) == 20 && RebirthTier.clamp(-1) == 0, "cap is 20");
        String[][] sym = {{"0", ""}, {"1", "✦"}, {"4", "✦✦✦✦"}, {"5", "✧"}, {"6", "✧✦"}, {"9", "✧✦✦✦✦"}, {"10", "✺"}, {"14", "✺✦✦✦✦"}, {"15", "✺✧"}, {"17", "✺✧✦✦"}, {"20", "✺✺"}, {"30", "✺✺"}};
        for (String[] s : sym) {
            check(f, RebirthTier.symbols(Integer.parseInt(s[0])).equals(s[1]), "symbols(" + s[0] + ") = " + RebirthTier.symbols(Integer.parseInt(s[0])) + " want " + s[1]);
        }
        check(f, RebirthTier.chatPrefix(0).isEmpty() && RebirthTier.chatPrefix(3).equals("✦3") && RebirthTier.chatPrefix(40).equals("✦20"), "chat prefix");
        check(f, RebirthTier.levelLabel(50, 0).equals("Lv 50") && RebirthTier.levelLabel(50, 6).equals("Lv 50+6") && RebirthTier.levelLabel(12, 0).equals("Lv 12"), "level label");
        check(f, RebirthTier.color(0, 0, false) == RebirthTier.WHITE_RGB && RebirthTier.color(2, 0, false) == RebirthTier.TEAL_TEXT && RebirthTier.color(7, 0, false) == RebirthTier.GOLD_TEXT,
                "tier colours");
        // プリズム: 巡る (時刻で変わる)、止めるときは時刻に依らない、一周で戻る
        check(f, RebirthTier.color(15, 0, false) != RebirthTier.color(15, 1500, false), "prism cycles over time");
        check(f, RebirthTier.color(15, 0, true) == RebirthTier.color(15, 1500, true), "prism is still when asked");
        check(f, RebirthTier.prism(0, 0f) == RebirthTier.prism(RebirthTier.PRISM_PERIOD_MS, 0f), "prism loops every period");
        check(f, RebirthTier.prism(0, 0f) == 0xFF7AA8, "prism starts at the first stop");
        // 色だけに頼らない: 段ごとに縁の形が違う
        Set<Integer> frames = new HashSet<>();
        for (int c : new int[] {0, 1, 5, 15}) {
            frames.add(RebirthTier.frameStyle(c));
        }
        check(f, frames.size() == 4, "frame style differs per tier");
        // 文: 接頭辞は元の名前を壊さない (前に足すだけ)
        Component other = Component.literal("[Guild] Bob");
        check(f, RebirthNames.prefixed(3, other).getString().equals("✦3 [Guild] Bob"), "chat prefix keeps the other name intact: " + RebirthNames.prefixed(3, other).getString());
        check(f, RebirthNames.prefixed(0, other).getString().equals("[Guild] Bob"), "no prefix at 0");
        check(f, RebirthNames.tabName(2, other).getString().equals("✦✦ [Guild] Bob"), "tab name: " + RebirthNames.tabName(2, other).getString());
        TextColor tc = RebirthText.chatPrefix(3).getSiblings().isEmpty() ? RebirthText.chatPrefix(3).getStyle().getColor() : null;
        check(f, tc != null && tc.getValue() == RebirthTier.TEAL_RGB, "teal prefix colour " + tc);
        check(f, RebirthText.chatPrefix(17).getString().equals("✦17 "), "prism prefix text");
    }

    // ---------------------------------------------------------------- 称号
    private static void titles(List<String> f) {
        List<RebirthTitles.Title> all = RebirthTitles.all();
        check(f, all.size() == 24 && all.size() <= RebirthTitles.MAX, "24 titles at first: " + all.size());
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            check(f, all.get(i).index() == i, "title index order at " + i);
            ids.add(all.get(i).id());
        }
        check(f, ids.size() == all.size(), "title ids are unique");
        check(f, RebirthTitles.unlockedMask(RebirthTitles.Facts.NONE) == 0L, "a new player has no titles");
        long m3 = RebirthTitles.unlockedMask(new RebirthTitles.Facts(3, 0, 0, 0, -1, -1, 0));
        check(f, RebirthTitles.has(m3, "reborn_1") && RebirthTitles.has(m3, "reborn_3") && !RebirthTitles.has(m3, "reborn_5"), "rebirth count titles");
        long flawless = RebirthTitles.unlockedMask(new RebirthTitles.Facts(5, 3, 0, 0, 50_000, 0, 0));
        check(f, RebirthTitles.has(flawless, "flawless_1") && RebirthTitles.has(flawless, "flawless_3") && !RebirthTitles.has(flawless, "flawless_5")
                && RebirthTitles.has(flawless, "almost_flawless") && RebirthTitles.has(flawless, "swift_1h") && !RebirthTitles.has(flawless, "swift_30m"), "feat titles");
        long full = RebirthTitles.unlockedMask(new RebirthTitles.Facts(20, 9, 4, 60, 10_000, 0, 4));
        check(f, Long.bitCount(full) == 24, "every title can be unlocked: " + Long.bitCount(full));
        long some = RebirthTitles.unlockedMask(new RebirthTitles.Facts(2, 0, 4, 12, -1, -1, 1));
        check(f, RebirthTitles.has(some, "titan_4") && RebirthTitles.has(some, "all_backs") && !RebirthTitles.has(some, "gold_backs") && RebirthTitles.has(some, "fallen_10")
                && !RebirthTitles.has(some, "fallen_50") && RebirthTitles.has(some, "pulse_counted") && !RebirthTitles.has(some, "pulse_all"), "progress titles");
        // 解除は足すだけ: 事実が減っても (例: 転生の数が仮の元で戻っても) 前の印は Rebirths.updateTitles が OR で残す
        check(f, (m3 | RebirthTitles.unlockedMask(RebirthTitles.Facts.NONE)) == m3, "unlock mask is additive");
    }

    // ---------------------------------------------------------------- 順位表の並び
    private static RebirthLeaderboard.Row row(String name, int rebirths, long fastest, int fewest, int deaths, long order) {
        return new RebirthLeaderboard.Row(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, rebirths, fastest, fewest, deaths, order);
    }

    private static List<String> names(List<RebirthLeaderboard.Row> rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(r -> out.add(r.name()));
        return out;
    }

    private static void leaderboard(List<String> f) {
        List<RebirthLeaderboard.Row> rows = List.of(
                row("cara", 5, 90_000, 2, 7, 3), row("abe", 5, 70_000, 0, 7, 1), row("bob", 5, 70_000, 0, 7, 2), row("dina", 9, 200_000, 4, 30, 4),
                row("eli", 2, 40_000, 0, 1, 5), row("zero", 0, -1, -1, 3, Long.MAX_VALUE), row("fay", 5, 70_000, 0, 2, 6));
        List<String> byCount = names(RebirthLeaderboard.top(rows, RebirthLeaderboard.Metric.REBIRTHS, 10));
        // 9 → 5 回の中では死が少ない fay (2) → 死が同じなら先に着いた abe, bob, cara
        check(f, byCount.equals(List.of("dina", "fay", "abe", "bob", "cara", "eli")), "rebirth order " + byCount);
        List<String> fastest = names(RebirthLeaderboard.top(rows, RebirthLeaderboard.Metric.FASTEST, 10));
        check(f, fastest.equals(List.of("eli", "abe", "bob", "fay", "cara", "dina")), "fastest order (ties by name) " + fastest);
        List<String> fewest = names(RebirthLeaderboard.top(rows, RebirthLeaderboard.Metric.FEWEST_DEATHS, 10));
        check(f, fewest.equals(List.of("eli", "abe", "bob", "fay", "cara", "dina")), "fewest deaths order " + fewest);
        check(f, !fastest.contains("zero") && !byCount.contains("zero") && !fewest.contains("zero"), "players without a rebirth are not ranked");
        check(f, RebirthLeaderboard.top(rows, RebirthLeaderboard.Metric.REBIRTHS, 3).size() == 3, "limit applies");
        check(f, RebirthLeaderboard.time(72_000).equals("1:00:00") && RebirthLeaderboard.time(1_300).equals("1:05"), "time text " + RebirthLeaderboard.time(1_300));
    }

    // ---------------------------------------------------------------- 同期 (符号化の往復)
    private static RegistryFriendlyByteBuf buf(MinecraftServer server) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
    }

    private static void sync(MinecraftServer server, List<String> f) {
        RebirthData d = new RebirthData(13, "flawless_1", 0b1011L, 2, true);
        RegistryFriendlyByteBuf b = buf(server);
        RebirthData.STREAM_CODEC.encode(b, d);
        check(f, RebirthData.STREAM_CODEC.decode(b).equals(d), "RebirthData stream codec round trip");
        check(f, RebirthData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, d).result().flatMap(t -> RebirthData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, t).result())
                .map(d::equals).orElse(false), "RebirthData save round trip");
        check(f, new RebirthData(99, null, 0, 0, false).count() == 20, "count is clamped on load");
        // 順位の石・飾り板のパケット
        var board = new RebirthNet.BoardPayload(0, true, List.of(List.of(new RebirthNet.Line("abe", 5, 5), new RebirthNet.Line("bob", 3, 3)), List.of(), List.of(new RebirthNet.Line("cara", 1, 2))),
                List.of());
        RegistryFriendlyByteBuf b2 = buf(server);
        RebirthNet.BoardPayload.STREAM_CODEC.encode(b2, board);
        var back = RebirthNet.BoardPayload.STREAM_CODEC.decode(b2);
        check(f, back.hidden() && back.lists().size() == 3 && back.lists().get(0).get(1).name().equals("bob") && back.lists().get(2).get(0).value() == 2, "leaderboard payload round trip");
        var rec = new RebirthNet.BoardPayload(1, false, List.of(), List.of(new RebirthNet.RecordLine(Component.literal("T"), "abe", 1234, true, true),
                new RebirthNet.RecordLine(Component.literal("U"), "", -1, false, false)));
        RegistryFriendlyByteBuf b3 = buf(server);
        RebirthNet.BoardPayload.STREAM_CODEC.encode(b3, rec);
        var rb = RebirthNet.BoardPayload.STREAM_CODEC.decode(b3);
        check(f, rb.records().size() == 2 && rb.records().get(0).ticks() == 1234 && rb.records().get(0).fresh() && rb.records().get(1).ticks() < 0, "record plaque payload round trip");
        // パーティ一覧: 転生の回数が往復で残る
        var member = new PartyNetwork.Member("id", "abe", 50, 20f, 20f, true, true, false, 12);
        var sync = new PartyNetwork.Sync(1, 20, 1, "stone_whale", 50, List.of(member), "");
        RegistryFriendlyByteBuf b4 = buf(server);
        PartyNetwork.Sync.STREAM_CODEC.encode(b4, sync);
        check(f, PartyNetwork.Sync.STREAM_CODEC.decode(b4).members().get(0).rebirths() == 12, "party list carries the rebirth count");
        // 記録画面: 転生の欄の行から回数が戻る (名前の枠の色に使う)
        FakePlayer p = player(server, "sync_p");
        Rebirths.setCount(p, 7);
        List<RecordSnapshot.Row> rows = RebirthRecord.rows(p);
        var snap = new RecordSnapshot(p.getUUID(), Component.literal("sync_p"), true, List.of(new RecordSnapshot.Tab(RecordSnapshot.GROWTH, rows)));
        RegistryFriendlyByteBuf b5 = buf(server);
        RecordSnapshot.STREAM_CODEC.encode(b5, snap);
        check(f, RebirthRecord.countOf(RecordSnapshot.STREAM_CODEC.decode(b5)) == 7, "record screen frame reads the count through the snapshot");
        check(f, RebirthRecord.countOf(new RecordSnapshot(p.getUUID(), Component.empty(), true, List.of())) == 0, "no growth tab means no frame");
        // 読み出し口: 元を替えられる (本体の API へ切り替える点)
        check(f, RebirthView.isStub() && RebirthView.count(p) == 7, "stub view reads the synced attachment");
        RebirthView.useSource(pl -> 3);
        check(f, !RebirthView.isStub() && RebirthView.count(p) == 3, "view source can be swapped");
        RebirthView.useSource(null);
        check(f, RebirthView.isStub() && RebirthView.count(p) == 7, "view source resets to the stub");
    }

    // ---------------------------------------------------------------- 記録・称号・名前の流れ
    private static void flow(MinecraftServer server, List<String> f) {
        FakePlayer p = player(server, "flow_p");
        RebirthStats stats = RebirthStats.get(server);
        Rebirths.setCount(p, 0);
        check(f, RebirthView.count(p) == 0 && !p.getDisplayName().getString().startsWith("✦"), "no prefix before the first rebirth");
        Rebirths.setCount(p, 3);
        check(f, RebirthView.count(p) == 3 && stats.peek(p.getUUID()).rebirths == 3, "count is written to the attachment and the board record");
        check(f, p.getDisplayName().getString().startsWith("✦3 "), "chat name carries the prefix: " + p.getDisplayName().getString());
        RebirthData d = RebirthView.data(p);
        check(f, RebirthTitles.has(d.unlocked(), "reborn_1") && RebirthTitles.has(d.unlocked(), "reborn_3") && !RebirthTitles.has(d.unlocked(), "reborn_5"), "titles unlock with the count");
        check(f, !Rebirths.selectTitle(p, "reborn_5"), "a locked title cannot be selected");
        check(f, !Rebirths.selectTitle(p, "no_such_title"), "an unknown title cannot be selected");
        check(f, Rebirths.selectTitle(p, "reborn_3") && RebirthView.data(p).title().equals("reborn_3"), "an unlocked title can be selected");
        check(f, Rebirths.selectTitle(p, "") && RebirthView.data(p).title().isEmpty(), "the title can be taken off");
        Rebirths.setCount(p, 99);
        check(f, RebirthView.count(p) == 20, "count caps at 20");
        // 倒れの数と人生の記録
        Rebirths.setCount(p, 0);
        RebirthStats.Entry e = stats.entry(p.getUUID());
        e.deathsLife = 0;
        long play = Rebirths.playTicks(p);
        e.lifeStartPlay = play - 5_000;
        Rebirths.onRebirth(p);
        check(f, RebirthView.count(p) == 1 && e.rebirths == 1 && e.fastestLife == 5_000 && e.fewestDeaths == 0 && e.flawless == 1 && RebirthView.data(p).flawless() == 1,
                "a flawless rebirth records life length, deaths and flawless count");
        check(f, RebirthTitles.has(RebirthView.data(p).unlocked(), "flawless_1") && RebirthTitles.has(RebirthView.data(p).unlocked(), "almost_flawless"), "flawless titles unlock on rebirth");
        Rebirths.onDeath(new LivingDeathEvent(p, p.damageSources().generic()));
        Rebirths.onDeath(new LivingDeathEvent(p, p.damageSources().generic()));
        check(f, e.deathsLife == 2 && e.deathsTotal == 2, "falls are counted for the current life and in total");
        e.lifeStartPlay = play - 9_000;
        Rebirths.onRebirth(p);
        check(f, RebirthView.count(p) == 2 && e.fastestLife == 5_000 && e.fewestDeaths == 0 && e.flawless == 1 && e.deathsLife == 0, "a worse life does not replace the bests and resets the life counter");
        e.lifeStartPlay = play - 4_000;
        Rebirths.onRebirth(p);
        check(f, e.fastestLife == 4_000, "a faster life replaces the fastest");
        // 順位表: 載らない設定は元の表から消える
        UUID id = p.getUUID();
        check(f, stats.rows().stream().anyMatch(r -> r.id().equals(id)), "listed by default");
        Rebirths.setHidden(p, true);
        check(f, stats.rows().stream().noneMatch(r -> r.id().equals(id)) && RebirthView.data(p).hidden(), "opt-out removes the player from the boards");
        Rebirths.setHidden(p, false);
        check(f, stats.rows().stream().anyMatch(r -> r.id().equals(id)), "opt back in");
        // 巨神の最速: 入りの時刻 → 心臓を止める
        long now = server.overworld().getGameTime();
        e.entered.put("stone_whale", now - 3_000);
        Rebirths.onTitanStopped(server, List.of(id), "stone_whale");
        RebirthStats.Record r = stats.record("stone_whale");
        check(f, r != null && r.ticks() >= 3_000 && r.who().equals(id) && e.titanBest.containsKey("stone_whale") && e.entered.isEmpty(), "heart stop records the fastest time and clears the entry");
        check(f, RebirthBoards.isFresh(server, "stone_whale") && RebirthBoards.fresh(100L, 99L) && !RebirthBoards.fresh(100_000L, 0L), "a new record is fresh, an old one is not");
        long keep = r.ticks();
        e.entered.put("stone_whale", now - 9_000);
        Rebirths.onTitanStopped(server, List.of(id), "stone_whale");
        check(f, stats.record("stone_whale").ticks() == keep, "a slower clear does not replace the record");
        Rebirths.updateTitles(p);
        check(f, RebirthTitles.has(RebirthView.data(p).unlocked(), "pulse_counted"), "pulse title unlocks with a record");
        check(f, !stats.offerRecord("stone_whale", UUID.randomUUID(), "x", keep, now) && stats.offerRecord("stone_whale", UUID.randomUUID(), "x", keep - 1, now), "records need to be strictly faster");
        check(f, RebirthBoards.recordLines(server, "stone_whale").size() == 4 && RebirthBoards.recordLines(server, "stone_whale").get(0).here(), "plaque lists the four titans and marks this one");
        // 保存の往復
        net.minecraft.nbt.CompoundTag saved = stats.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
        check(f, saved.getList("entries", 10).size() >= 1 && saved.getList("records", 10).size() >= 1, "stats save to NBT");
        Rebirths.refreshNames(p);
    }

    // ---------------------------------------------------------------- 墓の飾り
    private static void graves(MinecraftServer server, List<String> f) {
        ServerLevel level = server.overworld();
        FakePlayer p = player(server, "grave_p");
        BlockPos base = graveBase(server);
        level.getChunk(base);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlock(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int y = 0; y < 4; y++) {
                    level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        Rebirths.setCount(p, 0);
        RebirthFx.graveCreated(p, level, base);
        check(f, RebirthFx.labels(level, base).isEmpty(), "no flair for a player who has not reborn");
        Rebirths.setCount(p, 7);
        RebirthFx.graveCreated(p, level, base);
        List<Display.TextDisplay> labels = RebirthFx.labels(level, base);
        check(f, labels.size() == 1, "one label floats over the grave: " + labels.size());
        if (!labels.isEmpty()) {
            String json = labels.get(0).saveWithoutId(new net.minecraft.nbt.CompoundTag()).getString("text");
            check(f, json.contains("✧") && json.contains("✦") && json.contains("Lv 50+7"), "label shows the bundled symbols and the level: " + json);
        }
        int flowers = 0;
        for (BlockPos q : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 0, 1))) {
            if (level.getBlockState(q).is(Blocks.DANDELION)) {
                flowers++;
            }
        }
        check(f, flowers == 3, "gold tier plants 3 dandelions: " + flowers);
        RebirthFx.graveRemoved(level, base);
        check(f, RebirthFx.labels(level, base).isEmpty(), "label is removed with the grave");
    }

    // ---------------------------------------------------------------- 日英の文 (動的に作る鍵)
    private static void lang(List<String> f) throws Exception {
        for (String lang : new String[] {"ja_jp", "en_us"}) {
            try (InputStream in = RebirthSelfTest.class.getResourceAsStream("/assets/skyabyss_rebirth/lang/" + lang + ".json")) {
                if (in == null) {
                    f.add("missing lang file " + lang);
                    continue;
                }
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (RebirthTitles.Title t : RebirthTitles.all()) {
                    check(f, o.has("title.skyabyss_rebirth." + t.id()) && o.has("title.skyabyss_rebirth." + t.id() + ".how"), lang + " lacks title text for " + t.id());
                }
                for (int i = 0; i < 3; i++) {
                    check(f, o.has("skyabyss_rebirth.board.metric." + i), lang + " lacks board metric " + i);
                }
                for (String key : new String[] {"toggle.nametag_effects", "toggle.nametag_title", "toggle.back_self", "toggle.back_others", "toggle.burst", "toggle.prism_still", "toggle.board"}) {
                    check(f, o.has("skyabyss_rebirth.gui." + key), lang + " lacks gui." + key);
                }
                for (String key : new String[] {"header", "label", "none", "none_hint", "count", "fastest", "fewest", "flawless", "deaths", "titan_best", "title"}) {
                    check(f, o.has("skyabyss_rebirth.record." + key), lang + " lacks record." + key);
                }
                for (String id : new String[] {"rebirth_1", "rebirth_5", "rebirth_10", "rebirth_20", "rebirth_flawless"}) {
                    check(f, o.has("advancement.skyabyss." + id + ".title") && o.has("advancement.skyabyss." + id + ".description"), lang + " lacks advancement " + id);
                }
            }
        }
    }
}
