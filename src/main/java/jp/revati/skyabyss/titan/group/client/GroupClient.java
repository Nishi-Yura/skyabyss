package jp.revati.skyabyss.titan.group.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// グループごとの見かけ (クライアント側)。
//  - ブロックの上書き: サーバーから届いた「自分のグループにだけ見せるブロック」を自分の画面のワールドに当てる。
//    サーバーが同じ座標を送り直して元に戻しても、毎 tick 見直して当て直す。上書きが外れたら、当てる前のブロックに戻す。
//    当たり判定はブロックの状態そのものなので、サーバーの判定 (SealedGateBlock.openFor) と合う
//  - 別のグループの人: 描かない (名前札・影ごと)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GroupClient {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final double RANGE_SQ = 160.0 * 160.0;

    private static final Map<Long, BlockState> DESIRED = new HashMap<>();
    // 当てる前のブロック
    private static final Map<Long, BlockState> ORIGINAL = new HashMap<>();
    // 最後に当てたブロック (いまの状態がこれと違えば、サーバーが書き換えた)
    private static final Map<Long, BlockState> APPLIED = new HashMap<>();
    private static Set<Integer> hidden = Set.of();
    private static ClientLevel lastLevel;
    // いまの DESIRED を受け取ったときのワールド。死んで戻る・次元の移動では、新しいワールドの作り直しと同じ tick のうちに
    // 上書きの知らせが届くことがあり、「ワールドが変わった」で消すと、届いたばかりの上書きまで消える (扉が透けて見える原因だった)
    private static ClientLevel desiredLevel;
    private static ClientLevel hiddenLevel;

    private GroupClient() {
    }

    public static void receiveOverlay(GroupNetwork.Overlay overlay) {
        Minecraft.getInstance().execute(() -> {
            Set<Long> next = new HashSet<>();
            Map<Long, BlockState> wanted = new HashMap<>();
            for (int i = 0; i < overlay.pos().length; i++) {
                next.add(overlay.pos()[i]);
                wanted.put(overlay.pos()[i], Block.stateById(overlay.states()[i]));
            }
            ClientLevel level = Minecraft.getInstance().level;
            if (level != desiredLevel) {
                // 前のワールドの上書きは、新しいワールドのブロックには当たっていない (戻すものはない)
                DESIRED.clear();
                ORIGINAL.clear();
                APPLIED.clear();
            }
            desiredLevel = level;
            // 外れた上書きを元に戻す
            for (Long key : new HashSet<>(DESIRED.keySet())) {
                if (next.contains(key)) {
                    continue;
                }
                restore(level, key);
            }
            DESIRED.clear();
            DESIRED.putAll(wanted);
            apply(level);
        });
    }

    public static void receiveHidden(GroupNetwork.Hidden hiddenPacket) {
        Minecraft.getInstance().execute(() -> {
            Set<Integer> set = new HashSet<>();
            for (int id : hiddenPacket.ids()) {
                set.add(id);
            }
            hidden = set;
            hiddenLevel = Minecraft.getInstance().level;
        });
    }

    private static void restore(ClientLevel level, long key) {
        BlockState original = ORIGINAL.remove(key);
        BlockState applied = APPLIED.remove(key);
        if (level != null && original != null && applied != null) {
            BlockPos pos = BlockPos.of(key);
            if (level.hasChunkAt(pos) && level.getBlockState(pos) == applied) {
                level.setBlock(pos, original, FLAGS);
            }
        }
    }

    private static void apply(ClientLevel level) {
        if (level == null || DESIRED.isEmpty()) {
            return;
        }
        BlockPos at = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.blockPosition();
        for (Map.Entry<Long, BlockState> e : DESIRED.entrySet()) {
            long key = e.getKey();
            BlockPos pos = BlockPos.of(key);
            if (at != null && pos.distSqr(at) > RANGE_SQ || !level.hasChunkAt(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            BlockState want = e.getValue();
            if (current == want) {
                continue;
            }
            // いまのブロックが、当てたものと違えば、サーバーが書いた (または初めて見る) 元の姿
            if (current != APPLIED.get(key)) {
                ORIGINAL.put(key, current);
            }
            level.setBlock(pos, want, FLAGS);
            APPLIED.put(key, want);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            // 新しいワールド。受け取った上書きがこのワールドのものなら、記録ごと残す (ワールドの作り直しと同じ tick のうちに届いたもの)。
            // 古いワールドのものなら、当てた記録ごと捨てる
            if (desiredLevel != level) {
                DESIRED.clear();
                ORIGINAL.clear();
                APPLIED.clear();
            }
            if (hiddenLevel != level) {
                hidden = Set.of();
            }
        }
        apply(level);
    }

    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!hidden.isEmpty() && hidden.contains(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }
}
