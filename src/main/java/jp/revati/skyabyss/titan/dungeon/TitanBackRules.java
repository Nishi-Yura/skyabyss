package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.TitanTravel;
import static jp.revati.skyabyss.titan.dungeon.TitanBackLayout.*;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

// 巨神の背の決まりごと。巨神の体は、よそ者の手を拒む。
// 背中のディメンションの一覧 (TitanBacks) のどれでも同じ決まりにする。巨神ごとの追加の禁止 (潮殻の背の舟など) は TitanBacks.extraBanned
// サバイバルでは、ブロックを置く・壊す、水を撒く、ワープや跳躍で近道する、といったことができない。
// (謎解きと試練を、正面から越えてもらうため。クリエイティブでは制限しない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanBackRules {
    private TitanBackRules() {
    }

    private static boolean restricted(LevelAccessor level, Player player) {
        return player != null && !player.isCreative() && !player.isSpectator()
                && level instanceof net.minecraft.world.level.Level l && TitanBacks.isBack(l.dimension());
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

    private static boolean banned(net.minecraft.world.level.Level level, ItemStack stack) {
        return isShortcutItem(stack) || TitanBacks.extraBanned(level.dimension(), stack);
    }

    private static void deny(Player player) {
        if (!player.level().isClientSide) {
            Notice.bar(player, ModText.deny("message.skyabyss.titan_refuses"));
        }
    }

    // 掘ろうとしても、ひびすら入らない
    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (restricted(event.getEntity().level(), event.getEntity())) {
            // 野営の道具は、持ち主 (と同じグループ) なら一瞬で外せる (外す処理は onBreak)
            if (event.getEntity() instanceof ServerPlayer sp && event.getPosition().isPresent()
                    && CampBlocks.at(sp.server, sp.level().dimension(), event.getPosition().get()) instanceof CampBlocks.Entry e
                    && CampBlocks.mayBreak(sp, e)) {
                event.setNewSpeed(100000f);
                return;
            }
            event.setCanceled(true);
        }
    }

    // 爆発でも、巨神の体は崩れない (誰が起こしたものでも)
    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Detonate event) {
        if (TitanBacks.isBack(event.getLevel().dimension())) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (restricted(event.getLevel(), event.getPlayer())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp && event.getLevel() instanceof ServerLevel sl) {
                CampBlocks.Entry camp = CampBlocks.at(sp.server, sl.dimension(), event.getPos());
                if (camp != null && CampBlocks.mayBreak(sp, camp)) {
                    CampBlocks.breakByPlayer(sp, sl, camp);
                    return;
                }
                if (camp != null) {
                    Notice.bar(sp, ModText.deny("message.skyabyss.camp.stranger"));
                    return;
                }
            }
            deny(event.getPlayer());
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && restricted(event.getLevel(), player)) {
            // 野営の道具 (作業台・かまど・たいまつ・チェストなど) は、決まりの中で置いてよい (CampBlocks)
            if (player instanceof ServerPlayer sp && event.getLevel() instanceof ServerLevel sl) {
                CampBlocks.Refusal why = CampBlocks.evaluate(sp, sl, event.getPos(), event.getPlacedBlock());
                if (why == null) {
                    CampBlocks.record(sp, sl, event.getPos(), event.getPlacedBlock());
                    return;
                }
                event.setCanceled(true);
                CampBlocks.refuse(sp, why);
                // 置けなかった: 手持ちが消えたように見えないよう、持ち物と置こうとした場所をクライアントへ送り直す
                List<BlockPos> touched = new java.util.ArrayList<>();
                touched.add(event.getPos());
                if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                    multi.getReplacedBlockSnapshots().forEach(snap -> touched.add(snap.getPos()));
                }
                CampBlocks.resync(sp, sl, touched);
                return;
            }
            event.setCanceled(true);
            deny(player);
        }
    }

    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (restricted(event.getLevel(), event.getEntity()) && banned(event.getLevel(), event.getItemStack())) {
            event.setCanceled(true);
            deny(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onUseItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (restricted(event.getLevel(), event.getEntity()) && banned(event.getLevel(), event.getItemStack())) {
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
        if (player.tickCount % 5 != 0 || player.level().dimension() != TitanTravel.TITAN_BACK) {
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
