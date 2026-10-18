package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

// グループごとの「見かけだけ違うブロック」(クライアントの画面だけに当てる上書き)。
//  - 扉: GroupGates の上書きから自動で作る (開いたグループには消え、ほかには見える)
//  - 見かけだけの仕掛け (光る共鳴石・ランプ・受光器・棒の電球など。当たり判定が同じもの): 各背中の Provider が、
//    グループの記録から「このグループに見せたい状態」を出す。記録が変わったら refresh(level, group) を呼ぶ
// サーバーのワールドのブロックは変わらない (あるのは「まだ何も進んでいない」姿)。
// 当たり判定や水が変わる仕掛け (水路・溶岩・足場) はここでは扱えない (部屋の占有 = RoomLocks にする)。
public final class GroupOverlays {
    public interface Provider {
        // そのグループに見せたいブロックの状態を out に出す
        void provide(ServerLevel level, UUID group, BiConsumer<BlockPos, BlockState> out);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();
    // 最後に送った中身がある人 (背中を離れたら空を送って消す)
    private static final Set<UUID> SENT = new HashSet<>();

    private GroupOverlays() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    public static Map<BlockPos, BlockState> compute(ServerLevel level, UUID group) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        BackGroupData.Record record = BackGroupData.get(level).peek(group);
        if (record != null) {
            BlockState gate = ModBlocks.SEALED_GATE.get().defaultBlockState();
            for (long key : record.openOverride) {
                map.put(BlockPos.of(key), gate.setValue(SealedGateBlock.OPEN, true));
            }
            for (long key : record.closedOverride) {
                map.put(BlockPos.of(key), gate.setValue(SealedGateBlock.OPEN, false));
            }
        }
        for (Provider provider : PROVIDERS) {
            provider.provide(level, group, map::put);
        }
        return map;
    }

    public static void send(ServerPlayer player) {
        if (player.connection == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !TitanBacks.isBack(level.dimension())) {
            if (SENT.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new GroupNetwork.Overlay(new long[0], new int[0]));
            }
            return;
        }
        Map<BlockPos, BlockState> map = compute(level, Groups.keyOf(player));
        long[] pos = new long[map.size()];
        int[] states = new int[map.size()];
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> e : map.entrySet()) {
            pos[i] = e.getKey().asLong();
            states[i] = Block.getId(e.getValue());
            i++;
        }
        SENT.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new GroupNetwork.Overlay(pos, states));
    }

    // グループの記録が変わった: そのグループの人 (その背中にいる人) の画面を更新する
    public static void refresh(ServerLevel level, UUID group) {
        for (ServerPlayer player : Groups.online(level, group)) {
            send(player);
        }
    }

    public static void forget(UUID player) {
        SENT.remove(player);
    }

    public static void clear() {
        SENT.clear();
    }
}
