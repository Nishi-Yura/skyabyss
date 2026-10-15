package jp.revati.skyabyss.item;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModArmorMaterials;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の甲殻の防具。四つそろえて身につけると「岩の守り」(耐性) がつく
@EventBusSubscriber(modid = SkyAbyss.MODID)
public class StoneWhaleArmorItem extends ArmorItem implements ModGear {
    public StoneWhaleArmorItem(Type type, Properties properties) {
        super(ModArmorMaterials.STONE_WHALE, type, properties.durability(type.getDurability(30)));
    }


    @Override
    public GearTier tier() {
        return GearTier.RARE;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier(), "item.skyabyss.stone_whale_armor.desc", true);
    }

    // 身につけている岩鯨の甲殻の数
    public static int piecesWorn(Player player) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).getItem() instanceof StoneWhaleArmorItem) {
                count++;
            }
        }
        return count;
    }

    private static boolean wearsFullSet(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(player.getItemBySlot(slot).getItem() instanceof StoneWhaleArmorItem)) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 40 == 0 && wearsFullSet(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, false, true));
        }
    }
}
