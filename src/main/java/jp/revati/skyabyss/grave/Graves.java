package jp.revati.skyabyss.grave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.CampBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// 墓の置き方・開け方・救い方 (docs/design/11_record_screen.md の 6 章)。
// 中身の出入りは必ず GraveData から消してから渡す。入りきらない分は墓に残す (開けるとき) か、報酬の預かりへ入れる (救うとき)
public final class Graves {
    // 墓を置く場所を探す範囲 (死んだ場所の周り)
    static final int SEARCH_RADIUS = 8;
    static final int SEARCH_VERTICAL = 6;

    // 自己テスト用: true の間は置ける所がないものとして扱う
    static volatile boolean testNoSpot;

    private Graves() {
    }

    // ---------------------------------------------------------------- 置く場所
    public record Spot(ServerLevel level, BlockPos pos) {
    }

    private static List<BlockPos> offsets(int horizontal, int vertical) {
        List<BlockPos> list = new ArrayList<>();
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    list.add(new BlockPos(dx, dy, dz));
                }
            }
        }
        list.sort(Comparator.comparingInt((BlockPos p) -> p.getX() * p.getX() + p.getY() * p.getY() * 2 + p.getZ() * p.getZ())
                .thenComparingInt(BlockPos::getY));
        return list;
    }

    private static final List<BlockPos> NEAR = offsets(SEARCH_RADIUS, SEARCH_VERTICAL);

    // 墓を置いてよい所: 本物の空気 (何も壊さず、置き換えもしない) で、液体・火・溶岩のそばでなく、足もとが固く、
    // 戦いの部屋・仕掛けの足場や仕掛けのそばでない
    public static boolean isSafeSpot(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() || !here.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
            return false;
        }
        if (CampBlocks.inArena(level.dimension(), pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty()
                || CampBlocks.isMechanism(floor) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.CACTUS)) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getFluidState(n).is(FluidTags.LAVA) || level.getBlockState(n).is(BlockTags.FIRE)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static BlockPos search(ServerLevel level, BlockPos center, int horizontal, int vertical) {
        List<BlockPos> list = horizontal == SEARCH_RADIUS && vertical == SEARCH_VERTICAL ? NEAR : offsets(horizontal, vertical);
        for (BlockPos o : list) {
            BlockPos p = center.offset(o);
            if (isSafeSpot(level, p)) {
                return p;
            }
        }
        return null;
    }

    // 死んだ場所 → 周りの安全な所 → 最後に安全だった場所 → その柱の地表 → 世界の初めの場所 の順に探す。
    // 奈落・溶岩・壁の中で倒れても、取りに行ける所へ置く。ブロックは決して壊さない・置き換えない。
    // どこにも置けなければ null (呼んだ側が報酬の預かりへ回す)
    @Nullable
    public static Spot chooseSpot(ServerLevel level, BlockPos origin, @Nullable BlockPos lastSafe) {
        MinecraftServer server = level.getServer();
        if (testNoSpot) {
            return null;
        }
        if (origin.getY() > level.getMinBuildHeight() && origin.getY() < level.getMaxBuildHeight()) {
            BlockPos p = search(level, origin, SEARCH_RADIUS, SEARCH_VERTICAL);
            if (p != null) {
                return new Spot(level, p);
            }
        }
        if (lastSafe != null) {
            BlockPos p = search(level, lastSafe, 4, 4);
            if (p != null) {
                return new Spot(level, p);
            }
        }
        if (level.hasChunkAt(origin)) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin);
            BlockPos p = search(level, top, 8, 8);
            if (p != null) {
                return new Spot(level, p);
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        BlockPos p = search(overworld, top, 16, 12);
        if (p != null) {
            return new Spot(overworld, p);
        }
        return null;
    }

    // ---------------------------------------------------------------- 作る
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // 墓を作って置く。items は呼んだ側がもう持ち物から外した品 (コピーして持つ)
    // 置ける所がなければ墓は作らず、品と経験値を報酬の預かりへ入れて null を返す (控え帳で受け取れる)
    @Nullable
    public static GraveData.Grave create(ServerPlayer owner, ServerLevel deathLevel, BlockPos origin, @Nullable BlockPos lastSafe, List<ItemStack> items, int xp) {
        Spot spot = chooseSpot(deathLevel, origin, lastSafe);
        if (spot == null) {
            TitanMemoryData.Pending pending = TitanMemoryData.get(owner.server).pending(owner.getUUID());
            for (ItemStack s : items) {
                if (!s.isEmpty()) {
                    pending.items.add(s.copy());
                }
            }
            pending.xp += xp;
            SkyAbyss.LOGGER.info("[grave] no free spot near {} for {}: {} stacks and {} xp went to the pending reward box", origin.toShortString(), owner.getGameProfile().getName(),
                    items.size(), xp);
            return null;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                copies.add(s.copy());
            }
        }
        MinecraftServer server = owner.server;
        GraveData.Grave grave = new GraveData.Grave(UUID.randomUUID(), owner.getUUID(), owner.getGameProfile().getName(), spot.level().dimension(),
                spot.pos(), now(server), xp, copies);
        GraveData.get(server).add(grave);
        jp.revati.skyabyss.tutorial.TutorialHints.start(owner, jp.revati.skyabyss.tutorial.TutorialHints.RECORD);
        spot.level().setBlock(spot.pos(), ModBlocks.GRAVE.get().defaultBlockState(), Block.UPDATE_ALL);
        jp.revati.skyabyss.rebirth.RebirthFx.graveCreated(owner, spot.level(), spot.pos());
        SkyAbyss.LOGGER.info("[grave] {} made a grave at {} {} ({} stacks, {} xp)", grave.ownerName, grave.dimension.location(), spot.pos().toShortString(),
                copies.size(), xp);
        return grave;
    }

    // ---------------------------------------------------------------- 開ける
    public static boolean canOpen(ServerPlayer player, GraveData.Grave grave) {
        if (grave.owner.equals(player.getUUID()) || !DeathRules.ownerOnly(player.server)) {
            return true;
        }
        return player.isCreative() && player.hasPermissions(2);
    }

    public static void interact(ServerPlayer player, ServerLevel level, BlockPos pos) {
        GraveData data = GraveData.get(level.getServer());
        GraveData.Grave grave = data.at(level.dimension(), pos);
        if (grave == null) {
            // 控えのない墓 (救いのあとなど): 消す
            level.removeBlock(pos, false);
            return;
        }
        if (!canOpen(player, grave)) {
            Notice.warn(player, "skyabyss_grave.not_yours", grave.ownerName);
            return;
        }
        collect(player, grave);
    }

    public record Collected(int stacks, int items, int xp, int leftStacks) {
    }

    // 墓の中身を手もとへ移す。持ち物に入りきらない分は墓に残す (空になるまで墓は消えない)
    public static Collected collect(ServerPlayer player, GraveData.Grave grave) {
        MinecraftServer server = player.server;
        GraveData data = GraveData.get(server);
        int movedItems = 0;
        int movedStacks = 0;
        Iterator<ItemStack> it = grave.items.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            ItemStack copy = s.copy();
            int before = copy.getCount();
            // 留め袋に入る品は袋へ (22 の 4.2)
            jp.revati.skyabyss.pouch.PouchRouting.give(player, copy);
            int moved = before - copy.getCount();
            movedItems += moved;
            if (copy.isEmpty()) {
                movedStacks++;
                it.remove();
            } else if (moved > 0) {
                s.setCount(copy.getCount());
            }
        }
        // 経験値は持ち物と関係なく全部渡す
        int xp = grave.xp;
        if (xp > 0) {
            player.giveExperiencePoints(xp);
            grave.xp = 0;
        }
        int left = grave.items.size();
        if (grave.isEmpty()) {
            data.remove(grave.id);
            removeBlock(server, grave);
            Notice.unlock(player, "skyabyss_grave.collected", movedItems, xp);
        } else {
            data.touch();
            Notice.warn(player, "skyabyss_grave.collected_part", movedItems, left);
        }
        return new Collected(movedStacks, movedItems, xp, left);
    }

    // 中身を外から減らしたあと (留め袋への移し替え。docs/design/22 の 8.4) に呼ぶ: 空になった墓は控えとブロックを消し、残るなら控えを保存し直す
    public static void settle(MinecraftServer server, GraveData.Grave grave) {
        GraveData data = GraveData.get(server);
        grave.items.removeIf(ItemStack::isEmpty);
        if (grave.isEmpty()) {
            data.remove(grave.id);
            removeBlock(server, grave);
        } else {
            data.touch();
        }
    }

    private static void removeBlock(MinecraftServer server, GraveData.Grave grave) {
        ServerLevel level = server.getLevel(grave.dimension);
        if (level != null && level.hasChunkAt(grave.pos) && level.getBlockState(grave.pos).is(ModBlocks.GRAVE.get())) {
            level.removeBlock(grave.pos, false);
        }
    }

    // ---------------------------------------------------------------- 落とす (ブロックが壊された・時間が来た)
    public static void spill(ServerLevel level, BlockPos pos) {
        GraveData data = GraveData.get(level.getServer());
        GraveData.Grave grave = data.at(level.dimension(), pos);
        if (grave == null) {
            return;
        }
        data.remove(grave.id);
        spillContents(level, pos, grave);
    }

    private static void spillContents(ServerLevel level, BlockPos pos, GraveData.Grave grave) {
        Vec3 c = Vec3.atCenterOf(pos);
        for (ItemStack s : grave.items) {
            ItemEntity e = new ItemEntity(level, c.x, c.y, c.z, s.copy());
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        if (grave.xp > 0) {
            ExperienceOrb.award(level, c, grave.xp);
        }
        SkyAbyss.LOGGER.info("[grave] spilled {}'s grave at {} ({} stacks)", grave.ownerName, pos.toShortString(), grave.items.size());
    }

    // 見回り (ブロックの予約された tick): 控えのない墓を消し、時間が来た墓を壊す
    static void patrol(ServerLevel level, BlockPos pos, Block block) {
        MinecraftServer server = level.getServer();
        GraveData data = GraveData.get(server);
        GraveData.Grave grave = data.at(level.dimension(), pos);
        if (grave == null) {
            level.removeBlock(pos, false);
            return;
        }
        int expire = DeathRules.expireMinutes(server);
        if (expire > 0 && now(server) - grave.created >= expire * 1200L) {
            data.remove(grave.id);
            spillContents(level, pos, grave);
            level.removeBlock(pos, false);
            ServerPlayer owner = server.getPlayerList().getPlayer(grave.owner);
            if (owner != null) {
                Notice.warn(owner, "skyabyss_grave.expired");
            }
            return;
        }
        level.scheduleTick(pos, block, GraveBlock.CHECK_TICKS);
    }

    // ---------------------------------------------------------------- 語り部の救い
    // 各品を割合で等しく間引く (積み上げた個数の通し番号で、割合を超えた所の品だけ戻す。件数は切り捨て)
    public static List<ItemStack> share(List<ItemStack> items, int percent) {
        List<ItemStack> out = new ArrayList<>();
        long before = 0;
        for (ItemStack s : items) {
            long after = before + s.getCount();
            long give = after * percent / 100 - before * percent / 100;
            if (give > 0) {
                ItemStack part = s.copy();
                part.setCount((int) give);
                out.add(part);
            }
            before = after;
        }
        return out;
    }

    public record Rescued(int items, int xp, int toPending, int lostItems) {
    }

    public static boolean rescueAvailable(ServerPlayer player) {
        return DeathRules.rescuePercent(player.server) > 0 && !GraveData.get(player.server).owned(player.getUUID()).isEmpty();
    }

    // いちばん古い墓を一つ救う (割合は skyabyssGraveRescue)。墓は消え、残りの分は土に還る。
    // 持ち物に入りきらない分は報酬の預かりへ (控え帳で受け取れる)。救うものがなければ null
    @Nullable
    public static Rescued rescue(ServerPlayer player) {
        List<GraveData.Grave> owned = GraveData.get(player.server).owned(player.getUUID());
        return owned.isEmpty() ? null : rescue(player, owned.get(0));
    }

    // 選んだ墓を一つ救う (自分の墓だけ。割合が 0、または自分の墓でなければ null)
    @Nullable
    public static Rescued rescue(ServerPlayer player, GraveData.Grave grave) {
        MinecraftServer server = player.server;
        int percent = DeathRules.rescuePercent(server);
        GraveData data = GraveData.get(server);
        if (percent <= 0 || !grave.owner.equals(player.getUUID()) || data.get(grave.id) != grave) {
            return null;
        }
        data.remove(grave.id);
        List<ItemStack> back = share(grave.items, percent);
        int given = 0;
        int pending = 0;
        for (ItemStack s : back) {
            ItemStack copy = s.copy();
            int before = copy.getCount();
            jp.revati.skyabyss.pouch.PouchRouting.give(player, copy);
            given += before - copy.getCount();
            if (!copy.isEmpty()) {
                TitanMemoryData.get(server).pending(player.getUUID()).items.add(copy);
                pending += copy.getCount();
            }
        }
        int xp = grave.xp * percent / 100;
        if (xp > 0) {
            player.giveExperiencePoints(xp);
        }
        removeBlock(server, grave);
        int total = grave.itemCount();
        SkyAbyss.LOGGER.info("[grave] rescued {}'s grave at {} ({} of {} items, {} to pending)", grave.ownerName, grave.pos.toShortString(), given + pending, total, pending);
        return new Rescued(given + pending, xp, pending, total - given - pending);
    }
}
