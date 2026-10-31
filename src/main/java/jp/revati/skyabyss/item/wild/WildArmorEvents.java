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
                    out.addAll(a.row().fx());
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
        for (Fx f : fx) {
            switch (f.kind()) {
                case CRIT -> {
                    if (p.getRandom().nextDouble() < f.p(0)) {
                        m *= 1 + f.p(1);
                        if (p.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                        }
                    }
                }
                case BERSERK -> m *= p.getHealth() < p.getMaxHealth() * f.p(0) ? 1 + f.p(1) : 1;
                case DARK_BONUS -> m *= p.level().getMaxLocalRawBrightness(p.blockPosition()) <= f.p(0) ? 1 + f.p(1) : 1;
                default -> {
                }
            }
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
        Fx f = find(active(p), Kind.LAST_STAND);
        long now = p.level().getGameTime();
        if (f != null && p.getHealth() < p.getMaxHealth() * f.p(0) && p.getPersistentData().getLong(LAST_STAND) <= now) {
            p.getPersistentData().putLong(LAST_STAND, now + f.ticks(2));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, f.ticks(1), 1, true, true));
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
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
            for (Fx f : fx) {
                if (f.kind() == Kind.REGEN_TICK && (p.tickCount / 20) % Math.max(1, f.ticks(1) / 20) == 0 && p.getHealth() < p.getMaxHealth()) {
                    p.heal((float) f.p(0));
                }
            }
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
