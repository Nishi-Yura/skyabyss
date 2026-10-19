package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 能動の技の中身 (サーバーだけで動く)。perform は、できなければ理由の翻訳キーを返す (マナもクールダウンも使わない)。
// 傷はバニラの攻撃に上乗せする形で入れる: 当てる前後で相手の無敵時間を 0 にし、ふつうの攻撃を食わないようにする。
// 時間のある効き目 (熾火・渦・罠・吸収・出血) は SkillEffects が持つ
public final class SkillActions {
    // いま技の傷を入れている最中か (常時の技が「技の傷」を見分けるため)
    private static final ThreadLocal<Skill> DEALING = new ThreadLocal<>();
    // 技の傷のうち、返し刃・熾・炉の記憶・焼き入れの炎を足さないもの (出血・炎の上乗せ・稲光)
    private static final ThreadLocal<Boolean> RIDERLESS = new ThreadLocal<>();
    // 雲穿ちの矢 (寿命を数え、尾を引かせる)
    public static final String PIERCE_TAG = "skyabyss_cloud_pierce";
    public static final String LIGHTNING_TAG = "skyabyss_cloud_pierce_lightning";
    // 伝説の書き換え「天落とし」(雷淵の弓): 矢の止まった所に 0.8 秒後、雷の柱
    public static final String SKY_FALL_TAG = "skyabyss_cloud_pierce_sky_fall";
    private static final List<TrackedArrow> ARROWS = new ArrayList<>();
    // 天の驟雨の、これから降る分
    private static final List<Rain> RAINS = new ArrayList<>();
    // 技が決める、このあとの待ちの倍率 (連ねは当たると 0.5)。SkillManager が読んで 1 に戻す
    static double cooldownScale = 1;

    private record TrackedArrow(AbstractArrow arrow, int[] age, int lifetime) {
    }

    private static final class Rain {
        final ServerLevel level;
        final Vec3 center;
        final UUID caster;
        final double damage;
        int ticks;
        int pulses;

        Rain(ServerLevel level, Vec3 center, UUID caster, double damage) {
            this.level = level;
            this.center = center;
            this.caster = caster;
            this.damage = damage;
        }
    }

    private SkillActions() {
    }

    public static Skill dealing() {
        return DEALING.get();
    }

    public static boolean riderless() {
        return Boolean.TRUE.equals(RIDERLESS.get());
    }

    public static String perform(ServerPlayer player, Skill skill, int rank) {
        // 変異 (A08): 極めた技に選んでいれば、形の違う中身で動く (力・待ち・マナはそのまま)
        Mutation mutation = SkillExtOps.active(player, skill);
        if (mutation != null) {
            return ExtActions.mutated(player, mutation, rank);
        }
        return switch (skill) {
            case TITAN_CLEAVE -> cleave(player, rank);
            case ROCK_CALL -> rockCall(player, rank);
            case WHALE_SWEEP -> sweep(player, rank);
            case LUNGE -> lunge(player, rank);
            case BACKSTEP -> backstep(player, rank);
            case WIND_REND -> rend(player, rank);
            case WARD_SONG -> wardSong(player, rank);
            case EBB_TIDE -> ebbTide(player, rank);
            case CYCLE_SONG -> cycleSong(player, rank);
            case TEMPER -> temper(player, rank);
            case EMBER_HAMMER -> hammer(player, rank);
            case ASH_SCATTER -> ashScatter(player, rank);
            case CLOUD_PIERCE -> pierce(player, rank);
            case BONE_TRAP -> boneTrap(player, rank);
            case SKY_RAIN -> rain(player, rank);
            case SIGNAL -> signal(player, rank);
            default -> ExtActions.perform(player, skill, rank);
        };
    }

    private static int rune(ServerPlayer player, Skill skill) {
        return SkillManager.runeOf(player, skill);
    }

