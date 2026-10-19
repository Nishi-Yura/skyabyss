package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.storm.StormTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 羽の広場の見晴らしの宝 (26 の 3.3): 西と東の見晴らしに宝箱と碑。宝箱は見た目だけ (中身は無い)。右クリックすると、その人に一度だけ、
// 小さな贈り物 (食べ物・矢・経験値) が渡る。必須の道ではない (いつも開いている。拍や進みで閉じない)。取った印は PlayerPersisted に残る (死んでも消えない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormCaches {
    private static final String TAG = "skyabyss_storm_cache_";

    private StormCaches() {
    }

    // side -1 = 広場の西、+1 = 広場の東、-2 = 骨の書庫、+2 = 雷の炉
    static BlockPos cachePos(int side) {
        return switch (side) {
            case -2 -> VAULT_CACHE_W;
            case -1 -> CACHE_WEST;
            case 1 -> CACHE_EAST;
            default -> VAULT_CACHE_E;
        };
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().dimension() != StormTravel.STORM_BACK
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.CHEST)) {
            return;
        }
        for (int side : new int[] {-2, -1, 1, 2}) {
            if (cachePos(side).equals(event.getPos())) {
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
            Notice.bar(player, ModText.hint("message.skyabyss.storm_cache_empty"));
            return;
        }
        tag.putBoolean(TAG + side, true);
        int jerky = switch (side) {
            case -2 -> 5;
            case -1 -> 4;
            case 1 -> 6;
            default -> 7;
        };
        int arrows = switch (side) {
            case -2 -> 20;
            case -1 -> 16;
            case 1 -> 24;
            default -> 28;
        };
        int xp = switch (side) {
            case -2 -> 50;
            case -1 -> 30;
            case 1 -> 40;
            default -> 60;
        };
        give(player, new ItemStack(ModItems.CLOUD_JERKY.get(), jerky));
        give(player, new ItemStack(Items.ARROW, arrows));
        player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), xp));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 1.2f);
        Notice.send(player, ModText.progress(switch (side) {
            case -2 -> "message.skyabyss.storm_cache_vault_w";
            case -1 -> "message.skyabyss.storm_cache_west";
            case 1 -> "message.skyabyss.storm_cache_east";
            default -> "message.skyabyss.storm_cache_vault_e";
        }));
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
