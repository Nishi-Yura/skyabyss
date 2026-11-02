package jp.revati.skyabyss.story.ch4;

import java.util.List;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

// 灰の冷やし水。飲むと余燼の背の熱 (EmberHeat) が 40 下がり、耐火 30 秒
public class AshCoolerItem extends Item {
    public static final float COOL = 40f;
    public static final int FIRE_TICKS = 600;

    public AshCoolerItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            drink(sp);
        }
        // 空き瓶は残さない (使い道のない品を渡さない。2026-10-27)
        if (!(entity instanceof Player p) || !p.isCreative()) {
            stack.shrink(1);
        }
        return stack;
    }

    // 飲んだときの効き目 (自己テストからも呼ぶ)
    public static void drink(ServerPlayer player) {
        EmberHeat.cool(player, COOL);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_TICKS, 0, false, true, true));
        Notice.narrate(player, "message.skyabyss.ch4g.cooler.drink");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
