package jp.revati.skyabyss.titan.ember.gear;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

// 熾火の角杯 (食べ物、アンコモン): 熾を溶かした温かい飲み物。回復 6・飽和 0.8、いつでも飲める。
//  - 余燼の背で飲むと、3 分 熱のたまり方 ×0.6 (EmberGear.heatGain。火炎耐性 0.5 とは強いほうだけ)
//  - ほかの場所で飲むと、30 秒の火炎耐性
// 背中の効き目は効果の印ではなく、持ち主の記録 (飲んだ時刻) で持つ (新しい効果の ID を作らないため)。残りは手に持つと出る
public class EmberHornItem extends Item {
    private static final String UNTIL = SkyAbyss.MODID + ":ember_horn_until";

    public EmberHornItem(Properties properties) {
        super(properties.food(new FoodProperties.Builder().nutrition(EmberGear.HORN_NUTRITION).saturationModifier(EmberGear.HORN_SATURATION)
                .alwaysEdible().build()));
    }

    // 背中の角杯が効いているか
    public static boolean active(Player player) {
        return remainingTicks(player) > 0;
    }

    public static long remainingTicks(Player player) {
        long until = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(UNTIL);
        return Math.max(0, until - player.level().getGameTime());
    }

    // 背中で飲んだ (テストからも呼ぶ)
    public static void drinkOnBack(Player player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putLong(UNTIL, player.level().getGameTime() + EmberGear.HORN_BACK_TICKS);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void clear(Player player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.remove(UNTIL);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            if (level.dimension() == EmberIds.BACK) {
                drinkOnBack(player);
                Notice.bar(player, ModText.lore("tooltip.skyabyss.ember.horn_drunk"));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, EmberGear.HORN_SURFACE_FIRE_TICKS, 0));
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.ember_horn.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
