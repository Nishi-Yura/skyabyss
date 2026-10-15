package jp.revati.skyabyss.item;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 装備の効果のうち、持ち主の状態で変わるもの:
//  - 共鳴の炉で鍛えた段階に応じた、攻撃力・防具の上乗せ
//  - 水路の顎牙: 水の中で攻撃速度 +30%
//  - 苔の護石: 苔の上にいると体力が回復する
//  - 岩鯨の補強板: 岩鯨の甲殻を2部位で防具 +2、4部位でノックバック耐性
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GearEvents {
    // 1段階ごとの上乗せ
    public static final double ATTACK_PER_LEVEL = 0.6;
    public static final double ARMOR_PER_LEVEL = 0.4;
    public static final double TOUGHNESS_PER_LEVEL = 0.2;

    private static final ResourceLocation ENHANCE = id("enhance");
    private static final ResourceLocation FANG_WATER = id("fang_water");
    private static final ResourceLocation PLATING_ARMOR = id("plating_armor");
    private static final ResourceLocation PLATING_KNOCKBACK = id("plating_knockback");

    private GearEvents() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    @SubscribeEvent
    static void onAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ModGear gear) || !gear.enhanceable()) {
            return;
        }
        int level = ModGear.enhance(stack);
        if (level <= 0) {
            return;
        }
        if (stack.getItem() instanceof SwordItem) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(ENHANCE, level * ATTACK_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        } else if (stack.getItem() instanceof ArmorItem armor) {
            // 修飾子の ID は部位ごとに分ける。同じ ID だと、二つ目の部位が一つ目を上書きし、外すと両方消える
            String part = armor.getEquipmentSlot().getName();
            EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
            event.addModifier(Attributes.ARMOR, new AttributeModifier(id("enhance_" + part), level * ARMOR_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE), slot);
            event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("enhance_toughness_" + part), level * TOUGHNESS_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE), slot);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 水路の顎牙: 手に持って水の中にいる間だけ、速く振れる (毎tick見る。水から出たらすぐ戻す)
        boolean fangInWater = player.getMainHandItem().getItem() instanceof WaterwayFangItem && player.isInWaterOrBubble();
        toggle(player, Attributes.ATTACK_SPEED, FANG_WATER, fangInWater ? 0.3 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (player.tickCount % 20 != 0) {
            return;
        }
        // 岩鯨の補強板: 持ち物にあれば、甲殻の部位数に応じて守りが増す
        boolean plating = player.getInventory().contains(new ItemStack(ModItems.STONE_WHALE_PLATING.get()));
        int pieces = plating ? StoneWhaleArmorItem.piecesWorn(player) : 0;
        toggle(player, Attributes.ARMOR, PLATING_ARMOR, pieces >= 2 ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
        toggle(player, Attributes.KNOCKBACK_RESISTANCE, PLATING_KNOCKBACK, pieces >= 4 ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);

        // 苔の護石: 苔のブロックか苔のカーペットの上で、毎秒 0.5 回復
        if (player.getHealth() < player.getMaxHealth() && onMoss(player)
                && player.getInventory().contains(new ItemStack(ModItems.MOSS_TALISMAN.get()))) {
            player.heal(0.5f);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.3, 0.1, 0.3, 0);
        }
    }

    private static boolean onMoss(Player player) {
        BlockPos feet = player.blockPosition();
        return isMoss(player.level().getBlockState(feet)) || isMoss(player.level().getBlockState(feet.below()));
    }

    private static boolean isMoss(BlockState state) {
        return state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET);
    }

    // amount が 0 なら外し、それ以外なら付け直す
    private static void toggle(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
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
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
