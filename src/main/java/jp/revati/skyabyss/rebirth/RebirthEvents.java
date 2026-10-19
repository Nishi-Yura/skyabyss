package jp.revati.skyabyss.rebirth;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.party.Parties;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 転生の効き目をゲームへつなぐ出来事 (傷・拾う範囲・ログイン・台座)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RebirthEvents {
    private RebirthEvents() {
    }

    // ---------------------------------------------------------------- 傷
    // 与える傷: 魂盤・祖の力・恩恵 (全ダメージ、飛び道具は少し上乗せ) と、低レベルの仲間と遊ぶときの合わせ (B21)。
    // 受ける傷: 岩の腕・恩恵の「吸収」。掛け算なので、ほかの出来事との順は問わない
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && event.getEntity() != attacker) {
            event.setAmount((float) (event.getAmount() * outgoingMultiplier(attacker, source.getDirectEntity() instanceof Projectile)));
        }
        if (event.getEntity() instanceof ServerPlayer victim && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            double cut = Rebirth.effects(victim).reduction();
            if (cut > 0) {
                event.setAmount((float) (event.getAmount() * (1 - cut)));
            }
        }
    }

    // 与える傷の倍率 (魂盤などの力 × 共闘の合わせ)
    public static double outgoingMultiplier(ServerPlayer attacker, boolean projectile) {
        RebirthRules.Effects e = Rebirth.effects(attacker);
        double m = 1 + e.damage() + (projectile ? e.projectile() : 0);
        return m * syncMultiplier(attacker);
    }

    // 転生者が低レベルの仲間と近くで遊ぶとき、傷の倍率だけが仲間の段に合わせて下がる (体力・報酬は変えない)。転生していない人は変わらない
    public static double syncMultiplier(ServerPlayer player) {
        if (Rebirth.count(player) <= 0) {
            return 1.0;
        }
        int low = lowestAllyLevel(player);
        return low <= 0 ? 1.0 : RebirthRules.syncMultiplier(Growth.get(player).level, low);
    }

    // 近くにいる (64 ブロック以内・同じディメンション) パーティの仲間のうち、いちばん低いレベル。仲間がいなければ 0
    public static int lowestAllyLevel(ServerPlayer player) {
        PartyData.Party party = Parties.of(player);
        if (party == null) {
            return 0;
        }
        int low = 0;
        for (ServerPlayer m : Parties.onlineMembers(player.server, party)) {
            if (m == player || m.isSpectator() || !m.isAlive() || m.level() != player.level()
                    || m.distanceToSqr(player) > Parties.SHARE_RANGE * Parties.SHARE_RANGE) {
                continue;
            }
            int lv = Growth.get(m).level;
            low = low == 0 ? lv : Math.min(low, lv);
        }
        return low;
    }

    // ---------------------------------------------------------------- 拾う範囲 (旅の腕・恩恵)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || player.tickCount % 5 != 3
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        double extra = Rebirth.effects(player).pickup();
        if (extra <= 0) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(1.0 + extra, 0.5 + extra * 0.5, 1.0 + extra);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (!item.isRemoved() && !item.hasPickUpDelay()) {
                item.playerTouch(player);
            }
        }
    }

    // ---------------------------------------------------------------- ログイン
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RebirthData d = Rebirth.data(player);
            d.sanitize(Rebirth.count(player));
            Rebirth.sync(player);
            Rebirth.applyModifiers(player);
        }
    }

    // ---------------------------------------------------------------- 台座
    // 岩鯨の鼓動の間の中央の台座 (眠る心臓の台座。強化深層岩 2 段) を右クリックすると、転生の画面が開く
    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getLevel().dimension() != TitanTravel.TITAN_BACK || !isWhalePedestal(event.getPos()) || player.isShiftKeyDown()) {
            return;
        }
        if (Rebirth.count(player) <= 0 && Growth.get(player).level < RebirthRules.MIN_LEVEL) {
            return;
        }
        event.setCanceled(true);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new RebirthOpenPayload());
    }

    public static boolean isWhalePedestal(BlockPos pos) {
        return pos.getX() == TitanBackLayout.HUB_CX && pos.getZ() == TitanBackLayout.HUB_CZ
                && pos.getY() >= TitanBackLayout.FLOOR + 1 && pos.getY() <= TitanBackLayout.FLOOR + 2;
    }
}
