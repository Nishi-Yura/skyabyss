package jp.revati.skyabyss.titan.storm.raiju;

import jp.revati.skyabyss.notice.Notice;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.GroupBossRoom;
import jp.revati.skyabyss.titan.group.GroupBossRooms;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.group.RoomLocks;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 雷獣の巣の戦い (12 の 5.2節・7.5節)。岩鯨の番兵長・潮殻の心臓と同じ決まり (GroupBossRoom):
// 巣の中に人が入ると 5 秒の集合 → 入口が閉じて、雷とともに雷獣が柱の先へ落ちてくる → 全員が倒れるか巣から消えて 3 秒で放棄。
// 雷獣が巣の外へ出たら中央へ戻す。撃破で入口が開き、報酬 (RaijuLoot。参加した一人ずつ)。
// グループ (docs/design/10 の 9 章):
//  - 巣は部屋の占有 (RoomLocks。ROOM): 柱の接地と栓が戦いで変わるので、一度に一つのグループだけが入れる。ほかのグループは肩の門の休み場 (e0) へ戻す。
//    持ち主が変わると巣を戦いの前の姿に戻す (柱は満ち、栓は起きる)
//  - 戦い・入口の封印・雷獣と雷蛾はグループのもの (GroupBossRooms。GroupOwned で、ほかのグループには見えず狙わない)
//  - 撃破はグループの記録 (StormProgress.raijuDefeated)。倒したグループの人は巣に入っても戦いは始まらない (頂の東の石へ行ける)
//    グループの試練が戻ると (StormBackTrials.resetGroup) 記録も消え、また挑める
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaijuBattle {
    public static final String ROOM = "storm.raiju";
    private static final GroupBossRooms ROOMS = new GroupBossRooms();
    private static long lastAsleepNotice;

    private RaijuBattle() {
    }

    public static GroupBossRooms rooms() {
        return ROOMS;
    }

    // 戦いの最中か (そのグループの部屋が集合中か戦闘中)。倒れた人の復活先の判断に使う (P1-1)
    public static boolean fighting(UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        return room != null && (room.active() || room.gathering());
    }

    public static GroupBossRoom room(UUID group) {
        return ROOMS.of(group);
    }

    // そのグループが、この周で雷獣を倒したか (東の風鳴り石が鳴らせる)
    public static boolean defeated(ServerLevel level, UUID group) {
        return StormProgress.of(level, group).raijuDefeated();
    }

    static final class Host implements GroupBossRoom.Host {
        private final RaijuNest.Site site;

        Host(RaijuNest.Site site) {
            this.site = site;
        }

        // 戦える人: 巣の中の、雷獣をまだ倒していないグループの人
        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, site.arena(),
                    p -> p.isAlive() && !p.isSpectator() && site.inside(p.position()))) {
                if (!StormProgress.of(level, p).raijuDefeated()) {
                    out.add(p);
                }
            }
            return out;
        }

        @Override
        public void seal(ServerLevel level, UUID group) {
            RaijuNest.seal(site, group);
            for (ServerPlayer p : Groups.online(level, group)) {
                p.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.5f);
            }
        }

        @Override
        public void open(ServerLevel level, UUID group) {
            RaijuNest.open(site, group);
        }

        @Override
        public void pullIn(ServerLevel level, UUID group) {
            AABB box = site.ante().minmax(site.door());
            for (ServerPlayer p : Groups.inBox(level, group, box)) {
                moveInside(p);
            }
        }

        @Override
        public void letBack(ServerLevel level, UUID group, Set<UUID> participants) {
            for (ServerPlayer p : Groups.inBox(level, group, site.door())) {
                if (participants.contains(p.getUUID())) {
                    moveInside(p);
                    Notice.bar(p, ModText.battle("message.skyabyss.gate_rejoin"));
                }
            }
        }

        private void moveInside(ServerPlayer p) {
            Vec3 at = site.entranceInside();
            p.fallDistance = 0;
            p.teleportTo(site.level(), at.x, at.y, at.z, -90f, 0f);
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, UUID group, List<ServerPlayer> challengers) {
            Raiju raiju = RaijuSetup.RAIJU.get().create(level);
            if (raiju == null) {
                return null;
            }
            // 入口 (西) からいちばん遠い柱 (東の 2 本のうち北) に、雷とともに落ちてくる
            int start = 0;
            raiju.moveTo(site.home().x, site.floor() + 7, site.home().z, 90f, 0f);
            raiju.bind(site, challengers.size(), start);
            raiju.setGroupId(group);
            if (!level.addFreshEntity(raiju)) {
                SkyAbyss.LOGGER.warn("The raiju could not be added to {} at {}", level.dimension().location(), raiju.position());
                return null;
            }
            Vec3 top = Vec3.atBottomCenterOf(site.rodTop(start));
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.moveTo(top.x, top.y, top.z);
                level.addFreshEntity(bolt);
            }
            for (ServerPlayer p : Groups.online(level, group)) {
                if (p.distanceToSqr(top) < 48 * 48) {
                    p.playNotifySound(RaijuSetup.ROAR.get(), SoundSource.HOSTILE, 3f, 1.15f);
                    level.sendParticles(p, ParticleTypes.ELECTRIC_SPARK, false, top.x, top.y + 1, top.z, 80, 1.0, 1.0, 1.0, 0.4);
                }
            }
            RaijuBattle.broadcast(site, group, ModText.battle("message.skyabyss.raiju.begin"));
            RaijuBattle.broadcast(site, group, ModText.hint("message.skyabyss.raiju.hint_plug"));
            SkyAbyss.LOGGER.info("The raiju woke with {} challenger(s) (group {})", challengers.size(), group);
            return raiju;
        }

        @Override
        public Vec3 home() {
            return site.home();
        }

        @Override
        public double leash() {
            return 16;
        }

        @Override
        public void broadcast(ServerLevel level, UUID group, Component message) {
            RaijuBattle.broadcast(site, group, message);
        }

        @Override
        public void countdown(ServerLevel level, UUID group, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer p : Groups.inBox(level, group, site.arena().minmax(site.ante()))) {
                Notice.bar(p, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level, UUID group) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, site.arena().inflate(6),
                    e -> e.getTags().contains(Raiju.TRIAL_TAG) && e instanceof jp.revati.skyabyss.titan.group.GroupOwned owned
                            && group.equals(owned.groupId()))) {
                e.discard();
            }
        }

        @Override
        public boolean asleep(ServerLevel level) {
            return level.getDifficulty() == Difficulty.PEACEFUL;
        }
    }

    public static Host host(MinecraftServer server) {
        return new Host(RaijuNest.site(server));
    }

    // そのグループの、巣のまわりにいる人へ
    static void broadcast(RaijuNest.Site site, UUID group, Component message) {
        AABB box = site.arena().minmax(site.ante()).inflate(2);
        for (ServerPlayer p : site.level().getEntitiesOfClass(ServerPlayer.class, box, Entity::isAlive)) {
            if (Groups.isMember(p, group)) {
                Notice.send(p, message);
            }
        }
    }

    private static RoomLocks.Listener lock(RaijuNest.Site site) {
        return new RoomLocks.Listener() {
            @Override
            public void acquired(ServerLevel level, UUID group) {
                RaijuNest.build(site);
            }

            @Override
            public void released(ServerLevel level, UUID formerOwner) {
                GroupBossRoom room = ROOMS.peek(formerOwner);
                if (room != null && room.active()) {
                    room.reset(level, new Host(site), true);
                }
                RaijuNest.build(site);
            }
        };
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.players().isEmpty() || !RaijuNest.isNestLevel(level)) {
            return;
        }
        RaijuNest.Site site = RaijuNest.site(level.getServer());
        if (!level.isLoaded(site.center())) {
            return;
        }
        if (level.getGameTime() % 5 == 0) {
            RaijuNest.tickRods(site);
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOMS.anyActive()) {
            RaijuNest.ensureBuilt(site);
        }
        tick(site);
    }

    // 10 tick ごと: 巣の占有を決め直し (ほかのグループは e0 へ)、グループごとの戦いを動かす
    static void tick(RaijuNest.Site site) {
        ServerLevel level = site.level();
        RoomLocks.update(level, ROOM, site.arena(), StormBackLayout.rest("e0"), RoomLocks.DEFAULT_GRACE, lock(site));
        Host host = new Host(site);
        if (host.asleep(level) && !host.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            for (ServerPlayer p : host.playersInside(level)) {
                Notice.send(p, ModText.deny("message.skyabyss.raiju.asleep"));
            }
        }
        ROOMS.tick(level, host);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。巣の中のそのグループの人を挑む人とする
    public static boolean startNow(MinecraftServer server, UUID group) {
        RaijuNest.Site site = RaijuNest.site(server);
        RaijuNest.ensureBuilt(site);
        if (defeated(site.level(), group)) {
            reawaken(server, group);
        }
        GroupBossRoom room = ROOMS.of(group);
        if (room.active()) {
            return false;
        }
        Host host = new Host(site);
        List<ServerPlayer> inside = new ArrayList<>();
        for (ServerPlayer p : host.playersInside(site.level())) {
            if (Groups.isMember(p, group)) {
                inside.add(p);
            }
        }
        return room.start(site.level(), host, inside);
    }

    public static void abandon(MinecraftServer server, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null) {
            room.reset(RaijuNest.site(server).level(), host(server), true);
        }
    }

    public static void abandonAll(MinecraftServer server) {
        ROOMS.resetAll(RaijuNest.site(server).level(), host(server), true);
    }

    // そのグループの雷獣をもう一度挑めるようにする (テスト用。背中のリセットはグループの記録ごと戻す)
    public static void reawaken(MinecraftServer server, UUID group) {
        ServerLevel level = RaijuNest.site(server).level();
        if (RaijuNest.isNestLevel(level)) {
            StormProgress.of(level, group).setRaijuDefeated(false);
        }
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null) {
            room.reset(level, host(server), false);
        }
    }

    // グループの試練が戻った (StormBackTrials.resetGroup から): そのグループの戦いを止め、巣を持っていれば手放す
    public static void onGroupReset(ServerLevel level, UUID group) {
        if (!RaijuNest.isNestLevel(level)) {
            return;
        }
        RaijuNest.Site site = RaijuNest.site(level.getServer());
        GroupBossRoom room = ROOMS.peek(group);
        if (room != null && (room.active() || room.gathering())) {
            room.reset(level, new Host(site), false);
        }
        if (group.equals(RoomLocks.owner(ROOM))) {
            RoomLocks.release(ROOM);
            if (level.isLoaded(site.center())) {
                RaijuNest.build(site);
            }
        }
    }

    // いま戦っている雷獣 (巣の持ち主の。いなければどれか一つ)
    @Nullable
    public static Raiju current(MinecraftServer server) {
        UUID owner = RoomLocks.owner(ROOM);
        if (owner != null) {
            Raiju r = current(server, owner);
            if (r != null) {
                return r;
            }
        }
        for (GroupBossRoom room : ROOMS.all()) {
            Raiju r = current(server, room.group);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    public static Raiju current(MinecraftServer server, UUID group) {
        GroupBossRoom room = ROOMS.peek(group);
        UUID id = room == null ? null : room.bossId();
        if (id == null) {
            return null;
        }
        RaijuNest.Site site = RaijuNest.site(server);
        if (site.level().getEntity(id) instanceof Raiju r) {
            return r;
        }
        // 湧いた直後で、まだ区画が読み込みの途中のときは、場所で探す
        return site.level().getEntitiesOfClass(Raiju.class, site.arena().inflate(4), e -> e.getUUID().equals(id)).stream().findFirst().orElse(null);
    }

    // 雷獣が倒れた (Raiju.die から)。いま戦っている雷獣なら、記録・報酬を渡して入口を開ける
    static void onDefeated(ServerLevel level, Raiju raiju) {
        RaijuNest.Site site = RaijuNest.site(level.getServer());
        if (site.level() != level) {
            return;
        }
        GroupBossRoom room = ROOMS.byBoss(raiju.getUUID());
        if (room == null) {
            return;
        }
        UUID group = room.group;
        Set<UUID> ids = room.finish(level, new Host(site), raiju.getUUID());
        if (ids == null) {
            return;
        }
        RaijuState.get(level.getServer()).setDefeated(true);
        // グループの記録へ (巣の真ん中の昇り風が立ち、東の風鳴り石が鳴らせる)
        StormBackTrials.onRaijuDefeated(level, group);
        RaijuLoot.onDefeat(level, ids);
        broadcast(site, group, ModText.lore("message.skyabyss.raiju.after"));
        broadcast(site, group, ModText.progress("message.skyabyss.raiju.east_stone_ready"));
        Vec3 c = site.home();
        for (ServerPlayer p : Groups.online(level, group)) {
            if (p.distanceToSqr(c) < 48 * 48) {
                p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 1.2f);
                // 行き場をなくした雷が柱を行き来する (9.3節の伝承の絵)
                for (int i = 0; i < RaijuNest.RODS; i++) {
                    Vec3 t = Vec3.atCenterOf(site.rodTop(i));
                    level.sendParticles(p, ParticleTypes.ELECTRIC_SPARK, false, t.x, t.y + 0.5, t.z, 40, 0.3, 0.6, 0.3, 0.3);
                }
            }
        }
        SkyAbyss.LOGGER.info("The raiju was defeated by {} participant(s) (group {})", ids.size(), group);
    }

    // 試練で湧いた雷獣は保存しないが、古いものが残っていれば読み込みのときに消す
    @SubscribeEvent
    static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(Raiju.TRIAL_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ROOMS.clear();
        RoomLocks.release(ROOM);
    }

}
