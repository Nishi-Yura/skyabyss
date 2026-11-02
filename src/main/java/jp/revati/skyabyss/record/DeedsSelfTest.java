package jp.revati.skyabyss.record;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 控え帳の「記し」(DeedPages) の自己テスト。SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="deeds" ./gradlew runServer。
//   表      deeds.json のどの id も、絵と題のある進捗。前の開幕の進捗 (野営の跡など) は残っていない
//   つまみ  控え帳に「記し」の一覧と 5 つの分けがあり、行の数が表と同じ
//   合図    島・都・町・名のある強敵で流す合図 (Beats) のどれにも、聞く記しがある。隠しの記しは「？？？」
public final class DeedsSelfTest {
    private DeedsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("deeds", 20, DeedsSelfTest::run));
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes("deeds test".getBytes(StandardCharsets.UTF_8)), "[deeds]"));
        try {
            int ids = 0;
            for (var en : DeedPages.pages().entrySet()) {
                for (String id : en.getValue()) {
                    ids++;
                    var h = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id));
                    check(h != null && h.value().display().isPresent(), "deed " + id + " has no advancement with a display", f);
                }
            }
            check(ids >= 60, "only " + ids + " deeds listed", f);
            for (String old : new String[] {"camp", "bell_lodge", "storyteller", "isana", "footprint", "sky_shadow", "bone_hill"}) {
                check(server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, old)) == null, "retired advancement " + old + " still loads", f);
            }
            RecordSnapshot before = RecordBuilder.build(p, true);
            check(RecordSnapshot.TAB_IDS.contains(RecordSnapshot.DEEDS) && before.tab(DeedPages.TAB) != null, "the record has no deeds tab", f);
            for (String g : DeedPages.GROUPS) {
                RecordSnapshot.Tab t = before.tab(DeedPages.TAB + RecordSnapshot.SUB + g);
                check(t != null && t.rows().size() == DeedPages.pages().get(g).size(), "deeds page " + g + " rows " + (t == null ? "missing" : t.rows().size()), f);
            }
            RecordSnapshot.Row job = before.tab(DeedPages.TAB + ".journey").row("island_job");
            check(job != null && job.mark() != RecordSnapshot.MARK_SAFE, "island_job should not be done yet: " + job, f);
            RecordSnapshot.Row secret = before.tab(DeedPages.TAB + ".foes").row("named_whale");
            check(secret != null && secret.mark() == RecordSnapshot.MARK_LOCKED && secret.icon().isEmpty(), "a hidden deed is not shown as ???: " + secret, f);
            // 偽の人 (FakePlayer) の進捗は記録されないので、流す名前と記しの条件が合っているかを見る
            java.util.Set<String> events = new java.util.HashSet<>();
            for (var h : server.getAdvancements().getAllAdvancements()) {
                for (var c : h.value().criteria().values()) {
                    if (c.triggerInstance() instanceof jp.revati.skyabyss.advancement.BeatTrigger.Instance bi) {
                        events.add(bi.event());
                    }
                }
            }
            for (String e : new String[] {"job:chosen", "island:prints", "island:bandits", "island:ferry", "town:tour", "town:visit:whale", "town:visit:tide",
                "town:visit:storm", "town:visit:ember", "named:any", "named:island", "named:meadow", "named:whale", "named:tide", "named:storm", "named:ember"}) {
                check(events.contains(e), "no deed listens for " + e, f);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST deeds crashed", e);
            f.add("crashed: " + e);
        } finally {
            p.discard();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST deeds PASSED (deeds table resolves, retired opening deeds gone, deeds tab with 5 pages, every hooked beat has a deed, hidden deeds show ???)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST deeds FAILED: {}", m));
        }
    }
}
