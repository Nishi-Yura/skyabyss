package jp.revati.skyabyss.rpg.aspect;

import java.util.List;

import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.Skill;

// 巨神のアスペクト一品 (docs/design/58)。職の使う技を一つ変える。稀さ 0 = レア・1 = エピック・2 = レジェンド (id の _m_ は前の名前ミシックの印)。
// shrine は出る祠 (巨神の id。空 = どの祠でも。レジェンドはいつも祠ごと)。効き目は Lv 1 → 5 で lo → hi
public record Aspect(String id, Job job, int rarity, String shrine, Skill skill, List<Fx> fx) {
    public static final int RARE = 0;
    public static final int EPIC = 1;
    public static final int LEGENDARY = 2;

    public enum Verb {
        // 技の力・待ち・マナ (SkillModifier の口)
        POWER, COOLDOWN, MANA,
        // 使ったあとの 3 秒の移動・次の一撃・受ける傷
        HASTE, SURGE, GUARD,
        // もう一度 (力の割合)・待ちが戻る確率
        ECHO, RESET
    }

    public record Fx(Verb verb, float lo, float hi) {
        // その Lv の値 (1〜5。0 なら 0)
        public double at(int level) {
            if (level <= 0) {
                return 0;
            }
            int l = Math.min(Aspects.MAX_LEVEL, level);
            return lo + (hi - lo) * (l - 1) / (double) (Aspects.MAX_LEVEL - 1);
        }
    }

    public String nameKey() {
        return "aspect.skyabyss." + id;
    }

    public double value(Verb verb, int level) {
        double v = 0;
        for (Fx f : fx) {
            if (f.verb() == verb) {
                v += f.at(level);
            }
        }
        return v;
    }

    public boolean shared() {
        return shrine.isEmpty();
    }
}
