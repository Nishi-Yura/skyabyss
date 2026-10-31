package jp.revati.skyabyss.loot.box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 封切り師の窓口 (docs/design/52 の 5・6 章)。話して「箱を開けてもらう」で画面を開く (BoxNetwork.View)。
// 画面の「開ける」(BoxNetwork.Open) は、封切り師のそば (REACH) にいる間だけ受ける。品はサーバーが渡してから画面に結果を送る
// (画面はルーレットを見せるだけ。途中で閉じても品は手もとにある)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BoxService {
    public static final double REACH = 8.0;
    // 話した封切り師 (画面を開いている人 → 封切り師の entity id)。-1 = 試しの開け方 (コマンド)
    private static final Map<UUID, Integer> SESSIONS = new HashMap<>();

    private BoxService() {
    }

    public static void open(ServerPlayer player, @Nullable NpcEntity npc) {
        SESSIONS.put(player.getUUID(), npc == null ? -1 : npc.getId());
        PacketDistributor.sendToPlayer(player, view(player));
    }

    public static BoxNetwork.View view(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (BoxKind k : BoxKind.ALL) {
            int n = LootBoxes.count(player, k);
            if (n > 0) {
                ids.add(k.id());
                counts.add(n);
            }
        }
        return new BoxNetwork.View(ids, counts);
    }

    // 画面の「開ける」: n 個まで。封切り師のそばにいなければ何もしない (画面を閉じさせる)
    static void openBoxes(ServerPlayer player, String kindId, int n) {
        BoxKind kind = BoxKind.byId(kindId);
        if (kind == null || n <= 0) {
            return;
        }
        if (!near(player)) {
            SESSIONS.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new BoxNetwork.Close());
            return;
        }
        List<LootBoxes.Opened> out = LootBoxes.open(player, kind, n);
        if (out.isEmpty()) {
            PacketDistributor.sendToPlayer(player, view(player));
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.3f);
        List<ItemStack> gear = new ArrayList<>();
        List<List<ItemStack>> reels = new ArrayList<>();
        for (LootBoxes.Opened o : out) {
            gear.add(o.gear());
            reels.add(o.reel());
        }
        PacketDistributor.sendToPlayer(player, new BoxNetwork.Result(kind.id(), gear, reels));
        PacketDistributor.sendToPlayer(player, view(player));
    }

    static boolean near(ServerPlayer player) {
        Integer id = SESSIONS.get(player.getUUID());
        if (id == null || !player.isAlive()) {
            return false;
        }
        if (id < 0) {
            return true;
        }
        Entity npc = player.level().getEntity(id);
        return npc != null && npc.isAlive() && npc.distanceToSqr(player) <= REACH * REACH;
    }

    // 封切り師の画面を開いているか (開けた品の名を知らせの欄に出さない)
    static boolean viewing(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    static void closed(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
