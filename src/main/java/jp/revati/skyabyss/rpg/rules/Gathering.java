package jp.revati.skyabyss.rpg.rules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 採集 (docs/design/40 の 2 章)。アドベンチャーではブロックを壊せないので、決まったブロックを右クリックすると、
// 壊さずに、壊したときと同じ品 (手の道具で掘った分) が手に入る。同じ所は人ごとにしばらく採れない (戻りの時間)。
//   木 (丸太)・石と土・砂・砂利・粘土・鉱石・実った作物・花・きのこ・溶岩の塊・黒曜石・紫水晶。道具が要るもの (鉱石・石) は、合う道具を持っていないと採れない
//   都の中・巨神の背・深淵では採れない (作った物は採らせない)。サバイバルの人は今までどおり壊す
//   水は、空のバケツで水源を見て右クリックすると汲める (水は減らない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Gathering {
    // 戻りの時間 (tick)
    static final int WOOD = 20 * 20;
    static final int STONE = 20 * 4;
    static final int ORE = 20 * 60;
    static final int PLANT = 20 * 45;
    static final int RARE = 20 * 180;
    static final int MAX_ENTRIES = 4096;

    private static final Map<UUID, Map<Long, Long>> READY = new HashMap<>();

    private Gathering() {
    }

    // そのブロックの戻りの時間。採れないブロックは 0
    static int regrow(BlockState s) {
        if (s.is(BlockTags.LOGS)) {
            return WOOD;
        }
        if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.OBSIDIAN)) {
            return RARE;
        }
        if (s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            return ORE;
        }
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MAGMA_BLOCK)) {
            return STONE;
        }
        if (s.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(s) ? PLANT : 0;
        }
        if (s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.TALL_FLOWERS) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.COBWEB)) {
            return PLANT;
        }
        return 0;
    }

    static boolean allowedHere(ServerLevel level, BlockPos pos) {
        return !jp.revati.skyabyss.titan.TitanBacks.isBack(level.dimension()) && level.dimension() != jp.revati.skyabyss.story.ch5.AbyssWorld.LEVEL
                && !jp.revati.skyabyss.opening.site.SiteGuard.inCapital(level, pos)
                && (level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER);
    }

    @SubscribeEvent
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        int regrow = regrow(state);
        if (regrow <= 0 || !allowedHere(level, pos)) {
            return;
        }
        event.setCanceled(true);
        ItemStack tool = player.getMainHandItem();
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)) {
            Notice.bar(player, jp.revati.skyabyss.ModText.deny("message.skyabyss.gather.tool"));
            return;
        }
        long now = level.getGameTime();
        Map<Long, Long> mine = READY.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long ready = mine.get(pos.asLong());
        if (ready != null && ready > now && ready - now <= regrow) {
            Notice.bar(player, jp.revati.skyabyss.ModText.deny("message.skyabyss.gather.wait", (ready - now + 19) / 20));
            return;
        }
        if (mine.size() > MAX_ENTRIES) {
            mine.values().removeIf(t -> t <= now);
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
        if (drops.isEmpty()) {
            return;
        }
        mine.put(pos.asLong(), now + regrow);
        for (ItemStack d : drops) {
            jp.revati.skyabyss.quest.Quests.give(player, d);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        var sound = state.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch() * 0.9f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
    }

    // 水を汲む: アドベンチャーではバケツが使えない (置く権利がない)。空のバケツで水源を見ていれば、水は減らさずに水入りにする
    @SubscribeEvent
    static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
                || !event.getItemStack().is(net.minecraft.world.item.Items.BUCKET) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(5)), net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
            return;
        }
        event.setCanceled(true);
        event.getItemStack().shrink(1);
        jp.revati.skyabyss.quest.Quests.give(player, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        level.playSound(null, hit.getBlockPos(), net.minecraft.sounds.SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1f, 1f);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        READY.clear();
    }

    // 自己テスト用
    public static void clearAll() {
        READY.clear();
    }
}
