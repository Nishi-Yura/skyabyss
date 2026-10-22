package jp.revati.skyabyss.abyss.mobs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;

// 深淵の敵 A の自己テスト。SKYABYSS_SELFTEST_ONLY="abyss enemies a"。
//   登録 (型・スポーンエッグ)・素材 (三段のメッシュ・アトラス・発光・動きの型)・段の変わり方 (メッシュが違う)
//   湧かせ方の決まり (段・層・元の層・灰より下) ・数字の伸び (単調で緩やか) ・予告の長さ・塩の抜け殻の割れ・殻の窓・盾の正面
public final class AbyssMobsASelfTest {
    private static final List<String> F = new ArrayList<>();

    private AbyssMobsASelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        F.clear();
        try {
            registration();
            assets();
            stageMeshes();
            stats();
            timing();
            spawnRules();
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies a crashed", e);
            F.add("crashed: " + e);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss enemies a PASSED (registration, assets x 3 stages, stage meshes, stat curve, telegraph timing, ambush, shell window, shield, spawn rules)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies a FAILED");
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss enemies a FAILED: {}", x));
        }
    }

    private static void registration() {
        check(AbyssMobKind.values().length == 9, "kinds are not nine");
        List<String> ids = new ArrayList<>();
        for (AbyssMobKind k : AbyssMobKind.values()) {
            ids.add(k.id);
            check(AbyssMobsA.type(k) != null && AbyssMobsA.type(k).get() != null, "entity type missing: " + k.id);
            check(AbyssMobsA.egg(k) != null, "spawn egg missing: " + k.id);
        }
        check(ids.stream().distinct().count() == 9, "ids are not unique");
        for (int layer = 0; layer <= 2; layer++) {
            check(AbyssMobKind.nativeTo(layer).size() == 3, "layer " + layer + " does not have three kinds");
        }
    }

    private static void assets() {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            for (int s = 1; s <= 3; s++) {
                String stem = "abyss_" + k.id + "_s" + s;
                check(res("assets/skyabyss/lowpoly/" + stem + ".json"), "mesh missing: " + stem);
                check(res("assets/skyabyss/textures/lowpoly/" + stem + ".png"), "atlas missing: " + stem);
                check(res("assets/skyabyss/geo/entity/" + stem + "_lowpoly.geo.json"), "skeleton missing: " + stem);
                check(res("assets/skyabyss/textures/lowpoly/" + stem + "_glowmask.png") || s == 1, "glow mask missing: " + stem);
            }
            check(res("assets/skyabyss/animations/entity/abyss_" + k.id + "_s1.animation.json"), "animation missing: " + k.id);
        }
    }

    // 段 2・3 のメッシュは段 1 と違う (骨が伸びている)。骨格 (骨の名前) は同じ
    private static void stageMeshes() {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            String one = text("assets/skyabyss/lowpoly/abyss_" + k.id + "_s1.json");
            String three = text("assets/skyabyss/lowpoly/abyss_" + k.id + "_s3.json");
            check(one != null && three != null, "stage meshes unreadable: " + k.id);
            if (one != null && three != null) {
                check(!one.equals(three), "stage 3 mesh equals stage 1: " + k.id);
                check(one.contains("\"root\""), "root bone missing: " + k.id);
            }
        }
    }

    private static void stats() {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            double prevHp = 0;
            for (int s = 1; s <= 3; s++) {
                double prev = 0;
                for (int d = 0; d <= AbyssMobStats.DEPTH_MAX; d++) {
                    double hp = AbyssMobStats.hp(k, d, s);
                    double dmg = AbyssMobStats.damage(k, d, s);
                    check(hp >= prev, "hp not monotonic in depth: " + k.id + " s" + s + " d" + d);
                    check(dmg >= 0, "damage negative: " + k.id);
                    if (prev > 0) {
                        check(hp / prev <= 1.25, "hp step too big: " + k.id + " s" + s + " d" + d);
                    }
                    prev = hp;
                }
                check(prev >= prevHp, "hp not monotonic in stage: " + k.id + " s" + s);
                prevHp = AbyssMobStats.hp(k, AbyssMobStats.DEPTH_MAX, s);
            }
            double base = AbyssMobStats.hp(k, 0, 1);
            double top = AbyssMobStats.hp(k, AbyssMobStats.DEPTH_MAX, AbyssMobStats.STAGE_MAX);
            check(top / base <= 1.8, "stat curve too steep: " + k.id + " " + (top / base));
            check(AbyssMobStats.xp(k, 3) >= AbyssMobStats.xp(k, 1), "xp not monotonic: " + k.id);
        }
    }

    private static void timing() {
        int[] warns = {AbyssMobStats.WARN_RING, AbyssMobStats.WARN_SHADOW, AbyssMobStats.WARN_CALL, AbyssMobStats.WARN_CRACK};
        for (int w : warns) {
            check(w >= 20 && w <= 60, "telegraph length out of 1-3 s: " + w);
        }
        check(AbyssMobStats.SNARE_EVERY > AbyssMobStats.SNARE_TICKS, "snare shorter than its gap");
        check(AbyssMobStats.STRIKE_EVERY > AbyssMobStats.WARN_RING, "strike warn longer than its gap");
        check(AbyssMobStats.DIVE_EVERY > AbyssMobStats.WARN_SHADOW, "dive shadow longer than its gap");
        check(AbyssMobStats.CALL_EVERY > AbyssMobStats.WARN_CALL, "call warn longer than its gap");
    }

    private static void spawnRules() {
        // 塩の抜け殻: 4 ブロック以内で割れ、それより遠ければ固まったまま
        check(AbyssMobStats.ambushWakes(3.9), "husk does not wake at 3.9");
        check(!AbyssMobStats.ambushWakes(4.1), "husk wakes at 4.1");
        // 白い貝守: 殻の窓が一周 160 tick のうち 60 tick。閉じている間は一割だけ通す
        int open = 0;
        for (int t = 0; t < AbyssMobStats.SHELL_PERIOD; t++) {
            if (AbyssMobStats.shellOpen(t)) {
                open++;
            }
        }
        check(open == AbyssMobStats.SHELL_OPEN, "shell window length: " + open);
        check(AbyssMobStats.shellKeep(0) == 1.0, "shell open does not pass hits");
        check(AbyssMobStats.shellKeep(AbyssMobStats.SHELL_OPEN) == AbyssMobStats.SHELL_KEEP, "shell closed does not block");
        // 肋の番: 盾の正面からは一割、横と後ろは全部
        check(AbyssMobStats.shieldKeep(1.0) == AbyssMobStats.SHIELD_KEEP, "shield front does not block");
        check(AbyssMobStats.shieldKeep(0.0) == 1.0, "shield side blocked");
        check(AbyssMobStats.shieldKeep(-1.0) == 1.0, "shield back blocked");
        // 段と層
        check(AbyssMobStats.stage(0, 0) == 1, "stage of native layer is not 1");
        check(AbyssMobStats.stage(0, 1) == 2, "one layer deeper is not stage 2");
        check(AbyssMobStats.stage(0, 2) == 3, "two layers deeper is not stage 3");
        check(AbyssMobStats.stage(0, AbyssMobStats.DEPTH_MAX) == 3, "endless depth is not stage 3");
        check(AbyssMobStats.stage(2, 1) == 1, "a higher layer is not stage 1");
        check(AbyssMobStats.layerOf(120) == 0 && AbyssMobStats.layerOf(119) == 1, "bone/salt boundary");
        check(AbyssMobStats.layerOf(0) == 1 && AbyssMobStats.layerOf(-1) == 2, "salt/storm boundary");
        check(AbyssMobStats.layerOf(-120) == 2 && AbyssMobStats.layerOf(-121) == 3, "storm/ash boundary");
        check(AbyssMobStats.layerOf(-240) == 3 && AbyssMobStats.layerOf(-241) == AbyssMobStats.DEPTH_MAX, "ash/deep boundary");
        // 種の元の層の選び方 (AbyssMobSpawner.pickNative): 範囲の中・灰より下は三層から
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 200; i++) {
            int n0 = AbyssMobSpawner.pickNative(0, r);
            int n2 = AbyssMobSpawner.pickNative(2, r);
            int nd = AbyssMobSpawner.pickNative(AbyssMobStats.DEPTH_MAX, r);
            check(n0 == 0, "bone layer picks another native");
            check(n2 >= 0 && n2 <= 2, "storm layer picks out of range");
            check(nd >= 0 && nd <= 2, "deep picks out of range");
        }
    }

    private static boolean res(String path) {
        return AbyssMobsASelfTest.class.getClassLoader().getResource(path) != null;
    }

    private static String text(String path) {
        try (InputStream in = AbyssMobsASelfTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
