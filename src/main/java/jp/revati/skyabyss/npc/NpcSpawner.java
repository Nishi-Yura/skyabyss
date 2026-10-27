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
//  - 語り部: 都の大鐘楼にプレイヤーが近づいたとき、まだいなければ、鐘のそばに一人立たせる
//  - 鯨追い: 鯨見の町の見張り台のそば (ContinentTowns が keepTownChaser を呼ぶ)
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
        // 語り部と鯨追いは都の大鐘楼にだけ立つ (2026-10-25 の試遊: 野の村・野営に立つ語り部はいらない)。
        // 前の版で野に立った二人は、近くを通ったときに消す
        removeWild(level, player);
        // 天と淵の大陸: 鯨追いは鯨見の町に立つ (ContinentTowns が keepTownChaser を呼ぶ)。都の鐘では語り部だけ
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), VILLAGE_RADIUS,
                PoiManager.Occupancy.ANY);
        if (bell.isPresent() && jp.revati.skyabyss.opening.site.SiteGuard.inCapital(level, bell.get())) {
            visitVillage(level, capitalBell(level, bell.get()), player);
        }
    }

    private static void removeWild(ServerLevel level, ServerPlayer player) {
        net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(SEARCH);
        for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, box,
                n -> (n.getType() == NpcSetup.STORYTELLER.get() || n.getType() == NpcSetup.WHALE_CHASER.get())
                        && !jp.revati.skyabyss.opening.site.SiteGuard.inCapital(level, n.blockPosition())
                        && !townChaser(level, n))) {
            SkyAbyss.LOGGER.info("[npc] removed a wild {} at {} (only the capital's bell tower keeps them)", npc.getType().toShortString(), npc.blockPosition());
            npc.discard();
        }
    }

    // 都の中の鐘は、どれも大鐘楼の鐘として扱う (都には宿の屋根の鐘楼などほかの鐘もあり、そこを村の鐘と見て語り部とイサナを
    // もう一人ずつ立たせていた。2026-10-21 の試遊)
    static BlockPos capitalBell(ServerLevel level, BlockPos bell) {
        BlockPos main = jp.revati.skyabyss.opening.OpeningSites.bell(level.getServer()).orElse(null);
        if (main == null || main.equals(bell)) {
            return bell;
        }
        return jp.revati.skyabyss.opening.site.SiteGuard.inCapital(level, bell) ? main : bell;
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
    }

    // 天と淵の大陸の鯨見の町の鯨追い (立つ所 spot の近く)。野に立つ二人を消すときに残す
    static boolean townChaser(ServerLevel level, NpcEntity n) {
        if (n.getType() != NpcSetup.WHALE_CHASER.get() || !jp.revati.skyabyss.world.continent.ContinentPois.in(level)) {
            return false;
        }
        BlockPos spot = jp.revati.skyabyss.world.continent.ContinentTowns.chaserSpot(level.getServer());
        return spot != null && n.blockPosition().distSqr(spot) <= SEARCH * SEARCH;
    }

    // 鯨見の町の鯨追い: 要る人が近くにいて、立っていなければ立たせる (一日おき)。離れていたら戻す
    public static void keepTownChaser(ServerLevel level, BlockPos spot, ServerPlayer player) {
        if (!entitiesLoaded(level, spot)) {
            return;
        }
        NpcEntity live = find(level, spot, NpcSetup.WHALE_CHASER.get());
        if (live != null) {
            if (live.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 64) {
                live.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            }
            return;
        }
        NpcWorldData data = NpcWorldData.get(level.getServer().overworld());
        long now = level.getGameTime();
        if (!needsChaser(player) || !canRespawn(data, spot, NpcSetup.WHALE_CHASER_ID, now)) {
            return;
        }
        NpcEntity npc = place(level, NpcSetup.WHALE_CHASER.get(), spot, spot);
        if (npc != null) {
            data.markSpawned(spot.asLong(), NpcSetup.WHALE_CHASER_ID, now);
        }
    }

        return NpcWorldData.get(level.getServer().overworld()).chaserFire(bell);
    }

    // 都 (鐘の宿り) の鐘なら、語り部の立つ所 = 鐘の 2 下 (一階の床) の、鐘から南へ 2。都でなければ null
    @Nullable
    public static BlockPos capitalStorytellerSpot(ServerLevel level, BlockPos bell) {
        if (jp.revati.skyabyss.opening.OpeningSites.chaserCamp(level.getServer()).isEmpty()) {
            return null;
        }
        BlockPos b = jp.revati.skyabyss.opening.OpeningSites.bell(level.getServer()).orElse(null);
        if (b == null || !b.equals(bell)) {
            return null;
        }
        return bell.offset(0, -2, 2);
    }

    // 都の語り部が決まった所から離れていたら (前の版で台座の縁に立った・押された)、戻す
    public static void keepCapitalStoryteller(ServerLevel level, BlockPos bell) {
        BlockPos spot = capitalStorytellerSpot(level, bell);
        if (spot == null || !level.isLoaded(spot)) {
            return;
        }
        NpcEntity npc = find(level, bell, NpcSetup.STORYTELLER.get());
        if (npc == null) {
            return;
        }
        if (Math.abs(npc.getY() - spot.getY()) > 1.5 || npc.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) > 16) {
            npc.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            npc.setHome(bell);
            SkyAbyss.LOGGER.info("[npc] storyteller moved back to {}", spot);
        }
    }

    static boolean canRespawn(NpcWorldData data, BlockPos bell, String role, long now) {
        long at = data.spawnedAt(bell.asLong(), role);
        return at < 0 || now - at > RESPAWN_AFTER || now < at;
    }

    // 鐘のまわり (探す半径 SEARCH の届くチャンク) の生き物が読み込まれているか (読み込み前に「いない」と見て二人目を立たせないため。
    // 都の鯨追いは鐘から 30 以上離れて立つので、3×3 では足りなかった)
    static boolean entitiesLoaded(ServerLevel level, BlockPos bell) {
        ChunkPos c = new ChunkPos(bell);
        int r = (int) Math.ceil(SEARCH / 16.0);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
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
        // 同じ村に同じ役が二人以上いたら (前の版の二重立ち)、一人を残して消す
        for (int i = 1; i < found.size(); i++) {
            SkyAbyss.LOGGER.info("[npc] removed a duplicate {} at {}", type.toShortString(), found.get(i).blockPosition());
            found.get(i).discard();
        }
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
        // 都の大鐘楼: 一階の鐘の足もと (外の台座の縁や上の段に立たないように、決まった所に)
        BlockPos spot = capitalStorytellerSpot(level, bell);
        if (spot != null) {
            return place(level, NpcSetup.STORYTELLER.get(), spot, bell);
        }
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
        // 都の鐘なら、設計図が用意した野営の場所 (庭の草地) を先に使う
        BlockPos camp = jp.revati.skyabyss.opening.OpeningSites.chaserCamp(level.getServer()).orElse(null);
        if (camp != null && camp.distSqr(bell) < 80 * 80) {
            level.getChunk(camp);
            BlockPos at = ground(level, camp, 3, 3);
            jp.revati.skyabyss.SkyAbyss.LOGGER.info("[npc] chaser camp {} -> {}", camp, at);
            if (at != null) {
                BlockPos fire = campfireSpot(level, at, random);
                if (fire != null) {
                    level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 3);
                    return place(level, NpcSetup.WHALE_CHASER.get(), at, fire);
                }
                return place(level, NpcSetup.WHALE_CHASER.get(), at, at);
            }
        }
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
