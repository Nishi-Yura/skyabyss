package jp.revati.skyabyss.titan.tide.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.tide.back.TideBackLayout.*;

import java.util.HashMap;
import java.util.HashSet;
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
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.heart.TideHeartBattle;
import jp.revati.skyabyss.titan.tide.mob.TideMob;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 潮殻の背の決まり (08 の 7 章)。背は外見だけで、順路 (docs/design/32) は体の外の閉じた部屋で遊ぶ。ここが持つのは:
//  - 潮の時計 (TideClock) を進める (島で一つ。景色と潮の敵の振る舞い)
//  - グループ (パーティ、または一人) ごとのリセット: そのグループの誰も島にいなくなって 3 分 (GroupProgress.idleGroups)、
//    またはそのグループが心臓を倒したあと、そのグループの誰も心臓の間の奥にいないまま 30 秒
//  - 休み場の記録 (着くと記録、倒れるとそこで目を覚ます。記録はその人のグループの番号つき)。焚き火にしゃがんで触れると順路のあきらめ
//  - 倒れても持ち物と経験値は預かる (岩鯨と同じ部品 TitanBackTrials.keepBelongings)
//  - 島に誰もいなくなって 3 分: 心臓の部屋の持ち主を手放し、背の敵を片付ける (グループの進みはそのまま)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TideBackTrials {
    // 島じゅう (背の敵を片付ける範囲)
    static final AABB ISLAND_BOX = new AABB(-135, 40, -145, 135, 230, 130);
    public static final int RESET_AFTER_IDLE = GroupProgress.IDLE_TICKS;
    public static final int RESET_AFTER_HEART = 20 * 30;
    public static final int TRIAL_XP = 50;
    private static final String TAG_CHECKPOINT = "skyabyss_tide_checkpoint";
    private static final String TAG_CHECKPOINT_EPOCH = "skyabyss_tide_checkpoint_epoch";
    private static final String TAG_SEEN_EPOCH = "skyabyss_tide_epoch";
    private static final String TAG_DIED = "skyabyss_died_on_tide";

    private static int idleTicks;
    // グループごとの「心臓を倒したあと、心臓の間の奥に誰もいない」tick
    private static final Map<UUID, Integer> AFTER_HEART = new HashMap<>();
    private static final Set<UUID> REVIVING = new HashSet<>();

    private TideBackTrials() {
    }

    static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // その人のグループの進み
    public static TideBackState.Group state(ServerLevel level, ServerPlayer player) {
        return TideBackState.of(level, Groups.keyOf(player));
    }

    // その人のグループの番号
    static int epochOf(ServerLevel level, ServerPlayer player) {
        return GroupProgress.epoch(level, Groups.keyOf(player));
    }

    // ---------------------------------------------------------------- グループが変わったときの後始末
    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GroupEvents.addMembershipHook(TideBackTrials::membershipChanged));
    }

    // グループの鍵が変わった人 (パーティを組んだ・入った・抜けた) が潮殻の背にいる: 記録が変わるので、休み場の記録を消す
    static void membershipChanged(ServerPlayer player) {
        if (player.level().dimension() != TideTravel.TIDE_BACK) {
            return;
        }
        int epoch = epochOf(player.serverLevel(), player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            clearCheckpoint(player);
        }
        tag.putInt(TAG_SEEN_EPOCH, epoch);
    }

    // ---------------------------------------------------------------- 建てたあと
    // 島を建てた直後 (TideBackBuilder から)。残った背の敵を片付ける (体の中は建てない。docs/design/32 の 2.5)
    public static void afterBuild(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX, e -> e.getTags().contains(TideMob.TRIAL_TAG)
                && !(e instanceof jp.revati.skyabyss.titan.tide.heart.TideHeart))) {
            e.discard();
        }
    }

    // 建て直し (古い配置の島): どのグループの進みも最初に戻す (番号が進み、古い休み場の記録は無効になる)
    public static void resetAllRecords(ServerLevel level) {
        BackGroupData data = BackGroupData.get(level);
        for (UUID id : data.ids()) {
            BackGroupData.Record r = data.peek(id);
            if (r != null) {
                new TideBackState.Group(id, r).reset(level.random, null, true);
            }
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != TideTravel.TIDE_BACK) {
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
            TideClock.advance(level);
        }
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
        // そのグループの誰も島にいないまま 3 分たち、進んでいたグループ
        for (UUID group : GroupProgress.idleGroups(level)) {
            BackGroupData.Record r = BackGroupData.get(level).peek(group);
            if (r != null && new TideBackState.Group(group, r).hasTrialProgress()) {
                resetGroup(level, group, false, null, false);
            }
        }
        // 心臓を倒したグループ: そのグループの誰も心臓の間の奥にいなくなって 30 秒たったら、次の周へ
        BackGroupData data = BackGroupData.get(level);
        for (UUID group : data.ids()) {
            BackGroupData.Record r = data.peek(group);
            if (r == null || !r.isHeartDefeated()) {
                AFTER_HEART.remove(group);
                continue;
            }
            boolean inside = false;
            for (ServerPlayer p : Groups.online(level, group)) {
                if (!p.isSpectator() && pastBottomGate(p.position())) {
                    inside = true;
                    break;
                }
            }
            int t = inside ? 0 : AFTER_HEART.getOrDefault(group, 0) + 10;
            AFTER_HEART.put(group, t);
            if (t >= RESET_AFTER_HEART) {
                resetGroup(level, group, true, null, false);
            }
        }
    }

    // 島に誰もいなくなって久しい: 心臓の部屋の持ち主を手放し、残った背の敵を片付ける (グループの進みはそのまま)
    static void cleanupWorld(ServerLevel level) {
        TideHeartBattle.releaseAll(level);
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX, e -> e.getTags().contains(TideMob.TRIAL_TAG))) {
            e.discard();
        }
    }

    // そのグループの進みを最初に戻す (08 の 7.1節)。心臓も眠り直し、番号が進む (古い休み場の記録は無効になる)。
    // ほかのグループの進み・部屋・敵・人には触れない
    public static void resetGroup(ServerLevel level, UUID group, boolean announce, @Nullable TideBackState.Trial forceSleep, boolean noSleep) {
        // そのグループの進みが元に戻る = 鼓動が戻る。眠っている巨神は、最短の長さを過ぎていれば目覚める (titan/end)
        jp.revati.skyabyss.titan.end.TitanDormancy.requestWake(level.getServer(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
        TideHeartBattle.reawaken(level, group);
        TideBackState.Group state = TideBackState.of(level, group);
        state.reset(level.random, forceSleep, noSleep);
        // 順路の記録 (引きは残る)
        jp.revati.skyabyss.trial.TrialRuntime.onGroupReset(level, group);
        GroupOverlays.refresh(level, group);
        AFTER_HEART.remove(group);
        // そのグループの背の敵 (印のある個体) を片付ける
        for (Entity e : level.getEntitiesOfClass(Entity.class, ISLAND_BOX,
                e -> e.getTags().contains(TideMob.TRIAL_TAG) && e instanceof GroupOwned o && group.equals(o.groupId()))) {
            e.discard();
        }
        for (ServerPlayer p : Groups.online(level, group)) {
            persisted(p).putInt(TAG_SEEN_EPOCH, state.epoch());
            if (announce) {
                Notice.narrate(p, "message.skyabyss.tide.trials_renewed");
            }
        }
        SkyAbyss.LOGGER.info("Tide back reset for group {}", group);
    }

    // 記録のあるグループをすべて戻す (コマンドで、人を指さないとき)
    public static void resetAllGroups(ServerLevel level, boolean announce, @Nullable TideBackState.Trial forceSleep, boolean noSleep) {
        for (UUID id : BackGroupData.get(level).ids()) {
            resetGroup(level, id, announce, forceSleep, noSleep);
        }
    }

    static void toArrival(ServerPlayer p) {
        p.fallDistance = 0;
        p.teleportTo(p.serverLevel(), ARRIVAL.getX() + 0.5, ARRIVAL.getY(), ARRIVAL.getZ() + 0.5, ARRIVAL_YAW, 0f);
    }

    // ---------------------------------------------------------------- 休み場 (記録の番号は、その人のグループの番号)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK || player.tickCount % 5 != 0) {
            return;
        }
        int epoch = epochOf(player.serverLevel(), player);
        CompoundTag tag = persisted(player);
        if (tag.getInt(TAG_SEEN_EPOCH) != epoch) {
            tag.putInt(TAG_SEEN_EPOCH, epoch);
        }
        BlockPos pos = player.blockPosition();
        for (Rest r : RESTS) {
            if (r.area().contains(pos)) {
                if (!r.id().equals(validCheckpoint(tag, epoch))) {
                    tag.putString(TAG_CHECKPOINT, r.id());
                    tag.putInt(TAG_CHECKPOINT_EPOCH, epoch);
                    Notice.bar(player, ModText.progress("message.skyabyss.checkpoint",
                            Component.translatable("checkpoint.skyabyss.tide_" + r.id())));
                    player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
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
        for (Rest r : RESTS) {
            if (r.id().equals(id)) {
                return id;
            }
        }
        return "";
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

    // 休み場の復活位置 (立てる場所を探す)。記録がなければ到着地点
    public static BlockPos restSpawn(ServerLevel level, String id) {
        for (Rest r : RESTS) {
            if (r.id().equals(id)) {
                return TitanBackTrials.safeSpot(level, r.spawn());
            }
        }
        return ARRIVAL;
    }

    // ---------------------------------------------------------------- 倒れたとき
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK || event.isCanceled()) {
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
        ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            return;
        }
        int epoch = epochOf(back, player);
        String cp = validCheckpoint(tag, epoch);
        BlockPos spawn = cp.isEmpty() ? ARRIVAL : restSpawn(back, cp);
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
        if (event.getFrom() == TideTravel.TIDE_BACK || event.getTo() == TideTravel.TIDE_BACK) {
            clearCheckpoint(player);
            ServerLevel back = player.server.getLevel(TideTravel.TIDE_BACK);
            if (event.getTo() == TideTravel.TIDE_BACK && back != null) {
                persisted(player).putInt(TAG_SEEN_EPOCH, epochOf(back, player));
            }
        }
    }

    // ログイン: 背中にいて、最後に見た番号 (その人のグループの) が違えば (その間にリセットされた・グループが変わった) 休み場の記録を消す
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().dimension() != TideTravel.TIDE_BACK) {
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
        if (event.getLevel().dimension() != TideTravel.TIDE_BACK || !(event.getEntity() instanceof ServerPlayer player)) {
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
                Notice.narrate(player, "message.skyabyss.tide.gave_up");
            } else {
                Notice.bar(player, ModText.hint("message.skyabyss.tide.campfire_hint"));
            }
        }
    }

    // 背で湧いた中ボス (保存しない印つき) が古いワールドに残っていれば、読み込みで消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getLevel().dimension() == TideTravel.TIDE_BACK && event.getEntity() instanceof TideBeckoner) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        idleTicks = 0;
        AFTER_HEART.clear();
        REVIVING.clear();
        TideClock.force(null);
    }
}
