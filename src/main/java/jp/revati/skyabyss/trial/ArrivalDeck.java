package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 到着の台の作りと、地上へ帰る石 (2026-11-07 の声「塀で囲むのは突貫工事すぎる。ちゃんと建築を凝る (最低でも岩鯨のように)。帰る場所も用意して。
// 巨神によっては全然帰れないことがあった」「岩鯨の集まる所に塀はない。建築ちゃんと凝って」)。
//   台 (潮殻・雷翼・余燼。岩鯨は TitanBackBuilder の左のわき腹の台): 部品 arrival_<巨神> (tools/gen/world/build_arrival_decks.py) を到着の足もとに置く。
//     床 ±6 (縁の帯・十字の道・紋・市松)、縁 ±7 は欄干 (塀の石 1 段。跳んで越せない)、±8 は植え込みの棚、外は一段ずつ下がる石組みの裾。
//     四隅に屋根つきの塔、三辺の真ん中に灯りの柱と旗、北の辺に帰りの石を納める切妻の祠。下は地面まで支える。石と飾りは巨神ごと
//   帰りの石 (天然磁石): 到着の台の祠の中と、心臓の前室 (休み場 heart) のそば。右クリックで地上へ (Titans.sendDown。笛がなくても帰れる)。
//     岩鯨の到着の台は錨石 (TitanTravel) がその役。名札は world/LodestoneLabels
//   背を建て直しても戻るように、人が背にいるあいだ CHECK_EVERY ごとに見て、欠けた所を置く (何度置いても同じ形)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ArrivalDeck {
    public static final int RING = 7;
    static final int CHECK_EVERY = 200;
    static final int DOWN = 14;
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

    // 到着の台の帰りの石 (北の祠の中。部品の印 home_stone。岩鯨は錨石なので null)
    @Nullable
    public static BlockPos homeStone(String titan) {
        if (paletteOf(titan) == null) {
            return null;
        }
        BlockPos f = RouteSeals.safeFeet(titan);
        return f.offset(0, 0, -6);
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

    // 台を置く (読み込まれていない列は飛ばす)。置いたブロックの数。
    // 部品 arrival_<巨神> (tools/gen/world/build_arrival_decks.py) を到着の足もとに合わせて置く:
    //   台の内 (±8・床から上) は部品どおり (空気も。前の版の塀・砂の盛り上がり・前の到着の台の手すりを取り除く)。
    //   外の裾は空き・地形・前の台の石だけを置き換える。封じの戸・天然磁石・鐘・箱 (旗のほかのブロックエンティティ) は残す。
    //   部品のいちばん下の段の下は、地面に着くまで支えの石で埋める。塀・階段・板は置いたあとに隣とつなぐ形へ直す
    public static int place(ServerLevel back, String titan) {
        Palette pal = paletteOf(titan);
        if (pal == null) {
            return 0;
        }
        Blueprint bp = Blueprint.get("arrival_" + titan);
        BlockPos f = RouteSeals.safeFeet(titan);
        int n = 0;
        // 前の版の見えない壁を外す
        for (int dx = -RING - 1; dx <= RING + 1; dx++) {
            for (int dz = -RING - 1; dz <= RING + 1; dz++) {
                for (int y = f.getY() - 2; y <= f.getY() + 4; y++) {
                    BlockPos p = new BlockPos(f.getX() + dx, y, f.getZ() + dz);
                    if (back.hasChunkAt(p) && back.getBlockState(p).is(Blocks.BARRIER)) {
                        back.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        n++;
                    }
                }
            }
        }
        Map<Long, Integer> lowest = new HashMap<>();
        List<BlockPos> shaped = new ArrayList<>();
        for (Blueprint.Cell c : bp.cells) {
            BlockPos p = f.offset(c.x(), c.y(), c.z());
            if (!back.hasChunkAt(p)) {
                continue;
            }
            if (!c.state().isAir()) {
                lowest.merge(BlockPos.asLong(c.x(), 0, c.z()), c.y(), Math::min);
            }
            BlockState now = back.getBlockState(p);
            BlockState want = c.state();
            if (now.getBlock() == want.getBlock() || keep(now)) {
                continue;
            }
            boolean core = Math.max(Math.abs(c.x()), Math.abs(c.z())) <= CORE && c.y() >= -1;
            if (!core && !(open(back, p) || natural(back, p) || oldDeck(now))) {
                continue;
            }
            back.setBlock(p, want, Block.UPDATE_CLIENTS);
            if (n < 6) {
                SkyAbyss.LOGGER.debug("Arrival deck of the {}: {} {} -> {}", titan, c.x() + "," + c.y() + "," + c.z(), now, want);
            }
            n++;
            Block wb = want.getBlock();
            if (wb instanceof WallBlock || wb instanceof StairBlock || wb instanceof IronBarsBlock) {
                shaped.add(p);
            }
        }
        // 支え: 部品の列のいちばん下の一つ下から、地面に着くまで
        for (Map.Entry<Long, Integer> e : lowest.entrySet()) {
            BlockPos col = BlockPos.of(e.getKey());
            n += footing(back, f.offset(col.getX(), e.getValue() - 1, col.getZ()), pal.footing(), DOWN);
        }
        for (BlockPos p : shaped) {
            BlockState st = back.getBlockState(p);
            BlockState fixed = Block.updateFromNeighbourShapes(st, back, p);
            if (fixed != st && !fixed.isAir()) {
                back.setBlock(p, fixed, Block.UPDATE_CLIENTS);
            }
        }
        // 前の版の帰りの石 (台の隅 +4, +4) を外す (帰りの石は祠の中へ移った)
        BlockPos oldStone = f.offset(4, 0, 4);
        if (back.hasChunkAt(oldStone) && back.getBlockState(oldStone).is(Blocks.LODESTONE)) {
            back.setBlock(oldStone, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            n++;
        }
        // 到着の台の帰りの石 (祠の中)
        BlockPos stone = homeStone(titan);
        if (stone != null && back.hasChunkAt(stone) && !back.getBlockState(stone).is(Blocks.LODESTONE)) {
            back.setBlock(stone, Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_ALL);
            n++;
        }
        if (n > 0) {
            SkyAbyss.LOGGER.info("Arrival deck of the {}: placed {} blocks", titan, n);
        }
        return n;
    }

    // 部品どおりに置き直す内の広さ (到着から ±8 の、床から上)
    static final int CORE = RING + 1;

    // 残すもの: 封じの戸・天然磁石・鐘・箱など (旗は台の物なので置き換えてよい)
    private static boolean keep(BlockState s) {
        if (s.is(Blocks.LODESTONE) || s.is(Blocks.BELL) || s.getBlock() instanceof jp.revati.skyabyss.titan.dungeon.SealedGateBlock) {
            return true;
        }
        return s.hasBlockEntity() && !(s.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock);
    }

    // 前の版の台の石 (ArrivalPen・前の ArrivalDeck の塀・柱・灯)
    private static boolean oldDeck(BlockState s) {
        for (String t : Titans.all()) {
            Palette p = paletteOf(t);
            if (p != null && (s.is(p.wall().getBlock()) || s.is(p.base().getBlock()) || s.is(p.post().getBlock()) || s.is(p.light().getBlock())
                    || s.is(p.footing().getBlock()))) {
                return true;
            }
        }
        return s.is(Blocks.BARRIER);
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

    private static boolean open(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty();
    }

    private static boolean solid(ServerLevel back, BlockPos p) {
        BlockState s = back.getBlockState(p);
        return !s.getCollisionShape(back, p).isEmpty() && !s.is(Blocks.BARRIER);
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
}
