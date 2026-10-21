package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

// 雷翼の背の自己テスト (SELFTEST storm back)。SelfTestRunner の段に入れる。
// 建てる (時間を測る) → 100 tick 待つ (落ちるブロック・空気の置き換えが落ち着いてから) → 形・看板・囁き・拍・鏡・柱・横風・
// 歩いて行ける所 (凪・嵐・戸が開いたあと・雷獣のあと・鏡のあと・竜骨の門のあと。進めたグループと進めていないグループ)・
// グループごとのリセット・眠る試練・二つのグループが同じ背中で (扉の当たり判定・部屋の占有・見かけ・雷)・パーティの記録の引き継ぎ・ピースフル
public final class StormBackSelfTest {
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    static final int SETTLE_TICKS = 100;

    private StormBackSelfTest() {
    }

    public static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("storm back (build)", server -> guarded(server, back -> {
            FAILURES.clear();
            NOTES.clear();
            forceChunks(back, true);
            long t0 = System.currentTimeMillis();
            StormBackBuilder.build(back, TitanBackData.get(back));
            NOTES.add("built in " + (System.currentTimeMillis() - t0) + " ms");
            if (StormBirdBuilder.BIRD.exists()) {
                NOTES.add("bird " + StormBirdBuilder.BIRD.count() + " blocks");
            }
        })));
        steps.add(Step.once("storm back", SETTLE_TICKS, server -> guarded(server, back -> {
            dumpSpec(back);
            check(back.getEntitiesOfClass(FallingBlockEntity.class, new AABB(-340, 60, -200, 340, 215, 205)).isEmpty(),
                    "blocks are falling in the storm back after building", FAILURES);
            checkClock(FAILURES);
            checkBird(back, FAILURES);
            checkShell(back, FAILURES, NOTES);
            checkSigns(back, FAILURES);
            checkWhispers(FAILURES);
            checkMirrors(back, FAILURES, NOTES);
            checkRods(back, FAILURES);
            checkWind(back, FAILURES);
            checkWalk(back, FAILURES, NOTES);
            // 詰み監査 (到達・戻れるか・はしご・床の穴)。戸は開いているものとして、嵐の昇り風も使える
            jp.revati.skyabyss.titan.audit.BackReach.Report reach = StormBackAudit.run(back);
            reach.notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST {}", n));
            reach.failures.forEach(f -> SkyAbyss.LOGGER.warn("REACH {}", f));
            FAILURES.addAll(reach.failures);
            checkReset(back, FAILURES);
            checkGroups(back, FAILURES);
            checkGuide(back, FAILURES);
            checkNoStateGates(back, FAILURES);
            checkParty(back, FAILURES);
            checkPeaceful(back, FAILURES);
            check(TitanBacks.isBack(StormTravel.STORM_BACK), "storm_back is not in the back list", FAILURES);
            StormBackClock.force(null);
            BackGroupData.get(back).remove(GROUP_A);
            BackGroupData.get(back).remove(GROUP_B);
            forceChunks(back, false);
            if (FAILURES.isEmpty()) {
                SkyAbyss.LOGGER.info("SELFTEST storm back PASSED ({})", String.join("; ", NOTES));
            } else {
                FAILURES.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: {}", f));
                SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: {} problems ({})", FAILURES.size(), String.join("; ", NOTES));
            }
        })));
        return steps;
    }

    private static void guarded(MinecraftServer server, java.util.function.Consumer<ServerLevel> body) {
        ServerLevel back = server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            FAILURES.add("storm_back is missing");
            SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: storm_back is missing");
            return;
        }
        try {
            body.accept(back);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm back crashed", e);
            FAILURES.add("crashed: " + e);
            SkyAbyss.LOGGER.error("SELFTEST storm back FAILED: crashed: {}", e.toString());
        }
    }

    private static void forceChunks(ServerLevel back, boolean on) {
        for (int cx = -340 >> 4; cx <= 340 >> 4; cx++) {
            for (int cz = -200 >> 4; cz <= 270 >> 4; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    // ---------------------------------------------------------------- 嵐の拍
    private static void checkClock(List<String> f) {
        check(StormBackClock.CYCLE.length() == 1800, "storm beat should be 90 s", f);
        check(StormBackClock.phaseAt(0) == StormBackClock.Phase.CALM && StormBackClock.phaseAt(799) == StormBackClock.Phase.CALM, "calm is 40 s", f);
        check(StormBackClock.phaseAt(800) == StormBackClock.Phase.RISING && StormBackClock.phaseAt(899) == StormBackClock.Phase.RISING, "rising is 5 s", f);
        check(StormBackClock.phaseAt(900) == StormBackClock.Phase.STORM && StormBackClock.phaseAt(1699) == StormBackClock.Phase.STORM, "storm is 40 s", f);
        check(StormBackClock.phaseAt(1700) == StormBackClock.Phase.FALLING && StormBackClock.phaseAt(1799) == StormBackClock.Phase.FALLING, "falling is 5 s", f);
        check(StormBackClock.CYCLE.warnsNow(600, 1) && StormBackClock.CYCLE.warnsNow(1500, 3), "the 10 s warnings are at 30 s and 75 s", f);
        check(StormBackClock.strengthAt(0) == 0 && StormBackClock.strengthAt(1000) == 1, "storm strength", f);
    }

    // ---------------------------------------------------------------- 鳥の形
    private static void checkBird(ServerLevel l, List<String> f) {
        if (!StormBirdBuilder.BIRD.exists()) {
            f.add("storm_back/bird.bin is missing (the back is not bird-shaped)");
            return;
        }
        BackShapeProbe p = new BackShapeProbe(l);
        // 頭 (北)・左右の翼の手首 (西・東)・尾 (南) に鳥のブロックがある
        check(p.columnHas(0, -130), "no head at (0, -130)", f);
        check(p.columnHas(-250, WZ) && p.columnHas(250, WZ), "no wings out to x ±250", f);
        check(p.columnHas(0, 170), "no tail at (0, 170)", f);
        check(!p.columnHas(0, -190) && !p.columnHas(340, 0), "the bird is larger than planned", f);
        // 部屋は体の中: 広間の真ん中の上は鳥 (天窓のガラスを除く)
        check(p.columnHas(10, -10) && StormBirdBuilder.top(10, -10) > HUB.floor() + HUB.height(), "the wind hall is not inside the body", f);
        check(StormBirdBuilder.top(RAIJU_NEST.getX(), WZ) > RAIJU_NEST_CEILING, "the right wrist knob pokes out of the wing", f);
        check(StormBirdBuilder.top(0, EYE_HALL_CENTER.getZ()) > EYE_HALL.floor() + EYE_HALL.height(), "the eye hall pokes out of the head", f);
    }

    // ---------------------------------------------------------------- 体の皮 (データで確かめる。ダンジョンの試遊はしない)
    // 1) 背の穴: 鳥のいちばん上のブロックとその下が空いている柱 (羽根の門・背の上へ出る段・到着の台・天窓を除く)
    // 2) 漏れ: 風の広間から空気をたどって、鳥の形の外 (上か下) へ出る所 (羽根の門と背の上の段の口は出口として許す)
    // 3) 水と溶岩: 建てて 100 tick 置いたあと、体の中の空気に流れる物が無い。休み場と部屋の真ん中は空気
    private static void checkShell(ServerLevel l, List<String> f, List<String> notes) {
        if (!StormBirdBuilder.BIRD.exists()) {
            return;
        }
        var bird = StormBirdBuilder.BIRD;
        int holes = 0;
        BlockPos firstHole = null;
        for (int x = bird.minX(); x <= bird.maxX(); x++) {
            for (int z = bird.minZ(); z <= bird.maxZ(); z++) {
                int t = bird.top(x, z);
                if (t == Integer.MIN_VALUE || allowedOpening(x, t, z)) {
                    continue;
                }
                // 空が見えて (上に何も建っていない)、鳥の上の 2 段が空いている
                if (skyAt(l, x, z) < t && !l.getBlockState(new BlockPos(x, t - 1, z)).blocksMotion()) {
                    holes++;
                    if (firstHole == null) {
                        firstHole = new BlockPos(x, t, z);
                    }
                }
            }
        }
        check(holes == 0, "the bird's back has " + holes + " holes (first at " + firstHole + ")", f);
        if (holes > 0 || DUMP) {
            dumpHoles(l, bird);
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(HUB_CENTER.above());
        seen.add(HUB_CENTER.above());
        List<BlockPos> leaks = new ArrayList<>();
        int fluids = 0;
        BlockPos firstFluid = null;
        while (!queue.isEmpty() && seen.size() < 3_000_000) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q)) {
                    continue;
                }
                var st = l.getBlockState(q);
                // 封印の扉は (開いた扉も) 壁として数える。扉の先は「開けたグループだけが入れる所」で、ここでは広間から空気をたどる
                if ((st.blocksMotion() || st.is(ModBlocks.SEALED_GATE.get())) && st.getFluidState().isEmpty()) {
                    continue;
                }
                seen.add(q);
                if (!st.getFluidState().isEmpty()) {
                    fluids++;
                    if (firstFluid == null) {
                        firstFluid = q;
                    }
                }
                // 外: 空が見える (上に固いブロックが無い) か、鳥の下
                boolean outside = q.getY() >= skyAt(l, q.getX(), q.getZ())
                        || (q.getY() < bird.bottom(q.getX(), q.getZ()) && openBelow(l, q, bird.minY() - 2));
                if (outside) {
                    if (!allowedOpening(q.getX(), q.getY(), q.getZ()) && leaks.size() < 5) {
                        leaks.add(q);
                    }
                    continue;
                }
                queue.add(q);
            }
        }
        check(leaks.isEmpty(), "the inside of the bird opens to the sky at " + leaks, f);
        check(fluids == 0, fluids + " fluid blocks inside the bird (first at " + firstFluid + ")", f);
        for (Rest r : RESTS) {
            check(l.getBlockState(r.spawn()).isAir() && l.getBlockState(r.spawn().above()).isAir(), "rest " + r.id() + " is not open air", f);
        }
        for (BlockPos c : List.of(TAIL_CENTER, HUB_CENTER.above(), EYE_HALL_CENTER.offset(1, 0, 6), HEART_ROOM.offset(0, 2, 8))) {
            check(l.getBlockState(c).isAir(), "the room around " + c + " is not air", f);
        }
        notes.add("inside air " + seen.size() + ", back holes " + holes + ", leaks " + leaks.size() + ", fluids " + fluids);
    }

    // いちばん上の固いブロックの 1 上 (ここから上は空が見える)
    static int skyAt(ServerLevel l, int x, int z) {
        return l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    // 下に固いブロックが一つもない (嵐の雲へ落ちる)
    static boolean openBelow(ServerLevel l, BlockPos q, int minY) {
        for (int y = q.getY() - 1; y >= minY; y--) {
            if (l.getBlockState(new BlockPos(q.getX(), y, q.getZ())).blocksMotion()) {
                return false;
            }
        }
        return true;
    }

    static final boolean DUMP = System.getenv("SKYABYSS_STORM_DUMP") != null;

    // 見取り図 (開発用): 環境変数 SKYABYSS_STORM_SPEC="plan,y,x1,x2,z1,z2;secz,z,x1,x2,y1,y2;secx,x,z1,z2,y1,y2" で、建てた形を run/storm_back_dump/ に文字で書く。
    // . 空気 ~ 流れる物 s 階段 L 灯り g ガラス c 銅 B 骨 k 方解石 G 封印の扉 = 格子 I 避雷の柱 / レバー f 焚き火 # ほかの固いもの
    static void dumpSpec(ServerLevel l) {
        String spec = System.getenv("SKYABYSS_STORM_SPEC");
        if (spec == null || spec.isBlank()) {
            return;
        }
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("storm_back_dump");
            java.nio.file.Files.createDirectories(dir);
            int n = 0;
            for (String item : spec.split(";")) {
                String[] a = item.trim().split(",");
                if (a.length < 6) {
                    continue;
                }
                int k = Integer.parseInt(a[1]);
                int[] v = new int[4];
                for (int i = 0; i < 4; i++) {
                    v[i] = Integer.parseInt(a[2 + i]);
                }
                StringBuilder sb = new StringBuilder(item + "\n");
                switch (a[0]) {
                    case "plan" -> {
                        for (int z = v[2]; z <= v[3]; z++) {
                            sb.append(String.format("%4d ", z));
                            for (int x = v[0]; x <= v[1]; x++) {
                                sb.append(glyph(l.getBlockState(new BlockPos(x, k, z))));
                            }
                            sb.append('\n');
                        }
                    }
                    case "secz" -> {
                        for (int y = v[3]; y >= v[2]; y--) {
                            sb.append(String.format("%4d ", y));
                            for (int x = v[0]; x <= v[1]; x++) {
                                sb.append(glyph(l.getBlockState(new BlockPos(x, y, k))));
                            }
                            sb.append('\n');
                        }
                    }
                    default -> {
                        for (int y = v[3]; y >= v[2]; y--) {
                            sb.append(String.format("%4d ", y));
                            for (int z = v[0]; z <= v[1]; z++) {
                                sb.append(glyph(l.getBlockState(new BlockPos(k, y, z))));
                            }
                            sb.append('\n');
                        }
                    }
                }
                java.nio.file.Files.writeString(dir.resolve("spec" + (n++) + "_" + a[0] + a[1] + ".txt"), sb.toString());
            }
        } catch (java.io.IOException | RuntimeException e) {
            SkyAbyss.LOGGER.error("could not write the storm back spec dump", e);
        }
    }

    private static char glyph(BlockState s) {
        if (s.isAir()) {
            return '.';
        }
        if (!s.getFluidState().isEmpty()) {
            return '~';
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (id.endsWith("_stairs")) {
            return 's';
        }
        if (id.contains("sea_lantern") || id.contains("froglight") || id.contains("glowstone") || id.contains("copper_bulb")) {
            return 'L';
        }
        if (id.contains("glass")) {
            return 'g';
        }
        if (id.contains("grate")) {
            return '=';
        }
        if (id.contains("sealed")) {
            return 'G';
        }
        if (id.contains("copper")) {
            return 'c';
        }
        if (id.contains("bone")) {
            return 'B';
        }
        if (id.equals("calcite")) {
            return 'k';
        }
        if (id.contains("rod")) {
            return 'I';
        }
        if (id.equals("lever")) {
            return '/';
        }
        if (id.contains("campfire")) {
            return 'f';
        }
        return s.blocksMotion() ? '#' : '+';
    }

    // 背の穴の地図と断面図を run/storm_back_dump/ に書く (上から: '#' 穴、'+' 鳥、'.' なし。断面: '#' 固い、' ' 空、'~' 流れる物)
    static void dumpHoles(ServerLevel l, jp.revati.skyabyss.titan.BackShape bird) {
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("storm_back_dump");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder top = new StringBuilder();
            for (int z = bird.minZ(); z <= bird.maxZ(); z++) {
                for (int x = bird.minX(); x <= bird.maxX(); x++) {
                    int t = bird.top(x, z);
                    char c = '.';
                    if (t != Integer.MIN_VALUE) {
                        boolean hole = !allowedOpening(x, t, z) && skyAt(l, x, z) < t && !l.getBlockState(new BlockPos(x, t - 1, z)).blocksMotion();
                        c = hole ? '#' : '+';
                    }
                    top.append(c);
                }
                top.append('\n');
            }
            java.nio.file.Files.writeString(dir.resolve("holes_top.txt"), top.toString());
            for (int z : new int[] {WZ, -10, HUB_CENTER.getZ(), 0, -126, 38, 111, 150}) {
                StringBuilder sec = new StringBuilder("z=" + z + " x " + bird.minX() + ".." + bird.maxX() + "\n");
                for (int y = 170; y >= 110; y--) {
                    sec.append(String.format("%3d ", y));
                    for (int x = bird.minX(); x <= bird.maxX(); x++) {
                        var st = l.getBlockState(new BlockPos(x, y, z));
                        boolean inBird = bird.at(x, y, z) != null;
                        sec.append(!st.getFluidState().isEmpty() ? '~' : st.blocksMotion() ? (inBird ? '#' : '@') : (inBird ? ' ' : (y > bird.top(x, z) ? '.' : ',')));
                    }
                    sec.append('\n');
                }
                java.nio.file.Files.writeString(dir.resolve("section_z" + z + ".txt"), sec.toString());
            }
        } catch (java.io.IOException e) {
            SkyAbyss.LOGGER.error("could not write the storm back dump", e);
        }
    }

    // 外へ開いていてよい所: 空へ開く口 (StormBackLayout.openToSky) と、風切りの骨の割れた床 (下の嵐の雲へ抜ける。12 の 4.1節)
    static boolean allowedOpening(int x, int y, int z) {
        if (y < HIGH_BEAM_Y && z >= BONE_Z1 - 1 && z <= BONE_Z2 + 1) {
            for (int[] g : WEST_GAPS) {
                if (x >= g[0] - 1 && x <= g[1] + 1) {
                    return true;
                }
            }
        }
        return openToSky(x, y, z);
    }

    private record BackShapeProbe(ServerLevel l) {
        boolean columnHas(int x, int z) {
            return StormBirdBuilder.BIRD.top(x, z) != Integer.MIN_VALUE;
        }
    }

    // ---------------------------------------------------------------- 碑 (ちょうど 9 枚、どの行も 90 px に収まる)
    private static void checkSigns(ServerLevel l, List<String> f) {
        Set<String> keys = new HashSet<>();
        int n = 0;
        for (int cx = -340 >> 4; cx <= 340 >> 4; cx++) {
            for (int cz = -200 >> 4; cz <= 270 >> 4; cz++) {
                for (BlockEntity be : l.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof TabletBlockEntity) {
                        n++;
                    }
                }
            }
        }
        int expected = LORE_SIGNS.size() + ENTRANCE_SIGNS.size();
        check(n == expected, "there should be exactly " + expected + " tablets on the storm back, found " + n, f);
        for (Inscription i : LORE_SIGNS) {
            keys.add(i.key());
            check(l.getBlockEntity(i.pos()) instanceof TabletBlockEntity, "sign " + i.key() + " is missing at " + i.pos(), f);
        }
        for (Inscription i : ENTRANCE_SIGNS) {
            keys.add(i.key());
            check(l.getBlockEntity(i.pos()) instanceof TabletBlockEntity, "sign " + i.key() + " is missing at " + i.pos(), f);
        }
        // 文の幅: 伝承の担当がまとめた lang (skyabyss_storm) で測る
        for (String lang : SignFit.LANGUAGES) {
            Map<String, String> m = SignFit.lang(lang);
            for (String k : keys) {
                if (!m.containsKey(k + ".1")) {
                    f.add("sign " + k + " has no text in " + lang);
                    continue;
                }
                for (int i = 1; i <= 4; i++) {
                    String line = m.getOrDefault(k + "." + i, "");
                    int w = SignFit.approxWidth(line);
                    check(w <= SignFit.MAX_LINE_WIDTH, "sign " + k + "." + i + " (" + lang + ") is " + w + " px wide: " + line, f);
                    check(!lang.equals("ja_jp") || line.length() <= 8, "sign " + k + "." + i + " (ja) is longer than 8 characters: " + line, f);
                }
            }
        }
    }

    // この担当と地上の担当が使う文の鍵 (lang/parts/{back,ground}.json。tools/gen_storm_lore.py が ja_jp・en_us にまとめる)
    static List<String> partKeys() {
        List<String> out = new ArrayList<>();
        for (String part : List.of("back", "ground")) {
            try (InputStream in = StormBackSelfTest.class.getResourceAsStream("/assets/skyabyss_storm/lang/parts/" + part + ".json")) {
                if (in != null) {
                    out.addAll(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().keySet());
                }
            } catch (Exception e) {
                SkyAbyss.LOGGER.error("could not read the storm lang part {}", part, e);
            }
        }
        return out;
    }

    private static void checkWhispers(List<String> f) {
        List<String> keys = new ArrayList<>(partKeys());
        keys.addAll(jp.revati.skyabyss.titan.storm.lore.StormWhispers.keys());
        check(keys.size() > 60, "the storm lang parts are missing", f);
        for (String lang : SignFit.LANGUAGES) {
            for (String k : keys) {
                check(SignFit.lang(lang).containsKey(k), "text " + k + " is missing in " + lang + " (run tools/gen_storm_lore.py)", f);
            }
        }
    }

    // ---------------------------------------------------------------- 二つのグループ (自己テストの合成の鍵。一人のグループ = 人の UUID)
    static final UUID GROUP_A = UUID.fromString("5c1e0a2b-0000-4000-8000-00000057a001");
    static final UUID GROUP_B = UUID.fromString("5c1e0a2b-0000-4000-8000-00000057b002");

    // グループの記録を最初に戻す (眠る試練なし、人は動かさない)
    static StormProgress fresh(ServerLevel l, UUID group) {
        StormBackTrials.resetGroup(l, group, false, null, true, false);
        return StormProgress.of(l, group);
    }

    // そのグループの人にとって、そのブロックを通れるか (扉はそのグループの開け閉め。当たり判定はその人の当たり判定で見る)
    static boolean blocksFor(ServerLevel l, BlockPos p, net.minecraft.world.entity.Entity who) {
        return !l.getBlockState(p).getCollisionShape(l, p, CollisionContext.of(who)).isEmpty();
    }

    // ---------------------------------------------------------------- 眼の間 (四つの配置を解く。世界の鏡でも同じ手で解ける)
    private static void checkMirrors(ServerLevel l, List<String> f, List<String> notes) {
        StormProgress state = fresh(l, GROUP_A);
        RoomLocks.release(StormBackTrials.EYE_ROOM);
        List<String> mins = new ArrayList<>();
        for (int i = 0; i < EyeMirrors.CONFIGS.size(); i++) {
            EyeMirrors.Config cfg = EyeMirrors.CONFIGS.get(i);
            int m = EyeMirrors.solve(cfg);
            mins.add(String.valueOf(m));
            check(m == EyeMirrors.MIN_MOVES[i], "mirror config " + i + " needs " + m + " moves (planned " + EyeMirrors.MIN_MOVES[i] + ")", f);
            check(m >= 4 && m <= 7, "mirror config " + i + " should take 4-7 moves", f);
            check(cfg.mirrors().size() >= 6 && cfg.mirrors().size() <= 8, "mirror config " + i + " should have 6-8 mirrors", f);
            // 世界の格子でたどった光が、格子だけの計算と同じ (部屋を A の記録で置く)
            state = fresh(l, GROUP_A);
            state.setMirrorConfig(i);
            EyeMirrors.apply(l, state);
            // 受け羽の額縁 (銅) は、いまの配置の受け羽のまわりだけ
            for (EyeMirrors.Config other : EyeMirrors.CONFIGS) {
                for (EyeMirrors.Wall w : other.receivers()) {
                    boolean active = cfg.receivers().contains(w);
                    boolean copper = l.getBlockState(EyeMirrors.wallPos(w).above()).is(net.minecraft.world.level.block.Blocks.WAXED_CUT_COPPER);
                    check(active == copper, "mirror config " + i + ": the copper frame of receiver " + w + " is " + copper + " (active " + active + ")", f);
                }
            }
            // しゃがんで右クリックは逆回し (3 回右へ = 1 回左へ)
            if (!cfg.mirrors().isEmpty()) {
                int[] m0 = cfg.mirrors().get(0);
                BlockPos mp = gridCell(m0[0], m0[1]);
                int before = state.mirrorRots()[0];
                EyeMirrors.turnFor(l, state, mp, 3);
                check(state.mirrorRots()[0] == Math.floorMod(before - 1, 4), "mirror config " + i + ": reverse turn", f);
                EyeMirrors.turnFor(l, state, mp);
                check(state.mirrorRots()[0] == before, "mirror config " + i + ": turning back and forth", f);
            }
            EyeMirrors.Trace a = EyeMirrors.trace(cfg.entry(), EyeMirrors.blockGrid(l));
            EyeMirrors.Trace b = EyeMirrors.trace(cfg.entry(), EyeMirrors.grid(cfg, cfg.init()));
            check(java.util.Objects.equals(a.exit(), b.exit()), "mirror config " + i + ": the world beam differs from the grid", f);
            BlockPos door = new BlockPos(0, EYE_HALL.floor() + 1, BEAK_DOOR_Z1);
            check(l.getBlockState(door).is(ModBlocks.SEALED_GATE.get()) && !GroupGates.isOpen(l, GROUP_A, door), "beak door should start closed", f);
            // 鏡を、解き方のとおりに回す (A の記録と世界の鏡の両方)
            List<Integer> path = EyeMirrors.solvePath(cfg);
            if (path == null) {
                f.add("mirror config " + i + " has no solution");
                continue;
            }
            boolean first = true;
            for (int k : path) {
                int[] c = cfg.mirrors().get(k);
                EyeMirrors.turnFor(l, state, gridCell(c[0], c[1]));
                EyeMirrors.tick(l, state);
                if (first && state.receivers() != 7) {
                    // 光の筋は光る箱 (block_display)。入口の窓から終わりまで、少なくとも 2 本はある (25 の 2.1 M1)
                    int boxes = l.getEntities((net.minecraft.world.entity.Entity) null, EYE_HALL.aabb().inflate(8, 6, 6),
                            e -> e.getTags().contains(EyeMirrors.BEAM_TAG)).size();
                    check(boxes >= 2, "mirror config " + i + ": the beam has " + boxes + " display boxes", f);
                }
                first = false;
            }
            EyeMirrors.Trace w = EyeMirrors.trace(cfg.entry(), EyeMirrors.blockGrid(l));
            EyeMirrors.Trace r = EyeMirrors.trace(cfg.entry(), EyeMirrors.grid(cfg, state.mirrorRots()));
            check(java.util.Objects.equals(w.exit(), r.exit()), "mirror config " + i + ": the world mirrors differ from the group's record", f);
            check(state.receivers() == 7, "mirror config " + i + ": receivers lit " + Integer.toBinaryString(state.receivers()) + " after solving", f);
            check(GroupGates.isOpen(l, GROUP_A, door), "mirror config " + i + ": beak door did not open for the group", f);
            check(!GroupGates.isOpen(l, GROUP_B, door), "mirror config " + i + ": beak door opened for another group", f);
        }
        check(l.getBlockState(MIRROR_RESET_HALL).is(StormBackSetup.RETURN_MIRROR.get()) && l.getBlockState(MIRROR_RESET).is(StormBackSetup.RETURN_MIRROR.get()),
                "both reset mirrors should be in place", f);
        notes.add("mirror moves " + String.join("/", mins));
        fresh(l, GROUP_A);
        fresh(l, GROUP_B);
        EyeMirrors.applyNeutral(l);
    }

    // ---------------------------------------------------------------- 避雷の柱・招雷・骨の戸・予告線
    private static void checkRods(ServerLevel l, List<String> f) {
        StormProgress state = fresh(l, GROUP_A);
        fresh(l, GROUP_B);
        StormEast.apply(l, state);
        BlockPos rod = EAST_RODS.get(0);
        check(StormRods.find(l, rod.offset(2, 0, 1), 3.0) != null, "a ring 2 blocks from the rod should go to the rod", f);
        check(StormRods.find(l, rod.offset(5, 0, 0), 3.0) == null, "a ring 5 blocks from the rod should not go to the rod", f);
        StormStrike.Strike s = StormStrike.call(l, net.minecraft.world.phys.Vec3.atBottomCenterOf(rod.offset(1, 0, 1)), 1, 11f, null, GROUP_A);
        check(s.rod != null && GROUP_A.equals(s.group), "the strike next to the rod did not pick the rod (or lost its group)", f);
        // 落雷は次の tick。ここでは柱を満たして、戸が開くのを見る
        StormRods.charge(l, rod, StormRods.FULL);
        check(StormRods.isFull(l, rod), "the first rod is not full after 3 charges", f);
        StormEast.openFullDoors(l, state);
        BlockPos door = new BlockPos(EAST_DOORS[0], BONE_FLOOR + 1, WZ);
        check(state.eastDoorOpen(0) && GroupGates.isOpen(l, GROUP_A, door), "the first bone door did not open", f);
        check(!StormProgress.of(l, GROUP_B).eastDoorOpen(0) && !GroupGates.isOpen(l, GROUP_B, door), "the first bone door opened for another group", f);
        StormRods.ground(rod, 40);
        check(StormRods.isGrounded(rod) && StormRods.groundedTicks(rod) > 0, "grounding the rod", f);
        // 予告線: 低い線は跳べば当たらない、高い線はしゃがめば当たらない
        double fl = BONE_FLOOR + 1;
        check(StormEast.hits(fl, 1.8, false) && !StormEast.hits(fl + 0.8, 1.8, false), "low line: jumping should dodge", f);
        check(StormEast.hits(fl, 1.8, true) && !StormEast.hits(fl, 1.5, true), "high line: crouching should dodge", f);
        StormStrike.cancelAll(l);
        fresh(l, GROUP_A);
        StormEast.applyNeutral(l);
    }

    // ---------------------------------------------------------------- 横風
    private static void checkWind(ServerLevel l, List<String> f) {
        StormBackClock.force(StormBackClock.Phase.CALM);
        check(StormWest.gust(l) == 0, "no crosswind in calm", f);
        check(StormBackClock.liftHeight(l) == StormBackClock.CALM_LIFT, "calm vent height", f);
        StormBackClock.force(StormBackClock.Phase.STORM);
        check(StormBackClock.liftHeight(l) == StormBackClock.STORM_LIFT, "storm vent height", f);
        StormBackClock.force(null);
    }

    // ---------------------------------------------------------------- 歩いて行ける所 (あるグループの人として)
    // 歩くグループ (扉の開け閉めはこのグループの記録で見る)
    private static UUID walker = GROUP_A;

    static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (s.is(ModBlocks.SEALED_GATE.get())) {
            // 試練の入口の戸は順路の今の段だけに開く (docs/design/32)。順路は入口の休み場へ運ぶので、歩いて届くかはその戸を開いたものとして見る
            return GroupGates.isOpen(l, walker, p) || jp.revati.skyabyss.trial.TrialRuntime.isTrialEntrance(p);
        }
        return s.getCollisionShape(l, p).isEmpty();
    }

    static boolean standable(ServerLevel l, BlockPos p) {
        if (!passable(l, p) || !passable(l, p.above())) {
            return false;
        }
        BlockPos b = p.below();
        BlockState below = l.getBlockState(b);
        if (below.is(ModBlocks.SEALED_GATE.get())) {
            return !GroupGates.isOpen(l, walker, b);
        }
        return !below.getCollisionShape(l, b).isEmpty();
    }

    // 昇り風の穴 (中心の床、上がる高さ)
    record Lift(BlockPos floor, double height) {
    }

    static List<Lift> lifts(ServerLevel l, boolean storm) {
        StormProgress state = StormProgress.of(l, walker);
        double h = storm ? StormBackClock.STORM_LIFT : StormBackClock.CALM_LIFT;
        List<Lift> out = new ArrayList<>();
        for (StormWest.Vent v : StormWest.vents()) {
            out.add(new Lift(v.floor(), v.fixedHeight() >= 0 ? v.fixedHeight() : h));
        }
        if (state.raijuVent()) {
            out.add(new Lift(RAIJU_VENT, EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1));
        }
        return out;
    }

    // 到着から歩いて (跳んで 1 段、落ちて 3 段。西の骨の中は風切りの羽根で滑空するので何段でも落ちられる)、昇り風の穴にも乗って行ける所
    static Map<Long, Integer> walk(ServerLevel l, UUID group, BlockPos start, boolean storm) {
        walker = group;
        List<Lift> lifts = lifts(l, storm);
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        dist.put(start.asLong(), 0);
        q.add(start);
        while (!q.isEmpty() && dist.size() < 600000) {
            BlockPos p = q.poll();
            int d = dist.get(p.asLong());
            List<BlockPos> next = new ArrayList<>();
            boolean glide = StormWest.inBone(p.getCenter());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(dir);
                int maxFall = glide ? 40 : 3;
                for (int dy = 1; dy >= -maxFall; dy--) {
                    BlockPos m = n.above(dy);
                    if (dy == 1 && !passable(l, p.above(2))) {
                        continue;
                    }
                    if (dy < 0 && !(passable(l, n) && passable(l, n.above()) && passable(l, m))) {
                        break;
                    }
                    if (standable(l, m)) {
                        next.add(m);
                        break;
                    }
                }
            }
            // 昇り風: 穴の上 (中心から 1 マス以内) に立っていれば、穴の上の柱の空いている所から、横 3 マス以内の立てる所へ
            for (Lift lf : lifts) {
                BlockPos c = lf.floor();
                if (p.getY() == c.getY() + 1 && Math.abs(p.getX() - c.getX()) <= 1 && Math.abs(p.getZ() - c.getZ()) <= 1) {
                    int top = c.getY() + 1 + (int) Math.floor(lf.height());
                    for (int y = c.getY() + 1; y <= top; y++) {
                        if (!passable(l, new BlockPos(p.getX(), y + 1, p.getZ()))) {
                            top = y;
                            break;
                        }
                    }
                    for (int y = c.getY() + 2; y <= top + 1; y++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            for (int dz = -3; dz <= 3; dz++) {
                                BlockPos m = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                                if (standable(l, m)) {
                                    next.add(m);
                                }
                            }
                        }
                    }
                }
            }
            for (BlockPos m : next) {
                if (m.getY() < 105 || m.getY() > 235 || Math.abs(m.getX()) > 330 || Math.abs(m.getZ()) > 200) {
                    continue;
                }
                if (!dist.containsKey(m.asLong())) {
                    dist.put(m.asLong(), d + 1);
                    q.add(m);
                }
            }
        }
        return dist;
    }

    private static void expect(Map<Long, Integer> dist, ServerLevel l, String name, BlockPos g, boolean should, List<String> f) {
        boolean can = dist.containsKey(g.asLong());
        if (can != should) {
            String extra = "";
            if (!can) {
                BlockPos best = null;
                double bd = Double.MAX_VALUE;
                for (long k : dist.keySet()) {
                    BlockPos q = BlockPos.of(k);
                    double dd = q.distSqr(g);
                    if (dd < bd) {
                        bd = dd;
                        best = q;
                    }
                }
                extra = " (closest " + best + ", standable " + standable(l, g) + ")";
            }
            f.add((should ? "cannot reach " : "can reach ") + name + " " + g + extra);
        }
    }

    private static void checkWalk(ServerLevel l, List<String> f, List<String> notes) {
        StormProgress state = fresh(l, GROUP_A);
        StormProgress other = fresh(l, GROUP_B);
        // 凪、戸は閉じたまま
        Map<Long, Integer> calm = walk(l, GROUP_A, ARRIVAL, false);
        Map<String, BlockPos> yes = new LinkedHashMap<>();
        for (String id : List.of("tail", "hub", "w0", "wm", "w1", "e0", "f0")) {
            yes.put("rest " + id, rest(id));
        }
        yes.put("gallery", new BlockPos(0, UPPER_GALLERY_Y + 1, GALLERY.z1() + 3));
        yes.put("rib floor", new BlockPos(RIB_X2 - 1, BONE_FLOOR + 1, WZ));
        yes.put("west vault cache spot", VAULT_CACHE_W.offset(0, 0, 3));
        yes.put("crown hall", new BlockPos(0, F0.floor() + 1, (F0.z1() + F0.z2()) / 2 + 3));
        for (String id : List.of("w2", "w3", "w4")) {
            yes.put("rest " + id, rest(id));
        }
        yes.put("tail hall", TAIL_CENTER);
        yes.put("eye hall", EYE_HALL_CENTER.offset(1, 0, 6));
        for (Map.Entry<String, BlockPos> e : yes.entrySet()) {
            expect(calm, l, e.getKey() + " (calm)", e.getValue(), true, f);
        }
        // どの拍でも行ける (25 の 2章): 岩棚の昇り風の穴は拍によらず岩棚まで届く
        expect(calm, l, "west stone ledge in calm", WEST_STONE.offset(1, 0, 0), true, f);
        expect(calm, l, "rest e1 with closed doors", rest("e1"), false, f);
        expect(calm, l, "east perch before the raiju", EAST_STONE.offset(-1, 0, 0), false, f);
        expect(calm, l, "beak room before the mirrors", FRONT_STONE.offset(1, 0, 0), false, f);
        expect(calm, l, "keel antechamber before the stones", rest("heart"), false, f);
        Integer toHub = calm.get(rest("hub").asLong());
        notes.add("arrival to hall " + toHub + " steps");
        check(toHub != null && toHub <= 260, "the wind hall is too far from the arrival (" + toHub + " steps)", f);
        // 嵐: 西の岩棚へ届く
        Map<Long, Integer> storm = walk(l, GROUP_A, ARRIVAL, true);
        expect(storm, l, "west stone ledge in storm", WEST_STONE.offset(1, 0, 0), true, f);
        expect(storm, l, "gallery in storm (by the hub updraft)", new BlockPos(0, UPPER_GALLERY_Y + 1, GALLERY.z2() + 1), true, f);
        // 骨の戸が開くと、肘・雷獣の巣へ。雷獣のあとは頂の台へ (A だけ)
        for (int i = 0; i < EAST_DOORS.length; i++) {
            state.openEastDoor(i);
        }
        state.setRaijuDefeated(true);
        StormBackTrials.syncGates(l, GROUP_A);
        Map<Long, Integer> open = walk(l, GROUP_A, ARRIVAL, false);
        expect(open, l, "rest e1 after the doors", rest("e1"), true, f);
        expect(open, l, "raiju nest after the doors", RAIJU_NEST.offset(-6, 1, 0), true, f);
        expect(open, l, "east perch after the raiju", EAST_STONE.offset(-1, 0, 0), true, f);
        // 鏡を解く (戸を開ける) と嘴の小部屋へ。三つの石で竜骨の門が開くと、前室と竜骨の間へ
        state.setReceivers(7);
        state.setKeelOpen(true);
        StormBackTrials.syncGates(l, GROUP_A);
        Map<Long, Integer> all = walk(l, GROUP_A, ARRIVAL, false);
        expect(all, l, "beak room after the mirrors", FRONT_STONE.offset(1, 0, 0), true, f);
        expect(all, l, "keel antechamber", rest("heart"), true, f);
        expect(all, l, "heart room", HEART_ROOM.offset(0, 2, 8), true, f);
        notes.add("standing spots " + all.size());
        // 同じ背中の、何も進めていないグループ B には、戸も竜骨の門も閉じたまま
        check(other.openDoorCount() == 0 && !other.keelOpen(), "group B's record changed while A played", f);
        Map<Long, Integer> b = walk(l, GROUP_B, ARRIVAL, false);
        expect(b, l, "rest e1 for group B (doors closed for B)", rest("e1"), false, f);
        expect(b, l, "beak room for group B", FRONT_STONE.offset(1, 0, 0), false, f);
        expect(b, l, "keel antechamber for group B", rest("heart"), false, f);
        expect(b, l, "rest hub for group B", rest("hub"), true, f);
        fresh(l, GROUP_A);
        fresh(l, GROUP_B);
        walker = GROUP_A;
    }

    // ---------------------------------------------------------------- リセットと眠る試練 (グループごと)
    private static void checkReset(ServerLevel l, List<String> f) {
        StormProgress state = fresh(l, GROUP_A);
        StormProgress other = fresh(l, GROUP_B);
        other.setRung(StormBackState.Trial.EAST, true);
        int epochB = other.epoch();
        int epoch = state.epoch();
        state.setRung(StormBackState.Trial.WEST, true);
        state.setKeelOpen(true);
        StormBackTrials.syncGates(l, GROUP_A);
        check(GroupGates.isOpen(l, GROUP_A, new BlockPos(0, HUB.floor(), HUB_CENTER.getZ())), "the keel gate did not open for the group", f);
        check(state.needsReset() && !StormProgress.of(l, UUID.randomUUID()).needsReset(), "needsReset is wrong", f);
        StormBackTrials.resetGroup(l, GROUP_A, false, null, true, false);
        state = StormProgress.of(l, GROUP_A);
        check(state.epoch() != epoch, "reset should advance the group's trial number", f);
        check(state.rungCount() == 0 && !state.keelOpen() && state.receivers() == 0, "reset should clear the stones, keel and receivers", f);
        check(l.getBlockState(new BlockPos(0, HUB.floor(), HUB_CENTER.getZ())).is(ModBlocks.SEALED_GATE.get()) && !GroupGates.isOpen(l, GROUP_A, new BlockPos(0, HUB.floor(), HUB_CENTER.getZ())),
                "the keel gate should be closed after reset", f);
        check(other.epoch() == epochB && other.rung(StormBackState.Trial.EAST), "resetting group A touched group B's record", f);
        check(state.mirrorConfig() >= 1, "the next round should draw a harder mirror config (" + state.mirrorConfig() + ")", f);
        // 試練は眠らない (25 の 2章): 心臓を倒したグループでも、リセットのあと三つとも鳴っていない・眠っていない
        state.markEverDefeated();
        for (int i = 0; i < 12; i++) {
            state.reset(l.random, null, false);
            check(state.sleeping() == null && state.rungCount() == 0, "a trial slept or rang by itself at reset " + i, f);
            check(state.everDefeated(), "the heart record of the group was lost by a reset", f);
        }
        // 東が眠ると、そのグループにだけ骨の戸が開いている
        StormBackTrials.resetGroup(l, GROUP_A, false, StormBackState.Trial.EAST, false, false);
        BlockPos door = new BlockPos(EAST_DOORS[1], BONE_FLOOR + 2, WZ);
        check(GroupGates.isOpen(l, GROUP_A, door) && !GroupGates.isOpen(l, GROUP_B, door), "east sleeping should open the bone doors for that group only", f);
        // まっさらな記録に戻す (心臓の記録も。自己テストのグループだけ)
        BackGroupData.get(l).remove(GROUP_A);
        BackGroupData.get(l).remove(GROUP_B);
        fresh(l, GROUP_A);
        fresh(l, GROUP_B);
    }

    // ---------------------------------------------------------------- 目的の板 (25 の 3章)
    private static void checkGuide(ServerLevel l, List<String> f) {
        for (String lang : SignFit.LANGUAGES) {
            Map<String, String> m = SignFit.lang(lang);
            for (String id : StormGuide.GOAL_IDS) {
                check(m.containsKey("gui.skyabyss.storm_goal." + id), "goal text " + id + " is missing in " + lang, f);
            }
            for (String t : List.of("west", "east", "front", "hub")) {
                check(m.containsKey("gui.skyabyss.storm_goal.title." + t), "goal title " + t + " is missing in " + lang, f);
            }
            for (String id : StormObjective.IDS) {
                check(m.containsKey("objective.skyabyss.trial_" + id) || SignFit.lang(lang).containsKey("objective.skyabyss.trial_" + id), "objective text " + id + " is missing in " + lang, f);
            }
            for (StormRoomTitles.Room room : StormRoomTitles.ROOMS) {
                check(m.containsKey("message.skyabyss.storm.room." + room.id()), "room title " + room.id() + " is missing in " + lang, f);
            }
        }
        StormProgress a = fresh(l, GROUP_A);
        StormTestPlayer pa = StormTestPlayer.join(l, GROUP_A, "[storm_g]", net.minecraft.world.phys.Vec3.atBottomCenterOf(rest("hub")));
        try {
            check(StormGuide.goalFor(l, pa).id().equals("hub.stones"), "hub goal should be hub.stones, was " + StormGuide.goalFor(l, pa).id(), f);
            Object[][] spots = {
                    {-75, BONE_FLOOR + 1, WZ, "west.gap", 0}, {-95, BONE_FLOOR + 1, WZ, "west.gap", 1}, {-130, BONE_FLOOR + 1, WZ, "west.rib", 2},
                    {100, BONE_FLOOR + 1, WZ, "east.circuit", 0}, {0, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ(), "front.mirror", 0}};
            for (Object[] sp : spots) {
                pa.moveTo((int) sp[0] + 0.5, (int) sp[1], (int) sp[2] + 0.5, 0f, 0f);
                StormGuide.Goal g = StormGuide.goalFor(l, pa);
                check(g.id().equals(sp[3]), "goal at " + sp[0] + "," + sp[1] + "," + sp[2] + " should be " + sp[3] + ", was " + g.id(), f);
                check(g.total() > 0 && g.done() >= 0 && g.done() <= g.total(), "goal pips are wrong at " + sp[3] + ": " + g.done() + "/" + g.total(), f);
                if (sp[3].toString().startsWith("west.gap")) {
                    check(g.a() == (int) sp[4], "gaps passed at x " + sp[0] + " should be " + sp[4] + ", was " + g.a(), f);
                }
            }
            a.setRibLayer(RIB_LAYERS);
            pa.moveTo(WEST_STONE.getX() + 0.5, WEST_STONE.getY(), WEST_STONE.getZ() + 0.5, 0f, 0f);
            check(StormGuide.goalFor(l, pa).id().equals("west.stone"), "west goal at the top should be west.stone, was " + StormGuide.goalFor(l, pa).id(), f);
            // 進みの点: 石が鳴ると満点、受け羽・戸で増える
            a.setRung(StormBackState.Trial.FRONT, true);
            pa.moveTo(0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 0.5, 0f, 0f);
            StormGuide.Goal g = StormGuide.goalFor(l, pa);
            check(g.id().equals("front.done") && g.done() == g.total(), "front goal after ringing: " + g, f);
            a.setRung(StormBackState.Trial.FRONT, false);
            a.setReceivers(7);
            g = StormGuide.goalFor(l, pa);
            check(g.id().equals("front.stone") && g.done() == 3, "front goal with all receivers: " + g, f);
            a.openEastDoor(0);
            a.openEastDoor(1);
            pa.moveTo(130.5, BONE_FLOOR + 1, WZ + 0.5, 0f, 0f);
            g = StormGuide.goalFor(l, pa);
            check(g.id().equals("east.circuit") && g.done() == 2 && g.a() == 2, "east goal with two doors: " + g, f);
            a.openEastDoor(2);
            check(StormGuide.goalFor(l, pa).id().equals("east.circuit"), "east goal with three of four doors should still be east.circuit", f);
            a.openEastDoor(3);
            check(StormGuide.goalFor(l, pa).id().equals("east.raiju"), "east goal with all doors should be east.raiju", f);
            a.setRaijuDefeated(true);
            check(StormGuide.goalFor(l, pa).id().equals("east.stone"), "east goal after the raiju should be east.stone", f);
            checkObjective(l, a, pa, f);
            checkCaches(pa, f);
        } finally {
            pa.leave();
            fresh(l, GROUP_A);
        }
    }

    // ---------------------------------------------------------------- 目的の光の柱 (StormObjective): いる場所と進みから、次にすること一つ
    private static void expectGoal(ServerLevel l, StormTestPlayer pa, double x, double y, double z, String id, List<String> f) {
        pa.moveTo(x, y, z, 0f, 0f);
        StormObjective.Goal g = StormObjective.next(l, pa);
        check(g != null && g.id().equals(id), "objective at " + x + "," + y + "," + z + " should be " + id + ", was " + (g == null ? "none" : g.id()), f);
    }

    private static void checkObjective(ServerLevel l, StormProgress a, StormTestPlayer pa, List<String> f) {
        StormObjective.IDS.forEach(id -> check(!id.isEmpty(), "empty objective id", f));
        a = fresh(l, GROUP_A);
        StormBackClock.force(StormBackClock.Phase.STORM);
        // 道の流れ: 尾羽の上 → 門 → 尾の間 → 広間
        expectGoal(l, pa, ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, "storm_gate", f);
        expectGoal(l, pa, PLAZA_CENTER.getX() + 0.5, PLAZA_CENTER.getY(), PLAZA_CENTER.getZ() + 0.5, "storm_gate", f);
        expectGoal(l, pa, TAIL_CENTER.getX() + 0.5, TAIL_CENTER.getY(), TAIL_CENTER.getZ() + 0.5, "storm_hall", f);
        // 広間: いちばん近い、まだの試練の入口 (休み場の西側からは西)。嵐なら前は昇り風、凪なら塔
        expectGoal(l, pa, rest("hub").getX() + 0.5, rest("hub").getY(), rest("hub").getZ() + 0.5, "storm_west", f);
        a.setRung(StormBackState.Trial.WEST, true);
        pa.moveTo(rest("hub").getX() + 0.5, rest("hub").getY(), rest("hub").getZ() + 0.5, 0f, 0f);
        StormObjective.Goal g = StormObjective.next(l, pa);
        check(g != null && (g.id().equals("storm_front_vent") || g.id().equals("storm_east")), "objective in the hall with the west rung should point to east or front, was " + (g == null ? "none" : g.id()), f);
        StormBackClock.force(StormBackClock.Phase.CALM);
        a.setRung(StormBackState.Trial.EAST, true);
        expectGoal(l, pa, rest("hub").getX() + 0.5, rest("hub").getY(), rest("hub").getZ() + 0.5, "storm_front_tower", f);
        pa.moveTo(0.5, UPPER_GALLERY_Y + 1, GALLERY.z1() + 2.5, 0f, 0f);
        g = StormObjective.next(l, pa);
        check(g != null && g.id().equals("storm_front_neck"), "objective on the gallery should be the neck, was " + (g == null ? "none" : g.id()), f);
        a.setRung(StormBackState.Trial.FRONT, true);
        expectGoal(l, pa, rest("hub").getX() + 0.5, rest("hub").getY(), rest("hub").getZ() + 0.5, "storm_keel", f);
        a = fresh(l, GROUP_A);
        // 西: 割れた床 → 肋の間 → 石 → 戻る
        expectGoal(l, pa, W0.x1() + 3.5, BONE_FLOOR + 1, WZ + 0.5, "storm_west_gap", f);
        expectGoal(l, pa, WEST_GAPS[0][0] - 8.5, BONE_FLOOR + 1, WZ + 0.5, "storm_west_gap", f);
        expectGoal(l, pa, WEST_GAPS[1][0] - 12.5, BONE_FLOOR + 1, WZ + 0.5, "storm_west_rib", f);
        a.setRibLayer(RIB_LAYERS);
        expectGoal(l, pa, WEST_GAPS[1][0] - 12.5, BONE_FLOOR + 1, WZ + 0.5, "storm_west_stone", f);
        a.setRung(StormBackState.Trial.WEST, true);
        expectGoal(l, pa, WEST_GAPS[1][0] - 12.5, BONE_FLOOR + 1, WZ + 0.5, "storm_back_w", f);
        a = fresh(l, GROUP_A);
        // 東: レバー → 金の床 → 次の回路 → 雷獣 → 石 → 戻る
        expectGoal(l, pa, 80.5, BONE_FLOOR + 1, WZ + 0.5, "storm_east_lever", f);
        for (BlockPos lever : StormCircuit.solution(StormCircuit.CIRCUITS.get(0))) {
            StormCircuit.setLever(l, lever, true);
        }
        expectGoal(l, pa, 80.5, BONE_FLOOR + 1, WZ + 0.5, "storm_east_pad", f);
        for (BlockPos lever : StormCircuit.solution(StormCircuit.CIRCUITS.get(0))) {
            StormCircuit.setLever(l, lever, false);
        }
        for (int i = 0; i < EAST_DOORS.length; i++) {
            a.openEastDoor(i);
        }
        expectGoal(l, pa, 80.5, BONE_FLOOR + 1, WZ + 0.5, "storm_east_raiju", f);
        a.setRaijuDefeated(true);
        expectGoal(l, pa, 80.5, BONE_FLOOR + 1, WZ + 0.5, "storm_east_stone", f);
        a.setRung(StormBackState.Trial.EAST, true);
        expectGoal(l, pa, 80.5, BONE_FLOOR + 1, WZ + 0.5, "storm_back_e", f);
        a = fresh(l, GROUP_A);
        // 前: 光を導く → 戸が開けば嘴の石 → 戻る。竜骨の前室は心臓へ
        expectGoal(l, pa, 0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 0.5, "storm_front_mirror", f);
        a.setReceivers(7);
        expectGoal(l, pa, 0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 0.5, "storm_front_stone", f);
        a.setRung(StormBackState.Trial.FRONT, true);
        expectGoal(l, pa, 0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 0.5, "storm_back_f", f);
        expectGoal(l, pa, rest("heart").getX() + 0.5, rest("heart").getY(), rest("heart").getZ() + 0.5, "storm_heart", f);
        StormBackClock.force(null);
        fresh(l, GROUP_A);
    }

    // 書庫と炉の宝箱: 一人に一度だけ、小さな贈り物 (食べ物・矢・経験値)
    private static void checkCaches(StormTestPlayer pa, List<String> f) {
        pa.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        int before = pa.getInventory().countItem(jp.revati.skyabyss.registry.ModItems.CLOUD_JERKY.get());
        StormCaches.open(pa, -2);
        int once = pa.getInventory().countItem(jp.revati.skyabyss.registry.ModItems.CLOUD_JERKY.get());
        StormCaches.open(pa, -2);
        int twice = pa.getInventory().countItem(jp.revati.skyabyss.registry.ModItems.CLOUD_JERKY.get());
        check(once - before == 5 && twice == once, "the west vault chest should give once (" + before + " -> " + once + " -> " + twice + ")", f);
        StormCaches.open(pa, 2);
        check(StormCaches.taken(pa, -2) && StormCaches.taken(pa, 2), "the vault chests should give once each", f);
        pa.getPersistentData().remove(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- どの拍でも試練に行ける・終えられる (25 の 2章)
    private static void checkNoStateGates(ServerLevel l, List<String> f) {
        // どの拍でも、西は肋の間を一段ずつ登れる (凪の昇り風 4 = 梁の段差)・東は回路を組める (StormReworkSelfTest が回路を両方の拍で通す)
        check(StormBackClock.CALM_LIFT >= RIB_HOP, "the calm vent must lift at least one rib hop", f);
        StormReworkSelfTest.run(l, f);
        StormBackClock.force(null);
        // 試練は自分では眠らない: 心臓を一度倒した記録でも、リセットのあと眠る試練がない (checkReset でも見る)
        StormProgress a = fresh(l, GROUP_A);
        a.markEverDefeated();
        a.reset(l.random, null, false);
        check(a.sleeping() == null && a.rungCount() == 0, "a trial sleeps by itself after the heart was defeated", f);
        fresh(l, GROUP_A);
    }

    // ---------------------------------------------------------------- 二つのグループが同じ背中で
    // A が骨の戸を開け・石を鳴らしても、B には閉じたまま (扉の当たり判定もその人のグループで決まる)。
    // A が眼の間・東の回廊を持っているあいだ、B は入れない (外の休み場へ戻される)。パーティが変わった人は広間へ
    private static void checkGroups(ServerLevel l, List<String> f) {
        StormProgress a = fresh(l, GROUP_A);
        StormProgress b = fresh(l, GROUP_B);
        RoomLocks.release(StormBackTrials.EYE_ROOM);
        RoomLocks.release(StormBackTrials.EAST_ROOM);
        StormTestPlayer pa = StormTestPlayer.join(l, GROUP_A, "[storm_a]", net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(75, BONE_FLOOR + 1, WZ)));
        StormTestPlayer pb = StormTestPlayer.join(l, GROUP_B, "[storm_b]", net.minecraft.world.phys.Vec3.atBottomCenterOf(rest("hub")));
        try {
            check(jp.revati.skyabyss.titan.group.Groups.keyOf(pa).equals(GROUP_A) && jp.revati.skyabyss.titan.group.Groups.keyOf(pb).equals(GROUP_B),
                    "test players are not in their own groups", f);
            check(l.players().contains(pa) && l.players().contains(pb), "test players did not join the storm back", f);
            // 東の回廊: A が入って持ち主に。A の柱を満たすと A にだけ戸が開く
            StormBackTrials.tickRooms(l);
            check(GROUP_A.equals(RoomLocks.owner(StormBackTrials.EAST_ROOM)), "group A does not own the east corridor (" + RoomLocks.owner(StormBackTrials.EAST_ROOM) + ")", f);
            StormRods.charge(l, EAST_RODS.get(0), StormRods.FULL);
            StormEast.openFullDoors(l, a);
            BlockPos door = new BlockPos(EAST_DOORS[0], BONE_FLOOR + 2, WZ);
            BlockState gate = l.getBlockState(door);
            check(gate.is(ModBlocks.SEALED_GATE.get()) && !gate.getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN), "the bone door block should stay closed by default", f);
            check(GroupGates.effective(l, GROUP_A, door, false) && !GroupGates.effective(l, GROUP_B, door, false), "the bone door override is wrong", f);
            check(!blocksFor(l, door, pa) && blocksFor(l, door, pb), "the bone door collision should let A through and stop B", f);
            check(!b.eastDoorOpen(0), "group B's bone door opened", f);
            // B が回廊に入ると、肩の門の休み場へ戻される
            pb.moveTo(80.5, BONE_FLOOR + 1, WZ + 0.5);
            StormBackTrials.tickRooms(l);
            check(!StormEast.room().contains(pb.position()), "group B was not sent out of the corridor A holds (at " + pb.position() + ")", f);
            check(GROUP_A.equals(RoomLocks.owner(StormBackTrials.EAST_ROOM)), "group B took the east corridor from A", f);
            // 眼の間: A が持っているあいだ、B は入れない。B は鏡を回せない
            pa.moveTo(EYE_HALL_CENTER.getX() + 0.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 6.5);
            StormBackTrials.tickRooms(l);
            check(GROUP_A.equals(RoomLocks.owner(StormBackTrials.EYE_ROOM)), "group A does not own the eye hall", f);
            pb.moveTo(EYE_HALL_CENTER.getX() + 2.5, EYE_HALL.floor() + 1, EYE_HALL_CENTER.getZ() + 4.5);
            StormBackTrials.tickRooms(l);
            check(!StormBackTrials.eyeRoom().contains(pb.position()), "group B was not sent out of the eye hall A holds (at " + pb.position() + ")", f);
            int[] mc = a.config().mirrors().get(0);
            BlockPos mirror = gridCell(mc[0], mc[1]);
            int before = l.getBlockState(mirror).getValue(StormBlocks.Mirror.ROT);
            EyeMirrors.turn(l, mirror, pb);
            check(l.getBlockState(mirror).getValue(StormBlocks.Mirror.ROT) == before && !b.record.has(StormProgress.MIRROR_ROTS),
                    "group B turned a mirror in the hall A holds", f);
            EyeMirrors.turn(l, mirror, pa);
            check(l.getBlockState(mirror).getValue(StormBlocks.Mirror.ROT) == (before + 1) % 4 && a.mirrorRots()[0] == (before + 1) % 4,
                    "group A could not turn a mirror in its hall", f);
            // 風鳴り石: A が鳴らすと A の見かけと記録だけ
            StormBackTrials.ring(l, a, StormBackState.Trial.WEST, null);
            BlockPos stone = StormBackTrials.stonePos(StormBackState.Trial.WEST);
            BlockState shownA = jp.revati.skyabyss.titan.group.GroupOverlays.compute(l, GROUP_A).get(stone);
            check(a.rung(StormBackState.Trial.WEST) && !b.rung(StormBackState.Trial.WEST), "ringing the west stone for A changed B", f);
            check(shownA != null && jp.revati.skyabyss.trial.TrialGoal.isReached(shownA), "A does not see its rung stone", f);
            check(!jp.revati.skyabyss.titan.group.GroupOverlays.compute(l, GROUP_B).containsKey(stone), "B sees A's rung stone", f);
            check(!jp.revati.skyabyss.trial.TrialGoal.isReached(l.getBlockState(stone)), "the world stone should stay silent (the rung look is per group)", f);
            // 竜骨の門: A の三つの石がそろうと A にだけ開く
            a.setRung(StormBackState.Trial.EAST, true);
            a.setRung(StormBackState.Trial.FRONT, true);
            StormBackTrials.openKeel(l, a);
            BlockPos keel = new BlockPos(0, HUB.floor(), HUB_CENTER.getZ());
            check(!blocksFor(l, keel, pa) && blocksFor(l, keel, pb), "the keel gate collision should let A through and stop B", f);
            // 招雷: A の雷は B に当たらない
            pb.moveTo(pa.getX(), pa.getY(), pa.getZ());
            float hb = pb.getHealth();
            StormStrike.Strike s = StormStrike.call(l, pa.position(), 1, 4f, null, GROUP_A);
            StormStrike.tick(l);
            check(s.landed() && !s.hit.contains(pb) && pb.getHealth() == hb, "group A's lightning hit group B", f);
            // パーティが変わった人: 試練の中なら広間へ、休み場の記録は番号が違えば消える
            pa.moveTo(rest("w0").getX() + 0.5, rest("w0").getY(), rest("w0").getZ() + 0.5);
            StormBackTrials.writeCheckpoint(pa, "w0", a.epoch() + 100);
            jp.revati.skyabyss.titan.group.GroupEvents.membershipChanged(pa);
            check(pa.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(rest("hub"))) < 4, "a player whose party changed was not moved to the hall", f);
            check(StormBackTrials.validCheckpoint(StormBackTrials.persisted(pa), a.epoch()).isEmpty()
                    && !StormBackTrials.persisted(pa).contains("skyabyss_storm_checkpoint"), "the old rest point was kept after the party changed", f);
        } finally {
            pa.leave();
            pb.leave();
            StormStrike.cancelAll(l);
            RoomLocks.release(StormBackTrials.EYE_ROOM);
            RoomLocks.release(StormBackTrials.EAST_ROOM);
            StormEast.applyNeutral(l);
            EyeMirrors.applyNeutral(l);
            BackGroupData.get(l).remove(GROUP_A);
            BackGroupData.get(l).remove(GROUP_B);
        }
    }

    // ---------------------------------------------------------------- パーティを組む・入る・抜ける (記録の引き継ぎ。6 人まで)
    private static void checkParty(ServerLevel l, List<String> f) {
        MinecraftServer server = l.getServer();
        check(jp.revati.skyabyss.party.Parties.MAX_MEMBERS == 6, "a party should hold 6", f);
        UUID leader = UUID.fromString("5c1e0a2b-0000-4000-8000-00000057c003");
        UUID mate = UUID.fromString("5c1e0a2b-0000-4000-8000-00000057c004");
        jp.revati.skyabyss.party.PartyData parties = jp.revati.skyabyss.party.PartyData.get(server);
        parties.remove(leader);
        parties.remove(mate);
        StormProgress lead = fresh(l, leader);
        StormProgress solo = fresh(l, mate);
        lead.setRung(StormBackState.Trial.WEST, true);
        solo.setRung(StormBackState.Trial.FRONT, true);
        jp.revati.skyabyss.party.PartyData.Party party = parties.create(leader, "[storm_lead]");
        try {
            StormProgress p = StormProgress.of(l, party.id);
            check(p.rung(StormBackState.Trial.WEST) && !p.rung(StormBackState.Trial.FRONT), "a new party should start from the leader's record", f);
            parties.add(party, mate, "[storm_mate]");
            check(jp.revati.skyabyss.titan.group.Groups.keyOf(server, mate).equals(party.id), "the joining player is not in the party's group", f);
            check(StormProgress.of(l, jp.revati.skyabyss.titan.group.Groups.keyOf(server, mate)).rung(StormBackState.Trial.WEST),
                    "the joining player does not use the party's record", f);
            p.setRung(StormBackState.Trial.EAST, true);
            parties.remove(mate);
            StormProgress after = StormProgress.of(l, mate);
            check(after.rung(StormBackState.Trial.WEST) && after.rung(StormBackState.Trial.EAST) && !after.rung(StormBackState.Trial.FRONT),
                    "a player leaving a party should keep a copy of the party's record", f);
            check(StormProgress.of(l, leader).rung(StormBackState.Trial.EAST), "the leader left alone should keep a copy of the party's record", f);
        } finally {
            parties.remove(leader);
            parties.remove(mate);
            BackGroupData.get(l).remove(party.id);
            BackGroupData.get(l).remove(leader);
            BackGroupData.get(l).remove(mate);
        }
    }

    // ---------------------------------------------------------------- ピースフル (腰の回廊を封じる。全員に同じ)
    private static void checkPeaceful(ServerLevel l, List<String> f) {
        Difficulty before = l.getDifficulty();
        BlockPos seal = new BlockPos(0, TAIL_HALL.floor() + 2, SEAL_Z);
        l.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        StormBackTrials.tickPeacefulForTest(l, true);
        check(l.getBlockState(seal).is(ModBlocks.SEALED_GATE.get()) && !GroupGates.isOpen(l, GROUP_A, seal) && !GroupGates.isOpen(l, GROUP_B, seal),
                "peaceful should seal the waist corridor for everyone", f);
        l.getServer().setDifficulty(before, true);
        StormBackTrials.tickPeacefulForTest(l, false);
        check(GroupGates.isOpen(l, GROUP_A, seal) && GroupGates.isOpen(l, GROUP_B, seal), "the seal should open again", f);
        check(l.getBlockState(seal).getCollisionShape(l, seal).isEmpty(), "the corridor should be passable after peaceful", f);
    }
}
