package jp.revati.skyabyss.titan.domain;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 領域の仕掛けの箱 (見えない Interaction) を、叩いた (左クリック) / 触れた (右クリック) 入口。
// 箱が読み込みで戻ってきたら (落ちたあとの残り) 片付ける
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DomainNodeEvents {
    private DomainNodeEvents() {
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && HeartDomain.onHit(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && HeartDomain.onHit(player, event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(HeartDomain.NODE_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HeartDomain.forgetAll();
    }
}
