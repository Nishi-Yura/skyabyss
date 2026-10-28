package jp.revati.skyabyss.rpg.skill.tree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillTree;

// 技の木 v2 の読み込み (assets/skyabyss/skill_tree/<職>.json と、職をまたぐ deep.json・past.json。jar の中から読むので、サーバーとクライアントで同じ木)。
// 数値の調整はこの JSON を直して jar を作り直す (コードは触らない)。壊れた木は起動時に例外で止める (自己テスト `tree` も見張る)
public final class AbilityTrees {
    // 職をまたぐ木 (転生の報い。docs/design/31)
    public static final List<SkillTree> SHARED = List.of(SkillTree.DEEP, SkillTree.PAST);
    private static final Map<SkillTree, AbilityTree> TREES = new EnumMap<>(SkillTree.class);
    private static boolean loaded;
    // 検査用: false にすると木が無いものとして動く (前の覚え方 = 技の段を直接。技の仕組みだけを検める自己テスト skills・skill lesson)
    private static boolean enabled = true;

    private AbilityTrees() {
    }

    public static synchronized void load() {
        TREES.clear();
        for (Job job : Job.values()) {
            read(job.id, job.tree, job);
        }
        for (SkillTree t : SHARED) {
            read(t.id, t, null);
        }
        loaded = true;
    }

    private static void read(String key, SkillTree tree, @Nullable Job job) {
        String path = "/assets/" + SkyAbyss.MODID + "/skill_tree/" + key + ".json";
        try (InputStream in = AbilityTrees.class.getResourceAsStream(path)) {
            if (in == null) {
                SkyAbyss.LOGGER.warn("[tree] no ability tree for {} ({})", key, path);
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            TREES.put(tree, AbilityTree.parse(key, tree, job, o));
        } catch (Exception e) {
            throw new IllegalStateException("ability tree " + path + " is broken", e);
        }
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    @Nullable
    public static AbilityTree of(@Nullable Job job) {
        return job == null ? null : of(job.tree);
    }

    @Nullable
    public static AbilityTree of(@Nullable SkillTree tree) {
        if (!enabled) {
            return null;
        }
        if (!loaded) {
            load();
        }
        return tree == null ? null : TREES.get(tree);
    }

    // 読み込んだ木すべて (検査で外しているときは空)
    public static Collection<AbilityTree> all() {
        if (!enabled) {
            return List.of();
        }
        if (!loaded) {
            load();
        }
        return TREES.values();
    }

    @Nullable
    public static AbilityNode node(@Nullable Job job, String id) {
        AbilityTree t = of(job);
        return t == null ? null : t.node(id);
    }

    @Nullable
    public static AbilityNode node(@Nullable SkillTree tree, String id) {
        AbilityTree t = of(tree);
        return t == null ? null : t.node(id);
    }
}
