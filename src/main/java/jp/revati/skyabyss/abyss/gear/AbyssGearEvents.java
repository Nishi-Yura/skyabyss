package jp.revati.skyabyss.abyss.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.story.ch5.AbyssPressure;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 深淵の装備の効果 (03 の 12 章)。
//  - 淵潜りの鎧: 2 部位で強靭 +3。4 部位「錨の足」: 深淵の上昇の重み ×0.6・闇のたまり ×0.8 (AbyssPressure の倍率)、どこでも暗い所 (明るさ 4 以下) で受ける傷 ×0.85
//    (耐性の効果があるときは重ねない。上限 ×0.8 相当の決まり)
//  - 灯り番の剣: 灯りの中で与える傷 +10% (闇の印は LampSwordItem)
//  - 鼓動の聴き筒: 4 秒ごとに 12 ブロック内の敵を 2 秒光らせる。深淵では闇のたまり ×0.85
//  - 測量士の錘: 深さで与える傷が増える (AbyssGear.plumbBonus)。落下の傷 ×0.7
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssGearEvents {
    static final ResourceLocation SET2 = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_diver_set2_toughness");

    static {
        AbyssPressure.WEIGHT_FACTORS.add(p -> armorSet(p) ? AbyssGear.SET4_WEIGHT : 1.0);
        AbyssPressure.VOICE_FACTORS.add(p -> (armorSet(p) ? AbyssGear.SET4_VOICE : 1.0) * (listenerWorn(p) ? AbyssGear.LISTENER_VOICE : 1.0));
    }

    private AbyssGearEvents() {
    }

    // 呼ぶだけで static の登録 (圧の倍率) が走る (AbyssGearSetup.register から)
    public static void init() {
    }

    public static boolean armorSet(LivingEntity e) {
        return AbyssDiverArmorItem.piecesWorn(e) >= 4;
    }

    public static boolean listenerWorn(Player p) {
        return Accessories.worn(p, AbyssGearSetup.LISTENER.get());
    }

    // 受ける傷の倍率: 4 部位で暗い所 ×0.85 (耐性の効果と重ねない)、錘で落下 ×0.7
    public static double takenFactor(LivingEntity target, DamageSource source) {
        double m = 1;
        if (target instanceof Player p) {
            if (armorSet(p) && AbyssGear.inDark(p) && !p.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                m *= AbyssGear.SET4_DARK_TAKEN;
            }
            if (source.is(DamageTypeTags.IS_FALL) && Accessories.worn(p, AbyssGearSetup.PLUMB.get())) {
                m *= AbyssGear.PLUMB_FALL_TAKEN;
            }
        }
        return m;
    }

    // 与える傷の倍率 (近接で手から): 灯り番の剣の灯り、測量士の錘の深さ
    public static double dealtFactor(Player player) {
        double m = 1 + AbyssGear.plumbBonus(player);
        ItemStack hand = player.getMainHandItem();
        if (hand.getItem() instanceof LampSwordItem && GearLevel.meets(player, hand) && AbyssGear.inLight(player)) {
            m *= 1 + AbyssGear.SWORD_LIGHT_BONUS;
        }
        return m;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getAmount() <= 0) {
            return;
        }
        DamageSource source = event.getSource();
        double m = takenFactor(target, source);
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            m *= dealtFactor(player);
        }
        if (m != 1) {
            event.setAmount((float) (event.getAmount() * m));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 == 13) {
            int pieces = AbyssDiverArmorItem.piecesWorn(player);
            toggle(player, Attributes.ARMOR_TOUGHNESS, SET2, pieces >= 2 ? AbyssGear.SET2_TOUGHNESS : 0);
        }
        if (player.tickCount % AbyssGear.LISTENER_PERIOD == 41 && listenerWorn(player) && GearLevel.meets(player, new ItemStack(AbyssGearSetup.LISTENER.get()))) {
            listen(player);
        }
    }

    // 鼓動の聴き筒: まわりの敵の鼓動を聞き、輪郭を光らせる (壁の向こうも)
    static int listen(ServerPlayer player) {
        double r = AbyssGear.LISTENER_RADIUS;
        int n = 0;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                e -> e.isAlive() && e instanceof Enemy && e.distanceToSqr(player) <= r * r)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, AbyssGear.LISTENER_GLOW_TICKS, 0, true, false));
            n++;
        }
        return n;
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
