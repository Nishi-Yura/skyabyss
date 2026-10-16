package jp.revati.skyabyss.quest.free;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.sky.SkyEvents;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 自由なモードのサブクエストの出来事を拾う。ほかの担当のコードには手を入れず、状態を見て「合図」を送る。
//   sky_shadow_seen  : 空の影の通過を、空の見える所で見た (一つの通過につき一度)
//   tide_low_watched : 干潮の刻に、休んでいる潮殻の巨神のそば (96 ブロック) にいた (一つの刻につき一度)
//   back_trial_cleared : 岩鯨の背の試練を踏破した (GrowthRewards から。居合わせた人と、同じ背中の仲間)
// あわせて、村の鐘のそばに言伝ての板を一枚立てる (村ごとに一度だけ)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FreeQuestEvents {
    public static final String SHADOW_SEEN = "sky_shadow_seen";
    public static final String TIDE_WATCHED = "tide_low_watched";
    public static final String TRIAL_CLEARED = "back_trial_cleared";
    // 伝承の書の「空の影を見た」と同じ距離
    static final double SHADOW_WITNESS = 150;
    static final double TIDE_WATCH = 96;
    private static final int VILLAGE_RADIUS = 48;

    private FreeQuestEvents() {
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime() + (player.getId() & 0xFF);
        if (now % 20 == 11) {
            watchShadow(player, level);
        }
        if (now % 40 == 13) {
            watchTide(player, level);
        }
        if (now % 100 == 17) {
            Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), player.blockPosition(), VILLAGE_RADIUS,
                    PoiManager.Occupancy.ANY);
            bell.ifPresent(b -> placeBoard(level, b));
        }
    }

    // ---------------------------------------------------------------- 空の影
    static void watchShadow(ServerPlayer player, ServerLevel level) {
        SkyPass pass = SkyEvents.activePass();
        long t = level.getGameTime();
        if (pass == null || !pass.isActive(t)) {
            return;
        }
        double age = pass.age(t, 0f);
        if (pass.storm(age) < 0.25f) {
            return;
        }
        double dx = player.getX() - pass.centerX(age);
        double dz = player.getZ() - pass.centerZ(age);
        if (dx * dx + dz * dz > SHADOW_WITNESS * SHADOW_WITNESS || !level.canSeeSky(player.blockPosition().above())) {
            return;
        }
        if (FreeQuestData.get(level).firstPass(player.getUUID(), pass.startTick())) {
            Quests.signal(player, SHADOW_SEEN);
        }
    }

    // ---------------------------------------------------------------- 干潮
    // 干潮の刻の番号 (朝 23000〜2600 と夕 11000〜14600 が、それぞれ別の番号になる)
    public static long tideIndex(long dayTime) {
        return Math.floorDiv(dayTime + 1000, 12000);
    }

    static void watchTide(ServerPlayer player, ServerLevel level) {
        TideShellEntity tide = TideKeeper.KEEPER.current(level);
        if (tide == null || !tide.isResting() || tide.distanceToSqr(player) > TIDE_WATCH * TIDE_WATCH) {
            return;
        }
        if (FreeQuestData.get(level).firstTide(player.getUUID(), tideIndex(level.getDayTime()))) {
            Quests.signal(player, TIDE_WATCHED);
        }
    }

    // ---------------------------------------------------------------- 板を立てる
    static void placeBoard(ServerLevel level, BlockPos bell) {
        FreeQuestData data = FreeQuestData.get(level);
        if (data.boards.contains(bell.asLong()) || !loaded(level, bell)) {
            return;
        }
        if (findBoard(level, bell) != null) {
            data.boards.add(bell.asLong());
            data.setDirty();
            return;
        }
        BlockPos at = spot(level, bell);
        if (at == null) {
            return;
        }
        Direction toBell = Direction.getNearest(bell.getX() - at.getX(), 0, bell.getZ() - at.getZ());
        if (toBell.getAxis() == Direction.Axis.Y) {
            toBell = Direction.NORTH;
        }
        BlockState state = FreeQuestSetup.MESSAGE_BOARD.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, toBell);
        level.setBlock(at, state, 3);
        data.boards.add(bell.asLong());
        data.setDirty();
        SkyAbyss.LOGGER.info("[board] message board placed at {} (bell {})", at, bell);
    }

    private static boolean loaded(ServerLevel level, BlockPos bell) {
        ChunkPos c = new ChunkPos(bell);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(c.x + dx, c.z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    static BlockPos findBoard(ServerLevel level, BlockPos bell) {
        for (BlockPos p : BlockPos.betweenClosed(bell.offset(-12, -6, -12), bell.offset(12, 6, 12))) {
            if (level.getBlockState(p).is(FreeQuestSetup.MESSAGE_BOARD.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    // 鐘から 3〜6 ブロックの、足もとが固く、置く所と上が空いている所。近い所から探す
    @Nullable
    static BlockPos spot(ServerLevel level, BlockPos bell) {
        for (int r = 3; r <= 6; r++) {
            for (int i = 0; i < 16; i++) {
                double a = (i / 16.0 + r * 0.13) * Math.PI * 2;
                BlockPos column = bell.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r));
                for (int dy = 3; dy >= -5; dy--) {
                    BlockPos p = column.above(dy);
                    BlockState here = level.getBlockState(p);
                    if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                            && (here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty()
                            && level.getBlockState(p.above()).isAir()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }
}
