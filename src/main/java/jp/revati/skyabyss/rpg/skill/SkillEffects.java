package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 技が残す「時間のある効き目」をまとめて持つ (サーバーだけ。保存しない)。
//   - 期限つきの修飾子 (鈍足・灰かぶり・見切り・連れ風・逃げ足)
//   - 地面の領域 (熾火・渦・骨の罠・光の標)
//   - 岩呼びの狙い、出血、吸収の期限、少しあとに起こす動き (二度薙ぎ・風裂きの二連など)
// 毎 tick の更新は SkillActions.tick から。ログアウト・サーバー停止で clear
public final class SkillEffects {
    private SkillEffects() {
    }

    // ================================================================ 力 (傷・量に掛ける倍率)
    // 近くの仲間 (自分以外のプレイヤー)。観戦者・死んだ人は数えない
    public static List<ServerPlayer> allies(ServerPlayer player, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel level)) {
            return out;
        }
        for (ServerPlayer p : level.players()) {
            if (p != player && p.isAlive() && !p.isSpectator() && p.distanceToSqr(player) <= radius * radius) {
                out.add(p);
            }
        }
        return out;
    }

    // 共鳴の声 (要): 近くの仲間 1 人につき +4% (最大 5 人)
    public static double chorus(ServerPlayer player) {
        if (!SkillManager.get(player).knows(Skill.CHORUS_VOICE)) {
            return 0;
        }
        int n = Math.min(SkillRules.CHORUS_MAX_ALLIES, allies(player, SkillRules.ALLY_RANGE).size());
        return SkillRules.CHORUS_PER_ALLY * n;
    }

    // 鍛え人の目 (要): 手の武器の強化段階 1 つにつき +1.5%
    public static double smith(ServerPlayer player) {
        if (!SkillManager.get(player).knows(Skill.SMITH_EYE)) {
            return 0;
        }
        return SkillRules.SMITH_PER_LEVEL * jp.revati.skyabyss.item.ModGear.enhance(player.getMainHandItem());
    }

    // 技の傷に掛ける倍率: 共鳴の声・鍛え人の目・巨神斬りの型のおまけ・木の印
    public static double damagePower(ServerPlayer player, Skill skill) {
        double v = 1 + chorus(player) + smith(player) + SkillMarks.power(player, skill.tree);
        if (skill == Skill.TITAN_CLEAVE && WeaponType.has(player, WeaponType.HEAVY)) {
            v += SkillRules.CLEAVE_HEAVY_BONUS;
        }
        return v;
    }

    // 支えの量 (吸収・マナの回復) に掛ける倍率: 共鳴の声・木の印・精神 30 の刻み目 (仲間に向ける技 +10%)
    public static double supportPower(ServerPlayer player, Skill skill) {
        double v = 1 + chorus(player) + SkillMarks.power(player, skill.tree);
        if (Breakpoints.reached(player, jp.revati.skyabyss.rpg.GrowthRules.Stat.SPIRIT, 30)) {
            v += Breakpoints.SPIRIT_30_ALLY;
        }
        return v;
    }

    // ================================================================ 期限つきの修飾子
    private record Timed(Holder<Attribute> attribute, ResourceLocation id, long until, double amount) {
    }

    private static final Map<LivingEntity, Map<String, Timed>> TIMED = new WeakHashMap<>();

    private static ResourceLocation timedId(String key) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skill/t_" + key);
    }

    // key の修飾子を、期限つきで付ける。同じ key が効いていて今のほうが強ければ、強いほうを残して期限だけ延ばす
    public static void timedMod(LivingEntity entity, Holder<Attribute> attribute, String key, double amount, AttributeModifier.Operation op, int ticks) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || ticks <= 0) {
            return;
        }
        long until = entity.level().getGameTime() + ticks;
        Map<String, Timed> map = TIMED.computeIfAbsent(entity, e -> new HashMap<>());
        Timed old = map.get(key);
        ResourceLocation id = timedId(key);
        if (old != null && old.until > entity.level().getGameTime() && Math.abs(old.amount) >= Math.abs(amount) && instance.getModifier(id) != null) {
            map.put(key, new Timed(attribute, id, Math.max(old.until, until), old.amount));
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, op));
        map.put(key, new Timed(attribute, id, until, amount));
    }

    public static boolean hasTimed(LivingEntity entity, String key) {
        Map<String, Timed> map = TIMED.get(entity);
        Timed t = map == null ? null : map.get(key);
        return t != null && t.until > entity.level().getGameTime();
    }

    private static void tickTimed(long now) {
        for (Iterator<Map.Entry<LivingEntity, Map<String, Timed>>> it = TIMED.entrySet().iterator(); it.hasNext();) {
            Map.Entry<LivingEntity, Map<String, Timed>> e = it.next();
            LivingEntity entity = e.getKey();
            boolean gone = entity.isRemoved();
            e.getValue().values().removeIf(t -> {
                if (gone || t.until <= now) {
                    AttributeInstance a = entity.getAttribute(t.attribute);
                    if (a != null) {
                        a.removeModifier(t.id);
                    }
                    return true;
                }
                return false;
            });
            if (e.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    // 敵の足を鈍らせる (割合 0.2 = 20% 遅く)。大きな相手は呼ぶ側で割合を半分にする
    public static void slow(LivingEntity foe, double fraction, int ticks) {
        timedMod(foe, Attributes.MOVEMENT_SPEED, "slow", -Math.min(fraction, 0.95), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
        if (foe.getAttribute(Attributes.FLYING_SPEED) != null) {
            timedMod(foe, Attributes.FLYING_SPEED, "slow_fly", -Math.min(fraction, 0.95), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
        }
    }

    // 縛る (ほぼ動けない)
    public static void bind(LivingEntity foe, int ticks) {
        slow(foe, 0.95, ticks);
    }

    // ================================================================ 灰かぶり (灰散らし)
    private static final Map<LivingEntity, Long> ASHED = new WeakHashMap<>();

    public static void ashFall(LivingEntity foe, int ticks) {
        timedMod(foe, Attributes.ATTACK_DAMAGE, "ash", -SkillRules.ASH_ATTACK, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
        ASHED.put(foe, foe.level().getGameTime() + ticks);
    }

    public static boolean ashed(LivingEntity foe) {
        Long until = ASHED.get(foe);
        return until != null && until > foe.level().getGameTime();
    }

    // ================================================================ 岩呼び (狙いを引く)
    private record Taunt(UUID owner, long until) {
    }

    private static final Map<Mob, Taunt> TAUNTS = new WeakHashMap<>();

    public static void taunt(Mob mob, ServerPlayer owner, int ticks) {
        TAUNTS.put(mob, new Taunt(owner.getUUID(), owner.level().getGameTime() + ticks));
        pull(mob, owner);
    }

    private static void pull(Mob mob, ServerPlayer owner) {
        mob.setTarget(owner);
        try {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, owner);
        } catch (RuntimeException ignored) {
            // 頭脳のない種類・その記憶を持たない種類は setTarget だけで足りる
        }
    }

    // その人を狙わされている敵が近くにいるか
    public static boolean taunting(ServerPlayer owner, double within) {
        long now = owner.level().getGameTime();
        for (Map.Entry<Mob, Taunt> e : TAUNTS.entrySet()) {
            Mob m = e.getKey();
            if (e.getValue().owner.equals(owner.getUUID()) && e.getValue().until > now && m.isAlive() && m.distanceToSqr(owner) <= within * within) {
                return true;
            }
        }
        return false;
    }

    // この敵は、その人の岩呼びで狙いを引かれているか
    public static boolean tauntedBy(LivingEntity foe, ServerPlayer owner) {
        if (!(foe instanceof Mob mob)) {
            return false;
        }
        Taunt t = TAUNTS.get(mob);
        return t != null && t.owner.equals(owner.getUUID()) && t.until > owner.level().getGameTime();
    }

    // ================================================================ 吸収 (守りの唄)
    private static final class Absorb {
        final UUID who;
        final long until;
        final float amount;
        final boolean cycling;

        Absorb(UUID who, long until, float amount, boolean cycling) {
            this.who = who;
            this.until = until;
            this.amount = amount;
            this.cycling = cycling;
        }
    }

    private static final List<Absorb> ABSORBS = new ArrayList<>();

    // 吸収を付ける。重ねがけで増えない (いまの吸収が大きければそのまま)。黄ハートは 2❤ (4 点) まで
    public static void absorb(ServerPlayer target, float amount, int ticks, boolean cycling) {
        amount = Math.min(SkillRules.ABSORB_CAP, amount);
        if (target.getAbsorptionAmount() < amount) {
            target.setAbsorptionAmount(amount);
        }
        ABSORBS.removeIf(a -> a.who.equals(target.getUUID()));
        ABSORBS.add(new Absorb(target.getUUID(), target.level().getGameTime() + ticks, amount, cycling));
    }

    private static void tickAbsorbs(MinecraftServer server, long now) {
        for (Iterator<Absorb> it = ABSORBS.iterator(); it.hasNext();) {
            Absorb a = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(a.who);
            if (p == null) {
                it.remove();
                continue;
            }
            if (now >= a.until) {
                // 切れたときに残っていれば消す (黄ハートを残さない)。金のリンゴなど、ほかの吸収の効果があるときは触らない
                if (p.getAbsorptionAmount() <= a.amount && !p.hasEffect(MobEffects.ABSORPTION)) {
                    p.setAbsorptionAmount(0);
                }
                it.remove();
                continue;
            }
            if (a.cycling && p.getAbsorptionAmount() <= 0f) {
                // 巡る歌: 吸収が割れたら、近くの 1 人へ半分
                it.remove();
                ServerPlayer next = null;
                for (ServerPlayer q : allies(p, SkillRules.ALLY_RANGE)) {
                    if (next == null || q.distanceToSqr(p) < next.distanceToSqr(p)) {
                        next = q;
                    }
                }
                if (next != null) {
                    float half = a.amount / 2f;
                    if (next.getAbsorptionAmount() < half) {
                        next.setAbsorptionAmount(half);
                    }
                    absorb(next, half, (int) Math.max(20, a.until - now), false);
                    if (next.level() instanceof ServerLevel l) {
                        l.sendParticles(ParticleTypes.NOTE, next.getX(), next.getY() + 1.8, next.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
                    }
                }
            }
        }
    }

    // ================================================================ 出血 (風裂きの刻印)
    private static final class Bleed {
        final UUID owner;
        final LivingEntity target;
        final float perTick;
        int left;

        Bleed(UUID owner, LivingEntity target, float perTick, int seconds) {
            this.owner = owner;
            this.target = target;
            this.perTick = perTick;
            this.left = seconds;
        }
    }

    private static final List<Bleed> BLEEDS = new ArrayList<>();
    private static int bleedClock;

    public static void bleed(ServerPlayer owner, LivingEntity target, float perSecond, int seconds) {
        BLEEDS.add(new Bleed(owner.getUUID(), target, perSecond, seconds));
    }

    private static void tickBleeds(MinecraftServer server) {
        if (++bleedClock % SkillRules.SECOND != 0) {
            return;
        }
        for (Iterator<Bleed> it = BLEEDS.iterator(); it.hasNext();) {
            Bleed b = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
            if (owner == null || !b.target.isAlive() || b.left <= 0 || owner.level() != b.target.level()) {
                it.remove();
                continue;
            }
            b.left--;
            SkillActions.hitPlain(owner, b.target, b.perTick, Skill.WIND_REND);
            if (b.target.level() instanceof ServerLevel l) {
                l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, b.target.getX(), b.target.getY() + b.target.getBbHeight() * 0.6, b.target.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    // ================================================================ 少しあとの動き
    private record Later(long at, Runnable run) {
    }

    private static final List<Later> LATER = new ArrayList<>();
    private static final List<Later> LATER_ADDED = new ArrayList<>();

    public static void later(ServerLevel level, int delay, Runnable run) {
        LATER_ADDED.add(new Later(level.getGameTime() + delay, run));
    }

    private static void tickLater(long now) {
        LATER.addAll(LATER_ADDED);
        LATER_ADDED.clear();
        for (Iterator<Later> it = LATER.iterator(); it.hasNext();) {
            Later l = it.next();
            if (now >= l.at) {
                it.remove();
                try {
                    l.run.run();
                } catch (RuntimeException e) {
                    SkyAbyss.LOGGER.warn("skill delayed action failed", e);
                }
            }
        }
    }

    // ================================================================ 地面の領域
    public enum ZoneKind {
        EMBER, WHIRL, TRAP, SIGNAL
    }

    public static final class Zone {
        public final ZoneKind kind;
        public final ServerLevel level;
        public final Vec3 center;
        public final double radius;
        public final UUID owner;
        public final int rank;
        public final long until;
        // 熾火は槌の傷の倍率ではなく、燃やすだけ。罠は踏まれたら 1 回で消える
        public boolean spent;

        Zone(ZoneKind kind, ServerLevel level, Vec3 center, double radius, UUID owner, int rank, long until) {
            this.kind = kind;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.owner = owner;
            this.rank = rank;
            this.until = until;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    public static Zone zone(ZoneKind kind, ServerPlayer owner, Vec3 center, double radius, int ticks, int rank) {
        ServerLevel level = owner.serverLevel();
        Zone z = new Zone(kind, level, center, radius, owner.getUUID(), rank, level.getGameTime() + ticks);
        if (kind == ZoneKind.TRAP) {
            // 罠は最大 2 つ。古いものから消える
            List<Zone> mine = new ArrayList<>();
            for (Zone o : ZONES) {
                if (o.kind == ZoneKind.TRAP && o.owner.equals(owner.getUUID())) {
                    mine.add(o);
                }
            }
            while (mine.size() >= SkillRules.TRAP_MAX) {
                ZONES.remove(mine.remove(0));
            }
        }
        ZONES.add(z);
        return z;
    }

    public static int zonesOf(ZoneKind kind, UUID owner) {
        int n = 0;
        for (Zone z : ZONES) {
            if (z.kind == kind && z.owner.equals(owner) && !z.spent) {
                n++;
            }
        }
        return n;
    }

    private static List<LivingEntity> foesIn(ServerPlayer owner, Zone z) {
        AABB box = new AABB(z.center, z.center).inflate(z.radius, 1.0, z.radius).expandTowards(0, 2.0, 0);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box, f -> SkillActions.isFoe(owner, f))) {
            double dx = e.getX() - z.center.x;
            double dz = e.getZ() - z.center.z;
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 <= z.radius) {
                out.add(e);
            }
        }
        return out;
    }

    private static void tickZones(MinecraftServer server, long now) {
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext();) {
            Zone z = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(z.owner);
            if (z.spent || now >= z.until || owner == null || owner.level() != z.level) {
                it.remove();
                continue;
            }
            long age = z.until - now;
            switch (z.kind) {
                case EMBER -> {
                    if (now % 4 == 0) {
                        for (int i = 0; i < 5; i++) {
                            double a = z.level.random.nextDouble() * Math.PI * 2;
                            double d = Math.sqrt(z.level.random.nextDouble()) * z.radius;
                            z.level.sendParticles(ParticleTypes.FLAME, z.center.x + Math.cos(a) * d, z.center.y + 0.1, z.center.z + Math.sin(a) * d, 1, 0.05, 0.1, 0.05, 0.01);
                        }
                    }
                    if (now % 10 == 0) {
                        for (LivingEntity e : foesIn(owner, z)) {
                            if (e.getRemainingFireTicks() < 50) {
                                e.setRemainingFireTicks(60);
                            }
                        }
                    }
                }
                case WHIRL -> {
                    if (now % 3 == 0) {
                        for (int i = 0; i < 6; i++) {
                            double a = (now * 0.5 + i * Math.PI / 3);
                            double d = z.radius * (0.4 + 0.5 * ((now + i) % 5) / 5.0);
                            z.level.sendParticles(Skills.SKY_MOTE.get(), z.center.x + Math.cos(a) * d, z.center.y + 0.3, z.center.z + Math.sin(a) * d, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                    }
                    if (now % 5 == 0) {
                        for (LivingEntity e : foesIn(owner, z)) {
                            slow(e, SkillRules.WHIRL_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), 12);
                        }
                    }
                }
                case TRAP -> {
                    if (now % 8 == 0) {
                        z.level.sendParticles(Skills.RUNE_SPARK.get(), z.center.x, z.center.y + 0.15, z.center.z, 1, 0.25, 0.02, 0.25, 0.0);
                    }
                    if (now % 3 == 0) {
                        Zone near = new Zone(z.kind, z.level, z.center, SkillRules.TRAP_TRIGGER, z.owner, z.rank, z.until);
                        List<LivingEntity> foes = foesIn(owner, near);
                        if (!foes.isEmpty()) {
                            LivingEntity victim = foes.get(0);
                            z.spent = true;
                            SkillActions.springTrap(owner, victim, z.rank, z.center);
                        }
                    }
                }
                case SIGNAL -> {
                    if (now % 4 == 0) {
                        for (int k = 0; k < 14; k++) {
                            z.level.sendParticles(ParticleTypes.END_ROD, z.center.x, z.center.y + 0.3 + k * 0.45, z.center.z, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                        if (age < 60) {
                            // 消える前は、ゆっくり明滅
                            z.level.sendParticles(ParticleTypes.END_ROD, z.center.x, z.center.y + 7, z.center.z, 2, 0.3, 0.3, 0.3, 0.0);
                        }
                    }
                }
            }
        }
    }

    // ================================================================ 毎 tick (SkillActions.tick から)
    private static int clock;

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        tickLater(now);
        tickZones(server, now);
        tickBleeds(server);
        tickAbsorbs(server, now);
        if (++clock % 5 == 0) {
            tickTimed(now);
            for (Iterator<Map.Entry<Mob, Taunt>> it = TAUNTS.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Mob, Taunt> e = it.next();
                Mob m = e.getKey();
                ServerPlayer owner = server.getPlayerList().getPlayer(e.getValue().owner);
                if (m.isRemoved() || !m.isAlive() || owner == null || e.getValue().until <= now || owner.level() != m.level()) {
                    it.remove();
                    continue;
                }
                if (!(m.getTarget() == owner)) {
                    pull(m, owner);
                }
            }
        }
    }

    // 自己テスト用
    public static int pendingLater() {
        return LATER.size() + LATER_ADDED.size();
    }

    public static void clear() {
        for (Iterator<Map.Entry<LivingEntity, Map<String, Timed>>> it = TIMED.entrySet().iterator(); it.hasNext();) {
            Map.Entry<LivingEntity, Map<String, Timed>> e = it.next();
            for (Timed t : e.getValue().values()) {
                AttributeInstance a = e.getKey().getAttribute(t.attribute);
                if (a != null) {
                    a.removeModifier(t.id);
                }
            }
        }
        TIMED.clear();
        ASHED.clear();
        TAUNTS.clear();
        ABSORBS.clear();
        BLEEDS.clear();
        LATER.clear();
        LATER_ADDED.clear();
        ZONES.clear();
    }

    // 縛りの効き目の説明用 (自己テスト): 鈍足の修飾子が付いているか
    public static boolean slowed(LivingEntity e) {
        return hasTimed(e, "slow");
    }

    // 偽のプレイヤーは tick されないので、自己テストの手伝い
    public static boolean isFake(Player p) {
        return p instanceof FakePlayer;
    }

    // 使っていない import を避けるための小さな窓口 (効果の見た目)
    static void glow(LivingEntity e, int ticks, LivingEntity source) {
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false), source);
    }
}
