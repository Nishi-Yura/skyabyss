package jp.revati.skyabyss.item;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 装備の効果のうち、持ち主の状態で変わるもの:
//  - 共鳴の炉で鍛えた段階に応じた、攻撃力 (武器の 3%/段)・強靭 (0.3/段) の上乗せ
//  - 水路の顎牙: 水の中で攻撃速度 +30%
//  - 苔の護石: 装飾の枠に付けて苔の上にいると体力が回復する
//  - 岩鯨の補強板: 装飾の枠に付けて、岩鯨の甲殻を2部位で強靭 +3、4部位でノックバック耐性
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GearEvents {
    // 1段階ごとの上乗せ (03 の 9章、2026-10-17 改)
    //  武器: その武器の攻撃力 (素手の 1 を含む) の 3%。+10 で 1.3 倍。速い武器だけが伸びる足し算をやめた
    //  防具: 強靭だけ 1 部位 +0.3。防御力はバニラの効きの上限 (20) の手前にあり、1 足すごとに受ける傷が 1 割以上減るので足さない
    public static final double ATTACK_RATE_PER_LEVEL = 0.03;
    public static final double TOUGHNESS_PER_LEVEL = 0.3;
    // 岩鯨の補強板: 甲殻 2 部位で強靭 +3 (以前は防御 +2)
    public static final double PLATING_TOUGHNESS = 3;

    private static final ResourceLocation ENHANCE = id("enhance");
    private static final ResourceLocation FANG_WATER = id("fang_water");
    private static final ResourceLocation PLATING_ARMOR = id("plating_armor");
    private static final ResourceLocation PLATING_TOUGHNESS_ID = id("plating_toughness");
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
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(ENHANCE, enhanceAttack(stack.getItem(), level), AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        } else if (stack.getItem() instanceof ArmorItem armor) {
            // 修飾子の ID は部位ごとに分ける。同じ ID だと、二つ目の部位が一つ目を上書きし、外すと両方消える
            String part = armor.getEquipmentSlot().getName();
            EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
            event.addModifier(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id("enhance_toughness_" + part), level * TOUGHNESS_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE), slot);
        }
    }

    // 武器の攻撃力 (素手の 1 + 品の既定の修飾子)。品の既定の値から読むので、数値を変えると古いセーブの品にもそのまま効く
    public static double weaponAttack(Item item) {
        double total = 1;
        for (ItemAttributeModifiers.Entry e : item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().equals(Attributes.ATTACK_DAMAGE) && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                total += e.modifier().amount();
            }
        }
        return total;
    }

    // 強化 level 段の攻撃力の上乗せ
    public static double enhanceAttack(Item item, int level) {
        return Math.round(weaponAttack(item) * ATTACK_RATE_PER_LEVEL * level * 100) / 100.0;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 水路の顎牙: 手に持って水の中にいる間だけ、速く振れる (毎tick見る。水から出たらすぐ戻す)
        boolean fangInWater = player.getMainHandItem().getItem() instanceof WaterwayFangItem && player.isInWaterOrBubble()
                && GearLevel.meets(player, player.getMainHandItem());
        toggle(player, Attributes.ATTACK_SPEED, FANG_WATER, fangInWater ? 0.3 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (player.tickCount % 20 != 0) {
            return;
        }
        // 岩鯨の補強板: 装飾の枠に付けていれば、甲殻の部位数に応じて守りが増す (持ち物にあるだけでは効かない。03 の 10章)
        boolean plating = Accessories.worn(player, ModItems.STONE_WHALE_PLATING.get());
        int pieces = plating ? StoneWhaleArmorItem.piecesWorn(player) : 0;
        toggle(player, Attributes.ARMOR_TOUGHNESS, PLATING_TOUGHNESS_ID, pieces >= 2 ? PLATING_TOUGHNESS : 0, AttributeModifier.Operation.ADD_VALUE);
        // 以前の「防御 +2」の修飾子が残っていたら外す (一時的な修飾子なので本来は残らないが、念のため)
        toggle(player, Attributes.ARMOR, PLATING_ARMOR, 0, AttributeModifier.Operation.ADD_VALUE);
        toggle(player, Attributes.KNOCKBACK_RESISTANCE, PLATING_KNOCKBACK, pieces >= 4 ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);

        // 苔の護石: 苔のブロックか苔のカーペットの上で、毎秒 0.5 回復
        if (player.getHealth() < player.getMaxHealth() && onMoss(player)
                && Accessories.worn(player, ModItems.MOSS_TALISMAN.get())) {
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
