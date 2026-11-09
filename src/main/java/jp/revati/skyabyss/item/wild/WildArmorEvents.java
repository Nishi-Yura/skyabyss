package jp.revati.skyabyss.item.wild;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.wild.WildEffect.Fx;
import jp.revati.skyabyss.item.wild.WildEffect.Kind;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 野の装備の防具の一式の効果 (docs/design/51 の 5 章。2 部位と 4 部位)。一式ごとの数は WildEffect の種類の並び。
// 二つの一式を 2 部位ずつ着ると、どちらの 2 部位の効果も効く
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WildArmorEvents {
    static final net.minecraft.core.particles.DustParticleOptions CLOUD_WHITE = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.96f, 0.97f, 1.0f), 1.3f);
    static final net.minecraft.core.particles.DustParticleOptions CLOUD_GOLD = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.86f, 0.5f), 1.3f);
    static final String P = SkyAbyss.MODID + ":wild_armor_";
    static final String STILL = P + "still", STILL_POS = P + "still_pos", LAST_STAND = P + "last_stand", NOOSE = P + "noose", CLOUD = P + "cloud";
    // 装飾の固有の働きの記録 (待ち・次の一撃・水の中にいたか)
    static final String CHAIN = P + "crit_chain", HURT_MANA = P + "hurt_mana", BURST = P + "burst", HORN = P + "horn", WET = P + "wet";

    // 期限つきの修飾子 (相手の防具を下げる能力など): 生き物の id・修飾子・期限
    record Expire(int entity, ResourceLocation id, long at) {
    }

    private static final List<Expire> EXPIRES = new ArrayList<>();

    private WildArmorEvents() {
    }

    // 効いている一式の効果 (2 部位・4 部位)
    static List<Fx> active(LivingEntity e) {
        List<Fx> out = new ArrayList<>();
        for (Map.Entry<WildGearTypes.ArmorSet, Integer> s : WildArmorItem.worn(e).entrySet()) {
            if (s.getValue() >= 2) {
                out.addAll(s.getKey().set2());
            }
            if (s.getValue() >= 4) {
                out.addAll(s.getKey().set4());
            }
        }
        // 装飾の枠に付けた野の装飾 (護符は使って効くので数えない)
        if (e instanceof Player p) {
            for (int i = 0; i < jp.revati.skyabyss.item.accessory.AccessoryData.SIZE; i++) {
                if (i == jp.revati.skyabyss.item.accessory.AccessoryData.CHARM) {
                    continue;
                }
                ItemStack s = jp.revati.skyabyss.item.accessory.Accessories.stack(p, i);
                if (s.getItem() instanceof WildAccessoryItem a && !a.row().charm() && jp.revati.skyabyss.item.GearLevel.meets(p, s)) {
                    // ダンジョンの装飾品は育った段で数字が伸びる (dungeon/DungeonGear)
                    // 野の装飾は個体差 (AccessoryRolls) も掛ける
                    out.addAll(AccessoryRolls.apply(s, jp.revati.skyabyss.dungeon.DungeonGear.scaled(s, a.row().fx())));
                }
            }
        }
        return out;
    }

    static Fx find(List<Fx> fx, Kind k) {
        for (Fx f : fx) {
            if (f.kind() == k) {
                return f;
            }
        }
        return null;
    }

    static double sum(List<Fx> fx, Kind k, int i) {
        double v = 0;
        for (Fx f : fx) {
            if (f.kind() == k) {
                v += f.p(i);
            }
        }
        return v;
    }

    // ---------------------------------------------------------------- 外から
    public static double manaRegenBonus(Player p) {
        List<Fx> fx = active(p);
        double b = sum(fx, Kind.MANA_REGEN, 0);
        if (night(p)) {
            b += sum(fx, Kind.NIGHT_MANA, 0);
        }
        if (p.isInWaterOrRain()) {
            b += sum(fx, Kind.WET_MANA, 0);
        }
        return b;
    }

    // 与える傷の倍率 (夜・燃えている相手)
    static double damageFactor(Player p, LivingEntity target, long now) {
        List<Fx> fx = active(p);
        if (fx.isEmpty()) {
            return 1;
        }
        double m = 1;
        if (night(p)) {
            m *= 1 + sum(fx, Kind.NIGHT_MANA, 1) + sum(fx, Kind.NIGHT_DAMAGE, 0);
        }
        if (target.isOnFire()) {
            m *= 1 + sum(fx, Kind.BURN_BONUS_SET, 0);
        }
        // 装飾の条件つきの上乗せ (会心・背水・暗がり)
        // 会心の次の一撃 (深みの眼の護り): 前の一撃で会心が出ていれば、この一撃は必ず会心
        boolean forced = p.getPersistentData().getBoolean(CHAIN);
        if (forced) {
            p.getPersistentData().remove(CHAIN);
        }
        boolean crit = false;
        for (Fx f : fx) {
            switch (f.kind()) {
                case CRIT -> {
                    if (!crit && (forced || p.getRandom().nextDouble() < f.p(0))) {
                        crit = true;
                        m *= 1 + f.p(1);
                        if (p.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                        }
                    }
                }
                case AIR_STRIKE -> m *= !p.onGround() && !p.isInWater() ? 1 + f.p(0) : 1;
                case STORM_DAMAGE -> m *= p.level().isRaining() ? 1 + f.p(0) : 1;
                case BURNING_RAGE -> m *= p.isOnFire() ? 1 + f.p(0) : 1;
                case BERSERK -> m *= p.getHealth() < p.getMaxHealth() * f.p(0) ? 1 + f.p(1) : 1;
                case DARK_BONUS -> m *= p.level().getMaxLocalRawBrightness(p.blockPosition()) <= f.p(0) ? 1 + f.p(1) : 1;
                default -> {
                }
            }
        }
        if (crit) {
            afterCrit(p, target, fx, forced);
        }
        return m;
    }

    // 受ける傷の倍率
    static double takenFactor(ServerLevel level, Player p, DamageSource source, long now) {
        List<Fx> fx = active(p);
        if (fx.isEmpty()) {
            return 1;
        }
        double m = 1;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            m *= 1 + sum(fx, Kind.FIRE_TAKEN, 0);
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            m *= Math.max(0, 1 + sum(fx, Kind.FALL_TAKEN, 0));
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypeTags.IS_LIGHTNING)) {
            m *= 1 + sum(fx, Kind.LIGHTNING_TAKEN, 0);
        }
        Fx grit = find(fx, Kind.GRIT);
        if (grit != null) {
            m *= 1 - (p.getHealth() < p.getMaxHealth() / 2 ? grit.p(1) : grit.p(0));
        }
        Fx bulwark = find(fx, Kind.BULWARK);
        if (bulwark != null) {
            m *= 1 - Math.min(bulwark.p(1), bulwark.p(0) * p.getPersistentData().getInt(STILL));
        }
        // 落ちて着地した (織り紐の腕輪): まわりの敵へ衝撃
        Fx shock = find(fx, Kind.FALL_SHOCK);
        if (shock != null && source.is(DamageTypeTags.IS_FALL)) {
            for (LivingEntity e : foes(level, p, shock.p(0))) {
                WildGearEvents.proc(level, p, e, (float) shock.p(1), ParticleTypes.POOF);
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        if (source.getEntity() instanceof LivingEntity a && a != p) {
            // 打たれるとマナ (塩の涙の耳飾り)
            Fx mana = find(fx, Kind.HURT_MANA);
            if (mana != null && p instanceof ServerPlayer sp && p.getPersistentData().getLong(HURT_MANA) <= now) {
                p.getPersistentData().putLong(HURT_MANA, now + mana.ticks(1));
                jp.revati.skyabyss.rpg.skill.SkillManager.gainMana(sp, mana.p(0));
            }
            // 打たれると燃やし返す (大蛇の熾の鱗)
            Fx ignite = find(fx, Kind.HURT_IGNITE);
            if (ignite != null && !(a instanceof Player) && p.getRandom().nextDouble() < ignite.p(0)) {
                a.igniteForSeconds((float) ignite.p(1));
                level.sendParticles(ParticleTypes.FLAME, a.getX(), a.getY(0.5), a.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            }
        }
        // 打たれ返し (雷・塩の棘)
        if (source.getEntity() instanceof LivingEntity a && a != p && !(a instanceof Player)) {
            for (Kind k : new Kind[]{Kind.THORN_BOLT, Kind.THORNS}) {
                Fx f = find(fx, k);
                if (f != null && p.getRandom().nextDouble() < f.p(0)) {
                    if (k == Kind.THORN_BOLT) {
                        WildGearEvents.bolt(level, a.position());
                    }
                    WildGearEvents.proc(level, p, a, (float) f.p(1), k == Kind.THORN_BOLT ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.WHITE_ASH);
                }
            }
        }
        return m;
    }

    // 敵に気づかれる距離の減り (しゃがむと)
    static double stealth(Player p) {
        return p.isShiftKeyDown() ? sum(active(p), Kind.SNEAK_STEALTH, 0) : 0;
    }

    // 雲の守りの膜があるか (膜の期限で消さない)
    static boolean cloudWard(Player p) {
        return find(active(p), Kind.CLOUD_WARD) != null;
    }

    // 期限で外す修飾子を覚える (同じ生き物の同じ修飾子は新しい期限で上書き)
    static void expire(LivingEntity e, ResourceLocation id, long at) {
        EXPIRES.removeIf(x -> x.entity() == e.getId() && x.id().equals(id));
        EXPIRES.add(new Expire(e.getId(), id, at));
    }

    static boolean night(Player p) {
        return p.level().isNight() || p.level().dimension() == jp.revati.skyabyss.story.ch6.MoonWorld.LEVEL;
    }

    // ---------------------------------------------------------------- 倒れる傷 (吊り) と、体力が減ったとき (起き上がる兵)
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() < p.getHealth()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Fx noose = find(active(p), Kind.NOOSE);
        long now = p.level().getGameTime();
        if (noose == null || p.getPersistentData().getLong(NOOSE) > now) {
            return;
        }
        p.getPersistentData().putLong(NOOSE, now + noose.ticks(1));
        event.setNewDamage(Math.max(0, p.getHealth() - 1));
        Vec3 back = p.getLookAngle().multiply(-1, 0, -1).normalize().scale(noose.p(0) * 0.25);
        p.push(back.x, 0.55, back.z);
        p.hurtMarked = true;
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.6, p.getZ(), 16, 0.2, 0.6, 0.2, 0.02);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        List<Fx> all = active(p);
        Fx f = find(all, Kind.LAST_STAND);
        long now = p.level().getGameTime();
        halfHealth(p, all, now);
        if (f != null && p.getHealth() < p.getMaxHealth() * f.p(0) && p.getPersistentData().getLong(LAST_STAND) <= now) {
            p.getPersistentData().putLong(LAST_STAND, now + f.ticks(2));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, f.ticks(1), 1, true, true));
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }

    // ---------------------------------------------------------------- 装飾の固有の働き (2026-11-10)
    static List<LivingEntity> foes(ServerLevel level, Player p, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy);
    }

    // 会心のあと (速さ・遅く・次の一撃)
    static void afterCrit(Player p, LivingEntity target, List<Fx> fx, boolean forced) {
        for (Fx f : fx) {
            switch (f.kind()) {
                case CRIT_RUSH -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, f.ticks(1), f.ticks(0), true, true));
                case CRIT_SLOW -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, f.ticks(0), f.ticks(1), true, true), p);
                case CRIT_CHAIN -> {
                    if (!forced) {
                        p.getPersistentData().putBoolean(CHAIN, true);
                    }
                }
                default -> {
                }
            }
        }
    }

    // 体力が半分を切った (隕鉄の環の衝撃・砦の骨の角笛)
    static void halfHealth(ServerPlayer p, List<Fx> fx, long now) {
        if (p.getHealth() >= p.getMaxHealth() / 2 || !p.isAlive()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        Fx burst = find(fx, Kind.HURT_BURST);
        if (burst != null && p.getPersistentData().getLong(BURST) <= now) {
            p.getPersistentData().putLong(BURST, now + burst.ticks(2));
            for (LivingEntity e : foes(level, p, burst.p(0))) {
                WildGearEvents.proc(level, p, e, (float) burst.p(1), ParticleTypes.EXPLOSION);
                Vec3 d = e.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.9);
                e.push(d.x, 0.35, d.z);
                e.hurtMarked = true;
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
        Fx horn = find(fx, Kind.HORN);
        if (horn != null && p.getPersistentData().getLong(HORN) <= now) {
            p.getPersistentData().putLong(HORN, now + horn.ticks(2));
            for (Player ally : WildGearEvents.withSelf(level, p, horn.p(0))) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, horn.ticks(1), 0, true, true));
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    // 一秒ごと: 暗がりの敵を光らせる・水から上がった
    static void accessoryTick(ServerPlayer p, List<Fx> fx, CompoundTag pd) {
        ServerLevel level = p.serverLevel();
        Fx glow = find(fx, Kind.DARK_GLOW);
        if (glow != null && level.getMaxLocalRawBrightness(p.blockPosition()) <= 6) {
            for (LivingEntity e : foes(level, p, glow.p(0))) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false));
            }
        }
        Fx exit = find(fx, Kind.WATER_EXIT);
        boolean wet = p.isInWater();
        if (exit != null && !wet && pd.getBoolean(WET)) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, exit.ticks(1), exit.ticks(0), true, true));
            level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        }
        pd.putBoolean(WET, wet);
    }

    // 倒した (灰の砂時計の飾り・骨掘りの顎飾り)
    @SubscribeEvent
    static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() == p) {
            return;
        }
        List<Fx> fx = active(p);
        Fx cut = find(fx, Kind.KILL_COOLDOWN);
        if (cut != null) {
            int t = cut.ticks(0);
            var d = jp.revati.skyabyss.rpg.skill.SkillManager.get(p);
            d.cooldowns.replaceAll((k, v) -> Math.max(0, v - t));
            d.cooldowns.values().removeIf(v -> v <= 0);
            jp.revati.skyabyss.rpg.skill.SkillManager.sync(p);
        }
        Fx ward = find(fx, Kind.BONE_WARD);
        if (ward != null) {
            p.setAbsorptionAmount((float) Math.max(p.getAbsorptionAmount(), ward.p(0)));
            p.serverLevel().sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 1, p.getZ(), 16, 0.4, 0.5, 0.4, 0.02);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        CompoundTag pd = p.getPersistentData();
        long now = p.level().getGameTime();
        if (p.tickCount % 20 == 9) {
            List<Fx> fx = active(p);
            Fx moss = find(fx, Kind.MOSS_STILL);
            double still = moss != null && pd.getInt(STILL) >= 1 ? moss.p(0) : 1;
            for (Fx f : fx) {
                if (f.kind() == Kind.REGEN_TICK && (p.tickCount / 20) % Math.max(1, f.ticks(1) / 20) == 0 && p.getHealth() < p.getMaxHealth()) {
                    p.heal((float) (f.p(0) * still));
                }
                if (f.kind() == Kind.WATER_REGEN && p.isInWater() && p.getHealth() < p.getMaxHealth()) {
                    p.heal((float) f.p(0));
                }
            }
            accessoryTick(p, fx, pd);
            toggle(p, Attributes.MAX_HEALTH, "max_health", sum(fx, Kind.MAX_HEALTH, 0), AttributeModifier.Operation.ADD_VALUE);
            toggle(p, Attributes.ARMOR_TOUGHNESS, "toughness", sum(fx, Kind.TOUGHNESS, 0), AttributeModifier.Operation.ADD_VALUE);
            toggle(p, jp.revati.skyabyss.rpg.Rpg.MAX_MANA, "max_mana", sum(fx, Kind.MAX_MANA, 0), AttributeModifier.Operation.ADD_VALUE);
            toggle(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "swim", sum(fx, Kind.SWIM, 0), AttributeModifier.Operation.ADD_VALUE);
            toggle(p, Attributes.OXYGEN_BONUS, "breath", sum(fx, Kind.BREATH, 0), AttributeModifier.Operation.ADD_VALUE);
            Fx cloud = find(fx, Kind.CLOUD_WARD);
            double move = sum(fx, Kind.MOVE_SPEED, 0) + (cloud != null && p.getAbsorptionAmount() > 0 ? cloud.p(2) : 0);
            toggle(p, Attributes.MOVEMENT_SPEED, "move", move, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (p.getHealth() > p.getMaxHealth()) {
                p.setHealth(p.getMaxHealth());
            }
            ServerLevel level = p.serverLevel();
            boolean dark = level.getMaxLocalRawBrightness(p.blockPosition()) <= 6;
            if (find(fx, Kind.NIGHT_VISION) != null && dark) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
            }
            // 灯守の法衣: 暗い所で、まわり 8 の仲間にも灯 (暗視) と灯の粒
            if (find(fx, Kind.LIGHT) != null && dark) {
                for (Player ally : WildGearEvents.withSelf(level, p, 8)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false));
                }
                level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 2.3, p.getZ(), 2, 0.1, 0.05, 0.1, 0.0);
            }
            // 雲の守り
            if (cloud != null && now - pd.getLong(CLOUD) >= cloud.p(0)) {
                pd.putLong(CLOUD, now);
                p.setAbsorptionAmount((float) Math.max(p.getAbsorptionAmount(), cloud.p(1)));
                // 白と金の光の輪 (2026-11-01: バニラの雲の粒は消えぎわに暗くなり、地面の上で黒い欠けに見えた)
                for (int i = 0; i < 16; i++) {
                    double ang = i / 16.0 * Math.PI * 2;
                    level.sendParticles(i % 4 == 0 ? CLOUD_GOLD : CLOUD_WHITE, p.getX() + Math.cos(ang) * 0.8, p.getY() + 0.9, p.getZ() + Math.sin(ang) * 0.8,
                            1, 0, 0.15, 0, 0);
                }
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
            }
            // 砦: 止まっている秒
            Vec3 at = p.position();
            long packed = net.minecraft.core.BlockPos.containing(at).asLong();
            if (pd.getLong(STILL_POS) == packed) {
                pd.putInt(STILL, pd.getInt(STILL) + 1);
            } else {
                pd.putLong(STILL_POS, packed);
                pd.putInt(STILL, 0);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || EXPIRES.isEmpty() || level.getGameTime() % 5 != 0) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Expire> it = EXPIRES.iterator(); it.hasNext();) {
            Expire x = it.next();
            if (x.at() > now) {
                continue;
            }
            Entity e = level.getEntity(x.entity());
            if (e == null) {
                if (now - x.at() > 200) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            if (e instanceof LivingEntity l) {
                AttributeInstance a = l.getAttribute(Attributes.ARMOR);
                if (a != null) {
                    a.removeModifier(x.id());
                }
            }
        }
    }

    static void toggle(Player p, Holder<Attribute> attr, String name, double amount, AttributeModifier.Operation op) {
        WildGearEvents.modifier(p, attr, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "wild/armor_" + name), amount, op);
    }
}
