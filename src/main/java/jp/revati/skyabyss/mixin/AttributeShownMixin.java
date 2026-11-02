package jp.revati.skyabyss.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import jp.revati.skyabyss.Hp;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.extensions.IAttributeExtension;

// 装備の説明の「攻撃力」「最大体力」の数を、画面の倍率 (Hp.SHOWN = 10 倍) で出す (2026-11-02 の声「攻撃力の迫力がない」)。
// NeoForge の属性の行はどれも toValueComponent を通るので、足す値 (と緑の基の値) だけ倍にする。割合の修飾はそのまま。中の値は変えない
@Mixin(Attribute.class)
public abstract class AttributeShownMixin implements IAttributeExtension {
    @Override
    public MutableComponent toValueComponent(@Nullable AttributeModifier.Operation op, double value, TooltipFlag flag) {
        if (IAttributeExtension.isNullOrAddition(op)) {
            Object self = this;
            if (self == Attributes.ATTACK_DAMAGE.value() || self == Attributes.MAX_HEALTH.value()) {
                value = Hp.shown(value);
            }
            return Component.translatable("neoforge.value.flat", FORMAT.format(value));
        }
        return Component.translatable("neoforge.value.percent", FORMAT.format(value * 100));
    }
}
