package jp.revati.skyabyss.opening.site;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.audit.BackReach;
import jp.revati.skyabyss.titan.audit.BuildAudit;
import jp.revati.skyabyss.titan.audit.BuildAudit.Finding;
import jp.revati.skyabyss.titan.audit.BuildAudit.Report;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

// 地上の構造物 (序盤の 4 種・章の小要素) の建築の監査。BuildAudit の決まりに、地形との関係を足す:
//  SITE_FLOAT   地面に建てる部品 (level でも anchor でもない) の最下段の下が空いている (宙に浮く)
//  SITE_SUNK    ならした床 (level の部品) が、まわりの地面より低い (埋まる)
//  SITE_RAISED  ならした床が、まわりの地面より高い (地面から浮いた台になる)
//  SITE_STAND   立つ場所の印 (stand) が埋まっている・足もとが空
public final class SiteAudit {
    private SiteAudit() {
    }

    // 部品の名前の一覧 (開発の実行では資源のフォルダから、jar の中では下の表から)
    private static final String[] KNOWN = {"camp", "ch1_bone_cache", "ch1_bone_pit", "ch1_cart_track", "ch1_cleft_shell_cave", "ch1_diary_spot", "ch1_lookout",
            "ch1_lost_cart", "ch1_road_cairn", "ch1_road_shrine", "ch1_song_chamber", "ch1_song_stone_1", "ch1_song_stone_2", "ch1_song_stone_3",
            "ch1_song_stone_4", "ch1_song_stone_5", "ch1_trace_altar", "ch1_trace_camp", "ch1_trace_cart", "ch1_trace_pack", "ch1_trace_well", "ch3_pack",
            "ch3_ruin", "ch3_tablet", "ch3_vane", "ember_ash_post", "ember_hearth_ruin", "ember_warm_ledge", "footprint", "footprint_end", "footprint_island", "ribs", "shelter",
            "slab", "tide_cairn_a", "tide_cairn_b", "tide_cargo", "tide_stele"};

