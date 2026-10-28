package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 戦いの中の回復 (docs/design/47 の 2.4)。主と戦っている間は、食べ物と薬をひとつ口にしたら WAIT_TICKS のあいだ次を口にできない。
// 戦っている = 主 (BossFooting.planted) が RANGE 以内にいて、主か本人が FRESH_TICKS 以内に傷を受けた。戦いの外は今までどおり
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FightRations {
    private FightRations() {
    }

    public static final int WAIT_TICKS = 400;
    public static final double RANGE = 48;
    public static final int FRESH_TICKS = 200;

    private static final Map<UUID, Long> NEXT = new HashMap<>();

    static boolean ration(ItemStack stack) {
        return stack.getFoodProperties(null) != null || stack.getItem() instanceof PotionItem;
    }

    public static boolean inFight(ServerPlayer p) {
        List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE),
                e -> e.isAlive() && e != p && BossOpenings.planted(e));
        for (LivingEntity boss : near) {
            boolean bossHurt = boss.getLastHurtByMob() != null && boss.tickCount - boss.getLastHurtByMobTimestamp() < FRESH_TICKS;
            boolean meHurt = p.getLastHurtByMob() == boss && p.tickCount - p.getLastHurtByMobTimestamp() < FRESH_TICKS;
            if (bossHurt || meHurt) {
                return true;
            }
        }
        return false;
    }

    // あと何 tick 待つか (待たなくてよければ 0)
    public static long waitLeft(ServerPlayer p) {
        Long next = NEXT.get(p.getUUID());
        return next == null ? 0 : Math.max(0, next - p.level().getGameTime());
    }

    @SubscribeEvent
    static void onStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ration(event.getItem())) {
            return;
        }
        long left = waitLeft(p);
        if (left > 0 && inFight(p)) {
            event.setCanceled(true);
            jp.revati.skyabyss.notice.Notice.bar(p, jp.revati.skyabyss.ModText.deny("message.skyabyss.fight_ration_wait", (left + 19) / 20));
        }
    }

    @SubscribeEvent
    static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer p && ration(event.getItem()) && inFight(p)) {
            NEXT.put(p.getUUID(), p.level().getGameTime() + WAIT_TICKS);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        NEXT.clear();
    }
}
