package jp.revati.skyabyss.item;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;

// 弓は矢がなくても撃てる (2026-10-28 の試遊「弓に、矢が無くても出せるようにして。矢いらない」)。
// 人が弓で射るとき、ふつうの矢 (と矢がないとき) は印のついた矢を一本渡す (手持ちの矢は減らない)。光る矢などの特別な矢は今までどおり使って減る。
// 印の矢で射た矢は拾えない (矢が増え続けないように)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FreeArrows {
    static final String TAG = "skyabyss_free_arrow";

    private FreeArrows() {
    }

    public static ItemStack arrow() {
        ItemStack s = new ItemStack(Items.ARROW);
        CompoundTag t = new CompoundTag();
        t.putBoolean(TAG, true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return s;
    }

    static boolean free(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean(TAG);
    }

    @SubscribeEvent
    static void onProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectileWeaponItemStack().getItem() instanceof BowItem)) {
            return;
        }
        ItemStack found = event.getProjectileItemStack();
        if (found.isEmpty() || found.is(Items.ARROW)) {
            event.setProjectileItemStack(arrow());
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof AbstractArrow a && free(a.getPickupItemStackOrigin())) {
            a.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
    }
}
