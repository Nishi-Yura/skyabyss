package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.rpg.skill.net.GaugePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// 型ごとの「溜まるもの」(docs/design/48 の 2.6。2026-11-02 の 00 の 342 A・345)。
// Wynncraft の型の仕組みのように、ある技 (や近接・矢) で溜め、別の技で放つ。技の順番に意味を持たせる。
//   出る条件: 今の職の木で、その型に 8 点以上使っている (二つ以上なら点の多い型)。ログアウト・死・職替えで空に戻る
//   見せ方: マナの帯の上の細い帯 (VitalsHud。GaugePayload で持ち主へ)。型が変わったときに一行の知らせ
// 数値はこの class の定数。技の中身からは onSkillHit (SkillActions.hit)・power (SkillEffects.damagePower)・
// beforeCast / afterCast (SkillManager.cast)・近接と傷 (SkillEvents) を呼ぶ
public final class Gauges {
    private Gauges() {
    }

    public static final int ARCH_POINTS = 8;

    public enum Kind {
        // 戦士
        SHIELD(Job.WARRIOR, "shield", 100, 0xFFB9C6CF),
        CLEAVER(Job.WARRIOR, "cleaver", 5, 0xFFE8D7A8),
        TAIL(Job.WARRIOR, "tail", 10, 0xFF8FD3E0),
        // 鍛冶師
        EMBER(Job.SMITH, "ember", 100, 0xFFF0763A),
        TEMPER(Job.SMITH, "temper", 10, 0xFFFFB347),
        ASH(Job.SMITH, "ash", 5, 0xFF9A9088),
        // 魔法使い
        THUNDER(Job.MAGE, "thunder", 3, 0xFFF4E46A),
        FIRE(Job.MAGE, "fire", 5, 0xFFF06A3A),
        TIDE(Job.MAGE, "tide", 5, 0xFF8FD8F4),
        // 弓使い
        PIERCE(Job.ARCHER, "pierce", 3, 0xFFE8E4D6),
        TRAP(Job.ARCHER, "trap", 3, 0xFFD8C8A0),
        RAIN(Job.ARCHER, "rain", 20, 0xFFB8E0FF),
        // 暗殺者
        SHADOW(Job.ASSASSIN, "shadow", 3, 0xFFB06AE0),
        DANCE(Job.ASSASSIN, "dance", 4, 0xFF7FE0B0),
        LONE(Job.ASSASSIN, "lone", 120, 0xFFC8D0E8),
        // 呪術師
        WARD(Job.SHAMAN, "ward", 3, 0xFFF2D78A),
        EBB(Job.SHAMAN, "ebb", 8, 0xFF5AB4E8),
        CURSE(Job.SHAMAN, "curse", 3, 0xFF9A5AD0);

        public final Job job;
        public final String arch;
        public final int max;
        public final int color;

        Kind(Job job, String arch, int max, int color) {
            this.job = job;
            this.arch = arch;
            this.max = max;
            this.color = color;
        }

        public String nameKey() {
            return "gauge.skyabyss." + job.id + "_" + arch;
        }

        // 小さい数は点で、大きい数 (盾の重み・炉の熱・孤り) は帯で見せる
        public boolean pips() {
            return max <= 20;
        }

        public static Kind of(Job job, String arch) {
            for (Kind k : values()) {
                if (k.job == job && k.arch.equals(arch)) {
                    return k;
                }
            }
            return null;
        }

        public static Kind byOrdinal(int i) {
            return i >= 0 && i < values().length ? values()[i] : null;
        }
    }

    // ---------------------------------------------------------------- 数値
    static final double SHIELD_HURT = 15;
    static final double SHIELD_SKILL = 25;
    static final double SMASH_RADIUS = 4;
    static final double SMASH_MULT = 2.5;
    static final int SMASH_BIND = 30;
    static final double CLEAVER_POWER = 0.20;
    static final double TAIL_ASPEED = 0.03;
    static final double TAIL_MOVE = 0.02;
    static final int TAIL_KEEP = 40;
    static final double EMBER_PER_CAST = 20;
    static final double FORGE_OPEN_MULT = 3.0;
    static final int TEMPER_BOOST = 120;
    static final double ASH_PER_LAYER = 0.6;
    static final double THUNDER_CHAIN_RANGE = 6;
    static final int THUNDER_CHAIN_COUNT = 5;
    static final double THUNDER_CHAIN_SCALE = 0.6;
    static final int THUNDER_KEEP = 120;
    static final double FIRE_POWER = 0.20;
    static final int FREEZE_TICKS = 40;
    static final int FREEZE_AGAIN = 160;
    static final double FROZEN_BOLT = 0.5;
    static final double PIERCE_POWER = 0.30;
    static final double TRAP_THREAD_SLOW = 0.5;
    static final double TRAP_BURST_RANGE = 6;
    static final double TRAP_BURST_MULT = 1.2;
    static final double RAIN_ARROW = 0.30;
    static final double SHADOW_BURST = 1.0;
    static final double DANCE_POWER = 0.08;
    static final double DANCE_COOLDOWN = 0.05;
    static final int DANCE_KEEP = 120;
    static final double LONE_RANGE = 12;
    static final double LONE_STRIKE = 2.5;
    static final double WARD_RADIUS = 1.0;
    static final int WARD_TICKS = 60;
    static final double TIDE_WAVE_RADIUS = 6;
    static final double TIDE_WAVE_MULT = 2.0;
    static final double CURSE_STEP = 0.5;
    static final double CURSE_BURST = 3.0;
    static final double CURSE_SPREAD = 6;

