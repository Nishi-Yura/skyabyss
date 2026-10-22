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
//   段の決まり (層から段・元の層より上は段 1) ・数字の伸び (AbyssScaling。高さだけで決まり、なだらか) ・予告の長さ・塩の抜け殻の割れ・殻の窓・盾の正面
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

    // 数字 (AbyssScaling): 縦穴の上端で元の数字、心臓の間の底で体力 1.5 倍・傷 1.25 倍。高さに対して単調・なだらか
    private static void stats() {
        check(Math.abs(AbyssScaling.hpMul(AbyssScaling.TOP_Y) - 1) < 1e-9, "hp is not the base at the top of the shaft");
        check(Math.abs(AbyssScaling.dmgMul(AbyssScaling.TOP_Y) - 1) < 1e-9, "damage is not the base at the top of the shaft");
        check(Math.abs(AbyssScaling.hpMul(AbyssScaling.HEART_Y) - AbyssScaling.HP_AT_HEART) < 1e-9, "hp at the heart is not 1.5");
        check(Math.abs(AbyssScaling.dmgMul(AbyssScaling.HEART_Y) - AbyssScaling.DMG_AT_HEART) < 1e-9, "damage at the heart is not 1.25");
        check(AbyssScaling.hpMul(AbyssScaling.HEART_Y - 200) == AbyssScaling.HP_AT_HEART, "hp goes on past the heart");
        for (AbyssMobKind k : AbyssMobKind.values()) {
            double prev = 0;
            for (int y = AbyssScaling.TOP_Y; y >= AbyssScaling.HEART_Y; y -= 2) {
                double hp = AbyssScaling.hp(k.baseHp, y);
                double dmg = AbyssScaling.dmg(k.baseDamage, y);
                check(hp >= prev, "hp not monotonic in height: " + k.id + " y" + y);
                check(dmg >= 0, "damage negative: " + k.id);
                if (prev > 0) {
                    check(hp / prev - 1 <= 0.01, "hp step too big over 2 blocks: " + k.id + " y" + y);
                }
                prev = hp;
            }
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
        // 層の境 (AbyssScaling)
        check(AbyssScaling.layerOf(120) == 0 && AbyssScaling.layerOf(119) == 1, "bone/salt boundary");
        check(AbyssScaling.layerOf(0) == 1 && AbyssScaling.layerOf(-1) == 2, "salt/storm boundary");
        check(AbyssScaling.layerOf(-120) == 2 && AbyssScaling.layerOf(-121) == 3, "storm/ash boundary");
        check(AbyssScaling.layerOf(-240) == 3 && AbyssScaling.layerOf(-241) == AbyssScaling.LAYER_MAX, "ash/deep boundary");
        // 段 (形): 元の層で 1、一つ下で 2、二つ下以上で 3。元の層より上へは出ない (段 1)
        check(AbyssScaling.form(0, 200) == 1, "native layer is not form 1");
        check(AbyssScaling.form(0, 100) == 2, "one layer down is not form 2");
        check(AbyssScaling.form(0, -50) == 3, "two layers down is not form 3");
        check(AbyssScaling.form(0, -300) == 3, "deep is not form 3");
        check(AbyssScaling.form(2, 200) == 1, "a higher layer is not form 1");
        // 元の層の種が、その層で必ず段 1 になる (表の塩・雷の一つ上は段 2)
        for (AbyssMobKind k : AbyssMobKind.values()) {
            int y = k.native_ == 0 ? 200 : k.native_ == 1 ? 60 : -60;
            check(AbyssScaling.form(k.native_, y) == 1, "a native kind is not form 1 at home: " + k.id);
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
