package jp.revati.skyabyss.story.ch6.gear;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 月の装備の効果 (docs/design/03 の 12.2)。
//  - 月渡りの装束: 2 部位で強靭 +4。4 部位「月の足」: 落下の傷 ×0.5、月の上で受ける傷 ×0.9
//  - 月秤の鎚: 相手の体力の割合が自分より高いと +15%
//  - 満ち引きの杖: 打つたびに満ちと引きが入れ替わる (満ち +20% / 引き 与えた傷の 10% を体力に)
//  - 下弦の弓: 矢が当たった相手に 1 秒後、残像の矢 (当たった傷の 40%)
//  - 欠け月の短剣: 背から刺すと +30%
//  - 星灯りの鈴杖: 打った相手に星の印 4 秒 (だれの傷も +10%、光る)
//  - 残月の太刀: 3 度に 1 度、1.5 秒後に残像の太刀 (攻撃力の 50%。相手が 5 の内)
//  - 月光の長剣: 月の上か夜は +15%
//  - 望月の大鎚: 4 度目ごとに満月の一撃 (×1.6、まわり 3 の敵を押す)
//  - 朔の杖: 打った相手を 2 秒影に縛る (遅く)。縛られた相手への傷 +17%
//  - 星落ちの弓: 矢が当たった所へ 0.8 秒後に星が落ちる (まわり 2.5 の敵に当たった傷の 40%)
//  - 朔影の短剣: 打つたびに影の印。3 つで弾けて攻撃力の 90% (少し遅れて)
//  - 月暈の鈴杖: 打つと月暈 5 秒。打つ傷 +10%、月暈のあいだに打つたびにまわり 6 の仲間の体力 1
//  - 満月の環: 月の上か夜、3 秒ごと (満月 2 秒ごと) に体力 1
//  - 月の涙: 30 秒のあいだ、次に受ける傷を一度 0 に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonGearEvents {
    static final ResourceLocation SET2 = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moon_walker_set2_toughness");
    public static final String TEAR = SkyAbyss.MODID + ":moon_tear";
    static final String STAFF_TIDE = SkyAbyss.MODID + ":moon_staff_ebb";
    static final String BLADE_COUNT = SkyAbyss.MODID + ":moon_blade_count";
    static final String BELL_MARK = SkyAbyss.MODID + ":moon_star_mark";
    static final String FULL_COUNT = SkyAbyss.MODID + ":moon_full_count";
    static final String NEW_BIND = SkyAbyss.MODID + ":moon_new_bind";
    static final String UMBRA = SkyAbyss.MODID + ":moon_umbra_marks";
    static final String HALO = SkyAbyss.MODID + ":moon_halo";
    // 落ちる星 (星落ちの弓): 落ちる tick・所・傷・打った人
    record Star(long at, Vec3 pos, float damage, int attacker) {
    }

    private static final List<Star> STARS = new ArrayList<>();
    // 遅れて当たる残像 (太刀と弓): 当たる tick・相手・傷・打った人
    record Echo(long at, int target, float damage, int attacker, boolean arrow) {
    }

    private static final List<Echo> ECHOES = new ArrayList<>();

    private MoonGearEvents() {
    }

    public static boolean armorSet(LivingEntity e) {
        return MoonWalkerArmorItem.piecesWorn(e) >= 4;
    }

    static boolean holds(Player p, Object item) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() == item && GearLevel.meets(p, hand);
    }

    // 受ける傷の倍率: 4 部位で落下 ×0.5、月の上 ×0.9
    public static double takenFactor(LivingEntity target, DamageSource source) {
        double m = 1;
        if (target instanceof Player p && armorSet(p) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.is(DamageTypeTags.IS_FALL)) {
                m *= MoonGear.SET4_FALL_TAKEN;
            }
            if (p.level().dimension() == MoonWorld.LEVEL) {
                m *= MoonGear.SET4_MOON_TAKEN;
            }
        }
        // 星の印 (だれの傷も)
        if (target.getPersistentData().getLong(BELL_MARK) > target.level().getGameTime()) {
            m *= 1 + MoonGear.BELL_MARK;
        }
        return m;
    }

    // 相手の背から刺したか (相手の向きの後ろ 110 度)
    public static boolean fromBehind(LivingEntity target, Vec3 from) {
        Vec3 to = from.subtract(target.position());
        Vec3 fwd = Vec3.directionFromRotation(0, target.yBodyRot);
        return new Vec3(to.x, 0, to.z).normalize().dot(fwd) < -0.57;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        // 月の涙: 次の傷を一度 0 に
        if (target instanceof Player p && p.getPersistentData().getLong(TEAR) > level.getGameTime() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            p.getPersistentData().remove(TEAR);
            event.setCanceled(true);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0.08);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f);
            return;
        }
        double m = takenFactor(target, source);
        if (source.getEntity() instanceof Player player) {
            if (source.getDirectEntity() == player) {
                m *= melee(level, player, target, event.getAmount());
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                    && arrow.getWeaponItem().getItem() == MoonGearSetup.BOW.get() && !arrow.getPersistentData().getBoolean("skyabyss_echo")) {
                ECHOES.add(new Echo(level.getGameTime() + MoonGear.BOW_ECHO_TICKS, target.getId(), (float) (event.getAmount() * MoonGear.BOW_ECHO),
                        player.getId(), true));
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                    && arrow.getWeaponItem().getItem() == MoonGearSetup.STARFALL_BOW.get()) {
                STARS.add(new Star(level.getGameTime() + MoonGear.STARFALL_TICKS, target.position(), (float) (event.getAmount() * MoonGear.STARFALL_SHARE),
                        player.getId()));
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 6, target.getZ(), 6, 0.2, 0.4, 0.2, 0.01);
            }
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 近接の武器の効果。与える傷の倍率を返す
    static double melee(ServerLevel level, Player player, LivingEntity target, float amount) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || !GearLevel.meets(player, hand)) {
            return 1;
        }
        long now = level.getGameTime();
        if (hand.getItem() == MoonGearSetup.SWORD.get()) {
            return level.dimension() == MoonWorld.LEVEL || level.isNight() ? 1 + MoonGear.SWORD_MOONLIT : 1;
        }
        if (hand.getItem() == MoonGearSetup.FULL_HAMMER.get()) {
            int n = player.getPersistentData().getInt(FULL_COUNT) + 1;
            if (n < MoonGear.FULL_HAMMER_EVERY) {
                player.getPersistentData().putInt(FULL_COUNT, n);
                return 1;
            }
            player.getPersistentData().putInt(FULL_COUNT, 0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3),
                    q -> q != player && q.isAlive() && !(q instanceof Player))) {
                Vec3 d = e.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                e.push(d.x * 0.8, 0.3, d.z * 0.8);
                e.hurtMarked = true;
            }
            level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.2), target.getZ(), 24, 1.2, 0.1, 1.2, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
            return 1 + MoonGear.FULL_HAMMER_BLOW;
        }
        if (hand.getItem() == MoonGearSetup.NEW_STAFF.get()) {
            boolean bound = target.getPersistentData().getLong(NEW_BIND) > now;
            target.getPersistentData().putLong(NEW_BIND, now + MoonGear.NEW_STAFF_BIND_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MoonGear.NEW_STAFF_BIND_TICKS, 1, true, false));
            level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY(0.1), target.getZ(), 6, 0.4, 0.05, 0.4, 0.01);
            return bound ? 1 + MoonGear.NEW_STAFF_BOUND : 1;
        }
        if (hand.getItem() == MoonGearSetup.UMBRA_DAGGER.get()) {
            int n = target.getPersistentData().getInt(UMBRA) + 1;
            if (n >= MoonGear.UMBRA_MARKS) {
                n = 0;
                float dmg = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * MoonGear.UMBRA_BURST);
                ECHOES.add(new Echo(now + 4, target.getId(), dmg, player.getId(), false));
            }
            target.getPersistentData().putInt(UMBRA, n);
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.5), target.getZ(), 3 + 3 * n, 0.2, 0.3, 0.2, 0.01);
            return 1;
        }
        if (hand.getItem() == MoonGearSetup.HALO_BELL.get()) {
            if (player.getPersistentData().getLong(HALO) > now) {
                for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(MoonGear.HALO_RADIUS),
                        q -> q != player && q.isAlive() && !q.isSpectator())) {
                    ally.heal(1f);
                }
            }
            player.getPersistentData().putLong(HALO, now + MoonGear.HALO_TICKS);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.2, player.getZ(), 8, 0.6, 0.05, 0.6, 0.01);
            return 1 + MoonGear.HALO_BONUS;
        }
        if (hand.getItem() == MoonGearSetup.HAMMER.get()) {
            return MoonGear.heavier(target.getHealth(), target.getMaxHealth(), player.getHealth(), player.getMaxHealth()) ? 1 + MoonGear.HAMMER_BONUS : 1;
        }
        if (hand.getItem() == MoonGearSetup.STAFF.get()) {
            boolean ebb = player.getPersistentData().getBoolean(STAFF_TIDE);
            player.getPersistentData().putBoolean(STAFF_TIDE, !ebb);
            if (ebb) {
                player.heal((float) (amount * MoonGear.STAFF_EBB_HEAL));
                level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
                return 1;
            }
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.03);
            return 1 + MoonGear.STAFF_SWELL;
        }
        if (hand.getItem() == MoonGearSetup.DAGGER.get()) {
            if (fromBehind(target, player.position())) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.3, 0.2, 0.1);
                return 1 + MoonGear.DAGGER_BACK;
            }
            return 1;
        }
        if (hand.getItem() == MoonGearSetup.BELL.get()) {
            target.getPersistentData().putLong(BELL_MARK, level.getGameTime() + MoonGear.BELL_MARK_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MoonGear.BELL_MARK_TICKS, 0, true, false));
            return 1;
        }
        if (hand.getItem() == MoonGearSetup.BLADE.get()) {
            int n = player.getPersistentData().getInt(BLADE_COUNT) + 1;
            if (n >= MoonGear.BLADE_EVERY) {
                n = 0;
                float dmg = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * MoonGear.BLADE_ECHO);
                ECHOES.add(new Echo(level.getGameTime() + MoonGear.BLADE_ECHO_TICKS, target.getId(), dmg, player.getId(), false));
            }
            player.getPersistentData().putInt(BLADE_COUNT, n);
            return 1;
        }
        return 1;
    }

    // 残像の当たり (太刀・弓)
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        stars(level, now);
        if (ECHOES.isEmpty()) {
            return;
        }
        for (Iterator<Echo> it = ECHOES.iterator(); it.hasNext();) {
            Echo e = it.next();
            if (e.at() > now) {
                continue;
            }
            Entity t = level.getEntity(e.target());
            Entity a = level.getEntity(e.attacker());
            if (t == null && a == null) {
                // ほかのワールドの残像は、そのワールドの tick で
                if (now - e.at() > 200) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            if (!(t instanceof LivingEntity target) || !target.isAlive() || !(a instanceof Player player)) {
                continue;
            }
            if (!e.arrow() && target.distanceTo(player) > MoonGear.BLADE_ECHO_REACH) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().indirectMagic(target, player), e.damage());
            level.sendParticles(e.arrow() ? ParticleTypes.END_ROD : ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(),
                    e.arrow() ? 10 : 1, 0.2, 0.3, 0.2, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.9f, e.arrow() ? 1.6f : 0.8f);
        }
    }

    // 落ちる星: 所のまわりの敵 (人でない生き物) に傷
    static void stars(ServerLevel level, long now) {
        for (Iterator<Star> it = STARS.iterator(); it.hasNext();) {
            Star s = it.next();
            if (s.at() > now) {
                continue;
            }
            Entity a = level.getEntity(s.attacker());
            if (!(a instanceof Player player)) {
                if (now - s.at() > 200) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            hitStar(level, player, s.pos(), s.damage());
        }
    }

    public static int hitStar(ServerLevel level, Player player, Vec3 pos, float damage) {
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(MoonGear.STARFALL_RADIUS),
                q -> q.isAlive() && !(q instanceof Player) && q.position().distanceTo(pos) <= MoonGear.STARFALL_RADIUS + q.getBbWidth() / 2)) {
            e.invulnerableTime = 0;
            e.hurt(player.damageSources().indirectMagic(e, player), damage);
            hit++;
        }
        level.sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y + 0.3, pos.z, 20, 0.8, 0.2, 0.8, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 3, pos.z, 12, 0.1, 1.5, 0.1, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        return hit;
    }

    public static int pendingEchoes() {
        return ECHOES.size();
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 == 15) {
            toggle(player, Attributes.ARMOR_TOUGHNESS, SET2, MoonWalkerArmorItem.piecesWorn(player) >= 2 ? MoonGear.SET2_TOUGHNESS : 0);
        }
        // 満月の環
        ServerLevel level = player.serverLevel();
        boolean onMoon = level.dimension() == MoonWorld.LEVEL;
        int period = onMoon && MoonMob.fullMoon(level) ? MoonGear.RING_PERIOD_FULL : MoonGear.RING_PERIOD;
        if (player.tickCount % period == 7 && player.getHealth() < player.getMaxHealth() && Accessories.worn(player, MoonGearSetup.RING.get())
                && GearLevel.meets(player, new ItemStack(MoonGearSetup.RING.get())) && (onMoon || level.isNight())) {
            player.heal(1f);
        }
    }

    private static void toggle(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
