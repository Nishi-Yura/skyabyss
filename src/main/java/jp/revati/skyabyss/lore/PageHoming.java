package jp.revati.skyabyss.lore;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 地面の破れた頁と伝承の書は、拾える人のほうへ寄っていく (2026-11-02 の声「破れたページも、動き回ってたら手に入らない」)。
// 拾えない置き物 (野営地の帳面など、拾う待ちが続く物) は動かさない。
// 頁は人の頭の上から落ちる・敵が落とすので、歩き回っていると落ちた所を通り過ぎ、物陰や段差の下に残って見つからなかった。
//   - 落ちてから FALL_TICKS たつと、拾える人 (持ち主の決まった頁はその人、ほかは近い人) へ経験値の玉のように飛んでいく (重さなし・壁を抜けない)
//   - 寄り始めて STUCK_TICKS たっても拾われない (壁の向こう・下の段) か、持ち主が NEAR より遠くにいれば、持ち主の足もとへ移す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PageHoming {
    private PageHoming() {
    }

    static final int FALL_TICKS = 15;
    static final int STUCK_TICKS = 100;
    static final double NEAR = 24;
    // 持ち主が離れても追う距離 (これより遠い・別の世界なら何もしない。見送りの消し方は今までどおり)
    static final double FAR = 160;
    static final String HOMING_TAG = "skyabyss_page_homing";

    public static boolean isPage(ItemStack stack) {
        return stack.is(LoreSetup.LORE_PAGE.get()) || stack.is(LoreSetup.LORE_BOOK.get());
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 2 != 0 || level.players().isEmpty()) {
            return;
        }
        List<? extends ItemEntity> pages = level.getEntities(EntityType.ITEM, e -> e.isAlive() && isPage(e.getItem()));
        for (ItemEntity page : pages) {
            if (page.getAge() < FALL_TICKS || page.hasPickUpDelay() || page.getTags().contains(jp.revati.skyabyss.opening.site.CampBuilder.NOTEBOOK_TAG)) {
                continue;
            }
            ServerPlayer to = taker(level, page);
            if (to == null) {
                page.setNoGravity(false);
                continue;
            }
            home(page, to);
        }
    }

    // 拾える人: 持ち主が決まっていればその人 (同じ世界で FAR 以内)、なければ NEAR 以内の一番近い人
    @Nullable
    static ServerPlayer taker(ServerLevel level, ItemEntity page) {
        UUID target = page.getTarget();
        if (target != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(target);
            return p != null && p.level() == level && p.isAlive() && !p.isSpectator() && p.distanceToSqr(page) < FAR * FAR ? p : null;
        }
        ServerPlayer best = null;
        double bd = NEAR * NEAR;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(page);
            if (p.isAlive() && !p.isSpectator() && d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    public static void home(ItemEntity page, ServerPlayer to) {
        var tag = page.getPersistentData();
        long now = page.level().getGameTime();
        if (!tag.contains(HOMING_TAG)) {
            tag.putLong(HOMING_TAG, now);
        }
        Vec3 aim = to.position().add(0, to.getBbHeight() * 0.5, 0);
        Vec3 d = aim.subtract(page.position());
        double len = d.length();
        if (len > NEAR || now - tag.getLong(HOMING_TAG) > STUCK_TICKS) {
            // 遠い・引っかかった: 持ち主の足もとへ
            page.teleportTo(to.getX(), to.getY() + 0.3, to.getZ());
            page.setDeltaMovement(Vec3.ZERO);
            tag.putLong(HOMING_TAG, now);
            return;
        }
        if (len < 0.6) {
            return;
        }
        page.setNoGravity(true);
        double speed = Math.min(0.6, 0.12 + len * 0.04);
        page.setDeltaMovement(page.getDeltaMovement().scale(0.4).add(d.normalize().scale(speed)));
        page.hasImpulse = true;
    }
}
