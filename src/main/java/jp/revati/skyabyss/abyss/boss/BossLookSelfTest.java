package jp.revati.skyabyss.abyss.boss;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.mobs.AbyssMobSpawner;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.story.ch5.depths.DepthBoss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

// 層の主・歪んだ王の見た目 (docs/design/15 の 9.7) の自己テスト。SKYABYSS_SELFTEST_ONLY="abyss boss looks"。
//   look の詰め方 (型・周・段)、9 型 × 周の素材 (メッシュ・アトラス・骨の geo・動き) と、動きの名前 (待機・歩き・殴り・段・倒れる・技ごとの溜めと当たり)、
//   体の敵が自分で流す動きの名前 (鼓動もどき・灯喰い・燠の番人・前の私の影)、体に look を入れると同期の値に入り、技の溜め・当たりが act に入ること。
public final class BossLookSelfTest {
    private static final List<String> F = new ArrayList<>();

    private BossLookSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        F.clear();
        try {
            packing();
            assets();
            live(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss boss looks crashed", e);
            F.add("crashed: " + e);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss boss looks PASSED (packing, 19 models' assets and animation names, body aliases, synced look/act)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST abyss boss looks FAILED");
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss boss looks FAILED: {}", x));
        }
    }

    private static void packing() {
        int l = BossLook.pack(BossLook.KING_SHADOW, 3, 2);
        check(BossLook.id(l) == BossLook.KING_SHADOW && BossLook.cycle(l) == 3 && BossLook.phase(l) == 2, "pack/unpack");
        check("king_shadow_c2".equals(BossLook.stem(l)), "cycle past 2 uses the third look: " + BossLook.stem(l));
        check("king_shadow".equals(BossLook.animStem(l)), "king anim stem");
        check("lord_salt".equals(BossLook.stem(BossLook.pack(BossLook.LORD_SALT, 0, 0))), "lord stem");
        check(DepthBoss.KINGS.length == 5, "five king types");
    }

    private static void assets() {
        String[] lords = {"lord_rib", "lord_salt", "lord_storm", "lord_ash"};
        String[] lordAct = {"snuff", "crush", "split", "roar"};
        String[] kings = {"king_bone", "king_salt", "king_storm", "king_ash", "king_shadow"};
        String[] common = {"idle", "walk", "attack", "phase", "death"};
        for (int i = 0; i < lords.length; i++) {
            files(lords[i]);
            String a = text("assets/skyabyss/animations/entity/" + lords[i] + ".animation.json");
            check(a != null, "animation missing: " + lords[i]);
            if (a != null) {
                for (String c : common) {
                    check(a.contains("\"animation." + lords[i] + "." + c + "\""), lords[i] + " lacks " + c);
                }
                check(a.contains("\"animation." + lords[i] + ".strike_" + lordAct[i] + "\""), lords[i] + " lacks strike_" + lordAct[i]);
            }
        }
        String salt = text("assets/skyabyss/animations/entity/lord_salt.animation.json");
        check(salt != null && salt.contains("\"animation.lord_salt.closed\""), "lord_salt lacks the closed-shell pose");
        alias("lord_storm", "heartbeat_mimic", "bite", "lure", "hurt", "death");
        alias("lord_ash", "lamp_eater", "snuff", "hurt", "death");
        alias("king_ash", "ember_keeper", "telegraph", "heat", "hurt", "death");
        alias("king_shadow", "past_shadow", "mirror", "hurt", "death");
        for (String k : kings) {
            for (int c = 0; c <= 2; c++) {
                files(k + "_c" + c);
            }
            String a = text("assets/skyabyss/animations/entity/" + k + ".animation.json");
            check(a != null, "animation missing: " + k);
            if (a != null) {
                for (String c : common) {
                    check(a.contains("\"animation." + k + "." + c + "\""), k + " lacks " + c);
                }
                for (DepthBoss.Move m : DepthBoss.Move.values()) {
                    String n = BossLook.actName(m.ordinal() + 1);
                    check(a.contains("\"animation." + k + ".windup_" + n + "\"") && a.contains("\"animation." + k + ".strike_" + n + "\""), k + " lacks " + n);
                }
            }
            String c0 = text("assets/skyabyss/lowpoly/" + k + "_c0.json");
            String c2 = text("assets/skyabyss/lowpoly/" + k + "_c2.json");
            check(c0 != null && c2 != null && !c0.equals(c2), k + ": cycle 2 mesh is not warped");
        }
    }

    private static void files(String stem) {
        check(res("assets/skyabyss/lowpoly/" + stem + ".json"), "mesh missing: " + stem);
        check(res("assets/skyabyss/textures/lowpoly/" + stem + ".png"), "atlas missing: " + stem);
        check(res("assets/skyabyss/geo/entity/" + stem + "_lowpoly.geo.json"), "skeleton missing: " + stem);
        check(res("assets/skyabyss/geo/entity/" + stem + "_lowpoly_debug.geo.json"), "debug skeleton missing: " + stem);
    }

    private static void alias(String anim, String body, String... acts) {
        String a = text("assets/skyabyss/animations/entity/" + anim + ".animation.json");
        if (a == null) {
            return;
        }
        for (String act : acts) {
            check(a.contains("\"animation." + body + "." + act + "\""), anim + " lacks the body action " + body + "." + act);
        }
    }

    private static void live(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Entity e = AbyssMobSpawner.spawnAt(level, DepthBoss.KINGS[0].body(), 0.5, level.getMinBuildHeight() + 2, 0.5, 3);
        check(e instanceof GuardMob, "king body did not spawn as a GuardMob");
        if (e instanceof GuardMob g) {
            BossLook.set(g, BossLook.pack(BossLook.KING_BONE, 1, 0));
            check(BossLook.stem(g.bossLook()).equals("king_bone_c1"), "look not stored");
            BossLook.windup(g, BossLook.SLAM, 36);
            check((g.bossAct() & 0xff) == BossLook.SLAM && ((g.bossAct() >> 8) & 0xff) == 36 && (g.bossAct() & (1 << 16)) == 0, "windup not stored");
            BossLook.strike(g, BossLook.SLAM);
            check((g.bossAct() & (1 << 16)) != 0, "strike not stored");
            BossLook.setPhase(g, 2);
            check(BossLook.phase(g.bossLook()) == 2 && BossLook.cycle(g.bossLook()) == 1, "phase not stored");
            BossLook.clear(g);
            check(g.bossAct() == 0, "act not cleared");
            g.discard();
        }
    }

    private static boolean res(String path) {
        return BossLookSelfTest.class.getClassLoader().getResource(path) != null;
    }

    private static String text(String path) {
        try (InputStream in = BossLookSelfTest.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }
}
