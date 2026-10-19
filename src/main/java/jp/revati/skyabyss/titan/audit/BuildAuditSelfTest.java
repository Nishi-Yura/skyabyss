package jp.revati.skyabyss.titan.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.opening.site.SiteAudit;
import jp.revati.skyabyss.opening.site.SiteBuilds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.titan.BackBuildJobs;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.audit.BuildAudit.Allow;
import jp.revati.skyabyss.titan.audit.BuildAudit.Report;
import jp.revati.skyabyss.titan.dungeon.TitanBackBuilder;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBodyBuilder;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackBuilder;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.TideBackBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

// 建築の監査 (自己テスト "build audit")。新しいワールドで、建てられるものを全部建てて、BuildAudit の決まりで調べる (docs/design/08 の「建築の監査」)。
//  (convention) 階段の facing = 登る向き、の約束が今の Minecraft でも成り立つ
//  (blueprints) 地上の部品 (data/skyabyss/opening/*.json) を 8 通りの向き (回す 4 × 左右反転 2) に並べて、階段・戸・はしごを調べる
//  (site <部品>) 部品を本物の SiteJob で世界に建て (向きは 8 通りを順に)、地形との関係 (浮く・埋まる・立つ場所) まで調べる
//  (opening) 新しい世界で自然に建った序盤の構造物
//  (whale / tide / storm / ember) 四つの背を建てて、体の全部を調べる
// 結果は SELFTEST build audit PASSED / FAILED。SKYABYSS_SELFTEST_ONLY="build audit" でこれだけ流せる
public final class BuildAuditSelfTest {
    private static final Report ALL = new Report();
    private static final List<String> LINES = new ArrayList<>();
    private static int siteIndex;
    private static int waited;

    // わざと許す例。理由を書くこと
    static final List<Allow> ALLOW = new ArrayList<>();

    static {
        // 歌の部屋のはしご: 頂の上 (door の印) は、閉じているあいだ石で、開けるとはしごが 1 つ足される (ch1 の SongChamber)。頂の出口は開けたあとの話
        ALLOW.add(new Allow("bp:ch1_song_chamber", "LADDER_TOP", null, "the hatch above the ladder is stone until the song chamber is opened (SongChamber.open adds the last ladder)"));
        ALLOW.add(new Allow("site:ch1_song_chamber", "LADDER_TOP", null, "same: the hatch is closed until the chamber is opened"));
        // 鼓動の間のはしご: 頂の隣 (x -19, y 109〜111) が封印の戸 = 出口。戸は開けたグループにとって開いている (BackReach が通れるか調べる)
        ALLOW.add(new Allow("whale", "LADDER_TOP", new BackReach.Box(-18, 108, -52, -18, 108, -52), "the exit is the sealed gate beside the top (BackReach checks that it opens)"));
        // 火口の縁の段の最後: 縁の高さで終わる見晴らしの段 (その先にしかけは無い。13 の火口の縁を切って外を見られる所)
        ALLOW.add(new Allow("ember", "STAIR_REVERSED", new BackReach.Box(2, 183, -43, 2, 183, -43), "the top step of the crater ledge ends at the rim as a lookout; nothing is gated behind it"));
    }

