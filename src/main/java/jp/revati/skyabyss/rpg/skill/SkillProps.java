package jp.revati.skyabyss.rpg.skill;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.prop.AttackProps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 自分の技の形 (2026-11-12 の声「こちらのスキルや攻撃で敵のボスみたいに下からモデルが出るとか、モデルが飛んでいくみたいな」)。
// 技を使った瞬間 (SkillManager の見た目の合図と同じ所) に、職の模型を床から突き出す・飛ばす・降らせる。見た目だけで、当たりは技そのものが持つ。
// 手元の板の段取り (rpg/client/skill/SkillFx) と同じ数・同じ時刻に合わせる (溜め → 放ち → 当たり)。
//   模型    prop_sk_<職>_<案>_<rise|shot> (tools/gen/mobs/attack_props/build_skill_props.py)。どれも青緑に光る人の技の印を持つ (敵の技と見分ける)
//   案      職ごとに a / b / c (形の違う三つ)。STYLE で選ぶ (朝の頁で決める。docs/review/skill_props/)
//   淵・前世 一つの案 (sk_deep_rise・sk_past_rise と、前世の光は light_pillar)
public final class SkillProps {
    // 職 (木) ごとの案。決まったらここを変えるだけ (模型は三つとも入っている)
    public static final Map<SkillTree, String> STYLE = new EnumMap<>(SkillTree.class);

    static {
        STYLE.put(SkillTree.ROCK, "a");
        STYLE.put(SkillTree.WIND, "a");
        STYLE.put(SkillTree.TIDE, "a");
        STYLE.put(SkillTree.ASH, "b");
        STYLE.put(SkillTree.BONE, "a");
        STYLE.put(SkillTree.STAR, "a");
    }

    // 人の目の高さ・三人称の背中から読める大きさへの倍率 (2026-11-12 の撮影で、敵の技と同じ大きさでは小さく見えた)
    static final float RISE_SCALE = 1.5f;
    static final float SHOT_SCALE = 1.3f;

    private SkillProps() {
    }

    static String job(SkillTree t) {
        return switch (t) {
            case ROCK -> "warrior";
            case WIND -> "assassin";
            case TIDE -> "shaman";
            case ASH -> "smith";
            case BONE -> "archer";
            case STAR -> "mage";
            default -> "";
        };
    }

    // 職の模型の名前 (role = rise | shot)。戦士は shot を持たないので rise
    public static String kind(SkillTree t, String role) {
        String j = job(t);
        if (j.isEmpty()) {
            return t == SkillTree.DEEP ? "sk_deep_rise" : "sk_past_rise";
        }
        if (j.equals("warrior")) {
            role = "rise";
        }
        return "sk_" + j + "_" + STYLE.getOrDefault(t, "a") + "_" + role;
    }

