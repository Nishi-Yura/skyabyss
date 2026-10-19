package jp.revati.skyabyss.party;

import jp.revati.skyabyss.util.Players;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.HeartbeatWard;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.titan.TitanBacks;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.ReentryRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 仲間が起こせる仕組み (docs/design/10 の 10.12)。巨神の背中 (戦いの部屋を含む) で、仲間が残っている人の致命の傷は、
// すぐには死なずに「倒れる」(DOWN_TICKS = 15 秒)。倒れた人は、その場で這い・何もできず・敵に狙われず・傷も受けない。
// 仲間が REVIVE_TICKS (3 秒) 「使う」を押し続けて起こす (体力 3 割で戻り、短い守りが付く)。傷を受けた・離れたら中断。
// 誰も起こせないまま時間が来た・仲間が全員倒れた (残りの仲間がいない) ときは、そのまま死ぬ (再入場の決まり: GroupBossRoom / Reentry)。
// 起こされた人は再入場の回数に数えない (死んでいないので)。一人用・仲間が背中にいない人は倒れずに普通に死ぬ。
//   skyabyssRevive  既定 true。false だと倒れの状態を使わず、これまでどおり死ぬ
// 自己テストでは、仲間の引き方 (testLookup) を渡して動かせる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Downed {
    public static final int DOWN_TICKS = 300;
    public static final int REVIVE_TICKS = 60;
    // 「使う」を押している信号が途切れても続けてよい tick (通信の揺れ)
    public static final int HOLD_GRACE = 8;
    public static final double REACH = 3.5;
    public static final float REVIVE_HEALTH = 0.3f;
    // 起こした人が動いてよい距離 (横)。これを超えたら中断
    public static final double MOVE_LIMIT = 1.5;

    public static GameRules.Key<GameRules.BooleanValue> REVIVE;

    private static final class State {
        long start;
        final MinecraftServer server;
        final ResourceKey<Level> dimension;
        UUID reviver;
        Vec3 reviverPos = Vec3.ZERO;
        int progress;

        State(long start, MinecraftServer server, ResourceKey<Level> dimension) {
            this.start = start;
            this.server = server;
            this.dimension = dimension;
        }
    }

    private record Hold(UUID target, long tick) {
    }

    private static final Map<UUID, State> DOWNED = new LinkedHashMap<>();
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    // 倒れてから死んだ人 (倒れた回数を二重に数えない。一度使うと消える)
    private static final Set<UUID> DIED_DOWN = new HashSet<>();
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    // 倒れてから死んだ人 (onDeath が付け、広間の数え方 TitanBackTrials.noteFallen が一度だけ取り出す)
    private static final Set<UUID> DOWN_DEATH = new HashSet<>();
    // 自己テスト用: 仲間の引き方 (null なら実サーバーの一覧)
    public static Function<UUID, ServerPlayer> testLookup;

    private Downed() {
    }

    @SubscribeEvent
    static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> REVIVE = GameRules.register("skyabyssRevive", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)));
    }

    public static boolean enabled(MinecraftServer server) {
        return REVIVE == null || server.getGameRules().getBoolean(REVIVE);
    }

    private static ServerPlayer lookup(MinecraftServer server, UUID id) {
        return testLookup != null ? testLookup.apply(id) : server.getPlayerList().getPlayer(id);
    }

    // ---------------------------------------------------------------- 状態
    public static boolean isDowned(@Nullable Entity e) {
        return e instanceof ServerPlayer p && DOWNED.containsKey(p.getUUID());
    }

    public static boolean isDowned(UUID id) {
        return DOWNED.containsKey(id);
    }

    public static int count() {
        return DOWNED.size();
    }

    public static int progress(UUID id) {
        State s = DOWNED.get(id);
        return s == null ? 0 : s.progress;
    }

    // 起こせる仲間: 同じパーティの、同じ次元の、生きていて倒れていない人
    public static List<ServerPlayer> rescuers(ServerPlayer player) {
        List<ServerPlayer> out = new ArrayList<>();
        PartyData.Party party = PartyData.get(player.server).of(player.getUUID());
        if (party == null) {
            return out;
        }
        for (UUID id : party.members) {
            if (id.equals(player.getUUID())) {
                continue;
            }
            ServerPlayer m = lookup(player.server, id);
            if (m != null && m.isAlive() && !m.isSpectator() && m.level() == player.level() && !DOWNED.containsKey(id)) {
                out.add(m);
            }
        }
        return out;
    }

    // 倒れてよい場面か: ルールが有効・背中の中・まだ倒れていない・起こせる仲間が残っている
    public static boolean mayDown(ServerPlayer player) {
        return enabled(player.server) && TitanBacks.isBack(player.level().dimension()) && player.isAlive() && !player.isSpectator()
                && !DOWNED.containsKey(player.getUUID()) && !rescuers(player).isEmpty();
    }

    // ---------------------------------------------------------------- 倒れる
    // 傷 damage を受ける直前。致命なら、傷を (体力 1 が残る量に) 抑えて倒れた状態にする。倒れたら true
    public static boolean tryDown(ServerPlayer player, float damage, @Nullable DamageSource source, java.util.function.Consumer<Float> setDamage) {
        if (!HeartbeatWard.lethal(damage, player.getHealth(), player.getAbsorptionAmount(), source) || !mayDown(player)) {
            return false;
        }
        setDamage.accept(HeartbeatWard.survivable(damage, player.getHealth(), player.getAbsorptionAmount()));
        down(player);
        return true;
    }

    // 倒れた状態にする (自己テストからも呼ぶ)
    public static void down(ServerPlayer player) {
        long now = player.level().getGameTime();
        DIED_DOWN.remove(player.getUUID());
        DOWNED.put(player.getUUID(), new State(now, player.server, player.level().dimension()));
        player.setForcedPose(Pose.SWIMMING);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOWN_TICKS + 40, 9, false, false, false));
        player.stopUsingItem();
        player.setSprinting(false);
        player.clearFire();
        release(player);
        GroupBossRoom.noteDown(player.getUUID());
        jp.revati.skyabyss.titan.dungeon.TitanBackTrials.noteDown(player.getUUID());
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        }
        Notice.bar(player, ModText.deny("message.skyabyss.revive.down_self", DOWN_TICKS / 20));
        Component name = player.getName();
        for (ServerPlayer mate : rescuers(player)) {
            Notice.alert(mate, "message.skyabyss.revive.down_mate", name);
        }
    }

    // 倒れた人を狙っている敵の狙いを外す
    private static void release(ServerPlayer player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(40), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    // 倒れた状態を解く (起こされた・時間切れ・退出)。姿勢と足止めを戻す
    private static void clear(UUID id, @Nullable ServerPlayer player) {
        DOWNED.remove(id);
        HOLDS.values().removeIf(h -> h.target.equals(id));
        HOLDS.remove(id);
        if (player != null) {
            player.setForcedPose(null);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    // ---------------------------------------------------------------- 起こす
    // 起こす人が「使う」を押し続けている信号 (サーバーが検める)。受け付けたら true
    public static boolean hold(ServerPlayer reviver, UUID target, long now) {
        State s = DOWNED.get(target);
        if (s == null || reviver.getUUID().equals(target) || !reviver.isAlive() || reviver.isSpectator() || DOWNED.containsKey(reviver.getUUID())
                || !enabled(reviver.server)) {
            return false;
        }
        ServerPlayer victim = lookup(reviver.server, target);
        PartyData.Party party = PartyData.get(reviver.server).of(reviver.getUUID());
        if (victim == null || party == null || !party.members.contains(target) || victim.level() != reviver.level()
                || reviver.distanceTo(victim) > REACH + 1.0) {
            return false;
        }
        HOLDS.put(reviver.getUUID(), new Hold(target, now));
        return true;
    }

    private static void revive(ServerPlayer victim, @Nullable ServerPlayer reviver) {
        clear(victim.getUUID(), victim);
        victim.setHealth(Math.max(1f, victim.getMaxHealth() * REVIVE_HEALTH));
        victim.fallDistance = 0;
        ReentryRules.guard(victim, false);
        if (victim.level() instanceof ServerLevel level) {
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.4f);
            level.sendParticles(ParticleTypes.HEART, victim.getX(), victim.getY() + 1.0, victim.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
        }
        Notice.bar(victim, ModText.battle("message.skyabyss.revive.up_self", reviver == null ? Component.empty() : reviver.getName()));
        if (reviver != null) {
            Notice.bar(reviver, ModText.progress("message.skyabyss.revive.up_mate", victim.getName()));
        }
    }

    // 時間切れ・仲間の全滅: そのまま死なせる (再入場の決まりへ)
    private static void expire(ServerPlayer player) {
        DIED_DOWN.add(player.getUUID());
        clear(player.getUUID(), player);
        player.kill();
    }

    // 倒れてから死んだ人か (倒れた回数に二重に数えない)。死んだ人の状態も片付ける
    public static boolean onDeath(ServerPlayer player) {
        boolean was = DOWNED.containsKey(player.getUUID());
        if (was) {
            clear(player.getUUID(), player);
        }
        boolean timeout = DIED_DOWN.remove(player.getUUID());
        if (was || timeout) {
            DOWN_DEATH.add(player.getUUID());
        }
        return was || timeout;
    }

    // 倒れてから死んだ人か (一度取り出すと消える)
    public static boolean takeDownDeath(UUID id) {
        return DOWN_DEATH.remove(id);
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!DOWNED.isEmpty() || !HOLDS.isEmpty()) {
            tick(server);
        }
        if (server.getTickCount() % 5 == 0 && (!DOWNED.isEmpty() || !LAST.isEmpty())) {
            syncTick(server, Players.snapshot(server.getPlayerList().getPlayers()), (p, sync) -> {
                if (p.connection != null) {
                    PacketDistributor.sendToPlayer(p, sync);
                }
            });
        }
    }

    public static void tick(MinecraftServer server) {
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            State s = DOWNED.get(id);
            if (s == null) {
                continue;
            }
            ServerPlayer p = lookup(server, id);
            if (p == null || !p.isAlive() || s.server != server || !s.dimension.equals(p.level().dimension())) {
                clear(id, p);
                continue;
            }
            long now = p.level().getGameTime();
            // 巻き戻った時刻の記録は無効
            if (s.start > now) {
                clear(id, p);
                continue;
            }
            if (!enabled(server) || rescuers(p).isEmpty() || now - s.start >= DOWN_TICKS) {
                expire(p);
                continue;
            }
            if (p.getHealth() > 1f) {
                p.setHealth(1f);
            }
            p.setSprinting(false);
            if (now % 10 == 0) {
                ring(p, now);
            }
            if (now % 20 == 0) {
                release(p);
            }
            advance(server, id, s, p);
        }
        HOLDS.values().removeIf(h -> !DOWNED.containsKey(h.target));
    }

    private static void ring(ServerPlayer p, long now) {
        if (!(p.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + (now % 40) * 0.05;
            level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 1.1, p.getY() + 0.15, p.getZ() + Math.sin(a) * 1.1, 1, 0, 0, 0, 0);
        }
    }

    // 起こしている人を一人決めて進める。中断の条件: 信号が途切れた・傷を受けた・動いた・離れた
    private static void advance(MinecraftServer server, UUID id, State s, ServerPlayer victim) {
        ServerPlayer holder = null;
        for (Map.Entry<UUID, Hold> e : HOLDS.entrySet()) {
            Hold h = e.getValue();
            if (!h.target.equals(id)) {
                continue;
            }
            ServerPlayer r = lookup(server, e.getKey());
            if (r == null || !r.isAlive() || DOWNED.containsKey(r.getUUID()) || r.level() != victim.level()) {
                continue;
            }
            // 信号の時刻は、押した人の次元のゲーム時刻
            if (Math.abs(r.level().getGameTime() - h.tick) > HOLD_GRACE) {
                continue;
            }
            if (e.getKey().equals(s.reviver)) {
                holder = r;
                break;
            }
            if (holder == null) {
                holder = r;
            }
        }
        if (holder == null) {
            s.reviver = null;
            s.progress = 0;
            return;
        }
        if (!holder.getUUID().equals(s.reviver)) {
            s.reviver = holder.getUUID();
            s.reviverPos = holder.position();
            s.progress = 0;
        }
        boolean hit = holder.hurtTime > 0;
        double dx = holder.getX() - s.reviverPos.x;
        double dz = holder.getZ() - s.reviverPos.z;
        boolean moved = dx * dx + dz * dz > MOVE_LIMIT * MOVE_LIMIT;
        boolean far = holder.distanceTo(victim) > REACH + 1.0;
        if (hit || moved || far) {
            HOLDS.remove(holder.getUUID());
            s.reviver = null;
            s.progress = 0;
            return;
        }
        s.progress++;
        if (s.progress >= REVIVE_TICKS) {
            revive(victim, holder);
        }
    }

    // ---------------------------------------------------------------- 動けない・狙われない・傷を受けない
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide && event.getNewDamage() > 0) {
            tryDown(player, event.getNewDamage(), event.getSource(), event::setNewDamage);
        }
    }

    // 倒れている間は傷を受けない (攻撃の予告の判定・炎・落下も)。虚空・/kill は貫く
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (isDowned(event.getEntity()) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onTarget(LivingChangeTargetEvent event) {
        if (isDowned(event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        deny(event.getEntity(), event);
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        deny(event.getEntity(), event);
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        deny(event.getEntity(), event);
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        deny(event.getEntity(), event);
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        deny(event.getEntity(), event);
    }

    private static void deny(Player player, net.neoforged.bus.api.ICancellableEvent event) {
        if (isDowned(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onAttack(AttackEntityEvent event) {
        if (isDowned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onUse(LivingEntityUseItemEvent.Start event) {
        if (isDowned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (isDowned(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 後始末
    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            clear(p.getUUID(), p);
            DIED_DOWN.remove(p.getUUID());
            LAST.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && DOWNED.containsKey(p.getUUID())) {
            clear(p.getUUID(), p);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        clearAll();
    }

    public static void clearAll() {
        DOWNED.clear();
        HOLDS.clear();
        DIED_DOWN.clear();
        DOWN_DEATH.clear();
        LAST.clear();
    }

    // ---------------------------------------------------------------- 仲間への同期
    // 受け取る人ごとの「パーティの倒れた人」の一覧 (自分を含む) を、変わったときだけ送る。パーティの外の人には送らない。
    // 倒れた人がいなくなったら、空を一度だけ送って止める。残り時間は 5 tick 刻み、進みは 5% 刻み
    public static void syncTick(MinecraftServer server, Iterable<? extends ServerPlayer> receivers,
            BiConsumer<ServerPlayer, DownedPackets.Sync> sink) {
        PartyData data = PartyData.get(server);
        for (ServerPlayer p : receivers) {
            PartyData.Party party = data.of(p.getUUID());
            List<DownedPackets.Entry> entries = party == null ? List.of() : entriesFor(party, p);
            if (entries.isEmpty()) {
                if (LAST.remove(p.getUUID()) != null) {
                    sink.accept(p, new DownedPackets.Sync(List.of()));
                }
                continue;
            }
            Integer hash = entries.hashCode();
            if (!Objects.equals(LAST.put(p.getUUID(), hash), hash)) {
                sink.accept(p, new DownedPackets.Sync(entries));
            }
        }
    }

    static List<DownedPackets.Entry> entriesFor(PartyData.Party party, ServerPlayer receiver) {
        List<DownedPackets.Entry> out = new ArrayList<>();
        for (UUID id : party.members) {
            State s = DOWNED.get(id);
            ServerPlayer m = s == null ? null : lookup(receiver.server, id);
            if (m == null) {
                continue;
            }
            long left = Math.max(0, s.start + DOWN_TICKS - m.level().getGameTime());
            out.add(new DownedPackets.Entry(id.toString(), party.names.getOrDefault(id, m.getGameProfile().getName()),
                    m.level().dimension().location().toString(), (int) Math.floor(m.getX()), (int) Math.floor(m.getY()),
                    (int) Math.floor(m.getZ()), (int) (left / 5) * 5, s.progress * 100 / REVIVE_TICKS / 5 * 5));
        }
        return out;
    }

    // 自己テスト用: 倒れてから ticks だけ時間がたった形にする
    public static void ageForTest(UUID id, long ticks) {
        State s = DOWNED.get(id);
        if (s != null) {
            s.start -= ticks;
        }
    }

    public static void clearSyncForTest() {
        LAST.clear();
    }
}
