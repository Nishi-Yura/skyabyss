package jp.revati.skyabyss.titan.storm.raiju;

import jp.revati.skyabyss.notice.Notice;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.BossRoom;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormBackTrials;
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

// 雷獣の巣の戦い (12 の 5.2節・7.5節)。岩鯨の番兵長・潮殻の心臓と同じ決まり (BossRoom):
// 巣の中に人が入ると 5 秒の集合 → 入口が閉じて、雷とともに雷獣が柱の先へ落ちてくる → 全員が倒れるか巣から消えて 3 秒で放棄。
// 雷獣が巣の外へ出たら中央へ戻す。撃破で入口が開き、報酬 (RaijuLoot)。
// 撃破の記録 (RaijuState.defeated) は、背中のリセットが reawaken で戻すまで残る (そのあいだ巣に入っても戦いは始まらない)。
// 撃破は StormBackTrials.onRaijuDefeated で背中の記録にも書く (東の風鳴り石・巣の昇り風は背中の担当がそちらを読む)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RaijuBattle {
    private static final BossRoom ROOM = new BossRoom();
    private static long lastAsleepNotice;

    private RaijuBattle() {
    }

    public static BossRoom room() {
        return ROOM;
    }

    // 東の風鳴り石が鳴れるか (この周で雷獣が倒された)
    public static boolean defeated(MinecraftServer server) {
        return RaijuState.get(server).defeated();
    }

    static final class Host implements BossRoom.Host {
        private final RaijuNest.Site site;

        Host(RaijuNest.Site site) {
            this.site = site;
        }

        @Override
        public List<ServerPlayer> playersInside(ServerLevel level) {
            return level.getEntitiesOfClass(ServerPlayer.class, site.arena(), p -> p.isAlive() && !p.isSpectator() && site.inside(p.position()));
        }

        @Override
        public void seal(ServerLevel level) {
            RaijuNest.seal(site);
            level.playSound(null, site.gateCenter(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        }

        @Override
        public void open(ServerLevel level) {
            RaijuNest.open(site);
        }

        @Override
        public void pullIn(ServerLevel level) {
            AABB box = site.ante().minmax(site.door());
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
                moveInside(p);
            }
        }

        @Override
        public void letBack(ServerLevel level, Set<UUID> participants) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, site.door(),
                    p -> p.isAlive() && !p.isSpectator() && participants.contains(p.getUUID()))) {
                moveInside(p);
                Notice.bar(p, ModText.battle("message.skyabyss.gate_rejoin"));
            }
        }

        private void moveInside(ServerPlayer p) {
            Vec3 at = site.entranceInside();
            p.fallDistance = 0;
            p.teleportTo(site.level(), at.x, at.y, at.z, -90f, 0f);
        }

        @Override
        @Nullable
        public Mob spawnBoss(ServerLevel level, List<ServerPlayer> challengers) {
            Raiju raiju = RaijuSetup.RAIJU.get().create(level);
            if (raiju == null) {
                return null;
            }
            // 入口 (西) からいちばん遠い柱 (東の 2 本のうち北) に、雷とともに落ちてくる
            int start = 0;
            raiju.moveTo(site.home().x, site.floor() + 7, site.home().z, 90f, 0f);
            raiju.bind(site, challengers.size(), start);
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
            level.playSound(null, top.x, top.y, top.z, RaijuSetup.ROAR.get(), SoundSource.HOSTILE, 3f, 1.15f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y + 1, top.z, 80, 1.0, 1.0, 1.0, 0.4);
            broadcast(level, ModText.battle("message.skyabyss.raiju.begin"));
            broadcast(level, ModText.hint("message.skyabyss.raiju.hint_plug"));
            SkyAbyss.LOGGER.info("The raiju woke with {} challenger(s)", challengers.size());
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
        public void broadcast(ServerLevel level, Component message) {
            RaijuBattle.broadcast(site, message);
        }

        @Override
        public void countdown(ServerLevel level, int secondsLeft) {
            Component text = ModText.battle("message.skyabyss.gate_countdown", secondsLeft);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, site.arena().minmax(site.ante()), Entity::isAlive)) {
                Notice.bar(p, text);
            }
        }

        @Override
        public void cleanup(ServerLevel level) {
            for (Entity e : level.getEntitiesOfClass(Entity.class, site.arena().inflate(6), e -> e.getTags().contains(Raiju.TRIAL_TAG))) {
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

    static void broadcast(RaijuNest.Site site, Component message) {
        AABB box = site.arena().minmax(site.ante()).inflate(2);
        for (ServerPlayer p : site.level().getEntitiesOfClass(ServerPlayer.class, box, Entity::isAlive)) {
            Notice.send(p, message);
        }
    }

    // ---------------------------------------------------------------- 毎 tick
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.players().isEmpty() || !RaijuNest.isNestLevel(level)) {
            return;
        }
        RaijuNest.Site site = RaijuNest.site(level.getServer());
        RaijuState state = RaijuState.get(level.getServer());
        if (!level.isLoaded(site.center())) {
            return;
        }
        if (level.getGameTime() % 5 == 0) {
            RaijuNest.tickRods(site);
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        if (level.getGameTime() % 100 == 0 && !ROOM.active()) {
            RaijuNest.ensureBuilt(site);
        }
        // 背中のリセット (StormBackTrials.reset) が背中の記録を戻したら、雷獣もまた挑めるようにする
        if (state.defeated() && !ROOM.active() && !StormBackState.get(level).raijuDefeated()) {
            state.setDefeated(false);
        }
        if (state.defeated()) {
            return;
        }
        Host host = new Host(site);
        if (host.asleep(level) && !host.playersInside(level).isEmpty() && level.getGameTime() - lastAsleepNotice > 200) {
            lastAsleepNotice = level.getGameTime();
            broadcast(site, ModText.deny("message.skyabyss.raiju.asleep"));
        }
        ROOM.tick(level, host);
    }

    // 戦いを今すぐ始める (テスト用のコマンドと自己テスト)。巣の中の人を挑む人とする
    public static boolean startNow(MinecraftServer server) {
        RaijuNest.Site site = RaijuNest.site(server);
        RaijuNest.ensureBuilt(site);
        if (RaijuState.get(server).defeated()) {
            reawaken(server);
        }
        if (ROOM.active()) {
            return false;
        }
        Host host = new Host(site);
        return ROOM.start(site.level(), host, host.playersInside(site.level()));
    }

    public static void abandon(MinecraftServer server) {
        ROOM.reset(RaijuNest.site(server).level(), host(server), true);
    }

    // 雷獣をもう一度挑めるようにする (背中のリセット・テスト用)
    public static void reawaken(MinecraftServer server) {
        RaijuState.get(server).setDefeated(false);
        ServerLevel level = RaijuNest.site(server).level();
        if (RaijuNest.isNestLevel(level)) {
            StormBackState.get(level).setRaijuDefeated(false);
        }
        ROOM.reset(RaijuNest.site(server).level(), host(server), false);
    }

    @Nullable
    public static Raiju current(MinecraftServer server) {
        UUID id = ROOM.bossId();
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
        Set<UUID> ids = ROOM.finish(level, new Host(site), raiju.getUUID());
        if (ids == null) {
            return;
        }
        RaijuState.get(level.getServer()).setDefeated(true);
        // 背中の記録へ (巣の真ん中の昇り風が立ち、東の風鳴り石が鳴らせる)
        StormBackTrials.onRaijuDefeated(level);
        RaijuLoot.onDefeat(level, ids);
        broadcast(site, ModText.lore("message.skyabyss.raiju.after"));
        broadcast(site, ModText.progress("message.skyabyss.raiju.east_stone_ready"));
        Vec3 c = site.home();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 3f, 1.2f);
        // 行き場をなくした雷が柱を行き来する (9.3節の伝承の絵)
        for (int i = 0; i < RaijuNest.RODS; i++) {
            Vec3 t = Vec3.atCenterOf(site.rodTop(i));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.x, t.y + 0.5, t.z, 40, 0.3, 0.6, 0.3, 0.3);
        }
        SkyAbyss.LOGGER.info("The raiju was defeated by {} participant(s)", ids.size());
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
        ROOM.clear();
    }

}
