package jp.revati.skyabyss.rpg.skill.tree;

import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.skill.Job;
import jp.revati.skyabyss.rpg.skill.SkillData;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.world.entity.player.Player;

// 取っている節の「常に効く数」(EFFECT・THRESHOLD) を鍵ごとに足す。使う側 (SkillBuild・SkillEvents・SkillActions) が鍵を決める。
//   armor / knockback / move / attack_speed / mana        : 属性 (SkillBuild.attrs)
//   attack / projectile                                  : 与える傷の割合 (SkillBuild.generalDamage)
//   skill_power / cooldown / mana_cost / regen            : 技の力・待ち・マナの費用・マナの回復 (SkillBuild)
//   damage_taken / damage_taken_still / fire_resist       : 受ける傷の割合 (SkillEvents。still = 動かずにいる間)
//   big_target / lifesteal / low_hp_armor                 : 大きな相手への傷・吸い (SkillActions.hit / SkillEvents)、体力が少ないときの防具
// THRESHOLD の節は、その基礎 (今の職の木から導いた値) が at 以上のときだけ数える
public final class NodeEffects {
    private NodeEffects() {
    }

    public static double sum(Player player, String key) {
        SkillData d = SkillManager.get(player);
        Job job = Job.byId(d.curJob);
        AbilityTree tree = AbilityTrees.of(job);
        if (tree == null) {
            return 0;
        }
        GrowthData g = Growth.get(player);
        double v = 0;
        for (String id : d.nodesOf(job)) {
            AbilityNode n = tree.node(id);
            if (n == null || n.effect.isEmpty()) {
                continue;
            }
            Double x = n.effect.get(key);
            if (x == null) {
                continue;
            }
            if (n.kind == AbilityNode.Kind.THRESHOLD && g.stat(n.stat) < n.at) {
                continue;
            }
            v += x;
        }
        return v;
    }

    // 閾値の節が今効いているか (画面の表示)
    public static boolean thresholdMet(Player player, AbilityNode n) {
        return n.kind != AbilityNode.Kind.THRESHOLD || Growth.get(player).stat(n.stat) >= n.at;
    }
}
