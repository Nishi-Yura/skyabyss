package jp.revati.skyabyss.titan.travel;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.TitanWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 巨神での移動のまとめ役。呼ぶ・乗る・行き先を決める・降りる、と、それを支える見張り
// (乗っている人の保護、ログアウトと再ログイン、解放の確認)。巨神は一体だけなので、旅も巨神ごとに一つだけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Voyages {
    // ログアウト・サーバーの停止で旅から外れた人を、次のログインで降ろす場所 (プレイヤーの保存データ)
    private static final String TAG_SAFE = "skyabyss_voyage_safe";

    private static final Map<String, Voyage> ACTIVE = new HashMap<>();
    // いま降ろしてよい人 (降りる操作を止めない)
    private static final Set<UUID> RELEASING = new HashSet<>();

    private Voyages() {
    }

    public static TravelState state(ServerPlayer player) {
        return player.getData(TravelSetup.STATE);
    }

    // 巨神に認められているか: その巨神の心臓を倒した (巨神の記憶に撃破の記録がある)、またはコマンドで認めた
    public static boolean isUnlocked(ServerPlayer player, TitanKind kind) {
        if (state(player).unlocked.contains(kind.id())) {
            return true;
        }
        TitanMemoryData.Record r = TitanMemoryData.get(player.server).peek(player.getUUID(), kind.id());
        return r != null && r.clears > 0;
    }

    @Nullable
    static Voyage active(TitanKind kind) {
        return ACTIVE.get(kind.id());
    }

    @Nullable
    static Voyage involving(UUID player) {
        for (Voyage v : ACTIVE.values()) {
            if (v.involves(player)) {
                return v;
            }
        }
        return null;
    }

    public static boolean isBusy(TitanKind kind) {
        return ACTIVE.containsKey(kind.id());
    }

    // ---------------------------------------------------------------- 呼ぶ

    public enum CallResult { CALLED, JOINED, REOPENED, REFUSED }

    // 呼び笛を吹いた。bypass はテスト用 (解放・クールダウン・空・戦いを見ない)
    public static CallResult call(ServerPlayer player, TitanKind kind, boolean bypass) {
        Voyage current = active(kind);
        if (current != null && current.involves(player.getUUID())) {
            // 乗っている人がもう一度吹いた: 決める人なら海図を開き直す
            if (current.riders.contains(player.getUUID()) && current.dest == null && current.leader.equals(player.getUUID())) {
                sendChart(player, current);
                return CallResult.REOPENED;
            }
            if (current.riders.contains(player.getUUID()) && !current.leader.equals(player.getUUID())) {
                Notice.bar(player, ModText.hint("message.skyabyss.travel.not_leader", current.leaderName));
            }
            return CallResult.REFUSED;
        }
        TravelRules.Refusal refusal = TravelRules.check(player, kind, bypass);
        if (current != null && !current.leader.equals(player.getUUID()) && current.beforeDeparture() && !current.pendingCancel
                && refusal != TravelRules.Refusal.WRONG_DIMENSION && refusal != TravelRules.Refusal.RIDING
                && player.distanceToSqr(Vec3.atCenterOf(current.pickup)) < TravelRules.JOIN_RADIUS * TravelRules.JOIN_RADIUS) {
            // ほかの人の呼んだ巨神には、まだ認められていない人も、休んでいる人も一緒に乗れる
            Notice.bar(player, ModText.hint(kind.text("message.skyabyss.travel.join"), current.leaderName));
            return CallResult.JOINED;
        }
        if (refusal == TravelRules.Refusal.COOLDOWN) {
            Notice.bar(player, ModText.deny(kind.text(refusal.key), (TravelRules.cooldownLeft(player) + 19) / 20));
            return CallResult.REFUSED;
        }
        if (refusal != null) {
            Notice.bar(player, ModText.deny(kind.text(refusal.key)));
            return CallResult.REFUSED;
        }
        if (current != null) {
            if (current.leader.equals(player.getUUID())) {
                Notice.bar(player, ModText.hint(kind.text("message.skyabyss.travel.already_coming")));
                return CallResult.REFUSED;
            }
            if (current.beforeDeparture() && !current.pendingCancel
                    && player.distanceToSqr(Vec3.atCenterOf(current.pickup)) < TravelRules.JOIN_RADIUS * TravelRules.JOIN_RADIUS) {
                // ほかの人の呼んだ巨神に、一緒に乗れる
                Notice.bar(player, ModText.hint(kind.text("message.skyabyss.travel.join"), current.leaderName));
                return CallResult.JOINED;
            }
            Notice.bar(player, ModText.deny(kind.text("message.skyabyss.travel.busy"), current.leaderName));
            return CallResult.REFUSED;
        }
        if (kind.busyAboard().get()) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.lifting"));
            return CallResult.REFUSED;
        }
        ServerLevel overworld = player.server.overworld();
        BlockPos pickup;
        if (kind.style() == TitanKind.Style.SEA) {
            // 潮殻: 海辺でなければ呼べない (寄れる浅瀬と、迎えの泡の柱を立てる水際を探す)
            pickup = SeaVoyage.findPickup(overworld, player);
            if (pickup == null) {
                Notice.bar(player, ModText.deny("message.skyabyss.travel.refuse.no_sea"));
                return CallResult.REFUSED;
            }
        } else {
            pickup = LandingFinder.find(overworld, player.blockPosition(), 2, 5);
        }
        TravelTitan titan = kind.find(overworld);
        Vec3 relocate = null;
        if (titan == null || Math.hypot(titan.asEntity().getX() - player.getX(), titan.asEntity().getZ() - player.getZ()) > TravelRules.CALL_FLY_LIMIT) {
            // 遠い・読み込まれていない: 雲の向こうから呼び寄せる。前にいた方角から来る
            Vec3 from = titan != null ? titan.asEntity().position() : TitanWorldData.get(overworld).lastPosition();
            double angle = from != null ? Math.atan2(from.z - player.getZ(), from.x - player.getX()) : overworld.random.nextDouble() * Math.PI * 2;
            double x = player.getX() + Math.cos(angle) * TravelRules.CALL_ENTRY;
            double z = player.getZ() + Math.sin(angle) * TravelRules.CALL_ENTRY;
            double y = Math.min(overworld.getMaxBuildHeight() - 50, Math.max(TravelRules.TRAVEL_Y, Voyage.surfaceMax(overworld, player.position()) + TravelRules.TERRAIN_CLEARANCE));
            if (kind.style() == TitanKind.Style.SEA) {
                // 潮殻は沖の海から歩いてくる (海の点が見つからなければ、迎えの場所の沖)
                Vec3 sea = SeaVoyage.entryPoint(overworld, pickup, angle);
                x = sea.x;
                z = sea.z;
                y = sea.y;
            }
            if (titan == null) {
                titan = kind.respawn().spawn(overworld, x, y, z);
            } else {
                relocate = new Vec3(x, y, z);
            }
        }
        titan.beginVoyage();
        Voyage voyage = newVoyage(kind, titan, player, pickup);
        voyage.relocateTo = relocate;
        ACTIVE.put(kind.id(), voyage);
        overworld.playSound(null, player.getX(), player.getY(), player.getZ(), kind.callSound().get(), SoundSource.PLAYERS, 4f, 1f);
        Notice.narrate(player, kind.text("message.skyabyss.travel.called"));
        SkyAbyss.LOGGER.info("{} called the {} at {}", player.getGameProfile().getName(), kind.id(), pickup);
        return CallResult.CALLED;
    }

    // 巨神の型ごとの旅 (後の巨神が新しい型を足すときは、ここに一行)
    static Voyage newVoyage(TitanKind kind, TravelTitan titan, ServerPlayer leader, BlockPos pickup) {
        return switch (kind.style()) {
            case SEA -> new SeaVoyage(kind, titan, leader, pickup);
            case SKY -> new Voyage(kind, titan, leader, pickup);
        };
    }

    // 呼ばれた巨神の鎖を右クリックした
    public static void grabChain(ServerPlayer player, TravelTitan titan) {
        for (Voyage v : ACTIVE.values()) {
            if (v.titan == titan) {
                if (v.pendingCancel) {
                    Notice.bar(player, ModText.deny("message.skyabyss.travel.leaving"));
                    return;
                }
                v.grab(player);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- 海図

    static void sendChart(ServerPlayer player, Voyage voyage) {
        List<TravelNetwork.Dest> dests = destinations(player, voyage);
        // 壊された碑は、この海図では「失われた碑」として灰色で見せ (選べない)、記録からは外して枠を空ける。次に開いたときには出ない
        pruneLost(state(player), TravelWorldData.get(player.server));
        PacketDistributor.sendToPlayer(player, new TravelNetwork.Chart(voyage.kind.id(), voyage.leader.equals(player.getUUID()),
                voyage.leaderName, voyage.pickup.getX(), voyage.pickup.getZ(), voyage.dest == null ? "" : voyage.dest.id(), dests));
    }

    // 行き先の一覧: はじまりの地 (世界の初期スポーン) と、覚えた碑
    public static List<TravelNetwork.Dest> destinations(ServerPlayer player) {
        return destinations(player, null);
    }

    public static List<TravelNetwork.Dest> destinations(ServerPlayer player, @Nullable Voyage voyage) {
        ServerLevel overworld = player.server.overworld();
        List<TravelNetwork.Dest> list = new ArrayList<>();
        BlockPos spawn = overworld.getSharedSpawnPos();
        list.add(dest(player, voyage, "spawn", "", "", spawn, 0, true));
        TravelWorldData world = TravelWorldData.get(player.server);
        for (BlockPos pos : state(player).waymarks) {
            TravelWorldData.Waymark mark = world.waymark(pos);
            list.add(dest(player, voyage, "w" + pos.asLong(), mark == null ? "" : mark.name(), mark == null ? "" : mark.biome(), pos, 1, mark != null));
        }
        return list;
    }

    // 世界にもうない碑を、プレイヤーの記録から外す。外した数を返す
    static int pruneLost(TravelState s, TravelWorldData world) {
        int before = s.waymarks.size();
        s.waymarks.removeIf(pos -> world.waymark(pos) == null);
        return before - s.waymarks.size();
    }

    private static TravelNetwork.Dest dest(ServerPlayer player, @Nullable Voyage voyage, String id, String name, String biome, BlockPos pos, int kind,
                                           boolean valid) {
        double distance = Math.hypot(pos.getX() + 0.5 - player.getX(), pos.getZ() + 0.5 - player.getZ());
        int seconds = voyage == null ? TravelRules.estimateSeconds(distance) : voyage.estimate(distance);
        return new TravelNetwork.Dest(id, name, biome, pos.getX(), pos.getY(), pos.getZ(), kind, valid, seconds);
    }

    public static Component destName(@Nullable TravelNetwork.Dest dest) {
        if (dest == null) {
            return Component.empty();
        }
        if (dest.kind() == 0) {
            return Component.translatable("gui.skyabyss.travel.spawn");
        }
        if (!dest.name().isEmpty()) {
            return Component.literal(dest.name());
        }
        return waymarkName(dest.biome());
    }

    public static Component waymarkName(String biome) {
        if (biome.isEmpty()) {
            return Component.translatable("gui.skyabyss.travel.waymark_unnamed");
        }
        return Component.translatable("gui.skyabyss.travel.waymark_of", Component.translatable("biome." + biome.replace(':', '.')));
    }

    // 決める人が行き先を選んだ
    static void choose(ServerPlayer player, String id) {
        Voyage v = involving(player.getUUID());
        if (v == null || v.pendingCancel || v.dest != null || v.phase != Voyage.Phase.WAITING) {
            return;
        }
        if (!v.leader.equals(player.getUUID()) || !v.riders.contains(player.getUUID())) {
            Notice.bar(player, ModText.hint("message.skyabyss.travel.not_leader", v.leaderName));
            return;
        }
        TravelNetwork.Dest chosen = null;
        for (TravelNetwork.Dest d : destinations(player, v)) {
            if (d.id().equals(id)) {
                chosen = d;
            }
        }
        if (chosen == null || !chosen.valid()) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.dest_lost"));
            sendChart(player, v);
            return;
        }
        double distance = Math.hypot(chosen.x() + 0.5 - v.pickup.getX(), chosen.z() + 0.5 - v.pickup.getZ());
        if (distance < TravelRules.MIN_DISTANCE) {
            Notice.bar(player, ModText.deny("message.skyabyss.travel.too_close"));
            sendChart(player, v);
            return;
        }
        String refused = v.accept(chosen);
        if (refused != null) {
            Notice.bar(player, ModText.deny(refused));
            sendChart(player, v);
            return;
        }
        v.chosen(chosen);
        for (ServerPlayer rider : v.players(v.riders)) {
            Notice.unlock(rider, "message.skyabyss.travel.chosen", destName(chosen), TravelRules.BOARDING_COUNTDOWN / 20);
        }
    }

    static void forget(ServerPlayer player, String id) {
        if (!id.startsWith("w")) {
            return;
        }
        try {
            BlockPos pos = BlockPos.of(Long.parseLong(id.substring(1)));
            state(player).forgetWaymark(pos);
        } catch (NumberFormatException ignored) {
            return;
        }
        Voyage v = involving(player.getUUID());
        if (v != null && v.riders.contains(player.getUUID())) {
            sendChart(player, v);
        }
    }

    // 出発の前に降りる (海図の「降りる」、またはしゃがむ)
    static void disembark(ServerPlayer player) {
        Voyage v = involving(player.getUUID());
        if (v == null || !v.riders.contains(player.getUUID()) || !v.beforeDeparture()) {
            return;
        }
        v.startLower(player);
        Notice.hint(player, v.kind.text("message.skyabyss.travel.disembark"));
        if (v.leader.equals(player.getUUID()) && v.dest == null && !v.passLeadership()) {
            v.cancel("message.skyabyss.travel.cancel.disembark", true);
        }
    }

    // ---------------------------------------------------------------- 降りたあと・記録

    static void arrived(ServerPlayer player, TitanKind kind, @Nullable TravelNetwork.Dest dest) {
        TravelState s = state(player);
        s.voyages++;
        Notice.narrate(player, kind.text("message.skyabyss.travel.arrived"), destName(dest));
        Quests.signal(player, "titan_voyage");
    }

    static void setCooldown(ServerPlayer player, int ticks) {
        TravelState s = state(player);
        s.readyAt = Math.max(s.readyAt, player.server.overworld().getGameTime() + ticks);
    }

    // ログアウト中の呼んだ人のクールダウン。再起動でも消えないよう世界の記録 (TravelWorldData) に置き、再ログインで本人へ移す
    static void setCooldownOffline(MinecraftServer server, UUID id, int ticks) {
        TravelWorldData.get(server).putOfflineReady(id, server.overworld().getGameTime() + ticks);
    }

    static void markSafe(ServerPlayer player, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        player.getPersistentData().put(TAG_SAFE, tag);
    }

    static void clearSafe(ServerPlayer player) {
        player.getPersistentData().remove(TAG_SAFE);
    }

    static void allowDismount(ServerPlayer player, Runnable action) {
        RELEASING.add(player.getUUID());
        try {
            action.run();
        } finally {
            RELEASING.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------- 見張り

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Voyage v : new ArrayList<>(ACTIVE.values())) {
            Entity whale = v.entity();
            TravelTitan registered = v.totalTicks % 20 == 0 ? v.kind.find(v.level()) : null;
            if (whale.isRemoved() || !v.titan.inVoyage() || (registered != null && registered != v.titan)) {
                // 消えた・呼び直された (本物が別の個体になった)
                v.lost();
            } else if (v.pendingCancel) {
                v.tickCancelling();
            } else {
                try {
                    v.tick();
                } catch (RuntimeException e) {
                    // 旅の不具合で世界を止めない。乗っている人は緩やかに降ろす
                    SkyAbyss.LOGGER.error("Titan voyage failed; ending it", e);
                    v.lost();
                }
            }
            if (v.finished) {
                ACTIVE.remove(v.kind.id());
            }
        }
    }

    // 着くまでは乗り場から降りられない (しゃがんでも降りない)。出発の前にしゃがんだら、鎖で降ろす
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer player) || !(event.getEntityBeingMounted() instanceof TravelTitan)) {
            return;
        }
        if (RELEASING.contains(player.getUUID()) || !player.isAlive() || player.isRemoved() || !player.isShiftKeyDown()) {
            return;
        }
        Voyage v = involving(player.getUUID());
        if (v == null || event.getEntityBeingMounted().isRemoved()) {
            return;
        }
        event.setCanceled(true);
        if (v.beforeDeparture() && !v.pendingCancel) {
            disembark(player);
        } else {
            Notice.bar(player, ModText.hint(v.kind.text("message.skyabyss.travel.no_dismount")));
        }
    }

    // 乗っている・上り下りしている人は、壁に埋まる・落ちる・ぶつかるで傷つかない
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || ACTIVE.isEmpty() || involving(player.getUUID()) == null) {
            return;
        }
        if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL)
                || event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    // ログアウト: 旅から外し、次のログインで降ろす場所 (印) を残す。ログアウトのイベントは保存より前に来る
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Voyage v = involving(player.getUUID());
        if (v == null) {
            return;
        }
        boolean departed = !v.beforeDeparture() && v.dest != null;
        v.riders.remove(player.getUUID());
        v.climbs.remove(player.getUUID());
        v.lowers.remove(player.getUUID());
        v.dismount(player);
        player.setNoGravity(false);
        player.fallDistance = 0;
        markSafe(player, departed ? v.destFeet() : v.pickup);
        PacketDistributor.sendToPlayer(player, new TravelNetwork.Veil(false, 0));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long ready = TravelWorldData.get(player.server).takeOfflineReady(player.getUUID());
        if (ready != null) {
            state(player).readyAt = Math.max(state(player).readyAt, ready);
        }
        CompoundTag tag = player.getPersistentData().getCompound(TAG_SAFE);
        if (!tag.contains("x")) {
            return;
        }
        clearSafe(player);
        // 旅の途中で抜けた (サーバーが落ちた場合も)。宙に残さず、降ろす場所へ
        if (player.isNoGravity()) {
            player.setNoGravity(false);
        }
        if (player.isAlive() && player.level().dimension() == Level.OVERWORLD) {
            ServerLevel overworld = player.serverLevel();
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            // 着く先の地面の高さは、チャンクを読み込んでから決める (読み込まれていないと高さが最低になり、地中へ埋まる)。
            // 古い高さで埋まらないよう、高い方を取る。ログインのときに 1 チャンクだけ同期で読み込む
            overworld.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int top = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            double y = Math.max(pos.getY(), top) + 0.1;
            player.fallDistance = 0;
            player.teleportTo(overworld, pos.getX() + 0.5, y, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 10, 0, false, false, true));
            Notice.narrate(player, "message.skyabyss.travel.woke_landed");
        }
    }

    // 解放の確認と、最初の呼び笛 (2 秒ごと。古いワールドで心臓をすでに倒している人にも渡る)
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 7 || !player.isAlive()) {
            return;
        }
        giveCalls(player);
    }

    // 心臓を倒した巨神 (巨神の記憶の撃破の記録) の呼び笛を、一度だけ渡す。渡した数を返す
    static int giveCalls(ServerPlayer player) {
        int given = 0;
        for (TitanKind kind : TitanKind.all()) {
            TravelState s = state(player);
            if (s.callGiven.contains(kind.id()) || !isUnlocked(player, kind)) {
                continue;
            }
            given++;
            s.callGiven.add(kind.id());
            s.unlocked.add(kind.id());
            ItemStack call = new ItemStack(kind.callItem().get());
            if (!player.getInventory().add(call)) {
                player.drop(call, false);
            }
            Notice.narrate(player, kind.text("message.skyabyss.travel.recognized"));
            Notice.hint(player, kind.text("message.skyabyss.travel.recognized_hint"));
            player.playNotifySound(kind.callSound().get(), SoundSource.PLAYERS, 0.6f, 1.2f);
            Quests.signal(player, "titan_travel_" + kind.id());
        }
        return given;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        RELEASING.clear();
    }

    // ---------------------------------------------------------------- コマンド・自己テスト向け

    @Nullable
    static String describe(TitanKind kind) {
        Voyage v = active(kind);
        if (v == null) {
            return null;
        }
        return v.phase + " t=" + v.phaseTicks + " leader=" + v.leaderName + " riders=" + v.riders.size() + " climbing=" + v.climbs.size()
                + " lowering=" + v.lowers.size() + " dest=" + (v.dest == null ? "-" : v.dest.id()) + (v.pendingCancel ? " (cancelling)" : "");
    }

    static void forceEnd(TitanKind kind) {
        Voyage v = active(kind);
        if (v != null) {
            v.finish(false);
            ACTIVE.remove(kind.id());
        }
    }

    static void put(Voyage voyage) {
        ACTIVE.put(voyage.kind.id(), voyage);
    }
}
