package jp.revati.skyabyss.grave;

import java.util.Iterator;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

// 前の版の墓 (docs/design/40 の 2 章)。2026-11-05 の決めごと ct_grave = cut で墓はもう作らない。
// 前の世界に残った墓は、持ち主が開けると中身 (と経験値) がすべて手もとへ戻り、空になると消える。入りきらない分は墓に残る。
// 中身の出入りは必ず GraveData から消してから渡す
public final class Graves {
    private Graves() {
    }

    // ---------------------------------------------------------------- 開ける
    public static boolean canOpen(ServerPlayer player, GraveData.Grave grave) {
        if (grave.owner.equals(player.getUUID())) {
            return true;
        }
        return player.isCreative() && player.hasPermissions(2);
    }

    public static void interact(ServerPlayer player, ServerLevel level, BlockPos pos) {
        GraveData data = GraveData.get(level.getServer());
        GraveData.Grave grave = data.at(level.dimension(), pos);
        if (grave == null) {
            // 控えのない墓: 消す
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
            // 留め袋に入る品は袋へ (22 の 4.2)。品ごとの知らせは出さない (最後に一行だけ)
            jp.revati.skyabyss.pouch.Pouch.quiet = true;
            try {
                jp.revati.skyabyss.pouch.PouchRouting.give(player, copy);
            } finally {
                jp.revati.skyabyss.pouch.Pouch.quiet = false;
            }
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
        jp.revati.skyabyss.advancement.Beats.fire(player, "grave:open");
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

    // 見回り (ブロックの予約された tick): 控えのない墓を消す
    static void patrol(ServerLevel level, BlockPos pos, Block block) {
        GraveData.Grave grave = GraveData.get(level.getServer()).at(level.dimension(), pos);
        if (grave == null) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, block, GraveBlock.CHECK_TICKS);
    }
}
