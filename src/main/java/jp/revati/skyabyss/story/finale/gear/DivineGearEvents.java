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

// 神器 (終章の一本目) のまわり: 約束の唄 (書き換え) の盾の円の内は受ける傷 ×0.6、心核をしゃがんで使うと欠片に砕く (DivineGear.breakCore)。
// 武器の大きな能力は item/mythic/MythicAbilities (2026-11-02 に、前の小さな効き方から作り替えた)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DivineGearEvents {
    // 約束の唄の盾の円 (書き換え。LegendRewrites が置く)
    record Circle(ServerLevel level, Vec3 c, double r, long until) {
    }

    private static final List<Circle> CIRCLES = new ArrayList<>();
    public static final double CIRCLE_TAKEN = 0.6;

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
        double m = 1;
        // 受ける側: 約束の唄の盾の円
        if (target instanceof Player p && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !CIRCLES.isEmpty() && inCircle(level, p.position())) {
            m *= CIRCLE_TAKEN;
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
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
    }
}
