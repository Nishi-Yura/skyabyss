package jp.revati.skyabyss.titan.ember.back;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout.Cache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// 余燼の背の任意の翼の宝箱 (版 20。26 の 9 章): 尾根の広間の見張り台 2・灰の墓所・工房・灰降りの庭・背へ出る洞の脇の小部屋。
// どれも行き止まりの奥にあり、碑が一行そえてある。宝箱は見た目だけ (中身は無い)。右クリックすると、その人に一度だけ、小さな贈り物
// (干し肉・余燼の燠石・火の粉の粉・経験値) が渡る。必須の道ではない (いつも開いている。状態の鍵なし)。取った印は PlayerPersisted に残る (死んでも消えない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberCaches {
    private static final String TAG = "skyabyss_ember_cache_";

    private EmberCaches() {
    }

    // 宝箱の台と箱と碑 (箱の上の看板。後ろの壁に付く)。何度建てても同じ
    static void place(EmberKit kit) {
        for (Cache c : EmberBackLayout.CACHES) {
            BlockPos p = c.pos();
            kit.set(p.below(), EmberKit.RUIN_CHISELED);
            kit.set(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, c.facing()));
            kit.wallSign(p.above(), c.facing(), "sign.skyabyss.ember_cache_" + c.id(), DyeColor.YELLOW);
        }
    }

    public static Cache at(BlockPos pos) {
        for (Cache c : EmberBackLayout.CACHES) {
            if (c.pos().equals(pos)) {
                return c;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().dimension() != EmberIds.BACK
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.CHEST)) {
            return;
        }
        Cache c = at(event.getPos());
        if (c != null) {
            event.setCanceled(true);
            open(player, c);
        }
    }

    public static boolean taken(Player player, Cache c) {
        return persisted(player).getBoolean(TAG + c.id());
    }

    static void open(ServerPlayer player, Cache c) {
        CompoundTag tag = persisted(player);
        if (tag.getBoolean(TAG + c.id())) {
            Notice.bar(player, ModText.hint("message.skyabyss.ember_cache_empty"));
            return;
        }
        tag.putBoolean(TAG + c.id(), true);
        give(player, new ItemStack(ModItems.CLOUD_JERKY.get(), 3));
        give(player, stackOf(EmberIds.id(EmberIds.EMBER_SHARD), 2));
        give(player, stackOf(EmberIds.id(EmberIds.SPARK_DUST), 2));
        player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY(), player.getZ(), 30));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4f, 0.8f);
        Notice.send(player, ModText.progress("message.skyabyss.ember_cache_" + c.id()));
    }

    private static ItemStack stackOf(ResourceLocation id, int n) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item, n);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
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
