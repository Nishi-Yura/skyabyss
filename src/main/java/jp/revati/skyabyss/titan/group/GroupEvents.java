package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyData;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

// グループ (Groups) まわりの共通の動き (docs/design/10 の 10 章)。
//  - 背中のグループの鍵が変わったとき (組む・入る・抜ける): 進みの引き継ぎ
//      ・組む: パーティの記録は、組んだ側 (リーダー) のグループの記録の写しから始まる
//      ・入る: パーティの記録をそのまま使う (入った人の元の一人の記録は使われない)
//      ・抜ける/解散: 抜けた人 (解散で一人に戻った人) の一人の記録は、そのときのパーティの記録の写しになる (進みは取り上げない)
//    背中の体内にいる人は、記録が変わるので、各背中の hook (addMembershipHook) が安全な所へ移す
//  - 見かけ: ブロックの上書き (GroupOverlays) と、別のグループの人を描かない一覧 (Hidden) を送る
//  - 戦い: 別のグループの人・敵とは、傷を与え合わない・狙わない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class GroupEvents {
    private static final List<Consumer<ServerPlayer>> MEMBERSHIP_HOOKS = new ArrayList<>();
    private static final Map<UUID, int[]> LAST_HIDDEN = new HashMap<>();

    private GroupEvents() {
    }

    // グループの鍵が変わった人 (その背中にいるとき) への後始末。各背中が足す
    public static void addMembershipHook(Consumer<ServerPlayer> hook) {
        MEMBERSHIP_HOOKS.add(hook);
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        PartyData.setListener(new PartyData.Listener() {
            @Override
            public void created(UUID partyId, UUID leader) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    forEachBack(server, data -> data.copy(leader, partyId));
                }
            }

            @Override
            public void joined(UUID partyId, UUID player) {
                changed(player);
            }

            @Override
            public void left(UUID partyId, UUID player, List<UUID> dissolved) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return;
                }
                List<UUID> solos = new ArrayList<>(dissolved);
                if (player != null) {
                    solos.add(player);
                }
                forEachBack(server, data -> {
                    for (UUID solo : solos) {
                        data.copy(partyId, solo);
                    }
                });
                for (UUID solo : solos) {
                    changed(solo);
                }
            }
        });
    }

    private static void forEachBack(MinecraftServer server, Consumer<BackGroupData> action) {
        for (ResourceKey<Level> dim : TitanBacks.all()) {
            ServerLevel level = server.getLevel(dim);
            if (level != null) {
                action.accept(BackGroupData.get(level));
            }
        }
    }

    // 人のグループの鍵が変わった (組む・入る・抜ける)
    public static void changed(UUID player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            membershipChanged(p);
        }
    }

    public static void membershipChanged(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !TitanBacks.isBack(level.dimension())) {
            return;
        }
        for (Consumer<ServerPlayer> hook : MEMBERSHIP_HOOKS) {
            hook.accept(player);
        }
        GroupOverlays.send(player);
        LAST_HIDDEN.remove(player.getUUID());
    }

    // ---------------------------------------------------------------- 見かけの送り直し
    // 上書き (扉・光る石) と、見えなくする人の一覧は、クライアントのワールドが作り直されるたび (ログイン・次元の移動・死んで戻る)
    // と、グループが変わるたびに、送り直す。倒れて戻るときはワールドが二度作り直される (地上 → 背中) ので、
    // 届くのが作り直しと同じ tick になってもよいよう、少し後にもう一度送る (RESEND)。念のため、背中にいる人には 10 秒ごとにも送る
    private static final Map<UUID, Integer> RESEND = new HashMap<>();
    private static final int RESEND_AFTER = 20;
    private static final int PERIODIC = 200;

    // 人のいまの見かけ (上書きと、見えなくする人) を送り、少し後にもう一度送る。ログイン・次元の移動・復活から呼ぶ
    public static void resync(ServerPlayer player) {
        GroupOverlays.send(player);
        LAST_HIDDEN.remove(player.getUUID());
        RESEND.put(player.getUUID(), RESEND_AFTER);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GroupOverlays.forget(event.getEntity().getUUID());
        LAST_HIDDEN.remove(event.getEntity().getUUID());
        RESEND.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
            if (player.connection != null) {
                PacketDistributor.sendToPlayer(player, new GroupNetwork.Hidden(new int[0]));
            }
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LAST_HIDDEN.clear();
        RESEND.clear();
        GroupOverlays.clear();
    }

    // 別のグループの人を、各人の画面から消す (描かない)。10 tick ごと、変わったときだけ送る
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 10 != 0 || !TitanBacks.isBack(level.dimension())) {
            return;
        }
        if (level.getGameTime() % 200 == 0) {
            GroupProgress.touch(level);
            if (level.getGameTime() % 2400 == 0) {
                GroupProgress.prune(level);
            }
        }
        List<ServerPlayer> players = level.players();
        for (ServerPlayer p : players) {
            Integer left = RESEND.get(p.getUUID());
            if (left != null) {
                if (left <= 10) {
                    RESEND.remove(p.getUUID());
                    GroupOverlays.send(p);
                    LAST_HIDDEN.remove(p.getUUID());
                } else {
                    RESEND.put(p.getUUID(), left - 10);
                }
            } else if ((level.getGameTime() / 10 + p.getId()) % (PERIODIC / 10) == 0) {
                GroupOverlays.send(p);
            }
        }
        if (players.size() < 2) {
            for (ServerPlayer viewer : players) {
                sendHidden(viewer, new int[0]);
            }
            return;
        }
        Map<ServerPlayer, UUID> keys = new HashMap<>();
        for (ServerPlayer p : players) {
            keys.put(p, Groups.keyOf(p));
        }
        for (ServerPlayer viewer : players) {
            UUID mine = keys.get(viewer);
            int[] ids = new int[players.size()];
            int n = 0;
            for (ServerPlayer other : players) {
                if (other != viewer && !other.isSpectator() && !keys.get(other).equals(mine)) {
                    ids[n++] = other.getId();
                }
            }
            sendHidden(viewer, Arrays.copyOf(ids, n));
        }
    }

    private static void sendHidden(ServerPlayer viewer, int[] ids) {
        int[] last = LAST_HIDDEN.get(viewer.getUUID());
        if (last == null ? ids.length == 0 : Arrays.equals(last, ids)) {
            return;
        }
        LAST_HIDDEN.put(viewer.getUUID(), ids);
        if (viewer.connection != null) {
            PacketDistributor.sendToPlayer(viewer, new GroupNetwork.Hidden(ids));
        }
    }

    // ---------------------------------------------------------------- 戦い
    // 別のグループの人・敵が与える傷は、無かったことにする (背中の中だけ)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!TitanBacks.isBack(victim.level().dimension())) {
            return;
        }
        if (Groups.differ(event.getSource().getEntity(), victim) || Groups.differ(event.getSource().getDirectEntity(), victim)) {
            event.setCanceled(true);
        }
    }

    // 戦いの部屋の中で倒れた参加者を覚える (撃破の参加者に数える。docs/design/10 の 10 章)
    @SubscribeEvent
    static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && TitanBacks.isBack(player.level().dimension())) {
            GroupBossRoom.noteDeath(player.getUUID());
        }
    }

    // 別のグループの人を狙わない (最寄りの探索は GroupOwned.mayTarget で除いてあるので、これは取りこぼしの備え)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && TitanBacks.isBack(event.getEntity().level().dimension()) && Groups.differ(event.getEntity(), target)) {
            event.setCanceled(true);
        }
    }
}