    // ---------------------------------------------------------------- 人ごとの状態 (保存しない)
    static final class State {
        Kind kind;
        double value;
        long lastGain;
        // 送った値 (変わったら送る)
        int sentKind = -2;
        int sentValue = -1;
        // 型ごとの補助
        LivingEntity target;
        String lastSkill;
        long lastCast;
        long lastCastGain;
        long boostUntil;
        long lastHurtGain;
        Vec3 lastPos;
        int stillTicks;
        // 開発用に決めた型 (点の決まりで選び直さない)
        boolean forced;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    // 雷の帯電・呪いの重ね (敵ごと)、凍て潮で冷えた敵・凍った敵
    private static final Map<LivingEntity, long[]> CHARGES = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> CHILLED = new WeakHashMap<>();
    private static final Map<LivingEntity, long[]> FROZEN = new WeakHashMap<>();
    private static boolean busy;

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), u -> new State());
    }

    public static Kind kindOf(ServerPlayer player) {
        return state(player).kind;
    }

    public static double value(ServerPlayer player) {
        return state(player).value;
    }

    // 今の職の木で、点の多い型 (8 点以上) の溜まるもの
    static Kind pick(ServerPlayer player) {
        SkillData d = SkillManager.get(player);
        Job job = Job.byId(d.curJob);
        if (job == null) {
            return null;
        }
        Kind best = null;
        int bestPoints = ARCH_POINTS - 1;
        for (Kind k : Kind.values()) {
            if (k.job != job) {
                continue;
            }
            int pts = d.archPoints(job, k.arch);
            if (pts > bestPoints) {
                best = k;
                bestPoints = pts;
            }
        }
        return best;
    }

    // 開発用 (/skyabyss skill gauge): 型と値を決める。off で点の決まりに戻す
    public static boolean devSet(ServerPlayer player, String kind, int value) {
        State s = state(player);
        if (kind.equals("off")) {
            s.forced = false;
            return true;
        }
        Kind k;
        try {
            k = Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        force(player, k);
        s.forced = true;
        set(player, value);
        send(player, true);
        return true;
    }

    // 自己テスト用: 型を決めて空にする
    static void force(ServerPlayer player, Kind kind) {
        State s = state(player);
        s.kind = kind;
        s.value = 0;
        s.target = null;
        s.lastSkill = null;
        s.lastCastGain = -1;
        s.lastHurtGain = -1000;
    }

    static boolean is(ServerPlayer player, Kind kind) {
        return state(player).kind == kind;
    }

    static void set(ServerPlayer player, double v) {
        State s = state(player);
        if (s.kind == null) {
            return;
        }
        double nv = Math.max(0, Math.min(s.kind.max, v));
        if (nv > s.value) {
            s.lastGain = player.level().getGameTime();
        }
        s.value = nv;
        if (s.kind == Kind.TAIL) {
            applyTail(player, nv);
        }
    }

    static void add(ServerPlayer player, double v) {
        set(player, state(player).value + v);
    }

    static boolean full(ServerPlayer player) {
        State s = state(player);
        return s.kind != null && s.value >= s.kind.max - 1e-6;
    }

    // ---------------------------------------------------------------- 技の傷の倍率 (SkillEffects.damagePower)
    public static double power(ServerPlayer player, Skill skill) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.kind == null) {
            return 0;
        }
        return switch (s.kind) {
            case CLEAVER -> skill == Skill.TITAN_CLEAVE ? CLEAVER_POWER * s.value : 0;
            case PIERCE -> skill == Skill.CLOUD_PIERCE ? PIERCE_POWER * s.value : 0;
            case DANCE -> DANCE_POWER * s.value;
            default -> 0;
        };
    }

    // 狙う相手ごとの倍率 (SkillActions.hit): 凍った敵への雷の矢
    public static double versus(ServerPlayer player, LivingEntity target, Skill skill) {
        if (skill == Skill.THUNDER_BOLT && frozen(target)) {
            return 1 + FROZEN_BOLT;
        }
        return 1;
    }

    // 雲穿ちの貫きの上乗せ (狙いの段)
    public static int pierceBonus(ServerPlayer player) {
        return is(player, Kind.PIERCE) ? (int) state(player).value : 0;
    }

    // 熾火の玉の大きさと力 (熾火の数。玉は飛んでから弾けるので、撃つときに決める)
    public static int orbStacks(ServerPlayer player) {
        return is(player, Kind.FIRE) ? (int) state(player).value : 0;
    }

    public static double orbPower(int stacks) {
        return 1 + FIRE_POWER * stacks;
    }

    // 灯の守り札の広さと長さ (調べの数)
    public static double lampRadius(ServerPlayer player) {
        return is(player, Kind.WARD) ? WARD_RADIUS * state(player).value : 0;
    }

    public static int lampTicks(ServerPlayer player) {
        return is(player, Kind.WARD) ? (int) (WARD_TICKS * state(player).value) : 0;
    }

    // 焼き入れの炎が二倍の間 (鍛えが満ちた)
    public static boolean temperBoost(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        return s != null && s.kind == Kind.TEMPER && s.boostUntil >= player.level().getGameTime();
    }

    // ---------------------------------------------------------------- 技を使う前とあと (SkillManager.cast)
    // この技の待ちに掛ける倍率 (舞の段)
    public static double cooldownScale(ServerPlayer player, Skill skill) {
        State s = STATES.get(player.getUUID());
        return s != null && s.kind == Kind.DANCE ? 1 - DANCE_COOLDOWN * s.value : 1;
    }

    public static void afterCast(ServerPlayer player, Skill skill) {
        State s = state(player);
        if (s.kind == null) {
            return;
        }
        long now = player.level().getGameTime();
        switch (s.kind) {
            case SHIELD -> {
                // 技の木 v3: 盾殴りは盾の踏み込みの変化になったので、満ちた盾の踏み込み (どの変化でも) で放つ
                if ((skill == Skill.SHIELD_RUSH || skill == Skill.SHIELD_BASH) && full(player)) {
                    rockSmash(player);
                    set(player, 0);
                } else if (skill == Skill.SHIELD_RUSH || skill == Skill.ROCK_CALL || skill == Skill.SHIELD_BASH) {
                    add(player, SHIELD_SKILL);
                }
            }
            case CLEAVER -> {
                if (skill == Skill.TITAN_CLEAVE) {
                    if (s.value > 0) {
                        prop(player.serverLevel(), player, "gx_cleaver", ahead(player, 2.5), 0.7f + 0.1f * (float) s.value, 14);
                    }
                    set(player, 0);
                }
            }
            case EMBER -> {
                if (skill == Skill.EMBER_HAMMER && full(player)) {
                    forgeOpen(player);
                    set(player, 0);
                } else {
                    add(player, EMBER_PER_CAST);
                }
            }
            case ASH -> {
                // 火花散らしは炉の踏みの変化になったので、炉の踏み (どの変化でも) で放つ
                if ((skill == Skill.FORGE_STOMP || skill == Skill.SPARK_BURST) && s.value > 0) {
                    ashBurn(player, s.value);
                    set(player, 0);
                }
            }
            case FIRE -> {
                if (skill == Skill.EMBER_ORB) {
                    if (s.value > 0) {
                        prop(player.serverLevel(), player, "gx_fire", ahead(player, 1.6), 0.5f + 0.1f * (float) s.value, 12);
                    }
                    set(player, 0);
                }
            }
            case PIERCE -> {
                if (skill == Skill.CLOUD_PIERCE) {
                    if (s.value > 0) {
                        float size = 1.2f + 0.2f * (float) s.value;
                        Vec3 lift = new Vec3(0, 0.25 * size, 0);
                        Vec3 eye = player.getEyePosition().subtract(lift);
                        jp.revati.skyabyss.combat.prop.AttackProps.fly(player.serverLevel(), "gx_pierce", player, eye, eye.add(player.getLookAngle().scale(22)), 8, size);
                    }
                    set(player, 0);
                }
            }
            case RAIN -> {
                if (skill == Skill.SKY_RAIN && s.value > 0 && SkillActions.fxTarget != null) {
                    rainDown(player, SkillActions.fxTarget, (int) s.value);
                    set(player, 0);
                }
            }
            case DANCE -> {
                // 違う技をつなぐと一段上がる。同じ技を続けると戻る (この技の力と待ちには前の段が効いている)
                set(player, s.lastSkill != null && !s.lastSkill.equals(skill.id) ? s.value + 1 : 0);
                s.lastSkill = skill.id;
            }
            case WARD -> {
                if (skill == Skill.LAMP_CHARM) {
                    if (s.value > 0) {
                        prop(player.serverLevel(), player, "gx_ward", player.position(), (float) Math.min(3.0, 1.0 + 0.6 * s.value), 40);
                    }
                    set(player, 0);
                } else if (skill == Skill.WARD_SONG || skill == Skill.CYCLE_SONG) {
                    add(player, 1);
                }
            }
            case EBB -> {
                // 潮位が満ちていたら、この技が満ち潮になる (引き潮で満ちたばかりなら、次の技から)
                if (full(player) && !(skill == Skill.EBB_TIDE && s.lastGain == now)) {
                    tideWave(player);
                    set(player, 0);
                }
            }
            default -> {
            }
        }
        s.lastCast = now;
        send(player, false);
    }

    // ---------------------------------------------------------------- 技が当たった (SkillActions.hit。実際に傷が入ったとき)
    public static void onSkillHit(ServerPlayer player, LivingEntity target, Skill skill) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.kind == null || busy) {
            return;
        }
        long now = player.level().getGameTime();
        switch (s.kind) {
            case TAIL -> {
                if (skill == Skill.SPIN_CUT || skill == Skill.WHALE_SWEEP) {
                    add(player, 1);
                }
            }
            case ASH -> {
                if (skill != Skill.SPARK_BURST && SkillEffects.ashed(target) && once(s, now)) {
                    add(player, 1);
                }
            }
            case FIRE -> {
                if (skill != Skill.EMBER_ORB && target.isOnFire() && once(s, now)) {
                    add(player, 1);
                }
            }
            case TIDE -> {
                Long chill = CHILLED.get(target);
                if (chill != null && chill >= now) {
                    freeze(player, target, now);
                }
            }
            case THUNDER -> {
                if (skill == Skill.THUNDER_BOLT) {
                    long[] c = CHARGES.get(target);
                    if (c == null || c[1] < now) {
                        c = new long[] {0, 0};
                    }
                    if (c[0] >= Kind.THUNDER.max) {
                        chain(player, target);
                        c[0] = 0;
                    } else {
                        c[0]++;
                    }
                    c[1] = now + THUNDER_KEEP;
                    CHARGES.put(target, c);
                    s.target = target;
                    set(player, c[0]);
                }
            }
            case SHADOW -> {
                if (skill == Skill.LUNGE || skill == Skill.TWIN_FANG) {
                    if (s.target != target) {
                        s.target = target;
                        set(player, 0);
                    }
                    if (once(s, now)) {
                        add(player, 1);
                    }
                } else if (skill == Skill.WIND_REND && s.target == target && s.value > 0) {
                    float amount = SkillActions.attackDamage(player) * (float) (SHADOW_BURST * s.value * SkillEffects.damagePower(player, Skill.WIND_REND));
                    set(player, 0);
                    busy = true;
                    try {
                        SkillActions.hitPlain(player, target, amount, Skill.WIND_REND);
                    } finally {
                        busy = false;
                    }
                    burst(player.serverLevel(), target, ParticleTypes.WITCH, 20);
                    prop(player.serverLevel(), player, "gx_shadow", target.position(), Math.max(0.7f, target.getBbWidth() * 0.9f), 14);
                    player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.4f);
                }
            }
            case TRAP -> {
                // 足止めの矢は雲穿ちの変化になったので、雲穿ち (どの変化でも) を当てた敵のまわりではぜる
                if (skill == Skill.SNARE_SHOT || skill == Skill.CLOUD_PIERCE) {
                    trapBurst(player, target);
                }
            }
            default -> {
            }
        }
        send(player, false);
    }

    // 範囲の技で何体に当たっても、一回の技で一つだけ溜める
    private static boolean once(State s, long now) {
        if (s.lastCastGain == now) {
            return false;
        }
        s.lastCastGain = now;
        return true;
    }

    // ---------------------------------------------------------------- 呪いの印を付けた (JobActions.curseMark)
    public static void onCurse(ServerPlayer player, LivingEntity target, int rank, int ticks) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.kind != Kind.CURSE) {
            return;
        }
        long now = player.level().getGameTime();
        if (s.target != target) {
            s.target = target;
            set(player, 0);
        }
        add(player, 1);
        double bonus = SkillRules.curseBonus(rank) * (1 + CURSE_STEP * (s.value - 1));
        SkillEvents.mark(target, bonus, now, ticks, player);
        if (full(player)) {
            float amount = SkillActions.attackDamage(player) * (float) (CURSE_BURST * SkillEffects.damagePower(player, Skill.CURSE_MARK));
            busy = true;
            try {
                SkillActions.hitPlain(player, target, amount, Skill.CURSE_MARK);
            } finally {
                busy = false;
            }
            burst(player.serverLevel(), target, ParticleTypes.SOUL_FIRE_FLAME, 24);
            prop(player.serverLevel(), player, "gx_curse", target.position(), Math.max(0.7f, target.getBbWidth() * 0.9f), 16);
            player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.9f, 0.8f);
            // まわりの一体へ印が一つ移る
            LivingEntity next = null;
            for (LivingEntity e : target.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CURSE_SPREAD),
                    e -> e != target && SkillActions.isFoe(player, e))) {
                if (next == null || e.distanceToSqr(target) < next.distanceToSqr(target)) {
                    next = e;
                }
            }
            set(player, 0);
            if (next != null) {
                SkillEvents.mark(next, SkillRules.curseBonus(rank), now, ticks, player);
                s.target = next;
                set(player, 1);
            }
        }
        send(player, false);
    }

    // ---------------------------------------------------------------- 引き潮で引いた (SkillActions.ebbTide)
    public static void onEbb(ServerPlayer player, int pulled) {
        if (is(player, Kind.EBB) && pulled > 0) {
            add(player, pulled);
            send(player, false);
        }
    }

    // 凍て潮で冷えた敵 (潮の型の「凍え」の的)
    public static void chill(LivingEntity e, int ticks) {
        CHILLED.put(e, e.level().getGameTime() + ticks);
    }

    // ---------------------------------------------------------------- 近接・傷・矢 (SkillEvents)
    // 近接の一撃に掛ける倍率 (孤りが満ちていれば使い切る)。技の斬撃も含む
    public static double meleeMultiplier(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null && s.kind == Kind.LONE && full(player)) {
            set(player, 0);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
            prop(player.serverLevel(), player, "gx_lone", ahead(player, 1.6), 1.0f, 12);
            send(player, false);
            return LONE_STRIKE;
        }
        return 1;
    }

    // ふつうの近接 (技でない) が当たった
    public static void onMelee(ServerPlayer player, LivingEntity target) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.kind == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (s.kind == Kind.CLEAVER) {
            add(player, 1);
        } else if (s.kind == Kind.TEMPER && SkillManager.get(player).temperUntil >= now) {
            add(player, 1);
            if (full(player)) {
                SkillData d = SkillManager.get(player);
                d.temperUntil = now + SkillRules.TEMPER_TICKS;
                s.boostUntil = now + TEMPER_BOOST;
                set(player, 0);
                burst(player.serverLevel(), player, ParticleTypes.FLAME, 30);
                prop(player.serverLevel(), player, "gx_temper", ahead(player, 1.2), 1.0f, 16);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
            }
        }
        send(player, false);
    }

    // 生き物から傷を受けた (盾の重み)
    public static void onHurt(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null || s.kind != Kind.SHIELD) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - s.lastHurtGain >= 10) {
            s.lastHurtGain = now;
            add(player, SHIELD_HURT);
            send(player, false);
        }
    }

    // ふつうの矢 (技の矢でない) が敵に当たった
    public static void onArrow(ServerPlayer player) {
        if (is(player, Kind.RAIN)) {
            add(player, 1);
            send(player, false);
        }
    }

    // ---------------------------------------------------------------- 毎 tick (SkillEvents.onPlayerTick)
    public static void tick(ServerPlayer player) {
        State s = state(player);
        long now = player.level().getGameTime();
        if (!s.forced && player.tickCount % 20 == 3) {
            Kind k = pick(player);
            if (k != s.kind) {
                if (s.kind == Kind.TAIL) {
                    applyTail(player, 0);
                }
                s.kind = k;
                s.value = 0;
                s.target = null;
                s.lastSkill = null;
                if (k != null && !(player instanceof FakePlayer)) {
                    Notice.hint(player, "message.skyabyss.gauge.unlocked", Component.translatable(k.nameKey()), Component.translatable(k.nameKey() + ".desc"));
                }
            }
        }
        if (s.kind == null) {
            send(player, false);
            return;
        }
        switch (s.kind) {
            case TAIL -> {
                if (s.value > 0 && now - s.lastGain > TAIL_KEEP && now % 10 == 0) {
                    add(player, -1);
                }
            }
            case EMBER -> {
                SkillData d = SkillManager.get(player);
                if (s.value > 0 && now - d.lastCombat > SkillRules.CALM_AFTER_TICKS && now % 20 == 0) {
                    add(player, -10);
                }
            }
            case DANCE -> {
                if (s.value > 0 && now - s.lastCast > DANCE_KEEP) {
                    set(player, 0);
                    s.lastSkill = null;
                }
            }
            case LONE -> {
                boolean alone = SkillEffects.allies(player, LONE_RANGE).isEmpty();
                add(player, alone ? 1 : -2);
            }
            case PIERCE -> {
                Vec3 pos = player.position();
                boolean still = s.lastPos != null && pos.distanceToSqr(s.lastPos) < 0.0004;
                s.lastPos = pos;
                if (still) {
                    if (++s.stillTicks >= 20) {
                        s.stillTicks = 0;
                        add(player, 1);
                    }
                } else {
                    s.stillTicks = 0;
                    if (s.value > 0 && now % 10 == 0) {
                        add(player, -1);
                    }
                }
            }
            case TRAP -> {
                List<SkillEffects.Zone> traps = SkillEffects.zonesListOf(SkillEffects.ZoneKind.TRAP, player.getUUID());
                set(player, traps.size());
                if (now % 5 == 0 && traps.size() >= 2) {
                    threads(player, traps, now);
                }
            }
            case THUNDER, SHADOW, CURSE -> {
                // 印を付けた敵が倒れた・離れた
                if (s.target != null && (!s.target.isAlive() || s.target.level() != player.level())) {
                    s.target = null;
                    set(player, 0);
                }
                if (s.kind == Kind.THUNDER && s.target != null) {
                    long[] c = CHARGES.get(s.target);
                    if (c == null || c[1] < now) {
                        set(player, 0);
                    }
                }
            }
            case TIDE -> {
                int n = 0;
                for (Map.Entry<LivingEntity, long[]> e : FROZEN.entrySet()) {
                    if (e.getValue()[0] >= now && e.getValue()[2] == player.getId() && e.getKey().isAlive()) {
                        n++;
                    }
                }
                set(player, n);
            }
            default -> {
            }
        }
        send(player, false);
    }

    // ---------------------------------------------------------------- 放つ技
    // 岩砕き (盾の守り手): 盾殴りが、まわりの敵すべてを叩いて小さな敵を縛る
    private static void rockSmash(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float amount = SkillActions.attackDamage(player) * (float) SMASH_MULT;
        busy = true;
        try {
            for (LivingEntity e : SkillActions.foesAround(player, SMASH_RADIUS, 1.0, 2.0)) {
                SkillActions.hit(player, e, amount, Skill.SHIELD_BASH);
                if (!SkillActions.isLarge(e)) {
                    SkillEffects.bind(e, SMASH_BIND);
                }
            }
        } finally {
            busy = false;
        }
        for (double r = 1; r <= SMASH_RADIUS; r += 1) {
            SkillActions.ring(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, player.position(), r, (int) (r * 8), 0.1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.6, 0.1, 0.6, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
        prop(level, player, "gx_smash", player.position(), (float) (SMASH_RADIUS * 0.6), 16);
    }

    // 炉開き (熾火の槌): 前に火の柱が三本立つ
    private static void forgeOpen(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = SkillActions.flatLook(player);
        float amount = SkillActions.attackDamage(player) * (float) FORGE_OPEN_MULT;
        List<LivingEntity> struck = new ArrayList<>();
        busy = true;
        try {
            for (int i = 1; i <= 3; i++) {
                Vec3 c = player.position().add(dir.scale(i * 2.0));
                for (int y = 0; y < 5; y++) {
                    level.sendParticles(ParticleTypes.FLAME, c.x, c.y + y * 0.6, c.z, 6, 0.3, 0.2, 0.3, 0.02);
                }
                level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 3, 0.3, 0.1, 0.3, 0);
                jp.revati.skyabyss.combat.prop.AttackProps.riseLater(level, "gx_forge", player, c, level.random.nextFloat() * 360f, 1.0f, 16, (i - 1) * 2);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(1.8, 2.5, 1.8),
                        e -> SkillActions.isFoe(player, e))) {
                    if (!struck.contains(e)) {
                        struck.add(e);
                        SkillActions.hit(player, e, amount, Skill.EMBER_HAMMER);
                        e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
                    }
                }
            }
        } finally {
            busy = false;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    // 灰の層を燃やす (火花散らし): まわりの灰かぶりの敵へ、層の数だけの傷
    private static void ashBurn(ServerPlayer player, double layers) {
        float amount = SkillActions.attackDamage(player) * (float) (ASH_PER_LAYER * layers);
        busy = true;
        try {
            for (LivingEntity e : SkillActions.foesAround(player, SkillRules.SPARK_RADIUS + 1, 1.0, 2.0)) {
                if (SkillEffects.ashed(e)) {
                    SkillActions.hit(player, e, amount, Skill.SPARK_BURST);
                    burst(player.serverLevel(), e, ParticleTypes.FLAME, 14);
                    prop(player.serverLevel(), player, "gx_ash", e.position(), Math.max(0.7f, e.getBbWidth() * 0.9f), 12);
                }
            }
        } finally {
            busy = false;
        }
    }

    // 帯電した敵から、まわりの敵へ雷が跳ねる
    private static void chain(ServerPlayer player, LivingEntity from) {
        ServerLevel level = player.serverLevel();
        int rank = Math.max(1, SkillManager.get(player).rank(Skill.THUNDER_BOLT));
        float amount = (float) (JobActions.spell(player, SkillRules.boltMultiplier(rank)) * THUNDER_CHAIN_SCALE);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(THUNDER_CHAIN_RANGE),
                e -> e != from && SkillActions.isFoe(player, e));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
        Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
        prop(level, player, "gx_thunder", from.position(), 0.9f, 12);
        busy = true;
        try {
            for (int i = 0; i < Math.min(THUNDER_CHAIN_COUNT, near.size()); i++) {
                LivingEntity e = near.get(i);
                Vec3 b = e.position().add(0, e.getBbHeight() * 0.6, 0);
                for (int k = 0; k <= 8; k++) {
                    Vec3 p = a.lerp(b, k / 8.0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
                prop(level, player, "gx_thunder", e.position(), 0.7f, 10);
                SkillActions.hit(player, e, amount, Skill.THUNDER_BOLT);
            }
        } finally {
            busy = false;
        }
        level.playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    // 凍え (潮): 冷えた敵に技が当たると凍る。大きな相手は鈍りが強まるだけ
    private static void freeze(ServerPlayer player, LivingEntity target, long now) {
        long[] f = FROZEN.get(target);
        if (f != null && f[1] > now) {
            return;
        }
        FROZEN.put(target, new long[] {now + FREEZE_TICKS, now + FREEZE_AGAIN, player.getId()});
        if (SkillActions.isLarge(target)) {
            SkillEffects.slow(target, 0.6, FREEZE_TICKS);
        } else {
            SkillEffects.bind(target, FREEZE_TICKS);
        }
        burst(player.serverLevel(), target, ParticleTypes.SNOWFLAKE, 20);
        prop(player.serverLevel(), player, "gx_freeze", target.position(), Math.max(0.6f, target.getBbWidth() * 0.8f), (int) Math.min(60, FREEZE_TICKS));
        player.serverLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    public static boolean frozen(LivingEntity e) {
        long[] f = FROZEN.get(e);
        return f != null && f[0] >= e.level().getGameTime();
    }

    // 罠の糸: 罠どうしを結ぶ線を越えた敵を鈍らせる
    private static void threads(ServerPlayer player, List<SkillEffects.Zone> traps, long now) {
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < traps.size(); i++) {
            for (int j = i + 1; j < traps.size(); j++) {
                Vec3 a = traps.get(i).center.add(0, 0.4, 0);
                Vec3 b = traps.get(j).center.add(0, 0.4, 0);
                if (a.distanceTo(b) > 16) {
                    continue;
                }
                if (now % 20 == 0) {
                    for (int k = 0; k <= 10; k++) {
                        Vec3 p = a.lerp(b, k / 10.0);
                        level.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                    }
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(a, b).inflate(1.0),
                        e -> SkillActions.isFoe(player, e))) {
                    if (segmentDistance(e.position().add(0, 0.4, 0), a, b) <= 0.8 + e.getBbWidth() / 2) {
                        SkillEffects.slow(e, TRAP_THREAD_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), 40);
                    }
                }
            }
        }
    }

    // 足止めの矢が当たった敵のまわりの罠を、みなはぜさせる
    private static void trapBurst(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        float amount = SkillActions.attackDamage(player) * (float) TRAP_BURST_MULT;
        busy = true;
        try {
            for (SkillEffects.Zone z : SkillEffects.zonesListOf(SkillEffects.ZoneKind.TRAP, player.getUUID())) {
                if (z.center.distanceTo(target.position()) > TRAP_BURST_RANGE) {
                    continue;
                }
                z.spent = true;
                level.sendParticles(ParticleTypes.EXPLOSION, z.center.x, z.center.y + 0.3, z.center.z, 1, 0, 0, 0, 0);
                prop(level, player, "gx_trap", z.center, 1.2f, 12);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(z.center, z.center).inflate(2.5, 1.5, 2.5),
                        e -> SkillActions.isFoe(player, e))) {
                    SkillActions.hit(player, e, amount, Skill.BONE_TRAP);
                }
                level.playSound(null, z.center.x, z.center.y, z.center.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
        } finally {
            busy = false;
        }
    }

    // 空の矢筒: 天の驟雨の所へ、溜めた矢が 2 秒かけて降る
    private static void rainDown(ServerPlayer player, Vec3 center, int arrows) {
        ServerLevel level = player.serverLevel();
        float amount = SkillActions.attackDamage(player) * (float) RAIN_ARROW;
        double r = SkillRules.RAIN_RADIUS;
        for (int i = 0; i < arrows; i++) {
            SkillEffects.later(level, 4 + i * 2, () -> {
                if (!player.isAlive() || player.level() != level) {
                    return;
                }
                List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(r, 3, r),
                        e -> SkillActions.isFoe(player, e));
                Vec3 at = foes.isEmpty() ? center.add((level.random.nextDouble() - 0.5) * r * 2, 0, (level.random.nextDouble() - 0.5) * r * 2)
                        : foes.get(level.random.nextInt(foes.size())).position();
                for (int k = 0; k < 6; k++) {
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 6 - k, at.z, 1, 0.02, 0.1, 0.02, 0);
                }
                jp.revati.skyabyss.combat.prop.AttackProps.fall(level, SkillProps.kind(SkillTree.BONE, "rise"), player, at, 2, 2, 5f, 0.6f, 8);
                if (!foes.isEmpty()) {
                    busy = true;
                    try {
                        for (LivingEntity e : foes) {
                            if (e.position().distanceToSqr(at) < 0.25) {
                                SkillActions.hit(player, e, amount, Skill.SKY_RAIN);
                                break;
                            }
                        }
                    } finally {
                        busy = false;
                    }
                }
            });
        }
    }

    // 満ち潮 (引き潮の型): まわりの敵を押し流して叩く
    private static void tideWave(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        float amount = SkillActions.attackDamage(player) * (float) TIDE_WAVE_MULT;
        busy = true;
        try {
            for (LivingEntity e : SkillActions.foesAround(player, TIDE_WAVE_RADIUS, 1.0, 2.0)) {
                SkillActions.hit(player, e, amount, Skill.EBB_TIDE);
                if (!SkillActions.isLarge(e)) {
                    Vec3 away = new Vec3(e.getX() - player.getX(), 0, e.getZ() - player.getZ());
                    away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
                    e.setDeltaMovement(away.scale(1.2).add(0, 0.3, 0));
                    e.hurtMarked = true;
                }
            }
        } finally {
            busy = false;
        }
        for (double r = 1.5; r <= TIDE_WAVE_RADIUS; r += 1.5) {
            SkillActions.ring(level, ParticleTypes.SPLASH, player.position(), r, (int) (r * 10), 0.2);
            SkillActions.ring(level, ParticleTypes.BUBBLE_POP, player.position(), r, (int) (r * 6), 0.5);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2f, 0.6f);
        // 満ち潮の形: 四方へ外向きに砕ける大波
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.toRadians(player.getYRot());
            Vec3 d = new Vec3(-Math.sin(a), 0, Math.cos(a));
            jp.revati.skyabyss.combat.prop.AttackProps.riseLater(level, "gx_tide", player, player.position().add(d.scale(2.0)), (float) Math.toDegrees(Math.atan2(-d.x, d.z)), 0.9f, 14, 0);
        }
    }

    // 勢い (鯨尾の振り回し): 攻撃の速さと足が、勢いの数だけ上がる
    private static final ResourceLocation TAIL_ASPEED_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gauge/tail_attack_speed");
    private static final ResourceLocation TAIL_MOVE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "gauge/tail_move");

    private static void applyTail(ServerPlayer player, double stacks) {
        setMod(player.getAttribute(Attributes.ATTACK_SPEED), TAIL_ASPEED_ID, TAIL_ASPEED * stacks);
        setMod(player.getAttribute(Attributes.MOVEMENT_SPEED), TAIL_MOVE_ID, TAIL_MOVE * stacks);
    }

    private static void setMod(AttributeInstance a, ResourceLocation id, double amount) {
        if (a == null) {
            return;
        }
        AttributeModifier old = a.getModifier(id);
        if (old != null && Math.abs(old.amount() - amount) < 1e-9) {
            return;
        }
        a.removeModifier(id);
        if (amount > 0) {
            a.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ---------------------------------------------------------------- 放つときの形 (2026-11-12。prop_gx_<型>、tools/gen/mobs/attack_props/build_skill_kit2.py)
    private static void prop(ServerLevel level, ServerPlayer player, String kind, Vec3 at, float size, int linger) {
        jp.revati.skyabyss.combat.prop.AttackProps.here(level, kind, player, at, size, linger);
    }

    // 人の前 (水平) へ d
    private static Vec3 ahead(ServerPlayer player, double d) {
        return player.position().add(SkillActions.flatLook(player).scale(d));
    }

    // ---------------------------------------------------------------- 小物
    private static void burst(ServerLevel level, LivingEntity e, net.minecraft.core.particles.ParticleOptions p, int n) {
        level.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), n, 0.35, 0.4, 0.35, 0.03);
    }

    private static double segmentDistance(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // 持ち主へ送る (型か整数の値が変わったときだけ)
    static void send(ServerPlayer player, boolean force) {
        State s = state(player);
        int k = s.kind == null ? -1 : s.kind.ordinal();
        int v = (int) Math.floor(s.value + 1e-6);
        if (!force && k == s.sentKind && v == s.sentValue) {
            return;
        }
        s.sentKind = k;
        s.sentValue = v;
        if (!(player instanceof FakePlayer) && player.connection != null) {
            PacketDistributor.sendToPlayer(player, new GaugePayload(k, v));
        }
    }

    // ログアウト・死・職替え: 空に戻す
    public static void reset(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s != null) {
            if (s.kind == Kind.TAIL) {
                applyTail(player, 0);
            }
            s.value = 0;
            s.target = null;
            s.lastSkill = null;
            s.boostUntil = 0;
            send(player, true);
        }
    }

    public static void forget(UUID id) {
        STATES.remove(id);
    }

    public static void clear() {
        STATES.clear();
        CHARGES.clear();
        CHILLED.clear();
        FROZEN.clear();
    }
}
