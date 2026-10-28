package jp.revati.skyabyss.rpg.skill.tree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;

// 職ごとの技の木 v2 の読み込み (assets/skyabyss/skill_tree/<職>.json。jar の中から読むので、サーバーとクライアントで同じ木)。
// 数値の調整はこの JSON を直して jar を作り直す (コードは触らない)。壊れた木は起動時に例外で止める (自己テスト `tree` も見張る)
public final class AbilityTrees {
    private static final Map<Job, AbilityTree> TREES = new EnumMap<>(Job.class);
    private static boolean loaded;
    // 検査用: false にすると木が無いものとして動く (前の覚え方 = 技の段を直接。技の仕組みだけを検める自己テスト skills・skill lesson)
    private static boolean enabled = true;

    private AbilityTrees() {
    }

    public static synchronized void load() {
        TREES.clear();
        for (Job job : Job.values()) {
            String path = "/assets/" + SkyAbyss.MODID + "/skill_tree/" + job.id + ".json";
            try (InputStream in = AbilityTrees.class.getResourceAsStream(path)) {
                if (in == null) {
                    SkyAbyss.LOGGER.warn("[tree] no ability tree for {} ({})", job.id, path);
                    continue;
                }
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                TREES.put(job, AbilityTree.parse(job, o));
            } catch (Exception e) {
                throw new IllegalStateException("ability tree " + path + " is broken", e);
            }
        }
        loaded = true;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    @Nullable
    public static AbilityTree of(@Nullable Job job) {
        if (!enabled) {
            return null;
        }
        if (!loaded) {
            load();
        }
        return job == null ? null : TREES.get(job);
    }

    @Nullable
    public static AbilityNode node(@Nullable Job job, String id) {
        AbilityTree t = of(job);
        return t == null ? null : t.node(id);
    }
}
