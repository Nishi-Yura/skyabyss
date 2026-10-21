package jp.revati.skyabyss.story.ch2;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.site.ChapterGrounds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.opening.site.SiteNetwork;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.titan.tide.chapter.TideChapter;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 沖の難破船 (docs/design/34 の 4.3 の ebb_boat。「跡地の浜から見える所に一つは出す」)。
//   置き方  二章の跡地が決まっていて、その中心から 200 以内に人がいるとき、跡地の海の向き (Ground.sea) の沖 40〜120 の、水深 3〜8 の浅瀬
//           (読み込まれたチャンクだけを見る) に、バニラの難破船の型 (minecraft:shipwreck/with_mast。なければ rightsideup_full) を世界で一度だけ置く。
//           箱はバニラの難破船と同じ戦利品の表 (地図・宝・食料)。置いた場所は Ch2Data に残し、目的の帯の行き先 (ch2_wreck) として送る
//   箱      船の中の箱を開けると、人ごとに一度: 潮の民の頁 (伝承 ch2/wreck_page) と、潮見の貝 (まだもらっていない人。TideChapter.giveShell)
//   見える  二章の人が船から 96 以内に初めて来たとき一行 (「沖に、傾いた船の帆柱が見える」)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Wreck {
    public static final String TARGET = "ch2_wreck";
    static final ResourceLocation TEMPLATE = ResourceLocation.withDefaultNamespace("shipwreck/with_mast");
    static final ResourceLocation FALLBACK = ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full");
    static final int WATCH = 200;
    static final int MIN_OUT = 40;
    static final int MAX_OUT = 120;
    static final int MIN_DEPTH = 3;
    static final int MAX_DEPTH = 8;
    static final int SEEN_NEAR = 96;
    static final String SEEN = "skyabyss_ch2_wreck_seen";

    private Wreck() {
    }

    // ---------------------------------------------------------------- 記録
    public static final class Data extends SavedData {
        private static final String NAME = "skyabyss_ch2_ground";
        private static final Factory<Data> FACTORY = new Factory<>(Data::new, Data::load);
        @Nullable
        public BlockPos spot;
        @Nullable
        public BoundingBox box;
        public int tries;

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (spot != null) {
                tag.put("spot", NbtUtils.writeBlockPos(spot));
            }
            if (box != null) {
                tag.putIntArray("box", new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
            }
            tag.putInt("tries", tries);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.spot = NbtUtils.readBlockPos(tag, "spot").orElse(null);
            int[] b = tag.getIntArray("box");
            if (b.length == 6) {
                d.box = new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
            }
            d.tries = tag.getInt("tries");
            return d;
        }
    }

    @Nullable
    public static BlockPos spot(MinecraftServer server) {
        return Data.get(server).spot;
    }

    // ---------------------------------------------------------------- 置く
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 61) {
            return;
        }
        ServerLevel level = server.overworld();
        Data data = Data.get(server);
        List<ServerPlayer> players = Players.snapshot(level.players());
        if (data.spot == null) {
            trySpawn(server, level, data, players);
            return;
        }
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && Ch2Ground.inChapter(p) && !p.getPersistentData().getBoolean(SEEN)
                    && Math.hypot(p.getX() - data.spot.getX(), p.getZ() - data.spot.getZ()) <= SEEN_NEAR) {
                p.getPersistentData().putBoolean(SEEN, true);
                Notice.narrate(p, "message.skyabyss.ch2.wreck.seen");
            }
        }
    }

    static void trySpawn(MinecraftServer server, ServerLevel level, Data data, List<ServerPlayer> players) {
        SiteData.Ground g = ChapterGrounds.ground(server, Chapters.TIDE);
        if (g == null || g.sea == null || !ChapterGrounds.ready(server, Chapters.TIDE)) {
            return;
        }
        BlockPos c = g.center;
        boolean near = false;
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && Math.hypot(p.getX() - c.getX(), p.getZ() - c.getZ()) <= WATCH) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        BlockPos at = findSpot(level, c, g.sea);
        if (at == null) {
            return;
        }
        place(level, at, Rotation.getRandom(level.random), data);
    }

    // 跡地の中心から海の向きへ、水深 3〜8 の浅瀬 (読み込まれたチャンクだけ)。なければ null
    @Nullable
    public static BlockPos findSpot(ServerLevel level, BlockPos center, BlockPos sea) {
        return findSpot(level, center, sea, level.getSeaLevel());
    }

    // seaLevel = 水面の上の高さ (WORLD_SURFACE の値。自己テストは空の上の水の箱で呼ぶ)
    @Nullable
    public static BlockPos findSpot(ServerLevel level, BlockPos center, BlockPos sea, int seaLevel) {
        double dx = sea.getX() - center.getX();
        double dz = sea.getZ() - center.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1) {
            return null;
        }
        dx /= len;
        dz /= len;
        for (int out = MIN_OUT; out <= MAX_OUT; out += 8) {
            for (int side : new int[] {0, 12, -12, 24, -24}) {
                int x = (int) Math.round(center.getX() + dx * out - dz * side);
                int z = (int) Math.round(center.getZ() + dz * out + dx * side);
                if (shallow(level, x, z, seaLevel) && shallow(level, x + 6, z, seaLevel) && shallow(level, x - 6, z, seaLevel)
                        && shallow(level, x, z + 6, seaLevel) && shallow(level, x, z - 6, seaLevel)) {
                    return new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z), z);
                }
            }
        }
        return null;
    }

    static boolean shallow(ServerLevel level, int x, int z, int seaLevel) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        int depth = surface - floor;
        return surface >= seaLevel - 1 && surface <= seaLevel + 1 && depth >= MIN_DEPTH && depth <= MAX_DEPTH
                && !level.getFluidState(new BlockPos(x, surface - 1, z)).isEmpty();
    }

    // 難破船を置く (floor = 海の底の一つ上)。置いたら箱の範囲を記録して目的の帯へ送る (開発コマンド・自己テストからも)
    public static boolean place(ServerLevel level, BlockPos floor, Rotation rotation, @Nullable Data data) {
        Optional<StructureTemplate> found = level.getStructureManager().get(TEMPLATE).or(() -> level.getStructureManager().get(FALLBACK));
        if (found.isEmpty()) {
            SkyAbyss.LOGGER.error("ch2 wreck: no shipwreck template");
            return false;
        }
        StructureTemplate t = found.get();
        var size = t.getSize();
        BlockPos pivot = new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        // 船底を 2 段、海の底へ沈める
        BlockPos pos = new BlockPos(floor.getX() - pivot.getX(), floor.getY() - 2, floor.getZ() - pivot.getZ());
        RandomSource random = level.random;
        t.placeInWorld(level, pos, pos, settings, random, 2);
        // 箱の印 (data の構造ブロック) → バニラの難破船の戦利品
        for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(pos, settings, Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() == null) {
                continue;
            }
            String meta = info.nbt().getString("metadata");
            ResourceKey<LootTable> table = switch (meta) {
                case "map_chest" -> BuiltInLootTables.SHIPWRECK_MAP;
                case "treasure_chest" -> BuiltInLootTables.SHIPWRECK_TREASURE;
                case "supply_chest" -> BuiltInLootTables.SHIPWRECK_SUPPLY;
                default -> null;
            };
            if (table != null) {
                RandomizableContainer.setBlockEntityLootTable(level, random, info.pos().below(), table);
            }
        }
        BoundingBox box = t.getBoundingBox(settings, pos);
        if (data != null) {
            data.spot = floor.immutable();
            data.box = box;
            data.setDirty();
            SiteNetwork.broadcast(level.getServer());
        }
        SkyAbyss.LOGGER.info("ch2 wreck placed at {} (box {})", floor, box);
        return true;
    }

    // ---------------------------------------------------------------- 箱
    @Nullable
    static BoundingBox box(MinecraftServer server) {
        BoundingBox b = Data.get(server).box;
        return b == null ? null : b.inflatedBy(1);
    }

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) {
            return;
        }
        var state = level.getBlockState(event.getPos());
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.BARREL)) {
            return;
        }
        BoundingBox b = box(level.getServer());
        if (b != null && b.isInside(event.getPos())) {
            openChest(player);
        }
    }

    // 船の箱を開けた (自己テストからも): 人ごとに一度、頁と貝。何か渡したら true
    public static boolean openChest(ServerPlayer p) {
        boolean page = !Lore.state(p).has(Ch2Ground.LORE_WRECK);
        if (page) {
            Notice.narrate(p, "message.skyabyss.ch2.wreck.page");
            Tasks.later(30, () -> Lore.unlock(p, Ch2Ground.LORE_WRECK, true));
        }
        boolean shell = TideChapter.giveShell(p);
        if (shell) {
            Tasks.later(60, () -> Notice.narrate(p, "message.skyabyss.ch2.wreck.shell"));
        }
        return page || shell;
    }
}
