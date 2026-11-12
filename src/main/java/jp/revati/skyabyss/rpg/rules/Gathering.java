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
//   木 (丸太)・石と土・砂・砂利・粘土・鉱石・実った作物・花・きのこ・溶岩の塊・黒曜石・紫水晶。
//   道具ごと (2026-10-27 の声「関連したツールでとれるように」): 丸太は斧、石・鉱石・黒曜石・溶岩の塊・紫水晶はつるはし、
//   土・砂・砂利・粘土はシャベル。作物・花・きのこは素手でよい。採った物は市の素材屋が買い取る (使い道 = 鐘銭)。
//   置く品で、売れず頼みにも使わないもの (花・きのこ・丸石以外の石など) は採れない (rpg/rules/NoPlaceables。2026-11-02「置く系のアイテムは手に入らないように」)
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

    // 人ごと。ログアウトで消える (PlayerMaps。2026-11-02 の監査: 消えずに溜まっていた)
    private static final Map<UUID, Map<Long, Long>> READY = jp.revati.skyabyss.util.PlayerMaps.track(new HashMap<>());

    private Gathering() {
    }

    // そのブロックの戻りの時間。採れないブロックは 0。
    // 2026-11-08 の声「土や木をとれるけど、なにこれ」: 使い道のない品 (丸太・土・石・砂・砂利・粘土・花・きのこ・かぼちゃなど) は採れなくした。
    // 残すのは、Mod の素材になる鉱石・紫水晶・実った麦と、頼みに使う黒曜石だけ
    static int regrow(BlockState s) {
        if (s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || s.is(Blocks.ANCIENT_DEBRIS) || s.is(Blocks.OBSIDIAN)) {
            return RARE;
        }
        if (s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            return ORE;
        }
        if (s.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(s) ? PLANT : 0;
        }
        if (s.is(Blocks.AMETHYST_CLUSTER)) {
            return PLANT;
        }
        return 0;
    }

    // そのブロックに要る道具 (null = 素手でよい)。採れるのは鉱石・紫水晶・黒曜石 (つるはし) と実った作物 (素手) だけ
    // (2026-11-12: 木・土を採らなくなったので、斧とシャベルの品ごと消した)
    @javax.annotation.Nullable
    static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toolFor(BlockState s) {
        if (s.getBlock() instanceof CropBlock) {
            return null;
        }
        return net.minecraft.tags.ItemTags.PICKAXES;
    }

    static String toolKey(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return "pickaxe";
    }

    // 採った品: ブロックの品はそのまま、バニラの素材は mod の素材へ (置き換えの第 3 段。2026-10-27)、それ以外 (種・野菜など) は出さない
    static ItemStack modDrop(ItemStack d) {
        if (d.getItem() instanceof net.minecraft.world.item.BlockItem || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(d.getItem()).getNamespace().equals("minecraft")) {
            return d;
        }
        net.minecraft.world.item.Item i = d.getItem();
        net.minecraft.world.item.Item to = i == net.minecraft.world.item.Items.RAW_IRON ? jp.revati.skyabyss.item.outfit.MaterialGear.IRON_LUMP.get()
                : i == net.minecraft.world.item.Items.RAW_COPPER ? jp.revati.skyabyss.item.outfit.MaterialGear.COPPER_LUMP.get()
                : i == net.minecraft.world.item.Items.COAL || i == net.minecraft.world.item.Items.CHARCOAL ? jp.revati.skyabyss.item.outfit.MaterialGear.CHARCOAL_LUMP.get()
                : i == net.minecraft.world.item.Items.DIAMOND ? jp.revati.skyabyss.item.outfit.MaterialGear.SKY_STONE.get()
                : i == net.minecraft.world.item.Items.RAW_GOLD || i == net.minecraft.world.item.Items.GOLD_NUGGET ? jp.revati.skyabyss.item.outfit.MaterialGear.GOLD_GRAIN.get()
                : i == net.minecraft.world.item.Items.LAPIS_LAZULI || i == net.minecraft.world.item.Items.REDSTONE || i == net.minecraft.world.item.Items.AMETHYST_SHARD
                        || i == net.minecraft.world.item.Items.EMERALD ? jp.revati.skyabyss.item.outfit.MaterialGear.GLINT_SHARD.get()
                : i == net.minecraft.world.item.Items.FLINT ? jp.revati.skyabyss.item.outfit.MaterialGear.FLINT_STONE.get()
                : i == net.minecraft.world.item.Items.CLAY_BALL ? jp.revati.skyabyss.item.outfit.MaterialGear.CLAY_LUMP.get()
                : i == net.minecraft.world.item.Items.WHEAT ? jp.revati.skyabyss.item.outfit.MaterialGear.WHEAT_SHEAF.get() : null;
        return to == null ? ItemStack.EMPTY : new ItemStack(to, d.getCount());
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
        var need = toolFor(state);
        if (need != null && !tool.is(need)) {
            Notice.bar(player, jp.revati.skyabyss.ModText.deny("message.skyabyss.gather.need." + toolKey(need)));
            return;
        }
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
        List<ItemStack> outs = new java.util.ArrayList<>();
        for (ItemStack d : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool)) {
            ItemStack out = modDrop(d);
            // 置く品で使い道のないもの (花・きのこ・売れない石など。NoPlaceables) は採れない
            if (!out.isEmpty() && !NoPlaceables.blockGive(player, out)) {
                outs.add(out);
            }
        }
        if (outs.isEmpty()) {
            return;
        }
        mine.put(pos.asLong(), now + regrow);
        for (ItemStack out : outs) {
            jp.revati.skyabyss.quest.Quests.give(player, out);
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