    // ---------------------------------------------------------------- 相手の選び方
    // 技が当たる相手: 敵 (モンスター) か、プレイヤーを狙っている生き物。プレイヤー、飼われた動物、防具立て、味方は外す
    public static boolean isFoe(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity e) || e == player || !e.isAlive() || e instanceof Player || e instanceof ArmorStand
                || !e.isAttackable() || e.isInvulnerable() || e.isAlliedTo(player)) {
            return false;
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return false;
        }
        // 怒っていなければ襲ってこない生き物 (エンダーマン、ゾンビピグリン、ピグリンなど) は、プレイヤーに怒っているときだけ。
        // 範囲の技でネザーの群れを起こさないように
        if ((e instanceof NeutralMob || e instanceof AbstractPiglin) && e instanceof Mob neutral) {
            return angryAtPlayer(neutral);
        }
        if (e instanceof Enemy) {
            return true;
        }
        return e instanceof Mob mob && angryAtPlayer(mob);
    }

    private static boolean angryAtPlayer(Mob mob) {
        if (mob.getTarget() instanceof Player) {
            return true;
        }
        return mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player;
    }

    public static boolean isLarge(LivingEntity e) {
        return SkillRules.isLarge(e.getBbWidth(), e.getBbHeight(), e.getMaxHealth());
    }

    // 水平の向き (見ている向きから上下を除いたもの)
    static Vec3 flatLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
    }

    static List<LivingEntity> foesAround(ServerPlayer player, double radius, double down, double up) {
        AABB box = player.getBoundingBox().inflate(radius, 0, radius).expandTowards(0, up, 0).expandTowards(0, -down, 0);
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(player, e) && player.hasLineOfSight(e));
    }

    // 見通しを問わない、近くの敵
    static List<LivingEntity> foesAroundAny(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> isFoe(player, e));
    }

    // 天の驟雨の傷の種類 (data/skyabyss/damage_type/sky_rain.json)。ノックバックしない (敵を降る範囲の外へ押し出さない)
    public static final ResourceKey<DamageType> SKY_RAIN_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID, "sky_rain"));

    // 技の傷を入れる。共鳴の声・鍛え人の目・木の印の倍率を掛け、当たった敵には波紋の鈍足が付く
    public static boolean hit(ServerPlayer player, LivingEntity target, float amount, Skill skill) {
        float scaled = amount * (float) SkillEffects.damagePower(player, skill);
        boolean hurt = deal(player, target, scaled, skill, false);
        if (hurt) {
            int ripple = SkillManager.get(player).rank(Skill.RIPPLE);
            if (ripple > 0) {
                SkillEffects.slow(target, SkillRules.rippleSlow(ripple) * (isLarge(target) ? 0.5 : 1.0), SkillRules.RIPPLE_TICKS);
            }
        }
        return hurt;
    }

    // 倍率を掛けない傷 (出血・炎の上乗せ・稲光。計算は呼ぶ側が済ませている)。返し刃などの上乗せは付かない
    public static boolean hitPlain(ServerPlayer player, LivingEntity target, float amount, Skill skill) {
        return deal(player, target, amount, skill, true);
    }

    private static boolean deal(ServerPlayer player, LivingEntity target, float amount, Skill skill, boolean riderless) {
        Skill before = DEALING.get();
        Boolean beforeFlag = RIDERLESS.get();
        DEALING.set(skill);
        RIDERLESS.set(riderless);
        try {
            DamageSource source = skill == Skill.SKY_RAIN
                    ? new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SKY_RAIN_DAMAGE), player)
                    : player.damageSources().playerAttack(player);
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(source, amount);
            target.invulnerableTime = 0;
            if (hurt) {
                player.setLastHurtMob(target);
            }
            return hurt;
        } finally {
            if (before == null) {
                DEALING.remove();
            } else {
                DEALING.set(before);
            }
            if (beforeFlag == null) {
                RIDERLESS.remove();
            } else {
                RIDERLESS.set(beforeFlag);
            }
        }
    }

    static float attackDamage(Player player) {
        return (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    static void ring(ServerLevel level, ParticleOptions p, Vec3 center, double radius, int n, double lift) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y + lift, center.z + Math.sin(a) * radius, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    // 範囲の技の押し出し: 筋力 30 の刻み目で 1.25 倍
    static double push(ServerPlayer player, double base) {
        return Breakpoints.reached(player, GrowthRules.Stat.STRENGTH, Breakpoints.SECOND) ? base * Breakpoints.STRENGTH_30_PUSH : base;
    }

    // ---------------------------------------------------------------- 岩守り
    // 巨神斬り: 前方の扇へ大きく振り下ろす。大きな相手 (巨神の番兵長・心臓など) には さらに 1.5 倍。
    // 刻印: 広刃 (180 度・倍率 −10%)、深刃 (30 度・一番近い 1 体に ×1.4)、地走り (前方 8 ブロック・幅 1.5 の衝撃波)
    static String cleave(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.TITAN_CLEAVE);
        Vec3 dir = flatLook(player);
        Vec3 origin = player.position();
        double halfAngle = rune == 1 ? SkillRules.WIDE_BLADE_HALF_ANGLE : rune == 2 ? SkillRules.DEEP_BLADE_HALF_ANGLE : SkillRules.CLEAVE_HALF_ANGLE;
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> targets = new ArrayList<>();
        if (rune == 3) {
            Vec3 side = new Vec3(-dir.z, 0, dir.x);
            for (LivingEntity e : foesAround(player, SkillRules.GROUND_RUN_LENGTH + 2, 1.0, 2.0)) {
                Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
                double along = to.dot(dir);
                double lateral = Math.abs(to.dot(side));
                if (along > -0.5 && along - e.getBbWidth() / 2 <= SkillRules.GROUND_RUN_LENGTH
                        && lateral <= SkillRules.GROUND_RUN_WIDTH / 2 + e.getBbWidth() / 2) {
                    targets.add(e);
                }
            }
        } else {
            for (LivingEntity e : foesAround(player, SkillRules.CLEAVE_RANGE + 2, 1.0, 2.0)) {
                Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
                double dist = to.length() - e.getBbWidth() / 2;
                if (dist > SkillRules.CLEAVE_RANGE) {
                    continue;
                }
                if (to.length() < 1.0 || to.normalize().dot(dir) >= cos) {
                    targets.add(e);
                }
            }
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        float base = attackDamage(player) * (float) SkillRules.cleaveMultiplier(rank);
        if (rune == 1) {
            base *= (float) SkillRules.WIDE_BLADE_SCALE;
        }
        int limit = rune == 2 ? 1 : SkillRules.CLEAVE_MAX_TARGETS;
        boolean bigHit = false;
        for (int i = 0; i < Math.min(targets.size(), limit); i++) {
            LivingEntity e = targets.get(i);
            boolean large = isLarge(e);
            float amount = large ? base * (float) SkillRules.CLEAVE_LARGE_BONUS : base;
            if (rune == 2) {
                amount *= (float) SkillRules.DEEP_BLADE_SCALE;
            }
            if (hit(player, e, amount, Skill.TITAN_CLEAVE)) {
                // 当たった印 (大きな相手には多め)
                particle(level, Skills.RUNE_SPARK.get(), e.position().add(0, e.getBbHeight() * 0.6, 0), large ? 14 : 5, large ? 0.4 : 0.25, 0.06);
                bigHit |= large;
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (rune == 3) {
            // 地を走る衝撃波: 前方 8 ブロックへ、2 本の筋
            for (int i = 0; i <= 16; i++) {
                double t = 0.8 + i * 0.5;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = origin.add(dir.scale(t)).add(side.scale(s * 0.6));
                    level.sendParticles(Skills.RUNE_SPARK.get(), p.x, p.y + 0.2, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
            }
        } else {
            // 弧を描く光: 右上から左下へ、半径 2.6 の扇に沿って
            double span = Math.min(halfAngle, 90);
            for (int i = 0; i <= 24; i++) {
                double a = Math.toRadians(-span + i * span * 2 / 24.0);
                Vec3 p = origin.add(dir.scale(Math.cos(a) * 2.6)).add(side.scale(Math.sin(a) * 2.6)).add(0, 1.8 - i * 0.045, 0);
                level.sendParticles(Skills.RUNE_SPARK.get(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        if (rune == 4) {
            abyssCleaveEcho(player, level, origin, dir, base);
        }
        sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, bigHit ? 0.85f : 1.0f);
        return null;
    }

    // 伝説の書き換え「奈落断ち」(底なしの大剣。docs/design/31 の B04): 振った扇が 1.2 秒後に裂けて、同じ扇にもう一度 ×0.6 の遅れた刃
    private static void abyssCleaveEcho(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 dir, float base) {
        SkillEffects.later(level, jp.revati.skyabyss.abyss.Abyss.ECHO_DELAY, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            double cos = Math.cos(Math.toRadians(SkillRules.CLEAVE_HALF_ANGLE));
            AABB box = new AABB(origin, origin).inflate(SkillRules.CLEAVE_RANGE + 2, 1.5, SkillRules.CLEAVE_RANGE + 2).expandTowards(0, 2.0, 0);
            int n = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, f -> isFoe(player, f))) {
                Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
                double dist = to.length() - e.getBbWidth() / 2;
                if (dist > SkillRules.CLEAVE_RANGE || to.length() >= 1.0 && to.normalize().dot(dir) < cos) {
                    continue;
                }
                if (n++ >= SkillRules.CLEAVE_MAX_TARGETS) {
                    break;
                }
                float amount = base * (float) jp.revati.skyabyss.abyss.Abyss.ECHO_SCALE * (isLarge(e) ? (float) SkillRules.CLEAVE_LARGE_BONUS : 1f);
                if (hit(player, e, amount, Skill.TITAN_CLEAVE)) {
                    particle(level, jp.revati.skyabyss.abyss.AbyssFx.DUST, e.position().add(0, e.getBbHeight() * 0.6, 0), 10, 0.3, 0.02);
                    particle(level, ParticleTypes.REVERSE_PORTAL, e.position().add(0, e.getBbHeight() * 0.6, 0), 8, 0.3, 0.1);
                }
            }
            // 裂け目の筋 (扇の中心線)
            for (int i = 0; i <= 12; i++) {
                Vec3 p = origin.add(dir.scale(0.8 + i * 0.35)).add(0, 0.3, 0);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 2, 0.05, 0.1, 0.05, 0.04);
            }
            level.playSound(null, origin.x, origin.y, origin.z, net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        });
    }

    // 岩呼び: 周りの敵の狙いを、数秒のあいだ自分へ引く。引いた敵の攻撃を受けても押されにくい (SkillEvents)
    static String rockCall(ServerPlayer player, int rank) {
        int rune = rune(player, Skill.ROCK_CALL);
        double radius = rune == 1 ? SkillRules.LONG_VOICE_RADIUS : SkillRules.ROCK_CALL_RADIUS;
        int ticks = SkillRules.rockCallTicks(rank) + (WeaponType.has(player, WeaponType.GUARD) ? SkillRules.SECOND : 0);
        List<Mob> mobs = new ArrayList<>();
        for (LivingEntity e : foesAroundAny(player, radius)) {
            if (e instanceof Mob m && m.distanceTo(player) - m.getBbWidth() / 2 <= radius) {
                mobs.add(m);
            }
        }
        if (mobs.isEmpty()) {
            return "message.skyabyss.skill.no_enemy_near";
        }
        for (Mob m : mobs) {
            SkillEffects.taunt(m, player, ticks);
        }
        if (rune == 4) {
            // 鉤寄せ: 引いた敵を前の 1 か所へ寄せる (大きな相手は動かさない)
            Vec3 gather = player.position().add(flatLook(player).scale(SkillRules.HOOK_POINT_AHEAD));
            for (Mob m : mobs) {
                if (isLarge(m)) {
                    continue;
                }
                Vec3 to = new Vec3(gather.x - m.getX(), 0, gather.z - m.getZ());
                double len = Math.max(0.3, to.length());
                m.setDeltaMovement(to.scale(Mth.clamp(len * 0.28, 0.3, 1.3) / len).add(0, 0.15, 0));
                m.hurtMarked = true;
            }
            ring(player.serverLevel(), Skills.RUNE_SPARK.get(), gather, 1.2, 14, 0.5);
        }
        SkillData d = SkillManager.get(player);
        d.tauntUntil = player.level().getGameTime() + ticks;
        d.tauntRune = rune;
        ServerLevel level = player.serverLevel();
        ring(level, Skills.RUNE_SPARK.get(), player.position(), radius * 0.5, 20, 0.3);
        ring(level, Skills.RUNE_SPARK.get(), player.position(), radius, 32, 0.3);
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_SWEEP.get(), 1.0f, 0.55f);
        return null;
    }

    // 踏み込み: 見ている先 (8 ブロック以内、左右 25 度) の敵の手前まで一気に詰めて斬る。
    // 足場のない所・壁の向こうへは出ない (背中のアスレの近道にならないように)。
    // 刻印: 通り抜け (敵の背後へ着く)、連ね (当たると待ちが半分)、突き通し (通り道の全員に ×0.6)
    static String lunge(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.LUNGE);
        double range = SkillRules.LUNGE_RANGE + (WeaponType.has(player, WeaponType.LONG) ? 1 : 0);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : foesAround(player, range + 1, 2.0, 3.0)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > range + e.getBbWidth() / 2) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (angle > 25) {
                continue;
            }
            double score = angle * 0.2 + d;
            if (score < best) {
                best = score;
                target = e;
            }
        }
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        // 道をたどる: 0.25 ずつ進み、ぶつかるか足場が切れたら止まる
        Vec3 start = player.position();
        Vec3 flat = new Vec3(target.getX() - start.x, 0, target.getZ() - start.z);
        double total = flat.length() - target.getBbWidth() / 2 - SkillRules.LUNGE_STOP;
        if (total < 1.0) {
            // もう目の前にいる: その場で斬る
            total = 0;
        }
        Vec3 dir = flat.lengthSqr() < 1e-6 ? flatLook(player) : flat.normalize();
        Vec3 reached = start;
        for (double t = 0.25; t <= total + 1e-6; t += 0.25) {
            Vec3 p = start.add(dir.scale(t));
            AABB box = player.getBoundingBox().move(p.subtract(player.position()));
            if (!level.noCollision(player, box) || !hasFooting(level, p)) {
                break;
            }
            reached = p;
        }
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        boolean arrived = reached.distanceToSqr(start) >= (total - 0.3) * (total - 0.3) || total == 0;
        if (rune == 1 && arrived) {
            // 通り抜け: 敵の向こう側へ (空きと足場があるときだけ)
            Vec3 behind = new Vec3(target.getX(), start.y, target.getZ()).add(dir.scale(target.getBbWidth() / 2 + 1.2));
            AABB box = player.getBoundingBox().move(behind.subtract(player.position()));
            if (level.noCollision(player, box) && hasFooting(level, behind)) {
                reached = behind;
                yaw = (float) (Mth.atan2(dir.x, -dir.z) * Mth.RAD_TO_DEG);
            }
        }
        Vec3 from = player.position();
        if (reached.distanceToSqr(from) > 0.01) {
            player.connection.teleport(reached.x, reached.y, reached.z, yaw, player.getXRot());
            // 通った跡に光を残す
            int steps = (int) Math.ceil(reached.distanceTo(from) / 0.6);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.lerp(reached, i / (double) Math.max(1, steps)).add(0, 0.9, 0);
                level.sendParticles(Skills.RUNE_SPARK.get(), p.x, p.y, p.z, 1, 0.15, 0.3, 0.15, 0.0);
            }
        }
        if (rune == 3 || rune == 4) {
            // 突き通し: 通り道の全員に ×0.6 (伝説の突き通し・骨は ×1.0)
            double through = rune == 4 ? SkillRules.BONE_PIERCE_MULTIPLIER : SkillRules.PIERCE_THROUGH_MULTIPLIER;
            for (LivingEntity e : foesAroundAny(player, range + 2)) {
                if (e != target && distanceToSegment(e.position(), from, reached) <= 1.3 + e.getBbWidth() / 2) {
                    hit(player, e, attackDamage(player) * (float) through, Skill.LUNGE);
                }
            }
        }
        if (rune == 4) {
            // 3 回に 1 度、着いた足もとから骨が噴く
            int n = BONE_COUNT.merge(player.getUUID(), 1, Integer::sum);
            if (n % SkillRules.BONE_EVERY == 0) {
                for (LivingEntity e : foesAroundAny(player, SkillRules.BONE_RADIUS + 2)) {
                    if (Math.hypot(e.getX() - reached.x, e.getZ() - reached.z) <= SkillRules.BONE_RADIUS + e.getBbWidth() / 2) {
                        hit(player, e, attackDamage(player) * (float) SkillRules.BONE_SCALE, Skill.LUNGE);
                    }
                }
                ring(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()),
                        reached, SkillRules.BONE_RADIUS * 0.6, 14, 0.2);
            }
        }
        boolean landed = false;
        if (player.distanceTo(target) <= SkillRules.LUNGE_STOP + target.getBbWidth() / 2 + 2.2) {
            landed = hit(player, target, attackDamage(player) * (float) SkillRules.LUNGE_MULTIPLIER, Skill.LUNGE);
        }
        if (rune == 2 && landed) {
            cooldownScale = 0.5;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_LUNGE.get(), 0.9f, 1.0f);
        return null;
    }

    private static final java.util.Map<java.util.UUID, Integer> BONE_COUNT = new java.util.HashMap<>();

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        double ax = b.x - a.x;
        double az = b.z - a.z;
        double len2 = ax * ax + az * az;
        double t = len2 < 1e-9 ? 0 : Mth.clamp(((p.x - a.x) * ax + (p.z - a.z) * az) / len2, 0, 1);
        double cx = a.x + ax * t;
        double cz = a.z + az * t;
        return Math.hypot(p.x - cx, p.z - cz);
    }

    // 足もと (0.2 下から 1.2 下まで) に、立てるブロックがあるか
    static boolean hasFooting(Level level, Vec3 p) {
        for (double dy = 0.2; dy <= 1.2; dy += 0.5) {
            BlockPos below = BlockPos.containing(p.x, p.y - dy, p.z);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 鯨尾薙ぎ: 身をひねり、周り (半径 3.5) の敵すべてを尾のように薙ぐ。外へ軽く押し出す。
    // 刻印: 押し潮 (押し出し 2 倍・傷 −20%)、挟み (当てた敵を足もとへ)、二度薙ぎ (半分の力で 2 回)
    static String sweep(ServerPlayer player, int rank) {
        int rune = rune(player, Skill.WHALE_SWEEP);
        float damage = attackDamage(player) * (float) SkillRules.sweepMultiplier(rank);
        if (rune == 1) {
            damage *= (float) SkillRules.PUSH_TIDE_SCALE;
        }
        if (rune == 3) {
            damage *= 0.5f;
        }
        sweepOnce(player, damage, rune);
        if (rune == 3) {
            float second = damage;
            SkillEffects.later(player.serverLevel(), SkillRules.DOUBLE_SWEEP_GAP, () -> {
                if (player.isAlive() && !player.hasDisconnected()) {
                    sweepOnce(player, second, 0);
                }
            });
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_SWEEP.get(), 1.0f, 1.0f);
        return null;
    }

    private static void sweepOnce(ServerPlayer player, float damage, int rune) {
        ServerLevel level = player.serverLevel();
        double radius = SkillRules.SWEEP_RADIUS + (WeaponType.has(player, WeaponType.LONG) ? 0.5 : 0);
        for (LivingEntity e : foesAround(player, radius + 1, 1.0, 2.0)) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 > radius) {
                continue;
            }
            if (hit(player, e, damage, Skill.WHALE_SWEEP)) {
                if (rune == 4) {
                    // 挟み切り: 動けなくする (大きな相手は半分の時間)
                    SkillEffects.slow(e, 1.0, isLarge(e) ? SkillRules.SNIP_LARGE_TICKS : SkillRules.SNIP_TICKS);
                    e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                    e.hurtMarked = true;
                } else if (rune == 2) {
                    // 挟み: 足もとへ引き寄せる (大きな相手は動かさない)
                    if (!isLarge(e)) {
                        Vec3 to = new Vec3(-dx, 0, -dz);
                        double len = Math.max(0.5, to.length());
                        e.setDeltaMovement(to.scale(0.55 / len).add(0, 0.12, 0));
                        e.hurtMarked = true;
                    }
                } else {
                    e.knockback(push(player, rune == 1 ? SkillRules.SWEEP_PUSH * 2 : SkillRules.SWEEP_PUSH), -dx, -dz);
                }
            }
        }
        // 足もとに輪を二重に
        for (int r = 0; r < 2; r++) {
            double rr = (r == 0 ? 1.6 : 3.2) * (radius / SkillRules.SWEEP_RADIUS);
            ring(level, Skills.RUNE_SPARK.get(), player.position(), rr, r == 0 ? 12 : 22, 0.9 + r * 0.2);
        }
    }

    // ---------------------------------------------------------------- 風読み
    // 跳び退き: 後ろへ軽く跳ぶ。地に足が着いていて、近くに敵がいるときだけ (背中のアスレの近道にならないように)。
    // 刻印: 射返し (直後の飛び道具 ×1.2)、長跳び (6 ブロック)、風の置き土産 (元の場所に 2 秒の渦)
    static String backstep(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        if (foesAroundAny(player, SkillRules.BACKSTEP_ENEMY_RANGE).isEmpty()) {
            return "message.skyabyss.skill.no_enemy_near";
        }
        int rune = rune(player, Skill.BACKSTEP);
        Vec3 back = flatLook(player).scale(-(rune == 2 ? SkillRules.LONG_LEAP_PUSH : SkillRules.BACKSTEP_PUSH));
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.position();
        player.setDeltaMovement(back.x, SkillRules.BACKSTEP_LIFT, back.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (rune == 4) {
            // 雷の輪: 着地したら、元の場所とのあいだの敵に ×0.8
            SkillEffects.later(level, SkillRules.THUNDER_RING_DELAY, () -> {
                if (!player.isAlive() || player.hasDisconnected() || player.level() != level) {
                    return;
                }
                Vec3 land = player.position();
                for (LivingEntity e : foesAroundAny(player, origin.distanceTo(land) + 3)) {
                    if (distanceToSegment(e.position(), origin, land) <= SkillRules.THUNDER_RING_WIDTH + e.getBbWidth() / 2) {
                        hit(player, e, attackDamage(player) * (float) SkillRules.THUNDER_RING_MULTIPLIER, Skill.BACKSTEP);
                    }
                }
                int steps = (int) Math.ceil(origin.distanceTo(land) / 0.5);
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = origin.lerp(land, i / (double) Math.max(1, steps)).add(0, 0.6, 0);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.1, 0.2, 0.1, 0.02);
                }
                ring(level, ParticleTypes.ELECTRIC_SPARK, origin, 1.0, 12, 0.3);
                ring(level, ParticleTypes.ELECTRIC_SPARK, land, 1.0, 12, 0.3);
            });
        } else if (rune == 1) {
            SkillManager.get(player).returnFireUntil = level.getGameTime() + SkillRules.RETURN_FIRE_TICKS;
        } else if (rune == 3) {
            SkillEffects.zone(SkillEffects.ZoneKind.WHIRL, player, origin, SkillRules.WHIRL_RADIUS, SkillRules.WHIRL_TICKS, rank);
        }
        level.sendParticles(Skills.SKY_MOTE.get(), player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
        sound(player, Skills.SOUND_BACKSTEP.get(), 0.8f, 1.0f);
        return null;
    }

    // 後ろから当てているか (相手の向きの反対側に自分がいるか)
    static boolean behind(LivingEntity target, Player player) {
        Vec3 facing = Vec3.directionFromRotation(0, target.yBodyRot);
        Vec3 to = new Vec3(player.getX() - target.getX(), 0, player.getZ() - target.getZ());
        if (to.lengthSqr() < 1e-6) {
            return false;
        }
        return facing.dot(to.normalize()) < -0.1;
    }

    // 風裂き: 素早い二連。背後から当てると 1.5 倍。
    // 刻印: 背取り (背後 1.8 倍・正面 0.75 倍)、出血 (3 秒の継続傷)、連れ風 (近くの仲間の移動 +10%・4 秒)
    static String rend(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.WIND_REND);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : foesAround(player, SkillRules.REND_RANGE + 1, 2.0, 3.0)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > SkillRules.REND_RANGE + e.getBbWidth() / 2) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (angle > 45) {
                continue;
            }
            double score = angle * 0.2 + d;
            if (score < best) {
                best = score;
                target = e;
            }
        }
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        boolean back = behind(target, player);
        double factor = back && rune != 4 ? (rune == 1 ? SkillRules.BACK_TAKE_BACK_FACTOR : SkillRules.REND_BACK_FACTOR) : (rune == 1 ? SkillRules.BACK_TAKE_FRONT_FACTOR : 1.0);
        double per = SkillRules.rendFront(rank) * factor + (WeaponType.has(player, WeaponType.FAST) ? SkillRules.REND_FAST_BONUS : 0);
        float amount = attackDamage(player) * (float) per;
        LivingEntity foe = target;
        slash(level, player, foe, back);
        hit(player, foe, amount, Skill.WIND_REND);
        // 二連 (噛みつきは三連)
        for (int i = 1; i < (rune == 4 ? SkillRules.BITE_HITS : 2); i++) {
            SkillEffects.later(level, SkillRules.REND_GAP * i, () -> {
                if (player.isAlive() && foe.isAlive() && !player.hasDisconnected() && player.level() == foe.level()) {
                    slash(level, player, foe, back);
                    hit(player, foe, amount, Skill.WIND_REND);
                }
            });
        }
        if (rune == 4 && back) {
            // 背後の上乗せは、傷の代わりに 3 秒の出血になる
            SkillEffects.bleed(player, foe, amount * (float) (SkillRules.REND_BACK_FACTOR - 1.0) * (float) SkillEffects.damagePower(player, Skill.WIND_REND), 3);
        }
        if (rune == 2) {
            SkillEffects.bleed(player, foe, attackDamage(player) * (float) SkillRules.BLEED_PER_SECOND * (float) SkillEffects.damagePower(player, Skill.WIND_REND),
                    SkillRules.BLEED_TICKS / SkillRules.SECOND);
        } else if (rune == 3) {
            for (ServerPlayer ally : SkillEffects.allies(player, SkillRules.ALLY_RANGE)) {
                SkillEffects.timedMod(ally, Attributes.MOVEMENT_SPEED, "companion_wind", SkillRules.COMPANION_WIND_MOVE,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SkillRules.COMPANION_WIND_TICKS);
            }
            SkillEffects.timedMod(player, Attributes.MOVEMENT_SPEED, "companion_wind", SkillRules.COMPANION_WIND_MOVE,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SkillRules.COMPANION_WIND_TICKS);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_LUNGE.get(), 0.8f, 1.5f);
        return null;
    }

    private static void slash(ServerLevel level, ServerPlayer player, LivingEntity foe, boolean back) {
        Vec3 c = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
        for (int i = 0; i < 8; i++) {
            double t = (i - 3.5) * 0.18;
            level.sendParticles(Skills.SKY_MOTE.get(), c.x + t, c.y + t * 0.8, c.z - t * 0.5, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (back) {
            level.sendParticles(Skills.RUNE_SPARK.get(), c.x, c.y, c.z, 4, 0.25, 0.25, 0.25, 0.05);
        }
    }

    // ---------------------------------------------------------------- 潮歌い
    // 守りの唄: 近くの仲間 (自分を含む) に、短い吸収 (黄ハート 1〜2❤)。重ねがけで増えない
    static String wardSong(ServerPlayer player, int rank) {
        int rune = rune(player, Skill.WARD_SONG);
        double radius = rune == 2 ? SkillRules.WIDE_SONG_RADIUS : SkillRules.WARD_SONG_RADIUS;
        float amount = SkillRules.wardAbsorb(rank) + (rune == 1 ? 1 : rune == 2 ? -1 : 0);
        amount = Math.max(1, Math.round(amount * (float) SkillEffects.supportPower(player, Skill.WARD_SONG)));
        int ticks = rune == 1 ? SkillRules.THICK_SONG_TICKS : SkillRules.WARD_SONG_TICKS;
        List<ServerPlayer> targets = new ArrayList<>();
        targets.add(player);
        targets.addAll(SkillEffects.allies(player, radius));
        for (ServerPlayer t : targets) {
            SkillEffects.absorb(t, amount, ticks, rune == 3);
            player.serverLevel().sendParticles(ParticleTypes.NOTE, t.getX(), t.getY() + 2.1, t.getZ(), 3, 0.3, 0.15, 0.3, 1.0);
            player.serverLevel().sendParticles(Skills.SKY_MOTE.get(), t.getX(), t.getY() + 1.0, t.getZ(), 8, 0.4, 0.6, 0.4, 0.02);
        }
        if (rune == 4) {
            abyssSunkSong(player, targets, ticks);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_MARK.get(), 0.9f, 0.8f);
        return null;
    }

    // 伝説の書き換え「沈む唄」(沈み鐘の杖): 割られずに残った守りは、唄の終わりに体力へ沈み、いちばん傷ついた仲間にも合計の半分が届く
    private static void abyssSunkSong(ServerPlayer player, List<ServerPlayer> targets, int ticks) {
        ServerLevel level = player.serverLevel();
        SkillEffects.later(level, Math.max(1, ticks - 2), () -> {
            float pool = 0f;
            for (ServerPlayer t : targets) {
                if (t.isAlive() && t.level() == level) {
                    float left = Math.min(t.getAbsorptionAmount(), SkillRules.ABSORB_CAP);
                    if (left > 0f) {
                        pool += left;
                        t.heal(left);
                        level.sendParticles(ParticleTypes.HEART, t.getX(), t.getY() + 1.9, t.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                        level.sendParticles(jp.revati.skyabyss.abyss.AbyssFx.DUST, t.getX(), t.getY() + 1.0, t.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
                    }
                }
            }
            if (pool > 0f) {
                ServerPlayer worst = null;
                for (ServerPlayer t : targets) {
                    if (t.isAlive() && t.level() == level && (worst == null || t.getHealth() / t.getMaxHealth() < worst.getHealth() / worst.getMaxHealth())) {
                        worst = t;
                    }
                }
                if (worst != null) {
                    worst.heal(pool * (float) jp.revati.skyabyss.abyss.Abyss.SUNK_SONG_SHARE);
                }
            }
        });
    }

    // 引き潮: 前方の敵を足もとへ引き寄せ、鈍足を付ける。大きな相手は引かず鈍足だけ。
    // 刻印: 強い潮 (大きな相手も 1 ブロック引く)、渦 (引いた先に 2 秒の渦)、押し返し (引かずに押し出す)
    static String ebbTide(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.EBB_TIDE);
        double range = SkillRules.ebbRange(rank) + (WeaponType.has(player, WeaponType.LONG) ? 1 : 0);
        Vec3 dir = flatLook(player);
        Vec3 origin = player.position();
        double cos = Math.cos(Math.toRadians(SkillRules.EBB_ANGLE));
        List<LivingEntity> foes = new ArrayList<>();
        for (LivingEntity e : foesAround(player, range + 2, 1.0, 2.0)) {
            Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
            double dist = to.length() - e.getBbWidth() / 2;
            if (dist > range || dist < 0.2 && to.length() < 0.3) {
                continue;
            }
            if (to.length() < 1.0 || to.normalize().dot(dir) >= cos) {
                foes.add(e);
            }
        }
        if (foes.isEmpty()) {
            return "message.skyabyss.skill.no_target";
        }
        int slowTicks = SkillRules.ebbSlowTicks(rank);
        for (LivingEntity e : foes) {
            boolean large = isLarge(e);
            Vec3 to = new Vec3(origin.x - e.getX(), 0, origin.z - e.getZ());
            double len = Math.max(0.3, to.length());
            Vec3 unit = to.scale(1.0 / len);
            if (rune == 4) {
                // 銛打ち: 引かずに結ぶ (下の tie で、結ばれた敵が動くと近くの敵が引かれる)
            } else if (rune == 3) {
                // 押し返し: 引かずに押し出す (大きな相手は動かさない)
                if (!large) {
                    e.setDeltaMovement(unit.scale(-1.0).add(0, 0.2, 0));
                    e.hurtMarked = true;
                }
            } else if (!large) {
                double pull = Mth.clamp(len * 0.28, 0.3, 1.4);
                e.setDeltaMovement(unit.scale(pull).add(0, 0.2, 0));
                e.hurtMarked = true;
            } else if (rune == 1) {
                e.setDeltaMovement(unit.scale(0.4));
                e.hurtMarked = true;
            }
            SkillEffects.slow(e, 0.30, slowTicks);
            level.sendParticles(Skills.SKY_MOTE.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        if (rune == 2) {
            SkillEffects.zone(SkillEffects.ZoneKind.WHIRL, player, origin.add(dir.scale(1.5)), SkillRules.WHIRL_RADIUS, SkillRules.WHIRL_TICKS, rank);
        }
        if (rune == 4) {
            // 結ばれた敵 (最も近い 1 体) が動くと、近くの敵が引かれる
            LivingEntity tied = foes.get(0);
            for (LivingEntity e : foes) {
                if (e.distanceToSqr(player) < tied.distanceToSqr(player)) {
                    tied = e;
                }
            }
            tieStep(player, level, tied, tied.position(), SkillRules.HARPOON_TICKS / 10);
        }
        // 引く波の筋
        for (int i = 0; i < 18; i++) {
            double t = range - i * range / 18.0;
            Vec3 p = origin.add(dir.scale(t));
            level.sendParticles(Skills.SKY_MOTE.get(), p.x, p.y + 0.4, p.z, 1, 0.5, 0.1, 0.5, 0.0);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_BACKSTEP.get(), 1.0f, 0.6f);
        return null;
    }

    // 銛の綱: 0.5 秒ごとに、結ばれた敵が動いた分だけ、近くの敵を引く
    private static void tieStep(ServerPlayer player, ServerLevel level, LivingEntity tied, Vec3 last, int steps) {
        if (steps <= 0 || !tied.isAlive() || !player.isAlive() || player.hasDisconnected() || tied.level() != level) {
            return;
        }
        SkillEffects.later(level, 10, () -> {
            if (!tied.isAlive()) {
                return;
            }
            Vec3 now = tied.position();
            if (now.distanceToSqr(last) > 0.25) {
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, tied.getBoundingBox().inflate(SkillRules.HARPOON_LINK_RADIUS), f -> isFoe(player, f))) {
                    if (e == tied || isLarge(e)) {
                        continue;
                    }
                    Vec3 to = new Vec3(now.x - e.getX(), 0, now.z - e.getZ());
                    double len = Math.max(0.3, to.length());
                    e.setDeltaMovement(to.scale(Mth.clamp(len * 0.2, 0.2, 0.7) / len).add(0, 0.1, 0));
                    e.hurtMarked = true;
                    level.sendParticles(Skills.SKY_MOTE.get(), e.getX(), e.getY() + 0.8, e.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
                }
            }
            tieStep(player, level, tied, now, steps - 1);
        });
        level.sendParticles(Skills.SKY_MOTE.get(), tied.getX(), tied.getY() + tied.getBbHeight() * 0.6, tied.getZ(), 4, 0.25, 0.25, 0.25, 0.0);
    }

    // 巡りの唄: 近くの仲間のマナの回復を数秒、速める (自分は半分)
    static String cycleSong(ServerPlayer player, int rank) {
        long until = player.level().getGameTime() + SkillRules.cycleSongTicks(rank);
        double power = SkillEffects.supportPower(player, Skill.CYCLE_SONG);
        SkillData self = SkillManager.get(player);
        self.regenBoostUntil = until;
        self.regenBoost = SkillRules.CYCLE_SONG_SELF * power;
        player.serverLevel().sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.1, player.getZ(), 4, 0.3, 0.15, 0.3, 1.0);
        for (ServerPlayer ally : SkillEffects.allies(player, SkillRules.CYCLE_SONG_RADIUS)) {
            SkillData d = SkillManager.get(ally);
            d.regenBoostUntil = until;
            d.regenBoost = SkillRules.CYCLE_SONG_ALLY * power;
            player.serverLevel().sendParticles(ParticleTypes.NOTE, ally.getX(), ally.getY() + 2.1, ally.getZ(), 3, 0.3, 0.15, 0.3, 1.0);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_MARK.get(), 0.9f, 0.6f);
        return null;
    }

    // ---------------------------------------------------------------- 灰の鍛え人
    // 焼き入れ: 手の武器を熾にする (自分か、見ている仲間 1 人)。攻撃に炎の傷が乗る (SkillEvents)
    static String temper(ServerPlayer player, int rank) {
        ServerPlayer target = player;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SkillRules.TEMPER_RANGE));
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
                new AABB(eye, end).inflate(1.0), e -> e instanceof ServerPlayer p && p != player && p.isAlive() && !p.isSpectator(),
                SkillRules.TEMPER_RANGE * SkillRules.TEMPER_RANGE);
        if (hit != null && hit.getEntity() instanceof ServerPlayer p) {
            target = p;
        }
        long now = player.level().getGameTime();
        ServerLevel level = player.serverLevel();
        List<ServerPlayer> targets = new ArrayList<>();
        targets.add(target);
        if (rune(player, Skill.TEMPER) == 4) {
            // 火移し: 自分と、仲間 2 人まで (見ている仲間が先) に同時に
            if (!targets.contains(player)) {
                targets.add(player);
            }
            List<ServerPlayer> near = new ArrayList<>(SkillEffects.allies(player, SkillRules.TEMPER_RANGE));
            near.sort(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(player)));
            int extra = SkillRules.FIRE_PASS_ALLIES - (target != player ? 1 : 0);
            for (ServerPlayer a : near) {
                if (extra <= 0) {
                    break;
                }
                if (!targets.contains(a)) {
                    targets.add(a);
                    extra--;
                }
            }
        }
        for (ServerPlayer t : targets) {
            SkillData d = SkillManager.get(t);
            d.temperUntil = now + SkillRules.TEMPER_TICKS;
            d.temperMult = SkillRules.temperMultiplier(rank);
            level.sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + 1.1, t.getZ(), 18, 0.35, 0.5, 0.35, 0.02);
            level.sendParticles(ParticleTypes.LAVA, t.getX(), t.getY() + 1.2, t.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_RIPOSTE.get(), 0.9f, 0.6f);
        return null;
    }

    // 熾火の槌: 前方に叩きつけ、地に熾火を残す (敵だけ燃やす)。
    // 刻印: 長い火 (熾火 8 秒・傷 −30%)、二度叩き (2 回・×0.6 ずつ)、火柱 (狭く、中心の敵に ×1.8)
    static String hammer(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.EMBER_HAMMER);
        double radius = rune == 3 ? SkillRules.FIRE_PILLAR_RADIUS
                : SkillRules.HAMMER_RADIUS + (WeaponType.has(player, WeaponType.HEAVY) ? SkillRules.HAMMER_HEAVY_RADIUS : 0);
        Vec3 center = player.position().add(flatLook(player).scale(SkillRules.HAMMER_REACH));
        double mult = SkillRules.hammerMultiplier(rank);
        int fireTicks = SkillRules.EMBER_TICKS;
        if (rune == 1) {
            mult *= SkillRules.LONG_FIRE_SCALE;
            fireTicks = SkillRules.LONG_FIRE_TICKS;
        } else if (rune == 2 || rune == 4) {
            mult = SkillRules.DOUBLE_STRIKE_SCALE;
            if (rune == 4) {
                // 二度打ち・火床: 熾火が 8 秒
                fireTicks = SkillRules.LONG_FIRE_TICKS;
            }
        }
        float amount = attackDamage(player) * (float) mult;
        slam(player, center, radius, amount, rune == 3);
        if (rune == 2 || rune == 4) {
            SkillEffects.later(level, SkillRules.DOUBLE_STRIKE_GAP, () -> {
                if (player.isAlive() && !player.hasDisconnected() && player.level() == level) {
                    slam(player, center, radius, amount, false);
                }
            });
        }
        SkillEffects.Zone bed = SkillEffects.zone(SkillEffects.ZoneKind.EMBER, player, center, radius, fireTicks, rank);
        bed.weaken = rune == 4;
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, 0.6f);
        return null;
    }

    private static void slam(ServerPlayer player, Vec3 center, double radius, float amount, boolean pillar) {
        ServerLevel level = player.serverLevel();
        AABB box = new AABB(center, center).inflate(radius + 1, 1.0, radius + 1).expandTowards(0, 2.0, 0);
        List<LivingEntity> foes = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, f -> isFoe(player, f))) {
            double d = Math.hypot(e.getX() - center.x, e.getZ() - center.z);
            if (d - e.getBbWidth() / 2 <= radius) {
                foes.add(e);
            }
        }
        foes.sort(Comparator.comparingDouble(e -> Math.hypot(e.getX() - center.x, e.getZ() - center.z)));
        for (int i = 0; i < foes.size(); i++) {
            LivingEntity e = foes.get(i);
            float a = pillar && i == 0 ? amount * (float) SkillRules.FIRE_PILLAR_CENTER : amount;
            if (hit(player, e, a, Skill.EMBER_HAMMER)) {
                e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 40));
            }
        }
        if (pillar) {
            for (int k = 0; k < 14; k++) {
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + k * 0.3, center.z, 3, 0.2, 0.05, 0.2, 0.02);
            }
        } else {
            ring(level, ParticleTypes.FLAME, center, radius, 26, 0.2);
            level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.3, center.z, 8, radius * 0.4, 0.1, radius * 0.4, 0.0);
        }
    }

    // 灰散らし: 周りの敵に「灰かぶり」(攻撃が弱まり、飛び道具が遅くなる)
    static String ashScatter(ServerPlayer player, int rank) {
        List<LivingEntity> foes = new ArrayList<>();
        for (LivingEntity e : foesAroundAny(player, SkillRules.ASH_SCATTER_RADIUS)) {
            if (e.distanceTo(player) - e.getBbWidth() / 2 <= SkillRules.ASH_SCATTER_RADIUS) {
                foes.add(e);
            }
        }
        if (foes.isEmpty()) {
            return "message.skyabyss.skill.no_enemy_near";
        }
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : foes) {
            SkillEffects.ashFall(e, SkillRules.ashScatterTicks(rank));
            level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 14, 0.35, 0.4, 0.35, 0.02);
        }
        ring(level, ParticleTypes.ASH, player.position(), SkillRules.ASH_SCATTER_RADIUS, 36, 0.8);
        if (rune(player, Skill.ASH_SCATTER) == 4) {
            abyssCreepingAsh(player, level, rank);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_SWEEP.get(), 0.8f, 0.5f);
        return null;
    }

    // 伝説の書き換え「這う灰」(灰喰いの鎌): 灰かぶりの敵のまわりの敵へ、2 秒おきに灰が移る (最大 3 度)。灰かぶりは重ならないので、数が増えるだけ
    private static void abyssCreepingAsh(ServerPlayer player, ServerLevel level, int rank) {
        for (int step = 1; step <= jp.revati.skyabyss.abyss.Abyss.ASH_STEPS; step++) {
            SkillEffects.later(level, step * jp.revati.skyabyss.abyss.Abyss.ASH_EVERY, () -> {
                if (!player.isAlive() || player.level() != level) {
                    return;
                }
                List<LivingEntity> ashed = new ArrayList<>();
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24, 8, 24), f -> isFoe(player, f))) {
                    if (SkillEffects.ashed(e)) {
                        ashed.add(e);
                    }
                }
                for (LivingEntity from : ashed) {
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(jp.revati.skyabyss.abyss.Abyss.ASH_RADIUS, 2, jp.revati.skyabyss.abyss.Abyss.ASH_RADIUS),
                            f -> isFoe(player, f) && !SkillEffects.ashed(f))) {
                        SkillEffects.ashFall(e, SkillRules.ashScatterTicks(rank));
                        level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 14, 0.35, 0.4, 0.35, 0.02);
                        level.sendParticles(jp.revati.skyabyss.abyss.AbyssFx.DUST, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- 骨の射手
    static ItemStack heldBow(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // 雲穿ち: 弓を引かずに放つ、まっすぐ飛ぶ貫く矢。矢を 1 本使う (クリエイティブでは使わない)。
    // 弓の写しに「貫通」を付けて矢を作るので、弓の力・火矢などのエンチャントもそのまま乗る。
    // 刻印: 細矢 (貫通 +2・威力 −10%)、重矢 (貫通 −2・威力 ×1.3・速さ −30%)、稲光 (貫いた敵の間に小さな雷)
    static String pierce(ServerPlayer player, int rank) {
        ItemStack bow = heldBow(player);
        if (bow.isEmpty()) {
            return "message.skyabyss.skill.need_bow";
        }
        ItemStack ammo = player.getProjectile(bow);
        if (ammo.isEmpty() || !(ammo.getItem() instanceof ArrowItem arrowItem)) {
            return "message.skyabyss.skill.no_arrows";
        }
        ServerLevel level = player.serverLevel();
        int rune = rune(player, Skill.CLOUD_PIERCE);
        int pierce = SkillRules.piercePierce(rank) + (WeaponType.has(player, WeaponType.RANGED) ? 1 : 0);
        double damageScale = SkillEffects.damagePower(player, Skill.CLOUD_PIERCE);
        float speed = SkillRules.PIERCE_SPEED;
        if (rune == 1) {
            pierce += SkillRules.THIN_ARROW_PIERCE;
            damageScale *= SkillRules.THIN_ARROW_SCALE;
        } else if (rune == 2) {
            pierce = Math.max(0, pierce - 2);
            damageScale *= SkillRules.HEAVY_ARROW_SCALE;
            speed *= (float) SkillRules.HEAVY_ARROW_SPEED;
        }
        ItemStack weapon = bow.copy();
        if (pierce > 0) {
            weapon.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PIERCING), pierce);
        }
        AbstractArrow arrow = arrowItem.createArrow(level, ammo.copyWithCount(1), player, weapon);
        arrow.setNoGravity(true);
        arrow.setBaseDamage(SkillRules.pierceBaseDamage(rank) * damageScale);
        arrow.setCritArrow(false);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.addTag(PIERCE_TAG);
        if (rune == 3) {
            arrow.addTag(LIGHTNING_TAG);
        }
        if (rune == 4) {
            arrow.addTag(SKY_FALL_TAG);
        }
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 0f);
        level.addFreshEntity(arrow);
        ARROWS.add(new TrackedArrow(arrow, new int[1], (int) Math.ceil(SkillRules.PIERCE_LIFETIME * SkillRules.PIERCE_SPEED / speed)));
        // 無限のエンチャントの弓なら、ふつうの矢は減らない (バニラの弓と同じ)
        if (!player.getAbilities().instabuild) {
            ammo.shrink(net.minecraft.world.item.enchantment.EnchantmentHelper.processAmmoUse(level, bow, ammo, 1));
        }
        player.swing(player.getMainHandItem() == bow ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        particle(level, Skills.SKY_MOTE.get(), player.getEyePosition().add(player.getLookAngle().scale(1.2)), 8, 0.15, 0.05);
        sound(player, Skills.SOUND_PIERCE.get(), 1.0f, rune == 2 ? 0.7f : 1.0f);
        return null;
    }

    // 骨の罠: 足もとに罠を置く (最大 2。古いものから消える)。踏んだ敵を縛り、傷を与える
    static String boneTrap(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        SkillEffects.zone(SkillEffects.ZoneKind.TRAP, player, player.position(), SkillRules.TRAP_TRIGGER, SkillRules.TRAP_LIFETIME, rank);
        ServerLevel level = player.serverLevel();
        ring(level, Skills.RUNE_SPARK.get(), player.position(), 1.0, 14, 0.15);
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_BACKSTEP.get(), 0.7f, 0.5f);
        return null;
    }

    // 罠が踏まれた: 大きな相手は縛られず ×1.2 の傷だけ。ほかは縛って ×0.8 / ×1.0
    static void springTrap(ServerPlayer owner, LivingEntity victim, int rank, Vec3 at) {
        ServerLevel level = owner.serverLevel();
        boolean large = isLarge(victim);
        float amount = attackDamage(owner) * (float) (large ? SkillRules.TRAP_LARGE_MULTIPLIER : SkillRules.trapMultiplier(rank));
        if (!large) {
            SkillEffects.bind(victim, SkillRules.trapBindTicks(rank));
        }
        hit(owner, victim, amount, Skill.BONE_TRAP);
        particle(level, Skills.RUNE_SPARK.get(), at.add(0, 0.5, 0), 22, 0.5, 0.08);
        level.playSound(null, at.x, at.y, at.z, Skills.SOUND_MARK.get(), SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    // 天の驟雨: 見ている先 (24 ブロック以内) に、空から光の矢が 6 度降る。矢は使わない
    static String rain(ServerPlayer player, int rank) {
        if (heldBow(player).isEmpty()) {
            return "message.skyabyss.skill.need_bow";
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SkillRules.RAIN_REACH));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        // 途中に敵がいれば、その足もとへ
        var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, center,
                new AABB(eye, center).inflate(1.0), e -> isFoe(player, e), eye.distanceToSqr(center));
        if (entityHit != null) {
            // 浮いている相手 (岩鯨の心臓など) にも当たるよう、相手の足もとを中心にする
            center = entityHit.getEntity().position();
        } else {
            // 宙を指していたら、真下の地面まで下ろす (8 ブロックまで)
            BlockHitResult ground = level.clip(new ClipContext(center.add(0, 0.5, 0), center.add(0, -8, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (ground.getType() != HitResult.Type.MISS) {
                center = ground.getLocation();
            }
        }
        int agility = Growth.get(player).stat(GrowthRules.Stat.AGILITY);
        double damage = SkillRules.rainDamage(rank) * (1 + GrowthRules.projectileMultiplier(agility));
        RAINS.add(new Rain(level, center, player.getUUID(), damage));
        // 降る範囲を地面に輪で示す (どこに降るか分かるように)
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28;
            level.sendParticles(Skills.SKY_MOTE.get(), center.x + Math.cos(a) * SkillRules.RAIN_RADIUS, center.y + 0.15,
                    center.z + Math.sin(a) * SkillRules.RAIN_RADIUS, 1, 0.02, 0.02, 0.02, 0.0);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, center.x, center.y, center.z, Skills.SOUND_RAIN.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        sound(player, Skills.SOUND_PIERCE.get(), 0.5f, 1.4f);
        return null;
    }

    // ---------------------------------------------------------------- 旅人
    // 合図: 見ている先に 20 秒の光の標 (仲間全員に見える)。敵に当てると 5 秒の印 (鷹の目と同じ。高いほう)
    static String signal(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SkillRules.SIGNAL_RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, stop,
                new AABB(eye, stop).inflate(1.0), e -> e instanceof LivingEntity le && le.isAlive() && e != player && !(e instanceof ArmorStand),
                eye.distanceToSqr(stop));
        Vec3 at = stop;
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            at = target.position();
            if (isFoe(player, target)) {
                SkillEvents.mark(target, SkillRules.signalMark(rank), level.getGameTime(), SkillRules.MARK_TICKS, player);
            } else {
                SkillEffects.glow(target, SkillRules.MARK_TICKS, player);
            }
        } else if (block.getType() == HitResult.Type.MISS) {
            // 宙を指していたら、真下の地面まで下ろす (16 ブロックまで)
            BlockHitResult ground = level.clip(new ClipContext(at, at.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (ground.getType() != HitResult.Type.MISS) {
                at = ground.getLocation();
            }
        }
        SkillEffects.zone(SkillEffects.ZoneKind.SIGNAL, player, at, 0.5, SkillRules.SIGNAL_TICKS, rank);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, at.x, at.y, at.z, Skills.SOUND_MARK.get(), SoundSource.PLAYERS, 1.0f, 1.3f);
        return null;
    }

    // ---------------------------------------------------------------- 毎 tick (SkillEvents から)
    public static void tick(MinecraftServer server) {
        SkillEffects.tick(server);
        for (Iterator<TrackedArrow> it = ARROWS.iterator(); it.hasNext();) {
            TrackedArrow t = it.next();
            AbstractArrow a = t.arrow();
            int age = ++t.age()[0];
            if (a.isRemoved()) {
                abyssSkyFall(a);
                it.remove();
                continue;
            }
            // 刺さるか寿命が来たら消える (拾えない矢を残さない)
            boolean stuck = age > 2 && a.position().distanceToSqr(a.xo, a.yo, a.zo) < 1e-4;
            if (age > t.lifetime() || stuck) {
                if (a.level() instanceof ServerLevel l) {
                    l.sendParticles(Skills.SKY_MOTE.get(), a.getX(), a.getY(), a.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
                }
                abyssSkyFall(a);
                a.discard();
                it.remove();
                continue;
            }
            if (a.level() instanceof ServerLevel l) {
                l.sendParticles(Skills.SKY_MOTE.get(), a.getX(), a.getY(), a.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        for (Iterator<Rain> it = RAINS.iterator(); it.hasNext();) {
            Rain r = it.next();
            r.ticks++;
            // 降ってくる光の矢 (毎 tick 3 本。1 本は縦に並んだ 3 粒で、筋に見える)
            for (int i = 0; i < 3; i++) {
                double a = r.level.random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.level.random.nextDouble()) * SkillRules.RAIN_RADIUS;
                double top = r.center.y + 5 + r.level.random.nextDouble() * 2;
                for (int k = 0; k < 3; k++) {
                    r.level.sendParticles(Skills.SKY_MOTE.get(), r.center.x + Math.cos(a) * d, top + k * 0.45,
                            r.center.z + Math.sin(a) * d, 0, 0, -1.4, 0, 1.0);
                }
            }
            if (r.ticks % SkillRules.RAIN_INTERVAL != 0) {
                continue;
            }
            r.pulses++;
            // 一度降るたびに、地面で光がはじける
            r.level.sendParticles(Skills.SKY_MOTE.get(), r.center.x, r.center.y + 0.2, r.center.z, 10,
                    SkillRules.RAIN_RADIUS * 0.45, 0.05, SkillRules.RAIN_RADIUS * 0.45, 0.03);
            ServerPlayer caster = server.getPlayerList().getPlayer(r.caster);
            if (caster != null && caster.level() == r.level && caster.isAlive()) {
                AABB box = new AABB(r.center, r.center).inflate(SkillRules.RAIN_RADIUS, 1, SkillRules.RAIN_RADIUS).expandTowards(0, 4, 0);
                for (LivingEntity e : r.level.getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(caster, e))) {
                    double dx = e.getX() - r.center.x;
                    double dz = e.getZ() - r.center.z;
                    if (dx * dx + dz * dz <= SkillRules.RAIN_RADIUS * SkillRules.RAIN_RADIUS + e.getBbWidth()) {
                        if (hit(caster, e, (float) r.damage, Skill.SKY_RAIN)) {
                            r.level.sendParticles(Skills.SKY_MOTE.get(), e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                        }
                    }
                }
            }
            if (r.pulses >= SkillRules.RAIN_PULSES || caster == null) {
                it.remove();
            }
        }
    }

    // 天落とし: 矢が止まった所 (刺さる・寿命・貫き終え) に、0.8 秒後に雷の柱 (半径 2.5。攻撃力の ×0.9)
    public static void abyssSkyFall(AbstractArrow arrow) {
        if (!arrow.getTags().contains(SKY_FALL_TAG) || !(arrow.level() instanceof ServerLevel level) || !(arrow.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        Vec3 at = arrow.position();
        arrow.removeTag(SKY_FALL_TAG);
        ring(level, jp.revati.skyabyss.abyss.AbyssFx.DUST, at, jp.revati.skyabyss.abyss.Abyss.SKY_FALL_RADIUS, 20, 0.1);
        SkillEffects.later(level, jp.revati.skyabyss.abyss.Abyss.SKY_FALL_DELAY, () -> {
            if (!owner.isAlive() || owner.level() != level) {
                return;
            }
            AABB box = new AABB(at, at).inflate(jp.revati.skyabyss.abyss.Abyss.SKY_FALL_RADIUS + 1, 2, jp.revati.skyabyss.abyss.Abyss.SKY_FALL_RADIUS + 1).expandTowards(0, 3, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, f -> isFoe(owner, f))) {
                if (Math.hypot(e.getX() - at.x, e.getZ() - at.z) - e.getBbWidth() / 2 <= jp.revati.skyabyss.abyss.Abyss.SKY_FALL_RADIUS) {
                    hit(owner, e, attackDamage(owner) * (float) jp.revati.skyabyss.abyss.Abyss.SKY_FALL_SCALE, Skill.CLOUD_PIERCE);
                }
            }
            for (int k = 0; k < 16; k++) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + k * 0.4, at.z, 3, 0.15, 0.05, 0.15, 0.02);
            }
            level.sendParticles(jp.revati.skyabyss.abyss.AbyssFx.DUST, at.x, at.y + 0.3, at.z, 14, 0.8, 0.3, 0.8, 0.0);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
        });
    }

    public static void clear() {
        ARROWS.clear();
        RAINS.clear();
        SkillEffects.clear();
    }

    // 自己テスト用: 降っている途中の驟雨の数
    static int pendingRains() {
        return RAINS.size();
    }
}
