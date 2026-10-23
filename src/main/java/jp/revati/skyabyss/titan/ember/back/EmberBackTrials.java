package jp.revati.skyabyss.titan.ember.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.ember.back.EmberBackLayout.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.titan.dungeon.SealedGateBlock;
import jp.revati.skyabyss.titan.dungeon.TitanBackData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.group.GroupEvents;
import jp.revati.skyabyss.titan.group.GroupGates;
import jp.revati.skyabyss.titan.group.GroupOverlays;
import jp.revati.skyabyss.titan.group.GroupProgress;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 余燼の背の決まり (13 の 7 章)。背は外見だけで、順路 (docs/design/32) は体の外の閉じた部屋で遊ぶ。ここが持つのは:
//  - グループ (パーティ、または一人) ごとのリセット: 放置 3 分 (GroupProgress.idleGroups)、または心臓を倒したあと 30 秒
//  - 休み場の記録 (着くと記録、倒れるとそこで目を覚ます)。焚き火にしゃがんで触れると順路のあきらめ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - グループの人にだけ送る音と粒 (soundTo・particlesTo)、熾の底の入口の扉
// ほかの担当が呼ぶ API: markReady (鍛え手を倒した)、resetGroup、restSpawn、respawnCheckpoint
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberBackTrials {
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 60;
    public static final int EMBER_LEVEL = 30;
    private static final String TAG_CHECKPOINT = "skyabyss_ember_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_ember_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_ember_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_ember";

    // 心臓を倒したあと門の奥にグループの誰もいない時間
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
    private static final Set<UUID> REVIVING = new HashSet<>();

    private EmberBackTrials() {
    }

    public static List<BlockPos> boxBlocks(Box b) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(b.min(), b.max())) {
            out.add(p.immutable());
        }
        return out;
    }

    public static boolean onBack(ServerLevel level) {
        return level.dimension() == EmberIds.BACK;
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ---------------------------------------------------------------- グループの見かけ・音 (EmberBackSetup から一度だけ)
    static void registerGroupHooks() {
        GroupEvents.addMembershipHook(EmberBackTrials::onGroupChanged);
    }

    // グループの人 (近くにいる) にだけ音を送る
    public static void soundTo(ServerLevel level, UUID group, Vec3 at, SoundEvent sound, float volume, float pitch) {
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder, SoundSource.BLOCKS, at.x, at.y, at.z, volume, pitch, level.random.nextLong());
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(at) < 64 * 64 && p.connection != null) {
                p.connection.send(packet);
            }
        }
    }

    // グループの人 (近くにいる) にだけ粒を送る
    public static void particlesTo(ServerLevel level, UUID group, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy,
            double dz, double speed) {
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(x, y, z) < 64 * 64) {
                level.sendParticles(p, particle, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    // グループの鍵が変わった人 (組む・入る・抜ける): 休み場の記録は番号が違えば無効になる
    static void onGroupChanged(ServerPlayer player) {
        if (!onBack(player.serverLevel())) {
            return;
        }
        int epoch = EmberBackState.of(player.serverLevel(), player).epoch();
        CompoundTag tag = persisted(player);
        if (tag.contains(TAG_CHECKPOINT) && tag.getInt(TAG_CHECKPOINT_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 起動・建てたあと
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel back = event.getServer().getLevel(EmberIds.BACK);
        if (back != null) {
            EmberBodyBuilder.schedule(back);
            TitanBackData data = TitanBackData.get(back);
            if (data.isBuilt() && !data.needsBuild(EmberBodyBuilder.buildVersion())) {
                // 建て直さない起動でも、扉のブロックと石の既定の姿をそろえる (何度呼んでも同じ)
                placeFixtures(back);
            }
        }
    }

    // 体を建てた直後 (EmberBodyBuilder から)。扉と石を置き、すべてのグループの試練を最初の形にする (静かにしない)
    static void afterBuild(ServerLevel level) {
        AFTER_HEART.clear();
        placeFixtures(level);
        for (UUID id : BackGroupData.get(level).ids()) {
            EmberBackState state = EmberBackState.of(level, id);
            if (state.record() != null && state.record().hasProgress()) {
                GroupProgress.reset(level, id);
                state.afterReset(level.random, null, true);
            }
        }
        EmberRooms.releaseAll(level);
    }

    // ワールドのブロック (どのグループにも同じ): 体の外の熾の底の入口の扉 (開いた既定)。体の中は建てない (docs/design/32 の 2.5)
    public static void placeFixtures(ServerLevel level) {
        GroupGates.place(level, boxBlocks(HEART_DOOR), true);
    }

    // ---------------------------------------------------------------- API
    public static void markReady(ServerLevel level, UUID group, EmberBackState.Trial trial) {
        EmberBackState state = EmberBackState.of(level, group);
        if (state.ready(trial)) {
            return;
        }
        state.markReady(trial);
    }

    // 熾の底の門より奥 (門の下の螺旋・前室・熾の底)
    public static boolean pastGate(Vec3 pos) {
        return HEART_ANTE.aabb().inflate(1, 0, 1).contains(pos) || HEART.aabb().contains(pos)
                || new AABB(GATE_SHAFT.minX() - 1, FLOOR_HEART, GATE_SHAFT.minZ() - 1, GATE_SHAFT.maxX() + 2, 84.9, HEART_ANTE.minZ() + 1).contains(pos);
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !onBack(level)) {
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        EmberRooms.tick(level);
        tickReset(level);
    }

    // ---------------------------------------------------------------- 休み場の記録 (人ごと)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || player.tickCount % 5 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        EmberBackState state = EmberBackState.of(level, player);
        // 休み場の記録 (試練の番号は、その人のグループのもの)
        int epoch = state.epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().grow(1).contains(pos)) {
                if (!r.name().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.name());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    if (!r.name().equals("hub")) {
                        Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                                Component.translatable("checkpoint.skyabyss.ember_" + r.name())));
                        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f);
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
        return EmberIds.RESTS.contains(id) ? id : "";
    }

    // 休み場の記録 (その人のグループの試練の番号で確かめる)。背中にいない人は、背中のディメンションの記録で見る
    public static String checkpoint(ServerPlayer player) {
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            return "";
        }
        return validCheckpoint(persisted(player), EmberBackState.of(back, player).epoch());
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

    // 休み場の復活位置 (焚き火の前の台の上。立てる場所を探す)

    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.name().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.floor().above());
            }
        }
        return ARRIVAL.above();
    }

    public static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY() + 1, ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    public static Vec3 hubSpawn() {
        return Vec3.atBottomCenterOf(rest("hub").floor().above());
    }

    // ---------------------------------------------------------------- リセット (グループごと。7.1節)
    // - 放置: そのグループの誰も背中にいないまま 3 分 (GroupProgress.idleGroups) たち、試練が進んでいれば
    // - 心臓のあと: そのグループが心臓を倒し、そのグループの誰も門の奥にいないまま 30 秒たったら、次の周へ
    private static void tickReset(ServerLevel level) {
        for (UUID group : GroupProgress.idleGroups(level)) {
            if (EmberBackState.of(level, group).hasProgress()) {
                resetGroup(level, group, false, null, false);
            }
        }
        BackGroupData data = BackGroupData.get(level);
        for (UUID group : data.ids()) {
            BackGroupData.Record r = data.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastGate(p.position())) {
                    inside = true;
                    break;
                }
            }
            int idle = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            if (idle >= RESET_AFTER_HEART) {
                AFTER_HEART.remove(group);
                resetGroup(level, group, true, null, false);
            } else {
                AFTER_HEART.put(group, idle);
            }
        }
    }

    // そのグループの進みを最初に戻す (7.1節)。番号が進む (古い休み場の記録は無効)。
    // そのグループが持っている部屋は手放して最初に戻す。ほかのグループの進み・部屋・人には触らない
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable EmberBackState.Trial forceSleep, boolean noSleep) {
        // そのグループの試練が元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.EMBER);
        AFTER_HEART.remove(group);
        // 部屋 (戦いの部屋も) を先に戻す。戦いの放棄は扉をそのグループに開け直すので、そのあとで扉の上書きを記録ごと消す
        EmberRooms.releaseHeldBy(level, group);
        resetRecord(level, group, forceSleep, noSleep);
        // 順路の記録 (引きは残る)
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        if (announce) {
            for (ServerPlayer p : Groups.online(level, group)) {
                Notice.narrate(p, EmberIds.LANG_BACK + "trials_renewed");
            }
        }
        SkyAbyss.LOGGER.info("Ember back reset for group {}", group);
    }

    private static void resetRecord(ServerLevel level, UUID group, @Nullable EmberBackState.Trial forceSleep, boolean noSleep) {
        GroupProgress.reset(level, group);
        EmberBackState.of(level, group).afterReset(level.random, forceSleep, noSleep);
        GroupOverlays.refresh(level, group);
    }

    // すべてのグループの試練を最初に戻す (コマンド)
    public static void resetAll(ServerLevel level, boolean announce) {
        for (UUID group : BackGroupData.get(level).ids()) {
            resetGroup(level, group, announce, null, false);
        }
        EmberRooms.releaseAll(level);
    }

    // ---------------------------------------------------------------- 倒れたとき (7.3節)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel()) || event.isCanceled()) {
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
        ServerLevel back = player.server.getLevel(EmberIds.BACK);
        if (back == null) {
            return;
        }
        int epoch = EmberBackState.of(back, player).epoch();
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL.above() : restSpawn(back, respawnCheckpoint(Groups.keyOf(player), cp));
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

    // 乗り込み・地上へ戻る (次元の移動) では休み場の記録を消す。背中で倒れて連れ戻すときだけは残す
    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || REVIVING.contains(player.getUUID())) {
            return;
        }
        if (event.getFrom() == EmberIds.BACK || event.getTo() == EmberIds.BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(EmberIds.BACK);
            if (event.getTo() == EmberIds.BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, EmberBackState.of(back, player).epoch());
            }
        }
    }

    // ログイン: 背中にいて、その間にそのグループの進みがリセットされていれば、休み場の記録を消す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        int epoch = EmberBackState.of(player.serverLevel(), player).epoch();
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // 焚き火: しゃがんで触れると、試練をあきらめて到着点へ
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !onBack(player.serverLevel())) {
            return;
        }
        if (player.serverLevel().getBlockState(event.getPos()).is(jp.revati.skyabyss.registry.ModBlocks.REST_HEARTH.get())) {
            event.setCanceled(true);
            if (player.isShiftKeyDown()) {
                if (jp.revati.skyabyss.trial.TrialRoute.propose(player)) {
                    return;
                }
                clearCheckpoint(player);
                toArrival(player);
                EmberHeat.reset(player);
                Notice.narrate(player, EmberIds.LANG_BACK + "gave_up");
            } else {
                Notice.bar(player, ModText.hint(EmberIds.LANG_BACK + "campfire_hint"));
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        AFTER_HEART.clear();
        REVIVING.clear();
    }
}
