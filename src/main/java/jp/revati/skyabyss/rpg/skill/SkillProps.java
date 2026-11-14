package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.prop.AttackProps;
import jp.revati.skyabyss.item.extra.Element;
import jp.revati.skyabyss.item.extra.PowderArts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 自分の技の形 (2026-11-12 の声「こちらのスキルや攻撃で敵のボスみたいに下からモデルが出るとか、モデルが飛んでいくみたいな」)。
// 技を使った瞬間 (SkillManager の見た目の合図と同じ所) に、模型を床から突き出す・飛ばす・降らせる。見た目だけで、当たりは技そのものが持つ。
// 手元の板の段取り (rpg/client/skill/SkillFx) と同じ数・同じ時刻に合わせる (溜め → 放ち → 当たり)。
// 技ごとの専用の形 (2026-11-12 の声「攻撃とバフ・デバフでモデルの見た目変えた方がいい。スキルの種類によっても変えないと。使い回しは良くない」)。
//   模型    prop_ss_<技> (tools/gen/mobs/attack_props/build_skill_each.py)。飛ぶものは ss_<技>_fly、降るものは ss_<技>_fall
//   見分け  案 A「形で分ける」: 攻撃 = 外へ尖る刃 (刃先が白) / 強化 = 丸く包んで昇る (金と青緑) / 弱体 = 枷と鎖 (紫と黒) / 移動 = 線だけの残像
//   大きさ  範囲のものは模型の半径 1 ブロック (大きさ = 半径)。板が高くなりすぎないよう技ごとに決める
// 添える印 (tools/gen/mobs/attack_props/build_skill_kit.py):
//   粉      武器の粉の技が働いていれば (いちばん重い粉)、形の置き場の三か所まで prop_sk_el_<粉> を添える
//   変化    技を変えているもの (SkillChanges: 書き換え > 変異 > 秘伝 > 刻印) があれば、技の芯に prop_sk_ch_<種類> を添える
//   熟練    技の熟練の段 (SkillData.masteryLevel 0〜5) で形が段ごとに 4% 大きくなり、3 から足もとに棘の輪 (sk_mastery_3)、5 で冠 (sk_mastery_5)
public final class SkillProps {
    // 熟練の段ごとの大きさ
    static final float MASTERY_GROW = 0.04f;
    // 粉の印を添える置き場の数
    static final int ELEMENT_MARKS = 3;

    private SkillProps() {
    }

