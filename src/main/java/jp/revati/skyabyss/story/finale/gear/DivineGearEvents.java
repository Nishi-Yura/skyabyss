package jp.revati.skyabyss.story.finale.gear;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.rpg.skill.SkillEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 神器の固有の効果 (docs/design/50 の 4.5。手に持って必要 Lv に届いているとき):
//   天穹の剣       4 度目ごとに、0.4 秒後に光の手が相手を打つ (攻撃力の ×0.6。まわり 2 も)
//   七つ鐘の大鎚   3 度目ごとに鐘が鳴る (相手のまわり 3.5 の敵に攻撃力の ×0.3 と鈍り 1 秒)
//   暁の灯杖       打った相手を 4 秒灯す (灯された相手は、だれの傷も +12%)
//   巡礼の羽弓     矢が当たると、0.8 秒後に羽根が三本落ちる (当たった傷の 30% ずつ)
//   巻き戻りの短剣 打った 1.5 秒後に、同じ相手がもう一度打たれる (当たった傷の 35%)
//   約束の鈴杖     打つと、まわり 5 の仲間に約束の守り 3 秒 (受ける傷 ×0.85)
// ほか: 心核をしゃがんで使うと欠片に砕く (DivineGear.breakCore)。約束の唄 (書き換え) の盾の円の内は受ける傷 ×0.6
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DivineGearEvents {
    static final String SWORD_COUNT = "skyabyss_divine_sword";
    static final String HAMMER_COUNT = "skyabyss_divine_hammer";
    static final String LIT = "skyabyss_divine_lit";
    static final String PROMISE = "skyabyss_divine_promise";
    // 約束の唄の盾の円 (書き換え。LegendRewrites が置く)
    record Circle(ServerLevel level, Vec3 c, double r, long until) {
    }

    private static final List<Circle> CIRCLES = new ArrayList<>();
    public static final double CIRCLE_TAKEN = 0.6;
    // 神器が足した傷を当てている間 (その傷からまた効果を起こさない)
    private static boolean inner;

    private DivineGearEvents() {
    }

    public static void addCircle(ServerLevel level, Vec3 c, double r, int ticks) {
        CIRCLES.add(new Circle(level, c, r, level.getGameTime() + ticks));
    }

    static boolean inCircle(ServerLevel level, Vec3 p) {
        long now = level.getGameTime();
        for (Iterator<Circle> it = CIRCLES.iterator(); it.hasNext();) {
            Circle c = it.next();
            if (c.until < now) {
                it.remove();
            } else if (c.level == level && Math.hypot(p.x - c.c.x, p.z - c.c.z) <= c.r && Math.abs(p.y - c.c.y) < 3) {
                return true;
            }
        }
        return false;
    }

    static boolean holds(Player p, net.minecraft.world.item.Item item) {
        ItemStack hand = p.getMainHandItem();
        return hand.getItem() == item && GearLevel.meets(p, hand);
    }

    static float attack(Player p) {
        return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        long now = level.getGameTime();
        double m = 1;
        // 受ける側: 約束の守り・約束の唄の盾の円
        if (target instanceof Player p && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (p.getPersistentData().getLong(PROMISE) > now) {
                m *= DivineGear.BELL_PROMISE;
            }
            if (!CIRCLES.isEmpty() && inCircle(level, p.position())) {
                m *= CIRCLE_TAKEN;
            }
        }
        // 灯された相手
        if (target.getPersistentData().getLong(LIT) > now) {
            m *= 1 + DivineGear.STAFF_LIT;
        }
        if (inner) {
            // 足した傷: 効果は起こさない
        } else if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && !(target instanceof Player)) {
            melee(level, player, target, event.getAmount());
        } else if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() == DivineGearSetup.BOW.get() && !(target instanceof Player)) {
            feathers(level, player, target.position(), (float) (event.getAmount() * DivineGear.BOW_FEATHERS));
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    // 神器が足す傷 (効果を起こさない)
    static void extra(ServerLevel level, ServerPlayer player, LivingEntity e, float dmg) {
        inner = true;
        try {
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(player), dmg);
        } finally {
            inner = false;
        }
    }

    static void melee(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
        long now = level.getGameTime();
        if (holds(player, DivineGearSetup.SWORD.get())) {
            int n = player.getPersistentData().getInt(SWORD_COUNT) + 1;
            if (n >= DivineGear.SWORD_EVERY) {
                n = 0;
                float dmg = attack(player) * (float) DivineGear.SWORD_SLAM;
                Vec3 c = target.position();
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 5, c.z, 10, 0.4, 0.6, 0.4, 0.02);
                SkillEffects.later(level, 8, () -> {
                    level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                    level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 16, 1.0, 0.1, 1.0, 0.04);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.6f);
                    for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(2, 2, 2),
                            q -> q.isAlive() && !(q instanceof Player))) {
                        extra(level, player, e, dmg);
                    }
                });
            }
            player.getPersistentData().putInt(SWORD_COUNT, n);
        } else if (holds(player, DivineGearSetup.HAMMER.get())) {
            int n = player.getPersistentData().getInt(HAMMER_COUNT) + 1;
            if (n >= DivineGear.HAMMER_EVERY) {
                n = 0;
                float dmg = attack(player) * (float) DivineGear.HAMMER_RING;
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 1.2f);
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.WAX_ON, target.getX() + Math.cos(a) * DivineGear.HAMMER_RADIUS, target.getY() + 0.3,
                            target.getZ() + Math.sin(a) * DivineGear.HAMMER_RADIUS, 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(DivineGear.HAMMER_RADIUS),
                        q -> q != target && q.isAlive() && !(q instanceof Player))) {
                    extra(level, player, e, dmg);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), player);
                }
            }
            player.getPersistentData().putInt(HAMMER_COUNT, n);
        } else if (holds(player, DivineGearSetup.STAFF.get())) {
            target.getPersistentData().putLong(LIT, now + DivineGear.STAFF_LIT_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, DivineGear.STAFF_LIT_TICKS, 0, true, false));
            level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
        } else if (holds(player, DivineGearSetup.DAGGER.get())) {
            float dmg = (float) (amount * DivineGear.DAGGER_ECHO);
            int id = target.getId();
            level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
            SkillEffects.later(level, DivineGear.DAGGER_ECHO_TICKS, () -> {
                if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
                    extra(level, player, e, dmg);
                    level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY(0.5), e.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
                }
            });
        } else if (holds(player, DivineGearSetup.BELL.get())) {
            for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(DivineGear.BELL_RADIUS), q -> q.isAlive() && !q.isSpectator())) {
                ally.getPersistentData().putLong(PROMISE, now + DivineGear.BELL_PROMISE_TICKS);
            }
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 2.2, player.getZ(), 6, 0.8, 0.05, 0.8, 0.01);
        }
    }

    // 羽根が三本落ちる
    static void feathers(ServerLevel level, ServerPlayer player, Vec3 at, float dmg) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 6, at.z, 6, 0.6, 0.3, 0.6, 0.01);
        SkillEffects.later(level, DivineGear.BOW_FEATHER_TICKS, () -> {
            for (int k = 0; k < DivineGear.BOW_FEATHER_COUNT; k++) {
                double a = k * Math.PI * 2 / DivineGear.BOW_FEATHER_COUNT;
                Vec3 p = at.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.4, p.z, 6, 0.1, 0.3, 0.1, 0.05);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p, p).inflate(1.5, 2, 1.5),
                        q -> q.isAlive() && !(q instanceof Player))) {
                    extra(level, player, e, dmg);
                    break;
                }
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8f, 1.2f);
        });
    }

    // 心核をしゃがんで使うと欠片に砕く
    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && p.isShiftKeyDown() && DivineGear.isCore(event.getItemStack())) {
            if (DivineGear.breakCore(p, event.getItemStack())) {
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    // サーバーが止まったとき (story/finale/FinaleReset)
    public static void resetAll() {
        CIRCLES.clear();
        inner = false;
    }
}
