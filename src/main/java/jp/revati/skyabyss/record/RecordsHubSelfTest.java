package jp.revati.skyabyss.record;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 記録の画面 (record/RecordsHub。2026-11-05 の ct_records)。SKYABYSS_SELFTEST_ONLY="records hub"
//   7 つの頁に行がある・チャットへ書かずに集める (塔・釣り・早駆け)・つまみの文が日英にある・通信の往復
public final class RecordsHubSelfTest {
    private RecordsHubSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "hub_t"));
            for (int t = 0; t < RecordsHub.TABS.length; t++) {
                List<Component> lines = RecordsHub.lines(p, t);
                check(f, !lines.isEmpty(), "the " + RecordsHub.TABS[t] + " page is empty");
                check(f, !RecordsHub.capturing(), "capturing did not stop after the " + RecordsHub.TABS[t] + " page");
            }
            check(f, RecordsHub.lines(p, RecordsHub.CLEAR).size() >= 4 + 2, "the clears page should list the four titans and the fastest table");
            for (String lang : new String[] {"ja_jp", "en_us"}) {
                try (InputStream in = RecordsHubSelfTest.class.getResourceAsStream("/assets/skyabyss_fac/lang/" + lang + ".json")) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String tab : RecordsHub.TABS) {
                        check(f, o.has("gui.skyabyss.records_hub.tab." + tab), lang + " lacks the tab " + tab);
                    }
                    check(f, o.has("gui.skyabyss.records_hub.title") && o.has("gui.skyabyss.records_hub.open"), lang + " lacks the title or the open button");
                }
            }
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
            List<Component> lines = RecordsHub.lines(p, RecordsHub.FISHING);
            RecordsHub.View.STREAM_CODEC.encode(buf, new RecordsHub.View(RecordsHub.FISHING, true, lines));
            RecordsHub.View back = RecordsHub.View.STREAM_CODEC.decode(buf);
            check(f, back.tab() == RecordsHub.FISHING && back.hidden() && back.lines().size() == lines.size(), "the records view did not survive the round trip");
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("SELFTEST records hub crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST records hub PASSED ({} pages with lines, chat boards captured, ja/en tabs, round trip)", RecordsHub.TABS.length);
        } else {
            SkyAbyss.LOGGER.error("SELFTEST records hub FAILED: {}", f);
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }
}
