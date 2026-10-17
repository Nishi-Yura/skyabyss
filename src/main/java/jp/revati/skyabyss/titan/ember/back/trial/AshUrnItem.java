package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.List;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// 灰の壺 (13 の 4.1節): 右クリックで、向いている先 4 ブロック以内の溶岩の面に灰をまく (3 x 3 が冷え殻になる)。灰は 5 回分。
// 島の灰溜まり (灰色の羊毛) に触れると満ちる。湖を出ると消える (台に戻る)。残りは耐久の棒で見せる (満ち = 5、空 = 0)
public class AshUrnItem extends Item {
    public AshUrnItem(Properties properties) {
        super(properties);
    }

    public static int charges(ItemStack stack) {
        return Math.max(0, AshPath.URN_CHARGES - stack.getDamageValue());
    }

    public static void setCharges(ItemStack stack, int charges) {
        stack.setDamageValue(AshPath.URN_CHARGES - Math.max(0, Math.min(AshPath.URN_CHARGES, charges)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            AshPath.useUrn(server, sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ModText.hint("item.skyabyss.ash_urn.desc", charges(stack), AshPath.URN_CHARGES));
        tooltip.add(ModText.hint("item.skyabyss.ash_urn.desc2"));
    }
}
