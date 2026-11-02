package jp.revati.skyabyss.item.outfit;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// 置き換えの第 3 段 (2026-10-27): バニラの素材と道具類の代わり。絵は選定の案 (tools/rough/icons_phase3.py)。
//   素材   鉄の塊・銅の塊・炭・空石・金の粒・光る石の欠片・火打ち石・粘土の塊・麦の束・糸巻き・獣の革・熾火の棒・熾の雫
//          (採集で出るバニラの品も、ここへ替える。rpg/rules/Gathering.MOD_DROPS)
//   道具類 方位盤・白紙の海図・藍の栞 (頼みと記念の品)、水袋 (水場で右クリックすると水入り)、小舟 (乗れる舟。SkiffEntity)
public final class MaterialGear {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SkyAbyss.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, SkyAbyss.MODID);

    private static DeferredItem<Item> plain(String id) {
        return ITEMS.register(id, () -> new ConsumableGear.DescItem(new Item.Properties()));
    }

    public static final DeferredItem<Item> IRON_LUMP = plain("iron_lump");
    public static final DeferredItem<Item> COPPER_LUMP = plain("copper_lump");
    public static final DeferredItem<Item> CHARCOAL_LUMP = plain("charcoal_lump");
    public static final DeferredItem<Item> SKY_STONE = plain("sky_stone");
    public static final DeferredItem<Item> GOLD_GRAIN = plain("gold_grain");
    public static final DeferredItem<Item> GLINT_SHARD = plain("glint_shard");
    public static final DeferredItem<Item> FLINT_STONE = plain("flint_stone");
    public static final DeferredItem<Item> CLAY_LUMP = plain("clay_lump");
    public static final DeferredItem<Item> WHEAT_SHEAF = plain("wheat_sheaf");
    public static final DeferredItem<Item> THREAD_SPOOL = plain("thread_spool");
    public static final DeferredItem<Item> HIDE = plain("hide");
    public static final DeferredItem<Item> EMBER_ROD = plain("ember_rod");
    public static final DeferredItem<Item> EMBER_DROP = plain("ember_drop");
    public static final DeferredItem<Item> WAYFINDER = ITEMS.register("wayfinder", () -> new ConsumableGear.DescItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> BLANK_CHART = plain("blank_chart");
    public static final DeferredItem<Item> INDIGO_BOOKMARK = plain("indigo_bookmark");
    public static final DeferredItem<Item> WATER_SKIN_FULL = ITEMS.register("water_skin_full", () -> new ConsumableGear.DescItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WATER_SKIN = ITEMS.register("water_skin", () -> new WaterSkinItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<EntityType<?>, EntityType<SkiffEntity>> SKIFF_ENTITY = ENTITIES.register("skiff",
            () -> EntityType.Builder.<SkiffEntity>of(SkiffEntity::new, MobCategory.MISC).sized(1.375f, 0.5625f).eyeHeight(0.5625f).clientTrackingRange(10)
                    .build(SkyAbyss.MODID + ":skiff"));
    public static final DeferredItem<Item> SKIFF = ITEMS.register("skiff", () -> new SkiffItem(new Item.Properties().stacksTo(1)));

    // 水袋 (空): 水の源を見て右クリックすると水入りになる (水は減らない。アドベンチャーでも)
    public static class WaterSkinItem extends ConsumableGear.DescItem {
        public WaterSkinItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
                return InteractionResultHolder.pass(stack);
            }
            level.playSound(player, hit.getBlockPos(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1f, 0.9f);
            if (!level.isClientSide) {
                stack.consume(1, player);
                jp.revati.skyabyss.quest.Quests.give((net.minecraft.server.level.ServerPlayer) player, new ItemStack(WATER_SKIN_FULL.get()));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    // 小舟: バニラの舟と同じに水 (か地面) を見て置く。置かれるのは SkiffEntity (壊すとこの品に戻る)
    public static class SkiffItem extends ConsumableGear.DescItem {
        public SkiffItem(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
            if (hit.getType() != HitResult.Type.BLOCK) {
                return InteractionResultHolder.pass(stack);
            }
            Vec3 look = player.getViewVector(1f);
            List<Entity> near = level.getEntities(player, player.getBoundingBox().expandTowards(look.scale(5.0)).inflate(1.0),
                    EntitySelector.NO_SPECTATORS.and(Entity::isPickable));
            for (Entity e : near) {
                AABB box = e.getBoundingBox().inflate(e.getPickRadius());
                if (box.contains(player.getEyePosition())) {
                    return InteractionResultHolder.pass(stack);
                }
            }
            Vec3 at = hit.getLocation();
            SkiffEntity boat = new SkiffEntity(SKIFF_ENTITY.get(), level);
            boat.setPos(at.x, at.y, at.z);
            boat.setVariant(Boat.Type.OAK);
            boat.setYRot(player.getYRot());
            if (!level.noCollision(boat, boat.getBoundingBox())) {
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide) {
                level.addFreshEntity(boat);
                level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
                stack.consume(1, player);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
    }

    // 乗れる小舟 (バニラの舟の動き。壊すと小舟の品に戻る)
    public static class SkiffEntity extends Boat {
        public SkiffEntity(EntityType<? extends Boat> type, Level level) {
            super(type, level);
        }

        @Override
        public Item getDropItem() {
            return SKIFF.get();
        }
    }

    private MaterialGear() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
    }
}
