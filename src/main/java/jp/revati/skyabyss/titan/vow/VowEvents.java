package jp.revati.skyabyss.titan.vow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.dungeon.CampBlocks;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 誓いの効き (Vow の数字を読んで当てる)。予告の短縮は Telegraph → Vows.telegraphWarn、報酬は TitanEnd → Vows.onHeartStopped。
//  - 敵の体力: 1 秒ごとに背中の敵 (GroupOwned) を見て、そのグループの誓いの分だけ最大体力に上乗せ (一度だけ。体力は割合で保つ)
//  - 受ける傷: 背中の人が受ける傷に上乗せ
//  - 野営禁止: 背中で野営の道具を置けない (置こうとした品は手に残る)
//  - 時間制限: 背中に誰かが入った時刻を始まりにする (Vows.startTimerIfNeeded)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class VowEvents {
    public static final ResourceLocation HARDY_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "vow_foe_health");

    private VowEvents() {
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !TitanBacks.isBack(level.dimension()) || level.players().isEmpty()) {
            return;
        }
        long t = level.getGameTime();
        if (t % 20 == 11) {
            Set<UUID> groups = new HashSet<>();
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && groups.add(Groups.keyOf(p))) {
                    Vows.startTimerIfNeeded(level, Groups.keyOf(p), level.getServer().overworld().getGameTime());
                }
            }
            applyFoeHealth(level);
        }
        if (t % 100 == 13) {
            for (ServerPlayer p : level.players()) {
                VowNetwork.resend(p);
            }
        }
    }

    // 背中の敵に、そのグループの誓いの体力を一度だけ上乗せする。上乗せした数 (自己テスト用)
    public static int applyFoeHealth(ServerLevel level) {
        Map<UUID, Float> cache = new HashMap<>();
        int n = 0;
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity living) || !(e instanceof GroupOwned owned) || owned.groupId() == null || living.isDeadOrDying()) {
                continue;
            }
            float extra = cache.computeIfAbsent(owned.groupId(), g -> foeHealth(Vows.active(level, g)));
            if (extra > 0 && applyFoeHealth(living, extra)) {
                n++;
            }
        }
        return n;
    }

    public static float foeHealth(List<Vow> vows) {
        float sum = 0;
        for (Vow v : vows) {
            sum += v.foeHealth();
        }
        return sum;
    }

    // 一体に上乗せ。すでに上乗せしてあれば何もしない (true = 今回上乗せした)
    public static boolean applyFoeHealth(LivingEntity living, float extra) {
        AttributeInstance health = living.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.hasModifier(HARDY_ID)) {
            return false;
        }
        float before = living.getMaxHealth();
        health.addPermanentModifier(new AttributeModifier(HARDY_ID, extra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        float after = living.getMaxHealth();
        if (before > 0) {
            living.setHealth(Math.min(after, living.getHealth() * after / before));
        }
        return true;
    }

    public static float damageTaken(List<Vow> vows) {
        float sum = 0;
        for (Vow v : vows) {
            sum += v.damageTaken();
        }
        return sum;
    }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TitanBacks.isBack(player.level().dimension())) {
            float extra = damageTaken(Vows.activeFor(player));
            if (extra > 0) {
                event.setAmount(event.getAmount() * (1f + extra));
            }
        }
    }

    public static boolean noCamp(List<Vow> vows) {
        return vows.stream().anyMatch(Vow::noCamp);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TitanBacks.isBack(player.level().dimension())
                && CampBlocks.isCamp(event.getPlacedBlock()) && noCamp(Vows.activeFor(player))) {
            event.setCanceled(true);
            Notice.bar(player, ModText.deny("message.skyabyss.vow.no_camp"));
        }
    }
}
