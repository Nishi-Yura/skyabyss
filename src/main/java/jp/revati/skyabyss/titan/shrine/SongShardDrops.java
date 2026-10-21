package jp.revati.skyabyss.titan.shrine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.registry.ModItems;

// 歌片の落とし物 (docs/design/32 の 3.2): その巨神の章の敵 (背の敵。骨の番兵・磯走り・雷乞い・灰被りなど) から CHANCE で一つ。
// 倒した人にだけ拾える (ほかの落とし物と同じ)。心臓を止めるたびの一つ・祠の炉で作る・自由なモードの依頼の報いと並ぶ手に入れ方
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SongShardDrops {
    private SongShardDrops() {
    }

    public static final float CHANCE = 0.025f;

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p : mob.getKillCredit() instanceof ServerPlayer p2 ? p2 : null;
        if (killer == null || killer instanceof FakePlayer || level.random.nextFloat() >= CHANCE) {
            return;
        }
        ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), new ItemStack(ModItems.songShard(titan)));
        item.setTarget(killer.getUUID());
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }
}
