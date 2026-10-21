package jp.revati.skyabyss.npc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.guide.HeartbeatWard;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.quest.Action;
import jp.revati.skyabyss.quest.Condition;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.ch1.Ch1Prep;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

// 旅の職人 (docs/design/21 の P4) の自己テスト。段: "artisan"。
//  - 絵と骨: メッシュ・骨だけの geo・アニメ・章ごとの 4 つの絵・対話の顔・防具の絵が、そろっていて、骨の名前とピボットが合う (root の yaw 180 度は語り部と同じ)
//  - 立たせる: 章ごとの立つ所の表 (ArtisanSpawner.Spot) が引き、いなければ立たせ、二人目は立たせない。装い (variant) を保存する
//  - 会話: 入口の順 (初対面・頼み 3 本・乗る前の一言)、選択肢の条件 (好感度・覚書・鼓動守の編み直し)、結果 (作り方・鼓動守)、語り部は一言だけ残る
//  - 頼みの依頼主は職人、日英の文
public final class ArtisanSelfTest {
    private static final String ROLE = NpcSetup.ARTISAN_ID;

    private ArtisanSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.until("artisan (load)", ArtisanSelfTest::load), SelfTestRunner.Step.once("artisan", ArtisanSelfTest::run));
    }

    // 立たせる検査の前に、出現地点の脇のまわり (NpcSpawner.entitiesLoaded が見る 7x7 チャンク) を読み込んで、生き物の読み込みを待つ。
    // 読み込みは非同期で、ほかの段の順番 (都の建設など) しだいで間に合わないことがあった (「ensure did not stand a chapter-3 artisan (null)」)
    private static final java.util.Set<Long> FORCED = new java.util.HashSet<>();
    private static int loadWait;

    static BlockPos testMark(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(spawn.getX() + 4, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX() + 4, spawn.getZ() + 4), spawn.getZ() + 4);
    }

    static boolean load(net.minecraft.server.MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos mark = testMark(level);
        if (FORCED.isEmpty()) {
            loadWait = 0;
            net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(mark);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (level.setChunkForced(c.x + dx, c.z + dz, true)) {
                        FORCED.add(net.minecraft.world.level.ChunkPos.asLong(c.x + dx, c.z + dz));
                    }
                }
            }
            FORCED.add(Long.MIN_VALUE);
        }
        return NpcSpawner.entitiesLoaded(level, mark) || ++loadWait > 1200;
    }

    static void unforce(ServerLevel level) {
        for (long key : FORCED) {
            if (key != Long.MIN_VALUE) {
                level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false);
            }
        }
        FORCED.clear();
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "artisan_p"));
        Growth.get(p).level = 30;
        try {
            assets(f);
            spawning(f, level, p);
            dialogue(f, level, p);
            texts(f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST artisan crashed", e);
            f.add("crashed: " + e);
        } finally {
            Quests.forgetAll(p);
            Growth.get(p).discoveries.clear();
            resetBond(p);
            p.getInventory().clearContent();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST artisan PASSED (model/geo/animation/colourways, faces, armor assets, spawn table, variant save, dialogue entries/options/results, texts)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST artisan FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 絵と骨
    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = ArtisanSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("resource missing: " + path);
                return new JsonObject();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            f.add("resource unreadable: " + path + " " + e);
            return new JsonObject();
        }
    }

    private static int[] pngSize(String path, List<String> f) {
        try (InputStream in = ArtisanSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("texture missing: " + path);
                return new int[] {0, 0};
            }
            var img = javax.imageio.ImageIO.read(in);
            return new int[] {img.getWidth(), img.getHeight()};
        } catch (java.io.IOException | RuntimeException e) {
            f.add("texture unreadable: " + path + " " + e);
            return new int[] {0, 0};
        }
    }

    private static void assets(List<String> f) {
        String a = "/assets/skyabyss/";
        JsonObject mesh = json(a + "lowpoly/artisan.json", f);
        JsonObject geo = json(a + "geo/entity/artisan_lowpoly.geo.json", f);
        JsonObject anim = json(a + "animations/entity/artisan.animation.json", f);
        // 骨: メッシュとスケルトンで名前・親・ピボットが合う。root だけ yaw 180 (語り部・イサナと同じ向き)
        Set<String> meshBones = new HashSet<>();
        int tris = 0;
        JsonObject mb = mesh.has("bones") ? mesh.getAsJsonObject("bones") : new JsonObject();
        for (var e : mb.entrySet()) {
            meshBones.add(e.getKey());
            tris += e.getValue().getAsJsonObject().getAsJsonArray("triangles").size();
        }
        check(f, meshBones.equals(Set.of("root", "body", "head", "armL", "armR", "legL", "legR")), "the artisan mesh bones are " + meshBones);
        check(f, tris >= 740 && tris <= 1200, "the artisan mesh has " + tris + " triangles (want 740 to 1200: the closed mesh grows with joint fixes)"); // 740 → 1058: watertight pass (docs/design/15)
        Set<String> geoBones = new HashSet<>();
        JsonArray bones = geo.has("minecraft:geometry") ? geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones") : new JsonArray();
        for (JsonElement e : bones) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            geoBones.add(name);
            JsonObject m = mb.has(name) ? mb.getAsJsonObject(name) : null;
            check(f, m != null, "geo bone " + name + " is not in the mesh");
            if (m == null) {
                continue;
            }
            check(f, b.get("pivot").toString().equals(pivot(m)), "pivot of " + name + " differs between geo and mesh: " + b.get("pivot") + " vs " + pivot(m));
            String parent = m.has("parent") && !m.get("parent").isJsonNull() ? m.get("parent").getAsString() : null;
            check(f, (b.has("parent") ? b.get("parent").getAsString() : null) == null ? parent == null : b.get("parent").getAsString().equals(parent), "parent of " + name + " differs");
            if (name.equals("root")) {
                // 2026-10-21: 180 度だとゲームの中で後ろ向きだった。root は回さず、頭の上下は NpcRenderer.ArtisanModel が符号を戻す (係の人と同じ)
                check(f, !b.has("rotation") || b.getAsJsonArray("rotation").get(1).getAsDouble() == 0.0, "the artisan root must not be yawed (faces forward unrotated, like the attendants)");
            }
        }
        check(f, geoBones.equals(meshBones), "geo bones " + geoBones + " do not match mesh bones " + meshBones);
        // 肩・腰・首のピボット (腕は肩、頭は首、脚は腰)
        check(f, pivot(mb.getAsJsonObject("armL")).equals("[4.8,20.6,0.0]") && pivot(mb.getAsJsonObject("armR")).equals("[-4.8,20.6,0.0]")
                && pivot(mb.getAsJsonObject("head")).equals("[0.0,22.0,0.0]") && pivot(mb.getAsJsonObject("legL")).equals("[2.0,12.4,0.0]"), "the artisan pivots are not at shoulder / neck / hip");
        // アニメ: idle / walk / talk があり、使う骨は全部ある
        JsonObject anims = anim.has("animations") ? anim.getAsJsonObject("animations") : new JsonObject();
        for (String n : List.of("idle", "walk", "talk")) {
            check(f, anims.has("animation.artisan." + n), "animation " + n + " is missing");
            if (anims.has("animation.artisan." + n)) {
                for (String bone : anims.getAsJsonObject("animation.artisan." + n).getAsJsonObject("bones").keySet()) {
                    check(f, geoBones.contains(bone), "animation " + n + " moves a bone that does not exist: " + bone);
                }
            }
        }
        // 絵: 章ごとの 4 つ (同じ大きさ。メッシュの texture_size と同じ) と、既定の絵、対話の顔 (80x80 が 3 枚)
        int[] size = {256, 256};
        for (int ch = 1; ch <= 4; ch++) {
            check(f, java.util.Arrays.equals(pngSize(a + "textures/lowpoly/artisan_ch" + ch + ".png", f), size), "artisan_ch" + ch + " is not 256x256");
        }
        check(f, java.util.Arrays.equals(pngSize(a + "textures/lowpoly/artisan.png", f), size), "the default artisan texture is not 256x256");
        for (String face : List.of("neutral", "smile", "grave")) {
            check(f, java.util.Arrays.equals(pngSize(a + "textures/gui/npc/face/artisan_" + face + ".png", f), new int[] {128, 128}), "the artisan face " + face + " is not 128x128");
        }
        // 防具 (鯨道の旅装 案 A): 4 部位のメッシュ・絵・光る画素・アイコン・モデル。骨は共通の geo の 8 本のうち
        Set<String> armorBones = Set.of("armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot");
        int armorTris = 0;
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            JsonObject m = json(a + "lowpoly/armor/whale_road/" + piece + ".json", f);
            if (m.has("bones")) {
                for (var e : m.getAsJsonObject("bones").entrySet()) {
                    check(f, armorBones.contains(e.getKey()), "armor " + piece + " has an unknown bone " + e.getKey());
                    armorTris += e.getValue().getAsJsonObject().getAsJsonArray("triangles").size();
                }
            }
            check(f, m.has("texture") && m.get("texture").getAsString().equals("armor/whale_road/" + piece), "armor " + piece + " points at the wrong texture");
            int[] tex = pngSize(a + "textures/lowpoly/armor/whale_road/" + piece + ".png", f);
            check(f, tex[0] > 0 && java.util.Arrays.equals(tex, pngSize(a + "textures/lowpoly/armor/whale_road/" + piece + "_glowmask.png", f)), "armor " + piece + " atlas and glow mask differ in size");
            check(f, java.util.Arrays.equals(pngSize(a + "textures/item/whale_road_" + piece + ".png", f), new int[] {32, 32}), "the " + piece + " icon is not 32x32");
            check(f, json(a + "models/item/whale_road_" + piece + ".json", f).has("textures"), "the " + piece + " item model is missing");
        }
        check(f, armorTris == 600, "the whale road armor has " + armorTris + " triangles (want 600 after closing the meshes)"); // 418 → 600: watertight pass (docs/design/15)
        // 旅の覚書・鼓動守の絵 (案 A に差し替え済み。仮の絵ではない)
        check(f, java.util.Arrays.equals(pngSize(a + "textures/item/wayfarer_notes.png", f), new int[] {32, 32}), "the notes icon is not 32x32");
        check(f, java.util.Arrays.equals(pngSize(a + "textures/item/heartbeat_ward.png", f), new int[] {32, 32}), "the heartbeat ward icon is not 32x32");
        check(f, !placeholderMark(a + "textures/item/wayfarer_notes.png") && !placeholderMark(a + "textures/item/heartbeat_ward.png"), "a placeholder mark (magenta, top right) is still on the notes / ward icon");
    }

    private static String pivot(JsonObject bone) {
        JsonArray p = bone.getAsJsonArray("pivot");
        return "[" + p.get(0).getAsDouble() + "," + p.get(1).getAsDouble() + "," + p.get(2).getAsDouble() + "]";
    }

    // 右上のマゼンタの印 (tools/gen_guide_placeholders.py の仮の絵)
    private static boolean placeholderMark(String path) {
        try (InputStream in = ArtisanSelfTest.class.getResourceAsStream(path)) {
            var img = javax.imageio.ImageIO.read(in);
            for (int y = 0; y < 7; y++) {
                int rgb = img.getRGB(31, y);
                if (((rgb >> 24) & 0xFF) > 0 && ((rgb >> 16) & 0xFF) == 255 && ((rgb >> 8) & 0xFF) == 0 && (rgb & 0xFF) == 255) {
                    return true;
                }
            }
            return false;
        } catch (java.io.IOException | RuntimeException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- 立たせる
    private static void spawning(List<String> f, ServerLevel level, FakePlayer p) {
        // 表: 一章の立つ所は失せ物の荷車の傍らの道 (印 artisan)。条件はイサナと会ったあと
        var ch1 = ArtisanSpawner.spots().stream().filter(s -> s.chapter() == 1).findFirst();
        check(f, ch1.isPresent() && ch1.get().siteId().equals("ch1_lost_cart") && ch1.get().mark().equals("artisan"), "the chapter-1 artisan spot is not the cart track");
        if (ch1.isPresent()) {
            check(f, !ch1.get().gate().test(p), "the artisan stood before the player met Isana");
            Lore.unlock(p, "chapter1/whale_chaser", false);
            check(f, ch1.get().gate().test(p), "the artisan did not stand after the player met Isana");
            Lore.lock(p, "chapter1/whale_chaser");
        }
        // 表は章ごと (データ)。足して、引ける
        // 二章の本物の立つ所 (筏の跡) は、確かめのあとに戻す
        var realCh2 = ArtisanSpawner.spots().stream().filter(s -> s.chapter() == 2).findFirst();
        ArtisanSpawner.register(new ArtisanSpawner.Spot(2, "selftest_site", "artisan", pl -> true));
        check(f, ArtisanSpawner.spots().stream().anyMatch(s -> s.chapter() == 2 && s.siteId().equals("selftest_site")), "a chapter-2 spot was not added to the table");
        check(f, ArtisanSpawner.marks(level, ArtisanSpawner.spots().stream().filter(s -> s.chapter() == 2).findFirst().orElseThrow()).isEmpty(), "marks for an unbuilt site");
        ArtisanSpawner.unregister(2);
        check(f, ArtisanSpawner.spots().stream().noneMatch(s -> s.chapter() == 2), "unregister did not remove the spot");
        realCh2.ifPresent(ArtisanSpawner::register);

        // 立たせる: 地面のある所で
        BlockPos mark = testMark(level);
        NpcEntity first = null;
        try {
            check(f, NpcSpawner.entitiesLoaded(level, mark), "the entities around the test mark did not load in time");
            first = ArtisanSpawner.ensure(level, mark, 3);
            check(f, first != null && first.variant() == 3 && first.getType() == NpcSetup.ARTISAN.get(), "ensure did not stand a chapter-3 artisan (" + first + ")");
            if (first != null) {
                check(f, first.home() != null && first.home().equals(mark) && "skyabyss:artisan".equals(first.role()), "the artisan home is not the mark");
                check(f, first.getCustomName() != null, "the artisan has no name");
                check(f, ArtisanSpawner.live(level, mark, 3) == first && ArtisanSpawner.live(level, mark, 1) == null, "live() does not tell chapters apart");
                check(f, ArtisanSpawner.ensure(level, mark, 3) == null, "a second artisan was stood beside the first");
                // 装いは保存される
                CompoundTag tag = new CompoundTag();
                first.addAdditionalSaveData(tag);
                NpcEntity copy = NpcSetup.ARTISAN.get().create(level);
                copy.readAdditionalSaveData(tag);
                check(f, copy.variant() == 3 && tag.getInt("Variant") == 3, "the artisan variant was not saved");
                // 違う章の装いは別の人 (同じ印の近くでも立つ)
                NpcEntity other = ArtisanSpawner.ensure(level, mark, 2);
                check(f, other != null && other.variant() == 2, "a chapter-2 artisan was not stood beside the chapter-3 one");
                if (other != null) {
                    other.discard();
                }
            }
        } finally {
            if (first != null) {
                first.discard();
            }
            unforce(level);
        }
        // 役: 殴られても傷つかない・名前の鍵
        NpcEntity npc = NpcSetup.ARTISAN.get().create(level);
        check(f, npc != null && npc.isInvulnerable() && npc.variant() == 0, "a fresh artisan should be invulnerable with variant 0");
        // 描画の絵の選び方 (章 1〜4。範囲外は一章)
        check(f, ArtisanLook.texture(0).equals("artisan_ch1") && ArtisanLook.texture(1).equals("artisan_ch1") && ArtisanLook.texture(4).equals("artisan_ch4")
                && ArtisanLook.texture(9).equals("artisan_ch1"), "the artisan texture is not chosen by chapter");
    }

    // ---------------------------------------------------------------- 会話
    private static void resetBond(FakePlayer p) {
        NpcBonds.Bond bond = NpcBonds.of(p).bond(ROLE);
        bond.flags().clear();
        bond.echoes().clear();
        Affinity.set(p, ROLE, 0);
    }

    private static StoryContext ctx(FakePlayer p, NpcEntity npc) {
        return new StoryContext(p, ROLE, npc);
    }

    private static String entry(Dialogue d, StoryContext c) {
        Dialogue.Node n = d.entryNode(c);
        return n == null ? "(none)" : n.id();
    }

    private static Set<String> shown(Dialogue d, String node, StoryContext c) {
        Set<String> ids = new HashSet<>();
        for (Dialogue.Option o : d.nodes().get(node).options()) {
            if (o.condition().test(c)) {
                ids.add(o.id());
            }
        }
        return ids;
    }

    private static void dialogue(List<String> f, ServerLevel level, FakePlayer p) {
        DialogueManager.loadErrors().forEach(e -> f.add("dialogue load: " + e));
        QuestManager.loadErrors().forEach(e -> f.add("quest load: " + e));
        Dialogue d = DialogueManager.dialogue(ROLE);
        check(f, d != null, "no dialogue for the artisan");
        if (d == null) {
            return;
        }
        NpcEntity npc = NpcSetup.ARTISAN.get().create(level);
        Quests.forgetAll(p);
        p.getInventory().clearContent();
        resetBond(p);
        Growth.get(p).discoveries.clear();
        StoryContext c = ctx(p, npc);
        // 入口の最後は条件なしの a_menu。章の足し書きが前に入る
        check(f, d.entries().get(d.entries().size() - 1).node().equals("a_menu"), "the artisan catch-all entry must be last");
        // 初対面
        check(f, entry(d, c).equals("ch1a_first"), "a first meeting should open ch1a_first, got " + entry(d, c));
        Action.parseList(JsonParser.parseString("{\"type\":\"flag\",\"flag\":\"met\"}"), new ArrayList<>(), "t").get(0).run(c);
        check(f, entry(d, c).equals("a_menu"), "after meeting, the menu should open, got " + entry(d, c));
        // 好感度で開く「手の話」 (10・30・60)
        check(f, shown(d, "a_menu", c).equals(Set.of("bye")), "a stranger sees only the goodbye option: " + shown(d, "a_menu", c));
        Affinity.set(p, ROLE, 10);
        check(f, shown(d, "a_menu", c).equals(Set.of("hammer", "bye")), "affinity 10 should open the hammer talk: " + shown(d, "a_menu", c));
        Affinity.set(p, ROLE, 30);
        check(f, shown(d, "a_menu", c).equals(Set.of("hammer", "whet", "bye")), "affinity 30 should open the whetstone talk: " + shown(d, "a_menu", c));
        Affinity.set(p, ROLE, 60);
        check(f, shown(d, "a_menu", c).equals(Set.of("hammer", "whet", "hands", "bye")), "affinity 60 should open the hands talk: " + shown(d, "a_menu", c));
        Affinity.set(p, ROLE, 0);

        // 支度の段「折れた剣」: 受けると作り方と覚書の選択肢が出る。洞の段のうちは一度だけ「折れた剣を見た」
        Quests.start(p, Ch1Prep.Q_BLADE);
        Set<String> menu = shown(d, "a_menu", c);
        check(f, menu.contains("teach") && menu.contains("notes"), "starting trail_blade should open the teach / notes options: " + menu);
        check(f, entry(d, c).equals("ch1t_blade_tell"), "the cave stage should open ch1t_blade_tell, got " + entry(d, c));
        d.nodes().get("ch1t_blade_tell").actions().forEach(a -> a.run(c));
        check(f, entry(d, c).equals("a_menu"), "the broken sword pointer came twice, got " + entry(d, c));
        // 洞の台座の頁 → 話す段: 作り方を教わる (レシピが解け、入口は段が進めば出ない)
        jp.revati.skyabyss.lore.Lore.unlock(p, Ch1Prep.PAGE_BLADE, false);
        Quests.poll(p, true);
        check(f, entry(d, c).equals("ch1t_blade_show"), "the show stage should open ch1t_blade_show, got " + entry(d, c));
        var whetHolder = level.getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("skyabyss", "rock_whetstone"));
        check(f, whetHolder.isPresent(), "the whetstone recipe is missing");
        whetHolder.ifPresent(h -> p.getRecipeBook().remove(h));
        for (Action a : d.nodes().get("ch1t_blade_show").actions()) {
            a.run(c);
        }
        check(f, whetHolder.isPresent() && p.getRecipeBook().contains(whetHolder.get()), "the artisan did not teach the recipes");
        Quests.onTalk(p, ROLE, "ch1t_blade_show");
        check(f, !entry(d, c).equals("ch1t_blade_show"), "the show entry came again after the talk");
        // 鍛えの段: 鉄の剣と砥石 (どちらが先でもよい)
        p.getInventory().add(new ItemStack(Items.IRON_SWORD));
        Quests.poll(p, true);
        check(f, !QuestLog.of(p).entry(Ch1Prep.Q_BLADE).done, "trail_blade finished without the whetstone");
        Quests.signal(p, Ch1Prep.SIGNAL_WHETSTONE_USED);
        check(f, QuestLog.of(p).entry(Ch1Prep.Q_BLADE).done, "an iron sword and a used whetstone did not finish trail_blade");

        // 護り糸 → 最初の鼓動守 (人ごとに一度。頼みは要らない)
        p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_THREAD.get(), 1));
        check(f, entry(d, c).equals("ch1t_ward_weave"), "a thread in hand should open ch1t_ward_weave, got " + entry(d, c));
        d.nodes().get("ch1t_ward_weave").actions().forEach(a -> a.run(c));
        check(f, p.getInventory().countItem(GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get()) == 1 && p.getInventory().countItem(GuideSetup.ROCK_SHELL_THREAD.get()) == 0,
                "the first ward was not woven from the thread");
        p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_THREAD.get(), 1));
        check(f, !entry(d, c).equals("ch1t_ward_weave"), "the first-ward entry came twice");

        // 乗る前の一言 (三本そろった。鉄の跡・盾と糧は段を飛ばして済ませる)
        for (String id : List.of(Ch1Prep.Q_IRON, Ch1Prep.Q_SHIELD)) {
            Quests.start(p, id);
            for (int i = 0; i < 4 && !QuestLog.of(p).entry(id).done; i++) {
                Quests.forceStage(p, id);
            }
        }
        check(f, Ch1PrepDone.allDone(p), "the three trail steps are not all done");
        check(f, entry(d, c).equals("ch1a_ready"), "all three done should open ch1a_ready, got " + entry(d, c));

        // 鼓動守の編み直し: 持っていない・今日まだ・岩殻片 8 つ
        p.getInventory().clearContent();
        Growth.get(p).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
        menu = shown(d, "a_menu", c);
        check(f, menu.contains("ward_ask") && !menu.contains("ward"), "without shell scraps the artisan only explains: " + menu);
        p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 8));
        menu = shown(d, "a_menu", c);
        check(f, menu.contains("ward") && !menu.contains("ward_ask"), "with eight shell scraps the artisan offers to weave: " + menu);
        Dialogue.Option ward = d.nodes().get("a_menu").options().stream().filter(o -> o.id().equals("ward")).findFirst().orElseThrow();
        check(f, ward.target().equals("ch1a_ward"), "the ward option should lead to ch1a_ward");
        d.nodes().get(ward.target()).actions().forEach(a -> a.run(c));
        check(f, p.getInventory().countItem(GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get()) == 1 && p.getInventory().countItem(GuideSetup.ROCK_SHELL_SCRAP.get()) == 0,
                "the ward was not woven for eight shell scraps");
        check(f, !shown(d, "a_menu", c).contains("ward") && !shown(d, "a_menu", c).contains("ward_ask"), "a player who holds the ward was offered another");
        // 使い切っても、その日のうちは二つ目を編まない (1 日に 1 つ)
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 8));
        check(f, !HeartbeatWard.remakeable(p, "stone_whale") && !shown(d, "a_menu", c).contains("ward"), "a second ward was woven on the same day");
        Growth.get(p).discoveries.removeIf(k -> k.startsWith("ward_remake:"));
        check(f, HeartbeatWard.remakeable(p, "stone_whale") && !HeartbeatWard.remakeable(p, "no_such_titan"), "the daily ward limit did not reset / accepted an unknown titan");

        // 覚書の渡し直し (結果)
        p.getInventory().clearContent();
        check(f, !WayfarerNotes.has(p, 1), "the player still has the notes");
        d.nodes().get("ch1a_notes").actions().forEach(a -> a.run(c));
        check(f, WayfarerNotes.has(p, 1), "the artisan did not hand the notes back");

        // 折れた剣の依頼主は職人。語り部には職人を指す一言が残る
        QuestDef bladeDef = QuestManager.quest(Ch1Prep.Q_BLADE);
        check(f, bladeDef != null && ROLE.equals(bladeDef.giver()), Ch1Prep.Q_BLADE + " is not given by the artisan");
        Dialogue st = DialogueManager.dialogue(NpcSetup.STORYTELLER_ID);
        check(f, st != null && st.nodes().containsKey("ch1p_artisan_pointer") && !st.nodes().containsKey("ch1p_gear_weave")
                && !st.nodes().containsKey("ch1p_blade_report") && !st.nodes().containsKey("ch1p_pulse_weave"), "the storyteller keeps only the pointer line");
        check(f, st != null && st.entries().get(st.entries().size() - 1).node().equals("menu"), "the storyteller catch-all entry must stay last");

        // 参照先: 条件・結果の型 (ward_ok・unlock_recipes・remake_ward が読み込めている)
        check(f, Condition.TYPES.contains("ward_ok") && Action.TYPES.contains("unlock_recipes") && Action.TYPES.contains("remake_ward"), "the new condition / action types are not registered");
        npc.discard();
    }

    // ---------------------------------------------------------------- 文 (日英)
    private static void texts(List<String> f) {
        Dialogue d = DialogueManager.dialogue(ROLE);
        if (d == null) {
            return;
        }
        List<String> keys = new ArrayList<>(List.of("entity.skyabyss.artisan", "message.skyabyss.artisan.struck", "message.skyabyss.heartbeat_ward.remade",
                "dialogue.skyabyss.storyteller.ch1p_artisan_pointer"));
        for (Dialogue.Node n : d.nodes().values()) {
            keys.add(n.textKey());
            n.options().forEach(o -> keys.add(o.textKey()));
        }
        for (String lang : List.of("ja_jp", "en_us")) {
            Set<String> have = new HashSet<>();
            for (String ns : List.of("skyabyss_npc", "skyabyss_ch1", "skyabyss_ch2", "skyabyss_ch3", "skyabyss_ch3gear", "skyabyss_ch4")) {
                try (InputStream in = ArtisanSelfTest.class.getResourceAsStream("/assets/" + ns + "/lang/" + lang + ".json")) {
                    if (in != null) {
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e -> have.add(e.getKey()));
                    }
                } catch (java.io.IOException | RuntimeException e) {
                    f.add("lang unreadable: " + ns + "/" + lang);
                }
            }
            for (String k : keys) {
                check(f, have.contains(k), lang + " is missing " + k);
            }
        }
        check(f, keys.size() > 20, "the artisan dialogue looks too small (" + keys.size() + " keys)");
    }

    // 三本とも終えたか (PrepQuests.ready を直に)
    private static final class Ch1PrepDone {
        static boolean allDone(FakePlayer p) {
            return jp.revati.skyabyss.guide.PrepQuests.ready(p, 1);
        }
    }
}