    private BuildAuditSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("build audit (start)", server -> {
            ALL.findings.clear();
            ALL.suppressed.clear();
            ALL.allow.clear();
            ALL.allow.addAll(ALLOW);
            LINES.clear();
            siteIndex = 0;
            waited = 0;
            if (!BuildAudit.stairConventionHolds()) {
                LINES.add("the stair convention (facing = the tall side) does not hold in this Minecraft version; BuildAudit's stair rules are wrong");
            }
        }));
        steps.add(Step.once("build audit (blueprints)", server -> {
            for (String name : SiteAudit.blueprintNames()) {
                ALL.addAll(SiteAudit.auditBlueprint(name));
            }
        }));
        steps.add(Step.until("build audit (wait for opening sites)", server -> {
            waited++;
            return (!SiteBuilds.busy() && !SiteBuilds.planning() && SiteData.get(server.overworld()).planned()) || waited > 12000;
        }));
        steps.add(Step.once("build audit (opening)", server -> {
            ServerLevel level = server.overworld();
            SiteData data = SiteData.get(level);
            for (SiteData.Entry e : data.allEntries()) {
                if (e.built) {
                    ALL.addAll(SiteAudit.auditEntry(level, data, e));
                }
            }
        }));
        for (String name : SiteAudit.blueprintNames()) {
            steps.add(Step.once("build audit (site " + name + ")", server -> {
                // 向きは 8 通りを順に、場所は新しい世界の地形が毎回違う所を散らす
                int k = siteIndex++;
                int x = 900 + (k % 8) * 170;
                int z = -700 + (k / 8) * 170;
                ALL.addAll(SiteAudit.placeAndAudit(server.overworld(), name, x, z, k & 7));
            }));
        }
        steps.add(Step.once("build audit (backs build)", server -> BackBuildJobs.runAll()));
        steps.add(Step.once("build audit (whale)", server -> back(server, TitanTravel.TITAN_BACK, "whale", level -> {
            TitanBackData data = TitanBackData.get(level);
            if (data.needsBuild()) {
                TitanBackBuilder.build(level, data);
            }
            return new BackReach.Box(-100, 40, -256, 100, 175, 256);
        })));
        steps.add(Step.once("build audit (tide)", server -> back(server, TideTravel.TIDE_BACK, "tide", level -> {
            TideBackBuilder.ensureBuilt(level);
            return new BackReach.Box(-115, 38, -135, 115, 190, 125);
        })));
        steps.add(Step.once("build audit (storm)", server -> back(server, StormTravel.STORM_BACK, "storm", level -> {
            StormBackBuilder.ensureBuilt(level);
            return new BackReach.Box(-225, 105, -145, 225, 200, 145);
        })));
        steps.add(Step.once("build audit (ember)", server -> back(server, EmberIds.BACK, "ember", level -> {
            EmberBodyBuilder.ensureBuilt(level);
            return new BackReach.Box(-140, 44, -230, 140, 235, 260);
        })));
        steps.add(Step.once("build audit", BuildAuditSelfTest::finish));
        return steps;
    }

    private interface Builder {
        BackReach.Box build(ServerLevel level);
    }

    private static void back(MinecraftServer server, ResourceKey<Level> key, String name, Builder b) {
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            LINES.add(name + " back dimension is missing");
            return;
        }
        BackReach.Box box = b.build(level);
        long t0 = System.currentTimeMillis();
        Report r = new Report();
        r.allow.addAll(ALL.allow);
        BuildAudit.scan(new BuildAudit.WorldGrid(level), box, name, r);
        SkyAbyss.LOGGER.info("SELFTEST build audit: {} scanned {} stairs/doors/ladders in {} ms, {} findings", name, r.scanned, System.currentTimeMillis() - t0,
                r.findings.size());
        ALL.addAll(r);
    }

    private static void finish(MinecraftServer server) {
        Map<String, List<BuildAudit.Finding>> groups = new LinkedHashMap<>();
        for (BuildAudit.Finding f : ALL.findings) {
            String s = f.structure();
            String key = s.startsWith("bp:") || s.startsWith("site:") ? s.substring(0, s.indexOf(' ') < 0 ? s.length() : s.indexOf(' ')) + " " + f.kind() : s + " " + f.kind();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        for (var g : groups.entrySet()) {
            List<BuildAudit.Finding> list = g.getValue();
            SkyAbyss.LOGGER.warn("BUILD AUDIT {} x{}", g.getKey(), list.size());
            int max = g.getKey().startsWith("bp:") ? 3 : 400;
            for (int i = 0; i < list.size() && i < max; i++) {
                SkyAbyss.LOGGER.warn("BUILD AUDIT   {}", list.get(i));
            }
        }
        for (String s : ALL.suppressed) {
            SkyAbyss.LOGGER.info("BUILD AUDIT allowed: {}", s);
        }
        SkyAbyss.LOGGER.info("SELFTEST build audit: scanned {} blocks, {} findings in {} groups, {} allowed", ALL.scanned, ALL.findings.size(), groups.size(),
                ALL.suppressed.size());
        if (ALL.findings.isEmpty() && LINES.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST build audit PASSED (stairs facing/gaps/embedding, doors, ladders, site floors, four backs)");
        } else {
            LINES.forEach(l -> SkyAbyss.LOGGER.error("SELFTEST build audit FAILED: {}", l));
            for (var g : groups.entrySet()) {
                SkyAbyss.LOGGER.error("SELFTEST build audit FAILED: {} x{} (first: {})", g.getKey(), g.getValue().size(), g.getValue().get(0));
            }
        }
    }
}
