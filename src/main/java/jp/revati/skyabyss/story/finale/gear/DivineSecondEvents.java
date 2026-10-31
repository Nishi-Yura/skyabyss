package jp.revati.skyabyss.story.finale.gear;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.story.ch6.gear.MoonGearEvents;
import jp.revati.skyabyss.tower.gear.TowerArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

// 神器の二本目・防具・装飾の効き方 (docs/design/50 の 4.5.1。必要 Lv に届いているときだけ。数値は DivineGear):
//   羽の番人の大剣  傷を受けると番の構え 3 秒: 次の一撃 +40%
//   灰打ちの大鎚    打った相手を 3 秒溶かす (その相手が受ける傷はだれのものでも +10%)
//   雲の瞼の杖      打った相手に凝視の糸 3 秒。糸のある相手が 6 より離れると光の柱 (攻撃力の 80%)
//   霞鳥の弓        当たった所に 3 秒の霞 (中の敵は遅く、受ける傷 +10%)
//   灯蛾の短剣      打つと灯の粉 (5 秒、相手が光る)。粉のある相手を背から刺すと ×1.5、粉が消える
//   鐘守の鈴杖      4 度目ごとに沈黙の鐘: まわり 5 の敵を 2 秒遅く、まわり 5 の仲間 (自分も) の体力 2
//   鐘守の重鎧 4    受ける傷 ×0.92。傷を受けると鐘が鳴り、まわり 4 の敵を押し返す (8 秒に一度)
//   霞渡りの装束 4  速さ +10%。傷を受けると 1 秒霞に隠れる (姿が消え、狙っていた敵が見失う。10 秒に一度)
//   灯守の法衣 4    マナの戻り +35% (WildGearEvents.manaRegenBonus から)。技を使うと 4 秒、まわり 6 の仲間の受ける傷 ×0.92
//   巡礼の羽飾り    落ちる傷が無くなる。空中で打つ傷 +8%
//   約束の小鈴      倒れた仲間を起こす速さ ×1.5 (Downed から)。起こした仲間に守り 3 秒 (受ける傷 ×0.7)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DivineSecondEvents {
    static final String GUARD = SkyAbyss.MODID + ":divine_warden_guard";
    static final String MELT = SkyAbyss.MODID + ":divine_ash_melt";
    static final String MOTH = SkyAbyss.MODID + ":divine_moth_dust";
    static final String MIST_MARK = SkyAbyss.MODID + ":divine_mist_mark";
    static final String KEEPER_COUNT = SkyAbyss.MODID + ":divine_keeper_count";
    static final String BELL_CD = SkyAbyss.MODID + ":divine_bellkeeper_cd";
    static final String MIST_CD = SkyAbyss.MODID + ":divine_mistwalker_cd";
    static final String LAMP_WARD = SkyAbyss.MODID + ":divine_lamp_ward";
    static final String CHIME_WARD = SkyAbyss.MODID + ":divine_chime_ward";
    static final ResourceLocation MIST_SPEED = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "divine_mistwalker_speed");
    static final DustParticleOptions DAWN = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.65f), 1.0f);
    static final DustParticleOptions SKY = new DustParticleOptions(new Vector3f(0.75f, 0.85f, 1.0f), 1.2f);

    // 凝視の糸 (相手・打った人・切れる tick) と霞 (所・切れる tick)
    record Gaze(int target, int attacker, long until) {
    }

    record Mist(ServerLevel level, Vec3 pos, long until) {
    }

    private static final List<Gaze> GAZES = new ArrayList<>();
    private static final List<Mist> MISTS = new ArrayList<>();
    // 効き方が足した傷を入れているあいだ (その傷からまた効き方を起こさない)
    private static boolean inner;

    private DivineSecondEvents() {
    }

    static boolean full(LivingEntity e, String set) {
        return TowerArmorItem.piecesWorn(e, set) >= 4;
    }

    static boolean wears(Player p, Item item) {
        return Accessories.worn(p, item) && GearLevel.meets(p, new ItemStack(item));
    }

    // ---------------------------------------------------------------- よそから呼ぶ
    public static double manaRegenBonus(Player p) {
        return full(p, DivineGear.LAMPKEEPER) ? DivineGear.LAMPKEEPER_REGEN : 0;
    }

    // 約束の小鈴: 一 tick おきに 1 進みを足す (平均 ×1.5)
    public static int reviveExtra(Player holder) {
        return wears(holder, DivineGearSetup.CHIME.get()) && holder.tickCount % 2 == 0 ? 1 : 0;
    }

    public static void onRevived(ServerPlayer victim, ServerPlayer reviver) {
        if (reviver != null && wears(reviver, DivineGearSetup.CHIME.get())) {
            victim.getPersistentData().putLong(CHIME_WARD, victim.level().getGameTime() + DivineGear.CHIME_WARD_TICKS);
            ((ServerLevel) victim.level()).sendParticles(DAWN, victim.getX(), victim.getY() + 1, victim.getZ(), 20, 0.4, 0.6, 0.4, 0);
        }
    }

    public static void onSkillCast(ServerPlayer p) {
        if (!full(p, DivineGear.LAMPKEEPER)) {
            return;
        }
        long until = p.level().getGameTime() + DivineGear.LAMPKEEPER_WARD_TICKS;
        for (Player ally : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(DivineGear.LAMPKEEPER_RADIUS), q -> q.isAlive() && !q.isSpectator())) {
            ally.getPersistentData().putLong(LAMP_WARD, until);
            ((ServerLevel) p.level()).sendParticles(DAWN, ally.getX(), ally.getY() + 2.1, ally.getZ(), 6, 0.3, 0.05, 0.3, 0);
        }
    }

    // ---------------------------------------------------------------- 受ける傷と与える傷
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (inner || !(target.level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        long now = level.getGameTime();
        var source = event.getSource();
        double m = 1;
        var td = target.getPersistentData();
        if (td.getLong(MELT) > now) {
            m *= 1 + DivineGear.ASH_MELT;
        }
        if (td.getLong(MIST_MARK) > now) {
            m *= 1 + DivineGear.MIST_TAKEN;
        }
        if (target instanceof Player p && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.is(DamageTypeTags.IS_FALL) && wears(p, DivineGearSetup.CHARM.get())) {
                event.setCanceled(true);
                return;
            }
            if (full(p, DivineGear.BELLKEEPER)) {
                m *= DivineGear.BELLKEEPER_TAKEN;
            }
            if (td.getLong(LAMP_WARD) > now) {
                m *= DivineGear.LAMPKEEPER_WARD;
            }
            if (td.getLong(CHIME_WARD) > now) {
                m *= DivineGear.CHIME_WARD;
            }
        }
        if (source.getEntity() instanceof Player player && player != target) {
            if (!player.onGround() && wears(player, DivineGearSetup.CHARM.get())) {
                m *= 1 + DivineGear.CHARM_AIR;
            }
            if (source.getDirectEntity() == player) {
                m *= melee(level, player, target, now);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                    && arrow.getWeaponItem().getItem() == DivineGearSetup.MIST.get() && GearLevel.meets(player, arrow.getWeaponItem())) {
                MISTS.add(new Mist(level, target.position(), now + DivineGear.MIST_TICKS));
                level.sendParticles(SKY, target.getX(), target.getY() + 0.5, target.getZ(), 30, 1.2, 0.4, 1.2, 0);
            }
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    static double melee(ServerLevel level, Player player, LivingEntity target, long now) {
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty() || !GearLevel.meets(player, hand)) {
            return 1;
        }
        var pd = player.getPersistentData();
        var td = target.getPersistentData();
        Item it = hand.getItem();
        if (it == DivineGearSetup.WARDEN.get()) {
            if (pd.getLong(GUARD) > now) {
                pd.remove(GUARD);
                level.sendParticles(DAWN, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.4, 0.3, 0);
                return 1 + DivineGear.WARDEN_GUARD;
            }
            return 1;
        }
        if (it == DivineGearSetup.ASH.get()) {
            td.putLong(MELT, now + DivineGear.ASH_MELT_TICKS);
            level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.6), target.getZ(), 3, 0.3, 0.3, 0.3, 0);
            return 1;
        }
        if (it == DivineGearSetup.EYELID.get()) {
            GAZES.removeIf(g -> g.target() == target.getId() && g.attacker() == player.getId());
            GAZES.add(new Gaze(target.getId(), player.getId(), now + DivineGear.EYELID_TICKS));
            return 1;
        }
        if (it == DivineGearSetup.MOTH.get()) {
            if (td.getLong(MOTH) > now && MoonGearEvents.fromBehind(target, player.position())) {
                td.remove(MOTH);
                target.removeEffect(MobEffects.GLOWING);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                return DivineGear.MOTH_BACK;
            }
            td.putLong(MOTH, now + DivineGear.MOTH_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, DivineGear.MOTH_TICKS, 0, true, false));
            level.sendParticles(DAWN, target.getX(), target.getY(0.8), target.getZ(), 8, 0.3, 0.3, 0.3, 0);
            return 1;
        }
        if (it == DivineGearSetup.KEEPER.get()) {
            int n = pd.getInt(KEEPER_COUNT) + 1;
            if (n >= DivineGear.KEEPER_EVERY) {
                n = 0;
                AABB box = player.getBoundingBox().inflate(DivineGear.KEEPER_RADIUS);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, q -> q.isAlive() && !(q instanceof Player))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false));
                }
                for (Player ally : level.getEntitiesOfClass(Player.class, box, q -> q.isAlive() && !q.isSpectator())) {
                    ally.heal(DivineGear.KEEPER_HEAL);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9f, 1.4f);
                level.sendParticles(DAWN, player.getX(), player.getY() + 0.3, player.getZ(), 30, 2.5, 0.1, 2.5, 0);
            }
            pd.putInt(KEEPER_COUNT, n);
        }
        return 1;
    }

    // 傷を受けたあと: 番の構え・鐘守の鐘・霞に隠れる
    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() <= 0 || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        var pd = p.getPersistentData();
        if (p.getMainHandItem().getItem() == DivineGearSetup.WARDEN.get() && GearLevel.meets(p, p.getMainHandItem())) {
            pd.putLong(GUARD, now + DivineGear.WARDEN_GUARD_TICKS);
        }
        if (full(p, DivineGear.BELLKEEPER) && pd.getLong(BELL_CD) <= now) {
            pd.putLong(BELL_CD, now + DivineGear.BELLKEEPER_COOLDOWN);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(DivineGear.BELLKEEPER_PUSH_RADIUS),
                    q -> q.isAlive() && !(q instanceof Player))) {
                Vec3 d = e.position().subtract(p.position()).multiply(1, 0, 1);
                d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
                e.push(d.x * 1.0, 0.3, d.z * 1.0);
                e.hurtMarked = true;
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 0.8f);
            level.sendParticles(DAWN, p.getX(), p.getY() + 1, p.getZ(), 24, 1.5, 0.6, 1.5, 0);
        }
        if (full(p, DivineGear.MISTWALKER) && pd.getLong(MIST_CD) <= now) {
            pd.putLong(MIST_CD, now + DivineGear.MISTWALKER_COOLDOWN);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DivineGear.MISTWALKER_HIDE, 0, true, false));
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), q -> q.getTarget() == p)) {
                m.setTarget(null);
            }
            level.sendParticles(SKY, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 0.9, 0.8, 0);
        }
    }

    // 霞渡りの装束の速さ (1 秒ごとに付け外し)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || p.tickCount % 20 != 7) {
            return;
        }
        toggle(p, Attributes.MOVEMENT_SPEED, MIST_SPEED, full(p, DivineGear.MISTWALKER), DivineGear.MISTWALKER_SPEED);
    }

    static void toggle(Player p, Holder<Attribute> attr, ResourceLocation id, boolean on, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        boolean has = inst.getModifier(id) != null;
        if (on && !has) {
            inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!on && has) {
            inst.removeModifier(id);
        }
    }

    // 凝視の糸と霞 (毎 tick)
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || GAZES.isEmpty() && MISTS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Gaze g : List.copyOf(GAZES)) {
            Entity t = level.getEntity(g.target());
            Entity a = level.getEntity(g.attacker());
            if (g.until() < now || !(t instanceof LivingEntity target) || !target.isAlive() || !(a instanceof Player p)) {
                if (t != null || a != null || g.until() < now) {
                    GAZES.remove(g);
                }
                continue;
            }
            if (now % 4 == 0) {
                Vec3 from = p.getEyePosition();
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
                for (int i = 1; i < 6; i++) {
                    Vec3 q = from.lerp(to, i / 6.0);
                    level.sendParticles(ParticleTypes.WAX_ON, q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            }
            if (target.distanceTo(p) > DivineGear.EYELID_RANGE) {
                GAZES.remove(g);
                for (double h = 0; h < 6; h += 0.5) {
                    level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + h, target.getZ(), 1, 0.15, 0.1, 0.15, 0.01);
                }
                inner = true;
                try {
                    target.invulnerableTime = 0;
                    target.hurt(level.damageSources().indirectMagic(p, p), (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * DivineGear.EYELID_PILLAR));
                } finally {
                    inner = false;
                }
            }
        }
        if (now % 10 == 0) {
            for (Mist m : List.copyOf(MISTS)) {
                if (m.until() < now) {
                    MISTS.remove(m);
                    continue;
                }
                if (m.level() != level) {
                    continue;
                }
                level.sendParticles(SKY, m.pos().x, m.pos().y + 0.4, m.pos().z, 10, DivineGear.MIST_RADIUS * 0.5, 0.3, DivineGear.MIST_RADIUS * 0.5, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(m.pos(), m.pos()).inflate(DivineGear.MIST_RADIUS, 2, DivineGear.MIST_RADIUS),
                        q -> q.isAlive() && !(q instanceof Player))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, true, false));
                    e.getPersistentData().putLong(MIST_MARK, now + 20);
                }
            }
        }
    }
}
