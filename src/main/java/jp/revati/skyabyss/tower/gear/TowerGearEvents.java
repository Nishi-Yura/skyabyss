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

// 塔の防具・装飾の効き方 (docs/design/54 の 2.6。表は TowerGear)。必要 Lv に届いていないと効かない。
// 武器の大きな能力は item/mythic/MythicAbilities (2026-11-02 に作り替えた)
//  - 天秤の甲冑 4 部位: 受ける傷 ×0.90、体力が半分以下で強靭 +12 / 渡りの装束 4 部位: 速さ +10%、走っているあいだ受ける傷 ×0.90 /
//    光輪の衣 4 部位: マナの戻り +40%・技の待ち −8% (WildGearEvents の manaRegenBonus・cooldownScale から呼ぶ)
//  - 天の環: 体力が満ちているあいだ与える傷 +8% / 淵の鎖: 体力が 3 割以下で受ける傷 ×0.80
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TowerGearEvents {
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
}
