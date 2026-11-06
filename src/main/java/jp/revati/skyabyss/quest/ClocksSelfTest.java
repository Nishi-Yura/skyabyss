package jp.revati.skyabyss.quest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 今日・今週 (quest/Clocks。2026-11-05 の ct_clock)。SKYABYSS_SELFTEST_ONLY="clocks"
//   一覧の行がそろう (11。早駆けの行は 2026-11-06 に消した)・名前・様子・受ける所の文が日英にある・板の日替わりの数が数えられる・通信の往復
public final class ClocksSelfTest {
    static final List<String> IDS = List.of("board_day", "board_week", "circuit", "tower", "dungeon", "fish_order", "fish_week",
            "guild_week", "world_event", "camp_prizes", "chests");

    private ClocksSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "clock_t"));
            List<Clocks.Row> rows = Clocks.rows(p);
            List<String> ids = rows.stream().map(Clocks.Row::id).toList();
            check(f, ids.equals(IDS), "clock rows: " + ids);
            Clocks.Row board = rows.get(0);
            check(f, board.args().equals(List.of("0", String.valueOf(jp.revati.skyabyss.quest.free.Board.QUOTA))) && board.reset() == Clocks.DAY,
                    "the daily board row should read 0 / quota and reset daily: " + board);
            check(f, rows.stream().filter(r -> r.reset() == Clocks.WEEK).count() >= 3, "weekly rows are missing");
            for (String lang : new String[] {"ja_jp", "en_us"}) {
                try (InputStream in = ClocksSelfTest.class.getResourceAsStream("/assets/skyabyss_quest/lang/" + lang + ".json")) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Clocks.Row r : rows) {
                        check(f, o.has("quest.skyabyss.clock.name." + r.id()), lang + " lacks the name of " + r.id());
                        check(f, o.has(r.statusKey()), lang + " lacks " + r.statusKey());
                        check(f, o.has(r.where()), lang + " lacks " + r.where());
                    }
                    for (String k : new String[] {"title", "row", "lead", "where", "reset.day", "reset.week", "left.days", "left.hours", "left.minutes",
                            "tower.used", "tower.free", "fish_week.status", "world_event.now", "world_event.next", "world_event.idle"}) {
                        check(f, o.has("quest.skyabyss.clock." + k), lang + " lacks quest.skyabyss.clock." + k);
                    }
                }
            }
            // 通信の往復
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            Clocks.ListPayload.STREAM_CODEC.encode(buf, new Clocks.ListPayload(rows));
            Clocks.ListPayload back = Clocks.ListPayload.STREAM_CODEC.decode(buf);
            check(f, back.rows().equals(rows), "the clock list did not survive the round trip");
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("SELFTEST clocks crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST clocks PASSED ({} rows, ja/en names, statuses and places, daily board count, round trip)", IDS.size());
        } else {
            SkyAbyss.LOGGER.error("SELFTEST clocks FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
