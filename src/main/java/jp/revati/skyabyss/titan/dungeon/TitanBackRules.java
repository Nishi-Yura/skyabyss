package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 岩鯨の背の決まりごと。巨神の体は、よそ者の手を拒む。
// サバイバルでは、ブロックを置く・壊す、水を撒く、ワープや跳躍で近道する、といったことができない。
// (謎解きと試練を、正面から越えてもらうため。クリエイティブでは制限しない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanBackRules {
    private TitanBackRules() {
    }

    private static boolean restricted(LevelAccessor level, Player player) {
        return player != null && !player.isCreative() && !player.isSpectator()
                && level instanceof net.minecraft.world.level.Level l && l.dimension() == TitanTravel.TITAN_BACK;
    }

    private static boolean isShortcutItem(ItemStack stack) {
        return stack.getItem() instanceof BucketItem
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.CHORUS_FRUIT)
                || stack.is(Items.WIND_CHARGE)
                || stack.is(Items.FIREWORK_ROCKET)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.FLINT_AND_STEEL)
                || stack.is(Items.FIRE_CHARGE)
                || stack.is(Items.POWDER_SNOW_BUCKET)
                || stack.is(Items.END_CRYSTAL)
                || stack.is(Items.TNT)
                || stack.is(Items.TNT_MINECART)
                || stack.is(Items.RESPAWN_ANCHOR);
    }

    private static void deny(Player player) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(ModText.deny("message.skyabyss.titan_refuses"), true);
        }
    }

    // 掘ろうとしても、ひびすら入らない
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (restricted(event.getEntity().level(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // 爆発でも、巨神の体は崩れない (誰が起こしたものでも)
    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == TitanTravel.TITAN_BACK) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (restricted(event.getLevel(), event.getPlayer())) {
            event.setCanceled(true);
            deny(event.getPlayer());
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && restricted(event.getLevel(), player)) {
            event.setCanceled(true);
            deny(player);
        }
    }

    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (restricted(event.getLevel(), event.getEntity()) && isShortcutItem(event.getItemStack())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onUseItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (restricted(event.getLevel(), event.getEntity()) && isShortcutItem(event.getItemStack())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    // 西の翼 (アスレチックの谷と休み場)。ここでは跳躍とゆっくり落ちる効果を消す
    private static final List<Box> WEST_WING = List.of(W0, WA, W1, WB, W2, WC);

    // 背中ではエリトラで滑空できない。西の翼では、跳躍とゆっくり落ちる効果 (薬・食べ物) を消す (足場の近道になるため)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !restricted(player.level(), player)) {
            return;
        }
        if (player.isFallFlying()) {
            player.stopFallFlying();
            if (player.tickCount % 20 == 0) {
                deny(player);
            }
        }
        if (player.tickCount % 5 != 0) {
            return;
        }
        BlockPos pos = player.blockPosition();
        for (Box box : WEST_WING) {
            if (box.contains(pos)) {
                if (player.hasEffect(MobEffects.JUMP) || player.hasEffect(MobEffects.SLOW_FALLING)) {
                    player.removeEffect(MobEffects.JUMP);
                    player.removeEffect(MobEffects.SLOW_FALLING);
                    deny(player);
                }
                return;
            }
        }
    }
}
