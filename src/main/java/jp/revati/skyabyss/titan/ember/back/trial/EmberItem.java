package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.List;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 熾 (live_coal。13 の 4.3節): 火守りの谷の敵を倒すと落ちる、まだ赤い炭。持って火床に右クリックすると火が +8。
// だれでも拾える。持っていると熱が 1 秒に +1 (Hearth が見る)。谷を出ると消える
public class EmberItem extends Item {
    public EmberItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ModText.hint("item.skyabyss.live_coal.desc"));
    }
}