    // 技を使った瞬間 (SkillManager.cast の見た目の合図のあと)。fx = 見た目の技 (変化の中身)、target = サーバーが決めた点 (なければ null)
    public static void onCast(ServerPlayer p, Skill fx, int rank, @Nullable Vec3 target) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        Cast c = new Cast(level, p, fx.tree, target);
        switch (fx) {
            // ---------------------------------------------------------------- 戦士 (岩守り)
            case TITAN_CLEAVE -> c.rise(c.front(3.0), 0.9f, 14, 4);
            case ROCK_CALL -> {
                c.around(2.0, 3, 0.6f, 14, 0);
                c.around(5.0, 8, 0.5f, 10, 4);
            }
            case WHALE_SWEEP -> c.spinRing(3.5, 8, 0.5f, 10, 1, 12);
            case SHIELD_RUSH -> c.path(0.5, 6.0, 2.0, 0.45f, 8, 1);
            case SHIELD_BASH -> c.rise(c.front(2.0), 0.6f, 10, 1);
            case BONE_SWING -> c.rise(c.front(3.0), 1.1f, 14, 6);
            case SPIN_CUT -> c.spinRing(2.5, 6, 0.5f, 8, 0, 4);
            // ---------------------------------------------------------------- 暗殺者 (風読み)
            case LUNGE -> c.path(1.0, 8.0, 2.4, 0.45f, 8, 1);
            case BACKSTEP -> c.rise(c.foot(), 0.6f, 10, 0);
            case WIND_REND -> {
                c.toss(c.chest().add(c.side(1.6)), c.front(4.0).add(c.side(-1.6)).add(0, 1, 0), 3, 0.9f);
                c.toss(c.chest().add(c.side(-1.6)), c.front(4.0).add(c.side(1.6)).add(0, 1, 0), 7, 0.9f);
            }
            case MIST_VEIL -> c.around(1.6, 5, 0.4f, 20, 0);
            case TWIN_FANG -> {
                c.toss(c.chest(), c.front(3.0).add(0, 1, 0), 2, 0.7f);
                c.toss(c.chest(), c.front(3.0).add(0, 1.2, 0), 6, 0.7f);
            }
            // ---------------------------------------------------------------- 呪術師 (潮歌い)
            case WARD_SONG -> {
                c.rise(c.foot(), 0.8f, 18, 1);
                c.around(6.0, 8, 0.4f, 10, 8);
            }
            case EBB_TIDE -> {
                for (int k = -1; k <= 1; k++) {
                    c.rise(c.foot().add(c.dir(Math.toRadians(30 * k)).scale(4.0)), 0.6f, 12, 4);
                }
            }
            case CYCLE_SONG -> c.around(8.0, 8, 0.45f, 10, 8);
            case CURSE_MARK -> {
                Vec3 t = c.targetOr(10.0);
                c.toss(c.chest(), t.add(0, 0.8, 0), 3, 0.8f);
                c.rise(t, 0.6f, 14, 3);
            }
            case LAMP_CHARM -> c.rise(c.front(1.0), 0.7f, 100, 0);
            // ---------------------------------------------------------------- 鍛冶師 (灰の鍛え人)
            case TEMPER -> c.around(1.5, 3, 0.5f, 14, 0);
            case EMBER_HAMMER -> c.rise(c.front(2.0), 1.0f, 16, 3);
            case ASH_SCATTER -> c.around(5.0, 8, 0.5f, 10, 8);
            case FORGE_STOMP -> {
                c.around(3.0, 6, 0.5f, 10, 0);
                c.around(5.0, 10, 0.45f, 10, 3);
            }
            case SPARK_BURST -> c.scatter(3.0, 5, 0.4f, 10, 1);
            // ---------------------------------------------------------------- 弓使い (骨の射手)
            case CLOUD_PIERCE -> c.toss(c.chest(), c.targetOr(24.0).add(0, 1.2, 0), 6, 1.2f);
            case BONE_TRAP -> c.around(1.0, 3, 0.5f, 30, 8);
            case SKY_RAIN -> {
                Vec3 t = c.targetOr(12.0);
                for (int k = 0; k < 8; k++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(level.random.nextDouble()) * 3.6;
                    c.fall(t.add(Math.cos(a) * d, 0, Math.sin(a) * d), 10 + k * 10, 0.5f, "rise");
                }
            }
            case ARCHER_LEAP -> c.rise(c.foot(), 0.5f, 10, 0);
            case SNARE_SHOT -> {
                Vec3 t = c.targetOr(12.0);
                c.toss(c.chest(), t.add(0, 1, 0), 3, 0.9f);
                c.rise(t, 0.4f, 16, 3);
            }
            // ---------------------------------------------------------------- 魔法使い (星読み)
            case THUNDER_BOLT -> {
                Vec3 t = c.targetOr(16.0);
                c.toss(c.chest(), t.add(0, 1, 0), 2, 0.8f);
                c.rise(t, 0.5f, 10, 2);
            }
            case EMBER_ORB -> c.toss(c.chest(), c.front(14.0).add(0, 1.2, 0), 10, 1.0f);
            case FROST_TIDE -> {
                c.around(3.0, 6, 0.45f, 12, 0);
                c.around(5.0, 10, 0.4f, 12, 3);
            }
            case BLINK -> {
                c.rise(c.foot(), 0.6f, 10, 0);
                if (target != null) {
                    c.rise(target, 0.6f, 10, 1);
                }
            }
            case STARDUST -> {
                Vec3 t = c.targetOr(10.0);
                for (int k = 0; k < 6; k++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(level.random.nextDouble()) * 3.5;
                    c.fall(t.add(Math.cos(a) * d, 0, Math.sin(a) * d), 2 + k, 0.8f, "shot");
                }
            }
            // ---------------------------------------------------------------- 淵・前世
            case DEEP_PULL -> c.rise(c.front(3.0), 0.8f, 14, 4);
            case DEEP_ECHO -> c.rise(c.foot(), 0.5f, 14, 1);
            case DEEP_MAW -> c.rise(c.targetOr(8.0), 1.0f, 14, 7);
            case ECHO_STRIKE -> AttackProps.riseLater(level, "light_pillar", p, c.front(2.5), 0f, 0.5f, 10, 1);
            case GRAVE_BELL -> c.fall(c.foot(), 3, 2.2f, "rise");
            case FINAL_LIGHT -> {
                int i = 0;
                for (double r : new double[] {1.8, 3.7, 5.5}) {
                    for (int k = 0; k < 6 + i * 3; k++) {
                        double a = k * Math.PI * 2 / (6 + i * 3);
                        AttackProps.riseLater(level, "light_pillar", p, c.foot().add(Math.cos(a) * r, 0, Math.sin(a) * r), (float) Math.toDegrees(a), 0.35f, 8,
                                3 + i * 3);
                    }
                    i++;
                }
            }
            default -> {
            }
        }
    }

    // 一つの技の置き場の計算 (人の足もと・向き・狙い)
    static final class Cast {
        final ServerLevel level;
        final ServerPlayer p;
        final SkillTree tree;
        @Nullable
        final Vec3 target;
        final Vec3 fwd;

        Cast(ServerLevel level, ServerPlayer p, SkillTree tree, @Nullable Vec3 target) {
            this.level = level;
            this.p = p;
            this.tree = tree;
            this.target = target;
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

        void rise(Vec3 at, float size, int linger, int delay) {
            AttackProps.riseLater(level, kind(tree, "rise"), p, at, yaw() + level.random.nextFloat() * 30f - 15f, size * RISE_SCALE, linger, delay);
        }

        // 足もとのまわりの輪に n 本 (同時)
        void around(double r, int n, float size, int linger, int delay) {
            double off = level.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = off + i * Math.PI * 2 / n;
                AttackProps.riseLater(level, kind(tree, "rise"), p, foot().add(Math.cos(a) * r, 0, Math.sin(a) * r), (float) Math.toDegrees(a), size * RISE_SCALE, linger, delay);
            }
        }

        // 回る輪: 後ろから一回りしながら順に (t0 から t1 tick)
        void spinRing(double r, int n, float size, int linger, int t0, int t1) {
            double start = Math.atan2(fwd.z, fwd.x) + Math.PI;
            for (int i = 0; i < n; i++) {
                double a = start + i * Math.PI * 2 / n;
                int delay = t0 + (t1 - t0) * i / Math.max(1, n - 1);
                AttackProps.riseLater(level, kind(tree, "rise"), p, foot().add(Math.cos(a) * r, 0, Math.sin(a) * r), (float) Math.toDegrees(a), size * RISE_SCALE, linger, delay);
            }
        }

        // 前への道に沿って順に (from から to まで step おき、1 本ごとに perTick tick ずつ遅れる)
        void path(double from, double to, double step, float size, int linger, int perTick) {
            int i = 0;
            for (double d = from; d <= to; d += step, i++) {
                AttackProps.riseLater(level, kind(tree, "rise"), p, front(d), yaw() + i * 40f, size * RISE_SCALE, linger, i * perTick);
            }
        }

        // 円の中の n か所 (少しずつずらして)
        void scatter(double r, int n, float size, int linger, int delay) {
            for (int i = 0; i < n; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = r * Math.sqrt(level.random.nextDouble());
                AttackProps.riseLater(level, kind(tree, "rise"), p, foot().add(Math.cos(a) * d, 0, Math.sin(a) * d), (float) Math.toDegrees(a), size * RISE_SCALE, linger,
                        delay + i);
            }
        }

        // from から to へ飛ぶ (arrive tick 後に着く)
        void toss(Vec3 from, Vec3 to, int arrive, float size) {
            AttackProps.toss(level, kind(tree, "shot"), p, from, to, Math.max(2, arrive), Math.max(2, arrive), 0.3f, size * SHOT_SCALE, 3);
        }

        // 空から at へ (arrive tick 後に着く)。role = 降らせる模型 (rise の矢・鐘は先が下)
        void fall(Vec3 at, int arrive, float size, String role) {
            AttackProps.fall(level, kind(tree, role), p, at, Math.max(2, arrive), Math.min(8, Math.max(2, arrive)), 6f, size * (role.equals("shot") ? SHOT_SCALE : RISE_SCALE), 10);
        }
    }
}