    // 武器の粉の技 (いちばん重い粉)。なければ null
    @Nullable
    static Element element(ServerPlayer p) {
        Element best = null;
        int w = 0;
        for (var e : PowderArts.weaponArts(p).entrySet()) {
            if (e.getValue() > w) {
                w = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    // 通常攻撃 (魔弾・呪印) の当たりに、武器の粉の印を添える
    public static void elementMark(ServerPlayer p, Vec3 at, float size, int linger) {
        Element el = element(p);
        if (el != null && p.level() instanceof ServerLevel level) {
            AttackProps.riseLater(level, "sk_el_" + el.id, p, at, level.random.nextFloat() * 360f, size, linger, 0);
        }
    }

    // 技を変えているもの (書き換え > 変異 > 秘伝 > 刻印)。なければ null
    @Nullable
    static String change(ServerPlayer p, Skill skill) {
        String best = null;
        int rank = -1;
        for (SkillChanges.Change c : SkillChanges.of(p, skill)) {
            if (!c.active()) {
                continue;
            }
            int r = switch (c.kind()) {
                case REWRITE -> 3;
                case MUTATION -> 2;
                case SECRET -> 1;
                case RUNE -> 0;
            };
            if (r > rank) {
                rank = r;
                best = switch (c.kind()) {
                    case REWRITE -> "rewrite";
                    case MUTATION -> "mutation";
                    case SECRET -> "secret";
                    case RUNE -> "rune";
                };
            }
        }
        return best;
    }

    // 技を使った瞬間 (SkillManager.cast の見た目の合図のあと)。skill = 使った技、fx = 見た目の技 (変化の中身)、target = サーバーが決めた点 (なければ null)
    public static void onCast(ServerPlayer p, Skill skill, Skill fx, int rank, @Nullable Vec3 target) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        int mastery = SkillManager.get(p).masteryLevel(skill);
        Cast c = new Cast(level, p, fx.tree, target, 1f + MASTERY_GROW * mastery);
        place(c, fx);
        decorate(c, element(p), change(p, skill), mastery);
    }

    static void place(Cast c, Skill fx) {
        switch (fx) {
            // ---------------------------------------------------------------- 戦士 (花崗岩)
            case TITAN_CLEAVE -> c.put("titan_cleave", c.foot(), 2.2f, 14, 2, 0f);
            case ROCK_CALL -> c.put("rock_call", c.foot(), 2.2f, 20, 0, 0f);
            case WHALE_SWEEP -> c.put("whale_sweep", c.foot(), 2.6f, 14, 0, 0f);
            case SHIELD_RUSH -> c.put("shield_rush", c.foot(), 1.2f, 10, 0, 0f);
            case SHIELD_BASH -> c.put("shield_bash", c.foot(), 1.3f, 10, 1, 0f);
            case BONE_SWING -> c.put("bone_swing", c.foot(), 2.0f, 14, 3, 0f);
            case SPIN_CUT -> c.put("spin_cut", c.foot(), 2.5f, 10, 2, 0f);
            // ---------------------------------------------------------------- 暗殺者 (風)
            case LUNGE -> c.put("lunge", c.foot(), 1.3f, 10, 0, 0f);
            case BACKSTEP -> c.put("backstep", c.foot(), 1.0f, 10, 0, 180f);
            case WIND_REND -> {
                c.fly("wind_rend_fly", c.front(4.0), 3, 1.4f, 12f, 2);
                c.fly("wind_rend_fly", c.front(4.0), 3, 1.4f, -12f, 6);
            }
            case MIST_VEIL -> c.put("mist_veil", c.foot(), 1.2f, 30, 0, 0f);
            case TWIN_FANG -> {
                Vec3 t = c.targetOr(3.0);
                c.put("twin_fang", t, 1.2f, 12, 2, 0f);
                c.put("twin_fang", t, 1.1f, 12, 6, 50f);
            }
            // ---------------------------------------------------------------- 呪術師 (潮)
            case WARD_SONG -> c.put("ward_song", c.foot(), 1.3f, 24, 0, 0f);
            case EBB_TIDE -> c.put("ebb_tide", c.foot(), 2.4f, 14, 2, 0f);
            case CYCLE_SONG -> c.put("cycle_song", c.foot(), 1.3f, 24, 0, 0f);
            case CURSE_MARK -> c.put("curse_mark", c.targetOr(10.0), 1.0f, 40, 3, 0f);
            case LAMP_CHARM -> c.put("lamp_charm", c.targetOr(3.0), 1.0f, 100, 0, 0f);
            // ---------------------------------------------------------------- 鍛冶師 (赤熱の鉄)
            case TEMPER -> c.put("temper", c.foot(), 1.1f, 24, 0, 0f);
            case EMBER_HAMMER -> c.put("ember_hammer", c.front(2.0), 1.6f, 16, 3, 0f);
            case ASH_SCATTER -> c.put("ash_scatter", c.foot(), 4.5f, 40, 2, 0f);
            case FORGE_STOMP -> {
                c.put("forge_stomp", c.foot(), 2.4f, 10, 0, 0f);
                c.put("forge_stomp", c.foot(), 3.4f, 10, 3, 20f);
            }
            case SPARK_BURST -> c.put("spark_burst", c.foot(), 1.6f, 10, 1, 0f);
            // ---------------------------------------------------------------- 弓使い (骨)
            case CLOUD_PIERCE -> c.fly("cloud_pierce_fly", c.targetOr(24.0).add(0, 1.2, 0), 6, 1.4f, 0f, 0);
            case BONE_TRAP -> c.put("bone_trap", c.front(1.0), 1.0f, 60, 2, 0f);
            case SKY_RAIN -> {
                Vec3 t = c.targetOr(12.0);
                c.put("sky_rain", t, 3.6f, 70, 0, 0f);
                for (int k = 0; k < 8; k++) {
                    double a = c.level.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(c.level.random.nextDouble()) * 3.6;
                    c.fall("sky_rain_fall", t.add(Math.cos(a) * d, 0, Math.sin(a) * d), 10 + k * 10, 1.0f);
                }
            }
            // 呼んだ弓の霊そのものが形 (BowSpirits が prop_bow_spirit を出す)
            case BOW_SPIRIT -> {
            }
            case SNARE_SHOT -> {
                Vec3 t = c.targetOr(12.0);
                c.fly("snare_shot_fly", t.add(0, 1, 0), 3, 1.2f, 0f, 0);
                c.put("snare_shot", t, 1.0f, 40, 3, 0f);
            }
            // ---------------------------------------------------------------- 魔法使い (星)
            case THUNDER_BOLT -> {
                Vec3 t = c.targetOr(16.0);
                c.fly("thunder_bolt_fly", t.add(0, 1, 0), 2, 1.4f, 0f, 0);
                c.put("thunder_bolt", t, 1.0f, 10, 2, 0f);
            }
            case EMBER_ORB -> {
                Vec3 t = c.front(14.0);
                c.fly("ember_orb_fly", t.add(0, 1.2, 0), 10, 1.3f, 0f, 0);
                c.put("ember_orb", t, 1.6f, 10, 10, 0f);
            }
            case FROST_TIDE -> c.put("frost_tide", c.targetOr(6.0), 3.0f, 12, 0, 0f);
            case BLINK -> c.put("blink", c.foot(), 1.0f, 12, 0, 0f);
            case STARDUST -> {
                Vec3 t = c.targetOr(10.0);
                c.put("stardust", t, 3.5f, 30, 0, 0f);
                for (int k = 0; k < 6; k++) {
                    double a = c.level.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(c.level.random.nextDouble()) * 3.5;
                    c.fall("stardust_fall", t.add(Math.cos(a) * d, 0, Math.sin(a) * d), 2 + k, 1.2f);
                }
            }
            // ---------------------------------------------------------------- 淵・前世
            case DEEP_PULL -> c.put("deep_pull", c.foot(), 3.0f, 14, 0, 0f);
            case DEEP_ECHO -> c.put("deep_echo", c.foot(), 1.2f, 20, 0, 0f);
            case DEEP_MAW -> c.put("deep_maw", c.targetOr(8.0), 2.4f, 14, 4, 0f);
            case ECHO_STRIKE -> {
                c.put("echo_strike", c.front(2.5), 1.2f, 14, 1, 0f);
                c.put("echo_strike", c.front(2.5), 0.9f, 12, 10, 45f);
            }
            case GRAVE_BELL -> c.fall("grave_bell_fall", c.foot(), 3, 2.2f);
            case FINAL_LIGHT -> {
                int i = 0;
                for (float r : new float[] {1.8f, 3.7f, 5.5f}) {
                    c.put("final_light", c.foot(), r, 8, 3 + i * 3, i * 15f);
                    i++;
                }
            }
            default -> {
            }
        }
    }

    // 粉・変化・熟練の印を添える
    static void decorate(Cast c, @Nullable Element el, @Nullable String change, int mastery) {
        List<Spot> spots = c.spots;
        if (el != null) {
            int n = Math.min(ELEMENT_MARKS, spots.size());
            for (int i = 0; i < n; i++) {
                Spot s = spots.get(i * spots.size() / Math.max(1, n));
                AttackProps.riseLater(c.level, "sk_el_" + el.id, c.p, s.at(), c.level.random.nextFloat() * 360f, 0.9f * c.grow, 12, s.delay());
            }
        }
        if (change != null) {
            Spot s = spots.isEmpty() ? new Spot(c.foot().add(c.side(1.0)), 0) : spots.get(0);
            AttackProps.riseLater(c.level, "sk_ch_" + change, c.p, s.at(), c.yaw(), 1.0f * c.grow, 18, s.delay());
        }
        if (mastery >= 5) {
            AttackProps.riseLater(c.level, "sk_mastery_5", c.p, c.foot(), c.yaw(), 1.0f, 14, 0);
        } else if (mastery >= 3) {
            AttackProps.riseLater(c.level, "sk_mastery_3", c.p, c.foot(), c.yaw(), 1.0f, 12, 0);
        }
    }

    // 印を添える置き場 (床の点と、出る時刻)
    record Spot(Vec3 at, int delay) {
    }

    // 一つの技の置き場の計算 (人の足もと・向き・狙い)
    static final class Cast {
        final ServerLevel level;
        final ServerPlayer p;
        final SkillTree tree;
        @Nullable
        final Vec3 target;
        final Vec3 fwd;
        // 熟練の大きさ
        final float grow;
        final List<Spot> spots = new ArrayList<>();

        Cast(ServerLevel level, ServerPlayer p, SkillTree tree, @Nullable Vec3 target, float grow) {
            this.level = level;
            this.p = p;
            this.tree = tree;
            this.target = target;
            this.grow = grow;
            Vec3 f = p.getLookAngle().multiply(1, 0, 1);
            this.fwd = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        }

        Vec3 foot() {
            return p.position();
        }

        Vec3 chest() {
            return p.position().add(0, 1.2, 0);
        }

        Vec3 front(double d) {
            return p.position().add(fwd.scale(d));
        }

        // 前から a ラジアン回した水平の向き
        Vec3 dir(double a) {
            return fwd.yRot((float) a);
        }

        Vec3 side(double d) {
            return new Vec3(-fwd.z, 0, fwd.x).scale(d);
        }

        Vec3 targetOr(double d) {
            return target != null ? target : front(d);
        }

        float yaw() {
            return (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
        }

        // 床の高さへ下ろす (狙った点が宙にあるとき。上 1.5 から下 12 までで最初の床、なければそのまま)
        Vec3 ground(Vec3 at) {
            Vec3 from = at.add(0, 1.5, 0);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(from, at.add(0, -12, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : hit.getLocation();
        }

        void mark(Vec3 at, int delay) {
            spots.add(new Spot(at, delay));
        }

        // 技の模型を床に置く (at は床へ下ろす)。yawOff = 前から回す角 (度)
        void put(String key, Vec3 at, float size, int linger, int delay, float yawOff) {
            at = ground(at);
            AttackProps.riseLater(level, "ss_" + key, p, at, yaw() + yawOff, size * grow, linger, delay);
            mark(at, delay);
        }

        // 胸の高さから to へ飛ばす (flight tick)。turn = 横へずらす角 (度)、delay tick 後に放つ。模型の芯は大きさ × 0.25 ブロック上
        void fly(String key, Vec3 to, int flight, float size, float turn, int delay) {
            float s = size * grow;
            Vec3 lift = new Vec3(0, 0.25 * s, 0);
            Vec3 d = fwd.yRot((float) Math.toRadians(-turn));
            Vec3 from = chest().add(d.scale(0.6)).subtract(lift);
            Vec3 aim = turn == 0f ? to : chest().add(d.scale(to.subtract(foot()).horizontalDistance()));
            Vec3 end = aim.subtract(lift);
            if (delay <= 0) {
                AttackProps.fly(level, "ss_" + key, p, from, end, flight, s);
            } else {
                SkillEffects.later(level, delay, () -> {
                    if (p.isAlive() && p.level() == level) {
                        AttackProps.fly(level, "ss_" + key, p, from, end, flight, s);
                    }
                });
            }
            mark(ground(aim), delay + flight);
        }

        // 空から at へ降らせる (arrive tick 後に着く)
        void fall(String key, Vec3 at, int arrive, float size) {
            at = ground(at);
            AttackProps.fall(level, "ss_" + key, p, at, Math.max(2, arrive), Math.min(8, Math.max(2, arrive)), 6f, size * grow, 10);
            mark(at, arrive);
        }
    }
}
