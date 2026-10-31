package jp.revati.skyabyss.tower.gear;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// 塔の武器の効き方 (docs/design/54 の 2.6。表は TowerGear)。必要 Lv に届いていないと効かない
//  - あいだの大剣: 体力が半分より上なら +15%、半分以下なら与えた傷の 10% を体力に
//  - 引き合いの鎚: 4 度目ごとに ×1.5、まわり 5 の敵を自分のほうへ引き寄せる
//  - 落ちる月の杖: 打った相手に月の印 6 秒。印のある相手への 3 打目ごとに、次の tick に小さな月が落ちる (まわり 3 に攻撃力の 120%)
//  - 側の目の弓: 同じ相手に続けて当てるたびに +6% (5 重ねまで)。別の相手で数え直し
//  - 追う印の短剣: 打つと 3 秒足が速くなり、その間の次の一撃は +35% (3 秒に一度)
//  - 合掌の鈴杖: 打つたびに祈り 1。5 つで合掌 (まわり 6 の仲間と自分の体力 4・マナ 6)
//  - 天秤の甲冑 4 部位: 受ける傷 ×0.90、体力が半分以下で強靭 +12 / 渡りの装束 4 部位: 速さ +10%、走っているあいだ受ける傷 ×0.90 /
//    光輪の衣 4 部位: マナの戻り +40%・技の待ち −8% (WildGearEvents の manaRegenBonus・cooldownScale から呼ぶ)
//  - 天の環: 体力が満ちているあいだ与える傷 +8% / 淵の鎖: 体力が 3 割以下で受ける傷 ×0.80
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TowerGearEvents {
    static final String HAMMER_COUNT = SkyAbyss.MODID + ":tower_pull_count";
    static final String MOON_MARK = SkyAbyss.MODID + ":tower_moon_mark";
    static final String MOON_COUNT = SkyAbyss.MODID + ":tower_moon_count";
    static final String EYE_TARGET = SkyAbyss.MODID + ":tower_eye_target";
    static final String EYE_COUNT = SkyAbyss.MODID + ":tower_eye_count";
    static final String SIGIL_READY = SkyAbyss.MODID + ":tower_sigil_ready";
    static final String SIGIL_ARMED = SkyAbyss.MODID + ":tower_sigil_armed";
    static final String PRAYERS = SkyAbyss.MODID + ":tower_prayers";
    static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.4f), 1.0f);
    static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.6f, 0.35f, 1.0f), 1.0f);

    // 次の tick に落ちる小さな月: 所・傷・打った人 (傷の中から別の傷を起こさない)
    record Moon(Vec3 pos, float damage, int attacker) {
    }

    private static final List<Moon> MOONS = new ArrayList<>();
    // 小さな月の傷を入れているあいだ (その傷で武器の効き方を動かさない)
    private static boolean falling;

    private TowerGearEvents() {
    }

    static boolean holds(Player p, Object item) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() == item && GearLevel.meets(p, hand);
    }

    static final ResourceLocation BRIDGE_SPEED = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tower_bridge_speed");
    static final ResourceLocation SCALE_TOUGH = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tower_scale_toughness");

    static boolean full(LivingEntity e, String set) {
        return TowerArmorItem.piecesWorn(e, set) >= 4;
    }

    static boolean wears(Player p, Item item) {
        return Accessories.worn(p, item) && GearLevel.meets(p, new ItemStack(item));
    }

    // 光輪の衣のマナの戻りと技の待ち (rpg/skill から WildGearEvents を通して)
    public static double manaRegenBonus(Player p) {
        return full(p, TowerGear.HALO) ? TowerGear.HALO_REGEN : 0;
    }

    public static double cooldownCut(Player p) {
        return full(p, TowerGear.HALO) ? TowerGear.HALO_COOLDOWN : 0;
    }

    // 受ける傷 (防具と淵の鎖) と、天の環の与える傷
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTaken(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getAmount() <= 0) {
            return;
        }
        double m = 1;
        if (target instanceof Player p && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (full(p, TowerGear.SCALE)) {
                m *= TowerGear.SCALE_TAKEN;
            }
            if (p.isSprinting() && full(p, TowerGear.BRIDGE)) {
                m *= TowerGear.BRIDGE_SPRINT_TAKEN;
            }
            if (p.getHealth() <= p.getMaxHealth() * TowerGear.CHAIN_LOW && wears(p, TowerGearSetup.CHAIN.get())) {
                m *= TowerGear.CHAIN_TAKEN;
            }
        }
        if (event.getSource().getEntity() instanceof Player a && a != target && a.getHealth() >= a.getMaxHealth() - 0.5f && wears(a, TowerGearSetup.RING.get())) {
            m *= 1 + TowerGear.RING_BONUS;
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 渡りの装束の速さと、天秤の甲冑の低い体力の強靭 (1 秒ごとに付け外し)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || p.tickCount % 20 != 3) {
            return;
        }
        toggle(p, Attributes.MOVEMENT_SPEED, BRIDGE_SPEED, full(p, TowerGear.BRIDGE), TowerGear.BRIDGE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        toggle(p, Attributes.ARMOR_TOUGHNESS, SCALE_TOUGH, full(p, TowerGear.SCALE) && p.getHealth() <= p.getMaxHealth() * 0.5f,
                TowerGear.SCALE_LOW_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
    }

    static void toggle(Player p, Holder<Attribute> attr, ResourceLocation id, boolean on, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        boolean has = inst.getModifier(id) != null;
        if (on && !has) {
            inst.addTransientModifier(new AttributeModifier(id, amount, op));
        } else if (!on && has) {
            inst.removeModifier(id);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (falling || !(target.level() instanceof ServerLevel level) || event.getAmount() <= 0 || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        DamageSource source = event.getSource();
        double m = 1;
        if (source.getDirectEntity() == player) {
            m = melee(level, player, target, event.getAmount());
        } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                && arrow.getWeaponItem().getItem() == TowerGearSetup.BOW.get() && GearLevel.meets(player, arrow.getWeaponItem())) {
            m = eyeBow(level, player, target);
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 側の目の弓: 同じ相手に続けて当てた数 (この一矢を含む) で +6% ずつ
    static double eyeBow(ServerLevel level, Player player, LivingEntity target) {
        var d = player.getPersistentData();
        int n = d.getInt(EYE_TARGET) == target.getId() ? Math.min(TowerGear.BOW_MAX, d.getInt(EYE_COUNT) + 1) : 0;
        d.putInt(EYE_TARGET, target.getId());
        d.putInt(EYE_COUNT, n);
        level.sendParticles(GOLD, target.getX(), target.getY(0.6), target.getZ(), 2 + 2 * n, 0.3, 0.3, 0.3, 0);
        return 1 + TowerGear.BOW_STEP * n;
    }

    // 近接の武器の効果。与える傷の倍率を返す
    static double melee(ServerLevel level, Player player, LivingEntity target, float amount) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || !GearLevel.meets(player, hand)) {
            return 1;
        }
        long now = level.getGameTime();
        var pd = player.getPersistentData();
        if (hand.getItem() == TowerGearSetup.SWORD.get()) {
            if (player.getHealth() > player.getMaxHealth() * 0.5f) {
                level.sendParticles(GOLD, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.3, 0.2, 0);
                return 1 + TowerGear.SWORD_HIGH;
            }
            player.heal((float) (amount * TowerGear.SWORD_DRAIN));
            level.sendParticles(VIOLET, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.4, 0.3, 0);
            return 1;
        }
        if (hand.getItem() == TowerGearSetup.HAMMER.get()) {
            int n = pd.getInt(HAMMER_COUNT) + 1;
            if (n < TowerGear.HAMMER_EVERY) {
                pd.putInt(HAMMER_COUNT, n);
                return 1;
            }
            pd.putInt(HAMMER_COUNT, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TowerGear.HAMMER_RADIUS),
                    q -> q != player && q.isAlive() && !(q instanceof Player))) {
                Vec3 d = player.position().subtract(e.position()).multiply(1, 0, 1);
                if (d.lengthSqr() > 1.5) {
                    d = d.normalize();
                    e.push(d.x * 0.7, 0.15, d.z * 0.7);
                    e.hurtMarked = true;
                }
            }
            level.sendParticles(VIOLET, player.getX(), player.getY() + 0.2, player.getZ(), 30, TowerGear.HAMMER_RADIUS * 0.5, 0.1, TowerGear.HAMMER_RADIUS * 0.5, 0);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.4f, 0.6f);
            return TowerGear.HAMMER_PULL;
        }
        if (hand.getItem() == TowerGearSetup.STAFF.get()) {
            var td = target.getPersistentData();
            boolean marked = td.getLong(MOON_MARK) > now;
            td.putLong(MOON_MARK, now + TowerGear.STAFF_MARK_TICKS);
            int n = marked ? td.getInt(MOON_COUNT) + 1 : 1;
            if (n >= TowerGear.STAFF_EVERY) {
                n = 0;
                MOONS.add(new Moon(target.position(), (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * TowerGear.STAFF_MOON), player.getId()));
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 5, target.getZ(), 8, 0.2, 0.6, 0.2, 0.01);
            }
            td.putInt(MOON_COUNT, n);
            level.sendParticles(GOLD, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 2 + n * 2, 0.2, 0.05, 0.2, 0);
            return 1;
        }
        if (hand.getItem() == TowerGearSetup.DAGGER.get()) {
            double m = 1;
            if (pd.getBoolean(SIGIL_ARMED) && pd.getLong(SIGIL_READY) > now) {
                pd.putBoolean(SIGIL_ARMED, false);
                m = 1 + TowerGear.DAGGER_NEXT;
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.2, 0.3, 0.2, 0.1);
            } else if (pd.getLong(SIGIL_READY) <= now) {
                pd.putLong(SIGIL_READY, now + TowerGear.DAGGER_TICKS);
                pd.putBoolean(SIGIL_ARMED, true);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, TowerGear.DAGGER_TICKS, 0, true, false));
                level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.15f), 1.0f), target.getX(), target.getY() + 0.1, target.getZ(),
                        12, 0.6, 0, 0.6, 0);
            }
            return m;
        }
        if (hand.getItem() == TowerGearSetup.BELL.get()) {
            int n = pd.getInt(PRAYERS) + 1;
            if (n >= TowerGear.BELL_PRAYERS) {
                n = 0;
                clap(level, player);
            }
            pd.putInt(PRAYERS, n);
            return 1;
        }
        return 1;
    }

    // 合掌: まわりの仲間 (自分も) の体力とマナを戻す
    static void clap(ServerLevel level, Player player) {
        for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(TowerGear.BELL_RADIUS), q -> q.isAlive() && !q.isSpectator())) {
            ally.heal(TowerGear.BELL_HEAL);
            if (ally instanceof ServerPlayer sp) {
                SkillManager.setMana(sp, SkillManager.get(sp).mana + TowerGear.BELL_MANA);
            }
            level.sendParticles(GOLD, ally.getX(), ally.getY() + 1.2, ally.getZ(), 10, 0.3, 0.5, 0.3, 0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    // 落ちる小さな月 (打った人の傷として。印や数は動かさない)
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (MOONS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Moon> now = new ArrayList<>(MOONS);
        MOONS.clear();
        for (Iterator<Moon> it = now.iterator(); it.hasNext();) {
            Moon m = it.next();
            Entity a = level.getEntity(m.attacker());
            if (!(a instanceof Player p)) {
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, m.pos().x, m.pos().y + 0.5, m.pos().z, 24, TowerGear.STAFF_RADIUS * 0.5, 0.3, TowerGear.STAFF_RADIUS * 0.5, 0.05);
            level.playSound(null, m.pos().x, m.pos().y, m.pos().z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(m.pos(), m.pos()).inflate(TowerGear.STAFF_RADIUS),
                    q -> q != p && q.isAlive() && !(q instanceof Player))) {
                e.invulnerableTime = 0;
                falling = true;
                try {
                    e.hurt(level.damageSources().indirectMagic(p, p), m.damage());
                } finally {
                    falling = false;
                }
            }
        }
    }
}