    public static List<String> blueprintNames() {
        Set<String> names = new HashSet<>(Arrays.asList(KNOWN));
        try {
            URL url = SiteAudit.class.getResource("/data/" + SkyAbyss.MODID + "/opening");
            if (url != null && url.getProtocol().equals("file")) {
                File[] files = new File(url.toURI()).listFiles((d, n) -> n.endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        String n = f.getName().substring(0, f.getName().length() - 5);
                        if (!n.startsWith("chapter_test")) {
                            names.add(n);
                        }
                    }
                }
            }
        } catch (URISyntaxException | RuntimeException e) {
            // 表だけで足りる
        }
        List<String> out = new ArrayList<>(names);
        out.sort(String::compareTo);
        return out;
    }

    // ---------------------------------------------------------------- 部品だけ (8 通りの向き。世界はいらない)
    public static Report auditBlueprint(String name) {
        Report rep = new Report();
        Blueprint bp = Blueprint.get(name);
        for (int mirror = 0; mirror < 2; mirror++) {
            for (int rot = 0; rot < 4; rot++) {
                BuildAudit.MapGrid g = new BuildAudit.MapGrid();
                for (Blueprint.Cell c : bp.cells) {
                    BlockPos at = Blueprint.transform(c.x(), c.y(), c.z(), rot, mirror == 1, BlockPos.ZERO);
                    g.put(at.getX(), at.getY(), at.getZ(), Blueprint.transform(c.state(), rot, mirror == 1));
                }
                int[] r = bp.worldRect(rot, mirror == 1, BlockPos.ZERO);
                BackReach.Box box = new BackReach.Box(r[0] - 3, bp.minY - 2, r[1] - 3, r[2] + 3, bp.maxY + 4, r[3] + 3);
                Report one = new Report();
                BuildAudit.scan(g, box, "bp:" + name + " r" + rot + (mirror == 1 ? "m" : ""), one);
                rep.addAll(one);
            }
        }
        return rep;
    }

    // ---------------------------------------------------------------- 世界に建てて調べる
    // 部品を level の at (x, z) に、向き yaw で、本物の SiteJob で (その場で最後まで) 建てて調べる。野営地などの実際の建て方と同じ ANALYZE/TERRAIN/PLACE
    // 建てる場所の試し数 (水や人の建物で諦められても、次の場所で建て直す)
    static final int TRIES = 16;

    public static Report placeAndAudit(ServerLevel level, String name, int x, int z, int yaw) {
        Report rep = new Report();
        // 乾いた陸に建てる。水・人の建物で建てられなかった (SiteJob が諦めて消した) ら、東へずらして最大 TRIES 回やり直す
        for (int attempt = 0; attempt < TRIES; attempt++) {
            for (int tries = 0; tries < 10 && !dry(level, x, z); tries++) {
                x += 40;
            }
            SiteData tmp = new SiteData();
            BlockPos start = new BlockPos(x, level.getSeaLevel() + 8, z);
            SiteData.Entry e = tmp.add("audit", 0, start, yaw);
            e.blueprint = name;
            e.started = true;
            SiteJob job = new SiteJob(level, tmp, e, Blueprint.get(name), "audit " + name, j -> {
            });
            job.blocking = true;
            int guard = 0;
            while (!job.step(System.nanoTime() + 2_000_000_000L) && guard++ < 200) {
                // 重い部品は何度かに分けて進む
            }
            if (!job.done() || !tmp.allEntries().contains(e) || job.placedCount == 0) {
                x += 97;
                continue;
            }
            auditPlaced(level, Blueprint.get(name), job.rot, job.mirror, job.origin, "site:" + name + " yaw" + yaw, rep);
            return rep;
        }
        rep.add(new Finding("site:" + name, "SITE_UNBUILT", new BlockPos(x, 64, z), "could not be placed on land in " + TRIES + " tries (test spot problem, or the job gives up)"));
        return rep;
    }

    // 中心の 25x25 に、水・木が建物の邪魔をする所が無いか (水面と海底の高さが同じ = 水が無い)。
    // 読み込み済みのチャンクには地形生成用 (_WG) の高さ図が残っていない ("Unprimed heightmap" の ERROR になる) ので、ふつうの高さ図で見る
    private static boolean dry(ServerLevel level, int x, int z) {
        for (int dx = -12; dx <= 12; dx += 6) {
            for (int dz = -12; dz <= 12; dz += 6) {
                int a = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x + dx, z + dz);
                int b = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x + dx, z + dz);
                if (a != b || a <= level.getSeaLevel()) {
                    return false;
                }
            }
        }
        return true;
    }

    // 本物の (自然に建った) 序盤・章の構造物を調べる
    public static Report auditEntry(ServerLevel level, SiteData data, SiteData.Entry e) {
        Report rep = new Report();
        String bpName = !e.blueprint.isEmpty() ? e.blueprint : e.kind.equals("footprint") ? FootprintBuilder.blueprintName(data, e)
                : e.kind.equals("shelter") && SiteFinder.capital() ? "capital" : e.kind;
        Blueprint bp;
        try {
            bp = Blueprint.get(bpName);
        } catch (RuntimeException ex) {
            return rep;
        }
        auditPlaced(level, bp, SiteFinder.rot(e.yaw), SiteFinder.mirror(e.yaw), e.pos.below(), "built:" + e.kind + e.index + "(" + bpName + ") o=" + e.pos.getX() + "," + (e.pos.getY() - 1) + "," + e.pos.getZ() + " r=" + SiteFinder.rot(e.yaw) + " m=" + SiteFinder.mirror(e.yaw), rep);
        return rep;
    }

    // 建て終わった部品 (origin は地面の上面のブロック)
    static void auditPlaced(ServerLevel level, Blueprint bp, int rot, boolean mirror, BlockPos origin, String label, Report rep) {
        int[] r = bp.worldRect(rot, mirror, new BlockPos(origin.getX(), 0, origin.getZ()));
        BuildAudit.WorldGrid g = new BuildAudit.WorldGrid(level);
        for (int cx = (r[0] - 4) >> 4; cx <= (r[2] + 4) >> 4; cx++) {
            for (int cz = (r[1] - 4) >> 4; cz <= (r[3] + 4) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BackReach.Box box = new BackReach.Box(r[0] - 3, origin.getY() + bp.minY - 2, r[1] - 3, r[2] + 3, origin.getY() + bp.maxY + 5, r[3] + 3);
        Report one = new Report();
        BuildAudit.scan(g, box, label, one);
        // 枠は部品の外へ 3 マス広い。範囲の外の戸・向きのある物 (近くの村の家の戸・かまど) は、この Mod の部品ではないので数えない
        for (Finding f : one.findings) {
            boolean foreign = (f.kind().startsWith("DOOR_") || f.kind().equals("ATTACH_FACING")) && f.pos() != null
                    && (f.pos().getX() < r[0] || f.pos().getX() > r[2] || f.pos().getZ() < r[1] || f.pos().getZ() > r[3]);
            if (foreign) {
                rep.suppressed.add(f + " [outside the part: a neighbouring vanilla structure]");
            } else {
                rep.add(f);
            }
        }
        rep.suppressed.addAll(one.suppressed);
        rep.scanned += one.scanned;
        terrain(g, bp, rot, mirror, origin, label, rep);
    }

    private static void terrain(BuildAudit.Grid g, Blueprint bp, int rot, boolean mirror, BlockPos origin, String label, Report rep) {
        // 最下段 (部品の y <= 0) の列
        java.util.Map<Long, Integer> lowest = new java.util.HashMap<>();
        for (Blueprint.Cell c : bp.cells) {
            if (c.state().isAir() || c.y() > 0) {
                continue;
            }
            BlockPos at = Blueprint.transform(c.x(), c.y() + 1, c.z(), rot, mirror, origin);
            lowest.merge(BlockPos.asLong(at.getX(), 0, at.getZ()), at.getY(), Math::min);
        }
        // 地下のある列 (Blueprint.deepColumns。都の酒場の地下・井戸の下など) は浮きの検査から外す。
        // 列ごとに外すので、地下を持つ部品でもほかの列は調べる。地下だけの部品 (列がすべて深い) は高さの検査も外す
        java.util.Set<Long> deep = new java.util.HashSet<>();
        for (long d : bp.deepColumns) {
            BlockPos at = Blueprint.transform(BlockPos.getX(d), 0, BlockPos.getZ(d), rot, mirror, origin);
            deep.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
        }
        boolean underground = bp.minY < -2 && deep.size() * 2 >= lowest.size();
        if (!bp.anchor && bp.relief.isEmpty() && !underground) {
            int floating = 0;
            int maxGap = 0;
            BlockPos first = null;
            for (var en : lowest.entrySet()) {
                if (deep.contains(en.getKey())) {
                    continue;
                }
                int x = BlockPos.getX(en.getKey());
                int z = BlockPos.getZ(en.getKey());
                int y = en.getValue();
                int gap = 0;
                while (gap < 8 && BuildAudit.topOf(g.get(x, y - 1 - gap, z)) == 0 && g.get(x, y - 1 - gap, z).getFluidState().isEmpty()) {
                    gap++;
                }
                if (gap > 0) {
                    floating++;
                    maxGap = Math.max(maxGap, gap);
                    if (first == null) {
                        first = new BlockPos(x, y, z);
                    }
                }
            }
            if (floating > 0) {
                rep.add(new Finding(label, "SITE_FLOAT", first, floating + " of " + lowest.size() + " base columns have air under the lowest block (up to " + maxGap
                        + " deep) on uneven ground"));
            }
        }
        if (bp.level && !underground && !lowest.isEmpty()) {
            int total = 0;
            int sunk = 0;
            int raised = 0;
            int sunkMax = 0;
            int raisedMax = 0;
            BlockPos firstSunk = null;
            BlockPos firstRaised = null;
            for (var en : lowest.entrySet()) {
                int x = BlockPos.getX(en.getKey());
                int z = BlockPos.getZ(en.getKey());
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (lowest.containsKey(BlockPos.asLong(nx, 0, nz))) {
                        continue;
                    }
                    int t = groundTop(g, nx, nz, origin.getY() + 12);
                    if (t == Integer.MIN_VALUE) {
                        continue;
                    }
                    total++;
                    int diff = t - origin.getY();
                    if (diff >= 1) {
                        sunk++;
                        sunkMax = Math.max(sunkMax, diff);
                        if (firstSunk == null) {
                            firstSunk = new BlockPos(nx, t, nz);
                        }
                    } else if (diff <= -1) {
                        raised++;
                        raisedMax = Math.max(raisedMax, -diff);
                        if (firstRaised == null) {
                            firstRaised = new BlockPos(nx, t, nz);
                        }
                    }
                }
            }
            if (total > 0 && sunk * 10 >= total * 4) {
                rep.add(new Finding(label, "SITE_SUNK", firstSunk, sunk + " of " + total + " perimeter columns are higher than the floor (ground block " + origin.getY()
                        + "), up to " + sunkMax));
            }
            if (total > 0 && raised * 10 >= total * 4) {
                rep.add(new Finding(label, "SITE_RAISED", firstRaised, raised + " of " + total + " perimeter columns are lower than the floor (ground block "
                        + origin.getY() + "), up to " + raisedMax + " (there: " + g.get(firstRaised) + " / above " + g.get(firstRaised.above()) + ")"));
            }
        }
        // 立つ場所の印 (部品の y は最初の段が 0)
        int[] m = bp.marks.get("stand");
        if (m != null) {
            BlockPos at = Blueprint.transform(m[0], m[1] + 1, m[2], rot, mirror, origin);
            if (BuildAudit.topOf(g.get(at)) > 0 || BuildAudit.topOf(g.get(at.above())) > 0) {
                rep.add(new Finding(label, "SITE_STAND", at, "the stand mark is inside blocks: " + g.get(at) + " / " + g.get(at.above())));
            } else if (BuildAudit.topOf(g.get(at.below())) < 0.5) {
                rep.add(new Finding(label, "SITE_STAND", at, "nothing to stand on under the stand mark: " + g.get(at.below())));
            }
        }
    }

    // その列の地面 (葉・木・草花を除いた、いちばん上の丸ごとのブロック・土の道) の y。水面なら MIN_VALUE
    private static int groundTop(BuildAudit.Grid g, int x, int z, int from) {
        for (int y = from; y > from - 40; y--) {
            BlockState s = g.get(x, y, z);
            // 水面の列は比べない (水の上の石の台は水面より 1 高い)。土の道・耕地・泥 (14〜15/16 の高さ) も地面
            if (!s.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (BuildAudit.topOf(s) >= 0.85
                    && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
