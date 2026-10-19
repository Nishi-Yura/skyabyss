package jp.revati.skyabyss.item.affix;

import java.util.Map;
import java.util.WeakHashMap;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.item.GearLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 特性の効き目:
//  - 数値の特性 (攻撃力・攻撃速度・最大マナ・防具の強靭・最大体力・移動速度・ノックバック耐性) は装備の修飾子として足す。
//    修飾子の ID は部位ごとに分ける (同じ ID だと、二つの部位の値が上書きし合う)
//  - 守り: 着ている防具の合計だけ、受ける傷を減らす (上限 10%)。防具とエンチャントのあとに掛ける
//  - 渇き: 手の武器で直接与えた傷の一部を回復する (0.25 秒に一度まで)
//  - 冴え: 技の待ちを縮める (SkillManager が cooldownScale を呼ぶ)
//  - 特性のない装備 (第1段階のセーブ・コマンドで出したもの) は、持ち物に入ったときに抽選する
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AffixEvents {
    private static final int THIRST_INTERVAL = 5;
    private static final float THIRST_MAX_HEAL = 2f;
    // 冴えの上限 (伝説の最大 12.5% より少し上。今は一つの武器にしか付かないので届かない)
    private static final double KEEN_CAP = 0.15;
    private static final Map<Player, Long> lastThirst = new WeakHashMap<>();

    private AffixEvents() {
    }

    private static ResourceLocation id(AffixType type, String slot) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "affix/" + type.id + "_" + slot);
    }

    @SubscribeEvent
    static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        AffixType.Slot kind = AffixRules.slotOf(stack.getItem());
        if (kind == null) {
            return;
        }
        EquipmentSlotGroup group;
        String slotName;
        if (kind == AffixType.Slot.WEAPON) {
            group = EquipmentSlotGroup.MAINHAND;
            slotName = "mainhand";
        } else if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            group = EquipmentSlotGroup.bySlot(slot);
            slotName = slot.getName();
        } else {
            // 盾: 左手に持っているあいだ
            group = EquipmentSlotGroup.OFFHAND;
            slotName = "offhand";
        }
        for (Affix affix : AffixRules.effective(stack)) {
            AffixType t = affix.kind();
            if (t == null) {
                continue;
            }
            switch (t) {
                case SHARP -> add(event, Attributes.ATTACK_DAMAGE, t, slotName, affix.value(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group);
                case SWIFT -> add(event, Attributes.ATTACK_SPEED, t, slotName, affix.value(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group);
                case RESONANT, CALM -> add(event, Rpg.MAX_MANA, t, slotName, affix.value(), AttributeModifier.Operation.ADD_VALUE, group);
                case BULWARK -> add(event, Attributes.ARMOR_TOUGHNESS, t, slotName, affix.value(), AttributeModifier.Operation.ADD_VALUE, group);
                case VIGOR -> add(event, Attributes.MAX_HEALTH, t, slotName, affix.value(), AttributeModifier.Operation.ADD_VALUE, group);
                case STRIDE -> add(event, Attributes.MOVEMENT_SPEED, t, slotName, affix.value(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, group);
                case STEADY -> add(event, Attributes.KNOCKBACK_RESISTANCE, t, slotName, affix.value(), AttributeModifier.Operation.ADD_VALUE, group);
                default -> {
                    // 渇き・冴え・守りは出来事で効かせる
                }
            }
        }
    }

    private static void add(ItemAttributeModifierEvent event, Holder<Attribute> attribute, AffixType t, String slot, double amount,
            AttributeModifier.Operation op, EquipmentSlotGroup group) {
        event.addModifier(attribute, new AttributeModifier(id(t, slot), amount, op), group);
    }

    // ---------------------------------------------------------------- 守り・渇き・冴え
    // 着ている防具の守りの合計 (上限つき)
    public static double ward(LivingEntity entity) {
        double sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (AffixRules.slotOf(stack.getItem()) == AffixType.Slot.ARMOR) {
                sum += AffixRules.value(stack, AffixType.WARD);
            }
        }
        // 左手の盾の守り (必要 Lv に届いていれば)
        ItemStack off = entity.getOffhandItem();
        if (off.getItem() instanceof net.minecraft.world.item.ShieldItem && AffixRules.slotOf(off.getItem()) == AffixType.Slot.ARMOR
                && GearLevel.usable(entity, off)) {
            sum += AffixRules.value(off, AffixType.WARD);
        }
        return Math.min(AffixRules.WARD_CAP, sum);
    }

    // 技の待ちに掛ける倍率 (手の武器の冴え)
    public static double cooldownScale(Player player) {
        ItemStack hand = player.getMainHandItem();
        if (AffixRules.slotOf(hand.getItem()) != AffixType.Slot.WEAPON || !GearLevel.meets(player, hand)) {
            return 1;
        }
        return 1 - Math.min(KEEN_CAP, AffixRules.value(hand, AffixType.KEEN));
    }

    public static int scaleCooldown(Player player, int ticks) {
        return Math.max(1, (int) Math.round(ticks * cooldownScale(player)));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        double ward = ward(player);
        if (ward > 0 && event.getNewDamage() > 0) {
            event.setNewDamage((float) (event.getNewDamage() * (1 - ward)));
        }
    }

    @SubscribeEvent
    static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player
                || event.getEntity() == player || event.getNewDamage() <= 0 || !player.isAlive()) {
            return;
        }
        ItemStack hand = player.getMainHandItem();
        if (AffixRules.slotOf(hand.getItem()) != AffixType.Slot.WEAPON || !GearLevel.meets(player, hand)) {
            return;
        }
        double thirst = AffixRules.value(hand, AffixType.THIRST);
        if (thirst <= 0 || player.getHealth() >= player.getMaxHealth()) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = lastThirst.get(player);
        if (last != null && now - last < THIRST_INTERVAL) {
            return;
        }
        lastThirst.put(player, now);
        player.heal((float) Math.min(THIRST_MAX_HEAL, event.getNewDamage() * thirst));
    }

    // ---------------------------------------------------------------- 特性のない装備
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            boolean rolled = AffixRules.ensureRolled(stack, player.getRandom());
            // 穴・世代の刻み (古いセーブの装備も、手にしたときに一度だけ穴を抽選する。item/extra/GearFreedom)
            rolled |= jp.revati.skyabyss.item.extra.GearFreedom.ensure(stack, player.getRandom());
            if (rolled) {
                inventory.setChanged();
            }
        }
    }
}
