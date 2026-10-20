package jp.revati.skyabyss.weapon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 武器の「当たる瞬間」をサーバーで動きに合わせる。
// バニラは、クリックした tick にダメージを入れる。本 mod の武器は、動きに溜め (予備動作) があるので、クリックを一度預かり、
// MoveSet の strike (振りの何割か) の tick に、そのまま Player.attack を呼び直す。ダメージの式・クールダウン・各武器の固有効果 (hurtEnemy) は
// 全部バニラの経路のままなので、攻撃力・攻撃速度・1 秒あたりの与ダメージは変わらず、最初の一撃が 2〜8 tick 遅れるだけ。
//  - クリックした tick に、攻撃の強さの数え (attackStrengthTicker) を預けて 0 に戻す (クライアントも同じに戻す。連打で強さが貯まらない)。
//    当たる tick に預けた値を戻して attack を呼び、そのあと「クリックからの経過」へ置き直す
//  - 当たる tick に、相手が生きていて、手の届く範囲 (範囲 + MoveSet の reach の余裕) にいて、持ち物が同じなら当たる。外れたら空振り
//  - 当たったら、持ち主と見ている人へ StrikePayload (ヒットストップ・画面の揺れ)
// 偽のプレイヤー (FakePlayer など ServerPlayer そのものでないもの) と、通信のない人は対象にしない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WeaponStrikes {
    private record Pending(ServerPlayer player, int target, long clickAt, long strikeAt, int savedTicker, Item item, MoveSet set, int step) {
    }

    private static final List<Pending> QUEUE = new ArrayList<>();
    // 預けたクリックを呼び直している間は、AttackEntityEvent を素通しにする
    private static boolean replaying;
    // 連撃の数え: 持ち主 → [前の振りの始まりの tick, 前の段]
    private static final Map<UUID, long[]> COMBO = new HashMap<>();

    private WeaponStrikes() {
    }

    // 自己テストだけが true にする: 偽のプレイヤー (FakePlayer) でも預かりの経路を通す
    static boolean testAllowFake;

    static boolean applies(Object player) {
        if (player == null) {
            return false;
        }
        boolean real = player.getClass() == ServerPlayer.class;
        boolean fake = testAllowFake && player instanceof net.neoforged.neoforge.common.util.FakePlayer;
        return (real || fake) && ((ServerPlayer) player).connection != null;
    }

    // 動きのある品 (弓・盾も含む)
    public static MoveSet setOfAny(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SkyAbyss.MODID.equals(id.getNamespace()) ? MoveSets.get(id.getPath()) : null;
    }

    // 一撃を預ける武器。弓と盾 (strike が 0 以下) は、バニラの経路のまま
    static MoveSet setOf(ItemStack stack) {
        MoveSet ms = setOfAny(stack);
        return ms == null || ms.strike <= 0f ? null : ms;
    }

    // 今の振りの段を数える (クリックと、空振りの振りの両方から呼ばれる。同じ振りは 1 度だけ数える)
    static int registerSwing(ServerPlayer p, MoveSet ms, int duration, long now) {
        long[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new long[] {Long.MIN_VALUE / 2, -1});
        if (now - c[0] <= 1 && c[1] >= 0) {
            return (int) c[1];
        }
        int step = ComboTracker.next((int) c[1], (int) Math.min(Integer.MAX_VALUE, now - c[0]), duration, ms.window, ms.steps.size());
        c[0] = now;
        c[1] = step;
        return step;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onAttack(AttackEntityEvent event) {
        if (replaying || !(event.getEntity() instanceof ServerPlayer p) || !applies(p)) {
            return;
        }
        MoveSet ms = setOf(p.getMainHandItem());
        if (ms == null) {
            return;
        }
        Entity target = event.getTarget();
        long now = p.level().getGameTime();
        int duration = ms.durationFor(p.getAttributeValue(Attributes.ATTACK_SPEED));
        int step = registerSwing(p, ms, duration, now);
        QUEUE.add(new Pending(p, target.getId(), now, now + ms.strikeTick(duration), p.attackStrengthTicker, p.getMainHandItem().getItem(), ms, step));
        // 預けたので、クライアントと同じに強さの数えを戻す
        p.attackStrengthTicker = 0;
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        // 空振りの振りも、連撃の段として数える (クライアントはバニラの振りの合図で数えるので、同じに見る)
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (applies(p) && p.swinging && p.swingTime == 0 && p.swingingArm == InteractionHand.MAIN_HAND) {
                MoveSet ms = setOf(p.getMainHandItem());
                if (ms != null) {
                    registerSwing(p, ms, ms.durationFor(p.getAttributeValue(Attributes.ATTACK_SPEED)), now);
                }
            }
        }
        if (QUEUE.isEmpty()) {
            return;
        }
        for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext();) {
            Pending pe = it.next();
            if (pe.strikeAt() > now) {
                continue;
            }
            it.remove();
            ServerPlayer p = pe.player();
            if (p != null && !p.isRemoved()) {
                strike(p, pe, now);
            }
        }
    }

    private static void strike(ServerPlayer p, Pending pe, long now) {
        if (!p.isAlive() || p.isSpectator() || p.getMainHandItem().getItem() != pe.item()) {
            return;
        }
        Entity target = p.level().getEntity(pe.target());
        if (target == null || !target.isAlive() || !p.canInteractWithEntity(target.getBoundingBox(), pe.set().reach)) {
            return;
        }
        int before = target instanceof LivingEntity le ? (int) (le.getHealth() * 10f) : 0;
        replaying = true;
        p.attackStrengthTicker = pe.savedTicker();
        try {
            p.attack(target);
        } finally {
            replaying = false;
            // クリックからの経過に置き直す (クライアントがクリックで戻した数えと同じ)
            p.attackStrengthTicker = (int) Math.max(0, now - pe.clickAt());
        }
        boolean hit = target instanceof LivingEntity le && (le.getHealth() * 10f < before || !le.isAlive());
        if (hit) {
            MoveSet.Step st = pe.set().step(pe.step());
            if ((st.hitstop > 0 || st.shake > 0f) && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new StrikePayload(p.getId(), pe.step(), st.hitstop, Math.round(st.shake * 100f)));
            }
        }
    }

    // 預かっているクリックの数 (自己テスト用)
    public static int pending() {
        return QUEUE.size();
    }
}
