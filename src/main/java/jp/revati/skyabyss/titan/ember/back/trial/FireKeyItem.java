package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.List;

import jp.revati.skyabyss.ModText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

// 火の鍵 (13 の 4.2節): 大炉で鍛える。焼き入れ前 (fire_key_hot) は 10 秒で冷えて割れる (鍛えた時刻を持つ)。
// 10 秒以内に焼き入れの槽へ入れると、焼き入れた鍵 (fire_key) になる。鍛え手の扉に差す
public class FireKeyItem extends Item {
    private final boolean hot;

    public FireKeyItem(Properties properties, boolean hot) {
        super(properties);
        this.hot = hot;
    }

    public boolean hot() {
        return hot;
    }

    // 焼き入れ前の鍵に鍛えた時刻 (ゲーム時間) を書く
    public static void stamp(ItemStack stack, long gameTime) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Forged", gameTime);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static long forgedAt(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || !data.contains("Forged") ? Long.MIN_VALUE : data.copyTag().getLong("Forged");
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ModText.hint(hot ? "item.skyabyss.fire_key_hot.desc" : "item.skyabyss.fire_key.desc"));
    }
}
