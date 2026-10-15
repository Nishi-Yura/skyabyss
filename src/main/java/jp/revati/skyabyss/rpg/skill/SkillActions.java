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
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.OwnableEntity;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 能動の技の中身 (サーバーだけで動く)。perform は、できなければ理由の翻訳キーを返す (マナもクールダウンも使わない)。
// 傷はバニラの攻撃に上乗せする形で入れる: 当てる前後で相手の無敵時間を 0 にし、ふつうの攻撃を食わないようにする
public final class SkillActions {
    // いま技の傷を入れている最中か (常時の技が「技の傷」を見分けるため)
    private static final ThreadLocal<Skill> DEALING = new ThreadLocal<>();
    // 雲穿ちの矢 (寿命を数え、尾を引かせる)
    public static final String PIERCE_TAG = "skyabyss_cloud_pierce";
    private static final List<TrackedArrow> ARROWS = new ArrayList<>();
    // 天の驟雨の、これから降る分
    private static final List<Rain> RAINS = new ArrayList<>();

    private record TrackedArrow(AbstractArrow arrow, int[] age) {
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

    public static String perform(ServerPlayer player, Skill skill, int rank) {
        return switch (skill) {
            case TITAN_CLEAVE -> cleave(player, rank);
            case LUNGE -> lunge(player, rank);
            case WHALE_SWEEP -> sweep(player, rank);
            case CLOUD_PIERCE -> pierce(player, rank);
            case BACKSTEP -> backstep(player, rank);
            case SKY_RAIN -> rain(player, rank);
            default -> "message.skyabyss.skill.not_active";
        };
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
        if (e instanceof Enemy) {
            return true;
        }
        return e instanceof Mob mob && mob.getTarget() instanceof Player;
    }

    public static boolean isLarge(LivingEntity e) {
        return SkillRules.isLarge(e.getBbWidth(), e.getBbHeight(), e.getMaxHealth());
    }

    // 水平の向き (見ている向きから上下を除いたもの)
    private static Vec3 flatLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
    }

