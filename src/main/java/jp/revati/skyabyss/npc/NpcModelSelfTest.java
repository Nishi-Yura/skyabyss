package jp.revati.skyabyss.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.AttendantRole;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.server.MinecraftServer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// NPC v2 (人ごとの形。client/npc/NpcModels・tools/install/install_npc2.py) の素材の自己テスト。段: "lowpoly npc"。
//   都の係の人 (全役)・深淵の生き残り 4 人と荷運び・町の人 (全員)・旅の職人 (章ごとの絵 4 枚) に、体のメッシュ・絵・骨の geo・動き (idle / walk / talk) があること。
//   メッシュは水密の印 (mesh_close) があり、動きが使う骨 (body・head・armL/R・foreL/R・legL/R・hem・hairB・受け口) がそろっていること。
//   持ち物のメッシュ (npc_<id>_<受け口>.json) があれば、その絵があり、受け口のメッシュ (socket: true) であること。
public final class NpcModelSelfTest {
    static final List<String> EXTRA = List.of("survivor_bone", "survivor_salt", "survivor_storm", "survivor_ash", "abyss_porter");
    static final List<String> BONES = List.of("root", "body", "head", "hairB", "armL", "armR", "foreL", "foreR", "legL", "legR", "hem",
            "prop_r", "prop_l", "prop_back");

    private NpcModelSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("lowpoly npc", NpcModelSelfTest::run));
    }

    static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (AttendantRole r : AttendantRole.values()) {
            out.add(r.id);
        }
        out.addAll(EXTRA);
        for (TownGuests.Guest g : TownGuests.ALL) {
            out.add("guest_" + g.id());
        }
        out.add("artisan");
        return out;
    }

    private static JsonObject json(String path) {
        try (InputStream in = NpcModelSelfTest.class.getResourceAsStream(path)) {
            return in == null ? null : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean resource(String path) {
        try (InputStream in = NpcModelSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        String a = "/assets/skyabyss/";
        int props = 0;
        for (String id : ids()) {
            String m = "npc_" + id;
            JsonObject mesh = json(a + "lowpoly/" + m + ".json");
            if (mesh == null) {
                f.add("mesh " + m);
                continue;
            }
            if (!mesh.has("skyabyss_closed")) {
                f.add("not watertight (mesh_close) " + m);
            }
            JsonObject bones = mesh.getAsJsonObject("bones");
            for (String b : BONES) {
                if (!bones.has(b)) {
                    f.add("bone " + b + " in " + m);
                }
            }
            if (bones.has("root") && bones.getAsJsonObject("root").has("rotation")
                    && Math.abs(bones.getAsJsonObject("root").getAsJsonArray("rotation").get(1).getAsDouble()) > 0.01) {
                f.add("root is rotated in " + m + " (v2 faces -Z without a root turn)");
            }
            if (!resource(a + "textures/lowpoly/" + m + ".png")) {
                f.add("texture " + m);
            }
            if (id.equals("artisan")) {
                // 旅の職人は章ごとの絵 (形は一つ)
                for (int ch = 1; ch <= 4; ch++) {
                    if (!resource(a + "textures/lowpoly/" + m + "_ch" + ch + ".png")) {
                        f.add("texture " + m + "_ch" + ch);
                    }
                }
            }
            JsonObject geo = json(a + "geo/entity/" + m + "_lowpoly.geo.json");
            if (geo == null) {
                f.add("geo " + m);
            } else {
                int n = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones").size();
                if (n != bones.size()) {
                    f.add("geo bones " + n + " != mesh bones " + bones.size() + " in " + m);
                }
            }
            JsonObject anim = json(a + "animations/entity/" + m + ".animation.json");
            String prefix = id.startsWith("guest_") ? "animation.town_guest." : id.equals("artisan") ? "animation.artisan." : "animation.attendant.";
            for (String clip : List.of("idle", "walk", "talk")) {
                if (anim == null || !anim.getAsJsonObject("animations").has(prefix + clip)) {
                    f.add("animation " + prefix + clip + " for " + m);
                }
            }
            for (String s : List.of("prop_r", "prop_l", "prop_back")) {
                JsonObject p = json(a + "lowpoly/" + m + "_" + s + ".json");
                if (p == null) {
                    continue;
                }
                props++;
                if (!p.has("socket") || !p.get("socket").getAsBoolean()) {
                    f.add("prop not a socket mesh " + m + "_" + s);
                }
                if (!p.getAsJsonObject("bones").has(s)) {
                    f.add("prop bone " + s + " in " + m + "_" + s);
                }
                if (!resource(a + "textures/lowpoly/" + m + "_" + s + ".png")) {
                    f.add("prop texture " + m + "_" + s);
                }
            }
        }
        if (props < ids().size()) {
            f.add("only " + props + " props for " + ids().size() + " people (every v2 person carries a role prop)");
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST lowpoly npc PASSED ({} people: mesh/texture/geo/animation, watertight, {} role props)", ids().size(), props);
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST lowpoly npc FAILED: {}", x));
        }
    }
}
