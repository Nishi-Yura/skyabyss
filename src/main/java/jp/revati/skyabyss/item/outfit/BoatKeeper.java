package jp.revati.skyabyss.item.outfit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 舟に乗ったまま別の次元へ移ったら (巨神の背へ渡る・門をくぐるなど)、舟を持ち物に戻す (2026-11-07 の声「巨神にボートで行くと、ボートが取り残される」)。
// 人を別の次元へ移すとき (ServerPlayer.teleportTo) は、次元の知らせより先に舟から降ろされるので、降りた舟を覚えておき、
// 同じ時 (WINDOW tick 以内) に次元が変わったら、その舟 (ほかに誰も乗っていないもの) を消して品を渡す。持ち物に入らなければ舟はそのまま
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoatKeeper {
    static final int WINDOW = 2;

    record Left(UUID boat, ResourceKey<Level> level, long tick) {
    }

    private static final Map<UUID, Left> LEFT = new HashMap<>();

    private BoatKeeper() {
    }

    @SubscribeEvent
    static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer p && event.getEntityBeingMounted() instanceof Boat boat) {
            LEFT.put(p.getUUID(), new Left(boat.getUUID(), boat.level().dimension(), p.server.getTickCount()));
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        Left left = LEFT.remove(p.getUUID());
        if (left == null || p.server.getTickCount() - left.tick() > WINDOW || !left.level().equals(event.getFrom())) {
            return;
        }
        ServerLevel from = p.server.getLevel(left.level());
        Entity e = from == null ? null : from.getEntity(left.boat());
        if (e instanceof Boat boat && boat.isAlive() && boat.getPassengers().isEmpty()) {
            pocket(p, boat);
        }
    }

    // 舟を品にして持たせる。持たせたら true
    public static boolean pocket(ServerPlayer p, Boat boat) {
        ItemStack stack = new ItemStack(boat.getDropItem());
        if (stack.isEmpty() || !p.getInventory().add(stack)) {
            return false;
        }
        boat.discard();
        return true;
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LEFT.clear();
    }
}