    private static List<LivingEntity> foesAround(ServerPlayer player, double radius, double down, double up) {
        AABB box = player.getBoundingBox().inflate(radius, 0, radius).expandTowards(0, up, 0).expandTowards(0, -down, 0);
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, box, e -> isFoe(player, e) && player.hasLineOfSight(e));
    }

    // 技の傷を入れる。近接の技は「プレイヤーの攻撃」として入る (返し刃・鷹の目の印が効く)
    public static boolean hit(ServerPlayer player, LivingEntity target, float amount, Skill skill) {
        DEALING.set(skill);
        try {
            target.invulnerableTime = 0;
            boolean hurt = target.hurt(player.damageSources().playerAttack(player), amount);
            target.invulnerableTime = 0;
            if (hurt) {
                player.setLastHurtMob(target);
            }
            return hurt;
        } finally {
            DEALING.remove();
        }
    }

    private static float attackDamage(Player player) {
        return (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    // ---------------------------------------------------------------- 剣士
    // 巨神斬り: 前方の扇へ大きく振り下ろす。大きな相手 (巨神の番兵長・心臓など) には さらに 1.5 倍
    static String cleave(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = flatLook(player);
        Vec3 origin = player.position();
        double cos = Math.cos(Math.toRadians(SkillRules.CLEAVE_HALF_ANGLE));
        List<LivingEntity> targets = new ArrayList<>();
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
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        float base = attackDamage(player) * (float) SkillRules.cleaveMultiplier(rank);
        boolean bigHit = false;
        for (int i = 0; i < Math.min(targets.size(), SkillRules.CLEAVE_MAX_TARGETS); i++) {
            LivingEntity e = targets.get(i);
            boolean large = isLarge(e);
            if (hit(player, e, large ? base * (float) SkillRules.CLEAVE_LARGE_BONUS : base, Skill.TITAN_CLEAVE) && large) {
                bigHit = true;
                particle(level, Skills.RUNE_SPARK.get(), e.position().add(0, e.getBbHeight() * 0.6, 0), 14, 0.4, 0.08);
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        // 弧を描く光: 右上から左下へ、半径 2.6 の扇に沿って
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = 0; i <= 12; i++) {
            double a = Math.toRadians(-SkillRules.CLEAVE_HALF_ANGLE + i * SkillRules.CLEAVE_HALF_ANGLE * 2 / 12.0);
            Vec3 p = origin.add(dir.scale(Math.cos(a) * 2.6)).add(side.scale(Math.sin(a) * 2.6)).add(0, 1.8 - i * 0.09, 0);
            level.sendParticles(Skills.RUNE_SPARK.get(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
        sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, bigHit ? 0.85f : 1.0f);
        return null;
    }

    // 踏み込み: 見ている先 (8 ブロック以内、左右 25 度) の敵の手前まで一気に詰めて斬る。
    // 足場のない所・壁の向こうへは出ない (背中のアスレの近道にならないように)
    static String lunge(ServerPlayer player, int rank) {
        if (!player.onGround()) {
            return "message.skyabyss.skill.need_ground";
        }
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : foesAround(player, SkillRules.LUNGE_RANGE + 1, 2.0, 3.0)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > SkillRules.LUNGE_RANGE + e.getBbWidth() / 2) {
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
        Vec3 from = player.position();
        if (reached.distanceToSqr(from) > 0.01) {
            float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
            player.connection.teleport(reached.x, reached.y, reached.z, yaw, player.getXRot());
            // 通った跡に光を残す
            int steps = (int) Math.ceil(reached.distanceTo(from) / 0.6);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = from.lerp(reached, i / (double) Math.max(1, steps)).add(0, 0.9, 0);
                level.sendParticles(Skills.RUNE_SPARK.get(), p.x, p.y, p.z, 1, 0.15, 0.3, 0.15, 0.0);
            }
        }
        if (player.distanceTo(target) <= SkillRules.LUNGE_STOP + target.getBbWidth() / 2 + 2.2) {
            hit(player, target, attackDamage(player) * (float) SkillRules.LUNGE_MULTIPLIER, Skill.LUNGE);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(player, Skills.SOUND_LUNGE.get(), 0.9f, 1.0f);
        return null;
    }

    // 足もと (0.2 下から 1.2 下まで) に、立てるブロックがあるか
    private static boolean hasFooting(Level level, Vec3 p) {
        for (double dy = 0.2; dy <= 1.2; dy += 0.5) {
            BlockPos below = BlockPos.containing(p.x, p.y - dy, p.z);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 鯨尾薙ぎ: 身をひねり、周り (半径 3.5) の敵すべてを尾のように薙ぐ。外へ軽く押し出す
    static String sweep(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        float damage = attackDamage(player) * (float) SkillRules.sweepMultiplier(rank);
        for (LivingEntity e : foesAround(player, SkillRules.SWEEP_RADIUS + 1, 1.0, 2.0)) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 > SkillRules.SWEEP_RADIUS) {
                continue;
            }
            if (hit(player, e, damage, Skill.WHALE_SWEEP)) {
                e.knockback(0.6, -dx, -dz);
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        // 足もとに輪を二重に
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? 1.6 : 3.2;
            int n = ring == 0 ? 12 : 22;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                level.sendParticles(Skills.RUNE_SPARK.get(), player.getX() + Math.cos(a) * r, player.getY() + 0.9 + ring * 0.2,
                        player.getZ() + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
        sound(player, Skills.SOUND_SWEEP.get(), 1.0f, 1.0f);
        return null;
    }

    // ---------------------------------------------------------------- 弓使い
    private static ItemStack heldBow(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // 雲穿ち: 弓を引かずに放つ、まっすぐ飛ぶ貫く矢。矢を 1 本使う (クリエイティブでは使わない)。
    // 弓の写しに「貫通」を付けて矢を作るので、弓の力・火矢などのエンチャントもそのまま乗る
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
        ItemStack weapon = bow.copy();
        weapon.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PIERCING),
                SkillRules.piercePierce(rank));
        AbstractArrow arrow = arrowItem.createArrow(level, ammo.copyWithCount(1), player, weapon);
        arrow.setNoGravity(true);
        arrow.setBaseDamage(SkillRules.pierceBaseDamage(rank));
        arrow.setCritArrow(false);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.addTag(PIERCE_TAG);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, SkillRules.PIERCE_SPEED, 0f);
        level.addFreshEntity(arrow);
        ARROWS.add(new TrackedArrow(arrow, new int[1]));
        if (!player.getAbilities().instabuild) {
            ammo.shrink(1);
        }
        player.swing(player.getMainHandItem() == bow ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        particle(level, Skills.SKY_MOTE.get(), player.getEyePosition().add(player.getLookAngle().scale(1.2)), 8, 0.15, 0.05);
        sound(player, Skills.SOUND_PIERCE.get(), 1.0f, 1.0f);
        return null;
    }

    // 跳び退き: 後ろへ軽く跳ぶ。地に足が着いていて、近くに敵がいるときだけ (背中のアスレの近道にならないように)
    static String backstep(ServerPlayer player, int rank) {
        if (!player.onGround()) {
            return "message.skyabyss.skill.need_ground";
        }
        if (foesAroundAny(player, SkillRules.BACKSTEP_ENEMY_RANGE).isEmpty()) {
            return "message.skyabyss.skill.no_enemy_near";
        }
        Vec3 back = flatLook(player).scale(-SkillRules.BACKSTEP_PUSH);
        player.setDeltaMovement(back.x, SkillRules.BACKSTEP_LIFT, back.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        ServerLevel level = player.serverLevel();
        level.sendParticles(Skills.SKY_MOTE.get(), player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
        sound(player, Skills.SOUND_BACKSTEP.get(), 0.8f, 1.0f);
        return null;
    }

    // 見通しを問わない、近くの敵 (跳び退きの条件)
    private static List<LivingEntity> foesAroundAny(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> isFoe(player, e));
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
            center = entityHit.getEntity().position();
        }
        // 宙を指していたら、真下の地面まで下ろす (8 ブロックまで)
        BlockHitResult ground = level.clip(new ClipContext(center.add(0, 0.5, 0), center.add(0, -8, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (ground.getType() != HitResult.Type.MISS) {
            center = ground.getLocation();
        }
        int agility = Growth.get(player).stat(GrowthRules.Stat.AGILITY);
        double damage = SkillRules.rainDamage(rank) * (1 + GrowthRules.projectileMultiplier(agility));
        RAINS.add(new Rain(level, center, player.getUUID(), damage));
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, center.x, center.y, center.z, Skills.SOUND_RAIN.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        sound(player, Skills.SOUND_PIERCE.get(), 0.5f, 1.4f);
        return null;
    }

    // ---------------------------------------------------------------- 毎 tick (SkillEvents から)
    public static void tick(MinecraftServer server) {
        for (Iterator<TrackedArrow> it = ARROWS.iterator(); it.hasNext();) {
            TrackedArrow t = it.next();
            AbstractArrow a = t.arrow();
            int age = ++t.age()[0];
            if (a.isRemoved()) {
                it.remove();
                continue;
            }
            // 刺さるか寿命が来たら消える (拾えない矢を残さない)
            boolean stuck = a.getDeltaMovement().lengthSqr() < 1e-4 || age > 3 && a.position().distanceToSqr(a.xo, a.yo, a.zo) < 1e-4;
            if (age > SkillRules.PIERCE_LIFETIME || (stuck && age > 2)) {
                if (a.level() instanceof ServerLevel l) {
                    l.sendParticles(Skills.SKY_MOTE.get(), a.getX(), a.getY(), a.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
                }
                a.discard();
                it.remove();
                continue;
            }
            if (a.level() instanceof ServerLevel l && age % 1 == 0) {
                l.sendParticles(Skills.SKY_MOTE.get(), a.getX(), a.getY(), a.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        for (Iterator<Rain> it = RAINS.iterator(); it.hasNext();) {
            Rain r = it.next();
            r.ticks++;
            // 降ってくる光 (毎 tick 少し)
            for (int i = 0; i < 3; i++) {
                double a = r.level.random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.level.random.nextDouble()) * SkillRules.RAIN_RADIUS;
                r.level.sendParticles(Skills.SKY_MOTE.get(), r.center.x + Math.cos(a) * d, r.center.y + 5 + r.level.random.nextDouble() * 2,
                        r.center.z + Math.sin(a) * d, 0, 0, -1.4, 0, 1.0);
            }
            if (r.ticks % SkillRules.RAIN_INTERVAL != 0) {
                continue;
            }
            r.pulses++;
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

    public static void clear() {
        ARROWS.clear();
        RAINS.clear();
    }

    // 自己テスト用: 降っている途中の驟雨の数
    static int pendingRains() {
        return RAINS.size();
    }
}
