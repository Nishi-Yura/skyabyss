package jp.revati.skyabyss.trial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 到着の台の作りと、地上へ帰る石 (2026-11-07 の声「塀で囲むのは突貫工事すぎる。ちゃんと建築を凝る (最低でも岩鯨のように)。帰る場所も用意して。
// 巨神によっては全然帰れないことがあった」)。
//   台 (潮殻・雷翼・余燼。岩鯨は TitanBackBuilder の左のわき腹の台): 入口の箱 (TrialRoute.entryBox。到着の足もと ±6) のすぐ外の一周 (±RING) に、
//     その巨神の石の胸壁 (足の高さに台石 + その上に塀。跳んで越せない高さ 2.5)。四隅は灯りの柱 (高さ 4 + 灯)、各辺の真ん中に低い灯りの柱。
//     胸壁の下が空いていれば地面まで台の石で支え (宙に浮かせない)、台の内の足もとが抜けていれば床を張る。台のほかの物 (床・飾り・鐘) は壊さない。
//     前の版の塀の上の見えない壁 (ArrivalPen) は外す
//   帰りの石 (天然磁石): 到着の台の内の隅と、心臓の前室 (休み場 heart) のそば。右クリックで地上へ (Titans.sendDown。笛がなくても帰れる)。
//     岩鯨の到着の台は錨石 (TitanTravel) がその役。名札は world/LodestoneLabels
//   背を建て直しても戻るように、人が背にいるあいだ CHECK_EVERY ごとに見て、欠けた所を置く (何度置いても同じ形)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArrivalDeck {
    public static final int RING = 7;
    static final int CHECK_EVERY = 200;
    static final int DOWN = 6;
    static final int UP = 2;

    // 巨神ごとの石: 支え・台石・塀・柱・灯・床
    public record Palette(BlockState footing, BlockState base, BlockState wall, BlockState post, BlockState light, BlockState floor, BlockState floor2) {
    }

    @Nullable
    public static Palette paletteOf(String titan) {
        return switch (titan) {
            case Titans.TIDE_SHELL -> new Palette(Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.DARK_PRISMARINE.defaultBlockState(),
                    Blocks.PRISMARINE_WALL.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(), Blocks.SEA_LANTERN.defaultBlockState(),
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState());
            case Titans.THUNDER_WING -> new Palette(Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
                    Blocks.DIORITE_WALL.defaultBlockState(), Blocks.WAXED_CUT_COPPER.defaultBlockState(),
                    Blocks.LANTERN.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState());
            case Titans.EMBER -> new Palette(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
                    Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), Blocks.POLISHED_BASALT.defaultBlockState(),
                    Blocks.SHROOMLIGHT.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            default -> null;
        };
    }

    // 帰りの石の場所 (巨神 → 到着の台の石 / 前室の石)。前室の石は前室が建ってから決める
    private static final Map<String, BlockPos> HEART_STONES = new ConcurrentHashMap<>();

    private ArrivalDeck() {
    }

    // 到着の台の帰りの石 (足もとから外へ 4・横へ 4。岩鯨は錨石なので null)
    @Nullable
    public static BlockPos homeStone(String titan) {
        if (paletteOf(titan) == null) {
            return null;
        }
        BlockPos f = RouteSeals.safeFeet(titan);
        return f.offset(4, 0, 4);
    }

    @Nullable
    public static BlockPos heartStone(String titan) {
        return HEART_STONES.get(titan);
    }

    // その背の帰りの石か
    public static boolean isReturnStone(ServerLevel level, BlockPos pos) {
        String titan = Titans.titanOfBack(level.dimension());
        return titan != null && (pos.equals(homeStone(titan)) || pos.equals(heartStone(titan)));
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_EVERY != 41) {
            return;
        }
        for (String titan : Titans.all()) {
            var dim = Titans.backOf(titan);
            ServerLevel back = dim == null ? null : server.getLevel(dim);
            if (back != null && !back.players().isEmpty()) {
                place(back, titan);
                placeHeartStone(back, titan);
            }
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HEART_STONES.clear();
    }

    // 台を置く (読み込まれていない列は飛ばす)。置いたブロックの数
    public static int place(ServerLevel back, String titan) {
        Palette pal = paletteOf(titan);
        if (pal == null) {
            return 0;
        }
        BlockPos f = RouteSeals.safeFeet(titan);
        int n = 0;
        // 前の版の見えない壁を外す
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RING) {
                    continue;
                }
                for (int y = f.getY() - 2; y <= f.getY() + 4; y++) {
                    BlockPos p = new BlockPos(f.getX() + dx, y, f.getZ() + dz);
                    if (back.hasChunkAt(p) && back.getBlockState(p).is(Blocks.BARRIER)) {
                        back.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        n++;
                    }
                }
            }
        }
        // 台の内の床。潮殻 (殻の上の砂浜で床がない) は、足もとの高さを平らにならして石畳を敷く (縁は台石の帯、真ん中に模様)。
        // ほかは抜けている所だけ (雷翼・余燼の台には床が敷いてある)
        boolean pave = titan.equals(Titans.TIDE_SHELL);
        for (int dx = -RING + 1; dx <= RING - 1; dx++) {
            for (int dz = -RING + 1; dz <= RING - 1; dz++) {
                BlockPos fl = new BlockPos(f.getX() + dx, f.getY() - 1, f.getZ() + dz);
                if (!back.hasChunkAt(fl)) {
                    continue;
                }
                BlockState tile = deckTile(pal, dx, dz);
                if (pave) {
                    for (int up = 0; up <= 2; up++) {
                        BlockPos q = fl.above(1 + up);
                        if (natural(back, q)) {
                            back.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            n++;
                        }
                    }
                    if (natural(back, fl) || open(back, fl)) {
                        n += set(back, fl, tile);
                        n += footing(back, fl.below(), pal.footing(), 3);
                    }
                } else if (open(back, fl) && open(back, fl.above())) {
                    n += put(back, fl, tile);
                    n += footing(back, fl.below(), pal.footing(), 3);
                }
            }
        }
        // 胸壁と柱
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RING) {
                    continue;
                }
                int x = f.getX() + dx;
                int z = f.getZ() + dz;
                BlockPos feet = new BlockPos(x, f.getY(), z);
                if (!back.hasChunkAt(feet)) {
                    continue;
                }
                if (solid(back, feet) && solid(back, feet.above()) && !mine(back, feet, pal) && !mine(back, feet.above(), pal)) {
                    continue;
                }
                boolean corner = Math.abs(dx) == RING && Math.abs(dz) == RING;
                boolean mid = (dx == 0 || dz == 0);
                // 台石の下を地面まで支える
                n += footing(back, feet.below(), pal.footing(), DOWN);
                if (corner) {
                    for (int y = 0; y < 4; y++) {
                        n += put(back, feet.above(y), pal.post());
                    }
                    n += put(back, feet.above(4), pal.light());
                } else if (mid) {
                    n += put(back, feet, pal.post());
                    n += put(back, feet.above(), pal.post());
                    n += put(back, feet.above(2), pal.light());
                } else {
                    n += put(back, feet, pal.base());
                    n += put(back, feet.above(), pal.wall());
                }
            }
        }
        // 到着の台の帰りの石 (台の上の隅。下に床)
        BlockPos stone = homeStone(titan);
        if (stone != null && back.hasChunkAt(stone)) {
            n += put(back, stone.below(), pal.base());
            if (!back.getBlockState(stone).is(Blocks.LODESTONE)) {
                back.setBlock(stone, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("Arrival deck of the {}: placed {} blocks", titan, n);
        }
        return n;
    }

    // 心臓の前室の帰りの石: 休み場の立つ所のまわり (2〜4) で、足もとが空き・下が固い所。決めたら覚える
    public static void placeHeartStone(ServerLevel back, String titan) {
        BlockPos known = HEART_STONES.get(titan);
        if (known != null) {
            if (back.hasChunkAt(known) && back.getBlockState(known).isAir()) {
                back.setBlock(known, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            return;
        }
        BlockPos rest = jp.revati.skyabyss.trial.raid.InstanceArenas.heartRest(back, titan);
        if (rest == null || !back.hasChunkAt(rest)) {
            return;
        }
        BlockPos entry = jp.revati.skyabyss.trial.raid.InstanceArenas.heartEntry(titan);
        for (int r = 2; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = rest.offset(dx, dy, dz);
                        if (entry != null && p.distSqr(entry) < 9) {
                            continue;
                        }
                        // 壁ぎわ (隣のどれかが固い) で、置いても通り道を塞がない所
                        boolean wallSide = solid(back, p.north()) || solid(back, p.south()) || solid(back, p.east()) || solid(back, p.west());
                        if (back.getBlockState(p).isAir() && back.getBlockState(p.above()).isAir() && solid(back, p.below()) && wallSide
                                && !back.getBlockState(p.below()).is(Blocks.CAMPFIRE) && !back.getBlockState(p.below()).is(Blocks.SOUL_CAMPFIRE)) {
                            back.setBlock(p, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
                            HEART_STONES.put(titan, p.immutable());
                            SkyAbyss.LOGGER.info("Heart front room return stone of the {} at {}", titan, p.toShortString());
                            return;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 帰る
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p)
                || !(event.getLevel() instanceof ServerLevel level) || !level.getBlockState(event.getPos()).is(Blocks.LODESTONE)) {
            return;
        }
        if (!isReturnStone(level, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        String titan = Titans.titanOfBack(level.dimension());
        Notice.narrate(p, "message.skyabyss.return_beacon.descending");
        SkyAbyss.LOGGER.info("{} went down from the {} back by the return stone", p.getGameProfile().getName(), titan);
        Titans.sendDown(p, titan);
    }

    // ---------------------------------------------------------------- 調べ (自己テスト)
    // 一周のうち、足の高さと頭の高さのどちらも塞がっていない列の数 (0 なら台から歩いて出られない)
    public static int openColumns(ServerLevel back, String titan) {
        BlockPos f = RouteSeals.safeFeet(titan);
        int open = 0;
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RING) {
                    continue;
                }
                BlockPos feet = new BlockPos(f.getX() + dx, f.getY(), f.getZ() + dz);
                if (!solid(back, feet) && !solid(back, feet.above())) {
                    open++;
                }
            }
        }
        return open;
    }

    // 石畳の模様: 縁の一周は台石、真ん中 (半径 2) は柱の石の円、その間は床の二色の市松
    static BlockState deckTile(Palette pal, int dx, int dz) {
        int r = Math.max(Math.abs(dx), Math.abs(dz));
        if (r == RING - 1) {
            return pal.base();
        }
        if (dx * dx + dz * dz <= 5) {
            return pal.post();
        }
        return Math.floorMod(dx + dz, 2) == 0 ? pal.floor() : pal.floor2();
    }

    // 均してよい地形 (背の砂浜・土・石)。台の物 (鐘・石・灯・模様の石・木) は含めない
    private static boolean natural(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        if (s.isAir() || s.is(Blocks.BELL) || s.is(Blocks.LODESTONE) || s.is(Blocks.LIGHT) || s.hasBlockEntity()) {
            return false;
        }
        return s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(Blocks.GRAVEL) || s.is(Blocks.SANDSTONE)
                || s.is(Blocks.CLAY) || s.is(net.minecraft.tags.BlockTags.TERRACOTTA) || s.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS)
                || s.getBlock().getDescriptionId().contains("concrete_powder") || s.is(Blocks.TUFF) || s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.SMOOTH_SANDSTONE)
                || s.is(Blocks.CUT_SANDSTONE) || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    private static int set(ServerLevel back, BlockPos p, BlockState state) {
        if (back.getBlockState(p).is(state.getBlock())) {
            return 0;
        }
        back.setBlock(p, state, Block.UPDATE_ALL);
        return 1;
    }

    private static boolean open(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty();
    }

    private static boolean solid(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return !s.getCollisionShape(back, p).isEmpty() && !s.is(Blocks.BARRIER);
    }

    private static boolean mine(ServerLevel back, BlockPos p, Palette pal) {
        BlockState s = back.getBlockState(p);
        return s.is(pal.wall().getBlock()) || s.is(pal.base().getBlock()) || s.is(pal.post().getBlock());
    }

    // 下へ空いている所を、地面に着くまで (max 段) 支えの石で埋める
    private static int footing(ServerLevel back, BlockPos from, BlockState footing, int max) {
        int n = 0;
        BlockPos p = from;
        for (int i = 0; i < max && open(back, p); i++) {
            back.setBlock(p, footing, Block.UPDATE_ALL);
            n++;
            p = p.below();
        }
        return n;
    }

    private static int put(ServerLevel back, BlockPos p, BlockState state) {
        BlockState s = back.getBlockState(p);
        if (s.is(state.getBlock())) {
            return 0;
        }
        // 前の版の塀 (ArrivalPen の塀) と見えない壁は置き換えてよい
        if (open(back, p) || s.is(Blocks.BARRIER) || s.is(Blocks.PRISMARINE_WALL) || s.is(Blocks.DIORITE_WALL) || s.is(Blocks.POLISHED_BLACKSTONE_BRICK_WALL)) {
            back.setBlock(p, state.hasProperty(LanternBlock.HANGING) ? state.setValue(LanternBlock.HANGING, false) : state, Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }
}
