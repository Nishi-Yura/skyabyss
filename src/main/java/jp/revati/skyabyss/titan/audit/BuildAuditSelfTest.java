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
        // 潮殻の体 (turtle.bin) の黄色い羊毛の盛り上がり: 模型の薄い壁の中が埋められて 1 マスだけ低く見える (体の飾りの色の塊。歩く所ではない)
        ALLOW.add(new Allow("tide", "FLOOR_DIMPLE", new BackReach.Box(40, 125, -41, 40, 125, -41), "a single wool voxel in the turtle body's coral-coloured mound (filled thin wall of the baked model); not a walking surface"));
        // 骨のあばら (anchor の部品) は地形に沿って建つので、起伏のある所で肋骨の先の 1 個が地面から離れて見えることがある (自然の起伏との関係。調べて直すなら RibBuilder / ribs.json)
        ALLOW.add(new Allow("built:ribs", "FLOAT_BLOCK", null, "terrain-following anchor site: a rib tip can end up one step above a dip in the ground; reported in docs/review/build_audit_report.md"));
        // 火口の縁の段の最後: 縁の高さで終わる見晴らしの段 (その先にしかけは無い。13 の火口の縁を切って外を見られる所)
        ALLOW.add(new Allow("ember", "STAIR_REVERSED", new BackReach.Box(2, 183, -43, 2, 183, -43), "the top step of the crater ledge ends at the rim as a lookout; nothing is gated behind it"));
    }

    // 四つの背の調べる範囲 (開発コマンド /skyabyss dev buildaudit <名前> でも使う)
    public static final BackReach.Box WHALE_BOX = new BackReach.Box(-100, 40, -256, 100, 175, 256);
    public static final BackReach.Box TIDE_BOX = new BackReach.Box(-190, 38, -245, 190, 230, 170);
    public static final BackReach.Box STORM_BOX = new BackReach.Box(-225, 105, -145, 225, 200, 145);
    public static final BackReach.Box EMBER_BOX = new BackReach.Box(-140, 44, -230, 140, 235, 260);

    // 「いまある数まで」許す指摘 (増えたら全部失敗)。直す担当が別にある、または設計の判断待ちのもの。直したら数を下げる (0 になったら消す)
    record Budget(String structure, String kind, int max, String reason) {
    }

    static final List<Budget> BUDGETS = new ArrayList<>();

    static {
        // 暗さ: 内部の広間 (潮殻の上段の広間・岩鯨の腹の通路・雷翼の内側・余燼の洞) は、灯りが少なく光度 0〜2 の歩ける所が残る。
        // 数は光の計算の時間で数件ゆらぐので少し余裕を見る。灯りを足す (または暗さを演出として許す) 設計の判断待ち (docs/review/build_audit_report.md の「暗さ」)
        BUDGETS.add(new Budget("whale", "LIGHT_DARK", 300, "the dark waterway (about 126 cells by design: the lamp-stand game) plus a few pockets of shadow in halls and stairs (version 18 lit the routes to 4+; the audit limit rose from 2 to 3, so the count moved from 175 to about 260-290)"));
        BUDGETS.add(new Budget("tide", "LIGHT_DARK", 45, "pockets of shadow left on purpose (lower waterways and a few corners of UPPER_1..3); version 14 lit the routes to 4+ with sea lanterns in the walls"));
        // 岩鯨の谷の飛び石は、宙に浮かぶ一つの石 (跳ぶ道の部品)。浮きの検査から外す
        for (BackReach.Box pit : new BackReach.Box[]{new BackReach.Box(-58, 100, -55, -36, 118, -18), new BackReach.Box(-56, 100, -91, -37, 118, -68),
                new BackReach.Box(-36, 100, -86, -19, 118, -48)}) {
            ALLOW.add(new Allow("whale", "FLOAT_BLOCK", pit, "stepping stones in the west pits float by design (the readable jump line, 26 of 8.6)"));
        }
        // 岩鯨の鎖・くぼみ・浮いた灯りは版 18 で直した (0 件)
    }

    private BuildAuditSelfTest() {
    }

    // 許す数 (Budget) の中の指摘を許す側へ移す。超えていたら移さず、失敗として残す
    private static void applyBudgets() {
        for (Budget b : BUDGETS) {
            List<BuildAudit.Finding> hit = new ArrayList<>();
            for (BuildAudit.Finding f : ALL.findings) {
                if (f.structure().equals(b.structure()) && f.kind().equals(b.kind())) {
                    hit.add(f);
                }
            }
            if (hit.isEmpty()) {
                continue;
            }
            if (hit.size() <= b.max()) {
                ALL.findings.removeAll(hit);
                ALL.suppressed.add(b.structure() + " " + b.kind() + " x" + hit.size() + " [allowed within budget " + b.max() + ": " + b.reason() + "]");
                for (BuildAudit.Finding f : hit) {
                    ALL.suppressed.add("  " + f);
                }
            } else {
                LINES.add(b.structure() + " " + b.kind() + " grew to " + hit.size() + " (budget " + b.max() + "): " + b.reason());
            }
        }
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
        // 背を全部建ててから、光の計算が落ち着くのを待って (200 tick) 調べる
        steps.add(Step.once("build audit (whale build)", server -> build(server, TitanTravel.TITAN_BACK, level -> {
            TitanBackData data = TitanBackData.get(level);
            if (data.needsBuild()) {
                TitanBackBuilder.build(level, data);
            }
        })));
        steps.add(Step.once("build audit (tide build)", server -> build(server, TideTravel.TIDE_BACK, TideBackBuilder::ensureBuilt)));
        steps.add(Step.once("build audit (storm build)", server -> build(server, StormTravel.STORM_BACK, StormBackBuilder::ensureBuilt)));
        steps.add(Step.once("build audit (ember build)", server -> build(server, EmberIds.BACK, EmberBodyBuilder::ensureBuilt)));
        steps.add(Step.once("build audit (whale)", 200, server -> back(server, TitanTravel.TITAN_BACK, "whale", WHALE_BOX,
                level -> jp.revati.skyabyss.titan.dungeon.TitanBackAudit.run(level))));
        steps.add(Step.once("build audit (tide)", server -> back(server, TideTravel.TIDE_BACK, "tide", TIDE_BOX,
                level -> BackReach.run(level, jp.revati.skyabyss.titan.tide.back.TideBackAudit.spec()))));
        steps.add(Step.once("build audit (storm)", server -> back(server, StormTravel.STORM_BACK, "storm", STORM_BOX,
                level -> jp.revati.skyabyss.titan.storm.back.StormBackAudit.run(level))));
        steps.add(Step.once("build audit (ember)", server -> back(server, EmberIds.BACK, "ember", EMBER_BOX,
                level -> jp.revati.skyabyss.titan.ember.back.EmberBackAudit.run(level))));
        steps.add(Step.once("build audit", BuildAuditSelfTest::finish));
        return steps;
    }

    private interface Builder {
        void build(ServerLevel level);
    }

    // 背ごとの到達の調べ (歩ける所 reached を持つ)。RouteLightDev も使う
    public static BackReach.Report routeOf(String name, ServerLevel level) {
        return switch (name) {
            case "whale" -> jp.revati.skyabyss.titan.dungeon.TitanBackAudit.run(level);
            case "tide" -> BackReach.run(level, jp.revati.skyabyss.titan.tide.back.TideBackAudit.spec());
            case "storm" -> jp.revati.skyabyss.titan.storm.back.StormBackAudit.run(level);
            default -> jp.revati.skyabyss.titan.ember.back.EmberBackAudit.run(level);
        };
    }

    private static void build(MinecraftServer server, ResourceKey<Level> key, Builder b) {
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            LINES.add(key.location() + " back dimension is missing");
            return;
        }
        b.build(level);
    }

    private interface Router {
        BackReach.Report run(ServerLevel level);
    }

    private static void back(MinecraftServer server, ResourceKey<Level> key, String name, BackReach.Box box, Router router) {
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            LINES.add(name + " back dimension is missing");
            return;
        }
        long t0 = System.currentTimeMillis();
        Report r = new Report();
        r.allow.addAll(ALL.allow);
        BuildAudit.WorldGrid grid = new BuildAudit.WorldGrid(level);
        BuildAudit.scan(grid, box, name, r);
        // 歩ける所 (BackReach の到達点) の暗さ
        try {
            BackReach.Report reach = router.run(level);
            if (reach.reached != null) {
                BuildAuditExtra.light(grid, reach.reached, box, name, r);
                SkyAbyss.LOGGER.info("SELFTEST build audit: {} route has {} standing cells", name, reach.reached.size());
            }
        } catch (RuntimeException e) {
            LINES.add(name + " route probe failed: " + e);
        }
        SkyAbyss.LOGGER.info("SELFTEST build audit: {} scanned {} stairs/doors/ladders in {} ms, {} findings", name, r.scanned, System.currentTimeMillis() - t0,
                r.findings.size());
        ALL.addAll(r);
    }

    // 構造物ごとの報告 (座標つき) をサーバーのフォルダの build_audit_report.txt に書く
    private static void writeReport() {
        StringBuilder sb = new StringBuilder("# build audit findings (generated by the self test)\n");
        Map<String, List<BuildAudit.Finding>> byStructure = new LinkedHashMap<>();
        for (BuildAudit.Finding f : ALL.findings) {
            String s = f.structure();
            String key = s.startsWith("bp:") || s.startsWith("site:") || s.startsWith("built:") ? s.substring(0, s.indexOf(' ') < 0 ? s.length() : s.indexOf(' ')) : s;
            byStructure.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        for (var e : byStructure.entrySet()) {
            sb.append("\n## ").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");
            Map<String, List<BuildAudit.Finding>> byKind = new LinkedHashMap<>();
            for (BuildAudit.Finding f : e.getValue()) {
                byKind.computeIfAbsent(f.kind(), k -> new ArrayList<>()).add(f);
            }
            for (var k : byKind.entrySet()) {
                sb.append("### ").append(k.getKey()).append(" x").append(k.getValue().size()).append("\n");
                for (BuildAudit.Finding f : k.getValue()) {
                    sb.append("- ").append(f.pos().getX()).append(",").append(f.pos().getY()).append(",").append(f.pos().getZ()).append(": ").append(f.message()).append("\n");
                }
            }
        }
        sb.append("\n## allowed\n");
        for (String a : ALL.suppressed) {
            sb.append("- ").append(a).append("\n");
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("build_audit_report.txt"), sb.toString());
        } catch (java.io.IOException ex) {
            SkyAbyss.LOGGER.warn("could not write build_audit_report.txt: {}", ex.toString());
        }
    }

    private static void finish(MinecraftServer server) {
        applyBudgets();
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
        writeReport();
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
