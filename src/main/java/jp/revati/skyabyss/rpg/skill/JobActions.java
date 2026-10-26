package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 職の新しい技 (docs/design/43 の 4 段目) の中身。星読み (魔法使い) の 4 つと、鍛冶師・暗殺者・呪術師の 4 つ目。
// 数値は SkillRules の「職の新しい技」。傷は SkillActions.hit で入れる (共鳴の声などの倍率と、波紋の鈍足が付く)。
// 魔法の傷 (星読み) は手の武器の攻撃力から: 武器の攻撃力 × 倍率 × (1 + 精神 × 0.6%)。星の誓い (要) で +25%
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class JobActions {
    // 飛んでいる熾火の玉
    private static final List<Orb> ORBS = new ArrayList<>();
    // 霧隠れの最中の人 (消えている期限)。最初の一撃で解ける
    private static final Map<UUID, Long> VEIL = new HashMap<>();

    private static final class Orb {
        final ServerLevel level;
        final ServerPlayer owner;
        final float damage;
        final Vec3 dir;
        Vec3 pos;
        int age;

        Orb(ServerLevel level, ServerPlayer owner, float damage, Vec3 pos, Vec3 dir) {
            this.level = level;
            this.owner = owner;
            this.damage = damage;
            this.pos = pos;
            this.dir = dir;
        }
    }

    private JobActions() {
    }

    public static String perform(ServerPlayer player, Skill skill, int rank) {
        return switch (skill) {
            case THUNDER_BOLT -> thunderBolt(player, rank);
            case EMBER_ORB -> emberOrb(player, rank);
            case FROST_TIDE -> frostTide(player, rank);
            case BLINK -> blink(player, rank);
            case FORGE_STOMP -> forgeStomp(player, rank);
            case MIST_VEIL -> mistVeil(player, rank);
            case CURSE_MARK -> curseMark(player, rank);
            case LAMP_CHARM -> lampCharm(player, rank);
            default -> "message.skyabyss.skill.no_target";
        };
    }

    // ---------------------------------------------------------------- 魔法の傷
    // 手の武器の攻撃力 (素の 1 + 武器の上乗せ)。鍛えた段は 1 段 3%
    static double weaponDamage(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        double[] sum = {1.0};
        stack.getAttributeModifiers().forEach(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum[0] += mod.amount();
            }
        });
        return sum[0] * (1 + 0.03 * ModGear.enhance(stack));
    }

    public static float spell(ServerPlayer player, double multiplier) {
        int spirit = Growth.get(player).stats[GrowthRules.Stat.SPIRIT.ordinal()];
        double oath = SkillManager.get(player).knows(Skill.STAR_OATH) ? 1 + SkillRules.STAR_OATH_POWER : 1;
        return (float) (weaponDamage(player) * multiplier * (1 + GrowthRules.SPIRIT_SPELL * spirit) * oath);
    }

    // 見ている向きの最初の敵 (届く所まで)。壁で止まる
    private static LivingEntity aimed(ServerPlayer player, double range, Vec3[] stopOut) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && e != player && !(e instanceof ArmorStand) && SkillActions.isFoe(player, e),
                eye.distanceToSqr(stop));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            stopOut[0] = target.position().add(0, target.getBbHeight() * 0.6, 0);
            return target;
        }
        stopOut[0] = stop;
        return null;
    }

    private static void line(ServerLevel level, Vec3 a, Vec3 b, net.minecraft.core.particles.ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private static void ring(ServerLevel level, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions p, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    // ---------------------------------------------------------------- 雷の矢
    static String thunderBolt(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3[] stop = new Vec3[1];
        LivingEntity target = aimed(player, SkillRules.BOLT_RANGE, stop);
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6)).subtract(0, 0.25, 0);
        line(level, from, stop[0], ParticleTypes.ELECTRIC_SPARK, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.7f);
        if (target != null) {
            float dmg = spell(player, SkillRules.boltMultiplier(rank));
            SkillActions.hit(player, target, dmg, Skill.THUNDER_BOLT);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, stop[0].x, stop[0].y, stop[0].z, 12, 0.3, 0.3, 0.3, 0.2);
            if (rank >= 3) {
                LivingEntity next = null;
                double best = SkillRules.BOLT_CHAIN_RANGE * SkillRules.BOLT_CHAIN_RANGE;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SkillRules.BOLT_CHAIN_RANGE),
                        e -> e != target && SkillActions.isFoe(player, e))) {
                    double d = e.distanceToSqr(target);
                    if (d < best) {
                        best = d;
                        next = e;
                    }
                }
                if (next != null) {
                    line(level, stop[0], next.position().add(0, next.getBbHeight() * 0.6, 0), ParticleTypes.ELECTRIC_SPARK, 0.4);
                    SkillActions.hit(player, next, (float) (dmg * SkillRules.BOLT_CHAIN), Skill.THUNDER_BOLT);
                }
            }
        }
        SkillActions.fxTarget = stop[0];
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 熾火の玉
    static String emberOrb(ServerPlayer player, int rank) {
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 from = player.getEyePosition().add(dir.scale(0.8)).subtract(0, 0.2, 0);
        ORBS.add(new Orb(player.serverLevel(), player, spell(player, SkillRules.orbMultiplier(rank)), from, dir));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    private static void tickOrbs(MinecraftServer server) {
        for (Iterator<Orb> it = ORBS.iterator(); it.hasNext();) {
            Orb o = it.next();
            ServerPlayer owner = o.owner;
            if (owner.isRemoved() || owner.level() != o.level) {
                it.remove();
                continue;
            }
            Vec3 next = o.pos.add(o.dir.scale(SkillRules.ORB_SPEED));
            BlockHitResult block = o.level.clip(new ClipContext(o.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            boolean wall = block.getType() != HitResult.Type.MISS;
            Vec3 at = wall ? block.getLocation() : next;
            boolean foe = !o.level.getEntitiesOfClass(LivingEntity.class, new AABB(o.pos, at).inflate(0.6), e -> SkillActions.isFoe(owner, e)).isEmpty();
            o.pos = at;
            o.age++;
            o.level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 3, 0.12, 0.12, 0.12, 0.01);
            o.level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (wall || foe || o.age >= SkillRules.ORB_LIFE) {
                burst(owner, o);
                it.remove();
            }
        }
    }

    private static void burst(ServerPlayer owner, Orb o) {
        ServerLevel level = o.level;
        Vec3 c = o.pos;
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, 0.8, 0.5, 0.8, 0.06);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        double r = SkillRules.ORB_RADIUS;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> SkillActions.isFoe(owner, e))) {
            if (e.distanceToSqr(c) - e.getBbWidth() <= r * r) {
                SkillActions.hit(owner, e, o.damage, Skill.EMBER_ORB);
                e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), SkillRules.ORB_BURN));
            }
        }
    }

    // ---------------------------------------------------------------- 凍て潮
    static String frostTide(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        double r = SkillRules.frostRadius(rank);
        float dmg = spell(player, SkillRules.frostMultiplier(rank));
        for (LivingEntity e : SkillActions.foesAround(player, r, 1.0, 2.0)) {
            SkillActions.hit(player, e, dmg, Skill.FROST_TIDE);
            SkillEffects.slow(e, SkillRules.FROST_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.frostTicks(rank));
            level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        }
        for (double rr = 1.5; rr <= r; rr += 1.5) {
            ring(level, player.position(), rr, ParticleTypes.SNOWFLAKE, (int) (rr * 8));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8f, 0.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6f, 0.7f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 転移
    static String blink(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 start = player.position();
        Vec3 eye = player.getEyePosition();
        double range = SkillRules.blinkRange(rank);
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = block.getType() == HitResult.Type.MISS ? range : Math.max(0, block.getLocation().distanceTo(eye) - 0.6);
        Vec3 dest = null;
        // 壁の手前から戻りながら、体が収まる所 (足もとが半ブロック上でもよい) を探す
        for (double d = reach; d >= 1.5 && dest == null; d -= 0.5) {
            Vec3 feet = start.add(dir.scale(d));
            for (double up : new double[] {0, 0.6, 1.1}) {
                Vec3 q = feet.add(0, up, 0);
                if (level.noCollision(player, player.getBoundingBox().move(q.subtract(start)))) {
                    dest = q;
                    break;
                }
            }
        }
        if (dest == null) {
            return "message.skyabyss.skill.blink_blocked";
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y + 1, start.z, 24, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.5f);
        player.moveTo(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.teleportTo(dest.x, dest.y, dest.z);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1, dest.z, 16, 0.3, 0.6, 0.3, 0.03);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);
        SkillActions.fxTarget = dest;
        return null;
    }

    // ---------------------------------------------------------------- 炉の踏み (鍛冶師)
    static String forgeStomp(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        float dmg = SkillActions.attackDamage(player) * (float) SkillRules.stompMultiplier(rank);
        for (LivingEntity e : SkillActions.foesAround(player, SkillRules.STOMP_RADIUS, 1.0, 2.0)) {
            SkillActions.hit(player, e, dmg, Skill.FORGE_STOMP);
            e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), SkillRules.STOMP_BURN));
            if (!SkillActions.isLarge(e)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                e.hurtMarked = true;
            }
        }
        for (double rr = 1.0; rr <= SkillRules.STOMP_RADIUS; rr += 1.0) {
            ring(level, player.position(), rr, ParticleTypes.FLAME, (int) (rr * 9));
        }
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.6, 0.1, 0.6, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.7f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 霧隠れ (暗殺者)
    static String mistVeil(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        int ticks = SkillRules.veilTicks(rank);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        SkillEffects.timedMod(player, Attributes.MOVEMENT_SPEED, "mist_veil", SkillRules.VEIL_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
        VEIL.put(player.getUUID(), level.getGameTime() + ticks);
        for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SkillRules.VEIL_LOSE_RANGE), m -> m.getTarget() == player)) {
            m.setTarget(null);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.2f);
        return null;
    }

    static boolean veiled(ServerPlayer player) {
        Long until = VEIL.get(player.getUUID());
        return until != null && until >= player.level().getGameTime();
    }

    // 消えている間の最初の一撃 (手で殴る) は +50%。当てると姿が戻る
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer p && event.getSource().getEntity() == p && veiled(p)
                && SkillActions.dealing() == null) {
            event.setAmount(event.getAmount() * (float) SkillRules.VEIL_STRIKE);
            VEIL.remove(p.getUUID());
            p.removeEffect(MobEffects.INVISIBILITY);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, event.getEntity().getX(), event.getEntity().getY() + 1, event.getEntity().getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    // ---------------------------------------------------------------- 呪いの印 (呪術師)
    static String curseMark(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3[] stop = new Vec3[1];
        LivingEntity target = aimed(player, SkillRules.CURSE_RANGE, stop);
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        int ticks = SkillRules.curseTicks(rank);
        SkillEvents.mark(target, SkillRules.curseBonus(rank), level.getGameTime(), ticks, player);
        SkillEffects.timedMod(target, Attributes.ATTACK_DAMAGE, "curse_mark", -SkillRules.CURSE_WEAKEN, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
        line(level, player.getEyePosition().subtract(0, 0.3, 0), stop[0], ParticleTypes.WITCH, 0.6);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 8, 0.3, 0.2, 0.3, 0.01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.3f);
        SkillActions.fxTarget = stop[0];
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 灯の守り札 (呪術師)
    static String lampCharm(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        SkillEffects.zone(SkillEffects.ZoneKind.LAMP, player, at, SkillRules.LAMP_RADIUS, SkillRules.lampTicks(rank), rank);
        lampPulse(player, at, rank);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // 灯のそばの自分と仲間の吸収を、段の量まで満たす (SkillEffects の LAMP の場が 2 秒ごとに呼ぶ)
    static void lampPulse(ServerPlayer owner, Vec3 center, int rank) {
        float amount = (float) (SkillRules.lampAbsorb(rank) * SkillEffects.supportPower(owner, Skill.LAMP_CHARM));
        List<ServerPlayer> near = new ArrayList<>();
        double r2 = SkillRules.LAMP_RADIUS * SkillRules.LAMP_RADIUS;
        if (owner.isAlive() && owner.position().distanceToSqr(center) <= r2) {
            near.add(owner);
        }
        for (ServerPlayer p : SkillEffects.allies(owner, 64)) {
            if (p.position().distanceToSqr(center) <= r2) {
                near.add(p);
            }
        }
        for (ServerPlayer p : near) {
            SkillEffects.absorb(p, amount, SkillRules.LAMP_PULSE + 40, false);
        }
        ServerLevel level = owner.serverLevel();
        level.sendParticles(ParticleTypes.GLOW, center.x, center.y + 0.8, center.z, 6, 0.2, 0.3, 0.2, 0.01);
        ring(level, center, SkillRules.LAMP_RADIUS, ParticleTypes.END_ROD, 20);
    }

    // ---------------------------------------------------------------- 毎 tick (SkillActions.tick から)
    public static void tick(MinecraftServer server) {
        tickOrbs(server);
        if (server.getTickCount() % 20 == 0) {
            long now = server.overworld().getGameTime();
            VEIL.values().removeIf(until -> until < now);
        }
    }

    public static void clear() {
        ORBS.clear();
        VEIL.clear();
    }

    // 自己テスト用
    static int orbsInFlight() {
        return ORBS.size();
    }
}
