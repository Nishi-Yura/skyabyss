package jp.revati.skyabyss.titan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 岩鯨の見守り。岩鯨はワールドに一体だけで、見失ってはいけない。
// - 近く (水平 640 ブロック以内) にプレイヤーがいる間と、誰かが背中にいる間は、岩鯨のいるチャンクに
//   時間切れ付きのチケットを張り続ける。シミュレーション距離の外でも泳ぎ続け、降下の待ち時間も進む。
//   張るのは岩鯨のいる 1 チャンク (と、エンティティを動かすのに要る周り) だけで、離れたら外す
// - TitanWorldData に本物の UUID と最後の位置・状態を残す。UUID の違う岩鯨は、読み込まれた時点で取り消す
// - 本物が読み込まれていなければ、最後の位置にチケットを張って読み込み直す。それでも見つからなければ
//   (/kill などで消えた)、近くのプレイヤーのそばへ呼び直す。誰からも遠く離れたまま 2 分たったときも呼び直す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WhaleKeeper {
    // 60 tick で切れる。張り直しが止まれば (岩鯨が消えた・サーバーが止まった) 自然に外れる
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyabyss_whale", Comparator.comparingLong(ChunkPos::toLong), 60);
    // 33 - 2 = 31: 中心のチャンクがエンティティの tick に入る最小の距離
    private static final int TICKET_RADIUS = 2;
    static final double NEAR = 640;
    static final double SPAWN_DISTANCE = 280;
    // 誰からも遠いまま、これだけたったら呼び直す
    private static final int RELOCATE_AFTER = 2400;
    // 最後の位置を読み込み直してから、見つからないと判断するまで
    private static final int RELOAD_GRACE = 200;
    // チケットを張っているのに tick が進まない (止まっている) と判断するまで
    private static final int STUCK_AFTER = 600;

    @Nullable
    private static ChunkPos ticketPos;
    private static long ticketRefreshed;
    private static long unreachableSince = -1;
    private static long missingSince = -1;
    private static int lastTickCount = -1;
    private static long frozenSince = -1;

    private WhaleKeeper() {
    }

    // 本物の岩鯨 (読み込まれているときだけ)
    @Nullable
    public static StoneWhaleEntity current(ServerLevel overworld) {
        TitanWorldData data = TitanWorldData.get(overworld);
        if (data.whaleId == null) {
            return null;
        }
        Entity entity = overworld.getEntity(data.whaleId);
        return entity instanceof StoneWhaleEntity whale && !whale.isRemoved() ? whale : null;
    }

    // 毎 tick、オーバーワールドで呼ばれる
    static void tick(ServerLevel level) {
        TitanWorldData data = TitanWorldData.get(level);
        if (!data.spawned) {
            return;
        }
        long now = level.getGameTime();
        ServerLevel back = level.getServer().getLevel(TitanTravel.TITAN_BACK);
        boolean someoneAboard = back != null && !back.players().isEmpty();
        StoneWhaleEntity whale = current(level);
        if (whale != null) {
            missingSince = -1;
            boolean near = nearestEligible(level, whale.getX(), whale.getZ(), NEAR) != null;
            if (near || someoneAboard) {
                holdTicket(level, whale.chunkPosition(), now);
                unreachableSince = -1;
            } else {
                releaseTicket(level);
                if (unreachableSince < 0) {
                    unreachableSince = now;
                }
            }
            if (now % 20 == 0) {
                data.remember(whale);
            }
            // 巨神での移動の最中 (人を乗せている・迎えに行く) は、呼び直さない。動きと読み込みは旅の操縦が受け持つ
            if (whale.inVoyage()) {
                frozenSince = -1;
                unreachableSince = -1;
                return;
            }
            // チケットを張っていて、オーバーワールドに人がいる (エンティティが動く) のに tick が進まない
            if (near && !level.players().isEmpty()) {
                if (whale.tickCount != lastTickCount || frozenSince < 0) {
                    lastTickCount = whale.tickCount;
                    frozenSince = now;
                } else if (now - frozenSince > STUCK_AFTER) {
                    SkyAbyss.LOGGER.warn("The stone whale stopped ticking at {}; calling it again", whale.blockPosition());
                    relocate(level, data, whale, randomEligible(level));
                    return;
                }
            } else {
                frozenSince = -1;
            }
            if (unreachableSince >= 0 && !someoneAboard && now - unreachableSince > RELOCATE_AFTER) {
                ServerPlayer target = randomEligible(level);
                if (target != null) {
                    relocate(level, data, whale, target);
                }
            }
            return;
        }
        if (now % 20 != 0) {
            return;
        }
        // 本物が読み込まれていない
        ServerPlayer near = data.hasLast ? nearestEligible(level, data.lastX, data.lastZ, NEAR) : null;
        if (data.hasLast && (near != null || someoneAboard)) {
            // 最後にいた所を読み込み直す
            holdTicket(level, new ChunkPos(BlockPos.containing(data.lastX, data.lastY, data.lastZ)), now);
            unreachableSince = -1;
            if (missingSince < 0) {
                missingSince = now;
            } else if (now - missingSince > RELOAD_GRACE) {
                ServerPlayer target = near != null ? near : randomEligible(level);
                if (target != null) {
                    SkyAbyss.LOGGER.warn("The stone whale was not found at its last position; calling it again");
                    relocate(level, data, null, target);
                }
            }
            return;
        }
        releaseTicket(level);
        missingSince = -1;
        if (unreachableSince < 0) {
            unreachableSince = now;
        } else if (!someoneAboard && now - unreachableSince > RELOCATE_AFTER) {
            ServerPlayer target = randomEligible(level);
            if (target != null) {
                relocate(level, data, null, target);
            }
        }
    }

    // 古い個体 (読み込まれていれば) を消し、プレイヤーの遠くへ新しい個体を出して、前の状態を引き継がせる
    private static void relocate(ServerLevel level, TitanWorldData data, @Nullable StoneWhaleEntity old, @Nullable ServerPlayer target) {
        if (target == null) {
            return;
        }
        CompoundTag state = old != null ? old.saveState() : data.state;
        if (old != null) {
            old.discard();
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        spawn(level, target.getX() + Math.cos(angle) * SPAWN_DISTANCE, target.getZ() + Math.sin(angle) * SPAWN_DISTANCE, state);
    }

    // 新しい岩鯨を出し、本物として登録する。登録してから加えるので、参加の検査で取り消されない
    public static StoneWhaleEntity spawn(ServerLevel level, double x, double z, @Nullable CompoundTag state) {
        TitanWorldData data = TitanWorldData.get(level);
        StoneWhaleEntity old = current(level);
        if (old != null) {
            old.discard();
        }
        StoneWhaleEntity whale = ModEntities.STONE_WHALE.get().create(level);
        if (whale == null) {
            throw new IllegalStateException("Failed to create stone whale");
        }
        whale.moveTo(x, 200, z, level.random.nextFloat() * 360f, 0f);
        if (state != null && !state.isEmpty()) {
            whale.restoreState(state);
        }
        data.register(whale);
        releaseTicket(level);
        unreachableSince = -1;
        missingSince = -1;
        frozenSince = -1;
        level.addFreshEntity(whale);
        holdTicket(level, whale.chunkPosition(), level.getGameTime());
        level.playSound(null, x, 200, z, ModSounds.TITAN_SONG.get(), SoundSource.AMBIENT, 24f, 0.8f);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, 200, z) < NEAR * NEAR) {
                player.sendSystemMessage(ModText.lore("message.skyabyss.titan_appears"));
            }
        }
        SkyAbyss.LOGGER.info("The stone whale appeared at {}, {} ({})", (int) x, (int) z, whale.getUUID());
        return whale;
    }

    private static void holdTicket(ServerLevel level, ChunkPos pos, long now) {
        if (pos.equals(ticketPos) && now - ticketRefreshed < 20) {
            return;
        }
        if (ticketPos != null && !pos.equals(ticketPos)) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketPos, TICKET_RADIUS, ticketPos);
        }
        level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_RADIUS, pos);
        ticketPos = pos;
        ticketRefreshed = now;
    }

    private static void releaseTicket(ServerLevel level) {
        if (ticketPos != null) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketPos, TICKET_RADIUS, ticketPos);
            ticketPos = null;
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return !player.isSpectator() && player.isAlive();
    }

    @Nullable
    static ServerPlayer nearestEligible(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            double d = dx * dx + dz * dz;
            if (eligible(player) && d < bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    static ServerPlayer randomEligible(ServerLevel level) {
        List<ServerPlayer> players = level.players().stream().filter(WhaleKeeper::eligible).toList();
        return players.isEmpty() ? null : players.get(level.random.nextInt(players.size()));
    }

    // 本物でない岩鯨は、ワールドに加わる前に取り消す (古い保存、呼び直す前の個体、/summon など)
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof StoneWhaleEntity whale) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            event.setCanceled(true);
            return;
        }
        TitanWorldData data = TitanWorldData.get(level);
        if (data.whaleId == null) {
            // 記録より前のワールドの個体か、まだ誰もいないときの /summon。これを本物とする
            data.register(whale);
            return;
        }
        if (!data.whaleId.equals(whale.getUUID())) {
            SkyAbyss.LOGGER.info("Discarded a stale stone whale {} (the current one is {})", whale.getUUID(), data.whaleId);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ticketPos = null;
        unreachableSince = -1;
        missingSince = -1;
        frozenSince = -1;
        lastTickCount = -1;
    }

    // 自己テスト (SKYABYSS_SELFTEST): 一体だけの決まり、状態の引き継ぎ、チケット。
    // プレイヤーがいないので、試す場所のチャンクを強制読み込みし、エンティティが読み込まれるのを待ってから試す
    private static final int TEST_X = 4000;
    private static final int TEST_Z = 4000;
    private static int selfTestCountdown = -1;

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (System.getenv("SKYABYSS_SELFTEST") == null) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        for (int dx = 0; dx <= 4; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, true);
        }
        selfTestCountdown = 40;
    }

    @SubscribeEvent
    static void onSelfTestTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (selfTestCountdown < 0 || !(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (--selfTestCountdown == 0) {
            selfTestCountdown = -1;
            runSelfTest(level);
        }
    }

    private static void runSelfTest(ServerLevel level) {
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        List<String> failures = new ArrayList<>();
        int x = TEST_X + 8;
        int z = TEST_Z + 8;
        StoneWhaleEntity first = spawn(level, x, z, null);
        if (current(level) != first) {
            failures.add("the spawned whale is not the current one");
        }
        if (ticketPos == null || !ticketPos.equals(first.chunkPosition())) {
            failures.add("no ticket on the whale's chunk");
        }
        StoneWhaleEntity stray = ModEntities.STONE_WHALE.get().create(level);
        if (stray != null) {
            stray.moveTo(x + 20, 200, z, 0f, 0f);
            if (level.addFreshEntity(stray) || level.getEntity(stray.getUUID()) != null) {
                failures.add("a second whale was allowed to join");
            }
        }
        CompoundTag state = first.saveState();
        state.putInt("RestCooldown", 99999);
        StoneWhaleEntity second = spawn(level, x + 64, z, state);
        if (!first.isRemoved()) {
            failures.add("calling the whale again left the old one");
        }
        if (current(level) != second || !second.getUUID().equals(data.whaleId)) {
            failures.add("the new whale is not registered");
        }
        if (second.saveState().getInt("RestCooldown") != 2400 || second.chainLength() != 0f) {
            failures.add("the carried state was not clamped: " + second.saveState());
        }
        long count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof StoneWhaleEntity) {
                count++;
            }
        }
        if (count != 1) {
            failures.add(count + " whales are loaded");
        }
        // 片付け: 岩鯨を消し、記録を元に戻す
        second.discard();
        releaseTicket(level);
        TitanWorldData restored = TitanWorldData.load(saved, level.registryAccess());
        data.spawned = restored.spawned;
        data.whaleId = restored.whaleId;
        data.hasLast = restored.hasLast;
        data.lastX = restored.lastX;
        data.lastY = restored.lastY;
        data.lastZ = restored.lastZ;
        data.lastYaw = restored.lastYaw;
        data.state = restored.state;
        data.setDirty();
        for (int dx = 0; dx <= 4; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, false);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST whale PASSED");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST whale FAILED: {}", f));
        }
    }
}
