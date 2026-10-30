package jp.revati.skyabyss.item.wild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BossFooting;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.wild.WildEffect.Fx;
import jp.revati.skyabyss.item.wild.WildEffect.Kind;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 野の装備の武器の能力 (docs/design/51 の 4 章。種類ごとの意味と数の並びは WildEffect)。
//  - 発動は、手に持った野の武器 (必要 Lv に届く) で、その人が与えた傷 (近接・技・その弓の矢) で起きる
//  - 能力が出す追い打ちの傷は「打った相手そのものが元」の魔法の傷 (indirectMagic(相手, 人)) にして、ここの発動をもう一度起こさない
//  - 爆ぜる・雷は粒と音だけ (地形を壊さない。2026-10-27 の声「爆発を減らす」)
// 防具の一式は WildArmorEvents
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WildGearEvents {
    static final String P = SkyAbyss.MODID + ":wild_";
    // 相手に付ける印 (persistent data): 期限の tick と量
    static final String MARK = P + "mark", MARK_AMT = P + "mark_amt";
    static final String WEAK = P + "weak", WEAK_AMT = P + "weak_amt";
    static final String HANG = P + "hang", HANG_AMT = P + "hang_amt";
    static final String LINK = P + "link", LINK_TO = P + "link_to", LINK_AMT = P + "link_amt";
    static final String FROST = P + "frost";
    static final String HIT_FULL = P + "touched";
    // 人に付ける状態
    static final String BUFF = P + "buff", BUFF_AMT = P + "buff_amt";
    static final String GUARD = P + "guard", GUARD_AMT = P + "guard_amt";
    static final String MANA = P + "mana", MANA_AMT = P + "mana_amt";
    static final String HYMN = P + "hymn", HYMN_AMT = P + "hymn_amt";
    static final String NEXT = P + "next", NEXT_AMT = P + "next_amt";
    static final String ROOT_CD = P + "root_cd", HOOK_CD = P + "hook_cd";
    static final String HEAT = P + "heat", BEATS = P + "beats", STARS = P + "stars", PRISM = P + "prism";
    static final String RAMP_T = P + "ramp_t", RAMP_N = P + "ramp_n", RAMP_AT = P + "ramp_at";
    static final String HASTE_N = P + "haste_n", HASTE_AT = P + "haste_at";
    static final String MOVE_N = P + "move_n", MOVE_AT = P + "move_at", MOVE_KILL = P + "move_kill";
    static final String OPEN_HASTE = P + "open_haste";
    static final String VANISH = P + "vanish", WARD_AT = P + "ward_at";
    static final String COUNT = P + "count_";
    static final String SHOTS = P + "shots";
    static final String CHARGE = P + "charge";
    static final String ARROW_CHARGE = P + "arrow_charge", ARROW_SHARE = P + "arrow_share", ARROW_CLONE = P + "arrow_clone", ARROW_HOME = P + "arrow_home";
    static final String SNUFFED = P + "snuffed";

    static final ResourceLocation MOD_HASTE = id("haste"), MOD_MOVE = id("move_ramp"), MOD_ARMOR_DOWN = id("armor_down"), MOD_SHRED = id("armor_shred"),
            MOD_TEAM_HP = id("team_health"), MOD_TEAM_MOVE = id("team_move");

    // 能力の傷を流している間 (同じ流れで能力をもう一度起こさない)
    private static boolean inProc;

    // 遅れて当たる傷 (雷・熾り蟲・分身・毒・出血): 当たる tick・相手・打った人・傷・見た目
    record Delayed(long at, int target, int attacker, float damage, int look, boolean nearest) {
    }

    static final int LOOK_BOLT = 0, LOOK_EMBER = 1, LOOK_MIRROR = 2, LOOK_VENOM = 3, LOOK_BLEED = 4;
    private static final List<Delayed> DELAYED = new ArrayList<>();
    // 香の煙: 期限・所・半径・敵を遅くするか・仲間の受ける傷の割合
    record Smoke(long until, Vec3 pos, double radius, double guard, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
    }

    private static final List<Smoke> SMOKES = new ArrayList<>();
    // 曲がる矢
    private static final List<Integer> HOMING = new ArrayList<>();
    // 出血の重ね (相手の id → [重ね, 打った人, 1 重ねの傷, 期限])
    private static final Map<Integer, double[]> BLEEDS = new HashMap<>();

    private WildGearEvents() {
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild/" + path);
    }

    // ---------------------------------------------------------------- 持っている品
    public static WildGearTypes.Weapon held(Player p) {
        ItemStack hand = p.getMainHandItem();
        WildGearTypes.Weapon w = WildGearSetup.weapon(hand);
        return w != null && GearLevel.meets(p, hand) ? w : null;
    }

    static Fx fx(Player p, Kind k) {
        WildGearTypes.Weapon w = held(p);
        return w == null ? null : w.get(k);
    }

    static double val(Player p, Kind k, int i) {
        Fx f = fx(p, k);
        return f == null ? 0 : f.p(i);
    }

    // ---------------------------------------------------------------- 外から呼ぶ口
    // マナの戻りの上乗せ (rpg/skill/SkillManager.regenTick)
    public static double manaRegenBonus(Player p) {
        double b = val(p, Kind.MANA_REGEN, 0);
        Fx weather = fx(p, Kind.WEATHER);
        if (weather != null && !p.level().isRaining()) {
            b += weather.p(1);
        }
        if (p.getPersistentData().getLong(MANA) > p.level().getGameTime()) {
            b += p.getPersistentData().getDouble(MANA_AMT);
        }
        return b + WildArmorEvents.manaRegenBonus(p);
    }

    // 技の待ちの倍率 (rpg/skill/SkillManager.cooldownFor)
    public static double cooldownScale(Player p) {
        return 1 - val(p, Kind.COOLDOWN, 0);
    }

    // 起こす 1 tick の進み (party/Downed)
    public static int reviveStep(Player holder) {
        double m = val(holder, Kind.REVIVE_SPEED, 0);
        return m > 1 ? (int) Math.round(m) : 1;
    }

    // ---------------------------------------------------------------- 傷
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        long now = level.getGameTime();
        double m = 1;
        CompoundTag td = target.getPersistentData();
        // 相手に付いた印 (だれの傷も)
        if (td.getLong(MARK) > now) {
            m *= 1 + td.getDouble(MARK_AMT);
        }
        // 弱く (打った相手の与える傷)
        if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player) && attacker.getPersistentData().getLong(WEAK) > now) {
            m *= 1 - attacker.getPersistentData().getDouble(WEAK_AMT);
        }
        // 受ける側の人: 手の品と仲間の守り
        if (target instanceof Player p) {
            m *= takenFactor(level, p, source, now);
        }
        // 与える側の人
        if (source.getEntity() instanceof Player player && player != target && !inProc) {
            if (source.getDirectEntity() == player) {
                m *= onHit(level, player, target, event.getAmount() * (float) m, now);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && WildGearSetup.weapon(arrow.getWeaponItem()) != null) {
                m *= onArrow(level, player, arrow, target, event.getAmount() * (float) m, now);
            }
            m *= auraDamage(level, player, now);
            m *= WildArmorEvents.damageFactor(player, target, now);
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 受ける傷の倍率 (人)
    static double takenFactor(ServerLevel level, Player p, DamageSource source, long now) {
        double m = 1;
        WildGearTypes.Weapon w = held(p);
        if (w != null) {
            if (source.is(DamageTypeTags.IS_FIRE)) {
                m *= 1 + val(p, Kind.FIRE_TAKEN, 0);
            }
            if (source.is(DamageTypeTags.IS_FALL)) {
                m *= Math.max(0, 1 + val(p, Kind.FALL_TAKEN, 0));
            }
            Fx protect = w.get(Kind.PROTECT);
            if (protect != null) {
                m *= 1 - protect.p(1);
            }
        }
        if (p.getPersistentData().getLong(GUARD) > now) {
            m *= 1 - p.getPersistentData().getDouble(GUARD_AMT);
        }
        if (p.getPersistentData().getLong(HYMN) > now) {
            m *= 1 - p.getPersistentData().getDouble(HYMN_AMT);
        }
        // 常に効く守り (まわりの仲間の品)
        double aura = 0;
        for (Player ally : allies(level, p, 8)) {
            Fx f = fx(ally, Kind.TEAM_GUARD_AURA);
            if (f != null && ally.distanceTo(p) <= f.p(1)) {
                aura = Math.max(aura, f.p(0));
            }
        }
        m *= 1 - aura;
        // 香の煙の中
        for (Smoke s : SMOKES) {
            if (s.until() > now && s.dim() == level.dimension() && p.position().distanceTo(s.pos()) <= s.radius()) {
                m *= 1 - s.guard();
                break;
            }
        }
        return m * WildArmorEvents.takenFactor(level, p, source, now);
    }

    // 近接 (と技) の一撃。与える傷の倍率を返す
    static double onHit(ServerLevel level, Player player, LivingEntity target, float amount, long now) {
        WildGearTypes.Weapon w = held(player);
        if (w == null || w.isBow()) {
            return 1;
        }
        double m = 1;
        CompoundTag pd = player.getPersistentData();
        CompoundTag td = target.getPersistentData();
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean boss = BossFooting.planted(target);
        boolean back = fromBehind(target, player.position());
        boolean full = !td.getBoolean(HIT_FULL) && target.getHealth() >= target.getMaxHealth() - 0.01f;
        td.putBoolean(HIT_FULL, true);
        // 次の一撃の上乗せ (鉤・釣り針・鼓動・姿消し)
        if (pd.getLong(NEXT) > now) {
            m *= 1 + pd.getDouble(NEXT_AMT);
            pd.remove(NEXT);
        }
        for (Fx f : w.fx()) {
            switch (f.kind()) {
                case SPRINT_BONUS -> m *= player.isSprinting() ? 1 + f.p(0) : 1;
                case SLAYER_BEAST -> m *= beast(target) ? 1 + f.p(0) : 1;
                case BACKSTAB -> m *= back ? 1 + f.p(0) : 1;
                case FIRST_STRIKE -> m *= full ? 1 + f.p(0) : 1;
                case EXECUTE -> m *= target.getHealth() < target.getMaxHealth() * f.p(0) ? 1 + f.p(1) : 1;
                case BERSERK -> m *= player.getHealth() < player.getMaxHealth() * f.p(0) ? 1 + f.p(1) : 1;
                case DARK_BONUS -> m *= level.getMaxLocalRawBrightness(player.blockPosition()) <= f.p(0) ? 1 + f.p(1) : 1;
                case FAR_BONUS -> m *= player.distanceTo(target) > f.p(0) ? 1 + f.p(1) : 1;
                case AIR_BONUS -> m *= !player.onGround() && !player.isInWater() ? 1 + f.p(0) : 1;
                case WEATHER -> m *= level.isRaining() ? 1 + f.p(0) : 1;
                case CRIT -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        m *= 1 + f.p(1);
                        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                    }
                }
                case ARMOR_PIERCE -> m *= 1 + f.p(0) * armorShare(target, amount);
                case OPENER -> m *= target.getHealth() > target.getMaxHealth() * f.p(0) ? 1 + f.p(1) : 1;
                case BURN_BONUS -> {
                    m *= target.isOnFire() ? 1 + f.p(1) : 1;
                    target.igniteForTicks(f.ticks(0));
                }
                case SLOW_BONUS -> {
                    m *= target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? 1 + f.p(2) : 1;
                    effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(0), (int) f.p(1));
                }
                case RAMP -> {
                    int n = pd.getInt(RAMP_T) == target.getId() && now - pd.getLong(RAMP_AT) <= f.p(2) ? pd.getInt(RAMP_N) + 1 : 0;
                    pd.putInt(RAMP_T, target.getId());
                    pd.putInt(RAMP_N, n);
                    pd.putLong(RAMP_AT, now);
                    m *= 1 + Math.min(f.p(1), f.p(0) * n);
                }
                case POISON -> effect(target, MobEffects.POISON, f.ticks(0), (int) f.p(1));
                case SLOW -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(0), (int) f.p(1));
                case WEAKEN -> {
                    td.putLong(WEAK, now + f.ticks(0));
                    td.putDouble(WEAK_AMT, f.p(1));
                    level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.8), target.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
                }
                case ARMOR_SHRED -> shred(target, f.p(0), f.p(1), f.ticks(2), now);
                case ARMOR_DOWN -> armorDown(target, f.p(0), f.ticks(1), now);
                case MARK -> mark(level, target, f.p(0), f.ticks(1), now);
                case FREEZE_STACK -> {
                    int n = td.getInt(FROST) + 1;
                    if (n >= f.p(0)) {
                        n = 0;
                        effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(1), 2);
                        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 16, 0.4, 0.4, 0.4, 0.05);
                    } else {
                        effect(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
                    }
                    td.putInt(FROST, n);
                }
                case ROOT -> {
                    if (pd.getLong(ROOT_CD) <= now) {
                        pd.putLong(ROOT_CD, now + f.ticks(1));
                        effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(0), boss ? 1 : 6);
                        level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY(0.2), target.getZ(), 16, 0.4, 0.1, 0.4, 0.01);
                    }
                }
                case LINK -> link(level, target, f.ticks(0), f.p(1), f.p(2), now);
                case HOOK_PULL -> {
                    if (pd.getLong(HOOK_CD) <= now) {
                        pd.putLong(HOOK_CD, now + f.ticks(0));
                        pull(player, target, 2.5, boss);
                        next(pd, f.p(1), now);
                    }
                }
                case HOOK -> {
                    pull(player, target, f.p(0), boss);
                    next(pd, f.p(1), now);
                }
                case PULL_AOE -> {
                    pull(player, target, f.p(0), boss);
                    area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), target, ParticleTypes.SQUID_INK);
                }
                case STUN_CHANCE -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(1), boss ? 1 : 6);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.1);
                    }
                }
                case SNUFF -> {
                    if (back) {
                        m *= 1 + f.p(0);
                        snuff(level, target, boss ? f.ticks(2) : f.ticks(1), now);
                    }
                }
                case CLEAVE -> area(level, player, target.position(), f.p(1), (float) (amount * f.p(0)), target, ParticleTypes.SWEEP_ATTACK);
                case PIERCE_LINE -> line(level, player, target, 3.0, 1, (float) (amount * f.p(0)), 0);
                case CHAIN -> chain(level, player, target, (float) (amount * f.p(0)), (int) f.p(1), f.p(2));
                case EVERY_N_BOLT -> {
                    if (count(pd, w, (int) f.p(0))) {
                        bolt(level, target.position());
                        area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), null, ParticleTypes.ELECTRIC_SPARK);
                    }
                }
                case EVERY_N_BITE -> {
                    if (count(pd, w, (int) f.p(0))) {
                        proc(level, player, target, (float) (attack * f.p(1)), ParticleTypes.LAVA);
                        target.igniteForTicks(f.ticks(2));
                        sound(level, target, SoundEvents.EVOKER_FANGS_ATTACK, 0.8f);
                    }
                }
                case EVERY_N_QUAKE -> {
                    if (count(pd, w, (int) f.p(0))) {
                        area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), target, ParticleTypes.EXPLOSION);
                        sound(level, target, SoundEvents.ANVIL_LAND, 0.5f);
                    }
                }
                case EVERY_N_ASHRAIN -> {
                    if (count(pd, w, (int) f.p(0))) {
                        for (LivingEntity e : foes(level, player, target.position(), f.p(2))) {
                            proc(level, player, e, (float) (attack * f.p(1)), ParticleTypes.WHITE_ASH);
                            effect(e, MobEffects.BLINDNESS, f.ticks(3), 0);
                            e.getPersistentData().putLong(WEAK, now + f.ticks(3));
                            e.getPersistentData().putDouble(WEAK_AMT, 0.15);
                        }
                        level.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + 3, target.getZ(), 60, 2.5, 0.5, 2.5, 0.02);
                    }
                }
                case EVERY_N_SPIT -> {
                    if (count(pd, w, (int) f.p(0))) {
                        cone(level, player, f.p(2), (float) (attack * f.p(1)));
                    }
                }
                case EVERY_N_SPARKS -> {
                    if (count(pd, w, (int) f.p(0))) {
                        for (LivingEntity e : foes(level, player, target.position(), f.p(2))) {
                            proc(level, player, e, (float) (attack * f.p(1)), ParticleTypes.FIREWORK);
                            e.igniteForTicks(f.ticks(3));
                        }
                        sound(level, target, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f);
                    }
                }
                case HEAT_STACK -> {
                    int n = pd.getInt(HEAT) + 1;
                    if (n > f.p(0)) {
                        n = 0;
                        area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), null, ParticleTypes.FLAME);
                        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                        sound(level, target, SoundEvents.FIRECHARGE_USE, 1.0f);
                    } else {
                        level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY(0.6), player.getZ(), n * 2, 0.3, 0.3, 0.3, 0.01);
                    }
                    pd.putInt(HEAT, n);
                }
                case DELAYED_BOLT -> {
                    later(now + f.ticks(0), target, player, (float) (attack * f.p(1)), LOOK_BOLT, false);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(1.0), target.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                }
                case EMBERBUG -> later(now + f.ticks(1), target, player, (float) (attack * f.p(0)), LOOK_EMBER, true);
                case FEATHERS -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        for (int i = 0; i < (int) f.p(1); i++) {
                            Vec3 at = target.position().add((player.getRandom().nextDouble() - 0.5) * f.p(3), 0, (player.getRandom().nextDouble() - 0.5) * f.p(3));
                            area(level, player, at, f.p(3) * 0.6, (float) (attack * f.p(2)), null, ParticleTypes.ELECTRIC_SPARK);
                        }
                        bolt(level, target.position());
                    }
                }
                case PROC_BOLT -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        bolt(level, target.position());
                        proc(level, player, target, (float) (attack * f.p(1)), ParticleTypes.ELECTRIC_SPARK);
                    }
                }
                case BACK_THRUST -> {
                    if (count(pd, w, (int) f.p(0))) {
                        Vec3 behind = player.position().subtract(player.getLookAngle().multiply(1.6, 0, 1.6));
                        area(level, player, behind, f.p(2), (float) (attack * f.p(1)), null, ParticleTypes.CRIT);
                    }
                }
                case WIND_SLASH -> windSlash(level, player, f.p(0), (float) (attack * f.p(1)), target);
                case CONSTELLATION -> {
                    int n = pd.getInt(STARS) + 1;
                    level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 2.4, target.getZ(), 3, 0.5, 0.1, 0.5, 0.0);
                    if (n >= f.p(0)) {
                        n = 0;
                        area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), null, ParticleTypes.END_ROD);
                        level.sendParticles(ParticleTypes.FIREWORK, target.getX(), target.getY() + 2, target.getZ(), 40, 2, 1, 2, 0.05);
                        sound(level, target, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f);
                    }
                    pd.putInt(STARS, n);
                }
                case PRISM -> m *= prism(level, player, target, pd, (int) f.p(0), now);
                case BACK_VENOM -> {
                    if (back) {
                        dot(level, player, target, (float) (attack * f.p(0)), f.ticks(1));
                        level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                    }
                }
                case MIRROR -> {
                    if (back) {
                        later(now + f.ticks(0), target, player, (float) (amount * m * f.p(1)), LOOK_MIRROR, false);
                    }
                }
                case BLEED_STACK -> bleedStack(target, player, (int) f.p(0), (float) f.p(1), now);
                case LIFESTEAL -> player.heal((float) (amount * f.p(0)));
                case HEARTBEAT -> {
                    if (player.getHealth() < player.getMaxHealth()) {
                        player.heal((float) (amount * f.p(0)));
                    } else {
                        int n = pd.getInt(BEATS) + 1;
                        if (n >= f.p(1)) {
                            n = 0;
                            next(pd, 1.0, now);
                            level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.1, player.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
                            sound(level, player, SoundEvents.WARDEN_HEARTBEAT, 1.0f);
                        }
                        pd.putInt(BEATS, n);
                    }
                }
                case LEECH_LOWEST -> {
                    Player low = player;
                    for (Player ally : allies(level, player, f.p(1))) {
                        if (ally.getHealth() / ally.getMaxHealth() < low.getHealth() / low.getMaxHealth()) {
                            low = ally;
                        }
                    }
                    low.heal((float) (amount * f.p(0)));
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, low.getX(), low.getY() + 1.2, low.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                }
                case WARD_STACK -> {
                    if (player.getAbsorptionAmount() > 0) {
                        m *= 1 + f.p(2);
                    }
                    player.setAbsorptionAmount((float) Math.min(f.p(0), player.getAbsorptionAmount() + 1));
                    pd.putLong(WARD_AT, now + f.ticks(1));
                }
                case HASTE_RAMP -> {
                    int n = now - pd.getLong(HASTE_AT) <= f.p(2) ? pd.getInt(HASTE_N) + 1 : 0;
                    pd.putInt(HASTE_N, n);
                    pd.putLong(HASTE_AT, now);
                    modifier(player, Attributes.ATTACK_SPEED, MOD_HASTE, Math.min(f.p(1), f.p(0) * n), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                }
                case MOVE_RAMP -> {
                    int n = now - pd.getLong(MOVE_AT) <= f.p(2) ? pd.getInt(MOVE_N) + 1 : 1;
                    pd.putInt(MOVE_N, n);
                    pd.putLong(MOVE_AT, now);
                    modifier(player, Attributes.MOVEMENT_SPEED, MOD_MOVE, Math.min(f.p(1), f.p(0) * n), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                }
                case VANISH -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        pd.putLong(VANISH, now + f.ticks(1));
                        next(pd, f.p(2), now);
                        effect(player, MobEffects.INVISIBILITY, f.ticks(1), 0);
                        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), q -> q.getTarget() == player)) {
                            mob.setTarget(null);
                        }
                        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(0.5), player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
                    }
                }
                case PLUNGE -> {
                    if (player.fallDistance > 1.0f && !player.onGround()) {
                        m *= 1 + f.p(0);
                        area(level, player, target.position(), f.p(2), (float) (attack * f.p(1)), target, ParticleTypes.EXPLOSION);
                        sound(level, target, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f);
                    }
                }
                case TEAM_HEAL_ON_HIT -> {
                    player.heal((float) f.p(0));
                    for (Player ally : allies(level, player, f.p(1))) {
                        ally.heal((float) f.p(0));
                    }
                }
                case TEAM_HASTE -> {
                    for (Player ally : withSelf(level, player, f.p(3))) {
                        buff(ally, BUFF, BUFF_AMT, f.p(1), now + f.ticks(2));
                        effect(ally, MobEffects.MOVEMENT_SPEED, f.ticks(2), 0);
                    }
                }
                case TEAM_GUARD -> {
                    for (Player ally : withSelf(level, player, f.p(2))) {
                        buff(ally, GUARD, GUARD_AMT, f.p(0), now + f.ticks(1));
                    }
                }
                case TEAM_REGEN -> {
                    for (Player ally : withSelf(level, player, f.p(2))) {
                        effect(ally, MobEffects.REGENERATION, f.ticks(0), (int) f.p(1));
                    }
                }
                case TEAM_MANA -> {
                    for (Player ally : withSelf(level, player, f.p(2))) {
                        buff(ally, MANA, MANA_AMT, f.p(0), now + f.ticks(1));
                    }
                }
                case DRUM -> {
                    if (count(pd, w, (int) f.p(0))) {
                        for (LivingEntity e : foes(level, player, player.position(), f.p(1))) {
                            effect(e, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(2), BossFooting.planted(e) ? 1 : 5);
                        }
                        for (Player ally : withSelf(level, player, f.p(1))) {
                            ally.heal((float) f.p(3));
                        }
                        sound(level, player, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2.0f);
                        level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 2.2, player.getZ(), 8, 1.0, 0.3, 1.0, 0.5);
                    }
                }
                case LULLABY -> {
                    if (count(pd, w, (int) f.p(0))) {
                        for (LivingEntity e : foes(level, player, player.position(), f.p(1))) {
                            boolean b = BossFooting.planted(e);
                            effect(e, MobEffects.MOVEMENT_SLOWDOWN, b ? f.ticks(3) : f.ticks(2), b ? 2 : 7);
                            if (!b && e instanceof Mob mob) {
                                mob.setTarget(null);
                            }
                            level.sendParticles(ParticleTypes.NOTE, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 2, 0.2, 0.1, 0.2, 0.5);
                        }
                        for (Player ally : withSelf(level, player, f.p(1))) {
                            ally.heal((float) f.p(4));
                        }
                        sound(level, player, SoundEvents.BELL_RESONATE, 0.6f);
                    }
                }
                case STAR_FRUIT -> {
                    if (count(pd, w, (int) f.p(0))) {
                        for (Player ally : withSelf(level, player, f.p(4))) {
                            ally.heal((float) f.p(1));
                            buff(ally, BUFF, BUFF_AMT, f.p(2), now + f.ticks(3));
                            level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 2, ally.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
                        }
                        sound(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f);
                    }
                }
                case HYMN -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        for (Player ally : withSelf(level, player, f.p(3))) {
                            buff(ally, HYMN, HYMN_AMT, f.p(1), now + f.ticks(2));
                            effect(ally, MobEffects.REGENERATION, f.ticks(2), 1);
                        }
                        sound(level, player, SoundEvents.BELL_BLOCK, 1.6f);
                        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.5, player.getZ(), 20, 1.5, 0.3, 1.5, 0.02);
                    }
                }
                case SMOKE -> {
                    SMOKES.add(new Smoke(now + f.ticks(0), player.position(), f.p(1), f.p(2), level.dimension()));
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 6, f.p(1) * 0.4, 0.1, f.p(1) * 0.4, 0.01);
                }
                default -> {
                }
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- 弓
    @SubscribeEvent
    static void onLoose(ArrowLooseEvent event) {
        Player p = event.getEntity();
        if (WildGearSetup.weapon(event.getBow()) != null) {
            p.getPersistentData().putInt(CHARGE, event.getCharge());
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof Player p)) {
            return;
        }
        CompoundTag ad = arrow.getPersistentData();
        if (ad.getBoolean(ARROW_CLONE) || ad.contains(ARROW_CHARGE)) {
            return;
        }
        WildGearTypes.Weapon w = WildGearSetup.weapon(arrow.getWeaponItem());
        if (w == null || !GearLevel.meets(p, arrow.getWeaponItem())) {
            return;
        }
        int charge = p.getPersistentData().getInt(CHARGE);
        ad.putInt(ARROW_CHARGE, charge);
        boolean airborne = !p.onGround() && !p.isInWater();
        Fx speed = w.get(Kind.ARROW_SPEED);
        if (speed != null) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + speed.p(0)));
        }
        Fx home = w.get(Kind.HOMING);
        if (home != null) {
            ad.putBoolean(ARROW_HOME, true);
            HOMING.add(arrow.getId());
        }
        Fx beam = w.get(Kind.BEAM);
        if (beam != null && charge >= 20 + beam.p(0)) {
            event.setCanceled(true);
            beam(level, p, arrow.getBaseDamage() * arrow.getDeltaMovement().length(), beam.p(1), beam.p(2));
            return;
        }
        Fx split = w.get(Kind.SPLIT_EVERY);
        if (split != null) {
            int n = p.getPersistentData().getInt(SHOTS) + 1;
            if (n >= split.p(0)) {
                n = 0;
                clones(level, arrow, (int) split.p(1) - 1, split.p(2), 9);
            }
            p.getPersistentData().putInt(SHOTS, n);
        }
        Fx air = w.get(Kind.AIR_SPLIT);
        if (air != null && airborne) {
            clones(level, arrow, (int) air.p(0) - 1, air.p(1), 8);
            ad.putDouble(ARROW_SHARE, 1 + air.p(2));
        }
    }

    // 矢が当たった。与える傷の倍率を返す
    static double onArrow(ServerLevel level, Player player, AbstractArrow arrow, LivingEntity target, float amount, long now) {
        WildGearTypes.Weapon w = WildGearSetup.weapon(arrow.getWeaponItem());
        if (w == null || !GearLevel.meets(player, arrow.getWeaponItem())) {
            return 1;
        }
        CompoundTag ad = arrow.getPersistentData();
        boolean full = ad.getInt(ARROW_CHARGE) >= 20 || arrow.isCritArrow();
        boolean boss = BossFooting.planted(target);
        double m = ad.contains(ARROW_SHARE) ? ad.getDouble(ARROW_SHARE) : 1;
        if (target.getPersistentData().getLong(HANG) > now) {
            m *= 1 + target.getPersistentData().getDouble(HANG_AMT);
        }
        if (ad.getBoolean(ARROW_CLONE)) {
            return m;
        }
        for (Fx f : w.fx()) {
            switch (f.kind()) {
                case BLEED -> dot(level, player, target, (float) (amount * f.p(0)), f.ticks(1));
                case PIERCE -> line(level, player, target, 8.0, (int) f.p(0), (float) (amount * m), f.p(1));
                case FAR_BONUS -> m *= player.distanceTo(target) > f.p(0) ? 1 + f.p(1) : 1;
                case RICOCHET -> chain(level, player, target, (float) (amount * f.p(0)), 1, f.p(1));
                case PULL -> {
                    pull(player, target, f.p(0), boss);
                    effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(1), (int) f.p(2) + 1);
                }
                case ARROW_BURST -> {
                    if (full) {
                        for (LivingEntity e : foes(level, player, target.position(), f.p(1))) {
                            if (e != target) {
                                proc(level, player, e, (float) (amount * f.p(0)), ParticleTypes.FLAME);
                            }
                            e.igniteForTicks(f.ticks(2));
                        }
                        level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.5), target.getZ(), 10, 0.6, 0.4, 0.6, 0.1);
                        sound(level, target, SoundEvents.FIRECHARGE_USE, 0.9f);
                    }
                }
                case HANG -> {
                    if (full) {
                        if (boss) {
                            effect(target, MobEffects.MOVEMENT_SLOWDOWN, f.ticks(0), 2);
                        } else {
                            effect(target, MobEffects.LEVITATION, f.ticks(0), 1);
                        }
                        target.getPersistentData().putLong(HANG, now + f.ticks(0) + 10);
                        target.getPersistentData().putDouble(HANG_AMT, f.p(1));
                        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(1.0) + 0.6, target.getZ(), 8, 0.1, 0.8, 0.1, 0.01);
                    }
                }
                case MARK -> mark(level, target, f.p(0), f.ticks(1), now);
                case PROC_BOLT -> {
                    if (player.getRandom().nextDouble() < f.p(0)) {
                        bolt(level, target.position());
                        later(now + 2, target, player, (float) (amount * f.p(1)), LOOK_BOLT, false);
                    }
                }
                case HOLD_BONUS -> m *= ad.getInt(ARROW_CHARGE) >= 20 + f.p(0) ? 1 + f.p(1) : 1;
                case HOMING -> m *= 1 + f.p(1);
                default -> {
                }
            }
        }
        return m;
    }

    // 光の矢: 目の向きの直線 length の敵みなに
    static void beam(ServerLevel level, Player p, double base, double length, double mult) {
        Vec3 from = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        float dmg = (float) (Math.max(6, base) * mult);
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (double d = 1; d <= length; d += 0.5) {
            Vec3 at = from.add(dir.scale(d));
            if (!level.getBlockState(net.minecraft.core.BlockPos.containing(at)).getCollisionShape(level, net.minecraft.core.BlockPos.containing(at)).isEmpty()) {
                break;
            }
            if (((int) (d * 2)) % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(0.8), q -> foe(q, p))) {
                if (hit.add(e)) {
                    proc(level, p, e, dmg, ParticleTypes.FLASH);
                }
            }
        }
        sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1.6f);
    }

    // 分かれる矢 (元の矢と同じ種類・同じ傷の share)
    static void clones(ServerLevel level, AbstractArrow arrow, int count, double share, double spread) {
        Vec3 v = arrow.getDeltaMovement();
        for (int i = 0; i < count; i++) {
            double yaw = (i % 2 == 0 ? 1 : -1) * spread * (1 + i / 2);
            Entity e = arrow.getType().create(level);
            if (!(e instanceof AbstractArrow c)) {
                return;
            }
            c.setOwner(arrow.getOwner());
            c.moveTo(arrow.getX(), arrow.getY(), arrow.getZ(), arrow.getYRot(), arrow.getXRot());
            c.setDeltaMovement(v.yRot((float) Math.toRadians(yaw)));
            c.setBaseDamage(arrow.getBaseDamage() * share);
            c.setCritArrow(arrow.isCritArrow());
            c.pickup = AbstractArrow.Pickup.DISALLOWED;
            c.getPersistentData().putBoolean(ARROW_CLONE, true);
            level.addFreshEntity(c);
        }
    }

    // ---------------------------------------------------------------- 倒したとき
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof Player player) || dead instanceof Player) {
            return;
        }
        BLEEDS.remove(dead.getId());
        WildGearTypes.Weapon w = held(player);
        if (w == null) {
            return;
        }
        long now = level.getGameTime();
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (Fx f : w.fx()) {
            switch (f.kind()) {
                case KILL_WARD -> {
                    player.setAbsorptionAmount((float) Math.max(player.getAbsorptionAmount(), f.p(0)));
                    player.getPersistentData().putLong(WARD_AT, now + f.ticks(1));
                    level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY(0.6), player.getZ(), 20, 0.5, 0.6, 0.5, 0.02);
                }
                case KILL_BLAST -> {
                    area(level, player, dead.position(), f.p(1), (float) (attack * f.p(0)), dead, ParticleTypes.EXPLOSION);
                    sound(level, dead, SoundEvents.GENERIC_EXPLODE.value(), 0.6f);
                }
                case MOVE_RAMP -> {
                    player.getPersistentData().putLong(MOVE_KILL, now + 60);
                    modifier(player, Attributes.MOVEMENT_SPEED, MOD_MOVE, f.p(3), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    player.getPersistentData().putLong(MOVE_AT, now);
                }
                case OPENER -> {
                    player.getPersistentData().putLong(OPEN_HASTE, now + f.ticks(2));
                    modifier(player, Attributes.ATTACK_SPEED, MOD_HASTE, f.p(3), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
                }
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- 結ぶ・肩代わり (傷が決まったあと)
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || inProc || event.getNewDamage() <= 0) {
            return;
        }
        long now = level.getGameTime();
        CompoundTag td = target.getPersistentData();
        if (td.getLong(LINK) > now && event.getSource().getEntity() instanceof Player p) {
            Entity other = level.getEntity(td.getInt(LINK_TO));
            if (other instanceof LivingEntity o && o.isAlive()) {
                proc(level, p, o, (float) (event.getNewDamage() * td.getDouble(LINK_AMT)), ParticleTypes.ENCHANT);
            }
        }
        // 身代わりの人形柱: まわりの仲間の受けた傷の一部を持ち主へ移す (仲間を癒やし、持ち主が受ける)
        if (target instanceof Player hurt) {
            for (Player keeper : allies(level, hurt, 8)) {
                Fx f = fx(keeper, Kind.PROTECT);
                if (f != null && keeper.distanceTo(hurt) <= f.p(2)) {
                    float share = (float) (event.getNewDamage() * f.p(0));
                    hurt.heal(share);
                    inProc = true;
                    try {
                        keeper.hurt(keeper.damageSources().magic(), share);
                    } finally {
                        inProc = false;
                    }
                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, keeper.getX(), keeper.getY(0.8), keeper.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
                    break;
                }
            }
            // 鏡写し: 受けた傷の一部を返す
            Fx reflect = fx(hurt, Kind.REFLECT);
            if (reflect != null && event.getSource().getEntity() instanceof LivingEntity a && a != hurt && !(a instanceof Player)) {
                proc(level, hurt, a, (float) (event.getNewDamage() * reflect.p(0)), ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    // ---------------------------------------------------------------- 気づかれにくい
    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof Player p) || !(event.getEntity() instanceof Mob mob) || BossFooting.planted(mob)) {
            return;
        }
        double cut = val(p, Kind.STEALTH, 0);
        cut = Math.max(cut, WildArmorEvents.stealth(p));
        if (p.getPersistentData().getLong(VANISH) > p.level().getGameTime()) {
            event.setCanceled(true);
            return;
        }
        if (cut > 0 && mob.getTarget() != p) {
            double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * (1 - cut);
            if (mob.distanceTo(p) > Math.max(3, range)) {
                event.setCanceled(true);
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        CompoundTag pd = player.getPersistentData();
        // 続けて打つ速さ・移動の上乗せが切れたら戻す
        if (player.tickCount % 5 == 0) {
            Fx haste = fx(player, Kind.HASTE_RAMP);
            boolean hasteOn = haste != null && now - pd.getLong(HASTE_AT) <= haste.p(2) || pd.getLong(OPEN_HASTE) > now;
            if (!hasteOn) {
                modifier(player, Attributes.ATTACK_SPEED, MOD_HASTE, 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            Fx move = fx(player, Kind.MOVE_RAMP);
            boolean moveOn = move != null && (now - pd.getLong(MOVE_AT) <= move.p(2) || pd.getLong(MOVE_KILL) > now);
            if (!moveOn) {
                modifier(player, Attributes.MOVEMENT_SPEED, MOD_MOVE, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
            // 膜の期限 (隕鉄・葬り鎚)
            if (pd.contains(WARD_AT) && pd.getLong(WARD_AT) <= now) {
                pd.remove(WARD_AT);
                if (!WildArmorEvents.cloudWard(player)) {
                    player.setAbsorptionAmount(0);
                }
            }
        }
        // 仲間の体力の上限 (星鈴の木)
        if (player.tickCount % 20 == 11) {
            double hp = 0;
            for (Player ally : withSelf(player.serverLevel(), player, 8)) {
                Fx f = fx(ally, Kind.TEAM_HEALTH_AURA);
                if (f != null && ally.distanceTo(player) <= f.p(1)) {
                    hp = Math.max(hp, f.p(0));
                }
            }
            modifier(player, Attributes.MAX_HEALTH, MOD_TEAM_HP, hp, AttributeModifier.Operation.ADD_VALUE);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        // 遅れて当たる傷
        if (!DELAYED.isEmpty()) {
            for (Iterator<Delayed> it = DELAYED.iterator(); it.hasNext();) {
                Delayed d = it.next();
                if (d.at() > now) {
                    continue;
                }
                Entity t = level.getEntity(d.target());
                Entity a = level.getEntity(d.attacker());
                if (t == null && a == null) {
                    if (now - d.at() > 200) {
                        it.remove();
                    }
                    continue;
                }
                it.remove();
                if (!(a instanceof Player player)) {
                    continue;
                }
                LivingEntity target = t instanceof LivingEntity l && l.isAlive() ? l : null;
                if (d.nearest()) {
                    Vec3 from = target != null ? target.position() : player.position();
                    target = nearestFoe(level, player, from, 8);
                }
                if (target == null) {
                    continue;
                }
                switch (d.look()) {
                    case LOOK_BOLT -> {
                        bolt(level, target.position());
                        proc(level, player, target, d.damage(), ParticleTypes.ELECTRIC_SPARK);
                    }
                    case LOOK_EMBER -> {
                        proc(level, player, target, d.damage(), ParticleTypes.FLAME);
                        target.igniteForTicks(80);
                        level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                        sound(level, target, SoundEvents.FIRECHARGE_USE, 1.6f);
                    }
                    case LOOK_MIRROR -> {
                        if (d.damage() > 0) {
                            proc(level, player, target, d.damage(), ParticleTypes.ENCHANTED_HIT);
                            sound(level, target, SoundEvents.AMETHYST_BLOCK_HIT, 1.4f);
                        }
                    }
                    default -> proc(level, player, target, d.damage(), d.look() == LOOK_VENOM ? ParticleTypes.ITEM_SLIME : ParticleTypes.DAMAGE_INDICATOR);
                }
            }
        }
        // 出血の重ね (1 秒ごと)
        if (now % 20 == 0 && !BLEEDS.isEmpty()) {
            for (Iterator<Map.Entry<Integer, double[]>> it = BLEEDS.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Integer, double[]> e = it.next();
                double[] b = e.getValue();
                Entity t = level.getEntity(e.getKey());
                Entity a = level.getEntity((int) b[1]);
                if (t == null) {
                    if (b[3] < now - 200) {
                        it.remove();
                    }
                    continue;
                }
                if (b[3] < now || !(t instanceof LivingEntity target) || !target.isAlive() || !(a instanceof Player p)) {
                    it.remove();
                    continue;
                }
                proc(level, p, target, (float) (b[0] * b[2]), ParticleTypes.DAMAGE_INDICATOR);
            }
        }
        // 曲がる矢
        if (!HOMING.isEmpty()) {
            for (Iterator<Integer> it = HOMING.iterator(); it.hasNext();) {
                Entity e = level.getEntity(it.next());
                if (e == null) {
                    if (now % 100 == 0) {
                        it.remove();
                    }
                    continue;
                }
                if (!(e instanceof AbstractArrow arrow) || !arrow.isAlive() || arrow.onGround() || arrow.tickCount > 80 || !(arrow.getOwner() instanceof Player p)) {
                    it.remove();
                    continue;
                }
                WildGearTypes.Weapon w = WildGearSetup.weapon(arrow.getWeaponItem());
                Fx f = w == null ? null : w.get(Kind.HOMING);
                if (f == null) {
                    it.remove();
                    continue;
                }
                LivingEntity t = nearestFoe(level, p, arrow.position(), f.p(0));
                if (t != null) {
                    Vec3 v = arrow.getDeltaMovement();
                    Vec3 want = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(arrow.position()).normalize().scale(v.length());
                    if (want.dot(v) > 0) {
                        arrow.setDeltaMovement(v.scale(0.75).add(want.scale(0.25)));
                    }
                }
            }
        }
        // 香の煙
        if (!SMOKES.isEmpty()) {
            SMOKES.removeIf(s -> s.until() <= now && s.dim() == level.dimension());
            if (now % 10 == 0) {
                for (Smoke s : SMOKES) {
                    if (s.dim() != level.dimension()) {
                        continue;
                    }
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.pos().x, s.pos().y + 0.3, s.pos().z, 2, s.radius() * 0.4, 0.05, s.radius() * 0.4, 0.005);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos(), s.pos()).inflate(s.radius()),
                            q -> q.isAlive() && !(q instanceof Player))) {
                        effect(e, MobEffects.MOVEMENT_SLOWDOWN, 15, 0);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 部品
    static boolean count(CompoundTag pd, WildGearTypes.Weapon w, int every) {
        String key = COUNT + w.id();
        int n = pd.getInt(key) + 1;
        if (n >= every) {
            pd.putInt(key, 0);
            return true;
        }
        pd.putInt(key, n);
        return false;
    }

    static void next(CompoundTag pd, double amount, long now) {
        pd.putLong(NEXT, now + 100);
        pd.putDouble(NEXT_AMT, Math.max(amount, pd.getLong(NEXT) > now ? pd.getDouble(NEXT_AMT) : 0));
    }

    static void buff(Player p, String until, String amt, double value, long end) {
        CompoundTag d = p.getPersistentData();
        if (d.getLong(until) <= p.level().getGameTime() || d.getDouble(amt) <= value) {
            d.putDouble(amt, value);
        }
        d.putLong(until, Math.max(end, d.getLong(until)));
    }

    // 人の与える傷の上乗せ: 旗の下で (まわりの仲間の品) と、受けた鼓舞 (風鈴・星の実)
    static double auraDamage(ServerLevel level, Player player, long now) {
        double aura = 0;
        for (Player ally : withSelf(level, player, 8)) {
            Fx f = fx(ally, Kind.AURA_DAMAGE);
            if (f != null && ally.distanceTo(player) <= f.p(1)) {
                aura = Math.max(aura, f.p(0));
            }
        }
        double buff = player.getPersistentData().getLong(BUFF) > now ? player.getPersistentData().getDouble(BUFF_AMT) : 0;
        return (1 + aura) * (1 + buff);
    }

    static void mark(ServerLevel level, LivingEntity target, double amount, int ticks, long now) {
        CompoundTag td = target.getPersistentData();
        if (td.getLong(MARK) <= now || td.getDouble(MARK_AMT) <= amount) {
            td.putDouble(MARK_AMT, amount);
        }
        td.putLong(MARK, now + ticks);
        effect(target, MobEffects.GLOWING, ticks, 0);
    }

    static void link(ServerLevel level, LivingEntity target, int ticks, double share, double radius, long now) {
        LivingEntity other = null;
        double best = radius * radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius), q -> q != target && q.isAlive() && !(q instanceof Player))) {
            double d = e.distanceToSqr(target);
            if (d < best) {
                best = d;
                other = e;
            }
        }
        if (other == null) {
            return;
        }
        for (LivingEntity[] pair : new LivingEntity[][]{{target, other}, {other, target}}) {
            CompoundTag d = pair[0].getPersistentData();
            d.putLong(LINK, now + ticks);
            d.putInt(LINK_TO, pair[1].getId());
            d.putDouble(LINK_AMT, share);
        }
        Vec3 a = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 b = other.position().add(0, other.getBbHeight() * 0.6, 0);
        for (int i = 0; i <= 8; i++) {
            Vec3 p = a.lerp(b, i / 8.0);
            level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    static void snuff(ServerLevel level, LivingEntity target, int ticks, long now) {
        target.getPersistentData().putLong(SNUFFED, now + ticks);
        effect(target, MobEffects.MOVEMENT_SLOWDOWN, ticks, 2);
        effect(target, MobEffects.BLINDNESS, ticks, 0);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(1.0), target.getZ(), 14, 0.2, 0.3, 0.2, 0.02);
        sound(level, target, SoundEvents.CANDLE_EXTINGUISH, 1.0f);
    }

    static void dot(ServerLevel level, Player player, LivingEntity target, float total, int ticks) {
        int pulses = Math.max(1, ticks / 20);
        long now = level.getGameTime();
        for (int i = 1; i <= pulses; i++) {
            later(now + i * 20L, target, player, total / pulses, LOOK_BLEED, false);
        }
    }

    static void bleedStack(LivingEntity target, Player player, int max, float per, long now) {
        double[] b = BLEEDS.get(target.getId());
        if (b == null || b[1] != player.getId()) {
            b = new double[]{0, player.getId(), per, 0};
        }
        b[0] = Math.min(max, b[0] + 1);
        b[3] = now + 80;
        BLEEDS.put(target.getId(), b);
    }

    static void later(long at, LivingEntity target, Player player, float damage, int look, boolean nearest) {
        DELAYED.add(new Delayed(at, target.getId(), player.getId(), damage, look, nearest));
    }

    // 七色: 燃やす・凍え・雷・毒・縛り・癒し・印
    static double prism(ServerLevel level, Player player, LivingEntity target, CompoundTag pd, int colors, long now) {
        int c = pd.getInt(PRISM) % colors;
        pd.putInt(PRISM, c + 1);
        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (c) {
            case 0 -> target.igniteForTicks(60);
            case 1 -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
            case 2 -> {
                bolt(level, target.position());
                proc(level, player, target, (float) (attack * 0.5), ParticleTypes.ELECTRIC_SPARK);
            }
            case 3 -> effect(target, MobEffects.POISON, 80, 1);
            case 4 -> effect(target, MobEffects.MOVEMENT_SLOWDOWN, 30, BossFooting.planted(target) ? 1 : 6);
            case 5 -> {
                for (Player ally : withSelf(level, player, 6)) {
                    ally.heal(2f);
                }
            }
            default -> mark(level, target, 0.12, 100, now);
        }
        int[] rgb = {0xFF4A3A, 0x7FD4FF, 0xFFF06A, 0x6ADA5A, 0x9A7AFF, 0xFFB0D8, 0xFFFFFF};
        net.minecraft.core.particles.DustParticleOptions dust = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(((rgb[c] >> 16) & 255) / 255f, ((rgb[c] >> 8) & 255) / 255f, (rgb[c] & 255) / 255f), 1.4f);
        level.sendParticles(dust, target.getX(), target.getY(0.7), target.getZ(), 12, 0.3, 0.4, 0.3, 0.0);
        return 1;
    }

    static void windSlash(ServerLevel level, Player player, double range, float damage, LivingEntity first) {
        Vec3 from = player.position().add(0, 1.0, 0);
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1).normalize();
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        hit.add(first);
        for (double d = 1; d <= range; d += 0.5) {
            Vec3 at = from.add(dir.scale(d));
            if (((int) (d * 2)) % 2 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(0.9), q -> foe(q, player))) {
                if (hit.add(e)) {
                    proc(level, player, e, damage, ParticleTypes.CLOUD);
                }
            }
        }
        sound(level, player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.3f);
    }

    static void cone(ServerLevel level, Player player, double range, float damage) {
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1).normalize();
        for (LivingEntity e : foes(level, player, player.position(), range)) {
            Vec3 to = e.position().subtract(player.position()).multiply(1, 0, 1);
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(dir) > 0.5) {
                proc(level, player, e, damage, ParticleTypes.END_ROD);
            }
        }
        for (int i = 1; i <= 5; i++) {
            Vec3 at = player.getEyePosition().add(dir.scale(i));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y - 0.3, at.z, 4, 0.3 * i * 0.3, 0.2, 0.3 * i * 0.3, 0.02);
        }
        sound(level, player, SoundEvents.BLAZE_SHOOT, 1.4f);
    }

    // 相手の後ろへ続く直線の敵 n 体に damage (貫くたび +step)
    static void line(ServerLevel level, Player player, LivingEntity first, double length, int n, float damage, double step) {
        Vec3 dir = first.position().subtract(player.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) {
            dir = player.getLookAngle().multiply(1, 0, 1);
        }
        dir = dir.normalize();
        int done = 0;
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        hit.add(first);
        for (double d = 0.5; d <= length && done < n; d += 0.5) {
            Vec3 at = first.position().add(dir.scale(d)).add(0, first.getBbHeight() * 0.5, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(0.7), q -> foe(q, player))) {
                if (done < n && hit.add(e)) {
                    done++;
                    proc(level, player, e, (float) (damage * (1 + step * done)), ParticleTypes.CRIT);
                }
            }
        }
    }

    static void chain(ServerLevel level, Player player, LivingEntity from, float damage, int count, double radius) {
        LivingEntity prev = from;
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        hit.add(from);
        for (int i = 0; i < count; i++) {
            LivingEntity next = null;
            double best = radius * radius;
            for (LivingEntity e : foes(level, player, prev.position(), radius)) {
                double d = e.distanceToSqr(prev);
                if (!hit.contains(e) && d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            hit.add(next);
            Vec3 a = prev.position().add(0, prev.getBbHeight() * 0.6, 0);
            Vec3 b = next.position().add(0, next.getBbHeight() * 0.6, 0);
            for (int k = 0; k <= 6; k++) {
                Vec3 p = a.lerp(b, k / 6.0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
            proc(level, player, next, damage, ParticleTypes.ELECTRIC_SPARK);
            prev = next;
        }
    }

    // まわり radius の敵 (except を除く) に damage
    static void area(ServerLevel level, Player player, Vec3 at, double radius, float damage, LivingEntity except, ParticleOptions look) {
        for (LivingEntity e : foes(level, player, at, radius)) {
            if (e != except) {
                proc(level, player, e, damage, look);
            }
        }
        level.sendParticles(look, at.x, at.y + 0.4, at.z, 10, radius * 0.4, 0.2, radius * 0.4, 0.02);
    }

    // 能力の傷 (ここの発動をもう一度起こさない)
    static void proc(ServerLevel level, Player player, LivingEntity target, float damage, ParticleOptions look) {
        if (damage <= 0 || !target.isAlive()) {
            return;
        }
        inProc = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().indirectMagic(target, player), damage);
        } finally {
            inProc = false;
        }
        level.sendParticles(look, target.getX(), target.getY(0.6), target.getZ(), 3, 0.2, 0.3, 0.2, 0.02);
    }

    static void bolt(ServerLevel level, Vec3 at) {
        for (int i = 0; i < 10; i++) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + i * 0.6, at.z, 3, 0.08, 0.2, 0.08, 0.02);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.6f);
    }

    static void pull(Player player, LivingEntity target, double blocks, boolean boss) {
        if (boss) {
            return;
        }
        Vec3 d = player.position().subtract(target.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 2.0) {
            return;
        }
        Vec3 v = d.normalize().scale(Math.min(blocks, d.length() - 1.2) * 0.35);
        target.push(v.x, 0.15, v.z);
        target.hurtMarked = true;
    }

    static void shred(LivingEntity target, double per, double max, int ticks, long now) {
        AttributeInstance a = target.getAttribute(Attributes.ARMOR);
        if (a == null) {
            return;
        }
        AttributeModifier cur = a.getModifier(MOD_SHRED);
        double v = Math.max(-max, (cur == null ? 0 : cur.amount()) - per);
        a.addOrUpdateTransientModifier(new AttributeModifier(MOD_SHRED, v, AttributeModifier.Operation.ADD_VALUE));
        target.getPersistentData().putLong(P + "shred_until", now + ticks);
        WildArmorEvents.expire(target, MOD_SHRED, now + ticks);
    }

    static void armorDown(LivingEntity target, double amount, int ticks, long now) {
        AttributeInstance a = target.getAttribute(Attributes.ARMOR);
        if (a == null) {
            return;
        }
        a.addOrUpdateTransientModifier(new AttributeModifier(MOD_ARMOR_DOWN, -amount, AttributeModifier.Operation.ADD_VALUE));
        WildArmorEvents.expire(target, MOD_ARMOR_DOWN, now + ticks);
    }

    // 防具を無視した分の目安: 相手の防具の効き (防具 / 25、上限 0.8) × 無視する割合
    static double armorShare(LivingEntity target, float amount) {
        return Math.min(0.8, target.getArmorValue() / 25.0);
    }

    static void modifier(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance a = e.getAttribute(attr);
        if (a == null) {
            return;
        }
        AttributeModifier cur = a.getModifier(id);
        if (amount == 0) {
            if (cur != null) {
                a.removeModifier(id);
            }
        } else if (cur == null || cur.amount() != amount) {
            a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    static void effect(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp) {
        if (ticks > 0) {
            e.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amp), true, false));
        }
    }

    static void sound(ServerLevel level, Entity at, SoundEvent s, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundSource.PLAYERS, 0.8f, pitch);
    }

    // 敵 = 生きていて、人でなく、仲間にした者でない
    static boolean foe(LivingEntity e, Player p) {
        return e.isAlive() && !(e instanceof Player) && e != p && !(e instanceof net.minecraft.world.entity.TamableAnimal t && t.isOwnedBy(p))
                && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
    }

    static List<LivingEntity> foes(ServerLevel level, Player p, Vec3 at, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                q -> foe(q, p) && q.position().distanceTo(at) <= radius + q.getBbWidth() / 2);
    }

    static LivingEntity nearestFoe(ServerLevel level, Player p, Vec3 at, double radius) {
        LivingEntity best = null;
        double bd = radius * radius;
        for (LivingEntity e : foes(level, p, at, radius)) {
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    static List<Player> allies(ServerLevel level, Player p, double radius) {
        return level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(radius), q -> q != p && q.isAlive() && !q.isSpectator());
    }

    static List<Player> withSelf(ServerLevel level, Player p, double radius) {
        List<Player> l = new ArrayList<>(allies(level, p, radius));
        l.add(p);
        return l;
    }

    // 背から (相手の向きの後ろ 110 度)
    public static boolean fromBehind(LivingEntity target, Vec3 from) {
        Vec3 to = from.subtract(target.position());
        Vec3 fwd = Vec3.directionFromRotation(0, target.yBodyRot);
        return new Vec3(to.x, 0, to.z).normalize().dot(fwd) < -0.57;
    }

    // 獣 (鼠・犬・狼・猪・兎・羊・蟹のような四つ足の生き物。登録名の語で)
    static final java.util.Set<String> BEAST_WORDS = java.util.Set.of("rat", "dog", "wolf", "boar", "rabbit", "hare", "ram", "sheep", "beast", "hound",
            "fox", "bear", "lizard", "crab", "rodent");

    public static boolean beast(LivingEntity e) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        for (String w : path.split("_")) {
            if (BEAST_WORDS.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
