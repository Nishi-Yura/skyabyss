package jp.revati.skyabyss.titan.group;

import java.util.Collection;
import java.util.UUID;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// 扉 (封印の扉 = SealedGateBlock) のグループごとの開け閉め。
// 扉のブロックはワールドにいつも置いてあり (開いているものも OPEN=true の扉として)、グループの記録 (BackGroupData.Record) に
// 「このグループには開けた/閉めた」という上書きを持つ。上書きがなければブロックの OPEN (既定) のまま。
//
// 使い方 (試練のコードが、これまで setBlock(SEALED_GATE / AIR) していた所):
//   place(level, 扉の座標, 既定で開いているか)      建てたあと・起動のあとに一度。ワールドの扉ブロックを (再) 配置する
//   open(level, グループ, 扉の座標) / seal(...)     そのグループにだけ開ける・閉める
//   isOpen(level, グループ, 扉の座標)               そのグループにとって開いているか (旧 isSealed の代わり)
//   reset(level, グループ)                          そのグループの上書きを全部消す (リセット)
public final class GroupGates {
    private GroupGates() {
    }

    // 当たり判定から呼ばれる: グループの上書き > ブロックの既定
    public static boolean effective(ServerLevel level, UUID group, BlockPos pos, boolean def) {
        BackGroupData.Record r = BackGroupData.get(level).peek(group);
        if (r == null) {
            return def;
        }
        long key = pos.asLong();
        if (r.openOverride.contains(key)) {
            return true;
        }
        if (r.closedOverride.contains(key)) {
            return false;
        }
        return def;
    }

    // 扉のブロックを置く (建て直し・起動時に呼ぶ。座標に何があっても置き換える)。defaultOpen はグループの上書きがないときの状態
    public static void place(ServerLevel level, Collection<BlockPos> blocks, boolean defaultOpen) {
        BlockState state = ModBlocks.SEALED_GATE.get().defaultBlockState().setValue(SealedGateBlock.OPEN, defaultOpen);
        for (BlockPos p : blocks) {
            if (level.getBlockState(p) != state) {
                level.setBlock(p, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // ワールド全体の既定を変える (潮で開く扉など、進みと関係なく全員に同じに変わるもの)。座標が扉でなければ置く
    public static void setDefault(ServerLevel level, Collection<BlockPos> blocks, boolean open) {
        place(level, blocks, open);
    }

    // 扉のブロックがその座標にあるか
    public static boolean isGate(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.SEALED_GATE.get());
    }

    // そのグループにとって開いているか。扉のブロックがなければ (空気など) 開いているとみなす
    public static boolean isOpen(ServerLevel level, UUID group, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.SEALED_GATE.get())) {
            return true;
        }
        return effective(level, group, pos, state.getValue(SealedGateBlock.OPEN));
    }

    // 扉の先頭の一つで調べる (扉は一続きのブロックなので)
    public static boolean isOpen(ServerLevel level, UUID group, Collection<BlockPos> door) {
        return door.isEmpty() || isOpen(level, group, door.iterator().next());
    }

    // そのグループにだけ開ける。変わったら true (粒と、グループの人の画面への反映を行う)
    public static boolean open(ServerLevel level, UUID group, Collection<BlockPos> blocks) {
        return set(level, group, blocks, true);
    }

    // そのグループにだけ閉める
    public static boolean seal(ServerLevel level, UUID group, Collection<BlockPos> blocks) {
        return set(level, group, blocks, false);
    }

    public static boolean set(ServerLevel level, UUID group, Collection<BlockPos> blocks, boolean open) {
        if (blocks.isEmpty()) {
            return false;
        }
        BackGroupData data = BackGroupData.get(level);
        BackGroupData.Record record = data.of(group);
        boolean changed = false;
        for (BlockPos p : blocks) {
            BlockState state = level.getBlockState(p);
            if (!state.is(ModBlocks.SEALED_GATE.get())) {
                continue;
            }
            boolean was = effective(level, group, p, state.getValue(SealedGateBlock.OPEN));
            long key = p.asLong();
            // 既定と同じなら上書きを持たない (記録を小さく保つ)
            if (open == state.getValue(SealedGateBlock.OPEN)) {
                changed |= record.openOverride.remove(key) | record.closedOverride.remove(key);
            } else if (open) {
                changed |= record.openOverride.add(key) | record.closedOverride.remove(key);
            } else {
                changed |= record.closedOverride.add(key) | record.openOverride.remove(key);
            }
            if (open && !was) {
                for (var player : Groups.online(level, group)) {
                    if (player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < 48 * 48) {
                        level.sendParticles(player, ParticleTypes.REVERSE_PORTAL, false, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3,
                                0.3, 0.3, 0.02);
                    }
                }
            }
        }
        if (changed) {
            data.setDirty();
            GroupOverlays.refresh(level, group);
        }
        return changed;
    }

    // そのグループへの上書きをやめて、ブロックの既定に戻す (既定が時間で変わる扉の「閉じていたのをやめる」はこちら)
    public static boolean release(ServerLevel level, UUID group, Collection<BlockPos> blocks) {
        BackGroupData data = BackGroupData.get(level);
        BackGroupData.Record record = data.peek(group);
        if (record == null) {
            return false;
        }
        boolean changed = false;
        for (BlockPos p : blocks) {
            long key = p.asLong();
            changed |= record.openOverride.remove(key) | record.closedOverride.remove(key);
        }
        if (changed) {
            data.setDirty();
            GroupOverlays.refresh(level, group);
        }
        return changed;
    }

    // そのグループの扉の上書きを全部消す (リセット)
    public static void reset(ServerLevel level, UUID group) {
        BackGroupData data = BackGroupData.get(level);
        BackGroupData.Record record = data.peek(group);
        if (record == null || !record.hasOverride()) {
            return;
        }
        record.openOverride.clear();
        record.closedOverride.clear();
        data.setDirty();
        GroupOverlays.refresh(level, group);
    }
}
