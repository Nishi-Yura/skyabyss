package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.heart.StormHeartBattle;
import jp.revati.skyabyss.titan.storm.raiju.RaijuBattle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 雷翼の背の決まり (12 の 7 章)。背は外見だけで、順路 (docs/design/32) は体の外の閉じた部屋で遊ぶ。ここが持つのは:
//  - 嵐の拍 (StormBackClock) を進める (背中全体で一つ。景色と嵐の敵の振る舞い)
//  - グループ (パーティ、または一人) ごとのリセット: そのグループのだれも背中にいないまま 3 分 (GroupProgress.idleGroups)、
//    または心臓を倒したあと、そのグループのだれも心臓の間の奥にいないまま 30 秒
//  - 休み場の記録 (着くと記録、倒れるとそこで目を覚ます。記録はグループの番号つき)。焚き火にしゃがんで触れると順路のあきらめ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - 背中にだれもいなくなって 3 分: 背の敵・雷・心臓と雷獣の部屋の持ち主を片付ける
// 背で湧かせた敵には StormMob.TRIAL_TAG とグループ (GroupOwned.setGroupId) を付けると、そのグループのリセットで消える。
// 雷獣を倒したら onRaijuDefeated(level, group) を呼ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StormBackTrials {
    public static final String TRIAL_TAG = "skyabyss_storm_trial";
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    // 雷翼の推奨レベル (12 の 6.6節)
    public static final int STORM_LEVEL = 24;
    private static final String TAG_CHECKPOINT = "skyabyss_storm_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_storm_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_storm_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_storm";
    private static final AABB BODY = new AABB(-330, 100, -190, 330, 200, 200);

    // 背中にだれもいない時間 (背中そのものの片付け)
    private static int idleTicks;
    // 心臓を倒したグループの、心臓の間の奥にだれもいない時間
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
    private static final Set<UUID> REVIVING = new HashSet<>();

    static {
        GroupEvents.addMembershipHook(StormBackTrials::onMembershipChanged);
    }

    private StormBackTrials() {
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // その人のグループの番号 (休み場の記録の番号)
    static int epochOf(ServerLevel back, ServerPlayer player) {
        return GroupProgress.epoch(back, Groups.keyOf(player));
    }

    // 嵐に投げ返されたときに戻す所 (最後の休み場、なければ到着)
    public static BlockPos fallbackPoint(ServerPlayer player) {
        ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return ARRIVAL;
        }
        String cp = validCheckpoint(persisted(player), epochOf(back, player));
        return cp.isEmpty() ? ARRIVAL : restSpawn(back, cp);
    }

    // 試練の中にいるか (嵐に投げ返されたときの傷が 3 になる)
    public static boolean inTrial(ServerPlayer player) {
        return StormBackLayout.inTrial(player.position());
    }

    // ---------------------------------------------------------------- 建てたあと
    // 建て直したあと: 全グループの進みを戻す (形が変わったので)
    public static void afterBuild(ServerLevel level) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, false, null, true, false);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        TitanBackData data = TitanBackData.get(level);
        if (!data.isBuilt() || data.needsBuild(LAYOUT_VERSION)) {
            return;
        }
        boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator());
        if (level.getGameTime() % 10 == 0) {
            tickReset(level, anyone);
        }
        if (anyone) {
            StormBackClock.advance(level);
        }
    }

    // 雷獣を倒した (雷獣の担当が、倒したグループで呼ぶ)
    public static void onRaijuDefeated(ServerLevel level, UUID group) {
        StormProgress.of(level, group).setRaijuDefeated(true);
    }

    // ---------------------------------------------------------------- リセット (グループごと)
    private static void tickReset(ServerLevel level, boolean anyone) {
        if (!anyone) {
            idleTicks += 10;
            if (idleTicks == RESET_AFTER_IDLE) {
                cleanupWorld(level);
            }
        } else {
            idleTicks = 0;
        }
        // そのグループのだれも背中にいないまま 3 分
        if (level.getGameTime() % 100 == 0) {
            for (UUID group : GroupProgress.idleGroups(level)) {
                if (StormProgress.of(level, group).needsReset()) {
                    resetGroup(level, group, false, null, false, true);
                }
            }
        }
        // 心臓を倒したあと、そのグループのだれも心臓の間の奥にいないまま 30 秒
        BackGroupData groups = BackGroupData.get(level);
        for (UUID group : groups.ids()) {
            BackGroupData.Record r = groups.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastKeel(p.position())) {
                    inside = true;
                    break;
                }
            }
            int t = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            if (t >= RESET_AFTER_HEART) {
                AFTER_HEART.remove(group);
                resetGroup(level, group, true, null, false, true);
            } else {
                AFTER_HEART.put(group, t);
            }
        }
    }

    // 背中にだれもいなくなって 3 分: 背中そのものを片付ける (グループの進みは消さない。グループごとの放置のリセットは別)
    static void cleanupWorld(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, BODY, e -> e.getTags().contains(TRIAL_TAG))) {
            e.discard();
        }
        StormStrike.cancelAll(level);
        for (String room : List.of(StormHeartBattle.ROOM, RaijuBattle.ROOM)) {
            RoomLocks.release(room);
        }
        SkyAbyss.LOGGER.info("Storm back cleaned up (nobody on the back)");
    }

    // 記録のあるすべてのグループを最初に戻す (コマンド・建て直し)
    public static void resetAll(ServerLevel level, boolean announce, @Nullable StormBackState.Trial forceSleep, boolean noSleep) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, announce, forceSleep, noSleep, true);
        }
    }

    // そのグループの進みを最初に戻す (12 の 7.1節)。記録の番号が進み (古い休み場の記録は無効)、心臓も眠り直す。
    // そのグループの心臓・雷獣の戦いを止め、そのグループの背の敵と雷を消す
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable StormBackState.Trial forceSleep, boolean noSleep,
            boolean moveOut) {
        // そのグループの進みが元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.THUNDER_WING);
        StormProgress p = StormProgress.of(level, group);
        p.reset(level.random, forceSleep, noSleep);
        AFTER_HEART.remove(group);
        StormHeartBattle.onGroupReset(level, group);
        RaijuBattle.onGroupReset(level, group);
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        GroupOverlays.refresh(level, group);
        for (Entity e : level.getEntitiesOfClass(Entity.class, BODY,
                e -> e.getTags().contains(TRIAL_TAG) && e instanceof GroupOwned owned && group.equals(owned.groupId()))) {
            e.discard();
        }
        StormStrike.cancel(level, group);
        if (announce) {
            for (ServerPlayer pl : Groups.online(level, group)) {
                Notice.narrate(pl, "message.skyabyss.storm.trials_renewed");
            }
        }
        SkyAbyss.LOGGER.info("Storm back reset for group {}", group);
    }

    // ---------------------------------------------------------------- パーティが変わった人
    // 記録が変わるので、休み場の記録の番号が変われば記録を消す
    static void onMembershipChanged(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != StormTravel.STORM_BACK) {
            return;
        }
        int epoch = GroupProgress.epoch(level, Groups.keyOf(player));
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 休み場
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK || player.tickCount % 5 != 0) {
            return;
        }
        int epoch = epochOf(player.serverLevel(), player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().contains(pos) || pos.distSqr(r.spawn()) < 25) {
                if (!r.id().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.id());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.id().equals("hub")) {
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.storm_" + r.id())));
                        player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
                    }
                }
                return;
            }
        }
    }

    public static String validCheckpoint(CompoundTag tag, int epoch) {
        String id = tag.getString(TAG_CHECKPOINT);
        if (id.isEmpty() || tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            return "";
        }
        return REST.containsKey(id) ? id : "";
    }

    public static void writeCheckpoint(Player player, String id, int epoch) {
        CompoundTag tag = persisted(player);
        tag.putString(TAG_CHECKPOINT, id);
        tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
    }

    static void clearCheckpoint(Player player) {
        CompoundTag tag = persisted(player);
        tag.remove(TAG_CHECKPOINT);
        tag.remove(TAG_CHECKPOINT_EPOCH);
    }

    // 倒れて目覚める休み場。順路では、その段の休み場で目覚める (32 の 2.4)
    public static String respawnCheckpoint(java.util.UUID group, String cp) {
        return cp;
    }

    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.id().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.spawn());
            }
        }
        return ARRIVAL;
    }

    static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // ---------------------------------------------------------------- 倒れたとき
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK || event.isCanceled()) {
            return;
        }
        persisted(player).putBoolean(TAG_DIED, true);
        TitanBackTrials.keepBelongings(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        CompoundTag tag = persisted(player);
        if (!tag.getBoolean(TAG_DIED)) {
            return;
        }
        tag.remove(TAG_DIED);
        ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
        if (back == null) {
            return;
        }
        int epoch = epochOf(back, player);
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL : restSpawn(back, respawnCheckpoint(Groups.keyOf(player), cp));
        REVIVING.add(player.getUUID());
        try {
            player.teleportTo(back, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0f);
        } finally {
            REVIVING.remove(player.getUUID());
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
        jp.revati.skyabyss.titan.group.RespawnGuard.begin(player);
        Notice.narrate(player, cp.isEmpty() && tag.contains(TAG_CHECKPOINT) ? "message.skyabyss.rest_point_lost" : "message.skyabyss.revived_on_back");
        if (cp.isEmpty()) {
            clearCheckpoint(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == StormTravel.STORM_BACK || event.getTo() == StormTravel.STORM_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(StormTravel.STORM_BACK);
            if (event.getTo() == StormTravel.STORM_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, epochOf(back, player));
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != StormTravel.STORM_BACK) {
            return;
        }
        int epoch = epochOf(player.serverLevel(), player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 焚き火
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().dimension() != StormTravel.STORM_BACK || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.getBlockState(event.getPos()).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                if (jp.revati.skyabyss.trial.TrialRoute.propose(player)) {
                    return;
                }
                clearCheckpoint(player);
                toArrival(player);
                Notice.narrate(player, "message.skyabyss.storm.gave_up");
            } else {
                Notice.bar(player, ModText.hint("message.skyabyss.storm.campfire_hint"));
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        idleTicks = 0;
        AFTER_HEART.clear();
        REVIVING.clear();
        StormBackClock.force(null);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(rest("hub"));
    }
}
