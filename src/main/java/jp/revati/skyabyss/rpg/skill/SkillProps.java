package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.prop.AttackProps;
import jp.revati.skyabyss.item.extra.Element;
import jp.revati.skyabyss.item.extra.PowderArts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 自分の技の形 (2026-11-12 の声「こちらのスキルや攻撃で敵のボスみたいに下からモデルが出るとか、モデルが飛んでいくみたいな」)。
// 技を使った瞬間 (SkillManager の見た目の合図と同じ所) に、職の模型を床から突き出す・飛ばす・降らせる。見た目だけで、当たりは技そのものが持つ。
// 手元の板の段取り (rpg/client/skill/SkillFx) と同じ数・同じ時刻に合わせる (溜め → 放ち → 当たり)。
//   模型    prop_sk_<職>_<案>_<rise|shot> (tools/gen/mobs/attack_props/build_skill_props.py)。どれも青緑に光る人の技の印を持つ (敵の技と見分ける)
//   案      職ごとに a / b / c (形の違う三つ)。STYLE で選ぶ (2026-11-12 におすすめのまま決まった)
//   淵・前世 一つの案 (sk_deep_rise・sk_past_rise と、前世の光は light_pillar)
// 型を増やした (2026-11-12 の声「単体攻撃だけでなく、範囲攻撃等もっといろんな攻撃の種類があるので、もっと種類は作るべき」。
// 「属性変わった時とか、やり込みとかで少しずつ見た目変わったりとか、スキル変化とかに対応できないんじゃない？」)。tools/gen/mobs/attack_props/build_skill_kit.py:
//   型      prop_sk_<職>_<slash|wave|aura|zone>: 斬撃の弧 (前へ飛ぶ)・衝撃の輪 (大きさ = 半径)・身にまとう輪・床に残る陣 (大きさ = 半径)
//   粉      武器の粉の技が働いていれば (いちばん重い粉)、形の置き場の三か所まで prop_sk_el_<粉> を添える
//   変化    技を変えているもの (SkillChanges: 書き換え > 変異 > 秘伝 > 刻印) があれば、技の芯に prop_sk_ch_<種類> を添える
//   熟練    技の熟練の段 (SkillData.masteryLevel 0〜5) で形が段ごとに 4% 大きくなり、3 から足もとに棘の輪 (sk_mastery_3)、5 で冠 (sk_mastery_5)
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
    // 熟練の段ごとの大きさ
    static final float MASTERY_GROW = 0.04f;
    // 粉の印を添える置き場の数
    static final int ELEMENT_MARKS = 3;
    // 衝撃の輪・陣の大きさの上限 (半径。大きすぎると板が高くなりすぎる)
    static final double WAVE_MAX = 2.6;
    static final double ZONE_MAX = 6.0;

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

    // 型の模型 (role = slash | wave | aura | zone)。職の木でなければ null
    @Nullable
    public static String roleKind(SkillTree t, String role) {
        String j = job(t);
        return j.isEmpty() ? null : "sk_" + j + "_" + role;
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
        ServerLevel level = c.level;
        Vec3 target = c.target;
        switch (fx) {
            // ---------------------------------------------------------------- 戦士 (岩守り)
            case TITAN_CLEAVE -> {
                c.slash(3.5, 3, 1.8f, 0f);
                c.rise(c.front(3.0), 0.9f, 14, 4);
            }
            case ROCK_CALL -> {
                c.wave(c.foot(), 2.4, 14, 0);
                c.around(5.0, 8, 0.5f, 10, 4);
            }
            case WHALE_SWEEP -> {
                for (int k = 0; k < 3; k++) {
                    c.slash(3.0, 3, 1.6f, 120f * k, k * 4);
                }
                c.spinRing(3.5, 8, 0.5f, 10, 1, 12);
            }
            case SHIELD_RUSH -> {
                c.path(0.5, 6.0, 2.0, 0.45f, 8, 1);
                c.wave(c.front(6.5), 1.4, 10, 5);
            }
            case SHIELD_BASH -> {
                c.slash(2.0, 2, 1.2f, 0f);
                c.rise(c.front(2.0), 0.6f, 10, 1);
            }
            case BONE_SWING -> {
                c.slash(3.0, 3, 2.0f, 0f, 3);
                c.rise(c.front(3.0), 1.1f, 14, 6);
            }
            case SPIN_CUT -> {
                c.wave(c.foot(), 2.5, 10, 2);
                c.spinRing(2.5, 6, 0.5f, 8, 0, 4);
            }
            // ---------------------------------------------------------------- 暗殺者 (風読み)
            case LUNGE -> {
                c.path(1.0, 8.0, 2.4, 0.45f, 8, 1);
                c.slash(9.0, 4, 1.5f, 0f, 3);
            }
            case BACKSTEP -> {
                c.rise(c.foot(), 0.6f, 10, 0);
                c.zone(c.foot(), 1.6, 16, 0);
            }
            case WIND_REND -> {
                c.toss(c.chest().add(c.side(1.6)), c.front(4.0).add(c.side(-1.6)).add(0, 1, 0), 3, 0.9f);
                c.toss(c.chest().add(c.side(-1.6)), c.front(4.0).add(c.side(1.6)).add(0, 1, 0), 7, 0.9f);
                c.slash(4.0, 3, 1.6f, 0f, 2);
            }
            case MIST_VEIL -> {
                c.aura(1.2f, 30, 0);
                c.around(1.6, 5, 0.4f, 20, 0);
            }
            case TWIN_FANG -> {
                c.toss(c.chest(), c.front(3.0).add(0, 1, 0), 2, 0.7f);
                c.toss(c.chest(), c.front(3.0).add(0, 1.2, 0), 6, 0.7f);
                c.slash(3.0, 2, 1.3f, 25f, 0);
                c.slash(3.0, 2, 1.3f, -25f, 4);
            }
            // ---------------------------------------------------------------- 呪術師 (潮歌い)
            case WARD_SONG -> {
                c.aura(1.2f, 24, 0);
                c.rise(c.foot(), 0.8f, 18, 1);
                c.around(6.0, 8, 0.4f, 10, 8);
            }
            case EBB_TIDE -> {
                c.slash(4.0, 4, 1.8f, 0f);
                for (int k = -1; k <= 1; k++) {
                    c.rise(c.foot().add(c.dir(Math.toRadians(30 * k)).scale(4.0)), 0.6f, 12, 4);
                }
            }
            case CYCLE_SONG -> {
                c.aura(1.3f, 24, 0);
                c.wave(c.foot(), 3.0, 12, 4);
                c.around(8.0, 8, 0.45f, 10, 8);
            }
            case CURSE_MARK -> {
                Vec3 t = c.targetOr(10.0);
                c.toss(c.chest(), t.add(0, 0.8, 0), 3, 0.8f);
                c.zone(t, 1.6, 40, 3);
                c.rise(t, 0.6f, 14, 3);
            }
            case LAMP_CHARM -> {
                c.rise(c.front(1.0), 0.7f, 100, 0);
                c.aura(1.0f, 20, 0);
            }
            // ---------------------------------------------------------------- 鍛冶師 (灰の鍛え人)
            case TEMPER -> {
                c.aura(1.2f, 24, 0);
                c.around(1.5, 3, 0.5f, 14, 0);
            }
            case EMBER_HAMMER -> {
                c.slash(2.0, 2, 1.4f, 0f);
                c.rise(c.front(2.0), 1.0f, 16, 3);
                c.wave(c.front(2.0), 1.6, 10, 3);
            }
            case ASH_SCATTER -> {
                c.zone(c.foot(), 5.0, 40, 2);
                c.around(5.0, 8, 0.5f, 10, 8);
            }
            case FORGE_STOMP -> {
                c.wave(c.foot(), 2.6, 10, 0);
                c.wave(c.foot(), 3.5, 10, 3);
                c.around(5.0, 10, 0.45f, 10, 3);
            }
            case SPARK_BURST -> {
                c.slash(3.0, 3, 1.4f, 0f);
                c.scatter(3.0, 5, 0.4f, 10, 1);
            }
            // ---------------------------------------------------------------- 弓使い (骨の射手)
            case CLOUD_PIERCE -> {
                Vec3 t = c.targetOr(24.0);
                c.toss(c.chest(), t.add(0, 1.2, 0), 6, 1.2f);
                c.wave(t, 1.4, 10, 6);
            }
            case BONE_TRAP -> {
                c.zone(c.front(1.0), 2.0, 60, 2);
                c.around(1.0, 3, 0.5f, 30, 8);
            }
            case SKY_RAIN -> {
                Vec3 t = c.targetOr(12.0);
                c.zone(t, 3.6, 70, 0);
                for (int k = 0; k < 8; k++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(level.random.nextDouble()) * 3.6;
                    c.fall(t.add(Math.cos(a) * d, 0, Math.sin(a) * d), 10 + k * 10, 0.5f, "rise");
                }
            }
            case ARCHER_LEAP -> {
                c.rise(c.foot(), 0.5f, 10, 0);
                c.aura(1.0f, 14, 0);
            }
            case SNARE_SHOT -> {
                Vec3 t = c.targetOr(12.0);
                c.toss(c.chest(), t.add(0, 1, 0), 3, 0.9f);
                c.zone(t, 1.6, 40, 3);
                c.rise(t, 0.4f, 16, 3);
            }
            // ---------------------------------------------------------------- 魔法使い (星読み)
            case THUNDER_BOLT -> {
                Vec3 t = c.targetOr(16.0);
                c.toss(c.chest(), t.add(0, 1, 0), 2, 0.8f);
                c.rise(t, 0.5f, 10, 2);
            }
            case EMBER_ORB -> {
                Vec3 t = c.front(14.0);
                c.toss(c.chest(), t.add(0, 1.2, 0), 10, 1.0f);
                c.wave(t, 2.0, 10, 10);
            }
            case FROST_TIDE -> {
                c.wave(c.foot(), 3.0, 12, 0);
                c.around(5.0, 10, 0.4f, 12, 3);
            }
            case BLINK -> {
                c.aura(1.0f, 12, 0);
                if (target != null) {
                    c.aura(1.0f, 12, 1, target);
                }
            }
            case STARDUST -> {
                Vec3 t = c.targetOr(10.0);
                c.zone(t, 3.5, 30, 0);
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
            case ECHO_STRIKE -> {
                AttackProps.riseLater(level, "light_pillar", c.p, c.front(2.5), 0f, 0.5f, 10, 1);
                c.mark(c.front(2.5), 1);
            }
            case GRAVE_BELL -> {
                c.fall(c.foot(), 3, 2.2f, "rise");
                c.mark(c.foot(), 3);
            }
            case FINAL_LIGHT -> {
                int i = 0;
                for (double r : new double[] {1.8, 3.7, 5.5}) {
                    for (int k = 0; k < 6 + i * 3; k++) {
                        double a = k * Math.PI * 2 / (6 + i * 3);
                        AttackProps.riseLater(level, "light_pillar", c.p, c.foot().add(Math.cos(a) * r, 0, Math.sin(a) * r), (float) Math.toDegrees(a), 0.35f, 8,
                                3 + i * 3);
                    }
                    i++;
                }
                c.mark(c.foot(), 3);
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

        void rise(Vec3 at, float size, int linger, int delay) {
            AttackProps.riseLater(level, kind(tree, "rise"), p, at, yaw() + level.random.nextFloat() * 30f - 15f, size * RISE_SCALE * grow, linger, delay);
            mark(at, delay);
        }

        // 足もとのまわりの輪に n 本 (同時)
        void around(double r, int n, float size, int linger, int delay) {
            double off = level.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = off + i * Math.PI * 2 / n;
                Vec3 at = foot().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                AttackProps.riseLater(level, kind(tree, "rise"), p, at, (float) Math.toDegrees(a), size * RISE_SCALE * grow, linger, delay);
                mark(at, delay);
            }
        }

        // 回る輪: 後ろから一回りしながら順に (t0 から t1 tick)
        void spinRing(double r, int n, float size, int linger, int t0, int t1) {
            double start = Math.atan2(fwd.z, fwd.x) + Math.PI;
            for (int i = 0; i < n; i++) {
                double a = start + i * Math.PI * 2 / n;
                int delay = t0 + (t1 - t0) * i / Math.max(1, n - 1);
                Vec3 at = foot().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                AttackProps.riseLater(level, kind(tree, "rise"), p, at, (float) Math.toDegrees(a), size * RISE_SCALE * grow, linger, delay);
                mark(at, delay);
            }
        }

        // 前への道に沿って順に (from から to まで step おき、1 本ごとに perTick tick ずつ遅れる)
        void path(double from, double to, double step, float size, int linger, int perTick) {
            int i = 0;
            for (double d = from; d <= to; d += step, i++) {
                AttackProps.riseLater(level, kind(tree, "rise"), p, front(d), yaw() + i * 40f, size * RISE_SCALE * grow, linger, i * perTick);
                mark(front(d), i * perTick);
            }
        }

        // 円の中の n か所 (少しずつずらして)
        void scatter(double r, int n, float size, int linger, int delay) {
            for (int i = 0; i < n; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = r * Math.sqrt(level.random.nextDouble());
                Vec3 at = foot().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                AttackProps.riseLater(level, kind(tree, "rise"), p, at, (float) Math.toDegrees(a), size * RISE_SCALE * grow, linger, delay + i);
                mark(at, delay + i);
            }
        }

        // from から to へ飛ぶ (arrive tick 後に着く)
        void toss(Vec3 from, Vec3 to, int arrive, float size) {
            AttackProps.toss(level, kind(tree, "shot"), p, from, to, Math.max(2, arrive), Math.max(2, arrive), 0.3f, size * SHOT_SCALE * grow, 3);
        }

        // 空から at へ (arrive tick 後に着く)。role = 降らせる模型 (rise の矢・鐘は先が下)
        void fall(Vec3 at, int arrive, float size, String role) {
            AttackProps.fall(level, kind(tree, role), p, at, Math.max(2, arrive), Math.min(8, Math.max(2, arrive)), 6f,
                    size * (role.equals("shot") ? SHOT_SCALE : RISE_SCALE) * grow, 10);
        }

        // 斬撃の弧: 胸の高さから前へ dist だけ飛ぶ (flight tick)。turn = 前から回す角 (度)、delay tick 後に放つ
        void slash(double dist, int flight, float size, float turn) {
            slash(dist, flight, size, turn, 0);
        }

        void slash(double dist, int flight, float size, float turn, int delay) {
            String k = roleKind(tree, "slash");
            if (k == null) {
                return;
            }
            float s = size * grow;
            Vec3 d = fwd.yRot((float) Math.toRadians(-turn));
            Vec3 lift = new Vec3(0, 0.25 * s, 0);
            Vec3 from = foot().add(0, 1.0, 0).add(d.scale(0.6)).subtract(lift);
            Vec3 to = foot().add(0, 1.0, 0).add(d.scale(dist)).subtract(lift);
            if (delay <= 0) {
                AttackProps.fly(level, k, p, from, to, flight, s);
            } else {
                jp.revati.skyabyss.rpg.skill.SkillEffects.later(level, delay, () -> {
                    if (p.isAlive() && p.level() == level) {
                        AttackProps.fly(level, k, p, from, to, flight, s);
                    }
                });
            }
        }

        // 衝撃の輪 (at を中心に半径 r)
        void wave(Vec3 at, double r, int linger, int delay) {
            String k = roleKind(tree, "wave");
            if (k == null) {
                return;
            }
            at = ground(at);
            AttackProps.riseLater(level, k, p, at, level.random.nextFloat() * 360f, (float) Math.min(WAVE_MAX, r) * grow, linger, delay);
            mark(at, delay);
        }

        // 身にまとう輪 (足もと、または at)
        void aura(float size, int linger, int delay) {
            aura(size, linger, delay, foot());
        }

        void aura(float size, int linger, int delay, Vec3 at) {
            String k = roleKind(tree, "aura");
            if (k == null) {
                return;
            }
            at = ground(at);
            AttackProps.riseLater(level, k, p, at, yaw(), size * grow, linger, delay);
            mark(at, delay);
        }

        // 床に残る陣 (at を中心に半径 r)
        void zone(Vec3 at, double r, int linger, int delay) {
            String k = roleKind(tree, "zone");
            if (k == null) {
                return;
            }
            at = ground(at);
            AttackProps.riseLater(level, k, p, at, level.random.nextFloat() * 360f, (float) Math.min(ZONE_MAX, r) * grow, linger, delay);
            mark(at, delay);
        }
    }
}
