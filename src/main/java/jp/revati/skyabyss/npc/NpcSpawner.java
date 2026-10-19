package jp.revati.skyabyss.npc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// NPC をどこに立たせるか。
//  - 語り部: 村 (集会所の鐘) にプレイヤーが近づいたとき、その村にまだいなければ、鐘のそばに一人立たせる。村ごとに一人
//  - 鯨追い: 村の近くにいる人のクエストが鯨追いを必要としている (話す目的・依頼主) とき、村の外れ (鐘から 18〜34 ブロック) に
//           火を焚いて立たせる。焚き火の煙が、遠くからの目印になる
// どちらもいなくなったら (コマンドで消した等)、立たせてから 1 日たてば立て直す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class NpcSpawner {
    private static final int CHECK_INTERVAL = 100;
    private static final int VILLAGE_RADIUS = 48;
    private static final double SEARCH = 40;
    private static final long RESPAWN_AFTER = 24000;

    private NpcSpawner() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || (level.getGameTime() + (player.getId() & 0xFF)) % CHECK_INTERVAL != 0) {
            return;
        }
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), VILLAGE_RADIUS,
                PoiManager.Occupancy.ANY);
        if (bell.isPresent()) {
            visitVillage(level, bell.get(), player);
            return;
        }
        // 鐘のある村が近くに無い人のための野営 (StoryGuide が焚き火を置く)。村と同じように語り部が立ち、必要なら鯨追いも立つ
        BlockPos camp = nearestCamp(NpcWorldData.get(level.getServer().overworld()), player.blockPosition(), VILLAGE_RADIUS);
        if (camp != null) {
            visitVillage(level, camp, player);
        }
    }

    // 位置 near から水平 radius 以内の、いちばん近い野営の焚き火 (なければ null)
    @Nullable
    public static BlockPos nearestCamp(NpcWorldData data, BlockPos near, double radius) {
        BlockPos best = null;
        double bestD = radius * radius;
        for (long packed : data.camps) {
            BlockPos camp = BlockPos.of(packed);
            double dx = camp.getX() - near.getX();
            double dz = camp.getZ() - near.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                best = camp;
                bestD = d;
            }
        }
        return best;
    }

    // 語り部の野営を、位置 around の 18〜34 ブロック先の自然の地面に作る (焚き火を置き、野営として記録する)。置けなければ null。
    // 語り部本人は、このあと visitVillage が (焚き火を鐘の代わりにして) 立たせる
    @Nullable
    public static BlockPos buildCamp(ServerLevel level, BlockPos around, RandomSource random) {
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int r = 18 + random.nextInt(17);
            BlockPos column = around.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos at = ground(level, column, 12, 16);
            if (at == null || !openSky(level, at) || !natural(level.getBlockState(at.below()))) {
                continue;
            }
            BlockPos fire = campfireSpot(level, at, random);
            if (fire == null) {
                continue;
            }
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
            NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
            data.camps.add(fire.asLong());
            data.setDirty();
            SkyAbyss.LOGGER.info("[npc] a storyteller's camp was made at {}", fire);
            return fire;
        }
        return null;
    }

    public static void visitVillage(ServerLevel level, BlockPos bell, ServerPlayer player) {
        if (!entitiesLoaded(level, bell)) {
            return;
        }
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        long now = level.getGameTime();
        if (find(level, bell, NpcSetup.STORYTELLER.get()) == null && canRespawn(data, bell, NpcSetup.STORYTELLER_ID, now)) {
            NpcEntity npc = spawnStoryteller(level, bell);
            if (npc != null) {
                data.markSpawned(bell.asLong(), NpcSetup.STORYTELLER_ID, now);
            }
        }
        if (needsChaser(player) && find(level, bell, NpcSetup.WHALE_CHASER.get()) == null
                && canRespawn(data, bell, NpcSetup.WHALE_CHASER_ID, now)) {
            NpcEntity npc = spawnChaser(level, bell, level.random);
            if (npc != null) {
                data.markSpawned(bell.asLong(), NpcSetup.WHALE_CHASER_ID, now);
                data.markChaserFire(bell.asLong(), npc.home().asLong());
            }
        }
    }

    // 鐘 bell の村の鯨追いの焚き火の位置。立っていればその今の足もと、いなければ立たせたときの記録 (なければ null)。
    // 目的の帯の「宿りの外れの焚き火」の目印 (語り部の鐘の位置ではない)
    @Nullable
    public static BlockPos chaserSpot(ServerLevel level, BlockPos bell) {
        NpcEntity live = find(level, bell, NpcSetup.WHALE_CHASER.get());
        if (live != null) {
            return live.home() != null ? live.home() : live.blockPosition();
        }
        return NpcWorldData.get(level.getServer().overworld()).chaserFire(bell);
    }

    static boolean canRespawn(NpcWorldData data, BlockPos bell, String role, long now) {
        long at = data.spawnedAt(bell.asLong(), role);
        return at < 0 || now - at > RESPAWN_AFTER || now < at;
    }

    // 鐘のまわり 3×3 チャンクの生き物が読み込まれているか (読み込み前に「いない」と見て二人目を立たせないため)
    static boolean entitiesLoaded(ServerLevel level, BlockPos bell) {
        ChunkPos c = new ChunkPos(bell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(c.x + dx, c.z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    static NpcEntity find(ServerLevel level, BlockPos bell, EntityType<NpcEntity> type) {
        List<NpcEntity> found = level.getEntities(type, new AABB(bell).inflate(SEARCH), NpcEntity::isAlive);
        return found.isEmpty() ? null : found.get(0);
    }

    // 鯨追いを要る人か: 進行中のクエストの今の段階に「鯨追いと話す」目的がある、または依頼主が鯨追いのクエストが進行中
    public static boolean needsChaser(ServerPlayer player) {
        for (Map.Entry<String, QuestLog.Entry> e : QuestLog.of(player).quests().entrySet()) {
            QuestDef def = QuestManager.quest(e.getKey());
            QuestLog.Entry entry = e.getValue();
            if (def == null || entry.done || entry.stage >= def.stages().size()) {
                continue;
            }
            if (NpcSetup.WHALE_CHASER_ID.equals(def.giver())) {
                return true;
            }
            for (QuestDef.Objective ob : def.stages().get(entry.stage).objectives()) {
                if (ob.type().equals("talk") && ob.targets().contains(NpcSetup.WHALE_CHASER_ID)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 立たせる
    @Nullable
    public static NpcEntity spawnStoryteller(ServerLevel level, BlockPos bell) {
        RandomSource random = level.random;
        for (int i = 0; i < 40; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int r = 2 + random.nextInt(5);
            BlockPos at = ground(level, bell.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r)), 4, 6);
            if (at != null) {
                return place(level, NpcSetup.STORYTELLER.get(), at, bell);
            }
        }
        BlockPos at = ground(level, bell, 4, 8);
        return at == null ? null : place(level, NpcSetup.STORYTELLER.get(), at, bell);
    }

    @Nullable
    public static NpcEntity spawnChaser(ServerLevel level, BlockPos bell, RandomSource random) {
        for (int i = 0; i < 60; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int r = 18 + random.nextInt(17);
            BlockPos column = bell.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos at = ground(level, column, 12, 16);
            if (at == null || !openSky(level, at) || !natural(level.getBlockState(at.below()))) {
                continue;
            }
            BlockPos fire = campfireSpot(level, at, random);
            if (fire == null) {
                continue;
            }
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
            return place(level, NpcSetup.WHALE_CHASER.get(), at, fire);
        }
        // 外れに場所がなければ、鐘のそばに (火は焚かない)
        BlockPos at = ground(level, bell.offset(3, 0, 3), 4, 8);
        return at == null ? null : place(level, NpcSetup.WHALE_CHASER.get(), at, at);
    }

    // 立っている人の隣 (1〜2 ブロック先) の、自然の地面の上の空き
    @Nullable
    private static BlockPos campfireSpot(ServerLevel level, BlockPos at, RandomSource random) {
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        int start = random.nextInt(4);
        for (int k = 0; k < 4; k++) {
            BlockPos p = at.relative(dirs[(start + k) % 4], 2);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && openSky(level, p) && natural(level.getBlockState(p.below()))
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                return p;
            }
        }
        return null;
    }

    // 真上に何もない (木の葉も屋根もない)。canSeeSky は空の明るさで見るので、読み込んだばかりのチャンクや葉の下で外れることがある
    private static boolean openSky(ServerLevel level, BlockPos p) {
        return p.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
    }

    private static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.STONE);
    }

    // column の上下 (up 上から down 下まで) で、足もとが固く、体の入る 2 ブロックが空いている所
    @Nullable
    static BlockPos ground(ServerLevel level, BlockPos column, int up, int down) {
        for (int dy = up; dy >= -down; dy--) {
            BlockPos p = column.above(dy);
            BlockState below = level.getBlockState(p.below());
            if (below.isFaceSturdy(level, p.below(), Direction.UP) && free(level, p) && free(level, p.above())) {
                return p;
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    @Nullable
    static NpcEntity place(ServerLevel level, EntityType<NpcEntity> type, BlockPos at, BlockPos home) {
        NpcEntity npc = type.create(level);
        if (npc == null) {
            return null;
        }
        npc.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        npc.setHome(home);
        npc.nameByRole();
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(npc);
        SkyAbyss.LOGGER.info("[npc] {} appeared at {} (home {})", npc.role(), at, home);
        return npc;
    }
}
