package jp.revati.skyabyss.titan.dungeon;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 胸びれの先の宝 (版 18。26 の 8.6): 左のひれの先には碑と小さな宝 (台の上の宝箱)、右のひれの先には見晴らしと、風の下の隠し棚の宝箱。
// 宝箱は見た目だけ (中身は無い)。右クリックすると、その人に一度だけ、小さな贈り物 (食べ物・矢・光る海のイソギンチャク・岩鯨の鱗片と経験値) が渡る。
// 必須の道ではない (いつも開いている)。取った印は PlayerPersisted に残る (死んでも消えない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FinCaches {
    private static final String TAG = "skyabyss_fin_cache_";

    private FinCaches() {
    }

    // side -1 = 左、+1 = 右
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().dimension() != TitanTravel.TITAN_BACK
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(jp.revati.skyabyss.registry.ModBlocks.RELIC_CACHE.get())) {
            return;
        }
        for (int side : new int[]{-1, 1}) {
            BlockPos at = WingsBuilder.cachePos(side);
            if (at.equals(event.getPos())) {
                event.setCanceled(true);
                open(player, side);
                return;
            }
        }
    }

    static boolean taken(Player player, int side) {
        return persisted(player).getBoolean(TAG + side);
    }

    static void open(ServerPlayer player, int side) {
        CompoundTag tag = persisted(player);
        if (tag.getBoolean(TAG + side)) {
            Notice.bar(player, ModText.hint("message.skyabyss.fin_cache_empty"));
            return;
        }
        tag.putBoolean(TAG + side, true);
        jp.revati.skyabyss.relic.RelicCacheBlock.pulse(player.level(), WingsBuilder.cachePos(side));
        give(player, new ItemStack(ModItems.GLOW_ANEMONE.get(), 1));
        if (side < 0) {
            give(player, new ItemStack(ModItems.CLOUD_JERKY.get(), 4));
            give(player, new ItemStack(ModItems.BONE_SPIKE_ARROW.get(), 24));
            player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), 30));
        } else {
            give(player, new ItemStack(ModItems.STONE_WHALE_SHARD.get(), 2));
            player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), 40));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.0f);
        Notice.send(player, ModText.progress(side < 0 ? "message.skyabyss.fin_cache_left" : "message.skyabyss.fin_cache_right"));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
