package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

// 人の盾を弱める (2026-10-23 の試遊: 盾を構えればボスの技も全部止まって強すぎた)。バニラは正面の傷を全部止める。
//  ふつうの敵の傷は KEEP (全部) 止め、大物 (最大体力 BIG_HEALTH 以上。心臓・中ボス・番人) と、持ち主のいない傷 (床の予告の技など) は KEEP_BIG だけ。
//  珊瑚の盾はどちらも CORAL_BONUS だけ多く止める。
//  一度に BREAK_DAMAGE 以上を受けると、盾は BREAK_TICKS のあいだ構えられない (斧で叩かれたときと同じ待ち)。
public final class ShieldRules {
    public static final float KEEP = 1.0f;   // 2026-10-23 午後: 構えているのに傷を受けるのは違和感、と。ふつうの敵は全部止める
    public static final float KEEP_BIG = 0.6f;
    public static final float BIG_HEALTH = 150f;
    public static final float BREAK_DAMAGE = 10f;
    public static final int BREAK_TICKS = 40;
    public static final float CORAL_BONUS = 0.15f;

    private ShieldRules() {
    }

    // 止める割合
    public static float keep(@javax.annotation.Nullable net.minecraft.world.entity.Entity attacker) {
        if (attacker instanceof LivingEntity l && l.getMaxHealth() < BIG_HEALTH) {
            return KEEP;
        }
        return attacker == null || attacker instanceof LivingEntity ? KEEP_BIG : KEEP;
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Events {
        @SubscribeEvent
        static void onBlock(LivingShieldBlockEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || !event.getBlocked()) {
                return;
            }
            float incoming = event.getOriginalBlockedDamage();
            var src = event.getDamageSource();
            // 矢などは撃った者で決める
            net.minecraft.world.entity.Entity by = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
            // 珊瑚の盾 (巨神の盾) は少しだけ多く止める
            float k = keep(by) + (player.getUseItem().getItem() instanceof jp.revati.skyabyss.titan.tide.gear.CoralShieldItem ? CORAL_BONUS : 0f);
            event.setBlockedDamage(incoming * Math.min(1f, k));
            if (incoming >= BREAK_DAMAGE) {
                ItemStack held = player.getUseItem();
                if (!held.isEmpty()) {
                    player.getCooldowns().addCooldown(held.getItem(), BREAK_TICKS);
                }
                player.stopUsingItem();
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f);
            }
        }
    }
}
