package jp.revati.skyabyss.story.ch6;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 月の軽い重さ (docs/design/46 の 3.1)。月のディメンションにいる生き物 (人も敵も) に、一時の補正を付ける。出ると外す:
//   重さ x0.4 (落ちが遅い)、跳び +40% (高く跳ぶ)、落ちても平気な高さ +8
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MoonGravity {
    public static final double GRAVITY = -0.6;
    public static final double JUMP = 0.4;
    public static final double SAFE_FALL = 8.0;
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "moon_gravity");

    private MoonGravity() {
    }

    @SubscribeEvent
    static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity e) || e.level().isClientSide() || e.tickCount % 10 != 0) {
            return;
        }
        apply(e, e.level().dimension() == MoonWorld.LEVEL);
    }

    public static void apply(LivingEntity e, boolean on) {
        set(e, Attributes.GRAVITY, on ? GRAVITY : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(e, Attributes.JUMP_STRENGTH, on ? JUMP : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(e, Attributes.SAFE_FALL_DISTANCE, on ? SAFE_FALL : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(LivingEntity e, Holder<Attribute> attr, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) {
            return;
        }
        if (amount == 0) {
            inst.removeModifier(ID);
        } else {
            inst.addOrUpdateTransientModifier(new AttributeModifier(ID, amount, op));
        }
    }
}
