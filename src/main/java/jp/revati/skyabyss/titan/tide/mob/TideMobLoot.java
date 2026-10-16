package jp.revati.skyabyss.titan.tide.mob;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

// 潮殻の背の敵が落とす物 (08 の 6.1節)。岩鯨の道中の番兵 (TitanLoot.rollGuard) と同じ型:
// 一体ごとに、倒した人のための抽選をし、その人にだけ拾える形で落とす。背中 (tide_back) の上で、プレイヤーが倒したときだけ。
//
// | 敵       | 潮殻の欠片 | 共鳴の欠片 | 潮干しの貝 | そのほか |
// | 珊瑚憑き | 30%、1〜2  | 5%、1      | 3%、1      | 潮殻の鎧のどれか一部位 1% (鎧はまだないので、今は落とさない) |
// | 磯走り   | 8%、1      | 1%、1      | —          | — |
// | 泡吹き   | 25%、1〜2  | 5%、1      | 3%、1      | 泡袋 20% |
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideMobLoot {
    private TideMobLoot() {
    }

    public record Row(float shard, int shardMin, int shardMax, float resonance, float driedShell, float bubbleSac) {
    }

    public static final Row THRALL = new Row(0.30f, 1, 2, 0.05f, 0.03f, 0f);
    public static final Row SCUTTLER = new Row(0.08f, 1, 1, 0.01f, 0f, 0f);
    public static final Row PUFFER = new Row(0.25f, 1, 2, 0.05f, 0.03f, 0.20f);

    public static Row row(LivingEntity mob) {
        if (mob instanceof CoralThrall) {
            return THRALL;
        }
        if (mob instanceof ReefScuttler) {
            return SCUTTLER;
        }
        if (mob instanceof BubblePuffer) {
            return PUFFER;
        }
        return null;
    }

    // 倒した人への、一体ぶんの抽選
    public static List<ItemStack> roll(Row r, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        if (r == null) {
            return items;
        }
        if (random.nextFloat() < r.shard()) {
            items.add(new ItemStack(TideMobSetup.TIDE_SHELL_SHARD.get(), r.shardMin() + random.nextInt(r.shardMax() - r.shardMin() + 1)));
        }
        if (random.nextFloat() < r.resonance()) {
            items.add(new ItemStack(ModItems.RESONANCE_SHARD.get()));
        }
        if (random.nextFloat() < r.driedShell()) {
            items.add(new ItemStack(TideMobSetup.TIDE_DRIED_SHELL.get()));
        }
        if (random.nextFloat() < r.bubbleSac()) {
            items.add(new ItemStack(TideMobSetup.BUBBLE_SAC.get()));
        }
        return items;
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        LivingEntity mob = event.getEntity();
        Row r = row(mob);
        if (r == null || !(mob.level() instanceof ServerLevel level) || level.dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            killer = p;
        } else if (mob.getKillCredit() instanceof ServerPlayer p) {
            killer = p;
        }
        if (killer == null || killer instanceof FakePlayer) {
            return;
        }
        for (ItemStack stack : roll(r, level.random)) {
            ItemEntity item = new ItemEntity(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), stack);
            item.setTarget(killer.getUUID());
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }
}
